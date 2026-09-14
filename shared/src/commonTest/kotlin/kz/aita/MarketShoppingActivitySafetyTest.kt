package kz.aita

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/** History is a read, never an acknowledgement of the durable pending command. */
class MarketShoppingActivitySafetyTest {
    private fun id(n: Int) = "00000000-0000-0000-0000-${n.toString().padStart(12, '0')}"
    private val account = id(1)
    private class Owner(override val accountId: String) : MarketAccountScope {
        override val generation = 1L
        var current = true
        override fun isCurrent() = current
    }
    private val line = MarketShoppingLine(id(3), id(4), "Historical product", "Historical shop", 2,
        MarketShoppingBasis(currencyCode = "KZT", unitId = "piece", pricedAmount = 1.0))
    private val changed = MarketShoppingActivityEntry(id(2), 100, 7, true, 8,
        kind = MARKET_ACTIVITY_QUANTITY, requestedUnits = 3, changedLines = 1,
        details = MarketShoppingActivityDetails(listOf(MarketShoppingActivityLine(line, line.copy(units = 3)))),
        previewTitle = line.title)
    private val summary get() = changed.copy(details = null)
    private val request = MarketShoppingActivitySearchRequest()
    private val legacyRequest = MarketShoppingActivityRequest()
    private fun page(input: MarketShoppingActivitySearchRequest = request) =
        MarketShoppingActivitySearchPage(account, input, listOf(summary), false, false, 101)
    private fun legacyPage() = MarketShoppingActivityPage(account, legacyRequest, listOf(summary), false, 101)
    private fun <T> ok(value: T) = ResponseDataModel(null, value, false, 200)

    @Test fun allHistoryReadersRejectFailedOrAbsentHttpStatus() = runTest {
        val owner = Owner(account)
        for (status in listOf(null, 201, 204, 403, 500)) {
            val a = readOwnedShoppingActivitySearch(owner, request) { ok(page()).copy(httpStatusCode = status) }
            val b = readOwnedShoppingActivity(owner, legacyRequest) { ok(legacyPage()).copy(httpStatusCode = status) }
            val c = readOwnedShoppingActivityDetail(owner, summary) { ok(changed).copy(httpStatusCode = status) }
            for (result in listOf(a, b, c)) { assertTrue(result.negative); assertNull(result.payload); assertNotNull(result.message) }
        }
    }
    @Test fun allHistoryReadersRejectTransportFailureEvenWithAValidPayload() = runTest {
        val owner = Owner(account)
        val a = readOwnedShoppingActivitySearch(owner, request) { ok(page()).copy(transportFailure = true) }
        val b = readOwnedShoppingActivity(owner, legacyRequest) { ok(legacyPage()).copy(transportFailure = true) }
        val c = readOwnedShoppingActivityDetail(owner, summary) { ok(changed).copy(transportFailure = true) }
        for (result in listOf(a, b, c)) { assertTrue(result.negative); assertTrue(result.transportFailure); assertNull(result.payload) }
    }
    @Test fun transportFailureDoesNotMasqueradeAsAMissingEndpoint() = runTest {
        val owner = Owner(account); val message = eventMessage("market.activity_busy")
        val result = readOwnedShoppingActivitySearch(owner, request) {
            ResponseDataModel(message, page(), false, 404, transportFailure = true)
        }
        assertEquals(message, result.message); assertTrue(result.transportFailure); assertNull(result.payload)
    }
    @Test fun validRecordedDetailAndEmptySearchRemainReadable() = runTest {
        val owner = Owner(account)
        assertFalse(readOwnedShoppingActivityDetail(owner, summary) { ok(changed) }.negative)
        assertFalse(readOwnedShoppingActivitySearch(owner, request) { ok(page().copy(entries = emptyList())) }.negative)
        assertFalse(readOwnedShoppingActivity(owner, legacyRequest) { ok(legacyPage()) }.negative)
    }
    @Test fun negativeSuccessStatusKeepsServerFailureAndStripsPayload() = runTest {
        val owner = Owner(account); val message = eventMessage("market.activity_busy")
        val result = readOwnedShoppingActivitySearch(owner, request) { ResponseDataModel(message, page(), true, 200) }
        assertEquals(message, result.message); assertTrue(result.negative); assertNull(result.payload)
    }
    @Test fun cancelledCallerCannotStartAnyHistoryRead() = runTest {
        var calls = 0
        for (kind in 0..2) {
            val job = async {
                currentCoroutineContext().cancel()
                val owner = Owner(account)
                when (kind) {
                    0 -> readOwnedShoppingActivitySearch(owner, request) { calls++; ok(page()) }
                    1 -> readOwnedShoppingActivity(owner, legacyRequest) { calls++; ok(legacyPage()) }
                    else -> readOwnedShoppingActivityDetail(owner, summary) { calls++; ok(changed) }
                }
            }
            assertFailsWith<CancellationException> { job.await() }
        }
        assertEquals(0, calls)
    }
    @Test fun adapterReturningAfterCancellationCannotPublishAnyHistoryRead() = runTest {
        var returned = 0
        for (kind in 0..2) {
            val job = async {
                val owner = Owner(account)
                when (kind) {
                    0 -> readOwnedShoppingActivitySearch(owner, request) { currentCoroutineContext().cancel(); ok(page()) }
                    1 -> readOwnedShoppingActivity(owner, legacyRequest) { currentCoroutineContext().cancel(); ok(legacyPage()) }
                    else -> readOwnedShoppingActivityDetail(owner, summary) { currentCoroutineContext().cancel(); ok(changed) }
                }
                returned++
            }
            assertFailsWith<CancellationException> { job.await() }
        }
        assertEquals(0, returned)
    }
    @Test fun lateAccountChangeCannotPublishAValidDetailOrPage() = runTest {
        for (kind in 0..2) {
            val owner = Owner(account)
            val result = when (kind) {
                0 -> readOwnedShoppingActivitySearch(owner, request) { owner.current = false; ok(page()) }
                1 -> readOwnedShoppingActivity(owner, legacyRequest) { owner.current = false; ok(legacyPage()) }
                else -> readOwnedShoppingActivityDetail(owner, summary) { owner.current = false; ok(changed) }
            }
            assertTrue(result.negative); assertNull(result.payload)
        }
    }
    @Test fun aDifferentImmutableSummaryCannotSupplyTheSelectedDetail() = runTest {
        val owner = Owner(account)
        assertTrue(readOwnedShoppingActivityDetail(owner, summary) { ok(changed.copy(recordedAtMillis = 99)) }.negative)
        assertTrue(readOwnedShoppingActivityDetail(owner, summary) { ok(changed.copy(commandId = id(90))) }.negative)
    }
    @Test fun noOpAndAppliedFlagsMustAgreeWithChangedLineCount() {
        assertFalse(summary.copy(changedLines = 0).isValidShoppingActivityEntry())
        assertFalse(summary.copy(appliedRevision = 7).isValidShoppingActivityEntry())
        assertTrue(summary.copy(appliedRevision = 7, changedLines = 0).isValidShoppingActivityEntry())
    }
    @Test fun rejectionCannotClaimAnAppliedLineCount() {
        val rejected = summary.copy(accepted = false, appliedRevision = null, errorKey = "market.shopping_changed",
            detailsRecorded = false, changedLines = 0)
        assertTrue(rejected.isValidShoppingActivityEntry())
        assertFalse(rejected.copy(changedLines = 1).isValidShoppingActivityEntry())
    }
    @Test fun previewCannotNameADifferentProductThanTheRecordedDetail() {
        assertFalse(changed.copy(previewTitle = "Different product").isValidShoppingActivityEntry())
        assertTrue(changed.copy(previewTitle = null).isValidShoppingActivityEntry())
    }
    @Test fun anUnchangedQuantityCannotContainAQuantityEdit() {
        assertFalse(changed.copy(appliedRevision = 7, changedLines = 0).isValidShoppingActivityEntry())
        val noOp = changed.copy(appliedRevision = 7, changedLines = 0,
            details = MarketShoppingActivityDetails(listOf(MarketShoppingActivityLine(line.copy(units = 3), line.copy(units = 3)))))
        assertTrue(noOp.isValidShoppingActivityEntry())
        assertFalse(noOp.copy(appliedRevision = 8, changedLines = 1).isValidShoppingActivityEntry())
    }
    @Test fun aQuantityEditCannotMoveShopOrChangeSellingBasisOrLabels() {
        for (after in listOf(line.copy(units = 3, storeId = id(44)), line.copy(units = 3, title = "New title"),
            line.copy(units = 3, basis = line.basis.copy(pricedAmount = 2.0)))) {
            assertFalse(changed.copy(details = MarketShoppingActivityDetails(listOf(MarketShoppingActivityLine(line, after))))
                .isValidShoppingActivityEntry())
        }
        assertTrue(changed.copy(details = MarketShoppingActivityDetails(listOf(
            MarketShoppingActivityLine(line, line.copy(units = 3, updatedAtMillis = 999)))))
            .isValidShoppingActivityEntry())
    }
    @Test fun replacementMustPreserveTheRequestedQuantityAndChangeShop() {
        val after = line.copy(offerId = id(30), storeId = id(40))
        val replacement = changed.copy(kind = MARKET_ACTIVITY_REPLACE, requestedUnits = 2, reviewedSubtotalMinor = 300,
            details = MarketShoppingActivityDetails(listOf(MarketShoppingActivityLine(line, after))))
        assertTrue(replacement.isValidShoppingActivityEntry())
        assertFalse(replacement.copy(requestedUnits = 4).isValidShoppingActivityEntry())
        assertFalse(replacement.copy(details = MarketShoppingActivityDetails(listOf(
            MarketShoppingActivityLine(line, after.copy(storeId = line.storeId))))).isValidShoppingActivityEntry())
    }
    @Test fun removingAnAbsentItemCannotDisplayAFabricatedDeletion() {
        val removal = changed.copy(kind = MARKET_ACTIVITY_REMOVE, requestedUnits = 0,
            details = MarketShoppingActivityDetails(listOf(MarketShoppingActivityLine(before = line))))
        assertTrue(removal.isValidShoppingActivityEntry())
        assertFalse(removal.copy(appliedRevision = 7, changedLines = 0).isValidShoppingActivityEntry())
        assertTrue(removal.copy(appliedRevision = 7, changedLines = 0, previewTitle = null,
            details = MarketShoppingActivityDetails(emptyList())).isValidShoppingActivityEntry())
    }
    @Test fun olderRecordsWithoutCapturedDetailsRemainValid() {
        assertTrue(summary.copy(detailsRecorded = false, previewTitle = null).isValidShoppingActivityEntry())
        assertTrue(summary.isValidShoppingActivityEntry())
    }
    @Test fun explicitRefreshRetiresTheHeadEvenBeforeAnotherReadStarts() {
        val fence = MarketShoppingActivityReadFence(); val stamp = fence.capture(request, 1)
        assertTrue(fence.canUse(stamp, page(), request, 1, false))
        fence.invalidate()
        assertFalse(fence.isCurrent(stamp, request, 1))
        assertFalse(fence.canUse(stamp, page(), request, 1, false))
    }
    @Test fun headAndExactReferencePagesCannotIgnoreANewerEvent() {
        val fence = MarketShoppingActivityReadFence()
        assertFalse(fence.isCurrent(fence.capture(request, 1), request, 2))
        val exact = request.copy(filter = MarketShoppingActivityFilter(commandId = changed.commandId))
        assertFalse(fence.isCurrent(fence.capture(exact, 1), exact, 2))
    }
    @Test fun olderImmutablePagesStayNavigableAfterNewActivity() {
        val fence = MarketShoppingActivityReadFence()
        val older = request.copy(boundary = MarketShoppingActivityCursor(200, id(90)))
        val stamp = fence.capture(older, 1)
        assertTrue(fence.canUse(stamp, page(older), older, 30, false))
        fence.invalidate()
        assertFalse(fence.canUse(stamp, page(older), older, 30, false))
    }
    @Test fun movingAwayAndBackCannotReviveTheFirstRead() {
        val fence = MarketShoppingActivityReadFence(); val stamp = fence.capture(request, 1)
        fence.invalidate(); fence.invalidate()
        assertFalse(fence.isCurrent(stamp, request, 1))
        assertTrue(fence.isCurrent(fence.capture(request, 1), request, 1))
    }
    @Test fun errorsLoadingAndOtherDialogsBlockNavigation() {
        val fence = MarketShoppingActivityReadFence(); val stamp = fence.capture(request, 1)
        assertFalse(fence.canUse(stamp, page(), request, 1, true))
        assertFalse(fence.canUse(stamp, null, request, 1, false))
        assertFalse(fence.canUse(null, page(), request, 1, false))
    }
    @Test fun requestAndDirectionCannotBeBorrowedFromAnotherPage() {
        val fence = MarketShoppingActivityReadFence(); val stamp = fence.capture(request, 1)
        val another = request.copy(filter = MarketShoppingActivityFilter(kind = MARKET_ACTIVITY_BASKET))
        assertFalse(fence.isCurrent(stamp, another, 1))
        assertFalse(fence.canUse(stamp, page(another), request, 1, false))
        val older = request.copy(boundary = MarketShoppingActivityCursor(200, id(90)))
        assertFalse(fence.isCurrent(fence.capture(older, 1), older.copy(newer = true), 1))
    }
    @Test fun negativeOrUnknownSignalNeverGrantsFreshness() {
        val fence = MarketShoppingActivityReadFence()
        assertFalse(fence.isCurrent(fence.capture(request, -1), request, -1))
        assertFalse(fence.isCurrent(fence.capture(request, 0), request, -1))
    }
    @Test fun invalidRequestsCannotCreateAReadStamp() {
        assertFailsWith<IllegalArgumentException> {
            MarketShoppingActivityReadFence().capture(request.copy(newer = true), 0)
        }
    }
}
