package kz.aita.android.app.system

import android.app.Application
import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import dagger.hilt.android.HiltAndroidApp
import kz.aita.KeyValueDatabase
import kz.aita.android.core.TokenStore
import kz.aita.android.core.UserAccountStore
import kz.aita.core.cacheDirPath
import kz.aita.core.sqlDelightDriver
import kz.aita.core.tokenStore
import kz.aita.core.userAccountStore

@HiltAndroidApp
class AITA: Application() {

  override fun onCreate() {
    super.onCreate()

    instance = this
    cacheDirPath = cacheDir.absolutePath
    sqlDelightDriver =
      AndroidSqliteDriver(
        schema = KeyValueDatabase.Schema.synchronous(),
        context = this,
        name = "key_value.db"
      )

    tokenStore = TokenStore()
    userAccountStore = UserAccountStore()
  }

  companion object {
    private lateinit var instance: AITA

    fun get() = instance
  }
}
