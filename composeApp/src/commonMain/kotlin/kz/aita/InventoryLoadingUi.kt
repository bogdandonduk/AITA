package kz.aita

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
internal fun AppConfiguration.InventoryLoadFeedback(
    modifier: Modifier = Modifier, status: InventoryLoadStatus, emptyText: String? = null, compact: Boolean = false
) {
    val transport by cloudTransportStatusState.collectAsState()
    val kind = inventoryFeedbackKind(status, transport == CLOUD_TRANSPORT_STATUS_UNAVAILABLE,
        compact || status.source != InventoryLoadSource.None, emptyText == null)
    // The banner explains connectivity. This area must still distinguish an empty saved list,
    // a never-downloaded store and a damaged/unreadable local snapshot.
    if (kind == InventoryFeedbackKind.SavedOffline && emptyText == null) return
    Column(modifier.padding(if (compact) 8.dp else 16.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        if (kind == InventoryFeedbackKind.Loading) {
            LoadingSkeleton(Modifier.fillMaxWidth(), layout = if (compact) LoadingLayout.InlineValue else LoadingLayout.StockCard,
                rows = if (compact) 1 else 4, compact = compact)
        } else {
            val text = when (kind) {
                InventoryFeedbackKind.CacheWriteFailure -> inventoryCacheWriteFailureMessage().extractLocalizedString(stateValues.appLanguage).orEmpty()
                InventoryFeedbackKind.CacheReadFailure -> eventMessage("inventory.cache_unreadable").extractLocalizedString(stateValues.appLanguage).orEmpty()
                InventoryFeedbackKind.NoCacheOffline -> eventMessage("inventory.no_offline_copy").extractLocalizedString(stateValues.appLanguage).orEmpty()
                InventoryFeedbackKind.SavedOffline -> emptyText.orEmpty()
                else -> status.failure?.extractLocalizedString(stateValues.appLanguage) ?: emptyText.orEmpty()
            }
            if (text.isNotBlank()) Text(text, color = if (kind == InventoryFeedbackKind.Denied || kind == InventoryFeedbackKind.CacheWriteFailure || kind == InventoryFeedbackKind.CacheReadFailure || kind == InventoryFeedbackKind.Failure)
                stateValues.ErrorColor else stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize, textAlign = TextAlign.Center)
        }
    }
}
