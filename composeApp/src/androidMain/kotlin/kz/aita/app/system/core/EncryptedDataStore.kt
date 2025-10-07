package kz.aita.app.system.core

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kz.aita.app.system.AITA
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object EncryptedDataStore {
  private const val ALIAS_KEYSTORE = "aita_keystore"

  suspend fun get(key: String): String? {
    println("here we are1")
    val preferencesKey = stringPreferencesKey(key)
    println("here we are2")

    val base64: String = AITA.get()
      .tokensDataStore
      .data
      .map { it[preferencesKey] }
      .first() ?: return null

    println("here we are3")

    val blob = Base64.decode(base64, Base64.DEFAULT)
    val key: SecretKey = getOrCreateKey()

    println("here we are4")

    return try {
      val iv = blob.copyOfRange(0, 12)
      val ct = blob.copyOfRange(12, blob.size)

      val cipher = Cipher
        .getInstance("AES/GCM/NoPadding")
        .apply {
          init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        }

      val plain = cipher.doFinal(ct)

      plain.decodeToString().apply {
        println("here we are5 $this")
      }
    } catch (throwable: Throwable) {
      AITA.get().tokensDataStore.edit { it.remove(preferencesKey) }
      throwable.printStackTrace()
      println("here we are6")

      null
    }
  }

  suspend fun set(key: String, value: String?) {
    println("here we are11")

    val preferencesKey = stringPreferencesKey(key)

    if (value == null) {
      AITA.get().tokensDataStore.edit { it.remove(preferencesKey) }
      return
    }
    println("here we are12")

    val key: SecretKey = getOrCreateKey()
    println("here we are13")

    val plain = value.encodeToByteArray()
    println("here we are14")

    val cipher = Cipher
      .getInstance("AES/GCM/NoPadding")
      .apply {
        init(Cipher.ENCRYPT_MODE, key)
      }
    println("here we are15")

    val iv = cipher.iv                                 // 12-byte nonce
    val ct = cipher.doFinal(plain)                     // ciphertext + 16-byte tag
    println("here we are16")

    val blob = iv + ct
    val base64 = Base64.encodeToString(blob, Base64.NO_WRAP)
    println("here we are17 $base64")

    AITA.get().tokensDataStore.edit { it[preferencesKey] = base64 }
  }

  private fun getOrCreateKey(): SecretKey {
    val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    (ks.getKey(ALIAS_KEYSTORE, null) as? SecretKey)?.let { return it }

    val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
    val spec = KeyGenParameterSpec.Builder(
      ALIAS_KEYSTORE,
      KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
    )
      .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
      .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
      .setRandomizedEncryptionRequired(true)
      .build()

    generator.init(spec)

    return generator.generateKey()
  }
}
