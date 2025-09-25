package kz.aita.core

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.db.SqlDriver
import kz.aita.AppDatabase

actual fun getSqlDelightDriver(): SqlDriver {
  return app.cash.sqldelight.driver.native.NativeSqliteDriver(
    schema = AppDatabase.Schema.synchronous(),
    name = "app.db"
  )
}

actual fun getAppDatabase(): AppDatabase {
  return AppDatabase(getSqlDelightDriver())
}