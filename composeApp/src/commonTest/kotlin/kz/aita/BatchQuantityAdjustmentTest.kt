package kz.aita

import kotlin.test.*

class BatchQuantityAdjustmentTest {
    private val pieces = QuantityDataModel("0", emptyList(), total = 1.0, roundTotal = true)
    private val weight = pieces.copy(id = "1", roundTotal = false)
    @Test fun subtractionKeepsUnitsAndAllowsEmptyBatchWithoutDeletingHistory() {
        assertEquals("7", subtractBatchQuantityText("10", "3", pieces))
        assertEquals("0", subtractBatchQuantityText("10", "10", pieces))
        assertEquals("1.125", subtractBatchQuantityText("1.5", "0.375", weight))
        assertEquals("0.0", subtractBatchQuantityText("0.375", "0,375", weight))
    }
    @Test fun restoredEditKeepsItsOriginalRevisionUntilExplicitlyReopened() {
        val batch = GoodsBatchDataModel(id = "batch", goodsItemId = "item", storeId = "store",
            quantity = pieces.copy(total = 10.0), supplyPrice = PriceDataModel("1", "KZT", ""), updatedAtMillis = 123L)
        val draft = batch.toDraft("0").copy(quantityText = "7")
        val restored = assertNotNull(goodsBatchDraftFromNavigationStateString(draft.toNavigationStateString()))
        assertEquals(123L, restored.editRevision)
        assertEquals("7", restored.quantityText)
    }
    @Test fun overdrawInvalidAndFractionalPieceRemovalAreRejected() {
        listOf("11", "0", "-1", "1.5", "NaN", "Infinity", "", "99999999999999999999").forEach {
            assertNull(subtractBatchQuantityText("10", it, pieces), it)
        }
    }
}
