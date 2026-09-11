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
import kotlin.test.assertTrue

class PublicAuthenticationRequestTest {
    @Test
    fun publicCredentialRejectionDoesNotSendStoredBearerRefreshOrReplay() = runBlocking {
        val endpoints = listOf(
            "auth/logIn", "auth/login/password", "auth/login/code/verify", "auth/login/totp",
            "auth/password-recovery/verify", "auth/password-recovery/reset",
            "auth/login/authenticator", "auth/login/authenticator/password",
            "auth/login/email-factor/request", "auth/login/email-factor/verify",
            "auth/authenticator-recovery/request", "auth/authenticator-recovery/resend", "auth/authenticator-recovery/confirm"
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
                    // Exercise the same provider predicate as production, not a fake bearer.
                    sendWithoutRequest { it.allowsStoredSessionAuthorization() }
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
        val endpoints = listOf("auth/session", "auth/security/settings", "auth/security/totp/setup/start",
            "auth/security/email-proof/request", "auth/security/login-policy")
        var requests = 0
        val engine = MockEngine { request ->
            requests++
            assertEquals("Bearer account-access", request.headers[HttpHeaders.Authorization])
            respond("{}", HttpStatusCode.OK)
        }
        val client = HttpClient(engine) {
            install(Auth) {
                bearer {
                    sendWithoutRequest { it.allowsStoredSessionAuthorization() }
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
                    sendWithoutRequest { it.allowsStoredSessionAuthorization() }
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

    @Test
    fun accountStoreAndRealtimeRequestsKeepPinnedTokenWithWarmBearerCache() = runBlocking {
        var loads = 0
        var refreshes = 0
        val headers = mutableListOf<Pair<String, String?>>()
        val client = HttpClient(MockEngine { request ->
            headers += request.url.encodedPath to request.headers[HttpHeaders.Authorization]
            respond("{}", HttpStatusCode.OK)
        }) {
            install(Auth) { bearer {
                sendWithoutRequest { it.allowsStoredSessionAuthorization() }
                loadTokens { loads++; BearerTokens("previous-account", "previous-refresh") }
                refreshTokens { refreshes++; null }
            } }
        }
        try {
            client.get("https://aita.test/warm-cache")
            listOf("user/get", "stores/get", "rt/updates", "auth/security/settings").forEach { endpoint ->
                client.get("https://aita.test/$endpoint") { pinSessionAuthorization("accepted-login") }
            }
            client.put("https://aita.test/user/update") { pinSessionAuthorization("accepted-login") }
            assertEquals("Bearer previous-account", headers.first().second)
            assertTrue(headers.drop(1).all { it.second == "Bearer accepted-login" })
            assertEquals(1, loads)
            assertEquals(0, refreshes)
        } finally { client.close() }
    }

    @Test
    fun ordinaryBearerRefreshStillWorksExactlyOnce() = runBlocking {
        var refreshes = 0
        val sent = mutableListOf<String?>()
        val client = HttpClient(MockEngine { request ->
            val token = request.headers[HttpHeaders.Authorization]
            sent += token
            if (token == "Bearer renewed") respond("{}", HttpStatusCode.OK)
            else respond("{}", HttpStatusCode.Unauthorized, headersOf(HttpHeaders.WWWAuthenticate, "Bearer"))
        }) {
            expectSuccess = false
            install(Auth) { bearer {
                sendWithoutRequest { it.allowsStoredSessionAuthorization() }
                loadTokens { BearerTokens("expired", "refresh") }
                refreshTokens { refreshes++; BearerTokens("renewed", "new-refresh") }
            } }
        }
        try {
            assertEquals(HttpStatusCode.OK, client.get("https://aita.test/stock/get").status)
            assertEquals(listOf("Bearer expired", "Bearer renewed"), sent)
            assertEquals(1, refreshes)
        } finally { client.close() }
    }

    @Test
    fun publicLoginAfterPinnedRejectionDoesNotReuseTheOldSession() = runBlocking {
        var loads = 0
        var refreshes = 0
        val sent = mutableListOf<String?>()
        val client = HttpClient(MockEngine { request ->
            sent += request.headers[HttpHeaders.Authorization]
            respond("{}", HttpStatusCode.Unauthorized, headersOf(HttpHeaders.WWWAuthenticate, "Bearer"))
        }) {
            expectSuccess = false
            install(Auth) { bearer {
                sendWithoutRequest { it.allowsStoredSessionAuthorization() }
                loadTokens { loads++; BearerTokens("stored-old", "refresh-old") }
                refreshTokens { refreshes++; null }
            } }
        }
        try {
            client.get("https://aita.test/user/get") { pinSessionAuthorization("rejected-session") }
            client.post("https://aita.test/auth/login/password") {
                disableSessionAuthForPublicAuthRequest("auth/login/password")
            }
            assertEquals(listOf("Bearer rejected-session", null), sent)
            assertEquals(0, loads)
            assertEquals(0, refreshes)
        } finally { client.close() }
    }
}
