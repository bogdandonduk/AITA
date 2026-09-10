package kz.aita

import kotlinx.coroutines.*
import kotlin.test.*

class AnalyticsPreparationTest {
    private val owner = InventoryOwner("store", "account", 1, 1)
    private val window = AnalyticsWindow(1000,10000,"UTC")
    private val first = GoodsItemInTransactionDataModel("111",2.0,50.0,supplierId=7,goodsItemId="first")
    private val second = GoodsItemInTransactionDataModel("222",1.0,100.0,supplierId=8,goodsItemId="second")
    private val stock = listOf(GoodsItemDataModel(id="first",storeId="store",barcodes=listOf("111")),GoodsItemDataModel(id="second",storeId="store",barcodes=listOf("222")))
    private fun tx(id:String="sale",type:String="purchase",store:String="store",at:Long=5000) =
        TransactionDataModel(id,1,type,store,listOf(first,second),120.0,30.0,0,timeMillis=at)
    private fun input(transactions:List<TransactionDataModel> = listOf(tx()),selection:AnalyticsSelection=AnalyticsSelection("all"),events:List<CashRegisterEventDataModel> = emptyList()) =
        AnalyticsInputs(owner,selection,window,transactions,stock,emptyList(),events,emptyList(),"KZT")

    @Test fun summaryContainsOneConsistentReceiptProjection() = runBlocking {
        val result=prepareAnalytics(input())
        val sales=result.type("purchase")
        assertEquals(1,sales.count);assertEquals(200.0,sales.total);assertEquals(3.0,sales.quantity)
        assertEquals(120.0,sales.cash);assertEquals(30.0,sales.card);assertEquals(50.0,sales.debt)
        assertEquals(200.0,result.dashboard.grossSales);assertEquals(150.0,sales.history.single().total)
    }
    @Test fun otherStoresAndDatesDoNotEnterCurrentDashboard() = runBlocking {
        val result=prepareAnalytics(input(listOf(tx(),tx("other",store="branch"),tx("before",at=999),tx("end",at=10000))))
        assertEquals(listOf("sale"),result.transactions.map { it.id });assertEquals(1,result.dashboard.saleCount)
    }
    @Test fun goodsScopeProratesReceiptPayments() = runBlocking {
        val result=prepareAnalytics(input(selection=AnalyticsSelection("all",goodsItemId="first")))
        assertEquals(100.0,result.type("purchase").total);assertEquals(60.0,result.type("purchase").cash)
        assertEquals(15.0,result.type("purchase").card);assertEquals(1,result.transactions.single().goodsInTransaction.size)
    }
    @Test fun legacyNumericSupplierFilterDoesNotMatchEverySupplier() = runBlocking {
        val result=prepareAnalytics(input(selection=AnalyticsSelection("all",supplierId="7")))
        assertEquals(listOf(7L),result.transactions.single().goodsInTransaction.map { it.supplierId })
        assertEquals(100.0,result.type("purchase").total)
    }
    @Test fun immutableSourceIdentityControlsCacheReuse() {
        val a=input();val b=AnalyticsInputs(a.owner,a.selection,a.window,a.transactions,a.stock,a.batches,a.events,a.suppliers,a.currency)
        assertTrue(a.sameSources(b));assertFalse(a.sameSources(input()))
        assertFalse(a.sameSources(AnalyticsInputs(owner.copy(epoch=2),a.selection,a.window,a.transactions,a.stock,a.batches,a.events,a.suppliers,a.currency)))
    }
    @Test fun emptyAuthoritativeLedgerIsARealZeroDashboard() = runBlocking {
        val result=prepareAnalytics(input(emptyList()))
        assertEquals(0,result.dashboard.transactionCount);assertEquals(0.0,result.type("purchase").total)
    }
    @Test fun cashEventsRespectStoreAndDate() = runBlocking {
        val events=listOf(CashRegisterEventDataModel("in","store",type=CASH_REGISTER_EVENT_SALE_CASH_IN,amount=120.0,timeMillis=5000),
            CashRegisterEventDataModel("out","store",type=CASH_REGISTER_EVENT_EXTRACTION,amount=20.0,timeMillis=5000),
            CashRegisterEventDataModel("other","branch",type=CASH_REGISTER_EVENT_EXTRACTION,amount=999.0,timeMillis=5000))
        val result=prepareAnalytics(input(events=events));assertEquals(2,result.events.size);assertEquals(120.0,result.saleCash);assertEquals(20.0,result.extractedCash)
    }
    @Test fun serverTotalsNeverPretendDetailedPaymentsAreKnown() = runBlocking {
        val local=prepareAnalytics(input());val remote=prepareRemoteAnalytics(owner,local.selection,window,local.dashboard)
        assertTrue(remote.remoteOnly);assertFalse(remote.stock.available);assertFalse(remote.cashAvailable)
        assertEquals(200.0,remote.type("purchase").total);assertFalse(remote.type("purchase").paymentsKnown)
        assertFalse(remote.type("purchase").historyKnown);assertTrue(remote.transactions.isEmpty())
    }
    @Test fun separateCashPermissionCanStillProvideCashWithServerTotals() = runBlocking {
        val local=prepareAnalytics(input());val events=listOf(CashRegisterEventDataModel("in","store",type=CASH_REGISTER_EVENT_SALE_CASH_IN,amount=32.0,timeMillis=5000))
        val remote=prepareRemoteAnalytics(owner,local.selection,window,local.dashboard,events)
        assertTrue(remote.cashAvailable);assertEquals(32.0,remote.saleCash);assertEquals(events,remote.events)
    }
    @Test fun customWindowPreservesExclusiveBounds() {
        val result=AnalyticsSelection("custom",100,200).window(1000)
        assertEquals(100,result.startMillis);assertEquals(200,result.endMillisExclusive)
    }
    @Test fun cancellationCannotPublishAPartialSummary() = runBlocking {
        val job=launch(start=CoroutineStart.LAZY) { prepareAnalytics(input(List(10000) { tx(it.toString()) }));fail("Cancelled computation ran") }
        job.cancel();job.join();assertTrue(job.isCancelled)
    }
}
