// THIS IS Server.kt - in ktor server module of kmp compose app

package kz.aita.server

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.UnauthorizedResponse
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.http.content.staticFiles
import io.ktor.server.netty.*
import io.ktor.server.plugins.autohead.*
import io.ktor.server.plugins.cachingheaders.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.conditionalheaders.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kz.aita.GenericGoodsCategoryDataModel
import kz.aita.GenericGoodsItemDataModel
import kz.aita.GoodsBatchDataModel
import kz.aita.GoodsItemDataModel
import kz.aita.LocalizedStringDataModel
import kz.aita.StoreDataModel
import kz.aita.SupplierDataModel
import kz.aita.TokenPair
import kz.aita.UserAccountDataModel
import kz.aita.UserAccountUpdateDataModel
import kz.aita.UserAuthLogInDataModel
import kz.aita.UserAuthSignUpDataModel
import kz.aita.UserBalanceDataModel
import kz.aita.jsonBase
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.exists
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.json.contains
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import org.postgresql.util.PSQLException
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.UUID
import at.favre.lib.crypto.bcrypt.BCrypt
import io.ktor.server.auth.principal
import io.ktor.server.plugins.origin
import io.ktor.server.request.userAgent
import io.ktor.server.routing.RoutingCall
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kz.aita.ActivationHistoryEntryDataModel
import kz.aita.BalanceHistoryEntryDataModel
import kz.aita.CompanyFormDataModel
import kz.aita.GenericResponseDataModel
import kz.aita.GoodsBatchShelfQueueDataModel
import kz.aita.LocationDataModel
import kz.aita.PriceDataModel
import kz.aita.QuantityDataModel
import kz.aita.RemoteResponseDataModel
import kz.aita.StylizedDrawablePathsGroupDataModel
import kz.aita.SubscriptionDataModel
import kz.aita.WorkerPrivilegeModeDataModel
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.json.jsonb
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

const val serverFilesPath = "AITA/server"
const val configAppPath = "$serverFilesPath/config/app"

fun metaFrom(call: ApplicationCall): Map<String, String> = mapOf(
  "ip" to (call.request.header("X-Forwarded-For") ?: call.request.origin.remoteHost),
  "ua" to (call.request.userAgent() ?: "unknown")
)

suspend fun RoutingCall.genericResponseNoPayload(
  status: HttpStatusCode,
  message: List<LocalizedStringDataModel>? = null
) {

  respond(
    status = status,
    message = GenericResponseDataModel(
      message = message?.let { jsonBase.encodeToString(it) },
      payload = null,
      negative = !status.isSuccess()
    )
  )
}

suspend inline fun <reified T> RoutingCall.genericResponse(
  status: HttpStatusCode,
  payload: T?,
  message: List<LocalizedStringDataModel>? = null
) {

  respond(
    status = status,
    message = GenericResponseDataModel(
      message = message?.let { jsonBase.encodeToString(it) },
      payload = payload?.let { jsonBase.encodeToString(it) },
      negative = !status.isSuccess()
    )
  )
}

suspend fun RoutingCall.checkPrincipal(): UUID? {
  val principal = principal<JWTPrincipal>()

  if (principal == null) {
    respond(UnauthorizedResponse())
    return null
  }

  val userId = runCatching { UUID.fromString(principal.subject) }.getOrNull()

  if (userId == null) {
    respond(UnauthorizedResponse())
    return null
  }

  return userId
}

fun getResponses(): List<RemoteResponseDataModel> {
  return Json.decodeFromString(Files.readString(Path.of(configAppPath).resolve("responses.json")))
}

fun getResponse(id: String): RemoteResponseDataModel {
  return getResponses().find { it.id == id }!!
}


object Workers: Table("workers") {
  val id = uuid("id").uniqueIndex()
  val userId = uuid("user_id")
  val workerTypeId = uuid("type_id")
  val placeId = uuid("place_id")
  val privilegeModes = jsonb("privilege_modes", Json, ListSerializer(WorkerPrivilegeModeDataModel.serializer()))
  val phoneNumber = varchar("phone_number", 32).uniqueIndex()
  val email = varchar("phone_number", 255).uniqueIndex()
  val firstName = varchar("first_name", 255)
  val lastName = varchar("last_name", 255)
  val salary = text("salary").default("0")
  val salaryCurrencyCode = text("salary_currency_code")
  val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
  val isActive = bool("is_active").default(true)

  override val primaryKey = PrimaryKey(id)
}

object Users: Table("users") {
  val id = uuid("id").uniqueIndex()
  val phoneNumber = varchar("phone_number", 32).uniqueIndex()
  val email = varchar("email", 255).uniqueIndex()
  val firstName = varchar("first_name", 255)
  val lastName = varchar("last_name", 255)
  val countryLocale = varchar("country_locale", 64)
  val workerIds = text("worker_ids").nullable().default(null)
  val supplierIds = text("supplier_ids").nullable().default(null)
  val passwordHash = varchar("password_hash", 100) // BCrypt ~60 chars, give some headroom
  val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
  val isActive = bool("is_active").default(true)

  override val primaryKey = PrimaryKey(id)
}

object UserBalances: Table("user_balances") {
  val id = uuid("id").uniqueIndex()
  val userId = uuid("user_id").uniqueIndex()
  val value = text("value")
  val currencyCode = text("currency_code")

  val history = jsonb("history", Json, ListSerializer(BalanceHistoryEntryDataModel.serializer()))

  override val primaryKey = PrimaryKey(id)
}

object Suppliers: Table("suppliers") {

  val id = uuid("id").uniqueIndex()
  val userIds = text("user_ids").nullable().default(null)
  val typeIds = text("type_ids").nullable().default(null)
  val categoryIds = text("category_ids").nullable().default(null)

  val name = text("name")

  val phoneNumbers = text("phone_numbers").nullable().default(null)
  val emails = text("emails").nullable().default(null)

  val addedAt = timestamp("added_at").defaultExpression(CurrentTimestamp)
  val isActive = bool("is_active").default(true)

  override val primaryKey = PrimaryKey(id)
}

object StoreUsers: Table("store_users") {
  val storeId = uuid("store_id").references(Stores.id, onDelete = ReferenceOption.CASCADE)
  val userId = uuid("user_id").references(Users.id, onDelete = ReferenceOption.CASCADE)

  override val primaryKey = PrimaryKey(storeId, userId)
}

object StoreSubscriptions: Table("store_subscriptions") {
  val id = uuid("id").uniqueIndex()
  val storeId = uuid("store_id").uniqueIndex()
  val history = jsonb("history", Json, ListSerializer(SubscriptionDataModel.serializer()))

  override val primaryKey = PrimaryKey(id)
}

object Stores: Table("stores") {
  val id = uuid("id").uniqueIndex()
  val ownerUserIds = jsonb("owner_user_ids", Json, ListSerializer(String.serializer()))
  val storeTypeIds = jsonb("store_type_ids", Json, ListSerializer(String.serializer()))

  val name = jsonb("name", Json, ListSerializer(LocalizedStringDataModel.serializer()))
  val alias = jsonb("alias", Json, ListSerializer(LocalizedStringDataModel.serializer()))
  val description = jsonb("description", Json, ListSerializer(LocalizedStringDataModel.serializer()))
  val companyForms = jsonb("company_forms", Json, ListSerializer(CompanyFormDataModel.serializer()))

  val location = jsonb("location", Json, LocationDataModel.serializer())
  val phoneNumbers = jsonb("phone_numbers", Json, ListSerializer(String.serializer()))
  val emails = jsonb("emails", Json, ListSerializer(String.serializer()))
  val countryLocales = jsonb("country_locales", Json, ListSerializer(String.serializer()))

  val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)

  override val primaryKey = PrimaryKey(id)
}

object StoreActivationHistory: Table("store_activation_history") {
  val id = uuid("id").uniqueIndex()
  val storeId = uuid("store_id").uniqueIndex()
  val history = jsonb("history", Json, ListSerializer(ActivationHistoryEntryDataModel.serializer()))

  override val primaryKey = PrimaryKey(id)
}

object Stock: Table("stock") {
  val id = uuid("id").uniqueIndex()
  val userId = uuid("user_id")
  val storeId = uuid("store_id")

  val barcode = jsonb("barcode", Json, ListSerializer(String.serializer()))
  val name = jsonb("name", Json, ListSerializer(LocalizedStringDataModel.serializer()))

  val measurementUnitId = text("measurement_unit_id")

  val categoryIds = jsonb("category_ids", Json, ListSerializer(String.serializer()))

  val salePrices = jsonb("sale_prices", Json, ListSerializer(PriceDataModel.serializer()))
  val returnPrices = jsonb("return_prices", Json, ListSerializer(PriceDataModel.serializer()))
  val supplyPrices = jsonb("supply_prices", Json, ListSerializer(PriceDataModel.serializer()))

  val isQuickItem = bool("is_quick_item")

  val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
  val isActive = bool("is_active").default(true)

  override val primaryKey = PrimaryKey(id)
}

object StockBatches: Table("stock_batches") {
  val id = uuid("id").uniqueIndex()
  val goodsItemId = uuid("goods_item_id")

  val userId = uuid("user_id")
  val storeId = uuid("store_id")
  val supplierId = uuid("supplierId")

  val salePrice = jsonb("sale_price", Json, PriceDataModel.serializer())
  val returnPrice = jsonb("return_price", Json, PriceDataModel.serializer())
  val supplyPrice = jsonb("supply_price", Json, PriceDataModel.serializer())

  val quantity = jsonb("quantity", Json, QuantityDataModel.serializer())

  val supplyTime = timestamp("supply_time")
  val expirationTime = timestamp("expiration_time")
  val shelfQueue = jsonb("shelf_queue", Json, GoodsBatchShelfQueueDataModel.serializer())

  val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)

  val createdByUserId = uuid("created_by_user_id")

  val isActive = bool("is_active").default(true)

  override val primaryKey = PrimaryKey(id)
}

object RefreshSessions: Table("refresh_sessions") {
  val id = uuid("id")
  val userId = uuid("user_id").references(Users.id, onDelete = ReferenceOption.CASCADE)
  val tokenHash = char("token_hash", 64).index()  // hex(sha256) = 64 chars
  val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
  val expiresAt = timestamp("expires_at")
  val rotatedFrom = uuid("rotated_from").nullable()
  val revokedAt = timestamp("revoked_at").nullable()

  val meta = jsonb<Map<String, String>>(
    name = "meta",
    jsonConfig = Json,
    kSerializer = MapSerializer(String.serializer(), String.serializer())
  ).nullable()
  override val primaryKey = PrimaryKey(id)
}

object RealtimeUpdates: Table("realtime_updates") {

  val userId = uuid("userId").uniqueIndex()
  val updateIds = jsonb("updateIds", Json, ListSerializer(String.serializer()))

  override val primaryKey = PrimaryKey(userId)
}

object Manufacturers: Table("manufacturers") {

  val id = uuid("id").uniqueIndex()
  val userIds = text("user_ids").nullable().default(null)
  val typeIds = text("type_ids").nullable().default(null)
  val categoryIds = text("category_ids").nullable().default(null)

  val name = text("name")

  val phoneNumbers = text("phone_numbers").nullable().default(null)
  val emails = text("emails").nullable().default(null)

  val addedAt = timestamp("added_at").defaultExpression(CurrentTimestamp)
  val isActive = bool("is_active").default(true)

  override val primaryKey = PrimaryKey(id)
}

object GenericGoodsItems: Table("generic_goods_items") {
  val id = uuid("id").uniqueIndex()

  val barcode = jsonb("barcode", Json, ListSerializer(String.serializer()))

  val name = text("name")
  val typeIds = text("type_ids").nullable().default(null)

  val categoryIds = text("category_ids").nullable().default(null)

  val supplierIds = text("supplier_ids").nullable().default(null)

  val manufacturerIds = text("manufacturer_ids").nullable().default(null)

  override val primaryKey = PrimaryKey(id)
}

object GenericGoodsCategories: Table("generic_goods_categories") {
  val id = uuid("id").uniqueIndex()

  val typeIds = jsonb("type_ids", Json, ListSerializer(String.serializer()))
  val name = jsonb("name", Json, ListSerializer(LocalizedStringDataModel.serializer()))
  val quantityUnitId = text("quantity_unit_id")
  val imagePaths = jsonb("image_paths", Json, ListSerializer(StylizedDrawablePathsGroupDataModel.serializer()))

  override val primaryKey = PrimaryKey(id)
}

object Pw {
  private val bcrypt = BCrypt.withDefaults()
  private val verifyer = BCrypt.verifyer()

  fun hash(password: CharArray): String =
    bcrypt.hashToString(14, password).also {
      Arrays.fill(password, '\u0000')
    }         // Cost 12; raise to 13–14 for extra security

  fun verify(password: CharArray, hash: String): Boolean =
    verifyer.verify(password, hash).verified.also {
      Arrays.fill(password, '\u0000')
    }
}

object Refresh {
  private val rng = SecureRandom()

  private val b64url = Base64.getUrlEncoder().withoutPadding()
  private val b64urlDec = Base64.getUrlDecoder()

  private val HEX = "0123456789abcdef".toCharArray()

  private val hmacKey: SecretKeySpec? = System.getenv("AITA_REFRESH_PEPPER")
    ?.takeIf { it.isNotBlank() }
    ?.let { SecretKeySpec(it.toByteArray(StandardCharsets.UTF_8), "HmacSHA256") }

  fun newPlainToken(): String {
    val buf = ByteArray(32)
    rng.nextBytes(buf)
    return b64url.encodeToString(buf)
  }

  fun hash(token: String): String {
    val key = requireNotNull(hmacKey) { "Set AITA_REFRESH_PEPPER to use HMAC hashing" }
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(key)
    val msg = try { b64urlDec.decode(token) } catch (_: Exception) {
      token.toByteArray(StandardCharsets.UTF_8)
    }
    return mac.doFinal(msg).toHexLower()
  }

  fun ByteArray.toHexLower(): String {
    val out = CharArray(size * 2)
    var i = 0
    for (b in this) {
      val v = b.toInt() and 0xFF
      out[i++] = HEX[v ushr 4]
      out[i++] = HEX[v and 0x0F]
    }
    return String(out)
  }
}

@kotlinx.serialization.Serializable
data class JwtConfig(
  val issuer: String,
  val audience: String,
  val realm: String,
  val secret: String,
  val accessTTL: Long,
  val refreshTTL: Long
)

fun Application.jwtConfig(): JwtConfig {
  val c = environment.config.config("ktor.security.jwt")
  return JwtConfig(
    issuer = c.property("issuer").getString(),
    audience = c.property("audience").getString(),
    realm = c.property("realm").getString(),
    secret = c.property("secret").getString(),
    accessTTL = c.property("accessTTL").getString().toLong(),
    refreshTTL = c.property("refreshTTL").getString().toLong()
  )
}

fun Application.configureJwtAuth() {
  val cfg = jwtConfig()

  install(Authentication) {
    jwt("auth-jwt") {                                 // Named auth provider
      realm = cfg.realm

      verifier(                                       // Defines how to verify incoming JWTs
        JWT.require(Algorithm.HMAC256(cfg.secret))    // HS256 with our secret
          .withIssuer(cfg.issuer)                     // Must match issuer
          .withAudience(cfg.audience)                 // Must match audience
          .build()
      )

      validate { cred ->
        val sessionId = runCatching { UUID.fromString(cred.payload.getClaim("sessionId").asString()) }.getOrNull()
          ?: return@validate null

        val ok = newSuspendedTransaction(Dispatchers.IO) {
          val row = RefreshSessions
            .selectAll()
            .where { RefreshSessions.id eq sessionId }
            .limit(1)
            .singleOrNull()

          row != null && row[RefreshSessions.revokedAt] == null && row[RefreshSessions.expiresAt].isAfter(Instant.now()) && cred.payload.issuer == cfg.issuer && cred.payload.audience.contains(
            cfg.audience
          ) && cred.subject != null
        }

        if (ok) JWTPrincipal(cred.payload) else null
      }

      challenge { _, _ ->
        call.respond(UnauthorizedResponse())
      }
    }
  }
}

class TokenService(private val cfg: JwtConfig) {

  private val algorithm = Algorithm
    .HMAC256(cfg.secret)

  fun signAccess(userId: UUID, sessionId: UUID, instant: Instant): String {
    val exp = instant.plusMillis(cfg.accessTTL)
    return JWT.create()
      .withIssuer(cfg.issuer)
      .withAudience(cfg.audience)
      .withSubject(userId.toString())
      .withIssuedAt(Date.from(instant))
      .withExpiresAt(Date.from(exp))
      .withClaim("sessionId", sessionId.toString())
      .sign(algorithm)
  }

  suspend fun newPair(userId: UUID, metaParam: Map<String, String>?): TokenPair = coroutineScope {
    val refreshPlain = Refresh.newPlainToken()
    val refreshHash = Refresh.hash(refreshPlain)
    val now = Instant.now()
    val expires = now.plus(cfg.refreshTTL, ChronoUnit.MILLIS)
    val sessionId = UUID.randomUUID()

    val signAccessAsync = async(Dispatchers.Default) {
      signAccess(userId, sessionId, now)
    }

    newSuspendedTransaction(Dispatchers.IO) {
      RefreshSessions.insert {
        it[id] = sessionId
        it[RefreshSessions.userId] = userId
        it[tokenHash] = refreshHash
        it[createdAt] = now
        it[expiresAt] = expires
        it[meta] = metaParam
      }
    }

    TokenPair(signAccessAsync.await(), cfg.accessTTL, refreshPlain, cfg.refreshTTL)
  }

  suspend fun rotate(refreshPlain: String, metaParam: Map<String, String>?): TokenPair = coroutineScope {
    val hash = Refresh.hash(refreshPlain)

    val oldSession = newSuspendedTransaction(Dispatchers.IO) {
      RefreshSessions.selectAll().where { RefreshSessions.tokenHash eq hash and RefreshSessions.revokedAt.isNull() }
        .forUpdate().singleOrNull() ?: throw IllegalAccessException("No valid previous refresh token")
    }

    val now = Instant.now()

    if (oldSession[RefreshSessions.expiresAt].isBefore(now))
      throw IllegalAccessException()

    // Revoke old session (so it cannot be used again)

    // Create a fresh session (rotation)
    val newPlain = Refresh.newPlainToken()
    val newHash = Refresh.hash(newPlain)
    val nowMillis = now.toEpochMilli()
    val expires = now.plus(cfg.refreshTTL, ChronoUnit.MILLIS)

    val sessionId = UUID.randomUUID()

    val signAccessAsync = async(Dispatchers.Default) {
      signAccess(oldSession[RefreshSessions.userId], sessionId, now)
    }

    newSuspendedTransaction(Dispatchers.IO) {
      RefreshSessions.update({ RefreshSessions.id eq oldSession[RefreshSessions.id] }) {
        it[revokedAt] = now
      }
    }

    newSuspendedTransaction(Dispatchers.IO) {
      RefreshSessions.insert {
        it[id] = sessionId
        it[userId] = oldSession[RefreshSessions.userId]
        it[tokenHash] = newHash
        it[createdAt] = now
        it[expiresAt] = expires
        it[rotatedFrom] = oldSession[RefreshSessions.id]
        it[meta] = metaParam
      }
    }

    TokenPair(
      signAccessAsync.await(),
      nowMillis + cfg.accessTTL,
      newPlain,
      nowMillis + cfg.refreshTTL
    )
  }

  suspend fun revoke(refreshPlain: String) = newSuspendedTransaction(Dispatchers.IO) {
    val hash = Refresh.hash(refreshPlain)
    RefreshSessions.update({ (RefreshSessions.tokenHash eq hash) and RefreshSessions.revokedAt.isNull() }) {
      it[revokedAt] = Instant.now()
    }
  }
}

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

  val tokenService = TokenService(jwtConfig())

  routing {
    route("/auth") {
      post("/signUp") {
        try {
          val body = call.receive<UserAuthSignUpDataModel>()
          val phoneNumber = body.phoneNumber.trim().lowercase()
          val email = body.email.trim().lowercase()

          val conflictResult = newSuspendedTransaction(Dispatchers.IO) {
            val userWithPhoneNumberExists = Users
              .select(Users.phoneNumber)
              .where { Users.phoneNumber eq phoneNumber }
              .empty()
              .not()

            val userWithEmailExists = Users
              .select(Users.email)
              .where { Users.email eq email }
              .empty()
              .not()

            if (userWithPhoneNumberExists && userWithEmailExists)
              return@newSuspendedTransaction 1
            else if (userWithPhoneNumberExists)
              return@newSuspendedTransaction 2
            else if (userWithEmailExists)
              return@newSuspendedTransaction 3

            return@newSuspendedTransaction 0
          }

          when (conflictResult) {
            1 -> {
              return@post call.genericResponseNoPayload(
                HttpStatusCode.Conflict,
                message = getResponse("2").message
              )
            }

            2 -> {
              return@post call.genericResponseNoPayload(
                HttpStatusCode.Conflict,
                message = getResponse("0").message
              )
            }

            3 -> {
              return@post call.genericResponseNoPayload(
                HttpStatusCode.Conflict,
                message = getResponse("1").message
              )
            }
          }

          val firstName = body.firstName.trim()
          val lastName = body.lastName.trim()
          val countryLocale = body.countryLocale.trim().lowercase()

          var id = UUID.randomUUID()

          val hash = Pw.hash(body.password.toCharArray())
          val instant = Instant.now()

          var state23505Reached: Boolean

          do {
            state23505Reached = try {
              id = UUID.randomUUID()

              newSuspendedTransaction(Dispatchers.IO) {
                Users.insert {
                  it[Users.id] = id
                  it[Users.phoneNumber] = phoneNumber
                  it[Users.email] = email
                  it[Users.firstName] = firstName
                  it[Users.lastName] = lastName
                  it[Users.countryLocale] = countryLocale
                  it[Users.workerIds] = null
                  it[Users.supplierIds] = null
                  it[Users.passwordHash] = hash
                  it[Users.createdAt] = instant
                  it[Users.isActive] = true
                }

                false
              }
            } catch (exception: ExposedSQLException) {
              val constraint = (exception.cause as? PSQLException)?.serverErrorMessage?.constraint
              val isPkCollision = exception.sqlState == "23505" && constraint?.equals("users_pkey", true) == true

              isPkCollision
            }
          } while (state23505Reached)

          id?.run {
            val tokenPair: TokenPair = tokenService.newPair(this, metaFrom(call))

            call.genericResponse(
              status = HttpStatusCode.Created,
              tokenPair
            )
          } ?: call.genericResponseNoPayload(
            status = HttpStatusCode.InternalServerError,
            message = getResponse("3").message
          )
        } catch (throwable: Throwable) {
          call.genericResponseNoPayload(
            status = HttpStatusCode.InternalServerError,
            message = getResponse("3").message
          )
          throwable.printStackTrace()
        }
      }

      post("/logIn") {
        val body = call.receive<UserAuthLogInDataModel>()

        val login = body.login.trim().lowercase()

        val user = newSuspendedTransaction(Dispatchers.IO) {
          Users.selectAll().where { (Users.phoneNumber eq login) or (Users.email eq login) }.singleOrNull()
        } ?: return@post call.respond(UnauthorizedResponse())

        val ok = Pw.verify(body.password.toCharArray(), user[Users.passwordHash])

        if (!ok)
          return@post call.respond(UnauthorizedResponse())

        val tokenPair: TokenPair = tokenService.newPair(user[Users.id], metaFrom(call))

        call.genericResponse<TokenPair>(HttpStatusCode.OK, tokenPair)
      }

      delete("/logOut") {
        val body = call.receive<String>()

        try {
          tokenService.revoke(body)
        } catch (throwable: Throwable) {
          throwable.printStackTrace()
        }

        call.genericResponseNoPayload(HttpStatusCode.OK, message = getResponse("8").message)
      }

      post("/refresh") {
        val body = call.receive<String>()
        try {
          val newTokens = tokenService.rotate(body, metaFrom(call))
          call.genericResponse(HttpStatusCode.OK, newTokens)
        } catch (throwable: Throwable) {
          call.respond(UnauthorizedResponse())
          throwable.printStackTrace()
        }
      }
    }

    route("/generic") {
      authenticate("auth-jwt") {
        route("/goodsCategories") {
          get("/get") {
            val userId = call.checkPrincipal() ?: return@get

            val genericGoodsCategories: Pair<Int, List<GenericGoodsCategoryDataModel>?> =
              newSuspendedTransaction(Dispatchers.IO) {
                val noUser = Users.select(Users.id).where { Users.id eq userId }.empty()

                if (noUser)
                  return@newSuspendedTransaction 1 to null

                val matches = GenericGoodsCategories
                  .selectAll()
                  .map {
                    GenericGoodsCategoryDataModel(
                      id = it[GenericGoodsCategories.id].toString(),
                      typeIds = it[GenericGoodsCategories.typeIds],
                      name = it[GenericGoodsCategories.name],
                      quantityUnitId = it[GenericGoodsCategories.quantityUnitId],
                      imagePaths = it[GenericGoodsCategories.imagePaths]
                    )
                  }

                0 to matches
              }

            when {
              genericGoodsCategories.first == 1 -> call.respond(UnauthorizedResponse())
              genericGoodsCategories.second?.isNotEmpty() == true -> {
                call.genericResponse(
                  HttpStatusCode.OK,
                  payload = genericGoodsCategories.second
                )
              }

              else -> {
                call.genericResponseNoPayload(HttpStatusCode.InternalServerError, message = getResponse("3").message)
              }
            }
          }
        }
      }
    }

    route("/generic") {
      authenticate("auth-jwt") {
        route("/goodsItems") {
          get("/get") {
            val userId = call.checkPrincipal() ?: return@get

            val barcode = call.request.header("barcode")

            val genericGoodsItems: Pair<Int, List<GenericGoodsItemDataModel>?> =
              newSuspendedTransaction(Dispatchers.IO) {
                val noUser = Users.select(Users.id).where { Users.id eq userId }.empty()

                if (noUser)
                  return@newSuspendedTransaction 1 to null

                val matches = GenericGoodsItems
                  .selectAll()
                  .where { GenericGoodsItems.barcode.contains(listOf(barcode)) }
                  .map {

                    GenericGoodsItemDataModel(
                      id = it[GenericGoodsItems.id].toString(),
                      barcode = it[GenericGoodsItems.barcode],
                      name = it[GenericGoodsItems.name].let { value ->
                        jsonBase.decodeFromString<List<LocalizedStringDataModel>>(
                          value
                        )
                      },
                      typeIds = it[GenericGoodsItems.typeIds]?.let { value ->
                        jsonBase.decodeFromString<List<String>>(
                          value
                        )
                      },
                      categoryIds = it[GenericGoodsItems.categoryIds]?.let { value ->
                        jsonBase.decodeFromString<List<String>>(
                          value
                        )
                      },
                      supplierIds = it[GenericGoodsItems.supplierIds]?.let { value ->
                        jsonBase.decodeFromString<List<String>>(
                          value
                        )
                      },
                      manufacturerIds = it[GenericGoodsItems.manufacturerIds]?.let { value ->
                        jsonBase.decodeFromString<List<String>>(
                          value
                        )
                      },
                    )
                  }

                0 to matches
              }

            when {
              genericGoodsItems.first == 1 -> call.respond(UnauthorizedResponse())
              genericGoodsItems.second?.isNotEmpty() == true -> {
                call.genericResponse(
                  HttpStatusCode.OK,
                  payload = genericGoodsItems.second
                )
              }

              else -> {
                call.genericResponseNoPayload(HttpStatusCode.NotFound, message = getResponse("13").message)
              }
            }
          }
        }
      }
    }

    staticFiles("config/global", File("AITA/server/config/app/global.json"))

    staticFiles("res/string", File("AITA/server/assets/values/strings.json"))
    staticFiles("res/dimension", File("AITA/server/assets/values/dimensions.json"))
    staticFiles("res/color", File("AITA/server/assets/values/colors.json"))
    staticFiles("res/drawableConfig", File("AITA/server/assets/drawable/drawables.json"))
    staticFiles("res/drawable", File("AITA/server/assets/drawable"))

    route("/stockBatches") {
      authenticate("auth-jwt") {
        get("/get") {
          val userId = call.checkPrincipal() ?: return@get

          val batches = newSuspendedTransaction(Dispatchers.IO) {
            val noUser = Users
              .select(Users.id)
              .where { Users.id eq userId }
              .empty()

            if (noUser)
              call.respond(UnauthorizedResponse())

            val storeId = UUID.fromString(call.request.headers["store_id"])

            StockBatches
              .selectAll()
              .where { (StockBatches.userId eq userId) and (StockBatches.storeId eq storeId) }
              .map {
                GoodsBatchDataModel(
                  id = it[StockBatches.id].toString(),
                  goodsItemId = it[StockBatches.goodsItemId].toString(),

                  userId = it[StockBatches.userId].toString(),
                  storeId = it[StockBatches.storeId].toString(),
                  supplierId = it[StockBatches.storeId].toString(),

                  salePrice = it[StockBatches.salePrice],
                  returnPrice = it[StockBatches.returnPrice],
                  supplyPrice = it[StockBatches.supplyPrice],

                  quantity = it[StockBatches.quantity],

                  supplyTime = it[StockBatches.supplyTime].toEpochMilli(),
                  expirationTime = it[StockBatches.expirationTime].toEpochMilli(),

                  shelfQueue = it[StockBatches.shelfQueue],
                  createdAt = it[StockBatches.createdAt].toEpochMilli(),
                  createdByUserId = it[StockBatches.createdByUserId].toString(),
                  isActive = it[StockBatches.isActive]
                )
              }
          }

          call.genericResponse(
            HttpStatusCode.OK,
            batches
          )
        }

        post("/add") {
          val userId = call.checkPrincipal() ?: return@post

          val noUser = newSuspendedTransaction(Dispatchers.IO) {
            Users
              .select(Users.id)
              .where {
                Users.id eq userId
              }
              .empty()
          }

          if (noUser) return@post call.respond(UnauthorizedResponse())

          val body = call.receive<GoodsBatchDataModel>()

          var state23505Reached: Boolean

          var id: UUID? = null
          var instant = Instant.now()

          do {
            state23505Reached = try {
              id = UUID.randomUUID()
              instant = Instant.now()

              newSuspendedTransaction(Dispatchers.IO) {
                StockBatches.insert {
                  it[StockBatches.id] = id
                  it[StockBatches.goodsItemId] = UUID.fromString(body.goodsItemId)

                  it[StockBatches.userId] = userId
                  it[StockBatches.storeId] = UUID.fromString(body.storeId)
                  it[StockBatches.supplierId] = UUID.fromString(body.supplierId)

                  it[StockBatches.salePrice] = body.salePrice
                  it[StockBatches.returnPrice] = body.returnPrice
                  it[StockBatches.supplyPrice] = body.supplyPrice
                  it[StockBatches.quantity] = body.quantity

                  it[StockBatches.supplyTime] = Instant.ofEpochMilli(body.supplyTime)
                  it[StockBatches.expirationTime] = Instant.ofEpochMilli(body.expirationTime)

                  it[StockBatches.shelfQueue] = body.shelfQueue

                  it[StockBatches.createdAt] = Instant.now()
                  it[StockBatches.createdByUserId] = userId

                  it[StockBatches.isActive] = body.isActive
                }
              }

              false
            } catch (exception: ExposedSQLException) {
              val constraint = (exception.cause as? PSQLException)?.serverErrorMessage?.constraint
              val isPkCollision = exception.sqlState == "23505" && constraint?.equals("stock_pkey", true) == true

              isPkCollision
            }
          } while (state23505Reached)

          id?.run {
            call.genericResponse(
              HttpStatusCode.Created,
              payload = body.copy(id = id.toString(), createdAt = instant.toEpochMilli()),
              message = getResponse("17").message
            )
          } ?: call.genericResponseNoPayload(
            status = HttpStatusCode.InternalServerError,
            message = getResponse("3").message
          )
        }

        put("/update") {
          val userId = call.checkPrincipal() ?: return@put

          val noUser = newSuspendedTransaction(Dispatchers.IO) {
            Users
              .select(Users.id)
              .where {
                Users.id eq userId
              }
              .empty()
          }

          if (noUser) return@put call.respond(UnauthorizedResponse())

          val body = call.receive<GoodsBatchDataModel>()
          val id = UUID.fromString(body.id)

          val doesntExist = newSuspendedTransaction(Dispatchers.IO) {
            Stock
              .select(StockBatches.id)
              .where {
                StockBatches.id eq id
              }
              .empty()
          }

          if (doesntExist) return@put call.respond(UnauthorizedResponse())

          val storeId = UUID.fromString(body.storeId)

          val updated = newSuspendedTransaction(Dispatchers.IO) {

            val id = runCatching { UUID.fromString(body.id) }.getOrNull() ?: return@newSuspendedTransaction 2

            StockBatches.update({
              (StockBatches.id eq id) and (StockBatches.userId eq userId) and (StockBatches.storeId eq storeId)
            }) {
              it[StockBatches.userId] = userId
              it[StockBatches.storeId] = UUID.fromString(body.storeId)
              it[StockBatches.supplierId] = UUID.fromString(body.supplierId)

              it[StockBatches.salePrice] = body.salePrice
              it[StockBatches.returnPrice] = body.returnPrice
              it[StockBatches.supplyPrice] = body.supplyPrice
              it[StockBatches.quantity] = body.quantity

              it[StockBatches.supplyTime] = Instant.ofEpochMilli(body.supplyTime)
              it[StockBatches.expirationTime] = Instant.ofEpochMilli(body.expirationTime)

              it[StockBatches.shelfQueue] = body.shelfQueue

              it[StockBatches.isActive] = body.isActive
            }.run {
              if (this > 0)
                0
              else
                1
            }
          }

          return@put when (updated) {
            0 -> call.genericResponse(
              HttpStatusCode.OK,
              payload = body,
              getResponse("18").message
            )

            1, 2 -> call.respond(UnauthorizedResponse())
            else -> call.genericResponseNoPayload(
              status = HttpStatusCode.InternalServerError,
              message = getResponse("3").message
            )
          }
        }

        delete("/delete") {
          val userId = call.checkPrincipal() ?: return@delete

          val body = call.receive<String>()
          val storeId = UUID.fromString(call.request.header("store_id"))
          val deleted = newSuspendedTransaction(Dispatchers.IO) {

            val id = runCatching { UUID.fromString(body) }.getOrNull() ?: return@newSuspendedTransaction 2

            if (StockBatches.deleteWhere { (StockBatches.id eq id) and (StockBatches.userId eq userId) and (StockBatches.storeId eq storeId) } > 0)
              0
            else
              1
          }

          return@delete when (deleted) {
            0 -> call.genericResponseNoPayload(
              HttpStatusCode.OK,
              message = getResponse("19").message
            )

            1, 2 -> call.respond(UnauthorizedResponse())
            else -> call.genericResponseNoPayload(
              status = HttpStatusCode.InternalServerError,
              message = getResponse("3").message
            )
          }
        }
      }
    }

    route("/stock") {
      authenticate("auth-jwt") {
        get("/get") {
          val userId = call.checkPrincipal() ?: return@get

          val goodsItems = newSuspendedTransaction(Dispatchers.IO) {
            val noUser = Users
              .select(Users.id)
              .where { Users.id eq userId }
              .empty()

            if (noUser)
              call.respond(UnauthorizedResponse())

            val storeId = UUID.fromString(call.request.headers["store_id"])

            Stock
              .selectAll()
              .where { (Stock.userId eq userId) and (Stock.storeId eq storeId) }
              .map {
                GoodsItemDataModel(
                  id = it[Stock.id].toString(),
                  userId = it[Stock.userId].toString(),
                  storeId = it[Stock.storeId].toString(),
                  barcode = it[Stock.barcode],
                  name = it[Stock.name],
                  measurementUnitId = it[Stock.measurementUnitId].toString(),
                  categoryIds = it[Stock.categoryIds],
                  salePrices = it[Stock.salePrices],
                  returnPrices = it[Stock.returnPrices],
                  supplyPrices = it[Stock.supplyPrices],
                  isQuickItem = it[Stock.isQuickItem],
                  createdAt = it[Stock.createdAt].toEpochMilli(),
                  isActive = it[Stock.isActive]
                )
              }
          }

          call.genericResponse(
            HttpStatusCode.OK,
            goodsItems
          )
        }

        post("/add") {
          val userId = call.checkPrincipal() ?: return@post

          val noUser = newSuspendedTransaction(Dispatchers.IO) {
            Users
              .select(Users.id)
              .where {
                Users.id eq userId
              }
              .empty()
          }

          if (noUser) return@post call.respond(UnauthorizedResponse())

          val body = call.receive<GoodsItemDataModel>()

          var state23505Reached: Boolean

          var id: UUID? = null
          var instant = Instant.now()

          do {
            state23505Reached = try {
              id = UUID.randomUUID()
              instant = Instant.now()

              newSuspendedTransaction(Dispatchers.IO) {
                Stock.insert {
                  it[Stock.id] = id
                  it[Stock.userId] = userId
                  it[Stock.storeId] = UUID.fromString(body.storeId)
                  it[Stock.name] = body.name
                  it[Stock.measurementUnitId] = body.measurementUnitId
                  it[Stock.barcode] = body.barcode
                  it[Stock.categoryIds] = body.categoryIds
                  it[Stock.salePrices] = body.salePrices
                  it[Stock.returnPrices] = body.returnPrices
                  it[Stock.supplyPrices] = body.supplyPrices
                  it[Stock.isQuickItem] = body.isQuickItem
                  it[Stock.createdAt] = instant
                  it[Stock.isActive] = body.isActive
                }
              }

              false
            } catch (exception: ExposedSQLException) {
              val constraint = (exception.cause as? PSQLException)?.serverErrorMessage?.constraint
              val isPkCollision = exception.sqlState == "23505" && constraint?.equals("stock_pkey", true) == true

              isPkCollision
            }
          } while (state23505Reached)

          id?.run {
            call.genericResponse(
              HttpStatusCode.Created,
              payload = body.copy(id = id.toString(), createdAt = instant.toEpochMilli()),
              message = getResponse("14").message
            )
          } ?: call.genericResponseNoPayload(
            status = HttpStatusCode.InternalServerError,
            message = getResponse("3").message
          )
        }

        put("/update") {
          val userId = call.checkPrincipal() ?: return@put

          val noUser = newSuspendedTransaction(Dispatchers.IO) {
            Users
              .select(Users.id)
              .where {
                Users.id eq userId
              }
              .empty()
          }

          if (noUser) return@put call.respond(UnauthorizedResponse())

          val body = call.receive<GoodsItemDataModel>()
          val id = UUID.fromString(body.id)

          val doesntExist = newSuspendedTransaction(Dispatchers.IO) {
            Stock
              .select(Stock.id)
              .where {
                Stock.id eq id
              }
              .empty()
          }

          if (doesntExist) return@put call.respond(UnauthorizedResponse())

          val storeId = UUID.fromString(body.storeId)

          val updated = newSuspendedTransaction(Dispatchers.IO) {

            val id = runCatching { UUID.fromString(body.id) }.getOrNull() ?: return@newSuspendedTransaction 2

            Stock.update({
              (Stock.id eq id) and (Stock.userId eq userId) and (Stock.storeId eq storeId)
            }) {
              it[Stock.id] = id
              it[Stock.userId] = userId
              it[Stock.storeId] = UUID.fromString(body.storeId)
              it[Stock.name] = body.name
              it[Stock.measurementUnitId] = body.measurementUnitId
              it[Stock.barcode] = body.barcode
              it[Stock.categoryIds] = body.categoryIds
              it[Stock.salePrices] = body.salePrices
              it[Stock.returnPrices] = body.returnPrices
              it[Stock.supplyPrices] = body.supplyPrices
              it[Stock.isQuickItem] = body.isQuickItem
            }.run {
              if (this > 0)
                0
              else
                1
            }
          }

          return@put when (updated) {
            0 -> call.genericResponse(
              HttpStatusCode.OK,
              payload = body,
              getResponse("15").message
            )

            1, 2 -> call.respond(UnauthorizedResponse())
            else -> call.genericResponseNoPayload(
              status = HttpStatusCode.InternalServerError,
              message = getResponse("3").message
            )
          }
        }

        delete("/delete") {
          val userId = call.checkPrincipal() ?: return@delete

          val noUser = newSuspendedTransaction(Dispatchers.IO) {
            Users
              .select(Users.id)
              .where {
                Users.id eq userId
              }
              .empty()
          }

          if (noUser) return@delete call.respond(UnauthorizedResponse())

          val body = call.receive<String>()
          val storeId = UUID.fromString(call.request.header("store_id"))
          val id = runCatching { UUID.fromString(body) }.getOrNull()

          if (id == null) {
            call.genericResponseNoPayload(
              status = HttpStatusCode.InternalServerError,
              message = getResponse("3").message
            )

            return@delete
          }

          val deleted = newSuspendedTransaction(Dispatchers.IO) {
            if (Stock.deleteWhere { (Stock.id eq id) and (Stock.userId eq userId) and (Stock.storeId eq storeId) } > 0)
              0
            else
              1
          }

          return@delete when (deleted) {
            0 -> call.genericResponse(
              HttpStatusCode.OK,
              message = getResponse("16").message,
              payload = id.toString()
            )

            1, 2 -> call.respond(UnauthorizedResponse())
            else -> call.genericResponseNoPayload(
              status = HttpStatusCode.InternalServerError,
              message = getResponse("3").message
            )
          }
        }
      }
    }

    route("/stores") {
      authenticate("auth-jwt") {
        get("/get") {
          val userId = call.checkPrincipal() ?: return@get

          val stores = newSuspendedTransaction(Dispatchers.IO) {
            val noUser = Users
              .select(Users.id)
              .where { Users.id eq userId }
              .empty()

            if (noUser)
              call.respond(UnauthorizedResponse())

            Stores
              .innerJoin(StoreUsers, { Stores.id }, { StoreUsers.storeId })
              .selectAll()
              .where { StoreUsers.userId eq userId }
              .map {
                StoreDataModel(
                  id = it[Stores.id].toString(),
                  userIds = it[Stores.ownerUserIds],
                  storeTypeIds = it[Stores.storeTypeIds],
                  name = it[Stores.name],
                  alias = it[Stores.alias],
                  description = it[Stores.description],
                  companyForms = it[Stores.companyForms],
                  location = it[Stores.location],
                  phoneNumbers = it[Stores.phoneNumbers],
                  emails = it[Stores.emails],
                  countryLocales = it[Stores.countryLocales],
                  createdAt = it[Stores.createdAt].toEpochMilli()
                )
              }
          }

          call.genericResponse(
            HttpStatusCode.OK,
            stores
          )
        }

        post("/add") {
          val userId = call.checkPrincipal() ?: return@post

          val noUser = newSuspendedTransaction(Dispatchers.IO) {
            Users
              .select(Users.id)
              .where {
                Users.id eq userId
              }
              .empty()
          }

          if (noUser) return@post call.respond(UnauthorizedResponse())

          val body = call.receive<StoreDataModel>()

          var state23505Reached: Boolean

          var id: UUID? = null
          var instant = Instant.now()

          do {
            state23505Reached = try {
              id = UUID.randomUUID()
              instant = Instant.now()

              newSuspendedTransaction(Dispatchers.IO) {
                Stores.insert {
                  it[Stores.id] = id
                  it[Stores.ownerUserIds] = listOf(userId.toString())
                  it[Stores.storeTypeIds] = body.storeTypeIds
                  it[Stores.name] = body.name
                  it[Stores.alias] = body.alias
                  it[Stores.description] = body.description
                  it[Stores.companyForms] = body.companyForms
                  it[Stores.location] = body.location
                  it[Stores.phoneNumbers] = body.phoneNumbers
                  it[Stores.emails] = body.emails
                  it[Stores.countryLocales] = body.countryLocales
                  it[Stores.createdAt] = instant
                }

                StoreUsers.insertIgnore {           // composite PK avoids dup (store_id,user_id)
                  it[StoreUsers.storeId] = id
                  it[StoreUsers.userId] = userId // from JWT principal
                }
              }

              false
            } catch (exception: ExposedSQLException) {
              val constraint = (exception.cause as? PSQLException)?.serverErrorMessage?.constraint
              val isPkCollision = exception.sqlState == "23505" && constraint?.equals("stores_pkey", true) == true

              isPkCollision
            }
          } while (state23505Reached)

          id?.run {
            call.genericResponse(
              HttpStatusCode.Created,
              payload = body.copy(id = id.toString(), createdAt = instant.toEpochMilli()),
              message = getResponse("10").message
            )
          } ?: call.genericResponseNoPayload(
            status = HttpStatusCode.InternalServerError,
            message = getResponse("3").message
          )
        }

        put("/update") {
          val userId = call.checkPrincipal() ?: return@put

          val body = call.receive<StoreDataModel>()

          val updated = newSuspendedTransaction(Dispatchers.IO) {

            val id = runCatching { UUID.fromString(body.id) }.getOrNull() ?: return@newSuspendedTransaction 2

            Stores.update({
              (Stores.id eq id) and exists(
                StoreUsers.selectAll().where { (StoreUsers.storeId eq id) and (StoreUsers.userId eq userId) })
            }) {
              it[Stores.storeTypeIds] = body.storeTypeIds
              it[Stores.name] = body.name
              it[Stores.alias] = body.alias
              it[Stores.description] = body.description
              it[Stores.companyForms] = body.companyForms
              it[Stores.location] = body.location
              it[Stores.phoneNumbers] = body.phoneNumbers
              it[Stores.emails] = body.emails
              it[Stores.countryLocales] = body.countryLocales
            }.run {
              if (this > 0)
                0
              else
                1
            }
          }

          return@put when (updated) {
            0 -> call.genericResponse(
              HttpStatusCode.OK,
              payload = body,
              getResponse("11").message
            )

            1, 2 -> call.respond(UnauthorizedResponse())
            else -> call.genericResponseNoPayload(
              status = HttpStatusCode.InternalServerError,
              message = getResponse("3").message
            )
          }
        }

        delete("/delete") {
          val userId = call.checkPrincipal() ?: return@delete

          val body = call.receive<String>()

          val deleted = newSuspendedTransaction(Dispatchers.IO) {

            val id = runCatching { UUID.fromString(body) }.getOrNull() ?: return@newSuspendedTransaction 2

            if (Stores.deleteWhere { (Stores.id eq id) and (Stores.ownerUserIds.contains(userId.toString())) } > 0)
              0
            else
              1
          }

          return@delete when (deleted) {
            0 -> call.genericResponseNoPayload(
              HttpStatusCode.OK,
              message = getResponse("12").message
            )

            1, 2 -> call.respond(UnauthorizedResponse())
            else -> call.genericResponseNoPayload(
              status = HttpStatusCode.InternalServerError,
              message = getResponse("3").message
            )
          }
        }
      }
    }

    route("/suppliers") {
      authenticate("auth-jwt") {
        get("/get") {
          val userId = call.checkPrincipal() ?: return@get

          val suppliers: Pair<Int, List<SupplierDataModel>?> =
            newSuspendedTransaction(Dispatchers.IO) {
              val noUser = Users.select(Users.id).where { Users.id eq userId }.empty()

              if (noUser)
                return@newSuspendedTransaction 1 to null

              val matches = Suppliers
                .selectAll()
                .map {
                  SupplierDataModel(
                    id = it[Suppliers.id].toString(),
                    typeIds = it[Suppliers.typeIds]?.let { value -> jsonBase.decodeFromString<List<String>?>(value) },
                    name = jsonBase.decodeFromString<List<LocalizedStringDataModel>>(it[Suppliers.name]),
                    phoneNumbers = it[Suppliers.phoneNumbers]?.let { value -> jsonBase.decodeFromString<List<String>?>(value) },
                    emails = it[Suppliers.emails]?.let { value -> jsonBase.decodeFromString<List<String>?>(value) },
                    addedAt = it[Suppliers.addedAt].toEpochMilli(),
                    isActive = it[Suppliers.isActive]
                  )
                }

              0 to matches
            }

          when {
            suppliers.first == 1 -> call.respond(UnauthorizedResponse())
            suppliers.second?.isNotEmpty() == true ->
              call.genericResponse(
                HttpStatusCode.OK,
                payload = suppliers.second
              )

            else -> {
              call.genericResponseNoPayload(HttpStatusCode.InternalServerError, message = getResponse("3").message)
            }
          }
        }
      }
    }

    route("/balance") {
      authenticate("auth-jwt") {
        get("/get") {
          val userId = call.checkPrincipal() ?: return@get

          val balance = newSuspendedTransaction(Dispatchers.IO) {
            val noUser = Users
              .select(Users.id)
              .where { Users.id eq userId }
              .empty()

            if (noUser)
              call.respond(UnauthorizedResponse())

            UserBalances
              .selectAll()
              .where { UserBalances.userId eq userId }
              .singleOrNull()
              ?.let {
                UserBalanceDataModel(
                  it[UserBalances.value],
                  it[UserBalances.currencyCode],
                  it[UserBalances.history]
                )
              }
          }

          balance?.let {
            call.genericResponse(
              HttpStatusCode.OK,
              balance
            )
          } ?: call.genericResponseNoPayload(
            HttpStatusCode.NotFound,
            getResponse("13").message
          )

        }

//        put("/topUp") {
//          val userId = call.checkPrincipal() ?: return@put
//
//          val body = call.receive<StoreDataModel>()
//
//          val updated = newSuspendedTransaction(Dispatchers.IO) {
//
//            val id = runCatching { UUID.fromString(body.id) }.getOrNull() ?: return@newSuspendedTransaction 2
//
//            Stores.update({
//              (Stores.id eq id) and exists(
//                StoreUsers.selectAll().where { (StoreUsers.storeId eq id) and (StoreUsers.userId eq userId) })
//            }) {
//              it[Stores.storeTypeIds] = body.storeTypeIds
//              it[Stores.name] = body.name
//              it[Stores.alias] = body.alias
//              it[Stores.description] = body.description
//              it[Stores.companyForms] = body.companyForms
//              it[Stores.location] = body.location
//              it[Stores.phoneNumbers] = body.phoneNumbers
//              it[Stores.emails] = body.emails
//              it[Stores.countryLocales] = body.countryLocales
//            }.run {
//              if (this > 0)
//                0
//              else
//                1
//            }
//          }
//
//          return@put when (updated) {
//            0 -> call.genericResponse(
//              HttpStatusCode.OK,
//              payload = body,
//              getResponse("11").message
//            )
//
//            1, 2 -> call.respond(UnauthorizedResponse())
//            else -> call.genericResponseNoPayload(
//              status = HttpStatusCode.InternalServerError,
//              message = getResponse("3").message
//            )
//          }
//        }
      }
    }

    route("/user") {
      authenticate("auth-jwt") {
        get("/get") {
          val uuid = call.checkPrincipal() ?: return@get

          val user = newSuspendedTransaction(Dispatchers.IO) {
            Users
              .selectAll()
              .where {
                Users.id eq uuid
              }
              .limit(1)
              .singleOrNull()
          } ?: return@get call.respond(UnauthorizedResponse())

          call.genericResponse(
            HttpStatusCode.OK,
            UserAccountDataModel(
              id = user[Users.id].toString(),
              phoneNumber = user[Users.phoneNumber],
              email = user[Users.email],
              firstName = user[Users.firstName],
              lastName = user[Users.lastName],
              countryLocale = user[Users.countryLocale],
              workerAccountIds = user[Users.workerIds],
              supplierAccountIds = user[Users.supplierIds],
              createdAt = user[Users.createdAt].toEpochMilli(),
              isActive = user[Users.isActive]
            )
          )
        }

        put("/update") {
          val uuid = call.checkPrincipal() ?: return@put

          val body = call.receive<UserAccountUpdateDataModel>()
          val newAccount = body.account

          val phoneNumber = newAccount.phoneNumber.trim().lowercase()
          val email = newAccount.email.trim().lowercase()
          val firstName = newAccount.firstName.trim()
          val lastName = newAccount.lastName.trim()
          val countryLocale = newAccount.countryLocale.trim().lowercase()
          val isActive = newAccount.isActive

          val updated = newSuspendedTransaction(Dispatchers.IO) {
            val existingUser =
              Users
                .selectAll()
                .where {
                  Users.id eq uuid
                }
                .forUpdate()
                .limit(1)
                .singleOrNull() ?: return@newSuspendedTransaction "unauthorized"

            if (!Pw.verify(body.password.toCharArray(), existingUser[Users.passwordHash]))
              return@newSuspendedTransaction "password_mismatch"

            val phoneNumberClash = Users
              .select(Users.id, Users.phoneNumber)
              .where {
                (Users.phoneNumber eq newAccount.phoneNumber) and (Users.id neq uuid)
              }
              .empty()
              .not()


            val emailClash = Users
              .select(Users.id, Users.email)
              .where {
                (Users.email eq newAccount.email) and (Users.id neq uuid)
              }
              .empty()
              .not()

            if (phoneNumberClash && emailClash)
              return@newSuspendedTransaction "phone_number_and_email_clash"
            else if (phoneNumberClash)
              return@newSuspendedTransaction "phone_number_clash"
            else if (emailClash)
              return@newSuspendedTransaction "email_clash"

            val newHash = body.newPassword
              ?.takeIf {
                it.isNotEmpty()
                    && it.isNotBlank()
                    && !Pw.verify(it.toCharArray(), existingUser[Users.passwordHash])
              }?.let {
                Pw.hash(it.toCharArray())
              }

            Users.update({ Users.id eq uuid }) {
              if (existingUser[Users.phoneNumber] != phoneNumber)
                it[Users.phoneNumber] = phoneNumber

              if (existingUser[Users.email] != email)
                it[Users.email] = email

              if (existingUser[Users.firstName] != firstName)
                it[Users.firstName] = firstName

              if (existingUser[Users.lastName] != lastName)
                it[Users.lastName] = lastName

              if (existingUser[Users.countryLocale] != countryLocale)
                it[Users.countryLocale] = countryLocale

              newHash?.run {
                it[Users.passwordHash] = this
              }

              if (existingUser[Users.isActive] != isActive)
                it[Users.isActive] = isActive
            }

            "ok"
          }

          when (updated) {
            "ok" -> call.genericResponse(
              HttpStatusCode.OK,
              payload = body.account,
              message = getResponse("9").message
            )

            "unauthorized", "password_mismatch" -> call.respond(UnauthorizedResponse())

            "phone_number_and_email_clash" -> call.genericResponseNoPayload(
              HttpStatusCode.Conflict,
              message = getResponse("2").message
            )

            "phone_number_clash" -> call.genericResponseNoPayload(
              HttpStatusCode.Conflict,
              message = getResponse("0").message
            )

            "email_clash" -> call.genericResponseNoPayload(
              HttpStatusCode.Conflict,
              message = getResponse("1").message
            )

            else -> call.genericResponseNoPayload(
              status = HttpStatusCode.InternalServerError,
              message = getResponse("3").message
            )
          }
        }
      }
    }
  }
}