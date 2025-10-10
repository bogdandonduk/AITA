package kz.aita.model.wrapper

sealed interface DataState<T> {

  data class Success<T>(val payload: T): DataState<T>

  class Progress<T>: DataState<T>

  class Empty<T>: DataState<T>

  data class Failure<T>(val exception: Exception): DataState<T>

  data class SoftFailure<T>(val exception: Exception, val existingPayload: T?): DataState<T>
}

fun <From, To> DataState<From>.map(
  action: (From) -> To
): DataState<To>  {

  return when (this) {
    is DataState.Success -> DataState.Success(action(payload))
    is DataState.Progress -> DataState.Progress()
    is DataState.Failure -> DataState.Failure(exception)
    is DataState.SoftFailure -> DataState.SoftFailure(exception, existingPayload?.let { action(existingPayload) })
    is DataState.Empty -> DataState.Empty()
  }
}
