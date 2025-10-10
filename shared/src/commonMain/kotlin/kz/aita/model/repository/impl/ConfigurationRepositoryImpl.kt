package kz.aita.model.repository.impl

import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kz.aita.core.extractExceptionMessage
import kz.aita.core.extractPath
import kz.aita.core.extractString
import kz.aita.core.genericLocalService
import kz.aita.core.io
import kz.aita.model.dataModel.*
import kz.aita.model.repository.ConfigurationRepository
import kz.aita.model.repository.Repository
import kz.aita.model.service.GenericRemoteService
import kz.aita.model.wrapper.DataState
import kz.aita.model.wrapper.MutableDataStateFlow
import kz.aita.model.wrapper.MutableDataStateFlowNonNull

class ConfigurationRepositoryImpl(
  private val genericRemoteService: GenericRemoteService
): Repository(), ConfigurationRepository {

  private val _globalAppConfigurationState = MutableDataStateFlowNonNull(
    coroutineScope = this,
    initial = GlobalAppConfigurationDataModel(
      appName = "AITA",
      serverUrl = "http://192.168.100.9:8080",
      globalAppConfigurationPath = "config/global",
      exceptionConfigurationPath = "config/exception",
      logInPath = "auth/logIn",
      signUpPath = "auth/signUp",
      refreshPath = "auth/refresh",
      logOutPath = "auth/logOut",
      getUserPath = "user/get",
      updateUserPath = "user/update",
      getStoresPath = "stores/get",
      addStoresPath = "stores/add",
      updateStoresPath = "stores/update",
      deleteStoresPath = "stores/delete",
      stockPath = "stock",
      suppliersPath = "suppliers",
      stringResourcesPath = "res/string",
      dimensionResourcesPath = "res/dimension",
      colorResourcesPath = "res/color",
      drawableResourcesConfigurationPath = "res/drawableConfig",
      drawableResourcesPath = "res/drawable",
      companyForms = emptyList(),
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
          currency = "₸",
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
          )
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
      currencies = listOf(
        CurrencyDataModel(
          currency = "KZT",
          countries = listOf("kz"),
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
        CurrencyDataModel(
          currency = "TJS",
          countries = listOf("tj"),
          symbol = "SM",
          name = listOf(
            LocalizedStringDataModel(
              language = "en",
              value = "somoni"
            ),
            LocalizedStringDataModel(
              language = "ru",
              value = "сом"
            ),
            LocalizedStringDataModel(
              language = "kk",
              value = "сом"
            )
          )
        ),
        CurrencyDataModel(
          currency = "RUB",
          countries = listOf("ru"),
          symbol = "₽",
          name = listOf(
            LocalizedStringDataModel(
              language = "en",
              value = "rub."
            ),
            LocalizedStringDataModel(
              language = "ru",
              value = "руб."
            ),
            LocalizedStringDataModel(
              language = "kk",
              value = "руб."
            )
          )
        ),
        CurrencyDataModel(
          currency = "USD",
          symbol = "$",
          countries = listOf("us"),
          name = listOf(
            LocalizedStringDataModel(
              language = "en",
              value = "US$"
            ),
            LocalizedStringDataModel(
              language = "ru",
              value = "$ США"
            ),
            LocalizedStringDataModel(
              language = "kk",
              value = "US$"
            )
          )
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
          id = 0,
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
          id = 1,
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
  override val globalAppConfigurationState = _globalAppConfigurationState.asDataStateFlow()

  private val _stringsState = MutableDataStateFlow<List<LocalizedStringGroupDataModel>>(this)
  override val stringsState = _stringsState.asDataStateFlow()

  private val _dimensionsState = MutableDataStateFlow<List<StylizedDimensionGroupDataModel>>(this)
  override val dimensionsState = _dimensionsState.asDataStateFlow()

  private val _colorsState = MutableDataStateFlow<List<StylizedColorGroupDataModel>>(this)
  override val colorsState = _colorsState.asDataStateFlow()

  private val _drawablesState = MutableDataStateFlow<List<StylizedDrawablePathsGroupDataModel>>(this)
  override val drawablesState = _drawablesState.asDataStateFlow()

  private val _exceptionsState = MutableDataStateFlow<List<ExceptionDataModel>>(this)
  override val exceptionsState = _exceptionsState.asDataStateFlow()

  private val _appLanguageState = MutableStateFlow("system")
  override val appLanguageState: StateFlow<String> = _appLanguageState.asStateFlow()
  private val _appThemeIdState = MutableStateFlow(0L)
  override val appThemeIdState: StateFlow<Long> = _appThemeIdState.asStateFlow()

  private val _appSizeModeIdState = MutableStateFlow(0L)
  override val appSizeModeIdState: StateFlow<Long> = _appSizeModeIdState.asStateFlow()

  private val _stringAppNameState = MutableStateFlow("AITA")
  override val stringAppNameState = _stringAppNameState.asStateFlow()
  private val _stringLogInState = MutableStateFlow("Log In")
  override val stringLogInState = _stringLogInState.asStateFlow()
  private val _stringPhoneNumberState = MutableStateFlow("Phone number")
  override val stringPhoneNumberState = _stringPhoneNumberState.asStateFlow()
  private val _stringEnterPhoneNumberState = MutableStateFlow("Enter phone number")
  override val stringEnterPhoneNumberState = _stringEnterPhoneNumberState.asStateFlow()
  private val _stringEmailState = MutableStateFlow("Email")
  override val stringEmailState = _stringEmailState.asStateFlow()
  private val _stringEnterEmailAddressState = MutableStateFlow("Enter email address")
  override val stringEnterEmailAddressState = _stringEnterEmailAddressState.asStateFlow()
  private val _stringPasswordState = MutableStateFlow("Password")
  override val stringPasswordState = _stringPasswordState.asStateFlow()
  private val _stringEnterPasswordState = MutableStateFlow("Enter password")
  override val stringEnterPasswordState = _stringEnterPasswordState.asStateFlow()
  private val _stringCancelState = MutableStateFlow("Cancel")
  override val stringCancelState = _stringCancelState.asStateFlow()
  private val _stringClearState = MutableStateFlow("Clear")
  override val stringClearState = _stringClearState.asStateFlow()
  private val _stringLoginAndOrPasswordIncorrectState = MutableStateFlow("Login and/or password incorrect")
  override val stringLoginAndOrPasswordIncorrectState = _stringLoginAndOrPasswordIncorrectState.asStateFlow()
  private val _stringPhoneNumberMustBeState = MutableStateFlow("Incorrect phone number length")
  override val stringPhoneNumberMustBeState = _stringPhoneNumberMustBeState.asStateFlow()
  private val _stringEmailMustBeState = MutableStateFlow("Incorrect email address format")
  override val stringEmailMustBeState = _stringEmailMustBeState.asStateFlow()
  private val _stringPasswordMustBeState = MutableStateFlow("Password must be 8 or more symbols long and contain at least one digit")
  override val stringPasswordMustBeState = _stringPasswordMustBeState.asStateFlow()
  private val _stringRepeatPasswordState = MutableStateFlow("Repeat password")
  override val stringRepeatPasswordState = _stringRepeatPasswordState.asStateFlow()
  private val _stringPasswordsMustMatchState = MutableStateFlow("Passwords must match")
  override val stringPasswordsMustMatchState = _stringPasswordsMustMatchState.asStateFlow()
  private val _stringFirstNameState = MutableStateFlow("First name")
  override val stringFirstNameState = _stringFirstNameState.asStateFlow()
  private val _stringLastNameState = MutableStateFlow("Last name")
  override val stringLastNameState = _stringLastNameState.asStateFlow()
  private val _stringEnterFirstNameState = MutableStateFlow("Enter first name")
  override val stringEnterFirstNameState = _stringEnterFirstNameState.asStateFlow()
  private val _stringEnterLastNameState = MutableStateFlow("Enter last name")
  override val stringEnterLastNameState = _stringEnterLastNameState.asStateFlow()

  private val _stringUserWithThisPhoneNumberIsAlreadyRegisteredState = MutableStateFlow("User with this phone number is already registered")
  override val stringUserWithThisPhoneNumberIsAlreadyRegisteredState = _stringUserWithThisPhoneNumberIsAlreadyRegisteredState.asStateFlow()
  private val _stringUserWithThisEmailAddressIsAlreadyRegisteredState = MutableStateFlow("User with this email address is already registered")
  override val stringUserWithThisEmailAddressIsAlreadyRegisteredState = _stringUserWithThisEmailAddressIsAlreadyRegisteredState.asStateFlow()
  private val _stringSignUpState = MutableStateFlow("Sign Up")
  override val stringSignUpState = _stringSignUpState.asStateFlow()
  private val _stringConfirmState = MutableStateFlow("Confirm")
  override val stringConfirmState = _stringConfirmState.asStateFlow()
  private val _stringSaleState = MutableStateFlow("Sale")
  override val stringSaleState = _stringSaleState.asStateFlow()
  private val _stringReturnState = MutableStateFlow("Return")
  override val stringReturnState = _stringReturnState.asStateFlow()
  private val _stringSupplyState = MutableStateFlow("Supply")
  override val stringSupplyState = _stringSupplyState.asStateFlow()
  private val _stringStockState = MutableStateFlow("Stock")
  override val stringStockState = _stringStockState.asStateFlow()
  private val _stringMenuState = MutableStateFlow("Menu")
  override val stringMenuState = _stringMenuState.asStateFlow()
  private val _stringBackState = MutableStateFlow("Back")
  override val stringBackState = _stringBackState.asStateFlow()
  private val _stringAddGoodsItemState = MutableStateFlow("Add goods item")
  override val stringAddGoodsItemState = _stringAddGoodsItemState.asStateFlow()
  private val _stringEditGoodsItemState = MutableStateFlow("Edit goods item")
  override val stringEditGoodsItemState = _stringEditGoodsItemState.asStateFlow()
  private val _stringUserAccountState = MutableStateFlow("User account")
  override val stringUserAccountState = _stringUserAccountState.asStateFlow()
  private val _stringGoodsCategoriesState = MutableStateFlow("Goods categories")
  override val stringGoodsCategoriesState = _stringGoodsCategoriesState.asStateFlow()
  private val _stringAddGoodsCategoryState = MutableStateFlow("Add goods category")
  override val stringAddGoodsCategoryState = _stringAddGoodsCategoryState.asStateFlow()
  private val _stringEditGoodsCategoryState = MutableStateFlow("Edit goods category")
  override val stringEditGoodsCategoryState = _stringEditGoodsCategoryState.asStateFlow()
  private val _stringStoresState = MutableStateFlow("Stores")
  override val stringStoresState = _stringStoresState.asStateFlow()
  private val _stringAddStoreState = MutableStateFlow("Add store")
  override val stringAddStoreState = _stringAddStoreState.asStateFlow()
  private val _stringEditStoreState = MutableStateFlow("Edit store")
  override val stringEditStoreState = _stringEditStoreState.asStateFlow()
  private val _stringSubscriptionState = MutableStateFlow("Subscription")
  override val stringSubscriptionState = _stringSubscriptionState.asStateFlow()
  private val _stringSubscriptionPlansState = MutableStateFlow("Subscription plans")
  override val stringSubscriptionPlansState = _stringSubscriptionPlansState.asStateFlow()
  private val _stringTransactionHistoryState = MutableStateFlow("Transaction history")
  override val stringTransactionHistoryState = _stringTransactionHistoryState.asStateFlow()
  private val _stringReceiptState = MutableStateFlow("Receipt")
  override val stringReceiptState = _stringReceiptState.asStateFlow()
  private val _stringAnalyticsState = MutableStateFlow("Analytics")
  override val stringAnalyticsState = _stringAnalyticsState.asStateFlow()
  private val _stringWorkersState = MutableStateFlow("Workers")
  override val stringWorkersState = _stringWorkersState.asStateFlow()
  private val _stringAddWorkerState = MutableStateFlow("Add worker")
  override val stringAddWorkerState = _stringAddWorkerState.asStateFlow()
  private val _stringEditWorkerState = MutableStateFlow("Edit worker")
  override val stringEditWorkerState = _stringEditWorkerState.asStateFlow()
  private val _stringSuppliersState = MutableStateFlow("Suppliers")
  override val stringSuppliersState = _stringSuppliersState.asStateFlow()
  private val _stringAddSupplierState = MutableStateFlow("Add supplier")
  override val stringAddSupplierState = _stringAddSupplierState.asStateFlow()
  private val _stringEditSupplierState = MutableStateFlow("Edit supplier")
  override val stringEditSupplierState = _stringEditSupplierState.asStateFlow()
  private val _stringDebtorsState = MutableStateFlow("Debtors")
  override val stringDebtorsState = _stringDebtorsState.asStateFlow()
  private val _stringCloseDebtState = MutableStateFlow("Close debt")
  override val stringCloseDebtState = _stringCloseDebtState.asStateFlow()
  private val _stringDevicesState = MutableStateFlow("Devices")
  override val stringDevicesState = _stringDevicesState.asStateFlow()
  private val _stringAppLanguageState = MutableStateFlow("App language")
  override val  stringAppLanguageState =  _stringAppLanguageState.asStateFlow()
  private val _stringAppThemeState = MutableStateFlow("App theme")
  override val stringAppThemeState = _stringAppThemeState .asStateFlow()
  private val _stringSelectState = MutableStateFlow("Select")
  override val stringSelectState = _stringSelectState.asStateFlow()
  private val _stringUserWithThisPhoneNumberAndEmailAddressIsAlreadyRegisteredState = MutableStateFlow("User with this phone number and email address is already registered")
  override val stringUserWithThisPhoneNumberAndEmailAddressIsAlreadyRegisteredState = _stringUserWithThisPhoneNumberAndEmailAddressIsAlreadyRegisteredState.asStateFlow()
  private val _stringFirstNameCannotBeEmptyOrJustWhitespacesState = MutableStateFlow("First name cannot be empty or just whitespaces")
  override val stringFirstNameCannotBeEmptyOrJustWhitespacesState = _stringFirstNameCannotBeEmptyOrJustWhitespacesState.asStateFlow()
  private val _stringLastNameCannotBeEmptyOrJustWhitespacesState = MutableStateFlow("Last cannot be empty or just whitespaces")
  override val stringLastNameCannotBeEmptyOrJustWhitespacesState = _stringLastNameCannotBeEmptyOrJustWhitespacesState.asStateFlow()
  private val _stringSystemLanguageState = MutableStateFlow("System language")
  override val stringSystemLanguageState = _stringSystemLanguageState.asStateFlow()
  private val _stringBluetoothPermissionRequiredState = MutableStateFlow("Bluetooth permission required")
  override val stringBluetoothPermissionRequiredState = _stringBluetoothPermissionRequiredState.asStateFlow()
  private val _stringForSearchAndConnectionToBluetoothBarcodeScannersAndReceiptPrintersState = MutableStateFlow("For search and connection to Bluetooth barcode scanners and receipt printers")
  override val stringForSearchAndConnectionToBluetoothBarcodeScannersAndReceiptPrintersState = _stringForSearchAndConnectionToBluetoothBarcodeScannersAndReceiptPrintersState.asStateFlow()
  private val _stringForSearchAndConnectionToBluetoothBarcodeScannersAndReceiptPrintersYouCanGrantItInAppSettingsState = MutableStateFlow("For search and connection to Bluetooth barcode scanners and receipt printers. You can grant it in app settings")
  override val stringForSearchAndConnectionToBluetoothBarcodeScannersAndReceiptPrintersYouCanGrantItInAppSettingsState = _stringForSearchAndConnectionToBluetoothBarcodeScannersAndReceiptPrintersYouCanGrantItInAppSettingsState.asStateFlow()
  private val _stringBluetoothDisabledState = MutableStateFlow("Bluetooth disabled")
  override val  stringBluetoothDisabledState =  _stringBluetoothDisabledState.asStateFlow()
  private val _stringEnableForSearchAndConnectionToBluetoothBarcodeScannersAndReceiptPrintersState = MutableStateFlow("Enable for search and connection to Bluetooth barcode scanners and receipt printers")
  override val stringEnableForSearchAndConnectionToBluetoothBarcodeScannersAndReceiptPrintersState = _stringEnableForSearchAndConnectionToBluetoothBarcodeScannersAndReceiptPrintersState.asStateFlow()
  private val _stringSearchByAnyDataState = MutableStateFlow("Search by any data")
  override val stringSearchByAnyDataState = _stringSearchByAnyDataState.asStateFlow()
  private val _stringListEmptyState = MutableStateFlow("List empty")
  override val stringListEmptyState = _stringListEmptyState.asStateFlow()
  private val _stringNoMatchesState = MutableStateFlow("No matches")
  override val stringNoMatchesState = _stringNoMatchesState.asStateFlow()
  private val _stringNameState = MutableStateFlow("Name")
  override val stringNameState = _stringNameState.asStateFlow()
  private val _stringBarcodeState = MutableStateFlow("Barcode")
  override val stringBarcodeState = _stringBarcodeState.asStateFlow()
  private val _stringSupplyPriceState = MutableStateFlow("Supply price")
  override val stringSupplyPriceState = _stringSupplyPriceState.asStateFlow()
  private val _stringSalePriceState = MutableStateFlow("Sale price")
  override val stringSalePriceState = _stringSalePriceState.asStateFlow()
  private val _stringReturnPriceState = MutableStateFlow("Return price")
  override val stringReturnPriceState = _stringReturnPriceState.asStateFlow()
  private val _stringCategoryState = MutableStateFlow("Category")
  override val stringCategoryState = _stringCategoryState.asStateFlow()
  private val _stringSupplierState  = MutableStateFlow("Supplier")
  override val stringSupplierState = _stringSupplierState.asStateFlow()
  private val _stringEnterNameState = MutableStateFlow("Enter name")
  override val stringEnterNameState = _stringEnterNameState.asStateFlow()
  private val _stringEnterBarcodeState = MutableStateFlow("Enter barcode")
  override val stringEnterBarcodeState = _stringEnterBarcodeState.asStateFlow()
  private val _stringEnterSupplyPriceState = MutableStateFlow("Enter supply price")
  override val stringEnterSupplyPriceState = _stringEnterSupplyPriceState.asStateFlow()
  private val _stringEnterSalePriceState = MutableStateFlow("Enter sale price")
  override val stringEnterSalePriceState = _stringEnterSalePriceState.asStateFlow()
  private val _stringEnterReturnPriceState = MutableStateFlow("Enter return price")
  override val stringEnterReturnPriceState = _stringEnterReturnPriceState.asStateFlow()
  private val _stringSelectCategoryState = MutableStateFlow("Select category")
  override val stringSelectCategoryState = _stringSelectCategoryState.asStateFlow()
  private val _stringSelectSupplierState  = MutableStateFlow("Select supplier")
  override val stringSelectSupplierState = _stringSelectSupplierState.asStateFlow()
  private val _stringEditState = MutableStateFlow("Edit")
  override val stringEditState = _stringEditState.asStateFlow()
  private val _stringChangePasswordState = MutableStateFlow("Change password")
  override val stringChangePasswordState = _stringChangePasswordState.asStateFlow()
  private val _stringNewPasswordState = MutableStateFlow("New password")
  override val stringNewPasswordState = _stringNewPasswordState.asStateFlow()
  private val _stringEnterNewPasswordState = MutableStateFlow("Enter new password")
  override val stringEnterNewPasswordState = _stringEnterNewPasswordState.asStateFlow()
  private val _stringRepeatNewPasswordState = MutableStateFlow("Repeat new password")
  override val stringRepeatNewPasswordState = _stringRepeatNewPasswordState.asStateFlow()
  private val _stringConfirmationPasswordState = MutableStateFlow("Confirmation password")
  override val stringConfirmationPasswordState = _stringConfirmationPasswordState.asStateFlow()
  private val _stringRequiredToEditAccountState = MutableStateFlow("Required to edit account")
  override val stringRequiredToEditAccountState = _stringRequiredToEditAccountState.asStateFlow()
  private val _stringAccountSuccessfullyUpdatedState = MutableStateFlow("Account successfully updated")
  override val stringAccountSuccessfullyUpdatedState = _stringAccountSuccessfullyUpdatedState.asStateFlow()
  private val _stringLoggingOutInProgressState = MutableStateFlow("Logging out in progress")
  override val stringLoggingOutInProgressState = _stringLoggingOutInProgressState.asStateFlow()
  private val _stringSessionTimeExpiredLoggingOutState = MutableStateFlow("Session expired")
  override val stringSessionTimeExpiredLoggingOutState = _stringSessionTimeExpiredLoggingOutState.asStateFlow()
  private val _stringAliasState = MutableStateFlow("Alias")
  override val stringAliasState: StateFlow<String> = _stringAliasState.asStateFlow()
  private val _stringDescriptionState = MutableStateFlow("Description")
  override val stringDescriptionState: StateFlow<String> = _stringDescriptionState.asStateFlow()
  private val _stringEnterAliasState = MutableStateFlow("Enter alias")
  override val stringEnterAliasState: StateFlow<String> = _stringEnterAliasState.asStateFlow()
  private val _stringEnterDescriptionState = MutableStateFlow("Enter description")
  override val stringEnterDescriptionState: StateFlow<String> = _stringEnterDescriptionState.asStateFlow()
  private val _stringOptionalState = MutableStateFlow("Optional")
  override val stringOptionalState: StateFlow<String> = _stringOptionalState.asStateFlow()

  private val _drawablePathAITALogoState = MutableStateFlow("svg/0_0.svg")
  override val drawablePathAITALogoState = _drawablePathAITALogoState.asStateFlow()
  private val _drawablePathIconPasswordState = MutableStateFlow("svg/1_0.svg")
  override val drawablePathIconPasswordState = _drawablePathIconPasswordState.asStateFlow()
  private val _drawablePathIconCancelState = MutableStateFlow("svg/2_0.svg")
  override val drawablePathIconCancelState = _drawablePathIconCancelState.asStateFlow()
  private val _drawablePathIconEyeHideState = MutableStateFlow("svg/3_0.svg")
  override val drawablePathIconEyeHideState = _drawablePathIconEyeHideState.asStateFlow()
  private val _drawablePathIconEyeShowState = MutableStateFlow("svg/4_0.svg")
  override val drawablePathIconEyeShowState = _drawablePathIconEyeShowState.asStateFlow()
  private val _drawablePathIconEmailState = MutableStateFlow("svg/5_0.svg")
  override val drawablePathIconEmailState = _drawablePathIconEmailState.asStateFlow()
  private val _drawablePathIconPhoneState = MutableStateFlow("svg/6_0.svg")
  override val drawablePathIconPhoneState = _drawablePathIconPhoneState.asStateFlow()
  private val _drawablePathIconExpandMoreState = MutableStateFlow("svg/7_0.svg")
  override val drawablePathIconExpandMoreState = _drawablePathIconExpandMoreState.asStateFlow()
  private val _drawablePathIconExpandLessState = MutableStateFlow("svg/8_0.svg")
  override val drawablePathIconExpandLessState = _drawablePathIconExpandLessState.asStateFlow()
  private val _drawablePathIconPersonState = MutableStateFlow("svg/9_0.svg")
  override val drawablePathIconPersonState = _drawablePathIconPersonState.asStateFlow()
  private val _drawablePathIconTransactionSaleState = MutableStateFlow("svg/10_0.svg")
  override val drawablePathIconTransactionSaleState = _drawablePathIconTransactionSaleState.asStateFlow()
  private val _drawablePathIconTransactionReturnState = MutableStateFlow("svg/11_0.svg")
  override val drawablePathIconTransactionReturnState = _drawablePathIconTransactionReturnState.asStateFlow()
  private val _drawablePathIconTransactionSupplyState = MutableStateFlow("svg/12_0.svg")
  override val drawablePathIconTransactionSupplyState = _drawablePathIconTransactionSupplyState.asStateFlow()
  private val _drawablePathIconStockState = MutableStateFlow("svg/13_0.svg")
  override val drawablePathIconStockState = _drawablePathIconStockState.asStateFlow()
  private val _drawablePathIconMenuState = MutableStateFlow("svg/14_0.svg")
  override val drawablePathIconMenuState = _drawablePathIconMenuState.asStateFlow()
  private val _drawablePathIconBackArrowState = MutableStateFlow("svg/15_0.svg")
  override val drawablePathIconBackArrowState = _drawablePathIconBackArrowState.asStateFlow()
  private val _drawablePathIconAddState = MutableStateFlow("svg/16_0.svg")
  override val drawablePathIconAddState = _drawablePathIconAddState.asStateFlow()
  private val _drawablePathIconUserAccountState = MutableStateFlow("svg/17_0.svg")
  override val drawablePathIconUserAccountState = _drawablePathIconUserAccountState.asStateFlow()
  private val _drawablePathIconGoodsCategoriesState = MutableStateFlow("svg/18_0.svg")
  override val drawablePathIconGoodsCategoriesState = _drawablePathIconGoodsCategoriesState.asStateFlow()
  private val _drawablePathIconStoresState = MutableStateFlow("svg/19_0.svg")
  override val drawablePathIconStoresState = _drawablePathIconStoresState.asStateFlow()
  private val _drawablePathIconTransactionHistoryState = MutableStateFlow("svg/20_0.svg")
  override val drawablePathIconTransactionHistoryState = _drawablePathIconTransactionHistoryState.asStateFlow()
  private val _drawablePathIconAnalyticsState = MutableStateFlow("svg/21_0.svg")
  override val drawablePathIconAnalyticsState = _drawablePathIconAnalyticsState.asStateFlow()
  private val _drawablePathIconWorkersState = MutableStateFlow("svg/22_0.svg")
  override val drawablePathIconWorkersState = _drawablePathIconWorkersState.asStateFlow()
  private val _drawablePathIconSuppliersState = MutableStateFlow("svg/23_0.svg")
  override val drawablePathIconSuppliersState = _drawablePathIconSuppliersState.asStateFlow()
  private val _drawablePathIconDebtorsState = MutableStateFlow("svg/24_0.svg")
  override val drawablePathIconDebtorsState = _drawablePathIconDebtorsState.asStateFlow()
  private val _drawablePathIconDevicesState = MutableStateFlow("svg/25_0.svg")
  override val drawablePathIconDevicesState = _drawablePathIconDevicesState.asStateFlow()
  private val _drawablePathIconAppLanguageState = MutableStateFlow("svg/26_0.svg")
  override val drawablePathIconAppLanguageState = _drawablePathIconAppLanguageState.asStateFlow()
  private val _drawablePathIconAppThemeState = MutableStateFlow("svg/27_0.svg")
  override val drawablePathIconAppThemeState = _drawablePathIconAppThemeState.asStateFlow()
  private val _drawablePathIconCheckState = MutableStateFlow("svg/28_0.svg")
  override val drawablePathIconCheckState = _drawablePathIconCheckState.asStateFlow()
  private val _drawablePathIconEditState = MutableStateFlow("svg/29_0.svg")
  override val drawablePathIconEditState = _drawablePathIconEditState.asStateFlow()
  private val _drawablePathIconSettingsState = MutableStateFlow("svg/30_0.svg")
  override val drawablePathIconSettingsState = _drawablePathIconSettingsState.asStateFlow()
  private val _drawablePathIconSearchState = MutableStateFlow("svg/31_0.svg")
  override val drawablePathIconSearchState = _drawablePathIconSearchState.asStateFlow()
  private val _drawablePathIconBarcodeCamScannerState = MutableStateFlow("svg/32_0.svg")
  override val drawablePathIconBarcodeCamScannerState = _drawablePathIconBarcodeCamScannerState.asStateFlow()
  private val _drawablePathIconDeleteState = MutableStateFlow("svg/33_0.svg")
  override val drawablePathIconDeleteState = _drawablePathIconDeleteState.asStateFlow()
  private val _drawablePathIconExitState = MutableStateFlow("svg/34_0.svg")
  override val drawablePathIconExitState = _drawablePathIconExitState.asStateFlow()

  private val _exceptionMessageUserWithThisPhoneNumberIsAlreadyRegisteredState = MutableStateFlow("User with this phone number is already registered")
  override val exceptionMessageUserWithThisPhoneNumberIsAlreadyRegisteredState = _exceptionMessageUserWithThisPhoneNumberIsAlreadyRegisteredState.asStateFlow()
  private val _exceptionMessageUserWithThisEmailAddressIsAlreadyRegisteredState = MutableStateFlow("User with this email address is already registered")
  override val exceptionMessageUserWithThisEmailAddressIsAlreadyRegisteredState = _exceptionMessageUserWithThisEmailAddressIsAlreadyRegisteredState.asStateFlow()
  private val _exceptionMessageUserWithThisPhoneNumberAndEmailAddressIsAlreadyRegisteredState = MutableStateFlow("User with this phone number and email address is already registered")
  override val exceptionMessageUserWithThisPhoneNumberAndEmailAddressIsAlreadyRegisteredState = _exceptionMessageUserWithThisPhoneNumberAndEmailAddressIsAlreadyRegisteredState.asStateFlow()
  private val _exceptionMessageRefreshTokenExpiredState = MutableStateFlow("Refresh token expired")
  override val exceptionMessageRefreshTokenExpiredState = _exceptionMessageRefreshTokenExpiredState.asStateFlow()
  private val _exceptionMessageLoginAndOrPasswordIncorrectState = MutableStateFlow("Login and/or password incorrect")
  override val exceptionMessageLoginAndOrPasswordIncorrectState = _exceptionMessageLoginAndOrPasswordIncorrectState.asStateFlow()
  private val _exceptionMessagePleaseLogInFirstState = MutableStateFlow("Please log in first")
  override val exceptionMessagePleaseLogInFirstState = _exceptionMessagePleaseLogInFirstState.asStateFlow()
  private val _exceptionMessageIncorrectPasswordState = MutableStateFlow("Incorrect password")
  override val exceptionMessageIncorrectPasswordState = _exceptionMessageIncorrectPasswordState.asStateFlow()

  companion object {
    private const val KEY_APP_THEME = "key_appTheme"
    private const val KEY_APP_LOCALE = "key_appLocale"
    private const val KEY_APP_SIZE_MODE = "key_appSizeMode"
  }

  init {
    launch {
      genericLocalService
        .observe(KEY_APP_LOCALE)
        .collect {
          it?.let {
            _appLanguageState.emit(it)
          }
        }
    }

    launch {
      genericLocalService
        .observe(KEY_APP_THEME)
        .collect {
          it?.let {
            _appThemeIdState.emit(it.toLong())
          }
        }
    }

    launch {
      genericLocalService
        .observe(KEY_APP_SIZE_MODE)
        .collect {
          it?.let {
            _appSizeModeIdState.emit(it.toLong())
          }
        }
    }

    getGlobalAppConfiguration()
  }

  override fun getGlobalAppConfiguration(loadAll: Boolean) {
    launch(Dispatchers.io) {
      genericRemoteService
        .request<GlobalAppConfigurationDataModel, Unit>(
          method = HttpMethod.Get,
          endpointUrl = globalAppConfigurationState.payloadValue.globalAppConfigurationPath,
          onFailure = {
            _globalAppConfigurationState.emit(DataState.Failure(it))
          }
        )?.run {
          _globalAppConfigurationState.emit(DataState.Success(this))

          if (loadAll) {
            getStrings()
            getDimensions()
            getColors()
            getDrawables()
            getExceptions()
          }
        }
    }
  }

  override fun getStrings() {
    launch(Dispatchers.io) {
      genericRemoteService
        .request<List<LocalizedStringGroupDataModel>, Unit>(
          method = HttpMethod.Get,
          endpointUrl = globalAppConfigurationState.payloadValue.stringResourcesPath,
          onFailure = {
            _stringsState.emit(DataState.Failure(it))
          }
        )?.run {
          _stringsState.emit(DataState.Success(this))
        }
    }
  }

  override fun getDimensions() {
    launch(Dispatchers.io) {
      genericRemoteService
        .request<List<StylizedDimensionGroupDataModel>, Unit>(
          method = HttpMethod.Get,
          endpointUrl = globalAppConfigurationState.payloadValue.dimensionResourcesPath,
          onFailure = {
            _dimensionsState.emit(DataState.Failure(it))
          }
        )?.run {
          _dimensionsState.emit(DataState.Success(this))
        }
    }
  }

  override fun getColors() {
    launch(Dispatchers.io) {
      genericRemoteService
        .request<List<StylizedColorGroupDataModel>, Unit>(
          method = HttpMethod.Get,
          endpointUrl = globalAppConfigurationState.payloadValue.colorResourcesPath,
          onFailure = {
            _colorsState.emit(DataState.Failure(it))
          }
        )?.run {
          _colorsState.emit(DataState.Success(this))
        }
    }
  }

  override fun getDrawables() {
    launch(Dispatchers.io) {
      genericRemoteService
        .request<List<StylizedDrawablePathsGroupDataModel>, Unit>(
          method = HttpMethod.Get,
          endpointUrl = globalAppConfigurationState.payloadValue.drawableResourcesConfigurationPath,
          onFailure = {
            _drawablesState.emit(DataState.Failure(it))
          }
        )?.run {
          _drawablesState.emit(DataState.Success(this))
        }
    }
  }

  override fun getDrawable(
    key: Long,
    themeId: Long,
    format: String
  ): Flow<DataState<String>> {
    return flow {
      genericRemoteService
        .request<String, Unit>(
          method = HttpMethod.Get,
          endpointUrl = "${globalAppConfigurationState.payloadValue.drawableResourcesPath}/$format/$key/$themeId.$format",
          onFailure = {
            emit(DataState.Failure(it))
          }
        )?.run {
          emit(DataState.Success(this))
        }
    }
  }

  override fun getDrawable(
    name: String,
    format: String
  ): Flow<DataState<String>> {
    return flow {
      genericRemoteService
        .request<String, Unit>(
          method = HttpMethod.Get,
          endpointUrl = "${globalAppConfigurationState.payloadValue.drawableResourcesPath}/$format/$name.$format",
          onFailure = {
            emit(DataState.Failure(it))
          }
        )?.run {
          emit(DataState.Success(this))
        }
    }
  }

  override fun getExceptions() {
    launch(Dispatchers.io) {
      genericRemoteService
        .request<List<ExceptionDataModel>, Unit>(
          method = HttpMethod.Get,
          endpointUrl = globalAppConfigurationState.payloadValue.exceptionConfigurationPath,
          onFailure = {
            _exceptionsState.emit(DataState.Failure(it))
          }
        )?.run {
          _exceptionsState.emit(DataState.Success(this))
        }
    }
  }


  override fun setAppLocale(language: String) {
    launch {
      genericLocalService
        .put(KEY_APP_LOCALE, language)
    }
  }

  override fun setAppTheme(themeId: Long) {
    launch(Dispatchers.io) {
      genericLocalService
        .put(KEY_APP_THEME, themeId.toString())
    }
  }

  override fun setAppSizeMode(sizeModeId: Long) {
    launch {
      genericLocalService
        .put(KEY_APP_SIZE_MODE, sizeModeId.toString())
    }
  }

  override fun updateGlobalAppConfiguration(
    configuration: GlobalAppConfigurationDataModel,
    resourceConfiguration: GlobalAppConfigurationDataModel
  ) {
    if (_globalAppConfigurationState.value.value is DataState.Failure)
      _globalAppConfigurationState.emit(DataState.Success(resourceConfiguration))
  }

  override fun updateStrings(strings: List<LocalizedStringGroupDataModel>, resourceStrings: List<LocalizedStringGroupDataModel>) {
    launch(Dispatchers.io) {
      _stringAppNameState.emit(strings.extractString(0, appLanguageState.value) ?: resourceStrings.extractString(0, appLanguageState.value)!!)
      _stringLogInState.emit(strings.extractString(1, appLanguageState.value) ?: resourceStrings.extractString(1, appLanguageState.value)!!)
      _stringPhoneNumberState.emit(strings.extractString(2, appLanguageState.value) ?: resourceStrings.extractString(2, appLanguageState.value)!!)
      _stringEnterPhoneNumberState.emit(strings.extractString(3, appLanguageState.value) ?: resourceStrings.extractString(3, appLanguageState.value)!!)
      _stringEmailState.emit(strings.extractString(4, appLanguageState.value) ?: resourceStrings.extractString(4, appLanguageState.value)!!)
      _stringEnterEmailAddressState.emit(strings.extractString(5, appLanguageState.value) ?: resourceStrings.extractString(5, appLanguageState.value)!!)
      _stringPasswordState.emit(strings.extractString(6, appLanguageState.value) ?: resourceStrings.extractString(6, appLanguageState.value)!!)
      _stringEnterPasswordState.emit(strings.extractString(7, appLanguageState.value) ?: resourceStrings.extractString(7, appLanguageState.value)!!)
      _stringCancelState.emit(strings.extractString(8, appLanguageState.value) ?: resourceStrings.extractString(8, appLanguageState.value)!!)
      _stringClearState.emit(strings.extractString(9, appLanguageState.value) ?: resourceStrings.extractString(9, appLanguageState.value)!!)
      _stringLoginAndOrPasswordIncorrectState.emit(strings.extractString(10, appLanguageState.value) ?: resourceStrings.extractString(10, appLanguageState.value)!!)
      _stringPhoneNumberMustBeState.emit(strings.extractString(11, appLanguageState.value) ?: resourceStrings.extractString(11, appLanguageState.value)!!)
      _stringEmailMustBeState.emit(strings.extractString(12, appLanguageState.value) ?: resourceStrings.extractString(12, appLanguageState.value)!!)
      _stringPasswordMustBeState.emit(strings.extractString(13, appLanguageState.value) ?: resourceStrings.extractString(13, appLanguageState.value)!!)
      _stringRepeatPasswordState.emit(strings.extractString(14, appLanguageState.value) ?: resourceStrings.extractString(14, appLanguageState.value)!!)
      _stringPasswordsMustMatchState.emit(strings.extractString(15, appLanguageState.value) ?: resourceStrings.extractString(15, appLanguageState.value)!!)
      _stringFirstNameState.emit(strings.extractString(16, appLanguageState.value) ?: resourceStrings.extractString(16, appLanguageState.value)!!)
      _stringLastNameState.emit(strings.extractString(17, appLanguageState.value) ?: resourceStrings.extractString(17, appLanguageState.value)!!)
      _stringEnterFirstNameState.emit(strings.extractString(18, appLanguageState.value) ?: resourceStrings.extractString(18, appLanguageState.value)!!)
      _stringEnterLastNameState.emit(strings.extractString(19, appLanguageState.value) ?: resourceStrings.extractString(19, appLanguageState.value)!!)
      _stringUserWithThisPhoneNumberIsAlreadyRegisteredState.emit(strings.extractString(20, appLanguageState.value) ?: resourceStrings.extractString(20, appLanguageState.value)!!)
      _stringUserWithThisEmailAddressIsAlreadyRegisteredState.emit(strings.extractString(21, appLanguageState.value) ?: resourceStrings.extractString(21, appLanguageState.value)!!)
      _stringSignUpState.emit(strings.extractString(22, appLanguageState.value) ?: resourceStrings.extractString(22, appLanguageState.value)!!)
      _stringConfirmState.emit(strings.extractString(23, appLanguageState.value) ?: resourceStrings.extractString(23, appLanguageState.value)!!)
      _stringSaleState.emit(strings.extractString(24, appLanguageState.value) ?: resourceStrings.extractString(24, appLanguageState.value)!!)
      _stringReturnState.emit(strings.extractString(25, appLanguageState.value) ?: resourceStrings.extractString(25, appLanguageState.value)!!)
      _stringSupplyState.emit(strings.extractString(26, appLanguageState.value) ?: resourceStrings.extractString(26, appLanguageState.value)!!)
      _stringStockState.emit(strings.extractString(27, appLanguageState.value) ?: resourceStrings.extractString(27, appLanguageState.value)!!)
      _stringMenuState.emit(strings.extractString(28, appLanguageState.value) ?: resourceStrings.extractString(28, appLanguageState.value)!!)
      _stringBackState.emit(strings.extractString(29, appLanguageState.value) ?: resourceStrings.extractString(29, appLanguageState.value)!!)
      _stringAddGoodsItemState.emit(strings.extractString(30, appLanguageState.value) ?: resourceStrings.extractString(30, appLanguageState.value)!!)
      _stringEditGoodsItemState.emit(strings.extractString(31, appLanguageState.value) ?: resourceStrings.extractString(31, appLanguageState.value)!!)
      _stringUserAccountState.emit(strings.extractString(32, appLanguageState.value) ?: resourceStrings.extractString(32, appLanguageState.value)!!)
      _stringGoodsCategoriesState.emit(strings.extractString(33, appLanguageState.value) ?: resourceStrings.extractString(33, appLanguageState.value)!!)
      _stringAddGoodsCategoryState.emit(strings.extractString(34, appLanguageState.value) ?: resourceStrings.extractString(34, appLanguageState.value)!!)
      _stringEditGoodsCategoryState.emit(strings.extractString(35, appLanguageState.value) ?: resourceStrings.extractString(35, appLanguageState.value)!!)
      _stringStoresState.emit(strings.extractString(36, appLanguageState.value) ?: resourceStrings.extractString(36, appLanguageState.value)!!)
      _stringAddStoreState.emit(strings.extractString(37, appLanguageState.value) ?: resourceStrings.extractString(37, appLanguageState.value)!!)
      _stringEditStoreState.emit(strings.extractString(38, appLanguageState.value) ?: resourceStrings.extractString(38, appLanguageState.value)!!)
      _stringSubscriptionState.emit(strings.extractString(39, appLanguageState.value) ?: resourceStrings.extractString(39, appLanguageState.value)!!)
      _stringSubscriptionPlansState.emit(strings.extractString(40, appLanguageState.value) ?: resourceStrings.extractString(40, appLanguageState.value)!!)
      _stringTransactionHistoryState.emit(strings.extractString(41, appLanguageState.value) ?: resourceStrings.extractString(41, appLanguageState.value)!!)
      _stringReceiptState.emit(strings.extractString(42, appLanguageState.value) ?: resourceStrings.extractString(42, appLanguageState.value)!!)
      _stringAnalyticsState.emit(strings.extractString(43, appLanguageState.value) ?: resourceStrings.extractString(43, appLanguageState.value)!!)
      _stringWorkersState.emit(strings.extractString(44, appLanguageState.value) ?: resourceStrings.extractString(44, appLanguageState.value)!!)
      _stringAddWorkerState.emit(strings.extractString(45, appLanguageState.value) ?: resourceStrings.extractString(45, appLanguageState.value)!!)
      _stringEditWorkerState.emit(strings.extractString(46, appLanguageState.value) ?: resourceStrings.extractString(46, appLanguageState.value)!!)
      _stringSuppliersState.emit(strings.extractString(47, appLanguageState.value) ?: resourceStrings.extractString(47, appLanguageState.value)!!)
      _stringAddSupplierState.emit(strings.extractString(48, appLanguageState.value) ?: resourceStrings.extractString(48, appLanguageState.value)!!)
      _stringEditSupplierState.emit(strings.extractString(49, appLanguageState.value) ?: resourceStrings.extractString(49, appLanguageState.value)!!)
      _stringDebtorsState.emit(strings.extractString(50, appLanguageState.value) ?: resourceStrings.extractString(50, appLanguageState.value)!!)
      _stringCloseDebtState.emit(strings.extractString(51, appLanguageState.value) ?: resourceStrings.extractString(51, appLanguageState.value)!!)
      _stringDevicesState.emit(strings.extractString(52, appLanguageState.value) ?: resourceStrings.extractString(52, appLanguageState.value)!!)
      _stringAppLanguageState.emit(strings.extractString(53, appLanguageState.value) ?: resourceStrings.extractString(53, appLanguageState.value)!!)
      _stringAppThemeState.emit(strings.extractString(54, appLanguageState.value) ?: resourceStrings.extractString(54, appLanguageState.value)!!)
      _stringSelectState.emit(strings.extractString(55, appLanguageState.value) ?: resourceStrings.extractString(55, appLanguageState.value)!!)
      _stringUserWithThisPhoneNumberAndEmailAddressIsAlreadyRegisteredState.emit(strings.extractString(56, appLanguageState.value) ?: resourceStrings.extractString(56, appLanguageState.value)!!)
      _stringFirstNameCannotBeEmptyOrJustWhitespacesState.emit(strings.extractString(57, appLanguageState.value) ?: resourceStrings.extractString(57, appLanguageState.value)!!)
      _stringLastNameCannotBeEmptyOrJustWhitespacesState.emit(strings.extractString(58, appLanguageState.value) ?: resourceStrings.extractString(58, appLanguageState.value)!!)
      _stringSystemLanguageState.emit(strings.extractString(59, appLanguageState.value) ?: resourceStrings.extractString(59, appLanguageState.value)!!)
      _stringBluetoothPermissionRequiredState.emit(strings.extractString(60, appLanguageState.value) ?: resourceStrings.extractString(60, appLanguageState.value)!!)
      _stringForSearchAndConnectionToBluetoothBarcodeScannersAndReceiptPrintersState.emit(strings.extractString(61, appLanguageState.value) ?: resourceStrings.extractString(61, appLanguageState.value)!!)
      _stringForSearchAndConnectionToBluetoothBarcodeScannersAndReceiptPrintersYouCanGrantItInAppSettingsState.emit(strings.extractString(62, appLanguageState.value) ?: resourceStrings.extractString(62, appLanguageState.value)!!)
      _stringBluetoothDisabledState.emit(strings.extractString(63, appLanguageState.value) ?: resourceStrings.extractString(63, appLanguageState.value)!!)
      _stringEnableForSearchAndConnectionToBluetoothBarcodeScannersAndReceiptPrintersState.emit(strings.extractString(64, appLanguageState.value) ?: resourceStrings.extractString(64, appLanguageState.value)!!)
      _stringSearchByAnyDataState.emit(strings.extractString(65, appLanguageState.value) ?: resourceStrings.extractString(65, appLanguageState.value)!!)
      _stringListEmptyState.emit(strings.extractString(66, appLanguageState.value) ?: resourceStrings.extractString(66, appLanguageState.value)!!)
      _stringNoMatchesState.emit(strings.extractString(67, appLanguageState.value) ?: resourceStrings.extractString(67, appLanguageState.value)!!)
      _stringNameState.emit(strings.extractString(68, appLanguageState.value) ?: resourceStrings.extractString(68, appLanguageState.value)!!)
      _stringBarcodeState.emit(strings.extractString(69, appLanguageState.value) ?: resourceStrings.extractString(69, appLanguageState.value)!!)
      _stringSupplyPriceState.emit(strings.extractString(70, appLanguageState.value) ?: resourceStrings.extractString(70, appLanguageState.value)!!)
      _stringSalePriceState.emit(strings.extractString(71, appLanguageState.value) ?: resourceStrings.extractString(71, appLanguageState.value)!!)
      _stringReturnPriceState.emit(strings.extractString(72, appLanguageState.value) ?: resourceStrings.extractString(72, appLanguageState.value)!!)
      _stringCategoryState.emit(strings.extractString(73, appLanguageState.value) ?: resourceStrings.extractString(73, appLanguageState.value)!!)
      _stringSupplierState.emit(strings.extractString(74, appLanguageState.value) ?: resourceStrings.extractString(74, appLanguageState.value)!!)
      _stringEnterBarcodeState.emit(strings.extractString(75, appLanguageState.value) ?: resourceStrings.extractString(75, appLanguageState.value)!!)
      _stringEnterNameState.emit(strings.extractString(76, appLanguageState.value) ?: resourceStrings.extractString(76, appLanguageState.value)!!)
      _stringEnterSupplyPriceState.emit(strings.extractString(77, appLanguageState.value) ?: resourceStrings.extractString(77, appLanguageState.value)!!)
      _stringEnterSalePriceState.emit(strings.extractString(78, appLanguageState.value) ?: resourceStrings.extractString(78, appLanguageState.value)!!)
      _stringEnterReturnPriceState.emit(strings.extractString(79, appLanguageState.value) ?: resourceStrings.extractString(79, appLanguageState.value)!!)
      _stringSelectCategoryState.emit(strings.extractString(80, appLanguageState.value) ?: resourceStrings.extractString(80, appLanguageState.value)!!)
      _stringSelectSupplierState.emit(strings.extractString(81, appLanguageState.value) ?: resourceStrings.extractString(81, appLanguageState.value)!!)
      _stringEditState.emit(strings.extractString(82, appLanguageState.value) ?: resourceStrings.extractString(82, appLanguageState.value)!!)
      _stringChangePasswordState.emit(strings.extractString(83, appLanguageState.value) ?: resourceStrings.extractString(83, appLanguageState.value)!!)
      _stringNewPasswordState.emit(strings.extractString(84, appLanguageState.value) ?: resourceStrings.extractString(84, appLanguageState.value)!!)
      _stringEnterNewPasswordState.emit(strings.extractString(85, appLanguageState.value) ?: resourceStrings.extractString(85, appLanguageState.value)!!)
      _stringRepeatNewPasswordState.emit(strings.extractString(86, appLanguageState.value) ?: resourceStrings.extractString(86, appLanguageState.value)!!)
      _stringConfirmationPasswordState.emit(strings.extractString(87, appLanguageState.value) ?: resourceStrings.extractString(87, appLanguageState.value)!!)
      _stringRequiredToEditAccountState.emit(strings.extractString(88, appLanguageState.value) ?: resourceStrings.extractString(88, appLanguageState.value)!!)
      _stringAccountSuccessfullyUpdatedState.emit(strings.extractString(89, appLanguageState.value) ?: resourceStrings.extractString(89, appLanguageState.value)!!)
      _stringLoggingOutInProgressState.emit(strings.extractString(90, appLanguageState.value) ?: resourceStrings.extractString(90, appLanguageState.value)!!)
      _stringSessionTimeExpiredLoggingOutState.emit(strings.extractString(91, appLanguageState.value) ?: resourceStrings.extractString(91, appLanguageState.value)!!)
      _stringAliasState.emit(strings.extractString(92, appLanguageState.value) ?: resourceStrings.extractString(92, appLanguageState.value)!!)
      _stringDescriptionState.emit(strings.extractString(93, appLanguageState.value) ?: resourceStrings.extractString(93, appLanguageState.value)!!)
      _stringEnterAliasState.emit(strings.extractString(94, appLanguageState.value) ?: resourceStrings.extractString(94, appLanguageState.value)!!)
      _stringEnterDescriptionState.emit(strings.extractString(95, appLanguageState.value) ?: resourceStrings.extractString(95, appLanguageState.value)!!)
      _stringOptionalState.emit(strings.extractString(96, appLanguageState.value) ?: resourceStrings.extractString(96, appLanguageState.value)!!)
    }
  }

  override fun updateDrawables(drawables: List<StylizedDrawablePathsGroupDataModel>, resourceDrawables: List<StylizedDrawablePathsGroupDataModel>) {
    launch(Dispatchers.io) {
      _drawablePathAITALogoState.emit(drawables.extractPath(0, appThemeIdState.value) ?: resourceDrawables.extractPath(0, appThemeIdState.value)!!)
      _drawablePathIconPasswordState.emit(drawables.extractPath(1, appThemeIdState.value) ?: resourceDrawables.extractPath(1, appThemeIdState.value)!!)
      _drawablePathIconCancelState.emit(drawables.extractPath(2, appThemeIdState.value) ?: resourceDrawables.extractPath(2, appThemeIdState.value)!!)
      _drawablePathIconEyeHideState.emit(drawables.extractPath(3, appThemeIdState.value) ?: resourceDrawables.extractPath(3, appThemeIdState.value)!!)
      _drawablePathIconEyeShowState.emit(drawables.extractPath(4, appThemeIdState.value) ?: resourceDrawables.extractPath(4, appThemeIdState.value)!!)
      _drawablePathIconEmailState.emit(drawables.extractPath(5, appThemeIdState.value) ?: resourceDrawables.extractPath(5, appThemeIdState.value)!!)
      _drawablePathIconPhoneState.emit(drawables.extractPath(6, appThemeIdState.value) ?: resourceDrawables.extractPath(6, appThemeIdState.value)!!)
      _drawablePathIconExpandMoreState.emit(drawables.extractPath(7, appThemeIdState.value) ?: resourceDrawables.extractPath(7, appThemeIdState.value)!!)
      _drawablePathIconExpandLessState.emit(drawables.extractPath(8, appThemeIdState.value) ?: resourceDrawables.extractPath(8, appThemeIdState.value)!!)
      _drawablePathIconPersonState.emit(drawables.extractPath(9, appThemeIdState.value) ?: resourceDrawables.extractPath(9, appThemeIdState.value)!!)
      _drawablePathIconTransactionSaleState.emit(drawables.extractPath(10, appThemeIdState.value) ?: resourceDrawables.extractPath(10, appThemeIdState.value)!!)
      _drawablePathIconTransactionReturnState.emit(drawables.extractPath(11, appThemeIdState.value) ?: resourceDrawables.extractPath(11, appThemeIdState.value)!!)
      _drawablePathIconTransactionSupplyState.emit(drawables.extractPath(12, appThemeIdState.value) ?: resourceDrawables.extractPath(12, appThemeIdState.value)!!)
      _drawablePathIconStockState.emit(drawables.extractPath(13, appThemeIdState.value) ?: resourceDrawables.extractPath(13, appThemeIdState.value)!!)
      _drawablePathIconMenuState.emit(drawables.extractPath(14, appThemeIdState.value) ?: resourceDrawables.extractPath(14, appThemeIdState.value)!!)
      _drawablePathIconBackArrowState.emit(drawables.extractPath(15, appThemeIdState.value) ?: resourceDrawables.extractPath(15, appThemeIdState.value)!!)
      _drawablePathIconAddState.emit(drawables.extractPath(16, appThemeIdState.value) ?: resourceDrawables.extractPath(16, appThemeIdState.value)!!)
      _drawablePathIconUserAccountState.emit(drawables.extractPath(17, appThemeIdState.value) ?: resourceDrawables.extractPath(17, appThemeIdState.value)!!)
      _drawablePathIconGoodsCategoriesState.emit(drawables.extractPath(18, appThemeIdState.value) ?: resourceDrawables.extractPath(18, appThemeIdState.value)!!)
      _drawablePathIconStoresState.emit(drawables.extractPath(19, appThemeIdState.value) ?: resourceDrawables.extractPath(19, appThemeIdState.value)!!)
      _drawablePathIconTransactionHistoryState.emit(drawables.extractPath(20, appThemeIdState.value) ?: resourceDrawables.extractPath(20, appThemeIdState.value)!!)
      _drawablePathIconAnalyticsState.emit(drawables.extractPath(21, appThemeIdState.value) ?: resourceDrawables.extractPath(21, appThemeIdState.value)!!)
      _drawablePathIconWorkersState.emit(drawables.extractPath(22, appThemeIdState.value) ?: resourceDrawables.extractPath(22, appThemeIdState.value)!!)
      _drawablePathIconSuppliersState.emit(drawables.extractPath(23, appThemeIdState.value) ?: resourceDrawables.extractPath(23, appThemeIdState.value)!!)
      _drawablePathIconDebtorsState.emit(drawables.extractPath(24, appThemeIdState.value) ?: resourceDrawables.extractPath(24, appThemeIdState.value)!!)
      _drawablePathIconDevicesState.emit(drawables.extractPath(25, appThemeIdState.value) ?: resourceDrawables.extractPath(25, appThemeIdState.value)!!)
      _drawablePathIconAppLanguageState.emit(drawables.extractPath(26, appThemeIdState.value) ?: resourceDrawables.extractPath(26, appThemeIdState.value)!!)
      _drawablePathIconAppThemeState.emit(drawables.extractPath(27, appThemeIdState.value) ?: resourceDrawables.extractPath(27, appThemeIdState.value)!!)
      _drawablePathIconCheckState.emit(drawables.extractPath(28, appThemeIdState.value) ?: resourceDrawables.extractPath(28, appThemeIdState.value)!!)
      _drawablePathIconEditState.emit(drawables.extractPath(29, appThemeIdState.value) ?: resourceDrawables.extractPath(29, appThemeIdState.value)!!)
      _drawablePathIconSettingsState.emit(drawables.extractPath(30, appThemeIdState.value) ?: resourceDrawables.extractPath(30, appThemeIdState.value)!!)
      _drawablePathIconSearchState.emit(drawables.extractPath(31, appThemeIdState.value) ?: resourceDrawables.extractPath(31, appThemeIdState.value)!!)
      _drawablePathIconBarcodeCamScannerState.emit(drawables.extractPath(32, appThemeIdState.value) ?: resourceDrawables.extractPath(32, appThemeIdState.value)!!)
      _drawablePathIconDeleteState.emit(drawables.extractPath(33, appThemeIdState.value) ?: resourceDrawables.extractPath(33, appThemeIdState.value)!!)
    }
  }

  override fun updateExceptions(exceptions: List<ExceptionDataModel>, resourceExceptions: List<ExceptionDataModel>) {
    launch(Dispatchers.io) {
      _exceptionMessageUserWithThisPhoneNumberIsAlreadyRegisteredState.emit(exceptions.extractExceptionMessage(0) ?: resourceExceptions.extractExceptionMessage(0)!!)
      _exceptionMessageUserWithThisEmailAddressIsAlreadyRegisteredState.emit(exceptions.extractExceptionMessage(1) ?: resourceExceptions.extractExceptionMessage(1)!!)
      _exceptionMessageUserWithThisPhoneNumberAndEmailAddressIsAlreadyRegisteredState.emit(exceptions.extractExceptionMessage(2) ?: resourceExceptions.extractExceptionMessage(2)!!)
      _exceptionMessageRefreshTokenExpiredState.emit(exceptions.extractExceptionMessage(3) ?: resourceExceptions.extractExceptionMessage(3)!!)
      _exceptionMessageLoginAndOrPasswordIncorrectState.emit(exceptions.extractExceptionMessage(4) ?: resourceExceptions.extractExceptionMessage(4)!!)
      _exceptionMessagePleaseLogInFirstState.emit(exceptions.extractExceptionMessage(5) ?: resourceExceptions.extractExceptionMessage(5)!!)
      _exceptionMessageIncorrectPasswordState.emit(exceptions.extractExceptionMessage(6) ?: resourceExceptions.extractExceptionMessage(6)!!)
    }
  }
}
