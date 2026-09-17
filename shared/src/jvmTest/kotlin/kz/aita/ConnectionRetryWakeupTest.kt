package kz.aita

import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

class ConnectionRetryWakeupTest {
    @Test fun availabilityInterruptsSixtySecondWait() = runBlocking<Unit> {
        val wake = ConnectionRetryWakeup()
        val waiting = async(start = CoroutineStart.UNDISPATCHED) { wake.await(wake.revision, 60_000L) }
        assertFalse(waiting.isCompleted)
        wake.request()
        assertTrue(withTimeout(1_000L) { waiting.await() })
    }

    @Test fun signalDuringFailedHandshakeIsNotLostBeforeBackoffStarts() = runBlocking<Unit> {
        val wake = ConnectionRetryWakeup()
        val beforeRequest = wake.revision
        wake.request() // onAvailable while a previous HTTP/WS attempt was still in progress
        assertTrue(withTimeout(1_000L) { wake.await(beforeRequest, 60_000L) })
        assertFalse(wake.await(wake.revision, 15L))
    }

    @Test fun secondNetworkEventIsNotDroppedAfterAnImmediateFirstRetry() = runBlocking<Unit> {
        val wake = ConnectionRetryWakeup()
        val old = wake.revision
        wake.request()
        assertTrue(wake.await(old, 60_000L))
        val next = wake.revision
        wake.request() // validated capabilities often follow onAvailable within milliseconds
        assertTrue(withTimeout(1_000L) { wake.await(next, 60_000L) })
    }

    @Test fun burstDoesNotQueueOneRetryPerCallback() = runBlocking<Unit> {
        val wake = ConnectionRetryWakeup()
        val before = wake.revision
        coroutineScope { repeat(100) { launch(Dispatchers.Default) { wake.request() } } }
        assertTrue(wake.await(before, 60_000L))
        assertFalse(wake.await(wake.revision, 15L))
    }

    @Test fun ordinaryTimerAndZeroWaitStillWorkWithoutSignals() = runBlocking<Unit> {
        val wake = ConnectionRetryWakeup()
        assertFalse(wake.await(wake.revision, 15L))
        assertFalse(wake.await(wake.revision, 0L))
    }

    @Test fun cancelDoesNotBecomeATimerExpiryOrARecoverySignal() = runBlocking<Unit> {
        val wake = ConnectionRetryWakeup()
        val waiting = async(start = CoroutineStart.UNDISPATCHED) { wake.await(wake.revision, 60_000L) }
        waiting.cancel()
        assertFailsWith<CancellationException> { waiting.await() }
        val cancelled = Job().also { it.cancel() }
        wake.request()
        assertFailsWith<CancellationException> {
            withContext(cancelled) { wake.await(0L, 60_000L) }
        }
    }

    @Test fun wakingRetryDoesNotCancelItsOwnedHandshake() = runBlocking<Unit> {
        val wake = ConnectionRetryWakeup()
        val slot = OwnedConnectionJob()
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val starts = AtomicInteger()
        slot.startIfIdle(this, Dispatchers.Unconfined) {
            starts.incrementAndGet(); entered.complete(Unit); finish.await()
        }
        entered.await()
        val original = slot.state.value.job
        repeat(50) { wake.request() }
        assertSame(original, slot.state.value.job)
        assertTrue(slot.isRunning)
        assertFalse(slot.startIfIdle(this, Dispatchers.Unconfined) { starts.incrementAndGet() })
        assertEquals(1, starts.get())
        finish.complete(Unit)
        original?.join()
    }

    @Test fun outageProbesSettleAtTenSecondsInsteadOfThirtyOrSixty() {
        assertEquals(listOf(2_000L, 4_000L, 7_000L, 10_000L, 10_000L), (0..4).map(::cloudUnavailableRetryDelayMillis))
        assertEquals(2_000L, cloudUnavailableRetryDelayMillis(-1))
        assertEquals(10_000L, cloudUnavailableRetryDelayMillis(Int.MAX_VALUE))
    }

    @Test fun readyHttpBoundsSocketBackoffAndSustainedFailureDoesNotSpin() {
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 5_000L, 5_000L),
            listOf(1_000L, 2_000L, 4_000L, 8_000L, 30_000L).map { realtimeRetryDelayMillis(it, true) })
        assertEquals(10_000L, realtimeRetryDelayMillis(30_000L, false))
        assertEquals(2_000L, realtimeRetryDelayMillis(0L, false))
    }

    @Test fun frequentRecoveryChecksDoNotRepeatedlyRefreshInventory() {
        var now = 0L
        val gate = ConnectionReconciliationGate({ now })
        assertTrue(gate.claim(1L))
        repeat(29) { now += 1_000L; assertFalse(gate.claim(1L)) }
        now = 30_000L
        assertTrue(gate.claim(1L))
        assertFalse(gate.claim(1L))
    }

    @Test fun replacementAccountSessionGetsItsOwnImmediateReconciliation() {
        var now = 100L
        val gate = ConnectionReconciliationGate({ now })
        assertTrue(gate.claim(1L))
        assertTrue(gate.claim(2L))
        assertTrue(gate.claim(3L)) // A -> B -> A is a new generation
        assertFalse(gate.claim(3L))
        now = 90L // Clock rollback must not suppress the operation indefinitely.
        assertTrue(gate.claim(3L))
    }

    @Test fun concurrentRecoveryCompletionsShareOneReconciliationClaim() = runBlocking<Unit> {
        val gate = ConnectionReconciliationGate({ 100L })
        val accepted = AtomicInteger()
        coroutineScope { repeat(100) { launch(Dispatchers.Default) { if (gate.claim(1L)) accepted.incrementAndGet() } } }
        assertEquals(1, accepted.get())
    }
}
