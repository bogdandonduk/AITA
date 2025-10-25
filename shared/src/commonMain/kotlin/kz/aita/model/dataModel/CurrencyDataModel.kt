package kz.aita.model.dataModel

import kotlinx.serialization.Serializable

@Serializable
data class CurrencyDataModel(
  val code: String,
  val symbol: String,
  val name: List<LocalizedStringDataModel>
)
