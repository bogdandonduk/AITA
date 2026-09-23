package kz.aita

/** Some SPP adapters acknowledge a write before the small printer receive buffer consumes it.
 * Keep a bounded stream, including the footer, and keep the connection alive after the final
 * flush. This is transport completion, not proof that paper printed. Never retry a partial job.
 */
fun writePacedBluetoothPrint(bytes: ByteArray, write: (ByteArray, Int, Int) -> Unit,
    flush: () -> Unit, pause: (Long) -> Unit) {
    require(bytes.isNotEmpty() && bytes.size <= RECEIPT_RASTER_MAX_BYTES)
    var offset = 0
    while (offset < bytes.size) {
        val count = minOf(512, bytes.size - offset)
        write(bytes, offset, count)
        flush()
        offset += count
        pause(50L)
    }
    pause(1_000L)
}

fun bluetoothPrinterWriteTimeoutMillis(byteCount: Int): Long =
    receiptPrinterWriteTimeoutMillis(byteCount) + ((byteCount.toLong() + 511) / 512) * 50L + 1_000L
