package kz.aita.core

import app.cash.sqldelight.db.SqlDriver
import kz.aita.KeyValueDatabase

expect fun getSqlDelightDriver(): SqlDriver

expect fun getKeyValueDatabase(): KeyValueDatabase