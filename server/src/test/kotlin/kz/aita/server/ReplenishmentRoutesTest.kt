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
import kotlinx.serialization.decodeFromString
import kz.aita.*

class ReplenishmentRoutesTest {
    private val user = UUID.fromString("11111111-1111-4111-8111-111111111111")
    private val store = UUID.fromString("22222222-2222-4222-8222-222222222222")
    private val algo = Algorithm.HMAC256("isolated-purchasing-route-fixture")
    private val token
        get() = JWT.create().withSubject(user.toString()).sign(algo)

    private class Fixture {
        var allowed = true
        var failure: Exception? = null
        val calls = mutableListOf<List<String>>()

        suspend fun read(
            user: UUID,
            store: UUID,
            days: Int,
            zone: String,
            now: Long,
        ): ReplenishmentSnapshot? {
            calls += listOf(user.toString(), store.toString(), days.toString(), zone)
            failure?.let { throw it }
            return if (allowed)
                ReplenishmentSnapshot(store.toString(), now, 1, now - 1, days, zone, emptyList())
            else null
        }
    }

    private fun scenario(block: suspend ApplicationTestBuilder.(Fixture) -> Unit) =
        testApplication {
            val fixture = Fixture()
            application {
                install(Authentication) {
                    jwt("fixture") {
                        verifier(JWT.require(algo).build())
                        validate { JWTPrincipal(it.payload) }
                    }
                }
                routing {
                    authenticate("fixture") {
                        route("/analytics") {
                            replenishmentRoute(
                                fixture::read,
                                principal = {
                                    principal<JWTPrincipal>()?.subject?.let(UUID::fromString)
                                },
                                clock = { 1_000_000 },
                            )
                        }
                    }
                }
            }
            block(fixture)
        }

    @Test
    fun unauthenticatedCallsCannotReadAnyPurchasingData() = scenario { f ->
        assertEquals(
            HttpStatusCode.Unauthorized,
            client.get("/analytics/replenishment") { header("store_id", store) }.status,
        )
        assertTrue(f.calls.isEmpty())
    }

    @Test
    fun explicitInvalidQueryIsRejectedRatherThanSilentlyUsingDefault() = scenario { f ->
        for (query in
            listOf(
                "days=x",
                "days=0",
                "days=365",
                "days=",
                "zone=",
                "zone=not/a/timezone",
                "zone=" + "a".repeat(101),
            )) {
            assertEquals(
                HttpStatusCode.BadRequest,
                client
                    .get("/analytics/replenishment?$query") {
                        bearerAuth(token)
                        header("store_id", store)
                    }
                    .status,
                query,
            )
        }
        for (raw in listOf("", "1-1-1-1-1", "not-a-store")) assertEquals(
            HttpStatusCode.BadRequest,
            client
                .get("/analytics/replenishment") {
                    bearerAuth(token)
                    header("store_id", raw)
                }
                .status,
        )
        assertTrue(f.calls.isEmpty())
    }

    @Test
    fun usesAuthenticatedOwnerAndRequestedStorePeriodAndTimezone() = scenario { f ->
        val response =
            client.get("/analytics/replenishment?days=7&zone=Europe/Moscow&userId=spoofed") {
                bearerAuth(token)
                header("store_id", store)
            }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("private, no-store", response.headers[HttpHeaders.CacheControl])
        assertEquals(
            listOf(user.toString(), store.toString(), "7", "Europe/Moscow"),
            f.calls.single(),
        )
        val envelope = jsonBase.decodeFromString<GenericResponseDataModel>(response.bodyAsText())
        assertFalse(envelope.negative)
        assertTrue(response.bodyAsText().contains(store.toString()))
    }

    @Test
    fun deniedAccessNeverReturnsTheSnapshot() = scenario { f ->
        f.allowed = false
        val response =
            client.get("/analytics/replenishment") {
                bearerAuth(token)
                header("store_id", store)
            }
        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertFalse(response.bodyAsText().contains("generatedAtMillis"))
    }

    @Test
    fun oversizedOrFailedQueriesNeverReturnPartialData() = scenario { f ->
        for ((failure, expected) in
            listOf(
                ReplenishmentTooLarge() to HttpStatusCode.UnprocessableEntity,
                IllegalStateException("fixture unavailable") to HttpStatusCode.ServiceUnavailable,
            )) {
            f.failure = failure
            val response =
                client.get("/analytics/replenishment") {
                    bearerAuth(token)
                    header("store_id", store)
                }
            assertEquals(expected, response.status)
            assertFalse(response.bodyAsText().contains("generatedAtMillis"))
            assertFalse(response.bodyAsText().contains("fixture unavailable"))
        }
    }

    @Test
    fun heavyRepeatedRefreshIsBoundedPerAccount() = scenario { f ->
        repeat(20) {
            assertEquals(
                HttpStatusCode.OK,
                client
                    .get("/analytics/replenishment") {
                        bearerAuth(token)
                        header("store_id", store)
                    }
                    .status,
            )
        }
        val blocked =
            client.get("/analytics/replenishment") {
                bearerAuth(token)
                header("store_id", store)
            }
        assertEquals(HttpStatusCode.TooManyRequests, blocked.status)
        assertEquals("60", blocked.headers[HttpHeaders.RetryAfter])
        assertEquals(20, f.calls.size)
    }
}
