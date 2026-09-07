package kz.aita.auth

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class PublicAuthenticationRequestTest {
    @Test
    fun publicCredentialRejectionDoesNotSendStoredBearerRefreshOrReplay() = runBlocking {
        val endpoints = listOf(
            "auth/logIn", "auth/login/password", "auth/login/code/verify", "auth/login/totp",
            "auth/password-recovery/verify", "auth/password-recovery/reset"
        )
        var refreshCalls = 0
        var requests = 0
        val engine = MockEngine { request ->
            requests++
            assertNull(request.headers[HttpHeaders.Authorization])
            respond("{}", HttpStatusCode.Unauthorized, headersOf(HttpHeaders.WWWAuthenticate, "Bearer"))
        }
        val client = HttpClient(engine) {
            expectSuccess = false
            install(Auth) {
                bearer {
                    // Deliberately allow preemptive auth: the public request itself must bypass it.
                    sendWithoutRequest { true }
                    loadTokens { BearerTokens("old-account-access", "old-account-refresh") }
                    refreshTokens {
                        refreshCalls++
                        BearerTokens("refreshed-access", "refreshed-refresh")
                    }
                }
            }
        }
        try {
            endpoints.forEach { endpoint ->
                val response = client.post("https://aita.test/$endpoint") {
                    disableSessionAuthForPublicAuthRequest(endpoint)
                }
                assertEquals(HttpStatusCode.Unauthorized, response.status)
            }
            assertEquals(endpoints.size, requests)
            assertEquals(0, refreshCalls)
        } finally { client.close() }
    }

    @Test
    fun protectedSecurityAndSessionRoutesRetainBearerAuthentication() = runBlocking {
        val endpoints = listOf("auth/session", "auth/security/settings", "auth/security/totp/setup/start")
        var requests = 0
        val engine = MockEngine { request ->
            requests++
            assertEquals("Bearer account-access", request.headers[HttpHeaders.Authorization])
            respond("{}", HttpStatusCode.OK)
        }
        val client = HttpClient(engine) {
            install(Auth) {
                bearer {
                    sendWithoutRequest { true }
                    loadTokens { BearerTokens("account-access", "account-refresh") }
                }
            }
        }
        try {
            endpoints.forEach { endpoint ->
                val response = client.get("https://aita.test/$endpoint") {
                    disableSessionAuthForPublicAuthRequest(endpoint)
                }
                assertEquals(HttpStatusCode.OK, response.status)
            }
            assertEquals(endpoints.size, requests)
        } finally { client.close() }
    }
}
