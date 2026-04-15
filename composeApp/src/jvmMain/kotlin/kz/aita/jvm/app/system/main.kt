package kz.aita.jvm.app.system

import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kz.aita.cacheDirPath
import kz.aita.compose.AppConfiguration
import kz.aita.compose.MainScreen
import kz.aita.jvm.app.system.core.getOrCreateCacheDirPath
import kz.aita.jvm.core.TokenStore
import kz.aita.jvm.core.UserAccountStore
import kz.aita.tokenStore
import kz.aita.userAccountStore

fun main() {
  cacheDirPath = getOrCreateCacheDirPath()
  tokenStore = TokenStore()
  userAccountStore = UserAccountStore()

  application {
    Window(
      onCloseRequest = ::exitApplication,
      title = "AITA",
      icon = painterResource("drawable/app_icon.ico")
    ) {
      AppConfiguration(
        content = {
          MainScreen()
        }
      )
    }
  }
}