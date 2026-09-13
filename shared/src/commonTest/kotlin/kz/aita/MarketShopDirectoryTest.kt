package kz.aita

import kotlinx.coroutines.test.runTest
import kotlin.test.*

class MarketShopDirectoryTest {
    private val id = "00000000-0000-0000-0000-000000000001"
    private val shop = MarketStorefront(id, "Neighbour Shop", "Astana", "Public door", published = true, revision = 1)
    private val request = MarketShopDirectoryRequest()
    private fun page() = MarketShopDirectoryResult("buyer", request, listOf(MarketShopDirectoryEntry(shop, 3)), 1, 100)
    private class Owner : MarketAccountScope {
        override val accountId = "buyer"; override val generation = 1L
        var active = true
        override fun isCurrent() = active
    }
    private fun <T> ok(value: T) = ResponseDataModel(null, value, false, 200)

    @Test fun queryNormalizationUsesBoundedLiteralSearchNotWildcards() {
        assertEquals(MarketShopDirectoryRequest("Shop %_ ' door", "Astana city"),
            MarketShopDirectoryRequest("  Shop  %_  ' door\n", " Astana\tcity ").normalizedShopDirectoryRequest())
    }
    @Test fun invalidSearchOrCityNeverNormalizesToABroaderQuery() {
        listOf(MarketShopDirectoryRequest("x".repeat(121)), MarketShopDirectoryRequest(city = "x".repeat(101)),
            MarketShopDirectoryRequest("a\u0000b"), MarketShopDirectoryRequest((1..13).joinToString(" "))).forEach {
            assertNull(it.normalizedShopDirectoryRequest())
        }
    }
    @Test fun onlyCompleteBoundedWindowsAreAccepted() {
        listOf(0, 1, 19, 21, 201, Int.MAX_VALUE).forEach { assertNull(request.copy(limit = it).normalizedShopDirectoryRequest()) }
        for (limit in 20..200 step 20) assertNotNull(request.copy(limit = limit).normalizedShopDirectoryRequest())
    }
    @Test fun publishedEmptyShopIsAValidDirectoryEntry() {
        assertTrue(page().copy(shops = listOf(MarketShopDirectoryEntry(shop, 0))).isValidShopDirectoryResult("buyer", request))
    }
    @Test fun responseMustMatchAccountRequestAndProtocol() {
        assertFalse(page().isValidShopDirectoryResult("other", request))
        assertFalse(page().isValidShopDirectoryResult("buyer", request.copy(city = "Almaty")))
        assertFalse(page().copy(protocolVersion = 0).isValidShopDirectoryResult("buyer", request))
        assertFalse(page().copy(checkedAtMillis = 0).isValidShopDirectoryResult("buyer", request))
    }
    @Test fun responseCountsCannotPretendAPartialWindowIsComplete() {
        assertFalse(page().copy(totalShops = 20).isValidShopDirectoryResult("buyer", request))
        assertFalse(page().copy(totalShops = -1).isValidShopDirectoryResult("buyer", request))
        assertFalse(page().copy(shops = emptyList()).isValidShopDirectoryResult("buyer", request))
        assertTrue(page().copy(shops = emptyList(), totalShops = 0).isValidShopDirectoryResult("buyer", request))
    }
    @Test fun duplicateShopIdsAndNegativeOfferCountsAreRejected() {
        assertFalse(page().copy(shops = page().shops + page().shops, totalShops = 2).isValidShopDirectoryResult("buyer", request))
        assertFalse(page().copy(shops = listOf(MarketShopDirectoryEntry(shop, -1))).isValidShopDirectoryResult("buyer", request))
    }
    @Test fun unpublishedMalformedOrIncompleteStorefrontsStayOutOfTheView() {
        for (invalid in listOf(shop.copy(published = false), shop.copy(storeId = "private-id"), shop.copy(revision = 0),
            shop.copy(displayName = ""), shop.copy(city = ""), shop.copy(publicAddress = ""), shop.copy(pickupNote = "x".repeat(1001)))) {
            assertFalse(page().copy(shops = listOf(MarketShopDirectoryEntry(invalid, 0))).isValidShopDirectoryResult("buyer", request))
        }
    }
    @Test fun normalizedRequestIsSentAndReturnedWithoutChangingItsMeaning() = runTest {
        val raw = request.copy(text = " Shop ", city = " Astana ")
        val result = readOwnedMarketShopDirectory(Owner(), raw) { actual ->
            assertEquals(MarketShopDirectoryRequest("Shop", "Astana"), actual)
            ok(page().copy(request = actual))
        }
        assertFalse(result.negative); assertNotNull(result.payload)
    }
    @Test fun ownerChangingWhileReadIsSuspendedRejectsTheOldAccountResult() = runTest {
        val owner = Owner()
        val result = readOwnedMarketShopDirectory(owner, request) { owner.active = false; ok(page()) }
        assertTrue(result.negative); assertNull(result.payload)
    }
    @Test fun obsoleteOwnerAndInvalidRequestDoNotCallTransport() = runTest {
        var calls = 0
        val owner = Owner().apply { active = false }
        readOwnedMarketShopDirectory(owner, request) { calls++; ok(page()) }
        readOwnedMarketShopDirectory(Owner(), request.copy(limit = 21)) { calls++; ok(page()) }
        assertEquals(0, calls)
    }
    @Test fun oldBackendProducesAnUpgradeErrorWithoutFallback() = runTest {
        var calls = 0
        val result = readOwnedMarketShopDirectory(Owner(), request) { calls++; ResponseDataModel(null, page(), true, 404) }
        assertEquals(1, calls); assertTrue(result.negative); assertNull(result.payload); assertNotNull(result.message)
    }
    @Test fun malformedOrNegativeResponseCannotEnterTheDirectory() = runTest {
        val malformed = readOwnedMarketShopDirectory(Owner(), request) { ok(page().copy(totalShops = 99)) }
        assertTrue(malformed.negative); assertNull(malformed.payload); assertEquals(502, malformed.httpStatusCode)
        val failure = readOwnedMarketShopDirectory(Owner(), request) { ResponseDataModel(null, page(), true, 503) }
        assertTrue(failure.negative); assertNull(failure.payload)
    }
    @Test fun cancelledActivityIsAFilterableSubsetOfNotApplied() {
        val entry = MarketShoppingActivityEntry(id, 1, expectedRevision = 0, accepted = false, kind = MARKET_ACTIVITY_QUANTITY,
             errorKey = "market.shopping_cancelled", requestedUnits = 1)
        assertNotNull(MarketShoppingActivitySearchRequest(MarketShoppingActivityFilter(result = MARKET_ACTIVITY_RESULT_CANCELLED)).normalizedActivitySearch())
        assertTrue(MarketShoppingActivityFilter(result = MARKET_ACTIVITY_RESULT_CANCELLED).matchesActivity(entry))
        assertTrue(MarketShoppingActivityFilter(result = MARKET_ACTIVITY_RESULT_REJECTED).matchesActivity(entry))
        assertFalse(MarketShoppingActivityFilter(result = MARKET_ACTIVITY_RESULT_CANCELLED).matchesActivity(entry.copy(errorKey = "market.shopping_changed")))
    }
}
