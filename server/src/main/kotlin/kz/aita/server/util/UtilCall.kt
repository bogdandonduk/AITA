package kz.aita.server.util

import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.server.auth.UnauthorizedResponse
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingCall
import kz.aita.core.jsonBase
import kz.aita.model.dataModel.GenericResponseDataModel
import kz.aita.model.dataModel.LocalizedStringDataModel
import java.util.UUID

suspend fun RoutingCall.genericResponseNoPayload(
  status: HttpStatusCode,
  message: List<LocalizedStringDataModel>? = null
) {

  respond(
    status = status,
    message = GenericResponseDataModel(
      message = message?.let { jsonBase.encodeToString(it) },
      payload = null,
      negative = !status.isSuccess()
    )
  )
}

suspend inline fun <reified T> RoutingCall.genericResponse(
  status: HttpStatusCode,
  payload: T?,
  message: List<LocalizedStringDataModel>? = null
) {

  respond(
    status = status,
    message = GenericResponseDataModel(
      message = message?.let { jsonBase.encodeToString(it) },
      payload = payload?.let { jsonBase.encodeToString(it) },
      negative = !status.isSuccess()
    )
  )
}

suspend fun RoutingCall.checkPrincipal(): UUID? {
  val principal = principal<JWTPrincipal>()

  if (principal == null){
    respond(UnauthorizedResponse())
    return null
  }

  val userId = runCatching { UUID.fromString(principal.subject) }.getOrNull()

  if (userId == null) {
    respond(UnauthorizedResponse())
    return null
  }

  return userId
}


