package kz.aita

/** Requested capability is distinct from a language actually reported by the recognizer. */
enum class VoiceLanguageMode { AutomaticRequested, DetectionOnly, DeviceDefault }
data class VoiceRecognitionResult(val text: String, val detectedLanguage: String? = null)
data class VoiceLanguagePlan(val detect: Boolean, val switch: Boolean, val installedLanguages: List<String>?) {
    val mode: VoiceLanguageMode get() = when { switch -> VoiceLanguageMode.AutomaticRequested; detect -> VoiceLanguageMode.DetectionOnly; else -> VoiceLanguageMode.DeviceDefault }
}

fun normalizedDetectedLanguage(value: String?): String? = value?.trim()?.replace('_', '-')
    ?.takeIf { it.length in 2..48 && it.split('-').all { part -> part.isNotEmpty() && part.all { c -> c.isLetterOrDigit() && c.code < 128 } } && !it.equals("und", ignoreCase = true) }

/** Null = provider did not report its model inventory, not an empty inventory. No app-language allow-list. */
fun voiceLanguagePlan(apiLevel: Int, automatic: Boolean, installed: List<String>?): VoiceLanguagePlan {
    if (apiLevel < 34 || !automatic) return VoiceLanguagePlan(false, false, null)
    val languages = installed?.mapNotNull(::normalizedDetectedLanguage)?.distinctBy { it.lowercase() }
    return VoiceLanguagePlan(true, languages == null || languages.size > 1, languages)
}
