package kz.aita.widget

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kz.aita.AppUIConfiguration
import kz.aita.model.TabContent
import kz.aita.model.TabRowContent

@Composable
fun AppUIConfiguration.tabRowWidget(
  modifier: Modifier = Modifier,
  tabs: List<TabContent>,

  selectedContainerColor: Color = stateValues.AccentColor,
  unselectedContainerColor: Color = Color.Transparent,

  selectedTextColor: Color = stateValues.AccentTextColor,
  unselectedTextColor: Color = stateValues.TextColor,

  cornerRadius: Dp = stateValues.cornerRadius,

  textSize: TextUnit = stateValues.textSize,

  titleText: String = "",
  titleTextSize: TextUnit = stateValues.accentTextSize,
  titleTextColor: Color = stateValues.TextColor,
): TabRowContent {
  var index by rememberSaveable {
    mutableStateOf(0)
  }

  if (tabs.isNotEmpty()) {
    Column(modifier = modifier) {
      titleText.takeIf { it.isNotEmpty() && it.isNotBlank() }?.apply {
        Text(
          text = this,
          modifier = Modifier,
          style = TextStyle(
            color = titleTextColor,
            fontSize = titleTextSize,
            fontWeight = FontWeight.Bold
          )
        )
      }

      Row(
        modifier = Modifier
          .clip(RoundedCornerShape(cornerRadius))
      ) {
        tabs.forEachIndexed { i, tab ->
          val isSelected = i == index

          val containerColor by animateColorAsState(
            targetValue = if (isSelected) selectedContainerColor else unselectedContainerColor
          )
          val textColor by animateColorAsState(
            targetValue = if (isSelected) selectedTextColor else unselectedTextColor
          )

          Box(
            modifier = Modifier
              .weight(1f)
              .background(containerColor)
              .clickable(
                interactionSource = remember {
                  MutableInteractionSource()
                },
                indication = ripple(color = textColor)
              ) {
                index = i

                tab.onClick?.invoke(i)
              },
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = tab.text,
              modifier = Modifier
                .padding(8.dp),
              fontSize = textSize,
              color = textColor,
              textAlign = TextAlign.Center,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        }
      }
    }
  }

  return TabRowContent(index)
}
