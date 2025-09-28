package kz.aita

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kz.aita.compose.screen.UserAuthLogInScreenNarrow
import kz.aita.compose.screen.UserAuthSignUpScreenNarrow

fun main() {
  application {
    Window(
      onCloseRequest = ::exitApplication,
      title = "AITA",
    ) {
      AppUIConfiguration(
        narrowScreenContent = {
          UserAuthSignUpScreenNarrow()
        },
        wideScreenContent = {
          UserAuthSignUpScreenNarrow()
        }
      )
    }
  }
}