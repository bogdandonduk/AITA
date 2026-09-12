package kz.aita

import kotlin.test.*

class VerifiedStoreSubscriptionTest {
    private fun proof() = VerifiedStoreSubscription("account", StoreSubscriptionStateDataModel(storeId = "store",
        planId = "basic", status = "active", currentPeriodStartMillis = 100_000L, currentPeriodEndMillis = 120_000L),
        verifiedAtLocalMillis = 50_000L, verifiedAtServerMillis = 100_000L)
    @Test fun offlineClockFollowsServerPlusElapsedLocalTime() {
        assertEquals(110_000L, proof().effectiveNow(60_000L)); assertTrue(proof().allows("store", 60_000L))
    }
    @Test fun serverExpiryCannotBeExtendedByADeviceTimezone() {
        assertFalse(proof().allows("store", 70_000L)); assertFalse(proof().allows("other", 50_000L))
    }
    @Test fun aSignificantClockRollbackRequiresOnlineVerification() {
        assertNull(proof().effectiveNow(44_999)); assertFalse(proof().allows("store", 44_999))
    }
    @Test fun minorClockAdjustmentNeverBackdatesTheVerifiedServerTime() {
        assertEquals(100_000L, proof().effectiveNow(45_000)); assertEquals(100_000L, proof().effectiveNow(49_000))
    }
    @Test fun corruptClockProofsAndOverflowCannotGrantAccess() {
        assertNull(proof().copy(verifiedAtServerMillis = 0).effectiveNow(50_000))
        assertNull(proof().copy(verifiedAtLocalMillis = -1).effectiveNow(50_000))
        assertNull(proof().effectiveNow(Long.MIN_VALUE))
        assertNull(proof().copy(verifiedAtServerMillis = Long.MAX_VALUE).effectiveNow(60_000))
    }
}
