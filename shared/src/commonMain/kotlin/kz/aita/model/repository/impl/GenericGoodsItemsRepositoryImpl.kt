package kz.aita.model.repository.impl

import io.ktor.http.HttpMethod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kz.aita.core.DataStore
import kz.aita.core.io
import kz.aita.model.dataModel.GenericGoodsItemDataModel
import kz.aita.model.repository.ConfigurationRepository
import kz.aita.model.repository.GenericGoodsItemsRepository
import kz.aita.model.repository.Repository
import kz.aita.model.service.GenericRemoteService
import kz.aita.model.wrapper.DataState
import kz.aita.model.wrapper.MutableDataStateFlow

class GenericGoodsItemsRepositoryImpl(
  private val genericRemoteService: GenericRemoteService,
  private val configurationRepository: ConfigurationRepository
) : Repository(), GenericGoodsItemsRepository {

  private val _genericGoodsItemsState = MutableDataStateFlow<List<GenericGoodsItemDataModel>>(this)
  override val genericGoodsItemsState = _genericGoodsItemsState.asDataStateFlow()

  private val getGenericGoodsItemsMutex = Mutex()

  override suspend fun getGenericGoodsItems(barcode: String): Flow<DataState<List<GenericGoodsItemDataModel>>> {
    return flow {
//      getGenericGoodsItemsMutex.withLock {
//        genericRemoteService
//          .request<List<GenericGoodsItemDataModel>, String>(
//            method = HttpMethod.Post,
//            endpointUrl = configurationRepository.globalAppConfigurationState.payloadValue.getGenericGoodsItemsPath,
//            body = barcode,
//            onFailure = {
//              emit(DataState.Failure(it))
//            }
//          )?.run {
//            emit(DataState.Success(this))
//          }
//      }
    }
  }
}
