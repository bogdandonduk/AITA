package kz.aita.compose.widget

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.kamel.image.KamelImage
import io.kamel.image.asyncPainterResource
import io.kamel.image.config.LocalKamelConfig
import io.ktor.http.*
import kz.aita.AppConfiguration
import kz.aita.compose.render.kamelConfig
import kz.aita.core.getFullDrawableResourceUrl
import kz.aita.model.dataModel.LocalizedStringDataModel

@Composable
fun AppConfiguration.selectableDomainWidget(
  modifier: Modifier = Modifier,
  domain: SelectableDomain,
  showName: Boolean = false,
  textColor: Color = stateValues.TextColor,
  onClick: (() -> Unit)? = null
): SelectableDomainWidgetContent {

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
        contentDescription = domain.name.find { it.language == stateValues.appLocaleLanguage }?.value ?: domain.id
      )

      KamelImage(
        modifier = Modifier
          .padding(stateValues.textFieldIconPadding)
          .fillMaxHeight()
          .aspectRatio(1f, matchHeightConstraintsFirst = true),
        resource = {
          asyncPainterResource(
            data = Url(getFullDrawableResourceUrl(domain.iconPath))
          )
        },
        contentDescription = domain.name.find { it.language == stateValues.appLocaleLanguage }?.value ?: domain.id
      )
    }

    Text(
      text = domain.id,
      color = textColor,
      modifier = Modifier
        .padding(8.dp)
    )

    if (showName) {
      Spacer(modifier = Modifier.width(8.dp))

      Text(
        text = domain.name.find { it.language == stateValues.appLocaleLanguage }?.value ?: "",
        color = textColor
      )
    }
  }

  return SelectableDomainWidgetContent(expanded)
}

data class SelectableDomainWidgetContent(
  var expanded: Boolean
)

class SelectableDomain(
  val id: String,
  val name: List<LocalizedStringDataModel>,
  val iconPath: String
)
