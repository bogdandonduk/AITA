package kz.aita.compose.screen.stock

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kz.aita.AppConfiguration
import kz.aita.compose.widget.ScreenAppBarWidget
import kz.aita.compose.widget.SelectableDomain
import kz.aita.compose.widget.dropdownListWidget
import kz.aita.compose.widget.genericTextField
import kz.aita.core.extractLocalizedString
import kz.aita.core.genericGoodsItemsRepository
import kz.aita.model.wrapper.DataState

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

        var name: String? by rememberSaveable {
          mutableStateOf(null)
        }

        LaunchedEffect(barcodeTextFieldContent.value.text) {
          barcodeTextFieldContent.value.text.takeIf { it.length == 13 }?.run {
            genericGoodsItemsRepository
              .getGenericGoodsItems(this)
              .collect {
                if (it is DataState.Success && it.payload.isNotEmpty())
                  name = it.payload.first().name.extractLocalizedString(stateValues.appLanguage)
              }
          }
        }

        Spacer(
          modifier = Modifier
            .height(8.dp)
        )

        val nameTextFieldContent =
          genericTextField(
            titleText = stateValues.stringName,
            placeholderText = stateValues.stringEnterName,
            valueInitial = name
          )

        Spacer(
          modifier = Modifier
            .height(24.dp)
        )

        dropdownListWidget(
          titleText = "Measurement unit",
          domains = stateValues.globalAppConfiguration.goodsItemsQuantityUnits.map {
            SelectableDomain(
              id = it.id,
              name = it.immutableUnitName,
              iconPath = null
            )
          }
        )

        Spacer(
          modifier = Modifier
            .height(24.dp)
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
