package kz.aita

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*

@Composable
internal fun AppConfiguration.PrintSelectionButton(report: Boolean = false, a4Only: Boolean = false, label: Boolean = false, enabled: Boolean = true) {
    var open by remember { mutableStateOf(false) }
    actionButton(modifier = Modifier.size(44.dp), text = "",
        iconPath = if (open) stateValues.drawablePathIconExpandLess else stateValues.drawablePathIconExpandMore,
        iconRes = if (open) stateValues.drawableResIconExpandLess.value else stateValues.drawableResIconExpandMore.value,
        iconContentDescription = pass27Text("print_destination"), enabled = enabled,
        autoLoading = false, confirmationRequired = false, onClick = { open = !open })
    if (open) PrinterSelectionSheet(report = report, a4Only = a4Only, label = label, onDismiss = { open = false })
}

@Composable
internal fun AppConfiguration.PrinterSelectionSheet(report: Boolean, a4Only: Boolean = false, label: Boolean = false, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var printers by remember { mutableStateOf<List<PlatformReceiptPrinterDataModel>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val onReceipt by reportPrintOnReceiptState.collectAsState()
    val paper by receiptPaperWidthMmState.collectAsState()
    val a4Name by systemA4PrinterNameState.collectAsState()
    val receiptName by systemReceiptPrinterNameState.collectAsState()
    val native = listSystemDocumentPrintersAction != null
    val thermal = !label && !a4Only && (!report || onReceipt)
    fun refresh() {
        if (busy) return
        busy = true; error = ""
        scope.launch {
            try { printers = withTimeout(20_000) { listSystemDocumentPrintersAction?.invoke().orEmpty() } }
            catch (cancelled: CancellationException) { if (cancelled !is TimeoutCancellationException) throw cancelled; error = deviceWorkflowText("driver_print_failed") }
            catch (_: Exception) { error = deviceWorkflowText("driver_print_failed") }
            finally { busy = false }
        }
    }
    LaunchedEffect(Unit) {
        loadReceiptPaperWidth()
        if (report) loadReportPrintDestination()
        if (label) refreshLabelPrinterDevices() else if (native) refresh()
    }
    AitaBottomSheet(title = pass27Text("print_destination"), onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (report && !a4Only) AitaDropdownField(title = pass27Text("paper"), selectedId = if (onReceipt) "receipt" else "a4",
                options = listOf(DropdownOption("a4", "A4"), DropdownOption("receipt", pass27Text("receipt_roll"))),
                placeholder = "A4", onSelected = { value -> scope.launch { saveReportPrintDestination(value == "receipt") } })
            if (thermal) AitaDropdownField(title = pass27Text("paper"), selectedId = paper.toString(),
                options = listOf(DropdownOption("58", "58 mm"), DropdownOption("80", "80 mm")), placeholder = "80 mm",
                onSelected = { value -> scope.launch { saveReceiptPaperWidth(value.toInt()) } })
            if (label) {
                val devices by labelPrinterDevicesState.collectAsState()
                val selected by configuredLabelPrinterDeviceIdState.collectAsState()
                if (preferHtmlDocumentPrinting) Text(pass27Text("browser_printer"), color = stateValues.TextColor, fontSize = stateValues.smallTextSize)
                else {
                    AitaDropdownField(title = pass27Text("choose_printer"), selectedId = selected,
                        options = devices.map { DropdownOption(it.id, it.name) }, placeholder = pass27Text("choose_printer"),
                        enabled = !busy, onSelected = { id ->
                            busy = true
                            configureLabelPrinterDevice(id) { result -> busy = false; error = if (result.success) "" else result.message }
                        })
                    actionButton(text = localizedStringResource(1259, "Refresh printers"), loading = busy, enabled = !busy,
                        autoLoading = false, confirmationRequired = false, onClick = {
                            busy = true
                            refreshLabelPrinterDevices { result -> busy = false; error = if (result.success) "" else result.message }
                        })
                }
            } else if (native) {
                AitaDropdownField(title = pass27Text("print_destination"), selectedId = if (thermal) receiptName else a4Name,
                    options = printers.map { DropdownOption(it.id, it.name) }, placeholder = pass27Text("choose_printer"),
                    enabled = !busy, onSelected = { name ->
                        busy = true; error = ""
                        scope.launch {
                            try {
                                val result = selectSystemDocumentPrinterAction?.invoke(!thermal, name)
                                if (result?.success != true) error = result?.message ?: deviceWorkflowText("driver_print_failed")
                                else if (thermal) configureReceiptPrinterDevice(SYSTEM_DOCUMENT_PRINTER_ID) { saved ->
                                    if (!saved.success) error = saved.message
                                }
                            } catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { error = deviceWorkflowText("driver_print_failed") }
                            finally { busy = false }
                        }
                    })
                actionButton(text = localizedStringResource(1259, "Refresh printers"), iconPath = stateValues.drawablePathIconRefresh,
                    loading = busy, enabled = !busy, autoLoading = false, confirmationRequired = false, onClick = ::refresh)
            } else {
                if (preferHtmlDocumentPrinting) Text(pass27Text("browser_printer"), color = stateValues.TextColor, fontSize = stateValues.smallTextSize)
                // Native Android/Bluetooth and raw connections retain their existing selection.
                if (!preferHtmlDocumentPrinting && thermal) {
                    val devices by receiptPrinterDevicesState.collectAsState()
                    val selected by configuredReceiptPrinterDeviceIdState.collectAsState()
                    AitaDropdownField(title = pass27Text("choose_printer"), selectedId = selected,
                        options = devices.map { DropdownOption(it.id, it.name) }, placeholder = pass27Text("choose_printer"),
                        onSelected = { id -> configureReceiptPrinterDevice(id) { if (!it.success) error = it.message } })
                    actionButton(text = localizedStringResource(1259, "Refresh printers"), loading = busy, autoLoading = false,
                        confirmationRequired = false, enabled = !busy, onClick = {
                            busy = true
                            refreshReceiptPrinterDevices(requestPermission = true) { result -> busy = false; error = if (result.success) "" else result.message }
                        })
                }
            }
            if (!label && !thermal && !native) actionButton(text = pass27Text("print_test_a4"),
                iconPath = stateValues.drawablePathIconReceipt, enabled = !busy, loading = busy,
                autoLoading = false, confirmationRequired = false, onClick = {
                    busy = true; error = ""
                    scope.launch {
                        try {
                            val document = AitaPdfDocument(width = 595f, minHeight = 842f, maxHeight = 842f,
                                blocks = listOf(AitaPdfBlock("AITA", AitaPdfRole.Title), AitaPdfBlock(pass27Text("print_test_a4"))))
                            val result = if (preferHtmlDocumentPrinting) printHtmlDocument("AITA A4", document.toPrintHtml("AITA A4"))
                                else printPdfDocument("AITA A4.pdf", withContext(Dispatchers.Default) { renderAitaPdfDocument(document) })
                            if (!result.success) error = result.message
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { error = deviceWorkflowText("driver_print_failed") }
                        finally { busy = false }
                    }
                })
            if (error.isNotBlank()) Text(error, color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize)
        }
    }
}

@Composable
internal fun AppConfiguration.ReportPrintAction(busy: Boolean, printing: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        actionButton(modifier = Modifier.weight(1f), text = localizedStringResource(1245, "Print report"),
            iconPath = stateValues.drawablePathIconReceipt, confirmationRequired = false, autoLoading = false,
            enabled = !busy, loading = printing, onClick = onClick)
        PrintSelectionButton(report = true, enabled = !busy)
    }
}
