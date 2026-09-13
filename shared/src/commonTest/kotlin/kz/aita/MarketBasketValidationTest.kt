package kz.aita

import kotlin.test.*

class MarketBasketValidationTest {
    private val row=BasketTestData.row(1)
    private val result=BasketTestData.plan(BasketTestData.snapshot(row),listOf(BasketTestData.alternative(row,10,500)))
    private fun valid(value:MarketBasketResult)=value.isValidBasketResult(BasketTestData.account,result.request)
    private fun changedPlan(change:(MarketBasketPlan)->MarketBasketPlan)=result.copy(currencies=result.currencies.map { it.copy(lowestItems=change(it.lowestItems)) })

    @Test fun actualPlannerResultPassesItsWireContract() { assertTrue(valid(result)) }
    @Test fun accountAndRevisionMustMatchTheRequest() {
        assertFalse(result.isValidBasketResult(BasketTestData.id(999),result.request))
        assertFalse(result.isValidBasketResult(BasketTestData.account,result.request.copy(expectedRevision=8)))
    }
    @Test fun unknownProtocolAndScopeAreNotSilentlyAccepted() {
        assertFalse(valid(result.copy(protocolVersion=2)))
        assertFalse(valid(result.copy(request=result.request.copy(city="Other"))))
    }
    @Test fun cannotInventOrDropACurrencyGroup() {
        assertFalse(valid(result.copy(currencies=emptyList())))
        assertFalse(valid(result.copy(currencies=result.currencies+result.currencies)))
    }
    @Test fun cannotDropDemandAndCallThePartialPlanComplete() {
        assertFalse(valid(changedPlan { it.copy(choices=emptyList(),itemsSubtotalMinor=null,storeIds=emptyList()) }))
    }
    @Test fun badTotalCannotClaimSavings() { assertFalse(valid(changedPlan { it.copy(itemsSubtotalMinor=0) })) }
    @Test fun duplicateSourcesAndTargetsAreRejected() { assertFalse(valid(changedPlan { it.copy(choices=it.choices+it.choices) })) }
    @Test fun extraMissingLinesAreRejected() { assertFalse(valid(changedPlan { it.copy(missingOfferIds=listOf(row.line.offerId)) })) }
    @Test fun quantityAndIdentityCannotChangeInTransit() {
        assertFalse(valid(changedPlan { plan -> plan.copy(choices=plan.choices.map { c -> c.copy(quote=c.quote.copy(line=c.quote.line.copy(units=2))) }) }))
        assertFalse(valid(changedPlan { plan -> plan.copy(choices=plan.choices.map { c -> c.copy(quote=c.quote.copy(line=c.quote.line.copy(basis=c.quote.line.basis.copy(pricedAmount=2.0)))) }) }))
    }
    @Test fun shopAndTimestampMustBelongToTheQuotedOffer() {
        assertFalse(valid(changedPlan { plan -> plan.copy(choices=plan.choices.map { c -> c.copy(quote=c.quote.copy(offer=c.quote.offer?.copy(checkedAtMillis=1))) }) }))
        assertFalse(valid(changedPlan { plan -> plan.copy(storeIds=listOf(BasketTestData.id(900))) }))
    }
    @Test fun unpricedResultCannotMasqueradeAsEstimated() {
        assertFalse(valid(changedPlan { plan -> plan.copy(choices=plan.choices.map { c -> c.copy(quote=c.quote.copy(status=MARKET_QUOTE_QUANTITY)) }) }))
    }
    @Test fun fixedLineAndCandidateLimitMetadataMustMatchIntent() {
        assertFalse(valid(result.copy(fixedLines=listOf(MarketBasketFixedLine(row.line.offerId,MARKET_BASKET_NO_BARCODE)))))
        assertFalse(valid(result.copy(limitedSourceOfferIds=listOf(row.line.offerId))))
        assertFalse(valid(result.copy(candidatesChecked=601)))
        assertFalse(valid(result.copy(currencies=result.currencies.map { it.copy(pairSearchShopCount=999) })))
    }
    @Test fun alteredCurrentBasketCannotBecomeTheSavingsReference() {
        assertFalse(valid(result.copy(currencies=result.currencies.map { it.copy(current=it.current.copy(itemsSubtotalMinor=99_999)) })))
    }
    @Test fun filteredPlansMustActuallyRespectTheSelectedCity() {
        val filtered=result.copy(request=result.request.copy(city="Almaty"))
        assertFalse(filtered.isValidBasketResult(BasketTestData.account,filtered.request))
    }
    @Test fun requestNormalizationDoesNotAllowInvalidOrOversizedInputs() {
        assertEquals("Astana City",MarketBasketRequest(0,"  Astana\t City  ").normalizedBasketRequest()?.city)
        assertNull(MarketBasketRequest(-1).normalizedBasketRequest())
        assertNull(MarketBasketRequest(0,"a".repeat(101)).normalizedBasketRequest())
        assertNull(MarketBasketRequest(0,"A\u0000B").normalizedBasketRequest())
        assertEquals("%_'",MarketBasketRequest(0,"%_'").normalizedBasketRequest()?.city)
    }
    @Test fun impossibleOneShopClaimAndUnknownSourceStatusAreRejected() {
        val two=BasketTestData.plan(BasketTestData.snapshot(row,BasketTestData.row(2)))
        val group=two.currencies.single()
        assertFalse(two.copy(currencies=listOf(group.copy(oneShop=group.twoShops.copy(kind=MARKET_BASKET_ONE_SHOP))))
            .isValidBasketResult(BasketTestData.account,two.request))
        assertFalse(valid(result.copy(snapshot=result.snapshot.copy(lines=listOf(row.copy(status="unknown"))))))
    }
    @Test fun oversizedOrPrivatePublicOfferMetadataIsRejected() {
        val longTitle=changedPlan { plan -> plan.copy(choices=plan.choices.map { choice ->
            choice.copy(quote=choice.quote.copy(offer=choice.quote.offer?.copy(title="x".repeat(181))))
        }) }
        val privateShop=changedPlan { plan -> plan.copy(choices=plan.choices.map { choice ->
            val offer=requireNotNull(choice.quote.offer)
            choice.copy(quote=choice.quote.copy(offer=offer.copy(storefront=offer.storefront.copy(published=false))))
        }) }
        assertFalse(valid(longTitle));assertFalse(valid(privateShop))
    }
    @Test fun savingsHelperRejectsCrossCurrencyAndChangedQuantities() {
        val group = result.currencies.single()
        fun altered(change: (MarketShoppingLine) -> MarketShoppingLine) = group.lowestItems.copy(
            choices = group.lowestItems.choices.map { it.copy(quote = it.quote.copy(line = change(it.quote.line))) })
        assertNull(altered { it.copy(basis = it.basis.copy(currencyCode = "USD")) }.savingsAgainst(group.current))
        assertNull(altered { it.copy(units = it.units + 1) }.savingsAgainst(group.current))
    }
    @Test fun savingsHelperDoesNotAddTwoCurrenciesEvenWithMatchingSources() {
        val usd = BasketTestData.row(2).let { it.copy(line = it.line.copy(basis = it.line.basis.copy(currencyCode = "USD"))) }
        val mixed = MarketBasketPlan(MARKET_BASKET_CURRENT,
            listOf(MarketBasketChoice(row.line.offerId, row), MarketBasketChoice(usd.line.offerId, usd)),
            emptyList(), 2000L, listOf(row.line.storeId, usd.line.storeId))
        assertNull(mixed.savingsAgainst(mixed))
    }

}
