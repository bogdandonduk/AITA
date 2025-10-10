package kz.aita.compose.screen.menu

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import kz.aita.AppConfiguration
import kz.aita.compose.navigation.Navigation
import kz.aita.compose.widget.AppLanguageSettingsItemWidget
import kz.aita.compose.widget.ScreenAppBarWidget
import kz.aita.core.extractLocalizedString

@Composable
fun AppConfiguration.MenuAppLanguageScreen() {
  Column(
    modifier = Modifier
      .fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    ScreenAppBarWidget(
      title = stateValues.stringAppLanguage,
      iconPath = stateValues.drawablePathIconAppLanguage,
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
      item {
        AppLanguageSettingsItemWidget(
            "system",
            flagDrawablePath = stateValues.drawablePathIconSettings,
            name = stateValues.stringSystemLanguage,
            isActive = stateValues.appLanguage == "system"
        )
      }

      items(stateValues.globalAppConfiguration.languages) { language ->
        AppLanguageSettingsItemWidget(
          language = language.language,
          name = language.name.extractLocalizedString(stateValues.appLanguage) ?: language.language,
          flagDrawablePath = language.flagDrawablePath,
          isActive = stateValues.appLanguage == language.language
        )
      }
    }
  }
}