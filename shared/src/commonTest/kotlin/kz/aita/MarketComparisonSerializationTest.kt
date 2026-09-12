package kz.aita

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlin.test.*

/** Real serialization tests: omitting new default fields is part of the legacy hash protocol. */
class MarketComparisonSerializationTest {
    @Test fun legacyCommandEncodingIsByteForByteUnchanged() {
        val legacy = """{"commandId":"command","expectedRevision":2,"offerId":"offer","units":0,"basis":null}"""
        val decoded = jsonBase.decodeFromString<MarketShoppingCommand>(legacy)
        assertNull(decoded.replaceOfferId); assertNull(decoded.reviewedSubtotalMinor)
        assertEquals(legacy, jsonBase.encodeToString(decoded))
    }
    @Test fun replacementJournalPreservesItsFullIdentityAcrossRestart() {
        val command = MarketShoppingCommand("command", 2, "target", 3, MarketShoppingBasis("04006381333931", "KZT", "piece", 1.0), "source", 123)
        val journal = MarketShoppingJournal("buyer", pending = PendingMarketShoppingCommand("buyer", command))
        val encoded = jsonBase.encodeToString(journal)
        assertTrue("replaceOfferId" in encoded && "reviewedSubtotalMinor" in encoded)
        assertEquals(journal, jsonBase.decodeFromString<MarketShoppingJournal>(encoded))
    }
    @Test fun legacyAddAlsoRetainsExactEncoding() {
        val legacy = """{"commandId":"command","expectedRevision":0,"offerId":"offer","units":1,"basis":{"gtin":null,"currencyCode":"KZT","unitId":"piece","pricedAmount":1.0}}"""
        assertEquals(legacy, jsonBase.encodeToString(jsonBase.decodeFromString<MarketShoppingCommand>(legacy)))
    }
}
