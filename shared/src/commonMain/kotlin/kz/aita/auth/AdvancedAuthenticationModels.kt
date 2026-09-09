package kz.aita.auth

import kotlinx.serialization.Serializable
import kz.aita.ClientDeviceInfoDataModel
import kz.aita.TokenPair

@Serializable
enum class AitaAuthIdentifierKind { EMAIL, PHONE }

@Serializable
enum class AitaAuthNextStep {
    AUTHENTICATED,
    EMAIL_CODE,
    TOTP,
    PASSWORD_RESET,
    COMPLETE
}

@Serializable
enum class AitaEmailCodePurpose {
    PASSWORDLESS_LOGIN,
    PASSWORD_RECOVERY,
    PHONE_ALIAS,
    EMAIL_ALIAS
}

@Serializable
enum class AitaPhoneAliasAction { ADD_OR_REPLACE, REMOVE }

@Serializable
data class AitaNormalizedLoginIdentifier(
    val kind: AitaAuthIdentifierKind,
    val value: String
)

@Serializable
data class AitaAuthCapabilitiesDataModel(
    val enabled: Boolean = false,
    val passwordLoginEnabled: Boolean = true,
    val emailCodeLoginEnabled: Boolean = false,
    val passwordRecoveryEnabled: Boolean = false,
    val authenticatorTwoFactorEnabled: Boolean = false,
    val phoneLoginAliasEnabled: Boolean = false,
    val codeLength: Int = 6,
    val codeTtlSeconds: Long = 600,
    val resendCooldownSeconds: Long = 60,
    // Provider-wide state only: never expose delivery/account existence for an anonymous flow.
    val emailDeliveryUnavailable: Boolean = false,
    val additionalEmailLoginEnabled: Boolean = false
)

@Serializable
data class AitaAuthFlowDataModel(
    val flowId: String = "",
    val nextStep: AitaAuthNextStep = AitaAuthNextStep.COMPLETE,
    val maskedDestination: String = "",
    val expiresAtMillis: Long = 0L,
    val resendAfterMillis: Long = 0L,
    val tokenPair: TokenPair? = null,
    val resetTicket: String = "",
    val recoveryCodes: List<String> = emptyList(),
    val serverTimeMillis: Long = 0L
)

@Serializable
data class AitaPasswordLoginRequestDataModel(
    val identifier: String,
    val password: String,
    val deviceInfo: ClientDeviceInfoDataModel? = null
)

@Serializable
data class AitaEmailCodeRequestDataModel(
    val identifier: String,
    val locale: String = "en",
    val deviceInfo: ClientDeviceInfoDataModel? = null
)

@Serializable
data class AitaEmailCodeVerifyRequestDataModel(
    val flowId: String,
    val code: String,
    val deviceInfo: ClientDeviceInfoDataModel? = null
)

@Serializable
data class AitaEmailCodeResendRequestDataModel(
    val flowId: String,
    val locale: String = "en"
)

@Serializable
data class AitaTotpLoginRequestDataModel(
    val flowId: String,
    val code: String,
    val deviceInfo: ClientDeviceInfoDataModel? = null
)

@Serializable
data class AitaPasswordRecoveryResetRequestDataModel(
    val flowId: String,
    val resetTicket: String,
    val newPassword: String
)

@Serializable
data class AitaAuthenticationSettingsDataModel(
    val email: String = "",
    val emailVerified: Boolean = false,
    val phoneLoginAlias: String? = null,
    val phoneLoginAliasVerified: Boolean = false,
    val authenticatorEnabled: Boolean = false,
    val recoveryCodesRemaining: Int = 0,
    val securityRevision: Long = 0L,
    val additionalLoginEmails: List<String> = emptyList()
)

@Serializable
data class AitaTotpSetupDataModel(
    val setupId: String,
    val secret: String,
    val otpauthUri: String,
    val expiresAtMillis: Long,
    val serverTimeMillis: Long = 0L
)

@Serializable
data class AitaTotpSetupConfirmRequestDataModel(
    val setupId: String,
    val code: String
)

@Serializable
data class AitaSensitiveSecurityActionRequestDataModel(
    val currentPassword: String,
    val secondFactorCode: String = ""
)

@Serializable
data class AitaPhoneAliasRequestDataModel(
    val action: AitaPhoneAliasAction,
    val phoneNumber: String = "",
    val currentPassword: String,
    val secondFactorCode: String = "",
    val locale: String = "en"
)

@Serializable
data class AitaPhoneAliasConfirmRequestDataModel(
    val flowId: String,
    val code: String
)

/** New identity is pending until an authenticated confirmation succeeds. */
@Serializable
data class AitaEmailAliasRequestDataModel(
    val email: String,
    val currentPassword: String,
    val secondFactorCode: String = "",
    val locale: String = "en"
)

@Serializable
data class AitaEmailAliasConfirmRequestDataModel(val flowId: String, val code: String)

@Serializable
data class AitaEmailAliasRemoveRequestDataModel(
    val email: String,
    val currentPassword: String,
    val secondFactorCode: String = ""
)

const val AITA_MAX_ADDITIONAL_LOGIN_EMAILS = 5

fun normalizeAitaLoginIdentifier(raw: String): AitaNormalizedLoginIdentifier? {
    val clean = raw.trim()
    if (clean.isBlank()) return null
    return if ('@' in clean) {
        normalizeAitaEmail(clean)?.let { AitaNormalizedLoginIdentifier(AitaAuthIdentifierKind.EMAIL, it) }
    } else {
        normalizeAitaPhoneAlias(clean)?.let { AitaNormalizedLoginIdentifier(AitaAuthIdentifierKind.PHONE, it) }
    }
}

fun normalizeAitaEmail(raw: String): String? {
    val value = raw.trim().lowercase()
    if (value.length !in 5..254) return null
    if (value.count { it == '@' } != 1) return null
    val local = value.substringBefore('@')
    val domain = value.substringAfter('@')
    if (local.isBlank() || domain.isBlank() || '.' !in domain) return null
    if (local.startsWith('.') || local.endsWith('.') || ".." in local) return null
    if (domain.startsWith('.') || domain.endsWith('.') || ".." in domain) return null
    if (value.any { it.isWhitespace() || it.isISOControl() }) return null
    return value
}

fun normalizeAitaPhoneAlias(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return null
    if (trimmed.any { it.digitToIntOrNull() == null && !it.isWhitespace() && it !in "+()-./" }) return null
    if (trimmed.count { it == '+' } > 1 || ('+' in trimmed && !trimmed.startsWith('+'))) return null
    val hasPlus = trimmed.startsWith('+')
    val digits = aitaAuthCodeDigits(trimmed)
    if (digits.length !in 7..15) return null
    val canonicalDigits = when {
        hasPlus -> digits
        digits.length == 11 && digits.startsWith("8") -> "7" + digits.drop(1)
        else -> digits
    }
    return "+$canonicalDigits"
}

fun normalizeAitaOneTimeCode(raw: String, expectedLength: Int = 6): String? =
    aitaAuthCodeDigits(raw).takeIf { it.length == expectedLength }

fun normalizeAitaRecoveryCode(raw: String): String =
    raw.trim().uppercase().filter(Char::isLetterOrDigit)

/** Accept pasted/localized decimal digits, but send the same ASCII digits used by the server HMAC. */
fun aitaAuthCodeDigits(raw: String): String = buildString {
    raw.forEach { character -> character.digitToIntOrNull()?.let { append(('0'.code + it).toChar()) } }
}
