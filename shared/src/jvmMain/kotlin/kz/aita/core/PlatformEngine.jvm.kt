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
          Files.createDirectories(
            System.getProperty("os.name").lowercase().run {
              when {
                contains("win") -> {
                  val base = System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home")
                  Paths.get(
                    base,
                    "AITA",
                    "Cache",
                    "http"
                  )
                }

                contains("mac") -> {
                  Paths.get(
                    System.getProperty("user.home"),
                    "Library",
                    "Caches",
                    "AITA",
                    "http"
                  )
                }

                else -> {
                  Paths.get(
                    System.getenv("XDG_CACHE_HOME") ?: "${System.getProperty("user.home")}/.cache",
                    "AITA", "http"
                  )
                }
              }
            }
          ).toFile(),
          cacheSize)
      ).build()
  }

}