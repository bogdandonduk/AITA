package kz.aita

import androidx.compose.foundation.layout.*
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
        actionButton(modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp), text = store.name, subText = pass27Text("open_store"),
            iconPath = downloadPlatformIconPath(platform), iconRes = downloadPlatformIconResource(platform),
            enabledColor = stateValues.TextColor, textColor = stateValues.BackgroundColor,
            autoLoading = false, confirmationRequired = false, onClick = {
                scope.launch {
                    try { failed = openExternalUrlPlatformAction?.invoke(store.url)?.success != true }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { failed = true }
                }
            })
        if (failed) Text(updateText("error.unavailable"), color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize)
    }
}
