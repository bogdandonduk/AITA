package kz.aita.model.repository.impl

import io.ktor.http.HttpMethod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kz.aita.core.DataStore
import kz.aita.core.io
import kz.aita.model.dataModel.GenericGoodsItemDataModel
import kz.aita.model.repository.ConfigurationRepository
import kz.aita.model.repository.GenericGoodsItemsRepository
import kz.aita.model.repository.Repository
import kz.aita.model.service.GenericRemoteService
import kz.aita.model.wrapper.DataState
import kz.aita.model.wrapper.MutableDataStateFlow
import kz.aita.model.wrapper.TokenPair

class GenericGoodsItemsRepositoryImpl(
  private val genericRemoteService: GenericRemoteService,
  private val configurationRepository: ConfigurationRepository,
  private val tokenStore: DataStore<TokenPair>?,
): Repository(), GenericGoodsItemsRepository {

  private val _genericGoodsItemsState = MutableDataStateFlow<List<GenericGoodsItemDataModel>>(this)
  override val genericGoodsItemsState = _genericGoodsItemsState.asDataStateFlow()

  private val getGenericGoodsItemsMutex = Mutex()

  init {
    getGenericGoodsItems()
  }

  override fun getGenericGoodsItems() {
    println("we called bruh")
    launch(Dispatchers.io) {
      if (!getGenericGoodsItemsMutex.tryLock())
        return@launch

      if (tokenStore?.get() == null)
        return@launch getGenericGoodsItemsMutex.unlock()

      genericRemoteService
        .request<List<GenericGoodsItemDataModel>, Unit>(
          method = HttpMethod.Get,
          endpointUrl = configurationRepository.globalAppConfigurationState.payloadValue.getGenericGoodsItemsPath,
          onFailure = {
            _genericGoodsItemsState.emit(DataState.Failure(it))
            getGenericGoodsItemsMutex.unlock()
          }
        )?.run {
          _genericGoodsItemsState.emit(DataState.Success(this))
          getGenericGoodsItemsMutex.unlock()

        }
    }
  }
}
