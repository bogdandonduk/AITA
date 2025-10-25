package kz.aita.model.dataModel

import kotlinx.serialization.Serializable

@Serializable
data class CountryDataModel(
  val locale: String,
  val language: String,
  val name: List<LocalizedStringDataModel>,
  val flagDrawablePath: String,
  val cities: List<CityDataModel>,
  val phoneNumberCode: String,
  val phoneNumberSize: Int,
  val currencies: List<CurrencyDataModel>
)
