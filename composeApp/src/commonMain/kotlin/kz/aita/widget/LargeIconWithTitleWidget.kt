package kz.aita.widget

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import io.kamel.image.KamelImage
import io.kamel.image.asyncPainterResource
import kz.aita.core.configuration.AppUIConfiguration

@Composable
fun AppUIConfiguration.LargeIconWithTitleWidget(
  modifier: Modifier = Modifier,
  icon: Painter,
  title: String = "",
  titleTextSize: TextUnit = largeTextSize,
  contentDescription: String = title
) {
  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    KamelImage(
      resource = asyncPainterResource("http://127.0.0.1:8080/drawable/svg/aita_logo.svg"),
      contentDescription = null,
      onLoading = { CircularProgressIndicator() },
      onFailure = { Text("failed to load") },
      contentScale = ContentScale.Fit,
      modifier = modifier
    )

    Image(
      painter = icon,
      contentDescription = contentDescription,
      modifier = Modifier
        .size(200.dp)
    )
    
    Spacer(modifier = Modifier.height(spaceBetweenWidgets))
    
    title.takeIf { it.isNotEmpty() }?.run {
      Text(
        text = title,
        style = TextStyle(
          color = TextColor,
          fontSize = titleTextSize,
          fontWeight = FontWeight.Bold
        )
      )
    }
  }
}