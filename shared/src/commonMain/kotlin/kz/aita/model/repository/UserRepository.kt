package kz.aita.model.repository

import kotlinx.coroutines.flow.Flow
import kz.aita.model.dataModel.UserAccountDataModel
import kz.aita.model.dataModel.UserAuthLogInDataModel
import kz.aita.model.wrapper.DataState

interface UserRepository {

  suspend fun logIn(userAuthLogIn: UserAuthLogInDataModel): Flow<DataState<UserAccountDataModel>>
}
