package kz.aita.server.auth

import java.util.Base64
import kotlin.test.*

class AitaAuthConfigurationTest {
    private fun production(vararg extras: Pair<String, String>) = mapOf(
        "AITA_ENV" to "production", "AITA_ADVANCED_AUTH_ENABLED" to "true"
    ) + extras

    private fun configured(vararg extras: Pair<String, String>) = production(
        "AITA_AUTH_CODE_PEPPER" to "p".repeat(128),
        "AITA_ACCOUNT_SECURITY_MASTER_KEY_B64" to Base64.getEncoder().encodeToString(ByteArray(32) { (it + 1).toByte() }),
        *extras
    )

    @Test
    fun missingOptionalProductionSecretsDoNotAbortServerStartup() {
        val config = AdvancedAuthConfig.load(production()::get)
        assertTrue(config.enabled)
        assertFalse(config.securityConfigured)
        assertFalse(config.advancedReady)
        assertFalse(config.emailReady)
        assertTrue(config.encryptionKey.isEmpty())
        assertEquals(64, config.codePepper.size)
        assertTrue(config.configurationIssue.orEmpty().contains("AITA_AUTH_CODE_PEPPER"))
    }

    @Test
    fun invalidOrWrongLengthEncryptionKeyDoesNotBecomeADevelopmentKey() {
        listOf("not base64!", Base64.getEncoder().encodeToString(ByteArray(16))).forEach { invalid ->
            val config = AdvancedAuthConfig.load(configured("AITA_ACCOUNT_SECURITY_MASTER_KEY_B64" to invalid)::get)
            assertFalse(config.securityConfigured)
            assertTrue(config.encryptionKey.isEmpty())
            assertTrue(config.configurationIssue.orEmpty().contains("AITA_ACCOUNT_SECURITY_MASTER_KEY_B64"))
        }
    }

    @Test
    fun missingEmailProviderDoesNotDisableConfiguredAuthenticatorSecurity() {
        val config = AdvancedAuthConfig.load(configured()::get)
        assertTrue(config.securityConfigured)
        assertTrue(config.advancedReady)
        assertFalse(config.emailConfigured)
        assertFalse(config.emailReady)
    }

    @Test
    fun validSecretsArePreservedExactlyAcrossRestarts() {
        val values = configured("AITA_RESEND_API_KEY" to "re_test", "AITA_AUTH_EMAIL_FROM" to "AITA <security@auth.example.com>")
        val first = AdvancedAuthConfig.load(values::get)
        val second = AdvancedAuthConfig.load(values::get)
        assertTrue(first.emailReady)
        assertContentEquals("p".repeat(128).toByteArray(), first.codePepper)
        assertContentEquals(ByteArray(32) { (it + 1).toByte() }, first.encryptionKey)
        assertContentEquals(first.encryptionKey, second.encryptionKey)
        assertContentEquals(first.codePepper, second.codePepper)
        assertNull(first.configurationIssue)
    }

    @Test
    fun turningOffNewEnrollmentDoesNotDiscardExistingSecondFactorKeys() {
        val config = AdvancedAuthConfig.load(configured("AITA_ADVANCED_AUTH_ENABLED" to "false")::get)
        assertFalse(config.enabled)
        assertFalse(config.advancedReady)
        assertTrue(config.securityConfigured)
        assertEquals(32, config.encryptionKey.size)
    }

    @Test
    fun productionNeverUsesTheFixedDevelopmentPepper() {
        val first = AdvancedAuthConfig.load(production()::get)
        val second = AdvancedAuthConfig.load(production()::get)
        assertFalse(first.codePepper.contentEquals(second.codePepper))
        assertFalse(first.codePepper.contentEquals(AdvancedAuthConfig.load({ null }).codePepper))
    }

    @Test
    fun yamlProductionEnvironmentPreventsDevelopmentKeyFallback() {
        val config = AdvancedAuthConfig.load(readValue = { null }, environmentName = "production")
        assertFalse(config.securityConfigured)
        assertFalse(config.enabled)
        assertTrue(config.encryptionKey.isEmpty())
    }

    @Test
    fun numericBoundsAreAppliedBeforeNarrowingOrMultiplication() {
        val high = AdvancedAuthConfig.load(configured(
            "AITA_AUTH_MAX_CODE_ATTEMPTS" to Long.MAX_VALUE.toString(),
            "AITA_AUTH_CODE_TTL_SECONDS" to Long.MAX_VALUE.toString(),
            "AITA_AUTH_RESEND_COOLDOWN_SECONDS" to Long.MAX_VALUE.toString()
        )::get)
        assertEquals(10, high.maxAttempts)
        assertEquals(1_800_000L, high.codeTtlMillis)
        assertEquals(600_000L, high.resendCooldownMillis)
        val low = AdvancedAuthConfig.load(configured("AITA_AUTH_MAX_CODE_ATTEMPTS" to Long.MIN_VALUE.toString())::get)
        assertEquals(3, low.maxAttempts)
    }

    @Test
    fun providerCredentialsCannotAdvertiseFeaturesWithoutSecurityKeys() {
        val config = AdvancedAuthConfig.load(production(
            "AITA_RESEND_API_KEY" to "re_test",
            "AITA_AUTH_EMAIL_FROM" to "AITA <security@auth.example.com>"
        )::get)
        assertTrue(config.emailConfigured)
        val capabilities = config.capabilities()
        assertTrue(capabilities.passwordLoginEnabled)
        assertFalse(capabilities.enabled)
        assertFalse(capabilities.emailCodeLoginEnabled)
        assertFalse(capabilities.passwordRecoveryEnabled)
        assertFalse(capabilities.authenticatorTwoFactorEnabled)
        assertFalse(capabilities.phoneLoginAliasEnabled)
    }

    @Test
    fun missingProviderStillAdvertisesPasswordAndConfiguredAuthenticator() {
        val config = AdvancedAuthConfig.load(configured()::get)
        val capabilities = config.capabilities()
        assertTrue(capabilities.enabled)
        assertTrue(capabilities.passwordLoginEnabled)
        assertTrue(capabilities.authenticatorTwoFactorEnabled)
        assertFalse(capabilities.emailCodeLoginEnabled)
        assertFalse(capabilities.passwordRecoveryEnabled)
        assertFalse(capabilities.phoneLoginAliasEnabled)
        config.requireAdvancedAuthentication()
        config.requireSecurityConfigured()
    }

    @Test
    fun configuredCapabilitiesUseTheSameLifetimeValuesAsTheService() {
        val config = AdvancedAuthConfig.load(configured(
            "AITA_RESEND_API_KEY" to "re_test",
            "AITA_AUTH_EMAIL_FROM" to "AITA <security@auth.example.com>",
            "AITA_AUTH_CODE_TTL_SECONDS" to "180",
            "AITA_AUTH_RESEND_COOLDOWN_SECONDS" to "90"
        )::get)
        val capabilities = config.capabilities()
        assertTrue(capabilities.enabled)
        assertTrue(capabilities.passwordLoginEnabled)
        assertTrue(capabilities.emailCodeLoginEnabled)
        assertTrue(capabilities.passwordRecoveryEnabled)
        assertTrue(capabilities.authenticatorTwoFactorEnabled)
        assertTrue(capabilities.phoneLoginAliasEnabled)
        assertEquals(6, capabilities.codeLength)
        assertEquals(180L, capabilities.codeTtlSeconds)
        assertEquals(90L, capabilities.resendCooldownSeconds)
        config.requireEmailAuthentication()
        config.requireEmailAuthentication(recovery = true)
    }

    @Test
    fun invalidSecurityNeverFallsThroughToProviderOrEncryption() {
        val config = AdvancedAuthConfig.load(configured(
            "AITA_ACCOUNT_SECURITY_MASTER_KEY_B64" to "not-a-base64-key",
            "AITA_RESEND_API_KEY" to "re_test",
            "AITA_AUTH_EMAIL_FROM" to "AITA <security@auth.example.com>"
        )::get)
        assertEquals(AitaAuthUnavailableReason.SECURITY_CONFIGURATION,
            assertFailsWith<AitaAuthUnavailableException> { config.requireSecurityConfigured() }.reason)
        assertEquals(AitaAuthUnavailableReason.SECURITY_CONFIGURATION,
            assertFailsWith<AitaAuthUnavailableException> { config.requireAdvancedAuthentication() }.reason)
        assertEquals(AitaAuthUnavailableReason.SECURITY_CONFIGURATION,
            assertFailsWith<AitaAuthUnavailableException> { config.requireEmailAuthentication() }.reason)
    }

    @Test
    fun disablingOptionalFeaturesDoesNotBypassExistingSecondFactors() {
        val config = AdvancedAuthConfig.load(configured(
            "AITA_ADVANCED_AUTH_ENABLED" to "false",
            "AITA_RESEND_API_KEY" to "re_test",
            "AITA_AUTH_EMAIL_FROM" to "AITA <security@auth.example.com>"
        )::get)
        // Existing TOTP verification needs keys, not permission to enroll a new factor.
        config.requireSecurityConfigured()
        assertEquals(AitaAuthUnavailableReason.DISABLED,
            assertFailsWith<AitaAuthUnavailableException> { config.requireAdvancedAuthentication() }.reason)
        assertEquals(AitaAuthUnavailableReason.DISABLED,
            assertFailsWith<AitaAuthUnavailableException> { config.requireEmailAuthentication() }.reason)
        assertTrue(config.capabilities().passwordLoginEnabled)
        assertFalse(config.capabilities().enabled)
    }

    @Test
    fun disablingOptionalFeaturesWithoutKeysDoesNotPermitSecondFactorVerification() {
        val config = AdvancedAuthConfig.load(production("AITA_ADVANCED_AUTH_ENABLED" to "false")::get)
        assertTrue(config.capabilities().passwordLoginEnabled)
        assertEquals(AitaAuthUnavailableReason.SECURITY_CONFIGURATION,
            assertFailsWith<AitaAuthUnavailableException> { config.requireSecurityConfigured() }.reason)
    }

    @Test
    fun disablingRecoveryDoesNotDisableSignInCodes() {
        val config = AdvancedAuthConfig.load(configured(
            "AITA_PASSWORD_RECOVERY_ENABLED" to "false",
            "AITA_RESEND_API_KEY" to "re_test",
            "AITA_AUTH_EMAIL_FROM" to "AITA <security@auth.example.com>"
        )::get)
        config.requireEmailAuthentication()
        assertTrue(config.capabilities().emailCodeLoginEnabled)
        assertFalse(config.capabilities().passwordRecoveryEnabled)
        assertEquals(AitaAuthUnavailableReason.RECOVERY_DISABLED,
            assertFailsWith<AitaAuthUnavailableException> { config.requireEmailAuthentication(recovery = true) }.reason)
        assertEquals(AitaAuthUnavailableReason.RECOVERY_DISABLED,
            assertFailsWith<AitaAuthUnavailableException> { config.requireAdvancedAuthentication(recovery = true) }.reason)
    }

    @Test
    fun verificationDoesNotRequireAnEmailProviderAfterTheCodeWasDelivered() {
        val config = AdvancedAuthConfig.load(configured()::get)
        config.requireAdvancedAuthentication()
        config.requireAdvancedAuthentication(recovery = true)
        assertEquals(AitaAuthUnavailableReason.EMAIL_CONFIGURATION,
            assertFailsWith<AitaAuthUnavailableException> { config.requireEmailAuthentication() }.reason)
    }

    @Test
    fun emailConfigurationRequiresProviderKeyAndSender() {
        val base = configured(
            "AITA_RESEND_API_KEY" to "re_test",
            "AITA_AUTH_EMAIL_FROM" to "AITA <security@auth.example.com>"
        )
        listOf(
            base + ("AITA_AUTH_EMAIL_PROVIDER" to "unsupported"),
            base + ("AITA_RESEND_API_KEY" to "  "),
            base + ("AITA_AUTH_EMAIL_FROM" to "  ")
        ).forEach { values ->
            val config = AdvancedAuthConfig.load(values::get)
            assertFalse(config.emailReady)
            assertEquals(AitaAuthUnavailableReason.EMAIL_CONFIGURATION,
                assertFailsWith<AitaAuthUnavailableException> { config.requireEmailAuthentication() }.reason)
        }
    }

    @Test
    fun environmentAndProviderNamesAreTrimmedAndCaseInsensitive() {
        val config = AdvancedAuthConfig.load(configured(
            "AITA_ENV" to " PrOdUcTiOn ",
            "AITA_AUTH_EMAIL_PROVIDER" to " ReSeNd ",
            "AITA_RESEND_API_KEY" to " re_test ",
            "AITA_AUTH_EMAIL_FROM" to " AITA <security@auth.example.com> "
        )::get)
        assertTrue(config.emailReady)
        assertEquals("re_test", config.resendApiKey)
        assertEquals("AITA <security@auth.example.com>", config.fromEmail)
    }
}
