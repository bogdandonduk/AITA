package kz.aita

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.*

@Composable internal fun AppConfiguration.BrowserPrinterSetupDialog(onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(browserReceiptPrinterNameState.value.orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Dialog(onDismissRequest = { if (!busy) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        TransactionBarcodeModalGuard()
        Column(Modifier.fillMaxWidth().aitaWidthCap(520.dp).imePadding().padding(12.dp)
            .heightIn(max = 560.dp).aitaDialogEntrance().clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.BackgroundColor)
            .border(stateValues.unfocusedBorderWidth, stateValues.PlaceholderTextColor.copy(alpha = .25f), RoundedCornerShape(stateValues.cornerRadius))
            .verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(deviceWorkflowText("test_confirm"), color = stateValues.TextColor, fontSize = stateValues.accentTextSize, fontWeight = FontWeight.Bold)
            Text(deviceWorkflowText("saved_setup_help"), color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
            aitaFormTextField(Modifier.fillMaxWidth(), name, { name = it.filter { c -> c >= ' ' && c != '\u007f' }.take(80) },
                deviceWorkflowText("printer_name"), identityKey = "browser-printer-confirmation", enabled = !busy,
                parentOwnsValue = true, sensitive = true)
            Text("${receiptPaperWidthMmState.value} mm", color = stateValues.TextColor, fontSize = stateValues.textSize)
            if (failed) Text(deviceWorkflowText("paper_save_failed"), color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize)
            actionButton(text = deviceWorkflowText("confirm_save"), autoLoading = false, confirmationRequired = false,
                enabled = !busy && name.isNotBlank(), onClick = {
                    busy = true
                    scope.launch {
                        try { withContext(Dispatchers.ourIo) { saveBrowserReceiptPrinterName(name) }; onDismiss() }
                        catch (cancel: CancellationException) { throw cancel }
                        catch (_: Exception) { failed = true }
                        finally { busy = false }
                    }
                })
            actionButton(text = deviceWorkflowText("test_not_printed"), autoLoading = false, confirmationRequired = false,
                enabled = !busy, onClick = onDismiss)
        }
    }
}
