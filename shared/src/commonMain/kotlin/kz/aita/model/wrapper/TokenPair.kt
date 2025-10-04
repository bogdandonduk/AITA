package kz.aita.model.wrapper

import kotlinx.serialization.Serializable

@Serializable
data class TokenPair(
  val accessToken: String,
  val accessExpiresInSec: Long,     // seconds
  val refreshToken: String
)