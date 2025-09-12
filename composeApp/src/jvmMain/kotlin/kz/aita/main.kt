package kz.aita

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.kamel.core.config.Core
import io.kamel.core.config.KamelConfig
import io.kamel.core.config.takeFrom
import kz.aita.core.configuration.AppUIConfiguration
import kz.aita.model.store.ConfigurationStore
import io.kamel.image.config.batikSvgDecoder
import kz.aita.screen.UserAuthLogInScreenNarrow

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
      AppUIConfiguration(
        narrowScreenContent = {
          UserAuthLogInScreenNarrow()
        },
        wideScreenContent = {
          UserAuthLogInScreenNarrow()
        },
      )
    }
  }
}