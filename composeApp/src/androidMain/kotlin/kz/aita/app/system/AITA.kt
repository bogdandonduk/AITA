package kz.aita.app.system

import android.app.Application
import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import dagger.hilt.android.HiltAndroidApp
import kz.aita.KeyValueDatabase
import kz.aita.app.system.core.EncryptedDataStore
import kz.aita.core.*
import kz.aita.model.dataModel.UserAccountDataModel
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

    tokenStore = object: kz.aita.core.DataStore<TokenPair> {
      private val key = "key_auth_tokens"

      override suspend fun get(): TokenPair? {
        return EncryptedDataStore.get(key)?.run { jsonBase.decodeFromString<TokenPair>(this) }
      }

      override suspend fun set(value: TokenPair?) {
        EncryptedDataStore.set(key, value?.run { jsonBase.encodeToString(this) })
      }
    }

    userAccountStore = object: kz.aita.core.DataStore<UserAccountDataModel> {
      private val key = "key_user_account"

      override suspend fun get(): UserAccountDataModel? {
        return EncryptedDataStore.get(key)?.run { jsonBase.decodeFromString<UserAccountDataModel>(this) }
      }

      override suspend fun set(value: UserAccountDataModel?) {
        EncryptedDataStore.set(key, value?.run { jsonBase.encodeToString(this) })
      }
    }
  }

  companion object {
    private lateinit var instance: AITA

    fun get() = instance
  }
}
