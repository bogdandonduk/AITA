package kz.aita.model.repository.impl

import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kz.aita.core.io
import kz.aita.model.dataModel.StoreDataModel
import kz.aita.model.repository.ConfigurationRepository
import kz.aita.model.repository.Repository
import kz.aita.model.repository.StoreRepository
import kz.aita.model.service.GenericRemoteService
import kz.aita.model.wrapper.DataState
import kz.aita.model.wrapper.MutableDataStateFlow

class StoreRepositoryImpl(
  private val genericRemoteService: GenericRemoteService,
  private val configurationRepository: ConfigurationRepository
): Repository(), StoreRepository {

  private val _storesState = MutableDataStateFlow<List<StoreDataModel>>(this)
  override val storesState = _storesState.asDataStateFlow()

  private val getStoresMutex = Mutex()

  init {
    getStores()
  }

  override fun getStores() {
    launch(Dispatchers.io) {
      if (!getStoresMutex.tryLock())
        return@launch

      _storesState.emit(DataState.Progress())

      genericRemoteService
        .request<List<StoreDataModel>, Unit>(
          HttpMethod.Get,
          endpointUrl = configurationRepository.globalAppConfigurationState.payloadValue.storesPath,
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
}
