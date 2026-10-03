package kz.aita

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.font.FontRenderContext
import java.awt.font.TextLayout
import java.awt.geom.Rectangle2D
import java.awt.print.PageFormat
import java.awt.print.Pageable
import java.awt.print.Paper
import java.awt.print.Printable
import java.awt.print.PrinterJob
import javax.swing.SwingUtilities
import kotlinx.coroutines.sync.Mutex
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.print.PrintService
import javax.print.PrintServiceLookup
import javax.print.attribute.HashPrintRequestAttributeSet
import javax.print.attribute.standard.DialogTypeSelection

/** A real driver-rendered route: USB, network and virtual queues are chosen by the OS. */
class SystemReceiptPages(document: AitaPdfDocument) : Pageable {
    private val context = FontRenderContext(null, true, true)
    private fun font(style: AitaPdfStyle) = Font(Font.SANS_SERIF, if (style.bold) Font.BOLD else Font.PLAIN, 1)
        .deriveFont(style.size)
    private val pages = layoutAitaPdfDocument(document,
        { text, style -> if (text.isEmpty()) 0f else TextLayout(text, font(style), context).advance },
        { style -> font(style).getLineMetrics("HgӘәҚқ₸", context).let { -it.ascent to it.descent } })
    private val margin = document.margin

    override fun getNumberOfPages() = pages.size
    override fun getPageFormat(pageIndex: Int): PageFormat {
        val page = pages[pageIndex]
        return PageFormat().apply {
            paper = Paper().apply {
                setSize(page.width.toDouble(), page.height.toDouble())
                setImageableArea(0.0, 0.0, page.width.toDouble(), page.height.toDouble())
            }
        }
    }
    override fun getPrintable(pageIndex: Int): Printable {
        pages[pageIndex] // Bounds checked before handing a callback to the spooler.
        return Printable { graphics, _, requested ->
            if (requested !in pages.indices) Printable.NO_SUCH_PAGE else {
                val page = pages[requested]
                val output = graphics.create() as Graphics2D
                try {
                    output.color = Color.BLACK
                    output.stroke = BasicStroke(.6f)
                    page.lines.forEach { line ->
                        val barcode = line.barcode
                        when {
                            barcode != null -> barcode.bars.forEach { bar ->
                                output.fill(Rectangle2D.Float(line.x + bar.x, line.baseline + bar.y, bar.width, bar.height))
                            }
                            line.divider -> output.draw(java.awt.geom.Line2D.Float(margin, line.baseline, page.width - margin, line.baseline))
                            line.text.isNotEmpty() -> TextLayout(line.text, font(line.style), context).draw(output, line.x, line.baseline)
                        }
                    }
                } finally { output.dispose() }
                Printable.PAGE_EXISTS
            }
        }
    }
}

private class RememberedDocumentPrinter(private val a4: Boolean) {
    private val mutex = Mutex()
    private var cached: PrintService? = null
    private val state get() = if (a4) systemA4PrinterNameState else systemReceiptPrinterNameState
    private fun preference() = File(jvmPersistentDataRoot(), if (a4) "system-a4-printer.txt" else "system-receipt-printer.txt")
    private fun readName(): String? {
        val file = preference()
        if (!file.isFile) return null
        require(file.length() <= 4096)
        return file.readText().trim().takeIf { it.isNotEmpty() }
    }
    fun restore() { state.value = runCatching { readName() }.getOrNull() }
    private fun service(): PrintService? {
        cached?.let { return it }
        val name = readName() ?: return null
        return PrintServiceLookup.lookupPrintServices(null, null).firstOrNull { it.name == name }
            ?.also { cached = it; state.value = name }
            ?: error("Saved printer is unavailable; select its current queue")
    }
    private fun remember(service: PrintService) {
        val file = preference()
        file.parentFile.mkdirs()
        val staged = File.createTempFile("system-printer-", ".tmp", file.parentFile)
        try {
            staged.writeText(service.name)
            try { Files.move(staged.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE) }
            catch (_: java.nio.file.AtomicMoveNotSupportedException) { Files.move(staged.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING) }
            cached = service; state.value = service.name
        } finally { staged.delete() }
    }
    fun select(name: String?): ReceiptPlatformActionResult = guarded {
        if (name == null) { Files.deleteIfExists(preference().toPath()); cached = null; state.value = null }
        else remember(PrintServiceLookup.lookupPrintServices(null, null).firstOrNull { it.name == name }
            ?: return@guarded ReceiptPlatformActionResult(false, deviceWorkflowText("driver_print_failed")))
        ReceiptPlatformActionResult(true, deviceWorkflowText("saved_setup"))
    }
    fun choose(): ReceiptPlatformActionResult = guarded {
        val job = PrinterJob.getPrinterJob()
        runCatching { service() }.getOrNull()?.let { job.printService = it }
        if (!showNativePrinterDialog(job)) ReceiptPlatformActionResult(false, deviceWorkflowText("print_cancelled"))
        else { remember(job.printService); ReceiptPlatformActionResult(true, deviceWorkflowText("saved_setup")) }
    }
    fun print(title: String, document: AitaPdfDocument): ReceiptPlatformActionResult = guarded {
        val pages = SystemReceiptPages(document)
        val job = PrinterJob.getPrinterJob()
        val saved = service()
        saved?.let { job.printService = it }
        job.jobName = title
        job.setPageable(pages)
        if (saved == null && !showNativePrinterDialog(job))
            return@guarded ReceiptPlatformActionResult(false, deviceWorkflowText("print_cancelled"))
        job.setPageable(pages)
        job.print()
        if (saved == null) runCatching { remember(job.printService) }
        ReceiptPlatformActionResult(true, deviceWorkflowText("print_queued"))
    }
    private inline fun guarded(block: () -> ReceiptPlatformActionResult): ReceiptPlatformActionResult {
        if (!mutex.tryLock()) return ReceiptPlatformActionResult(false, deviceWorkflowText("print_busy"))
        return try { block() }
        catch (error: Exception) {
            RuntimeDiagnostics.capture(error, "printer.document")
            ReceiptPlatformActionResult(false, deviceWorkflowText("driver_print_failed"))
        } finally { mutex.unlock() }
    }
}
private fun showNativePrinterDialog(job: PrinterJob): Boolean {
    var accepted = false
    val attributes = HashPrintRequestAttributeSet().apply { add(DialogTypeSelection.NATIVE) }
    val show = Runnable { accepted = job.printDialog(attributes) }
    if (SwingUtilities.isEventDispatchThread()) show.run() else SwingUtilities.invokeAndWait(show)
    return accepted
}
private val receiptDocumentPrinter = RememberedDocumentPrinter(false)
private val a4DocumentPrinter = RememberedDocumentPrinter(true)
fun restoreSystemReceiptPrinterName() { receiptDocumentPrinter.restore(); a4DocumentPrinter.restore() }
fun chooseSystemReceiptPrinter() = receiptDocumentPrinter.choose()
fun chooseSystemA4Printer() = a4DocumentPrinter.choose()
fun printSystemReceiptDocument(title: String, document: AitaPdfDocument) = receiptDocumentPrinter.print(title, document)
fun printSystemA4Document(title: String, document: AitaPdfDocument) = a4DocumentPrinter.print(title, document)
fun listSystemDocumentPrinters(): List<PlatformReceiptPrinterDataModel> =
    PrintServiceLookup.lookupPrintServices(null, null).map { PlatformReceiptPrinterDataModel(it.name, it.name) }.sortedBy { it.name }
fun selectSystemDocumentPrinter(a4: Boolean, name: String?): ReceiptPlatformActionResult =
    (if (a4) a4DocumentPrinter else receiptDocumentPrinter).select(name)
