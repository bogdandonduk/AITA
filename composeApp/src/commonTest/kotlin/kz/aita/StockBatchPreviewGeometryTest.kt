package kz.aita

import kotlin.test.Test
import kotlin.test.assertEquals

class StockBatchPreviewGeometryTest {
    private val step = (STOCK_BATCH_PREVIEW_WIDTH_DP + STOCK_BATCH_PREVIEW_GAP_DP).toFloat()

    @Test
    fun aWholeVisibleSlotMovesOneBatchAtEveryDensity() {
        listOf(0.75f, 1f, 1.5f, 2f, 3f).forEach { density ->
            assertEquals(2, stockBatchPreviewTarget(1, 5, step * density, step * density))
            assertEquals(0, stockBatchPreviewTarget(1, 5, step * density, -step * density))
        }
    }

    @Test
    fun aSmallMovementDoesNotAccidentallyReorder() {
        assertEquals(1, stockBatchPreviewTarget(1, 5, step, step * .49f))
        assertEquals(1, stockBatchPreviewTarget(1, 5, step, -step * .49f))
        assertEquals(2, stockBatchPreviewTarget(1, 5, step, step * .51f))
        assertEquals(0, stockBatchPreviewTarget(1, 5, step, -step * .51f))
    }

    @Test
    fun dragNeverMovesPastTheFirstOrLastSlot() {
        assertEquals(-2 * step, stockBatchPreviewDragOffset(2, 5, step, -10_000f))
        assertEquals(2 * step, stockBatchPreviewDragOffset(2, 5, step, 10_000f))
        assertEquals(0, stockBatchPreviewTarget(2, 5, step, -10_000f))
        assertEquals(4, stockBatchPreviewTarget(2, 5, step, 10_000f))
    }

    @Test
    fun aSingleBatchCannotBeDraggedOutOfItsSlot() {
        assertEquals(0f, stockBatchPreviewDragOffset(0, 1, step, step))
        assertEquals(0, stockBatchPreviewTarget(0, 1, step, step))
    }

    @Test
    fun emptyOrInvalidGeometryKeepsTheCardSafe() {
        assertEquals(0, stockBatchPreviewTarget(0, 0, step, step))
        assertEquals(1, stockBatchPreviewTarget(1, 5, 0f, step))
        assertEquals(1, stockBatchPreviewTarget(1, 5, Float.NaN, step))
        assertEquals(1, stockBatchPreviewTarget(1, 5, step, Float.NaN))
        assertEquals(0f, stockBatchPreviewDragOffset(-1, 5, step, step))
    }
}
