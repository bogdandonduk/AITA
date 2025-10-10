package kz.aita.server.util

import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*

fun metaFrom(call: ApplicationCall): Map<String, String> = mapOf(
  "ip" to (call.request.header("X-Forwarded-For") ?: call.request.origin.remoteHost),
  "ua" to (call.request.userAgent() ?: "unknown")
)