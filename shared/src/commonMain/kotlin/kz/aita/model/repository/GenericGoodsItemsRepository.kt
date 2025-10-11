package kz.aita.model.repository

import kz.aita.model.dataModel.GenericGoodsItemDataModel
import kz.aita.model.wrapper.DataStateFlow

interface GenericGoodsItemsRepository {

  val genericGoodsItemsState: DataStateFlow<List<GenericGoodsItemDataModel>>

  fun getGenericGoodsItems()
}