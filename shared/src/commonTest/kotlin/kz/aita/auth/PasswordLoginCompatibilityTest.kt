package kz.aita.auth

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kz.aita.LocalizedStringDataModel
import kz.aita.ResponseDataModel
import kz.aita.TokenPair
import kotlin.test.*

class PasswordLoginCompatibilityTest {
    private val request = AitaPasswordLoginRequestDataModel("user@example.com", "test-password!")
    private val tokens = TokenPair(accessToken = "test-access", refreshToken = "test-refresh")

    @Test
    fun legacyIsCalledOnceOnlyAfterAnExplicitMissingRoute() = runTest {
        listOf(404, 405).forEach { status ->
            var advancedCalls = 0
            var legacyCalls = 0
            val result = passwordLoginWithCompatibility(request,
                advanced = { sent ->
                    advancedCalls++
                    assertEquals(request, sent)
                    ResponseDataModel(message = null, payload = null, negative = true, httpStatusCode = status)
                },
                legacy = { sent ->
                    legacyCalls++
                    assertEquals(request.identifier, sent.login)
                    assertEquals(request.password, sent.password)
                    assertEquals(request.deviceInfo, sent.deviceInfo)
                    ResponseDataModel(message = null, payload = tokens, negative = false, httpStatusCode = 200)
                }
            )
            assertEquals(1, advancedCalls)
            assertEquals(1, legacyCalls)
            assertFalse(result.negative)
            assertEquals(AitaAuthNextStep.AUTHENTICATED, result.payload?.nextStep)
            assertEquals(tokens, result.payload?.tokenPair)
        }
    }

    @Test
    fun credentialPolicyProviderAndTransportFailuresNeverReplayPassword() = runTest {
        listOf(null, 401, 403, 409, 428, 429, 500, 502, 503, 504).forEach { status ->
            val failure = ResponseDataModel<AitaAuthFlowDataModel>(message = null, payload = null, negative = true, httpStatusCode = status)
            val result = passwordLoginWithCompatibility(request, advanced = { failure }, legacy = { error("Password was replayed") })
            assertEquals(failure, result)
        }
        val proxyFailure = ResponseDataModel<AitaAuthFlowDataModel>(message = null, payload = null, negative = true, httpStatusCode = 404, transportFailure = true)
        assertEquals(proxyFailure, passwordLoginWithCompatibility(request, { proxyFailure }, { error("Proxy 404 is not an AITA route") }))
    }

    @Test
    fun advancedTotpChallengeIsNotBypassed() = runTest {
        val challenge = ResponseDataModel(
            message = null, payload = AitaAuthFlowDataModel(flowId = "totp-challenge", nextStep = AitaAuthNextStep.TOTP),
            negative = false, httpStatusCode = 200
        )
        assertEquals(challenge, passwordLoginWithCompatibility(request, { challenge }, { error("TOTP bypass") }))
    }

    @Test
    fun legacySecondFactorRequirementDoesNotBecomeAuthenticated() = runTest {
        val result = passwordLoginWithCompatibility(request,
            advanced = { ResponseDataModel(message = null, payload = null, negative = true, httpStatusCode = 404) },
            legacy = { ResponseDataModel(message = null, payload = null, negative = true, httpStatusCode = 428) }
        )
        assertTrue(result.negative)
        assertNull(result.payload)
        assertEquals(428, result.httpStatusCode)
    }

    @Test
    fun malformedLegacyTokenPairIsNotAdopted() = runTest {
        val result = passwordLoginWithCompatibility(request,
            advanced = { ResponseDataModel(message = null, payload = null, negative = true, httpStatusCode = 404) },
            legacy = { ResponseDataModel(message = null, payload = TokenPair(accessToken = "", refreshToken = "refresh"), negative = false, httpStatusCode = 200) }
        )
        assertTrue(result.negative)
        assertNull(result.payload)
    }

    @Test
    fun cancellationPropagatesWithoutFallback() = runTest {
        assertFailsWith<CancellationException> {
            passwordLoginWithCompatibility(request, { throw CancellationException("screen closed") }, { error("Cancellation was retried") })
        }
    }

    @Test
    fun configurationAndCredentialErrorsRemainSpecific() {
        val message = listOf(LocalizedStringDataModel("en", "Email codes are not configured. Use your password."))
        listOf(401, 409, 428).forEach { status ->
            val response = ResponseDataModel<Unit>(message = message, payload = null, negative = true, httpStatusCode = status)
            assertEquals(response, response.withAuthFailureMessage())
        }
        val unavailable = ResponseDataModel<Unit>(message = message, payload = null, negative = true, httpStatusCode = 503, transportFailure = true)
        assertEquals(unavailable, unavailable.withAuthFailureMessage())
        val missing = ResponseDataModel<Unit>(message = null, payload = null, negative = true, httpStatusCode = 404).withAuthFailureMessage()
        assertTrue(missing.message.orEmpty().any { it.value.contains("Update the AITA server") })
        assertFalse(missing.transportFailure)
    }
}
