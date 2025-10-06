package kz.aita.server

import io.ktor.server.application.*
import io.ktor.server.netty.*
import io.ktor.http.*
import io.ktor.http.content.CachingOptions
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.autohead.*
import io.ktor.server.plugins.cachingheaders.CachingHeaders
import io.ktor.server.plugins.conditionalheaders.*
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.netty.handler.codec.compression.StandardCompressionOptions.deflate
import io.netty.handler.codec.compression.StandardCompressionOptions.gzip
import kotlinx.serialization.json.Json
import kz.aita.core.cacheMaxAgeSec
import kz.aita.server.jwt.TokenService
import kz.aita.server.jwt.configureJwtAuth
import kz.aita.server.jwt.jwtCfg
import kz.aita.server.route.authRoutes
import kz.aita.server.route.filesRoutes
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database

fun main() = EngineMain.main(emptyArray())

fun Application.module() {
  install(CallLogging)
  install(AutoHeadResponse)
//  install(Compression) {
//    gzip()
//    deflate()
//  }
  install(DefaultHeaders) {
    header(HttpHeaders.Vary, "Accept-Encoding")
  }
  install(ConditionalHeaders) // adds ETag/Last-Modified when possible
  install(CachingHeaders) {
    options { _, outgoing ->
      when (outgoing.contentType?.withoutParameters()) {
        ContentType.Image.SVG,
        ContentType.Image.PNG,
        ContentType.Image.JPEG,
        ContentType("image","webp") ->
          CachingOptions(CacheControl.NoCache(null))

        else -> CachingOptions(CacheControl.NoCache(null))
      }
    }
  }
  install(CORS) {
    anyHost() // for LAN/dev; lock down in prod
    allowHeader(HttpHeaders.ContentType)
    allowMethod(HttpMethod.Get)
  }
  install(ContentNegotiation) {
    json(Json {
      prettyPrint = true
      ignoreUnknownKeys = true
      explicitNulls = true
      encodeDefaults = true
    })
  }

  Flyway.configure()
    .dataSource(
      environment.config.property("db.url").getString(),
      environment.config.property("db.user").getString(),
      environment.config.property("db.pass").getString()
    )
    .locations(environment.config.propertyOrNull("flyway.locations")?.getString() ?: "classpath:db/migration")
    .baselineOnMigrate(true)
    .validateOnMigrate(true)
    .load()
    .migrate()

  Database.connect(
    url = System.getenv("AITA_DB_URL"),
    driver = "org.postgresql.Driver",
    user = System.getenv("DB_USER"),
    password = System.getenv("DB_PASS"),
  )

  configureJwtAuth()

  filesRoutes()

  val tokenService = TokenService(jwtCfg())
  authRoutes(tokenService)
}
