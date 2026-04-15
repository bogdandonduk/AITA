package kz.aita.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource

@Composable
fun AppConfiguration.ScreenAppBarWidget(
  modifier: Modifier = Modifier,
  title: String,
  iconPath: String? = null,
  iconRes: DrawableResource? = null,
  textColor: Color = stateValues.TextColor,
  cornerRadius: Dp = stateValues.cornerRadius,
  leadingContent: @Composable (() -> Unit)? = null,
  trailingIcons: List<Triple<String, DrawableResource, () -> Unit>> = emptyList(),
  onBack: (() -> Unit)? = null
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
  ) {
    Row(
      modifier = modifier
        .run {
          if (stateValues.isNarrowScreen)
            clip(
              RoundedCornerShape(
                bottomStart = cornerRadius,
                bottomEnd = cornerRadius
              )
            )
          else
            this
        }
        .run {
          if (stateValues.isNarrowScreen)
            border(
              stateValues.unfocusedBorderWidth,
              stateValues.PlaceholderTextColor,
              RoundedCornerShape(
                bottomStart = cornerRadius,
                bottomEnd = cornerRadius
              )
            )
          else
            this
        }
        .fillMaxWidth()
        .height(48.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      onBack?.run {
        Box(
          modifier = Modifier
            .fillMaxHeight()
            .clickable(
              interactionSource = remember {
                MutableInteractionSource()
              },
              indication = ripple(color = textColor, radius = cornerRadius),
              onClick = this
            )
        ) {
          val iconRes by stateValues.drawableResIconBackArrow.collectAsState()

          CpImage(
            modifier = Modifier
              .padding(16.dp)
              .aspectRatio(1f, matchHeightConstraintsFirst = true),
            url = stateValues.drawablePathIconBackArrow,
            fallbackRes = iconRes,
            contentDescription = stateValues.stringBack
          )
        }
      }

      leadingContent?.invoke()

      Row(
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
      ) {
        if (iconPath != null) {
          CpImage(
            modifier = Modifier
              .padding(start = 0.dp, top = 14.dp, end = 8.dp, bottom = 14.dp)
              .aspectRatio(1f, matchHeightConstraintsFirst = true),
            url = iconPath,
            fallbackRes = iconRes,
            contentDescription = title
          )
        }

        Text(
          text = title,
          modifier = modifier,
          textAlign = TextAlign.Center,
          fontWeight = FontWeight.Bold,
          fontSize = stateValues.accentTextSize,
          color = textColor
        )
      }

      trailingIcons.takeIf { it.isNotEmpty() }?.run {
        forEach {
          Spacer(modifier = Modifier.width(8.dp))

          actionButton(
            text = "",
            iconPath = it.first,
            iconRes = it.second,
            onClick = it.third
          )
        }

        Spacer(modifier = Modifier.width(16.dp))
      }
    }

    if (!stateValues.isNarrowScreen)
      Spacer(
        modifier = Modifier
          .background(stateValues.PlaceholderTextColor)
          .fillMaxWidth()
          .height(stateValues.unfocusedBorderWidth)
      )
  }
}