package kz.aita

internal const val SUPPLIER_DISPATCH_FILTER_ALL = "all"
internal const val SUPPLIER_DISPATCH_FILTER_READY_TO_PACK = "ready_to_pack"
internal const val SUPPLIER_DISPATCH_FILTER_READY_TO_DISPATCH = "ready_to_dispatch"
internal const val SUPPLIER_DISPATCH_FILTER_IN_DELIVERY = "in_delivery"
internal const val SUPPLIER_DISPATCH_FILTER_ATTENTION = "attention"
internal const val SUPPLIER_DISPATCH_FILTER_CONTRACTS = "contracts"
internal const val SUPPLIER_DISPATCH_FILTER_OVERDUE_PROMISE = "overdue_promise"
internal const val SUPPLIER_DISPATCH_FILTER_DUE_SOON_PROMISE = "due_soon_promise"

internal const val SUPPLIER_DISPATCH_SORT_ACTION = "action"
internal const val SUPPLIER_DISPATCH_SORT_DUE = "due"
internal const val SUPPLIER_DISPATCH_SORT_STORE = "store"
internal const val SUPPLIER_DISPATCH_SORT_RECENT = "recent"

internal data class SupplierDispatchWorkspaceRunUiModel(
    val key: String,
    val serverRunId: String = "",
    val supplierId: String = "",
    val storeId: String = "",
    val storePublicId: String = "",
    val storeTitle: String = "",
    val storeAddress: String = "",
    val orderIds: List<String> = emptyList(),
    val bundles: List<SupplierOrderWithLinesDataModel> = emptyList(),
    val packableOrderIds: List<String> = emptyList(),
    val dispatchableOrderIds: List<String> = emptyList(),
    val inDeliveryOrderIds: List<String> = emptyList(),
    val issueOrderIds: List<String> = emptyList(),
    val attentionOrderIds: List<String> = emptyList(),
    val contractBlockedOrderIds: List<String> = emptyList(),
    val statusMix: List<SupplierDashboardStatusBucketDataModel> = emptyList(),
    val orderCount: Int = 0,
    val lineCount: Int = 0,
    val activeContractCount: Int = 0,
    val pendingContractCount: Int = 0,
    val contractSafetyReady: Boolean = true,
    val earliestDueAtMillis: Long? = null,
    val latestDueAtMillis: Long? = null,
    val latestActivityMillis: Long = 0L,
    val goodsPreview: String = "",
    val packChecklist: String = "",
    val attentionSummary: String = "",
    val driverHandoff: String = "",
    val estimatedAmount: PriceDataModel? = null,
    val priorityScore: Int = 0,
    val suggestedAction: String = "plan",
    val serverPlanned: Boolean = false,
    val searchKey: String = ""
) {
    val readyToPackCount: Int get() = packableOrderIds.size
    val readyToDispatchCount: Int get() = dispatchableOrderIds.size
    val inDeliveryCount: Int get() = inDeliveryOrderIds.size
    val issueCount: Int get() = issueOrderIds.size
    val attentionCount: Int
        get() = (attentionOrderIds + issueOrderIds + contractBlockedOrderIds)
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .distinct()
            .size

    val navigationSearchQuery: String
        get() = storeId.trim()
            .ifBlank { storePublicId.trim() }
            .ifBlank { orderIds.firstOrNull().orEmpty().trim() }
            .ifBlank { storeTitle.trim() }
}

internal fun cleanSupplierDispatchOrderIds(ids: Collection<String>): List<String> = ids
    .asSequence()
    .map { it.trim() }
    .filter { it.isNotBlank() }
    .distinctBy { it.lowercase() }
    .toList()

internal fun claimSupplierDispatchServerOrderIds(
    runs: List<SupplierDashboardDispatchRunDataModel>
): List<List<String>> {
    val claimed = mutableSetOf<String>()
    return runs.map { run ->
        cleanSupplierDispatchOrderIds(
            run.orderIds +
                    run.packableOrderIds +
                    run.dispatchableOrderIds +
                    run.inDeliveryOrderIds +
                    run.issueOrderIds +
                    run.attentionOrderIds +
                    run.contractBlockedOrderIds
        ).filter { orderId -> claimed.add(orderId.lowercase()) }
    }
}

internal fun supplierDispatchOrderIdsForLoadedState(
    claimedOrderIds: Collection<String>,
    locallyOpenOrderIds: Collection<String>,
    ordersLoaded: Boolean
): List<String> {
    val claimed = cleanSupplierDispatchOrderIds(claimedOrderIds)
    if (!ordersLoaded) return claimed
    val localIds = cleanSupplierDispatchOrderIds(locallyOpenOrderIds).map { it.lowercase() }.toSet()
    return claimed.filter { orderId -> orderId.lowercase() in localIds }
}

internal fun safeSupplierDispatchActionOrderIds(
    runOrderIds: Collection<String>,
    requestedActionOrderIds: Collection<String>,
    contractBlockedOrderIds: Collection<String>
): List<String> {
    val runIds = cleanSupplierDispatchOrderIds(runOrderIds).associateBy { it.lowercase() }
    val blocked = cleanSupplierDispatchOrderIds(contractBlockedOrderIds).map { it.lowercase() }.toSet()
    return cleanSupplierDispatchOrderIds(requestedActionOrderIds)
        .mapNotNull { requested -> runIds[requested.lowercase()] }
        .filterNot { it.lowercase() in blocked }
}

internal fun SupplierDispatchWorkspaceRunUiModel.orderIdsForFilter(filterId: String): List<String> = when (filterId) {
    SUPPLIER_DISPATCH_FILTER_READY_TO_PACK -> packableOrderIds
    SUPPLIER_DISPATCH_FILTER_READY_TO_DISPATCH -> dispatchableOrderIds
    SUPPLIER_DISPATCH_FILTER_IN_DELIVERY -> inDeliveryOrderIds
    SUPPLIER_DISPATCH_FILTER_ATTENTION -> attentionOrderIds + issueOrderIds + contractBlockedOrderIds
    SUPPLIER_DISPATCH_FILTER_CONTRACTS -> contractBlockedOrderIds
    else -> orderIds
}.let(::cleanSupplierDispatchOrderIds)

private fun SupplierOrderDataModel.supplierDispatchPromiseUrgency(
    nowEpochMillis: Long,
    dueSoonWindowMillis: Long = SUPPLIER_PROMISE_WATCH_WINDOW_MILLIS,
): SupplierPromiseUrgency {
    if (status.isSupplierOrderClosed()) return SupplierPromiseUrgency.LATER
    val promisedAt = supplierDueAtMillis() ?: return SupplierPromiseUrgency.UNSCHEDULED
    val safeWindow = dueSoonWindowMillis.coerceAtLeast(0L)
    return when {
        promisedAt < nowEpochMillis -> SupplierPromiseUrgency.OVERDUE
        promisedAt <= nowEpochMillis + safeWindow -> SupplierPromiseUrgency.DUE_SOON
        else -> SupplierPromiseUrgency.LATER
    }
}

/**
 * Time-sensitive dispatch filters operate on the orders inside a Store run, not merely on the
 * run's earliest date. This keeps the result counter honest when one run contains a late order and
 * several later commitments. Server-only provisional runs fall back to their earliest promise until
 * the detailed order payload arrives.
 */
internal fun SupplierDispatchWorkspaceRunUiModel.orderIdsForFilter(
    filterId: String,
    nowEpochMillis: Long,
): List<String> = when (normalizedSupplierDispatchFilter(filterId)) {
    SUPPLIER_DISPATCH_FILTER_OVERDUE_PROMISE,
    SUPPLIER_DISPATCH_FILTER_DUE_SOON_PROMISE -> {
        val requiredUrgency = if (filterId == SUPPLIER_DISPATCH_FILTER_OVERDUE_PROMISE) {
            SupplierPromiseUrgency.OVERDUE
        } else {
            SupplierPromiseUrgency.DUE_SOON
        }
        val detailedMatches = bundles
            .asSequence()
            .map { it.order }
            .filter { it.supplierDispatchPromiseUrgency(nowEpochMillis) == requiredUrgency }
            .map { it.id }
            .toList()

        if (bundles.isNotEmpty()) {
            detailedMatches
        } else {
            val provisionalUrgency = when {
                earliestDueAtMillis == null -> SupplierPromiseUrgency.UNSCHEDULED
                earliestDueAtMillis < nowEpochMillis -> SupplierPromiseUrgency.OVERDUE
                earliestDueAtMillis <= nowEpochMillis + SUPPLIER_PROMISE_WATCH_WINDOW_MILLIS ->
                    SupplierPromiseUrgency.DUE_SOON
                else -> SupplierPromiseUrgency.LATER
            }
            orderIds.takeIf { provisionalUrgency == requiredUrgency }.orEmpty()
        }
    }

    else -> orderIdsForFilter(filterId)
}.let(::cleanSupplierDispatchOrderIds)

internal fun SupplierDispatchWorkspaceRunUiModel.matchesSupplierDispatchFilter(filterId: String): Boolean =
    orderIdsForFilter(filterId).isNotEmpty()

internal fun SupplierDispatchWorkspaceRunUiModel.matchesSupplierDispatchFilter(
    filterId: String,
    nowEpochMillis: Long,
): Boolean = orderIdsForFilter(filterId, nowEpochMillis).isNotEmpty()

internal fun List<SupplierDispatchWorkspaceRunUiModel>.distinctOrderCountForSupplierDispatchFilter(
    filterId: String
): Int = flatMap { run -> run.orderIdsForFilter(filterId) }
    .map { it.lowercase() }
    .distinct()
    .size

internal fun List<SupplierDispatchWorkspaceRunUiModel>.distinctOrderCountForSupplierDispatchFilter(
    filterId: String,
    nowEpochMillis: Long,
): Int = flatMap { run -> run.orderIdsForFilter(filterId, nowEpochMillis) }
    .map { it.lowercase() }
    .distinct()
    .size

internal fun normalizedSupplierDispatchFilter(filterId: String): String = when (filterId) {
    SUPPLIER_DISPATCH_FILTER_READY_TO_PACK,
    SUPPLIER_DISPATCH_FILTER_READY_TO_DISPATCH,
    SUPPLIER_DISPATCH_FILTER_IN_DELIVERY,
    SUPPLIER_DISPATCH_FILTER_ATTENTION,
    SUPPLIER_DISPATCH_FILTER_CONTRACTS,
    SUPPLIER_DISPATCH_FILTER_OVERDUE_PROMISE,
    SUPPLIER_DISPATCH_FILTER_DUE_SOON_PROMISE -> filterId
    else -> SUPPLIER_DISPATCH_FILTER_ALL
}

internal fun normalizedSupplierDispatchSort(sortId: String): String = when (sortId) {
    SUPPLIER_DISPATCH_SORT_DUE,
    SUPPLIER_DISPATCH_SORT_STORE,
    SUPPLIER_DISPATCH_SORT_RECENT -> sortId
    else -> SUPPLIER_DISPATCH_SORT_ACTION
}

internal fun List<SupplierDispatchWorkspaceRunUiModel>.sortedForSupplierDispatch(
    sortId: String
): List<SupplierDispatchWorkspaceRunUiModel> {
    val comparator = when (normalizedSupplierDispatchSort(sortId)) {
        SUPPLIER_DISPATCH_SORT_DUE ->
            compareBy<SupplierDispatchWorkspaceRunUiModel> { it.earliestDueAtMillis ?: Long.MAX_VALUE }
                .thenByDescending { it.priorityScore }
                .thenBy { it.storeTitle.lowercase() }

        SUPPLIER_DISPATCH_SORT_STORE ->
            compareBy<SupplierDispatchWorkspaceRunUiModel> { it.storeTitle.lowercase() }
                .thenBy { it.earliestDueAtMillis ?: Long.MAX_VALUE }
                .thenByDescending { it.latestActivityMillis }

        SUPPLIER_DISPATCH_SORT_RECENT ->
            compareByDescending<SupplierDispatchWorkspaceRunUiModel> { it.latestActivityMillis }
                .thenByDescending { it.priorityScore }
                .thenBy { it.storeTitle.lowercase() }

        else ->
            compareByDescending<SupplierDispatchWorkspaceRunUiModel> { if (it.issueCount > 0) 1 else 0 }
                .thenByDescending { if (it.contractBlockedOrderIds.isNotEmpty()) 1 else 0 }
                .thenByDescending { if (!it.contractSafetyReady) 1 else 0 }
                .thenByDescending { if (it.attentionCount > 0) 1 else 0 }
                .thenByDescending { if (it.readyToPackCount > 0) 1 else 0 }
                .thenByDescending { if (it.readyToDispatchCount > 0) 1 else 0 }
                .thenByDescending { if (it.inDeliveryCount > 0) 1 else 0 }
                .thenByDescending { it.priorityScore }
                .thenBy { it.earliestDueAtMillis ?: Long.MAX_VALUE }
                .thenByDescending { it.latestActivityMillis }
    }
    return sortedWith(comparator)
}

private fun String.supplierDispatchIdentityToken(): String = trim().lowercase()

private fun SupplierOrderDataModel.supplierDispatchStableStoreIdentity(sourceIndex: Int): String =
    storeId.supplierDispatchIdentityToken()
        .takeIf { it.isNotBlank() }
        ?.let { "store-id:$it" }
        ?: storePublicIdSnapshot.supplierDispatchIdentityToken()
            .takeIf { it.isNotBlank() }
            ?.let { "store-public:$it" }
        ?: id.supplierDispatchIdentityToken()
            .takeIf { it.isNotBlank() }
            ?.let { "order:$it" }
        ?: "anonymous-order:$sourceIndex"

private fun supplierDispatchDayKey(dueAtMillis: Long?): String = dueAtMillis
    ?.takeIf { it > 0L }
    ?.toStockDateInputText()
    ?.takeIf { it.isNotBlank() }
    ?: "unscheduled"

private fun SupplierOrderLineDataModel.supplierDispatchTargetGoodsItemId(): String =
    substituteGoodsItemId.orEmpty().trim().ifBlank { goodsItemId.trim() }

private fun SupplierOrderWithLinesDataModel.supplierDispatchGoodsItemIds(): Set<String> = lines
    .asSequence()
    .filter { it.isActive }
    .map { it.supplierDispatchTargetGoodsItemId() }
    .filter { it.isNotBlank() }
    .map { it.lowercase() }
    .toSet()

private fun SupplierOrderWithLinesDataModel.isContractBlockedForSupplierDispatch(
    contracts: List<SupplierPartnershipContractDataModel>
): Boolean {
    val cleanStoreId = order.storeId.trim()
    val cleanSupplierId = order.supplierId.trim()
    if (cleanStoreId.isBlank() || cleanSupplierId.isBlank()) return false
    val goodsIds = supplierDispatchGoodsItemIds()

    return contracts.any { contract ->
        contract.storeId.trim().equals(cleanStoreId, ignoreCase = true) &&
                contract.supplierId.trim().equals(cleanSupplierId, ignoreCase = true) &&
                contract.blocksSupplierSupplyForGoods(goodsIds)
    }
}

private fun SupplierOrderDataModel.supplierDispatchActivityMillis(): Long =
    updatedAtMillis.takeIf { it > 0L }
        ?: orderedAtMillis.takeIf { it > 0L }
        ?: createdAtMillis

private fun AppConfiguration.supplierDispatchGoodsPreview(
    bundles: List<SupplierOrderWithLinesDataModel>,
    limit: Int = 5
): String = bundles
    .flatMap { it.lines }
    .filter { it.isActive }
    .groupBy { line ->
        line.supplierDispatchTargetGoodsItemId()
            .ifBlank { line.substituteGoodsItemBarcodeSnapshots.firstOrNull().orEmpty() }
            .ifBlank { line.goodsItemBarcodeSnapshots.firstOrNull().orEmpty() }
            .ifBlank { line.id }
    }
    .values
    .take(limit)
    .joinToString(" • ") { itemLines ->
        val first = itemLines.first()
        val quantity = itemLines.sumOf { it.supplierDeskFulfillmentQuantity().total.coerceAtLeast(0.0) }
        val quantityText = if (quantity > 0.0) {
            first.supplierDeskFulfillmentQuantity().copy(total = quantity).quantityText(stateValues.appLanguage)
        } else {
            itemLines.size.toString()
        }
        "${supplierDeskFulfillmentLineTitle(first)} × $quantityText"
    }
    .ifBlank { localizedStringResource(1566, "No goods lines yet") }

private fun AppConfiguration.supplierDispatchPackChecklist(
    bundles: List<SupplierOrderWithLinesDataModel>,
    actionableOrderIds: Set<String>
): String = bundles
    .flatMap { it.lines }
    .filter { line ->
        line.isActive &&
                line.orderId.trim().lowercase() in actionableOrderIds &&
                line.supplierDeskFulfillmentQuantity().total > 0.0
    }
    .groupBy { line ->
        line.supplierDispatchTargetGoodsItemId()
            .ifBlank { line.substituteGoodsItemBarcodeSnapshots.firstOrNull().orEmpty() }
            .ifBlank { line.goodsItemBarcodeSnapshots.firstOrNull().orEmpty() }
            .ifBlank { line.id }
    }
    .values
    .sortedBy { itemLines -> supplierDeskFulfillmentLineTitle(itemLines.first()).lowercase() }
    .take(12)
    .joinToString("\n") { itemLines ->
        val first = itemLines.first()
        val quantity = itemLines.sumOf { it.supplierDeskFulfillmentQuantity().total.coerceAtLeast(0.0) }
        val quantityText = first.supplierDeskFulfillmentQuantity().copy(total = quantity)
            .quantityText(stateValues.appLanguage)
        val barcode = first.substituteGoodsItemBarcodeSnapshots.firstOrNull()
            ?: first.goodsItemBarcodeSnapshots.firstOrNull()
        buildString {
            append("□ ").append(supplierDeskFulfillmentLineTitle(first)).append(" × ").append(quantityText)
            barcode?.trim()?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
        }
    }

private fun supplierDispatchStatusMix(
    bundles: List<SupplierOrderWithLinesDataModel>
): List<SupplierDashboardStatusBucketDataModel> = SupplierOrderStatusDataModel.entries.mapNotNull { status ->
    val matching = bundles.filter { it.order.status == status }
    if (matching.isEmpty()) null else SupplierDashboardStatusBucketDataModel(
        status = status,
        orderCount = matching.size,
        lineCount = matching.sumOf { bundle -> bundle.lines.count { it.isActive } }
    )
}

private fun supplierDispatchEstimatedAmount(
    bundles: List<SupplierOrderWithLinesDataModel>
): PriceDataModel? {
    val pricedOrders = bundles.mapNotNull { bundle ->
        val price = bundle.order.amount?.takeIf { it.hasPositiveSupplierDeskPrice() } ?: return@mapNotNull null
        price.price.toMoneyDouble() to price.currency.trim().uppercase().ifBlank { "KZT" }
    }
    if (pricedOrders.isEmpty()) return null
    val currencies = pricedOrders.map { it.second }.distinct()
    // A single number with one currency would be misleading when a run contains mixed currencies.
    if (currencies.size != 1) return null
    val total = pricedOrders.sumOf { it.first }
    return total.takeIf { it.isFinite() && it > 0.0 }?.let {
        PriceDataModel(it.toStockMoneyText(), currencies.single(), "")
    }
}

private fun AppConfiguration.buildLocalSupplierDispatchRun(
    key: String,
    bundles: List<SupplierOrderWithLinesDataModel>,
    contracts: List<SupplierPartnershipContractDataModel>,
    serverPlanned: Boolean = false,
    contractSafetyReady: Boolean = true
): SupplierDispatchWorkspaceRunUiModel {
    val orderedBundles = bundles
        .filter { it.order.id.isNotBlank() }
        .distinctBy { it.order.id.trim().lowercase() }
        .sortedWith(
            compareBy<SupplierOrderWithLinesDataModel> { it.order.supplierDueAtMillis() ?: Long.MAX_VALUE }
                .thenByDescending { it.order.supplierDispatchActivityMillis() }
        )
    val newestBundle = orderedBundles.maxByOrNull { it.order.supplierDispatchActivityMillis() }
    val newestOrder = newestBundle?.order
    val orderIds = cleanSupplierDispatchOrderIds(orderedBundles.map { it.order.id })
    val blockedIds = if (contractSafetyReady) {
        cleanSupplierDispatchOrderIds(
            orderedBundles.filter { it.isContractBlockedForSupplierDispatch(contracts) }.map { it.order.id }
        )
    } else {
        emptyList()
    }
    val blockedSet = blockedIds.map { it.lowercase() }.toSet()
    val packableStatusIds = cleanSupplierDispatchOrderIds(
        orderedBundles.filter { it.isSupplierReadyToPackForSupplierDesk() }.map { it.order.id }
    )
    val dispatchableStatusIds = cleanSupplierDispatchOrderIds(
        orderedBundles.filter { it.order.status == SupplierOrderStatusDataModel.Packed }.map { it.order.id }
    )
    val packableIds = if (contractSafetyReady) {
        packableStatusIds.filterNot { it.lowercase() in blockedSet }
    } else {
        emptyList()
    }
    val dispatchableIds = if (contractSafetyReady) {
        dispatchableStatusIds.filterNot { it.lowercase() in blockedSet }
    } else {
        emptyList()
    }
    val inDeliveryIds = cleanSupplierDispatchOrderIds(
        orderedBundles.filter { it.order.status == SupplierOrderStatusDataModel.InDelivery }.map { it.order.id }
    )
    val issueIds = cleanSupplierDispatchOrderIds(
        orderedBundles.filter { it.order.status == SupplierOrderStatusDataModel.IssueReported }.map { it.order.id }
    )
    val actionIds = cleanSupplierDispatchOrderIds(
        orderedBundles.filter { it.needsSupplierActionForSupplierDesk() }.map { it.order.id } + blockedIds
    )
    val runGoodsIds = orderedBundles.flatMap { it.supplierDispatchGoodsItemIds() }.toSet()
    val relatedContracts = contracts.filter { contract ->
        newestOrder != null &&
                contract.storeId.trim().equals(newestOrder.storeId.trim(), ignoreCase = true) &&
                contract.supplierId.trim().equals(newestOrder.supplierId.trim(), ignoreCase = true) &&
                contract.coversSupplierSupplyGoods(runGoodsIds)
    }
    val dueValues = orderedBundles.mapNotNull { it.order.supplierDueAtMillis() }
    val priority = issueIds.size * 40 +
            blockedIds.size * 28 +
            actionIds.size * 20 +
            (if (contractSafetyReady) packableIds.size else packableStatusIds.size) * 16 +
            (if (contractSafetyReady) dispatchableIds.size else dispatchableStatusIds.size) * 14 +
            inDeliveryIds.size * 8 +
            orderedBundles.size * 3
    val suggestedAction = when {
        issueIds.isNotEmpty() -> "issue"
        blockedIds.isNotEmpty() -> "contract"
        actionIds.any { it.lowercase() !in blockedSet } -> "answer"
        !contractSafetyReady && (packableStatusIds.isNotEmpty() || dispatchableStatusIds.isNotEmpty()) -> "check"
        packableIds.isNotEmpty() -> "pack"
        dispatchableIds.isNotEmpty() -> "dispatch"
        inDeliveryIds.isNotEmpty() -> "delivery"
        else -> "plan"
    }
    // Goods already in delivery belong in the route manifest, but not in a packing checklist.
    // Keeping them here would tell warehouse staff to pack a load that has already left.
    val actionableForChecklist = (packableIds + dispatchableIds)
        .map { it.lowercase() }
        .toSet()
    val storeTitle = newestOrder?.let { supplierDeskStoreTitle(it) }.orEmpty()
        .ifBlank { newestOrder?.storePublicIdSnapshot.orEmpty() }
        .ifBlank { newestOrder?.storeId.orEmpty().take(12) }
        .ifBlank { localizedStringResource(2477, "Store delivery run") }
    val storePublicId = newestOrder?.storePublicIdSnapshot.orEmpty()
    val storeAddress = newestOrder?.storeAddressTextSnapshot.orEmpty()
    val goodsPreview = supplierDispatchGoodsPreview(orderedBundles)
    val packChecklist = supplierDispatchPackChecklist(orderedBundles, actionableForChecklist)
    val attentionSummary = buildList {
        if (issueIds.isNotEmpty()) add("${localizedStringResource(1473, "Issues")}: ${issueIds.size}")
        if (blockedIds.isNotEmpty()) add("${localizedStringResource(1791, "Contract-blocked orders")}: ${blockedIds.size}")
        val issueIdSet = issueIds.map { it.lowercase() }.toSet()
        val responseActionCount = actionIds.count {
            it.lowercase() !in blockedSet && it.lowercase() !in issueIdSet
        }
        if (responseActionCount > 0) add("${localizedStringResource(1371, "Needs attention")}: $responseActionCount")
    }.joinToString("\n")
    val driverHandoff = buildList {
        add("□ ${localizedStringResource(2478, "Orders in this run")}: ${orderIds.size}")
        storeAddress.takeIf { it.isNotBlank() }?.let { add("□ ${localizedStringResource(147, "Address")}: $it") }
        if (packableIds.isNotEmpty()) add("□ ${localizedStringResource(1689, "Ready to pack")}: ${packableIds.size}")
        if (dispatchableIds.isNotEmpty()) add("□ ${localizedStringResource(2442, "Ready to dispatch")}: ${dispatchableIds.size}")
        if (inDeliveryIds.isNotEmpty()) add("□ ${localizedStringResource(1560, "In delivery")}: ${inDeliveryIds.size}")
    }.joinToString("\n")
    val searchKey = buildString {
        append(key).append(' ')
        append(newestOrder?.storeId.orEmpty()).append(' ')
        append(storePublicId).append(' ')
        append(storeTitle).append(' ')
        append(storeAddress).append(' ')
        append(goodsPreview).append(' ')
        orderedBundles.forEach { bundle ->
            append(bundle.order.id).append(' ')
            append(bundle.order.status.name).append(' ')
            append(supplierOrderStatusTitle(bundle.order.status)).append(' ')
            append(bundle.order.additionalNotes.orEmpty()).append(' ')
            append(bundle.order.supplierComment.orEmpty()).append(' ')
            bundle.lines.forEach { line ->
                append(line.goodsItemId).append(' ')
                append(line.substituteGoodsItemId.orEmpty()).append(' ')
                append(supplierDeskLineTitle(line)).append(' ')
                append(supplierDeskFulfillmentLineTitle(line)).append(' ')
                append(line.goodsItemBarcodeSnapshots.joinToString(" ")).append(' ')
                append(line.substituteGoodsItemBarcodeSnapshots.joinToString(" ")).append(' ')
            }
        }
    }.lowercase()

    return SupplierDispatchWorkspaceRunUiModel(
        key = key,
        supplierId = newestOrder?.supplierId.orEmpty(),
        storeId = newestOrder?.storeId.orEmpty(),
        storePublicId = storePublicId,
        storeTitle = storeTitle,
        storeAddress = storeAddress,
        orderIds = orderIds,
        bundles = orderedBundles,
        packableOrderIds = packableIds,
        dispatchableOrderIds = dispatchableIds,
        inDeliveryOrderIds = inDeliveryIds,
        issueOrderIds = issueIds,
        attentionOrderIds = actionIds,
        contractBlockedOrderIds = blockedIds,
        statusMix = supplierDispatchStatusMix(orderedBundles),
        orderCount = orderIds.size,
        lineCount = orderedBundles.sumOf { bundle -> bundle.lines.count { it.isActive } },
        activeContractCount = relatedContracts.count { it.status == SUPPLIER_CONTRACT_STATUS_ACTIVE },
        pendingContractCount = relatedContracts.count { it.status.isPendingSupplierContractStatus() },
        contractSafetyReady = contractSafetyReady,
        earliestDueAtMillis = dueValues.minOrNull(),
        latestDueAtMillis = dueValues.maxOrNull(),
        latestActivityMillis = orderedBundles.maxOfOrNull { it.order.supplierDispatchActivityMillis() } ?: 0L,
        goodsPreview = goodsPreview,
        packChecklist = packChecklist,
        attentionSummary = attentionSummary,
        driverHandoff = driverHandoff,
        estimatedAmount = supplierDispatchEstimatedAmount(orderedBundles),
        priorityScore = priority,
        suggestedAction = suggestedAction,
        serverPlanned = serverPlanned,
        searchKey = searchKey
    )
}

internal fun AppConfiguration.buildSupplierDispatchWorkspaceRuns(
    orders: List<SupplierOrderDataModel>,
    lines: List<SupplierOrderLineDataModel>,
    contracts: List<SupplierPartnershipContractDataModel>,
    serverRuns: List<SupplierDashboardDispatchRunDataModel>,
    ordersLoaded: Boolean = true,
    linesLoaded: Boolean = true,
    contractsLoaded: Boolean = true
): List<SupplierDispatchWorkspaceRunUiModel> {
    val activeOrders = orders.filter { order ->
        order.isActive &&
                order.id.isNotBlank() &&
                order.status != SupplierOrderStatusDataModel.Draft &&
                !order.status.isSupplierOrderClosed()
    }
    val activeLinesByOrderId = lines
        .filter { it.isActive }
        .groupBy { it.orderId.trim().lowercase() }
    val localBundlesByOrderId = activeOrders.associate { order ->
        order.id.trim().lowercase() to SupplierOrderWithLinesDataModel(
            order = order,
            lines = activeLinesByOrderId[order.id.trim().lowercase()].orEmpty()
        )
    }

    val sortedServerRuns = serverRuns
        .filter { run ->
            run.orderIds.isNotEmpty() ||
                    run.packableOrderIds.isNotEmpty() ||
                    run.dispatchableOrderIds.isNotEmpty() ||
                    run.inDeliveryOrderIds.isNotEmpty() ||
                    run.issueOrderIds.isNotEmpty() ||
                    run.attentionOrderIds.isNotEmpty() ||
                    run.contractBlockedOrderIds.isNotEmpty()
        }
        .sortedWith(
            compareByDescending<SupplierDashboardDispatchRunDataModel> { it.priorityScore }
                .thenBy { it.earliestDueAtMillis ?: Long.MAX_VALUE }
                .thenByDescending { it.latestActivityMillis }
        )
    val claimedServerOrderIds = claimSupplierDispatchServerOrderIds(sortedServerRuns)
    val coveredOrderIds = mutableSetOf<String>()
    val usedServerRunKeys = mutableSetOf<String>()

    val serverWorkspaceRuns = sortedServerRuns.mapIndexedNotNull { index, serverRun ->
        val claimedOrderIds = claimedServerOrderIds[index]
        val orderIds = supplierDispatchOrderIdsForLoadedState(
            claimedOrderIds = claimedOrderIds,
            locallyOpenOrderIds = localBundlesByOrderId.keys,
            ordersLoaded = ordersLoaded
        )
        if (orderIds.isEmpty()) return@mapIndexedNotNull null
        coveredOrderIds.addAll(orderIds.map { it.lowercase() })
        val bundles = orderIds.mapNotNull { localBundlesByOrderId[it.lowercase()] }
        val serverKeyBase = serverRun.runId.trim().ifBlank {
            val storeIdentity = serverRun.storeId.trim()
                .ifBlank { serverRun.storePublicIdSnapshot.trim() }
                .ifBlank { "anonymous-store-$index" }
            "server:${serverRun.supplierId.trim()}|$storeIdentity|${supplierDispatchDayKey(serverRun.earliestDueAtMillis)}|${orderIds.firstOrNull().orEmpty().lowercase()}"
        }
        val serverKey = if (usedServerRunKeys.add(serverKeyBase)) {
            serverKeyBase
        } else {
            // A malformed/stale dashboard must never make a second run vanish merely because it
            // reused another run ID. Keep normal server IDs stable and disambiguate only collisions.
            val duplicateKey = "$serverKeyBase|duplicate:$index|${orderIds.firstOrNull().orEmpty().lowercase()}"
            usedServerRunKeys.add(duplicateKey)
            duplicateKey
        }
        val localBase = buildLocalSupplierDispatchRun(
            key = serverKey,
            bundles = bundles,
            contracts = contracts,
            serverPlanned = true,
            contractSafetyReady = contractsLoaded
        )
        val loadedIds = bundles.map { it.order.id.trim().lowercase() }.toSet()
        val unloadedOrderIds = orderIds.filter { it.lowercase() !in loadedIds }
        val loadedPackableStatusIds = bundles
            .filter { it.isSupplierReadyToPackForSupplierDesk() }
            .map { it.order.id.trim().lowercase() }
            .toSet()
        val loadedDispatchableStatusIds = bundles
            .filter { it.order.status == SupplierOrderStatusDataModel.Packed }
            .map { it.order.id.trim().lowercase() }
            .toSet()
        val serverBlockedIds = safeSupplierDispatchActionOrderIds(
            runOrderIds = orderIds,
            requestedActionOrderIds = serverRun.contractBlockedOrderIds,
            contractBlockedOrderIds = emptyList()
        )
        val blockedIds = cleanSupplierDispatchOrderIds(
            if (contractsLoaded) {
                localBase.contractBlockedOrderIds + serverBlockedIds.filter { it.lowercase() !in loadedIds }
            } else {
                serverBlockedIds
            }
        )
        val serverPackableIds = safeSupplierDispatchActionOrderIds(
            runOrderIds = orderIds,
            requestedActionOrderIds = serverRun.packableOrderIds,
            contractBlockedOrderIds = blockedIds
        )
        val serverDispatchableIds = safeSupplierDispatchActionOrderIds(
            runOrderIds = orderIds,
            requestedActionOrderIds = serverRun.dispatchableOrderIds,
            contractBlockedOrderIds = blockedIds
        )
        val packableIds = cleanSupplierDispatchOrderIds(
            if (contractsLoaded) {
                localBase.packableOrderIds + serverPackableIds.filter { it.lowercase() !in loadedIds }
            } else {
                serverPackableIds.filter { id ->
                    id.lowercase() !in loadedIds || id.lowercase() in loadedPackableStatusIds
                }
            }
        )
        val dispatchableIds = cleanSupplierDispatchOrderIds(
            if (contractsLoaded) {
                localBase.dispatchableOrderIds + serverDispatchableIds.filter { it.lowercase() !in loadedIds }
            } else {
                serverDispatchableIds.filter { id ->
                    id.lowercase() !in loadedIds || id.lowercase() in loadedDispatchableStatusIds
                }
            }
        )
        val inDeliveryIds = cleanSupplierDispatchOrderIds(
            localBase.inDeliveryOrderIds + safeSupplierDispatchActionOrderIds(
                runOrderIds = unloadedOrderIds,
                requestedActionOrderIds = serverRun.inDeliveryOrderIds,
                contractBlockedOrderIds = emptyList()
            )
        )
        val issueIds = cleanSupplierDispatchOrderIds(
            localBase.issueOrderIds + safeSupplierDispatchActionOrderIds(
                runOrderIds = unloadedOrderIds,
                requestedActionOrderIds = serverRun.issueOrderIds,
                contractBlockedOrderIds = emptyList()
            )
        )
        val attentionIds = cleanSupplierDispatchOrderIds(
            localBase.attentionOrderIds +
                    safeSupplierDispatchActionOrderIds(
                        runOrderIds = unloadedOrderIds,
                        requestedActionOrderIds = serverRun.attentionOrderIds,
                        contractBlockedOrderIds = emptyList()
                    ) + blockedIds + issueIds
        )
        val blockedIdSet = blockedIds.map { it.lowercase() }.toSet()
        val serverTitle = serverRun.storeNameSnapshot.visibleLocalizedString(stateValues.appLanguage, "")
            .ifBlank { serverRun.storePublicIdSnapshot }
            .ifBlank { serverRun.storeId.take(12) }
            .ifBlank { localizedStringResource(2477, "Store delivery run") }
        val localOrdersComplete = ordersLoaded && bundles.size == orderIds.size
        val localLinesComplete = localOrdersComplete && linesLoaded
        val goodsPreview = if (localLinesComplete) {
            localBase.goodsPreview
        } else {
            serverRun.goodsPreview.visibleLocalizedString(stateValues.appLanguage, "")
                .ifBlank { localBase.goodsPreview }
        }
        val packChecklist = if (localLinesComplete) {
            localBase.packChecklist
        } else {
            serverRun.packChecklist.visibleLocalizedString(stateValues.appLanguage, "")
                .ifBlank { localBase.packChecklist }
        }
        val localOperationsComplete = localOrdersComplete && contractsLoaded
        val attentionSummary = if (localOperationsComplete) {
            localBase.attentionSummary
        } else {
            serverRun.attentionSummary.visibleLocalizedString(stateValues.appLanguage, "")
                .ifBlank { localBase.attentionSummary }
        }
        val handoff = if (localOrdersComplete) {
            localBase.driverHandoff
        } else {
            serverRun.driverHandoffChecklist.visibleLocalizedString(stateValues.appLanguage, "")
                .ifBlank { localBase.driverHandoff }
        }
        val searchKey = buildString {
            append(localBase.searchKey).append(' ')
            append(serverRun.runId).append(' ')
            append(serverRun.supplierId).append(' ')
            append(serverRun.storeId).append(' ')
            append(serverRun.storePublicIdSnapshot).append(' ')
            append(serverTitle).append(' ')
            append(serverRun.storeAddressTextSnapshot).append(' ')
            append(goodsPreview).append(' ')
            append(packChecklist).append(' ')
            append(attentionSummary).append(' ')
            append(handoff).append(' ')
            append(orderIds.joinToString(" "))
        }.lowercase()

        localBase.copy(
            key = serverKey,
            serverRunId = serverRun.runId,
            supplierId = localBase.supplierId.ifBlank { serverRun.supplierId },
            storeId = localBase.storeId.ifBlank { serverRun.storeId },
            storePublicId = localBase.storePublicId.ifBlank { serverRun.storePublicIdSnapshot },
            storeTitle = if (bundles.isNotEmpty()) localBase.storeTitle.ifBlank { serverTitle } else serverTitle,
            storeAddress = localBase.storeAddress.ifBlank { serverRun.storeAddressTextSnapshot },
            orderIds = orderIds,
            bundles = bundles,
            packableOrderIds = packableIds,
            dispatchableOrderIds = dispatchableIds,
            inDeliveryOrderIds = inDeliveryIds,
            issueOrderIds = issueIds,
            attentionOrderIds = attentionIds,
            contractBlockedOrderIds = blockedIds,
            statusMix = if (localOrdersComplete) localBase.statusMix else serverRun.statusMix,
            orderCount = orderIds.size,
            lineCount = if (localLinesComplete) localBase.lineCount else maxOf(localBase.lineCount, serverRun.lineCount),
            activeContractCount = if (contractsLoaded && localOrdersComplete) {
                localBase.activeContractCount
            } else {
                serverRun.activeContractCount
            },
            pendingContractCount = if (contractsLoaded && localOrdersComplete) {
                localBase.pendingContractCount
            } else {
                serverRun.pendingContractCount
            },
            // Server-planned action IDs are useful while contracts load, but the buttons stay
            // disabled until the current agreement payload is present. The server still rechecks
            // every transition, so this is both conservative and race-safe.
            contractSafetyReady = contractsLoaded,
            earliestDueAtMillis = if (localOrdersComplete) {
                localBase.earliestDueAtMillis
            } else {
                localBase.earliestDueAtMillis ?: serverRun.earliestDueAtMillis
            },
            latestDueAtMillis = if (localOrdersComplete) {
                localBase.latestDueAtMillis
            } else {
                localBase.latestDueAtMillis ?: serverRun.latestDueAtMillis
            },
            latestActivityMillis = if (localOrdersComplete) {
                localBase.latestActivityMillis
            } else {
                maxOf(localBase.latestActivityMillis, serverRun.latestActivityMillis)
            },
            goodsPreview = goodsPreview,
            packChecklist = packChecklist,
            attentionSummary = attentionSummary,
            driverHandoff = handoff,
            estimatedAmount = if (localOrdersComplete) {
                localBase.estimatedAmount
            } else {
                localBase.estimatedAmount ?: serverRun.estimatedAmount
            },
            priorityScore = if (localOperationsComplete) {
                localBase.priorityScore
            } else {
                maxOf(localBase.priorityScore, serverRun.priorityScore)
            },
            suggestedAction = when {
                issueIds.isNotEmpty() -> "issue"
                blockedIds.isNotEmpty() -> "contract"
                attentionIds.any { id -> id.lowercase() !in blockedIdSet } -> "answer"
                !contractsLoaded && (packableIds.isNotEmpty() || dispatchableIds.isNotEmpty()) -> "check"
                packableIds.isNotEmpty() -> "pack"
                dispatchableIds.isNotEmpty() -> "dispatch"
                inDeliveryIds.isNotEmpty() -> "delivery"
                else -> serverRun.suggestedAction.ifBlank { "plan" }
            },
            serverPlanned = true,
            searchKey = searchKey
        )
    }

    val localFallbackGroups = activeOrders
        .mapIndexedNotNull { index, order ->
            val normalizedOrderId = order.id.trim().lowercase()
            if (normalizedOrderId in coveredOrderIds) null else {
                val supplierKey = order.supplierId.trim().lowercase().ifBlank { "unknown-supplier" }
                val storeKey = order.supplierDispatchStableStoreIdentity(index)
                val dueKey = supplierDispatchDayKey(order.supplierDueAtMillis())
                "$supplierKey|$storeKey|$dueKey" to normalizedOrderId
            }
        }
        .groupBy({ it.first }, { it.second })

    val fallbackRuns = localFallbackGroups.mapNotNull { (groupKey, orderIds) ->
        val bundles = orderIds.mapNotNull { localBundlesByOrderId[it] }
        if (bundles.isEmpty()) null else buildLocalSupplierDispatchRun(
            key = "local:$groupKey",
            bundles = bundles,
            contracts = contracts,
            serverPlanned = false,
            contractSafetyReady = contractsLoaded
        )
    }

    return (serverWorkspaceRuns + fallbackRuns)
        .filter { it.orderIds.isNotEmpty() }
        .distinctBy { it.key }
        .sortedForSupplierDispatch(SUPPLIER_DISPATCH_SORT_ACTION)
}

internal fun AppConfiguration.supplierDispatchSuggestedActionTitle(action: String): String = when (action) {
    "issue" -> localizedStringResource(1742, "Resolve issue")
    "contract" -> localizedStringResource(1737, "Clear contract")
    "answer" -> localizedStringResource(1736, "Answer store")
    "check" -> localizedStringResource(1562, "Contract check")
    "pack" -> localizedStringResource(1738, "Pack goods")
    "dispatch" -> localizedStringResource(1739, "Send driver")
    "delivery" -> localizedStringResource(1740, "Track delivery")
    else -> localizedStringResource(1741, "Plan route")
}

internal fun AppConfiguration.supplierDispatchRunManifest(
    run: SupplierDispatchWorkspaceRunUiModel
): String = buildString {
    append(localizedStringResource(2477, "Store delivery run")).append(" — ").append(run.storeTitle).append('\n')
    run.storePublicId.takeIf { it.isNotBlank() }?.let { append("ID: ").append(it).append('\n') }
    run.storeAddress.takeIf { it.isNotBlank() }?.let {
        append(localizedStringResource(147, "Address")).append(": ").append(it).append('\n')
    }
    append(localizedStringResource(2478, "Orders in this run")).append(": ").append(run.orderIds.size).append('\n')
    append(localizedStringResource(1564, "Goods lines")).append(": ").append(run.lineCount).append('\n')
    run.earliestDueAtMillis?.takeIf { it > 0L }?.let {
        append(localizedStringResource(1723, "Earliest due")).append(": ").append(receiptUiDateTime(it)).append('\n')
    }
    run.estimatedAmount.supplierDeskMoneyText().takeIf { it.isNotBlank() }?.let {
        append(localizedStringResource(581, "Amount")).append(": ").append(it).append('\n')
    }
    append(localizedStringResource(1689, "Ready to pack")).append(": ").append(run.readyToPackCount).append('\n')
    append(localizedStringResource(2442, "Ready to dispatch")).append(": ").append(run.readyToDispatchCount).append('\n')
    append(localizedStringResource(1560, "In delivery")).append(": ").append(run.inDeliveryCount).append('\n')
    if (run.contractBlockedOrderIds.isNotEmpty()) {
        append(localizedStringResource(1791, "Contract-blocked orders")).append(": ").append(run.contractBlockedOrderIds.size).append('\n')
    }
    run.goodsPreview.takeIf { it.isNotBlank() }?.let { append('\n').append(it).append('\n') }
    run.packChecklist.takeIf { it.isNotBlank() }?.let {
        append('\n').append(localizedStringResource(1746, "Pack checklist")).append(":\n").append(it).append('\n')
    }
    run.attentionSummary.takeIf { it.isNotBlank() }?.let {
        append('\n').append(localizedStringResource(1756, "Attention notes")).append(":\n").append(it).append('\n')
    }
    run.driverHandoff.takeIf { it.isNotBlank() }?.let {
        append('\n').append(localizedStringResource(1757, "Driver handoff")).append(":\n").append(it).append('\n')
    }
    append('\n').append(localizedStringResource(254, "Orders")).append(": ")
    append(run.orderIds.joinToString(", ") { it.take(12) })
}
