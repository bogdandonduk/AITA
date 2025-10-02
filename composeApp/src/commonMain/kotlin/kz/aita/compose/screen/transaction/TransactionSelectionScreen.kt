package kz.aita.compose.screen.transaction

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import kz.aita.AppUIConfiguration
import kz.aita.compose.navigation.Navigation
import kz.aita.compose.navigation.NavigationScreenModel
import kz.aita.compose.widget.ScreenAppBarWidget

@Composable
fun AppUIConfiguration.TransactionSelectionScreen() {
  Column(
    modifier = Modifier
      .fillMaxSize()
  ) {
    val clientId = when(stateValues.navigationScreensMain.last()) {
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
  }
}