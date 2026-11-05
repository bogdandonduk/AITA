package kz.aita

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class MarketOfferDetailCacheTest {
    private fun id(n: Int) = "00000000-0000-0000-0000-${n.toString().padStart(12,'0')}"
    private class Owner(override val accountId: String, var current: Boolean = true) : MarketAccountScope {
        override val generation = 4L
        override fun isCurrent() = current
    }
    private fun detail(n: Int = 1, title: String = "Tea", description: String = "", account: String = id(90)) =
        MarketOfferDetailResult(account, MarketOffer(id(n),
            MarketStorefront(id(20), "Shop", "Astana", "Public pickup door", published = true, revision = 1),
            title, description, priceMinor = 12345, currencyCode = "KZT", pricedAmount = 1.0, unitId = "piece",
            availability = MARKET_AVAILABILITY_RECORDED, checkedAtMillis = 100, sourceUpdatedAtMillis = 90))
    private fun ok(value: MarketOfferDetailResult = detail()) = ResponseDataModel(null,value,false,200)
    private fun failed(status: Int, key: String = "market.detail_failed", transport: Boolean = false) =
        ResponseDataModel<MarketOfferDetailResult>(eventMessage(key),null,true,status,transport)
    private class Memory {
        val values = mutableMapOf<String,String?>()
        var failWrite = false
        suspend fun read(key: String) = values[key]
        suspend fun write(key: String, value: String?) { check(!failWrite); values[key] = value }
    }
    @Test fun validatedDetailsSurviveRestartButStayBoundToTheirAccountAndOffer() = runTest {
        val memory=Memory();val owner=Owner(id(90));val cache=MarketOfferDetailCache(memory::read,memory::write)
        assertFalse(cache.refresh(owner,id(1)){ok()}.negative)
        assertEquals(detail(),MarketOfferDetailCache(memory::read,memory::write).cached(owner,id(1)))
        assertNull(cache.cached(Owner(id(91)),id(1)));assertNull(cache.cached(owner,id(2)))
        assertNull(cache.cached(owner,"../../private"))
        owner.current=false;assertNull(cache.cached(owner,id(1)))
    }
    @Test fun recentValidRefreshesEvictOldDetailsWithoutUnboundedPerOfferKeys() = runTest {
        val memory=Memory();val owner=Owner(id(90));val cache=MarketOfferDetailCache(memory::read,memory::write,maximumEntries=2)
        for(n in 1..3) cache.refresh(owner,id(n)){ok(detail(n))}
        assertNull(cache.cached(owner,id(1)));assertNotNull(cache.cached(owner,id(2)));assertNotNull(cache.cached(owner,id(3)))
        cache.refresh(owner,id(2)){ok(detail(2,title="Updated"))}
        cache.refresh(owner,id(4)){ok(detail(4))}
        assertNull(cache.cached(owner,id(3)));assertEquals("Updated",cache.cached(owner,id(2))?.offer?.title)
        assertEquals(1,memory.values.size)
    }
    @Test fun utf8BytesAreBoundedAndAnOversizedLiveDetailCannotDestroyTheSavedSet() = runTest {
        val owner=Owner(id(90));val probe=Memory();val large=detail(description="я".repeat(1000))
        MarketOfferDetailCache(probe::read,probe::write).refresh(owner,id(1)){ok(large)}
        val oneSize=requireNotNull(probe.values.values.single()).encodeToByteArray().size
        val memory=Memory();val cache=MarketOfferDetailCache(memory::read,memory::write,maximumBytes=oneSize+20)
        cache.refresh(owner,id(1)){ok(large)}
        cache.refresh(owner,id(2)){ok(large.copy(offer=large.offer.copy(id=id(2))))}
        assertNull(cache.cached(owner,id(1)));assertNotNull(cache.cached(owner,id(2)))
        assertTrue(requireNotNull(memory.values.values.single()).encodeToByteArray().size<=oneSize+20)
        val before=memory.values.toMap()
        val response=cache.refresh(owner,id(3)){ok(detail(3,description="я".repeat(2000)))}
        assertFalse(response.negative);assertNotNull(response.payload)
        assertEquals("market.detail_cache_warning",response.message?.eventMessageReferenceOrNull()?.key)
        assertEquals(before,memory.values)
    }
    @Test fun transportAndOldEndpointFailuresPreserveFactsButWithdrawalAndDenialEraseThem() = runTest {
        val memory=Memory();val owner=Owner(id(90));val cache=MarketOfferDetailCache(memory::read,memory::write)
        cache.refresh(owner,id(1)){ok()}
        for(reply in listOf(failed(503),failed(404,transport=true),failed(404))) {
            cache.refresh(owner,id(1)){reply};assertNotNull(cache.cached(owner,id(1)))
        }
        for(reply in listOf(failed(404,"market.unavailable"),failed(401),failed(403))) {
            cache.refresh(owner,id(1)){ok()}
            cache.refresh(owner,id(1)){reply};assertNull(cache.cached(owner,id(1)))
        }
    }
    @Test fun lateOldSuccessCannotOverwriteTheNewerRequestedDetail() = runTest {
        val memory=Memory();val owner=Owner(id(90));val cache=MarketOfferDetailCache(memory::read,memory::write)
        val release=CompletableDeferred<Unit>()
        val old=async(start=CoroutineStart.UNDISPATCHED){cache.refresh(owner,id(1)){release.await();ok(detail(title="Old"))}}
        cache.refresh(owner,id(1)){ok(detail(title="New"))}
        release.complete(Unit);old.await()
        assertEquals("New",cache.cached(owner,id(1))?.offer?.title)
    }
    @Test fun anOlderDenialRetiresEveryAlreadyRunningReadForThatOffer() = runTest {
        val memory=Memory();val owner=Owner(id(90));val cache=MarketOfferDetailCache(memory::read,memory::write)
        cache.refresh(owner,id(1)){ok()};cache.refresh(owner,id(2)){ok(detail(2))}
        val deniedGate=CompletableDeferred<Unit>();val successGate=CompletableDeferred<Unit>()
        val denied=async(start=CoroutineStart.UNDISPATCHED){cache.refresh(owner,id(1)){deniedGate.await();failed(403)}}
        val success=async(start=CoroutineStart.UNDISPATCHED){cache.refresh(owner,id(1)){successGate.await();ok()}}
        deniedGate.complete(Unit);denied.await();successGate.complete(Unit);success.await()
        assertNull(cache.cached(owner,id(1)));assertNotNull(cache.cached(owner,id(2)))
        cache.refresh(owner,id(1)){ok()};assertNotNull(cache.cached(owner,id(1)))
    }
    @Test fun accountChangeWhileNetworkIsSuspendedCannotPersistOrExposeItsReply() = runTest {
        val memory=Memory();val owner=Owner(id(90));val cache=MarketOfferDetailCache(memory::read,memory::write)
        val release=CompletableDeferred<Unit>()
        val pending=async(start=CoroutineStart.UNDISPATCHED){cache.refresh(owner,id(1)){release.await();ok()}}
        owner.current=false;release.complete(Unit)
        assertTrue(pending.await().negative);assertTrue(memory.values.isEmpty())
    }
    @Test fun corruptOrWrongOwnerSnapshotsDoNotLeakAndOnlyVerifiedReadsReplaceThem() = runTest {
        val memory=Memory();val owner=Owner(id(90));val cache=MarketOfferDetailCache(memory::read,memory::write)
        cache.refresh(owner,id(1)){ok()}
        val key=memory.values.keys.single()
        val original=requireNotNull(memory.values[key])
        for(raw in listOf("broken",original.replace(id(90),id(91)),original.replace("\"schema\":1","\"schema\":2"))) {
            memory.values[key]=raw
            assertNull(cache.cached(owner,id(1)))
            val invalid=cache.refresh(owner,id(1)){ok(detail(account=id(91)))}
            assertTrue(invalid.negative);assertEquals(raw,memory.values[key])
        }
        cache.refresh(owner,id(1)){ok()};assertNotNull(cache.cached(owner,id(1)))
    }
    @Test fun clearingAccountInvalidatesInFlightReadsWithoutClearingAnotherAccount() = runTest {
        val memory=Memory();val owner=Owner(id(90));val other=Owner(id(91));val cache=MarketOfferDetailCache(memory::read,memory::write)
        cache.refresh(other,id(1)){ok(detail(account=id(91)))}
        val release=CompletableDeferred<Unit>()
        val pending=async(start=CoroutineStart.UNDISPATCHED){cache.refresh(owner,id(1)){release.await();ok()}}
        cache.clear(owner.accountId);release.complete(Unit);pending.await()
        assertNull(cache.cached(owner,id(1)));assertNotNull(cache.cached(other,id(1)))
    }
    @Test fun failedCacheWriteKeepsValidNetworkDetailsAndCancellationStillPropagates() = runTest {
        val memory=Memory().apply{failWrite=true};val owner=Owner(id(90));val cache=MarketOfferDetailCache(memory::read,memory::write)
        val response=cache.refresh(owner,id(1)){ok()}
        assertFalse(response.negative);assertEquals(detail(),response.payload)
        assertEquals("market.detail_cache_warning",response.message?.eventMessageReferenceOrNull()?.key)
        assertFailsWith<CancellationException>{cache.refresh(owner,id(1)){throw CancellationException()}}
        val cancelledRead=MarketOfferDetailCache({throw CancellationException()},{_,_->})
        assertFailsWith<CancellationException>{cancelledRead.cached(owner,id(1))}
    }
    @Test fun failedWithdrawalDeletionCannotResurrectDiskDataAndRequiresFreshSuccessfulSave() = runTest {
        val memory=Memory();val owner=Owner(id(90));val cache=MarketOfferDetailCache(memory::read,memory::write)
        cache.refresh(owner,id(1)){ok()};cache.refresh(owner,id(2)){ok(detail(2))}
        memory.failWrite=true
        assertEquals(404,cache.refresh(owner,id(1)){failed(404,"market.unavailable")}.httpStatusCode)
        memory.failWrite=false
        assertTrue(memory.values.values.any { it != null }) // The failed erase left the old disk snapshot.
        assertNull(cache.cached(owner,id(1)))
        cache.refresh(owner,id(2)){ok(detail(2))}
        assertNotNull(cache.cached(owner,id(2)));assertNull(cache.cached(owner,id(1)))
        memory.failWrite=true;cache.refresh(owner,id(1)){ok()};assertNull(cache.cached(owner,id(1)))
        memory.failWrite=false;cache.refresh(owner,id(1)){ok()};assertNotNull(cache.cached(owner,id(1)))
    }
    @Test fun cancellationWhileDeletingDenialAlsoBlocksTheRemainingDiskSnapshot() = runTest {
        val memory=Memory();val owner=Owner(id(90));var cancelDelete=false
        val cache=MarketOfferDetailCache(memory::read,{key,value->
            if(cancelDelete) throw CancellationException("Interrupted erase")
            memory.write(key,value)
        })
        cache.refresh(owner,id(1)){ok()};cancelDelete=true
        assertFailsWith<CancellationException>{cache.refresh(owner,id(1)){failed(403)}}
        cancelDelete=false;assertNull(cache.cached(owner,id(1)))
        cache.refresh(owner,id(1)){ok()};assertNotNull(cache.cached(owner,id(1)))
        cancelDelete=true;assertFailsWith<CancellationException>{cache.clear(owner.accountId)}
        cancelDelete=false;assertNull(cache.cached(owner,id(1)))
    }
    @Test fun failedEraseRecoveryAllowlistIsBoundedAndNeverGrantsOtherOffersAccess() = runTest {
        val memory=Memory();val owner=Owner(id(90));val cache=MarketOfferDetailCache(memory::read,memory::write,maximumReads=1)
        cache.refresh(owner,id(1)){ok()};memory.failWrite=true
        cache.refresh(owner,id(1)){failed(403)};memory.failWrite=false
        cache.refresh(owner,id(2)){ok(detail(2))};assertNotNull(cache.cached(owner,id(2)))
        cache.refresh(owner,id(3)){ok(detail(3))};assertNotNull(cache.cached(owner,id(3)))
        assertNull(cache.cached(owner,id(2)));assertNull(cache.cached(owner,id(1)))
    }
    @Test fun evictedInFlightTokensCannotGrowTheSavedSetAfterTheirCapacityIsExceeded() = runTest {
        val memory=Memory();val owner=Owner(id(90));val cache=MarketOfferDetailCache(memory::read,memory::write,maximumReads=1)
        val release=CompletableDeferred<Unit>()
        val pending=async(start=CoroutineStart.UNDISPATCHED){cache.refresh(owner,id(1)){release.await();ok()}}
        cache.refresh(owner,id(2)){ok(detail(2))};release.complete(Unit);pending.await()
        assertNull(cache.cached(owner,id(1)));assertNotNull(cache.cached(owner,id(2)))
    }
}
