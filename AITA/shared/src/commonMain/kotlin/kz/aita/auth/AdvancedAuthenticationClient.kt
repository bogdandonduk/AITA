package kz.aita.auth

import io.ktor.client.plugins.auth.authProvider
import io.ktor.client.plugins.auth.providers.BearerAuthProvider
import io.ktor.http.HttpMethod
import kz.aita.*

object AitaAdvancedAuthenticationClient {
    suspend fun capabilities() = networkRequest<AitaAuthCapabilitiesDataModel, Unit>(
        method = HttpMethod.Get,
        endpointUrl = "auth/capabilities"
    )

    suspend fun passwordLogin(request: AitaPasswordLoginRequestDataModel) =
        networkRequest<AitaAuthFlowDataModel, AitaPasswordLoginRequestDataModel>(
            method = HttpMethod.Post,
            endpointUrl = "auth/login/password",
            body = request
        )

    suspend fun requestLoginCode(request: AitaEmailCodeRequestDataModel) =
        networkRequest<AitaAuthFlowDataModel, AitaEmailCodeRequestDataModel>(
            method = HttpMethod.Post,
            endpointUrl = "auth/login/code/request",
            body = request
        )

    suspend fun verifyLoginCode(request: AitaEmailCodeVerifyRequestDataModel) =
        networkRequest<AitaAuthFlowDataModel, AitaEmailCodeVerifyRequestDataModel>(
            method = HttpMethod.Post,
            endpointUrl = "auth/login/code/verify",
            body = request
        )

    suspend fun resendLoginCode(request: AitaEmailCodeResendRequestDataModel) =
        networkRequest<AitaAuthFlowDataModel, AitaEmailCodeResendRequestDataModel>(
            method = HttpMethod.Post,
            endpointUrl = "auth/login/code/resend",
            body = request
        )

    suspend fun completeTotpLogin(request: AitaTotpLoginRequestDataModel) =
        networkRequest<AitaAuthFlowDataModel, AitaTotpLoginRequestDataModel>(
            method = HttpMethod.Post,
            endpointUrl = "auth/login/totp",
            body = request
        )

    suspend fun requestPasswordRecovery(request: AitaEmailCodeRequestDataModel) =
        networkRequest<AitaAuthFlowDataModel, AitaEmailCodeRequestDataModel>(
            method = HttpMethod.Post,
            endpointUrl = "auth/password-recovery/request",
            body = request
        )

    suspend fun verifyPasswordRecovery(request: AitaEmailCodeVerifyRequestDataModel) =
        networkRequest<AitaAuthFlowDataModel, AitaEmailCodeVerifyRequestDataModel>(
            method = HttpMethod.Post,
            endpointUrl = "auth/password-recovery/verify",
            body = request
        )

    suspend fun resendPasswordRecovery(request: AitaEmailCodeResendRequestDataModel) =
        networkRequest<AitaAuthFlowDataModel, AitaEmailCodeResendRequestDataModel>(
            method = HttpMethod.Post,
            endpointUrl = "auth/password-recovery/resend",
            body = request
        )

    suspend fun resetPassword(request: AitaPasswordRecoveryResetRequestDataModel) =
        networkRequest<AitaAuthFlowDataModel, AitaPasswordRecoveryResetRequestDataModel>(
            method = HttpMethod.Post,
            endpointUrl = "auth/password-recovery/reset",
            body = request
        )

    suspend fun settings() = networkRequest<AitaAuthenticationSettingsDataModel, Unit>(
        method = HttpMethod.Get,
        endpointUrl = "auth/security/settings"
    )

    suspend fun startTotpSetup(request: AitaSensitiveSecurityActionRequestDataModel) =
        networkRequest<AitaTotpSetupDataModel, AitaSensitiveSecurityActionRequestDataModel>(
            method = HttpMethod.Post,
            endpointUrl = "auth/security/totp/setup/start",
            body = request
        )

    suspend fun confirmTotpSetup(request: AitaTotpSetupConfirmRequestDataModel) =
        networkRequest<AitaAuthFlowDataModel, AitaTotpSetupConfirmRequestDataModel>(
            method = HttpMethod.Post,
            endpointUrl = "auth/security/totp/setup/confirm",
            body = request
        )

    suspend fun disableTotp(request: AitaSensitiveSecurityActionRequestDataModel) =
        networkRequest<AitaAuthenticationSettingsDataModel, AitaSensitiveSecurityActionRequestDataModel>(
            method = HttpMethod.Post,
            endpointUrl = "auth/security/totp/disable",
            body = request
        )

    suspend fun regenerateRecoveryCodes(request: AitaSensitiveSecurityActionRequestDataModel) =
        networkRequest<AitaAuthFlowDataModel, AitaSensitiveSecurityActionRequestDataModel>(
            method = HttpMethod.Post,
            endpointUrl = "auth/security/totp/recovery-codes/regenerate",
            body = request
        )

    suspend fun requestPhoneAlias(request: AitaPhoneAliasRequestDataModel) =
        networkRequest<AitaAuthFlowDataModel, AitaPhoneAliasRequestDataModel>(
            method = HttpMethod.Post,
            endpointUrl = "auth/security/phone/request",
            body = request
        )

    suspend fun confirmPhoneAlias(request: AitaPhoneAliasConfirmRequestDataModel) =
        networkRequest<AitaAuthenticationSettingsDataModel, AitaPhoneAliasConfirmRequestDataModel>(
            method = HttpMethod.Post,
            endpointUrl = "auth/security/phone/confirm",
            body = request
        )
}

suspend fun adoptAdvancedAuthenticationTokens(tokenPair: TokenPair) {
    setStoredUserAuthTokens?.invoke(tokenPair)
    clearCloudAuthRequestMemory(tokenPair)
    markCloudAccessTokenValidated(tokenPair.accessToken)
    clearCloudSessionRefreshRequirementForNotifications(CLOUD_TRANSPORT_STATUS_REACHABLE)
    markCloudTransportReachableForNotifications(authenticated = true)
    httpClient.authProvider<BearerAuthProvider>()?.clearToken()
    getUser(forceLogOut = false)
}
