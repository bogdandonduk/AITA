package kz.aita

import kotlin.test.*
import kotlinx.serialization.encodeToString

class SupplierPickingTest {
    private val q = QuantityDataModel("0", emptyList(), 3.0, 1.0, true)
    private val order = SupplierOrderDataModel(id = "order", storeId = "store", supplierId = "supplier", status = SupplierOrderStatusDataModel.Confirmed)
    private val line = SupplierOrderLineDataModel("line", "order", "goods", q, supplierAcceptedQuantity = q)
    private fun rows(value: SupplierOrderLineDataModel = line) = supplierPickingRows(listOf(SupplierOrderWithLinesDataModel(order, listOf(value))))
    @Test fun changingAgreedQuantityOrSubstituteInvalidatesAPreviousPhysicalCheck() {
        val row = rows().single(); val marks = mapOf(row.key to row.signature)
        assertEquals(marks, reconcilePickingMarks(marks, rows()))
        assertTrue(reconcilePickingMarks(marks, rows(line.copy(supplierAcceptedQuantity = q.copy(total = 4.0)))).isEmpty())
        assertTrue(reconcilePickingMarks(marks, rows(line.copy(substituteGoodsItemId = "other"))).isEmpty())
        assertTrue(reconcilePickingMarks(marks, rows(line.copy(supplierAcceptedQuantity = null))).isEmpty())
        assertEquals(marks, reconcilePickingMarks(marks, rows(line.copy(supplierComment = "Delivery today"))))
    }
    @Test fun closedForeignInactiveOrDuplicateLinesNeverInflateProgress() {
        assertTrue(supplierPickingRows(listOf(SupplierOrderWithLinesDataModel(order.copy(status = SupplierOrderStatusDataModel.Delivered), listOf(line)))).isEmpty())
        assertTrue(rows(line.copy(orderId = "someone-else")).isEmpty())
        assertTrue(rows(line.copy(isActive = false)).isEmpty())
        assertEquals(1, supplierPickingRows(listOf(SupplierOrderWithLinesDataModel(order, listOf(line, line)))).size)
    }
    @Test fun absentRejectedOrInvalidAgreedQuantitiesCannotBeChecked() {
        listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach { total ->
            assertNull(rows(line.copy(supplierAcceptedQuantity = q.copy(total = total))).single().quantity)
        }
    }
    @Test fun marksRoundTripWithoutRestoringInvalidContent() {
        val row = rows().single(); val marks = mapOf(row.key to row.signature)
        assertEquals(marks, decodePickingMarks(jsonBase.encodeToString(marks)))
        assertTrue(decodePickingMarks("broken").isEmpty())
        assertTrue(decodePickingMarks("x".repeat(32769)).isEmpty())
        assertTrue(reconcilePickingMarks(marks, emptyList()).isEmpty())
    }
}
