package kz.aita.server.dataModel.request

import kotlinx.serialization.Serializable

@Serializable
data class RefreshTokenRequestBody(
  val refresh_token: String,
  val deviceId: String? = null
)