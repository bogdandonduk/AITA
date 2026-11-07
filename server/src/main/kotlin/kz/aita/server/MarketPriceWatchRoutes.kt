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
import kz.aita.server.marketplace.MarketBasketReadGate
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

internal fun Route.marketPriceWatchRoutes(
    principal: suspend RoutingCall.() -> UUID? = { checkPrincipal() },
    read: suspend (UUID, UUID) -> MarketPriceWatchState = { user, store ->
        newSuspendedTransaction(aitaServerIoContext) { maxAttempts = 1; exec("SET LOCAL statement_timeout = '5s'"); readPriceWatchInsideTransaction(user, store) }
    },
    save: suspend (UUID, UUID, MarketPriceWatchUpdate, Long) -> MarketPriceWatchState =
        { user, store, body, now ->
            newSuspendedTransaction(aitaServerIoContext, transactionIsolation = java.sql.Connection.TRANSACTION_REPEATABLE_READ) {
                maxAttempts = 1
                exec("SET LOCAL statement_timeout = '10000ms'"); exec("SET LOCAL lock_timeout = '2s'")
                savePriceWatchInsideTransaction(user, store, body, now)
            }
        },
    clock: () -> Long = System::currentTimeMillis,
) {
    val limiter = AuthSlidingWindowLimiter(10_000)
    val reads = MarketBasketReadGate(requestsPerMinute = 30)
    suspend fun RoutingCall.admit(user: UUID): Boolean {
        val decision = reads.acquire(user, System.nanoTime() / 1_000_000)
        if (!decision.allowed) {
            response.header(HttpHeaders.RetryAfter, decision.retryAfterSeconds.toString())
            genericResponseNoPayload(HttpStatusCode.TooManyRequests, marketPriceMessage("busy"))
        }
        return decision.allowed
    }
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
                marketPriceMessage("watch_invalid"),
            )
            return null
        }
        if (!limiter.allow(user.toString(), 30, clock(), 60_000)) {
            response.header(HttpHeaders.RetryAfter, "60")
            genericResponseNoPayload(HttpStatusCode.TooManyRequests, marketPriceMessage("busy"))
            return null
        }
        return user to store
    }
    suspend fun RoutingCall.failed(problem: MarketPriceWatchFailure) {
        genericResponseNoPayload(
            when (problem.kind) {
                "access" -> HttpStatusCode.Forbidden
                "watch_conflict" -> HttpStatusCode.Conflict
                else -> HttpStatusCode.UnprocessableEntity
            },
            marketPriceMessage(problem.kind),
        )
    }
    get("/market-prices/watch") {
        val (user, store) = call.identity() ?: return@get
        if (!call.admit(user)) return@get
        try {
            call.genericResponse(HttpStatusCode.OK, read(user, store))
        } catch (failure: MarketPriceWatchFailure) {
            call.failed(failure)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (problem: Exception) {
            call.application.environment.log.error("Market price watch read failed", problem)
            call.genericResponseNoPayload(
                HttpStatusCode.ServiceUnavailable,
                marketPriceMessage("watch_unavailable"),
            )
        } finally { reads.release(user) }
    }
    put("/market-prices/watch") {
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
                        jsonBase.decodeFromString<MarketPriceWatchUpdate>(
                            bytes.toByteArray().decodeToString(throwOnInvalidSequence = true)
                        )
                    }
                )
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                call.genericResponseNoPayload(
                    HttpStatusCode.BadRequest,
                    marketPriceMessage("watch_invalid"),
                )
                return@put
            }
        if (!call.admit(user)) return@put
        try {
            call.genericResponse(HttpStatusCode.OK, save(user, store, body, clock()))
        } catch (failure: MarketPriceWatchFailure) {
            call.failed(failure)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (problem: Exception) {
            call.application.environment.log.error("Market price watch save failed", problem)
            call.genericResponseNoPayload(
                HttpStatusCode.ServiceUnavailable,
                marketPriceMessage("watch_unavailable"),
            )
        } finally { reads.release(user) }
    }
}
