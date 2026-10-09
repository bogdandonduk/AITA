package kz.aita

import kotlin.test.*
class NondepletingInventoryTest {
    private fun batch(kind:StockBatchKindDataModel)=GoodsBatchDataModel(goodsItemId="item",storeId="store",kind=if(kind==StockBatchKindDataModel.UNLIMITED) StockBatchKindDataModel.UNIVERSAL else kind,unlimitedQuantity=kind==StockBatchKindDataModel.UNLIMITED,
        quantity=QuantityDataModel("0",emptyList(),1.0,1.0,true),supplyPrice=PriceDataModel("10","KZT",""))
    @Test fun universalRemainsMeasuredAndUnlimitedHasNoFictitiousStoredQuantity() {
        assertTrue(batch(StockBatchKindDataModel.UNIVERSAL).tracksQuantity)
        val unlimited=batch(StockBatchKindDataModel.UNLIMITED)
        assertFalse(unlimited.tracksQuantity)
        assertEquals(Double.POSITIVE_INFINITY,listOf(unlimited).availableStockQuantity())
        assertEquals(1.0,unlimited.quantity.total)
        val raw=jsonBase.encodeToString(GoodsBatchDataModel.serializer(),unlimited)
        assertFalse(raw.contains("Infinity"));assertFalse(raw.contains("UNLIMITED"))
        assertTrue(raw.contains("UNIVERSAL"))
    }
    @Test fun lifetimeMetadataHasNoBasicPlanCapacity() {
        val plan=lifetimeStoreSubscriptionPlan("KZ","KZT")
        assertEquals(Int.MAX_VALUE,plan.maxStockItems)
        assertEquals(Int.MAX_VALUE,plan.maxWorkers)
        assertEquals(1000,basicStoreSubscriptionPlan().maxStockItems)
    }
    @Test fun allSupportedLanguagesHaveBarcodeFeedback() {
        for(language in listOf("ru","kk","ky","tg","uz")) {
            val message=inventoryExperienceMessage("out_of_stock","barcode" to "1234","name" to "Test").first {it.language==language}
            assertFalse(message.value.contains("out of stock"));assertTrue(message.value.contains("1234"))
        }
    }
}
