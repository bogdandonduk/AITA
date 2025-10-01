package kz.aita.compose.screen.stock

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kz.aita.AppUIConfiguration
import kz.aita.compose.navigation.NavigationScreenModel

@Composable
fun AppUIConfiguration.StockScreen() {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
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
          targetState = stateValues.navigationScreensStockLeft.last()
        ) { model ->
          when (model) {
            is NavigationScreenModel.Stock.Warehouse -> {
              StockWarehouseScreen()
            }
            is NavigationScreenModel.Stock.AddEditGoodsItem -> {
              StockAddEditGoodsItemScreen()
            }

            else -> {}
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

            else -> {}
          }
        }
      }
    }
  }
}
