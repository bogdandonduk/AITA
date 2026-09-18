package kz.aita

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface

internal actual fun renderReceiptRaster(lines: List<String>, barcodePayload: String?): ByteArray? {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 20f }
    val regular = Typeface.create("monospace", Typeface.NORMAL)
    val heading = Typeface.create("monospace", Typeface.BOLD)
    val layout = layoutReceiptRasterLines(lines) { text, bold ->
        paint.typeface = if (bold) heading else regular
        paint.measureText(text)
    }
    val bitmap = Bitmap.createBitmap(RECEIPT_RASTER_WIDTH, 32, Bitmap.Config.ARGB_8888)
    return try {
        val canvas = Canvas(bitmap)
        val pixels = IntArray(bitmap.width * bitmap.height)
        val encoder = ReceiptRasterEncoder()
        layout.forEach { line ->
            canvas.drawColor(Color.WHITE)
            paint.typeface = if (line.heading) heading else regular
            val width = paint.measureText(line.text)
            val x = if (line.heading) ((bitmap.width - width) / 2f).coerceAtLeast(4f) else 4f
            canvas.drawText(line.text, x, 23f, paint)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            encoder.strip(bitmap.width, bitmap.height, pixels)
        }
        barcodePayload?.let(encoder::barcode)
        encoder.finish()
    } finally {
        bitmap.recycle()
    }
}
