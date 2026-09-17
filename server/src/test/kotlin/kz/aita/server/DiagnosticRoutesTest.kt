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
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kz.aita.*
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.*

class DiagnosticRoutesTest {
    private val owner = UUID.fromString("11111111-1111-4111-8111-111111111111")
    private val admin = UUID.fromString("22222222-2222-4222-8222-222222222222")
    private val algorithm = Algorithm.HMAC256("isolated-diagnostic-route-test-only")
    private fun token(user: UUID) = JWT.create().withSubject(user.toString()).sign(algorithm)
    private fun batch(user: UUID? = null, geo: Boolean = false) = DiagnosticBatch(UUID.randomUUID().toString(), listOf(
        DiagnosticEvent(UUID.randomUUID().toString(), UUID.randomUUID().toString(), System.currentTimeMillis(), "runtime", "IllegalStateException",
            listOf("at kz.aita.Checkout.render(Checkout.kt:42)"), false,
            DiagnosticContext("1.0.0", 1, "RELEASE", "abc1234", DiagnosticDevice("android"), user?.toString()), geo)))
    private class Repository : DiagnosticRepository {
        data class Write(val owner: UUID?, val batch: DiagnosticBatch, val location: TrustedDiagnosticLocation?)
        val writes = CopyOnWriteArrayList<Write>()
        val reads = CopyOnWriteArrayList<Pair<Long, Int>>()
        override val changes = MutableStateFlow(0L)
        @Volatile var live = true
        var reading: CompletableDeferred<Unit>? = null
        override suspend fun ingest(owner: UUID?, batch: DiagnosticBatch, location: TrustedDiagnosticLocation?, now: Long): List<String> {
            writes += Write(owner, batch, location); changes.value++
            return batch.events.map { it.id }
        }
        override suspend fun page(after: Long, limit: Int, now: Long): DiagnosticPage {
            reads += after to limit; return DiagnosticPage(emptyList(), after, false)
        }
        override suspend fun latest(now: Long): Long { reading?.complete(Unit); return writes.size.toLong() }
        override suspend fun cleanup(now: Long) = Unit
    }
    private fun scenario(settings: DiagnosticServerSettings = DiagnosticServerSettings(administrators = setOf(admin)),
        block: suspend ApplicationTestBuilder.(Repository) -> Unit) = testApplication {
        val repository = Repository(); val budget = DiagnosticRequestBudget()
        application {
            install(Authentication) { jwt("auth-jwt") { verifier(JWT.require(algorithm).build()); validate { JWTPrincipal(it.payload) } } }
            routing {
                diagnosticPublicRoutes(repository, settings, budget)
                authenticate("auth-jwt") { diagnosticProtectedRoutes(repository, settings, budget, sessionLive = { repository.live }) }
            }
        }
        block(repository)
    }
    @Test fun anonymousIngestionWorksButProtectedEndpointsRequireAuthentication() = scenario { repo ->
        val body = diagnosticJson.encodeToString(DiagnosticBatch.serializer(), batch())
        assertEquals(HttpStatusCode.OK, client.post("/diagnostics/events/anonymous") { contentType(ContentType.Application.Json); setBody(body) }.status)
        assertNull(repo.writes.single().owner)
        for (path in listOf("/diagnostics/admin/events", "/diagnostics/admin/changes?after=0"))
            assertEquals(HttpStatusCode.Unauthorized, client.get(path).status)
        assertEquals(HttpStatusCode.Unauthorized, client.post("/diagnostics/events") { contentType(ContentType.Application.Json); setBody(body) }.status)
    }
    @Test fun bodyCannotImpersonateAnotherAccountOrPromoteAnonymousReports() = scenario { repo ->
        for ((path, body, auth) in listOf(Triple("/diagnostics/events", batch(admin), token(owner)),
            Triple("/diagnostics/events", batch(), token(owner)), Triple("/diagnostics/events/anonymous", batch(owner), null),
            Triple("/diagnostics/events/anonymous", batch(), token(owner)))) {
            val response = client.post(path) { contentType(ContentType.Application.Json); if (auth != null) bearerAuth(auth)
                setBody(diagnosticJson.encodeToString(DiagnosticBatch.serializer(), body)) }
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
        assertTrue(repo.writes.isEmpty())
    }
    @Test fun ordinaryUsersCannotReadDiagnosticsOrInvalidationCursors() = scenario { repo ->
        for (path in listOf("/diagnostics/admin/events?viewer=$admin", "/diagnostics/admin/changes?after=0")) {
            val response = client.get(path) { bearerAuth(token(owner)) }
            assertEquals(HttpStatusCode.Forbidden, response.status); assertFalse(response.bodyAsText().contains("IllegalStateException"))
        }
        assertTrue(repo.reads.isEmpty())
        assertEquals(HttpStatusCode.OK, client.get("/diagnostics/admin/events?after=3&limit=7") { bearerAuth(token(admin)) }.status)
        assertEquals(listOf(3L to 7), repo.reads)
    }
    @Test fun oversizedAndMalformedBodiesAreRejectedBeforeStorage() = scenario { repo ->
        assertEquals(HttpStatusCode.PayloadTooLarge, client.post("/diagnostics/events/anonymous") {
            contentType(ContentType.Application.Json); setBody(" ".repeat(DIAGNOSTIC_BODY_LIMIT + 1)) }.status)
        for (body in listOf("{}", "{unfinished", "[]")) assertEquals(HttpStatusCode.BadRequest,
            client.post("/diagnostics/events/anonymous") { contentType(ContentType.Application.Json); setBody(body) }.status)
        assertTrue(repo.writes.isEmpty())
    }
    @Test fun forgedLocationHeadersAreIgnoredAndResponsesAreNotCacheable() = scenario { repo ->
        val body = batch(owner, geo = true)
        val result = client.post("/diagnostics/events") {
            bearerAuth(token(owner)); contentType(ContentType.Application.Json)
            header("x-aita-diagnostics-gateway-key", "forged-credential-".repeat(3)); header("x-aita-diagnostic-country", "KZ")
            setBody(diagnosticJson.encodeToString(DiagnosticBatch.serializer(), body))
        }
        assertEquals(HttpStatusCode.OK, result.status); assertNull(repo.writes.single().location)
        assertEquals(owner, repo.writes.single().owner); assertEquals("private, no-store", result.headers[HttpHeaders.CacheControl])
        assertTrue(result.bodyAsText().contains(body.events.single().id))
    }
    @Test fun malformedPaginationAndDisabledCollectionFailClosed() = scenario { repo ->
        for (query in listOf("after=-1", "after=oops", "limit=0", "limit=51", "limit=oops"))
            assertEquals(HttpStatusCode.BadRequest, client.get("/diagnostics/admin/events?$query") { bearerAuth(token(admin)) }.status)
        assertTrue(repo.reads.isEmpty())
    }
    @Test fun serverCanDisableCollectionWithoutBreakingItsOtherRoutes() = scenario(DiagnosticServerSettings(enabled = false)) { repo ->
        assertEquals(HttpStatusCode.ServiceUnavailable, client.post("/diagnostics/events/anonymous") {
            contentType(ContentType.Application.Json); setBody(diagnosticJson.encodeToString(DiagnosticBatch.serializer(), batch())) }.status)
        assertTrue(repo.writes.isEmpty())
    }
    @Test fun longPollRechecksSessionAfterWaiting() = scenario { repo ->
        repo.reading = CompletableDeferred()
        coroutineScope {
            val response = async { client.get("/diagnostics/admin/changes?after=0") { bearerAuth(token(admin)) } }
            withTimeout(5_000) { repo.reading!!.await() }
            repo.live = false
            repo.changes.value++
            assertEquals(HttpStatusCode.Unauthorized, withTimeout(5_000) { response.await() }.status)
        }
    }
    @Test fun ingestionHasAnInstallationQuotaBeforeStorage() = scenario { repo ->
        val body = diagnosticJson.encodeToString(DiagnosticBatch.serializer(), batch())
        repeat(30) {
            assertEquals(HttpStatusCode.OK, client.post("/diagnostics/events/anonymous") {
                contentType(ContentType.Application.Json); setBody(body)
            }.status)
        }
        val denied = client.post("/diagnostics/events/anonymous") { contentType(ContentType.Application.Json); setBody(body) }
        assertEquals(HttpStatusCode.TooManyRequests, denied.status)
        assertEquals("60", denied.headers[HttpHeaders.RetryAfter]); assertEquals(30, repo.writes.size)
    }
}
