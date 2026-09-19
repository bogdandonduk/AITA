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

private val systemReceiptPrintMutex = Mutex()
private var chosenSystemReceiptService: PrintService? = null
private fun systemReceiptPreference() = File(jvmPersistentDataRoot(), "system-receipt-printer.txt")
fun restoreSystemReceiptPrinterName() {
    systemReceiptPrinterNameState.value = runCatching { savedSystemReceiptPrinterName() }.getOrNull()
}
private fun savedSystemReceiptPrinterName(): String? {
    val file = systemReceiptPreference()
    if (!file.isFile) return null
    require(file.length() <= 4096)
    return file.readText().trim().takeIf { it.isNotEmpty() }
}
private fun rememberedSystemReceiptService(): PrintService? {
    chosenSystemReceiptService?.let { return it }
    val name = savedSystemReceiptPrinterName() ?: return null
    return PrintServiceLookup.lookupPrintServices(null, null).firstOrNull { it.name == name }
        ?.also { chosenSystemReceiptService = it; systemReceiptPrinterNameState.value = name }
        ?: error("Saved system printer is unavailable; select its current queue")
}
private fun rememberSystemReceiptService(service: PrintService) {
    val file = systemReceiptPreference()
    file.parentFile.mkdirs()
    val staged = File.createTempFile("system-receipt-", ".tmp", file.parentFile)
    try {
        staged.writeText(service.name)
        try { Files.move(staged.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE) }
        catch (_: java.nio.file.AtomicMoveNotSupportedException) { Files.move(staged.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING) }
        chosenSystemReceiptService = service
        systemReceiptPrinterNameState.value = service.name
    } finally { staged.delete() }
}
private fun showNativePrinterDialog(job: PrinterJob): Boolean {
    var accepted = false
    val attributes = HashPrintRequestAttributeSet().apply { add(DialogTypeSelection.NATIVE) }
    val show = Runnable { accepted = job.printDialog(attributes) }
    if (SwingUtilities.isEventDispatchThread()) show.run() else SwingUtilities.invokeAndWait(show)
    return accepted
}

/** Selection alone never sends a test page. The OS dialog owns driver properties. */
fun chooseSystemReceiptPrinter(): ReceiptPlatformActionResult {
    if (!systemReceiptPrintMutex.tryLock()) return ReceiptPlatformActionResult(false, deviceWorkflowText("print_busy"))
    return try {
        val job = PrinterJob.getPrinterJob()
        runCatching { rememberedSystemReceiptService() }.getOrNull()?.let { job.printService = it }
        if (!showNativePrinterDialog(job)) ReceiptPlatformActionResult(false, deviceWorkflowText("print_cancelled"))
        else {
            rememberSystemReceiptService(job.printService)
            ReceiptPlatformActionResult(true, deviceWorkflowText("saved_setup"))
        }
    } catch (_: Exception) { ReceiptPlatformActionResult(false, deviceWorkflowText("driver_print_failed")) }
    finally { systemReceiptPrintMutex.unlock() }
}

/** Caller uses a worker dispatcher. Only initial/reselection dialogs run on AWT; subsequent
 * receipts use the explicitly saved queue. Success means queued, never verified on paper. */
fun printSystemReceiptDocument(title: String, document: AitaPdfDocument): ReceiptPlatformActionResult {
    if (!systemReceiptPrintMutex.tryLock()) return ReceiptPlatformActionResult(false, deviceWorkflowText("print_busy"))
    try {
        val pages = SystemReceiptPages(document)
        val job = PrinterJob.getPrinterJob()
        val saved = rememberedSystemReceiptService()
        saved?.let { job.printService = it }
        job.jobName = title
        job.setPageable(pages)
        if (saved == null && !showNativePrinterDialog(job))
            return ReceiptPlatformActionResult(false, deviceWorkflowText("print_cancelled"))
        job.setPageable(pages)
        // Never fall back to RAW or another queue after an uncertain driver submission.
        job.print()
        if (saved == null) runCatching { rememberSystemReceiptService(job.printService) }
        return ReceiptPlatformActionResult(true, deviceWorkflowText("print_queued"))
    } catch (error: Exception) {
        System.err.println("AITA system receipt printing failed: ${error.javaClass.simpleName}: ${error.message}")
        return ReceiptPlatformActionResult(false, deviceWorkflowText("driver_print_failed"))
    } finally { systemReceiptPrintMutex.unlock() }
}
