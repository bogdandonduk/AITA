package kz.aita.model.repository.impl

import io.ktor.http.HttpMethod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kz.aita.core.DataStore
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
  private val tokenStore: DataStore<TokenPair>?,
  private val userAccountDataStore: DataStore<UserAccountDataModel>?
): Repository(), UserRepository {

  private val _userAccountState = MutableDataStateFlow<UserAccountDataModel>(
    this
  )
  override val userAccountState = _userAccountState.asDataStateFlow()

  private val loginMutex = Mutex()
  private val signUpMutex = Mutex()
  private val getUserAccountMutex = Mutex()

  private val logOutMutex = Mutex()

  init {
    getUserAccount()
  }

  override fun logIn(userAuthLogIn: UserAuthLogInDataModel) {
    launch(Dispatchers.io) {
      if (!loginMutex.tryLock())
        return@launch

      _userAccountState.emit(DataState.Progress())

      genericRemoteService
        .request<TokenPair, UserAuthLogInDataModel>(
          HttpMethod.Post,
          endpointUrl = configurationRepository.globalAppConfigurationState.payloadValue.logInPath,
          body = userAuthLogIn,
          onFailure = {
            _userAccountState.emit(DataState.Failure(it))
            loginMutex.unlock()
            it.printStackTrace()
          }
        )?.run {
          tokenStore?.set(this)
          loginMutex.unlock()
          getUserAccount()
        }
    }

  }

  override fun signUp(userAuthSignUp: UserAuthSignUpDataModel) {
    launch(Dispatchers.io) {
      if (!signUpMutex.tryLock())
        return@launch

      _userAccountState.emit(DataState.Progress())

      genericRemoteService
        .request<TokenPair, UserAuthSignUpDataModel>(
          HttpMethod.Post,
          endpointUrl = configurationRepository.globalAppConfigurationState.payloadValue.signUpPath,
          body = userAuthSignUp,
          onFailure = {
            _userAccountState.emit(DataState.Failure(it))
            signUpMutex.unlock()
            it.printStackTrace()
          }
        )?.run {
          tokenStore?.set(this)
          signUpMutex.unlock()
          getUserAccount()
        }
    }
  }

  override fun getUserAccount() {
    launch(Dispatchers.io) {
      if (!getUserAccountMutex.tryLock())
        return@launch

      if (_userAccountState.value.value !is DataState.Progress)
        _userAccountState.emit(DataState.Progress())

      userAccountDataStore?.get()?.run {
        _userAccountState.emit(DataState.Success(this))
      }

      genericRemoteService
        .request<UserAccountDataModel, Unit>(
          HttpMethod.Get,
          endpointUrl = configurationRepository.globalAppConfigurationState.payloadValue.userAccountPath,
          onFailure = {
            getUserAccountMutex.unlock()
            it.printStackTrace()
          }
        )?.run {
          _userAccountState.emit(DataState.Success(this))
          userAccountDataStore?.set(this)
          getUserAccountMutex.unlock()
        }
    }
  }

  override fun logOut() {
    launch(Dispatchers.io) {
      if (!logOutMutex.tryLock())
        return@launch

      _userAccountState.emit(DataState.Progress())

      genericRemoteService
        .request<Unit, String>(
          HttpMethod.Post,
          endpointUrl = configurationRepository.globalAppConfigurationState.payloadValue.logOutPath,
          body = tokenStore?.get()?.refreshToken,
          onFailure = {
            _userAccountState.emit(DataState.Failure(it))
            logOutMutex.unlock()
            it.printStackTrace()
          }
        )?.run {
          _userAccountState.emit(DataState.Empty())

          tokenStore?.set(null)
          userAccountDataStore?.set(null)

          logOutMutex.unlock()
        }
    }
  }
}