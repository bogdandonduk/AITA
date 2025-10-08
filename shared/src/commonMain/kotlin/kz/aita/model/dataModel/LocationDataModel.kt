package kz.aita.model.dataModel

import kotlinx.serialization.Serializable

@Serializable
data class LocationDataModel(
  val name: String,
  val postalIndex: String,
  val latitude: Double,
  val longitude: Double
)