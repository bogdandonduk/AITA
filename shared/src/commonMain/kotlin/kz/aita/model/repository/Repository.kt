package kz.aita.model.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.CoroutineContext

open class Repository : CoroutineScope {

  override val coroutineContext: CoroutineContext = SupervisorJob()
}
