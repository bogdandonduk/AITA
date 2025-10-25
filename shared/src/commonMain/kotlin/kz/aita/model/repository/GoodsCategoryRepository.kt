package kz.aita.model.repository

import kz.aita.model.dataModel.GenericGoodsCategoryDataModel
import kz.aita.model.wrapper.DataStateFlow

interface GoodsCategoryRepository {

  val categoriesState: DataStateFlow<List<GenericGoodsCategoryDataModel>>

  fun getCategories()
}
