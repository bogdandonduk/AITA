package kz.aita.model.service

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kz.aita.AppDatabase
import kz.aita.core.io
import kz.aita.core.jsonBase
import kz.aita.model.dataModel.GoodsItemInCartDataModel
import kz.aita.model.dataModel.QuantityDataModel

class GenericLocalService(
  private val appDatabase: AppDatabase
) {

  suspend fun putKv(key: String, value: String?) {
    appDatabase.app_databaseQueries.insertKv(key, value)
  }

  suspend fun getKv(key: String): String? =
    appDatabase.app_databaseQueries.selectKvByKey(key).awaitAsOneOrNull()?.value_

  suspend fun deleteKv(key: String) {
    appDatabase.app_databaseQueries.deleteKv(key)
  }

  fun observeKv(key: String): Flow<String?> =
    appDatabase.app_databaseQueries.selectKvByKey(key)
      .asFlow()
      .mapToOneOrNull(Dispatchers.io)
      .map {
        it?.value_
      }

  suspend fun upsertCart(
    id: String,
    transactionTypeIndex: Int,
    clientId: Int,
    quantity: QuantityDataModel
  ) {
    appDatabase.app_databaseQueries.upsertCart(id, transactionTypeIndex.toLong(), clientId.toLong(), jsonBase.encodeToString(quantity))
  }

  suspend fun deleteCart(transactionTypeIndex: Int, clientId: Int) {
    appDatabase.app_databaseQueries.deleteCart(transactionTypeIndex.toLong(), clientId.toLong())
  }

  suspend fun deleteCartById(id: String, transactionTypeIndex: Int, clientId: Int) {
    appDatabase.app_databaseQueries.deleteCartById(id, transactionTypeIndex.toLong(), clientId.toLong())
  }

  fun observeCart(transactionTypeIndex: Int, clientId: Int): Flow<List<GoodsItemInCartDataModel>?> =
    appDatabase.app_databaseQueries.getCart(transactionTypeIndex.toLong(), clientId.toLong())
      .asFlow()
      .mapToList(Dispatchers.io)
      .map { rows ->
        rows.map { row ->
          GoodsItemInCartDataModel(
            id = row.id,
            transactionTypeIndex = row.transactionTypeIndex.toInt(),
            clientId = row.clientId.toInt(),
            quantity = jsonBase.decodeFromString(row.quantity),
            timeAdded = row.timeAdded
          )
        }
      }
}