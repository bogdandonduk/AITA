package kz.aita

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.ByteArrayOutputStream
import kotlin.math.ceil

actual fun renderAitaPdfDocument(document: AitaPdfDocument): ByteArray {
    val paints = mutableMapOf<AitaPdfStyle, Paint>()
    fun paint(style: AitaPdfStyle): Paint = paints.getOrPut(style) {
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            color = Color.BLACK
            textSize = style.size
            typeface = Typeface.create("sans-serif", if (style.bold) Typeface.BOLD else Typeface.NORMAL)
        }
    }
    val pages = layoutAitaPdfDocument(document, { text, style -> paint(style).measureText(text) }) { style ->
        paint(style).fontMetrics.let { it.ascent to it.descent }
    }
    // PdfDocument has close(), but is not Closeable. Always finish an open page before closing.
    val pdf = PdfDocument()
    try {
        pages.forEachIndexed { index, page ->
            val native = pdf.startPage(PdfDocument.PageInfo.Builder(ceil(page.width).toInt(), ceil(page.height).toInt(), index + 1).create())
            try {
                native.canvas.drawColor(Color.WHITE)
                page.lines.forEach { line ->
                    val barcode = line.barcode
                    if (barcode != null) {
                        val ink = Paint().apply { color = Color.BLACK }
                        barcode.bars.forEach { bar ->
                            native.canvas.drawRect(line.x + bar.x, line.baseline + bar.y,
                                line.x + bar.x + bar.width, line.baseline + bar.y + bar.height, ink)
                        }
                    } else if (line.divider) {
                        val rule = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; strokeWidth = 0.6f }
                        native.canvas.drawLine(document.margin, line.baseline, page.width - document.margin, line.baseline, rule)
                    } else native.canvas.drawText(line.text, line.x, line.baseline, paint(line.style))
                }
            } finally { pdf.finishPage(native) }
        }
        return ByteArrayOutputStream().use { output -> pdf.writeTo(output); output.toByteArray() }
    } finally { pdf.close() }
}
