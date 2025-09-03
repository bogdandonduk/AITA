package kz.aita.core.async.coroutines

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

actual val Dispatchers.io: CoroutineDispatcher
  get() = Dispatchers.IO