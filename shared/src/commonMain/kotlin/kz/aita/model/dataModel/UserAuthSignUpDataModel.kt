package kz.aita.model.dataModel

import kotlinx.serialization.Serializable

@Serializable
data class UserAuthSignUpDataModel(
  val phoneNumber: String,
  val email: String,
  val password: String,
  val firstName: String,
  val lastName: String,
  val countryLocale: String
)
