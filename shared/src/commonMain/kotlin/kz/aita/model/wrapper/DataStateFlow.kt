package kz.aita.model.wrapper

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kz.aita.core.io

class MutableDataStateFlow<T>(
  private val coroutineScope: CoroutineScope,
  initial: T? = null
  ): DataStateFlow<T> {

  private val _state = MutableStateFlow<DataState<T>>(initial?.run { DataState.Success(initial) } ?: DataState.Empty())
  override val value = _state.asStateFlow()
  private val _payload = MutableStateFlow(initial)
  override val payload = _payload.asStateFlow()

  init {
    coroutineScope.launch(Dispatchers.io) {
      value.collect {
        if (it is DataState.Success)
          _payload.emit(it.payload)
      }
    }
  }

  fun emit(newValue: DataState<T>) {
    coroutineScope.launch(Dispatchers.io) {
      _state.emit(newValue)
    }
  }

  fun asDataStateFlow(): DataStateFlow<T> {
    return this as DataStateFlow<T>
  }
}

class MutableDataStateFlowNonNull<T>(
  private val coroutineScope: CoroutineScope,
  initial: T
) : DataStateFlowNonNull<T> {

  private val _state = MutableStateFlow<DataState<T>>(DataState.Success(initial))
  override val value = _state.asStateFlow()
  private val _payload = MutableStateFlow(initial)
  override val payload = _payload.asStateFlow()

  init {
    coroutineScope.launch(Dispatchers.io) {
      value.collect {
        if (it is DataState.Success)
          _payload.emit(it.payload)
      }
    }
  }

  fun emit(newValue: DataState<T>) {
    coroutineScope.launch(Dispatchers.io) {
      _state.emit(newValue)
    }
  }

  fun asDataStateFlow(): DataStateFlowNonNull<T> {
    return this as DataStateFlowNonNull<T>
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

interface DataStateFlowNonNull<T> {

  val value: StateFlow<DataState<T>>
  val payload: StateFlow<T>

  val payloadValue: T
    get() = payload.value
}


