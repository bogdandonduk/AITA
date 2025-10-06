package kz.aita.app.system

import android.app.Application
import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import dagger.hilt.android.HiltAndroidApp
import kz.aita.KeyValueDatabase
import kz.aita.core.osCacheDirPath
import kz.aita.core.sqlDelightDriver
import kotlin.text.get

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
  }

  companion object {
    private lateinit var instance: AITA

    fun get() = instance
  }
}
