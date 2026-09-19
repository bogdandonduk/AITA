package kz.aita

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.WString
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary
import java.io.File
import java.util.Base64

internal class WindowsSpoolFailure(val operation: String, val code: Int) :
    IllegalStateException("$operation failed (Win32=$code)")

/** The process boundary bounds a stuck driver without abandoning a native thread in the app. */
internal interface WindowsRawSpoolApi {
    fun open(name: String)
    fun startDocument(): Int
    fun startPage()
    fun write(bytes: ByteArray, offset: Int, count: Int): Int
    fun endPage()
    fun endDocument()
    fun abort()
    fun close()
}

internal fun submitWindowsRawReceipt(api: WindowsRawSpoolApi, name: String, bytes: ByteArray, emit: (String) -> Unit) {
    require(bytes.isNotEmpty() && bytes.size <= 8 * 1024 * 1024)
    require(name.isNotBlank() && name.none { it.code < 32 })
    api.open(name)
    var documentStarted = false
    try {
        emit("AITA_PRINT_SUBMITTING:")
        val job = api.startDocument()
        require(job > 0) { "Invalid spool job ID" }
        documentStarted = true
        emit("AITA_PRINT_JOB_STARTED:job=$job")
        api.startPage()
        var offset = 0
        while (offset < bytes.size) {
            val count = minOf(16 * 1024, bytes.size - offset)
            val written = api.write(bytes, offset, count)
            check(written in 1..count) { "WritePrinter made invalid progress" }
            offset += written
        }
        api.endPage()
        api.endDocument()
        documentStarted = false
        emit("AITA_PRINT_OK:job=$job;bytes=$offset")
    } finally {
        // A failed partial job must not be completed as though it were a full receipt.
        // A physical printer may already have consumed bytes, so callers still never replay it.
        if (documentStarted) runCatching { api.abort() }
        runCatching { api.close() }
    }
}

@Structure.FieldOrder("pDocName", "pOutputFile", "pDataType")
class WindowsReceiptDocumentInfo : Structure() {
    @JvmField var pDocName: WString? = WString("AITA receipt")
    @JvmField var pOutputFile: WString? = null
    @JvmField var pDataType: WString? = WString("RAW")
}

internal interface WindowsReceiptSpoolLibrary : StdCallLibrary {
    fun OpenPrinterW(name: WString, handle: PointerByReference, defaults: Pointer?): Boolean
    fun StartDocPrinterW(handle: Pointer, level: Int, info: WindowsReceiptDocumentInfo): Int
    fun StartPagePrinter(handle: Pointer): Boolean
    fun WritePrinter(handle: Pointer, bytes: Pointer, count: Int, written: IntByReference): Boolean
    fun EndPagePrinter(handle: Pointer): Boolean
    fun EndDocPrinter(handle: Pointer): Boolean
    fun AbortPrinter(handle: Pointer): Boolean
    fun ClosePrinter(handle: Pointer): Boolean
}

private class NativeWindowsReceiptSpool : WindowsRawSpoolApi {
    private val lib = Native.load("winspool.drv", WindowsReceiptSpoolLibrary::class.java)
    private lateinit var handle: Pointer
    private fun checked(ok: Boolean, operation: String) {
        if (!ok) throw WindowsSpoolFailure(operation, Native.getLastError())
    }
    override fun open(name: String) {
        val out = PointerByReference()
        checked(lib.OpenPrinterW(WString(name), out, null), "OpenPrinter")
        handle = requireNotNull(out.value)
    }
    override fun startDocument(): Int {
        val id = lib.StartDocPrinterW(handle, 1, WindowsReceiptDocumentInfo())
        checked(id > 0, "StartDocPrinter")
        return id
    }
    override fun startPage() = checked(lib.StartPagePrinter(handle), "StartPagePrinter")
    override fun write(bytes: ByteArray, offset: Int, count: Int): Int = Memory(count.toLong()).use { buffer ->
        buffer.write(0, bytes, offset, count)
        val written = IntByReference()
        checked(lib.WritePrinter(handle, buffer, count, written), "WritePrinter")
        written.value
    }
    override fun endPage() = checked(lib.EndPagePrinter(handle), "EndPagePrinter")
    override fun endDocument() = checked(lib.EndDocPrinter(handle), "EndDocPrinter")
    override fun abort() { lib.AbortPrinter(handle) }
    override fun close() { lib.ClosePrinter(handle) }
}

internal fun encodeWindowsRawPrintArguments(printer: String, path: String): List<String> = listOf(printer, path).map {
    require('\u0000' !in it) { "Invalid printer handoff argument" }
    Base64.getEncoder().encodeToString(it.toByteArray(Charsets.UTF_8)).also { encoded ->
        require(encoded.length in 1..12000) { "Printer handoff argument is too long or empty" }
    }
}

internal fun decodeWindowsRawPrintArguments(args: Array<String>): Pair<String, String> {
    require(args.size == 2) { "Expected two encoded printer handoff arguments" }
    val values = args.map {
        require(it.length in 1..12000) { "Invalid encoded printer handoff argument" }
        String(Base64.getDecoder().decode(it), Charsets.UTF_8)
    }
    return values[0] to values[1]
}

internal fun executeWindowsRawPrintRequest(args: Array<String>, emit: (String) -> Unit) {
    require(System.getProperty("os.name").contains("Windows", true)) { "Windows printer helper requires Windows" }
    val (printer, path) = decodeWindowsRawPrintArguments(args)
    val data = File(path)
    require(data.isFile && data.length() in 1..8L * 1024 * 1024) { "Staged receipt file is unavailable or invalid" }
    submitWindowsRawReceipt(NativeWindowsReceiptSpool(), printer, data.readBytes(), emit)
}

/** Only the installed JVM and packaged code run. No PowerShell, generated script or management
 * module is needed per receipt. Arguments are passed as data, never interpreted by a shell. */
object WindowsRawPrintProcess {
    @JvmStatic fun main(args: Array<String>) {
        var submitting = false
        try {
            executeWindowsRawPrintRequest(args) { message ->
                if (message == "AITA_PRINT_SUBMITTING:") submitting = true
                println(message); System.out.flush()
            }
        } catch (failure: Throwable) {
            val message = if (failure is WindowsSpoolFailure) failure.message else
                failure.javaClass.simpleName + ": " + failure.message.orEmpty().replace('\n', ' ').replace('\r', ' ').take(300)
            println("AITA_PRINT_ERROR:$message")
            if (!submitting && failure !is WindowsSpoolFailure) println("AITA_PRINT_SAFE_FAILURE:")
            if (failure !is WindowsSpoolFailure) failure.printStackTrace(System.err)
            System.out.flush()
            kotlin.system.exitProcess(1)
        }
    }
}

internal fun javaLauncherArgument(value: String): String {
    require(value.none { it == '\n' || it == '\r' || it == '\u0000' })
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
