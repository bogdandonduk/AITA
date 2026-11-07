package kz.aita

import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString

class MarketPriceInsightsTest {
    private val account = "11111111-1111-4111-8111-111111111111"
    private val store = "22222222-2222-4222-8222-222222222222"
    private val item = "33333333-3333-4333-8333-333333333333"
    private val code = "04006381333931"
    private val basis = MarketShoppingBasis(code, "KZT", "0", 1.0)
    private val now = 1_000_000L
    private val request = MarketPriceRequest()
    private fun row() = MarketPriceInsightRow(item, listOf(LocalizedStringDataModel("ru", "Товар")), listOf(code),
        basis, ownPriceMinor = 12_000, referenceKind = "shelf")
    private fun offer(price: Long = 10_000) = MarketOffer("public-offer", MarketStorefront("public-shop", published = true),
        "Public product", gtin = code, priceMinor = price, currencyCode = "KZT", pricedAmount = 1.0, unitId = "0",
        availability = MARKET_AVAILABILITY_RECORDED, checkedAtMillis = now, sourceUpdatedAtMillis = 1)
    private fun page(time: Long = now) = MarketPriceInsightsPage(account, store, request,
        listOf(row()), totalItems = 1, checkedAtMillis = time)
    private fun sample(prices: List<Long>) = prices.mapIndexed { i, price -> MarketPriceObservation("group-$i", offer(price)) }
    private fun checkdigit(prefix: String): String {
        val sum = prefix.reversed().mapIndexed { index, c -> (c - '0') * if (index % 2 == 0) 3 else 1 }.sum()
        return prefix + (10 - sum % 10) % 10
    }

    @Test fun onlyVerifiedGlobalProductCodesMayBeCompared() {
        assertEquals(code, marketInsightGtin("4006381333931"))
        assertEquals(code, marketInsightGtin(code))
        for (prefix in listOf("200001123456", "290001123456", "020001123456", "040001123456", "0123456", "2123456")) {
            val value = checkdigit(prefix)
            assertNotNull(marketCanonicalGtin(value))
            assertNull(marketInsightGtin(value), value)
            assertNull(marketInsightGtin(value.padStart(14, '0')), value)
        }
        for (value in listOf("123", "00000000000000", "4006381333932", "400638X333931")) assertNull(marketInsightGtin(value))
    }
    @Test fun gramsAndKilogramsMatchOnlyAtTheSameSellingQuantity() {
        val kg = basis.copy(unitId = "1", pricedAmount = 1.0)
        assertTrue(marketPriceBasisMatches(kg, basis.copy(unitId = "g", pricedAmount = 1000.0)))
        assertTrue(marketPriceBasisMatches(kg.copy(pricedAmount = .5), basis.copy(unitId = "g", pricedAmount = 500.0)))
        assertTrue(marketPriceBasisMatches(kg.copy(pricedAmount = 1.001), basis.copy(unitId = "g", pricedAmount = 1001.0)))
        assertTrue(marketPriceBasisMatches(kg.copy(pricedAmount = .0001), basis.copy(unitId = "g", pricedAmount = .1)))
        assertFalse(marketPriceBasisMatches(kg.copy(pricedAmount = 1.0010000001), basis.copy(unitId = "g", pricedAmount = 1001.0)))
        assertFalse(marketPriceBasisMatches(kg, basis.copy(unitId = "g", pricedAmount = 500.0)))
        assertFalse(marketPriceBasisMatches(basis, basis.copy(pricedAmount = 2.0)))
        assertFalse(marketPriceBasisMatches(basis, basis.copy(currencyCode = "RUB")))
        assertFalse(marketPriceBasisMatches(basis, basis.copy(gtin = null)))
        assertFalse(marketPriceBasisMatches(basis, basis.copy(pricedAmount = Double.NaN)))
        assertFalse(marketPriceBasisMatches(basis, basis.copy(unitId = "box")))
    }
    @Test fun branchDuplicatesCountOnceAndOwnStoreFamilyIsExcluded() {
        val values = sample(listOf(10_000, 20_000, 30_000)) + listOf(
            MarketPriceObservation("group-0", offer(5000)),
            MarketPriceObservation("mine", offer(1)), MarketPriceObservation("mine", offer(999_999)))
        val result = marketPriceInsight(row(), values, "mine", now)
        assertEquals(3, result.compatibleGroups)
        assertEquals(20_000L, result.statistics?.medianMinor)
        assertEquals(5000L, result.statistics?.lowestMinor)
    }
    @Test fun unpublishedUnavailableStaleUnpricedAndDifferentPacksDoNotEnterStatistics() {
        val invalid = listOf(offer().copy(storefront = MarketStorefront("id", published = false)),
            offer().copy(availability = MARKET_AVAILABILITY_CONFIRM), offer().copy(checkedAtMillis = now - 1),
            offer().copy(priceMinor = 0), offer().copy(priceMinor = -1), offer().copy(priceMinor = null),
            offer().copy(priceMinor = Long.MAX_VALUE), offer().copy(pricedAmount = 2.0), offer().copy(currencyCode = "RUB"))
        val result = marketPriceInsight(row(), sample(listOf(10_000, 20_000)) + invalid.mapIndexed { i, value -> MarketPriceObservation("invalid-$i", value) }, "mine", now)
        assertEquals(2, result.compatibleGroups)
        assertNull(result.statistics)
    }
    @Test fun invalidOrAbsentOwnBasisNeverCreatesAComparison() {
        val values = sample(listOf(1, 2, 3))
        for (reference in listOf(row().copy(basis = null), row().copy(ownPriceMinor = 0), row().copy(basis = basis.copy(gtin = null)))) {
            assertNull(marketPriceInsight(reference, values, "mine", now).statistics)
        }
    }
    @Test fun medianAndQuartilesAreDeterministicAndBounded() {
        assertNull(marketPriceStatistics(listOf(1, 2)))
        assertNull(marketPriceStatistics(List(81) { 1 }))
        assertNull(marketPriceStatistics(listOf(1, 2, 0)))
        assertEquals(MarketPriceStatistics(4, 100, 100, 251, 301, 900), marketPriceStatistics(listOf(900, 200, 301, 100)))
        for (n in 3..80) {
            val stats = assertNotNull(marketPriceStatistics(List(n) { i -> (i + 1).toLong() }))
            assertTrue(stats.isValidMarketPriceStatistics(), "n=$n, $stats")
        }
        assertEquals(MARKET_PRICE_MAX_MINOR, marketPriceStatistics(List(3) { MARKET_PRICE_MAX_MINOR })?.medianMinor)
    }
    @Test fun percentageUsesIntegerMoneyAndDoesNotOverflowAtLimits() {
        assertEquals(2000L, marketPriceDifferenceBasisPoints(12_000, 10_000))
        assertEquals(-2000L, marketPriceDifferenceBasisPoints(8000, 10_000))
        assertEquals(1L, marketPriceDifferenceBasisPoints(20_001, 20_000))
        assertEquals(-1L, marketPriceDifferenceBasisPoints(19_999, 20_000))
        assertEquals((MARKET_PRICE_MAX_MINOR - 1) * 10_000, marketPriceDifferenceBasisPoints(MARKET_PRICE_MAX_MINOR, 1))
        assertNull(marketPriceDifferenceBasisPoints(100, 0))
        assertNull(marketPriceDifferenceBasisPoints(Long.MAX_VALUE, 1))
    }
    @Test fun requestNormalizationPreservesExplicitScopeAndRejectsAmbiguousInput() {
        assertEquals("Almaty City", request.copy(city = "  Almaty  City ").normalizedMarketPriceRequest()?.city)
        for (bad in listOf(request.copy(country = "us"), request.copy(limit = 31), request.copy(city = "Astana\n"),
            request.copy(itemId = "1-1-1-1-1"), request.copy(gtin = code), request.copy(itemId = item, after = item))) {
            assertNull(bad.normalizedMarketPriceRequest(), "$bad")
        }
        assertEquals(code, request.copy(itemId = item, gtin = "4006381333931").normalizedMarketPriceRequest()?.gtin)
    }
    @Test fun responseValidationRejectsCrossAccountCrossStoreAndMalformedStatistics() {
        val value = page()
        assertTrue(value.isValidMarketPricePage(account, store, request))
        for (bad in listOf(value.copy(accountId = store), value.copy(storeId = item), value.copy(request = request.copy(city = "Other")),
            value.copy(rows = listOf(row(), row())), value.copy(nextId = item), value.copy(rows = listOf(row().copy(ownCurrencyCode = ""))),
            value.copy(rows = listOf(row().copy(compatibleGroups = 3))), value.copy(cities = listOf("Bad\ncity")))) {
            assertFalse(bad.isValidMarketPricePage(account, store, request), "$bad")
        }
    }
    @Test fun paginationRejectsRepeatedOrBackwardsCursors() {
        val r = request.copy(after = item)
        assertFalse(page().copy(request = r).isValidMarketPricePage(account, store, r))
    }
    @Test fun previousMedianRequiresSamePlaceCurrencyQuantityAndOlderObservation() {
        val stats = marketPriceStatistics(listOf(10, 20, 30))
        val before = page(now - 60_000).copy(rows = listOf(row().copy(statistics = stats, compatibleGroups = 3)))
        assertEquals(20L, row().previousObservedMedian(before, page()))
        assertNull(row().previousObservedMedian(before.copy(request = request.copy(city = "Other")), page()))
        assertNull(row().copy(basis = basis.copy(currencyCode = "RUB")).previousObservedMedian(before, page()))
        assertNull(row().previousObservedMedian(before.copy(checkedAtMillis = now), page()))
    }

    private class Memory {
        val values = mutableMapOf<String, String>()
        var failWrite = false
        fun cache() = MarketPriceCache({ values[it] }, { key, value -> if (failWrite) error("disk full") else values[key] = value })
    }
    private val origin = "https://example.test"
    @Test fun reportsSurviveRestartAndNeverLeakBetweenAccountStoreOrServer() = runTest {
        val memory = Memory()
        memory.cache().accept(page(), origin) { true }
        val restarted = memory.cache()
        assertEquals(page(), restarted.restore(account, store, origin)?.entries?.single()?.current)
        assertNull(restarted.restore(store, store, origin))
        assertNull(restarted.restore(account, item, origin))
        assertNull(restarted.restore(account, store, "https://other.test"))
    }
    @Test fun failedWriteKeepsPreviousReportAndLaterRefreshCanRecover() = runTest {
        val memory = Memory(); val cache = memory.cache()
        cache.accept(page(), origin) { true }; memory.failWrite = true
        assertFails { cache.accept(page(now + 60_000), origin) { true } }
        assertEquals(now, cache.restore(account, store, origin)?.entries?.single()?.current?.checkedAtMillis)
        memory.failWrite = false
        val next = assertNotNull(cache.accept(page(now + 60_000), origin) { true })
        assertEquals(now, next.previous?.checkedAtMillis)
    }
    @Test fun warmDecodedJournalStillObservesExternalChangesRevocationAndAccountCleanup() = runTest {
        val memory = Memory(); val first = memory.cache(); val second = memory.cache()
        first.accept(page(), origin) { true }
        assertEquals(now, first.restore(account, store, origin)?.entries?.single()?.current?.checkedAtMillis)
        second.accept(page(now + 60_000), origin) { true }
        assertEquals(now + 60_000, first.restore(account, store, origin)?.entries?.single()?.current?.checkedAtMillis)
        second.revoke(account, store, origin)
        assertNull(first.restore(account, store, origin))
        first.accept(page(now + 120_000), origin) { true }
        assertNotNull(first.restore(account, store, origin))
        first.clearAccount(account) { prefix -> memory.values.keys.removeAll { it.startsWith(prefix) } }
        assertNull(first.restore(account, store, origin)); assertTrue(memory.values.isEmpty())
    }
    @Test fun onlyEightQueriesAreRetainedAndAnOlderResponseCannotReplaceANewerReport() = runTest {
        val memory = Memory(); val cache = memory.cache()
        repeat(12) { i -> cache.accept(page().copy(request = request.copy(city = "City $i")), origin) { true } }
        assertEquals(8, cache.restore(account, store, origin)?.entries?.size)
        cache.accept(page(now + 1000), origin) { true }
        assertEquals(now + 1000, cache.accept(page(), origin) { true }?.current?.checkedAtMillis)
        assertFalse(cache.restore(account, store, origin)!!.entries.any { it.current.request.city == "City 0" })
    }
    @Test fun revokedPermissionCannotResurrectCachedReportsEvenWhenDeletionCannotPersist() = runTest {
        val memory = Memory(); val cache = memory.cache()
        cache.accept(page(), origin) { true }; memory.failWrite = true
        assertFails { cache.revoke(account, store, origin) }
        assertNull(cache.restore(account, store, origin))
        memory.failWrite = false; cache.revoke(account, store, origin)
        assertNull(memory.cache().restore(account, store, origin))
        cache.accept(page(now + 1000), origin) { true }
        assertNotNull(cache.restore(account, store, origin))
    }
    @Test fun expiredOwnerNeverWritesAndFutureCacheSchemasAreNotDowngraded() = runTest {
        val memory = Memory(); val cache = memory.cache()
        assertNull(cache.accept(page(), origin) { false }); assertTrue(memory.values.isEmpty())
        cache.accept(page(), origin) { true }
        val key = memory.values.keys.single()
        memory.values[key] = jsonBase.encodeToString(MarketPriceCacheSnapshot(account, store, origin, schema = 2))
        val preserved = memory.values[key]
        assertFails { cache.accept(page(now + 1000), origin) { true } }
        assertEquals(preserved, memory.values[key])
    }
    @Test fun aWriteThatSilentlyFailedIsNotReportedAsSaved() = runTest {
        val cache = MarketPriceCache({ null }, { _, _ -> })
        assertFails { cache.accept(page(), origin) { true } }
    }

    @Test fun malformedCurrentCacheCanRecoverButUnreadableStorageIsPreserved() = runTest {
        val memory = Memory(); val cache = memory.cache()
        cache.accept(page(), origin) { true }
        memory.values[memory.values.keys.single()] = "{broken"
        assertNotNull(cache.accept(page(now + 1000), origin) { true })
        var writes = 0
        val unreadable = MarketPriceCache({ error("read failed") }, { _, _ -> writes++ })
        assertFails { unreadable.accept(page(), origin) { true } }
        assertEquals(0, writes)
    }
    private fun reply(value: MarketPriceInsightsPage = page()) = ResponseDataModel(null, value, false, 200)
    @Test fun offlineAndTemporaryFailuresKeepTheLastConfirmedReport() = runTest {
        val cache = Memory().cache(); cache.accept(page(), origin) { true }
        for (status in listOf(401, 408, 429, 500, 503)) {
            val result = readOwnedMarketPriceReport(account, store, origin, request, { true }, cache) { reply().copy(payload = null, negative = true, httpStatusCode = status) }
            assertTrue(result.cached, "HTTP $status"); assertEquals(page(), result.page)
            assertNotNull(cache.restore(account, store, origin))
        }
        val result = readOwnedMarketPriceReport(account, store, origin, request, { true }, cache) { throw IllegalStateException("offline") }
        assertEquals(page(), result.page)
        assertTrue(result.cached)
    }
    @Test fun cachedReportIsDeliveredBeforeNetworkAndHangingReadsFinishWithinBound() = runTest {
        val cache = Memory().cache(); cache.accept(page(), origin) { true }
        val events = mutableListOf<String>()
        val result = readOwnedMarketPriceReport(account, store, origin, request, { true }, cache, { events += "cached" }) {
            events += "network"; delay(60_000); reply()
        }
        assertEquals(listOf("cached", "network"), events)
        assertTrue(result.cached); assertEquals(page(), result.page)
        assertEquals(20_000L, testScheduler.currentTime)
    }
    @Test fun offlineMessageExplainsWhetherAPriceReportActuallyExistsOnThisDevice() = runTest {
        val cache = Memory().cache()
        val transport = reply().copy(payload = null, negative = true, httpStatusCode = 0, transportFailure = true,
            message = listOf(LocalizedStringDataModel("en", "Generic connectivity message")))
        val empty = readOwnedMarketPriceReport(account, store, origin, request, { true }, cache) { transport }
        assertEquals(marketPriceMessage("offline_empty"), empty.message)
        cache.accept(page(), origin) { true }
        val saved = readOwnedMarketPriceReport(account, store, origin, request, { true }, cache) { transport }
        assertEquals(marketPriceMessage("cached"), saved.message); assertEquals(page(), saved.page)
    }
    @Test fun explicitPermissionDenialErasesAllQueriesButDoesNotMislabelNetworkFailureAsRevocation() = runTest {
        val cache = Memory().cache(); cache.accept(page(), origin) { true }
        val transportFailure = readOwnedMarketPriceReport(account, store, origin, request, { true }, cache) {
            reply().copy(payload = null, negative = true, httpStatusCode = 403, transportFailure = true)
        }
        assertTrue(transportFailure.cached); assertFalse(transportFailure.accessDenied)
        val denied = readOwnedMarketPriceReport(account, store, origin, request, { true }, cache) {
            reply().copy(payload = null, negative = true, httpStatusCode = 403)
        }
        assertTrue(denied.accessDenied); assertNull(denied.page); assertNull(cache.restore(account, store, origin))
    }
    @Test fun responseAfterStoreOrAccountSwitchCannotPublishOrSave() = runTest {
        val memory = Memory(); val cache = memory.cache(); var current = true
        val result = readOwnedMarketPriceReport(account, store, origin, request, { current }, cache) {
            current = false; reply()
        }
        assertNull(result.page); assertTrue(memory.values.isEmpty())
    }
    @Test fun aReportAlreadyInFlightCannotUndoANewerPermissionRevocation() = runTest {
        for (transportFails in listOf(false, true)) {
            val cache = Memory().cache(); cache.accept(page(), origin) { true }
            val result = readOwnedMarketPriceReport(account, store, origin, request, { true }, cache) {
                // A concurrent watch-settings request can discover lost access while this
                // older report still has a valid database snapshot or an offline fallback.
                cache.revoke(account, store, origin)
                if (transportFails) error("connection lost") else reply(page(now + 1000))
            }
            assertTrue(result.accessDenied); assertNull(result.page)
            assertNull(cache.restore(account, store, origin))
            val retry = readOwnedMarketPriceReport(account, store, origin, request, { true }, cache) { reply(page(now + 2000)) }
            assertNotNull(retry.page, "A new authenticated request may confirm restored access")
        }
    }
    @Test fun onlySuccessfulExactOwnedResponsesCanBeCached() = runTest {
        val bad = listOf(reply().copy(httpStatusCode = 201), reply().copy(httpStatusCode = 304),
            reply().copy(negative = true), reply().copy(transportFailure = true),
            reply(page().copy(accountId = store)), reply(page().copy(request = request.copy(city = "Other"))))
        for (response in bad) {
            val memory = Memory()
            val result = readOwnedMarketPriceReport(account, store, origin, request, { true }, memory.cache()) { response }
            assertNull(result.page); assertTrue(memory.values.isEmpty())
        }
    }
    @Test fun anotherTabCannotHaveItsDenialOverwrittenByAnOlderResponseOrOfflineFallback() = runTest {
        for (transportFails in listOf(false, true)) {
            val memory = Memory(); val first = memory.cache(); val other = memory.cache()
            first.accept(page(), origin) { true }
            val result = readOwnedMarketPriceReport(account, store, origin, request, { true }, first) {
                other.revoke(account, store, origin)
                if (transportFails) error("connection lost") else reply(page(now + 1000))
            }
            assertTrue(result.accessDenied); assertNull(result.page)
            assertNull(memory.cache().restore(account, store, origin))
            val restored = readOwnedMarketPriceReport(account, store, origin, request, { true }, first) { reply(page(now + 2000)) }
            assertEquals(now + 2000, restored.page?.checkedAtMillis)
            assertNotNull(first.restore(account, store, origin))
        }
    }
    @Test fun firstAuthorizedRequestAfterRestartCanRecoverAPersistedDenial() = runTest {
        val memory = Memory(); memory.cache().revoke(account, store, origin)
        var requests = 0
        val result = readOwnedMarketPriceReport(account, store, origin, request, { true }, memory.cache()) {
            requests++; reply()
        }
        assertEquals(1, requests); assertEquals(page(), result.page); assertFalse(result.accessDenied)
    }
    @Test fun repeatedIdenticalExternalTombstonesRemainAuthoritativeAfterRecovery() = runTest {
        val memory = Memory(); val first = memory.cache(); val other = memory.cache()
        repeat(3) { index ->
            val restored = readOwnedMarketPriceReport(account, store, origin, request, { true }, first) { reply(page(now + index * 1000)) }
            assertNotNull(restored.page)
            val denied = readOwnedMarketPriceReport(account, store, origin, request, { true }, first) {
                other.revoke(account, store, origin); reply(page(now + index * 1000 + 1))
            }
            assertTrue(denied.accessDenied); assertNull(denied.page)
        }
    }
    @Test fun cancellationIsNotReportedAsAnOfflineSuccess() = runTest {
        val memory = Memory()
        val task = async {
            readOwnedMarketPriceReport(account, store, origin, request, { true }, memory.cache()) {
                currentCoroutineContext().cancel(); reply()
            }
        }
        assertFailsWith<CancellationException> { task.await() }
        assertTrue(memory.values.isEmpty())
    }
}
