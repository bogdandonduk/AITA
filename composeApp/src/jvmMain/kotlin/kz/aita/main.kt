package kz.aita

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kz.aita.screen.UserAuthLogInScreen

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "AITA",
    ) {
        UserAuthLogInScreen()
    }
}