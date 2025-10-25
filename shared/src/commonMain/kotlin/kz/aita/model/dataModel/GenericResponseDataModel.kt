package kz.aita.model.dataModel

import kotlinx.serialization.Serializable
import kz.aita.core.jsonBase

@Serializable
data class GenericResponseDataModel(
  val message: String?,
  val payload: String?,
  val negative: Boolean
) {

  fun getMessage(): List<LocalizedStringDataModel>? {
    return message?.let { jsonBase.decodeFromString(it) }
  }

  inline fun <reified T> getPayload(): T? {
    return payload?.let { jsonBase.decodeFromString(it) }
  }

  inline fun <reified T> toResponseDataModel(): ResponseDataModel<T> {
    return ResponseDataModel(
      message?.let { jsonBase.decodeFromString(it) },
      payload?.let { jsonBase.decodeFromString(it) },
      negative
    )
  }
}