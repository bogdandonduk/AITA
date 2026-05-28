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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import io.ktor.websocket.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kz.aita.*
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.json.contains
import org.jetbrains.exposed.sql.json.jsonb
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.postgresql.util.PSQLException
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
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
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(id)
}

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


const val serverFilesPath = "AITA/server"
const val configAppPath = "$serverFilesPath/config/app"

fun metaFrom(call: ApplicationCall, deviceInfo: ClientDeviceInfoDataModel? = null): Map<String, String> {
    val base = linkedMapOf(
        "ip" to (call.request.header("X-Forwarded-For") ?: call.request.origin.remoteHost),
        "ua" to (call.request.userAgent() ?: "unknown")
    )

    deviceInfo?.let { info ->
        if (info.installationId.isNotBlank()) base["installationId"] = info.installationId
        if (info.deviceName.isNotBlank()) base["deviceName"] = info.deviceName
        if (info.platformName.isNotBlank()) base["platformName"] = info.platformName
        if (info.osName.isNotBlank()) base["osName"] = info.osName
        if (info.appName.isNotBlank()) base["appName"] = info.appName
        if (info.appVersion.isNotBlank()) base["appVersion"] = info.appVersion
        if (info.localeLanguage.isNotBlank()) base["localeLanguage"] = info.localeLanguage
    }

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

suspend fun RoutingCall.genericResponseNoPayload(
    status: HttpStatusCode,
    message: List<LocalizedStringDataModel>? = null
) {
    val response = GenericResponseDataModel(
        message = message?.let { jsonBase.encodeToString(it) },
        payload = null,
        negative = !status.isSuccess()
    )

    respondText(
        text = jsonBase.encodeToString(response),
        contentType = ContentType.Application.Json,
        status = status
    )
}

suspend inline fun <reified T> RoutingCall.genericResponse(
    status: HttpStatusCode,
    payload: T?,
    message: List<LocalizedStringDataModel>? = null
) {
    val response = GenericResponseDataModel(
        message = message?.let { jsonBase.encodeToString(it) },
        payload = payload?.let { jsonBase.encodeToString(it) },
        negative = !status.isSuccess()
    )

    respondText(
        text = jsonBase.encodeToString(response),
        contentType = ContentType.Application.Json,
        status = status
    )
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
    throwable?.printStackTrace()

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
        expiresAtMillis = expires.toEpochMilli(),
        revokedAtMillis = revoked?.toEpochMilli(),
        current = sessionId == currentSessionId,
        active = revoked == null && expires.isAfter(Instant.now())
    )
}

suspend fun loadSecuritySessionsForUser(userId: UUID, currentSessionId: UUID?): List<SecuritySessionDataModel> =
    newSuspendedTransaction(Dispatchers.IO) {
        RefreshSessions
            .selectAll()
            .where { RefreshSessions.userId eq userId }
            .orderBy(RefreshSessions.createdAt, SortOrder.DESC)
            .limit(50)
            .map { it.toSecuritySessionDataModel(currentSessionId) }
            .filter { it.active }
    }

fun getResponses(): List<RemoteResponseDataModel> {
    return Json.decodeFromString(Files.readString(Path.of(configAppPath).resolve("responses.json")))
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
    val workshiftPasswordHash = text("workshift_password_hash").nullable()
    val note = text("note").nullable()
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
    val workshiftPasswordHash = text("workshift_password_hash").nullable()
    val requestedAtMillis = long("requested_at_millis").default(0L)
    val acceptedAtMillis = long("accepted_at_millis")
    val acceptedByUserId = uuid("accepted_by_user_id")
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

    val shelfPosition = text("shelf_position").nullable()
    val shelfPriority = integer("shelf_priority")

    val status = text("status")
    val additionalNotes = text("additional_notes").nullable()

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

    val deliveredBatchIds = jsonb("delivered_batch_ids", Json, ListSerializer(String.serializer()))
    val isActive = bool("is_active")

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
    val alias = jsonb("alias", Json, ListSerializer(LocalizedStringDataModel.serializer())).nullable()
    val description = jsonb("description", Json, ListSerializer(LocalizedStringDataModel.serializer())).nullable()
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

    private val hmacKey: SecretKeySpec by lazy {
        val pepper = System.getenv("AITA_REFRESH_PEPPER")
            ?.takeIf { it.isNotBlank() }
            ?: System.getProperty("AITA_REFRESH_PEPPER")
                ?.takeIf { it.isNotBlank() }
            ?: "aita-local-dev-refresh-pepper-change-this-before-production-2026"

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
            val installationId = metaParam?.get("installationId")?.takeIf { it.isNotBlank() }
            val deviceName = metaParam?.get("deviceName")?.takeIf { it.isNotBlank() }
            val platformName = metaParam?.get("platformName")?.takeIf { it.isNotBlank() }
            val osName = metaParam?.get("osName")?.takeIf { it.isNotBlank() }

            val oldSameDeviceSessionIds = RefreshSessions
                .selectAll()
                .where {
                    (RefreshSessions.userId eq userId) and
                            RefreshSessions.revokedAt.isNull()
                }
                .mapNotNull { row ->
                    val meta = row[RefreshSessions.meta].orEmpty()
                    val sameInstallation = installationId != null && meta["installationId"] == installationId
                    val sameVisibleDevice = deviceName != null &&
                            meta["installationId"].isNullOrBlank() &&
                            meta["deviceName"] == deviceName &&
                            meta["platformName"] == platformName &&
                            meta["osName"] == osName

                    if (sameInstallation || sameVisibleDevice) row[RefreshSessions.id] else null
                }

            if (oldSameDeviceSessionIds.isNotEmpty()) {
                RefreshSessions.update({ RefreshSessions.id inList oldSameDeviceSessionIds }) {
                    it[revokedAt] = now
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
        }

        TokenPair(
            signAccessAsync.await(),
            now.toEpochMilli() + cfg.accessTTL,
            refreshPlain,
            now.toEpochMilli() + cfg.refreshTTL
        )
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

        val rotatedMeta = oldSession[RefreshSessions.meta].orEmpty() + metaParam.orEmpty()

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
                it[meta] = rotatedMeta
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

private object RealtimeServerBus {
    private val updates = MutableSharedFlow<RealtimeUpdateDataModel>(
        extraBufferCapacity = 512
    )

    val sharedUpdates = updates.asSharedFlow()

    suspend fun publish(
        entity: String = "all",
        storeId: String? = null,
        reason: String? = null
    ) {
        updates.emit(
            RealtimeUpdateDataModel(
                id = UUID.randomUUID().toString(),
                type = "changed",
                entity = entity,
                storeId = storeId,
                reason = reason,
                createdAtMillis = System.currentTimeMillis()
            )
        )
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
private fun startSubscriptionRenewalDaemon() {
    if (subscriptionRenewalDaemonStarted) return
    subscriptionRenewalDaemonStarted = true
    kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
        while (true) {
            runCatching { runDueSubscriptionRenewalsOnce() }
            kotlinx.coroutines.delay(60_000L)
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

private fun RoutingCall.headerUuid(name: String): UUID? {
    return request.header(name)
        ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
}

private suspend inline fun <reified T> RoutingCall.receiveOneOrList(): List<T> {
    val raw = receiveText().trim()

    return if (raw.startsWith("[")) {
        jsonBase.decodeFromString<List<T>>(raw)
    } else {
        listOf(jsonBase.decodeFromString<T>(raw))
    }
}

private fun List<String>.cleanBarcodes(): List<String> {
    return flatMap { it.trim().toStoredGoodsItemBarcodeCandidates() }
        .filter { it.isNotEmpty() }
        .distinct()
}

private fun rootStoreIdForAccessInsideTransaction(storeId: UUID): UUID {
    return Stores
        .select(Stores.parentStoreId)
        .where { Stores.id eq storeId }
        .singleOrNull()
        ?.get(Stores.parentStoreId)
        ?: storeId
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
    return StoreWorkerMemberships
        .select(StoreWorkerMemberships.permissions)
        .where {
            (StoreWorkerMemberships.userId eq userId) and
                    ((StoreWorkerMemberships.storeId eq storeId) or (StoreWorkerMemberships.storeId eq rootStoreId)) and
                    (StoreWorkerMemberships.isActive eq true)
        }
        .singleOrNull()
        ?.get(StoreWorkerMemberships.permissions)
        .orEmpty()
}

private fun userHasStorePermissionInsideTransaction(userId: UUID, storeId: UUID, permission: String): Boolean {
    if (isStoreOwnerInsideTransaction(userId, storeId)) return true
    return permission in activeWorkerPermissionsInsideTransaction(userId, storeId)
}

private fun requiredPermissionForTransactionType(type: String): String? {
    return when (type) {
        "purchase" -> STORE_PERMISSION_SALE_TRANSACTION
        "return" -> STORE_PERMISSION_RETURN_TRANSACTION
        "accept" -> STORE_PERMISSION_SUPPLY_TRANSACTION
        else -> null
    }
}

private fun cleanPermissionIds(input: List<String>): List<String> {
    val known = ALL_STORE_PERMISSION_IDS.toSet()
    return input.filter { it in known }.distinct()
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
        permissions = this[StoreWorkerMemberships.permissions],
        requestedAtMillis = this[StoreWorkerMemberships.requestedAtMillis],
        acceptedAtMillis = this[StoreWorkerMemberships.acceptedAtMillis],
        acceptedByUserId = this[StoreWorkerMemberships.acceptedByUserId].toString(),
        isActive = this[StoreWorkerMemberships.isActive],
        hasWorkshiftPassword = !this[StoreWorkerMemberships.workshiftPasswordHash].isNullOrBlank()
    )
}

private fun String?.toWorkshiftPasswordHashOrNull(): String? =
    this?.takeIf { it.isNotBlank() }?.let { Pw.hash(it.toCharArray()) }

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
        permissions = this[StoreWorkerRequests.permissions],
        note = this[StoreWorkerRequests.note]
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
    return GoodsItemDataModel(
        id = this[StockItems.id].toString(),
        userId = this[StockItems.userId].toString(),
        storeId = this[StockItems.storeId].toString(),

        barcodes = this[StockItems.barcodes],
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

        note = this[StockItems.note],
        noteLocalized = this[StockItems.noteLocalized],
        conditions = this[StockItems.conditions],

        createdAtMillis = this[StockItems.createdAtMillis],
        updatedAtMillis = this[StockItems.updatedAtMillis],
        isActive = this[StockItems.isActive]
    )
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

        shelfPosition = this[StockBatchesV2.shelfPosition],
        shelfPriority = this[StockBatchesV2.shelfPriority],

        status = runCatching {
            StockBatchStatusDataModel.valueOf(this[StockBatchesV2.status])
        }.getOrDefault(StockBatchStatusDataModel.Delivered),

        additionalNotes = this[StockBatchesV2.additionalNotes],

        createdAtMillis = this[StockBatchesV2.createdAtMillis],
        updatedAtMillis = this[StockBatchesV2.updatedAtMillis],
        createdByUserId = this[StockBatchesV2.createdByUserId]?.toString(),

        isActive = this[StockBatchesV2.isActive]
    )
}

private fun ResultRow.toStockBatchMovementDataModel(): StockBatchMovementDataModel {
    val movementUserId = this[StockBatchMovements.userId]
    val userRow = Users
        .select(Users.firstName, Users.lastName)
        .where { Users.id eq movementUserId }
        .singleOrNull()

    val movedByName = userRow?.let { row ->
        "${row[Users.firstName]} ${row[Users.lastName]}".trim()
    }.orEmpty()

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
        movedByUserId = this[StockBatchMovements.userId].toString(),
        movedByName = movedByName,
        movedAtMillis = this[StockBatchMovements.movedAtMillis],
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
    val barcodeTokens = row[StockItems.barcodes]
        .flatMap { barcode -> barcode.toStoredGoodsItemBarcodeCandidates() + listOf(barcode) }
        .map { it.normalizedBarcodeToken() }
        .filter { it.isNotBlank() }

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

    return source[StockItems.barcodes].any { sourceBarcode ->
        candidate[StockItems.barcodes].any { candidateBarcode ->
            storedBarcodeMatchesScannedTransactionBarcode(sourceBarcode, candidateBarcode) ||
                    storedBarcodeMatchesScannedTransactionBarcode(candidateBarcode, sourceBarcode)
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
        it[StockItems.barcodes] = sourceItemRow[StockItems.barcodes]
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
        it[StockItems.note] = sourceItemRow[StockItems.note]
        it[StockItems.noteLocalized] = sourceItemRow[StockItems.noteLocalized]
        it[StockItems.conditions] = sourceItemRow[StockItems.conditions]
        it[StockItems.createdAtMillis] = now
        it[StockItems.updatedAtMillis] = now
        it[StockItems.isActive] = true
    }

    return StockItems.selectAll().where { StockItems.id eq id }.single()
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
    barcodes: List<String>
): Boolean {
    if (barcodes.isEmpty()) return false

    return StockItems
        .selectAll()
        .where {
            (StockItems.storeId eq storeId) and
                    (StockItems.isActive eq true)
        }
        .any { row ->
            val rowId = row[StockItems.id]
            val sameItem = currentItemId != null && rowId == currentItemId

            !sameItem && row[StockItems.barcodes].any { existingBarcode ->
                barcodes.any { incomingBarcode ->
                    storedBarcodeMatchesScannedTransactionBarcode(
                        storedBarcode = existingBarcode,
                        scannedBarcode = incomingBarcode
                    ) || storedBarcodeMatchesScannedTransactionBarcode(
                        storedBarcode = incomingBarcode,
                        scannedBarcode = existingBarcode
                    )
                }
            }
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
    barcode: String
): ResultRow? {
    val cleanBarcode = barcode.trim()
    if (cleanBarcode.isBlank()) return null

    return StockItems
        .selectAll()
        .where {
            (StockItems.storeId eq storeId) and
                    (StockItems.isActive eq true)
        }
        .firstOrNull { row ->
            row[StockItems.barcodes].any { storedBarcode ->
                storedBarcodeMatchesScannedTransactionBarcode(
                    storedBarcode = storedBarcode,
                    scannedBarcode = cleanBarcode
                )
            }
        }
}

private fun activeStockBatchesForGoodsItemInsideTransaction(
    storeId: UUID,
    goodsItemId: UUID,
    activeShelfBatchId: UUID?
): List<ResultRow> {
    return StockBatchesV2
        .selectAll()
        .where {
            (StockBatchesV2.storeId eq storeId) and
                    (StockBatchesV2.goodsItemId eq goodsItemId) and
                    (StockBatchesV2.isActive eq true)
        }
        .filter { row ->
            val status = row[StockBatchesV2.status]
            status != StockBatchStatusDataModel.Deleted.name &&
                    status != StockBatchStatusDataModel.WrittenOff.name
        }
        .sortedWith(
            compareBy<ResultRow> { row ->
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
        val itemRow = findStockItemRowByTransactionBarcodeInsideTransaction(storeId, line.barcode)
            ?: return NormalizedTransactionGoodsResult(null, "not_found")

        val goodsItem = itemRow.toGoodsItemDataModel()
        val activeBatch = activeStockBatchesForGoodsItemInsideTransaction(
            storeId = storeId,
            goodsItemId = itemRow[StockItems.id],
            activeShelfBatchId = itemRow[StockItems.activeShelfBatchId]
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
            saleMethodId = appliedSaleMethodId
        )
    }

    return NormalizedTransactionGoodsResult(normalizedLines)
}

private fun updateGoodsItemActiveShelfBatchInsideTransaction(
    goodsItemId: UUID,
    storeId: UUID,
    now: Long
) {
    val currentActiveBatchId = StockItems
        .selectAll()
        .where {
            (StockItems.id eq goodsItemId) and
                    (StockItems.storeId eq storeId)
        }
        .firstOrNull()
        ?.get(StockItems.activeShelfBatchId)

    val currentActiveBatchStillUsable = currentActiveBatchId?.let { activeId ->
        StockBatchesV2
            .selectAll()
            .where {
                (StockBatchesV2.id eq activeId) and
                        (StockBatchesV2.goodsItemId eq goodsItemId) and
                        (StockBatchesV2.storeId eq storeId) and
                        (StockBatchesV2.isActive eq true)
            }
            .firstOrNull()
            ?.let { row ->
                val status = row[StockBatchesV2.status]
                status != StockBatchStatusDataModel.Deleted.name &&
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
                        (StockBatchesV2.storeId eq storeId) and
                        (StockBatchesV2.isActive eq true)
            }
            .filter { row ->
                val status = row[StockBatchesV2.status]
                status != StockBatchStatusDataModel.Deleted.name &&
                        status != StockBatchStatusDataModel.WrittenOff.name &&
                        status != StockBatchStatusDataModel.SoldOut.name &&
                        row[StockBatchesV2.quantity].total > 0.0
            }
            .sortedWith(
                compareBy<ResultRow> { row ->
                    row[StockBatchesV2.shelfPriority]
                }.thenBy { row ->
                    row[StockBatchesV2.expirationDateMillis] ?: Long.MAX_VALUE
                }
            )
            .firstOrNull()
            ?.get(StockBatchesV2.id)
    }

    if (nextBatchId != currentActiveBatchId) {
        StockItems.update({
            (StockItems.id eq goodsItemId) and
                    (StockItems.storeId eq storeId)
        }) {
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
        activeShelfBatchId = activeShelfBatchId
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

    updateGoodsItemActiveShelfBatchInsideTransaction(goodsItemId, storeId, now)
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
        it[StockBatchesV2.shelfPosition] = null
        it[StockBatchesV2.shelfPriority] = 0
        it[StockBatchesV2.status] = StockBatchStatusDataModel.Delivered.name
        it[StockBatchesV2.additionalNotes] = null
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

private fun applyTransactionStockMutationInsideTransaction(
    userId: UUID,
    storeId: UUID,
    transaction: TransactionDataModel,
    now: Long
): Boolean {
    for (line in transaction.goodsInTransaction) {
        val itemRow = findStockItemRowByTransactionBarcodeInsideTransaction(storeId, line.barcode)
            ?: return false

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
                    CachingOptions(
                        CacheControl.MaxAge(
                            maxAgeSeconds = 30 * 24 * 3600
                        )
                    )

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

    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 30.seconds
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            cause.printStackTrace()
            val response = GenericResponseDataModel(
                message = jsonBase.encodeToString(
                    listOf(
                        LocalizedStringDataModel(
                            language = "main",
                            value = cause.message ?: cause::class.simpleName.orEmpty().ifBlank { "Internal server error" }
                        )
                    )
                ),
                payload = null,
                negative = true
            )
            call.respondText(
                text = jsonBase.encodeToString(response),
                contentType = ContentType.Application.Json,
                status = HttpStatusCode.InternalServerError
            )
        }
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

    org.jetbrains.exposed.sql.transactions.transaction {
        SchemaUtils.createMissingTablesAndColumns(Users, Stores, StockItems, StockBatchesV2, StockBatchMovements, Suppliers, Debtors, Notifications, StoreWorkerRequests, StoreWorkerMemberships, Workshifts, CashRegisters, CashRegisterEvents, UserWallets, UserWalletLedgerEntries, TopUpPaymentIntents, StoreSubscriptionStates, StoreSubscriptionChargeEvents)
    }

    configureJwtAuth()
    startSubscriptionRenewalDaemon()

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

            RealtimeServerBus.publish(
                entity = path.trim('/').ifBlank { "all" },
                storeId = storeId,
                reason = "mutation"
            )
        }
    }

    val tokenService = TokenService(jwtConfig())

    routing {
        authenticate("auth-jwt") {
            webSocket("/rt/updates") {
                val principal = call.principal<JWTPrincipal>()
                val userId = runCatching { principal?.subject?.let { UUID.fromString(it) } }.getOrNull()

                if (userId == null) {
                    close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Unauthorized"))
                    return@webSocket
                }

                send(
                    Frame.Text(
                        jsonBase.encodeToString(
                            RealtimeUpdateDataModel(
                                id = UUID.randomUUID().toString(),
                                type = "connected",
                                entity = "connection",
                                createdAtMillis = System.currentTimeMillis()
                            )
                        )
                    )
                )

                val collector = launch {
                    RealtimeServerBus.sharedUpdates.collect { update ->
                        send(Frame.Text(jsonBase.encodeToString(update)))
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
            }
        }

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
                                    it[Users.publicId] = generateUniqueUserPublicIdInsideTransaction()
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
                        val tokenPair: TokenPair = tokenService.newPair(this, metaFrom(call, body.deviceInfo))

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
                try {
                    val body = call.receive<UserAuthLogInDataModel>()

                    val login = body.login.trim().lowercase()

                    val user = newSuspendedTransaction(Dispatchers.IO) {
                        Users.selectAll().where { (Users.phoneNumber eq login) or (Users.email eq login) }.singleOrNull()
                    } ?: return@post call.respond(UnauthorizedResponse())

                    val ok = Pw.verify(body.password.toCharArray(), user[Users.passwordHash])

                    if (!ok)
                        return@post call.respond(UnauthorizedResponse())

                    val tokenPair: TokenPair = tokenService.newPair(user[Users.id], metaFrom(call, body.deviceInfo))

                    call.genericResponse<TokenPair>(HttpStatusCode.OK, tokenPair)
                } catch (throwable: Throwable) {
                    throwable.printStackTrace()
                    call.genericResponseNoPayload(
                        status = HttpStatusCode.InternalServerError,
                        message = getResponse("3").message
                    )
                }
            }

            delete("/logOut") {
                val refreshToken = runCatching { call.receiveText().trim() }
                    .getOrNull()
                    .orEmpty()
                    .trim('"')

                if (refreshToken.isNotBlank()) {
                    try {
                        tokenService.revoke(refreshToken)
                    } catch (throwable: Throwable) {
                        throwable.printStackTrace()
                    }
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

                post("/revoke") {
                    val userId = call.checkPrincipal() ?: return@post
                    val currentSessionId = call.currentJwtSessionId()
                    val request = runCatching { call.receive<SecuritySessionRevokeRequestDataModel>() }.getOrNull()
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

                    newSuspendedTransaction(Dispatchers.IO) {
                        RefreshSessions.update({
                            (RefreshSessions.id eq targetSessionId) and
                                    (RefreshSessions.userId eq userId) and
                                    RefreshSessions.revokedAt.isNull()
                        }) {
                            it[revokedAt] = Instant.now()
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

                    newSuspendedTransaction(Dispatchers.IO) {
                        RefreshSessions.update({
                            (RefreshSessions.userId eq userId) and
                                    RefreshSessions.revokedAt.isNull() and
                                    (RefreshSessions.id neq sessionToKeep)
                        }) {
                            it[revokedAt] = Instant.now()
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

        route("/notifications") {
            authenticate("auth-jwt") {
                get("/get") {
                    val userId = call.checkPrincipal() ?: return@get

                    val notifications = newSuspendedTransaction(Dispatchers.IO) {
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
                    val body = call.receive<NotificationDataModel>()
                    val now = System.currentTimeMillis()
                    val notificationId = body.id.ifBlank { "${now}_${body.type.name}_${body.message.hashCode()}" }
                    val storeId = body.storeId?.let { runCatching { UUID.fromString(it) }.getOrNull() }

                    val saved = newSuspendedTransaction(Dispatchers.IO) {
                        val exists = Notifications
                            .selectAll()
                            .where { (Notifications.id eq notificationId) and (Notifications.userId eq userId) }
                            .empty()
                            .not()

                        if (exists) {
                            Notifications.update({ (Notifications.id eq notificationId) and (Notifications.userId eq userId) }) {
                                it[title] = body.title
                                it[message] = body.message
                                it[type] = body.type.name
                                it[category] = body.category.ifBlank { body.type.name.lowercase() }
                                it[notificationSource] = body.source.ifBlank { "app" }
                                it[metadata] = body.metadata
                                it[Notifications.storeId] = storeId
                                it[shownAtMillis] = body.shownAtMillis.takeIf { value -> value > 0L } ?: now
                                it[readAtMillis] = body.readAtMillis
                                it[isActive] = true
                            }
                        } else {
                            Notifications.insert {
                                it[id] = notificationId
                                it[Notifications.userId] = userId
                                it[Notifications.storeId] = storeId
                                it[title] = body.title
                                it[message] = body.message
                                it[type] = body.type.name
                                it[category] = body.category.ifBlank { body.type.name.lowercase() }
                                it[notificationSource] = body.source.ifBlank { "app" }
                                it[metadata] = body.metadata
                                it[createdAtMillis] = body.createdAtMillis.takeIf { value -> value > 0L } ?: now
                                it[shownAtMillis] = body.shownAtMillis.takeIf { value -> value > 0L } ?: now
                                it[readAtMillis] = body.readAtMillis
                                it[isActive] = true
                            }
                        }

                        Notifications
                            .selectAll()
                            .where { (Notifications.id eq notificationId) and (Notifications.userId eq userId) }
                            .single()
                            .toNotificationDataModel()
                    }

                    call.genericResponse(HttpStatusCode.Created, saved)
                }

                put("/read") {
                    val userId = call.checkPrincipal() ?: return@put
                    val ids = runCatching { call.receive<List<String>>() }.getOrElse {
                        val one = call.receiveText().trim().trim('"')
                        listOf(one)
                    }.filter { it.isNotBlank() }
                    val now = System.currentTimeMillis()

                    val updated = newSuspendedTransaction(Dispatchers.IO) {
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
                                            alias = it[GenericGoodsCategories.alias],
                                            description = it[GenericGoodsCategories.description],
                                            quantityUnitId = it[GenericGoodsCategories.quantityUnitId],
                                            imagePaths = it[GenericGoodsCategories.imagePaths]
                                        )
                                    }

                                0 to matches
                            }

                        when {
                            genericGoodsCategories.first == 1 -> call.respond(UnauthorizedResponse())
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

        route("/stock") {
            authenticate("auth-jwt") {
                get("/get") {
                    val userId = call.checkPrincipal() ?: return@get
                    val storeId = call.headerUuid("store_id")
                        ?: return@get call.respond(UnauthorizedResponse())

                    val result = newSuspendedTransaction(Dispatchers.IO) {
                        if (!userHasStoreAccessInsideTransaction(userId, storeId))
                            return@newSuspendedTransaction null

                        StockItems
                            .selectAll()
                            .where {
                                (StockItems.storeId eq storeId) and
                                        (StockItems.isActive eq true)
                            }
                            .map { it.toGoodsItemDataModel() }
                    }

                    result?.let {
                        call.genericListResponse(
                            status = HttpStatusCode.OK,
                            payload = it
                        )
                    } ?: call.respond(UnauthorizedResponse())
                }

                post("/add") {
                    val userId = call.checkPrincipal() ?: return@post
                    val body = call.receive<GoodsItemDataModel>()

                    val inserted = newSuspendedTransaction(Dispatchers.IO) {
                        val storeId = runCatching { UUID.fromString(body.storeId) }.getOrNull()
                            ?: return@newSuspendedTransaction null

                        if (!userHasStoreAccessInsideTransaction(userId, storeId))
                            return@newSuspendedTransaction null

                        val cleanBarcodes = body.barcodes.cleanBarcodes()

                        if (cleanBarcodes.isEmpty())
                            return@newSuspendedTransaction null

                        if (barcodeClashesInsideTransaction(storeId, null, cleanBarcodes))
                            return@newSuspendedTransaction null

                        val now = System.currentTimeMillis()
                        val id = UUID.randomUUID()

                        StockItems.insert {
                            it[StockItems.id] = id
                            it[StockItems.userId] = userId
                            it[StockItems.storeId] = storeId

                            it[StockItems.barcodes] = cleanBarcodes
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

                            it[StockItems.note] = body.note
                            it[StockItems.noteLocalized] = body.noteLocalized
                            it[StockItems.conditions] = body.conditions.map { condition -> condition.trim() }.filter { condition -> condition.isNotBlank() }.distinct()

                            it[StockItems.createdAtMillis] = now
                            it[StockItems.updatedAtMillis] = now
                            it[StockItems.isActive] = true
                        }

                        body.copy(
                            id = id.toString(),
                            userId = userId.toString(),
                            storeId = storeId.toString(),
                            barcodes = cleanBarcodes,
                            conditions = body.conditions.map { condition -> condition.trim() }.filter { condition -> condition.isNotBlank() }.distinct(),
                            createdAtMillis = now,
                            updatedAtMillis = now,
                            isActive = true
                        )
                    }

                    inserted?.let {
                        call.genericResponse(
                            status = HttpStatusCode.Created,
                            payload = it,
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
                    val body = call.receive<GoodsItemDataModel>()

                    val updated = newSuspendedTransaction(Dispatchers.IO) {
                        val id = runCatching { UUID.fromString(body.id) }.getOrNull()
                            ?: return@newSuspendedTransaction null

                        val storeId = runCatching { UUID.fromString(body.storeId) }.getOrNull()
                            ?: return@newSuspendedTransaction null

                        if (!userHasStoreAccessInsideTransaction(userId, storeId))
                            return@newSuspendedTransaction null

                        val cleanBarcodes = body.barcodes.cleanBarcodes()

                        if (cleanBarcodes.isEmpty())
                            return@newSuspendedTransaction null

                        if (barcodeClashesInsideTransaction(storeId, id, cleanBarcodes))
                            return@newSuspendedTransaction null

                        val now = System.currentTimeMillis()

                        val affected = StockItems.update({
                            (StockItems.id eq id) and
                                    (StockItems.storeId eq storeId) and
                                    (StockItems.userId eq userId)
                        }) {
                            it[StockItems.barcodes] = cleanBarcodes
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

                            it[StockItems.note] = body.note
                            it[StockItems.noteLocalized] = body.noteLocalized
                            it[StockItems.conditions] = body.conditions.map { condition -> condition.trim() }.filter { condition -> condition.isNotBlank() }.distinct()
                            it[StockItems.updatedAtMillis] = now
                            it[StockItems.isActive] = body.isActive
                        }

                        if (affected <= 0)
                            return@newSuspendedTransaction null

                        body.copy(
                            userId = userId.toString(),
                            storeId = storeId.toString(),
                            barcodes = cleanBarcodes,
                            conditions = body.conditions.map { condition -> condition.trim() }.filter { condition -> condition.isNotBlank() }.distinct(),
                            updatedAtMillis = now
                        )
                    }

                    updated?.let {
                        call.genericResponse(
                            status = HttpStatusCode.OK,
                            payload = it,
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
                    val rawId = call.receive<String>()
                    val storeId = call.headerUuid("store_id")
                        ?: return@delete call.respond(UnauthorizedResponse())

                    val deletedId = newSuspendedTransaction(Dispatchers.IO) {
                        if (!userHasStoreAccessInsideTransaction(userId, storeId))
                            return@newSuspendedTransaction null

                        val id = runCatching { UUID.fromString(rawId) }.getOrNull()
                            ?: return@newSuspendedTransaction null

                        val now = System.currentTimeMillis()

                        val affected = StockItems.update({
                            (StockItems.id eq id) and
                                    (StockItems.storeId eq storeId) and
                                    (StockItems.userId eq userId)
                        }) {
                            it[StockItems.isActive] = false
                            it[StockItems.updatedAtMillis] = now
                        }

                        if (affected <= 0)
                            return@newSuspendedTransaction null

                        StockBatchesV2.update({
                            (StockBatchesV2.goodsItemId eq id) and
                                    (StockBatchesV2.storeId eq storeId) and
                                    (StockBatchesV2.userId eq userId)
                        }) {
                            it[StockBatchesV2.isActive] = false
                            it[StockBatchesV2.updatedAtMillis] = now
                        }

                        rawId
                    }

                    deletedId?.let {
                        call.genericResponse(
                            status = HttpStatusCode.OK,
                            payload = it,
                            message = getResponse("16").message
                        )
                    } ?: call.respond(UnauthorizedResponse())
                }
            }
        }

        route("/stockBatches") {
            authenticate("auth-jwt") {
                get("/get") {
                    val userId = call.checkPrincipal() ?: return@get
                    val storeId = call.headerUuid("store_id")
                        ?: return@get call.respond(UnauthorizedResponse())

                    val result = newSuspendedTransaction(Dispatchers.IO) {
                        if (!userHasStoreAccessInsideTransaction(userId, storeId))
                            return@newSuspendedTransaction null

                        StockBatchesV2
                            .selectAll()
                            .where {
                                (StockBatchesV2.storeId eq storeId) and
                                        (StockBatchesV2.isActive eq true)
                            }
                            .map { it.toGoodsBatchDataModel() }
                    }

                    result?.let {
                        call.genericListResponse(
                            status = HttpStatusCode.OK,
                            payload = it
                        )
                    } ?: call.respond(UnauthorizedResponse())
                }

                get("/branchAvailability") {
                    val userId = call.checkPrincipal() ?: return@get
                    val storeId = call.headerUuid("store_id")
                        ?: return@get call.respond(UnauthorizedResponse())
                    val goodsItemId = call.headerUuid("goods_item_id")
                        ?: return@get call.genericResponseNoPayload(HttpStatusCode.BadRequest, message = getResponse("13").message)

                    val availability = newSuspendedTransaction(Dispatchers.IO) {
                        if (!userHasStoreAccessInsideTransaction(userId, storeId))
                            return@newSuspendedTransaction null

                        buildStockBranchAvailabilityInsideTransaction(storeId, goodsItemId)
                    }

                    availability?.let {
                        call.genericResponse(
                            status = HttpStatusCode.OK,
                            payload = it,
                            message = getResponse("73").message
                        )
                    } ?: call.respond(UnauthorizedResponse())
                }

                post("/move") {
                    val userId = call.checkPrincipal() ?: return@post
                    val request = call.receive<StockBatchMoveRequestDataModel>()

                    val result = newSuspendedTransaction(Dispatchers.IO) {
                        val sourceStoreId = runCatching { UUID.fromString(request.sourceStoreId) }.getOrNull()
                            ?: return@newSuspendedTransaction null
                        val destinationStoreId = runCatching { UUID.fromString(request.destinationStoreId) }.getOrNull()
                            ?: return@newSuspendedTransaction null
                        val sourceGoodsItemId = runCatching { UUID.fromString(request.sourceGoodsItemId) }.getOrNull()
                            ?: return@newSuspendedTransaction null
                        val sourceBatchId = runCatching { UUID.fromString(request.sourceBatchId) }.getOrNull()
                            ?: return@newSuspendedTransaction null

                        if (sourceStoreId == destinationStoreId)
                            return@newSuspendedTransaction null

                        val rootSourceStoreId = rootStoreIdForAccessInsideTransaction(sourceStoreId)
                        val rootDestinationStoreId = rootStoreIdForAccessInsideTransaction(destinationStoreId)

                        if (rootSourceStoreId != rootDestinationStoreId)
                            return@newSuspendedTransaction null

                        if (!userHasStorePermissionInsideTransaction(userId, sourceStoreId, STORE_PERMISSION_STOCK_WRITE))
                            return@newSuspendedTransaction null

                        if (!userHasStorePermissionInsideTransaction(userId, destinationStoreId, STORE_PERMISSION_STOCK_WRITE))
                            return@newSuspendedTransaction null

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
                            it[StockBatchesV2.deliveredAtMillis] = sourceBatchRow[StockBatchesV2.deliveredAtMillis] ?: now
                            it[StockBatchesV2.manufacturedAtMillis] = sourceBatchRow[StockBatchesV2.manufacturedAtMillis]
                            it[StockBatchesV2.expirationDateMillis] = sourceBatchRow[StockBatchesV2.expirationDateMillis]
                            it[StockBatchesV2.discounts] = sourceBatchRow[StockBatchesV2.discounts]
                            it[StockBatchesV2.shelfPosition] = sourceBatchRow[StockBatchesV2.shelfPosition]
                            it[StockBatchesV2.shelfPriority] = activePhysicalBatchRowsForGoodsItemInsideTransaction(destinationStoreId, destinationGoodsItemId).size
                            it[StockBatchesV2.status] = StockBatchStatusDataModel.Delivered.name
                            it[StockBatchesV2.additionalNotes] = request.note?.takeIf { note -> note.isNotBlank() }
                            it[StockBatchesV2.createdAtMillis] = now
                            it[StockBatchesV2.updatedAtMillis] = now
                            it[StockBatchesV2.createdByUserId] = userId
                            it[StockBatchesV2.isActive] = true
                        }

                        if (destinationItemRow[StockItems.activeShelfBatchId] == null) {
                            StockItems.update({ StockItems.id eq destinationGoodsItemId }) {
                                it[StockItems.activeShelfBatchId] = destinationBatchId
                                it[StockItems.updatedAtMillis] = now
                            }
                        }

                        updateGoodsItemActiveShelfBatchInsideTransaction(sourceGoodsItemId, sourceStoreId, now)
                        updateGoodsItemActiveShelfBatchInsideTransaction(destinationGoodsItemId, destinationStoreId, now)

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
                        }

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
                            availability = availability
                        )
                    }

                    result?.let {
                        call.genericResponse(
                            status = HttpStatusCode.OK,
                            payload = it,
                            message = getResponse("74").message
                        )
                    } ?: call.genericResponseNoPayload(
                        status = HttpStatusCode.BadRequest,
                        message = getResponse("75").message
                    )
                }

                post("/add") {
                    val userId = call.checkPrincipal() ?: return@post
                    val bodies = call.receiveOneOrList<GoodsBatchDataModel>()

                    val inserted = newSuspendedTransaction(Dispatchers.IO) {
                        val result = mutableListOf<GoodsBatchDataModel>()
                        val now = System.currentTimeMillis()

                        for (body in bodies) {
                            val storeId = runCatching { UUID.fromString(body.storeId) }.getOrNull()
                                ?: return@newSuspendedTransaction null

                            val goodsItemId = runCatching { UUID.fromString(body.goodsItemId) }.getOrNull()
                                ?: return@newSuspendedTransaction null

                            if (!userHasStoreAccessInsideTransaction(userId, storeId))
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

                            val id = UUID.randomUUID()

                            StockBatchesV2.insert {
                                it[StockBatchesV2.id] = id
                                it[StockBatchesV2.goodsItemId] = goodsItemId
                                it[StockBatchesV2.userId] = userId
                                it[StockBatchesV2.storeId] = storeId

                                it[StockBatchesV2.supplierId] = body.supplierId?.let(UUID::fromString)
                                it[StockBatchesV2.supplierOrderId] = body.supplierOrderId?.let(UUID::fromString)

                                it[StockBatchesV2.quantity] = body.quantity

                                it[StockBatchesV2.supplyPrice] = body.supplyPrice
                                it[StockBatchesV2.salePriceOverride] = body.salePriceOverride
                                it[StockBatchesV2.returnPriceOverride] = body.returnPriceOverride
                                it[StockBatchesV2.wholesalePriceOverride] = body.wholesalePriceOverride

                                it[StockBatchesV2.deliveredAtMillis] = body.deliveredAtMillis ?: now
                                it[StockBatchesV2.manufacturedAtMillis] = body.manufacturedAtMillis
                                it[StockBatchesV2.expirationDateMillis] = body.expirationDateMillis

                                it[StockBatchesV2.discounts] = body.discounts

                                it[StockBatchesV2.shelfPosition] = body.shelfPosition
                                it[StockBatchesV2.shelfPriority] = body.shelfPriority

                                it[StockBatchesV2.status] = body.status.name
                                it[StockBatchesV2.additionalNotes] = body.additionalNotes

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

                            result += body.copy(
                                id = id.toString(),
                                userId = userId.toString(),
                                storeId = storeId.toString(),
                                deliveredAtMillis = body.deliveredAtMillis ?: now,
                                createdAtMillis = now,
                                updatedAtMillis = now,
                                createdByUserId = userId.toString(),
                                isActive = true
                            )
                        }

                        result
                    }

                    inserted?.let {
                        call.genericResponse(
                            status = HttpStatusCode.Created,
                            payload = it,
                            message = getResponse("17").message
                        )
                    } ?: call.respond(UnauthorizedResponse())
                }

                put("/update") {
                    val userId = call.checkPrincipal() ?: return@put
                    val bodies = call.receiveOneOrList<GoodsBatchDataModel>()

                    val updated = newSuspendedTransaction(Dispatchers.IO) {
                        val result = mutableListOf<GoodsBatchDataModel>()
                        val now = System.currentTimeMillis()

                        for (body in bodies) {
                            val id = runCatching { UUID.fromString(body.id) }.getOrNull()
                                ?: return@newSuspendedTransaction null

                            val storeId = runCatching { UUID.fromString(body.storeId) }.getOrNull()
                                ?: return@newSuspendedTransaction null

                            val goodsItemId = runCatching { UUID.fromString(body.goodsItemId) }.getOrNull()
                                ?: return@newSuspendedTransaction null

                            if (!userHasStoreAccessInsideTransaction(userId, storeId))
                                return@newSuspendedTransaction null

                            val affected = StockBatchesV2.update({
                                (StockBatchesV2.id eq id) and
                                        (StockBatchesV2.userId eq userId) and
                                        (StockBatchesV2.storeId eq storeId)
                            }) {
                                it[StockBatchesV2.goodsItemId] = goodsItemId

                                it[StockBatchesV2.supplierId] = body.supplierId?.let(UUID::fromString)
                                it[StockBatchesV2.supplierOrderId] = body.supplierOrderId?.let(UUID::fromString)

                                it[StockBatchesV2.quantity] = body.quantity

                                it[StockBatchesV2.supplyPrice] = body.supplyPrice
                                it[StockBatchesV2.salePriceOverride] = body.salePriceOverride
                                it[StockBatchesV2.returnPriceOverride] = body.returnPriceOverride
                                it[StockBatchesV2.wholesalePriceOverride] = body.wholesalePriceOverride

                                it[StockBatchesV2.deliveredAtMillis] = body.deliveredAtMillis
                                it[StockBatchesV2.manufacturedAtMillis] = body.manufacturedAtMillis
                                it[StockBatchesV2.expirationDateMillis] = body.expirationDateMillis

                                it[StockBatchesV2.discounts] = body.discounts

                                it[StockBatchesV2.shelfPosition] = body.shelfPosition
                                it[StockBatchesV2.shelfPriority] = body.shelfPriority

                                it[StockBatchesV2.status] = body.status.name
                                it[StockBatchesV2.additionalNotes] = body.additionalNotes

                                it[StockBatchesV2.updatedAtMillis] = now
                                it[StockBatchesV2.isActive] = body.isActive
                            }

                            if (affected <= 0)
                                return@newSuspendedTransaction null

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

                            result += body.copy(
                                userId = userId.toString(),
                                storeId = storeId.toString(),
                                updatedAtMillis = now
                            )
                        }

                        result
                    }

                    updated?.let {
                        call.genericResponse(
                            status = HttpStatusCode.OK,
                            payload = it,
                            message = getResponse("18").message
                        )
                    } ?: call.respond(UnauthorizedResponse())
                }

                delete("/delete") {
                    val userId = call.checkPrincipal() ?: return@delete
                    val ids = call.receiveOneOrList<String>()
                    val storeId = call.headerUuid("store_id")
                        ?: return@delete call.respond(UnauthorizedResponse())

                    val deletedIds = newSuspendedTransaction(Dispatchers.IO) {
                        if (!userHasStoreAccessInsideTransaction(userId, storeId))
                            return@newSuspendedTransaction null

                        val now = System.currentTimeMillis()
                        val result = mutableListOf<String>()

                        for (rawId in ids) {
                            val id = runCatching { UUID.fromString(rawId) }.getOrNull()
                                ?: continue

                            val affected = StockBatchesV2.update({
                                (StockBatchesV2.id eq id) and
                                        (StockBatchesV2.userId eq userId) and
                                        (StockBatchesV2.storeId eq storeId)
                            }) {
                                it[StockBatchesV2.isActive] = false
                                it[StockBatchesV2.updatedAtMillis] = now
                            }

                            if (affected > 0)
                                result += rawId
                        }

                        result
                    }

                    deletedIds?.let {
                        call.genericResponse(
                            status = HttpStatusCode.OK,
                            payload = it,
                            message = getResponse("19").message
                        )
                    } ?: call.respond(UnauthorizedResponse())
                }

                post("/setActiveShelfBatch") {
                    val userId = call.checkPrincipal() ?: return@post
                    val body = call.receive<GoodsBatchDataModel>()

                    val updatedItem = newSuspendedTransaction(Dispatchers.IO) {
                        val batchId = runCatching { UUID.fromString(body.id) }.getOrNull()
                            ?: return@newSuspendedTransaction null

                        val goodsItemId = runCatching { UUID.fromString(body.goodsItemId) }.getOrNull()
                            ?: return@newSuspendedTransaction null

                        val storeId = runCatching { UUID.fromString(body.storeId) }.getOrNull()
                            ?: return@newSuspendedTransaction null

                        if (!userHasStoreAccessInsideTransaction(userId, storeId))
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

                        val now = System.currentTimeMillis()

                        StockItems.update({
                            (StockItems.id eq goodsItemId) and
                                    (StockItems.storeId eq storeId) and
                                    (StockItems.userId eq userId)
                        }) {
                            it[StockItems.activeShelfBatchId] = batchId
                            it[StockItems.updatedAtMillis] = now
                        }

                        StockItems
                            .selectAll()
                            .where { StockItems.id eq goodsItemId }
                            .single()
                            .toGoodsItemDataModel()
                    }

                    updatedItem?.let {
                        call.genericResponse(
                            status = HttpStatusCode.OK,
                            payload = it,
                            message = simpleMessage(
                                main = "Shelf batch selected",
                                ru = "Партия на полке выбрана",
                                kk = "Сөредегі партия таңдалды"
                            )
                        )
                    } ?: call.respond(UnauthorizedResponse())
                }
            }
        }

        route("/supplierGoodsPrices") {
            authenticate("auth-jwt") {
                get("/get") {
                    val userId = call.checkPrincipal() ?: return@get
                    val storeId = call.headerUuid("store_id")
                        ?: return@get call.respond(UnauthorizedResponse())

                    val result = newSuspendedTransaction(Dispatchers.IO) {
                        if (!userHasStoreAccessInsideTransaction(userId, storeId))
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
                    } ?: call.respond(UnauthorizedResponse())
                }

                post("/upsert") {
                    val userId = call.checkPrincipal() ?: return@post
                    val body = call.receive<SupplierGoodsPriceDataModel>()

                    val result = newSuspendedTransaction(Dispatchers.IO) {
                        val storeId = runCatching { UUID.fromString(body.storeId) }.getOrNull()
                            ?: return@newSuspendedTransaction null

                        val supplierId = runCatching { UUID.fromString(body.supplierId) }.getOrNull()
                            ?: return@newSuspendedTransaction null

                        val goodsItemId = runCatching { UUID.fromString(body.goodsItemId) }.getOrNull()
                            ?: return@newSuspendedTransaction null

                        if (!userHasStoreAccessInsideTransaction(userId, storeId))
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
                    } ?: call.respond(UnauthorizedResponse())
                }

                delete("/delete") {
                    val userId = call.checkPrincipal() ?: return@delete
                    val ids = call.receiveOneOrList<String>()
                    val storeId = call.headerUuid("store_id")
                        ?: return@delete call.respond(UnauthorizedResponse())

                    val deleted = newSuspendedTransaction(Dispatchers.IO) {
                        if (!userHasStoreAccessInsideTransaction(userId, storeId))
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
                    } ?: call.respond(UnauthorizedResponse())
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
                    val body = call.receive<String>().trim()

                    val activeResult = newSuspendedTransaction(Dispatchers.IO) {
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

                        else -> call.respond(UnauthorizedResponse())
                    }
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
                        return@post call.respond(UnauthorizedResponse())
                    }

                    val parentAccessOk = newSuspendedTransaction(Dispatchers.IO) {
                        parentStoreIdForBranch?.let { parentId ->
                            userHasStorePermissionInsideTransaction(userId, parentId, STORE_PERMISSION_STORE_MANAGE) &&
                                    Stores.select(Stores.parentStoreId).where { Stores.id eq parentId }.singleOrNull()?.get(Stores.parentStoreId) == null
                        } ?: true
                    }

                    if (!parentAccessOk) return@post call.respond(UnauthorizedResponse())

                    var state23505Reached: Boolean

                    var id: UUID? = null
                    var instant = Instant.now()

                    do {
                        state23505Reached = try {
                            id = UUID.randomUUID()
                            instant = Instant.now()

                            newSuspendedTransaction(Dispatchers.IO) {
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
                        val publicId = newSuspendedTransaction(Dispatchers.IO) {
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

                    val body = call.receive<StoreDataModel>()

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

                    val updated = newSuspendedTransaction(Dispatchers.IO) {

                        val id = runCatching { UUID.fromString(body.id) }.getOrNull() ?: return@newSuspendedTransaction 2

                        if (!userHasStorePermissionInsideTransaction(userId, id, STORE_PERMISSION_STORE_MANAGE))
                            return@newSuspendedTransaction 1

                        val currentParentStoreId = Stores
                            .select(Stores.parentStoreId)
                            .where { Stores.id eq id }
                            .singleOrNull()
                            ?.get(Stores.parentStoreId)

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

                        val ownsStore = Stores
                            .select(Stores.ownerUserIds)
                            .where { Stores.id eq id }
                            .singleOrNull()
                            ?.get(Stores.ownerUserIds)
                            ?.contains(userId.toString()) == true

                        if (!ownsStore) {
                            1
                        } else {
                            Stores.deleteWhere { Stores.parentStoreId eq (id as UUID?) }
                            if (Stores.deleteWhere { Stores.id eq id } > 0) 0 else 1
                        }
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
                        suppliers.first == 1 -> call.respond(UnauthorizedResponse())
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
                    val body = call.receive<SupplierDataModel>()

                    val inserted = newSuspendedTransaction(Dispatchers.IO) {
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
                    } ?: call.respond(UnauthorizedResponse())
                }

                put("/update") {
                    val userId = call.checkPrincipal() ?: return@put
                    val body = call.receive<SupplierDataModel>()

                    val updated = newSuspendedTransaction(Dispatchers.IO) {
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
                    } ?: call.respond(UnauthorizedResponse())
                }

                delete("/delete") {
                    val userId = call.checkPrincipal() ?: return@delete
                    val supplierId = call.receive<String>().trim()

                    val deleted = newSuspendedTransaction(Dispatchers.IO) {
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
                        call.respond(UnauthorizedResponse())
                    }
                }
            }
        }

        route("/finance") {
            authenticate("auth-jwt") {
                get("/dashboard") {
                    val userId = call.checkPrincipal() ?: return@get
                    val dashboard = newSuspendedTransaction(Dispatchers.IO) {
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
                    } ?: call.respond(UnauthorizedResponse())
                }

                post("/topup/create") {
                    val userId = call.checkPrincipal() ?: return@post
                    val body = call.receive<TopUpCreateRequestDataModel>()

                    val result = newSuspendedTransaction(Dispatchers.IO) {
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
                    val body = call.receive<TopUpConfirmDevelopmentRequestDataModel>()

                    val dashboard = newSuspendedTransaction(Dispatchers.IO) {
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
                    val storeId = call.headerUuid("store_id") ?: return@get call.respond(UnauthorizedResponse())
                    val dashboard = newSuspendedTransaction(Dispatchers.IO) {
                        if (!userHasStoreAccessInsideTransaction(userId, storeId)) return@newSuspendedTransaction null
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
                    } ?: call.respond(UnauthorizedResponse())
                }

                post("/store/update") {
                    val userId = call.checkPrincipal() ?: return@post
                    val body = call.receive<StoreSubscriptionUpdateRequestDataModel>()
                    val storeId = runCatching { UUID.fromString(body.storeId) }.getOrNull()
                        ?: return@post call.respond(UnauthorizedResponse())

                    val dashboard = newSuspendedTransaction(Dispatchers.IO) {
                        val rootStoreId = rootStoreIdForAccessInsideTransaction(storeId)
                        if (!isStoreOwnerInsideTransaction(userId, rootStoreId)) return@newSuspendedTransaction null
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
                            publicId = user[Users.publicId],
                            phoneNumber = user[Users.phoneNumber],
                            email = user[Users.email],
                            firstName = user[Users.firstName],
                            lastName = user[Users.lastName],
                            countryLocale = user[Users.countryLocale],
                            workerAccountIds = user[Users.workerIds],
                            supplierAccountIds = user[Users.supplierIds],
                            activeStoreId = user[Users.activeStoreId]?.toString(),
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


        route("/debtors") {
            authenticate("auth-jwt") {
                get("/get") {
                    val userId = call.checkPrincipal() ?: return@get
                    val storeId = call.headerUuid("store_id") ?: return@get call.respond(UnauthorizedResponse())

                    val debtors = newSuspendedTransaction(Dispatchers.IO) {
                        if (!userHasStoreAccessInsideTransaction(userId, storeId))
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
                    } ?: call.respond(UnauthorizedResponse())
                }

                post("/add") {
                    val userId = call.checkPrincipal() ?: return@post
                    val storeId = call.headerUuid("store_id") ?: return@post call.respond(UnauthorizedResponse())
                    val body = call.receive<DebtorDataModel>()

                    val debtor = newSuspendedTransaction(Dispatchers.IO) {
                        if (!userHasStoreAccessInsideTransaction(userId, storeId))
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
                    } ?: call.respond(UnauthorizedResponse())
                }

                put("/update") {
                    val userId = call.checkPrincipal() ?: return@put
                    val storeId = call.headerUuid("store_id") ?: return@put call.respond(UnauthorizedResponse())
                    val body = call.receive<DebtorDataModel>()
                    val debtorId = runCatching { UUID.fromString(body.id) }.getOrNull()
                        ?: return@put call.respond(UnauthorizedResponse())

                    val debtor = newSuspendedTransaction(Dispatchers.IO) {
                        if (!userHasStoreAccessInsideTransaction(userId, storeId))
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
                    } ?: call.respond(UnauthorizedResponse())
                }

                delete("/delete") {
                    val userId = call.checkPrincipal() ?: return@delete
                    val storeId = call.headerUuid("store_id") ?: return@delete call.respond(UnauthorizedResponse())
                    val body = call.receive<String>()
                    val debtorId = runCatching { UUID.fromString(body) }.getOrNull()
                        ?: return@delete call.respond(UnauthorizedResponse())

                    val deleted = newSuspendedTransaction(Dispatchers.IO) {
                        if (!userHasStoreAccessInsideTransaction(userId, storeId))
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
                    } ?: call.respond(UnauthorizedResponse())
                }

                post("/pay") {
                    val userId = call.checkPrincipal() ?: return@post
                    val body = call.receive<DebtPaymentRequestDataModel>()
                    val storeId = runCatching { UUID.fromString(body.storeId) }.getOrNull()
                        ?: return@post call.respond(UnauthorizedResponse())
                    val debtorId = runCatching { UUID.fromString(body.debtorId) }.getOrNull()
                        ?: return@post call.respond(UnauthorizedResponse())

                    val debtor = newSuspendedTransaction(Dispatchers.IO) {
                        if (!userHasStoreAccessInsideTransaction(userId, storeId))
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
                    } ?: call.respond(UnauthorizedResponse())
                }
            }
        }


        route("/cashRegister") {
            authenticate("auth-jwt") {
                get("/get") {
                    val userId = call.checkPrincipal() ?: return@get
                    val storeId = call.headerUuid("store_id") ?: return@get call.respond(UnauthorizedResponse())

                    val state = newSuspendedTransaction(Dispatchers.IO) {
                        if (!userHasStoreAccessInsideTransaction(userId, storeId))
                            return@newSuspendedTransaction null

                        if (!userHasStorePermissionInsideTransaction(userId, storeId, STORE_PERMISSION_CASH_REGISTER_VIEW))
                            return@newSuspendedTransaction null

                        cashRegisterStateInsideTransaction(storeId)
                    }

                    state?.let {
                        call.genericResponse(HttpStatusCode.OK, payload = it, message = getResponse("49").message)
                    } ?: call.respond(UnauthorizedResponse())
                }

                post("/extract") {
                    val userId = call.checkPrincipal() ?: return@post
                    val body = call.receive<CashRegisterExtractionRequestDataModel>()
                    val storeId = runCatching { UUID.fromString(body.storeId) }.getOrNull()
                        ?: call.headerUuid("store_id")
                        ?: return@post call.respond(UnauthorizedResponse())
                    val now = body.timeMillis.takeIf { it > 0L } ?: System.currentTimeMillis()
                    val amount = kotlin.math.floor(body.amount.coerceAtLeast(0.0) * 100.0) / 100.0

                    var failureMessage: List<LocalizedStringDataModel>? = null

                    val state = newSuspendedTransaction(Dispatchers.IO) {
                        if (!userHasStoreAccessInsideTransaction(userId, storeId))
                            return@newSuspendedTransaction null

                        if (!userHasStorePermissionInsideTransaction(userId, storeId, STORE_PERMISSION_CASH_REGISTER_EXTRACT)) {
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
                    val result = newSuspendedTransaction(Dispatchers.IO) {
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
                    val storeId = call.headerUuid("store_id") ?: return@get call.respond(UnauthorizedResponse())

                    val result = newSuspendedTransaction(Dispatchers.IO) {
                        if (!userHasStorePermissionInsideTransaction(userId, storeId, STORE_PERMISSION_WORKERS_VIEW))
                            return@newSuspendedTransaction null

                        StoreWorkerMemberships
                            .innerJoin(Stores, { StoreWorkerMemberships.storeId }, { Stores.id })
                            .innerJoin(Users, { StoreWorkerMemberships.userId }, { Users.id })
                            .selectAll()
                            .where { (StoreWorkerMemberships.storeId eq storeId) and (StoreWorkerMemberships.isActive eq true) }
                            .orderBy(StoreWorkerMemberships.acceptedAtMillis, SortOrder.DESC)
                            .map { it.toStoreWorkerDataModel() }
                    }

                    result?.let { call.genericResponse(HttpStatusCode.OK, payload = it, message = getResponse("51").message) }
                        ?: call.respond(UnauthorizedResponse())
                }

                get("/requests/my") {
                    val userId = call.checkPrincipal() ?: return@get
                    val result = newSuspendedTransaction(Dispatchers.IO) {
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
                    val storeId = call.headerUuid("store_id") ?: return@get call.respond(UnauthorizedResponse())

                    val result = newSuspendedTransaction(Dispatchers.IO) {
                        if (!isStoreOwnerInsideTransaction(userId, storeId) && !userHasStorePermissionInsideTransaction(userId, storeId, STORE_PERMISSION_WORKERS_MANAGE))
                            return@newSuspendedTransaction null

                        val requestStoreIds = storeGroupIdsInsideTransaction(rootStoreIdForAccessInsideTransaction(storeId))
                        StoreWorkerRequests
                            .innerJoin(Stores, { StoreWorkerRequests.storeId }, { Stores.id })
                            .innerJoin(Users, { StoreWorkerRequests.requesterUserId }, { Users.id })
                            .selectAll()
                            .where { StoreWorkerRequests.storeId inList requestStoreIds }
                            .orderBy(StoreWorkerRequests.requestedAtMillis, SortOrder.DESC)
                            .map { it.toStoreWorkerRequestDataModel() }
                    }

                    result?.let { call.genericResponse(HttpStatusCode.OK, payload = it, message = getResponse("53").message) }
                        ?: call.respond(UnauthorizedResponse())
                }

                post("/request") {
                    val userId = call.checkPrincipal() ?: return@post
                    val body = call.receive<WorkerEmploymentRequestCreateDataModel>()
                    val now = System.currentTimeMillis()
                    var failureMessage: List<LocalizedStringDataModel>? = null

                    val request = newSuspendedTransaction(Dispatchers.IO) {
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
                            .where { (StoreWorkerRequests.storeId eq storeId) and (StoreWorkerRequests.requesterUserId eq userId) and (StoreWorkerRequests.status inList listOf("pending", "invited")) }
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
                            it[StoreWorkerRequests.status] = "pending"
                            it[StoreWorkerRequests.requestedAtMillis] = now
                            it[StoreWorkerRequests.roleId] = WORKER_ROLE_STANDARD
                            it[StoreWorkerRequests.permissions] = STANDARD_STORE_PERMISSION_IDS
                            it[StoreWorkerRequests.workshiftPasswordHash] = null
                            it[StoreWorkerRequests.note] = null
                            it[StoreWorkerRequests.updatedAt] = Instant.now()
                        }

                        StoreWorkerRequests
                            .innerJoin(Stores, { StoreWorkerRequests.storeId }, { Stores.id })
                            .innerJoin(Users, { StoreWorkerRequests.requesterUserId }, { Users.id })
                            .selectAll()
                            .where { StoreWorkerRequests.id eq requestId }
                            .single()
                            .toStoreWorkerRequestDataModel()
                    }

                    request?.let { call.genericResponse(HttpStatusCode.Created, payload = it, message = getResponse("55").message) }
                        ?: call.genericResponseNoPayload(HttpStatusCode.Conflict, failureMessage ?: getResponse("3").message)
                }

                post("/invite") {
                    val userId = call.checkPrincipal() ?: return@post
                    val storeId = call.headerUuid("store_id") ?: return@post call.respond(UnauthorizedResponse())
                    val body = call.receive<WorkerStoreInviteCreateDataModel>()
                    val now = System.currentTimeMillis()
                    val role = body.roleId.takeIf { it == WORKER_ROLE_ADMIN || it == WORKER_ROLE_STANDARD } ?: WORKER_ROLE_STANDARD
                    val permissions = cleanPermissionIds(body.permissions).ifEmpty { defaultStorePermissionsForRole(role) }
                    var failureMessage: List<LocalizedStringDataModel>? = null

                    val request = newSuspendedTransaction(Dispatchers.IO) {
                        if (!isStoreOwnerInsideTransaction(userId, storeId) && !userHasStorePermissionInsideTransaction(userId, storeId, STORE_PERMISSION_WORKERS_MANAGE)) {
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
                                        (StoreWorkerRequests.status inList listOf("pending", "invited"))
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
                            it[StoreWorkerRequests.status] = "invited"
                            it[StoreWorkerRequests.requestedAtMillis] = now
                            it[StoreWorkerRequests.roleId] = role
                            it[StoreWorkerRequests.permissions] = permissions
                            it[StoreWorkerRequests.workshiftPasswordHash] = body.workerPassword.toWorkshiftPasswordHashOrNull()
                            it[StoreWorkerRequests.note] = body.note
                            it[StoreWorkerRequests.updatedAt] = Instant.now()
                        }

                        StoreWorkerRequests
                            .innerJoin(Stores, { StoreWorkerRequests.storeId }, { Stores.id })
                            .innerJoin(Users, { StoreWorkerRequests.requesterUserId }, { Users.id })
                            .selectAll()
                            .where { StoreWorkerRequests.id eq requestId }
                            .single()
                            .toStoreWorkerRequestDataModel()
                    }

                    request?.let { call.genericResponse(HttpStatusCode.Created, payload = it, message = getResponse("65").message) }
                        ?: call.genericResponseNoPayload(HttpStatusCode.Conflict, failureMessage ?: getResponse("3").message)
                }

                post("/invitations/accept") {
                    val userId = call.checkPrincipal() ?: return@post
                    val body = call.receive<WorkerStoreInvitationDecisionDataModel>()
                    val requestId = runCatching { UUID.fromString(body.requestId) }.getOrNull()
                        ?: return@post call.genericResponseNoPayload(HttpStatusCode.BadRequest, getResponse("13").message)
                    val now = System.currentTimeMillis()
                    var failureMessage: List<LocalizedStringDataModel>? = null

                    val worker = newSuspendedTransaction(Dispatchers.IO) {
                        val requestRow = StoreWorkerRequests
                            .selectAll()
                            .where {
                                (StoreWorkerRequests.id eq requestId) and
                                        (StoreWorkerRequests.requesterUserId eq userId) and
                                        (StoreWorkerRequests.direction eq WORKER_REQUEST_DIRECTION_STORE_TO_USER) and
                                        (StoreWorkerRequests.status eq "invited")
                            }
                            .singleOrNull()
                        if (requestRow == null) {
                            failureMessage = getResponse("13").message
                            return@newSuspendedTransaction null
                        }

                        val storeId = requestRow[StoreWorkerRequests.storeId]
                        val accepterUserId = requestRow[StoreWorkerRequests.invitedByUserId] ?: userId
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
                                it[StoreWorkerMemberships.workshiftPasswordHash] = requestRow[StoreWorkerRequests.workshiftPasswordHash]
                                it[StoreWorkerMemberships.requestedAtMillis] = requestRow[StoreWorkerRequests.requestedAtMillis]
                                it[StoreWorkerMemberships.acceptedAtMillis] = now
                                it[StoreWorkerMemberships.acceptedByUserId] = accepterUserId
                                it[StoreWorkerMemberships.isActive] = true
                            }
                        } else {
                            StoreWorkerMemberships.update({ StoreWorkerMemberships.id eq membershipId }) {
                                it[StoreWorkerMemberships.roleId] = requestRow[StoreWorkerRequests.roleId]
                                it[StoreWorkerMemberships.permissions] = requestRow[StoreWorkerRequests.permissions]
                                it[StoreWorkerMemberships.workshiftPasswordHash] = requestRow[StoreWorkerRequests.workshiftPasswordHash]
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
                            it[StoreWorkerRequests.status] = "accepted"
                            it[StoreWorkerRequests.decidedAtMillis] = now
                            it[StoreWorkerRequests.decidedByUserId] = userId
                            it[StoreWorkerRequests.note] = body.note
                            it[StoreWorkerRequests.updatedAt] = Instant.now()
                        }

                        StoreWorkerMemberships
                            .innerJoin(Stores, { StoreWorkerMemberships.storeId }, { Stores.id })
                            .innerJoin(Users, { StoreWorkerMemberships.userId }, { Users.id })
                            .selectAll()
                            .where { StoreWorkerMemberships.id eq membershipId }
                            .single()
                            .toStoreWorkerDataModel()
                    }

                    worker?.let { call.genericResponse(HttpStatusCode.OK, payload = it, message = getResponse("66").message) }
                        ?: call.genericResponseNoPayload(HttpStatusCode.Conflict, failureMessage ?: getResponse("3").message)
                }

                post("/invitations/decline") {
                    val userId = call.checkPrincipal() ?: return@post
                    val body = call.receive<WorkerStoreInvitationDecisionDataModel>()
                    val requestId = runCatching { UUID.fromString(body.requestId) }.getOrNull()
                        ?: return@post call.genericResponseNoPayload(HttpStatusCode.BadRequest, getResponse("13").message)
                    val now = System.currentTimeMillis()
                    var failureMessage: List<LocalizedStringDataModel>? = null

                    val request = newSuspendedTransaction(Dispatchers.IO) {
                        val updated = StoreWorkerRequests.update({
                            (StoreWorkerRequests.id eq requestId) and
                                    (StoreWorkerRequests.requesterUserId eq userId) and
                                    (StoreWorkerRequests.direction eq WORKER_REQUEST_DIRECTION_STORE_TO_USER) and
                                    (StoreWorkerRequests.status eq "invited")
                        }) {
                            it[StoreWorkerRequests.status] = "declined"
                            it[StoreWorkerRequests.decidedAtMillis] = now
                            it[StoreWorkerRequests.decidedByUserId] = userId
                            it[StoreWorkerRequests.note] = body.note
                            it[StoreWorkerRequests.updatedAt] = Instant.now()
                        }

                        if (updated <= 0) {
                            failureMessage = getResponse("13").message
                            return@newSuspendedTransaction null
                        }

                        StoreWorkerRequests
                            .innerJoin(Stores, { StoreWorkerRequests.storeId }, { Stores.id })
                            .innerJoin(Users, { StoreWorkerRequests.requesterUserId }, { Users.id })
                            .selectAll()
                            .where { StoreWorkerRequests.id eq requestId }
                            .single()
                            .toStoreWorkerRequestDataModel()
                    }

                    request?.let { call.genericResponse(HttpStatusCode.OK, payload = it, message = getResponse("67").message) }
                        ?: call.genericResponseNoPayload(HttpStatusCode.Conflict, failureMessage ?: getResponse("3").message)
                }

                post("/accept") {
                    val userId = call.checkPrincipal() ?: return@post
                    val storeId = call.headerUuid("store_id") ?: return@post call.respond(UnauthorizedResponse())
                    val body = call.receive<WorkerEmploymentDecisionRequestDataModel>()
                    val requestId = runCatching { UUID.fromString(body.requestId) }.getOrNull()
                        ?: return@post call.genericResponseNoPayload(HttpStatusCode.BadRequest, getResponse("13").message)
                    val now = System.currentTimeMillis()
                    val role = body.roleId.takeIf { it == WORKER_ROLE_ADMIN || it == WORKER_ROLE_STANDARD } ?: WORKER_ROLE_STANDARD
                    val permissions = cleanPermissionIds(body.permissions)
                    var failureMessage: List<LocalizedStringDataModel>? = null

                    val worker = newSuspendedTransaction(Dispatchers.IO) {
                        if (!isStoreOwnerInsideTransaction(userId, storeId) && !userHasStorePermissionInsideTransaction(userId, storeId, STORE_PERMISSION_WORKERS_MANAGE)) {
                            failureMessage = getResponse("59").message
                            return@newSuspendedTransaction null
                        }

                        val requestRow = StoreWorkerRequests
                            .selectAll()
                            .where { (StoreWorkerRequests.id eq requestId) and (StoreWorkerRequests.storeId eq storeId) and (StoreWorkerRequests.direction eq WORKER_REQUEST_DIRECTION_USER_TO_STORE) }
                            .singleOrNull()
                        if (requestRow == null) {
                            failureMessage = getResponse("13").message
                            return@newSuspendedTransaction null
                        }

                        val workerUserId = requestRow[StoreWorkerRequests.requesterUserId]
                        val existing = StoreWorkerMemberships
                            .selectAll()
                            .where { (StoreWorkerMemberships.storeId eq storeId) and (StoreWorkerMemberships.userId eq workerUserId) and (StoreWorkerMemberships.isActive eq true) }
                            .singleOrNull()

                        val membershipId = existing?.get(StoreWorkerMemberships.id) ?: UUID.randomUUID()

                        if (existing == null) {
                            StoreWorkerMemberships.insert {
                                it[StoreWorkerMemberships.id] = membershipId
                                it[StoreWorkerMemberships.storeId] = storeId
                                it[StoreWorkerMemberships.userId] = workerUserId
                                it[StoreWorkerMemberships.requestId] = requestId
                                it[StoreWorkerMemberships.roleId] = role
                                it[StoreWorkerMemberships.permissions] = permissions
                                it[StoreWorkerMemberships.workshiftPasswordHash] = body.workerPassword.toWorkshiftPasswordHashOrNull()
                                it[StoreWorkerMemberships.requestedAtMillis] = requestRow[StoreWorkerRequests.requestedAtMillis]
                                it[StoreWorkerMemberships.acceptedAtMillis] = now
                                it[StoreWorkerMemberships.acceptedByUserId] = userId
                                it[StoreWorkerMemberships.isActive] = true
                            }
                        } else {
                            StoreWorkerMemberships.update({ StoreWorkerMemberships.id eq membershipId }) {
                                it[StoreWorkerMemberships.roleId] = role
                                it[StoreWorkerMemberships.permissions] = permissions
                                body.workerPassword.toWorkshiftPasswordHashOrNull()?.let { hash -> it[StoreWorkerMemberships.workshiftPasswordHash] = hash }
                                it[StoreWorkerMemberships.acceptedAtMillis] = now
                                it[StoreWorkerMemberships.acceptedByUserId] = userId
                                it[StoreWorkerMemberships.isActive] = true
                                it[StoreWorkerMemberships.updatedAt] = Instant.now()
                            }
                        }

                        StoreUsers.insertIgnore {
                            it[StoreUsers.storeId] = storeId
                            it[StoreUsers.userId] = workerUserId
                        }

                        StoreWorkerRequests.update({ StoreWorkerRequests.id eq requestId }) {
                            it[StoreWorkerRequests.status] = "accepted"
                            it[StoreWorkerRequests.decidedAtMillis] = now
                            it[StoreWorkerRequests.decidedByUserId] = userId
                            it[StoreWorkerRequests.roleId] = role
                            it[StoreWorkerRequests.permissions] = permissions
                            body.workerPassword.toWorkshiftPasswordHashOrNull()?.let { hash -> it[StoreWorkerRequests.workshiftPasswordHash] = hash }
                            it[StoreWorkerRequests.note] = body.note
                            it[StoreWorkerRequests.updatedAt] = Instant.now()
                        }

                        StoreWorkerMemberships
                            .innerJoin(Stores, { StoreWorkerMemberships.storeId }, { Stores.id })
                            .innerJoin(Users, { StoreWorkerMemberships.userId }, { Users.id })
                            .selectAll()
                            .where { StoreWorkerMemberships.id eq membershipId }
                            .single()
                            .toStoreWorkerDataModel()
                    }

                    worker?.let { call.genericResponse(HttpStatusCode.OK, payload = it, message = getResponse("56").message) }
                        ?: call.genericResponseNoPayload(HttpStatusCode.Conflict, failureMessage ?: getResponse("3").message)
                }

                post("/decline") {
                    val userId = call.checkPrincipal() ?: return@post
                    val storeId = call.headerUuid("store_id") ?: return@post call.respond(UnauthorizedResponse())
                    val body = call.receive<WorkerEmploymentDecisionRequestDataModel>()
                    val requestId = runCatching { UUID.fromString(body.requestId) }.getOrNull()
                        ?: return@post call.genericResponseNoPayload(HttpStatusCode.BadRequest, getResponse("13").message)
                    val now = System.currentTimeMillis()
                    var failureMessage: List<LocalizedStringDataModel>? = null

                    val request = newSuspendedTransaction(Dispatchers.IO) {
                        if (!isStoreOwnerInsideTransaction(userId, storeId) && !userHasStorePermissionInsideTransaction(userId, storeId, STORE_PERMISSION_WORKERS_MANAGE)) {
                            failureMessage = getResponse("59").message
                            return@newSuspendedTransaction null
                        }

                        val updated = StoreWorkerRequests.update({ (StoreWorkerRequests.id eq requestId) and (StoreWorkerRequests.storeId eq storeId) and (StoreWorkerRequests.direction eq WORKER_REQUEST_DIRECTION_USER_TO_STORE) }) {
                            it[StoreWorkerRequests.status] = "declined"
                            it[StoreWorkerRequests.decidedAtMillis] = now
                            it[StoreWorkerRequests.decidedByUserId] = userId
                            it[StoreWorkerRequests.note] = body.note
                            it[StoreWorkerRequests.updatedAt] = Instant.now()
                        }

                        if (updated <= 0) {
                            failureMessage = getResponse("13").message
                            return@newSuspendedTransaction null
                        }

                        StoreWorkerRequests
                            .innerJoin(Stores, { StoreWorkerRequests.storeId }, { Stores.id })
                            .innerJoin(Users, { StoreWorkerRequests.requesterUserId }, { Users.id })
                            .selectAll()
                            .where { StoreWorkerRequests.id eq requestId }
                            .single()
                            .toStoreWorkerRequestDataModel()
                    }

                    request?.let { call.genericResponse(HttpStatusCode.OK, payload = it, message = getResponse("57").message) }
                        ?: call.genericResponseNoPayload(HttpStatusCode.Conflict, failureMessage ?: getResponse("3").message)
                }

                post("/updatePermissions") {
                    val userId = call.checkPrincipal() ?: return@post
                    val storeId = call.headerUuid("store_id") ?: return@post call.respond(UnauthorizedResponse())
                    val body = call.receive<WorkerPermissionsUpdateRequestDataModel>()
                    val workerId = runCatching { UUID.fromString(body.workerId) }.getOrNull()
                        ?: return@post call.genericResponseNoPayload(HttpStatusCode.BadRequest, getResponse("13").message)
                    val role = body.roleId.takeIf { it == WORKER_ROLE_ADMIN || it == WORKER_ROLE_STANDARD } ?: WORKER_ROLE_STANDARD
                    val permissions = cleanPermissionIds(body.permissions)
                    var failureMessage: List<LocalizedStringDataModel>? = null

                    val worker = newSuspendedTransaction(Dispatchers.IO) {
                        if (!isStoreOwnerInsideTransaction(userId, storeId) && !userHasStorePermissionInsideTransaction(userId, storeId, STORE_PERMISSION_WORKERS_MANAGE)) {
                            failureMessage = getResponse("59").message
                            return@newSuspendedTransaction null
                        }

                        val updated = StoreWorkerMemberships.update({ (StoreWorkerMemberships.id eq workerId) and (StoreWorkerMemberships.storeId eq storeId) }) {
                            it[StoreWorkerMemberships.roleId] = role
                            it[StoreWorkerMemberships.permissions] = permissions
                            body.workerPassword.toWorkshiftPasswordHashOrNull()?.let { hash -> it[StoreWorkerMemberships.workshiftPasswordHash] = hash }
                            it[StoreWorkerMemberships.updatedAt] = Instant.now()
                        }

                        if (updated <= 0) {
                            failureMessage = getResponse("13").message
                            return@newSuspendedTransaction null
                        }

                        StoreWorkerMemberships
                            .innerJoin(Stores, { StoreWorkerMemberships.storeId }, { Stores.id })
                            .innerJoin(Users, { StoreWorkerMemberships.userId }, { Users.id })
                            .selectAll()
                            .where { StoreWorkerMemberships.id eq workerId }
                            .single()
                            .toStoreWorkerDataModel()
                    }

                    worker?.let { call.genericResponse(HttpStatusCode.OK, payload = it, message = getResponse("58").message) }
                        ?: call.genericResponseNoPayload(HttpStatusCode.Conflict, failureMessage ?: getResponse("3").message)
                }
            }
        }


        route("/workshifts") {
            authenticate("auth-jwt") {
                get("/current") {
                    val userId = call.checkPrincipal() ?: return@get
                    val storeId = call.headerUuid("store_id") ?: return@get call.respond(UnauthorizedResponse())

                    val workshift = newSuspendedTransaction(Dispatchers.IO) {
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

                    workshift?.let { call.genericResponse(HttpStatusCode.OK, it, getResponse("86").message) }
                        ?: call.genericResponseNoPayload(HttpStatusCode.NotFound, getResponse("13").message)
                }

                post("/start") {
                    val userId = call.checkPrincipal() ?: return@post
                    val storeId = call.headerUuid("store_id") ?: return@post call.respond(UnauthorizedResponse())
                    val body = call.receive<WorkshiftStartRequestDataModel>()
                    val now = System.currentTimeMillis()
                    var failureMessage: List<LocalizedStringDataModel>? = null

                    val workshift = newSuspendedTransaction(Dispatchers.IO) {
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

                        if (!Pw.verify(body.password.toCharArray(), passwordHash)) {
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

                        Workshifts
                            .innerJoin(Stores, { Workshifts.storeId }, { Stores.id })
                            .innerJoin(Users, { Workshifts.workerUserId }, { Users.id })
                            .selectAll()
                            .where { Workshifts.id eq workshiftId }
                            .single()
                            .toWorkshiftDataModel()
                    }

                    workshift?.let { call.genericResponse(HttpStatusCode.Created, it, getResponse("87").message) }
                        ?: call.genericResponseNoPayload(HttpStatusCode.Conflict, failureMessage ?: getResponse("3").message)
                }

                post("/end") {
                    val userId = call.checkPrincipal() ?: return@post
                    val storeId = call.headerUuid("store_id") ?: return@post call.respond(UnauthorizedResponse())
                    val now = System.currentTimeMillis()
                    var failureMessage: List<LocalizedStringDataModel>? = null

                    val workshift = newSuspendedTransaction(Dispatchers.IO) {
                        val row = Workshifts
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

                        if (row == null) {
                            failureMessage = getResponse("13").message
                            return@newSuspendedTransaction null
                        }

                        val workshiftId = row[Workshifts.id]
                        Workshifts.update({ Workshifts.id eq workshiftId }) {
                            it[Workshifts.endedAtMillis] = now
                            it[Workshifts.endedByUserId] = userId
                            it[Workshifts.isActive] = false
                            it[Workshifts.updatedAt] = Instant.now()
                        }

                        Workshifts
                            .innerJoin(Stores, { Workshifts.storeId }, { Stores.id })
                            .innerJoin(Users, { Workshifts.workerUserId }, { Users.id })
                            .selectAll()
                            .where { Workshifts.id eq workshiftId }
                            .single()
                            .toWorkshiftDataModel()
                    }

                    workshift?.let { call.genericResponse(HttpStatusCode.OK, it, getResponse("88").message) }
                        ?: call.genericResponseNoPayload(HttpStatusCode.Conflict, failureMessage ?: getResponse("3").message)
                }
            }
        }

        route("/transactions") {
            authenticate("auth-jwt") {
                get("/get") {
                    val userId = call.checkPrincipal() ?: return@get
                    val storeId = runCatching {
                        UUID.fromString(call.request.header("store_id"))
                    }.getOrNull() ?: return@get call.respond(UnauthorizedResponse())

                    val transactions = newSuspendedTransaction(Dispatchers.IO) {
                        val hasStoreAccess = StoreUsers
                            .selectAll()
                            .where { (StoreUsers.userId eq userId) and (StoreUsers.storeId eq storeId) }
                            .empty()
                            .not()

                        if (!hasStoreAccess)
                            return@newSuspendedTransaction null

                        Transactions
                            .selectAll()
                            .where { Transactions.storeId eq storeId }
                            .orderBy(Transactions.timeMillis, SortOrder.DESC)
                            .map {
                                TransactionDataModel(
                                    id = it[Transactions.id].toString(),
                                    workshiftId = it[Transactions.workshiftId],
                                    type = it[Transactions.type],
                                    storeId = it[Transactions.storeId].toString(),
                                    goodsInTransaction = it[Transactions.goodsInTransaction],
                                    paidCash = it[Transactions.paidCash],
                                    paidCard = it[Transactions.paidCard],
                                    cardPaymentOptionId = it[Transactions.cardPaymentOptionId],
                                    debtor = it[Transactions.debtor]?.let { raw ->
                                        jsonBase.decodeFromString<DebtorDataModel>(raw)
                                    },
                                    timeMillis = it[Transactions.timeMillis]
                                )
                            }
                    }

                    transactions?.let {
                        call.genericListResponse(
                            status = HttpStatusCode.OK,
                            payload = it
                        )
                    } ?: call.respond(UnauthorizedResponse())
                }

                post("/complete") {
                    val userId = call.checkPrincipal() ?: return@post
                    val body = call.receive<TransactionDataModel>()

                    val storeId = runCatching {
                        UUID.fromString(body.storeId)
                    }.getOrNull() ?: return@post call.respond(UnauthorizedResponse())

                    var transactionFailureMessage: List<LocalizedStringDataModel>? = null

                    val completed = newSuspendedTransaction(Dispatchers.IO) {
                        val hasStoreAccess = StoreUsers
                            .selectAll()
                            .where { (StoreUsers.userId eq userId) and (StoreUsers.storeId eq storeId) }
                            .empty()
                            .not()

                        if (!hasStoreAccess)
                            return@newSuspendedTransaction null

                        val requiredPermission = requiredPermissionForTransactionType(body.type)
                        if (requiredPermission != null && !userHasStorePermissionInsideTransaction(userId, storeId, requiredPermission)) {
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
                        }

                        applyCashRegisterTransactionEventInsideTransaction(
                            storeId = storeId,
                            userId = userId,
                            transactionId = id,
                            transactionType = transactionToSave.type,
                            cashAmount = transactionToSave.paidCash,
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
                        } ?: call.respond(UnauthorizedResponse())
                    }
                }
            }
        }
    }
}