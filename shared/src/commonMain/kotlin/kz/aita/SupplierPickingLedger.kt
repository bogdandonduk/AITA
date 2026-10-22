package kz.aita

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlin.math.abs
import kotlin.math.round

/** Physical preparation is device-local. It never changes order quantities, stock or receipt data. */
@Serializable data class SupplierPickingEntry(val signature: String, val quantity: Double, val updatedAtMillis: Long)
@Serializable data class SupplierPickingJournal(
    val accountId: String, val supplierId: String, val schema: Int = 1,
    val entries: Map<String, SupplierPickingEntry> = emptyMap()
)
data class SupplierPickingScope(val accountId: String, val supplierId: String, val isCurrent: () -> Boolean)
data class SupplierPickingLine(
    val orderId: String, val lineId: String, val productId: String,
    val quantity: QuantityDataModel?, val barcodes: List<String>, val signature: String
) { val key: String get() = "$orderId:$lineId" }

fun supplierPickingLine(line: SupplierOrderLineDataModel): SupplierPickingLine {
    val quantity = line.supplierAcceptedQuantity?.takeIf {
        it.total.isFinite() && it.total > 0 && it.pricedAmount.isFinite() && it.pricedAmount > 0 &&
            (!it.roundTotal || abs(it.total - round(it.total)) < 0.0000001)
    }
    val substitute = !line.substituteGoodsItemId.isNullOrBlank()
    return SupplierPickingLine(line.orderId, line.id,
        if (substitute) line.substituteGoodsItemId!! else line.goodsItemId, quantity,
        if (substitute) line.substituteGoodsItemBarcodeSnapshots else line.goodsItemBarcodeSnapshots,
        jsonBase.encodeToString(listOf(line.goodsItemId, line.substituteGoodsItemId.orEmpty(),
            quantity?.id.orEmpty(), quantity?.total.toString(), quantity?.pricedAmount.toString(), quantity?.roundTotal.toString())))
}
fun supplierPickedQuantity(line: SupplierPickingLine, entry: SupplierPickingEntry?): Double = entry
    ?.takeIf { it.signature == line.signature && supplierPickingQuantityValid(line, it.quantity) }
    ?.let { supplierPickingCanonicalQuantity(line, it.quantity) } ?: 0.0
fun supplierPickingQuantityValid(line: SupplierPickingLine, quantity: Double): Boolean {
    val agreed = line.quantity ?: return false
    return quantity.isFinite() && quantity >= 0 && (quantity <= agreed.total || abs(quantity - agreed.total) < 0.0000001) &&
        (!agreed.roundTotal || abs(quantity - round(quantity)) < 0.0000001)
}
private fun supplierPickingCanonicalQuantity(line: SupplierPickingLine, quantity: Double): Double {
    val agreed = requireNotNull(line.quantity)
    return when {
        abs(quantity - agreed.total) < 0.0000001 -> agreed.total
        agreed.roundTotal -> round(quantity)
        else -> quantity
    }
}

fun supplierPickingComplete(line: SupplierPickingLine, entry: SupplierPickingEntry?): Boolean =
    line.quantity?.let { abs(supplierPickedQuantity(line, entry) - it.total) < 0.0000001 } == true

/** Only orders whose complete active line set is present and physically checked can be bulk-packed. */
fun supplierPickingReadyOrderIds(bundles: List<SupplierOrderWithLinesDataModel>, eligibleIds: List<String>, entries: Map<String, SupplierPickingEntry>): List<String> =
    eligibleIds.distinct().filter { id ->
        val bundle = bundles.singleOrNull { it.order.id == id && it.order.isActive } ?: return@filter false
        val lines = bundle.lines.filter { it.isActive && it.orderId == id }
        val accepted = lines.filter { it.supplierAcceptedQuantity?.total?.let { total -> total.isFinite() && total > 0 } == true }
        // A rejected zero-quantity line needs no packing. Missing or malformed responses do.
        accepted.isNotEmpty() && lines.all { it.id.isNotBlank() && it.supplierAcceptedQuantity != null } &&
            lines.map { it.id }.distinct().size == lines.size &&
            lines.all { it.supplierAcceptedQuantity?.total == 0.0 || supplierPickingComplete(supplierPickingLine(it), entries["$id:${it.id}"]) }
    }

sealed interface SupplierPickingScan {
    data class Matches(val lines: List<SupplierPickingLine>, val quantitiesByLine: Map<String, Double?>) : SupplierPickingScan {
        /** Convenience for a single physical unit; chooser callers must use quantityForLine. */
        val quantity: Double? get() = quantitiesByLine.values.distinct().singleOrNull()
        fun quantityForLine(line: SupplierPickingLine): Double? = quantitiesByLine[line.key]
    }
    data object Missing : SupplierPickingScan
    data object AmbiguousWeight : SupplierPickingScan
}
/** Preserve leading PLU zeroes. Only full checksum-valid UPC-A codes have an EAN-13 alias;
 * a short six-digit code might be a plain PLU and must never be guessed to carry a prefix. */
private fun supplierPickingScaleFormats(token: String, allowZeroWeight: Boolean = false): List<EmbeddedWeightBarcodeDataModel> {
    if (!token.all(Char::isDigit)) return emptyList()
    if (token.length == 12 && token.startsWith("2") && token.hasValidRetailBarcodeChecksum())
        return ("0$token").parseEmbeddedWeightBarcodeFormats(requireValidChecksum = true, allowZeroWeight = allowZeroWeight)
            .filter { it.productCodeLength == 5 }
            .map { it.copy(productLookupCodes = (it.productLookupCodes + token.take(6)).distinct()) }
    return token.parseEmbeddedWeightBarcodeFormats(requireValidChecksum = true, allowZeroWeight = allowZeroWeight)
}

/** Strict exact matching for ordinary codes. Weighted product prefixes must identify one scale format. */
fun supplierPickingScan(raw: String, lines: List<SupplierPickingLine>): SupplierPickingScan {
    val token = raw.trim().removePrefix("]C1").removePrefix("]E0").trim()
    if (token.isBlank() || token.length > 128 || token.any { it.isISOControl() }) return SupplierPickingScan.Missing
    val exact = lines.filter { line -> line.quantity != null && line.barcodes.any { it.trim().equals(token, true) } }
    val weighted = supplierPickingScaleFormats(token).flatMap { scale ->
        lines.filter { line -> line.quantity?.isWeightQuantityUnit() == true && line.barcodes.any { stored ->
            val code = stored.trim()
            code in scale.productLookupCodes || supplierPickingScaleFormats(code, allowZeroWeight = true)
                .any { it.formatId == scale.formatId && it.productBarcode == scale.productBarcode }
        } }.map { line -> line to line.quantity!!.quantityFromScale(scale)!!.total }
    }
    val matches = if (exact.isNotEmpty()) exact else weighted.map { it.first }.distinctBy { it.key }
    if (matches.isEmpty()) return SupplierPickingScan.Missing
    val quantities = mutableMapOf<String, Double?>()
    matches.forEach { line ->
        if (line.quantity?.isWeightQuantityUnit() == true) {
            val measurements = weighted.filter { (matched, _) -> matched.key == line.key }.map { it.second }.distinct()
            if (measurements.size > 1) return SupplierPickingScan.AmbiguousWeight
            // Product-only weight barcodes open the quantity editor instead of pretending one unit was weighed.
            quantities[line.key] = measurements.singleOrNull()
        } else quantities[line.key] = if (line.quantity?.roundTotal == true) 1.0 else null
    }
    return SupplierPickingScan.Matches(matches, quantities)
}

/** Serial, read-back verified local journal. Failed storage is never displayed as saved progress. */
class SupplierPickingLedger(
    private val read: suspend (String) -> String?, private val write: suspend (String, String) -> Unit,
    private val now: () -> Long = ::getCurrentTimeMillis
) {
    private val mutex = Mutex()
    private fun key(owner: SupplierPickingScope) = "supplier-picking-v2:${owner.accountId}:${owner.supplierId}"
    private fun checkOwner(owner: SupplierPickingScope) {
        check(owner.accountId.isNotBlank() && owner.supplierId.isNotBlank() && owner.isCurrent()) { "Picking owner changed" }
    }
    private suspend fun readJournal(owner: SupplierPickingScope): SupplierPickingJournal {
        checkOwner(owner)
        val raw = read(key(owner))
        checkOwner(owner)
        check(raw == null || raw.length <= 1_000_000) { "Picking journal is too large" }
        val journal = raw?.let { jsonBase.decodeFromString<SupplierPickingJournal>(it) } ?: SupplierPickingJournal(owner.accountId, owner.supplierId)
        check(journal.accountId == owner.accountId && journal.supplierId == owner.supplierId && journal.schema == 1)
        check(journal.entries.size <= 4096 && journal.entries.all { (key, entry) ->
            key.length <= 256 && entry.signature.length <= 1000 && entry.quantity.isFinite() && entry.quantity >= 0 && entry.updatedAtMillis >= 0
        })
        return journal
    }
    suspend fun load(owner: SupplierPickingScope): Map<String, SupplierPickingEntry> = mutex.withLock { readJournal(owner).entries }
    suspend fun update(owner: SupplierPickingScope, lines: List<SupplierPickingLine>, changes: Map<String, Double>): Map<String, SupplierPickingEntry> = mutex.withLock {
        val journal = readJournal(owner)
        val current = lines.associateBy { it.key }
        check(changes.all { (key, quantity) -> current[key]?.let { supplierPickingQuantityValid(it, quantity) } == true })
        val timestamp = now()
        val next = journal.entries.filterValues { timestamp - it.updatedAtMillis <= 90L * 24 * 60 * 60 * 1000 }.toMutableMap()
        changes.forEach { (key, quantity) ->
            val line = current.getValue(key)
            val canonical = supplierPickingCanonicalQuantity(line, quantity)
            if (canonical == 0.0) next.remove(key)
            else next[key] = SupplierPickingEntry(line.signature, canonical, timestamp)
        }
        check(next.size <= 4096)
        val encoded = jsonBase.encodeToString(journal.copy(entries = next))
        check(encoded.length <= 1_000_000)
        checkOwner(owner)
        write(key(owner), encoded)
        check(read(key(owner)) == encoded) { "Picking progress was not saved" }
        checkOwner(owner)
        next
    }
}
private val supplierPickingLedger by lazy { SupplierPickingLedger(::readRequiredJsonJournal, ::writeJsonCacheText) }
suspend fun loadSupplierPicking(owner: SupplierPickingScope) = withContext(Dispatchers.ourIo) { supplierPickingLedger.load(owner) }
suspend fun saveSupplierPicking(owner: SupplierPickingScope, lines: List<SupplierPickingLine>, changes: Map<String, Double>) =
    withContext(Dispatchers.ourIo) { supplierPickingLedger.update(owner, lines, changes) }
