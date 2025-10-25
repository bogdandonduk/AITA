package kz.aita.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

abstract class StateHost {
  private val _state = MutableStateFlow(mapOf<String, String>())
  val state = _state.asStateFlow()

  suspend fun setState(pair: Pair<String, String>) {
    _state.emit(
      _state.value.toMutableMap().apply {
        this[pair.first] = pair.second
      }
    )
  }

  suspend fun removeState(key: String) {
    _state.emit(
      _state.value.toMutableMap().apply {
        remove(key)
      }
    )
  }
}