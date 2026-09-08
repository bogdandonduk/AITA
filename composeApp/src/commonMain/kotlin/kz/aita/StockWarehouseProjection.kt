package kz.aita

/** Pure inputs: the worker must not read Compose state, perform network calls, or mutate carts. */
internal data class StockWarehouseProjection(
    val search: StockWarehouseSearchResult,
    val defaultSortedItems: List<GoodsItemDataModel>,
    val unfilteredSortedItems: List<GoodsItemDataModel>,
    val sortedItems: List<GoodsItemDataModel>
)

internal fun buildStockWarehouseProjection(
    baseItems: List<GoodsItemDataModel>,
    query: String,
    preferredOrderIds: List<String>,
    sortMode: String,
    ascending: Boolean,
    language: String,
    quantityByItem: Map<String, Double>,
    batchesByItem: Map<String, List<GoodsBatchDataModel>>,
    filterId: String,
    fallbackOrderIds: List<String>
): StockWarehouseProjection {
    val search = if (query.isBlank()) StockWarehouseSearchResult(baseItems) else {
        val candidate = query.transactionBarcodeCandidate() ?: query
        val embedded = candidate.parseEmbeddedWeightBarcodeFormats()
        if (embedded.isNotEmpty()) {
            val matches = baseItems.filter { item ->
                item.matchesScannedBarcode(candidate) || embedded.any { item.matchesEmbeddedWeightBarcode(it) }
            }
            StockWarehouseSearchResult(matches, matches.singleOrNull(), candidate)
        } else {
            val matches = baseItems.search<GoodsItemDataModel>(query)
            StockWarehouseSearchResult(matches.first, if (matches.second) matches.first.firstOrNull() else null, query)
        }
    }
    val preferred = preferredOrderIds.filter { it.isNotBlank() }.withIndex().associate { it.value to it.index }
    val defaultSorted = stockWarehouseDefaultSortedItems(
        search.items, preferred, sortMode.takeUnless { it == "quantity" }, ascending, language
    )
    val fallback = if (fallbackOrderIds.isEmpty()) stockWarehouseFallbackOrderMap(defaultSorted)
        else fallbackOrderIds.withIndex().associate { it.value to it.index }
    val unfiltered = if (sortMode == "quantity" && quantityByItem.any { it.value > 0.0 }) {
        stockWarehouseQuantitySortedItems(search.items, quantityByItem, fallback, ascending)
    } else defaultSorted
    return StockWarehouseProjection(
        search, defaultSorted, unfiltered,
        stockWarehouseItemsForFilter(filterId, unfiltered, batchesByItem)
    )
}
