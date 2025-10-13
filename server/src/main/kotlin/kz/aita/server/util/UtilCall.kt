package kz.aita.server.util

import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingCall
import kz.aita.model.dataModel.GenericResponseDataModel
import kz.aita.model.dataModel.LocalizedStringDataModel

suspend fun RoutingCall.genericResponse(
  status: HttpStatusCode,
  payload: Any? = null,
  message: List<LocalizedStringDataModel>? = null
) {
  respond(
    status = status,
    message = GenericResponseDataModel(
      message = message,
      payload = payload,
      negative = !status.isSuccess()
    )
  )
}