package kz.aita.model.wrapper

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MutableDataStateFlow<T>(
  private val coroutineScope: CoroutineScope,
  initial: T? = null
  ) : DataStateFlow<T> {

  private val _state = MutableStateFlow<DataState<T>>(initial?.run { DataState.Success(initial) } ?: DataState.Empty())
  override val value = _state.asStateFlow()
  private val _payload = MutableStateFlow(initial)
  override val payload = _payload.asStateFlow()

  init {
    coroutineScope.launch {
      value.collect {
        if (it is DataState.Success)
          _payload.emit(it.payload)
      }
    }
  }

  fun emit(newValue: DataState<T>) {
    coroutineScope.launch {
      _state.emit(newValue)
    }
  }

  fun asDataStateFlow(): DataStateFlow<T> {
    return this as DataStateFlow<T>
  }
}

interface DataStateFlow<T> {

  val value: StateFlow<DataState<T>>
  val payload: StateFlow<T?>
  val payloadValue: T?
    get() = payload.value
  val payloadValueNonNull: T
    get() = payloadValue!!
}
