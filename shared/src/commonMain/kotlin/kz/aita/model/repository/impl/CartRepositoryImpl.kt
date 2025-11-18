package kz.aita.model.repository.impl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kz.aita.core.io
import kz.aita.model.dataModel.GoodsItemInCartDataModel
import kz.aita.model.dataModel.QuantityDataModel
import kz.aita.model.repository.CartRepository
import kz.aita.model.repository.Repository
import kz.aita.model.service.GenericLocalService

class CartRepositoryImpl(
  private val genericLocalService: GenericLocalService
): Repository(), CartRepository {


  override fun addToCart(
    id: String,
    transactionTypeIndex: Int,
    clientId: Int,
    quantity: QuantityDataModel
  ) {
    launch(Dispatchers.io) {
      genericLocalService.upsertCart(id, transactionTypeIndex, clientId, quantity)
    }
  }

  override fun deleteCart(transactionTypeIndex: Int, clientId: Int) {
    launch(Dispatchers.io) {
      genericLocalService.deleteCart(transactionTypeIndex, clientId)
    }
  }

  override fun deleteCartById(id: String, transactionTypeIndex: Int, clientId: Int) {
    launch(Dispatchers.io) {
      genericLocalService.deleteCartById(id, transactionTypeIndex, clientId)
    }
  }

  override fun observeCart(transactionTypeIndex: Int, clientId: Int): Flow<List<GoodsItemInCartDataModel>?> =
    genericLocalService.observeCart(transactionTypeIndex, clientId)
}