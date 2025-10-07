package kz.aita.model.dataModel

import kotlinx.serialization.Serializable

@Serializable
data class GoodsCategoryDataModel(
  val id: String,
  val name: String,
  val imageUrl: String,
  val quantityWithUnitSerialized: String,
  val storeId: String,
  val universal: Boolean
)
