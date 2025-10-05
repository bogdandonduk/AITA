package kz.aita.core

import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.languageCode
import platform.Foundation.preferredLanguages

actual fun getSystemLocaleLanguage(): String? {
  return (NSLocale.preferredLanguages.firstOrNull() as? String)
    ?: NSLocale.currentLocale.languageCode
}