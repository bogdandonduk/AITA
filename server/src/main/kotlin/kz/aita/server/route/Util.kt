package kz.aita.server.route

import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.origin
import io.ktor.server.request.header
import io.ktor.server.request.userAgent

fun metaFrom(call: ApplicationCall): Map<String, String> = mapOf(
  "ip" to (call.request.header("X-Forwarded-For") ?: call.request.origin.remoteHost),
  "ua" to (call.request.userAgent() ?: "unknown")
)