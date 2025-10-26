package kz.aita.model.repository.impl

import io.ktor.http.HttpMethod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kz.aita.core.io
import kz.aita.model.dataModel.GenericGoodsCategoryDataModel
import kz.aita.model.dataModel.GenericGoodsItemDataModel
import kz.aita.model.repository.ConfigurationRepository
import kz.aita.model.repository.GenericItemsRepository
import kz.aita.model.repository.Repository
import kz.aita.model.service.GenericRemoteService
import kz.aita.model.wrapper.DataState
import kz.aita.model.wrapper.MutableDataStateFlow

class GenericItemsRepositoryImpl(
  private val genericRemoteService: GenericRemoteService,
  private val configurationRepository: ConfigurationRepository
): Repository(), GenericItemsRepository {
  private val _genericGoodsCategoriesState = MutableDataStateFlow<List<GenericGoodsCategoryDataModel>>(this)
  override val goodsCategoriesState = _genericGoodsCategoriesState.asDataStateFlow()

  private val getGenericGoodsItemsMutex = Mutex()
  private val getGenericGoodsCategoriesMutex = Mutex()

  override fun getGenericGoodsItems(barcode: String): Flow<DataState<List<GenericGoodsItemDataModel>>> {
    return flow {
      getGenericGoodsItemsMutex.withLock {
        val response = genericRemoteService
          .request<List<GenericGoodsItemDataModel>, String>(
            method = HttpMethod.Get,
            endpointUrl = configurationRepository.globalAppConfigurationState.payloadValue.getGenericGoodsItemsPath,
            headers = mapOf("barcode" to barcode)
          )

          if (!response.negative) {
            emit(DataState.Success(response.payload!!, response.message))
          }
      }
    }
  }

  override fun getGenericGoodsCategories() {
    if (!getGenericGoodsCategoriesMutex.isLocked)
      launch(Dispatchers.io) {
        getGenericGoodsCategoriesMutex.withLock {
          val response = genericRemoteService
            .request<List<GenericGoodsCategoryDataModel>, Unit>(
              method = HttpMethod.Get,
              endpointUrl = configurationRepository.globalAppConfigurationState.payloadValue.getGenericGoodsCategoriesPath
            )

          if (!response.negative)
            _genericGoodsCategoriesState.emit(DataState.Success(response.payload!!, response.message))
        }
      }
  }
}
