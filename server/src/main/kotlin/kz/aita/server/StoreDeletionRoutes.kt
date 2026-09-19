package kz.aita.server

import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import kz.aita.eventMessage
import java.util.UUID

/** Publish after the committed deletion, without consulting the now-deleted store's audience. */
internal fun Route.storeDeletionActions(deleteOwnedStore: suspend (UUID, String) -> Int) {
    delete("/delete") {
        val userId = call.checkPrincipal() ?: return@delete
        val result = deleteOwnedStore(userId, call.receiveAita<String>())
        when (result) {
            0 -> {
                RealtimeServerBus.publish(entity = "stores", reason = "store_deleted")
                call.genericResponseNoPayload(HttpStatusCode.OK, getResponse("12").message)
            }
            3 -> call.genericResponseNoPayload(HttpStatusCode.Conflict, eventMessage("store.shared_catalogue_in_use"))
            1, 2 -> call.respondAitaUnauthorized()
            else -> call.genericResponseNoPayload(HttpStatusCode.InternalServerError, getResponse("3").message)
        }
    }
}
