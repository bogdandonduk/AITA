package kz.aita

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlin.test.*

/** Run with the real project serialization plugin/runtime; no fixture serializer. */
class MarketProductSerializationTest {
    @Test fun legacyStockWithoutProfileGetsAnAutomaticDraft() {
        val value = jsonBase.decodeFromString<GoodsItemDataModel>("""{"id":"old","name":[{"language":"en","value":"Tea"}]}""")
        assertNull(value.marketplaceProfile)
        assertEquals("Tea", value.effectiveMarketplaceProfile().name.single().value)
    }
    @Test fun customStockProfileRoundTripsWithoutPublishingFlags() {
        val profile = StockMarketplaceProfile(false, listOf(LocalizedStringDataModel("en","Tea")),
            product = MarketProductDetails(brand="Maker", attributes=listOf(MarketProductAttribute("Material","Paper"))))
        val json = jsonBase.encodeToString(profile)
        assertEquals(profile,jsonBase.decodeFromString<StockMarketplaceProfile>(json))
        assertFalse(json.contains("isPublished")); assertFalse(json.contains("published"))
    }
    @Test fun oldMutationDoesNotReplaceReviewedPublicFacts() {
        val old = """{"listing":{"id":"l","storeId":"s","goodsItemId":"i","title":"Tea"}}"""
        assertFalse(jsonBase.decodeFromString<MarketListingUpdate>(old).replaceProduct)
    }
    @Test fun branchEnvelopeContainsNoOperationalInventoryOrPriceFields() {
        val hint = MarketBranchAvailability("b",listOf(LocalizedStringDataModel("en","Branch")),"Public door",MARKET_AVAILABILITY_CONFIRM,50)
        val json=jsonBase.encodeToString(hint)
        assertEquals(hint,jsonBase.decodeFromString<MarketBranchAvailability>(json))
        listOf("price","quantity","goodsItemId","cost","phone","owner","token").forEach { assertFalse(json.contains(it),it) }
    }
}
