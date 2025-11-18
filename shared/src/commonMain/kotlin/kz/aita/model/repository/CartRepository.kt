package kz.aita.model.repository

import kotlinx.coroutines.flow.Flow
import kz.aita.model.dataModel.GoodsItemInCartDataModel
import kz.aita.model.dataModel.QuantityDataModel

interface CartRepository {

  fun addToCart(
    id: String,
    transactionTypeIndex: Int,
    clientId: Int,
    quantity: QuantityDataModel
  )

  fun deleteCart(transactionTypeIndex: Int, clientId: Int)

  fun deleteCartById(id: String, transactionTypeIndex: Int, clientId: Int)

  fun observeCart(transactionTypeIndex: Int, clientId: Int): Flow<List<GoodsItemInCartDataModel>?>
}