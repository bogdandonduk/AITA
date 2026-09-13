package kz.aita

import kotlin.test.*

class MarketBasketChangeTest {
    private val a = BasketTestData.row(1, units = 2)
    private val b = BasketTestData.row(2)
    private val result = BasketTestData.plan(BasketTestData.snapshot(a, b), listOf(
        BasketTestData.alternative(a, 10, 100), BasketTestData.alternative(b, 10, 200)))
    private val command get() = requireNotNull(result.reviewedBasketCommand("KZT", MARKET_BASKET_ONE_SHOP, BasketTestData.id(990)))
    private val change get() = requireNotNull(command.basketChange)
    private fun quotes() = result.currencies.single().oneShop.choices.map { it.quote }

    @Test fun wholeCurrencyReviewRetainsEveryQuantityAndReviewedSubtotal() {
        assertTrue(command.isValidMarketShoppingCommand()); assertEquals(2, change.changedLines)
        assertEquals(400L, change.reviewedItemsSubtotalMinor); assertEquals(listOf(2, 1), change.lines.map { it.units })
        assertNull(change.basketIntentError(result.snapshot, result.snapshot.revision))
        assertNull(change.basketQuotesError(quotes()))
    }
    @Test fun mutationUsesDedicatedEndpointAndCannotFallBackToSingleLine() {
        assertEquals("market/shopping-list/apply-plan", command.shoppingMutationEndpoint())
        assertFalse(command.copy(basketChange = null).isValidMarketShoppingCommand())
        assertFalse(command.copy(offerId = a.line.offerId).isValidMarketShoppingCommand())
        assertFalse(command.copy(units = 1).isValidMarketShoppingCommand())
        assertFalse(command.copy(basis = a.line.basis).isValidMarketShoppingCommand())
        assertFalse(command.copy(replaceOfferId = a.line.offerId).isValidMarketShoppingCommand())
    }
    @Test fun legacyCommandsStillSelectTheirOwnEndpoints() {
        val simple = MarketShoppingCommand("legacy", 0, "offer", 0)
        assertEquals("market/shopping-list", simple.shoppingMutationEndpoint()); assertTrue(simple.isValidMarketShoppingCommand())
        val replacement = MarketShoppingCommand("legacy", 0, "target", 1, a.line.basis, "source", 10)
        assertEquals("market/shopping-list/replace", replacement.shoppingMutationEndpoint()); assertTrue(replacement.isValidMarketShoppingCommand())
    }
    @Test fun currentAndUnchangedPlanCannotGenerateNoOpBasketCommand() {
        assertNull(result.reviewedBasketCommand("KZT", MARKET_BASKET_CURRENT, BasketTestData.id(990)))
        val same = BasketTestData.plan(BasketTestData.snapshot(a))
        assertNull(same.reviewedBasketCommand("KZT", MARKET_BASKET_LOWEST_ITEMS, BasketTestData.id(990)))
    }
    @Test fun partialCheaperBasketCannotDropAMissingItem() {
        val partial = BasketTestData.plan(BasketTestData.snapshot(a, b), listOf(BasketTestData.alternative(a, 10, 1)))
        assertFalse(partial.currencies.single().oneShop.complete)
        assertNull(partial.reviewedBasketCommand("KZT", MARKET_BASKET_ONE_SHOP, BasketTestData.id(990)))
    }
    @Test fun missingCurrencyAndUnknownPlanAreRejected() {
        assertNull(result.reviewedBasketCommand("USD", MARKET_BASKET_ONE_SHOP, BasketTestData.id(990)))
        assertNull(result.reviewedBasketCommand("KZT", "future", BasketTestData.id(990)))
    }
    @Test fun otherCurrencyIsNotAddedToTheReviewOrTotal() {
        val usd = BasketTestData.row(3, currency = "USD")
        val mixed = BasketTestData.plan(BasketTestData.snapshot(a, b, usd), result.currencies.single().oneShop.choices)
        val selected = requireNotNull(mixed.reviewedBasketCommand("KZT", MARKET_BASKET_ONE_SHOP, BasketTestData.id(990))).basketChange!!
        assertEquals(2, selected.lines.size); assertEquals(400L, selected.reviewedItemsSubtotalMinor)
        assertNull(selected.basketIntentError(mixed.snapshot, 7L))
    }
    @Test fun changedRevisionIncludingOtherCurrencyNeedsNewReview() {
        assertEquals("market.shopping_changed", change.basketIntentError(result.snapshot.copy(revision = 8), 7))
        assertFalse(command.copy(expectedRevision = Long.MAX_VALUE).isValidMarketShoppingCommand())
    }
    @Test fun missingExtraReorderedOrChangedSourceIsRejected() {
        assertEquals("market.shopping_changed", change.basketIntentError(result.snapshot.copy(lines = listOf(a)), 7))
        assertEquals("market.shopping_changed", change.basketIntentError(result.snapshot.copy(lines = listOf(b, a)), 7))
        assertEquals("market.shopping_changed", change.basketIntentError(result.snapshot.copy(lines = listOf(a.copy(line = a.line.copy(units = 3)), b)), 7))
        assertEquals("market.shopping_changed", change.basketIntentError(result.snapshot.copy(lines = listOf(a.copy(line = a.line.copy(basis = a.line.basis.copy(pricedAmount = 0.5))), b)), 7))
    }
    @Test fun targetsAlreadyInAnyListGroupCannotBeMergedOrOverwritten() {
        val extra = quotes().first()
        val snapshot = result.snapshot.copy(lines = result.snapshot.lines + extra.copy(line = extra.line.copy(basis = extra.line.basis.copy(currencyCode = "USD"))))
        assertEquals("market.comparison_already_listed", change.basketIntentError(snapshot, 7))
    }
    @Test fun duplicateTargetsAndSourcesAreInvalidEvenWithMatchingTotals() {
        assertFalse(change.copy(lines = change.lines + change.lines).isValidBasketChange())
        assertFalse(change.copy(lines = change.lines.map { it.copy(targetOfferId = change.lines.first().targetOfferId) }).isValidBasketChange())
    }
    @Test fun crossCurrencyOrMalformedIdsAndUnboundedTitlesFailShapeCheck() {
        val row = change.lines.first()
        for (invalid in listOf(row.copy(targetOfferId = "not-uuid"), row.copy(targetStoreId = ""),
            row.copy(reviewedTitle = "x".repeat(181)), row.copy(storefrontRevision = -1),
            row.copy(units = 0), row.copy(basis = row.basis.copy(currencyCode = "USD")))) {
            assertFalse(change.copy(lines = listOf(invalid) + change.lines.drop(1)).isValidBasketChange())
        }
    }
    @Test fun repeatedIdentityCannotBeIndependentlyReplaced() {
        val repeated = result.snapshot.copy(lines = listOf(a, b.copy(line = b.line.copy(basis = a.line.basis))))
        val altered = change.copy(lines = listOf(change.lines.first(), change.lines.last().copy(basis = a.line.basis)))
        assertEquals("market.basket_apply_invalid", altered.basketIntentError(repeated, 7))
    }
    @Test fun noBarcodeCannotBeReplacedByANameMatch() {
        assertFalse(change.copy(lines = change.lines.map { it.copy(basis = it.basis.copy(gtin = null)) }).isValidBasketChange())
    }
    @Test fun zeroPositiveNegativeAndOverflowingTotalsAreHandled() {
        assertFalse(change.copy(reviewedItemsSubtotalMinor = -1).isValidBasketChange())
        assertFalse(change.copy(reviewedItemsSubtotalMinor = 0).isValidBasketChange())
        assertTrue(change.copy(reviewedItemsSubtotalMinor = 0, lines = change.lines.map { it.copy(reviewedSubtotalMinor = 0) }).isValidBasketChange())
        assertFalse(change.copy(reviewedItemsSubtotalMinor = Long.MAX_VALUE, lines = change.lines.map { it.copy(reviewedSubtotalMinor = Long.MAX_VALUE) }).isValidBasketChange())
    }
    @Test fun oneAndTwoShopLimitsCannotBeMisrepresented() {
        assertFalse(change.copy(lines = change.lines.mapIndexed { index, row -> row.copy(targetStoreId = BasketTestData.id(800 + index)) }).isValidBasketChange())
        assertTrue(change.copy(kind = MARKET_BASKET_TWO_SHOPS, lines = change.lines.mapIndexed { index, row -> row.copy(targetStoreId = BasketTestData.id(800 + index)) }).isValidBasketChange())
    }
    @Test fun normalizedCityAndProtocolAreRequired() {
        assertFalse(change.copy(city = " Astana ").isValidBasketChange())
        assertFalse(change.copy(city = "A\u0000B").isValidBasketChange())
        assertNull(result.copy(protocolVersion = 100).reviewedBasketCommand("KZT", MARKET_BASKET_ONE_SHOP, BasketTestData.id(990)))
    }
    @Test fun reviewExpiryAndFutureTimeAreBoundedWithoutOverflow() {
        assertTrue(change.isWithinBasketReviewTime(BasketTestData.now + MARKET_BASKET_REVIEW_MAX_AGE_MILLIS))
        assertFalse(change.isWithinBasketReviewTime(BasketTestData.now + MARKET_BASKET_REVIEW_MAX_AGE_MILLIS + 1))
        assertFalse(change.copy(checkedAtMillis = Long.MAX_VALUE).isWithinBasketReviewTime(1))
        assertFalse(change.isWithinBasketReviewTime(0)); assertFalse(change.copy(checkedAtMillis = 0).isWithinBasketReviewTime(1))
        assertTrue(change.isWithinBasketReviewTime(BasketTestData.now - 5_000))
        assertFalse(change.isWithinBasketReviewTime(BasketTestData.now - 5_001))
    }
    @Test fun priceChangeInAnyReviewedLineRejectsTheWholeReview() {
        val rows = quotes().toMutableList(); val last = rows.last()
        rows[1] = last.copy(unitPriceMinor = 201, subtotalMinor = 201, offer = last.offer?.copy(priceMinor = 201))
        assertEquals("market.basket_apply_price_changed", change.basketQuotesError(rows))
    }
    @Test fun unavailableQuantityOrWithdrawalRejectsWholeReview() {
        val rows = quotes().toMutableList()
        rows[1] = rows[1].copy(status = MARKET_QUOTE_QUANTITY, subtotalMinor = null)
        assertEquals("market.basket_apply_unavailable", change.basketQuotesError(rows))
        rows[1] = quotes()[1].copy(offer = null)
        assertEquals("market.basket_apply_unavailable", change.basketQuotesError(rows))
    }
    @Test fun shopRevisionAndProductTitleChangesRequireReview() {
        for (altered in listOf(quotes()[0].offer!!.copy(title = "Different packaging label"),
            quotes()[0].offer!!.let { it.copy(storefront = it.storefront.copy(revision = 1)) })) {
            assertEquals("market.basket_apply_offer_changed", change.basketQuotesError(listOf(quotes()[0].copy(offer = altered), quotes()[1])))
        }
    }
    @Test fun reQuoteMustHonorReviewedCityAndIdentity() {
        val constrained = change.copy(city = "Astana")
        val first = quotes()[0]; val offer = first.offer!!
        assertEquals("market.basket_apply_offer_changed", constrained.basketQuotesError(listOf(first.copy(offer = offer.copy(storefront = offer.storefront.copy(city = "Almaty"))), quotes()[1])))
        assertEquals("market.basket_apply_unavailable", change.basketQuotesError(listOf(first.copy(offer = offer.copy(pricedAmount = 2.0)), quotes()[1])))
    }
    @Test fun quoteOrderOrMissingUnchangedLineCannotChangeReviewedTotal() {
        assertEquals("market.basket_apply_unavailable", change.basketQuotesError(quotes().reversed()))
        assertEquals("market.basket_apply_unavailable", change.basketQuotesError(quotes().take(1)))
    }
    @Test fun unchangedLinesAreIncludedAndReQuotedToo() {
        val partialMove = BasketTestData.plan(BasketTestData.snapshot(a, b), listOf(BasketTestData.alternative(a, 10, 100)))
        val cmd = partialMove.reviewedBasketCommand("KZT", MARKET_BASKET_LOWEST_ITEMS, BasketTestData.id(990))!!
        val changes = cmd.basketChange!!
        assertEquals(1, changes.changedLines); assertEquals(2, changes.lines.size)
        val priced = partialMove.currencies.single().lowestItems.choices.map { it.quote }.toMutableList()
        val old = priced.last(); priced[1] = old.copy(unitPriceMinor = 2000, subtotalMinor = 2000, offer = old.offer?.copy(priceMinor = 2000))
        assertEquals("market.basket_apply_price_changed", changes.basketQuotesError(priced))
    }
}
