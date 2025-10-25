package kz.aita.server

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.autohead.*
import io.ktor.server.plugins.cachingheaders.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.compression.gzip
import io.ktor.server.plugins.conditionalheaders.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.defaultheaders.*
import kotlinx.serialization.json.Json
import kz.aita.model.dataModel.LocalizedStringDataModel
import kz.aita.server.db.GenericGoodsCategories
import kz.aita.server.jwt.TokenService
import kz.aita.server.jwt.configureJwtAuth
import kz.aita.server.jwt.jwtConfig
import kz.aita.server.route.authRoutes
import kz.aita.server.route.filesRoutes
import kz.aita.server.route.genericGoodsCategoriesRoute
import kz.aita.server.route.genericGoodsItemsRoute
import kz.aita.server.route.stockRoute
import kz.aita.server.route.storesRoute
import kz.aita.server.route.suppliersRoute
import kz.aita.server.route.userRoute
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

fun main() = EngineMain.main(emptyArray())

fun Application.module() {

  install(CallLogging)
  install(AutoHeadResponse)
  install(Compression) {
    gzip() { priority = 1.0 }
  }
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
        ContentType("image", "webp") ->
          CachingOptions(CacheControl.NoCache(null))

        else -> CachingOptions(CacheControl.NoCache(null))
      }
    }
  }
  install(CORS) {
    anyHost() // for LAN/dev; lock down in prod
    allowHeader(HttpHeaders.ContentType)
    allowMethod(HttpMethod.Get)
    allowMethod(HttpMethod.Post)
    allowMethod(HttpMethod.Put)
    allowMethod(HttpMethod.Delete)
  }
  install(ContentNegotiation) {
    json(Json {
      prettyPrint = true
      isLenient = true
      ignoreUnknownKeys = true
      explicitNulls = true
      encodeDefaults = true
    })
  }
//  intercept(ApplicationCallPipeline.Plugins) {
//    val ct = call.request.headers[HttpHeaders.ContentType]
//
//  }
  val ds = HikariDataSource(HikariConfig().apply {
    jdbcUrl = environment.config.property("db.url").getString()
    username = environment.config.property("db.user").getString()
    password = environment.config.property("db.pass").getString()
    driverClassName = "org.postgresql.Driver"
    maximumPoolSize = 10
    minimumIdle = 2
    isAutoCommit = false
  })

  Flyway.configure()
    .dataSource(ds)
    .locations(environment.config.propertyOrNull("flyway.locations")?.getString() ?: "classpath:db/migration")
    .baselineOnMigrate(true)
    .validateOnMigrate(true)
    .load()
    .migrate()

  Database.connect(ds)

  configureJwtAuth()

  filesRoutes()
  authRoutes(TokenService(jwtConfig()))

  userRoute()
  storesRoute()
  stockRoute()
  suppliersRoute()
  genericGoodsItemsRoute()
  genericGoodsCategoriesRoute()
}