package kz.aita.model.dataModel

import kotlinx.serialization.Serializable

@Serializable
data class CityDataModel(
  val name: List<LocalizedStringDataModel>,
  var centerLatitude: Double,
  var centerLongitude: Double,
  val swLatitude: Double,
  val swLongitude: Double,
  val neLatitude: Double,
  val neLongitude: Double
)
