package kz.aita.model.dataModel

data class GoodsCategoryDataModel(
  val id: Long,
  val name: String,
  val imageUrl: String,
  val quantityWithUnitSerialized: String,
  val storeId: Long,
  val storeSubId: Long,
  val universal: Boolean
)
