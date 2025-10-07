package kz.aita.model.dataModel

import kotlinx.serialization.Serializable

@Serializable
data class GoodsItemInRemovalDataModel(
  val barcode: String,
  val quantity: Double,
  val storeId: String
)
