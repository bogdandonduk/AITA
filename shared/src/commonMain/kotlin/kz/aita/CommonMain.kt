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
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

private val completeTransactionMutex = Mutex()
private val getTransactionsMutex = Mutex()

data class TransactionPaymentDraftDataModel(
  val transactionTypeIndex: Int,
  val clientId: Int,
  val paymentModeId: String,
  val paidCash: Double,
  val paidCard: Double,
  val cardPaymentOptionId: Int,
)

data class TransactionReceiptSnapshotDataModel(
  val transaction: TransactionDataModel,
  val store: StoreDataModel?,
  val lines: List<TransactionReceiptLineDataModel>,
  val paymentDraft: TransactionPaymentDraftDataModel,
  val currencyCode: String,
  val currencySymbol: String,
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
) {
  val total: Double
    get() = quantity.total * pricePerUnit
}

val latestTransactionReceiptSnapshotState =
  MutableStateFlow<TransactionReceiptSnapshotDataModel?>(null)

private fun transactionKey(transactionTypeIndex: Int, clientId: Int): String {
  return "$transactionTypeIndex:$clientId"
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

fun GoodsItemDataModel.priceForTransaction(transactionTypeIndex: Int): PriceDataModel {
  return when (transactionTypeIndex) {
    0 -> salePrices.firstOrNull()
    1 -> returnPrices.firstOrNull()
    else -> supplyPrices.firstOrNull()
  } ?: PriceDataModel(
    price = "0",
    currency = salePrices.firstOrNull()?.currency
      ?: returnPrices.firstOrNull()?.currency
      ?: supplyPrices.firstOrNull()?.currency
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

fun GoodsItemDataModel.firstBarcode(): String {
  return barcodes.firstOrNull().orEmpty()
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
  val nextTotal = current.total + current.pricedAmount * deltaSteps

  if (nextTotal <= 0.0) {
    deleteCartById(id, transactionTypeIndex, clientId)
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
  currentCart: List<GoodsItemInCartDataModel>
) {
  val existing = currentCart.find { it.id == goodsItem.id }

  if (existing == null) {
    upsertCart(
      id = goodsItem.id,
      transactionTypeIndex = transactionTypeIndex,
      clientId = clientId,
      quantity = goodsItem.defaultCartQuantity(configuration)
    )
  } else {
    changeCartQuantity(
      id = goodsItem.id,
      transactionTypeIndex = transactionTypeIndex,
      clientId = clientId,
      current = existing.quantity,
      deltaSteps = 1
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
        postInAppNotification("Completing transaction", NotificationType.Neutral, transient = false)

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

        deleteCart(transactionTypeIndex, clientId)
        clearTransactionPaymentDraft(transactionTypeIndex, clientId)

        activeStoreIdState.value?.let {
          getStock(it)
          getStockBatches(it)
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
    serverUrl = Pair("http://192.168.0.103:8080", "1"),
    globalAppConfigurationPath = Pair("config/global", "2"),
    logInPath = Pair("auth/logIn", "3"),
    signUpPath = Pair("auth/signUp", "4"),
    refreshPath = Pair("auth/refresh", "5"),
    logOutPath = Pair("auth/logOut", "6"),
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
    getGenericGoodsItemsPath = Pair("generic/goodsItems/get", "21"),
    getGenericGoodsCategoriesPath = Pair("generic/goodsCategories/get", "22"),
    getSuppliersPath = Pair("suppliers/get", "23"),
    stringResourcesPath = Pair("res/string", "24"),
    dimensionResourcesPath = Pair("res/dimension", "25"),
    colorResourcesPath = Pair("res/color", "26"),
    drawableResourcesConfigurationPath = Pair("res/drawableConfig", "27"),
    drawableResourcesPath = Pair("res/drawable", "28"),
    getTransactionsPath = Pair("transactions/get", "29"),
    completeTransactionPath = Pair("transactions/complete", "30"),
    getSupplierGoodsPricesPath = Pair("supplierGoodsPrices/get", "31"),
    upsertSupplierGoodsPricePath = Pair("supplierGoodsPrices/upsert", "32"),
    deleteSupplierGoodsPricesPath = Pair("supplierGoodsPrices/delete", "33"),
    getSupplierOrdersPath = Pair("supplierOrders/get", "34"),
    addSupplierOrderPath = Pair("supplierOrders/add", "35"),
    updateSupplierOrderPath = Pair("supplierOrders/update", "36"),
    deleteSupplierOrdersPath = Pair("supplierOrders/delete", "37"),
    receiveSupplierOrderPath = Pair("supplierOrders/receive", "38"),
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

              if (response.negative) {
                postInAppNotification(
                  stringSessionTimeExpiredLoggingOutState.value,
                  NotificationType.Negative
                )
                delay(3000)
                forceLogOutUser()
              }

              httpClient.close()

              if (response.payload != null) {
                setStoredUserAuthTokens?.invoke(response.payload)
                BearerTokens(response.payload.accessToken, response.payload.refreshToken)
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

val getStockMutex = Mutex()
val addGoodsItemMutex = Mutex()
val updateGoodsItemMutex = Mutex()
val deleteGoodsItemMutex = Mutex()

val logInMutex = Mutex()
val signUpUserMutex = Mutex()

val logOutUserMutex = Mutex()
val getUserAccountMutex = Mutex()
val updateUserMutex = Mutex()
val latestInAppNotificationState = MutableStateFlow<NotificationDataModel?>(null)

val cashRegisterExtractionsState =
  MutableDataStateFlow<List<CashRegisterExtractionEntryDataModel>>(GlobalScope)

val cashRegisterAmountState = MutableStateFlow(0.0)


























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

fun getStoreWorkers() {
  TODO("Not yet implemented")
}

fun addStoreWorker(
  phoneNumber: String,
  email: String,
  firstName: String,
  lastName: String,
  password: String
) {
  TODO("Not yet implemented")
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
    ?.run {
      find { it.id == id }
        ?.values
        ?.find {
          (language == "system" && it.language == getSystemLocaleLanguage()) || it.language == language
        }?.value
    }
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
  return find { language == "system" && it.language == getSystemLocaleLanguage() || it.language == language || it.language == "main" }?.value
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
          getStock(it)
          getTransactions(it)
        }
      }
  }

  GlobalScope.launch(Dispatchers.ourIo) {
    storesState.payload.collect {
      it?.let {
        if (it.size == 1 && activeStoreIdState.value == null) {
          activeStoreIdState.emit(it.first().id)
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
  GlobalScope.launch {
    putLocalKv(KEY_APP_LOCALE, language)
  }
}

fun setAppTheme(themeId: Long) {
  GlobalScope.launch(Dispatchers.ourIo) {
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
    stringBatchesState.emit(
      strings.extractString(141, appLanguageState.value) ?: resourceStrings.extractString(
        141,
        appLanguageState.value
      )!!
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
}

fun deleteCartById(id: String, transactionTypeIndex: Int, clientId: Int) {
  GlobalScope.launch {
    appDatabase.app_databaseQueries.deleteCartById(id, transactionTypeIndex.toLong(), clientId.toLong())
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

fun postInAppNotification(
  message: List<LocalizedStringDataModel>?,
  type: NotificationType,
  transient: Boolean = true
) {
  message?.extractLocalizedString(appLanguageState.value)?.run {
    GlobalScope.launch(Dispatchers.ourIo) {
      latestInAppNotificationState.emit(
        NotificationDataModel(
          this@run,
          type
        )
      )

      if (transient) {
        delay(3000)
        latestInAppNotificationState.emit(null)
      }
    }
  }
}

fun postInAppNotification(message: String, type: NotificationType, transient: Boolean = true) {
  GlobalScope.launch(Dispatchers.ourIo) {
    latestInAppNotificationState.emit(
      NotificationDataModel(
        message,
        type
      )
    )

    if (transient) {
      delay(3000)
      latestInAppNotificationState.emit(null)
    }
  }
}

fun clearInAppNotification() {
  GlobalScope.launch(Dispatchers.ourIo) {
    latestInAppNotificationState.emit(null)
  }
}

fun logInUser(userAuthLogIn: UserAuthLogInDataModel) {
  if (!logInMutex.isLocked)
    GlobalScope.launch(Dispatchers.ourIo) {
      logInMutex.withLock {
        postInAppNotification(stringLoggingInState.value, NotificationType.Neutral)

        val response = networkRequest<TokenPair, UserAuthLogInDataModel>(
          HttpMethod.Post,
          endpointUrl = globalAppConfigurationState.payloadValue.logInPath.first,
          body = userAuthLogIn
        )

        if (response.negative) {
          postInAppNotification(response.message, NotificationType.Negative, transient = true)
        } else {
          setStoredUserAuthTokens?.invoke(response.payload)
          httpClient.authProvider<BearerAuthProvider>()?.clearToken()
          getUser()
        }
      }
    }
}

fun signUpUser(userAuthSignUp: UserAuthSignUpDataModel) {
  if (!signUpUserMutex.isLocked)
    GlobalScope.launch(Dispatchers.ourIo) {
      signUpUserMutex.withLock {
        postInAppNotification(stringSigningUpState.value, NotificationType.Neutral)

        val response = networkRequest<TokenPair, UserAuthSignUpDataModel>(
          HttpMethod.Post,
          endpointUrl = globalAppConfigurationState.payloadValue.signUpPath.first,
          body = userAuthSignUp
        )

        if (response.negative) {
          postInAppNotification(response.message, NotificationType.Negative)
        } else {
          setStoredUserAuthTokens?.invoke(response.payload)
          httpClient.authProvider<BearerAuthProvider>()?.clearToken()

          getUser()
        }
      }
    }
}

fun logOutUser() {
  if (!logOutUserMutex.isLocked)
    GlobalScope.launch(Dispatchers.ourIo) {
      logOutUserMutex.withLock {
        val response = networkRequest<Unit, String>(
          HttpMethod.Delete,
          endpointUrl = globalAppConfigurationState.payloadValue.logOutPath.first,
          body = getStoredUserAuthTokens?.invoke()?.refreshToken
        )

        if (response.negative) {
          postInAppNotification(response.message, NotificationType.Negative)
        } else {
          postInAppNotification(response.message, NotificationType.Positive)
          userAccountState.emit(DataState.Empty())

          setStoredUserAuthTokens?.invoke(null)
          setStoredUserAccountDataModel?.invoke(null)
          setActiveStoreId(null)
          httpClient.authProvider<BearerAuthProvider>()?.clearToken()
        }
      }
    }
}

fun getUser(forceLogOut: Boolean = true) {
  if (!getUserAccountMutex.isLocked)
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
            if (forceLogOut)
              forceLogOutUser()

            postInAppNotification(response.message, NotificationType.Negative)
          } else {
            clearInAppNotification()
            setStoredUserAccountDataModel?.invoke(response.payload)
            userAccountState.emit(DataState.Success(response.payload!!, response.message))

            getGlobalAppConfiguration()
            getStores()
            getSuppliers()
            getGenericGoodsCategories()
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
    setStoredUserAuthTokens?.invoke(null)
    setStoredUserAccountDataModel?.invoke(null)
    setActiveStoreId(null)

    userAccountState.emit(DataState.Empty())
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
        negative = true
      )
    } else {
      try {
        response.body<GenericResponseDataModel>().toResponseDataModel()
      } catch (_: Throwable) {
        response.body<ResponseDataModel<Response>>()
      }
    }
  } catch (throwable: Throwable) {
    ResponseDataModel(
      throwable.message?.let { listOf(LocalizedStringDataModel("main", it)) },
      null,
      true
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
          storesState.emit(DataState.Success(response.payload!!, response.message))
          if (response.payload.size == 1)
            setActiveStoreId(response.payload.first().id)
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

fun setActiveStoreId(id: String?) {
  GlobalScope.launch(Dispatchers.ourIo) {
    putLocalKv(KEY_ACTIVE_STORE_ID, id)
  }
}

val suppliersState = MutableDataStateFlow<List<SupplierDataModel>>(GlobalScope)

private val getSuppliersMutex = Mutex()

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
  if (!getStockMutex.isLocked)
    GlobalScope.launch(Dispatchers.ourIo) {
      getStockMutex.withLock {
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
                  stockBatchesState.payloadValue?.indexOfFirst { it.id == item.id }?.let { index ->
                    newList[index] = item
                  } ?: newList.addAll(response.payload)
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
        val response = networkRequest<String, List<String>>(
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
@kotlinx.serialization.Serializable
data class CashRegisterExtractionEntryDataModel(
  val id: String,
  val amount: Double,
  val timeMillis: Long
)

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
  val id: String,
  val type: Int,
  val amount: Double,
  val currency: String
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
data class DebtorDataModel(
  val id: String,
  val email: String,
  val debtAmount: Double,
  val currency: String,
  val phoneNumber: String,
  val firstName: String,
  val lastName: String,
  val transactionIds: List<Long>
)

@kotlinx.serialization.Serializable
data class GenericGoodsCategoryDataModel(
  val id: String,
  val typeIds: List<String>?,
  val name: List<LocalizedStringDataModel>,
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

  val getGenericGoodsItemsPath: Pair<String, String>,
  val getGenericGoodsCategoriesPath: Pair<String, String>,
  val getSuppliersPath: Pair<String, String>,
  val stringResourcesPath: Pair<String, String>,
  val dimensionResourcesPath: Pair<String, String>,
  val colorResourcesPath: Pair<String, String>,
  val drawableResourcesConfigurationPath: Pair<String, String>,
  val drawableResourcesPath: Pair<String, String>,
  val getTransactionsPath: Pair<String, String>,
  val completeTransactionPath: Pair<String, String>,
  val getSupplierGoodsPricesPath: Pair<String, String> = Pair("supplierGoodsPrices/get", "31"),
  val upsertSupplierGoodsPricePath: Pair<String, String> = Pair("supplierGoodsPrices/upsert", "32"),
  val deleteSupplierGoodsPricesPath: Pair<String, String> = Pair("supplierGoodsPrices/delete", "33"),

  val getSupplierOrdersPath: Pair<String, String> = Pair("supplierOrders/get", "34"),
  val addSupplierOrderPath: Pair<String, String> = Pair("supplierOrders/add", "35"),
  val updateSupplierOrderPath: Pair<String, String> = Pair("supplierOrders/update", "36"),
  val deleteSupplierOrdersPath: Pair<String, String> = Pair("supplierOrders/delete", "37"),
  val receiveSupplierOrderPath: Pair<String, String> = Pair("supplierOrders/receive", "38"),
  val companyForms: List<CompanyFormDataModel>,
  val countries: List<CountryDataModel>,
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
  val id: String,
  val name: String,
  val imageUrl: String,
  val quantityWithUnitSerialized: String,
  val storeId: String,
  val universal: Boolean
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

  val isQuickItem: Boolean = false,
  val imagePaths: List<String> = emptyList(),

  val activeShelfBatchId: String? = null,

  val note: String? = null,

  val createdAtMillis: Long = 0L,
  val updatedAtMillis: Long = 0L,
  val isActive: Boolean = true
): Searchable {

  override val exactSearchOperands: List<String>
    get() = mutableListOf<String>().apply {
      addAll(barcodes)
      addAll(name.map { it.value })
      addAll(salePrices.map { it.price })
      addAll(returnPrices.map { it.price })
      addAll(supplyPrices.map { it.price })
      addAll(salePrices.map { it.currency })
      addAll(returnPrices.map { it.currency })
      addAll(supplyPrices.map { it.currency })
    }
  override val containsSearchOperands: List<String>
    get() = mutableListOf<String>().apply {
      addAll(barcodes)
      addAll(name.map { it.value })
      addAll(salePrices.map { it.price })
      addAll(returnPrices.map { it.price })
      addAll(supplyPrices.map { it.price })
      addAll(salePrices.map { it.currency })
      addAll(returnPrices.map { it.currency })
      addAll(supplyPrices.map { it.currency })
    }
  override val uniqueSearchOperands: List<String>
    get() = mutableListOf<String>().apply {
      addAll(barcodes)
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
  val supplierId: Long? = null
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
  val type: NotificationType
)

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
data class ResponseDataModel<T>(
  val message: List<LocalizedStringDataModel>?,
  val payload: T?,
  val negative: Boolean
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
  val userIds: List<String>,
  val storeTypeIds: List<String>,
  val name: List<LocalizedStringDataModel>,
  val alias: List<LocalizedStringDataModel>,
  val description: List<LocalizedStringDataModel>,
  val companyForms: List<CompanyFormDataModel>,
  val location: LocationDataModel,
  val phoneNumbers: List<String>,
  val emails: List<String>,
  val countryLocales: List<String>,
  val createdAt: Long
): Searchable {

  override val exactSearchOperands: List<String>
    get() {
      return mutableListOf<String>()
        .apply {
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
          add(location.postalIndex)
          add(location.latitude.toString())
          add(location.longitude.toString())

          phoneNumbers.forEach { add(it) }
          emails.forEach { add(it) }
        }
    }
  override val uniqueSearchOperands: List<String>
    get() {
      return emptyList()
    }
}

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
  val id: String,
  val typeIds: List<String>?,
  val name: List<LocalizedStringDataModel>,
  val phoneNumbers: List<String>?,
  val emails: List<String>?,
  val addedAt: Long,
  val isActive: Boolean
)

@kotlinx.serialization.Serializable
data class TokenPair(
  val accessToken: String,
  val accessExpiryTime: Long,
  val refreshToken: String,
  val refreshExpiryTime: Long
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
  val timeMillis: Long
)

@kotlinx.serialization.Serializable
data class UserAccountDataModel(
  val id: String,
  val phoneNumber: String,
  val email: String,
  val firstName: String,
  val lastName: String,
  val countryLocale: String,
  val workerAccountIds: String?,
  val supplierAccountIds: String?,
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
  val password: String
)

@kotlinx.serialization.Serializable
data class UserAuthSignUpDataModel(
  val phoneNumber: String,
  val email: String,
  val firstName: String,
  val lastName: String,
  val countryLocale: String,
  val password: String
)

@kotlinx.serialization.Serializable
class UserBalanceDataModel(
  val value: String,
  val currencyCode: String,
  val history: List<BalanceHistoryEntryDataModel>
)

@kotlinx.serialization.Serializable
data class UserSettingsDataModel(
  val registrationTime: Long,
  val appLanguage: String,
  val appThemeId: Long,
  val appSizeModeId: Long
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

@kotlinx.serialization.Serializable
data class WorkshiftDataModel(
  val id: Long,
  val startTime: Long,
  val endTime: Long,
  val employeeId: Long
)