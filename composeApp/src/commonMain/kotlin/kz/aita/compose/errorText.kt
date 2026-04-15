package kz.aita.compose

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun AppConfiguration.responseText(
  invalidText: String,
  color: Color = stateValues.ErrorColor,
  showIf: () -> Boolean
): ResponseTextContent {
  var show by rememberSaveable {
    mutableStateOf(false)
  }

  show = showIf()

  if (show) {
    Text(
      modifier = Modifier
        .fillMaxWidth()
        .padding(top = 4.dp),
      text = invalidText,
      style = TextStyle(
        color = color,
        fontSize = stateValues.textSize,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
      )
    )
  }

  return ResponseTextContent(
    isActual = !show,
    onActualityCheck = {
      val value = showIf()
      show = value
      value
    }
  )
}

class ResponseTextContent(
  var isActual: Boolean,
  private val onActualityCheck: () -> Boolean
) {

  fun checkContentValidity() {
    isActual = onActualityCheck.invoke()
  }
}