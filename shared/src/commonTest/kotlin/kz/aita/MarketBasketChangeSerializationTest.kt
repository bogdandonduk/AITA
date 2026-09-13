package kz.aita

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlin.test.*

/** Real serializer tests, not merely Kotlin object equality with a fake codec. */
class MarketBasketChangeSerializationTest {
    private fun command(): MarketShoppingCommand {
        val row = BasketTestData.row(1)
        return requireNotNull(BasketTestData.plan(BasketTestData.snapshot(row), listOf(BasketTestData.alternative(row, 10, 1)))
            .reviewedBasketCommand("KZT", MARKET_BASKET_ONE_SHOP, BasketTestData.id(990)))
    }
    @Test fun legacyPendingRemoveEncodingDoesNotAcquireBasketFields() {
        val legacy = """{"commandId":"command","expectedRevision":2,"offerId":"offer","units":0,"basis":null}"""
        val decoded = jsonBase.decodeFromString<MarketShoppingCommand>(legacy)
        assertNull(decoded.basketChange); assertEquals(legacy, jsonBase.encodeToString(decoded))
    }
    @Test fun batchJournalRoundTripPreservesEveryReviewedFactAndIdentity() {
        val command = command()
        val journal = MarketShoppingJournal(BasketTestData.account, pending = PendingMarketShoppingCommand(BasketTestData.account, command))
        val text = jsonBase.encodeToString(journal)
        assertTrue("basketChange" in text && "reviewedItemsSubtotalMinor" in text && "storefrontRevision" in text)
        assertEquals(journal, jsonBase.decodeFromString<MarketShoppingJournal>(text))
    }
    @Test fun legacyReplacementEncodingRemainsUnchanged() {
        val legacy = """{"commandId":"command","expectedRevision":2,"offerId":"target","units":1,"basis":{"gtin":"04006381333931","currencyCode":"KZT","unitId":"piece","pricedAmount":1.0},"replaceOfferId":"source","reviewedSubtotalMinor":10}"""
        assertEquals(legacy, jsonBase.encodeToString(jsonBase.decodeFromString<MarketShoppingCommand>(legacy)))
    }
    @Test fun unknownExtraJsonDoesNotChangeCanonicalRetryPayload() {
        val command = command(); val canonical = jsonBase.encodeToString(command)
        val decoded = jsonBase.decodeFromString<MarketShoppingCommand>(canonical.dropLast(1) + ",\"futureOptional\":true}")
        assertEquals(command, decoded); assertEquals(canonical, jsonBase.encodeToString(decoded))
    }
}
