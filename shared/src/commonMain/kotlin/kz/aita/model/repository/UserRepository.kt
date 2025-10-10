package kz.aita.model.repository

import kz.aita.model.dataModel.UserAccountDataModel
import kz.aita.model.dataModel.UserAccountUpdateDataModel
import kz.aita.model.dataModel.UserAuthLogInDataModel
import kz.aita.model.dataModel.UserAuthSignUpDataModel
import kz.aita.model.wrapper.DataState
import kz.aita.model.wrapper.DataStateFlow

interface UserRepository {

  val userAccountState: DataStateFlow<UserAccountDataModel>

  fun logIn(userAuthLogIn: UserAuthLogInDataModel)

  fun signUp(userAuthSignUp: UserAuthSignUpDataModel)

  fun logOut()

  fun get(forceLogOut: Boolean = false)

  fun update(userAccountUpdate: UserAccountUpdateDataModel)

  fun forceLogOut()
}
