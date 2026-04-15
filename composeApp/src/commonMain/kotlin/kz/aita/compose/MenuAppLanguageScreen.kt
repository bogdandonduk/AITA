package kz.aita.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import kz.aita.extractLocalizedString

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
        val settingsIconRes by stateValues.drawableResIconSettings.collectAsState()

        AppLanguageSettingsItemWidget(
          "system",
          flagDrawablePath = stateValues.drawablePathIconSettings,
          flagDrawableRes = settingsIconRes,
          name = stateValues.stringSystemLanguage,
          isActive = stateValues.appLanguage == "system"
        )
      }

      items(stateValues.globalAppConfiguration.languages) { language ->
        AppLanguageSettingsItemWidget(
          language = language.language,
          name = language.name.extractLocalizedString(stateValues.appLanguage) ?: language.language,
          flagDrawablePath = language.flagDrawablePath,
          flagDrawableRes = language.mapIconRes(),
          isActive = stateValues.appLanguage == language.language
        )
      }
    }
  }
}