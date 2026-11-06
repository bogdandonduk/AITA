package kz.aita.server

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.decodeFromString
import kz.aita.*
import kz.aita.server.auth.AuthSlidingWindowLimiter
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

internal fun Route.replenishmentReminderRoutes(
    principal: suspend RoutingCall.() -> UUID? = { checkPrincipal() },
    read: suspend (UUID, UUID) -> ReplenishmentReminder = { user, store ->
        newSuspendedTransaction(aitaServerIoContext) { readReminderInsideTransaction(user, store) }
    },
    save: suspend (UUID, UUID, ReplenishmentReminderUpdate, Long) -> ReplenishmentReminder =
        { user, store, body, now ->
            newSuspendedTransaction(aitaServerIoContext) {
                maxAttempts = 1
                exec("SET LOCAL statement_timeout = '10000ms'")
                saveReminderInsideTransaction(user, store, body, now)
            }
        },
    clock: () -> Long = System::currentTimeMillis,
) {
    val limiter = AuthSlidingWindowLimiter(10_000)
    suspend fun RoutingCall.identity(): Pair<UUID, UUID>? {
        response.header(HttpHeaders.CacheControl, "private, no-store")
        val user = principal() ?: return null
        val raw = request.header("store_id").orEmpty()
        val store = runCatching {
            UUID.fromString(raw)
        }.getOrNull()?.takeIf { it.toString().equals(raw, true) }
        if (store == null) {
            genericResponseNoPayload(
                HttpStatusCode.BadRequest,
                replenishmentMessage("reminder_invalid"),
            )
            return null
        }
        if (!limiter.allow(user.toString(), 30, clock(), 60_000)) {
            response.header(HttpHeaders.RetryAfter, "60")
            genericResponseNoPayload(HttpStatusCode.TooManyRequests, replenishmentMessage("busy"))
            return null
        }
        return user to store
    }
    suspend fun RoutingCall.failed(problem: ReplenishmentReminderFailure) {
        genericResponseNoPayload(
            when (problem.kind) {
                "access" -> HttpStatusCode.Forbidden
                "reminder_conflict" -> HttpStatusCode.Conflict
                else -> HttpStatusCode.UnprocessableEntity
            },
            replenishmentMessage(problem.kind),
        )
    }
    get("/replenishment/reminder") {
        val (user, store) = call.identity() ?: return@get
        try {
            call.genericResponse(HttpStatusCode.OK, read(user, store))
        } catch (failure: ReplenishmentReminderFailure) {
            call.failed(failure)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (problem: Exception) {
            call.application.environment.log.error("Purchasing reminder read failed", problem)
            call.genericResponseNoPayload(
                HttpStatusCode.ServiceUnavailable,
                replenishmentMessage("reminder_unavailable"),
            )
        }
    }
    put("/replenishment/reminder") {
        val (user, store) = call.identity() ?: return@put
        val body =
            try {
                requireNotNull(
                    withTimeoutOrNull(10_000) {
                        require(call.request.contentType().match(ContentType.Application.Json))
                        require((call.request.contentLength() ?: 0) <= 16_384)
                        val stream = call.receiveChannel()
                        val bytes = ByteArrayOutputStream()
                        val buffer = ByteArray(4096)
                        while (true) {
                            val count = stream.readAvailable(buffer, 0, buffer.size)
                            if (count < 0) break
                            require(bytes.size() + count <= 16_384)
                            bytes.write(buffer, 0, count)
                        }
                        jsonBase.decodeFromString<ReplenishmentReminderUpdate>(
                            bytes.toByteArray().decodeToString(throwOnInvalidSequence = true)
                        )
                    }
                )
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                call.genericResponseNoPayload(
                    HttpStatusCode.BadRequest,
                    replenishmentMessage("reminder_invalid"),
                )
                return@put
            }
        try {
            call.genericResponse(HttpStatusCode.OK, save(user, store, body, clock()))
        } catch (failure: ReplenishmentReminderFailure) {
            call.failed(failure)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (problem: Exception) {
            call.application.environment.log.error("Purchasing reminder save failed", problem)
            call.genericResponseNoPayload(
                HttpStatusCode.ServiceUnavailable,
                replenishmentMessage("reminder_unavailable"),
            )
        }
    }
}
