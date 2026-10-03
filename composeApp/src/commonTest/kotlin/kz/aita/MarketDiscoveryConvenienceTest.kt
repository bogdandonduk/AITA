package kz.aita

import kotlin.test.*

class MarketDiscoveryConvenienceTest {
    private val offer = MarketOffer("offer", MarketStorefront("shop"), "Tea", priceMinor = 800,
        originalPriceMinor = 1000, currencyCode = "KZT", pricedAmount = 1.0, checkedAtMillis = 1, sourceUpdatedAtMillis = 1)
    @Test fun promotionBadgeUsesActualQuoteAndNeverInventsDiscounts() {
        assertEquals(20, marketPromotionPercent(offer))
        assertNull(marketPromotionPercent(offer.copy(originalPriceMinor = 500)))
        assertNull(marketPromotionPercent(offer.copy(priceMinor = null)))
        assertNull(marketPromotionPercent(offer.copy(pricedAmount = 0.0)))
        assertEquals(100, marketPromotionPercent(offer.copy(priceMinor = 0)))
    }
    @Test fun recentSearchesAreBoundedAndMostRecentSpellingWins() {
        val old = (1..10).map { "Tea $it" }
        val next = rememberMarketSearch(old, "  tea 4  ")
        assertEquals("tea 4", next.first()); assertEquals(8, next.size)
        assertEquals(1, next.count { it.equals("tea 4", true) })
        assertEquals(next, rememberMarketSearch(next, "   "))
    }
}
