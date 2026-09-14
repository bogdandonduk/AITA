package kz.aita

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/** Pure wire-policy and suspended-read tests. No device, network or database is needed. */
class MarketDiscoveryReadTest {
    private fun id(n: Int) = "00000000-0000-0000-0000-${n.toString().padStart(12, '0')}"
    private val account = id(99)
    private val shop = MarketStorefront(id(20), "Shop", "Astana", "Door 1", "Collect here", true, 2)
    private val catalogue = MarketCategoryCatalogue("a".repeat(64), emptyList())
    private val request = MarketDiscoveryRequest(MarketDiscoveryQuery(storefrontId = shop.storeId))
    private fun offer(n: Int = 10) = MarketOffer(id(n), shop, "Tea", checkedAtMillis = 100, sourceUpdatedAtMillis = 90)
    private fun result(wanted: MarketDiscoveryRequest = request) = MarketDiscoveryResult(
        requireNotNull(wanted.query.normalizedDiscoveryQuery()), wanted.limit,
        MarketPage(listOf(offer().copy(saved = wanted.query.savedOnly)), checkedAtMillis = 100), 1, 1,
        catalogue.version, catalogue.categories, account, shop.takeIf { wanted.query.storefrontId != null })
    private fun checked(value: MarketDiscoveryResult, wanted: MarketDiscoveryRequest = request) =
        value.validatedDiscovery(wanted, null, account)
    private class Owner(override val accountId: String, var current: Boolean = true) : MarketAccountScope {
        override val generation = 7L
        override fun isCurrent() = current
    }
    private fun reply(value: MarketDiscoveryResult = result()) = ResponseDataModel(null, value, false, 200)

    @Test fun newHeaderAndAllCardsMustBeTheExactSamePublicRevision() {
        assertNotNull(checked(result()))
        listOf(shop.copy(publicAddress = "Door 2"), shop.copy(displayName = "Renamed"), shop.copy(revision = 3),
            shop.copy(pickupNote = "Call first"), shop.copy(storeId = id(21))).forEach { header ->
            assertNull(checked(result().copy(storefront = header)))
        }
    }
    @Test fun emptyPublishedShopStillCarriesItsHeaderAndAccount() {
        val empty = result().copy(page = MarketPage(checkedAtMillis = 100), totalOffers = 0, totalShops = 0)
        assertEquals(shop, checked(empty)?.result?.storefront)
        assertNull(checked(empty.copy(storefront = null)))
        assertNull(checked(empty.copy(storefront = shop.copy(published = false))))
        assertNull(checked(empty.copy(totalShops = 1)))
    }
    @Test fun legacyAbsentOwnerAndDifferentAccountCannotSupplySavedMarkers() {
        assertNull(checked(result().copy(accountId = null)))
        assertNull(checked(result().copy(accountId = id(98))))
        assertNull(result().validatedDiscovery(request, null, ""))
    }
    @Test fun unscopedDiscoveryCannotSmuggleAnUnrelatedHeader() {
        val wanted = MarketDiscoveryRequest(MarketDiscoveryQuery())
        assertNotNull(checked(result(wanted), wanted))
        assertNull(checked(result(wanted).copy(storefront = shop), wanted))
    }
    @Test fun cardsFromOneShopCannotDisagreeAboutItsPickupInformation() {
        val wanted = MarketDiscoveryRequest(MarketDiscoveryQuery())
        val value = result(wanted).copy(totalOffers = 2, page = MarketPage(listOf(offer(), offer(11)), checkedAtMillis = 100))
        assertNotNull(checked(value, wanted))
        assertNull(checked(value.copy(page = value.page.copy(offers = listOf(offer(),
            offer(11).copy(storefront = shop.copy(publicAddress = "Different address"))))), wanted))
    }
    @Test fun publicHeaderBoundsAndRequiredFieldsAreEnforcedEvenWithNoOffers() {
        val empty = result().copy(page = MarketPage(checkedAtMillis = 100), totalOffers = 0, totalShops = 0)
        listOf(shop.copy(revision = 0), shop.copy(displayName = ""), shop.copy(city = ""), shop.copy(publicAddress = ""),
            shop.copy(displayName = "a".repeat(121)), shop.copy(city = "a".repeat(101)),
            shop.copy(publicAddress = "a".repeat(401)), shop.copy(pickupNote = "a".repeat(1001))).forEach {
            assertNull(checked(empty.copy(storefront = it)))
        }
    }
    @Test fun cityMatchIsCheckedAgainstEachReturnedShop() {
        val wanted = MarketDiscoveryRequest(MarketDiscoveryQuery(city = "aStAnA"))
        assertNotNull(checked(result(wanted), wanted))
        val wrongCity = result(wanted).copy(page = MarketPage(listOf(offer().copy(storefront = shop.copy(city = "Almaty"))), checkedAtMillis = 100))
        assertNull(checked(wrongCity, wanted))
    }
    @Test fun offerDescriptionAndCanonicalBarcodeCannotBypassCatalogueValidation() {
        fun withOffer(value: MarketOffer) = result().copy(page = MarketPage(listOf(value), checkedAtMillis = 100))
        assertNotNull(checked(withOffer(offer().copy(gtin = "04006381333931"))))
        assertNull(checked(withOffer(offer().copy(gtin = "4006381333931"))))
        assertNull(checked(withOffer(offer().copy(gtin = "not-a-barcode"))))
        assertNull(checked(withOffer(offer().copy(description = "a".repeat(2001)))))
        assertNotNull(checked(withOffer(offer().copy(description = "a".repeat(2000)))))
    }
    @Test fun bookmarkMutationRepliesCannotMasqueradeAsCatalogueReads() {
        val value = result()
        assertNull(checked(value.copy(page = value.page.copy(savedMutation = MarketSavedUpdate(id(10), true)))))
        assertNull(checked(value.copy(page = value.page.copy(unavailableSavedCleared = true))))
    }
    @Test fun savedTotalsAndUnavailableCountsRespectTheSingleAccountBound() {
        val wanted = MarketDiscoveryRequest(MarketDiscoveryQuery(savedOnly = true))
        val value = result(wanted)
        assertNotNull(checked(value.copy(page = value.page.copy(unavailableSavedCount = 99)), wanted))
        assertNull(checked(value.copy(page = value.page.copy(unavailableSavedCount = 100)), wanted))
        assertNull(checked(value.copy(page = value.page.copy(unavailableSavedCount = 101)), wanted))
    }
    @Test fun positiveOfferCountMustIdentifyAtLeastOneShop() {
        val wanted = MarketDiscoveryRequest(MarketDiscoveryQuery())
        assertNull(checked(result(wanted).copy(totalShops = 0), wanted))
    }
    @Test fun currentWindowRequiresExactFiltersLimitAndInvalidationRevision() {
        val value = result()
        assertTrue(value.matchesDiscoveryRead(request, 4, 4))
        assertFalse(value.matchesDiscoveryRead(request.copy(limit = 80), 4, 4))
        assertFalse(value.matchesDiscoveryRead(request.copy(query = request.query.copy(text = "other")), 4, 4))
        assertFalse(value.matchesDiscoveryRead(request, 4, 5))
        assertFalse(value.matchesDiscoveryRead(request, 4, 4, refreshQueued = true))
        assertFalse(value.matchesDiscoveryRead(request, -1, -1))
    }
    @Test fun aNewTaxonomyVersionIsNotConfusedWithAWindowChange() {
        assertTrue(result().matchesDiscoveryRead(request.copy(knownCategoryVersion = "b".repeat(64)), 5, 5))
        assertFalse(result().matchesDiscoveryRead(request.copy(limit = 401), 5, 5))
    }
    @Test fun validReadNormalizesOnceAndReturnsVerifiedTaxonomyTogether() = runTest {
        var calls = 0
        val raw = MarketDiscoveryRequest(MarketDiscoveryQuery(text = " green\t tea ", city = " Astana "))
        val response = readOwnedMarketDiscovery(Owner(account), raw, null) { sent ->
            calls++
            assertEquals("green tea", sent.query.text); assertEquals("Astana", sent.query.city)
            reply(result(sent))
        }
        assertEquals(1, calls); assertFalse(response.negative)
        assertEquals(catalogue, response.payload?.catalogue)
        assertEquals(account, response.payload?.result?.accountId)
    }
    @Test fun absentTaxonomyRequiresTheExactCachedRevision() = runTest {
        val wanted = request.copy(knownCategoryVersion = catalogue.version)
        val value = result(wanted).copy(categories = null)
        assertFalse(readOwnedMarketDiscovery(Owner(account), wanted, catalogue) { reply(value) }.negative)
        assertTrue(readOwnedMarketDiscovery(Owner(account), wanted, null) { reply(value) }.negative)
        assertTrue(readOwnedMarketDiscovery(Owner(account), wanted, catalogue.copy(version = "b".repeat(64))) { reply(value) }.negative)
    }
    @Test fun obsoleteOwnerNeverStartsIo() = runTest {
        val response = readOwnedMarketDiscovery(Owner(account, false), request, null) { error("must not read") }
        assertTrue(response.negative); assertNull(response.payload)
    }
    @Test fun lateResponseCannotCrossAnAccountOrSessionChange() = runTest {
        val owner = Owner(account)
        val response = readOwnedMarketDiscovery(owner, request, null) { owner.current = false; reply() }
        assertNull(response.payload); assertTrue(response.negative)
    }
    @Test fun mismatchedWireAccountIsRejectedEvenWhileLocalOwnerIsCurrent() = runTest {
        val response = readOwnedMarketDiscovery(Owner(account), request, null) { reply(result().copy(accountId = id(98))) }
        assertTrue(response.negative); assertNull(response.payload); assertEquals(502, response.httpStatusCode)
    }
    @Test fun malformedRequestNeverReachesTransport() = runTest {
        val response = readOwnedMarketDiscovery(Owner(account), request.copy(limit = 39), null) { error("must not read") }
        assertEquals(400, response.httpStatusCode); assertNull(response.payload)
    }
    @Test fun legacySuccessRequiresAnUpgradeRatherThanBorrowingASeparateHeader() = runTest {
        var calls = 0
        val response = readOwnedMarketDiscovery(Owner(account), request, null) {
            calls++; reply(result().copy(accountId = null, storefront = null))
        }
        assertEquals(1, calls); assertNull(response.payload)
        assertEquals("market.discovery_scope_upgrade", response.message?.eventMessageReferenceOrNull()?.key)
    }
    @Test fun withdrawnShopIsNotReportedAsAMissingEndpoint() = runTest {
        val response = readOwnedMarketDiscovery(Owner(account), request, null) {
            ResponseDataModel(eventMessage("market.shop_unavailable"), null, true, 404)
        }
        assertEquals("market.shop_unavailable", response.message?.eventMessageReferenceOrNull()?.key)
        assertEquals(404, response.httpStatusCode); assertNull(response.payload)
    }
    @Test fun absentEndpointDoesNotFallBackToLegacyUnfilteredBrowse() = runTest {
        var calls = 0
        val response = readOwnedMarketDiscovery(Owner(account), request, null) {
            calls++; ResponseDataModel(null, null, true, 404)
        }
        assertEquals(1, calls); assertNull(response.payload)
        assertEquals("market.discovery_scope_upgrade", response.message?.eventMessageReferenceOrNull()?.key)
    }
    @Test fun non200AndTransportFailuresCannotPublishSuccessfulLookingPayloads() = runTest {
        listOf<Int?>(null, 201, 204, 304, 500).forEach { code ->
            val response = readOwnedMarketDiscovery(Owner(account), request, null) { reply().copy(httpStatusCode = code) }
            assertTrue(response.negative); assertNull(response.payload)
        }
        val response = readOwnedMarketDiscovery(Owner(account), request, null) { reply().copy(transportFailure = true) }
        assertTrue(response.transportFailure); assertNull(response.payload); assertTrue(response.negative)
    }
    @Test fun negativeReplyDiscardsPayloadAndRetainsTheServerExplanation() = runTest {
        val response = readOwnedMarketDiscovery(Owner(account), request, null) {
            reply().copy(negative = true, httpStatusCode = 429, message = eventMessage("market.discovery_busy"))
        }
        assertEquals(429, response.httpStatusCode); assertNull(response.payload)
        assertEquals("market.discovery_busy", response.message?.eventMessageReferenceOrNull()?.key)
    }
    @Test fun cancelledReadIsNotConvertedIntoAnEmptySuccessfulCatalogue() = runTest {
        assertFailsWith<CancellationException> {
            readOwnedMarketDiscovery(Owner(account), request, null) { throw CancellationException("closed screen") }
        }
    }
    @Test fun nonCooperativeTransportCannotPublishAfterCancellation() = runTest {
        var published = false
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            readOwnedMarketDiscovery(Owner(account), request, null) {
                currentCoroutineContext().cancel(); reply()
            }
            published = true
        }
        job.join()
        assertTrue(job.isCancelled); assertFalse(published)
    }
    @Test fun transportExceptionIsPropagatedWithoutAnAutomaticRetry() = runTest {
        var calls = 0
        assertFailsWith<IllegalStateException> {
            readOwnedMarketDiscovery(Owner(account), request, null) { calls++; error("offline") }
        }
        assertEquals(1, calls)
    }
}
