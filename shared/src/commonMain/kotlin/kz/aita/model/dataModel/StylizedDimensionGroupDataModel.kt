package kz.aita.model.dataModel

import kotlinx.serialization.Serializable

@Serializable
data class StylizedDimensionGroupDataModel(
  val id: Long,
  val values: List<StylizedDimensionDataModel>
)
