package kz.aita.model.repository.impl

import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kz.aita.core.DataStore
import kz.aita.core.io
import kz.aita.core.notificationRepository
import kz.aita.model.dataModel.NotificationType
import kz.aita.model.dataModel.StoreDataModel
import kz.aita.model.repository.ConfigurationRepository
import kz.aita.model.repository.Repository
import kz.aita.model.repository.StoreRepository
import kz.aita.model.service.GenericLocalService
import kz.aita.model.service.GenericRemoteService
import kz.aita.model.wrapper.DataState
import kz.aita.model.wrapper.MutableDataStateFlow
import kz.aita.model.wrapper.TokenPair

class StoreRepositoryImpl(
  private val genericRemoteService: GenericRemoteService,
  private val genericLocalService: GenericLocalService,
  private val configurationRepository: ConfigurationRepository,
  private val tokenStore: DataStore<TokenPair>?
) : Repository(), StoreRepository {

  private val _storesState = MutableDataStateFlow<List<StoreDataModel>>(this)
  override val storesState = _storesState.asDataStateFlow()
  private val _activeStoreId = MutableStateFlow<String?>(null)
  override val activeStoreId = _activeStoreId.asStateFlow()

  private val getStoresMutex = Mutex()
  private val addStoreMutex = Mutex()
  private val updateStoreMutex = Mutex()

  companion object {
    private const val KEY_ACTIVE_STORE_ID = "key_activeStoreId"
  }
  init {
    getStores()

    launch(Dispatchers.io) {
      launch {
        genericLocalService
          .observe(KEY_ACTIVE_STORE_ID)
          .collect {
            it?.let {
              _activeStoreId.emit(it)
            }
          }
      }
    }
  }

  override fun getStores() {
//    launch(Dispatchers.io) {
//      if (!getStoresMutex.tryLock())
//        return@launch
//
//      if (tokenStore?.get() == null)
//        return@launch getStoresMutex.unlock()
//
//      _storesState.emit(DataState.Progress())
//
//      genericRemoteService
//        .request<List<StoreDataModel>, Unit>(
//          HttpMethod.Get,
//          endpointUrl = configurationRepository.globalAppConfigurationState.payloadValue.getStoresPath,
//          onFailure = {
//            _storesState.emit(DataState.Failure(it))
//            getStoresMutex.unlock()
//            it.printStackTrace()
//          }
//        )?.run {
//          _storesState.emit(DataState.Success(this))
//          getStoresMutex.unlock()
//        }
//    }
  }

  override fun addStore(store: StoreDataModel) {
//    launch(Dispatchers.io) {
//      if (!addStoreMutex.tryLock())
//        return@launch
//
//      if (tokenStore?.get() == null)
//        return@launch addStoreMutex.unlock()
//
//      _storesState.emit(DataState.Progress())
//
//      genericRemoteService
//        .request<StoreDataModel, StoreDataModel>(
//          HttpMethod.Post,
//          endpointUrl = configurationRepository.globalAppConfigurationState.payloadValue.addStoresPath,
//          body = store,
//          onFailure = {
//            (_storesState.value.value as? DataState.Success)?.run {
//              _storesState.emit(DataState.SoftFailure(it, payload))
//            } ?: (_storesState.value.value as? DataState.SoftFailure)?.run {
//              _storesState.emit(DataState.SoftFailure(it, existingPayload))
//            }
//
//            notificationRepository.postNotification(it.message ?: "Some error", type = NotificationType.Negative)
//
//            addStoreMutex.unlock()
//            it.printStackTrace()
//          }
//        )?.run {
//          _storesState.emit(
//            DataState.Success(
//              mutableListOf<StoreDataModel>().also {
//                ((storesState.value.value as? DataState.Success)?.payload ?: (storesState.value.value as? DataState.SoftFailure)?.existingPayload)?.run {
//                  it.addAll(this)
//                }
//
//                it.add(this)
//              }
//            )
//          )
//
//          addStoreMutex.unlock()
//        }
//    }
  }

  override fun updateStore(store: StoreDataModel, onCompleted: ((DataState<StoreDataModel>) -> Unit)?) {
//    launch(Dispatchers.io) {
//      if (!updateStoreMutex.tryLock())
//        return@launch
//
//      if (tokenStore?.get() == null)
//        return@launch updateStoreMutex.unlock()
//
//      genericRemoteService
//        .request<StoreDataModel, StoreDataModel>(
//          HttpMethod.Put,
//          endpointUrl = configurationRepository.globalAppConfigurationState.payloadValue.updateStoresPath,
//          body = store,
//          onFailure = {
//            (_storesState.value.value as? DataState.Success)?.run {
//              _storesState.emit(DataState.SoftFailure(it, payload))
//            } ?: (_storesState.value.value as? DataState.SoftFailure)?.run {
//              _storesState.emit(DataState.SoftFailure(it, existingPayload))
//            }
//
//            notificationRepository.postNotification(it.message ?: "Some error", type = NotificationType.Negative)
//
//            updateStoreMutex.unlock()
//            it.printStackTrace()
//          }
//        )?.run {
//          _storesState.emit(
//            DataState.Success(
//              mutableListOf<StoreDataModel>().also {
//                ((storesState.value.value as? DataState.Success)?.payload ?: (storesState.value.value as? DataState.SoftFailure)?.existingPayload)?.run {
//                  it.addAll(this)
//                }
//
//                it.indexOfFirst { item -> item.id == store.id }.takeIf { index -> index.apply { println("index isss $this") } != -1 }?.let { index ->
//                  it[index] = this
//                }
//              }
//            )
//          )
//
//          notificationRepository.postNotification("Store updated", type = NotificationType.Positive)
//
//          updateStoreMutex.unlock()
//        }
//    }
  }

  override fun setActiveStoreId(id: String?) {
    launch(Dispatchers.io) {
      genericLocalService
        .put(KEY_ACTIVE_STORE_ID, id)
    }
  }
}
