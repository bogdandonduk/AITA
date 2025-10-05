package kz.aita.compose.widget

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.kamel.image.KamelImage
import io.kamel.image.asyncPainterResource
import io.kamel.image.config.LocalKamelConfig
import io.ktor.http.Url
import kotlinx.coroutines.launch
import kz.aita.AppConfiguration
import kz.aita.compose.render.kamelConfig
import kz.aita.core.getFullDrawableResourceUrl

@Composable
fun AppConfiguration.AppLanguageSettingsItemWidget(
  language: String,
  name: String,
  flagDrawablePath: String,
  isActive: Boolean
) {
  Row(
    modifier = Modifier
      .height(42.dp)
      .fillMaxWidth()
      .clickable(
        interactionSource = remember {
          MutableInteractionSource()
        },
        indication = ripple(color = stateValues.TextColor),
        onClick = {
          coroutineScope.launch {
            setAppLocale(language)
          }
        }
      ),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    CompositionLocalProvider(LocalKamelConfig provides kamelConfig) {
      Row {
        KamelImage(
          modifier = Modifier
            .padding(stateValues.textFieldIconPadding)
            .fillMaxHeight()
            .aspectRatio(1f, matchHeightConstraintsFirst = true),
          resource = {
            asyncPainterResource(
              data = Url(getFullDrawableResourceUrl(flagDrawablePath))
            )
          },
          contentDescription = name
        )

        Text(
          text = name,
          modifier = Modifier
            .padding(12.dp),
          fontSize = stateValues.textSize,
          color = if (isActive)
            stateValues.AccentColor
          else
            stateValues.TextColor,
          fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
        )
      }

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
            contentDescription = name,
            colorFilter = ColorFilter.tint(stateValues.AccentColor)
          )

          Spacer(modifier = Modifier.width(12.dp))
        }
      }
    }
  }
}
