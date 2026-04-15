package kz.aita.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kz.aita.GoodsItemInCartDataModel
import kz.aita.cartRepository
import kz.aita.extractLocalizedString
import kz.aita.getCurrency

@Composable
fun AppConfiguration.TransactionReceiptPreviewScreen() {
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

    val clientId = when (transactionTypeIndex) {
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
      title = stateValues.stringReceipt,
      iconPath = stateValues.drawablePathIconReceipt,
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

    var goodsInCart by rememberSaveable {
      mutableStateOf(emptyList<GoodsItemInCartDataModel>())
    }

    LaunchedEffect(Unit) {
      cartRepository
        .observeCart(transactionTypeIndex, clientId)
        .collect {
          it?.let {
            goodsInCart = it
          }
        }
    }

    LazyColumn(
      modifier = Modifier
        .padding(8.dp)
        .background(Color.White)
        .border(stateValues.unfocusedBorderWidth, stateValues.PlaceholderTextColor)
        .weight(1f)
    ) {
      var totalPrice = 0.0

      stateValues
        .stores
        ?.find {
          it.id == stateValues.activeStoreId
        }?.run {
          item {
            val ownershipFormTitle = this@run.companyForms.first().name.extractLocalizedString(stateValues.appLanguage)

            Text(
              text = "$ownershipFormTitle ${name.extractLocalizedString(stateValues.appLanguage)}",
              modifier = Modifier
                .padding(stateValues.marginTextFieldGroup, stateValues.marginTextFieldGroup, stateValues.marginTextFieldGroup,)
                .fillMaxWidth(),
              textAlign = TextAlign.Center,
              fontWeight = FontWeight.Bold,
              fontSize = stateValues.accentTextSize
            )

            Text(
              text = location.name,
              modifier = Modifier
                .fillMaxWidth()
                .padding(start = stateValues.marginTextFieldGroup, top = 2.dp, end = stateValues.marginTextFieldGroup, 12.dp),
              textAlign = TextAlign.Center,
              fontSize = stateValues.textSize
            )
          }
        }

      goodsInCart.forEachIndexed { index, item ->
        stateValues.stock?.find { it.id == item.id }?.run {
          val price =
            when (transactionTypeIndex) {
              0 -> supplyPrices.first().price.toDouble()
              1 -> returnPrices.first().price.toDouble()
              else -> supplyPrices.first().price.toDouble()
            }  // TODO

          val currencySymbol = stateValues.globalAppConfiguration.countries.getCurrency(
            when (transactionTypeIndex) {
              0 -> supplyPrices.first().currency
              1 -> returnPrices.first().currency
              else -> supplyPrices.first().currency
            }
          )?.symbol

          totalPrice += item.quantity.total * price

          item {
            Text(
              text = "${index + 1} ${name.extractLocalizedString(stateValues.appLanguage) ?: "No name"}", // TODO
              modifier = Modifier
                .padding(horizontal = stateValues.marginTextFieldGroup),
              fontWeight = FontWeight.Bold,
              fontSize = stateValues.textSize
            )

            Row(
              modifier = Modifier
                .fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              val suffix = item.quantity.immutableUnitName.extractLocalizedString(stateValues.appLanguage)

              Text(
                text = "${
                  item.quantity.run { if (roundTotal) total.toInt() else total }
                } $suffix x $price $currencySymbol",
                modifier = Modifier
                  .padding(horizontal = stateValues.marginTextFieldGroup),
                fontSize = stateValues.textSize
              )

              Text(
                text = "$price $currencySymbol",
                modifier = Modifier
                  .padding(horizontal = stateValues.marginTextFieldGroup),
                fontSize = stateValues.accentTextSize
              )
            }
          }
        }
      }
    }

    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 8.dp)
    ) {
      Spacer(modifier = Modifier.height(4.dp))

      Row(
        modifier = Modifier
          .fillMaxWidth()
      ) {
        actionButton(
          text = "",
          enabledColor = stateValues.DisabledColor,
          iconPath = stateValues.drawablePathIconAdd, // TODO
          iconContentDescription = stateValues.stringAdd, // TODO
          onClick = {

          }
        )
      }

      Spacer(modifier = Modifier.height(2.dp))

      actionButton(
        text = stateValues.stringComplete, // TODO
        enabled = stateValues.latestNotification == null,
        onClick = {

        }
      )

      Spacer(modifier = Modifier.height(4.dp))
    }
  }
}