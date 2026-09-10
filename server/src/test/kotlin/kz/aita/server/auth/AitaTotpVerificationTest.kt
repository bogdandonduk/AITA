package kz.aita.server.auth

import kotlin.test.*

class AitaTotpVerificationTest {
    // RFC 6238 appendix B SHA-1 vectors, reduced to this application's six output digits.
    private val secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
    @Test fun matchesIndependentPublishedVectorsIncludingBeyond2038() {
        listOf(59L to "287082", 1111111109L to "081804", 1111111111L to "050471",
            1234567890L to "005924", 2000000000L to "279037", 20000000000L to "353130").forEach { (seconds, code) ->
            assertEquals(code, aitaTotpCode(secret, seconds / 30))
            assertEquals(seconds / 30, aitaTotpMatchingStep(secret, code, seconds * 1000, null))
        }
    }
    @Test fun acceptsOnlyOneAdjacentTimeStep() {
        val now = 1234567890000L; val step = now / 30_000
        for (delta in -1L..1L) assertEquals(step + delta, aitaTotpMatchingStep(secret, aitaTotpCode(secret, step + delta), now, null))
        for (delta in listOf(-2L, 2L)) assertNull(aitaTotpMatchingStep(secret, aitaTotpCode(secret, step + delta), now, null))
    }
    @Test fun persistedStepPreventsReuseAndMovingTheClockBackward() {
        val now = 1234567890000L; val step = now / 30_000
        assertNull(aitaTotpMatchingStep(secret, aitaTotpCode(secret, step), now, step))
        assertNull(aitaTotpMatchingStep(secret, aitaTotpCode(secret, step - 1), now, step))
        assertEquals(step + 1, aitaTotpMatchingStep(secret, aitaTotpCode(secret, step + 1), now, step))
    }
    @Test fun localizedDigitsAndWhitespaceDoNotChangeTheNumericCode() {
        assertEquals(1L, aitaTotpMatchingStep(secret, " ２８７ ０８２ ", 59_000L, null))
        assertEquals(1L, aitaTotpMatchingStep(secret.lowercase(), "287082", 59_000L, null))
    }
    @Test fun textPunctuationPartialAndRecoveryCodesAreNotTotp() {
        for (input in listOf("code287082", "287-082", "28708", "1287082", "ABCDEF-GHJKLM", "", "287082!"))
            assertNull(aitaTotpMatchingStep(secret, input, 59_000L, null))
        assertNull(aitaTotpMatchingStep(secret, "287082", -1L, null))
    }
    @Test fun malformedKeyIsNeverSilentlyReinterpreted() {
        assertFailsWith<IllegalArgumentException> { aitaTotpCode("!", 1L) }
        assertFailsWith<IllegalArgumentException> { aitaTotpCode("", 1L) }
        assertFailsWith<IllegalArgumentException> { aitaTotpCode(secret, -1L) }
    }
}
