package kz.aita

import java.awt.*
import java.awt.font.TextLayout
import java.awt.geom.Rectangle2D
import java.awt.print.*
import javax.print.PrintServiceLookup
import javax.print.attribute.HashPrintRequestAttributeSet
import javax.print.attribute.standard.DialogTypeSelection
import javax.swing.SwingUtilities
import kotlinx.coroutines.sync.Mutex

/** Driver-rendered labels have their own physical page size; never use the receipt/A4 queue. */
class SystemLabelPages(private val label: StockItemLabelDataModel) : Pageable {
    private val width = label.labelWidthMm * 72.0 / 25.4
    private val height = label.labelHeightMm * 72.0 / 25.4
    private val barcode = stockItemLabelBarcodePreviewData(label.barcode)
    init {
        require(label.copies in 1..99 && label.labelWidthMm in 30..110 && label.labelHeightMm in 20..80)
        require(barcode.scannable && barcode.modules.isNotEmpty())
    }
    override fun getNumberOfPages() = label.copies
    override fun getPageFormat(pageIndex: Int): PageFormat {
        require(pageIndex in 0 until numberOfPages)
        return PageFormat().apply { paper = Paper().apply {
            setSize(this@SystemLabelPages.width, this@SystemLabelPages.height)
            setImageableArea(0.0, 0.0, this@SystemLabelPages.width, this@SystemLabelPages.height)
        } }
    }
    override fun getPrintable(pageIndex: Int): Printable {
        require(pageIndex in 0 until numberOfPages)
        return Printable { graphics, _, requested ->
            if (requested !in 0 until numberOfPages) Printable.NO_SUCH_PAGE else {
                val g = graphics.create() as Graphics2D
                try {
                    g.color = Color.BLACK
                    val margin = 72f / 25.4f * 2
                    val available = width.toFloat() - margin * 2
                    fun line(raw: String, baseline: Float, size: Float, bold: Boolean = false) {
                        val text = raw.replace(Regex("[\\r\\n\\t]+"), " ").take(240)
                        if (text.isBlank()) return
                        val font = Font(Font.SANS_SERIF, if (bold) Font.BOLD else Font.PLAIN, 1)
                        var layout = TextLayout(text, font.deriveFont(size), g.fontRenderContext)
                        if (layout.advance > available) layout = TextLayout(text,
                            font.deriveFont((size * available / layout.advance).coerceAtLeast(3f)), g.fontRenderContext)
                        g.clip = Rectangle2D.Double(margin.toDouble(), 0.0, available.toDouble(), height)
                        layout.draw(g, margin + (available - layout.advance).coerceAtLeast(0f) / 2, baseline)
                        g.clip = null
                    }
                    val h = height.toFloat()
                    line(label.storeName, h * .15f, (h * .10f).coerceAtMost(13f), true)
                    line(label.itemName, h * .31f, (h * .12f).coerceAtMost(15f), true)
                    line(listOf(label.priceText, label.unitText).filter { it.isNotBlank() }.joinToString(" / "),
                        h * .50f, (h * .15f).coerceAtMost(19f), true)
                    val module = available / barcode.modules.size
                    barcode.modules.forEachIndexed { i, black -> if (black)
                        g.fill(Rectangle2D.Float(margin + i * module, h * .57f, module, h * .25f)) }
                    line(barcode.humanText, h * .92f, (h * .08f).coerceAtMost(9f))
                } finally { g.dispose() }
                Printable.PAGE_EXISTS
            }
        }
    }
}

private val systemLabelPrintMutex = Mutex()
fun printSystemLabel(label: StockItemLabelDataModel, printerName: String?, remember: (String) -> Unit): ReceiptPlatformActionResult {
    if (!systemLabelPrintMutex.tryLock()) return ReceiptPlatformActionResult(false, deviceWorkflowText("print_busy"))
    try {
        val pages = SystemLabelPages(label)
        val job = PrinterJob.getPrinterJob()
        if (printerName != null) {
            val selected = PrintServiceLookup.lookupPrintServices(null, null).firstOrNull { it.name == printerName }
                ?: return ReceiptPlatformActionResult(false, deviceWorkflowText("driver_print_failed"))
            job.printService = selected
        }
        job.jobName = "AITA — ${label.itemName.take(80)}"
        job.setPageable(pages)
        if (printerName == null) {
            var accepted = false
            val show = Runnable { accepted = job.printDialog(HashPrintRequestAttributeSet().apply { add(DialogTypeSelection.NATIVE) }) }
            if (SwingUtilities.isEventDispatchThread()) show.run() else SwingUtilities.invokeAndWait(show)
            if (!accepted) return ReceiptPlatformActionResult(false, deviceWorkflowText("print_cancelled"))
            // Save selection before submission. A save failure must not duplicate an already queued job.
            remember(job.printService.name)
        }
        job.setPageable(pages)
        job.print()
        return ReceiptPlatformActionResult(true, deviceWorkflowText("print_queued"))
    } catch (_: Exception) { return ReceiptPlatformActionResult(false, deviceWorkflowText("driver_print_failed")) }
    finally { systemLabelPrintMutex.unlock() }
}
