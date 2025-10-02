package kz.aita.server

import io.ktor.server.application.*
import io.ktor.server.netty.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.autohead.*
import io.ktor.server.plugins.conditionalheaders.*
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.cors.routing.CORS
import io.netty.handler.codec.compression.StandardCompressionOptions.deflate
import io.netty.handler.codec.compression.StandardCompressionOptions.gzip
import kotlinx.serialization.json.Json
import kz.aita.server.db.RefreshSessions
import kz.aita.server.db.Users
import kz.aita.server.jwt.configureJwtAuth
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

fun main() = EngineMain.main(emptyArray())

fun Application.module() {
  install(CallLogging)
  install(AutoHeadResponse)
  install(Compression) {
    gzip()
    deflate()
  }
  install(ConditionalHeaders) // adds ETag/Last-Modified when possible
  install(CORS) {
    anyHost() // for LAN/dev; lock down in prod
    allowHeader(HttpHeaders.ContentType)
    allowMethod(HttpMethod.Get)
  }
  install(ContentNegotiation) {
    json(Json {
      prettyPrint = true
      ignoreUnknownKeys = true
      explicitNulls = false
      encodeDefaults = true
    })
  }

  Database.connect(
    url = System.getenv("DB_URL"),
    driver = "org.postgresql.Driver",
    user = System.getenv("DB_USER"),
    password = System.getenv("DB_PASS"),
  )

  transaction { SchemaUtils.create(Users, RefreshSessions) }

  configureJwtAuth()

  routes()
}
