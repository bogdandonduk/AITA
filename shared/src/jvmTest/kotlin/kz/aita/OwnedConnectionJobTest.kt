package kz.aita

import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext
import kotlin.test.*
import kotlinx.coroutines.*

class OwnedConnectionJobTest {
    private fun fixture(block: suspend CoroutineScope.() -> Unit) = runBlocking {
        val owner = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try { withTimeout(5_000L) { owner.block() } } finally { owner.cancel() }
    }

    @Test fun immediateDispatcherSeesRegisteredOwner() = fixture {
        val slot = OwnedConnectionJob()
        var ran = false
        assertTrue(slot.startIfIdle(this, Dispatchers.Unconfined) {
            assertTrue(slot.owns(coroutineContext[Job]))
            assertTrue(slot.isRunning)
            ran = true
        })
        assertTrue(ran)
        assertFalse(slot.isRunning)
        assertNull(slot.state.value.job)
    }

    @Test fun synchronousCompletionNeverLeavesAnUnstartableGhostJob() = fixture {
        val slot = OwnedConnectionJob()
        var calls = 0
        repeat(100) { assertTrue(slot.startIfIdle(this, Dispatchers.Unconfined) { calls++ }) }
        assertEquals(100, calls)
        assertFalse(slot.isRunning)
    }

    @Test fun concurrentCallersStartOnlyOneLoop() = fixture {
        val slot = OwnedConnectionJob()
        val gate = CompletableDeferred<Unit>()
        val accepted = AtomicInteger()
        val entered = AtomicInteger()
        val owner = this
        coroutineScope {
            repeat(100) {
                launch(Dispatchers.Default) {
                    if (slot.startIfIdle(owner, Dispatchers.Default) { entered.incrementAndGet(); gate.await() })
                        accepted.incrementAndGet()
                }
            }
        }
        val job = assertNotNull(slot.state.value.job)
        gate.complete(Unit)
        job.join()
        assertEquals(1, accepted.get())
        assertEquals(1, entered.get())
        assertFalse(slot.isRunning)
    }

    @Test fun cancellingOldSocketCannotClearReplacementDuringLateCleanup() = fixture {
        val slot = OwnedConnectionJob()
        val finishOld = CompletableDeferred<Unit>()
        val finishNew = CompletableDeferred<Unit>()
        slot.startIfIdle(this, Dispatchers.Unconfined) {
            try { awaitCancellation() }
            finally { withContext(NonCancellable) { finishOld.await() } }
        }
        val old = assertNotNull(slot.state.value.job)
        assertTrue(slot.setConnected(old, true))
        assertSame(old, slot.cancel())
        assertFalse(slot.isConnected)
        assertTrue(slot.startIfIdle(this, Dispatchers.Unconfined) { finishNew.await() })
        val current = assertNotNull(slot.state.value.job)
        assertTrue(slot.setConnected(current, true))
        assertFalse(slot.setConnected(old, false))
        slot.clear(old)
        finishOld.complete(Unit)
        old.join()
        assertSame(current, slot.state.value.job)
        assertTrue(slot.isConnected)
        finishNew.complete(Unit)
        current.join()
        assertFalse(slot.isConnected)
    }

    @Test fun pendingDispatcherJobAlreadyOwnsSlotAndCancellationPreventsItsBody() = fixture {
        val pending = ArrayDeque<Runnable>()
        val dispatcher = object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) { pending.addLast(block) }
        }
        val slot = OwnedConnectionJob()
        var calls = 0
        assertTrue(slot.startIfIdle(this, dispatcher) { calls++ })
        assertTrue(slot.isRunning)
        assertFalse(slot.startIfIdle(this, dispatcher) { calls++ })
        slot.cancel()
        while (pending.isNotEmpty()) pending.removeFirst().run()
        assertEquals(0, calls)
        assertFalse(slot.isRunning)
        assertTrue(slot.startIfIdle(this, Dispatchers.Unconfined) { calls++ })
        assertEquals(1, calls)
    }

    @Test fun cancelledParentDoesNotLeaveARegisteredLoop() = fixture {
        val parent = CoroutineScope(Job().also { it.cancel() })
        val slot = OwnedConnectionJob()
        var calls = 0
        slot.startIfIdle(parent, Dispatchers.Unconfined) { calls++ }
        assertFalse(slot.isRunning)
        assertNull(slot.state.value.job)
        assertEquals(0, calls)
    }

    @Test fun stopAndOldNullCallbacksCannotPublishConnected() = fixture {
        val slot = OwnedConnectionJob()
        slot.startIfIdle(this, Dispatchers.Unconfined) { awaitCancellation() }
        val job = slot.state.value.job
        assertTrue(slot.setConnected(job, true))
        slot.cancel()?.join()
        assertFalse(slot.setConnected(job, true))
        assertFalse(slot.setConnected(null, true))
        assertFalse(slot.isConnected)
    }

    @Test fun failingLoopReleasesItsSlotWithoutCancellingOtherLoops() = fixture {
        val errors = AtomicInteger()
        val slot = OwnedConnectionJob()
        val handler = CoroutineExceptionHandler { _, _ -> errors.incrementAndGet() }
        slot.startIfIdle(this, Dispatchers.Unconfined + handler) { error("simulated probe failure") }
        assertFalse(slot.isRunning)
        assertEquals(1, errors.get())
        var recovered = false
        assertTrue(slot.startIfIdle(this, Dispatchers.Unconfined) { recovered = true })
        assertTrue(recovered)
    }
}
