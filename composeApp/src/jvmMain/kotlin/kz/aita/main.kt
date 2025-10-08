package kz.aita

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.github.javakeyring.Keyring
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kz.aita.compose.screen.MainScreen
import kz.aita.core.DataStore
import kz.aita.core.io
import kz.aita.core.jsonBase
import kz.aita.core.osCacheDirPath
import kz.aita.core.tokenStore
import kz.aita.core.userAccountStore
import kz.aita.model.dataModel.UserAccountDataModel
import kz.aita.model.wrapper.TokenPair
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

  tokenStore = object: DataStore<TokenPair> {
    private val service = "aita_keyring"
    private val account = "auth_tokens"
    private val keyring: Keyring = Keyring.create()

    override suspend fun get(): TokenPair? {
      return try {
        val raw = keyring.getPassword(service, account)
        jsonBase.decodeFromString<TokenPair>(raw)
      } catch (_: Throwable) {
        null
      }
    }

    override suspend fun set(value: TokenPair?) {
      if (value == null) {
        try {
          keyring.deletePassword(service, account)
        } catch (_: Throwable) { }

        return
      }

      try {
        val payload = jsonBase.encodeToString(value)
        keyring.setPassword(service, account, payload)
      } catch (_: Throwable) { }
    }
  }

  userAccountStore = object: DataStore<UserAccountDataModel> {
    private val service = "aita_keyring"
    private val account = "user_account"
    private val keyring: Keyring = Keyring.create()

    override suspend fun get(): UserAccountDataModel? {
      return try {
        val raw = keyring.getPassword(service, account)
        jsonBase.decodeFromString<UserAccountDataModel>(raw)
      } catch (_: Throwable) {
        null
      }
    }

    override suspend fun set(value: UserAccountDataModel?) {
      if (value == null) {
        try {
          keyring.deletePassword(service, account)
        } catch (_: Throwable) { }

        return
      }

      try {
        val payload = jsonBase.encodeToString(value)
        keyring.setPassword(service, account, payload)
      } catch (_: Throwable) { }
    }
  }

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