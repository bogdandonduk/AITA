package kz.aita.model.repository.impl

import io.ktor.client.plugins.auth.authProvider
import io.ktor.client.plugins.auth.authProviders
import io.ktor.client.plugins.auth.providers.BearerAuthProvider
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kz.aita.core.DataStore
import kz.aita.core.genericItemsRepository
import kz.aita.core.httpClient
import kz.aita.core.io
import kz.aita.core.stockRepository
import kz.aita.core.storeRepository
import kz.aita.core.supplierRepository
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
): Repository(), UserRepository {

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
          notificationRepository.post(configurationRepository.stringLoggingInState.value, NotificationType.Neutral)

          val response = genericRemoteService
            .request<TokenPair, UserAuthLogInDataModel>(
              HttpMethod.Post,
              endpointUrl = configurationRepository.globalAppConfigurationState.payloadValue.logInPath,
              body = userAuthLogIn
            )

          if (response.negative) {
            notificationRepository.post(response.message, NotificationType.Negative, transient = true)
          } else {
            tokenStore?.set(response.payload)
            httpClient.authProvider<BearerAuthProvider>()?.clearToken()
            get()
          }
        }
      }
  }

  override fun signUp(userAuthSignUp: UserAuthSignUpDataModel) {
    if (!signUpMutex.isLocked)
      launch(Dispatchers.io) {
        signUpMutex.withLock {
          notificationRepository.post(configurationRepository.stringSigningUpState.value, NotificationType.Neutral)

          val response = genericRemoteService
            .request<TokenPair, UserAuthSignUpDataModel>(
              HttpMethod.Post,
              endpointUrl = configurationRepository.globalAppConfigurationState.payloadValue.signUpPath,
              body = userAuthSignUp
            )

          if (response.negative) {
            notificationRepository.post(response.message, NotificationType.Negative)
          } else {
            tokenStore?.set(response.payload)
            httpClient.authProvider<BearerAuthProvider>()?.clearToken()

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
            notificationRepository.post(response.message, NotificationType.Negative)
          } else {
            notificationRepository.post(response.message, NotificationType.Positive)
            _userAccountState.emit(DataState.Empty())

            tokenStore?.set(null)
            userAccountStore?.set(null)
            storeRepository.setActiveStoreId(null)
            httpClient.authProvider<BearerAuthProvider>()?.clearToken()
          }
        }
      }
  }

  override fun get(forceLogOut: Boolean) {
    if (!getUserAccountMutex.isLocked)
      launch(Dispatchers.io) {
        if (tokenStore?.get() != null)
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

              notificationRepository.post(response.message, NotificationType.Negative)
            } else {
              notificationRepository.clear()
              userAccountStore?.set(response.payload)
              _userAccountState.emit(DataState.Success(response.payload!!, response.message))

              configurationRepository.getGlobalAppConfiguration()
              storeRepository.getStores()
              supplierRepository.getSuppliers()
              genericItemsRepository.getGenericGoodsCategories()
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
            notificationRepository.post(response.message, NotificationType.Negative)
          } else {
            _userAccountState.emit(DataState.Success(response.payload!!, response.message))

            notificationRepository.post(
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
      storeRepository.setActiveStoreId(null)

      _userAccountState.emit(DataState.Empty())
    }
  }
}