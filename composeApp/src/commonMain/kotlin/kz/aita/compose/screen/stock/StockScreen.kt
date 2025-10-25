package kz.aita.compose.screen.stock

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kz.aita.AppConfiguration
import kz.aita.compose.navigation.Navigation
import kz.aita.compose.navigation.NavigationScreenModel
import kz.aita.compose.widget.MessageText
import kz.aita.compose.widget.actionButton

@Composable
fun AppConfiguration.StockScreen() {
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

        Spacer(modifier = Modifier.height(8.dp))

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
      if (stateValues.isNarrowScreen) {
        AnimatedContent(
          modifier = Modifier
            .weight(1f),
          targetState = stateValues.navigationScreensStockLeft.last()
        ) { model ->
          when (model) {
            is NavigationScreenModel.Stock.Warehouse -> {
              StockWarehouseScreen()
            }
            is NavigationScreenModel.Stock.AddEditGoodsItem -> {
              StockAddEditGoodsItemScreen()
            }

            else -> { }
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
            targetState = stateValues.navigationScreensStockLeft.last()
          ) { model ->
            when (model) {
              is NavigationScreenModel.Stock.Warehouse -> {
                StockWarehouseScreen()
              }
              is NavigationScreenModel.Stock.AddEditGoodsItem -> {
                StockAddEditGoodsItemScreen()
              }

              else -> { }
            }
          }

          AnimatedContent(
            modifier = Modifier
              .weight(1f),
            targetState = stateValues.navigationScreensStockRight.last()
          ) { model ->
            when (model) {
              is NavigationScreenModel.Stock.Warehouse -> {
                StockWarehouseScreen()
              }
              is NavigationScreenModel.Stock.AddEditGoodsItem -> {
                StockAddEditGoodsItemScreen()
              }

              else -> { }
            }
          }
        }
      }
    }
  }
}
