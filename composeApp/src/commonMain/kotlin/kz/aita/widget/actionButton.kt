package kz.aita.widget

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kamel.image.KamelImage
import io.kamel.image.asyncPainterResource
import io.kamel.image.config.LocalKamelConfig
import io.ktor.http.Url
import kz.aita.AppUIConfiguration
import kz.aita.core.getFullDrawableResourceUrl
import kz.aita.render.kamelConfig
import kz.aita.model.ButtonContent

@Composable
fun AppUIConfiguration.actionButton(
  modifier: Modifier = Modifier,

  enabledColor: Color = stateValues.AccentColor,
  disabledColor: Color = stateValues.DisabledColor,

  text: String,
  textColor: Color = stateValues.AccentTextColor,

  subText: String = "",
  subTextColor: Color = textColor,

  cornerRadius: Dp = stateValues.cornerRadius,

  iconPath: String? = null,
  iconContentDescription: String = text,

  onLongClick: (() -> Unit)? = null,
  onClick: () -> Unit
): ButtonContent {
  var enabled by rememberSaveable {
    mutableStateOf(true)
  }

  val backgroundColor by animateColorAsState(
    targetValue = if (enabled) enabledColor else disabledColor
  )

  val textPresent =
    text.isNotEmpty() && text.isNotBlank()

  val subTextPresent =
    subText.isNotEmpty() && subText.isNotBlank()

  Row(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(cornerRadius))
      .background(backgroundColor)
      .run {
        if (enabled)
          clickable(
            onClick = onClick,
            interactionSource = remember {
              MutableInteractionSource()
            },
            indication = ripple(color = textColor)
          )
        else
          this
      }
      .run {
        onLongClick?.let {
          pointerInput(Unit) {
            detectTapGestures(
              onLongPress = {
                it()
              }
            )
          }
        } ?: this
      },
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically
  ) {
    val iconPadding = 9.dp

    iconPath?.run {
      CompositionLocalProvider(LocalKamelConfig provides kamelConfig) {
        KamelImage(
          modifier = Modifier
            .padding(iconPadding)
            .fillMaxHeight()
            .aspectRatio(1f, matchHeightConstraintsFirst = true),
          resource = {
            asyncPainterResource(
              data = Url(getFullDrawableResourceUrl(iconPath))
            )
          },
          contentDescription = iconContentDescription
        )
      }
    }

    Column(
      modifier = Modifier
        .fillMaxHeight()
        .padding(12.dp),
      verticalArrangement = Arrangement.Center
    ) {
      if (textPresent) {
        Text(
          modifier = Modifier.fillMaxWidth(),
          text = text,
          color = textColor,
          fontWeight = FontWeight.Bold,
          fontSize = stateValues.accentTextSize,
          textAlign = TextAlign.Center
        )

        if (subTextPresent) {
          Text(
            modifier = Modifier.fillMaxWidth(),
            text = subText,
            color = subTextColor,
            fontSize = stateValues.textSize,
            textAlign = TextAlign.Center
          )
        }
      }

    }
  }

  return ButtonContent(enabled)
}