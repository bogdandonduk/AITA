package kz.aita.core

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.db.SqlDriver
import kz.aita.KeyValueDatabase

actual fun getSqlDelightDriver(): SqlDriver {
  return app.cash.sqldelight.driver.native.NativeSqliteDriver(
    schema = KeyValueDatabase.Schema.synchronous(),
    name = "key_value.db"
  )
}

actual fun getKeyValueDatabase(): KeyValueDatabase {
  return KeyValueDatabase(getSqlDelightDriver())
}