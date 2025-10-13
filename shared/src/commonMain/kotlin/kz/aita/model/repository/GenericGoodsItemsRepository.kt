package kz.aita.model.repository

import kotlinx.coroutines.flow.Flow
import kz.aita.model.dataModel.GenericGoodsItemDataModel
import kz.aita.model.wrapper.DataState
import kz.aita.model.wrapper.DataStateFlow

interface GenericGoodsItemsRepository {

  val genericGoodsItemsState: DataStateFlow<List<GenericGoodsItemDataModel>>

  suspend fun getGenericGoodsItems(barcode: String): Flow<DataState<List<GenericGoodsItemDataModel>>>
}
