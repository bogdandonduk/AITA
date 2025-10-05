package kz.aita.compose.render

import io.kamel.core.config.Core
import io.kamel.core.config.KamelConfig
import io.kamel.core.config.httpUrlFetcher
import io.kamel.core.config.takeFrom
import io.kamel.image.config.imageBitmapDecoder
import io.kamel.image.config.svgDecoder
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kz.aita.core.cacheSize

val kamelConfig = KamelConfig {
  httpUrlFetcher {
    httpCache(cacheSize)

    defaultRequest {
      headers.append(HttpHeaders.CacheControl, "no-cache")
      headers.append(HttpHeaders.Pragma, "no-cache") // legacy proxies
    }

    install(HttpRequestRetry) {
      maxRetries = 2
      retryIf { _, response -> !response.status.isSuccess() }
    }
  }
  takeFrom(KamelConfig.Core)

  svgDecoder()
  imageBitmapDecoder()
}