package kz.aita

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.ktor.client.engine.*
import io.ktor.client.engine.okhttp.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.awt.Desktop
import java.io.File
import java.net.URLEncoder
import java.net.URI
import java.nio.file.Files
import java.util.*
import javax.print.DocFlavor
import javax.print.PrintServiceLookup
import javax.print.SimpleDoc
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

    private const val PRINT_SERVICE_PREFIX = "print-service:"
    private const val RECEIPT_PRINTER_DEVICE_FILE_NAME = "aita_receipt_printer_device.txt"

    private fun receiptPrinterDevicePreferenceFile(): File {
        return File(cacheDirPath.ifBlank { System.getProperty("java.io.tmpdir") }, RECEIPT_PRINTER_DEVICE_FILE_NAME)
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
        }
    }

    private fun likelyReceiptPrinterName(name: String): Boolean {
        val clean = name.lowercase(Locale.ROOT)
        return listOf("pos", "esc", "receipt", "thermal", "xprinter", "gprinter", "rongta", "sunmi", "mtp", "rp", "xp-", "чек", "касс")
            .any { clean.contains(it) }
    }

    fun knownEscPosDeviceCandidates(): List<String> {
        val configured = escPosDevicePath?.trim()?.takeIf { it.isNotBlank() && !it.startsWith(PRINT_SERVICE_PREFIX) }
        val osName = System.getProperty("os.name").orEmpty().lowercase(Locale.ROOT)
        val candidates = mutableListOf<String>()
        configured?.let { candidates += it }
        if (!osName.contains("win")) {
            candidates += listOf("/dev/usb/lp0", "/dev/usb/lp1", "/dev/usb/lp2", "/dev/ttyUSB0", "/dev/ttyUSB1", "/dev/ttyUSB2", "/dev/ttyACM0", "/dev/ttyACM1")
            val dev = File("/dev")
            if (dev.exists() && dev.isDirectory) {
                dev.listFiles()
                    .orEmpty()
                    .filter { file ->
                        val name = file.name.lowercase(Locale.ROOT)
                        name.startsWith("cu.usb") || name.startsWith("cu.slab") || name.startsWith("tty.usb") || name.startsWith("tty.slab")
                    }
                    .forEach { candidates += it.absolutePath }
            }
        }
        return candidates.distinct()
    }

    private fun listSystemPrintServiceCandidates(configured: String): List<PlatformReceiptPrinterDataModel> {
        return runCatching {
            PrintServiceLookup.lookupPrintServices(null, null)
                .orEmpty()
                .mapNotNull { service ->
                    val serviceName = service.name?.trim().orEmpty()
                    if (serviceName.isBlank()) return@mapNotNull null
                    val id = PRINT_SERVICE_PREFIX + serviceName
                    val probableReceiptPrinter = likelyReceiptPrinterName(serviceName)
                    val isConfigured = id == configured
                    if (!probableReceiptPrinter && !isConfigured) return@mapNotNull null
                    PlatformReceiptPrinterDataModel(
                        id = id,
                        name = serviceName,
                        subtitle = if (isConfigured) "Configured system ESC/POS print service" else "Likely thermal receipt printer from system printers",
                        configured = isConfigured,
                        available = true
                    )
                }
        }.getOrElse { emptyList() }
    }

    fun listConfiguredAndDetectedPrinters(): List<PlatformReceiptPrinterDataModel> {
        val configured = escPosDevicePath?.trim().orEmpty()
        val devicePathPrinters = knownEscPosDeviceCandidates()
            .mapNotNull { rawPath ->
                val clean = rawPath.trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val exists = runCatching { File(normalizedDevicePath(clean)).exists() }.getOrDefault(false)
                val isConfigured = clean == configured
                if (!isConfigured && !exists) return@mapNotNull null
                PlatformReceiptPrinterDataModel(
                    id = clean,
                    name = clean.substringAfterLast('/').ifBlank { clean },
                    subtitle = if (isConfigured) "Configured ESC/POS device path" else "Detected local ESC/POS device path",
                    configured = isConfigured,
                    available = exists || isConfigured
                )
            }
        return (devicePathPrinters + listSystemPrintServiceCandidates(configured))
            .distinctBy { it.id }
            .sortedWith(compareByDescending<PlatformReceiptPrinterDataModel> { it.configured }.thenBy { it.name.lowercase(Locale.ROOT) })
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

        val target = escPosDevicePath?.trim()?.takeIf { it.isNotBlank() } ?: return false

        return withContext(Dispatchers.IO) {
            if (target.startsWith(PRINT_SERVICE_PREFIX)) {
                val serviceName = target.removePrefix(PRINT_SERVICE_PREFIX)
                val service = PrintServiceLookup.lookupPrintServices(null, null)
                    .orEmpty()
                    .firstOrNull { it.name == serviceName }
                    ?: return@withContext false
                val job = service.createPrintJob()
                job.print(SimpleDoc(printerBytes, DocFlavor.BYTE_ARRAY.AUTOSENSE, null), null)
                true
            } else {
                val file = File(normalizedDevicePath(target))
                file.outputStream().use { output ->
                    output.write(printerBytes)
                    output.flush()
                }
                true
            }
        }
    }
}

fun installReceiptPlatformJvm() {
    ReceiptPlatformJvmBridge.loadPersistedEscPosDevicePath()
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

    fun writeHtmlToTemp(fileName: String, html: String): File {
        val safeName = fileName.ifBlank { "aita-document.html" }.let { name ->
            if (name.endsWith(".html", ignoreCase = true) || name.endsWith(".htm", ignoreCase = true)) name else "$name.html"
        }
        val file = File(System.getProperty("java.io.tmpdir"), safeName)
        file.writeText(html, Charsets.UTF_8)
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
                val shareLabel = if (fileName.contains("report", true) || fileName.contains("analytics", true)) "AITA analytics report" else "AITA receipt"
                if (whatsappOnly) {
                    val text = URLEncoder.encode("$shareLabel: ${file.absolutePath}", "UTF-8")
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



    printPdfDocumentPlatformAction = { fileName, pdfBytes ->
        withContext(Dispatchers.IO) {
            runCatching {
                val file = writePdfToTemp(fileName.ifBlank { "aita-document.pdf" }, pdfBytes)
                val desktop = desktop()
                when {
                    desktop != null && desktop.isSupported(Desktop.Action.PRINT) -> {
                        desktop.print(file)
                        ReceiptPlatformActionResult(true, "Opening system print dialog")
                    }
                    desktop != null && desktop.isSupported(Desktop.Action.OPEN) -> {
                        desktop.open(file)
                        ReceiptPlatformActionResult(true, "Opened PDF; print from the viewer")
                    }
                    else -> ReceiptPlatformActionResult(true, "PDF created at ${file.absolutePath}")
                }
            }.getOrElse { throwable ->
                ReceiptPlatformActionResult(false, throwable.message ?: "Could not print PDF document")
            }
        }
    }

    printHtmlDocumentPlatformAction = { fileName, html ->
        withContext(Dispatchers.IO) {
            runCatching {
                val file = writeHtmlToTemp(fileName.ifBlank { "aita-document.html" }, html)
                val desktop = desktop()
                when {
                    desktop != null && desktop.isSupported(Desktop.Action.BROWSE) -> {
                        desktop.browse(file.toURI())
                        ReceiptPlatformActionResult(true, "Opened label for printing")
                    }
                    desktop != null && desktop.isSupported(Desktop.Action.OPEN) -> {
                        desktop.open(file)
                        ReceiptPlatformActionResult(true, "Opened label; print from the viewer")
                    }
                    else -> ReceiptPlatformActionResult(true, "HTML label created at ${file.absolutePath}")
                }
            }.getOrElse { throwable ->
                ReceiptPlatformActionResult(false, throwable.message ?: "Could not print HTML document")
            }
        }
    }

    listPlatformReceiptPrinterDevicesAction = {
        withContext(Dispatchers.IO) {
            ReceiptPlatformJvmBridge.listConfiguredAndDetectedPrinters()
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

