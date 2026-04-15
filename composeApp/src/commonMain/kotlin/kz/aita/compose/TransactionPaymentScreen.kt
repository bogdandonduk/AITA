package kz.aita.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kz.aita.cartRepository
import kz.aita.extractLocalizedString

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
      onBack = if (
        when (transactionTypeIndex) {
          0 -> !Navigation.TransactionSale.isVeryFirstScreen(stateValues.isNarrowScreen, clientId)
          1 -> !Navigation.TransactionReturn.isVeryFirstScreen(stateValues.isNarrowScreen, clientId)
          else -> !Navigation.TransactionSupply.isVeryFirstScreen(stateValues.isNarrowScreen, clientId)
        }
      ) {
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

    val goodsInCart by cartRepository.getCartState(transactionTypeIndex, clientId).collectAsState()

    var selectedCashlessPaymentMethodId by rememberSaveable {
      mutableStateOf(stateValues.globalAppConfiguration.countries.find {
        it.locale.equals(stateValues.userAccount?.countryLocale, true)
      }?.preferredCashlessPaymentOptionId ?: "0")
    }

    Column(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f)
    ) {
      val scopeRowContent = tabRowWidget(
        modifier = Modifier
          .padding(stateValues.marginTextField),
        selectedIndexInitial = "1",
        tabs = listOf(
          TabContent("0", stateValues.stringCash),
          TabContent("1", stateValues.stringCashless),
          TabContent("2", stateValues.stringMixed)
        )
      )

      when (scopeRowContent.id) {
        "0" -> {

        }

        "1" -> {
          Column(
            modifier = Modifier
              .weight(1f)
              .fillMaxSize()
              .padding(stateValues.marginTextField),
          ) {
            LazyVerticalGrid(columns = GridCells.Fixed(2)) {
              stateValues.globalAppConfiguration.countries.find {
                it.locale.equals(stateValues.userAccount?.countryLocale, true)
              }?.cashlessPaymentOptions?.forEach { item ->
                item {
                  Box(
                    modifier = Modifier
                      .weight(1f)
                      .clip(RoundedCornerShape(stateValues.cornerRadius))
                      .border(
                        stateValues.unfocusedBorderWidth,
                        stateValues.PlaceholderTextColor,
                        RoundedCornerShape(
                          stateValues.cornerRadius
                        ),
                      )
                      .background(if (selectedCashlessPaymentMethodId == item.id) stateValues.AccentColor else Color.Transparent)
                      .clickable(
                        interactionSource = remember {
                          MutableInteractionSource()
                        },
                        indication = ripple(color = if (selectedCashlessPaymentMethodId == item.id) stateValues.AccentTextColor else stateValues.TextColor),
                        onClick = {
                          selectedCashlessPaymentMethodId = item.id
                        }
                      ),
                    contentAlignment = Alignment.Center
                  ) {
                    item.name.extractLocalizedString(stateValues.appLanguage)?.let { text ->
                      Text(
                        text = text,
                        modifier = Modifier
                          .padding(24.dp),
                        color = if (selectedCashlessPaymentMethodId == item.id) stateValues.AccentTextColor else stateValues.TextColor
                      )
                    }
                  }
                }
              }
            }
          }
        }

        else -> {

        }
      }
    }

    if (goodsInCart.isNotEmpty())
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 8.dp)
      ) {
        Spacer(modifier = Modifier.height(4.dp))

        actionButton(
          text = stateValues.stringReceipt,
          enabled = stateValues.latestNotification == null,
          onClick = {
            coroutineScope.launch {
              when (transactionTypeIndex) {
                0 -> {
                  Navigation.TransactionSale.go(NavigationScreenModel.Transaction.ReceiptPreview)
                }

                1 -> {
                  Navigation.TransactionReturn.go(NavigationScreenModel.Transaction.ReceiptPreview)
                }

                else -> {
                  Navigation.TransactionSupply.go(NavigationScreenModel.Transaction.ReceiptPreview)
                }

              }
            }
          }
        )

        Spacer(modifier = Modifier.height(4.dp))
      }
  }
}