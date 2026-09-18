package kz.aita

import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage

internal actual fun renderReceiptRaster(lines: List<String>, barcodePayload: String?): ByteArray? {
    // Logical fonts include platform fallbacks for Cyrillic/Kazakh. No external font download.
    val regular = Font(Font.MONOSPACED, Font.PLAIN, 20)
    val heading = Font(Font.MONOSPACED, Font.BOLD, 20)
    val image = BufferedImage(RECEIPT_RASTER_WIDTH, 32, BufferedImage.TYPE_INT_RGB)
    val graphics = image.createGraphics()
    return try {
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF)
        val layout = layoutReceiptRasterLines(lines) { text, bold ->
            graphics.font = if (bold) heading else regular
            graphics.fontMetrics.stringWidth(text).toFloat()
        }
        val encoder = ReceiptRasterEncoder()
        layout.forEach { line ->
            graphics.color = Color.WHITE
            graphics.fillRect(0, 0, image.width, image.height)
            graphics.font = if (line.heading) heading else regular
            graphics.color = Color.BLACK
            val width = graphics.fontMetrics.stringWidth(line.text)
            val x = if (line.heading) ((image.width - width) / 2).coerceAtLeast(4) else 4
            graphics.drawString(line.text, x, 23)
            encoder.strip(image.width, image.height, image.getRGB(0, 0, image.width, image.height, null, 0, image.width))
        }
        barcodePayload?.let(encoder::barcode)
        encoder.finish()
    } finally {
        graphics.dispose()
        image.flush()
    }
}
