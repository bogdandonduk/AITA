package kz.aita

import kotlin.test.*

class MarketShoppingListViewTest {
    private fun row(id: String, status: String = MARKET_QUOTE_ESTIMATED, store: String = "shop-a", currency: String = "KZT") =
        MarketShoppingQuotedLine(MarketShoppingLine(id, store, if (id == "milk") "Milk Молоко" else "Bread Хлеб",
            if (store == "shop-a") "Corner Shop" else "Bakery", 2,
            MarketShoppingBasis("04607060310215", currency, "piece", 1.0)), subtotalMinor = 200, status = status)
    private fun snapshot(vararg rows: MarketShoppingQuotedLine) = MarketShoppingSnapshot("buyer", 9, rows.toList())

    @Test fun combinesWordsAcrossTitleAndShopWithoutMutatingSnapshot() {
        val milk = row("milk"); val bread = row("bread")
        val original = snapshot(milk, bread)
        val view = MarketShoppingListView().apply { search = "  МОЛОКО   corner " }
        assertEquals(listOf(milk), view.rows(original))
        assertSame(milk, view.rows(original).single())
        assertEquals(listOf(milk, bread), original.lines)
        assertEquals(9L, original.revision)
        view.search = "4607060310215"
        assertEquals(2, view.rows(original).size)
    }
    @Test fun everyUncertainStatusAndMissingSubtotalNeedsReview() {
        val statuses = listOf(MARKET_QUOTE_ESTIMATED, MARKET_QUOTE_QUANTITY, MARKET_QUOTE_PRICE,
            MARKET_QUOTE_UNAVAILABLE, MARKET_QUOTE_CHANGED, "future-status")
        val input = snapshot(*statuses.mapIndexed { i, status -> row(i.toString(), status) }.toTypedArray())
        val view = MarketShoppingListView().apply { attentionOnly = true }
        assertEquals(statuses.drop(1), view.rows(input).map { it.status })
        assertTrue(row("missing").copy(subtotalMinor = null).needsShoppingReview())
    }
    @Test fun shopReviewSelectsExactCurrencyGroupAndResetsPreviousFilters() {
        val input = snapshot(row("milk"), row("usd", currency = "USD"), row("other", store = "shop-b"))
        val view = MarketShoppingListView().apply { search = "other"; attentionOnly = true; section = "estimate" }
        view.reviewShop("shop-a", "KZT")
        assertEquals("list", view.section)
        assertEquals(listOf("milk"), view.rows(input).map { it.line.offerId })
        view.clear()
        assertEquals(3, view.rows(input).size); assertFalse(view.filtered)
    }
    @Test fun removedShopAndEmptySearchResultsDoNotRevealAnotherShopsRows() {
        val view = MarketShoppingListView().apply { reviewShop("removed", "KZT") }
        assertTrue(view.rows(snapshot(row("milk"))).isEmpty())
        assertTrue(view.rows(null).isEmpty())
        view.clear(); view.search = "unknown"
        assertTrue(view.rows(snapshot(row("milk"))).isEmpty())
    }
    @Test fun aNewAccountWorkspaceDoesNotInheritListFilters() {
        val old = BuyerMarketNavigation(null).shoppingList.apply { search = "private"; attentionOnly = true }
        assertTrue(old.filtered)
        assertFalse(BuyerMarketNavigation(null).shoppingList.filtered)
    }
}
