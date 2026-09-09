package kz.aita.auth

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.auth.*
import io.ktor.client.plugins.auth.providers.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
    @Test
    fun pinnedAccountRequestCannotLoadOrReplayAnotherAccountsToken() = runBlocking {
        var loads = 0
        var refreshes = 0
        var requests = 0
        val client = HttpClient(MockEngine { request ->
            requests++
            assertEquals(listOf("Bearer original-account"), request.headers.getAll(HttpHeaders.Authorization))
            respond("{}", HttpStatusCode.Unauthorized, headersOf(HttpHeaders.WWWAuthenticate, "Bearer"))
        }) {
            expectSuccess = false
            install(Auth) {
                bearer {
                    sendWithoutRequest { true }
                    loadTokens { loads++; BearerTokens("different-account", "different-refresh") }
                    refreshTokens { refreshes++; BearerTokens("different-account-new", "different-refresh-new") }
                }
            }
        }
        try {
            val response = client.post("https://aita.test/auth/security/email/confirm") {
                header(HttpHeaders.Authorization, "Bearer stale")
                pinSessionAuthorization("original-account")
            }
            assertEquals(HttpStatusCode.Unauthorized, response.status)
            assertEquals(1, requests)
            assertEquals(0, loads)
            assertEquals(0, refreshes)
        } finally { client.close() }
    }
}
