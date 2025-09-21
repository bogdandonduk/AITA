package kz.aita.core

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.java.Java

actual fun getHttpClientEngine(): HttpClientEngine {
  return Java.create()
}