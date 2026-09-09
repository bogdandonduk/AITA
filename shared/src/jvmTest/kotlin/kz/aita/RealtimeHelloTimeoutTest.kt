package kz.aita

import kotlinx.coroutines.*
import kotlin.test.*

class RealtimeHelloTimeoutTest {
    @Test fun sendsClientHelloBeforeAwaitingServerHello() = runBlocking {
        val calls = mutableListOf<String>()
        val result = awaitAitaRealtimeHello(
            sendHello = { calls += "send" },
            receiveHello = { calls += "receive"; "connected" }
        )
        assertEquals("connected", result)
        assertEquals(listOf("send", "receive"), calls)
    }

    @Test fun blockedClientHelloTimesOutAsRetryableFailure() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        var received = false
        assertFailsWith<IllegalStateException> {
            awaitAitaRealtimeHello({ gate.await() }, { received = true; "connected" }, 30L)
        }
        assertFalse(received)
        assertTrue(currentCoroutineContext().isActive)
    }

    @Test fun silentServerBeforeNegotiationCannotHangForever() = runBlocking {
        assertFailsWith<IllegalStateException> {
            awaitAitaRealtimeHello({}, { CompletableDeferred<String>().await() }, 30L)
        }
        assertEquals("connected", awaitAitaRealtimeHello({}, { "connected" }))
    }

    @Test fun cancellationOfOwnerIsNotConvertedToRetryableTimeout() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val work = async {
            awaitAitaRealtimeHello({ started.complete(Unit) }, { awaitCancellation() }, 5_000L)
        }
        started.await()
        work.cancel()
        assertFailsWith<CancellationException> { work.await() }
    }

    @Test fun transportFailureKeepsItsOriginalException() = runBlocking {
        val failure = IllegalArgumentException("broken channel")
        val actual = assertFailsWith<IllegalArgumentException> {
            awaitAitaRealtimeHello<String>({}, { throw failure })
        }
        assertSame(failure, actual)
    }

    @Test fun legacyHelloDoesNotEnableUnsupportedHeartbeat() = runBlocking {
        val interval = awaitAitaRealtimeHello({}, { 0L })
        assertFalse(aitaRealtimeUsesHeartbeat("connected", interval))
        assertTrue(aitaRealtimeUsesHeartbeat("connected", 30_000L))
    }
}
