package kz.aita.model.dataModel

import kotlinx.serialization.Serializable

@Serializable
data class QuantityDataModel(
  val immutableUnitName: String,
  val total: Double,
  val pricedAmount: Double = 1.0
)
