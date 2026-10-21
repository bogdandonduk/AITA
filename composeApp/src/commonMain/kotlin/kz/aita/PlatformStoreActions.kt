package kz.aita

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import aita.composeapp.generated.resources.*
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import kotlinx.coroutines.CoroutineStart
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

internal data class PlatformStoreLink(val name: String, val url: String)

// These are the real storefronts, not invented AITA product IDs. Replace with verified
// product pages only after the corresponding store publication has been completed.
internal fun platformStoreLink(platform: String): PlatformStoreLink? = when (platform) {
    "android" -> PlatformStoreLink("Google Play", "https://play.google.com/store/apps")
    "windows" -> PlatformStoreLink("Microsoft Store", "https://apps.microsoft.com/")
    "macos", "ios" -> PlatformStoreLink("App Store", "https://apps.apple.com/")
    "linux" -> PlatformStoreLink("Flathub", "https://flathub.org/")
    else -> null
}

@Composable
internal fun AppConfiguration.AuthDownloadsAction(onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.widthIn(max = 680.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf("android", "windows", "linux", "macos", "ios", "web").forEach { platform ->
                    CpImage(Modifier.size(21.dp), url = downloadPlatformIconPath(platform),
                        fallbackRes = downloadPlatformIconResource(platform), contentDescription = platform,
                        tintColor = stateValues.AccentColor)
                }
            }
            Text(downloadsText("auth_link"), color = stateValues.AccentColor, fontSize = stateValues.smallTextSize,
                fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        }
    }
}

@Composable
internal fun AppConfiguration.PlatformStoreAction(platform: String, modifier: Modifier = Modifier) {
    val store = platformStoreLink(platform) ?: return
    val scope = rememberCoroutineScope()
    var failed by remember(platform) { mutableStateOf(false) }
    Column(modifier) {
        Image(painterResource(platformStoreBadge(platform, stateValues.appLanguage)),
            contentDescription = "${pass27Text("open_store")} · ${store.name}", contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().height(64.dp).clickable(role = Role.Button) {
                failed = false
                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    try { failed = openExternalUrlPlatformAction?.invoke(store.url)?.success != true }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { failed = true }
                }
            })
        if (failed) Text(updateText("error.unavailable"), color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize)
    }
}

/** Official artwork is bundled, never tinted or downloaded during an app launch. Missing official
 * translations use the official English badge; surrounding explanation remains localized. */
internal fun platformStoreBadge(platform: String, language: String): DrawableResource {
    val locale=if(canonicalLanguageCode(language)=="system") effectiveAppLanguage(language) else canonicalLanguageCode(language)
    return when (platform) {
    "android" -> when (locale) {
        "ru" -> Res.drawable.badge_google_play_ru
        "kk" -> Res.drawable.badge_google_play_kk
        "ky" -> Res.drawable.badge_google_play_ky
        "uz" -> Res.drawable.badge_google_play_uz
        else -> Res.drawable.badge_google_play_en
    }
    "windows" -> when (locale) {
        "ru" -> Res.drawable.badge_microsoft_store_ru
        "kk" -> Res.drawable.badge_microsoft_store_kk
        "uz" -> Res.drawable.badge_microsoft_store_uz
        else -> Res.drawable.badge_microsoft_store_en
    }
    "linux" -> if (locale == "ru") Res.drawable.badge_flathub_ru else Res.drawable.badge_flathub_en
    "macos" -> if (locale == "ru") Res.drawable.badge_mac_app_store_ru else Res.drawable.badge_mac_app_store_en
    else -> if (locale == "ru") Res.drawable.badge_app_store_ru else Res.drawable.badge_app_store_en
}

}
