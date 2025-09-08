package kz.aita.model.dataModel

import kotlinx.serialization.Serializable

@Serializable
data class GoodsItemInTransactionDataModel(
  val barcode: String,
  val quantity: Double,
  val pricePerUnit: Double,
  val supplierId: Long? = null
)
