package kz.aita.android.app.system

import android.app.Application
import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import dagger.hilt.android.HiltAndroidApp
import kz.aita.*
import kz.aita.android.core.TokenStore
import kz.aita.android.core.UserAccountStore

@HiltAndroidApp
class AITA: Application() {

  override fun onCreate() {
    super.onCreate()

    instance = this
    cacheDirPath = cacheDir.absolutePath
    sqlDelightDriver =
      AndroidSqliteDriver(
        schema = AppDatabase.Schema.synchronous(),
        context = this,
        name = "app_database.db"
      )
    
    tokenStore = TokenStore()
    userAccountStore = UserAccountStore()
  }

  companion object {
    private lateinit var instance: AITA

    fun get() = instance
  }
}
