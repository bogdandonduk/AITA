package kz.aita

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlin.test.*

/** Requires the real serialization plugin/runtime. */
class MarketShoppingCancellationSerializationTest {
    private val command = MarketShoppingCommand("command", 0, "offer", 0)
    @Test fun legacyJournalHasNoCancellationByDefault() {
        val input = """{"accountId":"buyer","snapshot":null,"pending":{"accountId":"buyer","command":{"commandId":"command","expectedRevision":0,"offerId":"offer","units":0,"basis":null}},"localRevision":1}"""
        val journal = jsonBase.decodeFromString<MarketShoppingJournal>(input)
        assertNull(journal.cancellingCommandId); assertEquals(input, jsonBase.encodeToString(journal))
    }
    @Test fun cancellationRoundTripKeepsPayloadBytesUnchanged() {
        val original = jsonBase.encodeToString(command)
        val initial = MarketShoppingJournal("buyer").prepare(PendingMarketShoppingCommand("buyer", command))
        val journal = initial.requestCancellation(initial.pending!!)
        val decoded = jsonBase.decodeFromString<MarketShoppingJournal>(jsonBase.encodeToString(journal))
        assertEquals(journal, decoded); assertEquals(original, jsonBase.encodeToString(decoded.pending!!.command))
        assertFalse("cancellingCommandId" in original)
    }
}
