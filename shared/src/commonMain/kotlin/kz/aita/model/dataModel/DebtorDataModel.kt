package kz.aita.model.dataModel

import kotlinx.serialization.Serializable

@Serializable
data class DebtorDataModel(
  val id: String,
  val email: String,
  val debtAmount: Double,
  val currency: String,
  val phoneNumber: String,
  val firstName: String,
  val lastName: String,
  val transactionIds: List<Long>
)