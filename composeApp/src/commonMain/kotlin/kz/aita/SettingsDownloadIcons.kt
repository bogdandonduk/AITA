package kz.aita

import aita.composeapp.generated.resources.*
import org.jetbrains.compose.resources.DrawableResource

internal fun AppConfiguration.appStateIconPath() = uiAppearanceResourcesState.value.catalog.drawable(216L, stateValues.appThemeId)
internal fun AppConfiguration.downloadsIconPath() = uiAppearanceResourcesState.value.catalog.drawable(217L, stateValues.appThemeId)
internal fun AppConfiguration.appStateIconResource(): DrawableResource = if (isDarkAppTheme(stateValues.appThemeId)) Res.drawable._216_1 else Res.drawable._216_0
internal fun AppConfiguration.downloadsIconResource(): DrawableResource = if (isDarkAppTheme(stateValues.appThemeId)) Res.drawable._217_1 else Res.drawable._217_0
internal fun AppConfiguration.downloadPlatformIconResource(platform: String): DrawableResource {
    val dark = isDarkAppTheme(stateValues.appThemeId)
    return when (platform) {
        "android" -> if (dark) Res.drawable._218_1 else Res.drawable._218_0
        "windows" -> if (dark) Res.drawable._219_1 else Res.drawable._219_0
        "web" -> if (dark) Res.drawable._220_1 else Res.drawable._220_0
        "macos" -> if (dark) Res.drawable._221_1 else Res.drawable._221_0
        else -> if (dark) Res.drawable._222_1 else Res.drawable._222_0
    }
}
internal fun AppConfiguration.downloadPlatformIconPath(platform: String): String = uiAppearanceResourcesState.value.catalog.drawable(
    when (platform) { "android" -> 218L; "windows" -> 219L; "web" -> 220L; "macos" -> 221L; else -> 222L }, stateValues.appThemeId)
