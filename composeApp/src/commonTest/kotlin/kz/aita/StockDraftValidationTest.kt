package kz.aita

import kotlin.test.*

class StockDraftValidationTest {
    private val config get() = globalAppConfigurationState.payloadValue
    private fun valid() = StockAddEditDraft(name = listOf(LocalizedStringDataModel("ru", "Игрушка")),
        barcodes = listOf("2618000001248"), measurementUnitId = config.goodsItemsQuantityUnits.first().id,
        salePrices = listOf(PriceDataModel("10,50", "KZT", "")), supplyPrices = listOf(PriceDataModel("0", "KZT", "")))
    @Test fun decimalCommaAndZeroPurchasePriceAreValid() { assertTrue(valid().stockDraftErrors(config).isEmpty()) }
    @Test fun errorsIdentifyEveryRequiredField() {
        val errors = valid().copy(name = emptyList(), barcodes = listOf(" "), measurementUnitId = "missing",
            salePrices = listOf(PriceDataModel("NaN", "KZT", "")), supplyPrices = emptyList()).stockDraftErrors(config)
        assertEquals(listOf("name", "barcode", "unit", "sale_price", "supply_price"), errors)
    }
    @Test fun negativeAndInfinitePricesNeverPass() {
        listOf("-1", "Infinity", "").forEach { price ->
            assertContains(valid().copy(salePrices = listOf(PriceDataModel(price, "KZT", ""))).stockDraftErrors(config), "sale_price")
        }
    }
}
