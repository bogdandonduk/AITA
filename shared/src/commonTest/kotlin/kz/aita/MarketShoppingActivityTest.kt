package kz.aita

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class MarketShoppingActivityTest {
    private val before = BasketTestData.row(1).line
    private val after = before.copy(units = 3)
    private val id = BasketTestData.id(990)
    private val request = MarketShoppingActivityRequest()
    private val entry = MarketShoppingActivityEntry(id, 100, 7, true, 8, kind = MARKET_ACTIVITY_QUANTITY,
        requestedUnits = 3, changedLines = 1,
        details = MarketShoppingActivityDetails(listOf(MarketShoppingActivityLine(before, after))), previewTitle = after.title)
    private fun page(rows: List<MarketShoppingActivityEntry> = listOf(entry.copy(details = null))) =
        MarketShoppingActivityPage(BasketTestData.account, request, rows, false, 200)
    private fun <T> ok(value: T) = ResponseDataModel(null, value, false, 200)
    private class Owner : MarketAccountScope {
        override val accountId = BasketTestData.account
        override val generation = 1L
        var current = true
        override fun isCurrent() = current
    }
    @Test fun historyIsExplicitlyBoundedToTwentyThroughTwoHundred() {
        assertTrue(request.isValidShoppingActivityRequest())
        assertTrue(MarketShoppingActivityRequest(200).isValidShoppingActivityRequest())
        for (limit in listOf(-1, 0, 1, 19, 21, 201, Int.MAX_VALUE))
            assertFalse(MarketShoppingActivityRequest(limit).isValidShoppingActivityRequest())
    }
    @Test fun successfulBeforeAndAfterAreHistoricalIntent() {
        assertTrue(entry.isValidShoppingActivityEntry())
        val command = MarketShoppingCommand(id, 7, before.offerId, 3, before.basis)
        val details = command.shoppingActivityDetails(listOf(before), listOf(after))
        assertEquals(entry.details, details)
        assertEquals(before.title, details.lines.single().before?.title)
        assertEquals(3, details.lines.single().after?.units)
    }
    @Test fun summaryDoesNotCarryAllHistoricalBasketDetails() {
        assertTrue(page().isValidShoppingActivityPage(BasketTestData.account, request))
        assertFalse(page(listOf(entry)).isValidShoppingActivityPage(BasketTestData.account, request))
        assertTrue(entry.copy(details = null).detailsRecorded)
    }
    @Test fun addingRemovingAndNoOpRemovalHaveDistinctHistoryShapes() {
        val add = MarketShoppingCommand(id, 0, after.offerId, after.units, after.basis)
        assertNull(add.shoppingActivityDetails(emptyList(), listOf(after)).lines.single().before)
        val remove = MarketShoppingCommand(id, 7, before.offerId, 0)
        assertNull(remove.shoppingActivityDetails(listOf(before), emptyList()).lines.single().after)
        assertTrue(remove.shoppingActivityDetails(emptyList(), emptyList()).lines.isEmpty())
    }
    @Test fun alreadyMatchedDoesNotPretendToAdvanceTheListRevision() {
        val unchanged = entry.copy(appliedRevision = 7, changedLines = 0, details = MarketShoppingActivityDetails(
            listOf(MarketShoppingActivityLine(after, after))))
        assertTrue(unchanged.isValidShoppingActivityEntry()); assertFalse(unchanged.changed)
    }
    @Test fun rejectionCannotContainAppliedHistoryOrAnAppliedRevision() {
        val rejected = entry.copy(accepted = false, appliedRevision = null, errorKey = "market.shopping_changed",
            details = null, detailsRecorded = false, changedLines = 0)
        assertTrue(rejected.isValidShoppingActivityEntry())
        assertFalse(rejected.copy(details = entry.details, detailsRecorded = true).isValidShoppingActivityEntry())
        assertFalse(rejected.copy(appliedRevision = 8).isValidShoppingActivityEntry())
        assertTrue(rejected.copy(expectedRevision = Long.MAX_VALUE).isValidShoppingActivityEntry())
    }
    @Test fun earlierRecordsMayLackLabelsWithoutGuessingFromCurrentCatalogue() {
        assertTrue(entry.copy(details = null, detailsRecorded = false, previewTitle = null).isValidShoppingActivityEntry())
        assertFalse(entry.copy(detailsRecorded = false).isValidShoppingActivityEntry())
    }
    @Test fun chronologicalOrderingBreaksTiesByCanonicalCommandIdentity() {
        val old = entry.copy(details = null)
        val newer = old.copy(commandId = BasketTestData.id(991))
        assertTrue(page(listOf(newer, old)).isValidShoppingActivityPage(BasketTestData.account, request))
        assertFalse(page(listOf(old, newer)).isValidShoppingActivityPage(BasketTestData.account, request))
        assertFalse(page(listOf(old, old)).isValidShoppingActivityPage(BasketTestData.account, request))
    }
    @Test fun wrongOwnerWindowProtocolOrTruncatedMoreFlagFails() {
        assertFalse(page().isValidShoppingActivityPage("other", request))
        assertFalse(page().copy(protocolVersion = 99).isValidShoppingActivityPage(BasketTestData.account, request))
        assertFalse(page().copy(hasMore = true).isValidShoppingActivityPage(BasketTestData.account, request))
        assertFalse(page().isValidShoppingActivityPage(BasketTestData.account, MarketShoppingActivityRequest(40)))
    }
    @Test fun emptyActualHistoryIsValidButNegativeTimeIsNot() {
        assertTrue(page(emptyList()).isValidShoppingActivityPage(BasketTestData.account, request))
        assertFalse(page().copy(checkedAtMillis = 0).isValidShoppingActivityPage(BasketTestData.account, request))
    }
    @Test fun invalidSnapshotsCannotEnterHistoryDetail() {
        for (line in listOf(after.copy(title = ""), after.copy(shopName = "x".repeat(121)),
            after.copy(units = 0), after.copy(basis = after.basis.copy(pricedAmount = Double.NaN)))) {
            assertFalse(entry.copy(details = MarketShoppingActivityDetails(listOf(MarketShoppingActivityLine(before, line)))).isValidShoppingActivityEntry())
        }
        assertFalse(entry.copy(previewTitle = "x".repeat(181)).isValidShoppingActivityEntry())
    }
    @Test fun completeCurrencyBasketRetainsKeptLinesAndDoesNotIncludeOtherCurrencies() {
        val a = BasketTestData.row(1); val b = BasketTestData.row(2); val usd = BasketTestData.row(3, currency = "USD")
        val result = BasketTestData.plan(BasketTestData.snapshot(a,b,usd), listOf(BasketTestData.alternative(a,10,1), BasketTestData.alternative(b,10,1)))
        val cmd = requireNotNull(result.reviewedBasketCommand("KZT", MARKET_BASKET_ONE_SHOP, id))
        val currency = result.currencies.first { it.currencyCode == "KZT" }
        val after = currency.oneShop.choices.map { it.quote.line } + usd.line
        val details = cmd.shoppingActivityDetails(result.snapshot.lines.map { it.line }, after)
        assertEquals(2, details.lines.size); assertTrue(details.lines.all { it.after?.basis?.currencyCode == "KZT" })
        val basket = cmd.basketChange!!
        val record = MarketShoppingActivityEntry(id, 100, 7, true, 8, kind = MARKET_ACTIVITY_BASKET,
            requestedUnits = 0, reviewedCurrency = "KZT", reviewedSubtotalMinor = basket.reviewedItemsSubtotalMinor,
            reviewedLines = 2, changedLines = 2, details = details)
        assertTrue(record.isValidShoppingActivityEntry())
        assertFalse(record.copy(details = details.copy(lines = details.lines.drop(1))).isValidShoppingActivityEntry())
    }
    @Test fun invalidOwnerCannotStartHistoryNetworkRead() = runTest {
        var reads = 0
        val owner = Owner().apply { current = false }
        assertTrue(readOwnedShoppingActivity(owner, request) { reads++; ok(page()) }.negative)
        assertEquals(0, reads)
    }
    @Test fun ownerChangeDuringHistoryReadDiscardsThePayload() = runTest {
        val owner = Owner(); val started = CompletableDeferred<Unit>(); val resume = CompletableDeferred<Unit>()
        val task = async { readOwnedShoppingActivity(owner, request) { started.complete(Unit); resume.await(); ok(page()) } }
        started.await(); owner.current = false; resume.complete(Unit)
        assertTrue(task.await().negative); assertNull(task.await().payload)
    }
    @Test fun oldBackendDoesNotAppearToHaveAnEmptyHistory() = runTest {
        val response = readOwnedShoppingActivity(Owner(), request) { ResponseDataModel(null, null, true, 404) }
        assertTrue(response.negative); assertNull(response.payload); assertNotNull(response.message)
    }
    @Test fun detailMatchesExactlyTheSelectedImmutableSummary() = runTest {
        assertFalse(readOwnedShoppingActivityDetail(Owner(), entry.copy(details = null)) { ok(entry) }.negative)
        assertTrue(readOwnedShoppingActivityDetail(Owner(), entry.copy(details = null)) { ok(entry.copy(recordedAtMillis = 101)) }.negative)
        assertTrue(readOwnedShoppingActivityDetail(Owner(), entry.copy(details = null)) { ok(entry.copy(commandId = BasketTestData.id(999))) }.negative)
        assertTrue(readOwnedShoppingActivityDetail(Owner(), entry.copy(details = null)) { ok(entry.copy(details = null)) }.negative)
    }
    @Test fun detailFromAnotherSessionIsNotPublished() = runTest {
        val owner = Owner()
        val response = readOwnedShoppingActivityDetail(owner, entry.copy(details = null)) { owner.current = false; ok(entry) }
        assertTrue(response.negative); assertNull(response.payload)
    }
}
