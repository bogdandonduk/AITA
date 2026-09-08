package kz.aita

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StockWarehouseProjectionTest {
    private fun item(id: String, name: String, price: String? = null) = GoodsItemDataModel(
        id = id, storeId = "store", name = listOf(LocalizedStringDataModel("en", name)),
        salePrices = price?.let { listOf(PriceDataModel(it, "KZT", "")) }.orEmpty()
    )
    private fun project(
        items: List<GoodsItemDataModel>, sort: String = "name", ascending: Boolean = true,
        preferred: List<String> = emptyList(), quantities: Map<String, Double> = emptyMap(),
        fallback: List<String> = emptyList(), filter: String = STOCK_WAREHOUSE_FILTER_TOTAL,
        batches: Map<String, List<GoodsBatchDataModel>> = emptyMap()
    ) = buildStockWarehouseProjection(items, "", preferred, sort, ascending, "en", quantities, batches, filter, fallback)

    @Test fun defaultAllCatalogueKeepsItemsWithoutQuickFlags() {
        val stock = listOf(item("b", "Banana"), item("a", "Apple"))
        assertTrue(stock.none { it.isQuickItem })
        assertEquals(listOf("a", "b"), project(stock).sortedItems.map { it.id })
    }

    @Test fun nameSortingIsCaseInsensitiveAndDoesNotMutateInput() {
        val stock = listOf(item("b", "banana"), item("a", "Apple"))
        assertEquals(listOf("a", "b"), project(stock).sortedItems.map { it.id })
        assertEquals(listOf("b", "a"), stock.map { it.id })
    }

    @Test fun blankDraftIdsDoNotShareTheWrongCachedNameKey() {
        val stock = listOf(item("", "Banana"), item("", "Apple"))
        assertEquals(listOf("Apple", "Banana"), project(stock).sortedItems.map { it.name.first().value })
    }

    @Test fun preferredOrderWinsOverAlphabeticalOrderAndDirection() {
        val stock = listOf(item("b", "Banana"), item("a", "Apple"), item("c", "Cherry"))
        assertEquals(listOf("c", "a", "b"), project(stock, ascending = false, preferred = listOf("c")).sortedItems.map { it.id })
    }

    @Test fun priceSortKeepsUnpricedItemsLast() {
        val stock = listOf(item("none", "A"), item("high", "B", "25"), item("low", "C", "2"))
        assertEquals(listOf("low", "high", "none"), project(stock, sort = "price").sortedItems.map { it.id })
        assertEquals(listOf("high", "low", "none"), project(stock, sort = "price", ascending = false).sortedItems.map { it.id })
    }

    @Test fun equalQuantitiesPreserveLastNonQuantityOrder() {
        val stock = listOf(item("b", "B"), item("a", "A"), item("c", "C"))
        val sorted = project(stock, sort = "quantity", quantities = mapOf("a" to 2.0, "b" to 2.0), fallback = listOf("b", "a", "c"))
        assertEquals(listOf("c", "b", "a"), sorted.sortedItems.map { it.id })
    }

    @Test fun absentBatchDataDoesNotFilterTheAllCatalogueAway() {
        assertEquals(2, project(listOf(item("a", "A"), item("b", "B"))).sortedItems.size)
    }

    @Test fun explicitBarcodeFilterDoesNotAlterUnfilteredTotals() {
        val stock = listOf(item("a", "A"), item("b", "B").copy(barcodes = listOf("12345")))
        val result = project(stock, filter = STOCK_WAREHOUSE_FILTER_NO_BARCODE)
        assertEquals(listOf("a"), result.sortedItems.map { it.id })
        assertEquals(2, result.unfilteredSortedItems.size)
    }

    @Test fun suggestionComputationDoesNotHideNonQuickCatalogueItems() {
        val result = buildTransactionSelectionSmartSets("store", 0, emptyList(), listOf(item("a", "A")), emptyList(), "en")
        assertEquals(1, result.totalStockCount)
        assertEquals(0, result.quickItemCount)
    }

    @Test fun suggestionsExcludeUnavailableBatchStatuses() {
        val quantity = QuantityDataModel("piece", listOf(LocalizedStringDataModel("en", "piece")), 4.0, 1.0, true)
        val batch = GoodsBatchDataModel(goodsItemId = "a", storeId = "store", quantity = quantity, supplyPrice = PriceDataModel("2", "KZT", ""))
        val result = buildTransactionSelectionSmartSets("store", 0, emptyList(), listOf(item("a", "A")), listOf(batch.copy(status = StockBatchStatusDataModel.InTransit)), "en")
        assertTrue(result.inStockIds.isEmpty())
        assertTrue(result.freshIds.isEmpty())
    }

    @Test fun historyRecommendationsDoNotUseOtherStoresTransactions() {
        val tx = TransactionDataModel("tx", 0L, "purchase", "other-store", listOf(GoodsItemInTransactionDataModel("123", 1.0, 2.0, goodsItemId = "a")), 2.0, 0.0, 0, timeMillis = 1)
        val result = buildTransactionSelectionSmartSets("store", 0, listOf(tx), listOf(item("a", "A")), emptyList(), "en")
        assertTrue(result.popularIds.isEmpty())
        assertTrue(result.recentIds.isEmpty())
    }
}
