package kz.aita.compose.screen.menu

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import kz.aita.AppConfiguration
import kz.aita.compose.navigation.Navigation
import kz.aita.compose.widget.AppThemeSettingsItemWidget
import kz.aita.compose.widget.ScreenAppBarWidget
import kz.aita.core.extractLocalizedString

@Composable
fun AppConfiguration.MenuAppThemeScreen() {
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

    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
    ) {
      items(stateValues.globalAppConfiguration.themes) { theme ->
        AppThemeSettingsItemWidget(
          id = theme.id,
          name = theme.name.extractLocalizedString(stateValues.appLocaleLanguage) ?: theme.id.toString(),
          isActive = stateValues.appThemeId == theme.id
        )
      }
    }
  }
}