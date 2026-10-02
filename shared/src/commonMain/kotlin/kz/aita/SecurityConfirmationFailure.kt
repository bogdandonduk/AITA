package kz.aita

/** Compatibility with older servers: action proof failures do not revoke a cloud session. */
@PublishedApi
internal fun isSecurityConfirmationFailure(endpoint: String, messages: List<LocalizedStringDataModel>?): Boolean {
    if (!endpoint.trim('/').startsWith("auth/security/") || messages.isNullOrEmpty()) return false
    val known = listOf("auth.message.security_confirmation_failed", "auth.message.authenticator_code_is_invalid",
        "auth.message.request_a_new_code", "auth.message.the_confirmation_code_is_invalid_or_expired")
        .flatMap { eventMessage(it) }.map { it.value }.filter { it.isNotBlank() }.toSet()
    return messages.any { it.value in known }
}
