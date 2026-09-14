package kz.aita

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
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

    @Test fun onlyHttp200CanPublishAPlausibleBasket() = runBlocking {
        for (status in listOf(null, 0, 201, 202, 204, 304, 401, 409, 500, 503)) {
            val reply = readOwnedMarketBasket(Owner(), result.request) { response().copy(httpStatusCode = status) }
            assertTrue(reply.negative, "HTTP $status")
            assertNull(reply.payload)
            assertEquals(502, reply.httpStatusCode)
        }
    }
    @Test fun transportFailureCannotPublishOrPretendTheEndpointIsMissing() = runBlocking {
        for (status in listOf(200, 404, 500)) {
            val reply = readOwnedMarketBasket(Owner(), result.request) {
                response().copy(httpStatusCode = status, transportFailure = true)
            }
            assertTrue(reply.negative); assertNull(reply.payload); assertTrue(reply.transportFailure)
            assertEquals(eventMessage("market.basket_refresh"), reply.message)
        }
    }
    @Test fun cancelledCallerDoesNotEvenStartANonSuspendingAdapter() = runBlocking {
        var calls = 0
        val child = launch {
            currentCoroutineContext().cancel()
            readOwnedMarketBasket(Owner(), result.request) { calls++; response() }
        }
        child.join()
        assertTrue(child.isCancelled); assertEquals(0, calls)
    }
    @Test fun adapterWhichReturnsAfterCancellationCannotPublish() = runBlocking {
        var published = false
        val child = launch {
            readOwnedMarketBasket(Owner(), result.request) {
                currentCoroutineContext().cancel()
                response()
            }
            published = true
        }
        child.join()
        assertTrue(child.isCancelled); assertFalse(published)
    }
    @Test fun missingSuccessPayloadAndUnexplainedErrorHaveUsefulFeedback() = runBlocking {
        val missing = readOwnedMarketBasket(Owner(), result.request) { response().copy(payload = null) }
        val error = readOwnedMarketBasket(Owner(), result.request) { response().copy(negative = true, httpStatusCode = 503) }
        assertNull(missing.payload); assertTrue(missing.negative); assertNotNull(missing.message)
        assertNull(error.payload); assertTrue(error.negative); assertNotNull(error.message)
        assertEquals(503, error.httpStatusCode)
    }
}
