package kz.aita.model.repository.impl

import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kz.aita.core.DataStore
import kz.aita.core.genericGoodsItemsRepository
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
): Repository(), UserRepository {

  private val _userAccountState = MutableDataStateFlow<UserAccountDataModel>(
    this
  )
  override val userAccountState = _userAccountState.asDataStateFlow()

  private val loginMutex = Mutex()
  private val signUpMutex = Mutex()

  private val logOutMutex = Mutex()
  private val getUserAccountMutex = Mutex()

  private val updateMutex = Mutex()

  init {
    get()
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

          get()
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

          get()
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
          HttpMethod.Delete,
          endpointUrl = configurationRepository.globalAppConfigurationState.payloadValue.logOutPath,
          body = tokenStore?.get()?.refreshToken,
          onFailure = {
            _userAccountState.emit(DataState.Failure(it))
            logOutMutex.unlock()
            it.printStackTrace()
          }
        )?.run {
          notificationRepository.postNotification(configurationRepository.stringLoggingOutInProgressState.value, NotificationType.Neutral)
          delay(3000)

          tokenStore?.set(null)
          userAccountStore?.set(null)
          _userAccountState.emit(DataState.Empty())

          logOutMutex.unlock()
        }
    }
  }

  override fun get(forceLogOut: Boolean) {
    launch(Dispatchers.io) {
      if (!getUserAccountMutex.tryLock())
        return@launch

      if (tokenStore?.get() == null)
        return@launch

      if (_userAccountState.value.value !is DataState.Progress)
        _userAccountState.emit(DataState.Progress())

      userAccountStore?.get()?.run {
        _userAccountState.emit(DataState.Success(this))
      }

      genericRemoteService
        .request<UserAccountDataModel, Unit>(
          HttpMethod.Get,
          endpointUrl = configurationRepository.globalAppConfigurationState.payloadValue.getUserPath,
          onFailure = {
            if (forceLogOut) {
              forceLogOut()
            } else {
              (_userAccountState.value.value as? DataState.Success)?.run {
                _userAccountState.emit(DataState.SoftFailure(it, payload))
              } ?: (_userAccountState.value.value as? DataState.SoftFailure)?.run {
                _userAccountState.emit(DataState.SoftFailure(it, existingPayload))
              }
            }

            getUserAccountMutex.unlock()

            it.printStackTrace()
          }
        )?.run {
          configurationRepository.getGlobalAppConfiguration()
          storeRepository.getStores()
          genericGoodsItemsRepository.getGenericGoodsItems()


          _userAccountState.emit(DataState.Success(this))
          userAccountStore?.set(this)
          getUserAccountMutex.unlock()
        }
    }
  }

  override fun update(
    userAccountUpdate: UserAccountUpdateDataModel
  ) {
    launch(Dispatchers.io) {
      if (!updateMutex.tryLock())
        return@launch

      _userAccountState.emit(DataState.Progress())

      genericRemoteService
        .request<UserAccountDataModel, UserAccountUpdateDataModel>(
          HttpMethod.Put,
          endpointUrl = configurationRepository.globalAppConfigurationState.payloadValue.updateUserPath,
          body = userAccountUpdate,
          onFailure = {
            (_userAccountState.value.value as? DataState.Success)?.run {
              DataState.Success(payload).let { dataState ->
                _userAccountState.emit(dataState)
                notificationRepository.postNotification(it.toString(), NotificationType.Negative)
              }
            } ?: (_userAccountState.value.value as? DataState.SoftFailure)?.run {
              DataState.SoftFailure(it, existingPayload).let { dataState ->
                _userAccountState.emit(dataState)
                notificationRepository.postNotification(it.toString(), NotificationType.Negative)
              }
            } ?: _userAccountState.emit(DataState.Failure(it)).run {
              notificationRepository.postNotification(it.toString(), NotificationType.Negative)
            }

            updateMutex.unlock()
            it.printStackTrace()
          }
        )?.run {
          DataState.Success(this).let { dataState ->
            _userAccountState.emit(dataState)
          }

          notificationRepository.postNotification(configurationRepository.stringAccountSuccessfullyUpdatedState.value, NotificationType.Positive)

          userAccountStore?.set(this)
          updateMutex.unlock()
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