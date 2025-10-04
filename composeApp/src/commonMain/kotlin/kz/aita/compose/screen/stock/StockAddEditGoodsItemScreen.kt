package kz.aita.compose.screen.stock

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kz.aita.AppConfiguration
import kz.aita.compose.widget.ScreenAppBarWidget

@Composable
fun AppConfiguration.StockAddEditGoodsItemScreen() {
  Column(
    modifier = Modifier
      .fillMaxSize()
  ) {
    ScreenAppBarWidget(
      title = stateValues.stringAddGoodsItem,
      iconPath = stateValues.drawablePathIconAdd,
    )
  }
}
