package kz.aita.auth

import kotlinx.coroutines.*
import kotlin.test.*

class AuthenticationCompletionCoordinatorTest {
    private fun fixture(block: suspend CoroutineScope.() -> Unit) = runBlocking {
        val owner = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try { withTimeout(5_000L) { owner.block() } } finally { owner.cancel() }
    }

    @Test fun concurrentWaitersShareOneInFlightBootstrap() = fixture {
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val c = AuthenticationCompletionCoordinator(this) { generation -> calls++; gate.await(); generation }
        val first = async { c.await(10L) }
        val second = async { c.await(10L) }
        assertEquals(1, calls)
        gate.complete(Unit)
        assertEquals(10L, first.await())
        assertEquals(10L, second.await())
    }

    @Test fun disposingLoginScreenDoesNotCancelAcceptedAccountInitialization() = fixture {
        val gate = CompletableDeferred<Unit>()
        var finished = false
        var cancelled = false
        val c = AuthenticationCompletionCoordinator(this) {
            try { gate.await(); finished = true; "loaded" }
            catch (e: CancellationException) { cancelled = true; throw e }
        }
        val screen = async { c.await(1L) }
        screen.cancelAndJoin()
        assertFalse(cancelled)
        assertFalse(finished)
        val nextScreen = async { c.await(1L) }
        gate.complete(Unit)
        assertEquals("loaded", nextScreen.await())
        assertTrue(finished)
    }

    @Test fun completedFailureCanBeRetriedWithoutAnotherAuthenticatorChallenge() = fixture {
        var attempts = 0
        val c = AuthenticationCompletionCoordinator(this) { ++attempts == 2 }
        assertFalse(c.await(1L))
        assertTrue(c.await(1L))
        assertEquals(2, attempts)
    }

    @Test fun newSessionCancelsOldBootstrapAndRejectsItsResult() = fixture {
        val gate = CompletableDeferred<Unit>()
        var oldCancelled = false
        val c = AuthenticationCompletionCoordinator(this) { generation ->
            if (generation == 1L) {
                try { gate.await() } catch (e: CancellationException) { oldCancelled = true; throw e }
            }
            generation
        }
        val old = async { c.await(1L) }
        assertEquals(2L, c.await(2L))
        assertTrue(oldCancelled)
        assertFailsWith<CancellationException> { old.await() }
    }

    @Test fun oldCancelCannotCancelCurrentSession() = fixture {
        val gate = CompletableDeferred<Unit>()
        val c = AuthenticationCompletionCoordinator(this) { generation -> gate.await(); generation }
        val current = async { c.await(2L) }
        c.cancel(1L)
        assertTrue(current.isActive)
        gate.complete(Unit)
        assertEquals(2L, current.await())
    }

    @Test fun explicitReturnToSignInCancelsPendingBootstrap() = fixture {
        val gate = CompletableDeferred<Unit>()
        var cancelled = false
        val c = AuthenticationCompletionCoordinator(this) {
            try { gate.await() } finally { cancelled = true }
        }
        val pending = async { c.await(1L) }
        c.cancel(1L)
        assertFailsWith<CancellationException> { pending.await() }
        assertTrue(cancelled)
    }
}
