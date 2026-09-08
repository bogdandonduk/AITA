package kz.aita.auth
import kotlin.test.*
class AuthCodeTimeAndInputTest {
    @Test fun serverClockControlsExpiryDespitePhoneClockSkew() {
        assertEquals(600000L,aitaAuthExpiryDelayMillis(1600000L,1000000L,900000000L))
        assertEquals(60000L,aitaAuthResendDelayMillis(1060000L,1000000L,900000000L))
    }
    @Test fun legacyServerUsesLocalEpoch() {
        assertEquals(1000L,aitaAuthExpiryDelayMillis(2000L,0L,1000L))
        assertEquals(0L,aitaAuthExpiryDelayMillis(1000L,0L,2000L))
    }
    @Test fun expiryAndCountdownNeverGoNegative() {
        assertEquals(0L,aitaAuthCountdownSeconds(-12));assertEquals(1L,aitaAuthCountdownSeconds(1))
        assertEquals(1L,aitaAuthCountdownSeconds(1000));assertEquals(2L,aitaAuthCountdownSeconds(1001))
        assertEquals(0L,aitaAuthExpiryDelayMillis(1,2,0))
    }
    @Test fun normalizeUnicodeAndPastedCode() {
        assertEquals("012345",normalizeAitaOneTimeCode("٠١٢٣٤٥"))
        assertEquals("012345",normalizeAitaOneTimeCode("０１２３４５"))
        assertEquals("123456",normalizeAitaOneTimeCode("123 456"))
        assertNull(normalizeAitaOneTimeCode("12")); assertNull(normalizeAitaOneTimeCode("1234567"))
    }
    @Test fun emailIdentifiersAreCaseInsensitiveAndTrimmed() {
        assertEquals("user@example.com",normalizeAitaLoginIdentifier(" USER@EXAMPLE.COM ")?.value)
        assertNull(normalizeAitaLoginIdentifier("person\n@example.com"))
    }
}
