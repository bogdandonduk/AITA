package kz.aita

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.languageCode
import platform.Foundation.preferredLanguages

actual var getStoredUserAuthTokens: (() -> TokenPair?)? = null
actual var setStoredUserAuthTokens: ((TokenPair?) -> Unit)? = null

actual var getStoredUserAccountDataModel: (() -> UserAccountDataModel?)? = null
actual var setStoredUserAccountDataModel: ((UserAccountDataModel?) -> Unit)? = null

actual var cacheDirPath: String = ""
actual val Dispatchers.ourIo: CoroutineDispatcher
  get() = Dispatchers.Default

actual var getHttpClientEngine: () -> HttpClientEngine = {
  Darwin.create()
}

actual var getSystemLocaleLanguage: () -> String = {
  (NSLocale.preferredLanguages.firstOrNull() as? String)
    ?: NSLocale.currentLocale.languageCode
}

actual var getPlatformName: () -> String = {
  "ios"
}

actual var getSqlDelightDriver: (() -> SqlDriver?)? = {
  NativeSqliteDriver(
    schema = AppDatabase.Schema.synchronous(),
    name = "app_database.db"
  )
}