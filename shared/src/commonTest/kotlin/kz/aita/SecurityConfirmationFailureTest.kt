package kz.aita

import kotlin.test.*

class SecurityConfirmationFailureTest {
    @Test fun wrongActionCodeIsNotSessionExpiry() {
        assertTrue(isSecurityConfirmationFailure("/auth/security/totp/setup/confirm", eventMessage("auth.message.authenticator_code_is_invalid")))
        assertTrue(isSecurityConfirmationFailure("auth/security/totp/setup/start", eventMessage("auth.message.security_confirmation_failed")))
        assertFalse(isSecurityConfirmationFailure("auth/session", eventMessage("auth.message.security_confirmation_failed")))
        assertFalse(isSecurityConfirmationFailure("auth/security/settings", cloudSessionExpiredMessage()))
        assertFalse(isSecurityConfirmationFailure("auth/security/settings", null))
    }
}
