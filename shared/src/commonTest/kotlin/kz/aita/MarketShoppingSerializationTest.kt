package kz.aita

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlin.test.*

/** Uses the real project JSON/plugin when run through Gradle, not just pure journal policy tests. */
class MarketShoppingSerializationTest {
    @Test fun pendingCommandAndUnitIdentityRoundTripWithoutReinterpretation() {
        val command = PendingMarketShoppingCommand("buyer", MarketShoppingCommand("command", 3, "offer", 7,
            MarketShoppingBasis(null, "KZT", "kg", 0.5)))
        val journal = MarketShoppingJournal("buyer").prepare(command)
        assertEquals(journal, jsonBase.decodeFromString<MarketShoppingJournal>(jsonBase.encodeToString(journal)))
    }
    @Test fun outcomeRoundTripPreservesRecordedRejection() {
        val result = MarketShoppingOutcome("command", false, replayed = true, errorKey = "market.shopping_changed",
            snapshot = MarketShoppingSnapshot("buyer", 4))
        assertEquals(result, jsonBase.decodeFromString<MarketShoppingOutcome>(jsonBase.encodeToString(result)))
    }
    @Test fun journalWithoutNewLocalSequenceUsesItsSafeDefault() {
        assertEquals(0L, jsonBase.decodeFromString<MarketShoppingJournal>("""{"accountId":"buyer"}""").localRevision)
    }
}
