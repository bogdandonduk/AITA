package kz.aita.core

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import kz.aita.KeyValueDatabase
import kz.aita.app.AITA

actual fun getSqlDelightDriver(): SqlDriver {
  return AndroidSqliteDriver(
    schema = KeyValueDatabase.Schema.synchronous(),
    context = AITA.get(),
    name = "key_value.db"
  )
}
actual fun getKeyValueDatabase(): KeyValueDatabase {
  return KeyValueDatabase(getSqlDelightDriver())
}