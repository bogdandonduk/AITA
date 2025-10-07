package kz.aita.model.dataModel

import kotlinx.serialization.Serializable

@Serializable
data class TransactionDataModel(
  val id: String,
  val workshiftId: Long,
  val type: String,
  val storeId: String,
  val goodsInTransaction: List<GoodsItemInTransactionDataModel>,
  val paidCash: Double,
  val paidCard: Double,
  val cardPaymentOptionId: Int,
  val debtor: DebtorDataModel? = null,
  val timeMillis: Long
)
