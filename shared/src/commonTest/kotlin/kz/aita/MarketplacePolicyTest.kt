package kz.aita

import kotlin.test.*

class MarketplacePolicyTest {
    private fun offer()=MarketOffer("offer",MarketStorefront("branch"),"Product",gtin="4006381333931",priceMinor=19999,
        currencyCode="KZT",pricedAmount=1.0,unitId="piece",checkedAtMillis=1,sourceUpdatedAtMillis=1)
    private fun batch()=GoodsBatchDataModel(goodsItemId="item",storeId="branch",quantity=QuantityDataModel("piece",emptyList(),2.0,1.0,true),supplyPrice=PriceDataModel("9","KZT",""))
    @Test fun standardGtinWidthsNormalizeToSameIdentity() {
        assertEquals("04006381333931",marketCanonicalGtin("4006381333931"))
        assertEquals("00036000291452",marketCanonicalGtin("036000291452"))
        assertEquals(marketCanonicalGtin("036000291452"),marketCanonicalGtin("0036000291452"))
        assertEquals("00000096385074",marketCanonicalGtin("96385074"))
    }
    @Test fun invalidCheckDigitDoesNotMergeDifferentProducts() { assertNull(marketCanonicalGtin("4006381333932")) }
    @Test fun emptyZerosLettersAndUnicodeDigitsNeverCountAsGtin() {
        listOf("", "00000000","00000000000000","１２３４５６７８","ABCD1234","1234567","123456789012345").forEach{assertNull(marketCanonicalGtin(it),it)}
    }
    @Test fun whitespaceTrimIsOnlyOuterFormattingChange() {
        assertEquals(marketCanonicalGtin("4006381333931"),marketCanonicalGtin(" 4006381333931 "))
        assertNull(marketCanonicalGtin("400638 333931"))
    }
    @Test fun comparisonRequiresKnownValidPriceUnitAndCurrency() {
        assertNotNull(offer().comparisonKey());assertNull(offer().copy(priceMinor=null).comparisonKey())
        assertNull(offer().copy(priceMinor=-1).comparisonKey());assertNull(offer().copy(currencyCode="tenge").comparisonKey())
        assertNull(offer().copy(unitId="").comparisonKey());assertNull(offer().copy(gtin=null).comparisonKey())
    }
    @Test fun differentPackUnitsCurrenciesAndSellingQuantitiesStaySeparate() {
        val key=offer().comparisonKey()
        assertNotEquals(key,offer().copy(pricedAmount=6.0).comparisonKey())
        assertNotEquals(key,offer().copy(currencyCode="USD").comparisonKey())
        assertNotEquals(key,offer().copy(unitId="box").comparisonKey())
    }
    @Test fun retailerNamesAndPricesDoNotCreateProductIdentity() {
        assertEquals(offer().comparisonKey(),offer().copy(title="Other seller title",priceMinor=1).comparisonKey())
        assertNull(offer().copy(gtin=null,title="Product").comparisonKey())
    }
    @Test fun nonFiniteOrInvalidSellingAmountsAreNotCompared() {
        listOf(Double.NaN,Double.POSITIVE_INFINITY,0.0,-1.0).forEach{assertNull(offer().copy(pricedAmount=it).comparisonKey())}
    }
    @Test fun genuineZeroPriceIsNotConfusedWithUnknown() { assertNotNull(offer().copy(priceMinor=0).comparisonKey()) }
    @Test fun physicalStockNeverIncludesAnotherBranch() {
        assertTrue(batch().isMarketSellableAt("branch",100));assertFalse(batch().isMarketSellableAt("parent",100))
    }
    @Test fun expectedOrInTransitStockNeverClaimsAvailability() {
        StockBatchStatusDataModel.values().filter{it !in setOf(StockBatchStatusDataModel.Delivered,StockBatchStatusDataModel.OnShelf)}.forEach {
            assertFalse(batch().copy(status=it).isMarketSellableAt("branch",100))
        }
    }
    @Test fun expiryIsExclusive() {
        assertTrue(batch().copy(expirationDateMillis=101).isMarketSellableAt("branch",100))
        assertFalse(batch().copy(expirationDateMillis=100).isMarketSellableAt("branch",100))
    }
    @Test fun inactiveEmptyAndInvalidQuantityNeverClaimAvailability() {
        assertFalse(batch().copy(isActive=false).isMarketSellableAt("branch",100))
        listOf(Double.NaN,Double.POSITIVE_INFINITY,0.0,-1.0).forEach {
            assertFalse(batch().copy(quantity=batch().quantity.copy(total=it)).isMarketSellableAt("branch",100))
            assertFalse(batch().copy(quantity=batch().quantity.copy(pricedAmount=it)).isMarketSellableAt("branch",100))
        }
    }
    @Test fun buyerRecoveryNeverDependsOnSelectedStoreSubscription() {
        listOf("market/offers","market/saved","market/saved/clear-unavailable","market/shopping-list","market/shops/example","market/offers/example").forEach{assertFalse(storeSubscriptionRequiredForEndpoint(it))}
        listOf("market/seller","market/seller/listing","/MARKET/seller/storefront?test=1").forEach{assertTrue(storeSubscriptionRequiredForEndpoint(it))}
    }
}
