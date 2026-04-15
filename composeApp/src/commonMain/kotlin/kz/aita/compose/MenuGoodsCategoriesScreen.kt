package kz.aita.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch

@Composable
fun AppConfiguration.MenuGoodsCategoriesScreen() {
  Column(
    modifier = Modifier
      .fillMaxSize()
  ) {
    ScreenAppBarWidget(
      title = stateValues.stringGoodsCategories,
      iconPath = stateValues.drawablePathIconGoodsCategories,
      onBack = {
        coroutineScope.launch {
          Navigation.Menu.pop(stateValues.isNarrowScreen)
        }
      }
    )
  }
}