package kz.aita.compose.screen.stock

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kz.aita.AppConfiguration
import kz.aita.compose.navigation.NavigationScreenModel
import kz.aita.compose.widget.GoodsItemInStockWidget
import kz.aita.compose.widget.MessageText
import kz.aita.compose.widget.ScreenAppBarWidget
import kz.aita.compose.widget.searchTextField
import kz.aita.core.search
import kz.aita.model.dataModel.GoodsItemDataModel
import kz.aita.model.wrapper.DataState

@Composable
fun AppConfiguration.StockWarehouseScreen() {
  Column(
    modifier = Modifier
      .fillMaxSize()
  ) {
    ScreenAppBarWidget(
      title = stateValues.stringStock,
      iconPath = stateValues.drawablePathIconStock
    )

    when (val state = stateValues.stockState) {
      is DataState.Success -> {
        if (state.payload.isEmpty()) {
          MessageText(
            modifier = Modifier
              .fillMaxWidth()
              .weight(1f),
            stateValues.stringListEmpty
          )
        } else {
          val searchTextFieldContent =
            searchTextField(
              valueInitial = NavigationScreenModel.Stock.Warehouse.state["search_query"],
              modifier = Modifier
                .padding(start = 8.dp, top = 8.dp, end = 8.dp),
              barcodeCamScanner = true
            )

          val items = searchTextFieldContent
            .value
            .text
            .takeIf {
              it.isNotEmpty()
            }?.let { query ->
              state.payload.search<GoodsItemDataModel>(query).first
            } ?: state.payload

          if (items.isEmpty()) {
            MessageText(
              modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
              stateValues.stringNoMatches)
          } else {
            LazyColumn(
              modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(8.dp)
            ) {
              items(items) {
                GoodsItemInStockWidget(
                  goodsItem = it,
                  onDelete = {

                  },
                  onEdit = {

                  }
                )
              }
            }
          }
        }
      }

      is DataState.Empty -> {
        MessageText(
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
          stateValues.stringListEmpty)
      }

      else -> {

      }
    }
  }
}
