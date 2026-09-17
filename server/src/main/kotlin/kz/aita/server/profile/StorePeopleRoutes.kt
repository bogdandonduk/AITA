package kz.aita.server.profile

import io.ktor.http.*
import io.ktor.server.auth.authenticate
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CancellationException
import kz.aita.eventMessage
import kz.aita.server.checkPrincipal
import kz.aita.server.genericResponse
import kz.aita.server.genericResponseNoPayload
import java.util.UUID

internal fun Route.storePeopleActions(repository:StorePeopleRepository,budget:ProfilePhotoBudget=ProfilePhotoBudget()) {
    get("/stores/{store}/people/{person}") {
        val viewer=call.checkPrincipal() ?: return@get
        call.response.header(HttpHeaders.CacheControl,"private, no-store")
        val store=runCatching {UUID.fromString(call.parameters["store"])}.getOrNull()
        val person=runCatching {UUID.fromString(call.parameters["person"])}.getOrNull()
        val rawDays=call.request.queryParameters["days"]
        val days=if(rawDays==null)30 else rawDays.toIntOrNull()
        if(store==null || person==null || days==null || days !in setOf(7,30,90)) {
            call.genericResponseNoPayload(HttpStatusCode.BadRequest,eventMessage("people.ui.unavailable"));return@get
        }
        try {
            val result=budget.bounded(viewer){repository.read(viewer,store,person,days)}
            if(result==null)call.genericResponseNoPayload(HttpStatusCode.NotFound,eventMessage("people.ui.unavailable"))
            else call.genericResponse(HttpStatusCode.OK,result)
        } catch(cancel:CancellationException){throw cancel}
        catch(problem:PhotoProblem){call.genericResponseNoPayload(HttpStatusCode.TooManyRequests,eventMessage("people.ui.unavailable"))}
        catch(_:Exception){call.genericResponseNoPayload(HttpStatusCode.ServiceUnavailable,eventMessage("people.ui.unavailable"))}
    }
}
fun Route.installStorePeopleRoutes(){authenticate("auth-jwt"){storePeopleActions(DatabaseStorePeople())}}
