package kz.aita

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class MarketOfferDetailTest {
    private fun id(n: Int) = "00000000-0000-0000-0000-${n.toString().padStart(12, '0')}"
    private val account = id(99)
    private val wanted = id(10)
    private val shop = MarketStorefront(id(20), "Shop", "Astana", "Pickup door", published = true, revision = 1)
    private fun offer() = MarketOffer(wanted, shop, "Tea", gtin = "04006381333931", priceMinor = 12345,
        currencyCode = "KZT", pricedAmount = 1.0, unitId = "piece", unitName = listOf(LocalizedStringDataModel("en", "piece")),
        availability = MARKET_AVAILABILITY_RECORDED, checkedAtMillis = 100, sourceUpdatedAtMillis = 90)
    private fun detail(value: MarketOffer = offer()) = MarketOfferDetailResult(account, value)
    private fun reply(value: MarketOfferDetailResult = detail()) = ResponseDataModel(null, value, false, 200)
    private class Owner(override val accountId: String, var current: Boolean = true) : MarketAccountScope {
        override val generation = 7L
        override fun isCurrent() = current
    }
    private fun valid(value: MarketOffer) = detail(value).isValidOfferDetailResult(account, wanted)
    private fun key(response: ResponseDataModel<*>) = response.message?.eventMessageReferenceOrNull()?.key

    @Test fun publicDetailIsBoundToExactAccountAndCanonicalOffer() {
        assertTrue(valid(offer()))
        assertFalse(detail().isValidOfferDetailResult("", wanted))
        assertFalse(detail().isValidOfferDetailResult(id(98), wanted))
        assertFalse(detail().isValidOfferDetailResult(account, id(11)))
        assertFalse(detail().isValidOfferDetailResult(account, "invalid"))
    }
    @Test fun unpublishedOrMalformedStorefrontCannotBecomeAValidDetail() {
        listOf(shop.copy(published = false), shop.copy(revision = 0), shop.copy(storeId = "private"),
            shop.copy(displayName = ""), shop.copy(city = ""), shop.copy(publicAddress = ""),
            shop.copy(displayName = "a".repeat(121)), shop.copy(city = "a".repeat(101)),
            shop.copy(publicAddress = "a".repeat(401)), shop.copy(pickupNote = "a".repeat(1001))).forEach {
            assertFalse(valid(offer().copy(storefront = it)), it.toString())
        }
    }
    @Test fun titlesDescriptionsAndTimestampsKeepTheirPublicBounds() {
        listOf(offer().copy(title = " "), offer().copy(title = "a".repeat(181)),
            offer().copy(description = "a".repeat(2001)), offer().copy(checkedAtMillis = 0),
            offer().copy(sourceUpdatedAtMillis = -1)).forEach { assertFalse(valid(it)) }
        assertTrue(valid(offer().copy(title = "a".repeat(180), description = "a".repeat(2000), sourceUpdatedAtMillis = 0)))
        // Inventory edits may originate from a clock ahead of the server. Do not compare clocks.
        assertTrue(valid(offer().copy(sourceUpdatedAtMillis = 1000)))
    }
    @Test fun canonicalBarcodeIsCheckedButUnidentifiedProductsRemainReadable() {
        assertTrue(valid(offer().copy(gtin = null)))
        listOf("4006381333931", "04006381333932", "00000000000000", "internal").forEach {
            assertFalse(valid(offer().copy(gtin = it)))
        }
    }
    @Test fun duplicateAndOversizedCategoryListsAreRejectedWithoutInventingATaxonomy() {
        assertFalse(valid(offer().copy(categoryIds = listOf(id(1), id(1)))))
        assertFalse(valid(offer().copy(categoryIds = (1..17).map(::id))))
        // Details do not load a taxonomy; keep existing legacy category labels as in other quotes.
        assertTrue(valid(offer().copy(categoryIds = listOf("legacy-tag", id(1)))))
    }
    @Test fun priceCannotBeNegativeUnboundedOrHaveAnInvalidCurrency() {
        listOf(-1L, 1_000_000_000_001L, Long.MAX_VALUE).forEach { assertFalse(valid(offer().copy(priceMinor = it))) }
        listOf(null, "kzt", "KZ", "KZTx").forEach { assertFalse(valid(offer().copy(currencyCode = it))) }
        assertTrue(valid(offer().copy(priceMinor = 0)))
        assertTrue(valid(offer().copy(priceMinor = 1_000_000_000_000L)))
    }
    @Test fun sellingAmountAndUnitMustBeFiniteAndUsable() {
        listOf(null, 0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach {
            assertFalse(valid(offer().copy(pricedAmount = it)))
        }
        listOf(null, "", " ", "a".repeat(129)).forEach { assertFalse(valid(offer().copy(unitId = it))) }
        assertFalse(valid(offer().copy(unitName = List(13) { LocalizedStringDataModel("en", "piece") })))
        assertFalse(valid(offer().copy(unitName = listOf(LocalizedStringDataModel("en", "a".repeat(121))))))
    }
    @Test fun unpricedOfferIsReadableButCannotClaimRecordedPricedAvailability() {
        val unpriced = offer().copy(priceMinor = null, currencyCode = null, pricedAmount = null,
            unitId = null, unitName = emptyList(), availability = MARKET_AVAILABILITY_CONFIRM)
        assertTrue(valid(unpriced))
        assertFalse(valid(unpriced.copy(currencyCode = "KZT")))
        assertFalse(valid(unpriced.copy(unitName = listOf(LocalizedStringDataModel("en", "piece")))))
        assertFalse(valid(unpriced.copy(availability = MARKET_AVAILABILITY_RECORDED)))
        assertFalse(valid(offer().copy(availability = "reserved")))
    }
    @Test fun staleOwnerAndInvalidIdNeverStartTheRead() = runTest {
        var calls = 0
        val read: suspend (String) -> ResponseDataModel<MarketOfferDetailResult> = { calls++; reply() }
        assertTrue(readOwnedMarketOfferDetail(Owner(account, false), wanted, read).negative)
        assertEquals(400, readOwnedMarketOfferDetail(Owner(account), "../seller", read).httpStatusCode)
        assertEquals(0, calls)
    }
    @Test fun validReadNormalizesTheIdAndUsesExactlyOneRequest() = runTest {
        val upperId = "abcdef01-abcd-abcd-abcd-abcdef012345"
        var calls = 0
        val result = readOwnedMarketOfferDetail(Owner(account), upperId.uppercase()) { sent ->
            calls++; assertEquals(upperId, sent); reply(detail(offer().copy(id = upperId)))
        }
        assertFalse(result.negative); assertEquals(upperId, result.payload?.offer?.id); assertEquals(1, calls)
    }
    @Test fun anotherAccountsSavedMarkerCannotCrossAValidSession() = runTest {
        val result = readOwnedMarketOfferDetail(Owner(account), wanted) { reply(detail().copy(accountId = id(98))) }
        assertTrue(result.negative); assertNull(result.payload); assertEquals(502, result.httpStatusCode)
    }
    @Test fun ownerChangeWhileSuspendedDiscardsTheWholePayload() = runTest {
        val owner = Owner(account)
        val started = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val pending = async(start = CoroutineStart.UNDISPATCHED) {
            readOwnedMarketOfferDetail(owner, wanted) { started.complete(Unit); release.await(); reply() }
        }
        started.await(); owner.current = false; release.complete(Unit)
        val result = pending.await(); assertTrue(result.negative); assertNull(result.payload)
    }
    @Test fun wrongOfferCannotBeAcceptedByAnOtherwiseValidEnvelope() = runTest {
        val result = readOwnedMarketOfferDetail(Owner(account), wanted) { reply(detail(offer().copy(id = id(11)))) }
        assertEquals(502, result.httpStatusCode); assertNull(result.payload)
    }
    @Test fun malformedSuccessIsAnErrorRatherThanAnEmptyOrFreshCard() = runTest {
        for (value in listOf<MarketOfferDetailResult?>(null, detail(offer().copy(storefront = shop.copy(published = false))))) {
            val result = readOwnedMarketOfferDetail(Owner(account), wanted) { reply().copy(payload = value) }
            assertTrue(result.negative); assertNull(result.payload); assertEquals("market.detail_failed", key(result))
        }
    }
    @Test fun httpFailureCannotHideBehindANonnegativeFlagAndPlausiblePayload() = runTest {
        for (status in listOf(null, 201, 204, 304, 401, 403, 429, 500, 503)) {
            val result = readOwnedMarketOfferDetail(Owner(account), wanted) { reply().copy(httpStatusCode = status) }
            assertTrue(result.negative); assertNull(result.payload)
        }
    }
    @Test fun negativeRepliesDropTheirPayloadAndKeepTheError() = runTest {
        val message = eventMessage("market.detail_busy")
        val result = readOwnedMarketOfferDetail(Owner(account), wanted) { reply().copy(message = message, negative = true, httpStatusCode = 429) }
        assertNull(result.payload); assertEquals(429, result.httpStatusCode); assertEquals(message, result.message)
    }
    @Test fun withdrawnOfferAndAbsentEndpointHaveDifferentRecoveryMessages() = runTest {
        val withdrawn = readOwnedMarketOfferDetail(Owner(account), wanted) {
            reply().copy(message = eventMessage("market.unavailable"), httpStatusCode = 404, negative = true)
        }
        assertEquals("market.unavailable", key(withdrawn)); assertNull(withdrawn.payload)
        val legacy = readOwnedMarketOfferDetail(Owner(account), wanted) { reply().copy(httpStatusCode = 404) }
        assertEquals("market.detail_upgrade", key(legacy)); assertTrue(legacy.negative); assertNull(legacy.payload)
    }
    @Test fun transportFailureIsNotMisreportedAsAnUpgradeOrWithdrawal() = runTest {
        val result = readOwnedMarketOfferDetail(Owner(account), wanted) { reply().copy(transportFailure = true, httpStatusCode = 404) }
        assertTrue(result.transportFailure); assertTrue(result.negative); assertNull(result.payload)
        assertEquals("market.detail_failed", key(result))
    }
    @Test fun cancellationIsNeverConvertedIntoAFreshCardOrAnErrorResponse() = runTest {
        assertFailsWith<CancellationException> {
            readOwnedMarketOfferDetail(Owner(account), wanted) { throw CancellationException("cancelled") }
        }
    }
    @Test fun cancellationAlreadyRequestedBeforeTheReadDoesNotCallTransport() = runTest {
        var calls = 0; var rejected = false
        val task = launch(start = CoroutineStart.UNDISPATCHED) {
            currentCoroutineContext().cancel()
            try { readOwnedMarketOfferDetail(Owner(account), wanted) { calls++; reply() } }
            catch (_: CancellationException) { rejected = true }
        }
        task.join(); assertEquals(0, calls); assertTrue(rejected)
    }
    @Test fun cancellationInsideANoncooperativeTransportStillRejectsItsReply() = runTest {
        var rejected = false
        val task = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                readOwnedMarketOfferDetail(Owner(account), wanted) { currentCoroutineContext().cancel(); reply() }
            } catch (_: CancellationException) { rejected = true }
        }
        task.join(); assertTrue(rejected)
    }
    @Test fun thrownTransportFailureDoesNotCauseAnAutomaticRetry() = runTest {
        var calls = 0
        assertFailsWith<IllegalStateException> {
            readOwnedMarketOfferDetail(Owner(account), wanted) { calls++; error("offline") }
        }
        assertEquals(1, calls)
    }
    @Test fun currentReadNeedsTheSameOfferAndMarketplaceRevision() {
        val fence = MarketOfferDetailReadFence(); val stamp = fence.capture(wanted, 4)
        assertTrue(fence.isCurrent(stamp, wanted, 4))
        assertFalse(fence.isCurrent(stamp, id(11), 4)); assertFalse(fence.isCurrent(stamp, wanted, 5))
        assertFalse(fence.isCurrent(null, wanted, 4))
    }
    @Test fun queuedLocalRefreshRetiresTheOldReadBeforeTheChannelIsConsumed() {
        val fence = MarketOfferDetailReadFence(); val stamp = fence.capture(wanted, 4)
        fence.invalidate()
        assertFalse(fence.isCurrent(stamp, wanted, 4))
        assertFalse(fence.canUse(stamp, wanted, 4, 0, false))
        assertTrue(fence.isCurrent(fence.capture(wanted, 4), wanted, 4))
    }
    @Test fun coalescedRefreshesDoNotResurrectAnEarlierToken() {
        val fence = MarketOfferDetailReadFence(); val original = fence.capture(wanted, 4)
        repeat(1000) { fence.invalidate() }
        val latest = fence.capture(wanted, 4)
        assertFalse(fence.isCurrent(original, wanted, 4)); assertTrue(fence.isCurrent(latest, wanted, 4))
    }
    @Test fun tokenFromAnotherDialogCannotBecomeCurrentEvenAtTheSameRevision() {
        val first = MarketOfferDetailReadFence(); val second = MarketOfferDetailReadFence()
        assertFalse(second.isCurrent(first.capture(wanted, 4), wanted, 4))
    }
    @Test fun refreshedReplyMustNotBecomeCurrentWhenAnotherRefreshArrivesDuringIo() = runTest {
        val fence = MarketOfferDetailReadFence(); val stamp = fence.capture(wanted, 4)
        val release = CompletableDeferred<Unit>()
        val pending = async(start = CoroutineStart.UNDISPATCHED) {
            readOwnedMarketOfferDetail(Owner(account), wanted) { release.await(); reply() }
        }
        fence.invalidate(); release.complete(Unit)
        assertFalse(pending.await().negative) // Valid wire data, but not the current requested read.
        assertFalse(fence.isCurrent(stamp, wanted, 4))
    }
    @Test fun freshnessExpiresAtThirtySecondsEvenWithoutATimerCallback() {
        val fence = MarketOfferDetailReadFence(); val stamp = fence.capture(wanted, 4)
        assertTrue(fence.canUse(stamp, wanted, 4, 0, false))
        assertTrue(fence.canUse(stamp, wanted, 4, 29_999, false))
        for (age in listOf(-1L, 30_000L, Long.MAX_VALUE)) assertFalse(fence.canUse(stamp, wanted, 4, age, false))
    }
    @Test fun loadingAlwaysDisablesUseEvenWhenThePreviousReadWasRecent() {
        val fence = MarketOfferDetailReadFence(); val stamp = fence.capture(wanted, 4)
        assertFalse(fence.canUse(stamp, wanted, 4, 1, true))
    }
}
