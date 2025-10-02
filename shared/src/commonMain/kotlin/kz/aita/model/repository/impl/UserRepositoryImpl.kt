package kz.aita.model.repository.impl

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kz.aita.model.dataModel.LocalizedStringGroupDataModel
import kz.aita.model.dataModel.UserAccountDataModel
import kz.aita.model.dataModel.UserAuthLogInDataModel
import kz.aita.model.dataModel.UserSettingsDataModel
import kz.aita.model.repository.Repository
import kz.aita.model.repository.UserRepository
import kz.aita.model.service.GenericRemoteService
import kz.aita.model.wrapper.DataState
import kz.aita.model.wrapper.DataStateFlow
import kz.aita.model.wrapper.MutableDataStateFlow

class UserRepositoryImpl(
  private val genericRemoteService: GenericRemoteService
) : Repository(), UserRepository {

  private val _userAccountState = MutableDataStateFlow(
    this,
    initial = UserAccountDataModel(
      0,
      "bogdan.donduk@gmail.com",
      "7714047737",
      "Bogdan",
      "Donduk",
      "kz",
//      UserSettingsDataModel(
//        1759232357,
//        "en",
//        0,
//        0
//      )

    )
  )
  override val userAccountState = _userAccountState.asDataStateFlow()

  override suspend fun logIn(userAuthLogIn: UserAuthLogInDataModel): Flow<DataState<UserAccountDataModel>> {
    return flow {

    }
  }
}