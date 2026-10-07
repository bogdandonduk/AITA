package kz.aita

import io.ktor.http.HttpMethod
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.*

/** Kept with the workspace, not the composable; relative periods roll forward at local midnight. */
data class AnalyticsSelection(
    val periodId: String = "30",
    val customStartMillis: Long = 0L,
    val customEndMillis: Long = Long.MAX_VALUE,
    val goodsItemId: String? = null,
    val supplierId: String? = null,
    val categoryId: String? = null
)

data class AnalyticsWindow(
    val startMillis: Long,
    val endMillisExclusive: Long,
    val timeZoneId: String
)

fun AnalyticsSelection.window(nowMillis: Long = getCurrentTimeMillis()): AnalyticsWindow {
    val zone = TimeZone.currentSystemDefault()
    if (periodId == "all") return AnalyticsWindow(0, Long.MAX_VALUE, zone.id)
    if (periodId == "custom") return AnalyticsWindow(customStartMillis, customEndMillis, zone.id)
    val today = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(zone).date
    val first = when (periodId) {
        "today" -> today
        "7" -> today.plus(-6, DateTimeUnit.DAY)
        "month" -> LocalDate(today.year, today.monthNumber, 1)
        "year" -> LocalDate(today.year, 1, 1)
        else -> today.plus(-29, DateTimeUnit.DAY)
    }
    return AnalyticsWindow(first.atStartOfDayIn(zone).toEpochMilliseconds(),
        today.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone).toEpochMilliseconds(), zone.id)
}

data class AnalyticsHistorySummary(val title: String, val count: Int, val total: Double)
data class AnalyticsTypeSummary(
    val count: Int = 0,
    val total: Double = 0.0,
    val cash: Double = 0.0,
    val card: Double = 0.0,
    val quantity: Double = 0.0,
    val history: List<AnalyticsHistorySummary> = emptyList(),
    val paymentsKnown: Boolean = true,
    val historyKnown: Boolean = true
) {
    val debt: Double get() = (total - cash - card).coerceAtLeast(0.0)
    val average: Double get() = if (count > 0) total / count else 0.0
    val averageItems: Double get() = if (count > 0) quantity / count else 0.0
}

data class AnalyticsStockSummary(
    val items: Int = 0, val activeItems: Int = 0, val quickItems: Int = 0,
    val batches: Int = 0, val activeBatches: Int = 0, val available: Boolean = true
)

/** Identity equality: Compose must not deep-compare a full transaction history on the UI thread. */
class PreparedAnalytics internal constructor(
    internal val owner: InventoryOwner,
    val selection: AnalyticsSelection,
    val window: AnalyticsWindow,
    val transactions: List<TransactionDataModel>,
    val events: List<CashRegisterEventDataModel>,
    val dashboard: StoreAnalyticsDashboardDataModel,
    val types: Map<String, AnalyticsTypeSummary>,
    val stock: AnalyticsStockSummary,
    val suppliers: List<AnalyticsRankedItemDataModel>,
    val workshifts: List<AnalyticsRankedItemDataModel>,
    val supplierQuantity: Double,
    val saleCash: Double,
    val returnCash: Double,
    val extractedCash: Double,
    val remoteOnly: Boolean = false,
    val cashAvailable: Boolean = true
) {
    fun type(id: String): AnalyticsTypeSummary = types[id] ?: AnalyticsTypeSummary()
}

internal class AnalyticsInputs(
    val owner: InventoryOwner,
    val selection: AnalyticsSelection,
    val window: AnalyticsWindow,
    val transactions: List<TransactionDataModel>,
    val stock: List<GoodsItemDataModel>,
    val batches: List<GoodsBatchDataModel>,
    val events: List<CashRegisterEventDataModel>,
    val suppliers: List<SupplierDataModel>,
    val currency: String,
    val cashAvailable: Boolean = true,
    val writeOffs: List<StockWriteOff>? = null
) {
    fun sameSources(other: AnalyticsInputs): Boolean = owner == other.owner && selection == other.selection &&
        window == other.window && currency == other.currency && cashAvailable == other.cashAvailable && transactions === other.transactions &&
        writeOffs === other.writeOffs && stock === other.stock && batches === other.batches && events === other.events && suppliers === other.suppliers
}

internal suspend fun prepareAnalytics(input: AnalyticsInputs): PreparedAnalytics {
    val context = currentCoroutineContext()
    val storeId = requireNotNull(input.owner.storeId)
    val selection = input.selection
    val period = input.transactions.filter { tx ->
        context.ensureActive()
        tx.storeId == storeId && tx.timeMillis >= input.window.startMillis && tx.timeMillis < input.window.endMillisExclusive
    }
    val scoped = analyticsScopedTransactions(period, input.stock, input.batches,
        selection.goodsItemId, selection.supplierId, selection.categoryId)
    context.ensureActive()
    val dashboard = buildStoreAnalyticsDashboard(storeId, input.window.startMillis, input.window.endMillisExclusive,
        input.transactions, input.stock, input.batches, input.currency,
        selection.goodsItemId, selection.supplierId, selection.categoryId).withWriteOffs(input.writeOffs)
    context.ensureActive()
    val zone = TimeZone.of(input.window.timeZoneId)
    val types = scoped.groupBy { it.type }.mapValues { (_, txs) ->
        context.ensureActive()
        AnalyticsTypeSummary(txs.size,
            txs.sumOf { tx -> tx.goodsInTransaction.sumOf { it.quantity * it.pricePerUnit } },
            txs.sumOf { it.paidCash }, txs.sumOf { it.paidCard },
            txs.sumOf { tx -> tx.goodsInTransaction.sumOf { it.quantity } },
            txs.groupBy { tx ->
                context.ensureActive()
                val date = Instant.fromEpochMilliseconds(tx.timeMillis).toLocalDateTime(zone)
                "${date.monthNumber.toString().padStart(2, '0')}.${date.year}"
            }.map { (label, entries) -> AnalyticsHistorySummary(label, entries.size, entries.sumOf { it.paidCash + it.paidCard }) })
    }
    val supplierMap = input.suppliers.associateBy { it.id }
    val suppliers = scoped.asSequence().filter { it.type == "accept" }
        .flatMap { tx -> tx.goodsInTransaction.asSequence().map { tx to it } }
        .groupBy { (_, line) -> line.supplierIdText?.takeIf { it.isNotBlank() } ?: line.supplierId?.toString().orEmpty().ifBlank { "unknown" } }
        .map { (id, entries) ->
            context.ensureActive()
            AnalyticsRankedItemDataModel(id = id, name = supplierMap[id]?.name.orEmpty(),
                quantity = entries.sumOf { it.second.quantity }, transactionCount = entries.map { it.first.id }.toSet().size,
                amount = entries.sumOf { it.second.quantity * it.second.pricePerUnit }, currencyCode = input.currency)
        }.sortedByDescending { it.amount }
    val workshifts = scoped.filter { it.type == "purchase" }.groupBy { it.workshiftId }.map { (id, txs) ->
        context.ensureActive()
        AnalyticsRankedItemDataModel(id = id.toString(), transactionCount = txs.size,
            quantity = txs.sumOf { tx -> tx.goodsInTransaction.sumOf { it.quantity } },
            amount = txs.sumOf { tx -> tx.goodsInTransaction.sumOf { it.quantity * it.pricePerUnit } }, currencyCode = input.currency)
    }.sortedByDescending { it.amount }.take(10)
    val events = input.events.takeIf { input.cashAvailable }.orEmpty().filter { event ->
        context.ensureActive()
        event.storeId == storeId && event.timeMillis >= input.window.startMillis && event.timeMillis < input.window.endMillisExclusive
    }
    return PreparedAnalytics(input.owner, selection, input.window, scoped, events, dashboard, types,
        AnalyticsStockSummary(input.stock.size, input.stock.count { it.isActive }, input.stock.count { it.isQuickItem },
            input.batches.size, input.batches.count { it.isActive }), suppliers, workshifts, suppliers.sumOf { it.quantity },
        events.filter { it.type == CASH_REGISTER_EVENT_SALE_CASH_IN }.sumOf { it.amount },
        events.filter { it.type == CASH_REGISTER_EVENT_RETURN_CASH_OUT }.sumOf { it.amount },
        events.filter { it.type == CASH_REGISTER_EVENT_EXTRACTION }.sumOf { it.amount }, cashAvailable = input.cashAvailable)
}

internal fun prepareRemoteAnalytics(owner: InventoryOwner, selection: AnalyticsSelection, window: AnalyticsWindow,
    dashboard: StoreAnalyticsDashboardDataModel, events: List<CashRegisterEventDataModel>? = null): PreparedAnalytics {
    fun summary(count: Int, total: Double, quantity: Double) = AnalyticsTypeSummary(
        count = count, total = total, quantity = quantity, paymentsKnown = false, historyKnown = false)
    val scopedEvents = events.orEmpty().filter {
        it.storeId == owner.storeId && it.timeMillis >= window.startMillis && it.timeMillis < window.endMillisExclusive
    }
    return PreparedAnalytics(owner, selection, window, emptyList(), scopedEvents, dashboard,
        mapOf("purchase" to summary(dashboard.saleCount, dashboard.grossSales, dashboard.soldQuantity),
            "return" to summary(dashboard.returnCount, dashboard.returnsAmount, dashboard.returnedQuantity),
            "accept" to summary(dashboard.supplyCount, dashboard.supplyCost, dashboard.suppliedQuantity)),
        AnalyticsStockSummary(available = false), emptyList(), emptyList(), dashboard.suppliedQuantity,
        scopedEvents.filter { it.type == CASH_REGISTER_EVENT_SALE_CASH_IN }.sumOf { it.amount },
        scopedEvents.filter { it.type == CASH_REGISTER_EVENT_RETURN_CASH_OUT }.sumOf { it.amount },
        scopedEvents.filter { it.type == CASH_REGISTER_EVENT_EXTRACTION }.sumOf { it.amount },
        remoteOnly = true, cashAvailable = events != null)
}

private fun analyticsNeedsServerTotals(storeId: String?): Boolean =
    !currentUserCanViewTransactionHistory(storeId) || !currentUserCanViewStock(storeId)

/** One bounded cache for the current account/store and most recently selected filters. */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
object AnalyticsWorkspace {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val job = OwnedConnectionJob()
    private val requests = MutableStateFlow<SelectionRequest?>(null)
    private data class SelectionRequest(val owner: InventoryOwner, val selection: AnalyticsSelection)
    private val result = MutableStateFlow<PreparedAnalytics?>(null)
    val state: StateFlow<PreparedAnalytics?> = result.asStateFlow()
    private val failed = MutableStateFlow(false)
    val failure: StateFlow<Boolean> = failed.asStateFlow()
    private val refreshRevision = MutableStateFlow(0L)

    fun selectionForCurrentStore(): AnalyticsSelection = requests.value
        ?.takeIf { it.owner == inventoryOwners.current }?.selection ?: AnalyticsSelection()

    fun select(selection: AnalyticsSelection) {
        val owner = inventoryOwners.current
        requests.value = SelectionRequest(owner, selection)
        start()
    }

    fun isCurrent(value: PreparedAnalytics, selection: AnalyticsSelection): Boolean =
        inventoryOwnerIsCurrent(value.owner) && currentUserCanViewAnalytics(value.owner.storeId) &&
            value.selection == selection && value.window == selection.window() &&
            value.remoteOnly == analyticsNeedsServerTotals(value.owner.storeId) &&
            value.cashAvailable == (currentUserCanViewCashRegister(value.owner.storeId) && cashRegisterEventsState.payloadValue != null)

    internal fun invalidate() { result.value = null; failed.value = false; refreshRevision.update { it + 1L } }

    fun retry() { failed.value = false; refreshRevision.update { it + 1L } }

    /** Called by the existing realtime invalidation path, never by a periodic network poll. */
    fun refreshServerTotalsIfNeeded() {
        if (analyticsNeedsServerTotals(activeStoreIdState.value)) refreshRevision.update { it + 1L }
    }

    private suspend fun remoteTotals(owner: InventoryOwner, selection: AnalyticsSelection, window: AnalyticsWindow): StoreAnalyticsDashboardDataModel? {
        val response = networkRequest<StoreAnalyticsDashboardDataModel, Unit>(
            method = HttpMethod.Get,
            endpointUrl = globalAppConfigurationState.payloadValue.getStoreAnalyticsPath.first,
            headers = mapOf("store_id" to requireNotNull(owner.storeId)),
            query = buildMap<String, Any?> {
                put("startMillis", window.startMillis); put("endMillisExclusive", window.endMillisExclusive)
                selection.goodsItemId?.takeIf { it.isNotBlank() }?.let { put("goodsItemId", it) }
                selection.supplierId?.takeIf { it.isNotBlank() }?.let { put("supplierId", it) }
                selection.categoryId?.takeIf { it.isNotBlank() }?.let { put("categoryId", it) }
            },
            expectedSessionGeneration = owner.sessionGeneration
        )
        return response.payload?.takeIf { !response.negative && it.storeId == owner.storeId &&
            it.startMillis == window.startMillis && it.endMillisExclusive == window.endMillisExclusive &&
            it.goodsItemIdFilter == selection.goodsItemId && it.supplierIdFilter == selection.supplierId &&
            it.categoryIdFilter == selection.categoryId }
    }

    internal fun start() {
        job.startIfIdle(scope, Dispatchers.Default) {
            // Only a date/time-zone change triggers recomputation; no minute-by-minute network polling.
            val dates = flow {
                while (currentCoroutineContext().isActive) {
                    val zone = TimeZone.currentSystemDefault()
                    emit(Instant.fromEpochMilliseconds(getCurrentTimeMillis()).toLocalDateTime(zone).date.toString() to zone.id)
                    delay(60_000)
                }
            }.distinctUntilChanged().map { Unit }
            val changes = merge(
                transactionsState.payload.map { Unit }, stockState.payload.map { Unit }, stockBatchesState.payload.map { Unit },
                cashRegisterEventsState.payload.map { Unit }, cashRegisterState.payload.map { Unit }, suppliersState.payload.map { Unit },
                inventoryOwners.state.map { Unit }, activeStoreIdState.map { Unit }, userAccountState.payload.map { Unit },
                storesState.payload.map { Unit }, myWorkerMembershipsState.payload.map { Unit },
                StoreCommerceClient.state.map {Unit}, requests.map { Unit }, refreshRevision.map { Unit }, globalAppConfigurationState.payload.map { Unit }, dates
            )
            var last: AnalyticsInputs? = null
            var lastRemote: Pair<SelectionRequest, Pair<AnalyticsWindow, Long>>? = null
            changes.debounce(100).collectLatest {
                val owner = inventoryOwners.current
                if (!inventoryOwnerIsCurrent(owner) || !currentUserCanViewAnalytics(owner.storeId)) {
                    result.value = null; last = null; return@collectLatest
                }
                val selection = selectionForCurrentStore()
                val window = selection.window()
                if (analyticsNeedsServerTotals(owner.storeId)) {
                    // Analytics permission does not grant transaction-history/stock-read permission.
                    // Keep the existing authorized aggregate endpoint for those roles.
                    val remoteKey = SelectionRequest(owner, selection) to (window to refreshRevision.value)
                    try {
                        val cached = result.value?.takeIf { lastRemote == remoteKey && it.remoteOnly }
                        val dashboard = cached?.dashboard ?: remoteTotals(owner, selection, window)
                        val events = cashRegisterEventsState.payloadValue
                        val cashAvailable = currentUserCanViewCashRegister(owner.storeId) && events != null
                        val computed = dashboard?.let { prepareRemoteAnalytics(owner, selection, window, it, events.takeIf { cashAvailable }) }
                        currentCoroutineContext().ensureActive()
                        inventoryStateMutex.withLock {
                            if (inventoryOwnerIsCurrent(owner) && currentUserCanViewAnalytics(owner.storeId) &&
                                analyticsNeedsServerTotals(owner.storeId) && selectionForCurrentStore() == selection &&
                                remoteKey.second.second == refreshRevision.value && events === cashRegisterEventsState.payloadValue &&
                                cashAvailable == (currentUserCanViewCashRegister(owner.storeId) && cashRegisterEventsState.payloadValue != null)) {
                                if (computed == null) failed.value = true
                                else {
                                    result.value = computed
                                    failed.value = false; lastRemote = remoteKey; last = null
                                }
                            }
                        }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { if (inventoryOwnerIsCurrent(owner)) failed.value = true }
                    return@collectLatest
                }
                lastRemote = null
                val transactions = transactionsState.payloadValue
                val stock = stockState.payloadValue
                val batches = stockBatchesState.payloadValue
                // Unloaded is not an empty ledger. Retain only an already-computed same-owner result.
                if (transactions == null || stock == null || batches == null) {
                    if (result.value?.owner != owner) result.value = null
                    return@collectLatest
                }
                val cash = cashRegisterState.payloadValue?.takeIf { it.storeId == owner.storeId }
                val currency = cash?.currencyCode?.takeIf { it.isNotBlank() }
                    ?: globalAppConfigurationState.payloadValue.countries.getFirstCurrencyByCountry(userAccountState.payloadValue?.countryLocale.orEmpty())?.code.orEmpty()
                val input = AnalyticsInputs(owner, selection, window, transactions, stock, batches,
                    cashRegisterEventsState.payloadValue.orEmpty(), suppliersState.payloadValue.orEmpty(), currency,
                    currentUserCanViewCashRegister(owner.storeId) && cashRegisterEventsState.payloadValue != null,
                    StoreCommerceClient.current().takeIf {it.historyLoaded}?.writeOffs)
                if (last?.sameSources(input) == true && result.value != null) return@collectLatest
                try {
                    val computed = prepareAnalytics(input)
                    currentCoroutineContext().ensureActive()
                    inventoryStateMutex.withLock {
                        if (inventoryOwnerIsCurrent(owner) && currentUserCanViewAnalytics(owner.storeId) &&
                            !analyticsNeedsServerTotals(owner.storeId) && selectionForCurrentStore() == selection && transactionsState.payloadValue === transactions &&
                            stockState.payloadValue === stock && stockBatchesState.payloadValue === batches &&
                            input.events === cashRegisterEventsState.payloadValue.orEmpty() &&
                            input.suppliers === suppliersState.payloadValue.orEmpty() &&
                            input.cashAvailable == (currentUserCanViewCashRegister(owner.storeId) && cashRegisterEventsState.payloadValue != null)) {
                            result.value = computed
                            last = input
                            failed.value = false
                        }
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) {
                    if (inventoryOwnerIsCurrent(owner) && selectionForCurrentStore() == selection) failed.value = true
                    // Keep the last successful same-scope view, never a fabricated zero dashboard.
                    logCloudConnectionDiagnostic("Analytics preparation failed; cached view retained")
                }
            }
        }
    }
}
