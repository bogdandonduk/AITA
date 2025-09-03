package kz.aita.core.http

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.java.Java

actual fun platformHttpClientEngine(): HttpClientEngine {
  return Java.create()
}