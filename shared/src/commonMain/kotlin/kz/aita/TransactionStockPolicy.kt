package kz.aita

import kotlinx.serialization.Serializable

/** Special groups still hold separate records and units for each goods item. */
@Serializable
enum class StockBatchKindDataModel { NORMAL, RETURNED, UNIVERSAL, UNLIMITED }

/** Captured by the server in the same transaction that debits inventory. */
@Serializable
data class TransactionStockAllocationDataModel(
    val stockBatchId: String,
    val storeId: String,
    val quantity: Double
)

/** Input order is the existing inventory priority; this function never changes that policy. */
fun planTransactionStockConsumption(
    orderedAvailable: List<TransactionStockAllocationDataModel>,
    requestedQuantity: Double
): List<TransactionStockAllocationDataModel>? {
    if (!requestedQuantity.isFinite() || requestedQuantity <= 0.0) return null
    if (orderedAvailable.any { !it.quantity.isFinite() || it.quantity < 0.0 || it.stockBatchId.isBlank() || it.storeId.isBlank() } ||
        orderedAvailable.map { it.stockBatchId }.distinct().size != orderedAvailable.size) return null
    if (orderedAvailable.sumOf { it.quantity } + 0.000001 < requestedQuantity) return null
    var remaining = requestedQuantity
    return buildList {
        for (batch in orderedAvailable) {
            if (remaining <= 0.0) break
            val taken = minOf(batch.quantity, remaining)
            if (taken > 0.0) add(batch.copy(quantity = taken))
            remaining -= taken
        }
    }
}

/** A linked return cannot refund more units than its original receipt line. */
fun validLinkedReturnQuantity(original: Double, alreadyReturned: Double, requested: Double): Boolean =
    original.isFinite() && alreadyReturned.isFinite() && requested.isFinite() &&
        original > 0.0 && alreadyReturned >= 0.0 && requested > 0.0 &&
        alreadyReturned + requested <= original + 0.000001

fun GoodsBatchDataModel.isReturnDestination(): Boolean = isActive && status in setOf(
    StockBatchStatusDataModel.Delivered, StockBatchStatusDataModel.OnShelf, StockBatchStatusDataModel.SoldOut
)

/** Piece returns start at one; measured goods retain their original fractional unit. */
fun receiptReturnMinimum(quantity: QuantityDataModel, maximum: Double): Double =
    if (quantity.roundTotal) 1.0 else minOf(quantity.pricedAmount.takeIf { it > 0.0 } ?: 1.0, maximum)

fun validReceiptCartQuantity(quantity: QuantityDataModel, maximum: Double?): Boolean = maximum == null ||
    (maximum.isFinite() && maximum > 0.0 && quantity.total.isFinite() &&
        quantity.total >= receiptReturnMinimum(quantity, maximum) - 0.000001 &&
        quantity.total <= maximum + 0.000001 && (!quantity.roundTotal || quantity.total % 1.0 == 0.0))

/** Availability for UI/validation only. Infinity must never be stored as a quantity or money value. */
val GoodsBatchDataModel.tracksQuantity: Boolean get() = !unlimitedQuantity && kind != StockBatchKindDataModel.UNLIMITED
fun Iterable<GoodsBatchDataModel>.availableStockQuantity(): Double =
    if (any { !it.tracksQuantity }) Double.POSITIVE_INFINITY else sumOf { it.quantity.total.coerceAtLeast(0.0) }

val GoodsBatchDataModel.displayKind: StockBatchKindDataModel get() = if (tracksQuantity) kind else StockBatchKindDataModel.UNLIMITED
