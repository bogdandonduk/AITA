package kz.aita.model.repository

import kz.aita.model.dataModel.GoodsItemCategoryDataModel
import kz.aita.model.wrapper.DataStateFlow

interface GoodsCategoryRepository {

  val categoriesState: DataStateFlow<List<GoodsItemCategoryDataModel>>

  fun getCategories()
}
