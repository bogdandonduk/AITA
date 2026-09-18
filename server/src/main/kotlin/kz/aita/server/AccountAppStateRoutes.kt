package kz.aita.server

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeout
import kz.aita.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.io.ByteArrayOutputStream
import java.util.UUID

/** Installed inside the existing authenticated /user route. Ownership comes only from the JWT. */
internal fun Route.installAccountAppStateRoutes() {
    val repository = AccountAppStateRepository()
    val budget = DiagnosticRequestBudget()
    suspend fun RoutingCall.allowed(owner: UUID, scope: AppStateScope): Boolean {
        if (!scope.valid()) { respond(HttpStatusCode.BadRequest); return false }
        if (!budget.allow(owner.toString(), 90, System.currentTimeMillis())) { respond(HttpStatusCode.TooManyRequests); return false }
        if (scope.store != null && !newSuspendedTransaction(Dispatchers.IO) {
            userHasStoreAccessInsideTransaction(owner, UUID.fromString(scope.store))
        }) { respond(HttpStatusCode.NotFound); return false }
        response.header(HttpHeaders.CacheControl, "private, no-store")
        return true
    }
    get("/app-state") {
        val owner = call.checkPrincipal() ?: return@get
        val mode = call.request.queryParameters["mode"]?.toIntOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest)
        val scope = AppStateScope(mode, call.request.queryParameters["store"]?.takeIf { it.isNotEmpty() })
        if (!call.allowed(owner, scope)) return@get
        call.genericResponse(HttpStatusCode.OK, AppStateResult(repository.read(owner, scope)))
    }
    put("/app-state") {
        val owner = call.checkPrincipal() ?: return@put
        if (!call.request.contentType().match(ContentType.Application.Json)) return@put call.respond(HttpStatusCode.UnsupportedMediaType)
        val request = try {
            withTimeout(10_000) {
                val stream = call.receiveChannel(); val bytes = ByteArrayOutputStream(); val block = ByteArray(4096)
                while (true) {
                    val count = stream.readAvailable(block, 0, block.size)
                    if (count < 0) break
                    require(bytes.size() + count <= APP_STATE_MAX_BYTES + 4096)
                    bytes.write(block, 0, count)
                }
                jsonBase.decodeFromString<AppStateWrite>(bytes.toByteArray().decodeToString(throwOnInvalidSequence = true))
            }
        } catch (_: IllegalArgumentException) { return@put call.respond(HttpStatusCode.BadRequest) }
        if (!call.allowed(owner, request.scope)) return@put
        if (request.expectedRevision !in 0 until Long.MAX_VALUE || request.document?.valid() == false || !request.enabled && request.document != null)
            return@put call.respond(HttpStatusCode.BadRequest)
        call.genericResponse(HttpStatusCode.OK, repository.write(owner, request))
    }
}
