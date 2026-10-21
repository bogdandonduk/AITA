package kz.aita

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class MarketDeliveryTest {
    private fun id(n:Int) = "00000000-0000-0000-0000-${n.toString().padStart(12,'0')}"
    private class Owner(override val accountId:String) : MarketAccountScope {
        override val generation=1L
        var current=true
        override fun isCurrent()=current
    }
    private fun order()=MarketDeliveryOrder(id(1),id(2),id(3),"Shop",
        MarketDeliveryRequest(id(3),1,1,MarketDeliveryMode.EXPRESS,MarketDeliveryAddress("Buyer","+77000000000","Astana, street 1"),
            listOf(MarketDeliveryLine(MarketShoppingLine(id(4),id(3),"Item","Shop",2,MarketShoppingBasis(currencyCode="KZT",unitId="piece",pricedAmount=1.0)),19999)),"KZT",1000),
        createdAtMillis=1000,updatedAtMillis=1000,estimatedFromMillis=2000,estimatedToMillis=3000,handoverCode="123456")
    private fun command()=MarketDeliveryCommand(id(1),MarketDeliveryAction.REQUEST,request=order().request)
    private fun <T> ok(value:T)=ResponseDataModel(null,value,false,200)
    private class Memory {
        val values=mutableMapOf<String,String?>()
        var failWrite=false
        suspend fun get(key:String)=values[key]
        suspend fun put(key:String,value:String?){ check(!failWrite);values[key]=value }
    }
    @Test fun feesUseExactMinorUnitsAndRejectAmbiguousInput() {
        assertEquals(12345L,parseDeliveryFeeMinor("123,45"));assertEquals(0L,parseDeliveryFeeMinor("0"))
        for(input in listOf("","-1","1e6","1.009","1,2.3","9999999999.99"))assertNull(parseDeliveryFeeMinor(input),input)
        assertEquals(40998L,order().totalMinor)
        assertNull(marketDeliveryItemsTotal(order().request.lines+order().request.lines))
        assertNull(marketDeliveryItemsTotal(order().request.lines.map { it.copy(unitPriceMinor=Long.MAX_VALUE) }))
    }
    @Test fun pickupDoesNotNeedAddressButDeliveryDoes() {
        val address=MarketDeliveryAddress("Buyer","+77000000000")
        assertTrue(address.isValid(MarketDeliveryMode.PICKUP));assertFalse(address.isValid(MarketDeliveryMode.EXPRESS))
        assertFalse(address.copy(phone="garbage").isValid(MarketDeliveryMode.PICKUP))
    }
    @Test fun transitionsRequireAcceptedCourierAndTerminalOrdersAreImmutable() {
        val ready=order().copy(status=MarketDeliveryStatus.READY,courierId=id(5))
        assertFalse(MarketDeliveryAction.DISPATCH in ready.availableActions(false,true,false))
        assertEquals(setOf(MarketDeliveryAction.ACCEPT_ASSIGNMENT),ready.availableActions(false,false,true))
        assertTrue(MarketDeliveryAction.DISPATCH in ready.copy(courierAccepted=true).availableActions(false,false,true))
        assertFalse(MarketDeliveryAction.DELIVER in ready.availableActions(true,false,false))
        assertTrue(MarketDeliveryAction.DELIVER in ready.copy(request=ready.request.copy(mode=MarketDeliveryMode.PICKUP)).availableActions(true,false,false))
        assertTrue(order().copy(status=MarketDeliveryStatus.DELIVERED).availableActions(true,true,true).isEmpty())
        assertTrue(MarketDeliveryAction.ASSIGN in ready.copy(status=MarketDeliveryStatus.ON_THE_WAY).availableActions(false,true,false))
    }
    @Test fun staleInaccurateOrInvalidCoordinatesAreNeverLive() {
        val point=MarketCourierPosition(51.1,71.4,20.0,1_000_000,1_000_000)
        assertTrue(point.isValid(1_000_000));assertTrue(point.isFresh(1_060_000))
        assertFalse(point.isFresh(1_120_001));assertFalse(point.isValid(1_200_001))
        assertFalse(point.copy(latitude=Double.NaN).isValid(1_000_000));assertFalse(point.copy(accuracyMeters=1001.0).isValid(1_000_000))
    }
    @Test fun accountDeletionRedactsContactsWithoutLosingTotals() {
        val done=order().copy(status=MarketDeliveryStatus.DELIVERED,events=listOf(MarketDeliveryEvent(MarketDeliveryAction.REQUEST,MarketDeliveryStatus.REQUESTED,1000,"Private door code")))
        val redacted=done.withoutDeletedAccountContacts(id(2))
        assertEquals(MarketDeliveryAddress(),redacted.request.address);assertEquals(done.totalMinor,redacted.totalMinor)
        assertTrue(redacted.isValidDeliveryOrder());assertTrue(redacted.events.all { it.note.isEmpty() })
        assertFalse(redacted.copy(status=MarketDeliveryStatus.REQUESTED).isValidDeliveryOrder())
        assertFailsWith<IllegalArgumentException>{order().withoutDeletedAccountContacts(id(2))}
    }
    @Test fun lostReplyRetriesSameDurableIntentAcrossRestart()=runTest {
        val owner=Owner(id(2));val memory=Memory();val sent=mutableListOf<MarketDeliveryCommand>()
        val first=MarketDeliveryOutbox(memory::get,memory::put,{_,c->sent+=c;ResponseDataModel(null,null,true,503,true)})
        assertEquals(command(),first.submit(owner,command()).pending)
        val restarted=MarketDeliveryOutbox(memory::get,memory::put,{_,c->sent+=c;ok(MarketDeliveryOutcome(c.commandId,order(),true))})
        val result=restarted.submit(owner)
        assertEquals(listOf(command(),command()),sent);assertNotNull(result.acknowledged);assertNull(result.pending)
        assertNull(result.acknowledged.order.handoverCode);assertNull(restarted.cached(owner).pending)
    }
    @Test fun writeFailureNeverStartsNetworkMutation()=runTest {
        val memory=Memory().apply{failWrite=true};var sent=0
        val box=MarketDeliveryOutbox(memory::get,memory::put,{_,_->sent++;ok(MarketDeliveryOutcome(id(1),order()))})
        val result=box.submit(Owner(id(2)),command())
        assertNotNull(result.error);assertNull(result.pending);assertEquals(0,sent)
    }
    @Test fun malformedJournalIsNotOverwrittenWithFreshCommand()=runTest {
        val memory=Memory().apply{values["market-delivery-outbox-v1:${id(2)}"]="broken"};var sent=0
        val box=MarketDeliveryOutbox(memory::get,memory::put,{_,_->sent++;ok(MarketDeliveryOutcome(id(1),order()))})
        assertNotNull(box.submit(Owner(id(2)),command()).error);assertEquals(0,sent);assertEquals("broken",memory.values.values.single())
    }
    @Test fun anotherCommandCannotOverwritePendingOrder()=runTest {
        val memory=Memory();var sent=0
        val box=MarketDeliveryOutbox(memory::get,memory::put,{_,_->sent++;ResponseDataModel(null,null,true,503,true)})
        val owner=Owner(id(2));box.submit(owner,command())
        assertEquals(command(),box.submit(owner,command().copy(commandId=id(8))).pending);assertEquals(1,sent)
    }
    @Test fun wrongAcknowledgementCannotRetireOrChangePending()=runTest {
        val memory=Memory();val owner=Owner(id(2))
        val box=MarketDeliveryOutbox(memory::get,memory::put,{_,_->ok(MarketDeliveryOutcome(id(9),order().copy(buyerId=id(9))))})
        assertEquals(command(),box.submit(owner,command()).pending);assertNotNull(box.cached(owner).pending)
    }
    @Test fun changingAccountDuringRequestNeverPublishesAcknowledgement()=runTest {
        val owner=Owner(id(2));val memory=Memory();val gate=CompletableDeferred<Unit>();val started=CompletableDeferred<Unit>()
        val box=MarketDeliveryOutbox(memory::get,memory::put,{_,_->started.complete(Unit);gate.await();ok(MarketDeliveryOutcome(id(1),order()))})
        val result=async {box.submit(owner,command())};started.await();owner.current=false;gate.complete(Unit)
        assertNull(result.await().acknowledged);assertNull(box.cached(Owner(id(9))).pending)
        assertEquals(command(),box.cached(Owner(id(2))).pending)
    }
    @Test fun acknowledgedWriteFailureKeepsOriginalIntentRecoverable()=runTest {
        val memory=Memory();val owner=Owner(id(2))
        val box=MarketDeliveryOutbox(memory::get,memory::put,{_,_->memory.failWrite=true;ok(MarketDeliveryOutcome(id(1),order()))})
        assertNull(box.submit(owner,command()).acknowledged);memory.failWrite=false
        assertEquals(command(),box.cached(owner).pending)
    }
    @Test fun deliveryCacheCannotCrossAccountsOrKeepLivePositionsAndCodes()=runTest {
        val memory=Memory();val cache=MarketDeliveryPageCache(memory::get,memory::put);val owner=Owner(id(2))
        val page=MarketDeliveryPage(id(2),"buyer",orders=listOf(order().copy(position=MarketCourierPosition(51.0,71.0,20.0,1000,1000))),checkedAtMillis=1000)
        cache.accept(owner,"buyer",null,ok(page))
        assertNull(cache.cached(owner,"buyer",null)!!.orders.single().handoverCode)
        assertNull(cache.cached(owner,"buyer",null)!!.orders.single().position)
        assertNull(cache.cached(Owner(id(9)),"buyer",null))
        cache.accept(owner,"buyer",null,ResponseDataModel(null,null,true,403));assertNull(cache.cached(owner,"buyer",null))
    }
    @Test fun malformedPageCannotCrashLazyListWithDuplicateKeys() {
        val memory=Memory();val cache=MarketDeliveryPageCache(memory::get,memory::put);val owner=Owner(id(2))
        val page=MarketDeliveryPage(id(2),"buyer",orders=listOf(order(),order()),checkedAtMillis=1000)
        assertFalse(cache.valid(page,owner,"buyer",null))
        assertFalse(cache.valid(page.copy(orders=emptyList(),view="bad"),owner,"bad",null))
        assertFalse(cache.valid(page.copy(orders=listOf(order()),nextBefore="bad"),owner,"buyer",null))
    }
    @Test fun detailReadRejectsAnotherOrderOrAccountTransition()=runTest {
        val owner=Owner(id(2))
        assertTrue(readOwnedMarketDeliveryOrder(owner,id(7)){ok(order())}.negative)
        val response=readOwnedMarketDeliveryOrder(owner,id(1)){owner.current=false;ok(order())}
        assertTrue(response.negative);assertNull(response.payload)
    }
}
