package kz.aita

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlin.test.*

class MarketShoppingCancellationTest {
    private class Owner : MarketAccountScope {
        override val accountId = "buyer"
        override val generation = 1L
        var active = true
        override fun isCurrent() = active
    }
    private class Memory {
        var value: String? = null
        var failWrites = false
        suspend fun get(@Suppress("UNUSED_PARAMETER") key: String) = value
        suspend fun put(@Suppress("UNUSED_PARAMETER") key: String, text: String?) {
            if (failWrites) error("disk unavailable")
            value = text
        }
        fun journal() = value?.let { jsonBase.decodeFromString<MarketShoppingJournal>(it) }
    }
    private val command = MarketShoppingCommand("command", 0L, "offer", 1, MarketShoppingBasis(null,"KZT","piece",1.0))
    private fun empty(revision: Long = 0) = MarketShoppingSnapshot("buyer", revision, checkedAtMillis = 1)
    private fun cancelled() = MarketShoppingOutcome(command.commandId, false, errorKey = "market.shopping_cancelled", snapshot = empty())
    private fun <T> ok(value: T) = ResponseDataModel(null, value, false, 200)
    private fun failed() = ResponseDataModel<MarketShoppingOutcome>(null, null, true, 503)
    private fun journal() = MarketShoppingJournal("buyer", empty()).prepare(PendingMarketShoppingCommand("buyer", command))

    @Test fun cancellationIntentIsWrittenBeforeTheCancelTransportRuns() = runTest {
        val memory = Memory(); val owner = Owner()
        val store = MarketShoppingDeliveryStore(memory::get, memory::put, { ok(empty()) }, { _, _ -> failed() },
            cancelRemote = { _, original ->
                assertEquals(command, original)
                assertEquals(command.commandId, memory.journal()?.cancellingCommandId)
                assertEquals(command, memory.journal()?.pending?.command)
                ok(cancelled())
            })
        store.change(owner, command)
        assertTrue(store.cancelPending(owner, command).acknowledged)
        assertNull(memory.journal()?.pending); assertNull(memory.journal()?.cancellingCommandId)
    }
    @Test fun cancellationLossSurvivesRestartAndRetryNeverResubmitsTheOriginalEdit() = runTest {
        val memory = Memory(); val owner = Owner(); var edits = 0; var cancels = 0
        fun store(success: Boolean) = MarketShoppingDeliveryStore({ key -> memory.get(key) }, { key,value -> memory.put(key,value) }, { ok(empty()) },
            { _, _ -> edits++; failed() }, cancelRemote = { _, original ->
                assertEquals(command, original); cancels++; if (success) ok(cancelled()) else failed()
            })
        val first = store(false); first.change(owner, command)
        assertNotNull(first.cancelPending(owner, command).error)
        assertEquals(command.commandId, memory.journal()?.cancellingCommandId)
        val restarted = store(true)
        assertEquals(command.commandId, restarted.cached(owner).cancellingCommandId)
        assertTrue(restarted.retry(owner).acknowledged)
        assertEquals(1, edits); assertEquals(2, cancels)
    }
    @Test fun normalChangeCannotBypassAPreviouslyRequestedCancellation() = runTest {
        val memory = Memory(); memory.value = jsonBase.encodeToString(journal().requestCancellation(journal().pending!!))
        var edits = 0
        val store = MarketShoppingDeliveryStore(memory::get, memory::put, { ok(empty()) }, { _, _ -> edits++; failed() })
        assertNotNull(store.change(Owner(), command).error)
        assertNotNull(store.change(Owner(), command.copy(commandId = "new")).error)
        assertEquals(0, edits); assertEquals(command.commandId, memory.journal()?.cancellingCommandId)
    }
    @Test fun staleConfirmationCannotCancelAnotherPendingPayload() = runTest {
        val memory = Memory(); val newer = command.copy(commandId = "new")
        memory.value = jsonBase.encodeToString(MarketShoppingJournal("buyer").prepare(PendingMarketShoppingCommand("buyer", newer)))
        var calls = 0
        val store = MarketShoppingDeliveryStore(memory::get, memory::put, { ok(empty()) }, { _, _ -> failed() },
            cancelRemote = { _, _ -> calls++; ok(cancelled()) })
        assertNotNull(store.cancelPending(Owner(), command).error)
        assertEquals(0, calls); assertEquals(newer, memory.journal()?.pending?.command); assertNull(memory.journal()?.cancellingCommandId)
    }
    @Test fun prepareFailureNeverCallsTheCancellationEndpoint() = runTest {
        val memory = Memory(); memory.value = jsonBase.encodeToString(journal()); memory.failWrites = true
        var calls = 0
        val store = MarketShoppingDeliveryStore(memory::get, memory::put, { ok(empty()) }, { _, _ -> failed() },
            cancelRemote = { _, _ -> calls++; ok(cancelled()) })
        assertNotNull(store.cancelPending(Owner(), command).error)
        assertEquals(0, calls); assertNotNull(memory.journal()?.pending); assertNull(memory.journal()?.cancellingCommandId)
    }
    @Test fun acknowledgementWriteFailureKeepsBothOriginalPayloadAndCancellationIntent() = runTest {
        val memory = Memory(); memory.value = jsonBase.encodeToString(journal())
        val store = MarketShoppingDeliveryStore(memory::get, memory::put, { ok(empty()) }, { _, _ -> failed() },
            cancelRemote = { _, _ -> memory.failWrites = true; ok(cancelled()) })
        val result = store.cancelPending(Owner(), command)
        assertFalse(result.acknowledged); assertNotNull(result.pending)
        assertEquals(command.commandId, result.cancellingCommandId)
        assertEquals(command, memory.journal()?.pending?.command)
        assertEquals(command.commandId, memory.journal()?.cancellingCommandId)
    }
    @Test fun missingReadOnlyResultCannotClearCancellationIntent() = runTest {
        val memory = Memory(); val initial = journal(); memory.value = jsonBase.encodeToString(initial.requestCancellation(initial.pending!!))
        var mutations = 0
        val store = MarketShoppingDeliveryStore(memory::get, memory::put, { ok(empty()) }, { _, _ -> mutations++; failed() },
            lookupRemote = { _, original -> ok(MarketShoppingCommandLookup(original.commandId, 10)) },
            cancelRemote = { _, _ -> mutations++; failed() })
        val result = store.checkResult(Owner())
        assertFalse(result.acknowledged); assertNotNull(result.notice)
        assertEquals(0, mutations); assertEquals(command.commandId, memory.journal()?.cancellingCommandId)
    }
    @Test fun checkingACancelledRecordSettlesNeutrallyAndRetainsANewerList() = runTest {
        val memory = Memory(); val initial = journal().withSnapshot(empty(9)); memory.value = jsonBase.encodeToString(initial.requestCancellation(initial.pending!!))
        val store = MarketShoppingDeliveryStore(memory::get, memory::put, { ok(empty()) }, { _, _ -> failed() },
            lookupRemote = { _, _ -> ok(MarketShoppingCommandLookup(command.commandId, 10, cancelled())) })
        val result = store.checkResult(Owner())
        assertTrue(result.acknowledged); assertNull(result.error); assertNotNull(result.notice)
        assertNull(result.pending); assertNull(result.cancellingCommandId); assertEquals(9L, result.snapshot?.revision)
    }
    @Test fun ownerChangeDuringCancellationPreparationSendsNothing() = runTest {
        val memory = Memory(); memory.value = jsonBase.encodeToString(journal()); val owner = Owner(); var calls = 0
        val store = MarketShoppingDeliveryStore(memory::get, { key, value -> memory.put(key, value); owner.active = false },
            { ok(empty()) }, { _, _ -> failed() }, cancelRemote = { _, _ -> calls++; ok(cancelled()) })
        val result = store.cancelPending(owner, command)
        assertNull(result.snapshot); assertFalse(result.acknowledged); assertEquals(0, calls)
        assertEquals(command.commandId, memory.journal()?.cancellingCommandId)
    }
    @Test fun malformedCancellationResponseCannotRetireTheSavedIdentity() = runTest {
        val memory = Memory(); memory.value = jsonBase.encodeToString(journal())
        val store = MarketShoppingDeliveryStore(memory::get, memory::put, { ok(empty()) }, { _, _ -> failed() },
            cancelRemote = { _, _ -> ok(cancelled().copy(commandId = "another")) })
        assertFalse(store.cancelPending(Owner(), command).acknowledged)
        assertEquals(command, memory.journal()?.pending?.command); assertEquals(command.commandId, memory.journal()?.cancellingCommandId)
    }
    @Test fun unsupportedBackendNeverFallsBackToAnEdit() = runTest {
        val memory = Memory(); memory.value = jsonBase.encodeToString(journal()); var edits = 0
        val store = MarketShoppingDeliveryStore(memory::get, memory::put, { ok(empty()) }, { _, _ -> edits++; failed() })
        assertNotNull(store.cancelPending(Owner(), command).error)
        assertNotNull(store.retry(Owner()).error)
        assertEquals(0, edits); assertNotNull(memory.journal()?.pending)
    }
    @Test fun corruptCancellationMarkerCannotBeLoadedOrRetried() = runTest {
        for (bad in listOf(journal().copy(cancellingCommandId = "different"), MarketShoppingJournal("buyer", cancellingCommandId = "command"))) {
            val memory = Memory(); memory.value = jsonBase.encodeToString(bad); var calls = 0
            val store = MarketShoppingDeliveryStore(memory::get, memory::put, { ok(empty()) }, { _, _ -> calls++; failed() },
                cancelRemote = { _, _ -> calls++; failed() })
            assertNotNull(store.cached(Owner()).error); assertNotNull(store.retry(Owner()).error); assertEquals(0, calls)
        }
    }
    @Test fun cancelAndRetryShareOneSenderWithoutRacingAnApply() = runTest {
        val memory = Memory(); memory.value = jsonBase.encodeToString(journal()); val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        var edits = 0; var cancels = 0
        val store = MarketShoppingDeliveryStore(memory::get, memory::put, { ok(empty()) }, { _, _ -> edits++; failed() },
            cancelRemote = { _, _ -> cancels++; entered.complete(Unit); release.await(); ok(cancelled()) })
        val owner = Owner(); val cancelling = async { store.cancelPending(owner, command) }; entered.await()
        val retrying = async { store.retry(owner) }; release.complete(Unit)
        assertTrue(cancelling.await().acknowledged); assertNull(retrying.await().pending)
        assertEquals(1, cancels); assertEquals(0, edits)
    }
    @Test fun journalRequestAndAcknowledgementDoNotChangeTheOriginalCommand() {
        val first = journal(); val prepared = first.requestCancellation(first.pending!!)
        assertEquals(first.pending, prepared.pending); assertEquals(first.localRevision + 1, prepared.localRevision)
        assertEquals(prepared, prepared.requestCancellation(prepared.pending!!)); assertTrue(prepared.hasValidCancellationIntent())
        val retired = prepared.acknowledge(prepared.pending!!, cancelled())
        assertNull(retired.pending); assertNull(retired.cancellingCommandId); assertTrue(retired.hasValidCancellationIntent())
    }
}
