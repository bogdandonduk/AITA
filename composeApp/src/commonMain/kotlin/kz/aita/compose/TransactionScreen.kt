package kz.aita.compose

import aita.composeapp.generated.resources.Res
import aita.composeapp.generated.resources._0_0
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kz.aita.cartRepository

@Composable
fun AppConfiguration.TransactionScreen() {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    if (stateValues.activeStoreId == null) {
      Column(
        modifier = Modifier
          .fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        MessageText(
          text = stateValues.stringNoActiveStore,
          textSize = stateValues.titleTextSize
        )

        actionButton(
          text = stateValues.stringSelectInMenu,
          fillMaxWidthIfTextPresent = false
        ) {
          coroutineScope.launch {
            Navigation.Menu.go(NavigationScreenModel.Menu.Stores)
            Navigation.goMain(NavigationScreenModel.Menu.Main)
          }
        }
      }
    } else {
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
          stateValues.navigationTransactionSaleClientId
        }
        1 -> {
          stateValues.navigationTransactionReturnClientId
        }
        else -> {
          stateValues.navigationTransactionSupplyClientId
        }
      }

      val goodsInCart by cartRepository.getCartState(transactionTypeIndex, clientId).collectAsState()


      LaunchedEffect(goodsInCart) {
        if (goodsInCart.isEmpty())
          coroutineScope.launch {
            when (transactionTypeIndex) {
              0 -> Navigation.TransactionSale.clear()
              1 -> Navigation.TransactionReturn.clear()
              2 -> Navigation.TransactionSupply.clear()
            }
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
        repeat(5) { index ->
          val cart by cartRepository.getCartState(transactionTypeIndex, index).collectAsState()

          Row(
            modifier = Modifier
              .weight(1f)
              .height(38.dp)
              .padding(stateValues.focusedBorderWidth)
              .clip(RoundedCornerShape(stateValues.cornerRadius))
              .border(
                stateValues.unfocusedBorderWidth,
                stateValues.PlaceholderTextColor,
                RoundedCornerShape(
                  stateValues.cornerRadius
                )
              )
              .background(if (clientId == index) stateValues.AccentColor else Color.Transparent)
              .clickable(
                interactionSource = remember {
                  MutableInteractionSource()
                },
                indication = ripple(color = stateValues.TextColor),
                onClick = {
                  coroutineScope.launch {
                    when (stateValues.navigationScreensMain.last()) {
                      is NavigationScreenModel.Transaction.MainSale -> {
                        Navigation.TransactionSale.setClientId(index)
                      }
                      is NavigationScreenModel.Transaction.MainReturn -> {
                        Navigation.TransactionReturn.setClientId(index)
                      }
                      is NavigationScreenModel.Transaction.MainSupply -> {
                        Navigation.TransactionReturn.setClientId(index)
                      }
                      else -> {

                      }
                    }
                  }
                }
              ),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
          ) {

              CpImage(
                modifier = Modifier
                  .padding(vertical = stateValues.textFieldIconPadding)
                  .aspectRatio(1f, matchHeightConstraintsFirst = true),
                url = if (cart.isEmpty()) stateValues.drawablePathIconAddCart else stateValues.drawablePathIconCart,
                fallbackRes = Res.drawable._0_0,
                contentDescription = (index + 1).toString(),
                tintColor = if (clientId == index) stateValues.AccentTextColor else stateValues.TextColor
              )


            Spacer(modifier = Modifier.width(6.dp))

            Text(
              text = (index + 1).toString(),
              color = if (clientId == index) stateValues.AccentTextColor else stateValues.TextColor,
              fontWeight = FontWeight.Bold
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
}
