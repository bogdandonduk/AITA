package kz.aita.model.repository

import kotlinx.coroutines.flow.StateFlow
import kz.aita.model.dataModel.StoreDataModel
import kz.aita.model.wrapper.DataState
import kz.aita.model.wrapper.DataStateFlow

interface StoreRepository {
  val storesState: DataStateFlow<List<StoreDataModel>>
  val activeStoreId: StateFlow<String?>

  fun getStores()

  fun addStore(store: StoreDataModel)

  fun updateStore(store: StoreDataModel, onCompleted: ((DataState<StoreDataModel>) -> Unit)? = null)

  fun setActiveStoreId(id: String?)
}
