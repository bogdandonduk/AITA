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

class MarketShopDirectorySafetyTest {
    private val account = "00000000-0000-0000-0000-000000000099"
    private val shop = MarketStorefront("00000000-0000-0000-0000-000000000001", "Neighbour Shop", "Astana",
        "Pickup door 50% _lane", published = true, revision = 1)
    private val wanted = MarketShopDirectoryRequest("shop door", "ast")
    private val entry = MarketShopDirectoryEntry(shop, 3)
    private fun page(request: MarketShopDirectoryRequest = wanted, row: MarketShopDirectoryEntry = entry) =
        MarketShopDirectoryResult(account, request, listOf(row), 1, 100)
    private fun ok(value: MarketShopDirectoryResult = page()) = ResponseDataModel(null, value, false, 200)
    private class Owner(override val accountId: String, var active: Boolean = true) : MarketAccountScope {
        override val generation = 1L
        override fun isCurrent() = active
    }
    private fun valid(value: MarketShopDirectoryResult, request: MarketShopDirectoryRequest = wanted) =
        value.isValidShopDirectoryResult(account, request)
    private fun key(value: ResponseDataModel<*>) = value.message?.eventMessageReferenceOrNull()?.key

    @Test fun echoedCityDoesNotValidateAShopFromAnotherCity() {
        assertFalse(valid(page(row = entry.copy(storefront = shop.copy(city = "Almaty")))))
    }
    @Test fun cityFilterIsACaseInsensitiveSubstringNotAnExactCityName() {
        assertTrue(valid(page()))
        assertTrue(valid(page(wanted.copy(city = "STANA")), wanted.copy(city = "STANA")))
    }
    @Test fun everySearchTermMustMatchTheNameOrPublicAddress() {
        assertTrue(valid(page())) // One term in the name, the other in the address.
        assertFalse(valid(page(row = entry.copy(storefront = shop.copy(displayName = "Market")))))
        assertFalse(valid(page(row = entry.copy(storefront = shop.copy(publicAddress = "Street 4")))))
    }
    @Test fun cyrillicAndUzbekLatinTextMatchWithoutAsciiOnlyCaseConversion() {
        val examples = listOf(
            Triple("Дүкөн эшик", "Бишкек", MarketShopDirectoryRequest("дүкөн ЭШИК", "БИШ")),
            Triple("Мағоза дар", "Душанбе", MarketShopDirectoryRequest("мағоза ДАР", "ДУШАНБЕ")),
            Triple("Do‘kon eshik", "Toshkent", MarketShopDirectoryRequest("do‘kon ESHIK", "TOSHKENT")),
            Triple("Дүкен есік", "Астана", MarketShopDirectoryRequest("дүкен ЕСІК", "АСТ"))
        )
        for ((name, city, request) in examples) {
            assertTrue(valid(page(request, entry.copy(storefront = shop.copy(displayName = name, city = city))), request))
        }
    }
    @Test fun pickupNoteDoesNotSilentlyBroadenTheSearchableFields() {
        assertFalse(valid(page(row = entry.copy(storefront = shop.copy(publicAddress = "Street 4", pickupNote = "door")))))
    }
    @Test fun wildcardPunctuationStaysLiteral() {
        val request = MarketShopDirectoryRequest("50% _lane", "Astana")
        assertTrue(valid(page(request), request))
        assertFalse(valid(page(request, entry.copy(storefront = shop.copy(publicAddress = "50percent Xlane"))), request))
    }
    @Test fun cityWildcardPunctuationIsNotTreatedAsSqlWildcards() {
        val request = wanted.copy(city = "%_")
        assertFalse(valid(page(request), request))
        assertTrue(valid(page(request, entry.copy(storefront = shop.copy(city = "District %_"))), request))
    }
    @Test fun blankAccountAndWrongRequestCannotBeAccepted() {
        assertFalse(page().copy(accountId = "").isValidShopDirectoryResult("", wanted))
        assertFalse(valid(page(wanted.copy(limit = 40))))
    }
    @Test fun emptyPublicShopsAndEmptyResultSetsRemainValid() {
        assertTrue(valid(page(row = entry.copy(publishedOffers = 0))))
        assertTrue(valid(page().copy(shops = emptyList(), totalShops = 0)))
    }
    @Test fun rawWhitespaceIsNormalizedWithoutChangingTheMeaning() = runTest {
        var calls = 0
        val response = readOwnedMarketShopDirectory(Owner(account), wanted.copy(text = " shop\t door\n", city = " ast ")) {
            calls++; assertEquals(wanted, it); ok()
        }
        assertFalse(response.negative); assertEquals(1, calls)
    }
    @Test fun errorStatusCannotHideBehindAValidPayloadAndNonnegativeFlag() = runTest {
        for (status in listOf(null, 201, 204, 304, 401, 403, 429, 500, 503)) {
            val response = readOwnedMarketShopDirectory(Owner(account), wanted) { ok().copy(httpStatusCode = status) }
            assertTrue(response.negative, "$status"); assertNull(response.payload)
            assertEquals("market.shops_failed", key(response))
        }
    }
    @Test fun transportFailureCannotLookLikeAnUpgradeOrACompletedRead() = runTest {
        for (status in listOf(200, 404)) {
            val response = readOwnedMarketShopDirectory(Owner(account), wanted) { ok().copy(transportFailure = true, httpStatusCode = status) }
            assertTrue(response.negative); assertTrue(response.transportFailure); assertNull(response.payload)
            assertEquals("market.shops_failed", key(response))
        }
    }
    @Test fun validOldProtocolStillWorksWithoutAnyNewEndpoint() = runTest {
        val response = readOwnedMarketShopDirectory(Owner(account), wanted) { ok(page().copy(protocolVersion = 1)) }
        assertFalse(response.negative); assertEquals(page(), response.payload)
    }
    @Test fun absentRouteIsStillAnExplicitUpgradeWithoutFallbackReads() = runTest {
        var calls = 0
        val response = readOwnedMarketShopDirectory(Owner(account), wanted) { calls++; ok().copy(httpStatusCode = 404) }
        assertEquals(1, calls); assertTrue(response.negative); assertNull(response.payload)
        assertEquals("market.shops_upgrade", key(response))
    }
    @Test fun negativeResponseDropsAllRowsAndRetainsItsMessage() = runTest {
        val error = eventMessage("market.shops_busy")
        val response = readOwnedMarketShopDirectory(Owner(account), wanted) { ok().copy(negative = true, message = error, httpStatusCode = 429) }
        assertEquals(error, response.message); assertEquals(429, response.httpStatusCode); assertNull(response.payload)
    }
    @Test fun missingNegativeMessageReceivesUsableFeedback() = runTest {
        val response = readOwnedMarketShopDirectory(Owner(account), wanted) { ok().copy(negative = true) }
        assertNull(response.payload); assertEquals("market.shops_failed", key(response))
    }
    @Test fun ownerChangingDuringAnActualSuspensionDiscardsTheReply() = runTest {
        val owner = Owner(account); val release = CompletableDeferred<Unit>()
        val pending = async(start = CoroutineStart.UNDISPATCHED) {
            readOwnedMarketShopDirectory(owner, wanted) { release.await(); ok() }
        }
        owner.active = false; release.complete(Unit)
        assertNull(pending.await().payload)
    }
    @Test fun cancelledReadNeverReturnsAPayloadEvenWhenTransportSwallowsCancellation() = runTest {
        var published = false
        val child = launch(start = CoroutineStart.UNDISPATCHED) {
            readOwnedMarketShopDirectory(Owner(account), wanted) { currentCoroutineContext().cancel(); ok() }
            published = true
        }
        child.join(); assertTrue(child.isCancelled); assertFalse(published)
    }
    @Test fun alreadyCancelledCallerDoesNotStartTransport() = runTest {
        var calls = 0
        val child = launch(start = CoroutineStart.UNDISPATCHED) {
            currentCoroutineContext().cancel()
            readOwnedMarketShopDirectory(Owner(account), wanted) { calls++; ok() }
        }
        child.join(); assertEquals(0, calls)
    }
    @Test fun thrownCancellationIsNotConvertedToAnErrorReply() = runTest {
        assertFailsWith<CancellationException> {
            readOwnedMarketShopDirectory(Owner(account), wanted) { throw CancellationException("cancelled") }
        }
    }
    @Test fun invalidInputsAndObsoleteOwnersMakeNoRequests() = runTest {
        var calls = 0
        val read: suspend (MarketShopDirectoryRequest) -> ResponseDataModel<MarketShopDirectoryResult> = { calls++; ok() }
        readOwnedMarketShopDirectory(Owner(account, false), wanted, read)
        readOwnedMarketShopDirectory(Owner(account), wanted.copy(limit = 201), read)
        assertEquals(0, calls)
    }
    @Test fun incompleteDuplicateOrWrongAccountPayloadsRemainRejected() {
        assertFalse(valid(page().copy(accountId = "other")))
        assertFalse(valid(page().copy(totalShops = 20)))
        assertFalse(valid(page().copy(totalShops = 2, shops = listOf(entry, entry))))
    }
    @Test fun refreshRetiresAlreadyDisplayedAndInFlightStampsImmediately() {
        val fence = MarketShopDirectoryReadFence(); val old = fence.capture(wanted, 3)
        assertTrue(fence.canUse(old, page(), wanted, 3, 0, false))
        fence.invalidate()
        assertFalse(fence.canUse(old, page(), wanted, 3, 1, false))
        assertFalse(fence.isCurrent(old, wanted, 3))
    }
    @Test fun returningToTheSameSearchDoesNotReviveItsOldStamp() {
        val fence = MarketShopDirectoryReadFence(); val old = fence.capture(wanted, 3)
        fence.invalidate(); fence.capture(wanted.copy(city = "Almaty"), 3); fence.invalidate()
        assertFalse(fence.isCurrent(old, wanted, 3))
        assertTrue(fence.isCurrent(fence.capture(wanted, 3), wanted, 3))
    }
    @Test fun largerWindowAndSignalsInvalidateBeforeAnyComposeEffectRuns() {
        val fence = MarketShopDirectoryReadFence(); val old = fence.capture(wanted, 3)
        assertFalse(fence.isCurrent(old, wanted.copy(limit = 40), 3))
        assertFalse(fence.isCurrent(old, wanted, 4))
    }
    @Test fun manyRefreshesLeaveOnlyTheLatestStampCurrent() {
        val fence = MarketShopDirectoryReadFence(); val first = fence.capture(wanted, 3)
        repeat(10_000) { fence.invalidate() }
        val current = fence.capture(wanted, 3)
        assertFalse(fence.isCurrent(first, wanted, 3)); assertTrue(fence.isCurrent(current, wanted, 3))
    }
    @Test fun tokenFromDisposedViewCannotBecomeCurrentInANewView() {
        val old = MarketShopDirectoryReadFence().capture(wanted, 3)
        assertFalse(MarketShopDirectoryReadFence().isCurrent(old, wanted, 3))
    }
    @Test fun staleSuccessAndStaleFailureUseTheSameGenerationCheck() = runTest {
        for (negative in listOf(false, true)) {
            val fence = MarketShopDirectoryReadFence(); val stamp = fence.capture(wanted, 3)
            val release = CompletableDeferred<Unit>()
            val pending = async(start = CoroutineStart.UNDISPATCHED) {
                readOwnedMarketShopDirectory(Owner(account), wanted) { release.await(); ok().copy(negative = negative) }
            }
            fence.invalidate(); release.complete(Unit); pending.await()
            assertFalse(fence.isCurrent(stamp, wanted, 3))
        }
    }
    @Test fun wallClockDatesCannotKeepADirectoryFreshBeyondThirtySeconds() {
        val fence = MarketShopDirectoryReadFence(); val stamp = fence.capture(wanted, 3)
        assertTrue(fence.canUse(stamp, page(), wanted, 3, 29_999, false))
        for (age in listOf(-1L, 30_000L, Long.MAX_VALUE)) {
            assertFalse(fence.canUse(stamp, page().copy(checkedAtMillis = Long.MAX_VALUE), wanted, 3, age, false))
        }
    }
    @Test fun busyInputErrorAndOwnershipGuardsDisableUse() {
        val fence = MarketShopDirectoryReadFence(); val stamp = fence.capture(wanted, 3)
        assertFalse(fence.canUse(stamp, page(), wanted, 3, 1, true))
        assertFalse(fence.canUse(null, page(), wanted, 3, 1, false))
        assertFalse(fence.canUse(stamp, null, wanted, 3, 1, false))
    }
    @Test fun retainedCardMustExactlyMatchTheCurrentDisplayedEntry() {
        val fence = MarketShopDirectoryReadFence(); val stamp = fence.capture(wanted, 3)
        assertTrue(fence.canVisit(stamp, page(), entry, wanted, 3, 1, false))
        assertFalse(fence.canVisit(stamp, page(row = entry.copy(publishedOffers = 4)), entry, wanted, 3, 1, false))
        assertFalse(fence.canVisit(stamp, page(row = entry.copy(storefront = shop.copy(publicAddress = "Different door"))), entry, wanted, 3, 1, false))
        assertFalse(fence.canVisit(stamp, page().copy(shops = emptyList(), totalShops = 0), entry, wanted, 3, 1, false))
    }
    @Test fun stampNormalizationDoesNotRejectEquivalentWhitespace() {
        val fence = MarketShopDirectoryReadFence(); val stamp = fence.capture(wanted.copy(city = " ast\t"), 3)
        assertTrue(fence.isCurrent(stamp, wanted, 3))
        assertFalse(fence.isCurrent(stamp, wanted.copy(limit = 21), 3))
    }
}
