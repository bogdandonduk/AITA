package kz.aita.model.dataModel

data class StoreDataModel(
  val id: Long,
  val subId: Long,
  val name: String,
  val location: LocationDataModel,
  val rent: String
)
