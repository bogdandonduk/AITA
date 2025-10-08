package kz.aita.compose.screen.menu

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kz.aita.AppConfiguration
import kz.aita.compose.navigation.Navigation
import kz.aita.compose.widget.ScreenAppBarWidget
import kz.aita.compose.widget.genericTextField

@Composable
fun AppConfiguration.MenuAddEditStoreScreen() {
  Column(
    modifier = Modifier
      .fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    ScreenAppBarWidget(
      title = stateValues.stringAddStore,
      iconPath = stateValues.drawablePathIconAdd,
      onBack = {
        coroutineScope.launch {
          Navigation.Menu.pop(stateValues.isNarrowScreen)
        }
      }
    )

    LazyColumn(
      modifier = Modifier
        .fillMaxWidth(
          if (stateValues.isNarrowScreen) 1f else 0.6f
        )
        .weight(1f)
        .padding(start = 8.dp, top = 24.dp, end = 8.dp)
    ) {
      item {
        val nameTextFieldContent =
          genericTextField(
            titleText = stateValues.stringName,
            placeholderText = stateValues.stringEnterName,
          )

        Spacer(
          modifier = Modifier
            .height(8.dp)
        )

        val alias =
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