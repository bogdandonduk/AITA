package kz.aita.core

import io.ktor.client.engine.*
import io.ktor.client.engine.okhttp.*
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File

actual fun getHttpClientEngine(): HttpClientEngine {
  return OkHttp.create {
    preconfigured = OkHttpClient.Builder()
      .cache(
        Cache(
          File(cacheDirPath),
          cacheSize
        )
      ).build()
  }

}