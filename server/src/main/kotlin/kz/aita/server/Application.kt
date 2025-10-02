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
  val cfg = environment.config
  val host = cfg.propertyOrNull("ktor.deployment.host")?.getString()
  val port = cfg.propertyOrNull("ktor.deployment.port")?.getString()
  val url = Thread.currentThread().contextClassLoader.getResource("application.yaml")
  println("Ktor read host=$host port=$port")
  println("application.yaml loaded from: $url")

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
    })
  }

  Database.connect(
    url = System.getenv("DB_URL") ?: "jdbc:postgresql://localhost:5432/aita",
    driver = "org.postgresql.Driver",
    user = System.getenv("DB_USER") ?: "postgres",
    password = System.getenv("DB_PASS") ?: "postgres"
  )

  transaction { SchemaUtils.create(Users, RefreshSessions) }

  configureJwtAuth()

  routes()
}




























