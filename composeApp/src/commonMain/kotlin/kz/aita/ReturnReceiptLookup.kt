package kz.aita

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

private data class ReturnReceiptScan(val owner: CartScope, val slot: Int, val identity: String, val nonce: Long)
private val returnReceiptScan = MutableStateFlow<ReturnReceiptScan?>(null)

internal fun AppConfiguration.requestReturnReceiptScan(raw: String, slot: Int): Boolean {
    val identity = parseTransactionReceiptBarcodeIdentity(raw) ?: return false
    val owner = DynamicCarts.captureScope() ?: return true
    returnReceiptScan.value = ReturnReceiptScan(owner, slot, identity.lookup, getCurrentTimeMillis())
    coroutineScope.launch {
        if (DynamicCarts.isCurrent(owner) && stateValues.navigationTransactionReturnClientId == slot) Navigation.TransactionReturn.go(NavigationScreenModel.Transaction.Cart)
    }
    return true
}

/** True while the temporary search list occupies the cart pane. Cart contents remain untouched. */
@Composable internal fun AppConfiguration.ReturnReceiptLookup(slot: Int): Boolean {
    val book by DynamicCarts.state.collectAsState()
    val owner = book.owner?.takeIf { book.ready && DynamicCarts.isCurrent(it) }
    val scan by returnReceiptScan.collectAsState()
    var exactQuery by remember(owner, slot) { mutableStateOf<String?>(null) }
    var matches by remember(owner, slot) { mutableStateOf<List<TransactionDataModel>>(emptyList()) }
    var selected by remember(owner, slot) { mutableStateOf<TransactionDataModel?>(null) }
    var searching by remember(owner, slot) { mutableStateOf(false) }
    var status by remember(owner, slot) { mutableStateOf<String?>(null) }
    var retry by remember(owner, slot) { mutableStateOf(0) }
    var lookupField by remember(owner, slot) { mutableStateOf<GenericTextFieldContent?>(null) }
    val field = key(owner, slot) {
        searchTextField(Modifier.fillMaxWidth().padding(stateValues.marginTextField),
            stateHost = null, stateKey = null, persistTextDraft = false, retainTextAcrossRecreation = false,
            autoFocus = false, barcodeCamScanner = true, captureTransactionBarcodeInput = true, placeholderText = returnFlowText("search"),
            onBarcodeScanned = { raw ->
                val identity = parseTransactionReceiptBarcodeIdentity(raw)
                if (identity == null) {
                    tryHandleTransactionBarcodeInput(raw + "\n", 1, slot, getCartState(1, slot).value,
                        lookupField?.captureCompletion())
                } else { exactQuery = identity.lookup; retry++ }
            })
    }
    SideEffect { lookupField = field }
    val typed = field.value.text.trim().take(160)
    LaunchedEffect(scan, owner, slot) {
        val pending = scan ?: return@LaunchedEffect
        if (pending.owner == owner && pending.slot == slot) {
            exactQuery = pending.identity
            retry++
            returnReceiptScan.compareAndSet(pending, null)
        } else if (!DynamicCarts.isCurrent(pending.owner)) returnReceiptScan.compareAndSet(pending, null)
    }
    LaunchedEffect(typed) {
        val identity = parseTransactionReceiptBarcodeIdentity(typed)
        if (typed.isNotBlank()) exactQuery = identity?.let { it.lookup }
        if (identity == null && field.editedSinceCreation && uniqueExactBarcode(transactionStockCandidatesForUi(false), typed) { it.allBarcodeValues() } != null) {
            delay(500)
            if (field.value.text.trim() == typed && !transactionBarcodeAddPending(typed, 1, slot))
                tryHandleTransactionBarcodeInput(typed + "\n", 1, slot, getCartState(1, slot).value, field.captureCompletion())
        }
    }
    val query = exactQuery ?: typed
    val isExact = exactQuery != null
    val lookupGeneration = remember { intArrayOf(0) }
    DisposableEffect(owner, slot, query, isExact, retry) {
        lookupGeneration[0]++
        onDispose { lookupGeneration[0]++ }
    }
    LaunchedEffect(owner, slot, query, isExact, retry) {
        val generation = lookupGeneration[0]
        matches = emptyList(); status = null; searching = false
        val captured = owner ?: return@LaunchedEffect
        if (query.isBlank()) return@LaunchedEffect
        if (!isExact && query.length < 3) { status = returnFlowText("minimum"); return@LaunchedEffect }
        searching = true
        try {
            if (!isExact) delay(250)
            val result = searchReturnReceipts(query, isExact, captured.storeId)
            if (!DynamicCarts.isCurrent(captured) || generation != lookupGeneration[0]) return@LaunchedEffect
            val receipts = if (!result.negative) result.payload.orEmpty() else if (result.transportFailure && currentUserCanViewTransactionHistory(captured.storeId)) {
                status = returnFlowText("offline")
                transactionsState.payloadValue.orEmpty().filter { it.storeId == captured.storeId && it.type == "purchase" &&
                    it.matchesReceiptLookup(query, isExact) }.take(20)
            } else { status = returnFlowText("lookup_error"); emptyList() }
            matches = receipts
            if (receipts.size == 1 && (isExact || receipts.single().matchesReceiptLookup(query, true))) {
                selected = receipts.single(); field.reset(); exactQuery = null; matches = emptyList()
            } else if (receipts.isEmpty() && status == null) status = returnFlowText("not_found")
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { if (DynamicCarts.isCurrent(captured) && generation == lookupGeneration[0]) status = returnFlowText("lookup_error") }
        finally { if (generation == lookupGeneration[0]) searching = false }
    }
    selected?.let { receipt -> ReturnReceiptSheet(receipt, owner, slot) { selected = null } }
    val visible = query.isNotBlank()
    if (visible) {
        if (isExact) actionButton(text = stateValues.stringClear, autoLoading = false, confirmationRequired = false, onClick = { exactQuery = null; field.reset() })
        if (searching) Text(returnFlowText("searching"), Modifier.padding(8.dp), color = stateValues.PlaceholderTextColor)
        status?.let { Text(it, Modifier.padding(8.dp), color = stateValues.PlaceholderTextColor) }
        if (!searching && status != null && query.length >= 3) actionButton(text = checkoutText("retry"), autoLoading = false,
            confirmationRequired = false, onClick = { retry++ })
        LazyColumn(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(matches, key = { it.id }) { receipt ->
                TransactionHistoryCard(receipt) { selected = receipt; field.reset(); exactQuery = null; matches = emptyList() }
            }
        }
    }
    return visible
}

@Composable private fun AppConfiguration.ReturnReceiptSheet(receipt: TransactionDataModel, owner: CartScope?, slot: Int, onDismiss: () -> Unit) {
    val snapshot = remember(receipt, stateValues.appLanguage, stateValues.stock, stateValues.stores) { transactionHistoryReceiptSnapshot(receipt) }
    val labels = receiptLabels()
    val scope = rememberCoroutineScope()
    val cart by getCartState(1, slot).collectAsState()
    var adding by remember(owner, slot, receipt.id) { mutableStateOf(false) }
    var tab by remember(receipt.id, owner, slot) { mutableStateOf("receipt") }
    AitaBottomSheet(title = stateValues.stringReceipt, iconPath = stateValues.drawablePathIconReceipt, onDismiss = onDismiss) {
        tabRowWidget(Modifier.fillMaxWidth().padding(8.dp), selectedIndexInitial = tab,
            tabs = listOf(TabContent("receipt", stateValues.stringReceipt) { tab = it },
                TabContent("items", "${returnFlowText("items")} · ${snapshot.lines.size}") { tab = it }),
            unselectedContainerColor = stateValues.BackgroundColor)
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (tab == "receipt") {
                item { Column(Modifier.fillMaxWidth().background(Color.White).padding(8.dp)) { ReceiptPreviewHeader(snapshot, labels) } }
                items(snapshot.lines, key = { it.index }) { line ->
                    Column(Modifier.fillMaxWidth().background(Color.White).padding(8.dp)) { ReceiptPreviewLine(line, labels) }
                }
                item { Column(Modifier.fillMaxWidth().background(Color.White).padding(8.dp)) { ReceiptPreviewTotals(snapshot, labels) } }
            } else items(snapshot.lines, key = { it.index }) { line ->
                val original = receipt.goodsInTransaction[line.index]
                val goods = transactionHistoryFindGoodsItem(original)?.takeIf { item ->
                    (original.goodsItemId.isNullOrBlank() || original.goodsItemId == item.id) && transactionStockCandidatesForUi(false).any { it.id == item.id } }
                val inCart = cart.any { it.id == goods?.id }
                val canAdd = goods != null && !inCart && !adding && owner != null && line.quantity.total > 0.0
                Row(Modifier.fillMaxWidth().border(stateValues.unfocusedBorderWidth,
                    if (inCart) stateValues.AccentColor else stateValues.PlaceholderTextColor,
                    RoundedCornerShape(stateValues.cornerRadius)).aitaClickable(onClick = {
                        val captured = owner
                        if (canAdd && captured != null && goods != null && DynamicCarts.isCurrent(captured)) {
                            adding = true
                            scope.launch {
                                try {
                                    val added = DynamicCarts.addReceiptReturn(captured, slot,
                                        line.quantity.copy(total = receiptReturnMinimum(line.quantity, line.quantity.total)),
                                        CartReturnBatchSelectionDataModel(goodsItemId = goods.id,
                                            pricePerUnit = original.pricePerUnit, currencyCode = line.currencyCode,
                                            originalQuickDiscountPercent = original.quickDiscountPercent,
                                            originalPriceBeforeDiscount = original.priceBeforeDiscount,
                                            originalTransactionId = receipt.serverReceiptIdOrNull(),
                                            originalClientOperationId = receipt.clientOperationId.takeIf { it.isNotBlank() },
                                            originalTransactionLineIndex = line.index,
                                            sourceBatchAllocations = original.sourceBatchAllocations,
                                            shelfBatchIdAtSale = original.shelfBatchIdAtSale,
                                            originalReceiptTimeMillis = receipt.timeMillis,
                                            originalReceiptQuantity = line.quantity.total,
                                            updatedAtMillis = getCurrentTimeMillis()))
                                    if (DynamicCarts.isCurrent(captured)) postInAppNotification(
                                        if (added) returnFlowText("added") else returnFlowText("already_added"),
                                        if (added) NotificationType.Positive else NotificationType.Neutral, transient = true)
                                } catch (cancel: CancellationException) { throw cancel }
                                catch (_: Exception) { if (DynamicCarts.isCurrent(captured)) postInAppNotification(checkoutText("save_error"), NotificationType.Negative, transient = true) }
                                finally { adding = false }
                            }
                        }
                    }).padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(original.name.visibleLocalizedString(stateValues.appLanguage,
                            goods?.name?.visibleLocalizedString(stateValues.appLanguage, original.barcode) ?: original.barcode),
                            color = stateValues.TextColor, fontSize = stateValues.textSize, fontWeight = FontWeight.Bold)
                        Text(line.quantity.quantityText(stateValues.appLanguage), color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                        if (goods == null || inCart) Text(returnFlowText(if (goods == null) "unavailable_item" else "already_added"),
                            color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                    }
                    if (canAdd) CpImage(Modifier.size(stateValues.iconSize), url = stateValues.drawablePathIconAdd,
                        fallbackRes = stateValues.drawableResIconAdd.collectAsState().value,
                        contentDescription = returnFlowText("add_return"), tintColor = stateValues.AccentColor)
                }
            }
        }
        actionButton(Modifier.fillMaxWidth().padding(8.dp), text = stateValues.stringCart, autoLoading = false, confirmationRequired = false, onClick = onDismiss)
    }
}
