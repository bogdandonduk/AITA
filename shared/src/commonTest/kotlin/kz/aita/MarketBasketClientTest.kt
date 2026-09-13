package kz.aita

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class MarketBasketClientTest {
    private class Owner(override val accountId:String=BasketTestData.account):MarketAccountScope {
        override val generation=1L
        var current=true
        override fun isCurrent()=current
    }
    private val result=BasketTestData.plan(BasketTestData.snapshot(BasketTestData.row(1)))
    private fun response(value:MarketBasketResult=result)=ResponseDataModel(null,value,false,200)
    @Test fun validOwnedReadPreservesAllPlanFields()=runBlocking {
        assertEquals(result,readOwnedMarketBasket(Owner(),result.request){response()}.payload)
    }
    @Test fun expiredOwnerDoesNotStartANetworkRead()=runBlocking {
        var calls=0; val owner=Owner().apply {current=false}
        val reply=readOwnedMarketBasket(owner,result.request){calls++;response()}
        assertEquals(0,calls);assertTrue(reply.negative);assertNull(reply.payload)
    }
    @Test fun logoutDuringReadDiscardsEvenASuccessfulResult()=runBlocking {
        val owner=Owner();val started=CompletableDeferred<Unit>();val complete=CompletableDeferred<Unit>()
        val job=async { readOwnedMarketBasket(owner,result.request){started.complete(Unit);complete.await();response()} }
        started.await();owner.current=false;complete.complete(Unit)
        assertTrue(job.await().negative);assertNull(job.await().payload)
    }
    @Test fun differentAccountSnapshotCannotBePublished()=runBlocking {
        val reply=readOwnedMarketBasket(Owner(),result.request){response(result.copy(snapshot=result.snapshot.copy(userId="someone-else")))}
        assertTrue(reply.negative);assertNull(reply.payload);assertEquals(502,reply.httpStatusCode)
    }
    @Test fun invalidRequestsNeverReachTransport()=runBlocking {
        var calls=0
        val reply=readOwnedMarketBasket(Owner(),MarketBasketRequest(-1)){calls++;response()}
        assertEquals(0,calls);assertEquals(400,reply.httpStatusCode)
    }
    @Test fun olderBackendDoesNotFallBackToAnUnfilteredOrMutatingRoute()=runBlocking {
        var calls=0
        val reply=readOwnedMarketBasket(Owner(),result.request){calls++;ResponseDataModel(null,null,true,404)}
        assertEquals(1,calls);assertTrue(reply.negative);assertNull(reply.payload)
    }
    @Test fun negativeResponseCannotSmuggleAPayloadIntoTheView()=runBlocking {
        val reply=readOwnedMarketBasket(Owner(),result.request){response().copy(negative=true,httpStatusCode=429)}
        assertTrue(reply.negative);assertNull(reply.payload)
    }
    @Test fun coroutineCancellationIsNotTurnedIntoAVisibleFailure()=runBlocking {
        assertFailsWith<CancellationException>{ readOwnedMarketBasket(Owner(),result.request){throw CancellationException("cancel")} }
    }
}
