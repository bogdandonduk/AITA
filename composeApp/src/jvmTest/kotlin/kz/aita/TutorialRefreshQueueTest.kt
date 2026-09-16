package kz.aita

import kotlinx.coroutines.*
import kotlin.test.*

class TutorialRefreshQueueTest {
    @Test fun invalidationDuringReadAlwaysGetsOneTrailingForcedRead()=runBlocking {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        try {
            val gate=CompletableDeferred<Unit>();val calls=mutableListOf<Boolean>()
            val queue=TutorialRefreshQueue(scope,{force->calls.add(force);if(calls.size==1)gate.await()},{throw it})
            queue.request();assertEquals(listOf(false),calls)
            repeat(100){queue.request(force=true)}
            queue.request(force=false) // Opening another screen cannot downgrade a pending invalidation.
            assertEquals(1,calls.size)
            gate.complete(Unit);yield()
            assertEquals(listOf(false,true),calls)
        } finally {scope.cancel()}
    }
    @Test fun failureDoesNotPermanentlyKillAutomaticRefresh()=runBlocking {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        try {
            var reads=0;var errors=0
            val queue=TutorialRefreshQueue(scope,{if(++reads==1)error("fixture failure")},{errors++})
            queue.request(force=true);queue.request(force=true);yield()
            assertEquals(2,reads);assertEquals(1,errors)
        } finally {scope.cancel()}
    }
    @Test fun canceledScopeDoesNotContinueToRead()=runBlocking {
        val job=SupervisorJob();val scope=CoroutineScope(job+Dispatchers.Unconfined);var reads=0
        val queue=TutorialRefreshQueue(scope,{reads++},{throw it})
        queue.request();job.cancel();queue.request(force=true);yield();assertEquals(1,reads)
    }
}
