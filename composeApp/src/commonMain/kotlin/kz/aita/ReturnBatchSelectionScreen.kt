package kz.aita

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

internal fun AppConfiguration.returnDestinationsReady(slot: Int): Boolean {
    val cart = getCartState(1, slot).value
    return cart.isNotEmpty() && cart.all { item ->
        val goods = stateValues.stock.orEmpty().find { it.id == item.id } ?: return@all false
        val selection = currentCartReturnBatchSelection(1, slot, item.id) ?: return@all false
        (selection.stockBatchId == null && selection.returnDestinationKind == StockBatchKindDataModel.RETURNED) ||
            (selection.returnDestinationKind == null && returnCandidateBatchesFor(goods, stateValues.stockBatches.orEmpty()).any { it.id == selection.stockBatchId })
    }
}

@Composable internal fun AppConfiguration.ReturnBatchSelectionScreen() {
    val context = rememberTransactionContext()
    val slot = context.clientId
    val cart by getCartState(1, slot).collectAsState()
    val selections by getCartReturnBatchSelectionsState().collectAsState()
    val ownerState by DynamicCarts.state.collectAsState()
    val owner = ownerState.owner?.takeIf { ownerState.ready && DynamicCarts.isCurrent(it) }
    val scope = rememberCoroutineScope()
    AitaScreenColumn(Modifier.fillMaxSize(), appBar = {
        ScreenAppBarWidget(title = returnFlowText("batches"), iconPath = stateValues.drawablePathIconStock,
            onBack = { scope.launch { Navigation.TransactionReturn.pop() } })
    }) {
        Text(returnFlowText("choose"), Modifier.padding(8.dp), color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
        LazyColumn(Modifier.weight(1f).padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(cart, key = { it.id }) { item ->
                val goods = stateValues.stock.orEmpty().find { it.id == item.id }
                if (goods != null) {
                    val selection = selections[cartReturnBatchSelectionKey(1, slot, item.id)]
                    val candidates = returnCandidateBatchesFor(goods, stateValues.stockBatches.orEmpty())
                    val sourceIds = selection?.sourceBatchAllocations.orEmpty().map { it.stockBatchId }.toSet()
                    val ordered = candidates.sortedWith(compareByDescending<GoodsBatchDataModel> { it.id == selection?.shelfBatchIdAtSale }
                        .thenByDescending { it.id in sourceIds })
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(goods.name.visibleLocalizedString(stateValues.appLanguage, goods.id), color = stateValues.TextColor,
                            fontSize = stateValues.accentTextSize, fontWeight = FontWeight.Bold)
                        Text(item.quantity.quantityText(stateValues.appLanguage), color = stateValues.PlaceholderTextColor)
                        selection?.originalReceiptTimeMillis?.let { Text(receiptUiDateTime(it), color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize) }
                        if (sourceIds.isEmpty()) Text(returnFlowText("unknown"), color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                        fun choose(id: String?, kind: StockBatchKindDataModel?) {
                            val captured = owner ?: return
                            if (!DynamicCarts.isCurrent(captured)) return
                            val base = selection ?: resolveReturnBatchSelection(goods, item, candidates, null).selection
                            setCartReturnBatchSelection(1, slot, item.id, base.copy(stockBatchId = id, returnDestinationKind = kind))
                        }
                        ordered.forEach { batch ->
                            ReturnBatchChoice(selected = selection?.stockBatchId == batch.id && selection.returnDestinationKind == null,
                                title = buildString {
                                    if (batch.kind != StockBatchKindDataModel.NORMAL) append(returnFlowText(batch.kind.name.lowercase()) + " • ")
                                    append(returnBatchSummaryText(goods, batch, candidates, selection?.currencyCode ?: defaultTransactionCurrencyCode()))
                                },
                                detail = listOfNotNull(
                                    returnFlowText("original").takeIf { batch.id in sourceIds },
                                    returnFlowText("shelf_then").takeIf { batch.id == selection?.shelfBatchIdAtSale }
                                ).joinToString(" • "),
                                emphasized = batch.id in sourceIds || batch.id == selection?.shelfBatchIdAtSale,
                                enabled = owner != null, onClick = { choose(batch.id, null) })
                        }
                        ReturnBatchChoice(selected = selection?.returnDestinationKind == StockBatchKindDataModel.RETURNED && selection.stockBatchId == null,
                            title = returnFlowText("new_returned"), enabled = owner != null, onClick = { choose(null, StockBatchKindDataModel.RETURNED) })
                    }
                } else Text(returnFlowText("unavailable_item"), color = stateValues.ErrorColor)
            }
        }
        actionButton(Modifier.fillMaxWidth().padding(8.dp), text = stateValues.stringPayment,
            autoLoading = false, confirmationRequired = false,
            enabled = owner != null && returnDestinationsReady(slot),
            onClick = { scope.launch { if (owner?.let(DynamicCarts::isCurrent) == true && returnDestinationsReady(slot)) Navigation.TransactionReturn.go(NavigationScreenModel.Transaction.Payment) } })
    }
}

@Composable private fun AppConfiguration.ReturnBatchChoice(selected: Boolean, title: String, detail: String = "", emphasized: Boolean = false,
    enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(stateValues.cornerRadius)
    Column(Modifier.fillMaxWidth().clip(shape).background(if (selected) stateValues.AccentColor else stateValues.BackgroundColor)
        .border(if (emphasized || selected) stateValues.focusedBorderWidth else stateValues.unfocusedBorderWidth,
            if (emphasized || selected) stateValues.AccentColor else stateValues.PlaceholderTextColor, shape)
        .semantics { this.selected = selected }.clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick).padding(12.dp)) {
        Text(title, color = if (selected) stateValues.AccentTextColor else stateValues.TextColor, fontSize = stateValues.textSize)
        if (detail.isNotBlank()) Text(detail, color = if (selected) stateValues.AccentTextColor else stateValues.AccentColor,
            fontSize = stateValues.smallTextSize, fontWeight = FontWeight.Bold)
    }
}
