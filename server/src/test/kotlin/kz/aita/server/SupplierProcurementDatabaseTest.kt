package kz.aita.server

import kz.aita.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Assume.assumeTrue
import java.sql.DriverManager
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.test.*

class SupplierProcurementDatabaseTest {
    private val owner=UUID.randomUUID(); private val store=UUID.randomUUID(); private val supplier=UUID.randomUUID()
    private val item=UUID.randomUUID(); private val second=UUID.randomUUID(); private val foreign=UUID.randomUUID()
    private val price=PriceDataModel("10","KZT",supplier.toString())
    private fun q(total:Double=10.0)=QuantityDataModel("0",emptyList(),total,1.0,true)
    private fun fixture(block:(Database)->Unit) {
        val url=System.getenv("AITA_STORE_ARCH_TEST_DB_URL").orEmpty()
        assumeTrue("Requires an isolated aita_test_* database",url.isNotBlank())
        require(Regex("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/aita_test_[a-z0-9]+").matches(url))
        val user=System.getProperty("user.name");val schema="procurement_"+UUID.randomUUID().toString().replace("-","")
        DriverManager.getConnection(url,user,"").use { it.createStatement().use { it.execute("CREATE SCHEMA $schema") } }
        val db=Database.connect("$url?currentSchema=$schema",driver="org.postgresql.Driver",user=user,password="")
        try {
            transaction(db) {
                SchemaUtils.create(StockItems,StockBatchesV2,SupplierOrders,SupplierOrderLines,SupplierGoodsPrices)
                // Exercise the production migration from the pre-receiving-counter shape.
                exec("ALTER TABLE supplier_order_lines DROP COLUMN received_quantity_total")
                exec(javaClass.getResource("/db/migration/V141__supplier_procurement_receiving.sql")!!.readText())
                listOf(item,second,foreign).forEach { key -> StockItems.insert {
                    it[id]=key;it[userId]=owner;it[storeId]=if(key==foreign)UUID.randomUUID() else store
                    it[barcodes]=listOf(key.toString());it[name]=listOf(LocalizedStringDataModel("en","Tea"))
                    it[description]=emptyList();it[measurementUnitId]="0";it[categoryIds]=emptyList()
                    it[salePrices]=listOf(price);it[returnPrices]=listOf(price);it[supplyPrices]=listOf(price)
                    it[isQuickItem]=false;it[imagePaths]=emptyList();it[createdAtMillis]=1L;it[updatedAtMillis]=1L;it[isActive]=true
                } }
            }
            block(db)
        } finally {
            TransactionManager.closeAndUnregister(db)
            DriverManager.getConnection(url,user,"").use{it.createStatement().use{it.execute("DROP SCHEMA $schema CASCADE")}}
        }
    }
    private fun draft(id:String=UUID.randomUUID().toString(), goods:List<UUID> = listOf(item), total:Double=10.0)=SupplierOrderWithLinesDataModel(
        SupplierOrderDataModel(id=id,storeId=store.toString(),supplierId=supplier.toString(),status=SupplierOrderStatusDataModel.Sent,orderedAtMillis=1L),
        goods.map { SupplierOrderLineDataModel(orderId=id,goodsItemId=it.toString(),requestedQuantity=q(total),expectedSupplyPrice=price) })
    private fun create(body:SupplierOrderWithLinesDataModel=draft())=createSupplierOrderInsideTransaction(owner,store,supplier,body,100L).bundle
    private fun receipt(order:SupplierOrderWithLinesDataModel,total:Double=4.0,id:String=UUID.randomUUID().toString(),line:Int=0)=ReceiveSupplierOrderRequestDataModel(
        order.order.id,listOf(ReceiveSupplierOrderLineDataModel(order.lines[line].id,order.lines[line].goodsItemId,q(total),price)),id)
    private fun receive(request:ReceiveSupplierOrderRequestDataModel)=receiveSupplierOrderInsideTransaction(owner,request,200L)
    @Test fun createRetryReturnsSameOrderAndDoesNotRewindSupplierProgress()=fixture { db ->
        val request=draft();val first=transaction(db){create(request)}
        transaction(db){SupplierOrders.update({SupplierOrders.id eq UUID.fromString(first.order.id)}){it[status]=SupplierOrderStatusDataModel.Confirmed.name}}
        val retry=transaction(db){createSupplierOrderInsideTransaction(owner,store,supplier,request,300L)}
        assertFalse(retry.changed);assertEquals(first.order.id,retry.bundle.order.id);assertEquals(SupplierOrderStatusDataModel.Confirmed,retry.bundle.order.status)
        transaction(db){assertEquals(1L,SupplierOrders.selectAll().count());assertEquals(1L,SupplierOrderLines.selectAll().count())}
    }
    @Test fun legacyBlankIdentityIsIdempotentForTheExactRetriedBody()=fixture { db ->
        val request=draft(id="");val first=transaction(db){create(request)};val retry=transaction(db){create(request)}
        assertEquals(first.order.id,retry.order.id)
        transaction(db){assertEquals(1L,SupplierOrders.selectAll().count())}
    }
    @Test fun changedPayloadUnderSameIdentityIsConflict()=fixture { db ->
        val request=draft();transaction(db){create(request)}
        assertFailsWith<SupplierProcurementFailure>{transaction(db){create(request.copy(lines=request.lines.map{it.copy(requestedQuantity=q(3.0))}))}}
        transaction(db){assertEquals(10.0,SupplierOrderLines.selectAll().single()[SupplierOrderLines.requestedQuantity].total)}
    }
    @Test fun invalidForeignOrEmptyOrderNeverPartiallySaves()=fixture { db ->
        for(request in listOf(draft(goods=listOf(item,foreign)),draft(goods=emptyList()),draft(total=0.0),draft(goods=listOf(item,item)),draft().let{it.copy(order=it.order.copy(status=SupplierOrderStatusDataModel.Delivered))})) {
            assertFailsWith<SupplierProcurementFailure>{transaction(db){create(request)}}
        }
        transaction(db){assertEquals(0L,SupplierOrders.selectAll().count());assertEquals(0L,SupplierOrderLines.selectAll().count())}
    }
    @Test fun partialDeliveryTracksOriginalReceivedQuantityAfterTheBatchIsSold()=fixture { db ->
        val order=transaction(db){create()};val request=receipt(order)
        val first=transaction(db){receive(request)}
        assertEquals(SupplierOrderStatusDataModel.PartiallyDelivered,first.bundle.order.status)
        assertEquals(6.0,first.bundle.lines.single().remainingReceivableQuantity())
        transaction(db){StockBatchesV2.update({StockBatchesV2.supplierOrderId eq UUID.fromString(order.order.id)}){it[quantity]=q(0.0)}}
        val retried=transaction(db){receive(request)};assertFalse(retried.changed)
        val final=transaction(db){receive(receipt(retried.bundle,6.0))}
        assertEquals(SupplierOrderStatusDataModel.Delivered,final.bundle.order.status)
        assertEquals(10.0,final.bundle.lines.single().receivedQuantityTotalValue())
        transaction(db){assertEquals(2L,StockBatchesV2.selectAll().count())}
    }
    @Test fun overReceivingOrASecondInvalidLineRollsBackEveryBatch()=fixture { db ->
        val order=transaction(db){create(draft(goods=listOf(item,second)))}
        val request=receipt(order).copy(receivedLines=receipt(order).receivedLines+receipt(order,11.0,line=1).receivedLines)
        assertFailsWith<SupplierProcurementFailure>{transaction(db){receive(request)}}
        transaction(db){assertEquals(0L,StockBatchesV2.selectAll().count());assertTrue(SupplierOrderLines.selectAll().all{it[SupplierOrderLines.receivedQuantityTotal]==0.0})}
    }
    @Test fun receivingRejectsWrongGoodsUnitsNegativePricesAndDuplicatedLines()=fixture { db ->
        val order=transaction(db){create()};val request=receipt(order);val line=request.receivedLines.single()
        listOf(request.copy(receivedLines=listOf(line.copy(goodsItemId=second.toString()))),
            request.copy(receivedLines=listOf(line.copy(receivedQuantity=q().copy(id="kg")))),
            request.copy(receivedLines=listOf(line.copy(actualSupplyPrice=price.copy(price="-5")))),
            request.copy(receivedLines=listOf(line,line))).forEach { bad -> assertFailsWith<SupplierProcurementFailure>{transaction(db){receive(bad)}} }
        transaction(db){assertEquals(0L,StockBatchesV2.selectAll().count())}
    }
    @Test fun declinedLinesDoNotKeepAnOtherwiseCompleteDeliveryOpen()=fixture { db ->
        val order=transaction(db){create(draft(goods=listOf(item,second)))}
        val declined=UUID.fromString(order.lines[1].id)
        transaction(db){SupplierOrderLines.update({SupplierOrderLines.id eq declined}){it[supplierAcceptedQuantity]=q(0.0)}}
        val result=transaction(db){receive(receipt(order,10.0))}
        assertEquals(SupplierOrderStatusDataModel.Delivered,result.bundle.order.status)
    }
    @Test fun cancelledOrdersAndForgedReceivingCountersAreNotAccepted()=fixture { db ->
        val order=transaction(db){create()}
        transaction(db){SupplierOrders.update({SupplierOrders.id eq UUID.fromString(order.order.id)}){it[status]=SupplierOrderStatusDataModel.Cancelled.name}}
        assertFailsWith<SupplierProcurementFailure>{transaction(db){receive(receipt(order))}}
        val forged=draft().let{it.copy(lines=it.lines.map{line->line.copy(receivedQuantityTotal=99.0,deliveredBatchIds=listOf(UUID.randomUUID().toString()))})}
        val clean=transaction(db){create(forged)};assertEquals(0.0,clean.lines.single().receivedQuantityTotal);assertTrue(clean.lines.single().deliveredBatchIds.isEmpty())
    }
    @Test fun concurrentIdenticalReceiptsInsertOneBatch()=fixture { db ->
        val order=transaction(db){create()};val request=receipt(order);val pool=Executors.newFixedThreadPool(2)
        try {
            val writes=(1..2).map{pool.submit(Callable{transaction(db){receive(request)}})}.map{it.get()}
            assertEquals(1,writes.count{it.changed})
            transaction(db){assertEquals(1L,StockBatchesV2.selectAll().count());assertEquals(4.0,SupplierOrderLines.selectAll().single()[SupplierOrderLines.receivedQuantityTotal])}
        }finally{pool.shutdownNow()}
    }
    @Test fun concurrentDifferentReceiptsCannotExceedRemainingQuantity()=fixture { db ->
        val order=transaction(db){create()};val pool=Executors.newFixedThreadPool(2)
        try {
            val writes=(1..2).map{pool.submit(Callable{runCatching{transaction(db){receive(receipt(order,7.0))}}})}.map{it.get()}
            assertEquals(1,writes.count{it.isSuccess});assertTrue(writes.single{it.isFailure}.exceptionOrNull() is SupplierProcurementFailure)
            transaction(db){assertEquals(1L,StockBatchesV2.selectAll().count())}
        }finally{pool.shutdownNow()}
    }
    @Test fun supplierCannotInsertForeignLineOrChangeBuyerQuantityDuringConfirmation()=fixture { db ->
        val order=transaction(db){create()};val line=order.lines.single()
        for (changed in listOf(line.copy(id=UUID.randomUUID().toString()),line.copy(requestedQuantity=q(200.0)),line.copy(substituteGoodsItemId=foreign.toString()))) {
            assertFailsWith<SupplierProcurementFailure>{transaction(db){validateSupplierOrderEditInsideTransaction(order,order.copy(lines=listOf(changed)),true)}}
        }
        transaction(db){validateSupplierOrderEditInsideTransaction(order,order.copy(lines=listOf(line.copy(supplierAcceptedQuantity=q(5.0),supplierOfferedSupplyPrice=price))),true)}
    }
    @Test fun quantityPriceBasisIsRespectedOnRequestAndSupplierResponse()=fixture { db ->
        val request=draft().let { it.copy(lines=it.lines.map { line->line.copy(requestedQuantity=q(250.0).copy(pricedAmount=100.0)) }) }
        val created=transaction(db){create(request)}
        assertEquals(25.0,created.order.amount!!.price.toDouble())
        val response=created.lines.map { it.copy(supplierAcceptedQuantity=it.requestedQuantity,supplierOfferedSupplyPrice=price) }
        assertEquals(25.0,supplierResponseAmountForStorage(response,supplier)!!.price.toDouble())
    }
    @Test fun staleOrderEditCannotReplaceAConfirmedSupplierResponse()=fixture { db ->
        val order=transaction(db){create()}
        val confirmed=order.copy(order=order.order.copy(status=SupplierOrderStatusDataModel.Confirmed,updatedAtMillis=101L))
        assertFailsWith<SupplierProcurementFailure>{transaction(db){validateSupplierOrderEditInsideTransaction(confirmed,order,true)}}
        assertFailsWith<SupplierProcurementFailure>{transaction(db){validateSupplierOrderEditInsideTransaction(confirmed,confirmed.copy(order=confirmed.order.copy(status=SupplierOrderStatusDataModel.Sent)),false)}}
    }
    @Test fun existingLegacyBatchMeansReceivedRatherThanNewStockOnRetry()=fixture { db ->
        val order=transaction(db){create()};val line=UUID.fromString(order.lines.single().id)
        transaction(db){SupplierOrderLines.update({SupplierOrderLines.id eq line}){it[receivedQuantityTotal]=null;it[deliveredBatchIds]=listOf(UUID.randomUUID().toString())}}
        assertFailsWith<SupplierProcurementFailure>{transaction(db){receive(receipt(order,1.0))}}
        transaction(db){assertEquals(0L,StockBatchesV2.selectAll().count())}
    }
    @Test fun editCannotForgeReceivingEvidenceOrOverwriteSupplierAcceptance()=fixture { db ->
        val order=transaction(db){create()};val line=order.lines.single()
        for (changed in listOf(line.copy(receivedQuantityTotal=2.0),line.copy(deliveredBatchIds=listOf(UUID.randomUUID().toString())),line.copy(id="",goodsItemId=second.toString(),receivedQuantityTotal=1.0)))
            assertFailsWith<SupplierProcurementFailure>{transaction(db){validateSupplierOrderEditInsideTransaction(order,order.copy(lines=listOf(changed)),false)}}
        val accepted=order.copy(lines=listOf(line.copy(supplierAcceptedQuantity=q(6.0))))
        assertFailsWith<SupplierProcurementFailure>{transaction(db){validateSupplierOrderEditInsideTransaction(accepted,accepted,false)}}
    }
    @Test fun orderUnitMetadataCannotTurnPiecesIntoFractionalStock()=fixture { db ->
        val request=draft().let { it.copy(lines=it.lines.map { line->line.copy(requestedQuantity=q(1.5).copy(roundTotal=false)) }) }
        assertFailsWith<SupplierProcurementFailure>{transaction(db){create(request)}}
        transaction(db){assertEquals(0L,SupplierOrders.selectAll().count())}
    }
    @Test fun customPriceBasisIsNormalizedForLearnedPriceAndReceivedBatch()=fixture { db ->
        val request=draft().let { it.copy(lines=it.lines.map { line->line.copy(requestedQuantity=q(250.0).copy(pricedAmount=100.0)) }) }
        val order=transaction(db){create(request)};val line=order.lines.single().copy(supplierAcceptedQuantity=order.lines.single().requestedQuantity,supplierOfferedSupplyPrice=price)
        transaction(db){learnSupplierPriceFromResponseLineInsideTransaction(owner,store,supplier,line,300L)}
        transaction(db){assertEquals(0.1,SupplierGoodsPrices.selectAll().single()[SupplierGoodsPrices.supplyPrice].price.toDouble())}
        val receivingRequest=ReceiveSupplierOrderRequestDataModel(order.order.id,listOf(ReceiveSupplierOrderLineDataModel(line.id,line.goodsItemId,line.requestedQuantity,price)),UUID.randomUUID().toString())
        transaction(db){receive(receivingRequest);val batch=StockBatchesV2.selectAll().single();assertEquals(1.0,batch[StockBatchesV2.quantity].pricedAmount);assertEquals(250.0,batch[StockBatchesV2.quantity].total);assertEquals(0.1,batch[StockBatchesV2.supplyPrice].price.toDouble())}
    }
    @Test fun committedRetriesBypassChangedBusinessPolicyButNewWritesDoNot()=fixture { db ->
        val request=draft();val order=transaction(db){create(request)}
        val replay=transaction(db){createSupplierOrderInsideTransaction(owner,store,supplier,request,300L){throw SupplierProcurementFailure("contract")}}
        assertFalse(replay.changed)
        val receipt=receipt(order);transaction(db){receive(receipt)}
        assertFalse(transaction(db){receiveSupplierOrderInsideTransaction(owner,receipt,400L){throw SupplierProcurementFailure("contract")}}.changed)
        assertFailsWith<SupplierProcurementFailure>{transaction(db){receiveSupplierOrderInsideTransaction(owner,receipt(order,1.0),500L){throw SupplierProcurementFailure("contract")}}}
        transaction(db){assertEquals(1L,StockBatchesV2.selectAll().count())}
    }
    @Test fun nonDecimalPricesAreValidationFailuresRatherThanServerErrors()=fixture { db ->
        val order=transaction(db){create()}
        for (raw in listOf("0x1.0p0","NaN","Infinity","not-a-price")) {
            val invalidOrder=draft().let { it.copy(lines=it.lines.map { line->line.copy(expectedSupplyPrice=price.copy(price=raw)) }) }
            val failure=assertFailsWith<SupplierProcurementFailure>{transaction(db){create(invalidOrder)}}
            assertEquals("invalid",failure.kind)
            val input=receipt(order).let { it.copy(receivedLines=it.receivedLines.map { line->line.copy(actualSupplyPrice=price.copy(price=raw)) }) }
            assertEquals("invalid",assertFailsWith<SupplierProcurementFailure>{transaction(db){receive(input)}}.kind)
        }
        transaction(db){assertEquals(1L,SupplierOrders.selectAll().count());assertEquals(0L,StockBatchesV2.selectAll().count())}
    }
}
