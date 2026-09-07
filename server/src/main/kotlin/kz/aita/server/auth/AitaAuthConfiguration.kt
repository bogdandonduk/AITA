package kz.aita.server

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.Locale

/** Optional authentication configuration must never prevent the ordinary server from starting. */
internal class AdvancedAuthConfig(
    val enabled: Boolean,
    val passwordRecoveryEnabled: Boolean,
    val emailProvider: String,
    val resendApiKey: String,
    val fromEmail: String,
    val replyTo: String,
    val codePepper: ByteArray,
    val encryptionKey: ByteArray,
    val securityConfigured: Boolean,
    val configurationIssue: String?,
    val codeTtlMillis: Long,
    val resetTtlMillis: Long,
    val resendCooldownMillis: Long,
    val maxAttempts: Int,
    val issuerName: String
) {
    val emailConfigured: Boolean
        get() = emailProvider.equals("resend", true) && resendApiKey.isNotBlank() && fromEmail.isNotBlank()

    val advancedReady: Boolean get() = enabled && securityConfigured
    val emailReady: Boolean get() = advancedReady && emailConfigured

    companion object {
        fun load(
            readValue: (String) -> String? = { name ->
                System.getenv(name)?.takeIf(String::isNotBlank) ?: System.getProperty(name)
            },
            environmentName: String? = null
        ): AdvancedAuthConfig {
            fun value(name: String): String? = readValue(name)?.trim()?.takeIf(String::isNotEmpty)
            fun flag(name: String, default: Boolean): Boolean = when (value(name)?.lowercase(Locale.ROOT)) {
                "1", "true", "yes", "on" -> true
                "0", "false", "no", "off" -> false
                else -> default
            }
            fun number(name: String, default: Long): Long = value(name)?.toLongOrNull() ?: default

            val production = (environmentName?.trim()?.takeIf(String::isNotBlank) ?: value("AITA_ENV"))?.lowercase(Locale.ROOT) in setOf("production", "prod", "stage", "staging", "cloud")
            val enabled = flag("AITA_ADVANCED_AUTH_ENABLED", !production)
            val suppliedPepper = value("AITA_AUTH_CODE_PEPPER")
            val suppliedKey = value("AITA_ACCOUNT_SECURITY_MASTER_KEY_B64")
            val pepperText = suppliedPepper ?: if (!production) {
                "aita-local-development-auth-code-pepper-change-before-production-2026"
            } else null
            val decodedKey = if (suppliedKey == null && !production) {
                MessageDigest.getInstance("SHA-256").digest("aita-local-development-account-security-key".toByteArray(StandardCharsets.UTF_8))
            } else suppliedKey?.let { runCatching { Base64.getDecoder().decode(it) }.getOrNull() }
            val pepperValid = pepperText != null && pepperText.length >= 64
            val keyValid = decodedKey?.size == 32
            val issue = when {
                !pepperValid -> "AITA_AUTH_CODE_PEPPER must contain at least 64 characters"
                !keyValid -> "AITA_ACCOUNT_SECURITY_MASTER_KEY_B64 must decode to exactly 32 bytes"
                else -> null
            }

            return AdvancedAuthConfig(
                enabled = enabled,
                passwordRecoveryEnabled = flag("AITA_PASSWORD_RECOVERY_ENABLED", enabled),
                emailProvider = value("AITA_AUTH_EMAIL_PROVIDER") ?: "resend",
                resendApiKey = value("AITA_RESEND_API_KEY").orEmpty(),
                fromEmail = value("AITA_AUTH_EMAIL_FROM").orEmpty(),
                replyTo = value("AITA_AUTH_EMAIL_REPLY_TO").orEmpty(),
                // With optional security unconfigured, password rate-limit/audit hashes use a
                // process-local random key. It is NEVER used for persistent codes or encryption:
                // every such operation is gated by securityConfigured/advancedReady below.
                codePepper = if (pepperValid) pepperText!!.toByteArray(StandardCharsets.UTF_8)
                    else ByteArray(64).also(SecureRandom()::nextBytes),
                encryptionKey = decodedKey?.takeIf { keyValid } ?: ByteArray(0),
                securityConfigured = pepperValid && keyValid,
                configurationIssue = issue,
                codeTtlMillis = number("AITA_AUTH_CODE_TTL_SECONDS", 600L).coerceIn(120L, 1800L) * 1000L,
                resetTtlMillis = number("AITA_AUTH_RESET_TTL_SECONDS", 600L).coerceIn(120L, 1800L) * 1000L,
                resendCooldownMillis = number("AITA_AUTH_RESEND_COOLDOWN_SECONDS", 60L).coerceIn(20L, 600L) * 1000L,
                maxAttempts = number("AITA_AUTH_MAX_CODE_ATTEMPTS", 5L).coerceIn(3L, 10L).toInt(),
                issuerName = value("AITA_AUTH_TOTP_ISSUER") ?: "AITA"
            )
        }
    }
}

internal enum class AitaAuthUnavailableReason { DISABLED, SECURITY_CONFIGURATION, EMAIL_CONFIGURATION, RECOVERY_DISABLED }

internal class AitaAuthUnavailableException(val reason: AitaAuthUnavailableReason) : IllegalStateException(reason.name)
