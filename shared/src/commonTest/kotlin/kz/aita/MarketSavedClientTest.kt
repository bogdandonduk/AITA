package kz.aita

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class MarketSavedClientTest {
    private fun id(n: Int) = "00000000-0000-0000-0000-${n.toString().padStart(12, '0')}"
    private val shop = MarketStorefront(id(900), "Public shop", "Astana", "Public door", published = true, revision = 1)
    private fun offer(n: Int = 1) = MarketOffer(id(n), shop, "Product $n", checkedAtMillis = 100, sourceUpdatedAtMillis = 10, saved = true)
    private fun page(vararg ids: Int) = MarketPage(ids.map(::offer), checkedAtMillis = 100)
    private fun MarketPage.ack(n: Int = 1, saved: Boolean = true) = copy(savedMutation = MarketSavedUpdate(id(n), saved))
    private fun ok(page: MarketPage) = ResponseDataModel(null, page, false, 200)
    private class Owner : MarketAccountScope {
        override val accountId = "buyer"
        override val generation = 1L
        var active = true
        override fun isCurrent() = active
    }

    @Test fun completePublicSavedSetIncludesUnpricedOffersAndUnavailableCount() {
        assertTrue(page(1).copy(unavailableSavedCount = 99).isValidMarketSavedSnapshot())
        assertTrue(page().copy(unavailableSavedCount = 100).isValidMarketSavedSnapshot())
        assertTrue(page().isValidMarketSavedSnapshot())
    }
    @Test fun boundsDuplicatesAndPartialPagesAreNotFullAcknowledgements() {
        for (invalid in listOf(page(1, 1), page(1).copy(unavailableSavedCount = 100),
            page().copy(unavailableSavedCount = -1), page().copy(unavailableSavedCount = 101),
            page(1).copy(nextId = id(1)), page(1).copy(checkedAtMillis = 0),
            page(1).copy(offers = (1..101).map(::offer)))) assertFalse(invalid.isValidMarketSavedSnapshot())
    }
    @Test fun privateInvalidAndWrongTimestampOffersCannotOverrideVisibleCards() {
        val good = offer()
        for (invalid in listOf(good.copy(saved = false), good.copy(id = "not-an-id"),
            good.copy(storefront = shop.copy(published = false)), good.copy(storefront = shop.copy(revision = 0)),
            good.copy(storefront = shop.copy(publicAddress = "")), good.copy(title = ""),
            good.copy(title = "x".repeat(181)), good.copy(description = "x".repeat(2001)),
            good.copy(sourceUpdatedAtMillis = -1), good.copy(checkedAtMillis = 101)))
            assertFalse(page(1).copy(offers = listOf(invalid)).isValidMarketSavedSnapshot())
    }
    @Test fun malformedPriceCannotEnterAcknowledgedSnapshot() {
        val priced = offer().copy(priceMinor = 500, currencyCode = "KZT", unitId = "piece", pricedAmount = 1.0)
        assertTrue(page(1).copy(offers = listOf(priced)).isValidMarketSavedSnapshot())
        for (invalid in listOf(priced.copy(priceMinor = -1), priced.copy(currencyCode = "kzt"),
            priced.copy(pricedAmount = Double.NaN), priced.copy(unitId = ""), priced.copy(priceMinor = null)))
            assertFalse(page(1).copy(offers = listOf(invalid)).isValidMarketSavedSnapshot())
    }
    @Test fun requestedStateMustBePresentNotJustAWellFormedPage() {
        assertTrue(page(1, 2).ack().acknowledgesMarketSavedUpdate(MarketSavedUpdate(id(1), true)))
        assertTrue(page(2).ack(saved = false).acknowledgesMarketSavedUpdate(MarketSavedUpdate(id(1), false)))
        assertFalse(page(2).acknowledgesMarketSavedUpdate(MarketSavedUpdate(id(1), true)))
        assertFalse(page(1, 2).acknowledgesMarketSavedUpdate(MarketSavedUpdate(id(1), false)))
        assertFalse(page().copy(unavailableSavedCount = 1).acknowledgesMarketSavedUpdate(MarketSavedUpdate(id(1), true)))
        assertFalse(page().acknowledgesMarketSavedUpdate(MarketSavedUpdate("", false)))
    }
    @Test fun hiddenCountOrLegacyPageCannotPretendThatADeletionCommitted() = runTest {
        val request = MarketSavedUpdate(id(1), false)
        val legacy = page(2).copy(unavailableSavedCount = 1)
        assertFalse(legacy.acknowledgesMarketSavedUpdate(request))
        assertTrue(legacy.ack(saved = false).acknowledgesMarketSavedUpdate(request))
        val response = updateOwnedMarketSaved(Owner(), request) { ok(legacy) }
        assertTrue(response.negative); assertNull(response.payload)
        // A subsequent old-server GET is still readable; only the write is unconfirmed.
        assertEquals(legacy, readOwnedMarketSaved(Owner()) { ok(legacy) }.payload)
    }
    @Test fun wrongTargetWrongActionAndMissingCleanupAcknowledgementAreRejected() = runTest {
        val request = MarketSavedUpdate(id(1), false)
        for (reply in listOf(page(2).ack(3, false), page(2).ack(1, true),
            page(2).ack(1, false).copy(unavailableSavedCleared = true))) {
            assertFalse(reply.acknowledgesMarketSavedUpdate(request))
            assertTrue(updateOwnedMarketSaved(Owner(), request) { ok(reply) }.negative)
        }
        assertTrue(clearUnavailableOwnedMarketSaved(Owner()) { ok(page(2)) }.negative)
        assertTrue(clearUnavailableOwnedMarketSaved(Owner()) {
            ok(page(2).ack(saved = false).copy(unavailableSavedCleared = true))
        }.negative)
    }
    @Test fun incoherentTransportReplyCannotDisplayASuccessMessageAsItsFailure() = runTest {
        val wrong = ok(page(1).ack()).copy(httpStatusCode = 202, message = eventMessage("market.saved"))
        val result = updateOwnedMarketSaved(Owner(), MarketSavedUpdate(id(1), true)) { wrong }
        assertTrue(result.negative); assertNull(result.payload)
        assertEquals(eventMessage("market.saved_unconfirmed"), result.message)
    }
    @Test fun validSaveAndRemovalAreReturnedWithOneReadInvalidation() = runTest {
        for (desired in listOf(true, false)) {
            var writes = 0; var invalidations = 0
            val expected = (if (desired) page(1, 2) else page(2)).ack(saved = desired)
            val result = updateOwnedMarketSaved(Owner(), MarketSavedUpdate(id(1), desired), { invalidations++ }) {
                writes++; assertEquals(MarketSavedUpdate(id(1), desired), it); ok(expected)
            }
            assertFalse(result.negative); assertEquals(expected, result.payload)
            assertEquals(1, writes); assertEquals(1, invalidations)
        }
    }
    @Test fun wrongSaveOrRemovalReplyCannotAdvanceTheReadFence() = runTest {
        val fence = MarketSavedReadFence(); val captured = fence.capture()
        for (desired in listOf(true, false)) {
            var invalidations = 0
            val result = updateOwnedMarketSaved(Owner(), MarketSavedUpdate(id(1), desired), { invalidations++ }) {
                ok((if (desired) page(2) else page(1, 2)).ack(saved = desired))
            }
            result.payload?.let { fence.acknowledgeDiscoverySaved(page(1, 2), it, savedOnly = true) }
            assertTrue(result.negative); assertNull(result.payload); assertEquals(502, result.httpStatusCode)
            assertEquals(captured, fence.capture()); assertEquals(1, invalidations)
        }
    }
    @Test fun clearRequiresZeroUnavailableWithoutDroppingPublicBookmarks() = runTest {
        val remaining = page(2, 3).copy(unavailableSavedCleared = true)
        val result = clearUnavailableOwnedMarketSaved(Owner()) { ok(remaining) }
        assertFalse(result.negative); assertEquals(remaining, result.payload)
        val invalid = clearUnavailableOwnedMarketSaved(Owner()) { ok(remaining.copy(unavailableSavedCount = 1)) }
        assertTrue(invalid.negative); assertNull(invalid.payload)
    }
    @Test fun serverFailureCannotSmuggleAPayloadIntoTheFence() = runTest {
        val original = ResponseDataModel(eventMessage("market.saved_limit"), page(1), true, 409)
        var invalidations = 0
        val result = updateOwnedMarketSaved(Owner(), MarketSavedUpdate(id(1), true), { invalidations++ }) { original }
        assertTrue(result.negative); assertNull(result.payload); assertEquals(original.message, result.message)
        assertEquals(409, result.httpStatusCode); assertEquals(1, invalidations)
    }
    @Test fun successStatusBodyAndTransportMustAllAgree() = runTest {
        for (response in listOf(ok(page(1)).copy(httpStatusCode = null), ok(page(1)).copy(httpStatusCode = 202),
            ok(page(1)).copy(httpStatusCode = 500), ok(page(1)).copy(transportFailure = true), ok(page(1)).copy(payload = null))) {
            val result = updateOwnedMarketSaved(Owner(), MarketSavedUpdate(id(1), true)) { response }
            assertTrue(result.negative); assertNull(result.payload)
        }
    }
    @Test fun lostReplyInvalidatesForReadButNeverResendsTheWrite() = runTest {
        var writes = 0; var invalidations = 0
        val result = updateOwnedMarketSaved(Owner(), MarketSavedUpdate(id(1), true), { invalidations++ }) {
            writes++; throw IllegalStateException("connection lost after possible commit")
        }
        assertTrue(result.negative); assertTrue(result.transportFailure); assertNull(result.payload)
        assertEquals(1, writes); assertEquals(1, invalidations)
    }
    @Test fun invalidIdOrObsoleteOwnerDoesNotWriteOrInvalidate() = runTest {
        var writes = 0; var invalidations = 0
        for (request in listOf(MarketSavedUpdate("", false), MarketSavedUpdate("../other", true), MarketSavedUpdate(" ${id(1)}", true))) {
            val result = updateOwnedMarketSaved(Owner(), request, { invalidations++ }) { writes++; ok(page(1)) }
            assertTrue(result.negative)
        }
        val old = Owner().apply { active = false }
        updateOwnedMarketSaved(old, MarketSavedUpdate(id(1), true), { invalidations++ }) { writes++; ok(page(1)) }
        clearUnavailableOwnedMarketSaved(old, { invalidations++ }) { writes++; ok(page()) }
        assertEquals(0, writes); assertEquals(0, invalidations)
    }
    @Test fun uppercaseUuidIsNormalizedOnceWithoutChangingDesiredState() = runTest {
        val upper = "AAAAAAAA-AAAA-AAAA-AAAA-AAAAAAAAAAAA"
        val result = updateOwnedMarketSaved(Owner(), MarketSavedUpdate(upper, false)) {
            assertEquals(upper.lowercase(), it.offerId); assertFalse(it.saved); ok(page().copy(savedMutation = it))
        }
        assertFalse(result.negative)
    }
    @Test fun suspendedReplyCannotPublishIntoTheNextAccount() = runTest {
        val owner = Owner(); val started = CompletableDeferred<Unit>(); val reply = CompletableDeferred<Unit>()
        var invalidations = 0
        val work = async {
            updateOwnedMarketSaved(owner, MarketSavedUpdate(id(1), true), { invalidations++ }) {
                started.complete(Unit); reply.await(); ok(page(1))
            }
        }
        started.await(); owner.active = false; reply.complete(Unit)
        val result = work.await()
        assertTrue(result.negative); assertNull(result.payload); assertEquals(0, invalidations)
    }
    @Test fun suspendedClearCannotPublishIntoTheNextAccount() = runTest {
        val owner = Owner(); var invalidations = 0
        val result = clearUnavailableOwnedMarketSaved(owner, { invalidations++ }) { owner.active = false; ok(page()) }
        assertTrue(result.negative); assertNull(result.payload); assertEquals(0, invalidations)
    }
    @Test fun cancellationPropagatesAndDoesNotPretendTheWriteWasRejected() = runTest {
        var writes = 0; var invalidations = 0
        assertFailsWith<CancellationException> {
            updateOwnedMarketSaved(Owner(), MarketSavedUpdate(id(1), true), { invalidations++ }) {
                writes++; throw CancellationException("screen left after submission")
            }
        }
        assertEquals(1, writes); assertEquals(1, invalidations)
    }
    @Test fun validReadDoesNotMutateOrNeedANewWireFormat() = runTest {
        val expected = page(1).copy(unavailableSavedCount = 1)
        assertEquals(expected, readOwnedMarketSaved(Owner()) { ok(expected) }.payload)
    }
    @Test fun readsRejectMalformedPayloadFailuresAndObsoleteScopes() = runTest {
        for (response in listOf(ok(page(1, 1)), ok(page(1)).copy(httpStatusCode = 500),
            ok(page(1)).copy(negative = true), ok(page(1)).copy(transportFailure = true))) {
            val result = readOwnedMarketSaved(Owner()) { response }
            assertTrue(result.negative); assertNull(result.payload)
        }
        val owner = Owner(); var reads = 0
        val late = readOwnedMarketSaved(owner) { reads++; owner.active = false; ok(page(1)) }
        assertNull(late.payload)
        readOwnedMarketSaved(owner) { reads++; ok(page(1)) }
        assertEquals(1, reads)
    }
}
