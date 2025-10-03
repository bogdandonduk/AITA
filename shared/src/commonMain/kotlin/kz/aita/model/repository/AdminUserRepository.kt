package kz.aita.model.repository

import kz.aita.model.dataModel.AdminUserAccountDataModel
import kz.aita.model.dataModel.UserAuthLogInDataModel
import kz.aita.model.dataModel.UserAuthSignUpDataModel
import kz.aita.model.wrapper.DataStateFlow

interface AdminUserRepository {

  val userAccountState: DataStateFlow<AdminUserAccountDataModel>

  suspend fun logIn(userAuthLogIn: UserAuthLogInDataModel)

  suspend fun signUp(userAuthSignUp: UserAuthSignUpDataModel)

  suspend fun logOut()
}
