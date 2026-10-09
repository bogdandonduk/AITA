// THIS IS JvmMainCompose.kt - in jvmMain compose module of kmp compose app

package kz.aita

import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.github.javakeyring.Keyring
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import java.awt.Desktop
import java.awt.GraphicsEnvironment
import java.awt.Taskbar
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.PrintStream
import java.net.URI
import java.net.URLEncoder
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermissions
import java.security.SecureRandom
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.imageio.ImageIO
import javax.print.DocFlavor
import javax.print.PrintServiceLookup
import javax.print.SimpleDoc
import kotlin.Array
import kotlin.Boolean
import kotlin.ByteArray
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Throwable
import kotlin.addSuppressed
import kotlin.also
import kotlin.apply
import kotlin.arrayOf
import kotlin.check
import kotlin.collections.List
import kotlin.collections.any
import kotlin.collections.buildList
import kotlin.collections.contains
import kotlin.collections.copyOf
import kotlin.collections.copyOfRange
import kotlin.collections.distinct
import kotlin.collections.distinctBy
import kotlin.collections.emptyList
import kotlin.collections.filter
import kotlin.collections.first
import kotlin.collections.firstNotNullOfOrNull
import kotlin.collections.firstOrNull
import kotlin.collections.forEach
import kotlin.collections.ifEmpty
import kotlin.collections.isEmpty
import kotlin.collections.joinToString
import kotlin.collections.listOf
import kotlin.collections.listOfNotNull
import kotlin.collections.map
import kotlin.collections.mapNotNull
import kotlin.collections.none
import kotlin.collections.orEmpty
import kotlin.collections.plus
import kotlin.collections.remove
import kotlin.collections.set
import kotlin.collections.setOf
import kotlin.collections.sortedWith
import kotlin.collections.toString
import kotlin.compareTo
import kotlin.error
import kotlin.getOrDefault
import kotlin.getOrElse
import kotlin.getValue
import kotlin.invoke
import kotlin.io.bufferedReader
import kotlin.io.bufferedWriter
import kotlin.io.inputStream
import kotlin.io.outputStream
import kotlin.io.println
import kotlin.io.readBytes
import kotlin.io.readLines
import kotlin.io.readText
import kotlin.io.use
import kotlin.lazy
import kotlin.let
import kotlin.onFailure
import kotlin.onSuccess
import kotlin.ranges.downTo
import kotlin.ranges.first
import kotlin.runCatching
import kotlin.sequences.any
import kotlin.sequences.first
import kotlin.sequences.firstNotNullOfOrNull
import kotlin.sequences.firstOrNull
import kotlin.sequences.mapNotNull
import kotlin.sequences.none
import kotlin.sequences.sequenceOf
import kotlin.synchronized
import kotlin.takeIf
import kotlin.text.Charsets
import kotlin.text.Regex
import kotlin.text.any
import kotlin.text.buildString
import kotlin.text.contains
import kotlin.text.endsWith
import kotlin.text.equals
import kotlin.text.first
import kotlin.text.firstNotNullOfOrNull
import kotlin.text.get
import kotlin.text.ifBlank
import kotlin.text.isNotBlank
import kotlin.text.isNullOrBlank
import kotlin.text.isWhitespace
import kotlin.text.lowercase
import kotlin.text.matches
import kotlin.text.none
import kotlin.text.orEmpty
import kotlin.text.removeSurrounding
import kotlin.text.replace
import kotlin.text.set
import kotlin.text.startsWith
import kotlin.text.substringAfter
import kotlin.text.substringAfterLast
import kotlin.text.substringBefore
import kotlin.text.substringBeforeLast
import kotlin.text.take
import kotlin.text.toByteArray
import kotlin.text.trim
import kotlin.text.trimStart
import kotlin.to
import kotlin.use

private val keyringService = "aita_keyring"
private val keyring: Keyring by lazy { Keyring.create() }

private val rng = SecureRandom()
private val fallbackEncryptionKeys = ConcurrentHashMap<String, ByteArray>()

private const val JVM_SECURE_STORE_DIR = "secure"
private const val JVM_SECRET_DELETED_SENTINEL = "AITA_SECRET_DELETED_V1"

private const val DESKTOP_DIAGNOSTIC_LOG_FILE_NAME = "aita-desktop.log"
private const val DESKTOP_DIAGNOSTIC_LOG_MAX_BYTES = 8L * 1024L * 1024L
private const val DESKTOP_DIAGNOSTIC_LOG_BACKUP_COUNT = 4

private enum class DesktopOsFamily {
    Windows,
    MacOS,
    Linux,
    Other
}

private val desktopOsFamily: DesktopOsFamily by lazy {
    val osName = System.getProperty("os.name").orEmpty().lowercase(Locale.ROOT)
    when {
        osName.contains("win") -> DesktopOsFamily.Windows
        osName.contains("mac") || osName.contains("darwin") -> DesktopOsFamily.MacOS
        osName.contains("linux") -> DesktopOsFamily.Linux
        else -> DesktopOsFamily.Other
    }
}

private fun desktopPlatformDisplayName(): String = when (desktopOsFamily) {
    DesktopOsFamily.Windows -> "Windows"
    DesktopOsFamily.MacOS -> "macOS"
    DesktopOsFamily.Linux -> "Linux"
    DesktopOsFamily.Other -> "Desktop JVM"
}

private fun configureDesktopApplicationIdentity() {
    // Set these before Compose/AWT creates the first native window. This keeps direct Kotlin-main
    // launches and Gradle debug runs visually aligned with packaged AITA applications.
    System.setProperty("apple.awt.application.name", "AITA")
    System.setProperty("com.apple.mrj.application.apple.menu.about.name", "AITA")

    if (desktopOsFamily != DesktopOsFamily.MacOS) return

    runCatching {
        if (!Taskbar.isTaskbarSupported()) return@runCatching
        val taskbar = Taskbar.getTaskbar()
        if (!taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) return@runCatching
        val iconStream = Thread.currentThread().contextClassLoader
            .getResourceAsStream("drawable/app_icon.png")
            ?: return@runCatching
        val iconImage = iconStream.use(ImageIO::read) ?: return@runCatching
        taskbar.iconImage = iconImage
    }.onFailure { throwable ->
        System.err.println("AITA desktop icon could not be applied: ${throwable.message}")
    }
}

private fun desktopUserHome(): File = File(
    System.getProperty("user.home").orEmpty().takeIf { it.isNotBlank() } ?: "."
)

private fun ensureDesktopDirectory(directory: File, ownerOnly: Boolean = false): File {
    if (!directory.exists() && !directory.mkdirs() && !directory.exists()) {
        error("Could not create AITA desktop directory '${directory.absolutePath}'")
    }
    if (!directory.isDirectory) {
        error("AITA desktop path '${directory.absolutePath}' is not a directory")
    }
    if (ownerOnly) hardenOwnerOnlyPermissions(directory, directory = true)
    return directory
}

private fun hardenOwnerOnlyPermissions(file: File, directory: Boolean) {
    if (!file.exists()) return
    val posixPermissions = if (directory) {
        PosixFilePermissions.fromString("rwx------")
    } else {
        PosixFilePermissions.fromString("rw-------")
    }
    val posixApplied = runCatching {
        Files.setPosixFilePermissions(file.toPath(), posixPermissions)
        true
    }.getOrDefault(false)

    if (!posixApplied) {
        runCatching {
            file.setReadable(false, false)
            file.setWritable(false, false)
            file.setExecutable(false, false)
            file.setReadable(true, true)
            file.setWritable(true, true)
            if (directory) file.setExecutable(true, true)
        }
    }
}

private fun desktopAitaCacheRootDir(): File {
    val home = desktopUserHome()
    return ensureDesktopDirectory(
        when (desktopOsFamily) {
            DesktopOsFamily.Windows -> File(System.getenv("LOCALAPPDATA") ?: home.absolutePath, "AITA/Cache")
            DesktopOsFamily.MacOS -> File(home, "Library/Caches/AITA")
            DesktopOsFamily.Linux -> File(
                System.getenv("XDG_CACHE_HOME") ?: File(home, ".cache").absolutePath,
                "aita"
            )
            DesktopOsFamily.Other -> File(home, ".aita/cache")
        }
    )
}

private fun desktopAitaDataRootDir(): File {
    val home = desktopUserHome()
    return ensureDesktopDirectory(
        when (desktopOsFamily) {
            DesktopOsFamily.Windows -> File(System.getenv("LOCALAPPDATA") ?: home.absolutePath, "AITA/Data")
            DesktopOsFamily.MacOS -> File(home, "Library/Application Support/AITA")
            DesktopOsFamily.Linux -> File(
                System.getenv("XDG_DATA_HOME") ?: File(home, ".local/share").absolutePath,
                "aita"
            )
            DesktopOsFamily.Other -> File(home, ".aita/data")
        },
        ownerOnly = true
    )
}

private fun legacyDesktopMixedDataRootDir(): File {
    val home = desktopUserHome()
    return when (desktopOsFamily) {
        DesktopOsFamily.Windows -> File(System.getenv("LOCALAPPDATA") ?: home.absolutePath, ".aita/Cache")
        DesktopOsFamily.MacOS -> File(home, ".aita/Caches/AITA")
        DesktopOsFamily.Linux -> File(
            System.getenv("XDG_CACHE_HOME") ?: File(home, ".cache").absolutePath,
            ".aita"
        )
        DesktopOsFamily.Other -> File(home, ".aita")
    }
}

private fun copyDesktopFile(source: java.nio.file.Path, destination: java.nio.file.Path) {
    try {
        Files.copy(source, destination, StandardCopyOption.COPY_ATTRIBUTES)
    } catch (attributeCopyFailure: Throwable) {
        try {
            Files.deleteIfExists(destination)
            Files.copy(source, destination)
        } catch (fallbackCopyFailure: Throwable) {
            fallbackCopyFailure.addSuppressed(attributeCopyFailure)
            throw fallbackCopyFailure
        }
    }
}

private fun copyDesktopPathIfMissing(source: File, destination: File) {
    if (!source.exists() || destination.exists()) return
    if (source.isDirectory) {
        Files.walk(source.toPath()).use { paths ->
            paths.forEach { sourcePath ->
                val relative = source.toPath().relativize(sourcePath)
                val targetPath = destination.toPath().resolve(relative)
                when {
                    Files.isDirectory(sourcePath, LinkOption.NOFOLLOW_LINKS) -> Files.createDirectories(targetPath)
                    Files.isRegularFile(sourcePath, LinkOption.NOFOLLOW_LINKS) -> {
                        Files.createDirectories(targetPath.parent)
                        copyDesktopFile(sourcePath, targetPath)
                        hardenOwnerOnlyPermissions(targetPath.toFile(), directory = false)
                    }
                }
            }
        }
        hardenOwnerOnlyPermissions(destination, directory = true)
    } else if (source.isFile) {
        destination.parentFile?.let { ensureDesktopDirectory(it, ownerOnly = true) }
        copyDesktopFile(source.toPath(), destination.toPath())
        hardenOwnerOnlyPermissions(destination, directory = false)
    }
}

private fun migrateLegacyDesktopPersistentState(dataRoot: File) {
    val legacyRoot = legacyDesktopMixedDataRootDir()
    if (!legacyRoot.exists() || legacyRoot.toPath() == dataRoot.toPath()) return

    val persistentNames = listOf(
        "app_database.db",
        "app_database.db-journal",
        "app_database.db-shm",
        "app_database.db-wal",
        "ui_drafts",
        "ua.bin",
        "user_account_encryption_key.key",
        "aita_receipt_printer_device.txt",
        "aita_label_printer.properties"
    )
    var migratedAny = false
    persistentNames.forEach { name ->
        val source = File(legacyRoot, name)
        val destination = File(dataRoot, name)
        if (source.exists() && !destination.exists()) {
            runCatching { copyDesktopPathIfMissing(source, destination) }
                .onSuccess {
                    hardenOwnerOnlyPermissions(source, directory = source.isDirectory)
                    migratedAny = true
                }
                .onFailure { throwable ->
                    System.err.println(
                        "AITA desktop legacy data migration skipped '$name': ${throwable.message}"
                    )
                }
        }
    }
    if (migratedAny) {
        println(
            "AITA desktop migrated durable state from '${legacyRoot.absolutePath}' " +
                "to '${dataRoot.absolutePath}'. The legacy cache copy was retained for safety."
        )
    }
}

private fun desktopGuiUnavailableReason(): String? {
    val explicitlyHeadless = System.getProperty("java.awt.headless")
        ?.trim()
        ?.equals("true", ignoreCase = true) == true
    val headlessResult = runCatching { GraphicsEnvironment.isHeadless() }
    val headlessFailure = headlessResult.exceptionOrNull()
    if (headlessFailure != null) {
        return "Java could not initialize its graphical environment: ${headlessFailure.message}"
    }
    if (!explicitlyHeadless && headlessResult.getOrDefault(true).not()) return null

    val display = System.getenv("DISPLAY").orEmpty().ifBlank { "<missing>" }
    val waylandDisplay = System.getenv("WAYLAND_DISPLAY").orEmpty().ifBlank { "<missing>" }
    val sessionType = System.getenv("XDG_SESSION_TYPE").orEmpty().ifBlank { "<unknown>" }
    val javaHome = System.getProperty("java.home").orEmpty().ifBlank { "<unknown>" }
    val linuxAwtLibrary = if (desktopOsFamily == DesktopOsFamily.Linux) {
        File(javaHome, "lib/libawt_xawt.so").takeIf { it.isFile }?.absolutePath ?: "<missing>"
    } else {
        "not-applicable"
    }
    return buildString {
        append("AITA desktop UI is running in a headless Java/session environment. ")
        append("DISPLAY=$display, WAYLAND_DISPLAY=$waylandDisplay, XDG_SESSION_TYPE=$sessionType, ")
        append("java.home=$javaHome, libawt_xawt=$linuxAwtLibrary")
    }
}

private fun verifyDesktopGuiRuntime(): Boolean {
    val reason = desktopGuiUnavailableReason() ?: return true
    System.err.println("AITA desktop UI cannot start: $reason")
    if (desktopOsFamily == DesktopOsFamily.Linux) {
        System.err.println(
            "AITA Linux fix: install the headful JDK with 'sudo apt install openjdk-21-jdk', " +
                "run './gradlew --stop', then launch IntelliJ/Gradle from the logged-in Ubuntu desktop session. " +
                "Do not launch the Compose UI from systemd, sudo, or a plain SSH session without graphical forwarding."
        )
    }
    return false
}

private fun launchDesktopCommand(vararg command: String): Boolean = runCatching {
    ProcessBuilder(*command)
        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
        .redirectError(ProcessBuilder.Redirect.DISCARD)
        .start()
    true
}.getOrDefault(false)

private fun writeTextToDesktopCommand(text: String, vararg command: String): Boolean = runCatching {
    val process = ProcessBuilder(*command)
        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
        .redirectError(ProcessBuilder.Redirect.DISCARD)
        .start()
    process.outputStream.bufferedWriter(Charsets.UTF_8).use { writer -> writer.write(text) }
    if (!process.waitFor(3, TimeUnit.SECONDS)) {
        process.destroyForcibly()
        return@runCatching false
    }
    process.exitValue() == 0
}.getOrDefault(false)

private fun copyTextToDesktopClipboard(text: String): Boolean {
    val awtCopied = runCatching {
        val selection = StringSelection(text)
        Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, null)
        true
    }.getOrDefault(false)
    if (awtCopied) return true

    return when (desktopOsFamily) {
        DesktopOsFamily.MacOS -> writeTextToDesktopCommand(text, "pbcopy")
        DesktopOsFamily.Windows -> writeTextToDesktopCommand(text, "cmd", "/c", "clip")
        DesktopOsFamily.Linux -> {
            (System.getenv("WAYLAND_DISPLAY").orEmpty().isNotBlank() &&
                writeTextToDesktopCommand(text, "wl-copy")) ||
                writeTextToDesktopCommand(text, "xclip", "-selection", "clipboard") ||
                writeTextToDesktopCommand(text, "xsel", "--clipboard", "--input")
        }
        DesktopOsFamily.Other -> false
    }
}

private fun writeDesktopBytesAtomically(file: File, bytes: ByteArray, ownerOnly: Boolean) {
    val parent = file.parentFile ?: error("AITA file '${file.absolutePath}' has no parent directory")
    ensureDesktopDirectory(parent, ownerOnly = ownerOnly)
    val temporary = File(parent, ".${file.name}.${UUID.randomUUID()}.tmp")
    try {
        Files.write(
            temporary.toPath(),
            bytes,
            StandardOpenOption.CREATE_NEW,
            StandardOpenOption.WRITE
        )
        if (ownerOnly) hardenOwnerOnlyPermissions(temporary, directory = false)
        try {
            Files.move(
                temporary.toPath(),
                file.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (atomicMoveFailure: Throwable) {
            try {
                Files.move(
                    temporary.toPath(),
                    file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (fallbackMoveFailure: Throwable) {
                fallbackMoveFailure.addSuppressed(atomicMoveFailure)
                throw fallbackMoveFailure
            }
        }
        if (ownerOnly) hardenOwnerOnlyPermissions(file, directory = false)
    } finally {
        runCatching { Files.deleteIfExists(temporary.toPath()) }
    }
}

private fun desktopCommandOutput(timeoutSeconds: Long, vararg command: String): String? = runCatching {
    val process = ProcessBuilder(*command)
        .redirectErrorStream(true)
        .start()
    if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
        process.destroyForcibly()
        return@runCatching null
    }
    process.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
        reader.readText().trim().takeIf { it.isNotBlank() }
    }
}.getOrNull()

private fun linuxXdgDownloadDirectory(): File? {
    val commandValue = desktopCommandOutput(2, "xdg-user-dir", "DOWNLOAD")
        ?.takeIf { it != desktopUserHome().absolutePath }
    if (commandValue != null) return File(commandValue)

    val configHome = System.getenv("XDG_CONFIG_HOME") ?: File(desktopUserHome(), ".config").absolutePath
    val userDirsFile = File(configHome, "user-dirs.dirs")
    return runCatching {
        userDirsFile
            .takeIf { it.isFile }
            ?.readLines(Charsets.UTF_8)
            ?.firstOrNull { it.trimStart().startsWith("XDG_DOWNLOAD_DIR=") }
            ?.substringAfter('=')
            ?.trim()
            ?.removeSurrounding("\"")
            ?.replace("\$HOME", desktopUserHome().absolutePath)
            ?.let(::File)
    }.getOrNull()
}

private fun desktopDownloadsDirectory(): File {
    val home = desktopUserHome()
    val candidate = when (desktopOsFamily) {
        DesktopOsFamily.Linux -> linuxXdgDownloadDirectory()
        else -> File(home, "Downloads")
    }
    return candidate
        ?.takeIf { (it.exists() && it.isDirectory) || it.mkdirs() }
        ?: home
}

private fun desktopEnvOrSystem(name: String): String? =
    System.getenv(name)?.takeIf { it.isNotBlank() }
        ?: System.getProperty(name)?.takeIf { it.isNotBlank() }

private fun desktopAitaLogRootDir(): File {
    desktopEnvOrSystem("AITA_CLIENT_LOG_DIR")?.let { configured ->
        return ensureDesktopDirectory(File(configured), ownerOnly = true)
    }

    val home = desktopUserHome()
    val directory = when (desktopOsFamily) {
        DesktopOsFamily.Windows -> File(System.getenv("LOCALAPPDATA") ?: home.absolutePath, "AITA/Logs")
        DesktopOsFamily.MacOS -> File(home, "Library/Logs/AITA")
        DesktopOsFamily.Linux -> File(
            System.getenv("XDG_STATE_HOME") ?: File(home, ".local/state").absolutePath,
            "aita/logs"
        )
        DesktopOsFamily.Other -> File(home, ".aita/logs")
    }
    return ensureDesktopDirectory(directory, ownerOnly = true)
}

private fun rotateDesktopDiagnosticLogIfNeeded(logFile: File) {
    runCatching {
        if (!logFile.exists() || logFile.length() < DESKTOP_DIAGNOSTIC_LOG_MAX_BYTES) return
        for (index in DESKTOP_DIAGNOSTIC_LOG_BACKUP_COUNT downTo 1) {
            val source = if (index == 1) logFile else File(logFile.parentFile, "${logFile.name}.${index - 1}")
            val target = File(logFile.parentFile, "${logFile.name}.$index")
            if (target.exists()) target.delete()
            if (source.exists()) source.renameTo(target)
        }
    }
}

private class DesktopTeeOutputStream(
    private val primary: OutputStream,
    private val secondary: OutputStream
) : OutputStream() {
    override fun write(b: Int) = synchronized(secondary) {
        primary.write(b)
        secondary.write(b)
    }

    override fun write(b: ByteArray, off: Int, len: Int) = synchronized(secondary) {
        primary.write(b, off, len)
        secondary.write(b, off, len)
    }

    override fun flush() = synchronized(secondary) {
        primary.flush()
        secondary.flush()
    }
}

private fun installDesktopDiagnosticLogging() {
    val originalOut = System.out
    val originalErr = System.err
    val logFile = runCatching {
        val file = File(desktopAitaLogRootDir(), DESKTOP_DIAGNOSTIC_LOG_FILE_NAME)
        file.parentFile?.mkdirs()
        rotateDesktopDiagnosticLogIfNeeded(file)
        if (!file.exists()) file.createNewFile()
        hardenOwnerOnlyPermissions(file, directory = false)
        file
    }.getOrNull()

    if (logFile != null) {
        runCatching {
            val logStream = FileOutputStream(logFile, true)
            System.setOut(PrintStream(DesktopTeeOutputStream(originalOut, logStream), true, Charsets.UTF_8.name()))
            System.setErr(PrintStream(DesktopTeeOutputStream(originalErr, logStream), true, Charsets.UTF_8.name()))
        }.onFailure { throwable ->
            originalErr.println("AITA desktop diagnostic logging failed: ${throwable.message}")
        }
    }

    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        System.err.println("AITA desktop uncaught exception in thread '${thread.name}': ${throwable::class.qualifiedName}: ${throwable.message}")
        throwable.printStackTrace(System.err)
    }

    println("AITA desktop diagnostic log: ${logFile?.absolutePath ?: "unavailable"}")
    println("AITA desktop cache dir: $cacheDirPath")
    println("AITA desktop persistent data dir: $jvmPersistentDataDirPath")
    println("AITA desktop runtime: java=${System.getProperty("java.version")} os=${System.getProperty("os.name")} ${System.getProperty("os.version")} arch=${System.getProperty("os.arch")}")
}

private fun configureClientServerUrlOverrideFromEnvironment() {
    val bootstrapOverride = desktopEnvOrSystem("AITA_CLIENT_BOOTSTRAP_URLS")
        ?: desktopEnvOrSystem("AITA_CLIENT_BOOTSTRAP_URL")
        ?: desktopEnvOrSystem("AITA_BOOTSTRAP_URL")
    if (!bootstrapOverride.isNullOrBlank()) {
        setRuntimeClientBootstrapUrlsOverride(bootstrapOverride)
    }

    val envOverrideEnabled = desktopEnvOrSystem("AITA_ENABLE_CLIENT_SERVER_URL_ENV_OVERRIDE")
        ?.toJvmBooleanLenientOrNull()
        ?: false
    if (!envOverrideEnabled) {
        println("AITA desktop server URL env override disabled; using the single Cloudflare workers.dev production gateway and /config/global global.json.")
        return
    }

    val override = desktopEnvOrSystem("AITA_CLIENT_SERVER_URL")
        ?: desktopEnvOrSystem("AITA_SERVER_URL")
        ?: return
    setHiddenClientServerUrlResolutionEnabled(true)
    setRuntimeClientServerUrlOverride(override)
}

private fun String.toJvmBooleanLenientOrNull(): Boolean? = when (trim().lowercase(Locale.ROOT)) {
    "true", "1", "yes", "y", "on" -> true
    "false", "0", "no", "n", "off" -> false
    else -> null
}

private val reportedKeyringFailures = ConcurrentHashMap.newKeySet<String>()

private fun jvmKeyringEnabled(): Boolean =
    (System.getenv("AITA_ENABLE_KEYRING") ?: System.getProperty("AITA_ENABLE_KEYRING"))
        ?.toJvmBooleanLenientOrNull()
        ?: true

private fun reportKeyringFailureOnce(operation: String, throwable: Throwable) {
    val signature = "$operation:${throwable::class.qualifiedName}:${throwable.message}"
    if (reportedKeyringFailures.add(signature)) {
        System.err.println(
            "AITA desktop OS keyring $operation unavailable; using owner-only local storage: " +
                (throwable.message ?: throwable::class.simpleName.orEmpty())
        )
    }
}

private fun secureStoreRootDir(): File? = runCatching {
    val home = desktopUserHome()
    val base = when (desktopOsFamily) {
        DesktopOsFamily.Windows -> File(System.getenv("APPDATA") ?: home.absolutePath, "AITA")
        DesktopOsFamily.MacOS -> File(home, "Library/Application Support/AITA")
        DesktopOsFamily.Linux -> File(
            System.getenv("XDG_CONFIG_HOME") ?: File(home, ".config").absolutePath,
            "aita"
        )
        DesktopOsFamily.Other -> File(home, ".aita")
    }
    ensureDesktopDirectory(File(base, JVM_SECURE_STORE_DIR), ownerOnly = true)
}.onFailure { throwable ->
    System.err.println("AITA desktop secure-store directory is unavailable: ${throwable.message}")
}.getOrNull()

private fun legacySecureStoreRootDir(): File? = when (desktopOsFamily) {
    DesktopOsFamily.MacOS -> File(desktopUserHome(), ".aita/AITA/$JVM_SECURE_STORE_DIR")
    else -> null
}

private fun secureAccountFileName(account: String): String = account
    .replace(Regex("[^A-Za-z0-9._-]+"), "_")
    .trim('_')
    .ifBlank { "secret" } + ".txt"

private fun secureStoreFile(account: String): File? =
    secureStoreRootDir()?.let { File(it, secureAccountFileName(account)) }

private fun legacySecureStoreFile(account: String): File? =
    legacySecureStoreRootDir()?.let { File(it, secureAccountFileName(account)) }

private fun readJvmSecretFile(file: File?): String? {
    val candidate = file ?: return null
    val path = candidate.toPath()
    if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) return null
    return runCatching {
        hardenOwnerOnlyPermissions(candidate, directory = false)
        candidate.readText(Charsets.UTF_8).trim().takeIf { it.isNotBlank() }
    }.getOrNull()
}

private fun writeJvmSecretFile(account: String, value: String): Boolean = runCatching {
    val file = secureStoreFile(account) ?: return@runCatching false
    val parent = file.parentFile ?: return@runCatching false
    ensureDesktopDirectory(parent, ownerOnly = true)
    val temporary = File(parent, ".${file.name}.${UUID.randomUUID()}.tmp")
    try {
        Files.writeString(
            temporary.toPath(),
            value,
            Charsets.UTF_8,
            StandardOpenOption.CREATE_NEW,
            StandardOpenOption.WRITE
        )
        hardenOwnerOnlyPermissions(temporary, directory = false)
        try {
            Files.move(
                temporary.toPath(),
                file.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (atomicMoveFailure: Throwable) {
            try {
                Files.move(
                    temporary.toPath(),
                    file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (fallbackMoveFailure: Throwable) {
                fallbackMoveFailure.addSuppressed(atomicMoveFailure)
                throw fallbackMoveFailure
            }
        }
        hardenOwnerOnlyPermissions(file, directory = false)
        true
    } finally {
        runCatching { Files.deleteIfExists(temporary.toPath()) }
    }
}.onFailure { throwable ->
    System.err.println("AITA desktop secure-file write failed for '$account': ${throwable.message}")
}.getOrDefault(false)

private fun readJvmKeyringSecret(account: String): String? {
    if (!jvmKeyringEnabled()) return null
    return runCatching {
        keyring.getPassword(keyringService, account)?.trim()?.takeIf { it.isNotBlank() }
    }.onFailure { reportKeyringFailureOnce("read", it) }.getOrNull()
}

private fun writeJvmKeyringSecret(account: String, value: String): Boolean {
    if (!jvmKeyringEnabled()) return false
    return runCatching {
        keyring.setPassword(keyringService, account, value)
        true
    }.onFailure { reportKeyringFailureOnce("write", it) }.getOrDefault(false)
}

private fun deleteJvmKeyringSecret(account: String) {
    if (!jvmKeyringEnabled()) return
    runCatching { keyring.deletePassword(keyringService, account) }
        .onFailure { reportKeyringFailureOnce("delete", it) }
}

private fun readJvmSecret(account: String): String? {
    val fallbackFile = listOfNotNull(secureStoreFile(account), legacySecureStoreFile(account))
        .firstOrNull { Files.isRegularFile(it.toPath(), LinkOption.NOFOLLOW_LINKS) }
    val fallbackValue = readJvmSecretFile(fallbackFile)
    if (fallbackValue != null) {
        // Deletion must be durable even if the OS keyring is temporarily unavailable. Without this
        // marker, a failed Keychain delete can revive the older token on the next macOS launch.
        if (fallbackValue == JVM_SECRET_DELETED_SENTINEL) {
            deleteJvmKeyringSecret(account)
            return null
        }

        // A fallback file means a previous keyring write was unavailable. Treat that newer durable
        // value as authoritative instead of accidentally reviving a stale keyring entry. When the
        // keyring recovers, migrate the fallback and remove it only after a confirmed write.
        if (writeJvmKeyringSecret(account, fallbackValue)) {
            runCatching { fallbackFile?.let { Files.deleteIfExists(it.toPath()) } }
        }
        return fallbackValue
    }

    return readJvmKeyringSecret(account)
}

private fun writeJvmSecret(account: String, value: String): Boolean {
    if (writeJvmKeyringSecret(account, value)) {
        runCatching { secureStoreFile(account)?.let { Files.deleteIfExists(it.toPath()) } }
        runCatching { legacySecureStoreFile(account)?.let { Files.deleteIfExists(it.toPath()) } }
        return true
    }
    return writeJvmSecretFile(account, value)
}

private fun deleteJvmSecret(account: String) {
    // Write the newer logical deletion before touching Keychain. The marker wins on the next read if
    // macOS rejects or delays keyring deletion, so a logged-out session cannot come back to life.
    val tombstoneWritten = writeJvmSecretFile(account, JVM_SECRET_DELETED_SENTINEL)
    if (!tombstoneWritten) {
        runCatching { secureStoreFile(account)?.let { Files.deleteIfExists(it.toPath()) } }
    }
    runCatching { legacySecureStoreFile(account)?.let { Files.deleteIfExists(it.toPath()) } }
    deleteJvmKeyringSecret(account)
}

// ReceiptPlatformJvmBridge is provided by the shared JVM source set. Keeping one JVM class for
// receipt printers avoids duplicate kz.aita.ReceiptPlatformJvmBridge classes on the desktop classpath.


object LabelPrinterPlatformJvmBridge {
    private const val SETTINGS_FILE_NAME = "aita_label_printer.properties"
    private const val MAX_LABEL_PRINTER_BYTES = 2 * 1024 * 1024

    /** Optional desktop label-printer writer for USB serial, network bridge, tests, etc. */
    var writeLabelBytes: (suspend (ByteArray) -> Boolean)? = null

    private val environmentDevicePath: String? = System.getenv("AITA_LABEL_PRINTER_DEVICE")
        ?.trim()
        ?.takeIf { it.isNotBlank() }
    private val environmentServiceName: String? = System.getenv("AITA_LABEL_PRINTER_SERVICE")
        ?.trim()
        ?.takeIf { it.isNotBlank() }
    private val environmentProtocol: String? = System.getenv("AITA_LABEL_PRINTER_PROTOCOL")
        ?.trim()
        ?.takeIf { it.isNotBlank() }

    @Volatile
    var labelPrinterDevicePath: String? = environmentDevicePath
        private set

    @Volatile
    var labelPrinterServiceName: String? = environmentServiceName
        private set

    @Volatile
    var labelPrinterProtocol: String = normalizeLabelPrinterProtocol(environmentProtocol)
        private set

    @Volatile
    private var persistedConfigurationLoaded: Boolean = false

    private fun settingsFile(): File {
        val dataRoot = jvmPersistentDataDirPath.trim()
            .ifBlank { cacheDirPath.trim() }
            .ifBlank { System.getProperty("java.io.tmpdir") }
        return File(ensureDesktopDirectory(File(dataRoot), ownerOnly = true), SETTINGS_FILE_NAME)
    }

    @Synchronized
    fun loadPersistedConfiguration() {
        if (persistedConfigurationLoaded) return
        persistedConfigurationLoaded = true

        val file = settingsFile()
        if (!file.isFile) return
        val properties = Properties()
        runCatching {
            file.inputStream().use { input -> properties.load(input) }
            hardenOwnerOnlyPermissions(file, directory = false)

            if (environmentDevicePath == null && environmentServiceName == null) {
                applyDeviceId(
                    properties.getProperty("deviceId")?.trim()?.takeIf { it.isNotBlank() },
                    persist = false
                )
            }
            if (environmentProtocol == null) {
                labelPrinterProtocol = normalizeLabelPrinterProtocol(properties.getProperty("protocol"))
            }
        }.onFailure { throwable ->
            System.err.println("AITA label-printer settings could not be loaded: ${throwable.message}")
        }
    }

    fun configuredDeviceId(): String? = when {
        labelPrinterServiceName == SYSTEM_DOCUMENT_PRINTER_ID -> SYSTEM_DOCUMENT_PRINTER_ID
        !labelPrinterServiceName.isNullOrBlank() -> "service:${labelPrinterServiceName!!.trim()}"
        !labelPrinterDevicePath.isNullOrBlank() -> "path:${labelPrinterDevicePath!!.trim()}"
        else -> null
    }

    private fun savePersistedConfiguration() {
        val file = settingsFile()
        val parent = file.parentFile ?: error("Label-printer settings directory is unavailable")
        val temporary = File(parent, ".${file.name}.${UUID.randomUUID()}.tmp")
        val properties = Properties().apply {
            configuredDeviceId()?.let { setProperty("deviceId", it) }
            setProperty("protocol", labelPrinterProtocol)
        }

        try {
            temporary.outputStream().use { output ->
                properties.store(output, "AITA desktop label-printer configuration")
            }
            hardenOwnerOnlyPermissions(temporary, directory = false)
            try {
                Files.move(
                    temporary.toPath(),
                    file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                )
            } catch (atomicMoveFailure: Throwable) {
                try {
                    Files.move(
                        temporary.toPath(),
                        file.toPath(),
                        StandardCopyOption.REPLACE_EXISTING
                    )
                } catch (fallbackMoveFailure: Throwable) {
                    fallbackMoveFailure.addSuppressed(atomicMoveFailure)
                    throw fallbackMoveFailure
                }
            }
            hardenOwnerOnlyPermissions(file, directory = false)
        } finally {
            runCatching { Files.deleteIfExists(temporary.toPath()) }
        }
    }

    private fun applyDeviceId(deviceId: String?, persist: Boolean) {
        val clean = deviceId?.trim()?.takeIf { it.isNotBlank() }
        when {
            clean == null -> {
                labelPrinterDevicePath = null
                labelPrinterServiceName = null
            }
            clean == SYSTEM_DOCUMENT_PRINTER_ID -> {
                labelPrinterServiceName = SYSTEM_DOCUMENT_PRINTER_ID
                labelPrinterDevicePath = null
            }
            clean.startsWith("service:", ignoreCase = true) -> {
                labelPrinterServiceName = clean.substringAfter(':').trim().takeIf { it.isNotBlank() }
                labelPrinterDevicePath = null
            }
            clean.startsWith("path:", ignoreCase = true) ||
                clean.startsWith("file:", ignoreCase = true) -> {
                labelPrinterDevicePath = clean.substringAfter(':').trim().takeIf { it.isNotBlank() }
                labelPrinterServiceName = null
            }
            else -> {
                labelPrinterDevicePath = clean
                labelPrinterServiceName = null
            }
        }
        if (persist) savePersistedConfiguration()
    }

    fun configureLabelPrinterDeviceId(deviceId: String?) {
        loadPersistedConfiguration()
        applyDeviceId(deviceId, persist = true)
    }

    fun configureProtocol(protocol: String) {
        loadPersistedConfiguration()
        labelPrinterProtocol = normalizeLabelPrinterProtocol(protocol)
        savePersistedConfiguration()
    }

    private fun normalizedDevicePath(rawPath: String): String {
        val clean = rawPath.trim()
        return if (desktopOsFamily == DesktopOsFamily.Windows && clean.matches(Regex("(?i)^COM\\d+$"))) {
            "\\\\.\\$clean"
        } else {
            clean
        }
    }

    private fun systemPrintServiceNames(): List<String> = runCatching {
        PrintServiceLookup.lookupPrintServices(null, null)
            .orEmpty()
            .mapNotNull { it.name?.trim()?.takeIf { it.isNotBlank() } }
            .distinct()
    }.getOrElse { throwable ->
        System.err.println("AITA label-printer service discovery failed: ${throwable.message}")
        emptyList()
    }

    fun listLabelPrinterDevices(): List<PlatformLabelPrinterDataModel> {
        loadPersistedConfiguration()
        val configuredPath = labelPrinterDevicePath?.trim().orEmpty()
        val configuredService = labelPrinterServiceName?.trim().orEmpty()
        val pathCandidates = listOf(
            configuredPath,
            environmentDevicePath.orEmpty(),
            "/dev/usb/lp0",
            "/dev/ttyUSB0",
            "/dev/ttyACM0"
        )
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .filter { path ->
                configuredPath.equals(path, true) ||
                    File(path).exists() ||
                    path.matches(Regex("(?i)^COM\\d+$"))
            }
            .map { path ->
                val file = File(normalizedDevicePath(path))
                val isWindowsCom = path.matches(Regex("(?i)^COM\\d+$"))
                val exists = isWindowsCom || file.exists()
                val writable = isWindowsCom || (exists && !file.isDirectory && file.canWrite())
                PlatformLabelPrinterDataModel(
                    id = "path:$path",
                    name = path.substringAfterLast('/').ifBlank { path },
                    subtitle = when {
                        !exists -> "Direct device path is currently unavailable"
                        !writable -> "Device exists but is not writable; add the desktop user to the printer/serial device group"
                        else -> "Direct device path • TSPL/ZPL/CPCL raw label bytes"
                    },
                    configured = configuredPath.equals(path, ignoreCase = true),
                    available = writable
                )
            }

        val printServices = systemPrintServiceNames().map { name ->
            PlatformLabelPrinterDataModel(
                id = "service:$name",
                name = name,
                subtitle = deviceWorkflowText("label_system_help"),
                configured = configuredService.equals(name, ignoreCase = true),
                available = true
            )
        }

        val savedUnavailable = when {
            configuredPath.isNotBlank() && pathCandidates.none { it.configured } -> listOf(
                PlatformLabelPrinterDataModel(
                    id = "path:$configuredPath",
                    name = configuredPath.substringAfterLast('/').ifBlank { configuredPath },
                    subtitle = "Saved label-printer path; reconnect it or restore its Linux device permissions",
                    configured = true,
                    available = false
                )
            )
            configuredService.isNotBlank() && printServices.none { it.configured } -> listOf(
                PlatformLabelPrinterDataModel(
                    id = "service:$configuredService",
                    name = configuredService,
                    subtitle = "Saved system print service; reconnect or reinstall it if unavailable",
                    configured = true,
                    available = false
                )
            )
            else -> emptyList()
        }

        return (savedUnavailable + pathCandidates + printServices)
            .distinctBy { it.id.lowercase(Locale.ROOT) }
            .sortedWith(
                compareByDescending<PlatformLabelPrinterDataModel> { it.configured }
                    .thenByDescending { it.available }
                    .thenBy { it.name.lowercase(Locale.ROOT) }
            )
    }

    suspend fun writeLabelBytesToConfiguredPrinter(labelBytes: ByteArray): Boolean {
        if (labelBytes.isEmpty()) error("Label contains no printer bytes")
        if (labelBytes.size > MAX_LABEL_PRINTER_BYTES) {
            error("Label print payload is too large (${labelBytes.size} bytes)")
        }
        loadPersistedConfiguration()
        writeLabelBytes?.let { customWriter -> return customWriter(labelBytes.copyOf()) }

        val path = labelPrinterDevicePath?.trim()?.takeIf { it.isNotBlank() }
        if (path != null) {
            return withContext(Dispatchers.IO) {
                val file = File(normalizedDevicePath(path))
                if (!file.exists() && desktopOsFamily != DesktopOsFamily.Windows) {
                    error("Label-printer device '$path' is not connected")
                }
                if (file.isDirectory) error("Label-printer device '$path' is a directory")
                if (file.exists() && !file.canWrite()) {
                    error("Label-printer device '$path' is not writable. Check Linux group/device permissions.")
                }
                file.outputStream().use { output ->
                    output.write(labelBytes)
                    output.flush()
                }
                true
            }
        }

        val serviceName = labelPrinterServiceName?.trim()?.takeIf { it.isNotBlank() } ?: return false
        return withContext(Dispatchers.IO) {
            val service = PrintServiceLookup.lookupPrintServices(null, null)
                .orEmpty()
                .firstOrNull { it.name.equals(serviceName, ignoreCase = true) }
                ?: return@withContext false
            val stableBytes = labelBytes.copyOf()
            val doc = SimpleDoc(stableBytes, DocFlavor.BYTE_ARRAY.AUTOSENSE, null)
            service.createPrintJob().print(doc, null)
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
    /** A multilingual engine returns its own detected language, never a guessed UI language. */
    var recognizeAutomaticallyOnce: (suspend () -> VoiceRecognitionResult?)? = null
}

private fun desktopPermissionSettingsCommands(kind: PlatformPermissionKind): List<Array<String>> =
    when (desktopOsFamily) {
        DesktopOsFamily.MacOS -> {
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
        DesktopOsFamily.Windows -> {
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
        DesktopOsFamily.Linux -> listOf(
            arrayOf("gnome-control-center", "privacy"),
            arrayOf("gnome-control-center", "applications"),
            arrayOf("systemsettings6"),
            arrayOf("systemsettings5")
        )
        DesktopOsFamily.Other -> emptyList()
    }

private suspend fun openDesktopPermissionSettings(kind: PlatformPermissionKind): ReceiptPlatformActionResult =
    withContext(Dispatchers.IO) {
        if (desktopPermissionSettingsCommands(kind).any { command -> launchDesktopCommand(*command) }) {
            ReceiptPlatformActionResult(true, "Permission settings opened")
        } else {
            ReceiptPlatformActionResult(
                false,
                "Could not open permission settings on ${desktopPlatformDisplayName()}"
            )
        }
    }

fun installDesktopVoiceInputJvm() {
    var generation = 0L
    var finishActiveSession: (() -> Unit)? = null
    fun available() = DesktopVoiceInputJvmBridge.recognizeAutomaticallyOnce != null || DesktopVoiceInputJvmBridge.recognizeOnce != null
    isPlatformVoiceInputAvailable = ::available
    getVoiceInputPermissionState = { if (available()) PlatformPermissionState.Granted else PlatformPermissionState.Unavailable }
    stopPlatformVoiceInput = {
        generation++
        val finished = finishActiveSession
        finishActiveSession = null
        finished?.invoke()
    }
    startPlatformVoiceInput = start@{ texts, callbacks ->
        stopPlatformVoiceInput?.invoke()
        val ticket = ++generation
        finishActiveSession = callbacks.onFinished
        try {
            val automatic = DesktopVoiceInputJvmBridge.recognizeAutomaticallyOnce
            val legacy = DesktopVoiceInputJvmBridge.recognizeOnce
            if (automatic == null && legacy == null) {
                callbacks.onError("Desktop voice input engine is not configured for ${desktopPlatformDisplayName()}.")
                return@start
            }
            callbacks.onAmplitude(0.30f)
            callbacks.onLanguageMode(if (automatic != null) VoiceLanguageMode.AutomaticRequested else VoiceLanguageMode.DeviceDefault)
            val result = withContext(Dispatchers.IO) {
                try {
                    if (automatic != null) automatic()
                    else legacy?.invoke(if (texts.automaticLanguageDetection) Locale.getDefault().language else texts.primaryLanguageTag)
                        ?.let { VoiceRecognitionResult(it) }
                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: Exception) { null }
            }
            if (ticket != generation) return@start
            normalizedDetectedLanguage(result?.detectedLanguage)?.let(callbacks.onDetectedLanguage)
            if (!result?.text.isNullOrBlank()) callbacks.onFinalText(result!!.text) else callbacks.onError("Nothing was recognized")
        } finally {
            if (ticket == generation) {
                val finished = finishActiveSession
                finishActiveSession = null
                finished?.invoke()
            }
        }
    }
}

private fun installDesktopPlatformActionsJvm() {
    ReceiptPlatformJvmBridge.loadPersistedEscPosDevicePath()
    LabelPrinterPlatformJvmBridge.loadPersistedConfiguration()
    configuredReceiptPrinterDeviceIdState.value = ReceiptPlatformJvmBridge.escPosDevicePath
    configuredLabelPrinterDeviceIdState.value = LabelPrinterPlatformJvmBridge.configuredDeviceId()
    configuredLabelPrinterProtocolState.value = LabelPrinterPlatformJvmBridge.labelPrinterProtocol

    fun safeDesktopFileName(
        rawName: String,
        fallbackName: String,
        requiredExtension: String? = null
    ): String {
        val fallback = fallbackName.ifBlank { "aita-file" }
        val sanitized = rawName
            .trim()
            .replace(Regex("""[\\/:*?"<>|\p{Cntrl}]+"""), "_")
            .trim { it.isWhitespace() || it == '.' || it == '_' }
            .ifBlank { fallback }
            .take(160)
            .trim { it.isWhitespace() || it == '.' || it == '_' }
            .ifBlank { fallback }
        return if (requiredExtension != null && !sanitized.endsWith(requiredExtension, ignoreCase = true)) {
            sanitized.substringBeforeLast('.', sanitized) + requiredExtension
        } else {
            sanitized
        }
    }

    fun uniqueDesktopFile(directory: File, preferredName: String): File {
        ensureDesktopDirectory(directory)
        val baseName = preferredName.substringBeforeLast('.', preferredName)
        val extension = preferredName
            .substringAfterLast('.', "")
            .takeIf { preferredName.contains('.') }
            ?.let { ".$it" }
            .orEmpty()
        var candidate = File(directory, preferredName)
        var index = 2
        while (candidate.exists() && index <= 999) {
            candidate = File(directory, "$baseName-$index$extension")
            index++
        }
        return candidate
    }

    fun writePdfToDownloads(fileName: String, pdfBytes: ByteArray): File {
        val safeName = safeDesktopFileName(fileName, "receipt.pdf", ".pdf")
        // Reserve a distinct path atomically: two simultaneous exports cannot replace each other
        // or redirect an earlier notification's Open action to a newer receipt.
        val directory = ensureDesktopDirectory(desktopDownloadsDirectory())
        val file = File.createTempFile((safeName.removeSuffix(".pdf") + "_").padEnd(3, '_'), ".pdf", directory)
        try {
            writeDesktopBytesAtomically(file, pdfBytes, ownerOnly = true)
            return file
        } catch (failure: Exception) {
            file.delete()
            throw failure
        }
    }

    fun writePdfToTemp(fileName: String, pdfBytes: ByteArray): File {
        val tempDir = ensureDesktopDirectory(
            File(System.getProperty("java.io.tmpdir"), "aita_documents"),
            ownerOnly = true
        )
        val safeName = safeDesktopFileName(fileName, "receipt.pdf", ".pdf")
        return uniqueDesktopFile(tempDir, safeName).also { file ->
            writeDesktopBytesAtomically(file, pdfBytes, ownerOnly = true)
            file.deleteOnExit()
        }
    }

    fun writeHtmlToTemp(fileName: String, html: String): File {
        val tempDir = ensureDesktopDirectory(
            File(System.getProperty("java.io.tmpdir"), "aita_documents"),
            ownerOnly = true
        )
        val rawName = fileName.ifBlank { "aita-document.html" }
        val extension = if (rawName.endsWith(".htm", ignoreCase = true)) ".htm" else ".html"
        val safeName = safeDesktopFileName(rawName, "aita-document$extension", extension)
        return uniqueDesktopFile(tempDir, safeName).also { file ->
            writeDesktopBytesAtomically(file, html.toByteArray(Charsets.UTF_8), ownerOnly = true)
            file.deleteOnExit()
        }
    }

    fun desktop(): Desktop? = runCatching {
        if (Desktop.isDesktopSupported()) Desktop.getDesktop() else null
    }.getOrNull()

    fun openDesktopFile(file: File): Boolean {
        val desktop = desktop()
        if (desktop != null && runCatching { desktop.isSupported(Desktop.Action.OPEN) }.getOrDefault(false)) {
            if (runCatching { desktop.open(file); true }.getOrDefault(false)) return true
        }
        return when (desktopOsFamily) {
            DesktopOsFamily.MacOS -> launchDesktopCommand("open", file.absolutePath)
            DesktopOsFamily.Windows -> launchDesktopCommand("cmd", "/c", "start", "", file.absolutePath)
            DesktopOsFamily.Linux -> launchDesktopCommand("xdg-open", file.absolutePath)
            DesktopOsFamily.Other -> false
        }
    }

    fun browseDesktopUri(uri: URI): Boolean {
        val desktop = desktop()
        if (desktop != null && runCatching { desktop.isSupported(Desktop.Action.BROWSE) }.getOrDefault(false)) {
            if (runCatching { desktop.browse(uri); true }.getOrDefault(false)) return true
        }
        return when (desktopOsFamily) {
            DesktopOsFamily.MacOS -> launchDesktopCommand("open", uri.toString())
            DesktopOsFamily.Windows -> launchDesktopCommand("cmd", "/c", "start", "", uri.toString())
            DesktopOsFamily.Linux -> launchDesktopCommand("xdg-open", uri.toString())
            DesktopOsFamily.Other -> false
        }
    }

    openExternalUrlPlatformAction = { rawUrl ->
        withContext(Dispatchers.IO) {
            runCatching {
                val uri = URI(rawUrl.trim())
                if (uri.scheme?.lowercase() !in setOf("http", "https", "geo")) {
                    ReceiptPlatformActionResult(false, "Unsupported link")
                } else if (browseDesktopUri(uri)) {
                    ReceiptPlatformActionResult(true, "Opened map")
                } else {
                    ReceiptPlatformActionResult(false, "Could not open the link")
                }
            }.getOrElse { throwable ->
                ReceiptPlatformActionResult(false, throwable.message ?: "Could not open the link")
            }
        }
    }

    fun printOrOpenDesktopFile(file: File): ReceiptPlatformActionResult {
        val desktop = desktop()
        if (desktop != null && runCatching { desktop.isSupported(Desktop.Action.PRINT) }.getOrDefault(false)) {
            if (runCatching { desktop.print(file); true }.getOrDefault(false)) {
                return ReceiptPlatformActionResult(true, "Opening system print dialog")
            }
        }
        return if (openDesktopFile(file)) {
            ReceiptPlatformActionResult(true, "Opened document; print from the viewer")
        } else {
            ReceiptPlatformActionResult(true, "Document created at ${file.absolutePath}")
        }
    }

    saveReceiptPdfFile = { fileName, pdfBytes ->
        withContext(Dispatchers.IO) {
            runCatching {
                val file = writePdfToDownloads(fileName, pdfBytes)
                ReceiptPlatformActionResult(true, "Saved to ${file.absolutePath}", SavedPdfFile(file.name, file.parentFile.absolutePath) { isCurrent ->
                    withContext(Dispatchers.IO) {
                        if (!file.isFile || !file.canRead()) ReceiptPlatformActionResult(false, "The saved PDF was moved, deleted, or is no longer accessible")
                        else if (!isCurrent()) ReceiptPlatformActionResult(false, "This file action belongs to an earlier sign-in")
                        else {
                            val opened = runCatching {
                                val viewer = desktop()
                                if (viewer != null && viewer.isSupported(Desktop.Action.OPEN)) { viewer.open(file); true }
                                else when (desktopOsFamily) {
                                    DesktopOsFamily.Windows -> launchDesktopCommand("rundll32.exe", "url.dll,FileProtocolHandler", file.toURI().toASCIIString())
                                    DesktopOsFamily.MacOS -> launchDesktopCommand("open", file.absolutePath)
                                    DesktopOsFamily.Linux -> launchDesktopCommand("xdg-open", file.absolutePath)
                                    else -> false
                                }
                            }.getOrDefault(false)
                            ReceiptPlatformActionResult(opened, if (opened) "Opening saved PDF" else "No available PDF viewer could open this file")
                        }
                    }
                })
            }.getOrElse { throwable ->
                ReceiptPlatformActionResult(false, throwable.message ?: "Could not save PDF")
            }
        }
    }

    shareReceiptPdfFile = { fileName, pdfBytes, whatsappOnly ->
        withContext(Dispatchers.IO) {
            runCatching {
                val file = writePdfToTemp(fileName, pdfBytes)
                if (whatsappOnly) {
                    val text = URLEncoder.encode("AITA receipt: ${file.absolutePath}", Charsets.UTF_8.name())
                    val whatsappOpened = browseDesktopUri(URI("https://web.whatsapp.com/send?text=$text"))
                    val pdfOpened = openDesktopFile(file)
                    when {
                        whatsappOpened && pdfOpened -> ReceiptPlatformActionResult(true, "Opened WhatsApp Web and PDF")
                        whatsappOpened -> ReceiptPlatformActionResult(true, "Opened WhatsApp Web; PDF is at ${file.absolutePath}")
                        pdfOpened -> ReceiptPlatformActionResult(true, "Opened PDF; WhatsApp Web could not be opened automatically")
                        else -> ReceiptPlatformActionResult(true, "PDF created at ${file.absolutePath}")
                    }
                } else if (openDesktopFile(file)) {
                    ReceiptPlatformActionResult(true, "Opened PDF")
                } else {
                    ReceiptPlatformActionResult(true, "PDF created at ${file.absolutePath}")
                }
            }.getOrElse { throwable ->
                ReceiptPlatformActionResult(false, throwable.message ?: "Could not share PDF")
            }
        }
    }

    printPdfDocumentPlatformAction = { fileName, pdfBytes ->
        withContext(Dispatchers.IO) {
            runCatching {
                val file = writePdfToTemp(fileName.ifBlank { "aita-document.pdf" }, pdfBytes)
                printOrOpenDesktopFile(file)
            }.getOrElse { throwable ->
                ReceiptPlatformActionResult(false, throwable.message ?: "Could not print PDF document")
            }
        }
    }

    printHtmlDocumentPlatformAction = { fileName, html ->
        withContext(Dispatchers.IO) {
            runCatching {
                val file = writeHtmlToTemp(fileName.ifBlank { "aita-document.html" }, html)
                if (openDesktopFile(file)) {
                    ReceiptPlatformActionResult(true, "Opened label for printing")
                } else {
                    ReceiptPlatformActionResult(true, "HTML label created at ${file.absolutePath}")
                }
            }.getOrElse { throwable ->
                ReceiptPlatformActionResult(false, throwable.message ?: "Could not open HTML document")
            }
        }
    }

    suspend fun printReceiptBytesOnDesktop(printerBytes: ByteArray): ReceiptPlatformActionResult =
        withContext(Dispatchers.IO) {
            try {
                if (ReceiptPlatformJvmBridge.writeEscPosBytesToConfiguredPrinter(printerBytes)) {
                    ReceiptPlatformActionResult(true, deviceWorkflowText("print_queued"))
                } else {
                    ReceiptPlatformActionResult(false, "Desktop ESC/POS receipt printer is not configured")
                }
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                ReceiptPlatformActionResult(false, throwable.message ?: "Could not print receipt")
            }
        }

    // Restore only the saved label here; printer/driver enumeration stays off the startup path.
    restoreSystemReceiptPrinterName()
    chooseSystemA4PrinterAction = { withContext(Dispatchers.IO) { chooseSystemA4Printer() } }
    listSystemDocumentPrintersAction = { withContext(Dispatchers.IO) { listSystemDocumentPrinters() } }
    selectSystemDocumentPrinterAction = { a4, name -> withContext(Dispatchers.IO) { selectSystemDocumentPrinter(a4, name) } }
    printA4DocumentPlatformAction = { title, document -> withContext(Dispatchers.IO) { printSystemA4Document(title, document) } }
    chooseSystemReceiptPrinterAction = { withContext(Dispatchers.IO) { chooseSystemReceiptPrinter() } }
    printReceiptDocumentPlatformAction = { title, document ->
        withContext(Dispatchers.IO) { printSystemReceiptDocument(title, document) }
    }

    listPlatformReceiptPrinterDevicesAction = {
        withContext(Dispatchers.IO) {
            val detected = ReceiptPlatformJvmBridge.listConfiguredAndDetectedPrinters()
            listOf(PlatformReceiptPrinterDataModel(SYSTEM_DOCUMENT_PRINTER_ID,
                deviceWorkflowText("desktop_system_print"), deviceWorkflowText("receipt_driver_help"),
                configured = ReceiptPlatformJvmBridge.escPosDevicePath == SYSTEM_DOCUMENT_PRINTER_ID)) +
            detected.filterNot { it.id == SYSTEM_DOCUMENT_PRINTER_ID }
        }
    }

    configurePlatformReceiptPrinterDeviceAction = { deviceId ->
        ReceiptPlatformJvmBridge.configureEscPosDevicePath(deviceId)
        ReceiptPlatformActionResult(
            true,
            if (deviceId.isNullOrBlank()) "Receipt printer cleared" else "Receipt printer selected"
        )
    }

    printReceiptPlatformAction = { _, _, printerBytes ->
        printReceiptBytesOnDesktop(printerBytes)
    }

    printReceiptEscPosBytes = { printerBytes ->
        printReceiptBytesOnDesktop(printerBytes)
    }

    printStockItemLabelPlatformAction = { label ->
        withContext(Dispatchers.IO) {
            LabelPrinterPlatformJvmBridge.loadPersistedConfiguration()
            val service = LabelPrinterPlatformJvmBridge.labelPrinterServiceName
            if (service == SYSTEM_DOCUMENT_PRINTER_ID ||
                (service != null && label.protocol == LABEL_PRINTER_PROTOCOL_AUTO)) {
                printSystemLabel(label, service.takeUnless { it == SYSTEM_DOCUMENT_PRINTER_ID }) { selected ->
                    LabelPrinterPlatformJvmBridge.configureLabelPrinterDeviceId("service:$selected")
                    configuredLabelPrinterDeviceIdState.value = "service:$selected"
                }
            } else null // Explicit TSPL/ZPL/CPCL and direct USB/Bluetooth keep their native byte protocol.
        }
    }

    listPlatformLabelPrinterDevicesAction = {
        withContext(Dispatchers.IO) {
            val detected = LabelPrinterPlatformJvmBridge.listLabelPrinterDevices()
            listOf(PlatformLabelPrinterDataModel(SYSTEM_DOCUMENT_PRINTER_ID,
                deviceWorkflowText("desktop_system_print"), deviceWorkflowText("label_system_help"),
                configured = LabelPrinterPlatformJvmBridge.configuredDeviceId() == SYSTEM_DOCUMENT_PRINTER_ID,
                supportedProtocols = emptyList())) +
            detected.filterNot { it.id == "service:$SYSTEM_DOCUMENT_PRINTER_ID" }
        }
    }

    configurePlatformLabelPrinterDeviceAction = { deviceId ->
        runCatching {
            LabelPrinterPlatformJvmBridge.configureLabelPrinterDeviceId(deviceId)
            ReceiptPlatformActionResult(
                true,
                if (deviceId.isNullOrBlank()) "Label printer cleared" else "Label printer selected"
            )
        }.getOrElse { throwable ->
            ReceiptPlatformActionResult(false, throwable.message ?: "Could not save label-printer selection")
        }
    }

    configurePlatformLabelPrinterProtocolAction = { protocol ->
        runCatching {
            LabelPrinterPlatformJvmBridge.configureProtocol(protocol)
            ReceiptPlatformActionResult(true, "Label printer protocol selected")
        }.getOrElse { throwable ->
            ReceiptPlatformActionResult(false, throwable.message ?: "Could not save label-printer protocol")
        }
    }

    printLabelPrinterBytes = { labelBytes ->
        withContext(Dispatchers.IO) {
            try {
                if (LabelPrinterPlatformJvmBridge.writeLabelBytesToConfiguredPrinter(labelBytes)) {
                    ReceiptPlatformActionResult(true, "Label sent to printer")
                } else {
                    ReceiptPlatformActionResult(false, "Desktop sticky label printer is not configured")
                }
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                ReceiptPlatformActionResult(false, throwable.message ?: "Could not print sticky label")
            }
        }
    }
}

fun loadOrCreateInstallationId(): String {
    readJvmSecret("installation_id")?.takeIf { it.isNotBlank() }?.let { return it }

    val fresh = UUID.randomUUID().toString()
    if (!writeJvmSecret("installation_id", fresh)) {
        System.err.println("AITA desktop installation ID could not be persisted; a new ID may be generated next launch")
    }
    return fresh
}

private fun legacyAccountKeyFiles(account: String): List<File> = buildList {
    cacheDirPath.trim().takeIf { it.isNotBlank() }?.let { add(File(it, "$account.key")) }
    jvmPersistentDataDirPath.trim().takeIf { it.isNotBlank() }?.let { add(File(it, "$account.key")) }
}.distinctBy { it.absolutePath }

fun loadOrCreateKey(account: String): ByteArray {
    fallbackEncryptionKeys[account]?.let { return it }

    val storedKey = runCatching {
        readJvmSecret(account)
            ?.let { Base64.getUrlDecoder().decode(it) }
            ?.takeIf { it.size == 32 }
    }.getOrNull()
    if (storedKey != null) {
        fallbackEncryptionKeys[account] = storedKey
        return storedKey
    }

    val legacyKeyEntry = legacyAccountKeyFiles(account)
        .firstNotNullOfOrNull { file ->
            runCatching {
                file
                    .takeIf { it.isFile }
                    ?.also { hardenOwnerOnlyPermissions(it, directory = false) }
                    ?.readText(Charsets.UTF_8)
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { Base64.getUrlDecoder().decode(it) }
                    ?.takeIf { it.size == 32 }
                    ?.let { file to it }
            }.getOrNull()
        }
    if (legacyKeyEntry != null) {
        val (legacyFile, legacyKey) = legacyKeyEntry
        if (writeJvmSecret(account, Base64.getUrlEncoder().withoutPadding().encodeToString(legacyKey))) {
            runCatching { Files.deleteIfExists(legacyFile.toPath()) }
        } else {
            System.err.println(
                "AITA desktop retained legacy encryption key '${legacyFile.absolutePath}' because secure migration failed"
            )
        }
        fallbackEncryptionKeys[account] = legacyKey
        return legacyKey
    }

    val raw = ByteArray(32).also { bytes -> rng.nextBytes(bytes) }
    val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(raw)
    check(writeJvmSecret(account, encoded)) {
        "AITA desktop could not persist the local account encryption key"
    }
    fallbackEncryptionKeys[account] = raw
    return raw
}

fun main() {
    configureDesktopApplicationIdentity()

    val cacheRoot = desktopAitaCacheRootDir()
    val dataRoot = desktopAitaDataRootDir()
    if (!DesktopSingleInstance.acquire(dataRoot)) return
    cacheDirPath = cacheRoot.absolutePath
    jvmPersistentDataDirPath = dataRoot.absolutePath
    migrateLegacyDesktopPersistentState(dataRoot)

    installDesktopDiagnosticLogging()
    installDesktopRuntimeDiagnostics()
    if (!verifyDesktopGuiRuntime()) kotlin.system.exitProcess(2)

    configureClientServerUrlOverrideFromEnvironment()
    installDesktopPlatformActionsJvm()
    installDesktopVoiceInputJvm()

    val accountDataFile = File(dataRoot, "ua.bin")

    val readAuthTokens = {
        runCatching {
            readJvmSecret("auth_tokens")?.let { raw -> jsonBase.decodeFromString<TokenPair>(raw) }
        }.onFailure { throwable ->
            System.err.println("AITA desktop stored auth tokens could not be read: ${throwable.message}")
        }.getOrThrow()
    }
    val writeAuthTokens: (TokenPair?) -> Unit = { tokenPair ->
        if (tokenPair == null) {
            deleteJvmSecret("auth_tokens")
        } else {
            runCatching {
                val payload = jsonBase.encodeToString(tokenPair)
                check(writeJvmSecret("auth_tokens", payload)) {
                    "Desktop auth tokens could not be persisted"
                }
            }.onFailure { throwable ->
                System.err.println("AITA desktop stored auth tokens could not be written: ${throwable.message}")
            }.getOrThrow()
        }
    }

    val readAccount = accountReader@{
        if (!accountDataFile.isFile) return@accountReader null
        runCatching {
            val blob = accountDataFile.readBytes()
            if (blob.size < 13) return@runCatching null

            val key = loadOrCreateKey("user_account_encryption_key")
            val iv = blob.copyOfRange(0, 12)
            val ciphertext = blob.copyOfRange(12, blob.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            }
            val plain = cipher.doFinal(ciphertext)
            jsonBase.decodeFromString<UserAccountDataModel>(plain.toString(Charsets.UTF_8))
        }.onFailure { throwable ->
            System.err.println("AITA desktop account cache could not be decrypted: ${throwable.message}")
        }.getOrThrow()
    }

    val writeAccount: (UserAccountDataModel?) -> Unit = { value ->
        runCatching {
            if (value == null) {
                if (accountDataFile.exists() && !accountDataFile.delete()) {
                    error("Could not delete desktop account cache '${accountDataFile.absolutePath}'")
                }
                fallbackEncryptionKeys.remove("user_account_encryption_key")
                deleteJvmSecret("user_account_encryption_key")
            } else {
                val key = loadOrCreateKey("user_account_encryption_key")
                val plain = jsonBase.encodeToString(value).toByteArray(Charsets.UTF_8)
                val iv = ByteArray(12).also { bytes -> rng.nextBytes(bytes) }
                val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                    init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
                }
                writeDesktopBytesAtomically(
                    file = accountDataFile,
                    bytes = iv + cipher.doFinal(plain),
                    ownerOnly = true
                )
            }
        }.onFailure { throwable ->
            System.err.println("AITA desktop account cache could not be updated: ${throwable.message}")
        }.getOrThrow()
    }

    // Ownership checks run on the UI thread and throughout navigation/cache restoration.
    // Match Android's process cache instead of doing a keyring round-trip on every check.
    val authCache = PersistentCredentialCache(readAuthTokens, writeAuthTokens)
    val accountCache = PersistentCredentialCache(readAccount, writeAccount)
    runCatching { authCache.get() }
    runCatching { accountCache.get() }
    getStoredUserAuthTokens = { runCatching { authCache.get() }.getOrNull() }
    setStoredUserAuthTokens = { value -> authCache.set(value) }
    getStoredUserAccountDataModel = { runCatching { accountCache.get() }.getOrNull() }
    setStoredUserAccountDataModel = { value -> accountCache.set(value) }

    setClipboardText = { text ->
        if (!copyTextToDesktopClipboard(text)) {
            System.err.println("AITA desktop clipboard is unavailable on ${desktopPlatformDisplayName()}")
        }
    }

    openPlatformAppSettings = { kind ->
        openDesktopPermissionSettings(kind)
    }

    openSystemDevicesSettings = {
        withContext(Dispatchers.IO) {
            val commands = when (desktopOsFamily) {
                DesktopOsFamily.MacOS -> listOf(
                    arrayOf("open", "x-apple.systempreferences:com.apple.BluetoothSettings"),
                    arrayOf("open", "x-apple.systempreferences:com.apple.preference.printers")
                )
                DesktopOsFamily.Windows -> listOf(
                    arrayOf("cmd", "/c", "start", "", "ms-settings:printers"),
                    arrayOf("cmd", "/c", "start", "", "ms-settings:bluetooth")
                )
                DesktopOsFamily.Linux -> listOf(
                    arrayOf("gnome-control-center", "bluetooth"),
                    arrayOf("gnome-control-center", "printers"),
                    arrayOf("blueman-manager")
                )
                DesktopOsFamily.Other -> emptyList()
            }
            if (commands.any { command -> launchDesktopCommand(*command) }) {
                ReceiptPlatformActionResult(true, "Device settings opened")
            } else {
                ReceiptPlatformActionResult(false, "Could not open device settings on ${desktopPlatformDisplayName()}")
            }
        }
    }

    val desktopDeviceInfo = run {
        val hostName = sequence {
            yield(System.getenv("COMPUTERNAME"))
            yield(System.getenv("HOSTNAME"))
            yield(desktopCommandOutput(1, "hostname"))
        }
            .mapNotNull { it?.trim()?.takeIf { it.isNotBlank() } }
            .firstOrNull()
        val userName = System.getProperty("user.name").orEmpty()
        val osName = System.getProperty("os.name").orEmpty()
        val osVersion = System.getProperty("os.version").orEmpty()
        val arch = System.getProperty("os.arch").orEmpty()
        val deviceName = hostName
            ?: userName.takeIf { it.isNotBlank() }?.let { "$it ${desktopPlatformDisplayName()} desktop" }
            ?: "${desktopPlatformDisplayName()} device"

        ClientDeviceInfoDataModel(
            installationId = loadOrCreateInstallationId(),
            deviceName = deviceName,
            platformName = desktopPlatformDisplayName(),
            osName = listOf(osName, osVersion, arch).filter { it.isNotBlank() }.joinToString(" - "),
            appName = "AITA",
            appVersion = "desktop",
            localeLanguage = Locale.getDefault().language.takeIf { it.isNotBlank() } ?: "en"
        )
    }

    // Device identity is process-stable. Never run hostname/DNS and read its file per request.
    getClientDeviceInfo = { desktopDeviceInfo }

    init()

    application {
        val closingScope = androidx.compose.runtime.rememberCoroutineScope()
        var closing by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
        Window(
            onCloseRequest = {
                if (!closing) {
                    closing = true
                    closingScope.launch {
                        try {
                            AppStateWorkspace.flush()
                            flushCartsBeforeClientUpdate()
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) {
                            closing = false
                            postInAppNotification(eventMessage("updates.error.storage"), NotificationType.Negative, transient = true)
                            return@launch
                        }
                        try { installPreparedWindowsUpdateOnExit() }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { System.err.println("AITA update-on-exit was deferred; verified files retained") }
                        exitApplication()
                    }
                }
            },
            title = "AITA",
            icon = painterResource("drawable/app_icon.png")
        ) {
            AppConfiguration({ MainScreen() })
        }
    }
}
