package kz.aita.model.dataModel

import kotlinx.serialization.Serializable

@Serializable
data class UserAuthLogInDataModel(
  val login: String,
  val password: String
)
