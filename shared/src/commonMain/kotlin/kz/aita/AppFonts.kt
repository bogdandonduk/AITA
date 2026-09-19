package kz.aita

const val DEFAULT_APP_FONT_ID = "noto_sans"
const val KEY_APP_FONT = "app_font"
data class AppFontChoice(val id: String, val name: String)
val APP_FONTS = listOf(
    AppFontChoice("noto_sans", "Noto Sans"),
    AppFontChoice("fira_sans", "Fira Sans"),
    AppFontChoice("fira_condensed", "Fira Sans Condensed"),
    AppFontChoice("lato", "Lato"),
    AppFontChoice("ubuntu", "Ubuntu"),
    AppFontChoice("pt_sans", "PT Sans"),
    AppFontChoice("pt_serif", "PT Serif"),
    AppFontChoice("plex_serif", "IBM Plex Serif"),
    AppFontChoice("plex_mono", "IBM Plex Mono")
)
fun normalizeAppFontPreference(value: String?): String = value?.takeIf { id -> APP_FONTS.any { it.id == id } } ?: DEFAULT_APP_FONT_ID
fun setAppFont(fontId: String, syncServer: Boolean = true) = AppPreferences.select(font = fontId, sync = syncServer)
fun setAuthScreenAppFont(fontId: String) {
    val normalized = normalizeAppFontPreference(fontId)
    authScreenPreferenceOverrideState.value = authScreenPreferenceOverrideState.value.copy(appFontId = normalized, fontTouched = true)
    setAppFont(normalized, syncServer = false)
}
