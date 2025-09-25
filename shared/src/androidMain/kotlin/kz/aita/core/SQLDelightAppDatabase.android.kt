package kz.aita.core

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import kz.aita.AppDatabase
import kz.aita.app.AITA

actual fun getSqlDelightDriver(): SqlDriver {
  return AndroidSqliteDriver(
    schema = AppDatabase.Schema.synchronous(),
    context = AITA.get(),
    name = "app.db"
  )
}

actual fun getAppDatabase(): AppDatabase {
  return AppDatabase(getSqlDelightDriver())
}