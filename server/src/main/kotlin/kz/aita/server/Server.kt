// THIS IS Server.kt - in ktor server module of kmp compose app

package kz.aita.server

import at.favre.lib.crypto.bcrypt.BCrypt
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.config.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.autohead.*
import io.ktor.server.plugins.cachingheaders.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.conditionalheaders.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kz.aita.*
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNotNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.json.contains
import org.jetbrains.exposed.sql.json.jsonb
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.postgresql.util.PSQLException
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.seconds

object Transactions: Table("transactions") {
  val id = uuid("id").uniqueIndex()
  val userId = uuid("user_id")
  val workshiftId = long("workshift_id")
  val type = text("type")
  val storeId = uuid("store_id")

  val goodsInTransaction = jsonb(
    "goods_in_transaction",
    Json,
    ListSerializer(GoodsItemInTransactionDataModel.serializer())
  )

  val paidCash = double("paid_cash")
  val paidCard = double("paid_card")
  val cardPaymentOptionId = integer("card_payment_option_id")
  val debtor = text("debtor").nullable()
  val timeMillis = long("time_millis")
  val clientOperationId = text("client_operation_id").nullable().uniqueIndex()
  val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)

  override val primaryKey = PrimaryKey(id)
}

object TransactionReturnItems: Table("transaction_return_items") {
  val id = uuid("id").uniqueIndex()
  val transactionId = uuid("transaction_id").references(Transactions.id, onDelete = ReferenceOption.CASCADE)
  val userId = uuid("user_id")
  val storeId = uuid("store_id")
  val lineIndex = integer("line_index")
  val goodsItemId = uuid("goods_item_id").nullable()
  val barcode = text("barcode").default("")
  val name = jsonb(
    "name",
    Json,
    ListSerializer(LocalizedStringDataModel.serializer())
  )
  val quantity = double("quantity")
  val pricePerUnit = double("price_per_unit")
  val currencyCode = text("currency_code").nullable()
  val returnReason = text("return_reason").default("")
  val clientOperationId = text("client_operation_id").nullable()
  val timeMillis = long("time_millis")
  val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)

  override val primaryKey = PrimaryKey(id)
}


private fun hardDeleteStoreOwnedDataInsideTransaction(storeIds: List<UUID>) {
  val cleanStoreIds = storeIds.distinct()
  if (cleanStoreIds.isEmpty()) return

  val nullableStoreIds = cleanStoreIds.map { it as UUID? }

  SupplierOrders.deleteWhere { SupplierOrders.storeId inList cleanStoreIds }
  TransactionReturnItems.deleteWhere { TransactionReturnItems.storeId inList cleanStoreIds }
  Transactions.deleteWhere { Transactions.storeId inList cleanStoreIds }
  Debtors.deleteWhere { Debtors.storeId inList cleanStoreIds }
  Notifications.deleteWhere { Notifications.storeId inList nullableStoreIds }
  SupportTickets.update({ SupportTickets.storeId inList nullableStoreIds }) {
    it[SupportTickets.storeId] = null
  }
  StockItems.deleteWhere { StockItems.storeId inList cleanStoreIds }
}

private fun ResultRow.toTransactionDataModel(): TransactionDataModel = TransactionDataModel(
  id = this[Transactions.id].toString(),
  workshiftId = this[Transactions.workshiftId],
  type = this[Transactions.type],
  storeId = this[Transactions.storeId].toString(),
  goodsInTransaction = this[Transactions.goodsInTransaction],
  paidCash = this[Transactions.paidCash],
  paidCard = this[Transactions.paidCard],
  cardPaymentOptionId = this[Transactions.cardPaymentOptionId],
  debtor = this[Transactions.debtor]?.let { raw -> jsonBase.decodeFromString<DebtorDataModel>(raw) },
  timeMillis = this[Transactions.timeMillis],
  clientOperationId = this[Transactions.clientOperationId].orEmpty()
)

object Debtors: Table("debtors") {
  val id = uuid("id").uniqueIndex()
  val userId = uuid("user_id")
  val storeId = uuid("store_id")
  val email = text("email").default("")
  val debtAmount = double("debt_amount")
  val currency = text("currency")
  val phoneNumber = text("phone_number").default("")
  val firstName = text("first_name")
  val lastName = text("last_name")
  val debtorType = text("debtor_type").default("individual")
  val idNumber = text("id_number").default("")
  val companyName = text("company_name").default("")
  val companyIdNumber = text("company_id_number").default("")
  val debtCreatedAtMillis = long("debt_created_at_millis").default(0L)
  val debtDueAtMillis = long("debt_due_at_millis").nullable()
  val originalDebtAmount = double("original_debt_amount").nullable()
  val interest = jsonb("interest", Json, DebtInterestDataModel.serializer()).nullable()
  val plannedPayments = jsonb("planned_payments", Json, ListSerializer(DebtPartialPaymentPlanDataModel.serializer())).default(emptyList())
  val paymentHistory = jsonb("payment_history", Json, ListSerializer(DebtPaymentRecordDataModel.serializer())).default(emptyList())
  val transactionIds = jsonb("transaction_ids", Json, ListSerializer(String.serializer()))
  val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
  val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)
  val isActive = bool("is_active").default(true)

  override val primaryKey = PrimaryKey(id)
}


private const val DEFAULT_SERVER_FILES_PATH = "AITA/server"
private const val LOCAL_ENVIRONMENT_NAME = "local"

private fun envOrSystem(name: String): String? =
  System.getenv(name)?.trim()?.takeIf { it.isNotEmpty() }
    ?: System.getProperty(name)?.trim()?.takeIf { it.isNotEmpty() }

private fun String.toBooleanLenientOrNull(): Boolean? =
  when (trim().lowercase(Locale.ROOT)) {
    "true", "1", "yes", "y", "on" -> true
    "false", "0", "no", "n", "off" -> false
    else -> null
  }

private fun runtimeEnvironmentName(): String =
  envOrSystem("AITA_ENV")?.lowercase(Locale.ROOT) ?: LOCAL_ENVIRONMENT_NAME

private fun isProductionRuntime(): Boolean =
  runtimeEnvironmentName() in setOf("prod", "production", "stage", "staging", "cloud")

private fun Path.normalizedAbsolute(): Path = toAbsolutePath().normalize()

private fun resolveServerFilesRootPath(): Path {
  envOrSystem("AITA_SERVER_FILES_ROOT")?.let { return Path.of(it).normalizedAbsolute() }

  val projectRootStyle = Path.of(DEFAULT_SERVER_FILES_PATH)
  if (Files.exists(projectRootStyle.resolve("assets")) || Files.exists(projectRootStyle.resolve("config/app"))) {
    return projectRootStyle.normalizedAbsolute()
  }

  val moduleRootStyle = Path.of(".")
  if (Files.exists(moduleRootStyle.resolve("assets")) || Files.exists(moduleRootStyle.resolve("config/app"))) {
    return moduleRootStyle.normalizedAbsolute()
  }

  val nestedServerStyle = Path.of("server")
  if (Files.exists(nestedServerStyle.resolve("assets")) || Files.exists(nestedServerStyle.resolve("config/app"))) {
    return nestedServerStyle.normalizedAbsolute()
  }

  return projectRootStyle.normalizedAbsolute()
}

private val serverFilesRootPath: Path by lazy(::resolveServerFilesRootPath)

private val configAppRootPath: Path by lazy {
  envOrSystem("AITA_CONFIG_APP_ROOT")
    ?.let { Path.of(it).normalizedAbsolute() }
    ?: serverFilesRootPath.resolve("config/app").normalizedAbsolute()
}

private val assetsRootPath: Path by lazy {
  envOrSystem("AITA_ASSETS_ROOT")
    ?.let { Path.of(it).normalizedAbsolute() }
    ?: serverFilesRootPath.resolve("assets").normalizedAbsolute()
}

private fun ApplicationConfig.optionalString(path: String): String? =
  runCatching { propertyOrNull(path)?.getString()?.trim()?.takeIf { it.isNotEmpty() } }.getOrNull()

private fun Application.configString(path: String, envName: String, default: String): String =
  environment.config.optionalString(path) ?: envOrSystem(envName) ?: default

private fun Application.configBoolean(path: String, envName: String, default: Boolean): Boolean {
  val raw = environment.config.optionalString(path) ?: envOrSystem(envName)
  return raw?.toBooleanLenientOrNull() ?: default
}

private fun Application.configInt(path: String, envName: String, default: Int): Int {
  val raw = environment.config.optionalString(path) ?: envOrSystem(envName)
  return raw?.toIntOrNull() ?: default
}

private fun Application.configLong(path: String, envName: String, default: Long): Long {
  val raw = environment.config.optionalString(path) ?: envOrSystem(envName)
  return raw?.toLongOrNull() ?: default
}

private fun Application.isProductionMode(): Boolean {
  val configured = environment.config.optionalString("app.environment") ?: envOrSystem("AITA_ENV") ?: LOCAL_ENVIRONMENT_NAME
  return configured.lowercase(Locale.ROOT) in setOf("prod", "production", "stage", "staging", "cloud")
}

private data class AllowedCorsOrigin(
  val host: String,
  val scheme: String
)

private fun parseAllowedCorsOrigins(raw: String): List<AllowedCorsOrigin> {
  return raw
    .split(',')
    .map { it.trim().trimEnd('/') }
    .filter { it.isNotBlank() }
    .mapNotNull { origin ->
      if (origin == "*") return@mapNotNull AllowedCorsOrigin("*", "*")

      val candidate = if (origin.contains("://")) origin else "https://$origin"
      val uri = runCatching { URI(candidate) }.getOrNull() ?: return@mapNotNull null
      val scheme = uri.scheme?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() } ?: "https"
      val host = uri.host?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
      val hostWithPort = if (uri.port >= 0) "$host:${uri.port}" else host
      AllowedCorsOrigin(hostWithPort, scheme)
    }
}

private fun Application.publicServerUrl(): String? =
  (environment.config.optionalString("app.publicServerUrl")
    ?: envOrSystem("AITA_PUBLIC_SERVER_URL")
    ?: envOrSystem("AITA_PUBLIC_BASE_URL"))
    ?.trimEnd('/')
    ?.takeIf { it.isNotBlank() }

private fun Application.buildGlobalConfigurationJson(): String {
  val globalConfigPath = configAppRootPath.resolve("global.json")
  val raw = Files.readString(globalConfigPath)
  val publicUrl = publicServerUrl() ?: return raw

  return runCatching {
    val root = Json.parseToJsonElement(raw).jsonObject
    val payload = root["payload"]?.jsonObject ?: return@runCatching raw
    val serverUrl = payload["serverUrl"]?.jsonObject ?: return@runCatching raw

    val updatedServerUrl = JsonObject(serverUrl + ("first" to JsonPrimitive(publicUrl)))
    val updatedPayload = JsonObject(payload + ("serverUrl" to updatedServerUrl))
    val updatedRoot = JsonObject(root + ("payload" to updatedPayload))
    updatedRoot.toString()
  }.getOrElse { throwable ->
    environment.log.error("Failed to apply configured public server URL to global configuration", throwable)
    raw
  }
}

private suspend fun ApplicationCall.respondStaticJsonFile(path: Path) {
  val normalizedPath = path.normalizedAbsolute()
  if (!Files.isRegularFile(normalizedPath)) {
    application.environment.log.error("Missing configured static file: $normalizedPath")
    respond(HttpStatusCode.NotFound)
    return
  }
  respondFile(normalizedPath.toFile())
}

fun metaFrom(call: ApplicationCall, deviceInfo: ClientDeviceInfoDataModel? = null): Map<String, String> {
  val base = linkedMapOf(
    "ip" to (call.request.header("X-Forwarded-For") ?: call.request.origin.remoteHost),
    "ua" to (call.request.userAgent() ?: "unknown")
  )

  fun clean(value: String?): String = value.orEmpty().trim().take(256)
  fun headerOrBody(headerName: String, bodyValue: String): String =
    clean(bodyValue).takeIf { it.isNotBlank() } ?: clean(call.request.header(headerName))

  val info = deviceInfo ?: ClientDeviceInfoDataModel()
  val installationId = headerOrBody(AITA_DEVICE_INSTALLATION_ID_HEADER, info.installationId)
  val deviceName = headerOrBody(AITA_DEVICE_NAME_HEADER, info.deviceName)
  val platformName = headerOrBody(AITA_DEVICE_PLATFORM_HEADER, info.platformName)
  val osName = headerOrBody(AITA_DEVICE_OS_HEADER, info.osName)
  val appName = headerOrBody(AITA_DEVICE_APP_NAME_HEADER, info.appName)
  val appVersion = headerOrBody(AITA_DEVICE_APP_VERSION_HEADER, info.appVersion)
  val localeLanguage = headerOrBody(AITA_DEVICE_LOCALE_HEADER, info.localeLanguage)

  if (installationId.isNotBlank()) base["installationId"] = installationId
  if (deviceName.isNotBlank()) base["deviceName"] = deviceName
  if (platformName.isNotBlank()) base["platformName"] = platformName
  if (osName.isNotBlank()) base["osName"] = osName
  if (appName.isNotBlank()) base["appName"] = appName
  if (appVersion.isNotBlank()) base["appVersion"] = appVersion
  if (localeLanguage.isNotBlank()) base["localeLanguage"] = localeLanguage

  return base
}

fun ResultRow.toNotificationDataModel(): NotificationDataModel {
  return NotificationDataModel(
    id = this[Notifications.id],
    userId = this[Notifications.userId].toString(),
    storeId = this[Notifications.storeId]?.toString(),
    title = this[Notifications.title],
    message = this[Notifications.message],
    type = runCatching { NotificationType.valueOf(this[Notifications.type]) }.getOrDefault(NotificationType.Neutral),
    category = this[Notifications.category],
    source = this[Notifications.notificationSource],
    metadata = this[Notifications.metadata],
    createdAtMillis = this[Notifications.createdAtMillis],
    shownAtMillis = this[Notifications.shownAtMillis],
    readAtMillis = this[Notifications.readAtMillis],
    isSavedOnServer = true
  )
}


private fun ResultRow.toSupportTicketDataModel(): SupportTicketDataModel = SupportTicketDataModel(
  id = this[SupportTickets.id].toString(),
  publicId = this[SupportTickets.publicId],
  userId = this[SupportTickets.userId].toString(),
  storeId = this[SupportTickets.storeId]?.toString(),
  subject = this[SupportTickets.subject],
  category = this[SupportTickets.category],
  priority = this[SupportTickets.priority],
  status = this[SupportTickets.status],
  assignedAgentUserId = this[SupportTickets.assignedAgentUserId]?.toString(),
  lastMessage = this[SupportTickets.lastMessage],
  lastMessageAtMillis = this[SupportTickets.lastMessageAtMillis],
  lastCustomerMessageAtMillis = this[SupportTickets.lastCustomerMessageAtMillis],
  lastAgentMessageAtMillis = this[SupportTickets.lastAgentMessageAtMillis],
  unreadForUserCount = this[SupportTickets.unreadForUserCount],
  unreadForAgentCount = this[SupportTickets.unreadForAgentCount],
  metadata = this[SupportTickets.metadata],
  createdAtMillis = this[SupportTickets.createdAtMillis],
  updatedAtMillis = this[SupportTickets.updatedAtMillis],
  closedAtMillis = this[SupportTickets.closedAtMillis],
  isActive = this[SupportTickets.isActive]
)

private fun ResultRow.toSupportMessageDataModel(): SupportMessageDataModel = SupportMessageDataModel(
  id = this[SupportMessages.id].toString(),
  ticketId = this[SupportMessages.ticketId].toString(),
  userId = this[SupportMessages.userId].toString(),
  senderUserId = this[SupportMessages.senderUserId].toString(),
  senderRole = this[SupportMessages.senderRole],
  senderDisplayName = this[SupportMessages.senderDisplayName],
  body = this[SupportMessages.body],
  attachments = this[SupportMessages.attachments],
  metadata = this[SupportMessages.metadata],
  clientMessageId = this[SupportMessages.clientMessageId],
  createdAtMillis = this[SupportMessages.createdAtMillis],
  editedAtMillis = this[SupportMessages.editedAtMillis],
  readByCustomerAtMillis = this[SupportMessages.readByCustomerAtMillis],
  readByAgentAtMillis = this[SupportMessages.readByAgentAtMillis],
  isActive = this[SupportMessages.isActive]
)

private fun aitaManualJsonString(value: String): String = buildString {
  append('"')
  value.forEach { char ->
    when (char) {
      '\\' -> append("\\\\")
      '"' -> append("\\\"")
      '\b' -> append("\\b")
      '\u000C' -> append("\\f")
      '\n' -> append("\\n")
      '\r' -> append("\\r")
      '\t' -> append("\\t")
      else -> {
        if (char.code < 0x20) {
          append("\\u")
          append(char.code.toString(16).padStart(4, '0'))
        } else {
          append(char)
        }
      }
    }
  }
  append('"')
}

private fun aitaManualLocalizedMessageArray(values: List<LocalizedStringDataModel>): String = values.joinToString(
  prefix = "[",
  postfix = "]"
) { value ->
  "{\"language\":${aitaManualJsonString(value.language)},\"value\":${aitaManualJsonString(value.value)}}"
}

@PublishedApi
internal fun aitaGenericEnvelopeText(
  message: List<LocalizedStringDataModel>?,
  payloadText: String?,
  negative: Boolean
): String = buildString {
  append('{')
  append("\"message\":")
  append(message?.let { aitaManualJsonString(aitaManualLocalizedMessageArray(it)) } ?: "null")
  append(',')
  append("\"payload\":")
  append(payloadText?.let { aitaManualJsonString(it) } ?: "null")
  append(',')
  append("\"negative\":")
  append(if (negative) "true" else "false")
  append('}')
}

suspend fun RoutingCall.genericResponseNoPayload(
  status: HttpStatusCode,
  message: List<LocalizedStringDataModel>? = null
) {
  withAitaServerRuntimeClassLoader("response-no-payload:${request.httpMethod.value}:${request.path()}") {
    respondText(
      text = aitaGenericEnvelopeText(message = message, payloadText = null, negative = !status.isSuccess()),
      contentType = ContentType.Application.Json,
      status = status
    )
  }
}

suspend fun RoutingCall.safeGenericResponseNoPayload(
  status: HttpStatusCode,
  message: List<LocalizedStringDataModel>? = null,
  logMessage: String? = null,
  throwable: Throwable? = null
) {
  if (logMessage != null && throwable != null) {
    application.environment.log.error(logMessage, throwable)
  } else if (logMessage != null) {
    application.environment.log.error(logMessage)
  }

  runCatching {
    genericResponseNoPayload(status = status, message = message)
  }.getOrElse { responseThrowable ->
    application.environment.log.error("Failed to send JSON error response", responseThrowable)
    val safeMessage = message ?: simpleMessage(
      main = "Internal server error",
      ru = "Внутренняя ошибка сервера",
      kk = "Сервердің ішкі қатесі"
    )
    runCatching {
      withAitaServerRuntimeClassLoader("safe-response-no-payload-fallback:${request.httpMethod.value}:${request.path()}") {
        respondText(
          text = aitaGenericEnvelopeText(message = safeMessage, payloadText = null, negative = true),
          contentType = ContentType.Application.Json,
          status = status
        )
      }
    }
  }
}

suspend fun ApplicationCall.safeGenericResponseNoPayload(
  status: HttpStatusCode,
  message: List<LocalizedStringDataModel>? = null,
  logMessage: String? = null,
  throwable: Throwable? = null
) {
  if (logMessage != null && throwable != null) {
    application.environment.log.error(logMessage, throwable)
  } else if (logMessage != null) {
    application.environment.log.error(logMessage)
  }

  runCatching {
    withAitaServerRuntimeClassLoader("response-no-payload:${request.httpMethod.value}:${request.path()}") {
      respondText(
        text = aitaGenericEnvelopeText(message = message, payloadText = null, negative = !status.isSuccess()),
        contentType = ContentType.Application.Json,
        status = status
      )
    }
  }.getOrElse { responseThrowable ->
    application.environment.log.error("Failed to send JSON error response", responseThrowable)
    val safeMessage = message ?: simpleMessage(
      main = "Internal server error",
      ru = "Внутренняя ошибка сервера",
      kk = "Сервердің ішкі қатесі"
    )
    runCatching {
      withAitaServerRuntimeClassLoader("safe-application-response-no-payload-fallback:${request.httpMethod.value}:${request.path()}") {
        respondText(
          text = aitaGenericEnvelopeText(message = safeMessage, payloadText = null, negative = true),
          contentType = ContentType.Application.Json,
          status = status
        )
      }
    }
  }
}

suspend fun ApplicationCall.respondAitaUnauthorized(
  message: List<LocalizedStringDataModel> = simpleMessage(
    main = "Authentication or permission is required",
    en = "Authentication or permission is required",
    ru = "Требуется вход или разрешение",
    kk = "Кіру немесе рұқсат қажет"
  )
) {
  withAitaServerRuntimeClassLoader("unauthorized:${request.httpMethod.value}:${request.path()}") {
    respondText(
      text = aitaGenericEnvelopeText(message = message, payloadText = null, negative = true),
      contentType = ContentType.Application.Json,
      status = HttpStatusCode.Unauthorized
    )
  }
}

suspend inline fun <reified T> RoutingCall.genericResponse(
  status: HttpStatusCode,
  payload: T?,
  message: List<LocalizedStringDataModel>? = null
) {
  withAitaServerRuntimeClassLoader("response:${request.httpMethod.value}:${request.path()}:${T::class.qualifiedName}") {
    val payloadText = try {
      payload?.let { jsonBase.encodeToString(it) }
    } catch (throwable: Throwable) {
      if (throwable.isClassLoadingFailure()) {
        refreshSharedRuntimeSerializersAfterClassLoadingFailure("response:${T::class.qualifiedName}", throwable)
      }
      application.environment.log.error("Failed to encode generic response payload", throwable)
      val safeMessage = listOf(
        LocalizedStringDataModel("main", "Internal server error"),
        LocalizedStringDataModel("en", "Internal server error"),
        LocalizedStringDataModel("ru", "Внутренняя ошибка сервера"),
        LocalizedStringDataModel("kk", "Сервердің ішкі қатесі")
      )
      respondText(
        text = aitaGenericEnvelopeText(message = safeMessage, payloadText = null, negative = true),
        contentType = ContentType.Application.Json,
        status = HttpStatusCode.InternalServerError
      )
      return@withAitaServerRuntimeClassLoader
    }

    respondText(
      text = aitaGenericEnvelopeText(message = message, payloadText = payloadText, negative = !status.isSuccess()),
      contentType = ContentType.Application.Json,
      status = status
    )
  }
}

private fun String.jsonStringLiteral(): String = jsonBase.encodeToString(this)

private fun TokenPair.toManualPayloadJson(): String = buildString {
  append('{')
  append("\"accessToken\":")
  append(accessToken.jsonStringLiteral())
  append(',')
  append("\"accessExpiryTime\":")
  append(accessExpiryTime)
  append(',')
  append("\"refreshToken\":")
  append(refreshToken.jsonStringLiteral())
  append(',')
  append("\"refreshExpiryTime\":")
  append(refreshExpiryTime)
  append('}')
}

suspend fun RoutingCall.genericTokenPairResponse(
  status: HttpStatusCode,
  payload: TokenPair?,
  message: List<LocalizedStringDataModel>? = null
) {
  withAitaServerRuntimeClassLoader("response-token:${request.httpMethod.value}:${request.path()}") {
    respondText(
      text = aitaGenericEnvelopeText(message = message, payloadText = payload?.toManualPayloadJson(), negative = !status.isSuccess()),
      contentType = ContentType.Application.Json,
      status = status
    )
  }
}

fun RoutingCall.pagingRequest(): PagingRequestDataModel {
  val maxPageSize = runCatching { request.queryParameters["max_page_size"]?.toInt() }.getOrNull() ?: 200
  return PagingRequestDataModel(
    page = request.queryParameters["page"]?.toIntOrNull() ?: 0,
    pageSize = request.queryParameters["page_size"]?.toIntOrNull()
      ?: request.queryParameters["pageSize"]?.toIntOrNull()
      ?: 40,
    query = request.queryParameters["query"].orEmpty(),
    sortBy = request.queryParameters["sort_by"] ?: request.queryParameters["sortBy"].orEmpty(),
    sortDirection = request.queryParameters["sort_direction"] ?: request.queryParameters["sortDirection"].orEmpty()
  ).normalized(maxPageSize)
}

fun RoutingCall.wantsPagedResponse(): Boolean {
  return request.queryParameters["paged"]?.equals("true", ignoreCase = true) == true
}

suspend inline fun <reified T> RoutingCall.genericListResponse(
  status: HttpStatusCode,
  payload: List<T>,
  message: List<LocalizedStringDataModel>? = null
) {
  if (wantsPagedResponse()) {
    genericResponse(
      status = status,
      payload = payload.toPagedResponse(pagingRequest()),
      message = message
    )
  } else {
    genericResponse(
      status = status,
      payload = payload,
      message = message
    )
  }
}

suspend fun RoutingCall.respondJsonError(
  status: HttpStatusCode,
  messageText: String,
  throwable: Throwable? = null
) {
  throwable?.let { application.environment.log.error(messageText, it) }

  genericResponseNoPayload(
    status = status,
    message = simpleMessage(
      main = messageText,
      ru = messageText,
      kk = messageText
    )
  )
}

suspend fun RoutingCall.checkPrincipal(): UUID? {
  val principal = principal<JWTPrincipal>()

  if (principal == null) {
    respondAitaUnauthorized()
    return null
  }

  val userId = runCatching { UUID.fromString(principal.subject) }.getOrNull()

  if (userId == null) {
    respondAitaUnauthorized()
    return null
  }

  return userId
}

fun RoutingCall.currentJwtSessionId(): UUID? {
  return runCatching {
    principal<JWTPrincipal>()
      ?.payload
      ?.getClaim("sessionId")
      ?.asString()
      ?.let { UUID.fromString(it) }
  }.getOrNull()
}

fun ResultRow.toSecuritySessionDataModel(currentSessionId: UUID?): SecuritySessionDataModel {
  val sessionMeta = this[RefreshSessions.meta].orEmpty()
  val sessionId = this[RefreshSessions.id]
  val revoked = this[RefreshSessions.revokedAt]
  val expires = this[RefreshSessions.expiresAt]
  val expiresAtMillis = expires.toEpochMilli().let { millis ->
    if (millis >= REFRESH_SESSION_NEVER_EXPIRES_AT_MILLIS - 86_400_000L) 0L else millis
  }

  return SecuritySessionDataModel(
    id = sessionId.toString(),
    userId = this[RefreshSessions.userId].toString(),
    deviceName = sessionMeta["deviceName"].orEmpty().ifBlank { sessionMeta["platformName"].orEmpty().ifBlank { "Unknown device" } },
    platformName = sessionMeta["platformName"].orEmpty(),
    osName = sessionMeta["osName"].orEmpty(),
    appName = sessionMeta["appName"].orEmpty(),
    appVersion = sessionMeta["appVersion"].orEmpty(),
    localeLanguage = sessionMeta["localeLanguage"].orEmpty(),
    ipAddress = sessionMeta["ip"].orEmpty(),
    userAgent = sessionMeta["ua"].orEmpty(),
    createdAtMillis = this[RefreshSessions.createdAt].toEpochMilli(),
    expiresAtMillis = expiresAtMillis,
    revokedAtMillis = revoked?.toEpochMilli(),
    current = sessionId == currentSessionId,
    active = revoked == null
  )
}

suspend fun loadSecuritySessionsForUser(userId: UUID, currentSessionId: UUID?): List<SecuritySessionDataModel> =
  newSuspendedTransaction(aitaServerIoContext) {
    RefreshSessions
      .selectAll()
      .where { RefreshSessions.userId eq userId }
      .orderBy(RefreshSessions.createdAt, SortOrder.DESC)
      .limit(50)
      .map { it.toSecuritySessionDataModel(currentSessionId) }
      .filter { it.active }
  }

fun ResultRow.toSecuritySessionHistoryDataModel(): SecuritySessionHistoryDataModel {
  return SecuritySessionHistoryDataModel(
    id = this[SecuritySessionEvents.id].toString(),
    userId = this[SecuritySessionEvents.userId].toString(),
    sessionId = this[SecuritySessionEvents.sessionId]?.toString(),
    eventType = this[SecuritySessionEvents.eventType],
    title = this[SecuritySessionEvents.title],
    details = this[SecuritySessionEvents.details],
    deviceName = this[SecuritySessionEvents.deviceName],
    platformName = this[SecuritySessionEvents.platformName],
    osName = this[SecuritySessionEvents.osName],
    appName = this[SecuritySessionEvents.appName],
    appVersion = this[SecuritySessionEvents.appVersion],
    ipAddress = this[SecuritySessionEvents.ipAddress],
    createdAtMillis = this[SecuritySessionEvents.createdAtMillis],
    metadata = this[SecuritySessionEvents.metadata]
  )
}

private const val SECURITY_EVENT_SESSION_CREATED = "session_created"
private const val SECURITY_EVENT_SESSION_REFRESHED = "session_refreshed"
private const val SECURITY_EVENT_SESSION_REPLACED = "session_replaced"
private const val SECURITY_EVENT_SESSION_REVOKED = "session_revoked"
private const val SECURITY_EVENT_SESSION_REVOKED_OTHERS = "session_revoked_others"
private const val SECURITY_EVENT_SESSION_LOGOUT = "session_logout"
private const val SECURITY_EVENT_SESSION_EXPIRED = "session_expired"

private fun securitySessionEventTitle(eventType: String): List<LocalizedStringDataModel> = when (eventType) {
  SECURITY_EVENT_SESSION_CREATED -> simpleMessage(
    main = "Session created",
    ru = "Сеанс создан",
    kk = "Сеанс жасалды"
  )
  SECURITY_EVENT_SESSION_REFRESHED -> simpleMessage(
    main = "Session refreshed",
    ru = "Сеанс обновлён",
    kk = "Сеанс жаңартылды"
  )
  SECURITY_EVENT_SESSION_REPLACED -> simpleMessage(
    main = "Older session replaced",
    ru = "Старый сеанс заменён",
    kk = "Ескі сеанс ауыстырылды"
  )
  SECURITY_EVENT_SESSION_REVOKED -> simpleMessage(
    main = "Session revoked",
    ru = "Сеанс завершён",
    kk = "Сеанс тоқтатылды"
  )
  SECURITY_EVENT_SESSION_REVOKED_OTHERS -> simpleMessage(
    main = "Other session revoked",
    ru = "Другой сеанс завершён",
    kk = "Басқа сеанс тоқтатылды"
  )
  SECURITY_EVENT_SESSION_LOGOUT -> simpleMessage(
    main = "Logged out",
    ru = "Выполнен выход",
    kk = "Шығу орындалды"
  )
  SECURITY_EVENT_SESSION_EXPIRED -> simpleMessage(
    main = "Session expired",
    ru = "Сеанс истёк",
    kk = "Сеанс мерзімі өтті"
  )
  else -> simpleMessage(
    main = "Security event",
    ru = "Событие безопасности",
    kk = "Қауіпсіздік оқиғасы"
  )
}

private fun securitySessionEventDetails(
  meta: Map<String, String>,
  metadata: Map<String, String>
): List<LocalizedStringDataModel> {
  val device = meta["deviceName"].orEmpty().ifBlank { meta["platformName"].orEmpty() }
  val parts = listOfNotNull(
    device.takeIf { it.isNotBlank() },
    meta["platformName"].orEmpty().takeIf { it.isNotBlank() && it != device },
    meta["osName"].orEmpty().takeIf { it.isNotBlank() },
    meta["appVersion"].orEmpty().takeIf { it.isNotBlank() }?.let { "AITA $it" },
    meta["ip"].orEmpty().takeIf { it.isNotBlank() }?.let { "IP $it" },
    metadata["reason"].orEmpty().takeIf { it.isNotBlank() }
  )
  val details = parts.joinToString(" • ")
  return if (details.isBlank()) emptyList() else simpleMessage(details, ru = details, kk = details)
}

private fun insertSecuritySessionEventInsideTransaction(
  userId: UUID,
  sessionId: UUID?,
  eventType: String,
  metaParam: Map<String, String>?,
  metadata: Map<String, String> = emptyMap(),
  now: Long = System.currentTimeMillis()
) {
  runCatching {
    val meta = metaParam.orEmpty()
    val storedMetadata = metadata.toMutableMap().apply {
      meta["localeLanguage"]?.takeIf { it.isNotBlank() }?.let { putIfAbsent("localeLanguage", it) }
    }
    SecuritySessionEvents.insert {
      it[id] = UUID.randomUUID()
      it[SecuritySessionEvents.userId] = userId
      it[SecuritySessionEvents.sessionId] = sessionId
      it[SecuritySessionEvents.eventType] = eventType
      it[title] = securitySessionEventTitle(eventType)
      it[details] = securitySessionEventDetails(meta, storedMetadata)
      it[deviceName] = meta["deviceName"].orEmpty().ifBlank { meta["platformName"].orEmpty() }
      it[platformName] = meta["platformName"].orEmpty()
      it[osName] = meta["osName"].orEmpty()
      it[appName] = meta["appName"].orEmpty()
      it[appVersion] = meta["appVersion"].orEmpty()
      it[ipAddress] = meta["ip"].orEmpty()
      it[SecuritySessionEvents.metadata] = storedMetadata
      it[SecuritySessionEvents.createdAtMillis] = now
    }
  }
}

private fun Throwable.isRefreshSessionInsertCollision(): Boolean {
  val exposed = this as? ExposedSQLException ?: return false
  if (exposed.sqlState != "23505") return false

  val postgresConstraint = (exposed.cause as? PSQLException)
    ?.serverErrorMessage
    ?.constraint
    .orEmpty()
    .lowercase()

  val exposedMessage = listOfNotNull(exposed.message, exposed.cause?.message)
    .joinToString(" ")
    .lowercase()

  return postgresConstraint.startsWith("idx_refresh_sessions_one_active_per_") ||
     postgresConstraint.contains("refresh_sessions") ||
     exposedMessage.contains("refresh_sessions")
}

private data class RefreshSessionRetryContext(
  val userId: UUID,
  val meta: Map<String, String>?
)

private suspend fun loadRefreshSessionRetryContext(
  refreshPlain: String,
  metaParam: Map<String, String>?
): RefreshSessionRetryContext? {
  val hash = Refresh.hash(refreshPlain)
  return newSuspendedTransaction(aitaServerIoContext) {
    RefreshSessions
      .selectAll()
      .where { RefreshSessions.tokenHash eq hash }
      .singleOrNull()
      ?.let { row ->
        RefreshSessionRetryContext(
          userId = row[RefreshSessions.userId],
          meta = row[RefreshSessions.meta].orEmpty() + metaParam.orEmpty()
        )
      }
  }
}

private suspend fun revokeSameDeviceSessionsBeforeRefreshSessionRetry(
  userId: UUID,
  metaParam: Map<String, String>?,
  replacementSessionId: UUID? = null
) {
  newSuspendedTransaction(aitaServerIoContext) {
    val now = Instant.now()
    val nowMillis = now.toEpochMilli()

    Users
      .selectAll()
      .where { Users.id eq userId }
      .forUpdate()
      .singleOrNull()

    val sessionIds = sameDeviceSessionIdsInsideTransaction(userId, metaParam)

    if (sessionIds.isNotEmpty()) {
      RefreshSessions.update({ RefreshSessions.id inList sessionIds }) {
        it[RefreshSessions.revokedAt] = now
      }

      sessionIds.forEach { oldId ->
        insertSecuritySessionEventInsideTransaction(
          userId = userId,
          sessionId = oldId,
          eventType = SECURITY_EVENT_SESSION_REPLACED,
          metaParam = metaParam,
          metadata = buildMap {
            put("reason", "retry cleanup before creating a new same-device session")
            replacementSessionId?.let { put("replacement_session_id", it.toString()) }
          },
          now = nowMillis
        )
      }
    }
  }
}

private fun sameDeviceSessionIdsInsideTransaction(
  userId: UUID,
  metaParam: Map<String, String>?,
  exceptSessionIds: Set<UUID> = emptySet()
): List<UUID> {
  val installationId = metaParam?.get("installationId")?.takeIf { it.isNotBlank() }
  val deviceName = metaParam?.get("deviceName")?.takeIf { it.isNotBlank() }
  val platformName = metaParam?.get("platformName")?.takeIf { it.isNotBlank() }
  val osName = metaParam?.get("osName")?.takeIf { it.isNotBlank() }

  return RefreshSessions
    .selectAll()
    .where {
      (RefreshSessions.userId eq userId) and
         RefreshSessions.revokedAt.isNull()
    }
    .mapNotNull { row ->
      val rowId = row[RefreshSessions.id]
      if (rowId in exceptSessionIds) {
        null
      } else {
        val meta = row[RefreshSessions.meta].orEmpty()
        val rowInstallationId = meta["installationId"]?.takeIf { it.isNotBlank() }
        val rowDeviceName = meta["deviceName"]?.takeIf { it.isNotBlank() }
        val rowPlatformName = meta["platformName"]?.takeIf { it.isNotBlank() }
        val rowOsName = meta["osName"]?.takeIf { it.isNotBlank() }
        val sameInstallation = installationId != null && rowInstallationId == installationId
        val sameVisibleDevice = deviceName != null &&
           rowDeviceName == deviceName &&
           rowPlatformName == platformName &&
           rowOsName == osName

        if (sameInstallation || sameVisibleDevice) rowId else null
      }
    }
}


private fun Throwable.isAlreadyRotatedOrRevokedRefreshToken(): Boolean =
  this is IllegalAccessException && message?.contains("already rotated or revoked", ignoreCase = true) == true

private fun sameRefreshSessionDeviceMeta(left: Map<String, String>?, right: Map<String, String>?): Boolean {
  val a = left.orEmpty()
  val b = right.orEmpty()
  val leftInstallation = a["installationId"]?.takeIf { it.isNotBlank() }
  val rightInstallation = b["installationId"]?.takeIf { it.isNotBlank() }
  if (leftInstallation != null && rightInstallation != null) return leftInstallation == rightInstallation

  val sameVisibleDevice = a["deviceName"]?.takeIf { it.isNotBlank() } != null &&
     a["deviceName"] == b["deviceName"] &&
     a["platformName"] == b["platformName"] &&
     a["osName"] == b["osName"]
  if (sameVisibleDevice) return true

  val leftUserAgent = a["ua"]?.takeIf { it.isNotBlank() }
  val rightUserAgent = b["ua"]?.takeIf { it.isNotBlank() }
  val leftIp = a["ip"]?.takeIf { it.isNotBlank() }
  val rightIp = b["ip"]?.takeIf { it.isNotBlank() }
  return leftUserAgent != null && rightUserAgent != null && leftUserAgent == rightUserAgent && leftIp == rightIp
}

private fun mergedRefreshSessionMeta(vararg values: Map<String, String>?): Map<String, String> =
  linkedMapOf<String, String>().apply {
    values.forEach { value -> value.orEmpty().forEach { (key, item) -> if (item.isNotBlank()) put(key, item) } }
  }

suspend fun loadSecuritySessionHistoryForUser(userId: UUID): List<SecuritySessionHistoryDataModel> =
  newSuspendedTransaction(aitaServerIoContext) {
    SecuritySessionEvents
      .selectAll()
      .where { SecuritySessionEvents.userId eq userId }
      .orderBy(SecuritySessionEvents.createdAtMillis, SortOrder.DESC)
      .limit(120)
      .map { it.toSecuritySessionHistoryDataModel() }
  }

@Volatile
private var responsesCache: List<RemoteResponseDataModel>? = null
private val responsesCacheLock = Any()

fun getResponses(): List<RemoteResponseDataModel> {
  responsesCache?.let { return it }

  return synchronized(responsesCacheLock) {
    responsesCache ?: Json.decodeFromString<List<RemoteResponseDataModel>>(
      Files.readString(configAppRootPath.resolve("responses.json"))
    ).also { responsesCache = it }
  }
}

private fun ResultRow.toUserAccountDataModel(): UserAccountDataModel {
  val userId = this[Users.id]
  val userIdText = userId.toString()

  val ownedStoreIds = Stores
    .selectAll()
    .mapNotNull { row -> row[Stores.id].toString().takeIf { row[Stores.ownerUserIds].contains(userIdText) } }
    .distinct()

  val managedStoreIds = StoreUsers
    .select(StoreUsers.storeId)
    .where { StoreUsers.userId eq userId }
    .map { it[StoreUsers.storeId].toString() }
    .distinct()

  val supplierIds = Suppliers
    .select(Suppliers.id, Suppliers.userIds)
    .where { Suppliers.isActive eq true }
    .mapNotNull { row ->
      row[Suppliers.id].toString().takeIf { decodeSupplierStringList(row[Suppliers.userIds]).contains(userIdText) }
    }
    .distinct()

  val manufacturerIds = Manufacturers
    .select(Manufacturers.id, Manufacturers.userIds)
    .where { Manufacturers.isActive eq true }
    .mapNotNull { row ->
      row[Manufacturers.id].toString().takeIf { decodeSupplierStringList(row[Manufacturers.userIds]).contains(userIdText) }
    }
    .distinct()

  val roleIds = buildList {
    add(USER_ROLE_BUYER)
    if (ownedStoreIds.isNotEmpty()) add(USER_ROLE_STORE_OWNER)
    if (managedStoreIds.isNotEmpty()) add(USER_ROLE_STORE_WORKER)
    if (supplierIds.isNotEmpty()) add(USER_ROLE_SUPPLIER)
    if (manufacturerIds.isNotEmpty()) add(USER_ROLE_MANUFACTURER)
  }.distinct()

  return UserAccountDataModel(
    id = userIdText,
    publicId = this[Users.publicId],
    phoneNumber = this[Users.phoneNumber],
    email = this[Users.email],
    firstName = this[Users.firstName],
    lastName = this[Users.lastName],
    countryLocale = this[Users.countryLocale],
    workerAccountIds = this[Users.workerIds],
    supplierAccountIds = supplierIds.takeIf { it.isNotEmpty() }?.let { jsonBase.encodeToString(it) } ?: this[Users.supplierIds],
    roleIds = roleIds,
    ownedStoreIds = ownedStoreIds,
    managedStoreIds = managedStoreIds,
    manufacturerAccountIds = manufacturerIds.takeIf { it.isNotEmpty() }?.let { jsonBase.encodeToString(it) },
    buyerAccountId = userIdText,
    activeStoreId = this[Users.activeStoreId]?.toString(),
    appLanguage = this[Users.appLanguage],
    appThemeId = this[Users.appThemeId],
    appSizeModeId = this[Users.appSizeModeId],
    createdAt = this[Users.createdAt].toEpochMilli(),
    isActive = this[Users.isActive]
  )
}


private fun fallbackResponseMessage(id: String): List<LocalizedStringDataModel> {
  return when (id) {
    "0" -> simpleMessage(
      main = "User with this phone number is already registered",
      ru = "Пользователь с этим номером телефона уже зарегистрирован",
      kk = "Бұл телефон нөмірі бар пайдаланушы әлдеқашан тіркелген"
    )
    "1" -> simpleMessage(
      main = "User with this email address is already registered",
      ru = "Пользователь с этим email уже зарегистрирован",
      kk = "Бұл email мекенжайы бар пайдаланушы әлдеқашан тіркелген"
    )
    "2" -> simpleMessage(
      main = "User with this phone number and email address is already registered",
      ru = "Пользователь с этим номером телефона и email уже зарегистрирован",
      kk = "Бұл телефон нөмірі мен email мекенжайы бар пайдаланушы әлдеқашан тіркелген"
    )
    "3" -> simpleMessage(
      main = "Internal server error",
      ru = "Внутренняя ошибка сервера",
      kk = "Сервердің ішкі қатесі"
    )
    "4" -> simpleMessage(
      main = "Authentication failed",
      ru = "Аутентификация не удалась",
      kk = "Аутентификация сәтсіз аяқталды"
    )
    "5" -> simpleMessage(
      main = "Please log in first",
      ru = "Пожалуйста, сначала войдите",
      kk = "Алдымен жүйеге кіріңіз"
    )
    "6" -> simpleMessage(
      main = "Incorrect password",
      ru = "Неверный пароль",
      kk = "Қате құпия сөз"
    )
    "7" -> simpleMessage(
      main = "Store is not registered",
      ru = "Магазин не зарегистрирован",
      kk = "Дүкен тіркелмеген"
    )
    "8" -> simpleMessage(
      main = "Successfully logged out",
      ru = "Вы успешно вышли",
      kk = "Жүйеден сәтті шықтыңыз"
    )
    "9" -> simpleMessage(
      main = "User successfully updated",
      ru = "Данные пользователя обновлены",
      kk = "Пайдаланушы деректері жаңартылды"
    )
    "10" -> simpleMessage(
      main = "Store successfully added",
      ru = "Магазин добавлен",
      kk = "Дүкен қосылды"
    )
    "11" -> simpleMessage(
      main = "Store successfully updated",
      ru = "Магазин обновлён",
      kk = "Дүкен жаңартылды"
    )
    "12" -> simpleMessage(
      main = "Store successfully deleted",
      ru = "Магазин удалён",
      kk = "Дүкен жойылды"
    )
    "13" -> simpleMessage(
      main = "Not found",
      ru = "Не найдено",
      kk = "Табылмады"
    )
    "14" -> simpleMessage(
      main = "Stock item added",
      ru = "Товар добавлен",
      kk = "Тауар қосылды"
    )
    "15" -> simpleMessage(
      main = "Stock item updated",
      ru = "Товар обновлён",
      kk = "Тауар жаңартылды"
    )
    "16" -> simpleMessage(
      main = "Stock item deleted",
      ru = "Товар удалён",
      kk = "Тауар жойылды"
    )
    "17" -> simpleMessage(
      main = "Batch added",
      ru = "Партия добавлена",
      kk = "Партия қосылды"
    )
    "18" -> simpleMessage(
      main = "Batch updated",
      ru = "Партия обновлена",
      kk = "Партия жаңартылды"
    )
    "19" -> simpleMessage(
      main = "Batch deleted",
      ru = "Партия удалена",
      kk = "Партия жойылды"
    )
    "44" -> simpleMessage(
      main = "Active sessions loaded",
      ru = "Активные сеансы загружены",
      kk = "Белсенді сеанстар жүктелді"
    )
    "45" -> simpleMessage(
      main = "Session revoked",
      ru = "Сеанс завершён",
      kk = "Сеанс тоқтатылды"
    )
    "46" -> simpleMessage(
      main = "Other sessions revoked",
      ru = "Другие сеансы завершены",
      kk = "Басқа сеанстар тоқтатылды"
    )
    "47" -> simpleMessage(
      main = "Session id is required",
      ru = "Нужен id сеанса",
      kk = "Сеанс id қажет"
    )
    "48" -> simpleMessage(
      main = "Use logout to revoke the current session",
      ru = "Чтобы завершить текущий сеанс, выйдите из аккаунта",
      kk = "Ағымдағы сеансты тоқтату үшін аккаунттан шығыңыз"
    )
    "49" -> simpleMessage("Cash register loaded", ru = "Касса загружена", kk = "Касса жүктелді")
    "50" -> simpleMessage("Cash extracted", ru = "Наличные извлечены", kk = "Қолма-қол ақша алынды")
    "51" -> simpleMessage("Workers loaded", ru = "Сотрудники загружены", kk = "Қызметкерлер жүктелді")
    "52" -> simpleMessage("My work loaded", ru = "Мои места работы загружены", kk = "Менің жұмыс орындарым жүктелді")
    "53" -> simpleMessage("Incoming requests loaded", ru = "Входящие заявки загружены", kk = "Кіріс өтінімдер жүктелді")
    "54" -> simpleMessage("My requests loaded", ru = "Мои заявки загружены", kk = "Менің өтінімдерім жүктелді")
    "55" -> simpleMessage("Employment request sent", ru = "Заявка на работу отправлена", kk = "Жұмысқа өтінім жіберілді")
    "56" -> simpleMessage("Worker accepted", ru = "Сотрудник принят", kk = "Қызметкер қабылданды")
    "57" -> simpleMessage("Request declined", ru = "Заявка отклонена", kk = "Өтінім қабылданбады")
    "58" -> simpleMessage("Worker permissions updated", ru = "Права сотрудника обновлены", kk = "Қызметкер рұқсаттары жаңартылды")
    "59" -> simpleMessage("Permission denied", ru = "Недостаточно прав", kk = "Рұқсат жеткіліксіз")
    "60" -> simpleMessage("Cash register amount is not enough", ru = "В кассе недостаточно наличных", kk = "Кассада қолма-қол ақша жеткіліксіз")
    "61" -> simpleMessage("Request is already pending", ru = "Заявка уже ожидает решения", kk = "Өтінім қазірдің өзінде күтуде")
    "62" -> simpleMessage("User is already a worker in this store", ru = "Пользователь уже сотрудник этого магазина", kk = "Пайдаланушы бұл дүкеннің қызметкері")
    "96" -> simpleMessage("Support tickets loaded", ru = "Обращения в поддержку загружены", kk = "Қолдау өтініштері жүктелді")
    "97" -> simpleMessage("Support request created", ru = "Обращение в поддержку создано", kk = "Қолдау өтініші жасалды")
    "98" -> simpleMessage("Support request closed", ru = "Обращение закрыто", kk = "Өтініш жабылды")
    "99" -> simpleMessage("Support request reopened", ru = "Обращение снова открыто", kk = "Өтініш қайта ашылды")
    "100" -> simpleMessage("Support messages loaded", ru = "Сообщения поддержки загружены", kk = "Қолдау хабарламалары жүктелді")
    "101" -> simpleMessage("Support message sent", ru = "Сообщение в поддержку отправлено", kk = "Қолдау хабарламасы жіберілді")
    "102" -> simpleMessage("Support messages marked as read", ru = "Сообщения поддержки отмечены прочитанными", kk = "Қолдау хабарламалары оқылған деп белгіленді")
    "103" -> simpleMessage("Workshift password updated", ru = "Пароль смены обновлён", kk = "Ауысым құпия сөзі жаңартылды")
    "104" -> simpleMessage("Security history loaded", ru = "История безопасности загружена", kk = "Қауіпсіздік тарихы жүктелді")
    "92" -> simpleMessage("Operation logs loaded", ru = "Журнал операций загружен", kk = "Операциялар журналы жүктелді")
    "93" -> simpleMessage("Operation logged", ru = "Операция записана в журнал", kk = "Операция журналға жазылды")
    else -> simpleMessage(
      main = "Done",
      ru = "Готово",
      kk = "Дайын"
    )
  }
}

fun getResponse(id: String): RemoteResponseDataModel {
  return runCatching { getResponses().find { it.id == id } }
    .getOrNull()
    ?: RemoteResponseDataModel(
      id = id,
      message = fallbackResponseMessage(id)
    )
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
  val publicId = varchar("public_id", 16).uniqueIndex()
  val phoneNumber = varchar("phone_number", 32).uniqueIndex()
  val email = varchar("email", 255).uniqueIndex()
  val firstName = varchar("first_name", 255)
  val lastName = varchar("last_name", 255)
  val countryLocale = varchar("country_locale", 64)
  val workerIds = text("worker_ids").nullable().default(null)
  val supplierIds = text("supplier_ids").nullable().default(null)
  val activeStoreId = uuid("active_store_id").nullable()
  val appLanguage = varchar("app_language", 16).default(DEFAULT_APP_LANGUAGE)
  val appThemeId = long("app_theme_id").default(DEFAULT_APP_THEME_ID)
  val appSizeModeId = long("app_size_mode_id").default(DEFAULT_APP_SIZE_MODE_ID)
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

object UserWallets: Table("user_wallets") {
  val id = uuid("id").uniqueIndex()
  val userId = uuid("user_id").uniqueIndex().references(Users.id, onDelete = ReferenceOption.CASCADE)
  val currencyCode = text("currency_code")
  val balanceMinor = long("balance_minor").default(0L)
  val reservedMinor = long("reserved_minor").default(0L)
  val updatedAtMillis = long("updated_at_millis").default(0L)
  val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
  val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)

  override val primaryKey = PrimaryKey(id)
}

object UserWalletLedgerEntries: Table("user_wallet_ledger_entries") {
  val id = uuid("id").uniqueIndex()
  val userId = uuid("user_id").references(Users.id, onDelete = ReferenceOption.CASCADE)
  val walletId = uuid("wallet_id").references(UserWallets.id, onDelete = ReferenceOption.CASCADE)
  val type = text("type")
  val amountMinor = long("amount_minor")
  val balanceBeforeMinor = long("balance_before_minor")
  val balanceAfterMinor = long("balance_after_minor")
  val currencyCode = text("currency_code")
  val referenceType = text("reference_type").default("")
  val referenceId = text("reference_id").default("")
  val note = text("note").default("")
  val createdAtMillis = long("created_at_millis")
  val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)

  override val primaryKey = PrimaryKey(id)
}

object TopUpPaymentIntents: Table("top_up_payment_intents") {
  val id = uuid("id").uniqueIndex()
  val userId = uuid("user_id").references(Users.id, onDelete = ReferenceOption.CASCADE)
  val providerId = text("provider_id")
  val amountMinor = long("amount_minor")
  val currencyCode = text("currency_code")
  val status = text("status")
  val providerInvoiceId = text("provider_invoice_id").default("")
  val paymentUrl = text("payment_url").default("")
  val qrPayload = text("qr_payload").default("")
  val createdAtMillis = long("created_at_millis")
  val expiresAtMillis = long("expires_at_millis").nullable()
  val paidAtMillis = long("paid_at_millis").nullable()
  val metadata = jsonb("metadata", Json, MapSerializer(String.serializer(), String.serializer())).default(emptyMap())
  val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
  val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)

  override val primaryKey = PrimaryKey(id)
}

object StoreSubscriptionStates: Table("store_subscription_states") {
  val id = uuid("id").uniqueIndex()
  val storeId = uuid("store_id").uniqueIndex().references(Stores.id, onDelete = ReferenceOption.CASCADE)
  val ownerUserId = uuid("owner_user_id").references(Users.id, onDelete = ReferenceOption.CASCADE)
  val planId = text("plan_id").default("")
  val status = text("status").default(SUBSCRIPTION_STATUS_INACTIVE)
  val autoRenew = bool("auto_renew").default(false)
  val startedAtMillis = long("started_at_millis").nullable()
  val currentPeriodStartMillis = long("current_period_start_millis").nullable()
  val currentPeriodEndMillis = long("current_period_end_millis").nullable()
  val nextChargeAtMillis = long("next_charge_at_millis").nullable()
  val cancelledAtMillis = long("cancelled_at_millis").nullable()
  val pastDueSinceMillis = long("past_due_since_millis").nullable()
  val updatedAtMillis = long("updated_at_millis").default(0L)
  val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
  val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)

  override val primaryKey = PrimaryKey(id)
}

object StoreSubscriptionChargeEvents: Table("store_subscription_charge_events") {
  val id = uuid("id").uniqueIndex()
  val storeId = uuid("store_id").references(Stores.id, onDelete = ReferenceOption.CASCADE)
  val userId = uuid("user_id").references(Users.id, onDelete = ReferenceOption.CASCADE)
  val planId = text("plan_id")
  val amountMinor = long("amount_minor")
  val currencyCode = text("currency_code")
  val periodStartMillis = long("period_start_millis")
  val periodEndMillis = long("period_end_millis")
  val status = text("status")
  val walletLedgerEntryId = text("wallet_ledger_entry_id").default("")
  val createdAtMillis = long("created_at_millis")
  val note = text("note").default("")
  val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)

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


object StoreWorkerRequests: Table("store_worker_requests") {
  val id = uuid("id").uniqueIndex()
  val storeId = uuid("store_id").references(Stores.id, onDelete = ReferenceOption.CASCADE)
  val requesterUserId = uuid("requester_user_id").references(Users.id, onDelete = ReferenceOption.CASCADE)
  val direction = text("direction").default(WORKER_REQUEST_DIRECTION_USER_TO_STORE)
  val invitedByUserId = uuid("invited_by_user_id").nullable()
  val status = text("status").default("pending")
  val requestedAtMillis = long("requested_at_millis")
  val decidedAtMillis = long("decided_at_millis").nullable()
  val decidedByUserId = uuid("decided_by_user_id").nullable()
  val roleId = text("role_id").default(WORKER_ROLE_STANDARD)
  val permissions = jsonb("permissions", Json, ListSerializer(String.serializer())).default(STANDARD_STORE_PERMISSION_IDS)
  val jobTitle = text("job_title").default("")
  val jobTitleLocalized = jsonb("job_title_localized", Json, ListSerializer(LocalizedStringDataModel.serializer())).default(emptyList())
  val salary = text("salary").default("")
  val salaryCurrencyCode = text("salary_currency_code").default("KZT")
  val offerNote = text("offer_note").nullable()
  val offerNoteLocalized = jsonb("offer_note_localized", Json, ListSerializer(LocalizedStringDataModel.serializer())).default(emptyList())
  val workshiftPasswordHash = text("workshift_password_hash").nullable()
  val note = text("note").nullable()
  val noteLocalized = jsonb("note_localized", Json, ListSerializer(LocalizedStringDataModel.serializer())).default(emptyList())
  val responseNote = text("response_note").nullable()
  val responseNoteLocalized = jsonb("response_note_localized", Json, ListSerializer(LocalizedStringDataModel.serializer())).default(emptyList())
  val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
  val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)

  override val primaryKey = PrimaryKey(id)
}

object StoreWorkerMemberships: Table("store_worker_memberships") {
  val id = uuid("id").uniqueIndex()
  val storeId = uuid("store_id").references(Stores.id, onDelete = ReferenceOption.CASCADE)
  val userId = uuid("user_id").references(Users.id, onDelete = ReferenceOption.CASCADE)
  val requestId = uuid("request_id").nullable()
  val roleId = text("role_id").default(WORKER_ROLE_STANDARD)
  val permissions = jsonb("permissions", Json, ListSerializer(String.serializer())).default(STANDARD_STORE_PERMISSION_IDS)
  val jobTitle = text("job_title").default("")
  val jobTitleLocalized = jsonb("job_title_localized", Json, ListSerializer(LocalizedStringDataModel.serializer())).default(emptyList())
  val salary = text("salary").default("")
  val salaryCurrencyCode = text("salary_currency_code").default("KZT")
  val workshiftPasswordHash = text("workshift_password_hash").nullable()
  val requestedAtMillis = long("requested_at_millis").default(0L)
  val acceptedAtMillis = long("accepted_at_millis")
  val acceptedByUserId = uuid("accepted_by_user_id")
  val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
  val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)
  val isActive = bool("is_active").default(true)

  override val primaryKey = PrimaryKey(id)
}

object StoreWorkerRoleTemplates: Table("store_worker_role_templates") {
  val id = uuid("id").uniqueIndex()
  val storeId = uuid("store_id").references(Stores.id, onDelete = ReferenceOption.CASCADE)
  val name = jsonb("name", Json, ListSerializer(LocalizedStringDataModel.serializer())).default(emptyList())
  val description = jsonb("description", Json, ListSerializer(LocalizedStringDataModel.serializer())).default(emptyList())
  val permissions = jsonb("permissions", Json, ListSerializer(String.serializer())).default(STANDARD_STORE_PERMISSION_IDS)
  val createdAtMillis = long("created_at_millis").default(0L)
  val updatedAtMillis = long("updated_at_millis").default(0L)
  val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
  val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)
  val isActive = bool("is_active").default(true)

  override val primaryKey = PrimaryKey(id)
}

object Workshifts: Table("workshifts") {
  val id = uuid("id").uniqueIndex()
  val storeId = uuid("store_id").references(Stores.id, onDelete = ReferenceOption.CASCADE)
  val workerMembershipId = uuid("worker_membership_id").references(StoreWorkerMemberships.id, onDelete = ReferenceOption.CASCADE)
  val workerUserId = uuid("worker_user_id").references(Users.id, onDelete = ReferenceOption.CASCADE)
  val startedAtMillis = long("started_at_millis")
  val endedAtMillis = long("ended_at_millis").nullable()
  val startedByUserId = uuid("started_by_user_id").references(Users.id, onDelete = ReferenceOption.CASCADE)
  val endedByUserId = uuid("ended_by_user_id").nullable()
  val metadata = jsonb("metadata", Json, MapSerializer(String.serializer(), String.serializer())).default(emptyMap())
  val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
  val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)
  val isActive = bool("is_active").default(true)

  override val primaryKey = PrimaryKey(id)
}

object OperationLogs: Table("operation_logs") {
  val id = uuid("id").uniqueIndex()
  val rootStoreId = uuid("root_store_id").references(Stores.id, onDelete = ReferenceOption.CASCADE)
  val storeId = uuid("store_id").references(Stores.id, onDelete = ReferenceOption.CASCADE)
  val storePublicId = text("store_public_id").default("")
  val storeName = jsonb("store_name", Json, ListSerializer(LocalizedStringDataModel.serializer())).default(emptyList())
  val actorUserId = uuid("actor_user_id").references(Users.id, onDelete = ReferenceOption.CASCADE)
  val actorPublicId = text("actor_public_id").default("")
  val actorDisplayName = text("actor_display_name").default("")
  val workshiftId = uuid("workshift_id").nullable()
  val action = text("action")
  val entityType = text("entity_type")
  val entityId = text("entity_id").nullable()
  val title = jsonb("title", Json, ListSerializer(LocalizedStringDataModel.serializer())).default(emptyList())
  val details = jsonb("details", Json, ListSerializer(LocalizedStringDataModel.serializer())).default(emptyList())
  val metadata = jsonb("metadata", Json, MapSerializer(String.serializer(), String.serializer())).default(emptyMap())
  val createdAtMillis = long("created_at_millis")
  val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)

  override val primaryKey = PrimaryKey(id)
}

object CashRegisters: Table("cash_registers") {
  val storeId = uuid("store_id").uniqueIndex().references(Stores.id, onDelete = ReferenceOption.CASCADE)
  val currentAmount = double("current_amount").default(0.0)
  val currencyCode = text("currency_code").default("KZT")
  val updatedAtMillis = long("updated_at_millis").default(0L)
  val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
  val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)

  override val primaryKey = PrimaryKey(storeId)
}

object CashRegisterEvents: Table("cash_register_events") {
  val id = uuid("id").uniqueIndex()
  val storeId = uuid("store_id").references(Stores.id, onDelete = ReferenceOption.CASCADE)
  val userId = uuid("user_id").references(Users.id, onDelete = ReferenceOption.CASCADE)
  val type = text("type")
  val amount = double("amount")
  val balanceBefore = double("balance_before")
  val balanceAfter = double("balance_after")
  val transactionId = uuid("transaction_id").nullable()
  val note = text("note").nullable()
  val timeMillis = long("time_millis")
  val metadata = jsonb("metadata", Json, MapSerializer(String.serializer(), String.serializer())).default(emptyMap())
  val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)

  override val primaryKey = PrimaryKey(id)
}

object StoreSubscriptions: Table("store_subscriptions") {
  val id = uuid("id").uniqueIndex()
  val storeId = uuid("store_id").uniqueIndex()
  val history = jsonb("history", Json, ListSerializer(SubscriptionDataModel.serializer()))

  override val primaryKey = PrimaryKey(id)
}

object Stores: Table("stores") {
  val id = uuid("id").uniqueIndex()
  val publicId = varchar("public_id", 16).uniqueIndex()
  val parentStoreId = uuid("parent_store_id").nullable()
  val ownerUserIds = jsonb("owner_user_ids", Json, ListSerializer(String.serializer()))
  val storeTypeIds = jsonb("store_type_ids", Json, ListSerializer(String.serializer()))

  val name = jsonb("name", Json, ListSerializer(LocalizedStringDataModel.serializer()))
  val alias = jsonb("alias", Json, ListSerializer(LocalizedStringDataModel.serializer()))
  val description = jsonb("description", Json, ListSerializer(LocalizedStringDataModel.serializer()))
  val companyForms = jsonb("company_forms", Json, ListSerializer(CompanyFormDataModel.serializer()))

  val location = jsonb("location", Json, LocationDataModel.serializer())
  val address = text("address").default("")
  val legalIdTypeId = text("legal_id_type_id").default("")
  val legalId = text("legal_id").default("")
  val phoneNumbers = jsonb("phone_numbers", Json, ListSerializer(String.serializer()))
  val emails = jsonb("emails", Json, ListSerializer(String.serializer()))
  val countryLocales = jsonb("country_locales", Json, ListSerializer(String.serializer()))

  val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
  val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)

  override val primaryKey = PrimaryKey(id)
}

object StoreActivationHistory: Table("store_activation_history") {
  val id = uuid("id").uniqueIndex()
  val storeId = uuid("store_id").uniqueIndex()
  val history = jsonb("history", Json, ListSerializer(ActivationHistoryEntryDataModel.serializer()))

  override val primaryKey = PrimaryKey(id)
}

object StockItems: Table("stock_items") {
  val id = uuid("id")
  val userId = uuid("user_id")
  val storeId = uuid("store_id")

  val barcodes = jsonb("barcodes", Json, ListSerializer(String.serializer()))
  val barcodeModels = jsonb("barcode_models", Json, ListSerializer(GoodsItemBarcodeDataModel.serializer())).default(emptyList())
  val name = jsonb("name", Json, ListSerializer(LocalizedStringDataModel.serializer()))
  val description = jsonb("description", Json, ListSerializer(LocalizedStringDataModel.serializer()))

  val measurementUnitId = text("measurement_unit_id")
  val categoryIds = jsonb("category_ids", Json, ListSerializer(String.serializer()))

  val salePrices = jsonb("sale_prices", Json, ListSerializer(PriceDataModel.serializer()))
  val returnPrices = jsonb("return_prices", Json, ListSerializer(PriceDataModel.serializer()))
  val supplyPrices = jsonb("supply_prices", Json, ListSerializer(PriceDataModel.serializer()))
  val wholesalePrices = jsonb("wholesale_prices", Json, ListSerializer(PriceDataModel.serializer())).default(emptyList())
  val wholesaleMinQuantity = jsonb("wholesale_min_quantity", Json, QuantityDataModel.serializer()).nullable()

  val genericExpirationPeriod = jsonb("generic_expiration_period", Json, ExpirationPeriodDataModel.serializer()).nullable()

  val isQuickItem = bool("is_quick_item")
  val imagePaths = jsonb("image_paths", Json, ListSerializer(String.serializer()))

  val activeShelfBatchId = uuid("active_shelf_batch_id").nullable()

  val promotions = jsonb("promotions", Json, ListSerializer(StockPromotionDataModel.serializer())).default(emptyList())

  val note = text("note").nullable()
  val noteLocalized = jsonb("note_localized", Json, ListSerializer(LocalizedStringDataModel.serializer())).default(emptyList())
  val conditions = jsonb("conditions", Json, ListSerializer(String.serializer())).default(emptyList())

  val createdAtMillis = long("created_at_millis")
  val updatedAtMillis = long("updated_at_millis")
  val isActive = bool("is_active")

  override val primaryKey = PrimaryKey(id)
}

object StockBatchesV2: Table("stock_batches") {
  val id = uuid("id")
  val goodsItemId = uuid("goods_item_id").references(StockItems.id, onDelete = ReferenceOption.CASCADE)
  val userId = uuid("user_id")
  val storeId = uuid("store_id")

  val supplierId = uuid("supplier_id").nullable()
  val supplierOrderId = uuid("supplier_order_id").nullable()

  val quantity = jsonb("quantity", Json, QuantityDataModel.serializer())

  val supplyPrice = jsonb("supply_price", Json, PriceDataModel.serializer())
  val salePriceOverride = jsonb("sale_price_override", Json, PriceDataModel.serializer()).nullable()
  val returnPriceOverride = jsonb("return_price_override", Json, PriceDataModel.serializer()).nullable()
  val wholesalePriceOverride = jsonb("wholesale_price_override", Json, PriceDataModel.serializer()).nullable()

  val deliveredAtMillis = long("delivered_at_millis").nullable()
  val manufacturedAtMillis = long("manufactured_at_millis").nullable()
  val expirationDateMillis = long("expiration_date_millis").nullable()

  val discounts = jsonb("discounts", Json, ListSerializer(BatchDiscountDataModel.serializer()))
  val promotions = jsonb("promotions", Json, ListSerializer(StockPromotionDataModel.serializer())).default(emptyList())

  val shelfPosition = text("shelf_position").nullable()
  val shelfPriority = integer("shelf_priority")

  val status = text("status")
  val additionalNotes = text("additional_notes").nullable()
  val additionalNotesLocalized = jsonb("additional_notes_localized", Json, ListSerializer(LocalizedStringDataModel.serializer())).default(emptyList())

  val createdAtMillis = long("created_at_millis")
  val updatedAtMillis = long("updated_at_millis")
  val createdByUserId = uuid("created_by_user_id").nullable()

  val isActive = bool("is_active")

  override val primaryKey = PrimaryKey(id)
}

object StockBatchMovements: Table("stock_batch_movements") {
  val id = uuid("id")
  val rootStoreId = uuid("root_store_id").references(Stores.id, onDelete = ReferenceOption.CASCADE)
  val sourceStoreId = uuid("source_store_id").references(Stores.id, onDelete = ReferenceOption.CASCADE)
  val destinationStoreId = uuid("destination_store_id").references(Stores.id, onDelete = ReferenceOption.CASCADE)
  val sourceGoodsItemId = uuid("source_goods_item_id").references(StockItems.id, onDelete = ReferenceOption.CASCADE)
  val destinationGoodsItemId = uuid("destination_goods_item_id").references(StockItems.id, onDelete = ReferenceOption.CASCADE)
  val sourceBatchId = uuid("source_batch_id").references(StockBatchesV2.id, onDelete = ReferenceOption.CASCADE)
  val destinationBatchId = uuid("destination_batch_id").references(StockBatchesV2.id, onDelete = ReferenceOption.CASCADE)
  val userId = uuid("user_id").references(Users.id, onDelete = ReferenceOption.CASCADE)
  val quantity = jsonb("quantity", Json, QuantityDataModel.serializer())
  val note = text("note").nullable()
  val movedAtMillis = long("moved_at_millis")
  val status = text("status").default(StockBatchMovementStatusDataModel.Accepted.name)
  val acceptedByUserId = uuid("accepted_by_user_id").nullable()
  val acceptedAtMillis = long("accepted_at_millis").nullable()
  val decisionNote = text("decision_note").nullable()
  val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)

  override val primaryKey = PrimaryKey(id)
}

object SupplierGoodsPrices: Table("supplier_goods_prices") {
  val id = uuid("id")
  val userId = uuid("user_id")
  val storeId = uuid("store_id")
  val supplierId = uuid("supplier_id")
  val goodsItemId = uuid("goods_item_id").references(StockItems.id, onDelete = ReferenceOption.CASCADE)

  val supplyPrice = jsonb("supply_price", Json, PriceDataModel.serializer())

  val minOrderQuantity = jsonb("min_order_quantity", Json, QuantityDataModel.serializer()).nullable()
  val packageQuantity = jsonb("package_quantity", Json, QuantityDataModel.serializer()).nullable()

  val supplierBarcode = text("supplier_barcode").nullable()
  val supplierGoodsName = text("supplier_goods_name").nullable()

  val lastUsedAtMillis = long("last_used_at_millis").nullable()
  val createdAtMillis = long("created_at_millis")
  val updatedAtMillis = long("updated_at_millis")
  val isActive = bool("is_active")

  override val primaryKey = PrimaryKey(id)
}

object SupplierOrders: Table("supplier_orders") {
  val id = uuid("id")
  val userId = uuid("user_id")
  val storeId = uuid("store_id")
  val supplierId = uuid("supplier_id")

  val amount = jsonb("amount", Json, PriceDataModel.serializer()).nullable()

  val orderedAtMillis = long("ordered_at_millis")
  val desiredDeliveryTimeMillis = long("desired_delivery_time_millis").nullable()
  val confirmedDeliveryTimeMillis = long("confirmed_delivery_time_millis").nullable()
  val deliveredAtMillis = long("delivered_at_millis").nullable()

  val storeAddress = jsonb("store_address", Json, LocationDataModel.serializer()).nullable()

  val additionalNotes = text("additional_notes").nullable()
  val additionalNotesLocalized = jsonb("additional_notes_localized", Json, ListSerializer(LocalizedStringDataModel.serializer())).default(emptyList())
  val supplierComment = text("supplier_comment").nullable()
  val supplierCommentLocalized = jsonb("supplier_comment_localized", Json, ListSerializer(LocalizedStringDataModel.serializer())).default(emptyList())
  val paymentTerms = text("payment_terms").nullable()
  val externalReference = text("external_reference").nullable()
  val storeContactUserId = uuid("store_contact_user_id").nullable()
  val status = text("status")

  val createdAtMillis = long("created_at_millis")
  val updatedAtMillis = long("updated_at_millis")
  val isActive = bool("is_active")

  override val primaryKey = PrimaryKey(id)
}

object SupplierOrderLines: Table("supplier_order_lines") {
  val id = uuid("id")
  val orderId = uuid("order_id").references(SupplierOrders.id, onDelete = ReferenceOption.CASCADE)
  val goodsItemId = uuid("goods_item_id").references(StockItems.id, onDelete = ReferenceOption.CASCADE)

  val requestedQuantity = jsonb("requested_quantity", Json, QuantityDataModel.serializer())
  val expectedSupplyPrice = jsonb("expected_supply_price", Json, PriceDataModel.serializer()).nullable()

  val desiredExpirationDateMillis = long("desired_expiration_date_millis").nullable()
  val additionalNotes = text("additional_notes").nullable()
  val additionalNotesLocalized = jsonb("additional_notes_localized", Json, ListSerializer(LocalizedStringDataModel.serializer())).default(emptyList())
  val supplierComment = text("supplier_comment").nullable()
  val supplierCommentLocalized = jsonb("supplier_comment_localized", Json, ListSerializer(LocalizedStringDataModel.serializer())).default(emptyList())
  val supplierAcceptedQuantity = jsonb("supplier_accepted_quantity", Json, QuantityDataModel.serializer()).nullable()
  val supplierOfferedSupplyPrice = jsonb("supplier_offered_supply_price", Json, PriceDataModel.serializer()).nullable()
  val substituteGoodsItemId = uuid("substitute_goods_item_id").nullable()

  val deliveredBatchIds = jsonb("delivered_batch_ids", Json, ListSerializer(String.serializer()))
  val isActive = bool("is_active")

  override val primaryKey = PrimaryKey(id)
}

object SupplierPartnershipContracts: Table("supplier_partnership_contracts") {
  val id = uuid("id")
  val storeId = uuid("store_id").references(Stores.id, onDelete = ReferenceOption.CASCADE)
  val supplierId = uuid("supplier_id").references(Suppliers.id, onDelete = ReferenceOption.CASCADE)
  val authorUserId = uuid("author_user_id").references(Users.id, onDelete = ReferenceOption.CASCADE)
  val lastEditorUserId = uuid("last_editor_user_id").references(Users.id, onDelete = ReferenceOption.CASCADE)
  val authorSide = text("author_side")
  val scopeType = text("scope_type")
  val goodsItemIds = jsonb("goods_item_ids", Json, ListSerializer(String.serializer())).default(emptyList())
  val title = jsonb("title", Json, ListSerializer(LocalizedStringDataModel.serializer())).default(emptyList())
  val summary = jsonb("summary", Json, ListSerializer(LocalizedStringDataModel.serializer())).default(emptyList())
  val conditions = jsonb("conditions", Json, ListSerializer(String.serializer())).default(emptyList())
  val customTerms = jsonb("custom_terms", Json, ListSerializer(LocalizedStringDataModel.serializer())).default(emptyList())
  val deliverySchedule = jsonb("delivery_schedule", Json, ListSerializer(LocalizedStringDataModel.serializer())).default(emptyList())
  val paymentSchedule = jsonb("payment_schedule", Json, ListSerializer(LocalizedStringDataModel.serializer())).default(emptyList())
  val priceTerms = jsonb("price_terms", Json, ListSerializer(SupplierContractPriceTermDataModel.serializer())).default(emptyList())
  val status = text("status")
  val revision = integer("revision").default(1)
  val supplierAcceptedAtMillis = long("supplier_accepted_at_millis").nullable()
  val storeAcceptedAtMillis = long("store_accepted_at_millis").nullable()
  val supplierAcceptedByUserId = uuid("supplier_accepted_by_user_id").nullable()
  val storeAcceptedByUserId = uuid("store_accepted_by_user_id").nullable()
  val declinedAtMillis = long("declined_at_millis").nullable()
  val declinedByUserId = uuid("declined_by_user_id").nullable()
  val createdAtMillis = long("created_at_millis")
  val updatedAtMillis = long("updated_at_millis")
  val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
  val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)
  val isActive = bool("is_active").default(true)

  override val primaryKey = PrimaryKey(id)
}

//object Stock: Table("stock_items") {
//  val id = uuid("id").uniqueIndex()
//  val userId = uuid("user_id")
//  val storeId = uuid("store_id")
//
//  val barcode = jsonb("barcode", Json, ListSerializer(String.serializer()))
//  val name = jsonb("name", Json, ListSerializer(LocalizedStringDataModel.serializer()))
//
//  val measurementUnitId = text("measurement_unit_id")
//
//  val categoryIds = jsonb("category_ids", Json, ListSerializer(String.serializer()))
//
//  val salePrices = jsonb("sale_prices", Json, ListSerializer(PriceDataModel.serializer()))
//  val returnPrices = jsonb("return_prices", Json, ListSerializer(PriceDataModel.serializer()))
//  val supplyPrices = jsonb("supply_prices", Json, ListSerializer(PriceDataModel.serializer()))
//
//  val isQuickItem = bool("is_quick_item")
//
//  val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
//  val isActive = bool("is_active").default(true)
//
//  override val primaryKey = PrimaryKey(id)
//}
//
//object StockBatches: Table("stock_batches") {
//  val id = uuid("id").uniqueIndex()
//  val goodsItemId = uuid("goods_item_id")
//
//  val userId = uuid("user_id")
//  val storeId = uuid("store_id")
//  val supplierId = uuid("supplierId")
//
//  val salePrice = jsonb("sale_price", Json, PriceDataModel.serializer())
//  val returnPrice = jsonb("return_price", Json, PriceDataModel.serializer())
//  val supplyPrice = jsonb("supply_price", Json, PriceDataModel.serializer())
//
//  val quantity = jsonb("quantity", Json, QuantityDataModel.serializer())
//
//  val supplyTime = timestamp("supply_time")
//  val expirationTime = timestamp("expiration_time")
//  val shelfQueue = jsonb("shelf_queue", Json, GoodsBatchShelfQueueDataModel.serializer())
//
//  val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
//
//  val createdByUserId = uuid("created_by_user_id")
//
//  val isActive = bool("is_active").default(true)
//
//  override val primaryKey = PrimaryKey(id)
//}

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

object SecuritySessionEvents: Table("security_session_events") {
  val id = uuid("id")
  val userId = uuid("user_id").references(Users.id, onDelete = ReferenceOption.CASCADE).index()
  val sessionId = uuid("session_id").nullable().index()
  val eventType = text("event_type").index()
  val title = jsonb("title", Json, ListSerializer(LocalizedStringDataModel.serializer())).default(emptyList())
  val details = jsonb("details", Json, ListSerializer(LocalizedStringDataModel.serializer())).default(emptyList())
  val deviceName = text("device_name").default("")
  val platformName = text("platform_name").default("")
  val osName = text("os_name").default("")
  val appName = text("app_name").default("")
  val appVersion = text("app_version").default("")
  val ipAddress = text("ip_address").default("")
  val metadata = jsonb("metadata", Json, MapSerializer(String.serializer(), String.serializer())).default(emptyMap())
  val createdAtMillis = long("created_at_millis").index()
  val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)

  override val primaryKey = PrimaryKey(id)
}

object RealtimeUpdates: Table("realtime_updates") {

  val userId = uuid("userId").uniqueIndex()
  val updateIds = jsonb("updateIds", Json, ListSerializer(String.serializer()))

  override val primaryKey = PrimaryKey(userId)
}

object Notifications: Table("user_notifications") {
  val id = text("id")
  val userId = uuid("user_id").references(Users.id, onDelete = ReferenceOption.CASCADE).index()
  val storeId = uuid("store_id").nullable().index()
  val title = text("title").default("")
  val message = text("message")
  val type = text("type")
  val category = text("category").default("general")
  val notificationSource = text("source").default("app")
  val metadata = jsonb("meta", Json, MapSerializer(String.serializer(), String.serializer())).default(emptyMap())
  val createdAtMillis = long("created_at_millis")
  val shownAtMillis = long("shown_at_millis")
  val readAtMillis = long("read_at_millis").nullable()
  val isActive = bool("is_active").default(true)

  override val primaryKey = PrimaryKey(id)
}


object SupportTickets: Table("support_tickets") {
  val id = uuid("id").uniqueIndex()
  val publicId = text("public_id").uniqueIndex()
  val userId = uuid("user_id").references(Users.id, onDelete = ReferenceOption.CASCADE).index()
  val storeId = uuid("store_id").nullable().index()
  val subject = text("subject")
  val category = text("category").default("general")
  val priority = text("priority").default("normal")
  val status = text("status").default("open").index()
  val assignedAgentUserId = uuid("assigned_agent_user_id").nullable().index()
  val lastMessage = text("last_message").default("")
  val lastMessageAtMillis = long("last_message_at_millis").default(0L)
  val lastCustomerMessageAtMillis = long("last_customer_message_at_millis").nullable()
  val lastAgentMessageAtMillis = long("last_agent_message_at_millis").nullable()
  val unreadForUserCount = integer("unread_for_user_count").default(0)
  val unreadForAgentCount = integer("unread_for_agent_count").default(0)
  val metadata = jsonb("meta", Json, MapSerializer(String.serializer(), String.serializer())).default(emptyMap())
  val createdAtMillis = long("created_at_millis")
  val updatedAtMillis = long("updated_at_millis")
  val closedAtMillis = long("closed_at_millis").nullable()
  val isActive = bool("is_active").default(true)

  override val primaryKey = PrimaryKey(id)
}

object SupportMessages: Table("support_messages") {
  val id = uuid("id").uniqueIndex()
  val ticketId = uuid("ticket_id").references(SupportTickets.id, onDelete = ReferenceOption.CASCADE).index()
  val userId = uuid("user_id").references(Users.id, onDelete = ReferenceOption.CASCADE).index()
  val senderUserId = uuid("sender_user_id").references(Users.id, onDelete = ReferenceOption.CASCADE).index()
  val senderRole = text("sender_role").default("customer").index()
  val senderDisplayName = text("sender_display_name").default("")
  val body = text("body")
  val attachments = jsonb("attachments", Json, ListSerializer(String.serializer())).default(emptyList())
  val metadata = jsonb("meta", Json, MapSerializer(String.serializer(), String.serializer())).default(emptyMap())
  val clientMessageId = text("client_message_id").nullable().uniqueIndex()
  val createdAtMillis = long("created_at_millis")
  val editedAtMillis = long("edited_at_millis").nullable()
  val readByCustomerAtMillis = long("read_by_customer_at_millis").nullable()
  val readByAgentAtMillis = long("read_by_agent_at_millis").nullable()
  val isActive = bool("is_active").default(true)

  override val primaryKey = PrimaryKey(id)
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

object GenericGoodsItemCandidates: Table("generic_goods_item_candidates") {
  val id = uuid("id").uniqueIndex()
  val normalizedBarcode = text("normalized_barcode").index()
  val barcode = text("barcode")
  val commonKeywords = jsonb("common_keywords", Json, ListSerializer(String.serializer())).default(emptyList())
  val sourceUserIds = jsonb("source_user_ids", Json, ListSerializer(String.serializer())).default(emptyList())
  val sourceStockItemIds = jsonb("source_stock_item_ids", Json, ListSerializer(String.serializer())).default(emptyList())
  val submissionCount = integer("submission_count").default(0)
  val status = text("status").default("waiting")
  val promotedGenericGoodsItemId = uuid("promoted_generic_goods_item_id").nullable()
  val createdAtMillis = long("created_at_millis").default(0L)
  val updatedAtMillis = long("updated_at_millis").default(0L)
  val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
  val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)

  override val primaryKey = PrimaryKey(id)
}

object GenericGoodsCategories: Table("generic_goods_categories") {
  val id = uuid("id").uniqueIndex()

  val typeIds = jsonb("type_ids", Json, ListSerializer(String.serializer()))
  val name = jsonb("name", Json, ListSerializer(LocalizedStringDataModel.serializer()))
  val alias = jsonb("alias", Json, ListSerializer(LocalizedStringDataModel.serializer())).nullable()
  val description = jsonb("description", Json, ListSerializer(LocalizedStringDataModel.serializer())).nullable()
  val quantityUnitId = text("quantity_unit_id")
  val imagePaths = jsonb("image_paths", Json, ListSerializer(StylizedDrawablePathsGroupDataModel.serializer()))
  val conditions = jsonb("conditions", Json, ListSerializer(String.serializer())).default(emptyList())

  override val primaryKey = PrimaryKey(id)
}


private const val STOCK_CONDITION_STORAGE_PREFIX = "aita-stock-condition-v1:"
private const val STOCK_CONDITION_KIND_BUYER_MINIMUM_AGE = "buyer_minimum_age"
private const val STOCK_CONDITION_KIND_TRANSACTION_TIME_WINDOW = "transaction_time_window"

private const val GENERIC_GOODS_CANDIDATE_STATUS_WAITING = "waiting"
private const val GENERIC_GOODS_CANDIDATE_STATUS_PROMOTED = "promoted"
private const val DEFAULT_GENERIC_GOODS_REQUIRED_OTHER_USERS_FOR_TESTING = 1
private const val PRODUCTION_GENERIC_GOODS_REQUIRED_OTHER_USERS_HINT = 100

private fun genericGoodsRequiredOtherUsers(): Int =
  envOrSystem("AITA_GENERIC_GOODS_REQUIRED_OTHER_USERS")
    ?.toIntOrNull()
    ?.coerceAtLeast(1)
    ?: DEFAULT_GENERIC_GOODS_REQUIRED_OTHER_USERS_FOR_TESTING

private val genericGoodsStopWords = setOf(
  "and", "the", "for", "with", "from", "item", "goods", "product",
  "и", "или", "для", "товар", "продукт", "из", "с", "со", "на", "в", "во",
  "және", "үшін", "тауар", "өнім"
)

private fun List<LocalizedStringDataModel>.genericGoodsKeywords(): List<String> =
  asSequence()
    .flatMap { localized ->
      localized.value
        .lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .split(Regex("\\s+"))
        .asSequence()
    }
    .map { it.trim() }
    .filter { it.length >= 2 }
    .filter { it !in genericGoodsStopWords }
    .distinct()
    .toList()

private fun genericGoodsNameFromKeywords(keywords: List<String>, fallbackBarcode: String): List<LocalizedStringDataModel> {
  val title = keywords
    .take(7)
    .joinToString(" ")
    .replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase(Locale.ROOT) else char.toString() }
    .ifBlank { fallbackBarcode }
  return listOf("main", "en", "ru", "kk").map { language -> LocalizedStringDataModel(language, title) }
}

private fun ResultRow.genericGoodsCandidateCommonKeywords(): List<String> =
  this[GenericGoodsItemCandidates.commonKeywords].map { it.trim().lowercase(Locale.ROOT) }.filter { it.isNotBlank() }.distinct()

private fun ResultRow.genericGoodsCandidateSourceUserIds(): List<String> =
  this[GenericGoodsItemCandidates.sourceUserIds].map { it.trim() }.filter { it.isNotBlank() }.distinct()

private fun ResultRow.genericGoodsCandidateSourceStockItemIds(): List<String> =
  this[GenericGoodsItemCandidates.sourceStockItemIds].map { it.trim() }.filter { it.isNotBlank() }.distinct()

private fun existingGenericGoodsItemIdForBarcodeInsideTransaction(normalizedBarcode: String): UUID? =
  GenericGoodsItems
    .select(GenericGoodsItems.id, GenericGoodsItems.barcode)
    .where { GenericGoodsItems.barcode.contains(listOf(normalizedBarcode)) }
    .limit(1)
    .firstOrNull()
    ?.get(GenericGoodsItems.id)

private fun promoteGenericGoodsCandidateInsideTransaction(
  candidateId: UUID,
  normalizedBarcode: String,
  keywords: List<String>,
  categoryIds: List<String>,
  now: Long
): UUID {
  val genericId = existingGenericGoodsItemIdForBarcodeInsideTransaction(normalizedBarcode) ?: UUID.randomUUID().also { newGenericId ->
    GenericGoodsItems.insert {
      it[GenericGoodsItems.id] = newGenericId
      it[GenericGoodsItems.barcode] = listOf(normalizedBarcode)
      it[GenericGoodsItems.name] = jsonBase.encodeToString(genericGoodsNameFromKeywords(keywords, normalizedBarcode))
      it[GenericGoodsItems.typeIds] = null
      it[GenericGoodsItems.categoryIds] = categoryIds.takeIf { ids -> ids.isNotEmpty() }?.let { ids -> jsonBase.encodeToString(ids.distinct()) }
      it[GenericGoodsItems.supplierIds] = null
      it[GenericGoodsItems.manufacturerIds] = null
    }
  }

  GenericGoodsItemCandidates.update({ GenericGoodsItemCandidates.id eq candidateId }) {
    it[GenericGoodsItemCandidates.status] = GENERIC_GOODS_CANDIDATE_STATUS_PROMOTED
    it[GenericGoodsItemCandidates.promotedGenericGoodsItemId] = genericId
    it[GenericGoodsItemCandidates.updatedAtMillis] = now
  }

  return genericId
}

private fun recordGenericGoodsContributionInsideTransaction(
  userId: UUID,
  stockItemId: UUID,
  item: GoodsItemDataModel,
  barcodeModels: List<GoodsItemBarcodeDataModel>,
  now: Long
) {
  val keywords = item.name.genericGoodsKeywords()
  if (keywords.size < 3) return

  val standardBarcodes = barcodeModels
    .filter { barcode -> barcode.type.normalizedGoodsItemBarcodeType(barcode.value) == GOODS_ITEM_BARCODE_TYPE_STANDARD }
    .flatMap { barcode -> barcode.value.toStoredGoodsItemBarcodeCandidates() }
    .map { barcode -> barcode.toStoredGoodsItemBarcode() }
    .filter { barcode -> barcode.isNotBlank() }
    .distinct()

  if (standardBarcodes.isEmpty()) return

  val userIdText = userId.toString()
  val stockItemIdText = stockItemId.toString()
  val requiredDistinctUsers = genericGoodsRequiredOtherUsers() + 1

  standardBarcodes.forEach { normalizedBarcode ->
    if (existingGenericGoodsItemIdForBarcodeInsideTransaction(normalizedBarcode) != null) return@forEach

    val existingCandidate = GenericGoodsItemCandidates
      .selectAll()
      .where {
        (GenericGoodsItemCandidates.normalizedBarcode eq normalizedBarcode) and
           (GenericGoodsItemCandidates.status eq GENERIC_GOODS_CANDIDATE_STATUS_WAITING)
      }
      .toList()
      .firstOrNull { row ->
        val common = row.genericGoodsCandidateCommonKeywords()
        common.isEmpty() || common.intersect(keywords.toSet()).size >= 3
      }

    val candidateId: UUID
    val mergedKeywords: List<String>
    val mergedUserIds: List<String>

    if (existingCandidate == null) {
      candidateId = UUID.randomUUID()
      mergedKeywords = keywords.take(12)
      mergedUserIds = listOf(userIdText)
      GenericGoodsItemCandidates.insert {
        it[GenericGoodsItemCandidates.id] = candidateId
        it[GenericGoodsItemCandidates.normalizedBarcode] = normalizedBarcode
        it[GenericGoodsItemCandidates.barcode] = normalizedBarcode
        it[GenericGoodsItemCandidates.commonKeywords] = mergedKeywords
        it[GenericGoodsItemCandidates.sourceUserIds] = mergedUserIds
        it[GenericGoodsItemCandidates.sourceStockItemIds] = listOf(stockItemIdText)
        it[GenericGoodsItemCandidates.submissionCount] = 1
        it[GenericGoodsItemCandidates.status] = GENERIC_GOODS_CANDIDATE_STATUS_WAITING
        it[GenericGoodsItemCandidates.promotedGenericGoodsItemId] = null
        it[GenericGoodsItemCandidates.createdAtMillis] = now
        it[GenericGoodsItemCandidates.updatedAtMillis] = now
      }
    } else {
      candidateId = existingCandidate[GenericGoodsItemCandidates.id]
      val previousKeywords = existingCandidate.genericGoodsCandidateCommonKeywords()
      val intersection = previousKeywords.intersect(keywords.toSet()).toList()
      mergedKeywords = if (intersection.size >= 3) intersection else previousKeywords.ifEmpty { keywords.take(12) }
      mergedUserIds = (existingCandidate.genericGoodsCandidateSourceUserIds() + userIdText).distinct()
      val mergedStockItemIds = (existingCandidate.genericGoodsCandidateSourceStockItemIds() + stockItemIdText).distinct()

      GenericGoodsItemCandidates.update({ GenericGoodsItemCandidates.id eq candidateId }) {
        it[GenericGoodsItemCandidates.commonKeywords] = mergedKeywords
        it[GenericGoodsItemCandidates.sourceUserIds] = mergedUserIds
        it[GenericGoodsItemCandidates.sourceStockItemIds] = mergedStockItemIds
        it[GenericGoodsItemCandidates.submissionCount] = existingCandidate[GenericGoodsItemCandidates.submissionCount] + 1
        it[GenericGoodsItemCandidates.updatedAtMillis] = now
      }
    }

    if (mergedUserIds.size >= requiredDistinctUsers) {
      promoteGenericGoodsCandidateInsideTransaction(
        candidateId = candidateId,
        normalizedBarcode = normalizedBarcode,
        keywords = mergedKeywords,
        categoryIds = item.categoryIds,
        now = now
      )
    }
  }
}


private fun ResultRow.toGenericGoodsItemDataModel(): GenericGoodsItemDataModel = GenericGoodsItemDataModel(
  id = this[GenericGoodsItems.id].toString(),
  barcode = this[GenericGoodsItems.barcode],
  name = this[GenericGoodsItems.name].let { value ->
    runCatching { jsonBase.decodeFromString<List<LocalizedStringDataModel>>(value) }.getOrDefault(emptyList())
  },
  typeIds = this[GenericGoodsItems.typeIds]?.let { value ->
    runCatching { jsonBase.decodeFromString<List<String>>(value) }.getOrDefault(emptyList())
  },
  categoryIds = this[GenericGoodsItems.categoryIds]?.let { value ->
    runCatching { jsonBase.decodeFromString<List<String>>(value) }.getOrDefault(emptyList())
  },
  supplierIds = this[GenericGoodsItems.supplierIds]?.let { value ->
    runCatching { jsonBase.decodeFromString<List<String>>(value) }.getOrDefault(emptyList())
  },
  manufacturerIds = this[GenericGoodsItems.manufacturerIds]?.let { value ->
    runCatching { jsonBase.decodeFromString<List<String>>(value) }.getOrDefault(emptyList())
  }
)

private fun genericGoodsBarcodeCandidates(raw: String?): List<String> =
  raw
    ?.trim()
    ?.takeIf { it.isNotBlank() }
    ?.let { value -> value.toStoredGoodsItemBarcodeCandidates() + listOf(value.toStoredGoodsItemBarcode()) }
    .orEmpty()
    .map { it.toStoredGoodsItemBarcode() }
    .filter { it.isNotBlank() }
    .distinct()

private fun genericGoodsSearchTokens(raw: String?): List<String> =
  raw
    ?.lowercase(Locale.ROOT)
    ?.replace(Regex("[^\\p{L}\\p{N}]+"), " ")
    ?.split(Regex("\\s+"))
    .orEmpty()
    .map { it.trim() }
    .filter { it.length >= 2 }
    .distinct()
    .take(8)

private fun GenericGoodsItemDataModel.matchesGenericGoodsSearchQuery(rawQuery: String?): Boolean {
  val tokens = genericGoodsSearchTokens(rawQuery)
  if (tokens.isEmpty()) return true
  val haystack = buildString {
    append(id.lowercase(Locale.ROOT))
    append(' ')
    append(barcode.orEmpty().joinToString(" ").lowercase(Locale.ROOT))
    append(' ')
    append(name.joinToString(" ") { it.value }.lowercase(Locale.ROOT))
    append(' ')
    append(categoryIds.orEmpty().joinToString(" ").lowercase(Locale.ROOT))
  }
  return tokens.all { token -> haystack.contains(token) }
}

private fun GenericGoodsItemDataModel.genericGoodsBarcodeMatchScore(barcodeCandidates: List<String>): Int {
  if (barcodeCandidates.isEmpty()) return 0
  val stored = barcode.orEmpty().flatMap { genericGoodsBarcodeCandidates(it) }.toSet()
  return if (stored.any { it in barcodeCandidates }) 1 else 0
}

private fun encodedSeedStockCondition(
  kind: String,
  minimumAge: Int = 18,
  startsAtMinutes: Int = 6 * 60,
  endsAtMinutes: Int = 22 * 60,
  transactionTypeIndex: Int = 0
): String = STOCK_CONDITION_STORAGE_PREFIX +
   "{\"kind\":\"$kind\",\"transactionTypeIndex\":$transactionTypeIndex,\"text\":[],\"minimumAge\":$minimumAge,\"startsAtMinutes\":$startsAtMinutes,\"endsAtMinutes\":$endsAtMinutes}"

private fun defaultAgeRestrictedAlcoholCategoryConditions(): List<String> = listOf(
  encodedSeedStockCondition(STOCK_CONDITION_KIND_BUYER_MINIMUM_AGE, minimumAge = 21),
  encodedSeedStockCondition(STOCK_CONDITION_KIND_TRANSACTION_TIME_WINDOW, startsAtMinutes = 6 * 60, endsAtMinutes = 22 * 60)
)

private data class GenericGoodsCategorySeed(
  val slug: String,
  val typeIds: List<String>,
  val name: List<LocalizedStringDataModel>,
  val quantityUnitId: String,
  val alias: List<LocalizedStringDataModel>? = null,
  val description: List<LocalizedStringDataModel>? = null,
  val imagePaths: List<StylizedDrawablePathsGroupDataModel> = emptyList(),
  val conditions: List<String> = emptyList()
)

private fun categoryText(
  en: String,
  ru: String,
  kk: String,
  main: String = en
): List<LocalizedStringDataModel> = listOf(
  LocalizedStringDataModel("main", main),
  LocalizedStringDataModel("en", en),
  LocalizedStringDataModel("ru", ru),
  LocalizedStringDataModel("kk", kk)
)

private fun String.withoutSeededGoodsCategoryPrefix(): String {
  return trim()
    .replace(
      Regex(
        pattern = """^(Goods\s+(categor(?:y|ies)|section)|Product\s+category|Category|Категория\s+товаров|Раздел\s+товар(?:ов|а)?|Товарный\s+раздел|Категория|Тауар(?:лар)?\s+(санаты|бөлімі)|Товар(?:лар)?\s+(санаты|бөлімі)|Өнім(?:дер)?\s+(санаты|бөлімі)|Санат|Бөлім)\s*[:：\-—]?\s*""",
        option = RegexOption.IGNORE_CASE
      ),
      ""
    )
    .trim()
    .trimStart(':', '：', '-', '—')
    .trim()
}

private fun List<LocalizedStringDataModel>.cleanSeededGoodsCategoryPrefixes(): List<LocalizedStringDataModel> =
  map { it.copy(value = it.value.withoutSeededGoodsCategoryPrefix()) }

private fun genericGoodsCategorySeedId(slug: String): UUID =
  UUID.nameUUIDFromBytes("aita:generic-goods-category:$slug".toByteArray(StandardCharsets.UTF_8))

private fun genericGoodsCategoryNameKey(values: List<LocalizedStringDataModel>): String {
  return (
     values.firstOrNull { it.language.equals("en", true) }
       ?: values.firstOrNull { it.language.equals("main", true) }
       ?: values.firstOrNull()
     )
    ?.value
    ?.withoutSeededGoodsCategoryPrefix()
    ?.lowercase()
    .orEmpty()
}

private val seededGenericGoodsCategoryRootSlugs = setOf(
  "food",
  "bakery",
  "dairy",
  "plant_based",
  "meat",
  "seafood",
  "eggs",
  "grocery_staples",
  "beverages",
  "snacks",
  "sweets",
  "frozen",
  "ready_meals",
  "baby",
  "pet",
  "household",
  "personal_care",
  "health",
  "stationery",
  "electronics",
  "clothing",
  "toys",
  "garden",
  "tools",
  "auto",
  "regulated",
  "seasonal"
)

private fun seededGenericGoodsCategoryRootSlug(slug: String): String? {
  if (slug == "grocery_staples" || slug.startsWith("grocery_")) return "grocery_staples"

  return seededGenericGoodsCategoryRootSlugs
    .filter { root -> slug == root || slug.startsWith("${root}_") }
    .maxByOrNull { it.length }
}

private fun GenericGoodsCategorySeed.parentCategoryIds(): List<String> {
  val rootSlug = seededGenericGoodsCategoryRootSlug(slug) ?: return emptyList()
  return if (rootSlug == slug) {
    emptyList()
  } else {
    listOf(genericGoodsCategorySeedId(rootSlug).toString())
  }
}

private fun defaultGenericGoodsCategorySeeds(): List<GenericGoodsCategorySeed> = listOf(
  GenericGoodsCategorySeed("food", listOf("food"), categoryText("Food", "Продукты питания", "Азық-түлік"), "1"),
  GenericGoodsCategorySeed("food_fresh_produce", listOf("food", "fresh"), categoryText("Food / Fresh produce", "Продукты / Свежие овощи и фрукты", "Азық-түлік / Жаңа көкөніс пен жеміс"), "1"),
  GenericGoodsCategorySeed("food_fruits", listOf("food", "fresh", "fruit"), categoryText("Food / Fruits", "Продукты / Фрукты", "Азық-түлік / Жемістер"), "1"),
  GenericGoodsCategorySeed("food_fruits_apples_pears", listOf("food", "fruit", "apple", "pear"), categoryText("Food / Fruits / Apples and pears", "Продукты / Фрукты / Яблоки и груши", "Азық-түлік / Жемістер / Алма және алмұрт"), "1"),
  GenericGoodsCategorySeed("food_fruits_citrus", listOf("food", "fruit", "citrus"), categoryText("Food / Fruits / Citrus", "Продукты / Фрукты / Цитрусовые", "Азық-түлік / Жемістер / Цитрус"), "1"),
  GenericGoodsCategorySeed("food_fruits_berries", listOf("food", "fruit", "berries"), categoryText("Food / Fruits / Berries", "Продукты / Фрукты / Ягоды", "Азық-түлік / Жемістер / Жидектер"), "1"),
  GenericGoodsCategorySeed("food_fruits_bananas", listOf("food", "fruit", "banana"), categoryText("Food / Fruits / Bananas", "Продукты / Фрукты / Бананы", "Азық-түлік / Жемістер / Банан"), "1"),
  GenericGoodsCategorySeed("food_fruits_grapes", listOf("food", "fruit", "grape"), categoryText("Food / Fruits / Grapes", "Продукты / Фрукты / Виноград", "Азық-түлік / Жемістер / Жүзім"), "1"),
  GenericGoodsCategorySeed("food_fruits_melons", listOf("food", "fruit", "melon"), categoryText("Food / Fruits / Melons", "Продукты / Фрукты / Дыни и арбузы", "Азық-түлік / Жемістер / Қауын және қарбыз"), "1"),
  GenericGoodsCategorySeed("food_vegetables", listOf("food", "vegetable"), categoryText("Food / Vegetables", "Продукты / Овощи", "Азық-түлік / Көкөністер"), "1"),
  GenericGoodsCategorySeed("food_vegetables_leafy", listOf("food", "vegetable", "greens"), categoryText("Food / Vegetables / Leafy greens", "Продукты / Овощи / Листовая зелень", "Азық-түлік / Көкөністер / Жапырақты көк"), "1"),
  GenericGoodsCategorySeed("food_vegetables_root", listOf("food", "vegetable", "root"), categoryText("Food / Vegetables / Root vegetables", "Продукты / Овощи / Корнеплоды", "Азық-түлік / Көкөністер / Тамыржемістер"), "1"),
  GenericGoodsCategorySeed("food_vegetables_tomatoes_cucumbers", listOf("food", "vegetable", "tomato", "cucumber"), categoryText("Food / Vegetables / Tomatoes and cucumbers", "Продукты / Овощи / Помидоры и огурцы", "Азық-түлік / Көкөністер / Қызанақ және қияр"), "1"),
  GenericGoodsCategorySeed("food_vegetables_onion_garlic", listOf("food", "vegetable", "onion", "garlic"), categoryText("Food / Vegetables / Onion and garlic", "Продукты / Овощи / Лук и чеснок", "Азық-түлік / Көкөністер / Пияз және сарымсақ"), "1"),
  GenericGoodsCategorySeed("food_vegetables_cabbage", listOf("food", "vegetable", "cabbage"), categoryText("Food / Vegetables / Cabbage", "Продукты / Овощи / Капуста", "Азық-түлік / Көкөністер / Қырыққабат"), "1"),
  GenericGoodsCategorySeed("food_herbs", listOf("food", "herbs"), categoryText("Food / Herbs", "Продукты / Зелень и травы", "Азық-түлік / Көк және шөптер"), "1"),
  GenericGoodsCategorySeed("food_mushrooms", listOf("food", "mushrooms"), categoryText("Food / Mushrooms", "Продукты / Грибы", "Азық-түлік / Саңырауқұлақтар"), "1"),
  GenericGoodsCategorySeed("bakery", listOf("food", "bakery"), categoryText("Bakery", "Выпечка и хлеб", "Нан-тоқаш өнімдері"), "0"),
  GenericGoodsCategorySeed("bakery_bread", listOf("food", "bakery", "bread"), categoryText("Bakery / Bread", "Выпечка / Хлеб", "Нан-тоқаш / Нан"), "0"),
  GenericGoodsCategorySeed("bakery_flatbread", listOf("food", "bakery", "flatbread"), categoryText("Bakery / Flatbread and lavash", "Выпечка / Лепёшки и лаваш", "Нан-тоқаш / Тандыр нан және лаваш"), "0"),
  GenericGoodsCategorySeed("bakery_buns", listOf("food", "bakery", "buns"), categoryText("Bakery / Buns", "Выпечка / Булочки", "Нан-тоқаш / Тоқаштар"), "0"),
  GenericGoodsCategorySeed("bakery_pastry", listOf("food", "bakery", "pastry"), categoryText("Bakery / Pastry", "Выпечка / Слойки и пирожки", "Нан-тоқаш / Қаттама және бәліштер"), "0"),
  GenericGoodsCategorySeed("bakery_cakes", listOf("food", "bakery", "cake"), categoryText("Bakery / Cakes and desserts", "Выпечка / Торты и десерты", "Нан-тоқаш / Торттар және десерттер"), "0"),
  GenericGoodsCategorySeed("bakery_cookies", listOf("food", "bakery", "cookies"), categoryText("Bakery / Cookies and biscuits", "Выпечка / Печенье и бисквиты", "Нан-тоқаш / Печенье және бисквиттер"), "0"),
  GenericGoodsCategorySeed("dairy", listOf("food", "dairy"), categoryText("Dairy and alternatives", "Молочные продукты и альтернативы", "Сүт өнімдері және баламалар"), "0"),
  GenericGoodsCategorySeed("dairy_milk", listOf("food", "dairy", "milk"), categoryText("Dairy / Milk", "Молочные продукты / Молоко", "Сүт өнімдері / Сүт"), "0"),
  GenericGoodsCategorySeed("dairy_yogurt", listOf("food", "dairy", "yogurt"), categoryText("Dairy / Yogurt and kefir", "Молочные продукты / Йогурт и кефир", "Сүт өнімдері / Йогурт және айран"), "0"),
  GenericGoodsCategorySeed("dairy_cheese", listOf("food", "dairy", "cheese"), categoryText("Dairy / Cheese", "Молочные продукты / Сыр", "Сүт өнімдері / Ірімшік"), "0"),
  GenericGoodsCategorySeed("dairy_cottage_cheese", listOf("food", "dairy", "cottage_cheese"), categoryText("Dairy / Cottage cheese", "Молочные продукты / Творог", "Сүт өнімдері / Сүзбе"), "0"),
  GenericGoodsCategorySeed("dairy_butter_cream", listOf("food", "dairy", "butter", "cream"), categoryText("Dairy / Butter and cream", "Молочные продукты / Масло и сливки", "Сүт өнімдері / Май және кілегей"), "0"),
  GenericGoodsCategorySeed("plant_based", listOf("food", "plant_based"), categoryText("Plant-based foods", "Растительные продукты", "Өсімдік негізіндегі өнімдер"), "0"),
  GenericGoodsCategorySeed("plant_based_milk", listOf("food", "plant_based", "milk"), categoryText("Plant-based / Milk alternatives", "Растительные продукты / Растительное молоко", "Өсімдік өнімдері / Өсімдік сүті"), "0"),
  GenericGoodsCategorySeed("plant_based_tofu", listOf("food", "plant_based", "tofu"), categoryText("Plant-based / Tofu and tempeh", "Растительные продукты / Тофу и темпе", "Өсімдік өнімдері / Тофу және темпе"), "0"),
  GenericGoodsCategorySeed("plant_based_meat", listOf("food", "plant_based", "meat_alternative"), categoryText("Plant-based / Meat alternatives", "Растительные продукты / Альтернативы мясу", "Өсімдік өнімдері / Ет баламалары"), "0"),
  GenericGoodsCategorySeed("plant_based_cheese", listOf("food", "plant_based", "cheese_alternative"), categoryText("Plant-based / Cheese alternatives", "Растительные продукты / Альтернативы сыру", "Өсімдік өнімдері / Ірімшік баламалары"), "0"),
  GenericGoodsCategorySeed("meat", listOf("food", "meat"), categoryText("Meat", "Мясо", "Ет"), "1"),
  GenericGoodsCategorySeed("meat_beef", listOf("food", "meat", "beef"), categoryText("Meat / Beef", "Мясо / Говядина", "Ет / Сиыр еті"), "1"),
  GenericGoodsCategorySeed("meat_lamb", listOf("food", "meat", "lamb"), categoryText("Meat / Lamb", "Мясо / Баранина", "Ет / Қой еті"), "1"),
  GenericGoodsCategorySeed("meat_poultry", listOf("food", "meat", "poultry"), categoryText("Meat / Poultry", "Мясо / Птица", "Ет / Құс еті"), "1"),
  GenericGoodsCategorySeed("meat_sausages", listOf("food", "meat", "sausage"), categoryText("Meat / Sausages and deli", "Мясо / Колбасы и деликатесы", "Ет / Шұжық және деликатестер"), "0"),
  GenericGoodsCategorySeed("seafood", listOf("food", "seafood"), categoryText("Fish and seafood", "Рыба и морепродукты", "Балық және теңіз өнімдері"), "1"),
  GenericGoodsCategorySeed("seafood_fish", listOf("food", "seafood", "fish"), categoryText("Seafood / Fish", "Морепродукты / Рыба", "Теңіз өнімдері / Балық"), "1"),
  GenericGoodsCategorySeed("seafood_canned", listOf("food", "seafood", "canned"), categoryText("Seafood / Canned fish", "Морепродукты / Рыбные консервы", "Теңіз өнімдері / Балық консервілері"), "0"),
  GenericGoodsCategorySeed("eggs", listOf("food", "eggs"), categoryText("Eggs", "Яйца", "Жұмыртқа"), "0"),
  GenericGoodsCategorySeed("grocery_staples", listOf("food", "grocery"), categoryText("Grocery staples", "Бакалея", "Бакалея"), "0"),
  GenericGoodsCategorySeed("grocery_grains", listOf("food", "grocery", "grains"), categoryText("Grocery / Grains and cereals", "Бакалея / Крупы", "Бакалея / Жармалар"), "1"),
  GenericGoodsCategorySeed("grocery_rice", listOf("food", "grocery", "rice"), categoryText("Grocery / Rice", "Бакалея / Рис", "Бакалея / Күріш"), "1"),
  GenericGoodsCategorySeed("grocery_pasta", listOf("food", "grocery", "pasta"), categoryText("Grocery / Pasta and noodles", "Бакалея / Макароны и лапша", "Бакалея / Макарон және кеспе"), "0"),
  GenericGoodsCategorySeed("grocery_flour", listOf("food", "grocery", "flour"), categoryText("Grocery / Flour and baking mixes", "Бакалея / Мука и смеси для выпечки", "Бакалея / Ұн және пісіру қоспалары"), "1"),
  GenericGoodsCategorySeed("grocery_sugar", listOf("food", "grocery", "sugar"), categoryText("Grocery / Sugar and sweeteners", "Бакалея / Сахар и подсластители", "Бакалея / Қант және тәттілендіргіштер"), "1"),
  GenericGoodsCategorySeed("grocery_salt", listOf("food", "grocery", "salt"), categoryText("Grocery / Salt", "Бакалея / Соль", "Бакалея / Тұз"), "1"),
  GenericGoodsCategorySeed("grocery_spices", listOf("food", "grocery", "spices"), categoryText("Grocery / Spices and seasonings", "Бакалея / Специи и приправы", "Бакалея / Дәмдеуіштер"), "0"),
  GenericGoodsCategorySeed("grocery_oil", listOf("food", "grocery", "oil"), categoryText("Grocery / Oils", "Бакалея / Масла", "Бакалея / Майлар"), "0"),
  GenericGoodsCategorySeed("grocery_sauces", listOf("food", "grocery", "sauce"), categoryText("Grocery / Sauces and dressings", "Бакалея / Соусы и заправки", "Бакалея / Тұздықтар"), "0"),
  GenericGoodsCategorySeed("grocery_canned", listOf("food", "grocery", "canned"), categoryText("Grocery / Canned food", "Бакалея / Консервы", "Бакалея / Консервілер"), "0"),
  GenericGoodsCategorySeed("grocery_pickles", listOf("food", "grocery", "pickles"), categoryText("Grocery / Pickles and marinades", "Бакалея / Соленья и маринады", "Бакалея / Тұздалған және маринадталған өнімдер"), "0"),
  GenericGoodsCategorySeed("grocery_breakfast", listOf("food", "grocery", "breakfast"), categoryText("Grocery / Breakfast foods", "Бакалея / Завтраки", "Бакалея / Таңғы ас өнімдері"), "0"),
  GenericGoodsCategorySeed("grocery_instant", listOf("food", "grocery", "instant"), categoryText("Grocery / Instant foods", "Бакалея / Быстрое питание", "Бакалея / Жылдам дайындалатын тағамдар"), "0"),
  GenericGoodsCategorySeed("beverages", listOf("food", "beverage"), categoryText("Beverages", "Напитки", "Сусындар"), "0"),
  GenericGoodsCategorySeed("beverages_water", listOf("food", "beverage", "water"), categoryText("Beverages / Water", "Напитки / Вода", "Сусындар / Су"), "0"),
  GenericGoodsCategorySeed("beverages_juice", listOf("food", "beverage", "juice"), categoryText("Beverages / Juice and nectar", "Напитки / Соки и нектары", "Сусындар / Шырын және нектар"), "0"),
  GenericGoodsCategorySeed("beverages_soda", listOf("food", "beverage", "soda"), categoryText("Beverages / Soda", "Напитки / Газировка", "Сусындар / Газдалған сусын"), "0"),
  GenericGoodsCategorySeed("beverages_tea", listOf("food", "beverage", "tea"), categoryText("Beverages / Tea", "Напитки / Чай", "Сусындар / Шай"), "0"),
  GenericGoodsCategorySeed("beverages_coffee", listOf("food", "beverage", "coffee"), categoryText("Beverages / Coffee", "Напитки / Кофе", "Сусындар / Кофе"), "0"),
  GenericGoodsCategorySeed("beverages_energy", listOf("food", "beverage", "energy"), categoryText("Beverages / Energy drinks", "Напитки / Энергетики", "Сусындар / Энергетикалық сусындар"), "0"),
  GenericGoodsCategorySeed("snacks", listOf("food", "snacks"), categoryText("Snacks", "Снеки", "Тіскебасарлар"), "0"),
  GenericGoodsCategorySeed("snacks_chips", listOf("food", "snacks", "chips"), categoryText("Snacks / Chips and crisps", "Снеки / Чипсы", "Тіскебасар / Чипстер"), "0"),
  GenericGoodsCategorySeed("snacks_nuts", listOf("food", "snacks", "nuts"), categoryText("Snacks / Nuts and seeds", "Снеки / Орехи и семечки", "Тіскебасар / Жаңғақ және дәндер"), "1"),
  GenericGoodsCategorySeed("snacks_crackers", listOf("food", "snacks", "crackers"), categoryText("Snacks / Crackers and breadsticks", "Снеки / Крекеры и хлебцы", "Тіскебасар / Крекер және қытырлақ нан"), "0"),
  GenericGoodsCategorySeed("sweets", listOf("food", "sweets"), categoryText("Sweets", "Сладости", "Тәттілер"), "0"),
  GenericGoodsCategorySeed("sweets_chocolate", listOf("food", "sweets", "chocolate"), categoryText("Sweets / Chocolate", "Сладости / Шоколад", "Тәттілер / Шоколад"), "0"),
  GenericGoodsCategorySeed("sweets_candy", listOf("food", "sweets", "candy"), categoryText("Sweets / Candy", "Сладости / Конфеты", "Тәттілер / Кәмпиттер"), "0"),
  GenericGoodsCategorySeed("sweets_cookies", listOf("food", "sweets", "cookies"), categoryText("Sweets / Cookies and wafers", "Сладости / Печенье и вафли", "Тәттілер / Печенье және вафли"), "0"),
  GenericGoodsCategorySeed("sweets_ice_cream", listOf("food", "sweets", "ice_cream"), categoryText("Sweets / Ice cream", "Сладости / Мороженое", "Тәттілер / Балмұздақ"), "0"),
  GenericGoodsCategorySeed("frozen", listOf("food", "frozen"), categoryText("Frozen foods", "Замороженные продукты", "Мұздатылған өнімдер"), "0"),
  GenericGoodsCategorySeed("frozen_vegetables", listOf("food", "frozen", "vegetables"), categoryText("Frozen / Vegetables and berries", "Заморозка / Овощи и ягоды", "Мұздатылған / Көкөніс және жидек"), "0"),
  GenericGoodsCategorySeed("frozen_ready_meals", listOf("food", "frozen", "ready_meals"), categoryText("Frozen / Ready meals", "Заморозка / Готовые блюда", "Мұздатылған / Дайын тағамдар"), "0"),
  GenericGoodsCategorySeed("ready_meals", listOf("food", "ready_meals"), categoryText("Ready meals and salads", "Готовая еда и салаты", "Дайын тағам және салаттар"), "0"),
  GenericGoodsCategorySeed("ready_meals_salads", listOf("food", "ready_meals", "salads"), categoryText("Ready meals / Salads", "Готовая еда / Салаты", "Дайын тағам / Салаттар"), "0"),
  GenericGoodsCategorySeed("ready_meals_soups", listOf("food", "ready_meals", "soups"), categoryText("Ready meals / Soups", "Готовая еда / Супы", "Дайын тағам / Сорпалар"), "0"),
  GenericGoodsCategorySeed("baby", listOf("baby"), categoryText("Baby products", "Детские товары", "Балалар тауарлары"), "0"),
  GenericGoodsCategorySeed("baby_food", listOf("baby", "food"), categoryText("Baby / Food", "Детские товары / Питание", "Балалар / Тағам"), "0"),
  GenericGoodsCategorySeed("baby_diapers", listOf("baby", "diapers"), categoryText("Baby / Diapers and wipes", "Детские товары / Подгузники и салфетки", "Балалар / Жөргек және майлық"), "0"),
  GenericGoodsCategorySeed("baby_care", listOf("baby", "care"), categoryText("Baby / Care", "Детские товары / Уход", "Балалар / Күтім"), "0"),
  GenericGoodsCategorySeed("pet", listOf("pet"), categoryText("Pet supplies", "Товары для животных", "Үй жануарларына арналған тауарлар"), "0"),
  GenericGoodsCategorySeed("pet_food_cats", listOf("pet", "cat_food"), categoryText("Pet / Cat food", "Животные / Корм для кошек", "Жануарлар / Мысық азығы"), "0"),
  GenericGoodsCategorySeed("pet_food_dogs", listOf("pet", "dog_food"), categoryText("Pet / Dog food", "Животные / Корм для собак", "Жануарлар / Ит азығы"), "0"),
  GenericGoodsCategorySeed("pet_litter", listOf("pet", "hygiene"), categoryText("Pet / Litter and hygiene", "Животные / Наполнители и гигиена", "Жануарлар / Толтырғыш және гигиена"), "0"),
  GenericGoodsCategorySeed("household", listOf("household"), categoryText("Household goods", "Хозяйственные товары", "Шаруашылық тауарлары"), "0"),
  GenericGoodsCategorySeed("household_cleaning", listOf("household", "cleaning"), categoryText("Household / Cleaning products", "Хозтовары / Чистящие средства", "Шаруашылық / Тазалау құралдары"), "0"),
  GenericGoodsCategorySeed("household_laundry", listOf("household", "laundry"), categoryText("Household / Laundry", "Хозтовары / Стирка", "Шаруашылық / Кір жуу"), "0"),
  GenericGoodsCategorySeed("household_dishwashing", listOf("household", "dishwashing"), categoryText("Household / Dishwashing", "Хозтовары / Мытьё посуды", "Шаруашылық / Ыдыс жуу"), "0"),
  GenericGoodsCategorySeed("household_paper", listOf("household", "paper"), categoryText("Household / Paper goods", "Хозтовары / Бумажные товары", "Шаруашылық / Қағаз өнімдері"), "0"),
  GenericGoodsCategorySeed("household_bags_foil", listOf("household", "bags", "foil"), categoryText("Household / Bags, foil and film", "Хозтовары / Пакеты, фольга и плёнка", "Шаруашылық / Қап, фольга және үлдір"), "0"),
  GenericGoodsCategorySeed("personal_care", listOf("personal_care"), categoryText("Personal care", "Личная гигиена", "Жеке күтім"), "0"),
  GenericGoodsCategorySeed("personal_care_hair", listOf("personal_care", "hair"), categoryText("Personal care / Hair care", "Личная гигиена / Уход за волосами", "Жеке күтім / Шаш күтімі"), "0"),
  GenericGoodsCategorySeed("personal_care_body", listOf("personal_care", "body"), categoryText("Personal care / Body care", "Личная гигиена / Уход за телом", "Жеке күтім / Дене күтімі"), "0"),
  GenericGoodsCategorySeed("personal_care_oral", listOf("personal_care", "oral"), categoryText("Personal care / Oral care", "Личная гигиена / Уход за полостью рта", "Жеке күтім / Ауыз қуысы күтімі"), "0"),
  GenericGoodsCategorySeed("personal_care_shaving", listOf("personal_care", "shaving"), categoryText("Personal care / Shaving", "Личная гигиена / Бритьё", "Жеке күтім / Қырыну"), "0"),
  GenericGoodsCategorySeed("health", listOf("health"), categoryText("Health and wellness", "Здоровье", "Денсаулық"), "0"),
  GenericGoodsCategorySeed("health_first_aid", listOf("health", "first_aid"), categoryText("Health / First aid", "Здоровье / Первая помощь", "Денсаулық / Алғашқы көмек"), "0"),
  GenericGoodsCategorySeed("health_hygiene", listOf("health", "hygiene"), categoryText("Health / Hygiene goods", "Здоровье / Гигиенические товары", "Денсаулық / Гигиена тауарлары"), "0"),
  GenericGoodsCategorySeed("stationery", listOf("stationery"), categoryText("Stationery and office", "Канцелярия и офис", "Кеңсе тауарлары"), "0"),
  GenericGoodsCategorySeed("stationery_paper", listOf("stationery", "paper"), categoryText("Stationery / Paper", "Канцелярия / Бумага", "Кеңсе / Қағаз"), "0"),
  GenericGoodsCategorySeed("stationery_writing", listOf("stationery", "writing"), categoryText("Stationery / Pens and writing", "Канцелярия / Ручки и письмо", "Кеңсе / Қалам және жазу құралдары"), "0"),
  GenericGoodsCategorySeed("electronics", listOf("electronics"), categoryText("Electronics", "Электроника", "Электроника"), "0"),
  GenericGoodsCategorySeed("electronics_accessories", listOf("electronics", "accessories"), categoryText("Electronics / Accessories", "Электроника / Аксессуары", "Электроника / Аксессуарлар"), "0"),
  GenericGoodsCategorySeed("electronics_batteries", listOf("electronics", "batteries"), categoryText("Electronics / Batteries", "Электроника / Батарейки", "Электроника / Батареялар"), "0"),
  GenericGoodsCategorySeed("electronics_lighting", listOf("electronics", "lighting"), categoryText("Electronics / Lighting", "Электроника / Освещение", "Электроника / Жарықтандыру"), "0"),
  GenericGoodsCategorySeed("clothing", listOf("clothing"), categoryText("Clothing and textiles", "Одежда и текстиль", "Киім және тоқыма"), "0"),
  GenericGoodsCategorySeed("clothing_socks_underwear", listOf("clothing", "socks", "underwear"), categoryText("Clothing / Socks and underwear", "Одежда / Носки и бельё", "Киім / Шұлық және іш киім"), "0"),
  GenericGoodsCategorySeed("clothing_home_textiles", listOf("clothing", "home_textile"), categoryText("Textiles / Home textiles", "Текстиль / Домашний текстиль", "Тоқыма / Үй тоқыма бұйымдары"), "0"),
  GenericGoodsCategorySeed("toys", listOf("toys"), categoryText("Toys and games", "Игрушки и игры", "Ойыншықтар және ойындар"), "0"),
  GenericGoodsCategorySeed("garden", listOf("garden"), categoryText("Garden and plants", "Сад и растения", "Бақша және өсімдіктер"), "0"),
  GenericGoodsCategorySeed("garden_soil", listOf("garden", "soil", "fertilizer"), categoryText("Garden / Soil and fertilizers", "Сад / Грунт и удобрения", "Бақша / Топырақ және тыңайтқыш"), "0"),
  GenericGoodsCategorySeed("tools", listOf("tools"), categoryText("Tools and repair", "Инструменты и ремонт", "Құралдар және жөндеу"), "0"),
  GenericGoodsCategorySeed("auto", listOf("auto"), categoryText("Auto goods", "Автотовары", "Автотауарлар"), "0"),
  GenericGoodsCategorySeed("regulated", listOf("regulated"), categoryText("Age-restricted goods", "Товары с ограничениями", "Шектеулі тауарлар"), "0"),
  GenericGoodsCategorySeed("regulated_alcohol", listOf("regulated", "alcohol"), categoryText("Age-restricted / Alcohol", "Ограничения / Алкоголь", "Шектеулер / Алкоголь"), "0", conditions = defaultAgeRestrictedAlcoholCategoryConditions()),
  GenericGoodsCategorySeed("seasonal", listOf("seasonal"), categoryText("Seasonal goods", "Сезонные товары", "Маусымдық тауарлар"), "0"),
)

private fun sanitizeGenericGoodsCategoryPrefixesInsideTransaction() {
  GenericGoodsCategories.selectAll().forEach { row ->
    val categoryId = row[GenericGoodsCategories.id]
    val cleanName = row[GenericGoodsCategories.name].cleanSeededGoodsCategoryPrefixes()
    val cleanAlias = row[GenericGoodsCategories.alias]?.cleanSeededGoodsCategoryPrefixes()
    val cleanDescription = row[GenericGoodsCategories.description]?.cleanSeededGoodsCategoryPrefixes()

    if (
      cleanName != row[GenericGoodsCategories.name] ||
      cleanAlias != row[GenericGoodsCategories.alias] ||
      cleanDescription != row[GenericGoodsCategories.description]
    ) {
      GenericGoodsCategories.update({ GenericGoodsCategories.id eq categoryId }) { update ->
        update[GenericGoodsCategories.name] = cleanName
        update[GenericGoodsCategories.alias] = cleanAlias
        update[GenericGoodsCategories.description] = cleanDescription
      }
    }
  }
}

private fun seedGenericGoodsCategoriesInsideTransaction() {
  val existingRows = GenericGoodsCategories.selectAll().toList()
  val existingIds = existingRows
    .map { it[GenericGoodsCategories.id] }
    .toMutableSet()

  val existingByName = linkedMapOf<String, UUID>()
  existingRows
    .forEach { row ->
      val key = genericGoodsCategoryNameKey(row[GenericGoodsCategories.name])
      if (key.isNotBlank()) existingByName.putIfAbsent(key, row[GenericGoodsCategories.id])
    }

  defaultGenericGoodsCategorySeeds().forEach { seed ->
    val cleanName = seed.name.cleanSeededGoodsCategoryPrefixes()
    val cleanAlias = seed.alias?.cleanSeededGoodsCategoryPrefixes()
    val cleanDescription = seed.description?.cleanSeededGoodsCategoryPrefixes()
    val parentCategoryIds = seed.parentCategoryIds()
    val existingId = existingByName[genericGoodsCategoryNameKey(cleanName)]
    val categoryId = existingId ?: genericGoodsCategorySeedId(seed.slug)

    if (categoryId in existingIds) {
      GenericGoodsCategories.update({ GenericGoodsCategories.id eq categoryId }) { row ->
        row[GenericGoodsCategories.typeIds] = parentCategoryIds.distinct()
        row[GenericGoodsCategories.name] = cleanName
        row[GenericGoodsCategories.alias] = cleanAlias
        row[GenericGoodsCategories.description] = cleanDescription
        row[GenericGoodsCategories.quantityUnitId] = seed.quantityUnitId
        row[GenericGoodsCategories.imagePaths] = seed.imagePaths
        if (seed.conditions.isNotEmpty()) {
          row[GenericGoodsCategories.conditions] = seed.conditions
        }
      }
    } else {
      GenericGoodsCategories.insert { row ->
        row[GenericGoodsCategories.id] = categoryId
        row[GenericGoodsCategories.typeIds] = parentCategoryIds.distinct()
        row[GenericGoodsCategories.name] = cleanName
        row[GenericGoodsCategories.alias] = cleanAlias
        row[GenericGoodsCategories.description] = cleanDescription
        row[GenericGoodsCategories.quantityUnitId] = seed.quantityUnitId
        row[GenericGoodsCategories.imagePaths] = seed.imagePaths
        row[GenericGoodsCategories.conditions] = seed.conditions
      }
      existingIds += categoryId
    }

    existingByName[genericGoodsCategoryNameKey(cleanName)] = categoryId
  }
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

  private val hmacKey: SecretKeySpec by lazy {
    val pepper = envOrSystem("AITA_REFRESH_PEPPER")
      ?: if (isProductionRuntime()) {
        error("AITA_REFRESH_PEPPER must be configured in production")
      } else {
        "aita-local-dev-refresh-pepper-change-this-before-production-2026"
      }

    if (isProductionRuntime() && pepper.length < 64) {
      error("AITA_REFRESH_PEPPER must be at least 64 characters in production")
    }

    SecretKeySpec(pepper.toByteArray(StandardCharsets.UTF_8), "HmacSHA256")
  }

  fun newPlainToken(): String {
    val buf = ByteArray(32)
    rng.nextBytes(buf)
    return b64url.encodeToString(buf)
  }

  fun hash(token: String): String {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(hmacKey)
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

private const val MIN_ACCESS_TOKEN_TTL_MILLIS = 15L * 60L * 1000L
private const val LEGACY_ACCESS_TOKEN_EXPIRY_COMPATIBILITY_SECONDS = 10L * 365L * 24L * 60L * 60L
private val REFRESH_SESSION_NEVER_EXPIRES_AT: Instant = Instant.parse("9999-12-31T23:59:59Z")
private val REFRESH_SESSION_NEVER_EXPIRES_AT_MILLIS: Long = REFRESH_SESSION_NEVER_EXPIRES_AT.toEpochMilli()

fun Application.jwtConfig(): JwtConfig {
  val c = environment.config.config("ktor.security.jwt")
  val configuredAccessTtl = (c.optionalString("accessTTL") ?: envOrSystem("AITA_JWT_ACCESS_TTL_MILLIS") ?: "900000").toLong()
  val configuredRefreshTtl = (c.optionalString("refreshTTL") ?: envOrSystem("AITA_JWT_REFRESH_TTL_MILLIS") ?: "31536000000").toLong()
  val issuer = c.optionalString("issuer") ?: envOrSystem("AITA_JWT_ISSUER").orEmpty()
  val audience = c.optionalString("audience") ?: envOrSystem("AITA_JWT_AUDIENCE").orEmpty()
  val realm = c.optionalString("realm") ?: "AITA API"
  val secret = c.optionalString("secret") ?: envOrSystem("AITA_JWT_SECRET") ?: if (isProductionMode()) "" else "aita-local-dev-jwt-secret-change-this-before-production-2026-very-long-local-secret"

  if (isProductionMode()) {
    require(issuer.isNotBlank()) { "AITA_JWT_ISSUER must be configured in production" }
    require(audience.isNotBlank()) { "AITA_JWT_AUDIENCE must be configured in production" }
    require(secret.length >= 64) { "AITA_JWT_SECRET must be at least 64 characters in production" }
  }

  return JwtConfig(
    issuer = issuer,
    audience = audience,
    realm = realm,
    secret = secret,
    accessTTL = configuredAccessTtl.coerceAtLeast(MIN_ACCESS_TOKEN_TTL_MILLIS),
    refreshTTL = configuredRefreshTtl.coerceAtLeast(0L)
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
          .acceptExpiresAt(LEGACY_ACCESS_TOKEN_EXPIRY_COMPATIBILITY_SECONDS)
          .build()
      )

      validate { cred ->
        val sessionId = runCatching { UUID.fromString(cred.payload.getClaim("sessionId").asString()) }.getOrNull()
          ?: return@validate null

        val ok = newSuspendedTransaction(aitaServerIoContext) {
          val row = RefreshSessions
            .selectAll()
            .where { RefreshSessions.id eq sessionId }
            .limit(1)
            .singleOrNull()

          row != null && row[RefreshSessions.revokedAt] == null && cred.payload.issuer == cfg.issuer && cred.payload.audience.contains(
            cfg.audience
          ) && cred.subject != null
        }

        if (ok) JWTPrincipal(cred.payload) else null
      }

      challenge { _, _ ->
        val message = simpleMessage(
          main = "Unauthorized",
          ru = "Требуется вход в аккаунт",
          kk = "Аккаунтқа кіру қажет"
        )
        val response = GenericResponseDataModel(
          message = jsonBase.encodeToString(message),
          payload = null,
          negative = true
        )
        call.respondText(
          text = jsonBase.encodeToString(response),
          contentType = ContentType.Application.Json,
          status = HttpStatusCode.Unauthorized
        )
      }
    }
  }
}

class TokenService(private val cfg: JwtConfig) {

  private val algorithm = Algorithm
    .HMAC256(cfg.secret)

  fun signAccess(userId: UUID, sessionId: UUID, instant: Instant): String {
    return JWT.create()
      .withIssuer(cfg.issuer)
      .withAudience(cfg.audience)
      .withSubject(userId.toString())
      .withIssuedAt(Date.from(instant))
      .withClaim("sessionId", sessionId.toString())
      .sign(algorithm)
  }

  suspend fun newPair(userId: UUID, metaParam: Map<String, String>?): TokenPair {
    repeat(3) { attempt ->
      try {
        return newPairOnce(userId, metaParam)
      } catch (throwable: Throwable) {
        if (!throwable.isRefreshSessionInsertCollision()) throw throwable

        revokeSameDeviceSessionsBeforeRefreshSessionRetry(userId, metaParam)
        delay(25L * (attempt + 1))
      }
    }

    return newPairOnce(userId, metaParam)
  }

  private suspend fun newPairOnce(userId: UUID, metaParam: Map<String, String>?): TokenPair = coroutineScope {
    val refreshPlain = Refresh.newPlainToken()
    val refreshHash = Refresh.hash(refreshPlain)
    val now = Instant.now()
    val nowMillis = now.toEpochMilli()
    val expires = REFRESH_SESSION_NEVER_EXPIRES_AT
    val sessionId = UUID.randomUUID()

    val signAccessAsync = async(Dispatchers.Default) {
      signAccess(userId, sessionId, now)
    }

    newSuspendedTransaction(aitaServerIoContext) {
      Users
        .selectAll()
        .where { Users.id eq userId }
        .forUpdate()
        .singleOrNull()

      val oldSameDeviceSessionIds = sameDeviceSessionIdsInsideTransaction(userId, metaParam)

      if (oldSameDeviceSessionIds.isNotEmpty()) {
        RefreshSessions.update({ RefreshSessions.id inList oldSameDeviceSessionIds }) {
          it[RefreshSessions.revokedAt] = now
        }
        oldSameDeviceSessionIds.forEach { oldId ->
          insertSecuritySessionEventInsideTransaction(
            userId = userId,
            sessionId = oldId,
            eventType = SECURITY_EVENT_SESSION_REPLACED,
            metaParam = metaParam,
            metadata = mapOf("replacement_session_id" to sessionId.toString()),
            now = nowMillis
          )
        }
      }

      RefreshSessions.insert {
        it[id] = sessionId
        it[RefreshSessions.userId] = userId
        it[tokenHash] = refreshHash
        it[createdAt] = now
        it[expiresAt] = expires
        it[meta] = metaParam
      }
      insertSecuritySessionEventInsideTransaction(
        userId = userId,
        sessionId = sessionId,
        eventType = SECURITY_EVENT_SESSION_CREATED,
        metaParam = metaParam,
        now = nowMillis
      )
    }

    TokenPair(
      signAccessAsync.await(),
      REFRESH_SESSION_NEVER_EXPIRES_AT_MILLIS,
      refreshPlain,
      REFRESH_SESSION_NEVER_EXPIRES_AT_MILLIS
    )
  }

  suspend fun rotate(refreshPlain: String, metaParam: Map<String, String>?): TokenPair {
    repeat(3) { attempt ->
      try {
        return rotateOnce(refreshPlain, metaParam)
      } catch (throwable: Throwable) {
        if (throwable.isAlreadyRotatedOrRevokedRefreshToken()) {
          recoverRecentlyRotatedRefreshToken(refreshPlain, metaParam)?.let { return it }
        }

        if (!throwable.isRefreshSessionInsertCollision()) throw throwable

        val retryContext = loadRefreshSessionRetryContext(refreshPlain, metaParam) ?: throw throwable
        revokeSameDeviceSessionsBeforeRefreshSessionRetry(retryContext.userId, retryContext.meta)
        delay(25L * (attempt + 1))
      }
    }

    return try {
      rotateOnce(refreshPlain, metaParam)
    } catch (throwable: Throwable) {
      if (throwable.isAlreadyRotatedOrRevokedRefreshToken()) {
        recoverRecentlyRotatedRefreshToken(refreshPlain, metaParam)?.let { return it }
      }
      throw throwable
    }
  }

  private suspend fun recoverRecentlyRotatedRefreshToken(
    refreshPlain: String,
    metaParam: Map<String, String>?
  ): TokenPair? = coroutineScope {
    val hash = Refresh.hash(refreshPlain)
    val now = Instant.now()
    val nowMillis = now.toEpochMilli()
    val newPlain = Refresh.newPlainToken()
    val newHash = Refresh.hash(newPlain)
    val expires = REFRESH_SESSION_NEVER_EXPIRES_AT
    val newSessionId = UUID.randomUUID()

    val userId = newSuspendedTransaction(aitaServerIoContext) {
      val oldSession = RefreshSessions
        .selectAll()
        .where { RefreshSessions.tokenHash eq hash }
        .forUpdate()
        .singleOrNull() ?: return@newSuspendedTransaction null

      oldSession[RefreshSessions.revokedAt] ?: return@newSuspendedTransaction null

      val oldSessionId = oldSession[RefreshSessions.id]
      val sessionUserId = oldSession[RefreshSessions.userId]
      val oldMeta = oldSession[RefreshSessions.meta].orEmpty()

      val revocationCanRecover = SecuritySessionEvents
        .selectAll()
        .where {
          (SecuritySessionEvents.sessionId eq oldSessionId) and
             (SecuritySessionEvents.eventType inList listOf(
               SECURITY_EVENT_SESSION_REFRESHED,
               SECURITY_EVENT_SESSION_REPLACED
             ))
        }
        .limit(1)
        .singleOrNull() != null

      if (!revocationCanRecover) return@newSuspendedTransaction null

      fun ResultRow.matchesRecoveryDevice(): Boolean {
        val replacementMeta = this[RefreshSessions.meta].orEmpty()
        return sameRefreshSessionDeviceMeta(oldMeta, replacementMeta) ||
           sameRefreshSessionDeviceMeta(oldMeta, metaParam) ||
           sameRefreshSessionDeviceMeta(replacementMeta, metaParam)
      }

      val replacement = RefreshSessions
        .selectAll()
        .where { (RefreshSessions.userId eq sessionUserId) and (RefreshSessions.rotatedFrom eq oldSessionId) }
        .orderBy(RefreshSessions.createdAt, SortOrder.DESC)
        .toList()
        .firstOrNull { row -> row.matchesRecoveryDevice() }
        ?: RefreshSessions
          .selectAll()
          .where { (RefreshSessions.userId eq sessionUserId) and RefreshSessions.revokedAt.isNull() }
          .orderBy(RefreshSessions.createdAt, SortOrder.DESC)
          .toList()
          .firstOrNull { row -> row.matchesRecoveryDevice() }
        ?: return@newSuspendedTransaction null

      val replacementSessionId = replacement[RefreshSessions.id]
      val recoveredMeta = mergedRefreshSessionMeta(oldMeta, replacement[RefreshSessions.meta], metaParam)

      Users
        .selectAll()
        .where { Users.id eq sessionUserId }
        .forUpdate()
        .singleOrNull()

      if (replacement[RefreshSessions.revokedAt] == null) {
        RefreshSessions.update({ RefreshSessions.id eq replacementSessionId }) {
          it[RefreshSessions.revokedAt] = now
        }
        insertSecuritySessionEventInsideTransaction(
          userId = sessionUserId,
          sessionId = replacementSessionId,
          eventType = SECURITY_EVENT_SESSION_REPLACED,
          metaParam = recoveredMeta,
          metadata = mapOf("replacement_session_id" to newSessionId.toString(), "reason" to "idempotent refresh recovery"),
          now = nowMillis
        )
      }

      val otherSameDeviceSessionIds = sameDeviceSessionIdsInsideTransaction(
        userId = sessionUserId,
        metaParam = recoveredMeta,
        exceptSessionIds = setOf(oldSessionId, replacementSessionId, newSessionId)
      )

      if (otherSameDeviceSessionIds.isNotEmpty()) {
        RefreshSessions.update({ RefreshSessions.id inList otherSameDeviceSessionIds }) {
          it[RefreshSessions.revokedAt] = now
        }
        otherSameDeviceSessionIds.forEach { oldId ->
          insertSecuritySessionEventInsideTransaction(
            userId = sessionUserId,
            sessionId = oldId,
            eventType = SECURITY_EVENT_SESSION_REPLACED,
            metaParam = recoveredMeta,
            metadata = mapOf("replacement_session_id" to newSessionId.toString(), "reason" to "idempotent refresh recovery cleanup"),
            now = nowMillis
          )
        }
      }

      RefreshSessions.insert {
        it[id] = newSessionId
        it[RefreshSessions.userId] = sessionUserId
        it[tokenHash] = newHash
        it[createdAt] = now
        it[expiresAt] = expires
        it[rotatedFrom] = oldSessionId
        it[meta] = recoveredMeta
      }
      insertSecuritySessionEventInsideTransaction(
        userId = sessionUserId,
        sessionId = newSessionId,
        eventType = SECURITY_EVENT_SESSION_CREATED,
        metaParam = recoveredMeta,
        metadata = mapOf("recovered_from_rotated_refresh" to oldSessionId.toString(), "replaced_session_id" to replacementSessionId.toString()),
        now = nowMillis
      )

      sessionUserId
    } ?: return@coroutineScope null

    TokenPair(
      signAccess(userId, newSessionId, now),
      REFRESH_SESSION_NEVER_EXPIRES_AT_MILLIS,
      newPlain,
      REFRESH_SESSION_NEVER_EXPIRES_AT_MILLIS
    )
  }

  private suspend fun rotateOnce(refreshPlain: String, metaParam: Map<String, String>?): TokenPair {
    val hash = Refresh.hash(refreshPlain)
    val now = Instant.now()
    val nowMillis = now.toEpochMilli()
    val expires = REFRESH_SESSION_NEVER_EXPIRES_AT

    val (userId, sessionId) = newSuspendedTransaction(aitaServerIoContext) {
      val session = RefreshSessions
        .selectAll()
        .where { RefreshSessions.tokenHash eq hash }
        .forUpdate()
        .singleOrNull() ?: throw IllegalAccessException("No refresh token session")

      val sessionId = session[RefreshSessions.id]
      val sessionUserId = session[RefreshSessions.userId]

      Users
        .selectAll()
        .where { Users.id eq sessionUserId }
        .forUpdate()
        .singleOrNull()

      if (session[RefreshSessions.revokedAt] != null) {
        throw IllegalAccessException("Refresh token was already rotated or revoked")
      }

      val refreshedMeta = mergedRefreshSessionMeta(session[RefreshSessions.meta], metaParam)

      val oldSameDeviceSessionIds = sameDeviceSessionIdsInsideTransaction(
        userId = sessionUserId,
        metaParam = refreshedMeta,
        exceptSessionIds = setOf(sessionId)
      )

      if (oldSameDeviceSessionIds.isNotEmpty()) {
        RefreshSessions.update({ RefreshSessions.id inList oldSameDeviceSessionIds }) {
          it[RefreshSessions.revokedAt] = now
        }
        oldSameDeviceSessionIds.forEach { oldId ->
          insertSecuritySessionEventInsideTransaction(
            userId = sessionUserId,
            sessionId = oldId,
            eventType = SECURITY_EVENT_SESSION_REPLACED,
            metaParam = refreshedMeta,
            metadata = mapOf("replacement_session_id" to sessionId.toString(), "reason" to "same-device refresh cleanup"),
            now = nowMillis
          )
        }
      }

      RefreshSessions.update({ RefreshSessions.id eq sessionId }) {
        it[expiresAt] = expires
        it[meta] = refreshedMeta
      }

      insertSecuritySessionEventInsideTransaction(
        userId = sessionUserId,
        sessionId = sessionId,
        eventType = SECURITY_EVENT_SESSION_REFRESHED,
        metaParam = refreshedMeta,
        metadata = mapOf("rotation" to "stable_refresh_token"),
        now = nowMillis
      )

      sessionUserId to sessionId
    }

    return TokenPair(
      signAccess(userId, sessionId, now),
      REFRESH_SESSION_NEVER_EXPIRES_AT_MILLIS,
      refreshPlain,
      REFRESH_SESSION_NEVER_EXPIRES_AT_MILLIS
    )
  }

  suspend fun revoke(refreshPlain: String) = newSuspendedTransaction(aitaServerIoContext) {
    val hash = Refresh.hash(refreshPlain)
    val now = Instant.now()
    val nowMillis = now.toEpochMilli()
    val sessions = RefreshSessions
      .selectAll()
      .where { (RefreshSessions.tokenHash eq hash) and RefreshSessions.revokedAt.isNull() }
      .forUpdate()
      .toList()

    sessions.forEach { row ->
      RefreshSessions.update({ RefreshSessions.id eq row[RefreshSessions.id] }) {
        it[RefreshSessions.revokedAt] = now
      }
      insertSecuritySessionEventInsideTransaction(
        userId = row[RefreshSessions.userId],
        sessionId = row[RefreshSessions.id],
        eventType = SECURITY_EVENT_SESSION_LOGOUT,
        metaParam = row[RefreshSessions.meta],
        now = nowMillis
      )
    }
  }
}

private suspend fun refreshSessionUserIdForPlainToken(refreshPlain: String): UUID? {
  val clean = refreshPlain.trim().trim('"')
  if (clean.isBlank()) return null

  return newSuspendedTransaction(aitaServerIoContext) {
    RefreshSessions
      .select(RefreshSessions.userId)
      .where { RefreshSessions.tokenHash eq Refresh.hash(clean) }
      .limit(1)
      .singleOrNull()
      ?.get(RefreshSessions.userId)
  }
}

private object AitaServerRuntimeAnchor

private val aitaServerRuntimeClassLoader: ClassLoader by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
  AitaServerRuntimeAnchor::class.java.classLoader ?: ClassLoader.getSystemClassLoader()
}

private fun classLoaderDebugName(loader: ClassLoader?): String =
  loader?.let { "${it::class.java.name}@${Integer.toHexString(System.identityHashCode(it))}" } ?: "null"

@Volatile
private var sharedRuntimeSerializersPrewarmed = false

@PublishedApi
internal fun stabilizeServerRuntimeClassLoader(reason: String = "runtime") {
  val serverClassLoader = aitaServerRuntimeClassLoader
  val before = Thread.currentThread().contextClassLoader
  if (before !== serverClassLoader) {
    println(
      "AITA server classloader: reset reason=$reason " +
         "from=${classLoaderDebugName(before)} to=${classLoaderDebugName(serverClassLoader)}"
    )
  }
  Thread.currentThread().contextClassLoader = serverClassLoader
  System.setProperty("io.ktor.development", "false")
  System.setProperty("ktor.development", "false")
}

private class AitaServerClassLoaderContextElement(
  private val loader: ClassLoader = aitaServerRuntimeClassLoader
) : ThreadContextElement<ClassLoader?>, CoroutineContext.Element {
  companion object Key : CoroutineContext.Key<AitaServerClassLoaderContextElement>

  override val key: CoroutineContext.Key<AitaServerClassLoaderContextElement>
    get() = Key

  override fun updateThreadContext(context: CoroutineContext): ClassLoader? {
    val thread = Thread.currentThread()
    val previous = thread.contextClassLoader
    if (previous !== loader) {
      thread.contextClassLoader = loader
    }
    return previous
  }

  override fun restoreThreadContext(context: CoroutineContext, oldState: ClassLoader?) {
    val thread = Thread.currentThread()
    if (thread.contextClassLoader !== loader) {
      thread.contextClassLoader = loader
    }
  }
}

private fun aitaServerClassLoaderContextElement(): CoroutineContext = AitaServerClassLoaderContextElement()
private val aitaServerIoContext: CoroutineContext = Dispatchers.IO + AitaServerClassLoaderContextElement()

@PublishedApi
internal suspend fun <T> withAitaServerRuntimeClassLoader(
  reason: String,
  block: suspend () -> T
): T {
  stabilizeServerRuntimeClassLoader(reason)
  return withContext(aitaServerClassLoaderContextElement()) {
    stabilizeServerRuntimeClassLoader("$reason-context")
    block()
  }
}

@PublishedApi
internal fun Throwable.isClassLoadingFailure(): Boolean =
  this is ClassNotFoundException || this is NoClassDefFoundError || cause?.isClassLoadingFailure() == true

@PublishedApi
internal fun refreshSharedRuntimeSerializersAfterClassLoadingFailure(reason: String, throwable: Throwable? = null) {
  sharedRuntimeSerializersPrewarmed = false
  runCatching { prewarmSharedRuntimeSerializers() }
    .onFailure { refreshThrowable ->
      System.err.println(
        "AITA server classloader: serializer refresh failed reason=$reason " +
           "original=${throwable?.let { it::class.qualifiedName + ": " + it.message }.orEmpty()} " +
           "refresh=${refreshThrowable::class.qualifiedName}: ${refreshThrowable.message}"
      )
    }
}

@Synchronized
private fun prewarmSharedRuntimeSerializers() {
  if (sharedRuntimeSerializersPrewarmed) return
  stabilizeServerRuntimeClassLoader()

  val touchedSerializers = mutableListOf<String>()
  fun touch(name: String, block: () -> Any?) {
    runCatching { block() }
      .onSuccess { touchedSerializers += name }
      .onFailure { throwable ->
        println("AITA server classloader: FAILED to prewarm serializer $name: ${throwable::class.qualifiedName} ${throwable.message}")
        throw throwable
      }
  }

  touch("ActivationHistoryEntryDataModel") { ActivationHistoryEntryDataModel.serializer() }
  touch("BalanceHistoryEntryDataModel") { BalanceHistoryEntryDataModel.serializer() }
  touch("BatchDiscountDataModel") { BatchDiscountDataModel.serializer() }
  touch("CashRegisterExtractionRequestDataModel") { CashRegisterExtractionRequestDataModel.serializer() }
  touch("CompanyFormDataModel") { CompanyFormDataModel.serializer() }
  touch("DebtInterestDataModel") { DebtInterestDataModel.serializer() }
  touch("DebtPartialPaymentPlanDataModel") { DebtPartialPaymentPlanDataModel.serializer() }
  touch("DebtPaymentRecordDataModel") { DebtPaymentRecordDataModel.serializer() }
  touch("DebtPaymentRequestDataModel") { DebtPaymentRequestDataModel.serializer() }
  touch("DebtorDataModel") { DebtorDataModel.serializer() }
  touch("ExpirationPeriodDataModel") { ExpirationPeriodDataModel.serializer() }
  touch("GoodsBatchDataModel") { GoodsBatchDataModel.serializer() }
  touch("GoodsBatchShelfQueueDataModel") { GoodsBatchShelfQueueDataModel.serializer() }
  touch("GoodsItemBarcodeDataModel") { GoodsItemBarcodeDataModel.serializer() }
  touch("GoodsItemDataModel") { GoodsItemDataModel.serializer() }
  touch("GoodsItemInCartDataModel") { GoodsItemInCartDataModel.serializer() }
  touch("GoodsItemInTransactionDataModel") { GoodsItemInTransactionDataModel.serializer() }
  touch("LocalizedStringDataModel") { LocalizedStringDataModel.serializer() }
  touch("LocationDataModel") { LocationDataModel.serializer() }
  touch("NotificationDataModel") { NotificationDataModel.serializer() }
  touch("PriceDataModel") { PriceDataModel.serializer() }
  touch("QuantityDataModel") { QuantityDataModel.serializer() }
  touch("ReceiveSupplierOrderRequestDataModel") { ReceiveSupplierOrderRequestDataModel.serializer() }
  touch("SecuritySessionRevokeRequestDataModel") { SecuritySessionRevokeRequestDataModel.serializer() }
  touch("StockBatchMoveDecisionRequestDataModel") { StockBatchMoveDecisionRequestDataModel.serializer() }
  touch("StockBatchMoveRequestDataModel") { StockBatchMoveRequestDataModel.serializer() }
  touch("StockPromotionDataModel") { StockPromotionDataModel.serializer() }
  touch("StoreDataModel") { StoreDataModel.serializer() }
  touch("StoreSubscriptionUpdateRequestDataModel") { StoreSubscriptionUpdateRequestDataModel.serializer() }
  touch("StylizedDrawablePathsGroupDataModel") { StylizedDrawablePathsGroupDataModel.serializer() }
  touch("SubscriptionDataModel") { SubscriptionDataModel.serializer() }
  touch("SupplierDataModel") { SupplierDataModel.serializer() }
  touch("SupplierGoodsPriceDataModel") { SupplierGoodsPriceDataModel.serializer() }
  touch("SupplierContractPriceTermDataModel") { SupplierContractPriceTermDataModel.serializer() }
  touch("SupplierPartnershipContractDataModel") { SupplierPartnershipContractDataModel.serializer() }
  touch("SupplierDashboardStatusBucketDataModel") { SupplierDashboardStatusBucketDataModel.serializer() }
  touch("SupplierDashboardDemandDataModel") { SupplierDashboardDemandDataModel.serializer() }
  touch("SupplierDashboardPartnerDataModel") { SupplierDashboardPartnerDataModel.serializer() }
  touch("SupplierDashboardProfileDataModel") { SupplierDashboardProfileDataModel.serializer() }
  touch("SupplierDashboardActionDataModel") { SupplierDashboardActionDataModel.serializer() }
  touch("SupplierDashboardDeliveryBucketDataModel") { SupplierDashboardDeliveryBucketDataModel.serializer() }
  touch("SupplierDashboardReadinessDataModel") { SupplierDashboardReadinessDataModel.serializer() }
  touch("SupplierDashboardManufacturerBridgeDataModel") { SupplierDashboardManufacturerBridgeDataModel.serializer() }
  touch("SupplierModeDashboardDataModel") { SupplierModeDashboardDataModel.serializer() }
  touch("SupplierOrderWithLinesDataModel") { SupplierOrderWithLinesDataModel.serializer() }
  touch("SupportMessageSendRequestDataModel") { SupportMessageSendRequestDataModel.serializer() }
  touch("SupportMessagesReadRequestDataModel") { SupportMessagesReadRequestDataModel.serializer() }
  touch("SupportTicketActionRequestDataModel") { SupportTicketActionRequestDataModel.serializer() }
  touch("SupportTicketCreateRequestDataModel") { SupportTicketCreateRequestDataModel.serializer() }
  touch("TopUpConfirmDevelopmentRequestDataModel") { TopUpConfirmDevelopmentRequestDataModel.serializer() }
  touch("TopUpCreateRequestDataModel") { TopUpCreateRequestDataModel.serializer() }
  touch("TransactionDataModel") { TransactionDataModel.serializer() }
  touch("UserAccountUpdateDataModel") { UserAccountUpdateDataModel.serializer() }
  touch("UserAuthLogInDataModel") { UserAuthLogInDataModel.serializer() }
  touch("UserAuthSignUpDataModel") { UserAuthSignUpDataModel.serializer() }
  touch("UserPreferencesDataModel") { UserPreferencesDataModel.serializer() }
  touch("WorkerEmploymentDecisionRequestDataModel") { WorkerEmploymentDecisionRequestDataModel.serializer() }
  touch("WorkerEmploymentRequestCreateDataModel") { WorkerEmploymentRequestCreateDataModel.serializer() }
  touch("WorkerPermissionsUpdateRequestDataModel") { WorkerPermissionsUpdateRequestDataModel.serializer() }
  touch("WorkerPrivilegeModeDataModel") { WorkerPrivilegeModeDataModel.serializer() }
  touch("WorkerRemovalDecisionRequestDataModel") { WorkerRemovalDecisionRequestDataModel.serializer() }
  touch("WorkerRemovalRequestDataModel") { WorkerRemovalRequestDataModel.serializer() }
  touch("WorkerSelfPasswordUpdateRequestDataModel") { WorkerSelfPasswordUpdateRequestDataModel.serializer() }
  touch("WorkerStoreInvitationDecisionDataModel") { WorkerStoreInvitationDecisionDataModel.serializer() }
  touch("WorkerStoreInviteCreateDataModel") { WorkerStoreInviteCreateDataModel.serializer() }
  touch("WorkshiftStartRequestDataModel") { WorkshiftStartRequestDataModel.serializer() }
  touch("MoneyDataModel") { MoneyDataModel.serializer() }
  touch("PromotedPriceDataModel") { PromotedPriceDataModel.serializer() }
  touch("PagingRequestDataModel") { PagingRequestDataModel.serializer() }
  touch("PagedResponseDataModel<String>") { PagedResponseDataModel.serializer(String.serializer()) }
  touch("PaymentProviderConfigDataModel") { PaymentProviderConfigDataModel.serializer() }
  touch("UserWalletDataModel") { UserWalletDataModel.serializer() }
  touch("WalletLedgerEntryDataModel") { WalletLedgerEntryDataModel.serializer() }
  touch("TopUpPaymentIntentDataModel") { TopUpPaymentIntentDataModel.serializer() }
  touch("StoreSubscriptionPlanDataModel") { StoreSubscriptionPlanDataModel.serializer() }
  touch("StoreSubscriptionStateDataModel") { StoreSubscriptionStateDataModel.serializer() }
  touch("StoreSubscriptionChargeDataModel") { StoreSubscriptionChargeDataModel.serializer() }
  touch("SubscriptionDashboardDataModel") { SubscriptionDashboardDataModel.serializer() }
  touch("UserFinanceDashboardDataModel") { UserFinanceDashboardDataModel.serializer() }
  touch("StockBatchStatusDataModel") { StockBatchStatusDataModel.serializer() }
  touch("StockBatchMovementStatusDataModel") { StockBatchMovementStatusDataModel.serializer() }
  touch("SupplierOrderStatusDataModel") { SupplierOrderStatusDataModel.serializer() }
  touch("TransactionPaymentDraftDataModel") { TransactionPaymentDraftDataModel.serializer() }
  touch("TransactionCartScrollStateDataModel") { TransactionCartScrollStateDataModel.serializer() }
  touch("TransactionReceiptSnapshotDataModel") { TransactionReceiptSnapshotDataModel.serializer() }
  touch("TransactionReceiptLineDataModel") { TransactionReceiptLineDataModel.serializer() }
  touch("PlatformReceiptPrinterDataModel") { PlatformReceiptPrinterDataModel.serializer() }
  touch("PlatformLabelPrinterDataModel") { PlatformLabelPrinterDataModel.serializer() }
  touch("StockItemLabelDataModel") { StockItemLabelDataModel.serializer() }
  touch("AnalyticsReportRowDataModel") { AnalyticsReportRowDataModel.serializer() }
  touch("AnalyticsReportSectionDataModel") { AnalyticsReportSectionDataModel.serializer() }
  touch("AnalyticsReportSnapshotDataModel") { AnalyticsReportSnapshotDataModel.serializer() }
  touch("AnalyticsReturnReasonDataModel") { AnalyticsReturnReasonDataModel.serializer() }
  touch("ReceiptTextLabelsDataModel") { ReceiptTextLabelsDataModel.serializer() }
  touch("EmbeddedWeightBarcodeDataModel") { EmbeddedWeightBarcodeDataModel.serializer() }
  touch("ReceiveSupplierOrderLineDataModel") { ReceiveSupplierOrderLineDataModel.serializer() }
  touch("AuthScreenPreferenceOverrideDataModel") { AuthScreenPreferenceOverrideDataModel.serializer() }
  touch("AccountSubscriptionStatusDataModel") { AccountSubscriptionStatusDataModel.serializer() }
  touch("AppLanguageDataModel") { AppLanguageDataModel.serializer() }
  touch("StoreCashRegisterDataModel") { StoreCashRegisterDataModel.serializer() }
  touch("CashRegisterEventDataModel") { CashRegisterEventDataModel.serializer() }
  touch("CashRegisterExtractionEntryDataModel") { CashRegisterExtractionEntryDataModel.serializer() }
  touch("CashRegisterStateDataModel") { CashRegisterStateDataModel.serializer() }
  touch("StockBranchQuantityDataModel") { StockBranchQuantityDataModel.serializer() }
  touch("StockBatchMovementDataModel") { StockBatchMovementDataModel.serializer() }
  touch("StockItemBranchAvailabilityDataModel") { StockItemBranchAvailabilityDataModel.serializer() }
  touch("StockBatchMoveResultDataModel") { StockBatchMoveResultDataModel.serializer() }
  touch("AppThemeDataModel") { AppThemeDataModel.serializer() }
  touch("CityDataModel") { CityDataModel.serializer() }
  touch("LegalIdFormatDataModel") { LegalIdFormatDataModel.serializer() }
  touch("CountryDataModel") { CountryDataModel.serializer() }
  touch("CurrencyDataModel") { CurrencyDataModel.serializer() }
  touch("GenericGoodsCategoryDataModel") { GenericGoodsCategoryDataModel.serializer() }
  touch("GenericGoodsItemDataModel") { GenericGoodsItemDataModel.serializer() }
  touch("GenericResponseDataModel") { GenericResponseDataModel.serializer() }
  touch("GlobalAppConfigurationDataModel") { GlobalAppConfigurationDataModel.serializer() }
  touch("SupplierOrderLineDataModel") { SupplierOrderLineDataModel.serializer() }
  touch("SupplierOrderDataModel") { SupplierOrderDataModel.serializer() }
  touch("GoodsCategoryDataModel") { GoodsCategoryDataModel.serializer() }
  touch("GoodsItemInRemovalDataModel") { GoodsItemInRemovalDataModel.serializer() }
  touch("LocalizedStringGroupDataModel") { LocalizedStringGroupDataModel.serializer() }
  touch("ManufacturerDataModel") { ManufacturerDataModel.serializer() }
  touch("SupportTicketDataModel") { SupportTicketDataModel.serializer() }
  touch("SupportMessageDataModel") { SupportMessageDataModel.serializer() }
  touch("ParameterDataModel") { ParameterDataModel.serializer() }
  touch("PaymentOptionDataModel") { PaymentOptionDataModel.serializer() }
  touch("RemoteResponseDataModel") { RemoteResponseDataModel.serializer() }
  touch("RealtimeUpdateDataModel") { RealtimeUpdateDataModel.serializer() }
  touch("RealtimeClientHelloDataModel") { RealtimeClientHelloDataModel.serializer() }
  touch("LocalNetworkDeviceDataModel") { LocalNetworkDeviceDataModel.serializer() }
  touch("LocalNetworkStateDataModel") { LocalNetworkStateDataModel.serializer() }
  touch("LocalNetworkSnapshotDataModel") { LocalNetworkSnapshotDataModel.serializer() }
  touch("LocalNetworkQueuedOperationDataModel") { LocalNetworkQueuedOperationDataModel.serializer() }
  touch("LocalNetworkEnvelopeDataModel") { LocalNetworkEnvelopeDataModel.serializer() }
  touch("ResponseDataModel<Unit>") { ResponseDataModel.serializer(Unit.serializer()) }
  touch("StylizedColorDataModel") { StylizedColorDataModel.serializer() }
  touch("StylizedColorGroupDataModel") { StylizedColorGroupDataModel.serializer() }
  touch("StylizedDimensionDataModel") { StylizedDimensionDataModel.serializer() }
  touch("StylizedDimensionGroupDataModel") { StylizedDimensionGroupDataModel.serializer() }
  touch("StylizedDrawablePathsDataModel") { StylizedDrawablePathsDataModel.serializer() }
  touch("SubscriptionPlanDataModel") { SubscriptionPlanDataModel.serializer() }
  touch("ClientDeviceInfoDataModel") { ClientDeviceInfoDataModel.serializer() }
  touch("PendingSessionCleanupDataModel") { PendingSessionCleanupDataModel.serializer() }
  touch("LogoutCleanupRequestDataModel") { LogoutCleanupRequestDataModel.serializer() }
  touch("PendingWorkshiftEndDataModel") { PendingWorkshiftEndDataModel.serializer() }
  touch("SecuritySessionDataModel") { SecuritySessionDataModel.serializer() }
  touch("SecuritySessionHistoryDataModel") { SecuritySessionHistoryDataModel.serializer() }
  touch("AnalyticsRankedItemDataModel") { AnalyticsRankedItemDataModel.serializer() }
  touch("AnalyticsBucketDataModel") { AnalyticsBucketDataModel.serializer() }
  touch("StoreAnalyticsDashboardDataModel") { StoreAnalyticsDashboardDataModel.serializer() }
  touch("UserAccountDataModel") { UserAccountDataModel.serializer() }
  touch("UserBalanceDataModel") { UserBalanceDataModel.serializer() }
  touch("UserSettingsDataModel") { UserSettingsDataModel.serializer() }
  touch("StoreWorkerDataModel") { StoreWorkerDataModel.serializer() }
  touch("StoreWorkerRequestDataModel") { StoreWorkerRequestDataModel.serializer() }
  touch("StoreWorkerRoleTemplateDataModel") { StoreWorkerRoleTemplateDataModel.serializer() }
  touch("StoreWorkerRoleTemplateUpsertRequestDataModel") { StoreWorkerRoleTemplateUpsertRequestDataModel.serializer() }
  touch("StoreWorkerRoleTemplateDeleteRequestDataModel") { StoreWorkerRoleTemplateDeleteRequestDataModel.serializer() }
  touch("StoreJobDataModel") { StoreJobDataModel.serializer() }
  touch("WorkshiftEndRequestDataModel") { WorkshiftEndRequestDataModel.serializer() }
  touch("WorkshiftDataModel") { WorkshiftDataModel.serializer() }
  touch("OperationLogDataModel") { OperationLogDataModel.serializer() }
  touch("WorkerDataModel") { WorkerDataModel.serializer() }
  touch("PagedResponseDataModel") { PagedResponseDataModel.serializer(String.serializer()) }
  touch("ResponseDataModel") { ResponseDataModel.serializer(String.serializer()) }
  touch("TokenPair") { TokenPair.serializer() }


  listOf(
    "kz.aita.MoneyDataModel",
    "kz.aita.StockBatchStatusDataModel",
    "kz.aita.StockBatchMovementStatusDataModel",
    "kz.aita.SupplierOrderStatusDataModel",
    "kz.aita.ExpirationPeriodDataModel",
    "kz.aita.BatchDiscountDataModel",
    "kz.aita.StockPromotionDataModel",
    "kz.aita.PromotedPriceDataModel",
    "kz.aita.PagingRequestDataModel",
    "kz.aita.PagedResponseDataModel",
    "kz.aita.PaymentProviderConfigDataModel",
    "kz.aita.UserWalletDataModel",
    "kz.aita.WalletLedgerEntryDataModel",
    "kz.aita.TopUpCreateRequestDataModel",
    "kz.aita.TopUpConfirmDevelopmentRequestDataModel",
    "kz.aita.TopUpPaymentIntentDataModel",
    "kz.aita.StoreSubscriptionPlanDataModel",
    "kz.aita.StoreSubscriptionStateDataModel",
    "kz.aita.StoreSubscriptionChargeDataModel",
    "kz.aita.StoreSubscriptionUpdateRequestDataModel",
    "kz.aita.SubscriptionDashboardDataModel",
    "kz.aita.UserFinanceDashboardDataModel",
    "kz.aita.TransactionPaymentDraftDataModel",
    "kz.aita.TransactionCartScrollStateDataModel",
    "kz.aita.TransactionReceiptSnapshotDataModel",
    "kz.aita.TransactionReceiptLineDataModel",
    "kz.aita.PlatformReceiptPrinterDataModel",
    "kz.aita.PlatformLabelPrinterDataModel",
    "kz.aita.StockItemLabelDataModel",
    "kz.aita.AnalyticsReportRowDataModel",
    "kz.aita.AnalyticsReportSectionDataModel",
    "kz.aita.AnalyticsReportSnapshotDataModel",
    "kz.aita.AnalyticsReturnReasonDataModel",
    "kz.aita.ReceiptTextLabelsDataModel",
    "kz.aita.AuthScreenPreferenceOverrideDataModel",
    "kz.aita.EmbeddedWeightBarcodeDataModel",
    "kz.aita.GoodsItemBarcodeDataModel",
    "kz.aita.ReceiveSupplierOrderRequestDataModel",
    "kz.aita.ReceiveSupplierOrderLineDataModel",
    "kz.aita.SupplierOrderWithLinesDataModel",
    "kz.aita.UserPreferencesDataModel",
    "kz.aita.AccountSubscriptionStatusDataModel",
    "kz.aita.ActivationHistoryEntryDataModel",
    "kz.aita.AppLanguageDataModel",
    "kz.aita.StoreCashRegisterDataModel",
    "kz.aita.CashRegisterEventDataModel",
    "kz.aita.CashRegisterExtractionEntryDataModel",
    "kz.aita.CashRegisterStateDataModel",
    "kz.aita.CashRegisterExtractionRequestDataModel",
    "kz.aita.StockBranchQuantityDataModel",
    "kz.aita.StockBatchMovementDataModel",
    "kz.aita.StockItemBranchAvailabilityDataModel",
    "kz.aita.StockBatchMoveRequestDataModel",
    "kz.aita.StockBatchMoveDecisionRequestDataModel",
    "kz.aita.StockBatchMoveResultDataModel",
    "kz.aita.AppThemeDataModel",
    "kz.aita.BalanceHistoryEntryDataModel",
    "kz.aita.CityDataModel",
    "kz.aita.CompanyFormDataModel",
    "kz.aita.LegalIdFormatDataModel",
    "kz.aita.CountryDataModel",
    "kz.aita.CurrencyDataModel",
    "kz.aita.DebtInterestDataModel",
    "kz.aita.DebtPartialPaymentPlanDataModel",
    "kz.aita.DebtPaymentRecordDataModel",
    "kz.aita.DebtorDataModel",
    "kz.aita.DebtPaymentRequestDataModel",
    "kz.aita.GenericGoodsCategoryDataModel",
    "kz.aita.GenericGoodsItemDataModel",
    "kz.aita.GenericResponseDataModel",
    "kz.aita.GlobalAppConfigurationDataModel",
    "kz.aita.SupplierOrderLineDataModel",
    "kz.aita.SupplierOrderDataModel",
    "kz.aita.SupplierGoodsPriceDataModel",
    "kz.aita.SupplierContractPriceTermDataModel",
    "kz.aita.SupplierPartnershipContractDataModel",
    "kz.aita.SupplierDashboardStatusBucketDataModel",
    "kz.aita.SupplierDashboardDemandDataModel",
    "kz.aita.SupplierDashboardPartnerDataModel",
    "kz.aita.SupplierDashboardProfileDataModel",
    "kz.aita.SupplierDashboardActionDataModel",
    "kz.aita.SupplierDashboardDeliveryBucketDataModel",
    "kz.aita.SupplierDashboardReadinessDataModel",
    "kz.aita.SupplierDashboardManufacturerBridgeDataModel",
    "kz.aita.SupplierModeDashboardDataModel",
    "kz.aita.GoodsBatchDataModel",
    "kz.aita.GoodsBatchShelfQueueDataModel",
    "kz.aita.GoodsCategoryDataModel",
    "kz.aita.GoodsItemDataModel",
    "kz.aita.GoodsItemInCartDataModel",
    "kz.aita.GoodsItemInRemovalDataModel",
    "kz.aita.GoodsItemInTransactionDataModel",
    "kz.aita.LocalizedStringDataModel",
    "kz.aita.LocalizedStringGroupDataModel",
    "kz.aita.LocationDataModel",
    "kz.aita.ManufacturerDataModel",
    "kz.aita.NotificationDataModel",
    "kz.aita.SupportTicketDataModel",
    "kz.aita.SupportMessageDataModel",
    "kz.aita.SupportTicketCreateRequestDataModel",
    "kz.aita.SupportMessageSendRequestDataModel",
    "kz.aita.SupportTicketActionRequestDataModel",
    "kz.aita.SupportMessagesReadRequestDataModel",
    "kz.aita.ParameterDataModel",
    "kz.aita.PaymentOptionDataModel",
    "kz.aita.PriceDataModel",
    "kz.aita.QuantityDataModel",
    "kz.aita.RemoteResponseDataModel",
    "kz.aita.RealtimeUpdateDataModel",
    "kz.aita.RealtimeClientHelloDataModel",
    "kz.aita.LocalNetworkDeviceDataModel",
    "kz.aita.LocalNetworkStateDataModel",
    "kz.aita.LocalNetworkSnapshotDataModel",
    "kz.aita.LocalNetworkQueuedOperationDataModel",
    "kz.aita.LocalNetworkEnvelopeDataModel",
    "kz.aita.ResponseDataModel",
    "kz.aita.StoreDataModel",
    "kz.aita.StylizedColorDataModel",
    "kz.aita.StylizedColorGroupDataModel",
    "kz.aita.StylizedDimensionDataModel",
    "kz.aita.StylizedDimensionGroupDataModel",
    "kz.aita.StylizedDrawablePathsDataModel",
    "kz.aita.StylizedDrawablePathsGroupDataModel",
    "kz.aita.SubscriptionDataModel",
    "kz.aita.SubscriptionPlanDataModel",
    "kz.aita.SupplierDataModel",
    "kz.aita.TokenPair",
    "kz.aita.ClientDeviceInfoDataModel",
    "kz.aita.PendingSessionCleanupDataModel",
    "kz.aita.LogoutCleanupRequestDataModel",
    "kz.aita.PendingWorkshiftEndDataModel",
    "kz.aita.SecuritySessionDataModel",
    "kz.aita.SecuritySessionHistoryDataModel",
    "kz.aita.SecuritySessionRevokeRequestDataModel",
    "kz.aita.AnalyticsRankedItemDataModel",
    "kz.aita.AnalyticsBucketDataModel",
    "kz.aita.StoreAnalyticsDashboardDataModel",
    "kz.aita.TransactionDataModel",
    "kz.aita.UserAccountDataModel",
    "kz.aita.UserAccountUpdateDataModel",
    "kz.aita.UserAuthLogInDataModel",
    "kz.aita.UserAuthSignUpDataModel",
    "kz.aita.UserBalanceDataModel",
    "kz.aita.UserSettingsDataModel",
    "kz.aita.StoreWorkerDataModel",
    "kz.aita.StoreWorkerRequestDataModel",
    "kz.aita.StoreWorkerRoleTemplateDataModel",
    "kz.aita.StoreWorkerRoleTemplateUpsertRequestDataModel",
    "kz.aita.StoreWorkerRoleTemplateDeleteRequestDataModel",
    "kz.aita.StoreJobDataModel",
    "kz.aita.StoreJobDataModel\$Cashier",
    "kz.aita.StoreJobDataModel\$WarehouseManager",
    "kz.aita.StoreJobDataModel\$Administrator",
    "kz.aita.WorkerEmploymentRequestCreateDataModel",
    "kz.aita.WorkerEmploymentDecisionRequestDataModel",
    "kz.aita.WorkerStoreInviteCreateDataModel",
    "kz.aita.WorkerStoreInvitationDecisionDataModel",
    "kz.aita.WorkerPermissionsUpdateRequestDataModel",
    "kz.aita.WorkerRemovalRequestDataModel",
    "kz.aita.WorkerRemovalDecisionRequestDataModel",
    "kz.aita.WorkerSelfPasswordUpdateRequestDataModel",
    "kz.aita.WorkshiftStartRequestDataModel",
    "kz.aita.WorkshiftEndRequestDataModel",
    "kz.aita.WorkshiftDataModel",
    "kz.aita.OperationLogDataModel",
    "kz.aita.WorkerDataModel",
    "kz.aita.WorkerPrivilegeModeDataModel",
  ).forEach { className ->
    runCatching { Class.forName(className, true, aitaServerRuntimeClassLoader) }
      .onFailure { throwable ->
        println("AITA server classloader: FAILED to verify $className in $aitaServerRuntimeClassLoader: ${throwable::class.qualifiedName} ${throwable.message}")
        throw throwable
      }
  }

  sharedRuntimeSerializersPrewarmed = true
  val developmentProperty = System.getProperty("io.ktor.development")
  println(
    "AITA server classloader: stable runtime loader=$aitaServerRuntimeClassLoader " +
       "prewarmedSerializers=${touchedSerializers.size} development=$developmentProperty"
  )
}

private val AitaRuntimeClassLoaderPlugin = createApplicationPlugin(name = "AitaRuntimeClassLoaderPlugin") {
  onCall { call ->
    val before = Thread.currentThread().contextClassLoader
    stabilizeServerRuntimeClassLoader("call:${call.request.httpMethod.value}:${call.request.path()}")
    val after = Thread.currentThread().contextClassLoader
    if (before !== after) {
      call.application.environment.log.warn(
        "AITA server classloader: call guard reset ${call.request.httpMethod.value} ${call.request.path()} " +
           "from=${classLoaderDebugName(before)} to=${classLoaderDebugName(after)}"
      )
    }
  }
}

fun main(args: Array<String>) {
  stabilizeServerRuntimeClassLoader("main")
  Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
    if (throwable.isClassLoadingFailure()) {
      System.err.println(
        "AITA server classloader: uncaught classloading failure thread=${thread.name} " +
           "threadLoader=${classLoaderDebugName(thread.contextClassLoader)} anchor=${classLoaderDebugName(aitaServerRuntimeClassLoader)}"
      )
    }
  }
  prewarmSharedRuntimeSerializers()
  EngineMain.main(args)
}

private object RealtimeServerBus {
  private const val DUPLICATE_COALESCE_WINDOW_MILLIS = 260L
  private const val RECENT_KEY_CLEANUP_WINDOW_MILLIS = 30_000L

  private val updates = MutableSharedFlow<RealtimeUpdateDataModel>(
    replay = 128,
    extraBufferCapacity = 192,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
  )
  private val recentPublishedAtByKey = ConcurrentHashMap<String, Long>()

  val sharedUpdates = updates.asSharedFlow()

  suspend fun publish(
    entity: String = "all",
    storeId: String? = null,
    reason: String? = null
  ) {
    val now = System.currentTimeMillis()
    val cleanEntity = entity.trim().ifBlank { "all" }
    val cleanStoreId = storeId?.trim()?.takeIf { it.isNotBlank() }
    val cleanReason = reason?.trim()?.takeIf { it.isNotBlank() }
    val coalescingKey = listOf(cleanEntity, cleanStoreId.orEmpty(), cleanReason.orEmpty()).joinToString("|")
    val previous = recentPublishedAtByKey.put(coalescingKey, now)
    if (previous != null && now - previous < DUPLICATE_COALESCE_WINDOW_MILLIS) return

    if (recentPublishedAtByKey.size > 512) {
      recentPublishedAtByKey.entries.removeIf { (_, time) -> now - time > RECENT_KEY_CLEANUP_WINDOW_MILLIS }
    }

    val update = RealtimeUpdateDataModel(
      id = UUID.randomUUID().toString(),
      type = "changed",
      entity = cleanEntity,
      storeId = cleanStoreId,
      reason = cleanReason,
      createdAtMillis = now
    )

    if (!updates.tryEmit(update)) updates.emit(update)
  }
}

private suspend fun publishWorkerRealtimeBundle(storeId: String?, reason: String) {
  val cleanStoreId = storeId?.trim()?.takeIf { it.isNotBlank() }
  val entities = listOf("workers/requests", "workers/memberships", "workers", "notifications", "stores", "all")

  entities.forEach { entity ->
    RealtimeServerBus.publish(entity = entity, storeId = cleanStoreId, reason = reason)
  }

  // Worker/employment events are personal and cross-store-group at the same time:
  // managers must see incoming requests for stores/branches they manage, while the worker
  // must see invites/decisions in My work even when no active store is selected.
  listOf("workers/requests", "workers/memberships", "workers", "notifications", "stores", "all").forEach { entity ->
    RealtimeServerBus.publish(entity = entity, storeId = null, reason = reason)
  }
}

private suspend fun publishStockRealtimeBundle(storeId: String?, reason: String) {
  val cleanStoreId = storeId?.trim()?.takeIf { it.isNotBlank() }
  RealtimeServerBus.publish(entity = "stock", storeId = cleanStoreId, reason = reason)
  RealtimeServerBus.publish(entity = "stockBatches", storeId = cleanStoreId, reason = reason)
  RealtimeServerBus.publish(entity = "stock/availability", storeId = cleanStoreId, reason = reason)
  RealtimeServerBus.publish(entity = "transactions/cart", storeId = cleanStoreId, reason = reason)
  RealtimeServerBus.publish(entity = "notifications", storeId = cleanStoreId, reason = reason)
  RealtimeServerBus.publish(entity = "all", storeId = cleanStoreId, reason = reason)
}

private suspend fun publishStockRealtimeBundle(storeIds: Iterable<String?>, reason: String) {
  val cleanStoreIds = storeIds
    .mapNotNull { it?.trim()?.takeIf { value -> value.isNotBlank() } }
    .distinct()

  if (cleanStoreIds.isEmpty()) {
    publishStockRealtimeBundle(null, reason)
  } else {
    cleanStoreIds.forEach { storeId ->
      publishStockRealtimeBundle(storeId, reason)
    }
  }
}

private fun Throwable.isExpectedRealtimeDisconnect(): Boolean {
  val combined = listOfNotNull(
    this::class.simpleName,
    message,
    cause?.message
  ).joinToString(" ").lowercase()

  return "ping timeout" in combined ||
     "closedreceivechannel" in combined ||
     "closedsendchannel" in combined ||
     "closed channel" in combined ||
     "connection reset" in combined ||
     "broken pipe" in combined ||
     "channel was closed" in combined
}


private const val NOTIFICATION_RECENT_DUPLICATE_WINDOW_MILLIS = 10L * 60L * 1000L
private const val NOTIFICATION_RETENTION_READ_MILLIS = 90L * 24L * 60L * 60L * 1000L
private const val NOTIFICATION_RETENTION_UNREAD_MILLIS = 180L * 24L * 60L * 60L * 1000L
private const val NOTIFICATION_RETENTION_INACTIVE_MILLIS = 30L * 24L * 60L * 60L * 1000L
private const val NOTIFICATION_RETENTION_CHECK_INTERVAL_MILLIS = 24L * 60L * 60L * 1000L
private const val NOTIFICATION_RETENTION_MAX_PER_USER = 1_000

@Volatile
private var notificationRetentionDaemonStarted = false

private fun cleanupNotificationsInsideTransaction(nowMillis: Long = System.currentTimeMillis()) {
  val readCutoff = nowMillis - NOTIFICATION_RETENTION_READ_MILLIS
  val unreadCutoff = nowMillis - NOTIFICATION_RETENTION_UNREAD_MILLIS
  val inactiveCutoff = nowMillis - NOTIFICATION_RETENTION_INACTIVE_MILLIS

  Notifications.deleteWhere {
    ((Notifications.readAtMillis.isNotNull()) and (Notifications.createdAtMillis lessEq readCutoff)) or
       ((Notifications.readAtMillis.isNull()) and (Notifications.createdAtMillis lessEq unreadCutoff)) or
       ((Notifications.isActive eq false) and (Notifications.createdAtMillis lessEq inactiveCutoff))
  }

  org.jetbrains.exposed.sql.transactions.TransactionManager.current().exec(
    """
        DELETE FROM user_notifications
        WHERE id IN (
            SELECT id FROM (
                SELECT id,
                       ROW_NUMBER() OVER (
                           PARTITION BY user_id
                           ORDER BY created_at_millis DESC, shown_at_millis DESC, id DESC
                       ) AS row_number_per_user
                FROM user_notifications
            ) ranked_notifications
            WHERE ranked_notifications.row_number_per_user > $NOTIFICATION_RETENTION_MAX_PER_USER
        )
        """.trimIndent()
  )
}

private fun Application.startNotificationRetentionDaemon(backgroundScope: CoroutineScope) {
  if (notificationRetentionDaemonStarted) return
  notificationRetentionDaemonStarted = true

  backgroundScope.launch {
    while (true) {
      runCatching {
        newSuspendedTransaction(aitaServerIoContext) {
          cleanupNotificationsInsideTransaction()
        }
      }.onFailure { log.error("Notification retention daemon iteration failed", it) }

      delay(NOTIFICATION_RETENTION_CHECK_INTERVAL_MILLIS)
    }
  }
}

private fun countryDefaultCurrencyCode(countryLocale: String): String {
  return when (countryLocale.trim().lowercase()) {
    "tj" -> "TJS"
    else -> "KZT"
  }
}

private fun ResultRow.toUserWalletDataModel(): UserWalletDataModel = UserWalletDataModel(
  id = this[UserWallets.id].toString(),
  userId = this[UserWallets.userId].toString(),
  currencyCode = this[UserWallets.currencyCode],
  aitaCurrencyCode = this[UserWallets.currencyCode].aitaCurrencyCode(),
  balanceMinor = this[UserWallets.balanceMinor],
  reservedMinor = this[UserWallets.reservedMinor],
  updatedAtMillis = this[UserWallets.updatedAtMillis]
)

private fun ResultRow.toWalletLedgerEntryDataModel(): WalletLedgerEntryDataModel = WalletLedgerEntryDataModel(
  id = this[UserWalletLedgerEntries.id].toString(),
  userId = this[UserWalletLedgerEntries.userId].toString(),
  walletId = this[UserWalletLedgerEntries.walletId].toString(),
  type = this[UserWalletLedgerEntries.type],
  amountMinor = this[UserWalletLedgerEntries.amountMinor],
  balanceBeforeMinor = this[UserWalletLedgerEntries.balanceBeforeMinor],
  balanceAfterMinor = this[UserWalletLedgerEntries.balanceAfterMinor],
  currencyCode = this[UserWalletLedgerEntries.currencyCode],
  referenceType = this[UserWalletLedgerEntries.referenceType],
  referenceId = this[UserWalletLedgerEntries.referenceId],
  note = this[UserWalletLedgerEntries.note],
  createdAtMillis = this[UserWalletLedgerEntries.createdAtMillis]
)

private fun ResultRow.toTopUpPaymentIntentDataModel(): TopUpPaymentIntentDataModel = TopUpPaymentIntentDataModel(
  id = this[TopUpPaymentIntents.id].toString(),
  userId = this[TopUpPaymentIntents.userId].toString(),
  providerId = this[TopUpPaymentIntents.providerId],
  amountMinor = this[TopUpPaymentIntents.amountMinor],
  currencyCode = this[TopUpPaymentIntents.currencyCode],
  aitaCurrencyCode = this[TopUpPaymentIntents.currencyCode].aitaCurrencyCode(),
  status = this[TopUpPaymentIntents.status],
  providerInvoiceId = this[TopUpPaymentIntents.providerInvoiceId],
  paymentUrl = this[TopUpPaymentIntents.paymentUrl],
  qrPayload = this[TopUpPaymentIntents.qrPayload],
  createdAtMillis = this[TopUpPaymentIntents.createdAtMillis],
  expiresAtMillis = this[TopUpPaymentIntents.expiresAtMillis],
  paidAtMillis = this[TopUpPaymentIntents.paidAtMillis],
  metadata = this[TopUpPaymentIntents.metadata]
)

private fun ResultRow.toStoreSubscriptionStateDataModel(): StoreSubscriptionStateDataModel = StoreSubscriptionStateDataModel(
  id = this[StoreSubscriptionStates.id].toString(),
  storeId = this[StoreSubscriptionStates.storeId].toString(),
  ownerUserId = this[StoreSubscriptionStates.ownerUserId].toString(),
  planId = this[StoreSubscriptionStates.planId],
  status = this[StoreSubscriptionStates.status],
  autoRenew = this[StoreSubscriptionStates.autoRenew],
  startedAtMillis = this[StoreSubscriptionStates.startedAtMillis],
  currentPeriodStartMillis = this[StoreSubscriptionStates.currentPeriodStartMillis],
  currentPeriodEndMillis = this[StoreSubscriptionStates.currentPeriodEndMillis],
  nextChargeAtMillis = this[StoreSubscriptionStates.nextChargeAtMillis],
  cancelledAtMillis = this[StoreSubscriptionStates.cancelledAtMillis],
  pastDueSinceMillis = this[StoreSubscriptionStates.pastDueSinceMillis],
  updatedAtMillis = this[StoreSubscriptionStates.updatedAtMillis]
)

private fun ResultRow.toStoreSubscriptionChargeDataModel(): StoreSubscriptionChargeDataModel = StoreSubscriptionChargeDataModel(
  id = this[StoreSubscriptionChargeEvents.id].toString(),
  storeId = this[StoreSubscriptionChargeEvents.storeId].toString(),
  userId = this[StoreSubscriptionChargeEvents.userId].toString(),
  planId = this[StoreSubscriptionChargeEvents.planId],
  amountMinor = this[StoreSubscriptionChargeEvents.amountMinor],
  currencyCode = this[StoreSubscriptionChargeEvents.currencyCode],
  periodStartMillis = this[StoreSubscriptionChargeEvents.periodStartMillis],
  periodEndMillis = this[StoreSubscriptionChargeEvents.periodEndMillis],
  status = this[StoreSubscriptionChargeEvents.status],
  walletLedgerEntryId = this[StoreSubscriptionChargeEvents.walletLedgerEntryId],
  createdAtMillis = this[StoreSubscriptionChargeEvents.createdAtMillis],
  note = this[StoreSubscriptionChargeEvents.note]
)

private fun ensureUserWalletInsideTransaction(userId: UUID): UserWalletDataModel? {
  val existing = UserWallets
    .selectAll()
    .where { UserWallets.userId eq userId }
    .singleOrNull()

  if (existing != null) return existing.toUserWalletDataModel()

  val userCountry = Users
    .select(Users.countryLocale)
    .where { Users.id eq userId }
    .singleOrNull()
    ?.get(Users.countryLocale)
    ?: return null

  val walletId = UUID.randomUUID()
  val now = System.currentTimeMillis()
  val currencyCode = countryDefaultCurrencyCode(userCountry)

  UserWallets.insert {
    it[UserWallets.id] = walletId
    it[UserWallets.userId] = userId
    it[UserWallets.currencyCode] = currencyCode
    it[UserWallets.balanceMinor] = 0L
    it[UserWallets.reservedMinor] = 0L
    it[UserWallets.updatedAtMillis] = now
  }

  return UserWallets
    .selectAll()
    .where { UserWallets.id eq walletId }
    .single()
    .toUserWalletDataModel()
}

private fun addWalletLedgerInsideTransaction(
  userId: UUID,
  type: String,
  amountMinor: Long,
  referenceType: String,
  referenceId: String,
  note: String
): WalletLedgerEntryDataModel? {
  val wallet = ensureUserWalletInsideTransaction(userId) ?: return null
  val before = wallet.balanceMinor
  val after = before + amountMinor
  if (after < 0L) return null
  val now = System.currentTimeMillis()
  val entryId = UUID.randomUUID()

  UserWallets.update({ UserWallets.id eq UUID.fromString(wallet.id) }) {
    it[UserWallets.balanceMinor] = after
    it[UserWallets.updatedAtMillis] = now
  }

  UserWalletLedgerEntries.insert {
    it[UserWalletLedgerEntries.id] = entryId
    it[UserWalletLedgerEntries.userId] = userId
    it[UserWalletLedgerEntries.walletId] = UUID.fromString(wallet.id)
    it[UserWalletLedgerEntries.type] = type
    it[UserWalletLedgerEntries.amountMinor] = amountMinor
    it[UserWalletLedgerEntries.balanceBeforeMinor] = before
    it[UserWalletLedgerEntries.balanceAfterMinor] = after
    it[UserWalletLedgerEntries.currencyCode] = wallet.currencyCode
    it[UserWalletLedgerEntries.referenceType] = referenceType
    it[UserWalletLedgerEntries.referenceId] = referenceId
    it[UserWalletLedgerEntries.note] = note
    it[UserWalletLedgerEntries.createdAtMillis] = now
  }

  return UserWalletLedgerEntries
    .selectAll()
    .where { UserWalletLedgerEntries.id eq entryId }
    .single()
    .toWalletLedgerEntryDataModel()
}

private fun financeDashboardInsideTransaction(userId: UUID): UserFinanceDashboardDataModel? {
  val wallet = ensureUserWalletInsideTransaction(userId) ?: return null
  val ledger = UserWalletLedgerEntries
    .selectAll()
    .where { UserWalletLedgerEntries.userId eq userId }
    .orderBy(UserWalletLedgerEntries.createdAtMillis, SortOrder.DESC)
    .limit(200)
    .map { it.toWalletLedgerEntryDataModel() }
  val intents = TopUpPaymentIntents
    .selectAll()
    .where { TopUpPaymentIntents.userId eq userId }
    .orderBy(TopUpPaymentIntents.createdAtMillis, SortOrder.DESC)
    .limit(50)
    .map { it.toTopUpPaymentIntentDataModel() }

  return UserFinanceDashboardDataModel(
    wallet = wallet,
    ledger = ledger,
    paymentIntents = intents,
    paymentProviders = defaultPaymentProviders(),
    subscriptionPlans = defaultStoreSubscriptionPlans()
  )
}

private fun ownerUserIdForStoreInsideTransaction(storeId: UUID): UUID? {
  val rootStoreId = rootStoreIdForAccessInsideTransaction(storeId)
  return Stores
    .select(Stores.ownerUserIds)
    .where { Stores.id eq rootStoreId }
    .singleOrNull()
    ?.get(Stores.ownerUserIds)
    ?.firstOrNull()
    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
}

private fun nextPeriodEndMillis(start: Long, plan: StoreSubscriptionPlanDataModel): Long {
  val days = when (plan.periodUnit) {
    SUBSCRIPTION_PERIOD_YEAR -> 365L * plan.periodCount.coerceAtLeast(1)
    else -> 30L * plan.periodCount.coerceAtLeast(1)
  }
  return start + days * 24L * 60L * 60L * 1000L
}

private fun ensureStoreSubscriptionInsideTransaction(storeId: UUID): StoreSubscriptionStateDataModel? {
  val rootStoreId = rootStoreIdForAccessInsideTransaction(storeId)
  StoreSubscriptionStates
    .selectAll()
    .where { StoreSubscriptionStates.storeId eq rootStoreId }
    .singleOrNull()
    ?.let { return it.toStoreSubscriptionStateDataModel() }

  val ownerId = ownerUserIdForStoreInsideTransaction(rootStoreId) ?: return null
  val id = UUID.randomUUID()
  val now = System.currentTimeMillis()
  StoreSubscriptionStates.insert {
    it[StoreSubscriptionStates.id] = id
    it[StoreSubscriptionStates.storeId] = rootStoreId
    it[StoreSubscriptionStates.ownerUserId] = ownerId
    it[StoreSubscriptionStates.planId] = ""
    it[StoreSubscriptionStates.status] = SUBSCRIPTION_STATUS_INACTIVE
    it[StoreSubscriptionStates.autoRenew] = false
    it[StoreSubscriptionStates.updatedAtMillis] = now
  }
  return StoreSubscriptionStates
    .selectAll()
    .where { StoreSubscriptionStates.id eq id }
    .single()
    .toStoreSubscriptionStateDataModel()
}

private fun subscriptionDashboardInsideTransaction(storeId: UUID): SubscriptionDashboardDataModel? {
  val rootStoreId = rootStoreIdForAccessInsideTransaction(storeId)
  val subscription = ensureStoreSubscriptionInsideTransaction(rootStoreId) ?: return null
  val charges = StoreSubscriptionChargeEvents
    .selectAll()
    .where { StoreSubscriptionChargeEvents.storeId eq rootStoreId }
    .orderBy(StoreSubscriptionChargeEvents.createdAtMillis, SortOrder.DESC)
    .limit(100)
    .map { it.toStoreSubscriptionChargeDataModel() }
  return SubscriptionDashboardDataModel(
    subscription = subscription,
    charges = charges,
    plans = defaultStoreSubscriptionPlans()
  )
}

private fun chargeSubscriptionInsideTransaction(subscription: StoreSubscriptionStateDataModel, now: Long): Boolean {
  val plan = defaultStoreSubscriptionPlans().firstOrNull { it.id == subscription.planId && it.isActive } ?: return false
  if (!subscription.autoRenew || subscription.status == SUBSCRIPTION_STATUS_CANCELLED) return false
  val nextCharge = subscription.nextChargeAtMillis ?: subscription.currentPeriodEndMillis ?: return false
  if (nextCharge > now) return true
  val userId = runCatching { UUID.fromString(subscription.ownerUserId) }.getOrNull() ?: return false
  val storeId = runCatching { UUID.fromString(subscription.storeId) }.getOrNull() ?: return false
  val periodStart = nextCharge
  val periodEnd = nextPeriodEndMillis(periodStart, plan)
  val ledger = addWalletLedgerInsideTransaction(
    userId = userId,
    type = WALLET_LEDGER_SUBSCRIPTION_CHARGE,
    amountMinor = -plan.priceMinor,
    referenceType = "store_subscription",
    referenceId = subscription.id,
    note = "${plan.id} renewal"
  )
  val chargeId = UUID.randomUUID()

  if (ledger == null) {
    StoreSubscriptionStates.update({ StoreSubscriptionStates.id eq UUID.fromString(subscription.id) }) {
      it[StoreSubscriptionStates.status] = SUBSCRIPTION_STATUS_PAST_DUE
      it[StoreSubscriptionStates.pastDueSinceMillis] = subscription.pastDueSinceMillis ?: now
      it[StoreSubscriptionStates.updatedAtMillis] = now
    }
    StoreSubscriptionChargeEvents.insert {
      it[StoreSubscriptionChargeEvents.id] = chargeId
      it[StoreSubscriptionChargeEvents.storeId] = storeId
      it[StoreSubscriptionChargeEvents.userId] = userId
      it[StoreSubscriptionChargeEvents.planId] = plan.id
      it[StoreSubscriptionChargeEvents.amountMinor] = plan.priceMinor
      it[StoreSubscriptionChargeEvents.currencyCode] = plan.currencyCode
      it[StoreSubscriptionChargeEvents.periodStartMillis] = periodStart
      it[StoreSubscriptionChargeEvents.periodEndMillis] = periodEnd
      it[StoreSubscriptionChargeEvents.status] = "failed_insufficient_balance"
      it[StoreSubscriptionChargeEvents.walletLedgerEntryId] = ""
      it[StoreSubscriptionChargeEvents.createdAtMillis] = now
      it[StoreSubscriptionChargeEvents.note] = "Insufficient balance"
    }
    return false
  }

  StoreSubscriptionStates.update({ StoreSubscriptionStates.id eq UUID.fromString(subscription.id) }) {
    it[StoreSubscriptionStates.status] = SUBSCRIPTION_STATUS_ACTIVE
    it[StoreSubscriptionStates.currentPeriodStartMillis] = periodStart
    it[StoreSubscriptionStates.currentPeriodEndMillis] = periodEnd
    it[StoreSubscriptionStates.nextChargeAtMillis] = periodEnd
    it[StoreSubscriptionStates.pastDueSinceMillis] = null
    it[StoreSubscriptionStates.updatedAtMillis] = now
  }
  StoreSubscriptionChargeEvents.insert {
    it[StoreSubscriptionChargeEvents.id] = chargeId
    it[StoreSubscriptionChargeEvents.storeId] = storeId
    it[StoreSubscriptionChargeEvents.userId] = userId
    it[StoreSubscriptionChargeEvents.planId] = plan.id
    it[StoreSubscriptionChargeEvents.amountMinor] = plan.priceMinor
    it[StoreSubscriptionChargeEvents.currencyCode] = plan.currencyCode
    it[StoreSubscriptionChargeEvents.periodStartMillis] = periodStart
    it[StoreSubscriptionChargeEvents.periodEndMillis] = periodEnd
    it[StoreSubscriptionChargeEvents.status] = "paid"
    it[StoreSubscriptionChargeEvents.walletLedgerEntryId] = ledger.id
    it[StoreSubscriptionChargeEvents.createdAtMillis] = now
    it[StoreSubscriptionChargeEvents.note] = "Auto-renewal"
  }
  return true
}

private fun runDueSubscriptionRenewalsOnce() {
  org.jetbrains.exposed.sql.transactions.transaction {
    val now = System.currentTimeMillis()
    StoreSubscriptionStates
      .selectAll()
      .where {
        (StoreSubscriptionStates.autoRenew eq true) and
           (StoreSubscriptionStates.status inList listOf(SUBSCRIPTION_STATUS_ACTIVE, SUBSCRIPTION_STATUS_PAST_DUE))
      }
      .map { it.toStoreSubscriptionStateDataModel() }
      .forEach { chargeSubscriptionInsideTransaction(it, now) }
  }
}

private var subscriptionRenewalDaemonStarted = false
private fun Application.startSubscriptionRenewalDaemon(backgroundScope: CoroutineScope) {
  if (subscriptionRenewalDaemonStarted) return
  subscriptionRenewalDaemonStarted = true
  backgroundScope.launch {
    while (true) {
      runCatching { runDueSubscriptionRenewalsOnce() }
        .onFailure { log.error("Subscription renewal daemon iteration failed", it) }
      delay(60_000L)
    }
  }
}

private fun simpleMessage(
  main: String,
  en: String = main,
  ru: String = main,
  kk: String = main
): List<LocalizedStringDataModel> {
  return listOf(
    LocalizedStringDataModel("main", main),
    LocalizedStringDataModel("en", en),
    LocalizedStringDataModel("ru", ru),
    LocalizedStringDataModel("kk", kk)
  )
}

private fun cleanOptionalText(value: String?): String? {
  return value?.trim()?.takeIf { it.isNotBlank() }
}

private fun cleanLocalizedValues(values: List<LocalizedStringDataModel>): List<LocalizedStringDataModel> {
  return values
    .mapNotNull { value ->
      val language = value.language.trim().ifBlank { "main" }
      val text = value.value.trim()
      if (text.isBlank()) null else LocalizedStringDataModel(language, text)
    }
    .distinctBy { it.language.lowercase() }
}

private fun localizedNoteForStorage(text: String?, values: List<LocalizedStringDataModel>): List<LocalizedStringDataModel> {
  val cleanValues = cleanLocalizedValues(values)
  val cleanText = cleanOptionalText(text)
  return when {
    cleanValues.isNotEmpty() -> cleanValues
    cleanText != null -> listOf(LocalizedStringDataModel("main", cleanText))
    else -> emptyList()
  }
}

private fun RoutingCall.headerUuid(name: String): UUID? {
  return request.header(name)
    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
}

private fun String?.optionalUuidOrNull(): UUID? {
  val clean = this?.trim()?.takeIf { it.isNotBlank() } ?: return null
  return runCatching { UUID.fromString(clean) }.getOrNull()
}

private fun RoutingCall.inventoryContextStoreId(): UUID? {
  return headerUuid("store_id")
    ?: headerUuid("store-id")
    ?: headerUuid("active_store_id")
    ?: headerUuid("active-store-id")
}

private fun activeStoreIdForUserInsideTransaction(userId: UUID): UUID? {
  return Users
    .select(Users.activeStoreId)
    .where { Users.id eq userId }
    .singleOrNull()
    ?.get(Users.activeStoreId)
}

private fun storesShareInventoryRootInsideTransaction(firstStoreId: UUID, secondStoreId: UUID): Boolean {
  return rootStoreIdForAccessInsideTransaction(firstStoreId) == rootStoreIdForAccessInsideTransaction(secondStoreId)
}

private fun RoutingCall.matchesInventoryContextStoreIdInsideTransaction(userId: UUID, storeId: UUID): Boolean {
  val headerStoreId = inventoryContextStoreId()
  if (headerStoreId != null) {
    return storesShareInventoryRootInsideTransaction(headerStoreId, storeId)
  }

  val activeStoreId = activeStoreIdForUserInsideTransaction(userId)
  return activeStoreId == null || storesShareInventoryRootInsideTransaction(activeStoreId, storeId)
}

private fun RoutingCall.matchesAnyInventoryContextStoreIdInsideTransaction(userId: UUID, storeIds: Set<UUID>): Boolean {
  val headerStoreId = inventoryContextStoreId()
  if (headerStoreId != null) {
    return storeIds.any { storesShareInventoryRootInsideTransaction(headerStoreId, it) }
  }

  val activeStoreId = activeStoreIdForUserInsideTransaction(userId)
  return activeStoreId == null || storeIds.any { storesShareInventoryRootInsideTransaction(activeStoreId, it) }
}

private suspend inline fun <reified T : Any> RoutingCall.receiveAita(): T {
  return withAitaServerRuntimeClassLoader("receive:${request.httpMethod.value}:${request.path()}:${T::class.qualifiedName}") {
    runCatching { receive<T>() }.getOrElse { throwable ->
      if (throwable.isClassLoadingFailure()) {
        refreshSharedRuntimeSerializersAfterClassLoadingFailure("receive:${T::class.qualifiedName}", throwable)
      }
      throw throwable
    }
  }
}

private suspend fun RoutingCall.receiveTextAita(): String {
  return withAitaServerRuntimeClassLoader("receive-text:${request.httpMethod.value}:${request.path()}") {
    receiveText()
  }
}

private suspend inline fun <reified T : Any> RoutingCall.receiveOneOrList(): List<T> {
  return withAitaServerRuntimeClassLoader("receive-one-or-list:${request.httpMethod.value}:${request.path()}:${T::class.qualifiedName}") {
    val raw = receiveText().trim()
    runCatching {
      if (raw.startsWith("[")) {
        jsonBase.decodeFromString<List<T>>(raw)
      } else {
        listOf(jsonBase.decodeFromString<T>(raw))
      }
    }.getOrElse { throwable ->
      if (throwable.isClassLoadingFailure()) {
        refreshSharedRuntimeSerializersAfterClassLoadingFailure("receive-one-or-list:${T::class.qualifiedName}", throwable)
      }
      throw throwable
    }
  }
}

private fun List<String>.cleanBarcodes(): List<String> {
  return flatMap { it.trim().toStoredGoodsItemBarcodeCandidates() }
    .filter { it.isNotEmpty() }
    .distinct()
}

private fun GoodsItemDataModel.cleanBarcodeModelsForStore(storeId: UUID): List<GoodsItemBarcodeDataModel> {
  return barcodeModels.normalizedGoodsItemBarcodesForStore(storeId.toString(), barcodes)
}

private fun List<GoodsItemBarcodeDataModel>.cleanBarcodeStrings(): List<String> {
  return toLegacyBarcodeStrings().cleanBarcodes()
}

private fun ResultRow.stockBarcodeModels(): List<GoodsItemBarcodeDataModel> {
  return this[StockItems.barcodeModels].normalizedGoodsItemBarcodesForStore(
    storeId = this[StockItems.storeId].toString(),
    legacyBarcodes = this[StockItems.barcodes]
  )
}

private fun ResultRow.stockBarcodeValues(): List<String> {
  return stockBarcodeModels().cleanBarcodeStrings().ifEmpty { this[StockItems.barcodes].cleanBarcodes() }
}

private fun GoodsItemBarcodeDataModel.matchesScannedBarcodeForStore(
  scannedBarcode: String,
  rowStoreId: UUID,
  currentStoreId: UUID
): Boolean {
  val cleanType = type.normalizedGoodsItemBarcodeType(value)
  if (cleanType == GOODS_ITEM_BARCODE_TYPE_INTERNAL) {
    val scopedStoreId = storeId
      ?.trim()
      ?.takeIf { it.isNotBlank() }
      ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
      ?: rowStoreId
    if (scopedStoreId != currentStoreId || rowStoreId != currentStoreId) return false
  }

  return storedBarcodeMatchesScannedTransactionBarcode(value, scannedBarcode)
}

private fun ResultRow.stockBarcodeMatchesScannedBarcode(
  scannedBarcode: String,
  currentStoreId: UUID
): Boolean {
  val rowStoreId = this[StockItems.storeId]
  return stockBarcodeModels().any { model ->
    model.matchesScannedBarcodeForStore(scannedBarcode, rowStoreId, currentStoreId)
  }
}

private fun GoodsItemBarcodeDataModel.scopedInternalStoreIdOrNull(rowStoreId: UUID): UUID? {
  val cleanType = type.normalizedGoodsItemBarcodeType(value)
  if (cleanType != GOODS_ITEM_BARCODE_TYPE_INTERNAL) return null

  return storeId
    ?.trim()
    ?.takeIf { it.isNotBlank() }
    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
    ?: rowStoreId
}

private fun stockBarcodeModelsConflictInsideStore(
  incomingBarcode: GoodsItemBarcodeDataModel,
  incomingRowStoreId: UUID,
  existingBarcode: GoodsItemBarcodeDataModel,
  existingRowStoreId: UUID
): Boolean {
  val incomingType = incomingBarcode.type.normalizedGoodsItemBarcodeType(incomingBarcode.value)
  val existingType = existingBarcode.type.normalizedGoodsItemBarcodeType(existingBarcode.value)

  if (incomingType != existingType) return false

  if (incomingType == GOODS_ITEM_BARCODE_TYPE_INTERNAL) {
    val incomingScope = incomingBarcode.scopedInternalStoreIdOrNull(incomingRowStoreId) ?: incomingRowStoreId
    val existingScope = existingBarcode.scopedInternalStoreIdOrNull(existingRowStoreId) ?: existingRowStoreId
    if (incomingScope != existingScope) return false
  }

  return storedBarcodeMatchesScannedTransactionBarcode(
    storedBarcode = existingBarcode.value,
    scannedBarcode = incomingBarcode.value
  ) || storedBarcodeMatchesScannedTransactionBarcode(
    storedBarcode = incomingBarcode.value,
    scannedBarcode = existingBarcode.value
  )
}

private fun rootStoreIdForAccessInsideTransaction(storeId: UUID): UUID {
  return Stores
    .select(Stores.parentStoreId)
    .where { Stores.id eq storeId }
    .singleOrNull()
    ?.get(Stores.parentStoreId)
    ?: storeId
}

private fun stockVisibleStoreIdsInsideTransaction(storeId: UUID): List<UUID> {
  val rootStoreId = rootStoreIdForAccessInsideTransaction(storeId)
  val directBranches = Stores
    .select(Stores.id)
    .where { Stores.parentStoreId eq rootStoreId }
    .map { it[Stores.id] }

  return (listOf(rootStoreId) + directBranches).distinct()
}

private fun userHasStoreAccessInsideTransaction(
  userId: UUID,
  storeId: UUID
): Boolean {
  val rootStoreId = rootStoreIdForAccessInsideTransaction(storeId)

  val directAccess = StoreUsers
    .selectAll()
    .where {
      (StoreUsers.userId eq userId) and
         ((StoreUsers.storeId eq storeId) or (StoreUsers.storeId eq rootStoreId))
    }
    .empty()
    .not()

  if (directAccess) return true

  return StoreWorkerMemberships
    .selectAll()
    .where {
      (StoreWorkerMemberships.userId eq userId) and
         ((StoreWorkerMemberships.storeId eq storeId) or (StoreWorkerMemberships.storeId eq rootStoreId)) and
         (StoreWorkerMemberships.isActive eq true)
    }
    .empty()
    .not()
}

private fun userHasSupplierAccessInsideTransaction(
  userId: UUID,
  supplierId: UUID
): Boolean {
  val supplier = Suppliers
    .select(Suppliers.userIds)
    .where { (Suppliers.id eq supplierId) and (Suppliers.isActive eq true) }
    .singleOrNull()
    ?: return false

  return decodeSupplierStringList(supplier[Suppliers.userIds]).contains(userId.toString())
}


private fun isStoreOwnerInsideTransaction(userId: UUID, storeId: UUID): Boolean {
  val rootStoreId = rootStoreIdForAccessInsideTransaction(storeId)
  return Stores
    .select(Stores.ownerUserIds)
    .where { Stores.id eq rootStoreId }
    .singleOrNull()
    ?.get(Stores.ownerUserIds)
    ?.contains(userId.toString()) == true
}

private fun activeWorkerPermissionsInsideTransaction(userId: UUID, storeId: UUID): List<String> {
  val rootStoreId = rootStoreIdForAccessInsideTransaction(storeId)
  return normalizeStorePermissionIds(
    StoreWorkerMemberships
      .select(StoreWorkerMemberships.permissions)
      .where {
        (StoreWorkerMemberships.userId eq userId) and
           ((StoreWorkerMemberships.storeId eq storeId) or (StoreWorkerMemberships.storeId eq rootStoreId)) and
           (StoreWorkerMemberships.isActive eq true)
      }
      .flatMap { it[StoreWorkerMemberships.permissions] }
  )
}

private fun userHasStorePermissionInsideTransaction(userId: UUID, storeId: UUID, permission: String): Boolean {
  if (isStoreOwnerInsideTransaction(userId, storeId)) return true
  val permissions = activeWorkerPermissionsInsideTransaction(userId, storeId)
  if (permission in permissions) return true
  val legacyExpansion = when (permission) {
    STORE_PERMISSION_STOCK_WRITE -> STORE_PERMISSION_LEGACY_EXPANSIONS[permission].orEmpty().filterNot { it == STORE_PERMISSION_STOCK_READ }
    STORE_PERMISSION_WORKERS_MANAGE -> STORE_PERMISSION_LEGACY_EXPANSIONS[permission].orEmpty().filterNot { it == STORE_PERMISSION_WORKERS_VIEW }
    else -> emptyList()
  }
  return legacyExpansion.any { it in permissions }
}

private fun requiredPermissionForTransactionType(type: String): String? {
  return when (type) {
    "purchase" -> STORE_PERMISSION_SALE_TRANSACTION
    "return" -> STORE_PERMISSION_RETURN_TRANSACTION
    "accept" -> STORE_PERMISSION_SUPPLY_TRANSACTION
    else -> null
  }
}

private fun cleanPermissionIds(input: List<String>): List<String> = normalizeStorePermissionIds(input)

private fun cleanWorkerRoleId(input: String): String {
  val clean = input.trim().take(80).filter { it.isLetterOrDigit() || it == '_' || it == '-' }
  return clean.ifBlank { WORKER_ROLE_STANDARD }
}

private fun cleanWorkerJobTitle(input: String?): String = input
  ?.trim()
  ?.replace(Regex("\\s+"), " ")
  ?.take(120)
  ?.takeIf { it.isNotBlank() }
  .orEmpty()

private fun cleanWorkerSalary(input: String?): String {
  val clean = input
    ?.trim()
    ?.replace(',', '.')
    ?.filter { it.isDigit() || it == '.' }
    ?.let { raw ->
      val firstDot = raw.indexOf('.')
      if (firstDot < 0) raw else raw.take(firstDot + 1) + raw.drop(firstDot + 1).replace(".", "")
    }
    ?.trim('.')
    ?.take(16)
    .orEmpty()
  if (clean.isBlank()) return ""
  val number = clean.toDoubleOrNull() ?: return ""
  if (number < 0.0) return ""
  val parts = clean.split('.', limit = 2)
  val whole = parts.getOrNull(0).orEmpty().trimStart('0').ifBlank { "0" }
  val fractional = parts.getOrNull(1)?.take(2).orEmpty()
  return if (fractional.isBlank()) whole else "$whole.$fractional"
}

private fun cleanWorkerSalaryCurrencyCode(input: String?): String {
  val clean = input
    ?.trim()
    ?.uppercase()
    ?.filter { it in 'A'..'Z' }
    ?.take(4)
    .orEmpty()
  return clean.ifBlank { "KZT" }
}

private fun canAssignWorkerPermissionsInsideTransaction(userId: UUID, storeId: UUID): Boolean {
  return isStoreOwnerInsideTransaction(userId, storeId) ||
     userHasStorePermissionInsideTransaction(userId, storeId, STORE_PERMISSION_WORKERS_INVITE) ||
     userHasStorePermissionInsideTransaction(userId, storeId, STORE_PERMISSION_WORKERS_DECIDE_REQUESTS) ||
     userHasStorePermissionInsideTransaction(userId, storeId, STORE_PERMISSION_WORKERS_EDIT_PERMISSIONS) ||
     userHasStorePermissionInsideTransaction(userId, storeId, STORE_PERMISSION_WORKER_ROLE_TEMPLATES_MANAGE)
}

private fun assignableStorePermissionsInsideTransaction(
  actorUserId: UUID,
  storeId: UUID,
  requestedPermissions: List<String>
): List<String>? {
  val permissions = cleanPermissionIds(requestedPermissions)
  if (isStoreOwnerInsideTransaction(actorUserId, storeId)) return permissions

  val actorPermissions = activeWorkerPermissionsInsideTransaction(actorUserId, storeId).toSet()
  val canAssign = STORE_PERMISSION_WORKERS_INVITE in actorPermissions ||
     STORE_PERMISSION_WORKERS_DECIDE_REQUESTS in actorPermissions ||
     STORE_PERMISSION_WORKERS_EDIT_PERMISSIONS in actorPermissions ||
     STORE_PERMISSION_WORKER_ROLE_TEMPLATES_MANAGE in actorPermissions
  if (!canAssign) return null

  return permissions.takeIf { requested -> requested.all { it in actorPermissions } }
}

private fun actorCanManageExistingWorkerPermissionsInsideTransaction(
  actorUserId: UUID,
  storeId: UUID,
  existingPermissions: List<String>
): Boolean {
  if (isStoreOwnerInsideTransaction(actorUserId, storeId)) return true

  val actorPermissions = activeWorkerPermissionsInsideTransaction(actorUserId, storeId).toSet()
  if (STORE_PERMISSION_WORKERS_EDIT_PERMISSIONS !in actorPermissions) return false

  return cleanPermissionIds(existingPermissions).all { it in actorPermissions }
}

private fun userRequiresWorkshiftInsideTransaction(userId: UUID, storeId: UUID): Boolean {
  if (isStoreOwnerInsideTransaction(userId, storeId)) return false
  val rootStoreId = rootStoreIdForAccessInsideTransaction(storeId)
  return StoreWorkerMemberships
    .select(StoreWorkerMemberships.id)
    .where {
      (StoreWorkerMemberships.userId eq userId) and
         ((StoreWorkerMemberships.storeId eq storeId) or (StoreWorkerMemberships.storeId eq rootStoreId)) and
         (StoreWorkerMemberships.isActive eq true)
    }
    .empty()
    .not()
}

private fun activeWorkshiftIdInsideTransaction(userId: UUID, storeId: UUID): UUID? {
  val exactShift = Workshifts
    .select(Workshifts.id)
    .where {
      (Workshifts.workerUserId eq userId) and
         (Workshifts.storeId eq storeId) and
         (Workshifts.isActive eq true) and
         Workshifts.endedAtMillis.isNull()
    }
    .orderBy(Workshifts.startedAtMillis, SortOrder.DESC)
    .limit(1)
    .singleOrNull()
    ?.get(Workshifts.id)

  if (exactShift != null) return exactShift

  val rootStoreId = rootStoreIdForAccessInsideTransaction(storeId)
  val visibleStoreIds = stockVisibleStoreIdsInsideTransaction(rootStoreId)
    .filter { it != storeId }

  if (visibleStoreIds.isEmpty()) return null

  return Workshifts
    .select(Workshifts.id)
    .where {
      (Workshifts.workerUserId eq userId) and
         (Workshifts.storeId inList visibleStoreIds) and
         (Workshifts.isActive eq true) and
         Workshifts.endedAtMillis.isNull()
    }
    .orderBy(Workshifts.startedAtMillis, SortOrder.DESC)
    .limit(1)
    .singleOrNull()
    ?.get(Workshifts.id)
}

private fun userHasRequiredActiveWorkshiftInsideTransaction(userId: UUID, storeId: UUID): Boolean {
  return !userRequiresWorkshiftInsideTransaction(userId, storeId) || activeWorkshiftIdInsideTransaction(userId, storeId) != null
}

private fun userCanUseStoreActionInsideTransaction(userId: UUID, storeId: UUID, permission: String, requireWorkshift: Boolean = true): Boolean {
  if (!userHasStorePermissionInsideTransaction(userId, storeId, permission)) return false
  if (requireWorkshift && !userHasRequiredActiveWorkshiftInsideTransaction(userId, storeId)) return false
  return true
}

private fun ResultRow.toOperationLogDataModel(): OperationLogDataModel {
  return OperationLogDataModel(
    id = this[OperationLogs.id].toString(),
    rootStoreId = this[OperationLogs.rootStoreId].toString(),
    storeId = this[OperationLogs.storeId].toString(),
    storePublicId = this[OperationLogs.storePublicId],
    storeName = this[OperationLogs.storeName],
    actorUserId = this[OperationLogs.actorUserId].toString(),
    actorPublicId = this[OperationLogs.actorPublicId],
    actorDisplayName = this[OperationLogs.actorDisplayName],
    workshiftId = this[OperationLogs.workshiftId]?.toString(),
    action = this[OperationLogs.action],
    entityType = this[OperationLogs.entityType],
    entityId = this[OperationLogs.entityId],
    title = this[OperationLogs.title],
    details = this[OperationLogs.details],
    metadata = this[OperationLogs.metadata],
    createdAtMillis = this[OperationLogs.createdAtMillis]
  )
}

private fun operationLogStoreIdsForScopeInsideTransaction(storeId: UUID, scope: String): List<UUID> {
  val rootStoreId = rootStoreIdForAccessInsideTransaction(storeId)
  val parentScope = scope.equals(OPERATION_LOG_SCOPE_ROOT, ignoreCase = true) || storeId == rootStoreId

  return if (parentScope) {
    listOf(rootStoreId) + Stores
      .select(Stores.id)
      .where { Stores.parentStoreId eq rootStoreId }
      .map { it[Stores.id] }
  } else {
    listOf(storeId)
  }
}


private fun operationLogActionForHttpMutation(method: String, path: String): String {
  val normalized = path.lowercase()
  return when {
    "transactions/complete" in normalized -> OPERATION_LOG_ACTION_COMPLETED
    "cashregister/extract" in normalized -> OPERATION_LOG_ACTION_EXTRACTED
    "workshifts/start" in normalized -> OPERATION_LOG_ACTION_STARTED
    "workshifts/end" in normalized -> OPERATION_LOG_ACTION_ENDED
    "workers/removal/confirm" in normalized -> OPERATION_LOG_ACTION_ACCEPTED
    "workers/removal/decline" in normalized -> OPERATION_LOG_ACTION_DECLINED
    "workers/accept" in normalized || "invitations/accept" in normalized -> OPERATION_LOG_ACTION_ACCEPTED
    "workers/decline" in normalized || "invitations/decline" in normalized -> OPERATION_LOG_ACTION_DECLINED
    "workers/invite" in normalized -> OPERATION_LOG_ACTION_INVITED
    "workers/remove" in normalized -> OPERATION_LOG_ACTION_CREATED
    "stockbatches/move" in normalized -> OPERATION_LOG_ACTION_MOVED
    method.equals("DELETE", ignoreCase = true) || normalized.contains("delete") -> OPERATION_LOG_ACTION_DELETED
    method.equals("PUT", ignoreCase = true) || normalized.contains("update") -> OPERATION_LOG_ACTION_UPDATED
    else -> OPERATION_LOG_ACTION_CREATED
  }
}

private fun operationLogEntityForPath(path: String): String {
  val normalized = path.lowercase()
  return when {
    normalized.startsWith("stockbatches") -> OPERATION_LOG_ENTITY_STOCK_BATCH
    normalized.startsWith("stock") -> OPERATION_LOG_ENTITY_STOCK_ITEM
    normalized.startsWith("transactions") -> OPERATION_LOG_ENTITY_TRANSACTION
    normalized.startsWith("cashregister") -> OPERATION_LOG_ENTITY_CASH_REGISTER
    normalized.startsWith("workers") -> OPERATION_LOG_ENTITY_WORKER
    normalized.startsWith("workshifts") -> OPERATION_LOG_ENTITY_WORKSHIFT
    normalized.startsWith("stores") -> OPERATION_LOG_ENTITY_STORE
    normalized.startsWith("suppliers") -> OPERATION_LOG_ENTITY_SUPPLIER
    normalized.startsWith("subscriptions") -> OPERATION_LOG_ENTITY_SUBSCRIPTION
    normalized.startsWith("finance") -> OPERATION_LOG_ENTITY_FINANCE
    else -> path.substringBefore('/').ifBlank { "system" }
  }
}


private data class OperationLogHumanText(
  val main: String,
  val en: String = main,
  val ru: String = main,
  val kk: String = main
)

private fun operationLogEntityHumanText(entityType: String): OperationLogHumanText {
  return when (entityType) {
    OPERATION_LOG_ENTITY_STORE -> OperationLogHumanText("Store", ru = "Магазин", kk = "Дүкен")
    OPERATION_LOG_ENTITY_WORKER -> OperationLogHumanText("Worker", ru = "Сотрудник", kk = "Қызметкер")
    OPERATION_LOG_ENTITY_WORKSHIFT -> OperationLogHumanText("Workshift", ru = "Смена", kk = "Ауысым")
    OPERATION_LOG_ENTITY_STOCK_ITEM -> OperationLogHumanText("Stock item", ru = "Товар", kk = "Тауар")
    OPERATION_LOG_ENTITY_STOCK_BATCH -> OperationLogHumanText("Stock batch", ru = "Партия", kk = "Партия")
    OPERATION_LOG_ENTITY_TRANSACTION -> OperationLogHumanText("Transaction", ru = "Транзакция", kk = "Транзакция")
    OPERATION_LOG_ENTITY_CASH_REGISTER -> OperationLogHumanText("Cash register", ru = "Касса", kk = "Касса")
    OPERATION_LOG_ENTITY_SUPPLIER -> OperationLogHumanText("Supplier", ru = "Поставщик", kk = "Жеткізуші")
    OPERATION_LOG_ENTITY_SUBSCRIPTION -> OperationLogHumanText("Subscription", ru = "Подписка", kk = "Жазылым")
    OPERATION_LOG_ENTITY_FINANCE -> OperationLogHumanText("Finance", ru = "Финансы", kk = "Қаржы")
    else -> OperationLogHumanText("Operation", ru = "Операция", kk = "Операция")
  }
}

private fun operationLogTransactionTypeHumanText(type: String): OperationLogHumanText {
  return when (type.trim().lowercase(Locale.ROOT)) {
    "purchase", "sale" -> OperationLogHumanText("Sale", ru = "Продажа", kk = "Сату")
    "return" -> OperationLogHumanText("Return", ru = "Возврат", kk = "Қайтару")
    "supply" -> OperationLogHumanText("Supply", ru = "Поставка", kk = "Жеткізу")
    else -> OperationLogHumanText(type.ifBlank { "Transaction" }, ru = type.ifBlank { "Транзакция" }, kk = type.ifBlank { "Транзакция" })
  }
}

private fun operationLogHumanTitleFor(action: String, entityType: String): List<LocalizedStringDataModel> {
  val entity = operationLogEntityHumanText(entityType)
  return when (action) {
    OPERATION_LOG_ACTION_CREATED -> simpleMessage(
      main = "${entity.main} saved",
      en = "${entity.en} saved",
      ru = "${entity.ru} сохранён",
      kk = "${entity.kk} сақталды"
    )
    OPERATION_LOG_ACTION_UPDATED -> simpleMessage(
      main = "${entity.main} updated",
      en = "${entity.en} updated",
      ru = "${entity.ru} обновлён",
      kk = "${entity.kk} жаңартылды"
    )
    OPERATION_LOG_ACTION_DELETED -> simpleMessage(
      main = "${entity.main} deleted",
      en = "${entity.en} deleted",
      ru = "${entity.ru} удалён",
      kk = "${entity.kk} жойылды"
    )
    OPERATION_LOG_ACTION_COMPLETED -> simpleMessage(
      main = "${entity.main} completed",
      en = "${entity.en} completed",
      ru = "${entity.ru} завершена",
      kk = "${entity.kk} аяқталды"
    )
    OPERATION_LOG_ACTION_EXTRACTED -> simpleMessage(
      main = "Cash extracted",
      ru = "Наличные изъяты",
      kk = "Қолма-қол ақша алынды"
    )
    OPERATION_LOG_ACTION_STARTED -> simpleMessage(
      main = "Workshift started",
      ru = "Смена начата",
      kk = "Ауысым басталды"
    )
    OPERATION_LOG_ACTION_ENDED -> simpleMessage(
      main = "Workshift ended",
      ru = "Смена завершена",
      kk = "Ауысым аяқталды"
    )
    OPERATION_LOG_ACTION_ACCEPTED -> simpleMessage(
      main = "Request accepted",
      ru = "Заявка принята",
      kk = "Өтінім қабылданды"
    )
    OPERATION_LOG_ACTION_DECLINED -> simpleMessage(
      main = "Request declined",
      ru = "Заявка отклонена",
      kk = "Өтінім қабылданбады"
    )
    OPERATION_LOG_ACTION_INVITED -> simpleMessage(
      main = "Worker invited",
      ru = "Сотрудник приглашён",
      kk = "Қызметкер шақырылды"
    )
    OPERATION_LOG_ACTION_MOVED -> simpleMessage(
      main = "Stock moved",
      ru = "Склад перемещён",
      kk = "Қор жылжытылды"
    )
    else -> simpleMessage(
      main = "Operation completed",
      ru = "Операция выполнена",
      kk = "Операция орындалды"
    )
  }
}

private fun operationLogHumanDetailsFor(action: String, entityType: String): List<LocalizedStringDataModel> {
  val entity = operationLogEntityHumanText(entityType)
  return simpleMessage(
    main = "${entity.main}: ${operationLogHumanTitleFor(action, entityType).extractLocalizedString("main").orEmpty()}",
    en = "${entity.en}: ${operationLogHumanTitleFor(action, entityType).extractLocalizedString("en").orEmpty()}",
    ru = "${entity.ru}: ${operationLogHumanTitleFor(action, entityType).extractLocalizedString("ru").orEmpty()}",
    kk = "${entity.kk}: ${operationLogHumanTitleFor(action, entityType).extractLocalizedString("kk").orEmpty()}"
  )
}


private fun Double.operationLogNumberText(): String {
  val longValue = toLong()
  return if (this == longValue.toDouble()) {
    longValue.toString()
  } else {
    toString().trimEnd('0').trimEnd('.')
  }
}

private fun List<LocalizedStringDataModel>.operationLogVisibleText(fallback: String = ""): String {
  return extractLocalizedString("main")
    ?: extractLocalizedString("en")
    ?: extractLocalizedString("ru")
    ?: extractLocalizedString("kk")
    ?: firstOrNull { it.value.isNotBlank() }?.value
    ?: fallback
}

private fun PriceDataModel?.operationLogPriceText(): String? {
  val price = this?.price?.trim().orEmpty()
  if (price.isBlank()) return null
  return listOf(price, this?.currency.orEmpty().trim()).filter { it.isNotBlank() }.joinToString(" ")
}

private fun QuantityDataModel.operationLogQuantityText(fallbackUnitId: String = ""): String {
  val unit = id.ifBlank { fallbackUnitId }.trim()
  return listOf(total.operationLogNumberText(), unit).filter { it.isNotBlank() }.joinToString(" ")
}

private data class StockBatchOperationLogText(
  val title: List<LocalizedStringDataModel>,
  val details: List<LocalizedStringDataModel>,
  val metadata: Map<String, String>
)

private fun stockBatchOperationLogTextInsideTransaction(
  action: String,
  batchId: UUID,
  goodsItemId: UUID,
  batch: GoodsBatchDataModel
): StockBatchOperationLogText {
  val itemRow = StockItems
    .select(StockItems.name, StockItems.barcodes, StockItems.measurementUnitId)
    .where { StockItems.id eq goodsItemId }
    .singleOrNull()

  val goodsName = itemRow
    ?.get(StockItems.name)
    ?.operationLogVisibleText(goodsItemId.toString())
    ?.ifBlank { goodsItemId.toString() }
    ?: goodsItemId.toString()

  val barcode = itemRow
    ?.get(StockItems.barcodes)
    ?.firstOrNull()
    .orEmpty()

  val unitId = itemRow?.get(StockItems.measurementUnitId).orEmpty()
  val quantityText = batch.quantity.operationLogQuantityText(unitId)

  val supplierId = batch.supplierId
    ?.takeIf { it.isNotBlank() }
    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }

  val supplierName = supplierId
    ?.let { id -> Suppliers.select(Suppliers.name).where { Suppliers.id eq id }.singleOrNull()?.get(Suppliers.name) }
    ?.trim()
    ?.takeIf { it.isNotBlank() }

  val supplyText = batch.supplyPrice.operationLogPriceText()
  val saleText = batch.salePriceOverride.operationLogPriceText()
  val returnText = batch.returnPriceOverride.operationLogPriceText()
  val wholesaleText = batch.wholesalePriceOverride.operationLogPriceText()
  val statusText = batch.status.name

  val title = when (action) {
    OPERATION_LOG_ACTION_UPDATED -> simpleMessage(
      main = "Batch updated: $goodsName",
      en = "Batch updated: $goodsName",
      ru = "Партия обновлена: $goodsName",
      kk = "Партия жаңартылды: $goodsName"
    )
    OPERATION_LOG_ACTION_DELETED -> simpleMessage(
      main = "Batch removed: $goodsName",
      en = "Batch removed: $goodsName",
      ru = "Партия удалена: $goodsName",
      kk = "Партия жойылды: $goodsName"
    )
    OPERATION_LOG_ACTION_MOVED -> simpleMessage(
      main = "Batch moved: $goodsName",
      en = "Batch moved: $goodsName",
      ru = "Партия перемещена: $goodsName",
      kk = "Партия жылжытылды: $goodsName"
    )
    OPERATION_LOG_ACTION_ACCEPTED -> simpleMessage(
      main = "Batch move accepted: $goodsName",
      en = "Batch move accepted: $goodsName",
      ru = "Перемещение партии принято: $goodsName",
      kk = "Партия ауыстыруы қабылданды: $goodsName"
    )
    OPERATION_LOG_ACTION_DECLINED -> simpleMessage(
      main = "Batch move declined: $goodsName",
      en = "Batch move declined: $goodsName",
      ru = "Перемещение партии отклонено: $goodsName",
      kk = "Партия ауыстыруы қабылданбады: $goodsName"
    )
    else -> simpleMessage(
      main = "Batch added: $goodsName",
      en = "Batch added: $goodsName",
      ru = "Партия добавлена: $goodsName",
      kk = "Партия қосылды: $goodsName"
    )
  }

  fun detailLine(
    quantityLabel: String,
    supplierLabel: String,
    barcodeLabel: String,
    supplyLabel: String,
    saleLabel: String,
    returnLabel: String,
    wholesaleLabel: String,
    statusLabel: String
  ): String {
    return listOfNotNull(
      goodsName,
      barcode.takeIf { it.isNotBlank() }?.let { "$barcodeLabel: $it" },
      "$quantityLabel: $quantityText",
      supplierName?.let { "$supplierLabel: $it" },
      supplyText?.let { "$supplyLabel: $it" },
      saleText?.let { "$saleLabel: $it" },
      returnText?.let { "$returnLabel: $it" },
      wholesaleText?.let { "$wholesaleLabel: $it" },
      "$statusLabel: $statusText"
    ).joinToString(" • ")
  }

  val details = simpleMessage(
    main = detailLine("Qty", "Supplier", "Barcode", "Supply", "Sale", "Return", "Wholesale", "Status"),
    en = detailLine("Qty", "Supplier", "Barcode", "Supply", "Sale", "Return", "Wholesale", "Status"),
    ru = detailLine("Кол-во", "Поставщик", "Штрихкод", "Закупка", "Продажа", "Возврат", "Опт", "Статус"),
    kk = detailLine("Саны", "Жеткізуші", "Штрихкод", "Жеткізу", "Сату", "Қайтару", "Көтерме", "Күйі")
  )

  val metadata = linkedMapOf(
    "batch_id" to batchId.toString(),
    "goods_item_id" to goodsItemId.toString(),
    "goods_name" to goodsName,
    "barcode" to barcode,
    "quantity" to batch.quantity.total.toString(),
    "quantity_unit" to batch.quantity.id.ifBlank { unitId },
    "supplier_id" to batch.supplierId.orEmpty(),
    "supplier_name" to supplierName.orEmpty(),
    "supply_price" to supplyText.orEmpty(),
    "sale_price" to saleText.orEmpty(),
    "return_price" to returnText.orEmpty(),
    "wholesale_price" to wholesaleText.orEmpty(),
    "status" to statusText
  ).filterValues { it.isNotBlank() }

  return StockBatchOperationLogText(
    title = title,
    details = details,
    metadata = metadata
  )
}

private fun ResultRow.stockItemOperationLogName(fallback: String = ""): String {
  val idFallback = fallback.ifBlank { this[StockItems.id].toString() }
  return this[StockItems.name].operationLogVisibleText(idFallback).ifBlank { idFallback }
}

private fun ResultRow.stockItemOperationLogBarcodeText(): String {
  return stockBarcodeValues().joinToString(",")
}

private fun stockItemChangeDetails(changedFields: List<String>): List<LocalizedStringDataModel> {
  val value = changedFields.joinToString(", ").ifBlank { "Stock item data" }
  return simpleMessage(
    main = "Changed: $value",
    en = "Changed: $value",
    ru = "Изменено: $value",
    kk = "Өзгерді: $value"
  )
}

private fun stockItemChangedFieldsInsideTransaction(
  previousRow: ResultRow,
  cleanBarcodes: List<String>,
  cleanBarcodeModels: List<GoodsItemBarcodeDataModel>,
  requestedActiveShelfBatchId: String?,
  requestedWholesaleMinQuantity: QuantityDataModel?,
  requestedGenericExpirationPeriod: ExpirationPeriodDataModel?,
  requestedConditions: List<String>,
  sanitizedPromotions: List<StockPromotionDataModel>,
  body: GoodsItemDataModel
): List<String> {
  val existingActiveShelfBatchId = previousRow[StockItems.activeShelfBatchId]?.toString()
  return buildList {
    if (cleanBarcodes.toSet() != previousRow.stockBarcodeValues().toSet()) add("barcodes")
    if (cleanBarcodeModels != previousRow.stockBarcodeModels()) add("barcode types")
    if (body.name != previousRow[StockItems.name]) add("name")
    if (body.description != previousRow[StockItems.description]) add("description")
    if (body.measurementUnitId != previousRow[StockItems.measurementUnitId]) add("unit")
    if (body.categoryIds != previousRow[StockItems.categoryIds]) add("categories")
    if (body.salePrices != previousRow[StockItems.salePrices]) add("sale prices")
    if (body.returnPrices != previousRow[StockItems.returnPrices]) add("return prices")
    if (body.supplyPrices != previousRow[StockItems.supplyPrices]) add("supply prices")
    if (body.wholesalePrices != previousRow[StockItems.wholesalePrices]) add("wholesale prices")
    if (requestedWholesaleMinQuantity != previousRow[StockItems.wholesaleMinQuantity]) add("wholesale minimum quantity")
    if (requestedGenericExpirationPeriod != previousRow[StockItems.genericExpirationPeriod]) add("expiration period")
    if (body.isQuickItem != previousRow[StockItems.isQuickItem]) add("quick item flag")
    if (body.imagePaths != previousRow[StockItems.imagePaths]) add("images")
    if (requestedActiveShelfBatchId != existingActiveShelfBatchId) add("active shelf batch")
    if (sanitizedPromotions != previousRow[StockItems.promotions]) add("promotions")
    if (body.note != previousRow[StockItems.note]) add("note")
    if (body.noteLocalized != previousRow[StockItems.noteLocalized]) add("localized note")
    if (requestedConditions != previousRow[StockItems.conditions].map { it.trim() }.filter { it.isNotBlank() }.distinct()) add("conditions")
    if (body.isActive != previousRow[StockItems.isActive]) add("activity")
  }
}

private fun batchChangedFieldsInsideTransaction(
  previousRow: ResultRow,
  goodsItemId: UUID,
  nextSupplierId: UUID?,
  nextSupplierOrderId: UUID?,
  sanitizedPromotions: List<StockPromotionDataModel>,
  body: GoodsBatchDataModel
): List<String> = buildList {
  if (previousRow[StockBatchesV2.goodsItemId] != goodsItemId) add("item")
  if (previousRow[StockBatchesV2.supplierId] != nextSupplierId) add("supplier")
  if (previousRow[StockBatchesV2.supplierOrderId] != nextSupplierOrderId) add("supplier order")
  if (previousRow[StockBatchesV2.quantity] != body.quantity) add("quantity")
  if (previousRow[StockBatchesV2.supplyPrice] != body.supplyPrice) add("supply price")
  if (previousRow[StockBatchesV2.salePriceOverride] != body.salePriceOverride) add("sale price override")
  if (previousRow[StockBatchesV2.returnPriceOverride] != body.returnPriceOverride) add("return price override")
  if (previousRow[StockBatchesV2.wholesalePriceOverride] != body.wholesalePriceOverride) add("wholesale price override")
  if (previousRow[StockBatchesV2.deliveredAtMillis] != body.deliveredAtMillis) add("delivery date")
  if (previousRow[StockBatchesV2.manufacturedAtMillis] != body.manufacturedAtMillis) add("manufacture date")
  if (previousRow[StockBatchesV2.expirationDateMillis] != body.expirationDateMillis) add("expiration date")
  if (previousRow[StockBatchesV2.discounts] != body.discounts) add("discounts")
  if (previousRow[StockBatchesV2.promotions] != sanitizedPromotions) add("promotions")
  if (previousRow[StockBatchesV2.shelfPosition] != body.shelfPosition) add("shelf position")
  if (previousRow[StockBatchesV2.shelfPriority] != body.shelfPriority) add("shelf priority")
  if (previousRow[StockBatchesV2.status] != body.status.name) add("status")
  if (previousRow[StockBatchesV2.additionalNotes] != body.additionalNotes) add("notes")
  if (previousRow[StockBatchesV2.additionalNotesLocalized] != body.additionalNotesLocalized) add("localized notes")
  if (previousRow[StockBatchesV2.isActive] != body.isActive) add("activity")
}

private fun OperationLogDataModel.matchesStockItemHistory(goodsItemId: String, batchIds: Set<String>): Boolean {
  if (entityType == OPERATION_LOG_ENTITY_STOCK_ITEM && entityId == goodsItemId) return true
  if (entityType == OPERATION_LOG_ENTITY_STOCK_BATCH && entityId != null && entityId in batchIds) return true
  val itemKeys = listOf("goods_item_id", "stock_item_id", "item_id", "source_goods_item_id", "destination_goods_item_id")
  val batchKeys = listOf("batch_id", "source_batch_id", "destination_batch_id")
  if (itemKeys.any { metadata[it] == goodsItemId }) return true
  if (batchKeys.any { key -> metadata[key]?.let { it in batchIds } == true }) return true
  return false
}

private fun insertOperationLogInsideTransaction(
  actorUserId: UUID,
  storeId: UUID,
  action: String,
  entityType: String,
  entityId: String? = null,
  title: List<LocalizedStringDataModel>,
  details: List<LocalizedStringDataModel> = emptyList(),
  metadata: Map<String, String> = emptyMap(),
  now: Long = System.currentTimeMillis()
) {
  runCatching {
    val rootStoreId = rootStoreIdForAccessInsideTransaction(storeId)
    val storeRow = Stores
      .select(Stores.publicId, Stores.name)
      .where { Stores.id eq storeId }
      .singleOrNull()
    val actorRow = Users
      .select(Users.publicId, Users.firstName, Users.lastName, Users.phoneNumber, Users.email)
      .where { Users.id eq actorUserId }
      .singleOrNull()
    val actorDisplayName = actorRow?.let { row ->
      "${row[Users.firstName]} ${row[Users.lastName]}".trim().ifBlank { row[Users.phoneNumber].ifBlank { row[Users.email] } }
    }.orEmpty()

    val duplicateCutoff = now - 2_500L
    val sameEntityCondition = entityId
      ?.takeIf { it.isNotBlank() }
      ?.let { OperationLogs.entityId eq it }
      ?: OperationLogs.entityId.isNull()

    val duplicateAlreadyExists = OperationLogs
      .select(OperationLogs.id)
      .where {
        (OperationLogs.rootStoreId eq rootStoreId) and
           (OperationLogs.storeId eq storeId) and
           (OperationLogs.actorUserId eq actorUserId) and
           (OperationLogs.action eq action) and
           (OperationLogs.entityType eq entityType) and
           sameEntityCondition and
           (OperationLogs.createdAtMillis greaterEq duplicateCutoff)
      }
      .limit(1)
      .empty()
      .not()

    if (duplicateAlreadyExists)
      return@runCatching

    OperationLogs.insert {
      it[OperationLogs.id] = UUID.randomUUID()
      it[OperationLogs.rootStoreId] = rootStoreId
      it[OperationLogs.storeId] = storeId
      it[OperationLogs.storePublicId] = storeRow?.get(Stores.publicId).orEmpty()
      it[OperationLogs.storeName] = storeRow?.get(Stores.name).orEmpty()
      it[OperationLogs.actorUserId] = actorUserId
      it[OperationLogs.actorPublicId] = actorRow?.get(Users.publicId).orEmpty()
      it[OperationLogs.actorDisplayName] = actorDisplayName
      it[OperationLogs.workshiftId] = activeWorkshiftIdInsideTransaction(actorUserId, storeId)
      it[OperationLogs.action] = action
      it[OperationLogs.entityType] = entityType
      it[OperationLogs.entityId] = entityId
      it[OperationLogs.title] = title
      it[OperationLogs.details] = details
      it[OperationLogs.metadata] = metadata
      it[OperationLogs.createdAtMillis] = now
    }
  }
}

private fun storeLegalIdFormatForCountry(countryLocale: String?): LegalIdFormatDataModel {
  val normalized = countryLocale?.trim()?.lowercase().orEmpty()
  return defaultLegalIdFormats().firstOrNull { format ->
    format.countryLocales.any { it.equals(normalized, ignoreCase = true) }
  } ?: defaultLegalIdFormats().first()
}

private fun validateStoreLegalId(body: StoreDataModel): Boolean {
  if (body.parentStoreId != null) return true
  val countryLocale = body.countryLocales.firstOrNull()
  val format = storeLegalIdFormatForCountry(countryLocale)
  return body.legalId.matchesLegalIdFormat(format)
}

private fun validateStoreAddress(body: StoreDataModel): Boolean = body.address.trim().isNotEmpty()

private fun nextPublicId(prefix: String): String {
  return prefix + UUID.randomUUID().toString().replace("-", "").take(8).uppercase()
}

private fun generateUniqueUserPublicIdInsideTransaction(): String {
  var candidate: String
  do {
    candidate = nextPublicId("U")
  } while (!Users.select(Users.id).where { Users.publicId eq candidate }.empty())
  return candidate
}

private fun generateUniqueStorePublicIdInsideTransaction(): String {
  var candidate: String
  do {
    candidate = nextPublicId("S")
  } while (!Stores.select(Stores.id).where { Stores.publicId eq candidate }.empty())
  return candidate
}


private fun generateUniqueSupportTicketPublicIdInsideTransaction(): String {
  var candidate: String
  do {
    candidate = nextPublicId("Q")
  } while (!SupportTickets.select(SupportTickets.id).where { SupportTickets.publicId eq candidate }.empty())
  return candidate
}

private fun sanitizeSupportSubject(subject: String, message: String): String {
  val source = subject.trim().ifBlank { message.trim().lineSequence().firstOrNull().orEmpty() }
  return source.take(96).ifBlank { "Support request" }
}

private fun sanitizeSupportCategory(category: String): String = when (category.trim().lowercase()) {
  "billing", "payments", "subscriptions" -> "billing"
  "technical", "devices", "printing", "sync" -> "technical"
  "stock", "sales", "transactions", "suppliers" -> "operations"
  "security", "account", "workers" -> "account"
  else -> "general"
}

private fun sanitizeSupportPriority(priority: String): String = when (priority.trim().lowercase()) {
  "urgent", "high" -> "high"
  "low" -> "low"
  else -> "normal"
}

private fun supportTicketForUserInsideTransaction(userId: UUID, ticketId: UUID): ResultRow? = SupportTickets
  .selectAll()
  .where { (SupportTickets.id eq ticketId) and (SupportTickets.userId eq userId) and (SupportTickets.isActive eq true) }
  .singleOrNull()

private fun userDisplayNameInsideTransaction(userId: UUID): String = Users
  .select(Users.firstName, Users.lastName)
  .where { Users.id eq userId }
  .singleOrNull()
  ?.let { row -> listOf(row[Users.firstName], row[Users.lastName]).filter { it.isNotBlank() }.joinToString(" ") }
  .orEmpty()

private fun storeDisplayNameInsideTransaction(storeId: UUID): String = Stores
  .select(Stores.publicId, Stores.name)
  .where { Stores.id eq storeId }
  .singleOrNull()
  ?.let { row -> row[Stores.name].extractLocalizedString("main").orEmpty().ifBlank { row[Stores.publicId] } }
  .orEmpty()

private fun userDisplayNameOrPublicIdInsideTransaction(userId: UUID): String {
  val displayName = userDisplayNameInsideTransaction(userId)
  if (displayName.isNotBlank()) return displayName

  return Users
    .select(Users.publicId)
    .where { Users.id eq userId }
    .singleOrNull()
    ?.get(Users.publicId)
    .orEmpty()
}

private fun storeWorkerNotificationRecipientUserIdsInsideTransaction(
  storeId: UUID,
  managersOnly: Boolean = true
): List<UUID> {
  val rootStoreId = rootStoreIdForAccessInsideTransaction(storeId)
  val storeIds = storeGroupIdsInsideTransaction(rootStoreId)

  val ownerIds = Stores
    .select(Stores.ownerUserIds)
    .where { Stores.id inList storeIds }
    .flatMap { row ->
      row[Stores.ownerUserIds].mapNotNull { raw ->
        runCatching { UUID.fromString(raw) }.getOrNull()
      }
    }

  val workerIds = StoreWorkerMemberships
    .select(StoreWorkerMemberships.userId, StoreWorkerMemberships.permissions)
    .where { (StoreWorkerMemberships.storeId inList storeIds) and (StoreWorkerMemberships.isActive eq true) }
    .filter { row -> !managersOnly || STORE_PERMISSION_WORKERS_VIEW in normalizeStorePermissionIds(row[StoreWorkerMemberships.permissions]) }
    .map { it[StoreWorkerMemberships.userId] }

  return (ownerIds + workerIds).distinct()
}

private fun notificationOperationIdFromId(id: String?): String? {
  val cleanId = id?.trim().orEmpty()
  return when {
    cleanId.startsWith("loading_") -> cleanId.removePrefix("loading_")
    cleanId.startsWith("operation_") -> cleanId.removePrefix("operation_")
    else -> null
  }?.takeIf { it.isNotBlank() }
}

private fun notificationOperationIdFromMetadata(metadata: Map<String, String>): String? {
  val direct = listOf(
    "operationId",
    "operation_id",
    "temporaryOperationId",
    "temporary_operation_id",
    "clientOperationId",
    "client_operation_id",
    "resultOfOperationId",
    "result_of_operation_id",
    "replacesOperationId",
    "replaces_operation_id"
  ).firstNotNullOfOrNull { key -> metadata[key]?.trim()?.takeIf { it.isNotBlank() } }

  if (!direct.isNullOrBlank()) return direct

  return listOf(
    "notificationId",
    "notification_id",
    "loadingNotificationId",
    "loading_notification_id",
    "previousNotificationId",
    "previous_notification_id",
    "replacesNotificationId",
    "replaces_notification_id"
  ).firstNotNullOfOrNull { key -> notificationOperationIdFromId(metadata[key]) }
}

private fun notificationOperationStatusFromMetadata(metadata: Map<String, String>): String = listOf(
  "operationStatus",
  "operation_status",
  "operationStage",
  "operation_stage",
  "stage",
  "status",
  "state"
).firstNotNullOfOrNull { key -> metadata[key]?.trim()?.lowercase()?.takeIf { it.isNotBlank() } }.orEmpty()

private fun isLoadingNotificationIntent(
  type: NotificationType,
  category: String,
  metadata: Map<String, String>,
  title: String,
  message: String
): Boolean {
  val status = notificationOperationStatusFromMetadata(metadata)
  if (status in setOf("loading", "pending", "progress", "in_progress", "working", "started")) return true
  if (metadata["loading"] == "true" || metadata["isLoading"] == "true" || metadata["inProgress"] == "true" || metadata["in_progress"] == "true") return true

  val text = listOf(category, title, message).joinToString(" ").lowercase()
  return type == NotificationType.Neutral && (
     "loading" in text ||
        "in progress" in text ||
        "working" in text ||
        "загрузка" in text ||
        "выполня" in text ||
        "орындал" in text
     )
}

private fun isResultNotificationIntent(
  type: NotificationType,
  metadata: Map<String, String>
): Boolean {
  val status = notificationOperationStatusFromMetadata(metadata)
  return status in setOf("result", "done", "success", "succeeded", "failed", "failure", "error", "cancelled", "canceled", "completed") ||
     metadata["resultOfOperationId"]?.isNotBlank() == true ||
     metadata["result_of_operation_id"]?.isNotBlank() == true ||
     metadata["replacesOperationId"]?.isNotBlank() == true ||
     metadata["replaces_operation_id"]?.isNotBlank() == true ||
     type != NotificationType.Neutral
}

private fun notificationRowMatchesOperation(row: ResultRow, operationId: String): Boolean {
  val metadata = row[Notifications.metadata]
  return notificationOperationIdFromMetadata(metadata) == operationId ||
     row[Notifications.id] == "loading_$operationId" ||
     row[Notifications.id] == "operation_$operationId" ||
     row[Notifications.id].endsWith("_$operationId")
}

private fun notificationRowIsLoading(row: ResultRow): Boolean {
  val type = runCatching { NotificationType.valueOf(row[Notifications.type]) }.getOrDefault(NotificationType.Neutral)
  return isLoadingNotificationIntent(
    type = type,
    category = row[Notifications.category],
    metadata = row[Notifications.metadata],
    title = row[Notifications.title],
    message = row[Notifications.message]
  )
}

private fun existingNotificationByOperationInsideTransaction(
  userId: UUID,
  operationId: String,
  loadingOnly: Boolean = false
): ResultRow? {
  if (operationId.isBlank()) return null

  return Notifications
    .selectAll()
    .where { (Notifications.userId eq userId) and (Notifications.isActive eq true) }
    .orderBy(Notifications.createdAtMillis, SortOrder.DESC)
    .limit(500)
    .firstOrNull { row ->
      notificationRowMatchesOperation(row, operationId) && (!loadingOnly || notificationRowIsLoading(row))
    }
}

private fun existingNotificationForIncomingOperationInsideTransaction(
  userId: UUID,
  operationId: String?,
  incomingIsLoading: Boolean,
  incomingIsResult: Boolean
): ResultRow? {
  val cleanOperationId = operationId?.takeIf { it.isNotBlank() } ?: return null
  return when {
    incomingIsResult -> existingNotificationByOperationInsideTransaction(userId, cleanOperationId, loadingOnly = true)
      ?: existingNotificationByOperationInsideTransaction(userId, cleanOperationId, loadingOnly = false)
    incomingIsLoading -> existingNotificationByOperationInsideTransaction(userId, cleanOperationId, loadingOnly = true)
    else -> existingNotificationByOperationInsideTransaction(userId, cleanOperationId, loadingOnly = false)
  }
}

private fun notificationReplacementIdFromMetadata(metadata: Map<String, String>): String? = listOf(
  metadata["replacesNotificationId"],
  metadata["replaces_notification_id"],
  metadata["loadingNotificationId"],
  metadata["loading_notification_id"],
  metadata["previousNotificationId"],
  metadata["previous_notification_id"]
).firstOrNull { !it.isNullOrBlank() }?.trim()

private fun notificationMetadataForStorage(
  metadata: Map<String, String>,
  operationId: String?,
  incomingIsLoading: Boolean,
  incomingIsResult: Boolean
): Map<String, String> {
  val status = notificationOperationStatusFromMetadata(metadata)
  return metadata
    .let { data -> operationId?.let { data + ("operationId" to it) } ?: data }
    .let { data ->
      if (status.isNotBlank()) data else data + ("operationStatus" to when {
        incomingIsLoading -> "loading"
        incomingIsResult -> "result"
        else -> "info"
      })
    }
}

private fun insertServerNotificationInsideTransaction(
  userId: UUID,
  storeId: UUID?,
  title: String,
  message: String,
  type: NotificationType = NotificationType.Neutral,
  category: String = "general",
  source: String = "server",
  metadata: Map<String, String> = emptyMap(),
  nowMillis: Long = System.currentTimeMillis()
): NotificationDataModel {
  cleanupNotificationsInsideTransaction(nowMillis)

  val operationId = notificationOperationIdFromMetadata(metadata)
  val incomingIsLoading = isLoadingNotificationIntent(type, category, metadata, title, message)
  val incomingIsResult = isResultNotificationIntent(type, metadata)
  val metadataForStorage = notificationMetadataForStorage(metadata, operationId, incomingIsLoading, incomingIsResult)
  val requestedNotificationId = metadataForStorage["notificationId"]
    ?.trim()
    ?.takeIf { it.isNotBlank() }
    ?: operationId?.let { if (incomingIsLoading) "loading_${userId}_$it" else "operation_${userId}_$it" }
    ?: "server_${nowMillis}_${UUID.randomUUID()}"

  val replacementNotificationId = notificationReplacementIdFromMetadata(metadataForStorage)
  val existingByReplacementId = replacementNotificationId?.let { replacementId ->
    Notifications
      .selectAll()
      .where { (Notifications.id eq replacementId) and (Notifications.userId eq userId) and (Notifications.isActive eq true) }
      .firstOrNull()
  }
  val existingByOperation = existingNotificationForIncomingOperationInsideTransaction(userId, operationId, incomingIsLoading, incomingIsResult)
  val existingById = Notifications
    .selectAll()
    .where { (Notifications.id eq requestedNotificationId) and (Notifications.userId eq userId) }
    .firstOrNull()
  val existing = existingByReplacementId ?: existingByOperation ?: existingById
  val finalNotificationId = existing?.get(Notifications.id) ?: requestedNotificationId

  if (existing != null) {
    Notifications.update({ (Notifications.id eq finalNotificationId) and (Notifications.userId eq userId) }) {
      it[Notifications.title] = title
      it[Notifications.message] = message
      it[Notifications.type] = type.name
      it[Notifications.category] = category.ifBlank { type.name.lowercase() }
      it[Notifications.notificationSource] = source.ifBlank { "server" }
      it[Notifications.metadata] = metadataForStorage
      it[Notifications.storeId] = storeId
      it[Notifications.createdAtMillis] = nowMillis
      it[Notifications.shownAtMillis] = nowMillis
      it[Notifications.readAtMillis] = null
      it[Notifications.isActive] = true
    }
  } else {
    Notifications.insert {
      it[Notifications.id] = finalNotificationId
      it[Notifications.userId] = userId
      it[Notifications.storeId] = storeId
      it[Notifications.title] = title
      it[Notifications.message] = message
      it[Notifications.type] = type.name
      it[Notifications.category] = category.ifBlank { type.name.lowercase() }
      it[Notifications.notificationSource] = source.ifBlank { "server" }
      it[Notifications.metadata] = metadataForStorage
      it[Notifications.createdAtMillis] = nowMillis
      it[Notifications.shownAtMillis] = nowMillis
      it[Notifications.readAtMillis] = null
      it[Notifications.isActive] = true
    }
  }

  return Notifications
    .selectAll()
    .where { (Notifications.id eq finalNotificationId) and (Notifications.userId eq userId) }
    .single()
    .toNotificationDataModel()
}

private fun notifyEmploymentRequestCreatedInsideTransaction(
  requesterUserId: UUID,
  storeId: UUID,
  requestId: UUID,
  nowMillis: Long
) {
  val requesterName = userDisplayNameOrPublicIdInsideTransaction(requesterUserId).ifBlank { "A user" }
  val storeName = storeDisplayNameInsideTransaction(storeId).ifBlank { "this store" }
  val operationId = "worker_request_$requestId"

  storeWorkerNotificationRecipientUserIdsInsideTransaction(storeId, managersOnly = true)
    .filter { it != requesterUserId }
    .forEach { recipientId ->
      insertServerNotificationInsideTransaction(
        userId = recipientId,
        storeId = storeId,
        title = "Employment request",
        message = "$requesterName requested to work in $storeName.",
        type = NotificationType.Neutral,
        category = "workers",
        metadata = mapOf("operationId" to operationId, "requestId" to requestId.toString(), "direction" to WORKER_REQUEST_DIRECTION_USER_TO_STORE),
        nowMillis = nowMillis
      )
    }

  insertServerNotificationInsideTransaction(
    userId = requesterUserId,
    storeId = storeId,
    title = "Employment request sent",
    message = "Your request to work in $storeName is waiting for review.",
    type = NotificationType.Positive,
    category = "workers",
    metadata = mapOf("operationId" to "worker_request_sent_$requestId", "requestId" to requestId.toString(), "direction" to WORKER_REQUEST_DIRECTION_USER_TO_STORE),
    nowMillis = nowMillis
  )
}

private fun notifyEmploymentInviteCreatedInsideTransaction(
  invitedUserId: UUID,
  inviterUserId: UUID,
  storeId: UUID,
  requestId: UUID,
  nowMillis: Long
) {
  val inviterName = userDisplayNameOrPublicIdInsideTransaction(inviterUserId).ifBlank { "A store manager" }
  val storeName = storeDisplayNameInsideTransaction(storeId).ifBlank { "this store" }

  insertServerNotificationInsideTransaction(
    userId = invitedUserId,
    storeId = storeId,
    title = "Store invitation",
    message = "$inviterName invited you to work in $storeName.",
    type = NotificationType.Neutral,
    category = "workers",
    metadata = mapOf("operationId" to "worker_invite_$requestId", "requestId" to requestId.toString(), "direction" to WORKER_REQUEST_DIRECTION_STORE_TO_USER),
    nowMillis = nowMillis
  )
}

private fun notifyEmploymentDecisionInsideTransaction(
  requestId: UUID,
  storeId: UUID,
  workerUserId: UUID,
  actorUserId: UUID,
  accepted: Boolean,
  direction: String,
  nowMillis: Long
) {
  val workerName = userDisplayNameOrPublicIdInsideTransaction(workerUserId).ifBlank { "The worker" }
  val storeName = storeDisplayNameInsideTransaction(storeId).ifBlank { "this store" }
  val actionText = if (accepted) "accepted" else "declined"
  val notificationType = if (accepted) NotificationType.Positive else NotificationType.Negative

  insertServerNotificationInsideTransaction(
    userId = workerUserId,
    storeId = storeId,
    title = if (accepted) "Employment accepted" else "Employment declined",
    message = if (direction == WORKER_REQUEST_DIRECTION_STORE_TO_USER) {
      "Your invitation for $storeName was $actionText."
    } else {
      "Your request to work in $storeName was $actionText."
    },
    type = notificationType,
    category = "workers",
    metadata = mapOf("operationId" to "worker_decision_${requestId}_$workerUserId", "requestId" to requestId.toString(), "status" to actionText, "direction" to direction),
    nowMillis = nowMillis
  )

  storeWorkerNotificationRecipientUserIdsInsideTransaction(storeId, managersOnly = true)
    .filter { it != actorUserId }
    .forEach { recipientId ->
      insertServerNotificationInsideTransaction(
        userId = recipientId,
        storeId = storeId,
        title = if (accepted) "Worker accepted" else "Worker declined",
        message = "$workerName $actionText employment in $storeName.",
        type = notificationType,
        category = "workers",
        metadata = mapOf("operationId" to "worker_manager_decision_${requestId}_$recipientId", "requestId" to requestId.toString(), "status" to actionText, "direction" to direction),
        nowMillis = nowMillis
      )
    }
}

private fun notifyWorkerPermissionsUpdatedInsideTransaction(
  storeId: UUID,
  workerUserId: UUID,
  workerId: UUID,
  nowMillis: Long
) {
  val storeName = storeDisplayNameInsideTransaction(storeId).ifBlank { "this store" }
  insertServerNotificationInsideTransaction(
    userId = workerUserId,
    storeId = storeId,
    title = "Worker permissions updated",
    message = "Your permissions in $storeName were updated.",
    type = NotificationType.Neutral,
    category = "workers",
    metadata = mapOf("operationId" to "worker_permissions_$workerId", "workerId" to workerId.toString()),
    nowMillis = nowMillis
  )
}

private fun notifyWorkerRemovedInsideTransaction(
  storeId: UUID,
  workerUserId: UUID,
  workerId: UUID,
  nowMillis: Long
) {
  val storeName = storeDisplayNameInsideTransaction(storeId).ifBlank { "this store" }
  insertServerNotificationInsideTransaction(
    userId = workerUserId,
    storeId = storeId,
    title = "Worker removed",
    message = "Your worker access to $storeName was removed.",
    type = NotificationType.Negative,
    category = "workers",
    metadata = mapOf("operationId" to "worker_removed_$workerId", "workerId" to workerId.toString()),
    nowMillis = nowMillis
  )
}

private fun notifyWorkerRemovalRequestCreatedInsideTransaction(
  workerUserId: UUID,
  requesterUserId: UUID,
  storeId: UUID,
  requestId: UUID,
  workerId: UUID,
  nowMillis: Long
) {
  val requesterName = userDisplayNameOrPublicIdInsideTransaction(requesterUserId).ifBlank { "A store manager" }
  val workerName = userDisplayNameOrPublicIdInsideTransaction(workerUserId).ifBlank { "The worker" }
  val storeName = storeDisplayNameInsideTransaction(storeId).ifBlank { "this store" }

  insertServerNotificationInsideTransaction(
    userId = workerUserId,
    storeId = storeId,
    title = "Removal request",
    message = "$requesterName asks to end your worker access to $storeName. Please confirm or decline.",
    type = NotificationType.Neutral,
    category = "workers",
    metadata = mapOf(
      "operationId" to "worker_removal_request_$requestId",
      "requestId" to requestId.toString(),
      "workerId" to workerId.toString(),
      "direction" to WORKER_REQUEST_DIRECTION_STORE_REMOVAL_TO_USER
    ),
    nowMillis = nowMillis
  )

  storeWorkerNotificationRecipientUserIdsInsideTransaction(storeId, managersOnly = true)
    .filter { it != workerUserId && it != requesterUserId }
    .forEach { recipientId ->
      insertServerNotificationInsideTransaction(
        userId = recipientId,
        storeId = storeId,
        title = "Removal request sent",
        message = "$workerName will decide whether to end worker access to $storeName.",
        type = NotificationType.Neutral,
        category = "workers",
        metadata = mapOf(
          "operationId" to "worker_removal_request_sent_${requestId}_$recipientId",
          "requestId" to requestId.toString(),
          "workerId" to workerId.toString(),
          "direction" to WORKER_REQUEST_DIRECTION_STORE_REMOVAL_TO_USER
        ),
        nowMillis = nowMillis
      )
    }
}

private fun notifyWorkerRemovalDecisionInsideTransaction(
  requestId: UUID,
  storeId: UUID,
  workerUserId: UUID,
  workerId: UUID,
  actorUserId: UUID,
  accepted: Boolean,
  nowMillis: Long
) {
  val workerName = userDisplayNameOrPublicIdInsideTransaction(workerUserId).ifBlank { "The worker" }
  val storeName = storeDisplayNameInsideTransaction(storeId).ifBlank { "this store" }

  insertServerNotificationInsideTransaction(
    userId = workerUserId,
    storeId = storeId,
    title = if (accepted) "Removal confirmed" else "Removal declined",
    message = if (accepted) {
      "Your worker access to $storeName ended after your confirmation."
    } else {
      "You declined the request to end your worker access to $storeName."
    },
    type = if (accepted) NotificationType.Positive else NotificationType.Neutral,
    category = "workers",
    metadata = mapOf(
      "operationId" to "worker_removal_decision_${requestId}_$workerUserId",
      "requestId" to requestId.toString(),
      "workerId" to workerId.toString(),
      "status" to if (accepted) WORKER_REQUEST_STATUS_ACCEPTED else WORKER_REQUEST_STATUS_DECLINED,
      "direction" to WORKER_REQUEST_DIRECTION_STORE_REMOVAL_TO_USER
    ),
    nowMillis = nowMillis
  )

  storeWorkerNotificationRecipientUserIdsInsideTransaction(storeId, managersOnly = true)
    .filter { it != actorUserId }
    .forEach { recipientId ->
      insertServerNotificationInsideTransaction(
        userId = recipientId,
        storeId = storeId,
        title = if (accepted) "Worker removal confirmed" else "Worker kept access",
        message = if (accepted) {
          "$workerName confirmed removal and no longer has worker access to $storeName."
        } else {
          "$workerName declined removal and keeps worker access to $storeName."
        },
        type = if (accepted) NotificationType.Positive else NotificationType.Neutral,
        category = "workers",
        metadata = mapOf(
          "operationId" to "worker_removal_manager_decision_${requestId}_$recipientId",
          "requestId" to requestId.toString(),
          "workerId" to workerId.toString(),
          "status" to if (accepted) WORKER_REQUEST_STATUS_ACCEPTED else WORKER_REQUEST_STATUS_DECLINED,
          "direction" to WORKER_REQUEST_DIRECTION_STORE_REMOVAL_TO_USER
        ),
        nowMillis = nowMillis
      )
    }
}

private fun resolveUserIdByPublicOrPrivateIdInsideTransaction(value: String): UUID? {
  val clean = value.trim()
  if (clean.isBlank()) return null
  runCatching { UUID.fromString(clean) }.getOrNull()?.let { return it }
  val normalized = clean.uppercase()
  return Users
    .select(Users.id)
    .where { Users.publicId eq normalized }
    .singleOrNull()
    ?.get(Users.id)
}

private fun resolveStoreIdByPublicOrPrivateIdInsideTransaction(value: String): UUID? {
  val clean = value.trim()
  if (clean.isBlank()) return null
  runCatching { UUID.fromString(clean) }.getOrNull()?.let { return it }
  val normalized = clean.uppercase()
  return Stores
    .select(Stores.id)
    .where { Stores.publicId eq normalized }
    .singleOrNull()
    ?.get(Stores.id)
}

private fun ResultRow.toStoreDataModel(branches: List<StoreDataModel> = emptyList()): StoreDataModel {
  return StoreDataModel(
    id = this[Stores.id].toString(),
    publicId = this[Stores.publicId],
    parentStoreId = this[Stores.parentStoreId]?.toString(),
    userIds = this[Stores.ownerUserIds],
    storeTypeIds = this[Stores.storeTypeIds],
    name = this[Stores.name],
    alias = this[Stores.alias],
    description = this[Stores.description],
    companyForms = this[Stores.companyForms],
    location = this[Stores.location],
    address = this[Stores.address],
    legalIdTypeId = this[Stores.legalIdTypeId],
    legalId = this[Stores.legalId],
    phoneNumbers = this[Stores.phoneNumbers],
    emails = this[Stores.emails],
    countryLocales = this[Stores.countryLocales],
    createdAt = this[Stores.createdAt].toEpochMilli(),
    branches = branches
  )
}

private fun List<ResultRow>.toHierarchicalStoreDataModels(): List<StoreDataModel> {
  val branchRowsByParent = filter { it[Stores.parentStoreId] != null }
    .groupBy { it[Stores.parentStoreId]!! }

  val branchModels = filter { it[Stores.parentStoreId] != null }
    .map { it.toStoreDataModel() }

  val parentModels = filter { it[Stores.parentStoreId] == null }
    .map { row ->
      row.toStoreDataModel(
        branches = branchRowsByParent[row[Stores.id]].orEmpty().map { it.toStoreDataModel() }
      )
    }

  return parentModels + branchModels
}

private fun ResultRow.toStoreWorkerDataModel(): StoreWorkerDataModel {
  val userName = this[Stores.name]
  return StoreWorkerDataModel(
    id = this[StoreWorkerMemberships.id].toString(),
    storeId = this[StoreWorkerMemberships.storeId].toString(),
    storePublicId = this[Stores.publicId],
    storeName = userName,
    userId = this[StoreWorkerMemberships.userId].toString(),
    userPublicId = this[Users.publicId],
    phoneNumber = this[Users.phoneNumber],
    email = this[Users.email],
    firstName = this[Users.firstName],
    lastName = this[Users.lastName],
    roleId = this[StoreWorkerMemberships.roleId],
    permissions = normalizeStorePermissionIds(this[StoreWorkerMemberships.permissions]),
    jobTitle = this[StoreWorkerMemberships.jobTitle],
    jobTitleLocalized = this[StoreWorkerMemberships.jobTitleLocalized],
    salary = this[StoreWorkerMemberships.salary],
    salaryCurrencyCode = this[StoreWorkerMemberships.salaryCurrencyCode],
    requestedAtMillis = this[StoreWorkerMemberships.requestedAtMillis],
    acceptedAtMillis = this[StoreWorkerMemberships.acceptedAtMillis],
    acceptedByUserId = this[StoreWorkerMemberships.acceptedByUserId].toString(),
    isActive = this[StoreWorkerMemberships.isActive],
    hasWorkshiftPassword = !this[StoreWorkerMemberships.workshiftPasswordHash].isNullOrBlank()
  )
}

private fun String?.toWorkshiftPasswordHashOrNull(): String? =
  this?.takeIf { it.isNotBlank() }?.let { Pw.hash(it.toCharArray()) }

private fun passwordRequirementMessage(): List<LocalizedStringDataModel> = simpleMessage(
  main = "Password must be 8 or more symbols long and contain at least one digit and one special symbol",
  ru = "Пароль должен быть длиной 8 или более символов и содержать хотя бы одну цифру и один специальный символ",
  kk = "Құпия сөз ұзындығы 8 немесе одан да көп таңбадан тұруы және кемінде бір сан мен бір арнайы таңбадан тұруы керек"
)

private fun accountPasswordRequiredMessage(): List<LocalizedStringDataModel> = simpleMessage(
  main = "Account password is required to change the workshift password",
  ru = "Для изменения пароля смены нужен пароль аккаунта",
  kk = "Ауысым құпия сөзін өзгерту үшін аккаунт құпия сөзі қажет"
)

private fun accountPasswordIncorrectMessage(): List<LocalizedStringDataModel> = simpleMessage(
  main = "Account password is incorrect",
  ru = "Пароль аккаунта неверный",
  kk = "Аккаунт құпия сөзі дұрыс емес"
)

private fun ResultRow.toWorkshiftDataModel(): WorkshiftDataModel {
  val displayName = "${this[Users.firstName]} ${this[Users.lastName]}".trim()
    .ifBlank { this[Users.phoneNumber] }
    .ifBlank { this[Users.email] }
    .ifBlank { this[Workshifts.workerUserId].toString() }

  return WorkshiftDataModel(
    id = this[Workshifts.id].toString(),
    storeId = this[Workshifts.storeId].toString(),
    storePublicId = this[Stores.publicId],
    storeName = this[Stores.name],
    workerMembershipId = this[Workshifts.workerMembershipId].toString(),
    workerUserId = this[Workshifts.workerUserId].toString(),
    workerPublicId = this[Users.publicId].orEmpty(),
    workerDisplayName = displayName,
    startedAtMillis = this[Workshifts.startedAtMillis],
    endedAtMillis = this[Workshifts.endedAtMillis],
    startedByUserId = this[Workshifts.startedByUserId].toString(),
    endedByUserId = this[Workshifts.endedByUserId]?.toString(),
    isActive = this[Workshifts.isActive]
  )
}

private fun endWorkshiftForUserInsideTransaction(
  workerUserId: UUID,
  storeId: UUID,
  endedByUserId: UUID,
  request: WorkshiftEndRequestDataModel? = null,
  now: Long = System.currentTimeMillis()
): WorkshiftDataModel? {
  val requestedWorkshiftId = request?.workshiftId
    ?.trim()
    ?.takeIf { it.isNotBlank() }
    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
  val clientOperationId = request?.clientOperationId?.trim().orEmpty()

  val row = if (requestedWorkshiftId != null) {
    Workshifts
      .innerJoin(Stores, { Workshifts.storeId }, { Stores.id })
      .innerJoin(Users, { Workshifts.workerUserId }, { Users.id })
      .selectAll()
      .where {
        (Workshifts.id eq requestedWorkshiftId) and
           (Workshifts.storeId eq storeId) and
           (Workshifts.workerUserId eq workerUserId)
      }
      .forUpdate()
      .singleOrNull()
  } else {
    Workshifts
      .innerJoin(Stores, { Workshifts.storeId }, { Stores.id })
      .innerJoin(Users, { Workshifts.workerUserId }, { Users.id })
      .selectAll()
      .where {
        (Workshifts.storeId eq storeId) and
           (Workshifts.workerUserId eq workerUserId) and
           (Workshifts.isActive eq true) and
           Workshifts.endedAtMillis.isNull()
      }
      .orderBy(Workshifts.startedAtMillis, SortOrder.DESC)
      .limit(1)
      .forUpdate()
      .singleOrNull()
  } ?: return null

  val workshiftId = row[Workshifts.id]
  val workshiftAlreadyEnded = !row[Workshifts.isActive] || row[Workshifts.endedAtMillis] != null
  val endedAtMillis = (request?.endedAtMillis?.takeIf { it > 0L } ?: now)
    .coerceAtLeast(row[Workshifts.startedAtMillis])

  if (!workshiftAlreadyEnded) {
    val metadata = row[Workshifts.metadata] + buildMap {
      put("ended_source", if (request == null) "server" else "client")
      put("ended_at_millis", endedAtMillis.toString())
      if (clientOperationId.isNotBlank()) put("client_operation_id", clientOperationId)
      request?.deviceInfo?.installationId?.takeIf { it.isNotBlank() }?.let { put("device_installation_id", it) }
    }

    val updatedRows = Workshifts.update({ (Workshifts.id eq workshiftId) and (Workshifts.isActive eq true) }) {
      it[Workshifts.endedAtMillis] = endedAtMillis
      it[Workshifts.endedByUserId] = endedByUserId
      it[Workshifts.metadata] = metadata
      it[Workshifts.isActive] = false
      it[Workshifts.updatedAt] = Instant.now()
    }

    if (updatedRows > 0) {
      insertOperationLogInsideTransaction(
        actorUserId = endedByUserId,
        storeId = storeId,
        action = OPERATION_LOG_ACTION_ENDED,
        entityType = OPERATION_LOG_ENTITY_WORKSHIFT,
        entityId = workshiftId.toString(),
        title = simpleMessage("Workshift ended", ru = "Смена завершена", kk = "Ауысым аяқталды"),
        details = simpleMessage(workerUserId.toString()),
        metadata = buildMap {
          put("workshift_id", workshiftId.toString())
          put("worker_user_id", workerUserId.toString())
          put("ended_at_millis", endedAtMillis.toString())
          if (clientOperationId.isNotBlank()) put("client_operation_id", clientOperationId)
        },
        now = endedAtMillis
      )
    }
  }

  return Workshifts
    .innerJoin(Stores, { Workshifts.storeId }, { Stores.id })
    .innerJoin(Users, { Workshifts.workerUserId }, { Users.id })
    .selectAll()
    .where { Workshifts.id eq workshiftId }
    .singleOrNull()
    ?.toWorkshiftDataModel()
}

private fun ResultRow.toStoreWorkerRequestDataModel(): StoreWorkerRequestDataModel {
  return StoreWorkerRequestDataModel(
    id = this[StoreWorkerRequests.id].toString(),
    storeId = this[StoreWorkerRequests.storeId].toString(),
    storePublicId = this[Stores.publicId],
    storeName = this[Stores.name],
    requesterUserId = this[StoreWorkerRequests.requesterUserId].toString(),
    requesterPublicId = this[Users.publicId],
    direction = this[StoreWorkerRequests.direction],
    invitedByUserId = this[StoreWorkerRequests.invitedByUserId]?.toString(),
    phoneNumber = this[Users.phoneNumber],
    email = this[Users.email],
    firstName = this[Users.firstName],
    lastName = this[Users.lastName],
    status = this[StoreWorkerRequests.status],
    requestedAtMillis = this[StoreWorkerRequests.requestedAtMillis],
    decidedAtMillis = this[StoreWorkerRequests.decidedAtMillis],
    decidedByUserId = this[StoreWorkerRequests.decidedByUserId]?.toString(),
    roleId = this[StoreWorkerRequests.roleId],
    permissions = normalizeStorePermissionIds(this[StoreWorkerRequests.permissions]),
    jobTitle = this[StoreWorkerRequests.jobTitle],
    jobTitleLocalized = this[StoreWorkerRequests.jobTitleLocalized],
    salary = this[StoreWorkerRequests.salary],
    salaryCurrencyCode = this[StoreWorkerRequests.salaryCurrencyCode],
    offerNote = this[StoreWorkerRequests.offerNote],
    offerNoteLocalized = this[StoreWorkerRequests.offerNoteLocalized],
    note = this[StoreWorkerRequests.note],
    noteLocalized = this[StoreWorkerRequests.noteLocalized],
    responseNote = this[StoreWorkerRequests.responseNote],
    responseNoteLocalized = this[StoreWorkerRequests.responseNoteLocalized]
  )
}

private fun ResultRow.toStoreWorkerRoleTemplateDataModel(): StoreWorkerRoleTemplateDataModel {
  return StoreWorkerRoleTemplateDataModel(
    id = this[StoreWorkerRoleTemplates.id].toString(),
    storeId = this[StoreWorkerRoleTemplates.storeId].toString(),
    name = this[StoreWorkerRoleTemplates.name],
    description = this[StoreWorkerRoleTemplates.description],
    permissions = normalizeStorePermissionIds(this[StoreWorkerRoleTemplates.permissions]),
    createdAtMillis = this[StoreWorkerRoleTemplates.createdAtMillis],
    updatedAtMillis = this[StoreWorkerRoleTemplates.updatedAtMillis],
    isActive = this[StoreWorkerRoleTemplates.isActive]
  )
}

private fun ensureCashRegisterInsideTransaction(storeId: UUID, currencyCode: String = "KZT", now: Long = System.currentTimeMillis()): StoreCashRegisterDataModel {
  val existing = CashRegisters
    .selectAll()
    .where { CashRegisters.storeId eq storeId }
    .singleOrNull()

  if (existing != null) {
    return StoreCashRegisterDataModel(
      storeId = existing[CashRegisters.storeId].toString(),
      currentAmount = existing[CashRegisters.currentAmount],
      currencyCode = existing[CashRegisters.currencyCode],
      updatedAtMillis = existing[CashRegisters.updatedAtMillis]
    )
  }

  CashRegisters.insert {
    it[CashRegisters.storeId] = storeId
    it[CashRegisters.currentAmount] = 0.0
    it[CashRegisters.currencyCode] = currencyCode
    it[CashRegisters.updatedAtMillis] = now
  }

  return StoreCashRegisterDataModel(
    storeId = storeId.toString(),
    currentAmount = 0.0,
    currencyCode = currencyCode,
    updatedAtMillis = now
  )
}

private fun cashRegisterEventsInsideTransaction(storeId: UUID): List<CashRegisterEventDataModel> {
  return CashRegisterEvents
    .innerJoin(Users, { CashRegisterEvents.userId }, { Users.id })
    .selectAll()
    .where { CashRegisterEvents.storeId eq storeId }
    .orderBy(CashRegisterEvents.timeMillis, SortOrder.DESC)
    .map {
      CashRegisterEventDataModel(
        id = it[CashRegisterEvents.id].toString(),
        storeId = it[CashRegisterEvents.storeId].toString(),
        userId = it[CashRegisterEvents.userId].toString(),
        userName = "${it[Users.firstName]} ${it[Users.lastName]}".trim(),
        type = it[CashRegisterEvents.type],
        amount = it[CashRegisterEvents.amount],
        balanceBefore = it[CashRegisterEvents.balanceBefore],
        balanceAfter = it[CashRegisterEvents.balanceAfter],
        transactionId = it[CashRegisterEvents.transactionId]?.toString(),
        note = it[CashRegisterEvents.note],
        timeMillis = it[CashRegisterEvents.timeMillis],
        metadata = it[CashRegisterEvents.metadata]
      )
    }
}

private fun cashRegisterStateInsideTransaction(storeId: UUID): CashRegisterStateDataModel {
  val register = ensureCashRegisterInsideTransaction(storeId)
  return CashRegisterStateDataModel(
    register = register,
    events = cashRegisterEventsInsideTransaction(storeId)
  )
}

private fun applyCashRegisterTransactionEventInsideTransaction(
  storeId: UUID,
  userId: UUID,
  transactionId: UUID,
  transactionType: String,
  cashAmount: Double,
  now: Long
) {
  val roundedAmount = kotlin.math.floor(cashAmount * 100.0) / 100.0
  if (roundedAmount <= 0.0) return

  val eventType = when (transactionType) {
    "purchase" -> CASH_REGISTER_EVENT_SALE_CASH_IN
    "return" -> CASH_REGISTER_EVENT_RETURN_CASH_OUT
    else -> return
  }

  val sign = if (eventType == CASH_REGISTER_EVENT_RETURN_CASH_OUT) -1.0 else 1.0
  val register = ensureCashRegisterInsideTransaction(storeId, now = now)
  val before = register.currentAmount
  val after = kotlin.math.floor((before + sign * roundedAmount) * 100.0) / 100.0

  CashRegisters.update({ CashRegisters.storeId eq storeId }) {
    it[CashRegisters.currentAmount] = after
    it[CashRegisters.updatedAtMillis] = now
    it[CashRegisters.updatedAt] = Instant.now()
  }

  CashRegisterEvents.insert {
    it[CashRegisterEvents.id] = UUID.randomUUID()
    it[CashRegisterEvents.storeId] = storeId
    it[CashRegisterEvents.userId] = userId
    it[CashRegisterEvents.type] = eventType
    it[CashRegisterEvents.amount] = roundedAmount
    it[CashRegisterEvents.balanceBefore] = before
    it[CashRegisterEvents.balanceAfter] = after
    it[CashRegisterEvents.transactionId] = transactionId
    it[CashRegisterEvents.note] = null
    it[CashRegisterEvents.timeMillis] = now
    it[CashRegisterEvents.metadata] = mapOf("transactionType" to transactionType)
  }
}

private fun ResultRow.toGoodsItemDataModel(): GoodsItemDataModel {
  val storeIdText = this[StockItems.storeId].toString()
  val legacyBarcodes = this[StockItems.barcodes]
  val cleanBarcodeModels = stockBarcodeModels()

  return GoodsItemDataModel(
    id = this[StockItems.id].toString(),
    userId = this[StockItems.userId].toString(),
    storeId = storeIdText,

    barcodes = cleanBarcodeModels.cleanBarcodeStrings().ifEmpty { legacyBarcodes.cleanBarcodes() },
    barcodeModels = cleanBarcodeModels,
    name = this[StockItems.name],
    description = this[StockItems.description],

    measurementUnitId = this[StockItems.measurementUnitId],
    categoryIds = this[StockItems.categoryIds],

    salePrices = this[StockItems.salePrices],
    returnPrices = this[StockItems.returnPrices],
    supplyPrices = this[StockItems.supplyPrices],
    wholesalePrices = this[StockItems.wholesalePrices],
    wholesaleMinQuantity = this[StockItems.wholesaleMinQuantity],

    genericExpirationPeriod = this[StockItems.genericExpirationPeriod],

    isQuickItem = this[StockItems.isQuickItem],
    imagePaths = this[StockItems.imagePaths],

    activeShelfBatchId = this[StockItems.activeShelfBatchId]?.toString(),

    promotions = this[StockItems.promotions],

    note = this[StockItems.note],
    noteLocalized = this[StockItems.noteLocalized],
    conditions = this[StockItems.conditions],

    createdAtMillis = this[StockItems.createdAtMillis],
    updatedAtMillis = this[StockItems.updatedAtMillis],
    isActive = this[StockItems.isActive]
  )
}

private fun GoodsItemDataModel.matchesParentStockSearchQuery(rawQuery: String?): Boolean {
  val queryTokens = rawQuery
    ?.trim()
    ?.lowercase()
    ?.split(Regex("\\s+"))
    ?.filter { it.isNotBlank() }
    .orEmpty()

  if (queryTokens.isEmpty()) return true

  val searchableText = buildList {
    add(id)
    add(userId)
    add(storeId)
    addAll(allBarcodeValues())
    addAll(allBarcodeValues().map { it.toStoredGoodsItemBarcode() })
    addAll(name.map { it.value })
    addAll(description.map { it.value })
    add(measurementUnitId)
    addAll(categoryIds)
    addAll(salePrices.flatMap { listOf(it.price, it.currency, it.supplierId) })
    addAll(returnPrices.flatMap { listOf(it.price, it.currency, it.supplierId) })
    addAll(supplyPrices.flatMap { listOf(it.price, it.currency, it.supplierId) })
    addAll(wholesalePrices.flatMap { listOf(it.price, it.currency, it.supplierId) })
    note?.let { add(it) }
    addAll(noteLocalized.map { it.value })
    addAll(conditions)
  }.joinToString(" ").lowercase()

  return queryTokens.all { token -> searchableText.contains(token) }
}

private fun decodeSupplierStringList(value: String?): List<String> {
  return value
    ?.takeIf { it.isNotBlank() }
    ?.let { raw -> runCatching { jsonBase.decodeFromString<List<String>>(raw) }.getOrNull() }
    .orEmpty()
}

private fun ResultRow.toSupplierDataModel(): SupplierDataModel {
  return SupplierDataModel(
    id = this[Suppliers.id].toString(),
    userIds = decodeSupplierStringList(this[Suppliers.userIds]),
    typeIds = decodeSupplierStringList(this[Suppliers.typeIds]).takeIf { it.isNotEmpty() },
    categoryIds = decodeSupplierStringList(this[Suppliers.categoryIds]),
    name = runCatching { jsonBase.decodeFromString<List<LocalizedStringDataModel>>(this[Suppliers.name]) }
      .getOrDefault(listOf(LocalizedStringDataModel("main", this[Suppliers.name]))),
    phoneNumbers = decodeSupplierStringList(this[Suppliers.phoneNumbers]),
    emails = decodeSupplierStringList(this[Suppliers.emails]),
    addedAt = this[Suppliers.addedAt].toEpochMilli(),
    isActive = this[Suppliers.isActive]
  )
}

private fun ResultRow.toSupplierOrderDataModel(): SupplierOrderDataModel = SupplierOrderDataModel(
  id = this[SupplierOrders.id].toString(),
  userId = this[SupplierOrders.userId].toString(),
  storeId = this[SupplierOrders.storeId].toString(),
  supplierId = this[SupplierOrders.supplierId].toString(),
  amount = this[SupplierOrders.amount],
  orderedAtMillis = this[SupplierOrders.orderedAtMillis],
  desiredDeliveryTimeMillis = this[SupplierOrders.desiredDeliveryTimeMillis],
  confirmedDeliveryTimeMillis = this[SupplierOrders.confirmedDeliveryTimeMillis],
  deliveredAtMillis = this[SupplierOrders.deliveredAtMillis],
  storeAddress = this[SupplierOrders.storeAddress],
  additionalNotes = this[SupplierOrders.additionalNotes],
  additionalNotesLocalized = this[SupplierOrders.additionalNotesLocalized],
  supplierComment = this[SupplierOrders.supplierComment],
  supplierCommentLocalized = this[SupplierOrders.supplierCommentLocalized],
  paymentTerms = this[SupplierOrders.paymentTerms],
  externalReference = this[SupplierOrders.externalReference],
  storeContactUserId = this[SupplierOrders.storeContactUserId]?.toString(),
  status = runCatching { SupplierOrderStatusDataModel.valueOf(this[SupplierOrders.status]) }.getOrDefault(SupplierOrderStatusDataModel.Draft),
  createdAtMillis = this[SupplierOrders.createdAtMillis],
  updatedAtMillis = this[SupplierOrders.updatedAtMillis],
  isActive = this[SupplierOrders.isActive]
)

private fun ResultRow.toSupplierOrderLineDataModel(): SupplierOrderLineDataModel = SupplierOrderLineDataModel(
  id = this[SupplierOrderLines.id].toString(),
  orderId = this[SupplierOrderLines.orderId].toString(),
  goodsItemId = this[SupplierOrderLines.goodsItemId].toString(),
  requestedQuantity = this[SupplierOrderLines.requestedQuantity],
  expectedSupplyPrice = this[SupplierOrderLines.expectedSupplyPrice],
  desiredExpirationDateMillis = this[SupplierOrderLines.desiredExpirationDateMillis],
  additionalNotes = this[SupplierOrderLines.additionalNotes],
  additionalNotesLocalized = this[SupplierOrderLines.additionalNotesLocalized],
  supplierComment = this[SupplierOrderLines.supplierComment],
  supplierCommentLocalized = this[SupplierOrderLines.supplierCommentLocalized],
  supplierAcceptedQuantity = this[SupplierOrderLines.supplierAcceptedQuantity],
  supplierOfferedSupplyPrice = this[SupplierOrderLines.supplierOfferedSupplyPrice],
  substituteGoodsItemId = this[SupplierOrderLines.substituteGoodsItemId]?.toString(),
  deliveredBatchIds = this[SupplierOrderLines.deliveredBatchIds],
  isActive = this[SupplierOrderLines.isActive]
)

private fun SupplierOrderDataModel.cleanForStorage(userId: UUID, storeId: UUID, supplierId: UUID, now: Long): SupplierOrderDataModel = copy(
  userId = userId.toString(),
  storeId = storeId.toString(),
  supplierId = supplierId.toString(),
  orderedAtMillis = orderedAtMillis.takeIf { it > 0L } ?: now,
  additionalNotes = additionalNotes?.trim()?.takeIf { it.isNotBlank() },
  additionalNotesLocalized = additionalNotesLocalized
    .map { it.copy(language = it.language.trim(), value = it.value.trim()) }
    .filter { it.language.isNotBlank() && it.value.isNotBlank() }
    .distinctBy { it.language },
  supplierComment = supplierComment?.trim()?.takeIf { it.isNotBlank() },
  supplierCommentLocalized = supplierCommentLocalized
    .map { it.copy(language = it.language.trim(), value = it.value.trim()) }
    .filter { it.language.isNotBlank() && it.value.isNotBlank() }
    .distinctBy { it.language },
  paymentTerms = paymentTerms?.trim()?.takeIf { it.isNotBlank() },
  externalReference = externalReference?.trim()?.takeIf { it.isNotBlank() },
  updatedAtMillis = now,
  createdAtMillis = createdAtMillis.takeIf { it > 0L } ?: now,
  isActive = isActive
)

private fun SupplierOrderLineDataModel.cleanForStorage(orderId: UUID): SupplierOrderLineDataModel = copy(
  orderId = orderId.toString(),
  requestedQuantity = requestedQuantity.copy(total = requestedQuantity.total.coerceAtLeast(0.0)),
  additionalNotes = additionalNotes?.trim()?.takeIf { it.isNotBlank() },
  additionalNotesLocalized = additionalNotesLocalized
    .map { it.copy(language = it.language.trim(), value = it.value.trim()) }
    .filter { it.language.isNotBlank() && it.value.isNotBlank() }
    .distinctBy { it.language },
  supplierComment = supplierComment?.trim()?.takeIf { it.isNotBlank() },
  supplierCommentLocalized = supplierCommentLocalized
    .map { it.copy(language = it.language.trim(), value = it.value.trim()) }
    .filter { it.language.isNotBlank() && it.value.isNotBlank() }
    .distinctBy { it.language }
)

private fun supplierOrderWithLinesInsideTransaction(orderId: UUID): SupplierOrderWithLinesDataModel? {
  val order = SupplierOrders
    .selectAll()
    .where { SupplierOrders.id eq orderId }
    .singleOrNull()
    ?.toSupplierOrderDataModel()
    ?: return null

  val lines = SupplierOrderLines
    .selectAll()
    .where { SupplierOrderLines.orderId eq orderId }
    .map { it.toSupplierOrderLineDataModel() }

  return SupplierOrderWithLinesDataModel(order, lines)
}

private fun supplierOrderStatusAllowedFromSupplier(
  requested: SupplierOrderStatusDataModel,
  current: SupplierOrderStatusDataModel
): SupplierOrderStatusDataModel {
  return when (requested) {
    SupplierOrderStatusDataModel.SeenBySupplier,
    SupplierOrderStatusDataModel.Confirmed,
    SupplierOrderStatusDataModel.Packed,
    SupplierOrderStatusDataModel.InDelivery,
    SupplierOrderStatusDataModel.IssueReported,
    SupplierOrderStatusDataModel.Cancelled -> requested
    SupplierOrderStatusDataModel.PartiallyDelivered,
    SupplierOrderStatusDataModel.Delivered -> current
    SupplierOrderStatusDataModel.Draft,
    SupplierOrderStatusDataModel.Sent -> if (current == SupplierOrderStatusDataModel.Draft || current == SupplierOrderStatusDataModel.Sent) requested else current
  }
}

private fun List<SupplierOrderWithLinesDataModel>.withSupplierDeskSnapshotsInsideTransaction(): List<SupplierOrderWithLinesDataModel> {
  if (isEmpty()) return this

  val storeIds = mapNotNull { orderWithLines ->
    runCatching { UUID.fromString(orderWithLines.order.storeId) }.getOrNull()
  }.distinct()

  val goodsItemIds = flatMap { orderWithLines -> orderWithLines.lines }
    .flatMap { line -> listOf(line.goodsItemId, line.substituteGoodsItemId.orEmpty()) }
    .mapNotNull { rawGoodsItemId -> runCatching { UUID.fromString(rawGoodsItemId) }.getOrNull() }
    .distinct()

  val storeRowsById = if (storeIds.isEmpty()) {
    emptyMap<String, ResultRow>()
  } else {
    Stores
      .selectAll()
      .where { Stores.id inList storeIds }
      .associateBy { it[Stores.id].toString() }
  }

  val goodsRowsById = if (goodsItemIds.isEmpty()) {
    emptyMap<String, ResultRow>()
  } else {
    StockItems
      .selectAll()
      .where { StockItems.id inList goodsItemIds }
      .associateBy { it[StockItems.id].toString() }
  }

  return map { orderWithLines ->
    val storeRow = storeRowsById[orderWithLines.order.storeId]
    val orderWithSnapshots = orderWithLines.order.copy(
      storeNameSnapshot = storeRow?.get(Stores.name).orEmpty(),
      storePublicIdSnapshot = storeRow?.get(Stores.publicId).orEmpty(),
      storeAddressTextSnapshot = storeRow?.get(Stores.address).orEmpty()
    )

    val lineSnapshots = orderWithLines.lines.map { line ->
      val goodsRow = goodsRowsById[line.goodsItemId]
      val substituteGoodsRow = line.substituteGoodsItemId?.let { goodsRowsById[it] }
      line.copy(
        goodsItemNameSnapshot = goodsRow?.get(StockItems.name).orEmpty(),
        goodsItemBarcodeSnapshots = goodsRow?.stockBarcodeValues().orEmpty(),
        goodsItemMeasurementUnitIdSnapshot = goodsRow?.get(StockItems.measurementUnitId),
        substituteGoodsItemNameSnapshot = substituteGoodsRow?.get(StockItems.name).orEmpty(),
        substituteGoodsItemBarcodeSnapshots = substituteGoodsRow?.stockBarcodeValues().orEmpty(),
        substituteGoodsItemMeasurementUnitIdSnapshot = substituteGoodsRow?.get(StockItems.measurementUnitId)
      )
    }

    SupplierOrderWithLinesDataModel(orderWithSnapshots, lineSnapshots)
  }
}

private fun accessibleSupplierProfilesForUserInsideTransaction(userId: UUID): List<SupplierDataModel> =
  Suppliers
    .selectAll()
    .where { Suppliers.isActive eq true }
    .map { it.toSupplierDataModel() }
    .filter { supplier -> supplier.userIds.contains(userId.toString()) }
    .distinctBy { it.id }

private fun accessibleSupplierIdsForUserInsideTransaction(userId: UUID): List<UUID> =
  accessibleSupplierProfilesForUserInsideTransaction(userId)
    .mapNotNull { supplier -> runCatching { UUID.fromString(supplier.id) }.getOrNull() }
    .distinct()

private fun SupplierOrderStatusDataModel.isClosedForSupplierDashboard(): Boolean =
  this == SupplierOrderStatusDataModel.Delivered || this == SupplierOrderStatusDataModel.Cancelled

private const val AITA_SUPPLIER_DAY_MILLIS: Long = 24L * 60L * 60L * 1000L

private fun supplierDashboardDayStartMillis(now: Long): Long = now - (now % AITA_SUPPLIER_DAY_MILLIS)

private fun supplierDashboardDeliveryBucketId(now: Long, dueAtMillis: Long?): String {
  val todayStart = supplierDashboardDayStartMillis(now)
  val safeDue = dueAtMillis ?: return "unscheduled"
  return when {
    safeDue < todayStart -> "overdue"
    safeDue < todayStart + AITA_SUPPLIER_DAY_MILLIS -> "today"
    safeDue < todayStart + 2L * AITA_SUPPLIER_DAY_MILLIS -> "tomorrow"
    safeDue < todayStart + 7L * AITA_SUPPLIER_DAY_MILLIS -> "week"
    else -> "later"
  }
}

private fun supplierDashboardDeliveryBucketRank(bucketId: String): Int = when (bucketId) {
  "overdue" -> 0
  "today" -> 1
  "tomorrow" -> 2
  "week" -> 3
  "later" -> 4
  else -> 5
}

private fun supplierDashboardDeliveryBucketTitle(bucketId: String): List<LocalizedStringDataModel> = when (bucketId) {
  "overdue" -> simpleMessage(
    main = "Overdue promises",
    ru = "Просроченные обещания",
    kk = "Кешіккен уәделер"
  )
  "today" -> simpleMessage(
    main = "Due today",
    ru = "На сегодня",
    kk = "Бүгінге"
  )
  "tomorrow" -> simpleMessage(
    main = "Due tomorrow",
    ru = "На завтра",
    kk = "Ертеңге"
  )
  "week" -> simpleMessage(
    main = "This week",
    ru = "На этой неделе",
    kk = "Осы аптада"
  )
  "later" -> simpleMessage(
    main = "Later",
    ru = "Позже",
    kk = "Кейін"
  )
  else -> simpleMessage(
    main = "No promised date",
    ru = "Без обещанной даты",
    kk = "Уәде күні жоқ"
  )
}

private fun supplierModeDashboardInsideTransaction(userId: UUID): SupplierModeDashboardDataModel {
  val now = System.currentTimeMillis()
  val supplierProfiles = accessibleSupplierProfilesForUserInsideTransaction(userId)
  val supplierIds = supplierProfiles
    .mapNotNull { supplier -> runCatching { UUID.fromString(supplier.id) }.getOrNull() }
    .distinct()
  if (supplierIds.isEmpty()) {
    return SupplierModeDashboardDataModel(generatedAtMillis = now)
  }

  val rawOrders = SupplierOrders
    .selectAll()
    .where { (SupplierOrders.supplierId inList supplierIds) and (SupplierOrders.isActive eq true) }
    .map { it.toSupplierOrderDataModel() }
    .sortedByDescending { it.updatedAtMillis.takeIf { value -> value > 0L } ?: it.orderedAtMillis }

  val orderIds = rawOrders.mapNotNull { runCatching { UUID.fromString(it.id) }.getOrNull() }
  val rawLines = if (orderIds.isEmpty()) {
    emptyList()
  } else {
    SupplierOrderLines
      .selectAll()
      .where { (SupplierOrderLines.orderId inList orderIds) and (SupplierOrderLines.isActive eq true) }
      .map { it.toSupplierOrderLineDataModel() }
  }

  val bundles = rawOrders.map { order ->
    SupplierOrderWithLinesDataModel(order, rawLines.filter { it.orderId == order.id })
  }.withSupplierDeskSnapshotsInsideTransaction()

  val orders = bundles.map { it.order }
  val lines = bundles.flatMap { it.lines }.filter { it.isActive }
  val linesByOrder = lines.groupBy { it.orderId }
  val ordersById = orders.associateBy { it.id }

  val contracts = SupplierPartnershipContracts
    .selectAll()
    .where { (SupplierPartnershipContracts.supplierId inList supplierIds) and (SupplierPartnershipContracts.isActive eq true) }
    .map { it.toSupplierPartnershipContractDataModel() }

  val supplierPriceRows = SupplierGoodsPrices
    .selectAll()
    .where { (SupplierGoodsPrices.supplierId inList supplierIds) and (SupplierGoodsPrices.isActive eq true) }
    .map { it.toSupplierGoodsPriceDataModel() }

  val priceBookGoodsItemIds = supplierPriceRows
    .map { it.goodsItemId }
    .filter { it.isNotBlank() }
    .distinct()

  val statusBuckets = SupplierOrderStatusDataModel.entries.mapNotNull { status ->
    val statusOrders = orders.filter { it.status == status }
    val lineCount = statusOrders.sumOf { linesByOrder[it.id].orEmpty().size }
    if (statusOrders.isEmpty() && lineCount == 0) null else SupplierDashboardStatusBucketDataModel(
      status = status,
      orderCount = statusOrders.size,
      lineCount = lineCount
    )
  }

  val demandHighlights = lines
    .groupBy { it.goodsItemId }
    .mapNotNull demandHighlightItem@{ (goodsItemId, itemLines) ->
      val relatedOrders = itemLines.mapNotNull { ordersById[it.orderId] }.distinctBy { it.id }
      val latestOrder = relatedOrders.maxByOrNull { it.updatedAtMillis.takeIf { value -> value > 0L } ?: it.orderedAtMillis }
        ?: return@demandHighlightItem null
      val sampleLine = itemLines.maxByOrNull { line -> ordersById[line.orderId]?.updatedAtMillis ?: 0L } ?: itemLines.firstOrNull()
      ?: return@demandHighlightItem null
      SupplierDashboardDemandDataModel(
        goodsItemId = goodsItemId,
        goodsItemNameSnapshot = sampleLine.goodsItemNameSnapshot,
        barcodeSnapshots = sampleLine.goodsItemBarcodeSnapshots,
        measurementUnitIdSnapshot = sampleLine.goodsItemMeasurementUnitIdSnapshot,
        requestedQuantityTotal = itemLines.sumOf { it.requestedQuantity.total.coerceAtLeast(0.0) },
        requestLineCount = itemLines.size,
        openOrderCount = relatedOrders.count { !it.status.isClosedForSupplierDashboard() },
        storeCount = relatedOrders.map { it.storeId }.filter { it.isNotBlank() }.distinct().size,
        latestStatus = latestOrder.status,
        latestActivityMillis = latestOrder.updatedAtMillis.takeIf { it > 0L } ?: latestOrder.orderedAtMillis,
        latestExpectedSupplyPrice = itemLines.asSequence().mapNotNull { it.expectedSupplyPrice }.firstOrNull(),
        latestOfferedSupplyPrice = itemLines.asSequence().mapNotNull { it.supplierOfferedSupplyPrice }.firstOrNull()
      )
    }
    .sortedWith(
      compareByDescending<SupplierDashboardDemandDataModel> { it.openOrderCount }
        .thenByDescending { it.requestLineCount }
        .thenByDescending { it.latestActivityMillis }
    )
    .take(8)

  val contractsByStore = contracts.groupBy { it.storeId }
  val partnerHighlights = orders
    .groupBy { it.storeId.ifBlank { it.storePublicIdSnapshot }.ifBlank { it.id } }
    .map { (storeKey, storeOrdersRaw) ->
      val storeOrders = storeOrdersRaw.sortedByDescending { it.updatedAtMillis.takeIf { value -> value > 0L } ?: it.orderedAtMillis }
      val latest = storeOrders.firstOrNull()
      val storeContracts = latest?.storeId?.let { contractsByStore[it].orEmpty() }.orEmpty()
      SupplierDashboardPartnerDataModel(
        storeId = latest?.storeId ?: storeKey,
        storeNameSnapshot = latest?.storeNameSnapshot.orEmpty(),
        storePublicIdSnapshot = latest?.storePublicIdSnapshot.orEmpty(),
        storeAddressTextSnapshot = latest?.storeAddressTextSnapshot.orEmpty(),
        orderCount = storeOrders.size,
        openOrderCount = storeOrders.count { !it.status.isClosedForSupplierDashboard() },
        deliveredOrderCount = storeOrders.count { it.status == SupplierOrderStatusDataModel.Delivered || it.status == SupplierOrderStatusDataModel.PartiallyDelivered },
        issueOrderCount = storeOrders.count { it.status == SupplierOrderStatusDataModel.IssueReported || it.status == SupplierOrderStatusDataModel.Cancelled },
        latestStatus = latest?.status ?: SupplierOrderStatusDataModel.Draft,
        latestActivityMillis = latest?.updatedAtMillis?.takeIf { it > 0L } ?: latest?.orderedAtMillis ?: 0L,
        activeContractCount = storeContracts.count { it.status == SUPPLIER_CONTRACT_STATUS_ACTIVE },
        pendingContractCount = storeContracts.count { it.status == SUPPLIER_CONTRACT_STATUS_PENDING_SUPPLIER || it.status == SUPPLIER_CONTRACT_STATUS_PENDING_STORE }
      )
    }
    .sortedWith(
      compareByDescending<SupplierDashboardPartnerDataModel> { it.openOrderCount }
        .thenByDescending { it.issueOrderCount }
        .thenByDescending { it.latestActivityMillis }
    )
    .take(8)

  val actionQueue = bundles
    .asSequence()
    .filter { bundle -> bundle.order.isActive && !bundle.order.status.isClosedForSupplierDashboard() }
    .mapNotNull { bundle ->
      val order = bundle.order
      val bundleLines = bundle.lines.filter { it.isActive }
      val dueAtMillis = order.confirmedDeliveryTimeMillis ?: order.desiredDeliveryTimeMillis
      val missingAcceptedQuantityCount = bundleLines.count { it.supplierAcceptedQuantity == null }
      val missingOfferedPriceCount = bundleLines.count { it.supplierOfferedSupplyPrice == null }
      val missingHeaderDetails = order.status in listOf(
        SupplierOrderStatusDataModel.Confirmed,
        SupplierOrderStatusDataModel.Packed,
        SupplierOrderStatusDataModel.InDelivery
      ) && (order.confirmedDeliveryTimeMillis == null || order.paymentTerms.isNullOrBlank() || order.externalReference.isNullOrBlank())
      val actionType = when {
        order.status == SupplierOrderStatusDataModel.IssueReported -> "issue"
        order.status == SupplierOrderStatusDataModel.Sent || order.status == SupplierOrderStatusDataModel.SeenBySupplier -> "answer"
        order.status == SupplierOrderStatusDataModel.Confirmed && (missingAcceptedQuantityCount > 0 || missingOfferedPriceCount > 0) -> "complete_response"
        missingHeaderDetails -> "terms"
        order.status == SupplierOrderStatusDataModel.Confirmed -> "pack"
        order.status == SupplierOrderStatusDataModel.Packed -> "dispatch"
        order.status == SupplierOrderStatusDataModel.InDelivery || order.status == SupplierOrderStatusDataModel.PartiallyDelivered -> "delivery"
        else -> null
      } ?: return@mapNotNull null
      val dueBoost = dueAtMillis?.let { due ->
        when {
          due < now -> 20
          due < now + 24L * 60L * 60L * 1000L -> 12
          due < now + 3L * 24L * 60L * 60L * 1000L -> 6
          else -> 0
        }
      } ?: 0
      val basePriority = when (actionType) {
        "issue" -> 100
        "answer" -> 90
        "complete_response" -> 84
        "pack" -> 72
        "dispatch" -> 70
        "terms" -> 58
        "delivery" -> 48
        else -> 10
      }
      val preview = bundleLines
        .take(3)
        .joinToString(" • ") { line ->
          val title = line.goodsItemNameSnapshot.firstOrNull { it.value.isNotBlank() }?.value
            ?: line.goodsItemBarcodeSnapshots.firstOrNull()
            ?: line.goodsItemId.take(8)
          val quantityText = line.requestedQuantity.total.takeIf { it > 0.0 }?.let { value ->
            val whole = value.toLong()
            if (value == whole.toDouble()) whole.toString() else value.toString()
          }.orEmpty()
          if (quantityText.isBlank()) title else "$title × $quantityText"
        }
      SupplierDashboardActionDataModel(
        actionId = "${actionType}_${order.id}",
        actionType = actionType,
        priority = basePriority + dueBoost,
        orderId = order.id,
        storeId = order.storeId,
        supplierId = order.supplierId,
        storeNameSnapshot = order.storeNameSnapshot,
        storePublicIdSnapshot = order.storePublicIdSnapshot,
        status = order.status,
        dueAtMillis = dueAtMillis,
        latestActivityMillis = order.updatedAtMillis.takeIf { it > 0L } ?: order.orderedAtMillis,
        lineCount = bundleLines.size,
        missingAcceptedQuantityCount = missingAcceptedQuantityCount,
        missingOfferedPriceCount = missingOfferedPriceCount,
        amount = order.amount,
        goodsPreview = preview.takeIf { it.isNotBlank() }?.let { listOf(LocalizedStringDataModel("main", it)) }.orEmpty()
      )
    }
    .sortedWith(
      compareByDescending<SupplierDashboardActionDataModel> { it.priority }
        .thenBy { it.dueAtMillis ?: Long.MAX_VALUE }
        .thenByDescending { it.latestActivityMillis }
    )
    .take(10)
    .toList()

  val deliveryBuckets = bundles
    .asSequence()
    .filter { bundle -> bundle.order.isActive && !bundle.order.status.isClosedForSupplierDashboard() }
    .groupBy { bundle ->
      supplierDashboardDeliveryBucketId(
        now = now,
        dueAtMillis = bundle.order.confirmedDeliveryTimeMillis ?: bundle.order.desiredDeliveryTimeMillis
      )
    }
    .map { (bucketId, bucketBundles) ->
      val bucketOrders = bucketBundles.map { it.order }.distinctBy { it.id }
      val bucketLines = bucketBundles.flatMap { bundle -> bundle.lines.filter { it.isActive } }
      val dueValues = bucketOrders.mapNotNull { order -> order.confirmedDeliveryTimeMillis ?: order.desiredDeliveryTimeMillis }
      val preview = bucketLines
        .distinctBy { it.goodsItemId.ifBlank { it.id } }
        .take(4)
        .joinToString(" • ") { line ->
          line.goodsItemNameSnapshot.firstOrNull { it.value.isNotBlank() }?.value
            ?: line.goodsItemBarcodeSnapshots.firstOrNull()
            ?: line.goodsItemId.take(8)
        }

      SupplierDashboardDeliveryBucketDataModel(
        bucketId = bucketId,
        title = supplierDashboardDeliveryBucketTitle(bucketId),
        orderCount = bucketOrders.size,
        lineCount = bucketLines.size,
        storeCount = bucketOrders.map { it.storeId }.filter { it.isNotBlank() }.distinct().size,
        actionRequiredOrderCount = bucketOrders.count { order ->
          order.status == SupplierOrderStatusDataModel.Sent ||
             order.status == SupplierOrderStatusDataModel.SeenBySupplier ||
             order.status == SupplierOrderStatusDataModel.IssueReported
        },
        packedOrderCount = bucketOrders.count { it.status == SupplierOrderStatusDataModel.Packed },
        inDeliveryOrderCount = bucketOrders.count { it.status == SupplierOrderStatusDataModel.InDelivery },
        issueOrderCount = bucketOrders.count { it.status == SupplierOrderStatusDataModel.IssueReported || it.status == SupplierOrderStatusDataModel.Cancelled },
        earliestDueAtMillis = dueValues.minOrNull(),
        latestDueAtMillis = dueValues.maxOrNull(),
        goodsPreview = preview.takeIf { it.isNotBlank() }?.let { listOf(LocalizedStringDataModel("main", it)) }.orEmpty()
      )
    }
    .sortedWith(
      compareBy<SupplierDashboardDeliveryBucketDataModel> { supplierDashboardDeliveryBucketRank(it.bucketId) }
        .thenByDescending { it.actionRequiredOrderCount }
        .thenByDescending { it.orderCount }
    )


  val openBundles = bundles.filter { bundle ->
    bundle.order.isActive && !bundle.order.status.isClosedForSupplierDashboard()
  }
  val openLines = openBundles.flatMap { bundle -> bundle.lines.filter { it.isActive } }
  val supplierPriceBookKeys = supplierPriceRows
    .map { price -> "${price.storeId}|${price.supplierId}|${price.goodsItemId}" }
    .toSet()
  fun supplierBridgeTargetGoodsItemId(line: SupplierOrderLineDataModel): String =
    line.substituteGoodsItemId?.takeIf { it.isNotBlank() } ?: line.goodsItemId
  fun supplierPriceBookKeyFor(order: SupplierOrderDataModel, line: SupplierOrderLineDataModel): String {
    val targetGoodsItemId = supplierBridgeTargetGoodsItemId(line)
    return "${order.storeId}|${order.supplierId}|$targetGoodsItemId"
  }
  val priceBookCoveredLineCount = openLines.count { line ->
    val order = ordersById[line.orderId] ?: return@count false
    supplierPriceBookKeyFor(order, line) in supplierPriceBookKeys
  }
  val missingAcceptedQuantityLineCount = openLines.count { it.supplierAcceptedQuantity == null }
  val missingOfferedPriceLineCount = openLines.count { it.supplierOfferedSupplyPrice == null }
  val responseReadyStatuses = setOf(
    SupplierOrderStatusDataModel.Sent,
    SupplierOrderStatusDataModel.SeenBySupplier,
    SupplierOrderStatusDataModel.Confirmed
  )
  val responseReadyOrders = openBundles.filter { bundle ->
    val bundleLines = bundle.lines.filter { it.isActive }
    bundleLines.isNotEmpty() &&
       bundle.order.status in responseReadyStatuses &&
       bundleLines.all { it.supplierAcceptedQuantity != null && it.supplierOfferedSupplyPrice != null } &&
       bundle.order.confirmedDeliveryTimeMillis != null
  }
  val readyToPackOrders = openBundles.filter { bundle ->
    val bundleLines = bundle.lines.filter { it.isActive }
    bundle.order.status == SupplierOrderStatusDataModel.Confirmed &&
       bundleLines.isNotEmpty() &&
       bundleLines.all { it.supplierAcceptedQuantity != null && it.supplierOfferedSupplyPrice != null } &&
       bundle.order.confirmedDeliveryTimeMillis != null
  }
  val readyAmountLines = openLines.filter { line ->
    line.supplierAcceptedQuantity != null && line.supplierOfferedSupplyPrice != null
  }
  val readyAmountValue = readyAmountLines.sumOf { line ->
    (line.supplierAcceptedQuantity?.total ?: 0.0).coerceAtLeast(0.0) *
       (line.supplierOfferedSupplyPrice?.price?.toMoneyDouble() ?: 0.0)
  }.roundMoney()
  val readyAmountCurrency = readyAmountLines.firstNotNullOfOrNull { line ->
    line.supplierOfferedSupplyPrice?.currency?.takeIf { it.isNotBlank() }
  } ?: supplierPriceRows.firstOrNull()?.supplyPrice?.currency?.takeIf { it.isNotBlank() } ?: "KZT"
  val readiness = SupplierDashboardReadinessDataModel(
    openOrderCount = openBundles.size,
    answerNeededOrderCount = openBundles.count { bundle ->
      bundle.order.status == SupplierOrderStatusDataModel.Sent ||
         bundle.order.status == SupplierOrderStatusDataModel.SeenBySupplier ||
         bundle.order.status == SupplierOrderStatusDataModel.IssueReported
    },
    responseReadyOrderCount = responseReadyOrders.size,
    readyToPackOrderCount = readyToPackOrders.size,
    packReadyLineCount = readyToPackOrders.sumOf { bundle -> bundle.lines.count { it.isActive } },
    missingAcceptedQuantityLineCount = missingAcceptedQuantityLineCount,
    missingOfferedPriceLineCount = missingOfferedPriceLineCount,
    priceBookCoveredLineCount = priceBookCoveredLineCount,
    priceBookMissingLineCount = (openLines.size - priceBookCoveredLineCount).coerceAtLeast(0),
    priceBookCoveragePercent = if (openLines.isEmpty()) 0 else (((priceBookCoveredLineCount * 100.0) / openLines.size) + 0.5).toInt().coerceIn(0, 100),
    estimatedReadyAmount = readyAmountValue.takeIf { it > 0.0 }?.let { amount ->
      PriceDataModel(amount.toStockMoneyText(), readyAmountCurrency, supplierIds.firstOrNull()?.toString().orEmpty())
    },
    earliestDueAtMillis = openBundles.mapNotNull { bundle -> bundle.order.confirmedDeliveryTimeMillis ?: bundle.order.desiredDeliveryTimeMillis }.minOrNull(),
    generatedAtMillis = now
  )

  val manufacturerBridge = openLines
    .filter { line -> supplierBridgeTargetGoodsItemId(line).isNotBlank() }
    .groupBy { line -> supplierBridgeTargetGoodsItemId(line) }
    .mapNotNull bridgeItem@{ (goodsItemId, itemLines) ->
      val relatedOrders = itemLines
        .mapNotNull { line -> ordersById[line.orderId] }
        .filter { order -> !order.status.isClosedForSupplierDashboard() }
        .distinctBy { it.id }
      if (relatedOrders.isEmpty()) return@bridgeItem null

      val sampleLine = itemLines
        .maxByOrNull { line -> ordersById[line.orderId]?.updatedAtMillis ?: 0L }
        ?: itemLines.firstOrNull()
        ?: return@bridgeItem null
      val sampleUsesSubstitute = sampleLine.substituteGoodsItemId?.takeIf { it.isNotBlank() } == goodsItemId
      val requestedQuantityTotal = itemLines.sumOf { line -> line.requestedQuantity.total.coerceAtLeast(0.0) }.roundMoney()
      val acceptedQuantityTotal = itemLines.sumOf { line -> line.supplierAcceptedQuantity?.total?.coerceAtLeast(0.0) ?: 0.0 }.roundMoney()
      val missingQuantityTotal = (requestedQuantityTotal - acceptedQuantityTotal).coerceAtLeast(0.0).roundMoney()
      val openOrderCount = relatedOrders.count { order -> !order.status.isClosedForSupplierDashboard() }
      val confirmedOrderCount = relatedOrders.count { order ->
        order.status == SupplierOrderStatusDataModel.Confirmed ||
           order.status == SupplierOrderStatusDataModel.Packed ||
           order.status == SupplierOrderStatusDataModel.InDelivery
      }
      val priceBookRowsForItem = supplierPriceRows.filter { price -> price.goodsItemId == goodsItemId }
      val responseCoveredLineCount = itemLines.count { line ->
        line.supplierAcceptedQuantity != null &&
           (line.supplierOfferedSupplyPrice != null || run {
             val order = ordersById[line.orderId]
             order != null && supplierPriceBookKeyFor(order, line) in supplierPriceBookKeys
           })
      }
      val amountLines = itemLines.mapNotNull lineAmount@{ line ->
        val order = ordersById[line.orderId] ?: return@lineAmount null
        val quantity = line.supplierAcceptedQuantity?.total?.coerceAtLeast(0.0) ?: return@lineAmount null
        val price = line.supplierOfferedSupplyPrice ?: supplierPriceRows
          .filter { price ->
            price.isActive &&
               price.storeId == order.storeId &&
               price.supplierId == order.supplierId &&
               price.goodsItemId == goodsItemId
          }
          .maxByOrNull { price -> price.lastUsedAtMillis ?: price.updatedAtMillis }
          ?.supplyPrice
        ?: return@lineAmount null
        quantity to price
      }
      val amountValue = amountLines.sumOf { (quantity, price) -> quantity * price.price.toMoneyDouble() }.roundMoney()
      val amountCurrency = amountLines.firstOrNull()?.second?.currency?.takeIf { it.isNotBlank() }
        ?: priceBookRowsForItem.firstOrNull()?.supplyPrice?.currency?.takeIf { it.isNotBlank() }
        ?: supplierPriceRows.firstOrNull()?.supplyPrice?.currency?.takeIf { it.isNotBlank() }
        ?: "KZT"
      val earliestDueAtMillis = relatedOrders
        .mapNotNull { order -> order.confirmedDeliveryTimeMillis ?: order.desiredDeliveryTimeMillis }
        .minOrNull()
      val latestActivityMillis = relatedOrders
        .map { order -> order.updatedAtMillis.takeIf { value -> value > 0L } ?: order.orderedAtMillis }
        .maxOrNull()
        ?: 0L
      val missingResponseLineCount = itemLines.count { line -> line.supplierAcceptedQuantity == null || line.supplierOfferedSupplyPrice == null }
      val duePressure = earliestDueAtMillis?.let { due ->
        when {
          due < now -> 24
          due < now + AITA_SUPPLIER_DAY_MILLIS -> 18
          due < now + 3L * AITA_SUPPLIER_DAY_MILLIS -> 12
          else -> 4
        }
      } ?: 2
      val suggestedAction = when {
        relatedOrders.any { order -> order.status == SupplierOrderStatusDataModel.Packed || order.status == SupplierOrderStatusDataModel.InDelivery } -> "ship"
        confirmedOrderCount > 0 && acceptedQuantityTotal > 0.0 -> "produce"
        missingResponseLineCount > 0 -> "quote"
        priceBookRowsForItem.isEmpty() -> "price_book"
        missingQuantityTotal > 0.0 -> "backorder"
        else -> "watch"
      }
      val priorityScore = (openOrderCount * 10 + confirmedOrderCount * 7 + missingResponseLineCount * 5 + duePressure + missingQuantityTotal.coerceAtMost(999.0).toInt())
        .coerceAtLeast(0)

      SupplierDashboardManufacturerBridgeDataModel(
        bridgeId = "${goodsItemId}:${relatedOrders.joinToString("-") { it.id.take(8) }}",
        goodsItemId = goodsItemId,
        goodsItemNameSnapshot = if (sampleUsesSubstitute) sampleLine.substituteGoodsItemNameSnapshot else sampleLine.goodsItemNameSnapshot,
        barcodeSnapshots = if (sampleUsesSubstitute) sampleLine.substituteGoodsItemBarcodeSnapshots else sampleLine.goodsItemBarcodeSnapshots,
        measurementUnitIdSnapshot = if (sampleUsesSubstitute) sampleLine.substituteGoodsItemMeasurementUnitIdSnapshot else sampleLine.goodsItemMeasurementUnitIdSnapshot,
        requestedQuantityTotal = requestedQuantityTotal,
        acceptedQuantityTotal = acceptedQuantityTotal,
        missingQuantityTotal = missingQuantityTotal,
        openOrderCount = openOrderCount,
        confirmedOrderCount = confirmedOrderCount,
        storeCount = relatedOrders.map { it.storeId }.filter { it.isNotBlank() }.distinct().size,
        priceBookRowCount = priceBookRowsForItem.size,
        responseCoveragePercent = if (itemLines.isEmpty()) 0 else (((responseCoveredLineCount * 100.0) / itemLines.size) + 0.5).toInt().coerceIn(0, 100),
        estimatedAcceptedAmount = amountValue.takeIf { it > 0.0 }?.let { amount ->
          PriceDataModel(amount.toStockMoneyText(), amountCurrency, supplierIds.firstOrNull()?.toString().orEmpty())
        },
        earliestDueAtMillis = earliestDueAtMillis,
        latestActivityMillis = latestActivityMillis,
        priorityScore = priorityScore,
        suggestedAction = suggestedAction
      )
    }
    .sortedWith(
      compareByDescending<SupplierDashboardManufacturerBridgeDataModel> { it.priorityScore }
        .thenBy { it.earliestDueAtMillis ?: Long.MAX_VALUE }
        .thenByDescending { it.latestActivityMillis }
    )
    .take(10)

  val dashboardProfiles = supplierProfiles.map { supplier ->
    val profileOrders = orders.filter { it.supplierId == supplier.id }
    val profileOrderIds = profileOrders.map { it.id }.toSet()
    val profileLines = lines.filter { it.orderId in profileOrderIds }
    val profilePriceBookGoodsItemIds = supplierPriceRows
      .filter { it.supplierId == supplier.id }
      .map { it.goodsItemId }
      .filter { it.isNotBlank() }
    SupplierDashboardProfileDataModel(
      supplierId = supplier.id,
      name = supplier.name,
      phoneNumbers = supplier.phoneNumbers.orEmpty(),
      emails = supplier.emails.orEmpty(),
      orderCount = profileOrders.size,
      openOrderCount = profileOrders.count { !it.status.isClosedForSupplierDashboard() },
      actionRequiredOrderCount = profileOrders.count {
        it.status == SupplierOrderStatusDataModel.Sent ||
           it.status == SupplierOrderStatusDataModel.SeenBySupplier ||
           it.status == SupplierOrderStatusDataModel.IssueReported
      },
      catalogSkuCount = (profileLines.map { it.goodsItemId } + profilePriceBookGoodsItemIds).filter { it.isNotBlank() }.distinct().size,
      partnerCount = (profileOrders.map { it.storeId } + supplierPriceRows.filter { it.supplierId == supplier.id }.map { it.storeId }).filter { it.isNotBlank() }.distinct().size,
      latestActivityMillis = profileOrders
        .map { it.updatedAtMillis.takeIf { value -> value > 0L } ?: it.orderedAtMillis }
        .maxOrNull() ?: supplier.addedAt
    )
  }.sortedWith(
    compareByDescending<SupplierDashboardProfileDataModel> { it.openOrderCount }
      .thenByDescending { it.actionRequiredOrderCount }
      .thenByDescending { it.latestActivityMillis }
  )

  return SupplierModeDashboardDataModel(
    supplierIds = supplierIds.map { it.toString() },
    supplierProfiles = dashboardProfiles,
    generatedAtMillis = now,
    orderCount = orders.size,
    openOrderCount = orders.count { !it.status.isClosedForSupplierDashboard() },
    actionRequiredOrderCount = orders.count {
      it.status == SupplierOrderStatusDataModel.Sent ||
         it.status == SupplierOrderStatusDataModel.SeenBySupplier ||
         it.status == SupplierOrderStatusDataModel.IssueReported
    },
    packedOrderCount = orders.count { it.status == SupplierOrderStatusDataModel.Packed },
    inDeliveryOrderCount = orders.count { it.status == SupplierOrderStatusDataModel.InDelivery },
    deliveredOrderCount = orders.count { it.status == SupplierOrderStatusDataModel.Delivered || it.status == SupplierOrderStatusDataModel.PartiallyDelivered },
    issueOrderCount = orders.count { it.status == SupplierOrderStatusDataModel.IssueReported || it.status == SupplierOrderStatusDataModel.Cancelled },
    lineCount = lines.size,
    catalogSkuCount = (lines.map { it.goodsItemId } + priceBookGoodsItemIds).filter { it.isNotBlank() }.distinct().size,
    partnerCount = (orders.map { it.storeId } + supplierPriceRows.map { it.storeId }).filter { it.isNotBlank() }.distinct().size,
    activeContractCount = contracts.count { it.status == SUPPLIER_CONTRACT_STATUS_ACTIVE },
    pendingContractCount = contracts.count { it.status == SUPPLIER_CONTRACT_STATUS_PENDING_SUPPLIER || it.status == SUPPLIER_CONTRACT_STATUS_PENDING_STORE },
    statusBuckets = statusBuckets,
    demandHighlights = demandHighlights,
    partnerHighlights = partnerHighlights,
    actionQueue = actionQueue,
    deliveryBuckets = deliveryBuckets,
    readiness = readiness,
    manufacturerBridge = manufacturerBridge
  )
}


private fun normalizeSupplierContractSide(side: String): String = when (side.trim().lowercase()) {
  SUPPLIER_CONTRACT_SIDE_STORE -> SUPPLIER_CONTRACT_SIDE_STORE
  else -> SUPPLIER_CONTRACT_SIDE_SUPPLIER
}

private fun normalizeSupplierContractScope(scope: String, goodsItemIds: List<String>): String = when (scope.trim().lowercase()) {
  SUPPLIER_CONTRACT_SCOPE_GOODS_ITEM -> SUPPLIER_CONTRACT_SCOPE_GOODS_ITEM
  SUPPLIER_CONTRACT_SCOPE_GOODS_GROUP -> SUPPLIER_CONTRACT_SCOPE_GOODS_GROUP
  else -> if (goodsItemIds.isNotEmpty()) SUPPLIER_CONTRACT_SCOPE_GOODS_GROUP else SUPPLIER_CONTRACT_SCOPE_PARTNERSHIP
}

private fun normalizeSupplierContractStatus(status: String): String = when (status.trim().lowercase()) {
  SUPPLIER_CONTRACT_STATUS_PENDING_SUPPLIER -> SUPPLIER_CONTRACT_STATUS_PENDING_SUPPLIER
  SUPPLIER_CONTRACT_STATUS_ACTIVE -> SUPPLIER_CONTRACT_STATUS_ACTIVE
  SUPPLIER_CONTRACT_STATUS_DECLINED -> SUPPLIER_CONTRACT_STATUS_DECLINED
  SUPPLIER_CONTRACT_STATUS_ARCHIVED -> SUPPLIER_CONTRACT_STATUS_ARCHIVED
  else -> SUPPLIER_CONTRACT_STATUS_PENDING_STORE
}

private fun List<LocalizedStringDataModel>.cleanContractLocalized(defaultValue: String = ""): List<LocalizedStringDataModel> =
  map { it.copy(language = it.language.trim().ifBlank { "main" }, value = it.value.trim()) }
    .filter { it.value.isNotBlank() }
    .distinctBy { it.language }
    .ifEmpty { defaultValue.takeIf { it.isNotBlank() }?.let { listOf(LocalizedStringDataModel("main", it)) } ?: emptyList() }

private fun SupplierContractPriceTermDataModel.cleanContractPriceTerm(validGoodsItemIds: Set<String>): SupplierContractPriceTermDataModel? {
  val cleanGoodsItemId = goodsItemId.trim().takeIf { it.isNotBlank() }
  if (cleanGoodsItemId != null && cleanGoodsItemId !in validGoodsItemIds) return null
  return copy(
    id = id.trim().takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
    goodsItemId = cleanGoodsItemId.orEmpty(),
    goodsItemNameSnapshot = goodsItemNameSnapshot.cleanContractLocalized(),
    scheduleText = scheduleText.cleanContractLocalized(),
    note = note.cleanContractLocalized(),
    isActive = isActive
  )
}

private fun ResultRow.toSupplierPartnershipContractDataModel(): SupplierPartnershipContractDataModel = SupplierPartnershipContractDataModel(
  id = this[SupplierPartnershipContracts.id].toString(),
  storeId = this[SupplierPartnershipContracts.storeId].toString(),
  supplierId = this[SupplierPartnershipContracts.supplierId].toString(),
  authorUserId = this[SupplierPartnershipContracts.authorUserId].toString(),
  lastEditorUserId = this[SupplierPartnershipContracts.lastEditorUserId].toString(),
  authorSide = this[SupplierPartnershipContracts.authorSide],
  scopeType = this[SupplierPartnershipContracts.scopeType],
  goodsItemIds = this[SupplierPartnershipContracts.goodsItemIds],
  title = this[SupplierPartnershipContracts.title],
  summary = this[SupplierPartnershipContracts.summary],
  conditions = this[SupplierPartnershipContracts.conditions],
  customTerms = this[SupplierPartnershipContracts.customTerms],
  deliverySchedule = this[SupplierPartnershipContracts.deliverySchedule],
  paymentSchedule = this[SupplierPartnershipContracts.paymentSchedule],
  priceTerms = this[SupplierPartnershipContracts.priceTerms],
  status = this[SupplierPartnershipContracts.status],
  revision = this[SupplierPartnershipContracts.revision],
  supplierAcceptedAtMillis = this[SupplierPartnershipContracts.supplierAcceptedAtMillis],
  storeAcceptedAtMillis = this[SupplierPartnershipContracts.storeAcceptedAtMillis],
  supplierAcceptedByUserId = this[SupplierPartnershipContracts.supplierAcceptedByUserId]?.toString(),
  storeAcceptedByUserId = this[SupplierPartnershipContracts.storeAcceptedByUserId]?.toString(),
  declinedAtMillis = this[SupplierPartnershipContracts.declinedAtMillis],
  declinedByUserId = this[SupplierPartnershipContracts.declinedByUserId]?.toString(),
  createdAtMillis = this[SupplierPartnershipContracts.createdAtMillis],
  updatedAtMillis = this[SupplierPartnershipContracts.updatedAtMillis],
  isActive = this[SupplierPartnershipContracts.isActive]
)

private fun List<SupplierPartnershipContractDataModel>.withSupplierContractSnapshotsInsideTransaction(): List<SupplierPartnershipContractDataModel> {
  if (isEmpty()) return this
  val storeIds = mapNotNull { runCatching { UUID.fromString(it.storeId) }.getOrNull() }.distinct()
  val supplierIds = mapNotNull { runCatching { UUID.fromString(it.supplierId) }.getOrNull() }.distinct()
  val storeRowsById = if (storeIds.isEmpty()) emptyMap<String, ResultRow>() else Stores.selectAll().where { Stores.id inList storeIds }.associateBy { it[Stores.id].toString() }
  val supplierRowsById = if (supplierIds.isEmpty()) emptyMap<String, ResultRow>() else Suppliers.selectAll().where { Suppliers.id inList supplierIds }.associateBy { it[Suppliers.id].toString() }
  return map { contract ->
    val storeRow = storeRowsById[contract.storeId]
    val supplierRow = supplierRowsById[contract.supplierId]
    contract.copy(
      storeNameSnapshot = storeRow?.get(Stores.name).orEmpty(),
      storePublicIdSnapshot = storeRow?.get(Stores.publicId).orEmpty(),
      supplierNameSnapshot = supplierRow?.let { row ->
        runCatching { jsonBase.decodeFromString<List<LocalizedStringDataModel>>(row[Suppliers.name]) }
          .getOrDefault(listOf(LocalizedStringDataModel("main", row[Suppliers.name])))
      }.orEmpty()
    )
  }
}

private fun SupplierPartnershipContractDataModel.cleanForContractStorageInsideTransaction(
  userId: UUID,
  storeId: UUID,
  supplierId: UUID,
  actorSide: String,
  now: Long,
  existing: ResultRow? = null
): SupplierPartnershipContractDataModel {
  val cleanActorSide = normalizeSupplierContractSide(actorSide)
  val validGoodsIds = StockItems
    .select(StockItems.id)
    .where { (StockItems.storeId eq storeId) and (StockItems.isActive eq true) }
    .map { it[StockItems.id].toString() }
    .toSet()
  val cleanGoodsItemIds = goodsItemIds.map { it.trim() }.filter { it in validGoodsIds }.distinct()
  val cleanScope = normalizeSupplierContractScope(scopeType, cleanGoodsItemIds)
  val baseTitle = when (cleanScope) {
    SUPPLIER_CONTRACT_SCOPE_GOODS_ITEM -> "Goods item contract"
    SUPPLIER_CONTRACT_SCOPE_GOODS_GROUP -> "Goods group contract"
    else -> "Partnership contract"
  }
  val cleanPriceTerms = priceTerms.mapNotNull { it.cleanContractPriceTerm(validGoodsIds) }
    .distinctBy { it.id.ifBlank { it.goodsItemId } }
  val supplierAcceptedAt = if (cleanActorSide == SUPPLIER_CONTRACT_SIDE_SUPPLIER) now else null
  val storeAcceptedAt = if (cleanActorSide == SUPPLIER_CONTRACT_SIDE_STORE) now else null
  val nextStatus = if (cleanActorSide == SUPPLIER_CONTRACT_SIDE_SUPPLIER) {
    SUPPLIER_CONTRACT_STATUS_PENDING_STORE
  } else {
    SUPPLIER_CONTRACT_STATUS_PENDING_SUPPLIER
  }

  return copy(
    id = id.trim(),
    storeId = storeId.toString(),
    supplierId = supplierId.toString(),
    authorUserId = existing?.get(SupplierPartnershipContracts.authorUserId)?.toString() ?: userId.toString(),
    lastEditorUserId = userId.toString(),
    authorSide = cleanActorSide,
    scopeType = cleanScope,
    goodsItemIds = cleanGoodsItemIds,
    title = title.cleanContractLocalized(baseTitle),
    summary = summary.cleanContractLocalized(),
    conditions = conditions.map { it.trim() }.filter { it.isNotBlank() }.distinct(),
    customTerms = customTerms.cleanContractLocalized(),
    deliverySchedule = deliverySchedule.cleanContractLocalized(),
    paymentSchedule = paymentSchedule.cleanContractLocalized(),
    priceTerms = cleanPriceTerms,
    status = nextStatus,
    revision = (existing?.get(SupplierPartnershipContracts.revision) ?: 0) + 1,
    supplierAcceptedAtMillis = supplierAcceptedAt,
    storeAcceptedAtMillis = storeAcceptedAt,
    supplierAcceptedByUserId = if (cleanActorSide == SUPPLIER_CONTRACT_SIDE_SUPPLIER) userId.toString() else null,
    storeAcceptedByUserId = if (cleanActorSide == SUPPLIER_CONTRACT_SIDE_STORE) userId.toString() else null,
    declinedAtMillis = null,
    declinedByUserId = null,
    createdAtMillis = existing?.get(SupplierPartnershipContracts.createdAtMillis) ?: now,
    updatedAtMillis = now,
    isActive = true
  )
}

private fun InsertStatement<Number>.setSupplierContractColumns(contractId: UUID, clean: SupplierPartnershipContractDataModel) {
  this[SupplierPartnershipContracts.id] = contractId
  this[SupplierPartnershipContracts.storeId] = UUID.fromString(clean.storeId)
  this[SupplierPartnershipContracts.supplierId] = UUID.fromString(clean.supplierId)
  this[SupplierPartnershipContracts.authorUserId] = UUID.fromString(clean.authorUserId)
  this[SupplierPartnershipContracts.lastEditorUserId] = UUID.fromString(clean.lastEditorUserId)
  this[SupplierPartnershipContracts.authorSide] = clean.authorSide
  this[SupplierPartnershipContracts.scopeType] = clean.scopeType
  this[SupplierPartnershipContracts.goodsItemIds] = clean.goodsItemIds
  this[SupplierPartnershipContracts.title] = clean.title
  this[SupplierPartnershipContracts.summary] = clean.summary
  this[SupplierPartnershipContracts.conditions] = clean.conditions
  this[SupplierPartnershipContracts.customTerms] = clean.customTerms
  this[SupplierPartnershipContracts.deliverySchedule] = clean.deliverySchedule
  this[SupplierPartnershipContracts.paymentSchedule] = clean.paymentSchedule
  this[SupplierPartnershipContracts.priceTerms] = clean.priceTerms
  this[SupplierPartnershipContracts.status] = clean.status
  this[SupplierPartnershipContracts.revision] = clean.revision
  this[SupplierPartnershipContracts.supplierAcceptedAtMillis] = clean.supplierAcceptedAtMillis
  this[SupplierPartnershipContracts.storeAcceptedAtMillis] = clean.storeAcceptedAtMillis
  this[SupplierPartnershipContracts.supplierAcceptedByUserId] = clean.supplierAcceptedByUserId?.let { runCatching { UUID.fromString(it) }.getOrNull() }
  this[SupplierPartnershipContracts.storeAcceptedByUserId] = clean.storeAcceptedByUserId?.let { runCatching { UUID.fromString(it) }.getOrNull() }
  this[SupplierPartnershipContracts.declinedAtMillis] = clean.declinedAtMillis
  this[SupplierPartnershipContracts.declinedByUserId] = clean.declinedByUserId?.let { runCatching { UUID.fromString(it) }.getOrNull() }
  this[SupplierPartnershipContracts.createdAtMillis] = clean.createdAtMillis
  this[SupplierPartnershipContracts.updatedAtMillis] = clean.updatedAtMillis
  this[SupplierPartnershipContracts.isActive] = clean.isActive
}

private fun UpdateBuilder<*>.setSupplierContractUpdateColumns(clean: SupplierPartnershipContractDataModel) {
  this[SupplierPartnershipContracts.lastEditorUserId] = UUID.fromString(clean.lastEditorUserId)
  this[SupplierPartnershipContracts.authorSide] = clean.authorSide
  this[SupplierPartnershipContracts.scopeType] = clean.scopeType
  this[SupplierPartnershipContracts.goodsItemIds] = clean.goodsItemIds
  this[SupplierPartnershipContracts.title] = clean.title
  this[SupplierPartnershipContracts.summary] = clean.summary
  this[SupplierPartnershipContracts.conditions] = clean.conditions
  this[SupplierPartnershipContracts.customTerms] = clean.customTerms
  this[SupplierPartnershipContracts.deliverySchedule] = clean.deliverySchedule
  this[SupplierPartnershipContracts.paymentSchedule] = clean.paymentSchedule
  this[SupplierPartnershipContracts.priceTerms] = clean.priceTerms
  this[SupplierPartnershipContracts.status] = clean.status
  this[SupplierPartnershipContracts.revision] = clean.revision
  this[SupplierPartnershipContracts.supplierAcceptedAtMillis] = clean.supplierAcceptedAtMillis
  this[SupplierPartnershipContracts.storeAcceptedAtMillis] = clean.storeAcceptedAtMillis
  this[SupplierPartnershipContracts.supplierAcceptedByUserId] = clean.supplierAcceptedByUserId?.let { runCatching { UUID.fromString(it) }.getOrNull() }
  this[SupplierPartnershipContracts.storeAcceptedByUserId] = clean.storeAcceptedByUserId?.let { runCatching { UUID.fromString(it) }.getOrNull() }
  this[SupplierPartnershipContracts.declinedAtMillis] = clean.declinedAtMillis
  this[SupplierPartnershipContracts.declinedByUserId] = clean.declinedByUserId?.let { runCatching { UUID.fromString(it) }.getOrNull() }
  this[SupplierPartnershipContracts.updatedAtMillis] = clean.updatedAtMillis
  this[SupplierPartnershipContracts.isActive] = clean.isActive
}

private fun supplierContractGuardFailureMessage(): List<LocalizedStringDataModel> = simpleMessage(
  main = "Pending supplier/store contract must be accepted before supply can continue",
  ru = "Ожидающий договор магазина и поставщика должен быть принят до продолжения поставки",
  kk = "Жеткізу жалғасуы үшін дүкен мен жеткізушінің күтіп тұрған келісімі қабылдануы керек"
)

private fun supplierContractBlocksStoreSupplyInsideTransaction(
  storeId: UUID,
  supplierId: UUID,
  goodsItemIds: List<UUID>
): SupplierPartnershipContractDataModel? {
  if (goodsItemIds.isEmpty()) return null
  val goodsIdStrings = goodsItemIds.map { it.toString() }.toSet()
  return SupplierPartnershipContracts
    .selectAll()
    .where {
      (SupplierPartnershipContracts.storeId eq storeId) and
         (SupplierPartnershipContracts.supplierId eq supplierId) and
         (SupplierPartnershipContracts.status neq SUPPLIER_CONTRACT_STATUS_ARCHIVED) and
         (SupplierPartnershipContracts.isActive eq true)
    }
    .map { it.toSupplierPartnershipContractDataModel() }
    .firstOrNull { contract ->
      val scopeMatches = contract.scopeType == SUPPLIER_CONTRACT_SCOPE_PARTNERSHIP ||
         contract.goodsItemIds.isEmpty() ||
         contract.goodsItemIds.any { it in goodsIdStrings }
      scopeMatches && contract.status != SUPPLIER_CONTRACT_STATUS_ACTIVE
    }
}

private fun InsertStatement<Number>.setSupplierOrderColumns(
  orderId: UUID,
  clean: SupplierOrderDataModel,
  userId: UUID,
  storeId: UUID,
  supplierId: UUID
) {
  this[SupplierOrders.id] = orderId
  this[SupplierOrders.userId] = userId
  this[SupplierOrders.storeId] = storeId
  this[SupplierOrders.supplierId] = supplierId
  this[SupplierOrders.amount] = clean.amount
  this[SupplierOrders.orderedAtMillis] = clean.orderedAtMillis
  this[SupplierOrders.desiredDeliveryTimeMillis] = clean.desiredDeliveryTimeMillis
  this[SupplierOrders.confirmedDeliveryTimeMillis] = clean.confirmedDeliveryTimeMillis
  this[SupplierOrders.deliveredAtMillis] = clean.deliveredAtMillis
  this[SupplierOrders.storeAddress] = clean.storeAddress
  this[SupplierOrders.additionalNotes] = clean.additionalNotes
  this[SupplierOrders.additionalNotesLocalized] = clean.additionalNotesLocalized
  this[SupplierOrders.supplierComment] = clean.supplierComment
  this[SupplierOrders.supplierCommentLocalized] = clean.supplierCommentLocalized
  this[SupplierOrders.paymentTerms] = clean.paymentTerms
  this[SupplierOrders.externalReference] = clean.externalReference
  this[SupplierOrders.storeContactUserId] = clean.storeContactUserId?.let { runCatching { UUID.fromString(it) }.getOrNull() }
  this[SupplierOrders.status] = clean.status.name
  this[SupplierOrders.createdAtMillis] = clean.createdAtMillis
  this[SupplierOrders.updatedAtMillis] = clean.updatedAtMillis
  this[SupplierOrders.isActive] = clean.isActive
}

private fun UpdateBuilder<*>.setSupplierOrderUpdateColumns(clean: SupplierOrderDataModel) {
  this[SupplierOrders.amount] = clean.amount
  this[SupplierOrders.desiredDeliveryTimeMillis] = clean.desiredDeliveryTimeMillis
  this[SupplierOrders.confirmedDeliveryTimeMillis] = clean.confirmedDeliveryTimeMillis
  this[SupplierOrders.deliveredAtMillis] = clean.deliveredAtMillis
  this[SupplierOrders.storeAddress] = clean.storeAddress
  this[SupplierOrders.additionalNotes] = clean.additionalNotes
  this[SupplierOrders.additionalNotesLocalized] = clean.additionalNotesLocalized
  this[SupplierOrders.supplierComment] = clean.supplierComment
  this[SupplierOrders.supplierCommentLocalized] = clean.supplierCommentLocalized
  this[SupplierOrders.paymentTerms] = clean.paymentTerms
  this[SupplierOrders.externalReference] = clean.externalReference
  this[SupplierOrders.storeContactUserId] = clean.storeContactUserId?.let { runCatching { UUID.fromString(it) }.getOrNull() }
  this[SupplierOrders.status] = clean.status.name
  this[SupplierOrders.updatedAtMillis] = clean.updatedAtMillis
  this[SupplierOrders.isActive] = clean.isActive
}

private fun InsertStatement<Number>.setSupplierOrderLineColumns(
  lineId: UUID,
  clean: SupplierOrderLineDataModel,
  orderId: UUID,
  goodsItemId: UUID
) {
  this[SupplierOrderLines.id] = lineId
  this[SupplierOrderLines.orderId] = orderId
  this[SupplierOrderLines.goodsItemId] = goodsItemId
  this[SupplierOrderLines.requestedQuantity] = clean.requestedQuantity
  this[SupplierOrderLines.expectedSupplyPrice] = clean.expectedSupplyPrice
  this[SupplierOrderLines.desiredExpirationDateMillis] = clean.desiredExpirationDateMillis
  this[SupplierOrderLines.additionalNotes] = clean.additionalNotes
  this[SupplierOrderLines.additionalNotesLocalized] = clean.additionalNotesLocalized
  this[SupplierOrderLines.supplierComment] = clean.supplierComment
  this[SupplierOrderLines.supplierCommentLocalized] = clean.supplierCommentLocalized
  this[SupplierOrderLines.supplierAcceptedQuantity] = clean.supplierAcceptedQuantity
  this[SupplierOrderLines.supplierOfferedSupplyPrice] = clean.supplierOfferedSupplyPrice
  this[SupplierOrderLines.substituteGoodsItemId] = clean.substituteGoodsItemId?.let { runCatching { UUID.fromString(it) }.getOrNull() }
  this[SupplierOrderLines.deliveredBatchIds] = clean.deliveredBatchIds
  this[SupplierOrderLines.isActive] = clean.isActive
}

private fun UpdateBuilder<*>.setSupplierOrderLineUpdateColumns(clean: SupplierOrderLineDataModel) {
  this[SupplierOrderLines.requestedQuantity] = clean.requestedQuantity
  this[SupplierOrderLines.expectedSupplyPrice] = clean.expectedSupplyPrice
  this[SupplierOrderLines.desiredExpirationDateMillis] = clean.desiredExpirationDateMillis
  this[SupplierOrderLines.additionalNotes] = clean.additionalNotes
  this[SupplierOrderLines.additionalNotesLocalized] = clean.additionalNotesLocalized
  this[SupplierOrderLines.supplierComment] = clean.supplierComment
  this[SupplierOrderLines.supplierCommentLocalized] = clean.supplierCommentLocalized
  this[SupplierOrderLines.supplierAcceptedQuantity] = clean.supplierAcceptedQuantity
  this[SupplierOrderLines.supplierOfferedSupplyPrice] = clean.supplierOfferedSupplyPrice
  this[SupplierOrderLines.substituteGoodsItemId] = clean.substituteGoodsItemId?.let { runCatching { UUID.fromString(it) }.getOrNull() }
  this[SupplierOrderLines.isActive] = clean.isActive
}

private fun SupplierDataModel.cleanedForStorage(ownerUserId: UUID? = null, existingUserIds: List<String> = emptyList()): SupplierDataModel {
  val cleanUserIds = (existingUserIds + userIds + listOfNotNull(ownerUserId?.toString()))
    .map { it.trim() }
    .filter { it.isNotBlank() }
    .distinct()
  return copy(
    userIds = cleanUserIds,
    name = name.map { it.copy(value = it.value.trim()) }.filter { it.value.isNotBlank() }.ifEmpty { listOf(LocalizedStringDataModel("main", "Supplier")) },
    phoneNumbers = phoneNumbers.orEmpty().map { it.trim() }.filter { it.isNotBlank() }.distinct(),
    emails = emails.orEmpty().map { it.trim().lowercase() }.filter { it.isNotBlank() }.distinct(),
    categoryIds = categoryIds.map { it.trim() }.filter { it.isNotBlank() }.distinct(),
    typeIds = typeIds.orEmpty().map { it.trim() }.filter { it.isNotBlank() }.distinct()
  )
}

private fun ResultRow.toGoodsBatchDataModel(): GoodsBatchDataModel {
  return GoodsBatchDataModel(
    id = this[StockBatchesV2.id].toString(),
    goodsItemId = this[StockBatchesV2.goodsItemId].toString(),
    userId = this[StockBatchesV2.userId].toString(),
    storeId = this[StockBatchesV2.storeId].toString(),

    supplierId = this[StockBatchesV2.supplierId]?.toString(),
    supplierOrderId = this[StockBatchesV2.supplierOrderId]?.toString(),

    quantity = this[StockBatchesV2.quantity],

    supplyPrice = this[StockBatchesV2.supplyPrice],
    salePriceOverride = this[StockBatchesV2.salePriceOverride],
    returnPriceOverride = this[StockBatchesV2.returnPriceOverride],
    wholesalePriceOverride = this[StockBatchesV2.wholesalePriceOverride],

    deliveredAtMillis = this[StockBatchesV2.deliveredAtMillis],
    manufacturedAtMillis = this[StockBatchesV2.manufacturedAtMillis],
    expirationDateMillis = this[StockBatchesV2.expirationDateMillis],

    discounts = this[StockBatchesV2.discounts],
    promotions = this[StockBatchesV2.promotions],

    shelfPosition = this[StockBatchesV2.shelfPosition],
    shelfPriority = this[StockBatchesV2.shelfPriority],

    status = runCatching {
      StockBatchStatusDataModel.valueOf(this[StockBatchesV2.status])
    }.getOrDefault(StockBatchStatusDataModel.Delivered),

    additionalNotes = this[StockBatchesV2.additionalNotes],
    additionalNotesLocalized = this[StockBatchesV2.additionalNotesLocalized],

    createdAtMillis = this[StockBatchesV2.createdAtMillis],
    updatedAtMillis = this[StockBatchesV2.updatedAtMillis],
    createdByUserId = this[StockBatchesV2.createdByUserId]?.toString(),

    isActive = this[StockBatchesV2.isActive]
  )
}

private fun ResultRow.toStockBatchMovementDataModel(): StockBatchMovementDataModel {
  fun userDisplayName(userId: UUID?): String {
    if (userId == null) return ""
    val userRow = Users
      .select(Users.firstName, Users.lastName)
      .where { Users.id eq userId }
      .singleOrNull()

    return userRow?.let { row ->
      "${row[Users.firstName]} ${row[Users.lastName]}".trim()
    }.orEmpty()
  }

  val movementUserId = this[StockBatchMovements.userId]
  val acceptedByUserId = this[StockBatchMovements.acceptedByUserId]

  return StockBatchMovementDataModel(
    id = this[StockBatchMovements.id].toString(),
    rootStoreId = this[StockBatchMovements.rootStoreId].toString(),
    sourceStoreId = this[StockBatchMovements.sourceStoreId].toString(),
    destinationStoreId = this[StockBatchMovements.destinationStoreId].toString(),
    sourceGoodsItemId = this[StockBatchMovements.sourceGoodsItemId].toString(),
    destinationGoodsItemId = this[StockBatchMovements.destinationGoodsItemId].toString(),
    sourceBatchId = this[StockBatchMovements.sourceBatchId].toString(),
    destinationBatchId = this[StockBatchMovements.destinationBatchId].toString(),
    quantity = this[StockBatchMovements.quantity],
    movedByUserId = movementUserId.toString(),
    movedByName = userDisplayName(movementUserId),
    movedAtMillis = this[StockBatchMovements.movedAtMillis],
    status = runCatching { StockBatchMovementStatusDataModel.valueOf(this[StockBatchMovements.status]) }
      .getOrDefault(StockBatchMovementStatusDataModel.Accepted),
    acceptedByUserId = acceptedByUserId?.toString(),
    acceptedByName = userDisplayName(acceptedByUserId),
    acceptedAtMillis = this[StockBatchMovements.acceptedAtMillis],
    decisionNote = this[StockBatchMovements.decisionNote],
    note = this[StockBatchMovements.note]
  )
}

private fun storeGroupIdsInsideTransaction(rootStoreId: UUID): List<UUID> {
  return Stores
    .select(Stores.id)
    .where {
      (Stores.id eq rootStoreId) or (Stores.parentStoreId eq (rootStoreId as UUID?))
    }
    .map { it[Stores.id] }
}

private fun stockItemIdentityTokens(row: ResultRow): Set<String> {
  val rowStoreId = row[StockItems.storeId].toString()
  val barcodeTokens = row.stockBarcodeModels()
    .flatMap { model ->
      val cleanType = model.type.normalizedGoodsItemBarcodeType(model.value)
      val prefix = if (cleanType == GOODS_ITEM_BARCODE_TYPE_INTERNAL) {
        "internal:${model.storeId?.takeIf { it.isNotBlank() } ?: rowStoreId}:"
      } else {
        "standard:"
      }
      (model.value.toStoredGoodsItemBarcodeCandidates() + listOf(model.value))
        .map { candidate -> prefix + candidate.normalizedBarcodeToken() }
    }
    .filter { it.substringAfterLast(':').isNotBlank() }

  if (barcodeTokens.isNotEmpty()) return barcodeTokens.toSet()

  return row[StockItems.name]
    .map { it.value.trim().lowercase() }
    .filter { it.isNotBlank() }
    .toSet()
}

private fun stockItemsMatchByIdentity(source: ResultRow, candidate: ResultRow): Boolean {
  val sourceTokens = stockItemIdentityTokens(source)
  val candidateTokens = stockItemIdentityTokens(candidate)
  if (sourceTokens.isEmpty() || candidateTokens.isEmpty()) return false

  if (sourceTokens.any { it in candidateTokens }) return true

  val sourceStoreId = source[StockItems.storeId]
  val candidateStoreId = candidate[StockItems.storeId]
  return source.stockBarcodeModels().any { sourceBarcode ->
    candidate.stockBarcodeModels().any { candidateBarcode ->
      val sourceType = sourceBarcode.type.normalizedGoodsItemBarcodeType(sourceBarcode.value)
      val candidateType = candidateBarcode.type.normalizedGoodsItemBarcodeType(candidateBarcode.value)
      if (sourceType != candidateType) {
        false
      } else if (sourceType == GOODS_ITEM_BARCODE_TYPE_INTERNAL && sourceStoreId != candidateStoreId) {
        false
      } else {
        storedBarcodeMatchesScannedTransactionBarcode(sourceBarcode.value, candidateBarcode.value) ||
           storedBarcodeMatchesScannedTransactionBarcode(candidateBarcode.value, sourceBarcode.value)
      }
    }
  }
}

private fun matchingStockItemRowsForStoreGroupInsideTransaction(
  rootStoreId: UUID,
  sourceItemRow: ResultRow
): List<ResultRow> {
  val storeIds = storeGroupIdsInsideTransaction(rootStoreId)
  if (storeIds.isEmpty()) return emptyList()

  return StockItems
    .selectAll()
    .where {
      (StockItems.storeId inList storeIds) and (StockItems.isActive eq true)
    }
    .filter { row -> stockItemsMatchByIdentity(sourceItemRow, row) }
}

private fun activePhysicalBatchRowsForGoodsItemInsideTransaction(
  storeId: UUID,
  goodsItemId: UUID
): List<ResultRow> {
  return StockBatchesV2
    .selectAll()
    .where {
      (StockBatchesV2.storeId eq storeId) and
         (StockBatchesV2.goodsItemId eq goodsItemId) and
         (StockBatchesV2.isActive eq true)
    }
    .filter { row ->
      row[StockBatchesV2.quantity].total > 0.0 &&
         row[StockBatchesV2.status] !in setOf(
        StockBatchStatusDataModel.Ordered.name,
        StockBatchStatusDataModel.InTransit.name,
        StockBatchStatusDataModel.SoldOut.name,
        StockBatchStatusDataModel.WrittenOff.name,
        StockBatchStatusDataModel.Deleted.name
      )
    }
}

private fun buildStockBranchAvailabilityInsideTransaction(
  currentStoreId: UUID,
  sourceGoodsItemId: UUID
): StockItemBranchAvailabilityDataModel? {
  val sourceItemRow = StockItems
    .selectAll()
    .where {
      (StockItems.id eq sourceGoodsItemId) and
         (StockItems.isActive eq true)
    }
    .singleOrNull()
    ?: return null

  val rootStoreId = rootStoreIdForAccessInsideTransaction(sourceItemRow[StockItems.storeId])
  val storeIds = storeGroupIdsInsideTransaction(rootStoreId)
  val storesById = Stores
    .selectAll()
    .where { Stores.id inList storeIds }
    .associateBy { it[Stores.id] }

  val matchingItemsByStore = matchingStockItemRowsForStoreGroupInsideTransaction(rootStoreId, sourceItemRow)
    .groupBy { it[StockItems.storeId] }

  val locations = storeIds
    .mapNotNull { storeId ->
      val storeRow = storesById[storeId] ?: return@mapNotNull null
      val itemRows = matchingItemsByStore[storeId].orEmpty()
      val batches = itemRows.flatMap { itemRow ->
        activePhysicalBatchRowsForGoodsItemInsideTransaction(storeId, itemRow[StockItems.id])
      }.sortedWith(compareBy<ResultRow> { it[StockBatchesV2.expirationDateMillis] ?: Long.MAX_VALUE }.thenBy { it[StockBatchesV2.createdAtMillis] })

      val firstQuantity = batches.firstOrNull()?.get(StockBatchesV2.quantity)
        ?: itemRows.firstOrNull()?.let { itemRow -> defaultServerQuantityForGoodsItem(itemRow[StockItems.measurementUnitId], 0.0) }
        ?: defaultServerQuantityForGoodsItem(sourceItemRow[StockItems.measurementUnitId], 0.0)

      val total = batches.sumOf { it[StockBatchesV2.quantity].total }
      val quantity = firstQuantity.copy(total = total)

      StockBranchQuantityDataModel(
        storeId = storeId.toString(),
        publicId = storeRow[Stores.publicId],
        parentStoreId = storeRow[Stores.parentStoreId]?.toString(),
        name = storeRow[Stores.name],
        address = storeRow[Stores.address],
        isCurrentStore = storeId == currentStoreId,
        isParentStore = storeRow[Stores.parentStoreId] == null,
        goodsItemId = itemRows.firstOrNull()?.get(StockItems.id)?.toString(),
        totalQuantity = quantity,
        batchCount = batches.size,
        batches = batches.map { it.toGoodsBatchDataModel() }
      )
    }

  val matchingItemIds = matchingItemsByStore.values.flatten().map { it[StockItems.id] }.distinct()
  val movements = if (matchingItemIds.isEmpty()) {
    emptyList()
  } else {
    StockBatchMovements
      .selectAll()
      .where {
        (StockBatchMovements.rootStoreId eq rootStoreId) and
           ((StockBatchMovements.sourceGoodsItemId inList matchingItemIds) or
              (StockBatchMovements.destinationGoodsItemId inList matchingItemIds))
      }
      .orderBy(StockBatchMovements.movedAtMillis, SortOrder.DESC)
      .limit(30)
      .map { it.toStockBatchMovementDataModel() }
  }

  return StockItemBranchAvailabilityDataModel(
    rootStoreId = rootStoreId.toString(),
    currentStoreId = currentStoreId.toString(),
    sourceGoodsItemId = sourceGoodsItemId.toString(),
    locations = locations,
    movements = movements
  )
}

private fun findMatchingStockItemInStoreInsideTransaction(
  destinationStoreId: UUID,
  sourceItemRow: ResultRow
): ResultRow? {
  return StockItems
    .selectAll()
    .where {
      (StockItems.storeId eq destinationStoreId) and
         (StockItems.isActive eq true)
    }
    .firstOrNull { candidate -> stockItemsMatchByIdentity(sourceItemRow, candidate) }
}

private fun cloneStockItemToStoreInsideTransaction(
  sourceItemRow: ResultRow,
  destinationStoreId: UUID,
  userId: UUID,
  now: Long
): ResultRow {
  val id = UUID.randomUUID()

  StockItems.insert {
    it[StockItems.id] = id
    it[StockItems.userId] = userId
    it[StockItems.storeId] = destinationStoreId
    val cleanBarcodeModels = sourceItemRow.stockBarcodeModels().normalizedGoodsItemBarcodesForStore(destinationStoreId.toString(), sourceItemRow[StockItems.barcodes])
    it[StockItems.barcodes] = cleanBarcodeModels.cleanBarcodeStrings().ifEmpty { sourceItemRow[StockItems.barcodes].cleanBarcodes() }
    it[StockItems.barcodeModels] = cleanBarcodeModels
    it[StockItems.name] = sourceItemRow[StockItems.name]
    it[StockItems.description] = sourceItemRow[StockItems.description]
    it[StockItems.measurementUnitId] = sourceItemRow[StockItems.measurementUnitId]
    it[StockItems.categoryIds] = sourceItemRow[StockItems.categoryIds]
    it[StockItems.salePrices] = sourceItemRow[StockItems.salePrices]
    it[StockItems.returnPrices] = sourceItemRow[StockItems.returnPrices]
    it[StockItems.supplyPrices] = sourceItemRow[StockItems.supplyPrices]
    it[StockItems.wholesalePrices] = sourceItemRow[StockItems.wholesalePrices]
    it[StockItems.wholesaleMinQuantity] = sourceItemRow[StockItems.wholesaleMinQuantity]
    it[StockItems.genericExpirationPeriod] = sourceItemRow[StockItems.genericExpirationPeriod]
    it[StockItems.isQuickItem] = sourceItemRow[StockItems.isQuickItem]
    it[StockItems.imagePaths] = sourceItemRow[StockItems.imagePaths]
    it[StockItems.activeShelfBatchId] = null
    it[StockItems.promotions] = sourceItemRow[StockItems.promotions]
    it[StockItems.note] = sourceItemRow[StockItems.note]
    it[StockItems.noteLocalized] = sourceItemRow[StockItems.noteLocalized]
    it[StockItems.conditions] = sourceItemRow[StockItems.conditions]
    it[StockItems.createdAtMillis] = now
    it[StockItems.updatedAtMillis] = now
    it[StockItems.isActive] = true
  }

  return StockItems.selectAll().where { StockItems.id eq id }.single()
}


private fun stockItemParentMirrorIdentityTokens(row: ResultRow): Set<String> {
  val standardBarcodeTokens = row.stockBarcodeModels()
    .filter { model -> model.type.normalizedGoodsItemBarcodeType(model.value) == GOODS_ITEM_BARCODE_TYPE_STANDARD }
    .flatMap { model -> model.value.toStoredGoodsItemBarcodeCandidates() + listOf(model.value) }
    .map { token -> "standard:${token.normalizedBarcodeToken()}" }
    .filter { token -> token.substringAfter(':').isNotBlank() }
    .toSet()
  if (standardBarcodeTokens.isNotEmpty()) return standardBarcodeTokens

  val nameTokens = row[StockItems.name]
    .map { it.value.trim().lowercase() }
    .filter { it.isNotBlank() }
    .map { "name:$it" }
    .toSet()
  if (nameTokens.isNotEmpty()) return nameTokens

  return row.stockBarcodeModels()
    .flatMap { model -> model.value.toStoredGoodsItemBarcodeCandidates() + listOf(model.value) }
    .map { token -> "barcode:${token.normalizedBarcodeToken()}" }
    .filter { token -> token.substringAfter(':').isNotBlank() }
    .toSet()
}

private fun stockItemsMatchForParentMirror(source: ResultRow, candidate: ResultRow): Boolean {
  val sourceTokens = stockItemParentMirrorIdentityTokens(source)
  val candidateTokens = stockItemParentMirrorIdentityTokens(candidate)
  return sourceTokens.isNotEmpty() && candidateTokens.isNotEmpty() && sourceTokens.any { it in candidateTokens }
}

private fun findMatchingParentMirrorStockItemInsideTransaction(
  parentStoreId: UUID,
  sourceItemRow: ResultRow
): ResultRow? {
  return StockItems
    .selectAll()
    .where {
      (StockItems.storeId eq parentStoreId) and
         (StockItems.isActive eq true)
    }
    .firstOrNull { candidate -> stockItemsMatchForParentMirror(sourceItemRow, candidate) }
}

private fun UpdateBuilder<*>.setParentStockMirrorFieldsFromBranchRow(
  sourceItemRow: ResultRow,
  parentStoreId: UUID,
  now: Long
) {
  val cleanBarcodeModels = sourceItemRow.stockBarcodeModels().normalizedGoodsItemBarcodesForStore(
    storeId = parentStoreId.toString(),
    legacyBarcodes = sourceItemRow[StockItems.barcodes]
  )
  this[StockItems.barcodes] = cleanBarcodeModels.cleanBarcodeStrings().ifEmpty { sourceItemRow[StockItems.barcodes].cleanBarcodes() }
  this[StockItems.barcodeModels] = cleanBarcodeModels
  this[StockItems.name] = sourceItemRow[StockItems.name]
  this[StockItems.description] = sourceItemRow[StockItems.description]
  this[StockItems.measurementUnitId] = sourceItemRow[StockItems.measurementUnitId]
  this[StockItems.categoryIds] = sourceItemRow[StockItems.categoryIds]
  this[StockItems.salePrices] = sourceItemRow[StockItems.salePrices]
  this[StockItems.returnPrices] = sourceItemRow[StockItems.returnPrices]
  this[StockItems.supplyPrices] = sourceItemRow[StockItems.supplyPrices]
  this[StockItems.wholesalePrices] = sourceItemRow[StockItems.wholesalePrices]
  this[StockItems.wholesaleMinQuantity] = sourceItemRow[StockItems.wholesaleMinQuantity]
  this[StockItems.genericExpirationPeriod] = sourceItemRow[StockItems.genericExpirationPeriod]
  this[StockItems.isQuickItem] = sourceItemRow[StockItems.isQuickItem]
  this[StockItems.imagePaths] = sourceItemRow[StockItems.imagePaths]
  this[StockItems.activeShelfBatchId] = null
  this[StockItems.promotions] = sourceItemRow[StockItems.promotions]
  this[StockItems.note] = sourceItemRow[StockItems.note]
  this[StockItems.noteLocalized] = sourceItemRow[StockItems.noteLocalized]
  this[StockItems.conditions] = sourceItemRow[StockItems.conditions]
  this[StockItems.updatedAtMillis] = now
  this[StockItems.isActive] = sourceItemRow[StockItems.isActive]
}

private fun mirrorBranchStockItemToParentInsideTransaction(
  branchItemRow: ResultRow,
  previousBranchItemRow: ResultRow? = null,
  userId: UUID,
  now: Long
): ResultRow? {
  val branchStoreId = branchItemRow[StockItems.storeId]
  val parentStoreId = Stores
    .select(Stores.parentStoreId)
    .where { Stores.id eq branchStoreId }
    .singleOrNull()
    ?.get(Stores.parentStoreId)
    ?: return null

  val existingParentMirror = previousBranchItemRow
    ?.let { findMatchingParentMirrorStockItemInsideTransaction(parentStoreId, it) }
    ?: findMatchingParentMirrorStockItemInsideTransaction(parentStoreId, branchItemRow)

  return if (existingParentMirror == null) {
    val mirrorId = UUID.randomUUID()
    StockItems.insert {
      it[StockItems.id] = mirrorId
      it[StockItems.userId] = userId
      it[StockItems.storeId] = parentStoreId
      it[StockItems.createdAtMillis] = now
      it.setParentStockMirrorFieldsFromBranchRow(branchItemRow, parentStoreId, now)
    }
    StockItems.selectAll().where { StockItems.id eq mirrorId }.single()
  } else {
    val mirrorId = existingParentMirror[StockItems.id]
    StockItems.update({ StockItems.id eq mirrorId }) {
      it.setParentStockMirrorFieldsFromBranchRow(branchItemRow, parentStoreId, now)
    }
    StockItems.selectAll().where { StockItems.id eq mirrorId }.single()
  }
}

private fun findOrCloneDestinationStockItemInsideTransaction(
  sourceItemRow: ResultRow,
  destinationStoreId: UUID,
  userId: UUID,
  now: Long
): ResultRow {
  return findMatchingStockItemInStoreInsideTransaction(destinationStoreId, sourceItemRow)
    ?: cloneStockItemToStoreInsideTransaction(sourceItemRow, destinationStoreId, userId, now)
}

private fun ResultRow.toSupplierGoodsPriceDataModel(): SupplierGoodsPriceDataModel {
  return SupplierGoodsPriceDataModel(
    id = this[SupplierGoodsPrices.id].toString(),
    userId = this[SupplierGoodsPrices.userId].toString(),
    storeId = this[SupplierGoodsPrices.storeId].toString(),
    supplierId = this[SupplierGoodsPrices.supplierId].toString(),
    goodsItemId = this[SupplierGoodsPrices.goodsItemId].toString(),

    supplyPrice = this[SupplierGoodsPrices.supplyPrice],

    minOrderQuantity = this[SupplierGoodsPrices.minOrderQuantity],
    packageQuantity = this[SupplierGoodsPrices.packageQuantity],

    supplierBarcode = this[SupplierGoodsPrices.supplierBarcode],
    supplierGoodsName = this[SupplierGoodsPrices.supplierGoodsName],

    lastUsedAtMillis = this[SupplierGoodsPrices.lastUsedAtMillis],
    createdAtMillis = this[SupplierGoodsPrices.createdAtMillis],
    updatedAtMillis = this[SupplierGoodsPrices.updatedAtMillis],

    isActive = this[SupplierGoodsPrices.isActive]
  )
}


private fun ResultRow.toDebtorDataModel(): DebtorDataModel {
  return DebtorDataModel(
    id = this[Debtors.id].toString(),
    email = this[Debtors.email],
    debtAmount = this[Debtors.debtAmount],
    currency = this[Debtors.currency],
    phoneNumber = this[Debtors.phoneNumber],
    firstName = this[Debtors.firstName],
    lastName = this[Debtors.lastName],
    debtorType = this[Debtors.debtorType],
    idNumber = this[Debtors.idNumber],
    companyName = this[Debtors.companyName],
    companyIdNumber = this[Debtors.companyIdNumber],
    debtCreatedAtMillis = this[Debtors.debtCreatedAtMillis],
    debtDueAtMillis = this[Debtors.debtDueAtMillis],
    originalDebtAmount = this[Debtors.originalDebtAmount],
    interest = this[Debtors.interest],
    plannedPayments = this[Debtors.plannedPayments],
    paymentHistory = this[Debtors.paymentHistory],
    transactionIds = this[Debtors.transactionIds]
  )
}

private fun upsertDebtorInsideTransaction(
  userId: UUID,
  storeId: UUID,
  debtor: DebtorDataModel,
  transactionId: String? = null
): DebtorDataModel {
  val now = Instant.now()
  val parsedId = debtor.id.takeIf { it.isNotBlank() }?.let { runCatching { UUID.fromString(it) }.getOrNull() }
  val existing = parsedId?.let { id ->
    Debtors
      .selectAll()
      .where {
        (Debtors.id eq id) and
           (Debtors.userId eq userId) and
           (Debtors.storeId eq storeId) and
           (Debtors.isActive eq true)
      }
      .singleOrNull()
  }

  val transactionIds = debtor.transactionIds
    .toMutableList()
    .also { ids ->
      transactionId?.takeIf { it.isNotBlank() && it !in ids }?.let { ids.add(it) }
    }
    .distinct()

  return if (existing == null) {
    val id = parsedId ?: UUID.randomUUID()

    Debtors.insert {
      it[Debtors.id] = id
      it[Debtors.userId] = userId
      it[Debtors.storeId] = storeId
      it[Debtors.email] = debtor.email.trim()
      it[Debtors.debtAmount] = debtor.debtAmount.coerceAtLeast(0.0)
      it[Debtors.currency] = debtor.currency
      it[Debtors.phoneNumber] = debtor.phoneNumber.trim()
      it[Debtors.firstName] = debtor.firstName.trim()
      it[Debtors.lastName] = debtor.lastName.trim()
      it[Debtors.debtorType] = debtor.debtorType.ifBlank { "individual" }
      it[Debtors.idNumber] = debtor.idNumber.trim()
      it[Debtors.companyName] = debtor.companyName.trim()
      it[Debtors.companyIdNumber] = debtor.companyIdNumber.trim()
      it[Debtors.debtCreatedAtMillis] = debtor.debtCreatedAtMillis.takeIf { value -> value > 0L } ?: System.currentTimeMillis()
      it[Debtors.debtDueAtMillis] = debtor.debtDueAtMillis
      it[Debtors.originalDebtAmount] = debtor.originalDebtAmount ?: debtor.debtAmount.coerceAtLeast(0.0)
      it[Debtors.interest] = debtor.interest
      it[Debtors.plannedPayments] = debtor.plannedPayments
      it[Debtors.paymentHistory] = debtor.paymentHistory
      it[Debtors.transactionIds] = transactionIds
      it[Debtors.updatedAt] = now
      it[Debtors.isActive] = true
    }

    debtor.copy(
      id = id.toString(),
      debtAmount = debtor.debtAmount.coerceAtLeast(0.0),
      originalDebtAmount = debtor.originalDebtAmount ?: debtor.debtAmount.coerceAtLeast(0.0),
      debtCreatedAtMillis = debtor.debtCreatedAtMillis.takeIf { it > 0L } ?: System.currentTimeMillis(),
      transactionIds = transactionIds
    )
  } else {
    val id = existing[Debtors.id]
    val newDebt = (existing[Debtors.debtAmount] + debtor.debtAmount).coerceAtLeast(0.0)
    val mergedIds = (existing[Debtors.transactionIds] + transactionIds).distinct()

    Debtors.update({ Debtors.id eq id }) {
      it[Debtors.email] = debtor.email.trim().ifBlank { existing[Debtors.email] }
      it[Debtors.debtAmount] = newDebt
      it[Debtors.currency] = debtor.currency.ifBlank { existing[Debtors.currency] }
      it[Debtors.phoneNumber] = debtor.phoneNumber.trim().ifBlank { existing[Debtors.phoneNumber] }
      it[Debtors.firstName] = debtor.firstName.trim().ifBlank { existing[Debtors.firstName] }
      it[Debtors.lastName] = debtor.lastName.trim().ifBlank { existing[Debtors.lastName] }
      it[Debtors.debtorType] = debtor.debtorType.ifBlank { existing[Debtors.debtorType] }
      it[Debtors.idNumber] = debtor.idNumber.trim().ifBlank { existing[Debtors.idNumber] }
      it[Debtors.companyName] = debtor.companyName.trim().ifBlank { existing[Debtors.companyName] }
      it[Debtors.companyIdNumber] = debtor.companyIdNumber.trim().ifBlank { existing[Debtors.companyIdNumber] }
      it[Debtors.debtCreatedAtMillis] = existing[Debtors.debtCreatedAtMillis].takeIf { value -> value > 0L } ?: System.currentTimeMillis()
      it[Debtors.debtDueAtMillis] = debtor.debtDueAtMillis ?: existing[Debtors.debtDueAtMillis]
      it[Debtors.originalDebtAmount] = existing[Debtors.originalDebtAmount] ?: newDebt
      it[Debtors.interest] = debtor.interest ?: existing[Debtors.interest]
      it[Debtors.plannedPayments] = if (debtor.plannedPayments.isNotEmpty()) debtor.plannedPayments else existing[Debtors.plannedPayments]
      it[Debtors.paymentHistory] = if (debtor.paymentHistory.isNotEmpty()) debtor.paymentHistory else existing[Debtors.paymentHistory]
      it[Debtors.transactionIds] = mergedIds
      it[Debtors.updatedAt] = now
      it[Debtors.isActive] = true
    }

    Debtors.selectAll().where { Debtors.id eq id }.single().toDebtorDataModel()
  }
}

private fun barcodeClashesInsideTransaction(
  storeId: UUID,
  currentItemId: UUID?,
  barcodeModels: List<GoodsItemBarcodeDataModel>
): Boolean {
  val incomingModels = barcodeModels.normalizedGoodsItemBarcodesForStore(storeId.toString())
  if (incomingModels.isEmpty()) return false

  val candidateStoreIds = listOf(storeId)

  return StockItems
    .selectAll()
    .where {
      (StockItems.storeId inList candidateStoreIds) and
         (StockItems.isActive eq true)
    }
    .any { row ->
      val rowId = row[StockItems.id]
      val sameItem = currentItemId != null && rowId == currentItemId
      val rowStoreId = row[StockItems.storeId]

      !sameItem && row.stockBarcodeModels().any { existingBarcode ->
        incomingModels.any { incomingBarcode ->
          stockBarcodeModelsConflictInsideStore(
            incomingBarcode = incomingBarcode,
            incomingRowStoreId = storeId,
            existingBarcode = existingBarcode,
            existingRowStoreId = rowStoreId
          )
        }
      }
    }
}

private fun supplierCanMaintainPriceBookInsideTransaction(
  userId: UUID,
  storeId: UUID,
  supplierId: UUID,
  goodsItemId: UUID
): Boolean {
  if (!userHasSupplierAccessInsideTransaction(userId, supplierId)) return false

  val goodsItemExists = StockItems
    .select(StockItems.id)
    .where {
      (StockItems.id eq goodsItemId) and
         (StockItems.storeId eq storeId) and
         (StockItems.isActive eq true)
    }
    .empty()
    .not()
  if (!goodsItemExists) return false

  val existingPriceBookRow = SupplierGoodsPrices
    .select(SupplierGoodsPrices.id)
    .where {
      (SupplierGoodsPrices.storeId eq storeId) and
         (SupplierGoodsPrices.supplierId eq supplierId) and
         (SupplierGoodsPrices.goodsItemId eq goodsItemId)
    }
    .empty()
    .not()
  if (existingPriceBookRow) return true

  val relatedOrderIds = SupplierOrders
    .select(SupplierOrders.id)
    .where {
      (SupplierOrders.storeId eq storeId) and
         (SupplierOrders.supplierId eq supplierId) and
         (SupplierOrders.isActive eq true)
    }
    .map { it[SupplierOrders.id] }

  val relatedOrderLineExists = relatedOrderIds.isNotEmpty() && SupplierOrderLines
    .select(SupplierOrderLines.id)
    .where {
      (SupplierOrderLines.orderId inList relatedOrderIds) and
         (SupplierOrderLines.isActive eq true) and
         ((SupplierOrderLines.goodsItemId eq goodsItemId) or (SupplierOrderLines.substituteGoodsItemId eq goodsItemId))
    }
    .empty()
    .not()
  if (relatedOrderLineExists) return true

  return SupplierPartnershipContracts
    .select(SupplierPartnershipContracts.status, SupplierPartnershipContracts.goodsItemIds)
    .where {
      (SupplierPartnershipContracts.storeId eq storeId) and
         (SupplierPartnershipContracts.supplierId eq supplierId) and
         (SupplierPartnershipContracts.isActive eq true)
    }
    .map { row -> row[SupplierPartnershipContracts.status] to row[SupplierPartnershipContracts.goodsItemIds] }
    .any { (status, goodsItemIds) ->
      status != SUPPLIER_CONTRACT_STATUS_DECLINED &&
         status != SUPPLIER_CONTRACT_STATUS_ARCHIVED &&
         (goodsItemIds.isEmpty() || goodsItemId.toString() in goodsItemIds)
    }
}

private fun upsertSupplierGoodsPriceInsideTransaction(
  userId: UUID,
  storeId: UUID,
  supplierId: UUID,
  goodsItemId: UUID,
  supplyPrice: PriceDataModel,
  minOrderQuantity: QuantityDataModel? = null,
  packageQuantity: QuantityDataModel? = null,
  supplierBarcode: String? = null,
  supplierGoodsName: String? = null,
  now: Long
) {
  val existing = SupplierGoodsPrices
    .selectAll()
    .where {
      (SupplierGoodsPrices.storeId eq storeId) and
         (SupplierGoodsPrices.supplierId eq supplierId) and
         (SupplierGoodsPrices.goodsItemId eq goodsItemId)
    }
    .singleOrNull()

  if (existing == null) {
    SupplierGoodsPrices.insert {
      it[SupplierGoodsPrices.id] = UUID.randomUUID()
      it[SupplierGoodsPrices.userId] = userId
      it[SupplierGoodsPrices.storeId] = storeId
      it[SupplierGoodsPrices.supplierId] = supplierId
      it[SupplierGoodsPrices.goodsItemId] = goodsItemId
      it[SupplierGoodsPrices.supplyPrice] = supplyPrice
      it[SupplierGoodsPrices.minOrderQuantity] = minOrderQuantity
      it[SupplierGoodsPrices.packageQuantity] = packageQuantity
      it[SupplierGoodsPrices.supplierBarcode] = supplierBarcode?.takeIf { value -> value.isNotBlank() }
      it[SupplierGoodsPrices.supplierGoodsName] = supplierGoodsName?.takeIf { value -> value.isNotBlank() }
      it[SupplierGoodsPrices.createdAtMillis] = now
      it[SupplierGoodsPrices.updatedAtMillis] = now
      it[SupplierGoodsPrices.lastUsedAtMillis] = now
      it[SupplierGoodsPrices.isActive] = true
    }
  } else {
    SupplierGoodsPrices.update({
      (SupplierGoodsPrices.storeId eq storeId) and
         (SupplierGoodsPrices.supplierId eq supplierId) and
         (SupplierGoodsPrices.goodsItemId eq goodsItemId)
    }) {
      it[SupplierGoodsPrices.supplyPrice] = supplyPrice
      minOrderQuantity?.let { value -> it[SupplierGoodsPrices.minOrderQuantity] = value }
      packageQuantity?.let { value -> it[SupplierGoodsPrices.packageQuantity] = value }
      supplierBarcode?.takeIf { value -> value.isNotBlank() }?.let { value ->
        it[SupplierGoodsPrices.supplierBarcode] = value
      }
      supplierGoodsName?.takeIf { value -> value.isNotBlank() }?.let { value ->
        it[SupplierGoodsPrices.supplierGoodsName] = value
      }
      it[SupplierGoodsPrices.updatedAtMillis] = now
      it[SupplierGoodsPrices.lastUsedAtMillis] = now
      it[SupplierGoodsPrices.isActive] = true
    }
  }
}

private fun learnSupplierPriceFromResponseLineInsideTransaction(
  userId: UUID,
  storeId: UUID,
  supplierId: UUID,
  line: SupplierOrderLineDataModel,
  now: Long
) {
  val offeredPrice = line.supplierOfferedSupplyPrice ?: return
  if (offeredPrice.price.toMoneyDouble() <= 0.0) return
  val acceptedQuantity = line.supplierAcceptedQuantity ?: line.requestedQuantity
  if (acceptedQuantity.total <= 0.0) return

  val effectiveGoodsItemId = (line.substituteGoodsItemId?.takeIf { it.isNotBlank() } ?: line.goodsItemId)
    .let { raw -> runCatching { UUID.fromString(raw) }.getOrNull() }
    ?: return
  val goodsRow = StockItems
    .selectAll()
    .where {
      (StockItems.id eq effectiveGoodsItemId) and
         (StockItems.storeId eq storeId) and
         (StockItems.isActive eq true)
    }
    .singleOrNull()
    ?: return

  upsertSupplierGoodsPriceInsideTransaction(
    userId = userId,
    storeId = storeId,
    supplierId = supplierId,
    goodsItemId = effectiveGoodsItemId,
    supplyPrice = offeredPrice.copy(supplierId = supplierId.toString()),
    supplierBarcode = goodsRow.stockBarcodeValues().firstOrNull { it.isNotBlank() },
    supplierGoodsName = goodsRow[StockItems.name].operationLogVisibleText(effectiveGoodsItemId.toString()),
    now = now
  )
}

private fun defaultServerQuantityForGoodsItem(
  measurementUnitId: String,
  total: Double
): QuantityDataModel {
  return when (measurementUnitId) {
    "1" -> QuantityDataModel(
      id = "1",
      immutableUnitName = listOf(
        LocalizedStringDataModel("main", "kg."),
        LocalizedStringDataModel("en", "kg."),
        LocalizedStringDataModel("ru", "кг."),
        LocalizedStringDataModel("kk", "кг.")
      ),
      total = total,
      pricedAmount = 1.0,
      roundTotal = false
    )

    else -> QuantityDataModel(
      id = measurementUnitId.ifBlank { "0" },
      immutableUnitName = listOf(
        LocalizedStringDataModel("main", "pc."),
        LocalizedStringDataModel("en", "pc."),
        LocalizedStringDataModel("ru", "шт."),
        LocalizedStringDataModel("kk", "дана")
      ),
      total = total,
      pricedAmount = 1.0,
      roundTotal = true
    )
  }
}

private fun findStockItemRowByTransactionBarcodeInsideTransaction(
  storeId: UUID,
  barcode: String,
  preferAvailableBatches: Boolean = false
): ResultRow? {
  val cleanBarcode = barcode.trim()
  if (cleanBarcode.isBlank()) return null

  val visibleStoreIds = stockVisibleStoreIdsInsideTransaction(storeId)
  val matchingRows = StockItems
    .selectAll()
    .where {
      (StockItems.storeId inList visibleStoreIds) and
         (StockItems.isActive eq true)
    }
    .filter { row ->
      row.stockBarcodeMatchesScannedBarcode(cleanBarcode, storeId)
    }

  if (matchingRows.isEmpty()) return null

  val goodsItemIdsWithBatchesInStore = StockBatchesV2
    .select(StockBatchesV2.goodsItemId)
    .where {
      (StockBatchesV2.storeId inList visibleStoreIds) and
         (StockBatchesV2.goodsItemId inList matchingRows.map { it[StockItems.id] }) and
         (StockBatchesV2.isActive eq true)
    }
    .filter { row ->
      val status = row[StockBatchesV2.status]
      status != StockBatchStatusDataModel.Deleted.name &&
         status != StockBatchStatusDataModel.InTransit.name &&
         status != StockBatchStatusDataModel.WrittenOff.name &&
         status != StockBatchStatusDataModel.SoldOut.name
    }
    .map { it[StockBatchesV2.goodsItemId] }
    .toSet()

  return matchingRows.sortedWith(
    compareBy<ResultRow> { row ->
      val hasVisibleBatch = row[StockItems.id] in goodsItemIdsWithBatchesInStore
      when {
        preferAvailableBatches && hasVisibleBatch -> 0
        row[StockItems.storeId] == storeId -> if (preferAvailableBatches) 1 else 0
        hasVisibleBatch -> 1
        else -> 2
      }
    }.thenByDescending { row -> row[StockItems.createdAtMillis] }
  ).firstOrNull()
}

private fun activeStockBatchesForGoodsItemInsideTransaction(
  storeId: UUID,
  goodsItemId: UUID,
  activeShelfBatchId: UUID?,
  visibleStoreGroup: Boolean = false
): List<ResultRow> {
  val storeIds = if (visibleStoreGroup) stockVisibleStoreIdsInsideTransaction(storeId) else listOf(storeId)

  return StockBatchesV2
    .selectAll()
    .where {
      (StockBatchesV2.storeId inList storeIds) and
         (StockBatchesV2.goodsItemId eq goodsItemId) and
         (StockBatchesV2.isActive eq true)
    }
    .filter { row ->
      val status = row[StockBatchesV2.status]
      status != StockBatchStatusDataModel.Deleted.name &&
         status != StockBatchStatusDataModel.InTransit.name &&
         status != StockBatchStatusDataModel.WrittenOff.name
    }
    .sortedWith(
      compareBy<ResultRow> { row ->
        if (row[StockBatchesV2.storeId] == storeId) 0 else 1
      }.thenBy { row ->
        if (activeShelfBatchId != null && row[StockBatchesV2.id] == activeShelfBatchId) 0 else 1
      }.thenBy { row ->
        row[StockBatchesV2.expirationDateMillis] ?: Long.MAX_VALUE
      }.thenByDescending { row ->
        row[StockBatchesV2.shelfPriority]
      }
    )
}

private data class NormalizedTransactionGoodsResult(
  val lines: List<GoodsItemInTransactionDataModel>?,
  val errorCode: String? = null
)

private fun normalizeTransactionGoodsInsideTransaction(
  storeId: UUID,
  transactionType: String,
  lines: List<GoodsItemInTransactionDataModel>
): NormalizedTransactionGoodsResult {
  val transactionTypeIndex = when (transactionType) {
    "purchase" -> 0
    "return" -> 1
    "accept" -> 2
    else -> return NormalizedTransactionGoodsResult(null, "bad_type")
  }

  val normalizedLines = mutableListOf<GoodsItemInTransactionDataModel>()

  for (line in lines) {
    val itemRow = findStockItemRowByTransactionBarcodeInsideTransaction(
      storeId = storeId,
      barcode = line.barcode,
      preferAvailableBatches = transactionType == "purchase"
    ) ?: return NormalizedTransactionGoodsResult(null, "not_found")

    val goodsItem = itemRow.toGoodsItemDataModel()
    val activeBatch = activeStockBatchesForGoodsItemInsideTransaction(
      storeId = storeId,
      goodsItemId = itemRow[StockItems.id],
      activeShelfBatchId = itemRow[StockItems.activeShelfBatchId],
      visibleStoreGroup = transactionType == "purchase"
    ).firstOrNull()?.toGoodsBatchDataModel()

    val requestedSaleMethodId = if (line.saleMethodId == SALE_METHOD_WHOLESALE) {
      SALE_METHOD_WHOLESALE
    } else {
      SALE_METHOD_RETAIL
    }

    if (transactionType == "purchase" && requestedSaleMethodId == SALE_METHOD_WHOLESALE && !goodsItem.isWholesaleEligible(line.quantity)) {
      return NormalizedTransactionGoodsResult(null, "wholesale_minimum")
    }

    val appliedSaleMethodId = if (transactionType == "purchase") {
      requestedSaleMethodId
    } else {
      SALE_METHOD_RETAIL
    }

    goodsItem.firstViolatedPromotionRestriction(
      transactionTypeIndex = transactionTypeIndex,
      quantityTotal = line.quantity,
      batch = activeBatch
    )?.let {
      return NormalizedTransactionGoodsResult(null, "promotion_restriction")
    }

    val resolvedPrice = goodsItem.priceForTransaction(
      transactionTypeIndex = transactionTypeIndex,
      saleMethodId = appliedSaleMethodId,
      quantityTotal = line.quantity,
      batch = activeBatch
    )

    val normalizedPricePerUnit = when {
      transactionType == "accept" && line.pricePerUnit > 0.0 -> line.pricePerUnit
      resolvedPrice.price.isNotBlank() -> resolvedPrice.price.toMoneyDouble()
      line.pricePerUnit >= 0.0 -> line.pricePerUnit
      else -> return NormalizedTransactionGoodsResult(null, "price_unavailable")
    }

    normalizedLines += line.copy(
      pricePerUnit = kotlin.math.round(normalizedPricePerUnit.coerceAtLeast(0.0) * 100.0) / 100.0,
      saleMethodId = appliedSaleMethodId,
      name = goodsItem.name.takeIf { it.isNotEmpty() } ?: line.name,
      goodsItemId = goodsItem.id.takeIf { it.isNotBlank() } ?: line.goodsItemId,
      quantityUnit = line.quantityUnit ?: defaultServerQuantityForGoodsItem(goodsItem.measurementUnitId, line.quantity),
      currencyCode = line.currencyCode?.takeIf { it.isNotBlank() }
        ?: resolvedPrice.currency.takeIf { it.isNotBlank() },
      returnReason = if (transactionType == "return") line.returnReason.trim().take(500) else ""
    )
  }

  return NormalizedTransactionGoodsResult(normalizedLines)
}

private fun preserveGoodsItemNameInTransactionsInsideTransaction(
  storeId: UUID,
  goodsItemRow: ResultRow
): Int {
  val goodsItem = goodsItemRow.toGoodsItemDataModel()
  val goodsItemIdText = goodsItemRow[StockItems.id].toString()
  val goodsItemBarcodes = goodsItemRow.stockBarcodeValues().toSet()
  val goodsItemName = goodsItemRow[StockItems.name]
  val fallbackCurrencyCode = (
     goodsItem.salePrices + goodsItem.returnPrices + goodsItem.supplyPrices + goodsItem.wholesalePrices
     ).firstOrNull { it.currency.isNotBlank() }?.currency
  var updatedTransactions = 0

  Transactions
    .selectAll()
    .where { Transactions.storeId eq storeId }
    .forEach { transactionRow ->
      val transactionId = transactionRow[Transactions.id]
      val originalLines = transactionRow[Transactions.goodsInTransaction]
      var changed = false

      val updatedLines = originalLines.map { line ->
        val belongsToDeletedItem = line.goodsItemId == goodsItemIdText || goodsItemBarcodes.any { storedBarcode ->
          storedBarcodeMatchesScannedTransactionBarcode(
            storedBarcode = storedBarcode,
            scannedBarcode = line.barcode
          ) || storedBarcodeMatchesScannedTransactionBarcode(
            storedBarcode = line.barcode,
            scannedBarcode = storedBarcode
          )
        }

        if (!belongsToDeletedItem) {
          line
        } else {
          val updatedLine = line.copy(
            name = line.name.takeIf { it.isNotEmpty() } ?: goodsItemName,
            goodsItemId = line.goodsItemId?.takeIf { it.isNotBlank() } ?: goodsItemIdText,
            quantityUnit = line.quantityUnit ?: defaultServerQuantityForGoodsItem(goodsItem.measurementUnitId, line.quantity),
            currencyCode = line.currencyCode?.takeIf { it.isNotBlank() } ?: fallbackCurrencyCode
          )
          if (updatedLine != line) changed = true
          updatedLine
        }
      }

      if (changed) {
        Transactions.update({ Transactions.id eq transactionId }) { update ->
          update[Transactions.goodsInTransaction] = updatedLines
        }
        updatedTransactions += 1
      }
    }

  return updatedTransactions
}

private fun preserveExistingGoodsItemNamesInTransactionHistoryInsideTransaction(): Int {
  var updatedTransactions = 0
  StockItems
    .selectAll()
    .forEach { goodsItemRow ->
      updatedTransactions += preserveGoodsItemNameInTransactionsInsideTransaction(
        storeId = goodsItemRow[StockItems.storeId],
        goodsItemRow = goodsItemRow
      )
    }
  return updatedTransactions
}

private fun updateGoodsItemActiveShelfBatchInsideTransaction(
  goodsItemId: UUID,
  storeId: UUID,
  now: Long,
  visibleStoreGroup: Boolean = false
) {
  val storeIds = if (visibleStoreGroup) stockVisibleStoreIdsInsideTransaction(storeId) else listOf(storeId)
  val currentActiveBatchId = StockItems
    .selectAll()
    .where { StockItems.id eq goodsItemId }
    .firstOrNull()
    ?.get(StockItems.activeShelfBatchId)

  val currentActiveBatchStillUsable = currentActiveBatchId?.let { activeId ->
    StockBatchesV2
      .selectAll()
      .where {
        (StockBatchesV2.id eq activeId) and
           (StockBatchesV2.goodsItemId eq goodsItemId) and
           (StockBatchesV2.storeId inList storeIds) and
           (StockBatchesV2.isActive eq true)
      }
      .firstOrNull()
      ?.let { row ->
        val status = row[StockBatchesV2.status]
        status != StockBatchStatusDataModel.Deleted.name &&
           status != StockBatchStatusDataModel.InTransit.name &&
           status != StockBatchStatusDataModel.WrittenOff.name &&
           status != StockBatchStatusDataModel.SoldOut.name &&
           row[StockBatchesV2.quantity].total > 0.0
      }
  } == true

  val nextBatchId = if (currentActiveBatchStillUsable) {
    currentActiveBatchId
  } else {
    StockBatchesV2
      .selectAll()
      .where {
        (StockBatchesV2.goodsItemId eq goodsItemId) and
           (StockBatchesV2.storeId inList storeIds) and
           (StockBatchesV2.isActive eq true)
      }
      .filter { row ->
        val status = row[StockBatchesV2.status]
        status != StockBatchStatusDataModel.Deleted.name &&
           status != StockBatchStatusDataModel.InTransit.name &&
           status != StockBatchStatusDataModel.WrittenOff.name &&
           status != StockBatchStatusDataModel.SoldOut.name &&
           row[StockBatchesV2.quantity].total > 0.0
      }
      .sortedWith(
        compareBy<ResultRow> { row ->
          if (row[StockBatchesV2.storeId] == storeId) 0 else 1
        }.thenBy { row ->
          row[StockBatchesV2.shelfPriority]
        }.thenBy { row ->
          row[StockBatchesV2.expirationDateMillis] ?: Long.MAX_VALUE
        }
      )
      .firstOrNull()
      ?.get(StockBatchesV2.id)
  }

  if (nextBatchId != currentActiveBatchId) {
    StockItems.update({ StockItems.id eq goodsItemId }) {
      it[StockItems.activeShelfBatchId] = nextBatchId
      it[StockItems.updatedAtMillis] = now
    }
  }
}

private fun subtractStockForTransactionLineInsideTransaction(
  storeId: UUID,
  itemRow: ResultRow,
  requestedQuantity: Double,
  now: Long
): Boolean {
  val goodsItemId = itemRow[StockItems.id]
  val activeShelfBatchId = itemRow[StockItems.activeShelfBatchId]
  val quantityToSubtract = requestedQuantity.coerceAtLeast(0.0)

  if (quantityToSubtract <= 0.0) return true

  val batches = activeStockBatchesForGoodsItemInsideTransaction(
    storeId = storeId,
    goodsItemId = goodsItemId,
    activeShelfBatchId = activeShelfBatchId,
    visibleStoreGroup = true
  ).filter { it[StockBatchesV2.quantity].total > 0.0 }

  val available = batches.sumOf { it[StockBatchesV2.quantity].total }
  if (available + 0.000001 < quantityToSubtract)
    return false

  var remaining = quantityToSubtract

  for (batch in batches) {
    if (remaining <= 0.0) break

    val batchId = batch[StockBatchesV2.id]
    val currentQuantity = batch[StockBatchesV2.quantity]
    val currentTotal = currentQuantity.total.coerceAtLeast(0.0)
    val taken = kotlin.math.min(currentTotal, remaining)
    val nextTotal = (currentTotal - taken).coerceAtLeast(0.0)
    val nextQuantity = currentQuantity.copy(total = nextTotal)
    val nextStatus = if (nextTotal <= 0.000001) {
      StockBatchStatusDataModel.SoldOut.name
    } else {
      batch[StockBatchesV2.status]
    }

    StockBatchesV2.update({ StockBatchesV2.id eq batchId }) {
      it[StockBatchesV2.quantity] = nextQuantity
      it[StockBatchesV2.status] = nextStatus
      it[StockBatchesV2.updatedAtMillis] = now
    }

    remaining -= taken
  }

  updateGoodsItemActiveShelfBatchInsideTransaction(goodsItemId, storeId, now, visibleStoreGroup = true)
  return true
}

private fun addStockForTransactionLineInsideTransaction(
  userId: UUID,
  storeId: UUID,
  itemRow: ResultRow,
  addedQuantity: Double,
  now: Long,
  preferredPricePerUnit: Double,
  preferredSupplierIdText: String? = null
): Boolean {
  val goodsItemId = itemRow[StockItems.id]
  val activeShelfBatchId = itemRow[StockItems.activeShelfBatchId]
  val quantityToAdd = addedQuantity.coerceAtLeast(0.0)
  val preferredSupplierId = preferredSupplierIdText
    ?.takeIf { it.isNotBlank() }
    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }

  if (quantityToAdd <= 0.0) return true

  val targetBatch = activeStockBatchesForGoodsItemInsideTransaction(
    storeId = storeId,
    goodsItemId = goodsItemId,
    activeShelfBatchId = activeShelfBatchId
  ).firstOrNull { row ->
    row[StockBatchesV2.status] != StockBatchStatusDataModel.Deleted.name &&
       row[StockBatchesV2.status] != StockBatchStatusDataModel.WrittenOff.name &&
       (preferredSupplierId == null || row[StockBatchesV2.supplierId] == preferredSupplierId)
  }

  if (targetBatch != null) {
    val batchId = targetBatch[StockBatchesV2.id]
    val currentQuantity = targetBatch[StockBatchesV2.quantity]
    val nextQuantity = currentQuantity.copy(total = currentQuantity.total + quantityToAdd)
    val currentStatus = targetBatch[StockBatchesV2.status]
    val nextStatus = if (currentStatus == StockBatchStatusDataModel.SoldOut.name) {
      StockBatchStatusDataModel.Delivered.name
    } else {
      currentStatus
    }

    StockBatchesV2.update({ StockBatchesV2.id eq batchId }) {
      it[StockBatchesV2.quantity] = nextQuantity
      it[StockBatchesV2.status] = nextStatus
      it[StockBatchesV2.updatedAtMillis] = now
    }

    if (activeShelfBatchId == null) {
      StockItems.update({ StockItems.id eq goodsItemId }) {
        it[StockItems.activeShelfBatchId] = batchId
        it[StockItems.updatedAtMillis] = now
      }
    }

    return true
  }

  val supplyPrice = itemRow[StockItems.supplyPrices].firstOrNull()
    ?: itemRow[StockItems.salePrices].firstOrNull()
    ?: itemRow[StockItems.returnPrices].firstOrNull()
    ?: PriceDataModel(
      price = preferredPricePerUnit.toString(),
      currency = "",
      supplierId = ""
    )

  val batchId = UUID.randomUUID()
  StockBatchesV2.insert {
    it[id] = batchId
    it[StockBatchesV2.goodsItemId] = goodsItemId
    it[StockBatchesV2.userId] = userId
    it[StockBatchesV2.storeId] = storeId
    it[StockBatchesV2.supplierId] = preferredSupplierId
    it[StockBatchesV2.supplierOrderId] = null
    it[StockBatchesV2.quantity] = defaultServerQuantityForGoodsItem(itemRow[StockItems.measurementUnitId], quantityToAdd)
    it[StockBatchesV2.supplyPrice] = supplyPrice
    it[StockBatchesV2.salePriceOverride] = null
    it[StockBatchesV2.returnPriceOverride] = null
    it[StockBatchesV2.wholesalePriceOverride] = null
    it[StockBatchesV2.deliveredAtMillis] = now
    it[StockBatchesV2.manufacturedAtMillis] = null
    it[StockBatchesV2.expirationDateMillis] = null
    it[StockBatchesV2.discounts] = emptyList()
    it[StockBatchesV2.promotions] = emptyList()
    it[StockBatchesV2.shelfPosition] = null
    it[StockBatchesV2.shelfPriority] = 0
    it[StockBatchesV2.status] = StockBatchStatusDataModel.Delivered.name
    it[StockBatchesV2.additionalNotes] = null
    it[StockBatchesV2.additionalNotesLocalized] = emptyList()
    it[StockBatchesV2.createdAtMillis] = now
    it[StockBatchesV2.updatedAtMillis] = now
    it[StockBatchesV2.createdByUserId] = userId
    it[StockBatchesV2.isActive] = true
  }

  StockItems.update({ StockItems.id eq goodsItemId }) {
    it[StockItems.activeShelfBatchId] = batchId
    it[StockItems.updatedAtMillis] = now
  }

  return true
}

private fun syncTransactionReturnItemsInsideTransaction(
  userId: UUID,
  storeId: UUID,
  transactionId: UUID,
  transaction: TransactionDataModel,
  clientOperationId: String?,
  timeMillis: Long
) {
  if (transaction.type != "return") return

  TransactionReturnItems.deleteWhere { TransactionReturnItems.transactionId eq transactionId }

  transaction.goodsInTransaction.forEachIndexed { index, line ->
    TransactionReturnItems.insert { row ->
      row[TransactionReturnItems.id] = UUID.randomUUID()
      row[TransactionReturnItems.transactionId] = transactionId
      row[TransactionReturnItems.userId] = userId
      row[TransactionReturnItems.storeId] = storeId
      row[TransactionReturnItems.lineIndex] = index
      row[TransactionReturnItems.goodsItemId] = line.goodsItemId
        ?.takeIf { it.isNotBlank() }
        ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
      row[TransactionReturnItems.barcode] = line.barcode
      row[TransactionReturnItems.name] = line.name
      row[TransactionReturnItems.quantity] = line.quantity
      row[TransactionReturnItems.pricePerUnit] = line.pricePerUnit
      row[TransactionReturnItems.currencyCode] = line.currencyCode?.takeIf { it.isNotBlank() }
      row[TransactionReturnItems.returnReason] = line.returnReason.trim().take(500)
      row[TransactionReturnItems.clientOperationId] = clientOperationId?.takeIf { it.isNotBlank() }
      row[TransactionReturnItems.timeMillis] = timeMillis
    }
  }
}

private fun applyTransactionStockMutationInsideTransaction(
  userId: UUID,
  storeId: UUID,
  transaction: TransactionDataModel,
  now: Long
): Boolean {
  for (line in transaction.goodsInTransaction) {
    val itemRow = findStockItemRowByTransactionBarcodeInsideTransaction(
      storeId = storeId,
      barcode = line.barcode,
      preferAvailableBatches = transaction.type == "purchase"
    ) ?: return false

    val ok = when (transaction.type) {
      "purchase" -> subtractStockForTransactionLineInsideTransaction(
        storeId = storeId,
        itemRow = itemRow,
        requestedQuantity = line.quantity,
        now = now
      )

      "return", "accept" -> addStockForTransactionLineInsideTransaction(
        userId = userId,
        storeId = storeId,
        itemRow = itemRow,
        addedQuantity = line.quantity,
        now = now,
        preferredPricePerUnit = line.pricePerUnit,
        preferredSupplierIdText = line.supplierIdText
      )

      else -> false
    }

    if (!ok) return false
  }

  return true
}

fun Application.module() {
  stabilizeServerRuntimeClassLoader("module")
  prewarmSharedRuntimeSerializers()
  environment.log.info("AITA server classloader: module anchored to ${classLoaderDebugName(aitaServerRuntimeClassLoader)}")
  install(AitaRuntimeClassLoaderPlugin)
  intercept(ApplicationCallPipeline.Setup) {
    val pipelineContext = this
    withContext(aitaServerClassLoaderContextElement()) {
      pipelineContext.proceed()
    }
  }

  install(CallLogging) {
    level = org.slf4j.event.Level.INFO
    format { call ->
      val status = call.response.status()?.value?.toString() ?: "-"
      "AITA HTTP ${call.request.httpMethod.value} ${call.request.path()} -> $status"
    }
  }
  install(AutoHeadResponse)
  install(Compression) {
    gzip() { priority = 1.0 }
  }
  install(DefaultHeaders) {
    header(AITA_SERVER_HEADER, AITA_SERVER_HEADER_VALUE)
    header(HttpHeaders.Vary, "Accept-Encoding")
    header("X-Content-Type-Options", "nosniff")
    header("X-Frame-Options", "DENY")
    header("Referrer-Policy", "no-referrer")
  }
  install(ConditionalHeaders) // adds ETag/Last-Modified when possible
  install(CachingHeaders) {
    options { _, outgoing ->
      when (outgoing.contentType?.withoutParameters()) {
        ContentType.Image.SVG,
        ContentType.Image.PNG,
        ContentType.Image.JPEG,
        ContentType("image", "webp") ->
          CachingOptions(
            CacheControl.MaxAge(
              maxAgeSeconds = 30 * 24 * 3600
            )
          )

        else -> CachingOptions(CacheControl.NoCache(null))
      }
    }
  }
  val productionMode = isProductionMode()
  val allowedCorsOrigins = parseAllowedCorsOrigins(
    environment.config.optionalString("cors.allowedOrigins") ?: envOrSystem("AITA_CORS_ALLOWED_ORIGINS").orEmpty()
  )

  install(CORS) {
    when {
      allowedCorsOrigins.any { it.host == "*" } && productionMode -> {
        error("Wildcard CORS is not allowed in production. Configure AITA_CORS_ALLOWED_ORIGINS explicitly.")
      }

      allowedCorsOrigins.any { it.host == "*" } -> {
        anyHost()
      }

      allowedCorsOrigins.isEmpty() && productionMode -> {
        error("AITA_CORS_ALLOWED_ORIGINS must be configured in production")
      }

      allowedCorsOrigins.isEmpty() -> {
        anyHost()
      }

      else -> {
        allowedCorsOrigins.forEach { origin ->
          allowHost(origin.host, schemes = listOf(origin.scheme))
        }
      }
    }

    allowHeader(HttpHeaders.Authorization)
    allowHeader(HttpHeaders.ContentType)
    allowHeader(HttpHeaders.Accept)
    allowHeader("store_id")
    allowHeader("store-id")
    allowHeader("worker_job_password")
    allowHeader(AITA_DEVICE_INSTALLATION_ID_HEADER)
    allowHeader(AITA_DEVICE_NAME_HEADER)
    allowHeader(AITA_DEVICE_PLATFORM_HEADER)
    allowHeader(AITA_DEVICE_OS_HEADER)
    allowHeader(AITA_DEVICE_APP_NAME_HEADER)
    allowHeader(AITA_DEVICE_APP_VERSION_HEADER)
    allowHeader(AITA_DEVICE_LOCALE_HEADER)
    allowHeader(AITA_CONNECTION_PROBE_HEADER)
    allowHeader("X-AITA-Client-Operation-Id")
    exposeHeader(AITA_SERVER_HEADER)
    allowMethod(HttpMethod.Get)
    allowMethod(HttpMethod.Post)
    allowMethod(HttpMethod.Put)
    allowMethod(HttpMethod.Delete)
    allowMethod(HttpMethod.Options)
    maxAgeInSeconds = 24 * 60 * 60
  }
  install(ContentNegotiation) {
    json(Json {
      prettyPrint = !productionMode
      isLenient = true
      ignoreUnknownKeys = true
      explicitNulls = true
      encodeDefaults = true
    })
  }

  val websocketMaxFrameSize = configLong("websocket.maxFrameSize", "AITA_WEBSOCKET_MAX_FRAME_SIZE", 1_048_576L)
  install(WebSockets) {
    pingPeriod = 30.seconds
    timeout = 90.seconds
    maxFrameSize = websocketMaxFrameSize
    masking = false
  }

  install(StatusPages) {
    exception<BadRequestException> { call, cause ->
      call.safeGenericResponseNoPayload(
        status = HttpStatusCode.BadRequest,
        message = simpleMessage(
          main = "Bad request",
          ru = "Неверный запрос",
          kk = "Қате сұрау"
        ),
        logMessage = "Bad request: ${cause.message}"
      )
    }

    exception<Throwable> { call, cause ->
      if (cause.isClassLoadingFailure()) {
        stabilizeServerRuntimeClassLoader("status-pages-classloading-failure")
        refreshSharedRuntimeSerializersAfterClassLoadingFailure("status-pages:${call.request.path()}", cause)
        call.application.environment.log.error(
          "AITA server classloader failure on ${call.request.httpMethod.value} ${call.request.path()} " +
             "thread=${classLoaderDebugName(Thread.currentThread().contextClassLoader)} " +
             "anchor=${classLoaderDebugName(aitaServerRuntimeClassLoader)}",
          cause
        )
      }
      call.safeGenericResponseNoPayload(
        status = HttpStatusCode.InternalServerError,
        message = simpleMessage(
          main = "Internal server error",
          ru = "Внутренняя ошибка сервера",
          kk = "Сервердің ішкі қатесі"
        ),
        logMessage = "Unhandled server error",
        throwable = cause
      )
    }
  }
//  intercept(ApplicationCallPipeline.Plugins) {
//    val ct = call.request.headers[HttpHeaders.ContentType]
//
//  }
  environment.log.info("AITA server files root: $serverFilesRootPath")
  environment.log.info("AITA config root: $configAppRootPath")
  environment.log.info("AITA assets root: $assetsRootPath")

  val backgroundScope = CoroutineScope(SupervisorJob() + aitaServerIoContext)
  environment.monitor.subscribe(ApplicationStopped) {
    backgroundScope.cancel()
    subscriptionRenewalDaemonStarted = false
    notificationRetentionDaemonStarted = false
  }

  val dbUrl = environment.config.optionalString("db.url") ?: envOrSystem("AITA_DB_URL").orEmpty()
  val dbUser = environment.config.optionalString("db.user") ?: envOrSystem("DB_USER").orEmpty()
  val dbPass = environment.config.optionalString("db.pass") ?: envOrSystem("DB_PASS").orEmpty()

  require(dbUrl.isNotBlank()) { "AITA_DB_URL must be configured" }
  require(dbUser.isNotBlank()) { "DB_USER must be configured" }
  require(dbPass.isNotBlank()) { "DB_PASS must be configured" }

  val hikariMaxPoolSize = configInt("db.maximumPoolSize", "AITA_DB_MAX_POOL_SIZE", 10).coerceAtLeast(1)
  val hikariMinimumIdle = configInt("db.minimumIdle", "AITA_DB_MIN_IDLE", 2).coerceIn(0, hikariMaxPoolSize)
  val hikariConnectionTimeoutMillis = configLong("db.connectionTimeoutMillis", "AITA_DB_CONNECTION_TIMEOUT_MILLIS", 30_000L)
  val hikariValidationTimeoutMillis = configLong("db.validationTimeoutMillis", "AITA_DB_VALIDATION_TIMEOUT_MILLIS", 5_000L)
  val hikariIdleTimeoutMillis = configLong("db.idleTimeoutMillis", "AITA_DB_IDLE_TIMEOUT_MILLIS", 600_000L)
  val hikariMaxLifetimeMillis = configLong("db.maxLifetimeMillis", "AITA_DB_MAX_LIFETIME_MILLIS", 1_800_000L)
  val hikariLeakDetectionThresholdMillis = configLong("db.leakDetectionThresholdMillis", "AITA_DB_LEAK_DETECTION_THRESHOLD_MILLIS", 0L)
  val hikariPoolName = configString("db.poolName", "AITA_DB_POOL_NAME", "aita-postgres-pool")

  val ds = HikariDataSource(HikariConfig().apply {
    jdbcUrl = dbUrl
    username = dbUser
    password = dbPass
    driverClassName = "org.postgresql.Driver"
    maximumPoolSize = hikariMaxPoolSize
    minimumIdle = hikariMinimumIdle
    connectionTimeout = hikariConnectionTimeoutMillis
    validationTimeout = hikariValidationTimeoutMillis
    idleTimeout = hikariIdleTimeoutMillis
    maxLifetime = hikariMaxLifetimeMillis
    leakDetectionThreshold = hikariLeakDetectionThresholdMillis
    poolName = hikariPoolName
    isAutoCommit = false
  })

  environment.monitor.subscribe(ApplicationStopped) {
    ds.close()
  }

  val flywayRunOnStart = configBoolean("flyway.runOnStart", "AITA_FLYWAY_RUN_ON_START", true)
  val flywayValidateOnMigrate = configBoolean("flyway.validateOnMigrate", "AITA_FLYWAY_VALIDATE_ON_MIGRATE", true)
  val flywayCleanDisabled = configBoolean("flyway.cleanDisabled", "AITA_FLYWAY_CLEAN_DISABLED", true)
  val flywayBaselineOnMigrate = configBoolean("flyway.baselineOnMigrate", "AITA_FLYWAY_BASELINE_ON_MIGRATE", true)

  val flyway = Flyway.configure()
    .dataSource(ds)
    .locations(environment.config.optionalString("flyway.locations") ?: "classpath:db/migration")
    .baselineOnMigrate(flywayBaselineOnMigrate)
    .validateOnMigrate(flywayValidateOnMigrate)
    .cleanDisabled(flywayCleanDisabled)
    .load()

  if (flywayRunOnStart) {
    flyway.migrate()
  } else if (flywayValidateOnMigrate) {
    flyway.validate()
  }

  Database.connect(ds)

  org.jetbrains.exposed.sql.transactions.transaction {
    if (configBoolean("app.schemaAutoRepair", "AITA_SCHEMA_AUTO_REPAIR", false)) {
      SchemaUtils.createMissingTablesAndColumns(Users, RefreshSessions, SecuritySessionEvents, Stores, StockItems, StockBatchesV2, StockBatchMovements, Suppliers, SupplierGoodsPrices, SupplierOrders, SupplierOrderLines, Debtors, TransactionReturnItems, Notifications, SupportTickets, SupportMessages, StoreWorkerRequests, StoreWorkerMemberships, StoreWorkerRoleTemplates, Workshifts, CashRegisters, CashRegisterEvents, UserWallets, UserWalletLedgerEntries, TopUpPaymentIntents, StoreSubscriptionStates, StoreSubscriptionChargeEvents, OperationLogs, Manufacturers, GenericGoodsItems, GenericGoodsItemCandidates, GenericGoodsCategories)
    }
    sanitizeGenericGoodsCategoryPrefixesInsideTransaction()
    seedGenericGoodsCategoriesInsideTransaction()
    preserveExistingGoodsItemNamesInTransactionHistoryInsideTransaction()
    cleanupNotificationsInsideTransaction()
  }

  configureJwtAuth()
  startSubscriptionRenewalDaemon(backgroundScope)
  startNotificationRetentionDaemon(backgroundScope)

  intercept(ApplicationCallPipeline.Call) {
    proceed()

    val method = call.request.httpMethod
    val path = call.request.path()
    val status = call.response.status()?.value

    if (
      method in setOf(HttpMethod.Post, HttpMethod.Put, HttpMethod.Delete) &&
      !path.startsWith("/rt/") &&
      !path.equals("/auth/refresh", ignoreCase = true) &&
      !path.equals("/auth/logIn", ignoreCase = true) &&
      (status == null || status in 200..299)
    ) {
      val storeId = call.request.header("store_id")
        ?: call.request.header("store-id")
        ?: call.request.queryParameters["store_id"]

      val entityPath = path.trim('/').ifBlank { "all" }

      RealtimeServerBus.publish(
        entity = entityPath,
        storeId = storeId,
        reason = "mutation"
      )

      val actorUserId = runCatching { call.principal<JWTPrincipal>()?.subject?.let { UUID.fromString(it) } }.getOrNull()
      val logStoreId = storeId?.let { runCatching { UUID.fromString(it) }.getOrNull() }
      val methodText = method.value
      val logAction = operationLogActionForHttpMutation(methodText, entityPath)
      val logEntityType = operationLogEntityForPath(entityPath)
      val normalizedEntityPath = entityPath.lowercase()
      val routeWritesSpecificOperationLog = normalizedEntityPath.startsWith("notifications") ||
         normalizedEntityPath.startsWith("operationlogs") ||
         normalizedEntityPath.startsWith("logs/") ||
         normalizedEntityPath.startsWith("stock/history") ||
         normalizedEntityPath.startsWith("stock/add") ||
         normalizedEntityPath.startsWith("stock/update") ||
         normalizedEntityPath.startsWith("stock/delete") ||
         normalizedEntityPath.startsWith("stockbatches/add") ||
         normalizedEntityPath.startsWith("stockbatches/update") ||
         normalizedEntityPath.startsWith("stockbatches/delete") ||
         normalizedEntityPath.startsWith("stockbatches/move") ||
         normalizedEntityPath.startsWith("stockbatches/decidemove") ||
         normalizedEntityPath.startsWith("stockbatches/setactiveshelfbatch") ||
         normalizedEntityPath.startsWith("transactions/complete") ||
         normalizedEntityPath.startsWith("workshifts/start") ||
         normalizedEntityPath.startsWith("workshifts/end")

      if (actorUserId != null && logStoreId != null && !routeWritesSpecificOperationLog) {
        backgroundScope.launch {
          runCatching {
            newSuspendedTransaction(aitaServerIoContext) {
              insertOperationLogInsideTransaction(
                actorUserId = actorUserId,
                storeId = logStoreId,
                action = logAction,
                entityType = logEntityType,
                entityId = null,
                title = operationLogHumanTitleFor(logAction, logEntityType),
                details = operationLogHumanDetailsFor(logAction, logEntityType),
                metadata = mapOf("http_method" to methodText, "http_path" to entityPath)
              )
            }
          }.onFailure { throwable ->
            this@module.environment.log.error("Failed to write operation log", throwable)
          }
        }
      }
    }
  }

  val tokenService = TokenService(jwtConfig())

  routing {
    get("/healthz") {
      call.respondText(
        text = "{\"status\":\"ok\"}",
        contentType = ContentType.Application.Json,
        status = HttpStatusCode.OK
      )
    }

    get("/readyz") {
      val ready = runCatching {
        newSuspendedTransaction(aitaServerIoContext) {
          exec("SELECT 1") { }
        }
      }.isSuccess

      call.respondText(
        text = if (ready) "{\"status\":\"ready\"}" else "{\"status\":\"not_ready\"}",
        contentType = ContentType.Application.Json,
        status = if (ready) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable
      )
    }

    authenticate("auth-jwt") {
      webSocket("/rt/updates") {
        val principal = call.principal<JWTPrincipal>()
        val userId = runCatching { principal?.subject?.let { UUID.fromString(it) } }.getOrNull()

        if (userId == null) {
          close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Unauthorized"))
          return@webSocket
        }

        suspend fun sendRealtimeUpdate(update: RealtimeUpdateDataModel) {
          send(Frame.Text(jsonBase.encodeToString(update)))
        }

        try {
          val now = System.currentTimeMillis()
          sendRealtimeUpdate(
            RealtimeUpdateDataModel(
              id = UUID.randomUUID().toString(),
              type = "connected",
              entity = "connection",
              reason = "websocket_connected",
              createdAtMillis = now
            )
          )


          val collector = launch {
            RealtimeServerBus.sharedUpdates.collect { update ->
              try {
                sendRealtimeUpdate(update)
              } catch (throwable: Throwable) {
                if (throwable.isExpectedRealtimeDisconnect()) {
                  call.application.environment.log.debug(
                    "Realtime WebSocket send stopped after normal disconnect: ${throwable.message ?: throwable::class.simpleName}"
                  )
                  cancel("Realtime client disconnected", throwable)
                } else {
                  throw throwable
                }
              }
            }
          }

          try {
            for (frame in incoming) {
              if (frame is Frame.Text) {
                // The current protocol only needs the client hello to keep the connection warm.
                // The client refreshes itself when the server broadcasts mutation notices.
                frame.readText()
              }
            }
          } finally {
            collector.cancel()
          }
        } catch (throwable: Throwable) {
          if (throwable.isExpectedRealtimeDisconnect()) {
            call.application.environment.log.debug("Realtime WebSocket disconnected normally: ${throwable.message ?: throwable::class.simpleName}")
          } else {
            throw throwable
          }
        }
      }
    }

    route("/auth") {
      post("/signUp") {
        try {
          val body = call.receiveAita<UserAuthSignUpDataModel>()
          val phoneNumber = body.phoneNumber.trim().lowercase()
          val email = body.email.trim().lowercase()
          val cleanPassword = body.password.trim()

          if (!cleanPassword.checkAsPassword()) {
            return@post call.genericResponseNoPayload(
              HttpStatusCode.BadRequest,
              message = passwordRequirementMessage()
            )
          }

          val conflictResult = newSuspendedTransaction(aitaServerIoContext) {
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

          val hash = Pw.hash(cleanPassword.toCharArray())
          val instant = Instant.now()

          var state23505Reached: Boolean

          do {
            state23505Reached = try {
              id = UUID.randomUUID()

              newSuspendedTransaction(aitaServerIoContext) {
                Users.insert {
                  it[Users.id] = id
                  it[Users.publicId] = generateUniqueUserPublicIdInsideTransaction()
                  it[Users.phoneNumber] = phoneNumber
                  it[Users.email] = email
                  it[Users.firstName] = firstName
                  it[Users.lastName] = lastName
                  it[Users.countryLocale] = countryLocale
                  it[Users.workerIds] = null
                  it[Users.supplierIds] = null
                  it[Users.appLanguage] = DEFAULT_APP_LANGUAGE
                  it[Users.appThemeId] = DEFAULT_APP_THEME_ID
                  it[Users.appSizeModeId] = DEFAULT_APP_SIZE_MODE_ID
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
            val tokenPair: TokenPair = tokenService.newPair(this, metaFrom(call, body.deviceInfo))

            call.genericTokenPairResponse(
              status = HttpStatusCode.Created,
              payload = tokenPair
            )
          } ?: call.genericResponseNoPayload(
            status = HttpStatusCode.InternalServerError,
            message = getResponse("3").message
          )
        } catch (throwable: Throwable) {
          call.safeGenericResponseNoPayload(
            status = HttpStatusCode.InternalServerError,
            message = getResponse("3").message,
            logMessage = "Sign-up failed",
            throwable = throwable
          )
        }
      }

      post("/logIn") {
        try {
          val body = call.receiveAita<UserAuthLogInDataModel>()

          val login = body.login.trim().lowercase()

          val invalidCredentialsMessage = simpleMessage(
            main = "Invalid login or password",
            ru = "Неверный логин или пароль",
            kk = "Логин немесе құпиясөз қате"
          )

          val user = newSuspendedTransaction(aitaServerIoContext) {
            Users.selectAll().where { (Users.phoneNumber eq login) or (Users.email eq login) }.singleOrNull()
          } ?: return@post call.genericResponseNoPayload(
            status = HttpStatusCode.Unauthorized,
            message = invalidCredentialsMessage
          )

          val ok = Pw.verify(body.password.toCharArray(), user[Users.passwordHash])

          if (!ok)
            return@post call.genericResponseNoPayload(
              status = HttpStatusCode.Unauthorized,
              message = invalidCredentialsMessage
            )

          val tokenPair: TokenPair = tokenService.newPair(user[Users.id], metaFrom(call, body.deviceInfo))

          call.genericTokenPairResponse(HttpStatusCode.OK, tokenPair)
        } catch (throwable: Throwable) {
          call.safeGenericResponseNoPayload(
            status = HttpStatusCode.InternalServerError,
            message = getResponse("3").message,
            logMessage = "Log-in failed",
            throwable = throwable
          )
        }
      }

      get("/ping") {
        val silentConnectionProbe = call.request.header(AITA_CONNECTION_PROBE_HEADER) == "1" ||
           call.request.queryParameters["silent"]?.equals("true", ignoreCase = true) == true

        val readinessOk = runCatching {
          stabilizeServerRuntimeClassLoader("auth-ping-readiness")
          prewarmSharedRuntimeSerializers()
          newSuspendedTransaction(aitaServerIoContext) {
            Users.select(Users.id).limit(1).toList()
          }
          true
        }.getOrElse { throwable ->
          call.application.environment.log.error("AITA readiness probe failed", throwable)
          false
        }

        if (!readinessOk) {
          return@get call.genericResponseNoPayload(
            status = HttpStatusCode.ServiceUnavailable,
            message = simpleMessage(
              main = "Server is starting or repairing itself. Try again shortly.",
              en = "Server is starting or repairing itself. Try again shortly.",
              ru = "Сервер запускается или восстанавливается. Повторите чуть позже.",
              kk = "Сервер іске қосылып немесе қалпына келіп жатыр. Сәл кейін қайталаңыз."
            )
          )
        }

        if (!silentConnectionProbe) {
          RealtimeServerBus.publish(
            storeId = call.request.header("store_id") ?: call.request.header("store-id"),
            entity = "connection",
            reason = "manual_connection_check"
          )
        }
        call.genericResponseNoPayload(
          status = HttpStatusCode.OK,
          message = simpleMessage(
            main = "Server connection available",
            en = "Server connection available",
            ru = "Сервер доступен",
            kk = "Сервер қолжетімді"
          )
        )
      }

      delete("/logOut") {
        val rawBody = runCatching { call.receiveTextAita().trim() }.getOrNull().orEmpty()
        val cleanup = rawBody
          .takeIf { it.startsWith("{") }
          ?.let { body -> runCatching { jsonBase.decodeFromString<LogoutCleanupRequestDataModel>(body) }.getOrNull() }
        val refreshToken = cleanup?.refreshToken?.trim().orEmpty()
          .ifBlank { rawBody.trim().trim('"') }

        val cleanupUserId = refreshSessionUserIdForPlainToken(refreshToken)
        if (cleanup != null && cleanupUserId != null) {
          val cleanupStoreId = cleanup.workshiftStoreId
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
          val cleanupWorkshiftEnd = cleanup.workshiftEnd
          if (cleanupStoreId != null && cleanupWorkshiftEnd != null) {
            val endedWorkshift = runCatching {
              newSuspendedTransaction(aitaServerIoContext) {
                endWorkshiftForUserInsideTransaction(
                  workerUserId = cleanupUserId,
                  storeId = cleanupStoreId,
                  endedByUserId = cleanupUserId,
                  request = cleanupWorkshiftEnd,
                  now = cleanupWorkshiftEnd.endedAtMillis.takeIf { it > 0L } ?: System.currentTimeMillis()
                )
              }
            }.onFailure { throwable ->
              call.application.environment.log.error("Queued logout workshift cleanup failed", throwable)
            }.getOrNull()

            endedWorkshift?.let { publishWorkerRealtimeBundle(it.storeId, "workshift_ended") }
          }
        }

        if (refreshToken.isNotBlank()) {
          try {
            tokenService.revoke(refreshToken)
          } catch (throwable: Throwable) {
            call.application.environment.log.error("Logout token revoke failed", throwable)
          }
        }

        call.genericResponseNoPayload(HttpStatusCode.OK, message = getResponse("8").message)
      }

      post("/refresh") {
        val body = runCatching { call.receiveTextAita().trim().trim('"') }.getOrNull().orEmpty()
        try {
          val newTokens = tokenService.rotate(body, metaFrom(call))
          call.genericTokenPairResponse(HttpStatusCode.OK, newTokens)
        } catch (throwable: Throwable) {
          if (throwable is IllegalAccessException) {
            call.genericResponseNoPayload(
              status = HttpStatusCode.Unauthorized,
              message = simpleMessage(
                main = "Cloud session needs refresh. You remain signed in locally.",
                ru = "Облачный сеанс нужно обновить. Вы остаётесь в аккаунте локально.",
                kk = "Бұлттық сеансты жаңарту қажет. Сіз жергілікті түрде аккаунтта қаласыз."
              )
            )
          } else {
            call.safeGenericResponseNoPayload(
              status = HttpStatusCode.InternalServerError,
              message = simpleMessage(
                main = "Server could not refresh session. Try again.",
                ru = "Сервер не смог обновить сеанс. Попробуйте ещё раз.",
                kk = "Сервер сеансты жаңарта алмады. Қайталап көріңіз."
              ),
              logMessage = "Refresh token rotation failed",
              throwable = throwable
            )
          }
        }
      }
    }

    authenticate("auth-jwt") {
      route("/security/sessions") {
        get("/get") {
          val userId = call.checkPrincipal() ?: return@get
          val currentSessionId = call.currentJwtSessionId()

          val sessions = loadSecuritySessionsForUser(userId, currentSessionId)

          call.genericResponse(
            status = HttpStatusCode.OK,
            payload = sessions,
            message = getResponse("44").message
          )
        }

        get("/history") {
          val userId = call.checkPrincipal() ?: return@get

          call.genericResponse(
            status = HttpStatusCode.OK,
            payload = loadSecuritySessionHistoryForUser(userId),
            message = getResponse("104").message
          )
        }

        post("/revoke") {
          val userId = call.checkPrincipal() ?: return@post
          val currentSessionId = call.currentJwtSessionId()
          val request = runCatching { call.receiveAita<SecuritySessionRevokeRequestDataModel>() }.getOrNull()
          val targetSessionId = request
            ?.sessionId
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }

          if (targetSessionId == null) {
            return@post call.genericResponseNoPayload(
              status = HttpStatusCode.BadRequest,
              message = getResponse("47").message
            )
          }

          if (targetSessionId == currentSessionId) {
            return@post call.genericResponseNoPayload(
              status = HttpStatusCode.BadRequest,
              message = getResponse("48").message
            )
          }

          newSuspendedTransaction(aitaServerIoContext) {
            val now = Instant.now()
            val nowMillis = now.toEpochMilli()
            val rows = RefreshSessions
              .selectAll()
              .where {
                (RefreshSessions.id eq targetSessionId) and
                   (RefreshSessions.userId eq userId) and
                   RefreshSessions.revokedAt.isNull()
              }
              .forUpdate()
              .toList()

            rows.forEach { row ->
              RefreshSessions.update({ RefreshSessions.id eq row[RefreshSessions.id] }) {
                it[RefreshSessions.revokedAt] = now
              }
              insertSecuritySessionEventInsideTransaction(
                userId = userId,
                sessionId = row[RefreshSessions.id],
                eventType = SECURITY_EVENT_SESSION_REVOKED,
                metaParam = row[RefreshSessions.meta],
                metadata = mapOf("actor" to "user"),
                now = nowMillis
              )
            }
          }

          call.genericResponse(
            status = HttpStatusCode.OK,
            payload = loadSecuritySessionsForUser(userId, currentSessionId),
            message = getResponse("45").message
          )
        }

        post("/revokeOthers") {
          val userId = call.checkPrincipal() ?: return@post
          val currentSessionId = call.currentJwtSessionId()
          val sessionToKeep = currentSessionId ?: UUID(0L, 0L)

          newSuspendedTransaction(aitaServerIoContext) {
            val now = Instant.now()
            val nowMillis = now.toEpochMilli()
            val rows = RefreshSessions
              .selectAll()
              .where {
                (RefreshSessions.userId eq userId) and
                   RefreshSessions.revokedAt.isNull() and
                   (RefreshSessions.id neq sessionToKeep)
              }
              .forUpdate()
              .toList()

            rows.forEach { row ->
              RefreshSessions.update({ RefreshSessions.id eq row[RefreshSessions.id] }) {
                it[RefreshSessions.revokedAt] = now
              }
              insertSecuritySessionEventInsideTransaction(
                userId = userId,
                sessionId = row[RefreshSessions.id],
                eventType = SECURITY_EVENT_SESSION_REVOKED_OTHERS,
                metaParam = row[RefreshSessions.meta],
                metadata = mapOf("actor" to "user", "kept_session_id" to sessionToKeep.toString()),
                now = nowMillis
              )
            }
          }

          call.genericResponse(
            status = HttpStatusCode.OK,
            payload = loadSecuritySessionsForUser(userId, currentSessionId),
            message = getResponse("46").message
          )
        }
      }
    }


    route("/support") {
      authenticate("auth-jwt") {
        route("/tickets") {
          get("/get") {
            val userId = call.checkPrincipal() ?: return@get

            val tickets = newSuspendedTransaction(aitaServerIoContext) {
              SupportTickets
                .selectAll()
                .where { (SupportTickets.userId eq userId) and (SupportTickets.isActive eq true) }
                .orderBy(SupportTickets.updatedAtMillis to SortOrder.DESC)
                .limit(100)
                .map { it.toSupportTicketDataModel() }
            }

            call.genericResponse(HttpStatusCode.OK, tickets, getResponse("96").message)
          }

          post("/create") {
            val userId = call.checkPrincipal() ?: return@post
            val body = call.receiveAita<SupportTicketCreateRequestDataModel>()
            val messageText = body.initialMessage.trim()

            if (messageText.isBlank()) {
              return@post call.genericResponseNoPayload(HttpStatusCode.BadRequest, getResponse("13").message)
            }

            val ticket = newSuspendedTransaction(aitaServerIoContext) {
              val now = System.currentTimeMillis()
              val ticketId = UUID.randomUUID()
              val ticketPublicId = generateUniqueSupportTicketPublicIdInsideTransaction()
              val storeId = body.storeId?.let { runCatching { UUID.fromString(it) }.getOrNull() }
              val subject = sanitizeSupportSubject(body.subject, messageText)
              val category = sanitizeSupportCategory(body.category)
              val priority = sanitizeSupportPriority(body.priority)
              val displayName = userDisplayNameInsideTransaction(userId)
              val clientMessageId = body.clientMessageId?.takeIf { it.isNotBlank() }

              SupportTickets.insert {
                it[SupportTickets.id] = ticketId
                it[SupportTickets.publicId] = ticketPublicId
                it[SupportTickets.userId] = userId
                it[SupportTickets.storeId] = storeId
                it[SupportTickets.subject] = subject
                it[SupportTickets.category] = category
                it[SupportTickets.priority] = priority
                it[SupportTickets.status] = "open"
                it[SupportTickets.assignedAgentUserId] = null
                it[SupportTickets.lastMessage] = messageText.take(500)
                it[SupportTickets.lastMessageAtMillis] = now
                it[SupportTickets.lastCustomerMessageAtMillis] = now
                it[SupportTickets.lastAgentMessageAtMillis] = null
                it[SupportTickets.unreadForUserCount] = 0
                it[SupportTickets.unreadForAgentCount] = 1
                it[SupportTickets.metadata] = body.metadata
                it[SupportTickets.createdAtMillis] = now
                it[SupportTickets.updatedAtMillis] = now
                it[SupportTickets.closedAtMillis] = null
                it[SupportTickets.isActive] = true
              }

              SupportMessages.insert {
                it[SupportMessages.id] = UUID.randomUUID()
                it[SupportMessages.ticketId] = ticketId
                it[SupportMessages.userId] = userId
                it[SupportMessages.senderUserId] = userId
                it[SupportMessages.senderRole] = "customer"
                it[SupportMessages.senderDisplayName] = displayName
                it[SupportMessages.body] = messageText
                it[SupportMessages.attachments] = emptyList()
                it[SupportMessages.metadata] = body.metadata
                it[SupportMessages.clientMessageId] = clientMessageId
                it[SupportMessages.createdAtMillis] = now
                it[SupportMessages.editedAtMillis] = null
                it[SupportMessages.readByCustomerAtMillis] = now
                it[SupportMessages.readByAgentAtMillis] = null
                it[SupportMessages.isActive] = true
              }

              SupportTickets.selectAll().where { SupportTickets.id eq ticketId }.single().toSupportTicketDataModel()
            }

            call.genericResponse(HttpStatusCode.Created, ticket, getResponse("97").message)
          }

          post("/close") {
            val userId = call.checkPrincipal() ?: return@post
            val body = call.receiveAita<SupportTicketActionRequestDataModel>()
            val ticketId = runCatching { UUID.fromString(body.ticketId) }.getOrNull()
              ?: return@post call.genericResponseNoPayload(HttpStatusCode.BadRequest, getResponse("13").message)

            val ticket = newSuspendedTransaction(aitaServerIoContext) {
              val existing = supportTicketForUserInsideTransaction(userId, ticketId) ?: return@newSuspendedTransaction null
              val now = System.currentTimeMillis()
              SupportTickets.update({ SupportTickets.id eq ticketId }) {
                it[SupportTickets.status] = "closed"
                it[SupportTickets.closedAtMillis] = now
                it[SupportTickets.updatedAtMillis] = now
                it[SupportTickets.unreadForUserCount] = 0
              }
              SupportTickets.selectAll().where { SupportTickets.id eq existing[SupportTickets.id] }.single().toSupportTicketDataModel()
            }

            ticket?.let { call.genericResponse(HttpStatusCode.OK, it, getResponse("98").message) }
              ?: call.genericResponseNoPayload(HttpStatusCode.NotFound, getResponse("13").message)
          }

          post("/reopen") {
            val userId = call.checkPrincipal() ?: return@post
            val body = call.receiveAita<SupportTicketActionRequestDataModel>()
            val ticketId = runCatching { UUID.fromString(body.ticketId) }.getOrNull()
              ?: return@post call.genericResponseNoPayload(HttpStatusCode.BadRequest, getResponse("13").message)

            val ticket = newSuspendedTransaction(aitaServerIoContext) {
              val existing = supportTicketForUserInsideTransaction(userId, ticketId) ?: return@newSuspendedTransaction null
              val now = System.currentTimeMillis()
              SupportTickets.update({ SupportTickets.id eq ticketId }) {
                it[SupportTickets.status] = "open"
                it[SupportTickets.closedAtMillis] = null
                it[SupportTickets.updatedAtMillis] = now
              }
              SupportTickets.selectAll().where { SupportTickets.id eq existing[SupportTickets.id] }.single().toSupportTicketDataModel()
            }

            ticket?.let { call.genericResponse(HttpStatusCode.OK, it, getResponse("99").message) }
              ?: call.genericResponseNoPayload(HttpStatusCode.NotFound, getResponse("13").message)
          }
        }

        route("/messages") {
          get("/get") {
            val userId = call.checkPrincipal() ?: return@get
            val ticketId = (call.request.header("ticket_id") ?: call.request.queryParameters["ticket_id"])
              ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
              ?: return@get call.genericResponseNoPayload(HttpStatusCode.BadRequest, getResponse("13").message)
            val markRead = (call.request.header("mark_read") ?: call.request.queryParameters["mark_read"])
              ?.equals("true", ignoreCase = true) != false

            val messages = newSuspendedTransaction(aitaServerIoContext) {
              supportTicketForUserInsideTransaction(userId, ticketId) ?: return@newSuspendedTransaction null
              val now = System.currentTimeMillis()

              if (markRead) {
                SupportMessages.update({
                  (SupportMessages.ticketId eq ticketId) and
                     (SupportMessages.userId eq userId) and
                     (SupportMessages.senderRole neq "customer") and
                     SupportMessages.readByCustomerAtMillis.isNull()
                }) {
                  it[SupportMessages.readByCustomerAtMillis] = now
                }
                SupportTickets.update({ SupportTickets.id eq ticketId }) {
                  it[SupportTickets.unreadForUserCount] = 0
                }
              }

              SupportMessages
                .selectAll()
                .where { (SupportMessages.ticketId eq ticketId) and (SupportMessages.userId eq userId) and (SupportMessages.isActive eq true) }
                .orderBy(SupportMessages.createdAtMillis to SortOrder.ASC)
                .map { it.toSupportMessageDataModel() }
            }

            messages?.let { call.genericResponse(HttpStatusCode.OK, it, getResponse("100").message) }
              ?: call.genericResponseNoPayload(HttpStatusCode.NotFound, getResponse("13").message)
          }

          post("/send") {
            val userId = call.checkPrincipal() ?: return@post
            val body = call.receiveAita<SupportMessageSendRequestDataModel>()
            val ticketId = runCatching { UUID.fromString(body.ticketId) }.getOrNull()
              ?: return@post call.genericResponseNoPayload(HttpStatusCode.BadRequest, getResponse("13").message)
            val messageText = body.body.trim()

            if (messageText.isBlank()) {
              return@post call.genericResponseNoPayload(HttpStatusCode.BadRequest, getResponse("13").message)
            }

            val message = newSuspendedTransaction(aitaServerIoContext) {
              val existingTicket = supportTicketForUserInsideTransaction(userId, ticketId) ?: return@newSuspendedTransaction null
              val clientMessageId = body.clientMessageId?.takeIf { it.isNotBlank() }
              clientMessageId?.let { id ->
                SupportMessages.selectAll()
                  .where { (SupportMessages.clientMessageId eq id) and (SupportMessages.userId eq userId) }
                  .singleOrNull()
                  ?.let { return@newSuspendedTransaction it.toSupportMessageDataModel() }
              }

              val now = System.currentTimeMillis()
              val messageId = UUID.randomUUID()
              val displayName = userDisplayNameInsideTransaction(userId)

              SupportMessages.insert {
                it[SupportMessages.id] = messageId
                it[SupportMessages.ticketId] = ticketId
                it[SupportMessages.userId] = userId
                it[SupportMessages.senderUserId] = userId
                it[SupportMessages.senderRole] = "customer"
                it[SupportMessages.senderDisplayName] = displayName
                it[SupportMessages.body] = messageText
                it[SupportMessages.attachments] = body.attachments
                it[SupportMessages.metadata] = body.metadata
                it[SupportMessages.clientMessageId] = clientMessageId
                it[SupportMessages.createdAtMillis] = now
                it[SupportMessages.editedAtMillis] = null
                it[SupportMessages.readByCustomerAtMillis] = now
                it[SupportMessages.readByAgentAtMillis] = null
                it[SupportMessages.isActive] = true
              }

              SupportTickets.update({ SupportTickets.id eq ticketId }) {
                it[SupportTickets.status] = "open"
                it[SupportTickets.lastMessage] = messageText.take(500)
                it[SupportTickets.lastMessageAtMillis] = now
                it[SupportTickets.lastCustomerMessageAtMillis] = now
                it[SupportTickets.updatedAtMillis] = now
                it[SupportTickets.closedAtMillis] = null
                it[SupportTickets.unreadForAgentCount] = existingTicket[SupportTickets.unreadForAgentCount] + 1
              }

              SupportMessages.selectAll().where { SupportMessages.id eq messageId }.single().toSupportMessageDataModel()
            }

            message?.let { call.genericResponse(HttpStatusCode.Created, it, getResponse("101").message) }
              ?: call.genericResponseNoPayload(HttpStatusCode.NotFound, getResponse("13").message)
          }

          post("/read") {
            val userId = call.checkPrincipal() ?: return@post
            val body = call.receiveAita<SupportMessagesReadRequestDataModel>()
            val ticketId = runCatching { UUID.fromString(body.ticketId) }.getOrNull()
              ?: return@post call.genericResponseNoPayload(HttpStatusCode.BadRequest, getResponse("13").message)

            val messages = newSuspendedTransaction(aitaServerIoContext) {
              supportTicketForUserInsideTransaction(userId, ticketId) ?: return@newSuspendedTransaction null
              val now = System.currentTimeMillis()
              SupportMessages.update({
                (SupportMessages.ticketId eq ticketId) and
                   (SupportMessages.userId eq userId) and
                   (SupportMessages.senderRole neq "customer") and
                   SupportMessages.readByCustomerAtMillis.isNull()
              }) {
                it[SupportMessages.readByCustomerAtMillis] = now
              }
              SupportTickets.update({ SupportTickets.id eq ticketId }) {
                it[SupportTickets.unreadForUserCount] = 0
                it[SupportTickets.updatedAtMillis] = now
              }
              SupportMessages
                .selectAll()
                .where { (SupportMessages.ticketId eq ticketId) and (SupportMessages.userId eq userId) and (SupportMessages.isActive eq true) }
                .orderBy(SupportMessages.createdAtMillis to SortOrder.ASC)
                .map { it.toSupportMessageDataModel() }
            }

            messages?.let { call.genericResponse(HttpStatusCode.OK, it, getResponse("102").message) }
              ?: call.genericResponseNoPayload(HttpStatusCode.NotFound, getResponse("13").message)
          }
        }
      }
    }

    route("/notifications") {
      authenticate("auth-jwt") {
        get("/get") {
          val userId = call.checkPrincipal() ?: return@get

          val notifications = newSuspendedTransaction(aitaServerIoContext) {
            Notifications
              .selectAll()
              .where { (Notifications.userId eq userId) and (Notifications.isActive eq true) }
              .orderBy(Notifications.createdAtMillis to SortOrder.DESC)
              .limit(500)
              .map { it.toNotificationDataModel() }
          }

          call.genericResponse(HttpStatusCode.OK, notifications)
        }



        post("/add") {
          val userId = call.checkPrincipal() ?: return@post
          val body = call.receiveAita<NotificationDataModel>()
          val now = System.currentTimeMillis()
          val storeId = body.storeId?.let { runCatching { UUID.fromString(it) }.getOrNull() }
          val category = body.category.ifBlank { body.type.name.lowercase() }
          val source = body.source.ifBlank { "app" }
          val dedupeCutoff = now - NOTIFICATION_RECENT_DUPLICATE_WINDOW_MILLIS
          val operationId = notificationOperationIdFromMetadata(body.metadata) ?: notificationOperationIdFromId(body.id)
          val incomingIsLoading = isLoadingNotificationIntent(body.type, category, body.metadata, body.title, body.message)
          val incomingIsResult = isResultNotificationIntent(body.type, body.metadata)
          val metadataForStorage = notificationMetadataForStorage(body.metadata, operationId, incomingIsLoading, incomingIsResult)
          val requestedNotificationId = body.id.ifBlank {
            operationId?.let { if (incomingIsLoading) "loading_${userId}_$it" else "operation_${userId}_$it" }
              ?: "${now}_${body.type.name}_${body.message.hashCode()}"
          }
          val replacementNotificationId = notificationReplacementIdFromMetadata(metadataForStorage)

          val saved = newSuspendedTransaction(aitaServerIoContext) {
            cleanupNotificationsInsideTransaction(now)

            val existingById = Notifications
              .selectAll()
              .where { (Notifications.id eq requestedNotificationId) and (Notifications.userId eq userId) }
              .firstOrNull()

            val existingByReplacementId = replacementNotificationId?.let { replacementId ->
              Notifications
                .selectAll()
                .where { (Notifications.id eq replacementId) and (Notifications.userId eq userId) and (Notifications.isActive eq true) }
                .firstOrNull()
            }
            val existingByOperation = existingNotificationForIncomingOperationInsideTransaction(userId, operationId, incomingIsLoading, incomingIsResult)

            val existingDuplicate = existingByReplacementId ?: existingByOperation ?: existingById ?: Notifications
              .selectAll()
              .where {
                (Notifications.userId eq userId) and
                   (Notifications.message eq body.message) and
                   (Notifications.type eq body.type.name) and
                   (Notifications.category eq category) and
                   (Notifications.notificationSource eq source) and
                   (Notifications.createdAtMillis greaterEq dedupeCutoff) and
                   (if (storeId == null) Notifications.storeId.isNull() else Notifications.storeId eq storeId)
              }
              .orderBy(Notifications.createdAtMillis, SortOrder.DESC)
              .limit(1)
              .firstOrNull()

            val finalNotificationId = existingDuplicate?.get(Notifications.id) ?: requestedNotificationId

            if (existingDuplicate != null) {
              Notifications.update({ (Notifications.id eq finalNotificationId) and (Notifications.userId eq userId) }) {
                it[title] = body.title
                it[message] = body.message
                it[type] = body.type.name
                it[Notifications.category] = category
                it[notificationSource] = source
                it[metadata] = metadataForStorage
                it[Notifications.storeId] = storeId
                it[Notifications.createdAtMillis] = body.createdAtMillis.takeIf { value -> value > 0L } ?: now
                it[shownAtMillis] = body.shownAtMillis.takeIf { value -> value > 0L } ?: now
                it[readAtMillis] = body.readAtMillis
                it[isActive] = true
              }
            } else {
              Notifications.insert {
                it[id] = finalNotificationId
                it[Notifications.userId] = userId
                it[Notifications.storeId] = storeId
                it[title] = body.title
                it[message] = body.message
                it[type] = body.type.name
                it[Notifications.category] = category
                it[notificationSource] = source
                it[metadata] = metadataForStorage
                it[createdAtMillis] = body.createdAtMillis.takeIf { value -> value > 0L } ?: now
                it[shownAtMillis] = body.shownAtMillis.takeIf { value -> value > 0L } ?: now
                it[readAtMillis] = body.readAtMillis
                it[isActive] = true
              }
            }

            Notifications
              .selectAll()
              .where { (Notifications.id eq finalNotificationId) and (Notifications.userId eq userId) }
              .single()
              .toNotificationDataModel()
          }

          RealtimeServerBus.publish(
            entity = "notifications",
            storeId = saved.storeId,
            reason = operationId?.let { "operation_notification" } ?: "notification"
          )
          call.genericResponse(HttpStatusCode.Created, saved)
        }

        put("/read") {
          val userId = call.checkPrincipal() ?: return@put
          val ids = runCatching { call.receiveAita<List<String>>() }.getOrElse {
            val one = call.receiveTextAita().trim().trim('"')
            listOf(one)
          }.filter { it.isNotBlank() }
          val now = System.currentTimeMillis()

          val updated = newSuspendedTransaction(aitaServerIoContext) {
            if (ids.isNotEmpty()) {
              Notifications.update({
                (Notifications.userId eq userId) and
                   (Notifications.id inList ids) and
                   (Notifications.isActive eq true)
              }) {
                it[readAtMillis] = now
              }
            }

            Notifications
              .selectAll()
              .where { (Notifications.userId eq userId) and (Notifications.isActive eq true) }
              .orderBy(Notifications.createdAtMillis to SortOrder.DESC)
              .limit(500)
              .map { it.toNotificationDataModel() }
          }

          RealtimeServerBus.publish(entity = "notifications", reason = "notification_read")
          call.genericResponse(HttpStatusCode.OK, updated)
        }
      }
    }

    route("/generic") {
      authenticate("auth-jwt") {
        route("/goodsCategories") {
          get("/get") {
            val userId = call.checkPrincipal() ?: return@get

            val genericGoodsCategories: Pair<Int, List<GenericGoodsCategoryDataModel>?> =
              newSuspendedTransaction(aitaServerIoContext) {
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
                      alias = it[GenericGoodsCategories.alias],
                      description = it[GenericGoodsCategories.description],
                      quantityUnitId = it[GenericGoodsCategories.quantityUnitId],
                      imagePaths = it[GenericGoodsCategories.imagePaths]
                    )
                  }

                0 to matches
              }

            when {
              genericGoodsCategories.first == 1 -> call.respondAitaUnauthorized()
              else -> {
                call.genericResponse(
                  HttpStatusCode.OK,
                  payload = genericGoodsCategories.second.orEmpty()
                )
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

            val barcodeCandidates = genericGoodsBarcodeCandidates(call.request.header("barcode"))
            val queryText = call.request.queryParameters["q"]
              ?.trim()
              ?.takeIf { it.isNotBlank() }
            val categoryFilterIds = (call.request.queryParameters["categoryIds"]
              ?: call.request.queryParameters["categoryId"])
              .orEmpty()
              .split(',')
              .map { it.trim() }
              .filter { it.isNotBlank() }
              .toSet()
            val limit = call.request.queryParameters["limit"]
              ?.toIntOrNull()
              ?.coerceIn(1, 200)
              ?: if (barcodeCandidates.isNotEmpty()) 40 else 80
            val offset = call.request.queryParameters["offset"]
              ?.toIntOrNull()
              ?.coerceAtLeast(0)
              ?: 0

            val genericGoodsItems: Pair<Int, List<GenericGoodsItemDataModel>?> =
              newSuspendedTransaction(aitaServerIoContext) {
                val noUser = Users.select(Users.id).where { Users.id eq userId }.empty()

                if (noUser)
                  return@newSuspendedTransaction 1 to null

                val matches = GenericGoodsItems
                  .selectAll()
                  .toList()
                  .map { it.toGenericGoodsItemDataModel() }
                  .filter { item ->
                    val barcodeMatches = barcodeCandidates.isEmpty() ||
                       item.genericGoodsBarcodeMatchScore(barcodeCandidates) > 0
                    val categoryMatches = categoryFilterIds.isEmpty() ||
                       item.categoryIds.orEmpty().any { it in categoryFilterIds }
                    barcodeMatches && categoryMatches && item.matchesGenericGoodsSearchQuery(queryText)
                  }
                  .sortedWith(
                    compareByDescending<GenericGoodsItemDataModel> { it.genericGoodsBarcodeMatchScore(barcodeCandidates) }
                      .thenBy { item -> item.name.firstOrNull()?.value.orEmpty().lowercase(Locale.ROOT) }
                      .thenBy { item -> item.id }
                  )
                  .drop(offset)
                  .take(limit)

                0 to matches
              }

            when {
              genericGoodsItems.first == 1 -> call.respondAitaUnauthorized()
              else -> {
                call.genericResponse(
                  HttpStatusCode.OK,
                  payload = genericGoodsItems.second.orEmpty()
                )
              }
            }
          }
        }
      }
    }

    get("/config/global") {
      call.respondText(
        text = call.application.buildGlobalConfigurationJson(),
        contentType = ContentType.Application.Json,
        status = HttpStatusCode.OK
      )
    }

    get("/res/string") {
      call.respondStaticJsonFile(assetsRootPath.resolve("values/strings.json"))
    }

    get("/res/dimension") {
      call.respondStaticJsonFile(assetsRootPath.resolve("values/dimensions.json"))
    }

    get("/res/color") {
      call.respondStaticJsonFile(assetsRootPath.resolve("values/colors.json"))
    }

    get("/res/drawableConfig") {
      call.respondStaticJsonFile(assetsRootPath.resolve("drawable/drawables.json"))
    }

    staticFiles("/res/drawable", assetsRootPath.resolve("drawable").toFile())

    route("/stock") {
      authenticate("auth-jwt") {
        get("/get") {
          val userId = call.checkPrincipal() ?: return@get
          val storeId = call.headerUuid("store_id")
            ?: return@get call.respondAitaUnauthorized()

          val result = newSuspendedTransaction(aitaServerIoContext) {
            if (!userCanUseStoreActionInsideTransaction(userId, storeId, STORE_PERMISSION_STOCK_READ, requireWorkshift = false))
              return@newSuspendedTransaction null

            val visibleStoreIds = stockVisibleStoreIdsInsideTransaction(storeId)

            StockItems
              .selectAll()
              .where {
                (StockItems.storeId inList visibleStoreIds) and
                   (StockItems.isActive eq true)
              }
              .orderBy(StockItems.updatedAtMillis, SortOrder.DESC)
              .map { it.toGoodsItemDataModel() }
          }

          result?.let {
            call.genericListResponse(
              status = HttpStatusCode.OK,
              payload = it
            )
          } ?: call.respondAitaUnauthorized()
        }

        get("/parent/get") {
          val userId = call.checkPrincipal() ?: return@get
          val storeId = call.headerUuid("store_id")
            ?: return@get call.respondAitaUnauthorized()
          val cleanQuery = call.request.queryParameters["q"]
            ?.trim()
            ?.takeIf { it.isNotBlank() }
          val limit = call.request.queryParameters["limit"]
            ?.toIntOrNull()
            ?.coerceIn(1, 200)
            ?: 80
          val offset = call.request.queryParameters["offset"]
            ?.toIntOrNull()
            ?.coerceAtLeast(0)
            ?: 0

          val result = newSuspendedTransaction(aitaServerIoContext) {
            if (!userCanUseStoreActionInsideTransaction(userId, storeId, STORE_PERMISSION_STOCK_READ, requireWorkshift = false))
              return@newSuspendedTransaction null

            val parentStoreId = Stores
              .select(Stores.parentStoreId)
              .where { Stores.id eq storeId }
              .singleOrNull()
              ?.get(Stores.parentStoreId)
              ?: return@newSuspendedTransaction emptyList<GoodsItemDataModel>()

            StockItems
              .selectAll()
              .where {
                (StockItems.storeId eq parentStoreId) and
                   (StockItems.isActive eq true)
              }
              .orderBy(StockItems.updatedAtMillis, SortOrder.DESC)
              .map { it.toGoodsItemDataModel() }
              .filter { item -> item.matchesParentStockSearchQuery(cleanQuery) }
              .drop(offset)
              .take(limit)
          }

          result?.let {
            call.genericListResponse(
              status = HttpStatusCode.OK,
              payload = it
            )
          } ?: call.respondAitaUnauthorized()
        }

        get("/history/get") {
          val userId = call.checkPrincipal() ?: return@get
          val storeId = call.headerUuid("store_id")
            ?: return@get call.respondAitaUnauthorized()
          val goodsItemId = call.headerUuid("goods_item_id")
            ?: return@get call.genericResponseNoPayload(HttpStatusCode.BadRequest, message = getResponse("13").message)

          val limit = call.request.queryParameters["limit"]
            ?.toIntOrNull()
            ?.coerceIn(1, 500)
            ?: 250
          val offset = call.request.queryParameters["offset"]
            ?.toIntOrNull()
            ?.coerceAtLeast(0)
            ?: 0

          val result = newSuspendedTransaction(aitaServerIoContext) {
            val canViewHistory = userCanUseStoreActionInsideTransaction(userId, storeId, STORE_PERMISSION_STOCK_HISTORY_VIEW, requireWorkshift = false) ||
               userCanUseStoreActionInsideTransaction(userId, storeId, STORE_PERMISSION_LOGS_VIEW, requireWorkshift = false)
            if (!canViewHistory)
              return@newSuspendedTransaction null

            val visibleStoreIds = stockVisibleStoreIdsInsideTransaction(storeId)
            val itemVisible = StockItems
              .select(StockItems.id)
              .where {
                (StockItems.id eq goodsItemId) and
                   (StockItems.storeId inList visibleStoreIds)
              }
              .empty()
              .not()

            if (!itemVisible)
              return@newSuspendedTransaction null

            val batchIds = StockBatchesV2
              .select(StockBatchesV2.id)
              .where {
                (StockBatchesV2.goodsItemId eq goodsItemId) and
                   (StockBatchesV2.storeId inList visibleStoreIds)
              }
              .map { it[StockBatchesV2.id].toString() }
              .toSet()

            OperationLogs
              .selectAll()
              .where {
                (OperationLogs.storeId inList visibleStoreIds) and
                   (OperationLogs.entityType inList listOf(OPERATION_LOG_ENTITY_STOCK_ITEM, OPERATION_LOG_ENTITY_STOCK_BATCH))
              }
              .orderBy(OperationLogs.createdAtMillis, SortOrder.DESC)
              .map { it.toOperationLogDataModel() }
              .filter { log -> log.matchesStockItemHistory(goodsItemId.toString(), batchIds) }
              .drop(offset)
              .take(limit)
          }

          result?.let {
            call.genericListResponse(
              status = HttpStatusCode.OK,
              payload = it
            )
          } ?: call.respondAitaUnauthorized()
        }


        post("/add") {
          val userId = call.checkPrincipal() ?: return@post
          val body = call.receiveAita<GoodsItemDataModel>()

          val inserted = newSuspendedTransaction(aitaServerIoContext) {
            val storeId = runCatching { UUID.fromString(body.storeId) }.getOrNull()
              ?: return@newSuspendedTransaction null

            if (!call.matchesInventoryContextStoreIdInsideTransaction(userId, storeId))
              return@newSuspendedTransaction null

            if (!userCanUseStoreActionInsideTransaction(userId, storeId, STORE_PERMISSION_STOCK_ITEM_CREATE, requireWorkshift = true))
              return@newSuspendedTransaction null

            val cleanBarcodeModels = body.cleanBarcodeModelsForStore(storeId)
            val cleanBarcodes = cleanBarcodeModels.cleanBarcodeStrings().ifEmpty { body.barcodes.cleanBarcodes() }

            if (cleanBarcodes.isEmpty())
              return@newSuspendedTransaction null

            if (barcodeClashesInsideTransaction(storeId, null, cleanBarcodeModels))
              return@newSuspendedTransaction null

            val sanitizedPromotions = body.promotions.sanitizedStockPromotions()
            val now = System.currentTimeMillis()
            val id = UUID.randomUUID()

            StockItems.insert {
              it[StockItems.id] = id
              it[StockItems.userId] = userId
              it[StockItems.storeId] = storeId

              it[StockItems.barcodes] = cleanBarcodes
              it[StockItems.barcodeModels] = cleanBarcodeModels
              it[StockItems.name] = body.name
              it[StockItems.description] = body.description

              it[StockItems.measurementUnitId] = body.measurementUnitId
              it[StockItems.categoryIds] = body.categoryIds

              it[StockItems.salePrices] = body.salePrices
              it[StockItems.returnPrices] = body.returnPrices
              it[StockItems.supplyPrices] = body.supplyPrices
              it[StockItems.wholesalePrices] = body.wholesalePrices
              it[StockItems.wholesaleMinQuantity] = body.wholesaleMinQuantity?.takeIf { quantity -> quantity.total > 0.0 }

              it[StockItems.genericExpirationPeriod] = body.genericExpirationPeriod?.takeIf { period -> period.isUsable }

              it[StockItems.isQuickItem] = body.isQuickItem
              it[StockItems.imagePaths] = body.imagePaths

              it[StockItems.activeShelfBatchId] = body.activeShelfBatchId
                ?.takeIf { value -> value.isNotBlank() }
                ?.let { value -> runCatching { UUID.fromString(value) }.getOrNull() }

              it[StockItems.promotions] = sanitizedPromotions

              it[StockItems.note] = body.note
              it[StockItems.noteLocalized] = body.noteLocalized
              it[StockItems.conditions] = body.conditions.map { condition -> condition.trim() }.filter { condition -> condition.isNotBlank() }.distinct()

              it[StockItems.createdAtMillis] = now
              it[StockItems.updatedAtMillis] = now
              it[StockItems.isActive] = true
            }

            val insertedRow = StockItems.selectAll().where { StockItems.id eq id }.single()
            val parentMirrorRow = mirrorBranchStockItemToParentInsideTransaction(
              branchItemRow = insertedRow,
              userId = userId,
              now = now
            )

            recordGenericGoodsContributionInsideTransaction(
              userId = userId,
              stockItemId = id,
              item = body.copy(
                id = id.toString(),
                userId = userId.toString(),
                storeId = storeId.toString(),
                barcodes = cleanBarcodes,
                barcodeModels = cleanBarcodeModels
              ),
              barcodeModels = cleanBarcodeModels,
              now = now
            )

            val insertedItemName = insertedRow.stockItemOperationLogName(cleanBarcodes.firstOrNull().orEmpty())
            insertOperationLogInsideTransaction(
              actorUserId = userId,
              storeId = storeId,
              action = OPERATION_LOG_ACTION_CREATED,
              entityType = OPERATION_LOG_ENTITY_STOCK_ITEM,
              entityId = id.toString(),
              title = simpleMessage(
                main = "Stock item added: $insertedItemName",
                en = "Stock item added: $insertedItemName",
                ru = "Товар добавлен: $insertedItemName",
                kk = "Тауар қосылды: $insertedItemName"
              ),
              details = stockItemChangeDetails(listOf("created", "name", "barcodes", "prices", "promotions", "conditions")),
              metadata = mapOf(
                "goods_item_id" to id.toString(),
                "goods_name" to insertedItemName,
                "barcode" to cleanBarcodes.joinToString(","),
                "changed_fields" to "created|name|barcodes|prices|promotions|conditions"
              ).filterValues { it.isNotBlank() },
              now = now
            )

            insertedRow.toGoodsItemDataModel() to parentMirrorRow?.get(StockItems.storeId)?.toString()
          }

          inserted?.let { (item, parentMirrorStoreId) ->
            publishStockRealtimeBundle(listOf(item.storeId, parentMirrorStoreId), "stock_item_added")
            call.genericResponse(
              status = HttpStatusCode.Created,
              payload = item,
              message = simpleMessage(
                main = "Goods item added",
                ru = "Товар добавлен",
                kk = "Тауар қосылды"
              )
            )
          } ?: call.genericResponseNoPayload(
            status = HttpStatusCode.Conflict,
            message = simpleMessage(
              main = "Invalid stock item or duplicated barcode",
              ru = "Некорректный товар или повторяющийся штрихкод",
              kk = "Қате тауар немесе қайталанған штрихкод"
            )
          )
        }

        put("/update") {
          val userId = call.checkPrincipal() ?: return@put
          val body = call.receiveAita<GoodsItemDataModel>()

          val updated = newSuspendedTransaction(aitaServerIoContext) {
            val id = runCatching { UUID.fromString(body.id) }.getOrNull()
              ?: return@newSuspendedTransaction null

            val storeId = runCatching { UUID.fromString(body.storeId) }.getOrNull()
              ?: return@newSuspendedTransaction null

            if (!call.matchesInventoryContextStoreIdInsideTransaction(userId, storeId))
              return@newSuspendedTransaction null

            val cleanBarcodeModels = body.cleanBarcodeModelsForStore(storeId)
            val cleanBarcodes = cleanBarcodeModels.cleanBarcodeStrings().ifEmpty { body.barcodes.cleanBarcodes() }

            if (cleanBarcodes.isEmpty())
              return@newSuspendedTransaction null

            if (barcodeClashesInsideTransaction(storeId, id, cleanBarcodeModels))
              return@newSuspendedTransaction null

            val goodsItemRow = StockItems
              .selectAll()
              .where {
                (StockItems.id eq id) and
                   (StockItems.storeId eq storeId)
              }
              .firstOrNull()
              ?: return@newSuspendedTransaction null

            val requestedActiveShelfBatchId = body.activeShelfBatchId
              ?.takeIf { value -> value.isNotBlank() }
              ?.let { value -> runCatching { UUID.fromString(value) }.getOrNull()?.toString() }
            val existingActiveShelfBatchId = goodsItemRow[StockItems.activeShelfBatchId]?.toString()
            val requestedConditions = body.conditions
              .map { condition -> condition.trim() }
              .filter { condition -> condition.isNotBlank() }
              .distinct()
            val existingConditions = goodsItemRow[StockItems.conditions]
              .map { condition -> condition.trim() }
              .filter { condition -> condition.isNotBlank() }
              .distinct()
            val requestedWholesaleMinQuantity = body.wholesaleMinQuantity?.takeIf { quantity -> quantity.total > 0.0 }
            val requestedGenericExpirationPeriod = body.genericExpirationPeriod?.takeIf { period -> period.isUsable }
            val sanitizedPromotions = body.promotions.sanitizedStockPromotions()
            val changedFields = stockItemChangedFieldsInsideTransaction(
              previousRow = goodsItemRow,
              cleanBarcodes = cleanBarcodes,
              cleanBarcodeModels = cleanBarcodeModels,
              requestedActiveShelfBatchId = requestedActiveShelfBatchId,
              requestedWholesaleMinQuantity = requestedWholesaleMinQuantity,
              requestedGenericExpirationPeriod = requestedGenericExpirationPeriod,
              requestedConditions = requestedConditions,
              sanitizedPromotions = sanitizedPromotions,
              body = body
            )
            val requestedCoreMatchesExisting = cleanBarcodes.toSet() == goodsItemRow.stockBarcodeValues().toSet() &&
               cleanBarcodeModels == goodsItemRow.stockBarcodeModels() &&
               body.name == goodsItemRow[StockItems.name] &&
               body.description == goodsItemRow[StockItems.description] &&
               body.measurementUnitId == goodsItemRow[StockItems.measurementUnitId] &&
               body.categoryIds == goodsItemRow[StockItems.categoryIds] &&
               body.salePrices == goodsItemRow[StockItems.salePrices] &&
               body.returnPrices == goodsItemRow[StockItems.returnPrices] &&
               body.supplyPrices == goodsItemRow[StockItems.supplyPrices] &&
               body.wholesalePrices == goodsItemRow[StockItems.wholesalePrices] &&
               requestedWholesaleMinQuantity == goodsItemRow[StockItems.wholesaleMinQuantity] &&
               requestedGenericExpirationPeriod == goodsItemRow[StockItems.genericExpirationPeriod] &&
               body.isQuickItem == goodsItemRow[StockItems.isQuickItem] &&
               body.imagePaths == goodsItemRow[StockItems.imagePaths] &&
               requestedActiveShelfBatchId == existingActiveShelfBatchId &&
               body.note == goodsItemRow[StockItems.note] &&
               body.noteLocalized == goodsItemRow[StockItems.noteLocalized] &&
               requestedConditions == existingConditions &&
               body.isActive == goodsItemRow[StockItems.isActive]
            val canEditStockItem = userCanUseStoreActionInsideTransaction(userId, storeId, STORE_PERMISSION_STOCK_ITEM_EDIT, requireWorkshift = true)
            val canManageOnlyPromotions = userCanUseStoreActionInsideTransaction(userId, storeId, STORE_PERMISSION_STOCK_PROMOTIONS_MANAGE, requireWorkshift = true) && requestedCoreMatchesExisting

            if (!canEditStockItem && !canManageOnlyPromotions)
              return@newSuspendedTransaction null

            if (body.name != goodsItemRow[StockItems.name] || cleanBarcodes.toSet() != goodsItemRow[StockItems.barcodes].toSet()) {
              preserveGoodsItemNameInTransactionsInsideTransaction(storeId, goodsItemRow)
            }

            val now = Instant.now().toEpochMilli()

            val affected = StockItems.update({
              (StockItems.id eq id) and
                 (StockItems.storeId eq storeId)
            }) {
              it[StockItems.barcodes] = cleanBarcodes
              it[StockItems.barcodeModels] = cleanBarcodeModels
              it[StockItems.name] = body.name
              it[StockItems.description] = body.description

              it[StockItems.measurementUnitId] = body.measurementUnitId
              it[StockItems.categoryIds] = body.categoryIds

              it[StockItems.salePrices] = body.salePrices
              it[StockItems.returnPrices] = body.returnPrices
              it[StockItems.supplyPrices] = body.supplyPrices
              it[StockItems.wholesalePrices] = body.wholesalePrices
              it[StockItems.wholesaleMinQuantity] = body.wholesaleMinQuantity?.takeIf { quantity -> quantity.total > 0.0 }

              it[StockItems.genericExpirationPeriod] = body.genericExpirationPeriod?.takeIf { period -> period.isUsable }

              it[StockItems.isQuickItem] = body.isQuickItem
              it[StockItems.imagePaths] = body.imagePaths

              it[StockItems.activeShelfBatchId] = body.activeShelfBatchId
                ?.takeIf { value -> value.isNotBlank() }
                ?.let { value -> runCatching { UUID.fromString(value) }.getOrNull() }

              it[StockItems.promotions] = sanitizedPromotions

              it[StockItems.note] = body.note
              it[StockItems.noteLocalized] = body.noteLocalized
              it[StockItems.conditions] = body.conditions.map { condition -> condition.trim() }.filter { condition -> condition.isNotBlank() }.distinct()
              it[StockItems.updatedAtMillis] = now
              it[StockItems.isActive] = body.isActive
            }

            if (affected <= 0)
              return@newSuspendedTransaction null

            val updatedRow = StockItems.selectAll().where { StockItems.id eq id }.single()
            val parentMirrorRow = mirrorBranchStockItemToParentInsideTransaction(
              branchItemRow = updatedRow,
              previousBranchItemRow = goodsItemRow,
              userId = userId,
              now = now
            )

            if (changedFields.isNotEmpty()) {
              val updatedItemName = updatedRow.stockItemOperationLogName(goodsItemRow.stockItemOperationLogName(id.toString()))
              insertOperationLogInsideTransaction(
                actorUserId = userId,
                storeId = storeId,
                action = OPERATION_LOG_ACTION_UPDATED,
                entityType = OPERATION_LOG_ENTITY_STOCK_ITEM,
                entityId = id.toString(),
                title = simpleMessage(
                  main = "Stock item updated: $updatedItemName",
                  en = "Stock item updated: $updatedItemName",
                  ru = "Товар обновлён: $updatedItemName",
                  kk = "Тауар жаңартылды: $updatedItemName"
                ),
                details = stockItemChangeDetails(changedFields),
                metadata = mapOf(
                  "goods_item_id" to id.toString(),
                  "goods_name" to updatedItemName,
                  "barcode" to updatedRow.stockItemOperationLogBarcodeText(),
                  "changed_fields" to changedFields.joinToString("|")
                ).filterValues { it.isNotBlank() },
                now = now
              )
            }

            updatedRow.toGoodsItemDataModel() to parentMirrorRow?.get(StockItems.storeId)?.toString()
          }

          updated?.let { (item, parentMirrorStoreId) ->
            publishStockRealtimeBundle(listOf(item.storeId, parentMirrorStoreId), "stock_item_updated")
            call.genericResponse(
              status = HttpStatusCode.OK,
              payload = item,
              message = simpleMessage(
                main = "Goods item updated",
                ru = "Товар обновлён",
                kk = "Тауар жаңартылды"
              )
            )
          } ?: call.genericResponseNoPayload(
            status = HttpStatusCode.Conflict,
            message = simpleMessage(
              main = "Invalid stock item or duplicated barcode",
              ru = "Некорректный товар или повторяющийся штрихкод",
              kk = "Қате тауар немесе қайталанған штрихкод"
            )
          )
        }

        delete("/delete") {
          val userId = call.checkPrincipal() ?: return@delete
          val rawId = call.receiveAita<String>()
          val storeId = call.headerUuid("store_id")
            ?: return@delete call.respondAitaUnauthorized()

          val deletedId = newSuspendedTransaction(aitaServerIoContext) {
            if (!call.matchesInventoryContextStoreIdInsideTransaction(userId, storeId))
              return@newSuspendedTransaction null

            if (!userCanUseStoreActionInsideTransaction(userId, storeId, STORE_PERMISSION_STOCK_ITEM_DELETE, requireWorkshift = true))
              return@newSuspendedTransaction null

            val id = runCatching { UUID.fromString(rawId) }.getOrNull()
              ?: return@newSuspendedTransaction null

            val goodsItemRow = StockItems
              .selectAll()
              .where {
                (StockItems.id eq id) and
                   (StockItems.storeId eq storeId)
              }
              .firstOrNull()
              ?: return@newSuspendedTransaction null

            preserveGoodsItemNameInTransactionsInsideTransaction(storeId, goodsItemRow)

            val deletedItemName = goodsItemRow.stockItemOperationLogName(id.toString())
            insertOperationLogInsideTransaction(
              actorUserId = userId,
              storeId = storeId,
              action = OPERATION_LOG_ACTION_DELETED,
              entityType = OPERATION_LOG_ENTITY_STOCK_ITEM,
              entityId = id.toString(),
              title = simpleMessage(
                main = "Stock item deleted: $deletedItemName",
                en = "Stock item deleted: $deletedItemName",
                ru = "Товар удалён: $deletedItemName",
                kk = "Тауар жойылды: $deletedItemName"
              ),
              details = simpleMessage(
                main = "Removed stock item and its batch links from the active catalog.",
                en = "Removed stock item and its batch links from the active catalog.",
                ru = "Товар и его связи с партиями удалены из активного каталога.",
                kk = "Тауар және оның партиялармен байланыстары белсенді каталогтан жойылды."
              ),
              metadata = mapOf(
                "goods_item_id" to id.toString(),
                "goods_name" to deletedItemName,
                "barcode" to goodsItemRow.stockItemOperationLogBarcodeText(),
                "changed_fields" to "deleted"
              ).filterValues { it.isNotBlank() }
            )

            val affected = StockItems.deleteWhere {
              (StockItems.id eq id) and
                 (StockItems.storeId eq storeId)
            }

            if (affected <= 0)
              return@newSuspendedTransaction null

            rawId
          }

          deletedId?.let {
            publishStockRealtimeBundle(storeId.toString(), "stock_item_deleted")
            call.genericResponse(
              status = HttpStatusCode.OK,
              payload = it,
              message = getResponse("16").message
            )
          } ?: call.respondAitaUnauthorized()
        }
      }
    }

    route("/stockBatches") {
      authenticate("auth-jwt") {
        get("/get") {
          val userId = call.checkPrincipal() ?: return@get
          val storeId = call.headerUuid("store_id")
            ?: return@get call.respondAitaUnauthorized()

          val result = newSuspendedTransaction(aitaServerIoContext) {
            if (!userCanUseStoreActionInsideTransaction(userId, storeId, STORE_PERMISSION_STOCK_READ, requireWorkshift = false))
              return@newSuspendedTransaction null

            val visibleStoreIds = stockVisibleStoreIdsInsideTransaction(storeId)

            StockBatchesV2
              .selectAll()
              .where {
                (StockBatchesV2.storeId inList visibleStoreIds) and
                   (StockBatchesV2.isActive eq true)
              }
              .map { it.toGoodsBatchDataModel() }
          }

          result?.let {
            call.genericListResponse(
              status = HttpStatusCode.OK,
              payload = it
            )
          } ?: call.genericResponseNoPayload(HttpStatusCode.Forbidden, getResponse("665").message)
        }

        get("/branchAvailability") {
          val userId = call.checkPrincipal() ?: return@get
          val storeId = call.headerUuid("store_id")
            ?: return@get call.respondAitaUnauthorized()
          val goodsItemId = call.headerUuid("goods_item_id")
            ?: return@get call.genericResponseNoPayload(HttpStatusCode.BadRequest, message = getResponse("13").message)

          val availability = newSuspendedTransaction(aitaServerIoContext) {
            if (!userCanUseStoreActionInsideTransaction(userId, storeId, STORE_PERMISSION_STOCK_READ, requireWorkshift = false))
              return@newSuspendedTransaction null

            buildStockBranchAvailabilityInsideTransaction(storeId, goodsItemId)
          }

          availability?.let {
            call.genericResponse(
              status = HttpStatusCode.OK,
              payload = it,
              message = getResponse("73").message
            )
          } ?: call.respondAitaUnauthorized()
        }

        post("/move") {
          val userId = call.checkPrincipal() ?: return@post
          val request = call.receiveAita<StockBatchMoveRequestDataModel>()

          val result = newSuspendedTransaction(aitaServerIoContext) {
            val sourceStoreId = runCatching { UUID.fromString(request.sourceStoreId) }.getOrNull()
              ?: return@newSuspendedTransaction null
            val destinationStoreId = runCatching { UUID.fromString(request.destinationStoreId) }.getOrNull()
              ?: return@newSuspendedTransaction null
            val sourceGoodsItemId = runCatching { UUID.fromString(request.sourceGoodsItemId) }.getOrNull()
              ?: return@newSuspendedTransaction null
            val sourceBatchId = runCatching { UUID.fromString(request.sourceBatchId) }.getOrNull()
              ?: return@newSuspendedTransaction null
            val actorStoreId = request.actorStoreId
              ?.let { raw -> runCatching { UUID.fromString(raw) }.getOrNull() }
              ?: call.headerUuid("store_id")
              ?: sourceStoreId

            if (sourceStoreId == destinationStoreId)
              return@newSuspendedTransaction null

            val rootSourceStoreId = rootStoreIdForAccessInsideTransaction(sourceStoreId)
            val rootDestinationStoreId = rootStoreIdForAccessInsideTransaction(destinationStoreId)
            val rootActorStoreId = rootStoreIdForAccessInsideTransaction(actorStoreId)

            if (rootSourceStoreId != rootDestinationStoreId || rootActorStoreId != rootSourceStoreId)
              return@newSuspendedTransaction null

            if (!call.matchesAnyInventoryContextStoreIdInsideTransaction(userId, setOf(sourceStoreId, destinationStoreId, actorStoreId)))
              return@newSuspendedTransaction null

            val actorIsParentStore = actorStoreId == rootSourceStoreId
            val sourceIsBranch = sourceStoreId != rootSourceStoreId
            val destinationIsBranch = destinationStoreId != rootSourceStoreId
            val requiresAcceptance = sourceIsBranch && destinationIsBranch && !actorIsParentStore

            if (!userCanUseStoreActionInsideTransaction(userId, sourceStoreId, STORE_PERMISSION_STOCK_BATCH_MOVE))
              return@newSuspendedTransaction null

            if (requiresAcceptance) {
              if (!userHasStoreAccessInsideTransaction(userId, destinationStoreId))
                return@newSuspendedTransaction null
            } else if (!userCanUseStoreActionInsideTransaction(userId, destinationStoreId, STORE_PERMISSION_STOCK_BATCH_MOVE)) {
              return@newSuspendedTransaction null
            }

            val sourceItemRow = StockItems
              .selectAll()
              .where {
                (StockItems.id eq sourceGoodsItemId) and
                   (StockItems.storeId eq sourceStoreId) and
                   (StockItems.isActive eq true)
              }
              .singleOrNull()
              ?: return@newSuspendedTransaction null

            val sourceBatchRow = StockBatchesV2
              .selectAll()
              .where {
                (StockBatchesV2.id eq sourceBatchId) and
                   (StockBatchesV2.goodsItemId eq sourceGoodsItemId) and
                   (StockBatchesV2.storeId eq sourceStoreId) and
                   (StockBatchesV2.isActive eq true)
              }
              .singleOrNull()
              ?: return@newSuspendedTransaction null

            val sourceStatus = sourceBatchRow[StockBatchesV2.status]
            if (sourceStatus in setOf(
                StockBatchStatusDataModel.Ordered.name,
                StockBatchStatusDataModel.InTransit.name,
                StockBatchStatusDataModel.SoldOut.name,
                StockBatchStatusDataModel.WrittenOff.name,
                StockBatchStatusDataModel.Deleted.name
              )
            ) return@newSuspendedTransaction null

            val sourceQuantity = sourceBatchRow[StockBatchesV2.quantity]
            val moveQuantity = sourceQuantity.copy(
              total = sourceQuantity.withTotalValue(request.quantity.total).total
            )

            if (moveQuantity.total <= 0.0 || sourceQuantity.total + 0.000001 < moveQuantity.total)
              return@newSuspendedTransaction null

            val now = System.currentTimeMillis()
            val destinationItemRow = findOrCloneDestinationStockItemInsideTransaction(
              sourceItemRow = sourceItemRow,
              destinationStoreId = destinationStoreId,
              userId = userId,
              now = now
            )
            val destinationGoodsItemId = destinationItemRow[StockItems.id]

            val remainingQuantity = sourceQuantity.copy(
              total = sourceQuantity.withTotalValue(sourceQuantity.total - moveQuantity.total).total
            )
            val sourceNextStatus = if (remainingQuantity.total <= 0.0) {
              StockBatchStatusDataModel.SoldOut.name
            } else {
              sourceBatchRow[StockBatchesV2.status]
            }

            StockBatchesV2.update({ StockBatchesV2.id eq sourceBatchId }) {
              it[StockBatchesV2.quantity] = remainingQuantity
              it[StockBatchesV2.status] = sourceNextStatus
              it[StockBatchesV2.updatedAtMillis] = now
            }

            val destinationBatchId = UUID.randomUUID()
            val destinationStatus = if (requiresAcceptance) {
              StockBatchStatusDataModel.InTransit.name
            } else {
              StockBatchStatusDataModel.Delivered.name
            }
            StockBatchesV2.insert {
              it[StockBatchesV2.id] = destinationBatchId
              it[StockBatchesV2.goodsItemId] = destinationGoodsItemId
              it[StockBatchesV2.userId] = userId
              it[StockBatchesV2.storeId] = destinationStoreId
              it[StockBatchesV2.supplierId] = sourceBatchRow[StockBatchesV2.supplierId]
              it[StockBatchesV2.supplierOrderId] = sourceBatchRow[StockBatchesV2.supplierOrderId]
              it[StockBatchesV2.quantity] = moveQuantity
              it[StockBatchesV2.supplyPrice] = sourceBatchRow[StockBatchesV2.supplyPrice]
              it[StockBatchesV2.salePriceOverride] = sourceBatchRow[StockBatchesV2.salePriceOverride]
              it[StockBatchesV2.returnPriceOverride] = sourceBatchRow[StockBatchesV2.returnPriceOverride]
              it[StockBatchesV2.wholesalePriceOverride] = sourceBatchRow[StockBatchesV2.wholesalePriceOverride]
              it[StockBatchesV2.deliveredAtMillis] = if (requiresAcceptance) null else (sourceBatchRow[StockBatchesV2.deliveredAtMillis] ?: now)
              it[StockBatchesV2.manufacturedAtMillis] = sourceBatchRow[StockBatchesV2.manufacturedAtMillis]
              it[StockBatchesV2.expirationDateMillis] = sourceBatchRow[StockBatchesV2.expirationDateMillis]
              it[StockBatchesV2.discounts] = sourceBatchRow[StockBatchesV2.discounts]
              it[StockBatchesV2.promotions] = sourceBatchRow[StockBatchesV2.promotions]
              it[StockBatchesV2.shelfPosition] = sourceBatchRow[StockBatchesV2.shelfPosition]
              it[StockBatchesV2.shelfPriority] = activePhysicalBatchRowsForGoodsItemInsideTransaction(destinationStoreId, destinationGoodsItemId).size
              it[StockBatchesV2.status] = destinationStatus
              it[StockBatchesV2.additionalNotes] = request.note?.takeIf { note -> note.isNotBlank() }
              it[StockBatchesV2.additionalNotesLocalized] = request.note?.takeIf { note -> note.isNotBlank() }?.let { note -> listOf(LocalizedStringDataModel("main", note)) } ?: emptyList()
              it[StockBatchesV2.createdAtMillis] = now
              it[StockBatchesV2.updatedAtMillis] = now
              it[StockBatchesV2.createdByUserId] = userId
              it[StockBatchesV2.isActive] = true
            }

            if (!requiresAcceptance && destinationItemRow[StockItems.activeShelfBatchId] == null) {
              StockItems.update({ StockItems.id eq destinationGoodsItemId }) {
                it[StockItems.activeShelfBatchId] = destinationBatchId
                it[StockItems.updatedAtMillis] = now
              }
            }

            updateGoodsItemActiveShelfBatchInsideTransaction(sourceGoodsItemId, sourceStoreId, now)
            if (!requiresAcceptance) updateGoodsItemActiveShelfBatchInsideTransaction(destinationGoodsItemId, destinationStoreId, now)

            val movementId = UUID.randomUUID()
            StockBatchMovements.insert {
              it[StockBatchMovements.id] = movementId
              it[StockBatchMovements.rootStoreId] = rootSourceStoreId
              it[StockBatchMovements.sourceStoreId] = sourceStoreId
              it[StockBatchMovements.destinationStoreId] = destinationStoreId
              it[StockBatchMovements.sourceGoodsItemId] = sourceGoodsItemId
              it[StockBatchMovements.destinationGoodsItemId] = destinationGoodsItemId
              it[StockBatchMovements.sourceBatchId] = sourceBatchId
              it[StockBatchMovements.destinationBatchId] = destinationBatchId
              it[StockBatchMovements.userId] = userId
              it[StockBatchMovements.quantity] = moveQuantity
              it[StockBatchMovements.note] = request.note?.takeIf { note -> note.isNotBlank() }
              it[StockBatchMovements.movedAtMillis] = now
              it[StockBatchMovements.status] = if (requiresAcceptance) StockBatchMovementStatusDataModel.PendingAcceptance.name else StockBatchMovementStatusDataModel.Accepted.name
              it[StockBatchMovements.acceptedByUserId] = if (requiresAcceptance) null else userId
              it[StockBatchMovements.acceptedAtMillis] = if (requiresAcceptance) null else now
              it[StockBatchMovements.decisionNote] = null
            }

            val sourceItemName = sourceItemRow.stockItemOperationLogName(sourceGoodsItemId.toString())
            val destinationItemName = destinationItemRow.stockItemOperationLogName(destinationGoodsItemId.toString())
            val moveQuantityText = moveQuantity.operationLogQuantityText(sourceItemRow[StockItems.measurementUnitId])
            val moveMetadata = mapOf(
              "movement_id" to movementId.toString(),
              "source_goods_item_id" to sourceGoodsItemId.toString(),
              "destination_goods_item_id" to destinationGoodsItemId.toString(),
              "source_batch_id" to sourceBatchId.toString(),
              "destination_batch_id" to destinationBatchId.toString(),
              "goods_item_id" to sourceGoodsItemId.toString(),
              "batch_id" to sourceBatchId.toString(),
              "quantity" to moveQuantity.total.toString(),
              "quantity_unit" to moveQuantity.id.ifBlank { sourceItemRow[StockItems.measurementUnitId] },
              "status" to if (requiresAcceptance) StockBatchMovementStatusDataModel.PendingAcceptance.name else StockBatchMovementStatusDataModel.Accepted.name,
              "changed_fields" to "quantity|movement"
            ).filterValues { it.isNotBlank() }
            insertOperationLogInsideTransaction(
              actorUserId = userId,
              storeId = sourceStoreId,
              action = OPERATION_LOG_ACTION_MOVED,
              entityType = OPERATION_LOG_ENTITY_STOCK_BATCH,
              entityId = sourceBatchId.toString(),
              title = simpleMessage(
                main = "Batch moved out: $sourceItemName",
                en = "Batch moved out: $sourceItemName",
                ru = "Партия отправлена: $sourceItemName",
                kk = "Партия жіберілді: $sourceItemName"
              ),
              details = simpleMessage(
                main = "Quantity: $moveQuantityText",
                en = "Quantity: $moveQuantityText",
                ru = "Количество: $moveQuantityText",
                kk = "Саны: $moveQuantityText"
              ),
              metadata = moveMetadata,
              now = now
            )
            insertOperationLogInsideTransaction(
              actorUserId = userId,
              storeId = destinationStoreId,
              action = OPERATION_LOG_ACTION_MOVED,
              entityType = OPERATION_LOG_ENTITY_STOCK_BATCH,
              entityId = destinationBatchId.toString(),
              title = simpleMessage(
                main = if (requiresAcceptance) "Batch sent en route: $destinationItemName" else "Batch moved in: $destinationItemName",
                en = if (requiresAcceptance) "Batch sent en route: $destinationItemName" else "Batch moved in: $destinationItemName",
                ru = if (requiresAcceptance) "Партия в пути: $destinationItemName" else "Партия принята перемещением: $destinationItemName",
                kk = if (requiresAcceptance) "Партия жолда: $destinationItemName" else "Партия ауыстырумен қабылданды: $destinationItemName"
              ),
              details = simpleMessage(
                main = "Quantity: $moveQuantityText",
                en = "Quantity: $moveQuantityText",
                ru = "Количество: $moveQuantityText",
                kk = "Саны: $moveQuantityText"
              ),
              metadata = moveMetadata + mapOf(
                "goods_item_id" to destinationGoodsItemId.toString(),
                "batch_id" to destinationBatchId.toString(),
                "goods_name" to destinationItemName
              ),
              now = now
            )

            val sourceBatch = StockBatchesV2.selectAll().where { StockBatchesV2.id eq sourceBatchId }.single().toGoodsBatchDataModel()
            val destinationBatch = StockBatchesV2.selectAll().where { StockBatchesV2.id eq destinationBatchId }.single().toGoodsBatchDataModel()
            val sourceItem = StockItems.selectAll().where { StockItems.id eq sourceGoodsItemId }.single().toGoodsItemDataModel()
            val destinationItem = StockItems.selectAll().where { StockItems.id eq destinationGoodsItemId }.single().toGoodsItemDataModel()
            val movement = StockBatchMovements.selectAll().where { StockBatchMovements.id eq movementId }.single().toStockBatchMovementDataModel()
            val availability = buildStockBranchAvailabilityInsideTransaction(sourceStoreId, sourceGoodsItemId)
              ?: StockItemBranchAvailabilityDataModel()

            StockBatchMoveResultDataModel(
              sourceBatch = sourceBatch,
              destinationBatch = destinationBatch,
              sourceGoodsItem = sourceItem,
              destinationGoodsItem = destinationItem,
              movement = movement,
              availability = availability,
              requiresAcceptance = requiresAcceptance
            )
          }

          result?.let {
            publishStockRealtimeBundle(listOf(it.sourceBatch.storeId, it.destinationBatch.storeId), if (it.requiresAcceptance) "stock_batch_en_route" else "stock_batch_moved")
            call.genericResponse(
              status = HttpStatusCode.OK,
              payload = it,
              message = if (it.requiresAcceptance) {
                listOf(
                  LocalizedStringDataModel("main", "Batch sent en route. Receiving branch must accept it."),
                  LocalizedStringDataModel("en", "Batch sent en route. Receiving branch must accept it."),
                  LocalizedStringDataModel("ru", "Партия отправлена в пути. Принимающий филиал должен подтвердить получение."),
                  LocalizedStringDataModel("kk", "Партия жолға шықты. Қабылдайтын филиал қабылдауды растауы керек.")
                )
              } else getResponse("74").message
            )
          } ?: call.genericResponseNoPayload(
            status = HttpStatusCode.BadRequest,
            message = getResponse("75").message
          )
        }

        post("/decideMove") {
          val userId = call.checkPrincipal() ?: return@post
          val request = call.receiveAita<StockBatchMoveDecisionRequestDataModel>()

          val result = newSuspendedTransaction(aitaServerIoContext) {
            val movementId = runCatching { UUID.fromString(request.movementId) }.getOrNull()
              ?: return@newSuspendedTransaction null
            val movementRow = StockBatchMovements
              .selectAll()
              .where { StockBatchMovements.id eq movementId }
              .singleOrNull()
              ?: return@newSuspendedTransaction null

            if (movementRow[StockBatchMovements.status] != StockBatchMovementStatusDataModel.PendingAcceptance.name)
              return@newSuspendedTransaction null

            val sourceStoreId = movementRow[StockBatchMovements.sourceStoreId]
            val destinationStoreId = movementRow[StockBatchMovements.destinationStoreId]
            val rootStoreId = movementRow[StockBatchMovements.rootStoreId]
            val sourceGoodsItemId = movementRow[StockBatchMovements.sourceGoodsItemId]
            val destinationGoodsItemId = movementRow[StockBatchMovements.destinationGoodsItemId]
            val sourceBatchId = movementRow[StockBatchMovements.sourceBatchId]
            val destinationBatchId = movementRow[StockBatchMovements.destinationBatchId]
            val actorStoreId = call.headerUuid("store_id") ?: destinationStoreId

            if (!storesShareInventoryRootInsideTransaction(actorStoreId, destinationStoreId))
              return@newSuspendedTransaction null

            val canDecideFromDestination = userCanUseStoreActionInsideTransaction(userId, destinationStoreId, STORE_PERMISSION_STOCK_BATCH_TRANSFER_DECIDE)
            val canDecideFromParent = actorStoreId == rootStoreId && userCanUseStoreActionInsideTransaction(userId, rootStoreId, STORE_PERMISSION_STOCK_BATCH_TRANSFER_DECIDE)
            if (!canDecideFromDestination && !canDecideFromParent)
              return@newSuspendedTransaction null

            val destinationBatchRow = StockBatchesV2
              .selectAll()
              .where {
                (StockBatchesV2.id eq destinationBatchId) and
                   (StockBatchesV2.goodsItemId eq destinationGoodsItemId) and
                   (StockBatchesV2.storeId eq destinationStoreId) and
                   (StockBatchesV2.isActive eq true)
              }
              .singleOrNull()
              ?: return@newSuspendedTransaction null

            if (destinationBatchRow[StockBatchesV2.status] != StockBatchStatusDataModel.InTransit.name)
              return@newSuspendedTransaction null

            val now = System.currentTimeMillis()
            val decisionNote = request.note?.takeIf { it.isNotBlank() }

            if (request.accept) {
              StockBatchesV2.update({ StockBatchesV2.id eq destinationBatchId }) {
                it[StockBatchesV2.status] = StockBatchStatusDataModel.Delivered.name
                it[StockBatchesV2.deliveredAtMillis] = destinationBatchRow[StockBatchesV2.deliveredAtMillis] ?: now
                it[StockBatchesV2.updatedAtMillis] = now
              }

              val destinationItemRow = StockItems
                .selectAll()
                .where { StockItems.id eq destinationGoodsItemId }
                .singleOrNull()
                ?: return@newSuspendedTransaction null

              if (destinationItemRow[StockItems.activeShelfBatchId] == null) {
                StockItems.update({ StockItems.id eq destinationGoodsItemId }) {
                  it[StockItems.activeShelfBatchId] = destinationBatchId
                  it[StockItems.updatedAtMillis] = now
                }
              }

              StockBatchMovements.update({ StockBatchMovements.id eq movementId }) {
                it[StockBatchMovements.status] = StockBatchMovementStatusDataModel.Accepted.name
                it[StockBatchMovements.acceptedByUserId] = userId
                it[StockBatchMovements.acceptedAtMillis] = now
                it[StockBatchMovements.decisionNote] = decisionNote
              }
            } else {
              val movedQuantity = movementRow[StockBatchMovements.quantity]
              val sourceBatchRow = StockBatchesV2
                .selectAll()
                .where { StockBatchesV2.id eq sourceBatchId }
                .singleOrNull()

              if (sourceBatchRow != null) {
                val sourceQuantity = sourceBatchRow[StockBatchesV2.quantity]
                val restoredQuantity = sourceQuantity.copy(
                  total = sourceQuantity.withTotalValue(sourceQuantity.total + movedQuantity.total).total
                )
                val restoredStatus = when (sourceBatchRow[StockBatchesV2.status]) {
                  StockBatchStatusDataModel.SoldOut.name,
                  StockBatchStatusDataModel.Deleted.name -> StockBatchStatusDataModel.Delivered.name
                  else -> sourceBatchRow[StockBatchesV2.status]
                }
                StockBatchesV2.update({ StockBatchesV2.id eq sourceBatchId }) {
                  it[StockBatchesV2.quantity] = restoredQuantity
                  it[StockBatchesV2.status] = restoredStatus
                  it[StockBatchesV2.updatedAtMillis] = now
                  it[StockBatchesV2.isActive] = true
                }
              }

              StockBatchesV2.update({ StockBatchesV2.id eq destinationBatchId }) {
                it[StockBatchesV2.status] = StockBatchStatusDataModel.Deleted.name
                it[StockBatchesV2.isActive] = false
                it[StockBatchesV2.updatedAtMillis] = now
              }

              StockBatchMovements.update({ StockBatchMovements.id eq movementId }) {
                it[StockBatchMovements.status] = StockBatchMovementStatusDataModel.Declined.name
                it[StockBatchMovements.acceptedByUserId] = userId
                it[StockBatchMovements.acceptedAtMillis] = now
                it[StockBatchMovements.decisionNote] = decisionNote
              }
            }

            val decisionAction = if (request.accept) OPERATION_LOG_ACTION_ACCEPTED else OPERATION_LOG_ACTION_DECLINED
            val destinationItemNameForDecision = StockItems
              .selectAll()
              .where { StockItems.id eq destinationGoodsItemId }
              .singleOrNull()
              ?.stockItemOperationLogName(destinationGoodsItemId.toString())
              ?: destinationGoodsItemId.toString()
            val sourceItemNameForDecision = StockItems
              .selectAll()
              .where { StockItems.id eq sourceGoodsItemId }
              .singleOrNull()
              ?.stockItemOperationLogName(sourceGoodsItemId.toString())
              ?: sourceGoodsItemId.toString()
            val decisionQuantity = movementRow[StockBatchMovements.quantity]
            val decisionQuantityText = decisionQuantity.operationLogQuantityText()
            val decisionMetadata = mapOf(
              "movement_id" to movementId.toString(),
              "source_goods_item_id" to sourceGoodsItemId.toString(),
              "destination_goods_item_id" to destinationGoodsItemId.toString(),
              "source_batch_id" to sourceBatchId.toString(),
              "destination_batch_id" to destinationBatchId.toString(),
              "goods_item_id" to destinationGoodsItemId.toString(),
              "batch_id" to destinationBatchId.toString(),
              "goods_name" to destinationItemNameForDecision,
              "quantity" to decisionQuantity.total.toString(),
              "quantity_unit" to decisionQuantity.id,
              "status" to if (request.accept) StockBatchMovementStatusDataModel.Accepted.name else StockBatchMovementStatusDataModel.Declined.name,
              "changed_fields" to "movement_status|status"
            ).filterValues { it.isNotBlank() }
            insertOperationLogInsideTransaction(
              actorUserId = userId,
              storeId = destinationStoreId,
              action = decisionAction,
              entityType = OPERATION_LOG_ENTITY_STOCK_BATCH,
              entityId = destinationBatchId.toString(),
              title = simpleMessage(
                main = if (request.accept) "Incoming batch accepted: $destinationItemNameForDecision" else "Incoming batch declined: $destinationItemNameForDecision",
                en = if (request.accept) "Incoming batch accepted: $destinationItemNameForDecision" else "Incoming batch declined: $destinationItemNameForDecision",
                ru = if (request.accept) "Входящая партия принята: $destinationItemNameForDecision" else "Входящая партия отклонена: $destinationItemNameForDecision",
                kk = if (request.accept) "Кіріс партия қабылданды: $destinationItemNameForDecision" else "Кіріс партия қабылданбады: $destinationItemNameForDecision"
              ),
              details = simpleMessage(
                main = "Quantity: $decisionQuantityText" + (decisionNote?.let { " · Note: $it" } ?: ""),
                en = "Quantity: $decisionQuantityText" + (decisionNote?.let { " · Note: $it" } ?: ""),
                ru = "Количество: $decisionQuantityText" + (decisionNote?.let { " · Заметка: $it" } ?: ""),
                kk = "Саны: $decisionQuantityText" + (decisionNote?.let { " · Ескертпе: $it" } ?: "")
              ),
              metadata = decisionMetadata,
              now = now
            )
            if (!request.accept) {
              insertOperationLogInsideTransaction(
                actorUserId = userId,
                storeId = sourceStoreId,
                action = OPERATION_LOG_ACTION_DECLINED,
                entityType = OPERATION_LOG_ENTITY_STOCK_BATCH,
                entityId = sourceBatchId.toString(),
                title = simpleMessage(
                  main = "Batch move declined and returned: $sourceItemNameForDecision",
                  en = "Batch move declined and returned: $sourceItemNameForDecision",
                  ru = "Перемещение партии отклонено, остаток возвращён: $sourceItemNameForDecision",
                  kk = "Партия ауыстыруы қабылданбады, қалдық қайтарылды: $sourceItemNameForDecision"
                ),
                details = simpleMessage(
                  main = "Quantity: $decisionQuantityText" + (decisionNote?.let { " · Note: $it" } ?: ""),
                  en = "Quantity: $decisionQuantityText" + (decisionNote?.let { " · Note: $it" } ?: ""),
                  ru = "Количество: $decisionQuantityText" + (decisionNote?.let { " · Заметка: $it" } ?: ""),
                  kk = "Саны: $decisionQuantityText" + (decisionNote?.let { " · Ескертпе: $it" } ?: "")
                ),
                metadata = decisionMetadata + mapOf(
                  "goods_item_id" to sourceGoodsItemId.toString(),
                  "batch_id" to sourceBatchId.toString(),
                  "goods_name" to sourceItemNameForDecision
                ),
                now = now
              )
            }

            updateGoodsItemActiveShelfBatchInsideTransaction(sourceGoodsItemId, sourceStoreId, now)
            updateGoodsItemActiveShelfBatchInsideTransaction(destinationGoodsItemId, destinationStoreId, now)

            val sourceBatch = StockBatchesV2.selectAll().where { StockBatchesV2.id eq sourceBatchId }.single().toGoodsBatchDataModel()
            val destinationBatch = StockBatchesV2.selectAll().where { StockBatchesV2.id eq destinationBatchId }.single().toGoodsBatchDataModel()
            val sourceItem = StockItems.selectAll().where { StockItems.id eq sourceGoodsItemId }.single().toGoodsItemDataModel()
            val destinationItem = StockItems.selectAll().where { StockItems.id eq destinationGoodsItemId }.single().toGoodsItemDataModel()
            val movement = StockBatchMovements.selectAll().where { StockBatchMovements.id eq movementId }.single().toStockBatchMovementDataModel()
            val availability = buildStockBranchAvailabilityInsideTransaction(destinationStoreId, destinationGoodsItemId)
              ?: StockItemBranchAvailabilityDataModel()

            StockBatchMoveResultDataModel(
              sourceBatch = sourceBatch,
              destinationBatch = destinationBatch,
              sourceGoodsItem = sourceItem,
              destinationGoodsItem = destinationItem,
              movement = movement,
              availability = availability,
              requiresAcceptance = false
            )
          }

          result?.let {
            publishStockRealtimeBundle(listOf(it.sourceBatch.storeId, it.destinationBatch.storeId), if (it.movement.status == StockBatchMovementStatusDataModel.Accepted) "stock_batch_move_accepted" else "stock_batch_move_declined")
            call.genericResponse(
              status = HttpStatusCode.OK,
              payload = it,
              message = if (it.movement.status == StockBatchMovementStatusDataModel.Accepted) {
                listOf(
                  LocalizedStringDataModel("main", "Incoming batch accepted."),
                  LocalizedStringDataModel("en", "Incoming batch accepted."),
                  LocalizedStringDataModel("ru", "Входящая партия принята."),
                  LocalizedStringDataModel("kk", "Кіріс партия қабылданды.")
                )
              } else {
                listOf(
                  LocalizedStringDataModel("main", "Incoming batch declined and returned to source."),
                  LocalizedStringDataModel("en", "Incoming batch declined and returned to source."),
                  LocalizedStringDataModel("ru", "Входящая партия отклонена и возвращена источнику."),
                  LocalizedStringDataModel("kk", "Кіріс партия қабылданбады және бастапқы қоймаға қайтарылды.")
                )
              }
            )
          } ?: call.genericResponseNoPayload(
            status = HttpStatusCode.BadRequest,
            message = getResponse("75").message
          )
        }

        post("/add") {
          val userId = call.checkPrincipal() ?: return@post
          val bodies = call.receiveOneOrList<GoodsBatchDataModel>()

          val inserted = newSuspendedTransaction(aitaServerIoContext) {
            val result = mutableListOf<GoodsBatchDataModel>()
            val now = System.currentTimeMillis()

            batchAddLoop@ for (body in bodies) {
              val storeId = runCatching { UUID.fromString(body.storeId) }.getOrNull()
                ?: return@newSuspendedTransaction null

              val goodsItemId = runCatching { UUID.fromString(body.goodsItemId) }.getOrNull()
                ?: return@newSuspendedTransaction null

              if (!call.matchesInventoryContextStoreIdInsideTransaction(userId, storeId))
                return@newSuspendedTransaction null

              if (!userCanUseStoreActionInsideTransaction(userId, storeId, STORE_PERMISSION_STOCK_BATCH_CREATE, requireWorkshift = true))
                return@newSuspendedTransaction null

              val itemExists = StockItems
                .selectAll()
                .where {
                  (StockItems.id eq goodsItemId) and
                     (StockItems.storeId eq storeId) and
                     (StockItems.isActive eq true)
                }
                .empty()
                .not()

              if (!itemExists)
                return@newSuspendedTransaction null

              val requestedBatchId = body.id
                .trim()
                .takeIf { it.isNotBlank() }
                ?.let { rawId -> runCatching { UUID.fromString(rawId) }.getOrNull() }

              if (requestedBatchId != null) {
                val existingRow = StockBatchesV2
                  .selectAll()
                  .where { StockBatchesV2.id eq requestedBatchId }
                  .singleOrNull()

                if (existingRow != null) {
                  if (existingRow[StockBatchesV2.storeId] != storeId || existingRow[StockBatchesV2.goodsItemId] != goodsItemId) {
                    return@newSuspendedTransaction null
                  }

                  result += existingRow.toGoodsBatchDataModel()
                  continue@batchAddLoop
                }
              }

              val id = requestedBatchId ?: UUID.randomUUID()

              val nextSupplierId = body.supplierId.optionalUuidOrNull()
              val nextSupplierOrderId = body.supplierOrderId.optionalUuidOrNull()
              val sanitizedPromotions = body.promotions.sanitizedStockPromotions()
              val sanitizedAdditionalNotes = cleanOptionalText(body.additionalNotes)
              val sanitizedAdditionalNotesLocalized = cleanLocalizedValues(body.additionalNotesLocalized)

              StockBatchesV2.insert {
                it[StockBatchesV2.id] = id
                it[StockBatchesV2.goodsItemId] = goodsItemId
                it[StockBatchesV2.userId] = userId
                it[StockBatchesV2.storeId] = storeId

                it[StockBatchesV2.supplierId] = nextSupplierId
                it[StockBatchesV2.supplierOrderId] = nextSupplierOrderId

                it[StockBatchesV2.quantity] = body.quantity

                it[StockBatchesV2.supplyPrice] = body.supplyPrice
                it[StockBatchesV2.salePriceOverride] = body.salePriceOverride
                it[StockBatchesV2.returnPriceOverride] = body.returnPriceOverride
                it[StockBatchesV2.wholesalePriceOverride] = body.wholesalePriceOverride

                it[StockBatchesV2.deliveredAtMillis] = body.deliveredAtMillis ?: now
                it[StockBatchesV2.manufacturedAtMillis] = body.manufacturedAtMillis
                it[StockBatchesV2.expirationDateMillis] = body.expirationDateMillis

                it[StockBatchesV2.discounts] = body.discounts
                it[StockBatchesV2.promotions] = sanitizedPromotions

                it[StockBatchesV2.shelfPosition] = cleanOptionalText(body.shelfPosition)
                it[StockBatchesV2.shelfPriority] = body.shelfPriority

                it[StockBatchesV2.status] = body.status.name
                it[StockBatchesV2.additionalNotes] = sanitizedAdditionalNotes
                it[StockBatchesV2.additionalNotesLocalized] = sanitizedAdditionalNotesLocalized

                it[StockBatchesV2.createdAtMillis] = now
                it[StockBatchesV2.updatedAtMillis] = now
                it[StockBatchesV2.createdByUserId] = userId

                it[StockBatchesV2.isActive] = true
              }

              val item = StockItems
                .selectAll()
                .where { StockItems.id eq goodsItemId }
                .single()

              if (item[StockItems.activeShelfBatchId] == null) {
                StockItems.update({ StockItems.id eq goodsItemId }) {
                  it[StockItems.activeShelfBatchId] = id
                  it[StockItems.updatedAtMillis] = now
                }
              }

              nextSupplierId?.let { supplierId ->
                upsertSupplierGoodsPriceInsideTransaction(
                  userId = userId,
                  storeId = storeId,
                  supplierId = supplierId,
                  goodsItemId = goodsItemId,
                  supplyPrice = body.supplyPrice,
                  now = now
                )
              }

              val logText = stockBatchOperationLogTextInsideTransaction(
                action = OPERATION_LOG_ACTION_CREATED,
                batchId = id,
                goodsItemId = goodsItemId,
                batch = body.copy(id = id.toString())
              )

              insertOperationLogInsideTransaction(
                actorUserId = userId,
                storeId = storeId,
                action = OPERATION_LOG_ACTION_CREATED,
                entityType = OPERATION_LOG_ENTITY_STOCK_BATCH,
                entityId = id.toString(),
                title = logText.title,
                details = logText.details,
                metadata = logText.metadata,
                now = now
              )

              result += body.copy(
                id = id.toString(),
                userId = userId.toString(),
                storeId = storeId.toString(),
                supplierId = nextSupplierId?.toString(),
                supplierOrderId = nextSupplierOrderId?.toString(),
                deliveredAtMillis = body.deliveredAtMillis ?: now,
                promotions = sanitizedPromotions,
                shelfPosition = cleanOptionalText(body.shelfPosition),
                additionalNotes = sanitizedAdditionalNotes,
                additionalNotesLocalized = sanitizedAdditionalNotesLocalized,
                createdAtMillis = now,
                updatedAtMillis = now,
                createdByUserId = userId.toString(),
                isActive = true
              )
            }

            result
          }

          inserted?.let {
            publishStockRealtimeBundle(it.map { batch -> batch.storeId }, "stock_batch_added")
            call.genericResponse(
              status = HttpStatusCode.Created,
              payload = it,
              message = getResponse("17").message
            )
          } ?: call.respondAitaUnauthorized()
        }

        put("/update") {
          val userId = call.checkPrincipal() ?: return@put
          val bodies = call.receiveOneOrList<GoodsBatchDataModel>()

          val updated = newSuspendedTransaction(aitaServerIoContext) {
            val result = mutableListOf<GoodsBatchDataModel>()
            val now = System.currentTimeMillis()

            for (body in bodies) {
              val id = runCatching { UUID.fromString(body.id) }.getOrNull()
                ?: return@newSuspendedTransaction null

              val storeId = runCatching { UUID.fromString(body.storeId) }.getOrNull()
                ?: return@newSuspendedTransaction null

              val goodsItemId = runCatching { UUID.fromString(body.goodsItemId) }.getOrNull()
                ?: return@newSuspendedTransaction null

              if (!call.matchesInventoryContextStoreIdInsideTransaction(userId, storeId))
                return@newSuspendedTransaction null

              if (!userCanUseStoreActionInsideTransaction(userId, storeId, STORE_PERMISSION_STOCK_BATCH_EDIT, requireWorkshift = true))
                return@newSuspendedTransaction null

              val nextSupplierId = body.supplierId
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let { rawId -> runCatching { UUID.fromString(rawId) }.getOrNull() }
              val nextSupplierOrderId = body.supplierOrderId
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let { rawId -> runCatching { UUID.fromString(rawId) }.getOrNull() }
              val sanitizedPromotions = body.promotions.sanitizedStockPromotions()

              val previousBatchRow = StockBatchesV2
                .selectAll()
                .where {
                  (StockBatchesV2.id eq id) and
                     (StockBatchesV2.storeId eq storeId)
                }
                .singleOrNull()
                ?: return@newSuspendedTransaction null

              val changedBatchFields = batchChangedFieldsInsideTransaction(
                previousRow = previousBatchRow,
                goodsItemId = goodsItemId,
                nextSupplierId = nextSupplierId,
                nextSupplierOrderId = nextSupplierOrderId,
                sanitizedPromotions = sanitizedPromotions,
                body = body
              )
              val meaningfulBatchContentChanged = changedBatchFields.isNotEmpty()

              StockBatchesV2.update({
                (StockBatchesV2.id eq id) and
                   (StockBatchesV2.storeId eq storeId)
              }) {
                it[StockBatchesV2.goodsItemId] = goodsItemId

                it[StockBatchesV2.supplierId] = nextSupplierId
                it[StockBatchesV2.supplierOrderId] = nextSupplierOrderId

                it[StockBatchesV2.quantity] = body.quantity

                it[StockBatchesV2.supplyPrice] = body.supplyPrice
                it[StockBatchesV2.salePriceOverride] = body.salePriceOverride
                it[StockBatchesV2.returnPriceOverride] = body.returnPriceOverride
                it[StockBatchesV2.wholesalePriceOverride] = body.wholesalePriceOverride

                it[StockBatchesV2.deliveredAtMillis] = body.deliveredAtMillis
                it[StockBatchesV2.manufacturedAtMillis] = body.manufacturedAtMillis
                it[StockBatchesV2.expirationDateMillis] = body.expirationDateMillis

                it[StockBatchesV2.discounts] = body.discounts
                it[StockBatchesV2.promotions] = sanitizedPromotions

                it[StockBatchesV2.shelfPosition] = body.shelfPosition
                it[StockBatchesV2.shelfPriority] = body.shelfPriority

                it[StockBatchesV2.status] = body.status.name
                it[StockBatchesV2.additionalNotes] = body.additionalNotes
                it[StockBatchesV2.additionalNotesLocalized] = body.additionalNotesLocalized

                it[StockBatchesV2.updatedAtMillis] = now
                it[StockBatchesV2.isActive] = body.isActive
              }

              body.supplierId?.let { rawSupplierId ->
                val supplierId = runCatching { UUID.fromString(rawSupplierId) }.getOrNull()

                if (supplierId != null) {
                  upsertSupplierGoodsPriceInsideTransaction(
                    userId = userId,
                    storeId = storeId,
                    supplierId = supplierId,
                    goodsItemId = goodsItemId,
                    supplyPrice = body.supplyPrice,
                    now = now
                  )
                }
              }

              if (meaningfulBatchContentChanged) {
                val logText = stockBatchOperationLogTextInsideTransaction(
                  action = OPERATION_LOG_ACTION_UPDATED,
                  batchId = id,
                  goodsItemId = goodsItemId,
                  batch = body.copy(id = id.toString())
                )

                insertOperationLogInsideTransaction(
                  actorUserId = userId,
                  storeId = storeId,
                  action = OPERATION_LOG_ACTION_UPDATED,
                  entityType = OPERATION_LOG_ENTITY_STOCK_BATCH,
                  entityId = id.toString(),
                  title = logText.title,
                  details = logText.details,
                  metadata = logText.metadata + ("changed_fields" to changedBatchFields.joinToString("|")),
                  now = now
                )
              }

              result += body.copy(
                userId = userId.toString(),
                storeId = storeId.toString(),
                promotions = body.promotions.sanitizedStockPromotions(),
                updatedAtMillis = now
              )
            }

            result
          }

          updated?.let {
            publishStockRealtimeBundle(it.map { batch -> batch.storeId }, "stock_batch_updated")
            call.genericResponse(
              status = HttpStatusCode.OK,
              payload = it,
              message = getResponse("18").message
            )
          } ?: call.genericResponseNoPayload(
            HttpStatusCode.BadRequest,
            simpleMessage(
              main = "Cannot update stock batch",
              ru = "Не удалось обновить партию товара",
              kk = "Тауар партиясын жаңарту мүмкін болмады"
            )
          )
        }

        delete("/delete") {
          val userId = call.checkPrincipal() ?: return@delete
          val ids = call.receiveOneOrList<String>()
          val storeId = call.headerUuid("store_id")
            ?: return@delete call.respondAitaUnauthorized()

          val deletedIds = newSuspendedTransaction(aitaServerIoContext) {
            if (!call.matchesInventoryContextStoreIdInsideTransaction(userId, storeId))
              return@newSuspendedTransaction null

            if (!userCanUseStoreActionInsideTransaction(userId, storeId, STORE_PERMISSION_STOCK_BATCH_DELETE, requireWorkshift = true))
              return@newSuspendedTransaction null

            val now = System.currentTimeMillis()
            val result = mutableListOf<String>()

            for (rawId in ids) {
              val id = runCatching { UUID.fromString(rawId) }.getOrNull()
                ?: continue

              val previousBatchRow = StockBatchesV2
                .selectAll()
                .where {
                  (StockBatchesV2.id eq id) and
                     (StockBatchesV2.storeId eq storeId)
                }
                .singleOrNull()
                ?: continue

              val affected = StockBatchesV2.update({
                (StockBatchesV2.id eq id) and
                   (StockBatchesV2.storeId eq storeId)
              }) {
                it[StockBatchesV2.isActive] = false
                it[StockBatchesV2.status] = StockBatchStatusDataModel.Deleted.name
                it[StockBatchesV2.updatedAtMillis] = now
              }

              if (affected > 0) {
                val logText = stockBatchOperationLogTextInsideTransaction(
                  action = OPERATION_LOG_ACTION_DELETED,
                  batchId = id,
                  goodsItemId = previousBatchRow[StockBatchesV2.goodsItemId],
                  batch = previousBatchRow.toGoodsBatchDataModel()
                )
                insertOperationLogInsideTransaction(
                  actorUserId = userId,
                  storeId = storeId,
                  action = OPERATION_LOG_ACTION_DELETED,
                  entityType = OPERATION_LOG_ENTITY_STOCK_BATCH,
                  entityId = id.toString(),
                  title = logText.title,
                  details = logText.details,
                  metadata = logText.metadata + ("changed_fields" to "deleted"),
                  now = now
                )
                result += rawId
              }
            }

            result
          }

          deletedIds?.let {
            publishStockRealtimeBundle(storeId.toString(), "stock_batch_deleted")
            call.genericResponse(
              status = HttpStatusCode.OK,
              payload = it,
              message = getResponse("19").message
            )
          } ?: call.respondAitaUnauthorized()
        }

        post("/setActiveShelfBatch") {
          val userId = call.checkPrincipal() ?: return@post
          val body = call.receiveAita<GoodsBatchDataModel>()
          var changed = false

          val updatedItem = newSuspendedTransaction(aitaServerIoContext) {
            val batchId = runCatching { UUID.fromString(body.id) }.getOrNull()
              ?: return@newSuspendedTransaction null

            val goodsItemId = runCatching { UUID.fromString(body.goodsItemId) }.getOrNull()
              ?: return@newSuspendedTransaction null

            val storeId = runCatching { UUID.fromString(body.storeId) }.getOrNull()
              ?: return@newSuspendedTransaction null

            if (!call.matchesInventoryContextStoreIdInsideTransaction(userId, storeId))
              return@newSuspendedTransaction null

            if (!userCanUseStoreActionInsideTransaction(userId, storeId, STORE_PERMISSION_STOCK_BATCH_SET_ACTIVE_SHELF, requireWorkshift = true))
              return@newSuspendedTransaction null

            val batchExists = StockBatchesV2
              .selectAll()
              .where {
                (StockBatchesV2.id eq batchId) and
                   (StockBatchesV2.goodsItemId eq goodsItemId) and
                   (StockBatchesV2.storeId eq storeId) and
                   (StockBatchesV2.isActive eq true)
              }
              .empty()
              .not()

            if (!batchExists)
              return@newSuspendedTransaction null

            val existingItemRow = StockItems
              .selectAll()
              .where {
                (StockItems.id eq goodsItemId) and
                   (StockItems.storeId eq storeId)
              }
              .singleOrNull() ?: return@newSuspendedTransaction null

            val previousShelfBatchId = existingItemRow[StockItems.activeShelfBatchId]
            changed = previousShelfBatchId != batchId

            if (changed) {
              val now = System.currentTimeMillis()
              StockItems.update({
                (StockItems.id eq goodsItemId) and
                   (StockItems.storeId eq storeId)
              }) {
                it[StockItems.activeShelfBatchId] = batchId
                it[StockItems.updatedAtMillis] = now
              }
              val itemName = existingItemRow.stockItemOperationLogName(goodsItemId.toString())
              insertOperationLogInsideTransaction(
                actorUserId = userId,
                storeId = storeId,
                action = OPERATION_LOG_ACTION_UPDATED,
                entityType = OPERATION_LOG_ENTITY_STOCK_ITEM,
                entityId = goodsItemId.toString(),
                title = simpleMessage(
                  main = "Active shelf batch changed: $itemName",
                  en = "Active shelf batch changed: $itemName",
                  ru = "Активная партия на полке изменена: $itemName",
                  kk = "Сөредегі белсенді партия өзгерді: $itemName"
                ),
                details = stockItemChangeDetails(listOf("active shelf batch")),
                metadata = mapOf(
                  "goods_item_id" to goodsItemId.toString(),
                  "goods_name" to itemName,
                  "batch_id" to batchId.toString(),
                  "previous_batch_id" to previousShelfBatchId?.toString().orEmpty(),
                  "changed_fields" to "active_shelf_batch"
                ).filterValues { it.isNotBlank() },
                now = now
              )
            }

            StockItems
              .selectAll()
              .where { StockItems.id eq goodsItemId }
              .single()
              .toGoodsItemDataModel()
          }

          updatedItem?.let {
            if (changed) publishStockRealtimeBundle(it.storeId, "stock_shelf_batch_selected")
            call.genericResponse(
              status = HttpStatusCode.OK,
              payload = it,
              message = if (changed) simpleMessage(
                main = "Shelf batch selected",
                ru = "Партия на полке выбрана",
                kk = "Сөредегі партия таңдалды"
              ) else null
            )
          } ?: call.respondAitaUnauthorized()
        }
      }
    }

    route("/supplierGoodsPrices") {
      authenticate("auth-jwt") {
        get("/my") {
          val userId = call.checkPrincipal() ?: return@get

          val result = newSuspendedTransaction(aitaServerIoContext) {
            val supplierIds = accessibleSupplierIdsForUserInsideTransaction(userId)
            if (supplierIds.isEmpty()) {
              emptyList()
            } else {
              SupplierGoodsPrices
                .selectAll()
                .where {
                  (SupplierGoodsPrices.supplierId inList supplierIds) and
                     (SupplierGoodsPrices.isActive eq true)
                }
                .map { it.toSupplierGoodsPriceDataModel() }
                .sortedByDescending { price -> price.lastUsedAtMillis ?: price.updatedAtMillis }
            }
          }

          call.genericResponse(
            status = HttpStatusCode.OK,
            payload = result,
            message = simpleMessage(
              main = "Supplier price book loaded",
              ru = "Книга цен поставщика загружена",
              kk = "Жеткізуші бағалар кітабы жүктелді"
            )
          )
        }

        get("/get") {
          val userId = call.checkPrincipal() ?: return@get
          val storeId = call.headerUuid("store_id")
            ?: return@get call.respondAitaUnauthorized()

          val result = newSuspendedTransaction(aitaServerIoContext) {
            if (!userCanUseStoreActionInsideTransaction(userId, storeId, STORE_PERMISSION_SUPPLIERS_VIEW, requireWorkshift = false))
              return@newSuspendedTransaction null

            SupplierGoodsPrices
              .selectAll()
              .where {
                (SupplierGoodsPrices.storeId eq storeId) and
                   (SupplierGoodsPrices.isActive eq true)
              }
              .map { it.toSupplierGoodsPriceDataModel() }
          }

          result?.let {
            call.genericResponse(
              status = HttpStatusCode.OK,
              payload = it
            )
          } ?: call.respondAitaUnauthorized()
        }

        post("/upsert") {
          val userId = call.checkPrincipal() ?: return@post
          val body = call.receiveAita<SupplierGoodsPriceDataModel>()

          val result = newSuspendedTransaction(aitaServerIoContext) {
            val storeId = runCatching { UUID.fromString(body.storeId) }.getOrNull()
              ?: return@newSuspendedTransaction null

            val supplierId = runCatching { UUID.fromString(body.supplierId) }.getOrNull()
              ?: return@newSuspendedTransaction null

            val goodsItemId = runCatching { UUID.fromString(body.goodsItemId) }.getOrNull()
              ?: return@newSuspendedTransaction null

            val canManageFromStore = userCanUseStoreActionInsideTransaction(
              userId = userId,
              storeId = storeId,
              permission = STORE_PERMISSION_SUPPLIER_PRICES_MANAGE,
              requireWorkshift = true
            )
            val canManageFromSupplier = supplierCanMaintainPriceBookInsideTransaction(
              userId = userId,
              storeId = storeId,
              supplierId = supplierId,
              goodsItemId = goodsItemId
            )
            if (!canManageFromStore && !canManageFromSupplier)
              return@newSuspendedTransaction null

            val now = System.currentTimeMillis()

            upsertSupplierGoodsPriceInsideTransaction(
              userId = userId,
              storeId = storeId,
              supplierId = supplierId,
              goodsItemId = goodsItemId,
              supplyPrice = body.supplyPrice,
              minOrderQuantity = body.minOrderQuantity,
              packageQuantity = body.packageQuantity,
              supplierBarcode = body.supplierBarcode,
              supplierGoodsName = body.supplierGoodsName,
              now = now
            )

            SupplierGoodsPrices
              .selectAll()
              .where {
                (SupplierGoodsPrices.storeId eq storeId) and
                   (SupplierGoodsPrices.supplierId eq supplierId) and
                   (SupplierGoodsPrices.goodsItemId eq goodsItemId)
              }
              .single()
              .toSupplierGoodsPriceDataModel()
          }

          result?.let {
            call.genericResponse(
              status = HttpStatusCode.OK,
              payload = it,
              message = simpleMessage(
                main = "Supplier price saved",
                ru = "Цена поставщика сохранена",
                kk = "Жеткізуші бағасы сақталды"
              )
            )
          } ?: call.respondAitaUnauthorized()
        }

        delete("/delete") {
          val userId = call.checkPrincipal() ?: return@delete
          val ids = call.receiveOneOrList<String>()
          val storeId = call.headerUuid("store_id")
            ?: return@delete call.respondAitaUnauthorized()

          val deleted = newSuspendedTransaction(aitaServerIoContext) {
            if (!userCanUseStoreActionInsideTransaction(userId, storeId, STORE_PERMISSION_SUPPLIER_PRICES_MANAGE, requireWorkshift = true))
              return@newSuspendedTransaction null

            val now = System.currentTimeMillis()
            val result = mutableListOf<String>()

            for (rawId in ids) {
              val id = runCatching { UUID.fromString(rawId) }.getOrNull()
                ?: continue

              val affected = SupplierGoodsPrices.update({
                (SupplierGoodsPrices.id eq id) and
                   (SupplierGoodsPrices.storeId eq storeId) and
                   (SupplierGoodsPrices.userId eq userId)
              }) {
                it[SupplierGoodsPrices.isActive] = false
                it[SupplierGoodsPrices.updatedAtMillis] = now
              }

              if (affected > 0)
                result += rawId
            }

            result
          }

          deleted?.let {
            call.genericResponse(
              status = HttpStatusCode.OK,
              payload = it,
              message = simpleMessage(
                main = "Supplier prices deleted",
                ru = "Цены поставщика удалены",
                kk = "Жеткізуші бағалары өшірілді"
              )
            )
          } ?: call.respondAitaUnauthorized()
        }
      }
    }

    route("/stores") {
      authenticate("auth-jwt") {
        get("/get") {
          val userId = call.checkPrincipal() ?: return@get

          val stores = newSuspendedTransaction(aitaServerIoContext) {
            val noUser = Users
              .select(Users.id)
              .where { Users.id eq userId }
              .empty()

            if (noUser)
              call.respondAitaUnauthorized()

            val directStoreIds = StoreUsers
              .select(StoreUsers.storeId)
              .where { StoreUsers.userId eq userId }
              .map { it[StoreUsers.storeId] }

            val workerStoreIds = StoreWorkerMemberships
              .select(StoreWorkerMemberships.storeId)
              .where {
                (StoreWorkerMemberships.userId eq userId) and
                   (StoreWorkerMemberships.isActive eq true)
              }
              .map { it[StoreWorkerMemberships.storeId] }

            val accessibleRootStoreIds = (directStoreIds + workerStoreIds).distinct()
            val accessibleRootStoreIdsNullable = accessibleRootStoreIds.map { it as UUID? }

            if (accessibleRootStoreIds.isEmpty()) {
              emptyList()
            } else {
              Stores
                .selectAll()
                .where {
                  (Stores.id inList accessibleRootStoreIds) or
                     (Stores.parentStoreId inList accessibleRootStoreIdsNullable)
                }
                .toList()
                .toHierarchicalStoreDataModels()
            }
          }

          call.genericListResponse(
            HttpStatusCode.OK,
            stores
          )
        }

        put("/active") {
          val userId = call.checkPrincipal() ?: return@put
          val body = call.receiveAita<String>().trim()

          val activeResult = newSuspendedTransaction(aitaServerIoContext) {
            if (body.isBlank()) {
              return@newSuspendedTransaction if (Users.update({ Users.id eq userId }) {
                  it[Users.activeStoreId] = null
                } > 0
              ) 0 else 1
            }

            val storeId = resolveStoreIdByPublicOrPrivateIdInsideTransaction(body) ?: return@newSuspendedTransaction 1

            if (!userHasStoreAccessInsideTransaction(userId, storeId))
              return@newSuspendedTransaction 1

            if (Users.update({ Users.id eq userId }) {
                it[Users.activeStoreId] = storeId
              } > 0
            ) 0 else 1
          }

          when (activeResult) {
            0 -> call.genericResponseNoPayload(
              HttpStatusCode.OK,
              message = simpleMessage(
                main = "Active store saved",
                ru = "Активный магазин сохранён",
                kk = "Белсенді дүкен сақталды"
              )
            )

            else -> call.respondAitaUnauthorized()
          }
        }

        post("/add") {
          val userId = call.checkPrincipal() ?: return@post

          val noUser = newSuspendedTransaction(aitaServerIoContext) {
            Users
              .select(Users.id)
              .where {
                Users.id eq userId
              }
              .empty()
          }

          if (noUser) return@post call.respondAitaUnauthorized()

          val body = call.receiveAita<StoreDataModel>()

          if (!validateStoreAddress(body)) {
            return@post call.genericResponseNoPayload(
              HttpStatusCode.BadRequest,
              message = simpleMessage(
                main = "Address is required",
                ru = "Адрес обязателен",
                kk = "Мекенжай қажет"
              )
            )
          }

          if (!validateStoreLegalId(body)) {
            return@post call.genericResponseNoPayload(
              HttpStatusCode.BadRequest,
              message = simpleMessage(
                main = "Legal ID format is invalid",
                ru = "Неверный формат юридического ID",
                kk = "Заңды ID пішімі қате"
              )
            )
          }

          val parentStoreIdForBranch = body.parentStoreId
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }

          if (body.parentStoreId != null && parentStoreIdForBranch == null) {
            return@post call.respondAitaUnauthorized()
          }

          val parentAccessOk = newSuspendedTransaction(aitaServerIoContext) {
            parentStoreIdForBranch?.let { parentId ->
              val canManageBranches = userCanUseStoreActionInsideTransaction(userId, parentId, STORE_PERMISSION_BRANCHES_MANAGE, requireWorkshift = false) ||
                 userCanUseStoreActionInsideTransaction(userId, parentId, STORE_PERMISSION_STORE_MANAGE, requireWorkshift = false)

              canManageBranches &&
                 Stores.select(Stores.parentStoreId).where { Stores.id eq parentId }.singleOrNull()?.get(Stores.parentStoreId) == null
            } ?: true
          }

          if (!parentAccessOk) return@post call.respondAitaUnauthorized()

          var state23505Reached: Boolean

          var id: UUID? = null
          var instant = Instant.now()

          do {
            state23505Reached = try {
              id = UUID.randomUUID()
              instant = Instant.now()

              newSuspendedTransaction(aitaServerIoContext) {
                val publicId = generateUniqueStorePublicIdInsideTransaction()
                val parentOwnerUserIds = parentStoreIdForBranch?.let { parentId ->
                  Stores.select(Stores.ownerUserIds).where { Stores.id eq parentId }.singleOrNull()?.get(Stores.ownerUserIds)
                }
                Stores.insert {
                  it[Stores.id] = id
                  it[Stores.publicId] = publicId
                  it[Stores.parentStoreId] = parentStoreIdForBranch
                  it[Stores.ownerUserIds] = parentOwnerUserIds ?: listOf(userId.toString())
                  it[Stores.storeTypeIds] = body.storeTypeIds
                  it[Stores.name] = body.name
                  it[Stores.alias] = body.alias
                  it[Stores.description] = body.description
                  it[Stores.companyForms] = if (parentStoreIdForBranch == null) body.companyForms else emptyList()
                  it[Stores.location] = body.location
                  it[Stores.address] = body.address.trim()
                  it[Stores.legalIdTypeId] = if (parentStoreIdForBranch == null) body.legalIdTypeId.ifBlank { storeLegalIdFormatForCountry(body.countryLocales.firstOrNull()).id } else ""
                  it[Stores.legalId] = if (parentStoreIdForBranch == null) body.legalId.trim() else ""
                  it[Stores.phoneNumbers] = body.phoneNumbers
                  it[Stores.emails] = body.emails
                  it[Stores.countryLocales] = body.countryLocales
                  it[Stores.createdAt] = instant
                  it[Stores.updatedAt] = instant
                }

                StoreUsers.insertIgnore {           // composite PK avoids dup (store_id,user_id)
                  it[StoreUsers.storeId] = id
                  it[StoreUsers.userId] = userId // from JWT principal
                }

                parentStoreIdForBranch?.let { parentId ->
                  StoreUsers
                    .select(StoreUsers.userId)
                    .where { StoreUsers.storeId eq parentId }
                    .forEach { row ->
                      StoreUsers.insertIgnore { link ->
                        link[StoreUsers.storeId] = id
                        link[StoreUsers.userId] = row[StoreUsers.userId]
                      }
                    }

                }
              }

              false
            } catch (exception: ExposedSQLException) {
              val constraint = (exception.cause as? PSQLException)?.serverErrorMessage?.constraint
              val isPkCollision = exception.sqlState == "23505" && constraint?.equals("stores_pkey", true) == true

              if (!isPkCollision) {
                throw exception
              }

              true
            }
          } while (state23505Reached)

          id?.let { createdStoreId ->
            val publicId = newSuspendedTransaction(aitaServerIoContext) {
              Stores.select(Stores.publicId).where { Stores.id eq createdStoreId }.single()[Stores.publicId]
            }
            call.genericResponse(
              HttpStatusCode.Created,
              payload = body.copy(id = createdStoreId.toString(), publicId = publicId, createdAt = instant.toEpochMilli(), parentStoreId = parentStoreIdForBranch?.toString()),
              message = getResponse("10").message
            )
          } ?: call.genericResponseNoPayload(
            status = HttpStatusCode.InternalServerError,
            message = getResponse("3").message
          )
        }

        put("/update") {
          val userId = call.checkPrincipal() ?: return@put

          val body = call.receiveAita<StoreDataModel>()

          if (!validateStoreAddress(body)) {
            return@put call.genericResponseNoPayload(
              HttpStatusCode.BadRequest,
              message = simpleMessage(
                main = "Address is required",
                ru = "Адрес обязателен",
                kk = "Мекенжай қажет"
              )
            )
          }

          if (!validateStoreLegalId(body)) {
            return@put call.genericResponseNoPayload(
              HttpStatusCode.BadRequest,
              message = simpleMessage(
                main = "Legal ID format is invalid",
                ru = "Неверный формат юридического ID",
                kk = "Заңды ID пішімі қате"
              )
            )
          }

          val updated = newSuspendedTransaction(aitaServerIoContext) {

            val id = runCatching { UUID.fromString(body.id) }.getOrNull() ?: return@newSuspendedTransaction 2

            val currentParentStoreId = Stores
              .select(Stores.parentStoreId)
              .where { Stores.id eq id }
              .singleOrNull()
              ?.get(Stores.parentStoreId)

            val canUpdateStore = if (currentParentStoreId == null) {
              userCanUseStoreActionInsideTransaction(userId, id, STORE_PERMISSION_STORE_MANAGE, requireWorkshift = false)
            } else {
              userCanUseStoreActionInsideTransaction(userId, currentParentStoreId, STORE_PERMISSION_BRANCHES_MANAGE, requireWorkshift = false) ||
                 userCanUseStoreActionInsideTransaction(userId, currentParentStoreId, STORE_PERMISSION_STORE_MANAGE, requireWorkshift = false)
            }

            if (!canUpdateStore)
              return@newSuspendedTransaction 1

            Stores.update({ Stores.id eq id }) {
              it[Stores.storeTypeIds] = body.storeTypeIds
              it[Stores.name] = body.name
              it[Stores.alias] = body.alias
              it[Stores.description] = body.description
              it[Stores.companyForms] = if (currentParentStoreId == null) body.companyForms else emptyList()
              it[Stores.location] = body.location
              it[Stores.address] = body.address.trim()
              it[Stores.legalIdTypeId] = if (currentParentStoreId == null) body.legalIdTypeId.ifBlank { storeLegalIdFormatForCountry(body.countryLocales.firstOrNull()).id } else ""
              it[Stores.legalId] = if (currentParentStoreId == null) body.legalId.trim() else ""
              it[Stores.phoneNumbers] = body.phoneNumbers
              it[Stores.emails] = body.emails
              it[Stores.countryLocales] = body.countryLocales
              it[Stores.updatedAt] = Instant.now()
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

            1, 2 -> call.respondAitaUnauthorized()
            else -> call.genericResponseNoPayload(
              status = HttpStatusCode.InternalServerError,
              message = getResponse("3").message
            )
          }
        }

        delete("/delete") {
          val userId = call.checkPrincipal() ?: return@delete

          val body = call.receiveAita<String>()

          val deleted = newSuspendedTransaction(aitaServerIoContext) {

            val id = runCatching { UUID.fromString(body) }.getOrNull() ?: return@newSuspendedTransaction 2

            val ownsStore = Stores
              .select(Stores.ownerUserIds)
              .where { Stores.id eq id }
              .singleOrNull()
              ?.get(Stores.ownerUserIds)
              ?.contains(userId.toString()) == true

            if (!ownsStore) {
              1
            } else {
              val branchIds = Stores
                .select(Stores.id)
                .where { Stores.parentStoreId eq (id as UUID?) }
                .map { it[Stores.id] }
              val storeIdsToDelete = listOf(id) + branchIds

              hardDeleteStoreOwnedDataInsideTransaction(storeIdsToDelete)

              if (branchIds.isNotEmpty()) {
                Stores.deleteWhere { Stores.id inList branchIds }
              }
              if (Stores.deleteWhere { Stores.id eq id } > 0) 0 else 1
            }
          }

          return@delete when (deleted) {
            0 -> call.genericResponseNoPayload(
              HttpStatusCode.OK,
              message = getResponse("12").message
            )

            1, 2 -> call.respondAitaUnauthorized()
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
            newSuspendedTransaction(aitaServerIoContext) {
              val noUser = Users.select(Users.id).where { Users.id eq userId }.empty()

              if (noUser)
                return@newSuspendedTransaction 1 to null

              val userIdText = userId.toString()
              val matches = Suppliers
                .selectAll()
                .where { Suppliers.isActive eq true }
                .map { it.toSupplierDataModel() }
                .filter { supplier -> supplier.userIds.isEmpty() || supplier.userIds.contains(userIdText) }
                .sortedWith(
                  compareBy<SupplierDataModel> { it.userIds.isEmpty() }
                    .thenBy { it.name.extractLocalizedString("main") ?: it.id }
                )

              0 to matches
            }

          when {
            suppliers.first == 1 -> call.respondAitaUnauthorized()
            suppliers.second != null ->
              call.genericResponse(
                HttpStatusCode.OK,
                payload = suppliers.second
              )

            else -> call.genericResponseNoPayload(HttpStatusCode.InternalServerError, message = getResponse("3").message)
          }
        }

        post("/add") {
          val userId = call.checkPrincipal() ?: return@post
          val body = call.receiveAita<SupplierDataModel>()

          val inserted = newSuspendedTransaction(aitaServerIoContext) {
            if (Users.select(Users.id).where { Users.id eq userId }.empty())
              return@newSuspendedTransaction null

            val cleaned = body.cleanedForStorage(ownerUserId = userId)
            val id = UUID.randomUUID()

            Suppliers.insert {
              it[Suppliers.id] = id
              it[Suppliers.userIds] = jsonBase.encodeToString(cleaned.userIds)
              it[Suppliers.typeIds] = cleaned.typeIds?.let { value -> jsonBase.encodeToString(value) }
              it[Suppliers.categoryIds] = jsonBase.encodeToString(cleaned.categoryIds)
              it[Suppliers.name] = jsonBase.encodeToString(cleaned.name)
              it[Suppliers.phoneNumbers] = cleaned.phoneNumbers?.let { value -> jsonBase.encodeToString(value) }
              it[Suppliers.emails] = cleaned.emails?.let { value -> jsonBase.encodeToString(value) }
              it[Suppliers.isActive] = true
            }

            Suppliers.selectAll().where { Suppliers.id eq id }.single().toSupplierDataModel()
          }

          inserted?.let {
            call.genericResponse(HttpStatusCode.Created, payload = it, message = getResponse("83").message)
          } ?: call.respondAitaUnauthorized()
        }

        put("/update") {
          val userId = call.checkPrincipal() ?: return@put
          val body = call.receiveAita<SupplierDataModel>()

          val updated = newSuspendedTransaction(aitaServerIoContext) {
            val id = runCatching { UUID.fromString(body.id) }.getOrNull()
              ?: return@newSuspendedTransaction null
            val row = Suppliers.selectAll().where { Suppliers.id eq id }.singleOrNull()
              ?: return@newSuspendedTransaction null
            val existing = row.toSupplierDataModel()
            if (!existing.userIds.contains(userId.toString()))
              return@newSuspendedTransaction null

            val cleaned = body.cleanedForStorage(ownerUserId = userId, existingUserIds = existing.userIds)
            Suppliers.update({ Suppliers.id eq id }) {
              it[Suppliers.userIds] = jsonBase.encodeToString(cleaned.userIds)
              it[Suppliers.typeIds] = cleaned.typeIds?.let { value -> jsonBase.encodeToString(value) }
              it[Suppliers.categoryIds] = jsonBase.encodeToString(cleaned.categoryIds)
              it[Suppliers.name] = jsonBase.encodeToString(cleaned.name)
              it[Suppliers.phoneNumbers] = cleaned.phoneNumbers?.let { value -> jsonBase.encodeToString(value) }
              it[Suppliers.emails] = cleaned.emails?.let { value -> jsonBase.encodeToString(value) }
              it[Suppliers.isActive] = cleaned.isActive
            }

            Suppliers.selectAll().where { Suppliers.id eq id }.single().toSupplierDataModel()
          }

          updated?.let {
            call.genericResponse(HttpStatusCode.OK, payload = it, message = getResponse("84").message)
          } ?: call.respondAitaUnauthorized()
        }

        delete("/delete") {
          val userId = call.checkPrincipal() ?: return@delete
          val supplierId = call.receiveAita<String>().trim()

          val deleted = newSuspendedTransaction(aitaServerIoContext) {
            val id = runCatching { UUID.fromString(supplierId) }.getOrNull()
              ?: return@newSuspendedTransaction false
            val row = Suppliers.selectAll().where { Suppliers.id eq id }.singleOrNull()
              ?: return@newSuspendedTransaction false
            val supplier = row.toSupplierDataModel()
            if (!supplier.userIds.contains(userId.toString()))
              return@newSuspendedTransaction false

            Suppliers.update({ Suppliers.id eq id }) {
              it[Suppliers.isActive] = false
            } > 0
          }

          if (deleted) {
            call.genericResponse(HttpStatusCode.OK, payload = supplierId, message = getResponse("85").message)
          } else {
            call.respondAitaUnauthorized()
          }
        }
      }
    }

    route("/supplierContracts") {
      authenticate("auth-jwt") {
        get("/get") {
          val userId = call.checkPrincipal() ?: return@get
          val storeId = call.headerUuid("store_id")
          val supplierId = call.headerUuid("supplier_id")

          val result = newSuspendedTransaction(aitaServerIoContext) {
            val accessibleSupplierIds = if (storeId == null && supplierId == null) {
              Suppliers
                .select(Suppliers.id, Suppliers.userIds)
                .where { Suppliers.isActive eq true }
                .mapNotNull { row ->
                  row[Suppliers.id].takeIf { decodeSupplierStringList(row[Suppliers.userIds]).contains(userId.toString()) }
                }
            } else {
              emptyList()
            }

            if (storeId != null && !userCanUseStoreActionInsideTransaction(userId, storeId, STORE_PERMISSION_SUPPLIER_ORDERS_VIEW, requireWorkshift = false)) return@newSuspendedTransaction null
            if (supplierId != null && !userHasSupplierAccessInsideTransaction(userId, supplierId) && storeId == null) return@newSuspendedTransaction null
            if (storeId == null && supplierId == null && accessibleSupplierIds.isEmpty()) return@newSuspendedTransaction emptyList<SupplierPartnershipContractDataModel>()

            var filter: Op<Boolean> = SupplierPartnershipContracts.isActive eq true
            storeId?.let { filter = filter and (SupplierPartnershipContracts.storeId eq it) }
            supplierId?.let { filter = filter and (SupplierPartnershipContracts.supplierId eq it) }
            if (storeId == null && supplierId == null) {
              filter = filter and (SupplierPartnershipContracts.supplierId inList accessibleSupplierIds)
            }

            SupplierPartnershipContracts
              .selectAll()
              .where { filter }
              .map { it.toSupplierPartnershipContractDataModel() }
              .sortedByDescending { it.updatedAtMillis }
              .withSupplierContractSnapshotsInsideTransaction()
          }

          result?.let {
            call.genericListResponse(
              status = HttpStatusCode.OK,
              payload = it,
              message = simpleMessage(
                main = "Supplier contracts loaded",
                ru = "Договоры с поставщиками загружены",
                kk = "Жеткізуші келісімдері жүктелді"
              )
            )
          } ?: call.respondAitaUnauthorized()
        }

        post("/upsert") {
          val userId = call.checkPrincipal() ?: return@post
          val body = call.receiveAita<SupplierPartnershipContractDataModel>()

          val result = newSuspendedTransaction(aitaServerIoContext) {
            val storeId = runCatching { UUID.fromString(body.storeId) }.getOrNull()
              ?: return@newSuspendedTransaction null
            val supplierId = runCatching { UUID.fromString(body.supplierId) }.getOrNull()
              ?: return@newSuspendedTransaction null
            if (Suppliers.select(Suppliers.id).where { (Suppliers.id eq supplierId) and (Suppliers.isActive eq true) }.empty()) return@newSuspendedTransaction null

            val canStoreEdit = userCanUseStoreActionInsideTransaction(userId, storeId, STORE_PERMISSION_SUPPLIER_ORDERS_MANAGE, requireWorkshift = false)
            val canSupplierEdit = userHasSupplierAccessInsideTransaction(userId, supplierId)
            if (!canStoreEdit && !canSupplierEdit) return@newSuspendedTransaction null

            val requestedSide = normalizeSupplierContractSide(body.authorSide)
            val actorSide = when {
              requestedSide == SUPPLIER_CONTRACT_SIDE_SUPPLIER && canSupplierEdit -> SUPPLIER_CONTRACT_SIDE_SUPPLIER
              requestedSide == SUPPLIER_CONTRACT_SIDE_STORE && canStoreEdit -> SUPPLIER_CONTRACT_SIDE_STORE
              canSupplierEdit && !canStoreEdit -> SUPPLIER_CONTRACT_SIDE_SUPPLIER
              else -> SUPPLIER_CONTRACT_SIDE_STORE
            }

            val now = System.currentTimeMillis()
            val requestedId = runCatching { UUID.fromString(body.id) }.getOrNull()
            val existing = requestedId?.let { id ->
              SupplierPartnershipContracts
                .selectAll()
                .where { (SupplierPartnershipContracts.id eq id) and (SupplierPartnershipContracts.storeId eq storeId) and (SupplierPartnershipContracts.supplierId eq supplierId) }
                .singleOrNull()
            }
            val contractId = requestedId ?: UUID.randomUUID()
            val clean = body.cleanForContractStorageInsideTransaction(userId, storeId, supplierId, actorSide, now, existing)

            if (existing == null) {
              SupplierPartnershipContracts.insert { it.setSupplierContractColumns(contractId, clean.copy(id = contractId.toString())) }
            } else {
              SupplierPartnershipContracts.update({ SupplierPartnershipContracts.id eq contractId }) {
                it.setSupplierContractUpdateColumns(clean.copy(id = contractId.toString()))
              }
            }

            SupplierPartnershipContracts
              .selectAll()
              .where { SupplierPartnershipContracts.id eq contractId }
              .map { it.toSupplierPartnershipContractDataModel() }
              .withSupplierContractSnapshotsInsideTransaction()
              .firstOrNull()
          }

          result?.let {
            call.genericResponse(
              status = HttpStatusCode.OK,
              payload = it,
              message = simpleMessage(
                main = "Contract proposal sent",
                ru = "Предложение договора отправлено",
                kk = "Келісім ұсынысы жіберілді"
              )
            )
          } ?: call.genericResponseNoPayload(
            HttpStatusCode.BadRequest,
            simpleMessage(
              main = "Cannot save supplier contract",
              ru = "Не удалось сохранить договор с поставщиком",
              kk = "Жеткізуші келісімін сақтау мүмкін болмады"
            )
          )
        }

        post("/accept") {
          val userId = call.checkPrincipal() ?: return@post
          val contractId = runCatching { UUID.fromString(call.receiveAita<String>().trim()) }.getOrNull()
            ?: return@post call.respondAitaUnauthorized()

          val result = newSuspendedTransaction(aitaServerIoContext) {
            val existing = SupplierPartnershipContracts.selectAll().where { SupplierPartnershipContracts.id eq contractId }.singleOrNull()
              ?: return@newSuspendedTransaction null
            val storeId = existing[SupplierPartnershipContracts.storeId]
            val supplierId = existing[SupplierPartnershipContracts.supplierId]
            val canStoreAccept = userCanUseStoreActionInsideTransaction(userId, storeId, STORE_PERMISSION_SUPPLIER_ORDERS_MANAGE, requireWorkshift = false)
            val canSupplierAccept = userHasSupplierAccessInsideTransaction(userId, supplierId)
            if (!canStoreAccept && !canSupplierAccept) return@newSuspendedTransaction null
            val actorSide = when {
              canSupplierAccept && existing[SupplierPartnershipContracts.status] == SUPPLIER_CONTRACT_STATUS_PENDING_SUPPLIER -> SUPPLIER_CONTRACT_SIDE_SUPPLIER
              canStoreAccept && existing[SupplierPartnershipContracts.status] == SUPPLIER_CONTRACT_STATUS_PENDING_STORE -> SUPPLIER_CONTRACT_SIDE_STORE
              canSupplierAccept && !canStoreAccept -> SUPPLIER_CONTRACT_SIDE_SUPPLIER
              else -> SUPPLIER_CONTRACT_SIDE_STORE
            }
            val now = System.currentTimeMillis()
            val nextSupplierAcceptedAt = if (actorSide == SUPPLIER_CONTRACT_SIDE_SUPPLIER) now else existing[SupplierPartnershipContracts.supplierAcceptedAtMillis]
            val nextStoreAcceptedAt = if (actorSide == SUPPLIER_CONTRACT_SIDE_STORE) now else existing[SupplierPartnershipContracts.storeAcceptedAtMillis]
            val nextStatus = if (nextSupplierAcceptedAt != null && nextStoreAcceptedAt != null) SUPPLIER_CONTRACT_STATUS_ACTIVE else if (actorSide == SUPPLIER_CONTRACT_SIDE_SUPPLIER) SUPPLIER_CONTRACT_STATUS_PENDING_STORE else SUPPLIER_CONTRACT_STATUS_PENDING_SUPPLIER

            SupplierPartnershipContracts.update({ SupplierPartnershipContracts.id eq contractId }) {
              it[SupplierPartnershipContracts.status] = nextStatus
              it[SupplierPartnershipContracts.supplierAcceptedAtMillis] = nextSupplierAcceptedAt
              it[SupplierPartnershipContracts.storeAcceptedAtMillis] = nextStoreAcceptedAt
              if (actorSide == SUPPLIER_CONTRACT_SIDE_SUPPLIER) it[SupplierPartnershipContracts.supplierAcceptedByUserId] = userId
              if (actorSide == SUPPLIER_CONTRACT_SIDE_STORE) it[SupplierPartnershipContracts.storeAcceptedByUserId] = userId
              it[SupplierPartnershipContracts.updatedAtMillis] = now
            }

            SupplierPartnershipContracts
              .selectAll()
              .where { SupplierPartnershipContracts.id eq contractId }
              .map { it.toSupplierPartnershipContractDataModel() }
              .withSupplierContractSnapshotsInsideTransaction()
              .firstOrNull()
          }

          result?.let {
            call.genericResponse(
              status = HttpStatusCode.OK,
              payload = it,
              message = simpleMessage(
                main = if (it.status == SUPPLIER_CONTRACT_STATUS_ACTIVE) "Contract is active" else "Contract accepted",
                ru = if (it.status == SUPPLIER_CONTRACT_STATUS_ACTIVE) "Договор активен" else "Договор принят",
                kk = if (it.status == SUPPLIER_CONTRACT_STATUS_ACTIVE) "Келісім белсенді" else "Келісім қабылданды"
              )
            )
          } ?: call.respondAitaUnauthorized()
        }

        post("/decline") {
          val userId = call.checkPrincipal() ?: return@post
          val contractId = runCatching { UUID.fromString(call.receiveAita<String>().trim()) }.getOrNull()
            ?: return@post call.respondAitaUnauthorized()

          val result = newSuspendedTransaction(aitaServerIoContext) {
            val existing = SupplierPartnershipContracts.selectAll().where { SupplierPartnershipContracts.id eq contractId }.singleOrNull()
              ?: return@newSuspendedTransaction null
            val storeId = existing[SupplierPartnershipContracts.storeId]
            val supplierId = existing[SupplierPartnershipContracts.supplierId]
            val canStore = userCanUseStoreActionInsideTransaction(userId, storeId, STORE_PERMISSION_SUPPLIER_ORDERS_MANAGE, requireWorkshift = false)
            val canSupplier = userHasSupplierAccessInsideTransaction(userId, supplierId)
            if (!canStore && !canSupplier) return@newSuspendedTransaction null
            val now = System.currentTimeMillis()
            SupplierPartnershipContracts.update({ SupplierPartnershipContracts.id eq contractId }) {
              it[SupplierPartnershipContracts.status] = SUPPLIER_CONTRACT_STATUS_DECLINED
              it[SupplierPartnershipContracts.declinedAtMillis] = now
              it[SupplierPartnershipContracts.declinedByUserId] = userId
              it[SupplierPartnershipContracts.updatedAtMillis] = now
            }
            SupplierPartnershipContracts
              .selectAll()
              .where { SupplierPartnershipContracts.id eq contractId }
              .map { it.toSupplierPartnershipContractDataModel() }
              .withSupplierContractSnapshotsInsideTransaction()
              .firstOrNull()
          }

          result?.let {
            call.genericResponse(
              status = HttpStatusCode.OK,
              payload = it,
              message = simpleMessage(
                main = "Contract declined",
                ru = "Договор отклонён",
                kk = "Келісім қабылданбады"
              )
            )
          } ?: call.respondAitaUnauthorized()
        }

        post("/archive") {
          val userId = call.checkPrincipal() ?: return@post
          val contractId = runCatching { UUID.fromString(call.receiveAita<String>().trim()) }.getOrNull()
            ?: return@post call.respondAitaUnauthorized()

          val archived = newSuspendedTransaction(aitaServerIoContext) {
            val existing = SupplierPartnershipContracts.selectAll().where { SupplierPartnershipContracts.id eq contractId }.singleOrNull()
              ?: return@newSuspendedTransaction false
            val storeId = existing[SupplierPartnershipContracts.storeId]
            val supplierId = existing[SupplierPartnershipContracts.supplierId]
            val canStore = userCanUseStoreActionInsideTransaction(userId, storeId, STORE_PERMISSION_SUPPLIER_ORDERS_MANAGE, requireWorkshift = false)
            val canSupplier = userHasSupplierAccessInsideTransaction(userId, supplierId)
            if (!canStore && !canSupplier) return@newSuspendedTransaction false
            SupplierPartnershipContracts.update({ SupplierPartnershipContracts.id eq contractId }) {
              it[SupplierPartnershipContracts.status] = SUPPLIER_CONTRACT_STATUS_ARCHIVED
              it[SupplierPartnershipContracts.isActive] = false
              it[SupplierPartnershipContracts.updatedAtMillis] = System.currentTimeMillis()
            } > 0
          }

          if (archived) {
            call.genericResponse(
              status = HttpStatusCode.OK,
              payload = contractId.toString(),
              message = simpleMessage(
                main = "Contract archived",
                ru = "Договор архивирован",
                kk = "Келісім архивтелді"
              )
            )
          } else {
            call.respondAitaUnauthorized()
          }
        }
      }
    }

    route("/supplierOrders") {
      authenticate("auth-jwt") {
        get("/dashboard") {
          val userId = call.checkPrincipal() ?: return@get
          val dashboard = newSuspendedTransaction(aitaServerIoContext) {
            supplierModeDashboardInsideTransaction(userId)
          }

          call.genericResponse(
            status = HttpStatusCode.OK,
            payload = dashboard,
            message = simpleMessage(
              main = "Supplier dashboard loaded",
              ru = "Панель поставщика загружена",
              kk = "Жеткізуші панелі жүктелді"
            )
          )
        }

        get("/get") {
          val userId = call.checkPrincipal() ?: return@get
          val storeId = call.headerUuid("store_id")
          val supplierId = call.headerUuid("supplier_id")

          val result = newSuspendedTransaction(aitaServerIoContext) {
            val accessibleSupplierIds = if (storeId == null && supplierId == null) {
              Suppliers
                .select(Suppliers.id, Suppliers.userIds)
                .where { Suppliers.isActive eq true }
                .mapNotNull { row ->
                  row[Suppliers.id].takeIf { decodeSupplierStringList(row[Suppliers.userIds]).contains(userId.toString()) }
                }
            } else {
              emptyList()
            }

            if (storeId != null && !userCanUseStoreActionInsideTransaction(userId, storeId, STORE_PERMISSION_SUPPLIER_ORDERS_VIEW, requireWorkshift = false)) return@newSuspendedTransaction null
            if (supplierId != null && !userHasSupplierAccessInsideTransaction(userId, supplierId)) return@newSuspendedTransaction null
            if (storeId == null && supplierId == null && accessibleSupplierIds.isEmpty()) return@newSuspendedTransaction emptyList<SupplierOrderWithLinesDataModel>()

            var orderFilter: Op<Boolean> = SupplierOrders.isActive eq true
            storeId?.let { orderFilter = orderFilter and (SupplierOrders.storeId eq it) }
            supplierId?.let { orderFilter = orderFilter and (SupplierOrders.supplierId eq it) }
            if (storeId == null && supplierId == null) {
              orderFilter = orderFilter and (SupplierOrders.supplierId inList accessibleSupplierIds)
            }

            val supplierSideSeenIds = when {
              storeId == null && supplierId == null -> accessibleSupplierIds
              storeId == null && supplierId != null -> listOf(supplierId)
              else -> emptyList()
            }
            if (supplierSideSeenIds.isNotEmpty()) {
              val now = System.currentTimeMillis()
              SupplierOrders.update({
                (SupplierOrders.supplierId inList supplierSideSeenIds) and
                   (SupplierOrders.status eq SupplierOrderStatusDataModel.Sent.name) and
                   (SupplierOrders.isActive eq true)
              }) {
                it[SupplierOrders.status] = SupplierOrderStatusDataModel.SeenBySupplier.name
                it[SupplierOrders.updatedAtMillis] = now
              }
            }

            val orders = SupplierOrders
              .selectAll()
              .where { orderFilter }
              .map { it.toSupplierOrderDataModel() }
              .sortedByDescending { it.updatedAtMillis.takeIf { value -> value > 0L } ?: it.orderedAtMillis }

            val orderIds = orders.mapNotNull { runCatching { UUID.fromString(it.id) }.getOrNull() }
            val lines = if (orderIds.isEmpty()) {
              emptyList()
            } else {
              SupplierOrderLines
                .selectAll()
                .where { (SupplierOrderLines.orderId inList orderIds) and (SupplierOrderLines.isActive eq true) }
                .map { it.toSupplierOrderLineDataModel() }
            }

            orders.map { order ->
              SupplierOrderWithLinesDataModel(
                order = order,
                lines = lines.filter { it.orderId == order.id }
              )
            }.withSupplierDeskSnapshotsInsideTransaction()
          }

          result?.let {
            call.genericListResponse(
              status = HttpStatusCode.OK,
              payload = it,
              message = simpleMessage(
                main = "Supplier orders loaded",
                ru = "Заказы поставщикам загружены",
                kk = "Жеткізуші тапсырыстары жүктелді"
              )
            )
          } ?: call.respondAitaUnauthorized()
        }

        post("/add") {
          val userId = call.checkPrincipal() ?: return@post
          val body = call.receiveAita<SupplierOrderWithLinesDataModel>()
          var failureMessage: List<LocalizedStringDataModel>? = null

          val result = newSuspendedTransaction(aitaServerIoContext) {
            val storeId = runCatching { UUID.fromString(body.order.storeId) }.getOrNull()
              ?: return@newSuspendedTransaction null
            val supplierId = runCatching { UUID.fromString(body.order.supplierId) }.getOrNull()
              ?: return@newSuspendedTransaction null

            if (!userCanUseStoreActionInsideTransaction(userId, storeId, STORE_PERMISSION_SUPPLIER_ORDERS_MANAGE, requireWorkshift = true)) return@newSuspendedTransaction null
            if (Suppliers.select(Suppliers.id).where { (Suppliers.id eq supplierId) and (Suppliers.isActive eq true) }.empty()) return@newSuspendedTransaction null
            val requestedGoodsItemIds = body.lines.mapNotNull { line -> runCatching { UUID.fromString(line.goodsItemId) }.getOrNull() }
            if (supplierContractBlocksStoreSupplyInsideTransaction(storeId, supplierId, requestedGoodsItemIds) != null) {
              failureMessage = supplierContractGuardFailureMessage()
              return@newSuspendedTransaction null
            }

            val now = System.currentTimeMillis()
            val orderId = UUID.randomUUID()
            val cleanOrder = body.order.cleanForStorage(userId, storeId, supplierId, now)

            SupplierOrders.insert {
              it.setSupplierOrderColumns(orderId, cleanOrder, userId, storeId, supplierId)
            }

            body.lines
              .mapNotNull { line ->
                val goodsItemId = runCatching { UUID.fromString(line.goodsItemId) }.getOrNull()
                  ?: return@mapNotNull null
                val itemBelongsToStore = StockItems
                  .select(StockItems.id)
                  .where { (StockItems.id eq goodsItemId) and (StockItems.storeId eq storeId) and (StockItems.isActive eq true) }
                  .empty()
                  .not()
                if (!itemBelongsToStore) return@mapNotNull null
                line.cleanForStorage(orderId) to goodsItemId
              }
              .filter { (line, _) -> line.requestedQuantity.total > 0.0 }
              .forEach { (line, goodsItemId) ->
                val lineId = UUID.randomUUID()
                SupplierOrderLines.insert {
                  it.setSupplierOrderLineColumns(lineId, line, orderId, goodsItemId)
                }
              }

            supplierOrderWithLinesInsideTransaction(orderId)
              ?.let { listOf(it).withSupplierDeskSnapshotsInsideTransaction().firstOrNull() }
          }

          result?.let {
            call.genericResponse(
              status = HttpStatusCode.Created,
              payload = it,
              message = simpleMessage(
                main = "Supplier order created",
                ru = "Заказ поставщику создан",
                kk = "Жеткізушіге тапсырыс жасалды"
              )
            )
          } ?: call.genericResponseNoPayload(
            HttpStatusCode.BadRequest,
            failureMessage ?: simpleMessage(
              main = "Cannot create supplier order",
              ru = "Не удалось создать заказ поставщику",
              kk = "Жеткізушіге тапсырыс жасау мүмкін болмады"
            )
          )
        }

        put("/update") {
          val userId = call.checkPrincipal() ?: return@put
          val body = call.receiveAita<SupplierOrderWithLinesDataModel>()
          var failureMessage: List<LocalizedStringDataModel>? = null

          val result = newSuspendedTransaction(aitaServerIoContext) {
            val orderId = runCatching { UUID.fromString(body.order.id) }.getOrNull()
              ?: return@newSuspendedTransaction null
            val existing = SupplierOrders.selectAll().where { SupplierOrders.id eq orderId }.singleOrNull()
              ?: return@newSuspendedTransaction null
            val storeId = existing[SupplierOrders.storeId]
            val supplierId = existing[SupplierOrders.supplierId]
            val canEditFromStore = userCanUseStoreActionInsideTransaction(userId, storeId, STORE_PERMISSION_SUPPLIER_ORDERS_MANAGE, requireWorkshift = true)
            val canEditFromSupplier = userHasSupplierAccessInsideTransaction(userId, supplierId)
            if (!canEditFromStore && !canEditFromSupplier) return@newSuspendedTransaction null

            val now = System.currentTimeMillis()

            if (canEditFromSupplier && !canEditFromStore) {
              val existingOrder = existing.toSupplierOrderDataModel()
              val requestedOrder = body.order
              val supplierStatus = supplierOrderStatusAllowedFromSupplier(requestedOrder.status, existingOrder.status)
              val supplierPatch = existingOrder.copy(
                amount = requestedOrder.amount ?: existingOrder.amount,
                confirmedDeliveryTimeMillis = requestedOrder.confirmedDeliveryTimeMillis,
                supplierComment = requestedOrder.supplierComment,
                supplierCommentLocalized = requestedOrder.supplierCommentLocalized,
                paymentTerms = requestedOrder.paymentTerms,
                externalReference = requestedOrder.externalReference,
                status = supplierStatus,
                updatedAtMillis = now,
                isActive = existing[SupplierOrders.isActive]
              ).cleanForStorage(existing[SupplierOrders.userId], storeId, supplierId, now)

              SupplierOrders.update({ SupplierOrders.id eq orderId }) {
                it[SupplierOrders.amount] = supplierPatch.amount
                it[SupplierOrders.confirmedDeliveryTimeMillis] = supplierPatch.confirmedDeliveryTimeMillis
                it[SupplierOrders.supplierComment] = supplierPatch.supplierComment
                it[SupplierOrders.supplierCommentLocalized] = supplierPatch.supplierCommentLocalized
                it[SupplierOrders.paymentTerms] = supplierPatch.paymentTerms
                it[SupplierOrders.externalReference] = supplierPatch.externalReference
                it[SupplierOrders.status] = supplierPatch.status.name
                it[SupplierOrders.updatedAtMillis] = supplierPatch.updatedAtMillis
                it[SupplierOrders.isActive] = existing[SupplierOrders.isActive]
              }

              val existingLineIds = SupplierOrderLines
                .select(SupplierOrderLines.id)
                .where { SupplierOrderLines.orderId eq orderId }
                .map { it[SupplierOrderLines.id] }
                .toSet()

              body.lines.forEach { requestedLine ->
                val lineId = runCatching { UUID.fromString(requestedLine.id) }.getOrNull() ?: return@forEach
                if (lineId !in existingLineIds) return@forEach

                val cleanLine = requestedLine.cleanForStorage(orderId)
                SupplierOrderLines.update({ (SupplierOrderLines.id eq lineId) and (SupplierOrderLines.orderId eq orderId) }) {
                  it[SupplierOrderLines.supplierComment] = cleanLine.supplierComment
                  it[SupplierOrderLines.supplierCommentLocalized] = cleanLine.supplierCommentLocalized
                  it[SupplierOrderLines.supplierAcceptedQuantity] = cleanLine.supplierAcceptedQuantity
                  it[SupplierOrderLines.supplierOfferedSupplyPrice] = cleanLine.supplierOfferedSupplyPrice
                  it[SupplierOrderLines.substituteGoodsItemId] = cleanLine.substituteGoodsItemId
                    ?.let { raw -> runCatching { UUID.fromString(raw) }.getOrNull() }
                }

                if (supplierStatus != SupplierOrderStatusDataModel.Cancelled && supplierStatus != SupplierOrderStatusDataModel.IssueReported) {
                  learnSupplierPriceFromResponseLineInsideTransaction(
                    userId = userId,
                    storeId = storeId,
                    supplierId = supplierId,
                    line = cleanLine,
                    now = now
                  )
                }
              }

              return@newSuspendedTransaction supplierOrderWithLinesInsideTransaction(orderId)
                ?.let { listOf(it).withSupplierDeskSnapshotsInsideTransaction().firstOrNull() }
            }

            val requestedGoodsItemIdsForContract = body.lines.mapNotNull { line -> runCatching { UUID.fromString(line.goodsItemId) }.getOrNull() }
            if (supplierContractBlocksStoreSupplyInsideTransaction(storeId, supplierId, requestedGoodsItemIdsForContract) != null) {
              failureMessage = supplierContractGuardFailureMessage()
              return@newSuspendedTransaction null
            }

            val cleanOrder = body.order.cleanForStorage(existing[SupplierOrders.userId], storeId, supplierId, now)
            SupplierOrders.update({ SupplierOrders.id eq orderId }) {
              it.setSupplierOrderUpdateColumns(cleanOrder)
            }

            val requestedLineIds = body.lines.mapNotNull { runCatching { UUID.fromString(it.id) }.getOrNull() }.toSet()
            SupplierOrderLines
              .select(SupplierOrderLines.id)
              .where { SupplierOrderLines.orderId eq orderId }
              .map { it[SupplierOrderLines.id] }
              .filter { it !in requestedLineIds }
              .forEach { obsoleteLineId ->
                SupplierOrderLines.update({ SupplierOrderLines.id eq obsoleteLineId }) { it[SupplierOrderLines.isActive] = false }
              }

            body.lines
              .mapNotNull { line ->
                val goodsItemId = runCatching { UUID.fromString(line.goodsItemId) }.getOrNull()
                  ?: return@mapNotNull null
                val itemBelongsToStore = StockItems
                  .select(StockItems.id)
                  .where { (StockItems.id eq goodsItemId) and (StockItems.storeId eq storeId) and (StockItems.isActive eq true) }
                  .empty()
                  .not()
                if (!itemBelongsToStore) return@mapNotNull null
                line.cleanForStorage(orderId) to goodsItemId
              }
              .filter { (line, _) -> line.requestedQuantity.total > 0.0 }
              .forEach { (line, goodsItemId) ->
                val lineId = runCatching { UUID.fromString(line.id) }.getOrNull() ?: UUID.randomUUID()
                val exists = SupplierOrderLines.select(SupplierOrderLines.id).where { SupplierOrderLines.id eq lineId }.empty().not()
                if (exists) {
                  SupplierOrderLines.update({ SupplierOrderLines.id eq lineId }) {
                    it.setSupplierOrderLineUpdateColumns(line.copy(isActive = true))
                  }
                } else {
                  SupplierOrderLines.insert {
                    it.setSupplierOrderLineColumns(lineId, line.copy(isActive = true), orderId, goodsItemId)
                  }
                }
              }

            supplierOrderWithLinesInsideTransaction(orderId)
              ?.let { listOf(it).withSupplierDeskSnapshotsInsideTransaction().firstOrNull() }
          }

          result?.let {
            call.genericResponse(
              status = HttpStatusCode.OK,
              payload = it,
              message = simpleMessage(
                main = "Supplier order updated",
                ru = "Заказ поставщику обновлён",
                kk = "Жеткізуші тапсырысы жаңартылды"
              )
            )
          } ?: call.genericResponseNoPayload(
            HttpStatusCode.BadRequest,
            failureMessage ?: simpleMessage(
              main = "Cannot update supplier order",
              ru = "Не удалось обновить заказ поставщику",
              kk = "Жеткізуші тапсырысын жаңарту мүмкін болмады"
            )
          )
        }

        delete("/delete") {
          val userId = call.checkPrincipal() ?: return@delete
          val orderId = runCatching { UUID.fromString(call.receiveAita<String>().trim()) }.getOrNull()
            ?: return@delete call.respondAitaUnauthorized()

          val deleted = newSuspendedTransaction(aitaServerIoContext) {
            val existing = SupplierOrders.selectAll().where { SupplierOrders.id eq orderId }.singleOrNull()
              ?: return@newSuspendedTransaction false
            if (!userCanUseStoreActionInsideTransaction(userId, existing[SupplierOrders.storeId], STORE_PERMISSION_SUPPLIER_ORDERS_MANAGE, requireWorkshift = true)) return@newSuspendedTransaction false
            SupplierOrders.update({ SupplierOrders.id eq orderId }) {
              it[SupplierOrders.status] = SupplierOrderStatusDataModel.Cancelled.name
              it[SupplierOrders.isActive] = false
              it[SupplierOrders.updatedAtMillis] = System.currentTimeMillis()
            } > 0
          }

          if (deleted) {
            call.genericResponse(
              status = HttpStatusCode.OK,
              payload = orderId.toString(),
              message = simpleMessage(
                main = "Supplier order cancelled",
                ru = "Заказ поставщику отменён",
                kk = "Жеткізуші тапсырысы тоқтатылды"
              )
            )
          } else {
            call.respondAitaUnauthorized()
          }
        }

        post("/receive") {
          val userId = call.checkPrincipal() ?: return@post
          val request = call.receiveAita<ReceiveSupplierOrderRequestDataModel>()
          var failureMessage: List<LocalizedStringDataModel>? = null

          val result = newSuspendedTransaction(aitaServerIoContext) {
            val orderId = runCatching { UUID.fromString(request.orderId) }.getOrNull()
              ?: return@newSuspendedTransaction null
            val orderRow = SupplierOrders.selectAll().where { SupplierOrders.id eq orderId }.singleOrNull()
              ?: return@newSuspendedTransaction null
            val storeId = orderRow[SupplierOrders.storeId]
            val supplierId = orderRow[SupplierOrders.supplierId]
            if (!userCanUseStoreActionInsideTransaction(userId, storeId, STORE_PERMISSION_SUPPLIER_ORDERS_RECEIVE, requireWorkshift = true)) return@newSuspendedTransaction null
            val receivingGoodsItemIds = request.receivedLines.mapNotNull { received -> runCatching { UUID.fromString(received.goodsItemId) }.getOrNull() }
            if (supplierContractBlocksStoreSupplyInsideTransaction(storeId, supplierId, receivingGoodsItemIds) != null) {
              failureMessage = supplierContractGuardFailureMessage()
              return@newSuspendedTransaction null
            }

            val now = System.currentTimeMillis()
            request.receivedLines.forEach { received ->
              val lineId = runCatching { UUID.fromString(received.orderLineId) }.getOrNull() ?: return@forEach
              val lineRow = SupplierOrderLines
                .selectAll()
                .where { (SupplierOrderLines.id eq lineId) and (SupplierOrderLines.orderId eq orderId) and (SupplierOrderLines.isActive eq true) }
                .singleOrNull()
                ?: return@forEach
              val goodsItemId = runCatching { UUID.fromString(received.goodsItemId) }.getOrNull() ?: lineRow[SupplierOrderLines.goodsItemId]
              val itemRow = StockItems
                .selectAll()
                .where { (StockItems.id eq goodsItemId) and (StockItems.storeId eq storeId) and (StockItems.isActive eq true) }
                .singleOrNull()
                ?: return@forEach
              val quantity = received.receivedQuantity.copy(total = received.receivedQuantity.total.coerceAtLeast(0.0))
              if (quantity.total <= 0.0) return@forEach

              val batchId = UUID.randomUUID()
              val shelfPriority = StockBatchesV2
                .select(StockBatchesV2.id)
                .where { (StockBatchesV2.goodsItemId eq goodsItemId) and (StockBatchesV2.storeId eq storeId) and (StockBatchesV2.isActive eq true) }
                .count()
                .toInt()

              StockBatchesV2.insert {
                it[StockBatchesV2.id] = batchId
                it[StockBatchesV2.goodsItemId] = goodsItemId
                it[StockBatchesV2.userId] = userId
                it[StockBatchesV2.storeId] = storeId
                it[StockBatchesV2.supplierId] = supplierId
                it[StockBatchesV2.supplierOrderId] = orderId
                it[StockBatchesV2.quantity] = quantity
                it[StockBatchesV2.supplyPrice] = received.actualSupplyPrice.copy(supplierId = supplierId.toString())
                it[StockBatchesV2.salePriceOverride] = null
                it[StockBatchesV2.returnPriceOverride] = null
                it[StockBatchesV2.wholesalePriceOverride] = null
                it[StockBatchesV2.deliveredAtMillis] = now
                it[StockBatchesV2.manufacturedAtMillis] = received.manufacturedAtMillis
                it[StockBatchesV2.expirationDateMillis] = received.expirationDateMillis
                it[StockBatchesV2.discounts] = received.discounts
                it[StockBatchesV2.promotions] = received.promotions.sanitizedStockPromotions()
                it[StockBatchesV2.shelfPosition] = (shelfPriority + 1).toString()
                it[StockBatchesV2.shelfPriority] = shelfPriority
                it[StockBatchesV2.status] = StockBatchStatusDataModel.Delivered.name
                it[StockBatchesV2.additionalNotes] = received.notes?.takeIf { note -> note.isNotBlank() }
                it[StockBatchesV2.additionalNotesLocalized] = received.notesLocalized
                  .map { note -> note.copy(language = note.language.trim(), value = note.value.trim()) }
                  .filter { note -> note.language.isNotBlank() && note.value.isNotBlank() }
                  .distinctBy { note -> note.language }
                it[StockBatchesV2.createdAtMillis] = now
                it[StockBatchesV2.updatedAtMillis] = now
                it[StockBatchesV2.createdByUserId] = userId
                it[StockBatchesV2.isActive] = true
              }

              val deliveredBatchIds = (lineRow[SupplierOrderLines.deliveredBatchIds] + batchId.toString()).distinct()
              SupplierOrderLines.update({ SupplierOrderLines.id eq lineId }) {
                it[SupplierOrderLines.deliveredBatchIds] = deliveredBatchIds
              }
            }

            val activeLines = SupplierOrderLines
              .selectAll()
              .where { (SupplierOrderLines.orderId eq orderId) and (SupplierOrderLines.isActive eq true) }
              .map { it.toSupplierOrderLineDataModel() }
            val fullyDelivered = activeLines.isNotEmpty() && activeLines.all { it.deliveredBatchIds.isNotEmpty() }
            SupplierOrders.update({ SupplierOrders.id eq orderId }) {
              it[SupplierOrders.status] = if (fullyDelivered) SupplierOrderStatusDataModel.Delivered.name else SupplierOrderStatusDataModel.PartiallyDelivered.name
              it[SupplierOrders.deliveredAtMillis] = if (fullyDelivered) now else null
              it[SupplierOrders.updatedAtMillis] = now
            }

            supplierOrderWithLinesInsideTransaction(orderId)
          }

          result?.let {
            call.genericResponse(
              status = HttpStatusCode.OK,
              payload = it,
              message = simpleMessage(
                main = "Supplier order received",
                ru = "Заказ поставщика принят",
                kk = "Жеткізуші тапсырысы қабылданды"
              )
            )
          } ?: call.genericResponseNoPayload(
            HttpStatusCode.BadRequest,
            failureMessage ?: simpleMessage(
              main = "Cannot receive supplier order",
              ru = "Не удалось принять заказ поставщика",
              kk = "Жеткізуші тапсырысын қабылдау мүмкін болмады"
            )
          )
        }
      }
    }

    route("/finance") {
      authenticate("auth-jwt") {
        get("/dashboard") {
          val userId = call.checkPrincipal() ?: return@get
          val dashboard = newSuspendedTransaction(aitaServerIoContext) {
            financeDashboardInsideTransaction(userId)
          }
          dashboard?.let {
            call.genericResponse(
              status = HttpStatusCode.OK,
              payload = it,
              message = simpleMessage(
                main = "Finance dashboard loaded",
                ru = "Финансы загружены",
                kk = "Қаржы жүктелді"
              )
            )
          } ?: call.respondAitaUnauthorized()
        }

        post("/topup/create") {
          val userId = call.checkPrincipal() ?: return@post
          val body = call.receiveAita<TopUpCreateRequestDataModel>()

          val result = newSuspendedTransaction(aitaServerIoContext) {
            val wallet = ensureUserWalletInsideTransaction(userId) ?: return@newSuspendedTransaction null
            val amountMinor = body.amount.toMinorCurrencyUnits()
            if (amountMinor <= 0L || !body.currencyCode.equals(wallet.currencyCode, ignoreCase = true))
              return@newSuspendedTransaction null

            val provider = defaultPaymentProviders().firstOrNull { it.id == body.providerId }
              ?: return@newSuspendedTransaction null
            val now = System.currentTimeMillis()
            val intentId = UUID.randomUUID()
            val providerInvoiceId = "AITA-${intentId.toString().take(8).uppercase()}"
            val metadata = linkedMapOf<String, String>(
              "providerEnabled" to provider.enabled.toString(),
              "providerSandbox" to provider.sandbox.toString(),
              "comment" to body.comment
            )

            TopUpPaymentIntents.insert {
              it[TopUpPaymentIntents.id] = intentId
              it[TopUpPaymentIntents.userId] = userId
              it[TopUpPaymentIntents.providerId] = provider.id
              it[TopUpPaymentIntents.amountMinor] = amountMinor
              it[TopUpPaymentIntents.currencyCode] = wallet.currencyCode
              it[TopUpPaymentIntents.status] = PAYMENT_STATUS_WAITING
              it[TopUpPaymentIntents.providerInvoiceId] = providerInvoiceId
              it[TopUpPaymentIntents.paymentUrl] = if (provider.id == PAYMENT_PROVIDER_KASPI_INVOICE) "kaspi://invoice/$providerInvoiceId" else ""
              it[TopUpPaymentIntents.qrPayload] = providerInvoiceId
              it[TopUpPaymentIntents.createdAtMillis] = now
              it[TopUpPaymentIntents.expiresAtMillis] = now + 30L * 60L * 1000L
              it[TopUpPaymentIntents.metadata] = metadata
            }

            TopUpPaymentIntents
              .selectAll()
              .where { TopUpPaymentIntents.id eq intentId }
              .single()
              .toTopUpPaymentIntentDataModel()
          }

          result?.let {
            call.genericResponse(
              status = HttpStatusCode.OK,
              payload = it,
              message = simpleMessage(
                main = "Top-up invoice created",
                ru = "Счёт на пополнение создан",
                kk = "Толтыру шоты жасалды"
              )
            )
          } ?: call.genericResponseNoPayload(
            HttpStatusCode.BadRequest,
            simpleMessage(
              main = "Cannot create top-up invoice",
              ru = "Не удалось создать счёт на пополнение",
              kk = "Толтыру шотын жасау мүмкін болмады"
            )
          )
        }

        post("/topup/confirmDevelopment") {
          val userId = call.checkPrincipal() ?: return@post
          val body = call.receiveAita<TopUpConfirmDevelopmentRequestDataModel>()

          val dashboard = newSuspendedTransaction(aitaServerIoContext) {
            val intentId = runCatching { UUID.fromString(body.paymentIntentId) }.getOrNull()
              ?: return@newSuspendedTransaction null
            val intent = TopUpPaymentIntents
              .selectAll()
              .where { (TopUpPaymentIntents.id eq intentId) and (TopUpPaymentIntents.userId eq userId) }
              .singleOrNull()
              ?: return@newSuspendedTransaction null

            if (intent[TopUpPaymentIntents.status] == PAYMENT_STATUS_PAID)
              return@newSuspendedTransaction financeDashboardInsideTransaction(userId)

            val now = System.currentTimeMillis()
            val ledger = addWalletLedgerInsideTransaction(
              userId = userId,
              type = WALLET_LEDGER_TOP_UP,
              amountMinor = intent[TopUpPaymentIntents.amountMinor],
              referenceType = "top_up_payment_intent",
              referenceId = intentId.toString(),
              note = "Development confirmation"
            ) ?: return@newSuspendedTransaction null

            TopUpPaymentIntents.update({ TopUpPaymentIntents.id eq intentId }) {
              it[TopUpPaymentIntents.status] = PAYMENT_STATUS_PAID
              it[TopUpPaymentIntents.paidAtMillis] = now
              it[TopUpPaymentIntents.metadata] = intent[TopUpPaymentIntents.metadata] + mapOf("walletLedgerEntryId" to ledger.id)
            }

            financeDashboardInsideTransaction(userId)
          }

          dashboard?.let {
            call.genericResponse(
              status = HttpStatusCode.OK,
              payload = it,
              message = simpleMessage(
                main = "Balance topped up",
                ru = "Баланс пополнен",
                kk = "Баланс толтырылды"
              )
            )
          } ?: call.genericResponseNoPayload(
            HttpStatusCode.BadRequest,
            simpleMessage(
              main = "Cannot confirm payment",
              ru = "Не удалось подтвердить платёж",
              kk = "Төлемді растау мүмкін болмады"
            )
          )
        }
      }
    }

    route("/subscriptions") {
      authenticate("auth-jwt") {
        get("/plans") {
          call.genericListResponse(
            status = HttpStatusCode.OK,
            payload = defaultStoreSubscriptionPlans(),
            message = simpleMessage(
              main = "Subscription plans loaded",
              ru = "Планы подписки загружены",
              kk = "Жазылым жоспарлары жүктелді"
            )
          )
        }

        get("/store/get") {
          val userId = call.checkPrincipal() ?: return@get
          val storeId = call.headerUuid("store_id") ?: return@get call.respondAitaUnauthorized()
          val dashboard = newSuspendedTransaction(aitaServerIoContext) {
            if (!userCanUseStoreActionInsideTransaction(userId, storeId, STORE_PERMISSION_STORE_MANAGE, requireWorkshift = false) && !userCanUseStoreActionInsideTransaction(userId, storeId, STORE_PERMISSION_SUBSCRIPTION_MANAGE, requireWorkshift = false)) return@newSuspendedTransaction null
            subscriptionDashboardInsideTransaction(storeId)
          }
          dashboard?.let {
            call.genericResponse(
              status = HttpStatusCode.OK,
              payload = it,
              message = simpleMessage(
                main = "Store subscription loaded",
                ru = "Подписка магазина загружена",
                kk = "Дүкен жазылымы жүктелді"
              )
            )
          } ?: call.respondAitaUnauthorized()
        }

        post("/store/update") {
          val userId = call.checkPrincipal() ?: return@post
          val body = call.receiveAita<StoreSubscriptionUpdateRequestDataModel>()
          val storeId = runCatching { UUID.fromString(body.storeId) }.getOrNull()
            ?: return@post call.respondAitaUnauthorized()

          val dashboard = newSuspendedTransaction(aitaServerIoContext) {
            val rootStoreId = rootStoreIdForAccessInsideTransaction(storeId)
            if (!isStoreOwnerInsideTransaction(userId, rootStoreId) && !userCanUseStoreActionInsideTransaction(userId, rootStoreId, STORE_PERMISSION_SUBSCRIPTION_MANAGE, requireWorkshift = false)) return@newSuspendedTransaction null
            val plan = defaultStoreSubscriptionPlans().firstOrNull { it.id == body.planId && it.isActive }
              ?: return@newSuspendedTransaction null
            val subscription = ensureStoreSubscriptionInsideTransaction(rootStoreId) ?: return@newSuspendedTransaction null
            val now = System.currentTimeMillis()
            val periodStart = now
            val periodEnd = nextPeriodEndMillis(periodStart, plan)

            if (body.activateNow) {
              val ledger = addWalletLedgerInsideTransaction(
                userId = userId,
                type = WALLET_LEDGER_SUBSCRIPTION_CHARGE,
                amountMinor = -plan.priceMinor,
                referenceType = "store_subscription",
                referenceId = subscription.id,
                note = "${plan.id} activation"
              ) ?: return@newSuspendedTransaction null

              StoreSubscriptionChargeEvents.insert {
                it[StoreSubscriptionChargeEvents.id] = UUID.randomUUID()
                it[StoreSubscriptionChargeEvents.storeId] = rootStoreId
                it[StoreSubscriptionChargeEvents.userId] = userId
                it[StoreSubscriptionChargeEvents.planId] = plan.id
                it[StoreSubscriptionChargeEvents.amountMinor] = plan.priceMinor
                it[StoreSubscriptionChargeEvents.currencyCode] = plan.currencyCode
                it[StoreSubscriptionChargeEvents.periodStartMillis] = periodStart
                it[StoreSubscriptionChargeEvents.periodEndMillis] = periodEnd
                it[StoreSubscriptionChargeEvents.status] = "paid"
                it[StoreSubscriptionChargeEvents.walletLedgerEntryId] = ledger.id
                it[StoreSubscriptionChargeEvents.createdAtMillis] = now
                it[StoreSubscriptionChargeEvents.note] = "Initial subscription activation"
              }
            }

            StoreSubscriptionStates.update({ StoreSubscriptionStates.id eq UUID.fromString(subscription.id) }) {
              it[StoreSubscriptionStates.planId] = plan.id
              it[StoreSubscriptionStates.status] = SUBSCRIPTION_STATUS_ACTIVE
              it[StoreSubscriptionStates.autoRenew] = body.autoRenew
              it[StoreSubscriptionStates.startedAtMillis] = subscription.startedAtMillis ?: now
              it[StoreSubscriptionStates.currentPeriodStartMillis] = periodStart
              it[StoreSubscriptionStates.currentPeriodEndMillis] = periodEnd
              it[StoreSubscriptionStates.nextChargeAtMillis] = periodEnd
              it[StoreSubscriptionStates.cancelledAtMillis] = null
              it[StoreSubscriptionStates.pastDueSinceMillis] = null
              it[StoreSubscriptionStates.updatedAtMillis] = now
            }

            subscriptionDashboardInsideTransaction(rootStoreId)
          }

          dashboard?.let {
            call.genericResponse(
              status = HttpStatusCode.OK,
              payload = it,
              message = simpleMessage(
                main = "Subscription updated",
                ru = "Подписка обновлена",
                kk = "Жазылым жаңартылды"
              )
            )
          } ?: call.genericResponseNoPayload(
            HttpStatusCode.BadRequest,
            simpleMessage(
              main = "Cannot update subscription. Check balance and permissions.",
              ru = "Не удалось обновить подписку. Проверьте баланс и права.",
              kk = "Жазылымды жаңарту мүмкін болмады. Баланс пен құқықтарды тексеріңіз."
            )
          )
        }
      }
    }

    route("/balance") {
      authenticate("auth-jwt") {
        get("/get") {
          val userId = call.checkPrincipal() ?: return@get

          val balance = newSuspendedTransaction(aitaServerIoContext) {
            val noUser = Users
              .select(Users.id)
              .where { Users.id eq userId }
              .empty()

            if (noUser)
              call.respondAitaUnauthorized()

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
//          val body = call.receiveAita<StoreDataModel>()
//
//          val updated = newSuspendedTransaction(aitaServerIoContext) {
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
//            1, 2 -> call.respondAitaUnauthorized()
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

          val user = newSuspendedTransaction(aitaServerIoContext) {
            Users
              .selectAll()
              .where {
                Users.id eq uuid
              }
              .limit(1)
              .singleOrNull()
              ?.toUserAccountDataModel()
          } ?: return@get call.respondAitaUnauthorized()

          call.genericResponse(
            HttpStatusCode.OK,
            user
          )
        }

        put("/preferences/update") {
          val uuid = call.checkPrincipal() ?: return@put
          val body = call.receiveAita<UserPreferencesDataModel>()
          val language = normalizeAppLanguagePreference(body.appLanguage)
          val themeId = normalizeAppThemePreference(body.appThemeId)
          val sizeModeId = normalizeAppSizeModePreference(body.appSizeModeId)

          val updatedUser = newSuspendedTransaction(aitaServerIoContext) {
            val existing = Users
              .selectAll()
              .where { Users.id eq uuid }
              .forUpdate()
              .limit(1)
              .singleOrNull() ?: return@newSuspendedTransaction null

            Users.update({ Users.id eq uuid }) {
              if (existing[Users.appLanguage] != language) it[Users.appLanguage] = language
              if (existing[Users.appThemeId] != themeId) it[Users.appThemeId] = themeId
              if (existing[Users.appSizeModeId] != sizeModeId) it[Users.appSizeModeId] = sizeModeId
            }

            Users
              .selectAll()
              .where { Users.id eq uuid }
              .limit(1)
              .singleOrNull()
              ?.toUserAccountDataModel()
          } ?: return@put call.respondAitaUnauthorized()

          call.genericResponse(
            HttpStatusCode.OK,
            updatedUser,
            message = getResponse("9").message
          )
        }

        put("/update") {
          val uuid = call.checkPrincipal() ?: return@put

          val body = call.receiveAita<UserAccountUpdateDataModel>()
          val newAccount = body.account
          val cleanNewPassword = body.newPassword?.trim()?.takeIf { it.isNotBlank() }

          if (cleanNewPassword != null && !cleanNewPassword.checkAsPassword()) {
            return@put call.genericResponseNoPayload(
              HttpStatusCode.BadRequest,
              message = passwordRequirementMessage()
            )
          }

          val phoneNumber = newAccount.phoneNumber.trim().lowercase()
          val email = newAccount.email.trim().lowercase()
          val firstName = newAccount.firstName.trim()
          val lastName = newAccount.lastName.trim()
          val countryLocale = newAccount.countryLocale.trim().lowercase()
          val isActive = newAccount.isActive

          val updated = newSuspendedTransaction(aitaServerIoContext) {
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

            val newHash = cleanNewPassword
              ?.takeIf { !Pw.verify(it.toCharArray(), existingUser[Users.passwordHash]) }
              ?.let { Pw.hash(it.toCharArray()) }

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

              val appLanguage = normalizeAppLanguagePreference(newAccount.appLanguage)
              val appThemeId = normalizeAppThemePreference(newAccount.appThemeId)
              val appSizeModeId = normalizeAppSizeModePreference(newAccount.appSizeModeId)

              if (existingUser[Users.appLanguage] != appLanguage)
                it[Users.appLanguage] = appLanguage

              if (existingUser[Users.appThemeId] != appThemeId)
                it[Users.appThemeId] = appThemeId

              if (existingUser[Users.appSizeModeId] != appSizeModeId)
                it[Users.appSizeModeId] = appSizeModeId

              newHash?.run {
                it[Users.passwordHash] = this
              }

              if (existingUser[Users.isActive] != isActive)
                it[Users.isActive] = isActive
            }

            "ok"
          }

          when (updated) {
            "ok" -> {
              val refreshed = newSuspendedTransaction(aitaServerIoContext) {
                Users
                  .selectAll()
                  .where { Users.id eq uuid }
                  .limit(1)
                  .singleOrNull()
                  ?.toUserAccountDataModel()
              }

              call.genericResponse(
                HttpStatusCode.OK,
                payload = refreshed ?: body.account,
                message = getResponse("9").message
              )
            }

            "unauthorized", "password_mismatch" -> call.respondAitaUnauthorized()

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


    route("/debtors") {
      authenticate("auth-jwt") {
        get("/get") {
          val userId = call.checkPrincipal() ?: return@get
          val storeId = call.headerUuid("store_id") ?: return@get call.respondAitaUnauthorized()

          val debtors = newSuspendedTransaction(aitaServerIoContext) {
            if (!userCanUseStoreActionInsideTransaction(userId, storeId, STORE_PERMISSION_DEBTORS_VIEW, requireWorkshift = false))
              return@newSuspendedTransaction null

            Debtors
              .selectAll()
              .where {
                (Debtors.userId eq userId) and
                   (Debtors.storeId eq storeId) and
                   (Debtors.isActive eq true)
              }
              .map { it.toDebtorDataModel() }
          }

          debtors?.let {
            call.genericResponse(HttpStatusCode.OK, it)
          } ?: call.respondAitaUnauthorized()
        }

        post("/add") {
          val userId = call.checkPrincipal() ?: return@post
          val storeId = call.headerUuid("store_id") ?: return@post call.respondAitaUnauthorized()
          val body = call.receiveAita<DebtorDataModel>()

          val debtor = newSuspendedTransaction(aitaServerIoContext) {
            if (!userCanUseStoreActionInsideTransaction(userId, storeId, STORE_PERMISSION_DEBTORS_MANAGE, requireWorkshift = true))
              return@newSuspendedTransaction null

            upsertDebtorInsideTransaction(
              userId = userId,
              storeId = storeId,
              debtor = body.copy(debtAmount = body.debtAmount.coerceAtLeast(0.0))
            )
          }

          debtor?.let {
            call.genericResponse(
              HttpStatusCode.Created,
              it,
              simpleMessage("Debtor saved", ru = "Должник сохранён", kk = "Борышкер сақталды")
            )
          } ?: call.respondAitaUnauthorized()
        }

        put("/update") {
          val userId = call.checkPrincipal() ?: return@put
          val storeId = call.headerUuid("store_id") ?: return@put call.respondAitaUnauthorized()
          val body = call.receiveAita<DebtorDataModel>()
          val debtorId = runCatching { UUID.fromString(body.id) }.getOrNull()
            ?: return@put call.respondAitaUnauthorized()

          val debtor = newSuspendedTransaction(aitaServerIoContext) {
            if (!userCanUseStoreActionInsideTransaction(userId, storeId, STORE_PERMISSION_DEBTORS_MANAGE, requireWorkshift = true))
              return@newSuspendedTransaction null

            val existing = Debtors
              .selectAll()
              .where {
                (Debtors.id eq debtorId) and
                   (Debtors.userId eq userId) and
                   (Debtors.storeId eq storeId) and
                   (Debtors.isActive eq true)
              }
              .singleOrNull() ?: return@newSuspendedTransaction null

            Debtors.update({ Debtors.id eq debtorId }) {
              it[Debtors.email] = body.email.trim()
              it[Debtors.debtAmount] = body.debtAmount.coerceAtLeast(0.0)
              it[Debtors.currency] = body.currency
              it[Debtors.phoneNumber] = body.phoneNumber.trim()
              it[Debtors.firstName] = body.firstName.trim()
              it[Debtors.lastName] = body.lastName.trim()
              it[Debtors.debtorType] = body.debtorType.ifBlank { existing[Debtors.debtorType] }
              it[Debtors.idNumber] = body.idNumber.trim()
              it[Debtors.companyName] = body.companyName.trim()
              it[Debtors.companyIdNumber] = body.companyIdNumber.trim()
              it[Debtors.debtCreatedAtMillis] = body.debtCreatedAtMillis.takeIf { value -> value > 0L } ?: existing[Debtors.debtCreatedAtMillis]
              it[Debtors.debtDueAtMillis] = body.debtDueAtMillis
              it[Debtors.originalDebtAmount] = body.originalDebtAmount ?: existing[Debtors.originalDebtAmount] ?: body.debtAmount.coerceAtLeast(0.0)
              it[Debtors.interest] = body.interest
              it[Debtors.plannedPayments] = body.plannedPayments
              it[Debtors.paymentHistory] = body.paymentHistory
              it[Debtors.transactionIds] = body.transactionIds.ifEmpty { existing[Debtors.transactionIds] }
              it[Debtors.updatedAt] = Instant.now()
            }

            Debtors.selectAll().where { Debtors.id eq debtorId }.single().toDebtorDataModel()
          }

          debtor?.let {
            call.genericResponse(
              HttpStatusCode.OK,
              it,
              simpleMessage("Debtor updated", ru = "Должник обновлён", kk = "Борышкер жаңартылды")
            )
          } ?: call.respondAitaUnauthorized()
        }

        delete("/delete") {
          val userId = call.checkPrincipal() ?: return@delete
          val storeId = call.headerUuid("store_id") ?: return@delete call.respondAitaUnauthorized()
          val body = call.receiveAita<String>()
          val debtorId = runCatching { UUID.fromString(body) }.getOrNull()
            ?: return@delete call.respondAitaUnauthorized()

          val deleted = newSuspendedTransaction(aitaServerIoContext) {
            if (!userCanUseStoreActionInsideTransaction(userId, storeId, STORE_PERMISSION_DEBTORS_MANAGE, requireWorkshift = true))
              return@newSuspendedTransaction null

            val affected = Debtors.update({
              (Debtors.id eq debtorId) and
                 (Debtors.userId eq userId) and
                 (Debtors.storeId eq storeId)
            }) {
              it[Debtors.isActive] = false
              it[Debtors.updatedAt] = Instant.now()
            }

            if (affected > 0) body else null
          }

          deleted?.let {
            call.genericResponse(
              HttpStatusCode.OK,
              it,
              simpleMessage("Debtor deleted", ru = "Должник удалён", kk = "Борышкер өшірілді")
            )
          } ?: call.respondAitaUnauthorized()
        }

        post("/pay") {
          val userId = call.checkPrincipal() ?: return@post
          val body = call.receiveAita<DebtPaymentRequestDataModel>()
          val storeId = runCatching { UUID.fromString(body.storeId) }.getOrNull()
            ?: return@post call.respondAitaUnauthorized()
          val debtorId = runCatching { UUID.fromString(body.debtorId) }.getOrNull()
            ?: return@post call.respondAitaUnauthorized()

          val debtor = newSuspendedTransaction(aitaServerIoContext) {
            if (!userCanUseStoreActionInsideTransaction(userId, storeId, STORE_PERMISSION_DEBTOR_PAYMENTS_MANAGE, requireWorkshift = true))
              return@newSuspendedTransaction null

            val existing = Debtors
              .selectAll()
              .where {
                (Debtors.id eq debtorId) and
                   (Debtors.userId eq userId) and
                   (Debtors.storeId eq storeId) and
                   (Debtors.isActive eq true)
              }
              .singleOrNull() ?: return@newSuspendedTransaction null

            val debtBefore = existing[Debtors.debtAmount].coerceAtLeast(0.0)
            val paidAmount = body.amount.coerceAtLeast(0.0).coerceAtMost(debtBefore)
            val newDebt = (debtBefore - paidAmount)
              .coerceAtLeast(0.0)
              .let { kotlin.math.floor(it * 100.0) / 100.0 }
            val timeMillis = body.timeMillis.takeIf { it > 0L } ?: System.currentTimeMillis()
            val historyItem = DebtPaymentRecordDataModel(
              id = UUID.randomUUID().toString(),
              amount = paidAmount,
              currency = body.currency,
              timeMillis = timeMillis,
              paymentKind = body.paymentKind,
              plannedPaymentId = body.plannedPaymentId,
              note = body.note,
              debtBefore = debtBefore,
              debtAfter = newDebt
            )
            val nextPlans = existing[Debtors.plannedPayments].map { plan ->
              if (plan.id == body.plannedPaymentId || (body.paymentKind == "full" && !plan.completed))
                plan.copy(completed = true, paidAtMillis = timeMillis)
              else
                plan
            }

            Debtors.update({ Debtors.id eq debtorId }) {
              it[Debtors.debtAmount] = newDebt
              it[Debtors.paymentHistory] = existing[Debtors.paymentHistory] + historyItem
              it[Debtors.plannedPayments] = nextPlans
              it[Debtors.updatedAt] = Instant.now()
            }

            Debtors.selectAll().where { Debtors.id eq debtorId }.single().toDebtorDataModel()
          }

          debtor?.let {
            call.genericResponse(
              HttpStatusCode.OK,
              it,
              simpleMessage("Debt payment saved", ru = "Оплата долга сохранена", kk = "Қарыз төлемі сақталды")
            )
          } ?: call.respondAitaUnauthorized()
        }
      }
    }


    route("/cashRegister") {
      authenticate("auth-jwt") {
        get("/get") {
          val userId = call.checkPrincipal() ?: return@get
          val storeId = call.headerUuid("store_id") ?: return@get call.respondAitaUnauthorized()

          val state = newSuspendedTransaction(aitaServerIoContext) {
            if (!userHasStoreAccessInsideTransaction(userId, storeId))
              return@newSuspendedTransaction null

            if (!userCanUseStoreActionInsideTransaction(userId, storeId, STORE_PERMISSION_CASH_REGISTER_VIEW, requireWorkshift = false))
              return@newSuspendedTransaction null

            cashRegisterStateInsideTransaction(storeId)
          }

          state?.let {
            call.genericResponse(HttpStatusCode.OK, payload = it, message = getResponse("49").message)
          } ?: call.respondAitaUnauthorized()
        }

        post("/extract") {
          val userId = call.checkPrincipal() ?: return@post
          val body = call.receiveAita<CashRegisterExtractionRequestDataModel>()
          val storeId = runCatching { UUID.fromString(body.storeId) }.getOrNull()
            ?: call.headerUuid("store_id")
            ?: return@post call.respondAitaUnauthorized()
          val now = body.timeMillis.takeIf { it > 0L } ?: System.currentTimeMillis()
          val amount = kotlin.math.floor(body.amount.coerceAtLeast(0.0) * 100.0) / 100.0

          var failureMessage: List<LocalizedStringDataModel>? = null

          val state = newSuspendedTransaction(aitaServerIoContext) {
            if (!userHasStoreAccessInsideTransaction(userId, storeId))
              return@newSuspendedTransaction null

            if (!userCanUseStoreActionInsideTransaction(userId, storeId, STORE_PERMISSION_CASH_REGISTER_EXTRACT)) {
              failureMessage = getResponse("59").message
              return@newSuspendedTransaction null
            }

            if (amount <= 0.0) {
              failureMessage = simpleMessage(
                main = "Amount must be greater than zero",
                ru = "Сумма должна быть больше нуля",
                kk = "Сома нөлден көп болуы керек"
              )
              return@newSuspendedTransaction null
            }

            val register = ensureCashRegisterInsideTransaction(storeId, now = now)
            if (register.currentAmount + 0.01 < amount) {
              failureMessage = getResponse("60").message
              return@newSuspendedTransaction null
            }

            val before = register.currentAmount
            val after = kotlin.math.floor((before - amount) * 100.0) / 100.0

            CashRegisters.update({ CashRegisters.storeId eq storeId }) {
              it[CashRegisters.currentAmount] = after
              it[CashRegisters.updatedAtMillis] = now
              it[CashRegisters.updatedAt] = Instant.now()
            }

            CashRegisterEvents.insert {
              it[CashRegisterEvents.id] = UUID.randomUUID()
              it[CashRegisterEvents.storeId] = storeId
              it[CashRegisterEvents.userId] = userId
              it[CashRegisterEvents.type] = CASH_REGISTER_EVENT_EXTRACTION
              it[CashRegisterEvents.amount] = amount
              it[CashRegisterEvents.balanceBefore] = before
              it[CashRegisterEvents.balanceAfter] = after
              it[CashRegisterEvents.transactionId] = null
              it[CashRegisterEvents.note] = body.note?.takeIf { note -> note.isNotBlank() }
              it[CashRegisterEvents.timeMillis] = now
              it[CashRegisterEvents.metadata] = mapOf("source" to "manual_extraction")
            }

            cashRegisterStateInsideTransaction(storeId)
          }

          state?.let {
            call.genericResponse(HttpStatusCode.Created, payload = it, message = getResponse("50").message)
          } ?: call.genericResponseNoPayload(HttpStatusCode.Conflict, failureMessage ?: getResponse("3").message)
        }
      }
    }

    route("/workers") {
      authenticate("auth-jwt") {
        get("/my/get") {
          val userId = call.checkPrincipal() ?: return@get
          val result = newSuspendedTransaction(aitaServerIoContext) {
            StoreWorkerMemberships
              .innerJoin(Stores, { StoreWorkerMemberships.storeId }, { Stores.id })
              .innerJoin(Users, { StoreWorkerMemberships.userId }, { Users.id })
              .selectAll()
              .where { (StoreWorkerMemberships.userId eq userId) and (StoreWorkerMemberships.isActive eq true) }
              .orderBy(StoreWorkerMemberships.acceptedAtMillis, SortOrder.DESC)
              .map { it.toStoreWorkerDataModel() }
          }
          call.genericResponse(HttpStatusCode.OK, payload = result, message = getResponse("52").message)
        }

        get("/store/get") {
          val userId = call.checkPrincipal() ?: return@get
          val storeId = call.headerUuid("store_id") ?: return@get call.respondAitaUnauthorized()

          val result = newSuspendedTransaction(aitaServerIoContext) {
            val visibleStoreIds = storeGroupIdsInsideTransaction(rootStoreIdForAccessInsideTransaction(storeId))
              .filter { candidateStoreId ->
                isStoreOwnerInsideTransaction(userId, candidateStoreId) ||
                   userCanUseStoreActionInsideTransaction(userId, candidateStoreId, STORE_PERMISSION_WORKERS_VIEW, requireWorkshift = false)
              }
              .distinct()

            if (visibleStoreIds.isEmpty()) return@newSuspendedTransaction null

            StoreWorkerMemberships
              .innerJoin(Stores, { StoreWorkerMemberships.storeId }, { Stores.id })
              .innerJoin(Users, { StoreWorkerMemberships.userId }, { Users.id })
              .selectAll()
              .where { (StoreWorkerMemberships.storeId inList visibleStoreIds) and (StoreWorkerMemberships.isActive eq true) }
              .orderBy(StoreWorkerMemberships.acceptedAtMillis, SortOrder.DESC)
              .map { it.toStoreWorkerDataModel() }
          }

          result?.let { call.genericResponse(HttpStatusCode.OK, payload = it, message = getResponse("51").message) }
            ?: call.respondAitaUnauthorized()
        }

        get("/roleTemplates/get") {
          val userId = call.checkPrincipal() ?: return@get
          val storeId = call.headerUuid("store_id") ?: return@get call.respondAitaUnauthorized()

          val result = newSuspendedTransaction(aitaServerIoContext) {
            val rootStoreId = rootStoreIdForAccessInsideTransaction(storeId)
            val canReadTemplates = isStoreOwnerInsideTransaction(userId, rootStoreId) ||
               userCanUseStoreActionInsideTransaction(userId, rootStoreId, STORE_PERMISSION_WORKERS_INVITE, requireWorkshift = false) ||
               userCanUseStoreActionInsideTransaction(userId, rootStoreId, STORE_PERMISSION_WORKERS_DECIDE_REQUESTS, requireWorkshift = false) ||
               userCanUseStoreActionInsideTransaction(userId, rootStoreId, STORE_PERMISSION_WORKERS_EDIT_PERMISSIONS, requireWorkshift = false) ||
               userCanUseStoreActionInsideTransaction(userId, rootStoreId, STORE_PERMISSION_WORKER_ROLE_TEMPLATES_MANAGE, requireWorkshift = false)

            if (!canReadTemplates) return@newSuspendedTransaction null

            StoreWorkerRoleTemplates
              .selectAll()
              .where { (StoreWorkerRoleTemplates.storeId eq rootStoreId) and (StoreWorkerRoleTemplates.isActive eq true) }
              .orderBy(StoreWorkerRoleTemplates.updatedAtMillis, SortOrder.DESC)
              .map { it.toStoreWorkerRoleTemplateDataModel() }
          }

          result?.let {
            call.genericResponse(
              status = HttpStatusCode.OK,
              payload = it,
              message = simpleMessage(
                main = "Worker role templates loaded",
                ru = "Шаблоны ролей работников загружены",
                kk = "Қызметкер рөлінің үлгілері жүктелді"
              )
            )
          } ?: call.respondAitaUnauthorized()
        }

        post("/roleTemplates/upsert") {
          val userId = call.checkPrincipal() ?: return@post
          val headerStoreId = call.headerUuid("store_id") ?: return@post call.respondAitaUnauthorized()
          val body = call.receiveAita<StoreWorkerRoleTemplateUpsertRequestDataModel>()
          val now = System.currentTimeMillis()
          var failureMessage: List<LocalizedStringDataModel>? = null

          val template = newSuspendedTransaction(aitaServerIoContext) {
            val bodyStoreId = runCatching { UUID.fromString(body.storeId) }.getOrNull() ?: headerStoreId
            val rootStoreId = rootStoreIdForAccessInsideTransaction(bodyStoreId)
            if (rootStoreIdForAccessInsideTransaction(headerStoreId) != rootStoreId) {
              failureMessage = getResponse("13").message
              return@newSuspendedTransaction null
            }

            if (!isStoreOwnerInsideTransaction(userId, rootStoreId) && !userCanUseStoreActionInsideTransaction(userId, rootStoreId, STORE_PERMISSION_WORKER_ROLE_TEMPLATES_MANAGE, requireWorkshift = false)) {
              failureMessage = getResponse("59").message
              return@newSuspendedTransaction null
            }

            val permissions = assignableStorePermissionsInsideTransaction(userId, rootStoreId, body.permissions)
            if (permissions == null) {
              failureMessage = getResponse("59").message
              return@newSuspendedTransaction null
            }

            val cleanName = body.name
              .map { it.copy(value = it.value.trim().take(64)) }
              .filter { it.value.isNotBlank() }
              .ifEmpty {
                failureMessage = simpleMessage(
                  main = "Role name is required",
                  ru = "Укажите название роли",
                  kk = "Рөл атауын көрсетіңіз"
                )
                return@newSuspendedTransaction null
              }
            val cleanDescription = body.description
              .map { it.copy(value = it.value.trim().take(180)) }
              .filter { it.value.isNotBlank() }
            val templateId = runCatching { UUID.fromString(body.id) }.getOrNull() ?: UUID.randomUUID()
            val existing = StoreWorkerRoleTemplates
              .select(StoreWorkerRoleTemplates.id)
              .where { (StoreWorkerRoleTemplates.id eq templateId) and (StoreWorkerRoleTemplates.storeId eq rootStoreId) }
              .singleOrNull()

            if (existing == null) {
              StoreWorkerRoleTemplates.insert {
                it[StoreWorkerRoleTemplates.id] = templateId
                it[StoreWorkerRoleTemplates.storeId] = rootStoreId
                it[StoreWorkerRoleTemplates.name] = cleanName
                it[StoreWorkerRoleTemplates.description] = cleanDescription
                it[StoreWorkerRoleTemplates.permissions] = permissions
                it[StoreWorkerRoleTemplates.createdAtMillis] = now
                it[StoreWorkerRoleTemplates.updatedAtMillis] = now
                it[StoreWorkerRoleTemplates.isActive] = true
              }
            } else {
              StoreWorkerRoleTemplates.update({ (StoreWorkerRoleTemplates.id eq templateId) and (StoreWorkerRoleTemplates.storeId eq rootStoreId) }) {
                it[StoreWorkerRoleTemplates.name] = cleanName
                it[StoreWorkerRoleTemplates.description] = cleanDescription
                it[StoreWorkerRoleTemplates.permissions] = permissions
                it[StoreWorkerRoleTemplates.updatedAtMillis] = now
                it[StoreWorkerRoleTemplates.updatedAt] = Instant.now()
                it[StoreWorkerRoleTemplates.isActive] = true
              }
            }

            StoreWorkerRoleTemplates
              .selectAll()
              .where { StoreWorkerRoleTemplates.id eq templateId }
              .single()
              .toStoreWorkerRoleTemplateDataModel()
          }

          template?.let {
            publishWorkerRealtimeBundle(it.storeId, "worker_role_template_saved")
            call.genericResponse(
              status = HttpStatusCode.OK,
              payload = it,
              message = simpleMessage(
                main = "Worker role template saved",
                ru = "Шаблон роли работника сохранён",
                kk = "Қызметкер рөлінің үлгісі сақталды"
              )
            )
          } ?: call.genericResponseNoPayload(HttpStatusCode.Conflict, failureMessage ?: getResponse("3").message)
        }

        post("/roleTemplates/delete") {
          val userId = call.checkPrincipal() ?: return@post
          val headerStoreId = call.headerUuid("store_id") ?: return@post call.respondAitaUnauthorized()
          val body = call.receiveAita<StoreWorkerRoleTemplateDeleteRequestDataModel>()
          val templateId = runCatching { UUID.fromString(body.templateId) }.getOrNull()
            ?: return@post call.genericResponseNoPayload(HttpStatusCode.BadRequest, getResponse("13").message)
          val now = System.currentTimeMillis()
          var failureMessage: List<LocalizedStringDataModel>? = null

          val template = newSuspendedTransaction(aitaServerIoContext) {
            val rootStoreId = rootStoreIdForAccessInsideTransaction(headerStoreId)
            if (!isStoreOwnerInsideTransaction(userId, rootStoreId) && !userCanUseStoreActionInsideTransaction(userId, rootStoreId, STORE_PERMISSION_WORKER_ROLE_TEMPLATES_MANAGE, requireWorkshift = false)) {
              failureMessage = getResponse("59").message
              return@newSuspendedTransaction null
            }

            val existing = StoreWorkerRoleTemplates
              .selectAll()
              .where { (StoreWorkerRoleTemplates.id eq templateId) and (StoreWorkerRoleTemplates.storeId eq rootStoreId) and (StoreWorkerRoleTemplates.isActive eq true) }
              .singleOrNull()
              ?: run {
                failureMessage = getResponse("13").message
                return@newSuspendedTransaction null
              }

            StoreWorkerRoleTemplates.update({ StoreWorkerRoleTemplates.id eq templateId }) {
              it[StoreWorkerRoleTemplates.isActive] = false
              it[StoreWorkerRoleTemplates.updatedAtMillis] = now
              it[StoreWorkerRoleTemplates.updatedAt] = Instant.now()
            }

            existing.toStoreWorkerRoleTemplateDataModel().copy(isActive = false, updatedAtMillis = now)
          }

          template?.let {
            publishWorkerRealtimeBundle(it.storeId, "worker_role_template_deleted")
            call.genericResponse(
              status = HttpStatusCode.OK,
              payload = it,
              message = simpleMessage(
                main = "Worker role template deleted",
                ru = "Шаблон роли работника удалён",
                kk = "Қызметкер рөлінің үлгісі жойылды"
              )
            )
          } ?: call.genericResponseNoPayload(HttpStatusCode.Conflict, failureMessage ?: getResponse("3").message)
        }

        get("/requests/my") {
          val userId = call.checkPrincipal() ?: return@get
          val result = newSuspendedTransaction(aitaServerIoContext) {
            StoreWorkerRequests
              .innerJoin(Stores, { StoreWorkerRequests.storeId }, { Stores.id })
              .innerJoin(Users, { StoreWorkerRequests.requesterUserId }, { Users.id })
              .selectAll()
              .where { StoreWorkerRequests.requesterUserId eq userId }
              .orderBy(StoreWorkerRequests.requestedAtMillis, SortOrder.DESC)
              .map { it.toStoreWorkerRequestDataModel() }
          }
          call.genericResponse(HttpStatusCode.OK, payload = result, message = getResponse("54").message)
        }

        get("/requests/incoming") {
          val userId = call.checkPrincipal() ?: return@get
          val storeId = call.headerUuid("store_id") ?: return@get call.respondAitaUnauthorized()

          val result = newSuspendedTransaction(aitaServerIoContext) {
            val requestStoreIds = storeGroupIdsInsideTransaction(rootStoreIdForAccessInsideTransaction(storeId))
              .filter { candidateStoreId ->
                isStoreOwnerInsideTransaction(userId, candidateStoreId) ||
                   userCanUseStoreActionInsideTransaction(userId, candidateStoreId, STORE_PERMISSION_WORKERS_DECIDE_REQUESTS, requireWorkshift = false)
              }
              .distinct()

            if (requestStoreIds.isEmpty()) return@newSuspendedTransaction null

            StoreWorkerRequests
              .innerJoin(Stores, { StoreWorkerRequests.storeId }, { Stores.id })
              .innerJoin(Users, { StoreWorkerRequests.requesterUserId }, { Users.id })
              .selectAll()
              .where { StoreWorkerRequests.storeId inList requestStoreIds }
              .orderBy(StoreWorkerRequests.requestedAtMillis, SortOrder.DESC)
              .map { it.toStoreWorkerRequestDataModel() }
          }

          result?.let { call.genericResponse(HttpStatusCode.OK, payload = it, message = getResponse("53").message) }
            ?: call.respondAitaUnauthorized()
        }

        post("/request") {
          val userId = call.checkPrincipal() ?: return@post
          val body = call.receiveAita<WorkerEmploymentRequestCreateDataModel>()
          val now = System.currentTimeMillis()
          val requestNote = cleanOptionalText(body.note)
          val requestNoteLocalized = localizedNoteForStorage(requestNote, body.noteLocalized)
          var failureMessage: List<LocalizedStringDataModel>? = null

          val request = newSuspendedTransaction(aitaServerIoContext) {
            val storeId = resolveStoreIdByPublicOrPrivateIdInsideTransaction(body.storeId)
            if (storeId == null) {
              failureMessage = getResponse("13").message
              return@newSuspendedTransaction null
            }

            val storeRow = Stores.selectAll().where { Stores.id eq storeId }.singleOrNull()
            if (storeRow == null) {
              failureMessage = getResponse("13").message
              return@newSuspendedTransaction null
            }

            if (storeRow[Stores.ownerUserIds].contains(userId.toString())) {
              failureMessage = simpleMessage(
                main = "You already own this store",
                ru = "Вы уже владеете этим магазином",
                kk = "Сіз бұл дүкеннің иесісіз"
              )
              return@newSuspendedTransaction null
            }

            val activeMembership = StoreWorkerMemberships
              .selectAll()
              .where { (StoreWorkerMemberships.storeId eq storeId) and (StoreWorkerMemberships.userId eq userId) and (StoreWorkerMemberships.isActive eq true) }
              .empty()
              .not()
            if (activeMembership) {
              failureMessage = getResponse("62").message
              return@newSuspendedTransaction null
            }

            val pending = StoreWorkerRequests
              .selectAll()
              .where { (StoreWorkerRequests.storeId eq storeId) and (StoreWorkerRequests.requesterUserId eq userId) and (StoreWorkerRequests.status inList listOf(WORKER_REQUEST_STATUS_PENDING, WORKER_REQUEST_STATUS_INVITED)) }
              .singleOrNull()
            if (pending != null) {
              failureMessage = getResponse("64").message
              return@newSuspendedTransaction null
            }

            val requestId = UUID.randomUUID()
            StoreWorkerRequests.insert {
              it[StoreWorkerRequests.id] = requestId
              it[StoreWorkerRequests.storeId] = storeId
              it[StoreWorkerRequests.requesterUserId] = userId
              it[StoreWorkerRequests.direction] = WORKER_REQUEST_DIRECTION_USER_TO_STORE
              it[StoreWorkerRequests.invitedByUserId] = null
              it[StoreWorkerRequests.status] = WORKER_REQUEST_STATUS_PENDING
              it[StoreWorkerRequests.requestedAtMillis] = now
              it[StoreWorkerRequests.roleId] = WORKER_ROLE_STANDARD
              it[StoreWorkerRequests.permissions] = STANDARD_STORE_PERMISSION_IDS
              it[StoreWorkerRequests.jobTitle] = ""
              it[StoreWorkerRequests.jobTitleLocalized] = emptyList()
              it[StoreWorkerRequests.salary] = ""
              it[StoreWorkerRequests.salaryCurrencyCode] = "KZT"
              it[StoreWorkerRequests.offerNote] = null
              it[StoreWorkerRequests.offerNoteLocalized] = emptyList()
              it[StoreWorkerRequests.workshiftPasswordHash] = null
              it[StoreWorkerRequests.note] = requestNote
              it[StoreWorkerRequests.noteLocalized] = requestNoteLocalized
              it[StoreWorkerRequests.responseNote] = null
              it[StoreWorkerRequests.responseNoteLocalized] = emptyList()
              it[StoreWorkerRequests.updatedAt] = Instant.now()
            }

            notifyEmploymentRequestCreatedInsideTransaction(
              requesterUserId = userId,
              storeId = storeId,
              requestId = requestId,
              nowMillis = now
            )

            StoreWorkerRequests
              .innerJoin(Stores, { StoreWorkerRequests.storeId }, { Stores.id })
              .innerJoin(Users, { StoreWorkerRequests.requesterUserId }, { Users.id })
              .selectAll()
              .where { StoreWorkerRequests.id eq requestId }
              .single()
              .toStoreWorkerRequestDataModel()
          }

          request?.let {
            publishWorkerRealtimeBundle(it.storeId, "employment_request_created")
            call.genericResponse(HttpStatusCode.Created, payload = it, message = getResponse("55").message)
          } ?: call.genericResponseNoPayload(HttpStatusCode.Conflict, failureMessage ?: getResponse("3").message)
        }

        post("/invite") {
          val userId = call.checkPrincipal() ?: return@post
          val storeId = call.headerUuid("store_id") ?: return@post call.respondAitaUnauthorized()
          val body = call.receiveAita<WorkerStoreInviteCreateDataModel>()
          val now = System.currentTimeMillis()
          val role = cleanWorkerRoleId(body.roleId)
          val requestedPermissions = cleanPermissionIds(body.permissions).ifEmpty { defaultStorePermissionsForRole(role) }
          val jobTitle = cleanWorkerJobTitle(body.jobTitle)
          val jobTitleLocalized = localizedNoteForStorage(jobTitle.takeIf { it.isNotBlank() }, body.jobTitleLocalized)
          val salary = cleanWorkerSalary(body.salary)
          val salaryCurrencyCode = cleanWorkerSalaryCurrencyCode(body.salaryCurrencyCode)
          val inviteNote = cleanOptionalText(body.note)
          val inviteNoteLocalized = localizedNoteForStorage(inviteNote, body.noteLocalized)
          var failureMessage: List<LocalizedStringDataModel>? = null

          val request = newSuspendedTransaction(aitaServerIoContext) {
            if (!isStoreOwnerInsideTransaction(userId, storeId) && !userCanUseStoreActionInsideTransaction(userId, storeId, STORE_PERMISSION_WORKERS_INVITE, requireWorkshift = false)) {
              failureMessage = getResponse("59").message
              return@newSuspendedTransaction null
            }

            val permissions = assignableStorePermissionsInsideTransaction(userId, storeId, requestedPermissions)
            if (permissions == null) {
              failureMessage = getResponse("59").message
              return@newSuspendedTransaction null
            }

            val invitedUserId = resolveUserIdByPublicOrPrivateIdInsideTransaction(body.userId)
            if (invitedUserId == null) {
              failureMessage = getResponse("63").message
              return@newSuspendedTransaction null
            }

            val storeRow = Stores.selectAll().where { Stores.id eq storeId }.singleOrNull()
            if (storeRow == null) {
              failureMessage = getResponse("13").message
              return@newSuspendedTransaction null
            }

            if (storeRow[Stores.ownerUserIds].contains(invitedUserId.toString())) {
              failureMessage = simpleMessage(
                main = "User already owns this store",
                ru = "Пользователь уже владеет этим магазином",
                kk = "Пайдаланушы бұл дүкеннің иесі"
              )
              return@newSuspendedTransaction null
            }

            val activeMembership = StoreWorkerMemberships
              .selectAll()
              .where { (StoreWorkerMemberships.storeId eq storeId) and (StoreWorkerMemberships.userId eq invitedUserId) and (StoreWorkerMemberships.isActive eq true) }
              .empty()
              .not()
            if (activeMembership) {
              failureMessage = getResponse("62").message
              return@newSuspendedTransaction null
            }

            val existingInvite = StoreWorkerRequests
              .selectAll()
              .where {
                (StoreWorkerRequests.storeId eq storeId) and
                   (StoreWorkerRequests.requesterUserId eq invitedUserId) and
                   (StoreWorkerRequests.status inList listOf(WORKER_REQUEST_STATUS_PENDING, WORKER_REQUEST_STATUS_INVITED))
              }
              .singleOrNull()
            if (existingInvite != null) {
              failureMessage = getResponse("64").message
              return@newSuspendedTransaction null
            }

            val requestId = UUID.randomUUID()
            StoreWorkerRequests.insert {
              it[StoreWorkerRequests.id] = requestId
              it[StoreWorkerRequests.storeId] = storeId
              it[StoreWorkerRequests.requesterUserId] = invitedUserId
              it[StoreWorkerRequests.direction] = WORKER_REQUEST_DIRECTION_STORE_TO_USER
              it[StoreWorkerRequests.invitedByUserId] = userId
              it[StoreWorkerRequests.status] = WORKER_REQUEST_STATUS_INVITED
              it[StoreWorkerRequests.requestedAtMillis] = now
              it[StoreWorkerRequests.roleId] = role
              it[StoreWorkerRequests.permissions] = permissions
              it[StoreWorkerRequests.jobTitle] = jobTitle
              it[StoreWorkerRequests.jobTitleLocalized] = jobTitleLocalized
              it[StoreWorkerRequests.salary] = salary
              it[StoreWorkerRequests.salaryCurrencyCode] = salaryCurrencyCode
              it[StoreWorkerRequests.offerNote] = inviteNote
              it[StoreWorkerRequests.offerNoteLocalized] = inviteNoteLocalized
              it[StoreWorkerRequests.workshiftPasswordHash] = null
              it[StoreWorkerRequests.note] = inviteNote
              it[StoreWorkerRequests.noteLocalized] = inviteNoteLocalized
              it[StoreWorkerRequests.responseNote] = null
              it[StoreWorkerRequests.responseNoteLocalized] = emptyList()
              it[StoreWorkerRequests.updatedAt] = Instant.now()
            }

            notifyEmploymentInviteCreatedInsideTransaction(
              invitedUserId = invitedUserId,
              inviterUserId = userId,
              storeId = storeId,
              requestId = requestId,
              nowMillis = now
            )

            StoreWorkerRequests
              .innerJoin(Stores, { StoreWorkerRequests.storeId }, { Stores.id })
              .innerJoin(Users, { StoreWorkerRequests.requesterUserId }, { Users.id })
              .selectAll()
              .where { StoreWorkerRequests.id eq requestId }
              .single()
              .toStoreWorkerRequestDataModel()
          }

          request?.let {
            publishWorkerRealtimeBundle(it.storeId, "employment_invite_created")
            call.genericResponse(HttpStatusCode.Created, payload = it, message = getResponse("65").message)
          } ?: call.genericResponseNoPayload(HttpStatusCode.Conflict, failureMessage ?: getResponse("3").message)
        }

        post("/invitations/accept") {
          val userId = call.checkPrincipal() ?: return@post
          val body = call.receiveAita<WorkerStoreInvitationDecisionDataModel>()
          val requestId = runCatching { UUID.fromString(body.requestId) }.getOrNull()
            ?: return@post call.genericResponseNoPayload(HttpStatusCode.BadRequest, getResponse("13").message)
          val now = System.currentTimeMillis()
          val responseNote = cleanOptionalText(body.responseNote ?: body.note)
          val responseNoteLocalized = localizedNoteForStorage(responseNote, body.responseNoteLocalized)
          var failureMessage: List<LocalizedStringDataModel>? = null

          val worker = newSuspendedTransaction(aitaServerIoContext) {
            val requestRow = StoreWorkerRequests
              .selectAll()
              .where {
                (StoreWorkerRequests.id eq requestId) and
                   (StoreWorkerRequests.requesterUserId eq userId) and
                   (StoreWorkerRequests.status eq WORKER_REQUEST_STATUS_INVITED)
              }
              .singleOrNull()
            if (requestRow == null) {
              failureMessage = getResponse("13").message
              return@newSuspendedTransaction null
            }

            val storeId = requestRow[StoreWorkerRequests.storeId]
            val accepterUserId = userId
            val existing = StoreWorkerMemberships
              .selectAll()
              .where { (StoreWorkerMemberships.storeId eq storeId) and (StoreWorkerMemberships.userId eq userId) and (StoreWorkerMemberships.isActive eq true) }
              .singleOrNull()

            val membershipId = existing?.get(StoreWorkerMemberships.id) ?: UUID.randomUUID()
            if (existing == null) {
              StoreWorkerMemberships.insert {
                it[StoreWorkerMemberships.id] = membershipId
                it[StoreWorkerMemberships.storeId] = storeId
                it[StoreWorkerMemberships.userId] = userId
                it[StoreWorkerMemberships.requestId] = requestId
                it[StoreWorkerMemberships.roleId] = requestRow[StoreWorkerRequests.roleId]
                it[StoreWorkerMemberships.permissions] = requestRow[StoreWorkerRequests.permissions]
                it[StoreWorkerMemberships.jobTitle] = requestRow[StoreWorkerRequests.jobTitle]
                it[StoreWorkerMemberships.jobTitleLocalized] = requestRow[StoreWorkerRequests.jobTitleLocalized]
                it[StoreWorkerMemberships.salary] = requestRow[StoreWorkerRequests.salary]
                it[StoreWorkerMemberships.salaryCurrencyCode] = requestRow[StoreWorkerRequests.salaryCurrencyCode]
                it[StoreWorkerMemberships.workshiftPasswordHash] = null
                it[StoreWorkerMemberships.requestedAtMillis] = requestRow[StoreWorkerRequests.requestedAtMillis]
                it[StoreWorkerMemberships.acceptedAtMillis] = now
                it[StoreWorkerMemberships.acceptedByUserId] = accepterUserId
                it[StoreWorkerMemberships.isActive] = true
              }
            } else {
              StoreWorkerMemberships.update({ StoreWorkerMemberships.id eq membershipId }) {
                it[StoreWorkerMemberships.roleId] = requestRow[StoreWorkerRequests.roleId]
                it[StoreWorkerMemberships.permissions] = requestRow[StoreWorkerRequests.permissions]
                it[StoreWorkerMemberships.jobTitle] = requestRow[StoreWorkerRequests.jobTitle]
                it[StoreWorkerMemberships.jobTitleLocalized] = requestRow[StoreWorkerRequests.jobTitleLocalized]
                it[StoreWorkerMemberships.salary] = requestRow[StoreWorkerRequests.salary]
                it[StoreWorkerMemberships.salaryCurrencyCode] = requestRow[StoreWorkerRequests.salaryCurrencyCode]
                it[StoreWorkerMemberships.workshiftPasswordHash] = null
                it[StoreWorkerMemberships.acceptedAtMillis] = now
                it[StoreWorkerMemberships.acceptedByUserId] = accepterUserId
                it[StoreWorkerMemberships.isActive] = true
                it[StoreWorkerMemberships.updatedAt] = Instant.now()
              }
            }

            StoreUsers.insertIgnore {
              it[StoreUsers.storeId] = storeId
              it[StoreUsers.userId] = userId
            }

            StoreWorkerRequests.update({ StoreWorkerRequests.id eq requestId }) {
              it[StoreWorkerRequests.status] = WORKER_REQUEST_STATUS_ACCEPTED
              it[StoreWorkerRequests.decidedAtMillis] = now
              it[StoreWorkerRequests.decidedByUserId] = userId
              it[StoreWorkerRequests.responseNote] = responseNote
              it[StoreWorkerRequests.responseNoteLocalized] = responseNoteLocalized
              it[StoreWorkerRequests.updatedAt] = Instant.now()
            }

            notifyEmploymentDecisionInsideTransaction(
              requestId = requestId,
              storeId = storeId,
              workerUserId = userId,
              actorUserId = userId,
              accepted = true,
              direction = requestRow[StoreWorkerRequests.direction],
              nowMillis = now
            )

            StoreWorkerMemberships
              .innerJoin(Stores, { StoreWorkerMemberships.storeId }, { Stores.id })
              .innerJoin(Users, { StoreWorkerMemberships.userId }, { Users.id })
              .selectAll()
              .where { StoreWorkerMemberships.id eq membershipId }
              .single()
              .toStoreWorkerDataModel()
          }

          worker?.let {
            publishWorkerRealtimeBundle(it.storeId, "employment_invitation_accepted")
            call.genericResponse(HttpStatusCode.OK, payload = it, message = getResponse("66").message)
          } ?: call.genericResponseNoPayload(HttpStatusCode.Conflict, failureMessage ?: getResponse("3").message)
        }

        post("/invitations/decline") {
          val userId = call.checkPrincipal() ?: return@post
          val body = call.receiveAita<WorkerStoreInvitationDecisionDataModel>()
          val requestId = runCatching { UUID.fromString(body.requestId) }.getOrNull()
            ?: return@post call.genericResponseNoPayload(HttpStatusCode.BadRequest, getResponse("13").message)
          val now = System.currentTimeMillis()
          val responseNote = cleanOptionalText(body.responseNote ?: body.note)
          val responseNoteLocalized = localizedNoteForStorage(responseNote, body.responseNoteLocalized)
          var failureMessage: List<LocalizedStringDataModel>? = null

          val request = newSuspendedTransaction(aitaServerIoContext) {
            val updated = StoreWorkerRequests.update({
              (StoreWorkerRequests.id eq requestId) and
                 (StoreWorkerRequests.requesterUserId eq userId) and
                 (StoreWorkerRequests.status eq WORKER_REQUEST_STATUS_INVITED)
            }) {
              it[StoreWorkerRequests.status] = WORKER_REQUEST_STATUS_DECLINED
              it[StoreWorkerRequests.decidedAtMillis] = now
              it[StoreWorkerRequests.decidedByUserId] = userId
              it[StoreWorkerRequests.responseNote] = responseNote
              it[StoreWorkerRequests.responseNoteLocalized] = responseNoteLocalized
              it[StoreWorkerRequests.updatedAt] = Instant.now()
            }

            if (updated <= 0) {
              failureMessage = getResponse("13").message
              return@newSuspendedTransaction null
            }

            val requestRow = StoreWorkerRequests
              .innerJoin(Stores, { StoreWorkerRequests.storeId }, { Stores.id })
              .innerJoin(Users, { StoreWorkerRequests.requesterUserId }, { Users.id })
              .selectAll()
              .where { StoreWorkerRequests.id eq requestId }
              .single()

            notifyEmploymentDecisionInsideTransaction(
              requestId = requestId,
              storeId = requestRow[StoreWorkerRequests.storeId],
              workerUserId = userId,
              actorUserId = userId,
              accepted = false,
              direction = requestRow[StoreWorkerRequests.direction],
              nowMillis = now
            )

            requestRow.toStoreWorkerRequestDataModel()
          }

          request?.let {
            publishWorkerRealtimeBundle(it.storeId, "employment_invitation_declined")
            call.genericResponse(HttpStatusCode.OK, payload = it, message = getResponse("67").message)
          } ?: call.genericResponseNoPayload(HttpStatusCode.Conflict, failureMessage ?: getResponse("3").message)
        }

        post("/accept") {
          val userId = call.checkPrincipal() ?: return@post
          val headerStoreId = call.headerUuid("store_id") ?: return@post call.respondAitaUnauthorized()
          val body = call.receiveAita<WorkerEmploymentDecisionRequestDataModel>()
          val requestId = runCatching { UUID.fromString(body.requestId) }.getOrNull()
            ?: return@post call.genericResponseNoPayload(HttpStatusCode.BadRequest, getResponse("13").message)
          val now = System.currentTimeMillis()
          val role = cleanWorkerRoleId(body.roleId)
          val requestedPermissions = cleanPermissionIds(body.permissions).ifEmpty { defaultStorePermissionsForRole(role) }
          val jobTitle = cleanWorkerJobTitle(body.jobTitle)
          val jobTitleLocalized = localizedNoteForStorage(jobTitle.takeIf { it.isNotBlank() }, body.jobTitleLocalized)
          val salary = cleanWorkerSalary(body.salary)
          val salaryCurrencyCode = cleanWorkerSalaryCurrencyCode(body.salaryCurrencyCode)
          val responseNote = cleanOptionalText(body.responseNote ?: body.note)
          val responseNoteLocalized = localizedNoteForStorage(responseNote, body.responseNoteLocalized)
          var failureMessage: List<LocalizedStringDataModel>? = null
          var alreadyOffered = false

          val request = newSuspendedTransaction(aitaServerIoContext) {
            val requestRow = StoreWorkerRequests
              .selectAll()
              .where { StoreWorkerRequests.id eq requestId }
              .singleOrNull()

            if (requestRow == null) {
              failureMessage = getResponse("13").message
              return@newSuspendedTransaction null
            }

            val requestDirection = requestRow[StoreWorkerRequests.direction]
            if (requestDirection != WORKER_REQUEST_DIRECTION_USER_TO_STORE) {
              failureMessage = getResponse("13").message
              return@newSuspendedTransaction null
            }

            val requestStoreId = requestRow[StoreWorkerRequests.storeId]
            val headerRootStoreId = rootStoreIdForAccessInsideTransaction(headerStoreId)
            val requestRootStoreId = rootStoreIdForAccessInsideTransaction(requestStoreId)
            if (headerRootStoreId != requestRootStoreId) {
              failureMessage = getResponse("13").message
              return@newSuspendedTransaction null
            }

            if (!isStoreOwnerInsideTransaction(userId, requestStoreId) && !userCanUseStoreActionInsideTransaction(userId, requestStoreId, STORE_PERMISSION_WORKERS_DECIDE_REQUESTS, requireWorkshift = false)) {
              failureMessage = getResponse("59").message
              return@newSuspendedTransaction null
            }

            val permissions = assignableStorePermissionsInsideTransaction(userId, requestStoreId, requestedPermissions)
            if (permissions == null) {
              failureMessage = getResponse("59").message
              return@newSuspendedTransaction null
            }

            val workerUserId = requestRow[StoreWorkerRequests.requesterUserId]
            val activeMembership = StoreWorkerMemberships
              .selectAll()
              .where { (StoreWorkerMemberships.storeId eq requestStoreId) and (StoreWorkerMemberships.userId eq workerUserId) and (StoreWorkerMemberships.isActive eq true) }
              .empty()
              .not()
            if (activeMembership) {
              failureMessage = getResponse("62").message
              return@newSuspendedTransaction null
            }

            val currentStatus = requestRow[StoreWorkerRequests.status]

            when (currentStatus) {
              WORKER_REQUEST_STATUS_DECLINED -> {
                failureMessage = simpleMessage(
                  main = "This employment request has already been declined",
                  ru = "Эта заявка на работу уже отклонена",
                  kk = "Бұл жұмысқа өтінім бұрын қабылданбаған"
                )
                return@newSuspendedTransaction null
              }
              WORKER_REQUEST_STATUS_INVITED -> alreadyOffered = true
              WORKER_REQUEST_STATUS_ACCEPTED -> {
                failureMessage = simpleMessage(
                  main = "This employment request has already been accepted",
                  ru = "Эта заявка на работу уже принята",
                  kk = "Бұл жұмысқа өтінім бұрын қабылданған"
                )
                return@newSuspendedTransaction null
              }
            }

            StoreWorkerRequests.update({ StoreWorkerRequests.id eq requestId }) {
              it[StoreWorkerRequests.status] = WORKER_REQUEST_STATUS_INVITED
              it[StoreWorkerRequests.decidedAtMillis] = now
              it[StoreWorkerRequests.decidedByUserId] = userId
              it[StoreWorkerRequests.roleId] = role
              it[StoreWorkerRequests.permissions] = permissions
              it[StoreWorkerRequests.jobTitle] = jobTitle
              it[StoreWorkerRequests.jobTitleLocalized] = jobTitleLocalized
              it[StoreWorkerRequests.salary] = salary
              it[StoreWorkerRequests.salaryCurrencyCode] = salaryCurrencyCode
              it[StoreWorkerRequests.offerNote] = responseNote
              it[StoreWorkerRequests.offerNoteLocalized] = responseNoteLocalized
              it[StoreWorkerRequests.updatedAt] = Instant.now()
            }

            if (!alreadyOffered) {
              notifyEmploymentInviteCreatedInsideTransaction(
                invitedUserId = workerUserId,
                inviterUserId = userId,
                storeId = requestStoreId,
                requestId = requestId,
                nowMillis = now
              )
            }

            StoreWorkerRequests
              .innerJoin(Stores, { StoreWorkerRequests.storeId }, { Stores.id })
              .innerJoin(Users, { StoreWorkerRequests.requesterUserId }, { Users.id })
              .selectAll()
              .where { StoreWorkerRequests.id eq requestId }
              .single()
              .toStoreWorkerRequestDataModel()
          }

          request?.let {
            publishWorkerRealtimeBundle(it.storeId, if (alreadyOffered) "employment_offer_already_waiting" else "employment_offer_sent")
            call.genericResponse(HttpStatusCode.OK, payload = it, message = simpleMessage(
              main = if (alreadyOffered) "Job offer is already waiting for worker" else "Job offer sent to worker",
              ru = if (alreadyOffered) "Предложение работы уже ожидает работника" else "Предложение работы отправлено работнику",
              kk = if (alreadyOffered) "Жұмыс ұсынысы қызметкерді күтіп тұр" else "Жұмыс ұсынысы қызметкерге жіберілді"
            ))
          } ?: call.genericResponseNoPayload(HttpStatusCode.Conflict, failureMessage ?: getResponse("3").message)
        }

        post("/decline") {
          val userId = call.checkPrincipal() ?: return@post
          val headerStoreId = call.headerUuid("store_id") ?: return@post call.respondAitaUnauthorized()
          val body = call.receiveAita<WorkerEmploymentDecisionRequestDataModel>()
          val requestId = runCatching { UUID.fromString(body.requestId) }.getOrNull()
            ?: return@post call.genericResponseNoPayload(HttpStatusCode.BadRequest, getResponse("13").message)
          val now = System.currentTimeMillis()
          val responseNote = cleanOptionalText(body.responseNote ?: body.note)
          val responseNoteLocalized = localizedNoteForStorage(responseNote, body.responseNoteLocalized)
          var failureMessage: List<LocalizedStringDataModel>? = null
          var alreadyDeclined = false

          val request = newSuspendedTransaction(aitaServerIoContext) {
            val existingRequestRow = StoreWorkerRequests
              .selectAll()
              .where { StoreWorkerRequests.id eq requestId }
              .singleOrNull()

            if (existingRequestRow == null) {
              failureMessage = getResponse("13").message
              return@newSuspendedTransaction null
            }

            val requestDirection = existingRequestRow[StoreWorkerRequests.direction]
            if (requestDirection != WORKER_REQUEST_DIRECTION_USER_TO_STORE) {
              failureMessage = getResponse("13").message
              return@newSuspendedTransaction null
            }

            val requestStoreId = existingRequestRow[StoreWorkerRequests.storeId]
            val headerRootStoreId = rootStoreIdForAccessInsideTransaction(headerStoreId)
            val requestRootStoreId = rootStoreIdForAccessInsideTransaction(requestStoreId)
            if (headerRootStoreId != requestRootStoreId) {
              failureMessage = getResponse("13").message
              return@newSuspendedTransaction null
            }

            if (!isStoreOwnerInsideTransaction(userId, requestStoreId) && !userCanUseStoreActionInsideTransaction(userId, requestStoreId, STORE_PERMISSION_WORKERS_DECIDE_REQUESTS, requireWorkshift = false)) {
              failureMessage = getResponse("59").message
              return@newSuspendedTransaction null
            }

            when (existingRequestRow[StoreWorkerRequests.status]) {
              WORKER_REQUEST_STATUS_ACCEPTED -> {
                failureMessage = simpleMessage(
                  main = "This employment request has already been accepted",
                  ru = "Эта заявка на работу уже принята",
                  kk = "Бұл жұмысқа өтінім бұрын қабылданған"
                )
                return@newSuspendedTransaction null
              }
              WORKER_REQUEST_STATUS_DECLINED -> alreadyDeclined = true
            }

            if (!alreadyDeclined) {
              StoreWorkerRequests.update({ StoreWorkerRequests.id eq requestId }) {
                it[StoreWorkerRequests.status] = WORKER_REQUEST_STATUS_DECLINED
                it[StoreWorkerRequests.decidedAtMillis] = now
                it[StoreWorkerRequests.decidedByUserId] = userId
                it[StoreWorkerRequests.responseNote] = responseNote
                it[StoreWorkerRequests.responseNoteLocalized] = responseNoteLocalized
                it[StoreWorkerRequests.updatedAt] = Instant.now()
              }
            }

            val requestRow = StoreWorkerRequests
              .innerJoin(Stores, { StoreWorkerRequests.storeId }, { Stores.id })
              .innerJoin(Users, { StoreWorkerRequests.requesterUserId }, { Users.id })
              .selectAll()
              .where { StoreWorkerRequests.id eq requestId }
              .single()

            if (!alreadyDeclined) {
              notifyEmploymentDecisionInsideTransaction(
                requestId = requestId,
                storeId = requestStoreId,
                workerUserId = requestRow[StoreWorkerRequests.requesterUserId],
                actorUserId = userId,
                accepted = false,
                direction = WORKER_REQUEST_DIRECTION_USER_TO_STORE,
                nowMillis = now
              )
            }

            requestRow.toStoreWorkerRequestDataModel()
          }

          request?.let {
            publishWorkerRealtimeBundle(it.storeId, if (alreadyDeclined) "employment_request_already_declined" else "employment_request_declined")
            call.genericResponse(HttpStatusCode.OK, payload = it, message = getResponse("57").message)
          } ?: call.genericResponseNoPayload(HttpStatusCode.Conflict, failureMessage ?: getResponse("3").message)
        }

        post("/updatePermissions") {
          val userId = call.checkPrincipal() ?: return@post
          val storeId = call.headerUuid("store_id") ?: return@post call.respondAitaUnauthorized()
          val body = call.receiveAita<WorkerPermissionsUpdateRequestDataModel>()
          val workerId = runCatching { UUID.fromString(body.workerId) }.getOrNull()
            ?: return@post call.genericResponseNoPayload(HttpStatusCode.BadRequest, getResponse("13").message)
          val role = cleanWorkerRoleId(body.roleId)
          val requestedPermissions = cleanPermissionIds(body.permissions)
          val jobTitle = cleanWorkerJobTitle(body.jobTitle)
          val jobTitleLocalized = localizedNoteForStorage(jobTitle.takeIf { it.isNotBlank() }, body.jobTitleLocalized)
          val salary = cleanWorkerSalary(body.salary)
          val salaryCurrencyCode = cleanWorkerSalaryCurrencyCode(body.salaryCurrencyCode)
          val now = System.currentTimeMillis()
          var failureMessage: List<LocalizedStringDataModel>? = null

          val worker = newSuspendedTransaction(aitaServerIoContext) {
            val existingWorkerRow = StoreWorkerMemberships
              .selectAll()
              .where { (StoreWorkerMemberships.id eq workerId) and (StoreWorkerMemberships.isActive eq true) }
              .singleOrNull()

            if (existingWorkerRow == null) {
              failureMessage = getResponse("13").message
              return@newSuspendedTransaction null
            }

            val workerStoreId = existingWorkerRow[StoreWorkerMemberships.storeId]
            val headerRootStoreId = rootStoreIdForAccessInsideTransaction(storeId)
            val workerRootStoreId = rootStoreIdForAccessInsideTransaction(workerStoreId)
            if (headerRootStoreId != workerRootStoreId) {
              failureMessage = getResponse("13").message
              return@newSuspendedTransaction null
            }

            if (!actorCanManageExistingWorkerPermissionsInsideTransaction(userId, workerStoreId, existingWorkerRow[StoreWorkerMemberships.permissions])) {
              failureMessage = getResponse("59").message
              return@newSuspendedTransaction null
            }

            val permissions = assignableStorePermissionsInsideTransaction(userId, workerStoreId, requestedPermissions)
            if (permissions == null) {
              failureMessage = getResponse("59").message
              return@newSuspendedTransaction null
            }

            val updated = StoreWorkerMemberships.update({ (StoreWorkerMemberships.id eq workerId) and (StoreWorkerMemberships.isActive eq true) }) {
              it[StoreWorkerMemberships.roleId] = role
              it[StoreWorkerMemberships.permissions] = permissions
              it[StoreWorkerMemberships.jobTitle] = jobTitle
              it[StoreWorkerMemberships.jobTitleLocalized] = jobTitleLocalized
              it[StoreWorkerMemberships.salary] = salary
              it[StoreWorkerMemberships.salaryCurrencyCode] = salaryCurrencyCode
              it[StoreWorkerMemberships.updatedAt] = Instant.now()
            }

            if (updated <= 0) {
              failureMessage = getResponse("13").message
              return@newSuspendedTransaction null
            }

            val workerRow = StoreWorkerMemberships
              .innerJoin(Stores, { StoreWorkerMemberships.storeId }, { Stores.id })
              .innerJoin(Users, { StoreWorkerMemberships.userId }, { Users.id })
              .selectAll()
              .where { StoreWorkerMemberships.id eq workerId }
              .single()

            notifyWorkerPermissionsUpdatedInsideTransaction(
              storeId = workerStoreId,
              workerUserId = workerRow[StoreWorkerMemberships.userId],
              workerId = workerId,
              nowMillis = now
            )

            workerRow.toStoreWorkerDataModel()
          }

          worker?.let {
            publishWorkerRealtimeBundle(it.storeId, "worker_permissions_updated")
            call.genericResponse(HttpStatusCode.OK, payload = it, message = getResponse("58").message)
          } ?: call.genericResponseNoPayload(HttpStatusCode.Conflict, failureMessage ?: getResponse("3").message)
        }

        post("/remove") {
          val userId = call.checkPrincipal() ?: return@post
          val storeId = call.headerUuid("store_id") ?: return@post call.respondAitaUnauthorized()
          val body = call.receiveAita<WorkerRemovalRequestDataModel>()
          val workerId = runCatching { UUID.fromString(body.workerId) }.getOrNull()
            ?: return@post call.genericResponseNoPayload(HttpStatusCode.BadRequest, getResponse("13").message)
          val now = System.currentTimeMillis()
          val requestNote = cleanOptionalText(body.note)
          val requestNoteLocalized = localizedNoteForStorage(requestNote, body.noteLocalized)
          var failureMessage: List<LocalizedStringDataModel>? = null
          var alreadyPending = false

          val removalRequest = newSuspendedTransaction(aitaServerIoContext) {
            val existingWorkerRow = StoreWorkerMemberships
              .innerJoin(Stores, { StoreWorkerMemberships.storeId }, { Stores.id })
              .innerJoin(Users, { StoreWorkerMemberships.userId }, { Users.id })
              .selectAll()
              .where { (StoreWorkerMemberships.id eq workerId) and (StoreWorkerMemberships.isActive eq true) }
              .singleOrNull()

            if (existingWorkerRow == null) {
              failureMessage = getResponse("13").message
              return@newSuspendedTransaction null
            }

            val workerStoreId = existingWorkerRow[StoreWorkerMemberships.storeId]
            val workerUserId = existingWorkerRow[StoreWorkerMemberships.userId]
            val headerRootStoreId = rootStoreIdForAccessInsideTransaction(storeId)
            val workerRootStoreId = rootStoreIdForAccessInsideTransaction(workerStoreId)
            if (headerRootStoreId != workerRootStoreId) {
              failureMessage = getResponse("13").message
              return@newSuspendedTransaction null
            }

            if (workerUserId == userId) {
              failureMessage = simpleMessage(
                main = "You cannot request your own removal here",
                ru = "Нельзя запросить собственное удаление здесь",
                kk = "Бұл жерден өзіңізді алып тастауды сұрай алмайсыз"
              )
              return@newSuspendedTransaction null
            }

            if (!isStoreOwnerInsideTransaction(userId, workerStoreId) && !userCanUseStoreActionInsideTransaction(userId, workerStoreId, STORE_PERMISSION_WORKERS_REMOVE, requireWorkshift = false)) {
              failureMessage = getResponse("59").message
              return@newSuspendedTransaction null
            }

            StoreWorkerRequests
              .innerJoin(Stores, { StoreWorkerRequests.storeId }, { Stores.id })
              .innerJoin(Users, { StoreWorkerRequests.requesterUserId }, { Users.id })
              .selectAll()
              .where {
                (StoreWorkerRequests.storeId eq workerStoreId) and
                   (StoreWorkerRequests.requesterUserId eq workerUserId) and
                   (StoreWorkerRequests.direction eq WORKER_REQUEST_DIRECTION_STORE_REMOVAL_TO_USER) and
                   (StoreWorkerRequests.status eq WORKER_REQUEST_STATUS_PENDING)
              }
              .firstOrNull()
              ?.let { pendingRow ->
                alreadyPending = true
                return@newSuspendedTransaction pendingRow.toStoreWorkerRequestDataModel()
              }

            val requestId = UUID.randomUUID()
            StoreWorkerRequests.insert {
              it[StoreWorkerRequests.id] = requestId
              it[StoreWorkerRequests.storeId] = workerStoreId
              it[StoreWorkerRequests.requesterUserId] = workerUserId
              it[StoreWorkerRequests.direction] = WORKER_REQUEST_DIRECTION_STORE_REMOVAL_TO_USER
              it[StoreWorkerRequests.invitedByUserId] = userId
              it[StoreWorkerRequests.status] = WORKER_REQUEST_STATUS_PENDING
              it[StoreWorkerRequests.requestedAtMillis] = now
              it[StoreWorkerRequests.roleId] = existingWorkerRow[StoreWorkerMemberships.roleId]
              it[StoreWorkerRequests.permissions] = existingWorkerRow[StoreWorkerMemberships.permissions]
              it[StoreWorkerRequests.jobTitle] = existingWorkerRow[StoreWorkerMemberships.jobTitle]
              it[StoreWorkerRequests.jobTitleLocalized] = existingWorkerRow[StoreWorkerMemberships.jobTitleLocalized]
              it[StoreWorkerRequests.salary] = existingWorkerRow[StoreWorkerMemberships.salary]
              it[StoreWorkerRequests.salaryCurrencyCode] = existingWorkerRow[StoreWorkerMemberships.salaryCurrencyCode]
              it[StoreWorkerRequests.offerNote] = null
              it[StoreWorkerRequests.offerNoteLocalized] = emptyList()
              it[StoreWorkerRequests.workshiftPasswordHash] = null
              it[StoreWorkerRequests.note] = requestNote
              it[StoreWorkerRequests.noteLocalized] = requestNoteLocalized
              it[StoreWorkerRequests.responseNote] = null
              it[StoreWorkerRequests.responseNoteLocalized] = emptyList()
              it[StoreWorkerRequests.updatedAt] = Instant.now()
            }

            notifyWorkerRemovalRequestCreatedInsideTransaction(
              workerUserId = workerUserId,
              requesterUserId = userId,
              storeId = workerStoreId,
              requestId = requestId,
              workerId = workerId,
              nowMillis = now
            )

            StoreWorkerRequests
              .innerJoin(Stores, { StoreWorkerRequests.storeId }, { Stores.id })
              .innerJoin(Users, { StoreWorkerRequests.requesterUserId }, { Users.id })
              .selectAll()
              .where { StoreWorkerRequests.id eq requestId }
              .single()
              .toStoreWorkerRequestDataModel()
          }

          removalRequest?.let {
            if (!alreadyPending) publishWorkerRealtimeBundle(it.storeId, "worker_removal_requested")
            call.genericResponse(
              HttpStatusCode.OK,
              payload = it,
              message = if (alreadyPending) simpleMessage(
                main = "Removal request is already waiting",
                ru = "Запрос на удаление уже ожидает ответа",
                kk = "Алып тастау сұрауы жауап күтуде"
              ) else simpleMessage(
                main = "Removal request sent",
                ru = "Запрос на удаление отправлен",
                kk = "Алып тастау сұрауы жіберілді"
              )
            )
          } ?: call.genericResponseNoPayload(HttpStatusCode.Conflict, failureMessage ?: getResponse("3").message)
        }

        post("/removal/confirm") {
          val userId = call.checkPrincipal() ?: return@post
          val body = call.receiveAita<WorkerRemovalDecisionRequestDataModel>()
          val requestId = runCatching { UUID.fromString(body.requestId) }.getOrNull()
            ?: return@post call.genericResponseNoPayload(HttpStatusCode.BadRequest, getResponse("13").message)
          val now = System.currentTimeMillis()
          val responseNote = cleanOptionalText(body.responseNote ?: body.note)
          val responseNoteLocalized = localizedNoteForStorage(responseNote, body.responseNoteLocalized)
          var failureMessage: List<LocalizedStringDataModel>? = null

          val removedWorker = newSuspendedTransaction(aitaServerIoContext) {
            val requestRow = StoreWorkerRequests
              .selectAll()
              .where {
                (StoreWorkerRequests.id eq requestId) and
                   (StoreWorkerRequests.requesterUserId eq userId) and
                   (StoreWorkerRequests.direction eq WORKER_REQUEST_DIRECTION_STORE_REMOVAL_TO_USER) and
                   (StoreWorkerRequests.status eq WORKER_REQUEST_STATUS_PENDING)
              }
              .singleOrNull()

            if (requestRow == null) {
              failureMessage = getResponse("13").message
              return@newSuspendedTransaction null
            }

            val requestStoreId = requestRow[StoreWorkerRequests.storeId]
            val workerRow = StoreWorkerMemberships
              .innerJoin(Stores, { StoreWorkerMemberships.storeId }, { Stores.id })
              .innerJoin(Users, { StoreWorkerMemberships.userId }, { Users.id })
              .selectAll()
              .where {
                (StoreWorkerMemberships.storeId eq requestStoreId) and
                   (StoreWorkerMemberships.userId eq userId) and
                   (StoreWorkerMemberships.isActive eq true)
              }
              .singleOrNull()

            if (workerRow == null) {
              failureMessage = getResponse("13").message
              return@newSuspendedTransaction null
            }

            val workerId = workerRow[StoreWorkerMemberships.id]

            StoreWorkerMemberships.update({ (StoreWorkerMemberships.id eq workerId) and (StoreWorkerMemberships.isActive eq true) }) {
              it[StoreWorkerMemberships.isActive] = false
              it[StoreWorkerMemberships.updatedAt] = Instant.now()
            }

            Workshifts.update({
              (Workshifts.workerMembershipId eq workerId) and
                 Workshifts.endedAtMillis.isNull() and
                 (Workshifts.isActive eq true)
            }) {
              it[Workshifts.endedAtMillis] = now
              it[Workshifts.endedByUserId] = userId
              it[Workshifts.isActive] = false
              it[Workshifts.updatedAt] = Instant.now()
            }

            StoreUsers.deleteWhere {
              (StoreUsers.storeId eq requestStoreId) and (StoreUsers.userId eq userId)
            }

            StoreWorkerRequests.update({ StoreWorkerRequests.id eq requestId }) {
              it[StoreWorkerRequests.status] = WORKER_REQUEST_STATUS_ACCEPTED
              it[StoreWorkerRequests.decidedAtMillis] = now
              it[StoreWorkerRequests.decidedByUserId] = userId
              it[StoreWorkerRequests.responseNote] = responseNote
              it[StoreWorkerRequests.responseNoteLocalized] = responseNoteLocalized
              it[StoreWorkerRequests.updatedAt] = Instant.now()
            }

            notifyWorkerRemovalDecisionInsideTransaction(
              requestId = requestId,
              storeId = requestStoreId,
              workerUserId = userId,
              workerId = workerId,
              actorUserId = userId,
              accepted = true,
              nowMillis = now
            )

            StoreWorkerMemberships
              .innerJoin(Stores, { StoreWorkerMemberships.storeId }, { Stores.id })
              .innerJoin(Users, { StoreWorkerMemberships.userId }, { Users.id })
              .selectAll()
              .where { StoreWorkerMemberships.id eq workerId }
              .single()
              .toStoreWorkerDataModel()
          }

          removedWorker?.let {
            publishWorkerRealtimeBundle(it.storeId, "worker_removal_confirmed")
            call.genericResponse(
              HttpStatusCode.OK,
              payload = it,
              message = simpleMessage(
                main = "Removal confirmed",
                ru = "Удаление подтверждено",
                kk = "Алып тастау расталды"
              )
            )
          } ?: call.genericResponseNoPayload(HttpStatusCode.Conflict, failureMessage ?: getResponse("3").message)
        }

        post("/removal/decline") {
          val userId = call.checkPrincipal() ?: return@post
          val body = call.receiveAita<WorkerRemovalDecisionRequestDataModel>()
          val requestId = runCatching { UUID.fromString(body.requestId) }.getOrNull()
            ?: return@post call.genericResponseNoPayload(HttpStatusCode.BadRequest, getResponse("13").message)
          val now = System.currentTimeMillis()
          val responseNote = cleanOptionalText(body.responseNote ?: body.note)
          val responseNoteLocalized = localizedNoteForStorage(responseNote, body.responseNoteLocalized)
          var failureMessage: List<LocalizedStringDataModel>? = null

          val request = newSuspendedTransaction(aitaServerIoContext) {
            val updated = StoreWorkerRequests.update({
              (StoreWorkerRequests.id eq requestId) and
                 (StoreWorkerRequests.requesterUserId eq userId) and
                 (StoreWorkerRequests.direction eq WORKER_REQUEST_DIRECTION_STORE_REMOVAL_TO_USER) and
                 (StoreWorkerRequests.status eq WORKER_REQUEST_STATUS_PENDING)
            }) {
              it[StoreWorkerRequests.status] = WORKER_REQUEST_STATUS_DECLINED
              it[StoreWorkerRequests.decidedAtMillis] = now
              it[StoreWorkerRequests.decidedByUserId] = userId
              it[StoreWorkerRequests.responseNote] = responseNote
              it[StoreWorkerRequests.responseNoteLocalized] = responseNoteLocalized
              it[StoreWorkerRequests.updatedAt] = Instant.now()
            }

            if (updated <= 0) {
              failureMessage = getResponse("13").message
              return@newSuspendedTransaction null
            }

            val requestRow = StoreWorkerRequests
              .innerJoin(Stores, { StoreWorkerRequests.storeId }, { Stores.id })
              .innerJoin(Users, { StoreWorkerRequests.requesterUserId }, { Users.id })
              .selectAll()
              .where { StoreWorkerRequests.id eq requestId }
              .single()

            val workerRow = StoreWorkerMemberships
              .select(StoreWorkerMemberships.id)
              .where {
                (StoreWorkerMemberships.storeId eq requestRow[StoreWorkerRequests.storeId]) and
                   (StoreWorkerMemberships.userId eq userId)
              }
              .singleOrNull()

            notifyWorkerRemovalDecisionInsideTransaction(
              requestId = requestId,
              storeId = requestRow[StoreWorkerRequests.storeId],
              workerUserId = userId,
              workerId = workerRow?.get(StoreWorkerMemberships.id) ?: UUID(0, 0),
              actorUserId = userId,
              accepted = false,
              nowMillis = now
            )

            requestRow.toStoreWorkerRequestDataModel()
          }

          request?.let {
            publishWorkerRealtimeBundle(it.storeId, "worker_removal_declined")
            call.genericResponse(
              HttpStatusCode.OK,
              payload = it,
              message = simpleMessage(
                main = "Removal request declined",
                ru = "Запрос на удаление отклонён",
                kk = "Алып тастау сұрауы қабылданбады"
              )
            )
          } ?: call.genericResponseNoPayload(HttpStatusCode.Conflict, failureMessage ?: getResponse("3").message)
        }

        post("/my/password") {
          val userId = call.checkPrincipal() ?: return@post
          val body = call.receiveAita<WorkerSelfPasswordUpdateRequestDataModel>()
          val workerId = runCatching { UUID.fromString(body.workerId) }.getOrNull()
            ?: return@post call.genericResponseNoPayload(HttpStatusCode.BadRequest, getResponse("13").message)
          val cleanPassword = body.workerPassword.trim()
          val cleanAccountPassword = body.accountPassword.trim()
          if (!cleanPassword.checkAsPassword()) {
            return@post call.genericResponseNoPayload(
              HttpStatusCode.BadRequest,
              passwordRequirementMessage()
            )
          }
          if (cleanAccountPassword.isBlank()) {
            return@post call.genericResponseNoPayload(
              HttpStatusCode.BadRequest,
              accountPasswordRequiredMessage()
            )
          }

          var failureMessage: List<LocalizedStringDataModel>? = null
          val worker = newSuspendedTransaction(aitaServerIoContext) {
            val membershipRow = StoreWorkerMemberships
              .innerJoin(Stores, { StoreWorkerMemberships.storeId }, { Stores.id })
              .innerJoin(Users, { StoreWorkerMemberships.userId }, { Users.id })
              .selectAll()
              .where {
                (StoreWorkerMemberships.id eq workerId) and
                   (StoreWorkerMemberships.userId eq userId) and
                   (StoreWorkerMemberships.isActive eq true)
              }
              .singleOrNull()

            if (membershipRow == null) {
              failureMessage = getResponse("13").message
              return@newSuspendedTransaction null
            }

            if (!Pw.verify(cleanAccountPassword.toCharArray(), membershipRow[Users.passwordHash])) {
              failureMessage = accountPasswordIncorrectMessage()
              return@newSuspendedTransaction null
            }

            StoreWorkerMemberships.update({ (StoreWorkerMemberships.id eq workerId) and (StoreWorkerMemberships.userId eq userId) }) {
              it[StoreWorkerMemberships.workshiftPasswordHash] = cleanPassword.toWorkshiftPasswordHashOrNull()
              it[StoreWorkerMemberships.updatedAt] = Instant.now()
            }

            StoreWorkerMemberships
              .innerJoin(Stores, { StoreWorkerMemberships.storeId }, { Stores.id })
              .innerJoin(Users, { StoreWorkerMemberships.userId }, { Users.id })
              .selectAll()
              .where { StoreWorkerMemberships.id eq workerId }
              .single()
              .toStoreWorkerDataModel()
          }

          worker?.let {
            publishWorkerRealtimeBundle(it.storeId, "worker_self_password_updated")
            call.genericResponse(HttpStatusCode.OK, payload = it, message = getResponse("103").message)
          } ?: call.genericResponseNoPayload(HttpStatusCode.Conflict, failureMessage ?: getResponse("3").message)
        }
      }
    }


    route("/workshifts") {
      authenticate("auth-jwt") {
        get("/current") {
          val userId = call.checkPrincipal() ?: return@get
          val storeId = call.headerUuid("store_id") ?: return@get call.respondAitaUnauthorized()

          val workshift = newSuspendedTransaction(aitaServerIoContext) {
            Workshifts
              .innerJoin(Stores, { Workshifts.storeId }, { Stores.id })
              .innerJoin(Users, { Workshifts.workerUserId }, { Users.id })
              .selectAll()
              .where {
                (Workshifts.storeId eq storeId) and
                   (Workshifts.workerUserId eq userId) and
                   (Workshifts.isActive eq true) and
                   Workshifts.endedAtMillis.isNull()
              }
              .orderBy(Workshifts.startedAtMillis, SortOrder.DESC)
              .limit(1)
              .singleOrNull()
              ?.toWorkshiftDataModel()
          }

          workshift?.let { call.genericResponse(HttpStatusCode.OK, it, getResponse("88").message) }
            ?: call.genericResponseNoPayload(HttpStatusCode.NotFound, getResponse("13").message)
        }

        post("/start") {
          val userId = call.checkPrincipal() ?: return@post
          val storeId = call.headerUuid("store_id") ?: return@post call.respondAitaUnauthorized()
          val body = call.receiveAita<WorkshiftStartRequestDataModel>()
          val now = System.currentTimeMillis()
          var failureMessage: List<LocalizedStringDataModel>? = null

          val workshift = newSuspendedTransaction(aitaServerIoContext) {
            val rootStoreId = rootStoreIdForAccessInsideTransaction(storeId)
            val identifier = body.workerIdentifier.trim()
            val identifierUuid = runCatching { UUID.fromString(identifier) }.getOrNull()

            val membershipRow = StoreWorkerMemberships
              .innerJoin(Users, { StoreWorkerMemberships.userId }, { Users.id })
              .innerJoin(Stores, { StoreWorkerMemberships.storeId }, { Stores.id })
              .selectAll()
              .where {
                (StoreWorkerMemberships.isActive eq true) and
                   (StoreWorkerMemberships.userId eq userId) and
                   ((StoreWorkerMemberships.storeId eq storeId) or (StoreWorkerMemberships.storeId eq rootStoreId))
              }
              .firstOrNull { row ->
                row[StoreWorkerMemberships.id] == identifierUuid ||
                   row[Users.id] == identifierUuid ||
                   row[Users.publicId].equals(identifier, ignoreCase = true) ||
                   row[Users.phoneNumber].equals(identifier, ignoreCase = true) ||
                   row[Users.email].equals(identifier, ignoreCase = true)
              }

            if (membershipRow == null) {
              failureMessage = getResponse("90").message
              return@newSuspendedTransaction null
            }

            val workerUserId = membershipRow[StoreWorkerMemberships.userId]
            if (workerUserId != userId) {
              failureMessage = getResponse("91").message
              return@newSuspendedTransaction null
            }

            val passwordHash = membershipRow[StoreWorkerMemberships.workshiftPasswordHash]
            if (passwordHash.isNullOrBlank()) {
              failureMessage = getResponse("89").message
              return@newSuspendedTransaction null
            }

            val cleanPassword = body.password.trim()
            if (!Pw.verify(cleanPassword.toCharArray(), passwordHash)) {
              failureMessage = getResponse("90").message
              return@newSuspendedTransaction null
            }

            Workshifts.update({
              (Workshifts.storeId eq storeId) and
                 (Workshifts.workerUserId eq workerUserId) and
                 (Workshifts.isActive eq true) and
                 Workshifts.endedAtMillis.isNull()
            }) {
              it[Workshifts.endedAtMillis] = now
              it[Workshifts.endedByUserId] = userId
              it[Workshifts.isActive] = false
              it[Workshifts.updatedAt] = Instant.now()
            }

            val workshiftId = UUID.randomUUID()
            Workshifts.insert {
              it[Workshifts.id] = workshiftId
              it[Workshifts.storeId] = storeId
              it[Workshifts.workerMembershipId] = membershipRow[StoreWorkerMemberships.id]
              it[Workshifts.workerUserId] = workerUserId
              it[Workshifts.startedAtMillis] = now
              it[Workshifts.endedAtMillis] = null
              it[Workshifts.startedByUserId] = userId
              it[Workshifts.endedByUserId] = null
              it[Workshifts.metadata] = mapOf("root_store_id" to rootStoreId.toString())
              it[Workshifts.isActive] = true
            }

            insertOperationLogInsideTransaction(
              actorUserId = userId,
              storeId = storeId,
              action = OPERATION_LOG_ACTION_STARTED,
              entityType = OPERATION_LOG_ENTITY_WORKSHIFT,
              entityId = workshiftId.toString(),
              title = simpleMessage("Workshift started", ru = "Смена начата", kk = "Ауысым басталды"),
              details = simpleMessage(membershipRow[Users.publicId]),
              metadata = mapOf("worker_user_id" to workerUserId.toString(), "membership_id" to membershipRow[StoreWorkerMemberships.id].toString()),
              now = now
            )

            Workshifts
              .innerJoin(Stores, { Workshifts.storeId }, { Stores.id })
              .innerJoin(Users, { Workshifts.workerUserId }, { Users.id })
              .selectAll()
              .where { Workshifts.id eq workshiftId }
              .single()
              .toWorkshiftDataModel()
          }

          workshift?.let {
            publishWorkerRealtimeBundle(it.storeId, "workshift_started")
            call.genericResponse(HttpStatusCode.Created, it, getResponse("87").message)
          } ?: call.genericResponseNoPayload(HttpStatusCode.Conflict, failureMessage ?: getResponse("3").message)
        }

        post("/end") {
          val userId = call.checkPrincipal() ?: return@post
          val storeId = call.headerUuid("store_id") ?: return@post call.respondAitaUnauthorized()
          val rawBody = runCatching { call.receiveTextAita().trim() }.getOrNull().orEmpty()
          val body = rawBody
            .takeIf { it.isNotBlank() }
            ?.let { text -> runCatching { jsonBase.decodeFromString<WorkshiftEndRequestDataModel>(text) }.getOrNull() }
          val now = System.currentTimeMillis()

          val workshift = newSuspendedTransaction(aitaServerIoContext) {
            endWorkshiftForUserInsideTransaction(
              workerUserId = userId,
              storeId = storeId,
              endedByUserId = userId,
              request = body,
              now = body?.endedAtMillis?.takeIf { it > 0L } ?: now
            )
          }

          workshift?.let {
            publishWorkerRealtimeBundle(it.storeId, "workshift_ended")
            call.genericResponse(HttpStatusCode.OK, it, getResponse("86").message)
          } ?: call.genericResponseNoPayload(HttpStatusCode.NotFound, getResponse("13").message)
        }

      }
    }

    route("/logs") {
      authenticate("auth-jwt") {
        get("/get") {
          val userId = call.checkPrincipal() ?: return@get
          val storeId = call.headerUuid("store_id")
            ?: return@get call.respondAitaUnauthorized()
          val scope = call.request.queryParameters["scope"].orEmpty().ifBlank { OPERATION_LOG_SCOPE_CURRENT }

          val logs = newSuspendedTransaction(aitaServerIoContext) {
            if (!userCanUseStoreActionInsideTransaction(userId, storeId, STORE_PERMISSION_LOGS_VIEW, requireWorkshift = false))
              return@newSuspendedTransaction null

            val storeIds = operationLogStoreIdsForScopeInsideTransaction(storeId, scope)

            OperationLogs
              .selectAll()
              .where { OperationLogs.storeId inList storeIds }
              .orderBy(OperationLogs.createdAtMillis, SortOrder.DESC)
              .limit(500)
              .map { it.toOperationLogDataModel() }
          }

          logs?.let {
            call.genericListResponse(
              status = HttpStatusCode.OK,
              payload = it,
              message = getResponse("92").message
            )
          } ?: call.respondAitaUnauthorized()
        }
      }
    }

    route("/analytics") {
      authenticate("auth-jwt") {
        get("/store/get") {
          val userId = call.checkPrincipal() ?: return@get
          val storeId = runCatching { UUID.fromString(call.request.header("store_id")) }.getOrNull()
            ?: return@get call.respondAitaUnauthorized()
          val startMillis = call.request.queryParameters["startMillis"]?.toLongOrNull() ?: 0L
          val endMillisExclusive = call.request.queryParameters["endMillisExclusive"]?.toLongOrNull()
            ?: Long.MAX_VALUE
          val goodsItemIdFilter = call.request.queryParameters["goodsItemId"]?.trim()?.takeIf { it.isNotBlank() }
          val supplierIdFilter = call.request.queryParameters["supplierId"]?.trim()?.takeIf { it.isNotBlank() }
          val categoryIdFilter = call.request.queryParameters["categoryId"]?.trim()?.takeIf { it.isNotBlank() }

          val dashboard = newSuspendedTransaction(aitaServerIoContext) {
            if (!userCanUseStoreActionInsideTransaction(userId, storeId, STORE_PERMISSION_ANALYTICS_VIEW, requireWorkshift = false))
              return@newSuspendedTransaction null

            val transactions = Transactions
              .selectAll()
              .where {
                (Transactions.storeId eq storeId) and
                   (Transactions.timeMillis greaterEq startMillis) and
                   (Transactions.timeMillis less endMillisExclusive)
              }
              .orderBy(Transactions.timeMillis, SortOrder.DESC)
              .map {
                it.toTransactionDataModel()
              }

            val stock = StockItems
              .selectAll()
              .where { StockItems.storeId eq storeId }
              .map { it.toGoodsItemDataModel() }

            val batches = StockBatchesV2
              .selectAll()
              .where { StockBatchesV2.storeId eq storeId }
              .map { it.toGoodsBatchDataModel() }

            buildStoreAnalyticsDashboard(
              storeId = storeId.toString(),
              startMillis = startMillis,
              endMillisExclusive = endMillisExclusive,
              transactions = transactions,
              stock = stock,
              batches = batches,
              fallbackCurrencyCode = stock.asSequence()
                .flatMap { item -> (item.salePrices + item.supplyPrices + item.returnPrices + item.wholesalePrices).asSequence() }
                .map { it.currency }
                .firstOrNull { it.isNotBlank() }
                ?: batches.firstOrNull()?.supplyPrice?.currency.orEmpty(),
              goodsItemIdFilter = goodsItemIdFilter,
              supplierIdFilter = supplierIdFilter,
              categoryIdFilter = categoryIdFilter
            )
          }

          dashboard?.let {
            call.genericResponse(
              status = HttpStatusCode.OK,
              payload = it,
              message = getResponse("94").message
            )
          } ?: call.respondAitaUnauthorized()
        }
      }
    }

    route("/transactions") {
      authenticate("auth-jwt") {
        get("/get") {
          val userId = call.checkPrincipal() ?: return@get
          val storeId = runCatching {
            UUID.fromString(call.request.header("store_id"))
          }.getOrNull() ?: return@get call.respondAitaUnauthorized()

          val transactions = newSuspendedTransaction(aitaServerIoContext) {
            if (!userCanUseStoreActionInsideTransaction(userId, storeId, STORE_PERMISSION_TRANSACTION_HISTORY_VIEW, requireWorkshift = false))
              return@newSuspendedTransaction null

            val visibleStoreIds = stockVisibleStoreIdsInsideTransaction(storeId)

            Transactions
              .selectAll()
              .where { Transactions.storeId inList visibleStoreIds }
              .orderBy(Transactions.timeMillis, SortOrder.DESC)
              .map {
                it.toTransactionDataModel()
              }
          }

          transactions?.let {
            call.genericListResponse(
              status = HttpStatusCode.OK,
              payload = it
            )
          } ?: call.respondAitaUnauthorized()
        }

        post("/complete") {
          try {
            val userId = call.checkPrincipal() ?: return@post
            val body = call.receiveAita<TransactionDataModel>()
            val requestClientOperationId = body.clientOperationId.trim().takeIf { it.isNotBlank() }

            val storeId = runCatching {
              UUID.fromString(body.storeId)
            }.getOrNull() ?: return@post call.respondAitaUnauthorized()

            var transactionFailureMessage: List<LocalizedStringDataModel>? = null

            val completed = newSuspendedTransaction(aitaServerIoContext) {
              if (!call.matchesInventoryContextStoreIdInsideTransaction(userId, storeId))
                return@newSuspendedTransaction null

              if (!userHasStoreAccessInsideTransaction(userId, storeId))
                return@newSuspendedTransaction null

              requestClientOperationId?.let { operationId ->
                Transactions
                  .selectAll()
                  .where { (Transactions.clientOperationId eq operationId) and (Transactions.storeId eq storeId) }
                  .singleOrNull()
                  ?.let { row ->
                    val existingTransaction = row.toTransactionDataModel()
                    syncTransactionReturnItemsInsideTransaction(
                      userId = row[Transactions.userId],
                      storeId = storeId,
                      transactionId = row[Transactions.id],
                      transaction = existingTransaction,
                      clientOperationId = operationId,
                      timeMillis = existingTransaction.timeMillis
                    )
                    return@newSuspendedTransaction existingTransaction
                  }
              }

              val requiredPermission = requiredPermissionForTransactionType(body.type)
              if (requiredPermission != null && !userCanUseStoreActionInsideTransaction(userId, storeId, requiredPermission, requireWorkshift = true)) {
                transactionFailureMessage = getResponse("59").message
                return@newSuspendedTransaction null
              }

              if (body.goodsInTransaction.isEmpty()) {
                transactionFailureMessage = simpleMessage(
                  main = "Transaction has no items",
                  ru = "В транзакции нет товаров",
                  kk = "Транзакцияда тауарлар жоқ"
                )
                return@newSuspendedTransaction null
              }

              val normalizedGoodsResult = normalizeTransactionGoodsInsideTransaction(
                storeId = storeId,
                transactionType = body.type,
                lines = body.goodsInTransaction
              )

              val normalizedGoodsInTransaction = normalizedGoodsResult.lines

              if (normalizedGoodsInTransaction == null) {
                transactionFailureMessage = when (normalizedGoodsResult.errorCode) {
                  "wholesale_minimum" -> simpleMessage(
                    main = "Wholesale minimum quantity was not reached",
                    ru = "Минимальное количество для опта не набрано",
                    kk = "Көтерме үшін ең аз санға жеткен жоқ"
                  )
                  "price_unavailable" -> simpleMessage(
                    main = "Transaction price is not available",
                    ru = "Цена для транзакции недоступна",
                    kk = "Транзакция бағасы қолжетімсіз"
                  )
                  "promotion_restriction" -> simpleMessage(
                    main = "Promotion restriction is not satisfied",
                    ru = "Условие промо-периода не выполнено",
                    kk = "Промо-кезең шарты орындалмады"
                  )
                  else -> simpleMessage(
                    main = "Not enough stock or item barcode was not found",
                    ru = "Недостаточно товара на складе или штрихкод не найден",
                    kk = "Қоймада тауар жеткіліксіз немесе штрихкод табылмады"
                  )
                }
                return@newSuspendedTransaction null
              }

              val transactionToSave = body.copy(goodsInTransaction = normalizedGoodsInTransaction)

              val transactionTotal = transactionToSave.goodsInTransaction
                .sumOf { it.quantity * it.pricePerUnit }
                .let { kotlin.math.floor(it * 100.0) / 100.0 }

              val uploadedPaymentTotal = (transactionToSave.paidCash + transactionToSave.paidCard + (transactionToSave.debtor?.debtAmount ?: 0.0))
                .let { kotlin.math.floor(it * 100.0) / 100.0 }

              if (transactionTotal > 0.0 && uploadedPaymentTotal + 0.01 < transactionTotal) {
                transactionFailureMessage = simpleMessage(
                  main = "Payment amount is not enough",
                  ru = "Суммы оплаты недостаточно",
                  kk = "Төлем сомасы жеткіліксіз"
                )
                return@newSuspendedTransaction null
              }

              val id = UUID.randomUUID()
              val timeMillis = body.timeMillis.takeIf { it > 0 } ?: System.currentTimeMillis()

              val stockMutationOk = applyTransactionStockMutationInsideTransaction(
                userId = userId,
                storeId = storeId,
                transaction = transactionToSave,
                now = timeMillis
              )

              if (!stockMutationOk) {
                transactionFailureMessage = simpleMessage(
                  main = "Not enough stock or item barcode was not found",
                  ru = "Недостаточно товара на складе или штрихкод не найден",
                  kk = "Қоймада тауар жеткіліксіз немесе штрихкод табылмады"
                )
                return@newSuspendedTransaction null
              }

              val savedDebtor = transactionToSave.debtor
                ?.takeIf { it.debtAmount > 0.0 }
                ?.let { debtor ->
                  upsertDebtorInsideTransaction(
                    userId = userId,
                    storeId = storeId,
                    debtor = debtor,
                    transactionId = id.toString()
                  )
                }

              Transactions.insert {
                it[Transactions.id] = id
                it[Transactions.userId] = userId
                it[Transactions.workshiftId] = body.workshiftId
                it[Transactions.type] = body.type
                it[Transactions.storeId] = storeId
                it[Transactions.goodsInTransaction] = transactionToSave.goodsInTransaction
                it[Transactions.paidCash] = transactionToSave.paidCash
                it[Transactions.paidCard] = transactionToSave.paidCard
                it[Transactions.cardPaymentOptionId] = transactionToSave.cardPaymentOptionId
                it[Transactions.debtor] = savedDebtor?.let { debtor ->
                  jsonBase.encodeToString(debtor)
                }
                it[Transactions.timeMillis] = timeMillis
                it[Transactions.clientOperationId] = requestClientOperationId
              }

              syncTransactionReturnItemsInsideTransaction(
                userId = userId,
                storeId = storeId,
                transactionId = id,
                transaction = transactionToSave,
                clientOperationId = requestClientOperationId,
                timeMillis = timeMillis
              )

              applyCashRegisterTransactionEventInsideTransaction(
                storeId = storeId,
                userId = userId,
                transactionId = id,
                transactionType = transactionToSave.type,
                cashAmount = transactionToSave.paidCash,
                now = timeMillis
              )

              val transactionTypeText = operationLogTransactionTypeHumanText(transactionToSave.type)

              insertOperationLogInsideTransaction(
                actorUserId = userId,
                storeId = storeId,
                action = OPERATION_LOG_ACTION_COMPLETED,
                entityType = OPERATION_LOG_ENTITY_TRANSACTION,
                entityId = id.toString(),
                title = simpleMessage(
                  main = "${transactionTypeText.main} completed",
                  en = "${transactionTypeText.en} completed",
                  ru = "${transactionTypeText.ru} завершена",
                  kk = "${transactionTypeText.kk} аяқталды"
                ),
                details = simpleMessage(
                  main = "${transactionTypeText.main} • $transactionTotal",
                  en = "${transactionTypeText.en} • $transactionTotal",
                  ru = "${transactionTypeText.ru} • $transactionTotal",
                  kk = "${transactionTypeText.kk} • $transactionTotal"
                ),
                metadata = mapOf("type" to transactionToSave.type, "total" to transactionTotal.toString(), "cash" to transactionToSave.paidCash.toString(), "card" to transactionToSave.paidCard.toString()),
                now = timeMillis
              )

              transactionToSave.copy(
                id = id.toString(),
                storeId = storeId.toString(),
                debtor = savedDebtor,
                timeMillis = timeMillis
              )
            }

            completed?.let {
              publishStockRealtimeBundle(it.storeId, "transaction_completed")
              RealtimeServerBus.publish(entity = "transactions", storeId = it.storeId, reason = "transaction_completed")
              call.genericResponse(
                status = HttpStatusCode.Created,
                payload = it,
                message = simpleMessage(
                  main = "Transaction completed",
                  ru = "Транзакция завершена",
                  kk = "Транзакция аяқталды"
                )
              )
            } ?: run {
              transactionFailureMessage?.let { message ->
                call.genericResponseNoPayload(
                  status = HttpStatusCode.Conflict,
                  message = message
                )
              } ?: call.respondAitaUnauthorized()
            }

          } catch (throwable: Throwable) {
            call.safeGenericResponseNoPayload(
              status = HttpStatusCode.Conflict,
              message = simpleMessage(
                main = "Could not complete transaction. Please refresh stock and try again.",
                ru = "Не удалось завершить транзакцию. Обновите склад и попробуйте снова.",
                kk = "Транзакцияны аяқтау мүмкін болмады. Қойманы жаңартып, қайталап көріңіз."
              ),
              logMessage = "Transaction completion failed",
              throwable = throwable
            )
          }
        }
      }
    }
  }
}
