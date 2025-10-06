package kz.aita

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kz.aita.compose.screen.MainScreen
import kz.aita.core.io
import kz.aita.core.osCacheDirPath
import java.nio.file.Files
import java.nio.file.Paths

fun main() {
  osCacheDirPath = Files.createDirectories(
    System.getProperty("os.name").lowercase().run {
      when {
        contains("win") -> {
          val base = System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home")
          Paths.get(
            base,
            "AITA",
            "Cache",
            "http"
          )
        }

        contains("mac") -> {
          Paths.get(
            System.getProperty("user.home"),
            "Library",
            "Caches",
            "AITA",
            "http"
          )
        }

        else -> {
          Paths.get(
            System.getenv("XDG_CACHE_HOME") ?: "${System.getProperty("user.home")}/.cache",
            "AITA", "http"
          )
        }
      }
    }
  ).toFile().absolutePath

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