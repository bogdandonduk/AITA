package kz.aita

import java.util.*

actual fun getSystemLocaleLanguage(): String? {
  return Locale.getDefault()?.language
}