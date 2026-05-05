// THIS IS JvmMainCompose.kt - in jvmMain compose module of kmp compose app

package kz.aita

import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.github.javakeyring.Keyring
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.security.SecureRandom
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

private val keyringService = "aita_keyring"
private val keyring: Keyring = Keyring.create()

private val rng = SecureRandom()



fun loadOrCreateKey(account: String): ByteArray {
  val existingKey = try {
    keyring.getPassword(keyringService, account)
      ?.let { Base64.getUrlDecoder().decode(it) }
      ?.takeIf { it.size == 32 }
  } catch (thr: Throwable) {
    thr.printStackTrace()
    null
  }

  if (existingKey != null) return existingKey

  val raw = ByteArray(32).also { rng.nextBytes(it) }
  val b64 = Base64.getUrlEncoder().withoutPadding().encodeToString(raw)
  keyring.setPassword(keyringService, account, b64)

  return raw
}

fun main() {
  cacheDirPath = Files.createDirectories(
    System.getProperty("os.name").lowercase().run {
      when {
        contains("win") -> {
          val base = System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home")
          Paths.get(
            base,
            ".aita",
            "Cache"
          )
        }

        contains("mac") -> {
          Paths.get(
            System.getProperty("user.home"),
            ".aita",
            "Caches",
            "AITA"
          )
        }

        else -> {
          Paths.get(
            System.getenv("XDG_CACHE_HOME") ?: "${System.getProperty("user.home")}/.cache",
            ".aita"
          )
        }
      }
    }
  ).toFile().absolutePath

  val cacheFile = File(cacheDirPath, "ua.bin")

  getStoredUserAuthTokens = {
    try {
      val raw = keyring.getPassword(keyringService, "auth_tokens")
      jsonBase.decodeFromString<TokenPair>(raw)
    } catch (_: Throwable) {
      null
    }
  }
  setStoredUserAuthTokens = {
    if (it == null) {
      try {
        keyring.deletePassword(keyringService, "auth_tokens")
      } catch (_: Throwable) { }
    } else {
      try {
        val payload = jsonBase.encodeToString(it)
        keyring.setPassword(keyringService, "auth_tokens", payload)
      } catch (_: Throwable) { }
    }
  }
  getStoredUserAccountDataModel = {
    try {
      if (!cacheFile.exists())
        null
      else {
        val key = loadOrCreateKey("user_account_encryption_key")

        val blob = cacheFile.readBytes()
        if (blob.size < 13)
          null
        else {
          val iv = blob.copyOfRange(0, 12)
          val ct = blob.copyOfRange(12, blob.size)

          runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
              init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            }

            val plain = cipher.doFinal(ct)
            jsonBase.decodeFromString<UserAccountDataModel>(plain.toString(Charsets.UTF_8))
          }.getOrNull()
        }
      }
    } catch (throwable: Throwable) {
      throwable.printStackTrace()
      null
    }
  }
  setStoredUserAccountDataModel = { value ->
    try {
      if (value == null) {
        try {
          cacheFile.delete()
          keyring.deletePassword(keyringService, "user_account_encryption_key")
        } catch (_: Throwable) { }
      } else {
        val key = loadOrCreateKey("user_account_encryption_key")
        val plain = jsonBase.encodeToString(value).toByteArray(Charsets.UTF_8)
        val iv = ByteArray(12).also { rng.nextBytes(it) }

        val cipher = Cipher
          .getInstance("AES/GCM/NoPadding")
          .apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
          }

        val ct = cipher.doFinal(plain)

        val tmp = File(cacheFile.parentFile, cacheFile.name + ".tmp")
        tmp.writeBytes(iv + ct)

        try {
          Files.move(
            tmp.toPath(),
            cacheFile.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE
          )
        } catch (_: Throwable) {
          Files.move(
            tmp.toPath(),
            cacheFile.toPath(),
            StandardCopyOption.REPLACE_EXISTING
          )
        }
      }
    } catch (thr: Throwable) {
      thr.printStackTrace()
    }
  }

  init()

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

