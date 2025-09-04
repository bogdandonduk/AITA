package kz.aita

import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kz.aita.core.configuration.AppConfiguration
import kz.aita.screen.UserAuthLogInScreen

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "AITA",
    ) {
        AppConfiguration {
            UserAuthLogInScreen()
        }
    }
}