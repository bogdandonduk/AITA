package kz.aita.model.repository

import kz.aita.model.dataModel.StoreDataModel
import kz.aita.model.wrapper.DataStateFlow

interface StoreRepository {
  val storesState: DataStateFlow<List<StoreDataModel>>

  fun getStores()

  fun addStore(store: StoreDataModel)
}
