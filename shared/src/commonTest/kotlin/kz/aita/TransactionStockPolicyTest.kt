package kz.aita

import kotlinx.serialization.decodeFromString
import kotlin.test.*

class TransactionStockPolicyTest {
    private fun allocation(id: String, quantity: Double) = TransactionStockAllocationDataModel(id, "store", quantity)

    @Test fun recordsEveryConsumedBatchInInventoryOrder() {
        val source = listOf(allocation("shelf", 2.0), allocation("next", 8.0), allocation("later", 5.0))
        assertEquals(listOf(allocation("shelf", 2.0), allocation("next", 3.0)), planTransactionStockConsumption(source, 5.0))
        assertEquals(15.0, source.sumOf { it.quantity })
    }
    @Test fun neverFabricatesStockOrAcceptsInvalidQuantities() {
        assertNull(planTransactionStockConsumption(listOf(allocation("a", 2.0)), 3.0))
        for (quantity in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertNull(planTransactionStockConsumption(listOf(allocation("a", 2.0)), quantity))
        }
        assertNull(planTransactionStockConsumption(listOf(allocation("a", 2.0), allocation("a", 2.0)), 3.0))
        assertNull(planTransactionStockConsumption(listOf(allocation("a", Double.NaN)), 1.0))
    }
    @Test fun preservesFractionalQuantitiesAndSkipsEmptyBatches() {
        assertEquals(listOf(allocation("b", 0.125), allocation("c", 0.25)),
            planTransactionStockConsumption(listOf(allocation("a", 0.0), allocation("b", 0.125), allocation("c", 1.0)), 0.375))
    }
    @Test fun cumulativeReturnsCannotExceedOriginalReceipt() {
        assertTrue(validLinkedReturnQuantity(5.0, 2.0, 3.0))
        assertFalse(validLinkedReturnQuantity(5.0, 2.0, 3.001))
        assertFalse(validLinkedReturnQuantity(5.0, 5.0, 1.0))
        assertFalse(validLinkedReturnQuantity(5.0, -1.0, 1.0))
        assertFalse(validLinkedReturnQuantity(Double.NaN, 0.0, 1.0))
    }
    @Test fun oldTransactionLinesRetainUnknownProvenance() {
        val line = jsonBase.decodeFromString<GoodsItemInTransactionDataModel>("""{"barcode":"123","quantity":1.0,"pricePerUnit":10.0}""")
        assertTrue(line.sourceBatchAllocations.isEmpty())
        assertNull(line.shelfBatchIdAtSale)
        assertNull(line.originalTransactionId)
        assertNull(line.returnDestinationKind)
    }
    @Test fun searchRequiresUsefulBoundedLiteralInput() {
        assertFalse(validReturnReceiptQuery("ab", false))
        assertTrue(validReturnReceiptQuery("abc", false))
        assertTrue(validReturnReceiptQuery("txn-11111111-1111-4111-8111-111111111111", true))
        assertFalse(validReturnReceiptQuery("%", true))
        assertFalse(validReturnReceiptQuery("abc' OR 1=1", true))
        assertFalse(validReturnReceiptQuery("a".repeat(161), true))
    }
}
