package kz.aita.server.marketplace

import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.*
import io.ktor.server.response.header
import kz.aita.*
import kz.aita.server.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.sql.Connection
import java.util.UUID

private val deliveryReadGate = MarketBasketReadGate(requestsPerMinute = 100)
private val deliveryWriteGate = MarketBasketReadGate(requestsPerMinute = 60)

private suspend fun RoutingCall.deliveryGuard(user: UUID, write: Boolean, block: suspend () -> Unit) {
    response.header("Cache-Control", "private, no-store, max-age=0")
    val gate = if (write) deliveryWriteGate else deliveryReadGate
    val admission = gate.acquire(user, System.nanoTime() / 1_000_000)
    if (!admission.allowed) {
        response.header("Retry-After", admission.retryAfterSeconds.toString())
        genericResponseNoPayload(HttpStatusCode.TooManyRequests, eventMessage("delivery.busy"))
        return
    }
    try { block() } finally { gate.release(user) }
}
private fun MarketplaceRepository.deliveries(): MarketDeliveryRepository {
    val db = TransactionManager.current().connection.connection as Connection
    db.createStatement().use { it.execute("SET LOCAL statement_timeout='5s'; SET LOCAL lock_timeout='5s'") }
    return MarketDeliveryRepository(db, this, ::marketplaceStoreOwnerInsideTransaction)
}
private suspend fun deliveryChanged(order: MarketDeliveryOrder) {
    RealtimeServerBus.publish(entity="market/delivery", userId=order.buyerId, reason="delivery_changed")
    order.courierId?.let { RealtimeServerBus.publish(entity="market/delivery",userId=it,reason="delivery_changed") }
    RealtimeServerBus.publish(entity="market/delivery",storeId=order.branchStoreId,reason="delivery_changed")
}

internal fun Route.marketDeliveryRoutes() {
    route("/delivery") {
        get("/policy/{store}") {
            val user=call.checkPrincipal() ?: return@get
            call.deliveryGuard(user,false) { call.marketResult(readOnly=true) { deliveries().policy(user,call.parameters["store"].orEmpty(),false) } }
        }
        get("/seller/policy") {
            val user=call.checkPrincipal() ?: return@get
            call.deliveryGuard(user,false) { call.marketResult(readOnly=true) { deliveries().policy(user,call.request.headers["store_id"].orEmpty(),true) } }
        }
        put("/seller/policy") {
            val user=call.checkPrincipal() ?: return@put
            call.deliveryGuard(user,true) {
                val body=call.receiveAita<MarketDeliveryPolicy>()
                call.marketResult(after={ saved: MarketDeliveryPolicy ->
                    RealtimeServerBus.publish(entity="market/delivery",storeId=saved.branchStoreId,reason="delivery_policy_changed")
                }) { deliveries().savePolicy(user,body) }
            }
        }
        get("/orders") {
            val user=call.checkPrincipal() ?: return@get
            call.deliveryGuard(user,false) { call.marketResult(readOnly=true) {
                deliveries().page(user,call.request.queryParameters["view"] ?: "buyer",
                    call.request.queryParameters["store"],call.request.queryParameters["before"])
            } }
        }
        get("/orders/{id}") {
            val user=call.checkPrincipal() ?: return@get
            call.deliveryGuard(user,false) { call.marketResult(readOnly=true) { deliveries().get(user,call.parameters["id"].orEmpty()) } }
        }
        post("/commands") {
            val user=call.checkPrincipal() ?: return@post
            call.deliveryGuard(user,true) {
                val body=call.receiveAita<MarketDeliveryCommand>()
                call.marketResult(after={ outcome: MarketDeliveryOutcome -> if (!outcome.replayed) deliveryChanged(outcome.order) }) {
                    deliveries().command(user,body)
                }
            }
        }
        put("/orders/{id}/position") {
            val user=call.checkPrincipal() ?: return@put
            call.deliveryGuard(user,true) {
                val body=call.receiveAita<MarketCourierLocationUpdate>()
                call.marketResult(after={ order: MarketDeliveryOrder -> deliveryChanged(order) }) {
                    deliveries().location(user,call.parameters["id"].orEmpty(),body)
                }
            }
        }
    }
}
