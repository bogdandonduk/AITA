package kz.aita

private const val RECEIPT_BARCODE_PREFIX = "99101"
private const val RECEIPT_OPERATION_BARCODE_PREFIX = "99102"
private val RECEIPT_UUID = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

/** Format 1: 99101 + the unsigned 128-bit UUID in exactly 39 decimal digits.
 * Numeric Code 128 C works with both 1D keyboard scanners and camera readers. This is only
 * a lookup key: store/account access and remaining return quantities still require validation.
 */
fun transactionReceiptBarcodePayload(transactionId: String): String? {
    if (!RECEIPT_UUID.matches(transactionId)) return null
    val decimal = IntArray(39)
    transactionId.filter { it != '-' }.forEach { hex ->
        var carry = hex.digitToInt(16)
        for (i in decimal.lastIndex downTo 0) {
            val value = decimal[i] * 16 + carry
            decimal[i] = value % 10
            carry = value / 10
        }
        check(carry == 0)
    }
    return RECEIPT_BARCODE_PREFIX + decimal.joinToString("")
}

data class TransactionReceiptBarcodeIdentity(val transactionId: String? = null, val clientOperationId: String? = null)

/** Operation format2 retains the original lookup identity after an offline sale reaches the server. */
fun transactionOperationReceiptBarcodePayload(clientOperationId: String): String? {
    if (!clientOperationId.startsWith("txn-") || clientOperationId != clientOperationId.lowercase()) return null
    val payload = transactionReceiptBarcodePayload(clientOperationId.removePrefix("txn-")) ?: return null
    return RECEIPT_OPERATION_BARCODE_PREFIX + payload.drop(RECEIPT_BARCODE_PREFIX.length)
}

/** Accept surrounding scanner terminators and the optional Code 128 AIM identifier only.
 * Product codes, partial payloads, arbitrary text/URLs and values outside 128 bits are rejected.
 */
fun parseTransactionReceiptBarcodeIdentity(raw: String): TransactionReceiptBarcodeIdentity? {
    if (raw.length > 64) return null
    val value = raw.trim().removePrefix("]C0")
    if (value.length != 44 || value.any { it !in '0'..'9' }) return null
    val prefix = value.take(RECEIPT_BARCODE_PREFIX.length)
    if (prefix != RECEIPT_BARCODE_PREFIX && prefix != RECEIPT_OPERATION_BARCODE_PREFIX) return null
    val bytes = IntArray(16)
    value.drop(RECEIPT_BARCODE_PREFIX.length).forEach { digit ->
        var carry = digit - '0'
        for (i in bytes.lastIndex downTo 0) {
            val next = bytes[i] * 10 + carry
            bytes[i] = next and 255
            carry = next ushr 8
        }
        if (carry != 0) return null
    }
    val hex = bytes.joinToString("") { it.toString(16).padStart(2, '0') }
    val uuid = "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}"
    return if (prefix == RECEIPT_BARCODE_PREFIX) TransactionReceiptBarcodeIdentity(transactionId = uuid)
        else TransactionReceiptBarcodeIdentity(clientOperationId = "txn-$uuid")
}

fun parseTransactionReceiptBarcode(raw: String): String? = parseTransactionReceiptBarcodeIdentity(raw)?.transactionId

/** Unsaved previews have no identity. Accepted offline operations retain a stable operation lookup;
 * a server acknowledgement later allows UUID lookup without invalidating the already-printed code.
 */
fun TransactionDataModel.receiptBarcodePayload(): String? {
    serverReceiptIdOrNull()?.let(::transactionReceiptBarcodePayload)?.let { return it }
    if (!id.startsWith("local_", ignoreCase = true)) return null
    return transactionOperationReceiptBarcodePayload(clientOperationId)
}

// ISO/IEC 15417 Code 128 element widths, indexed by symbol value. These fixed standard
// patterns also appear in ZXing's Code128Reader; there is no platform/runtime dependency.
private val RECEIPT_CODE_128_WIDTHS = listOf(
    "212222", "222122", "222221", "121223", "121322", "131222", "122213", "122312", "132212", "221213",
    "221312", "231212", "112232", "122132", "122231", "113222", "123122", "123221", "223211", "221132",
    "221231", "213212", "223112", "312131", "311222", "321122", "321221", "312212", "322112", "322211",
    "212123", "212321", "232121", "111323", "131123", "131321", "112313", "132113", "132311", "211313",
    "231113", "231311", "112133", "112331", "132131", "113123", "113321", "133121", "313121", "211331",
    "231131", "213113", "213311", "213131", "311123", "311321", "331121", "312113", "312311", "332111",
    "314111", "221411", "431111", "111224", "111422", "121124", "121421", "141122", "141221", "112214",
    "112412", "122114", "122411", "142112", "142211", "241211", "221114", "413111", "241112", "134111",
    "111242", "121142", "121241", "114212", "124112", "124211", "411212", "421112", "421211", "212141",
    "214121", "412121", "111143", "111341", "131141", "114113", "114311", "411113", "411311", "113141",
    "114131", "311141", "411131", "211412", "211214", "211232", "2331112"
)

/** Ten white modules on both ends are part of the symbol and must never be cropped. */
fun transactionReceiptBarcodeModules(payload: String): BooleanArray {
    require(parseTransactionReceiptBarcodeIdentity(payload) != null && payload.length == 44) { "Invalid transaction receipt barcode" }
    val symbols = mutableListOf(105) // Start C: every subsequent symbol is a pair of digits.
    payload.chunked(2).forEach { symbols += it.toInt() }
    var checksum = symbols.first()
    for (i in 1 until symbols.size) checksum += symbols[i] * i
    symbols += checksum % 103
    symbols += 106
    val modules = ArrayList<Boolean>(297)
    repeat(10) { modules += false }
    symbols.forEach { symbol ->
        RECEIPT_CODE_128_WIDTHS[symbol].forEachIndexed { index, width ->
            repeat(width - '0') { modules += index % 2 == 0 }
        }
    }
    repeat(10) { modules += false }
    return modules.toBooleanArray()
}

data class ReceiptBarcodeBar(val x: Float, val y: Float, val width: Float, val height: Float)
data class ReceiptBarcodeGeometry(val width: Float, val height: Float, val bars: List<ReceiptBarcodeBar>)

/** Rotate narrow receipts instead of shrinking modules below the printer/scanner resolution. */
fun transactionReceiptBarcodeGeometry(
    payload: String,
    moduleWidth: Float,
    barHeight: Float,
    vertical: Boolean = false
): ReceiptBarcodeGeometry {
    require(moduleWidth.isFinite() && moduleWidth > 0f && barHeight.isFinite() && barHeight > 0f)
    val modules = transactionReceiptBarcodeModules(payload)
    val crossSize = barHeight + 8 * moduleWidth
    val bars = ArrayList<ReceiptBarcodeBar>()
    var index = 0
    while (index < modules.size) {
        if (!modules[index]) { index++; continue }
        val start = index
        while (index < modules.size && modules[index]) index++
        val at = start * moduleWidth
        val width = (index - start) * moduleWidth
        bars += if (vertical) ReceiptBarcodeBar(4 * moduleWidth, at, barHeight, width)
            else ReceiptBarcodeBar(at, 4 * moduleWidth, width, barHeight)
    }
    return if (vertical) ReceiptBarcodeGeometry(crossSize, modules.size * moduleWidth, bars)
        else ReceiptBarcodeGeometry(modules.size * moduleWidth, crossSize, bars)
}
