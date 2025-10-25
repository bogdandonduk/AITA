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
import kotlinx.coroutines.launch
import kz.aita.AppConfiguration
import kz.aita.compose.navigation.Navigation
import kz.aita.compose.navigation.NavigationScreenModel
import kz.aita.compose.widget.GoodsItemInStockWidget
import kz.aita.compose.widget.MessageText
import kz.aita.compose.widget.ScreenAppBarWidget
import kz.aita.compose.widget.searchTextField
import kz.aita.core.search
import kz.aita.core.stockRepository
import kz.aita.core.storeRepository
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
              modifier = Modifier
                .padding(start = 8.dp, top = 8.dp, end = 8.dp),
              stateHost = NavigationScreenModel.Stock.Warehouse,
              stateKey = "search_query",
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
              items(items) { item ->
                GoodsItemInStockWidget(
                  goodsItem = item,
                  onDelete = {
                    stockRepository.deleteGoodsItem(id = it.id, storeId = stateValues.activeStoreId!!)
                  },
                  onEdit = {
                    coroutineScope.launch {
                      if (!NavigationScreenModel.Stock.AddEditGoodsItem.state.value.contains("state_editedGoodsItemId")) {
                        NavigationScreenModel.Stock.AddEditGoodsItem.setState("state_editedGoodsItemId" to it.id)
                        Navigation.Stock.go(NavigationScreenModel.Stock.AddEditGoodsItem, forceSecond = true)
                      }
                    }
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
