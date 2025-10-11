package kz.aita.model.dataModel

import kotlinx.serialization.Serializable

@Serializable
data class GenericGoodsItemsDataModel(
  val id: String,
  val barcode: String,
  val extraBarcodes: String?,
  val name: String,
  val extraNames: String?,
  val quantity: QuantityDataModel,
  val categoryId: String,
  val extraCategoryIds: String?,
  val supplierId: String,
  val extraSupplierIds: String?,
  val manufacturerId: String,
  val extraManufacturerIds: String?,
  val brandId: String
)