package kz.aita

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlin.test.*

class MarketShoppingActivitySerializationTest {
    @Test fun summaryAndFullPublicHistoryRoundTrip() {
        val line = BasketTestData.row(1).line
        val entry = MarketShoppingActivityEntry(BasketTestData.id(900), 100, 7, true, 8,
            kind = MARKET_ACTIVITY_QUANTITY, requestedUnits = line.units, changedLines = 1,
            details = MarketShoppingActivityDetails(listOf(MarketShoppingActivityLine(after = line))), previewTitle = line.title)
        assertEquals(entry, jsonBase.decodeFromString<MarketShoppingActivityEntry>(jsonBase.encodeToString(entry)))
        val summary = entry.copy(details = null)
        assertTrue(summary.detailsRecorded)
        val page = MarketShoppingActivityPage(BasketTestData.account, MarketShoppingActivityRequest(), listOf(summary), false, 200)
        assertEquals(page, jsonBase.decodeFromString<MarketShoppingActivityPage>(jsonBase.encodeToString(page)))
        assertFalse(jsonBase.encodeToString(page).contains("request_hash"))
        assertFalse(jsonBase.encodeToString(page).contains("session_id"))
    }
    @Test fun missingLookupResultRemainsMissingNotAnAcceptedDefault() {
        val value = MarketShoppingCommandLookup(BasketTestData.id(900), 100)
        assertNull(jsonBase.decodeFromString<MarketShoppingCommandLookup>(jsonBase.encodeToString(value)).outcome)
    }
    @Test fun recoveryDoesNotAddFieldsToLegacyCommandIdentity() {
        val command = MarketShoppingCommand("legacy-command", 7, "legacy-offer", 0)
        val encoded = jsonBase.encodeToString(command)
        assertFalse(encoded.contains("activity")); assertFalse(encoded.contains("basketChange"))
        assertEquals(command, jsonBase.decodeFromString<MarketShoppingCommand>(encoded))
    }
}
