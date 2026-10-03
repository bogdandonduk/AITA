package kz.aita

import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class DynamicCartStoreTest {
    private val a = CartScope("account-a", "store-a", 1, 1)
    private val b = CartScope("account-b", "store-b", 2, 2)
    private fun line(slot: Int, type: Int = 0, id: String = "item-$slot") = StoredCartLine(
        id, type, slot, QuantityDataModel("piece", listOf(LocalizedStringDataModel("en", "piece")), roundTotal = true), 10L + slot)
    private class Memory(var current: CartScope?) {
        val saved = mutableMapOf<String, CartBook>()
        var fail = false
        var saves = 0
        var gate: CompletableDeferred<Unit>? = null
        var entered: CompletableDeferred<Unit>? = null
        val store = DynamicCartStore({ it == current }, { saved[it.storageKey] }, { owner, value ->
            entered?.complete(Unit); gate?.await()
            check(!fail) { "disk unavailable" }
            saved[owner.storageKey] = value; saves++
        })
    }
    @Test fun checkoutSurvivesFailedClearAndRestartWithSameOperationAndOtherCartsUntouched() = runTest {
        val f = Memory(a); f.store.adopt(a)
        val tx = TransactionDataModel("", 0, "purchase", a.storeId, emptyList(), 10.0, 0.0, 0, timeMillis = 100,
            clientOperationId = "stable-operation")
        val receipt = TransactionReceiptSnapshotDataModel(tx, null, emptyList(),
            TransactionPaymentDraftDataModel(0, 0, "0", 10.0, 0.0, 0), "KZT", "KZT")
        val attempt = CartCheckoutAttempt(tx, receipt)
        f.store.change(a) { it.copy(lines = listOf(line(0), line(1)),
            ui = CartUiState(discounts = mapOf("0:0" to 10.0, "0:1" to 25.0), checkouts = mapOf("0:0" to attempt))) }
        f.fail = true
        assertFailsWith<IllegalStateException> { f.store.change(a) { it.withoutCart(0, 0) } }
        assertEquals(attempt, f.store.state.value.book.ui.checkouts["0:0"])
        val serialized = jsonBase.encodeToString(CartBook.serializer(), f.saved[a.storageKey]!!)
        f.saved[a.storageKey] = jsonBase.decodeFromString(CartBook.serializer(), serialized)
        val renewed = a.copy(generation = 3, epoch = 3); f.current = renewed; f.fail = false
        f.store.adopt(renewed)
        assertEquals("stable-operation", f.store.state.value.book.ui.checkouts["0:0"]!!.transaction.clientOperationId)
        val before = f.store.state.value.book.ui
        val edited = before.copy(discounts = mapOf("0:0" to 100.0, "0:1" to 50.0)).preservingPending(before)
        assertEquals(mapOf("0:0" to 10.0, "0:1" to 50.0), edited.discounts)
        assertTrue(f.store.change(renewed) { it.withoutCart(0, 0) })
        assertTrue(f.store.state.value.book.ui.checkouts.isEmpty())
        assertEquals(listOf(1), f.store.state.value.book.lines.map { it.slot })
        assertEquals(mapOf("0:1" to 25.0), f.store.state.value.book.ui.discounts)
    }
    @Test fun supplyPriceSurvivesPersistenceAndQuantityChangesWithoutChangingOtherCarts() = runTest {
        val f = Memory(a); f.store.adopt(a)
        val price = PriceDataModel("17.5", "KZT", "supplier")
        val first = line(0, 2).copy(supplyPrice = price)
        f.store.change(a) { it.copy(lines = listOf(first, line(1, 2))) }
        f.store.change(a) { it.copy(lines = it.lines.map { l -> if (l.slot == 0) l.copy(quantity = l.quantity.withTotalValue(3.0)) else l }) }
        val json = jsonBase.encodeToString(CartBook.serializer(), f.saved[a.storageKey]!!)
        val restored = jsonBase.decodeFromString(CartBook.serializer(), json).validated()
        assertEquals(price, restored.lines.first().supplyPrice)
        assertEquals(3.0, restored.lines.first().quantity.total)
        assertNull(restored.lines.last().supplyPrice)
    }
    @Test fun freshBookStartsWithTwoAndAddsExactlyOne() = runTest {
        val f = Memory(a); f.store.adopt(a)
        assertEquals(listOf(2, 2, 2), f.store.state.value.book.counts)
        assertEquals(2, f.store.add(a, 0)); assertEquals(listOf(3, 2, 2), f.store.state.value.book.counts)
        assertEquals(1, f.saves)
    }
    @Test fun parallelAddsNeverLoseOrDuplicateSlots() = runTest {
        val f = Memory(a); f.store.adopt(a)
        val ids = (1..100).map { async { f.store.add(a, 0) } }.awaitAll()
        assertEquals((2..101).toSet(), ids.toSet())
        assertEquals(102, f.store.state.value.book.counts[0])
    }
    @Test fun occupiedLegacySlotsAndDraftOnlySlotsAreRevealed() {
        val book = CartBook(lines = listOf(line(2), line(4)), ui = CartUiState(suppliers = mapOf("2:27" to "supplier"))).validated()
        assertEquals(listOf(5, 2, 28), book.counts)
        assertEquals(listOf(2, 4), book.lines.map { it.slot })
        assertEquals("supplier", book.ui.suppliers["2:27"])
    }
    @Test fun restorationKeepsHighIndicesInsteadOfClampingToFive() = runTest {
        val f = Memory(a)
        f.saved[a.storageKey] = CartBook(lines = listOf(line(6), line(19), line(27), line(999)))
        f.store.adopt(a)
        assertEquals(1000, f.store.state.value.book.counts[0])
        assertEquals(listOf(6, 19, 27, 999), f.store.state.value.book.lines.map { it.slot })
        assertNull(f.store.add(a, 0)); assertEquals(0, f.saves)
    }
    @Test fun failedWriteDoesNotPublishOrClearPreviousState() = runTest {
        val f = Memory(a); f.store.adopt(a); f.fail = true
        assertFailsWith<IllegalStateException> { f.store.add(a, 0) }
        assertEquals(2, f.store.state.value.book.counts[0]); assertEquals(0, f.saves)
        assertTrue(f.store.state.value.ready)
    }
    @Test fun latePersistenceNeverPublishesUnderAnotherAccount() = runTest {
        val f = Memory(a); f.store.adopt(a)
        f.gate = CompletableDeferred(); f.entered = CompletableDeferred()
        val changing = async { f.store.add(a, 0) }
        f.entered!!.await(); f.current = b
        val adopting = async { f.store.adopt(b) }
        f.gate!!.complete(Unit); changing.await(); adopting.await()
        assertEquals(3, f.saved[a.storageKey]!!.counts[0])
        assertEquals(b, f.store.state.value.owner)
        assertEquals(2, f.store.state.value.book.counts[0])
        assertNull(f.saved[b.storageKey])
        assertFalse(f.store.change(a) { it.copy(lines = listOf(line(7))) })
    }
    @Test fun newSessionOfSameAccountRejectsOldTicketAndRestoresDurableBook() = runTest {
        val f = Memory(a); f.store.adopt(a); f.store.add(a, 0)
        val renewed = a.copy(generation = 3, epoch = 3); f.current = renewed
        f.store.adopt(renewed)
        assertEquals(3, f.store.state.value.book.counts[0])
        assertNull(f.store.add(a, 0)); assertEquals(3, f.store.add(renewed, 0))
    }
    @Test fun corruptSavedCountsFailClosedWithoutWritingAnEmptyReplacement() = runTest {
        val f = Memory(a); f.saved[a.storageKey] = CartBook(counts = listOf(2, 2, MAX_CART_SLOTS + 1))
        assertFailsWith<IllegalArgumentException> { f.store.adopt(a) }
        assertTrue(f.store.state.value.failed); assertFalse(f.store.state.value.ready)
        assertEquals(MAX_CART_SLOTS + 1, f.saved[a.storageKey]!!.counts[2]); assertEquals(0, f.saves)
    }
    @Test fun clearingOneCartAtomicallyKeepsOtherContentsAndSupplier() = runTest {
        val f = Memory(a)
        f.saved[a.storageKey] = CartBook(lines = listOf(line(6, 2), line(19, 2)),
            ui = CartUiState(suppliers = mapOf("2:6" to "first", "2:19" to "second"),
                checks = mapOf("2:6:item-6:check" to true, "2:19:item-19:check" to true)))
        f.store.adopt(a); f.store.change(a) { it.withoutCart(2, 6) }
        val saved = f.saved[a.storageKey]!!
        assertEquals(listOf(19), saved.lines.map { it.slot })
        assertEquals(mapOf("2:19" to "second"), saved.ui.suppliers)
        assertEquals(mapOf("2:19:item-19:check" to true), saved.ui.checks)
        assertEquals(20, saved.counts[2])
    }
    @Test fun removalKeepsStableIdsAndEveryOtherCartsState() = runTest {
        val f = Memory(a)
        f.saved[a.storageKey] = CartBook(counts = listOf(4, 2, 2), lines = listOf(line(2), line(3)),
            ui = CartUiState(suppliers = mapOf("0:2" to "removed", "0:3" to "retained")))
        f.store.adopt(a)
        assertTrue(f.store.change(a) { it.removeSlot(0, 2) })
        val book = f.saved[a.storageKey]!!
        assertEquals(listOf(0, 1, 3), book.activeSlots(0))
        assertEquals(listOf(3), book.lines.map { it.slot })
        assertEquals(mapOf("0:3" to "retained"), book.ui.suppliers)
        assertFalse(f.store.reveal(a, 0, 2))
        assertEquals(4, f.store.add(a, 0)) // Never reuse a removed ID held by a delayed callback.
        assertEquals(listOf(0, 1, 3, 4), f.store.state.value.book.activeSlots(0))
        val serialized = jsonBase.encodeToString(CartBook.serializer(), f.store.state.value.book)
        assertEquals(f.store.state.value.book, jsonBase.decodeFromString(CartBook.serializer(), serialized).validated())
    }
    @Test fun minimumCartsCannotBeRemovedAndEmptyAddedCartsCan() = runTest {
        val f = Memory(a); f.store.adopt(a)
        for (id in 0..1) assertFailsWith<IllegalArgumentException> { f.store.change(a) { it.removeSlot(0, id) } }
        val id = assertNotNull(f.store.add(a, 0))
        f.store.change(a) { it.removeSlot(0, id) }
        assertEquals(listOf(0, 1), f.store.state.value.book.activeSlots(0))
        assertTrue(f.store.state.value.book.lines.isEmpty())
    }
    @Test fun removalCapacityIsReclaimedWithoutReusingAnIdentity() = runTest {
        val f = Memory(a); f.store.adopt(a)
        repeat(MAX_CART_SLOTS + 2) {
            val id = assertNotNull(f.store.add(a, 0))
            assertEquals(it + 2, id)
            f.store.change(a) { book -> book.removeSlot(0, id) }
        }
        assertEquals(2, f.store.state.value.book.counts[0])
    }
    @Test fun failedRemovalPreservesCartAndWrongOwnerCannotRemoveIt() = runTest {
        val f = Memory(a); f.store.adopt(a); f.store.add(a, 0); f.fail = true
        assertFailsWith<IllegalStateException> { f.store.change(a) { it.removeSlot(0, 2) } }
        assertTrue(f.store.state.value.book.contains(0, 2))
        f.current = b
        assertFalse(f.store.change(a) { it.removeSlot(0, 2) })
        assertTrue(f.saved[a.storageKey]!!.contains(0, 2))
    }
    @Test fun stateValueImmediatelyHidesAnotherOwner() = runTest {
        val f = Memory(a); f.saved[a.storageKey] = CartBook(lines = listOf(line(27)))
        f.store.adopt(a)
        val flow = CartLinesStateFlow(f.store.state, 0, 27) { it == f.current }
        assertEquals(27, flow.value.single().clientId)
        f.current = b
        assertTrue(flow.value.isEmpty())
    }
    @Test fun invalidIdentityAndDuplicateRowsAreRejected() {
        assertFalse(validCartSlot(-1, 0)); assertFalse(validCartSlot(0, -1))
        assertFailsWith<IllegalArgumentException> { CartBook(lines = listOf(line(1000))).validated() }
        assertFailsWith<IllegalArgumentException> { CartBook(lines = listOf(line(0), line(0))).validated() }
        assertNotEquals(CartScope("a:b", "c", 1, 1).storageKey, CartScope("a", "b:c", 1, 1).storageKey)
    }
}
