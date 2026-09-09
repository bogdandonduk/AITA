package kz.aita.auth

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.test.*

class AuthenticatorLoginPolicySerializationTest {
    private val json = Json { ignoreUnknownKeys = true }
    @Test fun oldServerPayloadKeepsEnrolledAuthenticatorMandatory() {
        val settings = json.decodeFromString<AitaAuthenticationSettingsDataModel>("""{"authenticatorEnabled":true}""")
        assertTrue(settings.authenticatorRequiredForLogin)
    }
    @Test fun explicitOptionalPolicySurvivesWireRoundTrip() {
        val settings = AitaAuthenticationSettingsDataModel(authenticatorEnabled = true, authenticatorRequiredForLogin = false)
        assertEquals(settings, json.decodeFromString<AitaAuthenticationSettingsDataModel>(json.encodeToString(settings)))
    }
    @Test fun oldClientEnrollmentRequestRemainsProtectedByDefault() {
        val request = json.decodeFromString<AitaTotpSetupConfirmRequestDataModel>("""{"setupId":"setup","code":"123456"}""")
        assertTrue(request.requireForLogin)
    }
}
