package kz.aita.compose

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kz.aita.LocalizedStringDataModel
import kz.aita.QuantityDataModel
import kz.aita.cartRepository

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
        stateValues.navigationTransactionSaleClientId
      }
      1 -> {
        stateValues.navigationTransactionReturnClientId
      }
      else -> {
        stateValues.navigationTransactionSupplyClientId
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

    val goodsInCart by cartRepository.getCartState(transactionTypeIndex, clientId).collectAsState()

    if (goodsInCart.isEmpty()) {
      MessageText(
        modifier = Modifier
          .fillMaxSize(),
        stateValues.stringCartEmpty
      )
    } else {
      LazyColumn(
        modifier = Modifier
          .weight(1f)
          .padding(stateValues.marginTextField),
      ) {
        itemsIndexed(goodsInCart) { index, item ->
          stateValues.stock?.find {
            item.id == it.id
          }?.let {
            GoodsItemInCartWidget(
              index = index,
              goodsItemInCart = item,
              goodsItem = it,
              onDelete = {
                cartRepository.deleteCartById(item.id, transactionTypeIndex, clientId)
              },
              increaseQuantityAction = {
                cartRepository.addToCart(
                  id = it.id,
                  transactionTypeIndex,
                  clientId,
                  QuantityDataModel("", immutableUnitName = listOf(LocalizedStringDataModel("main", "pc.")), roundTotal = true) // TODO it.quantity.copy(total = it.quantity.total + it.quantity.pricedAmount)
                )
              },
              decreaseQuantityAction = {
                cartRepository.addToCart(
                  id = it.id,
                  transactionTypeIndex,
                  clientId,
                  QuantityDataModel("", immutableUnitName = listOf(LocalizedStringDataModel("main", "pc.")), roundTotal = true) // TODO it.quantity.copy(total = it.quantity.total + it.quantity.pricedAmount)
                )
              }
            )
          }
        }
      }
    }

    val currentTransactionScreens by Navigation.getCurrentTransactionScreens(transactionTypeIndex, clientId, stateValues.isNarrowScreen).collectAsState()

    if (goodsInCart.isNotEmpty() && currentTransactionScreens.run { last() is NavigationScreenModel.Transaction.Cart || last() is NavigationScreenModel.Transaction.Selection })
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
            coroutineScope.launch {
              when (transactionTypeIndex) {
                0 -> {
                  Navigation.TransactionSale.go(NavigationScreenModel.Transaction.Payment)
                }
                1 -> {
                  Navigation.TransactionReturn.go(NavigationScreenModel.Transaction.Payment)
                }
                else -> {
                  Navigation.TransactionSupply.go(NavigationScreenModel.Transaction.Payment)
                }

              }
            }
          }
        )

        Spacer(modifier = Modifier.height(4.dp))
      }
  }
}