package kz.aita.android

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PhoneOrientationPolicyTest {
    @Test fun compactPhoneUsesPortrait() {
        assertTrue(shouldLockPhoneToPortrait(411, 411, true, false, false))
    }
    @Test fun rotatingACompactPhoneDoesNotChangeClassification() {
        assertTrue(shouldLockPhoneToPortrait(411, 411, true, false, false))
    }
    @Test fun sixHundredDpTabletIsNotLocked() {
        assertFalse(shouldLockPhoneToPortrait(600, 600, true, false, false))
    }
    @Test fun narrowTabletWindowIsNotMistakenForAPhone() {
        assertFalse(shouldLockPhoneToPortrait(800, 360, true, false, false))
    }
    @Test fun largeConfigurationIsPreservedWithLetterboxedDisplay() {
        assertFalse(shouldLockPhoneToPortrait(500, 720, true, false, false))
    }
    @Test fun foldableChangesPolicyWhenItsDisplayOpens() {
        assertTrue(shouldLockPhoneToPortrait(380, 380, true, false, false))
        assertFalse(shouldLockPhoneToPortrait(720, 720, true, false, false))
    }
    @Test fun multiWindowAndPictureInPictureRemainSystemManaged() {
        assertFalse(shouldLockPhoneToPortrait(411, 411, true, true, false))
        assertFalse(shouldLockPhoneToPortrait(411, 411, true, false, true))
    }
    @Test fun legacyLargeScreenTabletIsNotLockedEvenBelowSixHundredDp() {
        assertFalse(shouldLockPhoneToPortrait(533, 533, true, false, false, largeScreenConfiguration = true))
    }
    @Test fun tvCarWatchOrUnknownDisplayAreNotLocked() {
        assertFalse(shouldLockPhoneToPortrait(411, 411, false, false, false))
        assertFalse(shouldLockPhoneToPortrait(0, 0, true, false, false))
    }
}
