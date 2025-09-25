package kz.aita.model.dataModel

import kotlinx.serialization.Serializable

@Serializable
data class StylizedDrawablePathsGroupDataModel(
  val id: Long,
  val values: List<StylizedDrawablePathsDataModel>
)