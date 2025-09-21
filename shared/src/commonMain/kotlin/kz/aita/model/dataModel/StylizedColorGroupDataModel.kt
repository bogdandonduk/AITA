package kz.aita.model.dataModel

import kotlinx.serialization.Serializable

@Serializable
data class StylizedColorGroupDataModel(
  val id: Long,
  val values: List<StylizedColorDataModel>
)
