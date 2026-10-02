package kz.aita

import kotlin.test.*

class ReturnCheckoutTest {
    @Test fun historicalAmountAndCurrencySurviveDifferentDestinationPrices() {
        val receipt = CartReturnBatchSelectionDataModel("item", pricePerUnit = 725.50, currencyCode = "KZT",
            originalTransactionId = "receipt", originalTransactionLineIndex = 0)
        val destination = PriceDataModel("99", "USD", "")
        val refund = destination.withReturnSelectionPrice(receipt)
        assertEquals(725.5, refund.price.toDouble())
        assertEquals("KZT", refund.currency)
        assertEquals(destination, destination.withReturnSelectionPrice(null))
    }
    @Test fun batchDraftPreservesSpecialKindAndOlderDraftDefaultsToNormal() {
        val draft = GoodsBatchDraft(goodsItemId = "item", storeId = "store", quantityUnitId = "piece",
            supplyPrice = PriceDataModel("1", "KZT", ""), kind = StockBatchKindDataModel.UNIVERSAL)
        val raw = draft.toNavigationStateString()
        assertEquals(StockBatchKindDataModel.UNIVERSAL, goodsBatchDraftFromNavigationStateString(raw)?.kind)
        assertEquals(StockBatchKindDataModel.NORMAL, goodsBatchDraftFromNavigationStateString(raw.split(GOODS_BATCH_DRAFT_SEPARATOR).take(23).joinToString(GOODS_BATCH_DRAFT_SEPARATOR))?.kind)
    }
    @Test fun persistedCheckoutCanRestoreBatchStep() {
        assertEquals(NavigationScreenModel.Transaction.ReturnBatches,
            persistentTransactionRouteToScreen(NavigationScreenModel.Transaction.ReturnBatches.route))
    }
}
