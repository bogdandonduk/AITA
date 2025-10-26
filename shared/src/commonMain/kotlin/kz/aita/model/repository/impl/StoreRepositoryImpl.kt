package kz.aita.model.repository.impl

import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kz.aita.core.io
import kz.aita.core.notificationRepository
import kz.aita.core.stockRepository
import kz.aita.model.dataModel.NotificationType
import kz.aita.model.dataModel.StoreDataModel
import kz.aita.model.repository.ConfigurationRepository
import kz.aita.model.repository.Repository
import kz.aita.model.repository.StoreRepository
import kz.aita.model.service.GenericLocalService
import kz.aita.model.service.GenericRemoteService
import kz.aita.model.wrapper.DataState
import kz.aita.model.wrapper.MutableDataStateFlow

class StoreRepositoryImpl(
  private val genericRemoteService: GenericRemoteService,
  private val genericLocalService: GenericLocalService,
  private val configurationRepository: ConfigurationRepository
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
    launch(Dispatchers.io) {

      genericLocalService
        .observeKv(KEY_ACTIVE_STORE_ID)
        .collect {
          it?.let {
            _activeStoreId.emit(it)
            stockRepository.getStock(it)
          }
        }

    }

    getStores()
  }

  override fun getStores() {
    if (!getStoresMutex.isLocked)
      launch(Dispatchers.io) {
        getStoresMutex.withLock {
          val response = genericRemoteService
            .request<List<StoreDataModel>, Unit>(
              HttpMethod.Get,
              endpointUrl = configurationRepository.globalAppConfigurationState.payloadValue.getStoresPath
            )

          if (!response.negative)
            _storesState.emit(DataState.Success(response.payload!!, response.message))
        }
      }
  }

  override fun addStore(store: StoreDataModel, onCompleted: ((DataState<StoreDataModel>) -> Unit)?) {
    if (!addStoreMutex.isLocked)
      launch(Dispatchers.io) {
        addStoreMutex.withLock {
          val response = genericRemoteService
            .request<StoreDataModel, StoreDataModel>(
              HttpMethod.Post,
              endpointUrl = configurationRepository.globalAppConfigurationState.payloadValue.addStoresPath,
              body = store
            )

          if (response.negative) {
            notificationRepository.post(response.message, NotificationType.Negative)

            onCompleted?.invoke(DataState.Empty())
          } else {
            notificationRepository.post(response.message, NotificationType.Positive)

            _storesState.emit(
              DataState.Success(
                mutableListOf<StoreDataModel>().also { newList ->
                  (storesState.value.value as? DataState.Success)?.payload?.run {
                    newList.addAll(this)
                  }

                  _storesState.payloadValue?.indexOfFirst { it.id == response.payload!!.id }?.let { index ->
                    newList[index] = response.payload!!
                  } ?: newList.add(response.payload!!)
                }
              )
            )

            onCompleted?.invoke(DataState.Success(response.payload!!))
          }
        }
      }
  }

  override fun updateStore(store: StoreDataModel, onCompleted: ((DataState<StoreDataModel>) -> Unit)?) {
    if (!updateStoreMutex.isLocked)
      launch(Dispatchers.io) {
        updateStoreMutex.withLock {
          val response = genericRemoteService
            .request<StoreDataModel, StoreDataModel>(
              HttpMethod.Put,
              endpointUrl = configurationRepository.globalAppConfigurationState.payloadValue.updateStoresPath,
              body = store
            )

          if (response.negative) {
            notificationRepository.post(response.message, NotificationType.Negative)

            onCompleted?.invoke(DataState.Empty())
          } else {
            notificationRepository.post(response.message, NotificationType.Positive)

            _storesState.emit(
              DataState.Success(
                mutableListOf<StoreDataModel>().also { newList ->
                  (storesState.value.value as? DataState.Success)?.payload?.run {
                    newList.addAll(this)
                  }

                  newList.indexOfFirst { item -> item.id == store.id }
                    .takeIf { index -> index != -1 }?.let { index ->
                      newList[index] = response.payload!!
                    }
                }
              )
            )

            onCompleted?.invoke(DataState.Success(response.payload!!))
          }
        }
      }
  }

  override fun setActiveStoreId(id: String?) {
    launch(Dispatchers.io) {
      genericLocalService
        .putKv(KEY_ACTIVE_STORE_ID, id)
    }
  }
}
