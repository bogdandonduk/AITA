package kz.aita.model.dataModel.response

import kz.aita.model.dataModel.UserAuthAccountDataModel

data class UserAuthLogInResponseDataModel(
  val code: String,
  val userAuthAccountDataModel: UserAuthAccountDataModel?
)
