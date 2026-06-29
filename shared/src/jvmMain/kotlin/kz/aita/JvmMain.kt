package kz.aita

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.ktor.client.engine.*
import io.ktor.client.engine.okhttp.*
import com.fazecast.jSerialComm.SerialPort
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.file.Files
import java.util.*
import java.util.concurrent.TimeUnit
import javax.print.DocFlavor
import javax.print.PrintService
import javax.print.PrintServiceLookup
import javax.print.SimpleDoc
import javax.print.attribute.HashPrintRequestAttributeSet
import javax.print.attribute.standard.JobName
import javax.print.attribute.standard.PrinterIsAcceptingJobs
import javax.print.attribute.standard.PrinterState
import javax.print.attribute.standard.PrinterStateReason
import javax.print.attribute.standard.PrinterStateReasons
import javax.print.attribute.standard.QueuedJobCount
import kotlin.io.path.Path

actual fun getCurrentTimeMillis(): Long = System.currentTimeMillis()
actual var getStoredUserAuthTokens: (() -> TokenPair?)? = null
actual var setStoredUserAuthTokens: ((TokenPair?) -> Unit)? = null

actual var getStoredUserAccountDataModel: (() -> UserAccountDataModel?)? = null
actual var setStoredUserAccountDataModel: ((UserAccountDataModel?) -> Unit)? = null

private fun persistentUiDraftFileForKey(key: String): File {
    val safeName = key
        .map { char -> if (char.isLetterOrDigit() || char == '-' || char == '_') char else '_' }
        .joinToString("")
        .let { if (it.length <= 140) it else it.take(96) + "_" + key.hashCode().toUInt().toString(16) }
    val dir = File(cacheDirPath.ifBlank { System.getProperty("java.io.tmpdir") }, "ui_drafts")
    return File(dir, "$safeName.txt")
}

actual var getPersistentUiDraftValue: (suspend (String) -> String?)? = { key ->
    runCatching {
        val file = persistentUiDraftFileForKey(key)
        if (file.exists()) file.readText() else null
    }.getOrNull()
}

actual var setPersistentUiDraftValue: (suspend (String, String?) -> Unit)? = { key, value ->
    runCatching {
        val file = persistentUiDraftFileForKey(key)
        file.parentFile?.mkdirs()
        if (value == null) {
            file.delete()
        } else {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(value)
            tmp.renameTo(file) || run { file.writeText(value); true }
        }
    }
    Unit
}

actual var cacheDirPath: String = ""
actual val Dispatchers.ourIo: CoroutineDispatcher
    get() = Dispatchers.IO

private fun buildAitaOkHttpClient(): OkHttpClient {
    val builder = OkHttpClient.Builder()
    runCatching {
        cacheDirPath
            .trim()
            .takeIf { it.isNotBlank() }
            ?.let { basePath ->
                val httpCacheDirectory = File(basePath, "http").apply { mkdirs() }
                builder.cache(Cache(httpCacheDirectory, cacheSize))
            }
    }
    return builder.build()
}

actual var getHttpClientEngine: () -> HttpClientEngine = {
    OkHttp.create {
        preconfigured = buildAitaOkHttpClient()
    }
}

actual var getSystemLocaleLanguage: () -> String = {
    Locale.getDefault()?.language ?: "ru"
}

actual var getPlatformName: () -> String = {
    "jvm"
}

actual var getSqlDelightDriver: (() -> SqlDriver?)? = {
    Unit.run {
        val dir = Path(cacheDirPath)
        val dbPath = dir.resolve("app_database.db").toAbsolutePath()

        val url = "jdbc:sqlite:$dbPath"
        val firstRun = !Files.exists(dbPath)

        val driver: SqlDriver = JdbcSqliteDriver(url)

        val schema = AppDatabase.Schema.synchronous()
        if (firstRun) {
            schema.create(driver)
        } else {
            val cursor = driver
                .executeQuery(
                    identifier = null,
                    sql = "PRAGMA user_version",
                    parameters = 0,
                    mapper = { cursor: SqlCursor ->
                        QueryResult.Value(
                            if (cursor.next().value)
                                cursor.getLong(0)?.toInt() ?: 0
                            else
                                0
                        )
                    }
                )
            val currentVersion = cursor.value
            val targetVersion = AppDatabase.Schema.version.toInt()
            if (currentVersion < targetVersion) {
                schema.migrate(driver, currentVersion.toLong(), schema.version)
            }
        }

        driver
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

    private fun receiptPrinterDevicePreferenceFile(): File {
        return File(cacheDirPath.ifBlank { System.getProperty("java.io.tmpdir") }, RECEIPT_PRINTER_DEVICE_FILE_NAME)
    }

    private fun currentOsName(): String = System.getProperty("os.name").orEmpty().lowercase(Locale.ROOT)
    private fun isWindows(): Boolean = currentOsName().contains("win")

    private class WindowsPrinterNotReadyException(message: String) : IllegalStateException(message)

    private fun Throwable.isWindowsPrinterNotReadyFailure(): Boolean {
        var cursor: Throwable? = this
        while (cursor != null) {
            if (cursor is WindowsPrinterNotReadyException) return true
            if (cursor.message?.contains(WINDOWS_PRINTER_NOT_READY_MARKER) == true) return true
            cursor = cursor.cause
        }
        return false
    }

    private fun throwWindowsRawPrintFailure(output: String, fallbackMessage: String): Nothing {
        val markerIndex = output.indexOf(WINDOWS_PRINTER_NOT_READY_MARKER)
        if (markerIndex >= 0) {
            val cleanMessage = output
                .substring(markerIndex + WINDOWS_PRINTER_NOT_READY_MARKER.length)
                .lineSequence()
                .firstOrNull()
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: "Windows reports the receipt printer is not ready"
            throw WindowsPrinterNotReadyException(cleanMessage)
        }
        error(output.ifBlank { fallbackMessage })
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
        escPosDevicePath = cleanPath
        runCatching {
            val file = receiptPrinterDevicePreferenceFile()
            file.parentFile?.mkdirs()
            if (cleanPath == null) file.delete() else file.writeText(cleanPath)
        }.onFailure { throwable ->
            System.err.println("AITA receipt printer preference write failed: ${throwable.message}")
        }
    }

    private fun likelyReceiptPrinterName(name: String): Boolean {
        val clean = name.lowercase(Locale.ROOT)
        return listOf(
            "aokia",
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

    private fun printServiceCandidateSubtitle(
        configured: Boolean,
        probableReceiptPrinter: Boolean,
        defaultPrinter: Boolean,
        healthNotes: List<String>
    ): String {
        val platform = if (isWindows()) "Windows" else "System"
        val base = when {
            configured -> "$platform RAW ESC/POS printer • selected"
            probableReceiptPrinter && defaultPrinter -> "$platform default printer • likely AOKIA/XP-58 thermal receipt printer"
            probableReceiptPrinter -> "$platform printer • likely AOKIA/XP-58 thermal receipt printer"
            defaultPrinter -> "$platform default printer • choose only if this is the thermal ESC/POS printer"
            else -> "$platform printer • choose only if it accepts raw ESC/POS receipt bytes"
        }
        val healthText = healthNotes.take(4).joinToString(" • ").takeIf { it.isNotBlank() }
        return if (healthText == null) base else "$base • Status: $healthText"
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
        val defaultServiceName = defaultPrintServiceName()
        val services = systemPrintServices()
        val includeAllWindowsServices = isWindows() && services.size <= 12
        return services.mapNotNull { service ->
            val serviceName = service.name?.trim().orEmpty()
            if (serviceName.isBlank()) return@mapNotNull null
            val probableReceiptPrinter = likelyReceiptPrinterName(serviceName)
            val isConfigured = configuredMatchesPrintService(configured, serviceName)
            val isDefault = defaultServiceName?.equals(serviceName, ignoreCase = true) == true
            if (!probableReceiptPrinter && !isConfigured && !isDefault && !includeAllWindowsServices) return@mapNotNull null
            val healthNotes = printServiceHealthNotes(service)
            PlatformReceiptPrinterDataModel(
                id = serviceId(serviceName),
                name = serviceName,
                subtitle = printServiceCandidateSubtitle(
                    configured = isConfigured,
                    probableReceiptPrinter = probableReceiptPrinter,
                    defaultPrinter = isDefault,
                    healthNotes = healthNotes
                ),
                configured = isConfigured,
                available = printServiceCanAcceptImmediateJobs(healthNotes)
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
        val clean = target.trim()
        val hostPort = when {
            clean.startsWith(TCP_PREFIX, ignoreCase = true) -> clean.substring(TCP_PREFIX.length)
            clean.startsWith(TCP_SHORT_PREFIX, ignoreCase = true) -> clean.substring(TCP_SHORT_PREFIX.length)
            else -> return null
        }.trim().trim('/')
        val host = hostPort.substringBefore(':').trim()
        val port = hostPort.substringAfter(':', "9100").trim().toIntOrNull() ?: 9100
        return host.takeIf { it.isNotBlank() }?.let { it to port }
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
                val exists = runCatching { File(normalizedDevicePath(clean)).exists() }.getOrDefault(false)
                val isConfigured = configured.equals(clean, ignoreCase = true) || configured.equals(FILE_PREFIX + clean, ignoreCase = true)
                if (!isConfigured && !exists) return@mapNotNull null
                PlatformReceiptPrinterDataModel(
                    id = clean,
                    name = clean.substringAfterLast('/').substringAfterLast('\\').ifBlank { clean },
                    subtitle = if (isConfigured) "Raw ESC/POS device path • selected" else "Detected raw ESC/POS device path",
                    configured = isConfigured,
                    available = exists || isConfigured
                )
            }
    }

    private fun listConfiguredNetworkCandidate(configured: String): List<PlatformReceiptPrinterDataModel> {
        val target = tcpTargetFrom(configured) ?: return emptyList()
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
        val preferredFlavors = listOf(
            DocFlavor.BYTE_ARRAY.AUTOSENSE,
            DocFlavor.BYTE_ARRAY.TEXT_PLAIN_HOST,
            DocFlavor.BYTE_ARRAY.TEXT_PLAIN_UTF_8
        )
        val flavor = preferredFlavors.firstOrNull { candidate ->
            runCatching { service.isDocFlavorSupported(candidate) }.getOrDefault(false)
        } ?: DocFlavor.BYTE_ARRAY.AUTOSENSE
        val attributes = HashPrintRequestAttributeSet().apply {
            add(JobName("AITA ESC/POS receipt", Locale.getDefault()))
        }
        service.createPrintJob().print(SimpleDoc(printerBytes, flavor, null), attributes)
    }

    private fun windowsRawPrinterPowerShellScript(): String {
        val d = '$'
        return """
param(
    [Parameter(Mandatory=${d}true)][string]${d}PrinterName,
    [Parameter(Mandatory=${d}true)][string]${d}DataPath
)
${d}ErrorActionPreference = "Stop"
function Fail-AitaPrinterNotReady([string]${d}Message) {
    throw "${WINDOWS_PRINTER_NOT_READY_MARKER} ${d}Message"
}
function Read-AitaText(${d}Value) {
    if (${d}null -eq ${d}Value) { return "" }
    return ${d}Value.ToString()
}
function Get-AitaPrintManagementPrinter([string]${d}Name) {
    try {
        return Get-Printer -ErrorAction SilentlyContinue | Where-Object { ${d}_.Name -eq ${d}Name } | Select-Object -First 1
    } catch {
        return ${d}null
    }
}
function Get-AitaPrinterStatusObject([string]${d}Name) {
    try {
        ${d}candidate = Get-CimInstance -ClassName Win32_Printer -ErrorAction SilentlyContinue | Where-Object { ${d}_.Name -eq ${d}Name } | Select-Object -First 1
        if (${d}null -ne ${d}candidate) { return ${d}candidate }
    } catch { }
    try {
        return Get-WmiObject -Class Win32_Printer -ErrorAction SilentlyContinue | Where-Object { ${d}_.Name -eq ${d}Name } | Select-Object -First 1
    } catch {
        return ${d}null
    }
}
${d}printManagementPrinter = Get-AitaPrintManagementPrinter ${d}PrinterName
if (${d}null -ne ${d}printManagementPrinter) {
    ${d}printerStatusText = (Read-AitaText ${d}printManagementPrinter.PrinterStatus).ToLowerInvariant()
    if (${d}printerStatusText -match "paused|stopped") {
        Fail-AitaPrinterNotReady "Windows shows printer '${d}PrinterName' as paused or stopped. Open the print queue and choose Resume printing."
    }
    if (${d}printerStatusText -match "offline|not\s*available|server\s*unknown") {
        Fail-AitaPrinterNotReady "Windows shows printer '${d}PrinterName' as offline or unavailable. Check USB and power, then refresh printers in AITA."
    }
    if (${d}printerStatusText -match "paper|jam|dooropen|cover|outputbin") {
        Fail-AitaPrinterNotReady "Windows reports paper, jam, output-bin, or cover problem on printer '${d}PrinterName'. Check the 58mm paper roll and close the cover."
    }
    if (${d}printerStatusText -match "error|userintervention|outofmemory|notavailable") {
        Fail-AitaPrinterNotReady "Windows reports printer '${d}PrinterName' needs attention. Check the queue, cable, paper and cover."
    }
}
${d}printerStatusObject = Get-AitaPrinterStatusObject ${d}PrinterName
if (${d}null -ne ${d}printerStatusObject) {
    [int]${d}printerState = 0
    [int]${d}printerStatus = 0
    [int]${d}detectedErrorState = 0
    if (${d}null -ne ${d}printerStatusObject.PrinterState) { ${d}printerState = [int]${d}printerStatusObject.PrinterState }
    if (${d}null -ne ${d}printerStatusObject.PrinterStatus) { ${d}printerStatus = [int]${d}printerStatusObject.PrinterStatus }
    if (${d}null -ne ${d}printerStatusObject.DetectedErrorState) { ${d}detectedErrorState = [int]${d}printerStatusObject.DetectedErrorState }
    ${d}stateLooksLikeLegacyFlags = ${d}printerState -gt 25
    if ([bool]${d}printerStatusObject.WorkOffline -or (${d}printerState -eq 8) -or (${d}stateLooksLikeLegacyFlags -and ((${d}printerState -band 128) -ne 0)) -or ${d}printerStatus -eq 7 -or ${d}detectedErrorState -eq 9) {
        Fail-AitaPrinterNotReady "Windows shows printer '${d}PrinterName' as offline. Check USB and power, then refresh printers in AITA."
    }
    if ((${d}printerState -eq 1) -or (${d}printerStatus -eq 6) -or (${d}stateLooksLikeLegacyFlags -and ((${d}printerState -band 1) -ne 0))) {
        Fail-AitaPrinterNotReady "Windows shows printer '${d}PrinterName' as paused or stopped. Open the print queue and choose Resume printing."
    }
    if ((${d}printerState -in 4,5,6,7,12,23) -or (${d}stateLooksLikeLegacyFlags -and (((${d}printerState -band 8) -ne 0) -or ((${d}printerState -band 16) -ne 0) -or ((${d}printerState -band 64) -ne 0) -or ((${d}printerState -band 4194304) -ne 0))) -or (${d}detectedErrorState -in 3,4,7,8,11)) {
        Fail-AitaPrinterNotReady "Windows reports paper, jam, output-bin, or cover problem on printer '${d}PrinterName'. Check the 58mm paper roll and close the cover."
    }
    if ((${d}printerState -in 2,13,21,22) -or (${d}stateLooksLikeLegacyFlags -and (((${d}printerState -band 2) -ne 0) -or ((${d}printerState -band 1048576) -ne 0))) -or (${d}detectedErrorState -in 6,10)) {
        Fail-AitaPrinterNotReady "Windows reports printer '${d}PrinterName' needs attention. Check the queue, cable, paper and cover."
    }
}
${d}source = @"
using System;
using System.Runtime.InteropServices;

public class AitaRawPrinter {
    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    public class DOC_INFO_1 {
        [MarshalAs(UnmanagedType.LPWStr)] public string pDocName;
        [MarshalAs(UnmanagedType.LPWStr)] public string pOutputFile;
        [MarshalAs(UnmanagedType.LPWStr)] public string pDataType;
    }

    [DllImport("winspool.Drv", EntryPoint="OpenPrinterW", SetLastError=true, CharSet=CharSet.Unicode)]
    public static extern bool OpenPrinter(string szPrinter, out IntPtr hPrinter, IntPtr pd);

    [DllImport("winspool.Drv", EntryPoint="ClosePrinter", SetLastError=true)]
    public static extern bool ClosePrinter(IntPtr hPrinter);

    [DllImport("winspool.Drv", EntryPoint="StartDocPrinterW", SetLastError=true, CharSet=CharSet.Unicode)]
    public static extern int StartDocPrinter(IntPtr hPrinter, int level, [In] DOC_INFO_1 di);

    [DllImport("winspool.Drv", EntryPoint="EndDocPrinter", SetLastError=true)]
    public static extern bool EndDocPrinter(IntPtr hPrinter);

    [DllImport("winspool.Drv", EntryPoint="StartPagePrinter", SetLastError=true)]
    public static extern bool StartPagePrinter(IntPtr hPrinter);

    [DllImport("winspool.Drv", EntryPoint="EndPagePrinter", SetLastError=true)]
    public static extern bool EndPagePrinter(IntPtr hPrinter);

    [DllImport("winspool.Drv", EntryPoint="WritePrinter", SetLastError=true)]
    public static extern bool WritePrinter(IntPtr hPrinter, IntPtr pBytes, int dwCount, out int dwWritten);
}
"@
Add-Type -TypeDefinition ${d}source
${d}bytes = [System.IO.File]::ReadAllBytes(${d}DataPath)
if (${d}bytes.Length -le 0) { throw "No bytes to print" }
${d}hPrinter = [IntPtr]::Zero
${d}buffer = [IntPtr]::Zero
${d}docStarted = ${d}false
${d}pageStarted = ${d}false
try {
    if (-not [AitaRawPrinter]::OpenPrinter(${d}PrinterName, [ref]${d}hPrinter, [IntPtr]::Zero)) {
        ${d}lastError = [Runtime.InteropServices.Marshal]::GetLastWin32Error()
        throw "OpenPrinter failed for '${d}PrinterName' (Win32=${d}lastError)"
    }
    ${d}doc = New-Object AitaRawPrinter+DOC_INFO_1
    ${d}doc.pDocName = "AITA ESC/POS receipt"
    ${d}doc.pOutputFile = ${d}null
    ${d}doc.pDataType = "RAW"
    if ([AitaRawPrinter]::StartDocPrinter(${d}hPrinter, 1, ${d}doc) -eq 0) {
        ${d}lastError = [Runtime.InteropServices.Marshal]::GetLastWin32Error()
        throw "StartDocPrinter failed (Win32=${d}lastError)"
    }
    ${d}docStarted = ${d}true
    if (-not [AitaRawPrinter]::StartPagePrinter(${d}hPrinter)) {
        ${d}lastError = [Runtime.InteropServices.Marshal]::GetLastWin32Error()
        throw "StartPagePrinter failed (Win32=${d}lastError)"
    }
    ${d}pageStarted = ${d}true
    ${d}buffer = [Runtime.InteropServices.Marshal]::AllocHGlobal(${d}bytes.Length)
    [Runtime.InteropServices.Marshal]::Copy(${d}bytes, 0, ${d}buffer, ${d}bytes.Length)
    [int]${d}written = 0
    if (-not [AitaRawPrinter]::WritePrinter(${d}hPrinter, ${d}buffer, ${d}bytes.Length, [ref]${d}written)) {
        ${d}lastError = [Runtime.InteropServices.Marshal]::GetLastWin32Error()
        throw "WritePrinter failed (Win32=${d}lastError)"
    }
    if (${d}written -ne ${d}bytes.Length) {
        throw "WritePrinter wrote ${d}written of ${d}(${d}bytes.Length) bytes"
    }
    Write-Output "OK ${d}written bytes"
}
finally {
    if (${d}buffer -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::FreeHGlobal(${d}buffer) }
    if (${d}pageStarted) { [void][AitaRawPrinter]::EndPagePrinter(${d}hPrinter) }
    if (${d}docStarted) { [void][AitaRawPrinter]::EndDocPrinter(${d}hPrinter) }
    if (${d}hPrinter -ne [IntPtr]::Zero) { [void][AitaRawPrinter]::ClosePrinter(${d}hPrinter) }
}
""".trimIndent()
    }

    private fun printWithWindowsRawSpooler(serviceName: String, printerBytes: ByteArray) {
        val spoolDir = File(cacheDirPath.ifBlank { System.getProperty("java.io.tmpdir") }, "receipt_printer_spool").apply { mkdirs() }
        val dataFile = File.createTempFile("aita_receipt_", ".bin", spoolDir)
        val scriptFile = File.createTempFile("aita_raw_print_", ".ps1", spoolDir)
        val outputFile = File.createTempFile("aita_raw_print_", ".log", spoolDir)
        try {
            dataFile.writeBytes(printerBytes)
            scriptFile.writeText(windowsRawPrinterPowerShellScript(), Charsets.UTF_8)
            val process = ProcessBuilder(
                "powershell.exe",
                "-NoProfile",
                "-NonInteractive",
                "-ExecutionPolicy",
                "Bypass",
                "-File",
                scriptFile.absolutePath,
                "-PrinterName",
                serviceName,
                "-DataPath",
                dataFile.absolutePath
            )
                .redirectErrorStream(true)
                .redirectOutput(outputFile)
                .start()
            val completed = process.waitFor(25, TimeUnit.SECONDS)
            val output = runCatching { outputFile.readText(Charsets.UTF_8).trim() }.getOrDefault("")
            if (!completed) {
                process.destroyForcibly()
                val killed = process.waitFor(2, TimeUnit.SECONDS)
                val finalOutput = if (killed) runCatching { outputFile.readText(Charsets.UTF_8).trim() }.getOrDefault(output) else output
                throwWindowsRawPrintFailure(
                    output = finalOutput,
                    fallbackMessage = buildString {
                        append("Windows RAW print timed out")
                        if (finalOutput.isNotBlank()) append(": ").append(finalOutput.take(1_000))
                    }
                )
            }
            if (process.exitValue() != 0) {
                throwWindowsRawPrintFailure(
                    output = output,
                    fallbackMessage = "Windows RAW print failed with exit code ${process.exitValue()}"
                )
            }
            if (output.isNotBlank()) println("AITA receipt printer Windows RAW: ${output.take(1_000)}")
        } finally {
            runCatching { dataFile.delete() }
            runCatching { scriptFile.delete() }
            runCatching { outputFile.delete() }
        }
    }

    private fun writeToPrintService(serviceName: String, printerBytes: ByteArray): Boolean {
        val service = systemPrintServices()
            .firstOrNull { it.name.equals(serviceName, ignoreCase = true) }
            ?: error("Printer '$serviceName' is not installed")

        val healthNotes = printServiceHealthNotes(service)
        if (!printServiceCanAcceptImmediateJobs(healthNotes)) {
            error("Printer '${service.name}' is not ready: ${healthNotes.joinToString(", ")}. Check the system printer queue, resume it if paused, then refresh printers in AITA.")
        }

        if (isWindows()) {
            val rawResult = runCatching {
                printWithWindowsRawSpooler(service.name, printerBytes)
                true
            }.onFailure { throwable ->
                if (throwable.isWindowsPrinterNotReadyFailure()) throw throwable
                System.err.println("AITA receipt printer Windows RAW spooler failed, trying Java PrintService: ${throwable.message}")
            }
            if (rawResult.getOrDefault(false)) return true
        }

        printWithJavaPrintService(service, printerBytes)
        return true
    }

    private fun writeToSerialPort(portName: String, printerBytes: ByteArray): Boolean {
        val cleanPortName = cleanSerialPortName(portName)
        val port = SerialPort.getCommPorts()
            .orEmpty()
            .firstOrNull { candidate -> candidate.systemPortName.equals(cleanPortName, ignoreCase = true) }
            ?: SerialPort.getCommPort(cleanPortName)
        port.setComPortParameters(serialBaudRate(), 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY)
        port.setComPortTimeouts(SerialPort.TIMEOUT_WRITE_BLOCKING, 2_000, 5_000)
        if (!port.openPort(5_000)) error("Could not open serial printer port $cleanPortName")
        return try {
            port.outputStream.use { output ->
                output.write(printerBytes)
                output.flush()
            }
            true
        } finally {
            runCatching { port.closePort() }
        }
    }

    private fun writeToTcpPrinter(target: Pair<String, Int>, printerBytes: ByteArray): Boolean {
        Socket().use { socket ->
            socket.tcpNoDelay = true
            socket.connect(InetSocketAddress(target.first, target.second), 5_000)
            socket.getOutputStream().use { output ->
                output.write(printerBytes)
                output.flush()
            }
        }
        return true
    }

    private fun writeToDeviceFile(target: String, printerBytes: ByteArray): Boolean {
        val file = File(normalizedDevicePath(target))
        file.outputStream().use { output ->
            output.write(printerBytes)
            output.flush()
        }
        return true
    }

    suspend fun writeEscPosBytesToConfiguredPrinter(printerBytes: ByteArray): Boolean {
        writeEscPosBytes?.let { customWriter ->
            return customWriter(printerBytes)
        }

        val target = configuredTarget().takeIf { it.isNotBlank() } ?: return false

        return withContext(Dispatchers.IO) {
            val printServiceName = printServiceNameFromTarget(target)
            val serialPortName = serialPortNameFromTarget(target)
            val tcpTarget = tcpTargetFrom(target)
            when {
                printServiceName != null -> writeToPrintService(printServiceName, printerBytes)
                serialPortName != null -> writeToSerialPort(serialPortName, printerBytes)
                tcpTarget != null -> writeToTcpPrinter(tcpTarget, printerBytes)
                else -> writeToDeviceFile(target, printerBytes)
            }
        }
    }
}


actual object LocalAitaLanTransport {
    private val running = java.util.concurrent.atomic.AtomicBoolean(false)
    private var tcpServer: java.net.ServerSocket? = null
    private var udpSocket: java.net.DatagramSocket? = null
    private var tcpThread: Thread? = null
    private var udpThread: Thread? = null

    actual fun start(
        deviceId: String,
        tcpPort: Int,
        discoveryPort: Int,
        onMessage: suspend (message: String, senderHost: String) -> String
    ): Boolean {
        if (running.get()) return true
        running.set(true)

        return runCatching {
            tcpThread = kotlin.concurrent.thread(name = "AITA-LAN-TCP-$deviceId", isDaemon = true) {
                runCatching {
                    val server = java.net.ServerSocket().apply {
                        reuseAddress = true
                        bind(java.net.InetSocketAddress(tcpPort))
                    }
                    tcpServer = server
                    while (running.get()) {
                        val socket = runCatching { server.accept() }.getOrNull() ?: continue
                        kotlin.concurrent.thread(name = "AITA-LAN-TCP-CLIENT", isDaemon = true) {
                            socket.use { client ->
                                runCatching {
                                    client.soTimeout = 5000
                                    val message = client.getInputStream().readBytes().toString(Charsets.UTF_8)
                                    val response = kotlinx.coroutines.runBlocking { onMessage(message, client.inetAddress?.hostAddress.orEmpty()) }
                                    if (response.isNotBlank()) {
                                        client.getOutputStream().write(response.toByteArray(Charsets.UTF_8))
                                        client.getOutputStream().flush()
                                    }
                                }
                            }
                        }
                    }
                }.onFailure { running.set(false) }
            }

            udpThread = kotlin.concurrent.thread(name = "AITA-LAN-UDP-$deviceId", isDaemon = true) {
                runCatching {
                    val socket = java.net.DatagramSocket(null).apply {
                        reuseAddress = true
                        broadcast = true
                        bind(java.net.InetSocketAddress(discoveryPort))
                    }
                    udpSocket = socket
                    val buffer = ByteArray(65507)
                    while (running.get()) {
                        val packet = java.net.DatagramPacket(buffer, buffer.size)
                        runCatching { socket.receive(packet) }.getOrNull() ?: continue
                        val message = packet.data.copyOfRange(packet.offset, packet.offset + packet.length).toString(Charsets.UTF_8)
                        val response = kotlinx.coroutines.runBlocking { onMessage(message, packet.address?.hostAddress.orEmpty()) }
                        if (response.isNotBlank()) {
                            val bytes = response.toByteArray(Charsets.UTF_8)
                            socket.send(java.net.DatagramPacket(bytes, bytes.size, packet.address, packet.port))
                        }
                    }
                }
            }
            true
        }.getOrElse {
            running.set(false)
            stop()
            false
        }
    }

    actual fun stop() {
        running.set(false)
        runCatching { tcpServer?.close() }
        runCatching { udpSocket?.close() }
        tcpServer = null
        udpSocket = null
        tcpThread = null
        udpThread = null
    }

    actual fun broadcast(message: String, discoveryPort: Int) {
        runCatching {
            java.net.DatagramSocket().use { socket ->
                socket.broadcast = true
                val bytes = message.toByteArray(Charsets.UTF_8)
                val packet = java.net.DatagramPacket(
                    bytes,
                    bytes.size,
                    java.net.InetAddress.getByName("255.255.255.255"),
                    discoveryPort
                )
                socket.send(packet)
            }
        }
    }

    actual suspend fun send(host: String, port: Int, message: String, timeoutMillis: Int): String? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                java.net.Socket().use { socket ->
                    socket.soTimeout = timeoutMillis
                    socket.connect(java.net.InetSocketAddress(host, port), timeoutMillis)
                    socket.getOutputStream().write(message.toByteArray(Charsets.UTF_8))
                    socket.getOutputStream().flush()
                    runCatching { socket.shutdownOutput() }
                    socket.getInputStream().readBytes().toString(Charsets.UTF_8).ifBlank { null }
                }
            }.getOrNull()
        }

    actual fun localHostAddress(): String {
        return runCatching {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is java.net.Inet4Address) {
                        return@runCatching address.hostAddress
                    }
                }
            }
            java.net.InetAddress.getLocalHost().hostAddress ?: "127.0.0.1"
        }.getOrDefault("127.0.0.1")
    }
}

