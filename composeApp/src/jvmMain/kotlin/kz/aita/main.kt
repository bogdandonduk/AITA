package kz.aita

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kz.aita.compose.screen.MainScreen
import kz.aita.compose.screen.UserAuthLogInScreenNarrow

fun main() {
  application {
    Window(
      onCloseRequest = ::exitApplication,
      title = "AITA",
    ) {
      AppUIConfiguration(
        content = {
          MainScreen()
        }
      )
    }
  }
}