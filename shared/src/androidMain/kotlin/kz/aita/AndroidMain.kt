package kz.aita

import android.content.ContentValues
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import app.cash.sqldelight.db.SqlDriver
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.OkHttpClient
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import java.io.FileOutputStream
import java.io.File
import java.util.UUID

actual fun getCurrentTimeMillis(): Long = System.currentTimeMillis()

actual var getStoredUserAuthTokens: (() -> TokenPair?)? = null
actual var setStoredUserAuthTokens: ((TokenPair?) -> Unit)? = null

actual var getStoredUserAccountDataModel: (() -> UserAccountDataModel?)? = null
actual var setStoredUserAccountDataModel: ((UserAccountDataModel?) -> Unit)? = null

actual var getPersistentUiDraftValue: (suspend (String) -> String?)? = null
actual var setPersistentUiDraftValue: (suspend (String, String?) -> Unit)? = null

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
    OkHttp.create { preconfigured = buildAitaOkHttpClient() }
}

actual var getSystemLocaleLanguage: () -> String = {
    "ru"
}

actual var getPlatformName: () -> String = {
    "android"
}

actual var getSqlDelightDriver: (() -> SqlDriver?)? = {
    null
}


private class ReceiptPdfPrintDocumentAdapter(
    private val fileName: String,
    private val pdfBytes: ByteArray
) : PrintDocumentAdapter() {
    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes?,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback?,
        extras: Bundle?
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback?.onLayoutCancelled()
            return
        }

        val info = PrintDocumentInfo.Builder(fileName)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
            .build()

        callback?.onLayoutFinished(info, true)
    }

    override fun onWrite(
        pages: Array<out PageRange>?,
        destination: ParcelFileDescriptor?,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback?
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback?.onWriteCancelled()
            return
        }

        runCatching {
            val descriptor = destination ?: error("Print destination is not available")
            FileOutputStream(descriptor.fileDescriptor).use { output ->
                output.write(pdfBytes)
                output.flush()
            }
            callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        }.getOrElse { throwable ->
            callback?.onWriteFailed(throwable.message ?: "Could not write receipt PDF")
        }
    }
}

object ReceiptPlatformAndroidBridge {
    /**
     * Optional direct ESC/POS writer. Use this when a real Bluetooth/USB manager owns the connection.
     */
    var writeEscPosBytes: (suspend (ByteArray) -> Boolean)? = null

    /**
     * Temporary built-in Bluetooth SPP path for common ESC/POS receipt printers.
     * Configure it later from the devices/settings screen with a paired printer MAC address.
     */
    var bluetoothPrinterMacAddress: String? = null

    private val bluetoothSerialPortProfileUuid: UUID =
        UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    fun configureBluetoothPrinter(macAddress: String?) {
        bluetoothPrinterMacAddress = macAddress
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    @SuppressLint("MissingPermission")
    private suspend fun writeEscPosBytesToConfiguredBluetoothPrinter(printerBytes: ByteArray): Boolean =
        withContext(Dispatchers.IO) {
            val address = bluetoothPrinterMacAddress?.trim()?.takeIf { it.isNotBlank() } ?: return@withContext false
            val adapter = BluetoothAdapter.getDefaultAdapter() ?: return@withContext false
            val device = runCatching { adapter.getRemoteDevice(address) }.getOrNull() ?: return@withContext false
            runCatching { adapter.cancelDiscovery() }
            val socket = device.createRfcommSocketToServiceRecord(bluetoothSerialPortProfileUuid)
            try {
                socket.connect()
                socket.outputStream.write(printerBytes)
                socket.outputStream.flush()
                true
            } finally {
                runCatching { socket.close() }
            }
        }

    suspend fun writeEscPosBytesToConfiguredPrinter(printerBytes: ByteArray): Boolean {
        writeEscPosBytes?.let { customWriter ->
            return customWriter(printerBytes)
        }
        return writeEscPosBytesToConfiguredBluetoothPrinter(printerBytes)
    }
}

fun installReceiptPlatformAndroid(context: Context) {
    val appContext = context.applicationContext

    fun createCachedPdfUri(fileName: String, pdfBytes: ByteArray): Uri {
        val safeFileName = fileName.ifBlank { "receipt.pdf" }
        val dir = File(appContext.cacheDir, "receipts").apply { mkdirs() }
        val file = File(dir, safeFileName).apply { writeBytes(pdfBytes) }

        return runCatching {
            FileProvider.getUriForFile(appContext, appContext.packageName + ".fileprovider", file)
        }.getOrElse {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) throw it

            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, safeFileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = appContext.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw it
            appContext.contentResolver.openOutputStream(uri)?.use { output -> output.write(pdfBytes) }
                ?: throw it
            val doneValues = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
            appContext.contentResolver.update(uri, doneValues, null, null)
            uri
        }
    }

    fun savePdfToDownloadsOrPrivateDocuments(fileName: String, pdfBytes: ByteArray): ReceiptPlatformActionResult {
        return runCatching {
            val safeFileName = fileName.ifBlank { "receipt.pdf" }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, safeFileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }

                val uri = appContext.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("Could not create PDF file")

                appContext.contentResolver.openOutputStream(uri)?.use { it.write(pdfBytes) }
                    ?: error("Could not open PDF output stream")

                val doneValues = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
                appContext.contentResolver.update(uri, doneValues, null, null)

                ReceiptPlatformActionResult(true, "Saved to Downloads")
            } else {
                val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val targetDir = if (publicDir.exists() || publicDir.mkdirs()) {
                    publicDir
                } else {
                    appContext.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: appContext.filesDir
                }
                val file = File(targetDir, safeFileName)
                file.writeBytes(pdfBytes)
                ReceiptPlatformActionResult(true, "Saved to ${file.absolutePath}")
            }
        }.getOrElse { throwable ->
            val fallbackDir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: appContext.filesDir
            runCatching {
                val file = File(fallbackDir, fileName.ifBlank { "receipt.pdf" })
                file.writeBytes(pdfBytes)
                ReceiptPlatformActionResult(true, "Saved to ${file.absolutePath}")
            }.getOrElse {
                ReceiptPlatformActionResult(false, throwable.message ?: it.message ?: "Could not save PDF")
            }
        }
    }

    saveReceiptPdfFile = { fileName, pdfBytes ->
        withContext(Dispatchers.IO) {
            savePdfToDownloadsOrPrivateDocuments(fileName, pdfBytes)
        }
    }

    shareReceiptPdfFile = { fileName, pdfBytes, whatsappOnly ->
        withContext(Dispatchers.Main) {
            runCatching {
                val uri = withContext(Dispatchers.IO) { createCachedPdfUri(fileName, pdfBytes) }
                val baseIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TEXT, "AITA receipt")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    if (whatsappOnly) setPackage("com.whatsapp")
                }

                try {
                    if (whatsappOnly) {
                        context.startActivity(baseIntent)
                    } else {
                        context.startActivity(Intent.createChooser(baseIntent, "Share receipt").apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                    }
                    ReceiptPlatformActionResult(true, if (whatsappOnly) "Opening WhatsApp" else "Opening share sheet")
                } catch (notFound: ActivityNotFoundException) {
                    if (!whatsappOnly) throw notFound
                    val chooserIntent = Intent.createChooser(
                        Intent(Intent.ACTION_SEND).apply {
                            type = "application/pdf"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            putExtra(Intent.EXTRA_TEXT, "AITA receipt")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        },
                        "Share receipt"
                    ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    context.startActivity(chooserIntent)
                    ReceiptPlatformActionResult(true, "WhatsApp is not installed; opening share sheet")
                }
            }.getOrElse { throwable ->
                ReceiptPlatformActionResult(false, throwable.message ?: "Could not share PDF")
            }
        }
    }

    printReceiptPlatformAction = { _, _, printerBytes ->
        withContext(Dispatchers.IO) {
            runCatching {
                if (ReceiptPlatformAndroidBridge.writeEscPosBytesToConfiguredPrinter(printerBytes)) {
                    ReceiptPlatformActionResult(true, "Receipt sent to printer")
                } else {
                    ReceiptPlatformActionResult(false, "Android Bluetooth ESC/POS receipt printer is not configured")
                }
            }.getOrElse { throwable ->
                ReceiptPlatformActionResult(false, throwable.message ?: "Could not print receipt")
            }
        }
    }

    printReceiptEscPosBytes = { printerBytes ->
        withContext(Dispatchers.IO) {
            runCatching {
                if (ReceiptPlatformAndroidBridge.writeEscPosBytesToConfiguredPrinter(printerBytes)) {
                    ReceiptPlatformActionResult(true, "Receipt sent to printer")
                } else {
                    ReceiptPlatformActionResult(false, "Android Bluetooth ESC/POS receipt printer is not configured")
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

