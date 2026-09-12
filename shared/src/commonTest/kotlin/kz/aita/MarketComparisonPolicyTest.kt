package kz.aita

import kotlinx.coroutines.test.runTest
import kotlin.test.*

class MarketComparisonPolicyTest {
    private val basis = MarketShoppingBasis("04006381333931", "KZT", "piece", 1.0)
    private fun offer(id: String = "source", price: Long = 100L) = MarketOffer(id,
        MarketStorefront("shop-$id", "Shop $id", "Astana", published = true), "Product", gtin = basis.gtin,
        priceMinor = price, currencyCode = basis.currencyCode, pricedAmount = basis.pricedAmount,
        unitId = basis.unitId, checkedAtMillis = 1L, sourceUpdatedAtMillis = 1L)
    private fun selection(revision: Long? = null, units: Int = 2) = MarketComparisonSelection("source", basis, units, revision)
    private fun quote(id: String = "source", price: Long = 100L, units: Int = 2): MarketShoppingQuotedLine {
        val item = offer(id, price)
        return MarketShoppingQuotedLine(MarketShoppingLine(id, item.storefront.storeId, item.title,
            item.storefront.displayName, units, basis), item, price, price * units, MARKET_QUOTE_ESTIMATED)
    }
    private fun page(rows: List<MarketShoppingQuotedLine> = listOf(quote("target")), cursor: String? = null) =
        MarketComparisonPage(selection(), quote(), rows, cursor, 1L)
    private fun <T> ok(data: T) = ResponseDataModel(null, data, false, 200)

    @Test fun comparisonRequiresVerifiedShapeNotJustSimilarTitle() {
        assertNotNull(offer().comparisonSelection())
        assertNull(offer().copy(gtin = "12345").comparisonSelection())
        assertFalse(selection().copy(basis = basis.copy(gtin = null)).isValidMarketComparison())
        assertFalse(selection().copy(units = 0).isValidMarketComparison())
        assertFalse(selection().copy(shoppingRevision = -1).isValidMarketComparison())
        assertFalse(offer().copy(currencyCode = "USD").matchesComparison(selection()))
        assertFalse(offer().copy(pricedAmount = 0.5).matchesComparison(selection()))
        assertFalse(offer().copy(unitId = "kg").matchesComparison(selection()))
    }
    @Test fun rankingUsesRequestedSubtotalAndPutsUnknownAtEnd() {
        val unknown = quote("unknown").copy(unitPriceMinor = null, subtotalMinor = null, status = MARKET_QUOTE_QUANTITY)
        assertEquals(listOf("free", "less", "more", "unknown"), listOf(unknown, quote("more", 200), quote("less", 99), quote("free", 0))
            .rankedComparison().map { it.line.offerId })
    }
    @Test fun reviewBuildsOneAbsoluteReplacementNotRemovePlusAdd() {
        val original = quote()
        val snapshot = MarketShoppingSnapshot("buyer", 7, listOf(original))
        val command = assertNotNull(selection(7).reviewedReplacement(snapshot, quote("target", 80), "change"))
        assertEquals("source", command.replaceOfferId); assertEquals("target", command.offerId)
        assertEquals(2, command.units); assertEquals(160L, command.reviewedSubtotalMinor)
        assertTrue(command.isValidMarketShoppingCommand())
    }
    @Test fun withdrawnOriginalCanBeReplacedWithoutLosingItsIdentity() {
        val original = quote().copy(offer = null, unitPriceMinor = null, subtotalMinor = null, status = MARKET_QUOTE_UNAVAILABLE)
        val snapshot = MarketShoppingSnapshot("buyer", 7, listOf(original))
        assertNotNull(selection(7).reviewedReplacement(snapshot, quote("target"), "replace"))
    }
    @Test fun staleOrEditedSourceCannotCreateReplacement() {
        val snapshot = MarketShoppingSnapshot("buyer", 7, listOf(quote()))
        assertNull(selection(6).reviewedReplacement(snapshot, quote("target"), "replace"))
        assertNull(selection(7, 3).reviewedReplacement(snapshot, quote("target", units = 3), "replace"))
        assertNull(selection().reviewedReplacement(snapshot, quote("target"), "replace"))
    }
    @Test fun targetAlreadyInListIsNotSilentlyMerged() {
        val snapshot = MarketShoppingSnapshot("buyer", 7, listOf(quote(), quote("target")))
        assertNull(selection(7).reviewedReplacement(snapshot, quote("target"), "replace"))
        assertNull(selection(7).reviewedReplacement(snapshot, quote(), "replace"))
    }
    @Test fun missingOrInconsistentQuoteCannotBeConfirmed() {
        val snapshot = MarketShoppingSnapshot("buyer", 7, listOf(quote()))
        assertNull(selection(7).reviewedReplacement(snapshot, quote("target").copy(subtotalMinor = null), "replace"))
        assertNull(selection(7).reviewedReplacement(snapshot, quote("target").copy(subtotalMinor = 99), "replace"))
        assertNull(selection(7).reviewedReplacement(snapshot, quote("target").copy(status = MARKET_QUOTE_QUANTITY), "replace"))
        assertNull(selection(7).reviewedReplacement(snapshot, quote("target").copy(offer = offer("other")), "replace"))
    }
    @Test fun commandShapePreservesLegacyRemovalsButRejectsHalfReplacements() {
        val remove = MarketShoppingCommand("c", 1, "source", 0)
        assertTrue(remove.isValidMarketShoppingCommand())
        assertFalse(remove.copy(replaceOfferId = "target", reviewedSubtotalMinor = 10).isValidMarketShoppingCommand())
        assertFalse(MarketShoppingCommand("c", 1, "target", 2, basis, "source").isValidMarketShoppingCommand())
        assertFalse(MarketShoppingCommand("c", 1, "target", 2, basis, reviewedSubtotalMinor = 20).isValidMarketShoppingCommand())
        assertFalse(MarketShoppingCommand("c", 1, "SOURCE", 2, basis, "source", 20).isValidMarketShoppingCommand())
    }
    @Test fun malformedSnapshotCannotPoisonTheJournal() {
        val valid = MarketShoppingSnapshot("buyer", 1, listOf(quote()))
        assertTrue(valid.isValidMarketShoppingSnapshot("buyer"))
        assertFalse(valid.copy(userId = "other").isValidMarketShoppingSnapshot("buyer"))
        assertFalse(valid.copy(revision = -1).isValidMarketShoppingSnapshot("buyer"))
        assertFalse(valid.copy(lines = listOf(quote(), quote())).isValidMarketShoppingSnapshot("buyer"))
        assertFalse(valid.copy(lines = listOf(quote().copy(subtotalMinor = 4))).isValidMarketShoppingSnapshot("buyer"))
        assertFalse(valid.copy(lines = listOf(quote().copy(offer = offer("foreign")))).isValidMarketShoppingSnapshot("buyer"))
    }
    @Test fun pageMustEchoPinnedIdentityQuantityAndCity() {
        assertTrue(page().isValidComparisonPage(selection(), ""))
        assertFalse(page().isValidComparisonPage(selection(units = 3), ""))
        assertFalse(page().isValidComparisonPage(selection(), "Astana"))
        assertFalse(page(listOf(quote())).isValidComparisonPage(selection(), ""))
        assertFalse(page(listOf(quote("target").copy(offer = offer("target").copy(currencyCode = "USD")))).isValidComparisonPage(selection(), ""))
    }
    @Test fun emptyCompatiblePageCanContinueToALaterMatch() = runTest {
        val seen = mutableListOf<String?>()
        val response = readMarketComparisonWindow(selection(), "", 2) { req ->
            seen += req.after
            ok(if (req.after == null) page(emptyList(), "next") else page())
        }
        assertFalse(response.negative); assertEquals(listOf(null, "next"), seen)
        assertEquals("target", response.payload?.matches?.single()?.line?.offerId)
    }
    @Test fun laterPageFailureDoesNotPublishAPartialSuccess() = runTest {
        val response = readMarketComparisonWindow(selection(), "", 2) { req ->
            if (req.after == null) ok(page(cursor = "next")) else ResponseDataModel(null, null, true, 503)
        }
        assertTrue(response.negative); assertNull(response.payload)
    }
    @Test fun changingReferencePriceDuringWindowRequiresRefresh() = runTest {
        val response = readMarketComparisonWindow(selection(), "", 2) { req ->
            ok(if (req.after == null) page(cursor = "next") else page().copy(reference = quote(price = 150)))
        }
        assertTrue(response.negative); assertEquals(409, response.httpStatusCode)
    }
    @Test fun observationTimeAloneDoesNotBreakComparisonWindow() = runTest {
        val response = readMarketComparisonWindow(selection(), "", 2) { req ->
            ok(if (req.after == null) page(cursor = "next") else page().copy(reference = quote().let { it.copy(offer = it.offer?.copy(checkedAtMillis = 5)) }, checkedAtMillis = 5))
        }
        assertFalse(response.negative); assertEquals(1L, response.payload?.checkedAtMillis)
    }
    @Test fun repeatedCursorAndRepeatedOfferDoNotLoopOrDuplicate() = runTest {
        val bad = readMarketComparisonWindow(selection(), "", 3) { ok(page(cursor = "same")) }
        assertTrue(bad.negative)
        val duplicate = readMarketComparisonWindow(selection(), "", 2) { req -> ok(page(cursor = if (req.after == null) "next" else null)) }
        assertEquals(1, duplicate.payload?.matches?.size)
    }
    @Test fun unknownPriceAndQuantityAreNeverReportedAsCheapest() {
        val unknown = quote("target").copy(unitPriceMinor = null, subtotalMinor = null, status = MARKET_QUOTE_QUANTITY)
        assertTrue(page(listOf(unknown)).isValidComparisonPage(selection(), ""))
        assertEquals("priced", listOf(unknown, quote("priced", 9000)).rankedComparison().first().line.offerId)
    }

    @Test fun anotherListingInTheSamePhysicalShopIsNotACrossShopReplacement() {
        val sameShopOffer = offer("target").copy(storefront = offer().storefront)
        val sameShop = quote("target").copy(line = quote("target").line.copy(storeId = "shop-source"), offer = sameShopOffer)
        assertFalse(page(listOf(sameShop)).isValidComparisonPage(selection(), ""))
        assertNull(selection(7).reviewedReplacement(MarketShoppingSnapshot("buyer", 7, listOf(quote())), sameShop, "change"))
    }
}
