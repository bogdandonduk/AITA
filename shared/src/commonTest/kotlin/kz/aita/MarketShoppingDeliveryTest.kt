package kz.aita

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlin.test.*

class MarketShoppingDeliveryTest {
    private class Owner(override val accountId: String = "buyer") : MarketAccountScope {
        override val generation = 1L
        var current = true
        override fun isCurrent() = current
    }
    private val command = MarketShoppingCommand("command", 0, "offer", 1, MarketShoppingBasis(null, "KZT", "piece", 1.0))
    private fun snapshot(revision: Long = 1) = MarketShoppingSnapshot("buyer", revision, checkedAtMillis = revision + 1)
    private fun outcome(request: MarketShoppingCommand = command) = MarketShoppingOutcome(request.commandId, true, 1,
        snapshot = snapshot().copy(lines = if (request.units == 0) emptyList() else listOf(MarketShoppingQuotedLine(
            MarketShoppingLine(request.offerId, "shop", "Product", "Shop", request.units, requireNotNull(request.basis))))))
    private fun <T> ok(data: T) = ResponseDataModel(null, data, false, 200)
    private class Memory {
        val data = mutableMapOf<String, String?>()
        var fail = false
        suspend fun get(key: String) = data[key]
        suspend fun put(key: String, value: String?) { if (fail) error("disk unavailable"); data[key] = value }
        fun journal() = data.values.singleOrNull()?.let { jsonBase.decodeFromString<MarketShoppingJournal>(it) }
    }
    @Test fun pendingIsDurableBeforeNetworkWriteAndAckRetiresIt() = runTest {
        val memory = Memory(); val owner = Owner(); var sends = 0
        val store = MarketShoppingDeliveryStore(memory::get, memory::put, { ok(snapshot(0)) }, { _, request ->
            sends++; assertEquals(request, memory.journal()?.pending?.command); ok(outcome(request))
        })
        val result = store.change(owner, command)
        assertTrue(result.acknowledged && result.accepted); assertEquals(1, sends); assertNull(memory.journal()?.pending)
    }
    @Test fun diskFailureBeforePrepareNeverSendsCommand() = runTest {
        val memory = Memory().apply { fail = true }; var sends = 0
        val store = MarketShoppingDeliveryStore(memory::get, memory::put, { ok(snapshot()) }, { _, request -> sends++; ok(outcome(request)) })
        assertFalse(store.change(Owner(), command).acknowledged); assertEquals(0, sends); assertNull(memory.journal())
    }
    @Test fun responseLossSurvivesANewClientAndRetriesExactCommand() = runTest {
        val memory = Memory(); val sent = mutableListOf<MarketShoppingCommand>(); val owner = Owner()
        val first = MarketShoppingDeliveryStore(memory::get, memory::put, { ok(snapshot()) }, { _, request ->
            sent += request; ResponseDataModel(null, null, true, 503, true)
        })
        assertNotNull(first.change(owner, command).pending)
        val restarted = MarketShoppingDeliveryStore(memory::get, memory::put, { ok(snapshot()) }, { _, request -> sent += request; ok(outcome(request).copy(replayed = true)) })
        assertNotNull(restarted.cached(owner).pending)
        assertTrue(restarted.retry(owner).acknowledged)
        assertEquals(listOf(command, command), sent); assertNull(memory.journal()?.pending)
    }
    @Test fun newCommandCannotReplaceUncertainOne() = runTest {
        val memory = Memory(); var sends = 0
        val store = MarketShoppingDeliveryStore(memory::get, memory::put, { ok(snapshot()) }, { _, _ -> sends++; ResponseDataModel(null, null, true, 503) })
        store.change(Owner(), command)
        store.change(Owner(), command.copy(commandId = "another"))
        assertEquals(1, sends); assertEquals(command, memory.journal()?.pending?.command)
    }
    @Test fun knownRecordedConflictRetiresRetryAndReturnsLatestList() = runTest {
        val memory = Memory()
        val store = MarketShoppingDeliveryStore(memory::get, memory::put, { ok(snapshot()) }, { _, request ->
            ok(MarketShoppingOutcome(request.commandId, false, errorKey = "market.shopping_changed", snapshot = snapshot(2)))
        })
        val result = store.change(Owner(), command)
        assertTrue(result.acknowledged); assertFalse(result.accepted); assertNotNull(result.error)
        assertEquals(2L, result.snapshot?.revision); assertNull(memory.journal()?.pending)
    }
    @Test fun wrongAccountOrCommandReceiptNeverClearsPending() = runTest {
        for (receipt in listOf(outcome().copy(commandId = "other"), outcome().copy(snapshot = snapshot().copy(userId = "other")))) {
            val memory = Memory()
            val store = MarketShoppingDeliveryStore(memory::get, memory::put, { ok(snapshot()) }, { _, _ -> ok(receipt) })
            assertFalse(store.change(Owner(), command).acknowledged); assertNotNull(memory.journal()?.pending)
        }
    }
    @Test fun acknowledgementStorageFailureKeepsRetryIdentity() = runTest {
        val memory = Memory()
        val store = MarketShoppingDeliveryStore(memory::get, memory::put, { ok(snapshot()) }, { _, request -> memory.fail = true; ok(outcome(request)) })
        val result = store.change(Owner(), command)
        assertFalse(result.acknowledged); assertNotNull(result.pending); assertNotNull(memory.journal()?.pending)
    }
    @Test fun ownerChangeDuringRequestDoesNotPublishOrRetireAnotherOwnersWork() = runTest {
        val memory = Memory(); val owner = Owner(); var signals = 0
        val store = MarketShoppingDeliveryStore(memory::get, memory::put, { ok(snapshot()) }, { _, request -> owner.current = false; ok(outcome(request)) }, { signals++ })
        val result = store.change(owner, command)
        assertFalse(result.acknowledged); assertNull(result.snapshot); assertEquals(0, signals); assertNotNull(memory.journal()?.pending)
    }
    @Test fun canceledTransportKeepsTheAlreadyStoredCommand() = runTest {
        val memory = Memory()
        val store = MarketShoppingDeliveryStore(memory::get, memory::put, { ok(snapshot()) }, { _, _ -> throw CancellationException("view closed") })
        assertFailsWith<CancellationException> { store.change(Owner(), command) }
        assertEquals(command, memory.journal()?.pending?.command)
    }
    @Test fun lateReadCannotRestoreOlderListOrAlreadyRetiredPendingCommand() = runTest {
        val memory = Memory(); val owner = Owner()
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val store = MarketShoppingDeliveryStore(memory::get, memory::put, { entered.complete(Unit); release.await(); ok(snapshot(0)) }, { _, request -> ok(outcome(request)) })
        val oldRead = async { store.refresh(owner) }; entered.await()
        val acknowledged = store.change(owner, command); release.complete(Unit)
        val late = oldRead.await()
        assertTrue(acknowledged.acknowledged); assertEquals(1L, late.snapshot?.revision); assertNull(late.pending)
    }
    @Test fun malformedLocalOwnerRecordIsNotDisplayedOrOverwritten() = runTest {
        val memory = Memory(); val original = jsonBase.encodeToString(MarketShoppingJournal("other"))
        memory.data["buyer-shopping-journal-v1:buyer"] = original
        var sends = 0
        val store = MarketShoppingDeliveryStore(memory::get, memory::put, { ok(snapshot()) }, { _, request -> sends++; ok(outcome(request)) })
        assertNotNull(store.cached(Owner()).error); store.change(Owner(), command)
        assertEquals(0, sends); assertEquals(original, memory.data.values.single())
    }
    @Test fun backgroundRefreshPreservesUnconfirmedCommand() = runTest {
        val memory = Memory(); val owner = Owner()
        val store = MarketShoppingDeliveryStore(memory::get, memory::put, { ok(snapshot(5)) }, { _, _ -> ResponseDataModel(null, null, true, 503) })
        store.change(owner, command)
        val current = store.refresh(owner)
        assertEquals(5L, current.snapshot?.revision); assertEquals(command, current.pending?.command)
    }
    @Test fun unknownResponseKeepsTheDurableSequenceAheadOfEarlierReads() = runTest {
        val memory = Memory(); val owner = Owner()
        val store = MarketShoppingDeliveryStore(memory::get, memory::put, { ok(snapshot(0)) }, { _, _ ->
            ResponseDataModel(null, null, true, 503)
        })
        val earlier = store.refresh(owner)
        val uncertain = store.change(owner, command)
        assertNotNull(uncertain.pending)
        assertTrue(uncertain.journalRevision > earlier.journalRevision)
        assertEquals(memory.journal()?.localRevision, uncertain.journalRevision)
    }
    @Test fun malformedSameAccountRevisionIsNotOverwrittenOrSent() = runTest {
        val memory = Memory(); val original = jsonBase.encodeToString(MarketShoppingJournal("buyer", snapshot(-1)))
        memory.data["buyer-shopping-journal-v1:buyer"] = original
        var sends = 0
        val store = MarketShoppingDeliveryStore(memory::get, memory::put, { ok(snapshot()) }, { _, request -> sends++; ok(outcome(request)) })
        assertNotNull(store.cached(Owner()).error)
        assertNull(store.refresh(Owner()).snapshot)
        assertFalse(store.change(Owner(), command).acknowledged)
        assertEquals(0, sends); assertEquals(original, memory.data.values.single())
    }

    @Test fun replacementResponseLossRetriesTheWholeReviewedCommand() = runTest {
        val memory = Memory(); val owner = Owner(); val sent = mutableListOf<MarketShoppingCommand>()
        val replacement = command.copy(basis = MarketShoppingBasis("04006381333931", "KZT", "piece", 1.0),
            replaceOfferId = "original", reviewedSubtotalMinor = 19999)
        val first = MarketShoppingDeliveryStore(memory::get, memory::put, { ok(snapshot()) }, { _, value ->
            sent += value; ResponseDataModel(null, null, true, 503)
        })
        assertNotNull(first.change(owner, replacement).pending)
        val restarted = MarketShoppingDeliveryStore(memory::get, memory::put, { ok(snapshot()) }, { _, value ->
            sent += value; ok(outcome(value).copy(replayed = true))
        })
        val result = restarted.retry(owner)
        assertTrue(result.acknowledged); assertEquals(replacement.commandId, result.acknowledgedCommandId)
        assertEquals(listOf(replacement, replacement), sent)
    }
    @Test fun malformedRemoteSnapshotDoesNotOverwriteGoodLocalData() = runTest {
        val memory = Memory(); val owner = Owner()
        val good = MarketShoppingJournal("buyer", snapshot(3))
        val original = jsonBase.encodeToString(good); memory.data["buyer-shopping-journal-v1:buyer"] = original
        val store = MarketShoppingDeliveryStore(memory::get, memory::put, { ok(snapshot(-1)) }, { _, value -> ok(outcome(value)) })
        val result = store.refresh(owner)
        assertNotNull(result.error); assertFalse(result.fresh); assertEquals(3L, result.snapshot?.revision)
        assertEquals(original, memory.data.values.single())
    }
    @Test fun malformedAcknowledgementDoesNotRetireTheStoredCommand() = runTest {
        val memory = Memory()
        val duplicate = MarketShoppingLine("x", "shop", "Product", "Shop", 1, MarketShoppingBasis(null, "KZT", "piece", 1.0))
        val malformed = snapshot().copy(lines = listOf(MarketShoppingQuotedLine(duplicate), MarketShoppingQuotedLine(duplicate)))
        val store = MarketShoppingDeliveryStore(memory::get, memory::put, { ok(snapshot()) }, { _, value -> ok(outcome(value).copy(snapshot = malformed)) })
        assertFalse(store.change(Owner(), command).acknowledged); assertNotNull(memory.journal()?.pending)
    }
    @Test fun invalidReplacementCannotBecomeAPendingPoisonedJournal() = runTest {
        val memory = Memory(); var sends = 0
        val store = MarketShoppingDeliveryStore(memory::get, memory::put, { ok(snapshot()) }, { _, value -> sends++; ok(outcome(value)) })
        assertFalse(store.change(Owner(), command.copy(replaceOfferId = "original")).acknowledged)
        assertEquals(0, sends); assertNull(memory.journal())
    }

    @Test fun ownerChangeDuringStorageReadNeverPreparesOrSendsNewWork() = runTest {
        val memory = Memory(); val owner = Owner(); var sends = 0
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val store = MarketShoppingDeliveryStore({ key -> entered.complete(Unit); release.await(); memory.get(key) },
            memory::put, { ok(snapshot()) }, { _, value -> sends++; ok(outcome(value)) })
        val changing = async { store.change(owner, command) }
        entered.await(); owner.current = false; release.complete(Unit)
        assertFalse(changing.await().acknowledged)
        assertEquals(0, sends); assertNull(memory.journal())
    }
    @Test fun apparentlySuccessfulButMissingQuantityCannotRetireTheJournal() = runTest {
        for (lookup in listOf(false,true)) {
            val memory = Memory(); val owner = Owner()
            val store = MarketShoppingDeliveryStore(memory::get,memory::put,{ ok(snapshot()) },
                { _, value -> if (lookup) ResponseDataModel(null,null,true,503)
                    else ok(outcome(value).copy(snapshot = snapshot())) },
                lookupRemote = { _, value -> ok(MarketShoppingCommandLookup(value.commandId,100,
                    outcome(value).copy(snapshot = snapshot()))) })
            val result = store.change(owner,command)
            assertFalse(result.acknowledged); assertNotNull(memory.journal()?.pending)
            if (lookup) { assertFalse(store.checkResult(owner).acknowledged); assertNotNull(memory.journal()?.pending) }
        }
    }
    @Test fun cacheReadFinishingAfterAnAccountSwitchDoesNotPublishTheOldJournal() = runTest {
        val memory = Memory(); val owner = Owner()
        memory.data["buyer-shopping-journal-v1:buyer"] = jsonBase.encodeToString(MarketShoppingJournal("buyer",snapshot()))
        val store = MarketShoppingDeliveryStore({ key -> owner.current = false; memory.get(key) },memory::put,
            { ok(snapshot()) },{ _, value -> ok(outcome(value)) })
        val result = store.cached(owner)
        assertNull(result.snapshot); assertNull(result.pending)
        assertEquals("buyer",memory.journal()?.accountId)
    }
    @Test fun acknowledgedWriteBelongsToOriginalAccountButCannotPublishAfterSwitch() = runTest {
        val memory = Memory(); val owner = Owner(); var signals = 0
        val store = MarketShoppingDeliveryStore(memory::get, { key,value ->
            memory.put(key,value)
            if (memory.journal()?.pending == null) owner.current = false
        }, { ok(snapshot()) },{ _, value -> ok(outcome(value)) },{ signals++ })
        val result = store.change(owner,command)
        assertNull(result.snapshot); assertFalse(result.acknowledged); assertEquals(0,signals)
        assertNull(memory.journal()?.pending); assertEquals(1L,memory.journal()?.snapshot?.revision)
    }
    @Test fun refreshWriteCannotReturnPreviousAccountsSnapshotAfterSwitch() = runTest {
        val memory = Memory(); val owner = Owner()
        val store = MarketShoppingDeliveryStore(memory::get,{ key,value -> memory.put(key,value); owner.current = false },
            { ok(snapshot()) },{ _, value -> ok(outcome(value)) })
        assertNull(store.refresh(owner).snapshot)
        assertEquals("buyer",memory.journal()?.snapshot?.userId)
    }

}
