package kz.aita

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.serialization.encodeToString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import kotlinx.coroutines.launch

internal data class SupplierPickingRow(val line: SupplierOrderLineDataModel, val quantity: QuantityDataModel?, val signature: String) {
    val key get() = "${line.orderId}:${line.id}"
    val names get() = if (line.substituteGoodsItemId.isNullOrBlank()) line.goodsItemNameSnapshot else line.substituteGoodsItemNameSnapshot
    val barcodes get() = if (line.substituteGoodsItemId.isNullOrBlank()) line.goodsItemBarcodeSnapshots else line.substituteGoodsItemBarcodeSnapshots
}

internal fun supplierPickingRows(bundles: List<SupplierOrderWithLinesDataModel>): List<SupplierPickingRow> = bundles
    .filter { it.order.isActive && !it.order.status.isSupplierOrderClosed() }
    .flatMap { bundle -> bundle.lines.filter { it.isActive && it.id.isNotBlank() && it.orderId == bundle.order.id }.map { line ->
        val q = supplierPickingLine(line).quantity
        // Prices/notes/status refreshes do not erase a physical check. Product, substitute and agreed quantity do.
        val signature = jsonBase.encodeToString(listOf(line.goodsItemId, line.substituteGoodsItemId.orEmpty(),
            q?.id.orEmpty(), q?.total.toString(), q?.pricedAmount.toString(), q?.roundTotal.toString()))
        SupplierPickingRow(line, q, signature)
    } }.distinctBy { it.key }

internal fun decodePickingMarks(raw: String?): Map<String, String> = raw?.takeIf { it.length <= 32768 }
    ?.let { runCatching { jsonBase.decodeFromString<Map<String, String>>(it) }.getOrNull() }
    ?.takeIf { it.size <= 256 && it.all { (k, v) -> k.length <= 256 && v.length <= 1000 } }.orEmpty()

internal fun reconcilePickingMarks(marks: Map<String, String>, rows: List<SupplierPickingRow>): Map<String, String> {
    val current = rows.filter { it.quantity != null }.associate { it.key to it.signature }
    return marks.filter { (id, value) -> current[id] == value }
}

@Composable
internal fun AppConfiguration.SupplierPickingChecklist(run: SupplierDispatchWorkspaceRunUiModel, session: SupplierPickingSession) {
    val account = stateValues.userAccount?.id ?: return
    val generation = currentAuthenticatedSessionGeneration()
    val key = "supplier_picking:$account:${run.supplierId}:${run.key}"
    val rows = remember(run.bundles) { supplierPickingRows(run.bundles) }
    val lines = remember(rows) { rows.map { supplierPickingLine(it.line) } }
    val entries = session.entries
    val linesByKey = remember(lines) { lines.associateBy { it.key } }
    val checkedCount = remember(lines, entries) { lines.count { supplierPickingComplete(it, entries[it.key]) } }
    val acceptedCount = remember(lines) { lines.count { it.quantity != null } }
    val readyOrders = remember(run.bundles, run.packableOrderIds, entries) {
        supplierPickingReadyOrderIds(run.bundles, run.packableOrderIds, entries)
    }
    var search by remember(key, generation) { mutableStateOf("") }
    var remaining by remember(key, generation) { mutableStateOf(false) }
    var editorKey by remember(key, generation) { mutableStateOf<String?>(null) }
    var amount by remember(key, generation) { mutableStateOf("") }
    var scanMatches by remember(key, generation) { mutableStateOf<SupplierPickingScan.Matches?>(null) }
    var message by remember(key, generation) { mutableStateOf<String?>(null) }
    var pageNumber by remember(key, generation) { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val scanField = remember(key, generation) { GenericTextFieldContentReference() }
    val visible = remember(rows, linesByKey, entries, search, remaining) {
        val words = search.trim().lowercase().split(Regex("\\s+")).filter(String::isNotEmpty)
        rows.filter { row -> (!remaining || !supplierPickingComplete(linesByKey.getValue(row.key), entries[row.key])) && words.all { word ->
            (row.names.map { it.value } + row.barcodes + row.line.orderId).any { it.lowercase().contains(word) }
        } }
    }
    val pageCount = ((visible.size + 39) / 40).coerceAtLeast(1)
    val visiblePage = pageNumber.coerceIn(0, pageCount - 1)
    LaunchedEffect(search, remaining, lines.map { it.key }) {
        // A product-only weighted scan can reveal an editor beyond the first page after clearing filters.
        pageNumber = editorKey?.let { key -> visible.indexOfFirst { it.key == key }.takeIf { it >= 0 }?.div(40) } ?: 0
    }
    fun openQuantity(line: SupplierPickingLine, revealScannedLine: Boolean = false) {
        editorKey = line.key
        val visibleIndex = visible.indexOfFirst { it.key == line.key }
        if (revealScannedLine && visibleIndex < 0) {
            search = ""
            remaining = false
            pageNumber = rows.indexOfFirst { it.key == line.key }.coerceAtLeast(0) / 40
        } else if (visibleIndex >= 0) pageNumber = visibleIndex / 40
        amount = line.quantity?.let { stockQuantityInputTextFromAmount(supplierPickedQuantity(line, entries[line.key]), it) }.orEmpty()
        message = null
    }
    fun applyScan(line: SupplierPickingLine, increment: Double?) {
        if (increment == null) { openQuantity(line, revealScannedLine = true); scanField.current?.reset(); scanMatches = null; return }
        val next = kotlin.math.round((supplierPickedQuantity(line, entries[line.key]) + increment) * 1000.0) / 1000.0
        if (!supplierPickingQuantityValid(line, next)) { message = "excess"; return }
        val clearSubmittedText = scanField.current?.captureCompletion()
        session.save(lines, mapOf(line.key to next)) { clearSubmittedText?.invoke(); scanMatches = null; message = null }
    }
    fun scan(raw: String) {
        if (session.busy || !session.loaded) { message = "loading"; return }
        message = null
        when (val result = supplierPickingScan(raw, lines)) {
            SupplierPickingScan.Missing -> { scanMatches = null; message = "missing" }
            SupplierPickingScan.AmbiguousWeight -> { scanMatches = null; message = "weight" }
            is SupplierPickingScan.Matches -> {
                val unfilled = result.lines.filter { !supplierPickingComplete(it, entries[it.key]) }
                when {
                    unfilled.isEmpty() -> { scanMatches = null; message = "excess" }
                    unfilled.size == 1 -> applyScan(unfilled.single(), result.quantityForLine(unfilled.single()))
                    else -> scanMatches = result.copy(lines = unfilled)
                }
            }
        }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(supplierPickingText("title"), color = stateValues.TextColor, fontWeight = FontWeight.Bold, fontSize = stateValues.accentTextSize)
        Text(supplierPickingText("help"), color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
        Text("${directionText("checked")}: $checkedCount/$acceptedCount", color = stateValues.TextColor, fontSize = stateValues.textSize)
        Text("${supplierPickingText("ready")}: ${readyOrders.size}/${run.packableOrderIds.size}",
            color = stateValues.AccentColor, fontSize = stateValues.smallTextSize)
        if (!session.loaded && !session.failed) Text(supplierPickingText("loading"), color = stateValues.PlaceholderTextColor)
        if (session.failed) {
            Text(supplierPickingText("save_error"), color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize)
            actionButton(text = localizedStringResource(237, "Refresh"), autoLoading = false, loading = session.busy, enabled = !session.busy,
                confirmationRequired = false, onClick = { scope.launch { session.load() } })
        }
        val currentScanField = searchTextField(
            modifier = Modifier.fillMaxWidth(),
            stateHost = null, stateKey = null, retainTextAcrossRecreation = false, persistTextDraft = false,
            autoFocus = false, forceRefocus = false, barcodeCamScanner = true,
            placeholderText = supplierPickingText("scan"), onBarcodeScanned = { scan(it) }, onSubmitInput = { scan(it) })
        scanField.current = currentScanField
        message?.takeUnless { it == "invalid" }?.let { Text(supplierPickingText(it), color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize) }
        actionButton(text = supplierPickingText("apply_scan"), autoLoading = false, confirmationRequired = false,
            enabled = !session.busy, loading = session.busy, onClick = { scan(currentScanField.liveText()) })
        scanMatches?.let { result ->
            Text(supplierPickingText("choose"), color = stateValues.TextColor, fontSize = stateValues.textSize)
            result.lines.forEach { line ->
                val row = rows.firstOrNull { it.key == line.key }
                actionButton(text = "${row?.names?.extractLocalizedString(stateValues.appLanguage).orEmpty()} · ${supplierPickingText("order")} ${line.orderId.takeLast(8)}",
                    autoLoading = false, confirmationRequired = false, enabled = !session.busy,
                    onClick = { applyScan(line, result.quantityForLine(line)) })
            }
        }
        aitaFormTextField(Modifier.fillMaxWidth(), search, { search = it.take(120) }, directionText("find"),
            identityKey = key, autoFocus = false, parentOwnsValue = true)
        sectionTabsWidget("$key:filter", listOf(TabContent("all", directionText("all")), TabContent("remaining", directionText("remaining"))),
            selectedId = if (remaining) "remaining" else "all", onSelected = { remaining = it == "remaining" })
        if (visible.isEmpty()) Text(directionText("empty"), color = stateValues.PlaceholderTextColor, fontSize = stateValues.textSize)
        visible.drop(visiblePage * 40).take(40).forEach { row -> androidx.compose.runtime.key(row.key) {
            val line = linesByKey.getValue(row.key)
            val checked = supplierPickingComplete(line, entries[line.key])
            val picked = supplierPickedQuantity(line, entries[line.key])
            val shape = RoundedCornerShape(stateValues.cornerRadius)
            Column(Modifier.fillMaxWidth().clip(shape).background(stateValues.BackgroundColor)
                .border(1.dp, if (checked) stateValues.AccentColor else stateValues.TextColor.copy(alpha = .15f), shape)
                .padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth().toggleable(checked,
                    enabled = row.quantity != null && session.loaded && !session.busy, role = Role.Checkbox) {
                    session.save(lines, mapOf(line.key to if (it) line.quantity!!.total else 0.0))
                }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Checkbox(checked, onCheckedChange = null, enabled = row.quantity != null && session.loaded && !session.busy,
                        colors = CheckboxDefaults.colors(checkedColor = stateValues.AccentColor, checkmarkColor = stateValues.AccentTextColor))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(row.names.extractLocalizedString(stateValues.appLanguage).orEmpty().ifBlank { row.line.goodsItemId },
                            color = stateValues.TextColor, fontWeight = FontWeight.Bold, fontSize = stateValues.textSize)
                        Text(row.quantity?.let { stockQuantityInputTextFromAmount(it.total, it) + " " + it.immutableUnitName.extractLocalizedString(stateValues.appLanguage).orEmpty() }
                            ?: supplierPickingText("pending"), color = stateValues.TextColor, fontSize = stateValues.smallTextSize)
                        Text(row.barcodes.joinToString(" · "), color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                    }
                }
                line.quantity?.let { quantity ->
                    Text("${supplierPickingText("quantity")}: ${stockQuantityInputTextFromAmount(picked, quantity)} / ${stockQuantityInputTextFromAmount(quantity.total, quantity)}",
                        color = if (checked) stateValues.OkayColor else stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                    Text("${supplierPickingText("order")}: ${line.orderId.takeLast(8)}", color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                    actionButton(text = supplierPickingText("edit"), autoLoading = false, confirmationRequired = false,
                        enabled = !session.busy, onClick = { if (editorKey == line.key) editorKey = null else openQuantity(line) })
                    if (editorKey == line.key) {
                        aitaFormTextField(Modifier.fillMaxWidth(), amount, { amount = it.take(24); message = null }, supplierPickingText("quantity"),
                            identityKey = "$key:amount:${line.key}", keyboardType = KeyboardType.Decimal, parentOwnsValue = true,
                            enabled = !session.busy, imeAction = ImeAction.Done)
                        StockQuantityQuickFillButtons(quantityUnit = quantity, currentText = amount,
                            shortcutAmounts = listOf(0.0,
                                if (quantity.roundTotal) kotlin.math.floor(quantity.total / 2) else quantity.total / 2,
                                quantity.total).distinct(), onAmountSelected = { amount = it; message = null })
                        if (message == "invalid") Text(supplierPickingText("invalid"), color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize)
                        actionButton(text = stateValues.stringConfirm, autoLoading = false, confirmationRequired = false,
                            enabled = !session.busy, loading = session.busy, onClick = {
                                val parsed = amount.trim().replace(',', '.').toDoubleOrNull()
                                if (parsed == null || !supplierPickingQuantityValid(line, parsed)) message = "invalid"
                                else session.save(lines, mapOf(line.key to parsed)) { editorKey = null; message = null }
                            })
                    }
                }
            }
        } }
        if (pageCount > 1) {
            Text("${supplierPickingText("page")}: ${visiblePage + 1} / $pageCount", color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (visiblePage > 0) actionButton(modifier = Modifier.weight(1f), text = supplierPickingText("previous"), autoLoading = false,
                    confirmationRequired = false, onClick = { pageNumber = visiblePage - 1; editorKey = null })
                if (visiblePage + 1 < pageCount) actionButton(modifier = Modifier.weight(1f), text = supplierPickingText("next"), autoLoading = false,
                    confirmationRequired = false, onClick = { pageNumber = visiblePage + 1; editorKey = null })
            }
        }
        if (lines.any { it.key in entries }) actionButton(text = directionText("reset"), autoLoading = false,
            enabled = !session.busy, loading = session.busy, confirmationRequired = true,
            onClick = { session.save(lines, lines.filter { it.quantity != null }.associate { it.key to 0.0 }) })
    }
}
