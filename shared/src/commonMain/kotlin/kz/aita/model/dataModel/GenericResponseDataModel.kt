package kz.aita.model.dataModel

import kotlinx.serialization.Serializable

@Serializable
data class GenericResponseDataModel<T>(
  val message: List<LocalizedStringDataModel>?,
  val payload: T?,
  val negative: Boolean
)