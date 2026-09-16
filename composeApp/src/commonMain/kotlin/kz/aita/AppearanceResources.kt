package kz.aita

import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow

internal fun parseAppearanceColor(value: String): Color {
    val hex = value.trim().removePrefix("#").removePrefix("0x").removePrefix("0X")
    require(hex.length == 6 || hex.length == 8) { "Expected RGB or ARGB color" }
    return (if (hex.length == 6) "ff$hex" else hex).toColor()
}

/** No chosen locale/theme/scale is captured in this object, so delayed resource loads cannot replay one. */
internal class UiAppearanceResources(val catalog: AppearanceCatalog) {
    private val palettes = SUPPORTED_APP_THEME_IDS.associateWith { theme ->
        val dark = isDarkAppTheme(theme)
        val defaults = listOf("#ffffba24", if (dark) "#ff111111" else "#ffffffff",
            if (dark) "#ffffffff" else "#ff000000", "#ffffffff",
            if (dark) "#aaffffff" else "#aa000000", "#ffa7a7a7", "#ffff0000",
            if (dark) "#ffffffff" else "#ff000000", "#ff6bb522", "#ffffa500")
        defaults.mapIndexed { id, default ->
            val fallback=tintedAppThemeColor(id.toLong(),theme) ?: default
            runCatching {parseAppearanceColor(catalog.color(id.toLong(),theme) ?: fallback)}.getOrElse {parseAppearanceColor(fallback)}
        }
    }
    fun color(id: Int, theme: Long): Color = palettes.getValue(normalizeAppThemePreference(theme))[id]
}

internal val uiAppearanceResourcesState = MutableStateFlow(UiAppearanceResources(AppearanceCatalog.build(
    bundledStrings = bundledLocalizedStringFallbacks.map { (id, values) ->
        LocalizedStringGroupDataModel(id, values.map { LocalizedStringDataModel(it.key, it.value) })
    }
)))
