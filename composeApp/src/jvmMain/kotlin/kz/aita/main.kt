package kz.aita

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.kamel.core.config.Core
import io.kamel.core.config.KamelConfig
import io.kamel.core.config.takeFrom
import io.kamel.image.KamelImage
import io.kamel.image.asyncPainterResource
import io.kamel.image.config.LocalKamelConfig
import kz.aita.core.configuration.AppUIConfiguration
import kz.aita.model.store.ConfigurationStore
import io.kamel.image.config.batikSvgDecoder
import io.kamel.image.config.imageBitmapDecoder
import io.kamel.image.config.svgDecoder
import io.ktor.http.Url
import kz.aita.core.remote.RemoteConfiguration
import kz.aita.screen.UserAuthLogInScreen

fun main() {
  KamelConfig {
    takeFrom(KamelConfig.Core)
    batikSvgDecoder()
  }

  application {
    val globalConfiguration by ConfigurationStore.globalAppConfigurationState.collectAsState()

    Window(
      onCloseRequest = ::exitApplication,
      title = "AITA",
    ) {
      AppUIConfiguration {
        UserAuthLogInScreen()
      }
    }
  }
}