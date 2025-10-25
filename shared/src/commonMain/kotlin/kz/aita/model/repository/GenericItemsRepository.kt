package kz.aita.model.repository

import kotlinx.coroutines.flow.Flow
import kz.aita.model.dataModel.GenericGoodsCategoryDataModel
import kz.aita.model.dataModel.GenericGoodsItemDataModel
import kz.aita.model.wrapper.DataState
import kz.aita.model.wrapper.DataStateFlow

interface GenericItemsRepository {

  val goodsCategoriesState: DataStateFlow<List<GenericGoodsCategoryDataModel>>

  fun getGenericGoodsItems(barcode: String): Flow<DataState<List<GenericGoodsItemDataModel>>>
  fun getGenericGoodsCategories()
}
