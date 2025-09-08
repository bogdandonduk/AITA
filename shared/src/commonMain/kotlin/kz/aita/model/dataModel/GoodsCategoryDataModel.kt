package kz.aita.model.dataModel

import kotlinx.serialization.Serializable

@Serializable
data class GoodsCategoryDataModel(
  val id: Long,
  val name: String,
  val imageUrl: String,
  val quantityWithUnitSerialized: String,
  val storeId: Long,
  val storeSubId: Long,
  val universal: Boolean
)
