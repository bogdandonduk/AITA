package kz.aita

import aita.composeapp.generated.resources.*
import org.jetbrains.compose.resources.DrawableResource

internal fun AppConfiguration.marketIconPath(id: Int): String {
    val theme = normalizeAppThemePreference(stateValues.appThemeId)
    return stateValues.drawables.orEmpty().extractPath(id.toLong(), theme) ?: "svg/${id}_${theme}.svg"
}
internal fun AppConfiguration.marketIconFallback(id: Int): DrawableResource {
    val dark = normalizeAppThemePreference(stateValues.appThemeId) == 1L
    return when(id) {
        140 -> if(dark) Res.drawable._140_1 else Res.drawable._140_0
        141 -> if(dark) Res.drawable._141_1 else Res.drawable._141_0
        142 -> if(dark) Res.drawable._142_1 else Res.drawable._142_0
        else -> if(dark) Res.drawable._139_1 else Res.drawable._139_0
    }
}
