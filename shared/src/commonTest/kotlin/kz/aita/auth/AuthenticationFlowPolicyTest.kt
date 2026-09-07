package kz.aita.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuthenticationFlowPolicyTest {
    @Test
    fun onlyKnownMissingRoutesPermitPasswordCompatibility() {
        listOf(404, 405).forEach {
            assertTrue(canUseLegacyPasswordRoute(it, transportFailure = false))
            assertFalse(canUseLegacyPasswordRoute(it, transportFailure = true))
        }
        listOf(null, 200, 202, 400, 401, 403, 409, 428, 429, 500, 501, 502, 503, 504).forEach { status ->
            assertFalse(canUseLegacyPasswordRoute(status, transportFailure = false))
            assertFalse(canUseLegacyPasswordRoute(status, transportFailure = true))
        }
    }

    @Test
    fun serverClockPreventsImmediateResendOnFastOrSlowDeviceClocks() {
        assertEquals(60_000L, aitaAuthResendDelayMillis(1_060_000L, 1_000_000L, 9_000_000L))
        assertEquals(60_000L, aitaAuthResendDelayMillis(1_060_000L, 1_000_000L, 100L))
        assertEquals(0L, aitaAuthResendDelayMillis(999_999L, 1_000_000L, 0L))
    }

    @Test
    fun oldServerClockFallbackAndInvalidValuesStayBounded() {
        assertEquals(60_000L, aitaAuthResendDelayMillis(160_000L, 0L, 100_000L))
        assertEquals(0L, aitaAuthResendDelayMillis(0L, 0L, 100L))
        assertEquals(600_000L, aitaAuthResendDelayMillis(Long.MAX_VALUE, 0L, Long.MIN_VALUE))
        assertEquals(1L, aitaAuthResendDelayMillis(Long.MAX_VALUE, Long.MAX_VALUE - 1L, 0L))
    }

    @Test
    fun countdownRoundsUpWithoutOverflow() {
        assertEquals(0L, aitaAuthCountdownSeconds(-1L))
        assertEquals(0L, aitaAuthCountdownSeconds(0L))
        assertEquals(1L, aitaAuthCountdownSeconds(1L))
        assertEquals(1L, aitaAuthCountdownSeconds(1000L))
        assertEquals(2L, aitaAuthCountdownSeconds(1001L))
        assertTrue(aitaAuthCountdownSeconds(Long.MAX_VALUE) > 0L)
    }
}
