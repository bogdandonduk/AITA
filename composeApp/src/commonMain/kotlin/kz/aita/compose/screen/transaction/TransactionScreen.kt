package kz.aita.compose.screen.transaction

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kz.aita.AppConfiguration
import kz.aita.compose.navigation.NavigationScreenModel

@Composable
fun AppConfiguration.TransactionScreen() {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    val navigationScreensLeft =
      when (stateValues.navigationScreensMain.last()) {
        is NavigationScreenModel.Transaction.MainSale -> {
          when (stateValues.navigationTransactionSaleClientId) {
            0 -> stateValues.navigationScreensTransactionSaleLeftClient1
            1 -> stateValues.navigationScreensTransactionSaleLeftClient2
            2 -> stateValues.navigationScreensTransactionSaleLeftClient3
            3 -> stateValues.navigationScreensTransactionSaleLeftClient4
            else -> stateValues.navigationScreensTransactionSaleLeftClient5
          }
        }
        is NavigationScreenModel.Transaction.MainReturn -> {
          when (stateValues.navigationTransactionReturnClientId) {
            0 -> stateValues.navigationScreensTransactionReturnLeftClient1
            1 -> stateValues.navigationScreensTransactionReturnLeftClient2
            2 -> stateValues.navigationScreensTransactionReturnLeftClient3
            3 -> stateValues.navigationScreensTransactionReturnLeftClient4
            else -> stateValues.navigationScreensTransactionReturnLeftClient5
          }
        }
        is NavigationScreenModel.Transaction.MainSupply -> {
          when (stateValues.navigationTransactionSupplyClientId) {
            0 -> stateValues.navigationScreensTransactionSupplyLeftClient1
            1 -> stateValues.navigationScreensTransactionSupplyLeftClient2
            2 -> stateValues.navigationScreensTransactionSupplyLeftClient3
            3 -> stateValues.navigationScreensTransactionSupplyLeftClient4
            else -> stateValues.navigationScreensTransactionSupplyLeftClient5
          }
        }
        else -> {
          emptyList()
        }
      }

    val navigationScreensRight =
      when (stateValues.navigationScreensMain.last()) {
        is NavigationScreenModel.Transaction.MainSale -> {
          when (stateValues.navigationTransactionSaleClientId) {
            0 -> stateValues.navigationScreensTransactionSaleRightClient1
            1 -> stateValues.navigationScreensTransactionSaleRightClient2
            2 -> stateValues.navigationScreensTransactionSaleRightClient3
            3 -> stateValues.navigationScreensTransactionSaleRightClient4
            else -> stateValues.navigationScreensTransactionSaleRightClient5
          }
        }
        is NavigationScreenModel.Transaction.MainReturn -> {
          when (stateValues.navigationTransactionReturnClientId) {
            0 -> stateValues.navigationScreensTransactionReturnRightClient1
            1 -> stateValues.navigationScreensTransactionReturnRightClient2
            2 -> stateValues.navigationScreensTransactionReturnRightClient3
            3 -> stateValues.navigationScreensTransactionReturnRightClient4
            else -> stateValues.navigationScreensTransactionReturnRightClient5
          }
        }
        is NavigationScreenModel.Transaction.MainSupply -> {
          when (stateValues.navigationTransactionSupplyClientId) {
            0 -> stateValues.navigationScreensTransactionSupplyRightClient1
            1 -> stateValues.navigationScreensTransactionSupplyRightClient2
            2 -> stateValues.navigationScreensTransactionSupplyRightClient3
            3 -> stateValues.navigationScreensTransactionSupplyRightClient4
            else -> stateValues.navigationScreensTransactionSupplyRightClient5
          }
        }
        else -> {
          emptyList()
        }
      }

    if (stateValues.isNarrowScreen) {
      AnimatedContent(
        modifier = Modifier
          .weight(1f),
        targetState = navigationScreensLeft.last()
      ) { model ->
        when (model) {
          is NavigationScreenModel.Transaction.Cart -> {
            TransactionCartScreen()
          }
          is NavigationScreenModel.Transaction.Selection -> {
            TransactionSelectionScreen()
          }
          is NavigationScreenModel.Transaction.Checkout -> {
            TransactionCheckoutScreen()
          }
          is NavigationScreenModel.Transaction.ReceiptPreview -> {
            TransactionReceiptPreviewScreen()
          }

          else -> {}
        }
      }
    } else {
      Row(
        modifier = Modifier
          .weight(1f)
      ) {
        AnimatedContent(
          modifier = Modifier
            .weight(1f),
          targetState = navigationScreensLeft.last()
        ) { model ->
          when (model) {
            is NavigationScreenModel.Transaction.Cart -> {
              TransactionCartScreen()
            }
            is NavigationScreenModel.Transaction.Selection -> {
              TransactionSelectionScreen()
            }

            is NavigationScreenModel.Transaction.Checkout -> {
              TransactionCheckoutScreen()
            }
            is NavigationScreenModel.Transaction.ReceiptPreview -> {
              TransactionReceiptPreviewScreen()
            }

            else -> {}
          }
        }

        AnimatedContent(
          modifier = Modifier
            .weight(1f),
          targetState = navigationScreensRight.last()
        ) { model ->
          when (model) {
            is NavigationScreenModel.Transaction.Cart -> {
              TransactionCartScreen()
            }
            is NavigationScreenModel.Transaction.Selection -> {
              TransactionSelectionScreen()
            }
            is NavigationScreenModel.Transaction.Checkout -> {
              TransactionCheckoutScreen()
            }
            is NavigationScreenModel.Transaction.ReceiptPreview -> {
              TransactionReceiptPreviewScreen()
            }

            else -> {}
          }
        }
      }
    }
  }
}
