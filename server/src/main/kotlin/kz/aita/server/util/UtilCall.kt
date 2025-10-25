package kz.aita.server.util

import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingCall
import kz.aita.core.jsonBase
import kz.aita.model.dataModel.GenericResponseDataModel
import kz.aita.model.dataModel.LocalizedStringDataModel

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

