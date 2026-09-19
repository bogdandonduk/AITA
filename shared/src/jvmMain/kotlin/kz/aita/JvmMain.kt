package kz.aita

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.fazecast.jSerialComm.SerialPort
import io.ktor.client.engine.*
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runInterruptible
import okhttp3.*
import java.io.File
import java.io.IOException
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.*
import java.util.concurrent.TimeUnit
import javax.print.DocFlavor
import javax.print.PrintService
import javax.print.PrintServiceLookup
import javax.print.SimpleDoc
import javax.print.attribute.HashPrintRequestAttributeSet
import javax.print.attribute.standard.*

actual fun getCurrentTimeMillis(): Long = System.currentTimeMillis()
actual var getStoredUserAuthTokens: (() -> TokenPair?)? = null
actual var setStoredUserAuthTokens: ((TokenPair?) -> Unit)? = null

actual var getStoredUserAccountDataModel: (() -> UserAccountDataModel?)? = null
actual var setStoredUserAccountDataModel: ((UserAccountDataModel?) -> Unit)? = null

/**
 * Durable JVM application data. Compose's desktop launcher initializes this separately from
 * [cacheDirPath] so SQLDelight state, UI drafts and printer selections are not lost when an OS or
 * cleanup tool clears the desktop cache directory. Tests and embedders that do not initialize it
 * retain the old behaviour by falling back to [cacheDirPath].
 */
@Volatile
var jvmPersistentDataDirPath: String = ""

internal fun jvmPersistentDataRoot(): File {
    val configured = jvmPersistentDataDirPath.trim().ifBlank { cacheDirPath.trim() }
    val fallback = File(System.getProperty("java.io.tmpdir"), "aita-jvm-data").absolutePath
    return File(configured.ifBlank { fallback }).apply {
        if (!exists() && !mkdirs() && !exists()) {
            error("Could not create AITA desktop data directory '$absolutePath'")
        }
        if (!isDirectory) error("AITA desktop data path '$absolutePath' is not a directory")
    }
}

private val persistentUiDraftMutex = Mutex()

private fun persistentUiDraftFileForKey(key: String): File {
    val safeName = key
        .map { char -> if (char.isLetterOrDigit() || char == '-' || char == '_') char else '_' }
        .joinToString("")
        .let { if (it.length <= 140) it else it.take(96) + "_" + key.hashCode().toUInt().toString(16) }
    return File(File(jvmPersistentDataRoot(), "ui_drafts"), "$safeName.txt")
}

private fun writeTextAtomically(file: File, value: String) {
    val parent = file.parentFile ?: error("AITA file '${file.absolutePath}' has no parent directory")
    if (!parent.exists() && !parent.mkdirs() && !parent.exists()) {
        error("Could not create AITA directory '${parent.absolutePath}'")
    }
    val temporary = File(parent, ".${file.name}.${UUID.randomUUID()}.tmp")
    try {
        temporary.writeText(value, Charsets.UTF_8)
        try {
            Files.move(
                temporary.toPath(),
                file.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (atomicMoveFailure: IOException) {
            try {
                Files.move(
                    temporary.toPath(),
                    file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (fallbackMoveFailure: IOException) {
                fallbackMoveFailure.addSuppressed(atomicMoveFailure)
                throw fallbackMoveFailure
            }
        }
    } finally {
        runCatching { temporary.delete() }
    }
}

actual var getPersistentUiDraftValue: (suspend (String) -> String?)? = { key ->
    persistentUiDraftMutex.withLock {
        runCatching {
            val file = persistentUiDraftFileForKey(key)
            if (file.isFile) file.readText(Charsets.UTF_8) else null
        }.getOrNull()
    }
}

actual var setPersistentUiDraftValue: (suspend (String, String?) -> Unit)? = { key, value ->
    persistentUiDraftMutex.withLock {
        runCatching {
            val file = persistentUiDraftFileForKey(key)
            if (value == null) {
                if (file.exists() && !file.delete()) {
                    error("Could not delete persistent AITA UI draft '${file.absolutePath}'")
                }
            } else {
                writeTextAtomically(file, value)
            }
        }.onFailure { throwable ->
            System.err.println("AITA persistent UI draft write failed for key '$key': ${throwable.message}")
        }
    }
}

actual var cacheDirPath: String = ""
actual val Dispatchers.ourIo: CoroutineDispatcher
    get() = Dispatchers.IO

private enum class AitaDesktopNetworkProfile {
    Modern,
    CloudflareCompatibility
}

private fun configuredAitaDesktopNetworkProfile(): AitaDesktopNetworkProfile {
    val configured = sequenceOf(
        System.getProperty("aita.desktop.network.profile"),
        System.getenv("AITA_DESKTOP_NETWORK_PROFILE")
    )
        .mapNotNull { value -> value?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() } }
        .firstOrNull()

    return when (configured) {
        "modern", "default", "auto-modern" -> AitaDesktopNetworkProfile.Modern
        "compat", "compatibility", "cloudflare", "tls12", "http1" ->
            AitaDesktopNetworkProfile.CloudflareCompatibility
        else -> AitaDesktopNetworkProfile.Modern
    }
}

private object AitaIpv4PreferredDns : Dns {
    override fun lookup(hostname: String) = Dns.SYSTEM.lookup(hostname)
        .sortedBy { address -> if (address is Inet4Address) 0 else 1 }
}

private object AitaHttpCacheOwner {
    private var cache: Cache? = null

    @Synchronized
    fun get(): Cache? {
        cache?.let { return it }
        val basePath = cacheDirPath.trim().takeIf { it.isNotBlank() } ?: return null
        val directory = File(basePath, "http").apply { mkdirs() }
        // OkHttp forbids two Cache instances owning the same disk journal. The engine factory is
        // also used by health, refresh and validation clients, not only the long-lived API client.
        return Cache(directory, cacheSize).also { cache = it }
    }
}

private fun buildAitaOkHttpClient(): OkHttpClient {
    val profile = configuredAitaDesktopNetworkProfile()
    val builder = OkHttpClient.Builder()
        .retryOnConnectionFailure(true)

    if (profile == AitaDesktopNetworkProfile.CloudflareCompatibility) {
        builder.dns(AitaIpv4PreferredDns)
        val tls12Spec = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
            .tlsVersions(TlsVersion.TLS_1_2)
            .build()
        builder.connectionSpecs(listOf(tls12Spec, ConnectionSpec.CLEARTEXT))
        builder.protocols(listOf(Protocol.HTTP_1_1))
    }

    runCatching {
        AitaHttpCacheOwner.get()?.let { builder.cache(it) }
    }.onFailure { throwable ->
        System.err.println("AITA desktop HTTP cache initialization failed: ${throwable.message}")
    }

    println(
        "AITA desktop network profile: ${profile.name.lowercase(Locale.ROOT)} " +
                "(override with AITA_DESKTOP_NETWORK_PROFILE=modern|compat)"
    )
    return builder.build()
}

actual var getHttpClientEngine: () -> HttpClientEngine = {
    OkHttp.create {
        preconfigured = buildAitaOkHttpClient()
    }
}

actual var getSystemLocaleLanguage: () -> String = {
    Locale.getDefault().language.takeIf { it.isNotBlank() } ?: "ru"
}

private fun currentJvmDesktopPlatformName(): String {
    val osName = System.getProperty("os.name").orEmpty().lowercase(Locale.ROOT)
    return when {
        osName.contains("linux") -> "jvm-linux"
        osName.contains("mac") || osName.contains("darwin") -> "jvm-macos"
        osName.contains("win") -> "jvm-windows"
        else -> "jvm"
    }
}

actual var getPlatformName: () -> String = {
    currentJvmDesktopPlatformName()
}

actual var getSqlDelightDriver: (() -> SqlDriver?)? = {
    val dataDirectory = jvmPersistentDataRoot().toPath()
    Files.createDirectories(dataDirectory)
    val dbPath = dataDirectory.resolve("app_database.db").toAbsolutePath()
    val firstRun = !Files.exists(dbPath) || runCatching { Files.size(dbPath) == 0L }.getOrDefault(false)
    val driver: SqlDriver = JdbcSqliteDriver("jdbc:sqlite:$dbPath")

    try {
        // These are per-connection protections. They keep foreign-key integrity enabled and avoid
        // transient "database is locked" failures during short concurrent desktop operations.
        driver.execute(null, "PRAGMA foreign_keys = ON", 0)
        driver.execute(null, "PRAGMA busy_timeout = 5000", 0)

        val schema = AppDatabase.Schema.synchronous()
        if (firstRun) {
            schema.create(driver)
        } else {
            val cursor = driver.executeQuery(
                identifier = null,
                sql = "PRAGMA user_version",
                parameters = 0,
                mapper = { sqlCursor: SqlCursor ->
                    QueryResult.Value(
                        if (sqlCursor.next().value) sqlCursor.getLong(0)?.toInt() ?: 0 else 0
                    )
                }
            )
            val currentVersion = cursor.value
            val targetVersion = AppDatabase.Schema.version.toInt()
            when {
                currentVersion < targetVersion -> schema.migrate(driver, currentVersion.toLong(), schema.version)
                currentVersion > targetVersion -> error(
                    "AITA local database version $currentVersion is newer than this app supports ($targetVersion)"
                )
            }
        }

        driver
    } catch (throwable: Throwable) {
        runCatching { driver.close() }
        throw throwable
    }
}

object ReceiptPlatformJvmBridge {
    /**
     * Optional desktop ESC/POS writer. Configure it for tests or future native bridges.
     */
    var writeEscPosBytes: (suspend (ByteArray) -> Boolean)? = null

    /**
     * Desktop receipt-printer target. Supported values:
     * - print-service:XP-58 (copy 1)  -> Windows/macOS/Linux system printer, written as RAW ESC/POS where possible
     * - serial:COM3                   -> serial/virtual-COM printer through jSerialComm
     * - tcp://192.168.1.50:9100       -> network ESC/POS printer
     * - /dev/usb/lp0                  -> Linux/macOS raw device file
     * You can also set AITA_RECEIPT_PRINTER_DEVICE before launching the desktop app.
     */
    @Volatile
    var escPosDevicePath: String? = System.getenv("AITA_RECEIPT_PRINTER_DEVICE")
        ?.trim()
        ?.takeIf { it.isNotBlank() }

    private const val PRINT_SERVICE_PREFIX = "print-service:"
    private const val SERIAL_PORT_PREFIX = "serial:"
    private const val TCP_PREFIX = "tcp://"
    private const val TCP_SHORT_PREFIX = "tcp:"
    private const val FILE_PREFIX = "file:"
    private const val RECEIPT_PRINTER_DEVICE_FILE_NAME = "aita_receipt_printer_device.txt"
    private const val DEFAULT_RECEIPT_PRINTER_SERIAL_BAUD_RATE = 9600
    private const val WINDOWS_PRINTER_NOT_READY_MARKER = "AITA_PRINTER_NOT_READY:"
    private const val WINDOWS_RAW_PRINT_SUBMITTING_MARKER = "AITA_PRINT_SUBMITTING:"
    private const val WINDOWS_RAW_PRINT_SAFE_FAILURE_MARKER = "AITA_PRINT_SAFE_FAILURE:"
    private const val WINDOWS_RAW_PRINT_JOB_STARTED_MARKER = "AITA_PRINT_JOB_STARTED:"
    private const val WINDOWS_RAW_PRINT_SUCCESS_MARKER = "AITA_PRINT_OK:"
    private const val WINDOWS_RAW_PRINT_TIMEOUT_SECONDS = 45L
    private const val WINDOWS_SPOOL_FILE_MAX_AGE_MILLIS = 24L * 60L * 60L * 1_000L
    private const val MAX_RECEIPT_PRINTER_BYTES = 8 * 1024 * 1024
    private const val TCP_PRINTER_CONNECT_TIMEOUT_MILLIS = 8_000
    private const val TCP_PRINTER_SOCKET_TIMEOUT_MILLIS = 12_000

    /**
     * ESC/POS printers are sequential byte-stream devices. Without serialization, two quick taps,
     * automatic retries, or two windows can interleave bytes and produce a corrupted receipt.
     */
    private val receiptPrinterWriteMutex = Mutex()

    private fun receiptPrinterDevicePreferenceFile(): File {
        return File(jvmPersistentDataRoot(), RECEIPT_PRINTER_DEVICE_FILE_NAME)
    }

    private fun currentOsName(): String = System.getProperty("os.name").orEmpty().lowercase(Locale.ROOT)
    private fun isWindows(): Boolean = currentOsName().contains("win")

    private class WindowsPrinterNotReadyException(message: String) : IllegalStateException(message)

    private class WindowsRawSpoolerUnavailableException(
        message: String,
        cause: Throwable? = null
    ) : IllegalStateException(message, cause)

    private data class WindowsRawPrintSuccess(
        val jobId: Long,
        val byteCount: Int
    )


    private fun throwWindowsRawPrintFailure(output: String, fallbackMessage: String, timedOut: Boolean = false): Nothing {
        // Technical details remain in the local process log, not in notification text.
        System.err.println("AITA native receipt submission failed: " + output.take(2_000).ifBlank { fallbackMessage })
        if (windowsRawFallbackIsSafe(output, timedOut))
            throw WindowsRawSpoolerUnavailableException(deviceWorkflowText("raw_failed"))
        val code = Regex("Win32=(\\d+)").find(output)?.groupValues?.getOrNull(1)
        val submitted = output.contains(WINDOWS_RAW_PRINT_SUBMITTING_MARKER)
        val message = deviceWorkflowText(if (submitted) "raw_uncertain" else "raw_failed")
        error(message + if (code != null) " (Windows: $code)" else "")
    }

    fun loadPersistedEscPosDevicePath() {
        if (!escPosDevicePath.isNullOrBlank()) return
        escPosDevicePath = runCatching {
            receiptPrinterDevicePreferenceFile()
                .takeIf { it.exists() }
                ?.readText()
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    fun configureEscPosDevicePath(path: String?) {
        val cleanPath = path
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        if (cleanPath != null) parseReceiptPrinterTarget(cleanPath)
        val file = receiptPrinterDevicePreferenceFile()
        val parent = file.parentFile

        if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.exists()) {
            error("Could not create receipt-printer settings directory '${parent.absolutePath}'")
        }

        if (cleanPath == null) {
            if (file.exists() && !file.delete()) {
                error("Could not clear saved receipt-printer selection '${file.absolutePath}'")
            }
            escPosDevicePath = null
            return
        }

        val temporary = File(
            parent ?: File(System.getProperty("java.io.tmpdir")),
            ".${file.name}.${UUID.randomUUID()}.tmp"
        )
        try {
            temporary.writeText(cleanPath, Charsets.UTF_8)
            try {
                Files.move(
                    temporary.toPath(),
                    file.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (atomicMoveFailure: IOException) {
                try {
                    Files.move(
                        temporary.toPath(),
                        file.toPath(),
                        StandardCopyOption.REPLACE_EXISTING
                    )
                } catch (fallbackMoveFailure: IOException) {
                    fallbackMoveFailure.addSuppressed(atomicMoveFailure)
                    throw fallbackMoveFailure
                }
            }
        } catch (throwable: Throwable) {
            throw IllegalStateException(
                "Could not save receipt-printer selection '${file.absolutePath}': " +
                        (throwable.message ?: throwable::class.simpleName.orEmpty()),
                throwable
            )
        } finally {
            runCatching { temporary.delete() }
        }

        // Publish the new target only after its durable preference has been committed. This keeps
        // the UI, current process and next launch in one consistent state if disk access fails.
        escPosDevicePath = cleanPath
    }

    private fun likelyReceiptPrinterName(name: String): Boolean {
        val clean = name.lowercase(Locale.ROOT)
        return listOf(
            "aokia",
            "ap58",
            "ap-58",
            "ak-3558",
            "ak3558",
            "xp-58",
            "xp58",
            "xprinter",
            "x-printer",
            "pos58",
            "pos-58",
            "58mm",
            "esc/pos",
            "escpos",
            "receipt",
            "thermal",
            "gprinter",
            "rongta",
            "sunmi",
            "mtp",
            "pos",
            "чек",
            "термо",
            "касс"
        ).any { clean.contains(it) }
    }

    private fun systemPrintServices(): List<PrintService> = runCatching {
        PrintServiceLookup.lookupPrintServices(null, null)
            .orEmpty()
            .toList()
    }.getOrElse { throwable ->
        System.err.println("AITA receipt printer system service lookup failed: ${throwable.message}")
        emptyList()
    }

    private fun defaultPrintServiceName(): String? = runCatching {
        PrintServiceLookup.lookupDefaultPrintService()?.name?.trim()?.takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun configuredTarget(): String = escPosDevicePath?.trim().orEmpty()

    private fun serviceId(serviceName: String): String = PRINT_SERVICE_PREFIX + serviceName

    private fun configuredMatchesPrintService(configured: String, serviceName: String): Boolean {
        if (configured.isBlank()) return false
        val serviceTarget = serviceId(serviceName)
        return configured.equals(serviceTarget, ignoreCase = true) || configured.equals(serviceName, ignoreCase = true)
    }

    private fun printServiceNameFromTarget(target: String): String? {
        val clean = target.trim()
        if (clean.startsWith(PRINT_SERVICE_PREFIX, ignoreCase = true)) {
            return clean.substring(PRINT_SERVICE_PREFIX.length).trim().takeIf { it.isNotBlank() }
        }
        return systemPrintServices()
            .firstOrNull { service -> service.name.equals(clean, ignoreCase = true) }
            ?.name
    }

    private fun printServiceCandidateSubtitle(healthNotes: List<String>): String {
        val base = deviceWorkflowText("raw_receipt_help")
        val healthText = healthNotes.take(4).joinToString(" • ").takeIf { it.isNotBlank() }
        return if (healthText == null) base else "$base • $healthText"
    }

    private fun printAttributeText(value: String): String {
        return value
            .replace('_', ' ')
            .replace('-', ' ')
            .trim()
            .replace(Regex("\\s+"), " ")
            .lowercase(Locale.ROOT)
    }

    private fun printServiceHealthNotes(service: PrintService): List<String> = runCatching {
        val notes = mutableListOf<String>()

        val acceptingJobs = service.getAttribute(PrinterIsAcceptingJobs::class.java)
        if (acceptingJobs == PrinterIsAcceptingJobs.NOT_ACCEPTING_JOBS) {
            notes += "not accepting jobs"
        }

        val printerState = service.getAttribute(PrinterState::class.java)
        if (printerState != null && printerState != PrinterState.IDLE && printerState != PrinterState.UNKNOWN) {
            notes += printAttributeText(printerState.toString())
        }

        val stateReasons = service.getAttribute(PrinterStateReasons::class.java)
        val visibleReasons = stateReasons
            ?.keys
            .orEmpty()
            .map { reason: PrinterStateReason -> printAttributeText(reason.toString()) }
            .filter { reason ->
                reason.isNotBlank() && reason !in setOf(
                    "none",
                    "other",
                    "moving to paused",
                    "connecting to device"
                )
            }
        notes += visibleReasons

        val queuedJobs = service.getAttribute(QueuedJobCount::class.java)?.value
            ?.takeIf { it > 0 }
        if (queuedJobs != null) {
            notes += if (queuedJobs == 1) "1 queued job" else "$queuedJobs queued jobs"
        }

        notes.distinct()
    }.getOrElse { throwable ->
        System.err.println("AITA receipt printer status lookup failed for '${service.name}': ${throwable.message}")
        emptyList()
    }

    private fun printServiceCanAcceptImmediateJobs(healthNotes: List<String>): Boolean {
        if (healthNotes.isEmpty()) return true
        val blockingWords = listOf(
            "not accepting jobs",
            "paused",
            "stopped",
            "shutdown",
            "offline",
            "timed out",
            "media empty",
            "media jam",
            "door open",
            "cover open"
        )
        return healthNotes.none { note -> blockingWords.any { word -> note.contains(word, ignoreCase = true) } }
    }

    private fun listSystemPrintServiceCandidates(configured: String): List<PlatformReceiptPrinterDataModel> {
        val services = systemPrintServices()
        return services.mapNotNull { service ->
            val serviceName = service.name?.trim().orEmpty()
            if (serviceName.isBlank()) return@mapNotNull null
            val isConfigured = configuredMatchesPrintService(configured, serviceName)
            val healthNotes = printServiceHealthNotes(service)
            val details = listOfNotNull(
                runCatching { service.getAttribute(PrinterMakeAndModel::class.java)?.value }.getOrNull(),
                runCatching { service.getAttribute(PrinterInfo::class.java)?.value }.getOrNull(),
                runCatching { service.getAttribute(PrinterLocation::class.java)?.value }.getOrNull()
            ).map { it.trim().take(160) }.filter { it.isNotBlank() && !it.equals(serviceName, true) }.distinct()
            PlatformReceiptPrinterDataModel(
                id = serviceId(serviceName),
                name = serviceName,
                subtitle = printServiceCandidateSubtitle(healthNotes) + details.joinToString(separator = " • ", prefix = if (details.isEmpty()) "" else " • "),
                configured = isConfigured,
                // Windows Java PrintService status attributes are frequently stale for cheap USB
                // thermal drivers. Keep an installed queue selectable; the exact Win32 RAW path
                // performs the authoritative readiness check immediately before printing.
                available = isWindows() || printServiceCanAcceptImmediateJobs(healthNotes)
            )
        }
    }

    private fun serialBaudRate(): Int {
        val raw = System.getenv("AITA_RECEIPT_PRINTER_SERIAL_BAUD")
            ?: System.getProperty("AITA_RECEIPT_PRINTER_SERIAL_BAUD")
        return raw?.trim()?.toIntOrNull()?.takeIf { it in 1_200..921_600 }
            ?: DEFAULT_RECEIPT_PRINTER_SERIAL_BAUD_RATE
    }

    private fun cleanSerialPortName(raw: String): String {
        val clean = raw.trim().removePrefix(SERIAL_PORT_PREFIX).trim()
        return if (clean.startsWith("\\\\.\\")) clean.removePrefix("\\\\.\\") else clean
    }

    private fun configuredMatchesSerialPort(configured: String, systemPortName: String): Boolean {
        if (configured.isBlank() || systemPortName.isBlank()) return false
        val cleanConfigured = cleanSerialPortName(configured)
        return configured.equals(SERIAL_PORT_PREFIX + systemPortName, ignoreCase = true) ||
                cleanConfigured.equals(systemPortName, ignoreCase = true)
    }

    private fun serialPortNameFromTarget(target: String): String? {
        val clean = target.trim()
        if (clean.startsWith(SERIAL_PORT_PREFIX, ignoreCase = true)) {
            return cleanSerialPortName(clean).takeIf { it.isNotBlank() }
        }
        val portName = cleanSerialPortName(clean)
        if (portName.matches(Regex("(?i)^COM\\d+$"))) return portName
        return SerialPort.getCommPorts()
            .orEmpty()
            .firstOrNull { port ->
                port.systemPortName.equals(portName, ignoreCase = true) ||
                        port.descriptivePortName.equals(portName, ignoreCase = true) ||
                        port.portDescription.equals(portName, ignoreCase = true)
            }
            ?.systemPortName
    }

    private fun configuredSerialPortDisplayName(configured: String): String? {
        val clean = configured.trim()
        val portName = cleanSerialPortName(clean)
        return when {
            clean.startsWith(SERIAL_PORT_PREFIX, ignoreCase = true) && portName.isNotBlank() -> portName
            portName.matches(Regex("(?i)^COM\\d+$")) -> portName
            else -> null
        }
    }

    private fun listSerialPortCandidates(configured: String): List<PlatformReceiptPrinterDataModel> {
        return runCatching {
            SerialPort.getCommPorts()
                .orEmpty()
                .mapNotNull { port ->
                    val systemPortName = port.systemPortName?.trim().orEmpty()
                    if (systemPortName.isBlank()) return@mapNotNull null
                    val descriptiveName = port.descriptivePortName?.trim().orEmpty()
                    val portDescription = port.portDescription?.trim().orEmpty()
                    val searchable = listOf(systemPortName, descriptiveName, portDescription).joinToString(" ")
                    val likely = likelyReceiptPrinterName(searchable) || searchable.lowercase(Locale.ROOT).contains("usb")
                    val isConfigured = configuredMatchesSerialPort(configured, systemPortName)
                    if (!likely && !isConfigured) return@mapNotNull null
                    PlatformReceiptPrinterDataModel(
                        id = SERIAL_PORT_PREFIX + systemPortName,
                        name = systemPortName,
                        subtitle = listOf(
                            descriptiveName.takeIf { it.isNotBlank() },
                            portDescription.takeIf { it.isNotBlank() },
                            "Serial ESC/POS ${serialBaudRate()} baud"
                        ).filterNotNull().distinct().joinToString(" • "),
                        configured = isConfigured,
                        available = true
                    )
                }
        }.getOrElse { throwable ->
            System.err.println("AITA receipt printer serial port lookup failed: ${throwable.message}")
            emptyList()
        }
    }

    private fun tcpTargetFrom(target: String): Pair<String, Int>? {
        if (!target.trim().startsWith("tcp:", ignoreCase = true)) return null
        return (parseReceiptPrinterTarget(target) as ReceiptPrinterTarget.Tcp).let { it.host to it.port }
    }

    fun knownEscPosDeviceCandidates(): List<String> {
        val configured = configuredTarget().takeIf { configured ->
            configured.isNotBlank() &&
                    !configured.startsWith(PRINT_SERVICE_PREFIX, ignoreCase = true) &&
                    !configured.startsWith(SERIAL_PORT_PREFIX, ignoreCase = true) &&
                    configuredSerialPortDisplayName(configured) == null &&
                    tcpTargetFrom(configured) == null &&
                    printServiceNameFromTarget(configured) == null
        }
        val candidates = mutableListOf<String>()
        configured?.let { candidates += it }
        if (!isWindows()) {
            candidates += listOf(
                "/dev/usb/lp0",
                "/dev/usb/lp1",
                "/dev/usb/lp2",
                "/dev/ttyUSB0",
                "/dev/ttyUSB1",
                "/dev/ttyUSB2",
                "/dev/ttyACM0",
                "/dev/ttyACM1"
            )
            val dev = File("/dev")
            if (dev.exists() && dev.isDirectory) {
                dev.listFiles()
                    .orEmpty()
                    .filter { file ->
                        val name = file.name.lowercase(Locale.ROOT)
                        name.startsWith("cu.usb") ||
                                name.startsWith("cu.slab") ||
                                name.startsWith("tty.usb") ||
                                name.startsWith("tty.slab")
                    }
                    .forEach { candidates += it.absolutePath }
            }
            val devUsb = File("/dev/usb")
            if (devUsb.exists() && devUsb.isDirectory) {
                devUsb.listFiles()
                    .orEmpty()
                    .filter { file -> file.name.lowercase(Locale.ROOT).startsWith("lp") }
                    .forEach { candidates += it.absolutePath }
            }
        }
        return candidates.distinct()
    }

    private fun normalizedDevicePath(rawPath: String): String {
        val clean = rawPath.trim().let { path ->
            if (path.startsWith(FILE_PREFIX, ignoreCase = true)) path.substring(FILE_PREFIX.length) else path
        }.trim()
        return if (isWindows() && clean.matches(Regex("(?i)^COM\\d+$"))) {
            "\\\\.\\$clean"
        } else {
            clean
        }
    }

    private fun listDevicePathCandidates(configured: String): List<PlatformReceiptPrinterDataModel> {
        return knownEscPosDeviceCandidates()
            .mapNotNull { rawPath ->
                val clean = rawPath.trim().let { path ->
                    if (path.startsWith(FILE_PREFIX, ignoreCase = true)) path.substring(FILE_PREFIX.length) else path
                }.trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val deviceFile = File(normalizedDevicePath(clean))
                val exists = runCatching { deviceFile.exists() }.getOrDefault(false)
                val writable = exists && !deviceFile.isDirectory && deviceFile.canWrite()
                val isConfigured = configured.equals(clean, ignoreCase = true) ||
                    configured.equals(FILE_PREFIX + clean, ignoreCase = true)
                if (!isConfigured && !exists) return@mapNotNull null
                PlatformReceiptPrinterDataModel(
                    id = clean,
                    name = clean.substringAfterLast('/').substringAfterLast('\\').ifBlank { clean },
                    subtitle = when {
                        !exists -> "Saved raw ESC/POS device path is not connected"
                        !writable -> "Device exists but is not writable; check printer/serial group permissions"
                        isConfigured -> "Raw ESC/POS device path • selected"
                        else -> "Detected raw ESC/POS device path"
                    },
                    configured = isConfigured,
                    available = writable
                )
            }
    }

    private fun listConfiguredNetworkCandidate(configured: String): List<PlatformReceiptPrinterDataModel> {
        val target = runCatching { tcpTargetFrom(configured) }.getOrNull() ?: return emptyList()
        return listOf(
            PlatformReceiptPrinterDataModel(
                id = configured,
                name = "${target.first}:${target.second}",
                subtitle = "Network ESC/POS printer • selected",
                configured = true,
                available = true
            )
        )
    }

    private fun configuredPrintServiceDisplayName(configured: String): String? {
        val clean = configured.trim()
        return if (clean.startsWith(PRINT_SERVICE_PREFIX, ignoreCase = true)) {
            clean.substring(PRINT_SERVICE_PREFIX.length).trim().takeIf { it.isNotBlank() }
        } else {
            null
        }
    }

    private fun listSavedUnavailableConfiguredCandidate(
        configured: String,
        detected: List<PlatformReceiptPrinterDataModel>
    ): List<PlatformReceiptPrinterDataModel> {
        if (configured.isBlank()) return emptyList()
        val detectedIds = detected.map { it.id.lowercase(Locale.ROOT) }.toSet()

        configuredPrintServiceDisplayName(configured)?.let { serviceName ->
            val id = serviceId(serviceName)
            if (id.lowercase(Locale.ROOT) !in detectedIds) {
                return listOf(
                    PlatformReceiptPrinterDataModel(
                        id = id,
                        name = serviceName,
                        subtitle = "Saved Windows/System receipt printer • reinstall, reconnect, or refresh after Windows sees it",
                        configured = true,
                        available = false
                    )
                )
            }
        }

        configuredSerialPortDisplayName(configured)?.let { portName ->
            val id = SERIAL_PORT_PREFIX + portName
            if (id.lowercase(Locale.ROOT) !in detectedIds) {
                return listOf(
                    PlatformReceiptPrinterDataModel(
                        id = id,
                        name = portName,
                        subtitle = "Saved serial ESC/POS printer port • reconnect it, then refresh",
                        configured = true,
                        available = false
                    )
                )
            }
        }

        return emptyList()
    }

    fun listConfiguredAndDetectedPrinters(): List<PlatformReceiptPrinterDataModel> {
        loadPersistedEscPosDevicePath()
        val configured = configuredTarget()
        val detected = listConfiguredNetworkCandidate(configured) +
                listDevicePathCandidates(configured) +
                listSerialPortCandidates(configured) +
                listSystemPrintServiceCandidates(configured)
        return (listSavedUnavailableConfiguredCandidate(configured, detected) + detected)
            .distinctBy { it.id.lowercase(Locale.ROOT) }
            .sortedWith(
                compareByDescending<PlatformReceiptPrinterDataModel> { it.configured }
                    .thenByDescending { likelyReceiptPrinterName(it.name + " " + it.subtitle) }
                    .thenByDescending { it.available }
                    .thenBy { it.name.lowercase(Locale.ROOT) }
            )
    }

    private fun printWithJavaPrintService(service: PrintService, printerBytes: ByteArray) {
        val rawFlavor = DocFlavor.BYTE_ARRAY.AUTOSENSE
        if (!runCatching { service.isDocFlavorSupported(rawFlavor) }.getOrDefault(false)) {
            error(
                "Printer '${service.name}' does not expose raw byte printing. " +
                        "Install the manufacturer's Windows driver and select its RAW print queue in AITA."
            )
        }
        val attributes = HashPrintRequestAttributeSet().apply {
            add(JobName("AITA ESC/POS receipt", Locale.getDefault()))
        }
        // Never use TEXT_PLAIN for ESC/POS. A print provider may transcode CP866 bytes and silently
        // corrupt Cyrillic text, barcode commands, or the paper-cut sequence.
        service.createPrintJob().print(SimpleDoc(printerBytes, rawFlavor, null), attributes)
    }

    private fun windowsPowerShellExecutableCandidates(): List<String> = buildList {
        (System.getenv("AITA_POWERSHELL_EXECUTABLE")
            ?: System.getProperty("AITA_POWERSHELL_EXECUTABLE"))
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let(::add)

        System.getenv("SystemRoot")?.takeIf { it.isNotBlank() }?.let { root ->
            add(File(root, "System32/WindowsPowerShell/v1.0/powershell.exe").absolutePath)
        }
        System.getenv("ProgramFiles")?.takeIf { it.isNotBlank() }?.let { root ->
            add(File(root, "PowerShell/7/pwsh.exe").absolutePath)
        }
        add("powershell.exe")
        add("pwsh.exe")
        add("pwsh")
    }.distinctBy { it.lowercase(Locale.ROOT) }

    private fun receiptPrinterSpoolDirectory(): File =
        File(cacheDirPath.ifBlank { System.getProperty("java.io.tmpdir") }, "receipt_printer_spool")

    private fun cleanupStaleWindowsRawPrintFiles(spoolDir: File) {
        val cutoff = System.currentTimeMillis() - WINDOWS_SPOOL_FILE_MAX_AGE_MILLIS
        spoolDir.listFiles()
            .orEmpty()
            .filter { file ->
                file.isFile &&
                        file.lastModified() in 1 until cutoff &&
                        (file.name.startsWith("aita_receipt_") || file.name.startsWith("aita_raw_print_"))
            }
            .forEach { file -> runCatching { file.delete() } }
    }

    private fun readWindowsRawPrintOutput(outputFile: File): String {
        val bytes = runCatching { outputFile.readBytes() }.getOrDefault(ByteArray(0))
        if (bytes.isEmpty()) return ""

        val utf8 = bytes.toString(Charsets.UTF_8).replace("\u0000", "").trim()
        val utf16 = runCatching { bytes.toString(Charsets.UTF_16LE).trimStart('\uFEFF').trim() }
            .getOrDefault("")
        return when {
            utf8.contains(WINDOWS_RAW_PRINT_SUCCESS_MARKER) ||
                    utf8.contains(WINDOWS_RAW_PRINT_JOB_STARTED_MARKER) ||
                    utf8.contains(WINDOWS_PRINTER_NOT_READY_MARKER) -> utf8
            utf16.contains(WINDOWS_RAW_PRINT_SUCCESS_MARKER) ||
                    utf16.contains(WINDOWS_RAW_PRINT_JOB_STARTED_MARKER) ||
                    utf16.contains(WINDOWS_PRINTER_NOT_READY_MARKER) -> utf16
            utf8.length >= utf16.length -> utf8
            else -> utf16
        }
    }

    private fun parseWindowsRawPrintSuccess(output: String, expectedByteCount: Int): WindowsRawPrintSuccess? {
        val match = Regex(
            Regex.escape(WINDOWS_RAW_PRINT_SUCCESS_MARKER) + "job=(\\d+);bytes=(\\d+)"
        ).find(output) ?: return null
        val jobId = match.groupValues[1].toLongOrNull() ?: return null
        val byteCount = match.groupValues[2].toIntOrNull() ?: return null
        if (jobId <= 0L || byteCount != expectedByteCount) return null
        return WindowsRawPrintSuccess(jobId = jobId, byteCount = byteCount)
    }

    private fun startWindowsRawPrintProcess(
        argumentFile: File, outputFile: File, serviceName: String, dataFile: File
    ): Process {
        val installedLauncher = System.getProperty("jpackage.app-path")?.let(::File)
            ?.takeIf { it.isAbsolute && it.isFile && it.name.equals("AITA.exe", true) }
        if (installedLauncher != null) {
            return ProcessBuilder(installedLauncher.absolutePath, "--aita-native-print", serviceName, dataFile.absolutePath)
                .redirectErrorStream(true).redirectOutput(outputFile).start()
        }
        val java = File(System.getProperty("java.home"), "bin/javaw.exe")
        val executable = java.takeIf { it.isFile } ?: File(System.getProperty("java.home"), "bin/java.exe")
        argumentFile.writeText(listOf("-cp", System.getProperty("java.class.path"),
            "kz.aita.WindowsRawPrintProcess", serviceName, dataFile.absolutePath)
            .joinToString("\n", transform = ::javaLauncherArgument), Charsets.UTF_8)
        return try {
            ProcessBuilder(executable.absolutePath, "@" + argumentFile.absolutePath)
                .redirectErrorStream(true).redirectOutput(outputFile).start()
        } catch (failure: IOException) {
            throw WindowsRawSpoolerUnavailableException("Native receipt helper could not start", failure)
        }
    }

    private fun printWithWindowsRawSpooler(serviceName: String, printerBytes: ByteArray) {
        val spoolDir = receiptPrinterSpoolDirectory().apply { mkdirs() }
        cleanupStaleWindowsRawPrintFiles(spoolDir)

        val dataFile = File.createTempFile("aita_receipt_", ".bin", spoolDir)
        val scriptFile = File.createTempFile("aita_raw_print_", ".args", spoolDir)
        val outputFile = File.createTempFile("aita_raw_print_", ".log", spoolDir)
        var process: Process? = null
        try {
            dataFile.writeBytes(printerBytes)
            if (dataFile.length() != printerBytes.size.toLong()) {
                error("Could not stage all receipt bytes for the Windows print spooler")
            }
            process = startWindowsRawPrintProcess(scriptFile, outputFile, serviceName, dataFile)

            val completed = process.waitFor(WINDOWS_RAW_PRINT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
                process.waitFor(2, TimeUnit.SECONDS)
                val finalOutput = readWindowsRawPrintOutput(outputFile)
                val acceptedJob = parseWindowsRawPrintSuccess(finalOutput, printerBytes.size)
                if (acceptedJob != null) {
                    println(
                        "AITA receipt printer Windows RAW accepted before process timeout: " +
                                "job=${acceptedJob.jobId} bytes=${acceptedJob.byteCount}"
                    )
                    return
                }
                throwWindowsRawPrintFailure(
                    output = finalOutput,
                    timedOut = true,
                    fallbackMessage = buildString {
                        append("Windows RAW print timed out before the spooler confirmed the receipt")
                        if (finalOutput.isNotBlank()) append(": ").append(finalOutput.take(1_000))
                    }
                )
            }

            val output = readWindowsRawPrintOutput(outputFile)
            val success = parseWindowsRawPrintSuccess(output, printerBytes.size)
            if (success != null) {
                println(
                    "AITA receipt printer Windows RAW accepted: " +
                            "printer='$serviceName' job=${success.jobId} bytes=${success.byteCount}"
                )
                return
            }

            if (process.exitValue() != 0) {
                throwWindowsRawPrintFailure(
                    output = output,
                    fallbackMessage = "Windows RAW print failed with exit code ${process.exitValue()}"
                )
            }
            throwWindowsRawPrintFailure(
                output = output,
                fallbackMessage = "Windows RAW print finished without a spooler success confirmation"
            )
        } finally {
            if (process?.isAlive == true) runCatching { process.destroyForcibly() }
            runCatching { dataFile.delete() }
            runCatching { scriptFile.delete() }
            runCatching { outputFile.delete() }
        }
    }

    private fun writeToPrintService(serviceName: String, printerBytes: ByteArray): Boolean {
        if (isWindows()) {
            // Win32 OpenPrinter accepts the exact saved queue even when Java enumeration is stale
            // or the bundled JVM driver does not expose it. In particular, no COM-port scan belongs here.
            try {
                printWithWindowsRawSpooler(serviceName, printerBytes)
                return true
            } catch (failure: WindowsRawSpoolerUnavailableException) {
                System.err.println("AITA Windows RAW bridge failed before submission; checking Java RAW fallback")
                val service = systemPrintServices().firstOrNull { it.name.equals(serviceName, ignoreCase = true) }
                    ?: throw failure
                printWithJavaPrintService(service, printerBytes)
                return true
            }
        }
        val service = systemPrintServices().firstOrNull { it.name.equals(serviceName, ignoreCase = true) }
            ?: error("Printer '$serviceName' is not installed. Refresh printers and select its current queue.")
        val healthNotes = printServiceHealthNotes(service)
        if (!printServiceCanAcceptImmediateJobs(healthNotes)) error(
            "Printer '${service.name}' is not ready: ${healthNotes.joinToString(", ")}. Check its queue, paper and cable."
        )
        printWithJavaPrintService(service, printerBytes)
        return true
    }

    private fun writeToSerialPort(portName: String, printerBytes: ByteArray): Boolean {
        val cleanPortName = cleanSerialPortName(portName)
        val baudRate = serialBaudRate()
        val port = SerialPort.getCommPorts()
            .orEmpty()
            .firstOrNull { candidate -> candidate.systemPortName.equals(cleanPortName, ignoreCase = true) }
            ?: SerialPort.getCommPort(cleanPortName)
        port.setComPortParameters(baudRate, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY)
        port.setComPortTimeouts(SerialPort.TIMEOUT_WRITE_BLOCKING, 2_000, 10_000)
        if (!port.openPort(5_000)) {
            error("Could not open serial printer port $cleanPortName. Close other POS software and reconnect the USB cable.")
        }
        var output: java.io.OutputStream? = null
        return try {
            val stream = port.outputStream.also { output = it }
            stream.write(printerBytes)
            stream.flush()

            // jSerialComm's stream flush confirms the application buffer, not necessarily the USB
            // adapter's transmit queue. Keep the port open while the native queue drains.
            val expectedDrainMillis = ((printerBytes.size.toLong() * 10L * 1_000L) / baudRate)
                .plus(1_500L)
                .coerceIn(1_500L, 60_000L)
            val deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(expectedDrainMillis)
            var queuedBytes = runCatching { port.bytesAwaitingWrite() }.getOrDefault(-1)
            if (queuedBytes < 0) {
                // A few Windows USB-to-serial drivers cannot expose their native transmit queue.
                // In that case keep the port open for the calculated wire time instead of closing it
                // immediately after Java's buffer flush and truncating the tail of the receipt.
                Thread.sleep(expectedDrainMillis)
            } else {
                while (queuedBytes > 0 && System.nanoTime() < deadlineNanos) {
                    Thread.sleep(20L)
                    queuedBytes = runCatching { port.bytesAwaitingWrite() }.getOrDefault(-1)
                }
                if (queuedBytes > 0) {
                    error("Serial printer $cleanPortName did not finish sending $queuedBytes byte(s) before timeout")
                }
                // Give inexpensive USB adapters a tiny hardware-tail margin after the driver queue
                // reaches zero before closing the COM handle.
                Thread.sleep(150L)
            }
            true
        } finally {
            runCatching { output?.close() }
            runCatching { port.closePort() }
        }
    }

    private val tcpWriteDeadline = java.util.concurrent.Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "aita-printer-write-deadline").apply { isDaemon = true }
    }

    private fun writeToTcpPrinter(target: Pair<String, Int>, printerBytes: ByteArray): Boolean {
        Socket().use { socket ->
            val expired = java.util.concurrent.atomic.AtomicBoolean(false)
            val deadline = tcpWriteDeadline.schedule({
                expired.set(true)
                runCatching { socket.close() }
            }, receiptPrinterWriteTimeoutMillis(printerBytes.size), TimeUnit.MILLISECONDS)
            var sending = false
            try {
                socket.tcpNoDelay = true
                socket.connect(InetSocketAddress(target.first, target.second), TCP_PRINTER_CONNECT_TIMEOUT_MILLIS)
                val output = socket.getOutputStream()
                sending = true
                output.write(printerBytes)
                output.flush()
                socket.shutdownOutput()
            } catch (error: IOException) {
                val prefix = if (expired.get()) "Printer write timed out" else "Could not send to network printer ${target.first}:${target.second}"
                throw IOException(prefix + if (sending) ". Some bytes may have been received; check the receipt before retrying." else ". Check its address, power and local network.", error)
            } finally {
                deadline.cancel(false)
            }
        }
        return true
    }

    private fun writeToDeviceFile(target: String, printerBytes: ByteArray): Boolean {
        val normalizedPath = normalizedDevicePath(target)
        if (normalizedPath.isBlank()) error("Receipt printer device path is empty")
        val file = File(normalizedPath)
        if (!file.exists()) {
            error("Receipt printer device '$normalizedPath' does not exist. Reconnect it and refresh printers in AITA.")
        }
        if (file.isDirectory) error("Receipt printer device '$normalizedPath' is a directory")
        if (!file.canWrite()) {
            error("Receipt printer device '$normalizedPath' is not writable. Check OS permissions for the AITA user.")
        }
        file.outputStream().use { output ->
            output.write(printerBytes)
            output.flush()
        }
        return true
    }

    suspend fun writeEscPosBytesToConfiguredPrinter(printerBytes: ByteArray): Boolean {
        if (printerBytes.isEmpty()) error("Receipt contains no ESC/POS bytes")
        if (printerBytes.size > MAX_RECEIPT_PRINTER_BYTES) {
            error(
                "Receipt print payload is too large (${printerBytes.size} bytes; " +
                        "maximum is $MAX_RECEIPT_PRINTER_BYTES bytes)"
            )
        }
        if (!receiptPrinterWriteMutex.tryLock()) {
            error("Another receipt is already being sent to the printer. Wait for it to finish, then retry.")
        }

        val stablePrinterBytes = printerBytes.copyOf()
        try {
            writeEscPosBytes?.let { customWriter ->
                return customWriter(stablePrinterBytes)
            }

            loadPersistedEscPosDevicePath()
            val target = configuredTarget().takeIf { it.isNotBlank() } ?: return false
            check(target != SYSTEM_DOCUMENT_PRINTER_ID) { deviceWorkflowText("receipt_driver_help") }
            val startedAtNanos = System.nanoTime()
            val parsed = parseReceiptPrinterTarget(target)
            return runInterruptible(Dispatchers.IO) {
                println("AITA receipt printer write start targetKind=${parsed.javaClass.simpleName} bytes=${stablePrinterBytes.size}")
                val result = when (parsed) {
                    is ReceiptPrinterTarget.SystemQueue -> writeToPrintService(parsed.name, stablePrinterBytes)
                    is ReceiptPrinterTarget.Serial -> try { writeToSerialPort(parsed.name, stablePrinterBytes) }
                        catch (error: LinkageError) { throw IllegalStateException("Serial driver unavailable. A USB printer installed in Windows must be selected as a Windows printer queue, not a COM port.", error) }
                    is ReceiptPrinterTarget.Tcp -> writeToTcpPrinter(parsed.host to parsed.port, stablePrinterBytes)
                    is ReceiptPrinterTarget.DeviceFile -> writeToDeviceFile(parsed.path, stablePrinterBytes)
                    is ReceiptPrinterTarget.LegacyName -> {
                        // Old saved Windows queue names did not have the print-service: prefix.
                        if (isWindows()) writeToPrintService(parsed.name, stablePrinterBytes)
                        else {
                            val queue = printServiceNameFromTarget(parsed.name)
                            if (queue != null) writeToPrintService(queue, stablePrinterBytes)
                            else {
                                val serial = runCatching { serialPortNameFromTarget(parsed.name) }.getOrNull()
                                if (serial != null) writeToSerialPort(serial, stablePrinterBytes)
                                else writeToDeviceFile(parsed.name, stablePrinterBytes)
                            }
                        }
                    }
                }
                println("AITA receipt printer write finish targetKind=${parsed.javaClass.simpleName} bytes=${stablePrinterBytes.size} elapsed=${TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos)}ms")
                result
            }
        } finally {
            // Do not overwrite stablePrinterBytes here. Java PrintService providers are allowed to
            // consume BYTE_ARRAY documents asynchronously after print() returns; clearing the array
            // can otherwise turn a valid fallback job into a blank or corrupted receipt.
            receiptPrinterWriteMutex.unlock()
        }
    }
}


actual object LocalAitaLanTransport {
    private const val MAX_LAN_MESSAGE_BYTES = 2 * 1024 * 1024
    private val lifecycleLock = Any()
    private val running = java.util.concurrent.atomic.AtomicBoolean(false)
    private val activeTcpClientSlots = java.util.concurrent.Semaphore(24)

    @Volatile
    private var tcpServer: java.net.ServerSocket? = null

    @Volatile
    private var udpSocket: java.net.DatagramSocket? = null

    @Volatile
    private var tcpThread: Thread? = null

    @Volatile
    private var udpThread: Thread? = null

    private fun readBoundedUtf8(input: java.io.InputStream): String {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            if (output.size() + count > MAX_LAN_MESSAGE_BYTES) {
                error("AITA LAN message exceeds $MAX_LAN_MESSAGE_BYTES bytes")
            }
            output.write(buffer, 0, count)
        }
        return output.toByteArray().toString(Charsets.UTF_8)
    }

    actual fun start(
        deviceId: String,
        tcpPort: Int,
        discoveryPort: Int,
        onMessage: suspend (message: String, senderHost: String) -> String
    ): Boolean = synchronized(lifecycleLock) {
        if (running.get()) {
            val samePorts = tcpServer?.localPort == tcpPort && udpSocket?.localPort == discoveryPort
            if (!samePorts) {
                System.err.println(
                    "AITA LAN transport is already running on tcp=${tcpServer?.localPort} " +
                        "discovery=${udpSocket?.localPort}; refusing a second configuration"
                )
            }
            return@synchronized samePorts
        }
        if (tcpPort !in 1..65535 || discoveryPort !in 1..65535) return@synchronized false

        val preparedTcpServer = java.net.ServerSocket()
        val preparedUdpSocket = java.net.DatagramSocket(null)
        try {
            preparedTcpServer.reuseAddress = true
            preparedTcpServer.bind(java.net.InetSocketAddress(tcpPort))

            preparedUdpSocket.reuseAddress = true
            preparedUdpSocket.broadcast = true
            preparedUdpSocket.bind(java.net.InetSocketAddress(discoveryPort))

            tcpServer = preparedTcpServer
            udpSocket = preparedUdpSocket
            running.set(true)

            tcpThread = kotlin.concurrent.thread(
                name = "AITA-LAN-TCP-$deviceId",
                isDaemon = true
            ) {
                while (running.get()) {
                    val client = try {
                        preparedTcpServer.accept()
                    } catch (throwable: Throwable) {
                        if (running.get() && !preparedTcpServer.isClosed) {
                            System.err.println("AITA LAN TCP accept failed: ${throwable.message}")
                        }
                        break
                    }

                    if (!activeTcpClientSlots.tryAcquire()) {
                        runCatching { client.close() }
                        System.err.println("AITA LAN TCP connection rejected: too many simultaneous clients")
                        continue
                    }
                    try {
                        kotlin.concurrent.thread(name = "AITA-LAN-TCP-CLIENT", isDaemon = true) {
                            try {
                                client.use { socket ->
                                    runCatching {
                                        socket.soTimeout = 5_000
                                        val message = readBoundedUtf8(socket.getInputStream())
                                        val response = kotlinx.coroutines.runBlocking {
                                            onMessage(message, socket.inetAddress?.hostAddress.orEmpty())
                                        }
                                        if (response.isNotBlank()) {
                                            val responseBytes = response.toByteArray(Charsets.UTF_8)
                                            if (responseBytes.size > MAX_LAN_MESSAGE_BYTES) {
                                                error("AITA LAN response exceeds $MAX_LAN_MESSAGE_BYTES bytes")
                                            }
                                            socket.getOutputStream().apply {
                                                write(responseBytes)
                                                flush()
                                            }
                                        }
                                    }.onFailure { throwable ->
                                        if (running.get()) {
                                            System.err.println("AITA LAN TCP client failed: ${throwable.message}")
                                        }
                                    }
                                }
                            } finally {
                                activeTcpClientSlots.release()
                            }
                        }
                    } catch (threadFailure: Throwable) {
                        activeTcpClientSlots.release()
                        runCatching { client.close() }
                        if (running.get()) {
                            System.err.println("AITA LAN TCP client thread could not start: ${threadFailure.message}")
                        }
                    }
                }
            }

            udpThread = kotlin.concurrent.thread(
                name = "AITA-LAN-UDP-$deviceId",
                isDaemon = true
            ) {
                val buffer = ByteArray(65_507)
                while (running.get()) {
                    val packet = java.net.DatagramPacket(buffer, buffer.size)
                    try {
                        preparedUdpSocket.receive(packet)
                    } catch (throwable: Throwable) {
                        if (running.get() && !preparedUdpSocket.isClosed) {
                            System.err.println("AITA LAN UDP receive failed: ${throwable.message}")
                        }
                        break
                    }

                    runCatching {
                        val message = packet.data
                            .copyOfRange(packet.offset, packet.offset + packet.length)
                            .toString(Charsets.UTF_8)
                        val response = kotlinx.coroutines.runBlocking {
                            onMessage(message, packet.address?.hostAddress.orEmpty())
                        }
                        if (response.isNotBlank()) {
                            val responseBytes = response.toByteArray(Charsets.UTF_8)
                            if (responseBytes.size <= 65_507) {
                                preparedUdpSocket.send(
                                    java.net.DatagramPacket(
                                        responseBytes,
                                        responseBytes.size,
                                        packet.address,
                                        packet.port
                                    )
                                )
                            }
                        }
                    }.onFailure { throwable ->
                        if (running.get()) {
                            System.err.println("AITA LAN UDP message failed: ${throwable.message}")
                        }
                    }
                }
            }
            true
        } catch (throwable: Throwable) {
            running.set(false)
            runCatching { preparedTcpServer.close() }
            runCatching { preparedUdpSocket.close() }
            tcpServer = null
            udpSocket = null
            tcpThread = null
            udpThread = null
            System.err.println(
                "AITA LAN transport could not bind tcp=$tcpPort discovery=$discoveryPort: ${throwable.message}"
            )
            false
        }
    }

    actual fun stop() {
        synchronized(lifecycleLock) {
            running.set(false)
            val oldTcpThread = tcpThread
            val oldUdpThread = udpThread
            runCatching { tcpServer?.close() }
            runCatching { udpSocket?.close() }
            tcpServer = null
            udpSocket = null
            tcpThread = null
            udpThread = null
            runCatching { oldTcpThread?.interrupt() }
            runCatching { oldUdpThread?.interrupt() }
        }
    }

    private fun broadcastAddresses(): List<java.net.InetAddress> {
        val addresses = linkedSetOf<java.net.InetAddress>()
        runCatching { addresses += java.net.InetAddress.getByName("255.255.255.255") }
        runCatching {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
                ?.let { java.util.Collections.list(it) }
                .orEmpty()
            interfaces
                .filter { networkInterface ->
                    runCatching {
                        networkInterface.isUp &&
                            !networkInterface.isLoopback &&
                            !networkInterface.isVirtual
                    }.getOrDefault(false)
                }
                .flatMap { it.interfaceAddresses.orEmpty() }
                .mapNotNullTo(addresses) { it.broadcast }
        }
        return addresses.toList()
    }

    actual fun broadcast(message: String, discoveryPort: Int) {
        if (discoveryPort !in 1..65535) return
        val bytes = message.toByteArray(Charsets.UTF_8)
        if (bytes.isEmpty() || bytes.size > 65_507) return
        runCatching {
            java.net.DatagramSocket().use { socket ->
                socket.broadcast = true
                broadcastAddresses().forEach { address ->
                    runCatching {
                        socket.send(java.net.DatagramPacket(bytes, bytes.size, address, discoveryPort))
                    }
                }
            }
        }.onFailure { throwable ->
            System.err.println("AITA LAN broadcast failed: ${throwable.message}")
        }
    }

    actual suspend fun send(
        host: String,
        port: Int,
        message: String,
        timeoutMillis: Int
    ): String? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val cleanHost = host.trim()
        if (cleanHost.isBlank() || port !in 1..65535) return@withContext null
        val stableTimeout = timeoutMillis.coerceIn(250, 60_000)
        val requestBytes = message.toByteArray(Charsets.UTF_8)
        if (requestBytes.size > MAX_LAN_MESSAGE_BYTES) return@withContext null

        runCatching {
            java.net.Socket().use { socket ->
                socket.soTimeout = stableTimeout
                socket.connect(java.net.InetSocketAddress(cleanHost, port), stableTimeout)
                socket.getOutputStream().apply {
                    write(requestBytes)
                    flush()
                }
                runCatching { socket.shutdownOutput() }
                readBoundedUtf8(socket.getInputStream()).ifBlank { null }
            }
        }.getOrNull()
    }

    private fun routeSelectedIpv4Address(): String? = runCatching {
        java.net.DatagramSocket().use { socket ->
            socket.connect(java.net.InetSocketAddress("1.1.1.1", 53))
            (socket.localAddress as? java.net.Inet4Address)
                ?.takeUnless { it.isLoopbackAddress || it.isAnyLocalAddress }
                ?.hostAddress
        }
    }.getOrNull()

    actual fun localHostAddress(): String {
        routeSelectedIpv4Address()?.let { return it }

        return runCatching {
            data class Candidate(val score: Int, val address: String)

            val ignoredInterfacePrefixes = listOf(
                "docker", "veth", "br-", "virbr", "vmnet", "vboxnet", "tun", "tap", "tailscale", "zt"
            )
            val preferredInterfacePrefixes = listOf("en", "eth", "wl", "wlan")

            java.net.NetworkInterface.getNetworkInterfaces()
                ?.let { java.util.Collections.list(it) }
                .orEmpty()
                .filter { networkInterface ->
                    runCatching {
                        networkInterface.isUp &&
                            !networkInterface.isLoopback &&
                            !networkInterface.isVirtual &&
                            ignoredInterfacePrefixes.none {
                                prefix -> networkInterface.name.orEmpty().lowercase(Locale.ROOT).startsWith(prefix)
                            }
                    }.getOrDefault(false)
                }
                .flatMap { networkInterface ->
                    val interfaceName = networkInterface.name.orEmpty().lowercase(Locale.ROOT)
                    java.util.Collections.list(networkInterface.inetAddresses).mapNotNull { address ->
                        val ipv4 = address as? java.net.Inet4Address ?: return@mapNotNull null
                        if (ipv4.isLoopbackAddress || ipv4.isAnyLocalAddress || ipv4.isLinkLocalAddress) {
                            return@mapNotNull null
                        }
                        val score =
                            (if (ipv4.isSiteLocalAddress) 100 else 0) +
                            (if (preferredInterfacePrefixes.any { interfaceName.startsWith(it) }) 40 else 0) +
                            (if (runCatching { networkInterface.supportsMulticast() }.getOrDefault(false)) 10 else 0)
                        Candidate(score, ipv4.hostAddress)
                    }
                }
                .maxByOrNull { it.score }
                ?.address
                ?: java.net.InetAddress.getLocalHost().hostAddress
                ?: "127.0.0.1"
        }.getOrDefault("127.0.0.1")
    }
}
