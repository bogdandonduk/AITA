package kz.aita.model.repository

import kz.aita.model.wrapper.DataStateFlow

interface WorkerUserRepository {

  val workerUsersState: DataStateFlow<List<WorkerUserRepository>>

  fun getWorkerUsers()
}
