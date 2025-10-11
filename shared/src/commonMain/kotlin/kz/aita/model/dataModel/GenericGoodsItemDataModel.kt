package kz.aita.model.dataModel

import kotlinx.serialization.Serializable

@Serializable
data class GenericGoodsItemDataModel(
  val id: String,
  val barcode: List<String>?,
  val name: List<LocalizedStringDataModel>,
  val typeIds: List<String>?,
  val categoryIds: List<String>?,
  val supplierIds: List<String>?,
  val manufacturerIds: List<String>?
)