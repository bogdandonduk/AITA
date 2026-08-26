package kz.aita

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SupplierOrderPromiseBoardTest {
    private val unit = QuantityDataModel(
        id = "unit",
        immutableUnitName = listOf(LocalizedStringDataModel("main", "unit")),
        total = 1.0,
        pricedAmount = 1.0,
        roundTotal = true,
    )

    private fun order(
        id: String,
        status: SupplierOrderStatusDataModel,
        dueAtMillis: Long?,
        storeId: String = "store-$id",
    ) = SupplierOrderDataModel(
        id = id,
        storeId = storeId,
        supplierId = "supplier",
        confirmedDeliveryTimeMillis = dueAtMillis,
        status = status,
        isActive = true,
    )

    private fun line(orderId: String, goodsId: String) = SupplierOrderLineDataModel(
        id = "line-$orderId-$goodsId",
        orderId = orderId,
        goodsItemId = goodsId,
        requestedQuantity = unit,
        goodsItemNameSnapshot = listOf(LocalizedStringDataModel("main", "Goods $goodsId")),
    )

    @Test
    fun localPromiseBucketsMirrorOpenOrderSemanticsAndKeepServerEnrichment() {
        val now = 1_720_000_000_000L
        val todayStart = supplierUiDayStartMillis(now)
        val overdue = order("late", SupplierOrderStatusDataModel.Sent, todayStart - 1L)
        val today = order("pack", SupplierOrderStatusDataModel.Packed, todayStart + 1L)
        val tomorrow = order(
            "drive",
            SupplierOrderStatusDataModel.InDelivery,
            todayStart + AITA_SUPPLIER_UI_DAY_MILLIS + 1L,
        )
        val alreadyDelivered = order(
            "done",
            SupplierOrderStatusDataModel.Delivered,
            todayStart - AITA_SUPPLIER_UI_DAY_MILLIS,
        )
        val serverOverdue = SupplierDashboardDeliveryBucketDataModel(
            bucketId = "overdue",
            title = listOf(LocalizedStringDataModel("main", "Server overdue title")),
            goodsPreview = listOf(LocalizedStringDataModel("main", "Server preview")),
        )

        val buckets = buildSupplierOrderPromiseBuckets(
            orders = listOf(overdue, today, tomorrow, alreadyDelivered, overdue.copy(storeId = "duplicate")),
            lines = listOf(line("late", "A"), line("pack", "B"), line("done", "C")),
            nowMillis = now,
            serverBuckets = listOf(serverOverdue),
        )

        assertEquals(listOf("overdue", "today", "tomorrow"), buckets.map { it.bucketId })

        val overdueBucket = assertNotNull(buckets.firstOrNull { it.bucketId == "overdue" })
        assertEquals(1, overdueBucket.orderCount)
        assertEquals(1, overdueBucket.lineCount)
        assertEquals(1, overdueBucket.storeCount)
        assertEquals(1, overdueBucket.actionRequiredOrderCount)
        assertEquals("Server overdue title", overdueBucket.title.first().value)
        assertTrue(overdueBucket.goodsPreview.first().value.contains("Goods A"))

        val todayBucket = assertNotNull(buckets.firstOrNull { it.bucketId == "today" })
        assertEquals(1, todayBucket.packedOrderCount)

        val tomorrowBucket = assertNotNull(buckets.firstOrNull { it.bucketId == "tomorrow" })
        assertEquals(1, tomorrowBucket.inDeliveryOrderCount)
    }

    @Test
    fun anOpenOrderMovesToOverdueWhenTheLiveClockCrossesItsPromiseDay() {
        val initialNow = 1_720_000_000_000L
        val initialDayStart = supplierUiDayStartMillis(initialNow)
        val dueAt = initialDayStart + 1L
        val openOrder = order("moving", SupplierOrderStatusDataModel.Confirmed, dueAt)

        val initialBuckets = buildSupplierOrderPromiseBuckets(
            orders = listOf(openOrder),
            lines = emptyList(),
            nowMillis = initialNow,
        )
        val laterBuckets = buildSupplierOrderPromiseBuckets(
            orders = listOf(openOrder),
            lines = emptyList(),
            nowMillis = initialDayStart + (2L * AITA_SUPPLIER_UI_DAY_MILLIS),
        )

        assertEquals("today", initialBuckets.single().bucketId)
        assertEquals("overdue", laterBuckets.single().bucketId)
    }
}
