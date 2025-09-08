package kz.aita.model.dataModel

import kotlinx.serialization.Serializable

@Serializable
data class LocalizedStringDataModel(
  val language: String,
  val value: String
)
