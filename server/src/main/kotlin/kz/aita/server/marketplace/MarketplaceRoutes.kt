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
    crossinline after: suspend (T) -> Unit = {},
    crossinline work: MarketplaceRepository.() -> T
) {
    response.header("Cache-Control", "private, no-store, max-age=0")
    try {
        val result = newSuspendedTransaction(aitaServerIoContext,
            transactionIsolation = Connection.TRANSACTION_REPEATABLE_READ, readOnly = readOnly) {
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

internal fun Route.marketplaceRoutes() {
    authenticate("auth-jwt") {
        route("/market") {
            get("/offers") {
                val user = call.checkPrincipal() ?: return@get
                call.marketResult(readOnly = true) { browse(user, call.request.queryParameters["q"].orEmpty(), call.request.queryParameters["city"].orEmpty(),
                    call.request.queryParameters["after"], call.request.queryParameters["gtin"], call.request.queryParameters["store"]) }
            }
            get("/offers/{offerId}") {
                val user = call.checkPrincipal() ?: return@get
                call.marketResult(readOnly = true) { offer(user, call.parameters["offerId"].orEmpty()) }
            }
            get("/shops/{storeId}") {
                call.checkPrincipal() ?: return@get
                call.marketResult(readOnly = true) { publicShop(call.parameters["storeId"].orEmpty()) }
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
                    if (result.accepted) RealtimeServerBus.publish(entity = "market/shopping-list", userId = user.toString(), reason = "shopping_list_changed")
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
                call.marketResult(after = { RealtimeServerBus.publish(entity="market/saved",userId=user.toString(),reason="saved_offers_changed") }) {
                    updateSaved(user,body)
                }
            }
            post("/saved/clear-unavailable") {
                val user = call.checkPrincipal() ?: return@post
                call.marketResult(after = { RealtimeServerBus.publish(entity="market/saved",userId=user.toString(),reason="saved_offers_changed") }) {
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
