package kz.aita.compose.widget

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kz.aita.AppConfiguration

@Composable
fun AppConfiguration.MessageText(
  modifier: Modifier = Modifier,
  text: String
) {
  Box(
    modifier = modifier,
    contentAlignment = Alignment.Center
  ) {
    Text(
      text = text,
      color = stateValues.TextColor,
      fontSize = stateValues.accentTextSize
    )
  }
}