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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
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

private fun userHasStoreAccessInsideTransaction(
    userId: UUID,
    storeId: UUID
): Boolean {
    return StoreUsers
        .selectAll()
        .where { (StoreUsers.userId eq userId) and (StoreUsers.storeId eq storeId) }
        .empty()
        .not()
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

        createdAtMillis = this[StockItems.createdAtMillis],
        updatedAtMillis = this[StockItems.updatedAtMillis],
        isActive = this[StockItems.isActive]
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
            it[id] = UUID.randomUUID()
            it[SupplierGoodsPrices.userId] = userId
            it[SupplierGoodsPrices.storeId] = storeId
            it[SupplierGoodsPrices.supplierId] = supplierId
            it[SupplierGoodsPrices.goodsItemId] = goodsItemId
            it[SupplierGoodsPrices.supplyPrice] = supplyPrice
            it[SupplierGoodsPrices.minOrderQuantity] = minOrderQuantity
            it[SupplierGoodsPrices.packageQuantity] = packageQuantity
            it[SupplierGoodsPrices.supplierBarcode] = supplierBarcode?.takeIf { value -> value.isNotBlank() }
            it[SupplierGoodsPrices.supplierGoodsName] = supplierGoodsName?.takeIf { value -> value.isNotBlank() }
            it[createdAtMillis] = now
            it[updatedAtMillis] = now
            it[lastUsedAtMillis] = now
            it[isActive] = true
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
            it[updatedAtMillis] = now
            it[lastUsedAtMillis] = now
            it[isActive] = true
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
    preferredPricePerUnit: Double
): Boolean {
    val goodsItemId = itemRow[StockItems.id]
    val activeShelfBatchId = itemRow[StockItems.activeShelfBatchId]
    val quantityToAdd = addedQuantity.coerceAtLeast(0.0)

    if (quantityToAdd <= 0.0) return true

    val targetBatch = activeStockBatchesForGoodsItemInsideTransaction(
        storeId = storeId,
        goodsItemId = goodsItemId,
        activeShelfBatchId = activeShelfBatchId
    ).firstOrNull { row ->
        row[StockBatchesV2.status] != StockBatchStatusDataModel.Deleted.name &&
                row[StockBatchesV2.status] != StockBatchStatusDataModel.WrittenOff.name
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
        it[StockBatchesV2.supplierId] = null
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
                preferredPricePerUnit = line.pricePerUnit
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
        SchemaUtils.createMissingTablesAndColumns(StockItems, StockBatchesV2, Debtors, Notifications)
    }

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
                        call.genericResponse(
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

                            it[StockItems.createdAtMillis] = now
                            it[StockItems.updatedAtMillis] = now
                            it[StockItems.isActive] = true
                        }

                        body.copy(
                            id = id.toString(),
                            userId = userId.toString(),
                            storeId = storeId.toString(),
                            barcodes = cleanBarcodes,
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
                            it[StockItems.updatedAtMillis] = now
                            it[StockItems.isActive] = body.isActive
                        }

                        if (affected <= 0)
                            return@newSuspendedTransaction null

                        body.copy(
                            userId = userId.toString(),
                            storeId = storeId.toString(),
                            barcodes = cleanBarcodes,
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
                            it[isActive] = false
                            it[updatedAtMillis] = now
                        }

                        if (affected <= 0)
                            return@newSuspendedTransaction null

                        StockBatchesV2.update({
                            (StockBatchesV2.goodsItemId eq id) and
                                    (StockBatchesV2.storeId eq storeId) and
                                    (StockBatchesV2.userId eq userId)
                        }) {
                            it[isActive] = false
                            it[updatedAtMillis] = now
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
                        call.genericResponse(
                            status = HttpStatusCode.OK,
                            payload = it
                        )
                    } ?: call.respond(UnauthorizedResponse())
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
                                    it[activeShelfBatchId] = id
                                    it[updatedAtMillis] = now
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
                                it[isActive] = false
                                it[updatedAtMillis] = now
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
                            it[activeShelfBatchId] = batchId
                            it[updatedAtMillis] = now
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
                                it[isActive] = false
                                it[updatedAtMillis] = now
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

                put("/active") {
                    val userId = call.checkPrincipal() ?: return@put
                    val body = call.receive<String>().trim()
                    val storeId = runCatching { UUID.fromString(body) }.getOrNull()
                        ?: return@put call.respond(UnauthorizedResponse())

                    val updated = newSuspendedTransaction(Dispatchers.IO) {
                        val hasAccess = StoreUsers
                            .selectAll()
                            .where { (StoreUsers.userId eq userId) and (StoreUsers.storeId eq storeId) }
                            .empty()
                            .not()

                        if (!hasAccess)
                            return@newSuspendedTransaction false

                        Users.update({ Users.id eq userId }) {
                            it[Users.activeStoreId] = storeId
                        } > 0
                    }

                    if (updated) {
                        call.genericResponseNoPayload(
                            HttpStatusCode.OK,
                            message = simpleMessage(
                                main = "Active store saved",
                                ru = "Активный магазин сохранён",
                                kk = "Белсенді дүкен сақталды"
                            )
                        )
                    } else {
                        call.respond(UnauthorizedResponse())
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
                            .where { (Transactions.userId eq userId) and (Transactions.storeId eq storeId) }
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
                        call.genericResponse(
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