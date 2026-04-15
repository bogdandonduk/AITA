package kz.aita.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import kz.aita.extractLocalizedString

@Composable
fun AppConfiguration.MenuAppThemeScreen() {
  Column(
    modifier = Modifier
      .fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally
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
        .weight(1f)
        .fillMaxWidth(
          if (stateValues.isNarrowScreen) 1f else 0.6f
        )
    ) {
      items(stateValues.globalAppConfiguration.themes) { theme ->
        AppThemeSettingsItemWidget(
          id = theme.id,
          name = theme.name.extractLocalizedString(stateValues.appLanguage) ?: theme.id.toString(),
          isActive = stateValues.appThemeId == theme.id
        )
      }
    }
  }
}