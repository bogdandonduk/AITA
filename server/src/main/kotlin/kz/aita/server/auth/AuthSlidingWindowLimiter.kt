package kz.aita.server.auth

import java.util.ArrayDeque

internal data class AuthRateLimitDecision(val allowed: Boolean, val retryAfterSeconds: Long = 0L)

/** Bounded process-local protection. Email's database quotas remain a separate cross-process gate. */
internal class AuthSlidingWindowLimiter(private val maximumTrackedKeys: Int = 20_000) {
    private data class Bucket(val windowMillis: Long, val times: ArrayDeque<Long> = ArrayDeque())
    private val values = HashMap<String, Bucket>()
    private var calls = 0L

    init { require(maximumTrackedKeys > 0) }

    fun allow(key: String, max: Int, now: Long, windowMillis: Long = 3_600_000L): Boolean =
        check(key, max, now, windowMillis).allowed

    // Cleanup and admission share a lock: a waiting caller cannot append to a removed bucket
    // and accidentally gain a second quota. No network/database work happens under this lock.
    @Synchronized
    fun check(key: String, max: Int, now: Long, windowMillis: Long = 3_600_000L): AuthRateLimitDecision {
        require(max > 0 && windowMillis > 0)
        if (++calls % 256L == 0L) cleanup(now)
        if (key !in values && values.size >= maximumTrackedKeys) {
            cleanup(now)
            if (values.size >= maximumTrackedKeys) return AuthRateLimitDecision(false, 60L)
        }
        val bucket = values.getOrPut(key) { Bucket(windowMillis) }
        require(bucket.windowMillis == windowMillis) { "A quota key must use a stable window" }
        prune(bucket, now)
        if (bucket.times.size >= max) {
            val remainingMillis = (bucket.windowMillis - (now - bucket.times.first())).coerceAtLeast(1L)
            return AuthRateLimitDecision(false, 1L + (remainingMillis - 1L) / 1_000L)
        }
        bucket.times.addLast(now)
        return AuthRateLimitDecision(true)
    }

    private fun prune(bucket: Bucket, now: Long) {
        while (bucket.times.isNotEmpty() && now >= bucket.times.first() &&
            now - bucket.times.first() >= bucket.windowMillis) bucket.times.removeFirst()
    }

    private fun cleanup(now: Long) {
        val iterator = values.values.iterator()
        while (iterator.hasNext()) {
            val bucket = iterator.next()
            prune(bucket, now)
            if (bucket.times.isEmpty()) iterator.remove()
        }
    }
}
