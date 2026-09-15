package kz.aita

import kotlin.test.*

class StockMarketplaceDraftTest {
    @Test fun marketplaceProfileAndBarcodeTypesSurviveDraftRestoration() {
        val profile = StockMarketplaceProfile(false, listOf(LocalizedStringDataModel("ru", "Чай")),
            product = MarketProductDetails(brand = "Example", imageUrls = listOf("https://images.example.com/a.jpg"),
                attributes = listOf(MarketProductAttribute("Pack", "200 g"))))
        val draft = StockAddEditDraft(id = "item", barcodes = listOf("4006381333931"),
            barcodeTypes = listOf(GOODS_ITEM_BARCODE_TYPE_INTERNAL), marketplaceProfile = profile)
        val restored = assertNotNull(draft.toPersistentDraftStateString().toPersistentStockAddEditDraftOrNull())
        assertEquals(profile, restored.marketplaceProfile)
        assertEquals(listOf(GOODS_ITEM_BARCODE_TYPE_INTERNAL), restored.barcodeTypes)
    }
    @Test fun olderDraftWithoutMarketplaceFieldRemainsRestorable() {
        val serialized = StockAddEditDraft(id = "old", barcodes = listOf("4006381333931"))
            .toPersistentDraftStateString().split(STOCK_ADD_EDIT_DRAFT_SEPARATOR).take(18).joinToString(STOCK_ADD_EDIT_DRAFT_SEPARATOR)
        val restored = assertNotNull(serialized.toPersistentStockAddEditDraftOrNull())
        assertEquals("old", restored.id); assertNull(restored.marketplaceProfile)
    }
    @Test fun partialCustomAttributeRemainsInEditingDraft() {
        val draft = StockAddEditDraft(id = "partial", marketplaceProfile = StockMarketplaceProfile(
            product = MarketProductDetails(attributes = listOf(MarketProductAttribute("Size", "")))))
        assertEquals(draft.marketplaceProfile,
            draft.toPersistentDraftStateString().toPersistentStockAddEditDraftOrNull()?.marketplaceProfile)
    }
}
