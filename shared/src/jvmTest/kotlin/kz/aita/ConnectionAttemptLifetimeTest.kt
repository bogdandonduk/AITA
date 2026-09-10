package kz.aita

import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

class ConnectionAttemptLifetimeTest {
    @Test fun recentReconnectSchedulesTrailingCatchupInsteadOfLosingIt() {
        assertEquals(20_000L, realtimeCatchupDelayMillis(20_000L, 10_000L, 30_000L))
        assertEquals(0L, realtimeCatchupDelayMillis(40_000L, 10_000L, 30_000L))
    }
    @Test fun firstConnectionAndClockRollbackDoNotDelayCatchup() {
        assertEquals(0L, realtimeCatchupDelayMillis(1_000L, 0L, 30_000L))
        assertEquals(0L, realtimeCatchupDelayMillis(500L, 1_000L, 30_000L))
    }

    @Test fun requestLocalCancellationDoesNotKillReconnectOwner() = runBlocking {
        val calls = AtomicInteger()
        repeat(3) {
            try { throw CancellationException("HTTP channel closed") }
            catch (failure: Throwable) { ensureConnectionOwnerActive(failure); calls.incrementAndGet() }
        }
        assertEquals(3, calls.get()); assertTrue(currentCoroutineContext().isActive)
    }

    @Test fun logoutCancellationStillPropagatesImmediately() = runBlocking {
        var continued = false
        val entered = CompletableDeferred<Unit>()
        val job = launch {
            try { entered.complete(Unit); awaitCancellation() }
            catch (failure: Throwable) { ensureConnectionOwnerActive(failure); continued = true }
        }
        entered.await(); job.cancelAndJoin(); assertFalse(continued)
    }

    @Test fun fatalErrorsAreNotDisguisedAsNetworkRetries() = runBlocking {
        val failure = AssertionError("test fatal error")
        assertSame(failure, assertFailsWith<AssertionError> { ensureConnectionOwnerActive(failure) })
    }

    @Test fun handshakeTimeoutCancelsTheOwnedRequest() = runBlocking {
        var cancelled = false
        assertFailsWith<RealtimeHandshakeTimeoutException> {
            withRealtimeHandshakeDeadline(30) {
                try { awaitCancellation() } finally { cancelled = true }
            }
        }
        assertTrue(cancelled); assertTrue(currentCoroutineContext().isActive)
    }

    @Test fun successfulHelloStopsDeadlineButNotLiveConnection() = runBlocking {
        val result = withRealtimeHandshakeDeadline(30) { hello ->
            hello(); delay(100); "still connected"
        }
        assertEquals("still connected", result)
    }

    @Test fun completingAnAttemptDoesNotLeaveAnOrphanWatchdog() = runBlocking {
        repeat(4) { assertEquals(it, withRealtimeHandshakeDeadline(15) { _ -> it }) }
        delay(40); assertTrue(currentCoroutineContext().isActive)
        assertTrue(coroutineContext[Job]!!.children.none())
    }

    @Test fun exceptionBeforeHelloReleasesDeadlineAndAllowsNextAttempt() = runBlocking {
        assertFailsWith<IllegalStateException> {
            withRealtimeHandshakeDeadline(20) { _ -> error("failed upgrade") }
        }
        assertEquals(7, withRealtimeHandshakeDeadline(30) { done -> done(); delay(60); 7 })
    }

    @Test fun ownerCancellationReleasesPendingUpgrade() = runBlocking {
        val entered = CompletableDeferred<Unit>(); var cleaned = false
        val job = launch {
            withRealtimeHandshakeDeadline(5_000) {
                try { entered.complete(Unit); awaitCancellation() } finally { cleaned = true }
            }
        }
        entered.await(); withTimeout(1_000) { job.cancelAndJoin() }
        assertTrue(cleaned)
    }

    @Test fun requestTimeoutScopeDoesNotBecomeOwnerTimeout() = runBlocking {
        var retries = 0
        repeat(3) {
            try { withTimeout(10) { awaitCancellation() } }
            catch (failure: Throwable) { ensureConnectionOwnerActive(failure); retries++ }
        }
        assertEquals(3, retries); assertTrue(currentCoroutineContext().isActive)
    }

    @Test fun cancelledOldConnectionCannotClearReplacement() = runBlocking {
        val owner = OwnedConnectionJob()
        val oldReady = CompletableDeferred<Unit>(); val finishOldCleanup = CompletableDeferred<Unit>()
        owner.startIfIdle(this, Dispatchers.Default) {
            val me = coroutineContext[Job]
            try { owner.setConnected(me, true); oldReady.complete(Unit); awaitCancellation() }
            finally { withContext(NonCancellable) { finishOldCleanup.await() }; owner.clear(me) }
        }
        oldReady.await(); val old = owner.cancel()!!
        val newReady = CompletableDeferred<Unit>()
        assertTrue(owner.startIfIdle(this, Dispatchers.Default) {
            val me = coroutineContext[Job]
            try { owner.setConnected(me, true); newReady.complete(Unit); awaitCancellation() }
            finally { owner.clear(me) }
        })
        newReady.await(); finishOldCleanup.complete(Unit); old.join()
        assertTrue(owner.isConnected); owner.cancel()!!.join()
    }
}
