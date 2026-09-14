package kz.aita.server.marketplace

import kz.aita.server.auth.AuthRateLimitDecision
import kz.aita.server.auth.AuthSlidingWindowLimiter
import java.util.UUID

/** Process-local load protection, not a distributed quota or entitlement grant. The route releases
 * its slot on success, failure and cancellation. No coroutine waits while holding this monitor.
 */
internal class MarketBasketReadGate(private val parallelLimit: Int = 4, private val requestsPerMinute: Int = 12) {
    private val active = mutableSetOf<UUID>()
    private val limiter = AuthSlidingWindowLimiter(maximumTrackedKeys = 8192)
    init { require(parallelLimit > 0 && requestsPerMinute > 0) }

    @Synchronized
    fun acquire(user: UUID, monotonicMillis: Long): AuthRateLimitDecision {
        if (user in active || active.size >= parallelLimit) return AuthRateLimitDecision(false, 2L)
        val decision = limiter.check(user.toString(), max = requestsPerMinute, now = monotonicMillis, windowMillis = 60_000L)
        if (decision.allowed) active.add(user)
        return decision
    }

    @Synchronized
    fun release(user: UUID) { active.remove(user) }
}
