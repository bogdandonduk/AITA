package kz.aita.model.dataModel

import kotlinx.serialization.Serializable

@Serializable
data class QuantityDataModel(
  val immutableUnitName: List<LocalizedStringDataModel>,
  val total: Double,
  val pricedAmount: Double = 1.0,
  val roundTotal: Boolean
)
