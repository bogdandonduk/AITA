package kz.aita

import kotlin.test.*

class MarketBasketPlanReadFenceTest {
    private val source = BasketTestData.row(1, units = 2)
    private val snapshot = BasketTestData.snapshot(source)
    private val result = BasketTestData.plan(snapshot, listOf(BasketTestData.alternative(source, 10, 100)))
    private val request = result.request
    private val fence = MarketBasketPlanReadFence()
    private val stamp = fence.capture(request, 7L)
    private val command = requireNotNull(result.reviewedBasketCommand("KZT", MARKET_BASKET_ONE_SHOP, BasketTestData.id(999)))

    private fun usable(current: MarketShoppingSnapshot? = snapshot, age: Long = 0L, blocked: Boolean = false,
        wanted: MarketBasketRequest? = request, signal: Long = 7L, value: MarketBasketResult? = result) =
        fence.canUse(stamp, value, wanted, signal, current, age, blocked)
    private fun confirm(current: MarketShoppingSnapshot? = snapshot, age: Long = 0L, blocked: Boolean = false,
        candidate: MarketShoppingCommand = command, signal: Long = 7L) =
        fence.canConfirm(stamp, result, candidate, request, signal, current, age, blocked)

    @Test fun currentOwnedIntentCanUseAValidatedPlan() { assertTrue(usable()); assertTrue(confirm()) }
    @Test fun refreshImmediatelyRetiresTheDisplayedPlanAndReview() {
        fence.invalidate(); assertFalse(usable()); assertFalse(confirm())
    }
    @Test fun responseCapturedBeforeRefreshCannotBecomeFreshAfterCooldown() {
        fence.invalidate()
        val trailing = fence.capture(request, 7L)
        assertFalse(fence.isCurrent(stamp, request, 7L))
        assertTrue(fence.canUse(trailing, result, request, 7L, snapshot, 0L, false))
    }
    @Test fun cityChangesCannotReviveAnOldReadAfterReturningToTheSameCity() {
        fence.invalidate(); fence.capture(request.copy(city = "Almaty"), 7L); fence.invalidate()
        assertFalse(usable()); assertFalse(confirm())
    }
    @Test fun marketChangeIsCheckedWithoutWaitingForAnEffect() {
        assertFalse(usable(signal = 8L)); assertFalse(confirm(signal = 8L))
    }
    @Test fun missingOrDifferentRequestsRejectBothSuccessAndErrorOwnership() {
        assertFalse(fence.isCurrent(stamp, null, 7L))
        assertFalse(fence.isCurrent(stamp, request.copy(city = "Almaty"), 7L))
        assertFalse(fence.isCurrent(stamp, request.copy(expectedRevision = 8L), 7L))
        assertFalse(fence.isCurrent(stamp, request, -1L))
    }
    @Test fun normalizedCityScopeStillMatches() {
        val normalized = request.copy(city = "Astana City")
        val named = fence.capture(normalized, 7L)
        assertTrue(fence.isCurrent(named, normalized.copy(city = "  Astana\tCity "), 7L))
    }
    @Test fun planHasABoundedMonotonicAgeIncludingTheReadTime() {
        assertFalse(usable(age = -1L)); assertTrue(usable(age = 0L))
        assertTrue(usable(age = MARKET_BASKET_PLAN_FRESH_MILLIS - 1L))
        assertFalse(usable(age = MARKET_BASKET_PLAN_FRESH_MILLIS)); assertFalse(usable(age = Long.MAX_VALUE))
    }
    @Test fun anOpenedReviewKeepsTheExistingLongerDeadlineNotAFreshClock() {
        assertFalse(usable(age = MARKET_BASKET_PLAN_FRESH_MILLIS))
        assertTrue(confirm(age = MARKET_BASKET_PLAN_FRESH_MILLIS))
        assertTrue(confirm(age = MARKET_BASKET_REVIEW_MAX_AGE_MILLIS - 1L))
        assertFalse(confirm(age = MARKET_BASKET_REVIEW_MAX_AGE_MILLIS)); assertFalse(confirm(age = -1L))
    }
    @Test fun changingServerWallClockDoesNotGrantAnActionFreshness() {
        assertFalse(usable(age = MARKET_BASKET_PLAN_FRESH_MILLIS,
            value = result.copy(snapshot = snapshot.copy(checkedAtMillis = Long.MAX_VALUE))))
    }
    @Test fun blockedOwnerOrPendingUiStateCannotUseOrSubmit() { assertFalse(usable(blocked = true)); assertFalse(confirm(blocked = true)) }
    @Test fun noSnapshotOrResultCannotCreateAPlanAction() {
        assertFalse(usable(current = null)); assertFalse(confirm(current = null))
        assertFalse(usable(value = null)); assertFalse(usable(wanted = null))
    }
    @Test fun anotherAccountsEqualRevisionAndLinesAreStillNotOurIntent() {
        val other = snapshot.copy(userId = BasketTestData.id(800))
        assertFalse(usable(other)); assertFalse(confirm(other))
    }
    @Test fun newerRevisionNeverGetsBorrowedByAnOldPlan() {
        val newer = snapshot.copy(revision = snapshot.revision + 1)
        assertFalse(usable(newer)); assertFalse(confirm(newer))
    }
    @Test fun inconsistentSameRevisionWithDifferentQuantityOrLineIsRejected() {
        val changed = snapshot.copy(lines = listOf(source.copy(line = source.line.copy(units = 8))))
        assertFalse(usable(changed)); assertFalse(confirm(changed))
        assertFalse(usable(snapshot.copy(lines = emptyList())))
    }
    @Test fun aPriceOrAvailabilityOnlyRefreshDoesNotInvalidateReviewedIntent() {
        val freshQuote = snapshot.copy(checkedAtMillis = snapshot.checkedAtMillis + 1,
            lines = listOf(source.copy(offer = null, subtotalMinor = null, unitPriceMinor = null, status = MARKET_QUOTE_UNAVAILABLE)))
        assertTrue(usable(freshQuote)); assertTrue(confirm(freshQuote))
    }
    @Test fun retainedLineLabelsAndOrderingCannotBeSwappedUnderAReview() {
        val renamed = snapshot.copy(lines = listOf(source.copy(line = source.line.copy(title = "Different retained label"))))
        assertFalse(usable(renamed)); assertFalse(confirm(renamed))
        val many = BasketTestData.snapshot(source, BasketTestData.row(2))
        assertFalse(many.hasSameBasketIntent(many.copy(lines = many.lines.reversed())))
    }
    @Test fun alteredFrozenCommandCannotPassEvenWithTheSameRevision() {
        assertFalse(confirm(candidate = command.copy(expectedRevision = command.expectedRevision + 1)))
        val change = requireNotNull(command.basketChange)
        assertFalse(confirm(candidate = command.copy(basketChange = change.copy(reviewedItemsSubtotalMinor = 0))))
        assertFalse(confirm(candidate = command.copy(basketChange = change.copy(lines = emptyList()))))
    }
    @Test fun unrelatedSingleLineCommandCannotUseTheReviewConfirmationGate() {
        assertFalse(confirm(candidate = MarketShoppingCommand("other", snapshot.revision, source.line.offerId, 0)))
    }
    @Test fun checkingTheReviewNeverRewritesItsIdentityOrReviewedBytes() {
        val before = command.copy()
        repeat(3) { assertTrue(confirm(age = 100L + it)) }
        assertEquals(before, command); assertEquals(BasketTestData.id(999), command.commandId)
    }
}
