package kz.aita.server.util

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kz.aita.GenericResponseDataModel
import kz.aita.LocalizedStringDataModel
import kz.aita.jsonBase
import java.util.*

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

  if (principal == null) {
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


