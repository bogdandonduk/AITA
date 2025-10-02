package kz.aita.compose.screen.menu

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import kz.aita.AppUIConfiguration
import kz.aita.AppUIConfiguration.coroutineScope
import kz.aita.AppUIConfiguration.stateValues
import kz.aita.compose.navigation.Navigation
import kz.aita.compose.widget.ScreenAppBarWidget

@Composable
fun AppUIConfiguration.MenuAppThemeScreen() {
  Column(
    modifier = Modifier
      .fillMaxSize()
  ) {
    ScreenAppBarWidget(
      title = stateValues.stringAppTheme,
      iconPath = stateValues.drawablePathIconAppTheme,
      onBack = {
        coroutineScope.launch {
          Navigation.Menu.pop(stateValues.isNarrowScreen)
        }
      }
    )
  }
}