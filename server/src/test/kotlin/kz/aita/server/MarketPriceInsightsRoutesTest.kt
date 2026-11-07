package kz.aita.server

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import java.util.UUID
import kotlin.test.*
import kz.aita.*
import kz.aita.server.marketplace.MarketFailure

class MarketPriceInsightsRoutesTest {
    private val user = UUID.fromString("11111111-1111-4111-8111-111111111111")
    private val store = UUID.fromString("22222222-2222-4222-8222-222222222222")
    private val algorithm = Algorithm.HMAC256("isolated-price-report-fixture")
    private val token get() = JWT.create().withSubject(user.toString()).sign(algorithm)
    private class Fixture {
        var failure: Exception? = null
        val calls = mutableListOf<Triple<UUID, UUID, MarketPriceRequest>>()
        suspend fun read(user: UUID, store: UUID, request: MarketPriceRequest, now: Long): MarketPriceInsightsPage {
            calls += Triple(user, store, request)
            failure?.let { throw it }
            return MarketPriceInsightsPage(user.toString(), store.toString(), request, checkedAtMillis = now)
        }
    }
    private fun scenario(block: suspend ApplicationTestBuilder.(Fixture) -> Unit) = testApplication {
        val fixture = Fixture()
        application {
            install(Authentication) { jwt("fixture") { verifier(JWT.require(algorithm).build()); validate { JWTPrincipal(it.payload) } } }
            routing { authenticate("fixture") { route("/analytics") {
                marketPriceInsightsRoute(fixture::read, principal = { principal<JWTPrincipal>()?.subject?.let(UUID::fromString) }, clock = { 1_000_000 })
            } } }
        }
        block(fixture)
    }
    @Test fun unauthenticatedCannotReadReport() = scenario { f ->
        assertEquals(HttpStatusCode.Unauthorized, client.get("/analytics/market-prices") { header("store_id", store) }.status)
        assertTrue(f.calls.isEmpty())
    }
    @Test fun invalidScopeAndUnboundedQueriesAreRejectedBeforeReadingDatabase() = scenario { f ->
        for (query in listOf("country=US", "limit=0", "limit=31", "limit=x", "limit=", "after=1-1-1-1-1",
            "city=%0A", "search=" + "a".repeat(121), "gtin=4006381333931")) {
            assertEquals(HttpStatusCode.BadRequest, client.get("/analytics/market-prices?$query") {
                bearerAuth(token); header("store_id", store)
            }.status, query)
        }
        for (headerValue in listOf("", "1-1-1-1-1", "invalid")) {
            assertEquals(HttpStatusCode.BadRequest, client.get("/analytics/market-prices") {
                bearerAuth(token); header("store_id", headerValue)
            }.status)
        }
        assertTrue(f.calls.isEmpty())
    }
    @Test fun authenticatedIdentityCannotBeSpoofedAndResponseIsNeverSharedCached() = scenario { f ->
        val response = client.get("/analytics/market-prices?country=kg&city=Bishkek&search=tea&userId=spoofed") {
            bearerAuth(token); header("store_id", store)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("private, no-store", response.headers[HttpHeaders.CacheControl])
        assertEquals(Triple(user, store, MarketPriceRequest(country = "kg", city = "Bishkek", search = "tea")), f.calls.single())
    }
    @Test fun forbiddenAndDatabaseFailuresRemainExplicitAndDoNotLeakInternals() = scenario { f ->
        f.failure = MarketFailure("private-store", 403)
        val denied = client.get("/analytics/market-prices") { bearerAuth(token); header("store_id", store) }
        assertEquals(HttpStatusCode.Forbidden, denied.status)
        assertFalse(denied.bodyAsText().contains("private-store"))
        f.failure = IllegalStateException("private database credential")
        val unavailable = client.get("/analytics/market-prices") { bearerAuth(token); header("store_id", store) }
        assertEquals(HttpStatusCode.ServiceUnavailable, unavailable.status)
        assertFalse(unavailable.bodyAsText().contains("private database credential"))
    }
    @Test fun repeatedRefreshesAreRateLimitedWithRetryInstruction() = scenario { f ->
        repeat(30) { assertEquals(HttpStatusCode.OK, client.get("/analytics/market-prices") { bearerAuth(token); header("store_id", store) }.status) }
        val response = client.get("/analytics/market-prices") { bearerAuth(token); header("store_id", store) }
        assertEquals(HttpStatusCode.TooManyRequests, response.status)
        assertNotNull(response.headers[HttpHeaders.RetryAfter])
        assertEquals(30, f.calls.size)
    }
}
