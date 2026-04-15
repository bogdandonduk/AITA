package kz.aita.compose

import aita.composeapp.generated.resources.*
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kz.aita.*
import org.jetbrains.compose.resources.DrawableResource

object AppConfiguration {

  interface StateValues {
    val latestNotification: NotificationDataModel?
    val userAccountState: DataState<UserAccountDataModel>
    val userAccount: UserAccountDataModel?

    //    val activeModeId: String?
    val stockState: DataState<List<GoodsItemDataModel>>
    val stock: List<GoodsItemDataModel>?
    val stockBatches: List<GoodsBatchDataModel>?

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
    val appModeId: Int

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
    val stringMain: String
    val stringAddTranslation: String
    val stringSetActive: String
    val stringOutOfStock: String
    val stringDelete: String
    val stringCash: String
    val stringCashless: String
    val stringMixed: String
    val stringAdd: String
    val stringSubtract: String
    val stringCurrentBatchData: String
    val stringEnterQuantity: String
    val stringAddQuantityData: String
    val stringShelfBatch: String
    val stringActiveStore: String
    val stringMakeInactive: String
    val stringCartEmpty: String
    val stringComplete: String
    val stringNoActiveWorkshift: String
    val stringCart: String
    val stringAppMode: String
    val stringFinances: String
    val stringItems: String
    val stringBatches: String
    val stringStandardPricesForSuppliers: String
    val stringEditableForIndividualBatches: String
    val stringBatchesData: String

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
    val drawableResAITALogo: StateFlow<DrawableResource>

    val drawablePathIconPassword: String
    val drawableResIconPassword: StateFlow<DrawableResource>

    val drawablePathIconCancel: String
    val drawableResIconCancel: StateFlow<DrawableResource>

    val drawablePathIconEyeHide: String
    val drawableResIconEyeHide: StateFlow<DrawableResource>

    val drawablePathIconEyeShow: String
    val drawableResIconEyeShow: StateFlow<DrawableResource>

    val drawablePathIconEmail: String
    val drawableResIconEmail: StateFlow<DrawableResource>
    val drawablePathIconPhone: String
    val drawableResIconPhone: StateFlow<DrawableResource>

    val drawablePathIconExpandMore: String
    val drawableResIconExpandMore: StateFlow<DrawableResource>

    val drawablePathIconExpandLess: String
    val drawableResIconExpandLess: StateFlow<DrawableResource>

    val drawablePathIconPerson: String
    val drawableResIconPerson: StateFlow<DrawableResource>

    val drawablePathIconTransactionSale: String
    val drawableResIconTransactionSale: StateFlow<DrawableResource>

    val drawablePathIconTransactionReturn: String
    val drawableResIconTransactionReturn: StateFlow<DrawableResource>

    val drawablePathIconTransactionSupply: String
    val drawableResIconTransactionSupply: StateFlow<DrawableResource>

    val drawablePathIconStock: String
    val drawableResIconStock: StateFlow<DrawableResource>

    val drawablePathIconMenu: String
    val drawableResIconMenu: StateFlow<DrawableResource>

    val drawablePathIconBackArrow: String
    val drawableResIconBackArrow: StateFlow<DrawableResource>

    val drawablePathIconAdd: String
    val drawableResIconAdd: StateFlow<DrawableResource>

    val drawablePathIconUserAccount: String
    val drawableResIconUserAccount: StateFlow<DrawableResource>

    val drawablePathIconGoodsCategories: String
    val drawableResIconGoodsCategories: StateFlow<DrawableResource>

    val drawablePathIconStores: String
    val drawableResIconStores: StateFlow<DrawableResource>

    val drawablePathIconTransactionHistory: String
    val drawableResIconTransactionHistory: StateFlow<DrawableResource>

    val drawablePathIconAnalytics: String
    val drawableResIconAnalytics: StateFlow<DrawableResource>

    val drawablePathIconWorkers: String
    val drawableResIconWorkers: StateFlow<DrawableResource>

    val drawablePathIconSuppliers: String
    val drawableResIconSuppliers: StateFlow<DrawableResource>

    val drawablePathIconDebtors: String
    val drawableResIconDebtors: StateFlow<DrawableResource>

    val drawablePathIconDevices: String
    val drawableResIconDevices: StateFlow<DrawableResource>

    val drawablePathIconAppLanguage: String
    val drawableResIconAppLanguage: StateFlow<DrawableResource>

    val drawablePathIconAppTheme: String
    val drawableResIconAppTheme: StateFlow<DrawableResource>

    val drawablePathIconCheck: String
    val drawableResIconCheck: StateFlow<DrawableResource>

    val drawablePathIconEdit: String
    val drawableResIconEdit: StateFlow<DrawableResource>

    val drawablePathIconSettings: String
    val drawableResIconSettings: StateFlow<DrawableResource>

    val drawablePathIconSearch: String
    val drawableResIconSearch: StateFlow<DrawableResource>

    val drawablePathIconBarcodeCamScanner: String
    val drawableResIconBarcodeCamScanner: StateFlow<DrawableResource>

    val drawablePathIconDelete: String
    val drawableResIconDelete: StateFlow<DrawableResource>

    val drawablePathIconExit: String
    val drawableResIconExit: StateFlow<DrawableResource>

    val drawablePathIconSwitch: String
    val drawableResIconSwitch: StateFlow<DrawableResource>

    val drawablePathIconCart: String
    val drawableResIconCart: StateFlow<DrawableResource>

    val drawablePathIconAddCart: String
    val drawableResIconAddCart: StateFlow<DrawableResource>

    val drawablePathIconSubtract: String
    val drawableResIconSubtract: StateFlow<DrawableResource>

    val drawablePathIconReceipt: String
    val drawableResIconReceipt: StateFlow<DrawableResource>

    val drawablePathIconFinances: String
    val drawableResIconFinances: StateFlow<DrawableResource>

    suspend fun updateDrawableResources()
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
  private val _textFieldHeightState =
    MutableStateFlow(((_textSizeState.value.value * _textFieldHeightMultiplierRelativeToTextSizeState.value)).dp)
  private val _wideTextFieldHeightState =
    MutableStateFlow((((_textSizeState.value.value * 4) * _textFieldHeightMultiplierRelativeToTextSizeState.value)).dp)
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
    Common.setAppTheme(themeId)
  }

  fun setAppLocale(language: String) {
    Common.setAppLocale(language)
  }

  fun setAppMode(modeId: Int) {
    Common.setAppMode(modeId)
  }

  fun postNotification(message: List<LocalizedStringDataModel>?, type: NotificationType) {
    Common.postInAppNotification(message, type)
  }

  @Composable
  operator fun invoke(
    content: @Composable AppConfiguration.() -> Unit,
    vararg keys: Any
  ) {

    stateValues = object : StateValues {
      override val latestNotification: NotificationDataModel? by Common.latestInAppNotificationState.collectAsState()

      override val userAccountState: DataState<UserAccountDataModel> by Common.userAccountState.value.collectAsState()
      override val userAccount: UserAccountDataModel? by Common.userAccountState.payload.collectAsState()

      override val stockState: DataState<List<GoodsItemDataModel>> by Common.stockState.value.collectAsState()
      override val stock: List<GoodsItemDataModel>? by Common.stockState.payload.collectAsState()
      override val stockBatches: List<GoodsBatchDataModel>? by Common.stockBatchesState.payload.collectAsState()

      override val storesState: DataState<List<StoreDataModel>> by Common.storesState.value.collectAsState()
      override val stores: List<StoreDataModel>? by Common.storesState.payload.collectAsState()
      override val activeStoreId: String? by Common.activeStoreId.collectAsState()

      override val goodsCategoriesState: DataState<List<GenericGoodsCategoryDataModel>> by Common.genericGoodsCategoriesState.value.collectAsState()
      override val goodsCategories: List<GenericGoodsCategoryDataModel>? by Common.genericGoodsCategoriesState.payload.collectAsState()

      override val suppliersState: DataState<List<SupplierDataModel>> by Common.suppliersState.value.collectAsState()
      override val suppliers: List<SupplierDataModel>? by Common.suppliersState.payload.collectAsState()

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

      override val globalAppConfiguration: GlobalAppConfigurationDataModel by Common.globalAppConfigurationState.payload.collectAsState()
      override val strings: List<LocalizedStringGroupDataModel>? by Common.stringsState.payload.collectAsState()
      override val dimensions: List<StylizedDimensionGroupDataModel>? by Common.dimensionsState.payload.collectAsState()
      override val colors: List<StylizedColorGroupDataModel>? by Common.colorsState.payload.collectAsState()
      override val drawables: List<StylizedDrawablePathsGroupDataModel>? by Common.drawablesState.payload.collectAsState()

      override val appModeId: Int by Common.appModeState.collectAsState()
      override val appLanguage: String by Common.appLanguageState.collectAsState()
      override val appThemeId: Long by Common.appThemeIdState.collectAsState()
      override val appSizeModeId: Long by Common.appSizeModeIdState.collectAsState()

      override val stringAppName: String by Common.stringAppNameState.collectAsState()
      override val stringLogIn: String by Common.stringLogInState.collectAsState()
      override val stringPhoneNumber: String by Common.stringPhoneNumberState.collectAsState()
      override val stringEnterPhoneNumber: String by Common.stringEnterPhoneNumberState.collectAsState()
      override val stringEmail: String by Common.stringEmailState.collectAsState()
      override val stringEnterEmailAddress: String by Common.stringEnterEmailAddressState.collectAsState()
      override val stringPassword: String by Common.stringPasswordState.collectAsState()
      override val stringEnterPassword: String by Common.stringEnterPasswordState.collectAsState()
      override val stringCancel: String by Common.stringCancelState.collectAsState()
      override val stringClear: String by Common.stringClearState.collectAsState()
      override val stringAuthenticationFailed: String by Common.stringAuthenticationFailedState.collectAsState()
      override val stringPhoneNumberMustBe: String by Common.stringPhoneNumberMustBeState.collectAsState()
      override val stringEmailMustBe: String by Common.stringEmailMustBeState.collectAsState()
      override val stringPasswordMustBe: String by Common.stringPasswordMustBeState.collectAsState()
      override val stringRepeatPassword: String by Common.stringRepeatPasswordState.collectAsState()
      override val stringPasswordsMustMatch: String by Common.stringPasswordsMustMatchState.collectAsState()
      override val stringFirstName: String by Common.stringFirstNameState.collectAsState()
      override val stringLastName: String by Common.stringLastNameState.collectAsState()
      override val stringEnterFirstName: String by Common.stringEnterFirstNameState.collectAsState()
      override val stringEnterLastName: String by Common.stringEnterLastNameState.collectAsState()
      override val stringUserWithThisPhoneNumberIsAlreadyRegistered: String by Common.stringUserWithThisPhoneNumberIsAlreadyRegisteredState.collectAsState()
      override val stringUserWithThisEmailAddressIsAlreadyRegistered: String by Common.stringUserWithThisEmailAddressIsAlreadyRegisteredState.collectAsState()
      override val stringSignUp: String by Common.stringSignUpState.collectAsState()
      override val stringConfirm: String by Common.stringConfirmState.collectAsState()
      override val stringSale: String by Common.stringSaleState.collectAsState()
      override val stringReturn: String by Common.stringReturnState.collectAsState()
      override val stringSupply: String by Common.stringSupplyState.collectAsState()
      override val stringStock: String by Common.stringStockState.collectAsState()
      override val stringMenu: String by Common.stringMenuState.collectAsState()
      override val stringBack: String by Common.stringBackState.collectAsState()
      override val stringAddGoodsItem: String by Common.stringAddGoodsItemState.collectAsState()
      override val stringEditGoodsItem: String by Common.stringEditGoodsItemState.collectAsState()
      override val stringUserAccount: String by Common.stringUserAccountState.collectAsState()
      override val stringGoodsCategories: String by Common.stringGoodsCategoriesState.collectAsState()
      override val stringAddGoodsCategory: String by Common.stringAddGoodsCategoryState.collectAsState()
      override val stringEditGoodsCategory: String by Common.stringEditGoodsCategoryState.collectAsState()
      override val stringStores: String by Common.stringStoresState.collectAsState()
      override val stringAddStore: String by Common.stringAddStoreState.collectAsState()
      override val stringEditStore: String by Common.stringEditStoreState.collectAsState()
      override val stringSubscription: String by Common.stringSubscriptionState.collectAsState()
      override val stringSubscriptionPlans: String by Common.stringSubscriptionPlansState.collectAsState()
      override val stringTransactionHistory: String by Common.stringTransactionHistoryState.collectAsState()
      override val stringReceipt: String by Common.stringReceiptState.collectAsState()
      override val stringAnalytics: String by Common.stringAnalyticsState.collectAsState()
      override val stringWorkers: String by Common.stringWorkersState.collectAsState()
      override val stringAddWorker: String by Common.stringAddWorkerState.collectAsState()
      override val stringEditWorker: String by Common.stringEditWorkerState.collectAsState()
      override val stringSuppliers: String by Common.stringSuppliersState.collectAsState()
      override val stringAddSupplier: String by Common.stringAddSupplierState.collectAsState()
      override val stringEditSupplier: String by Common.stringEditSupplierState.collectAsState()
      override val stringDebtors: String by Common.stringDebtorsState.collectAsState()
      override val stringCloseDebt: String by Common.stringCloseDebtState.collectAsState()
      override val stringDevices: String by Common.stringDevicesState.collectAsState()
      override val stringAppLanguage: String by Common.stringAppLanguageState.collectAsState()
      override val stringAppTheme: String by Common.stringAppThemeState.collectAsState()
      override val stringSelect: String by Common.stringSelectState.collectAsState()
      override val stringUserWithThisPhoneNumberAndEmailAddressIsAlreadyRegistered: String by Common.stringUserWithThisPhoneNumberAndEmailAddressIsAlreadyRegisteredState.collectAsState()
      override val stringFirstNameCannotBeEmptyOrJustWhitespaces: String by Common.stringFirstNameCannotBeEmptyOrJustWhitespacesState.collectAsState()
      override val stringLastNameCannotBeEmptyOrJustWhitespaces: String by Common.stringLastNameCannotBeEmptyOrJustWhitespacesState.collectAsState()
      override val stringSystemLanguage: String by Common.stringSystemLanguageState.collectAsState()
      override val stringBluetoothPermissionRequired: String by Common.stringBluetoothPermissionRequiredState.collectAsState()
      override val stringForSearchAndConnectionToBluetoothBarcodeScannersAndReceiptPrinters: String by Common.stringForSearchAndConnectionToBluetoothBarcodeScannersAndReceiptPrintersState.collectAsState()
      override val stringForSearchAndConnectionToBluetoothBarcodeScannersAndReceiptPrintersYouCanGrantItInAppSettings: String by Common.stringForSearchAndConnectionToBluetoothBarcodeScannersAndReceiptPrintersYouCanGrantItInAppSettingsState.collectAsState()
      override val stringBluetoothDisabled: String by Common.stringBluetoothDisabledState.collectAsState()
      override val stringEnableForSearchAndConnectionToBluetoothBarcodeScannersAndReceiptPrinters: String by Common.stringEnableForSearchAndConnectionToBluetoothBarcodeScannersAndReceiptPrintersState.collectAsState()
      override val stringSearchByAnyData: String by Common.stringSearchByAnyDataState.collectAsState()
      override val stringListEmpty: String by Common.stringListEmptyState.collectAsState()
      override val stringNoMatches: String by Common.stringNoMatchesState.collectAsState()
      override val stringName: String by Common.stringNameState.collectAsState()
      override val stringBarcode: String by Common.stringBarcodeState.collectAsState()
      override val stringSupplyPrice: String by Common.stringSupplyPriceState.collectAsState()
      override val stringSalePrice: String by Common.stringSalePriceState.collectAsState()
      override val stringReturnPrice: String by Common.stringReturnPriceState.collectAsState()
      override val stringCategory: String by Common.stringCategoryState.collectAsState()
      override val stringSupplier: String by Common.stringSupplierState.collectAsState()
      override val stringEnterName: String by Common.stringEnterNameState.collectAsState()
      override val stringEnterBarcode: String by Common.stringEnterBarcodeState.collectAsState()
      override val stringEnterSupplyPrice: String by Common.stringEnterSupplyPriceState.collectAsState()
      override val stringEnterSalePrice: String by Common.stringEnterSalePriceState.collectAsState()
      override val stringEnterReturnPrice: String by Common.stringEnterReturnPriceState.collectAsState()
      override val stringSelectCategory: String by Common.stringSelectCategoryState.collectAsState()
      override val stringSelectSupplier: String by Common.stringSelectSupplierState.collectAsState()
      override val stringEdit: String by Common.stringEditState.collectAsState()
      override val stringChangePassword: String by Common.stringChangePasswordState.collectAsState()
      override val stringNewPassword: String by Common.stringNewPasswordState.collectAsState()
      override val stringEnterNewPassword: String by Common.stringEnterNewPasswordState.collectAsState()
      override val stringRepeatNewPassword: String by Common.stringRepeatNewPasswordState.collectAsState()
      override val stringConfirmationPassword: String by Common.stringConfirmationPasswordState.collectAsState()
      override val stringRequiredToEditAccount: String by Common.stringRequiredToEditAccountState.collectAsState()
      override val stringAccountSuccessfullyUpdated: String by Common.stringAccountSuccessfullyUpdatedState.collectAsState()
      override val stringLoggingOut: String by Common.stringLoggingOutState.collectAsState()
      override val stringSessionTimeExpiredLoggingOut: String by Common.stringSessionTimeExpiredLoggingOutState.collectAsState()
      override val stringAlias: String by Common.stringAliasState.collectAsState()
      override val stringDescription: String by Common.stringDescriptionState.collectAsState()
      override val stringEnterAlias: String by Common.stringEnterAliasState.collectAsState()
      override val stringEnterDescription: String by Common.stringEnterDescriptionState.collectAsState()
      override val stringOptional: String by Common.stringOptionalState.collectAsState()
      override val stringLoggingIn: String by Common.stringLoggingInState.collectAsState()
      override val stringSigningUp: String by Common.stringSigningUpState.collectAsState()
      override val stringCompanyForm: String by Common.stringCompanyFormState.collectAsState()
      override val stringMeasurementUnit: String by Common.stringMeasurementUnitState.collectAsState()
      override val stringNoActiveStore: String by Common.stringNoActiveStoreState.collectAsState()
      override val stringSelectInMenu: String by Common.stringSelectInMenuState.collectAsState()
      override val stringSupplyData: String by Common.stringSupplyDataState.collectAsState()
      override val stringSaleData: String by Common.stringSaleDataState.collectAsState()
      override val stringReturnData: String by Common.stringReturnDataState.collectAsState()
      override val stringAddSupplyData: String by Common.stringAddSupplyDataState.collectAsState()
      override val stringAddSaleData: String by Common.stringAddSaleDataState.collectAsState()
      override val stringAddReturnData: String by Common.stringAddReturnDataState.collectAsState()
      override val stringAddBarcode: String by Common.stringAddBarcodeState.collectAsState()
      override val stringAddName: String by Common.stringAddNameState.collectAsState()
      override val stringPayment: String by Common.stringPaymentState.collectAsState()
      override val stringAll: String by Common.stringAllState.collectAsState()
      override val stringQuick: String by Common.stringQuickState.collectAsState()
      override val stringCategories: String by Common.stringCategoriesState.collectAsState()
      override val stringMain: String by Common.stringMainState.collectAsState()
      override val stringAddTranslation: String by Common.stringAddTranslationState.collectAsState()
      override val stringSetActive: String by Common.stringSetActiveState.collectAsState()
      override val stringOutOfStock: String by Common.stringOutOfStockState.collectAsState()
      override val stringDelete: String by Common.stringDeleteState.collectAsState()
      override val stringCash: String by Common.stringCashState.collectAsState()
      override val stringCashless: String by Common.stringCashlessState.collectAsState()
      override val stringMixed: String by Common.stringMixedState.collectAsState()
      override val stringAdd: String by Common.stringAddState.collectAsState()
      override val stringSubtract: String by Common.stringSubtractState.collectAsState()
      override val stringCurrentBatchData: String by Common.stringCurrentQuantityDataState.collectAsState()
      override val stringEnterQuantity: String by Common.stringEnterQuantityState.collectAsState()
      override val stringAddQuantityData: String by Common.stringAddQuantityDataState.collectAsState()
      override val stringShelfBatch: String by Common.stringShelfBatchState.collectAsState()
      override val stringActiveStore: String by Common.stringActiveStoreState.collectAsState()
      override val stringMakeInactive: String by Common.stringMakeInactiveState.collectAsState()
      override val stringCartEmpty: String by Common.stringCartEmptyState.collectAsState()
      override val stringComplete: String by Common.stringCompleteState.collectAsState()
      override val stringNoActiveWorkshift: String by Common.stringNoActiveWorkshiftState.collectAsState()
      override val stringCart: String by Common.stringCartState.collectAsState()
      override val stringAppMode: String by Common.stringAppModeState.collectAsState()
      override val stringFinances: String by Common.stringFinancesState.collectAsState()
      override val stringItems: String by Common.stringItemsState.collectAsState()
      override val stringBatches: String by Common.stringBatchesState.collectAsState()
      override val stringStandardPricesForSuppliers: String by Common.stringStandardPricesForSuppliersState.collectAsState()
      override val stringEditableForIndividualBatches: String by Common.stringEditableForIndividualBatchesState.collectAsState()
      override val stringBatchesData: String by Common.stringBatchesDataState.collectAsState()

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

      override val drawablePathAITALogo: String by Common.drawablePathAITALogoState.collectAsState()
      private val _drawableResAITALogo = MutableStateFlow(Res.drawable._0_0)
      override val drawableResAITALogo = _drawableResAITALogo.asStateFlow()

      override val drawablePathIconPassword: String by Common.drawablePathIconPasswordState.collectAsState()
      private val _drawableResIconPassword = MutableStateFlow(Res.drawable._1_0)
      override val drawableResIconPassword = _drawableResIconPassword.asStateFlow()

      override val drawablePathIconCancel: String by Common.drawablePathIconCancelState.collectAsState()
      private val _drawableResIconCancel = MutableStateFlow(Res.drawable._2_0)
      override val drawableResIconCancel = _drawableResIconCancel.asStateFlow()

      override val drawablePathIconEyeHide: String by Common.drawablePathIconEyeHideState.collectAsState()
      private val _drawableResIconEyeHide = MutableStateFlow(Res.drawable._3_0)
      override val drawableResIconEyeHide = _drawableResIconEyeHide.asStateFlow()

      override val drawablePathIconEyeShow: String by Common.drawablePathIconEyeShowState.collectAsState()
      private val _drawableResIconEyeShow = MutableStateFlow(Res.drawable._4_0)
      override val drawableResIconEyeShow = _drawableResIconEyeShow.asStateFlow()

      override val drawablePathIconEmail: String by Common.drawablePathIconEmailState.collectAsState()
      private val _drawableResIconEmail = MutableStateFlow(Res.drawable._5_0)
      override val drawableResIconEmail = _drawableResIconEmail.asStateFlow()

      override val drawablePathIconPhone: String by Common.drawablePathIconPhoneState.collectAsState()
      private val _drawableResIconPhone = MutableStateFlow(Res.drawable._6_0)
      override val drawableResIconPhone: StateFlow<DrawableResource> = _drawableResIconPhone.asStateFlow()

      override val drawablePathIconExpandMore: String by Common.drawablePathIconExpandMoreState.collectAsState()
      private val _drawableResIconExpandMore = MutableStateFlow(Res.drawable._7_0)
      override val drawableResIconExpandMore: StateFlow<DrawableResource> = _drawableResIconExpandMore.asStateFlow()

      override val drawablePathIconExpandLess: String by Common.drawablePathIconExpandLessState.collectAsState()
      private val _drawableResIconExpandLess = MutableStateFlow(Res.drawable._8_0)
      override val drawableResIconExpandLess: StateFlow<DrawableResource> = _drawableResIconExpandLess.asStateFlow()

      override val drawablePathIconPerson: String by Common.drawablePathIconPersonState.collectAsState()
      private val _drawableResIconPerson = MutableStateFlow(Res.drawable._9_0)
      override val drawableResIconPerson: StateFlow<DrawableResource> = _drawableResIconPerson.asStateFlow()

      override val drawablePathIconTransactionSale: String by Common.drawablePathIconTransactionSaleState.collectAsState()
      private val _drawableResIconTransactionSale = MutableStateFlow(Res.drawable._10_0)
      override val drawableResIconTransactionSale: StateFlow<DrawableResource> =
        _drawableResIconTransactionSale.asStateFlow()

      override val drawablePathIconTransactionReturn: String by Common.drawablePathIconTransactionReturnState.collectAsState()
      private val _drawableResIconTransactionReturn = MutableStateFlow(Res.drawable._11_0)
      override val drawableResIconTransactionReturn: StateFlow<DrawableResource> =
        _drawableResIconTransactionReturn.asStateFlow()

      override val drawablePathIconTransactionSupply: String by Common.drawablePathIconTransactionSupplyState.collectAsState()
      private val _drawableResIconTransactionSupply = MutableStateFlow(Res.drawable._12_0)
      override val drawableResIconTransactionSupply: StateFlow<DrawableResource> =
        _drawableResIconTransactionSupply.asStateFlow()

      override val drawablePathIconStock: String by Common.drawablePathIconStockState.collectAsState()
      private val _drawableResIconStock = MutableStateFlow(Res.drawable._13_0)
      override val drawableResIconStock: StateFlow<DrawableResource> = _drawableResIconStock.asStateFlow()

      override val drawablePathIconMenu: String by Common.drawablePathIconMenuState.collectAsState()
      private val _drawableResIconMenu = MutableStateFlow(Res.drawable._14_0)
      override val drawableResIconMenu: StateFlow<DrawableResource> = _drawableResIconMenu.asStateFlow()

      override val drawablePathIconBackArrow: String by Common.drawablePathIconBackArrowState.collectAsState()
      private val _drawableResIconBackArrow = MutableStateFlow(Res.drawable._15_0)
      override val drawableResIconBackArrow: StateFlow<DrawableResource> = _drawableResIconBackArrow.asStateFlow()

      override val drawablePathIconAdd: String by Common.drawablePathIconAddState.collectAsState()
      private val _drawableResIconAdd = MutableStateFlow(Res.drawable._16_0)
      override val drawableResIconAdd: StateFlow<DrawableResource> = _drawableResIconAdd.asStateFlow()

      override val drawablePathIconUserAccount: String by Common.drawablePathIconUserAccountState.collectAsState()
      private val _drawableResIconUserAccount = MutableStateFlow(Res.drawable._17_0)
      override val drawableResIconUserAccount: StateFlow<DrawableResource> = _drawableResIconUserAccount.asStateFlow()

      override val drawablePathIconGoodsCategories: String by Common.drawablePathIconGoodsCategoriesState.collectAsState()
      private val _drawableResIconGoodsCategories = MutableStateFlow(Res.drawable._18_0)
      override val drawableResIconGoodsCategories: StateFlow<DrawableResource> =
        _drawableResIconGoodsCategories.asStateFlow()

      override val drawablePathIconStores: String by Common.drawablePathIconStoresState.collectAsState()
      private val _drawableResIconStores = MutableStateFlow(Res.drawable._19_0)
      override val drawableResIconStores: StateFlow<DrawableResource> = _drawableResIconStores.asStateFlow()

      override val drawablePathIconTransactionHistory: String by Common.drawablePathIconTransactionHistoryState.collectAsState()
      private val _drawableResIconTransactionHistory = MutableStateFlow(Res.drawable._20_0)
      override val drawableResIconTransactionHistory: StateFlow<DrawableResource> =
        _drawableResIconTransactionHistory.asStateFlow()

      override val drawablePathIconAnalytics: String by Common.drawablePathIconAnalyticsState.collectAsState()
      private val _drawableResIconAnalytics = MutableStateFlow(Res.drawable._21_0)
      override val drawableResIconAnalytics: StateFlow<DrawableResource> = _drawableResIconAnalytics.asStateFlow()

      override val drawablePathIconWorkers: String by Common.drawablePathIconWorkersState.collectAsState()
      private val _drawableResIconWorkers = MutableStateFlow(Res.drawable._22_0)
      override val drawableResIconWorkers: StateFlow<DrawableResource> = _drawableResIconWorkers.asStateFlow()

      override val drawablePathIconSuppliers: String by Common.drawablePathIconSuppliersState.collectAsState()
      private val _drawableResIconSuppliers = MutableStateFlow(Res.drawable._23_0)
      override val drawableResIconSuppliers: StateFlow<DrawableResource> = _drawableResIconSuppliers.asStateFlow()

      override val drawablePathIconDebtors: String by Common.drawablePathIconDebtorsState.collectAsState()
      private val _drawableResIconDebtors = MutableStateFlow(Res.drawable._24_0)
      override val drawableResIconDebtors: StateFlow<DrawableResource> = _drawableResIconDebtors.asStateFlow()

      override val drawablePathIconDevices: String by Common.drawablePathIconDevicesState.collectAsState()
      private val _drawableResIconDevices = MutableStateFlow(Res.drawable._25_0)
      override val drawableResIconDevices: StateFlow<DrawableResource> = _drawableResIconDevices.asStateFlow()

      override val drawablePathIconAppLanguage: String by Common.drawablePathIconAppLanguageState.collectAsState()
      private val _drawableResIconAppLanguage = MutableStateFlow(Res.drawable._26_0)
      override val drawableResIconAppLanguage: StateFlow<DrawableResource> = _drawableResIconAppLanguage.asStateFlow()

      override val drawablePathIconAppTheme: String by Common.drawablePathIconAppThemeState.collectAsState()
      private val _drawableResIconAppTheme = MutableStateFlow(Res.drawable._27_0)
      override val drawableResIconAppTheme: StateFlow<DrawableResource> = _drawableResIconAppTheme.asStateFlow()

      override val drawablePathIconCheck: String by Common.drawablePathIconCheckState.collectAsState()
      private val _drawableResIconCheck = MutableStateFlow(Res.drawable._28_0)
      override val drawableResIconCheck: StateFlow<DrawableResource> = _drawableResIconCheck.asStateFlow()

      override val drawablePathIconEdit: String by Common.drawablePathIconEditState.collectAsState()
      private val _drawableResIconEdit = MutableStateFlow(Res.drawable._29_0)
      override val drawableResIconEdit: StateFlow<DrawableResource> = _drawableResIconEdit.asStateFlow()

      override val drawablePathIconSettings: String by Common.drawablePathIconSettingsState.collectAsState()
      private val _drawableResIconSettings = MutableStateFlow(Res.drawable._30_0)
      override val drawableResIconSettings: StateFlow<DrawableResource> = _drawableResIconSettings.asStateFlow()

      override val drawablePathIconSearch: String by Common.drawablePathIconSearchState.collectAsState()
      private val _drawableResIconSearch = MutableStateFlow(Res.drawable._31_0)
      override val drawableResIconSearch: StateFlow<DrawableResource> = _drawableResIconSearch.asStateFlow()

      override val drawablePathIconBarcodeCamScanner: String by Common.drawablePathIconBarcodeCamScannerState.collectAsState()
      private val _drawableResIconBarcodeCamScanner = MutableStateFlow(Res.drawable._32_0)
      override val drawableResIconBarcodeCamScanner: StateFlow<DrawableResource> =
        _drawableResIconBarcodeCamScanner.asStateFlow()

      override val drawablePathIconDelete: String by Common.drawablePathIconDeleteState.collectAsState()
      private val _drawableResIconDelete = MutableStateFlow(Res.drawable._33_0)
      override val drawableResIconDelete: StateFlow<DrawableResource> = _drawableResIconDelete.asStateFlow()

      override val drawablePathIconExit: String by Common.drawablePathIconExitState.collectAsState()
      private val _drawableResIconExit = MutableStateFlow(Res.drawable._34_0)
      override val drawableResIconExit: StateFlow<DrawableResource> = _drawableResIconExit.asStateFlow()

      override val drawablePathIconSwitch: String by Common.drawablePathIconSwitchState.collectAsState()
      private val _drawableResIconSwitch = MutableStateFlow(Res.drawable._35_0)
      override val drawableResIconSwitch: StateFlow<DrawableResource> = _drawableResIconSwitch.asStateFlow()

      override val drawablePathIconCart: String by Common.drawablePathIconCartState.collectAsState()
      private val _drawableResIconCart = MutableStateFlow(Res.drawable._36_0)
      override val drawableResIconCart: StateFlow<DrawableResource> = _drawableResIconCart.asStateFlow()

      override val drawablePathIconAddCart: String by Common.drawablePathIconAddCartState.collectAsState()
      private val _drawableResIconAddCart = MutableStateFlow(Res.drawable._37_0)
      override val drawableResIconAddCart: StateFlow<DrawableResource> = _drawableResIconAddCart.asStateFlow()

      override val drawablePathIconSubtract: String by Common.drawablePathIconSubtractState.collectAsState()
      private val _drawableResIconSubtract = MutableStateFlow(Res.drawable._38_0)
      override val drawableResIconSubtract: StateFlow<DrawableResource> = _drawableResIconSubtract.asStateFlow()

      override val drawablePathIconReceipt: String by Common.drawablePathIconReceiptState.collectAsState()
      private val _drawableResIconReceipt = MutableStateFlow(Res.drawable._39_0)
      override val drawableResIconReceipt: StateFlow<DrawableResource> = _drawableResIconReceipt.asStateFlow()

      override val drawablePathIconFinances: String by Common.drawablePathIconFinancesState.collectAsState()

      private val _drawableResIconFinances = MutableStateFlow(Res.drawable._40_0)
      override val drawableResIconFinances = _drawableResIconFinances.asStateFlow()

      override suspend fun updateDrawableResources() {
        _drawableResAITALogo.emit(if (stateValues.appThemeId == 1L) Res.drawable._0_1 else Res.drawable._0_0)

        _drawableResIconPassword.emit(if (stateValues.appThemeId == 1L) Res.drawable._1_1 else Res.drawable._1_0)

        _drawableResIconCancel.emit(if (stateValues.appThemeId == 1L) Res.drawable._2_1 else Res.drawable._2_0)

        _drawableResIconEyeHide.emit(if (stateValues.appThemeId == 1L) Res.drawable._3_1 else Res.drawable._3_0)

        _drawableResIconEyeShow.emit(if (stateValues.appThemeId == 1L) Res.drawable._4_1 else Res.drawable._4_0)

        _drawableResIconEmail.emit(if (stateValues.appThemeId == 1L) Res.drawable._5_1 else Res.drawable._5_0)

        _drawableResIconPhone.emit(if (stateValues.appThemeId == 1L) Res.drawable._6_1 else Res.drawable._6_0)

        _drawableResIconExpandMore.emit(if (stateValues.appThemeId == 1L) Res.drawable._7_1 else Res.drawable._7_0)

        _drawableResIconExpandLess.emit(if (stateValues.appThemeId == 1L) Res.drawable._8_1 else Res.drawable._8_0)

        _drawableResIconPerson.emit(if (stateValues.appThemeId == 1L) Res.drawable._9_1 else Res.drawable._9_0)

        _drawableResIconTransactionSale.emit(if (stateValues.appThemeId == 1L) Res.drawable._10_1 else Res.drawable._10_0)

        _drawableResIconTransactionReturn.emit(if (stateValues.appThemeId == 1L) Res.drawable._11_1 else Res.drawable._11_0)

        _drawableResIconTransactionSupply.emit(if (stateValues.appThemeId == 1L) Res.drawable._12_1 else Res.drawable._12_0)

        _drawableResIconStock.emit(if (stateValues.appThemeId == 1L) Res.drawable._13_1 else Res.drawable._13_0)

        _drawableResIconMenu.emit(if (stateValues.appThemeId == 1L) Res.drawable._14_1 else Res.drawable._14_0)

        _drawableResIconBackArrow.emit(if (stateValues.appThemeId == 1L) Res.drawable._15_1 else Res.drawable._15_0)

        _drawableResIconAdd.emit(if (stateValues.appThemeId == 1L) Res.drawable._16_1 else Res.drawable._16_0)

        _drawableResIconUserAccount.emit(if (stateValues.appThemeId == 1L) Res.drawable._17_1 else Res.drawable._17_0)

        _drawableResIconGoodsCategories.emit(if (stateValues.appThemeId == 1L) Res.drawable._18_1 else Res.drawable._18_0)

        _drawableResIconStores.emit(if (stateValues.appThemeId == 1L) Res.drawable._19_1 else Res.drawable._19_0)

        _drawableResIconTransactionHistory.emit(if (stateValues.appThemeId == 1L) Res.drawable._20_1 else Res.drawable._20_0)

        _drawableResIconAnalytics.emit(if (stateValues.appThemeId == 1L) Res.drawable._21_1 else Res.drawable._21_0)

        _drawableResIconWorkers.emit(if (stateValues.appThemeId == 1L) Res.drawable._22_1 else Res.drawable._22_0)

        _drawableResIconSuppliers.emit(if (stateValues.appThemeId == 1L) Res.drawable._23_1 else Res.drawable._23_0)

        _drawableResIconDebtors.emit(if (stateValues.appThemeId == 1L) Res.drawable._24_1 else Res.drawable._24_0)

        _drawableResIconDevices.emit(if (stateValues.appThemeId == 1L) Res.drawable._25_1 else Res.drawable._25_0)

        _drawableResIconAppLanguage.emit(if (stateValues.appThemeId == 1L) Res.drawable._26_1 else Res.drawable._26_0)

        _drawableResIconAppTheme.emit(if (stateValues.appThemeId == 1L) Res.drawable._27_1 else Res.drawable._27_0)

        _drawableResIconCheck.emit(if (stateValues.appThemeId == 1L) Res.drawable._28_1 else Res.drawable._28_0)

        _drawableResIconEdit.emit(if (stateValues.appThemeId == 1L) Res.drawable._29_1 else Res.drawable._29_0)

        _drawableResIconSettings.emit(if (stateValues.appThemeId == 1L) Res.drawable._30_1 else Res.drawable._30_0)

        _drawableResIconSearch.emit(if (stateValues.appThemeId == 1L) Res.drawable._31_1 else Res.drawable._31_0)

        _drawableResIconBarcodeCamScanner.emit(if (stateValues.appThemeId == 1L) Res.drawable._32_1 else Res.drawable._32_0)

        _drawableResIconDelete.emit(if (stateValues.appThemeId == 1L) Res.drawable._33_1 else Res.drawable._33_0)

        _drawableResIconExit.emit(if (stateValues.appThemeId == 1L) Res.drawable._34_1 else Res.drawable._34_0)

        _drawableResIconSwitch.emit(if (stateValues.appThemeId == 1L) Res.drawable._35_1 else Res.drawable._35_0)

        _drawableResIconCart.emit(if (stateValues.appThemeId == 1L) Res.drawable._36_1 else Res.drawable._36_0)

        _drawableResIconAddCart.emit(if (stateValues.appThemeId == 1L) Res.drawable._37_1 else Res.drawable._37_0)

        _drawableResIconSubtract.emit(if (stateValues.appThemeId == 1L) Res.drawable._38_1 else Res.drawable._38_0)

        _drawableResIconReceipt.emit(if (stateValues.appThemeId == 1L) Res.drawable._39_1 else Res.drawable._39_0)
        _drawableResIconFinances.emit(if (stateValues.appThemeId == 1L) Res.drawable._40_1 else Res.drawable._40_0)
      }
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
            Common
              .stringsState
              .payload
              .collect {
                it?.let {
                  Common.updateStrings(it, resourceStrings)
                }
              }
          }

          launch(Dispatchers.io) {
            Common
              .appLanguageState
              .collect {
                Common
                  .stringsState
                  .payloadValue?.let {
                    Common.updateStrings(it, resourceStrings)
                  }
              }
          }

          launch(Dispatchers.io) {
            Common
              .dimensionsState
              .payload
              .collect {
                it?.let {
                  updateDimensions(it, resourceDimensions)
                }
              }
          }

          launch(Dispatchers.io) {
            Common
              .colorsState
              .payload
              .collect {
                it?.let {
                  updateColors(it, resourceColors)
                }
              }
          }

          launch(Dispatchers.io) {
            Common
              .drawablesState
              .payload
              .collect {
                it?.let {
                  Common.updateDrawables(it, resourceDrawables)
                  stateValues.updateDrawableResources()
                }
              }
          }

          launch(Dispatchers.io) {
            Common
              .appThemeIdState
              .collect {
                Common
                  .colorsState
                  .payloadValue?.let {
                    updateColors(it, resourceColors)
                  }

                Common
                  .drawablesState
                  .payloadValue?.let {
                    Common.updateDrawables(it, resourceDrawables)
                  }
              }
          }

          launch(Dispatchers.io) {
            Common
              .appSizeModeIdState
              .collect {
                Common
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

  private suspend fun updateDimensions(
    dimensions: List<StylizedDimensionGroupDataModel>,
    resourceDimensions: List<StylizedDimensionGroupDataModel>
  ) {
    _wideScreenMinWidthState.emit(
      dimensions.extractValue(4, stateValues.appSizeModeId) ?: resourceDimensions.extractValue(
        4,
        stateValues.appSizeModeId
      )!!
    )
    _boundWidgetWidthState.emit(
      (dimensions.extractValue(9, stateValues.appSizeModeId) ?: resourceDimensions.extractValue(
        9,
        stateValues.appSizeModeId
      )!!).dp
    )

    _textSizeState.emit(
      (dimensions.extractValue(0, stateValues.appSizeModeId) ?: resourceDimensions.extractValue(
        0,
        stateValues.appSizeModeId
      )!!).sp
    )
    _titleTextSizeState.emit(
      (dimensions.extractValue(1, stateValues.appSizeModeId) ?: resourceDimensions.extractValue(
        1,
        stateValues.appSizeModeId
      )!!).sp
    )
    _accentTextSizeState.emit(
      (dimensions.extractValue(2, stateValues.appSizeModeId) ?: resourceDimensions.extractValue(
        2,
        stateValues.appSizeModeId
      )!!).sp
    )
    _smallTextSizeState.emit(
      (dimensions.extractValue(3, stateValues.appSizeModeId) ?: resourceDimensions.extractValue(
        3,
        stateValues.appSizeModeId
      )!!).sp
    )

    _focusedBorderWidthState.emit(
      (dimensions.extractValue(5, stateValues.appSizeModeId) ?: resourceDimensions.extractValue(
        5,
        stateValues.appSizeModeId
      )!!).dp
    )
    _unfocusedBorderWidthState.emit(
      (dimensions.extractValue(6, stateValues.appSizeModeId) ?: resourceDimensions.extractValue(
        6,
        stateValues.appSizeModeId
      )!!).dp
    )

    _cornerRadiusState.emit(
      (dimensions.extractValue(7, stateValues.appSizeModeId) ?: resourceDimensions.extractValue(
        7,
        stateValues.appSizeModeId
      )!!).dp
    )
    _iconSizeState.emit(
      (dimensions.extractValue(8, stateValues.appSizeModeId) ?: resourceDimensions.extractValue(
        8,
        stateValues.appSizeModeId
      )!!).dp
    )
    _textFieldHeightMultiplierRelativeToTextSizeState.emit(
      dimensions.extractValue(10, stateValues.appSizeModeId) ?: resourceDimensions.extractValue(
        10,
        stateValues.appSizeModeId
      )!!
    )
    _textFieldHeightState.emit((_textSizeState.value.value * _textFieldHeightMultiplierRelativeToTextSizeState.value).dp)
    _textFieldIconPaddingState.emit(
      (dimensions.extractValue(11, stateValues.appSizeModeId) ?: resourceDimensions.extractValue(
        11,
        stateValues.appSizeModeId
      )!!).dp
    )
  }

  private suspend fun updateColors(
    colors: List<StylizedColorGroupDataModel>,
    resourceColors: List<StylizedColorGroupDataModel>
  ) {
    _AccentColorState.emit(
      (colors.extractColor(0, Common.appThemeIdState.value) ?: resourceColors.extractColor(
        0,
        Common.appThemeIdState.value
      )!!).toColor()
    )
    _BackgroundColorState.emit(
      (colors.extractColor(1, Common.appThemeIdState.value) ?: resourceColors.extractColor(
        1,
        Common.appThemeIdState.value
      )!!).toColor()
    )
    _TextColorState.emit(
      (colors.extractColor(2, Common.appThemeIdState.value) ?: resourceColors.extractColor(
        2,
        Common.appThemeIdState.value
      )!!).toColor()
    )
    _AccentTextColorState.emit(
      (colors.extractColor(3, Common.appThemeIdState.value) ?: resourceColors.extractColor(
        3,
        Common.appThemeIdState.value
      )!!).toColor()
    )
    _PlaceholderTextColorState.emit(
      (colors.extractColor(4, Common.appThemeIdState.value) ?: resourceColors.extractColor(
        4,
        Common.appThemeIdState.value
      )!!).toColor()
    )
    _DisabledColorState.emit(
      (colors.extractColor(5, Common.appThemeIdState.value) ?: resourceColors.extractColor(
        5,
        Common.appThemeIdState.value
      )!!).toColor()
    )
    _ErrorColorState.emit(
      (colors.extractColor(6, Common.appThemeIdState.value) ?: resourceColors.extractColor(
        6,
        Common.appThemeIdState.value
      )!!).toColor()
    )
    _IconTintColorState.emit(
      (colors.extractColor(7, Common.appThemeIdState.value) ?: resourceColors.extractColor(
        7,
        Common.appThemeIdState.value
      )!!).toColor()
    )
    _OkayColorState.emit(
      (colors.extractColor(8, Common.appThemeIdState.value) ?: resourceColors.extractColor(
        8,
        Common.appThemeIdState.value
      )!!).toColor()
    )
    _BorderlineBadColorState.emit(
      (colors.extractColor(9, Common.appThemeIdState.value) ?: resourceColors.extractColor(
        9,
        Common.appThemeIdState.value
      )!!).toColor()
    )
  }
}