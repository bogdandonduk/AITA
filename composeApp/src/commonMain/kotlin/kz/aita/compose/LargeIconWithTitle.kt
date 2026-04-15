package kz.aita.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import org.jetbrains.compose.resources.DrawableResource

@Composable
fun AppConfiguration.LargeIconWithTitleWidget(
  modifier: Modifier = Modifier,
  imageUrl: String,
  imageRes: DrawableResource,
  title: String = "",
  titleTextSize: TextUnit = stateValues.titleTextSize,
  titleTextColor: Color = stateValues.TextColor,
  contentDescription: String = title
) {

  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally
  ) {

    CpImage(
      url = imageUrl,
      fallbackRes = imageRes,
      contentDescription = contentDescription
    )

    Spacer(modifier = Modifier.height(stateValues.screenHeight / 24))

    title.takeIf { it.isNotEmpty() }?.run {
      Text(
        text = title,
        style = TextStyle(
          color = titleTextColor,
          fontSize = titleTextSize,
          fontWeight = FontWeight.Bold
        )
      )
    }
  }
}