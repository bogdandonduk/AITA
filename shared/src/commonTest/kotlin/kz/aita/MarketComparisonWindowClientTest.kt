package kz.aita

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.*

class MarketComparisonWindowClientTest {
    private val f = ComparisonWindowFixtures
    private class Owner : MarketAccountScope {
        override val accountId = ComparisonWindowFixtures.account
        override val generation = 1L
        var current = true
        override fun isCurrent() = current
    }
    private fun ok(value: MarketComparisonWindowResult = f.result()) = ResponseDataModel(null, value, false, 200)

    @Test fun entireExpandedWindowUsesOneReadAndNormalizedFilters() = runTest {
        var calls = 0
        val input = f.request.copy(city = "  Astana ", candidateLimit = 400)
        val response = readOwnedMarketComparisonWindow(Owner(), input) { wanted ->
            calls++; assertEquals("Astana", wanted.city); assertEquals(400, wanted.candidateLimit)
            ok(f.result(wanted, 400))
        }
        assertEquals(1, calls); assertFalse(response.negative); assertEquals(400, response.payload!!.matches.size)
    }
    @Test fun staleOwnerNeverStartsIO() = runTest {
        val response = readOwnedMarketComparisonWindow(Owner().apply { current = false }, f.request) { error("must not read") }
        assertTrue(response.negative); assertNull(response.payload)
    }
    @Test fun invalidInputNeverStartsIO() = runTest {
        val response = readOwnedMarketComparisonWindow(Owner(), f.request.copy(candidateLimit = 401)) { error("must not read") }
        assertTrue(response.negative); assertEquals(400, response.httpStatusCode)
    }
    @Test fun ownershipChangeDuringReadDiscardsEvenAValidPayload() = runTest {
        val owner = Owner()
        val response = readOwnedMarketComparisonWindow(owner, f.request) { owner.current = false; ok() }
        assertTrue(response.negative); assertNull(response.payload)
    }
    @Test fun oldBackendGetsAnUpgradeMessageWithoutAnyPagedFallback() = runTest {
        var calls = 0
        val response = readOwnedMarketComparisonWindow(Owner(), f.request) { calls++; ok().copy(httpStatusCode = 404) }
        assertEquals(1, calls); assertTrue(response.negative); assertNull(response.payload)
        assertEquals("market.comparison_window_upgrade", response.message!!.eventMessageReferenceOrNull()?.key)
    }
    @Test fun withdrawnReferenceIsNotMisreportedAsAnOldBackend() = runTest {
        val response = readOwnedMarketComparisonWindow(Owner(), f.request) {
            ok().copy(httpStatusCode = 404, negative = true, message = eventMessage("market.unavailable"))
        }
        assertNull(response.payload); assertTrue(response.negative)
        assertEquals("market.unavailable", response.message!!.eventMessageReferenceOrNull()?.key)
    }
    @Test fun errorAndTransportPayloadsAreNotPublishedAsSuccess() = runTest {
        listOf(ok().copy(negative = true), ok().copy(transportFailure = true), ok().copy(httpStatusCode = 500),
            ok().copy(httpStatusCode = null), ok().copy(httpStatusCode = 204), ok().copy(payload = null)).forEach { received ->
            val response = readOwnedMarketComparisonWindow(Owner(), f.request) { received }
            assertTrue(response.negative); assertNull(response.payload)
        }
    }
    @Test fun aMalformedOrWrongAccountWindowIsRejectedAsAWhole() = runTest {
        listOf(f.result().copy(accountId = f.id(999)), f.result().copy(candidatesChecked = 0),
            f.result().copy(checkedAtMillis = 2L)).forEach { received ->
            val response = readOwnedMarketComparisonWindow(Owner(), f.request) { ok(received) }
            assertTrue(response.negative); assertEquals(502, response.httpStatusCode); assertNull(response.payload)
        }
    }
    @Test fun explicitServerBusyIsPreservedWithoutAutomaticRetry() = runTest {
        var calls = 0
        val response = readOwnedMarketComparisonWindow(Owner(), f.request) {
            calls++; ok().copy(httpStatusCode = 429, negative = true, message = eventMessage("market.comparison_window_busy"))
        }
        assertEquals(1, calls); assertNull(response.payload); assertEquals(429, response.httpStatusCode)
    }
    @Test fun cancellationAndExceptionsPropagateWithoutPublishingAPartialResult() = runTest {
        assertFailsWith<CancellationException> {
            readOwnedMarketComparisonWindow(Owner(), f.request) { throw CancellationException("closed") }
        }
        assertFailsWith<IllegalStateException> {
            readOwnedMarketComparisonWindow(Owner(), f.request) { error("disconnected") }
        }
    }
    @Test fun aTransportThatReturnsAfterCancellationCannotPublishItsReply() = runTest {
        assertFailsWith<CancellationException> {
            withContext(Job()) {
                readOwnedMarketComparisonWindow(Owner(), f.request) {
                    currentCoroutineContext().cancel(); ok()
                }
            }
        }
    }
}
