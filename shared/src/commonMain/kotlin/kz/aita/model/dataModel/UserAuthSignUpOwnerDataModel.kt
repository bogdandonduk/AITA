package kz.aita.model.dataModel

import kotlinx.serialization.Serializable

@Serializable
data class UserAuthSignUpOwnerDataModel(
  val email: String,
  val phoneNumber: String,
  val password: String,
  val firstName: String,
  val lastName: String,
  val countryLocale: String
)
