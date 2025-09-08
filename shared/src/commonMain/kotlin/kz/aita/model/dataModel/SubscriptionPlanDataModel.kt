package kz.aita.model.dataModel

import kotlinx.serialization.Serializable

@Serializable
data class SubscriptionPlanDataModel(
  val id: Int,
  val name: String,
  val storesCount: Int,
  val cashRegistersCount: Int,
  val monthlyPrice: Double
)
