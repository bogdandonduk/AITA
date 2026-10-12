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

internal data class SupplierPickingRow(val line: SupplierOrderLineDataModel, val quantity: QuantityDataModel?, val signature: String) {
    val key get() = "${line.orderId}:${line.id}"
    val names get() = if (line.substituteGoodsItemId.isNullOrBlank()) line.goodsItemNameSnapshot else line.substituteGoodsItemNameSnapshot
    val barcodes get() = if (line.substituteGoodsItemId.isNullOrBlank()) line.goodsItemBarcodeSnapshots else line.substituteGoodsItemBarcodeSnapshots
}

internal fun supplierPickingRows(bundles: List<SupplierOrderWithLinesDataModel>): List<SupplierPickingRow> = bundles
    .filter { it.order.isActive && !it.order.status.isSupplierOrderClosed() }
    .flatMap { bundle -> bundle.lines.filter { it.isActive && it.id.isNotBlank() && it.orderId == bundle.order.id }.map { line ->
        val q = line.supplierAcceptedQuantity?.takeIf { it.total.isFinite() && it.total > 0 && it.pricedAmount.isFinite() && it.pricedAmount > 0 }
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
internal fun AppConfiguration.SupplierPickingChecklist(run: SupplierDispatchWorkspaceRunUiModel) {
    val account = stateValues.userAccount?.id ?: return
    val generation = currentAuthenticatedSessionGeneration()
    val host = NavigationScreenModel.Supplier.Dispatch.Main
    val key = "supplier_picking:$account:${run.supplierId}:${run.key}"
    val navigation by host.state.collectAsState()
    val rows = remember(run.bundles) { supplierPickingRows(run.bundles) }
    val marks = reconcilePickingMarks(decodePickingMarks(navigation[key]), rows)
    var search by remember(key, generation) { mutableStateOf("") }
    var remaining by remember(key, generation) { mutableStateOf(false) }
    var error by remember(key, generation) { mutableStateOf(false) }
    fun save(next: Map<String, String>) {
        if (!authenticatedSessionGenerationIsCurrent(generation) || userAccountState.payloadValue?.id != account ||
            activeSupplierProfileIdState.value?.takeIf { it.isNotBlank() }?.let { it != run.supplierId } == true) return
        val raw = jsonBase.encodeToString(next)
        if (raw.length > 32768 || next.size > 256) { error = true; return }
        error = false
        host.setStateNow(key to raw)
    }
    val persisted = decodePickingMarks(navigation[key])
    val presentKeys = rows.map { it.key }.toSet()
    LaunchedEffect(rows, navigation[key]) {
        val retained = persisted.filterKeys { it !in presentKeys } + marks
        if (run.contractSafetyReady && retained != persisted) save(retained)
    }
    val words = search.trim().lowercase().split(Regex("\\s+")).filter(String::isNotEmpty)
    val visible = rows.filter { row -> (!remaining || row.key !in marks) && words.all { word ->
        (row.names.map { it.value } + row.barcodes + row.line.orderId).any { it.lowercase().contains(word) }
    } }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(directionText("picking"), color = stateValues.TextColor, fontWeight = FontWeight.Bold, fontSize = stateValues.accentTextSize)
        Text(directionText("picking_help"), color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
        Text("${directionText("checked")}: ${marks.size}/${rows.size}", color = stateValues.TextColor, fontSize = stateValues.textSize)
        aitaFormTextField(Modifier.fillMaxWidth(), search, { search = it.take(120) }, directionText("find"),
            identityKey = key, autoFocus = false, parentOwnsValue = true)
        sectionTabsWidget("$key:filter", listOf(TabContent("all", directionText("all")), TabContent("remaining", directionText("remaining"))),
            selectedId = if (remaining) "remaining" else "all", onSelected = { remaining = it == "remaining" })
        if (error) Text(directionText("full"), color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize)
        if (visible.isEmpty()) Text(directionText("empty"), color = stateValues.PlaceholderTextColor, fontSize = stateValues.textSize)
        // The surrounding run detail owns scrolling; no nested vertical scroll surface.
        visible.forEach { row -> androidx.compose.runtime.key(row.key) {
            val checked = row.key in marks
            val shape = RoundedCornerShape(stateValues.cornerRadius)
            Row(Modifier.fillMaxWidth().clip(shape).background(stateValues.BackgroundColor)
                .border(1.dp, if (checked) stateValues.AccentColor else stateValues.TextColor.copy(alpha = .15f), shape)
                .toggleable(checked, enabled = row.quantity != null, role = Role.Checkbox) {
                    save(if (it) persisted + (row.key to row.signature) else persisted - row.key)
                }.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Checkbox(checked, onCheckedChange = null, enabled = row.quantity != null,
                    colors = CheckboxDefaults.colors(checkedColor = stateValues.AccentColor, checkmarkColor = stateValues.AccentTextColor))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(row.names.extractLocalizedString(stateValues.appLanguage).orEmpty().ifBlank { row.line.goodsItemId },
                        color = stateValues.TextColor, fontWeight = FontWeight.Bold, fontSize = stateValues.textSize)
                    Text(row.quantity?.let { stockQuantityInputTextFromAmount(it.total, it) + " " + it.immutableUnitName.extractLocalizedString(stateValues.appLanguage).orEmpty() }
                        ?: directionText("pending"), color = stateValues.TextColor, fontSize = stateValues.smallTextSize)
                    Text(row.barcodes.joinToString(" · "), color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                }
            }
        } }
        if (persisted.isNotEmpty()) actionButton(text = directionText("reset"), autoLoading = false, confirmationRequired = true, onClick = { save(emptyMap()) })
    }
}
