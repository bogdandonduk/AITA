package kz.aita.compose.screen.transaction

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import kotlinx.io.files.Path
import kz.aita.AppConfiguration
import kz.aita.compose.navigation.Navigation
import kz.aita.compose.navigation.NavigationScreenModel
import kz.aita.compose.screen.stock.StockWarehouseScreenContent
import kz.aita.compose.widget.ScreenAppBarWidget
import kz.aita.compose.widget.TabContent
import kz.aita.compose.widget.searchTextField
import kz.aita.compose.widget.tabRowWidget
import kz.aita.core.cartRepository
import kz.aita.model.dataModel.QuantityDataModel

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

    val searchTextFieldContent =
      searchTextField(
        stateHost = when (transactionTypeIndex) {
          0 -> NavigationScreenModel.Transaction.MainSale
          1 -> NavigationScreenModel.Transaction.MainReturn
          else -> NavigationScreenModel.Transaction.MainSupply
        },
        stateKey = NavigationScreenModel.KEY_STATE_SEARCH_QUERY,
        modifier = Modifier
          .padding(stateValues.marginTextField),
        barcodeCamScanner = true
      )

    val scopeRowContent = tabRowWidget(
      modifier = Modifier
        .padding(horizontal = stateValues.marginTextField),
      tabs = listOf(
        TabContent(stateValues.stringAll),
        TabContent(stateValues.stringQuick)
      )
    )

    when (scopeRowContent.index) {
      0 -> {
        StockWarehouseScreenContent(
          modifier = Modifier
            .weight(1f),
          searchTextFieldContent.value.text,
          onClick = {
            cartRepository.addToCart(
              id = it.id,
              transactionTypeIndex,
              clientId,
              it.quantity.copy(total = 1.0)
            )
          }
        )
      }

      1 -> {
        StockWarehouseScreenContent(
          modifier = Modifier
            .weight(1f),
          searchTextFieldContent.value.text
        ) {
          it.isQuickItem
        }
      }

      2 -> {

      }
    }
  }
}