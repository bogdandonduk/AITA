package kz.aita.compose.screen.stock

import androidx.compose.foundation.layout.*
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
        .padding(start = 8.dp, top = 24.dp, end = 8.dp)
    ) {
      item {
        val barcodeTextFieldContent =
          genericTextField(
            titleText = stateValues.stringBarcode,
            placeholderText = stateValues.stringEnterBarcode,
            isFocusedInitial = true
          )

        Spacer(
          modifier = Modifier
            .height(8.dp)
        )

        val nameTextFieldContent =
          genericTextField(
            titleText = stateValues.stringName,
            placeholderText = stateValues.stringEnterName,
          )

        Spacer(
          modifier = Modifier
            .height(32.dp)
        )

        val supplyPriceTextFieldContent =
          genericTextField(
            titleText = stateValues.stringSupplyPriceState,
            placeholderText = stateValues.stringEnterSupplyPrice,
          )

        Spacer(
          modifier = Modifier
            .height(8.dp)
        )

        val salePriceTextFieldContent =
          genericTextField(
            titleText = stateValues.stringSalePriceState,
            placeholderText = stateValues.stringEnterSalePrice,
          )

        Spacer(
          modifier = Modifier
            .height(8.dp)
        )

        val returnPriceTextFieldContent =
          genericTextField(
            titleText = stateValues.stringReturnPriceState,
            placeholderText = stateValues.stringEnterReturnPrice,
          )

        Spacer(
          modifier = Modifier
            .height(8.dp)
        )
      }

      item {
        Spacer(
          modifier = Modifier
            .height(200.dp)
        )
      }
    }
  }
}
