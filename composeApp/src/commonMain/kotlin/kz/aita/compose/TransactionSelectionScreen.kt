package kz.aita.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import kz.aita.GoodsItemDataModel
import kz.aita.LocalizedStringDataModel
import kz.aita.QuantityDataModel
import kz.aita.cartRepository

@Composable
fun AppConfiguration.TransactionSelectionScreen() {
  Column(
    modifier = Modifier
      .fillMaxSize()
  ) {
    val transactionTypeIndex = when (stateValues.navigationScreensMain.last()) {
      is NavigationScreenModel.Transaction.MainSale -> {
        0
      }

      is NavigationScreenModel.Transaction.MainReturn -> {
        1
      }

      else -> {
        2
      }
    }

    val clientId = when (stateValues.navigationScreensMain.last()) {
      is NavigationScreenModel.Transaction.MainReturn -> {
        stateValues.navigationTransactionReturnClientId
      }

      is NavigationScreenModel.Transaction.MainSupply -> {
        stateValues.navigationTransactionSupplyClientId
      }

      else -> {
        stateValues.navigationTransactionSaleClientId
      }
    }

    ScreenAppBarWidget(
      title = stateValues.stringSelect,
      onBack = if (!Navigation.TransactionSale.isVeryFirstScreen(stateValues.isNarrowScreen, clientId)) {
        {
          coroutineScope.launch {
            Navigation.Menu.pop(stateValues.isNarrowScreen)
          }
        }
      } else null
    )

    var searchTextFieldFocused by rememberSaveable {
      mutableStateOf(true)
    }

    val searchTextFieldContent =
      searchTextField(
        stateHost = when (transactionTypeIndex) {
          0 -> NavigationScreenModel.Transaction.MainSale
          1 -> NavigationScreenModel.Transaction.MainReturn
          else -> NavigationScreenModel.Transaction.MainSupply
        },
        stateKey = NavigationScreenModel.KEY_STATE_SEARCH_QUERY,
        isFocusedInitial = searchTextFieldFocused,
        forceRefocus = true,
        modifier = Modifier
          .padding(stateValues.marginTextField),
        barcodeCamScanner = true
      )

    val scopeRowContent = tabRowWidget(
      modifier = Modifier
        .padding(horizontal = stateValues.marginTextField),
      tabs = listOf(
        TabContent("0", stateValues.stringAll),
        TabContent("1", stateValues.stringQuick)
      )
    )

    val addToCartAction: (GoodsItemDataModel) -> Unit = {
      cartRepository.addToCart(
        id = it.id,
        transactionTypeIndex,
        clientId,
        QuantityDataModel("", immutableUnitName = listOf(LocalizedStringDataModel("main", "pc.")), roundTotal = true) // TODO it.quantity.copy(total = it.quantity.total + it.quantity.pricedAmount)
      )
    }

    when (scopeRowContent.id) {
      "0" -> {
        StockWarehouseScreenContent(
          modifier = Modifier
            .weight(1f),
          searchTextFieldContent.value.text,
          onExactSearchHit = addToCartAction,
          disableIfOutOfStock = true,
          onClick = addToCartAction,
          showStockType = false
        )
      }

      "1" -> {
        StockWarehouseScreenContent(
          modifier = Modifier
            .weight(1f),
          searchTextFieldContent.value.text,
          onExactSearchHit = addToCartAction,
          disableIfOutOfStock = true,
          showStockType = false,
          onFilter = {
            it.isQuickItem
          },
          onClick = addToCartAction
        )
      }
    }
  }
}