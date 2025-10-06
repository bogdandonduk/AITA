package kz.aita.core

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import kz.aita.KeyValueDatabase

actual var sqlDelightDriver: SqlDriver? =
  NativeSqliteDriver(
    schema = KeyValueDatabase.Schema.synchronous(),
    name = "key_value.db"
  )
