package kz.aita.core

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import kz.aita.AppDatabase

actual var sqlDelightDriver: SqlDriver? =
  NativeSqliteDriver(
    schema = AppDatabase.Schema.synchronous(),
    name = "app_database.db"
  )