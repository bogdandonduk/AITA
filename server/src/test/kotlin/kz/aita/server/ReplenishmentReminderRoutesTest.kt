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
import kotlinx.serialization.encodeToString
import kz.aita.*

class ReplenishmentReminderRoutesTest {
    private val user = UUID.fromString("11111111-1111-4111-8111-111111111111")
    private val store = UUID.fromString("22222222-2222-4222-8222-222222222222")
    private val algorithm = Algorithm.HMAC256("isolated-reminder-test-only")
    private val token
        get() = JWT.create().withSubject(user.toString()).sign(algorithm)

    private val update =
        ReplenishmentReminderUpdate(
            UUID.randomUUID().toString(),
            0,
            true,
            ReplenishmentSettings(event = "new_year", arrivalDate = "2027-01-01"),
            "Asia/Almaty",
        )

    private class Fixture {
        var readCalls = 0
        val saved = mutableListOf<Triple<UUID, UUID, ReplenishmentReminderUpdate>>()
        var failure: String? = null
    }

    private fun scenario(block: suspend ApplicationTestBuilder.(Fixture) -> Unit) =
        testApplication {
            val f = Fixture()
            application {
                install(Authentication) {
                    jwt("fixture") {
                        verifier(JWT.require(algorithm).build())
                        validate { JWTPrincipal(it.payload) }
                    }
                }
                routing {
                    authenticate("fixture") {
                        route("/analytics") {
                            replenishmentReminderRoutes(
                                principal = {
                                    principal<JWTPrincipal>()?.subject?.let(UUID::fromString)
                                },
                                read = { _, sid ->
                                    f.readCalls++
                                    f.failure?.let { throw ReplenishmentReminderFailure(it) }
                                    ReplenishmentReminder(sid.toString())
                                },
                                save = { uid, sid, body, _ ->
                                    f.failure?.let { throw ReplenishmentReminderFailure(it) }
                                    f.saved += Triple(uid, sid, body)
                                    ReplenishmentReminder(sid.toString(), revision = 1)
                                },
                                clock = { 1_000_000 },
                            )
                        }
                    }
                }
            }
            block(f)
        }

    @Test
    fun requiresLoginAndBindsIdentityToAuthenticatedUser() = scenario { f ->
        assertEquals(
            HttpStatusCode.Unauthorized,
            client.get("/analytics/replenishment/reminder").status,
        )
        assertEquals(0, f.readCalls)
        val response =
            client.put("/analytics/replenishment/reminder?userId=somebody-else") {
                bearerAuth(token)
                header("store_id", store)
                contentType(ContentType.Application.Json)
                setBody(jsonBase.encodeToString(update))
            }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("private, no-store", response.headers[HttpHeaders.CacheControl])
        assertEquals(Triple(user, store, update), f.saved.single())
    }

    @Test
    fun malformedLargeOrWrongMediaBodiesNeverReachStorage() = scenario { f ->
        for ((body, type) in
            listOf(
                "{}" to ContentType.Application.Json,
                "not json" to ContentType.Application.Json,
                " ".repeat(16_385) to ContentType.Application.Json,
                jsonBase.encodeToString(update) to ContentType.Text.Plain,
            )) {
            val response =
                client.put("/analytics/replenishment/reminder") {
                    bearerAuth(token)
                    header("store_id", store)
                    contentType(type)
                    setBody(body)
                }
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
        assertTrue(f.saved.isEmpty())
        assertEquals(
            HttpStatusCode.BadRequest,
            client
                .get("/analytics/replenishment/reminder") {
                    bearerAuth(token)
                    header("store_id", "1-1-1-1-1")
                }
                .status,
        )
        assertEquals(0, f.readCalls)
    }

    @Test
    fun conflictsAndDeniedAccessHaveDistinctResponsesWithoutPersonalData() = scenario { f ->
        for ((kind, status) in
            listOf(
                "access" to HttpStatusCode.Forbidden,
                "reminder_conflict" to HttpStatusCode.Conflict,
                "reminder_invalid" to HttpStatusCode.UnprocessableEntity,
            )) {
            f.failure = kind
            val response =
                client.put("/analytics/replenishment/reminder") {
                    bearerAuth(token)
                    header("store_id", store)
                    contentType(ContentType.Application.Json)
                    setBody(jsonBase.encodeToString(update))
                }
            assertEquals(status, response.status)
            assertFalse(response.bodyAsText().contains("dueAtMillis"))
        }
    }

    @Test
    fun repeatedRequestsAreBoundedPerAccount() = scenario { f ->
        repeat(30) {
            assertEquals(
                HttpStatusCode.OK,
                client
                    .get("/analytics/replenishment/reminder") {
                        bearerAuth(token)
                        header("store_id", store)
                    }
                    .status,
            )
        }
        val response =
            client.get("/analytics/replenishment/reminder") {
                bearerAuth(token)
                header("store_id", store)
            }
        assertEquals(HttpStatusCode.TooManyRequests, response.status)
        assertEquals("60", response.headers[HttpHeaders.RetryAfter])
        assertEquals(30, f.readCalls)
    }
}
