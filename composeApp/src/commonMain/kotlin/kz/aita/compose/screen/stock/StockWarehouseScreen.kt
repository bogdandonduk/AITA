package kz.aita.compose.screen.stock

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import kz.aita.AppConfiguration
import kz.aita.compose.navigation.NavigationScreenModel
import kz.aita.compose.widget.SearchTextFieldWithCamBarcodeScanner
import kz.aita.compose.widget.ScreenAppBarWidget

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

    val searchTextFieldContent =
      SearchTextFieldWithCamBarcodeScanner(
        valueInitial = NavigationScreenModel.Stock.Warehouse.state["search_query"]
      )

    LaunchedEffect(searchTextFieldContent.value) {
      println("are we even called bruh")
      NavigationScreenModel.Stock.Warehouse.setState("search_query" to searchTextFieldContent.value.text)

      delay(2000)
      println("now bruh " + NavigationScreenModel.Stock.Warehouse.state["search_query"])
    }
  }
}
