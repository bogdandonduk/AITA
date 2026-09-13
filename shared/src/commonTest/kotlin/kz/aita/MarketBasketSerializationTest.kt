package kz.aita

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlin.test.*

/** Exercise these with the real pinned serialization plugin/runtime, not an annotation stub. */
class MarketBasketSerializationTest {
    @Test fun requestHasNoAccountStoreOwnerOrMutationIdentity() {
        val encoded=jsonBase.encodeToString(MarketBasketRequest(5,"Astana"))
        assertEquals(MarketBasketRequest(5,"Astana"),jsonBase.decodeFromString<MarketBasketRequest>(encoded))
        listOf("userId","storeId","commandId","promo","payment").forEach { assertFalse(encoded.contains(it)) }
    }
    @Test fun fullResultRoundTripPreservesPartialCoverageAndPerCurrencyTotals() {
        val first=BasketTestData.row(1)
        val missing=BasketTestData.row(2,currency="USD").copy(offer=null,subtotalMinor=null,unitPriceMinor=null,status=MARKET_QUOTE_UNAVAILABLE)
        val value=BasketTestData.plan(BasketTestData.snapshot(first,missing),listOf(BasketTestData.alternative(first,10,500)))
        val restored=jsonBase.decodeFromString<MarketBasketResult>(jsonBase.encodeToString(value))
        assertEquals(value,restored);assertTrue(restored.isValidBasketResult(BasketTestData.account,value.request))
        assertNull(restored.currencies.last().lowestItems.itemsSubtotalMinor)
    }
    @Test fun largeMinorAmountsRemainIntegersThroughTheWire() {
        val source=BasketTestData.row(1,price=1_000_000_000_000L,units=999)
        val value=BasketTestData.plan(BasketTestData.snapshot(source))
        val restored=jsonBase.decodeFromString<MarketBasketResult>(jsonBase.encodeToString(value))
        assertEquals(999_000_000_000_000L,restored.currencies.single().current.itemsSubtotalMinor)
        assertTrue(restored.isValidBasketResult(BasketTestData.account,value.request))
    }
}
