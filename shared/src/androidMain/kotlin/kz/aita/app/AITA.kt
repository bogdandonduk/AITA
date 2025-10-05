package kz.aita.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AITA : Application() {

  override fun onCreate() {
    super.onCreate()

    instance = this
  }

  companion object {
    private lateinit var instance: AITA

    fun get() = instance

  }
}