package kz.aita

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.languageCode
import platform.Foundation.preferredLanguages
import platform.UIKit.UIDevice

actual var cacheDirPath: String = ""
actual fun getSystemLocaleLanguage(): String? {
  return (NSLocale.preferredLanguages.firstOrNull() as? String)
    ?: NSLocale.currentLocale.languageCode
}

actual fun getPlatformName(): String= UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion

actual fun getHttpClientEngine(): HttpClientEngine {
  return Darwin.create()
}

actual var sqlDelightDriver: SqlDriver? =
  NativeSqliteDriver(
    schema = AppDatabase.Schema.synchronous(),
    name = "app_database.db"
  )

actual var tokenStore: DataStore<TokenPair>? = null
actual var userAccountStore: DataStore<UserAccountDataModel>? = null