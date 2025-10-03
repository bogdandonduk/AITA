package kz.aita.model.repository.impl

import kz.aita.model.dataModel.StoreDataModel
import kz.aita.model.dataModel.UserAccountDataModel
import kz.aita.model.repository.Repository
import kz.aita.model.repository.StoreRepository
import kz.aita.model.repository.WorkerRepository
import kz.aita.model.service.GenericLocalService
import kz.aita.model.service.GenericRemoteService
import kz.aita.model.wrapper.MutableDataStateFlow

class StoreRepositoryImpl(
  private val genericRemoteService: GenericRemoteService,
  private val genericLocalService: GenericLocalService
): Repository(), StoreRepository {

  private val _storesState = MutableDataStateFlow<List<StoreDataModel>>(this)
  override val storesState = _storesState.asDataStateFlow()

  override fun getStores() {
    TODO("Not yet implemented")
  }

}
