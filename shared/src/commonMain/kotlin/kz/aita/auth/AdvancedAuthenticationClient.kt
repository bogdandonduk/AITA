package kz.aita.auth

import io.ktor.http.*
import kotlinx.coroutines.*
import kz.aita.*

object AitaAdvancedAuthenticationClient {
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
        authRequest<AitaAuthFlowDataModel, AitaEmailCodeRequestDataModel>(HttpMethod.Post, "auth/login/code/request", request)

    suspend fun verifyLoginCode(request: AitaEmailCodeVerifyRequestDataModel) =
        authRequest<AitaAuthFlowDataModel, AitaEmailCodeVerifyRequestDataModel>(HttpMethod.Post, "auth/login/code/verify", request)

    suspend fun resendLoginCode(request: AitaEmailCodeResendRequestDataModel) =
        authRequest<AitaAuthFlowDataModel, AitaEmailCodeResendRequestDataModel>(HttpMethod.Post, "auth/login/code/resend", request)

    suspend fun completeTotpLogin(request: AitaTotpLoginRequestDataModel) =
        authRequest<AitaAuthFlowDataModel, AitaTotpLoginRequestDataModel>(HttpMethod.Post, "auth/login/totp", request)

    suspend fun authenticatorLogin(request: AitaAuthenticatorLoginRequestDataModel) =
        authRequest<AitaAuthFlowDataModel, AitaAuthenticatorLoginRequestDataModel>(HttpMethod.Post, "auth/login/authenticator", request)

    suspend fun completeAuthenticatorPassword(request: AitaAuthenticatorPasswordRequestDataModel) =
        authRequest<AitaAuthFlowDataModel, AitaAuthenticatorPasswordRequestDataModel>(HttpMethod.Post, "auth/login/authenticator/password", request)

    suspend fun requestAuthenticatorRecovery(request: AitaAuthenticatorRecoveryRequestDataModel) =
        authRequest<AitaAuthFlowDataModel, AitaAuthenticatorRecoveryRequestDataModel>(HttpMethod.Post, "auth/authenticator-recovery/request", request)

    suspend fun resendAuthenticatorRecovery(request: AitaEmailCodeResendRequestDataModel) =
        authRequest<AitaAuthFlowDataModel, AitaEmailCodeResendRequestDataModel>(HttpMethod.Post, "auth/authenticator-recovery/resend", request)

    suspend fun confirmAuthenticatorRecovery(request: AitaEmailCodeVerifyRequestDataModel) =
        authRequest<AitaAuthFlowDataModel, AitaEmailCodeVerifyRequestDataModel>(HttpMethod.Post, "auth/authenticator-recovery/confirm", request)

    suspend fun requestPasswordRecovery(request: AitaEmailCodeRequestDataModel) =
        authRequest<AitaAuthFlowDataModel, AitaEmailCodeRequestDataModel>(HttpMethod.Post, "auth/password-recovery/request", request)

    suspend fun verifyPasswordRecovery(request: AitaEmailCodeVerifyRequestDataModel) =
        authRequest<AitaAuthFlowDataModel, AitaEmailCodeVerifyRequestDataModel>(HttpMethod.Post, "auth/password-recovery/verify", request)

    suspend fun resendPasswordRecovery(request: AitaEmailCodeResendRequestDataModel) =
        authRequest<AitaAuthFlowDataModel, AitaEmailCodeResendRequestDataModel>(HttpMethod.Post, "auth/password-recovery/resend", request)

    suspend fun resetPassword(request: AitaPasswordRecoveryResetRequestDataModel) =
        authRequest<AitaAuthFlowDataModel, AitaPasswordRecoveryResetRequestDataModel>(HttpMethod.Post, "auth/password-recovery/reset", request)

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
        authRequest<AitaAuthFlowDataModel, AitaEmailAliasRequestDataModel>(HttpMethod.Post, "auth/security/email/request", request)

    suspend fun resendEmailAlias(request: AitaEmailCodeResendRequestDataModel) =
        authRequest<AitaAuthFlowDataModel, AitaEmailCodeResendRequestDataModel>(HttpMethod.Post, "auth/security/email/resend", request)

    suspend fun confirmEmailAlias(request: AitaEmailAliasConfirmRequestDataModel) =
        authRequest<AitaAuthenticationSettingsDataModel, AitaEmailAliasConfirmRequestDataModel>(HttpMethod.Post, "auth/security/email/confirm", request)

    suspend fun removeEmailAlias(request: AitaEmailAliasRemoveRequestDataModel) =
        authRequest<AitaAuthenticationSettingsDataModel, AitaEmailAliasRemoveRequestDataModel>(HttpMethod.Post, "auth/security/email/remove", request)

    suspend fun requestPhoneAlias(request: AitaPhoneAliasRequestDataModel) =
        authRequest<AitaAuthFlowDataModel, AitaPhoneAliasRequestDataModel>(HttpMethod.Post, "auth/security/phone/request", request)

    suspend fun resendPhoneAlias(request: AitaEmailCodeResendRequestDataModel) =
        authRequest<AitaAuthFlowDataModel, AitaEmailCodeResendRequestDataModel>(HttpMethod.Post, "auth/security/phone/resend", request)

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
    ).withAuthFailureMessage()
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
    val text = when {
        transportFailure -> return this
        httpStatusCode == 404 || httpStatusCode == 405 || httpStatusCode == 501 -> arrayOf(
            "Update the AITA server to use this sign-in method.",
            "Обновите сервер AITA для этого способа входа.",
            "Бұл кіру тәсілі үшін AITA серверін жаңартыңыз."
        )
        httpStatusCode == 429 && !message.isNullOrEmpty() -> return this
        httpStatusCode == 429 -> arrayOf(
            "Too many attempts. Try again later.",
            "Слишком много попыток. Попробуйте позже.",
            "Тым көп әрекет жасалды. Кейінірек қайталаңыз."
        )
        httpStatusCode == 500 -> arrayOf(
            "The server could not complete sign-in. Try again or contact the administrator.",
            "Сервер не смог выполнить вход. Повторите или обратитесь к администратору.",
            "Сервер кіруді аяқтай алмады. Қайталаңыз немесе әкімшіге хабарласыңыз."
        )
        else -> return this
    }
    return copy(message = listOf(
        LocalizedStringDataModel("main", text[0]), LocalizedStringDataModel("en", text[0]),
        LocalizedStringDataModel("ru", text[1]), LocalizedStringDataModel("kk", text[2])
    ))
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

private fun authenticationCompletionFailureMessage() = listOf(
    LocalizedStringDataModel("main", "Sign-in was accepted, but your account could not be loaded. Press Continue to retry."),
    LocalizedStringDataModel("en", "Sign-in was accepted, but your account could not be loaded. Press Continue to retry."),
    LocalizedStringDataModel("ru", "Вход подтверждён, но аккаунт не загрузился. Нажмите «Продолжить», чтобы повторить."),
    LocalizedStringDataModel("kk", "Кіру расталды, бірақ аккаунт жүктелмеді. Қайталау үшін «Жалғастыру» түймесін басыңыз.")
)

suspend fun finishAdvancedAuthenticationSignIn(generation: Long): ResponseDataModel<UserAccountDataModel> {
    if (!authenticatedSessionGenerationIsCurrent(generation)) return cloudSessionExpiredResponse()
    return authenticationCompletion.await(generation)
}

suspend fun discardAdvancedAuthenticationSignIn(generation: Long) {
    authenticationCompletion.cancel(generation)
    withContext(Dispatchers.ourIo) { clearAuthenticatedSessionStorage(expectedGeneration = generation) }
}
