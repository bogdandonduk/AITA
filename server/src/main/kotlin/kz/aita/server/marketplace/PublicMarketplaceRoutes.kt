package kz.aita.server.marketplace

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.*
import io.ktor.server.response.header
import io.ktor.server.routing.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Semaphore
import kz.aita.*
import kz.aita.server.*
import kz.aita.server.auth.AuthSlidingWindowLimiter
import kz.aita.server.auth.aitaAuthClientIp
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.sql.Connection

private val publicMarketBudget = AuthSlidingWindowLimiter()
private val publicMarketReaders = Semaphore(8)

/** One explicitly anonymous, read-only surface. Seller tools, saved lists and orders stay authenticated. */
internal fun Route.publicMarketplaceRoutes() {
    post("/market/public/discovery") {
        call.response.header("Cache-Control", "no-store")
        val now = System.currentTimeMillis()
        val peer = aitaAuthClientIp(call.request.local.remoteAddress,
            call.request.headers["X-Forwarded-For"], call.request.headers["X-AITA-Edge"])
        if (!publicMarketBudget.allow("all", 600, now, 60_000L) ||
            !publicMarketBudget.allow("peer:$peer", 120, now, 60_000L) || !publicMarketReaders.tryAcquire()) {
            call.response.header("Retry-After", "30")
            call.genericResponseNoPayload(HttpStatusCode.TooManyRequests, eventMessage("market.discovery_busy")); return@post
        }
        try {
            val request = call.receiveAita<MarketDiscoveryRequest>()
            if (request.query.savedOnly || request.limit > 120 || !request.isValidDiscoveryRequest()) {
                call.genericResponseNoPayload(HttpStatusCode.BadRequest, eventMessage("market.discovery_invalid")); return@post
            }
            val result = newSuspendedTransaction(aitaServerIoContext, transactionIsolation = Connection.TRANSACTION_REPEATABLE_READ, readOnly = true) {
                val db = TransactionManager.current().connection.connection as Connection
                db.createStatement().use { it.execute("SET LOCAL statement_timeout = '5s'") }
                MarketplaceRepository(db, ::marketplaceStoreOwnerInsideTransaction, ::marketplaceStockReaderInsideTransaction)
                    .run { withDiscoveryReadBudget { discover(null, request) } }
            }
            call.genericResponse(HttpStatusCode.OK, PublicMarketDiscovery(result))
        } catch (c: CancellationException) { throw c }
        catch (f: MarketFailure) { call.genericResponseNoPayload(HttpStatusCode.fromValue(f.status), eventMessage(f.key)) }
        finally { publicMarketReaders.release() }
    }
}
