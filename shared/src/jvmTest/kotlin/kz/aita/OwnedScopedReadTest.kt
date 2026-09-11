package kz.aita

import kotlinx.coroutines.*
import kotlin.test.*

class OwnedScopedReadTest {
    @Test fun sameOwnerRequestsAreCoalesced(): Unit = runBlocking {
        val reader = OwnedScopedRead<String>()
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        var starts = 0
        assertTrue(reader.start("a", this, Dispatchers.Unconfined) { starts++; entered.complete(Unit); finish.await() })
        entered.await()
        repeat(20) { assertFalse(reader.start("a", this, Dispatchers.Unconfined) { starts++ }) }
        assertEquals(1, starts)
        finish.complete(Unit)
    }

    @Test fun ownerIsRegisteredBeforeImmediateExecution(): Unit = runBlocking {
        val reader = OwnedScopedRead<String>()
        var owned = false
        reader.start("a", this, Dispatchers.Unconfined) {
            owned = reader.owns("a", currentCoroutineContext()[Job])
        }
        assertTrue(owned)
    }

    @Test fun replacementDoesNotWaitForAbandonedCleanup(): Unit = runBlocking {
        val reader = OwnedScopedRead<String>()
        val cleaning = CompletableDeferred<Unit>()
        val letOldFinish = CompletableDeferred<Unit>()
        val newStarted = CompletableDeferred<Unit>()
        val letNewFinish = CompletableDeferred<Unit>()
        reader.start("a", this, Dispatchers.Unconfined) {
            try { awaitCancellation() }
            finally { withContext(NonCancellable) { cleaning.complete(Unit); letOldFinish.await() } }
        }
        reader.start("b", this, Dispatchers.Unconfined) {
            newStarted.complete(Unit)
            letNewFinish.await()
            assertTrue(reader.owns("b", currentCoroutineContext()[Job]))
        }
        withTimeout(2_000) { cleaning.await(); newStarted.await() }
        letOldFinish.complete(Unit)
        yield()
        assertFalse(reader.start("b", this, Dispatchers.Unconfined) { error("duplicate b") })
        letNewFinish.complete(Unit)
    }

    @Test fun cancelThenSameKeyCannotBeClearedByOldCompletion(): Unit = runBlocking {
        val reader = OwnedScopedRead<String>()
        val releaseOld = CompletableDeferred<Unit>()
        val releaseNew = CompletableDeferred<Unit>()
        var oldOwnedAfterCancel = true
        reader.start("a", this, Dispatchers.Unconfined) {
            val job = currentCoroutineContext()[Job]
            try { awaitCancellation() }
            finally { withContext(NonCancellable) {
                releaseOld.await()
                oldOwnedAfterCancel = reader.owns("a", job)
            } }
        }
        reader.cancel()
        reader.start("a", this, Dispatchers.Unconfined) { releaseNew.await() }
        releaseOld.complete(Unit)
        yield()
        assertFalse(oldOwnedAfterCancel)
        assertFalse(reader.start("a", this, Dispatchers.Unconfined) { error("duplicate a") })
        releaseNew.complete(Unit)
    }

    @Test fun finishedReadCanStartAgain(): Unit = runBlocking {
        val reader = OwnedScopedRead<String>()
        var count = 0
        repeat(3) { assertTrue(reader.start("a", this, Dispatchers.Unconfined) { count++ }) }
        assertEquals(3, count)
    }

    @Test fun oneFailedReadDoesNotPreventTheNextRead(): Unit = runBlocking {
        val failed = CompletableDeferred<Unit>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined + CoroutineExceptionHandler { _, _ -> failed.complete(Unit) })
        val reader = OwnedScopedRead<String>()
        try {
            reader.start("a", scope, Dispatchers.Unconfined) { error("request failure") }
            withTimeout(2_000) { failed.await() }
            var succeeded = false
            assertTrue(reader.start("a", scope, Dispatchers.Unconfined) { succeeded = true })
            assertTrue(succeeded)
        } finally { scope.cancel() }
    }
}
