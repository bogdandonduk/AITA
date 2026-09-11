package kz.aita.auth

import kotlin.test.*

class EmailVerificationPolicyTest {
    @Test fun requiredTotpNeverDisappearsWhenEmailFlagIsAlsoPresent() {
        assertEquals(AitaLoginSecondFactor.AUTHENTICATOR, aitaLoginSecondFactor(true, true, true))
        assertEquals(AitaLoginSecondFactor.AUTHENTICATOR, aitaLoginSecondFactor(true, true, false))
    }
    @Test fun connectedButOptionalAuthenticatorAllowsExplicitEmailPolicy() {
        assertEquals(AitaLoginSecondFactor.EMAIL, aitaLoginSecondFactor(true, false, true))
        assertEquals(AitaLoginSecondFactor.NONE, aitaLoginSecondFactor(true, false, false))
    }
    @Test fun absentAuthenticatorDoesNotPretendToBeAnotherFactor() {
        assertEquals(AitaLoginSecondFactor.EMAIL, aitaLoginSecondFactor(false, true, true))
        assertEquals(AitaLoginSecondFactor.NONE, aitaLoginSecondFactor(false, true, false))
    }
    @Test fun settingsPreserveOlderServersMandatoryAuthenticatorDefault() {
        assertEquals(AitaLoginSecondFactor.AUTHENTICATOR, AitaAuthenticationSettingsDataModel(authenticatorEnabled = true).loginSecondFactor)
        assertEquals(AitaLoginSecondFactor.NONE, AitaAuthenticationSettingsDataModel().loginSecondFactor)
    }
    @Test fun maskShowsAtMostThreeLocalCharacters() {
        assertEquals("bog**********@example.test", maskAitaEmailDestination("bogdan.donduk@example.test"))
    }
    @Test fun shortAddressesAreStillMasked() {
        assertEquals("***@example.test", maskAitaEmailDestination("a@example.test"))
        assertEquals("a***@example.test", maskAitaEmailDestination("ab@example.test"))
        assertEquals("ab***@example.test", maskAitaEmailDestination("abc@example.test"))
        assertEquals("abc***@example.test", maskAitaEmailDestination("abcd@example.test"))
    }
    @Test fun invalidAddressDoesNotBecomeAMask() {
        assertEquals("", maskAitaEmailDestination("invalid"))
        assertEquals("", maskAitaEmailDestination("a@x.test\n<script>"))
    }
    @Test fun extraEmailLimitIsExactlyOne() { assertEquals(1, AITA_MAX_ADDITIONAL_LOGIN_EMAILS) }
    @Test fun loginPolicyTargetsAreClosedAndCaseSensitive() {
        for (method in AitaLoginSecondFactor.entries) assertEquals(method.name, canonicalAitaSecurityTarget(AitaSecurityEmailAction.LOGIN_POLICY, method.name))
        assertNull(canonicalAitaSecurityTarget(AitaSecurityEmailAction.LOGIN_POLICY, "email"))
        assertNull(canonicalAitaSecurityTarget(AitaSecurityEmailAction.LOGIN_POLICY, "arbitrary"))
    }
    @Test fun emailChangeProofTargetsAreNormalized() {
        for (action in listOf(AitaSecurityEmailAction.ADD_EMAIL, AitaSecurityEmailAction.REMOVE_EMAIL)) {
            assertEquals("extra@example.test", canonicalAitaSecurityTarget(action, " EXTRA@EXAMPLE.TEST "))
            assertNull(canonicalAitaSecurityTarget(action, "invalid"))
        }
    }
    @Test fun setupProofCannotCarryARecipient() {
        assertEquals("", canonicalAitaSecurityTarget(AitaSecurityEmailAction.TOTP_SETUP, ""))
        assertNull(canonicalAitaSecurityTarget(AitaSecurityEmailAction.TOTP_SETUP, "other@example.test"))
    }
    @Test fun profileTargetIsUnambiguousAndRejectsExtraFields() {
        val target = aitaProfileSecurityTarget("8 (777) 123-45-67", " MAIN@EXAMPLE.TEST ", true)
        assertEquals("+77771234567\nmain@example.test\ntrue", target)
        assertEquals(target, canonicalAitaSecurityTarget(AitaSecurityEmailAction.PROFILE, target))
        assertNull(canonicalAitaSecurityTarget(AitaSecurityEmailAction.PROFILE, "$target\nother"))
        assertNull(canonicalAitaSecurityTarget(AitaSecurityEmailAction.PROFILE, target.replace("true", "yes")))
        assertNull(canonicalAitaSecurityTarget(AitaSecurityEmailAction.PROFILE, "x".repeat(1025)))
    }
}
