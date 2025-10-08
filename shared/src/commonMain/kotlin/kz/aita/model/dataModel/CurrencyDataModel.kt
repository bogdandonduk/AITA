package kz.aita.model.dataModel

import kotlinx.serialization.Serializable

@Serializable
data class CurrencyDataModel(
  val currency: String,
  val countries: List<String>,
  val symbol: String,
  val name: List<LocalizedStringDataModel>
)
