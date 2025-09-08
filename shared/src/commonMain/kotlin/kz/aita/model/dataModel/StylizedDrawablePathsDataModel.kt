package kz.aita.model.dataModel

import kotlinx.serialization.Serializable

@Serializable
data class StylizedDrawablePathsDataModel(
  val locale: String,
  val theme: Long,
  val path: String
)