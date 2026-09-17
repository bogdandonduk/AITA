package kz.aita

import aita.composeapp.generated.resources.*
import org.jetbrains.compose.resources.DrawableResource

internal fun AppConfiguration.diagnosticsIconPath(): String =
    uiAppearanceResourcesState.value.catalog.drawable(213L, stateValues.appThemeId)
internal fun AppConfiguration.diagnosticsIconResource(): DrawableResource =
    if (isDarkAppTheme(stateValues.appThemeId)) Res.drawable._213_1 else Res.drawable._213_0
internal fun AppConfiguration.usbReceiptIconPath(): String =
    uiAppearanceResourcesState.value.catalog.drawable(214L, stateValues.appThemeId)
internal fun AppConfiguration.usbReceiptIconResource(): DrawableResource =
    if (isDarkAppTheme(stateValues.appThemeId)) Res.drawable._214_1 else Res.drawable._214_0
