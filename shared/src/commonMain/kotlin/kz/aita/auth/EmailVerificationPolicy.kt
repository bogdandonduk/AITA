package kz.aita.auth

import kotlinx.serialization.Serializable

@Serializable
enum class AitaLoginSecondFactor { NONE, AUTHENTICATOR, EMAIL }
@Serializable
enum class AitaEmailDestination { MAIN, EXTRA }
@Serializable
data class AitaEmailDestinationOption(val destination: AitaEmailDestination, val maskedAddress: String)
@Serializable
data class AitaLoginEmailFactorRequest(val flowId: String, val destination: AitaEmailDestination, val locale: String = "en")
@Serializable
data class AitaSecurityEmailProof(val flowId: String, val code: String)
@Serializable
enum class AitaSecurityEmailAction { LOGIN_POLICY, ADD_EMAIL, REMOVE_EMAIL, TOTP_SETUP, PROFILE }
@Serializable
data class AitaSecurityEmailRequest(
    val action: AitaSecurityEmailAction,
    val target: String,
    val currentPassword: String,
    val expectedSecurityRevision: Long,
    val locale: String = "en"
)
@Serializable
data class AitaLoginPolicyRequest(
    val method: AitaLoginSecondFactor,
    val currentPassword: String,
    val secondFactorCode: String = "",
    val expectedSecurityRevision: Long,
    val emailProof: AitaSecurityEmailProof? = null
)

fun aitaLoginSecondFactor(totpEnabled: Boolean, totpRequired: Boolean, emailRequired: Boolean): AitaLoginSecondFactor = when {
    totpEnabled && totpRequired -> AitaLoginSecondFactor.AUTHENTICATOR
    emailRequired -> AitaLoginSecondFactor.EMAIL
    else -> AitaLoginSecondFactor.NONE
}
val AitaAuthenticationSettingsDataModel.loginSecondFactor: AitaLoginSecondFactor
    get() = aitaLoginSecondFactor(authenticatorEnabled, authenticatorRequiredForLogin, emailRequiredForLogin)

/** At least one local-part character is ALWAYS hidden, including addresses like a@b.co.
 * Never return this for an anonymous phone lookup; a password proof must precede disclosure.
 */
fun maskAitaEmailDestination(raw: String): String {
    val email = normalizeAitaEmail(raw) ?: return ""
    val local = email.substringBefore('@')
    val visible = local.take((local.length - 1).coerceIn(0, 3))
    return visible + "*".repeat((local.length - visible.length).coerceAtLeast(3)) + "@" + email.substringAfter('@')
}

/** Shared by the profile editor and server; no submitted name/preference can change the proof target. */
fun aitaProfileSecurityTarget(phone: String, email: String, active: Boolean): String =
    listOf(normalizeAitaPhoneAlias(phone).orEmpty(), normalizeAitaEmail(email).orEmpty(), active.toString()).joinToString("\n")

fun canonicalAitaSecurityTarget(action: AitaSecurityEmailAction, value: String): String? {
    if (value.length > 1024) return null
    return when (action) {
        AitaSecurityEmailAction.LOGIN_POLICY -> AitaLoginSecondFactor.entries.firstOrNull { it.name == value }?.name
        AitaSecurityEmailAction.ADD_EMAIL, AitaSecurityEmailAction.REMOVE_EMAIL -> normalizeAitaEmail(value)
        AitaSecurityEmailAction.TOTP_SETUP -> value.takeIf { it.isEmpty() }
        AitaSecurityEmailAction.PROFILE -> {
            val fields = value.split('\n')
            if (fields.size != 3 || fields[2] !in listOf("true", "false") || normalizeAitaPhoneAlias(fields[0]) == null || normalizeAitaEmail(fields[1]) == null) null
            else aitaProfileSecurityTarget(fields[0], fields[1], fields[2] == "true")
        }
    }
}
