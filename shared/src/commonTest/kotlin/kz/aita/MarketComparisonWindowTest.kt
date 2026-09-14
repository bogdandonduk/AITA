package kz.aita

import kotlin.test.*

internal object ComparisonWindowFixtures {
    fun id(value: Int) = "00000000-0000-0000-0000-" + value.toString(16).padStart(12, '0')
    val account = id(1)
    val basis = MarketShoppingBasis("04006381333931", "KZT", "piece", 1.0)
    val selection = MarketComparisonSelection(id(2), basis, 2)
    val request = MarketComparisonWindowRequest(selection, "Astana")
    const val checkedAt = 1_000L
    fun quote(number: Int = 2, price: Long = 100L): MarketShoppingQuotedLine {
        val shop = MarketStorefront(id(if (number == 2) 3 else 4_000 + number % 3), "Shop", "Astana", "Public door",
            published = true, revision = 1L)
        val offer = MarketOffer(id(number), shop, "Product $number", gtin = basis.gtin, priceMinor = price,
            currencyCode = basis.currencyCode, pricedAmount = basis.pricedAmount, unitId = basis.unitId,
            availability = MARKET_AVAILABILITY_RECORDED, checkedAtMillis = checkedAt, sourceUpdatedAtMillis = 100L)
        return MarketShoppingQuotedLine(MarketShoppingLine(offer.id, shop.storeId, offer.title, shop.displayName,
            selection.units, basis, updatedAtMillis = offer.sourceUpdatedAtMillis), offer, price, price * selection.units,
            MARKET_QUOTE_ESTIMATED)
    }
    fun result(wanted: MarketComparisonWindowRequest = request, count: Int = 1): MarketComparisonWindowResult =
        MarketComparisonWindowResult(account, wanted, quote(), (0 until count).map { quote(1_000 + it, (it + 1L) * 10) },
            count, false, checkedAt)
}

class MarketComparisonWindowTest {
    private val f = ComparisonWindowFixtures
    private fun valid(result: MarketComparisonWindowResult) = result.isValidComparisonWindowResult(f.account, result.request)
    private fun changedOffer(transform: (MarketOffer) -> MarketOffer): MarketComparisonWindowResult {
        val result = f.result(); val row = result.matches.single()
        return result.copy(matches = listOf(row.copy(offer = transform(requireNotNull(row.offer)))))
    }

    @Test fun normalizesCityWhitespaceAndCanonicalOfferIdentity() {
        val source = f.id(0xABCD)
        val request = f.request.copy(selection = f.selection.copy(offerId = source.uppercase()), city = "  New\t York \n")
        assertEquals(request.copy(selection = request.selection.copy(offerId = source), city = "New York"), request.normalizedComparisonWindowRequest())
    }
    @Test fun rejectsInvalidLimitsAndFiltersWithoutBroadening() {
        listOf(-1, 0, 1, 39, 41, 399, 401, Int.MAX_VALUE).forEach { assertNull(f.request.copy(candidateLimit = it).normalizedComparisonWindowRequest()) }
        assertNull(f.request.copy(city = "x".repeat(101)).normalizedComparisonWindowRequest())
        assertNull(f.request.copy(city = "A\u0000B").normalizedComparisonWindowRequest())
        assertNull(f.request.copy(selection = f.selection.copy(offerId = "not-an-id")).normalizedComparisonWindowRequest())
        assertNull(f.request.copy(selection = f.selection.copy(basis = f.basis.copy(gtin = null))).normalizedComparisonWindowRequest())
    }
    @Test fun acceptsEveryBoundedWholeWindowIncludingMoreThanFiftyMatches() {
        (40..400 step 40).forEach { limit ->
            val result = f.result(f.request.copy(candidateLimit = limit), limit)
            assertTrue(valid(result), "limit=$limit")
            assertTrue(valid(result.copy(moreCandidates = true)))
        }
    }
    @Test fun emptyCompatibleWindowMayStillHaveMoreCandidates() {
        val result = f.result(count = 0).copy(candidatesChecked = 40, moreCandidates = true)
        assertTrue(valid(result))
        assertTrue(valid(f.result(count = 0)))
    }
    @Test fun enforcesCandidateAccountingAndTheRequestedWindow() {
        val result = f.result()
        listOf(result.copy(candidatesChecked = -1), result.copy(candidatesChecked = 0), result.copy(candidatesChecked = 41),
            result.copy(moreCandidates = true)).forEach { assertFalse(valid(it)) }
        assertFalse(result.isValidComparisonWindowResult(f.account, f.request.copy(candidateLimit = 80)))
        assertFalse(valid(result.copy(protocolVersion = 2)))
        assertFalse(result.copy(accountId = f.id(99)).isValidComparisonWindowResult(f.account, f.request))
    }
    @Test fun rejectsDifferentSelectionCurrencyQuantityAndCity() {
        val result = f.result()
        listOf(f.request.copy(city = "Almaty"), f.request.copy(selection = f.selection.copy(units = 3)),
            f.request.copy(selection = f.selection.copy(basis = f.basis.copy(currencyCode = "USD"))),
            f.request.copy(selection = f.selection.copy(shoppingRevision = 1))).forEach {
            assertFalse(result.isValidComparisonWindowResult(f.account, it))
        }
        assertFalse(valid(changedOffer { it.copy(storefront = it.storefront.copy(city = "Almaty")) }))
        assertTrue(valid(changedOffer { it.copy(storefront = it.storefront.copy(city = "ASTANA")) }))
    }
    @Test fun rejectsMixedQuoteInstantsEvenWhenTheReferencePriceDidNotChange() {
        assertFalse(valid(changedOffer { it.copy(checkedAtMillis = f.checkedAt + 1) }))
        assertFalse(valid(f.result().copy(checkedAtMillis = f.checkedAt - 1)))
        assertFalse(valid(f.result().copy(checkedAtMillis = 0)))
    }
    @Test fun rejectsPrivateMalformedAndMismatchedPublicShops() {
        listOf<(MarketOffer) -> MarketOffer>(
            { it.copy(storefront = it.storefront.copy(published = false)) },
            { it.copy(storefront = it.storefront.copy(revision = 0)) },
            { it.copy(storefront = it.storefront.copy(publicAddress = "")) },
            { it.copy(storefront = it.storefront.copy(storeId = f.id(99))) },
            { it.copy(id = f.id(99)) }, { it.copy(title = "") }, { it.copy(description = "x".repeat(2001)) },
            { it.copy(availability = "reserved") }, { it.copy(sourceUpdatedAtMillis = -1L) }
        ).forEach { assertFalse(valid(changedOffer(it))) }
    }
    @Test fun candidatesMustMatchTheActualSellingUnitNotJustTheBarcode() {
        listOf<(MarketOffer) -> MarketOffer>(
            { it.copy(gtin = "00036000291452") }, { it.copy(gtin = "1234") }, { it.copy(gtin = null) },
            { it.copy(currencyCode = "USD") }, { it.copy(unitId = "kg") }, { it.copy(pricedAmount = 2.0) },
            { it.copy(pricedAmount = Double.NaN) }, { it.copy(pricedAmount = Double.POSITIVE_INFINITY) }
        ).forEach { assertFalse(valid(changedOffer(it))) }
    }
    @Test fun candidatePublicLabelsMustMatchTheirQuotedLines() {
        assertFalse(valid(changedOffer { it.copy(title = "Different product label") }))
        assertFalse(valid(changedOffer { it.copy(storefront = it.storefront.copy(displayName = "Different shop")) }))
        assertFalse(valid(changedOffer { it.copy(sourceUpdatedAtMillis = 101L) }))
        assertFalse(valid(changedOffer { it.copy(unitName = listOf(LocalizedStringDataModel("en", "box"))) }))
    }
    @Test fun rejectsInventedEstimatesAndUnknownQuoteStatus() {
        val result = f.result(); val row = result.matches.single()
        listOf(row.copy(unitPriceMinor = 11), row.copy(subtotalMinor = 999), row.copy(subtotalMinor = null),
            row.copy(status = "accepted"), row.copy(line = row.line.copy(units = 3)), row.copy(offer = null),
            row.copy(unitPriceMinor = Long.MAX_VALUE, subtotalMinor = Long.MAX_VALUE)).forEach {
            assertFalse(valid(result.copy(matches = listOf(it))))
        }
        assertFalse(valid(changedOffer { it.copy(priceMinor = 1_000_000_000_001L) }))
        assertFalse(valid(changedOffer { it.copy(availability = MARKET_AVAILABILITY_CONFIRM) }))
    }
    @Test fun quantityConfirmationHasNoFabricatedSubtotal() {
        val result = f.result(); val row = result.matches.single()
        val unpriced = row.copy(status = MARKET_QUOTE_QUANTITY, unitPriceMinor = null, subtotalMinor = null,
            offer = row.offer!!.copy(availability = MARKET_AVAILABILITY_CONFIRM))
        assertTrue(valid(result.copy(matches = listOf(unpriced))))
        assertFalse(valid(result.copy(matches = listOf(unpriced.copy(subtotalMinor = 20L)))))
    }
    @Test fun rejectsRepeatedCandidatesAndTheOriginalShop() {
        val result = f.result(); val row = result.matches.single()
        assertFalse(valid(result.copy(matches = listOf(row, row), candidatesChecked = 2)))
        assertFalse(valid(result.copy(matches = listOf(result.reference))))
        val sameShop = row.copy(line = row.line.copy(storeId = result.reference.line.storeId),
            offer = row.offer!!.copy(storefront = result.reference.offer!!.storefront))
        assertFalse(valid(result.copy(matches = listOf(sameShop))))
    }
    @Test fun theRankingIsDeterministicAcrossTheEntireWindow() {
        val result = f.result(count = 3)
        assertTrue(valid(result)); assertFalse(valid(result.copy(matches = result.matches.reversed())))
        val expensive = result.matches.first().copy(unitPriceMinor = 5_000L, subtotalMinor = 10_000L,
            offer = result.matches.first().offer!!.copy(priceMinor = 5_000L))
        val latest = result.copy(matches = (result.matches.drop(1) + expensive).rankedComparison())
        assertTrue(valid(latest)); assertEquals(expensive.line.offerId, latest.matches.last().line.offerId)
    }
    @Test fun withdrawnReferenceIsAllowedOnlyForAnExistingListSelection() {
        val result = f.result()
        val withdrawn = result.reference.copy(offer = null, unitPriceMinor = null, subtotalMinor = null, status = MARKET_QUOTE_UNAVAILABLE)
        assertFalse(valid(result.copy(reference = withdrawn)))
        assertTrue(valid(result.copy(reference = withdrawn, request = result.request.copy(selection = f.selection.copy(shoppingRevision = 4)))))
    }
    @Test fun changedOriginalUnitMayBeShownWithoutAnOldPriceEstimate() {
        val result = f.result()
        val changed = result.reference.copy(status = MARKET_QUOTE_CHANGED, unitPriceMinor = null, subtotalMinor = null,
            offer = result.reference.offer!!.copy(pricedAmount = 2.0))
        assertFalse(valid(result.copy(reference = changed)))
        assertTrue(valid(result.copy(reference = changed, request = result.request.copy(selection = f.selection.copy(shoppingRevision = 1)))))
    }
    @Test fun aReviewedReplacementStillNeedsTheExactListRevisionAndPrice() {
        val result = f.result(f.request.copy(selection = f.selection.copy(shoppingRevision = 1)))
        val snapshot = MarketShoppingSnapshot(f.account, 1, listOf(result.reference), f.checkedAt)
        assertNotNull(result.request.selection.reviewedReplacement(snapshot, result.matches.single(), f.id(99)))
        assertNull(result.request.selection.reviewedReplacement(snapshot.copy(revision = 2), result.matches.single(), f.id(99)))
        assertNull(result.request.selection.reviewedReplacement(snapshot, result.matches.single().copy(subtotalMinor = 999), f.id(99)))
    }
    @Test fun changedWindowOrInvalidationCannotMarkALateReadFresh() {
        val result = f.result()
        assertTrue(result.matchesComparisonRead(f.request, 7L, 7L))
        assertFalse(result.matchesComparisonRead(f.request, 7L, 8L))
        assertFalse(result.matchesComparisonRead(f.request.copy(candidateLimit = 80), 7L, 7L))
        assertFalse(result.matchesComparisonRead(f.request.copy(city = "Almaty"), 7L, 7L))
    }
    @Test fun allNewComparisonFeedbackHasSixExplicitTranslations() {
        val keys = listOf("invalid", "upgrade", "refresh", "busy", "empty_more")
        for (suffix in keys) {
            val reference = EventMessageReference("market.comparison_window_$suffix")
            for (language in listOf("en", "ru", "kk", "tg", "ky", "uz")) {
                val text = EventMessages.renderExact(reference, language)
                assertFalse(text.isNullOrBlank(), "$suffix / $language")
                if (language != "en") assertNotEquals(EventMessages.renderExact(reference, "en"), text)
            }
        }
    }
    @Test fun theCoverageLabelInterpolatesCountsInAllSixLanguages() {
        val reference = EventMessageReference("market.comparison_window_scope", mapOf("checked" to "400", "matches" to "73"))
        for (language in listOf("en", "ru", "kk", "tg", "ky", "uz")) {
            val text = assertNotNull(EventMessages.renderExact(reference, language))
            assertTrue("400" in text && "73" in text)
            assertFalse("{checked}" in text || "{matches}" in text)
            assertNull(EventMessages.renderExact(reference.copy(arguments = mapOf("checked" to "400")), language))
        }
    }

}
