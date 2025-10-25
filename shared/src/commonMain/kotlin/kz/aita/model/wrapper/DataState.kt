package kz.aita.model.wrapper

import kz.aita.model.dataModel.LocalizedStringDataModel

sealed interface DataState<T> {

  val message: List<LocalizedStringDataModel>?

  data class Success<T>(
    val payload: T,
    override val message: List<LocalizedStringDataModel>? = null,
  ): DataState<T>

  class Empty<T>(override val message: List<LocalizedStringDataModel>? = null): DataState<T>
}

fun <From, To> DataState<From>.map(
  action: (From?) -> To
): DataState<To> {

  return when (this) {
    is DataState.Success -> DataState.Success(action(payload))
    is DataState.Empty -> DataState.Empty(message)
  }
}
