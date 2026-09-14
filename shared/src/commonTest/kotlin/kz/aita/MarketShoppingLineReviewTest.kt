package kz.aita

import kotlin.test.*

class MarketShoppingLineReviewTest {
    private val basis = MarketShoppingBasis(null, "KZT", "piece", 1.0)
    private val line = MarketShoppingLine("offer", "shop", "Product", "Shop", 2, basis, updatedAtMillis = 10)
    private fun snapshot(revision: Long = 4, intent: MarketShoppingLine = line) =
        MarketShoppingSnapshot("buyer", revision, listOf(MarketShoppingQuotedLine(intent)), 20)
    private fun review() = requireNotNull(snapshot().reviewShoppingLine(line))

    @Test fun decrementUsesTheDisplayedQuantityAndRevision() {
        val command = requireNotNull(review().command(snapshot(), line.units - 1, "edit"))
        assertEquals(MarketShoppingCommand("edit", 4, "offer", 1, basis), command)
    }
    @Test fun oldIncrementCannotBeRebasedOverAnotherDevicesQuantity() {
        assertNull(review().command(snapshot(5, line.copy(units = 8)), line.units + 1, "edit"))
    }
    @Test fun oldDecrementCannotOverwriteANewerQuantity() {
        // The old UI used latest.revision with oldRow.units - 1; that is a valid write of 1,
        // not a stale write, once another device's 8-unit revision has already been loaded.
        val latest = snapshot(5, line.copy(units = 8))
        val formerlyRebased = MarketShoppingCommand("edit", latest.revision, line.offerId, line.units - 1, line.basis)
        assertTrue(formerlyRebased.isValidMarketShoppingCommand())
        assertEquals(1, formerlyRebased.units)
        assertNull(review().command(latest, line.units - 1, "edit"))
    }
    @Test fun removalCarriesFrozenRevisionAndNoQuantityBasis() {
        val command = requireNotNull(review().command(snapshot(), 0, "remove"))
        assertEquals(MarketShoppingCommand("remove", 4, "offer", 0), command)
        assertNull(command.basis); assertNull(command.replaceOfferId); assertNull(command.basketChange)
    }
    @Test fun removalCannotDeleteAnItemChangedWhileConfirmationIsOpen() {
        val frozen = review()
        assertNull(frozen.command(snapshot(5, line.copy(units = 8)), 0, "remove"))
        assertEquals(2, frozen.line.units)
    }
    @Test fun removeAndReaddOfSameQuantityStillRequiresNewReview() {
        assertNull(review().command(snapshot(6), 0, "remove"))
    }
    @Test fun unrelatedListChangeStillRespectsWholeListOptimisticRevision() {
        assertNull(review().command(snapshot(5).copy(lines = snapshot().lines +
            MarketShoppingQuotedLine(line.copy(offerId = "other"))), 3, "edit"))
    }
    @Test fun removedLineIsNotResurrectedByAnOldQuantityButton() {
        assertNull(review().command(snapshot(5).copy(lines = emptyList()), 3, "edit"))
    }
    @Test fun foreignAccountCannotUseTheReviewEvenWithSameIdsAndRevision() {
        assertNull(review().command(snapshot().copy(userId = "other"), 0, "edit"))
    }
    @Test fun noSnapshotCannotUseAnOldReview() { assertNull(review().command(null, 0, "edit")) }
    @Test fun sameRevisionButDifferentRetainedIntentCannotPass() {
        for (changed in listOf(line.copy(units = 9), line.copy(storeId = "other"), line.copy(title = "Different"),
            line.copy(basis = basis.copy(pricedAmount = 2.0)), line.copy(updatedAtMillis = 11))) {
            assertFalse(review().matches(snapshot(intent = changed)))
        }
    }
    @Test fun newerPriceOnlyReadDoesNotInvalidateIntent() {
        val refreshed = snapshot().copy(checkedAtMillis = 50,
            lines = listOf(MarketShoppingQuotedLine(line, unitPriceMinor = 500, subtotalMinor = 1000,
                status = MARKET_QUOTE_QUANTITY)))
        assertTrue(review().matches(refreshed))
        assertEquals(4L, review().command(refreshed, 3, "edit")?.expectedRevision)
    }
    @Test fun withdrawnUnpricedLineRemainsEditableAndRemovable() {
        assertEquals(MARKET_QUOTE_UNAVAILABLE, snapshot().lines.single().status)
        assertNotNull(review().command(snapshot(), 3, "edit"))
        assertNotNull(review().command(snapshot(), 0, "remove"))
    }
    @Test fun noOpAndOutOfBoundsEditsDoNotCreateCommands() {
        for (units in listOf(-1, 2, MARKET_SHOPPING_MAX_UNITS + 1, Int.MAX_VALUE))
            assertNull(review().command(snapshot(), units, "edit"))
        assertNotNull(review().command(snapshot(), MARKET_SHOPPING_MAX_UNITS, "edit"))
    }
    @Test fun captureRequiresExactlyTheDisplayedRetainedLine() {
        assertNull(snapshot().reviewShoppingLine(line.copy(units = 3)))
        assertNull(snapshot().copy(lines = snapshot().lines + snapshot().lines).reviewShoppingLine(line))
        assertNull(snapshot().copy(userId = "").reviewShoppingLine(line))
        assertNull(snapshot(Long.MAX_VALUE).reviewShoppingLine(line))
    }
    @Test fun invalidRetainedBasisCannotBecomeAReview() {
        val invalid = line.copy(basis = basis.copy(pricedAmount = Double.NaN))
        assertNull(snapshot(intent = invalid).reviewShoppingLine(invalid))
    }
    @Test fun comparisonIsAlsoBoundToTheDisplayedListRevision() {
        val original = line.copy(basis = basis.copy(gtin = "04006381333931"))
        val before = snapshot(intent = original)
        val selected = requireNotNull(before.reviewShoppingLine(original))
        assertEquals(4L, selected.comparison(before)?.shoppingRevision)
        assertEquals(2, selected.comparison(before)?.units)
        assertNull(selected.comparison(snapshot(5, original.copy(units = 8))))
    }
    @Test fun addControlCannotResetAnAlreadyListedOfferToOne() {
        assertNull(snapshot(intent = line.copy(units = 8)).newShoppingLineCommand("offer", 1, basis, "add"))
    }
    @Test fun addControlCannotReplaceAnExistingBasis() {
        assertNull(snapshot().newShoppingLineCommand("offer", 7, basis.copy(pricedAmount = 2.0), "add"))
    }
    @Test fun newOfferKeepsAbsoluteComparedQuantityAndExactRevision() {
        assertEquals(MarketShoppingCommand("add", 4, "new", 7, basis),
            snapshot().newShoppingLineCommand("new", 7, basis, "add"))
    }
    @Test fun addCannotBeUsedAsRemoveOrWithoutASellingBasis() {
        for (units in listOf(-1, 0, MARKET_SHOPPING_MAX_UNITS + 1))
            assertNull(snapshot().newShoppingLineCommand("new", units, basis, "add"))
        assertNull(snapshot().newShoppingLineCommand("new", 1, null, "add"))
        assertNull(snapshot(Long.MAX_VALUE).newShoppingLineCommand("new", 1, basis, "add"))
    }
    @Test fun fullListPreventsNewAdditionButDoesNotBlockEditing() {
        val full = snapshot().copy(lines = listOf(MarketShoppingQuotedLine(line)) +
            (1 until MARKET_SHOPPING_MAX_LINES).map { MarketShoppingQuotedLine(line.copy(offerId = "offer-$it")) })
        assertNull(full.newShoppingLineCommand("new", 1, basis, "add"))
        assertNotNull(full.reviewShoppingLine(line)?.command(full, 3, "edit"))
    }
}
