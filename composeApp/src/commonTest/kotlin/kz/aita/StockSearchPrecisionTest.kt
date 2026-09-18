package kz.aita

import kotlin.test.*

class StockSearchPrecisionTest {
    private fun item(id: String, code: String) = GoodsItemDataModel(id = id, storeId = "s", barcodes = listOf(code),
        name = listOf(LocalizedStringDataModel("en", "Product $id")))
    @Test fun longerQueryCannotMatchShorterProductAliases() {
        val short = item("short", "12345")
        val long = item("long", "1234567890123")
        assertEquals(listOf(long), typedStockSearch(listOf(short, long), "123456").items)
        assertTrue(typedStockSearch(listOf(short), "1234567890123").items.isEmpty())
    }
    @Test fun partialNumericQueryOnlyMatchesCandidatePrefixes() {
        val a = item("a", "123456")
        val b = item("b", "99123456")
        val price = item("price", "789").copy(salePrices = listOf(PriceDataModel("1234", "KZT", "")))
        assertEquals(listOf(a), typedStockSearch(listOf(a, b, price), "123").items)
    }
    @Test fun exactMatchWinsButDuplicateBarcodeDoesNotAutoSelectOneItem() {
        val exact = item("exact", "12345")
        val longer = item("longer", "123456")
        assertEquals(exact, typedStockSearch(listOf(exact, longer), "12345").exactHit)
        val duplicate = exact.copy(id = "duplicate")
        val result = typedStockSearch(listOf(exact, duplicate), "12345")
        assertEquals(2, result.items.size)
        assertNull(result.exactHit)
    }
    @Test fun weightedInputDoesNotExpandDuringTypingButExplicitScannerStillResolvesIt() {
        val stored = item("weighted", "12345")
        val scan = "2112345010006"
        assertTrue(stored.matchesTransactionBarcode(scan))
        assertTrue(typedStockSearch(listOf(stored), scan).items.isEmpty())
        val exact = item("full", scan)
        assertEquals(listOf(exact), typedStockSearch(listOf(stored, exact), scan).items)
    }
    @Test fun namesAndNonNumericCodesStillSearch() {
        val a = item("coffee", "AB-12345")
        assertEquals(listOf(a), typedStockSearch(listOf(a), "coffee").items)
        assertEquals(listOf(a), typedStockSearch(listOf(a), "AB-123").items)
        assertTrue(typedStockSearch(listOf(a), "AB-123456").items.isEmpty())
    }
    @Test fun wideBrowserFocusKeepsMobileKeyboardClosed() {
        assertTrue(warehouseSearchAllowsAutomaticFocus("wasmJs", false))
        assertFalse(warehouseSearchAllowsAutomaticFocus("wasmJs", true))
        assertFalse(warehouseSearchAllowsAutomaticFocus("Android", false))
        assertTrue(warehouseSearchAllowsAutomaticFocus("Desktop JVM", false))
    }
    @Test fun supplySuggestionsNeverConvertCurrenciesOrUseInvalidPrices() {
        val prices = listOf(PriceDataModel("1000", "KZT", ""), PriceDataModel("10", "USD", ""))
        assertEquals(1000.0, supplyQuickFillBase(prices, "KZT"))
        assertEquals(10.0, supplyQuickFillBase(prices, "USD"))
        assertNull(supplyQuickFillBase(prices, "EUR"))
        listOf("0", "-1", "NaN", "Infinity", "").forEach { assertNull(supplyQuickFillBase(listOf(PriceDataModel(it, "KZT", "")), "KZT")) }
    }
}
