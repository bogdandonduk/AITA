package kz.aita

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
    coroutineScope.launch(Dispatchers.io) {
      _state.collect {
        if (it is DataState.Success)
          _payload.emit(it.payload)
      }
    }
  }

  fun emit(newValue: DataState<T>) {
    coroutineScope.launch(Dispatchers.io) {
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
    coroutineScope.launch(Dispatchers.io) {
      _state.collect {
        if (it is DataState.Success)
          _payload.emit(it.payload)
        else
          _payload.emit(null)
      }
    }
  }

  fun emit(newValue: DataState<T>) {
    coroutineScope.launch(Dispatchers.io) {
      _state.emit(newValue)
    }
  }

  fun asDataStateFlow(): DataStateFlow<T> {
    return this as DataStateFlow<T>
  }
}

interface DataStore<T> {
  suspend fun get(): T?
  suspend fun set(value: T?)
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
  val companyForms: List<CompanyFormDataModel>,
  val countries: List<CountryDataModel>,
  val languages: List<AppLanguageDataModel>,
  val themes: List<AppThemeDataModel>,
  val goodsItemsQuantityUnits: List<QuantityDataModel>
)

@kotlinx.serialization.Serializable
data class GoodsBatchDataModel(
  val id: String,
  val goodsItemId: String,
  val userId: String,
  val storeId: String,
  val supplierId: String,
  val salePrice: PriceDataModel,
  val returnPrice: PriceDataModel,
  val supplyPrice: PriceDataModel,
  val quantity: QuantityDataModel,
  val supplyTime: Long,
  val expirationTime: Long,
  val shelfQueue: GoodsBatchShelfQueueDataModel,
  val createdAt: Long,
  val createdByUserId: String,
  val isActive: Boolean
)

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
  val id: String,
  val userId: String,
  val storeId: String,
  val barcode: List<String>,
  val name: List<LocalizedStringDataModel>,
  val measurementUnitId: String,
  val categoryIds: List<String>,
  val salePrices: List<PriceDataModel>,
  val returnPrices: List<PriceDataModel> = salePrices,
  val supplyPrices: List<PriceDataModel>,
  val isQuickItem: Boolean,
  val createdAt: Long,
  val isActive: Boolean
): Searchable {

  override val exactSearchOperands: List<String>
    get() = mutableListOf<String>().apply {
      addAll(barcode)
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
      addAll(barcode)
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
      addAll(barcode)
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