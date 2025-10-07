package kz.aita.core

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

actual fun getHttpClientEngine(): HttpClientEngine {
  return OkHttp.create {
    preconfigured = OkHttpClient.Builder()
      .cache(
        Cache(
          File(osCacheDirPath),
          cacheSize
        )
      ).build()
  }

}