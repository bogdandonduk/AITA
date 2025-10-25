package kz.aita.model.dataModel

import kotlinx.serialization.Serializable

@Serializable
data class PriceDataModel(
  val price: String,
  val currency: String,
  val supplierId: String
)