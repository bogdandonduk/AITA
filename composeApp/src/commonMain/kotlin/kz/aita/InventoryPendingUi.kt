package kz.aita

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
internal fun AppConfiguration.InventoryPendingFeedback() {
    val entries by inventoryCreatePendingState.collectAsState()
    val pending = entries.filter { it.storeId == stateValues.activeStoreId }
    if (pending.isEmpty()) return
    var busy by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("${stockEditingMessage("pending").extractLocalizedString(stateValues.appLanguage).orEmpty()}: ${pending.size}",
            color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
        val failure = pending.firstNotNullOfOrNull { it.failure?.extractLocalizedString(stateValues.appLanguage) }
        if (!failure.isNullOrBlank()) Text(failure, color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize)
        TextButton(enabled = !busy, onClick = {
            busy = true
            coroutineScope.launch { try { retryPendingInventoryCreates() } finally { busy = false } }
        }) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(14.dp), color = stateValues.AccentColor, strokeWidth = 2.dp)
                Spacer(Modifier.width(6.dp))
            }
            Text(stockEditingMessage("retry").extractLocalizedString(stateValues.appLanguage).orEmpty(), color = stateValues.AccentColor)
        }
    }
}
