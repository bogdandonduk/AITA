package kz.aita.model.repository.impl

import io.ktor.http.HttpMethod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kz.aita.core.TokenStore
import kz.aita.core.io
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
  private val configurationRepository: ConfigurationRepository,
  private val tokenStore: TokenStore?
): Repository(), UserRepository {

  private val _userAccountState = MutableDataStateFlow<UserAccountDataModel>(
    this
  )
  override val userAccountState = _userAccountState.asDataStateFlow()

  override fun logIn(userAuthLogIn: UserAuthLogInDataModel) {
    launch(Dispatchers.io) {
      _userAccountState.emit(DataState.Progress())

      genericRemoteService
        .request<TokenPair, UserAuthLogInDataModel>(
          HttpMethod.Post,
          endpointUrl = configurationRepository.globalAppConfigurationState.payloadValue.logInPath,
          body = userAuthLogIn,
          onFailure = {
            _userAccountState.emit(DataState.Failure(it))
            it.printStackTrace()
          }
        )?.run {
          println("token key store is $tokenStore")
          tokenStore?.set(this)
          getUserAccount()
        }
    }

  }

  override fun signUp(userAuthSignUp: UserAuthSignUpDataModel) {
    launch(Dispatchers.io) {
      _userAccountState.emit(DataState.Progress())

      genericRemoteService
        .request<TokenPair, UserAuthSignUpDataModel>(
          HttpMethod.Post,
          endpointUrl = configurationRepository.globalAppConfigurationState.payloadValue.signUpPath,
          body = userAuthSignUp,
          onFailure = {
            _userAccountState.emit(DataState.Failure(it))
            it.printStackTrace()
          }
        )?.run {
          tokenStore?.set(this)
          getUserAccount()
        }
    }
  }

  override fun getUserAccount() {
    launch(Dispatchers.io) {
      genericRemoteService
        .request<UserAccountDataModel, Unit>(
          HttpMethod.Get,
          endpointUrl = configurationRepository.globalAppConfigurationState.payloadValue.userAccountPath,
          onFailure = {
            _userAccountState.emit(DataState.Failure(it))
            it.printStackTrace()
          }
        )?.run {
          _userAccountState.emit(DataState.Success(this))
        }
    }
  }

  override fun logOut() {
    TODO("Not yet implemented")
  }
}