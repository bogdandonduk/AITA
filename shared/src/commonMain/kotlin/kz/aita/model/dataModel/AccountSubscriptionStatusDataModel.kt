package kz.aita.model.dataModel

import kotlinx.serialization.Serializable

@Serializable
data class AccountSubscriptionStatusDataModel(
  val id: Long,
  val userId: Long,
  val balance: Double,
  val subscriptionPlanId: Long,
  val lastChargeTime: Long,
  val nextChargeTime: Long
)