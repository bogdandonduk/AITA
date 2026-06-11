// THIS IS CommonMain.kt - in shared commonMain module of kmp compose app
@file:OptIn(DelicateCoroutinesApi::class)
package kz.aita

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import app.cash.sqldelight.db.SqlDriver
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.plugins.auth.*
import io.ktor.client.plugins.auth.providers.*
import io.ktor.client.plugins.cache.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.core.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlin.concurrent.Volatile
import kotlin.random.Random

@kotlinx.serialization.Serializable
data class MoneyDataModel(
    val amount: String = "0",
    val currencyCode: String = "KZT"
) {
    val amountDouble: Double
        get() = amount.replace(",", ".").toDoubleOrNull() ?: 0.0
}

@kotlinx.serialization.Serializable
data class ExpirationPeriodDataModel(
    val amount: Int = 0,
    val unit: String = "days" // days, weeks, months, years
) {
    val isUsable: Boolean
        get() = amount > 0 && unit in setOf("days", "weeks", "months", "years")
}

@kotlinx.serialization.Serializable
data class BatchDiscountDataModel(
    val id: String = "",
    val title: List<LocalizedStringDataModel> = emptyList(),
    val mode: String = "percent", // "percent" or "fixed"
    val value: String = "0",
    val startsAtMillis: Long? = null,
    val endsAtMillis: Long? = null,
    val note: String? = null,
    val isActive: Boolean = true
)

const val STOCK_PROMOTION_TYPE_DISCOUNT = "discount"
const val STOCK_PROMOTION_TYPE_SPECIAL_PRICE = "special_price"
const val STOCK_PROMOTION_TYPE_RESTRICTION = "restriction"
const val STOCK_PROMOTION_MODE_PERCENT = "percent"
const val STOCK_PROMOTION_MODE_FIXED = "fixed"
const val STOCK_PROMOTION_MODE_PRICE = "price"

const val USER_ROLE_STORE_OWNER = "store_owner"
const val USER_ROLE_STORE_WORKER = "store_worker"
const val USER_ROLE_SUPPLIER = "supplier"
const val USER_ROLE_MANUFACTURER = "manufacturer"
const val USER_ROLE_BUYER = "buyer"

@kotlinx.serialization.Serializable
data class StockPromotionDataModel(
    val id: String = "",
    val title: List<LocalizedStringDataModel> = emptyList(),
    val type: String = STOCK_PROMOTION_TYPE_DISCOUNT, // discount, special_price, restriction
    val mode: String = STOCK_PROMOTION_MODE_PERCENT, // percent, fixed, price
    val value: String = "0",
    val transactionTypeIndices: List<Int> = listOf(0), // 0 sale, 1 return, 2 supply; empty = all
    val minQuantity: Double? = null,
    val startsAtMillis: Long? = null,
    val endsAtMillis: Long? = null,
    val note: String? = null,
    val noteLocalized: List<LocalizedStringDataModel> = emptyList(),
    val isActive: Boolean = true
)

@kotlinx.serialization.Serializable
data class PromotedPriceDataModel(
    val originalPrice: PriceDataModel,
    val finalPrice: PriceDataModel,
    val promotion: StockPromotionDataModel? = null
) {
    val hasPriceChange: Boolean
        get() = promotion != null && finalPrice.price.toMoneyDouble() + 0.000001 < originalPrice.price.toMoneyDouble()
}

const val AITA_CURRENCY_PREFIX = "AITA"
const val PAYMENT_PROVIDER_KASPI_INVOICE = "kaspi_invoice"
const val PAYMENT_PROVIDER_MANUAL_DEVELOPMENT = "manual_development"
const val PAYMENT_STATUS_CREATED = "created"
const val PAYMENT_STATUS_WAITING = "waiting"
const val PAYMENT_STATUS_PAID = "paid"
const val PAYMENT_STATUS_CANCELLED = "cancelled"
const val PAYMENT_STATUS_EXPIRED = "expired"
const val WALLET_LEDGER_TOP_UP = "top_up"
const val WALLET_LEDGER_SUBSCRIPTION_CHARGE = "subscription_charge"
const val SUBSCRIPTION_STATUS_INACTIVE = "inactive"
const val SUBSCRIPTION_STATUS_ACTIVE = "active"
const val SUBSCRIPTION_STATUS_PAST_DUE = "past_due"
const val SUBSCRIPTION_STATUS_CANCELLED = "cancelled"
const val SUBSCRIPTION_PERIOD_MONTH = "month"
const val SUBSCRIPTION_PERIOD_YEAR = "year"

const val DEFAULT_APP_LANGUAGE = "ru"
const val DEFAULT_APP_THEME_ID = 0L
const val DEFAULT_APP_SIZE_MODE_ID = 0L

fun normalizeAppLanguagePreference(language: String?): String {
    val value = language?.trim()?.lowercase().orEmpty()
    return when (value) {
        "system", "en", "ru", "kk" -> value
        else -> DEFAULT_APP_LANGUAGE
    }
}

fun normalizeAppThemePreference(themeId: Long?): Long {
    return if (themeId == 1L) 1L else DEFAULT_APP_THEME_ID
}

fun normalizeAppSizeModePreference(sizeModeId: Long?): Long {
    return if (sizeModeId == 1L) 1L else DEFAULT_APP_SIZE_MODE_ID
}

@kotlinx.serialization.Serializable
data class PagingRequestDataModel(
    val page: Int = 0,
    val pageSize: Int = 40,
    val query: String = "",
    val sortBy: String = "",
    val sortDirection: String = "asc"
) {
    fun normalized(maxPageSize: Int = 200): PagingRequestDataModel = copy(
        page = page.coerceAtLeast(0),
        pageSize = pageSize.coerceIn(1, maxPageSize),
        query = query.trim(),
        sortDirection = if (sortDirection.equals("desc", true)) "desc" else "asc"
    )
}

@kotlinx.serialization.Serializable
data class PagedResponseDataModel<T>(
    val items: List<T>,
    val page: Int,
    val pageSize: Int,
    val totalItems: Int,
    val totalPages: Int,
    val hasPreviousPage: Boolean,
    val hasNextPage: Boolean
)

fun <T> List<T>.toPagedResponse(request: PagingRequestDataModel): PagedResponseDataModel<T> {
    val normalized = request.normalized()
    val total = size
    val totalPages = if (total == 0) 0 else ((total - 1) / normalized.pageSize) + 1
    val page = normalized.page.coerceIn(0, (totalPages - 1).coerceAtLeast(0))
    val start = (page * normalized.pageSize).coerceAtMost(total)
    val end = (start + normalized.pageSize).coerceAtMost(total)
    return PagedResponseDataModel(
        items = subList(start, end),
        page = page,
        pageSize = normalized.pageSize,
        totalItems = total,
        totalPages = totalPages,
        hasPreviousPage = page > 0,
        hasNextPage = page + 1 < totalPages
    )
}

@kotlinx.serialization.Serializable
data class PaymentProviderConfigDataModel(
    val id: String,
    val name: List<LocalizedStringDataModel>,
    val countryLocales: List<String> = emptyList(),
    val currencyCodes: List<String> = emptyList(),
    val enabled: Boolean = true,
    val sandbox: Boolean = true,
    val apiKeyEnvironmentVariable: String = "",
    val webhookSecretEnvironmentVariable: String = "",
    val description: List<LocalizedStringDataModel> = emptyList()
)

fun defaultPaymentProviders(): List<PaymentProviderConfigDataModel> = listOf(
    PaymentProviderConfigDataModel(
        id = PAYMENT_PROVIDER_KASPI_INVOICE,
        name = listOf(
            LocalizedStringDataModel("main", "Kaspi invoice"),
            LocalizedStringDataModel("en", "Kaspi invoice"),
            LocalizedStringDataModel("ru", "Счёт Kaspi"),
            LocalizedStringDataModel("kk", "Kaspi шоты")
        ),
        countryLocales = listOf("kz"),
        currencyCodes = listOf("KZT"),
        enabled = false,
        sandbox = true,
        apiKeyEnvironmentVariable = "AITA_KASPI_API_KEY",
        webhookSecretEnvironmentVariable = "AITA_KASPI_WEBHOOK_SECRET",
        description = listOf(
            LocalizedStringDataModel("main", "Prepared integration placeholder. Real API call is disabled until merchant credentials are connected."),
            LocalizedStringDataModel("ru", "Заготовка интеграции. Реальный вызов API отключён до подключения данных мерчанта."),
            LocalizedStringDataModel("kk", "Интеграция дайындығы. Мерчант деректері қосылғанша нақты API шақыруы өшірулі.")
        )
    ),
    PaymentProviderConfigDataModel(
        id = PAYMENT_PROVIDER_MANUAL_DEVELOPMENT,
        name = listOf(
            LocalizedStringDataModel("main", "Manual development top-up"),
            LocalizedStringDataModel("en", "Manual development top-up"),
            LocalizedStringDataModel("ru", "Тестовое ручное пополнение"),
            LocalizedStringDataModel("kk", "Қолмен тест толтыру")
        ),
        enabled = true,
        sandbox = true
    )
)

@kotlinx.serialization.Serializable
data class UserWalletDataModel(
    val id: String = "",
    val userId: String = "",
    val currencyCode: String = "KZT",
    val aitaCurrencyCode: String = "AITA KZT",
    val balanceMinor: Long = 0L,
    val reservedMinor: Long = 0L,
    val updatedAtMillis: Long = 0L
) {
    val balance: Double
        get() = balanceMinor / 100.0
    val reserved: Double
        get() = reservedMinor / 100.0
    val available: Double
        get() = (balanceMinor - reservedMinor) / 100.0
}

@kotlinx.serialization.Serializable
data class WalletLedgerEntryDataModel(
    val id: String = "",
    val userId: String = "",
    val walletId: String = "",
    val type: String = "",
    val amountMinor: Long = 0L,
    val balanceBeforeMinor: Long = 0L,
    val balanceAfterMinor: Long = 0L,
    val currencyCode: String = "KZT",
    val referenceType: String = "",
    val referenceId: String = "",
    val note: String = "",
    val createdAtMillis: Long = 0L
) {
    val amount: Double get() = amountMinor / 100.0
    val balanceBefore: Double get() = balanceBeforeMinor / 100.0
    val balanceAfter: Double get() = balanceAfterMinor / 100.0
}

@kotlinx.serialization.Serializable
data class TopUpCreateRequestDataModel(
    val amount: Double,
    val currencyCode: String,
    val providerId: String = PAYMENT_PROVIDER_KASPI_INVOICE,
    val returnUrl: String = "",
    val comment: String = ""
)

@kotlinx.serialization.Serializable
data class TopUpConfirmDevelopmentRequestDataModel(
    val paymentIntentId: String
)

@kotlinx.serialization.Serializable
data class TopUpPaymentIntentDataModel(
    val id: String = "",
    val userId: String = "",
    val providerId: String = "",
    val amountMinor: Long = 0L,
    val currencyCode: String = "KZT",
    val aitaCurrencyCode: String = "AITA KZT",
    val status: String = PAYMENT_STATUS_CREATED,
    val providerInvoiceId: String = "",
    val paymentUrl: String = "",
    val qrPayload: String = "",
    val createdAtMillis: Long = 0L,
    val expiresAtMillis: Long? = null,
    val paidAtMillis: Long? = null,
    val metadata: Map<String, String> = emptyMap()
) {
    val amount: Double get() = amountMinor / 100.0
}

@kotlinx.serialization.Serializable
data class StoreSubscriptionPlanDataModel(
    val id: String,
    val name: List<LocalizedStringDataModel>,
    val description: List<LocalizedStringDataModel> = emptyList(),
    val priceMinor: Long,
    val currencyCode: String = "KZT",
    val periodUnit: String = SUBSCRIPTION_PERIOD_MONTH,
    val periodCount: Int = 1,
    val maxBranches: Int = 1,
    val maxWorkers: Int = 1,
    val maxStockItems: Int = 1000,
    val isActive: Boolean = true
) {
    val price: Double get() = priceMinor / 100.0
}

fun defaultStoreSubscriptionPlans(): List<StoreSubscriptionPlanDataModel> = listOf(
    StoreSubscriptionPlanDataModel(
        id = "standard_monthly_kzt",
        name = listOf(
            LocalizedStringDataModel("main", "Standard"),
            LocalizedStringDataModel("en", "Standard"),
            LocalizedStringDataModel("ru", "Стандартный"),
            LocalizedStringDataModel("kk", "Стандартты")
        ),
        description = listOf(
            LocalizedStringDataModel("main", "3 branches, 21 workers, 1000 stock items"),
            LocalizedStringDataModel("en", "3 branches, 21 workers, 1000 stock items"),
            LocalizedStringDataModel("ru", "3 филиала, 21 сотрудник, 1000 товаров на складе"),
            LocalizedStringDataModel("kk", "3 филиал, 21 қызметкер, қоймада 1000 тауар")
        ),
        priceMinor = 799000L,
        currencyCode = "KZT",
        periodUnit = SUBSCRIPTION_PERIOD_MONTH,
        periodCount = 1,
        maxBranches = 3,
        maxWorkers = 21,
        maxStockItems = 1000,
        isActive = true
    )
)

@kotlinx.serialization.Serializable
data class StoreSubscriptionStateDataModel(
    val id: String = "",
    val storeId: String = "",
    val ownerUserId: String = "",
    val planId: String = "",
    val status: String = SUBSCRIPTION_STATUS_INACTIVE,
    val autoRenew: Boolean = false,
    val startedAtMillis: Long? = null,
    val currentPeriodStartMillis: Long? = null,
    val currentPeriodEndMillis: Long? = null,
    val nextChargeAtMillis: Long? = null,
    val cancelledAtMillis: Long? = null,
    val pastDueSinceMillis: Long? = null,
    val updatedAtMillis: Long = 0L
)

@kotlinx.serialization.Serializable
data class StoreSubscriptionChargeDataModel(
    val id: String = "",
    val storeId: String = "",
    val userId: String = "",
    val planId: String = "",
    val amountMinor: Long = 0L,
    val currencyCode: String = "KZT",
    val periodStartMillis: Long = 0L,
    val periodEndMillis: Long = 0L,
    val status: String = "created",
    val walletLedgerEntryId: String = "",
    val createdAtMillis: Long = 0L,
    val note: String = ""
) {
    val amount: Double get() = amountMinor / 100.0
}

@kotlinx.serialization.Serializable
data class StoreSubscriptionUpdateRequestDataModel(
    val storeId: String,
    val planId: String,
    val autoRenew: Boolean = true,
    val activateNow: Boolean = true
)

@kotlinx.serialization.Serializable
data class SubscriptionDashboardDataModel(
    val subscription: StoreSubscriptionStateDataModel,
    val charges: List<StoreSubscriptionChargeDataModel>,
    val plans: List<StoreSubscriptionPlanDataModel>
)

@kotlinx.serialization.Serializable
data class UserFinanceDashboardDataModel(
    val wallet: UserWalletDataModel,
    val ledger: List<WalletLedgerEntryDataModel> = emptyList(),
    val paymentIntents: List<TopUpPaymentIntentDataModel> = emptyList(),
    val paymentProviders: List<PaymentProviderConfigDataModel> = emptyList(),
    val subscriptionPlans: List<StoreSubscriptionPlanDataModel> = emptyList()
)

fun Double.toMinorCurrencyUnits(): Long = kotlin.math.round(this * 100.0).toLong()
fun Long.fromMinorCurrencyUnits(): Double = this / 100.0
fun String.aitaCurrencyCode(): String = "$AITA_CURRENCY_PREFIX ${uppercase()}"

@kotlinx.serialization.Serializable
enum class StockBatchStatusDataModel {
    Ordered,
    Delivered,
    OnShelf,
    Reserved,
    InTransit,
    SoldOut,
    WrittenOff,
    Deleted
}

@kotlinx.serialization.Serializable
enum class StockBatchMovementStatusDataModel {
    PendingAcceptance,
    Accepted,
    Declined,
    Cancelled
}

@kotlinx.serialization.Serializable
enum class SupplierOrderStatusDataModel {
    Draft,
    Sent,
    SeenBySupplier,
    Confirmed,
    Packed,
    InDelivery,
    PartiallyDelivered,
    Delivered,
    IssueReported,
    Cancelled
}

expect fun getCurrentTimeMillis(): Long

val transactionsState = MutableDataStateFlow<List<TransactionDataModel>>(GlobalScope)


val debtorsState = MutableDataStateFlow<List<DebtorDataModel>>(GlobalScope)

val userFinanceDashboardState = MutableDataStateFlow<UserFinanceDashboardDataModel>(GlobalScope)
val userWalletState = MutableDataStateFlow<UserWalletDataModel>(GlobalScope)
val userWalletLedgerState = MutableDataStateFlow<List<WalletLedgerEntryDataModel>>(GlobalScope)
val paymentIntentsState = MutableDataStateFlow<List<TopUpPaymentIntentDataModel>>(GlobalScope)
val subscriptionPlansState = MutableDataStateFlow<List<StoreSubscriptionPlanDataModel>>(GlobalScope)
val activeStoreSubscriptionState = MutableDataStateFlow<StoreSubscriptionStateDataModel>(GlobalScope)
val activeStoreSubscriptionChargesState = MutableDataStateFlow<List<StoreSubscriptionChargeDataModel>>(GlobalScope)

private val getUserFinanceDashboardMutex = Mutex()
private val createTopUpPaymentMutex = Mutex()
private val confirmDevelopmentTopUpMutex = Mutex()
private val getSubscriptionPlansMutex = Mutex()
private val getStoreSubscriptionMutex = Mutex()
private val updateStoreSubscriptionMutex = Mutex()

private val getDebtorsMutex = Mutex()
private val addDebtorMutex = Mutex()
private val updateDebtorMutex = Mutex()
private val deleteDebtorMutex = Mutex()
private val payDebtorDebtMutex = Mutex()

private fun List<DebtorDataModel>.upsertDebtor(debtor: DebtorDataModel): List<DebtorDataModel> {
    val index = indexOfFirst { it.id == debtor.id }

    return if (index == -1) {
        this + debtor
    } else {
        toMutableList().also { it[index] = debtor }
    }
}

fun getDebtors(
    storeId: String,
    onCompleted: ((DataState<List<DebtorDataModel>>) -> Unit)? = null
) {
    if (!getDebtorsMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            getDebtorsMutex.withLock {
                val response = networkRequest<List<DebtorDataModel>, Unit>(
                    method = HttpMethod.Get,
                    endpointUrl = globalAppConfigurationState.payloadValue.getDebtorsPath.first,
                    headers = mapOf("store_id" to storeId)
                )

                if (response.negative || response.payload == null) {
                    postInAppNotification(response.message, NotificationType.Negative)
                    onCompleted?.invoke(DataState.Empty(response.message))
                } else {
                    debtorsState.emit(DataState.Success(response.payload, response.message))
                    onCompleted?.invoke(DataState.Success(response.payload, response.message))
                }
            }
        }
}

fun addDebtor(
    storeId: String,
    debtor: DebtorDataModel,
    onCompleted: ((DataState<DebtorDataModel>) -> Unit)? = null
) {
    if (!addDebtorMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            addDebtorMutex.withLock {
                val response = networkRequest<DebtorDataModel, DebtorDataModel>(
                    method = HttpMethod.Post,
                    endpointUrl = globalAppConfigurationState.payloadValue.addDebtorPath.first,
                    headers = mapOf("store_id" to storeId),
                    body = debtor
                )

                if (response.negative || response.payload == null) {
                    postInAppNotification(response.message, NotificationType.Negative)
                    onCompleted?.invoke(DataState.Empty(response.message))
                } else {
                    debtorsState.emit(
                        DataState.Success(
                            debtorsState.payloadValue.orEmpty().upsertDebtor(response.payload),
                            response.message
                        )
                    )
                    postInAppNotification(response.message, NotificationType.Positive)
                    onCompleted?.invoke(DataState.Success(response.payload, response.message))
                }
            }
        }
}

fun updateDebtor(
    storeId: String,
    debtor: DebtorDataModel,
    onCompleted: ((DataState<DebtorDataModel>) -> Unit)? = null
) {
    if (!updateDebtorMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            updateDebtorMutex.withLock {
                val response = networkRequest<DebtorDataModel, DebtorDataModel>(
                    method = HttpMethod.Put,
                    endpointUrl = globalAppConfigurationState.payloadValue.updateDebtorPath.first,
                    headers = mapOf("store_id" to storeId),
                    body = debtor
                )

                if (response.negative || response.payload == null) {
                    postInAppNotification(response.message, NotificationType.Negative)
                    onCompleted?.invoke(DataState.Empty(response.message))
                } else {
                    debtorsState.emit(
                        DataState.Success(
                            debtorsState.payloadValue.orEmpty().upsertDebtor(response.payload),
                            response.message
                        )
                    )
                    postInAppNotification(response.message, NotificationType.Positive)
                    onCompleted?.invoke(DataState.Success(response.payload, response.message))
                }
            }
        }
}

fun deleteDebtor(
    storeId: String,
    debtorId: String,
    onCompleted: ((DataState<String>) -> Unit)? = null
) {
    if (!deleteDebtorMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            deleteDebtorMutex.withLock {
                val response = networkRequest<String, String>(
                    method = HttpMethod.Delete,
                    endpointUrl = globalAppConfigurationState.payloadValue.deleteDebtorPath.first,
                    headers = mapOf("store_id" to storeId),
                    body = debtorId
                )

                if (response.negative || response.payload == null) {
                    postInAppNotification(response.message, NotificationType.Negative)
                    onCompleted?.invoke(DataState.Empty(response.message))
                } else {
                    debtorsState.emit(
                        DataState.Success(
                            debtorsState.payloadValue.orEmpty().filterNot { it.id == response.payload },
                            response.message
                        )
                    )
                    postInAppNotification(response.message, NotificationType.Positive)
                    onCompleted?.invoke(DataState.Success(response.payload, response.message))
                }
            }
        }
}

fun payDebtorDebt(
    request: DebtPaymentRequestDataModel,
    onCompleted: ((DataState<DebtorDataModel>) -> Unit)? = null
) {
    if (!payDebtorDebtMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            payDebtorDebtMutex.withLock {
                val response = networkRequest<DebtorDataModel, DebtPaymentRequestDataModel>(
                    method = HttpMethod.Post,
                    endpointUrl = globalAppConfigurationState.payloadValue.payDebtorDebtPath.first,
                    headers = mapOf("store_id" to request.storeId),
                    body = request
                )

                if (response.negative || response.payload == null) {
                    postInAppNotification(response.message, NotificationType.Negative)
                    onCompleted?.invoke(DataState.Empty(response.message))
                } else {
                    debtorsState.emit(
                        DataState.Success(
                            debtorsState.payloadValue.orEmpty().upsertDebtor(response.payload),
                            response.message
                        )
                    )
                    postInAppNotification(response.message, NotificationType.Positive)
                    onCompleted?.invoke(DataState.Success(response.payload, response.message))
                }
            }
        }
}

private val completeTransactionMutex = Mutex()
val completeTransactionInProgressState = MutableStateFlow(false)
private val getTransactionsMutex = Mutex()

@kotlinx.serialization.Serializable
data class TransactionPaymentDraftDataModel(
    val transactionTypeIndex: Int,
    val clientId: Int,
    val paymentModeId: String,
    val paidCash: Double,
    val paidCard: Double,
    val cardPaymentOptionId: Int,
    val debtor: DebtorDataModel? = null,
    val cashInputText: String = "",
    val cardInputText: String = "",
    val debtInputText: String = "",
    val activeAmountField: String = "",
    val selectedDebtorId: String? = null,
    val newDebtDueDateText: String = "",
    val updatedAtMillis: Long = 0L
)

@kotlinx.serialization.Serializable
data class TransactionCartScrollStateDataModel(
    val transactionTypeIndex: Int,
    val clientId: Int,
    val firstVisibleItemIndex: Int = 0,
    val firstVisibleItemScrollOffset: Int = 0,
    val updatedAtMillis: Long = 0L
)

@kotlinx.serialization.Serializable
data class TransactionReceiptSnapshotDataModel(
    val transaction: TransactionDataModel,
    val store: StoreDataModel?,
    val lines: List<TransactionReceiptLineDataModel>,
    val paymentDraft: TransactionPaymentDraftDataModel,
    val currencyCode: String,
    val currencySymbol: String,
    val cashierName: String = "",
    val cashierPhoneNumber: String = "",
    val cashierEmail: String = ""
)

@kotlinx.serialization.Serializable
data class TransactionReceiptLineDataModel(
    val index: Int,
    val goodsItemId: String,
    val name: List<LocalizedStringDataModel>,
    val barcode: String,
    val quantity: QuantityDataModel,
    val pricePerUnit: Double,
    val currencyCode: String,
    val currencySymbol: String,
    val saleMethodId: String = SALE_METHOD_RETAIL,
    val saleMethodName: List<LocalizedStringDataModel> = saleMethodLocalizedName(saleMethodId),
    val returnReason: String = ""
) {
    val total: Double
        get() = quantity.total * pricePerUnit
}


data class ReceiptPlatformActionResult(
    val success: Boolean,
    val message: String = ""
)

@kotlinx.serialization.Serializable
data class PlatformReceiptPrinterDataModel(
    val id: String,
    val name: String,
    val subtitle: String = "",
    val configured: Boolean = false,
    val available: Boolean = true
)

const val LABEL_PRINTER_PROTOCOL_AUTO = "auto"
const val LABEL_PRINTER_PROTOCOL_TSPL = "tspl"
const val LABEL_PRINTER_PROTOCOL_ZPL = "zpl"
const val LABEL_PRINTER_PROTOCOL_CPCL = "cpcl"

@kotlinx.serialization.Serializable
data class PlatformLabelPrinterDataModel(
    val id: String,
    val name: String,
    val subtitle: String = "",
    val configured: Boolean = false,
    val available: Boolean = true,
    val supportedProtocols: List<String> = listOf(
        LABEL_PRINTER_PROTOCOL_AUTO,
        LABEL_PRINTER_PROTOCOL_TSPL,
        LABEL_PRINTER_PROTOCOL_ZPL,
        LABEL_PRINTER_PROTOCOL_CPCL
    )
)

@kotlinx.serialization.Serializable
data class StockItemLabelDataModel(
    val itemName: String = "",
    val barcode: String = "",
    val priceText: String = "",
    val priceLabel: String = "PRICE",
    val storeName: String = "",
    val unitText: String = "",
    val note: String = "",
    val copies: Int = 1,
    val labelWidthMm: Int = 58,
    val labelHeightMm: Int = 40,
    val protocol: String = LABEL_PRINTER_PROTOCOL_AUTO
)

@kotlinx.serialization.Serializable
data class AnalyticsReportRowDataModel(
    val title: String,
    val value: String,
    val note: String = ""
)

@kotlinx.serialization.Serializable
data class AnalyticsReportSectionDataModel(
    val title: String,
    val rows: List<AnalyticsReportRowDataModel> = emptyList(),
    val notes: List<String> = emptyList()
)

@kotlinx.serialization.Serializable
data class AnalyticsReportSnapshotDataModel(
    val title: String,
    val storeName: String,
    val periodText: String,
    val scopeText: String,
    val generatedAtMillis: Long,
    val sections: List<AnalyticsReportSectionDataModel>
)

@kotlinx.serialization.Serializable
data class ReceiptTextLabelsDataModel(
    val store: String = "Store",
    val goodsReceiptTitle: String = "Goods receipt",
    val receipt: String = "Receipt",
    val transactionId: String = "Transaction ID",
    val draft: String = "Draft",
    val date: String = "Date",
    val cashier: String = "Cashier",
    val phone: String = "Phone",
    val email: String = "Email",
    val barcode: String = "Barcode",
    val noName: String = "No name",
    val noItems: String = "No items",
    val total: String = "Total",
    val cash: String = "Cash",
    val cashless: String = "Cashless",
    val debt: String = "Debt",
    val debtor: String = "Debtor",
    val debtorPhone: String = "Debtor phone",
    val change: String = "Change",
    val vat: String = "VAT / НДС / ҚҚС",
    val vatNotSpecified: String = "Not specified",
    val fiscalStatus: String = "Fiscal status",
    val nonFiscalSoftwareReceipt: String = "Non-fiscal software receipt",
    val thankYou: String = "Thank you",
    val saleReceiptTitle: String = "Sale",
    val returnReceiptTitle: String = "Return",
    val supplyReceiptTitle: String = "Acceptance",
    val returnReason: String = "Return reason",
    val pdfExportNotConfigured: String = "PDF export is not configured for this platform",
    val pdfSharingNotConfigured: String = "PDF sharing is not configured for this platform",
    val printerNotConfigured: String = "Receipt printer is not configured for this platform"
)

var saveReceiptPdfFile: (suspend (fileName: String, pdfBytes: ByteArray) -> ReceiptPlatformActionResult)? = null
var shareReceiptPdfFile: (suspend (fileName: String, pdfBytes: ByteArray, whatsappOnly: Boolean) -> ReceiptPlatformActionResult)? = null
var printReceiptEscPosBytes: (suspend (printerBytes: ByteArray) -> ReceiptPlatformActionResult)? = null
var printReceiptPlatformAction: (suspend (fileName: String, pdfBytes: ByteArray, printerBytes: ByteArray) -> ReceiptPlatformActionResult)? = null
var printPdfDocumentPlatformAction: (suspend (fileName: String, pdfBytes: ByteArray) -> ReceiptPlatformActionResult)? = null
var printHtmlDocumentPlatformAction: (suspend (fileName: String, html: String) -> ReceiptPlatformActionResult)? = null
var listPlatformReceiptPrinterDevicesAction: (suspend () -> List<PlatformReceiptPrinterDataModel>)? = null
var configurePlatformReceiptPrinterDeviceAction: (suspend (deviceId: String?) -> ReceiptPlatformActionResult)? = null
var printLabelPrinterBytes: (suspend (labelBytes: ByteArray) -> ReceiptPlatformActionResult)? = null
var listPlatformLabelPrinterDevicesAction: (suspend () -> List<PlatformLabelPrinterDataModel>)? = null
var configurePlatformLabelPrinterDeviceAction: (suspend (deviceId: String?) -> ReceiptPlatformActionResult)? = null
var configurePlatformLabelPrinterProtocolAction: (suspend (protocol: String) -> ReceiptPlatformActionResult)? = null

val receiptPrinterDevicesState = MutableStateFlow<List<PlatformReceiptPrinterDataModel>>(emptyList())
val configuredReceiptPrinterDeviceIdState = MutableStateFlow<String?>(null)
val labelPrinterDevicesState = MutableStateFlow<List<PlatformLabelPrinterDataModel>>(emptyList())
val configuredLabelPrinterDeviceIdState = MutableStateFlow<String?>(null)
val configuredLabelPrinterProtocolState = MutableStateFlow(LABEL_PRINTER_PROTOCOL_AUTO)

suspend fun saveReceiptPdf(fileName: String, pdfBytes: ByteArray, labels: ReceiptTextLabelsDataModel = ReceiptTextLabelsDataModel()): ReceiptPlatformActionResult {
    return saveReceiptPdfFile?.invoke(fileName, pdfBytes)
        ?: ReceiptPlatformActionResult(false, labels.pdfExportNotConfigured)
}

suspend fun savePdfDocument(fileName: String, pdfBytes: ByteArray, notConfiguredMessage: String = "PDF export is not configured for this platform"): ReceiptPlatformActionResult {
    return saveReceiptPdfFile?.invoke(fileName, pdfBytes)
        ?: ReceiptPlatformActionResult(false, notConfiguredMessage)
}

suspend fun shareReceiptPdf(fileName: String, pdfBytes: ByteArray, whatsappOnly: Boolean = false, labels: ReceiptTextLabelsDataModel = ReceiptTextLabelsDataModel()): ReceiptPlatformActionResult {
    return shareReceiptPdfFile?.invoke(fileName, pdfBytes, whatsappOnly)
        ?: ReceiptPlatformActionResult(false, labels.pdfSharingNotConfigured)
}

suspend fun sharePdfDocument(fileName: String, pdfBytes: ByteArray, notConfiguredMessage: String = "PDF sharing is not configured for this platform"): ReceiptPlatformActionResult {
    return shareReceiptPdfFile?.invoke(fileName, pdfBytes, false)
        ?: ReceiptPlatformActionResult(false, notConfiguredMessage)
}

suspend fun printReceiptEscPos(printerBytes: ByteArray, labels: ReceiptTextLabelsDataModel = ReceiptTextLabelsDataModel()): ReceiptPlatformActionResult {
    return printReceiptEscPosBytes?.invoke(printerBytes)
        ?: ReceiptPlatformActionResult(false, labels.printerNotConfigured)
}

suspend fun printReceipt(
    fileName: String,
    pdfBytes: ByteArray,
    printerBytes: ByteArray,
    labels: ReceiptTextLabelsDataModel = ReceiptTextLabelsDataModel()
): ReceiptPlatformActionResult {
    return printReceiptPlatformAction?.invoke(fileName, pdfBytes, printerBytes)
        ?: printReceiptEscPosBytes?.invoke(printerBytes)
        ?: ReceiptPlatformActionResult(false, labels.printerNotConfigured)
}

suspend fun printPdfDocument(fileName: String, pdfBytes: ByteArray, notConfiguredMessage: String = "Document printing is not configured for this platform"): ReceiptPlatformActionResult {
    return printPdfDocumentPlatformAction?.invoke(fileName, pdfBytes)
        ?: ReceiptPlatformActionResult(false, notConfiguredMessage)
}

suspend fun printHtmlDocument(fileName: String, html: String, notConfiguredMessage: String = "Document printing is not configured for this platform"): ReceiptPlatformActionResult {
    return printHtmlDocumentPlatformAction?.invoke(fileName, html)
        ?: ReceiptPlatformActionResult(false, notConfiguredMessage)
}

fun refreshReceiptPrinterDevices(onCompleted: ((ReceiptPlatformActionResult) -> Unit)? = null) {
    GlobalScope.launch(Dispatchers.ourIo) {
        val result = runCatching {
            val devices = listPlatformReceiptPrinterDevicesAction?.invoke().orEmpty()
            receiptPrinterDevicesState.emit(devices)
            configuredReceiptPrinterDeviceIdState.emit(devices.firstOrNull { it.configured }?.id)
            ReceiptPlatformActionResult(true, "Receipt printers refreshed")
        }.getOrElse { throwable ->
            ReceiptPlatformActionResult(false, throwable.message ?: "Could not refresh receipt printers")
        }
        onCompleted?.invoke(result)
    }
}

fun configureReceiptPrinterDevice(deviceId: String?, onCompleted: ((ReceiptPlatformActionResult) -> Unit)? = null) {
    GlobalScope.launch(Dispatchers.ourIo) {
        val result = runCatching {
            configurePlatformReceiptPrinterDeviceAction?.invoke(deviceId)
                ?: ReceiptPlatformActionResult(false, "Receipt printer configuration is not available on this platform")
        }.getOrElse { throwable ->
            ReceiptPlatformActionResult(false, throwable.message ?: "Could not configure receipt printer")
        }

        if (result.success) {
            configuredReceiptPrinterDeviceIdState.emit(deviceId)
            val devices = listPlatformReceiptPrinterDevicesAction?.invoke().orEmpty()
            receiptPrinterDevicesState.emit(devices)
        }

        onCompleted?.invoke(result)
    }
}


fun normalizeLabelPrinterProtocol(protocol: String?): String = when (protocol?.trim()?.lowercase()) {
    LABEL_PRINTER_PROTOCOL_TSPL -> LABEL_PRINTER_PROTOCOL_TSPL
    LABEL_PRINTER_PROTOCOL_ZPL -> LABEL_PRINTER_PROTOCOL_ZPL
    LABEL_PRINTER_PROTOCOL_CPCL -> LABEL_PRINTER_PROTOCOL_CPCL
    else -> LABEL_PRINTER_PROTOCOL_AUTO
}

fun effectiveLabelPrinterProtocol(protocol: String?): String = when (normalizeLabelPrinterProtocol(protocol)) {
    LABEL_PRINTER_PROTOCOL_ZPL -> LABEL_PRINTER_PROTOCOL_ZPL
    LABEL_PRINTER_PROTOCOL_CPCL -> LABEL_PRINTER_PROTOCOL_CPCL
    else -> LABEL_PRINTER_PROTOCOL_TSPL
}

suspend fun printStockItemLabel(
    label: StockItemLabelDataModel,
    protocol: String = label.protocol,
    notConfiguredMessage: String = "Sticky label printer is not configured for this platform"
): ReceiptPlatformActionResult {
    val normalizedProtocol = normalizeLabelPrinterProtocol(protocol)
    return printLabelPrinterBytes?.invoke(
        buildStockItemLabelPrinterBytes(label.copy(protocol = normalizedProtocol))
    ) ?: ReceiptPlatformActionResult(false, notConfiguredMessage)
}

suspend fun printStockItemLabelDocument(
    label: StockItemLabelDataModel,
    notConfiguredMessage: String = "Document printing is not configured for this platform"
): ReceiptPlatformActionResult {
    val cleanLabel = label.copy(
        barcode = label.barcode.trim(),
        copies = label.copies.coerceIn(1, 99),
        labelWidthMm = label.labelWidthMm.coerceIn(30, 110),
        labelHeightMm = label.labelHeightMm.coerceIn(20, 80)
    )
    val htmlResult = printHtmlDocument(
        fileName = cleanLabel.stockItemLabelDocumentFileName().removeSuffix(".pdf") + ".html",
        html = cleanLabel.buildStockItemLabelHtml(),
        notConfiguredMessage = notConfiguredMessage
    )
    if (htmlResult.success) return htmlResult

    return printPdfDocument(
        fileName = cleanLabel.stockItemLabelDocumentFileName(),
        pdfBytes = cleanLabel.buildStockItemLabelPdfBytes(),
        notConfiguredMessage = notConfiguredMessage
    )
}

suspend fun printStockItemLabelsDocument(
    labels: List<StockItemLabelDataModel>,
    notConfiguredMessage: String = "Document printing is not configured for this platform"
): ReceiptPlatformActionResult {
    val cleanLabels = labels.expandedCleanedStockItemLabelsForDocument()
    if (cleanLabels.isEmpty()) return ReceiptPlatformActionResult(false, "No printable labels")

    val htmlResult = printHtmlDocument(
        fileName = cleanLabels.stockItemLabelsSheetDocumentFileName().removeSuffix(".pdf") + ".html",
        html = cleanLabels.buildStockItemLabelsSheetHtml(),
        notConfiguredMessage = notConfiguredMessage
    )
    if (htmlResult.success) return htmlResult

    return printPdfDocument(
        fileName = cleanLabels.stockItemLabelsSheetDocumentFileName(),
        pdfBytes = cleanLabels.buildStockItemLabelsSheetPdfBytes(),
        notConfiguredMessage = notConfiguredMessage
    )
}

fun refreshLabelPrinterDevices(onCompleted: ((ReceiptPlatformActionResult) -> Unit)? = null) {
    GlobalScope.launch(Dispatchers.ourIo) {
        val result = runCatching {
            val devices = listPlatformLabelPrinterDevicesAction?.invoke().orEmpty()
            labelPrinterDevicesState.emit(devices)
            configuredLabelPrinterDeviceIdState.emit(devices.firstOrNull { it.configured }?.id)
            ReceiptPlatformActionResult(true, "Label printers refreshed")
        }.getOrElse { throwable ->
            ReceiptPlatformActionResult(false, throwable.message ?: "Could not refresh label printers")
        }
        onCompleted?.invoke(result)
    }
}

fun configureLabelPrinterDevice(deviceId: String?, onCompleted: ((ReceiptPlatformActionResult) -> Unit)? = null) {
    GlobalScope.launch(Dispatchers.ourIo) {
        val result = runCatching {
            configurePlatformLabelPrinterDeviceAction?.invoke(deviceId)
                ?: ReceiptPlatformActionResult(false, "Sticky label printer configuration is not available on this platform")
        }.getOrElse { throwable ->
            ReceiptPlatformActionResult(false, throwable.message ?: "Could not configure sticky label printer")
        }

        if (result.success) {
            configuredLabelPrinterDeviceIdState.emit(deviceId)
            val devices = listPlatformLabelPrinterDevicesAction?.invoke().orEmpty()
            labelPrinterDevicesState.emit(devices)
        }

        onCompleted?.invoke(result)
    }
}

fun configureLabelPrinterProtocol(protocol: String, onCompleted: ((ReceiptPlatformActionResult) -> Unit)? = null) {
    GlobalScope.launch(Dispatchers.ourIo) {
        val normalized = normalizeLabelPrinterProtocol(protocol)
        val result = runCatching {
            configurePlatformLabelPrinterProtocolAction?.invoke(normalized)
                ?: ReceiptPlatformActionResult(false, "Sticky label printer protocol configuration is not available on this platform")
        }.getOrElse { throwable ->
            ReceiptPlatformActionResult(false, throwable.message ?: "Could not configure sticky label printer protocol")
        }

        if (result.success) {
            configuredLabelPrinterProtocolState.emit(normalized)
        }

        onCompleted?.invoke(result)
    }
}

fun buildLabelPrinterTestBytes(protocol: String = LABEL_PRINTER_PROTOCOL_AUTO): ByteArray =
    buildStockItemLabelPrinterBytes(
        StockItemLabelDataModel(
            itemName = "AITA test item",
            barcode = "123456789012",
            priceText = "100 KZT",
            storeName = "AITA",
            unitText = "1 pc",
            note = "Sticker label printer is ready",
            copies = 1,
            protocol = protocol
        )
    )

fun buildStockItemLabelPrinterBytes(label: StockItemLabelDataModel): ByteArray {
    val cleanLabel = label.copy(
        itemName = labelPrinterSafeText(label.itemName, 42).ifBlank { "AITA item" },
        barcode = label.barcode.filter { it.isLetterOrDigit() }.take(48),
        priceText = labelPrinterSafeText(label.priceText, 28),
        storeName = labelPrinterSafeText(label.storeName, 32),
        unitText = labelPrinterSafeText(label.unitText, 20),
        note = labelPrinterSafeText(label.note, 38),
        copies = label.copies.coerceIn(1, 99),
        labelWidthMm = label.labelWidthMm.coerceIn(30, 110),
        labelHeightMm = label.labelHeightMm.coerceIn(20, 80),
        protocol = normalizeLabelPrinterProtocol(label.protocol)
    )

    return when (effectiveLabelPrinterProtocol(cleanLabel.protocol)) {
        LABEL_PRINTER_PROTOCOL_ZPL -> buildStockItemLabelZplBytes(cleanLabel)
        LABEL_PRINTER_PROTOCOL_CPCL -> buildStockItemLabelCpclBytes(cleanLabel)
        else -> buildStockItemLabelTsplBytes(cleanLabel)
    }
}

private fun labelDots(mm: Int, dpi: Int = 203): Int =
    kotlin.math.round(mm.toDouble() * dpi.toDouble() / 25.4).toInt().coerceAtLeast(1)

private fun labelPrinterSafeText(value: String, maxLength: Int): String = value
    .replace('\r', ' ')
    .replace('\n', ' ')
    .replace('\t', ' ')
    .replace(Regex("\\s+"), " ")
    .trim()
    .take(maxLength.coerceAtLeast(1))

private fun String.tsplQuoted(): String = labelPrinterSafeText(this, 80).replace("\"", "'")
private fun String.zplText(): String = labelPrinterSafeText(this, 80)
    .replace("^", " ")
    .replace("~", " ")
    .replace("\\", " ")
private fun String.cpclText(): String = labelPrinterSafeText(this, 80)

private fun buildStockItemLabelTsplBytes(label: StockItemLabelDataModel): ByteArray {
    val barcode = label.barcode.ifBlank { "000000000000" }
    val unitLine = label.unitText.takeIf { it.isNotBlank() }
    val noteLine = label.note.takeIf { it.isNotBlank() }
    val priceLine = label.priceText.ifBlank { " " }
    val height = label.labelHeightMm.coerceAtLeast(24)
    val commands = buildString {
        append("SIZE ${label.labelWidthMm} mm, ${height} mm\r\n")
        append("GAP 2 mm, 0 mm\r\n")
        append("DIRECTION 1\r\n")
        append("CODEPAGE UTF-8\r\n")
        append("CLS\r\n")
        label.storeName.takeIf { it.isNotBlank() }?.let { append("TEXT 24,12,\"0\",0,1,1,\"${it.tsplQuoted()}\"\r\n") }
        append("TEXT 24,44,\"0\",0,2,2,\"${label.itemName.tsplQuoted()}\"\r\n")
        append("TEXT 24,88,\"0\",0,2,2,\"${priceLine.tsplQuoted()}\"\r\n")
        unitLine?.let { append("TEXT 24,126,\"0\",0,1,1,\"${it.tsplQuoted()}\"\r\n") }
        append("BARCODE 24,154,\"128\",78,1,0,2,2,\"${barcode.tsplQuoted()}\"\r\n")
        noteLine?.let { append("TEXT 24,244,\"0\",0,1,1,\"${it.tsplQuoted()}\"\r\n") }
        append("PRINT ${label.copies.coerceIn(1,99)},1\r\n")
    }
    return commands.encodeToByteArray()
}

private fun buildStockItemLabelZplBytes(label: StockItemLabelDataModel): ByteArray {
    val width = labelDots(label.labelWidthMm)
    val height = labelDots(label.labelHeightMm)
    val barcode = label.barcode.ifBlank { "000000000000" }
    val commands = buildString {
        append("^XA\n")
        append("^CI28\n")
        append("^PW$width\n")
        append("^LL$height\n")
        append("^LH0,0\n")
        label.storeName.takeIf { it.isNotBlank() }?.let { append("^FO24,12^A0N,22,22^FD${it.zplText()}^FS\n") }
        append("^FO24,46^A0N,36,34^FB${(width - 48).coerceAtLeast(180)},2,4,L^FD${label.itemName.zplText()}^FS\n")
        label.priceText.takeIf { it.isNotBlank() }?.let { append("^FO24,116^A0N,34,34^FD${it.zplText()}^FS\n") }
        label.unitText.takeIf { it.isNotBlank() }?.let { append("^FO24,154^A0N,22,22^FD${it.zplText()}^FS\n") }
        append("^FO24,184^BY2,2,70^BCN,70,Y,N,N^FD${barcode.zplText()}^FS\n")
        label.note.takeIf { it.isNotBlank() }?.let { append("^FO24,278^A0N,20,20^FD${it.zplText()}^FS\n") }
        append("^PQ${label.copies.coerceIn(1,99)},0,1,Y\n")
        append("^XZ\n")
    }
    return commands.encodeToByteArray()
}

private fun buildStockItemLabelCpclBytes(label: StockItemLabelDataModel): ByteArray {
    val height = labelDots(label.labelHeightMm)
    val barcode = label.barcode.ifBlank { "000000000000" }
    val commands = buildString {
        append("! 0 200 200 $height ${label.copies.coerceIn(1,99)}\r\n")
        append("PAGE-WIDTH ${labelDots(label.labelWidthMm)}\r\n")
        label.storeName.takeIf { it.isNotBlank() }?.let { append("TEXT 0 1 24 12 ${it.cpclText()}\r\n") }
        append("TEXT 4 1 24 44 ${label.itemName.cpclText()}\r\n")
        label.priceText.takeIf { it.isNotBlank() }?.let { append("TEXT 4 1 24 92 ${it.cpclText()}\r\n") }
        label.unitText.takeIf { it.isNotBlank() }?.let { append("TEXT 0 1 24 132 ${it.cpclText()}\r\n") }
        append("BARCODE 128 2 1 78 24 162 ${barcode.cpclText()}\r\n")
        append("TEXT 0 1 24 244 ${barcode.cpclText()}\r\n")
        label.note.takeIf { it.isNotBlank() }?.let { append("TEXT 0 1 24 272 ${it.cpclText()}\r\n") }
        append("FORM\r\n")
        append("PRINT\r\n")
    }
    return commands.encodeToByteArray()
}

private fun String.labelDocumentSafeText(maxLength: Int = 80): String =
    replace('\r', ' ')
        .replace('\n', ' ')
        .replace('\t', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(maxLength.coerceAtLeast(1))

private fun htmlEscape(value: String): String = value
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&#39;")

private fun BarcodeLineRenderDataModel.stickyTagHumanText(): String =
    if (kind == "EAN-13" && humanText.length == 13 && humanText.all { it.isDigit() }) {
        "${humanText[0]} ${humanText.substring(1, 7)} ${humanText.substring(7, 13)}"
    } else {
        humanText
    }

data class StockItemLabelBarcodePreviewDataModel(
    val modules: List<Boolean>,
    val humanText: String,
    val scannable: Boolean
)

fun stockItemLabelBarcodePreviewData(rawBarcode: String): StockItemLabelBarcodePreviewDataModel {
    val renderData = buildBarcodeLineRenderData(rawBarcode)
    return StockItemLabelBarcodePreviewDataModel(
        modules = renderData.modules,
        humanText = renderData.stickyTagHumanText(),
        scannable = renderData.scannable
    )
}

fun stockItemLabelPriceDisplayText(rawPriceText: String): String =
    rawPriceText.stickyShelfTagPriceText()

private fun String.stickyShelfTagPriceText(): String {
    val clean = labelDocumentSafeText(32)
    val numeric = clean
        .takeWhile { char -> char.isDigit() || char == '.' || char == ',' || char.isWhitespace() }
        .filter { char -> char.isDigit() || char == '.' || char == ',' }
        .replace('.', ',')
        .trim(',')

    if (numeric.isBlank()) return clean.ifBlank { "—" }

    val pieces = numeric.split(',', limit = 2)
    val whole = pieces.getOrNull(0).orEmpty().trimStart('0').ifBlank { "0" }
    val fractionRaw = pieces.getOrNull(1).orEmpty().filter { it.isDigit() }
    if (fractionRaw.isBlank() || fractionRaw.all { it == '0' }) return whole

    val fraction = fractionRaw.padEnd(2, '0').take(2)
    return "$whole,$fraction"
}

fun StockItemLabelDataModel.stockItemLabelDocumentFileName(): String {
    val token = barcode.normalizedBarcodeToken().ifBlank { itemName.normalizedBarcodeToken() }.ifBlank { "label" }
    return "aita_item_label_${token.take(32)}.pdf"
}

private fun StockItemLabelDataModel.cleanedForDocument(): StockItemLabelDataModel = copy(
    itemName = itemName.labelDocumentSafeText(64).ifBlank { "AITA item" },
    barcode = barcode.labelDocumentSafeText(64),
    priceText = priceText.labelDocumentSafeText(32),
    priceLabel = priceLabel.labelDocumentSafeText(20).ifBlank { "PRICE" },
    storeName = storeName.labelDocumentSafeText(42).ifBlank { "AITA" },
    unitText = unitText.labelDocumentSafeText(24),
    note = note.labelDocumentSafeText(50),
    copies = copies.coerceIn(1, 99),
    labelWidthMm = labelWidthMm.coerceIn(30, 110),
    labelHeightMm = labelHeightMm.coerceIn(20, 80)
)

private fun BarcodeLineRenderDataModel.htmlBarcodeSvg(): String {
    val width = modules.size.coerceAtLeast(1)
    val rects = buildString {
        var index = 0
        while (index < modules.size) {
            if (!modules[index]) {
                index++
                continue
            }

            val start = index
            while (index < modules.size && modules[index]) index++
            append("<rect x=\"")
            append(start)
            append("\" y=\"0\" width=\"")
            append(index - start)
            append("\" height=\"100\"/>")
        }
    }
    return "<svg class=\"barcodeSvg\" viewBox=\"0 0 $width 100\" preserveAspectRatio=\"none\" aria-hidden=\"true\" xmlns=\"http://www.w3.org/2000/svg\">$rects</svg>"
}

fun StockItemLabelDataModel.buildStockItemLabelHtml(): String {
    val label = cleanedForDocument()
    val barcodeRender = buildBarcodeLineRenderData(label.barcode)
    val barcodeDigits = barcodeRender.stickyTagHumanText()
    val priceForTag = label.priceText.stickyShelfTagPriceText()
    val barcodeSvg = barcodeRender.htmlBarcodeSvg()
    val labels = (1..label.copies).joinToString("\n") { copyIndex ->
        """
        <section class="label">
          <div class="store">${htmlEscape(label.storeName)}</div>
          <div class="name">${htmlEscape(label.itemName)}</div>
          <div class="barcodeBox">
            <div class="barcode">$barcodeSvg</div>
            <div class="digits">${htmlEscape(barcodeDigits)}</div>
          </div>
          <div class="priceTitle">${htmlEscape(label.priceLabel)}</div>
          <div class="priceBox"><div class="price">${htmlEscape(priceForTag)}</div></div>
        </section>
        """ + if (copyIndex == label.copies) "" else "<div class=\"pageBreak\"></div>"
    }
    return """
<!doctype html>
<html>
<head>
<meta charset="utf-8">
<title>${htmlEscape(label.itemName)} ${htmlEscape(label.barcode)}</title>
<style>
  @page { size: ${label.labelWidthMm}mm ${label.labelHeightMm}mm; margin: 0; }
  * { box-sizing: border-box; }
  html, body { margin: 0; padding: 0; background: #ffffff; }
  body { font-family: Arial, Helvetica, system-ui, sans-serif; color: #050505; }
  .label { position: relative; width: ${label.labelWidthMm}mm; height: ${label.labelHeightMm}mm; padding: 0; overflow: hidden; background: #fff; border: 0.28mm solid #111; }
  .store { position: absolute; top: 1.05mm; left: 2.0mm; right: 2.0mm; text-align: center; font-style: italic; font-weight: 900; text-decoration-line: underline; text-decoration-thickness: 0.36mm; text-underline-offset: 0.55mm; font-size: 5.85mm; line-height: 6.45mm; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
  .name { position: absolute; top: 8.8mm; left: 1.15mm; right: 1.0mm; height: 13.9mm; font-size: 5.35mm; line-height: 5.95mm; font-weight: 400; overflow: hidden; }
  .barcodeBox { position: absolute; left: 3.25mm; bottom: 2.0mm; width: 25.8mm; height: 14.6mm; }
  .barcode { position: absolute; top: 0; left: 0; right: 0; height: 11.9mm; background: #fff; overflow: hidden; }
  .barcodeSvg { display: block; width: 100%; height: 100%; fill: #000; shape-rendering: crispEdges; }
  .digits { position: absolute; left: -1.85mm; right: -1.2mm; bottom: -0.1mm; font-family: Arial, Helvetica, sans-serif; font-size: 2.85mm; line-height: 3.0mm; letter-spacing: -0.17mm; white-space: pre; overflow: hidden; color: #000; }
  .priceTitle { position: absolute; left: 31.1mm; right: 0.8mm; bottom: 15.45mm; text-align: center; font-size: 2.95mm; line-height: 3.2mm; font-weight: 900; }
  .priceBox { position: absolute; left: 29.85mm; right: 0.65mm; bottom: 3.35mm; height: 9.75mm; border: 0.35mm solid #111; display: flex; align-items: center; justify-content: center; padding: 0 0.85mm; }
  .price { font-size: 7.95mm; line-height: 8.6mm; font-weight: 900; letter-spacing: 0.10mm; white-space: nowrap; overflow: hidden; text-overflow: clip; }
  .pageBreak { break-after: page; page-break-after: always; }
</style>
</head>
<body>
$labels
<script>window.onload = function(){ setTimeout(function(){ window.print(); }, 120); };</script>
</body>
</html>
""".trimIndent()
}

private fun List<StockItemLabelDataModel>.expandedCleanedStockItemLabelsForDocument(): List<StockItemLabelDataModel> =
    flatMap { rawLabel ->
        val cleanLabel = rawLabel.cleanedForDocument().copy(copies = 1)
        if (cleanLabel.barcode.isBlank()) {
            emptyList()
        } else {
            List(rawLabel.copies.coerceIn(1, 99)) { cleanLabel }
        }
    }

private fun List<StockItemLabelDataModel>.stockItemLabelsSheetDocumentFileName(): String {
    val token = joinToString("_") { label ->
        label.barcode.normalizedBarcodeToken()
            .ifBlank { label.itemName.normalizedBarcodeToken() }
            .ifBlank { "label" }
            .take(10)
    }.ifBlank { "sheet" }.take(48)
    return "aita_item_label_sheet_$token.pdf"
}

private fun StockItemLabelDataModel.buildStockItemLabelSheetSectionHtml(): String {
    val label = cleanedForDocument().copy(copies = 1)
    val barcodeRender = buildBarcodeLineRenderData(label.barcode)
    val barcodeDigits = barcodeRender.stickyTagHumanText()
    val priceForTag = label.priceText.stickyShelfTagPriceText()
    val barcodeSvg = barcodeRender.htmlBarcodeSvg()
    return """
    <section class="label">
      <div class="store">${htmlEscape(label.storeName)}</div>
      <div class="name">${htmlEscape(label.itemName)}</div>
      <div class="barcodeBox">
        <div class="barcode">$barcodeSvg</div>
        <div class="digits">${htmlEscape(barcodeDigits)}</div>
      </div>
      <div class="priceTitle">${htmlEscape(label.priceLabel)}</div>
      <div class="priceBox"><div class="price">${htmlEscape(priceForTag)}</div></div>
    </section>
    """.trimIndent()
}

fun List<StockItemLabelDataModel>.buildStockItemLabelsSheetHtml(): String {
    val labels = expandedCleanedStockItemLabelsForDocument()
    val content = labels.joinToString("\n") { it.buildStockItemLabelSheetSectionHtml() }
    val title = labels.firstOrNull()?.let { htmlEscape(it.storeName.ifBlank { "AITA" }) } ?: "AITA"
    return """
<!doctype html>
<html>
<head>
<meta charset="utf-8">
<title>$title labels</title>
<style>
  @page { size: A4; margin: 8mm; }
  * { box-sizing: border-box; }
  html, body { margin: 0; padding: 0; background: #ffffff; }
  body { font-family: Arial, Helvetica, system-ui, sans-serif; color: #050505; }
  .sheet { display: grid; grid-template-columns: repeat(3, 58mm); gap: 4mm; align-content: start; }
  .label { position: relative; width: 58mm; height: 40mm; padding: 0; overflow: hidden; background: #fff; border: 0.28mm solid #111; break-inside: avoid; page-break-inside: avoid; }
  .store { position: absolute; top: 1.05mm; left: 2.0mm; right: 2.0mm; text-align: center; font-style: italic; font-weight: 900; text-decoration-line: underline; text-decoration-thickness: 0.36mm; text-underline-offset: 0.55mm; font-size: 5.85mm; line-height: 6.45mm; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
  .name { position: absolute; top: 8.8mm; left: 1.15mm; right: 1.0mm; height: 13.9mm; font-size: 5.35mm; line-height: 5.95mm; font-weight: 400; overflow: hidden; }
  .barcodeBox { position: absolute; left: 3.25mm; bottom: 2.0mm; width: 25.8mm; height: 14.6mm; }
  .barcode { position: absolute; top: 0; left: 0; right: 0; height: 11.9mm; background: #fff; overflow: hidden; }
  .barcodeSvg { display: block; width: 100%; height: 100%; fill: #000; shape-rendering: crispEdges; }
  .digits { position: absolute; left: -1.85mm; right: -1.2mm; bottom: -0.1mm; font-family: Arial, Helvetica, sans-serif; font-size: 2.85mm; line-height: 3.0mm; letter-spacing: -0.17mm; white-space: pre; overflow: hidden; color: #000; }
  .priceTitle { position: absolute; left: 31.1mm; right: 0.8mm; bottom: 15.45mm; text-align: center; font-size: 2.95mm; line-height: 3.2mm; font-weight: 900; }
  .priceBox { position: absolute; left: 29.85mm; right: 0.65mm; bottom: 3.35mm; height: 9.75mm; border: 0.35mm solid #111; display: flex; align-items: center; justify-content: center; padding: 0 0.85mm; }
  .price { font-size: 7.95mm; line-height: 8.6mm; font-weight: 900; letter-spacing: 0.10mm; white-space: nowrap; overflow: hidden; text-overflow: clip; }
</style>
</head>
<body>
<main class="sheet">
$content
</main>
<script>window.onload = function(){ setTimeout(function(){ window.print(); }, 120); };</script>
</body>
</html>
""".trimIndent()
}

private fun Double.pdfNumber(): String {
    val scaled = kotlin.math.round(this * 100.0).toLong()
    val whole = scaled / 100L
    val fraction = kotlin.math.abs((scaled % 100L).toInt())
    return if (fraction == 0) whole.toString() else whole.toString() + "." + fraction.toString().padStart(2, '0').trimEnd('0')
}

private fun buildStockItemLabelPdfContent(label: StockItemLabelDataModel, pageWidth: Double, pageHeight: Double): String {
    val barcodeRender = buildBarcodeLineRenderData(label.barcode)
    val humanDigits = barcodeRender.stickyTagHumanText()
    val priceForTag = label.priceText.stickyShelfTagPriceText()
    val barcodeX = pageWidth * 0.052
    val barcodeY = pageHeight * 0.052
    val barcodeW = pageWidth * 0.445
    val barcodeH = pageHeight * 0.298
    val priceX = pageWidth * 0.512
    val priceY = pageHeight * 0.09
    val priceW = pageWidth - priceX - pageWidth * 0.012
    val priceH = pageHeight * 0.24
    val moduleW = (barcodeW / barcodeRender.modules.size.coerceAtLeast(1)).coerceAtLeast(0.18)
    return buildString {
        append("0 g 0 G\n")
        append("BT /F3 24 Tf 1 0 0 1 ${(pageWidth * 0.22).pdfNumber()} ${(pageHeight - 25).pdfNumber()} Tm (${pdfEscape(label.storeName)}) Tj ET\n")
        append("0.7 w ${(pageWidth * 0.21).pdfNumber()} ${(pageHeight - 29).pdfNumber()} ${(pageWidth * 0.58).pdfNumber()} 0 l S\n")
        append("BT /F1 23 Tf 1 0 0 1 ${(pageWidth * 0.02).pdfNumber()} ${(pageHeight - 62).pdfNumber()} Tm (${pdfEscape(label.itemName)}) Tj ET\n")
        append("0 g\n")
        barcodeRender.modules.forEachIndexed { index, black ->
            if (black) {
                val x = barcodeX + index * moduleW
                append("${x.pdfNumber()} ${(barcodeY + 8.0).pdfNumber()} ${(moduleW + 0.03).pdfNumber()} ${barcodeH.pdfNumber()} re f\n")
            }
        }
        append("BT /F1 8 Tf 1 0 0 1 ${(barcodeX - 5.0).pdfNumber()} ${barcodeY.pdfNumber()} Tm (${pdfEscape(humanDigits)}) Tj ET\n")
        append("BT /F2 10 Tf 1 0 0 1 ${(priceX + priceW * 0.33).pdfNumber()} ${(priceY + priceH + 9.0).pdfNumber()} Tm (${pdfEscape(label.priceLabel)}) Tj ET\n")
        append("1.0 w ${priceX.pdfNumber()} ${priceY.pdfNumber()} ${priceW.pdfNumber()} ${priceH.pdfNumber()} re S\n")
        append("BT /F2 30 Tf 1 0 0 1 ${(priceX + 9.0).pdfNumber()} ${(priceY + 7.0).pdfNumber()} Tm (${pdfEscape(priceForTag)}) Tj ET\n")
    }
}

private fun buildSimplePdfDocumentBytes(
    pageContents: List<String>,
    pageWidth: Double,
    pageHeight: Double
): ByteArray {
    val safePageContents = pageContents.ifEmpty { listOf("") }
    val fontRegularObj = 3 + safePageContents.size * 2
    val fontBoldObj = fontRegularObj + 1
    val fontObliqueObj = fontRegularObj + 2
    val objects = mutableListOf<String>()
    objects += "<< /Type /Catalog /Pages 2 0 R >>"
    val kids = safePageContents.indices.joinToString(" ") { index -> "${3 + index * 2} 0 R" }
    objects += "<< /Type /Pages /Kids [$kids] /Count ${safePageContents.size} >>"
    safePageContents.forEachIndexed { index, content ->
        val pageObj = 3 + index * 2
        val contentObj = pageObj + 1
        objects += "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${pageWidth.pdfNumber()} ${pageHeight.pdfNumber()}] /Resources << /Font << /F1 $fontRegularObj 0 R /F2 $fontBoldObj 0 R /F3 $fontObliqueObj 0 R >> >> /Contents $contentObj 0 R >>"
        objects += "<< /Length ${content.encodeToByteArray().size} >>\nstream\n$content\nendstream"
    }
    objects += "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"
    objects += "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>"
    objects += "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-BoldOblique >>"
    val out = StringBuilder()
    val offsets = mutableListOf<Int>()
    out.append("%PDF-1.4\n")
    objects.forEachIndexed { index, obj ->
        offsets += out.toString().encodeToByteArray().size
        out.append("${index + 1} 0 obj\n$obj\nendobj\n")
    }
    val xrefOffset = out.toString().encodeToByteArray().size
    out.append("xref\n0 ${objects.size + 1}\n")
    out.append("0000000000 65535 f \n")
    offsets.forEach { offset -> out.append(offset.toString().padStart(10, '0')).append(" 00000 n \n") }
    out.append("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\n")
    out.append("startxref\n$xrefOffset\n%%EOF")
    return out.toString().encodeToByteArray()
}

fun StockItemLabelDataModel.buildStockItemLabelPdfBytes(): ByteArray {
    val label = cleanedForDocument()
    val pageWidth = label.labelWidthMm.toDouble() * 72.0 / 25.4
    val pageHeight = label.labelHeightMm.toDouble() * 72.0 / 25.4
    val pageContents = (1..label.copies).map { buildStockItemLabelPdfContent(label, pageWidth, pageHeight) }
    return buildSimplePdfDocumentBytes(pageContents, pageWidth, pageHeight)
}

fun List<StockItemLabelDataModel>.buildStockItemLabelsSheetPdfBytes(): ByteArray {
    val labels = expandedCleanedStockItemLabelsForDocument()
    val pageWidth = 595.28
    val pageHeight = 841.89
    val margin = 22.68
    val gap = 11.34
    val labelWidth = 58.0 * 72.0 / 25.4
    val labelHeight = 40.0 * 72.0 / 25.4
    val columns = kotlin.math.floor((pageWidth - margin * 2 + gap) / (labelWidth + gap)).toInt().coerceAtLeast(1)
    val rows = kotlin.math.floor((pageHeight - margin * 2 + gap) / (labelHeight + gap)).toInt().coerceAtLeast(1)
    val labelsPerPage = (columns * rows).coerceAtLeast(1)
    val pageContents = labels.chunked(labelsPerPage).map { pageLabels ->
        buildString {
            pageLabels.forEachIndexed { index, label ->
                val column = index % columns
                val row = index / columns
                val x = margin + column * (labelWidth + gap)
                val y = pageHeight - margin - labelHeight - row * (labelHeight + gap)
                append("q\n1 0 0 1 ${x.pdfNumber()} ${y.pdfNumber()} cm\n")
                append(buildStockItemLabelPdfContent(label, labelWidth, labelHeight))
                append("Q\n")
            }
        }
    }
    return buildSimplePdfDocumentBytes(pageContents, pageWidth, pageHeight)
}


fun buildReceiptPrinterTestEscPosBytes(title: String = "AITA printer test", dateText: String = ""): ByteArray {
    val bytes = mutableListOf<Byte>()
    fun add(vararg values: Int) { values.forEach { bytes += it.toByte() } }
    fun addText(value: String) { bytes += value.escPosSafe().encodeToByteArray().toList() }

    add(0x1B, 0x40)
    add(0x1B, 0x61, 0x01)
    add(0x1B, 0x45, 0x01)
    addText(title.ifBlank { "AITA printer test" } + "\n")
    add(0x1B, 0x45, 0x00)
    if (dateText.isNotBlank()) addText(dateText + "\n")
    addText("--------------------------------\n")
    addText("Thermal receipt printer is ready.\n")
    addText("This path uses ESC/POS bytes, not A4 PDF.\n")
    addText("\n\n")
    add(0x1D, 0x56, 0x42, 0x00)
    return bytes.toByteArray()
}

private fun receiptVisibleString(values: List<LocalizedStringDataModel>, language: String, fallback: String): String {
    return values.extractLocalizedString(language)
        ?.trim()
        ?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
        ?: values.extractLocalizedString("main")
            ?.trim()
            ?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
        ?: values.firstOrNull { it.value.trim().isNotBlank() && !it.value.trim().equals("null", ignoreCase = true) }
            ?.value
            ?.trim()
        ?: fallback
}

private fun receiptMoney(value: Double): String {
    val rounded = kotlin.math.floor(value.coerceAtLeast(0.0) * 100.0) / 100.0
    val whole = rounded.toLong()
    val cents = kotlin.math.round((rounded - whole) * 100.0).toInt()
    return "$whole.${cents.toString().padStart(2, '0')}"
}

private fun receiptQuantityAmount(value: Double, roundTotal: Boolean): String {
    if (roundTotal)
        return value.toInt().toString()

    val scaled = kotlin.math.round(value.coerceAtLeast(0.0) * 1000.0).toLong()
    val whole = scaled / 1000
    val fraction = (scaled % 1000).toString().padStart(3, '0')

    return "$whole.$fraction"
}

private fun receiptQuantityText(quantity: QuantityDataModel, language: String): String {
    val value = receiptQuantityAmount(quantity.total, quantity.roundTotal)
    val unit = receiptVisibleString(quantity.immutableUnitName, language, quantity.id.ifBlank { "unit" })
    return "$value $unit".trim()
}

fun String.asDisplayPhoneNumber(): String {
    val clean = trim()
    if (clean.isBlank()) return ""
    if (clean.startsWith("+")) return clean
    return "+${clean.trimStart('+')}"
}

fun Iterable<String>.asDisplayPhoneNumbers(): List<String> =
    map { it.asDisplayPhoneNumber() }.filter { it.isNotBlank() }

private fun receiptDateTimeText(timeMillis: Long): String {
    return runCatching {
        val dt = Instant.fromEpochMilliseconds(timeMillis).toLocalDateTime(TimeZone.currentSystemDefault())
        "${dt.dayOfMonth.toString().padStart(2, '0')}.${dt.monthNumber.toString().padStart(2, '0')}.${dt.year} ${dt.hour.toString().padStart(2, '0')}:${dt.minute.toString().padStart(2, '0')}:${dt.second.toString().padStart(2, '0')}"
    }.getOrElse { timeMillis.toString() }
}

fun TransactionReceiptSnapshotDataModel.receiptTitle(labels: ReceiptTextLabelsDataModel): String {
    return when (transaction.type) {
        "purchase" -> labels.saleReceiptTitle
        "return" -> labels.returnReceiptTitle
        else -> labels.supplyReceiptTitle
    }
}

fun TransactionReceiptSnapshotDataModel.receiptNumberText(labels: ReceiptTextLabelsDataModel = ReceiptTextLabelsDataModel()): String {
    val id = transaction.id.takeIf { it.isNotBlank() } ?: labels.draft
    return id.take(8).uppercase()
}

fun TransactionReceiptSnapshotDataModel.totalAmount(): Double {
    return lines.sumOf { it.total }.roundMoney()
}

fun TransactionReceiptSnapshotDataModel.debtAmount(): Double {
    return paymentDraft.debtor?.debtAmount?.roundMoney() ?: 0.0
}

fun TransactionReceiptSnapshotDataModel.paidAmount(): Double {
    return (paymentDraft.paidCash + paymentDraft.paidCard).roundMoney()
}

fun TransactionReceiptSnapshotDataModel.changeAmount(): Double {
    val change = paymentDraft.paidCash - (totalAmount() - paymentDraft.paidCard - debtAmount()).coerceAtLeast(0.0)
    return change.coerceAtLeast(0.0).roundMoney()
}

fun TransactionReceiptSnapshotDataModel.buildReceiptPlainText(language: String, labels: ReceiptTextLabelsDataModel): String {
    val builder = StringBuilder()
    val storeName = store?.let {
        val form = it.companyForms.firstOrNull()?.name?.let { name -> receiptVisibleString(name, language, "") }.orEmpty()
        val name = receiptVisibleString(it.name, language, labels.store)
        "$form $name".trim()
    } ?: labels.store

    builder.appendLine(storeName)
    store?.location?.name?.takeIf { it.isNotBlank() }?.let { builder.appendLine(it) }
    store?.phoneNumbers?.asDisplayPhoneNumbers()?.takeIf { it.isNotEmpty() }?.let { builder.appendLine("${labels.phone}: ${it.joinToString()}") }
    store?.emails?.takeIf { it.isNotEmpty() }?.let { builder.appendLine("${labels.email}: ${it.joinToString()}") }
    builder.appendLine("--------------------------------")
    builder.appendLine(labels.goodsReceiptTitle)
    builder.appendLine(receiptTitle(labels))
    builder.appendLine("${labels.receipt}: ${receiptNumberText(labels)}")
    if (transaction.id.isNotBlank()) builder.appendLine("${labels.transactionId}: ${transaction.id}")
    builder.appendLine("${labels.date}: ${receiptDateTimeText(transaction.timeMillis)}")
    cashierName.takeIf { it.isNotBlank() }?.let { builder.appendLine("${labels.cashier}: $it") }
    builder.appendLine("--------------------------------")

    if (lines.isEmpty()) {
        builder.appendLine(labels.noItems)
    } else {
        lines.forEach { line ->
            val name = receiptVisibleString(line.name, language, labels.noName)
            builder.appendLine("${line.index + 1}. $name")
            if (line.barcode.isNotBlank()) builder.appendLine("   ${labels.barcode}: ${line.barcode}")
            if (line.saleMethodId == SALE_METHOD_WHOLESALE) {
                val saleMethodText = receiptVisibleString(line.saleMethodName, language, "")
                if (saleMethodText.isNotBlank()) builder.appendLine("   $saleMethodText")
            }
            line.returnReason.takeIf { it.isNotBlank() }?.let { reason ->
                builder.appendLine("   ${labels.returnReason}: $reason")
            }
            builder.appendLine("   ${receiptQuantityText(line.quantity, language)} x ${receiptMoney(line.pricePerUnit)} ${line.currencySymbol} = ${receiptMoney(line.total)} ${line.currencySymbol}")
        }
    }

    builder.appendLine("--------------------------------")
    builder.appendLine("${labels.total}: ${receiptMoney(totalAmount())} $currencySymbol")
    if (paymentDraft.paidCash > 0.0) builder.appendLine("${labels.cash}: ${receiptMoney(paymentDraft.paidCash)} $currencySymbol")
    if (paymentDraft.paidCard > 0.0) builder.appendLine("${labels.cashless}: ${receiptMoney(paymentDraft.paidCard)} $currencySymbol")
    if (debtAmount() > 0.0) {
        builder.appendLine("${labels.debt}: ${receiptMoney(debtAmount())} $currencySymbol")
        paymentDraft.debtor?.let { debtor ->
            builder.appendLine("${labels.debtor}: ${debtor.displayName}")
            builder.appendLine("Type: ${debtor.debtorType}")
            debtor.idNumber.takeIf { it.isNotBlank() }?.let { builder.appendLine("ID number: $it") }
            debtor.companyIdNumber.takeIf { it.isNotBlank() }?.let { builder.appendLine("Company ID: $it") }
            debtor.phoneNumber.asDisplayPhoneNumber().takeIf { it.isNotBlank() }?.let { builder.appendLine("${labels.debtorPhone}: $it") }
            debtor.debtDueAtMillis?.let { builder.appendLine("Debt due at: ${receiptDateTimeText(it)}") }
            debtor.interest?.takeIf { it.enabled && it.ratePercent > 0.0 }?.let {
                builder.appendLine("Interest: ${it.ratePercent}% per ${it.periodUnit}")
            }
            debtor.plannedPayments.takeIf { it.isNotEmpty() }?.let { plans ->
                builder.appendLine("Payment plan:")
                plans.forEach { plan ->
                    builder.appendLine("- ${receiptMoney(plan.amount)} ${debtor.currency} by ${plan.dueAtMillis?.let { due -> receiptDateTimeText(due) } ?: "no date"}${plan.percent?.let { pct -> " ($pct%)" } ?: ""}")
                }
            }
        }
    }
    if (changeAmount() > 0.0) builder.appendLine("${labels.change}: ${receiptMoney(changeAmount())} $currencySymbol")
    builder.appendLine("--------------------------------")
    builder.appendLine("${labels.vat}: ${labels.vatNotSpecified}")
    builder.appendLine("${labels.fiscalStatus}: ${labels.nonFiscalSoftwareReceipt}")
    builder.appendLine(labels.thankYou)

    return builder.toString()
}


private fun pdfEscape(value: String): String {
    return value
        .replace("\\", "\\\\")
        .replace("(", "\\(")
        .replace(")", "\\)")
        .map { ch -> if (ch.code in 32..126) ch else '?' }
        .joinToString("")
}

fun TransactionReceiptSnapshotDataModel.buildReceiptPdfBytes(language: String, labels: ReceiptTextLabelsDataModel): ByteArray {
    val lines = buildReceiptPlainText(language, labels)
        .lines()
        .flatMap { line ->
            if (line.length <= 58) listOf(line) else line.chunked(58)
        }

    val pageWidth = 226.0
    val pageHeight = kotlin.math.max(420.0, 84.0 + lines.size * 12.0)
    val content = buildString {
        append("BT\n")
        append("/F1 9 Tf\n")
        append("12 ${pageHeight - 24} Td\n")
        lines.forEachIndexed { index, line ->
            if (index > 0) append("0 -12 Td\n")
            append("(${pdfEscape(line)}) Tj\n")
        }
        append("ET\n")
    }

    val objects = mutableListOf<String>()
    objects += "<< /Type /Catalog /Pages 2 0 R >>"
    objects += "<< /Type /Pages /Kids [3 0 R] /Count 1 >>"
    objects += "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${pageWidth.toInt()} ${pageHeight.toInt()}] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>"
    objects += "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"
    objects += "<< /Length ${content.toByteArray().size} >>\nstream\n$content\nendstream"

    val out = StringBuilder()
    val offsets = mutableListOf<Int>()
    out.append("%PDF-1.4\n")
    objects.forEachIndexed { index, obj ->
        offsets += out.toString().toByteArray().size
        out.append("${index + 1} 0 obj\n$obj\nendobj\n")
    }
    val xrefOffset = out.toString().toByteArray().size
    out.append("xref\n0 ${objects.size + 1}\n")
    out.append("0000000000 65535 f \n")
    offsets.forEach { offset ->
        out.append(offset.toString().padStart(10, '0')).append(" 00000 n \n")
    }
    out.append("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\n")
    out.append("startxref\n$xrefOffset\n%%EOF")
    return out.toString().toByteArray()
}

fun AnalyticsReportSnapshotDataModel.buildAnalyticsReportPlainText(): String {
    val builder = StringBuilder()
    builder.appendLine(storeName.ifBlank { "Store" })
    builder.appendLine(title.ifBlank { "Analytics report" })
    builder.appendLine("Generated: ${receiptDateTimeText(generatedAtMillis)}")
    builder.appendLine("Period: $periodText")
    builder.appendLine("Scope: $scopeText")
    builder.appendLine("--------------------------------")

    sections.forEachIndexed { sectionIndex, section ->
        if (sectionIndex > 0) builder.appendLine()
        builder.appendLine(section.title)
        if (section.rows.isEmpty() && section.notes.isEmpty()) {
            builder.appendLine("- No data")
        }
        section.rows.forEach { row ->
            builder.appendLine("${row.title}: ${row.value}")
            row.note.takeIf { it.isNotBlank() }?.let { builder.appendLine("  $it") }
        }
        section.notes.forEach { note ->
            if (note.isNotBlank()) builder.appendLine(note)
        }
        builder.appendLine("--------------------------------")
    }

    return builder.toString()
}

private fun wrapPdfPlainTextLine(line: String, maxChars: Int): List<String> {
    if (line.isBlank()) return listOf("")

    val result = mutableListOf<String>()
    var current = StringBuilder()

    fun flush() {
        if (current.isNotEmpty()) {
            result += current.toString()
            current = StringBuilder()
        }
    }

    line.split(Regex("\\s+")).forEach { word ->
        if (word.length > maxChars) {
            flush()
            result += word.chunked(maxChars)
        } else if (current.isEmpty()) {
            current.append(word)
        } else if (current.length + 1 + word.length <= maxChars) {
            current.append(' ').append(word)
        } else {
            flush()
            current.append(word)
        }
    }

    flush()
    return result.ifEmpty { listOf("") }
}

private fun buildPlainTextPdfBytes(
    rawLines: List<String>,
    pageWidth: Double,
    pageHeight: Double,
    margin: Double,
    fontSize: Double,
    lineHeight: Double,
    maxCharsPerLine: Int
): ByteArray {
    val lines = rawLines.flatMap { wrapPdfPlainTextLine(it, maxCharsPerLine) }
    val maxLinesPerPage = kotlin.math.floor((pageHeight - margin * 2.0) / lineHeight).toInt().coerceAtLeast(1)
    val pages = lines.chunked(maxLinesPerPage).ifEmpty { listOf(listOf("")) }

    val objects = mutableListOf<String>()
    val pageObjectIds = pages.indices.map { 4 + it * 2 }
    val fontObjectId = 3

    objects += "<< /Type /Catalog /Pages 2 0 R >>"
    objects += "<< /Type /Pages /Kids [${pageObjectIds.joinToString(" ") { "$it 0 R" }}] /Count ${pages.size} >>"
    objects += "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"

    pages.forEachIndexed { index, pageLines ->
        val pageObjectId = 4 + index * 2
        val contentObjectId = pageObjectId + 1
        val content = buildString {
            append("BT\n")
            append("/F1 $fontSize Tf\n")
            append("$margin ${pageHeight - margin} Td\n")
            pageLines.forEachIndexed { lineIndex, line ->
                if (lineIndex > 0) append("0 -$lineHeight Td\n")
                append("(${pdfEscape(line)}) Tj\n")
            }
            append("ET\n")
        }
        objects += "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${pageWidth.toInt()} ${pageHeight.toInt()}] /Resources << /Font << /F1 $fontObjectId 0 R >> >> /Contents $contentObjectId 0 R >>"
        objects += "<< /Length ${content.toByteArray().size} >>\nstream\n$content\nendstream"
    }

    val out = StringBuilder()
    val offsets = mutableListOf<Int>()
    out.append("%PDF-1.4\n")
    objects.forEachIndexed { index, obj ->
        offsets += out.toString().toByteArray().size
        out.append("${index + 1} 0 obj\n$obj\nendobj\n")
    }
    val xrefOffset = out.toString().toByteArray().size
    out.append("xref\n0 ${objects.size + 1}\n")
    out.append("0000000000 65535 f \n")
    offsets.forEach { offset ->
        out.append(offset.toString().padStart(10, '0')).append(" 00000 n \n")
    }
    out.append("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\n")
    out.append("startxref\n$xrefOffset\n%%EOF")
    return out.toString().toByteArray()
}

fun AnalyticsReportSnapshotDataModel.buildAnalyticsReportPdfBytes(): ByteArray {
    return buildPlainTextPdfBytes(
        rawLines = buildAnalyticsReportPlainText().lines(),
        pageWidth = 595.0,
        pageHeight = 842.0,
        margin = 42.0,
        fontSize = 10.0,
        lineHeight = 14.0,
        maxCharsPerLine = 86
    )
}

fun AnalyticsReportSnapshotDataModel.analyticsReportPdfFileName(): String {
    val safeStore = storeName
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "_")
        .trim('_')
        .ifBlank { "store" }
        .take(32)
    return "analytics_report_${safeStore}_${generatedAtMillis}.pdf"
}

private fun String.escPosSafe(): String {
    return map { ch -> if (ch.code in 32..126 || ch == '\n') ch else '?' }.joinToString("")
}

fun TransactionReceiptSnapshotDataModel.buildReceiptEscPosBytes(language: String, labels: ReceiptTextLabelsDataModel): ByteArray {
    val text = buildReceiptPlainText(language, labels).escPosSafe()
    val bytes = mutableListOf<Byte>()
    fun add(vararg values: Int) { values.forEach { bytes += it.toByte() } }
    fun addText(value: String) { bytes += value.encodeToByteArray().toList() }

    add(0x1B, 0x40)
    add(0x1B, 0x61, 0x01)
    add(0x1B, 0x45, 0x01)
    addText((store?.name?.let { receiptVisibleString(it, language, labels.store) } ?: labels.store) + "\n")
    add(0x1B, 0x45, 0x00)
    addText("${labels.receipt} ${receiptNumberText(labels)}\n")
    add(0x1B, 0x61, 0x00)
    addText("--------------------------------\n")
    addText(text.substringAfter("--------------------------------\n", text))
    addText("\n\n")
    add(0x1D, 0x56, 0x42, 0x00)
    return bytes.toByteArray()
}

fun TransactionReceiptSnapshotDataModel.receiptPdfFileName(labels: ReceiptTextLabelsDataModel = ReceiptTextLabelsDataModel()): String {
    val safeId = receiptNumberText(labels).replace(Regex("[^A-Za-z0-9_-]"), "_")
    return "receipt_${safeId}.pdf"
}

val latestTransactionReceiptSnapshotState =
    MutableStateFlow<TransactionReceiptSnapshotDataModel?>(null)

private fun transactionKey(transactionTypeIndex: Int, clientId: Int): String {
    return "$transactionTypeIndex:$clientId"
}

const val SALE_METHOD_RETAIL = "retail"
const val SALE_METHOD_WHOLESALE = "wholesale"

val cartSaleMethodIdsState = MutableStateFlow<Map<String, String>>(emptyMap())

fun getCartSaleMethodId(transactionTypeIndex: Int, clientId: Int, goodsItemId: String): String {
    return cartSaleMethodIdsState.value["${transactionKey(transactionTypeIndex, clientId)}:$goodsItemId"]
        ?: SALE_METHOD_RETAIL
}

fun setCartSaleMethodId(
    transactionTypeIndex: Int,
    clientId: Int,
    goodsItemId: String,
    saleMethodId: String
) {
    GlobalScope.launch(Dispatchers.ourIo) {
        val normalizedSaleMethodId = if (saleMethodId == SALE_METHOD_WHOLESALE) {
            SALE_METHOD_WHOLESALE
        } else {
            SALE_METHOD_RETAIL
        }

        val updated = cartSaleMethodIdsState.value.toMutableMap().apply {
            val key = "${transactionKey(transactionTypeIndex, clientId)}:$goodsItemId"
            if (normalizedSaleMethodId == SALE_METHOD_RETAIL) {
                remove(key)
            } else {
                this[key] = normalizedSaleMethodId
            }
        }

        cartSaleMethodIdsState.emit(updated)
        persistTransactionCartUiState()
    }
}

private fun removeCartSaleMethodId(transactionTypeIndex: Int, clientId: Int, goodsItemId: String) {
    GlobalScope.launch(Dispatchers.ourIo) {
        val updated = cartSaleMethodIdsState.value.toMutableMap().apply {
            remove("${transactionKey(transactionTypeIndex, clientId)}:$goodsItemId")
        }
        cartSaleMethodIdsState.emit(updated)
        persistTransactionCartUiState()
    }
}

private fun removeCartSaleMethodIds(transactionTypeIndex: Int, clientId: Int) {
    val prefix = "${transactionKey(transactionTypeIndex, clientId)}:"

    GlobalScope.launch(Dispatchers.ourIo) {
        val updated = cartSaleMethodIdsState.value.filterKeys { !it.startsWith(prefix) }
        cartSaleMethodIdsState.emit(updated)
        persistTransactionCartUiState()
    }
}

fun saleMethodLocalizedName(saleMethodId: String): List<LocalizedStringDataModel> =
    if (saleMethodId == SALE_METHOD_WHOLESALE) {
        listOf(
            LocalizedStringDataModel("main", "Wholesale"),
            LocalizedStringDataModel("en", "Wholesale"),
            LocalizedStringDataModel("ru", "Оптом"),
            LocalizedStringDataModel("kk", "Көтерме")
        )
    } else {
        listOf(
            LocalizedStringDataModel("main", "Retail"),
            LocalizedStringDataModel("en", "Retail"),
            LocalizedStringDataModel("ru", "Розница"),
            LocalizedStringDataModel("kk", "Бөлшек")
        )
    }

private val transactionPaymentDraftsState =
    MutableStateFlow<Map<String, TransactionPaymentDraftDataModel>>(emptyMap())

fun getTransactionPaymentDraftsState(): StateFlow<Map<String, TransactionPaymentDraftDataModel>> =
    transactionPaymentDraftsState.asStateFlow()

val cartConditionChecksState = MutableStateFlow<Map<String, Boolean>>(emptyMap())

fun getCartConditionChecksState(): StateFlow<Map<String, Boolean>> = cartConditionChecksState.asStateFlow()

private fun cartScopedPrefix(transactionTypeIndex: Int, clientId: Int): String =
    "${transactionKey(transactionTypeIndex, clientId)}:"

fun setCartConditionChecked(conditionKey: String, checked: Boolean) {
    GlobalScope.launch(Dispatchers.ourIo) {
        val cleanKey = conditionKey.trim().takeIf { it.isNotBlank() } ?: return@launch
        val updated = cartConditionChecksState.value.toMutableMap().apply {
            this[cleanKey] = checked
        }
        cartConditionChecksState.emit(updated)
        persistTransactionCartUiState()
    }
}

fun pruneCartConditionChecks(transactionTypeIndex: Int, clientId: Int, validKeys: Set<String>) {
    GlobalScope.launch(Dispatchers.ourIo) {
        val prefix = cartScopedPrefix(transactionTypeIndex, clientId)
        val updated = cartConditionChecksState.value.filterKeys { key ->
            !key.startsWith(prefix) || key in validKeys
        }
        if (updated != cartConditionChecksState.value) {
            cartConditionChecksState.emit(updated)
            persistTransactionCartUiState()
        }
    }
}

private fun removeCartConditionChecks(transactionTypeIndex: Int, clientId: Int, goodsItemId: String? = null) {
    GlobalScope.launch(Dispatchers.ourIo) {
        val prefix = if (goodsItemId == null) {
            cartScopedPrefix(transactionTypeIndex, clientId)
        } else {
            "${cartScopedPrefix(transactionTypeIndex, clientId)}$goodsItemId:"
        }
        val updated = cartConditionChecksState.value.filterKeys { !it.startsWith(prefix) }
        if (updated != cartConditionChecksState.value) {
            cartConditionChecksState.emit(updated)
            persistTransactionCartUiState()
        }
    }
}

private val transactionCartScrollStatesState =
    MutableStateFlow<Map<String, TransactionCartScrollStateDataModel>>(emptyMap())

fun getTransactionCartScrollStatesState(): StateFlow<Map<String, TransactionCartScrollStateDataModel>> =
    transactionCartScrollStatesState.asStateFlow()

fun setTransactionCartScrollState(scrollState: TransactionCartScrollStateDataModel) {
    GlobalScope.launch(Dispatchers.ourIo) {
        val key = transactionKey(scrollState.transactionTypeIndex, scrollState.clientId)
        val cleanScrollState = scrollState.copy(
            firstVisibleItemIndex = scrollState.firstVisibleItemIndex.coerceAtLeast(0),
            firstVisibleItemScrollOffset = scrollState.firstVisibleItemScrollOffset.coerceAtLeast(0),
            updatedAtMillis = getCurrentTimeMillis()
        )
        val updated = transactionCartScrollStatesState.value.toMutableMap().apply {
            this[key] = cleanScrollState
        }
        transactionCartScrollStatesState.emit(updated)
        persistTransactionCartUiState()
    }
}

fun clearTransactionCartScrollState(transactionTypeIndex: Int, clientId: Int) {
    GlobalScope.launch(Dispatchers.ourIo) {
        val updated = transactionCartScrollStatesState.value.toMutableMap().apply {
            remove(transactionKey(transactionTypeIndex, clientId))
        }
        if (updated != transactionCartScrollStatesState.value) {
            transactionCartScrollStatesState.emit(updated)
            persistTransactionCartUiState()
        }
    }
}

fun setTransactionPaymentDraft(draft: TransactionPaymentDraftDataModel) {
    GlobalScope.launch(Dispatchers.ourIo) {
        val updated = transactionPaymentDraftsState.value.toMutableMap().apply {
            this[transactionKey(draft.transactionTypeIndex, draft.clientId)] = draft
        }
        transactionPaymentDraftsState.emit(updated)
        persistTransactionCartUiState()
    }
}

fun getTransactionPaymentDraft(
    transactionTypeIndex: Int,
    clientId: Int
): TransactionPaymentDraftDataModel? {
    return transactionPaymentDraftsState.value[transactionKey(transactionTypeIndex, clientId)]
}

fun clearTransactionPaymentDraft(transactionTypeIndex: Int, clientId: Int) {
    GlobalScope.launch(Dispatchers.ourIo) {
        val updated = transactionPaymentDraftsState.value.toMutableMap().apply {
            remove(transactionKey(transactionTypeIndex, clientId))
        }
        transactionPaymentDraftsState.emit(updated)
        persistTransactionCartUiState()
    }
}

private val transactionSupplySupplierIdsState = MutableStateFlow<Map<String, String>>(emptyMap())

fun getTransactionSupplySupplierIdsState(): StateFlow<Map<String, String>> = transactionSupplySupplierIdsState.asStateFlow()

fun transactionSupplySupplierKey(transactionTypeIndex: Int, clientId: Int): String = "$transactionTypeIndex:$clientId"

fun currentTransactionSupplySupplierId(transactionTypeIndex: Int, clientId: Int): String? =
    transactionSupplySupplierIdsState.value[transactionSupplySupplierKey(transactionTypeIndex, clientId)]

fun setTransactionSupplySupplierId(transactionTypeIndex: Int, clientId: Int, supplierId: String?) {
    GlobalScope.launch(Dispatchers.ourIo) {
        val key = transactionSupplySupplierKey(transactionTypeIndex, clientId)
        val updated = transactionSupplySupplierIdsState.value.toMutableMap().apply {
            if (supplierId.isNullOrBlank()) remove(key) else put(key, supplierId)
        }
        transactionSupplySupplierIdsState.emit(updated)
        persistTransactionCartUiState()
    }
}

fun clearTransactionSupplySupplierId(transactionTypeIndex: Int, clientId: Int) {
    setTransactionSupplySupplierId(transactionTypeIndex, clientId, null)
}

private const val MAX_RETURN_REASON_LENGTH = 500
private val cartReturnReasonsState = MutableStateFlow<Map<String, String>>(emptyMap())

fun getCartReturnReasonsState(): StateFlow<Map<String, String>> = cartReturnReasonsState.asStateFlow()

fun cartReturnReasonKey(transactionTypeIndex: Int, clientId: Int, goodsItemId: String): String =
    "${transactionKey(transactionTypeIndex, clientId)}:$goodsItemId"

fun currentCartReturnReason(transactionTypeIndex: Int, clientId: Int, goodsItemId: String): String =
    cartReturnReasonsState.value[cartReturnReasonKey(transactionTypeIndex, clientId, goodsItemId)].orEmpty()

fun setCartReturnReason(transactionTypeIndex: Int, clientId: Int, goodsItemId: String, reason: String) {
    GlobalScope.launch(Dispatchers.ourIo) {
        val key = cartReturnReasonKey(transactionTypeIndex, clientId, goodsItemId)
        val boundedReason = reason.take(MAX_RETURN_REASON_LENGTH)
        val updated = cartReturnReasonsState.value.toMutableMap().apply {
            if (transactionTypeIndex == 1 && boundedReason.trim().isNotBlank()) {
                this[key] = boundedReason
            } else {
                remove(key)
            }
        }
        if (updated != cartReturnReasonsState.value) {
            cartReturnReasonsState.emit(updated)
            persistTransactionCartUiState()
        }
    }
}

private fun removeCartReturnReason(transactionTypeIndex: Int, clientId: Int, goodsItemId: String) {
    GlobalScope.launch(Dispatchers.ourIo) {
        val updated = cartReturnReasonsState.value.toMutableMap().apply {
            remove(cartReturnReasonKey(transactionTypeIndex, clientId, goodsItemId))
        }
        if (updated != cartReturnReasonsState.value) {
            cartReturnReasonsState.emit(updated)
            persistTransactionCartUiState()
        }
    }
}

private fun removeCartReturnReasons(transactionTypeIndex: Int, clientId: Int) {
    val prefix = cartScopedPrefix(transactionTypeIndex, clientId)
    GlobalScope.launch(Dispatchers.ourIo) {
        val updated = cartReturnReasonsState.value.filterKeys { !it.startsWith(prefix) }
        if (updated != cartReturnReasonsState.value) {
            cartReturnReasonsState.emit(updated)
            persistTransactionCartUiState()
        }
    }
}

private fun removeCartReturnReasonsByGoodsItemId(goodsItemId: String) {
    GlobalScope.launch(Dispatchers.ourIo) {
        val updated = cartReturnReasonsState.value.filterKeys { key ->
            key.substringAfterLast(':') != goodsItemId
        }
        if (updated != cartReturnReasonsState.value) {
            cartReturnReasonsState.emit(updated)
            persistTransactionCartUiState()
        }
    }
}

fun transactionServerType(transactionTypeIndex: Int): String {
    return when (transactionTypeIndex) {
        0 -> "purchase"
        1 -> "return"
        else -> "accept"
    }
}

fun transactionTitle(
    transactionTypeIndex: Int,
    sale: String,
    returnText: String,
    supply: String
): String {
    return when (transactionTypeIndex) {
        0 -> sale
        1 -> returnText
        else -> supply
    }
}

fun GoodsItemDataModel.hasWholesalePrice(): Boolean =
    wholesalePrices.any { it.price.toMoneyDouble() > 0.0 }

fun GoodsItemDataModel.isWholesaleEligible(quantityTotal: Double): Boolean {
    val minimum = wholesaleMinQuantity?.total ?: 0.0
    return hasWholesalePrice() && minimum > 0.0 && quantityTotal >= minimum
}

fun GoodsItemDataModel.basePriceForTransaction(
    transactionTypeIndex: Int,
    saleMethodId: String = SALE_METHOD_RETAIL,
    quantityTotal: Double = 1.0,
    batch: GoodsBatchDataModel? = null
): PriceDataModel {
    val retailSalePrice = batch?.salePriceOverride ?: salePrices.firstOrNull()
    val wholesalePrice = batch?.wholesalePriceOverride ?: wholesalePrices.firstOrNull()
    val returnPrice = batch?.returnPriceOverride ?: returnPrices.firstOrNull()
    val supplyPrice = batch?.supplyPrice ?: supplyPrices.firstOrNull()

    val selectedPrice = when (transactionTypeIndex) {
        0 -> if (saleMethodId == SALE_METHOD_WHOLESALE && isWholesaleEligible(quantityTotal)) {
            wholesalePrice ?: retailSalePrice
        } else {
            retailSalePrice
        }
        1 -> returnPrice
        else -> supplyPrice
    }

    return selectedPrice ?: PriceDataModel(
        price = "0",
        currency = retailSalePrice?.currency
            ?: wholesalePrice?.currency
            ?: returnPrice?.currency
            ?: supplyPrice?.currency
            ?: "",
        supplierId = ""
    )
}

fun GoodsItemDataModel.allPromotionsForBatch(batch: GoodsBatchDataModel?): List<StockPromotionDataModel> =
    (promotions + batch?.promotions.orEmpty() + batch?.discounts.orEmpty().map { it.toStockPromotionDataModel() })
        .sanitizedStockPromotions()

fun GoodsItemDataModel.promotedPriceForTransaction(
    transactionTypeIndex: Int,
    saleMethodId: String = SALE_METHOD_RETAIL,
    quantityTotal: Double = 1.0,
    batch: GoodsBatchDataModel? = null,
    nowMillis: Long = getCurrentTimeMillis()
): PromotedPriceDataModel {
    val base = basePriceForTransaction(transactionTypeIndex, saleMethodId, quantityTotal, batch)
    return allPromotionsForBatch(batch).bestPromotedPrice(
        originalPrice = base,
        transactionTypeIndex = transactionTypeIndex,
        quantityTotal = quantityTotal,
        nowMillis = nowMillis
    )
}

fun GoodsItemDataModel.firstViolatedPromotionRestriction(
    transactionTypeIndex: Int,
    quantityTotal: Double,
    batch: GoodsBatchDataModel? = null,
    nowMillis: Long = getCurrentTimeMillis()
): StockPromotionDataModel? =
    allPromotionsForBatch(batch).firstViolatedRestriction(transactionTypeIndex, quantityTotal, nowMillis)

fun GoodsItemDataModel.priceForTransaction(
    transactionTypeIndex: Int,
    saleMethodId: String = SALE_METHOD_RETAIL,
    quantityTotal: Double = 1.0,
    batch: GoodsBatchDataModel? = null
): PriceDataModel = promotedPriceForTransaction(
    transactionTypeIndex = transactionTypeIndex,
    saleMethodId = saleMethodId,
    quantityTotal = quantityTotal,
    batch = batch
).finalPrice

fun GoodsItemDataModel.defaultCartQuantity(
    configuration: GlobalAppConfigurationDataModel
): QuantityDataModel {
    return configuration.goodsItemsQuantityUnits
        .find { it.id == measurementUnitId }
        ?: configuration.goodsItemsQuantityUnits.first()
}

fun GoodsItemDataModel.isWeightMeasurementUnit(
    configuration: GlobalAppConfigurationDataModel
): Boolean {
    val unit = configuration.goodsItemsQuantityUnits
        .find { it.id == measurementUnitId }
        ?: return measurementUnitId == "1"

    return unit.isWeightQuantityUnit()
}

fun QuantityDataModel.isWeightQuantityUnit(): Boolean {
    if (id == "1") return true

    return immutableUnitName.any { localized ->
        val value = localized.value.trim().lowercase()
        value == "kg" ||
                value == "kg." ||
                value == "кг" ||
                value == "кг." ||
                value.contains("kilogram") ||
                value.contains("килограмм")
    }
}

fun QuantityDataModel.withTotalValue(total: Double): QuantityDataModel {
    val normalized = if (roundTotal) {
        total.coerceAtLeast(0.0).toInt().toDouble()
    } else {
        kotlin.math.round(total.coerceAtLeast(0.0) * 1000.0) / 1000.0
    }

    return copy(total = normalized)
}

@kotlinx.serialization.Serializable
data class EmbeddedWeightBarcodeDataModel(
    val rawBarcode: String,
    val productBarcode: String,
    val productLookupCodes: List<String>,
    val weightKilograms: Double,
    val weightGrams: Int,
    val productCodeLength: Int = 5,
    val weightDigitsLength: Int = 5,
    val formatId: String = "2+5+5+1"
)

fun String.normalizedBarcodeToken(): String {
    return filter { it.isLetterOrDigit() }.uppercase()
}

fun String.barcodeDigitsOnly(): String = filter { it.isDigit() }

fun String.hasValidRetailBarcodeChecksum(): Boolean {
    val digits = barcodeDigitsOnly()
    if (digits.length !in setOf(8, 12, 13, 14))
        return false

    val check = digits.last().digitToInt()
    val body = digits.dropLast(1)
    var sum = 0
    var weight = 3

    for (index in body.length - 1 downTo 0) {
        val digit = body[index]
        sum += digit.digitToInt() * weight
        weight = if (weight == 3) 1 else 3
    }

    return ((10 - (sum % 10)) % 10) == check
}

fun calculateGtinModulo10CheckDigit(body: String): Int {
    val digits = body.barcodeDigitsOnly()
    if (digits.isBlank()) return 0

    var sum = 0
    var weight = 3
    for (index in digits.length - 1 downTo 0) {
        sum += digits[index].digitToInt() * weight
        weight = if (weight == 3) 1 else 3
    }
    return (10 - (sum % 10)) % 10
}

fun String.isValidEan13Barcode(): Boolean {
    val digits = barcodeDigitsOnly()
    return digits.length == 13 && digits.hasValidRetailBarcodeChecksum()
}

fun generateInternalEan13Barcode(
    existingBarcodes: Iterable<String> = emptyList(),
    prefix: String = "04"
): String {
    val existing = existingBarcodes.map { it.barcodeDigitsOnly() }.toSet()
    val safePrefix = prefix.barcodeDigitsOnly().take(11).ifBlank { "04" }

    repeat(512) {
        val body = buildString {
            append(safePrefix.take(12))
            while (length < 12) append(Random.nextInt(0, 10))
        }.take(12)
        val barcode = body + calculateGtinModulo10CheckDigit(body)
        if (barcode !in existing && !barcode.isVariableMeasureRetailBarcode()) return barcode
    }

    val fallbackPrefix = "04"
    val body = buildString {
        append(fallbackPrefix)
        while (length < 12) append(Random.nextInt(0, 10))
    }
    return body + calculateGtinModulo10CheckDigit(body)
}

private data class BarcodeLineRenderDataModel(
    val modules: List<Boolean>,
    val humanText: String,
    val kind: String,
    val scannable: Boolean
)

private val ean13LeftOddPatterns = arrayOf("0001101", "0011001", "0010011", "0111101", "0100011", "0110001", "0101111", "0111011", "0110111", "0001011")
private val ean13LeftEvenPatterns = arrayOf("0100111", "0110011", "0011011", "0100001", "0011101", "0111001", "0000101", "0010001", "0001001", "0010111")
private val ean13RightPatterns = arrayOf("1110010", "1100110", "1101100", "1000010", "1011100", "1001110", "1010000", "1000100", "1001000", "1110100")
private val ean13ParityPatterns = arrayOf("LLLLLL", "LLGLGG", "LLGGLG", "LLGGGL", "LGLLGG", "LGGLLG", "LGGGLL", "LGLGLG", "LGLGGL", "LGGLGL")

private fun buildEan13BarcodeModules(ean13: String): List<Boolean> {
    val digits = ean13.barcodeDigitsOnly()
    if (digits.length != 13) return emptyList()
    val modules = mutableListOf<Boolean>()
    fun appendPattern(pattern: String) { pattern.forEach { modules += it == '1' } }
    val first = digits.first().digitToInt()
    val parity = ean13ParityPatterns.getOrElse(first) { ean13ParityPatterns[0] }
    appendPattern("101")
    digits.substring(1, 7).forEachIndexed { index, ch ->
        val digit = ch.digitToInt()
        appendPattern(if (parity[index] == 'G') ean13LeftEvenPatterns[digit] else ean13LeftOddPatterns[digit])
    }
    appendPattern("01010")
    digits.substring(7, 13).forEach { ch -> appendPattern(ean13RightPatterns[ch.digitToInt()]) }
    appendPattern("101")
    return modules
}

private fun buildPseudoBarcodeModules(token: String): List<Boolean> {
    val clean = token.normalizedBarcodeToken().ifBlank { "AITA" }
    val modules = mutableListOf<Boolean>()
    modules += listOf(true, false, true, false)
    clean.encodeToByteArray().forEach { byte ->
        for (shift in 7 downTo 0) modules += ((byte.toInt() ushr shift) and 1) == 1
        modules += false
    }
    modules += listOf(false, true, false, true)
    return modules.take(128).ifEmpty { listOf(true, false, true, false, true, false) }
}

private fun buildBarcodeLineRenderData(rawBarcode: String): BarcodeLineRenderDataModel {
    val digits = rawBarcode.barcodeDigitsOnly()
    val ean13 = when {
        digits.length == 13 && digits.hasValidRetailBarcodeChecksum() -> digits
        digits.length == 12 && digits.hasValidRetailBarcodeChecksum() -> "0$digits"
        else -> null
    }
    if (ean13 != null) {
        return BarcodeLineRenderDataModel(
            modules = buildEan13BarcodeModules(ean13),
            humanText = ean13,
            kind = "EAN-13",
            scannable = true
        )
    }

    val token = rawBarcode.normalizedBarcodeToken().ifBlank { digits.ifBlank { "000000000000" } }
    return BarcodeLineRenderDataModel(
        modules = buildPseudoBarcodeModules(token),
        humanText = rawBarcode.takeIf { it.isNotBlank() } ?: token,
        kind = "Text",
        scannable = false
    )
}

fun String.isVariableMeasureRetailBarcode(): Boolean {
    val digits = barcodeDigitsOnly()
    if (digits.length != 13) return false

    val prefix2Text = digits.take(2)
    val prefix2 = prefix2Text.toIntOrNull() ?: return false

    return prefix2Text == "02" || prefix2 in 20..29
}

fun String.parseEmbeddedWeightBarcodeFormats(
    requireValidChecksum: Boolean = false,
    allowZeroWeight: Boolean = false
): List<EmbeddedWeightBarcodeDataModel> {
    val digits = barcodeDigitsOnly()

    fun ean13Format(productCodeLength: Int, weightDigitsLength: Int): EmbeddedWeightBarcodeDataModel? {
        if (digits.length != 13 || !digits.isVariableMeasureRetailBarcode()) return null
        if (requireValidChecksum && !digits.hasValidRetailBarcodeChecksum()) return null

        val productEnd = 2 + productCodeLength
        val weightEnd = productEnd + weightDigitsLength
        if (weightEnd > 12) return null

        val productBarcode = digits.take(productEnd)
        val itemCodeWithoutPrefix = digits.substring(2, productEnd)
        val grams = digits.substring(productEnd, weightEnd).toIntOrNull() ?: return null

        if (grams <= 0 && !allowZeroWeight) return null

        return EmbeddedWeightBarcodeDataModel(
            rawBarcode = digits,
            productBarcode = productBarcode,
            productLookupCodes = listOf(productBarcode, itemCodeWithoutPrefix).distinct(),
            weightKilograms = grams.toDouble() / 1000.0,
            weightGrams = grams,
            productCodeLength = productCodeLength,
            weightDigitsLength = weightDigitsLength,
            formatId = "2+$productCodeLength+$weightDigitsLength+1"
        )
    }

    fun upcLike12Format(): EmbeddedWeightBarcodeDataModel? {
        if (digits.length != 12 || digits.firstOrNull() != '2') return null
        if (requireValidChecksum && !digits.hasValidRetailBarcodeChecksum()) return null

        val productBarcode = digits.substring(0, 6)
        val itemCodeWithoutPrefix = digits.substring(1, 6)
        val grams = digits.substring(6, 11).toIntOrNull() ?: return null

        if (grams <= 0 && !allowZeroWeight) return null

        return EmbeddedWeightBarcodeDataModel(
            rawBarcode = digits,
            productBarcode = productBarcode,
            productLookupCodes = listOf(productBarcode, itemCodeWithoutPrefix).distinct(),
            weightKilograms = grams.toDouble() / 1000.0,
            weightGrams = grams,
            productCodeLength = 5,
            weightDigitsLength = 5,
            formatId = "1+5+5+1"
        )
    }

    return listOfNotNull(
        ean13Format(productCodeLength = 5, weightDigitsLength = 5),
        ean13Format(productCodeLength = 6, weightDigitsLength = 4),
        upcLike12Format()
    )
}

fun String.parseEmbeddedWeightBarcode(): EmbeddedWeightBarcodeDataModel? {
    return parseEmbeddedWeightBarcodeFormats(requireValidChecksum = false, allowZeroWeight = false)
        .firstOrNull()
}

fun String.toStoredGoodsItemBarcodeCandidates(): List<String> {
    val weightedCandidates = parseEmbeddedWeightBarcodeFormats(requireValidChecksum = false, allowZeroWeight = true)
        .flatMap { barcode ->
            listOf(barcode.productBarcode) + barcode.productLookupCodes.filter { it.length >= 5 }
        }
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()

    return weightedCandidates.ifEmpty { listOf(trim()).filter { it.isNotEmpty() } }
}

fun String.toStoredGoodsItemBarcode(): String {
    return toStoredGoodsItemBarcodeCandidates().firstOrNull() ?: trim()
}

const val GOODS_ITEM_BARCODE_TYPE_STANDARD = "standard"
const val GOODS_ITEM_BARCODE_TYPE_INTERNAL = "internal"

@kotlinx.serialization.Serializable
data class GoodsItemBarcodeDataModel(
    val value: String = "",
    val type: String = GOODS_ITEM_BARCODE_TYPE_STANDARD,
    val storeId: String? = null
)

fun String.isLikelyStandardGoodsItemBarcode(): Boolean {
    val clean = trim()
    val digits = clean.barcodeDigitsOnly()
    if (digits.isBlank()) return false
    if (digits != clean) return false
    if (digits.isVariableMeasureRetailBarcode()) return false
    return digits.hasValidRetailBarcodeChecksum()
}

fun String.inferredGoodsItemBarcodeType(): String {
    return if (isLikelyStandardGoodsItemBarcode()) GOODS_ITEM_BARCODE_TYPE_STANDARD else GOODS_ITEM_BARCODE_TYPE_INTERNAL
}

fun String.normalizedGoodsItemBarcodeType(cleanBarcodeValue: String? = null): String {
    return when (trim().lowercase()) {
        GOODS_ITEM_BARCODE_TYPE_INTERNAL -> GOODS_ITEM_BARCODE_TYPE_INTERNAL
        GOODS_ITEM_BARCODE_TYPE_STANDARD -> GOODS_ITEM_BARCODE_TYPE_STANDARD
        else -> GOODS_ITEM_BARCODE_TYPE_STANDARD
    }
}

fun List<String>.cleanLegacyGoodsItemBarcodes(): List<String> {
    return flatMap { it.trim().toStoredGoodsItemBarcodeCandidates() }
        .filter { it.isNotBlank() }
        .distinct()
}

fun GoodsItemBarcodeDataModel.normalizedForStore(storeId: String): GoodsItemBarcodeDataModel? {
    val cleanValue = value.trim().toStoredGoodsItemBarcode().takeIf { it.isNotBlank() } ?: return null
    val cleanType = type.normalizedGoodsItemBarcodeType(cleanValue)
    return copy(
        value = cleanValue,
        type = cleanType,
        storeId = if (cleanType == GOODS_ITEM_BARCODE_TYPE_INTERNAL) storeId.takeIf { it.isNotBlank() } else null
    )
}

fun List<GoodsItemBarcodeDataModel>.normalizedGoodsItemBarcodesForStore(
    storeId: String,
    legacyBarcodes: List<String> = emptyList()
): List<GoodsItemBarcodeDataModel> {
    val source = if (isNotEmpty()) this else legacyBarcodes.cleanLegacyGoodsItemBarcodes().map { barcode ->
        GoodsItemBarcodeDataModel(value = barcode, type = GOODS_ITEM_BARCODE_TYPE_STANDARD)
    }

    return source
        .flatMap { model ->
            val rawType = model.type
            model.value.trim().toStoredGoodsItemBarcodeCandidates().map { candidate ->
                model.copy(value = candidate, type = rawType.normalizedGoodsItemBarcodeType(candidate))
            }
        }
        .mapNotNull { it.normalizedForStore(storeId) }
        .distinctBy { "${it.type}|${it.storeId.orEmpty()}|${it.value.normalizedBarcodeToken()}" }
}

fun List<GoodsItemBarcodeDataModel>.toLegacyBarcodeStrings(): List<String> =
    map { it.value.trim().toStoredGoodsItemBarcode() }
        .filter { it.isNotBlank() }
        .distinct()

fun GoodsItemDataModel.effectiveBarcodeModels(): List<GoodsItemBarcodeDataModel> =
    barcodeModels.normalizedGoodsItemBarcodesForStore(storeId, barcodes)

fun GoodsItemDataModel.allBarcodeValues(): List<String> =
    effectiveBarcodeModels().toLegacyBarcodeStrings().ifEmpty { barcodes.cleanLegacyGoodsItemBarcodes() }

fun GoodsItemDataModel.standardBarcodeValues(): List<String> =
    effectiveBarcodeModels()
        .filter { it.type == GOODS_ITEM_BARCODE_TYPE_STANDARD }
        .toLegacyBarcodeStrings()

fun GoodsItemDataModel.internalBarcodeValues(): List<String> =
    effectiveBarcodeModels()
        .filter { it.type == GOODS_ITEM_BARCODE_TYPE_INTERNAL }
        .toLegacyBarcodeStrings()

fun storedBarcodeMatchesScannedTransactionBarcode(storedBarcode: String, scannedBarcode: String): Boolean {
    val storedToken = storedBarcode.normalizedBarcodeToken()
    val scannedToken = scannedBarcode.normalizedBarcodeToken()

    if (storedToken.isNotBlank() && storedToken == scannedToken) return true

    val storedWeightedLookupTokens = storedBarcode
        .parseEmbeddedWeightBarcodeFormats(requireValidChecksum = false, allowZeroWeight = true)
        .flatMap { barcode -> listOf(barcode.productBarcode) + barcode.productLookupCodes }
        .map { it.normalizedBarcodeToken() }
        .filter { it.isNotBlank() }
        .toSet()

    val scannedWeightedLookupTokens = scannedBarcode
        .parseEmbeddedWeightBarcodeFormats(requireValidChecksum = false, allowZeroWeight = true)
        .flatMap { barcode -> listOf(barcode.productBarcode) + barcode.productLookupCodes }
        .map { it.normalizedBarcodeToken() }
        .filter { it.isNotBlank() }
        .toSet()

    if (storedWeightedLookupTokens.isNotEmpty() && scannedToken in storedWeightedLookupTokens) return true
    if (storedToken in scannedWeightedLookupTokens) return true
    if (storedWeightedLookupTokens.any { it in scannedWeightedLookupTokens }) return true

    return false
}

fun GoodsItemDataModel.matchesEmbeddedWeightBarcode(
    embeddedWeightBarcode: EmbeddedWeightBarcodeDataModel
): Boolean {
    val lookupCodes = embeddedWeightBarcode.productLookupCodes.map { it.normalizedBarcodeToken() }.toSet()
    val productBarcode = embeddedWeightBarcode.productBarcode.normalizedBarcodeToken()

    return allBarcodeValues().any { barcode ->
        val normalized = barcode.normalizedBarcodeToken()
        val storedBarcode = barcode.toStoredGoodsItemBarcode().normalizedBarcodeToken()

        normalized in lookupCodes ||
                storedBarcode == productBarcode ||
                barcode.parseEmbeddedWeightBarcodeFormats(requireValidChecksum = false, allowZeroWeight = true)
                    .any { it.productBarcode.normalizedBarcodeToken() == productBarcode } ||
                (normalized.length in setOf(12, 13, 14) && normalized.startsWith(productBarcode))
    }
}

fun GoodsItemDataModel.firstBarcode(): String {
    return allBarcodeValues().firstOrNull()?.toStoredGoodsItemBarcode().orEmpty()
}

fun Double.roundMoney(): Double {
    return kotlin.math.floor(this * 100.0) / 100.0
}

fun String.toMoneyDouble(): Double {
    return trim()
        .replace(",", ".")
        .toDoubleOrNull()
        ?.roundMoney()
        ?: 0.0
}

fun Double.toStockMoneyText(): String {
    val fixed = roundMoney()
    val whole = fixed.toLong()
    val cents = kotlin.math.round((fixed - whole) * 100.0).toInt()
    return if (cents == 0) {
        whole.toString()
    } else {
        "${whole}.${cents.toString().padStart(2, '0')}"
    }
}

fun PriceDataModel.withMoneyAmount(amount: Double): PriceDataModel =
    copy(price = amount.coerceAtLeast(0.0).roundMoney().toStockMoneyText())

fun BatchDiscountDataModel.toStockPromotionDataModel(): StockPromotionDataModel = StockPromotionDataModel(
    id = id,
    title = title,
    type = STOCK_PROMOTION_TYPE_DISCOUNT,
    mode = if (mode == STOCK_PROMOTION_MODE_FIXED) STOCK_PROMOTION_MODE_FIXED else STOCK_PROMOTION_MODE_PERCENT,
    value = value,
    transactionTypeIndices = listOf(0),
    startsAtMillis = startsAtMillis,
    endsAtMillis = endsAtMillis,
    note = note,
    noteLocalized = note?.takeIf { it.isNotBlank() }?.let { listOf(LocalizedStringDataModel("main", it)) } ?: emptyList(),
    isActive = isActive
)

fun StockPromotionDataModel.normalized(): StockPromotionDataModel = copy(
    id = id.ifBlank { buildString { append("promo_"); append(kotlin.random.Random.nextLong().toString().replace("-", "")) } },
    title = title.filter { it.value.isNotBlank() },
    type = when (type) {
        STOCK_PROMOTION_TYPE_SPECIAL_PRICE, STOCK_PROMOTION_TYPE_RESTRICTION -> type
        else -> STOCK_PROMOTION_TYPE_DISCOUNT
    },
    mode = when (mode) {
        STOCK_PROMOTION_MODE_FIXED, STOCK_PROMOTION_MODE_PRICE -> mode
        else -> STOCK_PROMOTION_MODE_PERCENT
    },
    value = value.trim().replace(',', '.').takeIf { it.toDoubleOrNull() != null } ?: "0",
    transactionTypeIndices = transactionTypeIndices.filter { it in 0..2 }.distinct(),
    minQuantity = minQuantity?.takeIf { it > 0.0 },
    note = note?.trim()?.takeIf { it.isNotBlank() },
    noteLocalized = noteLocalized
        .map { it.copy(language = it.language.trim(), value = it.value.trim()) }
        .filter { it.language.isNotBlank() && it.value.isNotBlank() }
        .distinctBy { it.language }
)

fun List<StockPromotionDataModel>.sanitizedStockPromotions(): List<StockPromotionDataModel> =
    map { it.normalized() }
        .filter { promo ->
            promo.isActive ||
                    promo.title.isNotEmpty() ||
                    promo.note?.isNotBlank() == true ||
                    promo.noteLocalized.any { it.value.isNotBlank() } ||
                    promo.value.toMoneyDouble() > 0.0 ||
                    promo.minQuantity != null
        }
        .distinctBy { it.id }

fun StockPromotionDataModel.isActiveAt(nowMillis: Long = getCurrentTimeMillis()): Boolean {
    if (!isActive) return false
    if (startsAtMillis != null && nowMillis < startsAtMillis) return false
    if (endsAtMillis != null && nowMillis > endsAtMillis) return false
    return true
}

fun StockPromotionDataModel.appliesToTransaction(
    transactionTypeIndex: Int,
    quantityTotal: Double,
    nowMillis: Long = getCurrentTimeMillis()
): Boolean {
    if (!isActiveAt(nowMillis)) return false
    if (transactionTypeIndices.isNotEmpty() && transactionTypeIndex !in transactionTypeIndices) return false
    val minimum = minQuantity
    if (type != STOCK_PROMOTION_TYPE_RESTRICTION && minimum != null && quantityTotal + 0.000001 < minimum) return false
    return true
}

fun StockPromotionDataModel.violatesRestriction(
    transactionTypeIndex: Int,
    quantityTotal: Double,
    nowMillis: Long = getCurrentTimeMillis()
): Boolean {
    if (type != STOCK_PROMOTION_TYPE_RESTRICTION) return false
    if (!isActiveAt(nowMillis)) return false
    if (transactionTypeIndices.isNotEmpty() && transactionTypeIndex !in transactionTypeIndices) return false
    val minimum = minQuantity ?: return false
    return quantityTotal + 0.000001 < minimum
}

fun List<StockPromotionDataModel>.firstViolatedRestriction(
    transactionTypeIndex: Int,
    quantityTotal: Double,
    nowMillis: Long = getCurrentTimeMillis()
): StockPromotionDataModel? =
    firstOrNull { it.violatesRestriction(transactionTypeIndex, quantityTotal, nowMillis) }

private fun StockPromotionDataModel.applyToPrice(price: PriceDataModel): PriceDataModel? {
    if (type == STOCK_PROMOTION_TYPE_RESTRICTION) return null
    val current = price.price.toMoneyDouble()
    if (current < 0.0) return null

    val rawValue = value.toMoneyDouble()
    val promoted = when (type) {
        STOCK_PROMOTION_TYPE_SPECIAL_PRICE -> rawValue
        else -> when (mode) {
            STOCK_PROMOTION_MODE_FIXED -> current - rawValue
            STOCK_PROMOTION_MODE_PRICE -> rawValue
            else -> current * (1.0 - rawValue.coerceIn(0.0, 100.0) / 100.0)
        }
    }.coerceAtLeast(0.0).roundMoney()

    return price.withMoneyAmount(promoted)
}

fun List<StockPromotionDataModel>.bestPromotedPrice(
    originalPrice: PriceDataModel,
    transactionTypeIndex: Int,
    quantityTotal: Double,
    nowMillis: Long = getCurrentTimeMillis()
): PromotedPriceDataModel {
    val originalAmount = originalPrice.price.toMoneyDouble()
    val best = asSequence()
        .map { it.normalized() }
        .filter { it.type != STOCK_PROMOTION_TYPE_RESTRICTION }
        .filter { it.appliesToTransaction(transactionTypeIndex, quantityTotal, nowMillis) }
        .mapNotNull { promo -> promo.applyToPrice(originalPrice)?.let { promo to it } }
        .filter { (_, price) -> price.price.toMoneyDouble() + 0.000001 < originalAmount }
        .minByOrNull { (_, price) -> price.price.toMoneyDouble() }

    return best?.let { (promo, price) ->
        PromotedPriceDataModel(
            originalPrice = originalPrice,
            finalPrice = price,
            promotion = promo
        )
    } ?: PromotedPriceDataModel(originalPrice, originalPrice, null)
}

@kotlinx.serialization.Serializable
data class ReceiveSupplierOrderRequestDataModel(
    val orderId: String,
    val receivedLines: List<ReceiveSupplierOrderLineDataModel>
)

@kotlinx.serialization.Serializable
data class ReceiveSupplierOrderLineDataModel(
    val orderLineId: String,
    val goodsItemId: String,
    val receivedQuantity: QuantityDataModel,
    val actualSupplyPrice: PriceDataModel,
    val expirationDateMillis: Long? = null,
    val manufacturedAtMillis: Long? = null,
    val discounts: List<BatchDiscountDataModel> = emptyList(),
    val promotions: List<StockPromotionDataModel> = emptyList(),
    val notes: String? = null,
    val notesLocalized: List<LocalizedStringDataModel> = emptyList()
)

fun changeCartQuantity(
    id: String,
    transactionTypeIndex: Int,
    clientId: Int,
    current: QuantityDataModel,
    deltaSteps: Int
) {
    val step = current.pricedAmount.takeIf { it > 0.0 } ?: 1.0
    val nextTotal = current.total + step * deltaSteps

    if (deltaSteps < 0 && nextTotal < step - 0.000001) {
        return
    }

    setCartQuantity(
        id = id,
        transactionTypeIndex = transactionTypeIndex,
        clientId = clientId,
        current = current,
        total = nextTotal
    )
}

fun setCartQuantity(
    id: String,
    transactionTypeIndex: Int,
    clientId: Int,
    current: QuantityDataModel,
    total: Double
) {
    val nextTotal = current.withTotalValue(total).total

    val minimumTotal = current.pricedAmount
        .takeIf { it > 0.0 }
        ?: if (current.roundTotal) 1.0 else 0.001

    if (nextTotal + 0.000001 < minimumTotal) {
        return
    }

    upsertCart(
        id = id,
        transactionTypeIndex = transactionTypeIndex,
        clientId = clientId,
        quantity = current.copy(total = nextTotal)
    )
}

fun addGoodsItemToTransactionCart(
    goodsItem: GoodsItemDataModel,
    transactionTypeIndex: Int,
    clientId: Int,
    configuration: GlobalAppConfigurationDataModel,
    currentCart: List<GoodsItemInCartDataModel>,
    quantityToAdd: QuantityDataModel? = null
) {
    val existing = currentCart.find { it.id == goodsItem.id }
    val defaultQuantity = goodsItem.defaultCartQuantity(configuration)
    val deltaQuantity = quantityToAdd ?: defaultQuantity
    val normalizedDeltaQuantity = defaultQuantity.copy(
        total = defaultQuantity.withTotalValue(deltaQuantity.total).total,
        pricedAmount = deltaQuantity.pricedAmount.takeIf { it > 0.0 } ?: defaultQuantity.pricedAmount,
        roundTotal = deltaQuantity.roundTotal
    )

    if (normalizedDeltaQuantity.total <= 0.0)
        return

    if (existing == null) {
        upsertCart(
            id = goodsItem.id,
            transactionTypeIndex = transactionTypeIndex,
            clientId = clientId,
            quantity = normalizedDeltaQuantity
        )
    } else {
        upsertCart(
            id = goodsItem.id,
            transactionTypeIndex = transactionTypeIndex,
            clientId = clientId,
            quantity = existing.quantity.copy(total = existing.quantity.total + normalizedDeltaQuantity.total)
        )
    }
}

fun getTransactions(storeId: String) {
    if (!getTransactionsMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            getTransactionsMutex.withLock {
                val response = networkRequest<List<TransactionDataModel>, Unit>(
                    HttpMethod.Get,
                    endpointUrl = globalAppConfigurationState.payloadValue.getTransactionsPath.first,
                    headers = mapOf("store_id" to storeId)
                )

                if (response.negative) {
                    postInAppNotification(response.message, NotificationType.Negative)
                } else {
                    transactionsState.emit(DataState.Success(response.payload.orEmpty(), response.message))
                }
            }
        }
}

fun getUserFinanceDashboard(
    onCompleted: ((DataState<UserFinanceDashboardDataModel>) -> Unit)? = null
) {
    if (!getUserFinanceDashboardMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            getUserFinanceDashboardMutex.withLock {
                val response = networkRequest<UserFinanceDashboardDataModel, Unit>(
                    method = HttpMethod.Get,
                    endpointUrl = globalAppConfigurationState.payloadValue.getUserFinanceDashboardPath.first
                )

                if (response.negative || response.payload == null) {
                    if (!response.transportFailure) postInAppNotification(response.message, NotificationType.Negative)
                    onCompleted?.invoke(DataState.Empty(response.message))
                } else {
                    applyUserFinanceDashboard(response.payload, response.message)
                    onCompleted?.invoke(DataState.Success(response.payload, response.message))
                }
            }
        }
}

private suspend fun applyUserFinanceDashboard(
    payload: UserFinanceDashboardDataModel,
    message: List<LocalizedStringDataModel>? = null
) {
    userFinanceDashboardState.emit(DataState.Success(payload, message))
    userWalletState.emit(DataState.Success(payload.wallet, message))
    userWalletLedgerState.emit(DataState.Success(payload.ledger, message))
    paymentIntentsState.emit(DataState.Success(payload.paymentIntents, message))
    subscriptionPlansState.emit(DataState.Success(payload.subscriptionPlans, message))
}

fun createTopUpPayment(
    request: TopUpCreateRequestDataModel,
    onCompleted: ((DataState<TopUpPaymentIntentDataModel>) -> Unit)? = null
) {
    if (!createTopUpPaymentMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            createTopUpPaymentMutex.withLock {
                val response = networkRequest<TopUpPaymentIntentDataModel, TopUpCreateRequestDataModel>(
                    method = HttpMethod.Post,
                    endpointUrl = globalAppConfigurationState.payloadValue.createTopUpPaymentPath.first,
                    body = request
                )

                if (response.negative || response.payload == null) {
                    postInAppNotification(response.message, NotificationType.Negative)
                    onCompleted?.invoke(DataState.Empty(response.message))
                } else {
                    paymentIntentsState.emit(
                        DataState.Success(
                            paymentIntentsState.payloadValue.orEmpty().upsertById(response.payload) { it.id },
                            response.message
                        )
                    )
                    postInAppNotification(response.message, NotificationType.Positive)
                    onCompleted?.invoke(DataState.Success(response.payload, response.message))
                }
            }
        }
}

fun confirmDevelopmentTopUpPayment(
    paymentIntentId: String,
    onCompleted: ((DataState<UserFinanceDashboardDataModel>) -> Unit)? = null
) {
    if (!confirmDevelopmentTopUpMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            confirmDevelopmentTopUpMutex.withLock {
                val response = networkRequest<UserFinanceDashboardDataModel, TopUpConfirmDevelopmentRequestDataModel>(
                    method = HttpMethod.Post,
                    endpointUrl = globalAppConfigurationState.payloadValue.confirmDevelopmentTopUpPath.first,
                    body = TopUpConfirmDevelopmentRequestDataModel(paymentIntentId)
                )

                if (response.negative || response.payload == null) {
                    postInAppNotification(response.message, NotificationType.Negative)
                    onCompleted?.invoke(DataState.Empty(response.message))
                } else {
                    applyUserFinanceDashboard(response.payload, response.message)
                    postInAppNotification(response.message, NotificationType.Positive)
                    onCompleted?.invoke(DataState.Success(response.payload, response.message))
                }
            }
        }
}

fun getSubscriptionPlans(
    onCompleted: ((DataState<List<StoreSubscriptionPlanDataModel>>) -> Unit)? = null
) {
    if (!getSubscriptionPlansMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            getSubscriptionPlansMutex.withLock {
                val response = networkRequest<List<StoreSubscriptionPlanDataModel>, Unit>(
                    method = HttpMethod.Get,
                    endpointUrl = globalAppConfigurationState.payloadValue.getSubscriptionPlansPath.first
                )

                if (response.negative || response.payload == null) {
                    postInAppNotification(response.message, NotificationType.Negative)
                    onCompleted?.invoke(DataState.Empty(response.message))
                } else {
                    subscriptionPlansState.emit(DataState.Success(response.payload, response.message))
                    onCompleted?.invoke(DataState.Success(response.payload, response.message))
                }
            }
        }
}

fun getStoreSubscription(
    storeId: String,
    onCompleted: ((DataState<SubscriptionDashboardDataModel>) -> Unit)? = null
) {
    if (!getStoreSubscriptionMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            getStoreSubscriptionMutex.withLock {
                val response = networkRequest<SubscriptionDashboardDataModel, Unit>(
                    method = HttpMethod.Get,
                    endpointUrl = globalAppConfigurationState.payloadValue.getStoreSubscriptionPath.first,
                    headers = mapOf("store_id" to storeId)
                )

                if (response.negative || response.payload == null) {
                    postInAppNotification(response.message, NotificationType.Negative)
                    onCompleted?.invoke(DataState.Empty(response.message))
                } else {
                    activeStoreSubscriptionState.emit(DataState.Success(response.payload.subscription, response.message))
                    activeStoreSubscriptionChargesState.emit(DataState.Success(response.payload.charges, response.message))
                    subscriptionPlansState.emit(DataState.Success(response.payload.plans, response.message))
                    onCompleted?.invoke(DataState.Success(response.payload, response.message))
                }
            }
        }
}

fun updateStoreSubscription(
    request: StoreSubscriptionUpdateRequestDataModel,
    onCompleted: ((DataState<SubscriptionDashboardDataModel>) -> Unit)? = null
) {
    if (!updateStoreSubscriptionMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            updateStoreSubscriptionMutex.withLock {
                val response = networkRequest<SubscriptionDashboardDataModel, StoreSubscriptionUpdateRequestDataModel>(
                    method = HttpMethod.Post,
                    endpointUrl = globalAppConfigurationState.payloadValue.updateStoreSubscriptionPath.first,
                    body = request,
                    headers = mapOf("store_id" to request.storeId)
                )

                if (response.negative || response.payload == null) {
                    postInAppNotification(response.message, NotificationType.Negative)
                    onCompleted?.invoke(DataState.Empty(response.message))
                } else {
                    activeStoreSubscriptionState.emit(DataState.Success(response.payload.subscription, response.message))
                    activeStoreSubscriptionChargesState.emit(DataState.Success(response.payload.charges, response.message))
                    subscriptionPlansState.emit(DataState.Success(response.payload.plans, response.message))
                    getUserFinanceDashboard()
                    postInAppNotification(response.message, NotificationType.Positive)
                    onCompleted?.invoke(DataState.Success(response.payload, response.message))
                }
            }
        }
}

private suspend fun applyCashRegisterStatePayload(
    payload: CashRegisterStateDataModel,
    message: List<LocalizedStringDataModel>? = null
) {
    cashRegisterState.emit(DataState.Success(payload.register, message))
    cashRegisterEventsState.emit(DataState.Success(payload.events, message))
    cashRegisterAmountState.emit(payload.register.currentAmount)
    cashRegisterExtractionsState.emit(
        DataState.Success(
            payload.events
                .filter { it.type == CASH_REGISTER_EVENT_EXTRACTION }
                .map { it.toExtractionEntry() },
            message
        )
    )
}

fun getCashRegister(
    storeId: String,
    onCompleted: ((DataState<StoreCashRegisterDataModel>) -> Unit)? = null
) {
    if (!getCashRegisterMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            getCashRegisterMutex.withLock {
                val response = networkRequest<CashRegisterStateDataModel, Unit>(
                    method = HttpMethod.Get,
                    endpointUrl = globalAppConfigurationState.payloadValue.getCashRegisterPath.first,
                    headers = mapOf("store_id" to storeId)
                )

                if (response.negative || response.payload == null) {
                    postInAppNotification(response.message, NotificationType.Negative)
                    onCompleted?.invoke(DataState.Empty(response.message))
                } else {
                    applyCashRegisterStatePayload(response.payload, response.message)
                    onCompleted?.invoke(DataState.Success(response.payload.register, response.message))
                }
            }
        }
}

fun extractCashRegister(
    request: CashRegisterExtractionRequestDataModel,
    onCompleted: ((DataState<StoreCashRegisterDataModel>) -> Unit)? = null
) {
    if (!extractCashRegisterMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            extractCashRegisterMutex.withLock {
                val response = networkRequest<CashRegisterStateDataModel, CashRegisterExtractionRequestDataModel>(
                    method = HttpMethod.Post,
                    endpointUrl = globalAppConfigurationState.payloadValue.extractCashRegisterPath.first,
                    body = request,
                    headers = mapOf("store_id" to request.storeId)
                )

                if (response.negative || response.payload == null) {
                    postInAppNotification(response.message, NotificationType.Negative)
                    onCompleted?.invoke(DataState.Empty(response.message))
                } else {
                    applyCashRegisterStatePayload(response.payload, response.message)
                    postInAppNotification(response.message, NotificationType.Positive)
                    onCompleted?.invoke(DataState.Success(response.payload.register, response.message))
                }
            }
        }
}

fun getStoreWorkers(
    storeId: String,
    onCompleted: ((DataState<List<StoreWorkerDataModel>>) -> Unit)? = null
) {
    if (!getStoreWorkersMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            getStoreWorkersMutex.withLock {
                val response = networkRequest<List<StoreWorkerDataModel>, Unit>(
                    method = HttpMethod.Get,
                    endpointUrl = globalAppConfigurationState.payloadValue.getStoreWorkersPath.first,
                    headers = mapOf("store_id" to storeId)
                )

                if (response.negative || response.payload == null) {
                    postInAppNotification(response.message, NotificationType.Negative)
                    onCompleted?.invoke(DataState.Empty(response.message))
                } else {
                    storeWorkerMembershipsState.emit(DataState.Success(response.payload, response.message))
                    onCompleted?.invoke(DataState.Success(response.payload, response.message))
                }
            }
        }
}

fun getMyWorkerMemberships(
    onCompleted: ((DataState<List<StoreWorkerDataModel>>) -> Unit)? = null
) {
    if (!getMyWorkerMembershipsMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            getMyWorkerMembershipsMutex.withLock {
                val response = networkRequest<List<StoreWorkerDataModel>, Unit>(
                    method = HttpMethod.Get,
                    endpointUrl = globalAppConfigurationState.payloadValue.getMyWorkerMembershipsPath.first
                )

                if (response.negative || response.payload == null) {
                    postInAppNotification(response.message, NotificationType.Negative)
                    onCompleted?.invoke(DataState.Empty(response.message))
                } else {
                    myWorkerMembershipsState.emit(DataState.Success(response.payload, response.message))
                    onCompleted?.invoke(DataState.Success(response.payload, response.message))
                }
            }
        }
}

fun getIncomingWorkerRequests(
    storeId: String,
    onCompleted: ((DataState<List<StoreWorkerRequestDataModel>>) -> Unit)? = null
) {
    if (!getIncomingWorkerRequestsMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            getIncomingWorkerRequestsMutex.withLock {
                val response = networkRequest<List<StoreWorkerRequestDataModel>, Unit>(
                    method = HttpMethod.Get,
                    endpointUrl = globalAppConfigurationState.payloadValue.getIncomingWorkerRequestsPath.first,
                    headers = mapOf("store_id" to storeId)
                )

                if (response.negative || response.payload == null) {
                    postInAppNotification(response.message, NotificationType.Negative)
                    onCompleted?.invoke(DataState.Empty(response.message))
                } else {
                    incomingWorkerRequestsState.emit(DataState.Success(response.payload, response.message))
                    onCompleted?.invoke(DataState.Success(response.payload, response.message))
                }
            }
        }
}

fun getMyWorkerRequests(
    onCompleted: ((DataState<List<StoreWorkerRequestDataModel>>) -> Unit)? = null
) {
    if (!getMyWorkerRequestsMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            getMyWorkerRequestsMutex.withLock {
                val response = networkRequest<List<StoreWorkerRequestDataModel>, Unit>(
                    method = HttpMethod.Get,
                    endpointUrl = globalAppConfigurationState.payloadValue.getMyWorkerRequestsPath.first
                )

                if (response.negative || response.payload == null) {
                    postInAppNotification(response.message, NotificationType.Negative)
                    onCompleted?.invoke(DataState.Empty(response.message))
                } else {
                    myWorkerRequestsState.emit(DataState.Success(response.payload, response.message))
                    onCompleted?.invoke(DataState.Success(response.payload, response.message))
                }
            }
        }
}

fun getStoreWorkerRoleTemplates(
    storeId: String,
    onCompleted: ((DataState<List<StoreWorkerRoleTemplateDataModel>>) -> Unit)? = null
) {
    val cleanStoreId = storeId.trim()
    if (cleanStoreId.isBlank()) {
        onCompleted?.invoke(DataState.Empty())
        return
    }

    if (!getStoreWorkerRoleTemplatesMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            getStoreWorkerRoleTemplatesMutex.withLock {
                val response = networkRequest<List<StoreWorkerRoleTemplateDataModel>, Unit>(
                    method = HttpMethod.Get,
                    endpointUrl = globalAppConfigurationState.payloadValue.getStoreWorkerRoleTemplatesPath.first,
                    headers = mapOf("store_id" to cleanStoreId)
                )

                if (response.negative || response.payload == null) {
                    postInAppNotification(response.message, NotificationType.Negative)
                    onCompleted?.invoke(DataState.Empty(response.message))
                } else {
                    storeWorkerRoleTemplatesState.emit(DataState.Success(response.payload, response.message))
                    onCompleted?.invoke(DataState.Success(response.payload, response.message))
                }
            }
        }
}

fun upsertStoreWorkerRoleTemplate(
    storeId: String,
    templateId: String = "",
    name: List<LocalizedStringDataModel>,
    description: List<LocalizedStringDataModel> = emptyList(),
    permissions: List<String>,
    onCompleted: ((DataState<StoreWorkerRoleTemplateDataModel>) -> Unit)? = null
) {
    val cleanStoreId = storeId.trim()
    if (cleanStoreId.isBlank()) {
        onCompleted?.invoke(DataState.Empty())
        return
    }

    if (!upsertStoreWorkerRoleTemplateMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            upsertStoreWorkerRoleTemplateMutex.withLock {
                val response = networkRequest<StoreWorkerRoleTemplateDataModel, StoreWorkerRoleTemplateUpsertRequestDataModel>(
                    method = HttpMethod.Post,
                    endpointUrl = globalAppConfigurationState.payloadValue.upsertStoreWorkerRoleTemplatePath.first,
                    body = StoreWorkerRoleTemplateUpsertRequestDataModel(
                        id = templateId.trim(),
                        storeId = cleanStoreId,
                        name = name,
                        description = description,
                        permissions = permissions
                    ),
                    headers = mapOf("store_id" to cleanStoreId)
                )

                if (response.negative || response.payload == null) {
                    postInAppNotification(response.message, NotificationType.Negative)
                    onCompleted?.invoke(DataState.Empty(response.message))
                } else {
                    storeWorkerRoleTemplatesState.emit(
                        DataState.Success(
                            storeWorkerRoleTemplatesState.payloadValue.orEmpty().filterNot { it.id == response.payload.id } + response.payload,
                            response.message
                        )
                    )
                    getStoreWorkerRoleTemplates(cleanStoreId)
                    postInAppNotification(response.message, NotificationType.Positive)
                    onCompleted?.invoke(DataState.Success(response.payload, response.message))
                }
            }
        }
}

fun deleteStoreWorkerRoleTemplate(
    storeId: String,
    templateId: String,
    onCompleted: ((DataState<StoreWorkerRoleTemplateDataModel>) -> Unit)? = null
) {
    val cleanStoreId = storeId.trim()
    val cleanTemplateId = templateId.trim()
    if (cleanStoreId.isBlank() || cleanTemplateId.isBlank()) {
        onCompleted?.invoke(DataState.Empty())
        return
    }

    if (!deleteStoreWorkerRoleTemplateMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            deleteStoreWorkerRoleTemplateMutex.withLock {
                val response = networkRequest<StoreWorkerRoleTemplateDataModel, StoreWorkerRoleTemplateDeleteRequestDataModel>(
                    method = HttpMethod.Post,
                    endpointUrl = globalAppConfigurationState.payloadValue.deleteStoreWorkerRoleTemplatePath.first,
                    body = StoreWorkerRoleTemplateDeleteRequestDataModel(templateId = cleanTemplateId),
                    headers = mapOf("store_id" to cleanStoreId)
                )

                if (response.negative || response.payload == null) {
                    postInAppNotification(response.message, NotificationType.Negative)
                    onCompleted?.invoke(DataState.Empty(response.message))
                } else {
                    storeWorkerRoleTemplatesState.emit(
                        DataState.Success(
                            storeWorkerRoleTemplatesState.payloadValue.orEmpty().filterNot { it.id == response.payload.id },
                            response.message
                        )
                    )
                    getStoreWorkerRoleTemplates(cleanStoreId)
                    postInAppNotification(response.message, NotificationType.Positive)
                    onCompleted?.invoke(DataState.Success(response.payload, response.message))
                }
            }
        }
}

fun requestStoreEmployment(
    storeId: String,
    note: String? = null,
    onCompleted: ((DataState<StoreWorkerRequestDataModel>) -> Unit)? = null
) {
    if (!requestStoreEmploymentMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            requestStoreEmploymentMutex.withLock {
                val cleanNote = note?.trim()?.takeIf { it.isNotBlank() }
                val response = networkRequest<StoreWorkerRequestDataModel, WorkerEmploymentRequestCreateDataModel>(
                    method = HttpMethod.Post,
                    endpointUrl = globalAppConfigurationState.payloadValue.requestStoreEmploymentPath.first,
                    body = WorkerEmploymentRequestCreateDataModel(
                        storeId = storeId.trim(),
                        note = cleanNote,
                        noteLocalized = cleanNote.toLocalizedUserNote()
                    )
                )

                if (response.negative || response.payload == null) {
                    postInAppNotification(response.message, NotificationType.Negative)
                    onCompleted?.invoke(DataState.Empty(response.message))
                } else {
                    myWorkerRequestsState.emit(
                        DataState.Success(
                            myWorkerRequestsState.payloadValue.orEmpty().filterNot { it.id == response.payload.id } + response.payload,
                            response.message
                        )
                    )
                    getNotifications()
                    postInAppNotification(response.message, NotificationType.Positive)
                    onCompleted?.invoke(DataState.Success(response.payload, response.message))
                }
            }
        }
}

fun inviteStoreWorker(
    storeId: String,
    userId: String,
    roleId: String,
    permissions: List<String>,
    note: String? = null,
    workerPassword: String? = null,
    onCompleted: ((DataState<StoreWorkerRequestDataModel>) -> Unit)? = null
) {
    if (!inviteStoreWorkerMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            inviteStoreWorkerMutex.withLock {
                val cleanNote = note?.trim()?.takeIf { it.isNotBlank() }
                val response = networkRequest<StoreWorkerRequestDataModel, WorkerStoreInviteCreateDataModel>(
                    method = HttpMethod.Post,
                    endpointUrl = globalAppConfigurationState.payloadValue.inviteStoreWorkerPath.first,
                    body = WorkerStoreInviteCreateDataModel(
                        userId = userId.trim(),
                        roleId = roleId,
                        permissions = permissions,
                        note = cleanNote,
                        workerPassword = workerPassword,
                        noteLocalized = cleanNote.toLocalizedUserNote()
                    ),
                    headers = mapOf("store_id" to storeId)
                )

                if (response.negative || response.payload == null) {
                    postInAppNotification(response.message, NotificationType.Negative)
                    onCompleted?.invoke(DataState.Empty(response.message))
                } else {
                    incomingWorkerRequestsState.emit(
                        DataState.Success(
                            incomingWorkerRequestsState.payloadValue.orEmpty().filterNot { it.id == response.payload.id } + response.payload,
                            response.message
                        )
                    )
                    getNotifications()
                    postInAppNotification(response.message, NotificationType.Positive)
                    onCompleted?.invoke(DataState.Success(response.payload, response.message))
                }
            }
        }
}

fun acceptMyStoreWorkerInvitation(
    requestId: String,
    note: String? = null,
    onCompleted: ((DataState<StoreWorkerDataModel>) -> Unit)? = null
) {
    if (!decideStoreEmploymentMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            decideStoreEmploymentMutex.withLock {
                val response = networkRequest<StoreWorkerDataModel, WorkerStoreInvitationDecisionDataModel>(
                    method = HttpMethod.Post,
                    endpointUrl = globalAppConfigurationState.payloadValue.acceptStoreWorkerInvitationPath.first,
                    body = WorkerStoreInvitationDecisionDataModel(
                        requestId = requestId,
                        note = note?.trim()?.takeIf { it.isNotBlank() },
                        responseNote = note?.trim()?.takeIf { it.isNotBlank() },
                        responseNoteLocalized = note.toLocalizedUserNote()
                    )
                )

                if (response.negative || response.payload == null) {
                    postInAppNotification(response.message, NotificationType.Negative)
                    onCompleted?.invoke(DataState.Empty(response.message))
                } else {
                    myWorkerMembershipsState.emit(
                        DataState.Success(
                            myWorkerMembershipsState.payloadValue.orEmpty().filterNot { it.id == response.payload.id } + response.payload,
                            response.message
                        )
                    )
                    getMyWorkerRequests()
                    getMyWorkerMemberships()
                    getNotifications()
                    getStores()
                    postInAppNotification(response.message, NotificationType.Positive)
                    onCompleted?.invoke(DataState.Success(response.payload, response.message))
                }
            }
        }
}

fun declineMyStoreWorkerInvitation(
    requestId: String,
    note: String? = null,
    onCompleted: ((DataState<StoreWorkerRequestDataModel>) -> Unit)? = null
) {
    if (!decideStoreEmploymentMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            decideStoreEmploymentMutex.withLock {
                val response = networkRequest<StoreWorkerRequestDataModel, WorkerStoreInvitationDecisionDataModel>(
                    method = HttpMethod.Post,
                    endpointUrl = globalAppConfigurationState.payloadValue.declineStoreWorkerInvitationPath.first,
                    body = WorkerStoreInvitationDecisionDataModel(
                        requestId = requestId,
                        note = note?.trim()?.takeIf { it.isNotBlank() },
                        responseNote = note?.trim()?.takeIf { it.isNotBlank() },
                        responseNoteLocalized = note.toLocalizedUserNote()
                    )
                )

                if (response.negative || response.payload == null) {
                    postInAppNotification(response.message, NotificationType.Negative)
                    onCompleted?.invoke(DataState.Empty(response.message))
                } else {
                    getMyWorkerRequests()
                    getNotifications()
                    postInAppNotification(response.message, NotificationType.Positive)
                    onCompleted?.invoke(DataState.Success(response.payload, response.message))
                }
            }
        }
}

fun acceptStoreEmploymentRequest(
    storeId: String,
    requestId: String,
    roleId: String,
    permissions: List<String>,
    note: String? = null,
    workerPassword: String? = null,
    onCompleted: ((DataState<StoreWorkerDataModel>) -> Unit)? = null
) {
    if (!decideStoreEmploymentMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            decideStoreEmploymentMutex.withLock {
                val response = networkRequest<StoreWorkerDataModel, WorkerEmploymentDecisionRequestDataModel>(
                    method = HttpMethod.Post,
                    endpointUrl = globalAppConfigurationState.payloadValue.acceptStoreEmploymentPath.first,
                    body = WorkerEmploymentDecisionRequestDataModel(
                        requestId = requestId,
                        roleId = roleId,
                        permissions = permissions,
                        note = note?.trim()?.takeIf { it.isNotBlank() },
                        workerPassword = workerPassword,
                        responseNote = note?.trim()?.takeIf { it.isNotBlank() },
                        responseNoteLocalized = note.toLocalizedUserNote()
                    ),
                    headers = mapOf("store_id" to storeId)
                )

                if (response.negative || response.payload == null) {
                    postInAppNotification(response.message, NotificationType.Negative)
                    onCompleted?.invoke(DataState.Empty(response.message))
                } else {
                    storeWorkerMembershipsState.emit(
                        DataState.Success(
                            storeWorkerMembershipsState.payloadValue.orEmpty().filterNot { it.id == response.payload.id } + response.payload,
                            response.message
                        )
                    )
                    val realStoreId = response.payload.storeId.ifBlank { storeId }
                    val visibleStoreId = activeStoreIdState.value?.takeIf { it.isNotBlank() } ?: realStoreId
                    getIncomingWorkerRequests(visibleStoreId)
                    getStoreWorkers(visibleStoreId)
                    if (visibleStoreId != realStoreId) {
                        getIncomingWorkerRequests(realStoreId)
                        getStoreWorkers(realStoreId)
                    }
                    getMyWorkerRequests()
                    getNotifications()
                    getStores()
                    postInAppNotification(response.message, NotificationType.Positive)
                    onCompleted?.invoke(DataState.Success(response.payload, response.message))
                }
            }
        }
}

fun declineStoreEmploymentRequest(
    storeId: String,
    requestId: String,
    note: String? = null,
    onCompleted: ((DataState<StoreWorkerRequestDataModel>) -> Unit)? = null
) {
    if (!decideStoreEmploymentMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            decideStoreEmploymentMutex.withLock {
                val response = networkRequest<StoreWorkerRequestDataModel, WorkerEmploymentDecisionRequestDataModel>(
                    method = HttpMethod.Post,
                    endpointUrl = globalAppConfigurationState.payloadValue.declineStoreEmploymentPath.first,
                    body = WorkerEmploymentDecisionRequestDataModel(
                        requestId = requestId,
                        roleId = WORKER_ROLE_STANDARD,
                        permissions = emptyList(),
                        note = note?.trim()?.takeIf { it.isNotBlank() },
                        responseNote = note?.trim()?.takeIf { it.isNotBlank() },
                        responseNoteLocalized = note.toLocalizedUserNote()
                    ),
                    headers = mapOf("store_id" to storeId)
                )

                if (response.negative || response.payload == null) {
                    postInAppNotification(response.message, NotificationType.Negative)
                    onCompleted?.invoke(DataState.Empty(response.message))
                } else {
                    val realStoreId = response.payload.storeId.ifBlank { storeId }
                    val visibleStoreId = activeStoreIdState.value?.takeIf { it.isNotBlank() } ?: realStoreId
                    getIncomingWorkerRequests(visibleStoreId)
                    getStoreWorkers(visibleStoreId)
                    if (visibleStoreId != realStoreId) {
                        getIncomingWorkerRequests(realStoreId)
                    }
                    getMyWorkerRequests()
                    getNotifications()
                    postInAppNotification(response.message, NotificationType.Positive)
                    onCompleted?.invoke(DataState.Success(response.payload, response.message))
                }
            }
        }
}

fun updateStoreWorkerPermissions(
    storeId: String,
    workerId: String,
    roleId: String,
    permissions: List<String>,
    workerPassword: String? = null,
    onCompleted: ((DataState<StoreWorkerDataModel>) -> Unit)? = null
) {
    if (!updateStoreWorkerPermissionsMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            updateStoreWorkerPermissionsMutex.withLock {
                val response = networkRequest<StoreWorkerDataModel, WorkerPermissionsUpdateRequestDataModel>(
                    method = HttpMethod.Post,
                    endpointUrl = globalAppConfigurationState.payloadValue.updateStoreWorkerPermissionsPath.first,
                    body = WorkerPermissionsUpdateRequestDataModel(workerId, roleId, permissions, workerPassword),
                    headers = mapOf("store_id" to storeId)
                )

                if (response.negative || response.payload == null) {
                    postInAppNotification(response.message, NotificationType.Negative)
                    onCompleted?.invoke(DataState.Empty(response.message))
                } else {
                    storeWorkerMembershipsState.emit(
                        DataState.Success(
                            storeWorkerMembershipsState.payloadValue.orEmpty().filterNot { it.id == response.payload.id } + response.payload,
                            response.message
                        )
                    )
                    postInAppNotification(response.message, NotificationType.Positive)
                    onCompleted?.invoke(DataState.Success(response.payload, response.message))
                }
            }
        }
}

fun removeStoreWorker(
    storeId: String,
    workerId: String,
    note: String? = null,
    onCompleted: ((DataState<StoreWorkerRequestDataModel>) -> Unit)? = null
) {
    val cleanStoreId = storeId.trim()
    val cleanWorkerId = workerId.trim()
    if (cleanStoreId.isBlank() || cleanWorkerId.isBlank()) {
        val message = localizedStringResourceMessage(
            id = 13,
            main = "Not found",
            ru = "Не найдено",
            kk = "Табылмады"
        )
        onCompleted?.invoke(DataState.Empty(message))
        return
    }

    if (!removeStoreWorkerMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            removeStoreWorkerMutex.withLock {
                val cleanNote = note?.trim()?.takeIf { it.isNotBlank() }
                val response = networkRequest<StoreWorkerRequestDataModel, WorkerRemovalRequestDataModel>(
                    method = HttpMethod.Post,
                    endpointUrl = globalAppConfigurationState.payloadValue.removeStoreWorkerPath.first,
                    body = WorkerRemovalRequestDataModel(
                        workerId = cleanWorkerId,
                        note = cleanNote,
                        noteLocalized = cleanNote.toLocalizedUserNote()
                    ),
                    headers = mapOf("store_id" to cleanStoreId)
                )

                if (response.negative || response.payload == null) {
                    postInAppNotification(response.message, NotificationType.Negative)
                    onCompleted?.invoke(DataState.Empty(response.message))
                } else {
                    incomingWorkerRequestsState.emit(
                        DataState.Success(
                            incomingWorkerRequestsState.payloadValue.orEmpty().filterNot { it.id == response.payload.id } + response.payload,
                            response.message
                        )
                    )
                    getIncomingWorkerRequests(cleanStoreId)
                    getStoreWorkers(cleanStoreId)
                    getNotifications()
                    postInAppNotification(response.message, NotificationType.Positive)
                    onCompleted?.invoke(DataState.Success(response.payload, response.message))
                }
            }
        }
}

fun acceptMyStoreWorkerRemovalRequest(
    requestId: String,
    note: String? = null,
    onCompleted: ((DataState<StoreWorkerDataModel>) -> Unit)? = null
) {
    val cleanRequestId = requestId.trim()
    if (cleanRequestId.isBlank()) {
        val message = localizedStringResourceMessage(
            id = 13,
            main = "Not found",
            ru = "Не найдено",
            kk = "Табылмады"
        )
        onCompleted?.invoke(DataState.Empty(message))
        return
    }

    if (!decideStoreWorkerRemovalMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            decideStoreWorkerRemovalMutex.withLock {
                val cleanNote = note?.trim()?.takeIf { it.isNotBlank() }
                val response = networkRequest<StoreWorkerDataModel, WorkerRemovalDecisionRequestDataModel>(
                    method = HttpMethod.Post,
                    endpointUrl = globalAppConfigurationState.payloadValue.confirmStoreWorkerRemovalPath.first,
                    body = WorkerRemovalDecisionRequestDataModel(
                        requestId = cleanRequestId,
                        note = cleanNote,
                        responseNote = cleanNote,
                        responseNoteLocalized = cleanNote.toLocalizedUserNote()
                    )
                )

                if (response.negative || response.payload == null) {
                    postInAppNotification(response.message, NotificationType.Negative)
                    onCompleted?.invoke(DataState.Empty(response.message))
                } else {
                    val removedWorker = response.payload
                    myWorkerMembershipsState.emit(
                        DataState.Success(
                            myWorkerMembershipsState.payloadValue.orEmpty().filterNot { it.id == removedWorker.id },
                            response.message
                        )
                    )
                    storeWorkerMembershipsState.emit(
                        DataState.Success(
                            storeWorkerMembershipsState.payloadValue.orEmpty().filterNot { it.id == removedWorker.id },
                            response.message
                        )
                    )
                    getMyWorkerRequests()
                    getMyWorkerMemberships()
                    getNotifications()
                    getStores()
                    removedWorker.storeId.takeIf { it.isNotBlank() }?.let { storeId ->
                        getStoreWorkers(storeId)
                        getIncomingWorkerRequests(storeId)
                    }
                    postInAppNotification(response.message, NotificationType.Positive)
                    onCompleted?.invoke(DataState.Success(removedWorker, response.message))
                }
            }
        }
}


fun declineMyStoreWorkerRemovalRequest(
    requestId: String,
    note: String? = null,
    onCompleted: ((DataState<StoreWorkerRequestDataModel>) -> Unit)? = null
) {
    val cleanRequestId = requestId.trim()
    if (cleanRequestId.isBlank()) {
        val message = localizedStringResourceMessage(
            id = 13,
            main = "Not found",
            ru = "Не найдено",
            kk = "Табылмады"
        )
        onCompleted?.invoke(DataState.Empty(message))
        return
    }

    if (!decideStoreWorkerRemovalMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            decideStoreWorkerRemovalMutex.withLock {
                val response = networkRequest<StoreWorkerRequestDataModel, WorkerRemovalDecisionRequestDataModel>(
                    method = HttpMethod.Post,
                    endpointUrl = globalAppConfigurationState.payloadValue.declineStoreWorkerRemovalPath.first,
                    body = WorkerRemovalDecisionRequestDataModel(
                        requestId = cleanRequestId,
                        note = note?.trim()?.takeIf { it.isNotBlank() },
                        responseNote = note?.trim()?.takeIf { it.isNotBlank() },
                        responseNoteLocalized = note.toLocalizedUserNote()
                    )
                )

                if (response.negative || response.payload == null) {
                    postInAppNotification(response.message, NotificationType.Negative)
                    onCompleted?.invoke(DataState.Empty(response.message))
                } else {
                    myWorkerRequestsState.emit(
                        DataState.Success(
                            myWorkerRequestsState.payloadValue.orEmpty().filterNot { it.id == response.payload.id } + response.payload,
                            response.message
                        )
                    )
                    getMyWorkerRequests()
                    getMyWorkerMemberships()
                    getNotifications()
                    response.payload.storeId.takeIf { it.isNotBlank() }?.let { getStoreWorkers(it) }
                    postInAppNotification(response.message, NotificationType.Positive)
                    onCompleted?.invoke(DataState.Success(response.payload, response.message))
                }
            }
        }
}


fun updateMyWorkerPassword(
    workerId: String,
    workerPassword: String,
    accountPassword: String = "",
    onCompleted: ((DataState<StoreWorkerDataModel>) -> Unit)? = null
) {
    val cleanPassword = workerPassword.trim()
    val cleanAccountPassword = accountPassword.trim()
    if (!cleanPassword.checkAsPassword()) {
        val message = localizedStringResourceMessage(
            id = 13,
            main = "Password must be 8 or more symbols long and contain at least one digit and one special symbol",
            ru = "Пароль должен быть длиной 8 или более символов и содержать хотя бы одну цифру и один специальный символ",
            kk = "Құпия сөз ұзындығы 8 немесе одан да көп таңбадан тұруы және кемінде бір сан мен бір арнайы таңбадан тұруы керек"
        )
        postInAppNotification(message, NotificationType.Negative, transient = true)
        onCompleted?.invoke(DataState.Empty(message))
        return
    }

    if (cleanAccountPassword.isBlank()) {
        val message = localizedStringResourceMessage(
            id = 1145,
            main = "Account password is required to change the workshift password",
            ru = "Для изменения пароля смены нужен пароль аккаунта",
            kk = "Ауысым құпия сөзін өзгерту үшін аккаунт құпия сөзі қажет"
        )
        postInAppNotification(message, NotificationType.Negative, transient = true)
        onCompleted?.invoke(DataState.Empty(message))
        return
    }

    if (updateMyWorkerPasswordMutex.isLocked) {
        onCompleted?.invoke(
            DataState.Empty(
                localizedStringResourceMessage(
                    id = 1141,
                    main = "Please wait…",
                    ru = "Пожалуйста, подождите…",
                    kk = "Күте тұрыңыз…"
                )
            )
        )
        return
    }

    GlobalScope.launch(Dispatchers.ourIo) {
        updateMyWorkerPasswordMutex.withLock {
            val response = networkRequest<StoreWorkerDataModel, WorkerSelfPasswordUpdateRequestDataModel>(
                method = HttpMethod.Post,
                endpointUrl = globalAppConfigurationState.payloadValue.updateMyWorkerPasswordPath.first,
                body = WorkerSelfPasswordUpdateRequestDataModel(
                    workerId = workerId.trim(),
                    workerPassword = cleanPassword,
                    accountPassword = cleanAccountPassword
                )
            )

            if (response.negative || response.payload == null) {
                postInAppNotification(response.message, NotificationType.Negative)
                onCompleted?.invoke(DataState.Empty(response.message))
            } else {
                myWorkerMembershipsState.emit(
                    DataState.Success(
                        myWorkerMembershipsState.payloadValue.orEmpty().filterNot { it.id == response.payload.id } + response.payload,
                        response.message
                    )
                )
                getMyWorkerMemberships()
                postInAppNotification(response.message, NotificationType.Positive)
                onCompleted?.invoke(DataState.Success(response.payload, response.message))
            }
        }
    }
}


fun getOperationLogs(
    storeId: String,
    scope: String = OPERATION_LOG_SCOPE_CURRENT,
    onCompleted: ((DataState<List<OperationLogDataModel>>) -> Unit)? = null
) {
    if (!getOperationLogsMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            getOperationLogsMutex.withLock {
                val response = networkRequest<List<OperationLogDataModel>, Unit>(
                    method = HttpMethod.Get,
                    endpointUrl = globalAppConfigurationState.payloadValue.getOperationLogsPath.first,
                    headers = mapOf("store_id" to storeId),
                    query = mapOf("scope" to scope)
                )

                if (response.negative || response.payload == null) {
                    postInAppNotification(response.message, NotificationType.Negative)
                    onCompleted?.invoke(DataState.Empty(response.message))
                } else {
                    operationLogsState.emit(DataState.Success(response.payload, response.message))
                    onCompleted?.invoke(DataState.Success(response.payload, response.message))
                }
            }
        }
}

fun getStoreAnalytics(
    storeId: String,
    startMillis: Long = 0L,
    endMillisExclusive: Long = Long.MAX_VALUE,
    goodsItemIdFilter: String? = null,
    supplierIdFilter: String? = null,
    categoryIdFilter: String? = null,
    onCompleted: ((DataState<StoreAnalyticsDashboardDataModel>) -> Unit)? = null
) {
    if (!getStoreAnalyticsMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            getStoreAnalyticsMutex.withLock {
                val response = networkRequest<StoreAnalyticsDashboardDataModel, Unit>(
                    method = HttpMethod.Get,
                    endpointUrl = globalAppConfigurationState.payloadValue.getStoreAnalyticsPath.first,
                    headers = mapOf("store_id" to storeId),
                    query = buildMap<String, Any?> {
                        put("startMillis", startMillis)
                        put("endMillisExclusive", endMillisExclusive)
                        goodsItemIdFilter.cleanAnalyticsFilterId()?.let { put("goodsItemId", it) }
                        supplierIdFilter.cleanAnalyticsFilterId()?.let { put("supplierId", it) }
                        categoryIdFilter.cleanAnalyticsFilterId()?.let { put("categoryId", it) }
                    }
                )

                if (response.negative || response.payload == null) {
                    onCompleted?.invoke(DataState.Empty(response.message))
                } else {
                    storeAnalyticsDashboardState.emit(DataState.Success(response.payload, response.message))
                    onCompleted?.invoke(DataState.Success(response.payload, response.message))
                }
            }
        }
}

fun currentUserOwnsStore(storeId: String?): Boolean {
    val cleanStoreId = storeId?.takeIf { it.isNotBlank() } ?: return false
    val currentUserId = userAccountState.payloadValue?.id.orEmpty()
    if (currentUserId.isBlank()) return false
    val stores = storesState.payloadValue.orEmpty()
    val store = stores.findStoreOrBranch(cleanStoreId) ?: return false
    val rootStore = store.parentStoreId?.let { parentId -> stores.findStoreOrBranch(parentId) } ?: store
    return rootStore.userIds.contains(currentUserId)
}

fun activeStoreWorkerMembership(): StoreWorkerDataModel? {
    val activeStoreId = activeStoreIdState.value ?: return null
    val currentUserId = userAccountState.payloadValue?.id.orEmpty()
    if (currentUserId.isBlank()) return null
    val stores = storesState.payloadValue.orEmpty()
    val rootStoreId = stores.findStoreOrBranch(activeStoreId)?.parentStoreId ?: activeStoreId
    return myWorkerMembershipsState.payloadValue.orEmpty().firstOrNull {
        it.userId == currentUserId && it.isActive && (it.storeId == activeStoreId || it.storeId == rootStoreId)
    }
}

fun activeStoreRequiresWorkshift(): Boolean {
    val activeStoreId = activeStoreIdState.value ?: return false
    if (currentUserOwnsStore(activeStoreId)) return false
    return activeStoreWorkerMembership() != null
}

fun hasActiveWorkshiftForActiveStore(): Boolean {
    val activeStoreId = activeStoreIdState.value ?: return false
    val workshift = activeWorkshiftState.payloadValue ?: return false
    return workshift.isActive && workshift.endedAtMillis == null && workshift.storeId == activeStoreId
}

fun shouldBlockAppForWorkshift(): Boolean {
    return userAccountState.payloadValue != null && activeStoreRequiresWorkshift() && !hasActiveWorkshiftForActiveStore()
}

fun getCurrentWorkshift(
    storeId: String? = activeStoreIdState.value,
    onCompleted: ((DataState<WorkshiftDataModel>) -> Unit)? = null
) {
    val id = storeId?.takeIf { it.isNotBlank() } ?: return
    if (!getCurrentWorkshiftMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            getCurrentWorkshiftMutex.withLock {
                val response = networkRequest<WorkshiftDataModel, Unit>(
                    endpointUrl = globalAppConfigurationState.payloadValue.getCurrentWorkshiftPath.first,
                    method = HttpMethod.Get,
                    headers = mapOf("store_id" to id)
                )

                if (response.negative || response.payload == null) {
                    if (!response.transportFailure && response.httpStatusCode != HttpStatusCode.Unauthorized.value) {
                        activeWorkshiftState.emit(DataState.Empty(response.message))
                    }
                    onCompleted?.invoke(DataState.Empty(response.message))
                } else {
                    activeWorkshiftState.emit(DataState.Success(response.payload, response.message))
                    onCompleted?.invoke(DataState.Success(response.payload, response.message))
                }
            }
        }
}

fun startWorkshift(
    storeId: String,
    workerIdentifier: String,
    password: String,
    onCompleted: ((DataState<WorkshiftDataModel>) -> Unit)? = null
) {
    if (!startWorkshiftMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            startWorkshiftMutex.withLock {
                workshiftLoginInProgressState.emit(true)
                try {
                    val response = networkRequest<WorkshiftDataModel, WorkshiftStartRequestDataModel>(
                        method = HttpMethod.Post,
                        endpointUrl = globalAppConfigurationState.payloadValue.startWorkshiftPath.first,
                        body = WorkshiftStartRequestDataModel(workerIdentifier.trim(), password.trim()),
                        headers = mapOf("store_id" to storeId)
                    )

                    if (response.negative || response.payload == null) {
                        postInAppNotification(response.message, NotificationType.Negative)
                        if (!response.transportFailure && response.httpStatusCode != HttpStatusCode.Unauthorized.value) {
                            activeWorkshiftState.emit(DataState.Empty(response.message))
                        }
                        onCompleted?.invoke(DataState.Empty(response.message))
                    } else {
                        activeWorkshiftState.emit(DataState.Success(response.payload, response.message))
                        postInAppNotification(response.message, NotificationType.Positive)
                        onCompleted?.invoke(DataState.Success(response.payload, response.message))
                    }
                } finally {
                    workshiftLoginInProgressState.emit(false)
                }
            }
        }
}

fun endCurrentWorkshift(
    storeId: String? = activeStoreIdState.value,
    onCompleted: ((DataState<WorkshiftDataModel>) -> Unit)? = null
) {
    val id = storeId?.takeIf { it.isNotBlank() } ?: return
    if (!endWorkshiftMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            endWorkshiftMutex.withLock {
                val activeWorkshift = activeWorkshiftState.payloadValue
                    ?.takeIf { it.storeId == id && it.isActive && it.endedAtMillis == null }
                val endedAtMillis = getCurrentTimeMillis()
                val operationId = activeWorkshift?.let { workshift ->
                    createClientOperationId(
                        prefix = "wse",
                        seed = listOf(workshift.id, workshift.storeId, endedAtMillis.toString()).joinToString(":")
                    )
                }.orEmpty()

                val response = networkRequest<WorkshiftDataModel, WorkshiftEndRequestDataModel>(
                    method = HttpMethod.Post,
                    endpointUrl = globalAppConfigurationState.payloadValue.endWorkshiftPath.first,
                    headers = mapOf("store_id" to id),
                    body = WorkshiftEndRequestDataModel(
                        workshiftId = activeWorkshift?.id,
                        endedAtMillis = endedAtMillis,
                        clientOperationId = operationId,
                        deviceInfo = buildCurrentClientDeviceInfo()
                    )
                )

                if (response.negative || response.payload == null) {
                    val shouldQueueWorkshiftEnd = response.transportFailure ||
                            response.httpStatusCode == HttpStatusCode.Unauthorized.value ||
                            response.httpStatusCode == HttpStatusCode.ServiceUnavailable.value ||
                            (response.httpStatusCode ?: 0) >= 500

                    if (shouldQueueWorkshiftEnd && activeWorkshift != null) {
                        val ended = endWorkshiftLocallyAndQueue(
                            workshift = activeWorkshift,
                            endedAtMillis = endedAtMillis,
                            clientOperationId = operationId
                        )
                        onCompleted?.invoke(DataState.Success(ended, pendingWorkshiftEndMessage()))
                        return@withLock
                    }

                    postInAppNotification(response.message, NotificationType.Negative)
                    onCompleted?.invoke(DataState.Empty(response.message))
                } else {
                    activeWorkshiftState.emit(DataState.Empty(response.message))
                    dropPendingWorkshiftEnd(operationId)
                    postInAppNotification(response.message, NotificationType.Positive)
                    onCompleted?.invoke(DataState.Success(response.payload, response.message))
                }
            }
        }
}


fun currentUserStorePermissions(storeId: String?): Set<String> {
    val cleanStoreId = storeId?.takeIf { it.isNotBlank() } ?: return emptySet()
    val currentUserId = userAccountState.payloadValue?.id.orEmpty()
    if (currentUserId.isBlank()) return emptySet()

    val stores = storesState.payloadValue.orEmpty()
    val cleanRootStoreId = stores.findStoreOrBranch(cleanStoreId)?.parentStoreId ?: cleanStoreId

    if (currentUserOwnsStore(cleanStoreId)) return ALL_STORE_PERMISSION_IDS.toSet()

    return normalizeStorePermissionIds(
        myWorkerMembershipsState.payloadValue.orEmpty()
            .filter { it.userId == currentUserId && it.isActive && (it.storeId == cleanStoreId || it.storeId == cleanRootStoreId) }
            .flatMap { it.permissions }
    ).toSet()
}

fun currentUserAssignableStorePermissions(storeId: String?): Set<String> {
    val permissions = currentUserStorePermissions(storeId)
    return permissions.takeIf {
        STORE_PERMISSION_WORKERS_INVITE in it ||
                STORE_PERMISSION_WORKERS_DECIDE_REQUESTS in it ||
                STORE_PERMISSION_WORKERS_EDIT_PERMISSIONS in it ||
                STORE_PERMISSION_WORKER_ROLE_TEMPLATES_MANAGE in it
    }.orEmpty()
}

fun currentUserHasStorePermission(storeId: String?, permission: String): Boolean {
    val permissions = currentUserStorePermissions(storeId)
    if (permission in permissions) return true
    val legacyExpansion = when (permission) {
        STORE_PERMISSION_STOCK_WRITE -> STORE_PERMISSION_LEGACY_EXPANSIONS[permission].orEmpty().filterNot { it == STORE_PERMISSION_STOCK_READ }
        STORE_PERMISSION_WORKERS_MANAGE -> STORE_PERMISSION_LEGACY_EXPANSIONS[permission].orEmpty().filterNot { it == STORE_PERMISSION_WORKERS_VIEW }
        else -> emptyList()
    }
    return legacyExpansion.any { it in permissions }
}

fun currentUserCanExtractCashRegister(storeId: String?): Boolean {
    return currentUserHasStorePermission(storeId, STORE_PERMISSION_CASH_REGISTER_EXTRACT)
}

fun currentUserCanViewLogs(storeId: String?): Boolean {
    return currentUserOwnsStore(storeId) || currentUserHasStorePermission(storeId, STORE_PERMISSION_LOGS_VIEW)
}

fun currentUserCanViewWorkers(storeId: String?): Boolean {
    return currentUserOwnsStore(storeId) || currentUserHasStorePermission(storeId, STORE_PERMISSION_WORKERS_VIEW)
}

fun currentUserCanInviteWorkers(storeId: String?): Boolean {
    return currentUserOwnsStore(storeId) || currentUserHasStorePermission(storeId, STORE_PERMISSION_WORKERS_INVITE)
}

fun currentUserCanDecideWorkerRequests(storeId: String?): Boolean {
    return currentUserOwnsStore(storeId) || currentUserHasStorePermission(storeId, STORE_PERMISSION_WORKERS_DECIDE_REQUESTS)
}

fun currentUserCanEditWorkerPermissions(storeId: String?): Boolean {
    return currentUserOwnsStore(storeId) || currentUserHasStorePermission(storeId, STORE_PERMISSION_WORKERS_EDIT_PERMISSIONS)
}

fun currentUserCanRemoveWorkers(storeId: String?): Boolean {
    return currentUserOwnsStore(storeId) || currentUserHasStorePermission(storeId, STORE_PERMISSION_WORKERS_REMOVE)
}

fun currentUserCanManageWorkerRoleTemplates(storeId: String?): Boolean {
    return currentUserOwnsStore(storeId) || currentUserHasStorePermission(storeId, STORE_PERMISSION_WORKER_ROLE_TEMPLATES_MANAGE)
}

fun currentUserCanManageWorkers(storeId: String?): Boolean {
    return currentUserCanInviteWorkers(storeId) || currentUserCanDecideWorkerRequests(storeId) || currentUserCanEditWorkerPermissions(storeId) || currentUserCanRemoveWorkers(storeId) || currentUserCanManageWorkerRoleTemplates(storeId)
}

fun currentUserCanViewAnalytics(storeId: String?): Boolean {
    return currentUserOwnsStore(storeId) || currentUserHasStorePermission(storeId, STORE_PERMISSION_ANALYTICS_VIEW)
}

fun currentUserCanViewCashRegister(storeId: String?): Boolean {
    return currentUserOwnsStore(storeId) || currentUserHasStorePermission(storeId, STORE_PERMISSION_CASH_REGISTER_VIEW)
}

fun currentUserCanViewStock(storeId: String?): Boolean {
    return currentUserOwnsStore(storeId) || currentUserHasStorePermission(storeId, STORE_PERMISSION_STOCK_READ)
}

fun currentUserCanEditStock(storeId: String?): Boolean {
    return currentUserOwnsStore(storeId) || listOf(
        STORE_PERMISSION_STOCK_ITEM_CREATE,
        STORE_PERMISSION_STOCK_ITEM_EDIT,
        STORE_PERMISSION_STOCK_ITEM_DELETE,
        STORE_PERMISSION_STOCK_BATCH_CREATE,
        STORE_PERMISSION_STOCK_BATCH_EDIT,
        STORE_PERMISSION_STOCK_BATCH_DELETE,
        STORE_PERMISSION_STOCK_BATCH_MOVE,
        STORE_PERMISSION_STOCK_BATCH_TRANSFER_DECIDE,
        STORE_PERMISSION_STOCK_BATCH_SET_ACTIVE_SHELF,
        STORE_PERMISSION_STOCK_PROMOTIONS_MANAGE
    ).any { currentUserHasStorePermission(storeId, it) }
}

fun currentUserCanViewSuppliers(storeId: String?): Boolean {
    return currentUserOwnsStore(storeId) || currentUserHasStorePermission(storeId, STORE_PERMISSION_SUPPLIERS_VIEW)
}

fun currentUserCanManageSuppliers(storeId: String?): Boolean {
    return currentUserOwnsStore(storeId) || currentUserHasStorePermission(storeId, STORE_PERMISSION_SUPPLIERS_MANAGE)
}

fun currentUserCanViewSupplierOrders(storeId: String?): Boolean {
    return currentUserOwnsStore(storeId) || currentUserHasStorePermission(storeId, STORE_PERMISSION_SUPPLIER_ORDERS_VIEW)
}

fun currentUserCanManageSupplierOrders(storeId: String?): Boolean {
    return currentUserOwnsStore(storeId) || currentUserHasStorePermission(storeId, STORE_PERMISSION_SUPPLIER_ORDERS_MANAGE)
}

fun currentUserCanReceiveSupplierOrders(storeId: String?): Boolean {
    return currentUserOwnsStore(storeId) || currentUserHasStorePermission(storeId, STORE_PERMISSION_SUPPLIER_ORDERS_RECEIVE)
}

fun currentUserCanViewDebtors(storeId: String?): Boolean {
    return currentUserOwnsStore(storeId) || currentUserHasStorePermission(storeId, STORE_PERMISSION_DEBTORS_VIEW)
}

fun currentUserCanManageDebtors(storeId: String?): Boolean {
    return currentUserOwnsStore(storeId) || currentUserHasStorePermission(storeId, STORE_PERMISSION_DEBTORS_MANAGE)
}

fun currentUserCanManageDebtorPayments(storeId: String?): Boolean {
    return currentUserOwnsStore(storeId) || currentUserHasStorePermission(storeId, STORE_PERMISSION_DEBTOR_PAYMENTS_MANAGE)
}

fun currentUserCanViewTransactionHistory(storeId: String?): Boolean {
    return currentUserOwnsStore(storeId) || currentUserHasStorePermission(storeId, STORE_PERMISSION_TRANSACTION_HISTORY_VIEW)
}

fun currentUserCanUseTransactionType(storeId: String?, transactionTypeIndex: Int): Boolean {
    val permission = when (transactionTypeIndex) {
        0 -> STORE_PERMISSION_SALE_TRANSACTION
        1 -> STORE_PERMISSION_RETURN_TRANSACTION
        2 -> STORE_PERMISSION_SUPPLY_TRANSACTION
        else -> null
    } ?: return true
    return currentUserOwnsStore(storeId) || currentUserHasStorePermission(storeId, permission)
}

fun currentUserPermissionDeniedMessage(): List<LocalizedStringDataModel> = localizedStringResourceMessage(
    id = 665,
    main = "You do not have permission for this action",
    ru = "У вас нет прав для этого действия",
    kk = "Бұл әрекетке рұқсатыңыз жоқ"
)

fun completeTransaction(
    transaction: TransactionDataModel,
    transactionTypeIndex: Int,
    clientId: Int,
    receiptSnapshot: TransactionReceiptSnapshotDataModel,
    onCompleted: (() -> Unit)? = null
) {
    if (!completeTransactionMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            completeTransactionInProgressState.emit(true)
            try {
                completeTransactionMutex.withLock {
                    val transactionWithOperationId = transaction.withClientOperationId()
                    postInAppNotification(
                        localizedStringResourceMessage(
                            id = 224,
                            main = "Completing transaction",
                            ru = "Завершение операции",
                            kk = "Операция аяқталуда"
                        ),
                        NotificationType.Neutral,
                        transient = true
                    )

                    val response = networkRequest<TransactionDataModel, TransactionDataModel>(
                        method = HttpMethod.Post,
                        endpointUrl = globalAppConfigurationState.payloadValue.completeTransactionPath.first,
                        headers = transactionWithOperationId.storeId.takeIf { it.isNotBlank() }?.let { mapOf("store_id" to it) } ?: emptyMap(),
                        body = transactionWithOperationId
                    )

                    if (response.negative || response.payload == null) {
                        val shouldQueueForCloudRetry = response.transportFailure ||
                                response.httpStatusCode == HttpStatusCode.Unauthorized.value ||
                                response.httpStatusCode == HttpStatusCode.ServiceUnavailable.value ||
                                (response.httpStatusCode ?: 0) >= 500

                        if (shouldQueueForCloudRetry) {
                            val localCompleted = queueTransactionThroughLocalNetwork(transactionWithOperationId)
                            if (localCompleted != null) {
                                latestTransactionReceiptSnapshotState.emit(receiptSnapshot.copy(transaction = localCompleted))
                                deleteCart(transactionTypeIndex, clientId)
                                clearTransactionPaymentDraft(transactionTypeIndex, clientId)
                                postInAppNotification(
                                    localNetworkMessage(
                                        id = 733,
                                        main = "Queued locally for cloud sync",
                                        ru = "Сохранено локально для синхронизации",
                                        kk = "Бұлтпен синхрондау үшін жергілікті сақталды"
                                    ),
                                    NotificationType.Positive
                                )
                                onCompleted?.invoke()
                                return@withLock
                            }
                        }

                        postInAppNotification(response.message, NotificationType.Negative)
                        return@withLock
                    }

                    val completed = response.payload

                    latestTransactionReceiptSnapshotState.emit(
                        receiptSnapshot.copy(transaction = completed)
                    )

                    transactionsState.emit(
                        DataState.Success(
                            mutableListOf<TransactionDataModel>().apply {
                                transactionsState.payloadValue?.let { addAll(it) }
                                add(completed)
                            }.distinctBy { transactionItem -> transactionItem.clientOperationId.ifBlank { transactionItem.id } },
                            response.message
                        )
                    )

                    completed.debtor?.let { debtor ->
                        debtorsState.emit(
                            DataState.Success(
                                debtorsState.payloadValue.orEmpty().upsertDebtor(debtor),
                                response.message
                            )
                        )
                    }

                    deleteCart(transactionTypeIndex, clientId)
                    clearTransactionPaymentDraft(transactionTypeIndex, clientId)

                    activeStoreIdState.value?.let {
                        getStock(it)
                        getStockBatches(it)
                        getTransactions(it)
                        getCashRegister(it)
                    }

                    postInAppNotification(response.message, NotificationType.Positive)
                    onCompleted?.invoke()
                }
            } finally {
                completeTransactionInProgressState.emit(false)
            }
        }
    else
        postInAppNotification(
            localizedStringResourceMessage(
                id = 224,
                main = "Completing transaction",
                ru = "Завершение операции",
                kk = "Операция аяқталуда"
            ),
            NotificationType.Neutral,
            transient = true
        )
}

expect val Dispatchers.ourIo: CoroutineDispatcher

expect var getStoredUserAuthTokens: (() -> TokenPair?)?
expect var setStoredUserAuthTokens: ((TokenPair?) -> Unit)?

expect var getStoredUserAccountDataModel: (() -> UserAccountDataModel?)?
expect var setStoredUserAccountDataModel: ((UserAccountDataModel?) -> Unit)?

expect var cacheDirPath: String
expect var getHttpClientEngine: () -> HttpClientEngine

expect var getSystemLocaleLanguage: () -> String

expect var getPlatformName: () -> String

var getClientDeviceInfo: (() -> ClientDeviceInfoDataModel?)? = null

var setClipboardText: ((String) -> Unit)? = null

var openSystemDevicesSettings: (suspend () -> ReceiptPlatformActionResult)? = null

fun openPlatformDevicesSettings() {
    GlobalScope.launch(Dispatchers.ourIo) {
        val result = runCatching {
            openSystemDevicesSettings?.invoke()
                ?: ReceiptPlatformActionResult(false, "Device settings are not configured on this platform")
        }.getOrElse {
            ReceiptPlatformActionResult(false, it.message ?: "Could not open device settings")
        }

        val successMessage = listOf(
            LocalizedStringDataModel("main", "Device settings opened"),
            LocalizedStringDataModel("en", "Device settings opened"),
            LocalizedStringDataModel("ru", "Настройки устройств открыты"),
            LocalizedStringDataModel("kk", "Құрылғы баптаулары ашылды")
        ).extractLocalizedString(appLanguageState.value) ?: "Device settings opened"

        val errorMessage = listOf(
            LocalizedStringDataModel("main", "Could not open device settings"),
            LocalizedStringDataModel("en", "Could not open device settings"),
            LocalizedStringDataModel("ru", "Не удалось открыть настройки устройств"),
            LocalizedStringDataModel("kk", "Құрылғы баптауларын ашу мүмкін болмады")
        ).extractLocalizedString(appLanguageState.value) ?: "Could not open device settings"

        postInAppNotification(
            message = if (result.success) successMessage else errorMessage,
            type = if (result.success) NotificationType.Positive else NotificationType.Negative,
            transient = true
        )
    }
}

fun copyTextToClipboard(text: String, label: String = "AITA") {
    if (text.isBlank()) return

    runCatching { setClipboardText?.invoke(text) }
        .onSuccess {
            postInAppNotification(
                localizedStringResourceMessage(
                    id = 501,
                    main = "Copied to clipboard",
                    ru = "Скопировано в буфер обмена",
                    kk = "Алмасу буферіне көшірілді"
                ),
                NotificationType.Positive,
                transient = true
            )
        }
        .onFailure { throwable ->
            postInAppNotification(
                listOf(
                    LocalizedStringDataModel("main", throwable.message ?: "Could not copy"),
                    LocalizedStringDataModel("en", throwable.message ?: "Could not copy"),
                    LocalizedStringDataModel("ru", "Не удалось скопировать"),
                    LocalizedStringDataModel("kk", "Көшіру мүмкін болмады")
                ),
                NotificationType.Negative,
                transient = true
            )
        }
}

expect var getSqlDelightDriver: (() -> SqlDriver?)?

expect object LocalAitaLanTransport {
    fun start(
        deviceId: String,
        tcpPort: Int,
        discoveryPort: Int,
        onMessage: suspend (message: String, senderHost: String) -> String
    ): Boolean

    fun stop()
    fun broadcast(message: String, discoveryPort: Int)
    suspend fun send(host: String, port: Int, message: String, timeoutMillis: Int = 2500): String?
    fun localHostAddress(): String
}

val supplierGoodsPricesState =
    MutableDataStateFlow<List<SupplierGoodsPriceDataModel>>(GlobalScope)

val supplierOrdersState =
    MutableDataStateFlow<List<SupplierOrderDataModel>>(GlobalScope)

val supplierOrderLinesState =
    MutableDataStateFlow<List<SupplierOrderLineDataModel>>(GlobalScope)

private val getSupplierGoodsPricesMutex = Mutex()
private val upsertSupplierGoodsPriceMutex = Mutex()

private val getSupplierOrdersMutex = Mutex()
private val addSupplierOrderMutex = Mutex()
private val updateSupplierOrderMutex = Mutex()
private val deleteSupplierOrderMutex = Mutex()
private val receiveSupplierOrderMutex = Mutex()

val securitySessionsState = MutableDataStateFlow<List<SecuritySessionDataModel>>(GlobalScope)
val securitySessionHistoryState = MutableDataStateFlow<List<SecuritySessionHistoryDataModel>>(GlobalScope)
private val getSecuritySessionsMutex = Mutex()
private val getSecuritySessionHistoryMutex = Mutex()
private val revokeSecuritySessionMutex = Mutex()
private val revokeOtherSecuritySessionsMutex = Mutex()

fun getSupplierGoodsPrices(
    storeId: String,
    onCompleted: ((DataState<List<SupplierGoodsPriceDataModel>>) -> Unit)? = null
) {
    if (!getSupplierGoodsPricesMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            getSupplierGoodsPricesMutex.withLock {
                val response = networkRequest<List<SupplierGoodsPriceDataModel>, Unit>(
                    method = HttpMethod.Get,
                    endpointUrl = globalAppConfigurationState.payloadValue.getSupplierGoodsPricesPath.first,
                    headers = mapOf("store_id" to storeId)
                )

                if (response.negative || response.payload == null) {
                    postInAppNotification(response.message, NotificationType.Negative)
                    onCompleted?.invoke(DataState.Empty(response.message))
                } else {
                    supplierGoodsPricesState.emit(DataState.Success(response.payload, response.message))
                    onCompleted?.invoke(DataState.Success(response.payload, response.message))
                }
            }
        }
}

fun upsertSupplierGoodsPrice(
    price: SupplierGoodsPriceDataModel,
    onCompleted: ((DataState<SupplierGoodsPriceDataModel>) -> Unit)? = null
) {
    if (!upsertSupplierGoodsPriceMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            upsertSupplierGoodsPriceMutex.withLock {
                val response = networkRequest<SupplierGoodsPriceDataModel, SupplierGoodsPriceDataModel>(
                    method = HttpMethod.Post,
                    endpointUrl = globalAppConfigurationState.payloadValue.upsertSupplierGoodsPricePath.first,
                    body = price
                )

                if (response.negative || response.payload == null) {
                    postInAppNotification(response.message, NotificationType.Negative)
                    onCompleted?.invoke(DataState.Empty(response.message))
                } else {
                    supplierGoodsPricesState.emit(
                        DataState.Success(
                            supplierGoodsPricesState.payloadValue
                                .orEmpty()
                                .upsertById(response.payload),
                            response.message
                        )
                    )
                    onCompleted?.invoke(DataState.Success(response.payload, response.message))
                }
            }
        }
}

@kotlinx.serialization.Serializable
data class SupplierOrderWithLinesDataModel(
    val order: SupplierOrderDataModel,
    val lines: List<SupplierOrderLineDataModel>
)

fun getSupplierOrders(
    storeId: String,
    onCompleted: ((DataState<List<SupplierOrderWithLinesDataModel>>) -> Unit)? = null
) {
    if (!getSupplierOrdersMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            getSupplierOrdersMutex.withLock {
                val response = networkRequest<List<SupplierOrderWithLinesDataModel>, Unit>(
                    method = HttpMethod.Get,
                    endpointUrl = globalAppConfigurationState.payloadValue.getSupplierOrdersPath.first,
                    headers = mapOf("store_id" to storeId)
                )

                if (response.negative || response.payload == null) {
                    postInAppNotification(response.message, NotificationType.Negative)
                    onCompleted?.invoke(DataState.Empty(response.message))
                } else {
                    supplierOrdersState.emit(
                        DataState.Success(response.payload.map { it.order }, response.message)
                    )
                    supplierOrderLinesState.emit(
                        DataState.Success(response.payload.flatMap { it.lines }, response.message)
                    )
                    onCompleted?.invoke(DataState.Success(response.payload, response.message))
                }
            }
        }
}

fun getSupplierOrdersForSupplier(
    supplierId: String,
    onCompleted: ((DataState<List<SupplierOrderWithLinesDataModel>>) -> Unit)? = null
) {
    if (!getSupplierOrdersMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            getSupplierOrdersMutex.withLock {
                val response = networkRequest<List<SupplierOrderWithLinesDataModel>, Unit>(
                    method = HttpMethod.Get,
                    endpointUrl = globalAppConfigurationState.payloadValue.getSupplierOrdersPath.first,
                    headers = mapOf("supplier_id" to supplierId)
                )

                if (response.negative || response.payload == null) {
                    postInAppNotification(response.message, NotificationType.Negative)
                    onCompleted?.invoke(DataState.Empty(response.message))
                } else {
                    supplierOrdersState.emit(
                        DataState.Success(response.payload.map { it.order }, response.message)
                    )
                    supplierOrderLinesState.emit(
                        DataState.Success(response.payload.flatMap { it.lines }, response.message)
                    )
                    onCompleted?.invoke(DataState.Success(response.payload, response.message))
                }
            }
        }
}

fun getMySupplierSideOrders(
    onCompleted: ((DataState<List<SupplierOrderWithLinesDataModel>>) -> Unit)? = null
) {
    if (!getSupplierOrdersMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            getSupplierOrdersMutex.withLock {
                val response = networkRequest<List<SupplierOrderWithLinesDataModel>, Unit>(
                    method = HttpMethod.Get,
                    endpointUrl = globalAppConfigurationState.payloadValue.getSupplierOrdersPath.first
                )

                if (response.negative || response.payload == null) {
                    postInAppNotification(response.message, NotificationType.Negative)
                    onCompleted?.invoke(DataState.Empty(response.message))
                } else {
                    supplierOrdersState.emit(
                        DataState.Success(response.payload.map { it.order }, response.message)
                    )
                    supplierOrderLinesState.emit(
                        DataState.Success(response.payload.flatMap { it.lines }, response.message)
                    )
                    onCompleted?.invoke(DataState.Success(response.payload, response.message))
                }
            }
        }
}

fun addSupplierOrder(
    orderWithLines: SupplierOrderWithLinesDataModel,
    onCompleted: ((DataState<SupplierOrderWithLinesDataModel>) -> Unit)? = null
) {
    if (!addSupplierOrderMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            addSupplierOrderMutex.withLock {
                val response = networkRequest<SupplierOrderWithLinesDataModel, SupplierOrderWithLinesDataModel>(
                    method = HttpMethod.Post,
                    endpointUrl = globalAppConfigurationState.payloadValue.addSupplierOrderPath.first,
                    body = orderWithLines
                )

                if (response.negative || response.payload == null) {
                    postInAppNotification(response.message, NotificationType.Negative)
                    onCompleted?.invoke(DataState.Empty(response.message))
                } else {
                    supplierOrdersState.emit(
                        DataState.Success(
                            supplierOrdersState.payloadValue.orEmpty().upsertById(response.payload.order),
                            response.message
                        )
                    )

                    supplierOrderLinesState.emit(
                        DataState.Success(
                            supplierOrderLinesState.payloadValue.orEmpty()
                                .filterNot { line -> response.payload.lines.any { it.id == line.id } } +
                                    response.payload.lines,
                            response.message
                        )
                    )

                    postInAppNotification(response.message, NotificationType.Positive)
                    onCompleted?.invoke(DataState.Success(response.payload, response.message))
                }
            }
        }
}

fun updateSupplierOrder(
    orderWithLines: SupplierOrderWithLinesDataModel,
    onCompleted: ((DataState<SupplierOrderWithLinesDataModel>) -> Unit)? = null
) {
    if (!updateSupplierOrderMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            updateSupplierOrderMutex.withLock {
                val response = networkRequest<SupplierOrderWithLinesDataModel, SupplierOrderWithLinesDataModel>(
                    method = HttpMethod.Put,
                    endpointUrl = globalAppConfigurationState.payloadValue.updateSupplierOrderPath.first,
                    body = orderWithLines
                )

                if (response.negative || response.payload == null) {
                    postInAppNotification(response.message, NotificationType.Negative)
                    onCompleted?.invoke(DataState.Empty(response.message))
                } else {
                    supplierOrdersState.emit(
                        DataState.Success(
                            supplierOrdersState.payloadValue.orEmpty().upsertById(response.payload.order),
                            response.message
                        )
                    )
                    supplierOrderLinesState.emit(
                        DataState.Success(
                            supplierOrderLinesState.payloadValue.orEmpty()
                                .filterNot { line -> line.orderId == response.payload.order.id } + response.payload.lines,
                            response.message
                        )
                    )
                    postInAppNotification(response.message, NotificationType.Positive)
                    onCompleted?.invoke(DataState.Success(response.payload, response.message))
                }
            }
        }
}

fun deleteSupplierOrder(
    orderId: String,
    onCompleted: ((DataState<String>) -> Unit)? = null
) {
    if (!deleteSupplierOrderMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            deleteSupplierOrderMutex.withLock {
                val response = networkRequest<String, String>(
                    method = HttpMethod.Delete,
                    endpointUrl = globalAppConfigurationState.payloadValue.deleteSupplierOrdersPath.first,
                    body = orderId
                )

                if (response.negative) {
                    postInAppNotification(response.message, NotificationType.Negative)
                    onCompleted?.invoke(DataState.Empty(response.message))
                } else {
                    supplierOrdersState.emit(
                        DataState.Success(
                            supplierOrdersState.payloadValue.orEmpty().filterNot { it.id == orderId },
                            response.message
                        )
                    )
                    supplierOrderLinesState.emit(
                        DataState.Success(
                            supplierOrderLinesState.payloadValue.orEmpty().filterNot { it.orderId == orderId },
                            response.message
                        )
                    )
                    postInAppNotification(response.message, NotificationType.Positive)
                    onCompleted?.invoke(DataState.Success(orderId, response.message))
                }
            }
        }
}

fun receiveSupplierOrder(
    request: ReceiveSupplierOrderRequestDataModel,
    onCompleted: ((DataState<SupplierOrderWithLinesDataModel>) -> Unit)? = null
) {
    if (!receiveSupplierOrderMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            receiveSupplierOrderMutex.withLock {
                val response = networkRequest<SupplierOrderWithLinesDataModel, ReceiveSupplierOrderRequestDataModel>(
                    method = HttpMethod.Post,
                    endpointUrl = globalAppConfigurationState.payloadValue.receiveSupplierOrderPath.first,
                    body = request
                )

                if (response.negative || response.payload == null) {
                    postInAppNotification(response.message, NotificationType.Negative)
                    onCompleted?.invoke(DataState.Empty(response.message))
                } else {
                    supplierOrdersState.emit(
                        DataState.Success(
                            supplierOrdersState.payloadValue.orEmpty().upsertById(response.payload.order),
                            response.message
                        )
                    )
                    supplierOrderLinesState.emit(
                        DataState.Success(
                            supplierOrderLinesState.payloadValue.orEmpty()
                                .filterNot { line -> line.orderId == response.payload.order.id } + response.payload.lines,
                            response.message
                        )
                    )
                    response.payload.order.storeId.takeIf { it.isNotBlank() }?.let { getStockBatches(it) }
                    postInAppNotification(response.message, NotificationType.Positive)
                    onCompleted?.invoke(DataState.Success(response.payload, response.message))
                }
            }
        }
}

private fun <T> List<T>.upsertById(
    item: T,
    idOf: (T) -> String = {
        when (it) {
            is SupplierGoodsPriceDataModel -> it.id
            is SupplierOrderDataModel -> it.id
            is SupplierOrderLineDataModel -> it.id
            is GoodsBatchDataModel -> it.id
            is GoodsItemDataModel -> it.id
            is TopUpPaymentIntentDataModel -> it.id
            else -> ""
        }
    }
): List<T> {
    val id = idOf(item)
    val index = indexOfFirst { idOf(it) == id }

    return if (index == -1) {
        this + item
    } else {
        toMutableList().also { it[index] = item }
    }
}

val categoriesState = MutableDataStateFlow<List<GenericGoodsCategoryDataModel>>(GlobalScope)
val storeWorkersState = MutableDataStateFlow<List<UserAccountDataModel>>(GlobalScope)

val jsonBase: Json by lazy {
    Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }
}

const val AITA_SERVER_HEADER = "X-AITA-Server"
const val AITA_SERVER_HEADER_VALUE = "AITA"

const val AITA_DEVICE_INSTALLATION_ID_HEADER = "X-AITA-Installation-Id"
const val AITA_DEVICE_NAME_HEADER = "X-AITA-Device-Name"
const val AITA_DEVICE_PLATFORM_HEADER = "X-AITA-Device-Platform"
const val AITA_DEVICE_OS_HEADER = "X-AITA-Device-Os"
const val AITA_DEVICE_APP_NAME_HEADER = "X-AITA-App-Name"
const val AITA_DEVICE_APP_VERSION_HEADER = "X-AITA-App-Version"
const val AITA_DEVICE_LOCALE_HEADER = "X-AITA-Device-Locale"
const val AITA_CONNECTION_PROBE_HEADER = "X-AITA-Connection-Probe"

const val CLOUD_TRANSPORT_STATUS_UNKNOWN = 0
const val CLOUD_TRANSPORT_STATUS_REACHABLE = 1
const val CLOUD_TRANSPORT_STATUS_AUTH_REFRESH_REQUIRED = 2
const val CLOUD_TRANSPORT_STATUS_UNAVAILABLE = -1

@PublishedApi
internal const val REALTIME_ACCESS_TOKEN_REFRESH_SKEW_MILLIS = 60_000L

private const val DEFAULT_AITA_SERVER_URL = "http://192.168.1.51:8080"
private val DEFAULT_AITA_SERVER_URL_PAIR = Pair(DEFAULT_AITA_SERVER_URL, "1")
@Volatile
private var currentNetworkRequestCandidateServerUrlsMemory: List<String> = emptyList()

val GlobalScope = CoroutineScope(SupervisorJob())
val appModeState = MutableStateFlow(0)
val globalAppConfigurationState = MutableDataStateFlowNonNull(
    coroutineScope = GlobalScope,
    initial = GlobalAppConfigurationDataModel(
        realtimeUpdatesPath = "rt/updates",
        appName = Pair("AITA", "0"),
        serverUrl = DEFAULT_AITA_SERVER_URL_PAIR,
        globalAppConfigurationPath = Pair("config/global", "2"),
        logInPath = Pair("auth/logIn", "3"),
        signUpPath = Pair("auth/signUp", "4"),
        refreshPath = Pair("auth/refresh", "5"),
        logOutPath = Pair("auth/logOut", "6"),
        connectionCheckPath = Pair("auth/ping", "1147"),
        getSecuritySessionsPath = Pair("security/sessions/get", "44"),
        getSecuritySessionHistoryPath = Pair("security/sessions/history", "104"),
        revokeSecuritySessionPath = Pair("security/sessions/revoke", "45"),
        revokeOtherSecuritySessionsPath = Pair("security/sessions/revokeOthers", "46"),
        getUserPath = Pair("user/get", "7"),
        updateUserPath = Pair("user/update", "8"),
        updateUserPreferencesPath = Pair("user/preferences/update", "95"),
        getStoresPath = Pair("stores/get", "9"),
        addStoresPath = Pair("stores/add", "10"),
        updateStoresPath = Pair("stores/update", "11"),
        deleteStoresPath = Pair("stores/delete", "12"),
        getStockPath = Pair("stock/get", "13"),
        getParentStoreStockPath = Pair("stock/parent/get", "1212"),
        addGoodsItemPath = Pair("stock/add", "14"),
        updateGoodsItemPath = Pair("stock/update", "15"),
        deleteGoodsItemPath = Pair("stock/delete", "16"),
        getStockBatchesPath = Pair("stockBatches/get", "17"),
        addStockBatchPath = Pair("stockBatches/add", "18"),
        updateStockBatchPath = Pair("stockBatches/update", "19"),
        deleteStockBatchPath = Pair("stockBatches/delete", "20"),
        getStockItemBranchAvailabilityPath = Pair("stockBatches/branchAvailability", "73"),
        moveStockBatchPath = Pair("stockBatches/move", "74"),
        getGenericGoodsItemsPath = Pair("generic/goodsItems/get", "21"),
        getGenericGoodsCategoriesPath = Pair("generic/goodsCategories/get", "22"),
        getSuppliersPath = Pair("suppliers/get", "23"),
        addSupplierPath = Pair("suppliers/add", "83"),
        updateSupplierPath = Pair("suppliers/update", "84"),
        deleteSupplierPath = Pair("suppliers/delete", "85"),
        stringResourcesPath = Pair("res/string", "24"),
        dimensionResourcesPath = Pair("res/dimension", "25"),
        colorResourcesPath = Pair("res/color", "26"),
        drawableResourcesConfigurationPath = Pair("res/drawableConfig", "27"),
        drawableResourcesPath = Pair("res/drawable", "28"),
        getTransactionsPath = Pair("transactions/get", "29"),
        completeTransactionPath = Pair("transactions/complete", "30"),
        getDebtorsPath = Pair("debtors/get", "39"),
        addDebtorPath = Pair("debtors/add", "40"),
        updateDebtorPath = Pair("debtors/update", "41"),
        deleteDebtorPath = Pair("debtors/delete", "42"),
        payDebtorDebtPath = Pair("debtors/pay", "43"),
        getSupplierGoodsPricesPath = Pair("supplierGoodsPrices/get", "31"),
        upsertSupplierGoodsPricePath = Pair("supplierGoodsPrices/upsert", "32"),
        deleteSupplierGoodsPricesPath = Pair("supplierGoodsPrices/delete", "33"),
        getSupplierOrdersPath = Pair("supplierOrders/get", "34"),
        addSupplierOrderPath = Pair("supplierOrders/add", "35"),
        updateSupplierOrderPath = Pair("supplierOrders/update", "36"),
        deleteSupplierOrdersPath = Pair("supplierOrders/delete", "37"),
        receiveSupplierOrderPath = Pair("supplierOrders/receive", "38"),
        getCashRegisterPath = Pair("cashRegister/get", "49"),
        extractCashRegisterPath = Pair("cashRegister/extract", "50"),
        getStoreWorkersPath = Pair("workers/store/get", "51"),
        getMyWorkerMembershipsPath = Pair("workers/my/get", "52"),
        getIncomingWorkerRequestsPath = Pair("workers/requests/incoming", "53"),
        getMyWorkerRequestsPath = Pair("workers/requests/my", "54"),
        getStoreWorkerRoleTemplatesPath = Pair("workers/roleTemplates/get", "1248"),
        upsertStoreWorkerRoleTemplatePath = Pair("workers/roleTemplates/upsert", "1249"),
        deleteStoreWorkerRoleTemplatePath = Pair("workers/roleTemplates/delete", "1250"),
        requestStoreEmploymentPath = Pair("workers/request", "55"),
        acceptStoreEmploymentPath = Pair("workers/accept", "56"),
        declineStoreEmploymentPath = Pair("workers/decline", "57"),
        updateStoreWorkerPermissionsPath = Pair("workers/updatePermissions", "58"),
        removeStoreWorkerPath = Pair("workers/remove", "105"),
        confirmStoreWorkerRemovalPath = Pair("workers/removal/confirm", "106"),
        declineStoreWorkerRemovalPath = Pair("workers/removal/decline", "107"),
        updateMyWorkerPasswordPath = Pair("workers/my/password", "103"),
        inviteStoreWorkerPath = Pair("workers/invite", "65"),
        acceptStoreWorkerInvitationPath = Pair("workers/invitations/accept", "66"),
        declineStoreWorkerInvitationPath = Pair("workers/invitations/decline", "67"),
        getUserFinanceDashboardPath = Pair("finance/dashboard", "77"),
        createTopUpPaymentPath = Pair("finance/topup/create", "78"),
        confirmDevelopmentTopUpPath = Pair("finance/topup/confirmDevelopment", "79"),
        getSubscriptionPlansPath = Pair("subscriptions/plans", "80"),
        getStoreSubscriptionPath = Pair("subscriptions/store/get", "81"),
        updateStoreSubscriptionPath = Pair("subscriptions/store/update", "82"),
        getSupportTicketsPath = Pair("support/tickets/get", "96"),
        createSupportTicketPath = Pair("support/tickets/create", "97"),
        closeSupportTicketPath = Pair("support/tickets/close", "98"),
        reopenSupportTicketPath = Pair("support/tickets/reopen", "99"),
        getSupportMessagesPath = Pair("support/messages/get", "100"),
        sendSupportMessagePath = Pair("support/messages/send", "101"),
        markSupportMessagesReadPath = Pair("support/messages/read", "102"),
        pagingDefaultPageSize = 40,
        pagingMaxPageSize = 200,
        paymentProviders = defaultPaymentProviders(),
        subscriptionPlans = defaultStoreSubscriptionPlans(),
        companyForms = listOf(
            CompanyFormDataModel(
                id = "0",
                name = listOf(
                    LocalizedStringDataModel(
                        language = "en",
                        value = "TOO",
                    ),
                    LocalizedStringDataModel(
                        language = "ru",
                        value = "TOO"
                    ),
                    LocalizedStringDataModel(
                        language = "kk",
                        value = "TOO"
                    )
                ),
                parameters = listOf(
                    ParameterDataModel(
                        name = listOf(
                            LocalizedStringDataModel(
                                language = "en",
                                value = "БИН",
                            ),
                            LocalizedStringDataModel(
                                language = "ru",
                                value = "БИН"
                            ),
                            LocalizedStringDataModel(
                                language = "kk",
                                value = "БИН"
                            )
                        ),
                        value = "",
                        length = 12,
                        number = true,
                        nonLetterSymbolsEnabled = false
                    )
                )
            )
        ),
        countries = listOf(
            CountryDataModel(
                locale = "kz",
                language = "kk",
                name = listOf(
                    LocalizedStringDataModel(
                        "en",
                        "Kazakhstan"
                    ),
                    LocalizedStringDataModel(
                        "ru",
                        "Казахстан"
                    ),
                    LocalizedStringDataModel(
                        "kk",
                        "Казакстан"
                    )
                ),
                flagDrawablePath = "png/flag_kz.png",
                phoneNumberCode = "7",
                phoneNumberSize = 10,
                currencies = listOf(
                    CurrencyDataModel(
                        code = "KZT",
                        symbol = "₸",
                        name = listOf(
                            LocalizedStringDataModel(
                                language = "en",
                                value = "tenge"
                            ),
                            LocalizedStringDataModel(
                                language = "ru",
                                value = "тенге"
                            ),
                            LocalizedStringDataModel(
                                language = "kk",
                                value = "теңге"
                            )
                        )
                    ),
                ),
                cities = listOf(
                    CityDataModel(
                        name = listOf(
                            LocalizedStringDataModel(
                                "en",
                                "Astana"
                            ),
                            LocalizedStringDataModel(
                                "ru",
                                "Астана"
                            ),
                            LocalizedStringDataModel(
                                "kk",
                                "Астана"
                            )
                        ),
                        51.1667, 71.4333,
                        51.0230, 71.2660,
                        51.250071, 71.5500
                    ),
                ),
                cashlessPaymentOptions = listOf(
                    PaymentOptionDataModel(
                        "0",
                        listOf(
                            "Card" localized "main",
                            "Card" localized "en",
                            "Карта" localized "ru",
                            "Карта" localized "kk",
                        )
                    ),
                    PaymentOptionDataModel(
                        "1",
                        listOf(
                            "QR" localized "main",
                            "QR" localized "en",
                            "QR" localized "ru",
                            "QR" localized "kk",
                        )
                    ),
                    PaymentOptionDataModel(
                        "2",
                        listOf(
                            "Kaspi RED" localized "main",
                            "Kaspi RED" localized "en",
                            "Каспи RED" localized "ru",
                            "Каспи RED" localized "kk",
                        )
                    ),
                    PaymentOptionDataModel(
                        "3",
                        listOf(
                            "Rakhmet" localized "main",
                            "Rakhmet" localized "en",
                            "Рахмет" localized "ru",
                            "Рахмет" localized "kk",
                        )
                    )
                ),
                preferredCashlessPaymentOptionId = "1"
            )
        ),
        languages = listOf(
            AppLanguageDataModel(
                "en",
                listOf(
                    LocalizedStringDataModel(
                        "en",
                        "English"
                    ),
                    LocalizedStringDataModel(
                        "ru",
                        "Английский"
                    ),
                    LocalizedStringDataModel(
                        "kk",
                        "Ағылшынша"
                    )
                ),
                "png/flag_en.png"
            ),
            AppLanguageDataModel(
                "ru",
                listOf(
                    LocalizedStringDataModel(
                        "en",
                        "Russian"
                    ),
                    LocalizedStringDataModel(
                        "ru",
                        "Русский"
                    ),
                    LocalizedStringDataModel(
                        "kk",
                        "Орысша"
                    )
                ),
                "png/flag_ru.png"
            ),
            AppLanguageDataModel(
                "kk",
                listOf(
                    LocalizedStringDataModel(
                        "en",
                        "Kazakh"
                    ),
                    LocalizedStringDataModel(
                        "ru",
                        "Казахский"
                    ),
                    LocalizedStringDataModel(
                        "kk",
                        "Қазақша"
                    )
                ),
                "png/flag_kz.png"
            )
        ),
        themes = listOf(
            AppThemeDataModel(
                0,
                listOf(
                    LocalizedStringDataModel(
                        "en",
                        "Light"
                    ),
                    LocalizedStringDataModel(
                        "ru",
                        "Светлая"
                    ),
                    LocalizedStringDataModel(
                        "kk",
                        "Жарық"
                    )
                )
            ),
            AppThemeDataModel(
                1,
                listOf(
                    LocalizedStringDataModel(
                        "en",
                        "Dark"
                    ),
                    LocalizedStringDataModel(
                        "ru",
                        "Темная"
                    ),
                    LocalizedStringDataModel(
                        "kk",
                        "Қараңғы"
                    )
                )
            )
        ),
        goodsItemsQuantityUnits = listOf(
            QuantityDataModel(
                id = "0",
                listOf(
                    LocalizedStringDataModel(
                        "en",
                        "pc."
                    ),
                    LocalizedStringDataModel(
                        "ru",
                        "шт."
                    ),
                    LocalizedStringDataModel(
                        "kk",
                        "шт."
                    )
                ),
                roundTotal = true
            ),
            QuantityDataModel(
                id = "1",
                listOf(
                    LocalizedStringDataModel(
                        "en",
                        "kg."
                    ),
                    LocalizedStringDataModel(
                        "ru",
                        "кг."
                    ),
                    LocalizedStringDataModel(
                        "kk",
                        "кг."
                    )
                ),
                roundTotal = false
            )
        )
    )
)
val stringsState = MutableDataStateFlow<List<LocalizedStringGroupDataModel>>(GlobalScope)
val dimensionsState = MutableDataStateFlow<List<StylizedDimensionGroupDataModel>>(GlobalScope)
val colorsState = MutableDataStateFlow<List<StylizedColorGroupDataModel>>(GlobalScope)
val drawablesState = MutableDataStateFlow<List<StylizedDrawablePathsGroupDataModel>>(GlobalScope)
val appLanguageState = MutableStateFlow(DEFAULT_APP_LANGUAGE)
val appThemeIdState = MutableStateFlow(DEFAULT_APP_THEME_ID)
val appSizeModeIdState = MutableStateFlow(DEFAULT_APP_SIZE_MODE_ID)

@kotlinx.serialization.Serializable
data class AuthScreenPreferenceOverrideDataModel(
    val appLanguage: String? = null,
    val appThemeId: Long? = null,
    val appSizeModeId: Long? = null,
    val languageTouched: Boolean = false,
    val themeTouched: Boolean = false,
    val sizeModeTouched: Boolean = false
) {
    val touched: Boolean get() = languageTouched || themeTouched || sizeModeTouched
}

val authScreenPreferenceOverrideState = MutableStateFlow(AuthScreenPreferenceOverrideDataModel())
val stringRawAuthenticationFailedState = MutableStateFlow(
    listOf(
        LocalizedStringDataModel("main", "Authentication failed"),
        LocalizedStringDataModel("en", "Authentication failed"),
        LocalizedStringDataModel("ru", "Аутентификация не удалась"),
        LocalizedStringDataModel("kk", "Аутентификация сәтсіз аяқталды"),
    )
)
val stringAppNameState = MutableStateFlow("AITA")
val stringLogInState = MutableStateFlow("Log In")
val stringPhoneNumberState = MutableStateFlow("Phone number")
val stringEnterPhoneNumberState = MutableStateFlow("Enter phone number")
val stringEmailState = MutableStateFlow("Email")
val stringEnterEmailAddressState = MutableStateFlow("Enter email address")
val stringPasswordState = MutableStateFlow("Password")
val stringEnterPasswordState = MutableStateFlow("Enter password")
val stringCancelState = MutableStateFlow("Cancel")
val stringClearState = MutableStateFlow("Clear")
val stringAuthenticationFailedState = MutableStateFlow("Authentication failed")
val stringPhoneNumberMustBeState = MutableStateFlow("Incorrect phone number length")
val stringEmailMustBeState = MutableStateFlow("Incorrect email address format")
val stringPasswordMustBeState =
    MutableStateFlow("Password must be 8 or more symbols long and contain at least one digit and one special symbol")
val stringRepeatPasswordState = MutableStateFlow("Repeat password")
val stringPasswordsMustMatchState = MutableStateFlow("Passwords must match")
val stringFirstNameState = MutableStateFlow("First name")
val stringLastNameState = MutableStateFlow("Last name")
val stringEnterFirstNameState = MutableStateFlow("Enter first name")
val stringEnterLastNameState = MutableStateFlow("Enter last name")

val stringUserWithThisPhoneNumberIsAlreadyRegisteredState =
    MutableStateFlow("User with this phone number is already registered")
val stringUserWithThisEmailAddressIsAlreadyRegisteredState =
    MutableStateFlow("User with this email address is already registered")
val stringSignUpState = MutableStateFlow("Sign Up")
val stringConfirmState = MutableStateFlow("Confirm")
val stringSaleState = MutableStateFlow("Sale")
val stringReturnState = MutableStateFlow("Return")
val stringSupplyState = MutableStateFlow("Supply")
val stringStockState = MutableStateFlow("Stock")
val stringMenuState = MutableStateFlow("Menu")
val stringBackState = MutableStateFlow("Back")
val stringAddGoodsItemState = MutableStateFlow("Add goods item")
val stringEditGoodsItemState = MutableStateFlow("Edit goods item")
val stringUserAccountState = MutableStateFlow("User account")
val stringGoodsCategoriesState = MutableStateFlow("Goods categories")
val stringAddGoodsCategoryState = MutableStateFlow("Add goods category")
val stringEditGoodsCategoryState = MutableStateFlow("Edit goods category")
val stringStoresState = MutableStateFlow("Stores")
val stringAddStoreState = MutableStateFlow("Add store")
val stringEditStoreState = MutableStateFlow("Edit store")
val stringSubscriptionState = MutableStateFlow("Subscription")
val stringSubscriptionPlansState = MutableStateFlow("Subscription plans")
val stringTransactionHistoryState = MutableStateFlow("Transaction history")
val stringReceiptState = MutableStateFlow("Receipt")
val stringAnalyticsState = MutableStateFlow("Analytics")
val stringWorkersState = MutableStateFlow("Workers")
val stringAddWorkerState = MutableStateFlow("Add worker")
val stringEditWorkerState = MutableStateFlow("Edit worker")
val stringSuppliersState = MutableStateFlow("Suppliers")
val stringAddSupplierState = MutableStateFlow("Add supplier")
val stringEditSupplierState = MutableStateFlow("Edit supplier")
val stringDebtorsState = MutableStateFlow("Debtors")
val stringCloseDebtState = MutableStateFlow("Close debt")
val stringDevicesState = MutableStateFlow("Devices")
val stringAppLanguageState = MutableStateFlow("App language")
val stringAppThemeState = MutableStateFlow("App theme")
val stringSelectState = MutableStateFlow("Select")
val stringUserWithThisPhoneNumberAndEmailAddressIsAlreadyRegisteredState =
    MutableStateFlow("User with this phone number and email address is already registered")
val stringFirstNameCannotBeEmptyOrJustWhitespacesState =
    MutableStateFlow("First name cannot be empty or just whitespaces")
val stringLastNameCannotBeEmptyOrJustWhitespacesState =
    MutableStateFlow("Last cannot be empty or just whitespaces")
val stringSystemLanguageState = MutableStateFlow("System language")
val stringBluetoothPermissionRequiredState = MutableStateFlow("Bluetooth permission required")
val stringForSearchAndConnectionToBluetoothBarcodeScannersAndReceiptPrintersState =
    MutableStateFlow("For search and connection to Bluetooth barcode scanners and receipt printers")
val stringForSearchAndConnectionToBluetoothBarcodeScannersAndReceiptPrintersYouCanGrantItInAppSettingsState =
    MutableStateFlow("For search and connection to Bluetooth barcode scanners and receipt printers. You can grant it in app settings")
val stringBluetoothDisabledState = MutableStateFlow("Bluetooth disabled")
val stringEnableForSearchAndConnectionToBluetoothBarcodeScannersAndReceiptPrintersState =
    MutableStateFlow("Enable for search and connection to Bluetooth barcode scanners and receipt printers")
val stringSearchByAnyDataState = MutableStateFlow("Search by any data")
val stringListEmptyState = MutableStateFlow("List empty")
val stringNoMatchesState = MutableStateFlow("No matches")
val stringNameState = MutableStateFlow("Name")
val stringBarcodeState = MutableStateFlow("Barcode")
val stringSupplyPriceState = MutableStateFlow("Supply price")
val stringSalePriceState = MutableStateFlow("Sale price")
val stringReturnPriceState = MutableStateFlow("Return price")
val stringCategoryState = MutableStateFlow("Category")
val stringSupplierState = MutableStateFlow("Supplier")
val stringEnterNameState = MutableStateFlow("Enter name")
val stringEnterBarcodeState = MutableStateFlow("Enter barcode")
val stringEnterSupplyPriceState = MutableStateFlow("Enter supply price")
val stringEnterSalePriceState = MutableStateFlow("Enter sale price")
val stringEnterReturnPriceState = MutableStateFlow("Enter return price")
val stringSelectCategoryState = MutableStateFlow("Select category")
val stringSelectSupplierState = MutableStateFlow("Select supplier")
val stringEditState = MutableStateFlow("Edit")
val stringChangePasswordState = MutableStateFlow("Change password")
val stringNewPasswordState = MutableStateFlow("New password")
val stringEnterNewPasswordState = MutableStateFlow("Enter new password")
val stringRepeatNewPasswordState = MutableStateFlow("Repeat new password")
val stringConfirmationPasswordState = MutableStateFlow("Confirmation password")
val stringRequiredToEditAccountState = MutableStateFlow("Required to edit account")
val stringAccountSuccessfullyUpdatedState = MutableStateFlow("Account successfully updated")
val stringLoggingOutState = MutableStateFlow("Logging out")
val stringSessionTimeExpiredLoggingOutState = MutableStateFlow("Cloud session needs refresh")
val stringAliasState = MutableStateFlow("Alias")
val stringDescriptionState = MutableStateFlow("Description")
val stringEnterAliasState = MutableStateFlow("Enter alias")
val stringEnterDescriptionState = MutableStateFlow("Enter description")
val stringOptionalState = MutableStateFlow("Optional")
val stringLoggingInState = MutableStateFlow("Logging in")
val stringSigningUpState = MutableStateFlow("Signing up")
val stringCompanyFormState = MutableStateFlow("Company form")
val stringMeasurementUnitState = MutableStateFlow("Measurement unit")
val stringNoActiveStoreState = MutableStateFlow("No active store")
val stringSelectInMenuState = MutableStateFlow("Select in menu")
val stringSupplyDataState = MutableStateFlow("Supply data")
val stringSaleDataState = MutableStateFlow("Sale data")
val stringReturnDataState = MutableStateFlow("Return data")
val stringAddSupplyDataState = MutableStateFlow("Add supply data")
val stringAddSaleDataState = MutableStateFlow("Add sale data")
val stringAddReturnDataState = MutableStateFlow("Add return data")
val stringAddBarcodeState = MutableStateFlow("Add barcode")
val stringAddNameState = MutableStateFlow("Add name")
val stringPaymentState = MutableStateFlow("Payment")
val stringAllState = MutableStateFlow("All")
val stringQuickState = MutableStateFlow("Quick")
val stringCategoriesState = MutableStateFlow("Categories")
val stringMainState = MutableStateFlow("Main")
val stringAddTranslationState = MutableStateFlow("Add translation")
val stringSetActiveState = MutableStateFlow("Set active")
val stringOutOfStockState = MutableStateFlow("Out of stock")
val stringDeleteState = MutableStateFlow("Delete")
val stringCashState = MutableStateFlow("Cash")
val stringCashlessState = MutableStateFlow("Cashless")
val stringMixedState = MutableStateFlow("Mixed")
val stringAddState = MutableStateFlow("Add")
val stringSubtractState = MutableStateFlow("Subtract")
val stringCurrentQuantityDataState = MutableStateFlow("Current batch data")
val stringEnterQuantityState = MutableStateFlow("Enter quantity")
val stringAddQuantityDataState = MutableStateFlow("Add quantity data")
val stringShelfBatchState = MutableStateFlow("Shelf batch")
val stringActiveStoreState = MutableStateFlow("Active store")
val stringMakeInactiveState = MutableStateFlow("Make inactive")
val stringCartEmptyState = MutableStateFlow("Cart empty")
val stringCompleteState = MutableStateFlow("Complete")
val stringNoActiveWorkshiftState = MutableStateFlow("No active store")
val stringCartState = MutableStateFlow("Cart")
val stringAppModeState = MutableStateFlow("App mode")
val stringFinancesState = MutableStateFlow("Finances")
val stringItemsState = MutableStateFlow("Items")
val stringBatchesState = MutableStateFlow("Batches")
val stringStandardPricesForSuppliersState = MutableStateFlow("Standard prices for suppliers")
val stringEditableForIndividualBatchesState = MutableStateFlow("Editable for individual batches")
val stringBatchesDataState = MutableStateFlow("Batches data")

val stringReceiptNumberState = MutableStateFlow("Receipt number")
val stringTransactionIdState = MutableStateFlow("Transaction ID")
val stringDateState = MutableStateFlow("Date")
val stringCashierState = MutableStateFlow("Cashier")
val stringStoreState = MutableStateFlow("Store")
val stringAddressState = MutableStateFlow("Address")
val stringPhoneState = MutableStateFlow("Phone")
val stringTotalState = MutableStateFlow("Total")
val stringPaidState = MutableStateFlow("Paid")
val stringDebtState = MutableStateFlow("Debt")
val stringDebtorState = MutableStateFlow("Debtor")
val stringDebtorPhoneState = MutableStateFlow("Debtor phone")
val stringChangeState = MutableStateFlow("Change")
val stringVatState = MutableStateFlow("VAT / НДС / ҚҚС")
val stringVatNotSpecifiedState = MutableStateFlow("Not specified")
val stringFiscalStatusState = MutableStateFlow("Fiscal status")
val stringNonFiscalSoftwareReceiptState = MutableStateFlow("Non-fiscal software receipt")
val stringThankYouState = MutableStateFlow("Thank you")
val stringNoItemsState = MutableStateFlow("No items")
val stringNoNameState = MutableStateFlow("No name")
val stringPdfState = MutableStateFlow("PDF")
val stringShareState = MutableStateFlow("Share")
val stringWhatsAppState = MutableStateFlow("WhatsApp")
val stringPrintState = MutableStateFlow("Print")
val stringQuitState = MutableStateFlow("Quit")
val stringReceiptPdfSavedState = MutableStateFlow("Receipt PDF saved")
val stringReceiptSharedState = MutableStateFlow("Receipt shared")
val stringReceiptSentToWhatsAppState = MutableStateFlow("Receipt sent to WhatsApp")
val stringReceiptSentToPrinterState = MutableStateFlow("Receipt sent to printer")
val stringReceiptActionFailedState = MutableStateFlow("Receipt action failed")
val stringGoodsReceiptTitleState = MutableStateFlow("Goods receipt")
val stringSaleReceiptTitleState = MutableStateFlow("Sale")
val stringReturnReceiptTitleState = MutableStateFlow("Return")
val stringSupplyReceiptTitleState = MutableStateFlow("Acceptance")
val stringDraftState = MutableStateFlow("Draft")

val drawablePathAITALogoState = MutableStateFlow("svg/0_0.svg")
val drawablePathIconPasswordState = MutableStateFlow("svg/1_0.svg")
val drawablePathIconSecurityState = MutableStateFlow("svg/1_0.svg")
val drawablePathIconCancelState = MutableStateFlow("svg/2_0.svg")
val drawablePathIconEyeHideState = MutableStateFlow("svg/3_0.svg")
val drawablePathIconEyeShowState = MutableStateFlow("svg/4_0.svg")
val drawablePathIconEmailState = MutableStateFlow("svg/5_0.svg")
val drawablePathIconPhoneState = MutableStateFlow("svg/6_0.svg")
val drawablePathIconExpandMoreState = MutableStateFlow("svg/7_0.svg")
val drawablePathIconExpandLessState = MutableStateFlow("svg/8_0.svg")
val drawablePathIconPersonState = MutableStateFlow("svg/9_0.svg")
val drawablePathIconTransactionSaleState = MutableStateFlow("svg/10_0.svg")
val drawablePathIconTransactionReturnState = MutableStateFlow("svg/11_0.svg")
val drawablePathIconTransactionSupplyState = MutableStateFlow("svg/12_0.svg")
val drawablePathIconTransactionSelectionState = MutableStateFlow("svg/57_0.svg")
val drawablePathIconStockState = MutableStateFlow("svg/13_0.svg")
val drawablePathIconMenuState = MutableStateFlow("svg/14_0.svg")
val drawablePathIconBackArrowState = MutableStateFlow("svg/15_0.svg")
val drawablePathIconAddState = MutableStateFlow("svg/16_0.svg")
val drawablePathIconUserAccountState = MutableStateFlow("svg/17_0.svg")
val drawablePathIconGoodsCategoriesState = MutableStateFlow("svg/18_0.svg")
val drawablePathIconStoresState = MutableStateFlow("svg/19_0.svg")
val drawablePathIconTransactionHistoryState = MutableStateFlow("svg/20_0.svg")
val drawablePathIconLogState = MutableStateFlow("svg/49_0.svg")
val drawablePathIconPromosState = MutableStateFlow("svg/50_0.svg")
val drawablePathIconAnalyticsState = MutableStateFlow("svg/21_0.svg")
val drawablePathIconAnalyticsReportState = MutableStateFlow("svg/62_0.svg")
val drawablePathIconLabelPrinterState = MutableStateFlow("svg/63_0.svg")
val drawablePathIconBarcodeGenerateState = MutableStateFlow("svg/64_0.svg")
val drawablePathIconPrintTagState = MutableStateFlow("svg/65_0.svg")
val drawablePathIconWorkersState = MutableStateFlow("svg/22_0.svg")
val drawablePathIconSuppliersState = MutableStateFlow("svg/23_0.svg")
val drawablePathIconDebtorsState = MutableStateFlow("svg/24_0.svg")
val drawablePathIconDevicesState = MutableStateFlow("svg/25_0.svg")
val drawablePathIconAppLanguageState = MutableStateFlow("svg/26_0.svg")
val drawablePathIconAppThemeState = MutableStateFlow("svg/27_0.svg")
val drawablePathIconAppScaleState = MutableStateFlow("svg/48_0.svg")
val drawablePathIconCheckState = MutableStateFlow("svg/28_0.svg")
val drawablePathIconEditState = MutableStateFlow("svg/29_0.svg")
val drawablePathIconSettingsState = MutableStateFlow("svg/30_0.svg")
val drawablePathIconSearchState = MutableStateFlow("svg/31_0.svg")
val drawablePathIconBarcodeCamScannerState = MutableStateFlow("svg/32_0.svg")
val drawablePathIconBarcodeScannerState = MutableStateFlow("svg/51_0.svg")
val drawablePathIconBarcodeTypeState = MutableStateFlow("svg/55_0.svg")
val drawablePathIconVoiceInputState = MutableStateFlow("svg/52_0.svg")
val drawablePathIconResponseState = MutableStateFlow("svg/53_0.svg")
val drawablePathIconDeleteState = MutableStateFlow("svg/33_0.svg")
val drawablePathIconExitState = MutableStateFlow("svg/34_0.svg")
val drawablePathIconSwitchState = MutableStateFlow("svg/35_0.svg")
val drawablePathIconSortState = MutableStateFlow("svg/61_0.svg")
val drawablePathIconCartState = MutableStateFlow("svg/36_0.svg")
val drawablePathIconAddCartState = MutableStateFlow("svg/37_0.svg")
val drawablePathIconSubtractState = MutableStateFlow("svg/38_0.svg")
val drawablePathIconReceiptState = MutableStateFlow("svg/39_0.svg")
val drawablePathIconFinancesState = MutableStateFlow("svg/40_0.svg")
val drawablePathIconClipboardState = MutableStateFlow("svg/41_0.svg")
val drawablePathIconSupportState = MutableStateFlow("svg/42_0.svg")
val drawablePathIconSubscriptionState = MutableStateFlow("svg/43_0.svg")
val drawablePathIconThemeLightState = MutableStateFlow("svg/44_0.svg")
val drawablePathIconThemeDarkState = MutableStateFlow("svg/45_0.svg")
val drawablePathIconShareState = MutableStateFlow("svg/46_0.svg")
val drawablePathIconWhatsAppState = MutableStateFlow("svg/47_0.svg")
val drawablePathIconRefreshState = MutableStateFlow("svg/54_0.svg")

val getGlobalAppConfigurationMutex = Mutex()
val getStringsMutex = Mutex()
val getDimensionsMutex = Mutex()
val getColorsMutex = Mutex()
val getDrawablesMutex = Mutex()

const val KEY_APP_THEME = "key_appTheme"
const val KEY_APP_LOCALE = "key_appLocale"
const val KEY_APP_SIZE_MODE = "key_appSizeMode"

const val KEY_APP_MODE = "key_appMode"

val cacheSize = 4000L * 1024 * 1024
val cacheMaxAgeSec = 30 * 24 * 3600

val tokenRefreshMutex = Mutex()
private val manualCloudConnectionRefreshMutex = Mutex()
private const val AUTH_REFRESH_NON_AUTH_FAILURE_GRACE_MILLIS = 5_000L
private const val CLOUD_CONNECTION_HEALTH_CHECK_REACHABLE_INTERVAL_MILLIS = 5_000L
private const val CLOUD_CONNECTION_HEALTH_CHECK_UNKNOWN_INTERVAL_MILLIS = 4_000L
private const val CLOUD_CONNECTION_HEALTH_CHECK_UNAVAILABLE_INTERVAL_MILLIS = 3_000L
private const val CLOUD_CONNECTION_HEALTH_CHECK_TIMEOUT_MILLIS = 3_500L
private const val CLOUD_CONNECTION_AUTH_REFRESH_SUPPRESSION_AFTER_TRANSPORT_FAILURE_MILLIS = 15_000L
@Volatile
private var cloudTransportLastUnavailableAtMillis: Long = 0L
@Volatile
private var cloudTransportLastReachableAtMillis: Long = 0L
@Volatile
private var lastAuthRefreshNonAuthFailureAtMillis: Long = 0L
@Volatile
private var lastAuthRefreshNonAuthFailureMessage: List<LocalizedStringDataModel>? = null
@Volatile
private var lastAuthRefreshNonAuthFailureWasTransportFailure: Boolean = false
val activeNetworkOperationsState = MutableStateFlow(0)
val cloudTransportStatusState = MutableStateFlow(CLOUD_TRANSPORT_STATUS_UNKNOWN)
val cloudConnectionManualRefreshInProgressState = MutableStateFlow(false)

private val productionAppDatabase: AppDatabase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    AppDatabase(
        getSqlDelightDriver?.invoke()
            ?: error("SQLDelight driver is not initialized yet. Set getSqlDelightDriver before the first database access.")
    )
}

@Volatile
private var appDatabaseForTests: AppDatabase? = null

val appDatabase: AppDatabase
    get() = appDatabaseForTests ?: productionAppDatabase

fun setAppDatabaseForTests(database: AppDatabase?) {
    appDatabaseForTests = database
}

var httpClient =
    HttpClient(getHttpClientEngine()) {
        install(ContentNegotiation) {
            json(
                Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                    explicitNulls = true
                    encodeDefaults = true
                }
            )
        }

        expectSuccess = false

        install(HttpTimeout) {
            requestTimeoutMillis = 18_000L
            connectTimeoutMillis = 4_000L
            socketTimeoutMillis = 18_000L
        }
        install(HttpCache)
        install(WebSockets)
//
//    install(Logging) {
//      level = LogLevel.ALL
//    }
        install(Auth) {
            bearer {
                sendWithoutRequest { request ->
                    val path = request.url.encodedPath.trimStart('/').lowercase()
                    if (path.startsWith("auth/")) {
                        false
                    } else {
                        val scheme = request.url.protocol.name
                        val port = request.url.port
                        val defaultPort = if (scheme.equals("https", ignoreCase = true)) 443 else 80
                        val requestBase = buildString {
                            append(scheme)
                            append("://")
                            append(request.url.host)
                            if (port > 0 && port != defaultPort) {
                                append(":")
                                append(port)
                            }
                        }
                        val requestNormalized = normalizedHttpServerUrlOrNull(requestBase)
                        val currentNormalized = normalizedHttpServerUrlOrNull(globalAppConfigurationState.payloadValue.serverUrl.first)
                        val knownAitaBases = buildList {
                            currentNormalized?.let { add(it) }
                            currentNetworkRequestCandidateServerUrlsMemory.forEach { candidate ->
                                normalizedHttpServerUrlOrNull(candidate)?.let(::add)
                            }
                        }.distinct()

                        requestNormalized != null && knownAitaBases.any { it == requestNormalized }
                    }
                }

                loadTokens {
                    withContext(Dispatchers.ourIo) {
                        getStoredUserAuthTokens?.invoke()?.let {
                            BearerTokens(it.accessToken, it.refreshToken)
                        }
                    }
                }

                refreshTokens {
                    withContext(Dispatchers.ourIo) {
                        tokenRefreshMutex.withLock {
                            val current = getStoredUserAuthTokens?.invoke() ?: return@withLock null

                            val refreshResponse = refreshAuthTokensWithServerFallback(current.refreshToken)

                            when {
                                refreshResponse.payload != null -> {
                                    clearAuthRefreshNonAuthFailure()
                                    setStoredUserAuthTokens?.invoke(refreshResponse.payload)
                                    markCloudTransportReachableForNotifications(authenticated = true, authRefreshRequired = false)
                                    BearerTokens(refreshResponse.payload.accessToken, refreshResponse.payload.refreshToken)
                                }

                                refreshResponse.transportFailure -> {
                                    // The persistent connection banner represents server outage; keep the user signed in locally.
                                    BearerTokens(current.accessToken, current.refreshToken)
                                }

                                refreshResponse.httpStatusCode == HttpStatusCode.Unauthorized.value -> {
                                    clearAuthRefreshNonAuthFailure()
                                    markCloudSessionNeedsRefreshForNotifications()
                                    postInAppNotification(
                                        refreshResponse.message ?: localizedStringResourceMessage(
                                            id = 91,
                                            main = "Cloud session needs refresh. You remain signed in locally.",
                                            ru = "Облачный сеанс нужно обновить. Вы остаётесь в аккаунте локально.",
                                            kk = "Бұлттық сеансты жаңарту қажет. Сіз жергілікті түрде аккаунтта қаласыз."
                                        ),
                                        NotificationType.Neutral
                                    )
                                    null
                                }

                                else -> {
                                    // Reachable AITA server + non-401 error means the session is not proven invalid.
                                    // Keep local auth and stop showing the auth-refresh banner.
                                    rememberAuthRefreshNonAuthFailure(refreshResponse.message)
                                    markCloudTransportReachableForNotifications(authenticated = false, authRefreshRequired = false)
                                    BearerTokens(current.accessToken, current.refreshToken)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

val userAccountState = MutableDataStateFlow<UserAccountDataModel>(GlobalScope)

val storesState = MutableDataStateFlow<List<StoreDataModel>>(GlobalScope)
val activeStoreIdState = MutableStateFlow<String?>(null)

val getStoresMutex = Mutex()
val addStoreMutex = Mutex()
val updateStoreMutex = Mutex()
val deleteStoreMutex = Mutex()

const val KEY_ACTIVE_STORE_ID = "key_activeStoreId"
const val KEY_ACTIVE_STORE_EXPLICIT_NONE = "key_activeStoreExplicitNone"

private suspend fun activeStoreExplicitNoneIsSet(): Boolean =
    getLocalKv(KEY_ACTIVE_STORE_EXPLICIT_NONE) == "1"


val genericGoodsCategoriesState = MutableDataStateFlow<List<GenericGoodsCategoryDataModel>>(GlobalScope)
val genericGoodsItemsState = MutableDataStateFlow<List<GenericGoodsItemDataModel>>(GlobalScope)

val getGenericGoodsItemsMutex = Mutex()
val getGenericGoodsCategoriesMutex = Mutex()
val cartTransactionType0_clientId0_state = MutableStateFlow<List<GoodsItemInCartDataModel>>(emptyList())
val cartTransactionType0_clientId1_state = MutableStateFlow<List<GoodsItemInCartDataModel>>(emptyList())
val cartTransactionType0_clientId2_state = MutableStateFlow<List<GoodsItemInCartDataModel>>(emptyList())
val cartTransactionType0_clientId3_state = MutableStateFlow<List<GoodsItemInCartDataModel>>(emptyList())
val cartTransactionType0_clientId4_state = MutableStateFlow<List<GoodsItemInCartDataModel>>(emptyList())
val cartTransactionType1_clientId0_state = MutableStateFlow<List<GoodsItemInCartDataModel>>(emptyList())
val cartTransactionType1_clientId1_state = MutableStateFlow<List<GoodsItemInCartDataModel>>(emptyList())
val cartTransactionType1_clientId2_state = MutableStateFlow<List<GoodsItemInCartDataModel>>(emptyList())
val cartTransactionType1_clientId3_state = MutableStateFlow<List<GoodsItemInCartDataModel>>(emptyList())
val cartTransactionType1_clientId4_state = MutableStateFlow<List<GoodsItemInCartDataModel>>(emptyList())
val cartTransactionType2_clientId0_state = MutableStateFlow<List<GoodsItemInCartDataModel>>(emptyList())
val cartTransactionType2_clientId1_state = MutableStateFlow<List<GoodsItemInCartDataModel>>(emptyList())
val cartTransactionType2_clientId2_state = MutableStateFlow<List<GoodsItemInCartDataModel>>(emptyList())
val cartTransactionType2_clientId3_state = MutableStateFlow<List<GoodsItemInCartDataModel>>(emptyList())
val cartTransactionType2_clientId4_state = MutableStateFlow<List<GoodsItemInCartDataModel>>(emptyList())
val cartPersistenceHydratedState = MutableStateFlow(false)

private val observedCartHydrationKeys = mutableSetOf<String>()
private val observedCartHydrationMutex = Mutex()

val stockState = MutableDataStateFlow<List<GoodsItemDataModel>>(GlobalScope)
val parentStoreStockState = MutableDataStateFlow<List<GoodsItemDataModel>>(GlobalScope)

val stockBatchesState = MutableDataStateFlow<List<GoodsBatchDataModel>>(GlobalScope)
val stockItemBranchAvailabilityState = MutableDataStateFlow<StockItemBranchAvailabilityDataModel>(GlobalScope)
val stockBatchMoveResultState = MutableDataStateFlow<StockBatchMoveResultDataModel>(GlobalScope)

val getStockMutex = Mutex()
val getParentStoreStockMutex = Mutex()
val getStockBatchesMutex = Mutex()
val addGoodsItemMutex = Mutex()
val updateGoodsItemMutex = Mutex()
val deleteGoodsItemMutex = Mutex()
val getStockItemBranchAvailabilityMutex = Mutex()
val moveStockBatchMutex = Mutex()

val logInMutex = Mutex()
val signUpUserMutex = Mutex()
val logInInProgressState = MutableStateFlow(false)
val signUpInProgressState = MutableStateFlow(false)

val logOutUserMutex = Mutex()
private val syncPendingSessionCleanupsMutex = Mutex()
private val syncPendingWorkshiftEndsMutex = Mutex()
private val syncPendingWorkshiftEndsRunMutex = Mutex()
val getUserAccountMutex = Mutex()
val updateUserMutex = Mutex()
val updateUserPreferencesMutex = Mutex()
val latestInAppNotificationState = MutableStateFlow<NotificationDataModel?>(null)
val activeInAppNotificationsState = MutableStateFlow<List<NotificationDataModel>>(emptyList())
val notificationsState = MutableDataStateFlow<List<NotificationDataModel>>(GlobalScope)
val getNotificationsMutex = Mutex()
val saveNotificationMutex = Mutex()
val markNotificationReadMutex = Mutex()
val syncPendingNotificationsMutex = Mutex()
private val notificationPopupJobs = mutableMapOf<String, Job>()
private val notificationPopupMutex = Mutex()
private val serverNotificationPopupIds = mutableSetOf<String>()
private val notificationPopupBootMillis = getCurrentTimeMillis()
private const val SERVER_NOTIFICATION_POPUP_FRESH_WINDOW_MILLIS = 180_000L

val supportTicketsState = MutableDataStateFlow<List<SupportTicketDataModel>>(GlobalScope)
val supportMessagesState = MutableDataStateFlow<List<SupportMessageDataModel>>(GlobalScope)
val activeSupportTicketIdState = MutableStateFlow<String?>(null)
val supportMessageSendingState = MutableStateFlow(false)
val getSupportTicketsMutex = Mutex()
val createSupportTicketMutex = Mutex()
val closeSupportTicketMutex = Mutex()
val reopenSupportTicketMutex = Mutex()
val getSupportMessagesMutex = Mutex()
val sendSupportMessageMutex = Mutex()
val markSupportMessagesReadMutex = Mutex()

val cashRegisterExtractionsState =
    MutableDataStateFlow<List<CashRegisterExtractionEntryDataModel>>(GlobalScope)

val cashRegisterAmountState = MutableStateFlow(0.0)
val cashRegisterState = MutableDataStateFlow<StoreCashRegisterDataModel>(GlobalScope)
val cashRegisterEventsState = MutableDataStateFlow<List<CashRegisterEventDataModel>>(GlobalScope)

val storeWorkerMembershipsState = MutableDataStateFlow<List<StoreWorkerDataModel>>(GlobalScope)
val myWorkerMembershipsState = MutableDataStateFlow<List<StoreWorkerDataModel>>(GlobalScope)
val incomingWorkerRequestsState = MutableDataStateFlow<List<StoreWorkerRequestDataModel>>(GlobalScope)
val myWorkerRequestsState = MutableDataStateFlow<List<StoreWorkerRequestDataModel>>(GlobalScope)
val storeWorkerRoleTemplatesState = MutableDataStateFlow<List<StoreWorkerRoleTemplateDataModel>>(GlobalScope)
val activeWorkshiftState = MutableDataStateFlow<WorkshiftDataModel>(GlobalScope)
val workshiftLoginInProgressState = MutableStateFlow(false)
val operationLogsState = MutableDataStateFlow<List<OperationLogDataModel>>(GlobalScope)
val localNetworkState = MutableStateFlow(LocalNetworkStateDataModel())
val localNetworkDevicesState = MutableStateFlow<List<LocalNetworkDeviceDataModel>>(emptyList())
val localNetworkQueuedOperationsState = MutableStateFlow<List<LocalNetworkQueuedOperationDataModel>>(emptyList())

val storeAnalyticsDashboardState = MutableDataStateFlow<StoreAnalyticsDashboardDataModel>(GlobalScope)

private val getCashRegisterMutex = Mutex()
private val extractCashRegisterMutex = Mutex()
private val getStoreWorkersMutex = Mutex()
private val getMyWorkerMembershipsMutex = Mutex()
private val getIncomingWorkerRequestsMutex = Mutex()
private val getMyWorkerRequestsMutex = Mutex()
private val getStoreWorkerRoleTemplatesMutex = Mutex()
private val upsertStoreWorkerRoleTemplateMutex = Mutex()
private val deleteStoreWorkerRoleTemplateMutex = Mutex()
private val requestStoreEmploymentMutex = Mutex()
private val inviteStoreWorkerMutex = Mutex()
private val decideStoreEmploymentMutex = Mutex()
private val updateStoreWorkerPermissionsMutex = Mutex()
private val removeStoreWorkerMutex = Mutex()
private val decideStoreWorkerRemovalMutex = Mutex()
private val updateMyWorkerPasswordMutex = Mutex()
private val getCurrentWorkshiftMutex = Mutex()
private val startWorkshiftMutex = Mutex()
private val endWorkshiftMutex = Mutex()
private val getOperationLogsMutex = Mutex()
private val getStoreAnalyticsMutex = Mutex()


























fun String.checkAsEmail(): Boolean {
    return isNotEmpty() && isNotBlank() && !contains(" ") &&
            contains("@") && contains(".") &&
            Regex("^[a-zA-Z0-9]").matches(first().toString()) &&
            filter { it == '@' }.length == 1 && lastIndexOf(".") > lastIndexOf("@") &&
            lastIndexOf(".") != lastIndex
}

fun String.checkAsPhoneNumber(country: CountryDataModel): Boolean {
    return length == country.phoneNumberSize
}

fun String.filterAsPhoneNumber(country: CountryDataModel): Boolean {
    return isNumericalString() && length <= country.phoneNumberSize
}

fun String.checkAsPassword(): Boolean {
    val value = trim()
    return value.length >= 8 &&
            value.any { it.isDigit() } &&
            value.any { !it.isLetterOrDigit() && !it.isWhitespace() }
}

fun String.isNumericalString(): Boolean {
    return all { it.isDigit() }
}

fun String.isNumericalDoubleString(): Boolean {
    var dots = 0

    forEach {
        if (it == '.')
            dots++
    }

    return if (dots > 1)
        false
    else all { it.isDigit() || it == '.' }
}

fun String.checkAsPersonName(): Boolean {
    return isNotEmpty() && isNotBlank() && matches(Regex("""^[\p{L}\p{M} .-]+$"""))
}

fun String.filterAsPersonName(): Boolean {
    return isEmpty() || matches(Regex("""^[\p{L}\p{M} .-]+$"""))
}

infix fun String.localized(locale: String): LocalizedStringDataModel {
    return LocalizedStringDataModel(locale, this)
}

fun getGoodsCategories() {
    GlobalScope.launch(Dispatchers.ourIo) {
        categoriesState
            .emit(
                DataState.Success(
                    listOf(

                    )
                )
            )
    }
}

fun List<LocalizedStringGroupDataModel>?.extractString(id: Long, language: String): String? {
    val values = this
        ?.find { it.id == id }
        ?.values
        ?: return null

    return values.extractLocalizedString(language)
        ?: values.extractLocalizedString("main")
        ?: values.extractLocalizedString(DEFAULT_APP_LANGUAGE)
        ?: values.extractLocalizedString("en")
        ?: values.firstOrNull { it.value.isNotBlank() }?.value
}

fun List<StylizedDimensionGroupDataModel>.extractValue(id: Long, sizeModeId: Long): Float? {
    val normalizedSizeModeId = normalizeAppSizeModePreference(sizeModeId)
    val values = find { it.id == id }?.values

    return values?.firstOrNull { it.sizeModeId == normalizedSizeModeId }?.value
        ?: values?.firstOrNull { it.sizeModeId == -1L }?.value
        ?: values?.firstOrNull { it.sizeModeId == DEFAULT_APP_SIZE_MODE_ID }?.value
        ?: values?.firstOrNull()?.value
        ?: when (id) {
            0L -> if (normalizedSizeModeId == 1L) 16f else 14f
            1L -> if (normalizedSizeModeId == 1L) 23f else 20f
            2L -> if (normalizedSizeModeId == 1L) 18f else 16f
            3L -> if (normalizedSizeModeId == 1L) 14f else 12f
            4L -> 600f
            5L -> 1f
            6L -> 0.5f
            7L -> if (normalizedSizeModeId == 1L) 16f else 14f
            8L -> if (normalizedSizeModeId == 1L) 28f else 24f
            9L -> if (normalizedSizeModeId == 1L) 340f else 300f
            10L -> if (normalizedSizeModeId == 1L) 2.75f else 2.6f
            11L -> if (normalizedSizeModeId == 1L) 10f else 9f
            else -> null
        }
}

fun List<StylizedColorGroupDataModel>.extractColor(id: Long, themeId: Long): String? {
    val normalizedThemeId = normalizeAppThemePreference(themeId)
    val values = find { it.id == id }?.values

    return values?.firstOrNull { it.themeId == normalizedThemeId }?.valueHex
        ?: values?.firstOrNull { it.themeId == -1L }?.valueHex
        ?: values?.firstOrNull { it.themeId == DEFAULT_APP_THEME_ID }?.valueHex
        ?: values?.firstOrNull()?.valueHex
}

fun List<StylizedDrawablePathsGroupDataModel>.extractPath(id: Long, themeId: Long): String? {
    val normalizedThemeId = normalizeAppThemePreference(themeId)
    val values = find { it.id == id }?.values

    return values?.firstOrNull { it.themeId == normalizedThemeId }?.path
        ?: values?.firstOrNull { it.themeId == -1L }?.path
        ?: values?.firstOrNull { it.themeId == DEFAULT_APP_THEME_ID }?.path
        ?: values?.firstOrNull()?.path
        ?: if (id in 0L..200L) "svg/${id}_${normalizedThemeId}.svg" else null
}

fun getFullDrawableRemoteResourceUrl(path: String): String {
    return globalAppConfigurationState.payloadValue.run {
        "${serverUrl.first}/${drawableResourcesPath.first}"
    } + "/$path"
}

fun getFullDrawableLocalResourceUrl(path: String): String {
    return "files/$path"
}

fun List<RemoteResponseDataModel>.extractExceptionMessage(id: String): List<LocalizedStringDataModel>? {
    return find { it.id == id }?.message
}

fun List<LocalizedStringDataModel>.extractLocalizedString(language: String): String? {
    val targetLanguage = if (language == "system") getSystemLocaleLanguage() else language

    return find { it.language.equals(targetLanguage, ignoreCase = true) }?.value
        ?: find { it.language.equals("main", ignoreCase = true) }?.value
        ?: find { it.language.equals("en", ignoreCase = true) }?.value
        ?: firstOrNull()?.value
}

fun localizedStringResourceMessage(
    id: Long,
    main: String,
    en: String = main,
    ru: String = main,
    kk: String = main
): List<LocalizedStringDataModel> {
    return stringsState.payloadValue
        ?.find { it.id == id }
        ?.values
        ?.takeIf { it.isNotEmpty() }
        ?: listOf(
            LocalizedStringDataModel("main", main),
            LocalizedStringDataModel("en", en),
            LocalizedStringDataModel("ru", ru),
            LocalizedStringDataModel("kk", kk)
        )
}

fun localizedStringResourceText(
    id: Long,
    main: String,
    en: String = main,
    ru: String = main,
    kk: String = main
): String {
    return localizedStringResourceMessage(id, main, en, ru, kk)
        .extractLocalizedString(appLanguageState.value)
        ?: main
}

fun String.toLocalizedSingleMain(): List<LocalizedStringDataModel> {
    return listOf(LocalizedStringDataModel("main", this))
}

fun String?.toLocalizedUserNote(language: String = appLanguageState.value): List<LocalizedStringDataModel> {
    val cleanValue = this?.trim()?.takeIf { it.isNotBlank() } ?: return emptyList()
    val cleanLanguage = normalizeAppLanguagePreference(language)
        .takeIf { it.isNotBlank() && !it.equals("system", ignoreCase = true) }
        ?: DEFAULT_APP_LANGUAGE
    return listOf("main", cleanLanguage)
        .distinctBy { it.lowercase() }
        .map { LocalizedStringDataModel(it, cleanValue) }
}

fun List<LocalizedStringDataModel>.normalizedLocalizedStrings(): List<LocalizedStringDataModel> {
    return mapNotNull { value ->
        val language = value.language.trim().ifBlank { "main" }
        val text = value.value.trim()
        if (text.isBlank()) null else LocalizedStringDataModel(language, text)
    }.distinctBy { it.language.lowercase() }
}

fun StoreWorkerRequestDataModel.isWorkerRemovalRequest(): Boolean {
    return direction == WORKER_REQUEST_DIRECTION_STORE_REMOVAL_TO_USER
}

fun StoreWorkerRequestDataModel.isPendingWorkerRemovalRequest(): Boolean {
    return isWorkerRemovalRequest() && status == WORKER_REQUEST_STATUS_PENDING
}

fun StoreWorkerRequestDataModel.isWorkerRemovalResponse(): Boolean {
    return isWorkerRemovalRequest() && (status == WORKER_REQUEST_STATUS_ACCEPTED || status == WORKER_REQUEST_STATUS_DECLINED)
}

fun StoreWorkerRequestDataModel.isEmploymentResponse(): Boolean {
    return !isWorkerRemovalRequest() && (status == WORKER_REQUEST_STATUS_ACCEPTED || status == WORKER_REQUEST_STATUS_DECLINED)
}

fun StoreWorkerRequestDataModel.requestNoteVisible(language: String): String? {
    return noteLocalized.extractLocalizedString(language)?.trim()?.takeIf { it.isNotBlank() }
        ?: note?.trim()?.takeIf { it.isNotBlank() }
}

fun StoreWorkerRequestDataModel.responseNoteVisible(language: String): String? {
    return responseNoteLocalized.extractLocalizedString(language)?.trim()?.takeIf { it.isNotBlank() }
        ?: responseNote?.trim()?.takeIf { it.isNotBlank() }
}

fun List<CountryDataModel>.getCurrency(code: String): CurrencyDataModel? {
    val currencies = mutableListOf<CurrencyDataModel>().apply {
        this@getCurrency.forEach {
            addAll(it.currencies)
        }
    }

    return currencies.find { it.code.equals(code, true) }
}

fun List<CountryDataModel>.getCurrenciesByCountry(locale: String): List<CurrencyDataModel>? {
    return this.find { it.locale == locale }?.currencies
}

fun List<CountryDataModel>.getFirstCurrencyByCountry(locale: String): CurrencyDataModel? {
    return getCurrenciesByCountry(locale)?.takeIf { it.isNotEmpty() }?.first()
}

fun init() {
    GlobalScope.launch(Dispatchers.ourIo) {
        // Load persisted configuration before starting collectors. Otherwise the initial bundled
        // server URL can overwrite the last working local server URL before the app has a chance
        // to use it, making the client appear to never reach the backend.
        loadCachedApplicationData()
        startAppCacheCollectors()
        loadTransactionCartUiState()
        initializeLocalBranchNetwork()
        startCloudConnectionHealthMonitor()
        syncPendingSessionCleanupsToServer()
        if (getStoredUserAuthTokens?.invoke() != null) startRealtimeUpdates()
    }

    GlobalScope.launch {
        observeLocalKv(KEY_APP_LOCALE)
            .collect {
                it?.let { stored ->
                    val normalized = normalizeAppLanguagePreference(stored)
                    appLanguageState.emit(normalized)
                    if (stored != normalized) putLocalKv(KEY_APP_LOCALE, normalized)
                }
            }
    }

    GlobalScope.launch {
        observeLocalKv(KEY_APP_THEME)
            .collect {
                it?.let { stored ->
                    appThemeIdState.emit(normalizeAppThemePreference(stored.toLongOrNull()))
                }
            }
    }

    GlobalScope.launch {
        observeLocalKv(KEY_APP_SIZE_MODE)
            .collect {
                it?.let { stored ->
                    val normalized = normalizeAppSizeModePreference(stored.toLongOrNull())
                    appSizeModeIdState.emit(normalized)
                    if (stored != normalized.toString()) putLocalKv(KEY_APP_SIZE_MODE, normalized.toString())
                }
            }
    }

    GlobalScope.launch {
        observeLocalKv(KEY_APP_MODE)
            .collect {
                it?.let {
                    appModeState.emit(it.toInt())
                }
            }
    }

    GlobalScope.launch(Dispatchers.ourIo) {
        observeLocalKv(KEY_ACTIVE_STORE_ID)
            .collect { storedActiveStoreId ->
                val normalizedStoreId = storedActiveStoreId?.takeIf { it.isNotBlank() }
                activeStoreIdState.emit(normalizedStoreId)
                normalizedStoreId?.let { storeId ->
                    loadCachedStoreScopedData(storeId)
                    getStock(storeId)
                    getStockBatches(storeId)
                    getTransactions(storeId)
                    getCashRegister(storeId)
                    getStoreWorkers(storeId)
                    getIncomingWorkerRequests(storeId)
                    getMyWorkerMemberships()
                    getMyWorkerRequests()
                    getStoreSubscription(storeId)
                }
            }
    }

    GlobalScope.launch(Dispatchers.ourIo) {
        storesState.payload.collect {
            it?.let {
                val settableStores = it.settableActiveStores()
                if (!activeStoreExplicitNoneIsSet() && settableStores.size == 1 && activeStoreIdState.value != settableStores.first().id) {
                    setActiveStoreId(settableStores.first().id)
                }
            }
        }
    }

    GlobalScope.launch(Dispatchers.ourIo) {
        observeCart(0, 0)
            .collect {
                emitObservedCartPreservingUntilStockLoaded(cartTransactionType0_clientId0_state, it, 0, 0)
            }
    }

    GlobalScope.launch(Dispatchers.ourIo) {
        observeCart(0, 1)
            .collect {
                emitObservedCartPreservingUntilStockLoaded(cartTransactionType0_clientId1_state, it, 0, 1)
            }
    }

    GlobalScope.launch(Dispatchers.ourIo) {

        observeCart(0, 2)
            .collect {
                emitObservedCartPreservingUntilStockLoaded(cartTransactionType0_clientId2_state, it, 0, 2)
            }
    }

    GlobalScope.launch(Dispatchers.ourIo) {

        observeCart(0, 3)
            .collect {
                emitObservedCartPreservingUntilStockLoaded(cartTransactionType0_clientId3_state, it, 0, 3)
            }
    }

    GlobalScope.launch(Dispatchers.ourIo) {
        observeCart(0, 4)
            .collect {
                emitObservedCartPreservingUntilStockLoaded(cartTransactionType0_clientId4_state, it, 0, 4)
            }
    }

    GlobalScope.launch(Dispatchers.ourIo) {

        observeCart(1, 0)
            .collect {
                emitObservedCartPreservingUntilStockLoaded(cartTransactionType1_clientId0_state, it, 1, 0)
            }
    }

    GlobalScope.launch(Dispatchers.ourIo) {
        observeCart(1, 1)
            .collect {
                emitObservedCartPreservingUntilStockLoaded(cartTransactionType1_clientId1_state, it, 1, 1)
            }
    }

    GlobalScope.launch(Dispatchers.ourIo) {
        observeCart(1, 2)
            .collect {
                emitObservedCartPreservingUntilStockLoaded(cartTransactionType1_clientId2_state, it, 1, 2)
            }
    }

    GlobalScope.launch(Dispatchers.ourIo) {
        observeCart(1, 3)
            .collect {
                emitObservedCartPreservingUntilStockLoaded(cartTransactionType1_clientId3_state, it, 1, 3)
            }
    }

    GlobalScope.launch(Dispatchers.ourIo) {
        observeCart(1, 4)
            .collect {
                emitObservedCartPreservingUntilStockLoaded(cartTransactionType1_clientId4_state, it, 1, 4)
            }
    }

    GlobalScope.launch(Dispatchers.ourIo) {
        observeCart(2, 0)
            .collect {
                emitObservedCartPreservingUntilStockLoaded(cartTransactionType2_clientId0_state, it, 2, 0)
            }
    }

    GlobalScope.launch(Dispatchers.ourIo) {
        observeCart(2, 1)
            .collect {
                emitObservedCartPreservingUntilStockLoaded(cartTransactionType2_clientId1_state, it, 2, 1)
            }
    }

    GlobalScope.launch(Dispatchers.ourIo) {
        observeCart(2, 2)
            .collect {
                emitObservedCartPreservingUntilStockLoaded(cartTransactionType2_clientId2_state, it, 2, 2)
            }
    }

    GlobalScope.launch(Dispatchers.ourIo) {

        observeCart(2, 3)
            .collect {
                emitObservedCartPreservingUntilStockLoaded(cartTransactionType2_clientId3_state, it, 2, 3)
            }
    }

    GlobalScope.launch(Dispatchers.ourIo) {
        observeCart(2, 4)
            .collect {
                emitObservedCartPreservingUntilStockLoaded(cartTransactionType2_clientId4_state, it, 2, 4)
            }
    }
    getGlobalAppConfiguration(true)
    getUser()
    getUserFinanceDashboard()
    getSubscriptionPlans()
    getSuppliers()
    getGoodsCategories()
    getStores()
}

fun getGlobalAppConfiguration(loadAll: Boolean = true) {
    if (!getGlobalAppConfigurationMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            getGlobalAppConfigurationMutex.withLock {
                val response = networkRequest<GlobalAppConfigurationDataModel, Unit>(
                    method = HttpMethod.Get,
                    endpointUrl = globalAppConfigurationState.payloadValue.globalAppConfigurationPath.first
                )

                if (!response.negative && response.payload != null) {
                    val currentConfiguration = globalAppConfigurationState.payloadValue
                    val anchoredPayload = response.payload.copy(
                        serverUrl = chooseClientServerUrlPair(currentConfiguration.serverUrl, response.payload.serverUrl)
                    )
                    globalAppConfigurationState.emit(DataState.Success(anchoredPayload, response.message))

                    if (loadAll) {
                        getStrings()
                        getDimensions()
                        getColors()
                        getDrawables()
                    }
                }
            }
        }
}

fun getStrings() {
    if (!getStringsMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            getStringsMutex.withLock {
                val response = networkRequest<List<LocalizedStringGroupDataModel>, Unit>(
                    method = HttpMethod.Get,
                    endpointUrl = globalAppConfigurationState.payloadValue.stringResourcesPath.first
                )

                if (!response.negative && response.payload != null) {
                    stringsState.emit(DataState.Success(response.payload, response.message))
                } else {
                    postInAppNotification(response.message, NotificationType.Negative)
                }
            }
        }
}

fun getDimensions() {
    if (!getDimensionsMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            getDimensionsMutex.withLock {
                val response = networkRequest<List<StylizedDimensionGroupDataModel>, Unit>(
                    method = HttpMethod.Get,
                    endpointUrl = globalAppConfigurationState.payloadValue.dimensionResourcesPath.first,
                )

                if (!response.negative && response.payload != null) {
                    dimensionsState.emit(DataState.Success(response.payload, response.message))
                }
            }
        }
}

fun getColors() {
    if (!getColorsMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            getColorsMutex.withLock {
                val response = networkRequest<List<StylizedColorGroupDataModel>, Unit>(
                    method = HttpMethod.Get,
                    endpointUrl = globalAppConfigurationState.payloadValue.colorResourcesPath.first
                )

                if (!response.negative && response.payload != null) {
                    colorsState.emit(DataState.Success(response.payload, response.message))
                } else {
                    postInAppNotification(response.message, NotificationType.Negative)
                }
            }
        }
}

fun getDrawables() {
    if (!getDrawablesMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            getDrawablesMutex.withLock {
                val response = networkRequest<List<StylizedDrawablePathsGroupDataModel>, Unit>(
                    method = HttpMethod.Get,
                    endpointUrl = globalAppConfigurationState.payloadValue.drawableResourcesConfigurationPath.first
                )

                if (response.negative || response.payload == null) {
                    drawablesState.emit(DataState.Empty(response.message))
                } else {
                    drawablesState.emit(DataState.Success(response.payload, response.message))
                }
            }
        }
}

fun setAppLocale(language: String, syncServer: Boolean = true) {
    GlobalScope.launch(Dispatchers.ourIo) {
        val normalized = normalizeAppLanguagePreference(language)
        appLanguageState.emit(normalized)
        putLocalKv(KEY_APP_LOCALE, normalized)
        if (syncServer) syncUserPreferencesToServer()
    }
}

fun setAppTheme(themeId: Long, syncServer: Boolean = true) {
    GlobalScope.launch(Dispatchers.ourIo) {
        val normalized = normalizeAppThemePreference(themeId)
        // Emit immediately so UI changes now; local storage observer will keep it persistent.
        appThemeIdState.emit(normalized)
        putLocalKv(KEY_APP_THEME, normalized.toString())
        if (syncServer) syncUserPreferencesToServer()
    }
}

fun setAuthScreenAppLocale(language: String) {
    val normalized = normalizeAppLanguagePreference(language)
    authScreenPreferenceOverrideState.value = authScreenPreferenceOverrideState.value.copy(
        appLanguage = normalized,
        languageTouched = true
    )
    setAppLocale(normalized, syncServer = false)
}

fun setAuthScreenAppTheme(themeId: Long) {
    val normalized = normalizeAppThemePreference(themeId)
    authScreenPreferenceOverrideState.value = authScreenPreferenceOverrideState.value.copy(
        appThemeId = normalized,
        themeTouched = true
    )
    setAppTheme(normalized, syncServer = false)
}

fun setAuthScreenAppSizeMode(sizeModeId: Long) {
    val normalized = normalizeAppSizeModePreference(sizeModeId)
    authScreenPreferenceOverrideState.value = authScreenPreferenceOverrideState.value.copy(
        appSizeModeId = normalized,
        sizeModeTouched = true
    )
    setAppSizeMode(normalized, syncServer = false)
}

fun setAppSizeMode(sizeModeId: Long, syncServer: Boolean = true) {
    GlobalScope.launch(Dispatchers.ourIo) {
        val normalized = normalizeAppSizeModePreference(sizeModeId)
        appSizeModeIdState.emit(normalized)
        putLocalKv(KEY_APP_SIZE_MODE, normalized.toString())
        if (syncServer) syncUserPreferencesToServer()
    }
}

fun setAppMode(modeId: Int) {
    GlobalScope.launch {
        putLocalKv(KEY_APP_MODE, modeId.toString())
    }
}

fun updateGlobalAppConfiguration(
    configuration: GlobalAppConfigurationDataModel,
    resourceConfiguration: GlobalAppConfigurationDataModel
) {
    if (globalAppConfigurationState.value.value is DataState.Empty)
        globalAppConfigurationState.emit(DataState.Success(resourceConfiguration))
}

fun updateStrings(
    strings: List<LocalizedStringGroupDataModel>,
    resourceStrings: List<LocalizedStringGroupDataModel>
) {
    GlobalScope.launch(Dispatchers.ourIo) {
        stringAppNameState.emit(
            strings.extractString(0, appLanguageState.value) ?: resourceStrings.extractString(
                0,
                appLanguageState.value
            )!!
        )
        stringLogInState.emit(
            strings.extractString(1, appLanguageState.value) ?: resourceStrings.extractString(
                1,
                appLanguageState.value
            )!!
        )
        stringPhoneNumberState.emit(
            strings.extractString(2, appLanguageState.value) ?: resourceStrings.extractString(
                2,
                appLanguageState.value
            )!!
        )
        stringEnterPhoneNumberState.emit(
            strings.extractString(3, appLanguageState.value) ?: resourceStrings.extractString(3, appLanguageState.value)!!
        )
        stringEmailState.emit(
            strings.extractString(4, appLanguageState.value) ?: resourceStrings.extractString(
                4,
                appLanguageState.value
            )!!
        )
        stringEnterEmailAddressState.emit(
            strings.extractString(5, appLanguageState.value) ?: resourceStrings.extractString(5, appLanguageState.value)!!
        )
        stringPasswordState.emit(
            strings.extractString(6, appLanguageState.value) ?: resourceStrings.extractString(
                6,
                appLanguageState.value
            )!!
        )
        stringEnterPasswordState.emit(
            strings.extractString(7, appLanguageState.value) ?: resourceStrings.extractString(7, appLanguageState.value)!!
        )
        stringCancelState.emit(
            strings.extractString(8, appLanguageState.value) ?: resourceStrings.extractString(
                8,
                appLanguageState.value
            )!!
        )
        stringClearState.emit(
            strings.extractString(9, appLanguageState.value) ?: resourceStrings.extractString(
                9,
                appLanguageState.value
            )!!
        )
        stringAuthenticationFailedState.emit(
            strings.extractString(10, appLanguageState.value) ?: resourceStrings.extractString(10, appLanguageState.value)!!
        )
        stringPhoneNumberMustBeState.emit(
            strings.extractString(11, appLanguageState.value) ?: resourceStrings.extractString(11, appLanguageState.value)!!
        )
        stringEmailMustBeState.emit(
            strings.extractString(12, appLanguageState.value) ?: resourceStrings.extractString(12, appLanguageState.value)!!
        )
        stringPasswordMustBeState.emit(
            strings.extractString(13, appLanguageState.value) ?: resourceStrings.extractString(13, appLanguageState.value)!!
        )
        stringRepeatPasswordState.emit(
            strings.extractString(14, appLanguageState.value) ?: resourceStrings.extractString(14, appLanguageState.value)!!
        )
        stringPasswordsMustMatchState.emit(
            strings.extractString(15, appLanguageState.value) ?: resourceStrings.extractString(15, appLanguageState.value)!!
        )
        stringFirstNameState.emit(
            strings.extractString(16, appLanguageState.value) ?: resourceStrings.extractString(
                16,
                appLanguageState.value
            )!!
        )
        stringLastNameState.emit(
            strings.extractString(17, appLanguageState.value) ?: resourceStrings.extractString(
                17,
                appLanguageState.value
            )!!
        )
        stringEnterFirstNameState.emit(
            strings.extractString(18, appLanguageState.value) ?: resourceStrings.extractString(18, appLanguageState.value)!!
        )
        stringEnterLastNameState.emit(
            strings.extractString(19, appLanguageState.value) ?: resourceStrings.extractString(
                19,
                appLanguageState.value
            )!!
        )
        stringUserWithThisPhoneNumberIsAlreadyRegisteredState.emit(
            strings.extractString(20, appLanguageState.value) ?: resourceStrings.extractString(20, appLanguageState.value)!!
        )
        stringUserWithThisEmailAddressIsAlreadyRegisteredState.emit(
            strings.extractString(21, appLanguageState.value) ?: resourceStrings.extractString(21, appLanguageState.value)!!
        )
        stringSignUpState.emit(
            strings.extractString(22, appLanguageState.value) ?: resourceStrings.extractString(
                22,
                appLanguageState.value
            )!!
        )
        stringConfirmState.emit(
            strings.extractString(23, appLanguageState.value) ?: resourceStrings.extractString(
                23,
                appLanguageState.value
            )!!
        )
        stringSaleState.emit(
            strings.extractString(24, appLanguageState.value) ?: resourceStrings.extractString(
                24,
                appLanguageState.value
            )!!
        )
        stringReturnState.emit(
            strings.extractString(25, appLanguageState.value) ?: resourceStrings.extractString(
                25,
                appLanguageState.value
            )!!
        )
        stringSupplyState.emit(
            strings.extractString(26, appLanguageState.value) ?: resourceStrings.extractString(
                26,
                appLanguageState.value
            )!!
        )
        stringStockState.emit(
            strings.extractString(27, appLanguageState.value) ?: resourceStrings.extractString(
                27,
                appLanguageState.value
            )!!
        )
        stringMenuState.emit(
            strings.extractString(28, appLanguageState.value) ?: resourceStrings.extractString(
                28,
                appLanguageState.value
            )!!
        )
        stringBackState.emit(
            strings.extractString(29, appLanguageState.value) ?: resourceStrings.extractString(
                29,
                appLanguageState.value
            )!!
        )
        stringAddGoodsItemState.emit(
            strings.extractString(30, appLanguageState.value) ?: resourceStrings.extractString(
                30,
                appLanguageState.value
            )!!
        )
        stringEditGoodsItemState.emit(
            strings.extractString(31, appLanguageState.value) ?: resourceStrings.extractString(
                31,
                appLanguageState.value
            )!!
        )
        stringUserAccountState.emit(
            strings.extractString(32, appLanguageState.value) ?: resourceStrings.extractString(32, appLanguageState.value)!!
        )
        stringGoodsCategoriesState.emit(
            strings.extractString(33, appLanguageState.value) ?: resourceStrings.extractString(33, appLanguageState.value)!!
        )
        stringAddGoodsCategoryState.emit(
            strings.extractString(34, appLanguageState.value) ?: resourceStrings.extractString(34, appLanguageState.value)!!
        )
        stringEditGoodsCategoryState.emit(
            strings.extractString(35, appLanguageState.value) ?: resourceStrings.extractString(35, appLanguageState.value)!!
        )
        stringStoresState.emit(
            strings.extractString(36, appLanguageState.value) ?: resourceStrings.extractString(
                36,
                appLanguageState.value
            )!!
        )
        stringAddStoreState.emit(
            strings.extractString(37, appLanguageState.value) ?: resourceStrings.extractString(
                37,
                appLanguageState.value
            )!!
        )
        stringEditStoreState.emit(
            strings.extractString(38, appLanguageState.value) ?: resourceStrings.extractString(
                38,
                appLanguageState.value
            )!!
        )
        stringSubscriptionState.emit(
            strings.extractString(39, appLanguageState.value) ?: resourceStrings.extractString(
                39,
                appLanguageState.value
            )!!
        )
        stringSubscriptionPlansState.emit(
            strings.extractString(40, appLanguageState.value) ?: resourceStrings.extractString(40, appLanguageState.value)!!
        )
        stringTransactionHistoryState.emit(
            strings.extractString(41, appLanguageState.value) ?: resourceStrings.extractString(41, appLanguageState.value)!!
        )
        stringReceiptState.emit(
            strings.extractString(42, appLanguageState.value) ?: resourceStrings.extractString(
                42,
                appLanguageState.value
            )!!
        )
        stringAnalyticsState.emit(
            strings.extractString(43, appLanguageState.value) ?: resourceStrings.extractString(
                43,
                appLanguageState.value
            )!!
        )
        stringWorkersState.emit(
            strings.extractString(44, appLanguageState.value) ?: resourceStrings.extractString(
                44,
                appLanguageState.value
            )!!
        )
        stringAddWorkerState.emit(
            strings.extractString(45, appLanguageState.value) ?: resourceStrings.extractString(
                45,
                appLanguageState.value
            )!!
        )
        stringEditWorkerState.emit(
            strings.extractString(46, appLanguageState.value) ?: resourceStrings.extractString(
                46,
                appLanguageState.value
            )!!
        )
        stringSuppliersState.emit(
            strings.extractString(47, appLanguageState.value) ?: resourceStrings.extractString(
                47,
                appLanguageState.value
            )!!
        )
        stringAddSupplierState.emit(
            strings.extractString(48, appLanguageState.value) ?: resourceStrings.extractString(48, appLanguageState.value)!!
        )
        stringEditSupplierState.emit(
            strings.extractString(49, appLanguageState.value) ?: resourceStrings.extractString(
                49,
                appLanguageState.value
            )!!
        )
        stringDebtorsState.emit(
            strings.extractString(50, appLanguageState.value) ?: resourceStrings.extractString(
                50,
                appLanguageState.value
            )!!
        )
        stringCloseDebtState.emit(
            strings.extractString(51, appLanguageState.value) ?: resourceStrings.extractString(
                51,
                appLanguageState.value
            )!!
        )
        stringDevicesState.emit(
            strings.extractString(52, appLanguageState.value) ?: resourceStrings.extractString(
                52,
                appLanguageState.value
            )!!
        )
        stringAppLanguageState.emit(
            strings.extractString(53, appLanguageState.value) ?: resourceStrings.extractString(53, appLanguageState.value)!!
        )
        stringAppThemeState.emit(
            strings.extractString(54, appLanguageState.value) ?: resourceStrings.extractString(
                54,
                appLanguageState.value
            )!!
        )
        stringSelectState.emit(
            strings.extractString(55, appLanguageState.value) ?: resourceStrings.extractString(
                55,
                appLanguageState.value
            )!!
        )
        stringUserWithThisPhoneNumberAndEmailAddressIsAlreadyRegisteredState.emit(
            strings.extractString(
                56,
                appLanguageState.value
            ) ?: resourceStrings.extractString(56, appLanguageState.value)!!
        )
        stringFirstNameCannotBeEmptyOrJustWhitespacesState.emit(
            strings.extractString(57, appLanguageState.value) ?: resourceStrings.extractString(57, appLanguageState.value)!!
        )
        stringLastNameCannotBeEmptyOrJustWhitespacesState.emit(
            strings.extractString(58, appLanguageState.value) ?: resourceStrings.extractString(58, appLanguageState.value)!!
        )
        stringSystemLanguageState.emit(
            strings.extractString(59, appLanguageState.value) ?: resourceStrings.extractString(59, appLanguageState.value)!!
        )
        stringBluetoothPermissionRequiredState.emit(
            strings.extractString(60, appLanguageState.value) ?: resourceStrings.extractString(60, appLanguageState.value)!!
        )
        stringForSearchAndConnectionToBluetoothBarcodeScannersAndReceiptPrintersState.emit(
            strings.extractString(
                61,
                appLanguageState.value
            ) ?: resourceStrings.extractString(61, appLanguageState.value)!!
        )
        stringForSearchAndConnectionToBluetoothBarcodeScannersAndReceiptPrintersYouCanGrantItInAppSettingsState.emit(
            strings.extractString(62, appLanguageState.value) ?: resourceStrings.extractString(62, appLanguageState.value)!!
        )
        stringBluetoothDisabledState.emit(
            strings.extractString(63, appLanguageState.value) ?: resourceStrings.extractString(63, appLanguageState.value)!!
        )
        stringEnableForSearchAndConnectionToBluetoothBarcodeScannersAndReceiptPrintersState.emit(
            strings.extractString(
                64,
                appLanguageState.value
            ) ?: resourceStrings.extractString(64, appLanguageState.value)!!
        )
        stringSearchByAnyDataState.emit(
            strings.extractString(65, appLanguageState.value) ?: resourceStrings.extractString(65, appLanguageState.value)!!
        )
        stringListEmptyState.emit(
            strings.extractString(66, appLanguageState.value) ?: resourceStrings.extractString(
                66,
                appLanguageState.value
            )!!
        )
        stringNoMatchesState.emit(
            strings.extractString(67, appLanguageState.value) ?: resourceStrings.extractString(
                67,
                appLanguageState.value
            )!!
        )
        stringNameState.emit(
            strings.extractString(68, appLanguageState.value) ?: resourceStrings.extractString(
                68,
                appLanguageState.value
            )!!
        )
        stringBarcodeState.emit(
            strings.extractString(69, appLanguageState.value) ?: resourceStrings.extractString(
                69,
                appLanguageState.value
            )!!
        )
        stringSupplyPriceState.emit(
            strings.extractString(70, appLanguageState.value) ?: resourceStrings.extractString(70, appLanguageState.value)!!
        )
        stringSalePriceState.emit(
            strings.extractString(71, appLanguageState.value) ?: resourceStrings.extractString(
                71,
                appLanguageState.value
            )!!
        )
        stringReturnPriceState.emit(
            strings.extractString(72, appLanguageState.value) ?: resourceStrings.extractString(72, appLanguageState.value)!!
        )
        stringCategoryState.emit(
            strings.extractString(73, appLanguageState.value) ?: resourceStrings.extractString(
                73,
                appLanguageState.value
            )!!
        )
        stringSupplierState.emit(
            strings.extractString(74, appLanguageState.value) ?: resourceStrings.extractString(
                74,
                appLanguageState.value
            )!!
        )
        stringEnterBarcodeState.emit(
            strings.extractString(75, appLanguageState.value) ?: resourceStrings.extractString(
                75,
                appLanguageState.value
            )!!
        )
        stringEnterNameState.emit(
            strings.extractString(76, appLanguageState.value) ?: resourceStrings.extractString(
                76,
                appLanguageState.value
            )!!
        )
        stringEnterSupplyPriceState.emit(
            strings.extractString(77, appLanguageState.value) ?: resourceStrings.extractString(77, appLanguageState.value)!!
        )
        stringEnterSalePriceState.emit(
            strings.extractString(78, appLanguageState.value) ?: resourceStrings.extractString(78, appLanguageState.value)!!
        )
        stringEnterReturnPriceState.emit(
            strings.extractString(79, appLanguageState.value) ?: resourceStrings.extractString(79, appLanguageState.value)!!
        )
        stringSelectCategoryState.emit(
            strings.extractString(80, appLanguageState.value) ?: resourceStrings.extractString(80, appLanguageState.value)!!
        )
        stringSelectSupplierState.emit(
            strings.extractString(81, appLanguageState.value) ?: resourceStrings.extractString(81, appLanguageState.value)!!
        )
        stringEditState.emit(
            strings.extractString(82, appLanguageState.value) ?: resourceStrings.extractString(
                82,
                appLanguageState.value
            )!!
        )
        stringChangePasswordState.emit(
            strings.extractString(83, appLanguageState.value) ?: resourceStrings.extractString(83, appLanguageState.value)!!
        )
        stringNewPasswordState.emit(
            strings.extractString(84, appLanguageState.value) ?: resourceStrings.extractString(84, appLanguageState.value)!!
        )
        stringEnterNewPasswordState.emit(
            strings.extractString(85, appLanguageState.value) ?: resourceStrings.extractString(85, appLanguageState.value)!!
        )
        stringRepeatNewPasswordState.emit(
            strings.extractString(86, appLanguageState.value) ?: resourceStrings.extractString(86, appLanguageState.value)!!
        )
        stringConfirmationPasswordState.emit(
            strings.extractString(87, appLanguageState.value) ?: resourceStrings.extractString(87, appLanguageState.value)!!
        )
        stringRequiredToEditAccountState.emit(
            strings.extractString(88, appLanguageState.value) ?: resourceStrings.extractString(88, appLanguageState.value)!!
        )
        stringAccountSuccessfullyUpdatedState.emit(
            strings.extractString(89, appLanguageState.value) ?: resourceStrings.extractString(89, appLanguageState.value)!!
        )
        stringLoggingOutState.emit(
            strings.extractString(90, appLanguageState.value) ?: resourceStrings.extractString(90, appLanguageState.value)!!
        )
        stringSessionTimeExpiredLoggingOutState.emit(
            strings.extractString(91, appLanguageState.value) ?: resourceStrings.extractString(91, appLanguageState.value)!!
        )
        stringAliasState.emit(
            strings.extractString(92, appLanguageState.value) ?: resourceStrings.extractString(
                92,
                appLanguageState.value
            )!!
        )
        stringDescriptionState.emit(
            strings.extractString(93, appLanguageState.value) ?: resourceStrings.extractString(93, appLanguageState.value)!!
        )
        stringEnterAliasState.emit(
            strings.extractString(94, appLanguageState.value) ?: resourceStrings.extractString(
                94,
                appLanguageState.value
            )!!
        )
        stringEnterDescriptionState.emit(
            strings.extractString(95, appLanguageState.value) ?: resourceStrings.extractString(95, appLanguageState.value)!!
        )
        stringOptionalState.emit(
            strings.extractString(96, appLanguageState.value) ?: resourceStrings.extractString(
                96,
                appLanguageState.value
            )!!
        )
        stringLoggingInState.emit(
            strings.extractString(97, appLanguageState.value) ?: resourceStrings.extractString(
                97,
                appLanguageState.value
            )!!
        )
        stringSigningUpState.emit(
            strings.extractString(98, appLanguageState.value) ?: resourceStrings.extractString(
                98,
                appLanguageState.value
            )!!
        )
        stringCompanyFormState.emit(
            strings.extractString(99, appLanguageState.value) ?: resourceStrings.extractString(
                99,
                appLanguageState.value
            )!!
        )
        stringMeasurementUnitState.emit(
            strings.extractString(100, appLanguageState.value) ?: resourceStrings.extractString(
                100,
                appLanguageState.value
            )!!
        )
        stringNoActiveStoreState.emit(
            strings.extractString(101, appLanguageState.value) ?: resourceStrings.extractString(
                101,
                appLanguageState.value
            )!!
        )
        stringSelectInMenuState.emit(
            strings.extractString(102, appLanguageState.value) ?: resourceStrings.extractString(
                102,
                appLanguageState.value
            )!!
        )
        stringSupplyDataState.emit(
            strings.extractString(103, appLanguageState.value) ?: resourceStrings.extractString(
                103,
                appLanguageState.value
            )!!
        )
        stringSaleDataState.emit(
            strings.extractString(104, appLanguageState.value) ?: resourceStrings.extractString(
                104,
                appLanguageState.value
            )!!
        )
        stringReturnDataState.emit(
            strings.extractString(105, appLanguageState.value) ?: resourceStrings.extractString(
                105,
                appLanguageState.value
            )!!
        )
        stringAddSupplyDataState.emit(
            strings.extractString(106, appLanguageState.value) ?: resourceStrings.extractString(
                106,
                appLanguageState.value
            )!!
        )
        stringAddSaleDataState.emit(
            strings.extractString(107, appLanguageState.value) ?: resourceStrings.extractString(
                107,
                appLanguageState.value
            )!!
        )
        stringAddReturnDataState.emit(
            strings.extractString(108, appLanguageState.value) ?: resourceStrings.extractString(
                108,
                appLanguageState.value
            )!!
        )
        stringAddBarcodeState.emit(
            strings.extractString(109, appLanguageState.value) ?: resourceStrings.extractString(
                109,
                appLanguageState.value
            )!!
        )
        stringAddNameState.emit(
            strings.extractString(110, appLanguageState.value) ?: resourceStrings.extractString(
                110,
                appLanguageState.value
            )!!
        )
        stringPaymentState.emit(
            strings.extractString(111, appLanguageState.value) ?: resourceStrings.extractString(
                111,
                appLanguageState.value
            )!!
        )
        stringAllState.emit(
            strings.extractString(112, appLanguageState.value) ?: resourceStrings.extractString(
                112,
                appLanguageState.value
            )!!
        )
        stringQuickState.emit(
            strings.extractString(113, appLanguageState.value) ?: resourceStrings.extractString(
                113,
                appLanguageState.value
            )!!
        )
        stringCategoriesState.emit(
            strings.extractString(114, appLanguageState.value) ?: resourceStrings.extractString(
                114,
                appLanguageState.value
            )!!
        )
        stringMainState.emit(
            strings.extractString(115, appLanguageState.value) ?: resourceStrings.extractString(
                115,
                appLanguageState.value
            )!!
        )
        stringAddTranslationState.emit(
            strings.extractString(116, appLanguageState.value) ?: resourceStrings.extractString(
                116,
                appLanguageState.value
            )!!
        )
        stringSetActiveState.emit(
            strings.extractString(117, appLanguageState.value) ?: resourceStrings.extractString(
                117,
                appLanguageState.value
            )!!
        )
        stringOutOfStockState.emit(
            strings.extractString(118, appLanguageState.value) ?: resourceStrings.extractString(
                118,
                appLanguageState.value
            )!!
        )
        stringDeleteState.emit(
            strings.extractString(119, appLanguageState.value) ?: resourceStrings.extractString(
                119,
                appLanguageState.value
            )!!
        )
        stringCashState.emit(
            strings.extractString(120, appLanguageState.value) ?: resourceStrings.extractString(
                120,
                appLanguageState.value
            )!!
        )
        stringCashlessState.emit(
            strings.extractString(121, appLanguageState.value) ?: resourceStrings.extractString(
                121,
                appLanguageState.value
            )!!
        )
        stringMixedState.emit(
            strings.extractString(122, appLanguageState.value) ?: resourceStrings.extractString(
                122,
                appLanguageState.value
            )!!
        )
        stringAddState.emit(
            strings.extractString(123, appLanguageState.value) ?: resourceStrings.extractString(
                123,
                appLanguageState.value
            )!!
        )
        stringSubtractState.emit(
            strings.extractString(124, appLanguageState.value) ?: resourceStrings.extractString(
                124,
                appLanguageState.value
            )!!
        )
        stringCurrentQuantityDataState.emit(
            strings.extractString(125, appLanguageState.value) ?: resourceStrings.extractString(
                125,
                appLanguageState.value
            )!!
        )
        stringEnterQuantityState.emit(
            strings.extractString(126, appLanguageState.value) ?: resourceStrings.extractString(
                126,
                appLanguageState.value
            )!!
        )
        stringAddQuantityDataState.emit(
            strings.extractString(127, appLanguageState.value) ?: resourceStrings.extractString(
                127,
                appLanguageState.value
            )!!
        )
        stringShelfBatchState.emit(
            strings.extractString(128, appLanguageState.value) ?: resourceStrings.extractString(
                128,
                appLanguageState.value
            )!!
        )
        stringActiveStoreState.emit(
            strings.extractString(129, appLanguageState.value) ?: resourceStrings.extractString(
                129,
                appLanguageState.value
            )!!
        )
        stringMakeInactiveState.emit(
            strings.extractString(130, appLanguageState.value) ?: resourceStrings.extractString(
                130,
                appLanguageState.value
            )!!
        )
        stringCartEmptyState.emit(
            strings.extractString(131, appLanguageState.value) ?: resourceStrings.extractString(
                131,
                appLanguageState.value
            )!!
        )
        stringCompleteState.emit(
            strings.extractString(132, appLanguageState.value) ?: resourceStrings.extractString(
                132,
                appLanguageState.value
            )!!
        )
        stringNoActiveWorkshiftState.emit(
            strings.extractString(133, appLanguageState.value) ?: resourceStrings.extractString(
                133,
                appLanguageState.value
            )!!
        )
        stringCartState.emit(
            strings.extractString(134, appLanguageState.value) ?: resourceStrings.extractString(
                134,
                appLanguageState.value
            )!!
        )
        stringAppModeState.emit(
            strings.extractString(135, appLanguageState.value) ?: resourceStrings.extractString(
                135,
                appLanguageState.value
            )!!
        )
        stringFinancesState.emit(
            strings.extractString(136, appLanguageState.value) ?: resourceStrings.extractString(
                136,
                appLanguageState.value
            )!!
        )
        stringItemsState.emit(
            strings.extractString(137, appLanguageState.value) ?: resourceStrings.extractString(
                137,
                appLanguageState.value
            )!!
        )
        stringBatchesState.emit(
            strings.extractString(138, appLanguageState.value) ?: resourceStrings.extractString(
                138,
                appLanguageState.value
            )!!
        )
        stringStandardPricesForSuppliersState.emit(
            strings.extractString(139, appLanguageState.value) ?: resourceStrings.extractString(
                139,
                appLanguageState.value
            )!!
        )
        stringEditableForIndividualBatchesState.emit(
            strings.extractString(140, appLanguageState.value) ?: resourceStrings.extractString(
                140,
                appLanguageState.value
            )!!
        )
        stringBatchesDataState.emit(
            strings.extractString(141, appLanguageState.value) ?: resourceStrings.extractString(
                141,
                appLanguageState.value
            )!!
        )
        stringReceiptNumberState.emit(
            strings.extractString(142, appLanguageState.value) ?: resourceStrings.extractString(
                142,
                appLanguageState.value
            ) ?: stringReceiptNumberState.value
        )
        stringTransactionIdState.emit(
            strings.extractString(143, appLanguageState.value) ?: resourceStrings.extractString(
                143,
                appLanguageState.value
            ) ?: stringTransactionIdState.value
        )
        stringDateState.emit(
            strings.extractString(144, appLanguageState.value) ?: resourceStrings.extractString(
                144,
                appLanguageState.value
            ) ?: stringDateState.value
        )
        stringCashierState.emit(
            strings.extractString(145, appLanguageState.value) ?: resourceStrings.extractString(
                145,
                appLanguageState.value
            ) ?: stringCashierState.value
        )
        stringStoreState.emit(
            strings.extractString(146, appLanguageState.value) ?: resourceStrings.extractString(
                146,
                appLanguageState.value
            ) ?: stringStoreState.value
        )
        stringAddressState.emit(
            strings.extractString(147, appLanguageState.value) ?: resourceStrings.extractString(
                147,
                appLanguageState.value
            ) ?: stringAddressState.value
        )
        stringPhoneState.emit(
            strings.extractString(148, appLanguageState.value) ?: resourceStrings.extractString(
                148,
                appLanguageState.value
            ) ?: stringPhoneState.value
        )
        stringTotalState.emit(
            strings.extractString(149, appLanguageState.value) ?: resourceStrings.extractString(
                149,
                appLanguageState.value
            ) ?: stringTotalState.value
        )
        stringPaidState.emit(
            strings.extractString(150, appLanguageState.value) ?: resourceStrings.extractString(
                150,
                appLanguageState.value
            ) ?: stringPaidState.value
        )
        stringDebtState.emit(
            strings.extractString(151, appLanguageState.value) ?: resourceStrings.extractString(
                151,
                appLanguageState.value
            ) ?: stringDebtState.value
        )
        stringDebtorState.emit(
            strings.extractString(152, appLanguageState.value) ?: resourceStrings.extractString(
                152,
                appLanguageState.value
            ) ?: stringDebtorState.value
        )
        stringDebtorPhoneState.emit(
            strings.extractString(153, appLanguageState.value) ?: resourceStrings.extractString(
                153,
                appLanguageState.value
            ) ?: stringDebtorPhoneState.value
        )
        stringChangeState.emit(
            strings.extractString(154, appLanguageState.value) ?: resourceStrings.extractString(
                154,
                appLanguageState.value
            ) ?: stringChangeState.value
        )
        stringVatState.emit(
            strings.extractString(155, appLanguageState.value) ?: resourceStrings.extractString(
                155,
                appLanguageState.value
            ) ?: stringVatState.value
        )
        stringVatNotSpecifiedState.emit(
            strings.extractString(156, appLanguageState.value) ?: resourceStrings.extractString(
                156,
                appLanguageState.value
            ) ?: stringVatNotSpecifiedState.value
        )
        stringFiscalStatusState.emit(
            strings.extractString(157, appLanguageState.value) ?: resourceStrings.extractString(
                157,
                appLanguageState.value
            ) ?: stringFiscalStatusState.value
        )
        stringNonFiscalSoftwareReceiptState.emit(
            strings.extractString(158, appLanguageState.value) ?: resourceStrings.extractString(
                158,
                appLanguageState.value
            ) ?: stringNonFiscalSoftwareReceiptState.value
        )
        stringThankYouState.emit(
            strings.extractString(159, appLanguageState.value) ?: resourceStrings.extractString(
                159,
                appLanguageState.value
            ) ?: stringThankYouState.value
        )
        stringNoItemsState.emit(
            strings.extractString(160, appLanguageState.value) ?: resourceStrings.extractString(
                160,
                appLanguageState.value
            ) ?: stringNoItemsState.value
        )
        stringPdfState.emit(
            strings.extractString(161, appLanguageState.value) ?: resourceStrings.extractString(
                161,
                appLanguageState.value
            ) ?: stringPdfState.value
        )
        stringShareState.emit(
            strings.extractString(162, appLanguageState.value) ?: resourceStrings.extractString(
                162,
                appLanguageState.value
            ) ?: stringShareState.value
        )
        stringWhatsAppState.emit(
            strings.extractString(163, appLanguageState.value) ?: resourceStrings.extractString(
                163,
                appLanguageState.value
            ) ?: stringWhatsAppState.value
        )
        stringPrintState.emit(
            strings.extractString(164, appLanguageState.value) ?: resourceStrings.extractString(
                164,
                appLanguageState.value
            ) ?: stringPrintState.value
        )
        stringQuitState.emit(
            strings.extractString(165, appLanguageState.value) ?: resourceStrings.extractString(
                165,
                appLanguageState.value
            ) ?: stringQuitState.value
        )
        stringReceiptPdfSavedState.emit(
            strings.extractString(166, appLanguageState.value) ?: resourceStrings.extractString(
                166,
                appLanguageState.value
            ) ?: stringReceiptPdfSavedState.value
        )
        stringReceiptSharedState.emit(
            strings.extractString(167, appLanguageState.value) ?: resourceStrings.extractString(
                167,
                appLanguageState.value
            ) ?: stringReceiptSharedState.value
        )
        stringReceiptSentToWhatsAppState.emit(
            strings.extractString(168, appLanguageState.value) ?: resourceStrings.extractString(
                168,
                appLanguageState.value
            ) ?: stringReceiptSentToWhatsAppState.value
        )
        stringReceiptSentToPrinterState.emit(
            strings.extractString(169, appLanguageState.value) ?: resourceStrings.extractString(
                169,
                appLanguageState.value
            ) ?: stringReceiptSentToPrinterState.value
        )
        stringReceiptActionFailedState.emit(
            strings.extractString(170, appLanguageState.value) ?: resourceStrings.extractString(
                170,
                appLanguageState.value
            ) ?: stringReceiptActionFailedState.value
        )
        stringGoodsReceiptTitleState.emit(
            strings.extractString(171, appLanguageState.value) ?: resourceStrings.extractString(
                171,
                appLanguageState.value
            ) ?: stringGoodsReceiptTitleState.value
        )
        stringSaleReceiptTitleState.emit(
            strings.extractString(172, appLanguageState.value) ?: resourceStrings.extractString(
                172,
                appLanguageState.value
            ) ?: stringSaleReceiptTitleState.value
        )
        stringReturnReceiptTitleState.emit(
            strings.extractString(173, appLanguageState.value) ?: resourceStrings.extractString(
                173,
                appLanguageState.value
            ) ?: stringReturnReceiptTitleState.value
        )
        stringSupplyReceiptTitleState.emit(
            strings.extractString(174, appLanguageState.value) ?: resourceStrings.extractString(
                174,
                appLanguageState.value
            ) ?: stringSupplyReceiptTitleState.value
        )
        stringDraftState.emit(
            strings.extractString(175, appLanguageState.value) ?: resourceStrings.extractString(
                175,
                appLanguageState.value
            ) ?: stringDraftState.value
        )
        stringNoNameState.emit(
            strings.extractString(176, appLanguageState.value) ?: resourceStrings.extractString(
                176,
                appLanguageState.value
            ) ?: stringNoNameState.value
        )

    }
}

fun updateDrawables(
    drawables: List<StylizedDrawablePathsGroupDataModel>,
    resourceDrawables: List<StylizedDrawablePathsGroupDataModel>
) {
    GlobalScope.launch(Dispatchers.ourIo) {
        val themeId = appThemeIdState.value
        fun drawablePath(id: Long): String {
            val normalizedThemeId = if (themeId == 1L) 1L else 0L
            return drawables.extractPath(id, themeId)
                ?: drawables.extractPath(id, normalizedThemeId)
                ?: resourceDrawables.extractPath(id, themeId)
                ?: resourceDrawables.extractPath(id, normalizedThemeId)
                ?: "svg/${id}_${normalizedThemeId}.svg"
        }
        drawablePathAITALogoState.emit(
            drawablePath(0L)
        )
        drawablePathIconPasswordState.emit(
            drawablePath(1L)
        )
        drawablePathIconSecurityState.emit(
            drawablePath(1L)
        )
        drawablePathIconCancelState.emit(
            drawablePath(2L)
        )
        drawablePathIconEyeHideState.emit(
            drawablePath(3L)
        )
        drawablePathIconEyeShowState.emit(
            drawablePath(4L)
        )
        drawablePathIconEmailState.emit(
            drawablePath(5L)
        )
        drawablePathIconPhoneState.emit(
            drawablePath(6L)
        )
        drawablePathIconExpandMoreState.emit(
            drawablePath(7L)
        )
        drawablePathIconExpandLessState.emit(
            drawablePath(8L)
        )
        drawablePathIconPersonState.emit(
            drawablePath(9L)
        )
        drawablePathIconTransactionSaleState.emit(
            drawablePath(10L)
        )
        drawablePathIconTransactionReturnState.emit(
            drawablePath(11L)
        )
        drawablePathIconTransactionSupplyState.emit(
            drawablePath(12L)
        )
        drawablePathIconTransactionSelectionState.emit(
            drawablePath(57L)
        )
        drawablePathIconStockState.emit(
            drawablePath(13L)
        )
        drawablePathIconMenuState.emit(
            drawablePath(14L)
        )
        drawablePathIconBackArrowState.emit(
            drawablePath(15L)
        )
        drawablePathIconAddState.emit(
            drawablePath(16L)
        )
        drawablePathIconUserAccountState.emit(
            drawablePath(17L)
        )
        drawablePathIconGoodsCategoriesState.emit(
            drawablePath(18L)
        )
        drawablePathIconStoresState.emit(
            drawablePath(19L)
        )
        drawablePathIconTransactionHistoryState.emit(
            drawablePath(20L)
        )
        drawablePathIconLogState.emit(
            drawablePath(49L)
        )
        drawablePathIconPromosState.emit(
            drawablePath(50L)
        )
        drawablePathIconAnalyticsState.emit(
            drawablePath(21L)
        )
        drawablePathIconAnalyticsReportState.emit(
            drawablePath(62L)
        )
        drawablePathIconLabelPrinterState.emit(
            drawablePath(63L)
        )
        drawablePathIconBarcodeGenerateState.emit(
            drawablePath(64L)
        )
        drawablePathIconPrintTagState.emit(
            drawablePath(65L)
        )
        drawablePathIconWorkersState.emit(
            drawablePath(22L)
        )
        drawablePathIconSuppliersState.emit(
            drawablePath(23L)
        )
        drawablePathIconDebtorsState.emit(
            drawablePath(24L)
        )
        drawablePathIconDevicesState.emit(
            drawablePath(25L)
        )
        drawablePathIconAppLanguageState.emit(
            drawablePath(26L)
        )
        drawablePathIconAppThemeState.emit(
            drawablePath(27L)
        )
        drawablePathIconCheckState.emit(
            drawablePath(28L)
        )
        drawablePathIconEditState.emit(
            drawablePath(29L)
        )
        drawablePathIconSettingsState.emit(
            drawablePath(30L)
        )
        drawablePathIconSearchState.emit(
            drawablePath(31L)
        )
        drawablePathIconBarcodeCamScannerState.emit(
            drawablePath(32L)
        )
        drawablePathIconBarcodeScannerState.emit(
            drawablePath(51L)
        )
        drawablePathIconBarcodeTypeState.emit(
            drawablePath(55L)
        )
        drawablePathIconVoiceInputState.emit(
            drawablePath(52L)
        )
        drawablePathIconResponseState.emit(
            drawablePath(53L)
        )
        drawablePathIconDeleteState.emit(
            drawablePath(33L)
        )
        drawablePathIconExitState.emit(
            drawablePath(34L)
        )
        drawablePathIconSwitchState.emit(
            drawablePath(35L)
        )
        drawablePathIconSortState.emit(
            drawablePath(61L)
        )
        drawablePathIconCartState.emit(
            drawablePath(36L)
        )
        drawablePathIconAddCartState.emit(
            drawablePath(37L)
        )
        drawablePathIconSubtractState.emit(
            drawablePath(38L)
        )
        drawablePathIconReceiptState.emit(
            drawablePath(39L)
        )
        drawablePathIconFinancesState.emit(
            drawablePath(40L)
        )
        drawablePathIconClipboardState.emit(
            drawablePath(41L)
        )
        drawablePathIconSupportState.emit(
            drawablePath(42L)
        )
        drawablePathIconSubscriptionState.emit(
            drawablePath(43L)
        )
        drawablePathIconThemeLightState.emit(
            drawablePath(44L)
        )
        drawablePathIconThemeDarkState.emit(
            drawablePath(45L)
        )
        drawablePathIconShareState.emit(
            drawablePath(46L)
        )
        drawablePathIconWhatsAppState.emit(
            drawablePath(47L)
        )
        drawablePathIconRefreshState.emit(
            drawablePath(54L)
        )
        drawablePathIconAppScaleState.emit(
            drawablePath(48L)
        )
    }
}

suspend fun putLocalKv(key: String, value: String?) {
    appDatabase.app_databaseQueries.insertKv(key, value)
}

suspend fun getLocalKv(key: String): String? =
    appDatabase.app_databaseQueries.selectKvByKey(key).awaitAsOneOrNull()?.value_

suspend fun deleteLocalKv(key: String) {
    appDatabase.app_databaseQueries.deleteKv(key)
}

fun observeLocalKv(key: String): Flow<String?> =
    appDatabase.app_databaseQueries.selectKvByKey(key)
        .asFlow()
        .mapToOneOrNull(Dispatchers.ourIo)
        .map {
            it?.value_
        }


private const val CACHE_PREFIX = "cache_json:"
private const val CACHE_GLOBAL_CONFIG = "global_config"
private const val CACHE_STRINGS = "strings"
private const val CACHE_DIMENSIONS = "dimensions"
private const val CACHE_COLORS = "colors"
private const val CACHE_DRAWABLES = "drawables"
private const val CACHE_USER = "user"
private const val CACHE_STORES = "stores"
private const val CACHE_SUPPLIERS = "suppliers"
private const val CACHE_GENERIC_GOODS_CATEGORIES = "generic_goods_categories"
private const val CACHE_GENERIC_GOODS_ITEMS = "generic_goods_items"
private const val CACHE_NOTIFICATIONS = "notifications"
private const val CACHE_SECURITY_SESSIONS = "security_sessions"
private const val CACHE_SECURITY_SESSION_HISTORY = "security_session_history"
private const val CACHE_MY_WORKER_MEMBERSHIPS = "my_worker_memberships"
private const val CACHE_MY_WORKER_REQUESTS = "my_worker_requests"
private const val CACHE_USER_FINANCE_DASHBOARD = "user_finance_dashboard"
private const val CACHE_SUBSCRIPTION_PLANS = "subscription_plans"
private const val CACHE_LOCAL_NETWORK_STATE = "local_network_state"
private const val CACHE_LOCAL_NETWORK_QUEUE = "local_network_queue"
private const val CACHE_CART_SALE_METHOD_IDS = "transaction_cart_sale_method_ids"
private const val CACHE_TRANSACTION_PAYMENT_DRAFTS = "transaction_payment_drafts"
private const val CACHE_TRANSACTION_SUPPLY_SUPPLIER_IDS = "transaction_supply_supplier_ids"
private const val CACHE_TRANSACTION_RETURN_REASONS = "transaction_return_reasons"
private const val CACHE_CART_CONDITION_CHECKS = "transaction_cart_condition_checks"
private const val CACHE_TRANSACTION_CART_SCROLL_STATES = "transaction_cart_scroll_states"
private const val CACHE_PENDING_SESSION_CLEANUPS = "pending_session_cleanups"
private const val CACHE_PENDING_WORKSHIFT_ENDS = "pending_workshift_ends"

private var cachedGlobalConfigurationPrimedForNetwork = false
private val cachedGlobalConfigurationPrimeMutex = Mutex()

@PublishedApi
internal suspend fun ensureCachedGlobalConfigurationPrimedForNetwork() {
    if (cachedGlobalConfigurationPrimedForNetwork) return

    cachedGlobalConfigurationPrimeMutex.withLock {
        if (cachedGlobalConfigurationPrimedForNetwork) return@withLock

        getJsonCache<GlobalAppConfigurationDataModel>(CACHE_GLOBAL_CONFIG)?.let { cachedConfiguration ->
            val currentConfiguration = globalAppConfigurationState.payloadValue
            // Keep the configured server address stable. Cached/server configuration may be old
            // after Wi-Fi/hotspot changes, so it must not silently replace the address that the
            // client is currently using.
            val cachedConfigurationForThisInstall = cachedConfiguration.copy(
                serverUrl = chooseClientServerUrlPair(
                    current = currentConfiguration.serverUrl,
                    incoming = cachedConfiguration.serverUrl
                )
            )
            globalAppConfigurationState.emit(DataState.Success(cachedConfigurationForThisInstall, cacheMessage()))
        }

        cachedGlobalConfigurationPrimedForNetwork = true
    }
}

private val AITA_SERVER_ENDPOINT_ROOT_SEGMENTS = setOf(
    "auth",
    "cashregister",
    "config",
    "debtors",
    "finance",
    "generic",
    "notifications",
    "res",
    "rt",
    "security",
    "stock",
    "stockbatches",
    "stores",
    "subscriptions",
    "suppliers",
    "suppliergoodsprices",
    "supplierorders",
    "support",
    "transactions",
    "user",
    "workers",
    "workshifts"
)

private fun String.withoutKnownAitaEndpointPath(): String {
    val noQueryOrFragment = substringBefore('#').substringBefore('?').trimEnd('/')
    val schemeSeparatorIndex = noQueryOrFragment.indexOf("://")
    val authorityStartIndex = if (schemeSeparatorIndex >= 0) schemeSeparatorIndex + 3 else 0
    val firstPathSlashIndex = noQueryOrFragment.indexOf('/', startIndex = authorityStartIndex)
    if (firstPathSlashIndex < 0) return noQueryOrFragment

    val firstPathSegment = noQueryOrFragment
        .substring(firstPathSlashIndex + 1)
        .substringBefore('/')
        .lowercase()

    return if (firstPathSegment in AITA_SERVER_ENDPOINT_ROOT_SEGMENTS) {
        noQueryOrFragment.substring(0, firstPathSlashIndex)
    } else {
        noQueryOrFragment
    }.trimEnd('/')
}

private fun looksLikeLocalDevelopmentHostWithoutPort(authority: String): Boolean {
    val host = authority
        .trim()
        .trimStart('[')
        .trimEnd(']')
        .substringBefore('/')
        .substringBefore('?')
        .substringBefore('#')

    if (host.equals("localhost", ignoreCase = true)) return true
    if (host == "10.0.2.2") return true
    if (host == "127.0.0.1") return true
    if (host.startsWith("192.168.")) return true
    if (host.startsWith("10.")) return true

    val parts = host.split('.')
    if (parts.size == 4 && parts.all { part -> part.toIntOrNull()?.let { it in 0..255 } == true }) {
        val first = parts[0].toIntOrNull() ?: return false
        val second = parts[1].toIntOrNull() ?: return false
        if (first == 172 && second in 16..31) return true
    }

    return false
}

private fun String.withDefaultAitaPortForLocalHostIfMissing(hadExplicitScheme: Boolean): String {
    if (hadExplicitScheme) return this

    val schemeSeparatorIndex = indexOf("://")
    val authorityStartIndex = if (schemeSeparatorIndex >= 0) schemeSeparatorIndex + 3 else 0
    val authorityEndIndex = listOf(
        indexOf('/', startIndex = authorityStartIndex),
        indexOf('?', startIndex = authorityStartIndex),
        indexOf('#', startIndex = authorityStartIndex)
    ).filter { it >= 0 }.minOrNull() ?: length
    val authority = substring(authorityStartIndex, authorityEndIndex)

    if (authority.isBlank()) return this
    if ('@' in authority) return this
    val hasExplicitPort = if (authority.startsWith("[")) {
        authority.substringAfter(']', missingDelimiterValue = "").startsWith(":")
    } else {
        authority.count { it == ':' } == 1
    }
    if (hasExplicitPort || !looksLikeLocalDevelopmentHostWithoutPort(authority)) return this

    return substring(0, authorityEndIndex) + ":8080" + substring(authorityEndIndex)
}

fun normalizedHttpServerUrlOrNull(raw: String?): String? {
    val trimmed = raw
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: return null

    val hadExplicitHttpScheme = trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)
    val hadExplicitWebSocketScheme = trimmed.startsWith("ws://", ignoreCase = true) || trimmed.startsWith("wss://", ignoreCase = true)
    val hadExplicitScheme = hadExplicitHttpScheme || hadExplicitWebSocketScheme

    val withHttpScheme = when {
        hadExplicitHttpScheme -> trimmed
        trimmed.startsWith("ws://", ignoreCase = true) -> "http://" + trimmed.substringAfter("://")
        trimmed.startsWith("wss://", ignoreCase = true) -> "https://" + trimmed.substringAfter("://")
        "://" in trimmed -> return null
        else -> "http://$trimmed"
    }
        .withoutKnownAitaEndpointPath()
        .withDefaultAitaPortForLocalHostIfMissing(hadExplicitScheme)

    return runCatching {
        Url(withHttpScheme)
        withHttpScheme
    }.getOrNull()
}

fun normalizedClientServerUrlOverride(raw: String?, currentRaw: String?): String? {
    val normalized = normalizedHttpServerUrlOrNull(raw) ?: return null
    val currentNormalized = normalizedHttpServerUrlOrNull(currentRaw)
    return normalized.takeIf { it != currentNormalized }
}

fun setClientServerUrlFromUserInput(raw: String, refreshNow: Boolean = true): Boolean {
    val normalized = normalizedHttpServerUrlOrNull(raw)
    if (normalized == null) {
        postInAppNotification(
            localizedStringResourceMessage(
                id = 1162,
                main = "Invalid server address",
                ru = "Неверный адрес сервера",
                kk = "Сервер мекенжайы қате"
            ),
            NotificationType.Negative,
            transient = true
        )
        return false
    }

    GlobalScope.launch(Dispatchers.ourIo) {
        val currentConfiguration = globalAppConfigurationState.payloadValue
        globalAppConfigurationState.emit(
            DataState.Success(
                currentConfiguration.copy(serverUrl = Pair(normalized, currentConfiguration.serverUrl.second)),
                localizedStringResourceMessage(
                    id = 1161,
                    main = "Server address saved",
                    ru = "Адрес сервера сохранён",
                    kk = "Сервер мекенжайы сақталды"
                )
            )
        )
        if (refreshNow) refreshCloudConnectionManually()
    }

    return true
}

@PublishedApi
internal fun currentServerUrlIsDefaultOrBlank(): Boolean {
    val current = normalizedHttpServerUrlOrNull(globalAppConfigurationState.payloadValue.serverUrl.first)
    val default = normalizedHttpServerUrlOrNull(DEFAULT_AITA_SERVER_URL)
    return current == null || current == default
}

@PublishedApi
internal fun chooseClientServerUrlPair(
    current: Pair<String, String>,
    incoming: Pair<String, String>
): Pair<String, String> {
    val currentNormalized = normalizedHttpServerUrlOrNull(current.first)
    val incomingNormalized = normalizedHttpServerUrlOrNull(incoming.first) ?: return current

    return when {
        currentNormalized == null -> Pair(incomingNormalized, incoming.second)
        currentNormalized == incomingNormalized -> current
        else -> Pair(currentNormalized, current.second)
    }
}

@PublishedApi
internal suspend fun resolvedServerUrlCandidates(explicitServerUrl: String? = null): List<String> {
    ensureCachedGlobalConfigurationPrimedForNetwork()

    val explicitNormalized = normalizedHttpServerUrlOrNull(explicitServerUrl)
    val currentConfiguredNormalized = normalizedHttpServerUrlOrNull(globalAppConfigurationState.payloadValue.serverUrl.first)
    val cachedConfiguredNormalized = runCatching {
        getJsonCache<GlobalAppConfigurationDataModel>(CACHE_GLOBAL_CONFIG)?.serverUrl?.first
    }.getOrNull()?.let { normalizedHttpServerUrlOrNull(it) }
    val defaultNormalized = normalizedHttpServerUrlOrNull(DEFAULT_AITA_SERVER_URL)

    val selected = explicitNormalized
        ?: currentConfiguredNormalized
        ?: cachedConfiguredNormalized
        ?: defaultNormalized
        ?: DEFAULT_AITA_SERVER_URL

    val normalizedCandidates = listOf(selected).distinct()
    currentNetworkRequestCandidateServerUrlsMemory = normalizedCandidates
    logNetworkAttempt("server URL = ${normalizedCandidates.firstOrNull().orEmpty()}")
    return normalizedCandidates
}

@PublishedApi
internal suspend fun rememberReachableServerUrl(serverUrl: String) {
    val normalized = normalizedHttpServerUrlOrNull(serverUrl) ?: return
    currentNetworkRequestCandidateServerUrlsMemory = listOf(normalized)

    val currentConfiguration = globalAppConfigurationState.payloadValue
    val currentNormalized = normalizedHttpServerUrlOrNull(currentConfiguration.serverUrl.first)
    if (currentNormalized == null) {
        globalAppConfigurationState.emit(
            DataState.Success(
                currentConfiguration.copy(serverUrl = Pair(normalized, currentConfiguration.serverUrl.second)),
                cacheMessage()
            )
        )
    }
}

@Suppress("UNUSED_PARAMETER")
@PublishedApi
internal suspend fun forgetReachableServerUrlCandidate(serverUrl: String) {
    // No alternate/fallback server URLs are kept anymore. A failed configured address should stay visible
    // to the user instead of being silently replaced by another local address.
}

@PublishedApi
internal fun rawBodyLooksLikeJson(rawBody: String): Boolean {
    val trimmed = rawBody.trim()
    if (trimmed.isBlank()) return false

    return trimmed.startsWith("{") ||
            trimmed.startsWith("[") ||
            trimmed.startsWith("\"") ||
            trimmed == "null" ||
            trimmed == "true" ||
            trimmed == "false" ||
            trimmed.firstOrNull()?.isDigit() == true ||
            trimmed.firstOrNull() == '-'
}

@PublishedApi
internal fun rawBodyLooksLikeAitaServerResponse(rawBody: String): Boolean {
    val trimmed = rawBody.trim()
    if (trimmed.isBlank()) return false

    if (trimmed.startsWith("{") &&
        trimmed.contains("\"message\"") &&
        trimmed.contains("\"negative\"") &&
        trimmed.contains("\"payload\"")
    ) return true

    if (trimmed.startsWith("{") &&
        trimmed.contains("\"serverUrl\"") &&
        trimmed.contains("\"globalAppConfigurationPath\"")
    ) return true

    return runCatching {
        jsonBase.decodeFromString<GenericResponseDataModel>(trimmed)
        true
    }.getOrDefault(false)
}

@PublishedApi
internal fun HttpResponse.isAitaServerResponse(rawBody: String): Boolean =
    headers[AITA_SERVER_HEADER]?.equals(AITA_SERVER_HEADER_VALUE, ignoreCase = true) == true ||
            rawBodyLooksLikeAitaServerResponse(rawBody)

@PublishedApi
internal fun nonAitaHttpResponseMessage(
    status: HttpStatusCode,
    rawBody: String,
    serverUrl: String
): List<LocalizedStringDataModel> {
    val looksLikeAnotherPage = status.value in 300..399 || rawBody.trimStart().startsWith("<")
    return if (looksLikeAnotherPage) {
        listOf(
            LocalizedStringDataModel("main", "Can’t reach AITA server. Check Wi‑Fi or server address."),
            LocalizedStringDataModel("en", "Can’t reach AITA server. Check Wi‑Fi or server address."),
            LocalizedStringDataModel("ru", "Сервер AITA недоступен. Проверьте Wi‑Fi или адрес сервера."),
            LocalizedStringDataModel("kk", "AITA сервері қолжетімсіз. Wi‑Fi немесе сервер мекенжайын тексеріңіз.")
        )
    } else {
        localizedStringResourceMessage(
            id = 1140,
            main = "Can’t reach AITA server. Check Wi‑Fi or server address.",
            ru = "Сервер AITA недоступен. Проверьте Wi‑Fi или адрес сервера.",
            kk = "AITA сервері қолжетімсіз. Wi‑Fi немесе сервер мекенжайын тексеріңіз."
        )
    }
}

@PublishedApi
internal fun <Response> nonAitaHttpResponseDataModel(
    status: HttpStatusCode,
    rawBody: String,
    serverUrl: String
): ResponseDataModel<Response> = ResponseDataModel(
    message = nonAitaHttpResponseMessage(status, rawBody, serverUrl),
    payload = null,
    negative = true,
    httpStatusCode = status.value,
    transportFailure = true
)

@PublishedApi
internal fun networkTargetUrl(serverUrl: String, endpointUrl: String): String =
    "${serverUrl.trimEnd('/')}/${endpointUrl.trimStart('/')}"

@PublishedApi
internal fun networkFailureSummary(throwable: Throwable?): String = throwable
    ?.toString()
    ?.replace(Regex("\\s+"), " ")
    ?.take(220)
    .orEmpty()

@PublishedApi
internal fun networkTransportFailureMessage(
    serverUrl: String,
    endpointUrl: String,
    throwable: Throwable? = null
): List<LocalizedStringDataModel> = localizedStringResourceMessage(
    id = 1140,
    main = "Can’t reach AITA server. Check Wi‑Fi or server address.",
    ru = "Сервер AITA недоступен. Проверьте Wi‑Fi или адрес сервера.",
    kk = "AITA сервері қолжетімсіз. Wi‑Fi немесе сервер мекенжайын тексеріңіз."
)

@PublishedApi
internal fun HttpStatusCode.isAitaServerUnhealthyForClientBanner(): Boolean = value >= 500

@PublishedApi
internal fun <Response> ResponseDataModel<Response>.withAitaTransportFailureFromStatus(status: HttpStatusCode): ResponseDataModel<Response> =
    if (status.isAitaServerUnhealthyForClientBanner()) copy(transportFailure = true) else this

@PublishedApi
internal fun cloudEndpointIsPublicReachabilityOnly(endpointUrl: String): Boolean {
    val endpoint = endpointUrl.trim('/').lowercase()
    return endpoint.startsWith("config/") || endpoint.startsWith("res/")
}

@PublishedApi
internal fun cloudResponseCanMarkReachable(
    endpointUrl: String,
    status: HttpStatusCode,
    hasStoredTokens: Boolean = getStoredUserAuthTokens?.invoke() != null
): Boolean {
    if (status.isAitaServerUnhealthyForClientBanner()) return false

    // Static resource/config endpoints can succeed while the authenticated/data side of the server is
    // crashing with 500s. In a logged-in app they must never be allowed to repaint the top banner green;
    // only the readiness probe, WebSocket, or real authenticated/data endpoints may clear an outage.
    if (hasStoredTokens && cloudEndpointIsPublicReachabilityOnly(endpointUrl)) return false

    return true
}

private const val AITA_NETWORK_VERBOSE_LOGS = true

@PublishedApi
internal fun logNetworkAttempt(message: String) {
    if (AITA_NETWORK_VERBOSE_LOGS || message.startsWith("FAILED", ignoreCase = true)) {
        println("AITA network: $message")
    }
}

@PublishedApi
internal fun rememberAuthRefreshNonAuthFailure(
    message: List<LocalizedStringDataModel>?,
    transportFailure: Boolean = false
) {
    lastAuthRefreshNonAuthFailureAtMillis = getCurrentTimeMillis()
    lastAuthRefreshNonAuthFailureMessage = message
    lastAuthRefreshNonAuthFailureWasTransportFailure = transportFailure
    logCloudConnectionDiagnostic(
        "auth refresh non-auth failure remembered transportFailure=$transportFailure " +
                "status=${cloudTransportStatusName(cloudTransportStatusState.value)}"
    )
}

@PublishedApi
internal fun clearAuthRefreshNonAuthFailure() {
    lastAuthRefreshNonAuthFailureAtMillis = 0L
    lastAuthRefreshNonAuthFailureMessage = null
    lastAuthRefreshNonAuthFailureWasTransportFailure = false
}

@PublishedApi
internal fun recentAuthRefreshNonAuthFailureMessage(): List<LocalizedStringDataModel>? {
    val failureAt = lastAuthRefreshNonAuthFailureAtMillis
    if (failureAt <= 0L) return null
    if (getCurrentTimeMillis() - failureAt > AUTH_REFRESH_NON_AUTH_FAILURE_GRACE_MILLIS) return null

    lastAuthRefreshNonAuthFailureMessage?.let { return it }

    return if (lastAuthRefreshNonAuthFailureWasTransportFailure) {
        localizedStringResourceMessage(
            id = 1140,
            main = "Can’t reach AITA server. Check Wi‑Fi or server address.",
            ru = "Сервер AITA недоступен. Проверьте Wi‑Fi или адрес сервера.",
            kk = "AITA сервері қолжетімсіз. Wi‑Fi немесе сервер мекенжайын тексеріңіз."
        )
    } else {
        localizedStringResourceMessage(
            id = 1149,
            main = "Server could not refresh session. Keeping local login active.",
            ru = "Сервер не смог обновить сеанс. Локальный вход сохранён.",
            kk = "Сервер сеансты жаңарта алмады. Жергілікті кіру сақталды."
        )
    }
}

@PublishedApi
internal fun recentAuthRefreshNonAuthFailureWasTransport(): Boolean {
    val failureAt = lastAuthRefreshNonAuthFailureAtMillis
    if (failureAt <= 0L) return false
    if (getCurrentTimeMillis() - failureAt > AUTH_REFRESH_NON_AUTH_FAILURE_GRACE_MILLIS) return false

    return lastAuthRefreshNonAuthFailureWasTransportFailure || recentCloudTransportFailureIsDominant()
}

@PublishedApi
internal fun <Response> authRefreshFailureResponseForNetworkRequest(
    message: List<LocalizedStringDataModel>
): ResponseDataModel<Response> {
    val transportFailure = recentAuthRefreshNonAuthFailureWasTransport()
    if (transportFailure) {
        markCloudTransportUnavailableForNotifications()
    } else {
        markCloudTransportReachableForNotifications(authenticated = false, authRefreshRequired = false)
    }

    return ResponseDataModel(
        message = message,
        payload = null,
        negative = true,
        httpStatusCode = HttpStatusCode.ServiceUnavailable.value,
        transportFailure = transportFailure
    )
}

@Suppress("UNUSED_PARAMETER")
@PublishedApi
internal fun shouldRetryNetworkRequestOnNextServerUrl(
    method: HttpMethod,
    endpointUrl: String,
    status: HttpStatusCode,
    rawBody: String,
    aitaServerResponse: Boolean = false
): Boolean = false

@PublishedApi
internal suspend fun refreshAuthTokensWithServerFallback(refreshToken: String): ResponseDataModel<TokenPair> {
    ensureCachedGlobalConfigurationPrimedForNetwork()

    val refreshEndpoint = globalAppConfigurationState.payloadValue.refreshPath.first.trimStart('/')
    val serverUrlCandidates = resolvedServerUrlCandidates(null)
    var lastServerErrorResponse: ResponseDataModel<TokenPair>? = null
    var lastTransportFailureMessage: List<LocalizedStringDataModel>? = null

    val refreshHttpClient = HttpClient(getHttpClientEngine()) {
        install(ContentNegotiation) {
            json(jsonBase)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 18_000L
            connectTimeoutMillis = 4_000L
            socketTimeoutMillis = 18_000L
        }
        expectSuccess = false
    }

    return try {
        for ((index, resolvedServerUrl) in serverUrlCandidates.withIndex()) {
            try {
                val requestUrl = networkTargetUrl(resolvedServerUrl, refreshEndpoint)
                logNetworkAttempt("TRY ${HttpMethod.Post.value} $requestUrl")
                val httpResponse = refreshHttpClient.request(requestUrl) {
                    method = HttpMethod.Post
                    contentType(ContentType.Application.Json)
                    header(HttpHeaders.CacheControl, "no-cache")
                    header(HttpHeaders.Pragma, "no-cache")
                    currentClientDeviceInfoHeaders().forEach { (key, value) ->
                        safeHttpHeaderValueOrNull(value)?.let { safeValue -> header(key, safeValue) }
                    }
                    setBody(refreshToken)
                }

                val rawBody = httpResponse.bodyAsText()
                val aitaServerResponse = httpResponse.isAitaServerResponse(rawBody)
                logNetworkAttempt("RESULT ${HttpMethod.Post.value} $requestUrl HTTP ${httpResponse.status.value} aita=$aitaServerResponse")
                val shouldRetryCandidate = index < serverUrlCandidates.lastIndex &&
                        shouldRetryNetworkRequestOnNextServerUrl(
                            method = HttpMethod.Post,
                            endpointUrl = refreshEndpoint,
                            status = httpResponse.status,
                            rawBody = rawBody,
                            aitaServerResponse = aitaServerResponse
                        )

                if (!aitaServerResponse) {
                    val nonAitaResponse = nonAitaHttpResponseDataModel<TokenPair>(httpResponse.status, rawBody, resolvedServerUrl)
                    lastTransportFailureMessage = nonAitaResponse.message
                    forgetReachableServerUrlCandidate(resolvedServerUrl)
                    markCloudTransportUnavailableForNotifications()
                    if (shouldRetryCandidate) continue
                    rememberAuthRefreshNonAuthFailure(nonAitaResponse.message, transportFailure = true)
                    return nonAitaResponse
                }

                val decodedRefreshResponse = decodeNetworkResponseDataModel<TokenPair>(rawBody, httpResponse.status)
                    .withAitaTransportFailureFromStatus(httpResponse.status)

                if (httpResponse.status.isAitaServerUnhealthyForClientBanner() || decodedRefreshResponse.transportFailure) {
                    val failureResponse = decodedRefreshResponse.copy(transportFailure = true)
                    lastServerErrorResponse = failureResponse
                    markCloudTransportUnavailableForNotifications()
                    rememberAuthRefreshNonAuthFailure(failureResponse.message, transportFailure = true)
                    logCloudConnectionDiagnostic(
                        "auth refresh response treated as unavailable http=${httpResponse.status.value} " +
                                "aita=$aitaServerResponse negative=${failureResponse.negative}"
                    )
                    if (shouldRetryCandidate) continue
                    return failureResponse
                }

                if (shouldRetryCandidate) {
                    lastServerErrorResponse = decodedRefreshResponse
                    continue
                }

                val canMarkReachable = cloudResponseCanMarkReachable(refreshEndpoint, httpResponse.status)
                if (canMarkReachable) {
                    rememberReachableServerUrl(resolvedServerUrl)
                    markCloudTransportReachableForNotifications(
                        authenticated = httpResponse.status.value in 200..299,
                        authRefreshRequired = httpResponse.status == HttpStatusCode.Unauthorized
                    )
                } else {
                    logCloudConnectionDiagnostic(
                        "auth refresh reachable mark suppressed http=${httpResponse.status.value} " +
                                "status=${cloudTransportStatusName(cloudTransportStatusState.value)}"
                    )
                    if (httpResponse.status.value >= 500) {
                        markCloudTransportUnavailableForNotifications()
                    }
                }

                if (httpResponse.status == HttpStatusCode.Unauthorized) {
                    return ResponseDataModel(
                        message = localizedStringResourceMessage(
                            id = 91,
                            main = "Cloud session needs refresh. You remain signed in locally.",
                            ru = "Облачный сеанс нужно обновить. Вы остаётесь в аккаунте локально.",
                            kk = "Бұлттық сеансты жаңарту қажет. Сіз жергілікті түрде аккаунтта қаласыз."
                        ),
                        payload = null,
                        negative = true,
                        httpStatusCode = httpResponse.status.value,
                        transportFailure = false
                    )
                }

                return decodedRefreshResponse
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                markCloudTransportUnavailableForNotifications()
                lastTransportFailureMessage = networkTransportFailureMessage(resolvedServerUrl, refreshEndpoint, throwable)
                logNetworkAttempt("FAILED ${networkTargetUrl(resolvedServerUrl, refreshEndpoint)} ${networkFailureSummary(throwable)}")
            }
        }

        lastServerErrorResponse ?: ResponseDataModel(
            message = lastTransportFailureMessage ?: localizedStringResourceMessage(
                id = 214,
                main = "Cannot reach server. Keeping you signed in offline.",
                ru = "Сервер недоступен. Вы остаётесь в аккаунте офлайн.",
                kk = "Сервер қолжетімсіз. Сіз офлайн режимде аккаунтта қаласыз."
            ),
            payload = null,
            negative = true,
            transportFailure = true
        )
    } finally {
        refreshHttpClient.close()
    }
}

@PublishedApi
internal suspend fun refreshStoredAuthTokensOnceForNetworkRetry(postNotification: Boolean = true): Boolean = tokenRefreshMutex.withLock {
    val current = getStoredUserAuthTokens?.invoke() ?: return@withLock false
    val refreshResponse = refreshAuthTokensWithServerFallback(current.refreshToken)

    refreshResponse.payload?.let { refreshedTokens ->
        clearAuthRefreshNonAuthFailure()
        setStoredUserAuthTokens?.invoke(refreshedTokens)
        httpClient.authProvider<BearerAuthProvider>()?.clearToken()
        markCloudTransportReachableForNotifications(authenticated = true, authRefreshRequired = false)
        return@withLock true
    }

    when {
        refreshResponse.transportFailure -> {
            rememberAuthRefreshNonAuthFailure(refreshResponse.message, transportFailure = true)
            markCloudTransportUnavailableForNotifications()
        }

        refreshResponse.httpStatusCode == HttpStatusCode.Unauthorized.value -> {
            clearAuthRefreshNonAuthFailure()
            markCloudSessionNeedsRefreshForNotifications()
            if (postNotification) {
                postInAppNotification(
                    refreshResponse.message ?: localizedStringResourceMessage(
                        id = 91,
                        main = "Cloud session needs refresh. You remain signed in locally.",
                        ru = "Облачный сеанс нужно обновить. Вы остаётесь в аккаунте локально.",
                        kk = "Бұлттық сеансты жаңарту қажет. Сіз жергілікті түрде аккаунтта қаласыз."
                    ),
                    NotificationType.Neutral
                )
            }
        }

        else -> {
            // The server answered, but the refresh endpoint failed for a non-auth reason (for example 500).
            // Do not convert that into an auth/logout problem.
            rememberAuthRefreshNonAuthFailure(refreshResponse.message)
            markCloudTransportReachableForNotifications(authenticated = false, authRefreshRequired = false)
        }
    }

    false
}


private const val LOCAL_NETWORK_DEFAULT_TCP_PORT = 45720
private const val LOCAL_NETWORK_DEFAULT_DISCOVERY_PORT = 45721
private const val LOCAL_NETWORK_STALE_DEVICE_MILLIS = 45_000L

private fun storeScopedCacheKey(name: String, storeId: String): String = "$name:$storeId"

private suspend inline fun <reified T> putJsonCache(key: String, value: T) {
    try {
        putLocalKv(CACHE_PREFIX + key, jsonBase.encodeToString(value))
    } catch (_: Throwable) {
    }
}

private suspend inline fun <reified T> getJsonCache(key: String): T? {
    return try {
        getLocalKv(CACHE_PREFIX + key)?.let { jsonBase.decodeFromString<T>(it) }
    } catch (_: Throwable) {
        null
    }
}

private const val MAX_PENDING_SESSION_CLEANUPS = 25

private suspend fun loadPendingSessionCleanups(): List<PendingSessionCleanupDataModel> =
    getJsonCache<List<PendingSessionCleanupDataModel>>(CACHE_PENDING_SESSION_CLEANUPS)
        .orEmpty()
        .filter { it.refreshToken.isNotBlank() }
        .distinctBy { it.refreshToken }
        .takeLast(MAX_PENDING_SESSION_CLEANUPS)

private suspend fun persistPendingSessionCleanups(items: List<PendingSessionCleanupDataModel>) {
    val cleaned = items
        .filter { it.refreshToken.isNotBlank() }
        .distinctBy { it.refreshToken }
        .sortedBy { it.queuedAtMillis }
        .takeLast(MAX_PENDING_SESSION_CLEANUPS)

    if (cleaned.isEmpty()) {
        putLocalKv(CACHE_PREFIX + CACHE_PENDING_SESSION_CLEANUPS, null)
    } else {
        putJsonCache(CACHE_PENDING_SESSION_CLEANUPS, cleaned)
    }
}

private suspend fun enqueuePendingSessionCleanup(
    refreshToken: String,
    reason: String = "local_logout",
    workshiftEnd: WorkshiftEndRequestDataModel? = null,
    workshiftStoreId: String? = null
) {
    if (refreshToken.isBlank()) return
    syncPendingSessionCleanupsMutex.withLock {
        val existing = loadPendingSessionCleanups().filterNot { it.refreshToken == refreshToken }
        persistPendingSessionCleanups(
            existing + PendingSessionCleanupDataModel(
                refreshToken = refreshToken,
                queuedAtMillis = getCurrentTimeMillis(),
                reason = reason,
                deviceInfo = buildCurrentClientDeviceInfo(),
                workshiftEnd = workshiftEnd,
                workshiftStoreId = workshiftStoreId
            )
        )
    }
}

private suspend fun dropPendingSessionCleanup(refreshToken: String) {
    if (refreshToken.isBlank()) return
    syncPendingSessionCleanupsMutex.withLock {
        persistPendingSessionCleanups(loadPendingSessionCleanups().filterNot { it.refreshToken == refreshToken })
    }
}

private const val MAX_PENDING_WORKSHIFT_ENDS = 40

private fun pendingWorkshiftEndMessage(): List<LocalizedStringDataModel> = localizedStringResourceMessage(
    id = 1150,
    main = "Workshift ended locally; server sync is queued",
    ru = "Смена завершена локально; синхронизация с сервером поставлена в очередь",
    kk = "Ауысым жергілікті аяқталды; сервермен синхрондау кезекке қойылды"
)

private suspend fun loadPendingWorkshiftEnds(): List<PendingWorkshiftEndDataModel> =
    getJsonCache<List<PendingWorkshiftEndDataModel>>(CACHE_PENDING_WORKSHIFT_ENDS)
        .orEmpty()
        .filter { it.workshiftId.isNotBlank() && it.storeId.isNotBlank() && it.clientOperationId.isNotBlank() }
        .distinctBy { it.clientOperationId }
        .sortedBy { it.queuedAtMillis }
        .takeLast(MAX_PENDING_WORKSHIFT_ENDS)

private suspend fun persistPendingWorkshiftEnds(items: List<PendingWorkshiftEndDataModel>) {
    val cleaned = items
        .filter { it.workshiftId.isNotBlank() && it.storeId.isNotBlank() && it.clientOperationId.isNotBlank() }
        .distinctBy { it.clientOperationId }
        .sortedBy { it.queuedAtMillis }
        .takeLast(MAX_PENDING_WORKSHIFT_ENDS)

    if (cleaned.isEmpty()) {
        putLocalKv(CACHE_PREFIX + CACHE_PENDING_WORKSHIFT_ENDS, null)
    } else {
        putJsonCache(CACHE_PENDING_WORKSHIFT_ENDS, cleaned)
    }
}

private suspend fun enqueuePendingWorkshiftEnd(
    workshift: WorkshiftDataModel,
    endedAtMillis: Long,
    tokenPair: TokenPair? = getStoredUserAuthTokens?.invoke(),
    clientOperationId: String? = null
): PendingWorkshiftEndDataModel? {
    if (workshift.id.isBlank() || workshift.storeId.isBlank()) return null

    val operationId = clientOperationId?.takeIf { it.isNotBlank() } ?: createClientOperationId(
        prefix = "wse",
        seed = listOf(workshift.id, workshift.storeId, endedAtMillis.toString()).joinToString(":")
    )

    val pending = PendingWorkshiftEndDataModel(
        clientOperationId = operationId,
        workshiftId = workshift.id,
        storeId = workshift.storeId,
        endedAtMillis = endedAtMillis,
        queuedAtMillis = getCurrentTimeMillis(),
        accessToken = tokenPair?.accessToken.orEmpty(),
        refreshToken = tokenPair?.refreshToken.orEmpty(),
        deviceInfo = buildCurrentClientDeviceInfo()
    )

    syncPendingWorkshiftEndsMutex.withLock {
        val existing = loadPendingWorkshiftEnds()
            .filterNot { it.clientOperationId == operationId || it.workshiftId == workshift.id }
        persistPendingWorkshiftEnds(existing + pending)
    }

    return pending
}

private suspend fun dropPendingWorkshiftEnd(clientOperationId: String) {
    if (clientOperationId.isBlank()) return
    syncPendingWorkshiftEndsMutex.withLock {
        persistPendingWorkshiftEnds(loadPendingWorkshiftEnds().filterNot { it.clientOperationId == clientOperationId })
    }
}

private suspend fun markPendingWorkshiftEndAttempt(
    pending: PendingWorkshiftEndDataModel,
    lastError: String?,
    accessToken: String = pending.accessToken,
    refreshToken: String = pending.refreshToken
) {
    syncPendingWorkshiftEndsMutex.withLock {
        persistPendingWorkshiftEnds(
            loadPendingWorkshiftEnds().map { item ->
                if (item.clientOperationId == pending.clientOperationId) {
                    item.copy(
                        attemptCount = item.attemptCount + 1,
                        lastError = lastError,
                        accessToken = accessToken,
                        refreshToken = refreshToken
                    )
                } else item
            }
        )
    }
}

private suspend fun postPendingWorkshiftEndWithAccessToken(
    pending: PendingWorkshiftEndDataModel,
    accessToken: String
): ResponseDataModel<WorkshiftDataModel> {
    ensureCachedGlobalConfigurationPrimedForNetwork()

    val endpoint = globalAppConfigurationState.payloadValue.endWorkshiftPath.first.trimStart('/')
    val candidates = resolvedServerUrlCandidates(null)
    var lastServerErrorResponse: ResponseDataModel<WorkshiftDataModel>? = null
    var lastTransportFailureMessage: List<LocalizedStringDataModel>? = null

    val queuedHttpClient = HttpClient(getHttpClientEngine()) {
        install(ContentNegotiation) {
            json(jsonBase)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 18_000L
            connectTimeoutMillis = 4_000L
            socketTimeoutMillis = 18_000L
        }
        expectSuccess = false
    }

    return try {
        for ((index, resolvedServerUrl) in candidates.withIndex()) {
            try {
                val httpResponse = queuedHttpClient.request("${resolvedServerUrl.trimEnd('/')}/$endpoint") {
                    method = HttpMethod.Post
                    contentType(ContentType.Application.Json)
                    header(HttpHeaders.Authorization, "Bearer $accessToken")
                    header("store_id", pending.storeId)
                    header("X-AITA-Client-Operation-Id", pending.clientOperationId)
                    currentClientDeviceInfoHeaders(pending.deviceInfo).forEach { (key, value) ->
                        safeHttpHeaderValueOrNull(value)?.let { safeValue -> header(key, safeValue) }
                    }
                    setBody(
                        WorkshiftEndRequestDataModel(
                            workshiftId = pending.workshiftId,
                            endedAtMillis = pending.endedAtMillis,
                            clientOperationId = pending.clientOperationId,
                            deviceInfo = pending.deviceInfo
                        )
                    )
                }

                val rawBody = httpResponse.bodyAsText()
                val aitaServerResponse = httpResponse.isAitaServerResponse(rawBody)
                val shouldRetryCandidate = index < candidates.lastIndex &&
                        shouldRetryNetworkRequestOnNextServerUrl(
                            method = HttpMethod.Post,
                            endpointUrl = endpoint,
                            status = httpResponse.status,
                            rawBody = rawBody,
                            aitaServerResponse = aitaServerResponse
                        )

                if (!aitaServerResponse) {
                    val nonAitaResponse = nonAitaHttpResponseDataModel<WorkshiftDataModel>(httpResponse.status, rawBody, resolvedServerUrl)
                    lastTransportFailureMessage = nonAitaResponse.message
                    markCloudTransportUnavailableForNotifications()
                    if (shouldRetryCandidate) continue
                    return nonAitaResponse
                }

                val decodedResponse = decodeNetworkResponseDataModel<WorkshiftDataModel>(rawBody, httpResponse.status)
                    .withAitaTransportFailureFromStatus(httpResponse.status)

                if (httpResponse.status.isAitaServerUnhealthyForClientBanner() || decodedResponse.transportFailure) {
                    val failureResponse = decodedResponse.copy(transportFailure = true)
                    lastServerErrorResponse = failureResponse
                    markCloudTransportUnavailableForNotifications()
                    logCloudConnectionDiagnostic(
                        "pending workshift end response treated as unavailable http=${httpResponse.status.value} " +
                                "negative=${failureResponse.negative}"
                    )
                    if (shouldRetryCandidate) continue
                    return failureResponse
                }

                if (shouldRetryCandidate) {
                    lastServerErrorResponse = decodedResponse
                    continue
                }

                if (cloudResponseCanMarkReachable(endpoint, httpResponse.status)) {
                    rememberReachableServerUrl(resolvedServerUrl)
                    markCloudTransportReachableForNotifications(
                        authenticated = getStoredUserAuthTokens?.invoke() != null,
                        authRefreshRequired = false
                    )
                }
                return decodedResponse
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                markCloudTransportUnavailableForNotifications()
            }
        }

        lastServerErrorResponse ?: ResponseDataModel(
            message = lastTransportFailureMessage ?: localizedStringResourceMessage(
                id = 214,
                main = "Cannot reach server. Keeping you signed in offline.",
                ru = "Сервер недоступен. Вы остаётесь в аккаунте офлайн.",
                kk = "Сервер қолжетімсіз. Сіз офлайн режимде аккаунтта қаласыз."
            ),
            payload = null,
            negative = true,
            transportFailure = true
        )
    } finally {
        queuedHttpClient.close()
    }
}

suspend fun syncPendingWorkshiftEndsToServerNow(): Int {
    if (syncPendingWorkshiftEndsRunMutex.isLocked) return 0

    return syncPendingWorkshiftEndsRunMutex.withLock {
        var syncedCount = 0

        while (true) {
        val pending = syncPendingWorkshiftEndsMutex.withLock {
            loadPendingWorkshiftEnds().firstOrNull()
        } ?: break

        var accessToken = pending.accessToken.ifBlank { getStoredUserAuthTokens?.invoke()?.accessToken.orEmpty() }
        var refreshToken = pending.refreshToken.ifBlank { getStoredUserAuthTokens?.invoke()?.refreshToken.orEmpty() }

        if (accessToken.isBlank() && refreshToken.isNotBlank()) {
            val refreshResponse = refreshAuthTokensWithServerFallback(refreshToken)
            val refreshed = refreshResponse.payload
            if (refreshed != null) {
                accessToken = refreshed.accessToken
                refreshToken = refreshed.refreshToken
                markPendingWorkshiftEndAttempt(pending, null, accessToken, refreshToken)
            } else if (refreshResponse.transportFailure) {
                break
            } else {
                markPendingWorkshiftEndAttempt(
                    pending,
                    refreshResponse.message?.extractLocalizedString(appLanguageState.value)
                )
                break
            }
        }

        if (accessToken.isNotBlank() &&
            refreshToken.isNotBlank() &&
            accessJwtExpiresBeforeOrAt(accessToken, getCurrentTimeMillis() + REALTIME_ACCESS_TOKEN_REFRESH_SKEW_MILLIS)
        ) {
            val refreshResponse = refreshAuthTokensWithServerFallback(refreshToken)
            val refreshed = refreshResponse.payload
            if (refreshed != null) {
                accessToken = refreshed.accessToken
                refreshToken = refreshed.refreshToken
                markPendingWorkshiftEndAttempt(pending, null, accessToken, refreshToken)
            } else if (refreshResponse.transportFailure) {
                break
            } else {
                markPendingWorkshiftEndAttempt(
                    pending,
                    refreshResponse.message?.extractLocalizedString(appLanguageState.value)
                )
                break
            }
        }

        if (accessToken.isBlank()) {
            markPendingWorkshiftEndAttempt(
                pending,
                localizedStringResourceText(
                    id = 91,
                    main = "Cloud session needs refresh. You remain signed in locally.",
                    ru = "Облачный сеанс нужно обновить. Вы остаётесь в аккаунте локально.",
                    kk = "Бұлттық сеансты жаңарту қажет. Сіз жергілікті түрде аккаунтта қаласыз."
                )
            )
            break
        }

        var response = postPendingWorkshiftEndWithAccessToken(pending, accessToken)

        if (response.httpStatusCode == HttpStatusCode.Unauthorized.value && refreshToken.isNotBlank()) {
            val refreshResponse = refreshAuthTokensWithServerFallback(refreshToken)
            val refreshed = refreshResponse.payload
            if (refreshed != null) {
                accessToken = refreshed.accessToken
                refreshToken = refreshed.refreshToken
                markPendingWorkshiftEndAttempt(pending, null, accessToken, refreshToken)
                response = postPendingWorkshiftEndWithAccessToken(
                    pending.copy(accessToken = accessToken, refreshToken = refreshToken),
                    accessToken
                )
            } else if (refreshResponse.transportFailure) {
                break
            } else {
                markPendingWorkshiftEndAttempt(
                    pending,
                    refreshResponse.message?.extractLocalizedString(appLanguageState.value)
                )
                break
            }
        }

        if (response.transportFailure ||
            response.httpStatusCode == HttpStatusCode.Unauthorized.value ||
            response.httpStatusCode == HttpStatusCode.ServiceUnavailable.value ||
            (response.httpStatusCode ?: 0) >= 500
        ) {
            markPendingWorkshiftEndAttempt(
                pending,
                response.message?.extractLocalizedString(appLanguageState.value)
            )
            break
        }

        if (response.negative || response.payload == null) {
            markPendingWorkshiftEndAttempt(
                pending,
                response.message?.extractLocalizedString(appLanguageState.value)
            )
            break
        }

        dropPendingWorkshiftEnd(pending.clientOperationId)
        syncedCount += 1

        val active = activeWorkshiftState.payloadValue
        if (active?.id == pending.workshiftId) {
            activeWorkshiftState.emit(DataState.Empty(response.message))
        }
    }

        syncedCount
    }
}

fun syncPendingWorkshiftEndsToServer() {
    if (syncPendingWorkshiftEndsRunMutex.isLocked) return

    GlobalScope.launch(Dispatchers.ourIo) {
        syncPendingWorkshiftEndsToServerNow()
    }
}

private suspend fun endWorkshiftLocallyAndQueue(
    workshift: WorkshiftDataModel,
    endedAtMillis: Long = getCurrentTimeMillis(),
    postNotification: Boolean = true,
    clientOperationId: String? = null
): WorkshiftDataModel {
    val ended = workshift.copy(
        endedAtMillis = endedAtMillis,
        endedByUserId = userAccountState.payloadValue?.id,
        isActive = false
    )

    enqueuePendingWorkshiftEnd(workshift, endedAtMillis, clientOperationId = clientOperationId)
    activeWorkshiftState.emit(DataState.Empty(pendingWorkshiftEndMessage()))

    if (postNotification) {
        postInAppNotification(pendingWorkshiftEndMessage(), NotificationType.Neutral)
    }

    return ended
}

suspend fun syncPendingSessionCleanupsToServerNow(): Int {
    syncPendingWorkshiftEndsToServerNow()

    if (syncPendingSessionCleanupsMutex.isLocked) return 0

    return syncPendingSessionCleanupsMutex.withLock {
        val queued = loadPendingSessionCleanups()
        if (queued.isEmpty()) return@withLock 0

        val remaining = queued.toMutableList()
        var cleanedCount = 0

        for (cleanup in queued.sortedBy { it.queuedAtMillis }) {
            val response = networkRequest<Unit, LogoutCleanupRequestDataModel>(
                method = HttpMethod.Delete,
                endpointUrl = globalAppConfigurationState.payloadValue.logOutPath.first,
                body = LogoutCleanupRequestDataModel(
                    refreshToken = cleanup.refreshToken,
                    queuedAtMillis = cleanup.queuedAtMillis,
                    reason = cleanup.reason,
                    deviceInfo = cleanup.deviceInfo,
                    workshiftEnd = cleanup.workshiftEnd,
                    workshiftStoreId = cleanup.workshiftStoreId
                ),
                headers = currentClientDeviceInfoHeaders(cleanup.deviceInfo)
            )

            if (response.negative) {
                if (response.transportFailure) break
                // Non-transport server failures are retried later; logout endpoint is public and idempotent.
                break
            }

            remaining.removeAll { it.refreshToken == cleanup.refreshToken }
            cleanup.workshiftEnd?.clientOperationId?.let { dropPendingWorkshiftEnd(it) }
            cleanedCount += 1
        }

        persistPendingSessionCleanups(remaining)
        cleanedCount
    }
}

fun syncPendingSessionCleanupsToServer() {
    if (syncPendingSessionCleanupsMutex.isLocked) return

    GlobalScope.launch(Dispatchers.ourIo) {
        syncPendingWorkshiftEndsToServerNow()
        syncPendingSessionCleanupsToServerNow()
    }
}

private suspend fun persistTransactionCartUiState() {
    putJsonCache(CACHE_CART_SALE_METHOD_IDS, cartSaleMethodIdsState.value)
    putJsonCache(CACHE_TRANSACTION_PAYMENT_DRAFTS, transactionPaymentDraftsState.value)
    putJsonCache(CACHE_TRANSACTION_SUPPLY_SUPPLIER_IDS, transactionSupplySupplierIdsState.value)
    putJsonCache(CACHE_TRANSACTION_RETURN_REASONS, cartReturnReasonsState.value)
    putJsonCache(CACHE_CART_CONDITION_CHECKS, cartConditionChecksState.value)
    putJsonCache(CACHE_TRANSACTION_CART_SCROLL_STATES, transactionCartScrollStatesState.value)
}

private suspend fun loadTransactionCartUiState() {
    getJsonCache<Map<String, String>>(CACHE_CART_SALE_METHOD_IDS)?.let { cached ->
        cartSaleMethodIdsState.emit(cached.filterValues { it == SALE_METHOD_WHOLESALE })
    }
    getJsonCache<Map<String, TransactionPaymentDraftDataModel>>(CACHE_TRANSACTION_PAYMENT_DRAFTS)?.let { cached ->
        transactionPaymentDraftsState.emit(cached)
    }
    getJsonCache<Map<String, String>>(CACHE_TRANSACTION_SUPPLY_SUPPLIER_IDS)?.let { cached ->
        transactionSupplySupplierIdsState.emit(cached.filterValues { it.isNotBlank() })
    }
    getJsonCache<Map<String, String>>(CACHE_TRANSACTION_RETURN_REASONS)?.let { cached ->
        cartReturnReasonsState.emit(cached.mapValues { it.value.take(MAX_RETURN_REASON_LENGTH) }.filterValues { it.trim().isNotBlank() })
    }
    getJsonCache<Map<String, Boolean>>(CACHE_CART_CONDITION_CHECKS)?.let { cached ->
        cartConditionChecksState.emit(cached)
    }
    getJsonCache<Map<String, TransactionCartScrollStateDataModel>>(CACHE_TRANSACTION_CART_SCROLL_STATES)?.let { cached ->
        transactionCartScrollStatesState.emit(cached)
    }
}

private fun localNetworkMessage(id: Long, main: String, ru: String, kk: String): List<LocalizedStringDataModel> =
    localizedStringResourceMessage(id = id, main = main, ru = ru, kk = kk)

private fun logLocalNetworkQueueDiagnostic(message: String) {
    println("AITA local queue: $message")
}

private fun localNetworkDeviceId(): String {
    val installation = getClientDeviceInfo?.invoke()?.installationId.orEmpty().ifBlank { getPlatformName() }
    val user = userAccountState.payloadValue?.id.orEmpty().ifBlank { "anonymous" }
    return "${getPlatformName()}_${user}_${installation}".replace(Regex("[^A-Za-z0-9_-]"), "_").take(96)
}

private fun createClientOperationId(prefix: String, seed: String = ""): String {
    val device = localNetworkDeviceId()
    val now = getCurrentTimeMillis()
    val hash = (device + seed + now.toString()).hashCode().toUInt().toString(16)
    return "$prefix-$device-$now-$hash".take(160)
}

private fun localBranchNetworkAllowedForActiveStore(): Boolean {
    val activeStore = storesState.payloadValue?.findStoreOrBranch(activeStoreIdState.value) ?: return false
    return activeStore.isBranchStore() || activeStore.branches.isEmpty()
}

private fun localBranchNetworkRestrictionMessage(): List<LocalizedStringDataModel> = localNetworkMessage(
    id = 751,
    main = "Select one physical branch first. Local branch network cannot run on a parent warehouse with several branches.",
    ru = "Сначала выберите один физический филиал. Локальная сеть филиала не работает на головном складе с несколькими филиалами.",
    kk = "Алдымен бір нақты филиалды таңдаңыз. Филиалдың жергілікті желісі бірнеше филиалы бар негізгі қоймада жұмыс істемейді."
)

private fun currentLocalNetworkDevice(roleOverride: String? = null): LocalNetworkDeviceDataModel {
    val user = userAccountState.payloadValue
    val state = localNetworkState.value
    val activeStore = storesState.payloadValue?.findStoreOrBranch(activeStoreIdState.value)
    return LocalNetworkDeviceDataModel(
        deviceId = state.deviceId.ifBlank { localNetworkDeviceId() },
        userId = user?.id.orEmpty(),
        userName = listOf(user?.firstName.orEmpty(), user?.lastName.orEmpty()).filter { it.isNotBlank() }.joinToString(" ").ifBlank { user?.phoneNumber.orEmpty().asDisplayPhoneNumber() },
        platform = getPlatformName(),
        host = LocalAitaLanTransport.localHostAddress(),
        port = state.tcpPort.takeIf { it > 0 } ?: LOCAL_NETWORK_DEFAULT_TCP_PORT,
        role = roleOverride ?: state.role,
        storeId = activeStore?.rootStoreId() ?: activeStoreIdState.value,
        branchStoreId = activeStoreIdState.value,
        queueSize = localNetworkQueuedOperationsState.value.count { it.status != LOCAL_NETWORK_QUEUE_SYNCED },
        online = true,
        lastSeenMillis = getCurrentTimeMillis()
    )
}

private suspend fun persistLocalNetworkState() {
    putJsonCache(CACHE_LOCAL_NETWORK_STATE, localNetworkState.value)
}

private suspend fun persistLocalNetworkQueue() {
    putJsonCache(CACHE_LOCAL_NETWORK_QUEUE, localNetworkQueuedOperationsState.value)
}

private suspend fun loadLocalNetworkCache() {
    val cachedState = getJsonCache<LocalNetworkStateDataModel>(CACHE_LOCAL_NETWORK_STATE)
    if (cachedState != null) {
        localNetworkState.emit(
            cachedState.copy(
                deviceId = cachedState.deviceId.ifBlank { localNetworkDeviceId() },
                tcpPort = cachedState.tcpPort.takeIf { it > 0 } ?: LOCAL_NETWORK_DEFAULT_TCP_PORT,
                discoveryPort = cachedState.discoveryPort.takeIf { it > 0 } ?: LOCAL_NETWORK_DEFAULT_DISCOVERY_PORT,
                activeStoreId = activeStoreIdState.value
            )
        )
    } else {
        localNetworkState.emit(
            LocalNetworkStateDataModel(
                enabled = false,
                role = LOCAL_NETWORK_ROLE_DISABLED,
                deviceId = localNetworkDeviceId(),
                tcpPort = LOCAL_NETWORK_DEFAULT_TCP_PORT,
                discoveryPort = LOCAL_NETWORK_DEFAULT_DISCOVERY_PORT,
                activeStoreId = activeStoreIdState.value
            )
        )
    }

    localNetworkQueuedOperationsState.emit(
        getJsonCache<List<LocalNetworkQueuedOperationDataModel>>(CACHE_LOCAL_NETWORK_QUEUE).orEmpty()
            .filter { it.id.isNotBlank() && it.bodyJson.isNotBlank() }
            .map { operation ->
                if (operation.status == LOCAL_NETWORK_QUEUE_SYNCING) {
                    operation.copy(status = LOCAL_NETWORK_QUEUE_PENDING, lastError = "Previous sync was interrupted; retry queued")
                } else operation
            }
            .distinctBy { it.id }
            .sortedWith(compareBy<LocalNetworkQueuedOperationDataModel> { it.createdAtMillis }.thenBy { it.id })
    )
}

private fun localNetworkSnapshot(): LocalNetworkSnapshotDataModel {
    val storeId = activeStoreIdState.value.orEmpty()
    return LocalNetworkSnapshotDataModel(
        storeId = storeId,
        stock = stockState.payloadValue.orEmpty(),
        stockBatches = stockBatchesState.payloadValue.orEmpty(),
        transactions = transactionsState.payloadValue.orEmpty(),
        debtors = debtorsState.payloadValue.orEmpty(),
        cashRegister = cashRegisterState.payloadValue,
        updatedAtMillis = getCurrentTimeMillis()
    )
}

private suspend fun applyLocalNetworkSnapshot(snapshot: LocalNetworkSnapshotDataModel) {
    val message = localNetworkMessage(734, "Local branch state updated", "Локальное состояние филиала обновлено", "Филиалдың жергілікті күйі жаңартылды")
    if (snapshot.stock.isNotEmpty()) stockState.emit(DataState.Success(snapshot.stock, message))
    if (snapshot.stockBatches.isNotEmpty()) stockBatchesState.emit(DataState.Success(snapshot.stockBatches, message))
    if (snapshot.transactions.isNotEmpty()) transactionsState.emit(DataState.Success(snapshot.transactions, message))
    if (snapshot.debtors.isNotEmpty()) debtorsState.emit(DataState.Success(snapshot.debtors, message))
    snapshot.cashRegister?.let {
        cashRegisterState.emit(DataState.Success(it, message))
        cashRegisterAmountState.emit(it.currentAmount)
    }
}

private fun updateLocalNetworkDevice(device: LocalNetworkDeviceDataModel) {
    if (device.deviceId.isBlank() || device.deviceId == localNetworkState.value.deviceId) return
    val now = getCurrentTimeMillis()
    val fresh = device.copy(lastSeenMillis = now, online = true)
    localNetworkDevicesState.value = (localNetworkDevicesState.value
        .filter { it.deviceId != fresh.deviceId }
        .plus(fresh))
        .filter { now - it.lastSeenMillis <= LOCAL_NETWORK_STALE_DEVICE_MILLIS }
        .sortedWith(compareByDescending<LocalNetworkDeviceDataModel> { it.role == LOCAL_NETWORK_ROLE_SERVER }.thenBy { it.userName }.thenBy { it.deviceId })
}

private fun localNetworkEnvelope(type: String, operation: LocalNetworkQueuedOperationDataModel? = null, snapshot: LocalNetworkSnapshotDataModel? = null, accepted: Boolean = true, error: String? = null): LocalNetworkEnvelopeDataModel =
    LocalNetworkEnvelopeDataModel(
        messageId = createClientOperationId("lan-msg", type + (operation?.id ?: "")),
        type = type,
        device = currentLocalNetworkDevice(),
        operation = operation,
        snapshot = snapshot,
        accepted = accepted,
        error = error,
        createdAtMillis = getCurrentTimeMillis()
    )

private suspend fun broadcastLocalNetworkSnapshot() {
    val state = localNetworkState.value
    if (!state.enabled || state.role != LOCAL_NETWORK_ROLE_SERVER) return
    val envelope = localNetworkEnvelope("state", snapshot = localNetworkSnapshot())
    val text = jsonBase.encodeToString(envelope)
    localNetworkDevicesState.value
        .filter { it.deviceId != state.deviceId && it.host.isNotBlank() }
        .forEach { device ->
            GlobalScope.launch(Dispatchers.ourIo) {
                runCatching { LocalAitaLanTransport.send(device.host, device.port, text, 1500) }
            }
        }
}

private fun nextLocalNetworkOperationTimestamp(): Long {
    val last = localNetworkQueuedOperationsState.value.maxOfOrNull { it.createdAtMillis } ?: 0L
    return maxOf(getCurrentTimeMillis(), last + 1L)
}

private suspend fun enqueueLocalNetworkOperation(operation: LocalNetworkQueuedOperationDataModel) {
    if (operation.id.isBlank() || operation.bodyJson.isBlank()) return
    val existing = localNetworkQueuedOperationsState.value.firstOrNull { it.id == operation.id }
    val nextOperation = existing?.let { old ->
        old.copy(
            operationType = operation.operationType,
            storeId = operation.storeId.ifBlank { old.storeId },
            branchStoreId = operation.branchStoreId ?: old.branchStoreId,
            endpointPath = operation.endpointPath.ifBlank { old.endpointPath },
            httpMethod = operation.httpMethod.ifBlank { old.httpMethod },
            bodyJson = operation.bodyJson,
            createdByUserId = operation.createdByUserId.ifBlank { old.createdByUserId },
            createdByDeviceId = operation.createdByDeviceId.ifBlank { old.createdByDeviceId },
            createdAtMillis = listOf(old.createdAtMillis, operation.createdAtMillis)
                .filter { it > 0L }
                .minOrNull() ?: nextLocalNetworkOperationTimestamp(),
            status = if (old.status == LOCAL_NETWORK_QUEUE_SYNCED) old.status else LOCAL_NETWORK_QUEUE_PENDING,
            lastError = null
        )
    } ?: operation.copy(
        createdAtMillis = operation.createdAtMillis.takeIf { it > 0L } ?: nextLocalNetworkOperationTimestamp(),
        status = LOCAL_NETWORK_QUEUE_PENDING
    )

    localNetworkQueuedOperationsState.emit(
        (localNetworkQueuedOperationsState.value.filterNot { it.id == nextOperation.id } + nextOperation)
            .distinctBy { it.id }
            .sortedWith(compareBy<LocalNetworkQueuedOperationDataModel> { it.createdAtMillis }.thenBy { it.id })
    )
    persistLocalNetworkQueue()
    logLocalNetworkQueueDiagnostic(
        "queued id=${nextOperation.id} type=${nextOperation.operationType} " +
                "createdAt=${nextOperation.createdAtMillis} size=${localNetworkQueuedOperationsState.value.count { it.status != LOCAL_NETWORK_QUEUE_SYNCED }}"
    )
}

private fun transactionLocalId(operationId: String): String = "local_${operationId.takeLast(48)}"

private fun TransactionDataModel.withClientOperationId(): TransactionDataModel =
    if (clientOperationId.isNotBlank()) this else copy(
        clientOperationId = createClientOperationId(
            "txn",
            listOf(
                storeId,
                type,
                timeMillis.toString(),
                paidCash.toString(),
                paidCard.toString(),
                goodsInTransaction.joinToString("|") { line ->
                    listOf(
                        line.goodsItemId.orEmpty(),
                        line.barcode,
                        line.quantity.toString(),
                        line.pricePerUnit.toString(),
                        line.saleMethodId,
                        line.supplierIdText.orEmpty(),
                        line.returnReason.trim()
                    ).joinToString(":")
                }
            ).joinToString(";")
        )
    )

private fun queuedTransactionOperation(transaction: TransactionDataModel): LocalNetworkQueuedOperationDataModel =
    LocalNetworkQueuedOperationDataModel(
        id = transaction.clientOperationId.ifBlank { createClientOperationId("txn") },
        operationType = LOCAL_NETWORK_OPERATION_TRANSACTION_COMPLETE,
        storeId = transaction.storeId,
        branchStoreId = activeStoreIdState.value,
        endpointPath = globalAppConfigurationState.payloadValue.completeTransactionPath.first,
        httpMethod = "POST",
        bodyJson = jsonBase.encodeToString(transaction),
        createdByUserId = userAccountState.payloadValue?.id.orEmpty(),
        createdByDeviceId = localNetworkState.value.deviceId.ifBlank { localNetworkDeviceId() },
        createdAtMillis = transaction.timeMillis.takeIf { it > 0L } ?: getCurrentTimeMillis(),
        status = LOCAL_NETWORK_QUEUE_PENDING
    )

private suspend fun queueTransactionForCloudSync(
    transaction: TransactionDataModel,
    broadcastSnapshot: Boolean = localNetworkState.value.isServer
): TransactionDataModel {
    val tx = transaction.withClientOperationId()
    val baseOperation = queuedTransactionOperation(tx)
    val operation = baseOperation.copy(
        id = tx.clientOperationId,
        bodyJson = jsonBase.encodeToString(tx.copy(clientOperationId = tx.clientOperationId)),
        createdAtMillis = baseOperation.createdAtMillis,
        status = LOCAL_NETWORK_QUEUE_PENDING,
        lastError = null
    )

    enqueueLocalNetworkOperation(operation)
    val completed = applyLocalQueuedTransaction(tx, operation.id)

    if (broadcastSnapshot) {
        broadcastLocalNetworkSnapshot()
    }

    return completed
}

private fun WorkshiftDataModel.localEndedCopy(endedAtMillis: Long): WorkshiftDataModel = copy(
    endedAtMillis = endedAtMillis,
    endedByUserId = userAccountState.payloadValue?.id,
    isActive = false
)

private fun queuedWorkshiftEndOperation(
    storeId: String,
    request: WorkshiftEndRequestDataModel,
    createdAtMillis: Long = request.endedAtMillis.takeIf { it > 0L } ?: getCurrentTimeMillis()
): LocalNetworkQueuedOperationDataModel =
    LocalNetworkQueuedOperationDataModel(
        id = request.clientOperationId.ifBlank {
            createClientOperationId("workshift-end", listOf(storeId, request.workshiftId, createdAtMillis.toString()).joinToString("|"))
        },
        operationType = LOCAL_NETWORK_OPERATION_WORKSHIFT_END,
        storeId = storeId,
        branchStoreId = storeId,
        endpointPath = globalAppConfigurationState.payloadValue.endWorkshiftPath.first,
        httpMethod = "POST",
        bodyJson = jsonBase.encodeToString(request),
        createdByUserId = userAccountState.payloadValue?.id.orEmpty(),
        createdByDeviceId = localNetworkState.value.deviceId.ifBlank { localNetworkDeviceId() },
        createdAtMillis = createdAtMillis,
        status = LOCAL_NETWORK_QUEUE_PENDING
    )

private suspend fun applyLocalWorkshiftEnd(
    workshift: WorkshiftDataModel,
    endedAtMillis: Long,
    message: List<LocalizedStringDataModel> = localNetworkMessage(1150, "Workshift ended locally; cloud sync queued", "Смена завершена локально; синхронизация с облаком поставлена в очередь", "Ауысым жергілікті аяқталды; бұлтпен синхрондау кезекке қойылды")
): WorkshiftDataModel {
    val ended = workshift.localEndedCopy(endedAtMillis)
    activeWorkshiftState.emit(DataState.Success(ended, message))
    return ended
}

private suspend fun queueWorkshiftEndForCloud(
    workshift: WorkshiftDataModel,
    endedAtMillis: Long = getCurrentTimeMillis()
): Pair<WorkshiftEndRequestDataModel, LocalNetworkQueuedOperationDataModel> {
    val operationId = createClientOperationId(
        "workshift-end",
        listOf(workshift.storeId, workshift.id, endedAtMillis.toString()).joinToString("|")
    )
    val request = WorkshiftEndRequestDataModel(
        workshiftId = workshift.id,
        endedAtMillis = endedAtMillis,
        clientOperationId = operationId,
        deviceInfo = buildCurrentClientDeviceInfo()
    )
    val operation = queuedWorkshiftEndOperation(
        storeId = workshift.storeId,
        request = request,
        createdAtMillis = endedAtMillis
    )
    enqueueLocalNetworkOperation(operation)
    return request to operation
}

private fun mutateLocalBatchQuantity(batches: List<GoodsBatchDataModel>, item: GoodsItemDataModel?, line: GoodsItemInTransactionDataModel, transactionType: String): List<GoodsBatchDataModel> {
    if (item == null) return batches
    val delta = when (transactionType) {
        "sale", "purchase" -> -line.quantity
        "return", "supply", "acceptance" -> line.quantity
        else -> 0.0
    }
    if (delta == 0.0) return batches

    val candidateIds = listOfNotNull(item.activeShelfBatchId).toMutableList().apply {
        addAll(batches.filter { it.goodsItemId == item.id && it.isActive }.sortedBy { it.shelfPriority }.map { it.id })
    }.distinct()
    val targetId = candidateIds.firstOrNull { id -> batches.any { it.id == id } } ?: return batches
    return batches.map { batch ->
        if (batch.id == targetId) {
            val updatedQuantity = batch.quantity.copy(total = (batch.quantity.total + delta).coerceAtLeast(0.0))
            batch.copy(quantity = updatedQuantity, updatedAtMillis = getCurrentTimeMillis())
        } else batch
    }
}

private suspend fun applyLocalQueuedTransaction(transaction: TransactionDataModel, operationId: String): TransactionDataModel {
    val localId = transaction.id.ifBlank { transactionLocalId(operationId) }
    val completed = transaction.copy(id = localId, clientOperationId = operationId, timeMillis = transaction.timeMillis.takeIf { it > 0 } ?: getCurrentTimeMillis())
    val message = localNetworkMessage(733, "Queued locally for cloud sync", "Сохранено локально для синхронизации", "Бұлтпен синхрондау үшін жергілікті сақталды")

    val existingTransactions = transactionsState.payloadValue.orEmpty()
    if (existingTransactions.any { it.clientOperationId == operationId || it.id == localId }) return completed

    var updatedBatches = stockBatchesState.payloadValue.orEmpty()
    val stock = stockState.payloadValue.orEmpty()
    completed.goodsInTransaction.forEach { line ->
        val item = stock.firstOrNull { goods ->
            goods.id == line.goodsItemId || goods.allBarcodeValues().any { it == line.barcode || it.toStoredGoodsItemBarcode() == line.barcode.toStoredGoodsItemBarcode() }
        }
        updatedBatches = mutateLocalBatchQuantity(updatedBatches, item, line, completed.type)
    }
    stockBatchesState.emit(DataState.Success(updatedBatches, message))

    transactionsState.emit(
        DataState.Success(
            (existingTransactions + completed).distinctBy { it.clientOperationId.ifBlank { it.id } }.sortedByDescending { it.timeMillis },
            message
        )
    )

    completed.debtor?.let { debtor ->
        debtorsState.emit(DataState.Success(debtorsState.payloadValue.orEmpty().upsertDebtor(debtor), message))
    }

    val cashDelta = when (completed.type) {
        "sale", "purchase" -> completed.paidCash
        "return" -> -completed.paidCash
        else -> 0.0
    }
    if (cashDelta != 0.0) {
        val current = cashRegisterState.payloadValue ?: StoreCashRegisterDataModel(storeId = completed.storeId, currentAmount = cashRegisterAmountState.value)
        val updated = current.copy(currentAmount = (current.currentAmount + cashDelta).roundMoney(), updatedAtMillis = getCurrentTimeMillis())
        cashRegisterState.emit(DataState.Success(updated, message))
        cashRegisterAmountState.emit(updated.currentAmount)
    }

    return completed
}

private suspend fun handleLocalNetworkOperation(operation: LocalNetworkQueuedOperationDataModel): LocalNetworkEnvelopeDataModel {
    return try {
        when (operation.operationType) {
            LOCAL_NETWORK_OPERATION_TRANSACTION_COMPLETE -> {
                val transaction = jsonBase.decodeFromString<TransactionDataModel>(operation.bodyJson).withClientOperationId()
                val opId = operation.id.ifBlank { transaction.clientOperationId }
                val serverOrderedOperation = operation.copy(
                    id = opId,
                    bodyJson = jsonBase.encodeToString(transaction.copy(clientOperationId = opId)),
                    createdAtMillis = operation.createdAtMillis.takeIf { it > 0L } ?: transaction.timeMillis.takeIf { it > 0L } ?: nextLocalNetworkOperationTimestamp(),
                    status = LOCAL_NETWORK_QUEUE_PENDING,
                    lastError = null
                )
                enqueueLocalNetworkOperation(serverOrderedOperation)
                applyLocalQueuedTransaction(transaction.copy(clientOperationId = opId), opId)
                broadcastLocalNetworkSnapshot()
                localNetworkEnvelope("ack", operation = serverOrderedOperation, snapshot = localNetworkSnapshot())
            }
            LOCAL_NETWORK_OPERATION_WORKSHIFT_END -> {
                val request = jsonBase.decodeFromString<WorkshiftEndRequestDataModel>(operation.bodyJson)
                val opId = operation.id.ifBlank { request.clientOperationId.ifBlank { createClientOperationId("workshift-end", request.workshiftId.orEmpty()) } }
                val serverOrderedOperation = operation.copy(
                    id = opId,
                    bodyJson = jsonBase.encodeToString(request.copy(clientOperationId = request.clientOperationId.ifBlank { opId })),
                    createdAtMillis = operation.createdAtMillis.takeIf { it > 0L } ?: request.endedAtMillis.takeIf { it > 0L } ?: nextLocalNetworkOperationTimestamp(),
                    status = LOCAL_NETWORK_QUEUE_PENDING,
                    lastError = null
                )
                enqueueLocalNetworkOperation(serverOrderedOperation)
                broadcastLocalNetworkSnapshot()
                localNetworkEnvelope("ack", operation = serverOrderedOperation, snapshot = localNetworkSnapshot())
            }
            else -> localNetworkEnvelope("ack", operation = operation, accepted = false, error = "Unsupported local operation")
        }
    } catch (throwable: Throwable) {
        localNetworkEnvelope("ack", operation = operation, accepted = false, error = throwable.message ?: "Local operation failed")
    }
}

private suspend fun handleLocalNetworkMessage(message: String, senderHost: String): String {
    return try {
        val envelope = jsonBase.decodeFromString<LocalNetworkEnvelopeDataModel>(message)
        envelope.device?.let { updateLocalNetworkDevice(it.copy(host = it.host.ifBlank { senderHost })) }
        when (envelope.type) {
            "hello" -> {
                if (localNetworkState.value.enabled) {
                    jsonBase.encodeToString(localNetworkEnvelope("hello_ack", snapshot = if (localNetworkState.value.isServer) localNetworkSnapshot() else null))
                } else ""
            }
            "hello_ack" -> {
                envelope.snapshot?.let { snapshot -> applyLocalNetworkSnapshot(snapshot) }
                ""
            }
            "operation" -> {
                val operation = envelope.operation
                if (operation == null) {
                    jsonBase.encodeToString(localNetworkEnvelope("ack", accepted = false, error = "Missing operation"))
                } else if (!localNetworkState.value.isServer) {
                    jsonBase.encodeToString(localNetworkEnvelope("ack", operation = operation, accepted = false, error = "This device is not the local server"))
                } else {
                    jsonBase.encodeToString(handleLocalNetworkOperation(operation))
                }
            }
            "state" -> {
                envelope.snapshot?.let { snapshot -> applyLocalNetworkSnapshot(snapshot) }
                jsonBase.encodeToString(localNetworkEnvelope("ack"))
            }
            else -> jsonBase.encodeToString(localNetworkEnvelope("ack"))
        }
    } catch (throwable: Throwable) {
        jsonBase.encodeToString(localNetworkEnvelope("ack", accepted = false, error = throwable.message ?: "Bad local network message"))
    }
}

private var localNetworkDiscoveryJob: Job? = null
private var localNetworkSyncJob: Job? = null
private val localNetworkCloudSyncMutex = Mutex()

private fun startLocalNetworkTransportIfNeeded() {
    val state = localNetworkState.value
    if (!state.enabled) return
    val started = runCatching {
        LocalAitaLanTransport.start(
            deviceId = state.deviceId.ifBlank { localNetworkDeviceId() },
            tcpPort = state.tcpPort,
            discoveryPort = state.discoveryPort,
            onMessage = ::handleLocalNetworkMessage
        )
    }.getOrDefault(false)
    if (!started) {
        localNetworkState.value = state.copy(lastError = "Could not start local network listener")
    }
}

private fun startLocalNetworkDiscoveryLoop() {
    localNetworkDiscoveryJob?.cancel()
    localNetworkDiscoveryJob = GlobalScope.launch(Dispatchers.ourIo) {
        while (isActive) {
            val state = localNetworkState.value
            if (state.enabled) {
                val envelope = localNetworkEnvelope("hello")
                runCatching { LocalAitaLanTransport.broadcast(jsonBase.encodeToString(envelope), state.discoveryPort) }
                localNetworkState.emit(state.copy(lastDiscoveryMillis = getCurrentTimeMillis()))
                localNetworkDevicesState.value = localNetworkDevicesState.value.filter { getCurrentTimeMillis() - it.lastSeenMillis <= LOCAL_NETWORK_STALE_DEVICE_MILLIS }

                val updatedState = localNetworkState.value
                val devices = localNetworkDevicesState.value
                val self = currentLocalNetworkDevice()
                val visibleServers = devices.filter { it.role == LOCAL_NETWORK_ROLE_SERVER }
                when (updatedState.role) {
                    LOCAL_NETWORK_ROLE_SERVER -> {
                        visibleServers
                            .filter { it.deviceId < self.deviceId }
                            .minByOrNull { it.deviceId }
                            ?.let { joinLocalBranchServer(it) }
                    }
                    LOCAL_NETWORK_ROLE_AUTO, LOCAL_NETWORK_ROLE_CLIENT -> {
                        val server = visibleServers.minByOrNull { it.deviceId }
                        when {
                            server != null && updatedState.serverDeviceId != server.deviceId -> joinLocalBranchServer(server)
                            server == null && updatedState.role == LOCAL_NETWORK_ROLE_AUTO -> {
                                val elected = (devices + self).minByOrNull { it.deviceId }
                                if (elected?.deviceId == self.deviceId) becomeLocalBranchServer()
                            }
                        }
                    }
                }
            }
            delay(5_000)
        }
    }
}

private fun startLocalNetworkSyncLoop() {
    localNetworkSyncJob?.cancel()
    localNetworkSyncJob = GlobalScope.launch(Dispatchers.ourIo) {
        while (isActive) {
            delay(8_000)
            if (realtimeUpdatesConnectedState.value || cloudTransportStatusState.value == CLOUD_TRANSPORT_STATUS_REACHABLE) {
                syncLocalNetworkOperationsToCloud()
            }
        }
    }
}

fun initializeLocalBranchNetwork() {
    GlobalScope.launch(Dispatchers.ourIo) {
        loadLocalNetworkCache()
        if (localNetworkState.value.enabled) {
            startLocalNetworkTransportIfNeeded()
            startLocalNetworkDiscoveryLoop()
            startLocalNetworkSyncLoop()
        }
    }
}

fun enableLocalBranchNetwork(asServer: Boolean = false) {
    GlobalScope.launch(Dispatchers.ourIo) {
        if (!localBranchNetworkAllowedForActiveStore()) {
            postInAppNotification(localBranchNetworkRestrictionMessage(), NotificationType.Negative)
            return@launch
        }

        val role = if (asServer) LOCAL_NETWORK_ROLE_SERVER else LOCAL_NETWORK_ROLE_AUTO
        val state = localNetworkState.value.copy(
            enabled = true,
            role = role,
            deviceId = localNetworkState.value.deviceId.ifBlank { localNetworkDeviceId() },
            tcpPort = localNetworkState.value.tcpPort.takeIf { it > 0 } ?: LOCAL_NETWORK_DEFAULT_TCP_PORT,
            discoveryPort = localNetworkState.value.discoveryPort.takeIf { it > 0 } ?: LOCAL_NETWORK_DEFAULT_DISCOVERY_PORT,
            activeStoreId = activeStoreIdState.value,
            branchStoreId = activeStoreIdState.value,
            serverDeviceId = if (asServer) localNetworkDeviceId() else localNetworkState.value.serverDeviceId,
            serverHost = if (asServer) LocalAitaLanTransport.localHostAddress() else localNetworkState.value.serverHost,
            lastError = null
        )
        localNetworkState.emit(state)
        persistLocalNetworkState()
        startLocalNetworkTransportIfNeeded()
        startLocalNetworkDiscoveryLoop()
        startLocalNetworkSyncLoop()
        postInAppNotification(
            localNetworkMessage(728, "Local branch network enabled", "Локальная сеть филиала включена", "Филиалдың жергілікті желісі қосылды"),
            NotificationType.Positive,
            transient = true
        )
    }
}

fun disableLocalBranchNetwork() {
    GlobalScope.launch(Dispatchers.ourIo) {
        LocalAitaLanTransport.stop()
        localNetworkDiscoveryJob?.cancel()
        localNetworkSyncJob?.cancel()
        localNetworkState.emit(localNetworkState.value.copy(enabled = false, role = LOCAL_NETWORK_ROLE_DISABLED, serverDeviceId = null, serverHost = null))
        persistLocalNetworkState()
        postInAppNotification(
            localNetworkMessage(729, "Local branch network disabled", "Локальная сеть филиала выключена", "Филиалдың жергілікті желісі өшірілді"),
            NotificationType.Neutral,
            transient = true
        )
    }
}

fun becomeLocalBranchServer() {
    GlobalScope.launch(Dispatchers.ourIo) {
        if (!localBranchNetworkAllowedForActiveStore()) {
            postInAppNotification(localBranchNetworkRestrictionMessage(), NotificationType.Negative)
            return@launch
        }

        val state = localNetworkState.value.copy(
            enabled = true,
            role = LOCAL_NETWORK_ROLE_SERVER,
            deviceId = localNetworkState.value.deviceId.ifBlank { localNetworkDeviceId() },
            serverDeviceId = localNetworkState.value.deviceId.ifBlank { localNetworkDeviceId() },
            serverHost = LocalAitaLanTransport.localHostAddress(),
            serverPort = localNetworkState.value.tcpPort.takeIf { it > 0 } ?: LOCAL_NETWORK_DEFAULT_TCP_PORT,
            activeStoreId = activeStoreIdState.value,
            branchStoreId = activeStoreIdState.value,
            lastError = null
        )
        localNetworkState.emit(state)
        persistLocalNetworkState()
        startLocalNetworkTransportIfNeeded()
        startLocalNetworkDiscoveryLoop()
        startLocalNetworkSyncLoop()
        broadcastLocalNetworkSnapshot()
        postInAppNotification(
            localNetworkMessage(730, "This device is the local server", "Это устройство — локальный сервер", "Бұл құрылғы жергілікті сервер"),
            NotificationType.Positive,
            transient = true
        )
    }
}

fun joinLocalBranchServer(device: LocalNetworkDeviceDataModel) {
    if (device.host.isBlank()) return
    GlobalScope.launch(Dispatchers.ourIo) {
        if (!localBranchNetworkAllowedForActiveStore()) {
            postInAppNotification(localBranchNetworkRestrictionMessage(), NotificationType.Negative)
            return@launch
        }

        localNetworkState.emit(
            localNetworkState.value.copy(
                enabled = true,
                role = LOCAL_NETWORK_ROLE_CLIENT,
                deviceId = localNetworkState.value.deviceId.ifBlank { localNetworkDeviceId() },
                serverDeviceId = device.deviceId,
                serverHost = device.host,
                serverPort = device.port,
                activeStoreId = activeStoreIdState.value,
                branchStoreId = activeStoreIdState.value,
                lastError = null
            )
        )
        persistLocalNetworkState()
        startLocalNetworkTransportIfNeeded()
        startLocalNetworkDiscoveryLoop()
        startLocalNetworkSyncLoop()
        val response = runCatching { LocalAitaLanTransport.send(device.host, device.port, jsonBase.encodeToString(localNetworkEnvelope("hello")), 2500) }.getOrNull()
        response?.let { handleLocalNetworkMessage(it, device.host) }
        postInAppNotification(
            localNetworkMessage(731, "Joined local server", "Подключено к локальному серверу", "Жергілікті серверге қосылды"),
            NotificationType.Positive,
            transient = true
        )
    }
}

fun scanLocalBranchNetworkNow() {
    GlobalScope.launch(Dispatchers.ourIo) {
        if (!localNetworkState.value.enabled) enableLocalBranchNetwork(asServer = false)
        runCatching { LocalAitaLanTransport.broadcast(jsonBase.encodeToString(localNetworkEnvelope("hello")), localNetworkState.value.discoveryPort) }
    }
}

private suspend fun sendOperationToLocalServer(operation: LocalNetworkQueuedOperationDataModel): LocalNetworkEnvelopeDataModel? {
    val state = localNetworkState.value
    val host = state.serverHost ?: return null
    val envelope = localNetworkEnvelope("operation", operation = operation)
    val response = runCatching { LocalAitaLanTransport.send(host, state.serverPort, jsonBase.encodeToString(envelope), 3500) }.getOrNull()
    return response?.let { jsonBase.decodeFromString<LocalNetworkEnvelopeDataModel>(it) }
}

private suspend fun queueTransactionThroughLocalNetwork(transaction: TransactionDataModel): TransactionDataModel? {
    if (localNetworkState.value.enabled && !localBranchNetworkAllowedForActiveStore()) {
        postInAppNotification(localBranchNetworkRestrictionMessage(), NotificationType.Negative)
        return null
    }

    val tx = transaction.withClientOperationId()
    val operation = queuedTransactionOperation(tx)
    val state = localNetworkState.value

    return when {
        state.enabled && state.isServer -> {
            queueTransactionForCloudSync(tx, broadcastSnapshot = true)
        }

        state.enabled && state.hasServer -> {
            val ack = sendOperationToLocalServer(operation)
            if (ack?.accepted == true) {
                ack.snapshot?.let { applyLocalNetworkSnapshot(it) }
                tx.copy(id = transactionLocalId(operation.id), clientOperationId = operation.id)
            } else {
                queueTransactionForCloudSync(tx, broadcastSnapshot = false)
            }
        }

        else -> {
            // No LAN server is configured: keep the operation in this device's durable cloud queue.
            // The same clientOperationId is sent on every retry, so a mid-send reconnect can never
            // create a duplicate transaction on the server.
            queueTransactionForCloudSync(tx, broadcastSnapshot = false)
        }
    }
}


fun clearSyncedLocalNetworkOperations() {
    GlobalScope.launch(Dispatchers.ourIo) {
        localNetworkQueuedOperationsState.emit(localNetworkQueuedOperationsState.value.filter { it.status != LOCAL_NETWORK_QUEUE_SYNCED })
        persistLocalNetworkQueue()
    }
}

private suspend fun updateQueuedLocalNetworkOperation(
    operationId: String,
    transform: (LocalNetworkQueuedOperationDataModel) -> LocalNetworkQueuedOperationDataModel
) {
    localNetworkQueuedOperationsState.emit(
        localNetworkQueuedOperationsState.value.map { operation ->
            if (operation.id == operationId) transform(operation) else operation
        }
    )
    persistLocalNetworkQueue()
}

suspend fun syncLocalNetworkOperationsToCloudNow(): Int {
    if (localNetworkCloudSyncMutex.isLocked) return 0

    return localNetworkCloudSyncMutex.withLock {
        val pending = localNetworkQueuedOperationsState.value
            .filter { it.status == LOCAL_NETWORK_QUEUE_PENDING || it.status == LOCAL_NETWORK_QUEUE_SYNCING }
            .sortedWith(compareBy<LocalNetworkQueuedOperationDataModel> { it.createdAtMillis }.thenBy { it.id })
        if (pending.isEmpty()) return@withLock 0

        logLocalNetworkQueueDiagnostic("sync start count=${pending.size} first=${pending.firstOrNull()?.id.orEmpty()} status=${cloudTransportStatusName(cloudTransportStatusState.value)} realtime=${realtimeUpdatesConnectedState.value}")

        var syncedCount = 0
        var activeStoreRefreshNeeded = false

        for (operation in pending) {
            logLocalNetworkQueueDiagnostic("sync attempt id=${operation.id} type=${operation.operationType} attempts=${operation.attemptCount}")
            updateQueuedLocalNetworkOperation(operation.id) {
                it.copy(status = LOCAL_NETWORK_QUEUE_SYNCING, lastError = null)
            }

            when (operation.operationType) {
                LOCAL_NETWORK_OPERATION_TRANSACTION_COMPLETE -> {
                    val tx = runCatching { jsonBase.decodeFromString<TransactionDataModel>(operation.bodyJson) }.getOrNull()
                    if (tx == null) {
                        updateQueuedLocalNetworkOperation(operation.id) {
                            it.copy(
                                status = LOCAL_NETWORK_QUEUE_FAILED,
                                attemptCount = it.attemptCount + 1,
                                lastError = "Could not decode transaction"
                            )
                        }
                        continue
                    }

                    val response = networkRequest<TransactionDataModel, TransactionDataModel>(
                        method = HttpMethod.Post,
                        endpointUrl = operation.endpointPath.ifBlank { globalAppConfigurationState.payloadValue.completeTransactionPath.first },
                        headers = buildMap {
                            tx.storeId.takeIf { it.isNotBlank() }?.let { put("store_id", it) }
                            put("X-AITA-Client-Operation-Id", operation.id)
                        },
                        body = tx.copy(clientOperationId = operation.id)
                    )

                    if (response.transportFailure ||
                        response.httpStatusCode == HttpStatusCode.Unauthorized.value ||
                        response.httpStatusCode == HttpStatusCode.ServiceUnavailable.value ||
                        (response.httpStatusCode ?: 0) >= 500
                    ) {
                        updateQueuedLocalNetworkOperation(operation.id) {
                            it.copy(
                                status = LOCAL_NETWORK_QUEUE_PENDING,
                                attemptCount = it.attemptCount + 1,
                                lastError = response.message?.extractLocalizedString(appLanguageState.value)
                            )
                        }
                        logLocalNetworkQueueDiagnostic("sync paused id=${operation.id} http=${response.httpStatusCode} transport=${response.transportFailure}")
                        break
                    }

                    if (response.negative || response.payload == null) {
                        logCloudConnectionDiagnostic(
                            "local outbox sync failed id=${operation.id} type=${operation.operationType} " +
                                    "http=${response.httpStatusCode ?: -1} negative=${response.negative}"
                        )
                        updateQueuedLocalNetworkOperation(operation.id) {
                            it.copy(
                                status = LOCAL_NETWORK_QUEUE_FAILED,
                                attemptCount = it.attemptCount + 1,
                                lastError = response.message?.extractLocalizedString(appLanguageState.value)
                            )
                        }
                        break
                    }

                    val synced = response.payload
                    logCloudConnectionDiagnostic(
                        "local outbox sync success id=${operation.id} type=${operation.operationType} " +
                                "http=${response.httpStatusCode ?: -1}"
                    )
                    updateQueuedLocalNetworkOperation(operation.id) {
                        it.copy(
                            status = LOCAL_NETWORK_QUEUE_SYNCED,
                            cloudSyncedAtMillis = getCurrentTimeMillis(),
                            attemptCount = it.attemptCount + 1,
                            lastError = null
                        )
                    }
                    syncedCount += 1
                    logLocalNetworkQueueDiagnostic("sync success id=${operation.id} type=${operation.operationType}")

                    transactionsState.emit(
                        DataState.Success(
                            transactionsState.payloadValue.orEmpty().map { existing ->
                                if (existing.clientOperationId == operation.id || existing.id == transactionLocalId(operation.id)) synced else existing
                            }.plus(synced).distinctBy { it.clientOperationId.ifBlank { it.id } }.sortedByDescending { it.timeMillis },
                            response.message
                        )
                    )

                    activeStoreRefreshNeeded = true
                    broadcastLocalNetworkSnapshot()
                }

                LOCAL_NETWORK_OPERATION_WORKSHIFT_END -> {
                    val request = runCatching { jsonBase.decodeFromString<WorkshiftEndRequestDataModel>(operation.bodyJson) }.getOrNull()
                    if (request == null) {
                        updateQueuedLocalNetworkOperation(operation.id) {
                            it.copy(
                                status = LOCAL_NETWORK_QUEUE_FAILED,
                                attemptCount = it.attemptCount + 1,
                                lastError = "Could not decode workshift end"
                            )
                        }
                        continue
                    }

                    val response = networkRequest<WorkshiftDataModel, WorkshiftEndRequestDataModel>(
                        method = HttpMethod.Post,
                        endpointUrl = operation.endpointPath.ifBlank { globalAppConfigurationState.payloadValue.endWorkshiftPath.first },
                        headers = buildMap {
                            operation.storeId.takeIf { it.isNotBlank() }?.let { put("store_id", it) }
                            put("X-AITA-Client-Operation-Id", operation.id)
                        },
                        body = request.copy(clientOperationId = request.clientOperationId.ifBlank { operation.id })
                    )

                    if (response.transportFailure ||
                        response.httpStatusCode == HttpStatusCode.Unauthorized.value ||
                        response.httpStatusCode == HttpStatusCode.ServiceUnavailable.value ||
                        (response.httpStatusCode ?: 0) >= 500
                    ) {
                        updateQueuedLocalNetworkOperation(operation.id) {
                            it.copy(
                                status = LOCAL_NETWORK_QUEUE_PENDING,
                                attemptCount = it.attemptCount + 1,
                                lastError = response.message?.extractLocalizedString(appLanguageState.value)
                            )
                        }
                        logLocalNetworkQueueDiagnostic("sync paused id=${operation.id} http=${response.httpStatusCode} transport=${response.transportFailure}")
                        break
                    }

                    if (response.negative || response.payload == null) {
                        logCloudConnectionDiagnostic(
                            "local outbox sync failed id=${operation.id} type=${operation.operationType} " +
                                    "http=${response.httpStatusCode ?: -1} negative=${response.negative}"
                        )
                        updateQueuedLocalNetworkOperation(operation.id) {
                            it.copy(
                                status = LOCAL_NETWORK_QUEUE_FAILED,
                                attemptCount = it.attemptCount + 1,
                                lastError = response.message?.extractLocalizedString(appLanguageState.value)
                            )
                        }
                        break
                    }

                    logCloudConnectionDiagnostic(
                        "local outbox sync success id=${operation.id} type=${operation.operationType} " +
                                "http=${response.httpStatusCode ?: -1}"
                    )
                    updateQueuedLocalNetworkOperation(operation.id) {
                        it.copy(
                            status = LOCAL_NETWORK_QUEUE_SYNCED,
                            cloudSyncedAtMillis = getCurrentTimeMillis(),
                            attemptCount = it.attemptCount + 1,
                            lastError = null
                        )
                    }
                    syncedCount += 1
                    logLocalNetworkQueueDiagnostic("sync success id=${operation.id} type=${operation.operationType}")

                    val syncedWorkshift = response.payload
                    if (activeWorkshiftState.payloadValue?.id == syncedWorkshift.id) {
                        activeWorkshiftState.emit(DataState.Empty(response.message))
                    }

                    activeStoreRefreshNeeded = true
                }

                else -> {
                    updateQueuedLocalNetworkOperation(operation.id) {
                        it.copy(
                            status = LOCAL_NETWORK_QUEUE_FAILED,
                            attemptCount = it.attemptCount + 1,
                            lastError = "Unsupported operation type: ${operation.operationType}"
                        )
                    }
                }
            }
        }

        if (syncedCount > 0) {
            if (activeStoreRefreshNeeded) {
                activeStoreIdState.value?.let { storeId ->
                    getStock(storeId)
                    getStockBatches(storeId)
                    getTransactions(storeId)
                    getCashRegister(storeId)
                    getCurrentWorkshift(storeId)
                    getOperationLogs(storeId, OPERATION_LOG_SCOPE_CURRENT)
                }
            }
            localNetworkQueuedOperationsState.emit(
                localNetworkQueuedOperationsState.value.filter { it.status != LOCAL_NETWORK_QUEUE_SYNCED }
            )
            persistLocalNetworkQueue()
        }

        localNetworkState.emit(localNetworkState.value.copy(lastSyncMillis = getCurrentTimeMillis()))
        persistLocalNetworkState()
        logLocalNetworkQueueDiagnostic("sync finish synced=$syncedCount remaining=${localNetworkQueuedOperationsState.value.count { it.status == LOCAL_NETWORK_QUEUE_PENDING || it.status == LOCAL_NETWORK_QUEUE_SYNCING }}")
        syncedCount
    }
}

fun syncLocalNetworkOperationsToCloud() {
    GlobalScope.launch(Dispatchers.ourIo) {
        syncLocalNetworkOperationsToCloudNow()
    }
}

private fun cacheMessage(): List<LocalizedStringDataModel> = localizedStringResourceMessage(
    id = 572,
    main = "Loaded cached data",
    ru = "Загружены сохранённые данные",
    kk = "Сақталған деректер жүктелді"
)

private fun realtimeConnectedMessage(): List<LocalizedStringDataModel> = localizedStringResourceMessage(
    id = 573,
    main = "Live updates connected",
    ru = "Онлайн-обновления подключены",
    kk = "Нақты уақыттағы жаңартулар қосылды"
)

private fun realtimeDisconnectedMessage(): List<LocalizedStringDataModel> = localizedStringResourceMessage(
    id = 574,
    main = "Live updates disconnected. Using cached data while reconnecting.",
    ru = "Онлайн-обновления отключены. Пока идёт переподключение, используются сохранённые данные.",
    kk = "Нақты уақыттағы жаңартулар ажыратылды. Қайта қосылғанша сақталған деректер қолданылады."
)

private fun realtimeRefreshingMessage(): List<LocalizedStringDataModel> = localizedStringResourceMessage(
    id = 575,
    main = "Refreshing changed data",
    ru = "Обновление изменённых данных",
    kk = "Өзгерген деректер жаңартылуда"
)

private var appCacheCollectorsStarted = false
private var cloudConnectionHealthMonitorJob: Job? = null
private var realtimeUpdatesJob: Job? = null
private var realtimeRefreshJob: Job? = null
private val realtimeRefreshMutex = Mutex()
private val cloudConnectionHealthProbeMutex = Mutex()
private var realtimeOfflineNoticePosted = false
val realtimeUpdatesConnectedState = MutableStateFlow(false)

private suspend fun loadCachedStoreScopedData(storeId: String) {
    getJsonCache<List<GoodsItemDataModel>>(storeScopedCacheKey("stock", storeId))?.let {
        stockState.emit(DataState.Success(it, cacheMessage()))
    }
    getJsonCache<List<GoodsBatchDataModel>>(storeScopedCacheKey("stock_batches", storeId))?.let {
        stockBatchesState.emit(DataState.Success(it, cacheMessage()))
    }
    getJsonCache<List<TransactionDataModel>>(storeScopedCacheKey("transactions", storeId))?.let {
        transactionsState.emit(DataState.Success(it, cacheMessage()))
    }
    getJsonCache<List<DebtorDataModel>>(storeScopedCacheKey("debtors", storeId))?.let {
        debtorsState.emit(DataState.Success(it, cacheMessage()))
    }
    getJsonCache<StoreCashRegisterDataModel>(storeScopedCacheKey("cash_register", storeId))?.let {
        cashRegisterState.emit(DataState.Success(it, cacheMessage()))
        cashRegisterAmountState.emit(it.currentAmount)
    }
    getJsonCache<List<CashRegisterEventDataModel>>(storeScopedCacheKey("cash_register_events", storeId))?.let { events ->
        cashRegisterEventsState.emit(DataState.Success(events, cacheMessage()))
        cashRegisterExtractionsState.emit(DataState.Success(events.filter { it.type == CASH_REGISTER_EVENT_EXTRACTION }.map { it.toExtractionEntry() }, cacheMessage()))
    }
    getJsonCache<List<StoreWorkerDataModel>>(storeScopedCacheKey("store_workers", storeId))?.let {
        storeWorkerMembershipsState.emit(DataState.Success(it, cacheMessage()))
    }
    getJsonCache<List<StoreWorkerRequestDataModel>>(storeScopedCacheKey("incoming_worker_requests", storeId))?.let {
        incomingWorkerRequestsState.emit(DataState.Success(it, cacheMessage()))
    }
    getJsonCache<List<OperationLogDataModel>>(storeScopedCacheKey("operation_logs", storeId))?.let {
        operationLogsState.emit(DataState.Success(it, cacheMessage()))
    }
}

private suspend fun loadCachedApplicationData() {
    getJsonCache<GlobalAppConfigurationDataModel>(CACHE_GLOBAL_CONFIG)?.let {
        val currentConfiguration = globalAppConfigurationState.payloadValue
        val anchoredServerUrl = chooseClientServerUrlPair(currentConfiguration.serverUrl, it.serverUrl)
        globalAppConfigurationState.emit(
            DataState.Success(
                it.copy(serverUrl = anchoredServerUrl),
                cacheMessage()
            )
        )
    }
    getJsonCache<List<LocalizedStringGroupDataModel>>(CACHE_STRINGS)?.let {
        stringsState.emit(DataState.Success(it, cacheMessage()))
    }
    getJsonCache<List<StylizedDimensionGroupDataModel>>(CACHE_DIMENSIONS)?.let {
        dimensionsState.emit(DataState.Success(it, cacheMessage()))
    }
    getJsonCache<List<StylizedColorGroupDataModel>>(CACHE_COLORS)?.let {
        colorsState.emit(DataState.Success(it, cacheMessage()))
    }
    getJsonCache<List<StylizedDrawablePathsGroupDataModel>>(CACHE_DRAWABLES)?.let {
        drawablesState.emit(DataState.Success(it, cacheMessage()))
    }
    getJsonCache<UserAccountDataModel>(CACHE_USER)?.let {
        userAccountState.emit(DataState.Success(it, cacheMessage()))
    }
    getJsonCache<List<StoreDataModel>>(CACHE_STORES)?.let {
        storesState.emit(DataState.Success(it, cacheMessage()))
    }
    getJsonCache<List<SupplierDataModel>>(CACHE_SUPPLIERS)?.let {
        suppliersState.emit(DataState.Success(it, cacheMessage()))
    }
    getJsonCache<List<GenericGoodsCategoryDataModel>>(CACHE_GENERIC_GOODS_CATEGORIES)?.let {
        genericGoodsCategoriesState.emit(DataState.Success(it, cacheMessage()))
        categoriesState.emit(DataState.Success(it, cacheMessage()))
    }
    getJsonCache<List<GenericGoodsItemDataModel>>(CACHE_GENERIC_GOODS_ITEMS)?.let {
        genericGoodsItemsState.emit(DataState.Success(it, cacheMessage()))
    }
    getJsonCache<List<NotificationDataModel>>(CACHE_NOTIFICATIONS)?.let {
        notificationsState.emit(DataState.Success(it, cacheMessage()))
    }
    getJsonCache<List<SecuritySessionDataModel>>(CACHE_SECURITY_SESSIONS)?.let {
        securitySessionsState.emit(DataState.Success(it, cacheMessage()))
    }
    getJsonCache<List<SecuritySessionHistoryDataModel>>(CACHE_SECURITY_SESSION_HISTORY)?.let {
        securitySessionHistoryState.emit(DataState.Success(it, cacheMessage()))
    }
    getJsonCache<List<StoreWorkerDataModel>>(CACHE_MY_WORKER_MEMBERSHIPS)?.let {
        myWorkerMembershipsState.emit(DataState.Success(it, cacheMessage()))
    }
    getJsonCache<List<StoreWorkerRequestDataModel>>(CACHE_MY_WORKER_REQUESTS)?.let {
        myWorkerRequestsState.emit(DataState.Success(it, cacheMessage()))
    }
    getJsonCache<UserFinanceDashboardDataModel>(CACHE_USER_FINANCE_DASHBOARD)?.let {
        userFinanceDashboardState.emit(DataState.Success(it, cacheMessage()))
        userWalletState.emit(DataState.Success(it.wallet, cacheMessage()))
        userWalletLedgerState.emit(DataState.Success(it.ledger, cacheMessage()))
        paymentIntentsState.emit(DataState.Success(it.paymentIntents, cacheMessage()))
    }
    getJsonCache<List<StoreSubscriptionPlanDataModel>>(CACHE_SUBSCRIPTION_PLANS)?.let { cachedPlans ->
        val allowedPlanIds = defaultStoreSubscriptionPlans().map { it.id }.toSet()
        val visiblePlans = cachedPlans.filter { it.id in allowedPlanIds }.ifEmpty { defaultStoreSubscriptionPlans() }
        subscriptionPlansState.emit(DataState.Success(visiblePlans, cacheMessage()))
    }

    getLocalKv(KEY_ACTIVE_STORE_ID)?.takeIf { it.isNotBlank() }?.let { storeId ->
        activeStoreIdState.emit(storeId)
        loadCachedStoreScopedData(storeId)
    }
}

private fun startAppCacheCollectors() {
    if (appCacheCollectorsStarted) return
    appCacheCollectorsStarted = true

    GlobalScope.launch(Dispatchers.ourIo) {
        globalAppConfigurationState.payload.collect { nextConfiguration ->
            val existingCached = getJsonCache<GlobalAppConfigurationDataModel>(CACHE_GLOBAL_CONFIG)
            val nextNormalized = normalizedHttpServerUrlOrNull(nextConfiguration.serverUrl.first)
            val defaultNormalized = normalizedHttpServerUrlOrNull(DEFAULT_AITA_SERVER_URL)
            val existingNonDefault = existingCached?.serverUrl?.takeIf { cachedPair ->
                val cachedNormalized = normalizedHttpServerUrlOrNull(cachedPair.first)
                cachedNormalized != null && cachedNormalized != defaultNormalized
            }
            val serverUrlForCache = when {
                existingNonDefault != null && (nextNormalized == null || nextNormalized == defaultNormalized) -> existingNonDefault
                else -> nextConfiguration.serverUrl
            }
            putJsonCache(CACHE_GLOBAL_CONFIG, nextConfiguration.copy(serverUrl = serverUrlForCache))
        }
    }
    GlobalScope.launch(Dispatchers.ourIo) { stringsState.payload.collect { it?.let { putJsonCache(CACHE_STRINGS, it) } } }
    GlobalScope.launch(Dispatchers.ourIo) { dimensionsState.payload.collect { it?.let { putJsonCache(CACHE_DIMENSIONS, it) } } }
    GlobalScope.launch(Dispatchers.ourIo) { colorsState.payload.collect { it?.let { putJsonCache(CACHE_COLORS, it) } } }
    GlobalScope.launch(Dispatchers.ourIo) { drawablesState.payload.collect { it?.let { putJsonCache(CACHE_DRAWABLES, it) } } }
    GlobalScope.launch(Dispatchers.ourIo) { userAccountState.payload.collect { it?.let { putJsonCache(CACHE_USER, it) } } }
    GlobalScope.launch(Dispatchers.ourIo) { storesState.payload.collect { it?.let { putJsonCache(CACHE_STORES, it) } } }
    GlobalScope.launch(Dispatchers.ourIo) { suppliersState.payload.collect { it?.let { putJsonCache(CACHE_SUPPLIERS, it) } } }
    GlobalScope.launch(Dispatchers.ourIo) { genericGoodsCategoriesState.payload.collect { it?.let { putJsonCache(CACHE_GENERIC_GOODS_CATEGORIES, it) } } }
    GlobalScope.launch(Dispatchers.ourIo) { genericGoodsItemsState.payload.collect { it?.let { putJsonCache(CACHE_GENERIC_GOODS_ITEMS, it) } } }
    GlobalScope.launch(Dispatchers.ourIo) { notificationsState.payload.collect { it?.let { putJsonCache(CACHE_NOTIFICATIONS, it) } } }
    GlobalScope.launch(Dispatchers.ourIo) { securitySessionsState.payload.collect { it?.let { putJsonCache(CACHE_SECURITY_SESSIONS, it) } } }
    GlobalScope.launch(Dispatchers.ourIo) { securitySessionHistoryState.payload.collect { it?.let { putJsonCache(CACHE_SECURITY_SESSION_HISTORY, it) } } }
    GlobalScope.launch(Dispatchers.ourIo) { myWorkerMembershipsState.payload.collect { it?.let { putJsonCache(CACHE_MY_WORKER_MEMBERSHIPS, it) } } }
    GlobalScope.launch(Dispatchers.ourIo) { myWorkerRequestsState.payload.collect { it?.let { putJsonCache(CACHE_MY_WORKER_REQUESTS, it) } } }
    GlobalScope.launch(Dispatchers.ourIo) { userFinanceDashboardState.payload.collect { it?.let { putJsonCache(CACHE_USER_FINANCE_DASHBOARD, it) } } }
    GlobalScope.launch(Dispatchers.ourIo) { subscriptionPlansState.payload.collect { it?.let { putJsonCache(CACHE_SUBSCRIPTION_PLANS, it) } } }

    GlobalScope.launch(Dispatchers.ourIo) {
        operationLogsState.payload.collect { payload ->
            val storeId = activeStoreIdState.value
            if (!storeId.isNullOrBlank() && payload != null) putJsonCache(storeScopedCacheKey("operation_logs", storeId), payload)
        }
    }

    GlobalScope.launch(Dispatchers.ourIo) {
        stockState.payload.collect { payload ->
            val storeId = activeStoreIdState.value
            if (!storeId.isNullOrBlank() && payload != null) putJsonCache(storeScopedCacheKey("stock", storeId), payload)
        }
    }
    GlobalScope.launch(Dispatchers.ourIo) {
        stockBatchesState.payload.collect { payload ->
            val storeId = activeStoreIdState.value
            if (!storeId.isNullOrBlank() && payload != null) putJsonCache(storeScopedCacheKey("stock_batches", storeId), payload)
        }
    }
    GlobalScope.launch(Dispatchers.ourIo) {
        transactionsState.payload.collect { payload ->
            val storeId = activeStoreIdState.value
            if (!storeId.isNullOrBlank() && payload != null) putJsonCache(storeScopedCacheKey("transactions", storeId), payload)
        }
    }
    GlobalScope.launch(Dispatchers.ourIo) {
        debtorsState.payload.collect { payload ->
            val storeId = activeStoreIdState.value
            if (!storeId.isNullOrBlank() && payload != null) putJsonCache(storeScopedCacheKey("debtors", storeId), payload)
        }
    }
    GlobalScope.launch(Dispatchers.ourIo) {
        cashRegisterState.payload.collect { payload ->
            val storeId = activeStoreIdState.value
            if (!storeId.isNullOrBlank() && payload != null) putJsonCache(storeScopedCacheKey("cash_register", storeId), payload)
        }
    }
    GlobalScope.launch(Dispatchers.ourIo) {
        cashRegisterEventsState.payload.collect { payload ->
            val storeId = activeStoreIdState.value
            if (!storeId.isNullOrBlank() && payload != null) putJsonCache(storeScopedCacheKey("cash_register_events", storeId), payload)
        }
    }
    GlobalScope.launch(Dispatchers.ourIo) {
        storeWorkerMembershipsState.payload.collect { payload ->
            val storeId = activeStoreIdState.value
            if (!storeId.isNullOrBlank() && payload != null) putJsonCache(storeScopedCacheKey("store_workers", storeId), payload)
        }
    }
    GlobalScope.launch(Dispatchers.ourIo) {
        incomingWorkerRequestsState.payload.collect { payload ->
            val storeId = activeStoreIdState.value
            if (!storeId.isNullOrBlank() && payload != null) putJsonCache(storeScopedCacheKey("incoming_worker_requests", storeId), payload)
        }
    }
}

private fun String.toRealtimeWebSocketUrl(path: String): String {
    val base = trim().removeSuffix("/")
    val wsBase = when {
        base.startsWith("https://", ignoreCase = true) -> "wss://" + base.substringAfter("https://")
        base.startsWith("http://", ignoreCase = true) -> "ws://" + base.substringAfter("http://")
        base.startsWith("ws://", ignoreCase = true) || base.startsWith("wss://", ignoreCase = true) -> base
        else -> "ws://$base"
    }

    return wsBase.removeSuffix("/") + "/" + path.trimStart('/')
}

private const val REALTIME_REFRESH_DEBOUNCE_MILLIS = 650L
private const val REALTIME_BROAD_REFRESH_MIN_INTERVAL_MILLIS = 20_000L
private const val REALTIME_CONNECTED_REFRESH_MIN_INTERVAL_MILLIS = 30_000L
private const val REALTIME_AFTER_WEBSOCKET_CLOSE_MIN_DELAY_MILLIS = 5_000L
private const val REALTIME_RECENT_UPDATE_IDS_LIMIT = 512

private val realtimeRefreshPlanMutex = Mutex()
private val realtimePendingRefreshEntities = mutableSetOf<String>()
private val realtimeRecentUpdateIds = ArrayDeque<String>()
private val realtimeRecentUpdateIdSet = mutableSetOf<String>()

@Volatile
private var lastRealtimeBroadRefreshAtMillis: Long = 0L

private fun cleanRealtimeEntity(entity: String?): String = entity
    ?.trim()
    ?.takeIf { it.isNotBlank() }
    ?.lowercase()
    ?: "all"

private fun cleanRealtimeReason(reason: String?): String = reason
    ?.trim()
    ?.takeIf { it.isNotBlank() }
    ?.lowercase()
    ?: "unknown"

private fun rememberRealtimeUpdateIdLocked(updateId: String?): Boolean {
    val cleanId = updateId?.trim()?.takeIf { it.isNotBlank() } ?: return true
    if (cleanId in realtimeRecentUpdateIdSet) return false

    realtimeRecentUpdateIdSet.add(cleanId)
    realtimeRecentUpdateIds.addLast(cleanId)

    while (realtimeRecentUpdateIds.size > REALTIME_RECENT_UPDATE_IDS_LIMIT) {
        realtimeRecentUpdateIdSet.remove(realtimeRecentUpdateIds.removeFirst())
    }

    return true
}

private fun realtimeUpdateRequiresBroadRefresh(entity: String, reason: String): Boolean =
    entity == "all" ||
            reason == "connected" ||
            reason == "manual_reconnect" ||
            reason == "manual reconnect" ||
            reason == "connection_sync" ||
            reason == "websocket_connected"

private fun refreshEverythingFromServerAfterRealtimeUpdate() {
    lastRealtimeBroadRefreshAtMillis = getCurrentTimeMillis()

    getGlobalAppConfiguration(loadAll = false)
    getUser(forceLogOut = false)
    getStores()
    getSuppliers()
    getGenericGoodsCategories()
    refreshGenericGoodsItems(limit = 200)
    getNotifications()
    getSupportTickets()
    activeSupportTicketIdState.value?.let { getSupportMessages(it, markRead = true) }
    getSecuritySessions()
    getSecuritySessionHistory()
    getMyWorkerMemberships()
    getMyWorkerRequests()
    getUserFinanceDashboard()
    getSubscriptionPlans()

    activeStoreIdState.value?.let { storeId ->
        getStock(storeId)
        getStockBatches(storeId)
        getTransactions(storeId)
        getDebtors(storeId)
        getCashRegister(storeId)
        getCurrentWorkshift(storeId)
        getStoreWorkers(storeId)
        getIncomingWorkerRequests(storeId)
        getStoreSubscription(storeId)
        getOperationLogs(storeId, OPERATION_LOG_SCOPE_CURRENT)
    }
}

private fun refreshRealtimeEntitiesFromServer(entities: Set<String>) {
    val cleanEntities = entities.map(::cleanRealtimeEntity).toSet()
    if (cleanEntities.isEmpty() || "all" in cleanEntities) {
        refreshEverythingFromServerAfterRealtimeUpdate()
        return
    }

    val activeStoreId = activeStoreIdState.value

    fun anyEntityMatches(vararg prefixes: String): Boolean = cleanEntities.any { entity ->
        prefixes.any { prefix -> entity == prefix || entity.startsWith("$prefix/") }
    }

    if (anyEntityMatches("config")) getGlobalAppConfiguration(loadAll = false)
    if (anyEntityMatches("user", "auth")) getUser(forceLogOut = false)
    if (anyEntityMatches("stores")) getStores()
    if (anyEntityMatches("suppliers", "suppliergoodsprices")) getSuppliers()
    if (anyEntityMatches("generic")) {
        getGenericGoodsCategories()
        refreshGenericGoodsItems(limit = 200)
    }
    if (anyEntityMatches("notifications")) getNotifications()
    if (anyEntityMatches("support")) {
        getSupportTickets()
        activeSupportTicketIdState.value?.let { getSupportMessages(it, markRead = true) }
    }
    if (anyEntityMatches("security")) {
        getSecuritySessions()
        getSecuritySessionHistory()
    }
    if (anyEntityMatches("finance")) getUserFinanceDashboard()
    if (anyEntityMatches("subscriptions")) {
        getSubscriptionPlans()
        activeStoreId?.let { getStoreSubscription(it) }
    }
    if (anyEntityMatches("workers")) {
        getMyWorkerMemberships()
        getMyWorkerRequests()
        activeStoreId?.let { storeId ->
            getStoreWorkers(storeId)
            getIncomingWorkerRequests(storeId)
        }
    }

    activeStoreId?.let { storeId ->
        if (anyEntityMatches("stock", "stockbatches", "stock/availability", "transactions/cart")) {
            getStock(storeId)
            getStockBatches(storeId)
        }

        if (anyEntityMatches("transactions")) {
            getTransactions(storeId)
            getCashRegister(storeId)
            getDebtors(storeId)
            getStock(storeId)
            getStockBatches(storeId)
        }

        if (anyEntityMatches("debtors")) getDebtors(storeId)
        if (anyEntityMatches("cashregister")) getCashRegister(storeId)
        if (anyEntityMatches("workshifts")) getCurrentWorkshift(storeId)

        if (cleanEntities.any { it !in setOf("notifications", "security", "support") }) {
            getOperationLogs(storeId, OPERATION_LOG_SCOPE_CURRENT)
        }
    }
}

private suspend fun scheduleRealtimeRefresh(
    reason: String? = null,
    entity: String? = null,
    updateId: String? = null,
    force: Boolean = false
) {
    val cleanEntity = cleanRealtimeEntity(entity)
    val cleanReason = cleanRealtimeReason(reason)
    val now = getCurrentTimeMillis()

    var shouldSchedule = false

    realtimeRefreshPlanMutex.withLock {
        if (force || rememberRealtimeUpdateIdLocked(updateId)) {
            val broad = realtimeUpdateRequiresBroadRefresh(cleanEntity, cleanReason)
            val minInterval = if (cleanReason == "connected" || cleanReason == "websocket_connected" || cleanReason == "connection_sync") {
                REALTIME_CONNECTED_REFRESH_MIN_INTERVAL_MILLIS
            } else {
                REALTIME_BROAD_REFRESH_MIN_INTERVAL_MILLIS
            }

            if (!broad || force || now - lastRealtimeBroadRefreshAtMillis >= minInterval) {
                if (broad) {
                    realtimePendingRefreshEntities.clear()
                    realtimePendingRefreshEntities.add("all")
                } else if ("all" !in realtimePendingRefreshEntities) {
                    realtimePendingRefreshEntities.add(cleanEntity)
                }
                shouldSchedule = true
            }
        }

        if (shouldSchedule) {
            realtimeRefreshJob?.cancel()
            realtimeRefreshJob = GlobalScope.launch(Dispatchers.ourIo) {
                delay(if (force) 75L else REALTIME_REFRESH_DEBOUNCE_MILLIS)
                val entitiesToRefresh = realtimeRefreshPlanMutex.withLock {
                    realtimePendingRefreshEntities.toSet().also { realtimePendingRefreshEntities.clear() }
                }
                if (entitiesToRefresh.isEmpty()) return@launch

                realtimeRefreshMutex.withLock {
                    refreshRealtimeEntitiesFromServer(entitiesToRefresh)
                }
            }
        }
    }
}

private suspend fun cloudConnectionProbeRequest(reason: String): ResponseDataModel<Unit> {
    ensureCachedGlobalConfigurationPrimedForNetwork()
    val endpointUrl = globalAppConfigurationState.payloadValue.connectionCheckPath.first
    val configuredServerUrl = globalAppConfigurationState.payloadValue.serverUrl.first
    val resolvedServerUrl = normalizedHttpServerUrlOrNull(configuredServerUrl)
        ?: return ResponseDataModel(
            message = localizedStringResourceMessage(
                id = 1140,
                main = "Can’t reach AITA server. Check Wi‑Fi or server address.",
                ru = "Сервер AITA недоступен. Проверьте Wi‑Fi или адрес сервера.",
                kk = "AITA сервері қолжетімсіз. Wi‑Fi немесе сервер мекенжайын тексеріңіз."
            ),
            payload = null,
            negative = true,
            httpStatusCode = null,
            transportFailure = true
        )

    val requestUrl = networkTargetUrl(resolvedServerUrl, endpointUrl)
    return try {
        logNetworkAttempt("TRY ${HttpMethod.Get.value} $requestUrl probe=$reason")
        val response = httpClient.request(requestUrl) {
            method = HttpMethod.Get
            header(AITA_CONNECTION_PROBE_HEADER, "1")
            header(HttpHeaders.CacheControl, "no-cache")
            header(HttpHeaders.Pragma, "no-cache")
            parameter("silent", "true")
            parameter(reason, "true")
            currentClientDeviceInfoHeaders().forEach { (key, value) ->
                safeHttpHeaderValueOrNull(value)?.let { safeValue -> header(key, safeValue) }
            }
        }
        val rawBody = response.bodyAsText()
        val aitaServerResponse = response.isAitaServerResponse(rawBody)
        logNetworkAttempt("RESULT ${HttpMethod.Get.value} $requestUrl HTTP ${response.status.value} aita=$aitaServerResponse probe=$reason")

        if (!aitaServerResponse) {
            nonAitaHttpResponseDataModel(response.status, rawBody, resolvedServerUrl)
        } else if (response.status.isSuccess()) {
            rememberReachableServerUrl(resolvedServerUrl)
            ResponseDataModel(
                message = null,
                payload = Unit,
                negative = false,
                httpStatusCode = response.status.value,
                transportFailure = false
            )
        } else {
            decodeNetworkResponseDataModel<Unit>(rawBody, response.status)
        }
    } catch (throwable: Throwable) {
        if (throwable is CancellationException) throw throwable
        logNetworkAttempt("FAILED ${HttpMethod.Get.value} $requestUrl probe=$reason ${networkFailureSummary(throwable)}")
        ResponseDataModel(
            message = networkTransportFailureMessage(resolvedServerUrl, endpointUrl, throwable),
            payload = null,
            negative = true,
            httpStatusCode = null,
            transportFailure = true
        )
    }
}

private suspend fun probeCloudServerReachableForRealtimeFallback(): Boolean =
    !cloudConnectionProbeRequest("realtime_fallback").negative

private fun cancelRealtimeUpdatesSocketAfterReachabilityFailure() {
    if (realtimeUpdatesConnectedState.value || realtimeUpdatesJob != null) {
        logCloudConnectionDiagnostic("realtime socket cancelled because health probe/server request says unreachable")
    }
    realtimeUpdatesJob?.cancel()
    realtimeUpdatesJob = null
    realtimeUpdatesConnectedState.value = false
}

fun startCloudConnectionHealthMonitor() {
    if (cloudConnectionHealthMonitorJob?.isActive == true) return

    cloudConnectionHealthMonitorJob = GlobalScope.launch(Dispatchers.ourIo) {
        delay(1_500L)

        while (isActive) {
            val configuredServerUrl = globalAppConfigurationState.payloadValue.serverUrl.first
            val hasConfiguredServerUrl = normalizedHttpServerUrlOrNull(configuredServerUrl) != null

            if (!hasConfiguredServerUrl) {
                delay(CLOUD_CONNECTION_HEALTH_CHECK_UNKNOWN_INTERVAL_MILLIS)
                continue
            }

            val wasRealtimeConnected = realtimeUpdatesConnectedState.value
            val wasTransportMarkedReachable = cloudTransportStatusState.value == CLOUD_TRANSPORT_STATUS_REACHABLE
            val hasLocalAccount = getStoredUserAuthTokens?.invoke() != null
            val probeStartedAt = getCurrentTimeMillis()
            logCloudConnectionDiagnostic(
                "health probe start server=$configuredServerUrl status=${cloudTransportStatusName(cloudTransportStatusState.value)} " +
                        "realtime=$wasRealtimeConnected hasTokens=$hasLocalAccount"
            )
            val response = cloudConnectionHealthProbeMutex.withLock {
                withTimeoutOrNull(CLOUD_CONNECTION_HEALTH_CHECK_TIMEOUT_MILLIS) {
                    cloudConnectionProbeRequest("health")
                }
            }

            val serverAvailable = response != null && !response.negative
            logCloudConnectionDiagnostic(
                "health probe result available=$serverAvailable http=${response?.httpStatusCode ?: -1} " +
                        "negative=${response?.negative} transportFailure=${response?.transportFailure} " +
                        "elapsed=${getCurrentTimeMillis() - probeStartedAt}ms"
            )

            if (serverAvailable) {
                realtimeOfflineNoticePosted = false
                markCloudTransportReachableForNotifications(
                    authenticated = getStoredUserAuthTokens?.invoke() != null,
                    authRefreshRequired = false
                )

                if (getStoredUserAuthTokens?.invoke() != null) {
                    if (realtimeUpdatesJob?.isActive != true) {
                        startRealtimeUpdates()
                    }
                    syncPendingSessionCleanupsToServerNow()
                    syncPendingNotificationsToServerNow()
                    syncLocalNetworkOperationsToCloudNow()
                }
            } else {
                val confirmedUnavailable = markCloudTransportUnavailableForNotifications()

                if (confirmedUnavailable) {
                    cancelRealtimeUpdatesSocketAfterReachabilityFailure()

                    val shouldPostOfflinePopup = hasLocalAccount && (
                            wasRealtimeConnected ||
                            wasTransportMarkedReachable ||
                            !realtimeOfflineNoticePosted
                    )

                    if (shouldPostOfflinePopup) {
                        realtimeOfflineNoticePosted = true
                        postInAppNotificationNow(
                            response?.message ?: realtimeDisconnectedMessage(),
                            NotificationType.Neutral,
                            transient = true
                        )
                    }
                }
            }

            val delayMillis = when (cloudTransportStatusState.value) {
                CLOUD_TRANSPORT_STATUS_REACHABLE -> CLOUD_CONNECTION_HEALTH_CHECK_REACHABLE_INTERVAL_MILLIS
                CLOUD_TRANSPORT_STATUS_AUTH_REFRESH_REQUIRED -> CLOUD_CONNECTION_HEALTH_CHECK_UNAVAILABLE_INTERVAL_MILLIS
                CLOUD_TRANSPORT_STATUS_UNAVAILABLE -> CLOUD_CONNECTION_HEALTH_CHECK_UNAVAILABLE_INTERVAL_MILLIS
                else -> CLOUD_CONNECTION_HEALTH_CHECK_UNKNOWN_INTERVAL_MILLIS
            }
            delay(delayMillis)
        }
    }
}

fun stopRealtimeUpdates() {
    realtimeUpdatesJob?.cancel()
    realtimeUpdatesJob = null
    realtimeOfflineNoticePosted = false
    realtimeUpdatesConnectedState.value = false
}

fun restartRealtimeUpdates() {
    stopRealtimeUpdates()
    startRealtimeUpdates()
}

private suspend fun currentRealtimeAccessTokenOrNull(): String? {
    val current = getStoredUserAuthTokens?.invoke() ?: return null
    if (current.accessTokenNeedsRefreshForNetwork()) {
        refreshStoredAuthTokensOnceForNetworkRetry(postNotification = false)
    }

    val latest = getStoredUserAuthTokens?.invoke() ?: return null
    if (!latest.accessTokenIsStillUsableForNetwork()) return null

    return latest.accessToken.takeIf { it.isNotBlank() }
}

fun startRealtimeUpdates() {
    startCloudConnectionHealthMonitor()

    if (realtimeUpdatesJob?.isActive == true) return

    realtimeUpdatesJob = GlobalScope.launch(Dispatchers.ourIo) {
        var reconnectDelayMillis = 1_000L

        while (isActive) {
            var accessToken = currentRealtimeAccessTokenOrNull()

            if (accessToken.isNullOrBlank()) {
                realtimeUpdatesConnectedState.emit(false)
                delay(if (cloudTransportStatusState.value == CLOUD_TRANSPORT_STATUS_AUTH_REFRESH_REQUIRED) 30_000L else 2_000L)
                continue
            }

            val wasConnected = realtimeUpdatesConnectedState.value
            var openedRealtimeSession = false
            var openedRealtimeSessionAtMillis = 0L
            val serverUrlCandidates = resolvedServerUrlCandidates(null)

            candidateLoop@ for (realtimeBaseUrl in serverUrlCandidates) {
                var retriedAfterTokenRefresh = false

                while (isActive) {
                    val realtimeUrl = realtimeBaseUrl.toRealtimeWebSocketUrl(
                        globalAppConfigurationState.payloadValue.realtimeUpdatesPath
                    )

                    try {
                        val session = httpClient.webSocketSession {
                            url(realtimeUrl)
                            header(HttpHeaders.Authorization, "Bearer $accessToken")
                            timeout {
                                connectTimeoutMillis = 4_000L
                                requestTimeoutMillis = Long.MAX_VALUE
                                socketTimeoutMillis = Long.MAX_VALUE
                            }
                        }

                        openedRealtimeSession = true
                        openedRealtimeSessionAtMillis = getCurrentTimeMillis()

                        try {
                            rememberReachableServerUrl(realtimeBaseUrl)
                            val becameConnected = !realtimeUpdatesConnectedState.value
                            val recoveredFromOffline = realtimeOfflineNoticePosted
                            markCloudTransportReachableForNotifications(authenticated = true)
                            realtimeUpdatesConnectedState.emit(true)
                            realtimeOfflineNoticePosted = false
                            reconnectDelayMillis = 1_000L

                            if (becameConnected || recoveredFromOffline) {
                                postInAppNotification(realtimeConnectedMessage(), NotificationType.Positive)
                                GlobalScope.launch(Dispatchers.ourIo) {
                                    syncPendingSessionCleanupsToServerNow()
                                    syncPendingNotificationsToServerNow()
                                    syncLocalNetworkOperationsToCloudNow()
                                    scheduleRealtimeRefresh(reason = "connected", entity = "all")
                                }
                            }

                            session.outgoing.send(
                                Frame.Text(
                                    jsonBase.encodeToString(
                                        RealtimeClientHelloDataModel(
                                            activeStoreId = activeStoreIdState.value,
                                            language = appLanguageState.value,
                                            platform = getPlatformName(),
                                            clientTimeMillis = getCurrentTimeMillis()
                                        )
                                    )
                                )
                            )

                            for (frame in session.incoming) {
                                val text = (frame as? Frame.Text)?.readText() ?: continue
                                val update = runCatching { jsonBase.decodeFromString<RealtimeUpdateDataModel>(text) }.getOrNull()
                                if (update != null && update.type != "connected") {
                                    scheduleRealtimeRefresh(
                                        reason = update.reason ?: update.entity,
                                        entity = update.entity,
                                        updateId = update.id
                                    )
                                }
                            }
                        } finally {
                            try {
                                session.close()
                            } catch (_: Throwable) {
                            }
                        }

                        break@candidateLoop
                    } catch (throwable: Throwable) {
                        if (throwable is CancellationException) throw throwable

                        if (!retriedAfterTokenRefresh && getStoredUserAuthTokens?.invoke() != null) {
                            retriedAfterTokenRefresh = true
                            if (refreshStoredAuthTokensOnceForNetworkRetry()) {
                                accessToken = getStoredUserAuthTokens?.invoke()?.accessToken
                                if (!accessToken.isNullOrBlank()) {
                                    continue
                                }
                            } else if (cloudTransportStatusState.value == CLOUD_TRANSPORT_STATUS_AUTH_REFRESH_REQUIRED) {
                                accessToken = null
                                break@candidateLoop
                            }
                        }

                        break
                    }
                }
            }

            if (isActive) {
                realtimeUpdatesConnectedState.emit(false)

                if (openedRealtimeSession) {
                    // The HTTP server may still be reachable while only the WebSocket dropped.
                    // Do not run the expensive REST fallback probe every time a socket closes; just reconnect
                    // with a small backoff. Otherwise a short WebSocket timeout can look like an endless data-pull loop.
                    val livedMillis = (getCurrentTimeMillis() - openedRealtimeSessionAtMillis).coerceAtLeast(0L)
                    val delayMillis = if (livedMillis < REALTIME_AFTER_WEBSOCKET_CLOSE_MIN_DELAY_MILLIS) {
                        REALTIME_AFTER_WEBSOCKET_CLOSE_MIN_DELAY_MILLIS
                    } else {
                        reconnectDelayMillis.coerceAtLeast(REALTIME_AFTER_WEBSOCKET_CLOSE_MIN_DELAY_MILLIS)
                    }
                    delay(delayMillis)
                    reconnectDelayMillis = (reconnectDelayMillis * 2).coerceAtMost(30_000L)
                    continue
                }

                val serverReachable = probeCloudServerReachableForRealtimeFallback()

                if (!serverReachable) {
                    val confirmedUnavailable = markCloudTransportUnavailableForNotifications()
                    val shouldPostOfflineNotice = confirmedUnavailable &&
                            (!realtimeOfflineNoticePosted || wasConnected || realtimeUpdatesConnectedState.value)
                    if (shouldPostOfflineNotice) {
                        // The persistent top connection banner represents server outage. Do not create
                        // repeating notification history entries while the same outage continues.
                        realtimeOfflineNoticePosted = true
                        postInAppNotification(realtimeDisconnectedMessage(), NotificationType.Neutral, transient = true)
                    }
                } else {
                    realtimeOfflineNoticePosted = false
                }

                delay(if (serverReachable) reconnectDelayMillis.coerceAtLeast(5_000L) else reconnectDelayMillis)
                reconnectDelayMillis = (reconnectDelayMillis * 2).coerceAtMost(30_000L)
            }
        }
    }
}

fun refreshCloudConnectionManually() {
    if (manualCloudConnectionRefreshMutex.isLocked) return

    GlobalScope.launch(Dispatchers.ourIo) {
        manualCloudConnectionRefreshMutex.withLock {
            cloudConnectionManualRefreshInProgressState.emit(true)

            try {
                logCloudConnectionDiagnostic("manual refresh start server=${globalAppConfigurationState.payloadValue.serverUrl.first}")
                val response = cloudConnectionProbeRequest("manual")
                logCloudConnectionDiagnostic(
                    "manual refresh ping result negative=${response.negative} http=${response.httpStatusCode} transportFailure=${response.transportFailure}"
                )

                if (response.negative) {
                    cancelRealtimeUpdatesSocketAfterReachabilityFailure()
                    markCloudTransportUnavailableForNotifications()
                    postInAppNotification(
                        response.message ?: localizedStringResourceMessage(
                            id = 1140,
                            main = "Can’t reach AITA server. Check Wi‑Fi or server address.",
                            ru = "Сервер AITA недоступен. Проверьте Wi‑Fi или адрес сервера.",
                            kk = "AITA сервері қолжетімсіз. Wi‑Fi немесе сервер мекенжайын тексеріңіз."
                        ),
                        NotificationType.Negative,
                        transient = true
                    )
                    return@withLock
                }

                val storedTokens = getStoredUserAuthTokens?.invoke()
                val hasLocalAccount = storedTokens != null

                var authenticatedReady = hasLocalAccount
                if (storedTokens != null) {
                    val needsRefresh = storedTokens.accessTokenNeedsRefreshForNetwork()
                    authenticatedReady = if (needsRefresh) {
                        refreshStoredAuthTokensOnceForNetworkRetry(postNotification = false)
                    } else {
                        storedTokens.accessTokenIsStillUsableForNetwork()
                    }

                    if (!authenticatedReady) {
                        val refreshFailureMessage = recentAuthRefreshNonAuthFailureMessage()
                        when {
                            cloudTransportStatusState.value == CLOUD_TRANSPORT_STATUS_UNAVAILABLE -> {
                                postInAppNotification(
                                    refreshFailureMessage ?: localizedStringResourceMessage(
                                        id = 1140,
                                        main = "Can’t reach AITA server. Check Wi‑Fi or server address.",
                                        ru = "Сервер AITA недоступен. Проверьте Wi‑Fi или адрес сервера.",
                                        kk = "AITA сервері қолжетімсіз. Wi‑Fi немесе сервер мекенжайын тексеріңіз."
                                    ),
                                    NotificationType.Negative,
                                    transient = true
                                )
                            }

                            cloudTransportStatusState.value == CLOUD_TRANSPORT_STATUS_AUTH_REFRESH_REQUIRED && refreshFailureMessage == null -> {
                                postInAppNotification(
                                    localizedStringResourceMessage(
                                        id = 91,
                                        main = "Cloud session needs refresh. You remain signed in locally.",
                                        ru = "Облачный сеанс нужно обновить. Вы остаётесь в аккаунте локально.",
                                        kk = "Бұлттық сеансты жаңарту қажет. Сіз жергілікті түрде аккаунтта қаласыз."
                                    ),
                                    NotificationType.Neutral
                                )
                            }

                            else -> {
                                postInAppNotification(
                                    refreshFailureMessage ?: localizedStringResourceMessage(
                                        id = 1149,
                                        main = "Server could not refresh session. Keeping local login active.",
                                        ru = "Сервер не смог обновить сеанс. Локальный вход сохранён.",
                                        kk = "Сервер сеансты жаңарта алмады. Жергілікті кіру сақталды."
                                    ),
                                    NotificationType.Negative,
                                    transient = false
                                )
                            }
                        }
                        return@withLock
                    }
                }

                markCloudTransportReachableForNotifications(authenticated = authenticatedReady, authRefreshRequired = false)
                syncPendingSessionCleanupsToServerNow()

                if (hasLocalAccount) {
                    getUser(forceLogOut = false, applyServerActiveStore = false)
                    syncPendingNotificationsToServerNow()
                    syncLocalNetworkOperationsToCloudNow()
                    scheduleRealtimeRefresh(reason = "manual_reconnect", entity = "all", force = true)
                    restartRealtimeUpdates()
                }

                postInAppNotification(
                    response.message ?: localizedStringResourceMessage(
                        id = 1138,
                        main = "Server connection available",
                        ru = "Сервер доступен",
                        kk = "Сервер қолжетімді"
                    ),
                    NotificationType.Positive
                )
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                logCloudConnectionDiagnostic("manual refresh failed ${throwable.message ?: throwable.toString()}")
                cancelRealtimeUpdatesSocketAfterReachabilityFailure()
                markCloudTransportUnavailableForNotifications()
                postInAppNotification(
                    localizedStringResourceMessage(
                        id = 1140,
                        main = "Can’t reach AITA server. Check Wi‑Fi or server address.",
                        ru = "Сервер AITA недоступен. Проверьте Wi‑Fi или адрес сервера.",
                        kk = "AITA сервері қолжетімсіз. Wi‑Fi немесе сервер мекенжайын тексеріңіз."
                    ),
                    NotificationType.Negative,
                    transient = true
                )
            } finally {
                logCloudConnectionDiagnostic("manual refresh finish status=${cloudTransportStatusName(cloudTransportStatusState.value)} realtime=${realtimeUpdatesConnectedState.value}")
                cloudConnectionManualRefreshInProgressState.emit(false)
            }
        }
    }
}

fun upsertCart(
    id: String,
    transactionTypeIndex: Int,
    clientId: Int,
    quantity: QuantityDataModel
) {
    GlobalScope.launch {
        appDatabase.app_databaseQueries.upsertCart(
            id,
            transactionTypeIndex.toLong(),
            clientId.toLong(),
            jsonBase.encodeToString(quantity)
        )
    }
}

suspend fun deleteCart(transactionTypeIndex: Int, clientId: Int) {
    appDatabase.app_databaseQueries.deleteCart(transactionTypeIndex.toLong(), clientId.toLong())
    removeCartSaleMethodIds(transactionTypeIndex, clientId)
    removeCartReturnReasons(transactionTypeIndex, clientId)
    removeCartConditionChecks(transactionTypeIndex, clientId)
    clearTransactionCartScrollState(transactionTypeIndex, clientId)
    clearTransactionPaymentDraft(transactionTypeIndex, clientId)
    if (transactionTypeIndex == 2) {
        clearTransactionSupplySupplierId(transactionTypeIndex, clientId)
    }
}

fun deleteCartById(id: String, transactionTypeIndex: Int, clientId: Int) {
    GlobalScope.launch {
        appDatabase.app_databaseQueries.deleteCartById(id, transactionTypeIndex.toLong(), clientId.toLong())
        removeCartSaleMethodId(transactionTypeIndex, clientId, id)
        removeCartReturnReason(transactionTypeIndex, clientId, id)
        removeCartConditionChecks(transactionTypeIndex, clientId, id)
    }
}

suspend fun deleteCartItemById(id: String) {
    appDatabase.app_databaseQueries.deleteById(id)
    removeCartReturnReasonsByGoodsItemId(id)
}

fun observeCart(transactionTypeIndex: Int, clientId: Int): Flow<List<GoodsItemInCartDataModel>?> =
    appDatabase.app_databaseQueries.getCart(transactionTypeIndex.toLong(), clientId.toLong())
        .asFlow()
        .mapToList(Dispatchers.ourIo)
        .map { rows ->
            rows.map { row ->
                GoodsItemInCartDataModel(
                    id = row.id,
                    transactionTypeIndex = row.transactionTypeIndex.toInt(),
                    clientId = row.clientId.toInt(),
                    quantity = jsonBase.decodeFromString(row.quantity),
                    timeAdded = row.timeAdded
                )
            }
        }

private suspend fun markObservedCartHydrated(transactionTypeIndex: Int, clientId: Int) {
    observedCartHydrationMutex.withLock {
        observedCartHydrationKeys += transactionKey(transactionTypeIndex, clientId)
        if (observedCartHydrationKeys.size >= 15 && !cartPersistenceHydratedState.value) {
            cartPersistenceHydratedState.emit(true)
        }
    }
}

private suspend fun emitObservedCartPreservingUntilStockLoaded(
    targetState: MutableStateFlow<List<GoodsItemInCartDataModel>>,
    observedCart: List<GoodsItemInCartDataModel>?,
    transactionTypeIndex: Int,
    clientId: Int
) {
    val cart = observedCart.orEmpty()
    val loadedStock = stockState.payloadValue

    if (loadedStock == null) {
        targetState.emit(cart)
        markObservedCartHydrated(transactionTypeIndex, clientId)
        return
    }

    val stockIds = loadedStock.map { it.id }.toSet()
    val filtered = cart.filter { item ->
        val stillExists = item.id in stockIds
        if (!stillExists) deleteCartItemById(item.id)
        stillExists
    }

    targetState.emit(filtered)
    markObservedCartHydrated(transactionTypeIndex, clientId)
}

private const val IN_APP_NOTIFICATION_DEDUPE_WINDOW_MILLIS = 10_000L
private const val IN_APP_NOTIFICATION_HISTORY_DEDUPE_WINDOW_MILLIS = 60_000L
private const val IN_APP_NOTIFICATION_ID_BUCKET_MILLIS = 10_000L
private const val IN_APP_NOTIFICATION_DISMISS_SUPPRESSION_MILLIS = 120_000L
private const val ACTIVE_IN_APP_NOTIFICATION_LIMIT = 5
private const val LOCAL_NOTIFICATION_HISTORY_LIMIT = 200
private const val NOTIFICATION_CONNECTION_CATEGORY = "connection"
private const val NOTIFICATION_SESSION_CATEGORY = "session"

private val notificationPopupRemovalTokens = mutableMapOf<String, String>()
private val notificationPopupTransientById = mutableMapOf<String, Boolean>()
private val dismissedNotificationPopupKeysUntil = mutableMapOf<String, Long>()

@Volatile
private var cloudTransportReachableForNotifications = true
@Volatile
private var cloudTransportFailureNotificationPending = false
@Volatile
private var cloudTransportFailureNoticePostedForCurrentOutage = false
@Volatile
private var cloudTransportRecoveryNotificationPending = false
@Volatile
private var cloudSessionRefreshRequiredForNotifications = false
@Volatile
private var cloudSessionRefreshNotificationPostedForCurrentRequirement = false

private const val CLOUD_TRANSPORT_FAILURE_CONFIRMATION_MIN_SIGNALS = 2
private const val CLOUD_TRANSPORT_FAILURE_CONFIRMATION_WINDOW_MILLIS = 7_000L
private const val CLOUD_TRANSPORT_FAILURE_SIGNAL_RESET_MILLIS = 20_000L

@Volatile
private var cloudTransportFailureSignalCount = 0
@Volatile
private var cloudTransportFirstFailureSignalAtMillis = 0L

private fun String.normalizedNotificationText(): String =
    trim()
        .lowercase()
        .replace(Regex("\\s+"), " ")

private fun String.isCloudSessionRefreshNotificationText(): Boolean {
    val normalized = normalizedNotificationText()
    if (normalized.isBlank()) return false

    return listOf(
        "cloud session needs refresh",
        "session needs refresh",
        "you remain signed in locally",
        "облачный сеанс",
        "сеанс нужно обновить",
        "остаётесь в аккаунте локально",
        "бұлттық сеанс",
        "сеансты жаңарту",
        "жергілікті түрде аккаунтта",
    ).any { marker -> normalized.contains(marker) }
}

private fun shouldPostCloudSessionRefreshNotificationNow(): Boolean {
    if (cloudSessionRefreshNotificationPostedForCurrentRequirement) return false
    cloudSessionRefreshNotificationPostedForCurrentRequirement = true
    return true
}

private fun String.isCloudTransportFailureNotificationText(): Boolean {
    val normalized = normalizedNotificationText()
    if (normalized.isBlank()) return false

    return listOf(
        "can't reach aita server",
        "can’t reach aita server",
        "cannot reach server",
        "cannot connect to server",
        "server unavailable",
        "server is unavailable",
        "server is offline",
        "server is not connected",
        "live updates disconnected",
        "connection unavailable",
        "using cached data while reconnecting",
        "keeping you signed in offline",
        "security sessions will refresh",
        "server address opened another page",
        "server address did not answer as aita",
        "not the aita server",
        "does not look like aita",
        "tried http://",
        "tried https://",
        "client error:",
        "connect timeout",
        "connection timeout",
        "connect_timeout",
        "connecttimeoutexception",
        "sockettimeoutexception",
        "timeout has expired",
        "connection refused",
        "network unreachable",
        "host unreachable",
        "failed to connect",
        "no route to host",
        "unknownhostexception",
        "unresolvedaddress",
        "socketexception",
        "url=http://",
        "url=https://",
        "[url=",
        "io.ktor.client.network.sockets",
        "сервер aita недоступ",
        "сервер недоступ",
        "сервер офлайн",
        "сервер не подключ",
        "нет соединения",
        "нет ответа от сервера",
        "адрес сервера открыл другую страницу",
        "адрес сервера ответил не как aita",
        "пробовали http://",
        "пробовали https://",
        "таймаут подключения",
        "ошибка подключения",
        "остаётесь в аккаунте офлайн",
        "сеансы безопасности обновятся",
        "aita сервері қолжетімсіз",
        "сервер қолжетімсіз",
        "сервер офлайн",
        "сервер қосылмаған",
        "қосылу уақыты",
        "қосылым қатесі"
    ).any { marker -> normalized.contains(marker) }
}

private fun String.isCloudTransportRecoveryNotificationText(): Boolean {
    val normalized = normalizedNotificationText()
    if (normalized.isBlank()) return false

    return listOf(
        "server is back online",
        "server back online",
        "live updates connected",
        "cloud connection restored",
        "server connection restored",
        "server connection available",
        "server connected",
        "онлайн-обновления подключены",
        "соединение восстановлено",
        "связь с сервером восстановлена",
        "сервер доступен",
        "сервер подключ",
        "сервер снова онлайн",
        "нақты уақыттағы жаңартулар қосылды",
        "сервермен байланыс қалпына",
        "сервер қайта онлайн",
        "сервер қосылды",
        "сервер қолжетімді"
    ).any { marker -> normalized.contains(marker) }
}

private fun localizedCloudTransportFailureNotificationText(): String = localizedStringResourceText(
    id = 1140,
    main = "Can’t reach AITA server. Check Wi‑Fi or server address.",
    ru = "Сервер AITA недоступен. Проверьте Wi‑Fi или адрес сервера.",
    kk = "AITA сервері қолжетімсіз. Wi‑Fi немесе сервер мекенжайын тексеріңіз."
)

private fun localizedCloudTransportRecoveryNotificationText(): String = localizedStringResourceText(
    id = 1138,
    main = "Server connected.",
    ru = "Сервер подключён.",
    kk = "Сервер қосылды."
)

private fun localizedCloudSessionRefreshNotificationText(): String = localizedStringResourceText(
    id = 91,
    main = "Cloud session needs refresh. You remain signed in locally.",
    ru = "Облачный сеанс нужно обновить. Вы остаётесь в аккаунте локально.",
    kk = "Бұлттық сеансты жаңарту қажет. Сіз жергілікті түрде аккаунтта қаласыз."
)

private fun String.humanFriendlyNotificationMessage(): String {
    val clean = trim().replace(Regex("\\s+"), " ")
    if (clean.isBlank()) return clean

    return when {
        clean.isCloudTransportFailureNotificationText() -> localizedCloudTransportFailureNotificationText()
        clean.isCloudTransportRecoveryNotificationText() -> localizedCloudTransportRecoveryNotificationText()
        clean.isCloudSessionRefreshNotificationText() -> localizedCloudSessionRefreshNotificationText()
        else -> clean
    }
}

private fun String.notificationCanonicalText(): String {
    val normalized = normalizedNotificationText()
    if (normalized.isBlank()) return ""

    return when {
        isCloudTransportFailureNotificationText() -> "cloud-transport-unavailable"
        isCloudTransportRecoveryNotificationText() -> "cloud-transport-recovered"
        isCloudSessionRefreshNotificationText() -> "cloud-session-refresh-required"
        else -> normalized
    }
}

private fun NotificationDataModel.notificationStatusCombinedText(): String =
    listOf(title, message, category, source).joinToString(" ")

private fun NotificationDataModel.isConnectionStatusNotification(): Boolean {
    val normalizedCategory = category.normalizedNotificationText()
    val combined = notificationStatusCombinedText()
    return normalizedCategory == NOTIFICATION_CONNECTION_CATEGORY ||
            combined.isCloudTransportFailureNotificationText() ||
            combined.isCloudTransportRecoveryNotificationText()
}

private fun NotificationDataModel.isSessionStatusNotification(): Boolean {
    val normalizedCategory = category.normalizedNotificationText()
    val combined = notificationStatusCombinedText()
    return normalizedCategory == NOTIFICATION_SESSION_CATEGORY || combined.isCloudSessionRefreshNotificationText()
}

private fun NotificationDataModel.isLocalOnlyNotification(): Boolean = isConnectionStatusNotification() || isSessionStatusNotification()

private fun NotificationDataModel.withHumanFriendlyNotificationText(): NotificationDataModel {
    val combined = notificationStatusCombinedText()
    val cleanMessage = message.humanFriendlyNotificationMessage()
    val cleanTitle = if (
        title.isCloudTransportFailureNotificationText() ||
        title.isCloudTransportRecoveryNotificationText() ||
        title.isCloudSessionRefreshNotificationText()
    ) "" else title.trim()
    val cleanCategory = when {
        combined.isCloudTransportFailureNotificationText() -> NOTIFICATION_CONNECTION_CATEGORY
        combined.isCloudTransportRecoveryNotificationText() -> NOTIFICATION_CONNECTION_CATEGORY
        combined.isCloudSessionRefreshNotificationText() -> NOTIFICATION_SESSION_CATEGORY
        else -> category
    }

    return if (cleanMessage == message && cleanTitle == title && cleanCategory == category) this else copy(
        title = cleanTitle,
        message = cleanMessage,
        category = cleanCategory
    )
}

private fun cloudSessionRefreshIsActiveForNotifications(): Boolean {
    return cloudSessionRefreshRequiredForNotifications && getStoredUserAuthTokens?.invoke() != null
}

@PublishedApi
internal fun cloudTransportStatusName(status: Int): String = when (status) {
    CLOUD_TRANSPORT_STATUS_REACHABLE -> "reachable"
    CLOUD_TRANSPORT_STATUS_AUTH_REFRESH_REQUIRED -> "auth_refresh_required"
    CLOUD_TRANSPORT_STATUS_UNAVAILABLE -> "unavailable"
    else -> "unknown"
}

@PublishedApi
internal fun logCloudConnectionDiagnostic(message: String) {
    println("AITA connection: $message")
}

private fun setCloudTransportStatusForDiagnostics(nextStatus: Int, reason: String) {
    val previousStatus = cloudTransportStatusState.value
    if (previousStatus != nextStatus) {
        logCloudConnectionDiagnostic(
            "status ${cloudTransportStatusName(previousStatus)} -> ${cloudTransportStatusName(nextStatus)} " +
                    "reason=$reason realtime=${realtimeUpdatesConnectedState.value} " +
                    "hasTokens=${getStoredUserAuthTokens?.invoke() != null} activeStore=${activeStoreIdState.value.orEmpty()}"
        )
    }
    cloudTransportStatusState.value = nextStatus
}

private fun recentCloudTransportFailureIsDominant(now: Long = getCurrentTimeMillis()): Boolean {
    val lastUnavailable = cloudTransportLastUnavailableAtMillis
    val lastReachable = cloudTransportLastReachableAtMillis
    return cloudTransportStatusState.value == CLOUD_TRANSPORT_STATUS_UNAVAILABLE &&
            lastUnavailable > 0L &&
            lastUnavailable >= lastReachable &&
            now - lastUnavailable <= CLOUD_CONNECTION_AUTH_REFRESH_SUPPRESSION_AFTER_TRANSPORT_FAILURE_MILLIS
}

private fun clearCloudTransportFailureSignalsForNotifications() {
    cloudTransportFailureSignalCount = 0
    cloudTransportFirstFailureSignalAtMillis = 0L
}

private fun recordCloudTransportFailureSignalForNotifications(reason: String = "transport_failure"): Boolean {
    val now = getCurrentTimeMillis()
    val firstSignalAt = cloudTransportFirstFailureSignalAtMillis

    if (firstSignalAt <= 0L || now - firstSignalAt > CLOUD_TRANSPORT_FAILURE_SIGNAL_RESET_MILLIS) {
        cloudTransportFirstFailureSignalAtMillis = now
        cloudTransportFailureSignalCount = 1
    } else {
        cloudTransportFailureSignalCount = (cloudTransportFailureSignalCount + 1).coerceAtMost(1000)
    }

    val elapsed = now - cloudTransportFirstFailureSignalAtMillis
    val confirmed = cloudTransportFailureSignalCount >= CLOUD_TRANSPORT_FAILURE_CONFIRMATION_MIN_SIGNALS ||
            elapsed >= CLOUD_TRANSPORT_FAILURE_CONFIRMATION_WINDOW_MILLIS

    if (!confirmed) {
        logCloudConnectionDiagnostic(
            "transport failure signal held for confirmation reason=$reason " +
                    "signals=$cloudTransportFailureSignalCount elapsed=${elapsed}ms"
        )
    }

    return confirmed
}

@PublishedApi
internal fun markCloudTransportUnavailableForNotifications(): Boolean {
    val now = getCurrentTimeMillis()
    cloudTransportLastUnavailableAtMillis = now

    if (!recordCloudTransportFailureSignalForNotifications()) {
        return false
    }

    val nextStatus = CLOUD_TRANSPORT_STATUS_UNAVAILABLE

    // A real transport failure is stronger evidence than an old or speculative auth-refresh state.
    // When the device cannot reach the server, the user needs a connection banner, not a session banner.
    cloudSessionRefreshRequiredForNotifications = false
    cloudSessionRefreshNotificationPostedForCurrentRequirement = false

    if (cloudTransportReachableForNotifications || cloudTransportStatusState.value != nextStatus) {
        cloudTransportFailureNotificationPending = true
        cloudTransportFailureNoticePostedForCurrentOutage = false
    }

    setCloudTransportStatusForDiagnostics(nextStatus, "transport_failure_confirmed")
    cloudTransportReachableForNotifications = false
    cloudTransportRecoveryNotificationPending = false
    return true
}

@PublishedApi
internal fun markCloudTransportReachableForNotifications(
    authenticated: Boolean = false,
    authRefreshRequired: Boolean? = null
) {
    clearCloudTransportFailureSignalsForNotifications()
    cloudTransportLastReachableAtMillis = getCurrentTimeMillis()
    val wasUnavailable = !cloudTransportReachableForNotifications ||
            cloudTransportStatusState.value == CLOUD_TRANSPORT_STATUS_UNAVAILABLE
    val hadVisibleOutage = cloudTransportFailureNoticePostedForCurrentOutage
    val hasStoredTokens = getStoredUserAuthTokens?.invoke() != null
    when {
        !hasStoredTokens -> cloudSessionRefreshRequiredForNotifications = false
        authenticated -> cloudSessionRefreshRequiredForNotifications = false
        authRefreshRequired == true -> cloudSessionRefreshRequiredForNotifications = true
        authRefreshRequired == false -> cloudSessionRefreshRequiredForNotifications = false
    }

    val nextStatus = if (cloudSessionRefreshRequiredForNotifications && hasStoredTokens) {
        CLOUD_TRANSPORT_STATUS_AUTH_REFRESH_REQUIRED
    } else {
        CLOUD_TRANSPORT_STATUS_REACHABLE
    }

    setCloudTransportStatusForDiagnostics(
        nextStatus,
        "transport_reachable authenticated=$authenticated authRefreshRequired=$authRefreshRequired"
    )
    cloudTransportReachableForNotifications = true
    cloudTransportFailureNotificationPending = false
    cloudTransportFailureNoticePostedForCurrentOutage = false
    cloudTransportRecoveryNotificationPending = wasUnavailable && hadVisibleOutage && nextStatus == CLOUD_TRANSPORT_STATUS_REACHABLE
}

@PublishedApi
internal fun markCloudSessionNeedsRefreshForNotifications() {
    if (recentCloudTransportFailureIsDominant()) {
        logCloudConnectionDiagnostic("session-refresh signal suppressed because transport is currently unavailable")
        setCloudTransportStatusForDiagnostics(CLOUD_TRANSPORT_STATUS_UNAVAILABLE, "auth_refresh_suppressed_by_transport_failure")
        return
    }

    cloudSessionRefreshRequiredForNotifications = true
    cloudTransportRecoveryNotificationPending = false
    setCloudTransportStatusForDiagnostics(CLOUD_TRANSPORT_STATUS_AUTH_REFRESH_REQUIRED, "auth_refresh_required")
}

@PublishedApi
internal fun clearCloudSessionRefreshRequirementForNotifications(statusAfterClear: Int = CLOUD_TRANSPORT_STATUS_UNKNOWN) {
    cloudSessionRefreshRequiredForNotifications = false
    cloudSessionRefreshNotificationPostedForCurrentRequirement = false
    if (cloudTransportStatusState.value == CLOUD_TRANSPORT_STATUS_AUTH_REFRESH_REQUIRED) {
        setCloudTransportStatusForDiagnostics(statusAfterClear, "auth_refresh_requirement_cleared")
    }
}

private fun shouldPostNotificationConsideringCloudTransport(
    notification: NotificationDataModel
): Boolean {
    val text = notification.notificationStatusCombinedText()

    if (text.isCloudSessionRefreshNotificationText()) {
        if (!cloudSessionRefreshIsActiveForNotifications()) return false
        if (cloudTransportStatusState.value == CLOUD_TRANSPORT_STATUS_UNAVAILABLE) return false
        return shouldPostCloudSessionRefreshNotificationNow()
    }

    if (text.isCloudTransportRecoveryNotificationText()) {
        val shouldPost = cloudTransportRecoveryNotificationPending
        cloudTransportRecoveryNotificationPending = false
        markCloudTransportReachableForNotifications(authRefreshRequired = null)
        return shouldPost
    }

    if (!text.isCloudTransportFailureNotificationText()) return true

    if (!markCloudTransportUnavailableForNotifications()) return false
    if (cloudTransportFailureNotificationPending && !cloudTransportFailureNoticePostedForCurrentOutage) {
        cloudTransportFailureNotificationPending = false
        cloudTransportFailureNoticePostedForCurrentOutage = true
        cloudTransportRecoveryNotificationPending = false
        return true
    }

    return false
}

private fun createNotificationDataModel(
    message: String,
    type: NotificationType,
    title: String = "",
    category: String = when (type) {
        NotificationType.Positive -> "positive"
        NotificationType.Negative -> "negative"
        NotificationType.Neutral -> "neutral"
    }
): NotificationDataModel {
    val combined = listOf(title, message, category).joinToString(" ")
    val cleanMessage = message.humanFriendlyNotificationMessage()
    val cleanTitle = if (
        title.isCloudTransportFailureNotificationText() ||
        title.isCloudTransportRecoveryNotificationText() ||
        title.isCloudSessionRefreshNotificationText()
    ) "" else title.trim()
    val cleanCategory = when {
        combined.isCloudTransportFailureNotificationText() -> NOTIFICATION_CONNECTION_CATEGORY
        combined.isCloudTransportRecoveryNotificationText() -> NOTIFICATION_CONNECTION_CATEGORY
        combined.isCloudSessionRefreshNotificationText() -> NOTIFICATION_SESSION_CATEGORY
        else -> category
    }
    val now = getCurrentTimeMillis()
    val storeId = activeStoreIdState.value
    val bucket = now / IN_APP_NOTIFICATION_ID_BUCKET_MILLIS
    val dedupeStoreId = if (cleanCategory == NOTIFICATION_CONNECTION_CATEGORY || cleanCategory == NOTIFICATION_SESSION_CATEGORY) "" else storeId.orEmpty()
    val stableKey = listOf(
        userAccountState.payloadValue?.id.orEmpty(),
        dedupeStoreId,
        cleanCategory,
        cleanTitle.notificationCanonicalText(),
        cleanMessage.notificationCanonicalText()
    ).joinToString("|")

    return NotificationDataModel(
        id = "${bucket}_${stableKey.hashCode()}_${now}_${kotlin.random.Random.nextInt(0, Int.MAX_VALUE)}_${type.name}",
        userId = userAccountState.payloadValue?.id,
        storeId = storeId,
        title = cleanTitle,
        message = cleanMessage,
        type = type,
        category = cleanCategory,
        source = "app",
        metadata = emptyMap(),
        createdAtMillis = now,
        shownAtMillis = now,
        readAtMillis = null,
        isSavedOnServer = false
    )
}

private fun NotificationDataModel.dedupeKey(): String = when {
    isConnectionStatusNotification() -> listOf(
        NOTIFICATION_CONNECTION_CATEGORY,
        notificationStatusCombinedText().notificationCanonicalText()
    ).joinToString("|")
    isSessionStatusNotification() -> listOf(
        userId.orEmpty(),
        NOTIFICATION_SESSION_CATEGORY,
        notificationStatusCombinedText().notificationCanonicalText()
    ).joinToString("|")
    else -> listOf(
        userId.orEmpty(),
        storeId.orEmpty(),
        type.name,
        category.notificationCanonicalText(),
        source.normalizedNotificationText(),
        title.notificationCanonicalText(),
        message.notificationCanonicalText()
    ).joinToString("|")
}

private fun NotificationDataModel.isHistoryDuplicateOf(other: NotificationDataModel): Boolean =
    dedupeKey() == other.dedupeKey() &&
            kotlin.math.abs(createdAtMillis - other.createdAtMillis) <= IN_APP_NOTIFICATION_HISTORY_DEDUPE_WINDOW_MILLIS

private fun List<NotificationDataModel>.dedupeRecentNotificationHistory(): List<NotificationDataModel> {
    val kept = mutableListOf<NotificationDataModel>()
    for (notification in sortedByDescending { it.createdAtMillis }) {
        if (kept.none { keptNotification -> notification.isHistoryDuplicateOf(keptNotification) }) {
            kept += notification
        }
    }
    return kept
}

private suspend fun appendNotificationLocally(notification: NotificationDataModel) {
    if (notification.isLocalOnlyNotification()) return
    val old = notificationsState.payloadValue.orEmpty()
    notificationsState.emit(
        DataState.Success(
            (listOf(notification) + old.filterNot { it.isLocalOnlyNotification() })
                .distinctBy { it.id }
                .dedupeRecentNotificationHistory()
                .take(LOCAL_NOTIFICATION_HISTORY_LIMIT)
        )
    )
}

private suspend fun removeActiveInAppNotification(notificationId: String) {
    notificationPopupMutex.withLock {
        notificationPopupJobs.remove(notificationId)?.cancel()
        notificationPopupRemovalTokens.remove(notificationId)
        notificationPopupTransientById.remove(notificationId)
        val remaining = activeInAppNotificationsState.value.filter { it.id != notificationId }
        activeInAppNotificationsState.emit(remaining)
        if (latestInAppNotificationState.value?.id == notificationId) {
            latestInAppNotificationState.emit(remaining.firstOrNull())
        }
    }
}

private fun notificationPopupDelayMillis(notification: NotificationDataModel, transient: Boolean): Long = when {
    transient && notification.type == NotificationType.Negative -> 9_000L
    transient && notification.type == NotificationType.Neutral -> 4_500L
    transient -> 4_000L
    notification.type == NotificationType.Negative -> 13_000L
    notification.type == NotificationType.Neutral -> 7_500L
    else -> 5_500L
}

private fun scheduleNotificationPopupRemoval(notificationId: String, delayMillis: Long = 5_000L) {
    notificationPopupJobs.remove(notificationId)?.cancel()
    val token = "${getCurrentTimeMillis()}_${kotlin.random.Random.nextInt(0, Int.MAX_VALUE)}"
    notificationPopupRemovalTokens[notificationId] = token
    notificationPopupJobs[notificationId] = GlobalScope.launch(Dispatchers.ourIo) {
        delay(delayMillis.coerceAtLeast(1_000L))
        notificationPopupMutex.withLock {
            if (notificationPopupRemovalTokens[notificationId] == token) {
                notificationPopupJobs.remove(notificationId)
                notificationPopupRemovalTokens.remove(notificationId)
                notificationPopupTransientById.remove(notificationId)
                val remaining = activeInAppNotificationsState.value.filter { it.id != notificationId }
                activeInAppNotificationsState.emit(remaining)
                if (latestInAppNotificationState.value?.id == notificationId) {
                    latestInAppNotificationState.emit(remaining.firstOrNull())
                }
            }
        }
    }
}

private fun pruneDismissedNotificationPopupKeys(now: Long) {
    val expiredKeys = dismissedNotificationPopupKeysUntil
        .filterValues { suppressUntil -> suppressUntil <= now }
        .keys
        .toList()
    expiredKeys.forEach { key -> dismissedNotificationPopupKeysUntil.remove(key) }
}

private fun rememberDismissedNotificationPopupLocked(notification: NotificationDataModel, now: Long = getCurrentTimeMillis()) {
    dismissedNotificationPopupKeysUntil[notification.dedupeKey()] = now + IN_APP_NOTIFICATION_DISMISS_SUPPRESSION_MILLIS
}

private suspend fun pushInAppNotificationNow(notification: NotificationDataModel, transient: Boolean) {
    val now = getCurrentTimeMillis()
    val preparedNotification = notification.withHumanFriendlyNotificationText().copy(shownAtMillis = now)
    val key = preparedNotification.dedupeKey()
    var shouldPersist = false

    notificationPopupMutex.withLock {
        pruneDismissedNotificationPopupKeys(now)
        if (!shouldPostNotificationConsideringCloudTransport(preparedNotification)) return@withLock
        if ((dismissedNotificationPopupKeysUntil[key] ?: 0L) > now) return@withLock

        val duplicateActive = activeInAppNotificationsState.value.firstOrNull { existing ->
            existing.dedupeKey() == key &&
                    (preparedNotification.isConnectionStatusNotification() || now - existing.shownAtMillis <= IN_APP_NOTIFICATION_DEDUPE_WINDOW_MILLIS)
        }
        val duplicateRecent = notificationsState.payloadValue.orEmpty().firstOrNull { existing ->
            existing.dedupeKey() == key &&
                    (preparedNotification.isConnectionStatusNotification() || now - existing.createdAtMillis <= IN_APP_NOTIFICATION_DEDUPE_WINDOW_MILLIS)
        }

        if (duplicateActive != null) {
            latestInAppNotificationState.emit(duplicateActive)
            scheduleNotificationPopupRemoval(
                duplicateActive.id,
                notificationPopupDelayMillis(duplicateActive, notificationPopupTransientById[duplicateActive.id] == true)
            )
            return@withLock
        }

        if (duplicateRecent != null) return@withLock

        val replacingConnectionStatus = preparedNotification.isConnectionStatusNotification()
        val activeBeforeInsert = activeInAppNotificationsState.value.filterNot { existing ->
            val shouldRemoveConnectionPeer = replacingConnectionStatus && existing.isConnectionStatusNotification()
            val shouldRemoveLoading = preparedNotification.type != NotificationType.Neutral &&
                    notificationPopupTransientById[existing.id] == true &&
                    existing.type == NotificationType.Neutral
            val shouldRemove = shouldRemoveConnectionPeer || shouldRemoveLoading
            if (shouldRemove) {
                notificationPopupJobs.remove(existing.id)?.cancel()
                notificationPopupRemovalTokens.remove(existing.id)
                notificationPopupTransientById.remove(existing.id)
            }
            shouldRemove
        }

        latestInAppNotificationState.emit(preparedNotification)
        notificationPopupTransientById[preparedNotification.id] = transient
        val nextActive = (listOf(preparedNotification) + activeBeforeInsert)
            .distinctBy { it.dedupeKey() }
            .take(ACTIVE_IN_APP_NOTIFICATION_LIMIT)
        val nextActiveIds = nextActive.map { it.id }.toSet()
        activeInAppNotificationsState.value
            .map { it.id }
            .filter { it !in nextActiveIds }
            .forEach { droppedId ->
                notificationPopupJobs.remove(droppedId)?.cancel()
                notificationPopupRemovalTokens.remove(droppedId)
                notificationPopupTransientById.remove(droppedId)
            }
        activeInAppNotificationsState.emit(nextActive)
        scheduleNotificationPopupRemoval(preparedNotification.id, notificationPopupDelayMillis(preparedNotification, transient))
        shouldPersist = !transient && !preparedNotification.isLocalOnlyNotification()
    }

    if (shouldPersist) {
        appendNotificationLocally(preparedNotification)
        saveNotificationToServer(preparedNotification)
    }
}

private fun pushInAppNotification(notification: NotificationDataModel, transient: Boolean) {
    GlobalScope.launch(Dispatchers.ourIo) {
        pushInAppNotificationNow(notification, transient)
    }
}

private suspend fun postInAppNotificationNow(
    message: List<LocalizedStringDataModel>?,
    type: NotificationType,
    transient: Boolean = false
) {
    message?.extractLocalizedString(appLanguageState.value)?.run {
        postInAppNotificationNow(this, type, transient)
    }
}

private suspend fun postInAppNotificationNow(message: String, type: NotificationType, transient: Boolean = false) {
    pushInAppNotificationNow(createNotificationDataModel(message, type), transient)
}

fun postInAppNotification(
    message: List<LocalizedStringDataModel>?,
    type: NotificationType,
    transient: Boolean = false
) {
    message?.extractLocalizedString(appLanguageState.value)?.run {
        postInAppNotification(this, type, transient)
    }
}

fun postInAppNotification(message: String, type: NotificationType, transient: Boolean = false) {
    pushInAppNotification(createNotificationDataModel(message, type), transient)
}

private suspend fun clearTransientOrNeutralInAppNotifications() {
    notificationPopupMutex.withLock {
        val idsToRemove = activeInAppNotificationsState.value
            .filter { notification ->
                notificationPopupTransientById[notification.id] == true || notification.type == NotificationType.Neutral
            }
            .map { it.id }
            .toSet()

        if (idsToRemove.isEmpty()) return@withLock

        idsToRemove.forEach { notificationId ->
            notificationPopupJobs.remove(notificationId)?.cancel()
            notificationPopupRemovalTokens.remove(notificationId)
            notificationPopupTransientById.remove(notificationId)
        }

        val remaining = activeInAppNotificationsState.value.filterNot { it.id in idsToRemove }
        activeInAppNotificationsState.emit(remaining)
        if (latestInAppNotificationState.value?.id?.let { it in idsToRemove } == true) {
            latestInAppNotificationState.emit(remaining.firstOrNull())
        }
    }
}

fun clearInAppNotification() {
    GlobalScope.launch(Dispatchers.ourIo) {
        notificationPopupMutex.withLock {
            val now = getCurrentTimeMillis()
            activeInAppNotificationsState.value.forEach { notification ->
                rememberDismissedNotificationPopupLocked(notification, now)
            }
            notificationPopupJobs.values.toList().forEach { it.cancel() }
            notificationPopupJobs.clear()
            notificationPopupRemovalTokens.clear()
            notificationPopupTransientById.clear()
            latestInAppNotificationState.emit(null)
            activeInAppNotificationsState.emit(emptyList())
        }
    }
}

fun dismissInAppNotification(notificationId: String, markAsRead: Boolean = true) {
    GlobalScope.launch(Dispatchers.ourIo) {
        var savedNotificationIdToMark: String? = null

        notificationPopupMutex.withLock {
            val target = activeInAppNotificationsState.value.firstOrNull { it.id == notificationId }
            val targetKey = target?.dedupeKey()
            val idsToRemove = activeInAppNotificationsState.value
                .filter { existing -> existing.id == notificationId || (targetKey != null && existing.dedupeKey() == targetKey) }
                .map { it.id }
                .toSet()

            if (target != null) rememberDismissedNotificationPopupLocked(target)

            idsToRemove.forEach { id ->
                notificationPopupJobs.remove(id)?.cancel()
                notificationPopupRemovalTokens.remove(id)
                notificationPopupTransientById.remove(id)
            }

            val remaining = activeInAppNotificationsState.value.filterNot { it.id in idsToRemove }
            activeInAppNotificationsState.emit(remaining)
            if (latestInAppNotificationState.value?.id?.let { it in idsToRemove } == true) {
                latestInAppNotificationState.emit(remaining.firstOrNull())
            }

            savedNotificationIdToMark = notificationId.takeIf {
                markAsRead && notificationsState.payloadValue.orEmpty().any { notification ->
                    notification.id == notificationId && notification.isSavedOnServer
                }
            }
        }

        savedNotificationIdToMark?.let { markNotificationRead(it) }
    }
}

private suspend fun saveNotificationToServerNow(notification: NotificationDataModel) {
    if (notification.message.isBlank()) return
    if (notification.isLocalOnlyNotification()) return
    if (getStoredUserAuthTokens?.invoke() == null) return
    if (userAccountState.payloadValue == null) return

    saveNotificationMutex.withLock {
        val response = networkRequest<NotificationDataModel, NotificationDataModel>(
            method = HttpMethod.Post,
            endpointUrl = "notifications/add",
            body = notification
        )

        if (!response.negative && response.payload != null) {
            val saved = response.payload.copy(isSavedOnServer = true)
            val old = notificationsState.payloadValue.orEmpty()
            notificationsState.emit(
                DataState.Success(
                    (listOf(saved) + old.filter { it.id != notification.id && it.id != saved.id && !it.isLocalOnlyNotification() })
                        .distinctBy { it.id }
                        .dedupeRecentNotificationHistory()
                        .take(LOCAL_NOTIFICATION_HISTORY_LIMIT)
                )
            )
        } else if (response.transportFailure) {
            // Leave the local copy with isSavedOnServer=false. It will be retried after reconnect.
        }
    }
}

suspend fun syncPendingNotificationsToServerNow(): Int {
    if (syncPendingNotificationsMutex.isLocked) return 0
    if (getStoredUserAuthTokens?.invoke() == null) return 0
    if (userAccountState.payloadValue == null) return 0

    return syncPendingNotificationsMutex.withLock {
        val pending = notificationsState.payloadValue.orEmpty()
            .filter { !it.isSavedOnServer && it.message.isNotBlank() && !it.isLocalOnlyNotification() }
            .distinctBy { notification -> notification.id.ifBlank { notification.dedupeKey() } }
            .sortedBy { it.createdAtMillis }

        var syncedCount = 0
        pending.forEach { notification ->
            val before = notificationsState.payloadValue.orEmpty().firstOrNull { it.id == notification.id }
            saveNotificationToServerNow(notification)
            val after = notificationsState.payloadValue.orEmpty().firstOrNull { it.id == notification.id }
            if (before?.isSavedOnServer != true && after?.isSavedOnServer == true) syncedCount += 1
        }
        syncedCount
    }
}

fun syncPendingNotificationsToServer() {
    if (syncPendingNotificationsMutex.isLocked) return

    GlobalScope.launch(Dispatchers.ourIo) {
        syncPendingNotificationsToServerNow()
    }
}

fun getNotifications() {
    if (!getNotificationsMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            getNotificationsMutex.withLock {
                if (getStoredUserAuthTokens?.invoke() == null) return@withLock

                val localPending = notificationsState.payloadValue.orEmpty()
                    .filter { !it.isSavedOnServer && !it.isLocalOnlyNotification() }

                val response = networkRequest<List<NotificationDataModel>, Unit>(
                    method = HttpMethod.Get,
                    endpointUrl = "notifications/get"
                )

                if (!response.negative) {
                    val serverNotifications = response.payload.orEmpty().filterNot { it.isLocalOnlyNotification() }
                    val currentNotifications = notificationsState.payloadValue.orEmpty().filterNot { it.isLocalOnlyNotification() }
                    val previousIds = currentNotifications.map { it.id }.toSet()
                    val now = getCurrentTimeMillis()
                    val recentPreviousByKey = currentNotifications
                        .filter { now - it.createdAtMillis <= IN_APP_NOTIFICATION_HISTORY_DEDUPE_WINDOW_MILLIS }
                        .groupBy { it.dedupeKey() }

                    serverNotifications
                        .asSequence()
                        .filter { notification ->
                            val recentDuplicates = recentPreviousByKey[notification.dedupeKey()].orEmpty()
                            notification.isSavedOnServer &&
                                    notification.id.isNotBlank() &&
                                    notification.id !in previousIds &&
                                    notification.id !in serverNotificationPopupIds &&
                                    recentDuplicates.none { previous -> notification.isHistoryDuplicateOf(previous) } &&
                                    notification.readAtMillis == null &&
                                    notification.message.isNotBlank() &&
                                    (notification.createdAtMillis >= notificationPopupBootMillis || now - notification.createdAtMillis <= SERVER_NOTIFICATION_POPUP_FRESH_WINDOW_MILLIS)
                        }
                        .sortedBy { it.createdAtMillis }
                        .forEach { notification ->
                            serverNotificationPopupIds += notification.id
                            pushInAppNotification(notification.copy(shownAtMillis = now), transient = true)
                        }

                    if (serverNotificationPopupIds.size > LOCAL_NOTIFICATION_HISTORY_LIMIT * 4) {
                        val visibleServerIds = serverNotifications.map { it.id }.toSet()
                        serverNotificationPopupIds.retainAll(visibleServerIds)
                    }

                    val merged = (localPending + serverNotifications)
                        .distinctBy { it.id }
                        .dedupeRecentNotificationHistory()
                        .take(LOCAL_NOTIFICATION_HISTORY_LIMIT)
                    notificationsState.emit(DataState.Success(merged, response.message))
                    syncPendingNotificationsToServer()
                } else if (response.transportFailure) {
                    // Keep local notification history available offline.
                    notificationsState.emit(
                        DataState.Success(
                            notificationsState.payloadValue.orEmpty().filterNot { it.isLocalOnlyNotification() },
                            response.message
                        )
                    )
                }
            }
        }
}

fun saveNotificationToServer(notification: NotificationDataModel) {
    if (notification.message.isBlank()) return
    if (notification.isLocalOnlyNotification()) return
    if (getStoredUserAuthTokens?.invoke() == null) return
    if (userAccountState.payloadValue == null) return

    GlobalScope.launch(Dispatchers.ourIo) {
        saveNotificationToServerNow(notification)
    }
}

fun markNotificationRead(notificationId: String) {
    if (!markNotificationReadMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            markNotificationReadMutex.withLock {
                val local = notificationsState.payloadValue.orEmpty()
                val target = local.firstOrNull { it.id == notificationId }
                removeActiveInAppNotification(notificationId)
                if (target == null) return@withLock

                val now = getCurrentTimeMillis()
                notificationsState.emit(
                    DataState.Success(local.map { if (it.id == notificationId) it.copy(readAtMillis = now) else it })
                )

                if (!target.isSavedOnServer) return@withLock

                val response = networkRequest<List<NotificationDataModel>, List<String>>(
                    method = HttpMethod.Put,
                    endpointUrl = "notifications/read",
                    body = listOf(notificationId)
                )

                if (!response.negative && response.payload != null) {
                    notificationsState.emit(DataState.Success(response.payload.filterNot { it.isLocalOnlyNotification() }))
                }
            }
        }
}

fun markAllNotificationsRead() {
    GlobalScope.launch(Dispatchers.ourIo) {
        val unreadNotifications = notificationsState.payloadValue.orEmpty()
            .filter { it.readAtMillis == null }

        if (unreadNotifications.isEmpty()) return@launch

        val ids = unreadNotifications.map { it.id }
        val savedIds = unreadNotifications
            .filter { it.isSavedOnServer }
            .map { it.id }

        val now = getCurrentTimeMillis()
        notificationsState.emit(
            DataState.Success(
                notificationsState.payloadValue.orEmpty().map { if (it.id in ids) it.copy(readAtMillis = now) else it }
            )
        )

        if (savedIds.isEmpty()) return@launch

        val response = networkRequest<List<NotificationDataModel>, List<String>>(
            method = HttpMethod.Put,
            endpointUrl = "notifications/read",
            body = savedIds
        )

        if (!response.negative && response.payload != null) {
            notificationsState.emit(DataState.Success(response.payload.filterNot { it.isLocalOnlyNotification() }))
        }
    }
}


private fun List<SupportTicketDataModel>.upsertSupportTicket(ticket: SupportTicketDataModel): List<SupportTicketDataModel> {
    val index = indexOfFirst { it.id == ticket.id }
    return (if (index == -1) listOf(ticket) + this else toMutableList().also { it[index] = ticket })
        .sortedByDescending { it.updatedAtMillis }
}

private fun List<SupportMessageDataModel>.upsertSupportMessage(message: SupportMessageDataModel): List<SupportMessageDataModel> {
    val index = indexOfFirst { it.id == message.id || (message.clientMessageId != null && it.clientMessageId == message.clientMessageId) }
    return (if (index == -1) this + message else toMutableList().also { it[index] = message })
        .sortedBy { it.createdAtMillis }
}

fun getSupportTickets(onCompleted: ((DataState<List<SupportTicketDataModel>>) -> Unit)? = null) {
    if (!getSupportTicketsMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            getSupportTicketsMutex.withLock {
                if (getStoredUserAuthTokens?.invoke() == null) return@withLock

                val response = networkRequest<List<SupportTicketDataModel>, Unit>(
                    method = HttpMethod.Get,
                    endpointUrl = globalAppConfigurationState.payloadValue.getSupportTicketsPath.first
                )

                if (response.negative || response.payload == null) {
                    if (!response.transportFailure) postInAppNotification(response.message, NotificationType.Negative)
                    onCompleted?.invoke(DataState.Empty(response.message))
                } else {
                    supportTicketsState.emit(DataState.Success(response.payload, response.message))
                    activeSupportTicketIdState.emit(
                        activeSupportTicketIdState.value?.takeIf { activeId -> response.payload.any { it.id == activeId } }
                            ?: response.payload.firstOrNull { it.status != "closed" }?.id
                            ?: response.payload.firstOrNull()?.id
                    )
                    onCompleted?.invoke(DataState.Success(response.payload, response.message))
                }
            }
        }
}

fun createSupportTicket(
    request: SupportTicketCreateRequestDataModel,
    onCompleted: ((DataState<SupportTicketDataModel>) -> Unit)? = null
) {
    if (request.initialMessage.isBlank()) return
    if (!createSupportTicketMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            createSupportTicketMutex.withLock {
                supportMessageSendingState.emit(true)
                val response = networkRequest<SupportTicketDataModel, SupportTicketCreateRequestDataModel>(
                    method = HttpMethod.Post,
                    endpointUrl = globalAppConfigurationState.payloadValue.createSupportTicketPath.first,
                    body = request.copy(
                        subject = request.subject.ifBlank { request.initialMessage.take(80) },
                        initialMessage = request.initialMessage.trim()
                    )
                )
                supportMessageSendingState.emit(false)

                if (response.negative || response.payload == null) {
                    postInAppNotification(response.message, if (response.transportFailure) NotificationType.Neutral else NotificationType.Negative)
                    onCompleted?.invoke(DataState.Empty(response.message))
                } else {
                    val ticket = response.payload
                    supportTicketsState.emit(DataState.Success(supportTicketsState.payloadValue.orEmpty().upsertSupportTicket(ticket), response.message))
                    activeSupportTicketIdState.emit(ticket.id)
                    getSupportMessages(ticket.id, markRead = true)
                    postInAppNotification(response.message, NotificationType.Positive)
                    onCompleted?.invoke(DataState.Success(ticket, response.message))
                }
            }
        }
}

fun getSupportMessages(
    ticketId: String,
    markRead: Boolean = true,
    onCompleted: ((DataState<List<SupportMessageDataModel>>) -> Unit)? = null
) {
    if (ticketId.isBlank()) return
    if (!getSupportMessagesMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            getSupportMessagesMutex.withLock {
                activeSupportTicketIdState.emit(ticketId)
                val response = networkRequest<List<SupportMessageDataModel>, Unit>(
                    method = HttpMethod.Get,
                    endpointUrl = globalAppConfigurationState.payloadValue.getSupportMessagesPath.first,
                    headers = mapOf(
                        "ticket_id" to ticketId,
                        "mark_read" to markRead.toString()
                    )
                )

                if (response.negative || response.payload == null) {
                    if (!response.transportFailure) postInAppNotification(response.message, NotificationType.Negative)
                    onCompleted?.invoke(DataState.Empty(response.message))
                } else {
                    supportMessagesState.emit(DataState.Success(response.payload, response.message))
                    if (markRead) {
                        supportTicketsState.emit(
                            DataState.Success(
                                supportTicketsState.payloadValue.orEmpty().map { if (it.id == ticketId) it.copy(unreadForUserCount = 0) else it }
                            )
                        )
                    }
                    onCompleted?.invoke(DataState.Success(response.payload, response.message))
                }
            }
        }
}

fun sendSupportMessage(
    request: SupportMessageSendRequestDataModel,
    onCompleted: ((DataState<SupportMessageDataModel>) -> Unit)? = null
) {
    if (request.ticketId.isBlank() || request.body.isBlank()) return
    if (!sendSupportMessageMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            sendSupportMessageMutex.withLock {
                supportMessageSendingState.emit(true)
                val response = networkRequest<SupportMessageDataModel, SupportMessageSendRequestDataModel>(
                    method = HttpMethod.Post,
                    endpointUrl = globalAppConfigurationState.payloadValue.sendSupportMessagePath.first,
                    body = request.copy(body = request.body.trim())
                )
                supportMessageSendingState.emit(false)

                if (response.negative || response.payload == null) {
                    postInAppNotification(response.message, if (response.transportFailure) NotificationType.Neutral else NotificationType.Negative)
                    onCompleted?.invoke(DataState.Empty(response.message))
                } else {
                    val message = response.payload
                    supportMessagesState.emit(DataState.Success(supportMessagesState.payloadValue.orEmpty().upsertSupportMessage(message), response.message))
                    getSupportTickets()
                    onCompleted?.invoke(DataState.Success(message, response.message))
                }
            }
        }
}

fun closeSupportTicket(ticketId: String, onCompleted: ((DataState<SupportTicketDataModel>) -> Unit)? = null) {
    if (ticketId.isBlank()) return
    if (!closeSupportTicketMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            closeSupportTicketMutex.withLock {
                val response = networkRequest<SupportTicketDataModel, SupportTicketActionRequestDataModel>(
                    method = HttpMethod.Post,
                    endpointUrl = globalAppConfigurationState.payloadValue.closeSupportTicketPath.first,
                    body = SupportTicketActionRequestDataModel(ticketId)
                )

                if (response.negative || response.payload == null) {
                    postInAppNotification(response.message, NotificationType.Negative)
                    onCompleted?.invoke(DataState.Empty(response.message))
                } else {
                    supportTicketsState.emit(DataState.Success(supportTicketsState.payloadValue.orEmpty().upsertSupportTicket(response.payload), response.message))
                    postInAppNotification(response.message, NotificationType.Positive)
                    onCompleted?.invoke(DataState.Success(response.payload, response.message))
                }
            }
        }
}

fun reopenSupportTicket(ticketId: String, onCompleted: ((DataState<SupportTicketDataModel>) -> Unit)? = null) {
    if (ticketId.isBlank()) return
    if (!reopenSupportTicketMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            reopenSupportTicketMutex.withLock {
                val response = networkRequest<SupportTicketDataModel, SupportTicketActionRequestDataModel>(
                    method = HttpMethod.Post,
                    endpointUrl = globalAppConfigurationState.payloadValue.reopenSupportTicketPath.first,
                    body = SupportTicketActionRequestDataModel(ticketId)
                )

                if (response.negative || response.payload == null) {
                    postInAppNotification(response.message, NotificationType.Negative)
                    onCompleted?.invoke(DataState.Empty(response.message))
                } else {
                    supportTicketsState.emit(DataState.Success(supportTicketsState.payloadValue.orEmpty().upsertSupportTicket(response.payload), response.message))
                    postInAppNotification(response.message, NotificationType.Positive)
                    onCompleted?.invoke(DataState.Success(response.payload, response.message))
                }
            }
        }
}

fun markSupportMessagesRead(ticketId: String) {
    if (ticketId.isBlank()) return
    if (!markSupportMessagesReadMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            markSupportMessagesReadMutex.withLock {
                networkRequest<List<SupportMessageDataModel>, SupportMessagesReadRequestDataModel>(
                    method = HttpMethod.Post,
                    endpointUrl = globalAppConfigurationState.payloadValue.markSupportMessagesReadPath.first,
                    body = SupportMessagesReadRequestDataModel(ticketId)
                ).takeIf { !it.negative && it.payload != null }?.payload?.let { messages ->
                    supportMessagesState.emit(DataState.Success(messages))
                    supportTicketsState.emit(
                        DataState.Success(
                            supportTicketsState.payloadValue.orEmpty().map { if (it.id == ticketId) it.copy(unreadForUserCount = 0) else it }
                        )
                    )
                }
            }
        }
}

fun getSecuritySessions(onCompleted: ((DataState<List<SecuritySessionDataModel>>) -> Unit)? = null) {
    if (!getSecuritySessionsMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            getSecuritySessionsMutex.withLock {
                val response = networkRequest<List<SecuritySessionDataModel>, Unit>(
                    method = HttpMethod.Get,
                    endpointUrl = globalAppConfigurationState.payloadValue.getSecuritySessionsPath.first
                )

                if (response.negative || response.payload == null) {
                    if (response.transportFailure) {
                        postInAppNotification(
                            localizedStringResourceMessage(
                                id = 215,
                                main = "Cannot reach server. Security sessions will refresh when connection returns.",
                                ru = "Сервер недоступен. Сеансы безопасности обновятся после восстановления соединения.",
                                kk = "Сервер қолжетімсіз. Қауіпсіздік сеанстары байланыс қалпына келгенде жаңартылады."
                            ),
                            NotificationType.Neutral
                        )
                    } else {
                        postInAppNotification(response.message, NotificationType.Negative)
                    }
                    onCompleted?.invoke(DataState.Empty(response.message))
                } else {
                    securitySessionsState.emit(DataState.Success(response.payload, response.message))
                    onCompleted?.invoke(DataState.Success(response.payload, response.message))
                }
            }
        }
}

fun getSecuritySessionHistory(onCompleted: ((DataState<List<SecuritySessionHistoryDataModel>>) -> Unit)? = null) {
    if (!getSecuritySessionHistoryMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            getSecuritySessionHistoryMutex.withLock {
                val response = networkRequest<List<SecuritySessionHistoryDataModel>, Unit>(
                    method = HttpMethod.Get,
                    endpointUrl = globalAppConfigurationState.payloadValue.getSecuritySessionHistoryPath.first
                )

                if (response.negative || response.payload == null) {
                    if (!response.transportFailure) {
                        postInAppNotification(response.message, NotificationType.Negative)
                    }
                    onCompleted?.invoke(DataState.Empty(response.message))
                } else {
                    securitySessionHistoryState.emit(DataState.Success(response.payload, response.message))
                    onCompleted?.invoke(DataState.Success(response.payload, response.message))
                }
            }
        }
}

fun revokeSecuritySession(
    sessionId: String,
    onCompleted: ((DataState<List<SecuritySessionDataModel>>) -> Unit)? = null
) {
    if (sessionId.isBlank()) return

    if (!revokeSecuritySessionMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            revokeSecuritySessionMutex.withLock {
                val response = networkRequest<List<SecuritySessionDataModel>, SecuritySessionRevokeRequestDataModel>(
                    method = HttpMethod.Post,
                    endpointUrl = globalAppConfigurationState.payloadValue.revokeSecuritySessionPath.first,
                    body = SecuritySessionRevokeRequestDataModel(sessionId)
                )

                if (response.negative || response.payload == null) {
                    postInAppNotification(response.message, if (response.transportFailure) NotificationType.Neutral else NotificationType.Negative)
                    onCompleted?.invoke(DataState.Empty(response.message))
                } else {
                    securitySessionsState.emit(DataState.Success(response.payload, response.message))
                    getSecuritySessionHistory()
                    postInAppNotification(response.message ?: localizedStringResourceMessage(
                        id = 216,
                        main = "Session revoked",
                        ru = "Сеанс завершён",
                        kk = "Сеанс тоқтатылды"
                    ), NotificationType.Positive)
                    onCompleted?.invoke(DataState.Success(response.payload, response.message))
                }
            }
        }
}

fun revokeOtherSecuritySessions(onCompleted: ((DataState<List<SecuritySessionDataModel>>) -> Unit)? = null) {
    if (!revokeOtherSecuritySessionsMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            revokeOtherSecuritySessionsMutex.withLock {
                val response = networkRequest<List<SecuritySessionDataModel>, Unit>(
                    method = HttpMethod.Post,
                    endpointUrl = globalAppConfigurationState.payloadValue.revokeOtherSecuritySessionsPath.first
                )

                if (response.negative || response.payload == null) {
                    postInAppNotification(response.message, if (response.transportFailure) NotificationType.Neutral else NotificationType.Negative)
                    onCompleted?.invoke(DataState.Empty(response.message))
                } else {
                    securitySessionsState.emit(DataState.Success(response.payload, response.message))
                    getSecuritySessionHistory()
                    postInAppNotification(response.message ?: localizedStringResourceMessage(
                        id = 217,
                        main = "Other sessions revoked",
                        ru = "Другие сеансы завершены",
                        kk = "Басқа сеанстар тоқтатылды"
                    ), NotificationType.Positive)
                    onCompleted?.invoke(DataState.Success(response.payload, response.message))
                }
            }
        }
}

fun logInUser(userAuthLogIn: UserAuthLogInDataModel, serverUrlOverride: String? = null) {
    if (!logInMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            logInMutex.withLock {
                logInInProgressState.emit(true)
                try {
                    postInAppNotificationNow(stringLoggingInState.value, NotificationType.Neutral, transient = true)

                    // Keep existing offline credentials until a new token pair is actually received.
                    // This prevents a temporary LAN/server outage during login retry from locally signing the user out.
                    httpClient.authProvider<BearerAuthProvider>()?.clearToken()

                    val logInRequest = userAuthLogIn.copy(deviceInfo = buildCurrentClientDeviceInfo())

                    val response = networkRequest<TokenPair, UserAuthLogInDataModel>(
                        HttpMethod.Post,
                        serverUrl = serverUrlOverride,
                        endpointUrl = globalAppConfigurationState.payloadValue.logInPath.first,
                        body = logInRequest
                    )

                    if (response.negative || response.payload == null) {
                        val fallbackMessage = if (response.transportFailure) {
                            localizedStringResourceMessage(
                                id = 1140,
                                main = "Can’t reach AITA server. Check Wi‑Fi or server address.",
                                ru = "Сервер AITA недоступен. Проверьте Wi‑Fi или адрес сервера.",
                                kk = "AITA сервері қолжетімсіз. Wi‑Fi немесе сервер мекенжайын тексеріңіз."
                            )
                        } else {
                            localizedStringResourceMessage(
                                id = 222,
                                main = "Login failed: empty token response",
                                ru = "Не удалось войти: сервер не вернул токены",
                                kk = "Кіру орындалмады: сервер токендерді қайтармады"
                            )
                        }
                        clearTransientOrNeutralInAppNotifications()
                        postInAppNotificationNow(
                            response.message ?: fallbackMessage,
                            NotificationType.Negative,
                            transient = false
                        )
                    } else {
                        setStoredUserAuthTokens?.invoke(response.payload)
                        clearCloudSessionRefreshRequirementForNotifications(CLOUD_TRANSPORT_STATUS_REACHABLE)
                        markCloudTransportReachableForNotifications(authenticated = true)
                        httpClient.authProvider<BearerAuthProvider>()?.clearToken()
                        clearTransientOrNeutralInAppNotifications()
                        postInAppNotificationNow(
                            localizedStringResourceMessage(
                                id = 1156,
                                main = "Logged in",
                                ru = "Вход выполнен",
                                kk = "Кіру орындалды"
                            ),
                            NotificationType.Positive,
                            transient = false
                        )
                        syncPendingSessionCleanupsToServer()
                        getUser(forceLogOut = false)
                    }
                } catch (throwable: Throwable) {
                    if (throwable is CancellationException) throw throwable
                    clearTransientOrNeutralInAppNotifications()
                    postInAppNotificationNow(
                        localizedStringResourceMessage(
                            id = 1140,
                            main = "Login failed. Check server connection and try again.",
                            ru = "Не удалось войти. Проверьте соединение с сервером и попробуйте ещё раз.",
                            kk = "Кіру орындалмады. Сервермен байланысты тексеріп, қайталап көріңіз."
                        ),
                        NotificationType.Negative,
                        transient = false
                    )
                } finally {
                    logInInProgressState.emit(false)
                }
            }
        }
    else
        postInAppNotification(
            localizedStringResourceMessage(
                id = 219,
                main = "Login is already in progress",
                ru = "Вход уже выполняется",
                kk = "Кіру қазірдің өзінде орындалып жатыр"
            ),
            NotificationType.Neutral,
            transient = true
        )
}

fun signUpUser(userAuthSignUp: UserAuthSignUpDataModel, serverUrlOverride: String? = null) {
    if (!signUpUserMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            signUpInProgressState.emit(true)

            try {
                signUpUserMutex.withLock {
                    postInAppNotificationNow(stringSigningUpState.value, NotificationType.Neutral, transient = true)

                    val signUpRequest = userAuthSignUp.copy(deviceInfo = buildCurrentClientDeviceInfo())

                    val response = networkRequest<TokenPair, UserAuthSignUpDataModel>(
                        HttpMethod.Post,
                        serverUrl = serverUrlOverride,
                        endpointUrl = globalAppConfigurationState.payloadValue.signUpPath.first,
                        body = signUpRequest
                    )

                    if (response.negative || response.payload == null) {
                        clearTransientOrNeutralInAppNotifications()
                        postInAppNotificationNow(
                            response.message ?: localizedStringResourceMessage(
                                id = 3,
                                main = "Internal server error",
                                ru = "Внутренняя ошибка сервера",
                                kk = "Сервердің ішкі қатесі"
                            ),
                            NotificationType.Negative,
                            transient = false
                        )
                    } else {
                        setStoredUserAuthTokens?.invoke(response.payload)
                        clearCloudSessionRefreshRequirementForNotifications(CLOUD_TRANSPORT_STATUS_REACHABLE)
                        markCloudTransportReachableForNotifications(authenticated = true)
                        httpClient.authProvider<BearerAuthProvider>()?.clearToken()
                        clearTransientOrNeutralInAppNotifications()
                        postInAppNotificationNow(
                            localizedStringResourceMessage(
                                id = 1157,
                                main = "Signed up",
                                ru = "Регистрация выполнена",
                                kk = "Тіркелу орындалды"
                            ),
                            NotificationType.Positive,
                            transient = false
                        )
                        syncPendingSessionCleanupsToServer()

                        getUser(forceLogOut = false)
                    }
                }
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                clearTransientOrNeutralInAppNotifications()
                postInAppNotificationNow(
                    localizedStringResourceMessage(
                        id = 3,
                        main = "Sign-up failed. Check server connection and try again.",
                        ru = "Не удалось зарегистрироваться. Проверьте соединение с сервером и попробуйте ещё раз.",
                        kk = "Тіркелу орындалмады. Сервермен байланысты тексеріп, қайталап көріңіз."
                    ),
                    NotificationType.Negative,
                    transient = false
                )
            } finally {
                signUpInProgressState.emit(false)
            }
        }
}

fun logOutUser() {
    if (!logOutUserMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            logOutUserMutex.withLock {
                val tokenSnapshot = getStoredUserAuthTokens?.invoke()
                val refreshToken = tokenSnapshot?.refreshToken
                val activeWorkshiftBeforeLogout = activeWorkshiftState.payloadValue
                    ?.takeIf { it.isActive && it.endedAtMillis == null }
                var logoutWorkshiftEndRequest: WorkshiftEndRequestDataModel? = null
                var logoutWorkshiftStoreId: String? = null

                if (activeWorkshiftBeforeLogout != null) {
                    val endedAtMillis = getCurrentTimeMillis()
                    val operationId = createClientOperationId(
                        prefix = "wse",
                        seed = listOf(activeWorkshiftBeforeLogout.id, activeWorkshiftBeforeLogout.storeId, endedAtMillis.toString()).joinToString(":")
                    )
                    logoutWorkshiftEndRequest = WorkshiftEndRequestDataModel(
                        workshiftId = activeWorkshiftBeforeLogout.id,
                        endedAtMillis = endedAtMillis,
                        clientOperationId = operationId,
                        deviceInfo = buildCurrentClientDeviceInfo()
                    )
                    logoutWorkshiftStoreId = activeWorkshiftBeforeLogout.storeId
                    endWorkshiftLocallyAndQueue(
                        workshift = activeWorkshiftBeforeLogout,
                        endedAtMillis = endedAtMillis,
                        postNotification = false,
                        clientOperationId = operationId
                    )
                }

                // Logout must never trap the cashier inside account screen. Local logout is immediate;
                // server refresh-session revoke is best-effort and can fail silently when the server token is already expired.
                stopRealtimeUpdates()
                setStoredUserAuthTokens?.invoke(null)
                setStoredUserAccountDataModel?.invoke(null)
                setActiveStoreId(null, syncServer = false)
                clearCloudSessionRefreshRequirementForNotifications(CLOUD_TRANSPORT_STATUS_UNKNOWN)
                httpClient.authProvider<BearerAuthProvider>()?.clearToken()

                if (!refreshToken.isNullOrBlank()) {
                    enqueuePendingSessionCleanup(
                        refreshToken = refreshToken,
                        reason = "local_logout",
                        workshiftEnd = logoutWorkshiftEndRequest,
                        workshiftStoreId = logoutWorkshiftStoreId
                    )
                }

                userAccountState.emit(DataState.Empty())
                storesState.emit(DataState.Empty())
                activeStoreIdState.emit(null)
                stockState.emit(DataState.Empty())
                stockBatchesState.emit(DataState.Empty())
                transactionsState.emit(DataState.Empty())
                securitySessionsState.emit(DataState.Empty())
                securitySessionHistoryState.emit(DataState.Empty())
                activeWorkshiftState.emit(DataState.Empty())
                supportTicketsState.emit(DataState.Empty())
                supportMessagesState.emit(DataState.Empty())
                activeSupportTicketIdState.emit(null)
                supportMessageSendingState.emit(false)
                latestInAppNotificationState.emit(null)
                activeInAppNotificationsState.emit(emptyList())

                val response = if (!refreshToken.isNullOrBlank()) {
                    val revokeResponse = networkRequest<Unit, LogoutCleanupRequestDataModel>(
                        HttpMethod.Delete,
                        endpointUrl = globalAppConfigurationState.payloadValue.logOutPath.first,
                        body = LogoutCleanupRequestDataModel(
                            refreshToken = refreshToken,
                            queuedAtMillis = getCurrentTimeMillis(),
                            reason = "local_logout",
                            deviceInfo = buildCurrentClientDeviceInfo(),
                            workshiftEnd = logoutWorkshiftEndRequest,
                            workshiftStoreId = logoutWorkshiftStoreId
                        )
                    )
                    if (!revokeResponse.negative) {
                        dropPendingSessionCleanup(refreshToken)
                        logoutWorkshiftEndRequest?.clientOperationId?.let { dropPendingWorkshiftEnd(it) }
                    }
                    revokeResponse
                } else {
                    ResponseDataModel<Unit>(
                        message = localizedStringResourceMessage(
                            id = 220,
                            main = "Logged out locally",
                            ru = "Выход выполнен локально",
                            kk = "Жергілікті түрде шығу орындалды"
                        ),
                        payload = null,
                        negative = false
                    )
                }

                postInAppNotification(
                    if (response.negative)
                        localizedStringResourceMessage(
                            id = 1148,
                            main = "Logged out locally; server session cleanup is queued",
                            ru = "Выход выполнен локально; завершение серверного сеанса поставлено в очередь",
                            kk = "Жергілікті түрде шығу орындалды; сервердегі сеансты аяқтау кезекке қойылды"
                        )
                    else response.message,
                    if (response.negative) NotificationType.Neutral else NotificationType.Positive
                )
            }
        }
}

fun getUser(forceLogOut: Boolean = true, applyServerActiveStore: Boolean = true) {
    GlobalScope.launch(Dispatchers.ourIo) {
        if (getStoredUserAuthTokens?.invoke() != null)
            getUserAccountMutex.withLock {
                getStoredUserAccountDataModel?.invoke()?.run {
                    userAccountState.emit(DataState.Success(this))
                }

                val response = networkRequest<UserAccountDataModel, Unit>(
                    HttpMethod.Get,
                    endpointUrl = globalAppConfigurationState.payloadValue.getUserPath.first
                )

                if (response.negative) {
                    when {
                        response.transportFailure -> {
                            // The persistent connection banner represents server outage; keep the cached account active.
                        }
                        response.httpStatusCode == HttpStatusCode.Unauthorized.value && forceLogOut -> {
                            postInAppNotification(response.message, NotificationType.Neutral)
                        }
                        else -> postInAppNotification(response.message, NotificationType.Negative)
                    }
                } else {
                    clearTransientOrNeutralInAppNotifications()
                    val account = response.payload!!
                    setStoredUserAccountDataModel?.invoke(account)
                    userAccountState.emit(DataState.Success(account, response.message))
                    applyUserAccountPreferencesAfterLogin(account)

                    if (applyServerActiveStore) {
                        val savedStoreId = account.activeStoreId?.takeIf { it.isNotBlank() }
                        when {
                            savedStoreId == null -> {
                                logCloudConnectionDiagnostic("getUser returned no server active store")
                            }

                            activeStoreExplicitNoneIsSet() -> {
                                logCloudConnectionDiagnostic(
                                    "getUser ignored server active store $savedStoreId because local active store is explicitly cleared"
                                )
                            }

                            else -> {
                                putLocalKv(KEY_ACTIVE_STORE_EXPLICIT_NONE, null)
                                putLocalKv(KEY_ACTIVE_STORE_ID, savedStoreId)
                                activeStoreIdState.emit(savedStoreId)
                                logCloudConnectionDiagnostic("getUser applied server active store $savedStoreId")
                            }
                        }
                    }

                    getGlobalAppConfiguration()
                    getNotifications()
                    syncPendingNotificationsToServer()
                    getSupportTickets()
                    getStores()
                    getSuppliers()
                    getGenericGoodsCategories()
                    startRealtimeUpdates()
                }
            }
    }
}

fun updateUser(
    userAccountUpdate: UserAccountUpdateDataModel
) {
    if (!updateUserMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            updateUserMutex.withLock {
                val response = networkRequest<UserAccountDataModel, UserAccountUpdateDataModel>(
                    HttpMethod.Put,
                    endpointUrl = globalAppConfigurationState.payloadValue.updateUserPath.first,
                    body = userAccountUpdate
                )

                if (response.negative) {
                    postInAppNotification(response.message, NotificationType.Negative)
                } else {
                    userAccountState.emit(DataState.Success(response.payload!!, response.message))

                    postInAppNotification(
                        response.message,
                        NotificationType.Positive
                    )
                    setStoredUserAccountDataModel?.invoke(response.payload)
                }
            }
        }
}

@kotlinx.serialization.Serializable
data class UserPreferencesDataModel(
    val appLanguage: String = DEFAULT_APP_LANGUAGE,
    val appThemeId: Long = DEFAULT_APP_THEME_ID,
    val appSizeModeId: Long = DEFAULT_APP_SIZE_MODE_ID
)

private suspend fun applyUserPreferencesLocally(
    appLanguage: String?,
    appThemeId: Long?,
    appSizeModeId: Long? = null
) {
    val normalizedLanguage = normalizeAppLanguagePreference(appLanguage)
    val normalizedThemeId = normalizeAppThemePreference(appThemeId)
    val normalizedSizeModeId = normalizeAppSizeModePreference(appSizeModeId)
    appLanguageState.emit(normalizedLanguage)
    appThemeIdState.emit(normalizedThemeId)
    appSizeModeIdState.emit(normalizedSizeModeId)
    putLocalKv(KEY_APP_LOCALE, normalizedLanguage)
    putLocalKv(KEY_APP_THEME, normalizedThemeId.toString())
    putLocalKv(KEY_APP_SIZE_MODE, normalizedSizeModeId.toString())
}

private suspend fun applyUserAccountPreferencesAfterLogin(account: UserAccountDataModel) {
    val override = authScreenPreferenceOverrideState.value
    if (override.touched) {
        val preferredLanguage = if (override.languageTouched) {
            normalizeAppLanguagePreference(override.appLanguage)
        } else {
            normalizeAppLanguagePreference(account.appLanguage)
        }
        val preferredThemeId = if (override.themeTouched) {
            normalizeAppThemePreference(override.appThemeId)
        } else {
            normalizeAppThemePreference(account.appThemeId)
        }
        val preferredSizeModeId = if (override.sizeModeTouched) {
            normalizeAppSizeModePreference(override.appSizeModeId)
        } else {
            normalizeAppSizeModePreference(account.appSizeModeId)
        }

        applyUserPreferencesLocally(preferredLanguage, preferredThemeId, preferredSizeModeId)
        authScreenPreferenceOverrideState.emit(AuthScreenPreferenceOverrideDataModel())
        syncUserPreferencesToServer(preferredLanguage, preferredThemeId, preferredSizeModeId, postFailure = false)
    } else {
        applyUserPreferencesLocally(account.appLanguage, account.appThemeId, account.appSizeModeId)
    }
}

fun syncUserPreferencesToServer(
    appLanguage: String = appLanguageState.value,
    appThemeId: Long = appThemeIdState.value,
    appSizeModeId: Long = appSizeModeIdState.value,
    postFailure: Boolean = true
) {
    if (getStoredUserAuthTokens?.invoke() == null || updateUserPreferencesMutex.isLocked) return

    GlobalScope.launch(Dispatchers.ourIo) {
        updateUserPreferencesMutex.withLock {
            val request = UserPreferencesDataModel(
                appLanguage = normalizeAppLanguagePreference(appLanguage),
                appThemeId = normalizeAppThemePreference(appThemeId),
                appSizeModeId = normalizeAppSizeModePreference(appSizeModeId)
            )

            val response = networkRequest<UserAccountDataModel, UserPreferencesDataModel>(
                method = HttpMethod.Put,
                endpointUrl = globalAppConfigurationState.payloadValue.updateUserPreferencesPath.first,
                body = request
            )

            if (response.negative || response.payload == null) {
                if (postFailure) {
                    postInAppNotification(response.message, NotificationType.Neutral, transient = true)
                }
            } else {
                userAccountState.emit(DataState.Success(response.payload, response.message))
                setStoredUserAccountDataModel?.invoke(response.payload)
            }
        }
    }
}

fun forceLogOutUser(
    message: List<LocalizedStringDataModel>? = null,
    postMessage: Boolean = false
) {
    GlobalScope.launch(Dispatchers.ourIo) {
        stopRealtimeUpdates()
        setStoredUserAuthTokens?.invoke(null)
        setStoredUserAccountDataModel?.invoke(null)
        setActiveStoreId(null, syncServer = false)
        clearCloudSessionRefreshRequirementForNotifications(CLOUD_TRANSPORT_STATUS_UNKNOWN)
        httpClient.authProvider<BearerAuthProvider>()?.clearToken()

        userAccountState.emit(DataState.Empty())
        storesState.emit(DataState.Empty())
        activeStoreIdState.emit(null)
        stockState.emit(DataState.Empty())
        stockBatchesState.emit(DataState.Empty())
        stockItemBranchAvailabilityState.emit(DataState.Empty())
        stockBatchMoveResultState.emit(DataState.Empty())
        transactionsState.emit(DataState.Empty())
        debtorsState.emit(DataState.Empty())
        securitySessionsState.emit(DataState.Empty())
        securitySessionHistoryState.emit(DataState.Empty())
        storeWorkerMembershipsState.emit(DataState.Empty())
        activeWorkshiftState.emit(DataState.Empty())
        operationLogsState.emit(DataState.Empty())
        storeAnalyticsDashboardState.emit(DataState.Empty())
        cashRegisterState.emit(DataState.Empty())
        cashRegisterEventsState.emit(DataState.Empty())
        userFinanceDashboardState.emit(DataState.Empty())
        activeStoreSubscriptionState.emit(DataState.Empty())
        activeStoreSubscriptionChargesState.emit(DataState.Empty())
        notificationPopupJobs.values.forEach { it.cancel() }
        notificationPopupJobs.clear()
        latestInAppNotificationState.emit(null)
        activeInAppNotificationsState.emit(emptyList())

        if (postMessage) {
            postInAppNotification(
                message ?: localizedStringResourceMessage(
                    id = 91,
                    main = "Cloud session needs refresh. You remain signed in locally.",
                    ru = "Облачный сеанс нужно обновить. Вы остаётесь в аккаунте локально.",
                    kk = "Бұлттық сеансты жаңарту қажет. Сіз жергілікті түрде аккаунтта қаласыз."
                ),
                NotificationType.Negative,
                transient = true
            )
        }
    }
}

suspend inline fun <reified Response, reified Body> networkRequest(
    method: HttpMethod,
    serverUrl: String? = null,
    endpointUrl: String,
    query: Map<String, Any?> = emptyMap(),
    headers: Map<String, String> = emptyMap(),
    body: Body? = null,
    contentType: ContentType? = ContentType.Application.Json
): ResponseDataModel<Response> {
    activeNetworkOperationsState.update { it + 1 }

    return try {
        ensureCachedGlobalConfigurationPrimedForNetwork()
        val serverUrlCandidates = resolvedServerUrlCandidates(serverUrl)
        var lastServerErrorResponse: ResponseDataModel<Response>? = null
        var lastTransportFailureMessage: List<LocalizedStringDataModel>? = null

        for ((index, resolvedServerUrl) in serverUrlCandidates.withIndex()) {
            var authRetryUsedForCandidate = false
            val requestHeaders = currentClientDeviceInfoHeaders() + headers

            retrySameServer@ while (true) {
                val endpointForAuthPreflight = endpointUrl.trim('/').lowercase()
                val publicEndpointForAuthPreflight = endpointForAuthPreflight.startsWith("auth/") ||
                        endpointForAuthPreflight.startsWith("config/") ||
                        endpointForAuthPreflight.startsWith("res/")
                val tokensForAuthPreflight = getStoredUserAuthTokens?.invoke()

                if (!publicEndpointForAuthPreflight &&
                    !authRetryUsedForCandidate &&
                    tokensForAuthPreflight != null &&
                    tokensForAuthPreflight.accessTokenNeedsRefreshForNetwork()
                ) {
                    val refreshed = refreshStoredAuthTokensOnceForNetworkRetry(postNotification = false)
                    if (refreshed) {
                        httpClient.authProvider<BearerAuthProvider>()?.clearToken()
                    } else if (!tokensForAuthPreflight.accessTokenIsStillUsableForNetwork()) {
                        recentAuthRefreshNonAuthFailureMessage()?.let { refreshFailureMessage ->
                            return authRefreshFailureResponseForNetworkRequest(refreshFailureMessage)
                        }

                        if (cloudTransportStatusState.value == CLOUD_TRANSPORT_STATUS_AUTH_REFRESH_REQUIRED) {
                            clearAuthRefreshNonAuthFailure()
                            val expiredMessage = localizedStringResourceMessage(
                                id = 91,
                                main = "Cloud session needs refresh. You remain signed in locally.",
                                ru = "Облачный сеанс нужно обновить. Вы остаётесь в аккаунте локально.",
                                kk = "Бұлттық сеансты жаңарту қажет. Сіз жергілікті түрде аккаунтта қаласыз."
                            )
                            markCloudSessionNeedsRefreshForNotifications()
                            return ResponseDataModel<Response>(
                                message = expiredMessage,
                                payload = null,
                                negative = true,
                                httpStatusCode = HttpStatusCode.Unauthorized.value,
                                transportFailure = false
                            )
                        }

                        val offlineMessage = localizedStringResourceMessage(
                            id = 214,
                            main = "Cannot reach server. Keeping you signed in offline.",
                            ru = "Сервер недоступен. Вы остаётесь в аккаунте офлайн.",
                            kk = "Сервер қолжетімсіз. Сіз офлайн режимде аккаунтта қаласыз."
                        )
                        markCloudTransportUnavailableForNotifications()
                        return ResponseDataModel<Response>(
                            message = offlineMessage,
                            payload = null,
                            negative = true,
                            httpStatusCode = HttpStatusCode.ServiceUnavailable.value,
                            transportFailure = true
                        )
                    }
                }

                val tokensBeforeRequest = getStoredUserAuthTokens?.invoke()
                try {
                    val requestUrl = networkTargetUrl(resolvedServerUrl, endpointUrl)
                    logNetworkAttempt("TRY ${method.value} $requestUrl")
                    val response = httpClient
                        .request(requestUrl) {
                            this.method = method

                            this.headers.append(HttpHeaders.CacheControl, "no-cache")
                            this.headers.append(HttpHeaders.Pragma, "no-cache")

                            requestHeaders.forEach { (key, value) ->
                                safeHttpHeaderValueOrNull(value)?.let { safeValue ->
                                    this.headers.append(key, safeValue)
                                }
                            }

                            query.forEach { (key, value) ->
                                value?.let {
                                    parameter(key, it)
                                }
                            }

                            body?.let { body ->
                                contentType?.let {
                                    this.contentType(it)
                                }

                                setBody(body)
                            }
                        }

                    val rawBody = response.bodyAsText()
                    val aitaServerResponse = response.isAitaServerResponse(rawBody)
                    logNetworkAttempt("RESULT ${method.value} $requestUrl HTTP ${response.status.value} aita=$aitaServerResponse")
                    val shouldRetryCandidate = index < serverUrlCandidates.lastIndex &&
                            shouldRetryNetworkRequestOnNextServerUrl(
                                method = method,
                                endpointUrl = endpointUrl,
                                status = response.status,
                                rawBody = rawBody,
                                aitaServerResponse = aitaServerResponse
                            )

                    if (!aitaServerResponse) {
                        val nonAitaResponse = nonAitaHttpResponseDataModel<Response>(response.status, rawBody, resolvedServerUrl)
                        lastTransportFailureMessage = nonAitaResponse.message
                        forgetReachableServerUrlCandidate(resolvedServerUrl)
                        markCloudTransportUnavailableForNotifications()
                        if (shouldRetryCandidate) {
                            break@retrySameServer
                        }
                        return nonAitaResponse
                    }

                    val decodedResponse = decodeNetworkResponseDataModel<Response>(rawBody, response.status)
                        .withAitaTransportFailureFromStatus(response.status)

                    if (response.status.isAitaServerUnhealthyForClientBanner() || decodedResponse.transportFailure) {
                        val failureResponse = decodedResponse.copy(transportFailure = true)
                        lastServerErrorResponse = failureResponse
                        markCloudTransportUnavailableForNotifications()
                        logCloudConnectionDiagnostic(
                            "server response treated as unavailable method=${method.value} endpoint=${endpointUrl.trim('/')} " +
                                    "http=${response.status.value} aita=$aitaServerResponse negative=${failureResponse.negative}"
                        )
                        if (shouldRetryCandidate) {
                            break@retrySameServer
                        }
                        return failureResponse
                    }

                    if (shouldRetryCandidate) {
                        lastServerErrorResponse = decodedResponse
                        break@retrySameServer
                    }

                    val endpointForReachability = endpointUrl.trim('/').lowercase()
                    val publicEndpointForReachability = endpointForReachability.startsWith("auth/") ||
                            endpointForReachability.startsWith("config/") ||
                            endpointForReachability.startsWith("res/")
                    val canMarkReachable = cloudResponseCanMarkReachable(endpointUrl, response.status)
                    if (canMarkReachable) {
                        rememberReachableServerUrl(resolvedServerUrl)
                        val authenticatedReachableResponse = response.status != HttpStatusCode.Unauthorized &&
                                getStoredUserAuthTokens?.invoke() != null &&
                                !publicEndpointForReachability
                        markCloudTransportReachableForNotifications(
                            authenticated = authenticatedReachableResponse,
                            authRefreshRequired = if (!publicEndpointForReachability && getStoredUserAuthTokens?.invoke() != null) {
                                response.status == HttpStatusCode.Unauthorized
                            } else {
                                null
                            }
                        )
                    } else {
                        logCloudConnectionDiagnostic(
                            "reachable mark suppressed endpoint=$endpointUrl http=${response.status.value} " +
                                    "status=${cloudTransportStatusName(cloudTransportStatusState.value)}"
                        )
                        if (response.status.value >= 500) {
                            markCloudTransportUnavailableForNotifications()
                        }
                    }

                    if (response.status == HttpStatusCode.Unauthorized) {
                        if (publicEndpointForReachability) {
                            return decodeNetworkResponseDataModel<Response>(rawBody, response.status)
                        }

                        val latestTokensAfterRequest = getStoredUserAuthTokens?.invoke()
                        if (!authRetryUsedForCandidate &&
                            tokensBeforeRequest != null &&
                            latestTokensAfterRequest != null &&
                            latestTokensAfterRequest.accessToken != tokensBeforeRequest.accessToken
                        ) {
                            httpClient.authProvider<BearerAuthProvider>()?.clearToken()
                            authRetryUsedForCandidate = true
                            continue@retrySameServer
                        }

                        if (!authRetryUsedForCandidate && refreshStoredAuthTokensOnceForNetworkRetry()) {
                            authRetryUsedForCandidate = true
                            continue@retrySameServer
                        }

                        recentAuthRefreshNonAuthFailureMessage()?.let { refreshFailureMessage ->
                            return authRefreshFailureResponseForNetworkRequest(refreshFailureMessage)
                        }

                        clearAuthRefreshNonAuthFailure()
                        val expiredMessage = localizedStringResourceMessage(
                            id = 91,
                            main = "Cloud session needs refresh. You remain signed in locally.",
                            ru = "Облачный сеанс нужно обновить. Вы остаётесь в аккаунте локально.",
                            kk = "Бұлттық сеансты жаңарту қажет. Сіз жергілікті түрде аккаунтта қаласыз."
                        )

                        markCloudSessionNeedsRefreshForNotifications()
                        // Do not destroy the local account on a single 401. The refresh token/session may be rotating,
                        // the server may have just restarted, or the user may be temporarily offline. Local logout remains manual.
                        return ResponseDataModel<Response>(
                            message = expiredMessage,
                            payload = null,
                            negative = true,
                            httpStatusCode = response.status.value,
                            transportFailure = false
                        )
                    }

                    return decodedResponse
                } catch (throwable: Throwable) {
                    if (throwable is CancellationException) throw throwable
                    val responseException = throwable as? ResponseException
                    val status = responseException?.response?.status

                    if (status == HttpStatusCode.Unauthorized) {
                        val latestTokensAfterException = getStoredUserAuthTokens?.invoke()
                        if (!authRetryUsedForCandidate &&
                            tokensBeforeRequest != null &&
                            latestTokensAfterException != null &&
                            latestTokensAfterException.accessToken != tokensBeforeRequest.accessToken
                        ) {
                            httpClient.authProvider<BearerAuthProvider>()?.clearToken()
                            authRetryUsedForCandidate = true
                            continue@retrySameServer
                        }

                        if (!authRetryUsedForCandidate && refreshStoredAuthTokensOnceForNetworkRetry()) {
                            authRetryUsedForCandidate = true
                            continue@retrySameServer
                        }

                        recentAuthRefreshNonAuthFailureMessage()?.let { refreshFailureMessage ->
                            return authRefreshFailureResponseForNetworkRequest(refreshFailureMessage)
                        }

                        clearAuthRefreshNonAuthFailure()
                        val expiredMessage = localizedStringResourceMessage(
                            id = 91,
                            main = "Cloud session needs refresh. You remain signed in locally.",
                            ru = "Облачный сеанс нужно обновить. Вы остаётесь в аккаунте локально.",
                            kk = "Бұлттық сеансты жаңарту қажет. Сіз жергілікті түрде аккаунтта қаласыз."
                        )

                        markCloudSessionNeedsRefreshForNotifications()
                        // Do not destroy the local account on a single 401. The refresh token/session may be rotating,
                        // the server may have just restarted, or the user may be temporarily offline. Local logout remains manual.
                        return ResponseDataModel<Response>(
                            message = expiredMessage,
                            payload = null,
                            negative = true,
                            httpStatusCode = status.value,
                            transportFailure = false
                        )
                    }

                    markCloudTransportUnavailableForNotifications()
                    lastTransportFailureMessage = networkTransportFailureMessage(resolvedServerUrl, endpointUrl, throwable)
                    logNetworkAttempt("FAILED ${method.value} ${networkTargetUrl(resolvedServerUrl, endpointUrl)} ${networkFailureSummary(throwable)}")
                    break@retrySameServer
                }
            }
        }

        lastServerErrorResponse ?: ResponseDataModel<Response>(
            message = lastTransportFailureMessage ?: localizedStringResourceMessage(
                id = 1140,
                main = "Can’t reach AITA server. Check Wi‑Fi or server address.",
                ru = "Сервер AITA недоступен. Проверьте Wi‑Fi или адрес сервера.",
                kk = "AITA сервері қолжетімсіз. Wi‑Fi немесе сервер мекенжайын тексеріңіз."
            ),
            payload = null,
            negative = true,
            transportFailure = true
        )
    } finally {
        activeNetworkOperationsState.update { (it - 1).coerceAtLeast(0) }
    }
}


@PublishedApi
internal inline fun <reified Response> decodeNetworkResponseDataModel(
    rawBody: String,
    status: HttpStatusCode
): ResponseDataModel<Response> {
    if (!status.isSuccess() && (rawBody.isBlank() || status.value >= 500 || !rawBodyLooksLikeJson(rawBody))) {
        return genericHttpErrorNetworkResponseDataModel(status)
    }

    val genericEnvelope: ResponseDataModel<Response>? = runCatching<ResponseDataModel<Response>> {
        jsonBase.decodeFromString<GenericResponseDataModel>(rawBody).toResponseDataModel<Response>()
    }.getOrNull()

    if (genericEnvelope != null) {
        return genericEnvelope.copy(
            httpStatusCode = status.value,
            transportFailure = genericEnvelope.transportFailure || status.isAitaServerUnhealthyForClientBanner()
        )
    }

    val typedEnvelope: ResponseDataModel<Response>? = runCatching<ResponseDataModel<Response>> {
        jsonBase.decodeFromString<ResponseDataModel<Response>>(rawBody)
    }.getOrNull()

    if (typedEnvelope != null) {
        return typedEnvelope.copy(
            httpStatusCode = status.value,
            transportFailure = typedEnvelope.transportFailure || status.isAitaServerUnhealthyForClientBanner()
        )
    }

    val payloadResult: Result<Response> = runCatching {
        jsonBase.decodeFromString<Response>(rawBody)
    }

    if (payloadResult.isSuccess) {
        return ResponseDataModel<Response>(
            message = null,
            payload = payloadResult.getOrThrow(),
            negative = !status.isSuccess(),
            httpStatusCode = status.value,
            transportFailure = status.isAitaServerUnhealthyForClientBanner()
        )
    }

    return unreadableNetworkResponseDataModel<Response>(status, rawBody)
}

@PublishedApi
internal fun <Response> genericHttpErrorNetworkResponseDataModel(status: HttpStatusCode): ResponseDataModel<Response> {
    val message = when (status.value) {
        in 500..599 -> localizedStringResourceMessage(
            id = 223,
            main = "Server is unavailable or returned an internal error. Please try again after the server is restarted.",
            ru = "Сервер недоступен или вернул внутреннюю ошибку. Попробуйте снова после перезапуска сервера.",
            kk = "Сервер қолжетімсіз немесе ішкі қате қайтарды. Сервер қайта іске қосылғаннан кейін қайталап көріңіз."
        )
        else -> localizedStringResourceMessage(
            id = 223,
            main = "Server request failed. Please check the server connection and try again.",
            ru = "Запрос к серверу не выполнен. Проверьте соединение с сервером и попробуйте снова.",
            kk = "Серверге сұрау орындалмады. Сервер байланысын тексеріп, қайталап көріңіз."
        )
    }

    return ResponseDataModel(
        message = message,
        payload = null,
        negative = true,
        httpStatusCode = status.value,
        transportFailure = status.value >= 500
    )
}

@PublishedApi
internal fun <Response> nonAitaServerResponseDataModel(
    status: HttpStatusCode,
    rawBody: String
): ResponseDataModel<Response> {
    val message = localizedStringResourceMessage(
        id = 1140,
        main = "Can’t reach AITA server. Check Wi‑Fi or server address.",
        ru = "Сервер AITA недоступен. Проверьте Wi‑Fi или адрес сервера.",
        kk = "AITA сервері қолжетімсіз. Wi‑Fi немесе сервер мекенжайын тексеріңіз."
    )

    return ResponseDataModel(
        message = message,
        payload = null,
        negative = true,
        httpStatusCode = status.value,
        transportFailure = true
    )
}

@PublishedApi
internal fun <Response> unreadableNetworkResponseDataModel(
    status: HttpStatusCode,
    rawBody: String
): ResponseDataModel<Response> {
    return ResponseDataModel<Response>(
        message = localizedStringResourceMessage(
            id = 225,
            main = "Server response could not be read",
            ru = "Не удалось прочитать ответ сервера",
            kk = "Сервер жауабын оқу мүмкін болмады"
        ),
        payload = null,
        negative = true,
        httpStatusCode = status.value,
        transportFailure = status.value >= 500
    )
}

fun getStores() {
    if (!getStoresMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            getStoresMutex.withLock {
                val response = networkRequest<List<StoreDataModel>, Unit>(
                    HttpMethod.Get,
                    endpointUrl = globalAppConfigurationState.payloadValue.getStoresPath.first
                )

                if (!response.negative) {
                    val stores = response.payload!!
                    storesState.emit(DataState.Success(stores, response.message))

                    val activeStore = stores.findStoreOrBranch(activeStoreIdState.value)
                    when {
                        activeStoreIdState.value != null && activeStore == null ->
                            setActiveStoreId(null)

                        activeStoreIdState.value == null -> {
                            val settableStores = stores.settableActiveStores()
                            if (!activeStoreExplicitNoneIsSet() && settableStores.size == 1) setActiveStoreId(settableStores.first().id)
                        }
                    }
                }
            }
        }
}

fun addStore(store: StoreDataModel, onCompleted: ((DataState<StoreDataModel>) -> Unit)?) {
    if (!addStoreMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            addStoreMutex.withLock {
                val response = networkRequest<StoreDataModel, StoreDataModel>(
                    HttpMethod.Post,
                    endpointUrl = globalAppConfigurationState.payloadValue.addStoresPath.first,
                    body = store
                )

                if (response.negative) {
                    postInAppNotification(response.message, NotificationType.Negative)

                    onCompleted?.invoke(DataState.Empty())
                } else {
                    postInAppNotification(response.message, NotificationType.Positive)

                    storesState.emit(
                        DataState.Success(
                            mutableListOf<StoreDataModel>().also { newList ->
                                (storesState.value.value as? DataState.Success)?.payload?.run {
                                    newList.addAll(this)
                                }

                                storesState.payloadValue?.indexOfFirst { it.id == response.payload!!.id }?.takeIf { it != -1 }
                                    ?.let { index ->
                                        newList[index] = response.payload!!
                                    } ?: newList.add(response.payload!!)
                            }
                        )
                    )

                    getStores()

                    onCompleted?.invoke(DataState.Success(response.payload!!))
                }
            }
        }
}

fun updateStore(store: StoreDataModel, onCompleted: ((DataState<StoreDataModel>) -> Unit)?) {
    if (!updateStoreMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            updateStoreMutex.withLock {
                val response = networkRequest<StoreDataModel, StoreDataModel>(
                    HttpMethod.Put,
                    endpointUrl = globalAppConfigurationState.payloadValue.updateStoresPath.first,
                    body = store
                )

                if (response.negative) {
                    postInAppNotification(response.message, NotificationType.Negative)

                    onCompleted?.invoke(DataState.Empty())
                } else {
                    postInAppNotification(response.message, NotificationType.Positive)

                    storesState.emit(
                        DataState.Success(
                            mutableListOf<StoreDataModel>().also { newList ->
                                (storesState.value.value as? DataState.Success)?.payload?.run {
                                    newList.addAll(this)
                                }

                                newList.indexOfFirst { item -> item.id == store.id }
                                    .takeIf { index -> index != -1 }?.let { index ->
                                        newList[index] = response.payload!!
                                    }
                            }
                        )
                    )

                    onCompleted?.invoke(DataState.Success(response.payload!!))
                }
            }
        }
}

fun deleteStore(store: StoreDataModel, onCompleted: ((DataState<Unit>) -> Unit)? = null) {
    if (!deleteStoreMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            deleteStoreMutex.withLock {
                val response = networkRequest<Unit, String>(
                    HttpMethod.Delete,
                    endpointUrl = globalAppConfigurationState.payloadValue.deleteStoresPath.first,
                    body = store.id
                )

                if (response.negative) {
                    postInAppNotification(response.message, NotificationType.Negative)
                    onCompleted?.invoke(DataState.Empty())
                } else {
                    postInAppNotification(response.message, NotificationType.Positive)

                    val removedIds = buildSet {
                        add(store.id)
                        store.branches.forEach { add(it.id) }
                    }

                    storesState.emit(
                        DataState.Success(
                            storesState.payloadValue
                                .orEmpty()
                                .filterNot { it.id in removedIds || (it.parentStoreId?.let { parentId -> parentId in removedIds } == true) }
                        )
                    )

                    if (activeStoreIdState.value in removedIds) {
                        setActiveStoreId(null)
                    }

                    getStores()
                    onCompleted?.invoke(DataState.Success(Unit, response.message))
                }
            }
        }
}

fun setActiveStoreId(
    id: String?,
    syncServer: Boolean = true
) {
    GlobalScope.launch(Dispatchers.ourIo) {
        val normalizedId = id?.takeIf { it.isNotBlank() }
        val explicitNone = normalizedId == null && syncServer
        putLocalKv(KEY_ACTIVE_STORE_ID, normalizedId)
        putLocalKv(KEY_ACTIVE_STORE_EXPLICIT_NONE, if (explicitNone) "1" else null)
        activeStoreIdState.emit(normalizedId)
        logCloudConnectionDiagnostic(
            "active store set local id=${normalizedId.orEmpty()} syncServer=$syncServer explicitNone=$explicitNone"
        )

        if (syncServer && getStoredUserAuthTokens?.invoke() != null) {
            val response = networkRequest<Unit, String>(
                method = HttpMethod.Put,
                endpointUrl = "stores/active",
                body = normalizedId.orEmpty()
            )

            if (response.negative) {
                postInAppNotification(response.message, NotificationType.Negative, transient = false)
            } else {
                (userAccountState.payloadValue)?.let { account ->
                    val updated = account.copy(activeStoreId = normalizedId)
                    userAccountState.emit(DataState.Success(updated))
                    setStoredUserAccountDataModel?.invoke(updated)
                }
                logCloudConnectionDiagnostic("active store synced to server id=${normalizedId.orEmpty()}")
            }
        }
    }
}

val suppliersState = MutableDataStateFlow<List<SupplierDataModel>>(GlobalScope)

private val getSuppliersMutex = Mutex()
private val addSupplierMutex = Mutex()
private val updateSupplierMutex = Mutex()
private val deleteSupplierMutex = Mutex()

fun getSuppliers() {
    if (!getSuppliersMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            getSuppliersMutex.withLock {
                val response = networkRequest<List<SupplierDataModel>, Unit>(
                    HttpMethod.Get,
                    endpointUrl = globalAppConfigurationState.payloadValue.getSuppliersPath.first
                )

                if (!response.negative) {
                    suppliersState.emit(DataState.Success(response.payload!!, response.message))
                }
            }
        }
}


fun addSupplier(
    supplier: SupplierDataModel,
    onCompleted: ((DataState<SupplierDataModel>) -> Unit)? = null
) {
    if (!addSupplierMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            addSupplierMutex.withLock {
                val response = networkRequest<SupplierDataModel, SupplierDataModel>(
                    method = HttpMethod.Post,
                    endpointUrl = globalAppConfigurationState.payloadValue.addSupplierPath.first,
                    body = supplier
                )

                if (response.negative || response.payload == null) {
                    postInAppNotification(response.message, NotificationType.Negative)
                    onCompleted?.invoke(DataState.Empty(response.message))
                } else {
                    val supplier = response.payload!!
                    suppliersState.emit(DataState.Success(suppliersState.payloadValue.orEmpty().upsertById(supplier), response.message))
                    onCompleted?.invoke(DataState.Success(supplier, response.message))
                }
            }
        }
}

fun updateSupplier(
    supplier: SupplierDataModel,
    onCompleted: ((DataState<SupplierDataModel>) -> Unit)? = null
) {
    if (!updateSupplierMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            updateSupplierMutex.withLock {
                val response = networkRequest<SupplierDataModel, SupplierDataModel>(
                    method = HttpMethod.Put,
                    endpointUrl = globalAppConfigurationState.payloadValue.updateSupplierPath.first,
                    body = supplier
                )

                if (response.negative || response.payload == null) {
                    postInAppNotification(response.message, NotificationType.Negative)
                    onCompleted?.invoke(DataState.Empty(response.message))
                } else {
                    val supplier = response.payload!!
                    suppliersState.emit(DataState.Success(suppliersState.payloadValue.orEmpty().upsertById(supplier), response.message))
                    onCompleted?.invoke(DataState.Success(supplier, response.message))
                }
            }
        }
}

fun deleteSupplier(
    supplierId: String,
    onCompleted: ((DataState<String>) -> Unit)? = null
) {
    if (!deleteSupplierMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            deleteSupplierMutex.withLock {
                val response = networkRequest<String, String>(
                    method = HttpMethod.Delete,
                    endpointUrl = globalAppConfigurationState.payloadValue.deleteSupplierPath.first,
                    body = supplierId
                )

                if (response.negative) {
                    postInAppNotification(response.message, NotificationType.Negative)
                    onCompleted?.invoke(DataState.Empty(response.message))
                } else {
                    suppliersState.emit(DataState.Success(suppliersState.payloadValue.orEmpty().filterNot { it.id == supplierId }, response.message))
                    onCompleted?.invoke(DataState.Success(supplierId, response.message))
                }
            }
        }
}

fun getGenericGoodsItems(
    barcode: String? = null,
    query: String? = null,
    categoryIds: List<String> = emptyList(),
    limit: Int = 80,
    offset: Int = 0,
    updateSharedState: Boolean = true,
    appendToSharedState: Boolean = false
): Flow<DataState<List<GenericGoodsItemDataModel>>> {
    return flow {
        getGenericGoodsItemsMutex.withLock {
            val cleanBarcode = barcode
                ?.trim()
                ?.takeIf { it.isNotBlank() }
            val cleanQuery = query
                ?.trim()
                ?.takeIf { it.isNotBlank() }
            val cleanCategoryIds = categoryIds
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
            val cleanLimit = limit.coerceIn(1, 200)
            val cleanOffset = offset.coerceAtLeast(0)

            val response = networkRequest<List<GenericGoodsItemDataModel>, Unit>(
                method = HttpMethod.Get,
                endpointUrl = globalAppConfigurationState.payloadValue.getGenericGoodsItemsPath.first,
                headers = buildMap {
                    cleanBarcode?.let { put("barcode", it) }
                },
                query = buildMap {
                    cleanQuery?.let { put("q", it) }
                    if (cleanCategoryIds.isNotEmpty()) put("categoryIds", cleanCategoryIds.joinToString(","))
                    put("limit", cleanLimit)
                    put("offset", cleanOffset)
                }
            )

            val state: DataState<List<GenericGoodsItemDataModel>> = if (!response.negative) {
                DataState.Success(response.payload.orEmpty(), response.message)
            } else {
                DataState.Empty(response.message)
            }

            if (updateSharedState && state is DataState.Success) {
                val mergedPayload = if (appendToSharedState || cleanOffset > 0 || cleanBarcode != null || cleanQuery != null || cleanCategoryIds.isNotEmpty()) {
                    val freshIds = state.payload.map { it.id }.toSet()
                    (genericGoodsItemsState.payloadValue.orEmpty().filterNot { it.id in freshIds } + state.payload).distinctBy { it.id }
                } else {
                    state.payload
                }
                genericGoodsItemsState.emit(DataState.Success(mergedPayload, state.message))
            }
            emit(state)
        }
    }
}

fun refreshGenericGoodsItems(
    barcode: String? = null,
    query: String? = null,
    categoryIds: List<String> = emptyList(),
    limit: Int = 80,
    offset: Int = 0,
    appendToSharedState: Boolean = false
) {
    if (!getGenericGoodsItemsMutex.isLocked) {
        GlobalScope.launch(Dispatchers.ourIo) {
            getGenericGoodsItems(
                barcode = barcode,
                query = query,
                categoryIds = categoryIds,
                limit = limit,
                offset = offset,
                updateSharedState = true,
                appendToSharedState = appendToSharedState
            ).collect()
        }
    }
}

fun getGenericGoodsCategories() {
    if (!getGenericGoodsCategoriesMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            getGenericGoodsCategoriesMutex.withLock {
                val response = networkRequest<List<GenericGoodsCategoryDataModel>, Unit>(
                    method = HttpMethod.Get,
                    endpointUrl = globalAppConfigurationState.payloadValue.getGenericGoodsCategoriesPath.first
                )

                if (!response.negative)
                    genericGoodsCategoriesState.emit(DataState.Success(response.payload!!, response.message))
            }
        }
}

fun getCartState(transactionTypeIndex: Int, clientId: Int): StateFlow<List<GoodsItemInCartDataModel>> {
    return when (transactionTypeIndex) {
        0 -> {
            when (clientId) {
                0 -> cartTransactionType0_clientId0_state
                1 -> cartTransactionType0_clientId1_state
                2 -> cartTransactionType0_clientId2_state
                3 -> cartTransactionType0_clientId3_state
                else -> cartTransactionType0_clientId4_state
            }
        }

        1 -> {
            when (clientId) {
                0 -> cartTransactionType1_clientId0_state
                1 -> cartTransactionType1_clientId1_state
                2 -> cartTransactionType1_clientId2_state
                3 -> cartTransactionType1_clientId3_state
                else -> cartTransactionType1_clientId4_state
            }
        }

        else -> {
            when (clientId) {
                0 -> cartTransactionType2_clientId0_state
                1 -> cartTransactionType2_clientId1_state
                2 -> cartTransactionType2_clientId2_state
                3 -> cartTransactionType2_clientId3_state
                else -> cartTransactionType2_clientId4_state
            }
        }
    }
}

fun getStock(storeId: String) {
    if (!getStockMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            getStockMutex.withLock {
                val response = networkRequest<List<GoodsItemDataModel>, Unit>(
                    HttpMethod.Get,
                    endpointUrl = globalAppConfigurationState.payloadValue.getStockPath.first,
                    headers = mapOf("store_id" to storeId)
                )

                if (!response.negative)
                    stockState.emit(DataState.Success(response.payload!!, response.message))
            }
        }
}

fun getParentStoreStock(
    storeId: String,
    query: String? = null,
    limit: Int = 80,
    offset: Int = 0,
    updateSharedState: Boolean = true,
    appendToSharedState: Boolean = false
): Flow<DataState<List<GoodsItemDataModel>>> {
    return flow {
        getParentStoreStockMutex.withLock {
            val cleanStoreId = storeId.trim()
            if (cleanStoreId.isBlank()) {
                emit(DataState.Empty())
                return@withLock
            }

            val cleanQuery = query
                ?.trim()
                ?.takeIf { it.isNotBlank() }
            val cleanLimit = limit.coerceIn(1, globalAppConfigurationState.payloadValue.pagingMaxPageSize)
            val cleanOffset = offset.coerceAtLeast(0)

            val response = networkRequest<List<GoodsItemDataModel>, Unit>(
                method = HttpMethod.Get,
                endpointUrl = globalAppConfigurationState.payloadValue.getParentStoreStockPath.first,
                headers = mapOf("store_id" to cleanStoreId),
                query = buildMap {
                    cleanQuery?.let { put("q", it) }
                    put("limit", cleanLimit)
                    put("offset", cleanOffset)
                }
            )

            val state: DataState<List<GoodsItemDataModel>> = if (!response.negative) {
                DataState.Success(response.payload.orEmpty(), response.message)
            } else {
                DataState.Empty(response.message)
            }

            if (updateSharedState && state is DataState.Success) {
                val mergedPayload = if (appendToSharedState || cleanOffset > 0 || cleanQuery != null) {
                    val freshIds = state.payload.map { it.id }.toSet()
                    (parentStoreStockState.payloadValue.orEmpty().filterNot { it.id in freshIds } + state.payload).distinctBy { it.id }
                } else {
                    state.payload
                }
                parentStoreStockState.emit(DataState.Success(mergedPayload, state.message))
            }

            emit(state)
        }
    }
}

fun refreshParentStoreStock(
    storeId: String,
    query: String? = null,
    limit: Int = 80,
    offset: Int = 0,
    appendToSharedState: Boolean = false
) {
    if (!getParentStoreStockMutex.isLocked) {
        GlobalScope.launch(Dispatchers.ourIo) {
            getParentStoreStock(
                storeId = storeId,
                query = query,
                limit = limit,
                offset = offset,
                updateSharedState = true,
                appendToSharedState = appendToSharedState
            ).collect()
        }
    }
}

fun updateGoodsItem(
    goodsItem: GoodsItemDataModel,
    onCompleted: ((DataState<GoodsItemDataModel>) -> Unit)?
) {
    if (!updateGoodsItemMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            updateGoodsItemMutex.withLock {
                val response = networkRequest<GoodsItemDataModel, GoodsItemDataModel>(
                    HttpMethod.Put,
                    endpointUrl = globalAppConfigurationState.payloadValue.updateGoodsItemPath.first,
                    body = goodsItem
                )

                if (response.negative) {
                    postInAppNotification(response.message, NotificationType.Negative)

                    onCompleted?.invoke(DataState.Empty())
                } else {
                    postInAppNotification(response.message, NotificationType.Positive)

                    stockState.emit(
                        DataState.Success(
                            mutableListOf<GoodsItemDataModel>().also { newList ->
                                (stockState.value.value as? DataState.Success)?.payload?.run {
                                    newList.addAll(this)
                                }

                                val existingIndex = newList.indexOfFirst { it.id == response.payload!!.id }
                                if (existingIndex >= 0) {
                                    newList[existingIndex] = response.payload!!
                                } else {
                                    newList.add(response.payload!!)
                                }
                            }
                        )
                    )

                    goodsItem.storeId.takeIf { it.isNotBlank() }?.let { storeId ->
                        getStock(storeId)
                        refreshParentStoreStock(storeId, limit = 32, appendToSharedState = false)
                    }

                    onCompleted?.invoke(DataState.Success(response.payload!!))
                }
            }
        }
}

fun addGoodsItem(goodsItem: GoodsItemDataModel, onCompleted: ((DataState<GoodsItemDataModel>) -> Unit)?) {
    if (!addGoodsItemMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            addGoodsItemMutex.withLock {
                val response = networkRequest<GoodsItemDataModel, GoodsItemDataModel>(
                    HttpMethod.Post,
                    endpointUrl = globalAppConfigurationState.payloadValue.addGoodsItemPath.first,
                    body = goodsItem
                )

                if (response.negative) {
                    postInAppNotification(response.message, NotificationType.Negative)

                    onCompleted?.invoke(DataState.Empty())
                } else {
                    postInAppNotification(response.message, NotificationType.Positive)

                    stockState.emit(
                        DataState.Success(
                            mutableListOf<GoodsItemDataModel>().also { newList ->
                                (stockState.value.value as? DataState.Success)?.payload?.run {
                                    newList.addAll(this)
                                }

                                newList.add(response.payload!!)
                            }
                        )
                    )

                    goodsItem.storeId.takeIf { it.isNotBlank() }?.let { storeId ->
                        getStock(storeId)
                        refreshParentStoreStock(storeId, limit = 32, appendToSharedState = false)
                    }

                    onCompleted?.invoke(DataState.Success(response.payload!!))
                }
            }
        }
}

fun deleteGoodsItem(id: String, storeId: String, onCompleted: (() -> Unit)?) {
    if (!deleteGoodsItemMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            deleteGoodsItemMutex.withLock {
                val response = networkRequest<String, String>(
                    HttpMethod.Delete,
                    endpointUrl = globalAppConfigurationState.payloadValue.deleteGoodsItemPath.first,
                    body = id,
                    headers = mapOf("store_id" to storeId)
                )

                if (response.negative) {
                    postInAppNotification(response.message, NotificationType.Negative)

                    onCompleted?.invoke()
                } else {
                    postInAppNotification(response.message, NotificationType.Positive)

                    stockState.payloadValue?.run {
                        stockState.emit(DataState.Success(filter { it.id != response.payload }))
                    }

                    deleteCartItemById(id)

                    onCompleted?.invoke()
                }
            }
        }
}

fun getStockBatches(storeId: String) {
    if (!getStockBatchesMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            getStockBatchesMutex.withLock {
                val response = networkRequest<List<GoodsBatchDataModel>, Unit>(
                    HttpMethod.Get,
                    endpointUrl = globalAppConfigurationState.payloadValue.getStockBatchesPath.first,
                    headers = mapOf("store_id" to storeId)
                )

                if (!response.negative)
                    stockBatchesState.emit(DataState.Success(response.payload!!, response.message))
            }
        }
}

fun getStockItemBranchAvailability(
    storeId: String,
    goodsItemId: String,
    onCompleted: ((DataState<StockItemBranchAvailabilityDataModel>) -> Unit)? = null
) {
    if (!getStockItemBranchAvailabilityMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            getStockItemBranchAvailabilityMutex.withLock {
                val response = networkRequest<StockItemBranchAvailabilityDataModel, Unit>(
                    method = HttpMethod.Get,
                    endpointUrl = globalAppConfigurationState.payloadValue.getStockItemBranchAvailabilityPath.first,
                    headers = mapOf(
                        "store_id" to storeId,
                        "goods_item_id" to goodsItemId
                    )
                )

                if (response.negative || response.payload == null) {
                    postInAppNotification(response.message, NotificationType.Negative)
                    onCompleted?.invoke(DataState.Empty(response.message))
                } else {
                    stockItemBranchAvailabilityState.emit(DataState.Success(response.payload, response.message))
                    onCompleted?.invoke(DataState.Success(response.payload, response.message))
                }
            }
        }
}

fun moveStockBatchBetweenStores(
    request: StockBatchMoveRequestDataModel,
    onCompleted: ((DataState<StockBatchMoveResultDataModel>) -> Unit)? = null
) {
    if (!moveStockBatchMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            moveStockBatchMutex.withLock {
                val response = networkRequest<StockBatchMoveResultDataModel, StockBatchMoveRequestDataModel>(
                    method = HttpMethod.Post,
                    endpointUrl = globalAppConfigurationState.payloadValue.moveStockBatchPath.first,
                    body = request.copy(actorStoreId = activeStoreIdState.value ?: request.actorStoreId ?: request.sourceStoreId),
                    headers = mapOf("store_id" to (activeStoreIdState.value ?: request.sourceStoreId))
                )

                if (response.negative || response.payload == null) {
                    postInAppNotification(response.message, NotificationType.Negative)
                    onCompleted?.invoke(DataState.Empty(response.message))
                } else {
                    stockBatchMoveResultState.emit(DataState.Success(response.payload, response.message))
                    stockItemBranchAvailabilityState.emit(DataState.Success(response.payload.availability, response.message))

                    val activeStoreId = activeStoreIdState.value
                    if (!activeStoreId.isNullOrBlank()) {
                        getStock(activeStoreId)
                        getStockBatches(activeStoreId)
                    }

                    postInAppNotification(response.message, NotificationType.Positive)
                    onCompleted?.invoke(DataState.Success(response.payload, response.message))
                }
            }
        }
}

fun decideStockBatchMove(
    request: StockBatchMoveDecisionRequestDataModel,
    onCompleted: ((DataState<StockBatchMoveResultDataModel>) -> Unit)? = null
) {
    if (!moveStockBatchMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            moveStockBatchMutex.withLock {
                val response = networkRequest<StockBatchMoveResultDataModel, StockBatchMoveDecisionRequestDataModel>(
                    method = HttpMethod.Post,
                    endpointUrl = globalAppConfigurationState.payloadValue.decideStockBatchMovePath.first,
                    body = request,
                    headers = mapOf("store_id" to (activeStoreIdState.value ?: ""))
                )

                if (response.negative || response.payload == null) {
                    postInAppNotification(response.message, NotificationType.Negative)
                    onCompleted?.invoke(DataState.Empty(response.message))
                } else {
                    stockBatchMoveResultState.emit(DataState.Success(response.payload, response.message))
                    stockItemBranchAvailabilityState.emit(DataState.Success(response.payload.availability, response.message))

                    val activeStoreId = activeStoreIdState.value
                    if (!activeStoreId.isNullOrBlank()) {
                        getStock(activeStoreId)
                        getStockBatches(activeStoreId)
                    }

                    postInAppNotification(response.message, NotificationType.Positive)
                    onCompleted?.invoke(DataState.Success(response.payload, response.message))
                }
            }
        }
}

fun updateGoodsBatches(
    goodsBatches: List<GoodsBatchDataModel>,
    onCompleted: ((DataState<List<GoodsBatchDataModel>>) -> Unit)?
) {
    if (!updateGoodsItemMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            updateGoodsItemMutex.withLock {
                val response = networkRequest<List<GoodsBatchDataModel>, List<GoodsBatchDataModel>>(
                    HttpMethod.Put,
                    endpointUrl = globalAppConfigurationState.payloadValue.updateStockBatchPath.first,
                    body = goodsBatches
                )

                if (response.negative) {
                    postInAppNotification(response.message, NotificationType.Negative)

                    onCompleted?.invoke(DataState.Empty())
                } else {
                    postInAppNotification(response.message, NotificationType.Positive)

                    stockBatchesState.emit(
                        DataState.Success(
                            mutableListOf<GoodsBatchDataModel>().also { newList ->
                                (stockBatchesState.value.value as? DataState.Success)?.payload?.run {
                                    newList.addAll(this)
                                }

                                response.payload!!.forEach { item ->
                                    val index = newList.indexOfFirst { it.id == item.id }

                                    if (index >= 0) {
                                        newList[index] = item
                                    } else {
                                        newList.add(item)
                                    }
                                }
                            }
                        )
                    )

                    onCompleted?.invoke(DataState.Success(response.payload!!))
                }
            }
        }
}

fun addGoodsBatches(
    goodsBatches: List<GoodsBatchDataModel>,
    onCompleted: ((DataState<List<GoodsBatchDataModel>>) -> Unit)?
) {
    if (!addGoodsItemMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            addGoodsItemMutex.withLock {
                val response = networkRequest<List<GoodsBatchDataModel>, List<GoodsBatchDataModel>>(
                    HttpMethod.Post,
                    endpointUrl = globalAppConfigurationState.payloadValue.addStockBatchPath.first,
                    body = goodsBatches
                )

                if (response.negative) {
                    postInAppNotification(response.message, NotificationType.Negative)

                    onCompleted?.invoke(DataState.Empty())
                } else {
                    postInAppNotification(response.message, NotificationType.Positive)

                    stockBatchesState.emit(
                        DataState.Success(
                            mutableListOf<GoodsBatchDataModel>().also { newList ->
                                (stockBatchesState.value.value as? DataState.Success)?.payload?.run {
                                    newList.addAll(this)
                                }

                                newList.addAll(response.payload!!)
                            }
                        )
                    )

                    onCompleted?.invoke(DataState.Success(response.payload!!))
                }
            }
        }
}

fun deleteGoodsBatches(ids: List<String>, storeId: String, onCompleted: (() -> Unit)?) {
    if (!deleteGoodsItemMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            deleteGoodsItemMutex.withLock {
                val response = networkRequest<List<String>, List<String>>(
                    HttpMethod.Delete,
                    endpointUrl = globalAppConfigurationState.payloadValue.deleteStockBatchPath.first,
                    body = ids,
                    headers = mapOf("store_id" to storeId)
                )

                if (response.negative) {
                    postInAppNotification(response.message, NotificationType.Negative)

                    onCompleted?.invoke()
                } else {
                    postInAppNotification(response.message, NotificationType.Positive)

                    val deletedIds = response.payload.orEmpty()

                    stockBatchesState.emit(
                        DataState.Success(
                            mutableListOf<GoodsBatchDataModel>().also { newList ->
                                stockBatchesState.payloadValue?.let {
                                    newList.addAll(it)
                                    newList.removeAll { item -> item.id in deletedIds }
                                }
                            },
                            response.message
                        )
                    )

//          stockBatchesState.emit(
//            DataState.Success(
//              mutableListOf<GoodsBatchDataModel>().also { newList ->
//                (stockBatchesState.value.value as? DataState.Success)?.payload?.let {
//                  newList.addAll(it)
//                  newList.removeAll { item -> item.id == response.payload }
//                }
//              }
//            )
//          )

                    onCompleted?.invoke()
                }
            }
        }
}

fun addGoodsItems(
    goodsItems: List<GoodsItemDataModel>,
    onCompleted: ((DataState<List<GoodsItemDataModel>>) -> Unit)? = null
) {
    val added = mutableListOf<GoodsItemDataModel>()

    fun addNext(index: Int) {
        if (index > goodsItems.lastIndex) {
            onCompleted?.invoke(DataState.Success(added))
            return
        }

        addGoodsItem(goodsItems[index]) { state ->
            if (state is DataState.Success) {
                added += state.payload
                addNext(index + 1)
            } else {
                onCompleted?.invoke(DataState.Empty())
            }
        }
    }

    addNext(0)
}

fun updateGoodsItems(
    goodsItems: List<GoodsItemDataModel>,
    onCompleted: ((DataState<List<GoodsItemDataModel>>) -> Unit)? = null
) {
    val updated = mutableListOf<GoodsItemDataModel>()

    fun updateNext(index: Int) {
        if (index > goodsItems.lastIndex) {
            onCompleted?.invoke(DataState.Success(updated))
            return
        }

        updateGoodsItem(goodsItems[index]) { state ->
            if (state is DataState.Success) {
                updated += state.payload
                updateNext(index + 1)
            } else {
                onCompleted?.invoke(DataState.Empty())
            }
        }
    }

    updateNext(0)
}


















































@kotlinx.serialization.Serializable
data class AccountSubscriptionStatusDataModel(
    val id: Long,
    val userId: Long,
    val balance: Double,
    val subscriptionPlanId: Long,
    val lastChargeTime: Long,
    val nextChargeTime: Long
)

@kotlinx.serialization.Serializable
data class ActivationHistoryEntryDataModel(
    val id: String,
    val type: Int,
    val time: Long
)

@kotlinx.serialization.Serializable
data class AppLanguageDataModel(
    val language: String,
    val name: List<LocalizedStringDataModel>,
    val flagDrawablePath: String
)
const val CASH_REGISTER_EVENT_SALE_CASH_IN = "sale_cash_in"
const val CASH_REGISTER_EVENT_RETURN_CASH_OUT = "return_cash_out"
const val CASH_REGISTER_EVENT_EXTRACTION = "extraction"
const val CASH_REGISTER_EVENT_MANUAL_ADJUSTMENT = "manual_adjustment"

const val WORKER_REQUEST_DIRECTION_USER_TO_STORE = "user_to_store"
const val WORKER_REQUEST_DIRECTION_STORE_TO_USER = "store_to_user"
const val WORKER_REQUEST_DIRECTION_STORE_REMOVAL_TO_USER = "store_removal_to_user"
const val WORKER_REQUEST_STATUS_PENDING = "pending"
const val WORKER_REQUEST_STATUS_INVITED = "invited"
const val WORKER_REQUEST_STATUS_ACCEPTED = "accepted"
const val WORKER_REQUEST_STATUS_DECLINED = "declined"

const val WORKER_ROLE_OWNER = "owner"
const val WORKER_ROLE_ADMIN = "admin"
const val WORKER_ROLE_STANDARD = "standard"

const val STORE_PERMISSION_SALE_TRANSACTION = "sale_transaction"
const val STORE_PERMISSION_RETURN_TRANSACTION = "return_transaction"
const val STORE_PERMISSION_SUPPLY_TRANSACTION = "supply_transaction"
const val STORE_PERMISSION_TRANSACTION_HISTORY_VIEW = "transaction_history_view"
const val STORE_PERMISSION_CASH_REGISTER_VIEW = "cash_register_view"
const val STORE_PERMISSION_CASH_REGISTER_EXTRACT = "cash_register_extract"

const val STORE_PERMISSION_STOCK_READ = "stock_read"
const val STORE_PERMISSION_STOCK_WRITE = "stock_write"
const val STORE_PERMISSION_STOCK_ITEM_CREATE = "stock_item_create"
const val STORE_PERMISSION_STOCK_ITEM_EDIT = "stock_item_edit"
const val STORE_PERMISSION_STOCK_ITEM_DELETE = "stock_item_delete"
const val STORE_PERMISSION_STOCK_BATCH_CREATE = "stock_batch_create"
const val STORE_PERMISSION_STOCK_BATCH_EDIT = "stock_batch_edit"
const val STORE_PERMISSION_STOCK_BATCH_DELETE = "stock_batch_delete"
const val STORE_PERMISSION_STOCK_BATCH_MOVE = "stock_batch_move"
const val STORE_PERMISSION_STOCK_BATCH_TRANSFER_DECIDE = "stock_batch_transfer_decide"
const val STORE_PERMISSION_STOCK_BATCH_SET_ACTIVE_SHELF = "stock_batch_set_active_shelf"
const val STORE_PERMISSION_STOCK_PROMOTIONS_MANAGE = "stock_promotions_manage"

const val STORE_PERMISSION_SUPPLIERS_VIEW = "suppliers_view"
const val STORE_PERMISSION_SUPPLIERS_MANAGE = "suppliers_manage"
const val STORE_PERMISSION_SUPPLIER_PRICES_MANAGE = "supplier_prices_manage"
const val STORE_PERMISSION_SUPPLIER_ORDERS_VIEW = "supplier_orders_view"
const val STORE_PERMISSION_SUPPLIER_ORDERS_MANAGE = "supplier_orders_manage"
const val STORE_PERMISSION_SUPPLIER_ORDERS_RECEIVE = "supplier_orders_receive"

const val STORE_PERMISSION_DEBTORS_VIEW = "debtors_view"
const val STORE_PERMISSION_DEBTORS_MANAGE = "debtors_manage"
const val STORE_PERMISSION_DEBTOR_PAYMENTS_MANAGE = "debtor_payments_manage"

const val STORE_PERMISSION_ANALYTICS_VIEW = "analytics_view"
const val STORE_PERMISSION_LOGS_VIEW = "logs_view"
const val STORE_PERMISSION_WORKERS_VIEW = "workers_view"
const val STORE_PERMISSION_WORKERS_MANAGE = "workers_manage"
const val STORE_PERMISSION_WORKERS_INVITE = "workers_invite"
const val STORE_PERMISSION_WORKERS_DECIDE_REQUESTS = "workers_decide_requests"
const val STORE_PERMISSION_WORKERS_EDIT_PERMISSIONS = "workers_edit_permissions"
const val STORE_PERMISSION_WORKERS_REMOVE = "workers_remove"
const val STORE_PERMISSION_WORKER_ROLE_TEMPLATES_MANAGE = "worker_role_templates_manage"
const val STORE_PERMISSION_STORE_MANAGE = "store_manage"
const val STORE_PERMISSION_BRANCHES_MANAGE = "branches_manage"
const val STORE_PERMISSION_SUBSCRIPTION_MANAGE = "subscription_manage"

const val OPERATION_LOG_SCOPE_CURRENT = "current"
const val OPERATION_LOG_SCOPE_ROOT = "root"
const val OPERATION_LOG_ENTITY_STORE = "store"
const val OPERATION_LOG_ENTITY_WORKER = "worker"
const val OPERATION_LOG_ENTITY_WORKSHIFT = "workshift"
const val OPERATION_LOG_ENTITY_STOCK_ITEM = "stock_item"
const val OPERATION_LOG_ENTITY_STOCK_BATCH = "stock_batch"
const val OPERATION_LOG_ENTITY_TRANSACTION = "transaction"
const val OPERATION_LOG_ENTITY_CASH_REGISTER = "cash_register"
const val OPERATION_LOG_ENTITY_SUPPLIER = "supplier"
const val OPERATION_LOG_ENTITY_SUBSCRIPTION = "subscription"
const val OPERATION_LOG_ENTITY_FINANCE = "finance"
const val OPERATION_LOG_ACTION_CREATED = "created"
const val OPERATION_LOG_ACTION_UPDATED = "updated"
const val OPERATION_LOG_ACTION_DELETED = "deleted"
const val OPERATION_LOG_ACTION_COMPLETED = "completed"
const val OPERATION_LOG_ACTION_EXTRACTED = "extracted"
const val OPERATION_LOG_ACTION_STARTED = "started"
const val OPERATION_LOG_ACTION_ENDED = "ended"
const val OPERATION_LOG_ACTION_ACCEPTED = "accepted"
const val OPERATION_LOG_ACTION_DECLINED = "declined"
const val OPERATION_LOG_ACTION_INVITED = "invited"
const val OPERATION_LOG_ACTION_MOVED = "moved"


val ALL_STORE_PERMISSION_IDS = listOf(
    STORE_PERMISSION_SALE_TRANSACTION,
    STORE_PERMISSION_RETURN_TRANSACTION,
    STORE_PERMISSION_SUPPLY_TRANSACTION,
    STORE_PERMISSION_TRANSACTION_HISTORY_VIEW,
    STORE_PERMISSION_CASH_REGISTER_VIEW,
    STORE_PERMISSION_CASH_REGISTER_EXTRACT,
    STORE_PERMISSION_STOCK_READ,
    STORE_PERMISSION_STOCK_ITEM_CREATE,
    STORE_PERMISSION_STOCK_ITEM_EDIT,
    STORE_PERMISSION_STOCK_ITEM_DELETE,
    STORE_PERMISSION_STOCK_BATCH_CREATE,
    STORE_PERMISSION_STOCK_BATCH_EDIT,
    STORE_PERMISSION_STOCK_BATCH_DELETE,
    STORE_PERMISSION_STOCK_BATCH_MOVE,
    STORE_PERMISSION_STOCK_BATCH_TRANSFER_DECIDE,
    STORE_PERMISSION_STOCK_BATCH_SET_ACTIVE_SHELF,
    STORE_PERMISSION_STOCK_PROMOTIONS_MANAGE,
    STORE_PERMISSION_SUPPLIERS_VIEW,
    STORE_PERMISSION_SUPPLIERS_MANAGE,
    STORE_PERMISSION_SUPPLIER_PRICES_MANAGE,
    STORE_PERMISSION_SUPPLIER_ORDERS_VIEW,
    STORE_PERMISSION_SUPPLIER_ORDERS_MANAGE,
    STORE_PERMISSION_SUPPLIER_ORDERS_RECEIVE,
    STORE_PERMISSION_DEBTORS_VIEW,
    STORE_PERMISSION_DEBTORS_MANAGE,
    STORE_PERMISSION_DEBTOR_PAYMENTS_MANAGE,
    STORE_PERMISSION_ANALYTICS_VIEW,
    STORE_PERMISSION_LOGS_VIEW,
    STORE_PERMISSION_WORKERS_VIEW,
    STORE_PERMISSION_WORKERS_INVITE,
    STORE_PERMISSION_WORKERS_DECIDE_REQUESTS,
    STORE_PERMISSION_WORKERS_EDIT_PERMISSIONS,
    STORE_PERMISSION_WORKERS_REMOVE,
    STORE_PERMISSION_WORKER_ROLE_TEMPLATES_MANAGE,
    STORE_PERMISSION_STORE_MANAGE,
    STORE_PERMISSION_BRANCHES_MANAGE,
    STORE_PERMISSION_SUBSCRIPTION_MANAGE
)

val LEGACY_STORE_PERMISSION_IDS = listOf(
    STORE_PERMISSION_STOCK_WRITE,
    STORE_PERMISSION_WORKERS_MANAGE
)

val STORE_PERMISSION_LEGACY_EXPANSIONS = mapOf(
    STORE_PERMISSION_STOCK_WRITE to listOf(
        STORE_PERMISSION_STOCK_READ,
        STORE_PERMISSION_STOCK_ITEM_CREATE,
        STORE_PERMISSION_STOCK_ITEM_EDIT,
        STORE_PERMISSION_STOCK_ITEM_DELETE,
        STORE_PERMISSION_STOCK_BATCH_CREATE,
        STORE_PERMISSION_STOCK_BATCH_EDIT,
        STORE_PERMISSION_STOCK_BATCH_DELETE,
        STORE_PERMISSION_STOCK_BATCH_MOVE,
        STORE_PERMISSION_STOCK_BATCH_TRANSFER_DECIDE,
        STORE_PERMISSION_STOCK_BATCH_SET_ACTIVE_SHELF,
        STORE_PERMISSION_STOCK_PROMOTIONS_MANAGE,
        STORE_PERMISSION_SUPPLIERS_VIEW,
        STORE_PERMISSION_SUPPLIER_PRICES_MANAGE,
        STORE_PERMISSION_SUPPLIER_ORDERS_VIEW,
        STORE_PERMISSION_SUPPLIER_ORDERS_MANAGE,
        STORE_PERMISSION_SUPPLIER_ORDERS_RECEIVE
    ),
    STORE_PERMISSION_WORKERS_MANAGE to listOf(
        STORE_PERMISSION_WORKERS_VIEW,
        STORE_PERMISSION_WORKERS_INVITE,
        STORE_PERMISSION_WORKERS_DECIDE_REQUESTS,
        STORE_PERMISSION_WORKERS_EDIT_PERMISSIONS,
        STORE_PERMISSION_WORKERS_REMOVE,
        STORE_PERMISSION_WORKER_ROLE_TEMPLATES_MANAGE
    )
)

val STORE_PERMISSION_IDS_ACCEPTED_BY_API = (ALL_STORE_PERMISSION_IDS + LEGACY_STORE_PERMISSION_IDS).distinct()

fun normalizeStorePermissionIds(input: Iterable<String>): List<String> {
    val result = linkedSetOf<String>()
    input.forEach { rawPermissionId ->
        val permissionId = rawPermissionId.trim()
        if (permissionId in ALL_STORE_PERMISSION_IDS) result += permissionId
        STORE_PERMISSION_LEGACY_EXPANSIONS[permissionId].orEmpty().forEach { result += it }
    }
    return ALL_STORE_PERMISSION_IDS.filter { it in result }
}

val STANDARD_STORE_PERMISSION_IDS = listOf(
    STORE_PERMISSION_SALE_TRANSACTION,
    STORE_PERMISSION_RETURN_TRANSACTION,
    STORE_PERMISSION_TRANSACTION_HISTORY_VIEW,
    STORE_PERMISSION_CASH_REGISTER_VIEW,
    STORE_PERMISSION_STOCK_READ,
    STORE_PERMISSION_DEBTORS_VIEW,
    STORE_PERMISSION_DEBTOR_PAYMENTS_MANAGE
)

fun defaultStorePermissionsForRole(roleId: String): List<String> {
    return when (roleId) {
        WORKER_ROLE_ADMIN -> ALL_STORE_PERMISSION_IDS
        WORKER_ROLE_OWNER -> ALL_STORE_PERMISSION_IDS
        else -> STANDARD_STORE_PERMISSION_IDS
    }
}

@kotlinx.serialization.Serializable
data class StoreCashRegisterDataModel(
    val storeId: String,
    val currentAmount: Double = 0.0,
    val currencyCode: String = "KZT",
    val updatedAtMillis: Long = 0L
)

@kotlinx.serialization.Serializable
data class CashRegisterEventDataModel(
    val id: String = "",
    val storeId: String = "",
    val userId: String = "",
    val userName: String = "",
    val type: String = CASH_REGISTER_EVENT_MANUAL_ADJUSTMENT,
    val amount: Double = 0.0,
    val balanceBefore: Double = 0.0,
    val balanceAfter: Double = 0.0,
    val transactionId: String? = null,
    val note: String? = null,
    val timeMillis: Long = 0L,
    val metadata: Map<String, String> = emptyMap()
)

@kotlinx.serialization.Serializable
data class CashRegisterExtractionEntryDataModel(
    val id: String,
    val amount: Double,
    val timeMillis: Long,
    val extractedByUserId: String = "",
    val extractedByName: String = "",
    val note: String? = null,
    val balanceBefore: Double = 0.0,
    val balanceAfter: Double = 0.0
)

@kotlinx.serialization.Serializable
data class CashRegisterStateDataModel(
    val register: StoreCashRegisterDataModel,
    val events: List<CashRegisterEventDataModel> = emptyList()
)

@kotlinx.serialization.Serializable
data class CashRegisterExtractionRequestDataModel(
    val storeId: String,
    val amount: Double,
    val note: String? = null,
    val timeMillis: Long = 0L
)
@kotlinx.serialization.Serializable
data class StockBranchQuantityDataModel(
    val storeId: String = "",
    val publicId: String = "",
    val parentStoreId: String? = null,
    val name: List<LocalizedStringDataModel> = emptyList(),
    val address: String = "",
    val isCurrentStore: Boolean = false,
    val isParentStore: Boolean = false,
    val goodsItemId: String? = null,
    val totalQuantity: QuantityDataModel? = null,
    val batchCount: Int = 0,
    val batches: List<GoodsBatchDataModel> = emptyList()
)

@kotlinx.serialization.Serializable
data class StockBatchMovementDataModel(
    val id: String = "",
    val rootStoreId: String = "",
    val sourceStoreId: String = "",
    val destinationStoreId: String = "",
    val sourceGoodsItemId: String = "",
    val destinationGoodsItemId: String = "",
    val sourceBatchId: String = "",
    val destinationBatchId: String = "",
    val quantity: QuantityDataModel,
    val movedByUserId: String = "",
    val movedByName: String = "",
    val movedAtMillis: Long = 0L,
    val status: StockBatchMovementStatusDataModel = StockBatchMovementStatusDataModel.Accepted,
    val acceptedByUserId: String? = null,
    val acceptedByName: String = "",
    val acceptedAtMillis: Long? = null,
    val decisionNote: String? = null,
    val note: String? = null
)

@kotlinx.serialization.Serializable
data class StockItemBranchAvailabilityDataModel(
    val rootStoreId: String = "",
    val currentStoreId: String = "",
    val sourceGoodsItemId: String = "",
    val locations: List<StockBranchQuantityDataModel> = emptyList(),
    val movements: List<StockBatchMovementDataModel> = emptyList()
)

@kotlinx.serialization.Serializable
data class StockBatchMoveRequestDataModel(
    val sourceStoreId: String,
    val destinationStoreId: String,
    val sourceGoodsItemId: String,
    val sourceBatchId: String,
    val quantity: QuantityDataModel,
    val note: String? = null,
    val actorStoreId: String? = null
)

@kotlinx.serialization.Serializable
data class StockBatchMoveDecisionRequestDataModel(
    val movementId: String,
    val accept: Boolean = true,
    val note: String? = null
)

@kotlinx.serialization.Serializable
data class StockBatchMoveResultDataModel(
    val sourceBatch: GoodsBatchDataModel,
    val destinationBatch: GoodsBatchDataModel,
    val sourceGoodsItem: GoodsItemDataModel,
    val destinationGoodsItem: GoodsItemDataModel,
    val movement: StockBatchMovementDataModel,
    val availability: StockItemBranchAvailabilityDataModel,
    val requiresAcceptance: Boolean = false
)


fun CashRegisterEventDataModel.toExtractionEntry(): CashRegisterExtractionEntryDataModel {
    return CashRegisterExtractionEntryDataModel(
        id = id,
        amount = amount,
        timeMillis = timeMillis,
        extractedByUserId = userId,
        extractedByName = userName,
        note = note,
        balanceBefore = balanceBefore,
        balanceAfter = balanceAfter
    )
}

private val setActiveShelfBatchMutex = Mutex()

fun setActiveShelfBatch(
    batch: GoodsBatchDataModel,
    storeId: String,
    onCompleted: ((DataState<GoodsItemDataModel>) -> Unit)? = null,
    previousActiveShelfBatchId: String? = null
) {
    val knownPreviousActiveShelfBatchId = previousActiveShelfBatchId
        ?: stockState.payloadValue?.firstOrNull { it.id == batch.goodsItemId }?.activeShelfBatchId

    if (knownPreviousActiveShelfBatchId == batch.id) {
        stockState.payloadValue?.firstOrNull { it.id == batch.goodsItemId }?.let { currentItem ->
            onCompleted?.invoke(DataState.Success(currentItem))
        } ?: onCompleted?.invoke(DataState.Empty())
        return
    }

    if (!setActiveShelfBatchMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            setActiveShelfBatchMutex.withLock {
                val response = networkRequest<GoodsItemDataModel, GoodsBatchDataModel>(
                    method = HttpMethod.Post,
                    endpointUrl = "stockBatches/setActiveShelfBatch",
                    body = batch,
                    headers = mapOf("store_id" to storeId)
                )

                if (response.negative || response.payload == null) {
                    postInAppNotification(response.message, NotificationType.Negative)
                    onCompleted?.invoke(DataState.Empty())
                } else {
                    val isGenuineShelfChange = response.message.orEmpty().isNotEmpty() &&
                            knownPreviousActiveShelfBatchId != response.payload.activeShelfBatchId

                    if (isGenuineShelfChange)
                        postInAppNotification(response.message, NotificationType.Positive)

                    stockState.emit(
                        DataState.Success(
                            mutableListOf<GoodsItemDataModel>().also { newList ->
                                stockState.payloadValue?.let { newList.addAll(it) }

                                val index = newList.indexOfFirst { it.id == response.payload.id }

                                if (index != -1)
                                    newList[index] = response.payload
                                else
                                    newList.add(response.payload)
                            },
                            response.message
                        )
                    )

                    onCompleted?.invoke(DataState.Success(response.payload, response.message))
                }
            }
        }
}

@kotlinx.serialization.Serializable
data class AppThemeDataModel(
    val id: Long,
    val name: List<LocalizedStringDataModel>
)

@kotlinx.serialization.Serializable
data class BalanceHistoryEntryDataModel(
    val id: String = "",
    val type: Int = 0,
    val amount: Double = 0.0,
    val currency: String = "KZT",
    val note: String = "",
    val referenceId: String = "",
    val timeMillis: Long = 0L
)

@kotlinx.serialization.Serializable
data class CityDataModel(
    val name: List<LocalizedStringDataModel>,
    var centerLatitude: Double,
    var centerLongitude: Double,
    val swLatitude: Double,
    val swLongitude: Double,
    val neLatitude: Double,
    val neLongitude: Double
)

@kotlinx.serialization.Serializable
data class CompanyFormDataModel(
    val id: String,
    val name: List<LocalizedStringDataModel>,
    val parameters: List<ParameterDataModel>
)

@kotlinx.serialization.Serializable
data class LegalIdFormatDataModel(
    val id: String,
    val countryLocales: List<String>,
    val name: List<LocalizedStringDataModel>,
    val label: List<LocalizedStringDataModel>,
    val placeholder: List<LocalizedStringDataModel>,
    val required: Boolean = true,
    val length: Int? = null,
    val minLength: Int? = null,
    val maxLength: Int? = null,
    val digitsOnly: Boolean = true,
    val regex: String? = null
)

fun defaultLegalIdFormats(): List<LegalIdFormatDataModel> = listOf(
    LegalIdFormatDataModel(
        id = "kz_bin",
        countryLocales = listOf("kz"),
        name = listOf(
            LocalizedStringDataModel("main", "BIN"),
            LocalizedStringDataModel("en", "BIN"),
            LocalizedStringDataModel("ru", "БИН"),
            LocalizedStringDataModel("kk", "БИН")
        ),
        label = listOf(
            LocalizedStringDataModel("main", "Business Identification Number"),
            LocalizedStringDataModel("en", "Business Identification Number"),
            LocalizedStringDataModel("ru", "Бизнес-идентификационный номер"),
            LocalizedStringDataModel("kk", "Бизнес сәйкестендіру нөмірі")
        ),
        placeholder = listOf(
            LocalizedStringDataModel("main", "12 digits"),
            LocalizedStringDataModel("en", "12 digits"),
            LocalizedStringDataModel("ru", "12 цифр"),
            LocalizedStringDataModel("kk", "12 сан")
        ),
        length = 12,
        digitsOnly = true,
        regex = "^[0-9]{12}$"
    ),
    LegalIdFormatDataModel(
        id = "tj_tin",
        countryLocales = listOf("tj"),
        name = listOf(
            LocalizedStringDataModel("main", "TIN"),
            LocalizedStringDataModel("en", "TIN"),
            LocalizedStringDataModel("ru", "ИНН / РМА"),
            LocalizedStringDataModel("kk", "СТН / РМА")
        ),
        label = listOf(
            LocalizedStringDataModel("main", "Taxpayer Identification Number"),
            LocalizedStringDataModel("en", "Taxpayer Identification Number"),
            LocalizedStringDataModel("ru", "Идентификационный номер налогоплательщика"),
            LocalizedStringDataModel("kk", "Салық төлеушінің сәйкестендіру нөмірі")
        ),
        placeholder = listOf(
            LocalizedStringDataModel("main", "9 digits"),
            LocalizedStringDataModel("en", "9 digits"),
            LocalizedStringDataModel("ru", "9 цифр"),
            LocalizedStringDataModel("kk", "9 сан")
        ),
        length = 9,
        digitsOnly = true,
        regex = "^[0-9]{9}$"
    )
)

fun GlobalAppConfigurationDataModel.legalIdFormatForCountry(countryLocale: String?): LegalIdFormatDataModel {
    val normalized = countryLocale?.trim()?.lowercase().orEmpty()
    return legalIdFormats.firstOrNull { format ->
        format.countryLocales.any { it.equals(normalized, ignoreCase = true) }
    } ?: legalIdFormats.firstOrNull() ?: defaultLegalIdFormats().first()
}

fun String.matchesLegalIdFormat(format: LegalIdFormatDataModel): Boolean {
    val normalized = trim()
    if (format.required && normalized.isBlank()) return false
    if (!format.required && normalized.isBlank()) return true
    if (format.digitsOnly && !normalized.all { it.isDigit() }) return false
    format.length?.let { if (normalized.length != it) return false }
    format.minLength?.let { if (normalized.length < it) return false }
    format.maxLength?.let { if (normalized.length > it) return false }
    return format.regex?.let { Regex(it).matches(normalized) } ?: true
}

@kotlinx.serialization.Serializable
data class CountryDataModel(
    val locale: String,
    val language: String,
    val name: List<LocalizedStringDataModel>,
    val flagDrawablePath: String,
    val cities: List<CityDataModel>,
    val phoneNumberCode: String,
    val phoneNumberSize: Int,
    val currencies: List<CurrencyDataModel>,
    val cashlessPaymentOptions: List<PaymentOptionDataModel>,
    val preferredCashlessPaymentOptionId: String
)

@kotlinx.serialization.Serializable
data class CurrencyDataModel(
    val code: String,
    val symbol: String,
    val name: List<LocalizedStringDataModel>
)

sealed interface DataState<T> {

    val message: List<LocalizedStringDataModel>?

    data class Success<T>(
        val payload: T,
        override val message: List<LocalizedStringDataModel>? = null,
    ): DataState<T>

    class Empty<T>(override val message: List<LocalizedStringDataModel>? = null): DataState<T>
}

fun <From, To> DataState<From>.map(
    action: (From?) -> To
): DataState<To> {

    return when (this) {
        is DataState.Success -> DataState.Success(action(payload))
        is DataState.Empty -> DataState.Empty(message)
    }
}

class MutableDataStateFlowNonNull<T>(
    private val coroutineScope: CoroutineScope,
    initial: T
): DataStateFlowNonNull<T> {

    private val _state = MutableStateFlow<DataState<T>>(DataState.Success(initial))
    override val value = _state.asStateFlow()
    private val _payload = MutableStateFlow(initial)
    override val payload = _payload.asStateFlow()

    init {
        coroutineScope.launch(Dispatchers.ourIo) {
            _state.collect {
                if (it is DataState.Success)
                    _payload.emit(it.payload)
            }
        }
    }

    fun emit(newValue: DataState<T>) {
        coroutineScope.launch(Dispatchers.ourIo) {
            _state.emit(newValue)
        }
    }

    fun asDataStateFlow(): DataStateFlowNonNull<T> {
        return this as DataStateFlowNonNull<T>
    }
}

interface DataStateFlow<T> {

    val value: StateFlow<DataState<T>>
    val payload: StateFlow<T?>
    val payloadValue: T?
        get() = payload.value
    val payloadValueNonNull: T
        get() = payloadValue!!
}

interface DataStateFlowNonNull<T> {

    val value: StateFlow<DataState<T>>
    val payload: StateFlow<T>

    val payloadValue: T
        get() = payload.value
}

class MutableDataStateFlow<T>(
    private val coroutineScope: CoroutineScope,
    initial: T? = null
): DataStateFlow<T> {

    private val _state = MutableStateFlow<DataState<T>>(initial?.run { DataState.Success(initial) } ?: DataState.Empty())
    override val value = _state.asStateFlow()
    private val _payload = MutableStateFlow(initial)
    override val payload = _payload.asStateFlow()

    init {
        coroutineScope.launch(Dispatchers.ourIo) {
            _state.collect {
                if (it is DataState.Success)
                    _payload.emit(it.payload)
                else
                    _payload.emit(null)
            }
        }
    }

    fun emit(newValue: DataState<T>) {
        coroutineScope.launch(Dispatchers.ourIo) {
            _state.emit(newValue)
        }
    }

    fun asDataStateFlow(): DataStateFlow<T> {
        return this as DataStateFlow<T>
    }
}

@kotlinx.serialization.Serializable
data class DebtInterestDataModel(
    val enabled: Boolean = false,
    val ratePercent: Double = 0.0,
    val periodUnit: String = "month", // day, week, month, year
    val startsAtMillis: Long? = null,
    val note: String? = null
)

@kotlinx.serialization.Serializable
data class DebtPartialPaymentPlanDataModel(
    val id: String = "",
    val amount: Double = 0.0,
    val percent: Double? = null,
    val dueAtMillis: Long? = null,
    val note: String? = null,
    val completed: Boolean = false,
    val paidAtMillis: Long? = null
)

@kotlinx.serialization.Serializable
data class DebtPaymentRecordDataModel(
    val id: String = "",
    val amount: Double = 0.0,
    val currency: String = "KZT",
    val timeMillis: Long = 0L,
    val paymentKind: String = "partial", // full, partial, edit
    val plannedPaymentId: String? = null,
    val note: String? = null,
    val debtBefore: Double = 0.0,
    val debtAfter: Double = 0.0
)

@kotlinx.serialization.Serializable
data class DebtorDataModel(
    val id: String = "",
    val email: String = "",
    val debtAmount: Double = 0.0,
    val currency: String = "KZT",
    val phoneNumber: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val debtorType: String = "individual", // individual, company
    val idNumber: String = "",
    val companyName: String = "",
    val companyIdNumber: String = "",
    val debtCreatedAtMillis: Long = 0L,
    val debtDueAtMillis: Long? = null,
    val originalDebtAmount: Double? = null,
    val interest: DebtInterestDataModel? = null,
    val plannedPayments: List<DebtPartialPaymentPlanDataModel> = emptyList(),
    val paymentHistory: List<DebtPaymentRecordDataModel> = emptyList(),
    val transactionIds: List<String> = emptyList()
) {
    val displayName: String
        get() = if (debtorType == "company") {
            companyName.ifBlank { companyIdNumber.ifBlank { id } }
        } else {
            "${firstName.trim()} ${lastName.trim()}".trim().ifBlank { phoneNumber.asDisplayPhoneNumber().ifBlank { id } }
        }
}

@kotlinx.serialization.Serializable
data class DebtPaymentRequestDataModel(
    val debtorId: String,
    val storeId: String,
    val amount: Double,
    val currency: String,
    val paymentKind: String = "partial", // full, partial
    val plannedPaymentId: String? = null,
    val note: String? = null,
    val timeMillis: Long = 0L
)

@kotlinx.serialization.Serializable
data class GenericGoodsCategoryDataModel(
    val id: String,
    val typeIds: List<String>?,
    val name: List<LocalizedStringDataModel>,
    val alias: List<LocalizedStringDataModel>? = null,
    val description: List<LocalizedStringDataModel>? = null,
    val quantityUnitId: String,
    val imagePaths: List<StylizedDrawablePathsGroupDataModel>?
)

@kotlinx.serialization.Serializable
data class GenericGoodsItemDataModel(
    val id: String,
    val barcode: List<String>?,
    val name: List<LocalizedStringDataModel>,
    val typeIds: List<String>?,
    val categoryIds: List<String>?,
    val supplierIds: List<String>?,
    val manufacturerIds: List<String>?
)

@kotlinx.serialization.Serializable
data class GenericResponseDataModel(
    val message: String?,
    val payload: String?,
    val negative: Boolean
) {

    fun getMessage(): List<LocalizedStringDataModel>? {
        return message?.let { jsonBase.decodeFromString<List<LocalizedStringDataModel>>(it) }
    }

    inline fun <reified T> getPayload(): T? {
        return payload?.let { jsonBase.decodeFromString<T>(it) }
    }

    inline fun <reified T> toResponseDataModel(): ResponseDataModel<T> {
        return ResponseDataModel<T>(
            message = message?.let { jsonBase.decodeFromString<List<LocalizedStringDataModel>>(it) },
            payload = payload?.let { jsonBase.decodeFromString<T>(it) },
            negative = negative
        )
    }
}


@kotlinx.serialization.Serializable
data class GlobalAppConfigurationDataModel(
    val realtimeUpdatesPath: String,
    val appName: Pair<String, String>,
    val serverUrl: Pair<String, String>,
    val globalAppConfigurationPath: Pair<String, String>,
    val logInPath: Pair<String, String>,
    val signUpPath: Pair<String, String>,
    val refreshPath: Pair<String, String>,
    val logOutPath: Pair<String, String>,
    val connectionCheckPath: Pair<String, String> = Pair("auth/ping", "1147"),
    val getSecuritySessionsPath: Pair<String, String> = Pair("security/sessions/get", "44"),
    val getSecuritySessionHistoryPath: Pair<String, String> = Pair("security/sessions/history", "104"),
    val revokeSecuritySessionPath: Pair<String, String> = Pair("security/sessions/revoke", "45"),
    val revokeOtherSecuritySessionsPath: Pair<String, String> = Pair("security/sessions/revokeOthers", "46"),
    val getUserPath: Pair<String, String>,
    val updateUserPath: Pair<String, String>,
    val updateUserPreferencesPath: Pair<String, String> = Pair("user/preferences/update", "95"),
    val getStoresPath: Pair<String, String>,
    val addStoresPath: Pair<String, String>,
    val updateStoresPath: Pair<String, String>,
    val deleteStoresPath: Pair<String, String>,
    val getStockPath: Pair<String, String>,
    val getParentStoreStockPath: Pair<String, String> = Pair("stock/parent/get", "1212"),
    val addGoodsItemPath: Pair<String, String>,
    val updateGoodsItemPath: Pair<String, String>,
    val deleteGoodsItemPath: Pair<String, String>,
    val getStockBatchesPath: Pair<String, String>,
    val addStockBatchPath: Pair<String, String>,
    val updateStockBatchPath: Pair<String, String>,
    val deleteStockBatchPath: Pair<String, String>,
    val getStockItemBranchAvailabilityPath: Pair<String, String> = Pair("stockBatches/branchAvailability", "73"),
    val moveStockBatchPath: Pair<String, String> = Pair("stockBatches/move", "74"),
    val decideStockBatchMovePath: Pair<String, String> = Pair("stockBatches/decideMove", "74"),

    val getGenericGoodsItemsPath: Pair<String, String>,
    val getGenericGoodsCategoriesPath: Pair<String, String>,
    val getSuppliersPath: Pair<String, String>,
    val addSupplierPath: Pair<String, String> = Pair("suppliers/add", "83"),
    val updateSupplierPath: Pair<String, String> = Pair("suppliers/update", "84"),
    val deleteSupplierPath: Pair<String, String> = Pair("suppliers/delete", "85"),
    val stringResourcesPath: Pair<String, String>,
    val dimensionResourcesPath: Pair<String, String>,
    val colorResourcesPath: Pair<String, String>,
    val drawableResourcesConfigurationPath: Pair<String, String>,
    val drawableResourcesPath: Pair<String, String>,
    val getTransactionsPath: Pair<String, String>,
    val completeTransactionPath: Pair<String, String>,
    val getDebtorsPath: Pair<String, String> = Pair("debtors/get", "39"),
    val addDebtorPath: Pair<String, String> = Pair("debtors/add", "40"),
    val updateDebtorPath: Pair<String, String> = Pair("debtors/update", "41"),
    val deleteDebtorPath: Pair<String, String> = Pair("debtors/delete", "42"),
    val payDebtorDebtPath: Pair<String, String> = Pair("debtors/pay", "43"),
    val getSupplierGoodsPricesPath: Pair<String, String> = Pair("supplierGoodsPrices/get", "31"),
    val upsertSupplierGoodsPricePath: Pair<String, String> = Pair("supplierGoodsPrices/upsert", "32"),
    val deleteSupplierGoodsPricesPath: Pair<String, String> = Pair("supplierGoodsPrices/delete", "33"),

    val getSupplierOrdersPath: Pair<String, String> = Pair("supplierOrders/get", "34"),
    val addSupplierOrderPath: Pair<String, String> = Pair("supplierOrders/add", "35"),
    val updateSupplierOrderPath: Pair<String, String> = Pair("supplierOrders/update", "36"),
    val deleteSupplierOrdersPath: Pair<String, String> = Pair("supplierOrders/delete", "37"),
    val receiveSupplierOrderPath: Pair<String, String> = Pair("supplierOrders/receive", "38"),
    val getCashRegisterPath: Pair<String, String> = Pair("cashRegister/get", "49"),
    val extractCashRegisterPath: Pair<String, String> = Pair("cashRegister/extract", "50"),
    val getStoreWorkersPath: Pair<String, String> = Pair("workers/store/get", "51"),
    val getMyWorkerMembershipsPath: Pair<String, String> = Pair("workers/my/get", "52"),
    val getIncomingWorkerRequestsPath: Pair<String, String> = Pair("workers/requests/incoming", "53"),
    val getMyWorkerRequestsPath: Pair<String, String> = Pair("workers/requests/my", "54"),
    val getStoreWorkerRoleTemplatesPath: Pair<String, String> = Pair("workers/roleTemplates/get", "1248"),
    val upsertStoreWorkerRoleTemplatePath: Pair<String, String> = Pair("workers/roleTemplates/upsert", "1249"),
    val deleteStoreWorkerRoleTemplatePath: Pair<String, String> = Pair("workers/roleTemplates/delete", "1250"),
    val requestStoreEmploymentPath: Pair<String, String> = Pair("workers/request", "55"),
    val acceptStoreEmploymentPath: Pair<String, String> = Pair("workers/accept", "56"),
    val declineStoreEmploymentPath: Pair<String, String> = Pair("workers/decline", "57"),
    val updateStoreWorkerPermissionsPath: Pair<String, String> = Pair("workers/updatePermissions", "58"),
    val removeStoreWorkerPath: Pair<String, String> = Pair("workers/remove", "105"),
    val confirmStoreWorkerRemovalPath: Pair<String, String> = Pair("workers/removal/confirm", "106"),
    val declineStoreWorkerRemovalPath: Pair<String, String> = Pair("workers/removal/decline", "107"),
    val updateMyWorkerPasswordPath: Pair<String, String> = Pair("workers/my/password", "103"),
    val inviteStoreWorkerPath: Pair<String, String> = Pair("workers/invite", "65"),
    val acceptStoreWorkerInvitationPath: Pair<String, String> = Pair("workers/invitations/accept", "66"),
    val declineStoreWorkerInvitationPath: Pair<String, String> = Pair("workers/invitations/decline", "67"),
    val getCurrentWorkshiftPath: Pair<String, String> = Pair("workshifts/current", "86"),
    val startWorkshiftPath: Pair<String, String> = Pair("workshifts/start", "87"),
    val endWorkshiftPath: Pair<String, String> = Pair("workshifts/end", "88"),
    val getOperationLogsPath: Pair<String, String> = Pair("logs/get", "92"),
    val getStoreAnalyticsPath: Pair<String, String> = Pair("analytics/store/get", "94"),
    val getUserFinanceDashboardPath: Pair<String, String> = Pair("finance/dashboard", "77"),
    val createTopUpPaymentPath: Pair<String, String> = Pair("finance/topup/create", "78"),
    val confirmDevelopmentTopUpPath: Pair<String, String> = Pair("finance/topup/confirmDevelopment", "79"),
    val getSubscriptionPlansPath: Pair<String, String> = Pair("subscriptions/plans", "80"),
    val getStoreSubscriptionPath: Pair<String, String> = Pair("subscriptions/store/get", "81"),
    val updateStoreSubscriptionPath: Pair<String, String> = Pair("subscriptions/store/update", "82"),
    val getSupportTicketsPath: Pair<String, String> = Pair("support/tickets/get", "96"),
    val createSupportTicketPath: Pair<String, String> = Pair("support/tickets/create", "97"),
    val closeSupportTicketPath: Pair<String, String> = Pair("support/tickets/close", "98"),
    val reopenSupportTicketPath: Pair<String, String> = Pair("support/tickets/reopen", "99"),
    val getSupportMessagesPath: Pair<String, String> = Pair("support/messages/get", "100"),
    val sendSupportMessagePath: Pair<String, String> = Pair("support/messages/send", "101"),
    val markSupportMessagesReadPath: Pair<String, String> = Pair("support/messages/read", "102"),
    val pagingDefaultPageSize: Int = 40,
    val pagingMaxPageSize: Int = 200,
    val paymentProviders: List<PaymentProviderConfigDataModel> = defaultPaymentProviders(),
    val subscriptionPlans: List<StoreSubscriptionPlanDataModel> = defaultStoreSubscriptionPlans(),
    val companyForms: List<CompanyFormDataModel>,
    val countries: List<CountryDataModel>,
    val legalIdFormats: List<LegalIdFormatDataModel> = defaultLegalIdFormats(),
    val languages: List<AppLanguageDataModel>,
    val themes: List<AppThemeDataModel>,
    val goodsItemsQuantityUnits: List<QuantityDataModel>
)

@kotlinx.serialization.Serializable
data class SupplierOrderLineDataModel(
    val id: String = "",
    val orderId: String,
    val goodsItemId: String,

    val requestedQuantity: QuantityDataModel,

    val expectedSupplyPrice: PriceDataModel? = null,

    val desiredExpirationDateMillis: Long? = null,
    val additionalNotes: String? = null,
    val additionalNotesLocalized: List<LocalizedStringDataModel> = emptyList(),

    val supplierComment: String? = null,
    val supplierCommentLocalized: List<LocalizedStringDataModel> = emptyList(),
    val supplierAcceptedQuantity: QuantityDataModel? = null,
    val supplierOfferedSupplyPrice: PriceDataModel? = null,
    val substituteGoodsItemId: String? = null,

    val deliveredBatchIds: List<String> = emptyList(),

    val isActive: Boolean = true
)

@kotlinx.serialization.Serializable
data class SupplierOrderDataModel(
    val id: String = "",
    val userId: String = "",
    val storeId: String,
    val supplierId: String,

    val amount: PriceDataModel? = null,

    val orderedAtMillis: Long = 0L,
    val desiredDeliveryTimeMillis: Long? = null,
    val confirmedDeliveryTimeMillis: Long? = null,
    val deliveredAtMillis: Long? = null,

    val storeAddress: LocationDataModel? = null,

    val additionalNotes: String? = null,
    val additionalNotesLocalized: List<LocalizedStringDataModel> = emptyList(),

    val supplierComment: String? = null,
    val supplierCommentLocalized: List<LocalizedStringDataModel> = emptyList(),
    val paymentTerms: String? = null,
    val externalReference: String? = null,
    val storeContactUserId: String? = null,

    val status: SupplierOrderStatusDataModel = SupplierOrderStatusDataModel.Draft,

    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L,
    val isActive: Boolean = true
)

@kotlinx.serialization.Serializable
data class SupplierGoodsPriceDataModel(
    val id: String = "",
    val userId: String = "",
    val storeId: String,
    val supplierId: String,
    val goodsItemId: String,

    val supplyPrice: PriceDataModel,

    val minOrderQuantity: QuantityDataModel? = null,
    val packageQuantity: QuantityDataModel? = null,

    val supplierBarcode: String? = null,
    val supplierGoodsName: String? = null,

    val lastUsedAtMillis: Long? = null,
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L,

    val isActive: Boolean = true
)

@kotlinx.serialization.Serializable
data class GoodsBatchDataModel(
    val id: String = "",
    val goodsItemId: String,
    val userId: String = "",
    val storeId: String,

    val supplierId: String? = null,
    val supplierOrderId: String? = null,

    val quantity: QuantityDataModel,

    val supplyPrice: PriceDataModel,
    val salePriceOverride: PriceDataModel? = null,
    val returnPriceOverride: PriceDataModel? = null,
    val wholesalePriceOverride: PriceDataModel? = null,

    val deliveredAtMillis: Long? = null,
    val manufacturedAtMillis: Long? = null,
    val expirationDateMillis: Long? = null,

    val discounts: List<BatchDiscountDataModel> = emptyList(),
    val promotions: List<StockPromotionDataModel> = emptyList(),

    val shelfPosition: String? = null,
    val shelfPriority: Int = 0,

    val status: StockBatchStatusDataModel = StockBatchStatusDataModel.Delivered,

    val additionalNotes: String? = null,
    val additionalNotesLocalized: List<LocalizedStringDataModel> = emptyList(),

    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L,
    val createdByUserId: String? = null,

    val isActive: Boolean = true
)

//@kotlinx.serialization.Serializable
//data class GoodsBatchDataModel(
//  val id: String,
//  val goodsItemId: String,
//  val userId: String,
//  val storeId: String,
//  val supplierId: String,
//  val salePrice: PriceDataModel,
//  val returnPrice: PriceDataModel,
//  val supplyPrice: PriceDataModel,
//  val quantity: QuantityDataModel,
//  val supplyTime: Long,
//  val expirationTime: Long,
//  val shelfQueue: GoodsBatchShelfQueueDataModel,
//  val createdAt: Long,
//  val createdByUserId: String,
//  val isActive: Boolean
//)

@kotlinx.serialization.Serializable
data class GoodsBatchShelfQueueDataModel(
    val statusId: Int,
    val startTime: Long,
    val endTime: Long
)

@kotlinx.serialization.Serializable
data class GoodsCategoryDataModel(
    val id: String = "",
    val name: List<LocalizedStringDataModel> = emptyList(),
    val imageUrl: String = "",
    val quantityWithUnitSerialized: String = "",
    val storeId: String = "",
    val universal: Boolean = false
)

@kotlinx.serialization.Serializable
data class GoodsItemDataModel(
    val id: String = "",
    val userId: String = "",
    val storeId: String = "",

    val barcodes: List<String> = emptyList(),
    val barcodeModels: List<GoodsItemBarcodeDataModel> = emptyList(),
    val name: List<LocalizedStringDataModel> = emptyList(),

    val description: List<LocalizedStringDataModel> = emptyList(),

    val measurementUnitId: String = "0",
    val categoryIds: List<String> = emptyList(),

    val salePrices: List<PriceDataModel> = emptyList(),
    val returnPrices: List<PriceDataModel> = emptyList(),
    val supplyPrices: List<PriceDataModel> = emptyList(),
    val wholesalePrices: List<PriceDataModel> = emptyList(),
    val wholesaleMinQuantity: QuantityDataModel? = null,

    val genericExpirationPeriod: ExpirationPeriodDataModel? = null,

    val isQuickItem: Boolean = false,
    val imagePaths: List<String> = emptyList(),

    val activeShelfBatchId: String? = null,

    val promotions: List<StockPromotionDataModel> = emptyList(),

    val note: String? = null,
    val noteLocalized: List<LocalizedStringDataModel> = emptyList(),
    val conditions: List<String> = emptyList(),

    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L,
    val isActive: Boolean = true
): Searchable {

    override val exactSearchOperands: List<String>
        get() = mutableListOf<String>().apply {
            addAll(allBarcodeValues())
            addAll(allBarcodeValues().map { it.toStoredGoodsItemBarcode() })
            addAll(name.map { it.value })
            addAll(salePrices.map { it.price })
            addAll(returnPrices.map { it.price })
            addAll(supplyPrices.map { it.price })
            addAll(wholesalePrices.map { it.price })
            addAll(noteLocalized.map { it.value })
            addAll(conditions)
            addAll(promotions.flatMap { promotion -> promotion.title.map { it.value } + listOfNotNull(promotion.note) })
            note?.let { add(it) }
            addAll(salePrices.map { it.currency })
            addAll(returnPrices.map { it.currency })
            addAll(supplyPrices.map { it.currency })
            addAll(wholesalePrices.map { it.currency })
        }
    override val containsSearchOperands: List<String>
        get() = mutableListOf<String>().apply {
            addAll(allBarcodeValues())
            addAll(allBarcodeValues().map { it.toStoredGoodsItemBarcode() })
            addAll(name.map { it.value })
            addAll(salePrices.map { it.price })
            addAll(returnPrices.map { it.price })
            addAll(supplyPrices.map { it.price })
            addAll(wholesalePrices.map { it.price })
            addAll(noteLocalized.map { it.value })
            addAll(conditions)
            addAll(promotions.flatMap { promotion -> promotion.title.map { it.value } + listOfNotNull(promotion.note) })
            note?.let { add(it) }
            addAll(salePrices.map { it.currency })
            addAll(returnPrices.map { it.currency })
            addAll(supplyPrices.map { it.currency })
            addAll(wholesalePrices.map { it.currency })
        }
    override val uniqueSearchOperands: List<String>
        get() = mutableListOf<String>().apply {
            addAll(allBarcodeValues())
            addAll(allBarcodeValues().map { it.toStoredGoodsItemBarcode() })
        }
}

@kotlinx.serialization.Serializable
class GoodsItemInCartDataModel(
    val id: String,
    val transactionTypeIndex: Int,
    val clientId: Int,
    val quantity: QuantityDataModel,
    val timeAdded: Long
)

@kotlinx.serialization.Serializable
data class GoodsItemInRemovalDataModel(
    val barcode: String,
    val quantity: Double,
    val storeId: String
)

@kotlinx.serialization.Serializable
data class GoodsItemInTransactionDataModel(
    val barcode: String,
    val quantity: Double,
    val pricePerUnit: Double,
    val supplierId: Long? = null,
    val saleMethodId: String = SALE_METHOD_RETAIL,
    val supplierIdText: String? = null,
    val name: List<LocalizedStringDataModel> = emptyList(),
    val goodsItemId: String? = null,
    val quantityUnit: QuantityDataModel? = null,
    val currencyCode: String? = null,
    val returnReason: String = ""
)

@kotlinx.serialization.Serializable
data class LocalizedStringDataModel(
    val language: String,
    val value: String
)

@kotlinx.serialization.Serializable
data class LocalizedStringGroupDataModel(
    val id: Long,
    val values: List<LocalizedStringDataModel>
)

@kotlinx.serialization.Serializable
data class LocationDataModel(
    val name: String,
    val postalIndex: String,
    val latitude: Double,
    val longitude: Double
)

@kotlinx.serialization.Serializable
data class ManufacturerDataModel(
    val id: String,
    val name: List<LocalizedStringDataModel>,
    val alias: List<LocalizedStringDataModel>?,
    val description: List<LocalizedStringDataModel>?
)

@kotlinx.serialization.Serializable
data class NotificationDataModel(
    val message: String,
    val type: NotificationType,
    val id: String = "",
    val userId: String? = null,
    val storeId: String? = null,
    val title: String = "",
    val category: String = "general",
    val source: String = "app",
    val metadata: Map<String, String> = emptyMap(),
    val createdAtMillis: Long = 0L,
    val shownAtMillis: Long = 0L,
    val readAtMillis: Long? = null,
    val isSavedOnServer: Boolean = false
): Searchable {
    override val exactSearchOperands: List<String>
        get() = listOf(id, title, message, category, source, type.name) + metadata.values

    override val containsSearchOperands: List<String>
        get() = listOf(id, title, message, category, source, type.name) + metadata.values

    override val uniqueSearchOperands: List<String>
        get() = listOf(id)
}

enum class NotificationType {
    Positive, Negative, Neutral
}

@kotlinx.serialization.Serializable
data class SupportTicketDataModel(
    val id: String,
    val publicId: String,
    val userId: String,
    val storeId: String? = null,
    val subject: String,
    val category: String = "general",
    val priority: String = "normal",
    val status: String = "open",
    val assignedAgentUserId: String? = null,
    val lastMessage: String = "",
    val lastMessageAtMillis: Long = 0L,
    val lastCustomerMessageAtMillis: Long? = null,
    val lastAgentMessageAtMillis: Long? = null,
    val unreadForUserCount: Int = 0,
    val unreadForAgentCount: Int = 0,
    val metadata: Map<String, String> = emptyMap(),
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L,
    val closedAtMillis: Long? = null,
    val isActive: Boolean = true
): Searchable {
    override val exactSearchOperands: List<String>
        get() = listOf(id, publicId, subject, category, priority, status, lastMessage) + metadata.values

    override val containsSearchOperands: List<String>
        get() = listOf(id, publicId, subject, category, priority, status, lastMessage) + metadata.values

    override val uniqueSearchOperands: List<String>
        get() = listOf(id, publicId)
}

@kotlinx.serialization.Serializable
data class SupportMessageDataModel(
    val id: String,
    val ticketId: String,
    val userId: String,
    val senderUserId: String,
    val senderRole: String,
    val senderDisplayName: String = "",
    val body: String,
    val attachments: List<String> = emptyList(),
    val metadata: Map<String, String> = emptyMap(),
    val clientMessageId: String? = null,
    val createdAtMillis: Long = 0L,
    val editedAtMillis: Long? = null,
    val readByCustomerAtMillis: Long? = null,
    val readByAgentAtMillis: Long? = null,
    val isActive: Boolean = true
): Searchable {
    override val exactSearchOperands: List<String>
        get() = listOf(id, ticketId, senderRole, senderDisplayName, body) + metadata.values + attachments

    override val containsSearchOperands: List<String>
        get() = listOf(id, ticketId, senderRole, senderDisplayName, body) + metadata.values + attachments

    override val uniqueSearchOperands: List<String>
        get() = listOf(id)
}

@kotlinx.serialization.Serializable
data class SupportTicketCreateRequestDataModel(
    val subject: String,
    val initialMessage: String,
    val category: String = "general",
    val priority: String = "normal",
    val storeId: String? = null,
    val metadata: Map<String, String> = emptyMap(),
    val clientMessageId: String? = null
)

@kotlinx.serialization.Serializable
data class SupportMessageSendRequestDataModel(
    val ticketId: String,
    val body: String,
    val attachments: List<String> = emptyList(),
    val metadata: Map<String, String> = emptyMap(),
    val clientMessageId: String? = null
)

@kotlinx.serialization.Serializable
data class SupportTicketActionRequestDataModel(
    val ticketId: String
)

@kotlinx.serialization.Serializable
data class SupportMessagesReadRequestDataModel(
    val ticketId: String
)


@kotlinx.serialization.Serializable
data class ParameterDataModel(
    val name: List<LocalizedStringDataModel>,
    val value: String,
    val length: Int,
    val number: Boolean,
    val nonLetterSymbolsEnabled: Boolean
)

@kotlinx.serialization.Serializable
data class PaymentOptionDataModel(
    val id: String,
    val name: List<LocalizedStringDataModel>
)

@kotlinx.serialization.Serializable
data class PriceDataModel(
    val price: String,
    val currency: String,
    val supplierId: String
)

@kotlinx.serialization.Serializable
data class QuantityDataModel(
    val id: String,
    val immutableUnitName: List<LocalizedStringDataModel>,
    val total: Double = 1.0,
    val pricedAmount: Double = 1.0,
    val roundTotal: Boolean
) {

    fun matchesName(name: String): Boolean {
        return immutableUnitName.any {
            it.value.equals(name, true)
        }
    }
}

@kotlinx.serialization.Serializable
data class RemoteResponseDataModel(
    val id: String,
    val message: List<LocalizedStringDataModel>
)

@kotlinx.serialization.Serializable
data class RealtimeUpdateDataModel(
    val id: String = "",
    val type: String = "changed",
    val entity: String = "all",
    val storeId: String? = null,
    val reason: String? = null,
    val createdAtMillis: Long = 0L
)

@kotlinx.serialization.Serializable
data class RealtimeClientHelloDataModel(
    val activeStoreId: String? = null,
    val language: String = "",
    val platform: String = "",
    val clientTimeMillis: Long = 0L
)

const val LOCAL_NETWORK_ROLE_DISABLED = "disabled"
const val LOCAL_NETWORK_ROLE_AUTO = "auto"
const val LOCAL_NETWORK_ROLE_SERVER = "server"
const val LOCAL_NETWORK_ROLE_CLIENT = "client"
const val LOCAL_NETWORK_OPERATION_TRANSACTION_COMPLETE = "transaction_complete"
const val LOCAL_NETWORK_OPERATION_WORKSHIFT_END = "workshift_end"
const val LOCAL_NETWORK_QUEUE_PENDING = "pending"
const val LOCAL_NETWORK_QUEUE_SYNCING = "syncing"
const val LOCAL_NETWORK_QUEUE_SYNCED = "synced"
const val LOCAL_NETWORK_QUEUE_FAILED = "failed"

@kotlinx.serialization.Serializable
data class LocalNetworkDeviceDataModel(
    val deviceId: String = "",
    val userId: String = "",
    val userName: String = "",
    val platform: String = "",
    val host: String = "",
    val port: Int = 45720,
    val role: String = LOCAL_NETWORK_ROLE_CLIENT,
    val storeId: String? = null,
    val branchStoreId: String? = null,
    val queueSize: Int = 0,
    val online: Boolean = true,
    val lastSeenMillis: Long = 0L
)

@kotlinx.serialization.Serializable
data class LocalNetworkStateDataModel(
    val enabled: Boolean = false,
    val role: String = LOCAL_NETWORK_ROLE_DISABLED,
    val deviceId: String = "",
    val tcpPort: Int = 45720,
    val discoveryPort: Int = 45721,
    val serverDeviceId: String? = null,
    val serverHost: String? = null,
    val serverPort: Int = 45720,
    val activeStoreId: String? = null,
    val branchStoreId: String? = null,
    val lastDiscoveryMillis: Long = 0L,
    val lastSyncMillis: Long = 0L,
    val lastError: String? = null
) {
    val isServer: Boolean get() = enabled && role == LOCAL_NETWORK_ROLE_SERVER
    val isClient: Boolean get() = enabled && role == LOCAL_NETWORK_ROLE_CLIENT
    val hasServer: Boolean get() = !serverHost.isNullOrBlank() && !serverDeviceId.isNullOrBlank()
}

@kotlinx.serialization.Serializable
data class LocalNetworkSnapshotDataModel(
    val storeId: String = "",
    val stock: List<GoodsItemDataModel> = emptyList(),
    val stockBatches: List<GoodsBatchDataModel> = emptyList(),
    val transactions: List<TransactionDataModel> = emptyList(),
    val debtors: List<DebtorDataModel> = emptyList(),
    val cashRegister: StoreCashRegisterDataModel? = null,
    val updatedAtMillis: Long = 0L
)

@kotlinx.serialization.Serializable
data class LocalNetworkQueuedOperationDataModel(
    val id: String = "",
    val operationType: String = LOCAL_NETWORK_OPERATION_TRANSACTION_COMPLETE,
    val storeId: String = "",
    val branchStoreId: String? = null,
    val endpointPath: String = "",
    val httpMethod: String = "POST",
    val bodyJson: String = "",
    val createdByUserId: String = "",
    val createdByDeviceId: String = "",
    val createdAtMillis: Long = 0L,
    val cloudSyncedAtMillis: Long? = null,
    val status: String = LOCAL_NETWORK_QUEUE_PENDING,
    val attemptCount: Int = 0,
    val lastError: String? = null
)

@kotlinx.serialization.Serializable
data class LocalNetworkEnvelopeDataModel(
    val messageId: String = "",
    val type: String = "hello",
    val device: LocalNetworkDeviceDataModel? = null,
    val operation: LocalNetworkQueuedOperationDataModel? = null,
    val operations: List<LocalNetworkQueuedOperationDataModel> = emptyList(),
    val snapshot: LocalNetworkSnapshotDataModel? = null,
    val accepted: Boolean = true,
    val error: String? = null,
    val createdAtMillis: Long = 0L
)

@kotlinx.serialization.Serializable
data class ResponseDataModel<T>(
    val message: List<LocalizedStringDataModel>?,
    val payload: T?,
    val negative: Boolean,
    val httpStatusCode: Int? = null,
    val transportFailure: Boolean = false
)

@Suppress("UNCHECKED_CAST")
fun <T : Searchable> List<Searchable>.search(query: String, vararg extraOperands: String): Pair<List<T>, Boolean> {
    singleOrNull {
        it.searchUnique(query, *extraOperands)
    }?.run {
        return map { it as T } to true
    }

    val exact = filter {
        it.searchExact(query, *extraOperands)
    }
    val contains = filter {
        it.searchContains(query, *extraOperands) && !exact.contains(it)
    }

    return mutableListOf<Searchable>()
        .apply {
            addAll(exact)
            addAll(contains)
        }
        .toList()
        .map { it as T } to false
}

interface Searchable {

    val exactSearchOperands: List<String>
    val containsSearchOperands: List<String>
    val uniqueSearchOperands: List<String>


    fun searchExact(query: String, vararg extraOperands: String): Boolean {
        return exactSearchOperands.any { it.equals(query, true) }
                || extraOperands.any { it.equals(query, true) }
    }

    fun searchContains(query: String, vararg extraOperands: String): Boolean {
        return containsSearchOperands.any { it.contains(query, true) }
                || extraOperands.any { it.contains(query, true) }
    }

    fun searchUnique(query: String, vararg extraOperands: String): Boolean {
        return uniqueSearchOperands.all { it.equals(query, true) } && extraOperands.any { it.equals(query, true) }
    }
}

abstract class StateHost {
    private val _state = MutableStateFlow(mapOf<String, String>())
    val state = _state.asStateFlow()

    suspend fun setState(pair: Pair<String, String>) {
        _state.emit(
            _state.value.toMutableMap().apply {
                this[pair.first] = pair.second
            }
        )
    }

    suspend fun removeState(key: String) {
        _state.emit(
            _state.value.toMutableMap().apply {
                remove(key)
            }
        )
    }
}

@kotlinx.serialization.Serializable
data class StoreDataModel(
    val id: String,
    val publicId: String = "",
    val parentStoreId: String? = null,
    val userIds: List<String>,
    val storeTypeIds: List<String>,
    val name: List<LocalizedStringDataModel>,
    val alias: List<LocalizedStringDataModel>,
    val description: List<LocalizedStringDataModel>,
    val companyForms: List<CompanyFormDataModel>,
    val location: LocationDataModel,
    val address: String = "",
    val legalIdTypeId: String = "",
    val legalId: String = "",
    val phoneNumbers: List<String>,
    val emails: List<String>,
    val countryLocales: List<String>,
    val createdAt: Long,
    val branches: List<StoreDataModel> = emptyList()
): Searchable {

    override val exactSearchOperands: List<String>
        get() {
            return mutableListOf<String>()
                .apply {
                    add(id)
                    parentStoreId?.let { add(it) }
                    add(publicId)
                    name.forEach {
                        add(it.value)
                    }

                    alias.forEach {
                        add(it.value)
                    }

                    description.forEach {
                        add(it.value)
                    }

                    add(location.name)
                    add(address)
                    add(legalIdTypeId)
                    add(legalId)
                    branches.forEach { branch ->
                        add(branch.id)
                        add(branch.publicId)
                        add(branch.address)
                        branch.name.forEach { add(it.value) }
                    }
                    add(location.postalIndex)
                    add(location.latitude.toString())
                    add(location.longitude.toString())

                    phoneNumbers.forEach { add(it) }
                    emails.forEach { add(it) }
                }
        }
    override val containsSearchOperands: List<String>
        get() {
            return mutableListOf<String>()
                .apply {
                    add(id)
                    parentStoreId?.let { add(it) }
                    add(publicId)
                    name.forEach {
                        add(it.value)
                    }

                    alias.forEach {
                        add(it.value)
                    }

                    description.forEach {
                        add(it.value)
                    }

                    add(location.name)
                    add(address)
                    add(legalIdTypeId)
                    add(legalId)
                    branches.forEach { branch ->
                        add(branch.id)
                        add(branch.publicId)
                        add(branch.address)
                        branch.name.forEach { add(it.value) }
                    }
                    add(location.postalIndex)
                    add(location.latitude.toString())
                    add(location.longitude.toString())

                    phoneNumbers.forEach { add(it) }
                    emails.forEach { add(it) }
                }
        }
    override val uniqueSearchOperands: List<String>
        get() {
            return listOf(id, publicId).filter { it.isNotBlank() }
        }
}

fun StoreDataModel.isBranchStore(): Boolean = !parentStoreId.isNullOrBlank()

fun StoreDataModel.rootStoreId(): String = parentStoreId?.takeIf { it.isNotBlank() } ?: id

fun StoreDataModel.canBeSelectedAsActiveStore(): Boolean = true

fun StoreDataModel.displayAddress(): String = address.ifBlank { location.name }

fun List<StoreDataModel>.flattenStoresWithBranches(): List<StoreDataModel> {
    val result = linkedMapOf<String, StoreDataModel>()

    fun addStore(store: StoreDataModel) {
        if (store.id.isNotBlank()) result[store.id] = store
        store.branches.forEach { branch -> addStore(branch) }
    }

    forEach { addStore(it) }
    return result.values.toList()
}

fun List<StoreDataModel>.topLevelStores(): List<StoreDataModel> =
    filter { it.parentStoreId.isNullOrBlank() }

fun List<StoreDataModel>.findStoreOrBranch(id: String?): StoreDataModel? {
    if (id.isNullOrBlank()) return null
    return flattenStoresWithBranches().firstOrNull { store ->
        store.id == id || store.publicId.equals(id, ignoreCase = true)
    }
}

fun List<StoreDataModel>.settableActiveStores(): List<StoreDataModel> =
    flattenStoresWithBranches().filter { it.canBeSelectedAsActiveStore() }

@kotlinx.serialization.Serializable
sealed interface StoreJobDataModel {

    data object Cashier: StoreJobDataModel

    data object WarehouseManager: StoreJobDataModel

    data object Administrator: StoreJobDataModel

    fun serialize(): String {
        return when (this) {
            is Cashier -> "Cashier"
            is WarehouseManager -> "WarehouseManager"
            is Administrator -> "Administrator"
        }
    }

    companion object {
        fun deserialize(serialized: String): StoreJobDataModel {
            return when (serialized) {
                "Cashier" -> Cashier
                "WarehouseManager" -> WarehouseManager
                "Administrator" -> Administrator
                else -> throw IllegalStateException("Must be Cashier or WarehouseManager or Administrator")
            }
        }
    }
}

@kotlinx.serialization.Serializable
data class StylizedColorDataModel(
    val themeId: Long,
    val valueHex: String
)

@kotlinx.serialization.Serializable
data class StylizedColorGroupDataModel(
    val id: Long,
    val values: List<StylizedColorDataModel>
)

@kotlinx.serialization.Serializable
data class StylizedDimensionDataModel(
    val sizeModeId: Long,
    val value: Float
)

@kotlinx.serialization.Serializable
data class StylizedDimensionGroupDataModel(
    val id: Long,
    val values: List<StylizedDimensionDataModel>
)

@kotlinx.serialization.Serializable
data class StylizedDrawablePathsDataModel(
    val themeId: Long,
    val path: String
)

@kotlinx.serialization.Serializable
data class StylizedDrawablePathsGroupDataModel(
    val id: Long,
    val values: List<StylizedDrawablePathsDataModel>
)

@kotlinx.serialization.Serializable
data class SubscriptionDataModel(
    val id: String,
    val startTime: Long,
    val endTime: Long
)

@kotlinx.serialization.Serializable
data class SubscriptionPlanDataModel(
    val id: Int,
    val name: String,
    val storesCount: Int,
    val cashRegistersCount: Int,
    val monthlyPrice: Double
)


@kotlinx.serialization.Serializable
data class SupplierDataModel(
    val id: String = "",
    val userIds: List<String> = emptyList(),
    val typeIds: List<String>? = null,
    val categoryIds: List<String> = emptyList(),
    val name: List<LocalizedStringDataModel> = emptyList(),
    val phoneNumbers: List<String>? = null,
    val emails: List<String>? = null,
    val addedAt: Long = 0L,
    val isActive: Boolean = true
) {
    fun isMineForUser(userId: String?): Boolean = !userId.isNullOrBlank() && userIds.contains(userId)
    fun isGenericSupplier(): Boolean = userIds.isEmpty()
}

fun List<SupplierDataModel>.upsertById(item: SupplierDataModel): List<SupplierDataModel> {
    val index = indexOfFirst { it.id == item.id }
    return if (index < 0) this + item else toMutableList().also { it[index] = item }
}

const val AITA_TOKEN_NEVER_EXPIRES_AT_MILLIS = 253402300799000L

private fun decodeJwtBase64UrlPayload(segment: String): String? = runCatching {
    val output = mutableListOf<Byte>()
    var buffer = 0
    var bitCount = 0

    for (char in segment) {
        if (char == '=') break
        val value = when (char) {
            in 'A'..'Z' -> char.code - 'A'.code
            in 'a'..'z' -> char.code - 'a'.code + 26
            in '0'..'9' -> char.code - '0'.code + 52
            '-' -> 62
            '_' -> 63
            else -> return@runCatching null
        }

        buffer = (buffer shl 6) or value
        bitCount += 6

        if (bitCount >= 8) {
            bitCount -= 8
            output += ((buffer shr bitCount) and 0xFF).toByte()
        }
    }

    output.toByteArray().decodeToString()
}.getOrNull()

private fun jwtAccessExpiryMillis(accessToken: String): Long? = runCatching {
    val payloadSegment = accessToken.split('.').getOrNull(1) ?: return@runCatching null
    val payload = decodeJwtBase64UrlPayload(payloadSegment) ?: return@runCatching null
    val expSeconds = Regex("""\"exp\"\s*:\s*(\d+)""")
        .find(payload)
        ?.groupValues
        ?.getOrNull(1)
        ?.toLongOrNull()
        ?: return@runCatching null

    if (expSeconds >= Long.MAX_VALUE / 1000L) Long.MAX_VALUE else expSeconds * 1000L
}.getOrNull()

@PublishedApi
internal fun TokenPair.effectiveAccessExpiryTimeMillis(): Long = when {
    accessToken.isBlank() -> 0L
    else -> jwtAccessExpiryMillis(accessToken) ?: AITA_TOKEN_NEVER_EXPIRES_AT_MILLIS
}

@PublishedApi
internal fun TokenPair.accessTokenNeedsRefreshForNetwork(
    nowMillis: Long = getCurrentTimeMillis(),
    skewMillis: Long = REALTIME_ACCESS_TOKEN_REFRESH_SKEW_MILLIS
): Boolean {
    if (refreshToken.isBlank()) return false
    if (accessToken.isBlank()) return true
    val jwtExpiry = jwtAccessExpiryMillis(accessToken) ?: return false
    return jwtExpiry <= nowMillis + skewMillis
}

@PublishedApi
internal fun TokenPair.accessTokenIsStillUsableForNetwork(nowMillis: Long = getCurrentTimeMillis()): Boolean {
    if (accessToken.isBlank()) return false
    val jwtExpiry = jwtAccessExpiryMillis(accessToken) ?: return true
    return jwtExpiry > nowMillis + 2_000L
}

@PublishedApi
internal fun accessJwtExpiresBeforeOrAt(accessToken: String, deadlineMillis: Long): Boolean =
    jwtAccessExpiryMillis(accessToken)?.let { it <= deadlineMillis } ?: false

@kotlinx.serialization.Serializable
data class TokenPair(
    val accessToken: String = "",
    val accessExpiryTime: Long = AITA_TOKEN_NEVER_EXPIRES_AT_MILLIS,
    val refreshToken: String = "",
    val refreshExpiryTime: Long = AITA_TOKEN_NEVER_EXPIRES_AT_MILLIS
)

@kotlinx.serialization.Serializable
data class ClientDeviceInfoDataModel(
    val installationId: String = "",
    val deviceName: String = "",
    val platformName: String = "",
    val osName: String = "",
    val appName: String = "AITA",
    val appVersion: String = "",
    val localeLanguage: String = ""
)

@kotlinx.serialization.Serializable
data class PendingSessionCleanupDataModel(
    val refreshToken: String = "",
    val queuedAtMillis: Long = 0L,
    val reason: String = "local_logout",
    val deviceInfo: ClientDeviceInfoDataModel = ClientDeviceInfoDataModel(),
    val workshiftEnd: WorkshiftEndRequestDataModel? = null,
    val workshiftStoreId: String? = null
)

@kotlinx.serialization.Serializable
data class LogoutCleanupRequestDataModel(
    val refreshToken: String = "",
    val queuedAtMillis: Long = 0L,
    val reason: String = "local_logout",
    val deviceInfo: ClientDeviceInfoDataModel = ClientDeviceInfoDataModel(),
    val workshiftEnd: WorkshiftEndRequestDataModel? = null,
    val workshiftStoreId: String? = null
)

@kotlinx.serialization.Serializable
data class PendingWorkshiftEndDataModel(
    val clientOperationId: String = "",
    val workshiftId: String = "",
    val storeId: String = "",
    val endedAtMillis: Long = 0L,
    val queuedAtMillis: Long = 0L,
    val accessToken: String = "",
    val refreshToken: String = "",
    val deviceInfo: ClientDeviceInfoDataModel = ClientDeviceInfoDataModel(),
    val attemptCount: Int = 0,
    val lastError: String? = null
)

@kotlinx.serialization.Serializable
data class SecuritySessionDataModel(
    val id: String,
    val userId: String,
    val deviceName: String = "",
    val platformName: String = "",
    val osName: String = "",
    val appName: String = "",
    val appVersion: String = "",
    val localeLanguage: String = "",
    val ipAddress: String = "",
    val userAgent: String = "",
    val createdAtMillis: Long = 0L,
    val expiresAtMillis: Long = 0L,
    val revokedAtMillis: Long? = null,
    val current: Boolean = false,
    val active: Boolean = true
)

@kotlinx.serialization.Serializable
data class SecuritySessionHistoryDataModel(
    val id: String = "",
    val userId: String = "",
    val sessionId: String? = null,
    val eventType: String = "",
    val title: List<LocalizedStringDataModel> = emptyList(),
    val details: List<LocalizedStringDataModel> = emptyList(),
    val deviceName: String = "",
    val platformName: String = "",
    val osName: String = "",
    val appName: String = "",
    val appVersion: String = "",
    val ipAddress: String = "",
    val createdAtMillis: Long = 0L,
    val metadata: Map<String, String> = emptyMap()
)

@kotlinx.serialization.Serializable
data class SecuritySessionRevokeRequestDataModel(
    val sessionId: String
)

fun buildCurrentClientDeviceInfo(): ClientDeviceInfoDataModel {
    val platformInfo = runCatching { getClientDeviceInfo?.invoke() }.getOrNull()

    return ClientDeviceInfoDataModel(
        installationId = platformInfo?.installationId.orEmpty(),
        deviceName = platformInfo?.deviceName?.takeIf { it.isNotBlank() } ?: getPlatformName(),
        platformName = platformInfo?.platformName?.takeIf { it.isNotBlank() } ?: getPlatformName(),
        osName = platformInfo?.osName.orEmpty(),
        appName = platformInfo?.appName?.takeIf { it.isNotBlank() } ?: globalAppConfigurationState.payloadValue.appName.first,
        appVersion = platformInfo?.appVersion.orEmpty(),
        localeLanguage = platformInfo?.localeLanguage?.takeIf { it.isNotBlank() } ?: getSystemLocaleLanguage()
    )
}

@PublishedApi
internal fun safeHttpHeaderValue(value: String, maxLength: Int = 256): String {
    val asciiOnly = buildString {
        value.trim()
            .replace('•', '-')
            .replace('–', '-')
            .replace('—', '-')
            .forEach { character ->
                when {
                    character == '\t' -> append(' ')
                    character.code in 0x20..0x7E -> append(character)
                    else -> append(' ')
                }
            }
    }

    return asciiOnly
        .replace(Regex(" +"), " ")
        .trim()
        .take(maxLength)
        .trim()
}

@PublishedApi
internal fun safeHttpHeaderValueOrNull(value: String, maxLength: Int = 256): String? =
    safeHttpHeaderValue(value, maxLength).takeIf { it.isNotBlank() }

fun currentClientDeviceInfoHeaders(deviceInfo: ClientDeviceInfoDataModel = buildCurrentClientDeviceInfo()): Map<String, String> = buildMap {
    safeHttpHeaderValueOrNull(deviceInfo.installationId)?.let { put(AITA_DEVICE_INSTALLATION_ID_HEADER, it) }
    safeHttpHeaderValueOrNull(deviceInfo.deviceName)?.let { put(AITA_DEVICE_NAME_HEADER, it) }
    safeHttpHeaderValueOrNull(deviceInfo.platformName)?.let { put(AITA_DEVICE_PLATFORM_HEADER, it) }
    safeHttpHeaderValueOrNull(deviceInfo.osName)?.let { put(AITA_DEVICE_OS_HEADER, it) }
    safeHttpHeaderValueOrNull(deviceInfo.appName)?.let { put(AITA_DEVICE_APP_NAME_HEADER, it) }
    safeHttpHeaderValueOrNull(deviceInfo.appVersion)?.let { put(AITA_DEVICE_APP_VERSION_HEADER, it) }
    safeHttpHeaderValueOrNull(deviceInfo.localeLanguage)?.let { put(AITA_DEVICE_LOCALE_HEADER, it) }
}


@kotlinx.serialization.Serializable
data class AnalyticsReturnReasonDataModel(
    val reason: String = "",
    val quantity: Double = 0.0,
    val transactionCount: Int = 0,
    val amount: Double = 0.0
)

@kotlinx.serialization.Serializable
data class AnalyticsRankedItemDataModel(
    val id: String = "",
    val name: List<LocalizedStringDataModel> = emptyList(),
    val subtitle: String = "",
    val quantity: Double = 0.0,
    val transactionCount: Int = 0,
    val amount: Double = 0.0,
    val costEstimate: Double = 0.0,
    val profitEstimate: Double = 0.0,
    val currencyCode: String = "",
    val returnReasons: List<AnalyticsReturnReasonDataModel> = emptyList()
)

@kotlinx.serialization.Serializable
data class AnalyticsBucketDataModel(
    val id: String = "",
    val label: String = "",
    val sortKey: Long = 0L,
    val transactionCount: Int = 0,
    val amount: Double = 0.0,
    val quantity: Double = 0.0,
    val cash: Double = 0.0,
    val cashless: Double = 0.0,
    val debt: Double = 0.0
)

@kotlinx.serialization.Serializable
data class StoreAnalyticsDashboardDataModel(
    val storeId: String = "",
    val startMillis: Long = 0L,
    val endMillisExclusive: Long = Long.MAX_VALUE,
    val currencyCode: String = "",
    val goodsItemIdFilter: String? = null,
    val supplierIdFilter: String? = null,
    val categoryIdFilter: String? = null,

    val grossSales: Double = 0.0,
    val returnsAmount: Double = 0.0,
    val supplyCost: Double = 0.0,
    val netRevenue: Double = 0.0,
    val estimatedSalesCost: Double = 0.0,
    val estimatedGrossProfit: Double = 0.0,
    val estimatedMarginPercent: Double = 0.0,

    val saleCount: Int = 0,
    val returnCount: Int = 0,
    val supplyCount: Int = 0,
    val transactionCount: Int = 0,
    val averageSale: Double = 0.0,
    val averageItemsPerSale: Double = 0.0,

    val cashTotal: Double = 0.0,
    val cashlessTotal: Double = 0.0,
    val debtTotal: Double = 0.0,
    val cashSharePercent: Double = 0.0,
    val cashlessSharePercent: Double = 0.0,
    val debtSharePercent: Double = 0.0,

    val soldQuantity: Double = 0.0,
    val returnedQuantity: Double = 0.0,
    val suppliedQuantity: Double = 0.0,

    val stockValueAtSupplyPrice: Double = 0.0,
    val stockValueAtSalePrice: Double = 0.0,
    val activeStockQuantity: Double = 0.0,
    val lowStockItemCount: Int = 0,
    val outOfStockItemCount: Int = 0,
    val expiredBatchCount: Int = 0,
    val expiringSoonBatchCount: Int = 0,
    val sellThroughPercentEstimate: Double = 0.0,

    val topItemsByRevenue: List<AnalyticsRankedItemDataModel> = emptyList(),
    val topItemsByQuantity: List<AnalyticsRankedItemDataModel> = emptyList(),
    val topReturnedItemsByQuantity: List<AnalyticsRankedItemDataModel> = emptyList(),
    val topReturnedItemsByAmount: List<AnalyticsRankedItemDataModel> = emptyList(),
    val slowMovingItems: List<AnalyticsRankedItemDataModel> = emptyList(),
    val salesByDay: List<AnalyticsBucketDataModel> = emptyList(),
    val salesByHour: List<AnalyticsBucketDataModel> = emptyList()
)

@kotlinx.serialization.Serializable
data class TransactionDataModel(
    val id: String,
    val workshiftId: Long,
    val type: String,
    val storeId: String,
    val goodsInTransaction: List<GoodsItemInTransactionDataModel>,
    val paidCash: Double,
    val paidCard: Double,
    val cardPaymentOptionId: Int,
    val debtor: DebtorDataModel? = null,
    val timeMillis: Long,
    val clientOperationId: String = ""
)

private data class MutableAnalyticsReturnReasonAccumulator(
    val reason: String,
    var quantity: Double = 0.0,
    var amount: Double = 0.0,
    val transactionIds: MutableSet<String> = mutableSetOf()
) {
    fun toReturnReason(): AnalyticsReturnReasonDataModel = AnalyticsReturnReasonDataModel(
        reason = reason,
        quantity = quantity.roundAnalyticsNumber(),
        transactionCount = transactionIds.size,
        amount = amount.roundMoney()
    )
}

private data class MutableAnalyticsItemAccumulator(
    val id: String,
    var name: List<LocalizedStringDataModel>,
    var subtitle: String,
    var quantity: Double = 0.0,
    var amount: Double = 0.0,
    var cost: Double = 0.0,
    val transactionIds: MutableSet<String> = mutableSetOf(),
    var currencyCode: String = "",
    val returnReasonAccumulators: MutableMap<String, MutableAnalyticsReturnReasonAccumulator> = linkedMapOf()
) {
    fun addReturnReason(reason: String, quantity: Double, amount: Double, transactionId: String) {
        val normalizedReason = reason.trim().take(MAX_RETURN_REASON_LENGTH)
        val accumulator = returnReasonAccumulators.getOrPut(normalizedReason) {
            MutableAnalyticsReturnReasonAccumulator(normalizedReason)
        }
        accumulator.quantity += quantity
        accumulator.amount += amount
        accumulator.transactionIds.add(transactionId)
    }

    fun toRankedItem(): AnalyticsRankedItemDataModel = AnalyticsRankedItemDataModel(
        id = id,
        name = name,
        subtitle = subtitle,
        quantity = quantity.roundAnalyticsNumber(),
        transactionCount = transactionIds.size,
        amount = amount.roundMoney(),
        costEstimate = cost.roundMoney(),
        profitEstimate = (amount - cost).roundMoney(),
        currencyCode = currencyCode,
        returnReasons = returnReasonAccumulators.values
            .map { it.toReturnReason() }
            .sortedWith(compareByDescending<AnalyticsReturnReasonDataModel> { it.quantity }.thenByDescending { it.transactionCount }.thenBy { it.reason })
    )
}

private data class MutableAnalyticsBucketAccumulator(
    val id: String,
    val label: String,
    val sortKey: Long,
    var transactionCount: Int = 0,
    var amount: Double = 0.0,
    var quantity: Double = 0.0,
    var cash: Double = 0.0,
    var cashless: Double = 0.0,
    var debt: Double = 0.0
) {
    fun toBucket(): AnalyticsBucketDataModel = AnalyticsBucketDataModel(
        id = id,
        label = label,
        sortKey = sortKey,
        transactionCount = transactionCount,
        amount = amount.roundMoney(),
        quantity = quantity.roundAnalyticsNumber(),
        cash = cash.roundMoney(),
        cashless = cashless.roundMoney(),
        debt = debt.roundMoney()
    )
}

private fun Double.roundAnalyticsNumber(): Double = kotlin.math.round(this * 1000.0) / 1000.0

private fun analyticsLineAmount(line: GoodsItemInTransactionDataModel): Double =
    (line.quantity * line.pricePerUnit).roundMoney()

private fun analyticsTransactionTotal(transaction: TransactionDataModel): Double =
    transaction.goodsInTransaction.sumOf { analyticsLineAmount(it) }.roundMoney()

private fun analyticsLineCurrency(line: GoodsItemInTransactionDataModel, fallback: String): String =
    line.currencyCode?.takeIf { it.isNotBlank() } ?: fallback

private fun analyticsDateLabel(timeMillis: Long): String {
    val date = Instant.fromEpochMilliseconds(timeMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
    return "${date.year}-${date.monthNumber.toString().padStart(2, '0')}-${date.dayOfMonth.toString().padStart(2, '0')}"
}

private fun analyticsDayStartSortKey(timeMillis: Long): Long {
    val date = Instant.fromEpochMilliseconds(timeMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
    return date.toString().filter { it.isDigit() }.toLongOrNull() ?: timeMillis
}

private fun analyticsHourOfDay(timeMillis: Long): Int =
    Instant.fromEpochMilliseconds(timeMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .hour

private fun GoodsItemDataModel.analyticsEstimatedCostPerUnit(batches: List<GoodsBatchDataModel>): Double {
    val activeBatch = activeShelfBatchId?.let { activeId -> batches.firstOrNull { it.id == activeId } }
        ?: batches.filter { it.goodsItemId == id && it.isActive }.maxByOrNull { it.updatedAtMillis }

    return activeBatch?.supplyPrice?.price?.toMoneyDouble()
        ?: supplyPrices.firstOrNull()?.price?.toMoneyDouble()
        ?: 0.0
}

private fun GoodsItemDataModel.analyticsEstimatedSalePricePerUnit(batches: List<GoodsBatchDataModel>): Double {
    val activeBatch = activeShelfBatchId?.let { activeId -> batches.firstOrNull { it.id == activeId } }
        ?: batches.filter { it.goodsItemId == id && it.isActive }.maxByOrNull { it.updatedAtMillis }

    return activeBatch?.salePriceOverride?.price?.toMoneyDouble()
        ?: salePrices.firstOrNull()?.price?.toMoneyDouble()
        ?: 0.0
}

private fun GoodsItemInTransactionDataModel.analyticsItemId(stockByBarcode: Map<String, GoodsItemDataModel>): String {
    goodsItemId?.takeIf { it.isNotBlank() }?.let { return it }
    stockByBarcode[barcode.toStoredGoodsItemBarcode()]?.id?.takeIf { it.isNotBlank() }?.let { return it }
    stockByBarcode[barcode]?.id?.takeIf { it.isNotBlank() }?.let { return it }
    return barcode.ifBlank { name.firstOrNull { it.value.isNotBlank() }?.value.orEmpty() }.ifBlank { "unknown" }
}

private fun GoodsItemInTransactionDataModel.analyticsItemName(
    item: GoodsItemDataModel?,
    fallbackId: String
): List<LocalizedStringDataModel> {
    return name.takeIf { it.any { value -> value.value.isNotBlank() } }
        ?: item?.name?.takeIf { it.any { value -> value.value.isNotBlank() } }
        ?: listOf(LocalizedStringDataModel("main", barcode.ifBlank { fallbackId }))
}

private fun GoodsItemInTransactionDataModel.analyticsResolvedItem(
    stockById: Map<String, GoodsItemDataModel>,
    stockByBarcode: Map<String, GoodsItemDataModel>
): GoodsItemDataModel? {
    goodsItemId?.takeIf { it.isNotBlank() }?.let { itemId -> stockById[itemId]?.let { return it } }
    stockByBarcode[barcode.toStoredGoodsItemBarcode()]?.let { return it }
    stockByBarcode[barcode]?.let { return it }
    return null
}

private fun String?.cleanAnalyticsFilterId(): String? =
    this?.trim()?.takeIf { it.isNotBlank() }

private fun GoodsItemDataModel.matchesAnalyticsStockScope(
    goodsItemIdFilter: String?,
    supplierIdFilter: String?,
    categoryIdFilter: String?,
    batchesByItem: Map<String, List<GoodsBatchDataModel>>
): Boolean {
    val itemId = goodsItemIdFilter.cleanAnalyticsFilterId()
    val supplierId = supplierIdFilter.cleanAnalyticsFilterId()
    val categoryId = categoryIdFilter.cleanAnalyticsFilterId()

    if (itemId != null && id != itemId) return false
    if (categoryId != null && categoryId !in categoryIds) return false
    if (supplierId != null) {
        val fromBatch = batchesByItem[id].orEmpty().any { batch -> batch.supplierId == supplierId }
        if (!fromBatch) return false
    }
    return true
}

private fun GoodsItemInTransactionDataModel.matchesAnalyticsLineScope(
    stockById: Map<String, GoodsItemDataModel>,
    stockByBarcode: Map<String, GoodsItemDataModel>,
    batchesByItem: Map<String, List<GoodsBatchDataModel>>,
    goodsItemIdFilter: String?,
    supplierIdFilter: String?,
    categoryIdFilter: String?
): Boolean {
    val itemId = goodsItemIdFilter.cleanAnalyticsFilterId()
    val supplierId = supplierIdFilter.cleanAnalyticsFilterId()
    val categoryId = categoryIdFilter.cleanAnalyticsFilterId()

    if (itemId == null && supplierId == null && categoryId == null) return true

    val item = analyticsResolvedItem(stockById, stockByBarcode)
    val resolvedItemId = item?.id ?: analyticsItemId(stockByBarcode)

    if (itemId != null && resolvedItemId != itemId) return false
    if (categoryId != null && item?.categoryIds?.contains(categoryId) != true) return false
    if (supplierId != null) {
        val directSupplier = supplierIdText?.trim()?.takeIf { it.isNotBlank() }
        val legacySupplier = supplierId?.toString()?.takeIf { it.isNotBlank() }
        val fromBatch = item?.let { goodsItem -> batchesByItem[goodsItem.id].orEmpty().any { batch -> batch.supplierId == supplierId } } == true
        if (directSupplier != supplierId && legacySupplier != supplierId && !fromBatch) return false
    }

    return true
}

fun analyticsScopedTransactions(
    transactions: List<TransactionDataModel>,
    stock: List<GoodsItemDataModel> = emptyList(),
    batches: List<GoodsBatchDataModel> = emptyList(),
    goodsItemIdFilter: String? = null,
    supplierIdFilter: String? = null,
    categoryIdFilter: String? = null
): List<TransactionDataModel> {
    val itemFilter = goodsItemIdFilter.cleanAnalyticsFilterId()
    val supplierFilter = supplierIdFilter.cleanAnalyticsFilterId()
    val categoryFilter = categoryIdFilter.cleanAnalyticsFilterId()

    if (itemFilter == null && supplierFilter == null && categoryFilter == null) return transactions

    val stockById = stock.associateBy { it.id }
    val stockByBarcode = stock
        .flatMap { item -> item.allBarcodeValues().flatMap { barcode -> listOf(barcode, barcode.toStoredGoodsItemBarcode()) }.map { it to item } }
        .associate { it }
    val batchesByItem = batches.groupBy { it.goodsItemId }

    return transactions.mapNotNull { transaction ->
        val originalTotal = analyticsTransactionTotal(transaction).takeIf { it > 0.0 }
        val scopedLines = transaction.goodsInTransaction.filter { line ->
            line.matchesAnalyticsLineScope(
                stockById = stockById,
                stockByBarcode = stockByBarcode,
                batchesByItem = batchesByItem,
                goodsItemIdFilter = itemFilter,
                supplierIdFilter = supplierFilter,
                categoryIdFilter = categoryFilter
            )
        }
        if (scopedLines.isEmpty()) return@mapNotNull null

        val scopedTotal = scopedLines.sumOf { analyticsLineAmount(it) }.roundMoney()
        val paymentRatio = originalTotal?.let { total -> (scopedTotal / total).coerceIn(0.0, 1.0) } ?: 1.0

        transaction.copy(
            goodsInTransaction = scopedLines,
            paidCash = (transaction.paidCash * paymentRatio).roundMoney(),
            paidCard = (transaction.paidCard * paymentRatio).roundMoney()
        )
    }
}

fun buildStoreAnalyticsDashboard(
    storeId: String,
    startMillis: Long = 0L,
    endMillisExclusive: Long = Long.MAX_VALUE,
    transactions: List<TransactionDataModel>,
    stock: List<GoodsItemDataModel> = emptyList(),
    batches: List<GoodsBatchDataModel> = emptyList(),
    fallbackCurrencyCode: String = "",
    goodsItemIdFilter: String? = null,
    supplierIdFilter: String? = null,
    categoryIdFilter: String? = null
): StoreAnalyticsDashboardDataModel {
    val normalizedEnd = if (endMillisExclusive <= 0L) Long.MAX_VALUE else endMillisExclusive
    val baseTransactions = transactions
        .filter { it.storeId == storeId || storeId.isBlank() }
        .filter { it.timeMillis >= startMillis && it.timeMillis < normalizedEnd }

    val allBatchesByItem = batches.groupBy { it.goodsItemId }
    val scopedStock = stock.filter { item ->
        item.matchesAnalyticsStockScope(
            goodsItemIdFilter = goodsItemIdFilter,
            supplierIdFilter = supplierIdFilter,
            categoryIdFilter = categoryIdFilter,
            batchesByItem = allBatchesByItem
        )
    }
    val scopedStockIds = scopedStock.map { it.id }.toSet()
    val scopedBatches = batches.filter { batch ->
        (scopedStockIds.isEmpty() && goodsItemIdFilter.cleanAnalyticsFilterId() == null && supplierIdFilter.cleanAnalyticsFilterId() == null && categoryIdFilter.cleanAnalyticsFilterId() == null) ||
                batch.goodsItemId in scopedStockIds ||
                supplierIdFilter.cleanAnalyticsFilterId()?.let { supplierId -> batch.supplierId == supplierId } == true
    }
    val scopedTransactions = analyticsScopedTransactions(
        transactions = baseTransactions,
        stock = stock,
        batches = batches,
        goodsItemIdFilter = goodsItemIdFilter,
        supplierIdFilter = supplierIdFilter,
        categoryIdFilter = categoryIdFilter
    )

    val saleTransactions = scopedTransactions.filter { it.type == "purchase" }
    val returnTransactions = scopedTransactions.filter { it.type == "return" }
    val supplyTransactions = scopedTransactions.filter { it.type == "accept" }

    val currencyCode = fallbackCurrencyCode.ifBlank {
        scopedTransactions.asSequence()
            .flatMap { it.goodsInTransaction.asSequence() }
            .mapNotNull { it.currencyCode?.takeIf { code -> code.isNotBlank() } }
            .firstOrNull()
            ?: scopedStock.asSequence()
                .flatMap { item -> (item.salePrices + item.supplyPrices + item.returnPrices + item.wholesalePrices).asSequence() }
                .map { it.currency }
                .firstOrNull { it.isNotBlank() }
            ?: scopedBatches.asSequence()
                .map { it.supplyPrice.currency }
                .firstOrNull { it.isNotBlank() }
            ?: ""
    }

    val stockById = scopedStock.associateBy { it.id }
    val stockByBarcode = scopedStock
        .flatMap { item -> item.allBarcodeValues().flatMap { barcode -> listOf(barcode, barcode.toStoredGoodsItemBarcode()) }.map { it to item } }
        .associate { it }
    val batchesByItem = scopedBatches.groupBy { it.goodsItemId }

    val grossSales = saleTransactions.sumOf { analyticsTransactionTotal(it) }.roundMoney()
    val returnsAmount = returnTransactions.sumOf { analyticsTransactionTotal(it) }.roundMoney()
    val supplyCost = supplyTransactions.sumOf { analyticsTransactionTotal(it) }.roundMoney()
    val netRevenue = (grossSales - returnsAmount).roundMoney()

    val topRevenue = linkedMapOf<String, MutableAnalyticsItemAccumulator>()
    val topQuantity = linkedMapOf<String, MutableAnalyticsItemAccumulator>()
    val topReturnedQuantity = linkedMapOf<String, MutableAnalyticsItemAccumulator>()
    val topReturnedAmount = linkedMapOf<String, MutableAnalyticsItemAccumulator>()
    val soldQuantityByItem = mutableMapOf<String, Double>()
    var estimatedSalesCost = 0.0
    var soldQuantity = 0.0

    saleTransactions.forEach { transaction ->
        transaction.goodsInTransaction.forEach { line ->
            val itemId = line.analyticsItemId(stockByBarcode)
            val item = stockById[itemId]
            val itemBatches = batchesByItem[itemId].orEmpty()
            val amount = analyticsLineAmount(line)
            val costPerUnit = item?.analyticsEstimatedCostPerUnit(itemBatches) ?: 0.0
            val cost = (costPerUnit * line.quantity).roundMoney()
            val name = line.analyticsItemName(item, itemId)
            val subtitle = line.barcode.takeIf { it.isNotBlank() } ?: item?.barcodes?.firstOrNull().orEmpty()
            val lineCurrency = analyticsLineCurrency(line, currencyCode)

            estimatedSalesCost += cost
            soldQuantity += line.quantity
            soldQuantityByItem[itemId] = (soldQuantityByItem[itemId] ?: 0.0) + line.quantity

            val revenueAcc = topRevenue.getOrPut(itemId) {
                MutableAnalyticsItemAccumulator(itemId, name, subtitle, currencyCode = lineCurrency)
            }
            revenueAcc.name = if (revenueAcc.name.isEmpty()) name else revenueAcc.name
            revenueAcc.subtitle = revenueAcc.subtitle.ifBlank { subtitle }
            revenueAcc.quantity += line.quantity
            revenueAcc.amount += amount
            revenueAcc.cost += cost
            revenueAcc.transactionIds.add(transaction.id)
            revenueAcc.currencyCode = revenueAcc.currencyCode.ifBlank { lineCurrency }

            val quantityAcc = topQuantity.getOrPut(itemId) {
                MutableAnalyticsItemAccumulator(itemId, name, subtitle, currencyCode = lineCurrency)
            }
            quantityAcc.name = if (quantityAcc.name.isEmpty()) name else quantityAcc.name
            quantityAcc.subtitle = quantityAcc.subtitle.ifBlank { subtitle }
            quantityAcc.quantity += line.quantity
            quantityAcc.amount += amount
            quantityAcc.cost += cost
            quantityAcc.transactionIds.add(transaction.id)
            quantityAcc.currencyCode = quantityAcc.currencyCode.ifBlank { lineCurrency }
        }
    }

    returnTransactions.forEach { transaction ->
        transaction.goodsInTransaction.forEach { line ->
            val itemId = line.analyticsItemId(stockByBarcode)
            val item = stockById[itemId]
            val itemBatches = batchesByItem[itemId].orEmpty()
            val amount = analyticsLineAmount(line)
            val costPerUnit = item?.analyticsEstimatedCostPerUnit(itemBatches) ?: 0.0
            val cost = (costPerUnit * line.quantity).roundMoney()
            val name = line.analyticsItemName(item, itemId)
            val subtitle = line.barcode.takeIf { it.isNotBlank() } ?: item?.barcodes?.firstOrNull().orEmpty()
            val lineCurrency = analyticsLineCurrency(line, currencyCode)

            val quantityAcc = topReturnedQuantity.getOrPut(itemId) {
                MutableAnalyticsItemAccumulator(itemId, name, subtitle, currencyCode = lineCurrency)
            }
            quantityAcc.name = if (quantityAcc.name.isEmpty()) name else quantityAcc.name
            quantityAcc.subtitle = quantityAcc.subtitle.ifBlank { subtitle }
            quantityAcc.quantity += line.quantity
            quantityAcc.amount += amount
            quantityAcc.cost += cost
            quantityAcc.transactionIds.add(transaction.id)
            quantityAcc.currencyCode = quantityAcc.currencyCode.ifBlank { lineCurrency }
            quantityAcc.addReturnReason(line.returnReason, line.quantity, amount, transaction.id)

            val amountAcc = topReturnedAmount.getOrPut(itemId) {
                MutableAnalyticsItemAccumulator(itemId, name, subtitle, currencyCode = lineCurrency)
            }
            amountAcc.name = if (amountAcc.name.isEmpty()) name else amountAcc.name
            amountAcc.subtitle = amountAcc.subtitle.ifBlank { subtitle }
            amountAcc.quantity += line.quantity
            amountAcc.amount += amount
            amountAcc.cost += cost
            amountAcc.transactionIds.add(transaction.id)
            amountAcc.currencyCode = amountAcc.currencyCode.ifBlank { lineCurrency }
            amountAcc.addReturnReason(line.returnReason, line.quantity, amount, transaction.id)
        }
    }

    val returnedQuantity = returnTransactions.flatMap { it.goodsInTransaction }.sumOf { it.quantity }.roundAnalyticsNumber()
    val suppliedQuantity = supplyTransactions.flatMap { it.goodsInTransaction }.sumOf { it.quantity }.roundAnalyticsNumber()
    estimatedSalesCost = estimatedSalesCost.roundMoney()
    val estimatedGrossProfit = (netRevenue - estimatedSalesCost).roundMoney()
    val estimatedMarginPercent = if (netRevenue > 0.0) (estimatedGrossProfit / netRevenue * 100.0).roundAnalyticsNumber() else 0.0

    val salePaymentsTotal = saleTransactions.sumOf { it.paidCash + it.paidCard }.roundMoney()
    val cashTotal = saleTransactions.sumOf { it.paidCash }.roundMoney()
    val cashlessTotal = saleTransactions.sumOf { it.paidCard }.roundMoney()
    val debtTotal = saleTransactions.sumOf { tx -> (analyticsTransactionTotal(tx) - tx.paidCash - tx.paidCard).coerceAtLeast(0.0) }.roundMoney()
    val paymentBase = (salePaymentsTotal + debtTotal).takeIf { it > 0.0 } ?: 0.0
    val cashShare = if (paymentBase > 0.0) cashTotal / paymentBase * 100.0 else 0.0
    val cashlessShare = if (paymentBase > 0.0) cashlessTotal / paymentBase * 100.0 else 0.0
    val debtShare = if (paymentBase > 0.0) debtTotal / paymentBase * 100.0 else 0.0

    val dailyBuckets = linkedMapOf<String, MutableAnalyticsBucketAccumulator>()
    val hourlyBuckets = linkedMapOf<String, MutableAnalyticsBucketAccumulator>()

    saleTransactions.forEach { transaction ->
        val amount = analyticsTransactionTotal(transaction)
        val quantity = transaction.goodsInTransaction.sumOf { it.quantity }
        val debt = (amount - transaction.paidCash - transaction.paidCard).coerceAtLeast(0.0)
        val dayLabel = analyticsDateLabel(transaction.timeMillis)
        val day = dailyBuckets.getOrPut(dayLabel) {
            MutableAnalyticsBucketAccumulator(dayLabel, dayLabel, analyticsDayStartSortKey(transaction.timeMillis))
        }
        day.transactionCount += 1
        day.amount += amount
        day.quantity += quantity
        day.cash += transaction.paidCash
        day.cashless += transaction.paidCard
        day.debt += debt

        val hour = analyticsHourOfDay(transaction.timeMillis)
        val hourId = hour.toString().padStart(2, '0')
        val hourBucket = hourlyBuckets.getOrPut(hourId) {
            MutableAnalyticsBucketAccumulator(hourId, "$hourId:00", hour.toLong())
        }
        hourBucket.transactionCount += 1
        hourBucket.amount += amount
        hourBucket.quantity += quantity
        hourBucket.cash += transaction.paidCash
        hourBucket.cashless += transaction.paidCard
        hourBucket.debt += debt
    }

    val activeBatches = scopedBatches.filter { it.isActive }
    val activeStockQuantityByItem = activeBatches.groupBy { it.goodsItemId }.mapValues { (_, itemBatches) ->
        itemBatches.sumOf { it.quantity.total }
    }
    val now = getCurrentTimeMillis()
    val expiringSoonCutoff = now + 14L * 24L * 60L * 60L * 1000L

    var stockValueAtSupplyPrice = 0.0
    var stockValueAtSalePrice = 0.0
    activeBatches.forEach { batch ->
        val item = stockById[batch.goodsItemId]
        val quantity = batch.quantity.total.coerceAtLeast(0.0)
        val supplyPrice = batch.supplyPrice.price.toMoneyDouble()
        val salePrice = batch.salePriceOverride?.price?.toMoneyDouble()
            ?: item?.analyticsEstimatedSalePricePerUnit(batchesByItem[batch.goodsItemId].orEmpty())
            ?: 0.0
        stockValueAtSupplyPrice += quantity * supplyPrice
        stockValueAtSalePrice += quantity * salePrice
    }

    val activeItems = scopedStock.filter { it.isActive }
    val outOfStockItemCount = activeItems.count { (activeStockQuantityByItem[it.id] ?: 0.0) <= 0.0 }
    val lowStockItemCount = activeItems.count { item ->
        val quantity = activeStockQuantityByItem[item.id] ?: 0.0
        quantity > 0.0 && quantity <= 5.0
    }
    val expiredBatchCount = activeBatches.count { batch -> batch.expirationDateMillis?.let { it < now } == true }
    val expiringSoonBatchCount = activeBatches.count { batch -> batch.expirationDateMillis?.let { it in now..expiringSoonCutoff } == true }
    val activeStockQuantity = activeStockQuantityByItem.values.sum().roundAnalyticsNumber()
    val sellThroughBase = soldQuantity + activeStockQuantity
    val sellThroughPercentEstimate = if (sellThroughBase > 0.0) (soldQuantity / sellThroughBase * 100.0).roundAnalyticsNumber() else 0.0

    val slowMovingItems = activeItems
        .map { item ->
            val stockQuantity = activeStockQuantityByItem[item.id] ?: 0.0
            val sold = soldQuantityByItem[item.id] ?: 0.0
            val cost = item.analyticsEstimatedCostPerUnit(batchesByItem[item.id].orEmpty()) * stockQuantity
            val saleValue = item.analyticsEstimatedSalePricePerUnit(batchesByItem[item.id].orEmpty()) * stockQuantity
            MutableAnalyticsItemAccumulator(
                id = item.id,
                name = item.name.ifEmpty { listOf(LocalizedStringDataModel("main", item.barcodes.firstOrNull().orEmpty().ifBlank { item.id })) },
                subtitle = item.barcodes.firstOrNull().orEmpty(),
                quantity = stockQuantity,
                amount = saleValue,
                cost = cost,
                currencyCode = currencyCode
            ).apply {
                if (sold > 0.0) transactionIds.add("sold")
            }.toRankedItem().copy(
                profitEstimate = sold.roundAnalyticsNumber()
            )
        }
        .filter { it.quantity > 0.0 }
        .sortedWith(compareBy<AnalyticsRankedItemDataModel> { it.profitEstimate > 0.0 }.thenByDescending { it.quantity })
        .take(10)

    return StoreAnalyticsDashboardDataModel(
        storeId = storeId,
        startMillis = startMillis,
        endMillisExclusive = normalizedEnd,
        currencyCode = currencyCode,
        goodsItemIdFilter = goodsItemIdFilter.cleanAnalyticsFilterId(),
        supplierIdFilter = supplierIdFilter.cleanAnalyticsFilterId(),
        categoryIdFilter = categoryIdFilter.cleanAnalyticsFilterId(),
        grossSales = grossSales,
        returnsAmount = returnsAmount,
        supplyCost = supplyCost,
        netRevenue = netRevenue,
        estimatedSalesCost = estimatedSalesCost,
        estimatedGrossProfit = estimatedGrossProfit,
        estimatedMarginPercent = estimatedMarginPercent,
        saleCount = saleTransactions.size,
        returnCount = returnTransactions.size,
        supplyCount = supplyTransactions.size,
        transactionCount = scopedTransactions.size,
        averageSale = if (saleTransactions.isNotEmpty()) (grossSales / saleTransactions.size).roundMoney() else 0.0,
        averageItemsPerSale = if (saleTransactions.isNotEmpty()) (soldQuantity / saleTransactions.size).roundAnalyticsNumber() else 0.0,
        cashTotal = cashTotal,
        cashlessTotal = cashlessTotal,
        debtTotal = debtTotal,
        cashSharePercent = cashShare.roundAnalyticsNumber(),
        cashlessSharePercent = cashlessShare.roundAnalyticsNumber(),
        debtSharePercent = debtShare.roundAnalyticsNumber(),
        soldQuantity = soldQuantity.roundAnalyticsNumber(),
        returnedQuantity = returnedQuantity,
        suppliedQuantity = suppliedQuantity,
        stockValueAtSupplyPrice = stockValueAtSupplyPrice.roundMoney(),
        stockValueAtSalePrice = stockValueAtSalePrice.roundMoney(),
        activeStockQuantity = activeStockQuantity,
        lowStockItemCount = lowStockItemCount,
        outOfStockItemCount = outOfStockItemCount,
        expiredBatchCount = expiredBatchCount,
        expiringSoonBatchCount = expiringSoonBatchCount,
        sellThroughPercentEstimate = sellThroughPercentEstimate,
        topItemsByRevenue = topRevenue.values.map { it.toRankedItem() }.sortedByDescending { it.amount }.take(10),
        topItemsByQuantity = topQuantity.values.map { it.toRankedItem() }.sortedByDescending { it.quantity }.take(10),
        topReturnedItemsByQuantity = topReturnedQuantity.values.map { it.toRankedItem() }.sortedByDescending { it.quantity }.take(10),
        topReturnedItemsByAmount = topReturnedAmount.values.map { it.toRankedItem() }.sortedByDescending { it.amount }.take(10),
        slowMovingItems = slowMovingItems,
        salesByDay = dailyBuckets.values.map { it.toBucket() }.sortedBy { it.sortKey },
        salesByHour = hourlyBuckets.values.map { it.toBucket() }.sortedBy { it.sortKey }
    )
}


fun GoodsItemInTransactionDataModel.lineTotalValue(): Double = quantity * pricePerUnit
fun TransactionDataModel.paidTotalValue(): Double = paidCash + paidCard
fun TransactionDataModel.grossLineTotalValue(): Double = goodsInTransaction.sumOf { it.lineTotalValue() }
fun TransactionDataModel.debtCreatedValue(): Double = (grossLineTotalValue() - paidTotalValue()).coerceAtLeast(0.0)

fun UserAccountDataModel.visibleWorkerInviteId(): String = publicId.ifBlank { id }

fun UserAccountDataModel.hasRole(roleId: String): Boolean = roleIds.contains(roleId)
fun UserAccountDataModel.canActAsStoreOwner(): Boolean = hasRole(USER_ROLE_STORE_OWNER) || ownedStoreIds.isNotEmpty()
fun UserAccountDataModel.canActAsStoreWorker(): Boolean = hasRole(USER_ROLE_STORE_WORKER) || managedStoreIds.isNotEmpty()
fun UserAccountDataModel.canActAsSupplier(): Boolean = hasRole(USER_ROLE_SUPPLIER) || decodeUserAccountIds(supplierAccountIds).isNotEmpty()
fun UserAccountDataModel.canActAsManufacturer(): Boolean = hasRole(USER_ROLE_MANUFACTURER) || decodeUserAccountIds(manufacturerAccountIds).isNotEmpty()
fun UserAccountDataModel.canActAsBuyer(): Boolean = hasRole(USER_ROLE_BUYER) || buyerAccountId != null

fun decodeUserAccountIds(raw: String?): List<String> = raw
    ?.let { value -> runCatching { jsonBase.decodeFromString<List<String>>(value) }.getOrNull() ?: value.split(',', ';', ' ') }
    .orEmpty()
    .map { it.trim() }
    .filter { it.isNotBlank() }
    .distinct()

fun StoreDataModel.visibleEmploymentId(): String = publicId.ifBlank { id }

@kotlinx.serialization.Serializable
data class UserAccountDataModel(
    val id: String,
    val publicId: String = "",
    val phoneNumber: String,
    val email: String,
    val firstName: String,
    val lastName: String,
    val countryLocale: String,
    val workerAccountIds: String?,
    val supplierAccountIds: String?,
    val roleIds: List<String> = emptyList(),
    val ownedStoreIds: List<String> = emptyList(),
    val managedStoreIds: List<String> = emptyList(),
    val manufacturerAccountIds: String? = null,
    val buyerAccountId: String? = null,
    val activeStoreId: String? = null,
    val appLanguage: String = DEFAULT_APP_LANGUAGE,
    val appThemeId: Long = DEFAULT_APP_THEME_ID,
    val appSizeModeId: Long = DEFAULT_APP_SIZE_MODE_ID,
    val createdAt: Long,
    val isActive: Boolean
)

@kotlinx.serialization.Serializable
class UserAccountUpdateDataModel(
    val account: UserAccountDataModel,
    val password: String,
    val newPassword: String?
)

@kotlinx.serialization.Serializable
data class UserAuthLogInDataModel(
    val login: String,
    val password: String,
    val deviceInfo: ClientDeviceInfoDataModel? = null
)

@kotlinx.serialization.Serializable
data class UserAuthSignUpDataModel(
    val phoneNumber: String,
    val email: String,
    val firstName: String,
    val lastName: String,
    val countryLocale: String,
    val password: String,
    val deviceInfo: ClientDeviceInfoDataModel? = null
)

@kotlinx.serialization.Serializable
data class UserBalanceDataModel(
    val value: String = "0",
    val currencyCode: String = "KZT",
    val history: List<BalanceHistoryEntryDataModel> = emptyList(),
    val aitaCurrencyCode: String = currencyCode.aitaCurrencyCode()
)

@kotlinx.serialization.Serializable
data class UserSettingsDataModel(
    val registrationTime: Long,
    val appLanguage: String,
    val appThemeId: Long,
    val appSizeModeId: Long
)

@kotlinx.serialization.Serializable
data class StoreWorkerDataModel(
    val id: String = "",
    val storeId: String = "",
    val storePublicId: String = "",
    val storeName: List<LocalizedStringDataModel> = emptyList(),
    val userId: String = "",
    val userPublicId: String = "",
    val phoneNumber: String = "",
    val email: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val roleId: String = WORKER_ROLE_STANDARD,
    val permissions: List<String> = STANDARD_STORE_PERMISSION_IDS,
    val requestedAtMillis: Long = 0L,
    val acceptedAtMillis: Long = 0L,
    val acceptedByUserId: String = "",
    val isActive: Boolean = true,
    val hasWorkshiftPassword: Boolean = false
) {
    val displayName: String
        get() = "${firstName.trim()} ${lastName.trim()}".trim().ifBlank { phoneNumber.asDisplayPhoneNumber().ifBlank { email.ifBlank { userId } } }
}

@kotlinx.serialization.Serializable
data class StoreWorkerRequestDataModel(
    val id: String = "",
    val storeId: String = "",
    val storePublicId: String = "",
    val storeName: List<LocalizedStringDataModel> = emptyList(),
    val requesterUserId: String = "",
    val requesterPublicId: String = "",
    val direction: String = WORKER_REQUEST_DIRECTION_USER_TO_STORE,
    val invitedByUserId: String? = null,
    val phoneNumber: String = "",
    val email: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val status: String = WORKER_REQUEST_STATUS_PENDING,
    val requestedAtMillis: Long = 0L,
    val decidedAtMillis: Long? = null,
    val decidedByUserId: String? = null,
    val roleId: String = WORKER_ROLE_STANDARD,
    val permissions: List<String> = STANDARD_STORE_PERMISSION_IDS,
    val note: String? = null,
    val noteLocalized: List<LocalizedStringDataModel> = emptyList(),
    val responseNote: String? = null,
    val responseNoteLocalized: List<LocalizedStringDataModel> = emptyList()
) {
    val displayName: String
        get() = "${firstName.trim()} ${lastName.trim()}".trim().ifBlank { phoneNumber.asDisplayPhoneNumber().ifBlank { email.ifBlank { requesterPublicId.ifBlank { requesterUserId } } } }
}

@kotlinx.serialization.Serializable
data class StoreWorkerRoleTemplateDataModel(
    val id: String = "",
    val storeId: String = "",
    val name: List<LocalizedStringDataModel> = emptyList(),
    val description: List<LocalizedStringDataModel> = emptyList(),
    val permissions: List<String> = STANDARD_STORE_PERMISSION_IDS,
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L,
    val isActive: Boolean = true
) {
    val displayName: String
        get() = name.firstOrNull { it.value.isNotBlank() }?.value.orEmpty().ifBlank { id }
}

@kotlinx.serialization.Serializable
data class StoreWorkerRoleTemplateUpsertRequestDataModel(
    val id: String = "",
    val storeId: String = "",
    val name: List<LocalizedStringDataModel> = emptyList(),
    val description: List<LocalizedStringDataModel> = emptyList(),
    val permissions: List<String> = STANDARD_STORE_PERMISSION_IDS
)

@kotlinx.serialization.Serializable
data class StoreWorkerRoleTemplateDeleteRequestDataModel(
    val templateId: String
)

@kotlinx.serialization.Serializable
data class WorkerEmploymentRequestCreateDataModel(
    val storeId: String,
    val note: String? = null,
    val noteLocalized: List<LocalizedStringDataModel> = emptyList()
)

@kotlinx.serialization.Serializable
data class WorkerEmploymentDecisionRequestDataModel(
    val requestId: String,
    val roleId: String = WORKER_ROLE_STANDARD,
    val permissions: List<String> = STANDARD_STORE_PERMISSION_IDS,
    val note: String? = null,
    val workerPassword: String? = null,
    val responseNote: String? = null,
    val responseNoteLocalized: List<LocalizedStringDataModel> = emptyList()
)

@kotlinx.serialization.Serializable
data class WorkerStoreInviteCreateDataModel(
    val userId: String,
    val roleId: String = WORKER_ROLE_STANDARD,
    val permissions: List<String> = STANDARD_STORE_PERMISSION_IDS,
    val note: String? = null,
    val workerPassword: String? = null,
    val noteLocalized: List<LocalizedStringDataModel> = emptyList()
)

@kotlinx.serialization.Serializable
data class WorkerStoreInvitationDecisionDataModel(
    val requestId: String,
    val note: String? = null,
    val responseNote: String? = null,
    val responseNoteLocalized: List<LocalizedStringDataModel> = emptyList()
)

@kotlinx.serialization.Serializable
data class WorkerPermissionsUpdateRequestDataModel(
    val workerId: String,
    val roleId: String,
    val permissions: List<String>,
    val workerPassword: String? = null
)

@kotlinx.serialization.Serializable
data class WorkerRemovalRequestDataModel(
    val workerId: String,
    val note: String? = null,
    val noteLocalized: List<LocalizedStringDataModel> = emptyList()
)

@kotlinx.serialization.Serializable
data class WorkerRemovalDecisionRequestDataModel(
    val requestId: String,
    val note: String? = null,
    val responseNote: String? = null,
    val responseNoteLocalized: List<LocalizedStringDataModel> = emptyList()
)

@kotlinx.serialization.Serializable
data class WorkerSelfPasswordUpdateRequestDataModel(
    val workerId: String,
    val workerPassword: String,
    val accountPassword: String = ""
)

@kotlinx.serialization.Serializable
data class WorkshiftStartRequestDataModel(
    val workerIdentifier: String,
    val password: String
)

@kotlinx.serialization.Serializable
data class WorkshiftEndRequestDataModel(
    val workshiftId: String? = null,
    val endedAtMillis: Long = 0L,
    val clientOperationId: String = "",
    val deviceInfo: ClientDeviceInfoDataModel = ClientDeviceInfoDataModel()
)

@kotlinx.serialization.Serializable
data class WorkshiftDataModel(
    val id: String = "",
    val storeId: String = "",
    val storePublicId: String = "",
    val storeName: List<LocalizedStringDataModel> = emptyList(),
    val workerMembershipId: String = "",
    val workerUserId: String = "",
    val workerPublicId: String = "",
    val workerDisplayName: String = "",
    val startedAtMillis: Long = 0L,
    val endedAtMillis: Long? = null,
    val startedByUserId: String = "",
    val endedByUserId: String? = null,
    val isActive: Boolean = true
)

@kotlinx.serialization.Serializable
data class OperationLogDataModel(
    val id: String = "",
    val rootStoreId: String = "",
    val storeId: String = "",
    val storePublicId: String = "",
    val storeName: List<LocalizedStringDataModel> = emptyList(),
    val actorUserId: String = "",
    val actorPublicId: String = "",
    val actorDisplayName: String = "",
    val workshiftId: String? = null,
    val action: String = "",
    val entityType: String = "",
    val entityId: String? = null,
    val title: List<LocalizedStringDataModel> = emptyList(),
    val details: List<LocalizedStringDataModel> = emptyList(),
    val metadata: Map<String, String> = emptyMap(),
    val createdAtMillis: Long = 0L
)

@kotlinx.serialization.Serializable
data class WorkerDataModel(
    val id: String,
    val userId: String,
    val workerTypeId: String,
    val placeId: String,
    val privilegeModes: List<WorkerPrivilegeModeDataModel>,
    val phoneNumber: String,
    val emails: String,
    val firstName: String,
    val lastName: String,
    val salary: String,
    val salaryCurrencyCode: String,
    val addedAt: Long,
    val isActive: Boolean
)

@kotlinx.serialization.Serializable
data class WorkerPrivilegeModeDataModel(
    val id: String,
    val parameters: List<ParameterDataModel>
)

expect var getPersistentUiDraftValue: (suspend (String) -> String?)?
expect var setPersistentUiDraftValue: (suspend (String, String?) -> Unit)?
