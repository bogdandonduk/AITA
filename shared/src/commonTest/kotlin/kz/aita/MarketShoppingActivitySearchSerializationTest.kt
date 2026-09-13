package kz.aita

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlin.test.*

/** Real kotlinx.serialization checks; must run with the project's pinned serializer, not an object-token codec. */
class MarketShoppingActivitySearchSerializationTest {
    @Test fun filterAndTwoPartCursorRoundTripWithoutChangingDirection() {
        val value = MarketShoppingActivitySearchRequest(
            MarketShoppingActivityFilter(MARKET_ACTIVITY_REMOVE,MARKET_ACTIVITY_RESULT_REJECTED),
            MarketShoppingActivityCursor(1_789_000_000_000,"abcdefab-cdef-abcd-efab-cdefabcdefab"),newer=true)
        assertEquals(value,jsonBase.decodeFromString<MarketShoppingActivitySearchRequest>(jsonBase.encodeToString(value)))
    }
    @Test fun emptyExactReferenceResultRoundTripsWithoutInventingAnOutcome() {
        val request=MarketShoppingActivitySearchRequest(MarketShoppingActivityFilter(commandId="abcdefab-cdef-abcd-efab-cdefabcdefab"))
        val page=MarketShoppingActivitySearchPage("buyer",request,emptyList(),false,false,100)
        val decoded=jsonBase.decodeFromString<MarketShoppingActivitySearchPage>(jsonBase.encodeToString(page))
        assertEquals(page,decoded);assertTrue(decoded.isValidActivitySearchPage("buyer",request))
    }
    @Test fun legacyGrowingWindowIsNotAFilteredKeysetResponse() {
        val legacy=MarketShoppingActivityPage("buyer",MarketShoppingActivityRequest(),emptyList(),false,100)
        assertFails { jsonBase.decodeFromString<MarketShoppingActivitySearchPage>(jsonBase.encodeToString(legacy)) }
    }
}
