package kz.aita.model.dataModel

import kotlinx.serialization.Serializable

@Serializable
data class GenericGoodsItemsDataModel(
  val id: String,
  val barcode: String,
  val name: String,
  val quantity: QuantityDataModel,
  val categoryId: String,
  val supplierId: String,
  val salePrice: Double,
  val returnPrice: Double = salePrice,
  val supplyPrice: Double,
  val saleCurrency: String,
  val returnCurrency: String = saleCurrency,
  val supplyCurrency: String
)