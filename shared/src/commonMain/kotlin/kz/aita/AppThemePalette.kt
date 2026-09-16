package kz.aita

const val APP_THEME_PURPLE = 2L
const val APP_THEME_BLUE = 3L
val SUPPORTED_APP_THEME_IDS: List<Long> = listOf(0L, 1L, APP_THEME_PURPLE, APP_THEME_BLUE)
fun isDarkAppTheme(theme: Long?): Boolean = normalizeAppThemePreference(theme) != 0L
fun appDrawableThemeId(theme: Long?): Long = if (isDarkAppTheme(theme)) 1L else 0L

/** Tinted surfaces, not a replacement accent; explicit remotely supplied variants still win. */
fun tintedAppThemeColor(id: Long, theme: Long): String? = when (normalizeAppThemePreference(theme)) {
    APP_THEME_PURPLE -> when (id) {
        1L -> "ff241c36"
        2L -> "ffffffff"
        4L -> "b3ded4ee"
        7L -> "ccffffff"
        else -> null
    }
    APP_THEME_BLUE -> when (id) {
        1L -> "ff17212b"
        2L -> "ffffffff"
        4L -> "b3d6e3ef"
        7L -> "ccffffff"
        else -> null
    }
    else -> null
}

fun availableAppThemes(configured: List<AppThemeDataModel>): List<AppThemeDataModel> {
    fun theme(id: Long, vararg names: String) = AppThemeDataModel(id,
        listOf("en", "ru", "kk", "ky", "tg", "uz").zip(names.toList()).map { LocalizedStringDataModel(it.first,it.second) })
    val defaults = listOf(
        theme(0L,"Light","Светлая","Жарық","Жарык","Равшан","Yorug‘"),
        theme(1L,"Dark","Тёмная","Қараңғы","Караңгы","Торик","Qorong‘i"),
        theme(APP_THEME_PURPLE,"Purple","Фиолетовая","Күлгін","Кызгылт көк","Бунафш","Binafsha"),
        theme(APP_THEME_BLUE,"Blue","Синяя","Көк","Көк","Кабуд","Ko‘k")
    )
    return defaults.map { fallback -> configured.firstOrNull { it.id == fallback.id } ?: fallback }
}
