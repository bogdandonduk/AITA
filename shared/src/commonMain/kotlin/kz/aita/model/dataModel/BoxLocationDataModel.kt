package kz.aita.model.dataModel

data class BoxLocationDataModel(
  val translatedNames: List<LocalizedStringDataModel>,
  var centerLatitude: Double,
  var centerLongitude: Double,
  val swLatitude: Double,
  val swLongitude: Double,
  val neLatitude: Double,
  val neLongitude: Double
)
