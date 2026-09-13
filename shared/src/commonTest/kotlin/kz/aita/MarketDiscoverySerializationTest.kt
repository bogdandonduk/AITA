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
        assertNull(decoded.validatedDiscovery(MarketDiscoveryRequest(MarketDiscoveryQuery()), null))
    }
    @Test fun localizedNamesAndAncestryAreNotReplacedByPrivateCategoryDto() {
        val catalogue = MarketCategoryCatalogue("a".repeat(64), listOf(MarketCategory("00000000-0000-0000-0000-000000000001",
            listOf(LocalizedStringDataModel("en", "Food"), LocalizedStringDataModel("ru", "Продукты"), LocalizedStringDataModel("kk", "Азық-түлік")))))
        assertEquals(catalogue, jsonBase.decodeFromString<MarketCategoryCatalogue>(jsonBase.encodeToString(catalogue)))
        listOf("quantity", "conditions", "storeId", "image").forEach { assertFalse(it in jsonBase.encodeToString(catalogue)) }
    }
}
