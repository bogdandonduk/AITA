package kz.aita.model.store.base

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

open class Store {

  protected val job = SupervisorJob()
  protected val coroutineScope = CoroutineScope(job)
}
