package kz.aita.compose

import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kz.aita.DataState
import kz.aita.GoodsItemDataModel
import kz.aita.search
import kz.aita.stockRepository
import kotlin.collections.contains
import kotlin.collections.filter
import kotlin.collections.first
import kotlin.collections.isNotEmpty
import kotlin.collections.listOf
import kotlin.ranges.first
import kotlin.sequences.first
import kotlin.text.first
import kotlin.text.isNotEmpty

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
        stockRepository.deleteGoodsItem(id = it.id, storeId = stateValues.activeStoreId!!) {

        }
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
  disableIfOutOfStock: Boolean = false,
  showStockType: Boolean = true,
  onFilter: ((GoodsItemDataModel) -> Boolean)? = null,
  onClick: ((GoodsItemDataModel) -> Unit)? = null,
  onDelete: ((GoodsItemDataModel) -> Unit)? = null,
  onEdit: ((GoodsItemDataModel) -> Unit)? = null,
  onExactSearchHit: ((GoodsItemDataModel) -> Unit)? = null
){
  when (val state = stateValues.stockState) {
    is DataState.Success -> {
      if (state.payload.run { onFilter?.let { filter { onFilter(it) } } ?: this }.isEmpty()) {
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

        if (showStockType) {
          val stockTypeTabRowContent = tabRowWidget(
            modifier = Modifier
              .padding(8.dp),
            tabs = listOf(
              TabContent("0", stateValues.stringItems),
              TabContent("1", stateValues.stringBatches)
            )
          )
        }

        val items = lSearchQuery
          .takeIf {
            it.isNotEmpty()
          }?.let { query ->
            state.payload.run { onFilter?.let { filter { onFilter(it) } } ?: this }.search<GoodsItemDataModel>(query).apply { if (second && onExactSearchHit != null && first.isNotEmpty()) onExactSearchHit(first.first()) }.first
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
                modifier = Modifier
                  .alpha(if (disableIfOutOfStock /* TODO && item.quantity.total == 0.0 */) 0.5f else 1f),
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