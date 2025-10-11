package kz.aita.compose.widget

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import kz.aita.AppConfiguration

@Composable
fun AppConfiguration.MessageText(
  modifier: Modifier = Modifier,
  text: String,
  textColor: Color = stateValues.TextColor,
  textSize: TextUnit = stateValues.accentTextSize
) {
  Box(
    modifier = modifier,
    contentAlignment = Alignment.Center
  ) {
    Text(
      text = text,
      color = textColor,
      fontSize = textSize
    )
  }
}