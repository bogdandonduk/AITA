package kz.aita.model.dataModel

import kotlinx.serialization.Serializable

@Serializable
class GoodsItemInCartDataModel(
  val id: String,
  val transactionTypeIndex: Int,
  val clientId: Int,
  val quantity: QuantityDataModel,
  val timeAdded: Long
)