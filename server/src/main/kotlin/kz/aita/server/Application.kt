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
import io.ktor.server.plugins.conditionalheaders.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.defaultheaders.*
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import kz.aita.core.jsonBase
import kz.aita.model.dataModel.LocalizedStringDataModel
import kz.aita.server.db.GenericGoodsItems
import kz.aita.server.db.Manufacturers
import kz.aita.server.db.Suppliers
import kz.aita.server.jwt.TokenService
import kz.aita.server.jwt.configureJwtAuth
import kz.aita.server.jwt.jwtConfig
import kz.aita.server.route.authRoutes
import kz.aita.server.route.filesRoutes
import kz.aita.server.route.storesRoute
import kz.aita.server.route.userRoute
import org.flywaydb.core.Flyway
import org.flywaydb.core.internal.database.sqlite.SQLiteDatabase
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.lowerCase
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.trim
import org.sqlite.SQLiteConfig
import java.io.File
import java.sql.DriverManager
import java.util.UUID

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
  intercept(ApplicationCallPipeline.Plugins) {
    val ct = call.request.headers[HttpHeaders.ContentType]
     println("CT=${ct}")
  }
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

  val path = "AITA/server/assets/temp/MagnumCatalog.sqlite"
  val cfg = SQLiteConfig().apply {
    setReadOnly(true)
    busyTimeout = 1000_000
  }
  val url = "jdbc:sqlite:file:$path?mode=ro&immutable=1"

  transaction {
    DriverManager.getConnection(url, cfg.toProperties()).use { connection ->
      connection.prepareStatement(
        """SELECT "0", "1", "2", "3" FROM excel_table""".trimIndent()
      ).use { preparedStatement ->
        preparedStatement.executeQuery().use { resultSet ->
          resultSet.next()

          while (resultSet.next()) {
            val name = resultSet.getString("0")
            val supplier = resultSet.getString("1")
            val barcode = resultSet.getString("2")
            val manufacturer = resultSet.getString("3")

            var supplierId: UUID? = null
            var manufacturerId: UUID? = null

            val supplierSerialized = try { jsonBase.encodeToString<List<LocalizedStringDataModel>>(listOf(LocalizedStringDataModel(language = "main", value = supplier))) } catch (_: Exception) { null }
            val manufacturerSerialized = try { jsonBase.encodeToString<List<LocalizedStringDataModel>>(listOf(LocalizedStringDataModel(language = "main", value = manufacturer))) } catch (_: Exception) { null }

            if (supplierSerialized != null && Suppliers.select(Suppliers.name).where {
               Suppliers.name eq supplierSerialized
            }.empty()) {
              supplierId = UUID.randomUUID()

              Suppliers.insert {
                it[Suppliers.id] = supplierId
                it[Suppliers.name] = supplierSerialized
              }
            }

            if (manufacturerSerialized != null && Manufacturers.select(Manufacturers.name).where {
                Manufacturers.name eq manufacturerSerialized
            }.empty()) {
              manufacturerId = UUID.randomUUID()

              Manufacturers.insert {
                it[Manufacturers.id] = manufacturerId
                it[Manufacturers.name] = manufacturerSerialized
              }
            }

            GenericGoodsItems.insert {
              it[GenericGoodsItems.barcode] = barcode
              it[GenericGoodsItems.name] = jsonBase.encodeToString(listOf(LocalizedStringDataModel(language = "main", value = name)))
              supplierId?.run {
                it[GenericGoodsItems.supplierIds] = jsonBase.encodeToString(listOf(this.toString()))
              }
              manufacturerId?.run {
                it[GenericGoodsItems.manufacturerIds] = jsonBase.encodeToString(listOf(this.toString()))
              }
            }
          }
        }
      }
    }
  }
}
