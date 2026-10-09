package kz.aita

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

internal data class LiveStockCollections(
    val owner: String,
    val stock: List<GoodsItemDataModel>,
    val batches: List<GoodsBatchDataModel>,
    val stores: List<StoreDataModel>,
    val byId: Map<String, GoodsItemDataModel>,
    val activeBatchesByItem: Map<String, List<GoodsBatchDataModel>>,
    val saleBatchesByItem: Map<String, List<GoodsBatchDataModel>>,
    val warehouseBatchesByItem: Map<String, List<GoodsBatchDataModel>>,
    val quantityByItem: Map<String, Double>
) {
    // Compose and StateFlow conflate equal values: a newly opened screen can hold an equal
    // list with a different reference from the app-lifetime worker's retained list.
    fun matchesSources(owner: String?, items: List<GoodsItemDataModel>?, sourceBatches: List<GoodsBatchDataModel>?): Boolean =
        this.owner == owner && stock == items && batches == sourceBatches.orEmpty()
}

internal data class WarehouseSelection(
    val query: String = "", val sort: String = "name", val ascending: Boolean = true,
    val filter: String = STOCK_WAREHOUSE_FILTER_TOTAL, val fallbackOrder: List<String> = emptyList()
)
internal data class WarmWarehouseProjection(
    val owner: String, val stock: List<GoodsItemDataModel>, val batches: List<GoodsBatchDataModel>,
    val language: String, val selection: WarehouseSelection, val projection: StockWarehouseProjection
)
internal data class NotificationSelection(val query: String = "", val category: String = "all")
internal data class NotificationListProjection(
    val account: String, val generation: Long, val language: String, val selection: NotificationSelection,
    val searched: List<NotificationDataModel>, val visible: List<NotificationDataModel>
)

/** App-lifetime worker, not a screen effect. Source flows keep feeding it while screens are closed. */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
internal object LiveCollectionWorkspace {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val started = MutableStateFlow(false)
    private val stockResult = MutableStateFlow<LiveStockCollections?>(null)
    val stock = stockResult.asStateFlow()
    private val warehouseSelection = MutableStateFlow(WarehouseSelection())
    private val warehouseResult = MutableStateFlow<WarmWarehouseProjection?>(null)
    val warehouse = warehouseResult.asStateFlow()
    private val notificationSelection = MutableStateFlow(NotificationSelection())
    private val notificationResult = MutableStateFlow<NotificationListProjection?>(null)
    val notifications = notificationResult.asStateFlow()
    fun selectWarehouse(selection: WarehouseSelection) { warehouseSelection.value = selection }
    fun selectNotifications(selection: NotificationSelection) { notificationSelection.value = selection }

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            merge(stockState.payload.map { Unit }, stockBatchesState.payload.map { Unit },
                activeStoreIdState.map { Unit }, userAccountState.payload.map { Unit }, storesState.payload.map { Unit },
                stockLoadStatusState.map { Unit }, stockBatchesLoadStatusState.map { Unit }
            ).collectLatest {
                val owner = inventoryViewScopeKey()
                val items = stockState.payloadValue
                val batches = stockBatchesState.payloadValue
                val stores = storesState.payloadValue.orEmpty()
                if (owner == null || items == null || stockLoadStatusState.value.accessDenied || stockBatchesLoadStatusState.value.accessDenied) {
                    stockResult.value = null
                    return@collectLatest
                }
                if (stockResult.value?.owner != owner) stockResult.value = null
                val previous = stockResult.value
                if (previous?.stock == items && previous.batches == batches.orEmpty() && previous.stores == stores) return@collectLatest
                val batchSource = batches.orEmpty()
                val storeId = activeStoreIdState.value
                fun belongsToStore(batchStoreId: String) = batchStoreId.isNotBlank() && batchStoreId == storeId
                val warehouseBatches = stockWarehouseBatchesByItemForUi(batchSource.filter { belongsToStore(it.storeId) })
                val result = LiveStockCollections(owner, items, batchSource, stores, items.associateBy { it.id },
                    batchSource.filter { it.isActive && belongsToStore(it.storeId) }.groupBy { it.goodsItemId },
                    batchSource.filter { it.isSelectableActiveStockBatch() && belongsToStore(it.storeId) }.groupBy { it.goodsItemId },
                    warehouseBatches, warehouseBatches.mapValues { (_, rows) -> rows.availableStockQuantity() })
                ensureActive()
                // StateFlow suppresses equal payload emissions. An equal cloud/cache replacement
                // can change list identity without scheduling another calculation; rejecting it
                // here would strand every transaction search on the previous inventory index.
                if (inventoryViewScopeKey() == owner && stockState.payloadValue == items && stockBatchesState.payloadValue == batches && storesState.payloadValue.orEmpty() == stores)
                    stockResult.value = result
            }
        }
        scope.launch {
            // Expiry-based filters stay alive across midnight/navigation without polling the server.
            val clock = flow { while (currentCoroutineContext().isActive) { emit(getCurrentTimeMillis() / 60_000L); delay(60_000L) } }
            combine(stock, appLanguageState, warehouseSelection, clock) { data, language, selection, _ -> Triple(data, language, selection) }
                .collectLatest { (data, rawLanguage, selection) ->
                    if (data == null || data.owner != inventoryViewScopeKey()) { warehouseResult.value = null; return@collectLatest }
                    if (warehouseResult.value?.owner != data.owner) warehouseResult.value = null
                    val language = effectiveAppLanguage(rawLanguage)
                    val projection = buildStockWarehouseProjection(data.stock, selection.query, emptyList(), selection.sort,
                        selection.ascending, language, data.quantityByItem, data.warehouseBatchesByItem, selection.filter, selection.fallbackOrder)
                    ensureActive()
                    if (data.owner == inventoryViewScopeKey() && stock.value === data && warehouseSelection.value == selection)
                        warehouseResult.value = WarmWarehouseProjection(data.owner, data.stock, data.batches, language, selection, projection)
                }
        }
        scope.launch {
            merge(notificationsState.payload.map { Unit }, deviceFileNotifications.map { Unit },
                userAccountState.payload.map { Unit }, appLanguageState.map { Unit }, stringsState.payload.map { Unit },
                notificationSelection.map { Unit }).collectLatest {
                val account = userAccountState.payloadValue?.id
                val generation = currentAuthenticatedSessionGeneration()
                if (account.isNullOrBlank()) { notificationResult.value = null; return@collectLatest }
                if (notificationResult.value?.let { it.account != account || it.generation != generation } == true) notificationResult.value = null
                val language = effectiveAppLanguage(appLanguageState.value)
                val selection = notificationSelection.value
                val resources = currentEventResourceCatalogue()
                val remote = notificationsState.payloadValue.orEmpty()
                val local = deviceFileNotifications.value.entries.filter { it.owner.isCurrent() }.map { it.notification }
                val all = (local + remote).distinctBy { it.id }
                val query = selection.query.trim()
                val searched = all.filter { notification ->
                    ensureActive()
                    query.isBlank() || listOf(notification.message, notification.title, notification.category, notification.source,
                        notification.localizedEventMessage(language, resources::values), notification.localizedEventTitle(language, resources::values),
                        resources.values(when(notification.type) { NotificationType.Positive -> 179L; NotificationType.Negative -> 180L; else -> 181L })
                            ?.extractLocalizedString(language).orEmpty(),
                        notification.metadata.values.joinToString(" "), notification.createdAtMillis.toString()
                    ).any { it.contains(query, ignoreCase = true) }
                }.sortedWith(compareByDescending<NotificationDataModel> { it.createdAtMillis }.thenBy { it.id })
                val filtered = searched.filter { notification -> when (selection.category) {
                    "positive" -> notification.type == NotificationType.Positive
                    "negative" -> notification.type == NotificationType.Negative
                    "neutral" -> notification.type == NotificationType.Neutral
                    "unread" -> notification.readAtMillis == null
                    else -> true
                } }
                ensureActive()
                if (userAccountState.payloadValue?.id == account && currentAuthenticatedSessionGeneration() == generation && notificationSelection.value == selection)
                    notificationResult.value = NotificationListProjection(account, generation, language, selection, searched, filtered)
            }
        }
    }
}
