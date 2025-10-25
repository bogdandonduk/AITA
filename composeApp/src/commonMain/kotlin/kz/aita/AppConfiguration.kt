package kz.aita

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kz.aita.compose.navigation.Navigation
import kz.aita.compose.navigation.NavigationScreenModel
import kz.aita.compose.util.loadResourceColors
import kz.aita.compose.util.loadResourceDimensions
import kz.aita.compose.util.loadResourceDrawablePaths
import kz.aita.compose.util.loadResourceStrings
import kz.aita.compose.util.toColor
import kz.aita.core.*
import kz.aita.model.dataModel.*
import kz.aita.model.wrapper.DataState

object AppConfiguration {

  interface StateValues {

    val latestNotification: NotificationDataModel?

    val userAccountState: DataState<UserAccountDataModel>
    val userAccount: UserAccountDataModel?

    val stockState: DataState<List<GoodsItemDataModel>>
    val stock: List<GoodsItemDataModel>?

    val storesState: DataState<List<StoreDataModel>>
    val stores: List<StoreDataModel>?
    val activeStoreId: String?

    val goodsCategoriesState: DataState<List<GenericGoodsCategoryDataModel>>
    val goodsCategories: List<GenericGoodsCategoryDataModel>?

    val suppliersState: DataState<List<SupplierDataModel>>
    val suppliers: List<SupplierDataModel>?

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

    val appLanguage: String
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
    val stringAuthenticationFailed: String
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
    val stringSupplyPrice: String
    val stringSalePrice: String
    val stringReturnPrice: String
    val stringCategory: String
    val stringSupplier: String
    val stringEnterName: String
    val stringEnterBarcode: String
    val stringEnterSupplyPrice: String
    val stringEnterSalePrice: String
    val stringEnterReturnPrice: String
    val stringSelectCategory: String
    val stringSelectSupplier: String
    val stringEdit: String
    val stringChangePassword: String
    val stringNewPassword: String
    val stringEnterNewPassword: String
    val stringRepeatNewPassword: String
    val stringConfirmationPassword: String
    val stringRequiredToEditAccount: String
    val stringAccountSuccessfullyUpdated: String
    val stringLoggingOut: String
    val stringSessionTimeExpiredLoggingOut: String
    val stringAlias: String
    val stringDescription: String
    val stringEnterAlias: String
    val stringEnterDescription: String
    val stringOptional: String
    val stringLoggingIn: String
    val stringSigningUp: String
    val stringCompanyForm: String
    val stringMeasurementUnit: String

    val stringNoActiveStore: String
    val stringSelectInMenu: String
    val stringSupplyData: String
    val stringSaleData: String
    val stringReturnData: String
    val stringAddSupplyData: String
    val stringAddSaleData: String
    val stringAddReturnData: String
    val stringAddBarcode: String
    val stringAddName: String
    val stringPayment: String
    val stringAll: String
    val stringQuick: String
    val stringCategories: String

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
    val wideTextFieldHeight: Dp

    val textFieldIconPadding: Dp

    val marginTextField: Dp
    val marginTextFieldGroup: Dp

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
    val drawablePathIconEdit: String
    val drawablePathIconSettings: String
    val drawablePathIconSearch: String
    val drawablePathIconBarcodeCamScanner: String
    val drawablePathIconDelete: String
    val drawablePathIconExit: String
  }

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
  private val _wideTextFieldHeightState = MutableStateFlow((((_textSizeState.value.value * 4) * _textFieldHeightMultiplierRelativeToTextSizeState.value)).dp)
  private val _textFieldIconPaddingState = MutableStateFlow((9.dp))

  private val _marginTextFieldState = MutableStateFlow((8.dp))
  private val _marginTextFieldGroupState = MutableStateFlow((24.dp))

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

  lateinit var stateValues: StateValues

  var softKeyboardController: SoftwareKeyboardController? = null

  lateinit var coroutineScope: CoroutineScope

  fun setAppTheme(themeId: Long) {
    configurationRepository.setAppTheme(themeId)
  }

  suspend fun setAppLocale(language: String) {
    configurationRepository.setAppLocale(language)
  }

  fun postNotification(message: List<LocalizedStringDataModel>?, type: NotificationType) {
    notificationRepository.post(message, type)
  }

  @Composable
  operator fun invoke(
    content: @Composable AppConfiguration.() -> Unit,
    vararg keys: Any
  ) {
    stateValues = object: StateValues {
      override val latestNotification: NotificationDataModel? by notificationRepository.latestNotificationState.collectAsState()

      override val userAccountState: DataState<UserAccountDataModel> by userRepository.userAccountState.value.collectAsState()
      override val userAccount: UserAccountDataModel? by userRepository.userAccountState.payload.collectAsState()

      override val stockState: DataState<List<GoodsItemDataModel>> by stockRepository.stockState.value.collectAsState()
      override val stock: List<GoodsItemDataModel>? by stockRepository.stockState.payload.collectAsState()

      override val storesState: DataState<List<StoreDataModel>> by storeRepository.storesState.value.collectAsState()
      override val stores: List<StoreDataModel>? by storeRepository.storesState.payload.collectAsState()
      override val activeStoreId: String? by storeRepository.activeStoreId.collectAsState()

      override val goodsCategoriesState: DataState<List<GenericGoodsCategoryDataModel>> by genericItemsRepository.goodsCategoriesState.value.collectAsState()
      override val goodsCategories: List<GenericGoodsCategoryDataModel>? by genericItemsRepository.goodsCategoriesState.payload.collectAsState()

      override val suppliersState: DataState<List<SupplierDataModel>> by supplierRepository.suppliersState.value.collectAsState()
      override val suppliers: List<SupplierDataModel>? by supplierRepository.suppliersState.payload.collectAsState()

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

      override val appLanguage: String by configurationRepository.appLanguageState.collectAsState()
      override val appThemeId: Long by configurationRepository.appThemeIdState.collectAsState()
      override val appSizeModeId: Long by configurationRepository.appSizeModeIdState.collectAsState()

      override val stringAppName: String by configurationRepository.stringAppNameState.collectAsState()
      override val stringLogIn: String by configurationRepository.stringLogInState.collectAsState()
      override val stringPhoneNumber: String by configurationRepository.stringPhoneNumberState.collectAsState()
      override val stringEnterPhoneNumber: String by configurationRepository.stringEnterPhoneNumberState.collectAsState()
      override val stringEmail: String by configurationRepository.stringEmailState.collectAsState()
      override val stringEnterEmailAddress: String by configurationRepository.stringEnterEmailAddressState.collectAsState()
      override val stringPassword: String by configurationRepository.stringPasswordState.collectAsState()
      override val stringEnterPassword: String by configurationRepository.stringEnterPasswordState.collectAsState()
      override val stringCancel: String by configurationRepository.stringCancelState.collectAsState()
      override val stringClear: String by configurationRepository.stringClearState.collectAsState()
      override val stringAuthenticationFailed: String by configurationRepository.stringAuthorizationFailedState.collectAsState()
      override val stringPhoneNumberMustBe: String by configurationRepository.stringPhoneNumberMustBeState.collectAsState()
      override val stringEmailMustBe: String by configurationRepository.stringEmailMustBeState.collectAsState()
      override val stringPasswordMustBe: String by configurationRepository.stringPasswordMustBeState.collectAsState()
      override val stringRepeatPassword: String by configurationRepository.stringRepeatPasswordState.collectAsState()
      override val stringPasswordsMustMatch: String by configurationRepository.stringPasswordsMustMatchState.collectAsState()
      override val stringFirstName: String by configurationRepository.stringFirstNameState.collectAsState()
      override val stringLastName: String by configurationRepository.stringLastNameState.collectAsState()
      override val stringEnterFirstName: String by configurationRepository.stringEnterFirstNameState.collectAsState()
      override val stringEnterLastName: String by configurationRepository.stringEnterLastNameState.collectAsState()
      override val stringUserWithThisPhoneNumberIsAlreadyRegistered: String by configurationRepository.stringUserWithThisPhoneNumberIsAlreadyRegisteredState.collectAsState()
      override val stringUserWithThisEmailAddressIsAlreadyRegistered: String by configurationRepository.stringUserWithThisEmailAddressIsAlreadyRegisteredState.collectAsState()
      override val stringSignUp: String by configurationRepository.stringSignUpState.collectAsState()
      override val stringConfirm: String by configurationRepository.stringConfirmState.collectAsState()
      override val stringSale: String by configurationRepository.stringSaleState.collectAsState()
      override val stringReturn: String by configurationRepository.stringReturnState.collectAsState()
      override val stringSupply: String by configurationRepository.stringSupplyState.collectAsState()
      override val stringStock: String by configurationRepository.stringStockState.collectAsState()
      override val stringMenu: String by configurationRepository.stringMenuState.collectAsState()
      override val stringBack: String by configurationRepository.stringBackState.collectAsState()
      override val stringAddGoodsItem: String by configurationRepository.stringAddGoodsItemState.collectAsState()
      override val stringEditGoodsItem: String by configurationRepository.stringEditGoodsItemState.collectAsState()
      override val stringUserAccount: String by configurationRepository.stringUserAccountState.collectAsState()
      override val stringGoodsCategories: String by configurationRepository.stringGoodsCategoriesState.collectAsState()
      override val stringAddGoodsCategory: String by configurationRepository.stringAddGoodsCategoryState.collectAsState()
      override val stringEditGoodsCategory: String by configurationRepository.stringEditGoodsCategoryState.collectAsState()
      override val stringStores: String by configurationRepository.stringStoresState.collectAsState()
      override val stringAddStore: String by configurationRepository.stringAddStoreState.collectAsState()
      override val stringEditStore: String by configurationRepository.stringEditStoreState.collectAsState()
      override val stringSubscription: String by configurationRepository.stringSubscriptionState.collectAsState()
      override val stringSubscriptionPlans: String by configurationRepository.stringSubscriptionPlansState.collectAsState()
      override val stringTransactionHistory: String by configurationRepository.stringTransactionHistoryState.collectAsState()
      override val stringReceipt: String by configurationRepository.stringReceiptState.collectAsState()
      override val stringAnalytics: String by configurationRepository.stringAnalyticsState.collectAsState()
      override val stringWorkers: String by configurationRepository.stringWorkersState.collectAsState()
      override val stringAddWorker: String by configurationRepository.stringAddWorkerState.collectAsState()
      override val stringEditWorker: String by configurationRepository.stringEditWorkerState.collectAsState()
      override val stringSuppliers: String by configurationRepository.stringSuppliersState.collectAsState()
      override val stringAddSupplier: String by configurationRepository.stringAddSupplierState.collectAsState()
      override val stringEditSupplier: String by configurationRepository.stringEditSupplierState.collectAsState()
      override val stringDebtors: String by configurationRepository.stringDebtorsState.collectAsState()
      override val stringCloseDebt: String by configurationRepository.stringCloseDebtState.collectAsState()
      override val stringDevices: String by configurationRepository.stringDevicesState.collectAsState()
      override val stringAppLanguage: String by configurationRepository.stringAppLanguageState.collectAsState()
      override val stringAppTheme: String by configurationRepository.stringAppThemeState.collectAsState()
      override val stringSelect: String by configurationRepository.stringSelectState.collectAsState()
      override val stringUserWithThisPhoneNumberAndEmailAddressIsAlreadyRegistered: String by configurationRepository.stringUserWithThisPhoneNumberAndEmailAddressIsAlreadyRegisteredState.collectAsState()
      override val stringFirstNameCannotBeEmptyOrJustWhitespaces: String by configurationRepository.stringFirstNameCannotBeEmptyOrJustWhitespacesState.collectAsState()
      override val stringLastNameCannotBeEmptyOrJustWhitespaces: String by configurationRepository.stringLastNameCannotBeEmptyOrJustWhitespacesState.collectAsState()
      override val stringSystemLanguage: String by configurationRepository.stringSystemLanguageState.collectAsState()
      override val stringBluetoothPermissionRequired: String by configurationRepository.stringBluetoothPermissionRequiredState.collectAsState()
      override val stringForSearchAndConnectionToBluetoothBarcodeScannersAndReceiptPrinters: String by configurationRepository.stringForSearchAndConnectionToBluetoothBarcodeScannersAndReceiptPrintersState.collectAsState()
      override val stringForSearchAndConnectionToBluetoothBarcodeScannersAndReceiptPrintersYouCanGrantItInAppSettings: String by configurationRepository.stringForSearchAndConnectionToBluetoothBarcodeScannersAndReceiptPrintersYouCanGrantItInAppSettingsState.collectAsState()
      override val stringBluetoothDisabled: String by configurationRepository.stringBluetoothDisabledState.collectAsState()
      override val stringEnableForSearchAndConnectionToBluetoothBarcodeScannersAndReceiptPrinters: String by configurationRepository.stringEnableForSearchAndConnectionToBluetoothBarcodeScannersAndReceiptPrintersState.collectAsState()
      override val stringSearchByAnyData: String by configurationRepository.stringSearchByAnyDataState.collectAsState()
      override val stringListEmpty: String by configurationRepository.stringListEmptyState.collectAsState()
      override val stringNoMatches: String by configurationRepository.stringNoMatchesState.collectAsState()
      override val stringName: String by configurationRepository.stringNameState.collectAsState()
      override val stringBarcode: String by configurationRepository.stringBarcodeState.collectAsState()
      override val stringSupplyPrice: String by configurationRepository.stringSupplyPriceState.collectAsState()
      override val stringSalePrice: String by configurationRepository.stringSalePriceState.collectAsState()
      override val stringReturnPrice: String by configurationRepository.stringReturnPriceState.collectAsState()
      override val stringCategory: String by configurationRepository.stringCategoryState.collectAsState()
      override val stringSupplier: String by configurationRepository.stringSupplierState.collectAsState()
      override val stringEnterName: String by configurationRepository.stringEnterNameState.collectAsState()
      override val stringEnterBarcode: String by configurationRepository.stringEnterBarcodeState.collectAsState()
      override val stringEnterSupplyPrice: String by configurationRepository.stringEnterSupplyPriceState.collectAsState()
      override val stringEnterSalePrice: String by configurationRepository.stringEnterSalePriceState.collectAsState()
      override val stringEnterReturnPrice: String by configurationRepository.stringEnterReturnPriceState.collectAsState()
      override val stringSelectCategory: String by configurationRepository.stringSelectCategoryState.collectAsState()
      override val stringSelectSupplier: String by configurationRepository.stringSelectSupplierState.collectAsState()
      override val stringEdit: String by configurationRepository.stringEditState.collectAsState()
      override val stringChangePassword: String by configurationRepository.stringChangePasswordState.collectAsState()
      override val stringNewPassword: String by configurationRepository.stringNewPasswordState.collectAsState()
      override val stringEnterNewPassword: String by configurationRepository.stringEnterNewPasswordState.collectAsState()
      override val stringRepeatNewPassword: String by configurationRepository.stringRepeatNewPasswordState.collectAsState()
      override val stringConfirmationPassword: String by configurationRepository.stringConfirmationPasswordState.collectAsState()
      override val stringRequiredToEditAccount: String by configurationRepository.stringRequiredToEditAccountState.collectAsState()
      override val stringAccountSuccessfullyUpdated: String by configurationRepository.stringAccountSuccessfullyUpdatedState.collectAsState()
      override val stringLoggingOut: String by configurationRepository.stringLoggingOutState.collectAsState()
      override val stringSessionTimeExpiredLoggingOut: String by configurationRepository.stringSessionTimeExpiredLoggingOutState.collectAsState()
      override val stringAlias: String by configurationRepository.stringAliasState.collectAsState()
      override val stringDescription: String by configurationRepository.stringDescriptionState.collectAsState()
      override val stringEnterAlias: String by configurationRepository.stringEnterAliasState.collectAsState()
      override val stringEnterDescription: String by configurationRepository.stringEnterDescriptionState.collectAsState()
      override val stringOptional: String by configurationRepository.stringOptionalState.collectAsState()
      override val stringLoggingIn: String by configurationRepository.stringLoggingInState.collectAsState()
      override val stringSigningUp: String by configurationRepository.stringSigningUpState.collectAsState()
      override val stringCompanyForm: String by configurationRepository.stringCompanyFormState.collectAsState()
      override val stringMeasurementUnit: String by configurationRepository.stringMeasurementUnitState.collectAsState()
      override val stringNoActiveStore: String by configurationRepository.stringNoActiveStoreState.collectAsState()
      override val stringSelectInMenu: String by configurationRepository.stringSelectInMenuState.collectAsState()
      override val stringSupplyData: String by configurationRepository.stringSupplyDataState.collectAsState()
      override val stringSaleData: String by configurationRepository.stringSaleDataState.collectAsState()
      override val stringReturnData: String by configurationRepository.stringReturnDataState.collectAsState()
      override val stringAddSupplyData: String by configurationRepository.stringAddSupplyDataState.collectAsState()
      override val stringAddSaleData: String by configurationRepository.stringAddSaleDataState.collectAsState()
      override val stringAddReturnData: String by configurationRepository.stringAddReturnDataState.collectAsState()
      override val stringAddBarcode: String by configurationRepository.stringAddBarcodeState.collectAsState()
      override val stringAddName: String by configurationRepository.stringAddNameState.collectAsState()
      override val stringPayment: String by configurationRepository.stringPaymentState.collectAsState()
      override val stringAll: String by configurationRepository.stringAllState.collectAsState()
      override val stringQuick: String by configurationRepository.stringQuickGoodsItemsState.collectAsState()
      override val stringCategories: String by configurationRepository.stringCategoriesState.collectAsState()

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
      override val wideTextFieldHeight: Dp by _wideTextFieldHeightState.collectAsState()
      override val textFieldIconPadding: Dp by _textFieldIconPaddingState.collectAsState()
      override val marginTextField: Dp by _marginTextFieldState.collectAsState()
      override val marginTextFieldGroup: Dp by _marginTextFieldGroupState.collectAsState()

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

      override val drawablePathAITALogo: String by configurationRepository.drawablePathAITALogoState.collectAsState()
      override val drawablePathIconPassword: String by configurationRepository.drawablePathIconPasswordState.collectAsState()
      override val drawablePathIconCancel: String by configurationRepository.drawablePathIconCancelState.collectAsState()
      override val drawablePathIconEyeHide: String by configurationRepository.drawablePathIconEyeHideState.collectAsState()
      override val drawablePathIconEyeShow: String by configurationRepository.drawablePathIconEyeShowState.collectAsState()
      override val drawablePathIconEmail: String by configurationRepository.drawablePathIconEmailState.collectAsState()
      override val drawablePathIconPhone: String by configurationRepository.drawablePathIconPhoneState.collectAsState()
      override val drawablePathIconExpandMore: String by configurationRepository.drawablePathIconExpandMoreState.collectAsState()
      override val drawablePathIconExpandLess: String by configurationRepository.drawablePathIconExpandLessState.collectAsState()
      override val drawablePathIconPerson: String by configurationRepository.drawablePathIconPersonState.collectAsState()
      override val drawablePathIconTransactionSale: String by configurationRepository.drawablePathIconTransactionSaleState.collectAsState()
      override val drawablePathIconTransactionReturn: String by configurationRepository.drawablePathIconTransactionReturnState.collectAsState()
      override val drawablePathIconTransactionSupply: String by configurationRepository.drawablePathIconTransactionSupplyState.collectAsState()
      override val drawablePathIconStock: String by configurationRepository.drawablePathIconStockState.collectAsState()
      override val drawablePathIconMenu: String by configurationRepository.drawablePathIconMenuState.collectAsState()
      override val drawablePathIconBackArrow: String by configurationRepository.drawablePathIconBackArrowState.collectAsState()
      override val drawablePathIconAdd: String by configurationRepository.drawablePathIconAddState.collectAsState()
      override val drawablePathIconUserAccount: String by configurationRepository.drawablePathIconUserAccountState.collectAsState()
      override val drawablePathIconGoodsCategories: String by configurationRepository.drawablePathIconGoodsCategoriesState.collectAsState()
      override val drawablePathIconStores: String by configurationRepository.drawablePathIconStoresState.collectAsState()
      override val drawablePathIconTransactionHistory: String by configurationRepository.drawablePathIconTransactionHistoryState.collectAsState()
      override val drawablePathIconAnalytics: String by configurationRepository.drawablePathIconAnalyticsState.collectAsState()
      override val drawablePathIconWorkers: String by configurationRepository.drawablePathIconWorkersState.collectAsState()
      override val drawablePathIconSuppliers: String by configurationRepository.drawablePathIconSuppliersState.collectAsState()
      override val drawablePathIconDebtors: String by configurationRepository.drawablePathIconDebtorsState.collectAsState()
      override val drawablePathIconDevices: String by configurationRepository.drawablePathIconDevicesState.collectAsState()
      override val drawablePathIconAppLanguage: String by configurationRepository.drawablePathIconAppLanguageState.collectAsState()
      override val drawablePathIconAppTheme: String by configurationRepository.drawablePathIconAppThemeState.collectAsState()
      override val drawablePathIconCheck: String by configurationRepository.drawablePathIconCheckState.collectAsState()
      override val drawablePathIconEdit: String by configurationRepository.drawablePathIconEditState.collectAsState()
      override val drawablePathIconSettings: String by configurationRepository.drawablePathIconSettingsState.collectAsState()
      override val drawablePathIconSearch: String by configurationRepository.drawablePathIconSearchState.collectAsState()
      override val drawablePathIconBarcodeCamScanner: String by configurationRepository.drawablePathIconBarcodeCamScannerState.collectAsState()
      override val drawablePathIconDelete: String by configurationRepository.drawablePathIconDeleteState.collectAsState()
      override val drawablePathIconExit: String by configurationRepository.drawablePathIconExitState.collectAsState()
    }

    softKeyboardController = LocalSoftwareKeyboardController.current
    coroutineScope = rememberCoroutineScope()

    key(keys) {
      BoxWithConstraints(
        modifier = Modifier
          .fillMaxSize()
      ) {
        content()

        coroutineScope.launch(Dispatchers.io) {
          _screenWidthState.emit(maxWidth)
          _screenHeightState.emit(maxHeight)
          _isNarrowScreenState.emit(maxWidth.value < stateValues.wideScreenMinWidth)

          val resourceStrings = loadResourceStrings()
          val resourceDimensions = loadResourceDimensions()
          val resourceColors = loadResourceColors()
          val resourceDrawables = loadResourceDrawablePaths()

          launch(Dispatchers.io) {
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

          launch(Dispatchers.io) {
            configurationRepository
              .stringsState
              .payload
              .collect {
                it?.let {
                  configurationRepository.updateStrings(it, resourceStrings)
                }
              }
          }

          launch(Dispatchers.io) {
            configurationRepository
              .appLanguageState
              .collect {
                configurationRepository
                  .stringsState
                  .payloadValue?.let {
                    configurationRepository.updateStrings(it, resourceStrings)
                  }
              }
          }

          launch(Dispatchers.io) {
            configurationRepository
              .dimensionsState
              .payload
              .collect {
                it?.let {
                  updateDimensions(it, resourceDimensions)
                }
              }
          }

          launch(Dispatchers.io) {
            configurationRepository
              .colorsState
              .payload
              .collect {
                it?.let {
                  updateColors(it, resourceColors)
                }
              }
          }

          launch(Dispatchers.io) {
            configurationRepository
              .drawablesState
              .payload
              .collect {
                it?.let {
                  configurationRepository.updateDrawables(it, resourceDrawables)
                }
              }
          }

          launch(Dispatchers.io) {
            configurationRepository
              .appThemeIdState
              .collect {
                configurationRepository
                  .colorsState
                  .payloadValue?.let {
                    updateColors(it, resourceColors)
                  }

                configurationRepository
                  .drawablesState
                  .payloadValue?.let {
                    configurationRepository.updateDrawables(it, resourceDrawables)
                  }
              }
          }

          launch(Dispatchers.io) {
            configurationRepository
              .appSizeModeIdState
              .collect {
                configurationRepository
                  .dimensionsState
                  .payloadValue?.let {
                    updateDimensions(it, resourceDimensions)
                  }

              }
          }
        }
      }
    }
  }

  private suspend fun updateDimensions(dimensions: List<StylizedDimensionGroupDataModel>, resourceDimensions: List<StylizedDimensionGroupDataModel>) {
    _wideScreenMinWidthState.emit(dimensions.extractValue(4, stateValues.appSizeModeId) ?: resourceDimensions.extractValue(4, stateValues.appSizeModeId)!!)
    _boundWidgetWidthState.emit((dimensions.extractValue(9, stateValues.appSizeModeId) ?: resourceDimensions.extractValue(9, stateValues.appSizeModeId)!!).dp)

    _textSizeState.emit((dimensions.extractValue(0, stateValues.appSizeModeId) ?: resourceDimensions.extractValue(0, stateValues.appSizeModeId)!!).sp)
    _titleTextSizeState.emit((dimensions.extractValue(1, stateValues.appSizeModeId) ?: resourceDimensions.extractValue(1, stateValues.appSizeModeId)!!).sp)
    _accentTextSizeState.emit((dimensions.extractValue(2, stateValues.appSizeModeId) ?: resourceDimensions.extractValue(2, stateValues.appSizeModeId)!!).sp)
    _smallTextSizeState.emit((dimensions.extractValue(3, stateValues.appSizeModeId) ?: resourceDimensions.extractValue(3, stateValues.appSizeModeId)!!).sp)

    _focusedBorderWidthState.emit((dimensions.extractValue(5, stateValues.appSizeModeId) ?: resourceDimensions.extractValue(5, stateValues.appSizeModeId)!!).dp)
    _unfocusedBorderWidthState.emit((dimensions.extractValue(6, stateValues.appSizeModeId) ?: resourceDimensions.extractValue(6, stateValues.appSizeModeId)!!).dp)

    _cornerRadiusState.emit((dimensions.extractValue(7, stateValues.appSizeModeId) ?: resourceDimensions.extractValue(7, stateValues.appSizeModeId)!!).dp)
    _iconSizeState.emit((dimensions.extractValue(8, stateValues.appSizeModeId) ?: resourceDimensions.extractValue(8, stateValues.appSizeModeId)!!).dp)
    _textFieldHeightMultiplierRelativeToTextSizeState.emit(dimensions.extractValue(10, stateValues.appSizeModeId) ?: resourceDimensions.extractValue(10, stateValues.appSizeModeId)!!)
    _textFieldHeightState.emit((_textSizeState.value.value * _textFieldHeightMultiplierRelativeToTextSizeState.value).dp)
    _textFieldIconPaddingState.emit((dimensions.extractValue(11, stateValues.appSizeModeId) ?: resourceDimensions.extractValue(11, stateValues.appSizeModeId)!!).dp)
  }
  
  private suspend fun updateColors(colors: List<StylizedColorGroupDataModel>, resourceColors: List<StylizedColorGroupDataModel>) {
    _AccentColorState.emit((colors.extractColor(0, configurationRepository.appThemeIdState.value) ?: resourceColors.extractColor(0, configurationRepository.appThemeIdState.value)!!).toColor())
    _BackgroundColorState.emit((colors.extractColor(1, configurationRepository.appThemeIdState.value) ?: resourceColors.extractColor(1, configurationRepository.appThemeIdState.value)!!).toColor())
    _TextColorState.emit((colors.extractColor(2, configurationRepository.appThemeIdState.value) ?: resourceColors.extractColor(2, configurationRepository.appThemeIdState.value)!!).toColor())
    _AccentTextColorState.emit((colors.extractColor(3, configurationRepository.appThemeIdState.value) ?: resourceColors.extractColor(3, configurationRepository.appThemeIdState.value)!!).toColor())
    _PlaceholderTextColorState.emit((colors.extractColor(4, configurationRepository.appThemeIdState.value) ?: resourceColors.extractColor(4, configurationRepository.appThemeIdState.value)!!).toColor())
    _DisabledColorState.emit((colors.extractColor(5, configurationRepository.appThemeIdState.value) ?: resourceColors.extractColor(5, configurationRepository.appThemeIdState.value)!!).toColor())
    _ErrorColorState.emit((colors.extractColor(6, configurationRepository.appThemeIdState.value) ?: resourceColors.extractColor(6, configurationRepository.appThemeIdState.value)!!).toColor())
    _IconTintColorState.emit((colors.extractColor(7, configurationRepository.appThemeIdState.value) ?: resourceColors.extractColor(7, configurationRepository.appThemeIdState.value)!!).toColor())
    _OkayColorState.emit((colors.extractColor(8, configurationRepository.appThemeIdState.value) ?: resourceColors.extractColor(8, configurationRepository.appThemeIdState.value)!!).toColor())
    _BorderlineBadColorState.emit((colors.extractColor(9, configurationRepository.appThemeIdState.value) ?: resourceColors.extractColor(9, configurationRepository.appThemeIdState.value)!!).toColor())
  }
  
}