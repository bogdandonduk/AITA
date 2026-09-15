package kz.aita

import kotlinx.serialization.Serializable

/** Only ordering intent crosses the wire; never replay quantities/prices from a dragged UI row. */
@Serializable
data class ShelfOrderRequest(
    val storeId: String,
    val goodsItemId: String,
    val expectedBatchIds: List<String>,
    val expectedActiveBatchId: String? = null,
    val orderedBatchIds: List<String>
)

@Serializable
data class ShelfOrderResult(
    val item: GoodsItemDataModel,
    val batches: List<GoodsBatchDataModel>,
    val changed: Boolean
)

enum class ShelfOrderDecision { Apply, Unchanged, Stale, Invalid }

fun shelfBatchCanBeActive(batch: GoodsBatchDataModel): Boolean = batch.isActive &&
    batch.quantity.total.isFinite() && batch.quantity.total > 0.0 && batch.status !in setOf(
        StockBatchStatusDataModel.Ordered, StockBatchStatusDataModel.Reserved,
        StockBatchStatusDataModel.InTransit, StockBatchStatusDataModel.SoldOut,
        StockBatchStatusDataModel.WrittenOff, StockBatchStatusDataModel.Deleted
    )

/** The same deterministic order is used by the UI and the locked server comparison. */
fun shelfOrderedBatches(item: GoodsItemDataModel, batches: List<GoodsBatchDataModel>): List<GoodsBatchDataModel> =
    batches.filter { it.goodsItemId == item.id && it.isActive && it.status != StockBatchStatusDataModel.Deleted }
        .sortedWith(compareBy<GoodsBatchDataModel> { if (it.id == item.activeShelfBatchId) 0 else 1 }
            .thenBy { it.shelfPriority }.thenBy { it.expirationDateMillis ?: Long.MAX_VALUE }.thenBy { it.id })

fun evaluateShelfOrder(request: ShelfOrderRequest, item: GoodsItemDataModel, batches: List<GoodsBatchDataModel>): ShelfOrderDecision {
    if (request.storeId.isBlank() || request.goodsItemId != item.id || request.storeId != item.storeId || !item.isActive)
        return ShelfOrderDecision.Invalid
    val target = request.orderedBatchIds
    val expected = request.expectedBatchIds
    if (target.isEmpty() || target.size > 10_000 || target.any { it.isBlank() } || target.distinct().size != target.size ||
        expected.size != target.size || expected.distinct().size != expected.size || expected.toSet() != target.toSet())
        return ShelfOrderDecision.Invalid
    val current = shelfOrderedBatches(item, batches)
    if (current.any { it.storeId != request.storeId } || current.map { it.id }.toSet() != target.toSet())
        return ShelfOrderDecision.Stale
    if (!shelfBatchCanBeActive(current.first { it.id == target.first() })) return ShelfOrderDecision.Invalid
    // A retry after a lost acknowledgement is safe and silent, even when its old expectation no longer matches.
    if (current.map { it.id } == target && item.activeShelfBatchId == target.first() &&
        current.withIndex().all { (index, batch) -> batch.shelfPriority == index && batch.shelfPosition == (index + 1).toString() })
        return ShelfOrderDecision.Unchanged
    if (current.map { it.id } != expected || item.activeShelfBatchId != request.expectedActiveBatchId)
        return ShelfOrderDecision.Stale
    return ShelfOrderDecision.Apply
}

fun shelfOrderAcknowledgementMatches(request: ShelfOrderRequest, result: ShelfOrderResult): Boolean =
    request.orderedBatchIds.isNotEmpty() && request.orderedBatchIds.distinct().size == request.orderedBatchIds.size &&
        result.batches.map { it.id }.distinct().size == result.batches.size &&
        result.item.id == request.goodsItemId && result.item.storeId == request.storeId && result.item.isActive &&
        result.item.activeShelfBatchId == request.orderedBatchIds.firstOrNull() &&
        result.batches.size == request.orderedBatchIds.size &&
        result.batches.map { it.id }.toSet() == request.orderedBatchIds.toSet() &&
        result.batches.all { it.goodsItemId == request.goodsItemId && it.storeId == request.storeId } &&
        shelfOrderedBatches(result.item, result.batches).map { it.id } == request.orderedBatchIds &&
        shelfOrderedBatches(result.item, result.batches).withIndex().all { (index, batch) ->
            batch.shelfPriority == index && batch.shelfPosition == (index + 1).toString()
        }
