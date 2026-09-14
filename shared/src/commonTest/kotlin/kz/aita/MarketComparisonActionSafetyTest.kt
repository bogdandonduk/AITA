package kz.aita

import kotlin.test.*

/** Local interaction identities do not replace the server's revision/price checks. */
class MarketComparisonActionSafetyTest {
    private val f = ComparisonWindowFixtures
    private val fence = MarketComparisonReadFence()
    private val request = f.request
    private val result = f.result()
    private val stamp = fence.capture(request, 10L)

    private fun canUse(read: MarketComparisonReadFence.Stamp? = stamp,
        page: MarketComparisonWindowResult? = result, wanted: MarketComparisonWindowRequest? = request,
        account: String = f.account, signal: Long = 10L, snapshot: MarketShoppingSnapshot? = null,
        age: Long = 0L, blocked: Boolean = false) =
        fence.canUse(read, page, wanted, account, signal, snapshot, age, blocked)

    private val listRequest = request.copy(selection = request.selection.copy(shoppingRevision = 7L))
    private val listResult = f.result(listRequest)
    private val listSnapshot = MarketShoppingSnapshot(f.account, 7L, listOf(listResult.reference), f.checkedAt)
    private fun confirm(page: MarketComparisonWindowResult = listResult,
        candidate: MarketShoppingQuotedLine = page.matches.single(),
        snapshot: MarketShoppingSnapshot? = listSnapshot, age: Long = 0L, blocked: Boolean = false): Boolean {
        val read = fence.capture(page.request, 10L)
        return fence.canConfirm(read, page, candidate, page.request, f.account, 10L, snapshot, age, blocked)
    }

    @Test fun freshBrowseDoesNotRequireACachedShoppingList() { assertTrue(canUse()) }
    @Test fun freshnessIsBoundedFromTheOriginalReadStart() {
        assertTrue(canUse(age = MARKET_COMPARISON_FRESH_MILLIS - 1))
        listOf(-1L, MARKET_COMPARISON_FRESH_MILLIS, Long.MAX_VALUE).forEach { assertFalse(canUse(age = it)) }
    }
    @Test fun queuedRefreshRetiresTheOldReplyBeforeNewIoStarts() {
        fence.invalidate(); assertFalse(canUse()); assertFalse(fence.isCurrent(stamp, request, 10L))
        assertTrue(canUse(read = fence.capture(request, 10L)))
    }
    @Test fun changingAwayAndBackCannotReviveARead() {
        fence.invalidate(); fence.capture(request.copy(city = "Almaty"), 10L)
        fence.invalidate(); assertFalse(canUse())
        assertTrue(canUse(read = fence.capture(request, 10L)))
    }
    @Test fun stampsFromAnotherDialogAreNeverCurrent() {
        assertFalse(canUse(read = MarketComparisonReadFence().capture(request, 10L)))
    }
    @Test fun requestIdentityIncludesQuantityCityWindowAndSource() {
        listOf(request.copy(city = "Almaty"), request.copy(candidateLimit = 80),
            request.copy(selection = request.selection.copy(units = 3)),
            request.copy(selection = request.selection.copy(offerId = f.id(99))),
            request.copy(selection = request.selection.copy(shoppingRevision = 7L))).forEach {
            assertFalse(canUse(wanted = it)); assertFalse(fence.isCurrent(stamp, it, 10L))
        }
    }
    @Test fun normalizationDoesNotInventAnotherScope() {
        assertTrue(canUse(wanted = request.copy(city = "  Astana \n")))
        assertFalse(canUse(wanted = request.copy(candidateLimit = 41)))
    }
    @Test fun missingResultsOwnersStampsAndRequestsCannotEnableActions() {
        assertFalse(canUse(read = null)); assertFalse(canUse(page = null)); assertFalse(canUse(wanted = null))
        assertFalse(canUse(account = "")); assertFalse(canUse(account = f.id(99)))
        assertFalse(canUse(page = result.copy(accountId = f.id(99))))
    }
    @Test fun remoteInvalidationAndBusyStateDisableBothReadAndActions() {
        assertFalse(canUse(signal = 11L)); assertFalse(canUse(signal = -1L)); assertFalse(canUse(blocked = true))
    }
    @Test fun aListReferenceRequiresItsAccountRevisionAndRetainedLine() {
        val read = fence.capture(listRequest, 10L)
        assertTrue(canUse(read, listResult, listRequest, snapshot = listSnapshot))
        listOf(null, listSnapshot.copy(userId = f.id(99)), listSnapshot.copy(revision = 8L),
            listSnapshot.copy(lines = emptyList())).forEach {
            assertFalse(canUse(read, listResult, listRequest, snapshot = it))
        }
    }
    @Test fun sameRevisionLineChangesAndDuplicateSourceAreNotAccepted() {
        val read = fence.capture(listRequest, 10L)
        val row = listSnapshot.lines.single()
        listOf(row.line.copy(units = 3), row.line.copy(title = "Another product"),
            row.line.copy(storeId = f.id(88)), row.line.copy(updatedAtMillis = 200L),
            row.line.copy(basis = row.line.basis.copy(currencyCode = "USD"))).forEach { changed ->
            assertFalse(canUse(read, listResult, listRequest, snapshot = listSnapshot.copy(lines = listOf(row.copy(line = changed)))))
        }
        assertFalse(canUse(read, listResult, listRequest, snapshot = listSnapshot.copy(lines = listOf(row, row))))
    }
    @Test fun priceOnlyRefreshDoesNotChangeTheRetainedIntent() {
        val row = listSnapshot.lines.single()
        val updated = row.copy(offer = row.offer?.copy(priceMinor = 300L, checkedAtMillis = 5000L),
            unitPriceMinor = 300L, subtotalMinor = 600L)
        assertTrue(confirm(snapshot = listSnapshot.copy(lines = listOf(updated), checkedAtMillis = 5000L)))
    }
    @Test fun withdrawnReferenceIsStillReplaceableWithoutRevivingIt() {
        val row = listResult.reference.copy(offer = null, unitPriceMinor = null, subtotalMinor = null, status = MARKET_QUOTE_UNAVAILABLE)
        val page = listResult.copy(reference = row)
        assertTrue(page.isValidComparisonWindowResult(f.account, listRequest))
        assertTrue(confirm(page, snapshot = listSnapshot.copy(lines = listOf(row))))
    }
    @Test fun confirmationUsesTheExactCandidateAndTheOriginalDeadline() {
        assertTrue(confirm())
        assertTrue(confirm(age = MARKET_COMPARISON_FRESH_MILLIS - 1))
        assertFalse(confirm(age = MARKET_COMPARISON_FRESH_MILLIS)); assertFalse(confirm(blocked = true))
        val row = listResult.matches.single()
        assertFalse(confirm(candidate = row.copy(subtotalMinor = row.subtotalMinor!! + 1)))
        assertFalse(confirm(candidate = row.copy(line = row.line.copy(title = "Changed"))))
    }
    @Test fun refreshedStampCannotBeBorrowedByAnOldReview() {
        val read = fence.capture(listRequest, 10L)
        fence.invalidate(); fence.capture(listRequest, 10L)
        assertFalse(fence.canConfirm(read, listResult, listResult.matches.single(), listRequest, f.account,
            10L, listSnapshot, 0L, false))
    }
    @Test fun browseComparisonCannotCreateAReplacementAndTargetsAreNotMerged() {
        assertFalse(confirm(result))
        assertFalse(confirm(snapshot = listSnapshot.copy(lines = listSnapshot.lines + listResult.matches.single())))
        assertFalse(confirm(candidate = listResult.reference))
    }
    @Test fun unpricedAndQuantityConfirmationCandidatesRemainReadableNotReplaceable() {
        val row = listResult.matches.single()
        val unconfirmed = row.copy(offer = row.offer?.copy(availability = MARKET_AVAILABILITY_CONFIRM),
            status = MARKET_QUOTE_QUANTITY, unitPriceMinor = null, subtotalMinor = null)
        val page = listResult.copy(matches = listOf(unconfirmed))
        assertTrue(page.isValidComparisonWindowResult(f.account, listRequest))
        assertFalse(confirm(page))
    }
    @Test fun repeatedShopMustHaveOneAddressNoteRevisionAndName() {
        val page = f.result(count = 4) // first and fourth candidates have the same shop ID
        assertTrue(page.isValidComparisonWindowResult(f.account, page.request))
        val row = page.matches.last(); val offer = requireNotNull(row.offer)
        listOf(offer.storefront.copy(publicAddress = "Another door"), offer.storefront.copy(pickupNote = "Call first"),
            offer.storefront.copy(revision = 2L), offer.storefront.copy(displayName = "Other shop")).forEach { shop ->
            val changed = row.copy(offer = offer.copy(storefront = shop), line = row.line.copy(shopName = shop.displayName))
            val invalid = page.copy(matches = (page.matches.dropLast(1) + changed).rankedComparison())
            assertFalse(invalid.isValidComparisonWindowResult(f.account, page.request), shop.toString())
        }
    }
    @Test fun differentShopsMayHaveDifferentPickupNotes() {
        val page = f.result(count = 2)
        val row = page.matches.last(); val offer = requireNotNull(row.offer)
        val changed = row.copy(offer = offer.copy(storefront = offer.storefront.copy(pickupNote = "Ask at door")))
        assertTrue(page.copy(matches = page.matches.dropLast(1) + changed).isValidComparisonWindowResult(f.account, page.request))
    }
    @Test fun actionAndReviewFeedbackHasSixExactTranslations() {
        for (key in listOf("market.comparison_action_stale", "market.comparison_review_stale", "market.comparison_quantity_pending")) {
            for (language in listOf("en", "ru", "kk", "tg", "ky", "uz")) {
                val message = assertNotNull(EventMessages.renderExact(EventMessageReference(key), language))
                assertTrue(message.isNotBlank())
                if (language != "en") assertNotEquals(EventMessages.renderExact(EventMessageReference(key), "en"), message)
            }
        }
    }

}
