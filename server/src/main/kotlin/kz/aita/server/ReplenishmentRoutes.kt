package kz.aita.server

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.TimeZone
import kz.aita.*
import kz.aita.server.auth.AuthSlidingWindowLimiter

internal class ReplenishmentTooLarge : IllegalStateException()

internal fun Route.replenishmentRoute(
    read: suspend (UUID, UUID, Int, String, Long) -> ReplenishmentSnapshot?,
    principal: suspend RoutingCall.() -> UUID? = { checkPrincipal() },
    clock: () -> Long = System::currentTimeMillis,
) {
    val requests = AuthSlidingWindowLimiter(10_000)
    get("/replenishment") {
        call.response.header(HttpHeaders.CacheControl, "private, no-store")
        val user = call.principal() ?: return@get
        val rawStore = call.request.header("store_id").orEmpty()
        val store = runCatching {
            UUID.fromString(rawStore)
        }.getOrNull()?.takeIf { it.toString().equals(rawStore, true) }
        val rawDays = call.request.queryParameters["days"]
        val days = if (rawDays == null) 28 else rawDays.toIntOrNull()
        val zone = call.request.queryParameters["zone"] ?: "Asia/Almaty"
        if (
            store == null ||
                days !in setOf(7, 28, 90) ||
                zone.length !in 1..100 ||
                runCatching { TimeZone.of(zone) }.isFailure
        ) {
            call.genericResponseNoPayload(
                HttpStatusCode.BadRequest,
                replenishmentMessage("invalid_settings"),
            )
            return@get
        }
        val now = clock()
        if (!requests.allow(user.toString(), 20, now, 60_000)) {
            call.response.header(HttpHeaders.RetryAfter, "60")
            call.genericResponseNoPayload(
                HttpStatusCode.TooManyRequests,
                replenishmentMessage("busy"),
            )
            return@get
        }
        try {
            val result = read(user, store, days!!, zone, now)
            if (result == null)
                call.genericResponseNoPayload(
                    HttpStatusCode.Forbidden,
                    replenishmentMessage("access"),
                )
            else call.genericResponse(HttpStatusCode.OK, result)
        } catch (_: ReplenishmentTooLarge) {
            call.genericResponseNoPayload(
                HttpStatusCode.UnprocessableEntity,
                replenishmentMessage("too_large"),
            )
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (problem: Exception) {
            call.application.environment.log.error("Purchasing snapshot failed", problem)
            call.genericResponseNoPayload(
                HttpStatusCode.ServiceUnavailable,
                replenishmentMessage("unavailable"),
            )
        }
    }
}
