package kz.aita.model.repository.impl

import io.ktor.http.HttpMethod
import kz.aita.model.dataModel.UserAccountDataModel
import kz.aita.model.dataModel.UserAuthLogInDataModel
import kz.aita.model.dataModel.UserAuthSignUpDataModel
import kz.aita.model.repository.ConfigurationRepository
import kz.aita.model.repository.Repository
import kz.aita.model.repository.UserRepository
import kz.aita.model.service.GenericRemoteService
import kz.aita.model.wrapper.DataState
import kz.aita.model.wrapper.MutableDataStateFlow
import kz.aita.model.wrapper.TokenPair

class UserRepositoryImpl(
  private val genericRemoteService: GenericRemoteService,
  private val configurationRepository: ConfigurationRepository
): Repository(), UserRepository {

  private val _userAccountState = MutableDataStateFlow(
    this,
    initial = UserAccountDataModel(
      "0",
      "bogdan.donduk@gmail.com",
      "7714047737",
      "Bogdan",
      "Donduk",
      "kz",
      null,
      null,
      1759232357,
      true
    )
  )
  override val userAccountState = _userAccountState.asDataStateFlow()

  override suspend fun logIn(userAuthLogIn: UserAuthLogInDataModel) {

  }

  override suspend fun signUp(userAuthSignUp: UserAuthSignUpDataModel) {
    _userAccountState.emit(DataState.Progress())

    genericRemoteService
      .request<TokenPair, UserAuthSignUpDataModel>(
        HttpMethod.Post,
        configurationRepository.globalAppConfigurationState.payloadValue.serverUrl,
        configurationRepository.globalAppConfigurationState.payloadValue.signUpPath,
        body = userAuthSignUp,
        onFailure = {
//          _userAccountState.emit(DataState.Failure(it))
        }
      )?.run {
//        _userAccountState.emit(DataState.Success(this))
      }
  }

  override suspend fun logOut() {
    TODO("Not yet implemented")
  }
}