package kz.aita

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlin.test.*

class MarketBasketDeliveryTest {
    private class Owner : MarketAccountScope {
        override val accountId = BasketTestData.account
        override val generation = 1L
        var current = true
        override fun isCurrent() = current
    }
    private val a = BasketTestData.row(1)
    private val b = BasketTestData.row(2)
    private val result = BasketTestData.plan(BasketTestData.snapshot(a,b), listOf(BasketTestData.alternative(a, 10, 1), BasketTestData.alternative(b, 10, 2)))
    private val command = requireNotNull(result.reviewedBasketCommand("KZT", MARKET_BASKET_ONE_SHOP, BasketTestData.id(990)))
    private val finalSnapshot = result.snapshot.copy(revision = 8L, lines = result.currencies.single().oneShop.choices.map { it.quote })
    private fun receipt(snapshot: MarketShoppingSnapshot = finalSnapshot) = MarketShoppingOutcome(command.commandId, true, 8L, snapshot = snapshot)
    private fun <T> ok(value: T) = ResponseDataModel(null, value, false, 200)
    private class Memory {
        val data = mutableMapOf<String, String?>()
        var fail = false
        suspend fun get(key: String) = data[key]
        suspend fun put(key: String, value: String?) { check(!fail); data[key] = value }
        fun journal() = data.values.singleOrNull()?.let { jsonBase.decodeFromString<MarketShoppingJournal>(it) }
    }
    @Test fun entireReviewIsSavedBeforeOneNetworkSend() = runTest {
        val memory = Memory(); var sends = 0
        val store = MarketShoppingDeliveryStore(memory::get, memory::put, { ok(result.snapshot) }, { _, sent ->
            sends++; assertEquals(command, sent); assertEquals(command, memory.journal()?.pending?.command); ok(receipt())
        })
        val response = store.change(Owner(), command)
        assertTrue(response.acknowledged && response.accepted); assertEquals(1, sends); assertNull(memory.journal()?.pending)
    }
    @Test fun processRestartRetriesWholeSameCommandNotIndividualLines() = runTest {
        val memory = Memory(); val sent = mutableListOf<MarketShoppingCommand>()
        val first = MarketShoppingDeliveryStore(memory::get, memory::put, { ok(result.snapshot) }, { _, cmd ->
            sent += cmd; ResponseDataModel(null, null, true, 503)
        })
        first.change(Owner(), command)
        val second = MarketShoppingDeliveryStore(memory::get, memory::put, { ok(finalSnapshot) }, { _, cmd ->
            sent += cmd; ok(receipt().copy(replayed = true))
        })
        assertNotNull(second.cached(Owner()).pending); assertTrue(second.retry(Owner()).accepted)
        assertEquals(listOf(command, command), sent); assertNull(memory.journal()?.pending)
    }
    @Test fun preparationStorageFailureSendsNothing() = runTest {
        val memory = Memory().apply { fail = true }; var sends = 0
        val store = MarketShoppingDeliveryStore(memory::get, memory::put, { ok(result.snapshot) }, { _, _ -> sends++; ok(receipt()) })
        assertFalse(store.change(Owner(), command).acknowledged); assertEquals(0, sends)
    }
    @Test fun oldServerDoesNotReceiveFallbackSingleLineMutations() = runTest {
        val memory = Memory(); var sends = 0
        val store = MarketShoppingDeliveryStore(memory::get, memory::put, { ok(result.snapshot) }, { _, cmd ->
            sends++; assertEquals("market/shopping-list/apply-plan", cmd.shoppingMutationEndpoint()); ResponseDataModel(null, null, true, 404)
        })
        assertNotNull(store.change(Owner(), command).pending); assertEquals(1, sends); assertEquals(command, memory.journal()?.pending?.command)
    }
    @Test fun pendingBasketBlocksAnotherBasketAndSingleLineEdit() = runTest {
        val memory = Memory(); var sends = 0
        val store = MarketShoppingDeliveryStore(memory::get, memory::put, { ok(result.snapshot) }, { _, _ -> sends++; ResponseDataModel(null, null, true, 503) })
        store.change(Owner(), command)
        store.change(Owner(), command.copy(commandId = BasketTestData.id(991)))
        store.change(Owner(), MarketShoppingCommand("remove", 7, a.line.offerId, 0))
        assertEquals(1, sends); assertEquals(command, memory.journal()?.pending?.command)
    }
    @Test fun pendingSingleLineEditBlocksBasketPreparation() = runTest {
        val memory = Memory(); var sends = 0
        val store = MarketShoppingDeliveryStore(memory::get, memory::put, { ok(result.snapshot) }, { _, _ -> sends++; ResponseDataModel(null, null, true, 503) })
        val remove = MarketShoppingCommand("remove", 7, a.line.offerId, 0)
        store.change(Owner(), remove); store.change(Owner(), command)
        assertEquals(1, sends); assertEquals(remove, memory.journal()?.pending?.command)
    }
    @Test fun newerPersistedListRejectsNewReviewBeforeNetworkWrite() = runTest {
        val memory = Memory(); var sends = 0
        val store = MarketShoppingDeliveryStore(memory::get, memory::put, { ok(finalSnapshot) }, { _, _ -> sends++; ok(receipt()) })
        store.refresh(Owner()); assertFalse(store.change(Owner(), command).acknowledged)
        assertEquals(0, sends); assertNull(memory.journal()?.pending)
    }
    @Test fun olderReadCannotOverwriteWholeBasketAcknowledgement() = runTest {
        val memory = Memory(); val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val store = MarketShoppingDeliveryStore(memory::get, memory::put, { entered.complete(Unit); release.await(); ok(result.snapshot) }, { _, _ -> ok(receipt()) })
        val reading = async { store.refresh(Owner()) }; entered.await()
        assertTrue(store.change(Owner(), command).accepted); release.complete(Unit)
        assertEquals(finalSnapshot, reading.await().snapshot); assertNull(memory.journal()?.pending)
    }
    @Test fun malformedPartialOrWrongRevisionReceiptKeepsRecovery() = runTest {
        for (invalid in listOf(receipt(finalSnapshot.copy(lines = finalSnapshot.lines.take(1))), receipt().copy(appliedRevision = 7L))) {
            val memory = Memory()
            val store = MarketShoppingDeliveryStore(memory::get, memory::put, { ok(result.snapshot) }, { _, _ -> ok(invalid) })
            assertFalse(store.change(Owner(), command).acknowledged); assertNotNull(memory.journal()?.pending)
        }
    }
    @Test fun replayAfterLaterListEditDoesNotRestoreReviewedBasket() = runTest {
        val memory = Memory(); val later = finalSnapshot.copy(revision = 9, lines = emptyList())
        val store = MarketShoppingDeliveryStore(memory::get, memory::put, { ok(later) }, { _, _ -> ok(receipt(later).copy(replayed = true)) })
        assertTrue(store.change(Owner(), command).accepted); assertEquals(later, memory.journal()?.snapshot)
    }
    @Test fun recordedRejectionRetiresWholeCommandWithoutAcceptingIt() = runTest {
        val memory = Memory()
        val store = MarketShoppingDeliveryStore(memory::get, memory::put, { ok(result.snapshot) }, { _, _ ->
            ok(MarketShoppingOutcome(command.commandId, false, errorKey = "market.basket_apply_price_changed", snapshot = result.snapshot))
        })
        val response = store.change(Owner(), command)
        assertTrue(response.acknowledged); assertFalse(response.accepted); assertNull(memory.journal()?.pending); assertEquals(result.snapshot, memory.journal()?.snapshot)
    }
    @Test fun accountChangeWhileSendingKeepsOriginalAccountPending() = runTest {
        val memory = Memory(); val owner = Owner(); var signals = 0
        val store = MarketShoppingDeliveryStore(memory::get, memory::put, { ok(result.snapshot) }, { _, _ -> owner.current = false; ok(receipt()) }, { signals++ })
        assertFalse(store.change(owner, command).acknowledged); assertNotNull(memory.journal()?.pending); assertEquals(0, signals)
    }
    @Test fun acknowledgementDiskFailureDoesNotLoseWholeBatchRetry() = runTest {
        val memory = Memory()
        val store = MarketShoppingDeliveryStore(memory::get, memory::put, { ok(result.snapshot) }, { _, _ -> memory.fail = true; ok(receipt()) })
        val response = store.change(Owner(), command)
        assertFalse(response.acknowledged); assertEquals(command, memory.journal()?.pending?.command)
    }
    @Test fun cancellationAfterPreparationDoesNotPartiallyDiscardThePlan() = runTest {
        val memory = Memory()
        val store = MarketShoppingDeliveryStore(memory::get, memory::put, { ok(result.snapshot) }, { _, _ -> throw CancellationException() })
        assertFailsWith<CancellationException> { store.change(Owner(), command) }; assertEquals(command, memory.journal()?.pending?.command)
    }
}
