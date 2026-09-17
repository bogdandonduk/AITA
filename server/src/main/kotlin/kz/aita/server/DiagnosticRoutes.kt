package kz.aita.server

import io.ktor.http.*
import io.ktor.server.auth.authenticate
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kz.aita.*
import kz.aita.server.auth.AuthSlidingWindowLimiter
import java.io.ByteArrayOutputStream
import java.util.UUID

internal class DiagnosticBodyTooLarge : IllegalArgumentException()
internal class DiagnosticBodyTimeout : IllegalStateException()
internal suspend fun RoutingCall.readDiagnosticBatch(): DiagnosticBatch {
    if (!request.contentType().match(ContentType.Application.Json)) throw IllegalArgumentException()
    if ((request.contentLength() ?: 0) > DIAGNOSTIC_BODY_LIMIT) throw DiagnosticBodyTooLarge()
    val bytes = withTimeoutOrNull(10_000) {
        val stream = receiveChannel()
        val result = ByteArrayOutputStream()
        val block = ByteArray(4096)
        while (true) {
            val count = stream.readAvailable(block, 0, block.size)
            if (count < 0) break
            if (result.size() + count > DIAGNOSTIC_BODY_LIMIT) throw DiagnosticBodyTooLarge()
            result.write(block, 0, count)
        }
        result.toByteArray()
    } ?: throw DiagnosticBodyTimeout()
    return diagnosticJson.decodeFromString(DiagnosticBatch.serializer(), bytes.decodeToString(throwOnInvalidSequence = true)).validated()
}
internal class DiagnosticRequestBudget {
    private val limiter = AuthSlidingWindowLimiter(20_000)
    val watchers = Semaphore(16)
    fun allow(key: String, maximum: Int, now: Long) = limiter.allow(key, maximum, now, 60_000L)
}
private suspend fun RoutingCall.diagnosticFailure(status: HttpStatusCode) {
    response.header(HttpHeaders.CacheControl, "private, no-store")
    if (status == HttpStatusCode.TooManyRequests) response.header(HttpHeaders.RetryAfter, "60")
    genericResponseNoPayload(status, eventMessage("diagnostics.ui.unavailable"))
}
private suspend fun RoutingCall.ingestDiagnostics(owner: UUID?, repository: DiagnosticRepository,
    settings: DiagnosticServerSettings, budget: DiagnosticRequestBudget) {
    response.header(HttpHeaders.CacheControl, "private, no-store")
    if (!settings.enabled) { diagnosticFailure(HttpStatusCode.ServiceUnavailable); return }
    val now = System.currentTimeMillis()
    if (!budget.allow("global-ingest", 500, now) || !budget.allow("peer:${request.local.remoteHost}", 300, now)) {
        diagnosticFailure(HttpStatusCode.TooManyRequests); return
    }
    try {
        if (owner == null && request.headers[HttpHeaders.Authorization] != null) throw IllegalArgumentException()
        val batch = readDiagnosticBatch()
        if (!diagnosticOwnerMatches(owner, batch)) throw IllegalArgumentException()
        if (!budget.allow("install:${batch.installationId}", 30, now) ||
            (owner != null && !budget.allow("account:$owner", 60, now))) {
            diagnosticFailure(HttpStatusCode.TooManyRequests); return
        }
        val location = trustedDiagnosticLocation(settings.gatewayKey, request.headers["x-aita-diagnostics-gateway-key"],
            request.headers["x-aita-diagnostic-country"], request.headers["x-aita-diagnostic-region"])
        val accepted = repository.ingest(owner, batch, location, now)
        genericResponse(HttpStatusCode.OK, DiagnosticAck(batch.installationId, owner?.toString(), accepted, now))
    } catch (problem: DiagnosticBodyTooLarge) { diagnosticFailure(HttpStatusCode.PayloadTooLarge) }
    catch (problem: DiagnosticBodyTimeout) { diagnosticFailure(HttpStatusCode.RequestTimeout) }
    catch (problem: DiagnosticQuotaReached) { diagnosticFailure(HttpStatusCode.TooManyRequests) }
    catch (problem: DiagnosticConflict) { diagnosticFailure(HttpStatusCode.Conflict) }
    catch (cancel: CancellationException) { throw cancel }
    catch (problem: IllegalArgumentException) { diagnosticFailure(HttpStatusCode.BadRequest) }
    catch (_: Exception) { diagnosticFailure(HttpStatusCode.ServiceUnavailable) }
}
internal fun Route.diagnosticPublicRoutes(repository: DiagnosticRepository, settings: DiagnosticServerSettings, budget: DiagnosticRequestBudget) {
    post("/diagnostics/events/anonymous") { call.ingestDiagnostics(null, repository, settings, budget) }
}
internal fun Route.diagnosticProtectedRoutes(repository: DiagnosticRepository, settings: DiagnosticServerSettings,
    budget: DiagnosticRequestBudget, principal: suspend RoutingCall.() -> UUID? = { checkPrincipal() },
    sessionLive: suspend RoutingCall.(UUID) -> Boolean = { diagnosticSessionIsLive(it) }) {
    post("/diagnostics/events") {
        val owner = call.principal() ?: return@post
        call.ingestDiagnostics(owner, repository, settings, budget)
    }
    get("/diagnostics/admin/events") {
        val viewer = call.principal() ?: return@get
        call.response.header(HttpHeaders.CacheControl, "private, no-store")
        if (!settings.canReview(viewer)) { call.diagnosticFailure(HttpStatusCode.Forbidden); return@get }
        val after = call.request.queryParameters["after"]?.toLongOrNull() ?: if (call.request.queryParameters["after"] == null) 0L else -1L
        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: if (call.request.queryParameters["limit"] == null) 25 else -1
        if (after < 0 || limit !in 1..50) { call.diagnosticFailure(HttpStatusCode.BadRequest); return@get }
        if (!budget.allow("review:$viewer", 60, System.currentTimeMillis())) { call.diagnosticFailure(HttpStatusCode.TooManyRequests); return@get }
        try { call.genericResponse(HttpStatusCode.OK, repository.page(after, limit, System.currentTimeMillis())) }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { call.diagnosticFailure(HttpStatusCode.ServiceUnavailable) }
    }
    get("/diagnostics/admin/changes") {
        val viewer = call.principal() ?: return@get
        call.response.header(HttpHeaders.CacheControl, "private, no-store")
        if (!settings.canReview(viewer)) { call.diagnosticFailure(HttpStatusCode.Forbidden); return@get }
        val after = call.request.queryParameters["after"]?.toLongOrNull()
        if (after == null || after < 0) { call.diagnosticFailure(HttpStatusCode.BadRequest); return@get }
        if (!budget.allow("watch:$viewer", 30, System.currentTimeMillis()) || !budget.watchers.tryAcquire()) {
            call.diagnosticFailure(HttpStatusCode.TooManyRequests); return@get
        }
        try {
            val tick = repository.changes.value
            var latest = repository.latest(System.currentTimeMillis())
            if (latest <= after) {
                withTimeoutOrNull(10_000) { repository.changes.first { it != tick } }
                latest = repository.latest(System.currentTimeMillis())
            }
            if (!call.sessionLive(viewer)) { call.diagnosticFailure(HttpStatusCode.Unauthorized); return@get }
            // Only an invalidation cursor is sent here. Reports are fetched through the separately authorized page route.
            call.genericResponse(HttpStatusCode.OK, DiagnosticChanges(maxOf(after, latest), latest > after))
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { call.diagnosticFailure(HttpStatusCode.ServiceUnavailable) }
        finally { budget.watchers.release() }
    }
}
fun Route.installRuntimeDiagnosticRoutes(config: ApplicationConfig, backgroundScope: CoroutineScope) {
    val settings = DiagnosticServerSettings.read(config)
    val repository = DatabaseDiagnostics()
    val budget = DiagnosticRequestBudget()
    diagnosticPublicRoutes(repository, settings, budget)
    authenticate("auth-jwt") { diagnosticProtectedRoutes(repository, settings, budget) }
    backgroundScope.launch {
        while (isActive) {
            try { repository.cleanup(System.currentTimeMillis()) }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { /* A reporter maintenance failure must not interrupt checkout or authentication. */ }
            delay(300_000)
        }
    }
}
