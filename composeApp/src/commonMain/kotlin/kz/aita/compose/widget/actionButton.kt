package kz.aita.compose.widget

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import io.kamel.image.KamelImage
import io.kamel.image.asyncPainterResource
import io.kamel.image.config.LocalKamelConfig
import io.ktor.http.*
import kz.aita.AppConfiguration
import kz.aita.compose.render.kamelConfig
import kz.aita.core.getFullDrawableResourceUrl

@Composable
fun AppConfiguration.actionButton(
  modifier: Modifier = Modifier,

  enabled: Boolean = true,

  enabledColor: Color = stateValues.AccentColor,
  disabledColor: Color = stateValues.DisabledColor,

  text: String,
  textColor: Color = stateValues.AccentTextColor,
  textSize: TextUnit = stateValues.textSize,

  subText: String = "",
  subTextColor: Color = textColor,
  subTextSize: TextUnit = stateValues.smallTextSize,

  cornerRadius: Dp = stateValues.cornerRadius,

  icon: @Composable (() -> Unit)? = null,

  iconPath: String? = null,
  iconContentDescription: String = text,
  iconTintColor: Color = stateValues.AccentTextColor,

  onLongClick: (() -> Unit)? = null,
  onClick: () -> Unit
): ActionButtonContent {
  var isEnabled by rememberSaveable {
    mutableStateOf(enabled)
  }

  LaunchedEffect(enabled) {
    isEnabled = enabled
  }

  val backgroundColor by animateColorAsState(
    targetValue = if (isEnabled) enabledColor else disabledColor
  )

  val textPresent =
    text.isNotEmpty() && text.isNotBlank()

  val subTextPresent =
    subText.isNotEmpty() && subText.isNotBlank()

  val textHeight = if (!textPresent) 0f else textSize.value
  val subTextHeight = if (subTextPresent) subTextSize.value else 0f

  val height = (textHeight + subTextHeight + 24).dp

  Row(
    modifier = modifier
      .run {
        if (!textPresent)
          wrapContentWidth()
        else
          fillMaxWidth()
      }
      .height(height)
      .clip(RoundedCornerShape(cornerRadius))
      .background(backgroundColor)
      .run {
        if (isEnabled)
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
    icon?.invoke() ?: iconPath?.run {
      CompositionLocalProvider(LocalKamelConfig provides kamelConfig) {
        KamelImage(
          modifier = Modifier
            .fillMaxHeight()
            .padding(2.dp)
            .aspectRatio(1f, matchHeightConstraintsFirst = true),
          resource = {
            asyncPainterResource(
              data = Url(getFullDrawableResourceUrl(iconPath))
            )
          },
          contentDescription = iconContentDescription,
          colorFilter = ColorFilter.tint(iconTintColor)
        )
      }
    }

    Column(
      verticalArrangement = Arrangement.Center
    ) {
      if (textPresent) {
        Text(
          modifier = Modifier.fillMaxWidth(),
          text = text,
          color = textColor,
          fontWeight = FontWeight.Bold,
          fontSize = textSize,
          textAlign = TextAlign.Center
        )

        if (subTextPresent) {
          Text(
            modifier = Modifier.fillMaxWidth(),
            text = subText,
            color = subTextColor,
            fontSize = subTextSize,
            textAlign = TextAlign.Center
          )
        }
      }

    }
  }

  return ActionButtonContent(isEnabled)
}

data class ActionButtonContent(
  var enabled: Boolean
)
