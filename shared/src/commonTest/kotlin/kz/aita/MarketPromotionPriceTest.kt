package kz.aita

import kotlin.test.*
import kotlinx.serialization.encodeToString

class MarketPromotionPriceTest {
    private val offer = MarketOffer("offer", MarketStorefront("store"), "Title", priceMinor=800,
        currencyCode="KZT", pricedAmount=1.0, unitId="0", checkedAtMillis=1, sourceUpdatedAtMillis=1, originalPriceMinor=1000)
    @Test fun onlyRealPriceReductionsAreAccepted() {
        assertTrue(offer.hasValidDiscoveryPrice())
        assertFalse(offer.copy(originalPriceMinor=800).hasValidDiscoveryPrice())
        assertFalse(offer.copy(originalPriceMinor=700).hasValidDiscoveryPrice())
        assertFalse(offer.copy(priceMinor=null).hasValidDiscoveryPrice())
        assertTrue(offer.copy(originalPriceMinor=null).hasValidDiscoveryPrice())
    }
    @Test fun advertisedBasePriceSurvivesWireRoundTrip() {
        assertEquals(offer, jsonBase.decodeFromString<MarketOffer>(jsonBase.encodeToString(offer)))
    }
}
