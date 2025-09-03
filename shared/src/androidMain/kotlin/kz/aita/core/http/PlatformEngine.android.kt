package kz.aita.core.http

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp

actual fun platformHttpClientEngine(): HttpClientEngine {
  return OkHttp.create()
}