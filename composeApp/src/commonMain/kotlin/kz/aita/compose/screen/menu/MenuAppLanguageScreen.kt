package kz.aita.compose.screen.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.kamel.image.KamelImage
import io.kamel.image.asyncPainterResource
import io.kamel.image.config.LocalKamelConfig
import io.ktor.http.Url
import kotlinx.coroutines.launch
import kz.aita.AppConfiguration
import kz.aita.compose.navigation.Navigation
import kz.aita.compose.render.kamelConfig
import kz.aita.compose.widget.ScreenAppBarWidget
import kz.aita.core.getFullDrawableResourceUrl

@Composable
fun AppConfiguration.MenuAppLanguageScreen() {
  Column(
    modifier = Modifier
      .fillMaxSize()
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
        .fillMaxSize()
    ) {
      items(stateValues.globalAppConfiguration.languages) { language ->
        val languageName = language.name.find { it.language == stateValues.appLocaleLanguage }?.value ?: language.language
        val isActive = stateValues.appLocaleLanguage.equals(language.language, true)

        Row(
          modifier = Modifier
            .height(42.dp)
            .fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically
        ) {
          CompositionLocalProvider(LocalKamelConfig provides kamelConfig) {
            KamelImage(
              modifier = Modifier
                .padding(stateValues.textFieldIconPadding)
                .fillMaxHeight()
                .aspectRatio(1f, matchHeightConstraintsFirst = true),
              resource = {
                asyncPainterResource(
                  data = Url(getFullDrawableResourceUrl(language.flagDrawablePath))
                )
              },
              contentDescription = languageName
            )


            Text(
              text = languageName,
              modifier = Modifier
                .padding(12.dp),
              fontSize = stateValues.textSize,
              color = if (isActive)
                stateValues.AccentColor
              else
                stateValues.TextColor
            )

            if (isActive) {
              Row {
                KamelImage(
                  modifier = Modifier
                    .padding(stateValues.textFieldIconPadding)
                    .fillMaxHeight()
                    .aspectRatio(1f, matchHeightConstraintsFirst = true),
                  resource = {
                    asyncPainterResource(
                      data = Url(getFullDrawableResourceUrl(stateValues.drawablePathIconCheck))
                    )
                  },
                  contentDescription = languageName
                )

                Spacer(modifier = Modifier.width(16.dp))
              }
            }
          }
        }
      }
    }
  }
}