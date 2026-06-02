// THIS IS JvmMainCompose.kt - in jvmMain compose module of kmp compose app

package kz.aita

import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.github.javakeyring.Keyring
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.net.URLEncoder
import java.net.URI
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.security.SecureRandom
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

private val keyringService = "aita_keyring"
private val keyring: Keyring = Keyring.create()

private val rng = SecureRandom()
private val fallbackEncryptionKeys = ConcurrentHashMap<String, ByteArray>()

object ReceiptPlatformJvmBridge {
    /**
     * Optional desktop ESC/POS writer. Configure it for USB serial, COM port, network printer, or tests.
     */
    var writeEscPosBytes: (suspend (ByteArray) -> Boolean)? = null

    /**
     * Simple cable/device-path writer for the first real-device pass.
     * Linux: /dev/usb/lp0, /dev/ttyUSB0
     * macOS: /dev/cu.usbserial-XXXX
     * Windows: COM3 or \.\COM3
     * You can also set AITA_RECEIPT_PRINTER_DEVICE before launching the desktop app.
     */
    var escPosDevicePath: String? = System.getenv("AITA_RECEIPT_PRINTER_DEVICE")
        ?.trim()
        ?.takeIf { it.isNotBlank() }

    fun configureEscPosDevicePath(path: String?) {
        escPosDevicePath = path
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun normalizedDevicePath(rawPath: String): String {
        val clean = rawPath.trim()
        val osName = System.getProperty("os.name").orEmpty().lowercase(Locale.ROOT)
        return if (osName.contains("win") && clean.matches(Regex("(?i)^COM\\d+$"))) {
            "\\\\.\\$clean"
        } else {
            clean
        }
    }

    suspend fun writeEscPosBytesToConfiguredPrinter(printerBytes: ByteArray): Boolean {
        writeEscPosBytes?.let { customWriter ->
            return customWriter(printerBytes)
        }

        val path = escPosDevicePath?.trim()?.takeIf { it.isNotBlank() } ?: return false

        return withContext(Dispatchers.IO) {
            val file = File(normalizedDevicePath(path))
            file.outputStream().use { output ->
                output.write(printerBytes)
                output.flush()
            }
            true
        }
    }
}

object DesktopVoiceInputJvmBridge {
    /**
     * Optional desktop speech recognizer hook.
     * Plug a local engine such as Vosk/Whisper here and return the recognized text for the requested locale.
     */
    var recognizeOnce: (suspend (localeLanguage: String) -> String?)? = null
}

private fun desktopPermissionSettingsCommands(kind: PlatformPermissionKind): List<Array<String>> {
    val osName = System.getProperty("os.name").orEmpty().lowercase(Locale.ROOT)
    return when {
        osName.contains("mac") -> {
            val pane = when (kind) {
                PlatformPermissionKind.Camera -> "Privacy_Camera"
                PlatformPermissionKind.Microphone -> "Privacy_Microphone"
                PlatformPermissionKind.SpeechRecognition -> "Privacy_SpeechRecognition"
                PlatformPermissionKind.AppSettings -> "Privacy"
            }
            listOf(
                arrayOf("open", "x-apple.systempreferences:com.apple.preference.security?$pane"),
                arrayOf("open", "x-apple.systempreferences:com.apple.preference.security"),
                arrayOf("open", "-b", "com.apple.systempreferences")
            )
        }
        osName.contains("win") -> {
            val page = when (kind) {
                PlatformPermissionKind.Camera -> "ms-settings:privacy-webcam"
                PlatformPermissionKind.Microphone, PlatformPermissionKind.SpeechRecognition -> "ms-settings:privacy-microphone"
                PlatformPermissionKind.AppSettings -> "ms-settings:privacy"
            }
            listOf(
                arrayOf("cmd", "/c", "start", "", page),
                arrayOf("rundll32.exe", "shell32.dll,Control_RunDLL")
            )
        }
        else -> listOf(
            arrayOf("gnome-control-center", "privacy"),
            arrayOf("xdg-open", "settings://privacy"),
            arrayOf("xdg-open", "settings://")
        )
    }
}

private suspend fun openDesktopPermissionSettings(kind: PlatformPermissionKind): ReceiptPlatformActionResult =
    withContext(Dispatchers.IO) {
        var lastErrorMessage: String? = null
        for (command in desktopPermissionSettingsCommands(kind)) {
            val result = runCatching {
                Runtime.getRuntime().exec(command)
                ReceiptPlatformActionResult(true, "Permission settings opened")
            }
            val value = result.getOrNull()
            if (value != null) return@withContext value
            lastErrorMessage = result.exceptionOrNull()?.message
        }
        ReceiptPlatformActionResult(false, lastErrorMessage ?: "Could not open permission settings")
    }

fun installDesktopVoiceInputJvm() {
    isPlatformVoiceInputAvailable = { DesktopVoiceInputJvmBridge.recognizeOnce != null }

    getVoiceInputPermissionState = {
        if (DesktopVoiceInputJvmBridge.recognizeOnce == null) PlatformPermissionState.Unavailable else PlatformPermissionState.Granted
    }

    stopPlatformVoiceInput = { }
    startPlatformVoiceInput = start@{ texts, callbacks ->
        val recognizer = DesktopVoiceInputJvmBridge.recognizeOnce
        if (recognizer == null) {
            val osName = System.getProperty("os.name").orEmpty().lowercase()
            if (osName.contains("mac")) {
                openPlatformAppSettings?.invoke(PlatformPermissionKind.Microphone)
            }
            callbacks.onError("Desktop voice input engine is not configured. Microphone settings opened if supported.")
            callbacks.onFinished()
            return@start
        }

        callbacks.onAmplitude(0.30f)
        val languageCandidates = (listOf(texts.primaryLanguageTag) + texts.languageTags + Locale.getDefault().toLanguageTag())
            .map { it.substringBefore('-').trim().lowercase(Locale.ROOT) }
            .filter { it.isNotBlank() && it != "main" && it != "system" }
            .distinct()
            .ifEmpty { listOf(Locale.getDefault().language) }
        val result = withContext(Dispatchers.IO) {
            languageCandidates.firstNotNullOfOrNull { language ->
                runCatching { recognizer(language) }
                    .getOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { recognizedText -> language to recognizedText }
            }
        }
        if (result != null) {
            callbacks.onDetectedLanguage(result.first)
            callbacks.onFinalText(result.second)
        } else {
            callbacks.onError("Nothing was recognized")
        }
        callbacks.onFinished()
    }
}

fun installReceiptPlatformJvm() {
    fun writePdfToDownloads(fileName: String, pdfBytes: ByteArray): File {
        val downloads = File(System.getProperty("user.home"), "Downloads").takeIf { it.exists() && it.isDirectory }
            ?: File(System.getProperty("user.home"))
        val file = File(downloads, fileName.ifBlank { "receipt.pdf" })
        file.writeBytes(pdfBytes)
        return file
    }

    fun writePdfToTemp(fileName: String, pdfBytes: ByteArray): File {
        val safeName = fileName.ifBlank { "receipt.pdf" }
        val file = File(System.getProperty("java.io.tmpdir"), safeName)
        file.writeBytes(pdfBytes)
        return file
    }

    fun desktop(): Desktop? = if (Desktop.isDesktopSupported()) Desktop.getDesktop() else null

    saveReceiptPdfFile = { fileName, pdfBytes ->
        withContext(Dispatchers.IO) {
            runCatching {
                val file = writePdfToDownloads(fileName, pdfBytes)
                ReceiptPlatformActionResult(true, "Saved to ${file.absolutePath}")
            }.getOrElse {
                ReceiptPlatformActionResult(false, it.message ?: "Could not save PDF")
            }
        }
    }

    shareReceiptPdfFile = { fileName, pdfBytes, whatsappOnly ->
        withContext(Dispatchers.IO) {
            runCatching {
                val file = writePdfToTemp(fileName, pdfBytes)
                val desktop = desktop()
                if (whatsappOnly) {
                    val text = URLEncoder.encode("AITA receipt: ${file.absolutePath}", "UTF-8")
                    if (desktop != null && desktop.isSupported(Desktop.Action.BROWSE)) {
                        desktop.browse(URI("https://web.whatsapp.com/send?text=$text"))
                    }
                    if (desktop != null && desktop.isSupported(Desktop.Action.OPEN)) {
                        desktop.open(file)
                    }
                    ReceiptPlatformActionResult(true, "Opened WhatsApp Web and PDF")
                } else {
                    if (desktop != null && desktop.isSupported(Desktop.Action.OPEN)) {
                        desktop.open(file)
                        ReceiptPlatformActionResult(true, "Opened PDF")
                    } else {
                        ReceiptPlatformActionResult(true, "PDF created at ${file.absolutePath}")
                    }
                }
            }.getOrElse {
                ReceiptPlatformActionResult(false, it.message ?: "Could not share PDF")
            }
        }
    }

    printReceiptPlatformAction = { _, _, printerBytes ->
        withContext(Dispatchers.IO) {
            runCatching {
                if (ReceiptPlatformJvmBridge.writeEscPosBytesToConfiguredPrinter(printerBytes)) {
                    ReceiptPlatformActionResult(true, "Receipt sent to printer")
                } else {
                    ReceiptPlatformActionResult(false, "Desktop ESC/POS receipt printer is not configured")
                }
            }.getOrElse {
                ReceiptPlatformActionResult(false, it.message ?: "Could not print receipt")
            }
        }
    }

    printReceiptEscPosBytes = { printerBytes ->
        withContext(Dispatchers.IO) {
            runCatching {
                if (ReceiptPlatformJvmBridge.writeEscPosBytesToConfiguredPrinter(printerBytes)) {
                    ReceiptPlatformActionResult(true, "Receipt sent to printer")
                } else {
                    ReceiptPlatformActionResult(false, "Desktop ESC/POS receipt printer is not configured")
                }
            }.getOrElse {
                ReceiptPlatformActionResult(false, it.message ?: "Could not print receipt")
            }
        }
    }
}

fun loadOrCreateInstallationId(): String {
    val existing = try {
        keyring.getPassword(keyringService, "installation_id")?.takeIf { it.isNotBlank() }
    } catch (_: Throwable) {
        null
    }

    if (existing != null) return existing

    val fresh = UUID.randomUUID().toString()
    try {
        keyring.setPassword(keyringService, "installation_id", fresh)
    } catch (_: Throwable) { }

    return fresh
}

fun loadOrCreateKey(account: String): ByteArray {
    fallbackEncryptionKeys[account]?.let { return it }

    val keyFile = runCatching {
        cacheDirPath.takeIf { it.isNotBlank() }?.let { File(it, "$account.key") }
    }.getOrNull()

    val existingKey = runCatching {
        keyring.getPassword(keyringService, account)
            ?.let { Base64.getUrlDecoder().decode(it) }
            ?.takeIf { it.size == 32 }
    }.getOrNull()
        ?: runCatching {
            keyFile
                ?.takeIf { it.exists() }
                ?.readText()
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let { Base64.getUrlDecoder().decode(it) }
                ?.takeIf { it.size == 32 }
        }.getOrNull()

    if (existingKey != null) {
        fallbackEncryptionKeys[account] = existingKey
        return existingKey
    }

    val raw = ByteArray(32).also { rng.nextBytes(it) }
    val b64 = Base64.getUrlEncoder().withoutPadding().encodeToString(raw)

    runCatching { keyring.setPassword(keyringService, account, b64) }
        .onFailure {
            runCatching {
                keyFile?.parentFile?.mkdirs()
                keyFile?.writeText(b64)
            }
        }

    fallbackEncryptionKeys[account] = raw
    return raw
}

fun main() {
    cacheDirPath = Files.createDirectories(
        System.getProperty("os.name").lowercase().run {
            when {
                contains("win") -> {
                    val base = System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home")
                    Paths.get(
                        base,
                        ".aita",
                        "Cache"
                    )
                }

                contains("mac") -> {
                    Paths.get(
                        System.getProperty("user.home"),
                        ".aita",
                        "Caches",
                        "AITA"
                    )
                }

                else -> {
                    Paths.get(
                        System.getenv("XDG_CACHE_HOME") ?: "${System.getProperty("user.home")}/.cache",
                        ".aita"
                    )
                }
            }
        }
    ).toFile().absolutePath

    installReceiptPlatformJvm()
    installDesktopVoiceInputJvm()

    val cacheFile = File(cacheDirPath, "ua.bin")

    getStoredUserAuthTokens = {
        try {
            val raw = keyring.getPassword(keyringService, "auth_tokens")
            jsonBase.decodeFromString<TokenPair>(raw)
        } catch (_: Throwable) {
            null
        }
    }
    setStoredUserAuthTokens = {
        if (it == null) {
            try {
                keyring.deletePassword(keyringService, "auth_tokens")
            } catch (_: Throwable) { }
        } else {
            try {
                val payload = jsonBase.encodeToString(it)
                keyring.setPassword(keyringService, "auth_tokens", payload)
            } catch (_: Throwable) { }
        }
    }
    getStoredUserAccountDataModel = {
        try {
            if (!cacheFile.exists())
                null
            else {
                val key = loadOrCreateKey("user_account_encryption_key")

                val blob = cacheFile.readBytes()
                if (blob.size < 13)
                    null
                else {
                    val iv = blob.copyOfRange(0, 12)
                    val ct = blob.copyOfRange(12, blob.size)

                    runCatching {
                        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
                        }

                        val plain = cipher.doFinal(ct)
                        jsonBase.decodeFromString<UserAccountDataModel>(plain.toString(Charsets.UTF_8))
                    }.getOrNull()
                }
            }
        } catch (_: Throwable) {
            null
        }
    }
    setStoredUserAccountDataModel = { value ->
        try {
            if (value == null) {
                try {
                    cacheFile.delete()
                    keyring.deletePassword(keyringService, "user_account_encryption_key")
                } catch (_: Throwable) { }
            } else {
                val key = loadOrCreateKey("user_account_encryption_key")
                val plain = jsonBase.encodeToString(value).toByteArray(Charsets.UTF_8)
                val iv = ByteArray(12).also { rng.nextBytes(it) }

                val cipher = Cipher
                    .getInstance("AES/GCM/NoPadding")
                    .apply {
                        init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
                    }

                val ct = cipher.doFinal(plain)

                val tmp = File(cacheFile.parentFile, cacheFile.name + ".tmp")
                tmp.writeBytes(iv + ct)

                try {
                    Files.move(
                        tmp.toPath(),
                        cacheFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                    )
                } catch (_: Throwable) {
                    Files.move(
                        tmp.toPath(),
                        cacheFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING
                    )
                }
            }
        } catch (_: Throwable) {
        }
    }

    setClipboardText = { text ->
        val selection = StringSelection(text)
        Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, null)
    }

    openPlatformAppSettings = { kind ->
        openDesktopPermissionSettings(kind)
    }

    openSystemDevicesSettings = {
        withContext(Dispatchers.IO) {
            runCatching {
                val osName = System.getProperty("os.name").orEmpty().lowercase()
                when {
                    osName.contains("mac") -> {
                        Runtime.getRuntime().exec(arrayOf("open", "x-apple.systempreferences:com.apple.BluetoothSettings"))
                    }
                    osName.contains("win") -> {
                        Runtime.getRuntime().exec(arrayOf("cmd", "/c", "start", "ms-settings:bluetooth"))
                    }
                    else -> {
                        runCatching { Runtime.getRuntime().exec(arrayOf("blueman-manager")) }
                            .getOrElse { Runtime.getRuntime().exec(arrayOf("gnome-control-center", "bluetooth")) }
                    }
                }
                ReceiptPlatformActionResult(true, "Device settings opened")
            }.getOrElse {
                ReceiptPlatformActionResult(false, it.message ?: "Could not open device settings")
            }
        }
    }

    getClientDeviceInfo = {
        val hostName = runCatching { java.net.InetAddress.getLocalHost().hostName }.getOrNull()
        val userName = System.getProperty("user.name").orEmpty()
        val osName = System.getProperty("os.name").orEmpty()
        val osVersion = System.getProperty("os.version").orEmpty()
        val arch = System.getProperty("os.arch").orEmpty()
        val deviceName = hostName
            ?.takeIf { it.isNotBlank() }
            ?: userName.takeIf { it.isNotBlank() }?.let { "$it desktop" }
            ?: "Desktop device"

        ClientDeviceInfoDataModel(
            installationId = loadOrCreateInstallationId(),
            deviceName = deviceName,
            platformName = "Desktop JVM",
            osName = listOf(osName, osVersion, arch).filter { it.isNotBlank() }.joinToString(" • "),
            appName = "AITA",
            appVersion = "desktop",
            localeLanguage = Locale.getDefault().language
        )
    }

    init()

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "AITA",
            icon = painterResource("drawable/app_icon.ico")
        ) {
            AppConfiguration(
                content = {
                    MainScreen()

                }
            )
        }
    }
}

