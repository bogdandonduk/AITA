package kz.aita.server.marketplace

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.*
import io.ktor.server.response.header
import kz.aita.*
import kz.aita.server.*
import kotlinx.coroutines.CancellationException
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.sql.Connection

private suspend inline fun <reified T> RoutingCall.marketResult(
    readOnly: Boolean = false,
    transactionIsolation: Int = Connection.TRANSACTION_REPEATABLE_READ,
    noinline after: suspend (T) -> Unit = {},
    crossinline work: MarketplaceRepository.() -> T
) {
    response.header("Cache-Control", "private, no-store, max-age=0")
    try {
        val result = newSuspendedTransaction(aitaServerIoContext,
            transactionIsolation = transactionIsolation, readOnly = readOnly) {
            maxAttempts = 3
            // Public visibility, stock projection and saved markers share one snapshot. A second
            // query must not accidentally mix a published row with a just-created private draft.
            MarketplaceRepository(TransactionManager.current().connection.connection as Connection,
                ::marketplaceStoreOwnerInsideTransaction).work()
        }
        // Invalidation only AFTER commit; no private inventory identifiers in public events.
        after(result)
        genericResponse(HttpStatusCode.OK, result)
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (failure: MarketFailure) { genericResponseNoPayload(HttpStatusCode.fromValue(failure.status), eventMessage(failure.key)) }
}

// Search typing and periodic refresh need a different allowance from explicit basket planning.
private val offerDetailReadGate = MarketBasketReadGate(requestsPerMinute = 60)
private val shopDirectoryReadGate = MarketBasketReadGate(requestsPerMinute = 60)
private val discoveryReadGate = MarketBasketReadGate(requestsPerMinute = 60)
private val comparisonReadGate = MarketBasketReadGate()
private val basketReadGate = MarketBasketReadGate()
private val basketApplyGate = MarketBasketReadGate()
private val shoppingCancelGate = MarketBasketReadGate()
// History reads share an allowance, but must never starve Retry/Check result/Cancel.
private val shoppingActivityReadGate = MarketBasketReadGate(requestsPerMinute = 60)

internal fun Route.marketplaceRoutes() {
    authenticate("auth-jwt") {
        route("/market") {
            post("/discovery") {
                val user = call.checkPrincipal() ?: return@post
                call.response.header("Cache-Control", "private, no-store, max-age=0")
                val admission = discoveryReadGate.acquire(user, System.nanoTime() / 1_000_000L)
                if (!admission.allowed) {
                    call.response.header("Retry-After", admission.retryAfterSeconds.toString())
                    call.genericResponseNoPayload(HttpStatusCode.TooManyRequests, eventMessage("market.discovery_busy"))
                    return@post
                }
                try {
                    val body = call.receiveAita<MarketDiscoveryRequest>()
                    call.marketResult(readOnly = true, transactionIsolation = Connection.TRANSACTION_REPEATABLE_READ) {
                        val db = TransactionManager.current().connection.connection as Connection
                        db.createStatement().use { it.execute("SET LOCAL statement_timeout = '5s'") }
                        withDiscoveryReadBudget { discover(user, body) }
                    }
                } finally { discoveryReadGate.release(user) }
            }
            post("/shops/search") {
                val user = call.checkPrincipal() ?: return@post
                call.response.header("Cache-Control", "private, no-store, max-age=0")
                val admission = shopDirectoryReadGate.acquire(user, System.nanoTime() / 1_000_000L)
                if (!admission.allowed) {
                    call.response.header("Retry-After", admission.retryAfterSeconds.toString())
                    call.genericResponseNoPayload(HttpStatusCode.TooManyRequests, eventMessage("market.shops_busy"))
                    return@post
                }
                try {
                    val body = call.receiveAita<MarketShopDirectoryRequest>()
                    call.marketResult(readOnly = true, transactionIsolation = Connection.TRANSACTION_REPEATABLE_READ) {
                        val db = TransactionManager.current().connection.connection as Connection
                        db.createStatement().use { it.execute("SET LOCAL statement_timeout = '5s'") }
                        MarketShopDirectoryRepository(db).search(user, body)
                    }
                } finally { shopDirectoryReadGate.release(user) }
            }
            get("/offers") {
                val user = call.checkPrincipal() ?: return@get
                call.marketResult(readOnly = true) { browse(user, call.request.queryParameters["q"].orEmpty(), call.request.queryParameters["city"].orEmpty(),
                    call.request.queryParameters["after"], call.request.queryParameters["gtin"], call.request.queryParameters["store"]) }
            }
            get("/offers/{offerId}/detail") {
                val user = call.checkPrincipal() ?: return@get
                call.response.header("Cache-Control", "private, no-store, max-age=0")
                val admission = offerDetailReadGate.acquire(user, System.nanoTime() / 1_000_000L)
                if (!admission.allowed) {
                    call.response.header("Retry-After", admission.retryAfterSeconds.toString())
                    call.genericResponseNoPayload(HttpStatusCode.TooManyRequests, eventMessage("market.detail_busy"))
                    return@get
                }
                try {
                    call.marketResult(readOnly = true, transactionIsolation = Connection.TRANSACTION_REPEATABLE_READ) {
                        val db = TransactionManager.current().connection.connection as Connection
                        db.createStatement().use { it.execute("SET LOCAL statement_timeout = '5s'") }
                        withOfferDetailReadBudget { offerDetail(user, call.parameters["offerId"].orEmpty()) }
                    }
                } finally { offerDetailReadGate.release(user) }
            }
            get("/offers/{offerId}") {
                val user = call.checkPrincipal() ?: return@get
                call.marketResult(readOnly = true) { offer(user, call.parameters["offerId"].orEmpty()) }
            }
            get("/shops/{storeId}") {
                call.checkPrincipal() ?: return@get
                call.marketResult(readOnly = true) { publicShop(call.parameters["storeId"].orEmpty()) }
            }
            post("/compare") {
                val user = call.checkPrincipal() ?: return@post
                val body = call.receiveAita<MarketComparisonRequest>()
                call.marketResult(readOnly = true) {
                    MarketShoppingRepository(TransactionManager.current().connection.connection as Connection, this).comparison(user, body)
                }
            }
            post("/compare/window") {
                val user = call.checkPrincipal() ?: return@post
                call.response.header("Cache-Control", "private, no-store, max-age=0")
                val admission = comparisonReadGate.acquire(user, System.nanoTime() / 1_000_000L)
                if (!admission.allowed) {
                    call.response.header("Retry-After", admission.retryAfterSeconds.toString())
                    call.genericResponseNoPayload(HttpStatusCode.TooManyRequests, eventMessage("market.comparison_window_busy"))
                    return@post
                }
                try {
                    val body = call.receiveAita<MarketComparisonWindowRequest>()
                    call.marketResult(readOnly = true, transactionIsolation = Connection.TRANSACTION_REPEATABLE_READ) {
                        val db = TransactionManager.current().connection.connection as Connection
                        db.createStatement().use { it.execute("SET LOCAL statement_timeout = '5s'") }
                        MarketShoppingRepository(db, this).comparisonWindow(user, body)
                    }
                } finally { comparisonReadGate.release(user) }
            }
            post("/shopping-list/result") {
                val user = call.checkPrincipal() ?: return@post
                val body = call.receiveAita<MarketShoppingCommand>()
                call.marketResult(readOnly = true) {
                    val db = TransactionManager.current().connection.connection as Connection
                    db.createStatement().use { it.execute("SET LOCAL statement_timeout = '5s'") }
                    MarketShoppingRepository(db, this).lookup(user, body)
                }
            }
            post("/shopping-list/cancel") {
                val user = call.checkPrincipal() ?: return@post
                call.response.header("Cache-Control", "private, no-store, max-age=0")
                val admission = shoppingCancelGate.acquire(user, System.nanoTime() / 1_000_000L)
                if (!admission.allowed) {
                    call.response.header("Retry-After", admission.retryAfterSeconds.toString())
                    call.genericResponseNoPayload(HttpStatusCode.TooManyRequests, eventMessage("market.shopping_cancel_failed"))
                    return@post
                }
                try {
                    val body = call.receiveAita<MarketShoppingCommand>()
                    call.marketResult(after = { result: MarketShoppingOutcome ->
                        if (!result.replayed) RealtimeServerBus.publish(
                            entity = "market/shopping-activity", userId = user.toString(), reason = "shopping_command_cancelled")
                    }) {
                        val db = TransactionManager.current().connection.connection as Connection
                        db.createStatement().use { it.execute("SET LOCAL statement_timeout = '5s'; SET LOCAL lock_timeout = '5s'") }
                        MarketShoppingRepository(db, this).cancel(user, call.currentJwtSessionId(), body)
                    }
                } finally { shoppingCancelGate.release(user) }
            }
            get("/shopping-list/activity/{commandId}") {
                val user = call.checkPrincipal() ?: return@get
                call.response.header("Cache-Control", "private, no-store, max-age=0")
                val admission = shoppingActivityReadGate.acquire(user, System.nanoTime() / 1_000_000L)
                if (!admission.allowed) {
                    call.response.header("Retry-After", admission.retryAfterSeconds.toString())
                    call.genericResponseNoPayload(HttpStatusCode.TooManyRequests, eventMessage("market.activity_busy"))
                    return@get
                }
                try {
                    call.marketResult(readOnly = true) {
                        val db = TransactionManager.current().connection.connection as Connection
                        db.createStatement().use { it.execute("SET LOCAL statement_timeout = '5s'") }
                        MarketShoppingRepository(db, this).activityDetail(user, call.parameters["commandId"].orEmpty())
                    }
                } finally { shoppingActivityReadGate.release(user) }
            }
            post("/shopping-list/activity/search") {
                val user = call.checkPrincipal() ?: return@post
                call.response.header("Cache-Control", "private, no-store, max-age=0")
                val admission = shoppingActivityReadGate.acquire(user, System.nanoTime() / 1_000_000L)
                if (!admission.allowed) {
                    call.response.header("Retry-After", admission.retryAfterSeconds.toString())
                    call.genericResponseNoPayload(HttpStatusCode.TooManyRequests, eventMessage("market.activity_busy"))
                    return@post
                }
                try {
                    val body = call.receiveAita<MarketShoppingActivitySearchRequest>()
                    call.marketResult(readOnly = true) {
                        val db = TransactionManager.current().connection.connection as Connection
                        db.createStatement().use { it.execute("SET LOCAL statement_timeout = '5s'") }
                        MarketShoppingRepository(db, this).activitySearch(user, body)
                    }
                } finally { shoppingActivityReadGate.release(user) }
            }
            post("/shopping-list/activity") {
                val user = call.checkPrincipal() ?: return@post
                call.response.header("Cache-Control", "private, no-store, max-age=0")
                val admission = shoppingActivityReadGate.acquire(user, System.nanoTime() / 1_000_000L)
                if (!admission.allowed) {
                    call.response.header("Retry-After", admission.retryAfterSeconds.toString())
                    call.genericResponseNoPayload(HttpStatusCode.TooManyRequests, eventMessage("market.activity_busy"))
                    return@post
                }
                try {
                    val body = call.receiveAita<MarketShoppingActivityRequest>()
                    call.marketResult(readOnly = true) {
                        val db = TransactionManager.current().connection.connection as Connection
                        db.createStatement().use { it.execute("SET LOCAL statement_timeout = '5s'") }
                        MarketShoppingRepository(db, this).activity(user, body)
                    }
                } finally { shoppingActivityReadGate.release(user) }
            }
            post("/shopping-list/plan") {
                val user = call.checkPrincipal() ?: return@post
                call.response.header("Cache-Control", "private, no-store, max-age=0")
                val admission = basketReadGate.acquire(user, System.nanoTime() / 1_000_000L)
                if (!admission.allowed) {
                    call.response.header("Retry-After", admission.retryAfterSeconds.toString())
                    call.genericResponseNoPayload(HttpStatusCode.TooManyRequests, eventMessage("market.basket_busy"))
                    return@post
                }
                try {
                    val body = call.receiveAita<MarketBasketRequest>()
                    call.marketResult(readOnly = true) {
                        val db = TransactionManager.current().connection.connection as Connection
                        // Transaction-local bound: do not change the pool connection's next request.
                        db.createStatement().use { it.execute("SET LOCAL statement_timeout = '5s'") }
                        MarketShoppingRepository(db, this).basketPlan(user, body)
                    }
                } finally { basketReadGate.release(user) }
            }
            put("/shopping-list/apply-plan") {
                val user = call.checkPrincipal() ?: return@put
                call.response.header("Cache-Control", "private, no-store, max-age=0")
                val admission = basketApplyGate.acquire(user, System.nanoTime() / 1_000_000L)
                if (!admission.allowed) {
                    call.response.header("Retry-After", admission.retryAfterSeconds.toString())
                    call.genericResponseNoPayload(HttpStatusCode.TooManyRequests, eventMessage("market.basket_apply_busy"))
                    return@put
                }
                try {
                    val body = call.receiveAita<MarketShoppingCommand>()
                    call.marketResult(after = { result: MarketShoppingOutcome ->
                        if (!result.replayed) RealtimeServerBus.publish(
                            entity = if (result.accepted) "market/shopping-list" else "market/shopping-activity",
                            userId = user.toString(), reason = "shopping_command_recorded")
                    }) {
                        // A timeout rolls back the ENTIRE command. Local limits do not leak to
                        // the pooled connection's next request; an uncertain client retries its ID.
                        val db = TransactionManager.current().connection.connection as Connection
                        db.createStatement().use { it.execute("SET LOCAL statement_timeout = '5s'; SET LOCAL lock_timeout = '5s'") }
                        MarketShoppingRepository(db, this).applyBasket(user, call.currentJwtSessionId(), body)
                    }
                } finally { basketApplyGate.release(user) }
            }
            put("/shopping-list/replace") {
                val user = call.checkPrincipal() ?: return@put
                val body = call.receiveAita<MarketShoppingCommand>()
                call.marketResult(after = { result: MarketShoppingOutcome ->
                    if (!result.replayed) RealtimeServerBus.publish(
                            entity = if (result.accepted) "market/shopping-list" else "market/shopping-activity",
                            userId = user.toString(), reason = "shopping_command_recorded")
                }) {
                    MarketShoppingRepository(TransactionManager.current().connection.connection as Connection, this)
                        .replace(user, call.currentJwtSessionId(), body)
                }
            }
            get("/shopping-list") {
                val user = call.checkPrincipal() ?: return@get
                call.marketResult(readOnly = true) {
                    MarketShoppingRepository(TransactionManager.current().connection.connection as Connection, this).snapshot(user)
                }
            }
            put("/shopping-list") {
                val user = call.checkPrincipal() ?: return@put
                val body = call.receiveAita<MarketShoppingCommand>()
                call.marketResult(after = { result: MarketShoppingOutcome ->
                    if (!result.replayed) RealtimeServerBus.publish(
                            entity = if (result.accepted) "market/shopping-list" else "market/shopping-activity",
                            userId = user.toString(), reason = "shopping_command_recorded")
                }) {
                    MarketShoppingRepository(TransactionManager.current().connection.connection as Connection, this)
                        .apply(user, call.currentJwtSessionId(), body)
                }
            }
            get("/saved") {
                val user = call.checkPrincipal() ?: return@get
                call.marketResult(readOnly = true) { saved(user) }
            }
            put("/saved") {
                val user = call.checkPrincipal() ?: return@put
                val body = call.receiveAita<MarketSavedUpdate>()
                // A users-row lock alone does NOT refresh a repeatable-read snapshot. SSI
                // makes overlapping count/check/write transactions retry as a whole.
                call.marketResult(transactionIsolation = Connection.TRANSACTION_SERIALIZABLE,
                    after = { RealtimeServerBus.publish(entity="market/saved",userId=user.toString(),reason="saved_offers_changed") }) {
                    updateSaved(user,body)
                }
            }
            post("/saved/clear-unavailable") {
                val user = call.checkPrincipal() ?: return@post
                // A users-row lock alone does NOT refresh a repeatable-read snapshot. SSI
                // makes overlapping count/check/write transactions retry as a whole.
                call.marketResult(transactionIsolation = Connection.TRANSACTION_SERIALIZABLE,
                    after = { RealtimeServerBus.publish(entity="market/saved",userId=user.toString(),reason="saved_offers_changed") }) {
                    clearUnavailableSaved(user)
                }
            }
            get("/seller") {
                val user = call.checkPrincipal() ?: return@get
                call.marketResult { dashboard(user,marketUuid(call.request.headers["store_id"].orEmpty())) }
            }
            put("/seller/storefront") {
                val user = call.checkPrincipal() ?: return@put
                val body = call.receiveAita<MarketStorefrontUpdate>()
                call.marketResult(after = {
                    RealtimeServerBus.publish(entity="market/catalog",reason="shop_window_changed")
                    RealtimeServerBus.publish(entity="market/seller",storeId=it.storefront.storeId,reason="shop_window_changed")
                }) { updateStorefront(user,call.currentJwtSessionId(),body) }
            }
            put("/seller/listing") {
                val user = call.checkPrincipal() ?: return@put
                val body = call.receiveAita<MarketListingUpdate>()
                call.marketResult(after = {
                    RealtimeServerBus.publish(entity="market/catalog",reason="shop_window_changed")
                    RealtimeServerBus.publish(entity="market/seller",storeId=it.storefront.storeId,reason="shop_window_changed")
                }) { updateListing(user,call.currentJwtSessionId(),body) }
            }
        }
    }
}

/** An opt-in shop-window change becomes an identifier-free public invalidation. */
internal suspend fun publishMarketplaceStockChange(storeText: String?) {
    val store = storeText?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() } ?: return
    try {
        val published = newSuspendedTransaction(aitaServerIoContext) {
        val connection = TransactionManager.current().connection.connection as Connection
        connection.prepareStatement("""SELECT EXISTS(SELECT 1 FROM marketplace_storefronts f
            JOIN stores s ON s.id=f.store_id WHERE f.is_published AND (f.store_id=? OR s.parent_store_id=?)
            AND EXISTS(SELECT 1 FROM marketplace_listings l WHERE l.store_id=f.store_id AND l.is_published))""").use { statement ->
            statement.setObject(1,store); statement.setObject(2,store)
            statement.executeQuery().use { rows -> rows.next(); rows.getBoolean(1) }
        }
    }
        if (published) RealtimeServerBus.publish(entity="market/catalog",reason="public_inventory_changed")
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) {
        // The stock/billing transaction has ALREADY committed. Optional public invalidation must
        // never turn that success into a retryable business failure. Focused catalogue polling repairs it.
        org.slf4j.LoggerFactory.getLogger("AITA.Marketplace").warn("Public catalogue invalidation deferred; committed business state is unchanged")
    }
}
