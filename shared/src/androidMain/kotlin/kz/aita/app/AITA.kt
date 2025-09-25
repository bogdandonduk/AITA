package kz.aita.app

import android.app.Application

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