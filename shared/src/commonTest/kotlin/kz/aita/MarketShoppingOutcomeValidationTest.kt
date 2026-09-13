package kz.aita

import kotlin.test.*

/** The same validator protects both ordinary mutation acknowledgements and read-only recovery. */
class MarketShoppingOutcomeValidationTest {
    private val basis = MarketShoppingBasis("04006381333931","KZT","piece",1.0)
    private val command = MarketShoppingCommand("command",7,"offer",2,basis)
    private val line = MarketShoppingLine("offer","shop","Product","Shop",2,basis)
    private fun outcome(revision: Long = 8, applied: Long = 8, rows: List<MarketShoppingLine> = listOf(line)) =
        MarketShoppingOutcome(command.commandId,true,applied,snapshot = MarketShoppingSnapshot("buyer",revision,
            rows.map { MarketShoppingQuotedLine(it) },100))
    @Test fun appliedQuantityMustActuallyBePresentAtTheAcknowledgedRevision() {
        assertTrue(command.isValidShoppingOutcome(outcome(),"buyer"))
        assertFalse(command.isValidShoppingOutcome(outcome(rows = emptyList()),"buyer"))
        assertFalse(command.isValidShoppingOutcome(outcome(rows = listOf(line.copy(units = 1))),"buyer"))
        assertFalse(command.isValidShoppingOutcome(outcome(rows = listOf(line.copy(basis = basis.copy(pricedAmount = 0.5)))),"buyer"))
    }
    @Test fun acceptedRevisionCannotPrecedeTheCommandOrJumpSeveralVersions() {
        assertFalse(command.isValidShoppingOutcome(outcome(applied = 6),"buyer"))
        assertFalse(command.isValidShoppingOutcome(outcome(revision = 9,applied = 9),"buyer"))
        assertFalse(command.isValidShoppingOutcome(outcome(revision = 7),"buyer"))
        assertFalse(command.isValidShoppingOutcome(outcome().copy(appliedRevision = null),"buyer"))
    }
    @Test fun noOpQuantityStillRequiresTheDesiredQuantityAndBasis() {
        assertTrue(command.isValidShoppingOutcome(outcome(revision = 7,applied = 7),"buyer"))
        assertFalse(command.isValidShoppingOutcome(outcome(revision = 7,applied = 7,rows = emptyList()),"buyer"))
    }
    @Test fun removalCannotClaimSuccessWhileTheLineStillExists() {
        val remove = command.copy(units = 0,basis = null)
        assertFalse(remove.isValidShoppingOutcome(outcome(),"buyer"))
        assertTrue(remove.isValidShoppingOutcome(outcome(rows = emptyList()),"buyer"))
        assertTrue(remove.isValidShoppingOutcome(outcome(revision = 7,applied = 7,rows = emptyList()),"buyer"))
    }
    @Test fun replacementMustRemoveTheOriginalAndCannotBeANoOp() {
        val replace = command.copy(replaceOfferId = "original",reviewedSubtotalMinor = 100)
        assertTrue(replace.isValidShoppingOutcome(outcome(),"buyer"))
        assertFalse(replace.isValidShoppingOutcome(outcome(revision = 7,applied = 7),"buyer"))
        assertFalse(replace.isValidShoppingOutcome(outcome(rows = listOf(line,line.copy(offerId = "original"))),"buyer"))
        assertFalse(replace.isValidShoppingOutcome(outcome(rows = listOf(line.copy(units = 1))),"buyer"))
    }
    @Test fun laterDeviceEditsDoNotInvalidateTheRecordedOriginalSuccess() {
        val newer = outcome(revision = 10,rows = emptyList())
        assertTrue(command.isValidShoppingOutcome(newer,"buyer"))
        assertTrue(command.copy(replaceOfferId = "original",reviewedSubtotalMinor = 100).isValidShoppingOutcome(newer,"buyer"))
        assertTrue(command.copy(units = 0,basis = null).isValidShoppingOutcome(outcome(revision = 10),"buyer"))
    }
    @Test fun maxRevisionCannotWrapAroundIntoAnAcceptedCommand() {
        assertFalse(command.copy(expectedRevision = Long.MAX_VALUE).isValidShoppingOutcome(outcome(),"buyer"))
        val rejection = outcome().copy(accepted = false,appliedRevision = null,errorKey = "market.shopping_changed")
        assertTrue(command.copy(expectedRevision = Long.MAX_VALUE).isValidShoppingOutcome(rejection,"buyer"))
    }
    @Test fun rejectionRequiresARecordedDomainReasonNotAnAppliedRevision() {
        val rejected = outcome().copy(accepted = false,appliedRevision = null,errorKey = "market.shopping_changed")
        assertTrue(command.isValidShoppingOutcome(rejected,"buyer"))
        assertFalse(command.isValidShoppingOutcome(rejected.copy(errorKey = " "),"buyer"))
        assertFalse(command.isValidShoppingOutcome(rejected.copy(appliedRevision = 8),"buyer"))
        assertFalse(command.isValidShoppingOutcome(outcome().copy(errorKey = "market.shopping_changed"),"buyer"))
    }
    @Test fun lookupUsesTheSameEffectCheckWithoutAcknowledgingADifferentReference() {
        assertTrue(MarketShoppingCommandLookup(command.commandId,200,outcome()).isValidShoppingLookup(command,"buyer"))
        assertFalse(MarketShoppingCommandLookup(command.commandId,200,outcome(rows = emptyList())).isValidShoppingLookup(command,"buyer"))
        assertFalse(MarketShoppingCommandLookup("other",200,outcome()).isValidShoppingLookup(command,"buyer"))
    }
}
