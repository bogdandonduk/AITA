package kz.aita.model.dataModel

import kotlinx.serialization.Serializable

@Serializable
data class GoodsItemDataModel(
  val id: Long,
  val barcode: String,
  val name: String
)