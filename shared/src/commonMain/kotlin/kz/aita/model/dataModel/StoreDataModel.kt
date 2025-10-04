package kz.aita.model.dataModel

import kotlinx.serialization.Serializable

@Serializable
data class StoreDataModel(
  val id: String,
  val subId: String,
  val name: String,
  val location: LocationDataModel
)
