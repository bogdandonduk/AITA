package kz.aita.model.dataModel

import kotlinx.serialization.Serializable

@Serializable
data class StylizedDrawablePathsDataModel(
  val themeId: Long,
  val path: String
)