package kz.aita.model.dataModel

data class CountryDataModel(
  val locale: String,
  val translatedNames: List<LocalizedStringDataModel>,
  val flagUrl: String,
  val cities: List<BoxLocationDataModel>,
  val phoneNumberCode: String,
  val phoneNumberSize: Int,
  val currency: String
)
