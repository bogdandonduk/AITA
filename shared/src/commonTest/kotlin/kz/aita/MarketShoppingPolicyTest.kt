package kz.aita

import kotlin.test.*

class MarketShoppingPolicyTest {
    private val basis = MarketShoppingBasis("04006381333931", "KZT", "piece", 1.0)
    private fun offer() = MarketOffer("offer", MarketStorefront("shop", "A shop"), "Product", gtin = basis.gtin,
        priceMinor = 19999, currencyCode = "KZT", pricedAmount = 1.0, unitId = "piece", checkedAtMillis = 10, sourceUpdatedAtMillis = 5)
    private fun line(store: String = "shop", currency: String = "KZT", units: Int = 3) =
        MarketShoppingLine("offer", store, "Product", "Shop", units, basis.copy(currencyCode = currency))
    private fun snapshot(revision: Long = 1, time: Long = 10) = MarketShoppingSnapshot("buyer", revision, checkedAtMillis = time)
    private fun command(id: String = "one") = PendingMarketShoppingCommand("buyer", MarketShoppingCommand(id, 0, "offer", 1, basis))

    @Test fun validBasisRequiresCanonicalBarcodeButBarcodeIsOptional() {
        assertTrue(basis.isValidMarketBasis()); assertTrue(basis.copy(gtin = null).isValidMarketBasis())
        assertFalse(basis.copy(gtin = "4006381333931").isValidMarketBasis())
        assertFalse(basis.copy(gtin = "04006381333932").isValidMarketBasis())
    }
    @Test fun invalidMoneyAndUnitCannotEnterShoppingBasis() {
        assertNull(offer().copy(priceMinor = null).shoppingBasis()); assertNull(offer().copy(priceMinor = -1).shoppingBasis())
        assertNull(offer().copy(currencyCode = "tenge").shoppingBasis()); assertNull(offer().copy(unitId = "").shoppingBasis())
        listOf(Double.NaN, Double.POSITIVE_INFINITY, -1.0, 0.0, 1_000_001.0).forEach { assertNull(offer().copy(pricedAmount = it).shoppingBasis()) }
    }
    @Test fun basisChangesWithCurrencyUnitAndPackButNotPriceOrTitle() {
        assertEquals(offer().shoppingBasis(), offer().copy(title = "New title", priceMinor = 456).shoppingBasis())
        assertNotEquals(offer().shoppingBasis(), offer().copy(currencyCode = "USD").shoppingBasis())
        assertNotEquals(offer().shoppingBasis(), offer().copy(unitId = "box").shoppingBasis())
        assertNotEquals(offer().shoppingBasis(), offer().copy(pricedAmount = 6.0).shoppingBasis())
    }
    @Test fun minorUnitMultiplicationIsExact() { assertEquals(59997L, marketShoppingSubtotal(19999, 3)) }
    @Test fun zeroAndUnknownPricesAreDifferent() {
        assertEquals(0L, marketShoppingSubtotal(0, 3)); assertNull(marketShoppingSubtotal(null, 3))
    }
    @Test fun invalidCountsAndOverflowNeverWrapIntoSmallOrNegativePrices() {
        listOf(-1, 0, 1000, Int.MAX_VALUE).forEach { assertNull(marketShoppingSubtotal(123, it)) }
        assertNull(marketShoppingSubtotal(-1, 1)); assertNull(marketShoppingSubtotal(Long.MAX_VALUE, 2))
        assertEquals(Long.MAX_VALUE, marketShoppingSubtotal(Long.MAX_VALUE, 1))
    }
    @Test fun estimatesAreGroupedByPhysicalShopAndCurrency() {
        val rows = listOf(MarketShoppingQuotedLine(line(), subtotalMinor = 100, status = MARKET_QUOTE_ESTIMATED),
            MarketShoppingQuotedLine(line(store = "branch"), subtotalMinor = 200, status = MARKET_QUOTE_ESTIMATED),
            MarketShoppingQuotedLine(line(currency = "USD"), subtotalMinor = 300, status = MARKET_QUOTE_ESTIMATED))
        assertEquals(3, snapshot().copy(lines = rows).shoppingGroups().size)
    }
    @Test fun missingPriceIsExcludedAndCountedNotTreatedAsFree() {
        val group = snapshot().copy(lines = listOf(MarketShoppingQuotedLine(line(), subtotalMinor = 0, status = MARKET_QUOTE_ESTIMATED),
            MarketShoppingQuotedLine(line().copy(offerId = "missing")))).shoppingGroups().single()
        assertEquals(0L, group.pricedSubtotalMinor); assertEquals(1, group.unpricedLines); assertEquals(1, group.confirmationLines)
    }
    @Test fun subtotalOverflowIsFlagged() {
        val group = snapshot().copy(lines = listOf(MarketShoppingQuotedLine(line(), subtotalMinor = Long.MAX_VALUE),
            MarketShoppingQuotedLine(line().copy(offerId = "two"), subtotalMinor = 1))).shoppingGroups().single()
        assertNull(group.pricedSubtotalMinor)
    }
    @Test fun aGroupWithOnlyUnknownLinesDoesNotDisplayAZeroPrice() {
        val group = snapshot().copy(lines = listOf(MarketShoppingQuotedLine(line()),
            MarketShoppingQuotedLine(line().copy(offerId = "missing")))).shoppingGroups().single()
        assertNull(group.pricedSubtotalMinor); assertEquals(2, group.unpricedLines)
    }
    @Test fun newerListRevisionWinsEvenWhenItsServerClockIsEarlier() {
        assertEquals(2L, snapshot(1, 100).acceptShoppingSnapshot(snapshot(2, 1)).revision)
        assertEquals(2L, snapshot(2, 1).acceptShoppingSnapshot(snapshot(1, 100)).revision)
    }
    @Test fun sameRevisionCannotRegressACompletedPriceRefresh() {
        assertEquals(100L, snapshot(1, 100).acceptShoppingSnapshot(snapshot(1, 1)).checkedAtMillis)
    }
    @Test fun firstCommandIsStoredAndIdenticalPreparationIsIdempotent() {
        val saved = MarketShoppingJournal("buyer").prepare(command())
        assertEquals(command(), saved.pending); assertEquals(1L, saved.localRevision)
        assertEquals(saved, saved.prepare(command()))
    }
    @Test fun unresolvedCommandCannotBeReplaced() {
        assertFailsWith<IllegalStateException> { MarketShoppingJournal("buyer").prepare(command()).prepare(command("different")) }
    }
    @Test fun foreignAccountCannotPrepareOrAcknowledgeAJournal() {
        assertFailsWith<IllegalArgumentException> { MarketShoppingJournal("other").prepare(command()) }
        assertFailsWith<IllegalArgumentException> { MarketShoppingJournal("buyer").withSnapshot(snapshot().copy(userId = "other")) }
    }
    @Test fun knownRejectedOutcomeAlsoRetiresOnlyItsExactCommand() {
        val current = MarketShoppingJournal("buyer").prepare(command())
        val outcome = MarketShoppingOutcome("one", false, errorKey = "market.shopping_changed", snapshot = snapshot())
        val next = current.acknowledge(command(), outcome)
        assertNull(next.pending); assertEquals(1L, next.snapshot?.revision); assertTrue(next.localRevision > current.localRevision)
    }
    @Test fun lateAcknowledgementCannotRetireANewerCommand() {
        val current = MarketShoppingJournal("buyer").prepare(command("new"))
        val result = current.acknowledge(command(), MarketShoppingOutcome("one", true, 1, snapshot = snapshot()))
        assertEquals(command("new"), result.pending)
    }
    @Test fun acknowledgementCannotRestoreAnOlderList() {
        val current = MarketShoppingJournal("buyer", snapshot = snapshot(4)).prepare(command())
        val result = current.acknowledge(command(), MarketShoppingOutcome("one", true, 1, snapshot = snapshot(1)))
        assertEquals(4L, result.snapshot?.revision); assertNull(result.pending)
    }
    @Test fun wrongReceiptCannotRetireCommand() {
        assertFailsWith<IllegalArgumentException> { MarketShoppingJournal("buyer").prepare(command())
            .acknowledge(command(), MarketShoppingOutcome("different", true, 1, snapshot = snapshot())) }
    }
    @Test fun refreshPreservesPendingCommandAndAdvancesLocalSequence() {
        val journal = MarketShoppingJournal("buyer").prepare(command())
        val next = journal.withSnapshot(snapshot())
        assertEquals(command(), next.pending); assertTrue(next.localRevision > journal.localRevision)
    }
}
