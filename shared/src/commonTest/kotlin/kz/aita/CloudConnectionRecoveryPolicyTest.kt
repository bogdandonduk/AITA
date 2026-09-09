package kz.aita

import kotlin.test.*

class CloudConnectionRecoveryPolicyTest {
    @Test fun successfulProbeDoesNotReturnToOutageBackoffBeforeQuorum() {
        listOf(15_000L, 30_000L, 60_000L, 120_000L).forEach { backoff ->
            assertEquals(4_000L, cloudRecoveryAwareProbeDelayMillis(true, true, backoff))
        }
    }

    @Test fun idleRecoveryCanActuallyReachThreeSignalsWithinExistingWindow() {
        var now = 1_000L
        val evidence = mutableListOf(now)
        repeat(2) {
            now += cloudRecoveryAwareProbeDelayMillis(true, true, 120_000L)
            evidence += now
        }
        assertEquals(3, evidence.size)
        assertTrue(evidence.last() - evidence.first() >= 8_000L)
        assertTrue(evidence.last() - evidence.first() < 45_000L)
        assertTrue(evidence.zipWithNext().all { (a, b) -> b - a >= 3_000L })
    }

    @Test fun normalHealthyAndFailedProbesRetainTheirOrdinaryIntervals() {
        assertEquals(30_000L, cloudRecoveryAwareProbeDelayMillis(true, false, 30_000L))
        assertEquals(30_000L, cloudRecoveryAwareProbeDelayMillis(false, true, 30_000L))
    }

    @Test fun busyRequestsCannotStarveProbesForever() {
        assertTrue(shouldDeferCloudHealthProbe(3, false, false, false, 30_000L, 1_000L))
        assertFalse(shouldDeferCloudHealthProbe(3, false, false, false, 61_000L, 1_000L))
        assertFalse(shouldDeferCloudHealthProbe(3, false, false, false, 1_000L, 0L))
    }

    @Test fun failureResumeOutageAndClockRollbackAreNotDeferred() {
        assertFalse(shouldDeferCloudHealthProbe(3, false, false, true, 5_000L, 1_000L))
        assertFalse(shouldDeferCloudHealthProbe(3, false, true, false, 5_000L, 1_000L))
        assertFalse(shouldDeferCloudHealthProbe(3, true, false, false, 5_000L, 1_000L))
        assertFalse(shouldDeferCloudHealthProbe(3, false, false, false, 500L, 1_000L))
        assertFalse(shouldDeferCloudHealthProbe(0, false, false, false, 5_000L, 1_000L))
    }
}
