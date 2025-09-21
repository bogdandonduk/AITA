package kz.aita.model.dataModel

import kotlinx.serialization.Serializable

@Serializable
data class StylizedDimensionDataModel(
  val sizeModeId: Long,
  val screenWidthDivisor: Float
)
