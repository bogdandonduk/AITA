package kz.aita.model.repository.impl

import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kz.aita.core.DataStore
import kz.aita.core.io
import kz.aita.model.dataModel.GoodsItemDataModel
import kz.aita.model.repository.ConfigurationRepository
import kz.aita.model.repository.Repository
import kz.aita.model.repository.StockRepository
import kz.aita.model.service.GenericRemoteService
import kz.aita.model.wrapper.DataState
import kz.aita.model.wrapper.MutableDataStateFlow
import kz.aita.model.wrapper.TokenPair

class StockRepositoryImpl(
  private val genericRemoteService: GenericRemoteService,
  private val configurationRepository: ConfigurationRepository,
  private val tokenStore: DataStore<TokenPair>?
): Repository(), StockRepository {

  private val _stockState = MutableDataStateFlow<List<GoodsItemDataModel>>(this)
  override val stockState = _stockState.asDataStateFlow()

  private val getStockMutex = Mutex()

  init {
    getStock()
  }
  override fun getStock() {
//    launch(Dispatchers.io) {
//      if (!getStockMutex.tryLock())
//        return@launch
//
//      if (tokenStore?.get() == null)
//        return@launch getStockMutex.unlock()
//
//      _stockState.emit(DataState.Progress())
//
//      genericRemoteService
//        .request<List<GoodsItemDataModel>, Unit>(
//          HttpMethod.Get,
//          endpointUrl = configurationRepository.globalAppConfigurationState.payloadValue.getStoresPath,
//          onFailure = {
//            _stockState.emit(DataState.Failure(it))
//            getStockMutex.unlock()
//            it.printStackTrace()
//          }
//        )?.run {
//          _stockState.emit(DataState.Success(this))
//          getStockMutex.unlock()
//        }
//    }
  }

  override fun addGoodsItem(
    goodsItem: GoodsItemDataModel
  ) {
    launch(Dispatchers.io) {
      _stockState.emit(
        DataState.Success(
          stockState.payloadValue?.let { payload ->
            mutableListOf<GoodsItemDataModel>().apply {
              addAll(payload)
              add(goodsItem)
            }
          } ?: listOf(goodsItem)
        )
      )
    }
  }

  override fun deleteGoodsItem(id: String) {
    launch(Dispatchers.io) {
      _stockState.payloadValue?.let { payload ->
        payload.indexOfFirst { it.id == id }
          .takeIf { it != -1 }
          ?.let { index ->
            _stockState.emit(
              DataState.Success(
                mutableListOf<GoodsItemDataModel>().apply {
                  addAll(payload)
                  removeAt(index)
                }.toList()
              )
            )
          }
      }
    }
  }
}
