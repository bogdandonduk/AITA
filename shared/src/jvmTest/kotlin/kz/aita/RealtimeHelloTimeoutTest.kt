package kz.aita

import kotlinx.coroutines.*
import kotlin.test.*

class RealtimeHelloTimeoutTest {
    @Test fun sendsClientHelloBeforeAwaitingServerHello() = runBlocking<Unit> {
        val calls = mutableListOf<String>()
        val result = awaitAitaRealtimeHello(
            sendHello = { calls += "send" },
            receiveHello = { calls += "receive"; "connected" }
        )
        assertEquals("connected", result)
        assertEquals(listOf("send", "receive"), calls)
    }

    @Test fun blockedClientHelloTimesOutAsRetryableFailure() = runBlocking<Unit> {
        val gate = CompletableDeferred<Unit>()
        var received = false
        assertFailsWith<IllegalStateException> {
            awaitAitaRealtimeHello({ gate.await() }, { received = true; "connected" }, 30L)
        }
        assertFalse(received)
        assertTrue(currentCoroutineContext().isActive)
    }

    @Test fun silentServerBeforeNegotiationCannotHangForever() = runBlocking<Unit> {
        assertFailsWith<IllegalStateException> {
            awaitAitaRealtimeHello({}, { CompletableDeferred<String>().await() }, 30L)
        }
        assertEquals("connected", awaitAitaRealtimeHello({}, { "connected" }))
    }

    @Test fun cancellationOfOwnerIsNotConvertedToRetryableTimeout() = runBlocking<Unit> {
        val started = CompletableDeferred<Unit>()
        val work = async {
            awaitAitaRealtimeHello({ started.complete(Unit) }, { awaitCancellation() }, 5_000L)
        }
        started.await()
        work.cancel()
        assertFailsWith<CancellationException> { work.await() }
    }

    @Test fun transportFailureKeepsItsOriginalException() = runBlocking<Unit> {
        val failure = IllegalArgumentException("broken channel")
        val actual = assertFailsWith<IllegalArgumentException> {
            awaitAitaRealtimeHello<String>({}, { throw failure })
        }
        assertEquals(failure.message, actual.message)
        // Coroutine debug stack recovery can copy an exception while retaining its original cause.
        assertTrue(actual === failure || actual.cause === failure)
    }

    @Test fun legacyHelloDoesNotEnableUnsupportedHeartbeat() = runBlocking<Unit> {
        val interval = awaitAitaRealtimeHello({}, { 0L })
        assertFalse(aitaRealtimeUsesHeartbeat("connected", interval))
        assertTrue(aitaRealtimeUsesHeartbeat("connected", 30_000L))
    }
}
