package kz.aita

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kz.aita.compose.screen.MainScreen

fun main() {
  application {
    Window(
      onCloseRequest = ::exitApplication,
      title = "AITA",
    ) {
      AppConfiguration(
        content = {
          MainScreen()
        }
      )
    }
  }
}