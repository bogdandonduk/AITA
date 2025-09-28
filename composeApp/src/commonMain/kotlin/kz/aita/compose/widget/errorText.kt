package kz.aita.compose.widget

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kz.aita.AppUIConfiguration

@Composable
fun AppUIConfiguration.errorText(
  invalidText: String,
  onValidityCheck: () -> Boolean
): ErrorTextContent {
  var isValid by rememberSaveable {
    mutableStateOf(onValidityCheck())
  }

  if (!isValid) {
    Text(
      modifier = Modifier
        .fillMaxWidth()
        .padding(top = 4.dp),
      text = invalidText,
      style = TextStyle(
        color = stateValues.ErrorColor,
        fontSize = stateValues.textSize,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
      )
    )
  }

  return ErrorTextContent(
    isValid = isValid,
    onValidityCheck = {
      val value = onValidityCheck.invoke()
      isValid = value
      value
    }
  )
}

class ErrorTextContent(
  var isValid: Boolean,
  private val onValidityCheck: () -> Boolean
) {

  fun checkContentValidity() {
    isValid = onValidityCheck.invoke()
  }
}