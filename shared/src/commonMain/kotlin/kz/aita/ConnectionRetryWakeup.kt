package kz.aita

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** Wake a sleeping retry without cancelling a handshake or a healthy connection.
 * A revision captured BEFORE work also observes a signal received while that work was running.
 * Multiple lifecycle/network/data hints coalesce; they never queue one request per hint.
 */
internal class ConnectionRetryWakeup {
    private val state = MutableStateFlow(0L)
    val revision: Long get() = state.value

    fun request() {
        while (true) {
            val current = state.value
            if (state.compareAndSet(current, current + 1L)) return
        }
    }

    /** true = new evidence, false = normal timer expiry. Parent cancellation always propagates. */
    suspend fun await(observedRevision: Long, delayMillis: Long): Boolean {
        currentCoroutineContext().ensureActive()
        if (state.value != observedRevision) return true
        if (delayMillis <= 0L) return false
        return withTimeoutOrNull(delayMillis) {
            state.first { it != observedRevision }
            true
        } ?: false
    }
}

/** A short outage gets frequent probes; a sustained outage settles at one every ten seconds. */
internal fun cloudUnavailableRetryDelayMillis(round: Int): Long = when (round.coerceAtLeast(0)) {
    0 -> 2_000L
    1 -> 4_000L
    2 -> 7_000L
    else -> 10_000L
}

/** Ready HTTP with a missing socket is not a reason to keep exponential outage backoff. */
internal fun realtimeRetryDelayMillis(attemptDelayMillis: Long, transportAvailable: Boolean): Long =
    if (transportAvailable) attemptDelayMillis.coerceIn(1_000L, 5_000L)
    else attemptDelayMillis.coerceIn(2_000L, 10_000L)

/** Fast transport checks must not become a full inventory/outbox refresh every few seconds.
 * A new account/session receives an immediate pass; the same session's HTTP fallback is bounded.
 */
internal class ConnectionReconciliationGate(
    private val nowMillis: () -> Long,
    private val intervalMillis: Long = 30_000L
) {
    private data class Stamp(val generation: Long, val atMillis: Long)
    private val state = MutableStateFlow<Stamp?>(null)

    init { require(intervalMillis > 0L) }

    fun claim(generation: Long): Boolean {
        while (true) {
            val previous = state.value
            val now = nowMillis()
            if (previous?.generation == generation && now >= previous.atMillis &&
                now - previous.atMillis < intervalMillis) return false
            if (state.compareAndSet(previous, Stamp(generation, now))) return true
        }
    }
}
