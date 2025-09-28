package kz.aita.model.repository.impl

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kz.aita.model.dataModel.UserAccountDataModel
import kz.aita.model.dataModel.UserAuthLogInDataModel
import kz.aita.model.repository.UserRepository
import kz.aita.model.service.GenericRemoteService
import kz.aita.model.wrapper.DataState

class UserRepositoryImpl(
  private val genericRemoteService: GenericRemoteService
) : UserRepository {

  override suspend fun logIn(userAuthLogIn: UserAuthLogInDataModel): Flow<DataState<UserAccountDataModel>> {
    return flow {

    }
  }
}