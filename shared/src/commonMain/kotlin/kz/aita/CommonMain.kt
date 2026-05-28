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
        id = "starter_monthly_kzt",
        name = listOf(
            LocalizedStringDataModel("main", "Starter"),
            LocalizedStringDataModel("en", "Starter"),
            LocalizedStringDataModel("ru", "Старт"),
            LocalizedStringDataModel("kk", "Бастау")
        ),
        description = listOf(
            LocalizedStringDataModel("main", "Small store, one branch, basic analytics"),
            LocalizedStringDataModel("ru", "Небольшой магазин, один филиал, базовая аналитика"),
            LocalizedStringDataModel("kk", "Шағын дүкен, бір филиал, негізгі аналитика")
        ),
        priceMinor = 499000L,
        maxBranches = 1,
        maxWorkers = 3,
        maxStockItems = 2000
    ),
    StoreSubscriptionPlanDataModel(
        id = "business_monthly_kzt",
        name = listOf(
            LocalizedStringDataModel("main", "Business"),
            LocalizedStringDataModel("en", "Business"),
            LocalizedStringDataModel("ru", "Бизнес"),
            LocalizedStringDataModel("kk", "Бизнес")
        ),
        description = listOf(
            LocalizedStringDataModel("main", "Branches, workers, cash register, realtime sync"),
            LocalizedStringDataModel("ru", "Филиалы, сотрудники, касса, онлайн-синхронизация"),
            LocalizedStringDataModel("kk", "Филиалдар, қызметкерлер, касса, нақты уақыт синхрондауы")
        ),
        priceMinor = 1499000L,
        maxBranches = 5,
        maxWorkers = 20,
        maxStockItems = 20000
    ),
    StoreSubscriptionPlanDataModel(
        id = "business_yearly_kzt",
        name = listOf(
            LocalizedStringDataModel("main", "Business yearly"),
            LocalizedStringDataModel("en", "Business yearly"),
            LocalizedStringDataModel("ru", "Бизнес на год"),
            LocalizedStringDataModel("kk", "Жылдық бизнес")
        ),
        description = listOf(
            LocalizedStringDataModel("main", "Twelve months for the price of ten"),
            LocalizedStringDataModel("ru", "Двенадцать месяцев по цене десяти"),
            LocalizedStringDataModel("kk", "Он ай бағасына он екі ай")
        ),
        priceMinor = 14990000L,
        periodUnit = SUBSCRIPTION_PERIOD_YEAR,
        maxBranches = 5,
        maxWorkers = 20,
        maxStockItems = 20000
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
    SoldOut,
    WrittenOff,
    Deleted
}

@kotlinx.serialization.Serializable
enum class SupplierOrderStatusDataModel {
    Draft,
    Sent,
    Confirmed,
    PartiallyDelivered,
    Delivered,
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
private val getTransactionsMutex = Mutex()

data class TransactionPaymentDraftDataModel(
    val transactionTypeIndex: Int,
    val clientId: Int,
    val paymentModeId: String,
    val paidCash: Double,
    val paidCard: Double,
    val cardPaymentOptionId: Int,
    val debtor: DebtorDataModel? = null
)

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
) {
    val total: Double
        get() = quantity.total * pricePerUnit
}


data class ReceiptPlatformActionResult(
    val success: Boolean,
    val message: String = ""
)

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
    val pdfExportNotConfigured: String = "PDF export is not configured for this platform",
    val pdfSharingNotConfigured: String = "PDF sharing is not configured for this platform",
    val printerNotConfigured: String = "Receipt printer is not configured for this platform"
)

var saveReceiptPdfFile: (suspend (fileName: String, pdfBytes: ByteArray) -> ReceiptPlatformActionResult)? = null
var shareReceiptPdfFile: (suspend (fileName: String, pdfBytes: ByteArray, whatsappOnly: Boolean) -> ReceiptPlatformActionResult)? = null
var printReceiptEscPosBytes: (suspend (printerBytes: ByteArray) -> ReceiptPlatformActionResult)? = null

suspend fun saveReceiptPdf(fileName: String, pdfBytes: ByteArray, labels: ReceiptTextLabelsDataModel = ReceiptTextLabelsDataModel()): ReceiptPlatformActionResult {
    return saveReceiptPdfFile?.invoke(fileName, pdfBytes)
        ?: ReceiptPlatformActionResult(false, labels.pdfExportNotConfigured)
}

suspend fun shareReceiptPdf(fileName: String, pdfBytes: ByteArray, whatsappOnly: Boolean = false, labels: ReceiptTextLabelsDataModel = ReceiptTextLabelsDataModel()): ReceiptPlatformActionResult {
    return shareReceiptPdfFile?.invoke(fileName, pdfBytes, whatsappOnly)
        ?: ReceiptPlatformActionResult(false, labels.pdfSharingNotConfigured)
}

suspend fun printReceiptEscPos(printerBytes: ByteArray, labels: ReceiptTextLabelsDataModel = ReceiptTextLabelsDataModel()): ReceiptPlatformActionResult {
    return printReceiptEscPosBytes?.invoke(printerBytes)
        ?: ReceiptPlatformActionResult(false, labels.printerNotConfigured)
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
    store?.phoneNumbers?.takeIf { it.isNotEmpty() }?.let { builder.appendLine("${labels.phone}: ${it.joinToString()}") }
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
            debtor.phoneNumber.takeIf { it.isNotBlank() }?.let { builder.appendLine("${labels.debtorPhone}: $it") }
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
    GlobalScope.launch {
        val normalizedSaleMethodId = if (saleMethodId == SALE_METHOD_WHOLESALE) {
            SALE_METHOD_WHOLESALE
        } else {
            SALE_METHOD_RETAIL
        }

        cartSaleMethodIdsState.emit(
            cartSaleMethodIdsState.value.toMutableMap().apply {
                val key = "${transactionKey(transactionTypeIndex, clientId)}:$goodsItemId"
                if (normalizedSaleMethodId == SALE_METHOD_RETAIL) {
                    remove(key)
                } else {
                    this[key] = normalizedSaleMethodId
                }
            }
        )
    }
}

private fun removeCartSaleMethodId(transactionTypeIndex: Int, clientId: Int, goodsItemId: String) {
    GlobalScope.launch {
        cartSaleMethodIdsState.emit(
            cartSaleMethodIdsState.value.toMutableMap().apply {
                remove("${transactionKey(transactionTypeIndex, clientId)}:$goodsItemId")
            }
        )
    }
}

private fun removeCartSaleMethodIds(transactionTypeIndex: Int, clientId: Int) {
    val prefix = "${transactionKey(transactionTypeIndex, clientId)}:"

    GlobalScope.launch {
        cartSaleMethodIdsState.emit(
            cartSaleMethodIdsState.value.filterKeys { !it.startsWith(prefix) }
        )
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

fun setTransactionPaymentDraft(draft: TransactionPaymentDraftDataModel) {
    GlobalScope.launch {
        transactionPaymentDraftsState.emit(
            transactionPaymentDraftsState.value.toMutableMap().apply {
                this[transactionKey(draft.transactionTypeIndex, draft.clientId)] = draft
            }
        )
    }
}

fun getTransactionPaymentDraft(
    transactionTypeIndex: Int,
    clientId: Int
): TransactionPaymentDraftDataModel? {
    return transactionPaymentDraftsState.value[transactionKey(transactionTypeIndex, clientId)]
}

fun clearTransactionPaymentDraft(transactionTypeIndex: Int, clientId: Int) {
    GlobalScope.launch {
        transactionPaymentDraftsState.emit(
            transactionPaymentDraftsState.value.toMutableMap().apply {
                remove(transactionKey(transactionTypeIndex, clientId))
            }
        )
    }
}

private val transactionSupplySupplierIdsState = MutableStateFlow<Map<String, String>>(emptyMap())

fun getTransactionSupplySupplierIdsState(): StateFlow<Map<String, String>> = transactionSupplySupplierIdsState.asStateFlow()

fun transactionSupplySupplierKey(transactionTypeIndex: Int, clientId: Int): String = "$transactionTypeIndex:$clientId"

fun currentTransactionSupplySupplierId(transactionTypeIndex: Int, clientId: Int): String? =
    transactionSupplySupplierIdsState.value[transactionSupplySupplierKey(transactionTypeIndex, clientId)]

fun setTransactionSupplySupplierId(transactionTypeIndex: Int, clientId: Int, supplierId: String?) {
    GlobalScope.launch {
        val key = transactionSupplySupplierKey(transactionTypeIndex, clientId)
        transactionSupplySupplierIdsState.emit(
            transactionSupplySupplierIdsState.value.toMutableMap().apply {
                if (supplierId.isNullOrBlank()) remove(key) else put(key, supplierId)
            }
        )
    }
}

fun clearTransactionSupplySupplierId(transactionTypeIndex: Int, clientId: Int) {
    setTransactionSupplySupplierId(transactionTypeIndex, clientId, null)
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

fun GoodsItemDataModel.priceForTransaction(
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

    return barcodes.any { barcode ->
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
    return barcodes.firstOrNull()?.toStoredGoodsItemBarcode().orEmpty()
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
    val notes: String? = null
)

fun changeCartQuantity(
    id: String,
    transactionTypeIndex: Int,
    clientId: Int,
    current: QuantityDataModel,
    deltaSteps: Int
) {
    setCartQuantity(
        id = id,
        transactionTypeIndex = transactionTypeIndex,
        clientId = clientId,
        current = current,
        total = current.total + current.pricedAmount * deltaSteps
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

    if (nextTotal <= 0.0) {
        deleteCartById(id, transactionTypeIndex, clientId)
        removeCartSaleMethodId(transactionTypeIndex, clientId, id)
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

fun requestStoreEmployment(
    storeId: String,
    onCompleted: ((DataState<StoreWorkerRequestDataModel>) -> Unit)? = null
) {
    if (!requestStoreEmploymentMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            requestStoreEmploymentMutex.withLock {
                val response = networkRequest<StoreWorkerRequestDataModel, WorkerEmploymentRequestCreateDataModel>(
                    method = HttpMethod.Post,
                    endpointUrl = globalAppConfigurationState.payloadValue.requestStoreEmploymentPath.first,
                    body = WorkerEmploymentRequestCreateDataModel(storeId.trim())
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
                val response = networkRequest<StoreWorkerRequestDataModel, WorkerStoreInviteCreateDataModel>(
                    method = HttpMethod.Post,
                    endpointUrl = globalAppConfigurationState.payloadValue.inviteStoreWorkerPath.first,
                    body = WorkerStoreInviteCreateDataModel(userId.trim(), roleId, permissions, note, workerPassword?.takeIf { it.isNotBlank() }),
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
                    body = WorkerStoreInvitationDecisionDataModel(requestId, note)
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
                    body = WorkerStoreInvitationDecisionDataModel(requestId, note)
                )

                if (response.negative || response.payload == null) {
                    postInAppNotification(response.message, NotificationType.Negative)
                    onCompleted?.invoke(DataState.Empty(response.message))
                } else {
                    getMyWorkerRequests()
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
                    body = WorkerEmploymentDecisionRequestDataModel(requestId, roleId, permissions, note, workerPassword?.takeIf { it.isNotBlank() }),
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
                    getIncomingWorkerRequests(storeId)
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
                    body = WorkerEmploymentDecisionRequestDataModel(requestId, WORKER_ROLE_STANDARD, emptyList(), note, null),
                    headers = mapOf("store_id" to storeId)
                )

                if (response.negative || response.payload == null) {
                    postInAppNotification(response.message, NotificationType.Negative)
                    onCompleted?.invoke(DataState.Empty(response.message))
                } else {
                    getIncomingWorkerRequests(storeId)
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
                    body = WorkerPermissionsUpdateRequestDataModel(workerId, roleId, permissions, workerPassword?.takeIf { it.isNotBlank() }),
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
                    activeWorkshiftState.emit(DataState.Empty(response.message))
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
                        body = WorkshiftStartRequestDataModel(workerIdentifier.trim(), password),
                        headers = mapOf("store_id" to storeId)
                    )

                    if (response.negative || response.payload == null) {
                        postInAppNotification(response.message, NotificationType.Negative)
                        activeWorkshiftState.emit(DataState.Empty(response.message))
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
                val response = networkRequest<WorkshiftDataModel, Unit>(
                    method = HttpMethod.Post,
                    endpointUrl = globalAppConfigurationState.payloadValue.endWorkshiftPath.first,
                    headers = mapOf("store_id" to id)
                )

                if (response.negative || response.payload == null) {
                    postInAppNotification(response.message, NotificationType.Negative)
                    onCompleted?.invoke(DataState.Empty(response.message))
                } else {
                    activeWorkshiftState.emit(DataState.Empty(response.message))
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

    return myWorkerMembershipsState.payloadValue.orEmpty()
        .find { it.userId == currentUserId && it.isActive && (it.storeId == cleanStoreId || it.storeId == cleanRootStoreId) }
        ?.permissions
        ?.toSet()
        .orEmpty()
}

fun currentUserHasStorePermission(storeId: String?, permission: String): Boolean {
    return permission in currentUserStorePermissions(storeId)
}

fun currentUserCanExtractCashRegister(storeId: String?): Boolean {
    return currentUserHasStorePermission(storeId, STORE_PERMISSION_CASH_REGISTER_EXTRACT)
}

fun completeTransaction(
    transaction: TransactionDataModel,
    transactionTypeIndex: Int,
    clientId: Int,
    receiptSnapshot: TransactionReceiptSnapshotDataModel,
    onCompleted: (() -> Unit)? = null
) {
    if (!completeTransactionMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            completeTransactionMutex.withLock {
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
                    body = transaction
                )

                if (response.negative || response.payload == null) {
                    postInAppNotification(response.message, NotificationType.Negative)
                    onCompleted?.invoke()
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
private val getSecuritySessionsMutex = Mutex()
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

val GlobalScope = CoroutineScope(SupervisorJob())
val appModeState = MutableStateFlow(0)
val globalAppConfigurationState = MutableDataStateFlowNonNull(
    coroutineScope = GlobalScope,
    initial = GlobalAppConfigurationDataModel(
        realtimeUpdatesPath = "rt/updates",
        appName = Pair("AITA", "0"),
        serverUrl = Pair("http://10.168.22.26", "1"),
        globalAppConfigurationPath = Pair("config/global", "2"),
        logInPath = Pair("auth/logIn", "3"),
        signUpPath = Pair("auth/signUp", "4"),
        refreshPath = Pair("auth/refresh", "5"),
        logOutPath = Pair("auth/logOut", "6"),
        getSecuritySessionsPath = Pair("security/sessions/get", "44"),
        revokeSecuritySessionPath = Pair("security/sessions/revoke", "45"),
        revokeOtherSecuritySessionsPath = Pair("security/sessions/revokeOthers", "46"),
        getUserPath = Pair("user/get", "7"),
        updateUserPath = Pair("user/update", "8"),
        getStoresPath = Pair("stores/get", "9"),
        addStoresPath = Pair("stores/add", "10"),
        updateStoresPath = Pair("stores/update", "11"),
        deleteStoresPath = Pair("stores/delete", "12"),
        getStockPath = Pair("stock/get", "13"),
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
        requestStoreEmploymentPath = Pair("workers/request", "55"),
        acceptStoreEmploymentPath = Pair("workers/accept", "56"),
        declineStoreEmploymentPath = Pair("workers/decline", "57"),
        updateStoreWorkerPermissionsPath = Pair("workers/updatePermissions", "58"),
        inviteStoreWorkerPath = Pair("workers/invite", "65"),
        acceptStoreWorkerInvitationPath = Pair("workers/invitations/accept", "66"),
        declineStoreWorkerInvitationPath = Pair("workers/invitations/decline", "67"),
        getUserFinanceDashboardPath = Pair("finance/dashboard", "77"),
        createTopUpPaymentPath = Pair("finance/topup/create", "78"),
        confirmDevelopmentTopUpPath = Pair("finance/topup/confirmDevelopment", "79"),
        getSubscriptionPlansPath = Pair("subscriptions/plans", "80"),
        getStoreSubscriptionPath = Pair("subscriptions/store/get", "81"),
        updateStoreSubscriptionPath = Pair("subscriptions/store/update", "82"),
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
val appLanguageState = MutableStateFlow("system")
val appThemeIdState = MutableStateFlow(0L)
val appSizeModeIdState = MutableStateFlow(0L)
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
    MutableStateFlow("Password must be 8 or more symbols long and contain at least one digit")
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
val stringSessionTimeExpiredLoggingOutState = MutableStateFlow("Session expired")
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
val drawablePathIconStockState = MutableStateFlow("svg/13_0.svg")
val drawablePathIconMenuState = MutableStateFlow("svg/14_0.svg")
val drawablePathIconBackArrowState = MutableStateFlow("svg/15_0.svg")
val drawablePathIconAddState = MutableStateFlow("svg/16_0.svg")
val drawablePathIconUserAccountState = MutableStateFlow("svg/17_0.svg")
val drawablePathIconGoodsCategoriesState = MutableStateFlow("svg/18_0.svg")
val drawablePathIconStoresState = MutableStateFlow("svg/19_0.svg")
val drawablePathIconTransactionHistoryState = MutableStateFlow("svg/20_0.svg")
val drawablePathIconAnalyticsState = MutableStateFlow("svg/21_0.svg")
val drawablePathIconWorkersState = MutableStateFlow("svg/22_0.svg")
val drawablePathIconSuppliersState = MutableStateFlow("svg/23_0.svg")
val drawablePathIconDebtorsState = MutableStateFlow("svg/24_0.svg")
val drawablePathIconDevicesState = MutableStateFlow("svg/25_0.svg")
val drawablePathIconAppLanguageState = MutableStateFlow("svg/26_0.svg")
val drawablePathIconAppThemeState = MutableStateFlow("svg/27_0.svg")
val drawablePathIconCheckState = MutableStateFlow("svg/28_0.svg")
val drawablePathIconEditState = MutableStateFlow("svg/29_0.svg")
val drawablePathIconSettingsState = MutableStateFlow("svg/30_0.svg")
val drawablePathIconSearchState = MutableStateFlow("svg/31_0.svg")
val drawablePathIconBarcodeCamScannerState = MutableStateFlow("svg/32_0.svg")
val drawablePathIconDeleteState = MutableStateFlow("svg/33_0.svg")
val drawablePathIconExitState = MutableStateFlow("svg/34_0.svg")
val drawablePathIconSwitchState = MutableStateFlow("svg/35_0.svg")
val drawablePathIconCartState = MutableStateFlow("svg/36_0.svg")
val drawablePathIconAddCartState = MutableStateFlow("svg/37_0.svg")
val drawablePathIconSubtractState = MutableStateFlow("svg/38_0.svg")
val drawablePathIconReceiptState = MutableStateFlow("svg/39_0.svg")
val drawablePathIconFinancesState = MutableStateFlow("svg/40_0.svg")

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

val appDatabase = AppDatabase(getSqlDelightDriver?.invoke()!!)

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

        install(HttpCache)
        install(WebSockets)
//
//    install(Logging) {
//      level = LogLevel.ALL
//    }
        install(Auth) {
            bearer {
                sendWithoutRequest {
                    it.url.host.equals(
                        Url(globalAppConfigurationState.payloadValue.serverUrl.first).host,
                        true
                    )
                            && !it.url.encodedPath.startsWith("/auth")
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
                            val current = getStoredUserAuthTokens?.invoke()

                            current ?: return@withLock null

                            val httpClient = HttpClient(getHttpClientEngine()) {
                                install(ContentNegotiation) {
                                    json(jsonBase)
                                }
                            }

                            val response = networkRequest<TokenPair, String>(
                                HttpMethod.Post,
                                endpointUrl = globalAppConfigurationState.payloadValue.refreshPath.first,
                                body = current.refreshToken
                            )

                            if (response.negative && response.payload == null) {
                                if (response.transportFailure) {
                                    postInAppNotification(
                                        localizedStringResourceMessage(
                                            id = 214,
                                            main = "Cannot reach server. Keeping you signed in offline.",
                                            ru = "Сервер недоступен. Вы остаётесь в аккаунте офлайн.",
                                            kk = "Сервер қолжетімсіз. Сіз офлайн режимде аккаунтта қаласыз."
                                        ),
                                        NotificationType.Neutral
                                    )
                                } else {
                                    postInAppNotification(
                                        stringSessionTimeExpiredLoggingOutState.value,
                                        NotificationType.Negative
                                    )
                                    delay(3000)
                                    forceLogOutUser()
                                }
                            }

                            httpClient.close()

                            if (response.payload != null) {
                                setStoredUserAuthTokens?.invoke(response.payload)
                                BearerTokens(response.payload.accessToken, response.payload.refreshToken)
                            } else if (response.transportFailure) {
                                current?.let { BearerTokens(it.accessToken, it.refreshToken) }
                            } else {
                                setStoredUserAuthTokens?.invoke(null)
                                null
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

val genericGoodsCategoriesState = MutableDataStateFlow<List<GenericGoodsCategoryDataModel>>(GlobalScope)

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

val stockState = MutableDataStateFlow<List<GoodsItemDataModel>>(GlobalScope)

val stockBatchesState = MutableDataStateFlow<List<GoodsBatchDataModel>>(GlobalScope)
val stockItemBranchAvailabilityState = MutableDataStateFlow<StockItemBranchAvailabilityDataModel>(GlobalScope)
val stockBatchMoveResultState = MutableDataStateFlow<StockBatchMoveResultDataModel>(GlobalScope)

val getStockMutex = Mutex()
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
val getUserAccountMutex = Mutex()
val updateUserMutex = Mutex()
val latestInAppNotificationState = MutableStateFlow<NotificationDataModel?>(null)
val activeInAppNotificationsState = MutableStateFlow<List<NotificationDataModel>>(emptyList())
val notificationsState = MutableDataStateFlow<List<NotificationDataModel>>(GlobalScope)
val getNotificationsMutex = Mutex()
val saveNotificationMutex = Mutex()
val markNotificationReadMutex = Mutex()

val cashRegisterExtractionsState =
    MutableDataStateFlow<List<CashRegisterExtractionEntryDataModel>>(GlobalScope)

val cashRegisterAmountState = MutableStateFlow(0.0)
val cashRegisterState = MutableDataStateFlow<StoreCashRegisterDataModel>(GlobalScope)
val cashRegisterEventsState = MutableDataStateFlow<List<CashRegisterEventDataModel>>(GlobalScope)

val storeWorkerMembershipsState = MutableDataStateFlow<List<StoreWorkerDataModel>>(GlobalScope)
val myWorkerMembershipsState = MutableDataStateFlow<List<StoreWorkerDataModel>>(GlobalScope)
val incomingWorkerRequestsState = MutableDataStateFlow<List<StoreWorkerRequestDataModel>>(GlobalScope)
val myWorkerRequestsState = MutableDataStateFlow<List<StoreWorkerRequestDataModel>>(GlobalScope)
val activeWorkshiftState = MutableDataStateFlow<WorkshiftDataModel>(GlobalScope)
val workshiftLoginInProgressState = MutableStateFlow(false)

private val getCashRegisterMutex = Mutex()
private val extractCashRegisterMutex = Mutex()
private val getStoreWorkersMutex = Mutex()
private val getMyWorkerMembershipsMutex = Mutex()
private val getIncomingWorkerRequestsMutex = Mutex()
private val getMyWorkerRequestsMutex = Mutex()
private val requestStoreEmploymentMutex = Mutex()
private val inviteStoreWorkerMutex = Mutex()
private val decideStoreEmploymentMutex = Mutex()
private val updateStoreWorkerPermissionsMutex = Mutex()
private val getCurrentWorkshiftMutex = Mutex()
private val startWorkshiftMutex = Mutex()
private val endWorkshiftMutex = Mutex()


























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
    return length >= 8 && isNotBlank() && any { it.isDigit() }
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
    return this
        ?.find { it.id == id }
        ?.values
        ?.extractLocalizedString(language)
}

fun List<StylizedDimensionGroupDataModel>.extractValue(id: Long, sizeModeId: Long): Float? {
    return find { it.id == id }?.values?.find { it.sizeModeId == -1L || it.sizeModeId == sizeModeId }?.value
}

fun List<StylizedColorGroupDataModel>.extractColor(id: Long, themeId: Long): String? {
    return find { it.id == id }?.values?.find { it.themeId == -1L || it.themeId == themeId }?.valueHex
}

fun List<StylizedDrawablePathsGroupDataModel>.extractPath(id: Long, themeId: Long): String? {
    return find { it.id == id }?.values?.find { it.themeId == -1L || it.themeId == themeId }?.path
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
    startAppCacheCollectors()

    GlobalScope.launch(Dispatchers.ourIo) {
        loadCachedApplicationData()
        if (getStoredUserAuthTokens?.invoke() != null) startRealtimeUpdates()
    }

    GlobalScope.launch {
        observeLocalKv(KEY_APP_LOCALE)
            .collect {
                it?.let {
                    appLanguageState.emit(it)
                }
            }
    }

    GlobalScope.launch {
        observeLocalKv(KEY_APP_THEME)
            .collect {
                it?.let {
                    appThemeIdState.emit(it.toLong())
                }
            }
    }

    GlobalScope.launch {
        observeLocalKv(KEY_APP_SIZE_MODE)
            .collect {
                it?.let {
                    appSizeModeIdState.emit(it.toLong())
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
            .collect {
                activeStoreIdState.emit(it)
                it?.let {
                    loadCachedStoreScopedData(it)
                    getStock(it)
                    getStockBatches(it)
                    getTransactions(it)
                    getCashRegister(it)
                    getStoreWorkers(it)
                    getIncomingWorkerRequests(it)
                    getMyWorkerMemberships()
                    getMyWorkerRequests()
                    getStoreSubscription(it)
                }
            }
    }

    GlobalScope.launch(Dispatchers.ourIo) {
        storesState.payload.collect {
            it?.let {
                val settableStores = it.settableActiveStores()
                if (settableStores.size == 1 && activeStoreIdState.value != settableStores.first().id) {
                    setActiveStoreId(settableStores.first().id)
                }
            }
        }
    }

    GlobalScope.launch(Dispatchers.ourIo) {
        observeCart(0, 0)
            .collect {
                cartTransactionType0_clientId0_state.emit(it?.filter { item ->
                    (stockState.payloadValue?.find { goodsItem -> goodsItem.id == item.id } != null).apply {
                        if (!this) deleteCartItemById(
                            item.id
                        )
                    }
                } ?: emptyList())
            }
    }

    GlobalScope.launch(Dispatchers.ourIo) {
        observeCart(0, 1)
            .collect {
                cartTransactionType0_clientId1_state.emit(it?.filter { item ->
                    (stockState.payloadValue?.find { goodsItem -> goodsItem.id == item.id } != null).apply {
                        if (!this) deleteCartItemById(
                            item.id
                        )
                    }
                } ?: emptyList())
            }
    }

    GlobalScope.launch(Dispatchers.ourIo) {

        observeCart(0, 2)
            .collect {
                cartTransactionType0_clientId2_state.emit(it?.filter { item ->
                    (stockState.payloadValue?.find { goodsItem -> goodsItem.id == item.id } != null).apply {
                        if (!this) deleteCartItemById(
                            item.id
                        )
                    }
                } ?: emptyList())
            }
    }

    GlobalScope.launch(Dispatchers.ourIo) {

        observeCart(0, 3)
            .collect {
                cartTransactionType0_clientId3_state.emit(it?.filter { item ->
                    (stockState.payloadValue?.find { goodsItem -> goodsItem.id == item.id } != null).apply {
                        if (!this) deleteCartItemById(
                            item.id
                        )
                    }
                } ?: emptyList())
            }
    }

    GlobalScope.launch(Dispatchers.ourIo) {
        observeCart(0, 4)
            .collect {
                cartTransactionType0_clientId4_state.emit(it?.filter { item ->
                    (stockState.payloadValue?.find { goodsItem -> goodsItem.id == item.id } != null).apply {
                        if (!this) deleteCartItemById(
                            item.id
                        )
                    }
                } ?: emptyList())
            }
    }

    GlobalScope.launch(Dispatchers.ourIo) {

        observeCart(1, 0)
            .collect {
                cartTransactionType1_clientId0_state.emit(it?.filter { item ->
                    (stockState.payloadValue?.find { goodsItem -> goodsItem.id == item.id } != null).apply {
                        if (!this) deleteCartItemById(
                            item.id
                        )
                    }
                } ?: emptyList())
            }
    }

    GlobalScope.launch(Dispatchers.ourIo) {
        observeCart(1, 1)
            .collect {
                cartTransactionType1_clientId1_state.emit(it?.filter { item ->
                    (stockState.payloadValue?.find { goodsItem -> goodsItem.id == item.id } != null).apply {
                        if (!this) deleteCartItemById(
                            item.id
                        )
                    }
                } ?: emptyList())
            }
    }

    GlobalScope.launch(Dispatchers.ourIo) {
        observeCart(1, 2)
            .collect {
                cartTransactionType1_clientId2_state.emit(it?.filter { item ->
                    (stockState.payloadValue?.find { goodsItem -> goodsItem.id == item.id } != null).apply {
                        if (!this) deleteCartItemById(
                            item.id
                        )
                    }
                } ?: emptyList())
            }
    }

    GlobalScope.launch(Dispatchers.ourIo) {
        observeCart(1, 3)
            .collect {
                cartTransactionType1_clientId3_state.emit(it?.filter { item ->
                    (stockState.payloadValue?.find { goodsItem -> goodsItem.id == item.id } != null).apply {
                        if (!this) deleteCartItemById(
                            item.id
                        )
                    }
                } ?: emptyList())
            }
    }

    GlobalScope.launch(Dispatchers.ourIo) {
        observeCart(1, 4)
            .collect {
                cartTransactionType1_clientId4_state.emit(it?.filter { item ->
                    (stockState.payloadValue?.find { goodsItem -> goodsItem.id == item.id } != null).apply {
                        if (!this) deleteCartItemById(
                            item.id
                        )
                    }
                } ?: emptyList())
            }
    }

    GlobalScope.launch(Dispatchers.ourIo) {
        observeCart(2, 0)
            .collect {
                cartTransactionType2_clientId0_state.emit(it?.filter { item ->
                    (stockState.payloadValue?.find { goodsItem -> goodsItem.id == item.id } != null).apply {
                        if (!this) deleteCartItemById(
                            item.id
                        )
                    }
                } ?: emptyList())
            }
    }

    GlobalScope.launch(Dispatchers.ourIo) {
        observeCart(2, 1)
            .collect {
                cartTransactionType2_clientId1_state.emit(it?.filter { item ->
                    (stockState.payloadValue?.find { goodsItem -> goodsItem.id == item.id } != null).apply {
                        if (!this) deleteCartItemById(
                            item.id
                        )
                    }
                } ?: emptyList())
            }
    }

    GlobalScope.launch(Dispatchers.ourIo) {
        observeCart(2, 2)
            .collect {
                cartTransactionType2_clientId2_state.emit(it?.filter { item ->
                    (stockState.payloadValue?.find { goodsItem -> goodsItem.id == item.id } != null).apply {
                        if (!this) deleteCartItemById(
                            item.id
                        )
                    }
                } ?: emptyList())
            }
    }

    GlobalScope.launch(Dispatchers.ourIo) {

        observeCart(2, 3)
            .collect {
                cartTransactionType2_clientId3_state.emit(it?.filter { item ->
                    (stockState.payloadValue?.find { goodsItem -> goodsItem.id == item.id } != null).apply {
                        if (!this) deleteCartItemById(
                            item.id
                        )
                    }
                } ?: emptyList())
            }
    }

    GlobalScope.launch(Dispatchers.ourIo) {
        observeCart(2, 4)
            .collect {
                cartTransactionType2_clientId4_state.emit(it?.filter { item ->
                    (stockState.payloadValue?.find { goodsItem -> goodsItem.id == item.id } != null).apply {
                        if (!this) deleteCartItemById(
                            item.id
                        )
                    }
                } ?: emptyList())
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

                if (!response.negative) {
                    globalAppConfigurationState.emit(DataState.Success(response.payload!!, response.message))

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

                if (response.negative) {
                    drawablesState.emit(DataState.Empty(response.message))
                } else {
                    drawablesState.emit(DataState.Success(response.payload!!, response.message))
                }
            }
        }
}

fun setAppLocale(language: String) {
    GlobalScope.launch(Dispatchers.ourIo) {
        appLanguageState.emit(language)
        putLocalKv(KEY_APP_LOCALE, language)
    }
}

fun setAppTheme(themeId: Long) {
    GlobalScope.launch(Dispatchers.ourIo) {
        // Emit immediately so UI changes now; local storage observer will keep it persistent.
        appThemeIdState.emit(themeId)
        putLocalKv(KEY_APP_THEME, themeId.toString())
    }
}

fun setAppSizeMode(sizeModeId: Long) {
    GlobalScope.launch {
        putLocalKv(KEY_APP_SIZE_MODE, sizeModeId.toString())
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
        drawablePathAITALogoState.emit(
            drawables.extractPath(0, appThemeIdState.value) ?: resourceDrawables.extractPath(0, appThemeIdState.value)!!
        )
        drawablePathIconPasswordState.emit(
            drawables.extractPath(1, appThemeIdState.value) ?: resourceDrawables.extractPath(1, appThemeIdState.value)!!
        )
        drawablePathIconCancelState.emit(
            drawables.extractPath(2, appThemeIdState.value) ?: resourceDrawables.extractPath(2, appThemeIdState.value)!!
        )
        drawablePathIconEyeHideState.emit(
            drawables.extractPath(3, appThemeIdState.value) ?: resourceDrawables.extractPath(3, appThemeIdState.value)!!
        )
        drawablePathIconEyeShowState.emit(
            drawables.extractPath(4, appThemeIdState.value) ?: resourceDrawables.extractPath(4, appThemeIdState.value)!!
        )
        drawablePathIconEmailState.emit(
            drawables.extractPath(5, appThemeIdState.value) ?: resourceDrawables.extractPath(
                5,
                appThemeIdState.value
            )!!
        )
        drawablePathIconPhoneState.emit(
            drawables.extractPath(6, appThemeIdState.value) ?: resourceDrawables.extractPath(
                6,
                appThemeIdState.value
            )!!
        )
        drawablePathIconExpandMoreState.emit(
            drawables.extractPath(7, appThemeIdState.value) ?: resourceDrawables.extractPath(7, appThemeIdState.value)!!
        )
        drawablePathIconExpandLessState.emit(
            drawables.extractPath(8, appThemeIdState.value) ?: resourceDrawables.extractPath(8, appThemeIdState.value)!!
        )
        drawablePathIconPersonState.emit(
            drawables.extractPath(9, appThemeIdState.value) ?: resourceDrawables.extractPath(9, appThemeIdState.value)!!
        )
        drawablePathIconTransactionSaleState.emit(
            drawables.extractPath(10, appThemeIdState.value) ?: resourceDrawables.extractPath(10, appThemeIdState.value)!!
        )
        drawablePathIconTransactionReturnState.emit(
            drawables.extractPath(11, appThemeIdState.value) ?: resourceDrawables.extractPath(11, appThemeIdState.value)!!
        )
        drawablePathIconTransactionSupplyState.emit(
            drawables.extractPath(12, appThemeIdState.value) ?: resourceDrawables.extractPath(12, appThemeIdState.value)!!
        )
        drawablePathIconStockState.emit(
            drawables.extractPath(13, appThemeIdState.value) ?: resourceDrawables.extractPath(13, appThemeIdState.value)!!
        )
        drawablePathIconMenuState.emit(
            drawables.extractPath(14, appThemeIdState.value) ?: resourceDrawables.extractPath(
                14,
                appThemeIdState.value
            )!!
        )
        drawablePathIconBackArrowState.emit(
            drawables.extractPath(15, appThemeIdState.value) ?: resourceDrawables.extractPath(15, appThemeIdState.value)!!
        )
        drawablePathIconAddState.emit(
            drawables.extractPath(16, appThemeIdState.value) ?: resourceDrawables.extractPath(
                16,
                appThemeIdState.value
            )!!
        )
        drawablePathIconUserAccountState.emit(
            drawables.extractPath(17, appThemeIdState.value) ?: resourceDrawables.extractPath(17, appThemeIdState.value)!!
        )
        drawablePathIconGoodsCategoriesState.emit(
            drawables.extractPath(18, appThemeIdState.value) ?: resourceDrawables.extractPath(18, appThemeIdState.value)!!
        )
        drawablePathIconStoresState.emit(
            drawables.extractPath(19, appThemeIdState.value) ?: resourceDrawables.extractPath(19, appThemeIdState.value)!!
        )
        drawablePathIconTransactionHistoryState.emit(
            drawables.extractPath(20, appThemeIdState.value) ?: resourceDrawables.extractPath(20, appThemeIdState.value)!!
        )
        drawablePathIconAnalyticsState.emit(
            drawables.extractPath(21, appThemeIdState.value) ?: resourceDrawables.extractPath(21, appThemeIdState.value)!!
        )
        drawablePathIconWorkersState.emit(
            drawables.extractPath(22, appThemeIdState.value) ?: resourceDrawables.extractPath(22, appThemeIdState.value)!!
        )
        drawablePathIconSuppliersState.emit(
            drawables.extractPath(23, appThemeIdState.value) ?: resourceDrawables.extractPath(23, appThemeIdState.value)!!
        )
        drawablePathIconDebtorsState.emit(
            drawables.extractPath(24, appThemeIdState.value) ?: resourceDrawables.extractPath(24, appThemeIdState.value)!!
        )
        drawablePathIconDevicesState.emit(
            drawables.extractPath(25, appThemeIdState.value) ?: resourceDrawables.extractPath(25, appThemeIdState.value)!!
        )
        drawablePathIconAppLanguageState.emit(
            drawables.extractPath(26, appThemeIdState.value) ?: resourceDrawables.extractPath(26, appThemeIdState.value)!!
        )
        drawablePathIconAppThemeState.emit(
            drawables.extractPath(27, appThemeIdState.value) ?: resourceDrawables.extractPath(27, appThemeIdState.value)!!
        )
        drawablePathIconCheckState.emit(
            drawables.extractPath(28, appThemeIdState.value) ?: resourceDrawables.extractPath(28, appThemeIdState.value)!!
        )
        drawablePathIconEditState.emit(
            drawables.extractPath(29, appThemeIdState.value) ?: resourceDrawables.extractPath(
                29,
                appThemeIdState.value
            )!!
        )
        drawablePathIconSettingsState.emit(
            drawables.extractPath(30, appThemeIdState.value) ?: resourceDrawables.extractPath(30, appThemeIdState.value)!!
        )
        drawablePathIconSearchState.emit(
            drawables.extractPath(31, appThemeIdState.value) ?: resourceDrawables.extractPath(31, appThemeIdState.value)!!
        )
        drawablePathIconBarcodeCamScannerState.emit(
            drawables.extractPath(32, appThemeIdState.value) ?: resourceDrawables.extractPath(32, appThemeIdState.value)!!
        )
        drawablePathIconDeleteState.emit(
            drawables.extractPath(33, appThemeIdState.value) ?: resourceDrawables.extractPath(33, appThemeIdState.value)!!
        )
        drawablePathIconExitState.emit(
            drawables.extractPath(34, appThemeIdState.value) ?: resourceDrawables.extractPath(34, appThemeIdState.value)!!
        )
        drawablePathIconSwitchState.emit(
            drawables.extractPath(35, appThemeIdState.value) ?: resourceDrawables.extractPath(35, appThemeIdState.value)!!
        )
        drawablePathIconCartState.emit(
            drawables.extractPath(36, appThemeIdState.value) ?: resourceDrawables.extractPath(36, appThemeIdState.value)!!
        )
        drawablePathIconAddCartState.emit(
            drawables.extractPath(37, appThemeIdState.value) ?: resourceDrawables.extractPath(37, appThemeIdState.value)!!
        )
        drawablePathIconSubtractState.emit(
            drawables.extractPath(38, appThemeIdState.value) ?: resourceDrawables.extractPath(38, appThemeIdState.value)!!
        )
        drawablePathIconReceiptState.emit(
            drawables.extractPath(39, appThemeIdState.value) ?: resourceDrawables.extractPath(39, appThemeIdState.value)!!
        )
        drawablePathIconFinancesState.emit(
            drawables.extractPath(40, appThemeIdState.value) ?: resourceDrawables.extractPath(40, appThemeIdState.value)!!
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
private const val CACHE_NOTIFICATIONS = "notifications"
private const val CACHE_SECURITY_SESSIONS = "security_sessions"
private const val CACHE_MY_WORKER_MEMBERSHIPS = "my_worker_memberships"
private const val CACHE_MY_WORKER_REQUESTS = "my_worker_requests"
private const val CACHE_USER_FINANCE_DASHBOARD = "user_finance_dashboard"
private const val CACHE_SUBSCRIPTION_PLANS = "subscription_plans"

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
private var realtimeUpdatesJob: Job? = null
private var realtimeRefreshJob: Job? = null
private val realtimeRefreshMutex = Mutex()
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
}

private suspend fun loadCachedApplicationData() {
    getJsonCache<GlobalAppConfigurationDataModel>(CACHE_GLOBAL_CONFIG)?.let {
        globalAppConfigurationState.emit(DataState.Success(it, cacheMessage()))
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
    getJsonCache<List<NotificationDataModel>>(CACHE_NOTIFICATIONS)?.let {
        notificationsState.emit(DataState.Success(it, cacheMessage()))
    }
    getJsonCache<List<SecuritySessionDataModel>>(CACHE_SECURITY_SESSIONS)?.let {
        securitySessionsState.emit(DataState.Success(it, cacheMessage()))
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
    getJsonCache<List<StoreSubscriptionPlanDataModel>>(CACHE_SUBSCRIPTION_PLANS)?.let {
        subscriptionPlansState.emit(DataState.Success(it, cacheMessage()))
    }

    getLocalKv(KEY_ACTIVE_STORE_ID)?.takeIf { it.isNotBlank() }?.let { storeId ->
        activeStoreIdState.emit(storeId)
        loadCachedStoreScopedData(storeId)
    }
}

private fun startAppCacheCollectors() {
    if (appCacheCollectorsStarted) return
    appCacheCollectorsStarted = true

    GlobalScope.launch(Dispatchers.ourIo) { globalAppConfigurationState.payload.collect { putJsonCache(CACHE_GLOBAL_CONFIG, it) } }
    GlobalScope.launch(Dispatchers.ourIo) { stringsState.payload.collect { it?.let { putJsonCache(CACHE_STRINGS, it) } } }
    GlobalScope.launch(Dispatchers.ourIo) { dimensionsState.payload.collect { it?.let { putJsonCache(CACHE_DIMENSIONS, it) } } }
    GlobalScope.launch(Dispatchers.ourIo) { colorsState.payload.collect { it?.let { putJsonCache(CACHE_COLORS, it) } } }
    GlobalScope.launch(Dispatchers.ourIo) { drawablesState.payload.collect { it?.let { putJsonCache(CACHE_DRAWABLES, it) } } }
    GlobalScope.launch(Dispatchers.ourIo) { userAccountState.payload.collect { it?.let { putJsonCache(CACHE_USER, it) } } }
    GlobalScope.launch(Dispatchers.ourIo) { storesState.payload.collect { it?.let { putJsonCache(CACHE_STORES, it) } } }
    GlobalScope.launch(Dispatchers.ourIo) { suppliersState.payload.collect { it?.let { putJsonCache(CACHE_SUPPLIERS, it) } } }
    GlobalScope.launch(Dispatchers.ourIo) { genericGoodsCategoriesState.payload.collect { it?.let { putJsonCache(CACHE_GENERIC_GOODS_CATEGORIES, it) } } }
    GlobalScope.launch(Dispatchers.ourIo) { notificationsState.payload.collect { it?.let { putJsonCache(CACHE_NOTIFICATIONS, it) } } }
    GlobalScope.launch(Dispatchers.ourIo) { securitySessionsState.payload.collect { it?.let { putJsonCache(CACHE_SECURITY_SESSIONS, it) } } }
    GlobalScope.launch(Dispatchers.ourIo) { myWorkerMembershipsState.payload.collect { it?.let { putJsonCache(CACHE_MY_WORKER_MEMBERSHIPS, it) } } }
    GlobalScope.launch(Dispatchers.ourIo) { myWorkerRequestsState.payload.collect { it?.let { putJsonCache(CACHE_MY_WORKER_REQUESTS, it) } } }
    GlobalScope.launch(Dispatchers.ourIo) { userFinanceDashboardState.payload.collect { it?.let { putJsonCache(CACHE_USER_FINANCE_DASHBOARD, it) } } }
    GlobalScope.launch(Dispatchers.ourIo) { subscriptionPlansState.payload.collect { it?.let { putJsonCache(CACHE_SUBSCRIPTION_PLANS, it) } } }

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

private fun refreshEverythingFromServerAfterRealtimeUpdate() {
    getGlobalAppConfiguration(loadAll = false)
    getUser(forceLogOut = false)
    getStores()
    getSuppliers()
    getGenericGoodsCategories()
    getNotifications()
    getSecuritySessions()
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
        getStoreWorkers(storeId)
        getIncomingWorkerRequests(storeId)
        getStoreSubscription(storeId)
    }
}

private fun scheduleRealtimeRefresh(reason: String? = null) {
    realtimeRefreshJob?.cancel()
    realtimeRefreshJob = GlobalScope.launch(Dispatchers.ourIo) {
        delay(350)
        realtimeRefreshMutex.withLock {
            refreshEverythingFromServerAfterRealtimeUpdate()
        }
    }
}

fun stopRealtimeUpdates() {
    realtimeUpdatesJob?.cancel()
    realtimeUpdatesJob = null
    realtimeUpdatesConnectedState.value = false
}

fun startRealtimeUpdates() {
    if (realtimeUpdatesJob?.isActive == true) return

    realtimeUpdatesJob = GlobalScope.launch(Dispatchers.ourIo) {
        var reconnectDelayMillis = 1_000L

        while (isActive) {
            val accessToken = getStoredUserAuthTokens?.invoke()?.accessToken

            if (accessToken.isNullOrBlank()) {
                realtimeUpdatesConnectedState.emit(false)
                delay(2_000)
                continue
            }

            val realtimeUrl = globalAppConfigurationState.payloadValue.serverUrl.first.toRealtimeWebSocketUrl(
                globalAppConfigurationState.payloadValue.realtimeUpdatesPath
            )

            val wasConnected = realtimeUpdatesConnectedState.value

            try {
                val session = httpClient.webSocketSession {
                    url(realtimeUrl)
                    header(HttpHeaders.Authorization, "Bearer $accessToken")
                }

                try {
                    val becameConnected = !realtimeUpdatesConnectedState.value
                    realtimeUpdatesConnectedState.emit(true)
                    reconnectDelayMillis = 1_000L

                    if (becameConnected) {
                        postInAppNotification(realtimeConnectedMessage(), NotificationType.Positive, transient = true)
                        scheduleRealtimeRefresh("connected")
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
                            scheduleRealtimeRefresh(update.reason ?: update.entity)
                        }
                    }
                } finally {
                    try {
                        session.close()
                    } catch (_: Throwable) {
                    }
                }
            } catch (_: Throwable) {
                if (wasConnected || realtimeUpdatesConnectedState.value) {
                    postInAppNotification(realtimeDisconnectedMessage(), NotificationType.Neutral, transient = true)
                }
                realtimeUpdatesConnectedState.emit(false)
                delay(reconnectDelayMillis)
                reconnectDelayMillis = (reconnectDelayMillis * 2).coerceAtMost(30_000L)
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
    if (transactionTypeIndex == 2) {
        clearTransactionSupplySupplierId(transactionTypeIndex, clientId)
    }
}

fun deleteCartById(id: String, transactionTypeIndex: Int, clientId: Int) {
    GlobalScope.launch {
        appDatabase.app_databaseQueries.deleteCartById(id, transactionTypeIndex.toLong(), clientId.toLong())
        removeCartSaleMethodId(transactionTypeIndex, clientId, id)
    }
}

suspend fun deleteCartItemById(id: String) {
    appDatabase.app_databaseQueries.deleteById(id)
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
    val now = getCurrentTimeMillis()
    return NotificationDataModel(
        id = "${now}_${message.hashCode()}_${type.name}",
        userId = userAccountState.payloadValue?.id,
        storeId = activeStoreIdState.value,
        title = title,
        message = message,
        type = type,
        category = category,
        source = "app",
        metadata = emptyMap(),
        createdAtMillis = now,
        shownAtMillis = now,
        readAtMillis = null,
        isSavedOnServer = false
    )
}

private suspend fun appendNotificationLocally(notification: NotificationDataModel) {
    val old = notificationsState.payloadValue.orEmpty()
    notificationsState.emit(
        DataState.Success(
            (listOf(notification) + old)
                .distinctBy { it.id }
                .sortedByDescending { it.createdAtMillis }
                .take(500)
        )
    )
}

private fun pushInAppNotification(notification: NotificationDataModel, transient: Boolean) {
    GlobalScope.launch(Dispatchers.ourIo) {
        latestInAppNotificationState.emit(notification)
        activeInAppNotificationsState.emit(
            (listOf(notification) + activeInAppNotificationsState.value)
                .distinctBy { it.id }
                .take(25)
        )
        appendNotificationLocally(notification)
        saveNotificationToServer(notification)

        // Popup cards are temporary visual toasts; the notification itself stays in history.
        // The transient flag is kept for call-site compatibility, but popups always disappear.
        delay(5000)
        activeInAppNotificationsState.emit(activeInAppNotificationsState.value.filter { it.id != notification.id })
        if (latestInAppNotificationState.value?.id == notification.id) {
            latestInAppNotificationState.emit(activeInAppNotificationsState.value.firstOrNull())
        }
    }
}

fun postInAppNotification(
    message: List<LocalizedStringDataModel>?,
    type: NotificationType,
    transient: Boolean = true
) {
    message?.extractLocalizedString(appLanguageState.value)?.run {
        postInAppNotification(this, type, transient)
    }
}

fun postInAppNotification(message: String, type: NotificationType, transient: Boolean = true) {
    pushInAppNotification(createNotificationDataModel(message, type), transient)
}

fun clearInAppNotification() {
    GlobalScope.launch(Dispatchers.ourIo) {
        latestInAppNotificationState.emit(null)
        activeInAppNotificationsState.emit(emptyList())
    }
}

fun dismissInAppNotification(notificationId: String, markAsRead: Boolean = true) {
    GlobalScope.launch(Dispatchers.ourIo) {
        val remaining = activeInAppNotificationsState.value.filter { it.id != notificationId }
        activeInAppNotificationsState.emit(remaining)
        if (latestInAppNotificationState.value?.id == notificationId) {
            latestInAppNotificationState.emit(remaining.firstOrNull())
        }
    }

    if (markAsRead) {
        markNotificationRead(notificationId)
    }
}

fun getNotifications() {
    if (!getNotificationsMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            getNotificationsMutex.withLock {
                if (getStoredUserAuthTokens?.invoke() == null) return@withLock

                val response = networkRequest<List<NotificationDataModel>, Unit>(
                    method = HttpMethod.Get,
                    endpointUrl = "notifications/get"
                )

                if (!response.negative) {
                    notificationsState.emit(DataState.Success(response.payload.orEmpty(), response.message))
                }
            }
        }
}

fun saveNotificationToServer(notification: NotificationDataModel) {
    if (notification.message.isBlank()) return
    if (getStoredUserAuthTokens?.invoke() == null) return
    if (userAccountState.payloadValue == null) return

    GlobalScope.launch(Dispatchers.ourIo) {
        if (!saveNotificationMutex.isLocked) {
            saveNotificationMutex.withLock {
                val response = networkRequest<NotificationDataModel, NotificationDataModel>(
                    method = HttpMethod.Post,
                    endpointUrl = "notifications/add",
                    body = notification
                )

                if (!response.negative && response.payload != null) {
                    val saved = response.payload
                    val old = notificationsState.payloadValue.orEmpty()
                    notificationsState.emit(
                        DataState.Success(
                            (listOf(saved) + old)
                                .distinctBy { it.id }
                                .sortedByDescending { it.createdAtMillis }
                                .take(500)
                        )
                    )
                }
            }
        }
    }
}

fun markNotificationRead(notificationId: String) {
    if (!markNotificationReadMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            markNotificationReadMutex.withLock {
                val local = notificationsState.payloadValue.orEmpty()
                val now = getCurrentTimeMillis()
                notificationsState.emit(
                    DataState.Success(local.map { if (it.id == notificationId) it.copy(readAtMillis = now) else it })
                )

                val response = networkRequest<List<NotificationDataModel>, List<String>>(
                    method = HttpMethod.Put,
                    endpointUrl = "notifications/read",
                    body = listOf(notificationId)
                )

                if (!response.negative && response.payload != null) {
                    notificationsState.emit(DataState.Success(response.payload))
                }
            }
        }
}

fun markAllNotificationsRead() {
    GlobalScope.launch(Dispatchers.ourIo) {
        val ids = notificationsState.payloadValue.orEmpty()
            .filter { it.readAtMillis == null }
            .map { it.id }

        if (ids.isEmpty()) return@launch

        val now = getCurrentTimeMillis()
        notificationsState.emit(
            DataState.Success(
                notificationsState.payloadValue.orEmpty().map { if (it.id in ids) it.copy(readAtMillis = now) else it }
            )
        )

        val response = networkRequest<List<NotificationDataModel>, List<String>>(
            method = HttpMethod.Put,
            endpointUrl = "notifications/read",
            body = ids
        )

        if (!response.negative && response.payload != null) {
            notificationsState.emit(DataState.Success(response.payload))
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

fun logInUser(userAuthLogIn: UserAuthLogInDataModel) {
    if (!logInMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            logInMutex.withLock {
                logInInProgressState.emit(true)
                try {
                    postInAppNotification(stringLoggingInState.value, NotificationType.Neutral, transient = true)

                    // Avoid stale sessions and make repeated login attempts deterministic.
                    setStoredUserAuthTokens?.invoke(null)
                    httpClient.authProvider<BearerAuthProvider>()?.clearToken()

                    val logInRequest = userAuthLogIn.copy(deviceInfo = buildCurrentClientDeviceInfo())

                    val response = networkRequest<TokenPair, UserAuthLogInDataModel>(
                        HttpMethod.Post,
                        endpointUrl = globalAppConfigurationState.payloadValue.logInPath.first,
                        body = logInRequest
                    )

                    if (response.negative || response.payload == null) {
                        postInAppNotification(
                            response.message ?: localizedStringResourceMessage(
                                id = 222,
                                main = "Login failed: empty token response",
                                ru = "Не удалось войти: сервер не вернул токены",
                                kk = "Кіру орындалмады: сервер токендерді қайтармады"
                            ),
                            NotificationType.Negative,
                            transient = true
                        )
                    } else {
                        setStoredUserAuthTokens?.invoke(response.payload)
                        httpClient.authProvider<BearerAuthProvider>()?.clearToken()
                        getUser(forceLogOut = false)
                    }
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

fun signUpUser(userAuthSignUp: UserAuthSignUpDataModel) {
    if (!signUpUserMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            signUpInProgressState.emit(true)

            try {
                signUpUserMutex.withLock {
                    postInAppNotification(stringSigningUpState.value, NotificationType.Neutral, transient = true)

                    val signUpRequest = userAuthSignUp.copy(deviceInfo = buildCurrentClientDeviceInfo())

                    val response = networkRequest<TokenPair, UserAuthSignUpDataModel>(
                        HttpMethod.Post,
                        endpointUrl = globalAppConfigurationState.payloadValue.signUpPath.first,
                        body = signUpRequest
                    )

                    if (response.negative) {
                        postInAppNotification(response.message, NotificationType.Negative)
                    } else {
                        setStoredUserAuthTokens?.invoke(response.payload)
                        httpClient.authProvider<BearerAuthProvider>()?.clearToken()

                        getUser(forceLogOut = false)
                    }
                }
            } finally {
                signUpInProgressState.emit(false)
            }
        }
}

fun logOutUser() {
    if (!logOutUserMutex.isLocked)
        GlobalScope.launch(Dispatchers.ourIo) {
            logOutUserMutex.withLock {
                val refreshToken = getStoredUserAuthTokens?.invoke()?.refreshToken

                val response = if (!refreshToken.isNullOrBlank()) {
                    networkRequest<Unit, String>(
                        HttpMethod.Delete,
                        endpointUrl = globalAppConfigurationState.payloadValue.logOutPath.first,
                        body = refreshToken
                    )
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

                // Logout must never trap the cashier inside account screen. Server revoke is best-effort.
                stopRealtimeUpdates()
                setStoredUserAuthTokens?.invoke(null)
                setStoredUserAccountDataModel?.invoke(null)
                setActiveStoreId(null, syncServer = false)
                httpClient.authProvider<BearerAuthProvider>()?.clearToken()

                userAccountState.emit(DataState.Empty())
                storesState.emit(DataState.Empty())
                securitySessionsState.emit(DataState.Empty())
                activeStoreIdState.emit(null)

                postInAppNotification(
                    if (response.negative)
                        localizedStringResourceMessage(
                            id = 221,
                            main = "Logged out locally; server session cleanup failed",
                            ru = "Выход выполнен локально; серверный сеанс не удалось завершить",
                            kk = "Жергілікті түрде шығу орындалды; сервердегі сеансты тоқтату мүмкін болмады"
                        )
                    else response.message,
                    if (response.negative) NotificationType.Neutral else NotificationType.Positive,
                    transient = true
                )
            }
        }
}

fun getUser(forceLogOut: Boolean = true) {
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
                            postInAppNotification(
                                localizedStringResourceMessage(
                                    id = 214,
                                    main = "Cannot reach server. Keeping you signed in offline.",
                                    ru = "Сервер недоступен. Вы остаётесь в аккаунте офлайн.",
                                    kk = "Сервер қолжетімсіз. Сіз офлайн режимде аккаунтта қаласыз."
                                ),
                                NotificationType.Neutral
                            )
                        }
                        response.httpStatusCode == HttpStatusCode.Unauthorized.value && forceLogOut -> {
                            forceLogOutUser()
                            postInAppNotification(response.message, NotificationType.Negative)
                        }
                        else -> postInAppNotification(response.message, NotificationType.Negative)
                    }
                } else {
                    clearInAppNotification()
                    setStoredUserAccountDataModel?.invoke(response.payload)
                    userAccountState.emit(DataState.Success(response.payload!!, response.message))

                    response.payload.activeStoreId?.takeIf { it.isNotBlank() }?.let { savedStoreId ->
                        putLocalKv(KEY_ACTIVE_STORE_ID, savedStoreId)
                        activeStoreIdState.emit(savedStoreId)
                    }

                    getGlobalAppConfiguration()
                    getNotifications()
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

fun forceLogOutUser() {
    GlobalScope.launch(Dispatchers.ourIo) {
        stopRealtimeUpdates()
        setStoredUserAuthTokens?.invoke(null)
        setStoredUserAccountDataModel?.invoke(null)
        setActiveStoreId(null, syncServer = false)

        userAccountState.emit(DataState.Empty())
        securitySessionsState.emit(DataState.Empty())
    }
}

suspend inline fun <reified Response, reified Body> networkRequest(
    method: HttpMethod,
    serverUrl: String = globalAppConfigurationState.payloadValue.serverUrl.first,
    endpointUrl: String,
    query: Map<String, Any?> = emptyMap(),
    headers: Map<String, String> = emptyMap(),
    body: Body? = null,
    contentType: ContentType? = ContentType.Application.Json
): ResponseDataModel<Response> {
    return try {
        val response = httpClient
            .request("$serverUrl/$endpointUrl") {
                this.method = method

                headers.forEach { (key, value) ->
                    this.headers.append(key, value)
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

        if (response.status == HttpStatusCode.Unauthorized) {
            ResponseDataModel(
                message = stringRawAuthenticationFailedState.value,
                payload = null,
                negative = true,
                httpStatusCode = response.status.value
            )
        } else {
            val rawBody = response.bodyAsText()

            try {
                jsonBase.decodeFromString<GenericResponseDataModel>(rawBody).toResponseDataModel<Response>()
            } catch (_: Throwable) {
                try {
                    jsonBase.decodeFromString<ResponseDataModel<Response>>(rawBody)
                } catch (decodeThrowable: Throwable) {
                    val rawPreview = rawBody
                        .replace("\n", " ")
                        .replace("\r", " ")
                        .take(1200)
                        .ifBlank { "<empty response body>" }

                    ResponseDataModel(
                        message = listOf(
                            LocalizedStringDataModel(
                                language = "main",
                                value = "Server returned an unreadable response: HTTP ${response.status.value} ${response.status.description}: $rawPreview"
                            ),
                            LocalizedStringDataModel(
                                language = "en",
                                value = "Server returned an unreadable response: HTTP ${response.status.value} ${response.status.description}: $rawPreview"
                            ),
                            LocalizedStringDataModel(
                                language = "ru",
                                value = "Сервер вернул нечитаемый ответ: HTTP ${response.status.value} ${response.status.description}: $rawPreview"
                            ),
                            LocalizedStringDataModel(
                                language = "kk",
                                value = "Сервер оқылмайтын жауап қайтарды: HTTP ${response.status.value} ${response.status.description}: $rawPreview"
                            )
                        ),
                        payload = null,
                        negative = true,
                        httpStatusCode = response.status.value
                    )
                }
            }
        }
    } catch (throwable: Throwable) {
        ResponseDataModel(
            message = localizedStringResourceMessage(
                id = 223,
                main = "Cannot reach server",
                ru = "Сервер недоступен",
                kk = "Сервер қолжетімсіз"
            ),
            payload = null,
            negative = true,
            transportFailure = true
        )
    }
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
                            if (settableStores.size == 1) setActiveStoreId(settableStores.first().id)
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
        putLocalKv(KEY_ACTIVE_STORE_ID, id)
        activeStoreIdState.emit(id)

        if (syncServer && !id.isNullOrBlank() && getStoredUserAuthTokens?.invoke() != null) {
            val response = networkRequest<Unit, String>(
                method = HttpMethod.Put,
                endpointUrl = "stores/active",
                body = id
            )

            if (response.negative)
                postInAppNotification(response.message, NotificationType.Negative, transient = true)
            else {
                (userAccountState.payloadValue)?.let { account ->
                    val updated = account.copy(activeStoreId = id)
                    userAccountState.emit(DataState.Success(updated))
                    setStoredUserAccountDataModel?.invoke(updated)
                }
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

fun getGenericGoodsItems(barcode: String): Flow<DataState<List<GenericGoodsItemDataModel>>> {
    return flow {
        getGenericGoodsItemsMutex.withLock {
            val response = networkRequest<List<GenericGoodsItemDataModel>, String>(
                method = HttpMethod.Get,
                endpointUrl = globalAppConfigurationState.payloadValue.getGenericGoodsItemsPath.first,
                headers = mapOf("barcode" to barcode)
            )

            if (!response.negative) {
                emit(DataState.Success(response.payload!!, response.message))
            }
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

                                stockState.payloadValue?.indexOfFirst { it.id == response.payload!!.id }?.let { index ->
                                    newList[index] = response.payload!!
                                } ?: newList.add(response.payload!!)
                            }
                        )
                    )

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
                    body = request,
                    headers = mapOf("store_id" to request.sourceStoreId)
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

const val WORKER_ROLE_OWNER = "owner"
const val WORKER_ROLE_ADMIN = "admin"
const val WORKER_ROLE_STANDARD = "standard"

const val STORE_PERMISSION_SALE_TRANSACTION = "sale_transaction"
const val STORE_PERMISSION_RETURN_TRANSACTION = "return_transaction"
const val STORE_PERMISSION_SUPPLY_TRANSACTION = "supply_transaction"
const val STORE_PERMISSION_STOCK_READ = "stock_read"
const val STORE_PERMISSION_STOCK_WRITE = "stock_write"
const val STORE_PERMISSION_TRANSACTION_HISTORY_VIEW = "transaction_history_view"
const val STORE_PERMISSION_ANALYTICS_VIEW = "analytics_view"
const val STORE_PERMISSION_CASH_REGISTER_VIEW = "cash_register_view"
const val STORE_PERMISSION_CASH_REGISTER_EXTRACT = "cash_register_extract"
const val STORE_PERMISSION_WORKERS_VIEW = "workers_view"
const val STORE_PERMISSION_WORKERS_MANAGE = "workers_manage"
const val STORE_PERMISSION_STORE_MANAGE = "store_manage"

val ALL_STORE_PERMISSION_IDS = listOf(
    STORE_PERMISSION_SALE_TRANSACTION,
    STORE_PERMISSION_RETURN_TRANSACTION,
    STORE_PERMISSION_SUPPLY_TRANSACTION,
    STORE_PERMISSION_STOCK_READ,
    STORE_PERMISSION_STOCK_WRITE,
    STORE_PERMISSION_TRANSACTION_HISTORY_VIEW,
    STORE_PERMISSION_ANALYTICS_VIEW,
    STORE_PERMISSION_CASH_REGISTER_VIEW,
    STORE_PERMISSION_CASH_REGISTER_EXTRACT,
    STORE_PERMISSION_WORKERS_VIEW,
    STORE_PERMISSION_WORKERS_MANAGE,
    STORE_PERMISSION_STORE_MANAGE
)

val STANDARD_STORE_PERMISSION_IDS = listOf(
    STORE_PERMISSION_SALE_TRANSACTION,
    STORE_PERMISSION_RETURN_TRANSACTION,
    STORE_PERMISSION_STOCK_READ,
    STORE_PERMISSION_TRANSACTION_HISTORY_VIEW,
    STORE_PERMISSION_CASH_REGISTER_VIEW
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
    val note: String? = null
)

@kotlinx.serialization.Serializable
data class StockBatchMoveResultDataModel(
    val sourceBatch: GoodsBatchDataModel,
    val destinationBatch: GoodsBatchDataModel,
    val sourceGoodsItem: GoodsItemDataModel,
    val destinationGoodsItem: GoodsItemDataModel,
    val movement: StockBatchMovementDataModel,
    val availability: StockItemBranchAvailabilityDataModel
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
    onCompleted: ((DataState<GoodsItemDataModel>) -> Unit)? = null
) {
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
            "${firstName.trim()} ${lastName.trim()}".trim().ifBlank { phoneNumber.ifBlank { id } }
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
        return message?.let { jsonBase.decodeFromString(it) }
    }

    inline fun <reified T> getPayload(): T? {
        return payload?.let { jsonBase.decodeFromString(it) }
    }

    inline fun <reified T> toResponseDataModel(): ResponseDataModel<T> {
        return ResponseDataModel(
            message?.let { jsonBase.decodeFromString(it) },
            payload?.let { jsonBase.decodeFromString(it) },
            negative
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
    val getSecuritySessionsPath: Pair<String, String> = Pair("security/sessions/get", "44"),
    val revokeSecuritySessionPath: Pair<String, String> = Pair("security/sessions/revoke", "45"),
    val revokeOtherSecuritySessionsPath: Pair<String, String> = Pair("security/sessions/revokeOthers", "46"),
    val getUserPath: Pair<String, String>,
    val updateUserPath: Pair<String, String>,
    val getStoresPath: Pair<String, String>,
    val addStoresPath: Pair<String, String>,
    val updateStoresPath: Pair<String, String>,
    val deleteStoresPath: Pair<String, String>,
    val getStockPath: Pair<String, String>,
    val addGoodsItemPath: Pair<String, String>,
    val updateGoodsItemPath: Pair<String, String>,
    val deleteGoodsItemPath: Pair<String, String>,
    val getStockBatchesPath: Pair<String, String>,
    val addStockBatchPath: Pair<String, String>,
    val updateStockBatchPath: Pair<String, String>,
    val deleteStockBatchPath: Pair<String, String>,
    val getStockItemBranchAvailabilityPath: Pair<String, String> = Pair("stockBatches/branchAvailability", "73"),
    val moveStockBatchPath: Pair<String, String> = Pair("stockBatches/move", "74"),

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
    val requestStoreEmploymentPath: Pair<String, String> = Pair("workers/request", "55"),
    val acceptStoreEmploymentPath: Pair<String, String> = Pair("workers/accept", "56"),
    val declineStoreEmploymentPath: Pair<String, String> = Pair("workers/decline", "57"),
    val updateStoreWorkerPermissionsPath: Pair<String, String> = Pair("workers/updatePermissions", "58"),
    val inviteStoreWorkerPath: Pair<String, String> = Pair("workers/invite", "65"),
    val acceptStoreWorkerInvitationPath: Pair<String, String> = Pair("workers/invitations/accept", "66"),
    val declineStoreWorkerInvitationPath: Pair<String, String> = Pair("workers/invitations/decline", "67"),
    val getCurrentWorkshiftPath: Pair<String, String> = Pair("workshifts/current", "86"),
    val startWorkshiftPath: Pair<String, String> = Pair("workshifts/start", "87"),
    val endWorkshiftPath: Pair<String, String> = Pair("workshifts/end", "88"),
    val getUserFinanceDashboardPath: Pair<String, String> = Pair("finance/dashboard", "77"),
    val createTopUpPaymentPath: Pair<String, String> = Pair("finance/topup/create", "78"),
    val confirmDevelopmentTopUpPath: Pair<String, String> = Pair("finance/topup/confirmDevelopment", "79"),
    val getSubscriptionPlansPath: Pair<String, String> = Pair("subscriptions/plans", "80"),
    val getStoreSubscriptionPath: Pair<String, String> = Pair("subscriptions/store/get", "81"),
    val updateStoreSubscriptionPath: Pair<String, String> = Pair("subscriptions/store/update", "82"),
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

    val shelfPosition: String? = null,
    val shelfPriority: Int = 0,

    val status: StockBatchStatusDataModel = StockBatchStatusDataModel.Delivered,

    val additionalNotes: String? = null,

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

    val note: String? = null,
    val noteLocalized: List<LocalizedStringDataModel> = emptyList(),
    val conditions: List<String> = emptyList(),

    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L,
    val isActive: Boolean = true
): Searchable {

    override val exactSearchOperands: List<String>
        get() = mutableListOf<String>().apply {
            addAll(barcodes)
            addAll(barcodes.map { it.toStoredGoodsItemBarcode() })
            addAll(name.map { it.value })
            addAll(salePrices.map { it.price })
            addAll(returnPrices.map { it.price })
            addAll(supplyPrices.map { it.price })
            addAll(wholesalePrices.map { it.price })
            addAll(noteLocalized.map { it.value })
            addAll(conditions)
            note?.let { add(it) }
            addAll(salePrices.map { it.currency })
            addAll(returnPrices.map { it.currency })
            addAll(supplyPrices.map { it.currency })
            addAll(wholesalePrices.map { it.currency })
        }
    override val containsSearchOperands: List<String>
        get() = mutableListOf<String>().apply {
            addAll(barcodes)
            addAll(barcodes.map { it.toStoredGoodsItemBarcode() })
            addAll(name.map { it.value })
            addAll(salePrices.map { it.price })
            addAll(returnPrices.map { it.price })
            addAll(supplyPrices.map { it.price })
            addAll(wholesalePrices.map { it.price })
            addAll(noteLocalized.map { it.value })
            addAll(conditions)
            note?.let { add(it) }
            addAll(salePrices.map { it.currency })
            addAll(returnPrices.map { it.currency })
            addAll(supplyPrices.map { it.currency })
            addAll(wholesalePrices.map { it.currency })
        }
    override val uniqueSearchOperands: List<String>
        get() = mutableListOf<String>().apply {
            addAll(barcodes)
            addAll(barcodes.map { it.toStoredGoodsItemBarcode() })
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
    val supplierIdText: String? = null
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

@kotlinx.serialization.Serializable
data class TokenPair(
    val accessToken: String,
    val accessExpiryTime: Long,
    val refreshToken: String,
    val refreshExpiryTime: Long
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
    val timeMillis: Long
)


fun UserAccountDataModel.visibleWorkerInviteId(): String = publicId.ifBlank { id }

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
    val activeStoreId: String? = null,
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
        get() = "${firstName.trim()} ${lastName.trim()}".trim().ifBlank { phoneNumber.ifBlank { email.ifBlank { userId } } }
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
    val status: String = "pending",
    val requestedAtMillis: Long = 0L,
    val decidedAtMillis: Long? = null,
    val decidedByUserId: String? = null,
    val roleId: String = WORKER_ROLE_STANDARD,
    val permissions: List<String> = STANDARD_STORE_PERMISSION_IDS,
    val note: String? = null
) {
    val displayName: String
        get() = "${firstName.trim()} ${lastName.trim()}".trim().ifBlank { phoneNumber.ifBlank { email.ifBlank { requesterPublicId.ifBlank { requesterUserId } } } }
}

@kotlinx.serialization.Serializable
data class WorkerEmploymentRequestCreateDataModel(
    val storeId: String
)

@kotlinx.serialization.Serializable
data class WorkerEmploymentDecisionRequestDataModel(
    val requestId: String,
    val roleId: String = WORKER_ROLE_STANDARD,
    val permissions: List<String> = STANDARD_STORE_PERMISSION_IDS,
    val note: String? = null,
    val workerPassword: String? = null
)

@kotlinx.serialization.Serializable
data class WorkerStoreInviteCreateDataModel(
    val userId: String,
    val roleId: String = WORKER_ROLE_STANDARD,
    val permissions: List<String> = STANDARD_STORE_PERMISSION_IDS,
    val note: String? = null,
    val workerPassword: String? = null
)

@kotlinx.serialization.Serializable
data class WorkerStoreInvitationDecisionDataModel(
    val requestId: String,
    val note: String? = null
)

@kotlinx.serialization.Serializable
data class WorkerPermissionsUpdateRequestDataModel(
    val workerId: String,
    val roleId: String,
    val permissions: List<String>,
    val workerPassword: String? = null
)

@kotlinx.serialization.Serializable
data class WorkshiftStartRequestDataModel(
    val workerIdentifier: String,
    val password: String
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