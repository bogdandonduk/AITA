package kz.aita.model.wrapper

import kz.aita.model.dataModel.LocalizedStringDataModel

sealed interface DataState<T> {

  val message: List<LocalizedStringDataModel>?

  data class Success<T>(
    val payload: T,
    override val message: List<LocalizedStringDataModel>? = null,
  ) : DataState<T>

  class Progress<T>(
    val existingPayload: T? = null,
    override val message: List<LocalizedStringDataModel>? = null
  ) : DataState<T>

  class Empty<T>(override val message: List<LocalizedStringDataModel>? = null) : DataState<T>

  data class Failure<T>(
    override val message: List<LocalizedStringDataModel>? = null
  ) : DataState<T>

  data class SoftFailure<T>(
    val existingPayload: T?,
    override val message: List<LocalizedStringDataModel>? = null
  ) : DataState<T>

  fun <T> toProgressWithPayload(message: List<LocalizedStringDataModel>?): Progress<T>? {
    return (this as? Success)?.run { Progress(payload, message) }
      ?: (this as? SoftFailure)?.run { Progress(existingPayload, message) }
  }

  fun <T> toSoftFailureWithPayload(message: List<LocalizedStringDataModel>?): SoftFailure<T>? {
    return (this as? Success)?.run { SoftFailure(payload, message) }
      ?: (this as? Progress)?.run { SoftFailure(existingPayload, message) }
  }
}

fun <From, To> DataState<From>.map(
  action: (From?) -> To
): DataState<To> {

  return when (this) {
    is DataState.Success -> DataState.Success(action(payload))
    is DataState.Progress -> DataState.Progress(action(existingPayload), message)
    is DataState.Failure -> DataState.Failure(message)
    is DataState.SoftFailure -> DataState.SoftFailure(action(existingPayload))
    is DataState.Empty -> DataState.Empty(message)
  }
}
