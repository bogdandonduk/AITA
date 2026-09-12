package kz.aita

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class MarketBrowseWindowTest {
    private fun offer(id: String = "one", saved: Boolean = false) = MarketOffer(id, MarketStorefront("shop"), "Product", saved = saved, checkedAtMillis = 5, sourceUpdatedAtMillis = 1)
    private fun ok(page: MarketPage) = ResponseDataModel(null, page, false, 200)
    @Test fun allRequestedPagesReplaceOneCompleteWindow() = runTest {
        val cursors = mutableListOf<String?>()
        val result = readMarketPageWindow(3) { cursor -> cursors += cursor
            if (cursor == null) ok(MarketPage(listOf(offer()), "next", 10)) else ok(MarketPage(listOf(offer("two")), null, 11))
        }
        assertEquals(listOf(null, "next"), cursors); assertEquals(listOf("one", "two"), result.payload?.offers?.map { it.id })
        assertEquals(10L, result.payload?.checkedAtMillis)
    }
    @Test fun laterPageFailureNeverPublishesPartialSuccess() = runTest {
        val result = readMarketPageWindow(2) { cursor -> if (cursor == null) ok(MarketPage(listOf(offer()), "next", 10))
            else ResponseDataModel(eventMessage("market.refresh_failed"), null, true, 503) }
        assertTrue(result.negative); assertNull(result.payload)
    }
    @Test fun duplicateIdsAreNotDuplicatedInVisibleWindow() = runTest {
        val result = readMarketPageWindow(2) { cursor -> if (cursor == null) ok(MarketPage(listOf(offer()), "next", 10))
            else ok(MarketPage(listOf(offer(), offer("two")), null, 11)) }
        assertEquals(2, result.payload?.offers?.size)
    }
    @Test fun repeatedCursorStopsInsteadOfCycling() = runTest {
        var calls = 0
        val result = readMarketPageWindow(10) { calls++; ok(MarketPage(listOf(offer()), "repeat", 1)) }
        assertEquals(2, calls); assertTrue(result.negative); assertNull(result.payload)
    }
    @Test fun requestedWindowIsBounded() = runTest {
        var calls = 0
        val result = readMarketPageWindow(10) { calls++; ok(MarketPage(listOf(offer(calls.toString())), calls.toString(), calls.toLong())) }
        assertEquals(10, calls); assertEquals("10", result.payload?.nextId)
    }
    @Test fun cancellationIsNotConvertedToAnEmptyPage() = runTest {
        assertFailsWith<CancellationException> { readMarketPageWindow(1) { throw CancellationException("cancelled") } }
    }
    @Test fun oldReadCannotUndoAcknowledgedHeartState() {
        val fence = MarketSavedReadFence(); val before = fence.capture()
        fence.acknowledge("one", true)
        assertTrue(fence.reconcile(MarketPage(listOf(offer())), before).offers.single().saved)
    }
    @Test fun laterReadCanShowOtherDeviceUnsave() {
        val fence = MarketSavedReadFence(); fence.acknowledge("one", true)
        assertFalse(fence.reconcile(MarketPage(listOf(offer())), fence.capture()).offers.single().saved)
    }
    @Test fun oldSavedPageCannotReinsertRemovedLine() {
        val fence = MarketSavedReadFence(); val before = fence.capture(); fence.acknowledge("one", false)
        assertTrue(fence.reconcile(MarketPage(listOf(offer(saved = true))), before, savedOnly = true).offers.isEmpty())
    }
    @Test fun readFenceKeepsUnrelatedOffersAndCursor() {
        val fence = MarketSavedReadFence(); val before = fence.capture(); fence.acknowledge("one", true)
        val result = fence.reconcile(MarketPage(listOf(offer(), offer("other")), "more"), before)
        assertEquals("more", result.nextId); assertFalse(result.offers.last().saved)
    }
}
