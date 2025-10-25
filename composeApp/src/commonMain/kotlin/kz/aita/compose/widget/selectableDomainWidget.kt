package kz.aita.compose.widget

import androidx.compose.animation.core.MutableTransitionState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.kamel.image.KamelImage
import io.kamel.image.asyncPainterResource
import io.kamel.image.config.LocalKamelConfig
import io.ktor.http.*
import kz.aita.AppConfiguration
import kz.aita.compose.render.kamelConfig
import kz.aita.core.Searchable
import kz.aita.core.extractLocalizedString
import kz.aita.core.getFullDrawableResourceUrl
import kz.aita.model.dataModel.LocalizedStringDataModel

@Composable
fun AppConfiguration.selectableDomainWidget(
  modifier: Modifier = Modifier,
  domain: SelectableDomain,
  state: MutableTransitionState<Boolean>? = null,
  showId: Boolean = true,
  showName: Boolean = false,
  showExpansion: Boolean = false,
  reverseExpandIconPosition: Boolean = false,
  textColor: Color = stateValues.TextColor,
  onClick: (() -> Unit)? = null
): SelectableDomainWidgetContent {

  var expanded by rememberSaveable {
    mutableStateOf(state?.targetState == true)
  }

  LaunchedEffect(expanded) {
    state?.targetState = expanded
  }

  LaunchedEffect(state?.targetState) {
    expanded = state?.targetState == true
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
      if (!reverseExpandIconPosition) {
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
              data = Url(getFullDrawableResourceUrl(if (!showExpansion) { "" } else { if (expanded) stateValues.drawablePathIconExpandLess else stateValues.drawablePathIconExpandMore }))
            )
          },
          contentDescription = if (showName)
            domain.name?.extractLocalizedString(stateValues.appLanguage) ?: domain.displayId.extractLocalizedString(stateValues.appLanguage)
          else
            domain.displayId.extractLocalizedString(stateValues.appLanguage) ?: domain.name?.extractLocalizedString(stateValues.appLanguage)
        )
      }

      domain.iconPath?.let {
        KamelImage(
          modifier = Modifier
            .padding(stateValues.textFieldIconPadding)
            .fillMaxHeight()
            .aspectRatio(1f, matchHeightConstraintsFirst = true),
          resource = {
            asyncPainterResource(
              data = Url(getFullDrawableResourceUrl(it))
            )
          },
          contentDescription = if (showName)
            domain.name?.extractLocalizedString(stateValues.appLanguage) ?: domain.displayId.extractLocalizedString(stateValues.appLanguage)
          else
            domain.displayId.extractLocalizedString(stateValues.appLanguage) ?: domain.name?.extractLocalizedString(stateValues.appLanguage)
        )
      }
    }

    if (showId)
      Text(
        text = domain.displayId.extractLocalizedString(stateValues.appLanguage) ?: domain.id,
        color = textColor,
        modifier = Modifier
          .padding(8.dp),
        overflow = TextOverflow.Ellipsis
      )

    if (showName && domain.name != null) {
      Spacer(modifier = Modifier.width(8.dp))

      Text(
        text = domain.name.extractLocalizedString(stateValues.appLanguage) ?: "",
        color = textColor,
        overflow = TextOverflow.Ellipsis
      )
    }

    if (reverseExpandIconPosition) {
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
            data = Url(getFullDrawableResourceUrl(if (!showExpansion) { "" } else { if (expanded) stateValues.drawablePathIconExpandLess else stateValues.drawablePathIconExpandMore }))
          )
        },
        contentDescription = if (showName)
          domain.name?.extractLocalizedString(stateValues.appLanguage) ?: domain.displayId.extractLocalizedString(stateValues.appLanguage)
        else
          domain.displayId.extractLocalizedString(stateValues.appLanguage) ?: domain.name?.extractLocalizedString(stateValues.appLanguage)
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
  val displayId: List<LocalizedStringDataModel>,
  val name: List<LocalizedStringDataModel>?,
  val iconPath: String?
): Searchable {

  override val exactSearchOperands: List<String> = mutableListOf<String>().apply {
    addAll(displayId.map { it.value })
    name?.let { addAll(name.map { it.value }) }
  }

  override val containsSearchOperands: List<String> = mutableListOf<String>().apply {
    addAll(displayId.map { it.value })
    name?.let { addAll(name.map { it.value }) }
  }
  override val uniqueSearchOperands: List<String> = mutableListOf<String>().apply {
    addAll(displayId.map { it.value })
    name?.let { addAll(name.map { it.value }) }
  }

}
