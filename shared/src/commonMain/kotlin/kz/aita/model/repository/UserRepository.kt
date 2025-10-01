package kz.aita.model.repository

import kotlinx.coroutines.flow.Flow
import kz.aita.model.dataModel.LocalizedStringGroupDataModel
import kz.aita.model.dataModel.UserAccountDataModel
import kz.aita.model.dataModel.UserAuthLogInDataModel
import kz.aita.model.wrapper.DataState
import kz.aita.model.wrapper.DataStateFlow

interface UserRepository {

  val userAccountState: DataStateFlow<UserAccountDataModel>

  suspend fun logIn(userAuthLogIn: UserAuthLogInDataModel): Flow<DataState<UserAccountDataModel>>
}
