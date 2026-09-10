package kz.aita.server

import kz.aita.QuantityDataModel
import kotlin.test.*

class StockBatchTransferRulesTest {
    private fun q(total: Double, pieces: Boolean = false, id: String = "kg") =
        QuantityDataModel(id = id, immutableUnitName = emptyList(), total = total, pricedAmount = 1.0, roundTotal = pieces)

    @Test fun partialTransferConservesStock() {
        val result = assertNotNull(planStockBatchTransfer(q(10.5), 3.25))
        assertEquals(3.25, result.moved.total); assertEquals(7.25, result.remaining.total)
    }
    @Test fun completeTransferDoesNotLeaveNegativeResidue() {
        val result = assertNotNull(planStockBatchTransfer(q(0.1 + 0.2), 0.3))
        assertEquals(0.3, result.moved.total, 1e-12)
        assertTrue(result.remaining.total >= 0.0)
        assertEquals(0.1 + 0.2, result.moved.total + result.remaining.total)
    }
    @Test fun pieceFractionsAreRejectedNotSilentlyTruncated() {
        assertNull(planStockBatchTransfer(q(10.0, true, "pc"), 1.9))
    }
    @Test fun overdrawIsRejected() { assertNull(planStockBatchTransfer(q(10.0), 10.001)) }
    @Test fun moreThanThreeWeightDecimalsIsRejected() { assertNull(planStockBatchTransfer(q(10.0), 0.1234)) }
    @Test fun nonFiniteSourceIsRejected() {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach { assertNull(planStockBatchTransfer(q(it), 1.0)) }
    }
    @Test fun nonFiniteRequestIsRejected() {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach { assertNull(planStockBatchTransfer(q(5.0), it)) }
    }
    @Test fun zeroAndNegativeAreRejected() {
        listOf(0.0, -0.0, -1.0).forEach { assertNull(planStockBatchTransfer(q(5.0), it)); assertNull(planStockBatchTransfer(q(it), 1.0)) }
    }
    @Test fun largePieceQuantityDoesNotSaturateAtIntMax() {
        val result = assertNotNull(planStockBatchTransfer(q(4_000_000_000.0, true, "pc"), 3_000_000_000.0))
        assertEquals(3_000_000_000.0, result.moved.total); assertEquals(1_000_000_000.0, result.remaining.total)
    }
    @Test fun sourceMetadataIsPreservedInBothHalves() {
        val original = q(10.0).copy(pricedAmount = 0.1)
        val result = assertNotNull(planStockBatchTransfer(original, 2.0))
        assertEquals(original.copy(total = 2.0), result.moved)
        assertEquals(original.copy(total = 8.0), result.remaining)
    }
    @Test fun legacyRemainderIsNotRoundedAway() {
        val result = assertNotNull(planStockBatchTransfer(q(5.123456), 2.0))
        assertEquals(5.123456, result.moved.total + result.remaining.total)
    }
    @Test fun declineRestoresExactlyTheMovedQuantity() {
        val original = q(7.456)
        val moved = assertNotNull(planStockBatchTransfer(original, 2.345))
        assertEquals(original.total, assertNotNull(restoreDeclinedStockQuantity(moved.remaining, moved.moved)).total, 1e-12)
    }
    @Test fun declineKeepsInterveningSaleBalance() {
        assertEquals(5.0, assertNotNull(restoreDeclinedStockQuantity(q(3.0), q(2.0))).total)
    }
    @Test fun declineRejectsChangedMeasurementUnit() {
        assertNull(restoreDeclinedStockQuantity(q(3.0), q(2.0, id = "litre")))
        assertNull(restoreDeclinedStockQuantity(q(3.0, true), q(2.0)))
    }
    @Test fun declineRejectsCorruptOrOverflowingBalances() {
        assertNull(restoreDeclinedStockQuantity(q(Double.MAX_VALUE), q(Double.MAX_VALUE)))
        assertNull(restoreDeclinedStockQuantity(q(-1.0), q(2.0)))
        assertNull(restoreDeclinedStockQuantity(q(1.0), q(Double.NaN)))
    }
    @Test fun repeatedPartialsPreserveTotalWithoutOverdraw() {
        var source = q(100.125); var movedTotal = 0.0
        repeat(100) {
            val result = assertNotNull(planStockBatchTransfer(source, 0.125))
            source = result.remaining; movedTotal += result.moved.total
        }
        assertEquals(100.125, source.total + movedTotal); assertTrue(source.total >= 0)
    }
}
