package kz.aita.compose.screen.transaction

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kz.aita.AppConfiguration
import kz.aita.compose.navigation.NavigationScreenModel
import kz.aita.compose.widget.ScreenAppBarWidget
import kz.aita.compose.widget.actionButton
import kz.aita.compose.widget.searchTextField

@Composable
fun AppConfiguration.TransactionCartScreen() {
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

    val clientId = when(transactionTypeIndex) {
      0 -> {
        stateValues.navigationTransactionReturnClientId
      }
      1 -> {
        stateValues.navigationTransactionSupplyClientId
      }
      else -> {
        stateValues.navigationTransactionSaleClientId
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
      }
    )

    LazyColumn(
      modifier = Modifier
        .weight(1f)
        .padding(horizontal = 8.dp)
    ) {

    }

    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 8.dp)
    ) {
      Spacer(modifier = Modifier.height(4.dp))

      actionButton(
        text = stateValues.stringPayment,
        enabled = stateValues.latestNotification == null,
        onClick = {

        }
      )

      Spacer(modifier = Modifier.height(4.dp))
    }
  }
}