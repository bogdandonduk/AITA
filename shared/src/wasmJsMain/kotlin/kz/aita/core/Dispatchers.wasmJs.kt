package kz.aita.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

actual val Dispatchers.io: CoroutineDispatcher
  get() = Dispatchers.Default