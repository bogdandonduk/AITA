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
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlin.test.*
import kz.aita.*

class MarketPriceWatchRoutesTest {
    private val user = UUID.fromString("11111111-1111-4111-8111-111111111111")
    private val store = UUID.fromString("22222222-2222-4222-8222-222222222222")
    private val algorithm = Algorithm.HMAC256("isolated-market-watch-fixture")
    private val token get() = JWT.create().withSubject(user.toString()).sign(algorithm)
    private val update = MarketPriceWatchUpdate(UUID.randomUUID().toString(), 0, false, MarketPriceWatchSettings())
    private class Fixture {
        var failure: Exception? = null
        var reads = 0
        var hold: CompletableDeferred<Unit>? = null
        val entered = CompletableDeferred<Unit>()
        val saves = mutableListOf<Triple<UUID, UUID, MarketPriceWatchUpdate>>()
        suspend fun read(user: UUID, store: UUID): MarketPriceWatchState {
            reads++; entered.complete(Unit); hold?.await()
            failure?.let { throw it }; return MarketPriceWatchState(user.toString(), store.toString())
        }
        fun save(user: UUID, store: UUID, body: MarketPriceWatchUpdate): MarketPriceWatchState {
            saves += Triple(user, store, body); failure?.let { throw it }
            return MarketPriceWatchState(user.toString(), store.toString(), revision = 1)
        }
    }
    private fun scenario(block: suspend ApplicationTestBuilder.(Fixture) -> Unit) = testApplication {
        val fixture = Fixture()
        application {
            install(Authentication) { jwt("fixture") { verifier(JWT.require(algorithm).build()); validate { JWTPrincipal(it.payload) } } }
            routing { authenticate("fixture") { route("/analytics") {
                marketPriceWatchRoutes(principal = { principal<JWTPrincipal>()?.subject?.let(UUID::fromString) },
                    read = { u, s -> fixture.read(u, s) }, save = { u, s, b, _ -> fixture.save(u, s, b) }, clock = { 1_000_000 })
            } } }
        }
        block(fixture)
    }
    @Test fun unauthenticatedReadAndSaveCannotReachData() = scenario { f ->
        assertEquals(HttpStatusCode.Unauthorized, client.get("/analytics/market-prices/watch") { header("store_id", store) }.status)
        assertEquals(HttpStatusCode.Unauthorized, client.put("/analytics/market-prices/watch") { header("store_id", store); contentType(ContentType.Application.Json); setBody(jsonBase.encodeToString(update)) }.status)
        assertEquals(0, f.reads); assertTrue(f.saves.isEmpty())
    }
    @Test fun identityComesFromAuthenticatedSessionAndRepliesAreNotCached() = scenario { f ->
        val response = client.put("/analytics/market-prices/watch?userId=spoofed") {
            bearerAuth(token); header("store_id", store); contentType(ContentType.Application.Json); setBody(jsonBase.encodeToString(update))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("private, no-store", response.headers[HttpHeaders.CacheControl])
        assertEquals(Triple(user, store, update), f.saves.single())
    }
    @Test fun invalidHeadersContentTypesJsonAndOversizedDocumentsNeverSave() = scenario { f ->
        for (value in listOf("", "1-1-1-1-1", "invalid")) {
            assertEquals(HttpStatusCode.BadRequest, client.get("/analytics/market-prices/watch") { bearerAuth(token); header("store_id", value) }.status)
        }
        for ((content, type) in listOf("{" to ContentType.Application.Json, "{}" to ContentType.Text.Plain,
            " ".repeat(16_385) to ContentType.Application.Json)) {
            assertEquals(HttpStatusCode.BadRequest, client.put("/analytics/market-prices/watch") {
                bearerAuth(token); header("store_id", store); contentType(type); setBody(content)
            }.status)
        }
        assertEquals(0, f.reads); assertTrue(f.saves.isEmpty())
    }
    @Test fun permissionConflictAndUnavailableAreDistinguishedWithoutLeakingDetails() = scenario { f ->
        for ((failure, status) in listOf(MarketPriceWatchFailure("access") to HttpStatusCode.Forbidden,
            MarketPriceWatchFailure("watch_conflict") to HttpStatusCode.Conflict,
            MarketPriceWatchFailure("watch_invalid") to HttpStatusCode.UnprocessableEntity,
            IllegalStateException("private credential") to HttpStatusCode.ServiceUnavailable)) {
            f.failure = failure
            val response = client.put("/analytics/market-prices/watch") {
                bearerAuth(token); header("store_id", store); contentType(ContentType.Application.Json); setBody(jsonBase.encodeToString(update))
            }
            assertEquals(status, response.status); assertFalse(response.bodyAsText().contains("private credential"))
        }
    }
    @Test fun rapidReadsAndWritesShareOneRateLimit() = scenario { f ->
        repeat(30) { assertEquals(HttpStatusCode.OK, client.get("/analytics/market-prices/watch") { bearerAuth(token); header("store_id", store) }.status) }
        val response = client.put("/analytics/market-prices/watch") {
            bearerAuth(token); header("store_id", store); contentType(ContentType.Application.Json); setBody(jsonBase.encodeToString(update))
        }
        assertEquals(HttpStatusCode.TooManyRequests, response.status); assertEquals("60", response.headers[HttpHeaders.RetryAfter])
        assertEquals(30, f.reads); assertTrue(f.saves.isEmpty())
    }
    @Test fun anOverlappingReadGetsAnExplicitRetryAndTheSlotIsReleased() = scenario { f ->
        f.hold = CompletableDeferred()
        coroutineScope {
            val first = async { client.get("/analytics/market-prices/watch") { bearerAuth(token); header("store_id", store) } }
            withTimeout(5000) { f.entered.await() }
            val overlap = client.get("/analytics/market-prices/watch") { bearerAuth(token); header("store_id", store) }
            assertEquals(HttpStatusCode.TooManyRequests, overlap.status); assertEquals("2", overlap.headers[HttpHeaders.RetryAfter])
            f.hold!!.complete(Unit); assertEquals(HttpStatusCode.OK, first.await().status)
            assertEquals(HttpStatusCode.OK, client.get("/analytics/market-prices/watch") { bearerAuth(token); header("store_id", store) }.status)
        }
    }
}
