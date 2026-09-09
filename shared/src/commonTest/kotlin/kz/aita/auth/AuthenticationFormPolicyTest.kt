package kz.aita.auth

import kotlin.test.*

class AuthenticationFormPolicyTest {
    @Test fun emptyOrMalformedCredentialsAreRejectedAtSubmission() {
        assertFalse(aitaPrimarySignInIsWellFormed("", AitaAuthIdentifierKind.EMAIL, "p", true))
        assertFalse(aitaPrimarySignInIsWellFormed("not-an-email", AitaAuthIdentifierKind.EMAIL, "p", true))
        assertFalse(aitaPrimarySignInIsWellFormed("user@example.com", AitaAuthIdentifierKind.EMAIL, "", true))
        assertFalse(aitaPrimarySignInIsWellFormed("user@example.com", AitaAuthIdentifierKind.EMAIL, "   ", true))
    }
    @Test fun existingShortPasswordsAreNotRejectedBySignupRules() {
        for (password in listOf("a", "12", "  legacy password  ")) {
            assertTrue(aitaPrimarySignInIsWellFormed("user@example.com", AitaAuthIdentifierKind.EMAIL, password, true))
        }
    }
    @Test fun emailCodeSubmissionDoesNotNeedAPassword() {
        assertTrue(aitaPrimarySignInIsWellFormed("USER@example.com", AitaAuthIdentifierKind.EMAIL, "", false))
        assertTrue(aitaPrimarySignInIsWellFormed("+7 (777) 123-45-67", AitaAuthIdentifierKind.PHONE, "", false))
    }
    @Test fun selectedIdentifierTabMustMatchInput() {
        assertFalse(aitaPrimarySignInIsWellFormed("user@example.com", AitaAuthIdentifierKind.PHONE, "p", true))
        assertFalse(aitaPrimarySignInIsWellFormed("+77771234567", AitaAuthIdentifierKind.EMAIL, "p", true))
    }
    @Test fun authenticatorAndRecoveryFormatsAreBothAccepted() {
        for (code in listOf("123456", "123 456", "１２３４５６", "abcdef-123456", "ABCDEF123456")) {
            assertTrue(aitaSecondFactorIsWellFormed(code), code)
        }
    }
    @Test fun incompleteOrMixedGarbageDoesNotLookLikeAValidFactor() {
        for (code in listOf("", "00000", "0000000", "123456?", "q123456", "xxxxx-xxxxxx", "abcdef!123456")) {
            assertFalse(aitaSecondFactorIsWellFormed(code), code)
        }
    }
    @Test fun loginRequirementIsDistinctFromEnrollment() {
        assertTrue(aitaRequiresLoginSecondFactor(true, true))
        assertFalse(aitaRequiresLoginSecondFactor(true, false))
        assertFalse(aitaRequiresLoginSecondFactor(false, true))
        assertFalse(aitaRequiresLoginSecondFactor(false, false))
    }
    @Test fun oldServerModelDefaultsRemainProtected() {
        assertTrue(AitaAuthenticationSettingsDataModel(authenticatorEnabled = true).authenticatorRequiredForLogin)
        assertFalse(AitaAuthenticationSettingsDataModel(authenticatorEnabled = false).authenticatorRequiredForLogin)
        assertFalse(AitaAuthCapabilitiesDataModel().authenticatorLoginPolicyEnabled)
        assertTrue(AitaTotpSetupConfirmRequestDataModel("setup", "123456").requireForLogin)
    }
}
