package kz.aita.core

import kz.aita.app.AITA

actual fun getSystemLocaleLanguage(): String? {
  return AITA.get().resources.configuration.locales[0].language
}