package kz.aita.model.service

import kz.aita.model.dataModel.UserAuthLogInDataModel
import kz.aita.model.dataModel.response.*

interface UserService {

  suspend fun logIn(userAuthLogIn: UserAuthLogInDataModel): UserAuthLogInResponseDataModel
}
