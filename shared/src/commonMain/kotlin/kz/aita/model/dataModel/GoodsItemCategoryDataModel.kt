package kz.aita.model.dataModel

import kotlinx.serialization.Serializable

@Serializable
data class GoodsItemCategoryDataModel(
  val id: Long,
  val name: String
)