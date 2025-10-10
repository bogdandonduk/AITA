package kz.aita.model.dataModel

import kotlinx.serialization.Serializable

@Serializable
data class UserAccountDataModel(
  val id: String,
  val phoneNumber: String,
  val email: String,
  val firstName: String,
  val lastName: String,
  val countryLocale: String,
  val workerAccountIds: String?,
  val supplierAccountIds: String?,
  val createdAt: Long,
  val isActive: Boolean
)