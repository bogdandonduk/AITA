package kz.aita.model.repository

import kz.aita.model.dataModel.UserAccountDataModel
import kz.aita.model.wrapper.DataStateFlow

interface WorkerRepository {

  val storeWorkersState: DataStateFlow<List<UserAccountDataModel>>

  fun getStoreWorkers()
}
