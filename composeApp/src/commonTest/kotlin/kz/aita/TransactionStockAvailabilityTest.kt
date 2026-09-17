package kz.aita

import kotlin.test.*

class TransactionStockAvailabilityTest {
    private val item = GoodsItemDataModel(id = "item", storeId = "store",
        name = listOf(LocalizedStringDataModel("en", "Item")))
    private val quantity = QuantityDataModel("piece", listOf(LocalizedStringDataModel("en", "piece")), 4.0, 1.0, true)
    private val batch = GoodsBatchDataModel(goodsItemId = "item", storeId = "store", quantity = quantity,
        supplyPrice = PriceDataModel("2", "KZT", ""))
    private fun collections() = LiveStockCollections("owner", listOf(item), listOf(batch), emptyList(),
        mapOf(item.id to item), emptyMap(), mapOf(item.id to listOf(batch)), emptyMap(), emptyMap())

    @Test fun reopenedSelectionAcceptsEqualRecreatedStockAndBatches() {
        val worker = collections()
        val newlyCollectedStock = listOf(item.copy())
        val newlyCollectedBatches = listOf(batch.copy())
        assertNotSame(worker.stock, newlyCollectedStock)
        assertNotSame(worker.batches, newlyCollectedBatches)
        assertTrue(worker.matchesSources("owner", newlyCollectedStock, newlyCollectedBatches))
    }
    @Test fun genuinelyChangedStockOrBatchesRemainDisabledUntilProjectionCatchesUp() {
        val worker = collections()
        assertFalse(worker.matchesSources("owner", listOf(item.copy(id = "changed")), listOf(batch)))
        assertFalse(worker.matchesSources("owner", listOf(item), listOf(batch.copy(quantity = quantity.copy(total = 1.0)))))
        assertFalse(worker.matchesSources("another-owner", listOf(item), listOf(batch)))
        assertFalse(worker.matchesSources(null, listOf(item), listOf(batch)))
    }
}
