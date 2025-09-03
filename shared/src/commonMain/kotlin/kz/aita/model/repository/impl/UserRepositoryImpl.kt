package kz.aita.model.repository.impl

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kz.aita.model.dataModel.UserAuthAccountDataModel
import kz.aita.model.dataModel.UserAuthLogInDataModel
import kz.aita.model.repository.UserRepository
import kz.aita.model.wrapper.DataState

class UserRepositoryImpl : UserRepository {

  override suspend fun logIn(userAuthLogIn: UserAuthLogInDataModel): Flow<DataState<UserAuthAccountDataModel>> {
    return flow {

    }
  }
}