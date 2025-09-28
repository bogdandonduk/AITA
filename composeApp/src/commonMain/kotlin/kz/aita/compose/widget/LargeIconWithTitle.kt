package kz.aita.compose.widget

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import io.kamel.image.KamelImage
import io.kamel.image.asyncPainterResource
import io.kamel.image.config.LocalKamelConfig
import io.ktor.http.Url
import kz.aita.AppUIConfiguration
import kz.aita.core.getFullDrawableResourceUrl
import kz.aita.compose.render.kamelConfig

@Composable
fun AppUIConfiguration.LargeIconWithTitleWidget(
  modifier: Modifier = Modifier,
  imageUrl: String,
  title: String = "",
  titleTextSize: TextUnit = stateValues.titleTextSize,
  titleTextColor: Color = stateValues.TextColor,
  contentDescription: String = title
) {

  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    CompositionLocalProvider(LocalKamelConfig provides kamelConfig) {
      KamelImage(
        resource = {
          asyncPainterResource(
            data = Url(getFullDrawableResourceUrl(imageUrl))
          )
        },
        contentScale = ContentScale.FillWidth,
        contentDescription = contentDescription
      )
    }

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