package kz.aita

import kotlin.test.*

class SupplierReceivingRulesTest {
    private fun quantity(total: Double = 10.0, unit: String = "0", whole: Boolean = true) = QuantityDataModel(unit, emptyList(), total, 1.0, whole)
    private fun line() = SupplierOrderLineDataModel(id = "line", orderId = "order", goodsItemId = "goods", requestedQuantity = quantity())
    @Test fun partialReceiptLeavesTheExactRemainingQuantity() {
        val line = line().copy(receivedQuantityTotal = 4.0, deliveredBatchIds = listOf("batch"))
        assertEquals(6.0, line.remainingReceivableQuantity())
        assertTrue(supplierReceiptQuantityValid(line, quantity(6.0)))
        assertFalse(supplierReceiptQuantityValid(line, quantity(6.1)))
    }
    @Test fun declinedLinesHaveNoReceivableQuantity() {
        val line = line().copy(supplierAcceptedQuantity = quantity(0.0), receivedQuantityTotal = 0.0)
        assertEquals(0.0, line.remainingReceivableQuantity())
        assertFalse(supplierReceiptQuantityValid(line, quantity(1.0)))
    }
    @Test fun legacyReceiptNeverReopensAsItsStockIsSold() {
        val line = line().copy(deliveredBatchIds = listOf("old-batch"))
        assertEquals(10.0, line.receivedQuantityTotalValue())
        assertEquals(0.0, line.remainingReceivableQuantity())
    }
    @Test fun changedUnitsFractionalPiecesAndNonFiniteAmountsAreRejected() {
        val line = line()
        listOf(quantity(1.5), quantity(Double.NaN), quantity(Double.POSITIVE_INFINITY), quantity(-1.0), quantity(0.0), quantity(1.0, "kg"), quantity(1.0).copy(pricedAmount = 0.0)).forEach {
            assertFalse(supplierReceiptQuantityValid(line, it))
        }
        val weighted = line.copy(requestedQuantity = quantity(5.0,"kg",false))
        assertTrue(supplierReceiptQuantityValid(weighted, quantity(1.25,"kg",false)))
    }
    @Test fun closedAndUnsentOrdersCannotReceive() {
        val order = SupplierOrderDataModel(storeId = "store", supplierId = "supplier")
        for (state in listOf(SupplierOrderStatusDataModel.Draft, SupplierOrderStatusDataModel.Delivered, SupplierOrderStatusDataModel.Cancelled))
            assertFalse(order.copy(status = state).canReceiveSupplierOrder())
        assertTrue(order.copy(status = SupplierOrderStatusDataModel.PartiallyDelivered).canReceiveSupplierOrder())
        assertFalse(order.copy(status = SupplierOrderStatusDataModel.Sent, isActive = false).canReceiveSupplierOrder())
    }
    @Test fun unrelatedCreateSuccessCannotClearPendingRequest() {
        val request=SupplierOrderWithLinesDataModel(SupplierOrderDataModel(id="order",userId="owner",storeId="store",supplierId="supplier"),listOf(line()))
        assertTrue(request.acknowledgesSupplierCreate(request))
        for (changed in listOf(request.order.copy(id="different"),request.order.copy(userId="other"),request.order.copy(storeId="foreign"),request.order.copy(supplierId="foreign")))
            assertFalse(request.copy(order=changed).acknowledgesSupplierCreate(request))
        assertFalse(request.copy(lines=emptyList()).acknowledgesSupplierCreate(request))
    }
    @Test fun receiptNeedsMatchingOrderLinesCumulativeQuantityAndBatchEvidence() {
        val request=ReceiveSupplierOrderRequestDataModel("order",listOf(ReceiveSupplierOrderLineDataModel("line","goods",quantity(4.0),PriceDataModel("10","KZT","supplier"))))
        val received=line().copy(receivedQuantityTotal=4.0,deliveredBatchIds=listOf("00000000-0000-4000-8000-000000000001"))
        val response=SupplierOrderWithLinesDataModel(SupplierOrderDataModel(id="order",storeId="store",supplierId="supplier"),listOf(received))
        assertTrue(response.acknowledgesSupplierReceipt(request))
        assertFalse(response.copy(order=response.order.copy(id="other")).acknowledgesSupplierReceipt(request))
        for (changed in listOf(received.copy(receivedQuantityTotal=3.0),received.copy(deliveredBatchIds=emptyList()),received.copy(goodsItemId="different"),received.copy(orderId="different")))
            assertFalse(response.copy(lines=listOf(changed)).acknowledgesSupplierReceipt(request))
    }
}
