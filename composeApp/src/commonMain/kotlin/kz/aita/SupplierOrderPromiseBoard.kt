package kz.aita

private val supplierPromiseBucketRank = mapOf(
    "overdue" to 0,
    "today" to 1,
    "tomorrow" to 2,
    "week" to 3,
    "later" to 4,
    "unscheduled" to 5,
)

/**
 * Rebuilds the Supplier Orders promise radar from the currently visible order snapshot.
 *
 * The server dashboard remains useful enrichment, but its generated-at timestamp must not freeze a
 * calendar boundary in a client that stays open overnight. Once the detailed order payload has
 * arrived, these local buckets become authoritative for counts and due lanes while server titles and
 * previews are retained as optional presentation enrichment.
 */
internal fun buildSupplierOrderPromiseBuckets(
    orders: List<SupplierOrderDataModel>,
    lines: List<SupplierOrderLineDataModel>,
    nowMillis: Long,
    serverBuckets: List<SupplierDashboardDeliveryBucketDataModel> = emptyList(),
): List<SupplierDashboardDeliveryBucketDataModel> {
    val serverById = serverBuckets
        .filter { it.bucketId.isNotBlank() }
        .associateBy { it.bucketId }
    val activeLinesByOrder = lines
        .asSequence()
        .filter { it.isActive && it.orderId.isNotBlank() }
        .groupBy { it.orderId.trim().lowercase() }

    val seenOrderIds = mutableSetOf<String>()
    val openOrders = orders.filter { order ->
        if (!order.isActive || order.status == SupplierOrderStatusDataModel.Draft || order.status.isClosedForSupplierDesk()) {
            return@filter false
        }
        val stableId = order.id.trim().lowercase()
        stableId.isBlank() || seenOrderIds.add(stableId)
    }

    return openOrders
        .groupBy { order -> supplierUiDeliveryBucketId(nowMillis, order.supplierDueAtMillis()) }
        .map { (bucketId, bucketOrders) ->
            val orderIds = bucketOrders
                .map { it.id.trim().lowercase() }
                .filter { it.isNotBlank() }
                .toSet()
            val bucketLines = activeLinesByOrder
                .asSequence()
                .filter { (orderId) -> orderId in orderIds }
                .flatMap { (_, orderLines) -> orderLines.asSequence() }
                .toList()
            val linesByOrder = bucketLines.groupBy { it.orderId.trim().lowercase() }
            val dueValues = bucketOrders.mapNotNull { it.supplierDueAtMillis() }
            val enriched = serverById[bucketId]
            val localPreview = bucketLines
                .asSequence()
                .distinctBy { line ->
                    line.goodsItemId.trim().lowercase()
                        .ifBlank { line.goodsItemBarcodeSnapshots.firstOrNull().orEmpty().trim().lowercase() }
                        .ifBlank { line.id.trim().lowercase() }
                }
                .take(4)
                .map { line ->
                    line.goodsItemNameSnapshot.firstOrNull { it.value.isNotBlank() }?.value
                        ?: line.goodsItemBarcodeSnapshots.firstOrNull { it.isNotBlank() }
                        ?: ""
                }
                .filter { it.isNotBlank() }
                .joinToString(" • ")

            SupplierDashboardDeliveryBucketDataModel(
                bucketId = bucketId,
                title = enriched?.title.orEmpty(),
                orderCount = bucketOrders.size,
                lineCount = bucketLines.size,
                storeCount = bucketOrders
                    .map { it.storeId.trim().lowercase() }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .size,
                actionRequiredOrderCount = bucketOrders.count { order ->
                    order.needsSupplierActionForSupplierDesk(
                        linesByOrder[order.id.trim().lowercase()].orEmpty()
                    )
                },
                packedOrderCount = bucketOrders.count { it.status == SupplierOrderStatusDataModel.Packed },
                inDeliveryOrderCount = bucketOrders.count { it.status == SupplierOrderStatusDataModel.InDelivery },
                issueOrderCount = bucketOrders.count { it.status == SupplierOrderStatusDataModel.IssueReported },
                earliestDueAtMillis = dueValues.minOrNull(),
                latestDueAtMillis = dueValues.maxOrNull(),
                goodsPreview = localPreview
                    .takeIf { it.isNotBlank() }
                    ?.let { listOf(LocalizedStringDataModel("main", it)) }
                    ?: enriched?.goodsPreview.orEmpty(),
            )
        }
        .sortedWith(
            compareBy<SupplierDashboardDeliveryBucketDataModel> {
                supplierPromiseBucketRank[it.bucketId] ?: Int.MAX_VALUE
            }
                .thenByDescending { it.actionRequiredOrderCount }
                .thenByDescending { it.orderCount }
        )
}
