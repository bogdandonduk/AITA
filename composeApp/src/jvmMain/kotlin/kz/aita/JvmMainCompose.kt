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
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.security.SecureRandom
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

private val keyringService = "aita_keyring"
private val keyring: Keyring = Keyring.create()

private val rng = SecureRandom()

object ReceiptPlatformJvmBridge {
    /**
     * Optional desktop ESC/POS writer. Configure it for a USB serial, COM port, or network printer.
     */
    var writeEscPosBytes: (suspend (ByteArray) -> Boolean)? = null
}

fun installReceiptPlatformJvm() {
    saveReceiptPdfFile = { fileName, pdfBytes ->
        withContext(Dispatchers.IO) {
            runCatching {
                val downloads = File(System.getProperty("user.home"), "Downloads").takeIf { it.exists() }
                    ?: File(System.getProperty("user.home"))
                val file = File(downloads, fileName)
                file.writeBytes(pdfBytes)
                ReceiptPlatformActionResult(true, "Saved to ${file.absolutePath}")
            }.getOrElse {
                ReceiptPlatformActionResult(false, it.message ?: "Could not save PDF")
            }
        }
    }

    shareReceiptPdfFile = { fileName, pdfBytes, whatsappOnly ->
        withContext(Dispatchers.IO) {
            runCatching {
                val file = File(System.getProperty("java.io.tmpdir"), fileName)
                file.writeBytes(pdfBytes)
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(file)
                    ReceiptPlatformActionResult(true, if (whatsappOnly) "Opened PDF; send it through WhatsApp Desktop manually" else "Opened PDF")
                } else {
                    ReceiptPlatformActionResult(true, "PDF created at ${file.absolutePath}")
                }
            }.getOrElse {
                ReceiptPlatformActionResult(false, it.message ?: "Could not share PDF")
            }
        }
    }

    printReceiptEscPosBytes = { printerBytes ->
        runCatching {
            val writer = ReceiptPlatformJvmBridge.writeEscPosBytes
                ?: return@runCatching ReceiptPlatformActionResult(false, "No desktop ESC/POS printer writer is configured")

            if (writer(printerBytes)) {
                ReceiptPlatformActionResult(true, "Sent to printer")
            } else {
                ReceiptPlatformActionResult(false, "Printer rejected the receipt")
            }
        }.getOrElse {
            ReceiptPlatformActionResult(false, it.message ?: "Could not print receipt")
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
    val existingKey = try {
        keyring.getPassword(keyringService, account)
            ?.let { Base64.getUrlDecoder().decode(it) }
            ?.takeIf { it.size == 32 }
    } catch (thr: Throwable) {
        thr.printStackTrace()
        null
    }

    if (existingKey != null) return existingKey

    val raw = ByteArray(32).also { rng.nextBytes(it) }
    val b64 = Base64.getUrlEncoder().withoutPadding().encodeToString(raw)
    keyring.setPassword(keyringService, account, b64)

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
        } catch (throwable: Throwable) {
            throwable.printStackTrace()
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
        } catch (thr: Throwable) {
            thr.printStackTrace()
        }
    }

    setClipboardText = { text ->
        val selection = StringSelection(text)
        Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, null)
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

