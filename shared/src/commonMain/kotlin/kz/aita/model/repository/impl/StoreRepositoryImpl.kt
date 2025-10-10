package kz.aita.model.repository.impl

import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
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
import kz.aita.model.service.GenericRemoteService
import kz.aita.model.wrapper.DataState
import kz.aita.model.wrapper.MutableDataStateFlow
import kz.aita.model.wrapper.TokenPair

class StoreRepositoryImpl(
  private val genericRemoteService: GenericRemoteService,
  private val configurationRepository: ConfigurationRepository,
  private val tokenStore: DataStore<TokenPair>?
) : Repository(), StoreRepository {

  private val _storesState = MutableDataStateFlow<List<StoreDataModel>>(this)
  override val storesState = _storesState.asDataStateFlow()

  private val getStoresMutex = Mutex()
  private val addStoreMutex = Mutex()

  init {
    getStores()
  }

  override fun getStores() {
    launch(Dispatchers.io) {
      if (tokenStore?.get() == null)
        return@launch

      if (!getStoresMutex.tryLock())
        return@launch

      _storesState.emit(DataState.Progress())

      genericRemoteService
        .request<List<StoreDataModel>, Unit>(
          HttpMethod.Get,
          endpointUrl = configurationRepository.globalAppConfigurationState.payloadValue.getStoresPath,
          onFailure = {
            _storesState.emit(DataState.Failure(it))
            getStoresMutex.unlock()
            it.printStackTrace()
          }
        )?.run {
          _storesState.emit(DataState.Success(this))
          getStoresMutex.unlock()
        }
    }
  }

  override fun addStore(store: StoreDataModel) {
    launch(Dispatchers.io) {
      if (tokenStore?.get() == null)
        return@launch

      if (!addStoreMutex.tryLock())
        return@launch

      _storesState.emit(DataState.Progress())

      genericRemoteService
        .request<StoreDataModel, StoreDataModel>(
          HttpMethod.Post,
          endpointUrl = configurationRepository.globalAppConfigurationState.payloadValue.addStoresPath,
          body = store,
          onFailure = {
            notificationRepository.postNotification(it.message ?: "Some error", type = NotificationType.Negative)

            addStoreMutex.unlock()
            it.printStackTrace()
          }
        )?.run {
          _storesState.emit(
            DataState.Success(
              mutableListOf<StoreDataModel>().also {
                ((storesState.value.value as? DataState.Success)?.payload ?: (storesState.value.value as? DataState.SoftFailure)?.existingPayload)?.run {
                  it.addAll(this)
                }

                it.add(this)
              }
            )
          )

          addStoreMutex.unlock()
        }
    }
  }
}
