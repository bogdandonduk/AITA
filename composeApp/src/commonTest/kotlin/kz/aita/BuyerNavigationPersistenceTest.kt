package kz.aita

import kotlin.test.*

class BuyerNavigationPersistenceTest {
    @Test fun shopAndItsBackDestinationSurviveANewSessionWithoutOffersOrCommands() {
        val original = BuyerMarketNavigation(null)
        original.market.search.value = "Tea"
        original.market.appliedSearch.value = "Tea"
        original.market.city.value = "Almaty"
        original.market.appliedCity.value = "Almaty"
        original.market.section.value = "shops"
        original.market.visitShop("00000000-0000-4000-8000-000000000001")
        original.shoppingList.section = "activity"
        original.shoppingList.attentionOnly = true
        val raw = original.savePlace()
        val restored = BuyerMarketNavigation(null)
        restored.restorePlace(raw)
        assertEquals(original.market.shopId.value, restored.market.shopId.value)
        assertEquals("shops", restored.market.section.value)
        assertEquals("activity", restored.shoppingList.section)
        assertTrue(restored.shoppingList.attentionOnly)
        assertNull(restored.market.directory.result)
        assertEquals("Tea", restored.market.leaveShop()?.search)
        assertEquals("Almaty", restored.market.city.value)
    }
    @Test fun invalidSavedPresentationCannotPreventOpeningTheWorkspace() {
        val restored = BuyerMarketNavigation(null)
        restored.restorePlace("broken")
        assertEquals("products", restored.market.section.value)
        assertNull(restored.market.shopId.value)
    }

    @Test fun brandAndPhotosSurviveShopBackReloadAndClearTogether() {
        val original = BuyerMarketNavigation(null)
        original.market.brand.value = "Aq Tea"; original.market.appliedBrand.value = "Aq Tea"
        original.market.withPhotos.value = true
        original.market.visitShop("00000000-0000-4000-8000-000000000001")
        assertEquals("", original.market.query.brand); assertFalse(original.market.query.withPhotos)
        val restored = BuyerMarketNavigation(null)
        restored.restorePlace(original.savePlace()); restored.market.leaveShop()
        assertEquals("Aq Tea", restored.market.query.brand); assertTrue(restored.market.query.withPhotos)
        assertEquals(2, restored.market.filterCount)
        restored.market.clearFilters()
        assertEquals(0, restored.market.filterCount); assertFalse(restored.market.query.withPhotos)
    }

}
