package kz.aita.compose.screen.transaction

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import kz.aita.AppConfiguration
import kz.aita.compose.navigation.Navigation
import kz.aita.compose.navigation.NavigationScreenModel
import kz.aita.compose.widget.ScreenAppBarWidget

@Composable
fun AppConfiguration.TransactionPaymentScreen() {
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
      title = stateValues.stringPayment,
      onBack = if (!Navigation.TransactionSale.isVeryFirstScreen(stateValues.isNarrowScreen, clientId)) {
        {
          coroutineScope.launch {
            when (transactionTypeIndex) {
              0 -> Navigation.TransactionSale.pop()
              1 -> Navigation.TransactionReturn.pop()
              2 -> Navigation.TransactionSupply.pop()
            }
          }
        }
      } else null
    )
  }
}