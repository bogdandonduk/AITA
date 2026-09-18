package kz.aita

/** Pure inputs: the worker must not read Compose state, perform network calls, or mutate carts. */
internal data class StockWarehouseProjection(
    val search: StockWarehouseSearchResult,
    val defaultSortedItems: List<GoodsItemDataModel>,
    val unfilteredSortedItems: List<GoodsItemDataModel>,
    val sortedItems: List<GoodsItemDataModel>,
    val metrics: StockWarehouseMetricsData
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
    val search = typedStockSearch(baseItems, query)
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
        stockWarehouseItemsForFilter(filterId, unfiltered, batchesByItem),
        stockWarehouseMetricsForUi(unfiltered, batchesByItem)
    )
}

/** Typed search is literal. Weighted-barcode decoding belongs to the explicit scanner path. */
internal fun typedStockSearch(items: List<GoodsItemDataModel>, query: String): StockWarehouseSearchResult {
    val text = query.trim()
    if (text.isEmpty()) return StockWarehouseSearchResult(items)
    fun GoodsItemDataModel.tokens() = (barcodes + barcodeModels.map { it.value } + allBarcodeValues())
        .map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    val exact = mutableListOf<GoodsItemDataModel>()
    val matching = mutableListOf<GoodsItemDataModel>()
    // A numeric search must never match a price or a shorter weighted-product alias.
    val numeric = text.all(Char::isDigit)
    items.forEach { item ->
        val codes = item.tokens()
        if (codes.any { it.equals(text, ignoreCase = true) }) exact += item
        else if (codes.any { it.startsWith(text, ignoreCase = true) } || (!numeric &&
            (item.containsSearchOperands - codes.toSet()).any { it.contains(text, ignoreCase = true) })) matching += item
    }
    if (exact.isNotEmpty()) return StockWarehouseSearchResult(exact, exact.singleOrNull(), text)
    return StockWarehouseSearchResult(matching, null, text)
}
