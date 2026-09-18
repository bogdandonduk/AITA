package kz.aita

import kotlinx.serialization.Serializable

/** Special groups still hold separate records and units for each goods item. */
@Serializable
enum class StockBatchKindDataModel { NORMAL, RETURNED, UNIVERSAL }

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
