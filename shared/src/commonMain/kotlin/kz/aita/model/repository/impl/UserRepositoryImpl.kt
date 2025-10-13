package kz.aita.model.repository.impl

import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kz.aita.core.DataStore
import kz.aita.core.io
import kz.aita.core.storeRepository
import kz.aita.model.dataModel.NotificationType
import kz.aita.model.dataModel.UserAccountDataModel
import kz.aita.model.dataModel.UserAccountUpdateDataModel
import kz.aita.model.dataModel.UserAuthLogInDataModel
import kz.aita.model.dataModel.UserAuthSignUpDataModel
import kz.aita.model.repository.ConfigurationRepository
import kz.aita.model.repository.NotificationRepository
import kz.aita.model.repository.Repository
import kz.aita.model.repository.UserRepository
import kz.aita.model.service.GenericRemoteService
import kz.aita.model.wrapper.DataState
import kz.aita.model.wrapper.MutableDataStateFlow
import kz.aita.model.wrapper.TokenPair

class UserRepositoryImpl(
  private val genericRemoteService: GenericRemoteService,
  private val configurationRepository: ConfigurationRepository,
  private val notificationRepository: NotificationRepository,
  private val tokenStore: DataStore<TokenPair>?,
  private val userAccountStore: DataStore<UserAccountDataModel>?
) : Repository(), UserRepository {

  private val _userAccountState = MutableDataStateFlow<UserAccountDataModel>(
    this
  )
  override val userAccountState = _userAccountState.asDataStateFlow()

  private val logInMutex = Mutex()
  private val signUpMutex = Mutex()

  private val logOutMutex = Mutex()
  private val getUserAccountMutex = Mutex()

  private val updateMutex = Mutex()

  init {
    get()
  }

  override fun logIn(userAuthLogIn: UserAuthLogInDataModel) {
    if (!logInMutex.isLocked)
      launch(Dispatchers.io) {
        logInMutex.withLock {
          _userAccountState.emit(DataState.Progress())

          val response = genericRemoteService
            .request<TokenPair, UserAuthLogInDataModel>(
              HttpMethod.Post,
              endpointUrl = configurationRepository.globalAppConfigurationState.payloadValue.logInPath,
              body = userAuthLogIn
            )

          userAccountStore?.set(null)

          if (response.negative) {
            tokenStore?.set(null)

            notificationRepository.postNotification(response.message, NotificationType.Negative)
          } else {
            tokenStore?.set(response.payload)

            get()
          }
        }
      }
  }

  override fun signUp(userAuthSignUp: UserAuthSignUpDataModel) {
    if (!signUpMutex.isLocked)
      launch(Dispatchers.io) {
        signUpMutex.withLock {
          _userAccountState.emit(DataState.Progress())

          val response = genericRemoteService
            .request<TokenPair, UserAuthSignUpDataModel>(
              HttpMethod.Post,
              endpointUrl = configurationRepository.globalAppConfigurationState.payloadValue.signUpPath,
              body = userAuthSignUp
            )

          userAccountStore?.set(null)

          if (response.negative) {
            tokenStore?.set(null)

            notificationRepository.postNotification(response.message, NotificationType.Negative)
          } else {
            tokenStore?.set(response.payload)

            get()
          }
        }
      }
  }

  override fun logOut() {
    if (!logOutMutex.isLocked)
      launch(Dispatchers.io) {
        logOutMutex.withLock {
          val response = genericRemoteService
            .request<Unit, String>(
              HttpMethod.Delete,
              endpointUrl = configurationRepository.globalAppConfigurationState.payloadValue.logOutPath,
              body = tokenStore?.get()?.refreshToken
            )

          if (response.negative) {
            notificationRepository.postNotification(response.message, NotificationType.Negative)
          } else {
            notificationRepository.postNotification(response.message, NotificationType.Positive)

            tokenStore?.set(null)
            userAccountStore?.set(null)
            _userAccountState.emit(DataState.Empty())
          }
        }
      }
  }

  override fun get(forceLogOut: Boolean) {
    if (!getUserAccountMutex.isLocked)
      launch(Dispatchers.io) {
        getUserAccountMutex.withLock {
          userAccountStore?.get()?.run {
            _userAccountState.emit(DataState.Success(this))
          }

          val response = genericRemoteService
            .request<UserAccountDataModel, Unit>(
              HttpMethod.Get,
              endpointUrl = configurationRepository.globalAppConfigurationState.payloadValue.getUserPath
            )

          if (response.negative) {
            if (forceLogOut)
              forceLogOut()

            notificationRepository.postNotification(response.message, NotificationType.Negative)
          } else {
            _userAccountState.emit(DataState.Success(response.payload!!, response.message))

            configurationRepository.getGlobalAppConfiguration()
            storeRepository.getStores()

            userAccountStore?.set(response.payload)
            getUserAccountMutex.unlock()
          }
        }
      }
  }

  override fun update(
    userAccountUpdate: UserAccountUpdateDataModel
  ) {
    if (!updateMutex.isLocked)
      launch(Dispatchers.io) {
        updateMutex.withLock {
          val response = genericRemoteService
            .request<UserAccountDataModel, UserAccountUpdateDataModel>(
              HttpMethod.Put,
              endpointUrl = configurationRepository.globalAppConfigurationState.payloadValue.updateUserPath,
              body = userAccountUpdate
            )

          if (response.negative) {
            notificationRepository.postNotification(response.message, NotificationType.Negative)
          } else {
            _userAccountState.emit(DataState.Success(response.payload!!, response.message))

            notificationRepository.postNotification(
              response.message,
              NotificationType.Positive
            )

            userAccountStore?.set(response.payload)
          }
        }
      }
  }

  override fun forceLogOut() {
    launch(Dispatchers.io) {
      tokenStore?.set(null)
      userAccountStore?.set(null)

      if (_userAccountState.value.value !is DataState.Empty)
        _userAccountState.emit(DataState.Empty())
    }
  }
}