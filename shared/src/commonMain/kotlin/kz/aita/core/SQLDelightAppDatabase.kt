package kz.aita.core

import app.cash.sqldelight.db.SqlDriver
import kz.aita.AppDatabase

expect fun getSqlDelightDriver(): SqlDriver

expect fun getAppDatabase(): AppDatabase