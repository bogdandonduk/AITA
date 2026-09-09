package kz.aita.server.auth

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

class AuthSlidingWindowLimiterTest {
    @Test fun passwordQuotaIsStillTwelveAttemptsPerHour() {
        val limiter = AuthSlidingWindowLimiter()
        repeat(12) { assertTrue(limiter.check("password:id:test", 12, 0L).allowed) }
        assertEquals(AuthRateLimitDecision(false, 3_600L), limiter.check("password:id:test", 12, 0L))
    }

    @Test fun deniedAttemptDoesNotExtendTheWindow() {
        val limiter = AuthSlidingWindowLimiter()
        assertTrue(limiter.allow("a", 1, 0L))
        assertEquals(1_800L, limiter.check("a", 1, 1_800_000L).retryAfterSeconds)
        assertEquals(1L, limiter.check("a", 1, 3_599_999L).retryAfterSeconds)
        assertTrue(limiter.allow("a", 1, 3_600_000L))
    }

    @Test fun retryRoundsUpToTheNextSecond() {
        val limiter = AuthSlidingWindowLimiter()
        limiter.allow("a", 1, 0L, 2_001L)
        assertEquals(3L, limiter.check("a", 1, 0L, 2_001L).retryAfterSeconds)
        assertEquals(2L, limiter.check("a", 1, 1L, 2_001L).retryAfterSeconds)
    }

    @Test fun identifiersAndIpBucketsRemainIndependent() {
        val limiter = AuthSlidingWindowLimiter()
        assertTrue(limiter.allow("id:a", 1, 0L))
        assertFalse(limiter.allow("id:a", 1, 0L))
        assertTrue(limiter.allow("id:b", 1, 0L))
        repeat(60) { assertTrue(limiter.allow("ip:a", 60, 0L)) }
        assertFalse(limiter.allow("ip:a", 60, 0L))
    }

    @Test fun fullMapFailsClosedAndExpiredEntriesCanBeReclaimed() {
        val limiter = AuthSlidingWindowLimiter(maximumTrackedKeys = 1)
        assertTrue(limiter.allow("a", 1, 0L, 1_000L))
        assertFalse(limiter.allow("b", 1, 0L, 1_000L))
        assertTrue(limiter.allow("b", 1, 1_000L, 1_000L))
    }

    @Test fun cleanupHonoursEachBucketsOwnWindow() {
        val limiter = AuthSlidingWindowLimiter(maximumTrackedKeys = 2)
        assertTrue(limiter.allow("long", 1, 0L, 3_600_000L))
        assertTrue(limiter.allow("short", 1, 0L, 1_000L))
        assertTrue(limiter.allow("new", 1, 2_000L, 1_000L))
        assertFalse(limiter.allow("long", 1, 2_000L, 3_600_000L))
    }

    @Test fun concurrentRequestsCannotExceedBucketLimit() {
        val limiter = AuthSlidingWindowLimiter()
        val executor = Executors.newFixedThreadPool(8)
        val go = CountDownLatch(1)
        val allowed = AtomicInteger()
        try {
            val futures = (1..500).map {
                executor.submit { go.await(); if (limiter.allow("same", 12, 100L)) allowed.incrementAndGet() }
            }
            go.countDown()
            futures.forEach { it.get(5, TimeUnit.SECONDS) }
            assertEquals(12, allowed.get())
        } finally { executor.shutdownNow() }
    }

    @Test fun invalidLimitsAreRejected() {
        assertFailsWith<IllegalArgumentException> { AuthSlidingWindowLimiter(0) }
        val limiter = AuthSlidingWindowLimiter()
        assertFailsWith<IllegalArgumentException> { limiter.allow("a", 0, 0L) }
        assertFailsWith<IllegalArgumentException> { limiter.allow("a", 1, 0L, 0L) }
    }
}
