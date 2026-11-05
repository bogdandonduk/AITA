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
    private suspend fun savePage(cache: MarketDeliveryPageCache, owner: MarketAccountScope, page: MarketDeliveryPage) {
        val token=assertNotNull(cache.beginRead(owner,page.view,page.storeId,page.filter))
        try { cache.accept(owner,page.view,page.storeId,ok(page),page.filter,token) }
        finally { cache.finishRead(token) }
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
    @Test fun merchantCanCompletePickupOnlyAtTheCounterReadyStage() {
        val pickup=order().copy(request=order().request.copy(mode=MarketDeliveryMode.PICKUP))
        for(status in MarketDeliveryStatus.entries) {
            val current=pickup.copy(status=status)
            assertEquals(status==MarketDeliveryStatus.READY,
                MarketDeliveryAction.DELIVER in current.availableActions(false,true,false),status.name)
        }
        assertFalse(MarketDeliveryAction.DELIVER in order().copy(status=MarketDeliveryStatus.READY).availableActions(false,true,false))
        assertEquals(setOf(MarketDeliveryAttention.READY_TO_HAND_OVER),
            pickup.copy(status=MarketDeliveryStatus.READY).attentionReasons("seller",2000))
        assertTrue(pickup.copy(status=MarketDeliveryStatus.READY).matchesFilter(
            MarketDeliveryFilter(MarketDeliveryQueue.ATTENTION),"seller",2000))
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
    @Test fun acknowledgedCheckoutRemainsVisibleOnlyAsAnOwnedFilteredOfflineFallback() {
        val confirmed=order().copy(position=MarketCourierPosition(51.0,71.0,5.0,1000,1000))
        val result=MarketDeliverySendResult(acknowledged=MarketDeliveryOutcome(id(1),confirmed))
        val filter=MarketDeliveryFilter(MarketDeliveryQueue.ACTIVE)
        fun fallback(account:String=id(2),view:String="buyer",query:MarketDeliveryFilter=filter,
            page:MarketDeliveryPage?=null,fresh:Boolean=false,denied:Boolean=false,before:String?=null)=
            result.confirmedBuyerFallback(account,view,query,page,fresh,denied,before,2000)
        val saved=assertNotNull(fallback())
        assertEquals(id(1),saved.id);assertNull(saved.position);assertNull(saved.handoverCode)
        assertNull(fallback(account=id(9)));assertNull(fallback(view="seller"));assertNull(fallback(view="courier"))
        assertNull(fallback(fresh=true));assertNull(fallback(denied=true));assertNull(fallback(before=id(8)))
        assertNull(fallback(query=filter.copy(search="other")))
        assertNull(fallback(query=filter.copy(queue=MarketDeliveryQueue.HISTORY)))
        assertNull(fallback(page=MarketDeliveryPage(id(2),"buyer",orders=listOf(confirmed),checkedAtMillis=2000)))
        assertNull(MarketDeliverySendResult(pending=command()).confirmedBuyerFallback(id(2),"buyer",filter,null,false,false,null,2000))
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
        savePage(cache,owner,page)
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
    @Test fun operationalAttentionIsRoleAwareAndTerminalHistoryNeverNeedsAction() {
        val request=order()
        assertEquals(setOf(MarketDeliveryAttention.WAITING_CONFIRMATION),request.attentionReasons("seller",2000))
        assertTrue(request.attentionReasons("buyer",2000).isEmpty())
        assertEquals(setOf(MarketDeliveryAttention.WAITING_CONFIRMATION,MarketDeliveryAttention.OVERDUE),request.attentionReasons("seller",3001))
        val ready=request.copy(status=MarketDeliveryStatus.READY,courierId=id(5))
        assertEquals(setOf(MarketDeliveryAttention.NEEDS_COURIER),ready.attentionReasons("seller",2000))
        assertEquals(setOf(MarketDeliveryAttention.ACCEPT_JOB),ready.attentionReasons("courier",2000))
        assertEquals(setOf(MarketDeliveryAttention.READY_TO_DISPATCH),ready.copy(courierAccepted=true).attentionReasons("seller",2000))
        assertEquals(setOf(MarketDeliveryAttention.READY_TO_RECEIVE),ready.copy(request=ready.request.copy(mode=MarketDeliveryMode.PICKUP)).attentionReasons("buyer",2000))
        assertEquals(setOf(MarketDeliveryAttention.READY_TO_HAND_OVER),ready.copy(courierAccepted=true,status=MarketDeliveryStatus.ARRIVED).attentionReasons("courier",2000))
        assertTrue(ready.copy(status=MarketDeliveryStatus.DELIVERED).attentionReasons("seller",4000).isEmpty())
        assertTrue(MarketDeliveryAction.DELAY in request.availableActions(false,true,false))
    }
    @Test fun deliveryFiltersAreLiteralBoundedAndMatchOnlyTheChosenQueue() {
        val item=order().copy(shopName="100% Cotton_Магазин")
        assertTrue(item.matchesFilter(MarketDeliveryFilter(search="  %  "),"buyer",2000))
        assertTrue(item.matchesFilter(MarketDeliveryFilter(search="_магазин"),"buyer",2000))
        assertFalse(item.matchesFilter(MarketDeliveryFilter(search="%not-a-pattern%"),"buyer",2000))
        assertFalse(item.matchesFilter(MarketDeliveryFilter(mode=MarketDeliveryMode.PICKUP),"buyer",2000))
        assertFalse(item.matchesFilter(MarketDeliveryFilter(queue=MarketDeliveryQueue.HISTORY),"buyer",2000))
        assertFalse(item.matchesFilter(MarketDeliveryFilter(queue=MarketDeliveryQueue.ATTENTION),"buyer",2000))
        assertTrue(item.matchesFilter(MarketDeliveryFilter(queue=MarketDeliveryQueue.ATTENTION),"seller",2000))
        assertFalse(MarketDeliveryFilter(search="x".repeat(121)).isValid())
        assertFalse(MarketDeliveryFilter(search="new\nline").isValid())
    }
    @Test fun queryCacheCannotShowHistoryAsActiveOrGrowWithSearchTerms()=runTest {
        val memory=Memory();val cache=MarketDeliveryPageCache(memory::get,memory::put);val owner=Owner(id(2))
        val active=MarketDeliveryFilter(queue=MarketDeliveryQueue.ACTIVE)
        val page=MarketDeliveryPage(id(2),"buyer",orders=listOf(order()),checkedAtMillis=2000,filter=active)
        savePage(cache,owner,page)
        assertEquals(page.orders.size,cache.cached(owner,"buyer",null,active)!!.orders.size)
        assertNull(cache.cached(owner,"buyer",null,MarketDeliveryFilter(queue=MarketDeliveryQueue.HISTORY)))
        assertFalse(cache.valid(page.copy(filter=MarketDeliveryFilter()),owner,"buyer",null,active))
        assertFalse(cache.valid(page.copy(orders=listOf(order().copy(status=MarketDeliveryStatus.CANCELLED))),owner,"buyer",null,active))
        for(search in listOf("Item","Shop",id(1))) {
            val filtered=active.copy(search=search)
            savePage(cache,owner,page.copy(filter=filtered))
        }
        assertEquals(1,memory.values.size)
        assertNull(cache.cached(owner,"buyer",null,active.copy(search="Item")))
        assertNotNull(cache.cached(owner,"buyer",null,active.copy(search=id(1))))
    }
    @Test fun preUpdateAllCacheRemainsUsableAsExplicitlyPartialReadOnlyOfflineSubset()=runTest {
        val memory=Memory();val cache=MarketDeliveryPageCache(memory::get,memory::put);val owner=Owner(id(2))
        val page=MarketDeliveryPage(id(2),"buyer",orders=listOf(order(),order().copy(id=id(8),status=MarketDeliveryStatus.DELIVERED)),
            checkedAtMillis=2000,nextBefore=id(8))
        savePage(cache,owner,page)
        val saved=memory.values.toMap()
        val active=MarketDeliveryFilter(MarketDeliveryQueue.ACTIVE)
        assertNull(cache.cached(owner,"buyer",null,active))
        val subset=assertNotNull(cache.cachedSubset(owner,"buyer",null,active))
        assertEquals(listOf(id(1)),subset.orders.map { it.id });assertNull(subset.nextBefore)
        assertEquals(2000L,subset.checkedAtMillis);assertEquals(active,subset.filter)
        assertTrue(cache.cachedSubset(owner,"buyer",null,active.copy(search="unmatched"))!!.orders.isEmpty())
        assertEquals(saved,memory.values)
        assertNull(cache.cachedSubset(Owner(id(9)),"buyer",null,active))
        cache.accept(owner,"buyer",null,ResponseDataModel(null,null,true,403),active)
        assertNull(cache.cachedSubset(owner,"buyer",null,active))
        owner.current=false;assertNull(cache.cachedSubset(owner,"buyer",null,active))
    }
    @Test fun lateOldQueryCannotOverwriteTheNewerQuerySavedForOfflineUse()=runTest {
        val memory=Memory();val cache=MarketDeliveryPageCache(memory::get,memory::put);val owner=Owner(id(2))
        val active=MarketDeliveryFilter(MarketDeliveryQueue.ACTIVE)
        val history=MarketDeliveryFilter(MarketDeliveryQueue.HISTORY)
        val newer=MarketDeliveryPage(id(2),"buyer",orders=listOf(order()),checkedAtMillis=2000,filter=active)
        val older=newer.copy(filter=history,orders=listOf(order().copy(id=id(8),status=MarketDeliveryStatus.DELIVERED)))
        val started=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
        val delayed=async {
            val token=assertNotNull(cache.beginRead(owner,"buyer",null,history))
            started.complete(Unit);release.await()
            try { cache.accept(owner,"buyer",null,ok(older),history,token) }
            finally { cache.finishRead(token) }
        }
        started.await();savePage(cache,owner,newer)
        release.complete(Unit);delayed.await()
        assertEquals(newer.orders.map { it.id },cache.cached(owner,"buyer",null,active)!!.orders.map { it.id })
        assertNull(cache.cached(owner,"buyer",null,history))
        assertEquals(1,memory.values.size)
    }
    @Test fun transientOrQueryFailureKeepsLatestGoodCacheAndCannotLetAnOlderReadReplaceIt()=runTest {
        val memory=Memory();val cache=MarketDeliveryPageCache(memory::get,memory::put);val owner=Owner(id(2))
        val active=MarketDeliveryFilter(MarketDeliveryQueue.ACTIVE)
        val page=MarketDeliveryPage(id(2),"buyer",orders=listOf(order()),checkedAtMillis=2000,filter=active)
        savePage(cache,owner,page)
        val saved=memory.values.toMap()
        for(status in listOf(400,503)) {
            val old=assertNotNull(cache.beginRead(owner,"buyer",null,active))
            val filter=active.copy(search="Shop")
            val newer=assertNotNull(cache.beginRead(owner,"buyer",null,filter))
            cache.accept(owner,"buyer",null,ResponseDataModel(null,null,true,status),filter,newer)
            cache.finishRead(newer)
            cache.accept(owner,"buyer",null,ok(page.copy(checkedAtMillis=3000)),active,old)
            cache.finishRead(old)
            assertEquals(saved,memory.values)
        }
    }
    @Test fun deniedOldOrPaginatedReadErasesCacheAndCannotBeUndoneByAnAlreadyRunningSuccess()=runTest {
        val memory=Memory();val cache=MarketDeliveryPageCache(memory::get,memory::put);val owner=Owner(id(2))
        val page=MarketDeliveryPage(id(2),"buyer",orders=listOf(order()),checkedAtMillis=2000)
        for(status in listOf(401,403)) {
            savePage(cache,owner,page)
            val old=assertNotNull(cache.beginRead(owner,"buyer",null))
            val newer=assertNotNull(cache.beginRead(owner,"buyer",null))
            // Exercise both a stale first-page denial and pagination without a cache token.
            cache.accept(owner,"buyer",null,ResponseDataModel(null,null,true,status),token=old.takeIf { status==401 })
            cache.accept(owner,"buyer",null,ok(page),token=old);cache.finishRead(old)
            cache.accept(owner,"buyer",null,ok(page),token=newer);cache.finishRead(newer)
            assertNull(cache.cached(owner,"buyer",null))
            savePage(cache,owner,page)
            assertNotNull(cache.cached(owner,"buyer",null))
        }
    }
    @Test fun cacheReadTokensAreBoundedAndFinishingAnOldReadCannotRetireItsReplacement()=runTest {
        val memory=Memory();val cache=MarketDeliveryPageCache(memory::get,memory::put,maximumInFlightSlots=1)
        val first=Owner(id(2));val second=Owner(id(9))
        val firstPage=MarketDeliveryPage(id(2),"buyer",orders=listOf(order()),checkedAtMillis=2000)
        val secondPage=firstPage.copy(accountId=id(9),orders=listOf(order().copy(buyerId=id(9))))
        val evicted=assertNotNull(cache.beginRead(first,"buyer",null))
        val other=assertNotNull(cache.beginRead(second,"buyer",null))
        cache.accept(first,"buyer",null,ok(firstPage),token=evicted);cache.finishRead(evicted)
        assertNull(cache.cached(first,"buyer",null))
        cache.accept(second,"buyer",null,ok(secondPage),token=other);cache.finishRead(other)
        assertNotNull(cache.cached(second,"buyer",null))
        val old=assertNotNull(cache.beginRead(first,"buyer",null))
        val replacement=assertNotNull(cache.beginRead(first,"buyer",null))
        cache.finishRead(old)
        cache.accept(first,"buyer",null,ok(firstPage),token=replacement);cache.finishRead(replacement)
        assertNotNull(cache.cached(first,"buyer",null))
        val completed=assertNotNull(cache.beginRead(second,"buyer",null))
        cache.finishRead(completed)
        cache.accept(second,"buyer",null,ok(secondPage.copy(checkedAtMillis=3000)),token=completed)
        assertEquals(2000L,cache.cached(second,"buyer",null)!!.checkedAtMillis)
    }
}
