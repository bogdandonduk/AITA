package kz.aita

import aita.composeapp.generated.resources.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.Font
import kotlinx.coroutines.CancellationException

internal val LocalAitaFontFamily = staticCompositionLocalOf<FontFamily> { FontFamily.Default }

@Composable internal fun aitaFontFamily(id: String): FontFamily {
    val resources = when (normalizeAppFontPreference(id)) {
        "fira_sans" -> Res.font.fira_sans_regular to Res.font.fira_sans_bold
        "fira_condensed" -> Res.font.fira_condensed_regular to Res.font.fira_condensed_bold
        "lato" -> Res.font.lato_regular to Res.font.lato_bold
        "ubuntu" -> Res.font.ubuntu_regular to Res.font.ubuntu_bold
        "pt_sans" -> Res.font.pt_sans_regular to Res.font.pt_sans_bold
        "pt_serif" -> Res.font.pt_serif_regular to Res.font.pt_serif_bold
        "plex_serif" -> Res.font.plex_serif_regular to Res.font.plex_serif_bold
        "plex_mono" -> Res.font.plex_mono_regular to Res.font.plex_mono_bold
        else -> Res.font.noto_sans_regular to Res.font.noto_sans_bold
    }
    val regular = Font(resources.first)
    val bold = Font(resources.second, FontWeight.Bold)
    return remember(regular, bold) { FontFamily(regular, bold) }
}

/** One typography on every client. Preference changes do not recreate navigation or drafts. */
@Composable internal fun AitaTypography(content: @Composable () -> Unit) {
    val preferences by appAppearancePreferencesState.collectAsState()
    val family = aitaFontFamily(normalizeAppFontPreference(preferences.appFontId))
    val fallback = aitaFontFamily(DEFAULT_APP_FONT_ID)
    val resolver = LocalFontFamilyResolver.current
    LaunchedEffect(family, resolver) {
        try { resolver.preload(fallback); resolver.preload(family) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* A cached/default face keeps the app usable while offline. */ }
    }
    val base = remember { Typography() }
    val typography = remember(family) { base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = family),
        displayMedium = base.displayMedium.copy(fontFamily = family),
        displaySmall = base.displaySmall.copy(fontFamily = family),
        headlineLarge = base.headlineLarge.copy(fontFamily = family),
        headlineMedium = base.headlineMedium.copy(fontFamily = family),
        headlineSmall = base.headlineSmall.copy(fontFamily = family),
        titleLarge = base.titleLarge.copy(fontFamily = family),
        titleMedium = base.titleMedium.copy(fontFamily = family),
        titleSmall = base.titleSmall.copy(fontFamily = family),
        bodyLarge = base.bodyLarge.copy(fontFamily = family),
        bodyMedium = base.bodyMedium.copy(fontFamily = family),
        bodySmall = base.bodySmall.copy(fontFamily = family),
        labelLarge = base.labelLarge.copy(fontFamily = family),
        labelMedium = base.labelMedium.copy(fontFamily = family),
        labelSmall = base.labelSmall.copy(fontFamily = family)
    ) }
    CompositionLocalProvider(LocalAitaFontFamily provides family) {
        MaterialTheme(typography = typography, content = content)
    }
}
