package kz.aita

import io.ktor.http.HttpMethod
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

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
    val cashAvailable: Boolean = true,
    val saved: Boolean = false
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
    dashboard: StoreAnalyticsDashboardDataModel, events: List<CashRegisterEventDataModel>? = null, saved: Boolean = false): PreparedAnalytics {
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
        remoteOnly = true, cashAvailable = events != null, saved = saved)
}

private fun analyticsNeedsServerTotals(storeId: String?): Boolean =
    !currentUserCanViewTransactionHistory(storeId) || !currentUserCanViewStock(storeId)

/** Snapshot equality compares immutable inputs by identity, never a large ledger by value. */
private class AnalyticsWork(
    val owner: InventoryOwner, val selection: AnalyticsSelection, val window: AnalyticsWindow,
    val revision: Long, val allowed: Boolean, val detailRestricted: Boolean,
    val cashAllowed: Boolean, val input: AnalyticsInputs?
) {
    fun sameAs(other: AnalyticsWork): Boolean = owner == other.owner && selection == other.selection &&
        window == other.window && revision == other.revision && allowed == other.allowed &&
        detailRestricted == other.detailRestricted && cashAllowed == other.cashAllowed &&
        (if (input == null) other.input == null else other.input?.let(input::sameSources) == true)
}

@Serializable
private data class SavedAnalytics(val accountId: String, val dashboard: StoreAnalyticsDashboardDataModel)

/** One bounded cache per account/store. Selecting analytics starts work; startup alone does not. */
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
        requests.value = SelectionRequest(inventoryOwners.current, selection)
        start()
    }

    fun isCurrent(value: PreparedAnalytics, selection: AnalyticsSelection): Boolean =
        inventoryOwnerIsCurrent(value.owner) && currentUserCanViewAnalytics(value.owner.storeId) &&
            value.selection == selection && value.window == selection.window() &&
            (!analyticsNeedsServerTotals(value.owner.storeId) || value.remoteOnly) &&
            (!value.cashAvailable || currentUserCanViewCashRegister(value.owner.storeId))

    internal fun invalidate() { result.value = null; failed.value = false; refreshRevision.update { it + 1L } }

    fun retry() {
        val owner = inventoryOwners.current
        if (!inventoryOwnerIsCurrent(owner) || !currentUserCanViewAnalytics(owner.storeId)) return
        val storeId = requireNotNull(owner.storeId)
        failed.value = false
        if (currentUserCanViewTransactionHistory(storeId)) getTransactions(storeId)
        if (currentUserCanViewStock(storeId)) { getStock(storeId); getStockBatches(storeId) }
        if (currentUserCanViewCashRegister(storeId)) getCashRegister(storeId)
        refreshRevision.update { it + 1L }
    }

    /** Realtime invalidation refreshes aggregates too when detailed data has not arrived. */
    fun refreshServerTotalsIfNeeded() {
        if (analyticsNeedsServerTotals(activeStoreIdState.value) || transactionsState.payloadValue == null ||
            stockState.payloadValue == null || stockBatchesState.payloadValue == null) refreshRevision.update { it + 1L }
    }

    private fun snapshot(): AnalyticsWork {
        val owner = inventoryOwners.current
        val selection = selectionForCurrentStore()
        val window = selection.window()
        val restricted = analyticsNeedsServerTotals(owner.storeId)
        val cashAllowed = currentUserCanViewCashRegister(owner.storeId)
        val transactions = transactionsState.payloadValue
        val stock = stockState.payloadValue
        val batches = stockBatchesState.payloadValue
        val cash = cashRegisterState.payloadValue?.takeIf { it.storeId == owner.storeId }
        val currency = cash?.currencyCode?.takeIf { it.isNotBlank() }
            ?: globalAppConfigurationState.payloadValue.countries.getFirstCurrencyByCountry(userAccountState.payloadValue?.countryLocale.orEmpty())?.code.orEmpty()
        val input = if (!restricted && transactions != null && stock != null && batches != null)
            AnalyticsInputs(owner, selection, window, transactions, stock, batches,
                cashRegisterEventsState.payloadValue.orEmpty(), suppliersState.payloadValue.orEmpty(), currency,
                cashAllowed && cashRegisterEventsState.payloadValue != null,
                StoreCommerceClient.current().takeIf { it.historyLoaded }?.writeOffs) else null
        return AnalyticsWork(owner, selection, window, refreshRevision.value,
            requests.value?.owner == owner && inventoryOwnerIsCurrent(owner) && currentUserCanViewAnalytics(owner.storeId),
            restricted, cashAllowed, input)
    }

    private fun matches(dashboard: StoreAnalyticsDashboardDataModel, work: AnalyticsWork): Boolean =
        dashboard.storeId == work.owner.storeId && dashboard.startMillis == work.window.startMillis &&
            dashboard.endMillisExclusive == work.window.endMillisExclusive && dashboard.goodsItemIdFilter == work.selection.goodsItemId &&
            dashboard.supplierIdFilter == work.selection.supplierId && dashboard.categoryIdFilter == work.selection.categoryId

    private fun key(work: AnalyticsWork) = inventoryCacheKey("analytics-summary", work.owner)

    private suspend fun publish(work: AnalyticsWork, prepared: PreparedAnalytics?, failure: Boolean) {
        inventoryStateMutex.withLock {
            if (snapshot().sameAs(work)) {
                if (prepared != null) result.value = prepared
                failed.value = failure
            }
        }
    }

    private fun remote(work: AnalyticsWork, dashboard: StoreAnalyticsDashboardDataModel, saved: Boolean) =
        prepareRemoteAnalytics(work.owner, work.selection, work.window, dashboard,
            cashRegisterEventsState.payloadValue.takeIf { work.cashAllowed }, saved)

    private suspend fun save(work: AnalyticsWork, dashboard: StoreAnalyticsDashboardDataModel) {
        try {
            withTimeoutOrNull(2_000) {
                if (snapshot().sameAs(work)) writeJsonCacheText(key(work), jsonBase.encodeToString(SavedAnalytics(requireNotNull(work.owner.accountId), dashboard)))
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { logCloudConnectionDiagnostic("Analytics cache could not be saved") }
    }

    private suspend fun prepare(work: AnalyticsWork) {
        if (!work.allowed) { result.value = null; failed.value = false; return }
        // Keep a usable same-scope view during refresh. Never mistake unloaded lists for zero sales.
        if (result.value?.let { isCurrent(it, work.selection) } != true) result.value = null
        failed.value = false
        try {
            work.input?.let { input ->
                val computed = prepareAnalytics(input)
                publish(work, computed, false)
                save(work, computed.dashboard)
                return
            }
            // Inventory/history can fail independently. The authorized totals endpoint does not
            // require downloading either full list, and offline can use the last saved summary.
            val cached = try {
                withTimeoutOrNull(2_000) { readJsonCacheText(key(work))?.let { jsonBase.decodeFromString<SavedAnalytics>(it) } }
                    ?.takeIf { it.accountId == work.owner.accountId && matches(it.dashboard, work) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
            if (cached != null) publish(work, remote(work, cached.dashboard, true), false)
            val response = withTimeoutOrNull(15_000) {
                networkRequest<StoreAnalyticsDashboardDataModel, Unit>(
                    method = HttpMethod.Get,
                    endpointUrl = globalAppConfigurationState.payloadValue.getStoreAnalyticsPath.first,
                    headers = mapOf("store_id" to requireNotNull(work.owner.storeId)),
                    query = buildMap<String, Any?> {
                        put("startMillis", work.window.startMillis); put("endMillisExclusive", work.window.endMillisExclusive)
                        work.selection.goodsItemId?.takeIf { it.isNotBlank() }?.let { put("goodsItemId", it) }
                        work.selection.supplierId?.takeIf { it.isNotBlank() }?.let { put("supplierId", it) }
                        work.selection.categoryId?.takeIf { it.isNotBlank() }?.let { put("categoryId", it) }
                    }, expectedSessionGeneration = work.owner.sessionGeneration)
            }
            val dashboard = response?.payload?.takeIf { !response.negative && matches(it, work) }
            if (response?.httpStatusCode == 403 && !response.transportFailure && snapshot().sameAs(work)) {
                result.value = null
                writeJsonCacheText(key(work), "")
            }
            publish(work, dashboard?.let { remote(work, it, false) }, dashboard == null)
            if (dashboard != null) save(work, dashboard)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            publish(work, null, true)
            logCloudConnectionDiagnostic("Analytics preparation failed; same-scope cached view retained")
        }
    }

    internal fun start() {
        job.startIfIdle(scope, Dispatchers.Default) {
            val dates = flow {
                while (currentCoroutineContext().isActive) {
                    val zone = TimeZone.currentSystemDefault()
                    emit(Instant.fromEpochMilliseconds(getCurrentTimeMillis()).toLocalDateTime(zone).date.toString() to zone.id)
                    delay(60_000)
                }
            }.distinctUntilChanged().map { Unit }
            merge(
                transactionsState.payload.map { Unit }, stockState.payload.map { Unit }, stockBatchesState.payload.map { Unit },
                cashRegisterEventsState.payload.map { Unit }, cashRegisterState.payload.map { Unit }, suppliersState.payload.map { Unit },
                inventoryOwners.state.map { Unit }, activeStoreIdState.map { Unit }, userAccountState.payload.map { Unit },
                storesState.payload.map { Unit }, myWorkerMembershipsState.payload.map { Unit }, StoreCommerceClient.state.map { Unit },
                requests.map { Unit }, refreshRevision.map { Unit }, globalAppConfigurationState.payload.map { Unit }, dates
            ).debounce(100).map { snapshot() }.distinctUntilChanged { a, b -> a.sameAs(b) }.collectLatest { prepare(it) }
        }
    }
}
