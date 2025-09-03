package kz.aita.model.dataModel

data class GoodsItemInRemovalDataModel(
  val barcode: String,
  val quantity: Double,
  val storeId: Long,
  val storeSubId: Long,
)
