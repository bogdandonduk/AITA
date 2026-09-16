package kz.aita

/** All supported variants are indexed together. Selecting a variant never schedules a projection. */
class AppearanceCatalog private constructor(
    private val strings: Map<String, Map<Long, String>>,
    private val dimensions: Map<Long, Map<Long, Float>>,
    private val colors: Map<Long, Map<Long, String>>,
    private val drawables: Map<Long, Map<Long, String>>
) {
    fun string(id: Long, language: String): String? {
        val selected = effectiveAppLanguage(language)
        return strings[selected]?.get(id) ?: bundledTranslatedStringResource(id, selected)
    }
    fun dimension(id: Long, mode: Long, fallback: Float): Float = dimensions[normalizeAppSizeModePreference(mode)]?.get(id) ?: fallback
    fun color(id: Long, theme: Long): String? = colors[normalizeAppThemePreference(theme)]?.get(id)
    fun drawable(id: Long, theme: Long): String = drawables[appDrawableThemeId(theme)]?.get(id)
        ?: "svg/${id}_${appDrawableThemeId(theme)}.svg"

    companion object {
        fun build(
            strings: List<LocalizedStringGroupDataModel> = emptyList(),
            bundledStrings: List<LocalizedStringGroupDataModel> = emptyList(),
            dimensions: List<StylizedDimensionGroupDataModel> = emptyList(),
            bundledDimensions: List<StylizedDimensionGroupDataModel> = emptyList(),
            colors: List<StylizedColorGroupDataModel> = emptyList(),
            bundledColors: List<StylizedColorGroupDataModel> = emptyList(),
            drawables: List<StylizedDrawablePathsGroupDataModel> = emptyList(),
            bundledDrawables: List<StylizedDrawablePathsGroupDataModel> = emptyList()
        ): AppearanceCatalog {
            // Keep the first duplicate just like extractString/extractValue; don't reverse precedence.
            fun <T> firstById(items: List<T>, id: (T) -> Long): Map<Long, List<T>> = buildMap {
                items.forEach { item -> val key = id(item); if (key !in this) put(key, listOf(item)) }
            }
            val primaryStrings = firstById(strings) { it.id }
            val fallbackStrings = firstById(bundledStrings) { it.id }
            val stringIds = primaryStrings.keys + fallbackStrings.keys
            val indexedStrings = SUPPORTED_APP_LANGUAGES.associateWith { language ->
                stringIds.mapNotNull { id ->
                    resolveLocalizedResource(id, language, primaryStrings[id]?.firstOrNull()?.values,
                        fallbackStrings[id]?.firstOrNull()?.values)?.let { id to it }
                }.toMap()
            }
            val primaryDimensions = firstById(dimensions) { it.id }
            val fallbackDimensions = firstById(bundledDimensions) { it.id }
            val indexedDimensions = listOf(0L, 1L).associateWith { mode ->
                (primaryDimensions.keys + fallbackDimensions.keys + (0L..13L)).mapNotNull { id ->
                    (primaryDimensions[id]?.extractValue(id, mode) ?: fallbackDimensions[id]?.extractValue(id, mode)
                        ?: emptyList<StylizedDimensionGroupDataModel>().extractValue(id, mode))?.let { id to it }
                }.toMap()
            }
            val primaryColors = firstById(colors) { it.id }
            val fallbackColors = firstById(bundledColors) { it.id }
            val indexedColors = SUPPORTED_APP_THEME_IDS.associateWith { theme ->
                (primaryColors.keys + fallbackColors.keys).mapNotNull { id ->
                    (primaryColors[id]?.extractColor(id, theme) ?: fallbackColors[id]?.extractColor(id, theme))?.let { id to it }
                }.toMap()
            }
            val primaryDrawables = firstById(drawables) { it.id }
            val fallbackDrawables = firstById(bundledDrawables) { it.id }
            val indexedDrawables = listOf(0L, 1L).associateWith { theme ->
                (primaryDrawables.keys + fallbackDrawables.keys).mapNotNull { id ->
                    (primaryDrawables[id]?.extractPath(id, theme) ?: fallbackDrawables[id]?.extractPath(id, theme))?.let { id to it }
                }.toMap()
            }
            return AppearanceCatalog(indexedStrings, indexedDimensions, indexedColors, indexedDrawables)
        }
    }
}
