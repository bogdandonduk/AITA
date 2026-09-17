@file:OptIn(kotlin.time.ExperimentalTime::class)
package kz.aita

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import kotlin.time.Instant

internal fun AppConfiguration.diagnosticsText(key: String) = eventMessage("diagnostics.ui.$key").extractLocalizedString(stateValues.appLanguage).orEmpty()
@Composable internal fun AppConfiguration.DiagnosticsSettingsPane(modifier: Modifier = Modifier) {
    val state by RuntimeDiagnostics.state.collectAsState()
    val sending by RuntimeDiagnostics.waiting.collectAsState()
    val error by RuntimeDiagnostics.storageError.collectAsState()
    val damaged by RuntimeDiagnostics.damaged.collectAsState()
    val waiting by RuntimeDiagnostics.storageWaiting.collectAsState()
    val account = stateValues.userAccount?.id
    val generation = currentAuthenticatedSessionGeneration()
    val pending = state.events.count { diagnosticCanUpload(it, account) }
    var busy by remember(account, generation) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun act(block: () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try { withContext(Dispatchers.ourIo) {
                if (userAccountState.payloadValue?.id == account && authenticatedSessionGenerationIsCurrent(generation)) block()
            } } finally { busy = false }
        }
    }
    Column(modifier.widthIn(max = 720.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CpImage(Modifier.size(32.dp), url = diagnosticsIconPath(), fallbackRes = diagnosticsIconResource(), contentDescription = null, tintColor = stateValues.AccentColor)
            Text(diagnosticsText("title"), fontWeight = FontWeight.Bold, fontSize = stateValues.accentTextSize, color = stateValues.TextColor)
        }
        Text(diagnosticsText("privacy"), color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
        Column(Modifier.fillMaxWidth().border(1.dp, stateValues.PlaceholderTextColor.copy(alpha = .25f), RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.AccentColor.copy(alpha = .04f), RoundedCornerShape(stateValues.cornerRadius)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(diagnosticsText("enabled"), Modifier.weight(1f), color = stateValues.TextColor, fontSize = stateValues.textSize)
                Switch(state.enabled, onCheckedChange = { value -> act { RuntimeDiagnostics.setEnabled(value) } },
                    enabled = !busy && !waiting && (!damaged || state.enabled))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(diagnosticsText("location"), Modifier.weight(1f), color = stateValues.TextColor, fontSize = stateValues.textSize)
                Switch(state.approximateLocation, onCheckedChange = { value -> act { RuntimeDiagnostics.setApproximateLocation(value) } },
                    enabled = state.enabled && !busy && !waiting && !damaged)
            }
            Text(diagnosticsText("location_help"), color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
        }
        Text(diagnosticsText("pending") + ": " + pending, color = stateValues.TextColor, fontSize = stateValues.textSize)
        if (sending) Text(diagnosticsText("sending"), color = stateValues.AccentColor, fontSize = stateValues.smallTextSize)
        if (waiting) Text(diagnosticsText("waiting"), color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
        if (error) Text(diagnosticsText(if (damaged) "damaged" else "storage_error"), color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize)
        if (state.nextAttemptAtMillis > getCurrentTimeMillis()) Text(diagnosticsText("retry_after") + ": " +
            Instant.fromEpochMilliseconds(state.nextAttemptAtMillis).toString(), color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
        actionButton(text = diagnosticsText("send"), autoLoading = false, confirmationRequired = false,
            enabled = state.enabled && pending > 0 && !sending && !busy && !waiting && !damaged,
            onClick = RuntimeDiagnostics::sendNow)
        actionButton(text = diagnosticsText("clear"), autoLoading = false, confirmationRequired = true,
            enabled = pending > 0 && !busy && !waiting && !damaged,
            onClick = { act { RuntimeDiagnostics.clearPending(account) } })
        if (damaged) actionButton(text = diagnosticsText("reset"), autoLoading = false, confirmationRequired = true,
            enabled = !busy && !waiting, onClick = { act { RuntimeDiagnostics.resetDamagedStorage() } })
        Text(diagnosticsText("retention"), color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
    }
}
