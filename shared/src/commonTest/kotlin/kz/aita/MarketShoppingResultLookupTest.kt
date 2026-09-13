package kz.aita

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlin.test.*

class MarketShoppingResultLookupTest {
    private class Owner : MarketAccountScope {
        override val accountId = BasketTestData.account
        override val generation = 1L
        var current = true
        override fun isCurrent() = current
    }
    private val a = BasketTestData.row(1)
    private val plan = BasketTestData.plan(BasketTestData.snapshot(a), listOf(BasketTestData.alternative(a,10,1)))
    private val command = requireNotNull(plan.reviewedBasketCommand("KZT", MARKET_BASKET_ONE_SHOP, BasketTestData.id(997)))
    private val acceptedSnapshot = plan.snapshot.copy(revision = 8, lines = plan.currencies.single().oneShop.choices.map { it.quote })
    private fun outcome() = MarketShoppingOutcome(command.commandId, true, 8, true, snapshot = acceptedSnapshot)
    private fun <T> ok(value: T) = ResponseDataModel(null, value, false, 200)
    private class Memory {
        val values = mutableMapOf<String, String?>()
        var failWrites = false
        var mutationSends = 0
        suspend fun get(key: String) = values[key]
        suspend fun put(key: String, value: String?) { check(!failWrites); values[key] = value }
        fun journal() = jsonBase.decodeFromString<MarketShoppingJournal>(values.values.single()!!)
    }
    private fun pendingMemory() = Memory().apply {
        values["buyer-shopping-journal-v1:${BasketTestData.account}"] = jsonBase.encodeToString(
            MarketShoppingJournal(BasketTestData.account, plan.snapshot, PendingMarketShoppingCommand(BasketTestData.account, command), 1))
    }
    private fun store(memory: Memory, read: suspend (MarketAccountScope, MarketShoppingCommand) -> ResponseDataModel<MarketShoppingCommandLookup>) =
        MarketShoppingDeliveryStore(memory::get, memory::put, { ok(plan.snapshot) },
            { _, _ -> memory.mutationSends++; error("Check result must NEVER invoke sendRemote") }, lookupRemote = read)

    @Test fun recordedWholeBasketOutcomeRetiresPendingWithoutSending() = runTest {
        val memory = pendingMemory(); var checks = 0
        val result = store(memory) { _, request -> checks++; assertEquals(command, request); ok(MarketShoppingCommandLookup(command.commandId, 500, outcome())) }.checkResult(Owner())
        assertEquals(1, checks); assertEquals(0, memory.mutationSends); assertTrue(result.acknowledged && result.accepted)
        assertNull(memory.journal().pending); assertEquals(acceptedSnapshot, memory.journal().snapshot)
    }
    @Test fun absentRecordDoesNotEraseExpiredOrInFlightCommand() = runTest {
        val memory = pendingMemory(); val before = memory.journal()
        val result = store(memory) { _, _ -> ok(MarketShoppingCommandLookup(command.commandId, Long.MAX_VALUE)) }.checkResult(Owner())
        assertFalse(result.acknowledged); assertNotNull(result.notice); assertEquals(before, memory.journal()); assertEquals(0, memory.mutationSends)
    }
    @Test fun checkDoesNotSubmitAnExpiredButNeverRecordedBasket() = runTest {
        val memory = pendingMemory()
        store(memory) { _, _ -> ok(MarketShoppingCommandLookup(command.commandId, MARKET_BASKET_REVIEW_MAX_AGE_MILLIS * 10)) }.checkResult(Owner())
        assertEquals(command, memory.journal().pending?.command)
    }
    @Test fun recordedRejectionIsNotConvertedToSuccessAndRetiresOnlyMatchingPending() = runTest {
        val memory = pendingMemory()
        val rejected = MarketShoppingOutcome(command.commandId, false, errorKey = "market.basket_apply_expired", snapshot = plan.snapshot)
        val result = store(memory) { _, _ -> ok(MarketShoppingCommandLookup(command.commandId, 500, rejected)) }.checkResult(Owner())
        assertTrue(result.acknowledged); assertFalse(result.accepted); assertNotNull(result.error); assertNull(memory.journal().pending); assertEquals(0, memory.mutationSends)
    }
    @Test fun laterListRevisionIsNotOverwrittenByTheRecoveredReceipt() = runTest {
        val memory = pendingMemory(); val newer = acceptedSnapshot.copy(revision = 10, lines = emptyList())
        val current = memory.journal().withSnapshot(newer)
        memory.values[memory.values.keys.single()] = jsonBase.encodeToString(current)
        store(memory) { _, _ -> ok(MarketShoppingCommandLookup(command.commandId, 500, outcome())) }.checkResult(Owner())
        assertEquals(newer, memory.journal().snapshot); assertNull(memory.journal().pending)
    }
    @Test fun partialBasketAndWrongCommandCannotRetirePending() = runTest {
        for (lookup in listOf(MarketShoppingCommandLookup("wrong", 500, outcome()),
            MarketShoppingCommandLookup(command.commandId, 500, outcome().copy(snapshot = acceptedSnapshot.copy(lines = emptyList()))),
            MarketShoppingCommandLookup(command.commandId, 0, outcome()),
            MarketShoppingCommandLookup(command.commandId, 500, outcome(), protocolVersion = 99))) {
            val memory = pendingMemory()
            assertFalse(store(memory) { _, _ -> ok(lookup) }.checkResult(Owner()).acknowledged)
            assertNotNull(memory.journal().pending)
        }
    }
    @Test fun oldBackendAndTransportFailureLeaveRecoveryIntact() = runTest {
        val memory = pendingMemory()
        val missing = store(memory) { _, _ -> ResponseDataModel(null, null, true, 404) }.checkResult(Owner())
        assertNotNull(missing.error); assertNotNull(memory.journal().pending)
        assertFalse(store(memory) { _, _ -> error("offline") }.checkResult(Owner()).acknowledged)
        assertNotNull(memory.journal().pending)
    }
    @Test fun acknowledgementStorageFailureDoesNotLosePendingIdentity() = runTest {
        val memory = pendingMemory().apply { failWrites = true }
        val result = store(memory) { _, _ -> ok(MarketShoppingCommandLookup(command.commandId, 500, outcome())) }.checkResult(Owner())
        assertFalse(result.acknowledged); assertNotNull(result.error); assertNotNull(memory.journal().pending)
    }
    @Test fun noPendingCommandMakesNoLookupRequest() = runTest {
        var reads = 0
        val memory = Memory()
        assertFalse(store(memory) { _, _ -> reads++; error("not expected") }.checkResult(Owner()).acknowledged)
        assertEquals(0, reads)
    }
    @Test fun obsoleteOwnerCannotStartOrFinishResultRead() = runTest {
        val memory = pendingMemory(); var reads = 0
        val owner = Owner().apply { current = false }
        store(memory) { _, _ -> reads++; error("not expected") }.checkResult(owner)
        assertEquals(0, reads)
        owner.current = true
        store(memory) { _, _ -> owner.current = false; ok(MarketShoppingCommandLookup(command.commandId, 500, outcome())) }.checkResult(owner)
        assertNotNull(memory.journal().pending)
    }
    @Test fun accountChangeDuringJournalReadPreventsLookupAndPublishing() = runTest {
        val memory = pendingMemory(); val owner = Owner(); var remoteReads = 0
        val delivery = MarketShoppingDeliveryStore({ key -> owner.current = false; memory.get(key) }, memory::put,
            { ok(plan.snapshot) }, { _, _ -> error("no mutation allowed") },
            lookupRemote = { _, _ -> remoteReads++; ok(MarketShoppingCommandLookup(command.commandId, 500, outcome())) })
        val result = delivery.checkResult(owner)
        assertEquals(0, remoteReads); assertNull(result.snapshot); assertFalse(result.acknowledged)
        assertNotNull(memory.journal().pending)
    }
    @Test fun cancellationCannotClearTheOriginalJournal() = runTest {
        val memory = pendingMemory()
        assertFailsWith<CancellationException> { store(memory) { _, _ -> throw CancellationException() }.checkResult(Owner()) }
        assertNotNull(memory.journal().pending)
    }
    @Test fun absentRecordReadsNewestJournalRatherThanRestoringAnOlderSnapshot() = runTest {
        val memory = pendingMemory(); val start = CompletableDeferred<Unit>(); val finish = CompletableDeferred<Unit>()
        val owner = Owner()
        val delivery = MarketShoppingDeliveryStore(memory::get, memory::put, { ok(acceptedSnapshot.copy(revision = 9)) },
            { _, _ -> error("write not allowed") }, lookupRemote = { _, _ -> start.complete(Unit); finish.await(); ok(MarketShoppingCommandLookup(command.commandId, 500)) })
        val result = async { delivery.checkResult(owner) }; start.await()
        delivery.refresh(owner); finish.complete(Unit)
        assertEquals(9, result.await().snapshot?.revision); assertNotNull(memory.journal().pending)
    }
    @Test fun readOnlyLookupStillChecksTheExactOriginalAccount() = runTest {
        val memory = pendingMemory()
        val foreign = outcome().copy(snapshot = acceptedSnapshot.copy(userId = "other"))
        assertFalse(store(memory) { _, _ -> ok(MarketShoppingCommandLookup(command.commandId, 500, foreign)) }.checkResult(Owner()).acknowledged)
        assertNotNull(memory.journal().pending)
    }
}
