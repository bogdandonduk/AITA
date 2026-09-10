package kz.aita

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** A cancelled HTTP call/channel is not necessarily a cancelled reconnect worker.
 * Call from the OWNER's catch block, after the failed request scope has unwound.
 * Logout, replacement and parent timeouts still propagate immediately.
 */
internal suspend fun ensureConnectionOwnerActive(failure: Throwable) {
    currentCoroutineContext().ensureActive()
    if (failure is Error) throw failure
}

internal class RealtimeHandshakeTimeoutException : IllegalStateException("Realtime handshake timed out")

/** The request and handshake watchdog are children of the reconnect attempt, not HttpClient.
 * Stop the deadline only after the server greeting; it must not time out a live connection.
 */
internal suspend fun <T> withRealtimeHandshakeDeadline(
    timeoutMillis: Long = 25_000L,
    connect: suspend (handshakeCompleted: () -> Unit) -> T
): T = coroutineScope {
    require(timeoutMillis > 0L)
    val deadline = launch {
        delay(timeoutMillis)
        throw RealtimeHandshakeTimeoutException()
    }
    try {
        connect { deadline.cancel() }
    } finally {
        deadline.cancel()
    }
}

/** Wall-clock rollback cannot park reconciliation indefinitely. */
internal fun realtimeCatchupDelayMillis(now: Long, lastRefresh: Long, interval: Long): Long {
    require(interval >= 0L)
    if (lastRefresh <= 0L || now < lastRefresh) return 0L
    return (interval - (now - lastRefresh)).coerceAtLeast(0L)
}
