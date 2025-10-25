package kz.aita.compose.widget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kz.aita.AppConfiguration

@Composable
fun AppConfiguration.MessageText(
  modifier: Modifier = Modifier,
  text: String,
  subText: String? = null,
  textColor: Color = stateValues.TextColor,
  textSize: TextUnit = stateValues.accentTextSize,
  subTextColor: Color = textColor,
  subTextSize: TextUnit = stateValues.textSize
) {
  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Text(
      text = text,
      modifier = Modifier
        .padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = if (subText == null) 24.dp else 0.dp),
      color = textColor,
      fontSize = textSize,
      fontWeight = FontWeight.Bold
    )

    subText?.run {
      Text(
        text = this,
        modifier = Modifier
          .padding(start = 24.dp, bottom = 24.dp, end = 24.dp),
        color = subTextColor,
        fontSize = subTextSize
      )
    }
  }
}