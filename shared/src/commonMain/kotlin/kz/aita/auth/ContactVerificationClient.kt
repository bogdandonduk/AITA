package kz.aita.auth

import io.ktor.http.HttpMethod
import kz.aita.*

object AitaContactVerificationClient {
    private fun endpoint(registration: Boolean, action: String): String =
        (if (registration) "auth/registration/email/" else "auth/security/contact-verification/") + action

    suspend fun request(body: AitaContactCodeRequest, generation: Long = currentAuthenticatedSessionGeneration()) =
        networkRequest<AitaAuthFlowDataModel, AitaContactCodeRequest>(
            HttpMethod.Post, endpointUrl = endpoint(body.target.purpose == AitaContactPurpose.REGISTRATION, "request"),
            body = body.copy(locale = authRequestLocale(body.locale)),
            expectedSessionGeneration = if (body.target.purpose == AitaContactPurpose.REGISTRATION) null else generation
        ).withAuthFailureMessage()

    suspend fun resend(registration: Boolean, body: AitaEmailCodeResendRequestDataModel, generation: Long = currentAuthenticatedSessionGeneration()) =
        networkRequest<AitaAuthFlowDataModel, AitaEmailCodeResendRequestDataModel>(
            HttpMethod.Post, endpointUrl = endpoint(registration, "resend"),
            body = body.copy(locale = authRequestLocale(body.locale)),
            expectedSessionGeneration = if (registration) null else generation
        ).withAuthFailureMessage()

    suspend fun verify(registration: Boolean, body: AitaEmailCodeVerifyRequestDataModel, generation: Long = currentAuthenticatedSessionGeneration()) =
        networkRequest<AitaContactVerificationResult, AitaEmailCodeVerifyRequestDataModel>(
            HttpMethod.Post, endpointUrl = endpoint(registration, "verify"), body = body,
            expectedSessionGeneration = if (registration) null else generation
        ).withAuthFailureMessage()
}
