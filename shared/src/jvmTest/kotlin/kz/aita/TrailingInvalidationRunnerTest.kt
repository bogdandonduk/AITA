package kz.aita

import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.*

class TrailingInvalidationRunnerTest {
    @Test fun invalidationDuringAReadAlwaysGetsAFreshTrailingRead() = runBlocking {
        val runner = TrailingInvalidationRunner<String>()
        val started = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>(); val finished = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        val read: suspend () -> Unit = {
            if (calls.incrementAndGet() == 1) { started.complete(Unit); release.await() }
            else finished.complete(Unit)
        }
        runner.invalidate("A", this, EmptyCoroutineContext, read)
        withTimeout(3000) { started.await() }
        runner.invalidate("A", this, EmptyCoroutineContext, read)
        release.complete(Unit); withTimeout(3000) { finished.await() }
        assertEquals(2, calls.get()); runner.cancel()
    }
    @Test fun aBurstCoalescesWithoutLosingItsTrailingEdge() = runBlocking {
        val runner = TrailingInvalidationRunner<String>(); val calls = AtomicInteger()
        val started = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>(); val done = CompletableDeferred<Unit>()
        val read: suspend () -> Unit = {
            if (calls.incrementAndGet() == 1) { started.complete(Unit); release.await() } else done.complete(Unit)
        }
        runner.invalidate("A", this, EmptyCoroutineContext, read); started.await()
        repeat(200) { runner.invalidate("A", this, EmptyCoroutineContext, read) }
        release.complete(Unit); withTimeout(3000) { done.await() }
        assertEquals(2, calls.get()); runner.cancel()
    }
    @Test fun storeReplacementCancelsTheOldWorkerButKeepsTheNewOne() = runBlocking {
        val runner = TrailingInvalidationRunner<String>()
        val aStarted = CompletableDeferred<Unit>(); val aCancelled = CompletableDeferred<Unit>(); val bDone = CompletableDeferred<Unit>()
        runner.invalidate("A", this, EmptyCoroutineContext) {
            try { aStarted.complete(Unit); awaitCancellation() } finally { aCancelled.complete(Unit) }
        }
        aStarted.await()
        runner.invalidate("B", this, EmptyCoroutineContext) { bDone.complete(Unit) }
        withTimeout(3000) { aCancelled.await(); bDone.await() }; runner.cancel()
    }
    @Test fun aLateOldFinalizerCannotEraseAReplacementWithAnotherDirtyRead() = runBlocking {
        val runner = TrailingInvalidationRunner<String>()
        val aStarted = CompletableDeferred<Unit>(); val aFinally = CompletableDeferred<Unit>(); val releaseA = CompletableDeferred<Unit>()
        runner.invalidate("A", this, EmptyCoroutineContext) {
            try { aStarted.complete(Unit); awaitCancellation() }
            finally { withContext(NonCancellable) { aFinally.complete(Unit); releaseA.await() } }
        }
        aStarted.await()
        val bStarted = CompletableDeferred<Unit>(); val releaseB = CompletableDeferred<Unit>(); val bDone = CompletableDeferred<Unit>(); var bReads = 0
        val readB: suspend () -> Unit = { bReads++; if (bReads == 1) { bStarted.complete(Unit); releaseB.await() } else bDone.complete(Unit) }
        runner.invalidate("B", this, EmptyCoroutineContext, readB)
        withTimeout(3000) { aFinally.await(); bStarted.await() }
        releaseA.complete(Unit); yield()
        runner.invalidate("B", this, EmptyCoroutineContext, readB); releaseB.complete(Unit)
        withTimeout(3000) { bDone.await() }; assertEquals(2, bReads); runner.cancel()
    }
    @Test fun returningToSameStoreUsesANewOwnershipEpoch() = runBlocking {
        data class Owner(val store: String, val epoch: Int)
        val runner = TrailingInvalidationRunner<Owner>(); val seen = mutableListOf<Owner>()
        val first = CompletableDeferred<Unit>(); val second = CompletableDeferred<Unit>(); val last = CompletableDeferred<Unit>()
        runner.invalidate(Owner("A", 1), this, EmptyCoroutineContext) { first.complete(Unit); awaitCancellation() }
        first.await()
        runner.invalidate(Owner("B", 2), this, EmptyCoroutineContext) { second.complete(Unit); awaitCancellation() }
        second.await()
        runner.invalidate(Owner("A", 3), this, EmptyCoroutineContext) { seen += Owner("A", 3); last.complete(Unit) }
        withTimeout(3000) { last.await() }; assertEquals(listOf(Owner("A", 3)), seen); runner.cancel()
    }
    @Test fun cancelStopsAnIdleSuspendedRead() = runBlocking {
        val runner = TrailingInvalidationRunner<String>(); val entered = CompletableDeferred<Unit>(); val stopped = CompletableDeferred<Unit>()
        runner.invalidate("A", this, EmptyCoroutineContext) { try { entered.complete(Unit); awaitCancellation() } finally { stopped.complete(Unit) } }
        entered.await(); runner.cancel(); withTimeout(3000) { stopped.await() }; Unit
    }
    @Test fun aCompletedRunCanBeInvalidatedAgain() = runBlocking {
        val runner = TrailingInvalidationRunner<String>(); var reads = 0
        runner.invalidate("A", this, EmptyCoroutineContext) { reads++ }; yield(); yield()
        runner.invalidate("A", this, EmptyCoroutineContext) { reads++ }; yield(); yield()
        assertEquals(2, reads); runner.cancel()
    }
}
