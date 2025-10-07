package kz.aita.core

import kotlinx.serialization.json.Json

val jsonBase: Json by lazy {
  Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
  }
}