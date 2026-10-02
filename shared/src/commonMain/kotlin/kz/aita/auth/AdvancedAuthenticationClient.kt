package kz.aita.auth

import io.ktor.http.*
import kotlinx.coroutines.*
import kz.aita.*

object AitaAdvancedAuthenticationClient {
    val securityRevision = kotlinx.coroutines.flow.MutableStateFlow(0L)
    suspend fun capabilities(): ResponseDataModel<AitaAuthCapabilitiesDataModel> {
        val response = authRequest<AitaAuthCapabilitiesDataModel, Unit>(HttpMethod.Get, "auth/capabilities")
        // A positively identified older AITA origin can still accept password sign-in. Do not
        // mistake an absent optional route for an offline server or invent support for email codes.
        return if (canUseLegacyPasswordRoute(response.httpStatusCode, response.transportFailure)) {
            response.copy(payload = AitaAuthCapabilitiesDataModel(), negative = false)
        } else response
    }

    suspend fun passwordLogin(request: AitaPasswordLoginRequestDataModel): ResponseDataModel<AitaAuthFlowDataModel> {
        val serverUrl = resolvedServerUrlCandidates().firstOrNull()
        return passwordLoginWithCompatibility(
            request = request,
            advanced = { body -> authRequest(HttpMethod.Post, "auth/login/password", body, serverUrl) },
            // Pin both attempts to the same selected origin. Only an explicit 404/405 permits
            // this compatibility path; wrong credentials, TOTP, 5xx and timeouts never do.
            legacy = { body -> authRequest(HttpMethod.Post, "auth/logIn", body, serverUrl) }
        )
    }

    suspend fun requestLoginCode(request: AitaEmailCodeRequestDataModel) =
        authRequest<AitaAuthFlowDataModel, AitaEmailCodeRequestDataModel>(HttpMethod.Post, "auth/login/code/request", request.copy(locale = authRequestLocale(request.locale)))

    suspend fun verifyLoginCode(request: AitaEmailCodeVerifyRequestDataModel) =
        authRequest<AitaAuthFlowDataModel, AitaEmailCodeVerifyRequestDataModel>(HttpMethod.Post, "auth/login/code/verify", request)

    suspend fun resendLoginCode(request: AitaEmailCodeResendRequestDataModel) =
        authRequest<AitaAuthFlowDataModel, AitaEmailCodeResendRequestDataModel>(HttpMethod.Post, "auth/login/code/resend", request.copy(locale = authRequestLocale(request.locale)))

    suspend fun completeTotpLogin(request: AitaTotpLoginRequestDataModel) =
        authRequest<AitaAuthFlowDataModel, AitaTotpLoginRequestDataModel>(HttpMethod.Post, "auth/login/totp", request)

    suspend fun authenticatorLogin(request: AitaAuthenticatorLoginRequestDataModel) =
        authRequest<AitaAuthFlowDataModel, AitaAuthenticatorLoginRequestDataModel>(HttpMethod.Post, "auth/login/authenticator", request)

    suspend fun completeAuthenticatorPassword(request: AitaAuthenticatorPasswordRequestDataModel) =
        authRequest<AitaAuthFlowDataModel, AitaAuthenticatorPasswordRequestDataModel>(HttpMethod.Post, "auth/login/authenticator/password", request)

    suspend fun requestAuthenticatorRecovery(request: AitaAuthenticatorRecoveryRequestDataModel) =
        authRequest<AitaAuthFlowDataModel, AitaAuthenticatorRecoveryRequestDataModel>(HttpMethod.Post, "auth/authenticator-recovery/request", request.copy(locale = authRequestLocale(request.locale)))

    suspend fun resendAuthenticatorRecovery(request: AitaEmailCodeResendRequestDataModel) =
        authRequest<AitaAuthFlowDataModel, AitaEmailCodeResendRequestDataModel>(HttpMethod.Post, "auth/authenticator-recovery/resend", request.copy(locale = authRequestLocale(request.locale)))

    suspend fun confirmAuthenticatorRecovery(request: AitaEmailCodeVerifyRequestDataModel) =
        authRequest<AitaAuthFlowDataModel, AitaEmailCodeVerifyRequestDataModel>(HttpMethod.Post, "auth/authenticator-recovery/confirm", request)

    suspend fun requestPasswordRecovery(request: AitaEmailCodeRequestDataModel) =
        authRequest<AitaAuthFlowDataModel, AitaEmailCodeRequestDataModel>(HttpMethod.Post, "auth/password-recovery/request", request.copy(locale = authRequestLocale(request.locale)))

    suspend fun verifyPasswordRecovery(request: AitaEmailCodeVerifyRequestDataModel) =
        authRequest<AitaAuthFlowDataModel, AitaEmailCodeVerifyRequestDataModel>(HttpMethod.Post, "auth/password-recovery/verify", request)

    suspend fun resendPasswordRecovery(request: AitaEmailCodeResendRequestDataModel) =
        authRequest<AitaAuthFlowDataModel, AitaEmailCodeResendRequestDataModel>(HttpMethod.Post, "auth/password-recovery/resend", request.copy(locale = authRequestLocale(request.locale)))

    suspend fun resetPassword(request: AitaPasswordRecoveryResetRequestDataModel) =
        authRequest<AitaAuthFlowDataModel, AitaPasswordRecoveryResetRequestDataModel>(HttpMethod.Post, "auth/password-recovery/reset", request)

    suspend fun requestLoginEmailFactor(request: AitaLoginEmailFactorRequest) =
        authRequest<AitaAuthFlowDataModel, AitaLoginEmailFactorRequest>(HttpMethod.Post, "auth/login/email-factor/request", request.copy(locale = authRequestLocale(request.locale)))

    suspend fun verifyLoginEmailFactor(request: AitaEmailCodeVerifyRequestDataModel) =
        authRequest<AitaAuthFlowDataModel, AitaEmailCodeVerifyRequestDataModel>(HttpMethod.Post, "auth/login/email-factor/verify", request)

    suspend fun requestSecurityEmail(request: AitaSecurityEmailRequest) =
        authRequest<AitaAuthFlowDataModel, AitaSecurityEmailRequest>(HttpMethod.Post, "auth/security/email-proof/request", request.copy(locale = authRequestLocale(request.locale)))

    suspend fun updateLoginPolicy(request: AitaLoginPolicyRequest) =
        authRequest<AitaAuthenticationSettingsDataModel, AitaLoginPolicyRequest>(HttpMethod.Post, "auth/security/login-policy", request)

    suspend fun deleteAccount(request: kz.aita.AccountDeletionRequest) = authRequest<String, kz.aita.AccountDeletionRequest>(HttpMethod.Post, "auth/security/account/delete", request)

    suspend fun settings() = authRequest<AitaAuthenticationSettingsDataModel, Unit>(HttpMethod.Get, "auth/security/settings")

    suspend fun startTotpSetup(request: AitaSensitiveSecurityActionRequestDataModel) =
        authRequest<AitaTotpSetupDataModel, AitaSensitiveSecurityActionRequestDataModel>(HttpMethod.Post, "auth/security/totp/setup/start", request)

    suspend fun confirmTotpSetup(request: AitaTotpSetupConfirmRequestDataModel) =
        authRequest<AitaAuthFlowDataModel, AitaTotpSetupConfirmRequestDataModel>(HttpMethod.Post, "auth/security/totp/setup/confirm", request)

    suspend fun disableTotp(request: AitaSensitiveSecurityActionRequestDataModel) =
        authRequest<AitaAuthenticationSettingsDataModel, AitaSensitiveSecurityActionRequestDataModel>(HttpMethod.Post, "auth/security/totp/disable", request)

    suspend fun updateTotpLoginPolicy(request: AitaTotpLoginPolicyRequestDataModel) =
        authRequest<AitaAuthenticationSettingsDataModel, AitaTotpLoginPolicyRequestDataModel>(HttpMethod.Post, "auth/security/totp/login-policy", request)

    suspend fun regenerateRecoveryCodes(request: AitaSensitiveSecurityActionRequestDataModel) =
        authRequest<AitaAuthFlowDataModel, AitaSensitiveSecurityActionRequestDataModel>(HttpMethod.Post, "auth/security/totp/recovery-codes/regenerate", request)

    suspend fun requestEmailAlias(request: AitaEmailAliasRequestDataModel) =
        authRequest<AitaAuthFlowDataModel, AitaEmailAliasRequestDataModel>(HttpMethod.Post, "auth/security/email/request", request.copy(locale = authRequestLocale(request.locale)))

    suspend fun resendEmailAlias(request: AitaEmailCodeResendRequestDataModel) =
        authRequest<AitaAuthFlowDataModel, AitaEmailCodeResendRequestDataModel>(HttpMethod.Post, "auth/security/email/resend", request.copy(locale = authRequestLocale(request.locale)))

    suspend fun confirmEmailAlias(request: AitaEmailAliasConfirmRequestDataModel) =
        authRequest<AitaAuthenticationSettingsDataModel, AitaEmailAliasConfirmRequestDataModel>(HttpMethod.Post, "auth/security/email/confirm", request)

    suspend fun removeEmailAlias(request: AitaEmailAliasRemoveRequestDataModel) =
        authRequest<AitaAuthenticationSettingsDataModel, AitaEmailAliasRemoveRequestDataModel>(HttpMethod.Post, "auth/security/email/remove", request)

    suspend fun requestPhoneAlias(request: AitaPhoneAliasRequestDataModel) =
        authRequest<AitaAuthFlowDataModel, AitaPhoneAliasRequestDataModel>(HttpMethod.Post, "auth/security/phone/request", request.copy(locale = authRequestLocale(request.locale)))

    suspend fun resendPhoneAlias(request: AitaEmailCodeResendRequestDataModel) =
        authRequest<AitaAuthFlowDataModel, AitaEmailCodeResendRequestDataModel>(HttpMethod.Post, "auth/security/phone/resend", request.copy(locale = authRequestLocale(request.locale)))

    suspend fun confirmPhoneAlias(request: AitaPhoneAliasConfirmRequestDataModel) =
        authRequest<AitaAuthenticationSettingsDataModel, AitaPhoneAliasConfirmRequestDataModel>(HttpMethod.Post, "auth/security/phone/confirm", request)

    private suspend inline fun <reified Response, reified Body> authRequest(
        method: HttpMethod,
        endpoint: String,
        body: Body? = null,
        serverUrl: String? = null
    ): ResponseDataModel<Response> = networkRequest<Response, Body>(
        method = method,
        serverUrl = serverUrl,
        endpointUrl = endpoint,
        body = body,
        expectedSessionGeneration = if (cloudEndpointRequiresAuthentication(endpoint)) currentAuthenticatedSessionGeneration() else null
    ).withAuthFailureMessage().also { response ->
        if (!response.negative && endpoint in setOf("auth/security/totp/setup/confirm", "auth/security/totp/disable",
                "auth/security/login-policy", "auth/security/totp/login-policy", "auth/security/email/confirm",
                "auth/security/email/remove", "auth/security/phone/confirm")) {
            securityRevision.value += 1L
        }
    }
}

internal suspend fun passwordLoginWithCompatibility(
    request: AitaPasswordLoginRequestDataModel,
    advanced: suspend (AitaPasswordLoginRequestDataModel) -> ResponseDataModel<AitaAuthFlowDataModel>,
    legacy: suspend (UserAuthLogInDataModel) -> ResponseDataModel<TokenPair>
): ResponseDataModel<AitaAuthFlowDataModel> {
    val response = advanced(request)
    if (!canUseLegacyPasswordRoute(response.httpStatusCode, response.transportFailure)) return response

    val legacyResponse = legacy(UserAuthLogInDataModel(request.identifier, request.password, request.deviceInfo))
    val tokens = legacyResponse.payload?.takeIf {
        !legacyResponse.negative && it.accessToken.isNotBlank() && it.refreshToken.isNotBlank()
    }
    return ResponseDataModel(
        message = legacyResponse.message,
        payload = tokens?.let { AitaAuthFlowDataModel(nextStep = AitaAuthNextStep.AUTHENTICATED, tokenPair = it) },
        negative = legacyResponse.negative || tokens == null,
        httpStatusCode = legacyResponse.httpStatusCode,
        transportFailure = legacyResponse.transportFailure
    )
}

/** Preserve provider/policy/credential errors; distinguish them from a genuine transport outage. */
internal fun <T> ResponseDataModel<T>.withAuthFailureMessage(): ResponseDataModel<T> {
    if (!negative) return this
    val key = when {
        transportFailure -> return this
        httpStatusCode == 404 || httpStatusCode == 405 || httpStatusCode == 501 -> "auth.client.server_update_required"
        httpStatusCode == 429 && !message.isNullOrEmpty() -> return this
        httpStatusCode == 429 -> "auth.client.too_many_attempts"
        httpStatusCode == 500 -> "auth.client.server_sign_in_failed"
        else -> return this
    }
    return copy(message = eventMessage(key))
}

/** Persisting credentials and loading the account are separate, retryable stages. */
suspend fun adoptAdvancedAuthenticationTokens(tokenPair: TokenPair): Long = withContext(Dispatchers.ourIo) {
    checkNotNull(setStoredUserAuthTokens) { "Credential storage is not initialized" }
    val generation = installAuthenticatedSession(tokenPair)
    check(getStoredUserAuthTokens?.invoke()?.accessToken == tokenPair.accessToken) { "Credentials could not be saved" }
    generation
}

private val authenticationCompletion = AuthenticationCompletionCoordinator<ResponseDataModel<UserAccountDataModel>>(
    scope = CoroutineScope(SupervisorJob() + Dispatchers.ourIo),
    complete = { generation ->
        try {
            withTimeoutOrNull(30_000L) {
                refreshUserAccountNow(generation, restoreCachedAccount = false, postFailure = false)
            } ?: ResponseDataModel(
                payload = null,
                negative = true, transportFailure = true,
                message = authenticationCompletionFailureMessage()
            )
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            ResponseDataModel(payload = null, negative = true, message = authenticationCompletionFailureMessage())
        }
    }
)

private fun authenticationCompletionFailureMessage() = eventMessage("message.sign_in_was_accepted_but_your_account_could_not_be_loaded")

suspend fun finishAdvancedAuthenticationSignIn(generation: Long): ResponseDataModel<UserAccountDataModel> {
    if (!authenticatedSessionGenerationIsCurrent(generation)) return cloudSessionExpiredResponse()
    return authenticationCompletion.await(generation)
}

suspend fun discardAdvancedAuthenticationSignIn(generation: Long) {
    authenticationCompletion.cancel(generation)
    withContext(Dispatchers.ourIo) { clearAuthenticatedSessionStorage(expectedGeneration = generation) }
}
