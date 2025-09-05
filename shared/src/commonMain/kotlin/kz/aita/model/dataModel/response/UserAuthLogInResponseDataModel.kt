package kz.aita.model.dataModel.response

import kotlinx.serialization.Serializable
import kz.aita.model.dataModel.UserAuthAccountDataModel

@Serializable
data class UserAuthLogInResponseDataModel(
  val payload: UserAuthAccountDataModel?
)
