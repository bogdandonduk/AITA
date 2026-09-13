package kz.aita

import kotlin.random.Random
import kotlin.test.*

internal object BasketTestData {
    fun id(n: Int) = "00000000-0000-0000-0000-${n.toString(16).padStart(12, '0')}"
    const val now = 100_000L
    val account = id(1)
    fun gtin(n: Int): String {
        val value = n.toString().padStart(13, '0')
        val sum = value.reversed().mapIndexed { i, c -> (c - '0') * if (i % 2 == 0) 3 else 1 }.sum()
        return value + ((10 - sum % 10) % 10)
    }
    fun row(n: Int, shop: Int = n, price: Long = 1000L, units: Int = 1, currency: String = "KZT",
        code: String? = gtin(n), city: String = "Astana"): MarketShoppingQuotedLine {
        val offer = MarketOffer(id(100 + n), MarketStorefront(id(1000 + shop), "Shop $shop", city, "Public address", published = true),
            "Product $n", gtin = code, priceMinor = price, currencyCode = currency, pricedAmount = 1.0, unitId = "piece",
            unitName = listOf(LocalizedStringDataModel("en", "piece")), availability = MARKET_AVAILABILITY_RECORDED,
            checkedAtMillis = now, sourceUpdatedAtMillis = 1L)
        val line = MarketShoppingLine(offer.id, offer.storefront.storeId, offer.title, offer.storefront.displayName,
            units, requireNotNull(offer.shoppingBasis()), offer.unitName)
        return MarketShoppingQuotedLine(line, offer, price, marketShoppingSubtotal(price, units), MARKET_QUOTE_ESTIMATED)
    }
    fun alternative(source: MarketShoppingQuotedLine, shop: Int, price: Long, city: String = "Astana"): MarketBasketChoice {
        val offer = requireNotNull(source.offer).copy(id = id(10_000 + shop * 100 + source.line.offerId.takeLast(4).toInt(16)),
            storefront = MarketStorefront(id(1000 + shop), "Shop $shop", city, "Public address", published = true), priceMinor = price)
        return MarketBasketChoice(source.line.offerId, MarketShoppingQuotedLine(source.line.copy(offerId = offer.id,
            storeId = offer.storefront.storeId, shopName = offer.storefront.displayName), offer, price,
            marketShoppingSubtotal(price, source.line.units), MARKET_QUOTE_ESTIMATED))
    }
    fun snapshot(vararg rows: MarketShoppingQuotedLine) = MarketShoppingSnapshot(account, 7L, rows.toList(), now)
    fun plan(snapshot: MarketShoppingSnapshot, choices: List<MarketBasketChoice> = emptyList(), city: String = "") =
        buildMarketBasketResult(snapshot, MarketBasketRequest(snapshot.revision, city), choices, emptyList(), choices.size)
}

class MarketBasketPlannerTest {
    private val a = BasketTestData.row(1)
    private val b = BasketTestData.row(2)
    private fun plan(rows: List<MarketShoppingQuotedLine>, vararg alternatives: MarketBasketChoice) =
        BasketTestData.plan(BasketTestData.snapshot(*rows.toTypedArray()), alternatives.toList()).currencies.single()
    private fun alt(row: MarketShoppingQuotedLine, shop: Int, price: Long) = BasketTestData.alternative(row, shop, price)

    @Test fun emptyListHasNoCurrencyNoZeroTotalAndNoPhantomShop() {
        val result = BasketTestData.plan(BasketTestData.snapshot())
        assertTrue(result.currencies.isEmpty()); assertTrue(result.isValidBasketResult(BasketTestData.account, result.request))
    }
    @Test fun unchangedListIsStillAUsefulBaseline() {
        val group = plan(listOf(a, b))
        assertEquals(2000L, group.current.itemsSubtotalMinor); assertTrue(group.twoShops.complete)
        assertEquals(0, group.lowestItems.changedLines); assertEquals(0L, group.lowestItems.savingsAgainst(group.current))
    }
    @Test fun fullOneShopCoverageBeatsMisleadingCheapPartialBasket() {
        val group = plan(listOf(a,b), alt(a,10,1), alt(a,11,1500), alt(b,11,1500))
        assertTrue(group.oneShop.complete); assertEquals(3000L, group.oneShop.itemsSubtotalMinor)
        assertEquals(listOf(BasketTestData.id(1011)), group.oneShop.storeIds)
    }
    @Test fun twoShopsCanLowerTheCompleteBasketWithoutMutatingSources() {
        val input = BasketTestData.snapshot(a,b)
        val result = BasketTestData.plan(input, listOf(alt(a,10,100), alt(b,11,200)))
        val group = result.currencies.single()
        assertEquals(300L, group.twoShops.itemsSubtotalMinor); assertEquals(1700L, group.twoShops.savingsAgainst(group.current))
        assertEquals(input, result.snapshot); assertEquals(2, group.twoShops.changedLines)
    }
    @Test fun lowestItemsMayRequireMoreShopsThanTwoShopPlan() {
        val c = BasketTestData.row(3)
        val group = plan(listOf(a,b,c), alt(a,10,100), alt(b,11,100), alt(c,12,100), alt(a,13,500),alt(b,13,500),alt(c,13,500))
        assertEquals(3, group.lowestItems.storeIds.size); assertEquals(300L, group.lowestItems.itemsSubtotalMinor)
        assertTrue(group.twoShops.complete); assertTrue(requireNotNull(group.twoShops.itemsSubtotalMinor) > 300L)
    }
    @Test fun partialTotalNeverClaimsSavingsAgainstCompleteBasket() {
        val group = plan(listOf(a,b), alt(a,10,1))
        assertFalse(group.oneShop.complete); assertEquals(1L, group.oneShop.itemsSubtotalMinor)
        assertNull(group.oneShop.savingsAgainst(group.current)); assertEquals(listOf(b.line.offerId),group.oneShop.missingOfferIds)
    }
    @Test fun quantityIsPreservedAndPricedAsSellingMultiples() {
        val source = BasketTestData.row(1,units=3)
        val group = plan(listOf(source), alt(source,10,240))
        assertEquals(720L,group.oneShop.itemsSubtotalMinor); assertEquals(3,group.oneShop.choices.single().quote.line.units)
    }
    @Test fun equalPricePerLineKeepsTheOriginalOffer() {
        val group = plan(listOf(a),alt(a,10,1000))
        assertEquals(a.line.offerId,group.lowestItems.choices.single().quote.line.offerId)
    }
    @Test fun deterministicOrderDoesNotDependOnCandidateArrivalOrder() {
        val input = BasketTestData.snapshot(a,b)
        val choices = listOf(alt(a,10,300),alt(b,10,300),alt(a,11,300),alt(b,11,300))
        assertEquals(BasketTestData.plan(input,choices),BasketTestData.plan(input,choices.reversed()))
    }
    @Test fun unavailableOriginalCanBeCoveredByAPublicAlternative() {
        val unavailable = a.copy(offer=null,unitPriceMinor=null,subtotalMinor=null,status=MARKET_QUOTE_UNAVAILABLE)
        val group = plan(listOf(unavailable), alt(a,10,300))
        assertFalse(group.current.complete); assertTrue(group.oneShop.complete); assertNull(group.oneShop.savingsAgainst(group.current))
    }
    @Test fun unpricedOrInsufficientQuantityCandidateIsNotAFreeItem() {
        val candidate = alt(a,10,0).let { it.copy(quote=it.quote.copy(subtotalMinor=null,status=MARKET_QUOTE_QUANTITY)) }
        val group = plan(listOf(a),candidate)
        assertEquals(1000L,group.lowestItems.itemsSubtotalMinor); assertEquals(0,group.lowestItems.changedLines)
    }
    @Test fun changedCurrencyOrUnitCannotCrossIntoAPlan() {
        val candidate = alt(a,10,10).let { it.copy(quote=it.quote.copy(line=it.quote.line.copy(basis=it.quote.line.basis.copy(unitId="kg")))) }
        assertEquals(0,plan(listOf(a),candidate).lowestItems.changedLines)
    }
    @Test fun noBarcodeLinesStayAtTheirOriginalShop() {
        val source = BasketTestData.row(1,code=null)
        val result = BasketTestData.plan(BasketTestData.snapshot(source),listOf(alt(source,10,10)))
        assertEquals(MARKET_BASKET_NO_BARCODE,result.fixedLines.single().reason)
        assertEquals(0,result.currencies.single().lowestItems.changedLines)
    }
    @Test fun repeatedProductDemandsCannotReuseTheSameStockQuote() {
        val duplicate = BasketTestData.row(2,code=a.line.basis.gtin)
        val result = BasketTestData.plan(BasketTestData.snapshot(a,duplicate), listOf(alt(a,10,10),alt(duplicate,10,10)))
        assertEquals(2,result.fixedLines.size); assertTrue(result.fixedLines.all { it.reason==MARKET_BASKET_REPEATED_PRODUCT })
        assertEquals(0,result.currencies.single().lowestItems.changedLines)
    }
    @Test fun differentCurrenciesGetIndependentPlansNotConvertedTotals() {
        val usd = BasketTestData.row(2,currency="USD")
        val result = BasketTestData.plan(BasketTestData.snapshot(a,usd),listOf(alt(a,10,100),alt(usd,10,200)))
        assertEquals(listOf("KZT","USD"),result.currencies.map { it.currencyCode })
        assertEquals(listOf(100L,200L),result.currencies.map { it.lowestItems.itemsSubtotalMinor })
        assertTrue(result.isValidBasketResult(BasketTestData.account,result.request))
    }
    @Test fun cityRestrictsPlansButDoesNotRewriteCurrentBasket() {
        val input = BasketTestData.snapshot(a)
        val result = BasketTestData.plan(input,listOf(BasketTestData.alternative(a,10,300,"Almaty")),"almaty")
        assertEquals(1000L,result.currencies.single().current.itemsSubtotalMinor)
        assertEquals(300L,result.currencies.single().oneShop.itemsSubtotalMinor)
        assertTrue(result.isValidBasketResult(BasketTestData.account,result.request))
    }
    @Test fun cityWithNoMatchesDoesNotBroadenToOtherCities() {
        val result = BasketTestData.plan(BasketTestData.snapshot(a),listOf(alt(a,10,1)),"Missing city")
        assertEquals(0,result.currencies.single().eligibleShopCount)
        assertNull(result.currencies.single().lowestItems.itemsSubtotalMinor)
        assertEquals(listOf(a.line.offerId),result.currencies.single().lowestItems.missingOfferIds)
    }
    @Test fun candidatesAlreadyInTheListAreNotMergedOrReassigned() {
        val target = alt(a,10,10).let { it.copy(quote=it.quote.copy(line=it.quote.line.copy(offerId=b.line.offerId),offer=it.quote.offer?.copy(id=b.line.offerId))) }
        val group=plan(listOf(a,b),target)
        assertEquals(0,group.lowestItems.changedLines)
    }
    @Test fun duplicateTargetsAcrossDifferentSourcesAreRejectedDefensively() {
        val x=alt(a,10,1)
        val y=alt(b,10,1).let { it.copy(quote=it.quote.copy(line=it.quote.line.copy(offerId=x.quote.line.offerId),offer=it.quote.offer?.copy(id=x.quote.line.offerId))) }
        assertEquals(0,plan(listOf(a,b),x,y).lowestItems.changedLines)
    }
    @Test fun staleQuoteTimestampIsNotIncluded() {
        val candidate=alt(a,10,1).let { it.copy(quote=it.quote.copy(offer=it.quote.offer?.copy(checkedAtMillis=1))) }
        assertEquals(0,plan(listOf(a),candidate).lowestItems.changedLines)
    }
    @Test fun pairSearchIsExplicitlyBoundedWithoutDroppingSingleShopSearch() {
        val rows=(1..5).map { BasketTestData.row(it) }
        val candidates=rows.flatMapIndexed { index,row -> (1..10).map { j -> alt(row,100+index*10+j,1) } }
        val result=BasketTestData.plan(BasketTestData.snapshot(*rows.toTypedArray()),candidates)
        val group=result.currencies.single()
        assertEquals(55,group.eligibleShopCount);assertEquals(MARKET_BASKET_PAIR_SHOP_LIMIT,group.pairSearchShopCount)
        assertTrue(result.isValidBasketResult(BasketTestData.account,result.request))
    }
    @Test fun arithmeticOverflowNeverBecomesZeroOrAnAffordablePlan() {
        val choice=alt(a,10,10)
        val huge=choice.copy(quote=choice.quote.copy(subtotalMinor=Long.MAX_VALUE))
        assertNull(basketSubtotal(listOf(huge,choice)));assertNull(basketSubtotal(emptyList()))
    }
    @Test fun maximumFiftyLineBasketAndSixHundredCandidatesStayBounded() {
        val rows=(1..50).map { BasketTestData.row(it,units=999) }
        val candidates=rows.flatMap { row -> (100..111).map { alt(row,it,100) } }
        val result=BasketTestData.plan(BasketTestData.snapshot(*rows.toTypedArray()),candidates)
        assertEquals(600,result.candidatesChecked)
        assertEquals(50,result.currencies.single().lowestItems.choices.size)
        assertEquals(4_995_000L,result.currencies.single().lowestItems.itemsSubtotalMinor)
        assertTrue(result.isValidBasketResult(BasketTestData.account,result.request))
    }
    @Test fun twoShopResultsMatchAnIndependentBruteForceOracleBelowTheCap() {
        val random=Random(623817)
        repeat(40) {
            val rows=(1..5).map { BasketTestData.row(it,price=random.nextLong(50,1000)) }
            val alternatives=rows.flatMap { row -> (10..15).filter { random.nextBoolean() }.map { alt(row,it,random.nextLong(1,1000)) } }
            val group=plan(rows,*alternatives.toTypedArray())
            val options=rows.map { MarketBasketChoice(it.line.offerId,it) }+alternatives
            val shops=options.map { it.quote.line.storeId }.distinct()
            var expectedCovered=-1;var expectedPrice=Long.MAX_VALUE
            for (i in shops.indices) for (j in i until shops.size) {
                val selected=rows.mapNotNull { row -> options.filter { it.sourceOfferId==row.line.offerId && it.quote.line.storeId in setOf(shops[i],shops[j]) }
                    .minOfOrNull { requireNotNull(it.quote.subtotalMinor) } }
                val sum=selected.sum()
                if (selected.size>expectedCovered || (selected.size==expectedCovered && sum<expectedPrice)) {expectedCovered=selected.size;expectedPrice=sum}
            }
            assertEquals(expectedCovered,group.twoShops.choices.size);assertEquals(expectedPrice,group.twoShops.itemsSubtotalMinor)
        }
    }
}
