package kz.aita.server.dataModel.request

import kotlinx.serialization.Serializable

@Serializable
data class UserAuthSignUpRequestBody(
  val email: String,
  val phoneNumber: String,
  val password: String,
  val firstName: String,
  val lastName: String,
  val countryLocale: String
)