package kz.aita.model.dataModel

data class AccountSubscriptionStatusDataModel(
  val id: Long,
  val userId: Long,
  val balance: Double,
  val subscriptionPlanId: Long,
  val lastChargeTime: Long,
  val nextChargeTime: Long
)