package kz.aita.model.dataModel

import kotlinx.serialization.Serializable

@Serializable
data class ResponseDataModel(
  val id: String,
  val message: List<LocalizedStringDataModel>
)