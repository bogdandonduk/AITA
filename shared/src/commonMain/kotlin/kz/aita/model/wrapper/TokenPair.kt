package kz.aita.model.wrapper

import kotlinx.serialization.Serializable

@Serializable
data class TokenPair(
  val accessToken: String,
  val accessExpiryTime: Long,
  val refreshToken: String,
  val refreshExpiryTime: Long
)
