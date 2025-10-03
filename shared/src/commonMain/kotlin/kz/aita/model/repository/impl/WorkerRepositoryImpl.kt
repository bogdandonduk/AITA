package kz.aita.model.repository.impl

import kz.aita.model.dataModel.UserAccountDataModel
import kz.aita.model.repository.Repository
import kz.aita.model.repository.WorkerRepository
import kz.aita.model.service.GenericLocalService
import kz.aita.model.service.GenericRemoteService
import kz.aita.model.wrapper.MutableDataStateFlow

class WorkerRepositoryImpl(
  private val genericRemoteService: GenericRemoteService,
  private val genericLocalService: GenericLocalService
): Repository(), WorkerRepository {

  private val _storeWorkersState = MutableDataStateFlow<List<UserAccountDataModel>>(this)
  override val storeWorkersState = _storeWorkersState.asDataStateFlow()

  override fun getStoreWorkers() {
    TODO("Not yet implemented")
  }

}
