package kz.aita

import kotlin.test.*

class MarketShoppingQuantityUiStateTest {
    private val basis = MarketShoppingBasis(null, "KZT", "kg", 0.5)
    private val line = MarketShoppingLine("offer", "shop", "Product", "Shop", 2, basis, updatedAtMillis = 10)
    private fun initial() = MarketShoppingSnapshot("buyer", 4, listOf(MarketShoppingQuotedLine(line)), 20)
    private class Fixture(snapshot: MarketShoppingSnapshot) {
        var current: MarketShoppingSnapshot? = snapshot
        var allowed = true
        var accepts = true
        var dismissed = 0
        val calls = mutableListOf<Pair<MarketShoppingLineReview, Int>>()
        val review = requireNotNull(snapshot.reviewShoppingLine(snapshot.lines.single().line))
        val editor = MarketShoppingQuantityUiState(review, { current }, { allowed }, { frozen, units ->
            calls += frozen to units
            accepts && frozen.command(current, units, "edit") != null
        }, { dismissed++ })
    }
    private fun fixture() = Fixture(initial())

    @Test fun initialDraftShowsRetainedQuantityWithoutEnablingANoOp() {
        val f = fixture()
        assertEquals("2", f.editor.draft); assertTrue(f.editor.editable); assertFalse(f.editor.canSubmit)
        assertFalse(f.editor.submit()); assertTrue(f.calls.isEmpty()); assertEquals(0, f.dismissed)
    }
    @Test fun directEditSubmitsOneAbsoluteQuantityWithOriginalReview() {
        val f = fixture(); f.editor.edit("125")
        assertTrue(f.editor.canSubmit); assertTrue(f.editor.submit())
        assertEquals(listOf(f.review to 125), f.calls); assertEquals(1, f.dismissed); assertFalse(f.editor.active)
    }
    @Test fun oldSaveCallbackReadsTheCurrentDraftNotItsRenderedNumber() {
        val f = fixture(); f.editor.edit("5")
        val save = { f.editor.submit() }
        f.editor.edit("12")
        assertTrue(save()); assertEquals(12, f.calls.single().second)
    }
    @Test fun invalidPasteReplacesValidDraftAndDisablesOldSaveCallback() {
        for (invalid in listOf("1.5", "1000", "-8", "999999999999999999", "12kg", "")) {
            val f = fixture(); f.editor.edit("5")
            val save = { f.editor.submit() }
            f.editor.edit(invalid)
            assertEquals(invalid, f.editor.draft); assertFalse(f.editor.canSubmit)
            assertFalse(save()); assertTrue(f.calls.isEmpty()); assertEquals(0, f.dismissed)
        }
    }
    @Test fun oldImeCallbackCannotSendAfterDraftWasCleared() {
        val f = fixture(); f.editor.edit("5"); val ime = { f.editor.submit() }
        f.editor.edit("")
        assertFalse(ime()); assertTrue(f.calls.isEmpty())
    }
    @Test fun changingBackToOriginalQuantityIsStillNoOp() {
        val f = fixture(); f.editor.edit("12"); f.editor.edit("0002")
        assertEquals(2, f.editor.enteredUnits); assertFalse(f.editor.submit()); assertTrue(f.calls.isEmpty())
    }
    @Test fun duplicateSaveAndImeActionsCannotSubmitTwice() {
        val f = fixture(); f.editor.edit("12")
        assertTrue(f.editor.submit()); assertFalse(f.editor.submit())
        f.editor.dismiss(); assertEquals(1, f.calls.size); assertEquals(1, f.dismissed)
    }
    @Test fun cancelledDraftIsNeitherSentNorReusedOnReopen() {
        val f = fixture(); f.editor.edit("12"); f.editor.dismiss()
        assertFalse(f.editor.submit()); assertTrue(f.calls.isEmpty()); assertEquals(1, f.dismissed)
        assertEquals("2", fixture().editor.draft)
    }
    @Test fun dismissalAndDisposalStopDelayedEditingAndSave() {
        val f = fixture(); f.editor.edit("12"); f.editor.dispose(); f.editor.edit("50")
        assertEquals("12", f.editor.draft); assertFalse(f.editor.submit()); assertTrue(f.calls.isEmpty())
        f.editor.dismiss(); assertEquals(0, f.dismissed)
    }
    @Test fun busySenderBlocksNewSubmissionButRetainsDraft() {
        val f = fixture(); f.editor.edit("12"); f.allowed = false
        assertFalse(f.editor.editable); assertFalse(f.editor.submit()); assertTrue(f.calls.isEmpty())
        assertEquals("12", f.editor.draft); f.allowed = true; assertTrue(f.editor.submit())
    }
    @Test fun failedFinalSenderCheckKeepsEditorAndDraftOpen() {
        val f = fixture(); f.editor.edit("12"); f.accepts = false
        assertFalse(f.editor.submit()); assertTrue(f.editor.active); assertEquals("12", f.editor.draft)
        assertEquals(0, f.dismissed)
    }
    @Test fun ownerOrSessionGuardIsRecheckedAtClickTime() {
        val f = fixture(); f.editor.edit("12"); val save = { f.editor.submit() }
        f.allowed = false
        assertFalse(save()); assertTrue(f.calls.isEmpty())
    }
    @Test fun differentAccountWithSameRevisionCannotSubmit() {
        val f = fixture(); f.editor.edit("12"); f.current = initial().copy(userId = "other")
        assertFalse(f.editor.matches); assertFalse(f.editor.submit()); assertTrue(f.calls.isEmpty())
    }
    @Test fun anotherDevicesRevisionCannotBeBorrowedByEditor() {
        val f = fixture(); f.editor.edit("12")
        f.current = initial().copy(revision = 5, lines = listOf(MarketShoppingQuotedLine(line.copy(units = 8))))
        assertFalse(f.editor.canSubmit); assertFalse(f.editor.submit()); assertTrue(f.calls.isEmpty())
        assertEquals(4L, f.editor.review.expectedRevision); assertEquals("12", f.editor.draft)
    }
    @Test fun removedLineCannotBeRecreatedFromEditor() {
        val f = fixture(); f.editor.edit("12"); f.current = initial().copy(revision = 5, lines = emptyList())
        assertFalse(f.editor.submit()); assertTrue(f.calls.isEmpty())
    }
    @Test fun missingSnapshotAndSameRevisionDifferentBasisBlockSave() {
        val f = fixture(); f.editor.edit("12"); f.current = null
        assertFalse(f.editor.submit())
        f.current = initial().copy(lines = listOf(MarketShoppingQuotedLine(line.copy(basis = basis.copy(pricedAmount = 1.0)))))
        assertFalse(f.editor.submit()); assertTrue(f.calls.isEmpty())
    }
    @Test fun priceOnlyRefreshDoesNotReplaceDraftOrInvalidateIntent() {
        val f = fixture(); f.editor.edit("12")
        f.current = initial().copy(checkedAtMillis = 100,
            lines = listOf(MarketShoppingQuotedLine(line, status = MARKET_QUOTE_PRICE)))
        assertEquals("12", f.editor.draft); assertTrue(f.editor.submit())
    }
    @Test fun retainedUnavailableLineCanStillReachMaximumQuantity() {
        val f = fixture(); f.editor.edit(MARKET_SHOPPING_MAX_UNITS.toString())
        assertTrue(f.editor.submit()); assertEquals(MARKET_SHOPPING_MAX_UNITS, f.calls.single().second)
        assertEquals(basis, f.calls.single().first.line.basis)
    }
}
