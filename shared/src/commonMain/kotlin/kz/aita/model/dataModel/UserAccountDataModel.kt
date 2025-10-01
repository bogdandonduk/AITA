package kz.aita.model.dataModel

import kotlinx.serialization.Serializable

@Serializable
data class UserAccountDataModel(
  val id: Long,
  val email: String,
  val phoneNumber: String,
  val countryLocale: String,
  val firstName: String,
  val lastName: String,
  val password: String,
  val registrationTime: Long
)