package kz.aita.model.store.base

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

abstract class Store {

  protected val job = SupervisorJob()
  protected val coroutineScope = CoroutineScope(job)

  abstract fun initialize()
}
