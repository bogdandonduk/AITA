package kz.aita

import kotlinx.coroutines.flow.*
import kotlinx.serialization.decodeFromString

val cartBuyerPromoClockState = MutableStateFlow(getCurrentTimeMillis())
val cartBuyersState = MutableStateFlow<Map<String, StoreBuyer>>(emptyMap())
val cartQuickDiscountsState = MutableStateFlow<Map<String, Double>>(emptyMap())
val cartCheckoutsState = MutableStateFlow<Map<String, CartCheckoutAttempt>>(emptyMap())
val cartSaleMethodIdsState = MutableStateFlow<Map<String, String>>(emptyMap())
val cartConditionChecksState = MutableStateFlow<Map<String, Boolean>>(emptyMap())
private val paymentDrafts = MutableStateFlow<Map<String, TransactionPaymentDraftDataModel>>(emptyMap())
private val suppliersForCarts = MutableStateFlow<Map<String, String>>(emptyMap())
private val returnReasons = MutableStateFlow<Map<String, String>>(emptyMap())
private val returnBatches = MutableStateFlow<Map<String, CartReturnBatchSelectionDataModel>>(emptyMap())
private val cartScrolls = MutableStateFlow<Map<String, TransactionCartScrollStateDataModel>>(emptyMap())
internal fun publishCartUiState(ui: CartUiState) {
    cartBuyersState.value = ui.buyers
    cartQuickDiscountsState.value = ui.discounts
    cartCheckoutsState.value = ui.checkouts
    cartSaleMethodIdsState.value = ui.saleMethods
    cartConditionChecksState.value = ui.checks
    paymentDrafts.value = ui.payments
    suppliersForCarts.value = ui.suppliers
    returnReasons.value = ui.reasons
    returnBatches.value = ui.batches
    cartScrolls.value = ui.scrolls
}
fun getTransactionPaymentDraftsState() = paymentDrafts.asStateFlow()
fun getTransactionSupplySupplierIdsState() = suppliersForCarts.asStateFlow()
fun getCartReturnReasonsState() = returnReasons.asStateFlow()
fun getCartReturnBatchSelectionsState() = returnBatches.asStateFlow()
fun getTransactionCartScrollStatesState() = cartScrolls.asStateFlow()
fun getCartConditionChecksState() = cartConditionChecksState.asStateFlow()
private fun cartKey(type: Int, slot: Int): String {
    require(validCartSlot(type, slot))
    return "$type:$slot"
}
fun getCartSaleMethodId(transactionTypeIndex: Int, clientId: Int, goodsItemId: String): String =
    cartSaleMethodIdsState.value["${cartKey(transactionTypeIndex, clientId)}:$goodsItemId"] ?: SALE_METHOD_RETAIL
fun setCartSaleMethodId(transactionTypeIndex: Int, clientId: Int, goodsItemId: String, saleMethodId: String) {
    val key = "${cartKey(transactionTypeIndex, clientId)}:$goodsItemId"
    DynamicCarts.editUiAsync { it.copy(saleMethods = if (saleMethodId == SALE_METHOD_WHOLESALE)
        it.saleMethods + (key to SALE_METHOD_WHOLESALE) else it.saleMethods - key) }
}
fun setCartConditionChecked(conditionKey: String, checked: Boolean) {
    val key = conditionKey.trim().takeIf { it.isNotBlank() } ?: return
    DynamicCarts.editUiAsync { it.copy(checks = it.checks + (key to checked)) }
}
fun pruneCartConditionChecks(transactionTypeIndex: Int, clientId: Int, validKeys: Set<String>) {
    val prefix = cartKey(transactionTypeIndex, clientId) + ":"
    val stableKeys = validKeys.toSet()
    DynamicCarts.editUiAsync { it.copy(checks = it.checks.filterKeys { key -> !key.startsWith(prefix) || key in stableKeys }) }
}
fun getTransactionPaymentDraft(transactionTypeIndex: Int, clientId: Int): TransactionPaymentDraftDataModel? =
    paymentDrafts.value[cartKey(transactionTypeIndex, clientId)]
fun setTransactionPaymentDraft(draft: TransactionPaymentDraftDataModel) {
    val key = cartKey(draft.transactionTypeIndex, draft.clientId)
    DynamicCarts.editUiAsync { it.copy(payments = it.payments + (key to draft)) }
}
fun clearTransactionPaymentDraft(transactionTypeIndex: Int, clientId: Int) {
    val key = cartKey(transactionTypeIndex, clientId)
    DynamicCarts.editUiAsync { it.copy(payments = it.payments - key) }
}
fun transactionSupplySupplierKey(transactionTypeIndex: Int, clientId: Int) = cartKey(transactionTypeIndex, clientId)
fun currentTransactionSupplySupplierId(transactionTypeIndex: Int, clientId: Int): String? =
    suppliersForCarts.value[cartKey(transactionTypeIndex, clientId)]
fun setTransactionSupplySupplierId(transactionTypeIndex: Int, clientId: Int, supplierId: String?) {
    val key = cartKey(transactionTypeIndex, clientId)
    val selected = supplierId?.trim()?.takeIf { it.isNotBlank() }
    DynamicCarts.editUiAsync { it.copy(suppliers = if (selected == null) it.suppliers - key else it.suppliers + (key to selected)) }
}
fun clearTransactionSupplySupplierId(transactionTypeIndex: Int, clientId: Int) = setTransactionSupplySupplierId(transactionTypeIndex, clientId, null)
fun setTransactionCartScrollState(scrollState: TransactionCartScrollStateDataModel) {
    val key = cartKey(scrollState.transactionTypeIndex, scrollState.clientId)
    val value = scrollState.copy(firstVisibleItemIndex = scrollState.firstVisibleItemIndex.coerceAtLeast(0),
        firstVisibleItemScrollOffset = scrollState.firstVisibleItemScrollOffset.coerceAtLeast(0), updatedAtMillis = getCurrentTimeMillis())
    DynamicCarts.editUiAsync { it.copy(scrolls = it.scrolls + (key to value)) }
}
fun clearTransactionCartScrollState(transactionTypeIndex: Int, clientId: Int) {
    val key = cartKey(transactionTypeIndex, clientId)
    DynamicCarts.editUiAsync { it.copy(scrolls = it.scrolls - key) }
}
fun cartReturnBatchSelectionKey(transactionTypeIndex: Int, clientId: Int, goodsItemId: String) = "${cartKey(transactionTypeIndex, clientId)}:$goodsItemId"
fun currentCartReturnBatchSelection(transactionTypeIndex: Int, clientId: Int, goodsItemId: String) =
    returnBatches.value[cartReturnBatchSelectionKey(transactionTypeIndex, clientId, goodsItemId)]
fun setCartReturnBatchSelection(transactionTypeIndex: Int, clientId: Int, goodsItemId: String, selection: CartReturnBatchSelectionDataModel?) {
    val cleanId = goodsItemId.trim().takeIf { it.isNotBlank() } ?: return
    val key = cartReturnBatchSelectionKey(transactionTypeIndex, clientId, cleanId)
    val value = selection?.takeIf { transactionTypeIndex == 1 }?.copy(goodsItemId = cleanId,
        stockBatchId = selection.stockBatchId?.trim()?.takeIf { it.isNotBlank() },
        pricePerUnit = selection.pricePerUnit?.coerceAtLeast(0.0)?.roundMoney(),
        currencyCode = selection.currencyCode.trim().uppercase(), updatedAtMillis = getCurrentTimeMillis())
    DynamicCarts.editUiAsync { it.copy(batches = if (value == null) it.batches - key else it.batches + (key to value)) }
}
fun cartReturnReasonKey(transactionTypeIndex: Int, clientId: Int, goodsItemId: String) = "${cartKey(transactionTypeIndex, clientId)}:$goodsItemId"
fun currentCartReturnReason(transactionTypeIndex: Int, clientId: Int, goodsItemId: String): String =
    returnReasons.value[cartReturnReasonKey(transactionTypeIndex, clientId, goodsItemId)].orEmpty()
fun setCartReturnReason(transactionTypeIndex: Int, clientId: Int, goodsItemId: String, reason: String) {
    val key = cartReturnReasonKey(transactionTypeIndex, clientId, goodsItemId)
    val value = reason.take(500).takeIf { transactionTypeIndex == 1 && it.isNotBlank() }
    DynamicCarts.editUiAsync { it.copy(reasons = if (value == null) it.reasons - key else it.reasons + (key to value)) }
}
private suspend inline fun <reified T> legacyCartValue(key: String): T? =
    readJsonCacheText("cache_json:$key")?.let { jsonBase.decodeFromString<T>(it) }
internal suspend fun readLegacyCartUiState() = CartUiState(
    saleMethods = legacyCartValue<Map<String, String>>("transaction_cart_sale_method_ids").orEmpty(),
    payments = legacyCartValue<Map<String, TransactionPaymentDraftDataModel>>("transaction_payment_drafts").orEmpty(),
    suppliers = legacyCartValue<Map<String, String>>("transaction_supply_supplier_ids").orEmpty(),
    reasons = legacyCartValue<Map<String, String>>("transaction_return_reasons").orEmpty(),
    batches = legacyCartValue<Map<String, CartReturnBatchSelectionDataModel>>("transaction_return_batch_selections").orEmpty(),
    checks = legacyCartValue<Map<String, Boolean>>("transaction_cart_condition_checks").orEmpty(),
    scrolls = legacyCartValue<Map<String, TransactionCartScrollStateDataModel>>("transaction_cart_scroll_states").orEmpty()
)

fun setCartQuickDiscount(slot: Int, percent: Double) {
    require(validCartSlot(0, slot) && validQuickDiscount(percent))
    val key = "0:$slot"
    DynamicCarts.editUiAsync { ui -> if (key in ui.checkouts) ui else ui.copy(
        discounts = if (percent == 0.0) ui.discounts - key else ui.discounts + (key to percent),
        payments = ui.payments - key) }
}

fun setCartItemDiscount(slot: Int, goodsId: String, percent: Double) {
    require(validCartSlot(0,slot) && goodsId.isNotBlank() && validQuickDiscount(percent))
    val cart = "0:$slot"; val key = "$cart:$goodsId"
    DynamicCarts.editUiAsync { it.copy(discounts = if (percent == 0.0) it.discounts-key else it.discounts+(key to percent), payments = it.payments-cart) }
}
fun setCartBuyer(slot: Int, buyer: StoreBuyer?) {
    val owner = DynamicCarts.captureScope() ?: return
    require(validCartSlot(0,slot) && (buyer == null || buyer.valid() && buyer.isActive && buyer.storeId == owner.storeId))
    val key = "0:$slot"
    DynamicCarts.editUiAsync { it.copy(buyers = if (buyer == null) it.buyers-key else it.buyers+(key to buyer), payments = it.payments-key) }
}
fun cartSaleDiscounts(slot: Int, goodsId: String, discounts: Map<String,Double> = cartQuickDiscountsState.value,
    buyer: StoreBuyer? = cartBuyersState.value["0:$slot"], now:Long=getCurrentTimeMillis()): SaleDiscounts = SaleDiscounts(
    itemPercent = discounts["0:$slot:$goodsId"] ?: 0.0, cartPercent = discounts["0:$slot"] ?: 0.0,
    buyerPercent = buyer?.activeDiscount(now) ?: 0.0)
