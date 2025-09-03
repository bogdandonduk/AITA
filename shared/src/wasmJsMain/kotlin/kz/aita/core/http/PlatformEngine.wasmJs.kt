package kz.aita.core.http

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.js.Js

actual fun platformHttpClientEngine(): HttpClientEngine {
  return Js.create()
}