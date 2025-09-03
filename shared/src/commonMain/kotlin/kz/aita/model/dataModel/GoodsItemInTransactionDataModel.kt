package kz.aita.model.dataModel

data class GoodsItemInTransactionDataModel(
  val barcode: String,
  val quantity: Double,
  val pricePerUnit: Double,
  val supplierId: Long? = null
)
