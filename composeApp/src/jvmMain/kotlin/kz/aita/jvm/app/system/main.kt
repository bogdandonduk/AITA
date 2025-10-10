package kz.aita.jvm.app.system

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kz.aita.AppConfiguration
import kz.aita.compose.screen.MainScreen
import kz.aita.core.cacheDirPath
import kz.aita.core.tokenStore
import kz.aita.core.userAccountStore
import kz.aita.jvm.app.system.core.getOrCreateCacheDirPath
import kz.aita.jvm.core.TokenStore
import kz.aita.jvm.core.UserAccountStore

fun main() {
  cacheDirPath = getOrCreateCacheDirPath()
  tokenStore = TokenStore()
  userAccountStore = UserAccountStore()

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