package kz.aita.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import io.kamel.core.config.Core
import io.kamel.core.config.DefaultCacheSize
import io.kamel.core.config.KamelConfig
import io.kamel.core.config.fileFetcher
import io.kamel.core.config.fileUrlFetcher
import io.kamel.core.config.httpUrlFetcher
import io.kamel.core.config.takeFrom
import io.kamel.core.utils.URL
import io.kamel.image.KamelImage
import io.kamel.image.asyncPainterResource
import io.kamel.image.config.LocalKamelConfig
import io.ktor.http.Url
import kz.aita.core.configuration.AppUIConfiguration
import kz.aita.core.remote.RemoteConfiguration
import kz.aita.render.kamelConfig

@Composable
fun AppUIConfiguration.UserAuthLogInScreen() {
  LazyColumn(
    modifier = Modifier
      .background(BackgroundColor)
      .fillMaxSize()
  ) {
    item {
      Column {
        CompositionLocalProvider(LocalKamelConfig provides kamelConfig) {
          KamelImage(
            resource = {
              asyncPainterResource(
                data = Url("${RemoteConfiguration.SERVER_URL}${RemoteConfiguration.DRAWABLE_SVG_RESOURCES_PATH}10.svg")
              )
            },
            contentDescription = ""
          )
        }
      }
    }
  }
}
