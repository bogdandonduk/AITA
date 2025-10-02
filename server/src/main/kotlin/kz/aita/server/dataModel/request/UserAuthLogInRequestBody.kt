package kz.aita.server.dataModel.request

import kotlinx.serialization.Serializable

@Serializable
data class UserAuthLogInRequestBody(
  val login: String,
  val password: String,
  val deviceId: String? = null
)