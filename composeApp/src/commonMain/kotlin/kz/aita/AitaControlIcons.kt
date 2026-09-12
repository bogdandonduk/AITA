package kz.aita

import aita.composeapp.generated.resources.Res
import aita.composeapp.generated.resources._136_0
import aita.composeapp.generated.resources._136_1
import aita.composeapp.generated.resources._137_0
import aita.composeapp.generated.resources._137_1
import aita.composeapp.generated.resources._138_0
import aita.composeapp.generated.resources._138_1
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.DrawableResource

// Monochrome action glyphs. CpImage tints them for the actual control surface, not the page theme.
@Composable
internal fun AppConfiguration.cameraTorchIconPath(): String {
    val theme = normalizeAppThemePreference(stateValues.appThemeId)
    return stateValues.drawables.orEmpty().extractPath(136L, theme) ?: "svg/136_${theme}.svg"
}

internal fun AppConfiguration.cameraTorchIconFallback(): DrawableResource =
    if (normalizeAppThemePreference(stateValues.appThemeId) == 1L) Res.drawable._136_1 else Res.drawable._136_0

@Composable
internal fun AppConfiguration.supportSendIconPath(): String {
    val theme = normalizeAppThemePreference(stateValues.appThemeId)
    return stateValues.drawables.orEmpty().extractPath(137L, theme) ?: "svg/137_${theme}.svg"
}

internal fun AppConfiguration.supportSendIconFallback(): DrawableResource =
    if (normalizeAppThemePreference(stateValues.appThemeId) == 1L) Res.drawable._137_1 else Res.drawable._137_0

@Composable
internal fun AppConfiguration.stockAddIconPath(): String {
    val theme = normalizeAppThemePreference(stateValues.appThemeId)
    return stateValues.drawables.orEmpty().extractPath(138L, theme) ?: "svg/138_${theme}.svg"
}

internal fun AppConfiguration.stockAddIconFallback(): DrawableResource =
    if (normalizeAppThemePreference(stateValues.appThemeId) == 1L) Res.drawable._138_1 else Res.drawable._138_0
