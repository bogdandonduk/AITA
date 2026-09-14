package kz.aita

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlin.test.*

/** Uses the real project serializer under Gradle, not the isolated annotation substitutes. */
class MarketDiscoverySerializationTest {
    @Test fun filtersAndWindowRoundTripWithoutChangingScope() {
        val query = MarketDiscoveryQuery(text = "green tea", city = "Астана", categoryId = "00000000-0000-0000-0000-000000000001", savedOnly = true, sort = MARKET_DISCOVERY_TITLE)
        val request = MarketDiscoveryRequest(query, 80, "a".repeat(64))
        assertEquals(request, jsonBase.decodeFromString<MarketDiscoveryRequest>(jsonBase.encodeToString(request)))
    }
    @Test fun absentTaxonomyStaysAbsentUntilKnownRevisionCanSupplyIt() {
        val result = MarketDiscoveryResult(MarketDiscoveryQuery(), 40, MarketPage(checkedAtMillis = 1), 0, 0, "b".repeat(64))
        val decoded = jsonBase.decodeFromString<MarketDiscoveryResult>(jsonBase.encodeToString(result))
        assertNull(decoded.categories)
        assertNull(decoded.validatedDiscovery(MarketDiscoveryRequest(MarketDiscoveryQuery()), null, "buyer"))
    }
    @Test fun localizedNamesAndAncestryAreNotReplacedByPrivateCategoryDto() {
        val catalogue = MarketCategoryCatalogue("a".repeat(64), listOf(MarketCategory("00000000-0000-0000-0000-000000000001",
            listOf(LocalizedStringDataModel("en", "Food"), LocalizedStringDataModel("ru", "Продукты"), LocalizedStringDataModel("kk", "Азық-түлік")))))
        assertEquals(catalogue, jsonBase.decodeFromString<MarketCategoryCatalogue>(jsonBase.encodeToString(catalogue)))
        listOf("quantity", "conditions", "storeId", "image").forEach { assertFalse(it in jsonBase.encodeToString(catalogue)) }
    }

    @Test fun emptyShopWindowRoundTripsWithItsPublicHeaderAndOwner() {
        val owner = "00000000-0000-0000-0000-000000000099"
        val shop = MarketStorefront("00000000-0000-0000-0000-000000000020", "Shop", "Astana", "Door", published = true, revision = 1)
        val query = MarketDiscoveryQuery(storefrontId = shop.storeId)
        val result = MarketDiscoveryResult(query, 40, MarketPage(checkedAtMillis = 100), 0, 0,
            "a".repeat(64), emptyList(), owner, shop)
        val decoded = jsonBase.decodeFromString<MarketDiscoveryResult>(jsonBase.encodeToString(result))
        assertEquals(result, decoded)
        assertNotNull(decoded.validatedDiscovery(MarketDiscoveryRequest(query), null, owner))
    }
    @Test fun legacyJsonCannotInventAccountBindingOrShopMetadata() {
        val raw = """{"query":{},"limit":40,"page":{"checkedAtMillis":100},"totalOffers":0,"totalShops":0,"categoryVersion":"${"a".repeat(64)}","categories":[]}"""
        val decoded = jsonBase.decodeFromString<MarketDiscoveryResult>(raw)
        assertNull(decoded.accountId); assertNull(decoded.storefront)
        assertNull(decoded.validatedDiscovery(MarketDiscoveryRequest(MarketDiscoveryQuery()), null, "buyer"))
    }
    @Test fun addedHeaderUsesOnlyThePublicStorefrontShape() {
        val shop = MarketStorefront("00000000-0000-0000-0000-000000000020", "Shop", "Astana", "Door", published = true, revision = 1)
        val result = MarketDiscoveryResult(MarketDiscoveryQuery(storefrontId = shop.storeId), 40,
            MarketPage(checkedAtMillis = 100), 0, 0, "a".repeat(64), emptyList(), "buyer", shop)
        val encoded = jsonBase.encodeToString(result)
        listOf("ownerUserIds", "goodsItemId", "batchId", "supplyPrice", "password", "token").forEach {
            assertFalse(it in encoded)
        }
        assertTrue("accountId" in encoded); assertTrue("storefront" in encoded)
    }
}
