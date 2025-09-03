package kz.aita.model.dataModel

data class TransactionDataModel(
  val id: Long,
  val workshiftId: Long,
  val type: String,
  val storeId: Long,
  val storeSubId: Long,
  val goodsInTransaction: List<GoodsItemInTransactionDataModel>,
  val paidCash: Double,
  val paidCard: Double,
  val cardPaymentOptionId: Int,
  val debtor: DebtorDataModel? = null,
  val timeMillis: Long
)
