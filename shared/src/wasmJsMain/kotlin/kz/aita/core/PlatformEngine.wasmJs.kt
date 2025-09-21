package kz.aita.core

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.js.Js

actual fun getHttpClientEngine(): HttpClientEngine {
  return Js.create()
}