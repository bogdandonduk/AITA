package kz.aita.model.wrapper

sealed interface DataState<T> {

  data class Success<T>(val payload: T) : DataState<T>

  data class Progress<T>(val progressPercentage: Int?) : DataState<T>

  class Empty<T> : DataState<T>

  class Failure<T>(val exception: Exception?) : DataState<T>
}

fun <From, To> DataState<From>.map(
  action: (From) -> To
) : DataState<To>  {

  return when (this) {
    is DataState.Success -> DataState.Success(action(payload))
    is DataState.Progress -> DataState.Progress(progressPercentage)
    is DataState.Failure -> DataState.Failure(exception)
    is DataState.Empty -> DataState.Empty()
  }
}
