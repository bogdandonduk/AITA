package kz.aita.server.marketplace

import java.util.UUID
import kotlin.test.*

class MarketBasketReadGateTest {
    @Test fun oneAccountCannotRunConcurrentPlans() {
        val gate=MarketBasketReadGate();val user=UUID.randomUUID()
        assertTrue(gate.acquire(user,0).allowed);assertFalse(gate.acquire(user,1).allowed)
        gate.release(user);assertTrue(gate.acquire(user,2).allowed)
    }
    @Test fun globalConcurrencyHasABoundAndReleaseRestoresCapacity() {
        val gate=MarketBasketReadGate(1);val a=UUID.randomUUID();val b=UUID.randomUUID()
        assertTrue(gate.acquire(a,0).allowed);assertFalse(gate.acquire(b,0).allowed)
        gate.release(a);assertTrue(gate.acquire(b,0).allowed)
    }
    @Test fun repeatedReadsAreRateLimitedAcrossReleasedSlots() {
        val gate=MarketBasketReadGate();val user=UUID.randomUUID()
        repeat(12){assertTrue(gate.acquire(user,it.toLong()).allowed);gate.release(user)}
        assertFalse(gate.acquire(user,12).allowed)
        assertTrue(gate.acquire(user,60_000).allowed)
    }
    @Test fun separateAccountsDoNotShareThePerAccountQuota() {
        val gate=MarketBasketReadGate();val a=UUID.randomUUID();val b=UUID.randomUUID()
        repeat(12){assertTrue(gate.acquire(a,it.toLong()).allowed);gate.release(a)}
        assertTrue(gate.acquire(b,12).allowed)
    }
    @Test fun simultaneousCallersCannotOverbookTheGlobalLimit() {
        val gate=MarketBasketReadGate(4)
        val start=java.util.concurrent.CountDownLatch(1)
        val pool=java.util.concurrent.Executors.newFixedThreadPool(16)
        try {
            val jobs=(1..16).map { pool.submit<Boolean> { start.await();gate.acquire(UUID.randomUUID(),0).allowed } }
            start.countDown()
            assertEquals(4,jobs.count { it.get(5,java.util.concurrent.TimeUnit.SECONDS) })
        } finally { pool.shutdownNow() }
    }
    @Test fun discoveryAllowanceDoesNotChangeTheDefaultBasketAllowance() {
        val gate = MarketBasketReadGate(requestsPerMinute = 60); val user = UUID.randomUUID()
        repeat(60) { assertTrue(gate.acquire(user, it.toLong()).allowed); gate.release(user) }
        assertFalse(gate.acquire(user, 60).allowed)
        assertTrue(gate.acquire(user, 60_000).allowed)
    }
    @Test fun invalidCapacityOrAllowanceIsRejected() {
        assertFailsWith<IllegalArgumentException> { MarketBasketReadGate(0) }
        assertFailsWith<IllegalArgumentException> { MarketBasketReadGate(requestsPerMinute = 0) }
        assertFailsWith<IllegalArgumentException> { MarketBasketReadGate(requestsPerMinute = -1) }
    }
}
