package kz.aita.core

import java.util.Locale

actual fun getSystemLocaleLanguage(): String? {
  return Locale.getDefault()?.language
}