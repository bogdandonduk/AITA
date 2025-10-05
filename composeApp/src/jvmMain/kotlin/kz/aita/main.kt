package kz.aita

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kz.aita.compose.screen.MainScreen
import kz.aita.core.io

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