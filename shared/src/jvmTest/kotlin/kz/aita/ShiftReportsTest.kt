package kz.aita

import kotlin.test.*

class ShiftReportsTest {
    private val shift = WorkshiftDataModel(id = "shift", storeId = "store", workerUserId = "worker", startedAtMillis = 1000)
    private fun receipt(id: String = "sale", actor: String = "worker", store: String = "store", at: Long = 1500, type: String = "purchase", currency: String = "KZT") =
        TransactionDataModel(id, 0L, type, store, listOf(GoodsItemInTransactionDataModel("123", 2.0, 50.0, currencyCode = currency)),
            60.0, 30.0, 0, timeMillis = at, actorUserId = actor)
    @Test fun xUsesTheEmployeeAndTheBranchAndIncludesTheCurrentInstant() {
        val result = buildShiftReport(shift, SHIFT_REPORT_X, listOf(receipt(), receipt(actor = "other"), receipt(store = "other"), receipt(at = 999), receipt(at = 1601)), 1600)
        assertEquals(1, result.transactionCount); assertEquals(100.0, result.totals.single().sales)
        assertEquals(10.0, result.totals.single().debt); assertEquals(1600L, result.throughMillis)
        assertEquals(1, buildShiftReport(shift, SHIFT_REPORT_X, listOf(receipt(at = 1600)), 1600).transactionCount)
    }
    @Test fun zKeepsTheClosingBoundaryEvenWhenViewedLater() {
        val closed = shift.copy(endedAtMillis = 1600, isActive = false)
        val a = buildShiftReport(closed, SHIFT_REPORT_Z, listOf(receipt(), receipt(at = 1600), receipt(at = 1700)), 2000)
        val b = buildShiftReport(closed, SHIFT_REPORT_Z, listOf(receipt(), receipt(at = 1600), receipt(at = 1700)), 9000)
        assertEquals(1, a.transactionCount); assertEquals(a.totals, b.totals); assertEquals(1600L, b.throughMillis)
        assertFailsWith<IllegalArgumentException> { buildShiftReport(shift, SHIFT_REPORT_Z, emptyList(), 2000) }
        assertFailsWith<IllegalArgumentException> { buildShiftReport(closed, SHIFT_REPORT_X, emptyList(), 2000) }
    }
    @Test fun currenciesAndReturnsAreNotMixedAndLocalTotalsStayProvisional() {
        val report = buildShiftReport(shift, SHIFT_REPORT_X, listOf(receipt(), receipt(id = "refund", type = "return"), receipt(id = "rub", currency = "RUB")), 2000, localOnly = true)
        assertTrue(report.localOnly); assertEquals(2, report.totals.size)
        assertEquals(100.0, report.totals.first { it.currencyCode == "KZT" }.returns)
        assertEquals(0.0, report.totals.first { it.currencyCode == "RUB" }.returns)
        val mixed = receipt().let { it.copy(goodsInTransaction = it.goodsInTransaction + it.goodsInTransaction.single().copy(currencyCode = "RUB")) }
        assertTrue(buildShiftReport(shift, SHIFT_REPORT_X, listOf(mixed), 2000).totals.all { !it.paymentsKnown })
    }
}
