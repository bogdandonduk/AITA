package kz.aita

/** Fixed printer dots, deliberately independent of the phone's theme, density and UI scale. */
internal const val RECEIPT_RASTER_WIDTH = 384
internal const val RECEIPT_RASTER_TEXT_WIDTH = 376
internal const val RECEIPT_RASTER_MAX_TEXT = 250_000
internal const val RECEIPT_RASTER_MAX_LINES = 4_000
internal const val RECEIPT_RASTER_MAX_BYTES = 8 * 1024 * 1024

internal data class ReceiptRasterLine(val text: String, val heading: Boolean = false)

/** Null only on platforms whose printing path is PDF/browser rather than raw ESC/POS. */
internal expect fun renderReceiptRaster(lines: List<String>): ByteArray?

/** Layout contains Unicode text, never printer commands. Avoid splitting surrogate pairs. */
internal fun layoutReceiptRasterLines(
    lines: List<String>,
    measure: (String, Boolean) -> Float
): List<ReceiptRasterLine> {
    require(lines.size <= RECEIPT_RASTER_MAX_LINES) { "Receipt has too many lines" }
    require(lines.sumOf { it.length.toLong() } <= RECEIPT_RASTER_MAX_TEXT) { "Receipt is too large to print safely" }
    val result = ArrayList<ReceiptRasterLine>()
    fun append(line: ReceiptRasterLine) {
        require(result.size < RECEIPT_RASTER_MAX_LINES) { "Receipt has too many lines" }
        result += line
    }
    lines.forEachIndexed { index, raw ->
        val clean = buildString(raw.length) {
            raw.forEach { ch ->
                append(when {
                    ch == '\t' || ch == '\u00a0' -> ' '
                    ch.code < 32 || ch.code in 127..159 -> ' '
                    else -> ch
                })
            }
        }.trimEnd()
        val heading = index == 0 && clean.isNotBlank()
        if (clean.isEmpty()) {
            append(ReceiptRasterLine(""))
        } else if (clean.length >= 8 && (clean.all { it == '-' } || clean.all { it == '=' })) {
            val glyphWidth = measure(clean.take(1), false)
            require(glyphWidth.isFinite() && glyphWidth > 0f)
            append(ReceiptRasterLine(clean.take((RECEIPT_RASTER_TEXT_WIDTH / glyphWidth).toInt().coerceAtLeast(1))))
        } else {
            require(clean.length <= 16_384) { "Receipt line is too long" }
            var remaining = clean
            val indent = clean.takeWhile { it == ' ' }.take(8)
            while (remaining.isNotEmpty()) {
                if (measure(remaining, heading) <= RECEIPT_RASTER_TEXT_WIDTH) {
                    append(ReceiptRasterLine(remaining, heading))
                    break
                }
                var lo = 0
                var hi = remaining.length
                while (lo < hi) {
                    val mid = (lo + hi + 1) / 2
                    if (measure(remaining.substring(0, mid), heading) <= RECEIPT_RASTER_TEXT_WIDTH) lo = mid else hi = mid - 1
                }
                var cut = lo
                if (cut > 0 && cut < remaining.length && remaining[cut - 1].isHighSurrogate() && remaining[cut].isLowSurrogate()) cut--
                require(cut > 0) { "Receipt contains a glyph wider than the printer line" }
                val wordBreak = remaining.lastIndexOf(' ', cut - 1)
                if (wordBreak > indent.length && wordBreak >= cut / 2) cut = wordBreak
                require(cut > indent.length) { "Receipt glyph does not fit after indentation" }
                append(ReceiptRasterLine(remaining.substring(0, cut).trimEnd(), heading))
                val tail = remaining.substring(cut).trimStart()
                remaining = if (tail.isEmpty()) "" else indent + tail
            }
        }
    }
    return result
}

/** Small strips avoid a receipt-height bitmap and the tiny image buffers in thermal printers. */
internal class ReceiptRasterEncoder {
    private var buffer = ByteArray(4096)
    private var size = 0

    init {
        command(0x1b, 0x40) // reset
        command(0x1c, 0x2e) // leave multibyte character mode for trailing feed commands
        command(0x1b, 0x61, 0) // left aligned raster canvas (heading is centered in the bitmap)
    }

    private fun reserve(count: Int) {
        require(count >= 0 && size.toLong() + count <= RECEIPT_RASTER_MAX_BYTES) { "Receipt exceeds printer job limit" }
        if (size + count > buffer.size) buffer = buffer.copyOf(maxOf(size + count, (buffer.size * 2).coerceAtMost(RECEIPT_RASTER_MAX_BYTES)))
    }

    private fun command(vararg bytes: Int) {
        reserve(bytes.size)
        bytes.forEach { buffer[size++] = it.toByte() }
    }

    fun strip(width: Int, height: Int, argb: IntArray) {
        require(width in 1..RECEIPT_RASTER_WIDTH && height in 1..64 && argb.size == width * height)
        val stride = (width + 7) / 8
        reserve(8 + stride * height)
        command(0x1d, 0x76, 0x30, 0, stride and 255, stride shr 8, height and 255, height shr 8)
        repeat(height) { y ->
            repeat(stride) { col ->
                var bits = 0
                repeat(8) { bit ->
                    val x = col * 8 + bit
                    if (x < width) {
                        val pixel = argb[y * width + x]
                        val alpha = (pixel ushr 24) and 255
                        val gray = (((pixel ushr 16) and 255) * 299 + ((pixel ushr 8) and 255) * 587 + (pixel and 255) * 114) / 1000
                        val onWhite = (gray * alpha + 255 * (255 - alpha)) / 255
                        if (onWhite < 160) bits = bits or (0x80 ushr bit)
                    }
                }
                buffer[size++] = bits.toByte()
            }
        }
    }

    fun finish(): ByteArray {
        command(0x1b, 0x64, 3) // feed three lines only after all raster strips
        command(0x1d, 0x56, 0x42, 0) // optional cutter, as in the existing receipt path
        return buffer.copyOf(size)
    }
}

/** Raster jobs are larger than text. Bound slow links without automatically replaying a partial job. */
fun receiptPrinterWriteTimeoutMillis(byteCount: Int): Long {
    require(byteCount in 1..RECEIPT_RASTER_MAX_BYTES) { "Invalid printer data size" }
    return (30_000L + ((byteCount.toLong() + 4095L) / 4096L) * 1000L).coerceAtMost(180_000L)
}
