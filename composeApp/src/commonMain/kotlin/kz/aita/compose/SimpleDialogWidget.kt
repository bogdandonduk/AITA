package kz.aita.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight

@Composable
fun AppConfiguration.SimpleDialogWidget(
  modifier: Modifier = Modifier,
  title: String,
  positiveAction: Pair<String, () -> Unit>,
  negativeAction: Pair<String, () -> Unit>
) {
  Column(
    modifier = modifier
      .fillMaxWidth(0.4f),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Text(
      text = title,
      fontWeight = FontWeight.Bold,
      fontSize = stateValues.titleTextSize
    )

    Row {
      actionButton(text = positiveAction.first, onClick = positiveAction.second)
      actionButton(text = negativeAction.first, onClick = negativeAction.second)
    }
  }
}