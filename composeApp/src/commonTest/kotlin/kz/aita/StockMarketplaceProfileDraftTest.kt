package kz.aita

import kotlin.test.*

class StockMarketplaceProfileDraftTest {
    @Test fun typingWordsAndClearingTheActiveLanguagePreservesTheExactInput() {
        var values = listOf(LocalizedStringDataModel("en", "Generic honey"))
        for (typed in listOf("", "Internet", "Internet ", "Internet exclusive ", "Internet exclusive honey")) {
            values = values.withMarketplaceProfileDraftText("en", typed)
            assertEquals(typed, values.marketplaceProfileDraftText("en"))
        }
        assertEquals("Internet exclusive honey", values.single().value)
    }

    @Test fun editingOneLanguagePreservesOthersAndBlankDoesNotJumpToFallback() {
        val generic = listOf(LocalizedStringDataModel("main", "Generic honey"), LocalizedStringDataModel("ru", "Общий мёд"))
        assertEquals("Generic honey", generic.marketplaceProfileDraftText("en"))
        val edited = generic.withMarketplaceProfileDraftText("en", "Internet honey ")
        assertEquals(generic, edited.filter { it.language != "en" })
        assertEquals("Internet honey ", edited.marketplaceProfileDraftText("en"))
        val cleared = edited.withMarketplaceProfileDraftText("en", "")
        assertEquals("", cleared.marketplaceProfileDraftText("en"))
        assertEquals(generic, cleared.filter { it.language != "en" })
    }
}
