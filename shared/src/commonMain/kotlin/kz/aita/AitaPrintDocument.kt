package kz.aita

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
        append("</title><style>@page{size:${width}pt ${maxHeight}pt;margin:${margin}pt}")
        append("html,body{background:white;color:black}body{margin:0;width:${contentWidth}pt;font-family:Arial,'Noto Sans',sans-serif}")
        append(".line{white-space:pre-wrap;overflow-wrap:anywhere;line-height:1.4;min-height:1em}.barcode{break-inside:avoid;text-align:center}hr{border:0;border-top:1pt solid black;margin:6pt 0}</style></head><body>")
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
        append("</body></html>")
    }
}
