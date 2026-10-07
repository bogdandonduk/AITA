package kz.aita

import kotlinx.serialization.Serializable

/** Percentages compound; the unit price is rounded exactly once, at the final currency boundary. */
@Serializable data class SaleDiscounts(val itemPercent: Double = 0.0, val cartPercent: Double = 0.0, val buyerPercent: Double = 0.0) {
    fun valid() = listOf(itemPercent, cartPercent, buyerPercent).all(::validQuickDiscount)
    fun effectivePercent(): Double {
        require(valid())
        return (kotlin.math.round(100.0 * (1.0 - (1.0-itemPercent/100.0)*(1.0-cartPercent/100.0)*(1.0-buyerPercent/100.0))*1e10)/1e10).coerceIn(0.0,100.0)
    }
}

@Serializable data class StoreBuyer(
    val id: String, val storeId: String, val name: String,
    val phone: String = "", val email: String = "", val note: String = "",
    val discountPercent: Double = 0.0, val promoTitle: String = "",
    val promoStartsAtMillis: Long? = null, val promoEndsAtMillis: Long? = null,
    val sourceParentBuyerId: String? = null, val revision: Long = 0,
    val isActive: Boolean = true, val updatedAtMillis: Long = 0
) {
    fun valid(): Boolean = validStorePersonId(id) && validStorePersonId(storeId) && name.isNotBlank() && name.length <= 160 &&
        phone.length <= 64 && email.length <= 254 && (email.isBlank() || Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+").matches(email)) &&
        note.length <= 1000 && promoTitle.length <= 160 && validQuickDiscount(discountPercent) && revision >= 0 &&
        (promoStartsAtMillis == null || promoStartsAtMillis >= 0) && (promoEndsAtMillis == null || promoEndsAtMillis > (promoStartsAtMillis ?: 0)) &&
        (sourceParentBuyerId == null || validStorePersonId(sourceParentBuyerId))
    fun activeDiscount(now: Long = getCurrentTimeMillis()): Double = discountPercent.takeIf {
        isActive && (promoStartsAtMillis == null || now >= promoStartsAtMillis) && (promoEndsAtMillis == null || now < promoEndsAtMillis)
    } ?: 0.0
    fun receiptSnapshot(now: Long = getCurrentTimeMillis()) = TransactionBuyerSnapshot(id, name, promoTitle, activeDiscount(now))
}
@Serializable data class TransactionBuyerSnapshot(val id: String, val name: String, val promoTitle: String = "", val discountPercent: Double = 0.0)
@Serializable data class StoreBuyerDirectory(val storeId: String, val buyers: List<StoreBuyer>, val parentBuyers: List<StoreBuyer> = emptyList())

@Serializable enum class WriteOffReason { DAMAGED, EXPIRED, LOST, DEFECTIVE, INTERNAL_USE, INVENTORY_SHORTAGE, OTHER }
@Serializable data class StockWriteOffCommand(
    val id: String, val storeId: String, val batchId: String, val quantity: Double,
    val reason: WriteOffReason, val note: String = ""
) {
    fun valid() = listOf(id,storeId,batchId).all(::validStorePersonId) && quantity.isFinite() && quantity > 0 && quantity <= 1e12 &&
        note.length <= 1000 && (reason != WriteOffReason.OTHER || note.isNotBlank())
}
@Serializable data class StockWriteOff(
    val command: StockWriteOffCommand, val goodsItemId: String, val goodsName: List<LocalizedStringDataModel>,
    val quantityUnit: QuantityDataModel, val supplyPrice: PriceDataModel, val cost: Double,
    val actorUserId: String, val timeMillis: Long, val batchRemaining: Double,
    val categoryIds: List<String> = emptyList(), val supplierId: String? = null
)
@Serializable data class StockWriteOffResult(val record: StockWriteOff, val batch: GoodsBatchDataModel)
@Serializable data class StockWriteOffPage(val records: List<StockWriteOff>, val nextOffset: Int? = null)
@Serializable data class BuyerWrite(val operationId: String, val buyer: StoreBuyer)

fun writeOffRemaining(available: QuantityDataModel, removal: Double): Double? {
    if (!available.total.isFinite() || available.total < 0 || !removal.isFinite() || removal <= 0 ||
        (removal > available.total && (available.roundTotal || removal-available.total > 1e-9)) ||
        (available.roundTotal && removal != kotlin.math.floor(removal)) ||
        (!available.roundTotal && kotlin.math.abs(removal*1000-kotlin.math.round(removal*1000))>0.000001)) return null
    val remaining = (available.total-removal).coerceAtLeast(0.0)
    // Match stock's three decimal places instead of leaving binary subtraction dust.
    return if (available.roundTotal) remaining else kotlin.math.round(remaining*1000.0)/1000.0
}
fun commerceMessage(key: String) = listOf("en","ru","kk","ky","tg","uz").map { LocalizedStringDataModel(it,commerceText(key,it)) }

fun canViewStoreBuyers(store: String?): Boolean = store != null && (currentUserOwnsStore(store) ||
    currentUserHasStorePermission(store, STORE_PERMISSION_BUYERS_VIEW) || currentUserHasStorePermission(store, STORE_PERMISSION_BUYERS_MANAGE) ||
    currentUserHasStorePermission(store, STORE_PERMISSION_SALE_TRANSACTION))
fun canManageStoreBuyers(store: String?): Boolean = store != null && (currentUserOwnsStore(store) || currentUserHasStorePermission(store, STORE_PERMISSION_BUYERS_MANAGE))

/** A response started before an edit cannot overwrite the committed buyer revision. */
internal fun mergeBuyersByRevision(previous:List<StoreBuyer>,incoming:List<StoreBuyer>):List<StoreBuyer> =
    (previous+incoming).groupBy {it.id}.values.map {rows->rows.maxBy {it.revision}}

internal fun nextBuyerPromoBoundary(buyers:Collection<StoreBuyer>,now:Long):Long? = buyers.filter {it.isActive && it.discountPercent>0}
    .flatMap {listOfNotNull(it.promoStartsAtMillis,it.promoEndsAtMillis)}.filter {it>now}.minOrNull()
