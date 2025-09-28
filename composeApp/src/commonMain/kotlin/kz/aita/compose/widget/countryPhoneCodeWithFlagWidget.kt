package kz.aita.compose.widget

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kamel.image.KamelImage
import io.kamel.image.asyncPainterResource
import io.kamel.image.config.LocalKamelConfig
import io.ktor.http.Url
import kz.aita.AppUIConfiguration
import kz.aita.compose.render.kamelConfig
import kz.aita.core.getFullDrawableResourceUrl
import kz.aita.model.dataModel.CountryDataModel

@Composable
fun AppUIConfiguration.countryPhoneCodeWithFlagWidget(
  modifier: Modifier = Modifier,
  country: CountryDataModel,
  textColor: Color = stateValues.TextColor,
  onClick: (() -> Unit)? = null
  ): CountryPhoneCodeWithFlagWidgetContent {
  var expanded by rememberSaveable {
    mutableStateOf(false)
  }

  Row(
    modifier = modifier
      .height(stateValues.textFieldHeight)
      .run {
        onClick?.let {
          clickable(
            interactionSource = remember {
              MutableInteractionSource()
            },
            indication = ripple(color = textColor),
            onClick = {
              expanded = !expanded
              it()
            }
          )
        } ?: this
      }
      .padding(),
    verticalAlignment = Alignment.CenterVertically
  ) {

    CompositionLocalProvider(LocalKamelConfig provides kamelConfig) {
      KamelImage(
        modifier = Modifier
          .padding(
            start = stateValues.textFieldIconPadding,
            top = stateValues.textFieldIconPadding,
            bottom = stateValues.textFieldIconPadding
          )
          .fillMaxHeight()
          .aspectRatio(1f, matchHeightConstraintsFirst = true),
        resource = {
          asyncPainterResource(
            data = Url(getFullDrawableResourceUrl(if (expanded) stateValues.drawablePathIconExpandLess else stateValues.drawablePathIconExpandMore))
          )
        },
        contentDescription = country.name.find { it.language == stateValues.appLocaleLanguage }?.value ?: country.locale
      )

      KamelImage(
        modifier = Modifier
          .padding(stateValues.textFieldIconPadding)
          .fillMaxHeight()
          .aspectRatio(1f, matchHeightConstraintsFirst = true),
        resource = {
          asyncPainterResource(
            data = Url(getFullDrawableResourceUrl(country.flagDrawablePath))
          )
        },
        contentDescription = country.name.find { it.language == stateValues.appLocaleLanguage }?.value ?: country.locale
      )
    }

    Text(
      text = "+${country.phoneNumberCode}",
      color = textColor,
      modifier = Modifier
        .padding(end = 8.dp)
    )
  }

  return CountryPhoneCodeWithFlagWidgetContent(expanded)
}

data class CountryPhoneCodeWithFlagWidgetContent(
  var expanded: Boolean
)