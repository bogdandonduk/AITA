package kz.aita.model.repository.impl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kz.aita.core.io
import kz.aita.model.dataModel.GoodsItemCategoryDataModel
import kz.aita.model.dataModel.UserAccountDataModel
import kz.aita.model.repository.GoodsCategoryRepository
import kz.aita.model.repository.Repository
import kz.aita.model.repository.SupplierRepository
import kz.aita.model.service.GenericLocalService
import kz.aita.model.service.GenericRemoteService
import kz.aita.model.wrapper.DataState
import kz.aita.model.wrapper.DataStateFlow
import kz.aita.model.wrapper.MutableDataStateFlow

class GoodsCategoryRepositoryImpl(
  private val genericRemoteService: GenericRemoteService,
  private val genericLocalService: GenericLocalService
): Repository(), GoodsCategoryRepository {

  private val _categoriesState = MutableDataStateFlow<List<GoodsItemCategoryDataModel>>(this)
  override val categoriesState = _categoriesState.asDataStateFlow()

  init {
    getCategories()
  }

  override fun getCategories() {
    launch(Dispatchers.io) {
      _categoriesState
        .emit(
          DataState.Success(
            listOf(

            )
          )
        )
    }
  }
}
