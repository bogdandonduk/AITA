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
        18 -> if(dark) Res.drawable._18_1 else Res.drawable._18_0
        140 -> if(dark) Res.drawable._140_1 else Res.drawable._140_0
        141 -> if(dark) Res.drawable._141_1 else Res.drawable._141_0
        142 -> if(dark) Res.drawable._142_1 else Res.drawable._142_0
        143 -> if(dark) Res.drawable._143_1 else Res.drawable._143_0
        148 -> if(dark) Res.drawable._148_1 else Res.drawable._148_0
        else -> if(dark) Res.drawable._139_1 else Res.drawable._139_0
    }
}
