@file:OptIn(DelicateCoroutinesApi::class)

package kz.aita

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import io.ktor.client.call.*
import io.ktor.client.plugins.auth.*
import io.ktor.client.plugins.auth.providers.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

  GlobalScope.launch(Dispatchers.io) {
    observeLocalKv(KEY_ACTIVE_STORE_ID)
      .collect {
        activeStoreId.emit(it)
        it?.let {
          getStock(it)
        }
      }
  }

  GlobalScope.launch(Dispatchers.io) {
    storesState.payload.collect {
      it?.let {
        if (it.size == 1 && activeStoreId.value == null) {
          activeStoreId.emit(it.first().id)
        }
      }
    }
  }

  GlobalScope.launch(Dispatchers.io) {
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

  GlobalScope.launch(Dispatchers.io) {
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

  GlobalScope.launch(Dispatchers.io) {

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

  GlobalScope.launch(Dispatchers.io) {

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

  GlobalScope.launch(Dispatchers.io) {
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

  GlobalScope.launch(Dispatchers.io) {

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

  GlobalScope.launch(Dispatchers.io) {
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

  GlobalScope.launch(Dispatchers.io) {
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

  GlobalScope.launch(Dispatchers.io) {
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

  GlobalScope.launch(Dispatchers.io) {
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

  GlobalScope.launch(Dispatchers.io) {
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

  GlobalScope.launch(Dispatchers.io) {
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

  GlobalScope.launch(Dispatchers.io) {
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

  GlobalScope.launch(Dispatchers.io) {

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

  GlobalScope.launch(Dispatchers.io) {
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
    GlobalScope.launch(Dispatchers.io) {
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
    GlobalScope.launch(Dispatchers.io) {
      getStringsMutex.withLock {
        val response = networkRequest<List<LocalizedStringGroupDataModel>, Unit>(
          method = HttpMethod.Get,
          endpointUrl = globalAppConfigurationState.payloadValue.stringResourcesPath.first
        )

        if (response.negative) {
          stringsState.emit(DataState.Empty(response.message))
        } else {
          stringsState.emit(DataState.Success(response.payload!!, response.message))
        }
      }

    }
}

fun getDimensions() {
  if (!getDimensionsMutex.isLocked)
    GlobalScope.launch(Dispatchers.io) {
      getDimensionsMutex.withLock {
        val response = networkRequest<List<StylizedDimensionGroupDataModel>, Unit>(
          method = HttpMethod.Get,
          endpointUrl = globalAppConfigurationState.payloadValue.dimensionResourcesPath.first,
        )

        if (response.negative) {
          dimensionsState.emit(DataState.Empty(response.message))
        } else {
          dimensionsState.emit(DataState.Success(response.payload!!, response.message))
        }
      }
    }
}

fun getColors() {
  if (!getColorsMutex.isLocked)
    GlobalScope.launch(Dispatchers.io) {
      getColorsMutex.withLock {
        val response = networkRequest<List<StylizedColorGroupDataModel>, Unit>(
          method = HttpMethod.Get,
          endpointUrl = globalAppConfigurationState.payloadValue.colorResourcesPath.first
        )

        if (response.negative) {
          colorsState.emit(DataState.Empty(response.message))
        } else {
          colorsState.emit(DataState.Success(response.payload!!, response.message))
        }
      }

    }
}

fun getDrawables() {
  if (!getDrawablesMutex.isLocked)
    GlobalScope.launch(Dispatchers.io) {
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
  GlobalScope.launch(Dispatchers.io) {
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
  GlobalScope.launch(Dispatchers.io) {
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
  GlobalScope.launch(Dispatchers.io) {
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
    .mapToOneOrNull(Dispatchers.io)
    .map {
      it?.value_
    }

suspend fun upsertCart(
  id: String,
  transactionTypeIndex: Int,
  clientId: Int,
  quantity: QuantityDataModel
) {
  appDatabase.app_databaseQueries.upsertCart(
    id,
    transactionTypeIndex.toLong(),
    clientId.toLong(),
    jsonBase.encodeToString(quantity)
  )
}

suspend fun deleteCart(transactionTypeIndex: Int, clientId: Int) {
  appDatabase.app_databaseQueries.deleteCart(transactionTypeIndex.toLong(), clientId.toLong())
}

suspend fun deleteCartById(id: String, transactionTypeIndex: Int, clientId: Int) {
  appDatabase.app_databaseQueries.deleteCartById(id, transactionTypeIndex.toLong(), clientId.toLong())
}

suspend fun deleteCartItemById(id: String) {
  appDatabase.app_databaseQueries.deleteById(id)
}

fun observeCart(transactionTypeIndex: Int, clientId: Int): Flow<List<GoodsItemInCartDataModel>?> =
  appDatabase.app_databaseQueries.getCart(transactionTypeIndex.toLong(), clientId.toLong())
    .asFlow()
    .mapToList(Dispatchers.io)
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
    GlobalScope.launch(Dispatchers.io) {
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
  GlobalScope.launch(Dispatchers.io) {
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
  GlobalScope.launch(Dispatchers.io) {
    latestInAppNotificationState.emit(null)
  }
}

fun logInUser(userAuthLogIn: UserAuthLogInDataModel) {
  if (!logInMutex.isLocked)
    GlobalScope.launch(Dispatchers.io) {
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
          tokenStore?.set(response.payload)
          httpClient.authProvider<BearerAuthProvider>()?.clearToken()
          getUser()
        }
      }
    }
}

fun signUpUser(userAuthSignUp: UserAuthSignUpDataModel) {
  if (!signUpUserMutex.isLocked)
    GlobalScope.launch(Dispatchers.io) {
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
          tokenStore?.set(response.payload)
          httpClient.authProvider<BearerAuthProvider>()?.clearToken()

          getUser()
        }
      }
    }
}

fun logOutUser() {
  if (!logOutUserMutex.isLocked)
    GlobalScope.launch(Dispatchers.io) {
      logOutUserMutex.withLock {
        val response = networkRequest<Unit, String>(
          HttpMethod.Delete,
          endpointUrl = globalAppConfigurationState.payloadValue.logOutPath.first,
          body = tokenStore?.get()?.refreshToken
        )

        if (response.negative) {
          postInAppNotification(response.message, NotificationType.Negative)
        } else {
          postInAppNotification(response.message, NotificationType.Positive)
          userAccountState.emit(DataState.Empty())

          tokenStore?.set(null)
          userAccountStore?.set(null)
          setActiveStoreId(null)
          httpClient.authProvider<BearerAuthProvider>()?.clearToken()
        }
      }
    }
}

fun getUser(forceLogOut: Boolean = true) {
  if (!getUserAccountMutex.isLocked)
    GlobalScope.launch(Dispatchers.io) {
      if (tokenStore?.get() != null)
        getUserAccountMutex.withLock {
          userAccountStore?.get()?.run {
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
            userAccountStore?.set(response.payload)
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
    GlobalScope.launch(Dispatchers.io) {
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
          userAccountStore?.set(response.payload)

        }
      }
    }
}

fun forceLogOutUser() {
  GlobalScope.launch(Dispatchers.io) {
    tokenStore?.set(null)
    userAccountStore?.set(null)
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
    GlobalScope.launch(Dispatchers.io) {
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
    GlobalScope.launch(Dispatchers.io) {
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
    GlobalScope.launch(Dispatchers.io) {
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
  GlobalScope.launch(Dispatchers.io) {
    putLocalKv(KEY_ACTIVE_STORE_ID, id)
  }
}

val suppliersState = MutableDataStateFlow<List<SupplierDataModel>>(GlobalScope)

private val getSuppliersMutex = Mutex()

fun getSuppliers() {
  if (!getSuppliersMutex.isLocked)
    GlobalScope.launch(Dispatchers.io) {
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
    GlobalScope.launch(Dispatchers.io) {
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
    GlobalScope.launch(Dispatchers.io) {
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
    GlobalScope.launch(Dispatchers.io) {
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
    GlobalScope.launch(Dispatchers.io) {
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
    GlobalScope.launch(Dispatchers.io) {
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
    GlobalScope.launch(Dispatchers.io) {
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
    GlobalScope.launch(Dispatchers.io) {
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
    GlobalScope.launch(Dispatchers.io) {
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
    GlobalScope.launch(Dispatchers.io) {
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

          stockBatchesState.emit(
            DataState.Success(
              mutableListOf<GoodsBatchDataModel>().also { newList ->
                (stockBatchesState.value.value as? DataState.Success)?.payload?.let {
                  newList.addAll(it)
                  newList.removeAll { item -> item.id == response.payload }
                }
              }
            )
          )

          onCompleted?.invoke()
        }
      }
    }
}
