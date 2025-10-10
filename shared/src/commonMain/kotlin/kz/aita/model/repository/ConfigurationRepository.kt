package kz.aita.model.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kz.aita.model.dataModel.*
import kz.aita.model.wrapper.DataState
import kz.aita.model.wrapper.DataStateFlow
import kz.aita.model.wrapper.DataStateFlowNonNull

interface ConfigurationRepository {

  val globalAppConfigurationState: DataStateFlowNonNull<GlobalAppConfigurationDataModel>
  val stringsState: DataStateFlow<List<LocalizedStringGroupDataModel>>
  val dimensionsState: DataStateFlow<List<StylizedDimensionGroupDataModel>>
  val colorsState: DataStateFlow<List<StylizedColorGroupDataModel>>
  val drawablesState: DataStateFlow<List<StylizedDrawablePathsGroupDataModel>>
  val exceptionsState: DataStateFlow<List<ExceptionDataModel>>

  val appLanguageState: StateFlow<String>
  val appThemeIdState: StateFlow<Long>
  val appSizeModeIdState: StateFlow<Long>

  val stringAppNameState: StateFlow<String>
  val stringLogInState: StateFlow<String>
  val stringPhoneNumberState: StateFlow<String>
  val stringEnterPhoneNumberState: StateFlow<String>
  val stringEmailState: StateFlow<String>
  val stringEnterEmailAddressState: StateFlow<String>
  val stringPasswordState: StateFlow<String>
  val stringEnterPasswordState: StateFlow<String>
  val stringCancelState: StateFlow<String>
  val stringClearState: StateFlow<String>
  val stringLoginAndOrPasswordIncorrectState: StateFlow<String>
  val stringPhoneNumberMustBeState: StateFlow<String>
  val stringEmailMustBeState: StateFlow<String>
  val stringPasswordMustBeState: StateFlow<String>
  val stringRepeatPasswordState: StateFlow<String>
  val stringPasswordsMustMatchState: StateFlow<String>
  val stringFirstNameState: StateFlow<String>
  val stringLastNameState: StateFlow<String>
  val stringEnterFirstNameState: StateFlow<String>
  val stringEnterLastNameState: StateFlow<String>
  val stringUserWithThisPhoneNumberIsAlreadyRegisteredState: StateFlow<String>
  val stringUserWithThisEmailAddressIsAlreadyRegisteredState: StateFlow<String>
  val stringSignUpState: StateFlow<String>
  val stringConfirmState: StateFlow<String>
  val stringSaleState: StateFlow<String>
  val stringReturnState: StateFlow<String>
  val stringSupplyState: StateFlow<String>
  val stringStockState: StateFlow<String>
  val stringMenuState: StateFlow<String>
  val stringBackState: StateFlow<String>
  val stringAddGoodsItemState: StateFlow<String>
  val stringEditGoodsItemState: StateFlow<String>
  val stringUserAccountState: StateFlow<String>
  val stringGoodsCategoriesState: StateFlow<String>
  val stringAddGoodsCategoryState: StateFlow<String>
  val stringEditGoodsCategoryState: StateFlow<String>
  val stringStoresState: StateFlow<String>
  val stringAddStoreState: StateFlow<String>
  val stringEditStoreState: StateFlow<String>
  val stringSubscriptionState: StateFlow<String>
  val stringSubscriptionPlansState: StateFlow<String>
  val stringTransactionHistoryState: StateFlow<String>
  val stringReceiptState: StateFlow<String>
  val stringAnalyticsState: StateFlow<String>
  val stringWorkersState: StateFlow<String>
  val stringAddWorkerState: StateFlow<String>
  val stringEditWorkerState: StateFlow<String>
  val stringSuppliersState: StateFlow<String>
  val stringAddSupplierState: StateFlow<String>
  val stringEditSupplierState: StateFlow<String>
  val stringDebtorsState: StateFlow<String>
  val stringCloseDebtState: StateFlow<String>
  val stringDevicesState: StateFlow<String>
  val stringAppLanguageState: StateFlow<String>
  val stringAppThemeState: StateFlow<String>
  val stringSelectState: StateFlow<String>
  val stringUserWithThisPhoneNumberAndEmailAddressIsAlreadyRegisteredState: StateFlow<String>
  val stringFirstNameCannotBeEmptyOrJustWhitespacesState: StateFlow<String>
  val stringLastNameCannotBeEmptyOrJustWhitespacesState: StateFlow<String>
  val stringSystemLanguageState: StateFlow<String>
  val stringBluetoothPermissionRequiredState: StateFlow<String>
  val stringForSearchAndConnectionToBluetoothBarcodeScannersAndReceiptPrintersState: StateFlow<String>
  val stringForSearchAndConnectionToBluetoothBarcodeScannersAndReceiptPrintersYouCanGrantItInAppSettingsState: StateFlow<String>
  val stringBluetoothDisabledState: StateFlow<String>
  val stringEnableForSearchAndConnectionToBluetoothBarcodeScannersAndReceiptPrintersState: StateFlow<String>
  val stringSearchByAnyDataState: StateFlow<String>
  val stringListEmptyState: StateFlow<String>
  val stringNoMatchesState: StateFlow<String>
  val stringNameState: StateFlow<String>
  val stringBarcodeState: StateFlow<String>
  val stringSupplyPriceState: StateFlow<String>
  val stringSalePriceState: StateFlow<String>
  val stringReturnPriceState: StateFlow<String>
  val stringCategoryState: StateFlow<String>
  val stringSupplierState : StateFlow<String>
  val stringEnterNameState: StateFlow<String>
  val stringEnterBarcodeState: StateFlow<String>
  val stringEnterSupplyPriceState: StateFlow<String>
  val stringEnterSalePriceState: StateFlow<String>
  val stringEnterReturnPriceState: StateFlow<String>
  val stringSelectCategoryState: StateFlow<String>
  val stringSelectSupplierState : StateFlow<String>
  val stringEditState: StateFlow<String>
  val stringChangePasswordState: StateFlow<String>
  val stringNewPasswordState: StateFlow<String>
  val stringEnterNewPasswordState: StateFlow<String>
  val stringRepeatNewPasswordState: StateFlow<String>
  val stringConfirmationPasswordState: StateFlow<String>
  val stringRequiredToEditAccountState: StateFlow<String>
  val stringAccountSuccessfullyUpdatedState: StateFlow<String>
  val stringLoggingOutInProgressState: StateFlow<String>
  val stringSessionTimeExpiredLoggingOutState: StateFlow<String>
  val stringAliasState: StateFlow<String>
  val stringDescriptionState: StateFlow<String>
  val stringEnterAliasState: StateFlow<String>
  val stringEnterDescriptionState: StateFlow<String>
  val stringOptionalState: StateFlow<String>

  val drawablePathAITALogoState: StateFlow<String>
  val drawablePathIconPasswordState: StateFlow<String>
  val drawablePathIconCancelState: StateFlow<String>
  val drawablePathIconEyeHideState: StateFlow<String>
  val drawablePathIconEyeShowState: StateFlow<String>
  val drawablePathIconEmailState: StateFlow<String>
  val drawablePathIconPhoneState: StateFlow<String>
  val drawablePathIconExpandMoreState: StateFlow<String>
  val drawablePathIconExpandLessState: StateFlow<String>
  val drawablePathIconPersonState: StateFlow<String>
  val drawablePathIconTransactionSaleState: StateFlow<String>
  val drawablePathIconTransactionReturnState: StateFlow<String>
  val drawablePathIconTransactionSupplyState: StateFlow<String>
  val drawablePathIconStockState: StateFlow<String>
  val drawablePathIconMenuState: StateFlow<String>
  val drawablePathIconBackArrowState: StateFlow<String>
  val drawablePathIconAddState: StateFlow<String>
  val drawablePathIconUserAccountState: StateFlow<String>
  val drawablePathIconGoodsCategoriesState: StateFlow<String>
  val drawablePathIconStoresState: StateFlow<String>
  val drawablePathIconTransactionHistoryState: StateFlow<String>
  val drawablePathIconAnalyticsState: StateFlow<String>
  val drawablePathIconWorkersState: StateFlow<String>
  val drawablePathIconSuppliersState: StateFlow<String>
  val drawablePathIconDebtorsState: StateFlow<String>
  val drawablePathIconDevicesState: StateFlow<String>
  val drawablePathIconAppLanguageState: StateFlow<String>
  val drawablePathIconAppThemeState: StateFlow<String>
  val drawablePathIconCheckState: StateFlow<String>
  val drawablePathIconEditState: StateFlow<String>
  val drawablePathIconSettingsState: StateFlow<String>
  val drawablePathIconSearchState: StateFlow<String>
  val drawablePathIconBarcodeCamScannerState: StateFlow<String>
  val drawablePathIconDeleteState: StateFlow<String>
  val drawablePathIconExitState: StateFlow<String>

  val exceptionMessageUserWithThisPhoneNumberIsAlreadyRegisteredState: StateFlow<String>
  val exceptionMessageUserWithThisEmailAddressIsAlreadyRegisteredState: StateFlow<String>
  val exceptionMessageUserWithThisPhoneNumberAndEmailAddressIsAlreadyRegisteredState: StateFlow<String>
  val exceptionMessageRefreshTokenExpiredState: StateFlow<String>
  val exceptionMessageLoginAndOrPasswordIncorrectState: StateFlow<String>
  val exceptionMessagePleaseLogInFirstState: StateFlow<String>
  val exceptionMessageIncorrectPasswordState: StateFlow<String>
  
  fun getGlobalAppConfiguration(loadAll: Boolean = true)

  fun getStrings()

  fun getDimensions()

  fun getColors()

  fun getDrawables()

  fun getDrawable(key: Long, themeId: Long, format: String): Flow<DataState<String>>

  fun getDrawable(name: String, format: String): Flow<DataState<String>>

  fun getExceptions()
  
  fun setAppLocale(language: String)

  fun setAppTheme(themeId: Long)

  fun setAppSizeMode(sizeModeId: Long)

  fun updateGlobalAppConfiguration(configuration: GlobalAppConfigurationDataModel, resourceConfiguration: GlobalAppConfigurationDataModel)
  fun updateStrings(strings: List<LocalizedStringGroupDataModel>, resourceStrings: List<LocalizedStringGroupDataModel>)

  fun updateDrawables(drawables: List<StylizedDrawablePathsGroupDataModel>, resourceDrawables: List<StylizedDrawablePathsGroupDataModel>)

  fun updateExceptions(exceptions: List<ExceptionDataModel>, resourceExceptions: List<ExceptionDataModel>)
}
