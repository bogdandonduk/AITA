package kz.aita

import kotlin.test.*

class BuyerMarketNavigationTest {
    private val shop = "00000000-0000-0000-0000-000000000001"
    private val otherShop = "00000000-0000-0000-0000-000000000002"
    private val category = "00000000-0000-0000-0000-000000000003"
    private class Owner(override val accountId: String = "buyer", override val generation: Long = 1) : MarketAccountScope {
        var current = true
        override fun isCurrent() = current
    }
    private fun filtered(browse: BuyerBrowseNavigation) = browse.apply {
        search.value = "Milk"; appliedSearch.value = "Milk"
        city.value = "Almaty"; appliedCity.value = "Almaty"
        categoryId.value = category; sort.value = MARKET_DISCOVERY_TITLE; limit.value = 120
        filtersExpanded = true
    }

    @Test fun marketAndSavedKeepIndependentSearchesAndReturnDestination() {
        val navigation = BuyerMarketNavigation(Owner())
        val market = filtered(navigation.browse(false))
        navigation.entered(false)
        assertEquals(NavigationScreenModel.Buyer.Main.Home, navigation.continueDestination())
        navigation.browse(true).search.value = "Bread"
        navigation.entered(true)
        assertEquals(NavigationScreenModel.Buyer.Main.Saved, navigation.continueDestination())
        assertSame(market, navigation.browse(false))
        assertEquals("Milk", market.search.value)
        assertEquals(120, market.limit.value)
        assertTrue(market.filtersExpanded)
        assertEquals("Bread", navigation.browse(true).search.value)
        assertTrue(navigation.browse(true).query.savedOnly)
    }

    @Test fun shopVisitsRestoreTheOriginalSearchCategorySortAndWindow() {
        val browse = filtered(BuyerBrowseNavigation(false))
        val original = browse.point()
        assertTrue(browse.visitShop(shop))
        assertEquals(MarketDiscoveryQuery(storefrontId = shop), browse.query)
        browse.search.value = "Chocolate"; browse.appliedSearch.value = "Chocolate"
        browse.limit.value = 80
        assertTrue(browse.visitShop(otherShop))
        assertEquals(original, browse.leaveShop())
        assertEquals(original.query, browse.query)
        assertEquals(120, browse.limit.value)
        assertEquals(original, browse.scrollRestore)
    }

    @Test fun pendingTextIsNotLostWhenVisitingAShopBeforeDebounce() {
        val browse = filtered(BuyerBrowseNavigation(false))
        browse.search.value = "Milk chocolate"
        browse.visitShop(shop); browse.leaveShop()
        assertEquals("Milk chocolate", browse.search.value)
        assertEquals("Milk", browse.appliedSearch.value)
    }

    @Test fun typingDuringScrollRestorationKeepsTheNewDraftOnAShopDetour() {
        val browse = filtered(BuyerBrowseNavigation(false))
        browse.scrollRestore = browse.point().copy(index = 28, offset = 17)
        browse.search.value = "New draft before debounce"
        browse.visitShop(shop); browse.leaveShop()
        assertEquals("New draft before debounce", browse.search.value)
        assertEquals("Milk", browse.appliedSearch.value)
        assertEquals(28, browse.scrollRestore?.index)
    }

    @Test fun savedShopVisitShowsAllShopOffersThenReturnsToSavedFilter() {
        val browse = filtered(BuyerBrowseNavigation(true))
        browse.visitShop(shop)
        assertFalse(browse.query.savedOnly)
        assertEquals("", browse.query.city)
        browse.leaveShop()
        assertTrue(browse.query.savedOnly)
        assertEquals("Almaty", browse.query.city)
    }

    @Test fun openingSameShopFromListKeepsItsSearchAndWindow() {
        val navigation = BuyerMarketNavigation(Owner())
        navigation.visitShop(shop)
        navigation.market.search.value = "Coffee"; navigation.market.appliedSearch.value = "Coffee"
        navigation.market.limit.value = 80
        val before = navigation.market.point()
        assertTrue(navigation.visitShop(shop))
        assertEquals(before, navigation.market.point())
    }

    @Test fun listShopLinkDoesNotOverwriteSavedBrowsingContext() {
        val navigation = BuyerMarketNavigation(Owner())
        filtered(navigation.saved)
        val saved = navigation.saved.point()
        navigation.entered(true)
        assertTrue(navigation.visitShop(shop))
        assertEquals(NavigationScreenModel.Buyer.Main.Home, navigation.continueDestination())
        assertEquals(saved, navigation.saved.point())
    }

    @Test fun clearingFiltersResetsSortAndWindowButStaysInSelectedShop() {
        val browse = filtered(BuyerBrowseNavigation(false))
        browse.visitShop(shop)
        filtered(browse)
        assertEquals(2, browse.filterCount) // The hidden city does not filter a shop window.
        browse.clearFilters()
        assertEquals(MarketDiscoveryQuery(storefrontId = shop), browse.query)
        assertEquals(0, browse.filterCount)
        assertEquals(MARKET_DISCOVERY_PAGE_SIZE, browse.limit.value)
        browse.leaveShop()
        assertEquals("Milk", browse.search.value) // Original global search is still recoverable.
    }

    @Test fun directoryQueryAndScrollOwnerSurviveProductsAndShoppingDetours() {
        val navigation = BuyerMarketNavigation(Owner())
        navigation.market.section.value = "shops"
        val directory = navigation.market.directory
        directory.text = "Corner"; directory.city = "Almaty"
        directory.request = MarketShopDirectoryRequest("Corner", "Almaty", 80)
        navigation.visitShop(shop); navigation.market.leaveShop()
        assertSame(directory, navigation.market.directory)
        assertEquals("shops", navigation.market.section.value)
        assertEquals(80, directory.request.limit)
        assertEquals("Corner", directory.text)
    }

    @Test fun expiredOwnerCannotNavigateUsingRetainedCallbacks() {
        val owner = Owner(); val navigation = BuyerMarketNavigation(owner)
        navigation.entered(true); owner.current = false
        assertFalse(navigation.visitShop(shop))
        navigation.entered(false)
        assertTrue(navigation.lastSavedOnly)
        assertNull(navigation.continueDestination())
        assertNull(navigation.market.shopId.value)
    }

    @Test fun newAccountOrNewLoginStartsWithNoOtherSessionsSearch() {
        val old = BuyerMarketNavigation(Owner())
        filtered(old.market); old.visitShop(shop)
        for (owner in listOf(Owner("other"), Owner("buyer", 2))) {
            val fresh = BuyerMarketNavigation(owner)
            assertEquals(MarketDiscoveryQuery(), fresh.market.query)
            assertEquals("", fresh.saved.search.value)
            assertNull(fresh.market.returnPoint.value)
            assertNotSame(old.market.grid, fresh.market.grid)
        }
        assertFalse(BuyerMarketNavigation(null).visitShop(shop))
    }

    @Test fun invalidShopLinkLeavesSearchAndReturnPointUntouched() {
        val navigation = BuyerMarketNavigation(Owner()); filtered(navigation.market)
        val original = navigation.market.point()
        for (id in listOf("", "bad/id", "not-a-uuid")) assertFalse(navigation.visitShop(id))
        assertEquals(original, navigation.market.point())
        assertNull(navigation.market.returnPoint.value)
    }

    @Test fun newCopyHasExactTranslationsInEverySupportedLanguage() {
        val keys = listOf("intro", "filters", "hide_filters", "continue", "list_count")
        for (language in listOf("en", "ru", "kk", "ky", "tg", "uz")) for (key in keys) {
            val args = if (key == "list_count") mapOf("count" to "7") else emptyMap()
            val text = assertNotNull(EventMessages.renderExact(EventMessageReference("market.browse_$key", args), language))
            assertTrue(text.isNotBlank()); assertFalse(text.contains('{'))
        }
    }
}
