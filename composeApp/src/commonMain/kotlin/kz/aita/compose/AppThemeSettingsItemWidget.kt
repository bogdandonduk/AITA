package kz.aita.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun AppConfiguration.AppThemeSettingsItemWidget(
  id: Long,
  name: String,
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
            setAppTheme(id)
          }
        }
      ),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
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

    if (isActive) {
      Row {
        val drawableResIconCheck by stateValues.drawableResIconCheck.collectAsState()

        CpImage(
          modifier = Modifier
            .padding(stateValues.textFieldIconPadding)
            .fillMaxHeight()
            .aspectRatio(1f, matchHeightConstraintsFirst = true),
          url = stateValues.drawablePathIconCheck,
          fallbackRes = drawableResIconCheck,
          contentDescription = name,
          tintColor = stateValues.AccentColor
        )

        Spacer(modifier = Modifier.width(12.dp))
      }
    }

  }
}
