package kz.aita.jvm.core

import com.github.javakeyring.Keyring
import io.ktor.client.HttpClient
import kz.aita.core.DataStore
import kz.aita.core.cacheDirPath
import kz.aita.core.jsonBase
import kz.aita.model.dataModel.UserAccountDataModel
import kz.aita.model.wrapper.TokenPair
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.SecureRandom
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class TokenStore: DataStore<TokenPair> {
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

class UserAccountStore: DataStore<UserAccountDataModel> {
  private val service = "aita_keyring"
  private val account = "user_account"
  private val keyring: Keyring = Keyring.create()

  private val rng = SecureRandom()

  val file = File(cacheDirPath, "ua.bin")

  private fun loadOrCreateKey(): ByteArray {

    val keyRaw = try {
      keyring.getPassword(service, account)
    } catch (thr: Throwable) {
      thr.printStackTrace()
      null
    }

    val key = if (keyRaw != null) {
      Base64.getUrlDecoder().decode(keyRaw)
    } else {
      val raw = ByteArray(32).also { rng.nextBytes(it) }
      val b64 = Base64.getUrlEncoder().withoutPadding().encodeToString(raw)
      keyring.setPassword(service, account, b64)

      raw
    }

    return key
  }

  override suspend fun get(): UserAccountDataModel? {
    return try {
      if (!file.exists())
        return null

      val key = loadOrCreateKey()

      val blob = file.readBytes()
      if (blob.size < 13)
        return null
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
    } catch (throwable: Throwable) {
      throwable.printStackTrace()
      null
    }
  }

  override suspend fun set(value: UserAccountDataModel?) {
    try {
      if (value == null) {
        try {
          file.delete()
          keyring.deletePassword(service, account)
        } catch (_: Throwable) { }

        return
      }

      val key = loadOrCreateKey()
      val plain = jsonBase.encodeToString(value).toByteArray(Charsets.UTF_8)
      val iv = ByteArray(12).also { rng.nextBytes(it) }

      val cipher = Cipher
        .getInstance("AES/GCM/NoPadding")
        .apply {
          init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        }

      val ct = cipher.doFinal(plain)

      val tmp = File(file.parentFile, file.name + ".tmp")
      tmp.writeBytes(iv + ct)

      Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    } catch (thr: Throwable) {
      thr.printStackTrace()
    }
  }
}