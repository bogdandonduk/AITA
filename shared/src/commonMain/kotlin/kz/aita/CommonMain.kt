// THIS IS CommonMain.kt - in shared commonMain module of kmp compose app
@file:OptIn(DelicateCoroutinesApi::class)
package kz.aita

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import app.cash.sqldelight.db.SqlDriver
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.*
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
import kotlinx.serialization.json.*
import kz.aita.auth.disableSessionAuthForPublicAuthRequest
import kz.aita.auth.pinSessionAuthorization
import kz.aita.auth.allowsStoredSessionAuthorization
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
val SUPPORTED_APP_LANGUAGES: List<String> = listOf("en", "ru", "kk", "tg", "ky", "uz")
const val DEFAULT_APP_THEME_ID = 0L
const val DEFAULT_APP_SIZE_MODE_ID = 0L

fun normalizeAppLanguagePreference(language: String?): String {
    val value = canonicalLanguageCode(language)
    return if (value == "system" || value in SUPPORTED_APP_LANGUAGES) value else DEFAULT_APP_LANGUAGE
}

fun normalizeAppThemePreference(themeId: Long?): Long {
    return themeId?.takeIf { it in SUPPORTED_APP_THEME_IDS } ?: DEFAULT_APP_THEME_ID
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
            LocalizedStringDataModel("kk", "Kaspi шоты"),
            LocalizedStringDataModel("ky", "Kaspi төлөм эсеби")
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
            LocalizedStringDataModel("kk", "Интеграция дайындығы. Мерчант деректері қосылғанша нақты API шақыруы өшірулі."),
            LocalizedStringDataModel("ky", "Даярдалган интеграциянын орду. Соодагердин кирүү маалыматы туташтырылганга чейин чыныгы API чакыруусу өчүрүлгөн.")
        )
    ),
    PaymentProviderConfigDataModel(
        id = PAYMENT_PROVIDER_MANUAL_DEVELOPMENT,
        name = listOf(
            LocalizedStringDataModel("main", "Manual development top-up"),
            LocalizedStringDataModel("en", "Manual development top-up"),
            LocalizedStringDataModel("ru", "Тестовое ручное пополнение"),
            LocalizedStringDataModel("kk", "Қолмен тест толтыру"),
            LocalizedStringDataModel("ky", "Иштеп чыгуу үчүн кол менен сыноо толуктоосу")
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
    val isActive: Boolean = true,
    val regionCode: String = "KZ",
    val priceVersion: Long = 1L,
    val hidden: Boolean = false
) {
    val price: Double get() = priceMinor / 100.0
}

fun defaultStoreSubscriptionPlans(): List<StoreSubscriptionPlanDataModel> = listOf(basicStoreSubscriptionPlan())

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
    val updatedAtMillis: Long = 0L,
    val accessKind: String = SUBSCRIPTION_ACCESS_PAID,
    val regionCode: String = "KZ",
    val renewalPriceMinor: Long = 0L,
    val currencyCode: String = "KZT",
    val revision: Long = 0L
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
    val activateNow: Boolean = true,
    val promoCode: String = "",
    val commandId: String = "",
    val expectedRevision: Long? = null,
    val expectedChargeMinor: Long? = null,
    val expectedCurrencyCode: String? = null,
    val expectedPriceVersion: Long? = null,
    val quoteValidUntilMillis: Long? = null,
    val expectedRegularPriceMinor: Long? = null,
    val expectedAccessKind: String? = null,
    val expectedDurationMillis: Long? = null
)

@kotlinx.serialization.Serializable
data class SubscriptionDashboardDataModel(
    val subscription: StoreSubscriptionStateDataModel,
    val charges: List<StoreSubscriptionChargeDataModel>,
    val plans: List<StoreSubscriptionPlanDataModel>,
    val canManage: Boolean = false,
    val billingWallet: UserWalletDataModel? = null,
    val serverTimeMillis: Long = 0L
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

const val SUPPLIER_CONTRACT_SIDE_STORE = "store"
const val SUPPLIER_CONTRACT_SIDE_SUPPLIER = "supplier"
const val SUPPLIER_CONTRACT_SCOPE_PARTNERSHIP = "partnership"
const val SUPPLIER_CONTRACT_SCOPE_GOODS_ITEM = "goods_item"
const val SUPPLIER_CONTRACT_SCOPE_GOODS_GROUP = "goods_group"
const val SUPPLIER_CONTRACT_STATUS_PENDING_STORE = "pending_store"
const val SUPPLIER_CONTRACT_STATUS_PENDING_SUPPLIER = "pending_supplier"
const val SUPPLIER_CONTRACT_STATUS_ACTIVE = "active"
const val SUPPLIER_CONTRACT_STATUS_DECLINED = "declined"
const val SUPPLIER_CONTRACT_STATUS_ARCHIVED = "archived"

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

// Read operations deliberately enter their mutex instead of checking isLocked before launch.
// Mutex.isLocked is only a snapshot: a second caller can race past it, while a caller that sees
// true is silently discarded and never receives its completion callback. Queuing through withLock
// keeps explicit refresh/connect actions deterministic and lets the newest queued read eventually
// publish instead of making the button appear dead.
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
    val owner = inventoryOwners.current
    if (owner.storeId != storeId || !inventoryOwnerIsCurrent(owner)) { onCompleted?.invoke(DataState.Empty()); return }
    GlobalScope.launch(Dispatchers.ourIo) {
        getDebtorsMutex.withLock {
            if (!inventoryOwnerIsCurrent(owner)) { onCompleted?.invoke(DataState.Empty()); return@withLock }
            val response = networkRequest<List<DebtorDataModel>, Unit>(
                method = HttpMethod.Get,
                endpointUrl = globalAppConfigurationState.payloadValue.getDebtorsPath.first,
                headers = mapOf("store_id" to storeId),
                expectedSessionGeneration = owner.sessionGeneration
            )

            inventoryStateMutex.withLock publication@ {
                if (!inventoryOwnerIsCurrent(owner)) { onCompleted?.invoke(DataState.Empty()); return@publication }
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
}

fun addDebtor(
    storeId: String,
    debtor: DebtorDataModel,
    onCompleted: ((DataState<DebtorDataModel>) -> Unit)? = null
) {
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
data class CartReturnBatchSelectionDataModel(
    val goodsItemId: String,
    val stockBatchId: String? = null,
    val pricePerUnit: Double? = null,
    val currencyCode: String = "",
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
    val returnReason: String = "",
    val stockBatchId: String? = null
) {
    val total: Double
        get() = quantity.total * pricePerUnit
}


data class ReceiptPlatformActionResult(
    val success: Boolean,
    val message: String = "",
    val savedFile: SavedPdfFile? = null
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
// A browser prints the current page; it does not consume a native PDF or ESC/POS document.
var receiptPrintUsesCurrentPage: Boolean = false
var printReceiptPlatformAction: (suspend (fileName: String, pdfBytes: ByteArray, printerBytes: ByteArray) -> ReceiptPlatformActionResult)? = null
var printPdfDocumentPlatformAction: (suspend (fileName: String, pdfBytes: ByteArray) -> ReceiptPlatformActionResult)? = null
var printHtmlDocumentPlatformAction: (suspend (fileName: String, html: String) -> ReceiptPlatformActionResult)? = null
/** Invoked only by a user-requested refresh/connection, never by startup polling. */
var preparePlatformReceiptPrinterAction: (suspend () -> ReceiptPlatformActionResult)? = null
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

private val receiptPrinterSelectionMutex = Mutex()

private suspend fun reloadReceiptPrintersInside() {
    val list = listPlatformReceiptPrinterDevicesAction ?: error("Printer discovery is unavailable on this platform")
    val devices = list()
    val selected = devices.firstOrNull { it.configured }?.id ?: configuredReceiptPrinterDeviceIdState.value
    // Disappearance from discovery is not permission to forget the user's durable selection.
    receiptPrinterDevicesState.emit(devices)
    configuredReceiptPrinterDeviceIdState.emit(selected)
}

fun refreshReceiptPrinterDevices(requestPermission: Boolean = false, onCompleted: ((ReceiptPlatformActionResult) -> Unit)? = null) {
    GlobalScope.launch(Dispatchers.ourIo) {
        val result = try {
            receiptPrinterSelectionMutex.withLock {
                val permission = if (requestPermission) preparePlatformReceiptPrinterAction?.invoke() else null
                if (permission != null && !permission.success) permission
                else { reloadReceiptPrintersInside(); ReceiptPlatformActionResult(true, "Receipt printers refreshed") }
            }
        } catch (cancel: CancellationException) { throw cancel }
        catch (exception: Exception) { ReceiptPlatformActionResult(false, exception.message ?: "Could not refresh receipt printers") }
        onCompleted?.invoke(result)
    }
}

fun configureReceiptPrinterDevice(deviceId: String?, onCompleted: ((ReceiptPlatformActionResult) -> Unit)? = null) {
    GlobalScope.launch(Dispatchers.ourIo) {
        val result = try {
            receiptPrinterSelectionMutex.withLock {
                val clean = deviceId?.trim()?.takeIf { it.isNotBlank() }
                val configured = configurePlatformReceiptPrinterDeviceAction?.invoke(clean)
                    ?: ReceiptPlatformActionResult(false, "Receipt printer configuration is not available on this platform")
                if (configured.success) {
                    configuredReceiptPrinterDeviceIdState.emit(clean)
                    receiptPrinterDevicesState.emit(receiptPrinterDevicesState.value.map { it.copy(configured = it.id == clean) })
                    try { reloadReceiptPrintersInside() }
                    catch (cancel: CancellationException) { throw cancel }
                    catch (_: Exception) { /* Discovery failure must not undo a successfully persisted selection. */ }
                }
                configured
            }
        } catch (cancel: CancellationException) { throw cancel }
        catch (exception: Exception) { ReceiptPlatformActionResult(false, exception.message ?: "Could not configure receipt printer") }
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
        val cleanDeviceId = deviceId?.trim()?.takeIf { it.isNotBlank() }
        val result = runCatching {
            configurePlatformLabelPrinterDeviceAction?.invoke(cleanDeviceId)
                ?: ReceiptPlatformActionResult(false, "Sticky label printer configuration is not available on this platform")
        }.getOrElse { throwable ->
            ReceiptPlatformActionResult(false, throwable.message ?: "Could not configure sticky label printer")
        }

        if (result.success) {
            configuredLabelPrinterDeviceIdState.emit(cleanDeviceId)
            runCatching {
                val devices = listPlatformLabelPrinterDevicesAction?.invoke().orEmpty()
                labelPrinterDevicesState.emit(devices)
                configuredLabelPrinterDeviceIdState.emit(devices.firstOrNull { it.configured }?.id ?: cleanDeviceId)
            }
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



private const val RECEIPT_ESC_POS_CODE_PAGE_CP866 = 17
private const val RECEIPT_ESC_POS_58MM_COLUMNS = 32
private const val RECEIPT_ESC_POS_SEPARATOR = "--------------------------------"

private fun MutableList<Byte>.addEscPosCommand(vararg values: Int) {
    values.forEach { value -> add(value.toByte()) }
}

private fun cp866ByteForCyrillic(ch: Char): Int? = when (ch) {
    'А' -> 0x80
    'Б' -> 0x81
    'В' -> 0x82
    'Г' -> 0x83
    'Д' -> 0x84
    'Е' -> 0x85
    'Ж' -> 0x86
    'З' -> 0x87
    'И' -> 0x88
    'Й' -> 0x89
    'К' -> 0x8A
    'Л' -> 0x8B
    'М' -> 0x8C
    'Н' -> 0x8D
    'О' -> 0x8E
    'П' -> 0x8F
    'Р' -> 0x90
    'С' -> 0x91
    'Т' -> 0x92
    'У' -> 0x93
    'Ф' -> 0x94
    'Х' -> 0x95
    'Ц' -> 0x96
    'Ч' -> 0x97
    'Ш' -> 0x98
    'Щ' -> 0x99
    'Ъ' -> 0x9A
    'Ы' -> 0x9B
    'Ь' -> 0x9C
    'Э' -> 0x9D
    'Ю' -> 0x9E
    'Я' -> 0x9F
    'а' -> 0xA0
    'б' -> 0xA1
    'в' -> 0xA2
    'г' -> 0xA3
    'д' -> 0xA4
    'е' -> 0xA5
    'ж' -> 0xA6
    'з' -> 0xA7
    'и' -> 0xA8
    'й' -> 0xA9
    'к' -> 0xAA
    'л' -> 0xAB
    'м' -> 0xAC
    'н' -> 0xAD
    'о' -> 0xAE
    'п' -> 0xAF
    'р' -> 0xE0
    'с' -> 0xE1
    'т' -> 0xE2
    'у' -> 0xE3
    'ф' -> 0xE4
    'х' -> 0xE5
    'ц' -> 0xE6
    'ч' -> 0xE7
    'ш' -> 0xE8
    'щ' -> 0xE9
    'ъ' -> 0xEA
    'ы' -> 0xEB
    'ь' -> 0xEC
    'э' -> 0xED
    'ю' -> 0xEE
    'я' -> 0xEF
    'Ё' -> 0xF0
    'ё' -> 0xF1
    '№' -> 0xFC
    else -> null
}

private fun escPosFallbackAscii(ch: Char): String = when (ch) {
    '\u00A0' -> " "
    '—', '–', '−' -> "-"
    '“', '”', '«', '»' -> "\""
    '‘', '’' -> "'"
    '₸' -> "KZT"
    '₽' -> "RUB"
    '€' -> "EUR"
    '$' -> "$"
    'Ә', 'ә' -> "a"
    'Ғ', 'ғ' -> "g"
    'Қ', 'қ' -> "k"
    'Ң', 'ң' -> "n"
    'Ө', 'ө' -> "o"
    'Ұ', 'ұ' -> "u"
    'Ү', 'ү' -> "u"
    'Һ', 'һ' -> "h"
    'І', 'і' -> "i"
    else -> "?"
}

private fun String.toEscPosCp866Bytes(): List<Byte> {
    val result = mutableListOf<Byte>()
    for (ch in this) {
        when {
            ch == '\r' -> Unit
            ch == '\n' -> result += 0x0A.toByte()
            ch == '\t' -> result += ' '.code.toByte()
            ch.code in 32..126 -> result += ch.code.toByte()
            else -> {
                val cp866 = cp866ByteForCyrillic(ch)
                if (cp866 != null) {
                    result += cp866.toByte()
                } else {
                    escPosFallbackAscii(ch).forEach { fallback ->
                        result += if (fallback.code in 32..126) fallback.code.toByte() else '?'.code.toByte()
                    }
                }
            }
        }
    }
    return result
}

private fun MutableList<Byte>.addEscPosText(value: String) {
    addAll(value.toEscPosCp866Bytes())
}

private fun wrapReceiptLineFor58mm(line: String, width: Int = RECEIPT_ESC_POS_58MM_COLUMNS): List<String> {
    val clean = line.replace('\t', ' ').trimEnd()
    if (clean.length <= width) return listOf(clean)

    val indent = clean.takeWhile { it == ' ' }.take(width / 3)
    val words = clean.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (words.isEmpty()) return listOf("")

    val result = mutableListOf<String>()
    var current = indent

    fun flushCurrent() {
        if (current.isNotBlank()) result += current.trimEnd()
        current = indent
    }

    fun appendChunkedWord(word: String) {
        var remaining = word
        while (remaining.isNotEmpty()) {
            val available = (width - current.length).coerceAtLeast(1)
            val chunk = remaining.take(available)
            current += chunk
            remaining = remaining.drop(chunk.length)
            if (remaining.isNotEmpty()) flushCurrent()
        }
    }

    for (word in words) {
        val prefix = if (current.isBlank() || current == indent) "" else " "
        if (word.length > width - indent.length) {
            if (current != indent) flushCurrent()
            appendChunkedWord(word)
            continue
        }
        if (current.length + prefix.length + word.length <= width) {
            current += prefix + word
        } else {
            flushCurrent()
            current += word
        }
    }
    flushCurrent()
    return result.ifEmpty { listOf(clean.take(width)) }
}

private fun MutableList<Byte>.addEscPosWrappedLine(line: String, width: Int = RECEIPT_ESC_POS_58MM_COLUMNS) {
    if (line.trim() == RECEIPT_ESC_POS_SEPARATOR) {
        addEscPosText(RECEIPT_ESC_POS_SEPARATOR + "\n")
        return
    }
    wrapReceiptLineFor58mm(line, width).forEach { wrapped ->
        addEscPosText(wrapped + "\n")
    }
}

private fun MutableList<Byte>.startReceiptEscPosDocument() {
    addEscPosCommand(0x1B, 0x40) // initialize printer
    addEscPosCommand(0x1C, 0x2E) // leave multibyte mode before legacy single-byte text
    addEscPosCommand(0x1B, 0x74, RECEIPT_ESC_POS_CODE_PAGE_CP866) // CP866 Cyrillic table used by many XP-58/AOKIA-class ESC/POS printers
}

private fun MutableList<Byte>.finishReceiptEscPosDocument() {
    addEscPosText("\n\n")
    addEscPosCommand(0x1D, 0x56, 0x42, 0x00) // partial cut when supported; ignored by many tear-bar 58mm devices
}

fun buildReceiptPrinterTestEscPosBytes(title: String = "AITA printer test", dateText: String = ""): ByteArray {
    renderReceiptRaster(listOf(
        title.ifBlank { "AITA printer test" }, dateText,
        "--------------------------------",
        "AITA / ESC-POS / 58 mm",
        "Кириллица: чек готов, Ёё № 123",
        "Қазақша: Әә Ғғ Ққ Ңң Өө Ұұ Үү Һһ Іі",
        "Итого / Total: 1 234.50 ₸",
        "Unicode raster / 384 dots"
    ))?.let { return it }
    val bytes = mutableListOf<Byte>()
    bytes.startReceiptEscPosDocument()
    bytes.addEscPosCommand(0x1B, 0x61, 0x01)
    bytes.addEscPosCommand(0x1B, 0x45, 0x01)
    bytes.addEscPosWrappedLine(title.ifBlank { "AITA printer test" })
    bytes.addEscPosCommand(0x1B, 0x45, 0x00)
    if (dateText.isNotBlank()) bytes.addEscPosWrappedLine(dateText)
    bytes.addEscPosCommand(0x1B, 0x61, 0x00)
    bytes.addEscPosWrappedLine(RECEIPT_ESC_POS_SEPARATOR)
    bytes.addEscPosWrappedLine("AOKIA / XP-58 USB ESC/POS path")
    bytes.addEscPosWrappedLine("58 mm receipt paper, 32-column layout")
    bytes.addEscPosWrappedLine("Кириллица: чек готов")
    bytes.addEscPosWrappedLine("If this printed, AITA can send receipts directly.")
    bytes.finishReceiptEscPosDocument()
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

@Suppress("UNUSED_PARAMETER") // retain the public signature; drafts no longer have a display number
fun TransactionReceiptSnapshotDataModel.receiptNumberText(labels: ReceiptTextLabelsDataModel = ReceiptTextLabelsDataModel()): String {
    return transaction.serverReceiptIdOrNull()?.take(8)?.uppercase().orEmpty()
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

fun TransactionReceiptSnapshotDataModel.buildReceiptPdfDocument(language: String, labels: ReceiptTextLabelsDataModel): AitaPdfDocument {
    val blocks = mutableListOf<AitaPdfBlock>()
    fun appendLine(text: String, role: AitaPdfRole = AitaPdfRole.Body) { blocks += AitaPdfBlock(text, role) }
    val storeName = store?.let {
        val form = it.companyForms.firstOrNull()?.name?.let { name -> receiptVisibleString(name, language, "") }.orEmpty()
        val name = receiptVisibleString(it.name, language, labels.store)
        "$form $name".trim()
    } ?: labels.store

    appendLine(storeName, AitaPdfRole.Store)
    store?.location?.name?.takeIf { it.isNotBlank() }?.let { appendLine(it, AitaPdfRole.Address) }
    store?.phoneNumbers?.asDisplayPhoneNumbers()?.takeIf { it.isNotEmpty() }?.let { appendLine("${labels.phone}: ${it.joinToString()}") }
    store?.emails?.takeIf { it.isNotEmpty() }?.let { appendLine("${labels.email}: ${it.joinToString()}") }
    appendLine("--------------------------------", AitaPdfRole.Divider)
    appendLine(labels.goodsReceiptTitle, AitaPdfRole.Title)
    appendLine(receiptTitle(labels), AitaPdfRole.Heading)
    transaction.serverReceiptIdOrNull()?.let { serverId ->
        appendLine("${labels.receipt}: ${receiptNumberText(labels)}")
        appendLine("${labels.transactionId}: $serverId")
    }
    appendLine("${labels.date}: ${receiptDateTimeText(transaction.timeMillis)}")
    cashierName.takeIf { it.isNotBlank() }?.let { appendLine("${labels.cashier}: $it") }
    appendLine("--------------------------------", AitaPdfRole.Divider)

    if (lines.isEmpty()) {
        appendLine(labels.noItems)
    } else {
        lines.forEach { line ->
            val name = receiptVisibleString(line.name, language, labels.noName)
            appendLine("${line.index + 1}. $name")
            if (line.barcode.isNotBlank()) appendLine("   ${labels.barcode}: ${line.barcode}")
            if (line.saleMethodId == SALE_METHOD_WHOLESALE) {
                val saleMethodText = receiptVisibleString(line.saleMethodName, language, "")
                if (saleMethodText.isNotBlank()) appendLine("   $saleMethodText")
            }
            line.returnReason.takeIf { it.isNotBlank() }?.let { reason ->
                appendLine("   ${labels.returnReason}: $reason")
            }
            appendLine("   ${receiptQuantityText(line.quantity, language)} x ${receiptMoney(line.pricePerUnit)} ${line.currencySymbol} = ${receiptMoney(line.total)} ${line.currencySymbol}")
        }
    }

    appendLine("--------------------------------", AitaPdfRole.Divider)
    appendLine("${labels.total}: ${receiptMoney(totalAmount())} $currencySymbol", AitaPdfRole.Total)
    if (paymentDraft.paidCash > 0.0) appendLine("${labels.cash}: ${receiptMoney(paymentDraft.paidCash)} $currencySymbol")
    if (paymentDraft.paidCard > 0.0) appendLine("${labels.cashless}: ${receiptMoney(paymentDraft.paidCard)} $currencySymbol")
    if (debtAmount() > 0.0) {
        appendLine("${labels.debt}: ${receiptMoney(debtAmount())} $currencySymbol")
        paymentDraft.debtor?.let { debtor ->
            appendLine("${labels.debtor}: ${debtor.displayName}")
            appendLine("Type: ${debtor.debtorType}")
            debtor.idNumber.takeIf { it.isNotBlank() }?.let { appendLine("ID number: $it") }
            debtor.companyIdNumber.takeIf { it.isNotBlank() }?.let { appendLine("Company ID: $it") }
            debtor.phoneNumber.asDisplayPhoneNumber().takeIf { it.isNotBlank() }?.let { appendLine("${labels.debtorPhone}: $it") }
            debtor.debtDueAtMillis?.let { appendLine("Debt due at: ${receiptDateTimeText(it)}") }
            debtor.interest?.takeIf { it.enabled && it.ratePercent > 0.0 }?.let {
                appendLine("Interest: ${it.ratePercent}% per ${it.periodUnit}")
            }
            debtor.plannedPayments.takeIf { it.isNotEmpty() }?.let { plans ->
                appendLine("Payment plan:")
                plans.forEach { plan ->
                    appendLine("- ${receiptMoney(plan.amount)} ${debtor.currency} by ${plan.dueAtMillis?.let { due -> receiptDateTimeText(due) } ?: "no date"}${plan.percent?.let { pct -> " ($pct%)" } ?: ""}")
                }
            }
        }
    }
    if (changeAmount() > 0.0) appendLine("${labels.change}: ${receiptMoney(changeAmount())} $currencySymbol")
    appendLine("--------------------------------", AitaPdfRole.Divider)
    appendLine("${labels.vat}: ${labels.vatNotSpecified}")
    appendLine("${labels.fiscalStatus}: ${labels.nonFiscalSoftwareReceipt}")
    appendLine(labels.thankYou)

    return AitaPdfDocument(blocks)
}


fun TransactionReceiptSnapshotDataModel.buildReceiptPlainText(language: String, labels: ReceiptTextLabelsDataModel): String =
    buildReceiptPdfDocument(language, labels).blocks.joinToString(separator = "\n", postfix = "\n") { it.text }


private fun pdfEscape(value: String): String {
    return value
        .replace("\\", "\\\\")
        .replace("(", "\\(")
        .replace(")", "\\)")
        .map { ch -> if (ch.code in 32..126) ch else '?' }
        .joinToString("")
}

fun TransactionReceiptSnapshotDataModel.buildReceiptPdfBytes(language: String, labels: ReceiptTextLabelsDataModel): ByteArray =
    renderAitaPdfDocument(buildReceiptPdfDocument(language, labels))

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

fun AnalyticsReportSnapshotDataModel.buildAnalyticsReportPdfBytes(): ByteArray = renderAitaPdfDocument(
    AitaPdfDocument(
        blocks = buildAnalyticsReportPlainText().lines().mapIndexed { index, text ->
            AitaPdfBlock(text, when {
                index == 0 -> AitaPdfRole.Store
                index == 1 -> AitaPdfRole.Title
                text == "--------------------------------" -> AitaPdfRole.Divider
                else -> AitaPdfRole.Body
            })
        },
        width = 595f, maxHeight = 842f, minHeight = 842f, margin = 42f, bodySize = 10f
    )
)

fun AnalyticsReportSnapshotDataModel.analyticsReportPdfFileName(): String {
    val safeStore = storeName
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "_")
        .trim('_')
        .ifBlank { "store" }
        .take(32)
    return "analytics_report_${safeStore}_${generatedAtMillis}.pdf"
}


fun TransactionReceiptSnapshotDataModel.buildReceiptEscPosBytes(language: String, labels: ReceiptTextLabelsDataModel): ByteArray {
    val plainLines = buildReceiptPlainText(language, labels)
        .lines()
        .dropLastWhile { it.isBlank() }
    renderReceiptRaster(plainLines)?.let { return it }
    val bytes = mutableListOf<Byte>()
    bytes.startReceiptEscPosDocument()

    val header = plainLines.firstOrNull()?.trim().orEmpty()
    if (header.isNotBlank()) {
        bytes.addEscPosCommand(0x1B, 0x61, 0x01)
        bytes.addEscPosCommand(0x1B, 0x45, 0x01)
        bytes.addEscPosWrappedLine(header)
        bytes.addEscPosCommand(0x1B, 0x45, 0x00)
        bytes.addEscPosCommand(0x1B, 0x61, 0x00)
    }

    plainLines.drop(if (header.isBlank()) 0 else 1).forEach { line ->
        bytes.addEscPosWrappedLine(line)
    }
    bytes.finishReceiptEscPosDocument()
    return bytes.toByteArray()
}

fun TransactionReceiptSnapshotDataModel.receiptPdfFileName(labels: ReceiptTextLabelsDataModel = ReceiptTextLabelsDataModel()): String {
    val safeId = receiptNumberText(labels).takeIf { it.isNotBlank() }
        ?: "pending_${transaction.timeMillis}"
    return "receipt_${safeId.replace(Regex("[^A-Za-z0-9_-]"), "_")}.pdf"
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
            LocalizedStringDataModel("kk", "Көтерме"),
            LocalizedStringDataModel("ky", "Дүң")
        )
    } else {
        listOf(
            LocalizedStringDataModel("main", "Retail"),
            LocalizedStringDataModel("en", "Retail"),
            LocalizedStringDataModel("ru", "Розница"),
            LocalizedStringDataModel("kk", "Бөлшек"),
            LocalizedStringDataModel("ky", "Чекене")
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

private val cartReturnBatchSelectionsState = MutableStateFlow<Map<String, CartReturnBatchSelectionDataModel>>(emptyMap())

fun getCartReturnBatchSelectionsState(): StateFlow<Map<String, CartReturnBatchSelectionDataModel>> =
    cartReturnBatchSelectionsState.asStateFlow()

fun cartReturnBatchSelectionKey(transactionTypeIndex: Int, clientId: Int, goodsItemId: String): String =
    "${transactionKey(transactionTypeIndex, clientId)}:$goodsItemId"

fun currentCartReturnBatchSelection(transactionTypeIndex: Int, clientId: Int, goodsItemId: String): CartReturnBatchSelectionDataModel? =
    cartReturnBatchSelectionsState.value[cartReturnBatchSelectionKey(transactionTypeIndex, clientId, goodsItemId)]

fun setCartReturnBatchSelection(
    transactionTypeIndex: Int,
    clientId: Int,
    goodsItemId: String,
    selection: CartReturnBatchSelectionDataModel?
) {
    GlobalScope.launch(Dispatchers.ourIo) {
        val cleanGoodsItemId = goodsItemId.trim().takeIf { it.isNotBlank() } ?: return@launch
        val key = cartReturnBatchSelectionKey(transactionTypeIndex, clientId, cleanGoodsItemId)
        val normalizedSelection = selection
            ?.takeIf { transactionTypeIndex == 1 }
            ?.let { selected ->
                selected.copy(
                    goodsItemId = cleanGoodsItemId,
                    stockBatchId = selected.stockBatchId?.trim()?.takeIf { it.isNotBlank() },
                    pricePerUnit = selected.pricePerUnit?.coerceAtLeast(0.0)?.roundMoney(),
                    currencyCode = selected.currencyCode.trim().uppercase(),
                    updatedAtMillis = getCurrentTimeMillis()
                )
            }

        val updated = cartReturnBatchSelectionsState.value.toMutableMap().apply {
            if (normalizedSelection == null) remove(key) else put(key, normalizedSelection)
        }

        if (updated != cartReturnBatchSelectionsState.value) {
            cartReturnBatchSelectionsState.emit(updated)
            persistTransactionCartUiState()
        }
    }
}

private fun removeCartReturnBatchSelection(transactionTypeIndex: Int, clientId: Int, goodsItemId: String) {
    GlobalScope.launch(Dispatchers.ourIo) {
        val updated = cartReturnBatchSelectionsState.value.toMutableMap().apply {
            remove(cartReturnBatchSelectionKey(transactionTypeIndex, clientId, goodsItemId))
        }
        if (updated != cartReturnBatchSelectionsState.value) {
            cartReturnBatchSelectionsState.emit(updated)
            persistTransactionCartUiState()
        }
    }
}

private fun removeCartReturnBatchSelections(transactionTypeIndex: Int, clientId: Int) {
    GlobalScope.launch(Dispatchers.ourIo) {
        val prefix = "${transactionKey(transactionTypeIndex, clientId)}:"
        val updated = cartReturnBatchSelectionsState.value.filterKeys { !it.startsWith(prefix) }
        if (updated != cartReturnBatchSelectionsState.value) {
            cartReturnBatchSelectionsState.emit(updated)
            persistTransactionCartUiState()
        }
    }
}

private fun removeCartReturnBatchSelectionsByGoodsItemId(goodsItemId: String) {
    GlobalScope.launch(Dispatchers.ourIo) {
        val suffix = ":$goodsItemId"
        val updated = cartReturnBatchSelectionsState.value.filterKeys { key -> !key.endsWith(suffix) }
        if (updated != cartReturnBatchSelectionsState.value) {
            cartReturnBatchSelectionsState.emit(updated)
            persistTransactionCartUiState()
        }
    }
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

    if (!normalizedDeltaQuantity.total.isFinite() || normalizedDeltaQuantity.total <= 0.0)
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
    val owner = inventoryOwners.current
    if (owner.storeId != storeId || !inventoryOwnerIsCurrent(owner)) return
    GlobalScope.launch(Dispatchers.ourIo) {
        getTransactionsMutex.withLock {
            if (!inventoryOwnerIsCurrent(owner)) return@withLock
            val response = networkRequest<List<TransactionDataModel>, Unit>(
                HttpMethod.Get,
                endpointUrl = globalAppConfigurationState.payloadValue.getTransactionsPath.first,
                headers = mapOf("store_id" to storeId),
                expectedSessionGeneration = owner.sessionGeneration
            )
            inventoryStateMutex.withLock {
                if (inventoryOwnerIsCurrent(owner)) {
                    if (response.negative) postInAppNotification(response.message, NotificationType.Negative)
                    else {
                        val transactions = response.payload.orEmpty()
                        transactionsState.emit(DataState.Success(transactions, response.message))
                        transactions.forEach(::reconcileLatestReceiptIdentity)
                    }
                }
            }
        }
    }
}

fun getUserFinanceDashboard(
    onCompleted: ((DataState<UserFinanceDashboardDataModel>) -> Unit)? = null
) {
    val generation = currentAuthenticatedSessionGeneration()
    GlobalScope.launch(Dispatchers.ourIo) {
        getUserFinanceDashboardMutex.withLock {
            if (!authenticatedSessionGenerationIsCurrent(generation)) return@withLock
            val response = networkRequest<UserFinanceDashboardDataModel, Unit>(
                method = HttpMethod.Get,
                endpointUrl = globalAppConfigurationState.payloadValue.getUserFinanceDashboardPath.first,
                expectedSessionGeneration = generation
            )
            if (!authenticatedSessionGenerationIsCurrent(generation)) return@withLock

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
}

fun createTopUpPayment(
    request: TopUpCreateRequestDataModel,
    onCompleted: ((DataState<TopUpPaymentIntentDataModel>) -> Unit)? = null
) {
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

fun getSubscriptionPlans(onCompleted: ((DataState<List<StoreSubscriptionPlanDataModel>>) -> Unit)? = null) {
    val store = activeStoreIdState.value
    if (store == null) {
        subscriptionPlansState.emit(DataState.Success(emptyList()))
        onCompleted?.invoke(DataState.Success(emptyList()))
        return
    }
    getStoreSubscription(store) { result ->
        onCompleted?.invoke(when (result) {
            is DataState.Success -> DataState.Success(result.payload.plans, result.message)
            is DataState.Empty -> DataState.Empty(result.message)
        })
    }
}

fun getStoreSubscription(storeId: String, onCompleted: ((DataState<SubscriptionDashboardDataModel>) -> Unit)? = null) {
    val owner = inventoryOwners.current
    GlobalScope.launch(Dispatchers.ourIo) {
        val response = refreshStoreSubscriptionNow(storeId)
        if (inventoryOwnerIsCurrent(owner)) onCompleted?.invoke(response.toSubscriptionDataState())
    }
}

fun updateStoreSubscription(request: StoreSubscriptionUpdateRequestDataModel,
    onCompleted: ((DataState<SubscriptionDashboardDataModel>) -> Unit)? = null) {
    val owner = inventoryOwners.current
    GlobalScope.launch(Dispatchers.ourIo) {
        val response = submitStoreSubscriptionNow(request)
        if (inventoryOwnerIsCurrent(owner)) onCompleted?.invoke(response.toSubscriptionDataState())
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
    val owner = inventoryOwners.current
    if (owner.storeId != storeId || !inventoryOwnerIsCurrent(owner)) {
        onCompleted?.invoke(DataState.Empty())
        return
    }
    GlobalScope.launch(Dispatchers.ourIo) {
        getCashRegisterMutex.withLock {
            if (!inventoryOwnerIsCurrent(owner)) {
                onCompleted?.invoke(DataState.Empty())
                return@withLock
            }
            val response = networkRequest<CashRegisterStateDataModel, Unit>(
                method = HttpMethod.Get,
                endpointUrl = globalAppConfigurationState.payloadValue.getCashRegisterPath.first,
                headers = mapOf("store_id" to storeId),
                expectedSessionGeneration = owner.sessionGeneration
            )
            val completed: DataState<StoreCashRegisterDataModel> = inventoryStateMutex.withLock {
                when {
                    !inventoryOwnerIsCurrent(owner) -> DataState.Empty<StoreCashRegisterDataModel>()
                    response.negative || response.payload == null || response.payload.register.storeId != storeId -> {
                        postInAppNotification(response.message, NotificationType.Negative)
                        DataState.Empty(response.message)
                    }
                    else -> {
                        applyCashRegisterStatePayload(response.payload, response.message)
                        DataState.Success(response.payload.register, response.message)
                    }
                }
            }
            onCompleted?.invoke(completed)
        }
    }
}

fun extractCashRegister(
    request: CashRegisterExtractionRequestDataModel,
    onCompleted: ((DataState<StoreCashRegisterDataModel>) -> Unit)? = null
) {
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
    val owner = inventoryOwners.current
    if (owner.storeId != storeId || !inventoryOwnerIsCurrent(owner)) { onCompleted?.invoke(DataState.Empty()); return }
    GlobalScope.launch(Dispatchers.ourIo) {
        getStoreWorkersMutex.withLock {
            if (!inventoryOwnerIsCurrent(owner)) { onCompleted?.invoke(DataState.Empty()); return@withLock }
            val response = networkRequest<List<StoreWorkerDataModel>, Unit>(
                method = HttpMethod.Get,
                endpointUrl = globalAppConfigurationState.payloadValue.getStoreWorkersPath.first,
                headers = mapOf("store_id" to storeId),
                expectedSessionGeneration = owner.sessionGeneration
            )

            inventoryStateMutex.withLock publication@ {
                if (!inventoryOwnerIsCurrent(owner)) { onCompleted?.invoke(DataState.Empty()); return@publication }
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
}

fun getMyWorkerMemberships(
    onCompleted: ((DataState<List<StoreWorkerDataModel>>) -> Unit)? = null
) {
    val generation = currentAuthenticatedSessionGeneration()
    GlobalScope.launch(Dispatchers.ourIo) {
        getMyWorkerMembershipsMutex.withLock {
            if (!authenticatedSessionGenerationIsCurrent(generation)) return@withLock
            val response = networkRequest<List<StoreWorkerDataModel>, Unit>(
                method = HttpMethod.Get,
                endpointUrl = globalAppConfigurationState.payloadValue.getMyWorkerMembershipsPath.first,
                expectedSessionGeneration = generation
            )
            if (!authenticatedSessionGenerationIsCurrent(generation)) return@withLock

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
    val owner = inventoryOwners.current
    if (owner.storeId != storeId || !inventoryOwnerIsCurrent(owner)) { onCompleted?.invoke(DataState.Empty()); return }
    GlobalScope.launch(Dispatchers.ourIo) {
        getIncomingWorkerRequestsMutex.withLock {
            if (!inventoryOwnerIsCurrent(owner)) { onCompleted?.invoke(DataState.Empty()); return@withLock }
            val response = networkRequest<List<StoreWorkerRequestDataModel>, Unit>(
                method = HttpMethod.Get,
                endpointUrl = globalAppConfigurationState.payloadValue.getIncomingWorkerRequestsPath.first,
                headers = mapOf("store_id" to storeId),
                expectedSessionGeneration = owner.sessionGeneration
            )

            inventoryStateMutex.withLock publication@ {
                if (!inventoryOwnerIsCurrent(owner)) { onCompleted?.invoke(DataState.Empty()); return@publication }
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
}

fun getMyWorkerRequests(
    onCompleted: ((DataState<List<StoreWorkerRequestDataModel>>) -> Unit)? = null
) {
    val generation = currentAuthenticatedSessionGeneration()
    GlobalScope.launch(Dispatchers.ourIo) {
        getMyWorkerRequestsMutex.withLock {
            if (!authenticatedSessionGenerationIsCurrent(generation)) return@withLock
            val response = networkRequest<List<StoreWorkerRequestDataModel>, Unit>(
                method = HttpMethod.Get,
                endpointUrl = globalAppConfigurationState.payloadValue.getMyWorkerRequestsPath.first,
                expectedSessionGeneration = generation
            )
            if (!authenticatedSessionGenerationIsCurrent(generation)) return@withLock

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
    jobTitle: String = "",
    jobTitleLocalized: List<LocalizedStringDataModel> = emptyList(),
    salary: String = "",
    salaryCurrencyCode: String = "KZT",
    note: String? = null,
    workerPassword: String? = null,
    onCompleted: ((DataState<StoreWorkerRequestDataModel>) -> Unit)? = null
) {
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
                    jobTitle = jobTitle.trim(),
                    jobTitleLocalized = jobTitleLocalized,
                    salary = salary.trim(),
                    salaryCurrencyCode = salaryCurrencyCode.trim().uppercase(),
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
    jobTitle: String = "",
    jobTitleLocalized: List<LocalizedStringDataModel> = emptyList(),
    salary: String = "",
    salaryCurrencyCode: String = "KZT",
    note: String? = null,
    workerPassword: String? = null,
    onCompleted: ((DataState<StoreWorkerRequestDataModel>) -> Unit)? = null
) {
    GlobalScope.launch(Dispatchers.ourIo) {
        decideStoreEmploymentMutex.withLock {
            val response = networkRequest<StoreWorkerRequestDataModel, WorkerEmploymentDecisionRequestDataModel>(
                method = HttpMethod.Post,
                endpointUrl = globalAppConfigurationState.payloadValue.acceptStoreEmploymentPath.first,
                body = WorkerEmploymentDecisionRequestDataModel(
                    requestId = requestId,
                    roleId = roleId,
                    permissions = permissions,
                    jobTitle = jobTitle.trim(),
                    jobTitleLocalized = jobTitleLocalized,
                    salary = salary.trim(),
                    salaryCurrencyCode = salaryCurrencyCode.trim().uppercase(),
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
                incomingWorkerRequestsState.emit(
                    DataState.Success(
                        incomingWorkerRequestsState.payloadValue.orEmpty().filterNot { it.id == response.payload.id } + response.payload,
                        response.message
                    )
                )
                val realStoreId = response.payload.storeId.ifBlank { storeId }
                val visibleStoreId = activeStoreIdState.value?.takeIf { it.isNotBlank() } ?: realStoreId
                getIncomingWorkerRequests(visibleStoreId)
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

fun declineStoreEmploymentRequest(
    storeId: String,
    requestId: String,
    note: String? = null,
    onCompleted: ((DataState<StoreWorkerRequestDataModel>) -> Unit)? = null
) {
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
    jobTitle: String = "",
    jobTitleLocalized: List<LocalizedStringDataModel> = emptyList(),
    salary: String = "",
    salaryCurrencyCode: String = "KZT",
    workerPassword: String? = null,
    onCompleted: ((DataState<StoreWorkerDataModel>) -> Unit)? = null
) {
    GlobalScope.launch(Dispatchers.ourIo) {
        updateStoreWorkerPermissionsMutex.withLock {
            val response = networkRequest<StoreWorkerDataModel, WorkerPermissionsUpdateRequestDataModel>(
                method = HttpMethod.Post,
                endpointUrl = globalAppConfigurationState.payloadValue.updateStoreWorkerPermissionsPath.first,
                body = WorkerPermissionsUpdateRequestDataModel(
                    workerId = workerId,
                    roleId = roleId,
                    permissions = permissions,
                    jobTitle = jobTitle.trim(),
                    jobTitleLocalized = jobTitleLocalized,
                    salary = salary.trim(),
                    salaryCurrencyCode = salaryCurrencyCode.trim().uppercase(),
                    workerPassword = workerPassword
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
    loadOperationLogScope(storeId, scope, onCompleted)
    // Keep an already-visited family view fresh without fetching it for users who never open logs.
    if (!operationLogScopeIsFamily(scope) && operationLogViewsState.value.family.records != null) {
        loadOperationLogScope(storeId, OPERATION_LOG_SCOPE_ROOT)
    }
}

fun getStockItemHistory(
    storeId: String,
    goodsItemId: String,
    onCompleted: ((DataState<List<OperationLogDataModel>>) -> Unit)? = null
) {
    GlobalScope.launch(Dispatchers.ourIo) {
        getStockItemHistoryMutex.withLock {
            val cleanStoreId = storeId.trim()
            val cleanGoodsItemId = goodsItemId.trim()
            if (cleanStoreId.isBlank() || cleanGoodsItemId.isBlank()) {
                stockItemHistoryState.emit(DataState.Empty())
                onCompleted?.invoke(DataState.Empty())
                return@withLock
            }

            val response = networkRequest<List<OperationLogDataModel>, Unit>(
                method = HttpMethod.Get,
                endpointUrl = globalAppConfigurationState.payloadValue.getStockItemHistoryPath.first,
                headers = mapOf(
                    "store_id" to cleanStoreId,
                    "goods_item_id" to cleanGoodsItemId
                )
            )

            if (response.negative || response.payload == null) {
                postInAppNotification(response.message, NotificationType.Negative)
                stockItemHistoryState.emit(DataState.Empty(response.message))
                onCompleted?.invoke(DataState.Empty(response.message))
            } else {
                stockItemHistoryState.emit(DataState.Success(response.payload, response.message))
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
    val owner = inventoryOwners.current
    if (owner.storeId != id || !inventoryOwnerIsCurrent(owner)) { onCompleted?.invoke(DataState.Empty()); return }
    GlobalScope.launch(Dispatchers.ourIo) {
        getCurrentWorkshiftMutex.withLock {
            if (!inventoryOwnerIsCurrent(owner)) { onCompleted?.invoke(DataState.Empty()); return@withLock }
            val response = networkRequest<WorkshiftDataModel, Unit>(
                endpointUrl = globalAppConfigurationState.payloadValue.getCurrentWorkshiftPath.first,
                method = HttpMethod.Get,
                headers = mapOf("store_id" to id),
                expectedSessionGeneration = owner.sessionGeneration
            )

            inventoryStateMutex.withLock publication@ {
                if (!inventoryOwnerIsCurrent(owner)) { onCompleted?.invoke(DataState.Empty()); return@publication }
            if (response.negative || response.payload == null) {
                if (!response.transportFailure && response.httpStatusCode != HttpStatusCode.Unauthorized.value && response.httpStatusCode != 402) {
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
}

fun startWorkshift(
    storeId: String,
    workerIdentifier: String,
    password: String,
    onCompleted: ((DataState<WorkshiftDataModel>) -> Unit)? = null
) {
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

fun currentUserCanViewStockHistory(storeId: String?): Boolean {
    return currentUserOwnsStore(storeId) ||
        currentUserHasStorePermission(storeId, STORE_PERMISSION_STOCK_HISTORY_VIEW) ||
        currentUserHasStorePermission(storeId, STORE_PERMISSION_LOGS_VIEW)
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
    GlobalScope.launch(Dispatchers.ourIo) {
        if (!completeTransactionMutex.tryLock()) {
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
            return@launch
        }

        try {
            completeTransactionInProgressState.emit(true)
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
                headers = transactionWithOperationId.storeId.takeIf { it.isNotBlank() }
                    ?.let { mapOf("store_id" to it) }
                    ?: emptyMap(),
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
                        return@launch
                    }
                }

                postInAppNotification(response.message, NotificationType.Negative)
                return@launch
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
                    }.distinctBy { transactionItem ->
                        transactionItem.clientOperationId.ifBlank { transactionItem.id }
                    },
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
        } finally {
            withContext(NonCancellable) {
                completeTransactionInProgressState.emit(false)
                completeTransactionMutex.unlock()
            }
        }
    }
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

        val successMessage = eventMessage("message.device_settings_opened").extractLocalizedString(appLanguageState.value) ?: "Device settings opened"

        val errorMessage = eventMessage("message.could_not_open_device_settings").extractLocalizedString(appLanguageState.value) ?: "Could not open device settings"

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

fun supplierGoodsOfferRelationshipKey(
    storeId: String,
    supplierId: String,
    goodsItemId: String
): String? {
    val cleanStoreId = storeId.trim().lowercase()
    val cleanSupplierId = supplierId.trim().lowercase()
    val cleanGoodsItemId = goodsItemId.trim().lowercase()
    if (cleanStoreId.isBlank() || cleanSupplierId.isBlank() || cleanGoodsItemId.isBlank()) {
        return null
    }
    return listOf(cleanStoreId, cleanSupplierId, cleanGoodsItemId).joinToString("|")
}

fun SupplierGoodsPriceDataModel.supplierGoodsOfferRelationshipKey(): String? =
    supplierGoodsOfferRelationshipKey(storeId, supplierId, goodsItemId)

fun SupplierGoodsPriceDataModel.supplierPriceBookIdentity(): String {
    supplierGoodsOfferRelationshipKey()?.let { return it }

    val cleanStoreId = storeId.trim().lowercase()
    val cleanSupplierId = supplierId.trim().lowercase()
    val cleanGoodsItemId = goodsItemId.trim().lowercase()

    val fallbackId = id.trim().lowercase().ifBlank {
        listOf(
            cleanStoreId,
            cleanSupplierId,
            cleanGoodsItemId,
            supplyPrice.price.trim(),
            supplyPrice.currency.trim().lowercase(),
            supplyPrice.supplierId.trim().lowercase(),
            minOrderQuantity?.let { "${it.id}:${it.total}:${it.pricedAmount}:${it.roundTotal}" }.orEmpty(),
            packageQuantity?.let { "${it.id}:${it.total}:${it.pricedAmount}:${it.roundTotal}" }.orEmpty(),
            supplierBarcode.orEmpty().trim().lowercase(),
            supplierGoodsName.orEmpty().trim().lowercase(),
            lastUsedAtMillis?.toString().orEmpty(),
            createdAtMillis.toString(),
            updatedAtMillis.toString()
        ).joinToString("|")
    }
    return "id:$fallbackId"
}

private fun SupplierGoodsPriceDataModel.supplierPriceBookFreshnessMillis(): Long =
    maxOf(lastUsedAtMillis ?: 0L, updatedAtMillis, createdAtMillis)

fun List<SupplierGoodsPriceDataModel>.normalizedSupplierGoodsPriceBook(): List<SupplierGoodsPriceDataModel> =
    asSequence()
        .filter { it.isActive }
        .groupBy { it.supplierPriceBookIdentity() }
        .values
        .mapNotNull { rows ->
            rows.maxWithOrNull(
                compareBy<SupplierGoodsPriceDataModel> { it.supplierPriceBookFreshnessMillis() }
                    .thenBy { it.updatedAtMillis }
                    .thenBy { it.createdAtMillis }
                    .thenBy { it.id }
            )
        }
        .sortedWith(
            compareByDescending<SupplierGoodsPriceDataModel> {
                it.supplierPriceBookFreshnessMillis()
            }.thenBy { it.supplierPriceBookIdentity() }
        )

/**
 * Counts Store × Supplier × Product offer relationships that still need a usable positive price.
 * Required relationships without a row and legacy non-positive rows are counted once each; malformed
 * historical rows remain visible as independent repair work rather than disappearing silently.
 */
fun supplierGoodsOfferPriceGapCount(
    requiredRelationshipKeys: Set<String>,
    prices: List<SupplierGoodsPriceDataModel>
): Int {
    val normalizedPrices = prices.normalizedSupplierGoodsPriceBook()
    val requiredKeys = requiredRelationshipKeys
        .map { it.trim().lowercase() }
        .filter { it.isNotBlank() }
        .toSet()
    val usableKeys = normalizedPrices
        .asSequence()
        .filter { it.supplyPrice.hasPositiveSupplierDeskPrice() }
        .mapNotNull { it.supplierGoodsOfferRelationshipKey() }
        .toSet()
    val invalidKeys = normalizedPrices
        .asSequence()
        .filterNot { it.supplyPrice.hasPositiveSupplierDeskPrice() }
        .mapNotNull { it.supplierGoodsOfferRelationshipKey() }
        .toSet()
    val malformedInvalidCount = normalizedPrices.count {
        !it.supplyPrice.hasPositiveSupplierDeskPrice() &&
            it.supplierGoodsOfferRelationshipKey() == null
    }
    return ((requiredKeys - usableKeys) + (invalidKeys - usableKeys)).size +
        malformedInvalidCount
}

fun List<SupplierGoodsPriceDataModel>.upsertSupplierGoodsPriceByIdentity(
    price: SupplierGoodsPriceDataModel
): List<SupplierGoodsPriceDataModel> {
    val incomingIdentity = price.supplierPriceBookIdentity()
    return (
        listOf(price) + filterNot { existing ->
            (existing.id.isNotBlank() && existing.id.trim().equals(price.id.trim(), ignoreCase = true)) ||
                existing.supplierPriceBookIdentity() == incomingIdentity
        }
        ).normalizedSupplierGoodsPriceBook()
}

@Volatile
private var supplierNetworkSessionEpoch: Long = 0L

private fun currentSupplierNetworkUserScope(): String {
    val userId = userAccountState.payloadValue?.id.orEmpty().trim().lowercase()
    return if (userId.isBlank()) "" else "$supplierNetworkSessionEpoch|$userId"
}

private fun supplierNetworkUserScopeIsCurrent(expectedScope: String): Boolean =
    expectedScope.isNotBlank() && currentSupplierNetworkUserScope() == expectedScope

private fun invalidateSupplierNetworkSessionScope() {
    // The exact value is not business data; it is only a generation marker. Any change invalidates
    // reads started before logout, even if the same account signs in again immediately.
    supplierNetworkSessionEpoch = if (supplierNetworkSessionEpoch == Long.MAX_VALUE) {
        0L
    } else {
        supplierNetworkSessionEpoch + 1L
    }
}

val supplierGoodsPricesState =
    MutableDataStateFlow<List<SupplierGoodsPriceDataModel>>(GlobalScope)

val supplierOrdersState =
    MutableDataStateFlow<List<SupplierOrderDataModel>>(GlobalScope)

val supplierOrderLinesState =
    MutableDataStateFlow<List<SupplierOrderLineDataModel>>(GlobalScope)

val supplierPartnershipContractsState =
    MutableDataStateFlow<List<SupplierPartnershipContractDataModel>>(GlobalScope)

val supplierModeDashboardState =
    MutableDataStateFlow<SupplierModeDashboardDataModel>(GlobalScope)

private val supplierGoodsPriceReadCoordinator =
    SingleFlightRequestCoordinator<String, DataState<List<SupplierGoodsPriceDataModel>>>()
// Reads and writes share one operation mutex so a slower read response cannot overwrite a newer
// offer mutation, and two queued saves cannot build their local state from the same stale snapshot.
private val supplierGoodsPriceOperationMutex = Mutex()

private val supplierOrderReadCoordinator =
    SingleFlightRequestCoordinator<String, DataState<List<SupplierOrderWithLinesDataModel>>>()
// Supplier-order reads and mutations share one operation mutex. A slower GET must never overwrite
// a newer response, packing transition, dispatch transition, cancellation, or Store receipt.
private val supplierOrderOperationMutex = Mutex()
private val supplierDashboardReadCoordinator =
    SingleFlightRequestCoordinator<String, DataState<SupplierModeDashboardDataModel>>()
private const val SUPPLIER_DASHBOARD_RECENT_SUCCESS_WINDOW_MILLIS = 2_000L
private const val SUPPLIER_DASHBOARD_SCOPE_ALL = "all"

private val supplierDashboardCacheMutex = Mutex()
private val supplierDashboardCacheByScope = mutableMapOf<String, SupplierModeDashboardDataModel>()
private val supplierDashboardLastSuccessAtMillisByScope = mutableMapOf<String, Long>()
private var supplierDashboardVisibleScopeKey: String = ""
private val supplierContractReadCoordinator =
    SingleFlightRequestCoordinator<String, DataState<List<SupplierPartnershipContractDataModel>>>()
// Contract reads and mutations share one operation mutex. A slow scoped GET must not overwrite a
// newer proposal/accept/decline result, and two lifecycle actions must never race each other.
private val supplierContractOperationMutex = Mutex()

private suspend fun emitSupplierContractsAndAwait(
    state: DataState.Success<List<SupplierPartnershipContractDataModel>>
) {
    supplierPartnershipContractsState.emit(state)
    // Publication is synchronous. Retain this explicit visibility barrier at the operation boundary
    // so a future state implementation change cannot quietly release the lock before the list is visible.
    supplierPartnershipContractsState.payload.first { current -> current == state.payload }
}

private fun logSupplierContractDiagnostic(message: String) {
    println("AITA supplier contracts: $message")
}

private suspend fun emitSupplierDashboardAndAwait(
    state: DataState.Success<SupplierModeDashboardDataModel>
) {
    supplierModeDashboardState.emit(state)
    // The dashboard soft-cache must never become "fresh" before its matching payload is visible.
    // Otherwise an immediate realtime echo could return the previous dashboard from the cache.
    supplierModeDashboardState.payload.first { current -> current == state.payload }
}

private fun refreshSupplierDashboardAfterContractMutationIfNeeded() {
    if (appModeState.value == APP_MODE_SUPPLIER || appModeState.value == APP_MODE_MANUFACTURER) {
        getSupplierModeDashboard(force = true)
    }
}

private val supplierWorkspaceRefreshScheduleMutex = Mutex()
// The throttle belongs to an account + Supplier identity scope. Switching identities must never
// reuse the previous identity's 15-second freshness window and leave the new workspace stale.
private var supplierWorkspaceRefreshScopeKey: String = ""
private var supplierWorkspaceLastBaseRefreshAtMillis: Long = 0L
private var supplierWorkspaceLastContractsRefreshAtMillis: Long = 0L

val securitySessionsState = MutableDataStateFlow<List<SecuritySessionDataModel>>(GlobalScope)
val securitySessionHistoryState = MutableDataStateFlow<List<SecuritySessionHistoryDataModel>>(GlobalScope)
private val getSecuritySessionsMutex = Mutex()
private val getSecuritySessionHistoryMutex = Mutex()
private val revokeSecuritySessionMutex = Mutex()
private val revokeOtherSecuritySessionsMutex = Mutex()

private suspend fun emitSupplierGoodsPricesAndAwait(
    state: DataState.Success<List<SupplierGoodsPriceDataModel>>
) {
    supplierGoodsPricesState.emit(state)
    // Publication is synchronous. Keep the explicit payload barrier before releasing the operation
    // mutex so a future state implementation change cannot let a queued operation lose the prior save.
    supplierGoodsPricesState.payload.first { current -> current == state.payload }
}

fun getSupplierGoodsPrices(
    storeId: String,
    onCompleted: ((DataState<List<SupplierGoodsPriceDataModel>>) -> Unit)? = null
) {
    val cleanStoreId = storeId.trim()
    val requestUserScope = currentSupplierNetworkUserScope()
    if (requestUserScope.isBlank()) return
    val requestKey = "$requestUserScope|store:${cleanStoreId.lowercase()}"
    GlobalScope.launch(Dispatchers.ourIo) {
        val result = supplierGoodsPriceReadCoordinator.run(requestKey) {
            supplierGoodsPriceOperationMutex.withLock {
                val response = networkRequest<List<SupplierGoodsPriceDataModel>, Unit>(
                    method = HttpMethod.Get,
                    endpointUrl = globalAppConfigurationState.payloadValue.getSupplierGoodsPricesPath.first,
                    headers = mapOf("store_id" to cleanStoreId)
                )

                if (!supplierNetworkUserScopeIsCurrent(requestUserScope)) {
                    DataState.Empty()
                } else if (response.negative || response.payload == null) {
                    postInAppNotification(response.message, NotificationType.Negative)
                    DataState.Empty(response.message)
                } else {
                    val normalizedStoreId = cleanStoreId.lowercase()
                    val scopedPrices = response.payload
                        .filter { price -> price.storeId.trim().lowercase() == normalizedStoreId }
                        .normalizedSupplierGoodsPriceBook()
                    val mergedPrices = (
                        supplierGoodsPricesState.payloadValue.orEmpty().filterNot { price ->
                            price.storeId.trim().lowercase() == normalizedStoreId
                        } + scopedPrices
                        ).normalizedSupplierGoodsPriceBook()
                    emitSupplierGoodsPricesAndAwait(DataState.Success(mergedPrices, response.message))
                    DataState.Success(scopedPrices, response.message)
                }
            }
        }
        if (supplierNetworkUserScopeIsCurrent(requestUserScope)) onCompleted?.invoke(result)
    }
}

fun getMySupplierGoodsPrices(
    supplierId: String? = effectiveActiveSupplierProfileId(),
    onCompleted: ((DataState<List<SupplierGoodsPriceDataModel>>) -> Unit)? = null
) {
    val cleanSupplierId = normalizeSupplierProfileIdentityId(supplierId).orEmpty()
    val requestUserScope = currentSupplierNetworkUserScope()
    if (requestUserScope.isBlank()) return
    val requestKey = if (cleanSupplierId.isBlank()) {
        "$requestUserScope|my"
    } else {
        "$requestUserScope|supplier:$cleanSupplierId"
    }
    GlobalScope.launch(Dispatchers.ourIo) {
        val result = supplierGoodsPriceReadCoordinator.run(requestKey) {
            supplierGoodsPriceOperationMutex.withLock {
                val response = networkRequest<List<SupplierGoodsPriceDataModel>, Unit>(
                    method = HttpMethod.Get,
                    endpointUrl = globalAppConfigurationState.payloadValue.getMySupplierGoodsPricesPath.first,
                    headers = buildMap {
                        cleanSupplierId.takeIf { it.isNotBlank() }?.let { put("supplier_id", it) }
                    }
                )

                if (!supplierNetworkUserScopeIsCurrent(requestUserScope)) {
                    DataState.Empty()
                } else if (response.negative || response.payload == null) {
                    postInAppNotification(response.message, NotificationType.Negative)
                    DataState.Empty(response.message)
                } else {
                    val scopedPrices = response.payload
                        .filter { price -> price.matchesSupplierProfileFocus(cleanSupplierId.takeIf { it.isNotBlank() }) }
                        .normalizedSupplierGoodsPriceBook()
                    if (cleanSupplierId.isBlank()) {
                        DataState.Success(scopedPrices, response.message).also { state ->
                            emitSupplierGoodsPricesAndAwait(state)
                        }
                    } else {
                        val mergedPrices = (
                            supplierGoodsPricesState.payloadValue.orEmpty().filterNot { price ->
                                price.matchesSupplierProfileFocus(cleanSupplierId)
                            } + scopedPrices
                            ).normalizedSupplierGoodsPriceBook()
                        emitSupplierGoodsPricesAndAwait(DataState.Success(mergedPrices, response.message))
                        DataState.Success(scopedPrices, response.message)
                    }
                }
            }
        }
        if (supplierNetworkUserScopeIsCurrent(requestUserScope)) onCompleted?.invoke(result)
    }
}

fun upsertSupplierGoodsPrice(
    price: SupplierGoodsPriceDataModel,
    onCompleted: ((DataState<SupplierGoodsPriceDataModel>) -> Unit)? = null
) {
    GlobalScope.launch(Dispatchers.ourIo) {
        val result: DataState<SupplierGoodsPriceDataModel> = try {
            supplierGoodsPriceOperationMutex.withLock {
                val response = networkRequest<SupplierGoodsPriceDataModel, SupplierGoodsPriceDataModel>(
                    method = HttpMethod.Post,
                    endpointUrl = globalAppConfigurationState.payloadValue.upsertSupplierGoodsPricePath.first,
                    body = price
                )

                if (response.negative || response.payload == null) {
                    postInAppNotification(response.message, NotificationType.Negative)
                    DataState.Empty(response.message)
                } else {
                    val nextPriceBookState = DataState.Success(
                        supplierGoodsPricesState.payloadValue
                            .orEmpty()
                            .upsertSupplierGoodsPriceByIdentity(response.payload),
                        response.message
                    )
                    emitSupplierGoodsPricesAndAwait(nextPriceBookState)
                    DataState.Success(response.payload, response.message)
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            logNetworkAttempt(
                "supplier price upsert failed unexpectedly: ${networkFailureSummary(throwable)}"
            )
            val message = localizedStringResourceMessage(
                id = 2331,
                main = "Could not save this offer",
                ru = "Не удалось сохранить предложение",
                kk = "Ұсынысты сақтау мүмкін болмады"
            )
            postInAppNotification(message, NotificationType.Negative)
            DataState.Empty(message)
        }

        if (result is DataState.Success &&
            (appModeState.value == APP_MODE_SUPPLIER || appModeState.value == APP_MODE_MANUFACTURER)
        ) {
            // The mutation response already updates the price book. Only the derived dashboard needs
            // one refresh; do not immediately re-read the entire price book.
            getSupplierModeDashboard(force = true)
        }
        onCompleted?.invoke(result)
    }
}

@kotlinx.serialization.Serializable
data class SupplierContractPriceTermDataModel(
    val id: String = "",
    val goodsItemId: String = "",
    val goodsItemNameSnapshot: List<LocalizedStringDataModel> = emptyList(),
    val supplyPrice: PriceDataModel? = null,
    val suggestedSalePrice: PriceDataModel? = null,
    val minOrderQuantity: QuantityDataModel? = null,
    val packageQuantity: QuantityDataModel? = null,
    val scheduleText: List<LocalizedStringDataModel> = emptyList(),
    val note: List<LocalizedStringDataModel> = emptyList(),
    val isActive: Boolean = true
)

@kotlinx.serialization.Serializable
data class SupplierContractRevisionActionRequestDataModel(
    val contractId: String = "",
    val revision: Int = 0
)

@kotlinx.serialization.Serializable
data class SupplierPartnershipContractDataModel(
    val id: String = "",
    val storeId: String = "",
    val supplierId: String = "",
    val authorUserId: String = "",
    val lastEditorUserId: String = "",
    val authorSide: String = SUPPLIER_CONTRACT_SIDE_SUPPLIER,
    val scopeType: String = SUPPLIER_CONTRACT_SCOPE_PARTNERSHIP,
    val goodsItemIds: List<String> = emptyList(),
    val title: List<LocalizedStringDataModel> = emptyList(),
    val summary: List<LocalizedStringDataModel> = emptyList(),
    val conditions: List<String> = emptyList(),
    val customTerms: List<LocalizedStringDataModel> = emptyList(),
    val deliverySchedule: List<LocalizedStringDataModel> = emptyList(),
    val paymentSchedule: List<LocalizedStringDataModel> = emptyList(),
    val priceTerms: List<SupplierContractPriceTermDataModel> = emptyList(),
    val status: String = SUPPLIER_CONTRACT_STATUS_PENDING_STORE,
    val revision: Int = 1,
    val supplierAcceptedAtMillis: Long? = null,
    val storeAcceptedAtMillis: Long? = null,
    val supplierAcceptedByUserId: String? = null,
    val storeAcceptedByUserId: String? = null,
    val declinedAtMillis: Long? = null,
    val declinedByUserId: String? = null,
    val storeNameSnapshot: List<LocalizedStringDataModel> = emptyList(),
    val storePublicIdSnapshot: String = "",
    val supplierNameSnapshot: List<LocalizedStringDataModel> = emptyList(),
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L,
    val isActive: Boolean = true
) {
    val isFullyAccepted: Boolean
        get() = status == SUPPLIER_CONTRACT_STATUS_ACTIVE || (supplierAcceptedAtMillis != null && storeAcceptedAtMillis != null)

    fun requiresStoreAcceptance(): Boolean = status == SUPPLIER_CONTRACT_STATUS_PENDING_STORE
    fun requiresSupplierAcceptance(): Boolean = status == SUPPLIER_CONTRACT_STATUS_PENDING_SUPPLIER
}


fun String.isPendingSupplierContractStatus(): Boolean =
    this == SUPPLIER_CONTRACT_STATUS_PENDING_STORE ||
        this == SUPPLIER_CONTRACT_STATUS_PENDING_SUPPLIER

fun SupplierPartnershipContractDataModel.requiresAcceptanceFrom(actorSide: String): Boolean =
    isActive && when (actorSide.trim().lowercase()) {
        SUPPLIER_CONTRACT_SIDE_STORE -> status == SUPPLIER_CONTRACT_STATUS_PENDING_STORE
        SUPPLIER_CONTRACT_SIDE_SUPPLIER -> status == SUPPLIER_CONTRACT_STATUS_PENDING_SUPPLIER
        else -> false
    }

fun SupplierPartnershipContractDataModel.waitsForOtherContractSide(actorSide: String): Boolean =
    isActive && status.isPendingSupplierContractStatus() && !requiresAcceptanceFrom(actorSide)

fun SupplierPartnershipContractDataModel.canBeDeclinedByContractParty(): Boolean =
    isActive && status.isPendingSupplierContractStatus()

fun SupplierPartnershipContractDataModel.canBeArchivedByContractParty(): Boolean =
    isActive && (status == SUPPLIER_CONTRACT_STATUS_ACTIVE || status == SUPPLIER_CONTRACT_STATUS_DECLINED)

/**
 * Returns whether this live agreement actually covers any of the supplied goods. A malformed
 * goods-scoped agreement with no goods never expands into a partnership-wide agreement.
 */
fun SupplierPartnershipContractDataModel.coversSupplierSupplyGoods(
    goodsItemIds: Collection<String>
): Boolean {
    if (!isActive) return false

    return when (scopeType) {
        SUPPLIER_CONTRACT_SCOPE_PARTNERSHIP -> true
        SUPPLIER_CONTRACT_SCOPE_GOODS_ITEM,
        SUPPLIER_CONTRACT_SCOPE_GOODS_GROUP -> {
            val contractGoods = this.goodsItemIds
                .asSequence()
                .map { it.trim().lowercase() }
                .filter { it.isNotBlank() }
                .toSet()
            contractGoods.isNotEmpty() && goodsItemIds
                .asSequence()
                .map { it.trim().lowercase() }
                .filter { it.isNotBlank() }
                .any { it in contractGoods }
        }
        else -> false
    }
}

/**
 * Only a live pending proposal can block a supply action. Declined and archived proposals are
 * historical decisions, while active agreements remain informative without blocking movement.
 */
fun SupplierPartnershipContractDataModel.blocksSupplierSupplyForGoods(
    goodsItemIds: Collection<String>
): Boolean = status.isPendingSupplierContractStatus() && coversSupplierSupplyGoods(goodsItemIds)

/**
 * A scoped read replaces only that read scope. This prevents a Store-side contract request from
 * erasing Supplier-mode contracts for unrelated Stores, while a full Supplier-mode read remains
 * authoritative for the complete payload it requested.
 */
fun List<SupplierPartnershipContractDataModel>.mergedWithSupplierContractRead(
    incoming: List<SupplierPartnershipContractDataModel>,
    storeId: String? = null,
    supplierId: String? = null
): List<SupplierPartnershipContractDataModel> {
    val cleanStoreId = storeId.orEmpty().trim().lowercase()
    val cleanSupplierId = supplierId.orEmpty().trim().lowercase()
    val preserved = if (cleanStoreId.isBlank() && cleanSupplierId.isBlank()) {
        emptyList()
    } else {
        filterNot { contract ->
            val storeMatches = cleanStoreId.isBlank() ||
                contract.storeId.trim().lowercase() == cleanStoreId
            val supplierMatches = cleanSupplierId.isBlank() ||
                contract.supplierId.trim().lowercase() == cleanSupplierId
            storeMatches && supplierMatches
        }
    }

    val keyed = linkedMapOf<String, SupplierPartnershipContractDataModel>()
    val anonymous = mutableListOf<SupplierPartnershipContractDataModel>()
    (preserved + incoming).forEach { contract ->
        val key = contract.id.trim().lowercase()
        if (key.isBlank()) {
            anonymous += contract
        } else {
            val current = keyed[key]
            if (current == null ||
                contract.revision > current.revision ||
                (contract.revision == current.revision &&
                    maxOf(contract.updatedAtMillis, contract.createdAtMillis) >=
                    maxOf(current.updatedAtMillis, current.createdAtMillis))
            ) {
                keyed[key] = contract
            }
        }
    }

    return (keyed.values + anonymous)
        .sortedWith(
            compareByDescending<SupplierPartnershipContractDataModel> {
                maxOf(it.updatedAtMillis, it.createdAtMillis)
            }.thenByDescending { it.revision }
                .thenBy { it.id }
        )
}

/**
 * Replaces one locally cached agreement only when the incoming revision is at least as fresh as the
 * copy already observed (for example through realtime). A late mutation response must not roll a
 * newer revision back on this client.
 */
fun List<SupplierPartnershipContractDataModel>.upsertSupplierContractByRevision(
    incoming: SupplierPartnershipContractDataModel
): List<SupplierPartnershipContractDataModel> =
    mergedWithSupplierContractRead(incoming = this + incoming)

@kotlinx.serialization.Serializable
data class SupplierOrderWithLinesDataModel(
    val order: SupplierOrderDataModel,
    val lines: List<SupplierOrderLineDataModel>
)

@kotlinx.serialization.Serializable
data class SupplierOrderStatusUpdateRequestDataModel(
    val orderIds: List<String> = emptyList(),
    val status: SupplierOrderStatusDataModel = SupplierOrderStatusDataModel.SeenBySupplier,
    val comment: String? = null
)

fun PriceDataModel?.hasPositiveSupplierDeskPrice(): Boolean =
    this?.price?.toMoneyDouble()?.let { amount -> amount.isFinite() && amount > 0.0 } == true

fun SupplierOrderLineDataModel.supplierDeskAcceptedQuantityTotal(): Double? =
    supplierAcceptedQuantity?.total?.coerceAtLeast(0.0)

fun SupplierOrderLineDataModel.supplierDeskPhysicalQuantityTotal(): Double =
    (supplierAcceptedQuantity?.total ?: requestedQuantity.total).coerceAtLeast(0.0)

fun SupplierOrderLineDataModel.hasPositiveSupplierDeskAcceptedQuantity(): Boolean =
    (supplierDeskAcceptedQuantityTotal() ?: 0.0) > 0.0

fun SupplierOrderLineDataModel.isMissingSupplierDeskAcceptedQuantity(): Boolean =
    supplierAcceptedQuantity == null

fun SupplierOrderLineDataModel.isMissingSupplierDeskOfferedPriceForAcceptedQuantity(): Boolean =
    hasPositiveSupplierDeskAcceptedQuantity() && !supplierOfferedSupplyPrice.hasPositiveSupplierDeskPrice()

fun SupplierOrderLineDataModel.needsSupplierDeskOfferedPrice(): Boolean =
    (supplierDeskAcceptedQuantityTotal() ?: 1.0) > 0.0

fun SupplierOrderLineDataModel.hasCompleteSupplierResponseLineForSupplierDesk(): Boolean {
    val acceptedTotal = supplierDeskAcceptedQuantityTotal() ?: return false
    return acceptedTotal <= 0.0 || supplierOfferedSupplyPrice.hasPositiveSupplierDeskPrice()
}

fun SupplierOrderDataModel.hasCompleteSupplierResponseForSupplierDesk(activeLines: List<SupplierOrderLineDataModel>): Boolean {
    val cleanLines = activeLines.filter { it.isActive }
    return cleanLines.isNotEmpty() &&
        confirmedDeliveryTimeMillis != null &&
        cleanLines.any { line -> (line.supplierDeskAcceptedQuantityTotal() ?: 0.0) > 0.0 } &&
        cleanLines.all { line -> line.hasCompleteSupplierResponseLineForSupplierDesk() }
}

fun SupplierOrderDataModel.isSupplierReadyToPackForSupplierDesk(activeLines: List<SupplierOrderLineDataModel>): Boolean =
    status == SupplierOrderStatusDataModel.Confirmed && hasCompleteSupplierResponseForSupplierDesk(activeLines)

fun SupplierOrderWithLinesDataModel.hasCompleteSupplierResponseForSupplierDesk(): Boolean =
    order.hasCompleteSupplierResponseForSupplierDesk(lines)

fun SupplierOrderWithLinesDataModel.isSupplierReadyToPackForSupplierDesk(): Boolean =
    order.isSupplierReadyToPackForSupplierDesk(lines)

fun SupplierOrderWithLinesDataModel.hasSupplierResponseGapsForSupplierDesk(): Boolean {
    val cleanLines = lines.filter { it.isActive }
    return cleanLines.isEmpty() ||
        order.confirmedDeliveryTimeMillis == null ||
        cleanLines.none { line -> (line.supplierDeskAcceptedQuantityTotal() ?: 0.0) > 0.0 } ||
        cleanLines.any { line -> !line.hasCompleteSupplierResponseLineForSupplierDesk() }
}

/**
 * One canonical definition shared by Store receiving, Supplier Orders, Customers, Insights and the
 * server dashboard. Keeping this in shared code prevents each screen from quietly inventing its own
 * interpretation of an open or actionable supplier order.
 */
fun SupplierOrderStatusDataModel.isClosedForSupplierDesk(): Boolean =
    this == SupplierOrderStatusDataModel.Delivered || this == SupplierOrderStatusDataModel.Cancelled

fun SupplierOrderStatusDataModel.needsSupplierActionForSupplierDesk(
    hasResponseGaps: Boolean
): Boolean = !isClosedForSupplierDesk() &&
    (this == SupplierOrderStatusDataModel.Sent ||
        this == SupplierOrderStatusDataModel.SeenBySupplier ||
        this == SupplierOrderStatusDataModel.IssueReported ||
        hasResponseGaps)

fun SupplierOrderDataModel.needsSupplierActionForSupplierDesk(
    activeLines: List<SupplierOrderLineDataModel>
): Boolean = isActive && status.needsSupplierActionForSupplierDesk(
    SupplierOrderWithLinesDataModel(this, activeLines).hasSupplierResponseGapsForSupplierDesk()
)

fun SupplierOrderWithLinesDataModel.needsSupplierActionForSupplierDesk(): Boolean =
    order.needsSupplierActionForSupplierDesk(lines)

@kotlinx.serialization.Serializable
data class SupplierDashboardStatusBucketDataModel(
    val status: SupplierOrderStatusDataModel = SupplierOrderStatusDataModel.Draft,
    val orderCount: Int = 0,
    val lineCount: Int = 0
)

@kotlinx.serialization.Serializable
data class SupplierDashboardDemandDataModel(
    val goodsItemId: String = "",
    val goodsItemNameSnapshot: List<LocalizedStringDataModel> = emptyList(),
    val barcodeSnapshots: List<String> = emptyList(),
    val measurementUnitIdSnapshot: String? = null,
    val requestedQuantityTotal: Double = 0.0,
    val requestLineCount: Int = 0,
    val openOrderCount: Int = 0,
    val storeCount: Int = 0,
    val latestStatus: SupplierOrderStatusDataModel = SupplierOrderStatusDataModel.Draft,
    val latestActivityMillis: Long = 0L,
    val latestExpectedSupplyPrice: PriceDataModel? = null,
    val latestOfferedSupplyPrice: PriceDataModel? = null
)

@kotlinx.serialization.Serializable
data class SupplierDashboardPartnerDataModel(
    val storeId: String = "",
    val storeNameSnapshot: List<LocalizedStringDataModel> = emptyList(),
    val storePublicIdSnapshot: String = "",
    val storeAddressTextSnapshot: String = "",
    val orderCount: Int = 0,
    val openOrderCount: Int = 0,
    val actionRequiredOrderCount: Int = 0,
    val readyToPackOrderCount: Int = 0,
    val packedOrderCount: Int = 0,
    val inDeliveryOrderCount: Int = 0,
    val partiallyDeliveredOrderCount: Int = 0,
    val overdueOrderCount: Int = 0,
    val deliveredOrderCount: Int = 0,
    val issueOrderCount: Int = 0,
    val catalogSkuCount: Int = 0,
    val savedOfferCount: Int = 0,
    val validOfferCount: Int = 0,
    val priceGapCount: Int = 0,
    val latestStatus: SupplierOrderStatusDataModel = SupplierOrderStatusDataModel.Draft,
    val latestActivityMillis: Long = 0L,
    val activeContractCount: Int = 0,
    val pendingContractCount: Int = 0
)

@kotlinx.serialization.Serializable
data class SupplierDashboardProfileDataModel(
    val supplierId: String = "",
    val name: List<LocalizedStringDataModel> = emptyList(),
    val phoneNumbers: List<String> = emptyList(),
    val emails: List<String> = emptyList(),
    val orderCount: Int = 0,
    val openOrderCount: Int = 0,
    val actionRequiredOrderCount: Int = 0,
    val catalogSkuCount: Int = 0,
    val savedOfferCount: Int = 0,
    val stockBatchCount: Int = 0,
    val commercialHistoryCount: Int = 0,
    val partnerCount: Int = 0,
    val activeContractCount: Int = 0,
    val pendingContractCount: Int = 0,
    val latestActivityMillis: Long = 0L
)

@kotlinx.serialization.Serializable
data class SupplierDashboardActionDataModel(
    val actionId: String = "",
    val actionType: String = "",
    val priority: Int = 0,
    val orderId: String = "",
    val storeId: String = "",
    val supplierId: String = "",
    val storeNameSnapshot: List<LocalizedStringDataModel> = emptyList(),
    val storePublicIdSnapshot: String = "",
    val status: SupplierOrderStatusDataModel = SupplierOrderStatusDataModel.Draft,
    val dueAtMillis: Long? = null,
    val latestActivityMillis: Long = 0L,
    val lineCount: Int = 0,
    val missingAcceptedQuantityCount: Int = 0,
    val missingOfferedPriceCount: Int = 0,
    val amount: PriceDataModel? = null,
    val goodsPreview: List<LocalizedStringDataModel> = emptyList(),
    val attentionSummary: List<LocalizedStringDataModel> = emptyList()
)

@kotlinx.serialization.Serializable
data class SupplierDashboardDeliveryBucketDataModel(
    val bucketId: String = "",
    val title: List<LocalizedStringDataModel> = emptyList(),
    val orderCount: Int = 0,
    val lineCount: Int = 0,
    val storeCount: Int = 0,
    val actionRequiredOrderCount: Int = 0,
    val packedOrderCount: Int = 0,
    val inDeliveryOrderCount: Int = 0,
    val issueOrderCount: Int = 0,
    val earliestDueAtMillis: Long? = null,
    val latestDueAtMillis: Long? = null,
    val goodsPreview: List<LocalizedStringDataModel> = emptyList()
)

@kotlinx.serialization.Serializable
data class SupplierDashboardDispatchRunDataModel(
    val runId: String = "",
    val supplierId: String = "",
    val storeId: String = "",
    val storeNameSnapshot: List<LocalizedStringDataModel> = emptyList(),
    val storePublicIdSnapshot: String = "",
    val storeAddressTextSnapshot: String = "",
    val orderIds: List<String> = emptyList(),
    val packableOrderIds: List<String> = emptyList(),
    val dispatchableOrderIds: List<String> = emptyList(),
    val inDeliveryOrderIds: List<String> = emptyList(),
    val issueOrderIds: List<String> = emptyList(),
    val attentionOrderIds: List<String> = emptyList(),
    val contractBlockedOrderIds: List<String> = emptyList(),
    val statusMix: List<SupplierDashboardStatusBucketDataModel> = emptyList(),
    val orderCount: Int = 0,
    val lineCount: Int = 0,
    val readyToPackOrderCount: Int = 0,
    val packedOrderCount: Int = 0,
    val inDeliveryOrderCount: Int = 0,
    val issueOrderCount: Int = 0,
    val actionRequiredOrderCount: Int = 0,
    val activeContractCount: Int = 0,
    val pendingContractCount: Int = 0,
    val earliestDueAtMillis: Long? = null,
    val latestDueAtMillis: Long? = null,
    val latestActivityMillis: Long = 0L,
    val goodsPreview: List<LocalizedStringDataModel> = emptyList(),
    val packChecklist: List<LocalizedStringDataModel> = emptyList(),
    val attentionSummary: List<LocalizedStringDataModel> = emptyList(),
    val driverHandoffChecklist: List<LocalizedStringDataModel> = emptyList(),
    val estimatedAmount: PriceDataModel? = null,
    val priorityScore: Int = 0,
    val suggestedAction: String = ""
)

@kotlinx.serialization.Serializable
data class SupplierDashboardReadinessDataModel(
    val openOrderCount: Int = 0,
    val answerNeededOrderCount: Int = 0,
    val responseReadyOrderCount: Int = 0,
    val readyToPackOrderCount: Int = 0,
    val packReadyLineCount: Int = 0,
    val missingAcceptedQuantityLineCount: Int = 0,
    val missingOfferedPriceLineCount: Int = 0,
    val priceBookCoveredLineCount: Int = 0,
    val priceBookMissingLineCount: Int = 0,
    val priceBookCoveragePercent: Int = 0,
    val responseLineCount: Int = 0,
    val answeredLineCount: Int = 0,
    val declinedLineCount: Int = 0,
    val positiveAcceptedLineCount: Int = 0,
    val requestedQuantityTotal: Double = 0.0,
    val acceptedQuantityTotal: Double = 0.0,
    val responseProgressPercent: Int = 0,
    val acceptedVsRequestedPercent: Int = 0,
    val estimatedReadyAmount: PriceDataModel? = null,
    val earliestDueAtMillis: Long? = null,
    val generatedAtMillis: Long = 0L
)

@kotlinx.serialization.Serializable
data class SupplierDashboardManufacturerBridgeDataModel(
    val bridgeId: String = "",
    val goodsItemId: String = "",
    val goodsItemNameSnapshot: List<LocalizedStringDataModel> = emptyList(),
    val barcodeSnapshots: List<String> = emptyList(),
    val measurementUnitIdSnapshot: String? = null,
    val orderIds: List<String> = emptyList(),
    val quoteNeededOrderIds: List<String> = emptyList(),
    val productionOrderIds: List<String> = emptyList(),
    val shipmentOrderIds: List<String> = emptyList(),
    val storePreview: List<LocalizedStringDataModel> = emptyList(),
    val requestedQuantityTotal: Double = 0.0,
    val acceptedQuantityTotal: Double = 0.0,
    val missingQuantityTotal: Double = 0.0,
    val openOrderCount: Int = 0,
    val confirmedOrderCount: Int = 0,
    val storeCount: Int = 0,
    val priceBookRowCount: Int = 0,
    val responseCoveragePercent: Int = 0,
    val estimatedAcceptedAmount: PriceDataModel? = null,
    val earliestDueAtMillis: Long? = null,
    val latestActivityMillis: Long = 0L,
    val priorityScore: Int = 0,
    val suggestedAction: String = "",
    val attentionSummary: List<LocalizedStringDataModel> = emptyList()
)

@kotlinx.serialization.Serializable
data class SupplierDashboardBackorderDataModel(
    val backorderId: String = "",
    val goodsItemId: String = "",
    val goodsItemNameSnapshot: List<LocalizedStringDataModel> = emptyList(),
    val barcodeSnapshots: List<String> = emptyList(),
    val measurementUnitIdSnapshot: String? = null,
    val affectedOrderIds: List<String> = emptyList(),
    val storePreview: List<LocalizedStringDataModel> = emptyList(),
    val requestedQuantityTotal: Double = 0.0,
    val acceptedQuantityTotal: Double = 0.0,
    val missingQuantityTotal: Double = 0.0,
    val missingLineCount: Int = 0,
    val declinedLineCount: Int = 0,
    val partialLineCount: Int = 0,
    val fullyShortLineCount: Int = 0,
    val recoveryLane: String = "",
    val recoveryHint: List<LocalizedStringDataModel> = emptyList(),
    val recoveryUrgencyLane: String = "",
    val recoveryUrgencyHint: List<LocalizedStringDataModel> = emptyList(),
    val recoveryOwnerLane: String = "",
    val recoveryOwnerHint: List<LocalizedStringDataModel> = emptyList(),
    val recoverySlaLane: String = "",
    val recoverySlaHint: List<LocalizedStringDataModel> = emptyList(),
    val recoveryCheckpointAtMillis: Long? = null,
    val recoveryEscalationLane: String = "",
    val recoveryEscalationHint: List<LocalizedStringDataModel> = emptyList(),
    val recoveryProofLane: String = "",
    val recoveryProofHint: List<LocalizedStringDataModel> = emptyList(),
    val recoveryOutcomeLane: String = "",
    val recoveryOutcomeHint: List<LocalizedStringDataModel> = emptyList(),
    val recoveryPackGuardLane: String = "",
    val recoveryPackGuardHint: List<LocalizedStringDataModel> = emptyList(),
    val recoveryContactLane: String = "",
    val recoveryContactHint: List<LocalizedStringDataModel> = emptyList(),
    val recoveryContactScript: List<LocalizedStringDataModel> = emptyList(),
    val recoveryRiskLane: String = "",
    val recoveryRiskHint: List<LocalizedStringDataModel> = emptyList(),
    val recoveryRiskScore: Int = 0,
    val recoveryRiskReasons: List<LocalizedStringDataModel> = emptyList(),
    val recoveryConfidenceLane: String = "",
    val recoveryConfidenceHint: List<LocalizedStringDataModel> = emptyList(),
    val recoveryConfidenceScore: Int = 0,
    val recoveryConfidenceChecklist: List<LocalizedStringDataModel> = emptyList(),
    val recoveryFollowUpLane: String = "",
    val recoveryFollowUpHint: List<LocalizedStringDataModel> = emptyList(),
    val recoveryFollowUpAtMillis: Long? = null,
    val recoveryFollowUpScript: List<LocalizedStringDataModel> = emptyList(),
    val recoveryHandoffLane: String = "",
    val recoveryHandoffHint: List<LocalizedStringDataModel> = emptyList(),
    val recoveryHandoffChecklist: List<LocalizedStringDataModel> = emptyList(),
    val recoveryHandoffScript: List<LocalizedStringDataModel> = emptyList(),
    val recoveryClosureLane: String = "",
    val recoveryClosureHint: List<LocalizedStringDataModel> = emptyList(),
    val recoveryClosureScore: Int = 0,
    val recoveryClosureChecklist: List<LocalizedStringDataModel> = emptyList(),
    val recoveryClosureScript: List<LocalizedStringDataModel> = emptyList(),
    val recoveryLedgerLane: String = "",
    val recoveryLedgerHint: List<LocalizedStringDataModel> = emptyList(),
    val recoveryLedgerScore: Int = 0,
    val recoveryLedgerChecklist: List<LocalizedStringDataModel> = emptyList(),
    val recoveryLedgerScript: List<LocalizedStringDataModel> = emptyList(),
    val recoveryTriageLane: String = "",
    val recoveryTriageHint: List<LocalizedStringDataModel> = emptyList(),
    val recoveryTriageScore: Int = 0,
    val recoveryTriageChecklist: List<LocalizedStringDataModel> = emptyList(),
    val recoveryTriageScript: List<LocalizedStringDataModel> = emptyList(),
    val recoveryCommandLane: String = "",
    val recoveryCommandHint: List<LocalizedStringDataModel> = emptyList(),
    val recoveryCommandScore: Int = 0,
    val recoveryCommandChecklist: List<LocalizedStringDataModel> = emptyList(),
    val recoveryCommandScript: List<LocalizedStringDataModel> = emptyList(),
    val recoveryPromiseShieldLane: String = "",
    val recoveryPromiseShieldHint: List<LocalizedStringDataModel> = emptyList(),
    val recoveryPromiseShieldScore: Int = 0,
    val recoveryPromiseShieldChecklist: List<LocalizedStringDataModel> = emptyList(),
    val recoveryPromiseShieldScript: List<LocalizedStringDataModel> = emptyList(),
    val recoveryWaveLane: String = "",
    val recoveryWaveHint: List<LocalizedStringDataModel> = emptyList(),
    val recoveryWaveScore: Int = 0,
    val recoveryAgingLane: String = "",
    val recoveryAgingHint: List<LocalizedStringDataModel> = emptyList(),
    val recoveryAgingScore: Int = 0,
    val recoveryAgingStartedAtMillis: Long? = null,
    val recoveryAgingHours: Int = 0,
    val recoveryAgingChecklist: List<LocalizedStringDataModel> = emptyList(),
    val recoveryAgingScript: List<LocalizedStringDataModel> = emptyList(),
    val recoveryBottleneckLane: String = "",
    val recoveryBottleneckHint: List<LocalizedStringDataModel> = emptyList(),
    val recoveryBottleneckScore: Int = 0,
    val recoveryBottleneckChecklist: List<LocalizedStringDataModel> = emptyList(),
    val recoveryBottleneckScript: List<LocalizedStringDataModel> = emptyList(),
    val recoveryLoadLane: String = "",
    val recoveryLoadHint: List<LocalizedStringDataModel> = emptyList(),
    val recoveryLoadScore: Int = 0,
    val recoveryLoadChecklist: List<LocalizedStringDataModel> = emptyList(),
    val recoveryLoadScript: List<LocalizedStringDataModel> = emptyList(),
    val recoveryImpactLane: String = "",
    val recoveryImpactHint: List<LocalizedStringDataModel> = emptyList(),
    val recoveryImpactScore: Int = 0,
    val recoveryImpactChecklist: List<LocalizedStringDataModel> = emptyList(),
    val recoveryImpactScript: List<LocalizedStringDataModel> = emptyList(),
    val recoveryCommitLane: String = "",
    val recoveryCommitHint: List<LocalizedStringDataModel> = emptyList(),
    val recoveryCommitScore: Int = 0,
    val recoveryCommitByMillis: Long? = null,
    val recoveryCommitChecklist: List<LocalizedStringDataModel> = emptyList(),
    val recoveryCommitScript: List<LocalizedStringDataModel> = emptyList(),
    val recoveryAllocationLane: String = "",
    val recoveryAllocationHint: List<LocalizedStringDataModel> = emptyList(),
    val recoveryAllocationScore: Int = 0,
    val recoveryAllocationChecklist: List<LocalizedStringDataModel> = emptyList(),
    val recoveryAllocationScript: List<LocalizedStringDataModel> = emptyList(),
    val recoveryExceptionLane: String = "",
    val recoveryExceptionHint: List<LocalizedStringDataModel> = emptyList(),
    val recoveryExceptionScore: Int = 0,
    val recoveryExceptionChecklist: List<LocalizedStringDataModel> = emptyList(),
    val recoveryExceptionScript: List<LocalizedStringDataModel> = emptyList(),
    val recoveryCauseLane: String = "",
    val recoveryCauseHint: List<LocalizedStringDataModel> = emptyList(),
    val recoveryCauseScore: Int = 0,
    val recoveryCauseChecklist: List<LocalizedStringDataModel> = emptyList(),
    val recoveryCauseScript: List<LocalizedStringDataModel> = emptyList(),
    val recoveryVerificationLane: String = "",
    val recoveryVerificationHint: List<LocalizedStringDataModel> = emptyList(),
    val recoveryVerificationScore: Int = 0,
    val recoveryVerificationChecklist: List<LocalizedStringDataModel> = emptyList(),
    val recoveryVerificationScript: List<LocalizedStringDataModel> = emptyList(),
    val recoveryApprovalLane: String = "",
    val recoveryApprovalHint: List<LocalizedStringDataModel> = emptyList(),
    val recoveryApprovalScore: Int = 0,
    val recoveryApprovalChecklist: List<LocalizedStringDataModel> = emptyList(),
    val recoveryApprovalScript: List<LocalizedStringDataModel> = emptyList(),
    val recoveryExecutionLane: String = "",
    val recoveryExecutionHint: List<LocalizedStringDataModel> = emptyList(),
    val recoveryExecutionScore: Int = 0,
    val recoveryExecutionChecklist: List<LocalizedStringDataModel> = emptyList(),
    val recoveryExecutionScript: List<LocalizedStringDataModel> = emptyList(),
    val recoveryReleaseLane: String = "",
    val recoveryReleaseHint: List<LocalizedStringDataModel> = emptyList(),
    val recoveryReleaseScore: Int = 0,
    val recoveryReleaseChecklist: List<LocalizedStringDataModel> = emptyList(),
    val recoveryReleaseScript: List<LocalizedStringDataModel> = emptyList(),
    val recoverySealLane: String = "",
    val recoverySealHint: List<LocalizedStringDataModel> = emptyList(),
    val recoverySealScore: Int = 0,
    val recoverySealChecklist: List<LocalizedStringDataModel> = emptyList(),
    val recoverySealScript: List<LocalizedStringDataModel> = emptyList(),
    val recoveryCloseoutLane: String = "",
    val recoveryCloseoutHint: List<LocalizedStringDataModel> = emptyList(),
    val recoveryCloseoutScore: Int = 0,
    val recoveryCloseoutChecklist: List<LocalizedStringDataModel> = emptyList(),
    val recoveryCloseoutScript: List<LocalizedStringDataModel> = emptyList(),
    val recoveryReopenLane: String = "",
    val recoveryReopenHint: List<LocalizedStringDataModel> = emptyList(),
    val recoveryReopenScore: Int = 0,
    val recoveryReopenAtMillis: Long? = null,
    val recoveryReopenChecklist: List<LocalizedStringDataModel> = emptyList(),
    val recoveryReopenScript: List<LocalizedStringDataModel> = emptyList(),
    val recoveryReconciliationLane: String = "",
    val recoveryReconciliationHint: List<LocalizedStringDataModel> = emptyList(),
    val recoveryReconciliationScore: Int = 0,
    val recoveryReconciliationChecklist: List<LocalizedStringDataModel> = emptyList(),
    val recoveryReconciliationScript: List<LocalizedStringDataModel> = emptyList(),
    val recoveryAuditLane: String = "",
    val recoveryAuditHint: List<LocalizedStringDataModel> = emptyList(),
    val recoveryAuditScore: Int = 0,
    val recoveryAuditChecklist: List<LocalizedStringDataModel> = emptyList(),
    val recoveryAuditScript: List<LocalizedStringDataModel> = emptyList(),
    val nextRecoveryStep: List<LocalizedStringDataModel> = emptyList(),
    val recoveryChecklist: List<LocalizedStringDataModel> = emptyList(),
    val affectedOrderCount: Int = 0,
    val affectedStoreCount: Int = 0,
    val earliestDueAtMillis: Long? = null,
    val latestActivityMillis: Long = 0L,
    val priorityScore: Int = 0,
    val suggestedAction: String = "",
    val attentionSummary: List<LocalizedStringDataModel> = emptyList()
)

@kotlinx.serialization.Serializable
data class SupplierDashboardRecoveryWaveDataModel(
    val recoveryWaveLane: String = "",
    val recoveryWaveHint: List<LocalizedStringDataModel> = emptyList(),
    val recoveryWaveChecklist: List<LocalizedStringDataModel> = emptyList(),
    val recoveryWaveScript: List<LocalizedStringDataModel> = emptyList(),
    val shortageCount: Int = 0,
    val shortQuantityTotal: Double = 0.0,
    val urgentCount: Int = 0,
    val stopPackCount: Int = 0,
    val promiseRiskCount: Int = 0,
    val readyCount: Int = 0,
    val maxRiskScore: Int = 0,
    val maxPriorityScore: Int = 0,
    val nextFollowUpAtMillis: Long? = null,
    val topBackorderId: String = "",
    val topGoodsItemId: String = "",
    val topGoodsItemNameSnapshot: List<LocalizedStringDataModel> = emptyList()
)

@kotlinx.serialization.Serializable
data class SupplierDashboardRecoveryDeskDataModel(
    val recoveryDeskLane: String = "",
    val recoveryDeskHint: List<LocalizedStringDataModel> = emptyList(),
    val recoveryDeskChecklist: List<LocalizedStringDataModel> = emptyList(),
    val recoveryDeskScript: List<LocalizedStringDataModel> = emptyList(),
    val shortageCount: Int = 0,
    val urgentCount: Int = 0,
    val stopPackCount: Int = 0,
    val storeContactCount: Int = 0,
    val sourcingCount: Int = 0,
    val splitShipCount: Int = 0,
    val promiseRiskCount: Int = 0,
    val readyCount: Int = 0,
    val staleRecoveryCount: Int = 0,
    val touchTodayRecoveryCount: Int = 0,
    val freshRecoveryCount: Int = 0,
    val oldestRecoveryAgeHours: Int = 0,
    val averageRecoveryAgeHours: Int = 0,
    val topBottleneckLane: String = "",
    val decisionBottleneckCount: Int = 0,
    val contactBottleneckCount: Int = 0,
    val sourcingBottleneckCount: Int = 0,
    val packBottleneckCount: Int = 0,
    val proofBottleneckCount: Int = 0,
    val agingBottleneckCount: Int = 0,
    val readyBottleneckCount: Int = 0,
    val topLoadLane: String = "",
    val heavyLoadCount: Int = 0,
    val multiStoreLoadCount: Int = 0,
    val packLoadCount: Int = 0,
    val readyLoadCount: Int = 0,
    val averageLoadScore: Int = 0,
    val topImpactLane: String = "",
    val highImpactCount: Int = 0,
    val promiseImpactCount: Int = 0,
    val multiStoreImpactCount: Int = 0,
    val replenishmentImpactCount: Int = 0,
    val controlledImpactCount: Int = 0,
    val averageImpactScore: Int = 0,
    val maxImpactScore: Int = 0,
    val topCommitLane: String = "",
    val blockedCommitCount: Int = 0,
    val dueCommitCount: Int = 0,
    val sourceCommitCount: Int = 0,
    val splitCommitCount: Int = 0,
    val readyCommitCount: Int = 0,
    val averageCommitScore: Int = 0,
    val nextCommitAtMillis: Long? = null,
    val topAllocationLane: String = "",
    val allocationPressureCount: Int = 0,
    val fairSplitAllocationCount: Int = 0,
    val priorityAllocationCount: Int = 0,
    val allocationReadyCount: Int = 0,
    val averageAllocationScore: Int = 0,
    val maxAllocationScore: Int = 0,
    val topExceptionLane: String = "",
    val exceptionPressureCount: Int = 0,
    val stopPackExceptionCount: Int = 0,
    val cancelReviewExceptionCount: Int = 0,
    val substituteExceptionCount: Int = 0,
    val sourcingExceptionCount: Int = 0,
    val allocationExceptionCount: Int = 0,
    val exceptionReadyCount: Int = 0,
    val averageExceptionScore: Int = 0,
    val maxExceptionScore: Int = 0,
    val topCauseLane: String = "",
    val causePressureCount: Int = 0,
    val zeroAcceptanceCauseCount: Int = 0,
    val partialCapacityCauseCount: Int = 0,
    val promiseConflictCauseCount: Int = 0,
    val allocationCauseCount: Int = 0,
    val exceptionCauseCount: Int = 0,
    val causeReadyCount: Int = 0,
    val averageCauseScore: Int = 0,
    val maxCauseScore: Int = 0,
    val topVerificationLane: String = "",
    val verificationBlockerCount: Int = 0,
    val storeVerificationCount: Int = 0,
    val sourceVerificationCount: Int = 0,
    val packVerificationCount: Int = 0,
    val causeVerificationCount: Int = 0,
    val verificationReadyCount: Int = 0,
    val averageVerificationScore: Int = 0,
    val maxVerificationScore: Int = 0,
    val topApprovalLane: String = "",
    val approvalBlockerCount: Int = 0,
    val managerApprovalCount: Int = 0,
    val storeApprovalCount: Int = 0,
    val sourceApprovalCount: Int = 0,
    val packApprovalCount: Int = 0,
    val approvalReadyCount: Int = 0,
    val averageApprovalScore: Int = 0,
    val maxApprovalScore: Int = 0,
    val topExecutionLane: String = "",
    val executionBlockerCount: Int = 0,
    val storeExecutionCount: Int = 0,
    val sourceExecutionCount: Int = 0,
    val splitExecutionCount: Int = 0,
    val readyExecutionCount: Int = 0,
    val averageExecutionScore: Int = 0,
    val maxExecutionScore: Int = 0,
    val topReleaseLane: String = "",
    val releaseBlockerCount: Int = 0,
    val storeReleaseCount: Int = 0,
    val sourceReleaseCount: Int = 0,
    val splitReleaseCount: Int = 0,
    val readyReleaseCount: Int = 0,
    val averageReleaseScore: Int = 0,
    val maxReleaseScore: Int = 0,
    val topSealLane: String = "",
    val sealBlockerCount: Int = 0,
    val storeSealCount: Int = 0,
    val sourceSealCount: Int = 0,
    val splitSealCount: Int = 0,
    val readySealCount: Int = 0,
    val averageSealScore: Int = 0,
    val maxSealScore: Int = 0,
    val topCloseoutLane: String = "",
    val closeoutBlockerCount: Int = 0,
    val storeCloseoutCount: Int = 0,
    val sourceCloseoutCount: Int = 0,
    val splitCloseoutCount: Int = 0,
    val readyCloseoutCount: Int = 0,
    val averageCloseoutScore: Int = 0,
    val maxCloseoutScore: Int = 0,
    val topReopenLane: String = "",
    val reopenBlockerCount: Int = 0,
    val reopenAnswerCount: Int = 0,
    val reopenPromiseCount: Int = 0,
    val reopenSplitCount: Int = 0,
    val reopenReadyCount: Int = 0,
    val averageReopenScore: Int = 0,
    val maxReopenScore: Int = 0,
    val nextReopenAtMillis: Long? = null,
    val topReconciliationLane: String = "",
    val reconciliationBlockerCount: Int = 0,
    val reconciliationStoreCount: Int = 0,
    val reconciliationSourceCount: Int = 0,
    val reconciliationSplitCount: Int = 0,
    val reconciliationReadyCount: Int = 0,
    val averageReconciliationScore: Int = 0,
    val maxReconciliationScore: Int = 0,
    val topAuditLane: String = "",
    val auditBlockerCount: Int = 0,
    val auditQuantityGapCount: Int = 0,
    val auditEvidenceGapCount: Int = 0,
    val auditStoreNoteGapCount: Int = 0,
    val auditReadyCount: Int = 0,
    val averageAuditScore: Int = 0,
    val maxAuditScore: Int = 0,
    val averageRiskScore: Int = 0,
    val maxPriorityScore: Int = 0,
    val nextFollowUpAtMillis: Long? = null,
    val recoveryWaves: List<SupplierDashboardRecoveryWaveDataModel> = emptyList(),
    val topBackorderId: String = "",
    val topGoodsItemId: String = "",
    val topGoodsItemNameSnapshot: List<LocalizedStringDataModel> = emptyList()
)

@kotlinx.serialization.Serializable
data class SupplierModeDashboardDataModel(
    val supplierIds: List<String> = emptyList(),
    val supplierProfiles: List<SupplierDashboardProfileDataModel> = emptyList(),
    val generatedAtMillis: Long = 0L,
    val orderCount: Int = 0,
    val openOrderCount: Int = 0,
    val actionRequiredOrderCount: Int = 0,
    val packedOrderCount: Int = 0,
    val inDeliveryOrderCount: Int = 0,
    val deliveredOrderCount: Int = 0,
    val issueOrderCount: Int = 0,
    val lineCount: Int = 0,
    val catalogSkuCount: Int = 0,
    val partnerCount: Int = 0,
    val activeContractCount: Int = 0,
    val pendingContractCount: Int = 0,
    val statusBuckets: List<SupplierDashboardStatusBucketDataModel> = emptyList(),
    val demandHighlights: List<SupplierDashboardDemandDataModel> = emptyList(),
    val partnerHighlights: List<SupplierDashboardPartnerDataModel> = emptyList(),
    val actionQueue: List<SupplierDashboardActionDataModel> = emptyList(),
    val bulkSeenOrderIds: List<String> = emptyList(),
    val bulkPackableOrderIds: List<String> = emptyList(),
    val bulkDispatchableOrderIds: List<String> = emptyList(),
    val deliveryBuckets: List<SupplierDashboardDeliveryBucketDataModel> = emptyList(),
    val dispatchRuns: List<SupplierDashboardDispatchRunDataModel> = emptyList(),
    val readiness: SupplierDashboardReadinessDataModel = SupplierDashboardReadinessDataModel(),
    val manufacturerBridge: List<SupplierDashboardManufacturerBridgeDataModel> = emptyList(),
    val backorderWatch: List<SupplierDashboardBackorderDataModel> = emptyList(),
    val recoveryDesk: SupplierDashboardRecoveryDeskDataModel = SupplierDashboardRecoveryDeskDataModel()
)

private suspend fun emitSupplierOrderSnapshotAndAwait(
    orders: List<SupplierOrderDataModel>,
    lines: List<SupplierOrderLineDataModel>,
    message: List<LocalizedStringDataModel>? = null
) {
    supplierOrdersState.emit(DataState.Success(orders, message))
    supplierOrderLinesState.emit(DataState.Success(lines, message))
    // Publication is synchronous. Keep the two explicit visibility barriers at the operation
    // boundary so a future state implementation change cannot expose a half-updated order snapshot.
    supplierOrdersState.payload.first { current -> current == orders }
    supplierOrderLinesState.payload.first { current -> current == lines }
}

fun getSupplierOrders(
    storeId: String,
    onCompleted: ((DataState<List<SupplierOrderWithLinesDataModel>>) -> Unit)? = null
) {
    val cleanStoreId = storeId.trim()
    val requestUserScope = currentSupplierNetworkUserScope()
    if (requestUserScope.isBlank()) return
    val requestKey = "$requestUserScope|store:${cleanStoreId.lowercase()}"
    GlobalScope.launch(Dispatchers.ourIo) {
        val result = supplierOrderReadCoordinator.run(requestKey) {
            supplierOrderOperationMutex.withLock {
                val response = networkRequest<List<SupplierOrderWithLinesDataModel>, Unit>(
                    method = HttpMethod.Get,
                    endpointUrl = globalAppConfigurationState.payloadValue.getSupplierOrdersPath.first,
                    headers = mapOf("store_id" to cleanStoreId)
                )
                applySupplierOrderReadResponse(
                    response = response,
                    scope = SupplierOrderReadScope(storeId = cleanStoreId),
                    expectedUserScope = requestUserScope
                )
            }
        }
        if (supplierNetworkUserScopeIsCurrent(requestUserScope)) onCompleted?.invoke(result)
    }
}

fun getSupplierOrdersForSupplier(
    supplierId: String,
    onCompleted: ((DataState<List<SupplierOrderWithLinesDataModel>>) -> Unit)? = null
) {
    val cleanSupplierId = supplierId.trim()
    val requestUserScope = currentSupplierNetworkUserScope()
    if (requestUserScope.isBlank()) return
    val requestKey = "$requestUserScope|supplier:${cleanSupplierId.lowercase()}"
    GlobalScope.launch(Dispatchers.ourIo) {
        val result = supplierOrderReadCoordinator.run(requestKey) {
            supplierOrderOperationMutex.withLock {
                val response = networkRequest<List<SupplierOrderWithLinesDataModel>, Unit>(
                    method = HttpMethod.Get,
                    endpointUrl = globalAppConfigurationState.payloadValue.getSupplierOrdersPath.first,
                    headers = mapOf("supplier_id" to cleanSupplierId)
                )
                applySupplierOrderReadResponse(
                    response = response,
                    scope = SupplierOrderReadScope(supplierId = cleanSupplierId),
                    expectedUserScope = requestUserScope
                )
            }
        }
        if (supplierNetworkUserScopeIsCurrent(requestUserScope)) onCompleted?.invoke(result)
    }
}

fun getMySupplierSideOrders(
    onCompleted: ((DataState<List<SupplierOrderWithLinesDataModel>>) -> Unit)? = null
) {
    val requestUserScope = currentSupplierNetworkUserScope()
    if (requestUserScope.isBlank()) return
    GlobalScope.launch(Dispatchers.ourIo) {
        val result = supplierOrderReadCoordinator.run("$requestUserScope|my") {
            supplierOrderOperationMutex.withLock {
                val response = networkRequest<List<SupplierOrderWithLinesDataModel>, Unit>(
                    method = HttpMethod.Get,
                    endpointUrl = globalAppConfigurationState.payloadValue.getSupplierOrdersPath.first
                )
                applySupplierOrderReadResponse(
                    response = response,
                    scope = SupplierOrderReadScope(replaceAll = true),
                    expectedUserScope = requestUserScope
                )
            }
        }
        if (supplierNetworkUserScopeIsCurrent(requestUserScope)) onCompleted?.invoke(result)
    }
}

private data class SupplierOrderReadScope(
    val storeId: String = "",
    val supplierId: String = "",
    val replaceAll: Boolean = false
) {
    private val normalizedStoreId: String = storeId.trim().lowercase()
    private val normalizedSupplierId: String = supplierId.trim().lowercase()

    fun contains(order: SupplierOrderDataModel): Boolean {
        if (replaceAll) return true
        if (normalizedStoreId.isBlank() && normalizedSupplierId.isBlank()) return false

        val storeMatches = normalizedStoreId.isBlank() ||
            order.storeId.trim().lowercase() == normalizedStoreId
        val supplierMatches = normalizedSupplierId.isBlank() ||
            order.supplierId.trim().lowercase() == normalizedSupplierId
        return storeMatches && supplierMatches
    }
}

private fun List<SupplierOrderLineDataModel>.distinctSupplierOrderLinesByStableId(): List<SupplierOrderLineDataModel> {
    val seenIds = mutableSetOf<String>()
    return filter { line ->
        val cleanId = line.id.trim().lowercase()
        // A proper server row always has an ID. Preserve every malformed legacy row without an ID
        // instead of accidentally merging two genuinely separate goods lines by a weak fallback key.
        cleanId.isBlank() || seenIds.add(cleanId)
    }
}

private suspend fun applySupplierOrderReadResponse(
    response: ResponseDataModel<List<SupplierOrderWithLinesDataModel>>,
    scope: SupplierOrderReadScope,
    expectedUserScope: String
): DataState<List<SupplierOrderWithLinesDataModel>> {
    if (!supplierNetworkUserScopeIsCurrent(expectedUserScope)) {
        return DataState.Empty()
    }
    if (response.negative || response.payload == null) {
        postInAppNotification(response.message, NotificationType.Negative)
        return DataState.Empty(response.message)
    }

    // A scoped Store/Supplier read replaces only that commercial scope. Otherwise opening one Store
    // could erase another Store's cached orders or the Supplier workspace while both modes share the
    // same state flows. The unscoped "my Supplier side" read remains authoritative for its payload.
    val scopedBundles = response.payload
        .filter { bundle -> scope.contains(bundle.order) }
        .distinctBy { bundle -> bundle.order.id.trim().lowercase() }
    val incomingOrders = scopedBundles.map { it.order }
    val incomingLines = scopedBundles
        .flatMap { it.lines }
        .distinctSupplierOrderLinesByStableId()

    if (scope.replaceAll) {
        emitSupplierOrderSnapshotAndAwait(
            orders = incomingOrders,
            lines = incomingLines,
            message = response.message
        )
    } else {
        val existingOrders = supplierOrdersState.payloadValue.orEmpty()
        val replacedOrderIds = (
            existingOrders.asSequence().filter(scope::contains).map { it.id } +
                incomingOrders.asSequence().map { it.id }
            )
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .toSet()
        val nextOrders = (
            existingOrders.filterNot(scope::contains) + incomingOrders
            ).distinctBy { order -> order.id.trim().lowercase() }
        val nextLines = (
            supplierOrderLinesState.payloadValue.orEmpty().filterNot { line ->
                line.orderId.trim().lowercase() in replacedOrderIds
            } + incomingLines
            ).distinctSupplierOrderLinesByStableId()
        emitSupplierOrderSnapshotAndAwait(
            orders = nextOrders,
            lines = nextLines,
            message = response.message
        )
    }

    return DataState.Success(scopedBundles, response.message)
}

fun getSupplierModeDashboard(
    force: Boolean = false,
    supplierId: String? = effectiveActiveSupplierProfileId(),
    publishToSharedState: Boolean = true,
    onCompleted: ((DataState<SupplierModeDashboardDataModel>) -> Unit)? = null
) {
    val cleanSupplierId = normalizeSupplierProfileIdentityId(supplierId)
    val currentUserScope = currentSupplierNetworkUserScope()
    val scopeKey = "$currentUserScope|${cleanSupplierId ?: SUPPLIER_DASHBOARD_SCOPE_ALL}"

    if (currentUserScope.isBlank()) return

    GlobalScope.launch(Dispatchers.ourIo) {
        if (!supplierNetworkUserScopeIsCurrent(currentUserScope)) return@launch
        var cachedDashboard: SupplierModeDashboardDataModel? = null
        var cachedAtMillis = 0L

        // Scope switching is serialized with result publication. A late response for Supplier A
        // therefore cannot flash over Supplier B after the user changes the active identity.
        var scopeStillCurrent = true
        supplierDashboardCacheMutex.withLock {
            if (!supplierNetworkUserScopeIsCurrent(currentUserScope)) {
                scopeStillCurrent = false
                return@withLock
            }
            if (publishToSharedState) supplierDashboardVisibleScopeKey = scopeKey
            cachedDashboard = supplierDashboardCacheByScope[scopeKey]
            cachedAtMillis = supplierDashboardLastSuccessAtMillisByScope[scopeKey] ?: 0L
            val cached = cachedDashboard
            if (publishToSharedState) {
                if (cached != null) {
                    emitSupplierDashboardAndAwait(DataState.Success(cached))
                } else {
                    supplierModeDashboardState.emit(DataState.Empty())
                    supplierModeDashboardState.payload.first { current -> current == null }
                }
            }
        }
        if (!scopeStillCurrent) return@launch

        val now = getCurrentTimeMillis()
        if (
            !force &&
            cachedDashboard != null &&
            cachedAtMillis > 0L &&
            now >= cachedAtMillis &&
            now - cachedAtMillis < SUPPLIER_DASHBOARD_RECENT_SUCCESS_WINDOW_MILLIS
        ) {
            if (supplierNetworkUserScopeIsCurrent(currentUserScope)) {
                onCompleted?.invoke(DataState.Success(cachedDashboard!!))
            }
            return@launch
        }

        val result = supplierDashboardReadCoordinator.run(scopeKey) {
            val response = networkRequest<SupplierModeDashboardDataModel, Unit>(
                method = HttpMethod.Get,
                endpointUrl = globalAppConfigurationState.payloadValue.getSupplierDashboardPath.first,
                headers = buildMap {
                    cleanSupplierId?.let { put("supplier_id", it) }
                }
            )

            if (!supplierNetworkUserScopeIsCurrent(currentUserScope)) {
                DataState.Empty()
            } else if (response.negative || response.payload == null) {
                DataState.Empty(response.message)
            } else {
                val state = DataState.Success(response.payload, response.message)
                supplierDashboardCacheMutex.withLock {
                    if (supplierNetworkUserScopeIsCurrent(currentUserScope)) {
                        supplierDashboardCacheByScope[scopeKey] = response.payload
                        supplierDashboardLastSuccessAtMillisByScope[scopeKey] = getCurrentTimeMillis()
                    }
                }
                state
            }
        }

        // Publication is caller-specific and therefore happens after the single-flight coordinator.
        // If a background/non-publishing consumer started the shared request first, a visible caller
        // joining that same request still needs to publish the result into the shared dashboard state.
        if (
            publishToSharedState &&
            result is DataState.Success &&
            supplierNetworkUserScopeIsCurrent(currentUserScope)
        ) {
            supplierDashboardCacheMutex.withLock {
                if (
                    supplierNetworkUserScopeIsCurrent(currentUserScope) &&
                    supplierDashboardVisibleScopeKey == scopeKey
                ) {
                    emitSupplierDashboardAndAwait(result)
                }
            }
        }
        if (supplierNetworkUserScopeIsCurrent(currentUserScope)) onCompleted?.invoke(result)
    }
}

fun refreshSupplierModeWorkspace(
    includeContracts: Boolean = false,
    force: Boolean = false
) {
    GlobalScope.launch(Dispatchers.ourIo) {
        supplierWorkspaceRefreshScheduleMutex.withLock {
            val currentUserId = userAccountState.payloadValue?.id.orEmpty().trim()
            val currentUserScope = currentSupplierNetworkUserScope()
            if (currentUserId.isBlank() || currentUserScope.isBlank()) return@withLock
            val focusedSupplierId = effectiveActiveSupplierProfileId()
            val refreshScopeKey = buildString {
                append(currentUserScope)
                append('|')
                append(normalizeSupplierProfileIdentityId(focusedSupplierId) ?: SUPPLIER_DASHBOARD_SCOPE_ALL)
            }
            if (supplierWorkspaceRefreshScopeKey != refreshScopeKey) {
                supplierWorkspaceRefreshScopeKey = refreshScopeKey
                supplierWorkspaceLastBaseRefreshAtMillis = 0L
                supplierWorkspaceLastContractsRefreshAtMillis = 0L
            }

            val nowMillis = getCurrentTimeMillis()
            val decision = supplierWorkspaceRefreshDecision(
                nowMillis = nowMillis,
                lastBaseRefreshAtMillis = supplierWorkspaceLastBaseRefreshAtMillis,
                lastContractsRefreshAtMillis = supplierWorkspaceLastContractsRefreshAtMillis,
                includeContracts = includeContracts,
                force = force
            )

            if (decision.refreshBaseWorkspace) {
                supplierWorkspaceLastBaseRefreshAtMillis = nowMillis
                getSuppliers()
                if (focusedSupplierId.isNullOrBlank()) {
                    getMySupplierSideOrders()
                } else {
                    getSupplierOrdersForSupplier(focusedSupplierId)
                }
                getMySupplierGoodsPrices(focusedSupplierId)
                getSupplierModeDashboard(force = force, supplierId = focusedSupplierId)
            }

            if (decision.refreshContracts) {
                supplierWorkspaceLastContractsRefreshAtMillis = nowMillis
                getSupplierContracts(supplierId = focusedSupplierId)
            }
        }
    }
}

private fun refreshSupplierModeWorkspaceIfActive(includeContracts: Boolean = false) {
    if (appModeState.value == APP_MODE_SUPPLIER || appModeState.value == APP_MODE_MANUFACTURER) {
        refreshSupplierModeWorkspace(includeContracts = includeContracts, force = true)
    }
}

fun addSupplierOrder(
    orderWithLines: SupplierOrderWithLinesDataModel,
    onCompleted: ((DataState<SupplierOrderWithLinesDataModel>) -> Unit)? = null
) {
    GlobalScope.launch(Dispatchers.ourIo) {
        supplierOrderOperationMutex.withLock {
            val response = networkRequest<SupplierOrderWithLinesDataModel, SupplierOrderWithLinesDataModel>(
                method = HttpMethod.Post,
                endpointUrl = globalAppConfigurationState.payloadValue.addSupplierOrderPath.first,
                body = orderWithLines
            )

            if (response.negative || response.payload == null) {
                postInAppNotification(response.message, NotificationType.Negative)
                onCompleted?.invoke(DataState.Empty(response.message))
            } else {
                emitSupplierOrderSnapshotAndAwait(
                    orders = supplierOrdersState.payloadValue.orEmpty().upsertById(response.payload.order),
                    lines = supplierOrderLinesState.payloadValue.orEmpty()
                        .filterNot { line ->
                            line.orderId.trim().equals(response.payload.order.id.trim(), ignoreCase = true)
                        } + response.payload.lines,
                    message = response.message
                )

                getSupplierModeDashboard(force = true)
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
    GlobalScope.launch(Dispatchers.ourIo) {
        supplierOrderOperationMutex.withLock {
            val response = networkRequest<SupplierOrderWithLinesDataModel, SupplierOrderWithLinesDataModel>(
                method = HttpMethod.Put,
                endpointUrl = globalAppConfigurationState.payloadValue.updateSupplierOrderPath.first,
                body = orderWithLines
            )

            if (response.negative || response.payload == null) {
                postInAppNotification(response.message, NotificationType.Negative)
                onCompleted?.invoke(DataState.Empty(response.message))
            } else {
                emitSupplierOrderSnapshotAndAwait(
                    orders = supplierOrdersState.payloadValue.orEmpty().upsertById(response.payload.order),
                    lines = supplierOrderLinesState.payloadValue.orEmpty()
                        .filterNot { line ->
                            line.orderId.trim().equals(response.payload.order.id.trim(), ignoreCase = true)
                        } + response.payload.lines,
                    message = response.message
                )
                getSupplierModeDashboard(force = true)
                postInAppNotification(response.message, NotificationType.Positive)
                onCompleted?.invoke(DataState.Success(response.payload, response.message))
            }
        }
    }
}

private suspend fun emitSupplierOrderBundlesFromServerResponse(
    bundles: List<SupplierOrderWithLinesDataModel>,
    message: List<LocalizedStringDataModel>?
) {
    val changedOrderIds = bundles
        .map { it.order.id.trim().lowercase() }
        .filter { it.isNotBlank() }
        .toSet()
    emitSupplierOrderSnapshotAndAwait(
        orders = supplierOrdersState.payloadValue.orEmpty()
            .filterNot { it.id.trim().lowercase() in changedOrderIds } + bundles.map { it.order },
        lines = supplierOrderLinesState.payloadValue.orEmpty()
            .filterNot { line -> line.orderId.trim().lowercase() in changedOrderIds } + bundles.flatMap { it.lines },
        message = message
    )
}

data class SupplierOrderStatusUpdateOutcome(
    val requestedOrderIds: List<String> = emptyList(),
    val updatedOrderIds: List<String> = emptyList(),
    val message: List<LocalizedStringDataModel>? = null,
    val negative: Boolean = false
) {
    val skippedOrderIds: List<String>
        get() {
            val updated = updatedOrderIds.map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
            return requestedOrderIds.filter { it.trim().lowercase() !in updated }
        }

    val requestedCount: Int get() = requestedOrderIds.size
    val updatedCount: Int get() = updatedOrderIds.size
    val skippedCount: Int get() = skippedOrderIds.size
    val partial: Boolean get() = !negative && updatedCount > 0 && skippedCount > 0
}

private fun supplierOrderStatusPartialMessage(
    updatedCount: Int,
    requestedCount: Int
): List<LocalizedStringDataModel> = localizedStringResourceMessage(
    id = 2443,
    main = "Updated $updatedCount of $requestedCount orders. Review the remaining orders before continuing.",
    ru = "Обновлено заказов: $updatedCount из $requestedCount. Проверьте оставшиеся заказы перед продолжением.",
    kk = "$requestedCount тапсырыстың $updatedCount жаңартылды. Жалғастырмас бұрын қалған тапсырыстарды тексеріңіз."
)

suspend fun updateSupplierOrdersSupplierStatusByIdsAwait(
    orderIds: List<String>,
    status: SupplierOrderStatusDataModel,
    comment: String? = null
): SupplierOrderStatusUpdateOutcome {
    val cleanOrderIds = orderIds
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase() }

    if (cleanOrderIds.isEmpty()) {
        return SupplierOrderStatusUpdateOutcome(
            requestedOrderIds = emptyList(),
            message = localizedStringResourceMessage(
                id = 2444,
                main = "No supplier orders were selected",
                ru = "Заказы поставщику не выбраны",
                kk = "Жеткізуші тапсырыстары таңдалмады"
            ),
            negative = true
        )
    }

    return supplierOrderOperationMutex.withLock {
        try {
            val response = networkRequest<List<SupplierOrderWithLinesDataModel>, SupplierOrderStatusUpdateRequestDataModel>(
                method = HttpMethod.Put,
                endpointUrl = globalAppConfigurationState.payloadValue.updateSupplierOrdersStatusPath.first,
                body = SupplierOrderStatusUpdateRequestDataModel(
                    orderIds = cleanOrderIds,
                    status = status,
                    comment = comment?.trim()?.takeIf { it.isNotBlank() }
                )
            )

            if (response.negative || response.payload == null) {
                SupplierOrderStatusUpdateOutcome(
                    requestedOrderIds = cleanOrderIds,
                    updatedOrderIds = emptyList(),
                    message = response.message ?: localizedStringResourceMessage(
                        id = 1575,
                        main = "Could not update supplier dispatch lane",
                        ru = "Не удалось обновить маршрут поставщика",
                        kk = "Жеткізуші жеткізу бағытын жаңарту мүмкін болмады"
                    ),
                    negative = true
                )
            } else {
                val requestedIdsByKey = cleanOrderIds.associateBy { it.lowercase() }
                val updatedBundles = response.payload
                    .filter { bundle -> bundle.order.id.trim().lowercase() in requestedIdsByKey }
                    .distinctBy { bundle -> bundle.order.id.trim().lowercase() }
                if (updatedBundles.isEmpty()) {
                    SupplierOrderStatusUpdateOutcome(
                        requestedOrderIds = cleanOrderIds,
                        updatedOrderIds = emptyList(),
                        message = response.message ?: localizedStringResourceMessage(
                            id = 1575,
                            main = "Could not update supplier dispatch lane",
                            ru = "Не удалось обновить маршрут поставщика",
                            kk = "Жеткізуші жеткізу бағытын жаңарту мүмкін болмады"
                        ),
                        negative = true
                    )
                } else {
                    emitSupplierOrderBundlesFromServerResponse(updatedBundles, response.message)
                    getSupplierModeDashboard(force = true)

                    val updatedIds = updatedBundles.mapNotNull { bundle ->
                        requestedIdsByKey[bundle.order.id.trim().lowercase()]
                    }
                    val partial = updatedIds.size < cleanOrderIds.size
                    SupplierOrderStatusUpdateOutcome(
                        requestedOrderIds = cleanOrderIds,
                        updatedOrderIds = updatedIds,
                        message = when {
                            // The client knows the complete selection, including any IDs beyond a
                            // server-side batch cap. Prefer that exact count over a generic success
                            // response so a partial bulk action is never presented as complete.
                            partial -> supplierOrderStatusPartialMessage(updatedIds.size, cleanOrderIds.size)
                            else -> response.message ?: localizedStringResourceMessage(
                                id = 1574,
                                main = "Supplier dispatch lane updated",
                                ru = "Маршрут поставщика обновлён",
                                kk = "Жеткізуші жеткізу бағыты жаңартылды"
                            )
                        },
                        negative = false
                    )
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (throwable: Throwable) {
            println("AITA supplier dispatch: status update failed: ${throwable.message ?: throwable::class.simpleName}")
            SupplierOrderStatusUpdateOutcome(
                requestedOrderIds = cleanOrderIds,
                updatedOrderIds = emptyList(),
                message = localizedStringResourceMessage(
                    id = 2445,
                    main = "Could not update the selected delivery orders",
                    ru = "Не удалось обновить выбранные заказы доставки",
                    kk = "Таңдалған жеткізу тапсырыстарын жаңарту мүмкін болмады"
                ),
                negative = true
            )
        }
    }
}

fun updateSupplierOrdersSupplierStatus(
    orderBundles: List<SupplierOrderWithLinesDataModel>,
    status: SupplierOrderStatusDataModel,
    comment: String? = null,
    onCompleted: ((Int) -> Unit)? = null,
    onOutcome: ((SupplierOrderStatusUpdateOutcome) -> Unit)? = null
) {
    updateSupplierOrdersSupplierStatusByIds(
        orderIds = orderBundles.map { it.order.id },
        status = status,
        comment = comment,
        onCompleted = onCompleted,
        onOutcome = onOutcome
    )
}

fun updateSupplierOrdersSupplierStatusByIds(
    orderIds: List<String>,
    status: SupplierOrderStatusDataModel,
    comment: String? = null,
    onCompleted: ((Int) -> Unit)? = null,
    onOutcome: ((SupplierOrderStatusUpdateOutcome) -> Unit)? = null
) {
    GlobalScope.launch(Dispatchers.ourIo) {
        val outcome = updateSupplierOrdersSupplierStatusByIdsAwait(
            orderIds = orderIds,
            status = status,
            comment = comment
        )
        val notificationType = when {
            outcome.negative -> NotificationType.Negative
            outcome.partial -> NotificationType.Neutral
            else -> NotificationType.Positive
        }
        postInAppNotification(
            outcome.message,
            notificationType,
            transient = !outcome.negative
        )
        onCompleted?.invoke(outcome.updatedCount)
        onOutcome?.invoke(outcome)
    }
}

fun deleteSupplierOrder(
    orderId: String,
    onCompleted: ((DataState<String>) -> Unit)? = null
) {
    GlobalScope.launch(Dispatchers.ourIo) {
        supplierOrderOperationMutex.withLock {
            val response = networkRequest<String, String>(
                method = HttpMethod.Delete,
                endpointUrl = globalAppConfigurationState.payloadValue.deleteSupplierOrdersPath.first,
                body = orderId
            )

            if (response.negative) {
                postInAppNotification(response.message, NotificationType.Negative)
                onCompleted?.invoke(DataState.Empty(response.message))
            } else {
                emitSupplierOrderSnapshotAndAwait(
                    orders = supplierOrdersState.payloadValue.orEmpty().filterNot {
                        it.id.trim().equals(orderId.trim(), ignoreCase = true)
                    },
                    lines = supplierOrderLinesState.payloadValue.orEmpty().filterNot {
                        it.orderId.trim().equals(orderId.trim(), ignoreCase = true)
                    },
                    message = response.message
                )
                getSupplierModeDashboard(force = true)
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
    GlobalScope.launch(Dispatchers.ourIo) {
        supplierOrderOperationMutex.withLock {
            val response = networkRequest<SupplierOrderWithLinesDataModel, ReceiveSupplierOrderRequestDataModel>(
                method = HttpMethod.Post,
                endpointUrl = globalAppConfigurationState.payloadValue.receiveSupplierOrderPath.first,
                body = request
            )

            if (response.negative || response.payload == null) {
                postInAppNotification(response.message, NotificationType.Negative)
                onCompleted?.invoke(DataState.Empty(response.message))
            } else {
                emitSupplierOrderSnapshotAndAwait(
                    orders = supplierOrdersState.payloadValue.orEmpty().upsertById(response.payload.order),
                    lines = supplierOrderLinesState.payloadValue.orEmpty()
                        .filterNot { line ->
                            line.orderId.trim().equals(response.payload.order.id.trim(), ignoreCase = true)
                        } + response.payload.lines,
                    message = response.message
                )
                response.payload.order.storeId.takeIf { it.isNotBlank() }?.let { getStockBatches(it) }
                getSupplierModeDashboard(force = true)
                postInAppNotification(response.message, NotificationType.Positive)
                onCompleted?.invoke(DataState.Success(response.payload, response.message))
            }
        }
    }
}


fun getSupplierContracts(
    storeId: String? = null,
    supplierId: String? = null,
    onCompleted: ((DataState<List<SupplierPartnershipContractDataModel>>) -> Unit)? = null
) {
    val cleanStoreId = storeId.orEmpty().trim().lowercase()
    val cleanSupplierId = supplierId.orEmpty().trim().lowercase()
    val requestUserScope = currentSupplierNetworkUserScope()
    if (requestUserScope.isBlank()) return
    val requestKey = "$requestUserScope|store=$cleanStoreId|supplier=$cleanSupplierId"
    GlobalScope.launch(Dispatchers.ourIo) {
        val result = try {
            supplierContractReadCoordinator.run(requestKey) {
                supplierContractOperationMutex.withLock {
                    val headers = buildMap {
                        cleanStoreId.takeIf { it.isNotBlank() }?.let { put("store_id", it) }
                        cleanSupplierId.takeIf { it.isNotBlank() }?.let { put("supplier_id", it) }
                    }
                    val response = networkRequest<List<SupplierPartnershipContractDataModel>, Unit>(
                        method = HttpMethod.Get,
                        endpointUrl = globalAppConfigurationState.payloadValue.getSupplierContractsPath.first,
                        headers = headers
                    )

                    if (!supplierNetworkUserScopeIsCurrent(requestUserScope)) {
                        DataState.Empty()
                    } else if (response.negative || response.payload == null) {
                        postInAppNotification(response.message, NotificationType.Negative)
                        DataState.Empty(response.message)
                    } else {
                        val mergedContracts = supplierPartnershipContractsState.payloadValue
                            .orEmpty()
                            .mergedWithSupplierContractRead(
                                incoming = response.payload,
                                storeId = cleanStoreId,
                                supplierId = cleanSupplierId
                            )
                        DataState.Success(mergedContracts, response.message).also { state ->
                            emitSupplierContractsAndAwait(state)
                        }
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (throwable: Throwable) {
            if (!supplierNetworkUserScopeIsCurrent(requestUserScope)) {
                return@launch
            }
            logSupplierContractDiagnostic("Supplier contracts read failed: ${throwable.message ?: throwable::class.simpleName}")
            val message = localizedStringResourceMessage(
                id = 2408,
                main = "Could not load supplier contracts",
                ru = "Не удалось загрузить договоры с поставщиками",
                kk = "Жеткізуші келісімдерін жүктеу мүмкін болмады"
            )
            postInAppNotification(message, NotificationType.Negative)
            DataState.Empty(message)
        }
        if (supplierNetworkUserScopeIsCurrent(requestUserScope)) onCompleted?.invoke(result)
    }
}

fun upsertSupplierContract(
    contract: SupplierPartnershipContractDataModel,
    onCompleted: ((DataState<SupplierPartnershipContractDataModel>) -> Unit)? = null
) {
    GlobalScope.launch(Dispatchers.ourIo) {
        val result = try {
            supplierContractOperationMutex.withLock {
                val response = networkRequest<SupplierPartnershipContractDataModel, SupplierPartnershipContractDataModel>(
                    method = HttpMethod.Post,
                    endpointUrl = globalAppConfigurationState.payloadValue.upsertSupplierContractPath.first,
                    body = contract
                )

                if (response.negative || response.payload == null) {
                    postInAppNotification(response.message, NotificationType.Negative)
                    DataState.Empty(response.message)
                } else {
                    emitSupplierContractsAndAwait(
                        DataState.Success(
                            supplierPartnershipContractsState.payloadValue.orEmpty()
                                .upsertSupplierContractByRevision(response.payload),
                            response.message
                        )
                    )
                    refreshSupplierDashboardAfterContractMutationIfNeeded()
                    postInAppNotification(response.message, NotificationType.Positive)
                    DataState.Success(response.payload, response.message)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (throwable: Throwable) {
            logSupplierContractDiagnostic("Supplier contract save failed: ${throwable.message ?: throwable::class.simpleName}")
            val message = localizedStringResourceMessage(
                id = 2409,
                main = "Could not save supplier contract",
                ru = "Не удалось сохранить договор с поставщиком",
                kk = "Жеткізуші келісімін сақтау мүмкін болмады"
            )
            postInAppNotification(message, NotificationType.Negative)
            DataState.Empty(message)
        }
        onCompleted?.invoke(result)
    }
}

fun acceptSupplierContract(
    contractId: String,
    revision: Int,
    onCompleted: ((DataState<SupplierPartnershipContractDataModel>) -> Unit)? = null
) {
    mutateSupplierContract(
        contractId = contractId,
        revision = revision,
        endpointUrl = globalAppConfigurationState.payloadValue.acceptSupplierContractPath.first,
        diagnosticAction = "accept",
        onCompleted = onCompleted
    )
}

fun declineSupplierContract(
    contractId: String,
    revision: Int,
    onCompleted: ((DataState<SupplierPartnershipContractDataModel>) -> Unit)? = null
) {
    mutateSupplierContract(
        contractId = contractId,
        revision = revision,
        endpointUrl = globalAppConfigurationState.payloadValue.declineSupplierContractPath.first,
        diagnosticAction = "decline",
        onCompleted = onCompleted
    )
}

private fun mutateSupplierContract(
    contractId: String,
    revision: Int,
    endpointUrl: String,
    diagnosticAction: String,
    onCompleted: ((DataState<SupplierPartnershipContractDataModel>) -> Unit)?
) {
    val cleanContractId = contractId.trim()
    if (cleanContractId.isBlank() || revision <= 0) {
        val message = localizedStringResourceMessage(
            id = 2410,
            main = "Supplier contract is missing",
            ru = "Договор с поставщиком не найден",
            kk = "Жеткізуші келісімі табылмады"
        )
        onCompleted?.invoke(DataState.Empty(message))
        return
    }

    GlobalScope.launch(Dispatchers.ourIo) {
        val result = try {
            supplierContractOperationMutex.withLock {
                val response = networkRequest<
                    SupplierPartnershipContractDataModel,
                    SupplierContractRevisionActionRequestDataModel
                    >(
                    method = HttpMethod.Post,
                    endpointUrl = endpointUrl,
                    body = SupplierContractRevisionActionRequestDataModel(
                        contractId = cleanContractId,
                        revision = revision
                    )
                )

                if (response.negative || response.payload == null) {
                    postInAppNotification(response.message, NotificationType.Negative)
                    DataState.Empty(response.message)
                } else {
                    emitSupplierContractsAndAwait(
                        DataState.Success(
                            supplierPartnershipContractsState.payloadValue.orEmpty()
                                .upsertSupplierContractByRevision(response.payload),
                            response.message
                        )
                    )
                    refreshSupplierDashboardAfterContractMutationIfNeeded()
                    postInAppNotification(response.message, NotificationType.Positive)
                    DataState.Success(response.payload, response.message)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (throwable: Throwable) {
            logSupplierContractDiagnostic("Supplier contract $diagnosticAction failed: ${throwable.message ?: throwable::class.simpleName}")
            val message = localizedStringResourceMessage(
                id = 2411,
                main = "Could not update supplier contract",
                ru = "Не удалось обновить договор с поставщиком",
                kk = "Жеткізуші келісімін жаңарту мүмкін болмады"
            )
            postInAppNotification(message, NotificationType.Negative)
            DataState.Empty(message)
        }
        onCompleted?.invoke(result)
    }
}

fun archiveSupplierContract(
    contractId: String,
    revision: Int,
    onCompleted: ((DataState<String>) -> Unit)? = null
) {
    val cleanContractId = contractId.trim()
    if (cleanContractId.isBlank() || revision <= 0) {
        val message = localizedStringResourceMessage(
            id = 2410,
            main = "Supplier contract is missing",
            ru = "Договор с поставщиком не найден",
            kk = "Жеткізуші келісімі табылмады"
        )
        onCompleted?.invoke(DataState.Empty(message))
        return
    }

    GlobalScope.launch(Dispatchers.ourIo) {
        val result = try {
            supplierContractOperationMutex.withLock {
                val response = networkRequest<String, SupplierContractRevisionActionRequestDataModel>(
                    method = HttpMethod.Post,
                    endpointUrl = globalAppConfigurationState.payloadValue.archiveSupplierContractPath.first,
                    body = SupplierContractRevisionActionRequestDataModel(
                        contractId = cleanContractId,
                        revision = revision
                    )
                )

                if (response.negative || response.payload == null) {
                    postInAppNotification(response.message, NotificationType.Negative)
                    DataState.Empty(response.message)
                } else {
                    val archivedId = response.payload.trim()
                    emitSupplierContractsAndAwait(
                        DataState.Success(
                            supplierPartnershipContractsState.payloadValue.orEmpty().filterNot { contract ->
                                contract.id.trim().equals(archivedId, ignoreCase = true)
                            },
                            response.message
                        )
                    )
                    refreshSupplierDashboardAfterContractMutationIfNeeded()
                    postInAppNotification(response.message, NotificationType.Positive)
                    DataState.Success(response.payload, response.message)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (throwable: Throwable) {
            logSupplierContractDiagnostic("Supplier contract archive failed: ${throwable.message ?: throwable::class.simpleName}")
            val message = localizedStringResourceMessage(
                id = 2411,
                main = "Could not update supplier contract",
                ru = "Не удалось обновить договор с поставщиком",
                kk = "Жеткізуші келісімін жаңарту мүмкін болмады"
            )
            postInAppNotification(message, NotificationType.Negative)
            DataState.Empty(message)
        }
        onCompleted?.invoke(result)
    }
}

private fun <T> List<T>.upsertById(
    item: T,
    idOf: (T) -> String = {
        when (it) {
            is SupplierGoodsPriceDataModel -> it.id
            is SupplierOrderDataModel -> it.id
            is SupplierOrderLineDataModel -> it.id
            is SupplierPartnershipContractDataModel -> it.id
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

private const val DEFAULT_AITA_SERVER_URL = "https://aita-api.bogdan-donduk.workers.dev"
private const val CANONICAL_AITA_PUBLIC_SERVER_HOST = "aita-api.bogdan-donduk.workers.dev"
private const val DEFAULT_AITA_FALLBACK_SERVER_URLS = ""
private const val DEFAULT_AITA_BOOTSTRAP_URLS = ""
private const val AITA_BOOTSTRAP_SERVER_URL_REFRESH_INTERVAL_MILLIS = 300_000L
private const val AITA_BOOTSTRAP_SERVER_URL_FAILURE_BACKOFF_MILLIS = 45_000L
private const val AITA_BOOTSTRAP_SERVER_URL_CACHE_MAX_AGE_MILLIS = 1_209_600_000L
private const val AITA_LAST_KNOWN_GOOD_SERVER_URL_CACHE_MAX_AGE_MILLIS = 2_592_000_000L
private const val AITA_NON_REPLAYABLE_MUTATION_PROOF_MAX_AGE_MILLIS = 30_000L
private const val AITA_BOOTSTRAP_HTTP_TIMEOUT_MILLIS = 8_000L
private val DEFAULT_AITA_SERVER_URL_PAIR = Pair(DEFAULT_AITA_SERVER_URL, "1")
@Volatile
private var runtimeClientServerUrlOverride: String? = null
@Volatile
private var runtimeClientBootstrapUrlsOverride: List<String>? = null
@Volatile
private var bootstrapServerUrlCandidatesMemory: List<String> = emptyList()
@Volatile
private var bootstrapServerUrlFetchedAtMillis: Long = 0L
@Volatile
private var bootstrapServerUrlLastFailureAtMillis: Long = 0L
private val bootstrapServerUrlMutex = Mutex()
@Volatile
private var lastKnownGoodServerUrlMemory: String? = null
@Volatile
private var lastKnownGoodServerUrlVerifiedAtMillis: Long = 0L
@Volatile
private var lastKnownGoodServerUrlPersistedAtMillis: Long = 0L
private val lastKnownGoodServerUrlMutex = Mutex()
@Volatile
private var currentNetworkRequestCandidateServerUrlsMemory: List<String> = emptyList()

// CommonMain.kt and server/config/app/global.json remain the visible source of truth. Automatic
// production discovery is locked to one workers.dev gateway. Every non-canonical public value from
// old caches, global.json files, bootstrap payloads, or environment variables is discarded before networking.
@Volatile
private var clientVisibleServerUrlFilesOnly: Boolean = true

val GlobalScope = CoroutineScope(SupervisorJob())

const val APP_MODE_STORE = 0
const val APP_MODE_BUYER = 1
const val APP_MODE_SUPPLIER = 2
const val APP_MODE_MANUFACTURER = 3

/**
 * Buyer is a read-only shop-window preview; Store and Supplier remain the business workspaces.
 * Manufacturer remains hidden until its own end-to-end workflow is implemented.
 */
const val APP_MODE_SELECTION_PUBLICLY_ENABLED = true

private fun normalizeAppModePreference(modeId: Int?): Int {
    if (!APP_MODE_SELECTION_PUBLICLY_ENABLED) return APP_MODE_STORE
    return when (modeId) {
        APP_MODE_SUPPLIER -> APP_MODE_SUPPLIER
        APP_MODE_BUYER -> APP_MODE_BUYER
        else -> APP_MODE_STORE
    }
}

val appModeState = MutableStateFlow(DEFAULT_NEW_ACCOUNT_APP_MODE)

private val appInitializationStartedState = MutableStateFlow(false)

internal fun beginAitaInitializationOnce(): Boolean =
    appInitializationStartedState.compareAndSet(expect = false, update = true)
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
        getStockItemHistoryPath = Pair("stock/history/get", "1330"),
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
        getMySupplierGoodsPricesPath = Pair("supplierGoodsPrices/my", "1685"),
        upsertSupplierGoodsPricePath = Pair("supplierGoodsPrices/upsert", "32"),
        deleteSupplierGoodsPricesPath = Pair("supplierGoodsPrices/delete", "33"),
        getSupplierOrdersPath = Pair("supplierOrders/get", "34"),
        addSupplierOrderPath = Pair("supplierOrders/add", "35"),
        updateSupplierOrderPath = Pair("supplierOrders/update", "36"),
        updateSupplierOrdersStatusPath = Pair("supplierOrders/status", "1751"),
        deleteSupplierOrdersPath = Pair("supplierOrders/delete", "37"),
        receiveSupplierOrderPath = Pair("supplierOrders/receive", "38"),
        getSupplierDashboardPath = Pair("supplierOrders/dashboard", "1616"),
        getSupplierContractsPath = Pair("supplierContracts/get", "1479"),
        upsertSupplierContractPath = Pair("supplierContracts/upsert", "1480"),
        acceptSupplierContractPath = Pair("supplierContracts/accept", "1481"),
        declineSupplierContractPath = Pair("supplierContracts/decline", "1482"),
        archiveSupplierContractPath = Pair("supplierContracts/archive", "1483"),
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
                    ),
                    LocalizedStringDataModel("ky", "Казакстан")
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
                            ),
                            LocalizedStringDataModel("ky", "теңге")
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
                            ),
                            LocalizedStringDataModel("ky", "Астана")
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
                    ),
                    LocalizedStringDataModel("ky", "Англисче")
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
                    ),
                    LocalizedStringDataModel("ky", "Орусча")
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
                    ),
                    LocalizedStringDataModel("ky", "Казакча")
                ),
                "png/flag_kz.png"
            )
        ),
        themes = availableAppThemes(emptyList()),
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
                    ),
                    LocalizedStringDataModel("ky", "даана")
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
                    ),
                    LocalizedStringDataModel("ky", "кг")
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
    eventMessage("message.authentication_failed")
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
val stringSessionTimeExpiredLoggingOutState = MutableStateFlow("Cloud sign-in expired")
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
val drawablePathIconPromosState = MutableStateFlow("svg/147_0.svg")
val drawablePathIconAnalyticsState = MutableStateFlow("svg/21_0.svg")
val drawablePathIconAnalyticsReportState = MutableStateFlow("svg/62_0.svg")
val drawablePathIconLabelPrinterState = MutableStateFlow("svg/63_0.svg")
val drawablePathIconBarcodeGenerateState = MutableStateFlow("svg/64_0.svg")
val drawablePathIconPrintTagState = MutableStateFlow("svg/65_0.svg")
val drawablePathIconWorkerRoleTemplatesState = MutableStateFlow("svg/66_0.svg")
val drawablePathIconStockHistoryState = MutableStateFlow("svg/67_0.svg")
val drawablePathIconAppModeStoreState = MutableStateFlow("svg/68_0.svg")
val drawablePathIconAppModeBuyerState = MutableStateFlow("svg/69_0.svg")
val drawablePathIconAppModeSupplierState = MutableStateFlow("svg/70_0.svg")
val drawablePathIconAppModeManufacturerState = MutableStateFlow("svg/71_0.svg")
val drawablePathIconSupplierCatalogState = MutableStateFlow("svg/72_0.svg")
val drawablePathIconSupplierContractsState = MutableStateFlow("svg/76_0.svg")
val drawablePathIconSupplierPartnersState = MutableStateFlow("svg/75_0.svg")
val drawablePathIconSupplierDemandRadarState = MutableStateFlow("svg/77_0.svg")
val drawablePathIconSupplierBackorderRecoveryState = MutableStateFlow("svg/91_0.svg")
val drawablePathIconSupplierRecoveryOwnerState = MutableStateFlow("svg/92_0.svg")
val drawablePathIconSupplierRecoveryClockState = MutableStateFlow("svg/93_0.svg")
val drawablePathIconSupplierRecoveryProofState = MutableStateFlow("svg/94_0.svg")
val drawablePathIconSupplierRecoveryResolutionState = MutableStateFlow("svg/95_0.svg")
val drawablePathIconSupplierRecoveryContactState = MutableStateFlow("svg/96_0.svg")
val drawablePathIconSupplierRecoveryRiskState = MutableStateFlow("svg/97_0.svg")
val drawablePathIconSupplierRecoveryConfidenceState = MutableStateFlow("svg/98_0.svg")
val drawablePathIconSupplierRecoveryFollowUpState = MutableStateFlow("svg/99_0.svg")
val drawablePathIconSupplierRecoveryHandoffState = MutableStateFlow("svg/100_0.svg")
val drawablePathIconSupplierRecoveryClosureState = MutableStateFlow("svg/101_0.svg")
val drawablePathIconSupplierRecoveryLedgerState = MutableStateFlow("svg/102_0.svg")
val drawablePathIconSupplierRecoveryTriageState = MutableStateFlow("svg/103_0.svg")
val drawablePathIconSupplierRecoveryCommandState = MutableStateFlow("svg/104_0.svg")
val drawablePathIconSupplierRecoveryPromiseShieldState = MutableStateFlow("svg/105_0.svg")
val drawablePathIconSupplierRecoveryDeskState = MutableStateFlow("svg/106_0.svg")
val drawablePathIconSupplierRecoveryWaveState = MutableStateFlow("svg/107_0.svg")
val drawablePathIconSupplierRecoveryAgingState = MutableStateFlow("svg/108_0.svg")
val drawablePathIconSupplierRecoveryBottleneckState = MutableStateFlow("svg/109_0.svg")
val drawablePathIconSupplierRecoveryLoadState = MutableStateFlow("svg/110_0.svg")
val drawablePathIconSupplierRecoveryImpactState = MutableStateFlow("svg/111_0.svg")
val drawablePathIconSupplierRecoveryCommitState = MutableStateFlow("svg/112_0.svg")
val drawablePathIconSupplierRecoveryAllocationState = MutableStateFlow("svg/113_0.svg")
val drawablePathIconSupplierRecoveryExceptionState = MutableStateFlow("svg/114_0.svg")
val drawablePathIconSupplierRecoveryCauseState = MutableStateFlow("svg/115_0.svg")
val drawablePathIconSupplierRecoveryVerificationState = MutableStateFlow("svg/116_0.svg")
val drawablePathIconSupplierRecoveryApprovalState = MutableStateFlow("svg/117_0.svg")
val drawablePathIconSupplierRecoveryExecutionState = MutableStateFlow("svg/118_0.svg")
val drawablePathIconSupplierRecoveryReleaseState = MutableStateFlow("svg/119_0.svg")
val drawablePathIconSupplierRecoverySealState = MutableStateFlow("svg/120_0.svg")
val drawablePathIconSupplierRecoveryCloseoutState = MutableStateFlow("svg/121_0.svg")
val drawablePathIconSupplierRecoveryReopenState = MutableStateFlow("svg/122_0.svg")
val drawablePathIconSupplierRecoveryReconciliationState = MutableStateFlow("svg/123_0.svg")
val drawablePathIconSupplierRecoveryAuditState = MutableStateFlow("svg/124_0.svg")
val drawablePathIconSupplierDispatchState = MutableStateFlow("svg/78_0.svg")
val drawablePathIconSupplierTermsGuardState = MutableStateFlow("svg/89_0.svg")
val drawablePathIconBuyerAgeRestrictionState = MutableStateFlow("svg/73_0.svg")
val drawablePathIconTransactionTimeRestrictionState = MutableStateFlow("svg/74_0.svg")
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
private val authSessionMutationMutex = Mutex()
private val authRefreshNetworkMutex = Mutex()
private val cloudSessionValidationMutex = Mutex()
private val manualCloudConnectionRefreshMutex = Mutex()
private val cloudConnectionRecoveryMutex = Mutex()
private const val AUTH_REFRESH_NON_AUTH_FAILURE_GRACE_MILLIS = 5_000L
private const val AUTH_REFRESH_SUCCESS_CACHE_MILLIS = 30_000L
private const val CLOUD_SESSION_VALIDATION_FAILURE_CACHE_MILLIS = 10_000L
private const val CLOUD_CONNECTION_HEALTH_CHECK_REACHABLE_INTERVAL_MILLIS = 30_000L
private const val CLOUD_CONNECTION_HEALTH_CHECK_REALTIME_CONNECTED_INTERVAL_MILLIS = 60_000L
private const val CLOUD_CONNECTION_HEALTH_CHECK_REALTIME_SANITY_INTERVAL_MILLIS = 5 * 60_000L
private const val CLOUD_CONNECTION_HEALTH_CHECK_LONG_PAUSE_MILLIS = 90_000L
private const val CLOUD_CONNECTION_HEALTH_MONITOR_EXCEPTION_RETRY_MILLIS = 5_000L
private const val CLOUD_CONNECTION_HEALTH_CHECK_AUTH_REQUIRED_INTERVAL_MILLIS = 60_000L
private const val CLOUD_CONNECTION_HEALTH_CHECK_UNKNOWN_INTERVAL_MILLIS = 10_000L
private const val CLOUD_CONNECTION_HEALTH_CHECK_UNAVAILABLE_INTERVAL_MILLIS = 2_000L
private const val CLOUD_CONNECTION_HEALTH_CHECK_BUSY_DEFER_MILLIS = 10_000L
private const val CLOUD_CONNECTION_HEALTH_CHECK_TIMEOUT_MILLIS = 20_000L
private const val CLOUD_CONNECTION_AUTH_REFRESH_SUPPRESSION_AFTER_TRANSPORT_FAILURE_MILLIS = 15_000L
private const val CLOUD_CONNECTION_PRESENTATION_INITIAL_OFFLINE_SETTLE_MILLIS = 3_000L
private const val CLOUD_CONNECTION_PRESENTATION_OFFLINE_SETTLE_MILLIS = 8_000L
private const val CLOUD_CONNECTION_PRESENTATION_RECOVERY_SETTLE_MILLIS = 1_500L
private const val CLOUD_CONNECTION_PRESENTATION_INITIAL_REACHABLE_SETTLE_MILLIS = 1_000L
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
@Volatile
private var rejectedAuthRefreshTokenMemory: String? = null
@Volatile
private var rejectedAuthRefreshMessageMemory: List<LocalizedStringDataModel>? = null
@Volatile
private var validatedCloudAccessTokenMemory: String? = null
@Volatile
private var cloudSessionValidationFailureMemory: CloudSessionValidationFailureMemory? = null
@Volatile
private var successfulAuthRefreshMemory: SuccessfulAuthRefreshMemory? = null
@Volatile
private var authenticatedSessionGeneration: Long = 0L

private data class CloudSessionValidationFailureMemory(
    val accessToken: String,
    val recordedAtMillis: Long,
    val response: ResponseDataModel<Unit>
)

private data class SuccessfulAuthRefreshMemory(
    val inputRefreshToken: String,
    val recordedAtMillis: Long,
    val response: ResponseDataModel<TokenPair>
)

val activeNetworkOperationsState = MutableStateFlow(0)
val cloudTransportStatusState = MutableStateFlow(CLOUD_TRANSPORT_STATUS_UNKNOWN)

// The transport state above is diagnostic/operational and may change as token refreshes and
// independent requests report evidence. The banner observes this presentation-grade state instead:
// it ignores auth-only transitions and requires the confirmed state to remain settled before the
// user sees a color or text change.
private data class CloudConnectionPresentationState(
    val displayedStatus: Int = CLOUD_TRANSPORT_STATUS_UNKNOWN,
    val generation: Long = 0L,
    val pendingStatus: Int? = null
)

// Network responses can finish on different dispatchers at nearly the same time. Keep both the
// displayed banner state and its pending transition in one atomic value. A delayed stale transition
// can then only commit with compareAndSet; newer reachability evidence makes that commit impossible.
private val cloudConnectionPresentationState = MutableStateFlow(CloudConnectionPresentationState())
val cloudConnectionPresentationStatusState: StateFlow<Int> = cloudConnectionPresentationState
    .map { state -> state.displayedStatus }
    .stateIn(
        scope = GlobalScope,
        started = SharingStarted.Eagerly,
        initialValue = CLOUD_TRANSPORT_STATUS_UNKNOWN
    )

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
            requestTimeoutMillis = 30_000L
            connectTimeoutMillis = 10_000L
            socketTimeoutMillis = 30_000L
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
                    if (!request.allowsStoredSessionAuthorization()) {
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
                            val sessionGeneration = currentAuthenticatedSessionGeneration()
                            if (rejectedAuthRefreshTokenMatches(current.refreshToken)) {
                                markCloudSessionNeedsRefreshForNotifications()
                                return@withLock null
                            }

                            val refreshResponse = refreshAuthTokensWithServerFallback(
                                refreshToken = current.refreshToken,
                                expectedSessionGeneration = sessionGeneration
                            )
                            if (!authenticatedSessionRefreshIsCurrent(sessionGeneration, current.refreshToken)) return@withLock null
                            val refreshedTokens = refreshResponse.payload
                            when {
                                refreshedTokens != null -> {
                                    val installed = installRefreshedAuthenticatedSession(
                                        expectedGeneration = sessionGeneration,
                                        expectedRefreshToken = current.refreshToken,
                                        tokenPair = refreshedTokens
                                    )
                                    if (!installed) return@withLock null
                                    BearerTokens(refreshedTokens.accessToken, refreshedTokens.refreshToken)
                                }

                                refreshResponse.transportFailure -> {
                                    // Never retry the rejected request with the same expired access token.
                                    // The original 401 is returned to networkRequest, while the grounded
                                    // transport state remains responsible for offline presentation.
                                    null
                                }

                                refreshResponse.httpStatusCode == HttpStatusCode.Unauthorized.value -> {
                                    // Recorded only for the matching session by the refresh coordinator.
                                    null
                                }

                                else -> {
                                    markCloudTransportReachableForNotifications(authenticated = false, authRefreshRequired = null)
                                    null
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
private val storesRefreshPendingState = MutableStateFlow(false)
val addStoreMutex = Mutex()
val updateStoreMutex = Mutex()
val deleteStoreMutex = Mutex()
private val refreshStoreAddressesMutex = Mutex()
@Volatile private var lastStoreAddressRefreshRequestMillis: Long = 0L

const val KEY_ACTIVE_STORE_ID = "key_activeStoreId"
const val KEY_ACTIVE_STORE_EXPLICIT_NONE = "key_activeStoreExplicitNone"

private suspend fun activeStoreExplicitNoneIsSet(): Boolean = ActiveStores.explicitNone


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

val getParentStoreStockMutex = Mutex()
val addGoodsItemMutex = Mutex()
val updateGoodsItemMutex = Mutex()
val deleteGoodsItemMutex = Mutex()
val addGoodsBatchMutex = Mutex()
val updateGoodsBatchMutex = Mutex()
val deleteGoodsBatchMutex = Mutex()
private const val STOCK_ITEM_DELETE_TOMBSTONE_TTL_MILLIS = 2L * 60L * 1000L
private val recentlyDeletedStockItemIds = mutableMapOf<String, Long>()
private val recentlyDeletedStockItemIdsMutex = Mutex()

private fun pruneRecentlyDeletedStockItemIdsLocked(now: Long = getCurrentTimeMillis()) {
    recentlyDeletedStockItemIds
        .filterValues { expiresAt -> expiresAt <= now }
        .keys
        .toList()
        .forEach { recentlyDeletedStockItemIds.remove(it) }
}

private suspend fun rememberRecentlyDeletedStockItemId(id: String) {
    val cleanId = id.trim().takeIf { it.isNotBlank() } ?: return
    recentlyDeletedStockItemIdsMutex.withLock {
        val now = getCurrentTimeMillis()
        pruneRecentlyDeletedStockItemIdsLocked(now)
        recentlyDeletedStockItemIds[cleanId] = now + STOCK_ITEM_DELETE_TOMBSTONE_TTL_MILLIS
    }
}

private suspend fun filterRecentlyDeletedStockItems(items: List<GoodsItemDataModel>): List<GoodsItemDataModel> {
    if (items.isEmpty()) return items
    return recentlyDeletedStockItemIdsMutex.withLock {
        pruneRecentlyDeletedStockItemIdsLocked()
        if (recentlyDeletedStockItemIds.isEmpty()) {
            items
        } else {
            items.filterNot { item -> item.id in recentlyDeletedStockItemIds }
        }
    }
}

private suspend fun filterRecentlyDeletedStockBatches(batches: List<GoodsBatchDataModel>): List<GoodsBatchDataModel> {
    if (batches.isEmpty()) return batches
    return recentlyDeletedStockItemIdsMutex.withLock {
        pruneRecentlyDeletedStockItemIdsLocked()
        if (recentlyDeletedStockItemIds.isEmpty()) {
            batches
        } else {
            batches.filterNot { batch -> recentlyDeletedStockItemIds.containsKey(batch.goodsItemId) }
        }
    }
}

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
private val supportMessageMutationMutex = Mutex()
val closeSupportTicketMutex = Mutex()
val reopenSupportTicketMutex = Mutex()
val getSupportMessagesMutex = Mutex()
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
val stockItemHistoryState = MutableDataStateFlow<List<OperationLogDataModel>>(GlobalScope)
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
private val getStockItemHistoryMutex = Mutex()
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
    val values = this?.find { it.id == id }?.values
    return resolveLocalizedResource(id, language, values, null)
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
    val normalized = normalizeAppThemePreference(themeId)
    val values = find { it.id == id }?.values
    return values?.firstOrNull { it.themeId == normalized }?.valueHex
        ?: tintedAppThemeColor(id, normalized)
        ?: values?.firstOrNull { it.themeId == -1L }?.valueHex
        ?: values?.firstOrNull { it.themeId == appDrawableThemeId(normalized) }?.valueHex
        ?: values?.firstOrNull { it.themeId == DEFAULT_APP_THEME_ID }?.valueHex
        ?: values?.firstOrNull()?.valueHex
}

fun List<StylizedDrawablePathsGroupDataModel>.extractPath(id: Long, themeId: Long): String? {
    val variant = appDrawableThemeId(themeId)
    val values = find { it.id == id }?.values
    return values?.firstOrNull { it.themeId == variant }?.path
        ?: values?.firstOrNull { it.themeId == -1L }?.path
        ?: values?.firstOrNull { it.themeId == DEFAULT_APP_THEME_ID }?.path
        ?: values?.firstOrNull()?.path
        ?: if (id in 0L..212L) "svg/${id}_${variant}.svg" else null
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
    val targetLanguage = if (canonicalLanguageCode(language) == "system") effectiveAppLanguage(language) else canonicalLanguageCode(language)

    explicitEventMessageReference()?.let { reference ->
        EventMessages.renderExact(reference, targetLanguage) { id ->
            stringsState.payloadValue?.find { it.id == id }?.values
        }?.let { return it }
        // A template's English fallback must not replace an authored Tajik/Uzbek value.
        exactLocalizedValue(targetLanguage)?.let { return it }
        EventMessages.render(reference, targetLanguage) { id ->
            stringsState.payloadValue?.find { it.id == id }?.values
        }?.let { return it }
    }
    return exactLocalizedValue(targetLanguage)
        ?: exactLocalizedValue("main")
        ?: exactLocalizedValue("en")
        ?: firstOrNull { it.value.isNotBlank() }?.value
}

fun localizedStringResourceMessage(
    id: Long,
    main: String,
    en: String = main,
    ru: String = main,
    kk: String = main
): List<LocalizedStringDataModel> {
    val values = stringsState.payloadValue
        ?.find { it.id == id }
        ?.values
        ?.takeIf { it.isNotEmpty() }
        ?: listOf(
            LocalizedStringDataModel("main", main),
            LocalizedStringDataModel("en", en),
            LocalizedStringDataModel("ru", ru),
            LocalizedStringDataModel("kk", kk)
        )
    val reference = EventMessageReference("resource.$id")
    val completeValues = values.withMissingLocalizedValues(listOf("tg", "ky", "uz").mapNotNull { language ->
        bundledTranslatedStringResource(id, language)?.let { LocalizedStringDataModel(language, it) }
    })
    return completeValues.mapIndexed { index, value ->
        value.copy(messageTemplate = reference.takeIf { index == 0 })
    }

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
    val cleanLanguage = effectiveAppLanguage(language)
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

fun StoreWorkerRequestDataModel.offerNoteVisible(language: String): String? {
    return offerNoteLocalized.extractLocalizedString(language)?.trim()?.takeIf { it.isNotBlank() }
        ?: offerNote?.trim()?.takeIf { it.isNotBlank() }
        ?: when {
            direction == WORKER_REQUEST_DIRECTION_STORE_TO_USER -> requestNoteVisible(language)
            direction == WORKER_REQUEST_DIRECTION_USER_TO_STORE && status == WORKER_REQUEST_STATUS_INVITED -> responseNoteVisible(language)
            else -> null
        }
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
    if (!beginAitaInitializationOnce()) {
        logCloudConnectionDiagnostic("AITA init ignored: initialization is already active")
        return
    }

    GlobalScope.launch(Dispatchers.ourIo) {
        // Load persisted configuration before starting collectors. Otherwise the initial bundled
        // server URL can overwrite the last working local server URL before the app has a chance
        // to use it, making the client appear to never reach the backend.
        loadCachedApplicationData()
        startSupplierIdentityFocus()
        startAppCacheCollectors()
        AnalyticsWorkspace.start()
        loadTransactionCartUiState()
        initializeLocalBranchNetwork()
        startCloudConnectionHealthMonitor()
        syncPendingSessionCleanupsToServer()
        if (getStoredUserAuthTokens?.invoke() != null) startRealtimeUpdates()
    }

    GlobalScope.launch(Dispatchers.ourIo) { AppPreferences.hydrate() }

    // Mode is restored with its account; a legacy device-global key is never a live command bus.

    GlobalScope.launch(Dispatchers.ourIo) { observeActiveInventoryData() }

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

fun setAppLocale(language: String, syncServer: Boolean = true) =
    AppPreferences.select(language = language, sync = syncServer)

fun setAppTheme(themeId: Long, syncServer: Boolean = true) =
    AppPreferences.select(theme = themeId, sync = syncServer)

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

fun setAppSizeMode(sizeModeId: Long, syncServer: Boolean = true) =
    AppPreferences.select(scale = sizeModeId, sync = syncServer)

fun setAppMode(modeId: Int) {
    val safeModeId = normalizeAppModePreference(modeId)
    AccountAppModes.select(safeModeId)
}

fun updateGlobalAppConfiguration(
    configuration: GlobalAppConfigurationDataModel,
    resourceConfiguration: GlobalAppConfigurationDataModel
) {
    if (globalAppConfigurationState.value.value is DataState.Empty)
        globalAppConfigurationState.emit(DataState.Success(resourceConfiguration))
}

suspend fun updateStrings(
    strings: List<LocalizedStringGroupDataModel>,
    resourceStrings: List<LocalizedStringGroupDataModel>
) {
    withContext(Dispatchers.Default) {
        val language = effectiveAppLanguage(appLanguageState.value)
        val strings = mergeLocalizedStringGroups(strings, resourceStrings)
        stringAppNameState.emit(
            strings.extractString(0, language) ?: resourceStrings.extractString(
                0,
                language
            )!!
        )
        stringLogInState.emit(
            strings.extractString(1, language) ?: resourceStrings.extractString(
                1,
                language
            )!!
        )
        stringPhoneNumberState.emit(
            strings.extractString(2, language) ?: resourceStrings.extractString(
                2,
                language
            )!!
        )
        stringEnterPhoneNumberState.emit(
            strings.extractString(3, language) ?: resourceStrings.extractString(3, language)!!
        )
        stringEmailState.emit(
            strings.extractString(4, language) ?: resourceStrings.extractString(
                4,
                language
            )!!
        )
        stringEnterEmailAddressState.emit(
            strings.extractString(5, language) ?: resourceStrings.extractString(5, language)!!
        )
        stringPasswordState.emit(
            strings.extractString(6, language) ?: resourceStrings.extractString(
                6,
                language
            )!!
        )
        stringEnterPasswordState.emit(
            strings.extractString(7, language) ?: resourceStrings.extractString(7, language)!!
        )
        stringCancelState.emit(
            strings.extractString(8, language) ?: resourceStrings.extractString(
                8,
                language
            )!!
        )
        stringClearState.emit(
            strings.extractString(9, language) ?: resourceStrings.extractString(
                9,
                language
            )!!
        )
        stringAuthenticationFailedState.emit(
            strings.extractString(10, language) ?: resourceStrings.extractString(10, language)!!
        )
        stringPhoneNumberMustBeState.emit(
            strings.extractString(11, language) ?: resourceStrings.extractString(11, language)!!
        )
        stringEmailMustBeState.emit(
            strings.extractString(12, language) ?: resourceStrings.extractString(12, language)!!
        )
        stringPasswordMustBeState.emit(
            strings.extractString(13, language) ?: resourceStrings.extractString(13, language)!!
        )
        stringRepeatPasswordState.emit(
            strings.extractString(14, language) ?: resourceStrings.extractString(14, language)!!
        )
        stringPasswordsMustMatchState.emit(
            strings.extractString(15, language) ?: resourceStrings.extractString(15, language)!!
        )
        stringFirstNameState.emit(
            strings.extractString(16, language) ?: resourceStrings.extractString(
                16,
                language
            )!!
        )
        stringLastNameState.emit(
            strings.extractString(17, language) ?: resourceStrings.extractString(
                17,
                language
            )!!
        )
        stringEnterFirstNameState.emit(
            strings.extractString(18, language) ?: resourceStrings.extractString(18, language)!!
        )
        stringEnterLastNameState.emit(
            strings.extractString(19, language) ?: resourceStrings.extractString(
                19,
                language
            )!!
        )
        stringUserWithThisPhoneNumberIsAlreadyRegisteredState.emit(
            strings.extractString(20, language) ?: resourceStrings.extractString(20, language)!!
        )
        stringUserWithThisEmailAddressIsAlreadyRegisteredState.emit(
            strings.extractString(21, language) ?: resourceStrings.extractString(21, language)!!
        )
        stringSignUpState.emit(
            strings.extractString(22, language) ?: resourceStrings.extractString(
                22,
                language
            )!!
        )
        stringConfirmState.emit(
            strings.extractString(23, language) ?: resourceStrings.extractString(
                23,
                language
            )!!
        )
        stringSaleState.emit(
            strings.extractString(24, language) ?: resourceStrings.extractString(
                24,
                language
            )!!
        )
        stringReturnState.emit(
            strings.extractString(25, language) ?: resourceStrings.extractString(
                25,
                language
            )!!
        )
        stringSupplyState.emit(
            strings.extractString(26, language) ?: resourceStrings.extractString(
                26,
                language
            )!!
        )
        stringStockState.emit(
            strings.extractString(27, language) ?: resourceStrings.extractString(
                27,
                language
            )!!
        )
        stringMenuState.emit(
            strings.extractString(28, language) ?: resourceStrings.extractString(
                28,
                language
            )!!
        )
        stringBackState.emit(
            strings.extractString(29, language) ?: resourceStrings.extractString(
                29,
                language
            )!!
        )
        stringAddGoodsItemState.emit(
            strings.extractString(30, language) ?: resourceStrings.extractString(
                30,
                language
            )!!
        )
        stringEditGoodsItemState.emit(
            strings.extractString(31, language) ?: resourceStrings.extractString(
                31,
                language
            )!!
        )
        stringUserAccountState.emit(
            strings.extractString(32, language) ?: resourceStrings.extractString(32, language)!!
        )
        stringGoodsCategoriesState.emit(
            strings.extractString(33, language) ?: resourceStrings.extractString(33, language)!!
        )
        stringAddGoodsCategoryState.emit(
            strings.extractString(34, language) ?: resourceStrings.extractString(34, language)!!
        )
        stringEditGoodsCategoryState.emit(
            strings.extractString(35, language) ?: resourceStrings.extractString(35, language)!!
        )
        stringStoresState.emit(
            strings.extractString(36, language) ?: resourceStrings.extractString(
                36,
                language
            )!!
        )
        stringAddStoreState.emit(
            strings.extractString(37, language) ?: resourceStrings.extractString(
                37,
                language
            )!!
        )
        stringEditStoreState.emit(
            strings.extractString(38, language) ?: resourceStrings.extractString(
                38,
                language
            )!!
        )
        stringSubscriptionState.emit(
            strings.extractString(39, language) ?: resourceStrings.extractString(
                39,
                language
            )!!
        )
        stringSubscriptionPlansState.emit(
            strings.extractString(40, language) ?: resourceStrings.extractString(40, language)!!
        )
        stringTransactionHistoryState.emit(
            strings.extractString(41, language) ?: resourceStrings.extractString(41, language)!!
        )
        stringReceiptState.emit(
            strings.extractString(42, language) ?: resourceStrings.extractString(
                42,
                language
            )!!
        )
        stringAnalyticsState.emit(
            strings.extractString(43, language) ?: resourceStrings.extractString(
                43,
                language
            )!!
        )
        stringWorkersState.emit(
            strings.extractString(44, language) ?: resourceStrings.extractString(
                44,
                language
            )!!
        )
        stringAddWorkerState.emit(
            strings.extractString(45, language) ?: resourceStrings.extractString(
                45,
                language
            )!!
        )
        stringEditWorkerState.emit(
            strings.extractString(46, language) ?: resourceStrings.extractString(
                46,
                language
            )!!
        )
        stringSuppliersState.emit(
            strings.extractString(47, language) ?: resourceStrings.extractString(
                47,
                language
            )!!
        )
        stringAddSupplierState.emit(
            strings.extractString(48, language) ?: resourceStrings.extractString(48, language)!!
        )
        stringEditSupplierState.emit(
            strings.extractString(49, language) ?: resourceStrings.extractString(
                49,
                language
            )!!
        )
        stringDebtorsState.emit(
            strings.extractString(50, language) ?: resourceStrings.extractString(
                50,
                language
            )!!
        )
        stringCloseDebtState.emit(
            strings.extractString(51, language) ?: resourceStrings.extractString(
                51,
                language
            )!!
        )
        stringDevicesState.emit(
            strings.extractString(52, language) ?: resourceStrings.extractString(
                52,
                language
            )!!
        )
        stringAppLanguageState.emit(
            strings.extractString(53, language) ?: resourceStrings.extractString(53, language)!!
        )
        stringAppThemeState.emit(
            strings.extractString(54, language) ?: resourceStrings.extractString(
                54,
                language
            )!!
        )
        stringSelectState.emit(
            strings.extractString(55, language) ?: resourceStrings.extractString(
                55,
                language
            )!!
        )
        stringUserWithThisPhoneNumberAndEmailAddressIsAlreadyRegisteredState.emit(
            strings.extractString(
                56,
                language
            ) ?: resourceStrings.extractString(56, language)!!
        )
        stringFirstNameCannotBeEmptyOrJustWhitespacesState.emit(
            strings.extractString(57, language) ?: resourceStrings.extractString(57, language)!!
        )
        stringLastNameCannotBeEmptyOrJustWhitespacesState.emit(
            strings.extractString(58, language) ?: resourceStrings.extractString(58, language)!!
        )
        stringSystemLanguageState.emit(
            strings.extractString(59, language) ?: resourceStrings.extractString(59, language)!!
        )
        stringBluetoothPermissionRequiredState.emit(
            strings.extractString(60, language) ?: resourceStrings.extractString(60, language)!!
        )
        stringForSearchAndConnectionToBluetoothBarcodeScannersAndReceiptPrintersState.emit(
            strings.extractString(
                61,
                language
            ) ?: resourceStrings.extractString(61, language)!!
        )
        stringForSearchAndConnectionToBluetoothBarcodeScannersAndReceiptPrintersYouCanGrantItInAppSettingsState.emit(
            strings.extractString(62, language) ?: resourceStrings.extractString(62, language)!!
        )
        stringBluetoothDisabledState.emit(
            strings.extractString(63, language) ?: resourceStrings.extractString(63, language)!!
        )
        stringEnableForSearchAndConnectionToBluetoothBarcodeScannersAndReceiptPrintersState.emit(
            strings.extractString(
                64,
                language
            ) ?: resourceStrings.extractString(64, language)!!
        )
        stringSearchByAnyDataState.emit(
            strings.extractString(65, language) ?: resourceStrings.extractString(65, language)!!
        )
        stringListEmptyState.emit(
            strings.extractString(66, language) ?: resourceStrings.extractString(
                66,
                language
            )!!
        )
        stringNoMatchesState.emit(
            strings.extractString(67, language) ?: resourceStrings.extractString(
                67,
                language
            )!!
        )
        stringNameState.emit(
            strings.extractString(68, language) ?: resourceStrings.extractString(
                68,
                language
            )!!
        )
        stringBarcodeState.emit(
            strings.extractString(69, language) ?: resourceStrings.extractString(
                69,
                language
            )!!
        )
        stringSupplyPriceState.emit(
            strings.extractString(70, language) ?: resourceStrings.extractString(70, language)!!
        )
        stringSalePriceState.emit(
            strings.extractString(71, language) ?: resourceStrings.extractString(
                71,
                language
            )!!
        )
        stringReturnPriceState.emit(
            strings.extractString(72, language) ?: resourceStrings.extractString(72, language)!!
        )
        stringCategoryState.emit(
            strings.extractString(73, language) ?: resourceStrings.extractString(
                73,
                language
            )!!
        )
        stringSupplierState.emit(
            strings.extractString(74, language) ?: resourceStrings.extractString(
                74,
                language
            )!!
        )
        stringEnterBarcodeState.emit(
            strings.extractString(75, language) ?: resourceStrings.extractString(
                75,
                language
            )!!
        )
        stringEnterNameState.emit(
            strings.extractString(76, language) ?: resourceStrings.extractString(
                76,
                language
            )!!
        )
        stringEnterSupplyPriceState.emit(
            strings.extractString(77, language) ?: resourceStrings.extractString(77, language)!!
        )
        stringEnterSalePriceState.emit(
            strings.extractString(78, language) ?: resourceStrings.extractString(78, language)!!
        )
        stringEnterReturnPriceState.emit(
            strings.extractString(79, language) ?: resourceStrings.extractString(79, language)!!
        )
        stringSelectCategoryState.emit(
            strings.extractString(80, language) ?: resourceStrings.extractString(80, language)!!
        )
        stringSelectSupplierState.emit(
            strings.extractString(81, language) ?: resourceStrings.extractString(81, language)!!
        )
        stringEditState.emit(
            strings.extractString(82, language) ?: resourceStrings.extractString(
                82,
                language
            )!!
        )
        stringChangePasswordState.emit(
            strings.extractString(83, language) ?: resourceStrings.extractString(83, language)!!
        )
        stringNewPasswordState.emit(
            strings.extractString(84, language) ?: resourceStrings.extractString(84, language)!!
        )
        stringEnterNewPasswordState.emit(
            strings.extractString(85, language) ?: resourceStrings.extractString(85, language)!!
        )
        stringRepeatNewPasswordState.emit(
            strings.extractString(86, language) ?: resourceStrings.extractString(86, language)!!
        )
        stringConfirmationPasswordState.emit(
            strings.extractString(87, language) ?: resourceStrings.extractString(87, language)!!
        )
        stringRequiredToEditAccountState.emit(
            strings.extractString(88, language) ?: resourceStrings.extractString(88, language)!!
        )
        stringAccountSuccessfullyUpdatedState.emit(
            strings.extractString(89, language) ?: resourceStrings.extractString(89, language)!!
        )
        stringLoggingOutState.emit(
            strings.extractString(90, language) ?: resourceStrings.extractString(90, language)!!
        )
        stringSessionTimeExpiredLoggingOutState.emit(
            strings.extractString(91, language) ?: resourceStrings.extractString(91, language)!!
        )
        stringAliasState.emit(
            strings.extractString(92, language) ?: resourceStrings.extractString(
                92,
                language
            )!!
        )
        stringDescriptionState.emit(
            strings.extractString(93, language) ?: resourceStrings.extractString(93, language)!!
        )
        stringEnterAliasState.emit(
            strings.extractString(94, language) ?: resourceStrings.extractString(
                94,
                language
            )!!
        )
        stringEnterDescriptionState.emit(
            strings.extractString(95, language) ?: resourceStrings.extractString(95, language)!!
        )
        stringOptionalState.emit(
            strings.extractString(96, language) ?: resourceStrings.extractString(
                96,
                language
            )!!
        )
        stringLoggingInState.emit(
            strings.extractString(97, language) ?: resourceStrings.extractString(
                97,
                language
            )!!
        )
        stringSigningUpState.emit(
            strings.extractString(98, language) ?: resourceStrings.extractString(
                98,
                language
            )!!
        )
        stringCompanyFormState.emit(
            strings.extractString(99, language) ?: resourceStrings.extractString(
                99,
                language
            )!!
        )
        stringMeasurementUnitState.emit(
            strings.extractString(100, language) ?: resourceStrings.extractString(
                100,
                language
            )!!
        )
        stringNoActiveStoreState.emit(
            strings.extractString(101, language) ?: resourceStrings.extractString(
                101,
                language
            )!!
        )
        stringSelectInMenuState.emit(
            strings.extractString(102, language) ?: resourceStrings.extractString(
                102,
                language
            )!!
        )
        stringSupplyDataState.emit(
            strings.extractString(103, language) ?: resourceStrings.extractString(
                103,
                language
            )!!
        )
        stringSaleDataState.emit(
            strings.extractString(104, language) ?: resourceStrings.extractString(
                104,
                language
            )!!
        )
        stringReturnDataState.emit(
            strings.extractString(105, language) ?: resourceStrings.extractString(
                105,
                language
            )!!
        )
        stringAddSupplyDataState.emit(
            strings.extractString(106, language) ?: resourceStrings.extractString(
                106,
                language
            )!!
        )
        stringAddSaleDataState.emit(
            strings.extractString(107, language) ?: resourceStrings.extractString(
                107,
                language
            )!!
        )
        stringAddReturnDataState.emit(
            strings.extractString(108, language) ?: resourceStrings.extractString(
                108,
                language
            )!!
        )
        stringAddBarcodeState.emit(
            strings.extractString(109, language) ?: resourceStrings.extractString(
                109,
                language
            )!!
        )
        stringAddNameState.emit(
            strings.extractString(110, language) ?: resourceStrings.extractString(
                110,
                language
            )!!
        )
        stringPaymentState.emit(
            strings.extractString(111, language) ?: resourceStrings.extractString(
                111,
                language
            )!!
        )
        stringAllState.emit(
            strings.extractString(112, language) ?: resourceStrings.extractString(
                112,
                language
            )!!
        )
        stringQuickState.emit(
            strings.extractString(113, language) ?: resourceStrings.extractString(
                113,
                language
            )!!
        )
        stringCategoriesState.emit(
            strings.extractString(114, language) ?: resourceStrings.extractString(
                114,
                language
            )!!
        )
        stringMainState.emit(
            strings.extractString(115, language) ?: resourceStrings.extractString(
                115,
                language
            )!!
        )
        stringAddTranslationState.emit(
            strings.extractString(116, language) ?: resourceStrings.extractString(
                116,
                language
            )!!
        )
        stringSetActiveState.emit(
            strings.extractString(117, language) ?: resourceStrings.extractString(
                117,
                language
            )!!
        )
        stringOutOfStockState.emit(
            strings.extractString(118, language) ?: resourceStrings.extractString(
                118,
                language
            )!!
        )
        stringDeleteState.emit(
            strings.extractString(119, language) ?: resourceStrings.extractString(
                119,
                language
            )!!
        )
        stringCashState.emit(
            strings.extractString(120, language) ?: resourceStrings.extractString(
                120,
                language
            )!!
        )
        stringCashlessState.emit(
            strings.extractString(121, language) ?: resourceStrings.extractString(
                121,
                language
            )!!
        )
        stringMixedState.emit(
            strings.extractString(122, language) ?: resourceStrings.extractString(
                122,
                language
            )!!
        )
        stringAddState.emit(
            strings.extractString(123, language) ?: resourceStrings.extractString(
                123,
                language
            )!!
        )
        stringSubtractState.emit(
            strings.extractString(124, language) ?: resourceStrings.extractString(
                124,
                language
            )!!
        )
        stringCurrentQuantityDataState.emit(
            strings.extractString(125, language) ?: resourceStrings.extractString(
                125,
                language
            )!!
        )
        stringEnterQuantityState.emit(
            strings.extractString(126, language) ?: resourceStrings.extractString(
                126,
                language
            )!!
        )
        stringAddQuantityDataState.emit(
            strings.extractString(127, language) ?: resourceStrings.extractString(
                127,
                language
            )!!
        )
        stringShelfBatchState.emit(
            strings.extractString(128, language) ?: resourceStrings.extractString(
                128,
                language
            )!!
        )
        stringActiveStoreState.emit(
            strings.extractString(129, language) ?: resourceStrings.extractString(
                129,
                language
            )!!
        )
        stringMakeInactiveState.emit(
            strings.extractString(130, language) ?: resourceStrings.extractString(
                130,
                language
            )!!
        )
        stringCartEmptyState.emit(
            strings.extractString(131, language) ?: resourceStrings.extractString(
                131,
                language
            )!!
        )
        stringCompleteState.emit(
            strings.extractString(132, language) ?: resourceStrings.extractString(
                132,
                language
            )!!
        )
        stringNoActiveWorkshiftState.emit(
            strings.extractString(133, language) ?: resourceStrings.extractString(
                133,
                language
            )!!
        )
        stringCartState.emit(
            strings.extractString(134, language) ?: resourceStrings.extractString(
                134,
                language
            )!!
        )
        stringAppModeState.emit(
            strings.extractString(135, language) ?: resourceStrings.extractString(
                135,
                language
            )!!
        )
        stringFinancesState.emit(
            strings.extractString(136, language) ?: resourceStrings.extractString(
                136,
                language
            )!!
        )
        stringItemsState.emit(
            strings.extractString(137, language) ?: resourceStrings.extractString(
                137,
                language
            )!!
        )
        stringBatchesState.emit(
            strings.extractString(138, language) ?: resourceStrings.extractString(
                138,
                language
            )!!
        )
        stringStandardPricesForSuppliersState.emit(
            strings.extractString(139, language) ?: resourceStrings.extractString(
                139,
                language
            )!!
        )
        stringEditableForIndividualBatchesState.emit(
            strings.extractString(140, language) ?: resourceStrings.extractString(
                140,
                language
            )!!
        )
        stringBatchesDataState.emit(
            strings.extractString(141, language) ?: resourceStrings.extractString(
                141,
                language
            )!!
        )
        stringReceiptNumberState.emit(
            strings.extractString(142, language) ?: resourceStrings.extractString(
                142,
                language
            ) ?: stringReceiptNumberState.value
        )
        stringTransactionIdState.emit(
            strings.extractString(143, language) ?: resourceStrings.extractString(
                143,
                language
            ) ?: stringTransactionIdState.value
        )
        stringDateState.emit(
            strings.extractString(144, language) ?: resourceStrings.extractString(
                144,
                language
            ) ?: stringDateState.value
        )
        stringCashierState.emit(
            strings.extractString(145, language) ?: resourceStrings.extractString(
                145,
                language
            ) ?: stringCashierState.value
        )
        stringStoreState.emit(
            strings.extractString(146, language) ?: resourceStrings.extractString(
                146,
                language
            ) ?: stringStoreState.value
        )
        stringAddressState.emit(
            strings.extractString(147, language) ?: resourceStrings.extractString(
                147,
                language
            ) ?: stringAddressState.value
        )
        stringPhoneState.emit(
            strings.extractString(148, language) ?: resourceStrings.extractString(
                148,
                language
            ) ?: stringPhoneState.value
        )
        stringTotalState.emit(
            strings.extractString(149, language) ?: resourceStrings.extractString(
                149,
                language
            ) ?: stringTotalState.value
        )
        stringPaidState.emit(
            strings.extractString(150, language) ?: resourceStrings.extractString(
                150,
                language
            ) ?: stringPaidState.value
        )
        stringDebtState.emit(
            strings.extractString(151, language) ?: resourceStrings.extractString(
                151,
                language
            ) ?: stringDebtState.value
        )
        stringDebtorState.emit(
            strings.extractString(152, language) ?: resourceStrings.extractString(
                152,
                language
            ) ?: stringDebtorState.value
        )
        stringDebtorPhoneState.emit(
            strings.extractString(153, language) ?: resourceStrings.extractString(
                153,
                language
            ) ?: stringDebtorPhoneState.value
        )
        stringChangeState.emit(
            strings.extractString(154, language) ?: resourceStrings.extractString(
                154,
                language
            ) ?: stringChangeState.value
        )
        stringVatState.emit(
            strings.extractString(155, language) ?: resourceStrings.extractString(
                155,
                language
            ) ?: stringVatState.value
        )
        stringVatNotSpecifiedState.emit(
            strings.extractString(156, language) ?: resourceStrings.extractString(
                156,
                language
            ) ?: stringVatNotSpecifiedState.value
        )
        stringFiscalStatusState.emit(
            strings.extractString(157, language) ?: resourceStrings.extractString(
                157,
                language
            ) ?: stringFiscalStatusState.value
        )
        stringNonFiscalSoftwareReceiptState.emit(
            strings.extractString(158, language) ?: resourceStrings.extractString(
                158,
                language
            ) ?: stringNonFiscalSoftwareReceiptState.value
        )
        stringThankYouState.emit(
            strings.extractString(159, language) ?: resourceStrings.extractString(
                159,
                language
            ) ?: stringThankYouState.value
        )
        stringNoItemsState.emit(
            strings.extractString(160, language) ?: resourceStrings.extractString(
                160,
                language
            ) ?: stringNoItemsState.value
        )
        stringPdfState.emit(
            strings.extractString(161, language) ?: resourceStrings.extractString(
                161,
                language
            ) ?: stringPdfState.value
        )
        stringShareState.emit(
            strings.extractString(162, language) ?: resourceStrings.extractString(
                162,
                language
            ) ?: stringShareState.value
        )
        stringWhatsAppState.emit(
            strings.extractString(163, language) ?: resourceStrings.extractString(
                163,
                language
            ) ?: stringWhatsAppState.value
        )
        stringPrintState.emit(
            strings.extractString(164, language) ?: resourceStrings.extractString(
                164,
                language
            ) ?: stringPrintState.value
        )
        stringQuitState.emit(
            strings.extractString(165, language) ?: resourceStrings.extractString(
                165,
                language
            ) ?: stringQuitState.value
        )
        stringReceiptPdfSavedState.emit(
            strings.extractString(166, language) ?: resourceStrings.extractString(
                166,
                language
            ) ?: stringReceiptPdfSavedState.value
        )
        stringReceiptSharedState.emit(
            strings.extractString(167, language) ?: resourceStrings.extractString(
                167,
                language
            ) ?: stringReceiptSharedState.value
        )
        stringReceiptSentToWhatsAppState.emit(
            strings.extractString(168, language) ?: resourceStrings.extractString(
                168,
                language
            ) ?: stringReceiptSentToWhatsAppState.value
        )
        stringReceiptSentToPrinterState.emit(
            strings.extractString(169, language) ?: resourceStrings.extractString(
                169,
                language
            ) ?: stringReceiptSentToPrinterState.value
        )
        stringReceiptActionFailedState.emit(
            strings.extractString(170, language) ?: resourceStrings.extractString(
                170,
                language
            ) ?: stringReceiptActionFailedState.value
        )
        stringGoodsReceiptTitleState.emit(
            strings.extractString(171, language) ?: resourceStrings.extractString(
                171,
                language
            ) ?: stringGoodsReceiptTitleState.value
        )
        stringSaleReceiptTitleState.emit(
            strings.extractString(172, language) ?: resourceStrings.extractString(
                172,
                language
            ) ?: stringSaleReceiptTitleState.value
        )
        stringReturnReceiptTitleState.emit(
            strings.extractString(173, language) ?: resourceStrings.extractString(
                173,
                language
            ) ?: stringReturnReceiptTitleState.value
        )
        stringSupplyReceiptTitleState.emit(
            strings.extractString(174, language) ?: resourceStrings.extractString(
                174,
                language
            ) ?: stringSupplyReceiptTitleState.value
        )
        stringDraftState.emit(
            strings.extractString(175, language) ?: resourceStrings.extractString(
                175,
                language
            ) ?: stringDraftState.value
        )
        stringNoNameState.emit(
            strings.extractString(176, language) ?: resourceStrings.extractString(
                176,
                language
            ) ?: stringNoNameState.value
        )

    }
}

suspend fun updateDrawables(
    drawables: List<StylizedDrawablePathsGroupDataModel>,
    resourceDrawables: List<StylizedDrawablePathsGroupDataModel>
) {
    withContext(Dispatchers.Default) {
        val themeId = appThemeIdState.value
        fun drawablePath(id: Long): String {
            val normalizedThemeId = appDrawableThemeId(themeId)
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
            drawablePath(147L)
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
        drawablePathIconWorkerRoleTemplatesState.emit(
            drawablePath(66L)
        )
        drawablePathIconStockHistoryState.emit(
            drawablePath(67L)
        )
        drawablePathIconAppModeStoreState.emit(
            drawablePath(68L)
        )
        drawablePathIconAppModeBuyerState.emit(
            drawablePath(69L)
        )
        drawablePathIconAppModeSupplierState.emit(
            drawablePath(70L)
        )
        drawablePathIconAppModeManufacturerState.emit(
            drawablePath(71L)
        )
        drawablePathIconSupplierCatalogState.emit(
            drawablePath(72L)
        )
        drawablePathIconSupplierContractsState.emit(
            drawablePath(76L)
        )
        drawablePathIconSupplierPartnersState.emit(
            drawablePath(75L)
        )
        drawablePathIconSupplierDemandRadarState.emit(
            drawablePath(77L)
        )
        drawablePathIconSupplierBackorderRecoveryState.emit(
            drawablePath(91L)
        )
        drawablePathIconSupplierRecoveryOwnerState.emit(
            drawablePath(92L)
        )
        drawablePathIconSupplierRecoveryClockState.emit(
            drawablePath(93L)
        )
        drawablePathIconSupplierRecoveryProofState.emit(
            drawablePath(94L)
        )
        drawablePathIconSupplierRecoveryResolutionState.emit(
            drawablePath(95L)
        )
        drawablePathIconSupplierRecoveryContactState.emit(
            drawablePath(96L)
        )
        drawablePathIconSupplierRecoveryRiskState.emit(
            drawablePath(97L)
        )
        drawablePathIconSupplierRecoveryConfidenceState.emit(
            drawablePath(98L)
        )
        drawablePathIconSupplierRecoveryFollowUpState.emit(
            drawablePath(99L)
        )
        drawablePathIconSupplierRecoveryHandoffState.emit(
            drawablePath(100L)
        )
        drawablePathIconSupplierRecoveryClosureState.emit(
            drawablePath(101L)
        )
        drawablePathIconSupplierRecoveryLedgerState.emit(
            drawablePath(102L)
        )
        drawablePathIconSupplierRecoveryTriageState.emit(
            drawablePath(103L)
        )
        drawablePathIconSupplierRecoveryCommandState.emit(
            drawablePath(104L)
        )
        drawablePathIconSupplierRecoveryPromiseShieldState.emit(
            drawablePath(105L)
        )
        drawablePathIconSupplierRecoveryDeskState.emit(
            drawablePath(106L)
        )
        drawablePathIconSupplierRecoveryWaveState.emit(
            drawablePath(107L)
        )
        drawablePathIconSupplierRecoveryAgingState.emit(
            drawablePath(108L)
        )
        drawablePathIconSupplierRecoveryBottleneckState.emit(
            drawablePath(109L)
        )
        drawablePathIconSupplierRecoveryLoadState.emit(
            drawablePath(110L)
        )
        drawablePathIconSupplierRecoveryImpactState.emit(
            drawablePath(111L)
        )
        drawablePathIconSupplierRecoveryCommitState.emit(
            drawablePath(112L)
        )
        drawablePathIconSupplierRecoveryAllocationState.emit(
            drawablePath(113L)
        )
        drawablePathIconSupplierRecoveryExceptionState.emit(
            drawablePath(114L)
        )
        drawablePathIconSupplierRecoveryCauseState.emit(
            drawablePath(115L)
        )
        drawablePathIconSupplierRecoveryVerificationState.emit(
            drawablePath(116L)
        )
        drawablePathIconSupplierRecoveryApprovalState.emit(
            drawablePath(117L)
        )
        drawablePathIconSupplierRecoveryExecutionState.emit(
            drawablePath(118L)
        )
        drawablePathIconSupplierRecoveryReleaseState.emit(
            drawablePath(119L)
        )
        drawablePathIconSupplierRecoverySealState.emit(
            drawablePath(120L)
        )
        drawablePathIconSupplierRecoveryCloseoutState.emit(
            drawablePath(121L)
        )
        drawablePathIconSupplierRecoveryReopenState.emit(
            drawablePath(122L)
        )
        drawablePathIconSupplierRecoveryReconciliationState.emit(
            drawablePath(123L)
        )
        drawablePathIconSupplierRecoveryAuditState.emit(
            drawablePath(124L)
        )
        drawablePathIconSupplierDispatchState.emit(
            drawablePath(78L)
        )
        drawablePathIconSupplierTermsGuardState.emit(
            drawablePath(89L)
        )
        drawablePathIconBuyerAgeRestrictionState.emit(
            drawablePath(73L)
        )
        drawablePathIconTransactionTimeRestrictionState.emit(
            drawablePath(74L)
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
private const val CACHE_BOOTSTRAP_SERVER_URL = "bootstrap_server_url"
private const val CACHE_LAST_KNOWN_GOOD_SERVER_URL = "last_known_good_server_url"
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
private const val CACHE_TRANSACTION_RETURN_BATCH_SELECTIONS = "transaction_return_batch_selections"
private const val CACHE_CART_CONDITION_CHECKS = "transaction_cart_condition_checks"
private const val CACHE_TRANSACTION_CART_SCROLL_STATES = "transaction_cart_scroll_states"
private const val CACHE_PENDING_SESSION_CLEANUPS = "pending_session_cleanups"
private const val CACHE_PENDING_WORKSHIFT_ENDS = "pending_workshift_ends"

private val AUTHENTICATED_ACCOUNT_CACHE_KEYS = listOf(
    CACHE_USER,
    CACHE_STORES,
    CACHE_SUPPLIERS,
    CACHE_NOTIFICATIONS,
    CACHE_SECURITY_SESSIONS,
    CACHE_SECURITY_SESSION_HISTORY,
    CACHE_MY_WORKER_MEMBERSHIPS,
    CACHE_MY_WORKER_REQUESTS,
    CACHE_USER_FINANCE_DASHBOARD,
)

private fun hasStoredAuthenticatedSession(): Boolean =
    runCatching { getStoredUserAuthTokens?.invoke() != null }.getOrDefault(false)

private var cachedGlobalConfigurationPrimedForNetwork = false
private val cachedGlobalConfigurationPrimeMutex = Mutex()

@PublishedApi
internal suspend fun ensureCachedGlobalConfigurationPrimedForNetwork() {
    if (cachedGlobalConfigurationPrimedForNetwork) return

    cachedGlobalConfigurationPrimeMutex.withLock {
        if (cachedGlobalConfigurationPrimedForNetwork) return@withLock

        getJsonCache<GlobalAppConfigurationDataModel>(CACHE_GLOBAL_CONFIG)?.let { cachedConfiguration ->
            val currentConfiguration = globalAppConfigurationState.payloadValue
            val cachedLastKnownGoodServerUrl = cachedLastKnownGoodServerUrlOrNull()
            val runtimeOverrideNormalized = normalizedExplicitAitaServerUrlOrNull(runtimeClientServerUrlOverride)
            // Cached global configuration is still useful for paths and resource IDs. Its embedded server
            // URL is not authoritative: only an explicit runtime override, a recently verified alias, or
            // the checked-in visible configuration may anchor this installation.
            val cachedConfigurationForThisInstall = cachedConfiguration.copy(
                serverUrl = when {
                    runtimeOverrideNormalized != null -> Pair(runtimeOverrideNormalized, currentConfiguration.serverUrl.second)
                    cachedLastKnownGoodServerUrl != null -> Pair(cachedLastKnownGoodServerUrl, currentConfiguration.serverUrl.second)
                    clientVisibleServerUrlFilesOnly -> currentConfiguration.serverUrl
                    else -> chooseClientServerUrlPair(
                        current = currentConfiguration.serverUrl,
                        incoming = cachedConfiguration.serverUrl
                    )
                }
            )
            globalAppConfigurationState.emit(DataState.Success(cachedConfigurationForThisInstall, cacheMessage()))
        }

        cachedGlobalConfigurationPrimedForNetwork = true
    }
}

private val AITA_SERVER_ENDPOINT_ROOT_SEGMENTS = setOf(
    "analytics",
    "auth",
    "balance",
    "cashregister",
    "config",
    ".well-known",
    "debtors",
    "finance",
    "generic",
    "healthz",
    "logs",
    "notifications",
    "readyz",
    "res",
    "rt",
    "security",
    "stock",
    "stockbatches",
    "stores",
    "subscriptions",
    "suppliers",
    "suppliercontracts",
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
    val cleanAuthority = authority
        .trim()
        .substringBefore('/')
        .substringBefore('?')
        .substringBefore('#')
    val host = when {
        cleanAuthority.startsWith("[") -> cleanAuthority.substringAfter('[').substringBefore(']')
        cleanAuthority.count { it == ':' } == 1 && cleanAuthority.substringAfter(':').all { it.isDigit() } -> cleanAuthority.substringBefore(':')
        else -> cleanAuthority
    }

    val normalizedHost = host.lowercase()
    if (normalizedHost == "localhost") return true
    if (normalizedHost == "::1") return true
    if (normalizedHost == "10.0.2.2") return true
    if (normalizedHost == "127.0.0.1") return true
    if (normalizedHost.startsWith("192.168.")) return true
    if (normalizedHost.startsWith("10.")) return true
    if (':' in normalizedHost && (normalizedHost.startsWith("fc") || normalizedHost.startsWith("fd"))) return true
    if (normalizedHost.endsWith(".local") || normalizedHost.endsWith(".ts.net")) return true
    if ('.' !in normalizedHost && ':' !in normalizedHost) return true

    val parts = normalizedHost.split('.')
    if (parts.size == 4 && parts.all { part -> part.toIntOrNull()?.let { it in 0..255 } == true }) {
        val first = parts[0].toIntOrNull() ?: return false
        val second = parts[1].toIntOrNull() ?: return false
        if (first == 172 && second in 16..31) return true
        if (first == 100 && second in 64..127) return true
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
        else -> {
            val authorityCandidate = trimmed
                .substringBefore('/')
                .substringBefore('?')
                .substringBefore('#')
            val inferredScheme = if (looksLikeLocalDevelopmentHostWithoutPort(authorityCandidate)) "http" else "https"
            "$inferredScheme://$trimmed"
        }
    }
        .withoutKnownAitaEndpointPath()
        .withDefaultAitaPortForLocalHostIfMissing(hadExplicitScheme)

    return runCatching {
        Url(withHttpScheme)
        withHttpScheme
    }.getOrNull()
}

private fun normalizedServerUrlHostOrNull(normalizedUrl: String): String? = runCatching {
    Url(normalizedUrl).host.trim().lowercase()
}.getOrNull()

/**
 * Automatic production discovery is intentionally locked to the registrar-independent Worker.
 * Explicit localhost/LAN overrides remain available for development, but every non-canonical
 * public endpoint is discarded before it can be revived by cached or server-published data.
 */
@PublishedApi
internal fun normalizedAutomaticAitaServerUrlOrNull(raw: String?): String? {
    val normalized = normalizedHttpServerUrlOrNull(raw) ?: return null
    return normalized.takeIf {
        normalizedServerUrlHostOrNull(it) == CANONICAL_AITA_PUBLIC_SERVER_HOST
    }
}

private fun isLocalOrPrivateAitaDevelopmentHost(host: String): Boolean {
    val normalizedHost = host.trim().lowercase()
    if (normalizedHost.isBlank()) return false
    if (looksLikeLocalDevelopmentHostWithoutPort(normalizedHost)) return true
    if (normalizedHost == "::1") return true
    if (':' in normalizedHost && (normalizedHost.startsWith("fc") || normalizedHost.startsWith("fd"))) return true
    if (normalizedHost.endsWith(".local") || normalizedHost.endsWith(".ts.net")) return true
    if ('.' !in normalizedHost && ':' !in normalizedHost) return true

    val ipv4Parts = normalizedHost.split('.')
    if (ipv4Parts.size == 4 && ipv4Parts.all { part -> part.toIntOrNull()?.let { it in 0..255 } == true }) {
        val first = ipv4Parts[0].toInt()
        val second = ipv4Parts[1].toInt()
        // Tailscale allocates addresses from 100.64.0.0/10.
        if (first == 100 && second in 64..127) return true
    }
    return false
}

@PublishedApi
internal fun normalizedExplicitAitaServerUrlOrNull(raw: String?): String? {
    val normalized = normalizedHttpServerUrlOrNull(raw) ?: return null
    val host = normalizedServerUrlHostOrNull(normalized) ?: return null
    return normalized.takeIf {
        host == CANONICAL_AITA_PUBLIC_SERVER_HOST || isLocalOrPrivateAitaDevelopmentHost(host)
    }
}

fun normalizedClientServerUrlOverride(raw: String?, currentRaw: String?): String? {
    val normalized = normalizedExplicitAitaServerUrlOrNull(raw) ?: return null
    val currentNormalized = normalizedExplicitAitaServerUrlOrNull(currentRaw)
    return normalized.takeIf { it != currentNormalized }
}

private fun normalizedHttpAbsoluteUrlOrNull(raw: String?): String? {
    val trimmed = raw
        ?.trim()
        ?.trimEnd('/')
        ?.takeIf { it.isNotBlank() }
        ?: return null

    val hadExplicitHttpScheme = trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true)
    val hadExplicitWebSocketScheme = trimmed.startsWith("ws://", ignoreCase = true) ||
        trimmed.startsWith("wss://", ignoreCase = true)

    val withHttpScheme = when {
        hadExplicitHttpScheme -> trimmed
        trimmed.startsWith("ws://", ignoreCase = true) -> "http://" + trimmed.substringAfter("://")
        trimmed.startsWith("wss://", ignoreCase = true) -> "https://" + trimmed.substringAfter("://")
        hadExplicitWebSocketScheme -> return null
        "://" in trimmed -> return null
        else -> {
            val authorityCandidate = trimmed
                .substringBefore('/')
                .substringBefore('?')
                .substringBefore('#')
            val inferredScheme = if (looksLikeLocalDevelopmentHostWithoutPort(authorityCandidate)) "http" else "https"
            "$inferredScheme://$trimmed"
        }
    }

    return runCatching {
        val url = Url(withHttpScheme)
        val protocol = url.protocol.name.lowercase()
        if (protocol == "http" || protocol == "https") withHttpScheme else null
    }.getOrNull()
}

private fun parseClientBootstrapUrls(raw: String?): List<String> = raw
    ?.split(Regex("[,\n\r\t ]+"))
    .orEmpty()
    .mapNotNull(::normalizedHttpAbsoluteUrlOrNull)
    .filter { bootstrapUrl ->
        normalizedServerUrlHostOrNull(bootstrapUrl) == CANONICAL_AITA_PUBLIC_SERVER_HOST
    }
    .distinct()

private fun parseClientServerUrls(raw: String?): List<String> = raw
    ?.split(Regex("[,\n\r\t ]+"))
    .orEmpty()
    .mapNotNull(::normalizedAutomaticAitaServerUrlOrNull)
    .distinct()

private fun configuredClientBootstrapUrls(): List<String> =
    runtimeClientBootstrapUrlsOverride ?: parseClientBootstrapUrls(DEFAULT_AITA_BOOTSTRAP_URLS)

private fun configuredClientFallbackServerUrls(): List<String> =
    parseClientServerUrls(DEFAULT_AITA_FALLBACK_SERVER_URLS)

fun setRuntimeClientBootstrapUrlsOverride(raw: String?) {
    val urls = parseClientBootstrapUrls(raw)
    runtimeClientBootstrapUrlsOverride = urls.takeIf { it.isNotEmpty() }
    bootstrapServerUrlCandidatesMemory = emptyList()
    bootstrapServerUrlFetchedAtMillis = 0L
    bootstrapServerUrlLastFailureAtMillis = 0L
    if (urls.isNotEmpty()) {
        logNetworkAttempt("bootstrap URL resolver endpoints = ${urls.joinToString()}")
    }
}

fun currentRuntimeClientBootstrapUrlsOverride(): List<String> = runtimeClientBootstrapUrlsOverride.orEmpty()

private fun JsonElement.jsonObjectOrNull(): JsonObject? = runCatching { jsonObject }.getOrNull()

private fun JsonElement.jsonArrayOrNull(): JsonArray? = runCatching { jsonArray }.getOrNull()

private fun JsonElement.jsonStringOrNull(): String? = runCatching { jsonPrimitive.contentOrNull }.getOrNull()

private fun bootstrapServerAddressOrNull(raw: String?): String? {
    val value = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val looksLikeAddress = value.startsWith("http://", ignoreCase = true) ||
        value.startsWith("https://", ignoreCase = true) ||
        value.startsWith("ws://", ignoreCase = true) ||
        value.startsWith("wss://", ignoreCase = true) ||
        (value.none { it.isWhitespace() } && ('.' in value || value.startsWith("localhost", ignoreCase = true)))
    return value.takeIf { looksLikeAddress }?.let(::normalizedHttpServerUrlOrNull)
}

private fun JsonElement.bootstrapUrlValueCandidates(depth: Int): List<String> {
    if (depth > 6) return emptyList()

    jsonStringOrNull()?.trim()?.let { text ->
        if (text.startsWith("{") || text.startsWith("[")) {
            return runCatching {
                jsonBase.parseToJsonElement(text).bootstrapServerUrlCandidates(depth + 1)
            }.getOrDefault(emptyList())
        }
        bootstrapServerAddressOrNull(text)?.let { return listOf(it) }
    }

    jsonArrayOrNull()?.let { array ->
        return array.flatMap { it.bootstrapUrlValueCandidates(depth + 1) }.distinct()
    }

    val obj = jsonObjectOrNull() ?: return emptyList()
    val values = mutableListOf<String>()
    obj["first"]?.bootstrapUrlValueCandidates(depth + 1)?.let(values::addAll)
    values += obj.bootstrapServerUrlCandidates(depth + 1)
    return values.distinct()
}

private fun JsonElement.bootstrapServerUrlCandidates(depth: Int = 0): List<String> {
    if (depth > 6) return emptyList()

    jsonStringOrNull()?.trim()?.let { text ->
        if (text.startsWith("{") || text.startsWith("[")) {
            return runCatching {
                jsonBase.parseToJsonElement(text).bootstrapServerUrlCandidates(depth + 1)
            }.getOrDefault(emptyList())
        }
        bootstrapServerAddressOrNull(text)?.let { return listOf(it) }
    }

    jsonArrayOrNull()?.let { array ->
        return array.flatMap { it.bootstrapUrlValueCandidates(depth + 1) }.distinct()
    }

    val obj = jsonObjectOrNull() ?: return emptyList()
    val candidates = mutableListOf<String>()

    listOf("serverUrl", "currentServerUrl", "url").forEach { key ->
        obj[key]?.bootstrapUrlValueCandidates(depth + 1)?.let(candidates::addAll)
    }
    listOf("serverCandidates", "servers", "fallbackServerUrls").forEach { key ->
        obj[key]?.bootstrapUrlValueCandidates(depth + 1)?.let(candidates::addAll)
    }
    obj["payload"]?.bootstrapUrlValueCandidates(depth + 1)?.let(candidates::addAll)

    return candidates.distinct()
}

@PublishedApi
internal fun decodeBootstrapServerUrlCandidates(rawBody: String): List<String> = runCatching {
    jsonBase.parseToJsonElement(rawBody).bootstrapServerUrlCandidates()
}.getOrDefault(emptyList())

private fun applyBootstrapResolvedServerUrl(normalized: String) {
    val currentConfiguration = globalAppConfigurationState.payloadValue
    val anchoredPair = chooseClientServerUrlPair(
        current = currentConfiguration.serverUrl,
        incoming = Pair(normalized, currentConfiguration.serverUrl.second)
    )
    if (anchoredPair == currentConfiguration.serverUrl) return

    globalAppConfigurationState.emit(
        DataState.Success(
            currentConfiguration.copy(serverUrl = anchoredPair),
            cacheMessage()
        )
    )
}

private suspend fun cachedBootstrapServerUrlCandidatesOrEmpty(
    nowMillis: Long = getCurrentTimeMillis()
): List<String> {
    val cache = getJsonCache<AitaServerBootstrapCacheDataModel>(CACHE_BOOTSTRAP_SERVER_URL)
        ?: return emptyList()
    val ageMillis = nowMillis - cache.fetchedAtMillis
    if (cache.fetchedAtMillis <= 0L || ageMillis !in 0L..AITA_BOOTSTRAP_SERVER_URL_CACHE_MAX_AGE_MILLIS) {
        deleteJsonCache(CACHE_BOOTSTRAP_SERVER_URL)
        return emptyList()
    }

    return (listOf(cache.serverUrl) + cache.serverCandidates)
        .mapNotNull(::normalizedAutomaticAitaServerUrlOrNull)
        .distinct()
}

private suspend fun cachedLastKnownGoodServerUrlOrNull(
    nowMillis: Long = getCurrentTimeMillis()
): String? = lastKnownGoodServerUrlMutex.withLock {
    val lockedMemory = lastKnownGoodServerUrlMemory
    val lockedNormalized = lockedMemory?.let(::normalizedAutomaticAitaServerUrlOrNull)
    val lockedMemoryAgeMillis = nowMillis - lastKnownGoodServerUrlVerifiedAtMillis
    if (lockedNormalized != null && lastKnownGoodServerUrlVerifiedAtMillis > 0L &&
        lockedMemoryAgeMillis in 0L..AITA_LAST_KNOWN_GOOD_SERVER_URL_CACHE_MAX_AGE_MILLIS
    ) {
        return@withLock lockedNormalized
    }

    if (lockedMemory != null && lockedNormalized == null) {
        lastKnownGoodServerUrlMemory = null
        lastKnownGoodServerUrlVerifiedAtMillis = 0L
        lastKnownGoodServerUrlPersistedAtMillis = 0L
        deleteJsonCache(CACHE_LAST_KNOWN_GOOD_SERVER_URL)
    }

    val cache = getJsonCache<AitaLastKnownGoodServerUrlCacheDataModel>(CACHE_LAST_KNOWN_GOOD_SERVER_URL)
    val normalized = cache?.serverUrl?.let(::normalizedAutomaticAitaServerUrlOrNull)
    val cacheAgeMillis = nowMillis - (cache?.verifiedAtMillis ?: 0L)
    if (normalized == null || cache == null || cache.verifiedAtMillis <= 0L ||
        cacheAgeMillis !in 0L..AITA_LAST_KNOWN_GOOD_SERVER_URL_CACHE_MAX_AGE_MILLIS
    ) {
        lastKnownGoodServerUrlMemory = null
        lastKnownGoodServerUrlVerifiedAtMillis = 0L
        lastKnownGoodServerUrlPersistedAtMillis = 0L
        if (cache != null) deleteJsonCache(CACHE_LAST_KNOWN_GOOD_SERVER_URL)
        return@withLock null
    }

    lastKnownGoodServerUrlMemory = normalized
    lastKnownGoodServerUrlVerifiedAtMillis = cache.verifiedAtMillis
    lastKnownGoodServerUrlPersistedAtMillis = cache.verifiedAtMillis
    normalized
}

private suspend fun resolveCurrentServerUrlCandidatesFromBootstrapIfConfigured(
    force: Boolean = false
): List<String> {
    val bootstrapUrls = configuredClientBootstrapUrls()
    if (bootstrapUrls.isEmpty()) return emptyList()

    val now = getCurrentTimeMillis()
    val memory = bootstrapServerUrlCandidatesMemory
    if (!force && memory.isNotEmpty() &&
        now - bootstrapServerUrlFetchedAtMillis in 0L..AITA_BOOTSTRAP_SERVER_URL_REFRESH_INTERVAL_MILLIS
    ) {
        return memory
    }

    if (!force && now - bootstrapServerUrlLastFailureAtMillis in 0L..AITA_BOOTSTRAP_SERVER_URL_FAILURE_BACKOFF_MILLIS) {
        return memory.ifEmpty { cachedBootstrapServerUrlCandidatesOrEmpty(now) }
    }

    return bootstrapServerUrlMutex.withLock {
        val lockedNow = getCurrentTimeMillis()
        val lockedMemory = bootstrapServerUrlCandidatesMemory
        if (!force && lockedMemory.isNotEmpty() &&
            lockedNow - bootstrapServerUrlFetchedAtMillis in 0L..AITA_BOOTSTRAP_SERVER_URL_REFRESH_INTERVAL_MILLIS
        ) {
            return@withLock lockedMemory
        }

        val bootstrapHttpClient = HttpClient(getHttpClientEngine()) {
            install(ContentNegotiation) {
                json(jsonBase)
            }
            install(HttpTimeout) {
                requestTimeoutMillis = AITA_BOOTSTRAP_HTTP_TIMEOUT_MILLIS
                connectTimeoutMillis = AITA_BOOTSTRAP_HTTP_TIMEOUT_MILLIS
                socketTimeoutMillis = AITA_BOOTSTRAP_HTTP_TIMEOUT_MILLIS
            }
            expectSuccess = false
        }

        try {
            for (bootstrapUrl in bootstrapUrls) {
                try {
                    logNetworkAttempt("BOOTSTRAP TRY $bootstrapUrl")
                    val response = bootstrapHttpClient.get(bootstrapUrl) {
                        header(HttpHeaders.CacheControl, "no-cache")
                        header(HttpHeaders.Pragma, "no-cache")
                        currentClientDeviceInfoHeaders().forEach { (key, value) ->
                            safeHttpHeaderValueOrNull(value)?.let { safeValue -> header(key, safeValue) }
                        }
                    }
                    val rawBody = response.bodyAsText()
                    val normalizedServerUrls = decodeBootstrapServerUrlCandidates(rawBody)
                        .mapNotNull(::normalizedAutomaticAitaServerUrlOrNull)
                        .distinct()

                    if (response.status.isSuccess() && normalizedServerUrls.isNotEmpty()) {
                        bootstrapServerUrlCandidatesMemory = normalizedServerUrls
                        bootstrapServerUrlFetchedAtMillis = getCurrentTimeMillis()
                        bootstrapServerUrlLastFailureAtMillis = 0L
                        putJsonCache(
                            CACHE_BOOTSTRAP_SERVER_URL,
                            AitaServerBootstrapCacheDataModel(
                                serverUrl = normalizedServerUrls.first(),
                                serverCandidates = normalizedServerUrls,
                                bootstrapUrl = bootstrapUrl,
                                fetchedAtMillis = bootstrapServerUrlFetchedAtMillis
                            )
                        )
                        applyBootstrapResolvedServerUrl(normalizedServerUrls.first())
                        logNetworkAttempt("BOOTSTRAP RESULT $bootstrapUrl -> ${normalizedServerUrls.joinToString()}")
                        return@withLock normalizedServerUrls
                    }

                    logNetworkAttempt("BOOTSTRAP MISS $bootstrapUrl HTTP ${response.status.value}")
                } catch (throwable: Throwable) {
                    if (throwable is CancellationException) throw throwable
                    logNetworkAttempt("BOOTSTRAP FAILED $bootstrapUrl ${networkFailureSummary(throwable)}")
                }
            }
        } finally {
            bootstrapHttpClient.close()
        }

        bootstrapServerUrlLastFailureAtMillis = getCurrentTimeMillis()
        lockedMemory.ifEmpty { cachedBootstrapServerUrlCandidatesOrEmpty(bootstrapServerUrlLastFailureAtMillis) }
    }
}

fun setRuntimeClientServerUrlOverride(raw: String?) {
    val normalized = normalizedExplicitAitaServerUrlOrNull(raw)
    runtimeClientServerUrlOverride = normalized
    if (normalized == null) return

    val currentConfiguration = globalAppConfigurationState.payloadValue
    globalAppConfigurationState.emit(
        DataState.Success(
            currentConfiguration.copy(serverUrl = Pair(normalized, currentConfiguration.serverUrl.second)),
            cacheMessage()
        )
    )
    logNetworkAttempt("runtime server URL override = $normalized")
}

fun setHiddenClientServerUrlResolutionEnabled(enabled: Boolean) {
    clientVisibleServerUrlFilesOnly = !enabled
}

fun currentRuntimeClientServerUrlOverride(): String? = runtimeClientServerUrlOverride

fun setClientServerUrlFromUserInput(raw: String, refreshNow: Boolean = true): Boolean {
    val normalized = normalizedExplicitAitaServerUrlOrNull(raw)
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

    runtimeClientServerUrlOverride = normalized
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
    val current = normalizedAutomaticAitaServerUrlOrNull(globalAppConfigurationState.payloadValue.serverUrl.first)
    val default = normalizedAutomaticAitaServerUrlOrNull(DEFAULT_AITA_SERVER_URL)
    return current == null || current == default
}

@PublishedApi
internal fun chooseClientServerUrlPair(
    current: Pair<String, String>,
    incoming: Pair<String, String>
): Pair<String, String> {
    val currentAutomatic = normalizedAutomaticAitaServerUrlOrNull(current.first)
    val currentExplicit = normalizedExplicitAitaServerUrlOrNull(current.first)

    // A deliberate localhost/LAN/Tailscale override wins over server-published production config.
    if (currentExplicit != null && currentAutomatic == null) {
        return Pair(currentExplicit, current.second)
    }

    val incomingAutomatic = normalizedAutomaticAitaServerUrlOrNull(incoming.first)
    val canonical = normalizedAutomaticAitaServerUrlOrNull(DEFAULT_AITA_SERVER_URL)
        ?: error("AITA canonical public server URL is invalid")
    val selected = incomingAutomatic ?: currentAutomatic ?: canonical
    val selectedVersion = if (incomingAutomatic != null) incoming.second else current.second

    return if (currentAutomatic == selected && current.first == selected) {
        current
    } else {
        Pair(selected, selectedVersion)
    }
}

@PublishedApi
internal suspend fun resolvedServerUrlCandidates(explicitServerUrl: String? = null): List<String> {
    ensureCachedGlobalConfigurationPrimedForNetwork()

    val explicitNormalized = normalizedExplicitAitaServerUrlOrNull(explicitServerUrl)
    if (explicitNormalized != null) {
        currentNetworkRequestCandidateServerUrlsMemory = listOf(explicitNormalized)
        return listOf(explicitNormalized)
    }

    val runtimeOverrideNormalized = normalizedExplicitAitaServerUrlOrNull(runtimeClientServerUrlOverride)
    if (runtimeOverrideNormalized != null) {
        currentNetworkRequestCandidateServerUrlsMemory = listOf(runtimeOverrideNormalized)
        return listOf(runtimeOverrideNormalized)
    }

    // Purge retired aliases left by older client versions, then use exactly one automatic endpoint.
    cachedLastKnownGoodServerUrlOrNull()
    val canonical = normalizedAutomaticAitaServerUrlOrNull(DEFAULT_AITA_SERVER_URL)
        ?: error("AITA canonical public server URL is invalid")
    val normalizedCandidates = listOf(canonical)

    val previousCandidates = currentNetworkRequestCandidateServerUrlsMemory
    currentNetworkRequestCandidateServerUrlsMemory = normalizedCandidates
    if (previousCandidates != normalizedCandidates) {
        logNetworkAttempt("server URL = $canonical")
    }
    return normalizedCandidates
}

@PublishedApi
internal suspend fun rememberReachableServerUrl(serverUrl: String) {
    val runtimeOverrideNormalized = normalizedExplicitAitaServerUrlOrNull(runtimeClientServerUrlOverride)
    val candidate = normalizedExplicitAitaServerUrlOrNull(serverUrl) ?: return
    val normalized = when {
        runtimeOverrideNormalized != null && candidate == runtimeOverrideNormalized -> candidate
        runtimeOverrideNormalized == null -> normalizedAutomaticAitaServerUrlOrNull(candidate)
        else -> null
    } ?: return

    val now = getCurrentTimeMillis()
    currentNetworkRequestCandidateServerUrlsMemory = listOf(normalized)

    lastKnownGoodServerUrlMutex.withLock {
        val shouldPersist = lastKnownGoodServerUrlMemory != normalized ||
            now - lastKnownGoodServerUrlPersistedAtMillis !in 0L..AITA_BOOTSTRAP_SERVER_URL_REFRESH_INTERVAL_MILLIS
        lastKnownGoodServerUrlMemory = normalized
        lastKnownGoodServerUrlVerifiedAtMillis = now
        if (shouldPersist) {
            try {
                putJsonCache(
                    CACHE_LAST_KNOWN_GOOD_SERVER_URL,
                    AitaLastKnownGoodServerUrlCacheDataModel(normalized, now)
                )
                lastKnownGoodServerUrlPersistedAtMillis = now
            } catch (failure: Throwable) {
                ensureConnectionOwnerActive(failure)
                logNetworkAttempt("last-known-good URL cache write failed; retaining fresh in-memory reachability")
            }
        }
    }

    val currentConfiguration = globalAppConfigurationState.payloadValue
    val currentNormalized = normalizedExplicitAitaServerUrlOrNull(currentConfiguration.serverUrl.first)
    if (currentNormalized != normalized) {
        globalAppConfigurationState.emit(
            DataState.Success(
                currentConfiguration.copy(serverUrl = Pair(normalized, currentConfiguration.serverUrl.second)),
                cacheMessage()
            )
        )
    }
}

@PublishedApi
internal suspend fun forgetReachableServerUrlCandidate(serverUrl: String) {
    val normalized = normalizedExplicitAitaServerUrlOrNull(serverUrl) ?: return
    currentNetworkRequestCandidateServerUrlsMemory =
        currentNetworkRequestCandidateServerUrlsMemory.filterNot { normalizedHttpServerUrlOrNull(it) == normalized }

    var removedRememberedAlias = false
    lastKnownGoodServerUrlMutex.withLock {
        val cached = getJsonCache<AitaLastKnownGoodServerUrlCacheDataModel>(CACHE_LAST_KNOWN_GOOD_SERVER_URL)
        val cachedNormalized = cached?.serverUrl?.let(::normalizedAutomaticAitaServerUrlOrNull)
        if (lastKnownGoodServerUrlMemory == normalized || cachedNormalized == normalized) {
            lastKnownGoodServerUrlMemory = null
            lastKnownGoodServerUrlVerifiedAtMillis = 0L
            lastKnownGoodServerUrlPersistedAtMillis = 0L
            deleteJsonCache(CACHE_LAST_KNOWN_GOOD_SERVER_URL)
            removedRememberedAlias = true
        }
    }

    if (removedRememberedAlias && normalizedExplicitAitaServerUrlOrNull(runtimeClientServerUrlOverride) == null) {
        val defaultNormalized = normalizedAutomaticAitaServerUrlOrNull(DEFAULT_AITA_SERVER_URL) ?: return
        val currentConfiguration = globalAppConfigurationState.payloadValue
        if (normalizedHttpServerUrlOrNull(currentConfiguration.serverUrl.first) == normalized && normalized != defaultNormalized) {
            globalAppConfigurationState.emit(
                DataState.Success(
                    currentConfiguration.copy(serverUrl = Pair(defaultNormalized, currentConfiguration.serverUrl.second)),
                    cacheMessage()
                )
            )
        }
    }
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
        trimmed.contains("\"negative\"")
    ) return true

    if (trimmed.startsWith("{") &&
        trimmed.contains("\"serverUrl\"") &&
        trimmed.contains("\"globalAppConfigurationPath\"")
    ) return true

    if (trimmed.startsWith("{") &&
        trimmed.contains("\"serverUrl\"") &&
        trimmed.contains("\"globalConfigPath\"")
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
        eventMessage("message.can_t_reach_aita_server_check_wi_fi_or_server_address")
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
internal fun HttpStatusCode.isAitaServerUnhealthyForClientBanner(): Boolean = value in 502..504

@PublishedApi
internal fun <Response> ResponseDataModel<Response>.withAitaTransportFailureFromStatus(status: HttpStatusCode): ResponseDataModel<Response> =
    if (status.isAitaServerUnhealthyForClientBanner()) copy(transportFailure = true) else this

@PublishedApi
internal fun cloudEndpointIsPublicReachabilityOnly(endpointUrl: String): Boolean {
    val endpoint = endpointUrl.trim('/').lowercase()
    return endpoint.startsWith("config/") || endpoint.startsWith("res/") || endpoint.startsWith(".well-known/")
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

private const val AITA_NETWORK_VERBOSE_LOGS = false

@PublishedApi
internal fun logNetworkAttempt(message: String) {
    if (AITA_NETWORK_VERBOSE_LOGS || message.startsWith("FAILED", ignoreCase = true)) {
        println("AITA network: $message")
    }
}

@PublishedApi
internal fun cloudEndpointRequiresAuthentication(endpointUrl: String): Boolean {
    val endpoint = endpointUrl.trim('/').lowercase()
    if (endpoint.isBlank()) return false

    return when {
        endpoint == "auth/session" -> true
        endpoint.startsWith("auth/security/") -> true
        endpoint.startsWith("auth/") -> false
        endpoint.startsWith("config/") -> false
        endpoint.startsWith("res/") -> false
        endpoint in setOf("help/tutorials/store", "help/tutorials/buyer", "help/tutorials/supplier", "help/tutorials/manufacturer") -> false
        endpoint.startsWith(".well-known/") -> false
        endpoint == "healthz" || endpoint == "readyz" -> false
        else -> true
    }
}

@PublishedApi
internal fun cloudSessionExpiredMessage(): List<LocalizedStringDataModel> = eventMessage("message.cloud_sign_in_expired_sign_in_again_to_sync_your_local")

@PublishedApi
internal fun <Response> cloudSessionExpiredResponse(
    message: List<LocalizedStringDataModel>? = rejectedAuthRefreshMessageMemory
): ResponseDataModel<Response> = ResponseDataModel(
    message = message ?: cloudSessionExpiredMessage(),
    payload = null,
    negative = true,
    httpStatusCode = HttpStatusCode.Unauthorized.value,
    transportFailure = false
)

@PublishedApi
internal fun rejectedAuthRefreshTokenMatches(refreshToken: String): Boolean =
    refreshToken.isNotBlank() && rejectedAuthRefreshTokenMemory == refreshToken

private fun recentSuccessfulAuthRefreshResponse(
    refreshToken: String,
    nowMillis: Long = getCurrentTimeMillis()
): ResponseDataModel<TokenPair>? {
    val memory = successfulAuthRefreshMemory ?: return null
    if (memory.inputRefreshToken != refreshToken) return null

    val ageMillis = nowMillis - memory.recordedAtMillis
    if (ageMillis !in 0L..AUTH_REFRESH_SUCCESS_CACHE_MILLIS) {
        if (successfulAuthRefreshMemory === memory) {
            successfulAuthRefreshMemory = null
        }
        return null
    }
    return memory.response
}

@PublishedApi
internal fun markCloudAccessTokenValidated(accessToken: String) {
    if (accessToken.isBlank() || getStoredUserAuthTokens?.invoke()?.accessToken != accessToken) return
    validatedCloudAccessTokenMemory = accessToken
    cloudSessionValidationFailureMemory = null

    val current = getStoredUserAuthTokens?.invoke()
    if (current?.accessToken == accessToken && rejectedAuthRefreshTokenMatches(current.refreshToken)) {
        rejectedAuthRefreshTokenMemory = null
        rejectedAuthRefreshMessageMemory = null
    }
}

@PublishedApi
internal fun invalidateCloudAccessTokenValidation(accessToken: String? = null) {
    if (accessToken == null || validatedCloudAccessTokenMemory == accessToken) {
        validatedCloudAccessTokenMemory = null
    }
    val validationFailure = cloudSessionValidationFailureMemory
    if (accessToken == null || validationFailure?.accessToken == accessToken) {
        cloudSessionValidationFailureMemory = null
    }
}

@PublishedApi
internal fun clearCloudAuthRequestMemory(tokens: TokenPair? = null) {
    clearAuthRefreshNonAuthFailure()
    rejectedAuthRefreshTokenMemory = null
    rejectedAuthRefreshMessageMemory = null
    successfulAuthRefreshMemory = null
    cloudSessionValidationFailureMemory = null
    validatedCloudAccessTokenMemory = tokens?.accessToken?.takeIf { it.isNotBlank() }
}

private fun advanceAuthenticatedSessionGenerationLocked(): Long {
    authenticatedSessionGeneration = if (authenticatedSessionGeneration == Long.MAX_VALUE) 1L else authenticatedSessionGeneration + 1L
    resetDeviceFileNotifications(authenticatedSessionGeneration)
    return authenticatedSessionGeneration
}

fun currentAuthenticatedSessionGeneration(): Long = authenticatedSessionGeneration

fun authenticatedSessionGenerationIsCurrent(expectedGeneration: Long): Boolean =
    authenticatedSessionGeneration == expectedGeneration && getStoredUserAuthTokens?.invoke() != null

private fun authenticatedSessionRefreshIsCurrent(
    expectedGeneration: Long,
    expectedRefreshToken: String
): Boolean {
    val current = getStoredUserAuthTokens?.invoke() ?: return false
    return authenticatedSessionGeneration == expectedGeneration &&
        current.refreshToken == expectedRefreshToken
}

@PublishedApi
internal suspend fun installAuthenticatedSession(tokenPair: TokenPair): Long =
    authSessionMutationMutex.withLock {
        val generation = advanceAuthenticatedSessionGenerationLocked()
        setStoredUserAuthTokens?.invoke(tokenPair)
        clearCloudAuthRequestMemory(tokenPair)
        markCloudAccessTokenValidated(tokenPair.accessToken)
        clearCloudSessionRefreshRequirementForNotifications(CLOUD_TRANSPORT_STATUS_REACHABLE)
        markCloudTransportReachableForNotifications(
            authenticated = true,
            authRefreshRequired = false
        )
        httpClient.authProvider<BearerAuthProvider>()?.clearToken()
        generation
    }

@PublishedApi
internal suspend fun installRefreshedAuthenticatedSession(
    expectedGeneration: Long,
    expectedRefreshToken: String,
    tokenPair: TokenPair
): Boolean = authSessionMutationMutex.withLock {
    if (!authenticatedSessionRefreshIsCurrent(expectedGeneration, expectedRefreshToken)) {
        return@withLock false
    }
    setStoredUserAuthTokens?.invoke(tokenPair)
    clearAuthRefreshNonAuthFailure()
    clearCloudAuthRequestMemory(tokenPair)
    markCloudAccessTokenValidated(tokenPair.accessToken)
    clearCloudSessionRefreshRequirementForNotifications(CLOUD_TRANSPORT_STATUS_REACHABLE)
    markCloudTransportReachableForNotifications(
        authenticated = true,
        authRefreshRequired = false
    )
    httpClient.authProvider<BearerAuthProvider>()?.clearToken()
    true
}

@PublishedApi
internal suspend fun clearAuthenticatedSessionStorage(expectedGeneration: Long? = null): TokenPair? =
    authSessionMutationMutex.withLock {
        if (expectedGeneration != null && authenticatedSessionGeneration != expectedGeneration) return@withLock null
        val tokenSnapshot = getStoredUserAuthTokens?.invoke()
        advanceAuthenticatedSessionGenerationLocked()
        setStoredUserAuthTokens?.invoke(null)
        clearCloudAuthRequestMemory(null)
        setStoredUserAccountDataModel?.invoke(null)
        clearCloudSessionRefreshRequirementForNotifications(CLOUD_TRANSPORT_STATUS_UNKNOWN)
        httpClient.authProvider<BearerAuthProvider>()?.clearToken()
        tokenSnapshot
    }

@PublishedApi
internal fun rememberRejectedAuthRefreshToken(
    refreshToken: String,
    message: List<LocalizedStringDataModel>? = null
) {
    // Queued cleanup or a delayed previous login must not expire the replacement session.
    if (refreshToken.isBlank() || getStoredUserAuthTokens?.invoke()?.refreshToken != refreshToken) return
    rejectedAuthRefreshTokenMemory = refreshToken
    rejectedAuthRefreshMessageMemory = message ?: cloudSessionExpiredMessage()
    validatedCloudAccessTokenMemory = null
    cloudSessionValidationFailureMemory = null
    clearAuthRefreshNonAuthFailure()
    markCloudSessionNeedsRefreshForNotifications()
}

@PublishedApi
internal fun currentCloudSessionIsReadyForBackgroundSync(): Boolean {
    val tokens = getStoredUserAuthTokens?.invoke() ?: return false
    return tokens.accessToken.isNotBlank() &&
        validatedCloudAccessTokenMemory == tokens.accessToken &&
        tokens.accessTokenIsStillUsableForNetwork()
}

@PublishedApi
internal fun recentCloudSessionValidationFailure(accessToken: String): ResponseDataModel<Unit>? {
    val remembered = cloudSessionValidationFailureMemory ?: return null
    if (remembered.accessToken != accessToken) return null
    if (getCurrentTimeMillis() - remembered.recordedAtMillis !in 0L..CLOUD_SESSION_VALIDATION_FAILURE_CACHE_MILLIS) {
        cloudSessionValidationFailureMemory = null
        return null
    }
    return remembered.response
}

@PublishedApi
internal fun rememberCloudSessionValidationFailure(
    accessToken: String,
    response: ResponseDataModel<Unit>
) {
    if (accessToken.isBlank()) return
    cloudSessionValidationFailureMemory = CloudSessionValidationFailureMemory(
        accessToken = accessToken,
        recordedAtMillis = getCurrentTimeMillis(),
        response = response
    )
}

@PublishedApi
internal fun <Response> cloudSessionValidationFailureForNetworkRequest(
    response: ResponseDataModel<Unit>
): ResponseDataModel<Response> = ResponseDataModel(
    message = response.message,
    payload = null,
    negative = true,
    httpStatusCode = response.httpStatusCode,
    transportFailure = response.transportFailure
)

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
        markCloudTransportReachableForNotifications(authenticated = false, authRefreshRequired = null)
    }

    return ResponseDataModel(
        message = message,
        payload = null,
        negative = true,
        httpStatusCode = HttpStatusCode.ServiceUnavailable.value,
        transportFailure = transportFailure
    )
}

@PublishedApi
internal fun HttpMethod.canRetryAcrossAitaServerAliases(): Boolean =
    this == HttpMethod.Get || this == HttpMethod.Head || this == HttpMethod.Options

@Suppress("UNUSED_PARAMETER")
@PublishedApi
internal fun shouldRetryNetworkRequestOnNextServerUrl(
    method: HttpMethod,
    endpointUrl: String,
    status: HttpStatusCode,
    rawBody: String,
    aitaServerResponse: Boolean = false
): Boolean {
    if (!method.canRetryAcrossAitaServerAliases()) return false
    if (!aitaServerResponse) return true
    if (status == HttpStatusCode.RequestTimeout || status.isAitaServerUnhealthyForClientBanner()) return true

    val compactBody = rawBody.filterNot { it.isWhitespace() }.lowercase()
    return compactBody.contains("\"transportfailure\":true")
}

private data class AitaServerProbeSelection(
    val serverUrl: String?,
    val response: ResponseDataModel<Unit>
)

private suspend fun probeReachableAitaServerUrl(
    probeHttpClient: HttpClient,
    serverUrlCandidates: List<String>,
    endpointUrl: String,
    reason: String
): AitaServerProbeSelection {
    var lastFailure: ResponseDataModel<Unit>? = null

    for (resolvedServerUrl in serverUrlCandidates.distinct()) {
        val requestUrl = networkTargetUrl(resolvedServerUrl, endpointUrl)
        val probeStarted = getCurrentTimeMillis()
        try {
            logNetworkAttempt("TRY ${HttpMethod.Get.value} $requestUrl probe=$reason")
            val response = probeHttpClient.request(requestUrl) {
                method = HttpMethod.Get
                timeout {
                    requestTimeoutMillis = AITA_BOOTSTRAP_HTTP_TIMEOUT_MILLIS
                    connectTimeoutMillis = AITA_BOOTSTRAP_HTTP_TIMEOUT_MILLIS
                    socketTimeoutMillis = AITA_BOOTSTRAP_HTTP_TIMEOUT_MILLIS
                }
                attributes.put(AuthCircuitBreaker, Unit)
                header(AITA_CONNECTION_PROBE_HEADER, "1")
                header(HttpHeaders.CacheControl, "no-cache")
                header(HttpHeaders.Pragma, "no-cache")
                parameter("silent", "true")
                parameter("probe_reason", reason.take(80))
                currentClientDeviceInfoHeaders().forEach { (key, value) ->
                    safeHttpHeaderValueOrNull(value)?.let { safeValue -> header(key, safeValue) }
                }
            }
            val rawBody = response.bodyAsText()
            val aitaServerResponse = response.isAitaServerResponse(rawBody)
            recordConnectionProbe(probeStarted, resolvedServerUrl, response.status.value, aitaServerResponse,
                originError = !response.headers["x-aita-origin-error"].isNullOrBlank())
            logNetworkAttempt(
                "RESULT ${HttpMethod.Get.value} $requestUrl HTTP ${response.status.value} " +
                    "aita=$aitaServerResponse probe=$reason"
            )

            if (!aitaServerResponse) {
                lastFailure = nonAitaHttpResponseDataModel(response.status, rawBody, resolvedServerUrl)
                forgetReachableServerUrlCandidate(resolvedServerUrl)
                continue
            }

            if (response.status.isSuccess()) {
                rememberReachableServerUrl(resolvedServerUrl)
                return AitaServerProbeSelection(
                    serverUrl = resolvedServerUrl,
                    response = ResponseDataModel(
                        message = null,
                        payload = Unit,
                        negative = false,
                        httpStatusCode = response.status.value,
                        transportFailure = false
                    )
                )
            }

            val decodedFailure = decodeNetworkResponseDataModel<Unit>(rawBody, response.status)
                .withAitaTransportFailureFromStatus(response.status)
            lastFailure = decodedFailure
            if (decodedFailure.transportFailure) {
                forgetReachableServerUrlCandidate(resolvedServerUrl)
            }
        } catch (throwable: Throwable) {
            ensureConnectionOwnerActive(throwable)
            recordConnectionProbe(probeStarted, resolvedServerUrl, null, false, failureSummary = networkFailureSummary(throwable))
            logNetworkAttempt(
                "FAILED ${HttpMethod.Get.value} $requestUrl probe=$reason ${networkFailureSummary(throwable)}"
            )
            forgetReachableServerUrlCandidate(resolvedServerUrl)
            lastFailure = ResponseDataModel(
                message = networkTransportFailureMessage(resolvedServerUrl, endpointUrl, throwable),
                payload = null,
                negative = true,
                httpStatusCode = null,
                transportFailure = true
            )
        }
    }

    return AitaServerProbeSelection(
        serverUrl = null,
        response = lastFailure ?: ResponseDataModel(
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
    )
}

@PublishedApi
internal suspend fun selectServerUrlForNonReplayableRequest(
    serverUrlCandidates: List<String>,
    reason: String,
    probeHttpClient: HttpClient = httpClient
): ResponseDataModel<String> {
    val normalizedCandidates = serverUrlCandidates.mapNotNull(::normalizedHttpServerUrlOrNull).distinct()
    val recentlyVerified = lastKnownGoodServerUrlMemory
        ?.let(::normalizedHttpServerUrlOrNull)
        ?.takeIf { candidate ->
            candidate in normalizedCandidates &&
                getCurrentTimeMillis() - lastKnownGoodServerUrlVerifiedAtMillis in
                0L..AITA_NON_REPLAYABLE_MUTATION_PROOF_MAX_AGE_MILLIS
        }
    if (recentlyVerified != null) {
        return ResponseDataModel(
            message = null,
            payload = recentlyVerified,
            negative = false,
            httpStatusCode = HttpStatusCode.OK.value,
            transportFailure = false
        )
    }

    val probeEndpoint = globalAppConfigurationState.payloadValue.connectionCheckPath.first.trimStart('/')
    val probe = probeReachableAitaServerUrl(
        probeHttpClient = probeHttpClient,
        serverUrlCandidates = normalizedCandidates,
        endpointUrl = probeEndpoint,
        reason = reason
    )
    return ResponseDataModel(
        message = probe.response.message,
        payload = probe.serverUrl,
        negative = probe.serverUrl == null,
        httpStatusCode = probe.response.httpStatusCode,
        transportFailure = probe.serverUrl == null
    )
}

private suspend fun performAuthTokenRefreshNetworkRequest(
    refreshToken: String
): ResponseDataModel<TokenPair> {
    ensureCachedGlobalConfigurationPrimedForNetwork()

    val currentConfiguration = globalAppConfigurationState.payloadValue
    val refreshEndpoint = currentConfiguration.refreshPath.first.trimStart('/')
    val serverUrlCandidates = resolvedServerUrlCandidates(null)

    val refreshHttpClient = HttpClient(getHttpClientEngine()) {
        install(ContentNegotiation) {
            json(jsonBase)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000L
            connectTimeoutMillis = 10_000L
            socketTimeoutMillis = 30_000L
        }
        expectSuccess = false
    }

    return try {
        // A refresh token rotates when used. Select one proven alias with a harmless GET only when
        // there is no recent last-known-good endpoint, then emit the refresh POST exactly once.
        val selection = selectServerUrlForNonReplayableRequest(
            serverUrlCandidates = serverUrlCandidates,
            reason = "auth_refresh",
            probeHttpClient = refreshHttpClient
        )
        val resolvedServerUrl = selection.payload
        if (resolvedServerUrl == null) {
            markCloudTransportUnavailableForNotifications(reason = "auth_refresh_alias_selection")
            val failure = ResponseDataModel<TokenPair>(
                message = selection.message,
                payload = null,
                negative = true,
                httpStatusCode = selection.httpStatusCode,
                transportFailure = true
            )
            return failure
        }

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
            logNetworkAttempt(
                "RESULT ${HttpMethod.Post.value} $requestUrl HTTP ${httpResponse.status.value} aita=$aitaServerResponse"
            )

            if (!aitaServerResponse) {
                val nonAitaResponse = nonAitaHttpResponseDataModel<TokenPair>(
                    httpResponse.status,
                    rawBody,
                    resolvedServerUrl
                )
                forgetReachableServerUrlCandidate(resolvedServerUrl)
                markCloudTransportUnavailableForNotifications(reason = "auth_refresh_non_aita_response")
                return nonAitaResponse
            }

            val decodedRefreshResponse = decodeNetworkResponseDataModel<TokenPair>(rawBody, httpResponse.status)
                .withAitaTransportFailureFromStatus(httpResponse.status)

            if (httpResponse.status.isAitaServerUnhealthyForClientBanner() || decodedRefreshResponse.transportFailure) {
                val failureResponse = decodedRefreshResponse.copy(transportFailure = true)
                forgetReachableServerUrlCandidate(resolvedServerUrl)
                markCloudTransportUnavailableForNotifications(reason = "auth_refresh_server_unhealthy")
                logCloudConnectionDiagnostic(
                    "auth refresh response treated as unavailable http=${httpResponse.status.value} " +
                        "aita=$aitaServerResponse negative=${failureResponse.negative}"
                )
                return failureResponse
            }

            if (cloudResponseCanMarkReachable(refreshEndpoint, httpResponse.status)) {
                rememberReachableServerUrl(resolvedServerUrl)
                markCloudTransportReachableForNotifications(
                    authenticated = false,
                    authRefreshRequired = null
                )
            }

            if (httpResponse.status == HttpStatusCode.Unauthorized) {
                return cloudSessionExpiredResponse()
            }

            decodedRefreshResponse
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            forgetReachableServerUrlCandidate(resolvedServerUrl)
            markCloudTransportUnavailableForNotifications(reason = "auth_refresh_transport_failure")
            val failure = ResponseDataModel<TokenPair>(
                message = networkTransportFailureMessage(resolvedServerUrl, refreshEndpoint, throwable),
                payload = null,
                negative = true,
                httpStatusCode = null,
                transportFailure = true
            )
            logNetworkAttempt(
                "FAILED ${networkTargetUrl(resolvedServerUrl, refreshEndpoint)} ${networkFailureSummary(throwable)}"
            )
            failure
        }
    } finally {
        refreshHttpClient.close()
    }
}

@PublishedApi
internal suspend fun refreshAuthTokensWithServerFallback(
    refreshToken: String,
    forceRejectedRetry: Boolean = false,
    expectedSessionGeneration: Long? = null
): ResponseDataModel<TokenPair> {
    val invocationGeneration = expectedSessionGeneration ?: currentAuthenticatedSessionGeneration()
    if (refreshToken.isBlank()) return cloudSessionExpiredResponse()
    if (expectedSessionGeneration != null && !authenticatedSessionGenerationIsCurrent(expectedSessionGeneration)) {
        return cloudSessionExpiredResponse()
    }

    recentSuccessfulAuthRefreshResponse(refreshToken)?.let { return it }

    if (!forceRejectedRetry && rejectedAuthRefreshTokenMatches(refreshToken)) {
        return cloudSessionExpiredResponse()
    }

    return authRefreshNetworkMutex.withLock {
        if (expectedSessionGeneration != null && !authenticatedSessionGenerationIsCurrent(expectedSessionGeneration)) {
            return@withLock cloudSessionExpiredResponse()
        }
        recentSuccessfulAuthRefreshResponse(refreshToken)?.let { return@withLock it }

        if (!forceRejectedRetry && rejectedAuthRefreshTokenMatches(refreshToken)) {
            return@withLock cloudSessionExpiredResponse()
        }

        val response = performAuthTokenRefreshNetworkRequest(refreshToken)
        // Session installation/logout uses this same lock. Response bookkeeping cannot run
        // between a new token installation and its memory reset, even on another dispatcher.
        authSessionMutationMutex.withLock bookkeeping@ {
            if (expectedSessionGeneration != null && !authenticatedSessionGenerationIsCurrent(expectedSessionGeneration)) {
                return@bookkeeping cloudSessionExpiredResponse()
            }
            val ownsCurrentSession = authenticatedSessionRefreshIsCurrent(invocationGeneration, refreshToken)
            val refreshedTokens = response.payload
            if (!response.negative && refreshedTokens != null) {
                successfulAuthRefreshMemory = SuccessfulAuthRefreshMemory(
                    inputRefreshToken = refreshToken,
                    recordedAtMillis = getCurrentTimeMillis(),
                    response = response
                )
                // Validation/readiness is published only by installRefreshedAuthenticatedSession.
                if (ownsCurrentSession) clearAuthRefreshNonAuthFailure()
            } else if (ownsCurrentSession) {
                if (response.httpStatusCode == HttpStatusCode.Unauthorized.value) {
                    rememberRejectedAuthRefreshToken(refreshToken, response.message)
                } else {
                    rememberAuthRefreshNonAuthFailure(response.message, response.transportFailure)
                }
            }
            response
        }
    }
}

@PublishedApi
internal suspend fun refreshStoredAuthTokensOnceForNetworkRetry(
    postNotification: Boolean = true,
    forceRejectedRetry: Boolean = false,
    expectedSessionGeneration: Long? = null
): Boolean = tokenRefreshMutex.withLock {
    if (expectedSessionGeneration != null && !authenticatedSessionGenerationIsCurrent(expectedSessionGeneration)) return@withLock false
    val current = getStoredUserAuthTokens?.invoke() ?: return@withLock false
    val sessionGeneration = currentAuthenticatedSessionGeneration()

    if (!forceRejectedRetry && rejectedAuthRefreshTokenMatches(current.refreshToken)) {
        markCloudSessionNeedsRefreshForNotifications()
        if (postNotification) {
            postInAppNotification(
                rejectedAuthRefreshMessageMemory ?: cloudSessionExpiredMessage(),
                NotificationType.Neutral
            )
        }
        return@withLock false
    }

    val refreshResponse = refreshAuthTokensWithServerFallback(
        refreshToken = current.refreshToken,
        forceRejectedRetry = forceRejectedRetry,
        expectedSessionGeneration = sessionGeneration
    )

    if (!authenticatedSessionRefreshIsCurrent(sessionGeneration, current.refreshToken)) return@withLock false

    refreshResponse.payload?.let { refreshedTokens ->
        return@withLock installRefreshedAuthenticatedSession(
            expectedGeneration = sessionGeneration,
            expectedRefreshToken = current.refreshToken,
            tokenPair = refreshedTokens
        )
    }

    when {
        refreshResponse.transportFailure -> {
            markCloudTransportUnavailableForNotifications(reason = "stored_auth_refresh_transport_failure")
        }

        refreshResponse.httpStatusCode == HttpStatusCode.Unauthorized.value -> {
            if (postNotification) {
                postInAppNotification(
                    refreshResponse.message ?: cloudSessionExpiredMessage(),
                    NotificationType.Neutral
                )
            }
        }

        else -> {
            // Reachability and authentication are independent. A reachable 500/409 from refresh
            // must not erase a previously grounded session-expired state.
            markCloudTransportReachableForNotifications(authenticated = false, authRefreshRequired = null)
        }
    }

    false
}

private fun successfulCloudSessionValidationResponse(): ResponseDataModel<Unit> = ResponseDataModel(
    message = null,
    payload = Unit,
    negative = false,
    httpStatusCode = HttpStatusCode.OK.value,
    transportFailure = false
)

private suspend fun performCloudAccessTokenValidation(
    accessToken: String,
    sessionGeneration: Long
): ResponseDataModel<Unit> {
    ensureCachedGlobalConfigurationPrimedForNetwork()
    val endpoint = "auth/session"
    val candidates = resolvedServerUrlCandidates(null)
    if (candidates.isEmpty()) {
        return ResponseDataModel(
            message = networkTransportFailureMessage("", endpoint),
            payload = null,
            negative = true,
            httpStatusCode = null,
            transportFailure = true
        )
    }

    val validationHttpClient = HttpClient(getHttpClientEngine()) {
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000L
            connectTimeoutMillis = 8_000L
            socketTimeoutMillis = 15_000L
        }
        expectSuccess = false
    }

    var lastFailure: ResponseDataModel<Unit>? = null
    return try {
        for ((index, serverUrl) in candidates.withIndex()) {
            val requestUrl = networkTargetUrl(serverUrl, endpoint)
            try {
                logNetworkAttempt("TRY ${HttpMethod.Get.value} $requestUrl probe=auth_session")
                val response = validationHttpClient.request(requestUrl) {
                    method = HttpMethod.Get
                    header(HttpHeaders.Authorization, "Bearer $accessToken")
                    header(HttpHeaders.CacheControl, "no-cache")
                    header(HttpHeaders.Pragma, "no-cache")
                    currentClientDeviceInfoHeaders().forEach { (key, value) ->
                        safeHttpHeaderValueOrNull(value)?.let { safeValue -> header(key, safeValue) }
                    }
                }
                val rawBody = response.bodyAsText()
                if (!authenticatedSessionGenerationIsCurrent(sessionGeneration)) return cloudSessionExpiredResponse()
                val aitaServerResponse = response.isAitaServerResponse(rawBody)
                logNetworkAttempt(
                    "RESULT ${HttpMethod.Get.value} $requestUrl HTTP ${response.status.value} " +
                        "aita=$aitaServerResponse probe=auth_session"
                )

                if (!aitaServerResponse) {
                    lastFailure = nonAitaHttpResponseDataModel(response.status, rawBody, serverUrl)
                    forgetReachableServerUrlCandidate(serverUrl)
                    if (index < candidates.lastIndex) continue
                    return lastFailure ?: ResponseDataModel(
                        message = networkTransportFailureMessage(serverUrl, endpoint),
                        payload = null,
                        negative = true,
                        transportFailure = true
                    )
                }

                val decoded = decodeNetworkResponseDataModel<Unit>(rawBody, response.status)
                    .withAitaTransportFailureFromStatus(response.status)
                if (response.status.isAitaServerUnhealthyForClientBanner() || decoded.transportFailure) {
                    lastFailure = decoded.copy(transportFailure = true)
                    forgetReachableServerUrlCandidate(serverUrl)
                    markCloudTransportUnavailableForNotifications(reason = "auth_session_server_unhealthy")
                    if (index < candidates.lastIndex) continue
                    return lastFailure ?: decoded
                }

                rememberReachableServerUrl(serverUrl)
                return when {
                    response.status.isSuccess() -> {
                        val ownsToken = getStoredUserAuthTokens?.invoke()?.accessToken == accessToken
                        markCloudTransportReachableForNotifications(authenticated = ownsToken, authRefreshRequired = if (ownsToken) false else null)
                        ResponseDataModel(
                            message = decoded.message,
                            payload = Unit,
                            negative = false,
                            httpStatusCode = response.status.value,
                            transportFailure = false
                        )
                    }

                    response.status == HttpStatusCode.Unauthorized -> {
                        // Do not publish a session warning yet. The caller gets exactly one chance to
                        // rotate the refresh token first; only a rejected refresh grounds that state.
                        markCloudTransportReachableForNotifications(authenticated = false, authRefreshRequired = null)
                        cloudSessionExpiredResponse()
                    }

                    else -> {
                        markCloudTransportReachableForNotifications(authenticated = false, authRefreshRequired = null)
                        decoded
                    }
                }
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                forgetReachableServerUrlCandidate(serverUrl)
                lastFailure = ResponseDataModel(
                    message = networkTransportFailureMessage(serverUrl, endpoint, throwable),
                    payload = null,
                    negative = true,
                    httpStatusCode = null,
                    transportFailure = true
                )
                logNetworkAttempt(
                    "FAILED ${HttpMethod.Get.value} $requestUrl probe=auth_session ${networkFailureSummary(throwable)}"
                )
            }
        }

        markCloudTransportUnavailableForNotifications(reason = "auth_session_transport_failure")
        lastFailure ?: ResponseDataModel(
            message = networkTransportFailureMessage(candidates.firstOrNull().orEmpty(), endpoint),
            payload = null,
            negative = true,
            httpStatusCode = null,
            transportFailure = true
        )
    } finally {
        validationHttpClient.close()
    }
}

private fun currentCloudSessionFailureResponse(): ResponseDataModel<Unit> {
    val current = getStoredUserAuthTokens?.invoke()
    if (current != null && rejectedAuthRefreshTokenMatches(current.refreshToken)) {
        return cloudSessionExpiredResponse()
    }

    recentAuthRefreshNonAuthFailureMessage()?.let { message ->
        val transportFailure = recentAuthRefreshNonAuthFailureWasTransport()
        return ResponseDataModel(
            message = message,
            payload = null,
            negative = true,
            httpStatusCode = HttpStatusCode.ServiceUnavailable.value,
            transportFailure = transportFailure
        )
    }

    if (cloudTransportStatusState.value == CLOUD_TRANSPORT_STATUS_UNAVAILABLE) {
        return ResponseDataModel(
            message = localizedStringResourceMessage(
                id = 214,
                main = "Cannot reach server. Keeping you signed in offline.",
                ru = "Сервер недоступен. Вы остаётесь в аккаунте офлайн.",
                kk = "Сервер қолжетімсіз. Сіз офлайн режимде аккаунтта қаласыз."
            ),
            payload = null,
            negative = true,
            httpStatusCode = HttpStatusCode.ServiceUnavailable.value,
            transportFailure = true
        )
    }

    return cloudSessionExpiredResponse()
}

@PublishedApi
internal suspend fun ensureCloudSessionReadyForProtectedRequest(
    forceRejectedRefreshRetry: Boolean = false,
    retryAfterTransportRecovery: Boolean = false
): ResponseDataModel<Unit> {
    val sessionGeneration = currentAuthenticatedSessionGeneration()
    val initialTokens = getStoredUserAuthTokens?.invoke() ?: return cloudSessionExpiredResponse()
    if (!forceRejectedRefreshRetry && rejectedAuthRefreshTokenMatches(initialTokens.refreshToken)) {
        return cloudSessionExpiredResponse()
    }
    val initialAccessToken = initialTokens.accessToken
    if (initialAccessToken.isNotBlank() &&
        validatedCloudAccessTokenMemory == initialAccessToken &&
        initialTokens.accessTokenIsStillUsableForNetwork()
    ) {
        return successfulCloudSessionValidationResponse()
    }
    recentCloudSessionValidationFailure(initialAccessToken)?.let {
        if (!retryAfterTransportRecovery || !it.transportFailure) return it
    }

    return cloudSessionValidationMutex.withLock {
        if (!authenticatedSessionGenerationIsCurrent(sessionGeneration)) return@withLock cloudSessionExpiredResponse()
        var current = getStoredUserAuthTokens?.invoke() ?: return@withLock cloudSessionExpiredResponse()
        if (!forceRejectedRefreshRetry && rejectedAuthRefreshTokenMatches(current.refreshToken)) {
            return@withLock cloudSessionExpiredResponse()
        }

        if (current.accessToken.isNotBlank() &&
            validatedCloudAccessTokenMemory == current.accessToken &&
            current.accessTokenIsStillUsableForNetwork()
        ) {
            return@withLock successfulCloudSessionValidationResponse()
        }
        recentCloudSessionValidationFailure(current.accessToken)?.let {
            if (!retryAfterTransportRecovery || !it.transportFailure) return@withLock it
        }

        // Refresh is mandatory only after the access token is actually unusable. Refreshing merely
        // because a valid token is near its expiry created a fan-out at startup and made an otherwise
        // usable local/cloud session look broken when the refresh token had already been revoked.
        if (current.accessToken.isBlank() || !current.accessTokenIsStillUsableForNetwork()) {
            if (!forceRejectedRefreshRetry && rejectedAuthRefreshTokenMatches(current.refreshToken)) {
                return@withLock cloudSessionExpiredResponse()
            }

            val refreshed = refreshStoredAuthTokensOnceForNetworkRetry(
                postNotification = false,
                forceRejectedRetry = forceRejectedRefreshRetry,
                expectedSessionGeneration = sessionGeneration
            )
            if (!authenticatedSessionGenerationIsCurrent(sessionGeneration)) return@withLock cloudSessionExpiredResponse()
            if (!refreshed) return@withLock currentCloudSessionFailureResponse()
            current = getStoredUserAuthTokens?.invoke() ?: return@withLock cloudSessionExpiredResponse()
            if (validatedCloudAccessTokenMemory == current.accessToken && current.accessTokenIsStillUsableForNetwork()) {
                return@withLock successfulCloudSessionValidationResponse()
            }
        }

        val validation = performCloudAccessTokenValidation(current.accessToken, sessionGeneration)
        if (!authenticatedSessionGenerationIsCurrent(sessionGeneration)) return@withLock cloudSessionExpiredResponse()
        if (getStoredUserAuthTokens?.invoke()?.accessToken != current.accessToken) {
            return@withLock if (currentCloudSessionIsReadyForBackgroundSync()) successfulCloudSessionValidationResponse()
                else currentCloudSessionFailureResponse()
        }
        if (!validation.negative && validation.httpStatusCode?.let { it in 200..299 } == true) {
            markCloudAccessTokenValidated(current.accessToken)
            clearCloudSessionRefreshRequirementForNotifications(CLOUD_TRANSPORT_STATUS_REACHABLE)
            return@withLock successfulCloudSessionValidationResponse()
        }

        if (validation.httpStatusCode == HttpStatusCode.Unauthorized.value) {
            invalidateCloudAccessTokenValidation(current.accessToken)
            if (!forceRejectedRefreshRetry && rejectedAuthRefreshTokenMatches(current.refreshToken)) {
                return@withLock cloudSessionExpiredResponse()
            }

            val refreshed = refreshStoredAuthTokensOnceForNetworkRetry(
                postNotification = false,
                forceRejectedRetry = forceRejectedRefreshRetry,
                expectedSessionGeneration = sessionGeneration
            )
            if (!authenticatedSessionGenerationIsCurrent(sessionGeneration)) return@withLock cloudSessionExpiredResponse()
            if (refreshed) return@withLock successfulCloudSessionValidationResponse()
            return@withLock currentCloudSessionFailureResponse()
        }

        rememberCloudSessionValidationFailure(current.accessToken, validation)
        validation
    }
}

private const val LOCAL_NETWORK_DEFAULT_TCP_PORT = 45720
private const val LOCAL_NETWORK_DEFAULT_DISCOVERY_PORT = 45721
private const val LOCAL_NETWORK_STALE_DEVICE_MILLIS = 45_000L

private fun storeScopedCacheKey(name: String, storeId: String): String = "$name:$storeId"

private suspend inline fun <reified T> putJsonCache(key: String, value: T) {
    try {
        writeJsonCacheText(CACHE_PREFIX + key, jsonBase.encodeToString(value))
    } catch (throwable: Throwable) {
        if (throwable is CancellationException) throw throwable
    }
}

private suspend inline fun <reified T> getJsonCache(key: String): T? {
    return try {
        readJsonCacheText(CACHE_PREFIX + key)?.let { jsonBase.decodeFromString<T>(it) }
    } catch (throwable: Throwable) {
        if (throwable is CancellationException) throw throwable
        null
    }
}

private suspend fun deleteJsonCache(key: String) {
    try {
        deleteJsonCacheText(CACHE_PREFIX + key)
    } catch (throwable: Throwable) {
        if (throwable is CancellationException) throw throwable
    }
}

private suspend fun clearAuthenticatedAccountCaches() {
    AUTHENTICATED_ACCOUNT_CACHE_KEYS.forEach { key ->
        deleteJsonCache(key)
    }
}

private suspend inline fun <reified T> putAuthenticatedJsonCache(key: String, value: T) {
    if (hasStoredAuthenticatedSession()) {
        putJsonCache(key, value)
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
        deleteJsonCache(CACHE_PENDING_SESSION_CLEANUPS)
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
        deleteJsonCache(CACHE_PENDING_WORKSHIFT_ENDS)
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
            requestTimeoutMillis = 30_000L
            connectTimeoutMillis = 10_000L
            socketTimeoutMillis = 30_000L
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
                        main = "Cloud sign-in expired. Sign in again to sync. Your local data stays available.",
                        ru = "Срок облачного входа истёк. Войдите снова для синхронизации. Локальные данные останутся доступны.",
                        kk = "Бұлттық кіру мерзімі аяқталды. Синхрондау үшін қайта кіріңіз. Жергілікті деректер қолжетімді болып қалады."
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
    putJsonCache(CACHE_TRANSACTION_RETURN_BATCH_SELECTIONS, cartReturnBatchSelectionsState.value)
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
    getJsonCache<Map<String, CartReturnBatchSelectionDataModel>>(CACHE_TRANSACTION_RETURN_BATCH_SELECTIONS)?.let { cached ->
        cartReturnBatchSelectionsState.emit(
            cached.mapValues { (_, value) ->
                value.copy(
                    stockBatchId = value.stockBatchId?.trim()?.takeIf { it.isNotBlank() },
                    pricePerUnit = value.pricePerUnit?.coerceAtLeast(0.0)?.roundMoney(),
                    currencyCode = value.currencyCode.trim().uppercase()
                )
            }.filterKeys { it.startsWith("1:") }
        )
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

private fun localNetworkSnapshot(): LocalNetworkSnapshotDataModel? {
    val owner = inventoryOwners.current
    val storeId = owner.storeId ?: return null
    if (!inventoryOwnerIsCurrent(owner) || !currentStoreHasSubscriptionAccess(storeId)) return null
    return LocalNetworkSnapshotDataModel(
        storeId = storeId,
        stock = stockState.payloadValue.orEmpty(),
        stockBatches = stockBatchesState.payloadValue.orEmpty(),
        stockLoaded = stockState.payloadValue != null,
        stockBatchesLoaded = stockBatchesState.payloadValue != null,
        transactions = transactionsState.payloadValue.orEmpty(),
        debtors = debtorsState.payloadValue.orEmpty(),
        cashRegister = cashRegisterState.payloadValue,
        updatedAtMillis = getCurrentTimeMillis()
    ).takeIf { inventoryOwnerIsCurrent(owner) && currentStoreHasSubscriptionAccess(storeId) }
}

private suspend fun applyLocalNetworkSnapshot(snapshot: LocalNetworkSnapshotDataModel) {
    val message = localNetworkMessage(734, "Local branch state updated", "Локальное состояние филиала обновлено", "Филиалдың жергілікті күйі жаңартылды")
    val owner = inventoryOwners.current
    if (snapshot.storeId != owner.storeId || !inventoryOwnerIsCurrent(owner) ||
        !currentStoreHasSubscriptionAccess(snapshot.storeId)) return
    val stock = if (snapshot.stockLoaded) filterRecentlyDeletedStockItems(snapshot.stock) else null
    val batches = if (snapshot.stockBatchesLoaded) filterRecentlyDeletedStockBatches(snapshot.stockBatches) else null
    inventoryStateMutex.withLock {
        if (!inventoryOwnerIsCurrent(owner) || !currentStoreHasSubscriptionAccess(snapshot.storeId)) return
        stock?.let {
            stockState.emit(DataState.Success(it, message))
            stockLoadStatusState.value = InventoryLoadStatus(owner.storeId, source = InventoryLoadSource.Local, cacheChecked = true)
        }
        batches?.let {
            stockBatchesState.emit(DataState.Success(it, message))
            stockBatchesLoadStatusState.value = InventoryLoadStatus(owner.storeId, source = InventoryLoadSource.Local, cacheChecked = true)
        }
        transactionsState.emit(DataState.Success(snapshot.transactions, message))
        snapshot.transactions.forEach(::reconcileLatestReceiptIdentity)
        debtorsState.emit(DataState.Success(snapshot.debtors, message))
        snapshot.cashRegister?.let {
            cashRegisterState.emit(DataState.Success(it, message))
            cashRegisterAmountState.emit(it.currentAmount)
        }
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
    val owner = inventoryOwners.current
    val snapshot = localNetworkSnapshot() ?: return
    val envelope = localNetworkEnvelope("state", snapshot = snapshot)
    val text = jsonBase.encodeToString(envelope)
    localNetworkDevicesState.value
        .filter { it.deviceId != state.deviceId && it.host.isNotBlank() }
        .forEach { device ->
            GlobalScope.launch(Dispatchers.ourIo) {
                if (inventoryOwnerIsCurrent(owner) && currentStoreHasSubscriptionAccess(snapshot.storeId)) {
                    runCatching { LocalAitaLanTransport.send(device.host, device.port, text, 1500) }
                }
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
                        line.returnReason.trim(),
                        line.stockBatchId.orEmpty()
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
        "return", "accept", "supply", "acceptance" -> line.quantity
        else -> 0.0
    }
    if (delta == 0.0) return batches

    val candidateIds = mutableListOf<String>().apply {
        line.stockBatchId?.takeIf { it.isNotBlank() }?.let { add(it) }
        item.activeShelfBatchId?.let { add(it) }
        addAll(batches.filter { it.goodsItemId == item.id && it.isActive }.sortedBy { it.shelfPriority }.map { it.id })
    }.distinct()
    val targetId = candidateIds.firstOrNull { id -> batches.any { it.id == id } }
    if (targetId == null) {
        if (transactionType != "return" || delta <= 0.0) return batches
        val now = getCurrentTimeMillis()
        val currencyCode = line.currencyCode?.takeIf { it.isNotBlank() }
            ?: (item.returnPrices + item.salePrices + item.supplyPrices + item.wholesalePrices).firstOrNull { it.currency.isNotBlank() }?.currency
            ?: ""
        val fallbackPrice = PriceDataModel(line.pricePerUnit.coerceAtLeast(0.0).toStockMoneyText(), currencyCode, "")
        val batch = GoodsBatchDataModel(
            id = createClientOperationId("local-return-batch", item.id + line.barcode + now.toString()),
            goodsItemId = item.id,
            userId = item.userId,
            storeId = item.storeId,
            quantity = line.quantityUnit?.copy(total = delta) ?: QuantityDataModel("0", emptyList(), delta, 1.0, true),
            supplyPrice = item.supplyPrices.firstOrNull() ?: item.salePrices.firstOrNull() ?: item.returnPrices.firstOrNull() ?: fallbackPrice,
            returnPriceOverride = fallbackPrice,
            deliveredAtMillis = now,
            status = StockBatchStatusDataModel.Delivered,
            additionalNotes = "aita_returned_no_stock_batch",
            additionalNotesLocalized = listOf(
                LocalizedStringDataModel("main", "Returned items with no previous stock batch"),
                LocalizedStringDataModel("ru", "Возвраты без предыдущей складской партии"),
                LocalizedStringDataModel("kk", "Алдыңғы қойма партиясы жоқ қайтарымдар"),
                LocalizedStringDataModel("ky", "Мурунку кампа партиясы жок кайтарылган товарлар")
            ),
            createdAtMillis = now,
            updatedAtMillis = now,
            createdByUserId = item.userId,
            isActive = true
        )
        return batches + batch
    }
    return batches.map { batch ->
        if (batch.id == targetId) {
            val updatedQuantity = batch.quantity.copy(total = (batch.quantity.total + delta).coerceAtLeast(0.0))
            val updatedStatus = if (batch.status == StockBatchStatusDataModel.SoldOut && delta > 0.0) StockBatchStatusDataModel.Delivered else batch.status
            batch.copy(quantity = updatedQuantity, status = updatedStatus, updatedAtMillis = getCurrentTimeMillis())
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
                if (transaction.storeId != activeStoreIdState.value ||
                    operation.storeId != transaction.storeId || !currentStoreHasSubscriptionAccess(transaction.storeId))
                    return localNetworkEnvelope("ack", operation = operation, accepted = false, error = "SUBSCRIPTION_REQUIRED")
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
        if (throwable is kotlinx.coroutines.CancellationException) throw throwable
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
            if (realtimeUpdatesJob.isConnected || cloudTransportStatusState.value == CLOUD_TRANSPORT_STATUS_REACHABLE) {
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
    if (!currentStoreHasSubscriptionAccess(transaction.storeId)) {
        postInAppNotification(eventMessage("subscription.required"), NotificationType.Negative)
        return null
    }

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
    if (!currentCloudSessionIsReadyForBackgroundSync()) return 0

    return localNetworkCloudSyncMutex.withLock {
        val pending = localNetworkQueuedOperationsState.value
            .filter { it.status == LOCAL_NETWORK_QUEUE_PENDING || it.status == LOCAL_NETWORK_QUEUE_SYNCING }
            .sortedWith(compareBy<LocalNetworkQueuedOperationDataModel> { it.createdAtMillis }.thenBy { it.id })
        if (pending.isEmpty()) return@withLock 0

        logLocalNetworkQueueDiagnostic("sync start count=${pending.size} first=${pending.firstOrNull()?.id.orEmpty()} status=${cloudTransportStatusName(cloudTransportStatusState.value)} realtime=${realtimeUpdatesJob.isConnected}")

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

                    val synced = response.payload.let { completed ->
                        if (completed.clientOperationId.isBlank()) completed.copy(clientOperationId = operation.id)
                        else completed
                    }
                    reconcileLatestReceiptIdentity(synced)
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
private val cloudHealthRetryWakeup = ConnectionRetryWakeup()
private val realtimeRetryWakeup = ConnectionRetryWakeup()
private val cloudNetworkChangeRevision = MutableStateFlow(0L)

/** Platform hints only wake probes; they do not assert reachability, rotate tokens or replay writes. */
fun notifyCloudConnectionMayBeAvailable(networkChanged: Boolean = false) {
    if (networkChanged) cloudNetworkChangeRevision.update { it + 1L }
    cloudHealthRetryWakeup.request()
    realtimeRetryWakeup.request()
    if (appInitializationStartedState.value) startCloudConnectionHealthMonitor()
}

private val cloudConnectionHealthMonitorJob = OwnedConnectionJob()
private val cloudConnectionRecoveryJob = OwnedConnectionJob()
private val cloudConnectionReconciliationJob = OwnedConnectionJob()
private var manualCloudConnectionRefreshJob: Job? = null
@Volatile
private var manualCloudConnectionRefreshGeneration: Long = 0L
private val realtimeUpdatesJob = OwnedConnectionJob()
private var realtimeRefreshJob: Job? = null
private val realtimeRefreshMutex = Mutex()
private val cloudConnectionHealthProbeMutex = Mutex()
val realtimeUpdatesConnectedState: StateFlow<Boolean> = realtimeUpdatesJob.state
    .map { it.connected }
    .stateIn(GlobalScope, SharingStarted.Eagerly, false)

/** Must be called with inventoryStateMutex held, so a late cache writer cannot undo revocation. */
private suspend inline fun <reified T> persistInventoryCacheLocked(
    name: String,
    owner: InventoryOwner,
    payload: List<T>,
    cloudVerified: Boolean = false
): Boolean = try {
    withTimeoutOrNull(30_000L) {
        val key = CACHE_PREFIX + inventoryCacheKey(name, owner)
        writeJsonCacheText(key, jsonBase.encodeToString(payload))
        // Cache/local writes must never grant permission. Only an accepted cloud read clears this marker.
        if (cloudVerified) deleteLocalKv(key + ":access-denied")
        getLocalKv(key + ":access-denied") != "1"
    } ?: false
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    logCloudConnectionDiagnostic("Inventory cache write failed; in-memory data retained")
    false
}

private suspend fun denyCachedInventoryLocked(owner: InventoryOwner, failure: List<LocalizedStringDataModel>) {
    inventoryAccessRevision++
    // STOCK_READ protects both endpoints. Do not leave sellable cached batches beside a denied list.
    stockState.emit(DataState.Empty(failure))
    stockBatchesState.emit(DataState.Empty(failure))
    parentStoreStockState.emit(DataState.Empty(failure))
    stockItemBranchAvailabilityState.emit(DataState.Empty(failure))
    stockLoadStatusState.value = InventoryLoadStatus(owner.storeId, failure = failure, accessDenied = true)
    stockBatchesLoadStatusState.value = InventoryLoadStatus(owner.storeId, failure = failure, accessDenied = true)
    for (name in listOf("stock", "stock_batches")) {
        try {
            val key = CACHE_PREFIX + inventoryCacheKey(name, owner)
            // Marker first: even an interrupted deletion must not make the old cache eligible again.
            val saved = withTimeoutOrNull(30_000L) {
                putLocalKv(key + ":access-denied", "1")
                deleteJsonCacheText(key)
                true
            } ?: false
            if (!saved) logCloudConnectionDiagnostic("Inventory revocation cache write timed out; memory remains denied")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            logCloudConnectionDiagnostic("Inventory revocation could not be persisted; memory access remains denied")
        }
    }
}

private suspend inline fun <reified T> hydrateInventoryResource(
    name: String,
    owner: InventoryOwner,
    state: MutableDataStateFlow<List<T>>,
    status: MutableStateFlow<InventoryLoadStatus>,
    filter: suspend (List<T>) -> List<T>
) {
    val accessAtStart = inventoryStateMutex.withLock {
        if (!inventoryOwnerIsCurrent(owner) || state.payloadValue != null || status.value.accessDenied) return
        inventoryAccessRevision
    }
    // Disk I/O must not hold the ownership lock: logout/store switching can cancel this hydration.
    val key = CACHE_PREFIX + inventoryCacheKey(name, owner)
    val cached = try {
        withTimeoutOrNull(30_000L) {
            val denied = getLocalKv(key + ":access-denied") == "1"
            denied to if (denied) null else getJsonCache<List<T>>(inventoryCacheKey(name, owner))
        }
    } catch (failure: Exception) {
        ensureConnectionOwnerActive(failure)
        logCloudConnectionDiagnostic("Inventory cache read failed; cloud loading remains available")
        null
    }
    val payload = cached?.second?.let { filter(it) }
    inventoryStateMutex.withLock {
        if (inventoryAccessRevision != accessAtStart ||
            !canHydrateInventory(state.payloadValue != null || status.value.accessDenied, inventoryOwnerIsCurrent(owner))) return
        status.value = status.value.copy(cacheChecked = true)
        if (cached?.first == true) {
            status.value = status.value.copy(accessDenied = true, failure = inventoryLoadFailureMessage())
        } else if (payload != null) {
            state.emit(DataState.Success(payload, cacheMessage()))
            status.value = status.value.copy(source = InventoryLoadSource.Cache)
        }
    }
}

private suspend fun loadCachedInventory(storeId: String, owner: InventoryOwner = inventoryOwners.current) {
    if (owner.storeId != storeId || !inventoryOwnerIsCurrent(owner)) return
    coroutineScope {
        launch { hydrateInventoryResource("stock", owner, stockState, stockLoadStatusState, ::filterRecentlyDeletedStockItems) }
        launch { hydrateInventoryResource("stock_batches", owner, stockBatchesState, stockBatchesLoadStatusState, ::filterRecentlyDeletedStockBatches) }
    }
}

/** Includes null owners so logout also cancels an in-progress cache hydration. */
internal suspend fun observeActiveInventoryData() {
    combine(inventoryOwners.state, activeStoreIdState) { owner, storeId ->
        owner.takeIf { it.storeId == storeId && !storeId.isNullOrBlank() }
    }.distinctUntilChanged().collectLatest { owner ->
        if (owner == null || !inventoryOwnerIsCurrent(owner)) return@collectLatest
        val storeId = owner.storeId ?: return@collectLatest
        loadCachedStoreScopedData(storeId, owner)
        if (!inventoryOwnerIsCurrent(owner)) return@collectLatest
        restoreStoreSubscriptionCache(storeId)
        refreshStoreSubscriptionNow(storeId)
        if (!inventoryOwnerIsCurrent(owner)) return@collectLatest
        getMyWorkerMemberships()
        getMyWorkerRequests()
        if (!currentStoreHasSubscriptionAccess(storeId)) return@collectLatest
        getStock(storeId)
        getStockBatches(storeId)
        getTransactions(storeId)
        getCashRegister(storeId)
        getStoreWorkers(storeId)
        getIncomingWorkerRequests(storeId)
    }
}

private suspend fun loadCachedStoreScopedData(storeId: String, owner: InventoryOwner = inventoryOwners.current) {
    if (owner.storeId != storeId || !inventoryOwnerIsCurrent(owner)) return
    loadCachedInventory(storeId, owner)
    if (!inventoryOwnerIsCurrent(owner)) return
    // Capture store-scoped keys before I/O; late disk reads cannot replace a fresh cloud/local ledger.
    val transactionKey = storeScopedCacheKey("transactions", storeId)
    val cashKey = storeScopedCacheKey("cash_register", storeId)
    val eventKey = storeScopedCacheKey("cash_register_events", storeId)
    getJsonCache<List<TransactionDataModel>>(transactionKey)?.let { cached ->
        inventoryStateMutex.withLock {
            if (canHydrateInventory(transactionsState.payloadValue != null, inventoryOwnerIsCurrent(owner)))
                transactionsState.emit(DataState.Success(cached, cacheMessage()))
        }
    }
    if (!inventoryOwnerIsCurrent(owner)) return
    getJsonCache<List<DebtorDataModel>>(storeScopedCacheKey("debtors", storeId))?.let {
        if (inventoryOwnerIsCurrent(owner)) debtorsState.emit(DataState.Success(it, cacheMessage()))
    }
    getJsonCache<StoreCashRegisterDataModel>(cashKey)?.let { cached ->
        inventoryStateMutex.withLock {
            if (cached.storeId == storeId && canHydrateInventory(cashRegisterState.payloadValue != null, inventoryOwnerIsCurrent(owner))) {
                cashRegisterState.emit(DataState.Success(cached, cacheMessage()))
                cashRegisterAmountState.emit(cached.currentAmount)
            }
        }
    }
    getJsonCache<List<CashRegisterEventDataModel>>(eventKey)?.let { events ->
        inventoryStateMutex.withLock {
            if (canHydrateInventory(cashRegisterEventsState.payloadValue != null, inventoryOwnerIsCurrent(owner))) {
                cashRegisterEventsState.emit(DataState.Success(events, cacheMessage()))
                cashRegisterExtractionsState.emit(DataState.Success(events.filter { it.type == CASH_REGISTER_EVENT_EXTRACTION }.map { it.toExtractionEntry() }, cacheMessage()))
            }
        }
    }
    if (!inventoryOwnerIsCurrent(owner)) return
    getJsonCache<List<StoreWorkerDataModel>>(storeScopedCacheKey("store_workers", storeId))?.let {
        inventoryStateMutex.withLock {
            if (inventoryOwnerIsCurrent(owner)) storeWorkerMembershipsState.emit(DataState.Success(it, cacheMessage()))
        }
    }
    getJsonCache<List<StoreWorkerRequestDataModel>>(storeScopedCacheKey("incoming_worker_requests", storeId))?.let {
        inventoryStateMutex.withLock {
            if (inventoryOwnerIsCurrent(owner)) incomingWorkerRequestsState.emit(DataState.Success(it, cacheMessage()))
        }
    }
}

private suspend fun loadCachedApplicationData() {
    getJsonCache<GlobalAppConfigurationDataModel>(CACHE_GLOBAL_CONFIG)?.let {
        val currentConfiguration = globalAppConfigurationState.payloadValue
        val cachedLastKnownGoodServerUrl = cachedLastKnownGoodServerUrlOrNull()
        val runtimeOverrideNormalized = normalizedExplicitAitaServerUrlOrNull(runtimeClientServerUrlOverride)
        val anchoredServerUrl = when {
            runtimeOverrideNormalized != null -> Pair(runtimeOverrideNormalized, currentConfiguration.serverUrl.second)
            cachedLastKnownGoodServerUrl != null -> Pair(cachedLastKnownGoodServerUrl, currentConfiguration.serverUrl.second)
            clientVisibleServerUrlFilesOnly -> currentConfiguration.serverUrl
            else -> chooseClientServerUrlPair(currentConfiguration.serverUrl, it.serverUrl)
        }
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

    // Account data may be shown offline only while a durable authenticated session still exists.
    // Older builds left JSON account caches behind on logout; hydrating those without tokens made
    // desktop relaunch appear logged in even after Keychain credentials had been removed.
    val hasAuthenticatedSession = hasStoredAuthenticatedSession()
    if (hasAuthenticatedSession) {
        getJsonCache<UserAccountDataModel>(CACHE_USER)?.let {
            userAccountState.emit(DataState.Success(it, cacheMessage()))
            ActiveStores.acceptAccount(it)
            AccountAppModes.acceptAccount(it, authoritative = false)
        }
        getJsonCache<List<StoreDataModel>>(CACHE_STORES)?.let {
            storesState.emit(DataState.Success(it, cacheMessage()))
        }
        getJsonCache<List<SupplierDataModel>>(CACHE_SUPPLIERS)?.let {
            suppliersState.emit(DataState.Success(it, cacheMessage()))
        }
        val notificationOwner = userAccountState.payloadValue?.id
        val notificationGeneration = currentAuthenticatedSessionGeneration()
        getJsonCache<List<NotificationDataModel>>(CACHE_NOTIFICATIONS)?.let { cached ->
            notificationHistoryMutex.withLock {
                if (userAccountState.payloadValue?.id == notificationOwner && authenticatedSessionGenerationIsCurrent(notificationGeneration)) {
                    val live = notificationsState.payloadValue.orEmpty()
                    val merged = mergeNotificationSnapshot(emptyList(), live, cached.filter { it.userId == null || it.userId == notificationOwner })
                    notificationsState.emit(DataState.Success(merged, cacheMessage()))
                }
            }
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
    } else {
        clearAuthenticatedAccountCaches()
    }

    getJsonCache<List<GenericGoodsCategoryDataModel>>(CACHE_GENERIC_GOODS_CATEGORIES)?.let {
        genericGoodsCategoriesState.emit(DataState.Success(it, cacheMessage()))
        categoriesState.emit(DataState.Success(it, cacheMessage()))
    }
    getJsonCache<List<GenericGoodsItemDataModel>>(CACHE_GENERIC_GOODS_ITEMS)?.let {
        genericGoodsItemsState.emit(DataState.Success(it, cacheMessage()))
    }
    getJsonCache<List<StoreSubscriptionPlanDataModel>>(CACHE_SUBSCRIPTION_PLANS)?.let { cachedPlans ->
        val allowedPlanIds = defaultStoreSubscriptionPlans().map { it.id }.toSet()
        val visiblePlans = cachedPlans.filter { it.id in allowedPlanIds }.ifEmpty { defaultStoreSubscriptionPlans() }
        subscriptionPlansState.emit(DataState.Success(visiblePlans, cacheMessage()))
    }

    if (hasAuthenticatedSession) {
        activeStoreIdState.value?.let { loadCachedStoreScopedData(it) }
    }
}

private fun startAppCacheCollectors() {
    if (appCacheCollectorsStarted) return
    appCacheCollectorsStarted = true

    GlobalScope.launch(Dispatchers.ourIo) {
        globalAppConfigurationState.payload.collect { nextConfiguration ->
            if (clientVisibleServerUrlFilesOnly) {
                putJsonCache(CACHE_GLOBAL_CONFIG, nextConfiguration)
            } else {
                val runtimeOverrideNormalized = normalizedExplicitAitaServerUrlOrNull(runtimeClientServerUrlOverride)
                val canonicalNormalized = normalizedAutomaticAitaServerUrlOrNull(nextConfiguration.serverUrl.first)
                    ?: normalizedAutomaticAitaServerUrlOrNull(DEFAULT_AITA_SERVER_URL)
                    ?: error("AITA canonical public server URL is invalid")
                val serverUrlForCache = Pair(
                    runtimeOverrideNormalized ?: canonicalNormalized,
                    nextConfiguration.serverUrl.second
                )
                putJsonCache(CACHE_GLOBAL_CONFIG, nextConfiguration.copy(serverUrl = serverUrlForCache))
            }
        }
    }
    GlobalScope.launch(Dispatchers.ourIo) { stringsState.payload.collect { it?.let { putJsonCache(CACHE_STRINGS, it) } } }
    GlobalScope.launch(Dispatchers.ourIo) { dimensionsState.payload.collect { it?.let { putJsonCache(CACHE_DIMENSIONS, it) } } }
    GlobalScope.launch(Dispatchers.ourIo) { colorsState.payload.collect { it?.let { putJsonCache(CACHE_COLORS, it) } } }
    GlobalScope.launch(Dispatchers.ourIo) { drawablesState.payload.collect { it?.let { putJsonCache(CACHE_DRAWABLES, it) } } }
    GlobalScope.launch(Dispatchers.ourIo) { userAccountState.payload.collect { it?.let { putAuthenticatedJsonCache(CACHE_USER, it) } } }
    GlobalScope.launch(Dispatchers.ourIo) { storesState.payload.collect { it?.let { putAuthenticatedJsonCache(CACHE_STORES, it) } } }
    GlobalScope.launch(Dispatchers.ourIo) { suppliersState.payload.collect { it?.let { putAuthenticatedJsonCache(CACHE_SUPPLIERS, it) } } }
    GlobalScope.launch(Dispatchers.ourIo) { genericGoodsCategoriesState.payload.collect { it?.let { putJsonCache(CACHE_GENERIC_GOODS_CATEGORIES, it) } } }
    GlobalScope.launch(Dispatchers.ourIo) { genericGoodsItemsState.payload.collect { it?.let { putJsonCache(CACHE_GENERIC_GOODS_ITEMS, it) } } }
    GlobalScope.launch(Dispatchers.ourIo) { notificationsState.payload.collect { it?.let { putAuthenticatedJsonCache(CACHE_NOTIFICATIONS, it) } } }
    GlobalScope.launch(Dispatchers.ourIo) { securitySessionsState.payload.collect { it?.let { putAuthenticatedJsonCache(CACHE_SECURITY_SESSIONS, it) } } }
    GlobalScope.launch(Dispatchers.ourIo) { securitySessionHistoryState.payload.collect { it?.let { putAuthenticatedJsonCache(CACHE_SECURITY_SESSION_HISTORY, it) } } }
    GlobalScope.launch(Dispatchers.ourIo) { myWorkerMembershipsState.payload.collect { it?.let { putAuthenticatedJsonCache(CACHE_MY_WORKER_MEMBERSHIPS, it) } } }
    GlobalScope.launch(Dispatchers.ourIo) { myWorkerRequestsState.payload.collect { it?.let { putAuthenticatedJsonCache(CACHE_MY_WORKER_REQUESTS, it) } } }
    GlobalScope.launch(Dispatchers.ourIo) { userFinanceDashboardState.payload.collect { it?.let { putAuthenticatedJsonCache(CACHE_USER_FINANCE_DASHBOARD, it) } } }
    GlobalScope.launch(Dispatchers.ourIo) { subscriptionPlansState.payload.collect { it?.let { putJsonCache(CACHE_SUBSCRIPTION_PLANS, it) } } }

    GlobalScope.launch(Dispatchers.ourIo) {
        stockState.payload.collect { payload ->
            if (payload != null) inventoryStateMutex.withLock {
                val owner = inventoryOwners.current
                if (inventoryOwnerIsCurrent(owner) && stockState.payloadValue === payload && !stockLoadStatusState.value.accessDenied) {
                    val saved = persistInventoryCacheLocked("stock", owner, payload)
                    if (inventoryOwnerIsCurrent(owner) && stockState.payloadValue === payload) {
                        stockLoadStatusState.value = stockLoadStatusState.value.copy(cacheWriteFailed = !saved)
                    }
                }
            }
        }
    }
    GlobalScope.launch(Dispatchers.ourIo) {
        stockBatchesState.payload.collect { payload ->
            if (payload != null) inventoryStateMutex.withLock {
                val owner = inventoryOwners.current
                if (inventoryOwnerIsCurrent(owner) && stockBatchesState.payloadValue === payload && !stockBatchesLoadStatusState.value.accessDenied) {
                    val saved = persistInventoryCacheLocked("stock_batches", owner, payload)
                    if (inventoryOwnerIsCurrent(owner) && stockBatchesState.payloadValue === payload) {
                        stockBatchesLoadStatusState.value = stockBatchesLoadStatusState.value.copy(cacheWriteFailed = !saved)
                    }
                }
            }
        }
    }
    GlobalScope.launch(Dispatchers.ourIo) {
        transactionsState.payload.collect { payload ->
            if (payload != null) {
                val key = inventoryStateMutex.withLock {
                    val owner = inventoryOwners.current
                    if (inventoryOwnerIsCurrent(owner) && transactionsState.payloadValue === payload)
                        storeScopedCacheKey("transactions", requireNotNull(owner.storeId)) else null
                }
                if (key != null) putJsonCache(key, payload)
            }
        }
    }
    GlobalScope.launch(Dispatchers.ourIo) {
        debtorsState.payload.collect { payload ->
            val storeId = activeStoreIdState.value
            if (hasStoredAuthenticatedSession() && !storeId.isNullOrBlank() && payload != null) putJsonCache(storeScopedCacheKey("debtors", storeId), payload)
        }
    }
    GlobalScope.launch(Dispatchers.ourIo) {
        cashRegisterState.payload.collect { payload ->
            if (payload != null) {
                val key = inventoryStateMutex.withLock {
                    val owner = inventoryOwners.current
                    if (inventoryOwnerIsCurrent(owner) && cashRegisterState.payloadValue === payload)
                        storeScopedCacheKey("cash_register", requireNotNull(owner.storeId)) else null
                }
                if (key != null) putJsonCache(key, payload)
            }
        }
    }
    GlobalScope.launch(Dispatchers.ourIo) {
        cashRegisterEventsState.payload.collect { payload ->
            if (payload != null) {
                val key = inventoryStateMutex.withLock {
                    val owner = inventoryOwners.current
                    if (inventoryOwnerIsCurrent(owner) && cashRegisterEventsState.payloadValue === payload)
                        storeScopedCacheKey("cash_register_events", requireNotNull(owner.storeId)) else null
                }
                if (key != null) putJsonCache(key, payload)
            }
        }
    }
    GlobalScope.launch(Dispatchers.ourIo) {
        storeWorkerMembershipsState.payload.collect { payload ->
            val storeId = activeStoreIdState.value
            if (hasStoredAuthenticatedSession() && !storeId.isNullOrBlank() && payload != null) putJsonCache(storeScopedCacheKey("store_workers", storeId), payload)
        }
    }
    GlobalScope.launch(Dispatchers.ourIo) {
        incomingWorkerRequestsState.payload.collect { payload ->
            val storeId = activeStoreIdState.value
            if (hasStoredAuthenticatedSession() && !storeId.isNullOrBlank() && payload != null) putJsonCache(storeScopedCacheKey("incoming_worker_requests", storeId), payload)
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
private const val REALTIME_AFTER_WEBSOCKET_CLOSE_MIN_DELAY_MILLIS = 1_000L
private const val REALTIME_RECENT_UPDATE_IDS_LIMIT = 512

private val realtimeRefreshPlanMutex = Mutex()
private val realtimePendingRefreshEntities = mutableSetOf<String>()
private var realtimeRefreshScheduledAtMillis = 0L
private var realtimeRefreshSessionGeneration = -1L
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

private fun String.isSupplierRealtimeEntity(): Boolean =
    this == "suppliers" ||
        startsWith("suppliers/") ||
        this == "supplierorders" ||
        startsWith("supplierorders/") ||
        this == "suppliergoodsprices" ||
        startsWith("suppliergoodsprices/") ||
        this == "suppliercontracts" ||
        startsWith("suppliercontracts/")

private fun String.isSupplierProfileRealtimeEntity(): Boolean =
    this == "suppliers" ||
        this == "suppliers/add" ||
        this == "suppliers/update" ||
        this == "suppliers/delete" ||
        startsWith("suppliers/profiles")

internal fun supplierRealtimeEntityChangesProfiles(entity: String?): Boolean =
    cleanRealtimeEntity(entity).isSupplierProfileRealtimeEntity()

internal fun realtimeUpdateIsRelevantToCurrentContext(
    entity: String?, updateStoreId: String?, activeStoreId: String?, appMode: Int,
    familyStoreIds: Set<String> = emptySet()
): Boolean = realtimeScopeMatches(cleanRealtimeEntity(entity), updateStoreId.orEmpty().trim(),
    activeStoreId?.trim(), familyStoreIds,
    appMode == APP_MODE_SUPPLIER || appMode == APP_MODE_MANUFACTURER)

private fun currentRealtimeStoreFamily(): Set<String> {
    val selected = activeStoreIdState.value ?: return emptySet()
    val stores = storesState.payloadValue.orEmpty().flattenStoresWithBranches()
    val root = stores.firstOrNull { it.id == selected }?.rootStoreId() ?: selected
    return stores.filter { it.rootStoreId() == root }.map { it.id }.toSet() + selected + root
}

private val realtimeInventoryReads = TrailingInvalidationRunner<InventoryOwner>()

private suspend fun refreshInventoryAfterRealtimeInvalidation() {
    val owner = inventoryOwners.current
    if (!inventoryOwnerIsCurrent(owner)) return
    realtimeInventoryReads.invalidate(owner, GlobalScope, Dispatchers.ourIo) {
        // The currently running read may have taken its snapshot BEFORE this invalidation.
        // Join it first, then ask for a fresh snapshot, rather than joining and losing the event.
        stockRead.jobFor(owner)?.join()
        stockBatchesRead.jobFor(owner)?.join()
        if (inventoryOwnerIsCurrent(owner)) {
            val storeId = requireNotNull(owner.storeId)
            getStock(storeId)
            getStockBatches(storeId)
            stockRead.jobFor(owner)?.join()
            stockBatchesRead.jobFor(owner)?.join()
        }
    }
}

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

private suspend fun refreshEverythingFromServerAfterRealtimeUpdate() {
    PublicContentSignals.changed()
    ProfilePhotoSignals.changed()
    AnalyticsWorkspace.refreshServerTotalsIfNeeded()
    lastRealtimeBroadRefreshAtMillis = getCurrentTimeMillis()

    getGlobalAppConfiguration(loadAll = false)
    getUser(forceLogOut = false, applyServerActiveStore = false, refreshRelatedData = false)
    getStores()
    getSuppliers()
    getGenericGoodsCategories()
    refreshGenericGoodsItems(limit = 200)
    getNotifications()
    getSupportTickets()
    CompanyEmployment.refreshSoon()
    SupportWorkspaceSignals.changed()
    MarketplaceSignals.changed()
    getSecuritySessions()
    getSecuritySessionHistory()
    getMyWorkerMemberships()
    getMyWorkerRequests()
    getUserFinanceDashboard()
    if (appModeState.value == APP_MODE_SUPPLIER || appModeState.value == APP_MODE_MANUFACTURER) {
        val focusedSupplierId = effectiveActiveSupplierProfileId()
        if (focusedSupplierId.isNullOrBlank()) getMySupplierSideOrders()
        else getSupplierOrdersForSupplier(focusedSupplierId)
        getMySupplierGoodsPrices(focusedSupplierId)
        getSupplierContracts(supplierId = focusedSupplierId)
        getSupplierModeDashboard(supplierId = focusedSupplierId)
    }

    activeStoreIdState.value?.let { storeId ->
        refreshStoreSubscriptionNow(storeId)
        if (!currentStoreHasSubscriptionAccess(storeId)) return@let
        refreshInventoryAfterRealtimeInvalidation()
        getTransactions(storeId)
        getDebtors(storeId)
        getCashRegister(storeId)
        getCurrentWorkshift(storeId)
        getStoreWorkers(storeId)
        getIncomingWorkerRequests(storeId)
        getOperationLogs(storeId, OPERATION_LOG_SCOPE_CURRENT)
        getOperationLogs(storeId, OPERATION_LOG_SCOPE_ROOT)
    }
}

private suspend fun refreshRealtimeEntitiesFromServer(entities: Set<String>) {
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
    if (anyEntityMatches("user", "auth")) getUser(forceLogOut = false, applyServerActiveStore = false, refreshRelatedData = false)
    if (anyEntityMatches("stores")) getStores()

    // Profile mutations and dashboard invalidations share the `suppliers/...` namespace, but they
    // are different resources. A dashboard-only event must not trigger an unnecessary supplier-list
    // GET. Profile edits only reload identities; commercial dashboard data is unchanged by a name or
    // contact edit, while add/delete focus changes are reconciled by SupplierIdentityFocus when needed.
    val supplierProfilesChanged = cleanEntities.any(::supplierRealtimeEntityChangesProfiles)
    if (supplierProfilesChanged) getSuppliers()

    val supplierOrdersChanged = anyEntityMatches("supplierorders")
    val supplierPricesChanged = anyEntityMatches("suppliergoodsprices")
    val supplierContractsChanged = anyEntityMatches("suppliercontracts")
    val supplierDashboardChanged = anyEntityMatches("suppliers/dashboard")
    val supplierModeActive = appModeState.value == APP_MODE_SUPPLIER ||
        appModeState.value == APP_MODE_MANUFACTURER

    if (supplierModeActive) {
        val focusedSupplierId = effectiveActiveSupplierProfileId()
        if (supplierOrdersChanged) {
            if (focusedSupplierId.isNullOrBlank()) getMySupplierSideOrders()
            else getSupplierOrdersForSupplier(focusedSupplierId)
        }
        if (supplierPricesChanged) getMySupplierGoodsPrices(focusedSupplierId)
        if (supplierContractsChanged) getSupplierContracts(supplierId = focusedSupplierId)
        if (
            supplierOrdersChanged ||
            supplierPricesChanged ||
            supplierContractsChanged ||
            supplierDashboardChanged
        ) {
            getSupplierModeDashboard(supplierId = focusedSupplierId)
        }
    } else {
        activeStoreId?.takeIf { currentStoreHasSubscriptionAccess(it) }?.let { storeId ->
            if (supplierOrdersChanged) getSupplierOrders(storeId)
            if (supplierPricesChanged) getSupplierGoodsPrices(storeId)
            if (supplierContractsChanged) getSupplierContracts(storeId = storeId)
        }
    }
    if (anyEntityMatches("generic")) {
        getGenericGoodsCategories()
        refreshGenericGoodsItems(limit = 200)
    }
    if (anyEntityMatches("notifications")) getNotifications()
    if (anyEntityMatches("company")) CompanyEmployment.refresh()
    if (anyEntityMatches("support")) {
        getSupportTickets()
        SupportWorkspaceSignals.changed()
    }
    if (anyEntityMatches("market")) MarketplaceSignals.changed()
    if (anyEntityMatches("security")) {
        getSecuritySessions()
        getSecuritySessionHistory()
    }
    if (anyEntityMatches("finance")) getUserFinanceDashboard()
    if (anyEntityMatches("subscriptions")) {
        activeStoreId?.let {
            refreshStoreSubscriptionNow(it)
            if (currentStoreHasSubscriptionAccess(it)) refreshInventoryAfterRealtimeInvalidation()
        }
    }
    if (anyEntityMatches("workers")) {
        getMyWorkerMemberships()
        getMyWorkerRequests()
        activeStoreId?.takeIf { currentStoreHasSubscriptionAccess(it) }?.let { storeId ->
            getStoreWorkers(storeId)
            getIncomingWorkerRequests(storeId)
        }
    }

    activeStoreId?.takeIf { currentStoreHasSubscriptionAccess(it) }?.let { storeId ->
        if (anyEntityMatches("stock", "stockbatches", "stock/availability", "transactions/cart")) {
            refreshInventoryAfterRealtimeInvalidation()
        }

        if (anyEntityMatches("transactions", "stock", "stockbatches", "cashregister")) AnalyticsWorkspace.refreshServerTotalsIfNeeded()
        if (anyEntityMatches("transactions")) {
            getTransactions(storeId)
            getCashRegister(storeId)
            getDebtors(storeId)
            refreshInventoryAfterRealtimeInvalidation()
        }

        if (anyEntityMatches("debtors")) getDebtors(storeId)
        if (anyEntityMatches("cashregister")) getCashRegister(storeId)
        if (anyEntityMatches("workshifts")) getCurrentWorkshift(storeId)

        if (cleanEntities.any { it.substringBefore('/') !in setOf("notifications", "security", "support", "company") }) {
            getOperationLogs(storeId, OPERATION_LOG_SCOPE_CURRENT)
            getOperationLogs(storeId, OPERATION_LOG_SCOPE_ROOT)
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
    if (cleanEntity == "users/profile-photo") {
        ProfilePhotoSignals.changed()
        return // Private invalidation already audience-filtered by the server socket.
    }
    if (cleanEntity == "help" || cleanEntity.startsWith("help/")) {
        PublicContentSignals.changed()
        return // No stock, finance or operation-log refresh is needed for a public handbook change.
    }
    val cleanReason = cleanRealtimeReason(reason)
    val now = getCurrentTimeMillis()
    val generation = currentAuthenticatedSessionGeneration()
    realtimeRefreshPlanMutex.withLock {
        if (!authenticatedSessionGenerationIsCurrent(generation)) return@withLock
        if (realtimeRefreshSessionGeneration != generation) {
            realtimeRefreshJob?.cancel()
            realtimeRefreshJob = null
            realtimePendingRefreshEntities.clear()
            realtimeRecentUpdateIds.clear()
            realtimeRecentUpdateIdSet.clear()
            realtimeRefreshSessionGeneration = generation
            lastRealtimeBroadRefreshAtMillis = 0L // A different sign-in does not inherit an old owner's cooldown.
        }
        if (!force && !rememberRealtimeUpdateIdLocked(updateId)) return@withLock
        val hadBroad = "all" in realtimePendingRefreshEntities
        val broad = realtimeUpdateRequiresBroadRefresh(cleanEntity, cleanReason)
        // Catch-up timers are throttled. An actual committed mutation must never sit behind
        // a recent reconnect's 20/30-second broad-refresh cooldown.
        val minInterval = when {
            cleanReason in setOf("connected", "websocket_connected", "connection_sync") -> REALTIME_CONNECTED_REFRESH_MIN_INTERVAL_MILLIS
            cleanReason in setOf("heartbeat_catchup", "connection_poll", "heartbeat") -> REALTIME_BROAD_REFRESH_MIN_INTERVAL_MILLIS
            else -> 0L
        }
        if (broad) {
            realtimePendingRefreshEntities.clear()
            realtimePendingRefreshEntities.add("all")
        } else if (!hadBroad) realtimePendingRefreshEntities.add(cleanEntity)

        // A reconnect invalidation inside the throttle interval must run at its trailing edge,
        // not be discarded forever. Repeated signals do not keep postponing the same job.
        val pendingBroad = "all" in realtimePendingRefreshEntities
        val waitMillis = if (force) 75L else maxOf(REALTIME_REFRESH_DEBOUNCE_MILLIS,
            if (pendingBroad) realtimeCatchupDelayMillis(now, lastRealtimeBroadRefreshAtMillis, minInterval) else 0L)
        val scheduledAt = now + waitMillis
        val delayNewBroad = !hadBroad && pendingBroad && realtimeRefreshScheduledAtMillis < scheduledAt
        if (!force && realtimeRefreshJob?.isActive == true &&
            realtimeRefreshScheduledAtMillis <= scheduledAt && !delayNewBroad) return@withLock

        realtimeRefreshJob?.cancel()
        realtimeRefreshScheduledAtMillis = scheduledAt
        realtimeRefreshJob = GlobalScope.launch(Dispatchers.ourIo, start = CoroutineStart.LAZY) {
            val thisJob = coroutineContext[Job]
            delay(waitMillis)
            val entities = realtimeRefreshPlanMutex.withLock {
                if (realtimeRefreshJob !== thisJob) emptySet() else {
                    realtimeRefreshJob = null
                    realtimeRefreshScheduledAtMillis = 0L
                    realtimePendingRefreshEntities.toSet().also { realtimePendingRefreshEntities.clear() }
                }
            }
            if (entities.isEmpty() || !authenticatedSessionGenerationIsCurrent(generation)) return@launch
            realtimeRefreshMutex.withLock {
                if (authenticatedSessionGenerationIsCurrent(generation)) refreshRealtimeEntitiesFromServer(entities)
            }
        }
        realtimeRefreshJob?.start()
    }
}

// Independent unauthenticated transport: health cannot queue behind bearer refresh or a
// half-open business/WebSocket request on the application's long-lived client.
internal val cloudHealthHttpClient: HttpClient by lazy {
    HttpClient(getHttpClientEngine()) {
        expectSuccess = false
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000L
            connectTimeoutMillis = 8_000L
            socketTimeoutMillis = 15_000L
        }
    }
}

private suspend fun cloudConnectionProbeRequest(reason: String): ResponseDataModel<Unit> {
    ensureCachedGlobalConfigurationPrimedForNetwork()
    val endpointUrl = globalAppConfigurationState.payloadValue.connectionCheckPath.first
    val serverUrlCandidates = resolvedServerUrlCandidates(null)
    if (serverUrlCandidates.isEmpty()) {
        return ResponseDataModel(
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
    }

    return probeReachableAitaServerUrl(
        probeHttpClient = cloudHealthHttpClient,
        serverUrlCandidates = serverUrlCandidates,
        endpointUrl = endpointUrl,
        reason = reason
    ).response
}

private fun cancelRealtimeUpdatesSocketAfterReachabilityFailure() {
    if (realtimeUpdatesJob.isConnected || realtimeUpdatesJob.isRunning) {
        logCloudConnectionDiagnostic("realtime socket cancelled because health probe/server request says unreachable")
    }
    realtimeUpdatesJob.cancel()
}

@PublishedApi
internal fun cloudConnectionUnavailableProbeDelayMillis(unavailableRound: Int): Long =
    cloudUnavailableRetryDelayMillis(unavailableRound)

private data class CloudConnectionRecoveryResult(
    val success: Boolean,
    val transportAvailable: Boolean,
    val hasLocalAccount: Boolean,
    val response: ResponseDataModel<Unit>
)

private fun unavailableCloudConnectionResponse(): ResponseDataModel<Unit> = ResponseDataModel(
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

/**
 * Restores only the transport/session path needed to call AITA again. Potentially slow cache,
 * outbox and entity reconciliation is deliberately launched separately so neither the health
 * monitor nor the user-facing Connect action can be trapped behind a large synchronization pass.
 */
private suspend fun recoverCloudConnectionFast(
    reason: String,
    knownReachabilityResponse: ResponseDataModel<Unit>? = null,
    forceRejectedRefreshRetry: Boolean = false,
    forcePresentationRecovery: Boolean = false,
    forceRealtimeRestart: Boolean = false
): CloudConnectionRecoveryResult = cloudConnectionRecoveryMutex.withLock {
    val probeResponse = knownReachabilityResponse ?: withTimeoutOrNull(
        CLOUD_CONNECTION_HEALTH_CHECK_TIMEOUT_MILLIS
    ) {
        cloudConnectionProbeRequest(reason)
    } ?: unavailableCloudConnectionResponse()

    if (probeResponse.negative) {
        cancelRealtimeUpdatesSocketAfterReachabilityFailure()
        markCloudTransportUnavailableForNotifications(
            forceConfirmation = forcePresentationRecovery,
            reason = "${reason}_probe"
        )
        return@withLock CloudConnectionRecoveryResult(
            success = false,
            transportAvailable = false,
            hasLocalAccount = false,
            response = probeResponse
        )
    }

    markCloudTransportReachableForNotifications(
        authenticated = false,
        authRefreshRequired = null,
        forceRecovery = forcePresentationRecovery
    )

    val hasLocalAccount = runCatching { getStoredUserAuthTokens?.invoke() != null }
        .onFailure { throwable ->
            logCloudConnectionDiagnostic(
                "connection recovery could not read local auth state: ${throwable.message ?: throwable}"
            )
        }
        .getOrDefault(false)

    if (!hasLocalAccount) {
        return@withLock CloudConnectionRecoveryResult(
            success = true,
            transportAvailable = true,
            hasLocalAccount = false,
            response = probeResponse
        )
    }

    val sessionGeneration = currentAuthenticatedSessionGeneration()
    // A newly successful AITA probe supersedes a cached transport error, not a rejected refresh
    // token or a server-side rate limit. Keep real authentication rejection remembered.
    if (lastAuthRefreshNonAuthFailureWasTransportFailure) clearAuthRefreshNonAuthFailure()
    val validation = ensureCloudSessionReadyForProtectedRequest(
        forceRejectedRefreshRetry = forceRejectedRefreshRetry,
        retryAfterTransportRecovery = true
    )
    if (!authenticatedSessionGenerationIsCurrent(sessionGeneration)) {
        // A sign-in/logout took ownership while validation was waiting. Its socket must survive.
        return@withLock CloudConnectionRecoveryResult(false, true, hasLocalAccount, cloudSessionExpiredResponse())
    }
    if (validation.negative) {
        if (validation.transportFailure) {
            cancelRealtimeUpdatesSocketAfterReachabilityFailure()
            markCloudTransportUnavailableForNotifications(
                forceConfirmation = forcePresentationRecovery,
                reason = "${reason}_auth_validation_transport"
            )
        } else {
            stopRealtimeUpdates()
        }
        return@withLock CloudConnectionRecoveryResult(
            success = false,
            transportAvailable = !validation.transportFailure,
            hasLocalAccount = true,
            response = validation
        )
    }

    markCloudTransportReachableForNotifications(
        authenticated = true,
        authRefreshRequired = false,
        forceRecovery = forcePresentationRecovery
    )

    if (
        shouldRestartRealtimeForRecovery(forceRealtimeRestart, realtimeUpdatesJob.isRunning)
    ) {
        restartRealtimeUpdates()
    } else if (!realtimeUpdatesJob.isConnected) {
        // The owner may be in backoff, not in a handshake. Wake only the wait; never cancel it.
        realtimeRetryWakeup.request()
    }

    CloudConnectionRecoveryResult(
        success = true,
        transportAvailable = true,
        hasLocalAccount = true,
        response = probeResponse
    )
}

private suspend fun runCloudConnectionReconciliationStep(
    name: String,
    block: suspend () -> Unit
) {
    try {
        block()
    } catch (throwable: Throwable) {
        ensureConnectionOwnerActive(throwable)
        logCloudConnectionDiagnostic(
            "connection reconciliation step=$name failed ${throwable.message ?: throwable}"
        )
    }
}

private fun launchCloudConnectionReconciliation(
    reason: String,
    forceBroadRefresh: Boolean
) {
    val started = cloudConnectionReconciliationJob.startIfIdle(GlobalScope, Dispatchers.ourIo) {
        val thisJob = coroutineContext[Job]
        try {
            if (!currentCloudSessionIsReadyForBackgroundSync()) return@startIfIdle

            runCloudConnectionReconciliationStep("session_cleanups") {
                syncPendingSessionCleanupsToServerNow()
            }
            runCloudConnectionReconciliationStep("notifications") {
                syncPendingNotificationsToServerNow()
            }
            runCloudConnectionReconciliationStep("local_outbox") {
                syncLocalNetworkOperationsToCloudNow()
            }
            runCloudConnectionReconciliationStep("active_store") { ActiveStores.retryPending() }
            runCloudConnectionReconciliationStep("app_mode") { AccountAppModes.retryPending() }
            runCloudConnectionReconciliationStep("entities") {
                scheduleRealtimeRefresh(
                    reason = reason,
                    entity = "all",
                    force = forceBroadRefresh
                )
            }
        } finally {
            cloudConnectionReconciliationJob.clear(thisJob)
        }
    }
    if (!started) {
        GlobalScope.launch(Dispatchers.ourIo) {
            runCatching {
                scheduleRealtimeRefresh(
                    reason = reason,
                    entity = "all",
                    force = forceBroadRefresh
                )
            }.onFailure { throwable ->
                ensureConnectionOwnerActive(throwable)
                logCloudConnectionDiagnostic(
                    "connection reconciliation refresh merge failed ${throwable.message ?: throwable}"
                )
            }
        }
    }

}

private val automaticReconciliationClock = kotlin.time.TimeSource.Monotonic.markNow()
private val automaticReconciliationGate = ConnectionReconciliationGate(
    nowMillis = { automaticReconciliationClock.elapsedNow().inWholeMilliseconds },
    intervalMillis = 120_000L
)

private fun launchAutomaticCloudConnectionRecovery(
    knownReachabilityResponse: ResponseDataModel<Unit>,
    forceRealtimeRestart: Boolean
) {
    cloudConnectionRecoveryJob.startIfIdle(GlobalScope, Dispatchers.ourIo) {
        val thisJob = coroutineContext[Job]
        try {
            val result = withTimeoutOrNull(60_000L) {
                recoverCloudConnectionFast(
                    reason = "automatic_reconnect",
                    knownReachabilityResponse = knownReachabilityResponse,
                    forcePresentationRecovery = true,
                    forceRealtimeRestart = forceRealtimeRestart
                )
            }
            if (result == null) {
                logCloudConnectionDiagnostic("automatic recovery timed out; monitor will retry")
                return@startIfIdle
            }
            if (result.success && result.hasLocalAccount &&
                automaticReconciliationGate.claim(currentAuthenticatedSessionGeneration())) {
                launchCloudConnectionReconciliation(
                    reason = "connection_sync",
                    forceBroadRefresh = false
                )
            }
        } catch (throwable: Throwable) {
            ensureConnectionOwnerActive(throwable)
            logCloudConnectionDiagnostic(
                "automatic connection recovery failed ${throwable.message ?: throwable}"
            )
        } finally {
            cloudConnectionRecoveryJob.clear(thisJob)
        }
    }
}

fun startCloudConnectionHealthMonitor() {
    cloudConnectionHealthMonitorJob.startIfIdle(GlobalScope, Dispatchers.ourIo) {
        val thisJob = coroutineContext[Job]
        delay(1_500L)
        var unavailableRound = 0
        var lastMonitorIterationAtMillis = getCurrentTimeMillis()
        var lastRealtimeSanityProbeAtMillis = 0L
        var lastProbeAtMillis = 0L
        var previousProbeFailed = false
        var observedNetworkRevision = cloudNetworkChangeRevision.value
        var pendingNetworkRestart = false
        var lastWakeRevision = cloudHealthRetryWakeup.revision

        try {
            while (isActive) {
                val wakeRevision = cloudHealthRetryWakeup.revision
                val explicitlyWoken = wakeRevision != lastWakeRevision
                lastWakeRevision = wakeRevision
                val networkRevision = cloudNetworkChangeRevision.value
                val networkChanged = networkRevision != observedNetworkRevision
                observedNetworkRevision = networkRevision
                pendingNetworkRestart = pendingNetworkRestart || networkChanged
                try {
                    val iterationStartedAt = getCurrentTimeMillis()
                    val resumedAfterLongPause =
                        iterationStartedAt - lastMonitorIterationAtMillis >= CLOUD_CONNECTION_HEALTH_CHECK_LONG_PAUSE_MILLIS
                    lastMonitorIterationAtMillis = iterationStartedAt

                    val configuredServerUrl = globalAppConfigurationState.payloadValue.serverUrl.first
                    val hasConfiguredServerUrl = normalizedHttpServerUrlOrNull(configuredServerUrl) != null

                    if (!hasConfiguredServerUrl) {
                        cloudHealthRetryWakeup.await(wakeRevision, CLOUD_CONNECTION_HEALTH_CHECK_UNKNOWN_INTERVAL_MILLIS)
                        continue
                    }

                    val realtimeReportedConnected = realtimeUpdatesJob.isConnected && !pendingNetworkRestart
                    val realtimeSanityProbeDue =
                        explicitlyWoken || networkChanged || resumedAfterLongPause ||
                            iterationStartedAt - lastRealtimeSanityProbeAtMillis >=
                            CLOUD_CONNECTION_HEALTH_CHECK_REALTIME_SANITY_INTERVAL_MILLIS

                    if (realtimeReportedConnected && !realtimeSanityProbeDue && !previousProbeFailed &&
                        cloudTransportStatusState.value == CLOUD_TRANSPORT_STATUS_REACHABLE) {
                        // A recently observed authenticated WebSocket is stronger and cheaper evidence
                        // than another synthetic ping. A periodic sanity probe, and an immediate probe
                        // after a long sleep/pause, still protect against half-open sockets.
                        markCloudTransportReachableForNotifications(
                            authenticated = true,
                            authRefreshRequired = false
                        )
                        cloudHealthRetryWakeup.await(wakeRevision, CLOUD_CONNECTION_HEALTH_CHECK_REALTIME_CONNECTED_INTERVAL_MILLIS)
                        continue
                    }

                    if (realtimeReportedConnected) {
                        lastRealtimeSanityProbeAtMillis = iterationStartedAt
                    }

                    val foregroundNetworkOperations = activeNetworkOperationsState.value
                    if (shouldDeferCloudHealthProbe(
                        activeNetworkOperations = foregroundNetworkOperations,
                        transportUnavailable = cloudTransportStatusState.value == CLOUD_TRANSPORT_STATUS_UNAVAILABLE,
                        resumedAfterPause = resumedAfterLongPause || explicitlyWoken || networkChanged,
                        previousProbeFailed = previousProbeFailed,
                        nowMillis = iterationStartedAt,
                        lastProbeAtMillis = lastProbeAtMillis
                    )) {
                        // Real user requests already provide grounded transport evidence. Avoid making
                        // a synthetic probe compete with useful foreground traffic on a slow uplink.
                        logCloudConnectionDiagnostic(
                            "health probe deferred activeNetworkOperations=$foregroundNetworkOperations " +
                                "status=${cloudTransportStatusName(cloudTransportStatusState.value)}"
                        )
                        cloudHealthRetryWakeup.await(wakeRevision, CLOUD_CONNECTION_HEALTH_CHECK_BUSY_DEFER_MILLIS)
                        continue
                    }

                    val hasLocalAccount = runCatching { getStoredUserAuthTokens?.invoke() != null }
                        .getOrDefault(false)
                    val probeStartedAt = getCurrentTimeMillis()
                    lastProbeAtMillis = probeStartedAt
                    logCloudConnectionDiagnostic(
                        "health probe start server=$configuredServerUrl " +
                            "status=${cloudTransportStatusName(cloudTransportStatusState.value)} " +
                            "realtime=$realtimeReportedConnected hasTokens=$hasLocalAccount " +
                            "resumedAfterPause=$resumedAfterLongPause"
                    )
                    val response = withTimeoutOrNull(CLOUD_CONNECTION_HEALTH_CHECK_TIMEOUT_MILLIS) {
                        cloudConnectionHealthProbeMutex.withLock { cloudConnectionProbeRequest("health") }
                    }

                    val serverAvailable = response != null && !response.negative
                    previousProbeFailed = !serverAvailable
                    logCloudConnectionDiagnostic(
                        "health probe result available=$serverAvailable http=${response?.httpStatusCode ?: -1} " +
                            "negative=${response?.negative} transportFailure=${response?.transportFailure} " +
                            "elapsed=${getCurrentTimeMillis() - probeStartedAt}ms"
                    )

                    if (serverAvailable) {
                        unavailableRound = 0
                        if (hasLocalAccount) { ActiveStores.retryPending(); AccountAppModes.retryPending() }
                        // A network-change hint must survive a failed first probe. Refresh the old
                        // network's socket once, after AITA is reachable on the replacement network.
                        if (pendingNetworkRestart) {
                            if (getStoredUserAuthTokens?.invoke() != null) restartRealtimeUpdates()
                            pendingNetworkRestart = false
                        }
                        val realtimeConnectedNow = realtimeUpdatesJob.isConnected && !resumedAfterLongPause
                        markCloudTransportReachableForNotifications(
                            authenticated = realtimeConnectedNow,
                            authRefreshRequired = if (realtimeConnectedNow) false else null,
                            // One fresh AITA probe proves transport. The banner keeps its own settle
                            // window; account validation still runs separately and may reject login.
                            forceRecovery = true
                        )

                        if (hasLocalAccount && !realtimeConnectedNow) {
                            launchAutomaticCloudConnectionRecovery(
                                knownReachabilityResponse = response!!,
                                forceRealtimeRestart = resumedAfterLongPause
                            )
                        }
                    } else if (markCloudTransportUnavailableForNotifications(reason = "health_probe")) {
                        cancelRealtimeUpdatesSocketAfterReachabilityFailure()
                    }

                    val delayMillis = if (!serverAvailable) {
                        cloudConnectionUnavailableProbeDelayMillis(unavailableRound).also {
                            unavailableRound = (unavailableRound + 1).coerceAtMost(3)
                        }
                    } else when (cloudTransportStatusState.value) {
                        CLOUD_TRANSPORT_STATUS_REACHABLE -> {
                            unavailableRound = 0
                            if (hasLocalAccount && !realtimeUpdatesJob.isConnected) 5_000L
                            else CLOUD_CONNECTION_HEALTH_CHECK_REACHABLE_INTERVAL_MILLIS
                        }
                        CLOUD_TRANSPORT_STATUS_AUTH_REFRESH_REQUIRED -> {
                            unavailableRound = 0
                            CLOUD_CONNECTION_HEALTH_CHECK_AUTH_REQUIRED_INTERVAL_MILLIS
                        }
                        CLOUD_TRANSPORT_STATUS_UNAVAILABLE -> {
                            cloudConnectionUnavailableProbeDelayMillis(unavailableRound).also {
                                unavailableRound = (unavailableRound + 1).coerceAtMost(3)
                            }
                        }
                        else -> {
                            unavailableRound = 0
                            CLOUD_CONNECTION_HEALTH_CHECK_UNKNOWN_INTERVAL_MILLIS
                        }
                    }
                    cloudHealthRetryWakeup.await(wakeRevision, cloudRecoveryAwareProbeDelayMillis(
                        serverAvailable = serverAvailable,
                        awaitingRecoveryConfirmation = cloudTransportStatusState.value == CLOUD_TRANSPORT_STATUS_UNAVAILABLE,
                        ordinaryDelayMillis = delayMillis
                    ))
                } catch (throwable: Throwable) {
                    ensureConnectionOwnerActive(throwable)
                    // A single cache, token, callback, or reconciliation exception must never kill the
                    // only monitor capable of bringing an unattended client back online.
                    logCloudConnectionDiagnostic(
                        "health monitor iteration failed ${throwable.message ?: throwable}"
                    )
                    cloudHealthRetryWakeup.await(wakeRevision, CLOUD_CONNECTION_HEALTH_MONITOR_EXCEPTION_RETRY_MILLIS)
                }
            }
        } finally {
            cloudConnectionHealthMonitorJob.clear(thisJob)
        }
    }
}

fun stopRealtimeUpdates() {
    realtimeUpdatesJob.cancel()
}

fun restartRealtimeUpdates() {
    stopRealtimeUpdates()
    startRealtimeUpdates()
}

private suspend fun currentRealtimeAccessTokenOrNull(): String? {
    val validation = ensureCloudSessionReadyForProtectedRequest()
    if (validation.negative) return null

    val latest = getStoredUserAuthTokens?.invoke() ?: return null
    if (!latest.accessTokenIsStillUsableForNetwork()) return null
    return latest.accessToken.takeIf { it.isNotBlank() }
}

private fun Throwable.isRealtimeUnauthorizedFailure(): Boolean {
    if ((this as? ResponseException)?.response?.status == HttpStatusCode.Unauthorized) return true
    val summary = toString().lowercase()
    return "401" in summary && ("unauthorized" in summary || "websocket" in summary || "handshake" in summary)
}

fun startRealtimeUpdates() {
    startCloudConnectionHealthMonitor()

    realtimeUpdatesJob.startIfIdle(GlobalScope, Dispatchers.ourIo) {
        val thisJob = coroutineContext[Job]
        var reconnectDelayMillis = 1_000L
        try {
            while (isActive) {
                val retryRevision = realtimeRetryWakeup.revision
                try {
                    // A token-validation mutex/refresh can stall too, not just the WebSocket handshake.
                    val sessionGeneration = currentAuthenticatedSessionGeneration()
                    var accessToken = withTimeoutOrNull(60_000L) { currentRealtimeAccessTokenOrNull() }
                    if (!authenticatedSessionGenerationIsCurrent(sessionGeneration)) accessToken = null

                    if (accessToken.isNullOrBlank()) {
                        realtimeUpdatesJob.setConnected(thisJob, false)
                        val retryDelay = when (cloudTransportStatusState.value) {
                            CLOUD_TRANSPORT_STATUS_AUTH_REFRESH_REQUIRED -> CLOUD_CONNECTION_HEALTH_CHECK_AUTH_REQUIRED_INTERVAL_MILLIS
                            CLOUD_TRANSPORT_STATUS_UNAVAILABLE -> CLOUD_CONNECTION_HEALTH_CHECK_UNAVAILABLE_INTERVAL_MILLIS
                            else -> 5_000L
                        }
                        realtimeRetryWakeup.await(retryRevision, retryDelay)
                        continue
                    }

                    var openedRealtimeSession = false
                    var openedRealtimeSessionAtMillis = 0L
                    var authenticationUnavailable = false
                    val serverUrlCandidates = resolvedServerUrlCandidates(null)

                    candidateLoop@ for (realtimeBaseUrl in serverUrlCandidates) {
                        var retriedAfterAuthRecovery = false

                        while (isActive) {
                            val realtimeUrl = realtimeBaseUrl.toRealtimeWebSocketUrl(
                                globalAppConfigurationState.payloadValue.realtimeUpdatesPath
                            )

                            try {
                                withRealtimeHandshakeDeadline { handshakeCompleted ->
                                    httpClient.webSocket(request = {
                                        url(realtimeUrl)
                                        pinSessionAuthorization(requireNotNull(accessToken))
                                        timeout {
                                            connectTimeoutMillis = 10_000L
                                            requestTimeoutMillis = Long.MAX_VALUE
                                            socketTimeoutMillis = Long.MAX_VALUE
                                        }
                                    }) {
                                        val session = this
                                        openedRealtimeSession = true
                                        openedRealtimeSessionAtMillis = getCurrentTimeMillis()
                                        try {
                                            currentCoroutineContext().ensureActive()
                                            if (!realtimeUpdatesJob.owns(thisJob)) return@webSocket
                                            val serverHello = awaitAitaRealtimeHello(
                                                sendHello = {
                                                    session.outgoing.send(
                                                        Frame.Text(
                                                            jsonBase.encodeToString(
                                                                RealtimeClientHelloDataModel(
                                                                    activeStoreId = activeStoreIdState.value,
                                                                    language = appLanguageState.value,
                                                                    platform = getPlatformName(),
                                                                    clientTimeMillis = getCurrentTimeMillis(),
                                                                    heartbeatVersion = AITA_REALTIME_HEARTBEAT_VERSION
                                                                )
                                                            )
                                                        )
                                                    )
                                                },
                                                receiveHello = {
                                                    var hello: RealtimeUpdateDataModel? = null
                                                    while (hello == null) {
                                                        currentCoroutineContext().ensureActive()
                                                        val frame = session.incoming.receiveCatching().getOrNull()
                                                            ?: throw IllegalStateException("Realtime closed before server greeting")
                                                        val text = (frame as? Frame.Text)?.readText() ?: continue
                                                        val update = runCatching {
                                                            jsonBase.decodeFromString<RealtimeUpdateDataModel>(text)
                                                        }.getOrNull()
                                                        if (update?.type == "connected") hello = update
                                                    }
                                                    hello
                                                }
                                            )
                                            ensureActive()
                                            if (!realtimeUpdatesJob.owns(thisJob) ||
                                                !authenticatedSessionGenerationIsCurrent(sessionGeneration)) return@webSocket
                                            handshakeCompleted()
                                            val usesHeartbeat = aitaRealtimeUsesHeartbeat(serverHello.type, serverHello.heartbeatIntervalMillis)
                                            rememberReachableServerUrl(realtimeBaseUrl)
                                            markCloudAccessTokenValidated(accessToken.orEmpty())
                                            markCloudTransportReachableForNotifications(
                                                authenticated = true,
                                                authRefreshRequired = false,
                                                forceRecovery = true
                                            )
                                            realtimeUpdatesJob.setConnected(thisJob, true)

                                            // Every completed reconnect requests a catch-up. The scheduler coalesces it
                                            // at a bounded trailing edge rather than losing missed events under a cooldown.
                                            launchCloudConnectionReconciliation("websocket_connected", false)
                                            var lastAntiEntropyAt = getCurrentTimeMillis()

                                            while (isActive) {
                                                val received = if (usesHeartbeat) {
                                                    withTimeoutOrNull(AITA_REALTIME_HEARTBEAT_TIMEOUT_MILLIS) {
                                                        session.incoming.receiveCatching()
                                                    } ?: throw IllegalStateException("Realtime heartbeat timed out")
                                                } else session.incoming.receiveCatching()
                                                ensureActive()
                                                if (!realtimeUpdatesJob.owns(thisJob) ||
                                                    !authenticatedSessionGenerationIsCurrent(sessionGeneration)) return@webSocket
                                                val frame = received.getOrNull() ?: break
                                                val text = (frame as? Frame.Text)?.readText() ?: continue
                                                val update = runCatching {
                                                    jsonBase.decodeFromString<RealtimeUpdateDataModel>(text)
                                                }.getOrNull()
                                                if (update?.type == "heartbeat") {
                                                    if (realtimeUpdatesJob.owns(thisJob)) markCloudTransportReachableForNotifications(
                                                        authenticated = true, authRefreshRequired = false, forceRecovery = true)
                                                    val now = getCurrentTimeMillis()
                                                    if (now - lastAntiEntropyAt >= 120_000L || now < lastAntiEntropyAt) {
                                                        lastAntiEntropyAt = now
                                                        scheduleRealtimeRefresh(reason = "connection_sync", entity = "all")
                                                    }
                                                    continue // A heartbeat is not itself a mutation; catch-up is bounded to two minutes.
                                                }
                                                if (
                                                    update != null &&
                                                    update.type != "connected" &&
                                                    realtimeUpdateIsRelevantToCurrentContext(
                                                        entity = update.entity,
                                                        updateStoreId = update.storeId,
                                                        activeStoreId = activeStoreIdState.value,
                                                        appMode = appModeState.value,
                                                        familyStoreIds = currentRealtimeStoreFamily()
                                                    )
                                                ) {
                                                    scheduleRealtimeRefresh(
                                                        reason = update.reason ?: update.entity,
                                                        entity = update.entity,
                                                        updateId = update.id
                                                    )
                                                }
                                            }
                                        } finally {
                                            // Do not leave a closed socket published as connected while its
                                            // close handshake is waiting. Ktor also closes its incoming side.
                                            realtimeUpdatesJob.setConnected(thisJob, false)
                                            session.cancel()
                                        }
                                    }
                                }

                                break@candidateLoop
                            } catch (throwable: Throwable) {
                                ensureConnectionOwnerActive(throwable)
                                logCloudConnectionDiagnostic(
                                    "realtime connect failed base=$realtimeBaseUrl unauthorized=${throwable.isRealtimeUnauthorizedFailure()} " +
                                        networkFailureSummary(throwable)
                                )

                                if (throwable.isRealtimeUnauthorizedFailure()) {
                                    invalidateCloudAccessTokenValidation(accessToken)
                                    if (!authenticatedSessionGenerationIsCurrent(sessionGeneration)) break@candidateLoop
                                    if (!retriedAfterAuthRecovery) {
                                        retriedAfterAuthRecovery = true
                                        val validation = ensureCloudSessionReadyForProtectedRequest()
                                        if (!validation.negative && authenticatedSessionGenerationIsCurrent(sessionGeneration)) {
                                            accessToken = getStoredUserAuthTokens?.invoke()?.accessToken
                                            if (!accessToken.isNullOrBlank()) continue
                                        }
                                    }

                                    authenticationUnavailable = true
                                    accessToken = null
                                    break@candidateLoop
                                }

                                // DNS, tunnel, timeout and ordinary WebSocket failures are transport failures,
                                // not evidence that the refresh token should be rotated. Try the next alias once.
                                break
                            }
                        }
                    }

                    if (isActive) {
                        realtimeUpdatesJob.setConnected(thisJob, false)

                        if (authenticationUnavailable) {
                            realtimeRetryWakeup.await(retryRevision, CLOUD_CONNECTION_HEALTH_CHECK_AUTH_REQUIRED_INTERVAL_MILLIS)
                            continue
                        }

                        if (openedRealtimeSession) {
                            // The HTTP server may still be reachable while only the WebSocket dropped. Do not
                            // add a REST ping after every normal socket close; reconnect with bounded backoff.
                            val livedMillis = (getCurrentTimeMillis() - openedRealtimeSessionAtMillis).coerceAtLeast(0L)
                            if (livedMillis >= 30_000L) reconnectDelayMillis = 1_000L
                            val delayMillis = reconnectDelayMillis.coerceAtLeast(REALTIME_AFTER_WEBSOCKET_CLOSE_MIN_DELAY_MILLIS)
                            realtimeRetryWakeup.await(retryRevision, realtimeRetryDelayMillis(
                                delayMillis, cloudTransportStatusState.value == CLOUD_TRANSPORT_STATUS_REACHABLE))
                            reconnectDelayMillis = (reconnectDelayMillis * 2).coerceAtMost(10_000L)
                            continue
                        }

                        // A failed WebSocket handshake is not enough evidence to launch another REST ping:
                        // the single health-monitor loop already owns transport probing. Keeping those duties
                        // separate prevents realtime reconnects from multiplying /auth/ping traffic.
                        val retryDelayMillis = when (cloudTransportStatusState.value) {
                            CLOUD_TRANSPORT_STATUS_AUTH_REFRESH_REQUIRED -> CLOUD_CONNECTION_HEALTH_CHECK_AUTH_REQUIRED_INTERVAL_MILLIS
                            CLOUD_TRANSPORT_STATUS_UNAVAILABLE -> realtimeRetryDelayMillis(reconnectDelayMillis, false)
                            else -> realtimeRetryDelayMillis(reconnectDelayMillis,
                                cloudTransportStatusState.value == CLOUD_TRANSPORT_STATUS_REACHABLE)
                        }
                        realtimeRetryWakeup.await(retryRevision, retryDelayMillis)
                        reconnectDelayMillis = (reconnectDelayMillis * 2).coerceAtMost(10_000L)
                    }
                } catch (failure: Throwable) {
                    ensureConnectionOwnerActive(failure)
                    realtimeUpdatesJob.setConnected(thisJob, false)
                    logCloudConnectionDiagnostic("realtime attempt failed " + networkFailureSummary(failure))
                    realtimeRetryWakeup.await(retryRevision, realtimeRetryDelayMillis(
                        reconnectDelayMillis, cloudTransportStatusState.value == CLOUD_TRANSPORT_STATUS_REACHABLE))
                    reconnectDelayMillis = (reconnectDelayMillis * 2).coerceAtMost(10_000L)
                }
            }
        } finally {
            // A cancelled old socket must not clear a replacement socket's state.
            realtimeUpdatesJob.clear(thisJob)
        }
    }
}
fun refreshCloudConnectionManually() {
    manualCloudConnectionRefreshGeneration += 1L
    val generation = manualCloudConnectionRefreshGeneration
    val previousManualJob = manualCloudConnectionRefreshJob
    previousManualJob?.cancel()

    manualCloudConnectionRefreshJob = GlobalScope.launch(Dispatchers.ourIo, start = CoroutineStart.LAZY) {
        val thisJob = coroutineContext[Job]
        try {
            // A second press supersedes a stale manual attempt instead of being silently ignored.
            // The old implementation simply returned while its mutex was locked, which made the
            // top-right Connect action appear permanently dead after a stalled synchronization.
            withTimeoutOrNull(5_000L) {
                previousManualJob?.cancelAndJoin()
            }

            manualCloudConnectionRefreshMutex.withLock {
                if (generation != manualCloudConnectionRefreshGeneration) return@withLock
                cloudConnectionManualRefreshInProgressState.emit(true)

                val previousMonitorJob = cloudConnectionHealthMonitorJob.cancel()
                val previousRecoveryJob = cloudConnectionRecoveryJob.cancel()
                withTimeoutOrNull(5_000L) {
                    previousMonitorJob?.join()
                    previousRecoveryJob?.join()
                }

                logCloudConnectionDiagnostic(
                    "manual refresh start server=${globalAppConfigurationState.payloadValue.serverUrl.first}"
                )

                val result = withTimeoutOrNull(60_000L) {
                    recoverCloudConnectionFast(
                        reason = "manual",
                        forceRejectedRefreshRetry = true,
                        forcePresentationRecovery = true,
                        forceRealtimeRestart = true
                    )
                } ?: CloudConnectionRecoveryResult(false, false, false, unavailableCloudConnectionResponse())

                logCloudConnectionDiagnostic(
                    "manual refresh result success=${result.success} " +
                        "transportAvailable=${result.transportAvailable} " +
                        "hasLocalAccount=${result.hasLocalAccount} " +
                        "http=${result.response.httpStatusCode}"
                )

                if (!result.success) {
                    postInAppNotification(
                        result.response.message ?: if (result.transportAvailable) {
                            cloudSessionExpiredMessage()
                        } else {
                            localizedStringResourceMessage(
                                id = 1140,
                                main = "Can’t reach AITA server. Check Wi‑Fi or server address.",
                                ru = "Сервер AITA недоступен. Проверьте Wi‑Fi или адрес сервера.",
                                kk = "AITA сервері қолжетімсіз. Wi‑Fi немесе сервер мекенжайын тексеріңіз."
                            )
                        },
                        if (result.transportAvailable) NotificationType.Neutral else NotificationType.Negative,
                        transient = !result.transportAvailable
                    )
                    return@withLock
                }

                if (result.hasLocalAccount) {
                    launchCloudConnectionReconciliation(
                        reason = "manual_reconnect",
                        forceBroadRefresh = true
                    )
                }

                postInAppNotification(
                    result.response.message ?: localizedStringResourceMessage(
                        id = 1138,
                        main = "Server connection available",
                        ru = "Сервер доступен",
                        kk = "Сервер қолжетімді"
                    ),
                    NotificationType.Positive
                )
            }
        } catch (throwable: Throwable) {
            ensureConnectionOwnerActive(throwable)
            logCloudConnectionDiagnostic(
                "manual refresh failed ${throwable.message ?: throwable}"
            )
            cancelRealtimeUpdatesSocketAfterReachabilityFailure()
            markCloudTransportUnavailableForNotifications(
                forceConfirmation = true,
                reason = "manual_refresh_exception"
            )
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
            if (generation == manualCloudConnectionRefreshGeneration) {
                startCloudConnectionHealthMonitor()
                logCloudConnectionDiagnostic(
                    "manual refresh finish status=${cloudTransportStatusName(cloudTransportStatusState.value)} " +
                        "realtime=${realtimeUpdatesJob.isConnected}"
                )
                cloudConnectionManualRefreshInProgressState.emit(false)
                if (manualCloudConnectionRefreshJob === thisJob) {
                    manualCloudConnectionRefreshJob = null
                }
            }
        }
    }
    manualCloudConnectionRefreshJob?.start()
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
    removeCartReturnBatchSelections(transactionTypeIndex, clientId)
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
        removeCartReturnBatchSelection(transactionTypeIndex, clientId, id)
        removeCartConditionChecks(transactionTypeIndex, clientId, id)
    }
}

suspend fun deleteCartItemById(id: String) {
    appDatabase.app_databaseQueries.deleteById(id)
    removeCartReturnReasonsByGoodsItemId(id)
    removeCartReturnBatchSelectionsByGoodsItemId(id)
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
private var cloudTransportReachableForNotifications = false
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
private const val CLOUD_TRANSPORT_FAILURE_CONFIRMATION_MIN_SIGNALS_WHILE_HEALTHY = 3
private const val CLOUD_TRANSPORT_FAILURE_CONFIRMATION_WINDOW_MILLIS = 8_000L
private const val CLOUD_TRANSPORT_FAILURE_CONFIRMATION_WINDOW_WHILE_HEALTHY_MILLIS = 20_000L
private const val CLOUD_TRANSPORT_FAILURE_MIN_SIGNAL_SPACING_MILLIS = 6_000L
private const val CLOUD_TRANSPORT_FAILURE_MIN_SIGNAL_SPACING_WHILE_HEALTHY_MILLIS = 8_000L
private const val CLOUD_TRANSPORT_FAILURE_SIGNAL_RESET_MILLIS = 180_000L
private const val CLOUD_TRANSPORT_RECOVERY_CONFIRMATION_MIN_SIGNALS = 3
private const val CLOUD_TRANSPORT_RECOVERY_CONFIRMATION_WINDOW_MILLIS = 8_000L
private const val CLOUD_TRANSPORT_RECOVERY_MIN_SIGNAL_SPACING_MILLIS = 3_000L
// Two further probes may each consume the full 20-second deadline plus confirmation delay.
private const val CLOUD_TRANSPORT_RECOVERY_SIGNAL_RESET_MILLIS = 90_000L

private data class CloudTransportSignalWindow(
    val signalCount: Int = 0,
    val firstSignalAtMillis: Long = 0L,
    val lastCountedSignalAtMillis: Long = 0L,
    val startedWhileGroundedHealthy: Boolean = false
)

private val cloudTransportFailureSignalWindowState = MutableStateFlow(CloudTransportSignalWindow())
private val cloudTransportRecoverySignalWindowState = MutableStateFlow(CloudTransportSignalWindow())

private fun String.normalizedNotificationText(): String =
    trim()
        .lowercase()
        .replace(Regex("\\s+"), " ")

private fun String.isCloudSessionRefreshNotificationText(): Boolean {
    val normalized = normalizedNotificationText()
    if (normalized.isBlank()) return false

    val preciseMarkers = listOf(
        // Current precise wording.
        "cloud sign-in expired",
        "sign in again to sync",
        "local data stays available",
        "срок облачного входа истёк",
        "войдите снова для синхронизации",
        "локальные данные останутся доступны",
        "бұлттық кіру мерзімі аяқталды",
        "синхрондау үшін қайта кіріңіз",
        "жергілікті деректер қолжетімді болып қалады",
        // Legacy wording remains recognized so old persisted notices are canonicalized and deduped.
        "cloud session needs refresh",
        "session needs refresh",
        "you remain signed in locally",
        "остаётесь в аккаунте локально",
        "жергілікті түрде аккаунтта",
    )
    if (preciseMarkers.any { marker -> normalized.contains(marker) }) return true

    // Do not classify positive text such as "Cloud session active" as an expiry warning merely
    // because it contains the generic words "cloud session".
    val legacyRussian = normalized.contains("облачный сеанс") &&
        normalized.contains("сеанс нужно обновить")
    val legacyKazakh = normalized.contains("бұлттық сеанс") &&
        normalized.contains("сеансты жаңарту")
    return legacyRussian || legacyKazakh
}

private fun String.isUnreadableServerResponseNotificationText(): Boolean {
    val normalized = normalizedNotificationText()
    if (normalized.isBlank()) return false

    return listOf(
        "server response could not be read",
        "could not read server response",
        "не удалось прочитать ответ сервера",
        "сервер жауабын оқу мүмкін болмады",
        "сервер жауабын оқу",
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
        "connectexception",
        "sockettimeoutexception",
        "timeout has expired",
        "server request failed. please check the server connection",
        "network request failed",
        "connection refused",
        "connection reset",
        "connection aborted",
        "connection closed prematurely",
        "broken pipe",
        "unexpected end of stream",
        "eofexception",
        "network unreachable",
        "host unreachable",
        "failed to connect",
        "no route to host",
        "temporary failure in name resolution",
        "unable to resolve host",
        "no address associated with hostname",
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
        "запрос к серверу не выполнен",
        "проверьте соединение с сервером",
        "соединение сброшено",
        "остаётесь в аккаунте офлайн",
        "сеансы безопасности обновятся",
        "aita сервері қолжетімсіз",
        "сервер қолжетімсіз",
        "сервер офлайн",
        "сервер қосылмаған",
        "серверге сұрау орындалмады",
        "сервер байланысын тексеріп",
        "қосылым үзілді",
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
    main = "Cloud sign-in expired. Sign in again to sync. Your local data stays available.",
    ru = "Срок облачного входа истёк. Войдите снова для синхронизации. Локальные данные останутся доступны.",
    kk = "Бұлттық кіру мерзімі аяқталды. Синхрондау үшін қайта кіріңіз. Жергілікті деректер қолжетімді болып қалады."
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

private fun NotificationDataModel.isLocalOnlyNotification(): Boolean =
    isDeviceFileNotification(this) || isConnectionStatusNotification() || isSessionStatusNotification()

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

private fun cloudTransportPresentationStatus(rawStatus: Int, currentStatus: Int): Int = when (rawStatus) {
    CLOUD_TRANSPORT_STATUS_UNAVAILABLE -> CLOUD_TRANSPORT_STATUS_UNAVAILABLE
    CLOUD_TRANSPORT_STATUS_REACHABLE,
    CLOUD_TRANSPORT_STATUS_AUTH_REFRESH_REQUIRED -> CLOUD_TRANSPORT_STATUS_REACHABLE
    else -> if (currentStatus == CLOUD_TRANSPORT_STATUS_UNKNOWN) {
        CLOUD_TRANSPORT_STATUS_UNKNOWN
    } else {
        // Once reachability has been established, an auth reset or a short diagnostic gap must not
        // push the banner back into its startup/checking state.
        currentStatus
    }
}

private fun cancelPendingCloudConnectionPresentationTransition() {
    while (true) {
        val current = cloudConnectionPresentationState.value
        if (current.pendingStatus == null) return
        val cancelled = current.copy(
            generation = current.generation + 1L,
            pendingStatus = null
        )
        if (cloudConnectionPresentationState.compareAndSet(current, cancelled)) return
    }
}

private fun reserveCloudConnectionPresentationTransition(
    nextPresentationStatus: Int
): CloudConnectionPresentationState? {
    while (true) {
        val current = cloudConnectionPresentationState.value
        if (current.displayedStatus == nextPresentationStatus) {
            cancelPendingCloudConnectionPresentationTransition()
            return null
        }
        if (current.pendingStatus == nextPresentationStatus) return null
        val reserved = current.copy(
            generation = current.generation + 1L,
            pendingStatus = nextPresentationStatus
        )
        if (cloudConnectionPresentationState.compareAndSet(current, reserved)) return reserved
    }
}

private fun scheduleCloudTransportPresentationStatus(rawStatus: Int, reason: String) {
    val currentPresentationState = cloudConnectionPresentationState.value
    val nextPresentationStatus = cloudTransportPresentationStatus(
        rawStatus,
        currentPresentationState.displayedStatus
    )

    if (currentPresentationState.displayedStatus == nextPresentationStatus) {
        // A return to the currently displayed state invalidates an opposite transition that was still
        // settling. Repeated same-state evidence otherwise leaves the active timer untouched.
        cancelPendingCloudConnectionPresentationTransition()
        return
    }

    val transition = reserveCloudConnectionPresentationTransition(nextPresentationStatus) ?: return
    val settleMillis = when {
        nextPresentationStatus == CLOUD_TRANSPORT_STATUS_UNAVAILABLE &&
            transition.displayedStatus == CLOUD_TRANSPORT_STATUS_UNKNOWN ->
            CLOUD_CONNECTION_PRESENTATION_INITIAL_OFFLINE_SETTLE_MILLIS
        nextPresentationStatus == CLOUD_TRANSPORT_STATUS_UNAVAILABLE ->
            CLOUD_CONNECTION_PRESENTATION_OFFLINE_SETTLE_MILLIS
        transition.displayedStatus == CLOUD_TRANSPORT_STATUS_UNAVAILABLE &&
            nextPresentationStatus == CLOUD_TRANSPORT_STATUS_REACHABLE ->
            CLOUD_CONNECTION_PRESENTATION_RECOVERY_SETTLE_MILLIS
        transition.displayedStatus == CLOUD_TRANSPORT_STATUS_UNKNOWN &&
            nextPresentationStatus == CLOUD_TRANSPORT_STATUS_REACHABLE ->
            CLOUD_CONNECTION_PRESENTATION_INITIAL_REACHABLE_SETTLE_MILLIS
        else -> 0L
    }

    GlobalScope.launch(Dispatchers.ourIo) {
        if (settleMillis > 0L) delay(settleMillis)
        if (cloudConnectionPresentationState.value != transition) return@launch

        val latestPresentationStatus = cloudTransportPresentationStatus(
            cloudTransportStatusState.value,
            transition.displayedStatus
        )
        if (latestPresentationStatus != nextPresentationStatus) {
            cloudConnectionPresentationState.compareAndSet(
                transition,
                transition.copy(pendingStatus = null)
            )
            return@launch
        }

        val settled = transition.copy(
            displayedStatus = nextPresentationStatus,
            pendingStatus = null
        )
        if (cloudConnectionPresentationState.compareAndSet(transition, settled)) {
            logCloudConnectionDiagnostic(
                "presentation ${cloudTransportStatusName(transition.displayedStatus)} -> " +
                    "${cloudTransportStatusName(nextPresentationStatus)} reason=$reason settled=${settleMillis}ms"
            )
        }
    }
}

private fun setCloudTransportStatusForDiagnostics(nextStatus: Int, reason: String) {
    while (true) {
        val previousStatus = cloudTransportStatusState.value
        if (previousStatus == nextStatus) break
        if (!cloudTransportStatusState.compareAndSet(previousStatus, nextStatus)) continue
        logCloudConnectionDiagnostic(
            "status ${cloudTransportStatusName(previousStatus)} -> ${cloudTransportStatusName(nextStatus)} " +
                "reason=$reason realtime=${realtimeUpdatesJob.isConnected} " +
                "hasTokens=${getStoredUserAuthTokens?.invoke() != null} activeStore=${activeStoreIdState.value.orEmpty()}"
        )
        break
    }
    scheduleCloudTransportPresentationStatus(nextStatus, reason)
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
    cloudTransportFailureSignalWindowState.value = CloudTransportSignalWindow()
}

private fun clearCloudTransportRecoverySignalsForNotifications() {
    cloudTransportRecoverySignalWindowState.value = CloudTransportSignalWindow()
}

private fun recordCloudTransportFailureSignalForNotifications(reason: String = "transport_failure"): Boolean {
    clearCloudTransportRecoverySignalsForNotifications()

    val now = getCurrentTimeMillis()
    val groundedHealthyNow = realtimeUpdatesJob.isConnected ||
        cloudTransportStatusState.value == CLOUD_TRANSPORT_STATUS_REACHABLE ||
        cloudTransportReachableForNotifications

    val updated = cloudTransportFailureSignalWindowState.updateAndGet { current ->
        val expired = current.firstSignalAtMillis <= 0L ||
            now - current.firstSignalAtMillis > CLOUD_TRANSPORT_FAILURE_SIGNAL_RESET_MILLIS
        if (expired) {
            CloudTransportSignalWindow(
                signalCount = 1,
                firstSignalAtMillis = now,
                lastCountedSignalAtMillis = now,
                startedWhileGroundedHealthy = groundedHealthyNow
            )
        } else {
            val minimumSpacing = if (current.startedWhileGroundedHealthy) {
                CLOUD_TRANSPORT_FAILURE_MIN_SIGNAL_SPACING_WHILE_HEALTHY_MILLIS
            } else {
                CLOUD_TRANSPORT_FAILURE_MIN_SIGNAL_SPACING_MILLIS
            }
            if (now - current.lastCountedSignalAtMillis < minimumSpacing) {
                current
            } else {
                current.copy(
                    signalCount = (current.signalCount + 1).coerceAtMost(1000),
                    lastCountedSignalAtMillis = now
                )
            }
        }
    }

    val requiredSignals = if (updated.startedWhileGroundedHealthy) {
        CLOUD_TRANSPORT_FAILURE_CONFIRMATION_MIN_SIGNALS_WHILE_HEALTHY
    } else {
        CLOUD_TRANSPORT_FAILURE_CONFIRMATION_MIN_SIGNALS
    }
    val requiredWindow = if (updated.startedWhileGroundedHealthy) {
        CLOUD_TRANSPORT_FAILURE_CONFIRMATION_WINDOW_WHILE_HEALTHY_MILLIS
    } else {
        CLOUD_TRANSPORT_FAILURE_CONFIRMATION_WINDOW_MILLIS
    }
    val elapsed = (now - updated.firstSignalAtMillis).coerceAtLeast(0L)
    val confirmed = updated.signalCount >= requiredSignals && elapsed >= requiredWindow

    if (!confirmed && updated.lastCountedSignalAtMillis == now) {
        val minimumSpacing = if (updated.startedWhileGroundedHealthy) {
            CLOUD_TRANSPORT_FAILURE_MIN_SIGNAL_SPACING_WHILE_HEALTHY_MILLIS
        } else {
            CLOUD_TRANSPORT_FAILURE_MIN_SIGNAL_SPACING_MILLIS
        }
        logCloudConnectionDiagnostic(
            "transport failure signal held for confirmation reason=$reason " +
                "signals=${updated.signalCount}/$requiredSignals elapsed=${elapsed}ms/${requiredWindow}ms " +
                "minimumSpacing=${minimumSpacing}ms realtime=${realtimeUpdatesJob.isConnected} " +
                "status=${cloudTransportStatusName(cloudTransportStatusState.value)}"
        )
    }

    return confirmed
}

private fun recordCloudTransportRecoverySignalForNotifications(reason: String = "transport_recovery"): Boolean {
    val now = getCurrentTimeMillis()
    val updated = cloudTransportRecoverySignalWindowState.updateAndGet { current ->
        val expired = current.firstSignalAtMillis <= 0L ||
            now - current.firstSignalAtMillis > CLOUD_TRANSPORT_RECOVERY_SIGNAL_RESET_MILLIS
        if (expired) {
            CloudTransportSignalWindow(
                signalCount = 1,
                firstSignalAtMillis = now,
                lastCountedSignalAtMillis = now
            )
        } else if (now - current.lastCountedSignalAtMillis < CLOUD_TRANSPORT_RECOVERY_MIN_SIGNAL_SPACING_MILLIS) {
            current
        } else {
            current.copy(
                signalCount = (current.signalCount + 1).coerceAtMost(1000),
                lastCountedSignalAtMillis = now
            )
        }
    }

    val elapsed = (now - updated.firstSignalAtMillis).coerceAtLeast(0L)
    val confirmed = updated.signalCount >= CLOUD_TRANSPORT_RECOVERY_CONFIRMATION_MIN_SIGNALS &&
        elapsed >= CLOUD_TRANSPORT_RECOVERY_CONFIRMATION_WINDOW_MILLIS

    if (!confirmed && updated.lastCountedSignalAtMillis == now) {
        logCloudConnectionDiagnostic(
            "transport recovery signal held for confirmation reason=$reason " +
                "signals=${updated.signalCount}/$CLOUD_TRANSPORT_RECOVERY_CONFIRMATION_MIN_SIGNALS " +
                "elapsed=${elapsed}ms/${CLOUD_TRANSPORT_RECOVERY_CONFIRMATION_WINDOW_MILLIS}ms " +
                "minimumSpacing=${CLOUD_TRANSPORT_RECOVERY_MIN_SIGNAL_SPACING_MILLIS}ms"
        )
    }

    return confirmed
}

@PublishedApi
internal fun markCloudTransportUnavailableForNotifications(
    forceConfirmation: Boolean = false,
    reason: String = "transport_failure"
): Boolean {
    clearCloudTransportRecoverySignalsForNotifications()
    if (cloudTransportFailureSignalWindowState.value.signalCount == 0) cloudHealthRetryWakeup.request()

    if (
        cloudTransportStatusState.value == CLOUD_TRANSPORT_STATUS_UNAVAILABLE &&
        !cloudTransportReachableForNotifications
    ) {
        // A previous positive response may have cancelled the banner's pending offline transition
        // before full recovery confirmation. Continued failure evidence starts a fresh settle window
        // instead of letting one lucky response hide a later sustained outage forever.
        scheduleCloudTransportPresentationStatus(
            CLOUD_TRANSPORT_STATUS_UNAVAILABLE,
            "continued_transport_failure reason=$reason"
        )
        return true
    }

    if (forceConfirmation) {
        clearCloudTransportFailureSignalsForNotifications()
    } else if (!recordCloudTransportFailureSignalForNotifications(reason)) {
        return false
    }

    cloudTransportLastUnavailableAtMillis = getCurrentTimeMillis()
    val nextStatus = CLOUD_TRANSPORT_STATUS_UNAVAILABLE

    // Transport and authentication are orthogonal. While offline the connection banner takes visual
    // precedence, but a refresh token that was already rejected must remain remembered so a later
    // public ping cannot incorrectly turn the app green and restart the same refresh loop.

    if (cloudTransportReachableForNotifications || cloudTransportStatusState.value != nextStatus) {
        cloudTransportFailureNotificationPending = true
        cloudTransportFailureNoticePostedForCurrentOutage = false
    }

    setCloudTransportStatusForDiagnostics(nextStatus, "transport_failure_confirmed reason=$reason")
    cloudTransportReachableForNotifications = false
    cloudTransportRecoveryNotificationPending = false
    return true
}

@PublishedApi
internal fun markCloudTransportReachableForNotifications(
    authenticated: Boolean = false,
    authRefreshRequired: Boolean? = null,
    forceRecovery: Boolean = false
): Boolean {
    val wasUnavailable = cloudTransportStatusState.value == CLOUD_TRANSPORT_STATUS_UNAVAILABLE

    if (wasUnavailable && !forceRecovery && !authenticated) {
        // Even one authoritative server response is enough to stop a pending visual outage. Raw
        // diagnostics still require the full recovery quorum below, so operational state remains
        // conservative while the user-facing banner avoids a green→red→green flash.
        scheduleCloudTransportPresentationStatus(
            CLOUD_TRANSPORT_STATUS_REACHABLE,
            "positive_recovery_evidence"
        )
        if (!recordCloudTransportRecoverySignalForNotifications()) return false
    }

    clearCloudTransportRecoverySignalsForNotifications()
    clearCloudTransportFailureSignalsForNotifications()
    cloudTransportLastReachableAtMillis = getCurrentTimeMillis()

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
        "transport_reachable authenticated=$authenticated authRefreshRequired=$authRefreshRequired forceRecovery=$forceRecovery"
    )
    cloudTransportReachableForNotifications = true
    cloudTransportFailureNotificationPending = false
    cloudTransportFailureNoticePostedForCurrentOutage = false
    if (wasUnavailable) {
        cloudHealthRetryWakeup.request()
        realtimeRetryWakeup.request()
        cloudTransportRecoveryNotificationPending = hadVisibleOutage &&
            nextStatus == CLOUD_TRANSPORT_STATUS_REACHABLE
    }
    return true
}

@PublishedApi
internal fun markCloudSessionNeedsRefreshForNotifications() {
    // Remember the grounded auth failure even if an active transport outage temporarily owns the
    // visible banner. Once reachability returns, markCloudTransportReachable... will reveal this state.
    cloudSessionRefreshRequiredForNotifications = true
    if (recentCloudTransportFailureIsDominant()) {
        logCloudConnectionDiagnostic("session-refresh signal hidden while transport is unavailable")
        setCloudTransportStatusForDiagnostics(CLOUD_TRANSPORT_STATUS_UNAVAILABLE, "auth_refresh_hidden_by_transport_failure")
        return
    }

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
        cloudTransportRecoveryNotificationPending = false
        markCloudTransportReachableForNotifications(authRefreshRequired = null)
        return false
    }

    if (text.isUnreadableServerResponseNotificationText()) {
        val serverIsAlreadyReachable = realtimeUpdatesJob.isConnected ||
            cloudTransportStatusState.value == CLOUD_TRANSPORT_STATUS_REACHABLE
        val serverIsBeingProbed = activeNetworkOperationsState.value > 0 &&
            cloudTransportStatusState.value == CLOUD_TRANSPORT_STATUS_UNKNOWN
        if (serverIsAlreadyReachable || serverIsBeingProbed) return false
    }

    if (!text.isCloudTransportFailureNotificationText()) return true

    // Connection state is persistent UI, not a popup event. Keep gathering evidence for the
    // grounded state machine, but never flash a disconnect notification over the user's work.
    markCloudTransportUnavailableForNotifications(reason = "notification_transport_signal")
    cloudTransportFailureNotificationPending = false
    cloudTransportFailureNoticePostedForCurrentOutage = false
    cloudTransportRecoveryNotificationPending = false
    return false
}

// Rebuild only when the immutable resource payload changes, not for each row or keystroke.
@Volatile private var notificationResourceIndex: Pair<List<LocalizedStringGroupDataModel>, EventResourceCatalogue>? = null

fun currentEventResourceCatalogue(): EventResourceCatalogue {
    val groups = stringsState.payloadValue.orEmpty()
    notificationResourceIndex?.takeIf { it.first === groups }?.let { return it.second }
    return EventResourceCatalogue(groups).also { notificationResourceIndex = groups to it }
}

private fun createNotificationDataModel(
    message: String,
    type: NotificationType,
    title: String = "",
    category: String = when (type) {
        NotificationType.Positive -> "positive"
        NotificationType.Negative -> "negative"
        NotificationType.Neutral -> "neutral"
    },
    translations: List<LocalizedStringDataModel> = emptyList()
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
    val resources = currentEventResourceCatalogue()
    val messageReference = when (cleanCategory) {
        NOTIFICATION_CONNECTION_CATEGORY -> resources.referenceFor(cleanMessage) ?: legacyEventMessageReference(cleanMessage)
        NOTIFICATION_SESSION_CATEGORY -> EventMessageReference("resource.91")
        else -> translations.eventMessageReferenceOrNull() ?: resources.referenceFor(translations)
            ?: legacyEventMessageReference(message) ?: resources.referenceFor(message)
    }
    val titleReference = legacyEventMessageReference(cleanTitle) ?: resources.referenceFor(cleanTitle)
    val now = getCurrentTimeMillis()
    val storeId = activeStoreIdState.value
    val bucket = now / IN_APP_NOTIFICATION_ID_BUCKET_MILLIS
    val dedupeStoreId = if (cleanCategory == NOTIFICATION_CONNECTION_CATEGORY || cleanCategory == NOTIFICATION_SESSION_CATEGORY) "" else storeId.orEmpty()
    val stableKey = listOf(
        userAccountState.payloadValue?.id.orEmpty(),
        dedupeStoreId,
        cleanCategory,
        eventTextIdentity(eventTextForStorage(cleanTitle, reference = titleReference, resources = resources)),
        eventTextIdentity(eventTextForStorage(cleanMessage, translations, messageReference, resources))
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
        isSavedOnServer = false,
        messageTemplate = messageReference,
        titleTemplate = titleReference,
        messageTranslations = translations
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
        eventTitleIdentity(currentEventResourceCatalogue()),
        eventMessageIdentity(currentEventResourceCatalogue())
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
    notificationHistoryMutex.withLock {
        if (notification.userId != null && notification.userId != userAccountState.payloadValue?.id) return@withLock
        val old = notificationsState.payloadValue.orEmpty()
        notificationsState.emit(DataState.Success(
            (listOf(notification) + old.filterNot { it.isLocalOnlyNotification() })
                .distinctBy { it.id }.dedupeRecentNotificationHistory().take(LOCAL_NOTIFICATION_HISTORY_LIMIT)
        ))
    }
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
    isDeviceFileNotification(notification) && hasDeviceNotificationFile(notification.id) -> 12_000L
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
        if (isDeviceFileNotification(preparedNotification) && deviceFileNotification(preparedNotification.id) == null) return@withLock
        pruneDismissedNotificationPopupKeys(now)
        if (!shouldPostNotificationConsideringCloudTransport(preparedNotification)) return@withLock

        // The persistent grounded banner is the single source of truth for connection loss and
        // recovery. Do not let connection-status events occupy popup slots, flash over the UI,
        // or displace actionable notifications; session-refresh notices remain independently visible.
        if (preparedNotification.isConnectionStatusNotification()) return@withLock

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
    if (isSubscriptionAccessNotice(message)) return
    val text = message?.extractLocalizedString(appLanguageState.value)?.takeIf { it.isNotBlank() } ?: return
    pushInAppNotificationNow(createNotificationDataModel(text, type, translations = message), transient)
}

private suspend fun postInAppNotificationNow(message: String, type: NotificationType, transient: Boolean = false) {
    if (isSubscriptionAccessNotice(message)) return
    if (message.trim().isBlank()) return
    pushInAppNotificationNow(createNotificationDataModel(message, type), transient)
}

fun postInAppNotification(
    message: List<LocalizedStringDataModel>?,
    type: NotificationType,
    transient: Boolean = false
) {
    if (isSubscriptionAccessNotice(message)) return
    val text = message?.extractLocalizedString(appLanguageState.value)?.takeIf { it.isNotBlank() } ?: return
    pushInAppNotification(createNotificationDataModel(text, type, translations = message), transient)
}

fun postInAppNotification(message: String, type: NotificationType, transient: Boolean = false) {
    if (isSubscriptionAccessNotice(message)) return
    if (message.trim().isBlank()) return
    pushInAppNotification(createNotificationDataModel(message, type), transient)
}

/** Saved-file messages and capabilities live on this device for this login only. */
suspend fun postDeviceFileNotification(
    message: String,
    savedFile: SavedPdfFile?,
    owner: ReceiptActionOwner,
    type: NotificationType = NotificationType.Positive,
    messageTemplate: EventMessageReference? = null
) {
    if (!owner.isCurrent()) return
    val notification = createNotificationDataModel(message, type).let { created ->
        created.copy(category = DEVICE_FILE_NOTIFICATION_CATEGORY, source = "device",
            messageTemplate = messageTemplate ?: created.messageTemplate)
    }
    rememberDeviceFileNotification(notification, savedFile, owner)
    if (owner.isCurrent()) pushInAppNotificationNow(notification, transient = true)
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
    if (notification.message.isBlank() || notification.isLocalOnlyNotification()) return
    val generation = currentAuthenticatedSessionGeneration()
    val account = userAccountState.payloadValue?.id ?: return
    if (notification.userId != null && notification.userId != account) return
    if (getStoredUserAuthTokens?.invoke() == null || !currentCloudSessionIsReadyForBackgroundSync()) return
    saveNotificationMutex.withLock {
        if (!authenticatedSessionGenerationIsCurrent(generation) || userAccountState.payloadValue?.id != account) return@withLock
        val response = networkRequest<NotificationDataModel, NotificationDataModel>(
            method = HttpMethod.Post, endpointUrl = "notifications/add", body = notification,
            expectedSessionGeneration = generation
        )
        notificationHistoryMutex.withLock history@{
            if (!authenticatedSessionGenerationIsCurrent(generation) || userAccountState.payloadValue?.id != account) return@history
            if (!response.negative && response.payload != null) {
                val old = notificationsState.payloadValue.orEmpty()
                val local = old.firstOrNull { it.id == notification.id }
                val saved = response.payload.copy(isSavedOnServer = true,
                    readAtMillis = local?.readAtMillis ?: response.payload.readAtMillis)
                notificationsState.emit(DataState.Success(
                    (listOf(saved) + old.filter { it.id != notification.id && it.id != saved.id && !it.isLocalOnlyNotification() })
                        .distinctBy { it.id }.dedupeRecentNotificationHistory().take(LOCAL_NOTIFICATION_HISTORY_LIMIT)
                ))
            }
        }
    }
}

suspend fun syncPendingNotificationsToServerNow(): Int {
    if (syncPendingNotificationsMutex.isLocked) return 0
    if (getStoredUserAuthTokens?.invoke() == null) return 0
    if (userAccountState.payloadValue == null) return 0
    if (!currentCloudSessionIsReadyForBackgroundSync()) return 0

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
    val generation = currentAuthenticatedSessionGeneration()
    GlobalScope.launch(Dispatchers.ourIo) {
        getNotificationsMutex.withLock {
            if (!authenticatedSessionGenerationIsCurrent(generation)) return@withLock
            if (getStoredUserAuthTokens?.invoke() == null) return@withLock

            val before = notificationHistoryMutex.withLock { notificationsState.payloadValue.orEmpty() }

            val response = networkRequest<List<NotificationDataModel>, Unit>(
                method = HttpMethod.Get,
                endpointUrl = "notifications/get",
                expectedSessionGeneration = generation
            )
            if (!authenticatedSessionGenerationIsCurrent(generation)) return@withLock

            if (!response.negative && response.payload != null) {
                notificationHistoryMutex.withLock history@{
                if (!authenticatedSessionGenerationIsCurrent(generation)) return@history
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

                val merged = mergeNotificationSnapshot(before, currentNotifications, serverNotifications)
                    .dedupeRecentNotificationHistory()
                    .take(LOCAL_NOTIFICATION_HISTORY_LIMIT)
                notificationsState.emit(DataState.Success(merged, response.message))
                // Re-send local offline read marks without replacing the visible list with the response.
                val remoteById = serverNotifications.associateBy { it.id }
                val pendingReadIds = merged.filter { local -> local.readAtMillis != null &&
                    remoteById[local.id]?.let { it.readAtMillis == null && it.createdAtMillis == local.createdAtMillis } == true
                }.map { it.id }
                if (pendingReadIds.isNotEmpty()) markNotificationsRead(pendingReadIds)
                }
                syncPendingNotificationsToServer()
            }
            // A failed refresh does not replace the last usable list or toggle it through Empty.
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

fun markNotificationRead(notificationId: String) = markNotificationsRead(listOf(notificationId))

fun markAllNotificationsRead() = markNotificationsRead(
    notificationsState.payloadValue.orEmpty().filter { it.readAtMillis == null }.map { it.id }
)

fun markNotificationsRead(notificationIds: List<String>) {
    val ids = notificationIds.filter { it.isNotBlank() }.toSet()
    if (ids.isEmpty()) return
    val generation = currentAuthenticatedSessionGeneration()
    val account = userAccountState.payloadValue?.id ?: return
    GlobalScope.launch(Dispatchers.ourIo) {
        markNotificationReadMutex.withLock {
            if (!authenticatedSessionGenerationIsCurrent(generation) || userAccountState.payloadValue?.id != account) return@withLock
            val savedIds = notificationHistoryMutex.withLock {
                val local = notificationsState.payloadValue.orEmpty()
                val now = getCurrentTimeMillis()
                notificationsState.emit(DataState.Success(local.map {
                    if (it.id in ids && it.readAtMillis == null) it.copy(readAtMillis = now) else it
                }))
                local.filter { it.id in ids && it.isSavedOnServer }.map { it.id }
            }
            ids.forEach { removeActiveInAppNotification(it) }
            if (savedIds.isEmpty()) return@withLock
            val response = networkRequest<List<NotificationDataModel>, List<String>>(
                method = HttpMethod.Put, endpointUrl = "notifications/read", body = savedIds,
                expectedSessionGeneration = generation
            )
            notificationHistoryMutex.withLock history@{
                if (!authenticatedSessionGenerationIsCurrent(generation) || userAccountState.payloadValue?.id != account) return@history
                if (!response.negative && response.payload != null) {
                    notificationsState.emit(DataState.Success(applyNotificationReadAcknowledgement(
                        notificationsState.payloadValue.orEmpty(), savedIds.toSet(), response.payload
                    )))
                }
            }
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
    val generation = currentAuthenticatedSessionGeneration()
    GlobalScope.launch(Dispatchers.ourIo) {
        getSupportTicketsMutex.withLock {
            if (!authenticatedSessionGenerationIsCurrent(generation)) return@withLock
            if (getStoredUserAuthTokens?.invoke() == null) return@withLock

            val response = networkRequest<List<SupportTicketDataModel>, Unit>(
                method = HttpMethod.Get,
                endpointUrl = globalAppConfigurationState.payloadValue.getSupportTicketsPath.first,
                expectedSessionGeneration = generation
            )

            if (!authenticatedSessionGenerationIsCurrent(generation)) return@withLock
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
    GlobalScope.launch(Dispatchers.ourIo) {
        supportMessageMutationMutex.withLock {
            supportMessageSendingState.emit(true)
            try {
                val response = networkRequest<SupportTicketDataModel, SupportTicketCreateRequestDataModel>(
                    method = HttpMethod.Post,
                    endpointUrl = globalAppConfigurationState.payloadValue.createSupportTicketPath.first,
                    body = request.copy(
                        subject = request.subject.ifBlank { request.initialMessage.take(80) },
                        initialMessage = request.initialMessage.trim()
                    )
                )

                if (response.negative || response.payload == null) {
                    postInAppNotification(
                        response.message,
                        if (response.transportFailure) NotificationType.Neutral else NotificationType.Negative
                    )
                    onCompleted?.invoke(DataState.Empty(response.message))
                } else {
                    val ticket = response.payload
                    supportTicketsState.emit(
                        DataState.Success(
                            supportTicketsState.payloadValue.orEmpty().upsertSupportTicket(ticket),
                            response.message
                        )
                    )
                    activeSupportTicketIdState.emit(ticket.id)
                    getSupportMessages(ticket.id, markRead = true)
                    postInAppNotification(response.message, NotificationType.Positive)
                    onCompleted?.invoke(DataState.Success(ticket, response.message))
                }
            } finally {
                withContext(NonCancellable) {
                    supportMessageSendingState.emit(false)
                }
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
    GlobalScope.launch(Dispatchers.ourIo) {
        supportMessageMutationMutex.withLock {
            supportMessageSendingState.emit(true)
            try {
                val response = networkRequest<SupportMessageDataModel, SupportMessageSendRequestDataModel>(
                    method = HttpMethod.Post,
                    endpointUrl = globalAppConfigurationState.payloadValue.sendSupportMessagePath.first,
                    body = request.copy(body = request.body.trim())
                )

                if (response.negative || response.payload == null) {
                    postInAppNotification(
                        response.message,
                        if (response.transportFailure) NotificationType.Neutral else NotificationType.Negative
                    )
                    onCompleted?.invoke(DataState.Empty(response.message))
                } else {
                    val message = response.payload
                    supportMessagesState.emit(
                        DataState.Success(
                            supportMessagesState.payloadValue.orEmpty().upsertSupportMessage(message),
                            response.message
                        )
                    )
                    getSupportTickets()
                    onCompleted?.invoke(DataState.Success(message, response.message))
                }
            } finally {
                withContext(NonCancellable) {
                    supportMessageSendingState.emit(false)
                }
            }
        }
    }
}

fun closeSupportTicket(ticketId: String, onCompleted: ((DataState<SupportTicketDataModel>) -> Unit)? = null) {
    if (ticketId.isBlank()) return
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
    GlobalScope.launch(Dispatchers.ourIo) {
        if (!logInMutex.tryLock()) {
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
            return@launch
        }

        try {
            logInInProgressState.emit(true)
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
                installAuthenticatedSession(response.payload)
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
            withContext(NonCancellable) {
                logInInProgressState.emit(false)
                logInMutex.unlock()
            }
        }
    }
}

fun signUpUser(userAuthSignUp: UserAuthSignUpDataModel, serverUrlOverride: String? = null) {
    GlobalScope.launch(Dispatchers.ourIo) {
        if (!signUpUserMutex.tryLock()) {
            postInAppNotification(
                localizedStringResourceMessage(
                    id = 1220,
                    main = "Sign-up is already in progress",
                    ru = "Регистрация уже выполняется",
                    kk = "Тіркелу қазірдің өзінде орындалып жатыр"
                ),
                NotificationType.Neutral,
                transient = true
            )
            return@launch
        }

        try {
            signUpInProgressState.emit(true)
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
                installAuthenticatedSession(response.payload)
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
            withContext(NonCancellable) {
                signUpInProgressState.emit(false)
                signUpUserMutex.unlock()
            }
        }
    }
}

private suspend fun clearAuthenticatedAccountRuntimeState() {
    clearAuthenticatedAccountCaches()
    userAccountState.emit(DataState.Empty())
    storesState.emit(DataState.Empty())
    publishActiveInventoryStoreId(null)

    stockState.emit(DataState.Empty())
    parentStoreStockState.emit(DataState.Empty())
    stockBatchesState.emit(DataState.Empty())
    stockItemBranchAvailabilityState.emit(DataState.Empty())
    stockBatchMoveResultState.emit(DataState.Empty())
    transactionsState.emit(DataState.Empty())
    latestTransactionReceiptSnapshotState.emit(null)
    completeTransactionInProgressState.emit(false)

    suppliersState.emit(DataState.Empty())
    supplierGoodsPricesState.emit(DataState.Empty())
    supplierOrdersState.emit(DataState.Empty())
    supplierOrderLinesState.emit(DataState.Empty())
    supplierPartnershipContractsState.emit(DataState.Empty())
    supplierModeDashboardState.emit(DataState.Empty())
    clearSupplierIdentityFocusForLogout()
    supplierDashboardCacheMutex.withLock {
        supplierDashboardCacheByScope.clear()
        supplierDashboardLastSuccessAtMillisByScope.clear()
        supplierDashboardVisibleScopeKey = ""
    }
    supplierWorkspaceRefreshScheduleMutex.withLock {
        supplierWorkspaceRefreshScopeKey = ""
        supplierWorkspaceLastBaseRefreshAtMillis = 0L
        supplierWorkspaceLastContractsRefreshAtMillis = 0L
    }

    debtorsState.emit(DataState.Empty())
    userFinanceDashboardState.emit(DataState.Empty())
    userWalletState.emit(DataState.Empty())
    userWalletLedgerState.emit(DataState.Empty())
    paymentIntentsState.emit(DataState.Empty())
    clearStoreSubscriptionRuntime()
    CompanyEmployment.clear()
    SupportWorkspaceSignals.changed()
    MarketplaceSignals.changed()
    activeStoreSubscriptionState.emit(DataState.Empty())
    activeStoreSubscriptionChargesState.emit(DataState.Empty())

    securitySessionsState.emit(DataState.Empty())
    securitySessionHistoryState.emit(DataState.Empty())
    storeWorkersState.emit(DataState.Empty())
    storeWorkerMembershipsState.emit(DataState.Empty())
    myWorkerMembershipsState.emit(DataState.Empty())
    incomingWorkerRequestsState.emit(DataState.Empty())
    myWorkerRequestsState.emit(DataState.Empty())
    storeWorkerRoleTemplatesState.emit(DataState.Empty())
    activeWorkshiftState.emit(DataState.Empty())
    workshiftLoginInProgressState.emit(false)
    operationLogsState.emit(DataState.Empty())
    stockItemHistoryState.emit(DataState.Empty())
    storeAnalyticsDashboardState.emit(DataState.Empty())

    cashRegisterAmountState.emit(0.0)
    cashRegisterState.emit(DataState.Empty())
    cashRegisterEventsState.emit(DataState.Empty())
    cashRegisterExtractionsState.emit(DataState.Empty())

    notificationHistoryMutex.withLock { notificationsState.emit(DataState.Empty()) }
    notificationPopupMutex.withLock {
        notificationPopupJobs.values.forEach { it.cancel() }
        notificationPopupJobs.clear()
        serverNotificationPopupIds.clear()
    }
    latestInAppNotificationState.emit(null)
    activeInAppNotificationsState.emit(emptyList())

    supportTicketsState.emit(DataState.Empty())
    supportMessagesState.emit(DataState.Empty())
    activeSupportTicketIdState.emit(null)
    supportMessageSendingState.emit(false)
}

fun logOutUser() {
    GlobalScope.launch(Dispatchers.ourIo) {
        if (!logOutUserMutex.tryLock()) {
            postInAppNotification(
                localizedStringResourceMessage(
                    id = 1221,
                    main = "Sign-out is already in progress",
                    ru = "Выход уже выполняется",
                    kk = "Шығу қазірдің өзінде орындалып жатыр"
                ),
                NotificationType.Neutral,
                transient = true
            )
            return@launch
        }

        try {
            invalidateSupplierNetworkSessionScope()
            val tokenSnapshot = clearAuthenticatedSessionStorage()
            val refreshToken = tokenSnapshot?.refreshToken
            val activeWorkshiftBeforeLogout = activeWorkshiftState.payloadValue
                ?.takeIf { it.isActive && it.endedAtMillis == null }
            var logoutWorkshiftEndRequest: WorkshiftEndRequestDataModel? = null
            var logoutWorkshiftStoreId: String? = null

            if (activeWorkshiftBeforeLogout != null) {
                val endedAtMillis = getCurrentTimeMillis()
                val operationId = createClientOperationId(
                    prefix = "wse",
                    seed = listOf(
                        activeWorkshiftBeforeLogout.id,
                        activeWorkshiftBeforeLogout.storeId,
                        endedAtMillis.toString()
                    ).joinToString(":")
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
            setActiveStoreId(null, syncServer = false)

            if (!refreshToken.isNullOrBlank()) {
                enqueuePendingSessionCleanup(
                    refreshToken = refreshToken,
                    reason = "local_logout",
                    workshiftEnd = logoutWorkshiftEndRequest,
                    workshiftStoreId = logoutWorkshiftStoreId
                )
            }

            clearAuthenticatedAccountRuntimeState()

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
                if (response.negative) {
                    localizedStringResourceMessage(
                        id = 1148,
                        main = "Logged out locally; server session cleanup is queued",
                        ru = "Выход выполнен локально; завершение серверного сеанса поставлено в очередь",
                        kk = "Жергілікті түрде шығу орындалды; сервердегі сеансты аяқтау кезекке қойылды"
                    )
                } else {
                    response.message
                },
                if (response.negative) NotificationType.Neutral else NotificationType.Positive
            )
        } finally {
            logOutUserMutex.unlock()
        }
    }
}

fun getUser(forceLogOut: Boolean = true, applyServerActiveStore: Boolean = true, refreshRelatedData: Boolean = true) {
    val sessionGeneration = currentAuthenticatedSessionGeneration()
    GlobalScope.launch(Dispatchers.ourIo) {
        refreshUserAccountNow(sessionGeneration, forceLogOut, applyServerActiveStore, refreshRelatedData = refreshRelatedData)
    }
}

/** Awaitable account hydration. Sign-in uses fresh account data, never an old cached identity. */
internal suspend fun refreshUserAccountNow(
    sessionGeneration: Long,
    forceLogOut: Boolean = false,
    applyServerActiveStore: Boolean = true,
    restoreCachedAccount: Boolean = true,
    postFailure: Boolean = true,
    refreshRelatedData: Boolean = true
): ResponseDataModel<UserAccountDataModel> = getUserAccountMutex.withLock {
    if (getStoredUserAuthTokens?.invoke() == null) return@withLock cloudSessionExpiredResponse()
    if (!authenticatedSessionGenerationIsCurrent(sessionGeneration)) return@withLock cloudSessionExpiredResponse()
    if (restoreCachedAccount) {
        getStoredUserAccountDataModel?.invoke()?.run {
            if (authenticatedSessionGenerationIsCurrent(sessionGeneration) && userAccountState.payloadValue == null) {
                userAccountState.emit(DataState.Success(this))
                ActiveStores.acceptAccount(this, applyServerActiveStore)
                AccountAppModes.acceptAccount(this, authoritative = false)
                activeStoreIdState.value?.let { loadCachedInventory(it) }
            }
        }
    }
    if (!authenticatedSessionGenerationIsCurrent(sessionGeneration)) return@withLock cloudSessionExpiredResponse()
    val preferenceRevisionAtRequest = AppPreferences.revision
    val response = networkRequest<UserAccountDataModel, Unit>(
        HttpMethod.Get,
        endpointUrl = globalAppConfigurationState.payloadValue.getUserPath.first,
        expectedSessionGeneration = sessionGeneration
    )
    if (!authenticatedSessionGenerationIsCurrent(sessionGeneration)) return@withLock cloudSessionExpiredResponse()
    val payload = response.payload
    if (response.negative || payload == null) {
        if (postFailure && !response.transportFailure) {
            postInAppNotification(response.message,
                if (response.httpStatusCode == HttpStatusCode.Unauthorized.value && forceLogOut) NotificationType.Neutral else NotificationType.Negative)
        }
        return@withLock response.copy(negative = true)
    }

    clearTransientOrNeutralInAppNotifications()
    val account = AccountAppModes.mergeAccount(ActiveStores.mergeAccount(payload))
    userAccountState.emit(DataState.Success(account, response.message))
    ActiveStores.acceptAccount(payload, applyServerActiveStore)
    AccountAppModes.acceptAccount(payload, authoritative = true)
    if (!authenticatedSessionGenerationIsCurrent(sessionGeneration)) return@withLock cloudSessionExpiredResponse()
    val currentAccount = AccountAppModes.mergeAccount(ActiveStores.mergeAccount(account))
    setStoredUserAccountDataModel?.invoke(currentAccount)
    AppPreferences.acceptAccount(currentAccount, preferenceRevisionAtRequest)
    if (!authenticatedSessionGenerationIsCurrent(sessionGeneration)) return@withLock cloudSessionExpiredResponse()

    activeStoreIdState.value?.takeIf { currentStoreHasSubscriptionAccess(it) }?.let { storeId ->
        if (stockState.payloadValue == null || stockLoadStatusState.value.failure != null) getStock(storeId)
        if (stockBatchesState.payloadValue == null || stockBatchesLoadStatusState.value.failure != null) getStockBatches(storeId)
    }
    // Recovery of locally queued notifications is not an optional UI list refresh.
    syncPendingNotificationsToServer()
    // Realtime callers already select the exact resources to refresh. Avoid fetching the
    // same lists a second time, and avoid making a preference edit reload the whole app.
    if (refreshRelatedData) {
        CompanyEmployment.refreshSoon()
        getGlobalAppConfiguration()
        getNotifications()
        getSupportTickets()
        getStores()
        getSuppliers()
        getGenericGoodsCategories()
    }
    startRealtimeUpdates()
    response.copy(payload = currentAccount)
}

fun updateUser(
    userAccountUpdate: UserAccountUpdateDataModel
) {
    val sessionGeneration = currentAuthenticatedSessionGeneration()
    GlobalScope.launch(Dispatchers.ourIo) {
        updateUserMutex.withLock {
            val response = networkRequest<UserAccountDataModel, UserAccountUpdateDataModel>(
                HttpMethod.Put,
                endpointUrl = globalAppConfigurationState.payloadValue.updateUserPath.first,
                body = userAccountUpdate,
                expectedSessionGeneration = sessionGeneration
            )
            if (!authenticatedSessionGenerationIsCurrent(sessionGeneration)) return@withLock

            if (response.negative) {
                postInAppNotification(response.message, NotificationType.Negative)
            } else {
                val account = ActiveStores.mergeAccount(response.payload!!)
                userAccountState.emit(DataState.Success(account, response.message))

                postInAppNotification(
                    response.message,
                    NotificationType.Positive
                )
                setStoredUserAccountDataModel?.invoke(account)
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

fun syncUserPreferencesToServer(postFailure: Boolean = true) = AppPreferences.retryPending(postFailure)

fun forceLogOutUser(
    message: List<LocalizedStringDataModel>? = null,
    postMessage: Boolean = false
) {
    GlobalScope.launch(Dispatchers.ourIo) {
        invalidateSupplierNetworkSessionScope()
        clearAuthenticatedSessionStorage()
        stopRealtimeUpdates()
        setActiveStoreId(null, syncServer = false)
        clearAuthenticatedAccountRuntimeState()

        if (postMessage) {
            postInAppNotification(
                message ?: localizedStringResourceMessage(
                    id = 91,
                    main = "Cloud sign-in expired. Sign in again to sync. Your local data stays available.",
                    ru = "Срок облачного входа истёк. Войдите снова для синхронизации. Локальные данные останутся доступны.",
                    kk = "Бұлттық кіру мерзімі аяқталды. Синхрондау үшін қайта кіріңіз. Жергілікті деректер қолжетімді болып қалады."
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
    contentType: ContentType? = ContentType.Application.Json,
    expectedSessionGeneration: Long? = null
): ResponseDataModel<Response> {
    activeNetworkOperationsState.update { it + 1 }
    val requestStartedAtMillis = getCurrentTimeMillis()
    val requestAccountId = userAccountState.payloadValue?.id
    val requestSessionGeneration = currentAuthenticatedSessionGeneration()

    return try {
        if (expectedSessionGeneration != null && !authenticatedSessionGenerationIsCurrent(expectedSessionGeneration))
            return cloudSessionExpiredResponse()
        ensureCachedGlobalConfigurationPrimedForNetwork()
        if (expectedSessionGeneration != null && !authenticatedSessionGenerationIsCurrent(expectedSessionGeneration))
            return cloudSessionExpiredResponse()

        val subscriptionStoreId = subscriptionRequestStoreId(endpointUrl, headers, query)
        val subscriptionFailure = checkStoreSubscriptionForNetwork(endpointUrl, subscriptionStoreId)
        if (subscriptionFailure != null) return ResponseDataModel(
            message = subscriptionFailure, payload = null, negative = true, httpStatusCode = 402)
        val scopedHeaders = if (subscriptionStoreId != null && storeSubscriptionRequiredForEndpoint(endpointUrl) &&
            headers.keys.none { it.equals("store_id", true) || it.equals("store-id", true) })
            headers + ("store_id" to subscriptionStoreId) else headers
        val protectedEndpoint = cloudEndpointRequiresAuthentication(endpointUrl)
        if (protectedEndpoint) {
            // All concurrent protected startup requests queue behind one authenticated session check.
            // A rejected refresh token is remembered, so later requests fail locally instead of creating
            // another /auth/ping -> /auth/refresh -> 401 storm.
            val validation = ensureCloudSessionReadyForProtectedRequest()
            if (expectedSessionGeneration != null && !authenticatedSessionGenerationIsCurrent(expectedSessionGeneration))
                return cloudSessionExpiredResponse()
            if (validation.negative) {
                return cloudSessionValidationFailureForNetworkRequest(validation)
            }
        }

        val allServerUrlCandidates = resolvedServerUrlCandidates(serverUrl)
        val serverUrlCandidates = if (method.canRetryAcrossAitaServerAliases()) {
            allServerUrlCandidates
        } else {
            // The authenticated /auth/session check above has already proven and remembered an alias.
            // If this is a public mutation, or that proof is stale, select one alias with a harmless GET.
            // The actual POST/PUT/PATCH/DELETE is still emitted exactly once.
            val selection = selectServerUrlForNonReplayableRequest(
                serverUrlCandidates = allServerUrlCandidates,
                reason = "mutation_${method.value.lowercase()}"
            )
            val selectedServerUrl = selection.payload
            if (selectedServerUrl == null) {
                markCloudTransportUnavailableForNotifications(reason = "mutation_alias_selection")
                return ResponseDataModel<Response>(
                    message = selection.message,
                    payload = null,
                    negative = true,
                    httpStatusCode = selection.httpStatusCode,
                    transportFailure = true
                )
            }
            listOf(selectedServerUrl)
        }

        if (!method.canRetryAcrossAitaServerAliases() && allServerUrlCandidates.size > 1) {
            logNetworkAttempt(
                "alias replay disabled for ${method.value}; selected ${serverUrlCandidates.firstOrNull().orEmpty()}"
            )
        }

        var lastServerErrorResponse: ResponseDataModel<Response>? = null
        var lastTransportFailureMessage: List<LocalizedStringDataModel>? = null

        for ((index, resolvedServerUrl) in serverUrlCandidates.withIndex()) {
            var authRetryUsedForCandidate = false
            val requestHeaders = mapOf(HttpHeaders.AcceptLanguage to effectiveAppLanguage(appLanguageState.value)) +
                currentClientDeviceInfoHeaders() + scopedHeaders

            retrySameServer@ while (true) {
                val tokensBeforeRequest = getStoredUserAuthTokens?.invoke()
                if (expectedSessionGeneration != null &&
                    (!authenticatedSessionGenerationIsCurrent(expectedSessionGeneration) || tokensBeforeRequest == null))
                    return cloudSessionExpiredResponse()
                try {
                    val requestUrl = networkTargetUrl(resolvedServerUrl, endpointUrl)
                    logNetworkAttempt("TRY ${method.value} $requestUrl")
                    val response = httpClient.request(requestUrl) {
                        this.method = method
                        disableSessionAuthForPublicAuthRequest(endpointUrl)
                        this.headers.append(HttpHeaders.CacheControl, "no-cache")
                        this.headers.append(HttpHeaders.Pragma, "no-cache")

                        requestHeaders.forEach { (key, value) ->
                            safeHttpHeaderValueOrNull(value)?.let { safeValue ->
                                this.headers.append(key, safeValue)
                            }
                        }

                        if (expectedSessionGeneration != null) {
                            // Use this request's account token, not the bearer's latest global token.
                            pinSessionAuthorization(requireNotNull(tokensBeforeRequest).accessToken)
                        }

                        query.forEach { (key, value) ->
                            value?.let { parameter(key, it) }
                        }

                        body?.let { requestBody ->
                            contentType?.let { this.contentType(it) }
                            setBody(requestBody)
                        }
                    }

                    val rawBody = response.bodyAsText()
                    if (expectedSessionGeneration != null && !authenticatedSessionGenerationIsCurrent(expectedSessionGeneration))
                        return cloudSessionExpiredResponse()
                    val aitaServerResponse = response.isAitaServerResponse(rawBody)
                    logNetworkAttempt(
                        "RESULT ${method.value} $requestUrl HTTP ${response.status.value} aita=$aitaServerResponse"
                    )
                    val shouldRetryCandidate = index < serverUrlCandidates.lastIndex &&
                        shouldRetryNetworkRequestOnNextServerUrl(
                            method = method,
                            endpointUrl = endpointUrl,
                            status = response.status,
                            rawBody = rawBody,
                            aitaServerResponse = aitaServerResponse
                        )

                    if (!aitaServerResponse) {
                        val nonAitaResponse = nonAitaHttpResponseDataModel<Response>(
                            response.status,
                            rawBody,
                            resolvedServerUrl
                        )
                        lastTransportFailureMessage = nonAitaResponse.message
                        forgetReachableServerUrlCandidate(resolvedServerUrl)
                        markCloudTransportUnavailableForNotifications(reason = "non_aita_response")
                        if (shouldRetryCandidate) break@retrySameServer
                        return nonAitaResponse
                    }

                    val decodedResponse = decodeNetworkResponseDataModel<Response>(rawBody, response.status)
                        .withAitaTransportFailureFromStatus(response.status)
                    if (response.status.value == 402 && subscriptionStoreId != null)
                        invalidateStoreSubscriptionAccess(subscriptionStoreId, requestStartedAtMillis, requestAccountId, requestSessionGeneration)

                    if (response.status.isAitaServerUnhealthyForClientBanner() || decodedResponse.transportFailure) {
                        val failureResponse = decodedResponse.copy(transportFailure = true)
                        lastServerErrorResponse = failureResponse
                        forgetReachableServerUrlCandidate(resolvedServerUrl)
                        markCloudTransportUnavailableForNotifications(reason = "server_unhealthy_${response.status.value}")
                        logCloudConnectionDiagnostic(
                            "server response treated as unavailable method=${method.value} endpoint=${endpointUrl.trim('/')} " +
                                "http=${response.status.value} aita=$aitaServerResponse negative=${failureResponse.negative}"
                        )
                        if (shouldRetryCandidate) break@retrySameServer
                        return failureResponse
                    }

                    if (shouldRetryCandidate) {
                        lastServerErrorResponse = decodedResponse
                        forgetReachableServerUrlCandidate(resolvedServerUrl)
                        break@retrySameServer
                    }

                    val canMarkReachable = cloudResponseCanMarkReachable(endpointUrl, response.status)
                    if (canMarkReachable) {
                        rememberReachableServerUrl(resolvedServerUrl)
                        val authenticatedResponse = protectedEndpoint &&
                            response.status != HttpStatusCode.Unauthorized &&
                            response.status.value < 500 &&
                            getStoredUserAuthTokens?.invoke() != null
                        if (authenticatedResponse) {
                            }
                        markCloudTransportReachableForNotifications(
                            authenticated = authenticatedResponse,
                            // A public ping proves transport only. A protected 401 also proves transport;
                            // session-expired state is grounded only if the one refresh attempt is rejected.
                            authRefreshRequired = when {
                                authenticatedResponse -> false
                                else -> null
                            }
                        )
                    } else {
                        logCloudConnectionDiagnostic(
                            "reachable mark suppressed endpoint=$endpointUrl http=${response.status.value} " +
                                "status=${cloudTransportStatusName(cloudTransportStatusState.value)}"
                        )
                        if (response.status.value >= 500) {
                            markCloudTransportUnavailableForNotifications(reason = "suppressed_server_failure")
                        }
                    }

                    if (response.status == HttpStatusCode.Unauthorized) {
                        if (!protectedEndpoint) return decodedResponse

                        invalidateCloudAccessTokenValidation(tokensBeforeRequest?.accessToken)
                        val latestTokensAfterRequest = getStoredUserAuthTokens?.invoke()

                        // Ktor's bearer plugin may already have rotated the token while handling this
                        // response. Retry the original request once with that newly stored token.
                        if (!authRetryUsedForCandidate &&
                            tokensBeforeRequest != null &&
                            latestTokensAfterRequest != null &&
                            latestTokensAfterRequest.accessToken != tokensBeforeRequest.accessToken
                        ) {
                            httpClient.authProvider<BearerAuthProvider>()?.clearToken()
                            authRetryUsedForCandidate = true
                            continue@retrySameServer
                        }

                        if (latestTokensAfterRequest != null &&
                            rejectedAuthRefreshTokenMatches(latestTokensAfterRequest.refreshToken)
                        ) {
                            return cloudSessionExpiredResponse()
                        }

                        if (!authRetryUsedForCandidate &&
                            refreshStoredAuthTokensOnceForNetworkRetry(postNotification = false, expectedSessionGeneration = expectedSessionGeneration)
                        ) {
                            httpClient.authProvider<BearerAuthProvider>()?.clearToken()
                            authRetryUsedForCandidate = true
                            continue@retrySameServer
                        }

                        recentAuthRefreshNonAuthFailureMessage()?.let { refreshFailureMessage ->
                            return authRefreshFailureResponseForNetworkRequest(refreshFailureMessage)
                        }

                        val currentTokens = getStoredUserAuthTokens?.invoke()
                        if (currentTokens != null && rejectedAuthRefreshTokenMatches(currentTokens.refreshToken)) {
                            return cloudSessionExpiredResponse()
                        }

                        return cloudSessionExpiredResponse()
                    }

                    return decodedResponse
                } catch (throwable: Throwable) {
                    if (throwable is CancellationException) throw throwable
                    if (expectedSessionGeneration != null && !authenticatedSessionGenerationIsCurrent(expectedSessionGeneration))
                        return cloudSessionExpiredResponse()
                    val status = (throwable as? ResponseException)?.response?.status

                    if (status == HttpStatusCode.Unauthorized && protectedEndpoint) {
                        invalidateCloudAccessTokenValidation(tokensBeforeRequest?.accessToken)
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

                        if (latestTokensAfterException != null &&
                            rejectedAuthRefreshTokenMatches(latestTokensAfterException.refreshToken)
                        ) {
                            return cloudSessionExpiredResponse()
                        }

                        if (!authRetryUsedForCandidate &&
                            refreshStoredAuthTokensOnceForNetworkRetry(postNotification = false, expectedSessionGeneration = expectedSessionGeneration)
                        ) {
                            httpClient.authProvider<BearerAuthProvider>()?.clearToken()
                            authRetryUsedForCandidate = true
                            continue@retrySameServer
                        }

                        recentAuthRefreshNonAuthFailureMessage()?.let { refreshFailureMessage ->
                            return authRefreshFailureResponseForNetworkRequest(refreshFailureMessage)
                        }
                        return cloudSessionExpiredResponse()
                    }

                    forgetReachableServerUrlCandidate(resolvedServerUrl)
                    markCloudTransportUnavailableForNotifications(reason = "request_exception")
                    lastTransportFailureMessage = networkTransportFailureMessage(
                        resolvedServerUrl,
                        endpointUrl,
                        throwable
                    )
                    logNetworkAttempt(
                        "FAILED ${method.value} ${networkTargetUrl(resolvedServerUrl, endpointUrl)} " +
                            networkFailureSummary(throwable)
                    )
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
    if (!status.isSuccess() && (rawBody.isBlank() || (!rawBodyLooksLikeAitaServerResponse(rawBody) && (status.value >= 500 || !rawBodyLooksLikeJson(rawBody))))) {
        return genericHttpErrorNetworkResponseDataModel(status)
    }

    val lenientEnvelopeObject = runCatching { jsonBase.decodeFromString<kotlinx.serialization.json.JsonElement>(rawBody).jsonObject }.getOrNull()
    if (lenientEnvelopeObject != null &&
        lenientEnvelopeObject.containsKey("message") &&
        lenientEnvelopeObject.containsKey("negative")
    ) {
        val messageText = lenientEnvelopeObject["message"]?.let { element ->
            runCatching { element.jsonPrimitive.contentOrNull }.getOrNull()
                ?: element.toString().takeIf { it != "null" }
        }
        val message = messageText?.takeIf { it.isNotBlank() }?.let { text ->
            runCatching { jsonBase.decodeFromString<List<LocalizedStringDataModel>>(text) }.getOrNull()
        }
        val payloadText = lenientEnvelopeObject["payload"]?.let { element ->
            runCatching { element.jsonPrimitive.contentOrNull }.getOrNull()
                ?: element.toString().takeIf { it != "null" }
        }
        val payload = payloadText?.let { text ->
            runCatching { jsonBase.decodeFromString<Response>(text) }.getOrNull()
        }
        val payloadUnreadable = payloadText != null && payload == null
        val negative = lenientEnvelopeObject["negative"]
            ?.let { runCatching { it.jsonPrimitive.booleanOrNull }.getOrNull() }
            ?: !status.isSuccess()
        val envelopeTransportFailure = lenientEnvelopeObject["transportFailure"]
            ?.let { runCatching { it.jsonPrimitive.booleanOrNull }.getOrNull() }
            ?: false

        return ResponseDataModel(
            message = if (payloadUnreadable && message == null) unreadableNetworkResponseDataModel<Response>(status, rawBody).message else message,
            payload = payload,
            negative = negative || payloadUnreadable || !status.isSuccess(),
            httpStatusCode = status.value,
            transportFailure = envelopeTransportFailure || status.isAitaServerUnhealthyForClientBanner()
        )
    }

    val genericResponse = runCatching {
        jsonBase.decodeFromString<GenericResponseDataModel>(rawBody)
    }.getOrNull()

    if (genericResponse != null) {
        val message = runCatching { genericResponse.getMessage() }.getOrNull()
        val payload = genericResponse.payload?.let { payloadText ->
            runCatching { jsonBase.decodeFromString<Response>(payloadText) }.getOrNull()
        }
        val payloadUnreadable = genericResponse.payload != null && payload == null

        return ResponseDataModel(
            message = if (payloadUnreadable && message == null) unreadableNetworkResponseDataModel<Response>(status, rawBody).message else message,
            payload = payload,
            negative = genericResponse.negative || payloadUnreadable || !status.isSuccess(),
            httpStatusCode = status.value,
            transportFailure = status.isAitaServerUnhealthyForClientBanner()
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
            main = "Server returned an internal request error. The server is reachable; try again after the failed action is fixed.",
            ru = "Сервер вернул внутреннюю ошибку запроса. Сервер доступен; попробуйте снова после исправления действия.",
            kk = "Сервер сұраудың ішкі қатесін қайтарды. Сервер қолжетімді; әрекет түзетілген соң қайталап көріңіз."
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
        transportFailure = status.isAitaServerUnhealthyForClientBanner()
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
    if (status.value >= 500) {
        return genericHttpErrorNetworkResponseDataModel(status)
    }

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
        transportFailure = status.isAitaServerUnhealthyForClientBanner()
    )
}

suspend fun suggestStoreAddresses(
    query: String,
    language: String,
    countryCodes: List<String> = emptyList(),
    userLatitude: Double? = null,
    userLongitude: Double? = null,
    limit: Int = 8
): ResponseDataModel<List<AddressSuggestionDataModel>> = networkRequest(
    method = HttpMethod.Post,
    endpointUrl = "geo/address/suggest",
    body = AddressSuggestRequestDataModel(
        query = query.trim(),
        language = normalizeAppLanguagePreference(language).takeUnless { it == "system" } ?: DEFAULT_APP_LANGUAGE,
        countryCodes = countryCodes.map { it.trim().uppercase() }.filter { it.length == 2 }.distinct(),
        userLatitude = userLatitude?.takeIf { it.isFinite() && it in -90.0..90.0 },
        userLongitude = userLongitude?.takeIf { it.isFinite() && it in -180.0..180.0 },
        limit = limit.coerceIn(1, 10)
    )
)

suspend fun resolveStoreAddressSuggestion(
    suggestion: AddressSuggestionDataModel,
    language: String
): ResponseDataModel<LocationDataModel> = networkRequest(
    method = HttpMethod.Post,
    endpointUrl = "geo/address/resolve",
    body = AddressResolveRequestDataModel(
        provider = suggestion.provider,
        providerObjectId = suggestion.providerObjectId,
        query = suggestion.formattedAddress.ifBlank { listOf(suggestion.title, suggestion.subtitle).filter { it.isNotBlank() }.joinToString(", ") },
        language = normalizeAppLanguagePreference(language).takeUnless { it == "system" } ?: DEFAULT_APP_LANGUAGE,
        countryCode = suggestion.countryCode
    )
)

suspend fun requestStoreAddressMapPreview(
    location: LocationDataModel,
    language: String,
    darkTheme: Boolean,
    width: Int = 650,
    height: Int = 360,
    zoom: Int = 16
): ResponseDataModel<AddressMapPreviewDataModel> = networkRequest(
    method = HttpMethod.Post,
    endpointUrl = "geo/address/mapPreview",
    body = AddressMapPreviewRequestDataModel(
        location = location,
        language = normalizeAppLanguagePreference(language).takeUnless { it == "system" } ?: DEFAULT_APP_LANGUAGE,
        darkTheme = darkTheme,
        width = width.coerceIn(320, 650),
        height = height.coerceIn(180, 450),
        zoom = zoom.coerceIn(10, 18)
    )
)

internal fun List<StoreDataModel>.withoutStoreOrBranch(storeId: String): List<StoreDataModel> {
    val cleanId = storeId.trim()
    if (cleanId.isBlank()) return this
    return mapNotNull { store ->
        if (store.id == cleanId) {
            null
        } else {
            store.copy(branches = store.branches.withoutStoreOrBranch(cleanId))
        }
    }
}

internal fun List<StoreDataModel>.upsertStoreOrBranch(saved: StoreDataModel): List<StoreDataModel> {
    val cleanId = saved.id.trim()
    if (cleanId.isBlank()) return this

    val existing = findStoreOrBranch(cleanId)
    val preservedChildren = if (saved.branches.isEmpty()) existing?.branches.orEmpty() else saved.branches
    val authoritative = saved.copy(branches = preservedChildren)
    val withoutOldCopy = withoutStoreOrBranch(cleanId)
    val parentId = authoritative.parentStoreId?.trim().orEmpty()

    if (parentId.isBlank()) return withoutOldCopy + authoritative

    var inserted = false
    fun insertIntoParent(store: StoreDataModel): StoreDataModel {
        if (store.id == parentId) {
            inserted = true
            return store.copy(branches = store.branches + authoritative)
        }
        return store.copy(branches = store.branches.map(::insertIntoParent))
    }

    val reconciled = withoutOldCopy.map(::insertIntoParent)
    // If the parent is not in the current partial cache, do not misrepresent the branch as a
    // top-level Store. The authoritative getStores() refresh will place it correctly.
    return if (inserted) reconciled else withoutOldCopy
}

private fun List<StoreDataModel>.mergeRefreshedStores(
    refreshedStores: List<StoreDataModel>
): List<StoreDataModel> {
    if (refreshedStores.isEmpty()) return this
    val refreshedById = refreshedStores.associateBy { it.id }

    fun merge(store: StoreDataModel): StoreDataModel {
        val refreshed = refreshedById[store.id]
        val base = refreshed ?: store
        return base.copy(branches = store.branches.map(::merge))
    }

    return map(::merge)
}

fun refreshStoreAddressLocalizations(
    force: Boolean = false,
    storeIds: List<String> = storesState.payloadValue.orEmpty().flattenStoresWithBranches().map { it.id }
) {
    val cleanIds = storeIds.map(String::trim).filter(String::isNotBlank).distinct().take(40)
    if (cleanIds.isEmpty() || refreshStoreAddressesMutex.isLocked) return

    val now = getCurrentTimeMillis()
    if (!force && now - lastStoreAddressRefreshRequestMillis < AITA_ADDRESS_CLIENT_REFRESH_COOLDOWN_MILLIS) return

    GlobalScope.launch(Dispatchers.ourIo) {
        refreshStoreAddressesMutex.withLock {
            val requestStartedAt = getCurrentTimeMillis()
            if (!force && requestStartedAt - lastStoreAddressRefreshRequestMillis < AITA_ADDRESS_CLIENT_REFRESH_COOLDOWN_MILLIS) {
                return@withLock
            }
            lastStoreAddressRefreshRequestMillis = requestStartedAt

            val response = networkRequest<StoreAddressRefreshResultDataModel, StoreAddressRefreshRequestDataModel>(
                method = HttpMethod.Post,
                endpointUrl = "geo/address/refreshStores",
                body = StoreAddressRefreshRequestDataModel(storeIds = cleanIds, force = force)
            )

            val refreshed = response.payload?.stores.orEmpty()
            if (!response.negative && refreshed.isNotEmpty()) {
                val current = storesState.payloadValue.orEmpty()
                storesState.emit(DataState.Success(current.mergeRefreshedStores(refreshed), response.message))
            }
        }
    }
}

fun getStores() {
    val sessionGeneration = currentAuthenticatedSessionGeneration()
    storesRefreshPendingState.value = true
    if (!getStoresMutex.tryLock()) return

    GlobalScope.launch(Dispatchers.ourIo) {
        try {
            do {
                if (!authenticatedSessionGenerationIsCurrent(sessionGeneration)) return@launch
                storesRefreshPendingState.value = false
                val selectionRevision = ActiveStores.revision
                val response = networkRequest<List<StoreDataModel>, Unit>(
                    HttpMethod.Get,
                    endpointUrl = globalAppConfigurationState.payloadValue.getStoresPath.first,
                    expectedSessionGeneration = sessionGeneration
                )
                if (!authenticatedSessionGenerationIsCurrent(sessionGeneration)) return@launch

                if (!response.negative && response.payload != null) {
                    val stores = response.payload
                    storesState.emit(DataState.Success(stores, response.message))
                    refreshStoreAddressLocalizations(
                        storeIds = stores.flattenStoresWithBranches()
                            .filter { it.location.isResolvedAddress() }
                            .map { it.id }
                    )

                    if (ActiveStores.revision != selectionRevision || !ActiveStores.hydrated) continue
                    val activeStore = stores.findStoreOrBranch(activeStoreIdState.value)
                    when {
                        activeStoreIdState.value != null && activeStore == null ->
                            setActiveStoreId(null)

                        activeStoreIdState.value == null -> {
                            val settableStores = stores.settableActiveStores()
                            if (!activeStoreExplicitNoneIsSet() && settableStores.size == 1) {
                                setActiveStoreId(settableStores.first().id)
                            }
                        }
                    }
                }
            } while (storesRefreshPendingState.value)
        } finally {
            getStoresMutex.unlock()
            if (storesRefreshPendingState.value && getStoredUserAuthTokens?.invoke() != null) getStores()
        }
    }
}

fun addStore(store: StoreDataModel, onCompleted: ((DataState<StoreDataModel>) -> Unit)?) {
    val requestGeneration = currentAuthenticatedSessionGeneration()
    val requestOwner = userAccountState.payloadValue?.id ?: return
    fun requestStillCurrent(): Boolean = authenticatedSessionGenerationIsCurrent(requestGeneration) &&
        userAccountState.payloadValue?.id == requestOwner
    GlobalScope.launch(Dispatchers.ourIo) {
        addStoreMutex.withLock {
            if (!requestStillCurrent()) return@withLock
            val response = networkRequest<StoreDataModel, StoreDataModel>(
                HttpMethod.Post,
                endpointUrl = globalAppConfigurationState.payloadValue.addStoresPath.first,
                body = store,
                expectedSessionGeneration = requestGeneration
            )

            if (!requestStillCurrent()) return@withLock
            if (response.negative || response.payload == null) {
                postInAppNotification(response.message, NotificationType.Negative)

                onCompleted?.invoke(DataState.Empty())
            } else {
                postInAppNotification(response.message, NotificationType.Positive)

                storesState.emit(
                    DataState.Success(
                        storesState.payloadValue.orEmpty().upsertStoreOrBranch(response.payload!!.withoutContactVerification())
                    )
                )

                getStores()

                onCompleted?.invoke(DataState.Success(response.payload!!.withoutContactVerification()))
            }
        }
    }
}

fun updateStore(store: StoreDataModel, onCompleted: ((DataState<StoreDataModel>) -> Unit)?) {
    val requestGeneration = currentAuthenticatedSessionGeneration()
    val requestOwner = userAccountState.payloadValue?.id ?: return
    fun requestStillCurrent(): Boolean = authenticatedSessionGenerationIsCurrent(requestGeneration) &&
        userAccountState.payloadValue?.id == requestOwner
    GlobalScope.launch(Dispatchers.ourIo) {
        updateStoreMutex.withLock {
            if (!requestStillCurrent()) return@withLock
            val response = networkRequest<StoreDataModel, StoreDataModel>(
                HttpMethod.Put,
                endpointUrl = globalAppConfigurationState.payloadValue.updateStoresPath.first,
                headers = mapOf("store_id" to store.id),
                body = store,
                expectedSessionGeneration = requestGeneration
            )

            if (!requestStillCurrent()) return@withLock
            if (response.negative || response.payload == null) {
                postInAppNotification(response.message, NotificationType.Negative)

                onCompleted?.invoke(DataState.Empty())
            } else {
                postInAppNotification(response.message, NotificationType.Positive)

                storesState.emit(
                    DataState.Success(
                        storesState.payloadValue.orEmpty().upsertStoreOrBranch(response.payload!!.withoutContactVerification())
                    )
                )
                getStores()

                onCompleted?.invoke(DataState.Success(response.payload!!.withoutContactVerification()))
            }
        }
    }
}

fun deleteStore(store: StoreDataModel, onCompleted: ((DataState<Unit>) -> Unit)? = null) {
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

                val removedIds = listOf(store).flattenStoresWithBranches().map { it.id }.toSet()

                storesState.emit(
                    DataState.Success(
                        storesState.payloadValue.orEmpty().withoutStoreOrBranch(store.id)
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

fun setActiveStoreId(id: String?, syncServer: Boolean = true) {
    ActiveStores.select(id, syncServer)
}

val suppliersState = MutableDataStateFlow<List<SupplierDataModel>>(GlobalScope)

private val supplierProfilesReadCoordinator =
    SingleFlightRequestCoordinator<String, DataState<List<SupplierDataModel>>>()
private val supplierProfileOperationMutex = Mutex()

fun getSuppliers() {
    val requestUserScope = currentSupplierNetworkUserScope()
    if (requestUserScope.isBlank()) return

    GlobalScope.launch(Dispatchers.ourIo) {
        supplierProfilesReadCoordinator.run(requestUserScope) {
            supplierProfileOperationMutex.withLock {
                if (!supplierNetworkUserScopeIsCurrent(requestUserScope)) return@withLock DataState.Empty()
                val response = networkRequest<List<SupplierDataModel>, Unit>(
                    HttpMethod.Get,
                    endpointUrl = globalAppConfigurationState.payloadValue.getSuppliersPath.first
                )

                if (!supplierNetworkUserScopeIsCurrent(requestUserScope)) {
                    DataState.Empty()
                } else if (response.negative || response.payload == null) {
                    DataState.Empty(response.message)
                } else {
                    DataState.Success(response.payload, response.message).also {
                        suppliersState.emit(it)
                        // Keep the operation lock until the exact authoritative payload is visible.
                        // A queued mutation can then build on this state instead of racing an older GET.
                        suppliersState.payload.first { current -> current == response.payload }
                    }
                }
            }
        }
    }
}


fun addSupplier(
    supplier: SupplierDataModel,
    onCompleted: ((DataState<SupplierDataModel>) -> Unit)? = null
) {
    val requestUserScope = currentSupplierNetworkUserScope()
    if (requestUserScope.isBlank()) return

    GlobalScope.launch(Dispatchers.ourIo) {
        supplierProfileOperationMutex.withLock {
            if (!supplierNetworkUserScopeIsCurrent(requestUserScope)) return@withLock
            val response = networkRequest<SupplierDataModel, SupplierDataModel>(
                method = HttpMethod.Post,
                endpointUrl = globalAppConfigurationState.payloadValue.addSupplierPath.first,
                body = supplier
            )
            if (!supplierNetworkUserScopeIsCurrent(requestUserScope)) return@withLock

            if (response.negative || response.payload == null) {
                postInAppNotification(response.message, NotificationType.Negative)
                onCompleted?.invoke(DataState.Empty(response.message))
            } else {
                val savedSupplier = response.payload!!.withoutContactVerification()
                suppliersState.emit(DataState.Success(suppliersState.payloadValue.orEmpty().upsertById(savedSupplier), response.message))
                // Profile mutations already publish the authoritative profile locally. A first-profile
                // focus change is reconciled by SupplierIdentityFocus and owns the one scoped workspace
                // refresh; editing another identity does not need to reload orders/prices/contracts.
                onCompleted?.invoke(DataState.Success(savedSupplier, response.message))
            }
        }
    }
}

fun updateSupplier(
    supplier: SupplierDataModel,
    onCompleted: ((DataState<SupplierDataModel>) -> Unit)? = null
) {
    val requestUserScope = currentSupplierNetworkUserScope()
    if (requestUserScope.isBlank()) return

    GlobalScope.launch(Dispatchers.ourIo) {
        supplierProfileOperationMutex.withLock {
            if (!supplierNetworkUserScopeIsCurrent(requestUserScope)) return@withLock
            val response = networkRequest<SupplierDataModel, SupplierDataModel>(
                method = HttpMethod.Put,
                endpointUrl = globalAppConfigurationState.payloadValue.updateSupplierPath.first,
                body = supplier
            )
            if (!supplierNetworkUserScopeIsCurrent(requestUserScope)) return@withLock

            if (response.negative || response.payload == null) {
                postInAppNotification(response.message, NotificationType.Negative)
                onCompleted?.invoke(DataState.Empty(response.message))
            } else {
                val savedSupplier = response.payload!!.withoutContactVerification()
                suppliersState.emit(DataState.Success(suppliersState.payloadValue.orEmpty().upsertById(savedSupplier), response.message))
                // Profile mutations already publish the authoritative profile locally. A first-profile
                // focus change is reconciled by SupplierIdentityFocus and owns the one scoped workspace
                // refresh; editing another identity does not need to reload orders/prices/contracts.
                onCompleted?.invoke(DataState.Success(savedSupplier, response.message))
            }
        }
    }
}

fun deleteSupplier(
    supplierId: String,
    onCompleted: ((DataState<String>) -> Unit)? = null
) {
    val requestUserScope = currentSupplierNetworkUserScope()
    if (requestUserScope.isBlank()) return

    GlobalScope.launch(Dispatchers.ourIo) {
        supplierProfileOperationMutex.withLock {
            if (!supplierNetworkUserScopeIsCurrent(requestUserScope)) return@withLock
            val response = networkRequest<String, String>(
                method = HttpMethod.Delete,
                endpointUrl = globalAppConfigurationState.payloadValue.deleteSupplierPath.first,
                body = supplierId
            )
            if (!supplierNetworkUserScopeIsCurrent(requestUserScope)) return@withLock

            if (response.negative) {
                postInAppNotification(response.message, NotificationType.Negative)
                onCompleted?.invoke(DataState.Empty(response.message))
            } else {
                suppliersState.emit(
                    DataState.Success(
                        suppliersState.payloadValue.orEmpty().filterNot { it.id.equals(supplierId, ignoreCase = true) },
                        response.message
                    )
                )
                // Deletion is restricted server-side to an unused identity. If the deleted profile was
                // focused, SupplierIdentityFocus reconciles the selection and performs one scoped refresh.
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

fun getGenericGoodsCategories() {
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

/** A read has one account/store owner and a bounded lifetime; repeat taps do not queue more reads. */
private inline fun <reified T> readInventoryResource(
    storeId: String,
    name: String,
    endpoint: String,
    state: MutableDataStateFlow<List<T>>,
    status: MutableStateFlow<InventoryLoadStatus>,
    read: OwnedScopedRead<InventoryOwner>,
    crossinline filter: suspend (List<T>) -> List<T>
) {
    val owner = inventoryOwners.current
    if (owner.storeId != storeId.trim() || !inventoryOwnerIsCurrent(owner)) return
    read.start(owner, GlobalScope, Dispatchers.ourIo) {
        val requestJob = currentCoroutineContext()[Job]
        try {
            hydrateInventoryResource(name, owner, state, status) { filter(it) }
            val atStart = inventoryStateMutex.withLock {
                if (!inventoryOwnerIsCurrent(owner) || !read.owns(owner, requestJob)) return@start
                status.value = status.value.copy(loading = true, failure = null)
                inventoryAccessRevision to state.payloadValue
            }
            val response = withTimeoutOrNull(45_000L) {
                networkRequest<List<T>, Unit>(
                    HttpMethod.Get,
                    endpointUrl = endpoint,
                    headers = mapOf("store_id" to requireNotNull(owner.storeId)),
                    expectedSessionGeneration = owner.sessionGeneration
                )
            }
            val cleanPayload = response?.payload?.let { filter(it) }
            inventoryStateMutex.withLock {
                if (!inventoryOwnerIsCurrent(owner) || !read.owns(owner, requestJob) || inventoryAccessRevision != atStart.first) return@withLock
                when {
                    response != null && inventoryReadRevokesAccess(response.httpStatusCode, response.transportFailure) -> {
                        denyCachedInventoryLocked(owner, response.message?.takeIf { it.isNotEmpty() } ?: inventoryLoadFailureMessage())
                    }
                    response != null && !response.negative && cleanPayload != null -> {
                        // An intervening local mutation wins over the snapshot requested BEFORE it.
                        if (state.payloadValue !== atStart.second) return@withLock
                        val saved = persistInventoryCacheLocked(name, owner, cleanPayload, cloudVerified = true)
                        if (!inventoryOwnerIsCurrent(owner) || state.payloadValue !== atStart.second) return@withLock
                        state.emit(DataState.Success(cleanPayload, response.message))
                        status.value = InventoryLoadStatus(owner.storeId, source = InventoryLoadSource.Cloud, cacheWriteFailed = !saved, cacheChecked = true)
                    }
                    else -> {
                        // 401, timeout and transport failures never erase same-owner data or become a false empty success.
                        status.value = status.value.copy(
                            loading = false,
                            failure = response?.message?.takeIf { it.isNotEmpty() } ?: inventoryLoadFailureMessage()
                        )
                    }
                }
            }
        } catch (failure: Exception) {
            ensureConnectionOwnerActive(failure)
            inventoryStateMutex.withLock {
                if (inventoryOwnerIsCurrent(owner) && read.owns(owner, requestJob)) status.value = status.value.copy(loading = false, failure = inventoryLoadFailureMessage())
            }
        } finally {
            withContext(NonCancellable) {
                inventoryStateMutex.withLock {
                    // Tokens can disappear before the next owner is published. Never strand this read's spinner.
                    if (inventoryOwners.owns(owner) && read.owns(owner, requestJob)) status.value = status.value.copy(loading = false)
                }
            }
        }
    }
}

fun getStock(storeId: String) = readInventoryResource(
    storeId, "stock", globalAppConfigurationState.payloadValue.getStockPath.first,
    stockState, stockLoadStatusState, stockRead, ::filterRecentlyDeletedStockItems
)

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
                DataState.Success(filterRecentlyDeletedStockItems(response.payload.orEmpty()), response.message)
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

private fun stockItemSaveFailureMessage(): List<LocalizedStringDataModel> = eventMessage("message.could_not_save_stock_item_please_try_again")

private suspend fun applySavedGoodsItemToStockState(
    savedGoodsItem: GoodsItemDataModel,
    responseMessage: List<LocalizedStringDataModel>?,
    owner: InventoryOwner
) {
    inventoryStateMutex.withLock {
        if (!inventoryOwnerIsCurrent(owner)) return
        val current = stockState.payloadValue.orEmpty().toMutableList()
        val existingIndex = current.indexOfFirst { it.id == savedGoodsItem.id }
        if (existingIndex >= 0) current[existingIndex] = savedGoodsItem
        else current.add(savedGoodsItem)
        stockState.emit(DataState.Success(current, responseMessage))
    }
    owner.storeId?.let { storeId ->
        getStock(storeId)
        refreshParentStoreStock(storeId, limit = 32, appendToSharedState = false)
    }
}

fun updateGoodsItem(
    goodsItem: GoodsItemDataModel,
    onCompleted: ((DataState<GoodsItemDataModel>) -> Unit)?
) {
    val owner = inventoryOwners.current
    GlobalScope.launch(Dispatchers.ourIo) {
        val completion: DataState<GoodsItemDataModel> = updateGoodsItemMutex.withLock {
            try {
                val response = networkRequest<GoodsItemDataModel, GoodsItemDataModel>(
                    HttpMethod.Put,
                    endpointUrl = globalAppConfigurationState.payloadValue.updateGoodsItemPath.first,
                    body = goodsItem
                )
                val savedGoodsItem = response.payload

                if (response.negative || savedGoodsItem == null) {
                    val message = response.message ?: stockItemSaveFailureMessage()
                    postInAppNotification(message, NotificationType.Negative)
                    DataState.Empty(message)
                } else {
                    postInAppNotification(response.message, NotificationType.Positive)
                    applySavedGoodsItemToStockState(
                        savedGoodsItem = savedGoodsItem,
                        responseMessage = response.message,
                        owner = owner
                    )
                    DataState.Success(savedGoodsItem, response.message)
                }
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                val message = stockItemSaveFailureMessage()
                logNetworkAttempt("FAILED stock item update ${networkFailureSummary(throwable)}")
                postInAppNotification(message, NotificationType.Negative)
                DataState.Empty(message)
            }
        }

        onCompleted?.invoke(completion)
    }
}

fun addGoodsItem(
    goodsItem: GoodsItemDataModel,
    onCompleted: ((DataState<GoodsItemDataModel>) -> Unit)?
) {
    val owner = inventoryOwners.current
    GlobalScope.launch(Dispatchers.ourIo) {
        val completion: DataState<GoodsItemDataModel> = addGoodsItemMutex.withLock {
            try {
                val response = networkRequest<GoodsItemDataModel, GoodsItemDataModel>(
                    HttpMethod.Post,
                    endpointUrl = globalAppConfigurationState.payloadValue.addGoodsItemPath.first,
                    body = goodsItem
                )
                val savedGoodsItem = response.payload

                if (response.negative || savedGoodsItem == null) {
                    val message = response.message ?: stockItemSaveFailureMessage()
                    postInAppNotification(message, NotificationType.Negative)
                    DataState.Empty(message)
                } else {
                    postInAppNotification(response.message, NotificationType.Positive)
                    applySavedGoodsItemToStockState(
                        savedGoodsItem = savedGoodsItem,
                        responseMessage = response.message,
                        owner = owner
                    )
                    DataState.Success(savedGoodsItem, response.message)
                }
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                val message = stockItemSaveFailureMessage()
                logNetworkAttempt("FAILED stock item add ${networkFailureSummary(throwable)}")
                postInAppNotification(message, NotificationType.Negative)
                DataState.Empty(message)
            }
        }

        onCompleted?.invoke(completion)
    }
}

fun deleteGoodsItem(id: String, storeId: String, onCompleted: (() -> Unit)?) {
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
                val deletedId = response.payload?.takeIf { it.isNotBlank() } ?: id
                rememberRecentlyDeletedStockItemId(deletedId)
                postInAppNotification(response.message, NotificationType.Positive, transient = true)

                stockState.payloadValue?.run {
                    stockState.emit(DataState.Success(filter { it.id != deletedId }))
                }
                parentStoreStockState.payloadValue?.run {
                    parentStoreStockState.emit(DataState.Success(filter { it.id != deletedId }))
                }
                stockBatchesState.payloadValue?.run {
                    stockBatchesState.emit(DataState.Success(filter { it.goodsItemId != deletedId }))
                }

                deleteCartItemById(deletedId)

                onCompleted?.invoke()
            }
        }
    }
}

fun getStockBatches(storeId: String) = readInventoryResource(
    storeId, "stock_batches", globalAppConfigurationState.payloadValue.getStockBatchesPath.first,
    stockBatchesState, stockBatchesLoadStatusState, stockBatchesRead, ::filterRecentlyDeletedStockBatches
)

private val branchAvailabilityReadRevision = MutableStateFlow(0L)

private fun stockMovementContextChangedMessage() = eventMessage("message.store_or_account_changed_open_the_batch_again")

private fun stockMovementBusyMessage() = eventMessage("message.a_batch_operation_is_already_in_progress_please_wait")

private fun stockMovementUnconfirmedMessage() = eventMessage("message.the_batch_result_is_not_confirmed_refresh_branch_stock_before_trying")

fun getStockItemBranchAvailability(
    storeId: String,
    goodsItemId: String,
    onCompleted: ((DataState<StockItemBranchAvailabilityDataModel>) -> Unit)? = null
) {
    val owner = inventoryOwners.current
    val revision = branchAvailabilityReadRevision.updateAndGet { it + 1L }
    fun isCurrent() = owner.storeId == storeId && inventoryOwnerIsCurrent(owner) &&
        branchAvailabilityReadRevision.value == revision
    GlobalScope.launch(Dispatchers.ourIo) {
        var completed: DataState<StockItemBranchAvailabilityDataModel> = DataState.Empty()
        try {
            getStockItemBranchAvailabilityMutex.withLock {
                if (!isCurrent()) return@withLock
                val response = networkRequest<StockItemBranchAvailabilityDataModel, Unit>(
                    method = HttpMethod.Get,
                    endpointUrl = globalAppConfigurationState.payloadValue.getStockItemBranchAvailabilityPath.first,
                    headers = mapOf("store_id" to storeId, "goods_item_id" to goodsItemId),
                    expectedSessionGeneration = owner.sessionGeneration
                )
                inventoryStateMutex.withLock publish@ {
                    if (!isCurrent()) return@publish
                    val payload = response.payload
                    completed = if (response.negative || payload == null) DataState.Empty(response.message)
                    else DataState.Success(payload, response.message)
                    // Never show a previous item's branch balances after a failed replacement read.
                    stockItemBranchAvailabilityState.emit(completed)
                }
            }
        } catch (failure: Throwable) {
            ensureConnectionOwnerActive(failure)
            inventoryStateMutex.withLock {
                if (isCurrent()) {
                    completed = DataState.Empty(inventoryLoadFailureMessage())
                    stockItemBranchAvailabilityState.emit(completed)
                }
            }
        } finally {
            onCompleted?.invoke(if (isCurrent()) completed else DataState.Empty())
        }
    }
}

/** Claim before launch: a second press must not queue another irreversible stock movement.
 * Actor, account and store epoch are immutable for the entire request, including the wait for auth.
 */
private inline fun <reified T : Any> submitStockBatchMutation(
    owner: InventoryOwner,
    endpoint: String,
    body: T,
    noinline onCompleted: ((DataState<StockBatchMoveResultDataModel>) -> Unit)?
) {
    if (!inventoryOwnerIsCurrent(owner)) {
        onCompleted?.invoke(DataState.Empty(stockMovementContextChangedMessage()))
        return
    }
    if (!moveStockBatchMutex.tryLock()) {
        onCompleted?.invoke(DataState.Empty(stockMovementBusyMessage()))
        return
    }
    branchAvailabilityReadRevision.update { it + 1L }
    GlobalScope.launch(Dispatchers.ourIo) {
        var completed: DataState<StockBatchMoveResultDataModel> = DataState.Empty(stockMovementContextChangedMessage())
        try {
            if (!inventoryOwnerIsCurrent(owner)) return@launch
            val response = networkRequest<StockBatchMoveResultDataModel, T>(
                method = HttpMethod.Post,
                endpointUrl = endpoint,
                body = body,
                headers = mapOf("store_id" to owner.storeId.orEmpty()),
                expectedSessionGeneration = owner.sessionGeneration
            )
            inventoryStateMutex.withLock publish@ {
                if (!inventoryOwnerIsCurrent(owner)) return@publish
                // Reads issued before this mutation result must not restore old branch balances.
                branchAvailabilityReadRevision.update { it + 1L }
                val payload = response.payload
                if (response.negative || payload == null) {
                    val message = if (response.transportFailure || (response.httpStatusCode ?: 0) >= 500)
                        stockMovementUnconfirmedMessage() else response.message
                    completed = DataState.Empty(message)
                    postInAppNotification(message, NotificationType.Negative)
                } else {
                    completed = DataState.Success(payload, response.message)
                    stockBatchMoveResultState.emit(completed)
                    val visible = stockItemBranchAvailabilityState.payloadValue
                    if (visible != null && visible.currentStoreId == owner.storeId &&
                        (visible.sourceGoodsItemId == payload.availability.sourceGoodsItemId ||
                            payload.availability.locations.any { it.goodsItemId == visible.sourceGoodsItemId })) {
                        stockItemBranchAvailabilityState.emit(DataState.Success(payload.availability, response.message))
                    }
                    postInAppNotification(response.message, NotificationType.Positive)
                }
            }
            if (completed is DataState.Success && inventoryOwnerIsCurrent(owner)) {
                getStock(owner.storeId.orEmpty())
                getStockBatches(owner.storeId.orEmpty())
            }
        } catch (failure: Throwable) {
            ensureConnectionOwnerActive(failure)
            if (inventoryOwnerIsCurrent(owner) && completed !is DataState.Success) {
                val message = stockMovementUnconfirmedMessage()
                completed = DataState.Empty(message)
                postInAppNotification(message, NotificationType.Negative)
            }
        } finally {
            moveStockBatchMutex.unlock()
            onCompleted?.invoke(if (inventoryOwnerIsCurrent(owner)) completed else DataState.Empty(stockMovementContextChangedMessage()))
        }
    }
}

fun moveStockBatchBetweenStores(
    request: StockBatchMoveRequestDataModel,
    onCompleted: ((DataState<StockBatchMoveResultDataModel>) -> Unit)? = null
) {
    val owner = inventoryOwners.current
    if (request.actorStoreId != null && request.actorStoreId != owner.storeId) {
        onCompleted?.invoke(DataState.Empty(stockMovementContextChangedMessage()))
        return
    }
    submitStockBatchMutation(owner, globalAppConfigurationState.payloadValue.moveStockBatchPath.first,
        request.copy(actorStoreId = owner.storeId), onCompleted)
}

fun decideStockBatchMove(
    request: StockBatchMoveDecisionRequestDataModel,
    onCompleted: ((DataState<StockBatchMoveResultDataModel>) -> Unit)? = null
) {
    val owner = inventoryOwners.current
    submitStockBatchMutation(owner, globalAppConfigurationState.payloadValue.decideStockBatchMovePath.first,
        request, onCompleted)
}

fun updateGoodsBatches(
    goodsBatches: List<GoodsBatchDataModel>,
    onCompleted: ((DataState<List<GoodsBatchDataModel>>) -> Unit)?
) {
    GlobalScope.launch(Dispatchers.ourIo) {
        updateGoodsBatchMutex.withLock {
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
    GlobalScope.launch(Dispatchers.ourIo) {
        addGoodsBatchMutex.withLock {
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
    GlobalScope.launch(Dispatchers.ourIo) {
        deleteGoodsBatchMutex.withLock {
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
const val STORE_PERMISSION_STOCK_HISTORY_VIEW = "stock_history_view"
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
    STORE_PERMISSION_STOCK_HISTORY_VIEW,
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
        STORE_PERMISSION_STOCK_HISTORY_VIEW,
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
    STORE_PERMISSION_STOCK_HISTORY_VIEW,
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

fun setActiveShelfBatch(
    batch: GoodsBatchDataModel,
    storeId: String,
    onCompleted: ((DataState<GoodsItemDataModel>) -> Unit)? = null,
    previousActiveShelfBatchId: String? = null
) {
    val owner = inventoryOwners.current
    if (owner.storeId != storeId || batch.storeId != storeId || !inventoryOwnerIsCurrent(owner)) return
    GlobalScope.launch(Dispatchers.ourIo) {
        shelfOrderSaveMutex.withLock {
            if (!inventoryOwnerIsCurrent(owner)) return@withLock
            val visible = stockState.payloadValue?.firstOrNull { it.id == batch.goodsItemId }
            val previous = visible?.activeShelfBatchId ?: previousActiveShelfBatchId
            if (visible != null && previous == batch.id) {
                withContext(Dispatchers.Main) { if (inventoryOwnerIsCurrent(owner)) onCompleted?.invoke(DataState.Success(visible)) }
                return@withLock
            }
            val response = networkRequest<GoodsItemDataModel, GoodsBatchDataModel>(
                method = HttpMethod.Post, endpointUrl = "stockBatches/setActiveShelfBatch", body = batch,
                headers = mapOf("store_id" to storeId), expectedSessionGeneration = owner.sessionGeneration
            )
            if (!inventoryOwnerIsCurrent(owner)) return@withLock
            val item = response.payload
            if (response.negative || item == null || item.id != batch.goodsItemId || item.storeId != storeId || item.activeShelfBatchId != batch.id) {
                postInAppNotification(response.message, NotificationType.Negative)
                withContext(Dispatchers.Main) { if (inventoryOwnerIsCurrent(owner)) onCompleted?.invoke(DataState.Empty(response.message)) }
            } else {
                val published = inventoryStateMutex.withLock publish@ {
                    if (!inventoryOwnerIsCurrent(owner)) return@publish false
                    stockState.emit(DataState.Success(stockState.payloadValue.orEmpty().map {
                        if (it.id == item.id && it.updatedAtMillis <= item.updatedAtMillis) item else it
                    }, response.message))
                    true
                }
                if (published && inventoryOwnerIsCurrent(owner)) {
                    val isGenuineShelfChange = response.message.orEmpty().isNotEmpty() && previous != item.activeShelfBatchId
                    if (isGenuineShelfChange) postInAppNotification(response.message, NotificationType.Positive)
                    withContext(Dispatchers.Main) { if (inventoryOwnerIsCurrent(owner)) onCompleted?.invoke(DataState.Success(item, response.message)) }
                }
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
            LocalizedStringDataModel("kk", "БИН"),
            LocalizedStringDataModel("ky", "БИН")
        ),
        label = listOf(
            LocalizedStringDataModel("main", "Business Identification Number"),
            LocalizedStringDataModel("en", "Business Identification Number"),
            LocalizedStringDataModel("ru", "Бизнес-идентификационный номер"),
            LocalizedStringDataModel("kk", "Бизнес сәйкестендіру нөмірі"),
            LocalizedStringDataModel("ky", "Бизнес идентификациялык номери")
        ),
        placeholder = listOf(
            LocalizedStringDataModel("main", "12 digits"),
            LocalizedStringDataModel("en", "12 digits"),
            LocalizedStringDataModel("ru", "12 цифр"),
            LocalizedStringDataModel("kk", "12 сан"),
            LocalizedStringDataModel("ky", "12 цифра")
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
            LocalizedStringDataModel("kk", "СТН / РМА"),
            LocalizedStringDataModel("ky", "ИНН")
        ),
        label = listOf(
            LocalizedStringDataModel("main", "Taxpayer Identification Number"),
            LocalizedStringDataModel("en", "Taxpayer Identification Number"),
            LocalizedStringDataModel("ru", "Идентификационный номер налогоплательщика"),
            LocalizedStringDataModel("kk", "Салық төлеушінің сәйкестендіру нөмірі"),
            LocalizedStringDataModel("ky", "Салык төлөөчүнүн идентификациялык номери")
        ),
        placeholder = listOf(
            LocalizedStringDataModel("main", "9 digits"),
            LocalizedStringDataModel("en", "9 digits"),
            LocalizedStringDataModel("ru", "9 цифр"),
            LocalizedStringDataModel("kk", "9 сан"),
            LocalizedStringDataModel("ky", "9 цифра")
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

private data class AitaDataStateSnapshot<T>(
    val state: DataState<T>,
    val payload: T?
)

private data class AitaNonNullDataStateSnapshot<T>(
    val state: DataState<T>,
    val payload: T
)

private inline fun <T> MutableStateFlow<T>.aitaAtomicUpdate(transform: (T) -> T) {
    while (true) {
        val current = value
        val next = transform(current)
        if (compareAndSet(current, next)) return
    }
}

/**
 * A read-only StateFlow projection backed by one authoritative snapshot flow.
 *
 * AITA exposes both a DataState envelope and a convenient payload flow. Keeping those as two
 * independently mutable StateFlows allows concurrent writers to interleave their assignments and
 * leave a final impossible pair (for example, Success(A) beside payload B). This projection lets
 * both public views read and collect from the same atomic MutableStateFlow snapshot instead.
 */
@OptIn(InternalCoroutinesApi::class)
private class AitaMappedStateFlow<Source, Value>(
    private val source: StateFlow<Source>,
    private val transform: (Source) -> Value
) : StateFlow<Value> {
    override val value: Value
        get() = transform(source.value)

    override val replayCache: List<Value>
        get() = listOf(value)

    override suspend fun collect(collector: FlowCollector<Value>): Nothing {
        source
            .map(transform)
            .distinctUntilChanged()
            .collect(collector)
        error("AITA StateFlow projection completed unexpectedly")
    }
}

class MutableDataStateFlowNonNull<T>(
    @Suppress("UNUSED_PARAMETER") coroutineScope: CoroutineScope,
    initial: T
): DataStateFlowNonNull<T> {

    private val snapshot = MutableStateFlow(
        AitaNonNullDataStateSnapshot<T>(
            state = DataState.Success(initial),
            payload = initial
        )
    )
    override val value: StateFlow<DataState<T>> = AitaMappedStateFlow(snapshot) { it.state }
    override val payload: StateFlow<T> = AitaMappedStateFlow(snapshot) { it.payload }

    /**
     * Atomically publishes the envelope together with its matching last successful payload.
     * Empty is still allowed to change the envelope without erasing the non-null payload contract.
     */
    fun emit(newValue: DataState<T>) {
        snapshot.aitaAtomicUpdate { current ->
            AitaNonNullDataStateSnapshot(
                state = newValue,
                payload = when (newValue) {
                    is DataState.Success -> newValue.payload
                    is DataState.Empty -> current.payload
                }
            )
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
    @Suppress("UNUSED_PARAMETER") coroutineScope: CoroutineScope,
    initial: T? = null
): DataStateFlow<T> {

    private val snapshot = MutableStateFlow(
        AitaDataStateSnapshot<T>(
            state = initial?.let { DataState.Success(it) } ?: DataState.Empty(),
            payload = initial
        )
    )
    override val value: StateFlow<DataState<T>> = AitaMappedStateFlow(snapshot) { it.state }
    override val payload: StateFlow<T?> = AitaMappedStateFlow(snapshot) { it.payload }

    /**
     * Atomically publishes one nullable payload/envelope snapshot. Concurrent reads and writes can
     * no longer leave the two public views permanently describing different operations.
     */
    fun emit(newValue: DataState<T>) {
        snapshot.aitaAtomicUpdate {
            AitaDataStateSnapshot(
                state = newValue,
                payload = when (newValue) {
                    is DataState.Success -> newValue.payload
                    is DataState.Empty -> null
                }
            )
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
    val message: String? = null,
    val payload: String? = null,
    val negative: Boolean = true
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
data class AitaServerBootstrapCandidateDataModel(
    val url: String,
    val priority: Int = 0,
    val supportsRealtime: Boolean = true,
    val role: String = ""
)

@kotlinx.serialization.Serializable
data class AitaServerBootstrapDataModel(
    val serverUrl: String,
    val serverCandidates: List<AitaServerBootstrapCandidateDataModel> = emptyList(),
    val globalConfigPath: String = "config/global",
    val globalConfigUrl: String = "",
    val environment: String = "",
    val version: String = "",
    val updatedAtMillis: Long = 0L
)

@kotlinx.serialization.Serializable
data class AitaServerBootstrapCacheDataModel(
    val serverUrl: String,
    val serverCandidates: List<String> = emptyList(),
    val bootstrapUrl: String = "",
    val fetchedAtMillis: Long = 0L
)

@kotlinx.serialization.Serializable
data class AitaLastKnownGoodServerUrlCacheDataModel(
    val serverUrl: String,
    val verifiedAtMillis: Long = 0L
)

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
    val getStockItemHistoryPath: Pair<String, String> = Pair("stock/history/get", "1330"),
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
    val getMySupplierGoodsPricesPath: Pair<String, String> = Pair("supplierGoodsPrices/my", "1685"),
    val upsertSupplierGoodsPricePath: Pair<String, String> = Pair("supplierGoodsPrices/upsert", "32"),
    val deleteSupplierGoodsPricesPath: Pair<String, String> = Pair("supplierGoodsPrices/delete", "33"),

    val getSupplierOrdersPath: Pair<String, String> = Pair("supplierOrders/get", "34"),
    val addSupplierOrderPath: Pair<String, String> = Pair("supplierOrders/add", "35"),
    val updateSupplierOrderPath: Pair<String, String> = Pair("supplierOrders/update", "36"),
    val updateSupplierOrdersStatusPath: Pair<String, String> = Pair("supplierOrders/status", "1751"),
    val deleteSupplierOrdersPath: Pair<String, String> = Pair("supplierOrders/delete", "37"),
    val receiveSupplierOrderPath: Pair<String, String> = Pair("supplierOrders/receive", "38"),
    val getSupplierDashboardPath: Pair<String, String> = Pair("supplierOrders/dashboard", "1616"),
    val getSupplierContractsPath: Pair<String, String> = Pair("supplierContracts/get", "1479"),
    val upsertSupplierContractPath: Pair<String, String> = Pair("supplierContracts/upsert", "1480"),
    val acceptSupplierContractPath: Pair<String, String> = Pair("supplierContracts/accept", "1481"),
    val declineSupplierContractPath: Pair<String, String> = Pair("supplierContracts/decline", "1482"),
    val archiveSupplierContractPath: Pair<String, String> = Pair("supplierContracts/archive", "1483"),
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

    val goodsItemNameSnapshot: List<LocalizedStringDataModel> = emptyList(),
    val goodsItemBarcodeSnapshots: List<String> = emptyList(),
    val goodsItemMeasurementUnitIdSnapshot: String? = null,
    val substituteGoodsItemNameSnapshot: List<LocalizedStringDataModel> = emptyList(),
    val substituteGoodsItemBarcodeSnapshots: List<String> = emptyList(),
    val substituteGoodsItemMeasurementUnitIdSnapshot: String? = null,

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
    val storeNameSnapshot: List<LocalizedStringDataModel> = emptyList(),
    val storePublicIdSnapshot: String = "",
    val storeAddressTextSnapshot: String = "",

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
    val marketplaceProfile: StockMarketplaceProfile? = null,

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
    val returnReason: String = "",
    val stockBatchId: String? = null
)

@kotlinx.serialization.Serializable
data class LocalizedStringDataModel(
    val language: String,
    val value: String,
    // Present only for application event wording, never inferred from customer-entered names.
    val messageTemplate: EventMessageReference? = null
)

@kotlinx.serialization.Serializable
data class LocalizedStringGroupDataModel(
    val id: Long,
    val values: List<LocalizedStringDataModel>
)

const val AITA_ADDRESS_PROVIDER_YANDEX = "yandex"
const val AITA_ADDRESS_REFRESH_INTERVAL_MILLIS = 7L * 24L * 60L * 60L * 1000L
const val AITA_ADDRESS_CLIENT_REFRESH_COOLDOWN_MILLIS = 60L * 60L * 1000L

@kotlinx.serialization.Serializable
data class LocationDataModel(
    val name: String = "",
    val postalIndex: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val provider: String = "",
    val providerObjectId: String = "",
    val kind: String = "",
    val countryCode: String = "",
    val primaryLanguage: String = "",
    val localizedNames: List<LocalizedStringDataModel> = emptyList(),
    val localizedAddresses: List<LocalizedStringDataModel> = emptyList(),
    val fallbackAddress: String = "",
    val resolvedAtMillis: Long = 0L,
    val lastCheckedAtMillis: Long = 0L,
    val providerRevision: String = ""
)

@kotlinx.serialization.Serializable
data class AddressSuggestionDataModel(
    val provider: String = AITA_ADDRESS_PROVIDER_YANDEX,
    val providerObjectId: String,
    val title: String,
    val subtitle: String = "",
    val formattedAddress: String = "",
    val kind: String = "",
    val countryCode: String = "",
    val distanceMeters: Double? = null
)

@kotlinx.serialization.Serializable
data class AddressSuggestRequestDataModel(
    val query: String,
    val language: String = DEFAULT_APP_LANGUAGE,
    val countryCodes: List<String> = emptyList(),
    val userLatitude: Double? = null,
    val userLongitude: Double? = null,
    val limit: Int = 8
)

@kotlinx.serialization.Serializable
data class AddressResolveRequestDataModel(
    val provider: String = AITA_ADDRESS_PROVIDER_YANDEX,
    val providerObjectId: String,
    val query: String = "",
    val language: String = DEFAULT_APP_LANGUAGE,
    val countryCode: String = ""
)

@kotlinx.serialization.Serializable
data class AddressMapPreviewRequestDataModel(
    val location: LocationDataModel,
    val language: String = DEFAULT_APP_LANGUAGE,
    val darkTheme: Boolean = false,
    val width: Int = 650,
    val height: Int = 360,
    val zoom: Int = 16
)

@kotlinx.serialization.Serializable
data class AddressMapPreviewDataModel(
    val url: String,
    val openMapUrl: String,
    val expiresAtMillis: Long,
    val attribution: String = "© Yandex Maps"
)

@kotlinx.serialization.Serializable
data class StoreAddressRefreshRequestDataModel(
    val storeIds: List<String> = emptyList(),
    val force: Boolean = false
)

@kotlinx.serialization.Serializable
data class StoreAddressRefreshResultDataModel(
    val stores: List<StoreDataModel> = emptyList(),
    val refreshedCount: Int = 0,
    val changedCount: Int = 0
)

fun LocationDataModel.hasValidCoordinates(): Boolean =
    latitude.isFinite() && longitude.isFinite() &&
        latitude in -90.0..90.0 && longitude in -180.0..180.0 &&
        !(latitude == 0.0 && longitude == 0.0)

fun LocationDataModel.displayName(language: String): String =
    localizedNames.extractLocalizedString(language)
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: name.trim()

fun LocationDataModel.displayAddress(language: String): String =
    localizedAddresses.extractLocalizedString(language)
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: fallbackAddress.trim().takeIf { it.isNotBlank() }
        ?: name.trim()

fun LocationDataModel.isResolvedAddress(): Boolean =
    provider.equals(AITA_ADDRESS_PROVIDER_YANDEX, ignoreCase = true) &&
        providerObjectId.isNotBlank() &&
        hasValidCoordinates() &&
        displayAddress(primaryLanguage.ifBlank { DEFAULT_APP_LANGUAGE }).isNotBlank()

fun LocationDataModel.needsProviderRefresh(nowMillis: Long = getCurrentTimeMillis()): Boolean =
    isResolvedAddress() &&
        (lastCheckedAtMillis <= 0L || nowMillis - lastCheckedAtMillis >= AITA_ADDRESS_REFRESH_INTERVAL_MILLIS)

fun LocationDataModel.matchesDisplayedAddress(rawText: String, language: String): Boolean {
    val normalized = rawText.trim().replace(Regex("\\s+"), " ")
    if (normalized.isBlank()) return false
    return buildList {
        add(displayAddress(language))
        add(displayAddress(primaryLanguage))
        add(fallbackAddress)
        add(name)
        addAll(localizedAddresses.map { it.value })
    }.any { candidate ->
        candidate.trim().replace(Regex("\\s+"), " ").equals(normalized, ignoreCase = true)
    }
}

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
    val isSavedOnServer: Boolean = false,
    val messageTemplate: EventMessageReference? = null,
    val titleTemplate: EventMessageReference? = null,
    val messageTranslations: List<LocalizedStringDataModel> = emptyList(),
    val titleTranslations: List<LocalizedStringDataModel> = emptyList()
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
    val isActive: Boolean = true,
    val revision: Long = 0L
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
    val isActive: Boolean = true,
    val sequence: Long = 0L
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
    val userId: String? = null,
    val reason: String? = null,
    val createdAtMillis: Long = 0L,
    val heartbeatIntervalMillis: Long = 0L,
    val sequence: Long? = null
)

@kotlinx.serialization.Serializable
data class RealtimeClientHelloDataModel(
    val activeStoreId: String? = null,
    val language: String = "",
    val platform: String = "",
    val clientTimeMillis: Long = 0L,
    val heartbeatVersion: Int = 0
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
    val updatedAtMillis: Long = 0L,
    val stockLoaded: Boolean = true,
    val stockBatchesLoaded: Boolean = true
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
    val cleanQuery = query.trim()
    if (cleanQuery.isBlank()) return emptyList<T>() to false

    val uniqueHits = mutableListOf<Searchable>()
    for (item in this) {
        if (item.searchUnique(cleanQuery, *extraOperands)) {
            uniqueHits += item
            if (uniqueHits.size > 1) break
        }
    }
    if (uniqueHits.size == 1) {
        return uniqueHits.map { it as T } to true
    }

    val exact = mutableListOf<Searchable>()
    val contains = mutableListOf<Searchable>()
    for (item in this) {
        if (item.searchExact(cleanQuery, *extraOperands)) {
            exact += item
        } else if (item.searchContains(cleanQuery, *extraOperands)) {
            contains += item
        }
    }

    return (exact + contains).map { it as T } to false
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
        val cleanQuery = query.trim().takeIf { it.isNotBlank() } ?: return false
        return uniqueSearchOperands.any { it.equals(cleanQuery, true) }
            || extraOperands.any { it.equals(cleanQuery, true) }
    }
}

abstract class StateHost {
    private val _state = MutableStateFlow(mapOf<String, String>())
    val state = _state.asStateFlow()

    suspend fun setState(pair: Pair<String, String>) = setStateNow(pair)

    /** Non-suspending atomic write for disposal callbacks; persistence remains in host collectors. */
    fun setStateNow(pair: Pair<String, String>) {
        _state.update { current ->
            current.toMutableMap().apply {
                this[pair.first] = pair.second
            }
        }
    }

    /**
     * Applies related navigation values in one StateFlow emission. Screens that both persist and
     * adopt navigation state must never observe a half-written search/filter/sort combination.
     * MutableStateFlow.update also prevents a simultaneous unrelated state write from being lost.
     */
    suspend fun setStates(vararg pairs: Pair<String, String>) {
        if (pairs.isEmpty()) return
        _state.update { current ->
            current.toMutableMap().apply {
                pairs.forEach { (key, value) -> this[key] = value }
            }
        }
    }

    suspend fun removeState(key: String) {
        _state.update { current ->
            current.toMutableMap().apply {
                remove(key)
            }
        }
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
    val branches: List<StoreDataModel> = emptyList(),
    val contactVerificationId: String = "",
    val contactEmailProofs: List<kz.aita.auth.AitaVerifiedContactProof> = emptyList()
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
                    add(location.fallbackAddress)
                    location.localizedNames.forEach { add(it.value) }
                    location.localizedAddresses.forEach { add(it.value) }
                    add(address)
                    add(legalIdTypeId)
                    add(legalId)
                    branches.forEach { branch ->
                        add(branch.id)
                        add(branch.publicId)
                        add(branch.address)
                        add(branch.location.name)
                        add(branch.location.fallbackAddress)
                        branch.location.localizedNames.forEach { add(it.value) }
                        branch.location.localizedAddresses.forEach { add(it.value) }
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
                    add(location.fallbackAddress)
                    location.localizedNames.forEach { add(it.value) }
                    location.localizedAddresses.forEach { add(it.value) }
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

fun StoreDataModel.displayAddress(language: String): String =
    location.displayAddress(language)
        .ifBlank { address.trim() }
        .ifBlank { location.name.trim() }

fun StoreDataModel.displayAddress(): String =
    address.trim().ifBlank { location.displayAddress(DEFAULT_APP_LANGUAGE) }

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
    val isActive: Boolean = true,
    val contactVerificationId: String = "",
    val contactEmailProofs: List<kz.aita.auth.AitaVerifiedContactProof> = emptyList()
) {
    fun isMineForUser(userId: String?): Boolean = !userId.isNullOrBlank() && userIds.contains(userId)
    fun isGenericSupplier(): Boolean = userIds.isEmpty()
}

fun List<SupplierDataModel>.upsertById(item: SupplierDataModel): List<SupplierDataModel> {
    val index = indexOfFirst { it.id == item.id }
    return if (index < 0) this + item else toMutableList().also { it[index] = item }
}

fun List<SupplierDataModel>.supplierProfilesOwnedBy(userId: String?): List<SupplierDataModel> {
    val cleanUserId = userId?.trim()?.takeIf { it.isNotBlank() } ?: return emptyList()
    return filter { supplier -> supplier.isActive && supplier.userIds.contains(cleanUserId) }
        .sortedBy { supplier ->
            supplier.name.extractLocalizedString("main")
                ?: supplier.name.firstOrNull()?.value
                ?: supplier.id
        }
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
        val legacySupplier = this.supplierId?.toString()?.takeIf { it.isNotBlank() }
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
    val isActive: Boolean,
    val appModeId: Int? = null
)

@kotlinx.serialization.Serializable
class UserAccountUpdateDataModel(
    val account: UserAccountDataModel,
    val password: String,
    val newPassword: String?,
    val secondFactorCode: String = "",
    val emailProof: kz.aita.auth.AitaSecurityEmailProof? = null,
    val contactEmailProofs: List<kz.aita.auth.AitaVerifiedContactProof> = emptyList()
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
    val deviceInfo: ClientDeviceInfoDataModel? = null,
    val contactVerificationId: String = "",
    val contactEmailProofs: List<kz.aita.auth.AitaVerifiedContactProof> = emptyList()
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
    val jobTitle: String = "",
    val jobTitleLocalized: List<LocalizedStringDataModel> = emptyList(),
    val salary: String = "",
    val salaryCurrencyCode: String = "KZT",
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
    val jobTitle: String = "",
    val jobTitleLocalized: List<LocalizedStringDataModel> = emptyList(),
    val salary: String = "",
    val salaryCurrencyCode: String = "KZT",
    val offerNote: String? = null,
    val offerNoteLocalized: List<LocalizedStringDataModel> = emptyList(),
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
    val jobTitle: String = "",
    val jobTitleLocalized: List<LocalizedStringDataModel> = emptyList(),
    val salary: String = "",
    val salaryCurrencyCode: String = "KZT",
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
    val jobTitle: String = "",
    val jobTitleLocalized: List<LocalizedStringDataModel> = emptyList(),
    val salary: String = "",
    val salaryCurrencyCode: String = "KZT",
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
    val jobTitle: String = "",
    val jobTitleLocalized: List<LocalizedStringDataModel> = emptyList(),
    val salary: String = "",
    val salaryCurrencyCode: String = "KZT",
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
    val createdAtMillis: Long = 0L,
    val titleTemplate: EventMessageReference? = null,
    val detailsTemplate: EventMessageReference? = null
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
