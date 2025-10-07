package kz.aita

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kz.aita.compose.navigation.Navigation
import kz.aita.compose.navigation.NavigationScreenModel
import kz.aita.compose.util.toColor
import kz.aita.core.configurationRepository
import kz.aita.core.extractColor
import kz.aita.core.extractExceptionMessage
import kz.aita.core.extractPath
import kz.aita.core.extractString
import kz.aita.core.extractValue
import kz.aita.core.genericLocalService
import kz.aita.core.stockRepository
import kz.aita.core.userRepository
import kz.aita.model.dataModel.GlobalAppConfigurationDataModel
import kz.aita.model.dataModel.GoodsItemDataModel
import kz.aita.model.dataModel.LocalizedStringGroupDataModel
import kz.aita.model.dataModel.StylizedColorGroupDataModel
import kz.aita.model.dataModel.StylizedDimensionGroupDataModel
import kz.aita.model.dataModel.StylizedDrawablePathsGroupDataModel
import kz.aita.model.dataModel.UserAccountDataModel
import kz.aita.model.wrapper.DataState

object AppConfiguration {

  const val KEY_APP_THEME = "key_appTheme"
  const val KEY_APP_LOCALE = "key_appLocale"

  interface StateValues {
    val userAccountState: DataState<UserAccountDataModel>
    val userAccount: UserAccountDataModel?

    val stockState: DataState<List<GoodsItemDataModel>>
    val stock: List<GoodsItemDataModel>?


    val navigationScreensMain: List<NavigationScreenModel>
    val navigationTransactionSaleClientId: Int
    val navigationTransactionReturnClientId: Int
    val navigationTransactionSupplyClientId: Int
    val navigationScreensTransactionSaleLeftClient1: List<NavigationScreenModel>
    val navigationScreensTransactionSaleLeftClient2: List<NavigationScreenModel>
    val navigationScreensTransactionSaleLeftClient3: List<NavigationScreenModel>
    val navigationScreensTransactionSaleLeftClient4: List<NavigationScreenModel>
    val navigationScreensTransactionSaleLeftClient5: List<NavigationScreenModel>
    val navigationScreensTransactionSaleRightClient1: List<NavigationScreenModel>
    val navigationScreensTransactionSaleRightClient2: List<NavigationScreenModel>
    val navigationScreensTransactionSaleRightClient3: List<NavigationScreenModel>
    val navigationScreensTransactionSaleRightClient4: List<NavigationScreenModel>
    val navigationScreensTransactionSaleRightClient5: List<NavigationScreenModel>
    val navigationScreensTransactionReturnLeftClient1: List<NavigationScreenModel>
    val navigationScreensTransactionReturnLeftClient2: List<NavigationScreenModel>
    val navigationScreensTransactionReturnLeftClient3: List<NavigationScreenModel>
    val navigationScreensTransactionReturnLeftClient4: List<NavigationScreenModel>
    val navigationScreensTransactionReturnLeftClient5: List<NavigationScreenModel>
    val navigationScreensTransactionReturnRightClient1: List<NavigationScreenModel>
    val navigationScreensTransactionReturnRightClient2: List<NavigationScreenModel>
    val navigationScreensTransactionReturnRightClient3: List<NavigationScreenModel>
    val navigationScreensTransactionReturnRightClient4: List<NavigationScreenModel>
    val navigationScreensTransactionReturnRightClient5: List<NavigationScreenModel>
    val navigationScreensTransactionSupplyLeftClient1: List<NavigationScreenModel>
    val navigationScreensTransactionSupplyLeftClient2: List<NavigationScreenModel>
    val navigationScreensTransactionSupplyLeftClient3: List<NavigationScreenModel>
    val navigationScreensTransactionSupplyLeftClient4: List<NavigationScreenModel>
    val navigationScreensTransactionSupplyLeftClient5: List<NavigationScreenModel>
    val navigationScreensTransactionSupplyRightClient1: List<NavigationScreenModel>
    val navigationScreensTransactionSupplyRightClient2: List<NavigationScreenModel>
    val navigationScreensTransactionSupplyRightClient3: List<NavigationScreenModel>
    val navigationScreensTransactionSupplyRightClient4: List<NavigationScreenModel>
    val navigationScreensTransactionSupplyRightClient5: List<NavigationScreenModel>
    val navigationScreensStockLeft: List<NavigationScreenModel>
    val navigationScreensStockRight: List<NavigationScreenModel>
    val navigationScreensMenuLeft: List<NavigationScreenModel>
    val navigationScreensMenuRight: List<NavigationScreenModel>

    val navigationScreensUserAuthLeft: List<NavigationScreenModel>
    val navigationScreensUserAuthRight: List<NavigationScreenModel>

    val globalAppConfiguration: GlobalAppConfigurationDataModel
    val strings: List<LocalizedStringGroupDataModel>?
    val dimensions: List<StylizedDimensionGroupDataModel>?
    val colors: List<StylizedColorGroupDataModel>?
    val drawables: List<StylizedDrawablePathsGroupDataModel>?

    val appLocaleLanguage: String
    val appThemeId: Long
    val appSizeModeId: Long

    val stringAppName: String
    val stringLogIn: String
    val stringPhoneNumber: String
    val stringEnterPhoneNumber: String
    val stringEmail: String
    val stringEnterEmailAddress: String
    val stringPassword: String
    val stringEnterPassword: String
    val stringCancel: String
    val stringClear: String
    val stringLoginAndOrPasswordIncorrect: String
    val stringPhoneNumberMustBe: String
    val stringEmailMustBe: String
    val stringPasswordMustBe: String
    val stringRepeatPassword: String
    val stringPasswordsMustMatch: String
    val stringFirstName: String
    val stringLastName: String
    val stringEnterFirstName: String
    val stringEnterLastName: String
    val stringUserWithThisPhoneNumberIsAlreadyRegistered: String
    val stringUserWithThisEmailAddressIsAlreadyRegistered: String
    val stringSignUp: String
    val stringConfirm: String
    val stringSale: String
    val stringReturn: String
    val stringSupply: String
    val stringStock: String
    val stringMenu: String
    val stringBack: String
    val stringAddGoodsItem: String
    val stringEditGoodsItem: String

    val stringUserAccount: String
    val stringGoodsCategories: String
    val stringAddGoodsCategory: String
    val stringEditGoodsCategory: String
    val stringStores: String
    val stringAddStore: String
    val stringEditStore: String
    val stringSubscription: String
    val stringSubscriptionPlans: String
    val stringTransactionHistory: String
    val stringReceipt: String
    val stringAnalytics: String
    val stringWorkers: String
    val stringAddWorker: String
    val stringEditWorker: String
    val stringSuppliers: String
    val stringAddSupplier: String
    val stringEditSupplier: String
    val stringDebtors: String
    val stringCloseDebt: String
    val stringDevices: String
    val stringAppLanguage: String
    val stringAppTheme: String
    val stringSelect: String
    val stringUserWithThisPhoneNumberAndEmailAddressIsAlreadyRegistered: String
    val stringFirstNameCannotBeEmptyOrJustWhitespaces: String
    val stringLastNameCannotBeEmptyOrJustWhitespaces: String
    val stringSystemLanguage: String
    val stringBluetoothPermissionRequired: String
    val stringForSearchAndConnectionToBluetoothBarcodeScannersAndReceiptPrinters: String
    val stringForSearchAndConnectionToBluetoothBarcodeScannersAndReceiptPrintersYouCanGrantItInAppSettings: String
    val stringBluetoothDisabled: String
    val stringEnableForSearchAndConnectionToBluetoothBarcodeScannersAndReceiptPrinters: String
    val stringSearchByAnyData: String
    val stringListEmpty: String
    val stringNoMatches: String
    val stringName: String
    val stringBarcode: String
    val stringSupplyPriceState: String
    val stringSalePriceState: String
    val stringReturnPriceState: String
    val stringCategoryState: String
    val stringSupplierState: String
    val stringEnterName: String
    val stringEnterBarcode: String
    val stringEnterSupplyPriceState: String
    val stringEnterSalePriceState: String
    val stringEnterReturnPriceState: String
    val stringEnterCategoryState: String
    val stringEnterSupplierState: String

    val screenWidth: Dp
    val screenHeight: Dp
    val wideScreenMinWidth: Float
    val boundWidgetWidth: Dp

    val isNarrowScreen: Boolean

    val textSize: TextUnit
    val titleTextSize: TextUnit
    val smallTextSize: TextUnit
    val accentTextSize: TextUnit

    val focusedBorderWidth: Dp
    val unfocusedBorderWidth: Dp
    val cornerRadius: Dp
    val iconSize: Dp
    val textFieldHeightMultiplierRelativeToTextSize: Float
    val textFieldHeight: Dp
    val textFieldIconPadding: Dp

    val AccentColor: Color
    val BackgroundColor: Color
    val TextColor: Color
    val AccentTextColor: Color
    val PlaceholderTextColor: Color
    val DisabledColor: Color
    val ErrorColor: Color

    val IconTintColor: Color
    val OkayColor: Color
    val BorderlineBadColor: Color

    val drawablePathAITALogo: String
    val drawablePathIconPassword: String
    val drawablePathIconCancel: String
    val drawablePathIconEyeHide: String
    val drawablePathIconEyeShow: String

    val drawablePathIconEmail: String
    val drawablePathIconPhone: String
    val drawablePathIconExpandMore: String
    val drawablePathIconExpandLess: String
    val drawablePathIconPerson: String
    val drawablePathIconTransactionSale: String
    val drawablePathIconTransactionReturn: String
    val drawablePathIconTransactionSupply: String
    val drawablePathIconStock: String
    val drawablePathIconMenu: String
    val drawablePathIconBackArrow: String
    val drawablePathIconAdd: String
    val drawablePathIconUserAccount: String
    val drawablePathIconGoodsCategories: String
    val drawablePathIconStores: String
    val drawablePathIconTransactionHistory: String
    val drawablePathIconAnalytics: String
    val drawablePathIconWorkers: String
    val drawablePathIconSuppliers: String
    val drawablePathIconDebtors: String
    val drawablePathIconDevices: String
    val drawablePathIconAppLanguage: String
    val drawablePathIconAppTheme: String
    val drawablePathIconCheck: String
    val drawablePathIconCreate: String
    val drawablePathIconSettings: String
    val drawablePathIconSearch: String
    val drawablePathIconBarcodeCamScanner: String
    val drawablePathIconDelete: String


    val exceptionMessageUserWithThisPhoneNumberIsAlreadyRegistered: String
    val exceptionMessageUserWithThisEmailAddressIsAlreadyRegistered: String
    val exceptionMessageUserWithThisPhoneNumberAndEmailAddressIsAlreadyRegistered: String
  }

  private val _appLocaleLanguageState = MutableStateFlow("system")
  private val _appThemeIdState = MutableStateFlow(0L)
  private val _appSizeModeIdState = MutableStateFlow(0L)

  private val _stringAppNameState = MutableStateFlow("AITA")
  private val _stringLogInState = MutableStateFlow("Log In")
  private val _stringPhoneNumberState = MutableStateFlow("Phone number")
  private val _stringEnterPhoneNumberState = MutableStateFlow("Enter phone number")
  private val _stringEmailState = MutableStateFlow("Email")
  private val _stringEnterEmailAddressState = MutableStateFlow("Enter email address")
  private val _stringPasswordState = MutableStateFlow("Password")
  private val _stringEnterPasswordState = MutableStateFlow("Enter password")
  private val _stringCancelState = MutableStateFlow("Cancel")
  private val _stringClearState = MutableStateFlow("Clear")
  private val _stringLoginAndOrPasswordIncorrectState = MutableStateFlow("Login and/or password incorrect")
  private val _stringPhoneNumberMustBeState = MutableStateFlow("Incorrect phone number length")
  private val _stringEmailMustBeState = MutableStateFlow("Incorrect email address format")
  private val _stringPasswordMustBeState = MutableStateFlow("Password must be 8 or more symbols long")
  private val _stringRepeatPasswordState = MutableStateFlow("Repeat password")
  private val _stringPasswordsMustMatchState = MutableStateFlow("Passwords must match")
  private val _stringFirstNameState = MutableStateFlow("First name")
  private val _stringLastNameState = MutableStateFlow("Last name")
  private val _stringEnterFirstNameState = MutableStateFlow("Enter first name")
  private val _stringEnterLastNameState = MutableStateFlow("Enter last name")
  private val _stringUserWithThisPhoneNumberIsAlreadyRegisteredState = MutableStateFlow("User with this phone number is already registered")
  private val _stringUserWithThisEmailAddressIsAlreadyRegisteredState = MutableStateFlow("User with this email address is already registered")
  private val _stringSignUpState = MutableStateFlow("Sign Up")
  private val _stringConfirmState = MutableStateFlow("Confirm")
  private val _stringSaleState = MutableStateFlow("Sale")
  private val _stringReturnState = MutableStateFlow("Return")
  private val _stringSupplyState = MutableStateFlow("Supply")
  private val _stringStockState = MutableStateFlow("Stock")
  private val _stringMenuState = MutableStateFlow("Menu")
  private val _stringBackState = MutableStateFlow("Back")
  private val _stringAddGoodsItemState = MutableStateFlow("Add goods item")
  private val _stringEditGoodsItemState = MutableStateFlow("Edit goods item")
  private val _stringUserAccountState = MutableStateFlow("User account")
  private val _stringGoodsCategoriesState = MutableStateFlow("Goods categories")
  private val _stringAddGoodsCategoryState = MutableStateFlow("Add goods category")
  private val _stringEditGoodsCategoryState = MutableStateFlow("Edit goods category")
  private val _stringStoresState = MutableStateFlow("Stores")
  private val _stringAddStoreState = MutableStateFlow("Add store")
  private val _stringEditStoreState = MutableStateFlow("Edit store")
  private val _stringSubscriptionState = MutableStateFlow("Subscription")
  private val _stringSubscriptionPlansState = MutableStateFlow("Subscription plans")
  private val _stringTransactionHistoryState = MutableStateFlow("Transaction history")
  private val _stringReceiptState = MutableStateFlow("Receipt")
  private val _stringAnalyticsState = MutableStateFlow("Analytics")
  private val _stringWorkersState = MutableStateFlow("Workers")
  private val _stringAddWorkerState = MutableStateFlow("Add worker")
  private val _stringEditWorkerState = MutableStateFlow("Edit worker")
  private val _stringSuppliersState = MutableStateFlow("Suppliers")
  private val _stringAddSupplierState = MutableStateFlow("Add supplier")
  private val _stringEditSupplierState = MutableStateFlow("Edit supplier")
  private val _stringDebtorsState = MutableStateFlow("Debtors")
  private val _stringCloseDebtState = MutableStateFlow("Close debt")
  private val _stringDevicesState = MutableStateFlow("Devices")
  private val _stringAppLanguageState = MutableStateFlow("App language")
  private val _stringAppThemeState = MutableStateFlow("App theme")
  private val _stringSelectState = MutableStateFlow("Select")

  private val _stringUserWithThisPhoneNumberAndEmailAddressIsAlreadyRegisteredState = MutableStateFlow("User with this phone number and email address is already registered")
  private val _stringFirstNameCannotBeEmptyOrJustWhitespacesState = MutableStateFlow("First name cannot be empty or just whitespaces")
  private val _stringLastNameCannotBeEmptyOrJustWhitespacesState = MutableStateFlow("Last cannot be empty or just whitespaces")
  private val _stringSystemLanguageState = MutableStateFlow("System language")

  private val _stringBluetoothPermissionRequiredState = MutableStateFlow("Bluetooth permission required")
  private val _stringForSearchAndConnectionToBluetoothBarcodeScannersAndReceiptPrintersState = MutableStateFlow("For search and connection to Bluetooth barcode scanners and receipt printers")
  private val _stringForSearchAndConnectionToBluetoothBarcodeScannersAndReceiptPrintersYouCanGrantItInAppSettingsState = MutableStateFlow("For search and connection to Bluetooth barcode scanners and receipt printers. You can grant it in app settings")
  private val _stringBluetoothDisabledState = MutableStateFlow("Bluetooth disabled")
  private val _stringEnableForSearchAndConnectionToBluetoothBarcodeScannersAndReceiptPrintersState = MutableStateFlow("Enable for search and connection to Bluetooth barcode scanners and receipt printers")
  private val _stringSearchByAnyDataState = MutableStateFlow("Search by any data")
  private val _stringListEmptyState = MutableStateFlow("List empty")
  private val _stringNoMatchesState = MutableStateFlow("No matches")
  private val _stringNameState = MutableStateFlow("Name")
  private val _stringBarcodeState = MutableStateFlow("Barcode")
  private val _stringSupplyPriceState = MutableStateFlow("Supply price")
  private val _stringSalePriceState = MutableStateFlow("Sale price")
  private val _stringReturnPriceState = MutableStateFlow("Return price")
  private val _stringCategoryState = MutableStateFlow("Category")
  private val _stringSupplierState  = MutableStateFlow("Supplier")
  private val _stringEnterNameState = MutableStateFlow("Enter name")
  private val _stringEnterBarcodeState = MutableStateFlow("Enter barcode")
  private val _stringEnterSupplyPriceState = MutableStateFlow("Enter supply price")
  private val _stringEnterSalePriceState = MutableStateFlow("Enter sale price")
  private val _stringEnterReturnPriceState = MutableStateFlow("Enter return price")
  private val _stringSelectCategoryState = MutableStateFlow("Select category")
  private val _stringSelectSupplierState  = MutableStateFlow("Select supplier")

  private val _screenWidthState = MutableStateFlow(0f.dp)
  private val _screenHeightState = MutableStateFlow(0f.dp)
  private val _wideScreenMinWidthState = MutableStateFlow(600f)
  private val _boundWidgetWidthState = MutableStateFlow(280f.dp)

  private val _isNarrowScreenState = MutableStateFlow(false)

  private val _textSizeState = MutableStateFlow(14.sp)
  private val _titleTextSizeState = MutableStateFlow(20.sp)
  private val _accentTextSizeState = MutableStateFlow(16.sp)
  private val _smallTextSizeState = MutableStateFlow(12.sp)
  private val _focusedBorderWidthState = MutableStateFlow(1.dp)
  private val _unfocusedBorderWidthState = MutableStateFlow(0.5.dp)
  private val _cornerRadiusState = MutableStateFlow(14.dp)
  private val _iconSizeState = MutableStateFlow(24.dp)
  private val _textFieldHeightMultiplierRelativeToTextSizeState = MutableStateFlow(2.6f)
  private val _textFieldHeightState = MutableStateFlow(((_textSizeState.value.value * _textFieldHeightMultiplierRelativeToTextSizeState.value)).dp)
  private val _textFieldIconPaddingState = MutableStateFlow((9.dp))

  private val _AccentColorState = MutableStateFlow(Color(0xffffba24))
  private val _BackgroundColorState = MutableStateFlow(Color(0xffffffff))
  private val _TextColorState = MutableStateFlow(Color(0xffffffff))
  private val _AccentTextColorState = MutableStateFlow(Color(0xffffffff))
  private val _PlaceholderTextColorState = MutableStateFlow(Color(0xaa000000))
  private val _DisabledColorState = MutableStateFlow(Color(0xffa7a7a7))
  private val _ErrorColorState = MutableStateFlow(Color(0xffff0000))
  private val _IconTintColorState = MutableStateFlow(Color(0xff000000))

  private val _OkayColorState = MutableStateFlow(Color(0xff6bb522))
  private val _BorderlineBadColorState = MutableStateFlow(Color(0xffffa500))

  private val _drawablePathAITALogoState = MutableStateFlow("svg/0_0.svg")
  private val _drawablePathIconPasswordState = MutableStateFlow("svg/1_0.svg")
  private val _drawablePathIconCancelState = MutableStateFlow("svg/2_0.svg")
  private val _drawablePathIconEyeHideState = MutableStateFlow("svg/3_0.svg")
  private val _drawablePathIconEyeShowState = MutableStateFlow("svg/4_0.svg")
  private val _drawablePathIconEmailState = MutableStateFlow("svg/5_0.svg")
  private val _drawablePathIconPhoneState = MutableStateFlow("svg/6_0.svg")
  private val _drawablePathIconExpandMoreState = MutableStateFlow("svg/7_0.svg")
  private val _drawablePathIconExpandLessState = MutableStateFlow("svg/8_0.svg")
  private val _drawablePathIconPersonState = MutableStateFlow("svg/9_0.svg")
  private val _drawablePathIconTransactionSaleState = MutableStateFlow("svg/10_0.svg")
  private val _drawablePathIconTransactionReturnState = MutableStateFlow("svg/11_0.svg")
  private val _drawablePathIconTransactionSupplyState = MutableStateFlow("svg/12_0.svg")
  private val _drawablePathIconStockState = MutableStateFlow("svg/13_0.svg")
  private val _drawablePathIconMenuState = MutableStateFlow("svg/14_0.svg")
  private val _drawablePathIconBackArrowState = MutableStateFlow("svg/15_0.svg")
  private val _drawablePathIconAddState = MutableStateFlow("svg/16_0.svg")
  private val _drawablePathIconUserAccountState = MutableStateFlow("svg/17_0.svg")
  private val _drawablePathIconGoodsCategoriesState = MutableStateFlow("svg/18_0.svg")
  private val _drawablePathIconStoresState = MutableStateFlow("svg/19_0.svg")
  private val _drawablePathIconTransactionHistoryState = MutableStateFlow("svg/20_0.svg")
  private val _drawablePathIconAnalyticsState = MutableStateFlow("svg/21_0.svg")
  private val _drawablePathIconWorkersState = MutableStateFlow("svg/22_0.svg")
  private val _drawablePathIconSuppliersState = MutableStateFlow("svg/23_0.svg")
  private val _drawablePathIconDebtorsState = MutableStateFlow("svg/24_0.svg")
  private val _drawablePathIconDevicesState = MutableStateFlow("svg/25_0.svg")
  private val _drawablePathIconAppLanguageState = MutableStateFlow("svg/26_0.svg")
  private val _drawablePathIconAppThemeState = MutableStateFlow("svg/27_0.svg")
  private val _drawablePathIconCheckState = MutableStateFlow("svg/28_0.svg")
  private val _drawablePathIconCreateState = MutableStateFlow("svg/29_0.svg")
  private val _drawablePathIconSettingsState = MutableStateFlow("svg/30_0.svg")
  private val _drawablePathIconSearchState = MutableStateFlow("svg/31_0.svg")
  private val _drawablePathIconBarcodeCamScannerState = MutableStateFlow("svg/32_0.svg")
  private val _drawablePathIconDeleteState = MutableStateFlow("svg/33_0.svg")

  private val _exceptionMessageUserWithThisPhoneNumberIsAlreadyRegistered = MutableStateFlow("User with this phone number is already registered")
  private val _exceptionMessageUserWithThisEmailAddressIsAlreadyRegistered = MutableStateFlow("User with this email address is already registered")
  private val _exceptionMessageUserWithThisPhoneNumberAndEmailAddressIsAlreadyRegistered = MutableStateFlow("User with this phone number and email address is already registered")

  lateinit var stateValues: StateValues

  lateinit var coroutineScope: CoroutineScope

  suspend fun setAppTheme(themeId: Long) {
    genericLocalService
      .put(KEY_APP_THEME, themeId.toString())
  }

  suspend fun setAppLocale(language: String) {
    genericLocalService
      .put(KEY_APP_LOCALE, language)
  }

  @Composable
  operator fun invoke(
    content: @Composable AppConfiguration.() -> Unit,
    vararg keys: Any
  ) {
    stateValues = object: StateValues {
      override val userAccountState: DataState<UserAccountDataModel> by userRepository.userAccountState.value.collectAsState()
      override val userAccount: UserAccountDataModel? by userRepository.userAccountState.payload.collectAsState()
      override val stockState: DataState<List<GoodsItemDataModel>> by stockRepository.stockState.value.collectAsState()
      override val stock: List<GoodsItemDataModel>? by stockRepository.stockState.payload.collectAsState()

      override val navigationScreensMain: List<NavigationScreenModel> by Navigation.Main.collectAsState()
      override val navigationTransactionSaleClientId: Int by Navigation.TransactionSale.ClientId.collectAsState()
      override val navigationTransactionReturnClientId: Int by Navigation.TransactionReturn.ClientId.collectAsState()
      override val navigationTransactionSupplyClientId: Int by Navigation.TransactionSupply.ClientId.collectAsState()

      override val navigationScreensTransactionSaleLeftClient1: List<NavigationScreenModel> by Navigation.TransactionSale.LeftClient1.collectAsState()
      override val navigationScreensTransactionSaleLeftClient2: List<NavigationScreenModel> by Navigation.TransactionSale.LeftClient2.collectAsState()
      override val navigationScreensTransactionSaleLeftClient3: List<NavigationScreenModel> by Navigation.TransactionSale.LeftClient3.collectAsState()
      override val navigationScreensTransactionSaleLeftClient4: List<NavigationScreenModel> by Navigation.TransactionSale.LeftClient4.collectAsState()
      override val navigationScreensTransactionSaleLeftClient5: List<NavigationScreenModel> by Navigation.TransactionSale.LeftClient5.collectAsState()

      override val navigationScreensTransactionSaleRightClient1: List<NavigationScreenModel> by Navigation.TransactionSale.RightClient1.collectAsState()
      override val navigationScreensTransactionSaleRightClient2: List<NavigationScreenModel> by Navigation.TransactionSale.RightClient2.collectAsState()
      override val navigationScreensTransactionSaleRightClient3: List<NavigationScreenModel> by Navigation.TransactionSale.RightClient3.collectAsState()
      override val navigationScreensTransactionSaleRightClient4: List<NavigationScreenModel> by Navigation.TransactionSale.RightClient4.collectAsState()
      override val navigationScreensTransactionSaleRightClient5: List<NavigationScreenModel> by Navigation.TransactionSale.RightClient5.collectAsState()

      override val navigationScreensTransactionReturnLeftClient1: List<NavigationScreenModel> by Navigation.TransactionReturn.LeftClient1.collectAsState()
      override val navigationScreensTransactionReturnLeftClient2: List<NavigationScreenModel> by Navigation.TransactionReturn.LeftClient2.collectAsState()
      override val navigationScreensTransactionReturnLeftClient3: List<NavigationScreenModel> by Navigation.TransactionReturn.LeftClient3.collectAsState()
      override val navigationScreensTransactionReturnLeftClient4: List<NavigationScreenModel> by Navigation.TransactionReturn.LeftClient4.collectAsState()
      override val navigationScreensTransactionReturnLeftClient5: List<NavigationScreenModel> by Navigation.TransactionReturn.LeftClient5.collectAsState()

      override val navigationScreensTransactionReturnRightClient1: List<NavigationScreenModel> by Navigation.TransactionReturn.RightClient1.collectAsState()
      override val navigationScreensTransactionReturnRightClient2: List<NavigationScreenModel> by Navigation.TransactionReturn.RightClient2.collectAsState()
      override val navigationScreensTransactionReturnRightClient3: List<NavigationScreenModel> by Navigation.TransactionReturn.RightClient3.collectAsState()
      override val navigationScreensTransactionReturnRightClient4: List<NavigationScreenModel> by Navigation.TransactionReturn.RightClient4.collectAsState()
      override val navigationScreensTransactionReturnRightClient5: List<NavigationScreenModel> by Navigation.TransactionReturn.RightClient5.collectAsState()

      override val navigationScreensTransactionSupplyLeftClient1: List<NavigationScreenModel> by Navigation.TransactionSupply.LeftClient1.collectAsState()
      override val navigationScreensTransactionSupplyLeftClient2: List<NavigationScreenModel> by Navigation.TransactionSupply.LeftClient2.collectAsState()
      override val navigationScreensTransactionSupplyLeftClient3: List<NavigationScreenModel> by Navigation.TransactionSupply.LeftClient3.collectAsState()
      override val navigationScreensTransactionSupplyLeftClient4: List<NavigationScreenModel> by Navigation.TransactionSupply.LeftClient4.collectAsState()
      override val navigationScreensTransactionSupplyLeftClient5: List<NavigationScreenModel> by Navigation.TransactionSupply.LeftClient5.collectAsState()

      override val navigationScreensTransactionSupplyRightClient1: List<NavigationScreenModel> by Navigation.TransactionSupply.RightClient1.collectAsState()
      override val navigationScreensTransactionSupplyRightClient2: List<NavigationScreenModel> by Navigation.TransactionSupply.RightClient2.collectAsState()
      override val navigationScreensTransactionSupplyRightClient3: List<NavigationScreenModel> by Navigation.TransactionSupply.RightClient3.collectAsState()
      override val navigationScreensTransactionSupplyRightClient4: List<NavigationScreenModel> by Navigation.TransactionSupply.RightClient4.collectAsState()
      override val navigationScreensTransactionSupplyRightClient5: List<NavigationScreenModel> by Navigation.TransactionSupply.RightClient5.collectAsState()

      override val navigationScreensStockLeft: List<NavigationScreenModel> by Navigation.Stock.Left.collectAsState()
      override val navigationScreensStockRight: List<NavigationScreenModel> by Navigation.Stock.Right.collectAsState()

      override val navigationScreensMenuLeft: List<NavigationScreenModel> by Navigation.Menu.Left.collectAsState()
      override val navigationScreensMenuRight: List<NavigationScreenModel> by Navigation.Menu.Right.collectAsState()
      override val navigationScreensUserAuthLeft: List<NavigationScreenModel> by Navigation.UserAuth.Left.collectAsState()
      override val navigationScreensUserAuthRight: List<NavigationScreenModel> by Navigation.UserAuth.Right.collectAsState()

      override val globalAppConfiguration: GlobalAppConfigurationDataModel by configurationRepository.globalAppConfigurationState.payload.collectAsState()
      override val strings: List<LocalizedStringGroupDataModel>? by configurationRepository.stringsState.payload.collectAsState()
      override val dimensions: List<StylizedDimensionGroupDataModel>? by configurationRepository.dimensionsState.payload.collectAsState()
      override val colors: List<StylizedColorGroupDataModel>? by configurationRepository.colorsState.payload.collectAsState()
      override val drawables: List<StylizedDrawablePathsGroupDataModel>? by configurationRepository.drawablesState.payload.collectAsState()

      override val appLocaleLanguage: String by _appLocaleLanguageState.collectAsState()
      override val appThemeId: Long by _appThemeIdState.collectAsState()
      override val appSizeModeId: Long by _appSizeModeIdState.collectAsState()

      override val stringAppName: String by _stringAppNameState.collectAsState()
      override val stringLogIn: String by _stringLogInState.collectAsState()
      override val stringPhoneNumber: String by _stringPhoneNumberState.collectAsState()
      override val stringEnterPhoneNumber: String by _stringEnterPhoneNumberState.collectAsState()
      override val stringEmail: String by _stringEmailState.collectAsState()
      override val stringEnterEmailAddress: String by _stringEnterEmailAddressState.collectAsState()
      override val stringPassword: String by _stringPasswordState.collectAsState()
      override val stringEnterPassword: String by _stringEnterPasswordState.collectAsState()
      override val stringCancel: String by _stringCancelState.collectAsState()
      override val stringClear: String by _stringClearState.collectAsState()
      override val stringLoginAndOrPasswordIncorrect: String by _stringLoginAndOrPasswordIncorrectState.collectAsState()
      override val stringPhoneNumberMustBe: String by _stringPhoneNumberMustBeState.collectAsState()
      override val stringEmailMustBe: String by _stringEmailMustBeState.collectAsState()
      override val stringPasswordMustBe: String by _stringPasswordMustBeState.collectAsState()
      override val stringRepeatPassword: String by _stringRepeatPasswordState.collectAsState()
      override val stringPasswordsMustMatch: String by _stringPasswordsMustMatchState.collectAsState()
      override val stringFirstName: String by _stringFirstNameState.collectAsState()
      override val stringLastName: String by _stringLastNameState.collectAsState()
      override val stringEnterFirstName: String by _stringEnterFirstNameState.collectAsState()
      override val stringEnterLastName: String by _stringEnterLastNameState.collectAsState()
      override val stringUserWithThisPhoneNumberIsAlreadyRegistered: String by _stringUserWithThisPhoneNumberIsAlreadyRegisteredState.collectAsState()
      override val stringUserWithThisEmailAddressIsAlreadyRegistered: String by _stringUserWithThisEmailAddressIsAlreadyRegisteredState.collectAsState()
      override val stringSignUp: String by _stringSignUpState.collectAsState()
      override val stringConfirm: String by _stringConfirmState.collectAsState()
      override val stringSale: String by _stringSaleState.collectAsState()
      override val stringReturn: String by _stringReturnState.collectAsState()
      override val stringSupply: String by _stringSupplyState.collectAsState()
      override val stringStock: String by _stringStockState.collectAsState()
      override val stringMenu: String by _stringMenuState.collectAsState()
      override val stringBack: String by _stringBackState.collectAsState()
      override val stringAddGoodsItem: String by _stringAddGoodsItemState.collectAsState()
      override val stringEditGoodsItem: String by _stringEditGoodsItemState.collectAsState()
      override val stringUserAccount: String by _stringUserAccountState.collectAsState()
      override val stringGoodsCategories: String by _stringGoodsCategoriesState.collectAsState()
      override val stringAddGoodsCategory: String by _stringAddGoodsCategoryState.collectAsState()
      override val stringEditGoodsCategory: String by _stringEditGoodsCategoryState.collectAsState()
      override val stringStores: String by _stringStoresState.collectAsState()
      override val stringAddStore: String by _stringAddStoreState.collectAsState()
      override val stringEditStore: String by _stringEditStoreState.collectAsState()
      override val stringSubscription: String by _stringSubscriptionState.collectAsState()
      override val stringSubscriptionPlans: String by _stringSubscriptionPlansState.collectAsState()
      override val stringTransactionHistory: String by _stringTransactionHistoryState.collectAsState()
      override val stringReceipt: String by _stringReceiptState.collectAsState()
      override val stringAnalytics: String by _stringAnalyticsState.collectAsState()
      override val stringWorkers: String by _stringWorkersState.collectAsState()
      override val stringAddWorker: String by _stringAddWorkerState.collectAsState()
      override val stringEditWorker: String by _stringEditWorkerState.collectAsState()
      override val stringSuppliers: String by _stringSuppliersState.collectAsState()
      override val stringAddSupplier: String by _stringAddSupplierState.collectAsState()
      override val stringEditSupplier: String by _stringEditSupplierState.collectAsState()
      override val stringDebtors: String by _stringDebtorsState.collectAsState()
      override val stringCloseDebt: String by _stringCloseDebtState.collectAsState()
      override val stringDevices: String by _stringDevicesState.collectAsState()
      override val stringAppLanguage: String by _stringAppLanguageState.collectAsState()
      override val stringAppTheme: String by _stringAppThemeState.collectAsState()
      override val stringSelect: String by _stringSelectState.collectAsState()
      override val stringUserWithThisPhoneNumberAndEmailAddressIsAlreadyRegistered: String by _stringUserWithThisPhoneNumberAndEmailAddressIsAlreadyRegisteredState.collectAsState()
      override val stringFirstNameCannotBeEmptyOrJustWhitespaces: String by _stringFirstNameCannotBeEmptyOrJustWhitespacesState.collectAsState()
      override val stringLastNameCannotBeEmptyOrJustWhitespaces: String by _stringLastNameCannotBeEmptyOrJustWhitespacesState.collectAsState()
      override val stringSystemLanguage: String by _stringSystemLanguageState.collectAsState()
      override val stringBluetoothPermissionRequired: String by _stringBluetoothPermissionRequiredState.collectAsState()
      override val stringForSearchAndConnectionToBluetoothBarcodeScannersAndReceiptPrinters: String by _stringForSearchAndConnectionToBluetoothBarcodeScannersAndReceiptPrintersState.collectAsState()
      override val stringForSearchAndConnectionToBluetoothBarcodeScannersAndReceiptPrintersYouCanGrantItInAppSettings: String by _stringForSearchAndConnectionToBluetoothBarcodeScannersAndReceiptPrintersYouCanGrantItInAppSettingsState.collectAsState()
      override val stringBluetoothDisabled: String by _stringBluetoothDisabledState.collectAsState()
      override val stringEnableForSearchAndConnectionToBluetoothBarcodeScannersAndReceiptPrinters: String by _stringEnableForSearchAndConnectionToBluetoothBarcodeScannersAndReceiptPrintersState.collectAsState()
      override val stringSearchByAnyData: String by _stringSearchByAnyDataState.collectAsState()
      override val stringListEmpty: String by _stringListEmptyState.collectAsState()
      override val stringNoMatches: String by _stringNoMatchesState.collectAsState()
      override val stringName: String by _stringNameState.collectAsState()
      override val stringBarcode: String by _stringBarcodeState.collectAsState()
      override val stringSupplyPriceState: String by _stringSupplyPriceState.collectAsState()
      override val stringSalePriceState: String by _stringSalePriceState.collectAsState()
      override val stringReturnPriceState: String by _stringReturnPriceState.collectAsState()
      override val stringCategoryState: String by _stringCategoryState.collectAsState()
      override val stringSupplierState: String by _stringSupplierState.collectAsState()
      override val stringEnterName: String by _stringEnterNameState.collectAsState()
      override val stringEnterBarcode: String by _stringEnterBarcodeState.collectAsState()
      override val stringEnterSupplyPriceState: String by _stringEnterSupplyPriceState.collectAsState()
      override val stringEnterSalePriceState: String by _stringEnterSalePriceState.collectAsState()
      override val stringEnterReturnPriceState: String by _stringEnterReturnPriceState.collectAsState()
      override val stringEnterCategoryState: String by _stringSelectCategoryState.collectAsState()
      override val stringEnterSupplierState: String by _stringSelectSupplierState.collectAsState()

      override val screenWidth: Dp by _screenWidthState.collectAsState()
      override val screenHeight: Dp by _screenHeightState.collectAsState()
      override val wideScreenMinWidth: Float by _wideScreenMinWidthState.collectAsState()
      override val boundWidgetWidth: Dp by _boundWidgetWidthState.collectAsState()
      override val isNarrowScreen: Boolean by _isNarrowScreenState.collectAsState()
      override val textSize: TextUnit by _textSizeState.collectAsState()
      override val titleTextSize: TextUnit by _titleTextSizeState.collectAsState()
      override val accentTextSize: TextUnit by _accentTextSizeState.collectAsState()
      override val smallTextSize: TextUnit by _smallTextSizeState.collectAsState()
      override val focusedBorderWidth: Dp by _focusedBorderWidthState.collectAsState()
      override val unfocusedBorderWidth: Dp by _unfocusedBorderWidthState.collectAsState()
      override val cornerRadius: Dp by _cornerRadiusState.collectAsState()
      override val iconSize: Dp by _iconSizeState.collectAsState()
      override val textFieldHeightMultiplierRelativeToTextSize: Float by _textFieldHeightMultiplierRelativeToTextSizeState.collectAsState()
      override val textFieldHeight: Dp by _textFieldHeightState.collectAsState()
      override val textFieldIconPadding: Dp by _textFieldIconPaddingState.collectAsState()

      override val AccentColor: Color by _AccentColorState.collectAsState()
      override val BackgroundColor: Color by _BackgroundColorState.collectAsState()
      override val TextColor: Color by _TextColorState.collectAsState()
      override val AccentTextColor: Color by _AccentTextColorState.collectAsState()
      override val PlaceholderTextColor: Color by _PlaceholderTextColorState.collectAsState()
      override val DisabledColor: Color by _DisabledColorState.collectAsState()
      override val ErrorColor: Color by _ErrorColorState.collectAsState()
      override val IconTintColor: Color by _IconTintColorState.collectAsState()
      override val OkayColor: Color by _OkayColorState.collectAsState()
      override val BorderlineBadColor: Color by _BorderlineBadColorState.collectAsState()

      override val drawablePathAITALogo: String by _drawablePathAITALogoState.collectAsState()
      override val drawablePathIconPassword: String by _drawablePathIconPasswordState.collectAsState()
      override val drawablePathIconCancel: String by _drawablePathIconCancelState.collectAsState()
      override val drawablePathIconEyeHide: String by _drawablePathIconEyeHideState.collectAsState()
      override val drawablePathIconEyeShow: String by _drawablePathIconEyeShowState.collectAsState()
      override val drawablePathIconEmail: String by _drawablePathIconEmailState.collectAsState()
      override val drawablePathIconPhone: String by _drawablePathIconPhoneState.collectAsState()
      override val drawablePathIconExpandMore: String by _drawablePathIconExpandMoreState.collectAsState()
      override val drawablePathIconExpandLess: String by _drawablePathIconExpandLessState.collectAsState()
      override val drawablePathIconPerson: String by _drawablePathIconPersonState.collectAsState()
      override val drawablePathIconTransactionSale: String by _drawablePathIconTransactionSaleState.collectAsState()
      override val drawablePathIconTransactionReturn: String by _drawablePathIconTransactionReturnState.collectAsState()
      override val drawablePathIconTransactionSupply: String by _drawablePathIconTransactionSupplyState.collectAsState()
      override val drawablePathIconStock: String by _drawablePathIconStockState.collectAsState()
      override val drawablePathIconMenu: String by _drawablePathIconMenuState.collectAsState()
      override val drawablePathIconBackArrow: String by _drawablePathIconBackArrowState.collectAsState()
      override val drawablePathIconAdd: String by _drawablePathIconAddState.collectAsState()
      override val drawablePathIconUserAccount: String by _drawablePathIconUserAccountState.collectAsState()
      override val drawablePathIconGoodsCategories: String by _drawablePathIconGoodsCategoriesState.collectAsState()
      override val drawablePathIconStores: String by _drawablePathIconStoresState.collectAsState()
      override val drawablePathIconTransactionHistory: String by _drawablePathIconTransactionHistoryState.collectAsState()
      override val drawablePathIconAnalytics: String by _drawablePathIconAnalyticsState.collectAsState()
      override val drawablePathIconWorkers: String by _drawablePathIconWorkersState.collectAsState()
      override val drawablePathIconSuppliers: String by _drawablePathIconSuppliersState.collectAsState()
      override val drawablePathIconDebtors: String by _drawablePathIconDebtorsState.collectAsState()
      override val drawablePathIconDevices: String by _drawablePathIconDevicesState.collectAsState()
      override val drawablePathIconAppLanguage: String by _drawablePathIconAppLanguageState.collectAsState()
      override val drawablePathIconAppTheme: String by _drawablePathIconAppThemeState.collectAsState()
      override val drawablePathIconCheck: String by _drawablePathIconCheckState.collectAsState()
      override val drawablePathIconCreate: String by _drawablePathIconCreateState.collectAsState()
      override val drawablePathIconSettings: String by _drawablePathIconSettingsState.collectAsState()
      override val drawablePathIconSearch: String by _drawablePathIconSearchState.collectAsState()
      override val drawablePathIconBarcodeCamScanner: String by _drawablePathIconBarcodeCamScannerState.collectAsState()
      override val drawablePathIconDelete: String by _drawablePathIconDeleteState.collectAsState()

      override val exceptionMessageUserWithThisPhoneNumberIsAlreadyRegistered: String by _exceptionMessageUserWithThisPhoneNumberIsAlreadyRegistered.collectAsState()
      override val exceptionMessageUserWithThisEmailAddressIsAlreadyRegistered: String by _exceptionMessageUserWithThisEmailAddressIsAlreadyRegistered.collectAsState()
      override val exceptionMessageUserWithThisPhoneNumberAndEmailAddressIsAlreadyRegistered: String by _exceptionMessageUserWithThisPhoneNumberAndEmailAddressIsAlreadyRegistered.collectAsState()
    }

    coroutineScope = rememberCoroutineScope()

    key(keys) {
      BoxWithConstraints(
        modifier = Modifier
          .fillMaxSize()
      ) {
        content()

        coroutineScope.launch {
          _screenWidthState.emit(maxWidth)
          _screenHeightState.emit(maxHeight)
          _isNarrowScreenState.emit(maxWidth.value < stateValues.wideScreenMinWidth)

          launch {
            _isNarrowScreenState
              .collect {
                Navigation.TransactionSale.init(it)
                Navigation.TransactionReturn.init(it)
                Navigation.TransactionSupply.init(it)
                Navigation.Stock.init(it)
                Navigation.Menu.init(it)
                Navigation.UserAuth.init(it)
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
              .observe(KEY_APP_LOCALE)
              .collect {
                it?.let {
                  _appLocaleLanguageState.emit(it)
                }
              }
          }

          launch {
            configurationRepository
              .stringsState
              .payload
              .collect {
                it?.let {
                  updateStringsState(it)
                }
              }
          }

          launch {
            _appLocaleLanguageState
              .collect {
                configurationRepository
                  .stringsState
                  .payloadValue?.let {
                    updateStringsState(it)
                  }
              }
          }

          launch {
            configurationRepository
              .dimensionsState
              .payload
              .collect {
                it?.let {
                  _wideScreenMinWidthState.emit(it.extractValue(4, stateValues.appSizeModeId))
                  _boundWidgetWidthState.emit(it.extractValue(9, stateValues.appSizeModeId).dp)

                  _textSizeState.emit(it.extractValue(0, stateValues.appSizeModeId).sp)
                  _titleTextSizeState.emit(it.extractValue(1, stateValues.appSizeModeId).sp)
                  _accentTextSizeState.emit(it.extractValue(2, stateValues.appSizeModeId).sp)
                  _smallTextSizeState.emit(it.extractValue(3, stateValues.appSizeModeId).sp)

                  _focusedBorderWidthState.emit(it.extractValue(5, stateValues.appSizeModeId).dp)
                  _unfocusedBorderWidthState.emit(it.extractValue(6, stateValues.appSizeModeId).dp)

                  _cornerRadiusState.emit(it.extractValue(7, stateValues.appSizeModeId).dp)
                  _iconSizeState.emit(it.extractValue(8, stateValues.appSizeModeId).dp)
                  _textFieldHeightMultiplierRelativeToTextSizeState.emit(it.extractValue(10, stateValues.appSizeModeId))
                  _textFieldHeightState.emit((_textSizeState.value.value * _textFieldHeightMultiplierRelativeToTextSizeState.value).dp)
                  _textFieldIconPaddingState.emit(it.extractValue(11, stateValues.appSizeModeId).dp)
                }
              }
          }

          launch {
            configurationRepository
              .colorsState
              .payload
              .collect {
                it?.let {
                  updateColors(it)
                }
              }
          }

          launch {
            configurationRepository
              .drawablesState
              .payload
              .collect {
                it?.let {
                  updateDrawables(it)
                }
              }
          }

          launch {
            _appThemeIdState
              .collect {
                configurationRepository
                  .colorsState
                  .payloadValue?.let {
                    updateColors(it)
                  }

                configurationRepository
                  .drawablesState
                  .payloadValue?.let {
                    updateDrawables(it)
                  }
              }
          }

          launch {
            configurationRepository
              .exceptionsState
              .payload
              .collect {
                it?.let {
                  _exceptionMessageUserWithThisPhoneNumberIsAlreadyRegistered.emit(it.extractExceptionMessage(0))
                  _exceptionMessageUserWithThisEmailAddressIsAlreadyRegistered.emit(it.extractExceptionMessage(1))
                  _exceptionMessageUserWithThisPhoneNumberAndEmailAddressIsAlreadyRegistered.emit(it.extractExceptionMessage(2))
                }
              }
          }
        }
      }
    }
  }

  private suspend fun updateStringsState(strings: List<LocalizedStringGroupDataModel>) {
    _stringAppNameState.emit(strings.extractString(0, stateValues.appLocaleLanguage))
    _stringLogInState.emit(strings.extractString(1, stateValues.appLocaleLanguage))
    _stringPhoneNumberState.emit(strings.extractString(2, stateValues.appLocaleLanguage))
    _stringEnterPhoneNumberState.emit(strings.extractString(3, stateValues.appLocaleLanguage))
    _stringEmailState.emit(strings.extractString(4, stateValues.appLocaleLanguage))
    _stringEnterEmailAddressState.emit(strings.extractString(5, stateValues.appLocaleLanguage))
    _stringPasswordState.emit(strings.extractString(6, stateValues.appLocaleLanguage))
    _stringEnterPasswordState.emit(strings.extractString(7, stateValues.appLocaleLanguage))
    _stringCancelState.emit(strings.extractString(8, stateValues.appLocaleLanguage))
    _stringClearState.emit(strings.extractString(9, stateValues.appLocaleLanguage))
    _stringLoginAndOrPasswordIncorrectState.emit(strings.extractString(10, stateValues.appLocaleLanguage))
    _stringPhoneNumberMustBeState.emit(strings.extractString(11, stateValues.appLocaleLanguage))
    _stringEmailMustBeState.emit(strings.extractString(12, stateValues.appLocaleLanguage))
    _stringPasswordMustBeState.emit(strings.extractString(13, stateValues.appLocaleLanguage))
    _stringRepeatPasswordState.emit(strings.extractString(14, stateValues.appLocaleLanguage))
    _stringPasswordsMustMatchState.emit(strings.extractString(15, stateValues.appLocaleLanguage))
    _stringFirstNameState.emit(strings.extractString(16, stateValues.appLocaleLanguage))
    _stringLastNameState.emit(strings.extractString(17, stateValues.appLocaleLanguage))
    _stringEnterFirstNameState.emit(strings.extractString(18, stateValues.appLocaleLanguage))
    _stringEnterLastNameState.emit(strings.extractString(19, stateValues.appLocaleLanguage))
    _stringUserWithThisPhoneNumberIsAlreadyRegisteredState.emit(strings.extractString(20, stateValues.appLocaleLanguage))
    _stringUserWithThisEmailAddressIsAlreadyRegisteredState.emit(strings.extractString(21, stateValues.appLocaleLanguage))
    _stringSignUpState.emit(strings.extractString(22, stateValues.appLocaleLanguage))
    _stringConfirmState.emit(strings.extractString(23, stateValues.appLocaleLanguage))
    _stringSaleState.emit(strings.extractString(24, stateValues.appLocaleLanguage))
    _stringReturnState.emit(strings.extractString(25, stateValues.appLocaleLanguage))
    _stringSupplyState.emit(strings.extractString(26, stateValues.appLocaleLanguage))
    _stringStockState.emit(strings.extractString(27, stateValues.appLocaleLanguage))
    _stringMenuState.emit(strings.extractString(28, stateValues.appLocaleLanguage))
    _stringBackState.emit(strings.extractString(29, stateValues.appLocaleLanguage))
    _stringAddGoodsItemState.emit(strings.extractString(30, stateValues.appLocaleLanguage))
    _stringEditGoodsItemState.emit(strings.extractString(31, stateValues.appLocaleLanguage))
    _stringUserAccountState.emit(strings.extractString(32, stateValues.appLocaleLanguage))
    _stringGoodsCategoriesState.emit(strings.extractString(33, stateValues.appLocaleLanguage))
    _stringAddGoodsCategoryState.emit(strings.extractString(34, stateValues.appLocaleLanguage))
    _stringEditGoodsCategoryState.emit(strings.extractString(35, stateValues.appLocaleLanguage))
    _stringStoresState.emit(strings.extractString(36, stateValues.appLocaleLanguage))
    _stringAddStoreState.emit(strings.extractString(37, stateValues.appLocaleLanguage))
    _stringEditStoreState.emit(strings.extractString(38, stateValues.appLocaleLanguage))
    _stringSubscriptionState.emit(strings.extractString(39, stateValues.appLocaleLanguage))
    _stringSubscriptionPlansState.emit(strings.extractString(40, stateValues.appLocaleLanguage))
    _stringTransactionHistoryState.emit(strings.extractString(41, stateValues.appLocaleLanguage))
    _stringReceiptState.emit(strings.extractString(42, stateValues.appLocaleLanguage))
    _stringAnalyticsState.emit(strings.extractString(43, stateValues.appLocaleLanguage))
    _stringWorkersState.emit(strings.extractString(44, stateValues.appLocaleLanguage))
    _stringAddWorkerState.emit(strings.extractString(45, stateValues.appLocaleLanguage))
    _stringEditWorkerState.emit(strings.extractString(46, stateValues.appLocaleLanguage))
    _stringSuppliersState.emit(strings.extractString(47, stateValues.appLocaleLanguage))
    _stringAddSupplierState.emit(strings.extractString(48, stateValues.appLocaleLanguage))
    _stringEditSupplierState.emit(strings.extractString(49, stateValues.appLocaleLanguage))
    _stringDebtorsState.emit(strings.extractString(50, stateValues.appLocaleLanguage))
    _stringCloseDebtState.emit(strings.extractString(51, stateValues.appLocaleLanguage))
    _stringDevicesState.emit(strings.extractString(52, stateValues.appLocaleLanguage))
    _stringAppLanguageState.emit(strings.extractString(53, stateValues.appLocaleLanguage))
    _stringAppThemeState.emit(strings.extractString(54, stateValues.appLocaleLanguage))
    _stringSelectState.emit(strings.extractString(55, stateValues.appLocaleLanguage))
    _stringUserWithThisPhoneNumberAndEmailAddressIsAlreadyRegisteredState.emit(strings.extractString(56, stateValues.appLocaleLanguage))
    _stringFirstNameCannotBeEmptyOrJustWhitespacesState.emit(strings.extractString(57, stateValues.appLocaleLanguage))
    _stringLastNameCannotBeEmptyOrJustWhitespacesState.emit(strings.extractString(58, stateValues.appLocaleLanguage))
    _stringSystemLanguageState.emit(strings.extractString(59, stateValues.appLocaleLanguage))
    _stringBluetoothPermissionRequiredState.emit(strings.extractString(60, stateValues.appLocaleLanguage))
    _stringForSearchAndConnectionToBluetoothBarcodeScannersAndReceiptPrintersState.emit(strings.extractString(61, stateValues.appLocaleLanguage))
    _stringForSearchAndConnectionToBluetoothBarcodeScannersAndReceiptPrintersYouCanGrantItInAppSettingsState.emit(strings.extractString(62, stateValues.appLocaleLanguage))
    _stringBluetoothDisabledState.emit(strings.extractString(63, stateValues.appLocaleLanguage))
    _stringEnableForSearchAndConnectionToBluetoothBarcodeScannersAndReceiptPrintersState.emit(strings.extractString(64, stateValues.appLocaleLanguage))
    _stringSearchByAnyDataState.emit(strings.extractString(65, stateValues.appLocaleLanguage))
    _stringListEmptyState.emit(strings.extractString(66, stateValues.appLocaleLanguage))
    _stringNoMatchesState.emit(strings.extractString(67, stateValues.appLocaleLanguage))
    _stringNameState.emit(strings.extractString(68, stateValues.appLocaleLanguage))
    _stringBarcodeState.emit(strings.extractString(69, stateValues.appLocaleLanguage))
    _stringSupplyPriceState.emit(strings.extractString(70, stateValues.appLocaleLanguage))
    _stringSalePriceState.emit(strings.extractString(71, stateValues.appLocaleLanguage))
    _stringReturnPriceState.emit(strings.extractString(72, stateValues.appLocaleLanguage))
    _stringCategoryState.emit(strings.extractString(73, stateValues.appLocaleLanguage))
    _stringSupplierState.emit(strings.extractString(74, stateValues.appLocaleLanguage))
    _stringEnterNameState.emit(strings.extractString(76, stateValues.appLocaleLanguage))
    _stringEnterBarcodeState.emit(strings.extractString(75, stateValues.appLocaleLanguage))
    _stringEnterSupplyPriceState.emit(strings.extractString(77, stateValues.appLocaleLanguage))
    _stringEnterSalePriceState.emit(strings.extractString(78, stateValues.appLocaleLanguage))
    _stringEnterReturnPriceState.emit(strings.extractString(79, stateValues.appLocaleLanguage))
    _stringSelectCategoryState.emit(strings.extractString(80, stateValues.appLocaleLanguage))
    _stringSelectSupplierState.emit(strings.extractString(81, stateValues.appLocaleLanguage))
  }

  private suspend fun updateColors(colors: List<StylizedColorGroupDataModel>) {
    _AccentColorState.emit(colors.extractColor(0, stateValues.appThemeId).toColor())
    _BackgroundColorState.emit(colors.extractColor(1, stateValues.appThemeId).toColor())
    _TextColorState.emit(colors.extractColor(2, stateValues.appThemeId).toColor())
    _AccentTextColorState.emit(colors.extractColor(3, stateValues.appThemeId).toColor())
    _PlaceholderTextColorState.emit(colors.extractColor(4, stateValues.appThemeId).toColor())
    _DisabledColorState.emit(colors.extractColor(5, stateValues.appThemeId).toColor())
    _ErrorColorState.emit(colors.extractColor(6, stateValues.appThemeId).toColor())
    _IconTintColorState.emit(colors.extractColor(7, stateValues.appThemeId).toColor())
    _OkayColorState.emit(colors.extractColor(8, stateValues.appThemeId).toColor())
    _BorderlineBadColorState.emit(colors.extractColor(9, stateValues.appThemeId).toColor())
  }

  private suspend fun updateDrawables(drawables: List<StylizedDrawablePathsGroupDataModel>) {
    _drawablePathAITALogoState.emit(drawables.extractPath(0, stateValues.appThemeId))
    _drawablePathIconPasswordState.emit(drawables.extractPath(1, stateValues.appThemeId))
    _drawablePathIconCancelState.emit(drawables.extractPath(2, stateValues.appThemeId))
    _drawablePathIconEyeHideState.emit(drawables.extractPath(3, stateValues.appThemeId))
    _drawablePathIconEyeShowState.emit(drawables.extractPath(4, stateValues.appThemeId))
    _drawablePathIconEmailState.emit(drawables.extractPath(5, stateValues.appThemeId))
    _drawablePathIconExpandMoreState.emit(drawables.extractPath(7, stateValues.appThemeId))
    _drawablePathIconExpandLessState.emit(drawables.extractPath(8, stateValues.appThemeId))
    _drawablePathIconPersonState.emit(drawables.extractPath(9, stateValues.appThemeId))
    _drawablePathIconTransactionSaleState.emit(drawables.extractPath(10, stateValues.appThemeId))
    _drawablePathIconTransactionReturnState.emit(drawables.extractPath(11, stateValues.appThemeId))
    _drawablePathIconTransactionSupplyState.emit(drawables.extractPath(12, stateValues.appThemeId))
    _drawablePathIconStockState.emit(drawables.extractPath(13, stateValues.appThemeId))
    _drawablePathIconMenuState.emit(drawables.extractPath(14, stateValues.appThemeId))
    _drawablePathIconBackArrowState.emit(drawables.extractPath(15, stateValues.appThemeId))
    _drawablePathIconAddState.emit(drawables.extractPath(16, stateValues.appThemeId))
    _drawablePathIconUserAccountState.emit(drawables.extractPath(17, stateValues.appThemeId))
    _drawablePathIconGoodsCategoriesState.emit(drawables.extractPath(18, stateValues.appThemeId))
    _drawablePathIconStoresState.emit(drawables.extractPath(19, stateValues.appThemeId))
    _drawablePathIconTransactionHistoryState.emit(drawables.extractPath(20, stateValues.appThemeId))
    _drawablePathIconAnalyticsState.emit(drawables.extractPath(21, stateValues.appThemeId))
    _drawablePathIconWorkersState.emit(drawables.extractPath(22, stateValues.appThemeId))
    _drawablePathIconSuppliersState.emit(drawables.extractPath(23, stateValues.appThemeId))
    _drawablePathIconDebtorsState.emit(drawables.extractPath(24, stateValues.appThemeId))
    _drawablePathIconDevicesState.emit(drawables.extractPath(25, stateValues.appThemeId))
    _drawablePathIconAppLanguageState.emit(drawables.extractPath(26, stateValues.appThemeId))
    _drawablePathIconAppThemeState.emit(drawables.extractPath(27, stateValues.appThemeId))
    _drawablePathIconCheckState.emit(drawables.extractPath(28, stateValues.appThemeId))
    _drawablePathIconCreateState.emit(drawables.extractPath(29, stateValues.appThemeId))
    _drawablePathIconSettingsState.emit(drawables.extractPath(30, stateValues.appThemeId))
    _drawablePathIconSearchState.emit(drawables.extractPath(31, stateValues.appThemeId))
    _drawablePathIconBarcodeCamScannerState.emit(drawables.extractPath(32, stateValues.appThemeId))
    _drawablePathIconDeleteState.emit(drawables.extractPath(33, stateValues.appThemeId))
  }
}