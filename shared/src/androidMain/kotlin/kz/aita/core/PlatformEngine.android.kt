package kz.aita.core

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File

actual fun getHttpClientEngine(): HttpClientEngine {
  return OkHttp.create { preconfigured = OkHttpClient.Builder().cache(Cache(File(osCacheDirPath, "http"), cacheSize)).build() }
}