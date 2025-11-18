package kz.aita.compose.screen.stock

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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

    StockWarehouseScreenContent(
      modifier = Modifier
        .weight(1f),
      onDelete = {
        stockRepository.deleteGoodsItem(id = it.id, storeId = stateValues.activeStoreId!!)
      },
      onEdit = {
        coroutineScope.launch {
          if (!NavigationScreenModel.Stock.AddEditGoodsItem.state.value.contains(NavigationScreenModel.Stock.AddEditGoodsItem.KEY_STATE_EDITED_GOODS_ITEM_ID)) {
            NavigationScreenModel.Stock.AddEditGoodsItem.setState(NavigationScreenModel.Stock.AddEditGoodsItem.KEY_STATE_EDITED_GOODS_ITEM_ID to it.id)
            Navigation.Stock.go(NavigationScreenModel.Stock.AddEditGoodsItem, forceSecond = true)
          }
        }
      }
    )
  }
}

@Composable
fun AppConfiguration.StockWarehouseScreenContent(
  modifier: Modifier = Modifier,
  searchQuery: String? = null,
  onFilter: ((GoodsItemDataModel) -> Boolean)? = null,
  onClick: ((GoodsItemDataModel) -> Unit)? = null,
  onDelete: ((GoodsItemDataModel) -> Unit)? = null,
  onEdit: ((GoodsItemDataModel) -> Unit)? = null
) {
  when (val state = stateValues.stockState) {
    is DataState.Success -> {
      if (state.payload.isEmpty()) {
        MessageText(
          modifier = modifier
            .fillMaxWidth(),
          stateValues.stringListEmpty
        )
      } else {
        var lSearchQuery: String by rememberSaveable {
          mutableStateOf("")
        }

        if (searchQuery == null) {
          val searchTextFieldContent =
            searchTextField(
              modifier = Modifier
                .padding(start = 8.dp, top = 8.dp, end = 8.dp),
              stateHost = NavigationScreenModel.Stock.Warehouse,
              stateKey = NavigationScreenModel.KEY_STATE_SEARCH_QUERY,
              barcodeCamScanner = true
            )

          LaunchedEffect(searchTextFieldContent.value) {
            lSearchQuery = searchTextFieldContent.value.text
          }
        } else {
          LaunchedEffect(searchQuery) {
            lSearchQuery = searchQuery
          }
        }

        val items = lSearchQuery
          .takeIf {
            it.isNotEmpty()
          }?.let { query ->
            state.payload.run { onFilter?.let { filter { onFilter(it) } } ?: this }.search<GoodsItemDataModel>(query).first
          } ?: state.payload.run { onFilter?.let { filter { onFilter(it) } } ?: this }

        if (items.isEmpty()) {
          MessageText(
            modifier = modifier
              .fillMaxWidth(),
            stateValues.stringNoMatches
          )
        } else {
          LazyColumn(
            modifier = modifier
              .fillMaxWidth()
              .padding(8.dp)
          ) {
            items(items) { item ->
              GoodsItemInStockWidget(
                goodsItem = item,
                onDelete = onDelete,
                onClick = onClick,
                onEdit = onEdit
              )
            }
          }
        }
      }
    }

    is DataState.Empty -> {
      MessageText(
        modifier = modifier
          .fillMaxWidth(),
        stateValues.stringListEmpty
      )
    }
  }
}