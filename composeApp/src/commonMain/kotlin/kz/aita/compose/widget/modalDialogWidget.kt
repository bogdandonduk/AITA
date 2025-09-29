package kz.aita.compose.widget

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import kz.aita.AppUIConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
fun AppUIConfiguration.ModalDialogWidget(
  title: String,
  subTitle: String? = null,
  negativeButtonText: String = stateValues.stringCancel,
  positiveButtonText: String = stateValues.stringConfirm,
  backgroundColor: Color = stateValues.BackgroundColor,
  cornerRadius: Dp = stateValues.cornerRadius,
  titleTextSize: TextUnit = stateValues.accentTextSize,
  subTitleTextSize: TextUnit = stateValues.textSize,
  titleTextColor: Color = stateValues.TextColor,
  subTitleTextColor: Color = titleTextColor,
  onDismiss: () -> Unit,
  negativeAction: () -> Unit,
  positiveAction: () -> Unit
) {
  Dialog(
    onDismissRequest = onDismiss,
  ) {
    Column(
      modifier = Modifier
        .clip(RoundedCornerShape(cornerRadius))
        .background(backgroundColor)
        .fillMaxWidth(),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Text(
        text = title,
        modifier = Modifier,
        fontSize = titleTextSize,
        color = titleTextColor,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
      )

      subTitle?.run {
        Text(
          text = subTitle,
          fontSize = subTitleTextSize,
          color = subTitleTextColor,
          textAlign = TextAlign.Center
        )
      }

      Row(
        modifier = Modifier
          .padding(horizontal = 16.dp)
      ) {
        actionButton(
          modifier = Modifier
            .weight(1f),
          enabledColor = Color.Transparent,
          text = negativeButtonText,
          onClick = negativeAction
        )

        Spacer(
          modifier = Modifier
            .width(4.dp)
        )

        actionButton(
          modifier = Modifier
            .weight(1f),
          text = positiveButtonText,
          onClick = negativeAction
        )
      }
    }
  }
}