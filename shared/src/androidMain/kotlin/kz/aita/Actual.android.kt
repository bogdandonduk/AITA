package kz.aita

import app.cash.sqldelight.db.SqlDriver
import io.ktor.client.engine.*
import io.ktor.client.engine.okhttp.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File

actual fun getCurrentTimeMillis(): Long = System.currentTimeMillis()

actual var getStoredUserAuthTokens: (() -> TokenPair?)? = null
actual var setStoredUserAuthTokens: ((TokenPair?) -> Unit)? = null

actual var getStoredUserAccountDataModel: (() -> UserAccountDataModel?)? = null
actual var setStoredUserAccountDataModel: ((UserAccountDataModel?) -> Unit)? = null

actual var cacheDirPath: String = ""
actual val Dispatchers.ourIo: CoroutineDispatcher
  get() = Dispatchers.IO

actual var getHttpClientEngine: () -> HttpClientEngine = {
  OkHttp.create { preconfigured = OkHttpClient.Builder().cache(Cache(File(cacheDirPath, "http"), cacheSize)).build() }
}

actual var getSystemLocaleLanguage: () -> String = {
  "ru"
}

actual var getPlatformName: () -> String = {
  "android"
}

actual var getSqlDelightDriver: (() -> SqlDriver?)? = {
  null
}