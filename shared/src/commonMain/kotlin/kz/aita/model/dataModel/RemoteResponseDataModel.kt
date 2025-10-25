package kz.aita.model.dataModel

import kotlinx.serialization.Serializable

@Serializable
data class RemoteResponseDataModel(
  val id: String,
  val message: List<LocalizedStringDataModel>
)