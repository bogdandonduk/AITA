package kz.aita.model.service

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kz.aita.KeyValueDatabase
import kz.aita.core.io

class GenericLocalService(
  private val keyValueDatabase: KeyValueDatabase,

) {

  suspend fun putKv(key: String, value: String?) {
    keyValueDatabase.key_valueQueries.insert(key, value)
  }

  suspend fun getKv(key: String): String? =
    keyValueDatabase.key_valueQueries.selectByKey(key).awaitAsOneOrNull()?.value_

  suspend fun deleteKv(key: String) {
    keyValueDatabase.key_valueQueries.delete(key)
  }

  fun observeKv(key: String): Flow<String?> =
    keyValueDatabase.key_valueQueries.selectByKey(key)
      .asFlow()
      .mapToOneOrNull(Dispatchers.io)
      .map {
        it?.value_
      }
}