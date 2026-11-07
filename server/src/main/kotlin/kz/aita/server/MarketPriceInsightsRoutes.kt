package kz.aita.server

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.sql.Connection
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kz.aita.*
import kz.aita.server.marketplace.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

internal fun priceInsightsReaderInsideTransaction(user: UUID, store: UUID): Boolean =
    Users.select(Users.isActive).where { Users.id eq user }.singleOrNull()?.get(Users.isActive) == true &&
        listOf(STORE_PERMISSION_ANALYTICS_VIEW, STORE_PERMISSION_STOCK_READ).all {
            userCanUseStoreActionInsideTransaction(user, store, it, requireWorkshift = false)
        }

internal suspend fun readMarketPriceInsights(user: UUID, store: UUID, request: MarketPriceRequest, now: Long): MarketPriceInsightsPage =
    newSuspendedTransaction(aitaServerIoContext, transactionIsolation = Connection.TRANSACTION_REPEATABLE_READ, readOnly = true) {
        maxAttempts = 1
        exec("SET LOCAL statement_timeout = '5s'")
        MarketplaceRepository(TransactionManager.current().connection.connection as Connection,
            ::marketplaceStoreOwnerInsideTransaction, ::priceInsightsReaderInsideTransaction).priceInsights(user, store, request, now)
    }

internal fun Route.marketPriceInsightsRoute(
    read: suspend (UUID, UUID, MarketPriceRequest, Long) -> MarketPriceInsightsPage = ::readMarketPriceInsights,
    principal: suspend RoutingCall.() -> UUID? = { checkPrincipal() },
    clock: () -> Long = System::currentTimeMillis,
) {
    val gate = MarketBasketReadGate(requestsPerMinute = 30)
    get("/market-prices") {
        call.response.header(HttpHeaders.CacheControl, "private, no-store")
        val user = call.principal() ?: return@get
        val storeRaw = call.request.header("store_id").orEmpty()
        val store = runCatching { UUID.fromString(storeRaw) }.getOrNull()?.takeIf { it.toString().equals(storeRaw, true) }
        val q = call.request.queryParameters
        val request = MarketPriceRequest(country = q["country"] ?: "kz", city = q["city"].orEmpty(), search = q["search"].orEmpty(),
            itemId = q["itemId"], gtin = q["gtin"], after = q["after"], limit = q["limit"]?.toIntOrNull() ?: if (q["limit"] == null) MARKET_PRICE_PAGE_SIZE else -1)
            .normalizedMarketPriceRequest()
        if (store == null || request == null) {
            call.genericResponseNoPayload(HttpStatusCode.BadRequest, marketPriceMessage("invalid")); return@get
        }
        val allowed = gate.acquire(user, System.nanoTime() / 1_000_000)
        if (!allowed.allowed) {
            call.response.header(HttpHeaders.RetryAfter, allowed.retryAfterSeconds.toString())
            call.genericResponseNoPayload(HttpStatusCode.TooManyRequests, marketPriceMessage("busy")); return@get
        }
        try {
            call.genericResponse(HttpStatusCode.OK, read(user, store, request, clock()))
        } catch (cancel: CancellationException) { throw cancel }
        catch (failure: MarketFailure) {
            call.genericResponseNoPayload(HttpStatusCode.fromValue(failure.status),
                marketPriceMessage(if (failure.status == 403) "access" else if (failure.status == 400) "invalid" else "unavailable"))
        } catch (problem: Exception) {
            call.application.environment.log.error("Market price report failed", problem)
            call.genericResponseNoPayload(HttpStatusCode.ServiceUnavailable, marketPriceMessage("unavailable"))
        } finally { gate.release(user) }
    }
}
