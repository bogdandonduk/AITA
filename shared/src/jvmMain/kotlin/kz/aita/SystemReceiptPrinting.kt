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

/** Caller uses a worker dispatcher. Dialog alone is on AWT; layout and spool submission are not. */
fun printSystemReceiptDocument(title: String, document: AitaPdfDocument): ReceiptPlatformActionResult {
    if (!systemReceiptPrintMutex.tryLock()) return ReceiptPlatformActionResult(false, deviceWorkflowText("print_busy"))
    try {
        val pages = SystemReceiptPages(document)
        val job = PrinterJob.getPrinterJob()
        job.jobName = title
        job.setPageable(pages)
        var accepted = false
        val showDialog = Runnable { accepted = job.printDialog() }
        if (SwingUtilities.isEventDispatchThread()) showDialog.run() else SwingUtilities.invokeAndWait(showDialog)
        if (!accepted) return ReceiptPlatformActionResult(false, deviceWorkflowText("print_cancelled"))
        // Never fall back to RAW on failure: the job may already be in the queue.
        job.print()
        return ReceiptPlatformActionResult(true, deviceWorkflowText("print_queued"))
    } catch (error: Exception) {
        System.err.println("AITA system receipt printing failed: ${error.javaClass.simpleName}: ${error.message}")
        return ReceiptPlatformActionResult(false, deviceWorkflowText("driver_print_failed"))
    } finally { systemReceiptPrintMutex.unlock() }
}
