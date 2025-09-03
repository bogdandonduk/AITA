package kz.aita.model.dataModel



data class UserAuthAccountDataModel(
  val id: Long,
  val email: String,
  val countryCode: String,
  val language: String,
  val currency: String,
  val phoneNumber: String,
  val firstName: String,
  val lastName: String,
  val password: String,
  val registrationTime: Long
)