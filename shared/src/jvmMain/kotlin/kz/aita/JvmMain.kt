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

@Suppress("unused")
private val jvmPersistentUiDraftHooksInstalled = run {
    getPersistentUiDraftValue = { key ->
        runCatching {
            val file = persistentUiDraftFileForKey(key)
            if (file.exists()) file.readText() else null
        }.getOrNull()
    }

    setPersistentUiDraftValue = { key, value ->
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
    true
}

actual var cacheDirPath: String = ""
actual val Dispatchers.ourIo: CoroutineDispatcher
    get() = Dispatchers.IO

actual var getHttpClientEngine: () -> HttpClientEngine = {
    OkHttp.create {
        preconfigured = OkHttpClient.Builder()
            .cache(
                Cache(
                    File(cacheDirPath, "http"),
                    cacheSize
                )
            ).build()
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

