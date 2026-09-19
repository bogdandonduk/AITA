package kz.aita

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CancellationException

const val SYSTEM_DOCUMENT_PRINTER_ID = "desktop-system-print"
private const val RECEIPT_PAPER_WIDTH_KEY = "receipt-paper-width-mm"
val receiptPaperWidthMmState = MutableStateFlow(80)
var printReceiptDocumentPlatformAction: (suspend (String, AitaPdfDocument) -> ReceiptPlatformActionResult)? = null

fun receiptUsesSystemDocumentPrinting(): Boolean = preferHtmlDocumentPrinting ||
    configuredReceiptPrinterDeviceIdState.value == SYSTEM_DOCUMENT_PRINTER_ID
fun labelUsesSystemDocumentPrinting(): Boolean = preferHtmlDocumentPrinting ||
    configuredLabelPrinterDeviceIdState.value == SYSTEM_DOCUMENT_PRINTER_ID

fun normalizedReceiptPaperWidthMm(value: Int?): Int = if (value == 58) 58 else 80

suspend fun loadReceiptPaperWidth(): Int {
    val stored = try { getLocalKv(RECEIPT_PAPER_WIDTH_KEY)?.toIntOrNull() }
    catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { receiptPaperWidthMmState.value }
    return normalizedReceiptPaperWidthMm(stored).also { receiptPaperWidthMmState.value = it }
}

suspend fun saveReceiptPaperWidth(widthMm: Int) {
    val width = normalizedReceiptPaperWidthMm(widthMm)
    putLocalKv(RECEIPT_PAPER_WIDTH_KEY, width.toString())
    receiptPaperWidthMmState.value = width
}

/** Only prepared print copies change size; downloaded receipts retain their original PDF layout. */
fun AitaPdfDocument.forReceiptPaper(widthMm: Int): AitaPdfDocument = copy(
    width = normalizedReceiptPaperWidthMm(widthMm) * 72f / 25.4f,
    // Common printable areas: 48 mm on a 58 mm roll, 72 mm on an 80 mm roll.
    margin = (if (normalizedReceiptPaperWidthMm(widthMm) == 58) 5f else 4f) * 72f / 25.4f,
    minHeight = 0f
)

suspend fun printReceiptDocument(title: String, document: AitaPdfDocument): ReceiptPlatformActionResult {
    if (preferHtmlDocumentPrinting) loadBrowserReceiptPrinterName()
    if (preferHtmlDocumentPrinting && !browserReceiptPrinterEnabledState.value) return ReceiptPlatformActionResult(false, deviceWorkflowText("clear_help"))
    val prepared = document.forReceiptPaper(loadReceiptPaperWidth())
    return printReceiptDocumentPlatformAction?.invoke(title, prepared)
        ?: printHtmlDocument(title, prepared.toPrintHtml(title))
}

/** Use a document in the system print dialog instead of trying to discover browser hardware. */
var preferHtmlDocumentPrinting: Boolean = false

fun deviceWorkflowText(key: String, language: String = appLanguageState.value): String =
    eventMessage("device.workflow.$key").extractLocalizedString(language).orEmpty()

internal fun printHtmlEscape(text: String): String = text.replace("&", "&amp;").replace("<", "&lt;")
    .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")

/** Same immutable receipt/report content and barcode geometry as the native PDF path. */
fun AitaPdfDocument.toPrintHtml(title: String): String {
    require(width.isFinite() && width >= 100 && maxHeight.isFinite() && maxHeight > 0)
    require(margin.isFinite() && margin >= 0 && width - margin * 2 >= 60 && bodySize in 6f..30f)
    require(blocks.size <= 50_000 && blocks.sumOf { it.text.length.toLong() } <= 2_000_000L)
    val contentWidth = width - margin * 2
    return buildString {
        append("<!doctype html><html><head><meta charset=\"UTF-8\"><title>")
        append(printHtmlEscape(title))
        append("</title>")
        if (minHeight == 0f && width < 250f) {
            append("<meta name=\"aita-receipt-paper\" content=\"${width},${margin},${maxHeight}\">")
        }
        append("<style>@page{size:${width}pt ${maxHeight}pt;margin:${margin}pt}")
        append("html,body{background:white;color:black;-webkit-text-fill-color:black;print-color-adjust:exact}body{margin:0;width:${contentWidth}pt;font-family:Arial,'Noto Sans',sans-serif}#aita-print-content{display:flow-root}")
        append(".line{white-space:pre-wrap;overflow-wrap:anywhere;line-height:1.4;min-height:1em}.barcode{break-inside:avoid;text-align:center}svg{display:block;margin:0 auto}hr{border:0;border-top:1pt solid black;margin:6pt 0}</style></head><body><main id=\"aita-print-content\">")
        blocks.forEach { block ->
            val payload = block.barcodePayload
            when {
                payload != null -> {
                    val geometry = transactionReceiptBarcodeGeometry(payload, .72f, 36f,
                        vertical = transactionReceiptBarcodeModules(payload).size * .72f > contentWidth)
                    require(geometry.width <= contentWidth && geometry.height <= maxHeight - margin * 2)
                    append("<div class=\"barcode\"><svg xmlns=\"http://www.w3.org/2000/svg\" role=\"img\" aria-label=\"")
                    append(printHtmlEscape(payload))
                    append("\" width=\"${geometry.width}pt\" height=\"${geometry.height}pt\" viewBox=\"0 0 ${geometry.width} ${geometry.height}\">")
                    append("<rect width=\"100%\" height=\"100%\" fill=\"white\"/>")
                    geometry.bars.forEach { bar -> append("<rect x=\"${bar.x}\" y=\"${bar.y}\" width=\"${bar.width}\" height=\"${bar.height}\" fill=\"black\"/>") }
                    append("</svg></div>")
                }
                block.role == AitaPdfRole.Divider -> append("<hr>")
                else -> {
                    val style = style(block.role)
                    append("<div class=\"line\" style=\"font-size:${style.size}pt;font-weight:${if (style.bold) 700 else 400};text-align:${if (style.centered) "center" else "left"}\">")
                    append(printHtmlEscape(block.text))
                    append("</div>")
                }
            }
        }
        append("</main></body></html>")
    }
}
