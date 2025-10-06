package kz.aita.compose.screen.stock

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kz.aita.AppConfiguration
import kz.aita.compose.widget.ScreenAppBarWidget
import kz.aita.compose.widget.genericTextField

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

    LazyColumn(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f)
        .padding(horizontal = 8.dp, vertical = 16.dp)
    ) {
      item {
        val barcodeTextFieldContent =
          genericTextField(
            titleText = stateValues.stringBarcode
          )

        Spacer(
          modifier = Modifier
            .height(8.dp)
        )

        val nameTextFieldContent =
          genericTextField(
            titleText = stateValues.stringName,
          )

        Spacer(
          modifier = Modifier
            .height(8.dp)
        )
      }
    }
  }
}
