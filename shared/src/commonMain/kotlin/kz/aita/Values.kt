@file:OptIn(DelicateCoroutinesApi::class)
package kz.aita

import io.ktor.client.HttpClient
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpMethod
import io.ktor.http.Url
import io.ktor.http.encodedPath
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

val categoriesState = MutableDataStateFlow<List<GenericGoodsCategoryDataModel>>(GlobalScope)
val storeWorkersState = MutableDataStateFlow<List<UserAccountDataModel>>(GlobalScope)

val jsonBase: Json by lazy {
  Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
  }
}

val appModeState = MutableStateFlow(0)
val globalAppConfigurationState = MutableDataStateFlowNonNull(
  coroutineScope = GlobalScope,
  initial = GlobalAppConfigurationDataModel(
    realtimeUpdatesPath = "rt/updates",
    appName = Pair("AITA", "0"),
    serverUrl = Pair("http://192.168.100.9:8080", "1"),
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

val drawablePathAITALogoState = MutableStateFlow("svg/00.svg")
val drawablePathIconPasswordState = MutableStateFlow("svg/10.svg")
val drawablePathIconCancelState = MutableStateFlow("svg/20.svg")
val drawablePathIconEyeHideState = MutableStateFlow("svg/30.svg")
val drawablePathIconEyeShowState = MutableStateFlow("svg/40.svg")
val drawablePathIconEmailState = MutableStateFlow("svg/50.svg")
val drawablePathIconPhoneState = MutableStateFlow("svg/60.svg")
val drawablePathIconExpandMoreState = MutableStateFlow("svg/70.svg")
val drawablePathIconExpandLessState = MutableStateFlow("svg/80.svg")
val drawablePathIconPersonState = MutableStateFlow("svg/90.svg")
val drawablePathIconTransactionSaleState = MutableStateFlow("svg/100.svg")
val drawablePathIconTransactionReturnState = MutableStateFlow("svg/110.svg")
val drawablePathIconTransactionSupplyState = MutableStateFlow("svg/120.svg")
val drawablePathIconStockState = MutableStateFlow("svg/130.svg")
val drawablePathIconMenuState = MutableStateFlow("svg/140.svg")
val drawablePathIconBackArrowState = MutableStateFlow("svg/150.svg")
val drawablePathIconAddState = MutableStateFlow("svg/160.svg")
val drawablePathIconUserAccountState = MutableStateFlow("svg/170.svg")
val drawablePathIconGoodsCategoriesState = MutableStateFlow("svg/180.svg")
val drawablePathIconStoresState = MutableStateFlow("svg/190.svg")
val drawablePathIconTransactionHistoryState = MutableStateFlow("svg/200.svg")
val drawablePathIconAnalyticsState = MutableStateFlow("svg/210.svg")
val drawablePathIconWorkersState = MutableStateFlow("svg/220.svg")
val drawablePathIconSuppliersState = MutableStateFlow("svg/230.svg")
val drawablePathIconDebtorsState = MutableStateFlow("svg/240.svg")
val drawablePathIconDevicesState = MutableStateFlow("svg/250.svg")
val drawablePathIconAppLanguageState = MutableStateFlow("svg/260.svg")
val drawablePathIconAppThemeState = MutableStateFlow("svg/270.svg")
val drawablePathIconCheckState = MutableStateFlow("svg/280.svg")
val drawablePathIconEditState = MutableStateFlow("svg/290.svg")
val drawablePathIconSettingsState = MutableStateFlow("svg/300.svg")
val drawablePathIconSearchState = MutableStateFlow("svg/310.svg")
val drawablePathIconBarcodeCamScannerState = MutableStateFlow("svg/320.svg")
val drawablePathIconDeleteState = MutableStateFlow("svg/330.svg")
val drawablePathIconExitState = MutableStateFlow("svg/340.svg")
val drawablePathIconSwitchState = MutableStateFlow("svg/350.svg")
val drawablePathIconCartState = MutableStateFlow("svg/360.svg")
val drawablePathIconAddCartState = MutableStateFlow("svg/370.svg")
val drawablePathIconSubtractState = MutableStateFlow("svg/380.svg")
val drawablePathIconReceiptState = MutableStateFlow("svg/390.svg")
val drawablePathIconFinancesState = MutableStateFlow("svg/400.svg")

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

val appDatabase = AppDatabase(sqlDelightDriver!!)

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
          withContext(Dispatchers.io) {
            tokenStore?.get()?.let {
              BearerTokens(it.accessToken, it.refreshToken)
            }
          }
        }

        refreshTokens {
          withContext(Dispatchers.io) {

            tokenRefreshMutex.withLock {
              val current = tokenStore?.get()

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
                tokenStore?.set(response.payload)
                BearerTokens(response.payload.accessToken, response.payload.refreshToken)
              } else {
                tokenStore?.set(null)
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
val activeStoreId = MutableStateFlow<String?>(null)

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