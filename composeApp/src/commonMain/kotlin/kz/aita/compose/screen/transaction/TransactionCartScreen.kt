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
fun AppUIConfiguration.TransactionCartScreen() {
  Column(
    modifier = Modifier
      .fillMaxSize()
  ) {
    val transactionTypeIndex = when (stateValues.navigationScreensMain.last()) {
      is NavigationScreenModel.Transaction.MainReturn -> {
        1
      }
      is NavigationScreenModel.Transaction.MainSupply -> {
        2
      }
      else -> {
        0
      }
    }

    ScreenAppBarWidget(
      title = when (transactionTypeIndex) {
        1 -> stateValues.stringReturn
        2 -> stateValues.stringSupply
        else -> stateValues.stringSale
      },
      iconPath = when (transactionTypeIndex) {
        1 -> stateValues.drawablePathIconTransactionReturn
        2 -> stateValues.drawablePathIconTransactionSupply
        else -> stateValues.drawablePathIconTransactionSale
      },
      onBack = Navigation.isVeryFirstScreen().takeIf { !it }?.run {
        {
          coroutineScope.launch {
            Navigation.popMain()
          }
        }
      }
    )
  }
}