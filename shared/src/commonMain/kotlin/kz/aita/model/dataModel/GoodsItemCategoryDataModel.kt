package kz.aita.model.dataModel

import kotlinx.serialization.Serializable

@Serializable
data class GoodsItemCategoryDataModel(
  val id: String,
  val name: String,
  val imageUrl: String,
  val quantityUnit: QuantityDataModel,
  val universal: Boolean,
  val storeId: String
)