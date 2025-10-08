package kz.aita.core

import java.util.*

actual fun getSystemLocaleLanguage(): String? {
  return Locale.getDefault()?.language
}