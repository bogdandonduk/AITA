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
    modifier: Modifier = Modifier,
    status: InventoryLoadStatus,
    emptyText: String? = null,
    compact: Boolean = false
) {
    val transport by cloudTransportStatusState.collectAsState()
    val refreshing by cloudConnectionManualRefreshInProgressState.collectAsState()
    val evidence by connectionProbeEvidenceState.collectAsState()
    val diagnosis by connectionDiagnosticResultState.collectAsState()
    val diagnosing by connectionDiagnosticRunningState.collectAsState()
    val offline = transport == CLOUD_TRANSPORT_STATUS_UNAVAILABLE
    val kind = inventoryFeedbackKind(status, offline, compact || status.source != InventoryLoadSource.None, emptyText == null)
    fun message(key: String) = eventMessage(key).extractLocalizedString(stateValues.appLanguage).orEmpty()
    val text = when (kind) {
        InventoryFeedbackKind.CacheWriteFailure -> inventoryCacheWriteFailureMessage().extractLocalizedString(stateValues.appLanguage).orEmpty()
        InventoryFeedbackKind.SavedOffline -> listOfNotNull(
            inventoryCachedWhileOfflineMessage().extractLocalizedString(stateValues.appLanguage),
            emptyText?.takeIf { !compact }
        ).joinToString("\n")
        InventoryFeedbackKind.NoCacheOffline -> message("inventory.no_offline_copy")
        else -> status.failure?.extractLocalizedString(stateValues.appLanguage) ?: emptyText ?: localizedStringResource(1141, "Please wait…")
    }
    Column(modifier = modifier.padding(if (compact) 8.dp else 16.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Column(modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (kind == InventoryFeedbackKind.Loading) {
                LoadingSkeleton(Modifier.fillMaxWidth(), layout = if (compact) LoadingLayout.InlineValue else LoadingLayout.StockCard, rows = if (compact) 1 else 4, compact = compact)
                return@Column
            }
            Text(text = text, color = when (kind) {
                InventoryFeedbackKind.Denied, InventoryFeedbackKind.CacheWriteFailure -> stateValues.ErrorColor
                InventoryFeedbackKind.Failure -> stateValues.ErrorColor
                else -> stateValues.PlaceholderTextColor
            }, fontSize = stateValues.smallTextSize, textAlign = TextAlign.Center)
            if (!status.accessDenied && (offline || status.failure != null)) {
                evidence?.takeIf { it.failure != ConnectionFailureKind.None && it.host == configuredConnectionHost() }?.let {
                    Text(text = message(it.failure.messageKey) + (it.httpStatus?.let { code -> " • HTTP $code" } ?: ""),
                        color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize,
                        textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
                }
                diagnosis?.takeIf { it.host == configuredConnectionHost() }?.let {
                    Text(text = message(it.messageKey) + "\n" +
                        message("connection.probe_codes") + ": ${it.edgeHttpStatus ?: "—"} / ${it.originHttpStatus ?: "—"}",
                        color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize,
                        textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
                }
            }
            if (!status.loading || offline) {
                Spacer(Modifier.height(8.dp))
                actionButton(modifier = Modifier.fillMaxWidth(), text = localizedStringResource(237, "Refresh"),
                    enabled = !refreshing, autoLoading = false, confirmationRequired = false, onClick = {
                        if (offline) refreshCloudConnectionManually()
                        stateValues.activeStoreId?.let { getStock(it); getStockBatches(it) }
                    })
            }
            if (!status.accessDenied && (offline || status.failure != null)) {
                Spacer(Modifier.height(6.dp))
                actionButton(modifier = Modifier.fillMaxWidth(), text = message(if (diagnosing) "connection.diagnosing" else "connection.diagnose"),
                    enabled = !diagnosing, autoLoading = false, confirmationRequired = false, onClick = { diagnoseAitaConnection() })
            }
        }
    }
}
