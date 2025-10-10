package kz.aita.model.dataModel

import kotlinx.serialization.Serializable

@Serializable
class UserAccountUpdateDataModel(
  val account: UserAccountDataModel,
  val password: String,
  val newPassword: String?
)