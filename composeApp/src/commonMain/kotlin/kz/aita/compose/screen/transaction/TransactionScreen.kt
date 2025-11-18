package kz.aita.compose.screen.transaction

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kz.aita.AppConfiguration
import kz.aita.compose.navigation.Navigation
import kz.aita.compose.navigation.NavigationScreenModel

@Composable
fun AppConfiguration.TransactionScreen() {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    val clientId =
      when (stateValues.navigationScreensMain.last()) {
        is NavigationScreenModel.Transaction.MainSale -> {
          stateValues.navigationTransactionSaleClientId
        }

        is NavigationScreenModel.Transaction.MainReturn -> {
          stateValues.navigationTransactionReturnClientId
        }

        is NavigationScreenModel.Transaction.MainSupply -> {
          stateValues.navigationTransactionSupplyClientId
        }

        else -> {
          0
        }
      }

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

    Row(
      modifier = Modifier
        .padding(8.dp)
        .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.6f)
    ) {
      repeat(5) {
        Row(
          modifier = Modifier
            .weight(1f)
            .padding(stateValues.focusedBorderWidth)
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .border(
              stateValues.unfocusedBorderWidth,
              stateValues.PlaceholderTextColor,
              RoundedCornerShape(
                stateValues.cornerRadius
              )
            )
            .background(if (clientId == it) stateValues.AccentColor else Color.Transparent)
            .clickable(
              interactionSource = remember {
                MutableInteractionSource()
              },
              indication = ripple(color = stateValues.TextColor),
              onClick = {
                coroutineScope.launch {
                  when (stateValues.navigationScreensMain.last()) {
                    is NavigationScreenModel.Transaction.MainSale -> {
                      Navigation.TransactionSale.setClientId(it)
                    }
                    is NavigationScreenModel.Transaction.MainReturn -> {
                      Navigation.TransactionReturn.setClientId(it)
                    }
                    is NavigationScreenModel.Transaction.MainSupply -> {
                      Navigation.TransactionReturn.setClientId(it)
                    }
                    else -> {

                    }
                  }
                }
              }
            ),
          horizontalArrangement = Arrangement.Center
        ) {

          Text(
            text = (it + 1).toString(),
            modifier = Modifier
              .padding(8.dp),
            color = if (clientId == it) stateValues.AccentTextColor else stateValues.TextColor
          )
        }
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

          is NavigationScreenModel.Transaction.Payment -> {
            TransactionPaymentScreen()
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

            is NavigationScreenModel.Transaction.Payment -> {
              TransactionPaymentScreen()
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

            is NavigationScreenModel.Transaction.Payment -> {
              TransactionPaymentScreen()
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
