package kz.aita.core

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.engine.okhttp.OkHttpEngine
import kz.aita.app.AITA
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File

actual fun getHttpClientEngine(): HttpClientEngine {
  return OkHttp.create { preconfigured = OkHttpClient.Builder().cache(Cache(File(AITA.get().cacheDir, "http"), 4000L * 1024 * 1024)).build() }
}