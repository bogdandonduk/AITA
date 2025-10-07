package kz.aita.app.system

import android.app.Application
import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import dagger.hilt.android.HiltAndroidApp
import kz.aita.KeyValueDatabase
import kz.aita.app.system.core.EncryptedDataStore
import kz.aita.core.TokenStore
import kz.aita.core.jsonBase
import kz.aita.core.osCacheDirPath
import kz.aita.core.sqlDelightDriver
import kz.aita.core.tokenStore
import kz.aita.model.wrapper.TokenPair

@HiltAndroidApp
class AITA : Application() {

  override fun onCreate() {
    super.onCreate()

    instance = this
    osCacheDirPath = cacheDir.absolutePath
    sqlDelightDriver =
      AndroidSqliteDriver(
        schema = KeyValueDatabase.Schema.synchronous(),
        context = this,
        name = "key_value.db"
      )

    tokenStore = object: TokenStore {
      override suspend fun get(): TokenPair? {
        println("blyat")
        return EncryptedDataStore.get("key_auth_tokens")?.run { jsonBase.decodeFromString<TokenPair>(this) }
      }

      override suspend fun set(tokens: TokenPair?) {
        println("blyat2")

        EncryptedDataStore.set("key_auth_tokens", tokens?.run { jsonBase.encodeToString(tokens) })
      }
    }
  }

  companion object {
    private lateinit var instance: AITA

    fun get() = instance
  }
}
