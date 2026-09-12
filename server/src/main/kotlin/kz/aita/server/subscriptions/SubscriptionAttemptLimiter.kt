package kz.aita.server.subscriptions

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Bounds code guessing by authenticated account. No raw promo codes or user secrets are retained. */
internal object SubscriptionAttemptLimiter {
    private data class Window(val started: Long, val attempts: Int)
    private val attempts = ConcurrentHashMap<String, Window>()
    fun allow(user: UUID, mutation: Boolean, now: Long = System.currentTimeMillis()): Boolean {
        val key = "$user:$mutation"
        val next = attempts.compute(key) { _, previous ->
            if (previous == null || now - previous.started >= 60_000L) Window(now, 1)
            else previous.copy(attempts = (previous.attempts + 1).coerceAtMost(1_000))
        } ?: return false
        if (attempts.size > 10_000) attempts.entries.removeIf { now - it.value.started > 60_000L }
        return next.attempts <= if (mutation) 10 else 20
    }
}
