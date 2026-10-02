package kz.aita

import kotlin.test.*

class ParentStockSearchTest {
    private fun item(code: String, unit: String = "0") = GoodsItemDataModel(id = code, storeId = "parent",
        barcodes = listOf(code), measurementUnitId = unit, name = listOf(LocalizedStringDataModel("ru", "Игрушка")))
    @Test fun photographedToyLabelsAreFullIdentifiersNotWeights() {
        listOf("2900060749858", "2900060749919", "2110060749929", "721688562788", "2637000008927",
            "2110050141987", "2647000008391", "2681000002430", "2900060749889", "2658000006249",
            "6947892686858", "7780021750520", "2634000005819", "2610000008746", "2618000001248", "2110027458970").forEach { code ->
            assertTrue(item(code).matchesParentCatalogueQuery(code), code)
            assertTrue(item(code).matchesParentCatalogueQuery(code.chunked(3).joinToString(" ")), code)
            assertFalse(item(code.take(7)).matchesParentCatalogueQuery(code), code)
        }
    }
    @Test fun completeCodeWinsOverLegacyPrefixAndPricesNeverMatch() {
        val full = item("2618000001248")
        val legacy = item("2618000")
        assertTrue(legacy.isLegacyParentBarcodeCandidate("2618000001248"))
        assertEquals(listOf(full), listOf(legacy, full).searchParentCatalogue("2618000001248"))
        assertEquals(listOf(legacy), listOf(legacy).searchParentCatalogue("2618000001248"))
        assertFalse(item("12345").matchesParentCatalogueQuery("1234567890123"))
        assertFalse(item("other").copy(salePrices = listOf(PriceDataModel("2618000001248", "KZT", ""))).matchesParentCatalogueQuery("2618000001248"))
    }
    @Test fun weightedItemStillMatchesItsCompleteValidatedScaleLabel() {
        assertTrue(item("12345", "1").matchesParentCatalogueQuery("2112345010007"))
        assertFalse(item("12345", "0").matchesParentCatalogueQuery("2112345010007"))
        assertFalse(item("12345", "1").matchesParentCatalogueQuery("2112345010006"))
    }
    @Test fun explicitLegacySelectionRestoresScannedFullCodeWithoutMutatingSource() {
        val source = item("2618000")
        val selected = source.withConfirmedParentBarcode("2618000001248")
        assertContains(selected.allBarcodeValues(), "2618000001248")
        assertEquals(listOf("2618000"), source.allBarcodeValues())
        assertEquals(source, source.withConfirmedParentBarcode("not a barcode"))
    }
    @Test fun fullCodeAlreadyStoredAlongsideOldShortModelRemainsSearchable() {
        val short = GoodsItemBarcodeDataModel("2618000", GOODS_ITEM_BARCODE_TYPE_INTERNAL, "parent")
        val source = item("2618000001248").copy(barcodeModels = listOf(short))
        assertContains(source.allBarcodeValues(), "2618000001248")
        assertTrue(source.matchesParentCatalogueQuery("2618000001248"))
        assertEquals(listOf(short), listOf(short).restoreFullLegacyBarcodeModels(listOf("2618000001248"), true))
    }
    @Test fun localizedTitlesAndForwardPrefixesRemainSearchable() {
        assertTrue(item("AB-123").matchesParentCatalogueQuery("ab 12"))
        assertTrue(item("2900060749858").matchesParentCatalogueQuery("290006"))
        assertTrue(item("2900060749858").matchesParentCatalogueQuery("игруш"))
    }
}
