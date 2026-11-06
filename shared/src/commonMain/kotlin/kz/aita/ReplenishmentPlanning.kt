package kz.aita

import kotlin.math.ceil
import kotlin.math.max
import kotlin.time.Instant
import kotlinx.datetime.*
import kotlinx.serialization.Serializable

@Serializable data class ReplenishmentStock(val quantity: Double, val expiresAtMillis: Long? = null)

@Serializable
data class ReplenishmentArrival(
    val quantity: Double,
    val arrivesAtMillis: Long?,
    val expiresAtMillis: Long? = null,
)

@Serializable
data class ReplenishmentItem(
    val goodsItem: GoodsItemDataModel,
    val unit: QuantityDataModel?,
    val sold: Double,
    val returned: Double,
    val observationDays: Int,
    val saleDays: Int,
    val stock: List<ReplenishmentStock>,
    val incoming: List<ReplenishmentArrival>,
    val unlimited: Boolean = false,
    val uncertainUnits: Boolean = false,
)

@Serializable
data class ReplenishmentSnapshot(
    val storeId: String,
    val generatedAtMillis: Long,
    val startMillis: Long,
    val endMillisExclusive: Long,
    val lookbackDays: Int,
    val timeZoneId: String,
    val items: List<ReplenishmentItem>,
    val unmatchedTransactionLines: Int = 0,
)

/**
 * Each snapshot is one store only. Completed calendar days avoid treating today's partial sales as
 * a complete day. Imported/new items get the actual observed interval, never fictional history.
 */
fun buildReplenishmentSnapshot(
    storeId: String,
    now: Long,
    lookbackDays: Int,
    timeZoneId: String,
    goods: List<GoodsItemDataModel>,
    batches: List<GoodsBatchDataModel>,
    transactions: List<TransactionDataModel>,
    orders: List<SupplierOrderDataModel>,
    lines: List<SupplierOrderLineDataModel>,
    units: List<QuantityDataModel>,
): ReplenishmentSnapshot {
    require(lookbackDays in setOf(7, 28, 90))
    val zone = TimeZone.of(timeZoneId)
    val today = Instant.fromEpochMilliseconds(now).toLocalDateTime(zone).date
    val start =
        today.minus(lookbackDays, DateTimeUnit.DAY).atStartOfDayIn(zone).toEpochMilliseconds()
    val end = today.atStartOfDayIn(zone).toEpochMilliseconds()
    val stock = goods.filter { it.isActive && it.storeId == storeId }.distinctBy { it.id }
    val byId = stock.associateBy { it.id }
    val byBarcode =
        stock
            .flatMap { item -> item.allBarcodeValues().map { it.normalizedBarcodeToken() to item } }
            .groupBy({ it.first }, { it.second })
    val sales = mutableMapOf<String, Double>()
    val returns = mutableMapOf<String, Double>()
    val days = mutableMapOf<String, MutableSet<String>>()
    val uncertain = mutableSetOf<String>()
    var unmatched = 0
    fun amount(item: GoodsItemDataModel, quantity: Double, fromUnit: String?): Double? {
        if (!quantity.isFinite() || quantity < 0) return null
        if (fromUnit.isNullOrBlank() || fromUnit == item.measurementUnitId) return quantity
        return when {
            fromUnit == "1" && item.measurementUnitId == GRAMS_UNIT_ID -> quantity * 1000
            fromUnit == GRAMS_UNIT_ID && item.measurementUnitId == "1" -> quantity / 1000
            else -> null
        }
    }
    transactions
        .asSequence()
        .filter {
            it.storeId == storeId &&
                it.type in setOf("purchase", "return") &&
                it.timeMillis in start until end
        }
        .distinctBy { it.clientOperationId.ifBlank { it.id } }
        .forEach { tx ->
            val date =
                Instant.fromEpochMilliseconds(tx.timeMillis).toLocalDateTime(zone).date.toString()
            tx.goodsInTransaction.forEach lineLoop@{ line ->
                val item =
                    line.goodsItemId?.takeIf(String::isNotBlank)?.let { byId[it] }
                        ?: if (line.goodsItemId.isNullOrBlank())
                            byBarcode[line.barcode.normalizedBarcodeToken()]
                                ?.distinctBy { it.id }
                                ?.singleOrNull()
                        else null
                if (item == null) {
                    unmatched++
                    return@lineLoop
                }
                val count = amount(item, line.quantity, line.quantityUnit?.id)
                if (count == null || !count.isFinite()) {
                    uncertain += item.id
                    return@lineLoop
                }
                val bucket = if (tx.type == "purchase") sales else returns
                bucket[item.id] = bucket.getOrElse(item.id) { 0.0 } + count
                if (tx.type == "purchase" && count > 0)
                    days.getOrPut(item.id) { mutableSetOf() }.add(date)
            }
        }
    val ownOrders =
        orders
            .filter {
                it.storeId == storeId &&
                    it.isActive &&
                    it.status in
                        setOf(
                            SupplierOrderStatusDataModel.Confirmed,
                            SupplierOrderStatusDataModel.Packed,
                            SupplierOrderStatusDataModel.InDelivery,
                            SupplierOrderStatusDataModel.PartiallyDelivered,
                        )
            }
            .associateBy { it.id }
    val linesByItem =
        lines
            .filter { it.isActive && it.orderId in ownOrders }
            .groupBy { it.substituteGoodsItemId?.takeIf(String::isNotBlank) ?: it.goodsItemId }
    val batchesByItem =
        batches
            .filter {
                it.storeId == storeId &&
                    it.isActive &&
                    it.status in
                        setOf(
                            StockBatchStatusDataModel.Delivered,
                            StockBatchStatusDataModel.OnShelf,
                        )
            }
            .groupBy { it.goodsItemId }
    return ReplenishmentSnapshot(
        storeId,
        now,
        start,
        end,
        lookbackDays,
        timeZoneId,
        stock.map { item ->
            val unit = units.firstOrNull { it.id == item.measurementUnitId }
            val ownBatches = batchesByItem[item.id].orEmpty()
            val available =
                ownBatches
                    .filter {
                        it.kind != StockBatchKindDataModel.UNLIMITED && !it.unlimitedQuantity
                    }
                    .mapNotNull { batch ->
                        val count = amount(item, batch.quantity.total, batch.quantity.id)
                        if (count == null) {
                            uncertain += item.id
                            null
                        } else ReplenishmentStock(count, batch.expirationDateMillis)
                    }
            val incoming =
                linesByItem[item.id].orEmpty().mapNotNull { line ->
                    val order = ownOrders.getValue(line.orderId)
                    val count =
                        amount(
                            item,
                            line.remainingReceivableQuantity(),
                            line.receivableQuantity().id,
                        )
                    if (count == null) {
                        uncertain += item.id
                        null
                    } else
                        ReplenishmentArrival(
                            count,
                            order.confirmedDeliveryTimeMillis ?: order.desiredDeliveryTimeMillis,
                            line.desiredExpirationDateMillis,
                        )
                }
            val createdDate =
                item.createdAtMillis
                    .takeIf { it > 0 }
                    ?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(zone).date }
            val observed =
                if (createdDate == null) lookbackDays
                else createdDate.daysUntil(today).coerceIn(0, lookbackDays)
            ReplenishmentItem(
                item,
                unit,
                sales[item.id] ?: 0.0,
                returns[item.id] ?: 0.0,
                observed,
                days[item.id]?.size ?: 0,
                available,
                incoming,
                ownBatches.any {
                    (it.kind == StockBatchKindDataModel.UNLIMITED || it.unlimitedQuantity) &&
                        (it.expirationDateMillis == null || it.expirationDateMillis > now)
                },
                item.id in uncertain || unit == null,
            )
        },
        unmatched,
    )
}

@Serializable
data class ReplenishmentSettings(
    val event: String = "regular",
    val arrivalDate: String = "",
    val leadDays: Int = 7,
    val coverDays: Int = 14,
    val bufferPercent: Int = 10,
    val upliftPercent: Int = 0,
    val lookbackDays: Int = 28,
    val categoryId: String = "",
)

data class ReplenishmentSuggestion(
    val item: ReplenishmentItem,
    val dailyQuantity: Double,
    val usableStock: Double,
    val expiringStock: Double,
    val confirmedIncoming: Double,
    val undatedIncoming: Double,
    val targetQuantity: Double,
    val suggestedQuantity: Double?,
    val orderBy: LocalDate,
    val lowHistory: Boolean,
)

fun ReplenishmentSettings.arrival(today: LocalDate): LocalDate =
    if (event == "regular") today.plus(leadDays, DateTimeUnit.DAY) else LocalDate.parse(arrivalDate)

fun ReplenishmentSettings.valid(today: LocalDate): Boolean = runCatching {
    leadDays in 0..180 &&
        coverDays in 1..90 &&
        bufferPercent in 0..100 &&
        upliftPercent in 0..300 &&
        lookbackDays in setOf(7, 28, 90) &&
        today.daysUntil(arrival(today)) in 0..366 &&
        event in setOf("regular", "new_year", "nauryz", "school", "kurban", "custom")
}
    .getOrDefault(false)

fun replenishmentEventDate(event: String, today: LocalDate): LocalDate? {
    val pair =
        when (event) {
            "new_year" -> 1 to 1
            "nauryz" -> 3 to 21
            "school" -> 9 to 1
            else -> return null
        }
    val date = LocalDate(today.year, pair.first, pair.second)
    return if (date < today) LocalDate(today.year + 1, pair.first, pair.second) else date
}

fun ReplenishmentSnapshot.suggest(
    settings: ReplenishmentSettings,
    planningAtMillis: Long = generatedAtMillis,
): List<ReplenishmentSuggestion> {
    val zone = TimeZone.of(timeZoneId)
    val today = Instant.fromEpochMilliseconds(planningAtMillis).toLocalDateTime(zone).date
    require(settings.valid(today))
    val arrival = settings.arrival(today)
    val untilArrival = today.daysUntil(arrival)
    val endDate = arrival.plus(settings.coverDays, DateTimeUnit.DAY)
    val end = endDate.atStartOfDayIn(zone).toEpochMilliseconds()
    return items
        .filter { settings.categoryId.isBlank() || settings.categoryId in it.goodsItem.categoryIds }
        .map { item ->
            val daily =
                if (item.observationDays > 0)
                    max(0.0, item.sold - item.returned) / item.observationDays
                else 0.0
            val usable =
                item.stock
                    .filter { it.expiresAtMillis == null || it.expiresAtMillis >= end }
                    .sumOf { it.quantity }
            val expiring =
                item.stock
                    .filter { it.expiresAtMillis != null && it.expiresAtMillis < end }
                    .sumOf { it.quantity }
            // A promise without a date is visible but cannot be assumed to arrive in time.
            val incoming =
                item.incoming
                    .filter {
                        it.arrivesAtMillis != null &&
                            it.arrivesAtMillis >= planningAtMillis &&
                            it.arrivesAtMillis < end &&
                            (it.expiresAtMillis == null || it.expiresAtMillis >= end)
                    }
                    .sumOf { it.quantity }
            val uncertainIncoming =
                item.incoming
                    .filter { it.arrivesAtMillis == null || it.arrivesAtMillis < planningAtMillis }
                    .sumOf { it.quantity }
            val target =
                daily *
                    (untilArrival + settings.coverDays * (1 + settings.upliftPercent / 100.0)) *
                    (1 + settings.bufferPercent / 100.0)
            val needed = max(0.0, target - usable - incoming)
            val rounded =
                if (needed == 0.0) 0.0
                else if (item.unit?.roundTotal == true) ceil(needed - 1e-8)
                else ceil(needed * 1000 - 1e-8) / 1000
            ReplenishmentSuggestion(
                item,
                daily,
                usable,
                expiring,
                incoming,
                uncertainIncoming,
                target,
                rounded.takeIf {
                    !item.unlimited &&
                        !item.uncertainUnits &&
                        item.observationDays > 0 &&
                        it.isFinite() &&
                        it <= 999_999_999
                },
                arrival.minus(settings.leadDays, DateTimeUnit.DAY),
                item.observationDays < 14 || item.saleDays < 3,
            )
        }
        .sortedWith(
            compareByDescending<ReplenishmentSuggestion> { it.suggestedQuantity ?: -1.0 }
                .thenBy { it.item.goodsItem.id }
        )
}

/** A selected line is always an explicit draft quantity, never an automatic order. */
fun replenishmentOrder(
    snapshot: ReplenishmentSnapshot,
    settings: ReplenishmentSettings,
    quantities: Map<String, Double>,
    supplierId: String,
    accountId: String,
    orderId: String,
    planningAtMillis: Long = snapshot.generatedAtMillis,
): SupplierOrderWithLinesDataModel {
    require(
        quantities.isNotEmpty() &&
            quantities.size <= 500 &&
            supplierId.isNotBlank() &&
            accountId.isNotBlank() &&
            orderId.isNotBlank()
    )
    val today =
        Instant.fromEpochMilliseconds(planningAtMillis)
            .toLocalDateTime(TimeZone.of(snapshot.timeZoneId))
            .date
    require(settings.valid(today))
    val items = snapshot.items.associateBy { it.goodsItem.id }
    val lines =
        quantities.entries
            .sortedBy { it.key }
            .map { (id, count) ->
                val item = requireNotNull(items[id])
                val unit = requireNotNull(item.unit)
                require(
                    !item.unlimited &&
                        !item.uncertainUnits &&
                        count.isFinite() &&
                        count > 0 &&
                        count <= 999_999_999 &&
                        (!unit.roundTotal || count % 1.0 == 0.0)
                )
                val prices =
                    item.goodsItem.supplyPrices
                        .filter { it.supplierId == supplierId }
                        .ifEmpty { item.goodsItem.supplyPrices.filter { it.supplierId.isBlank() } }
                val price =
                    prices.singleOrNull()?.takeIf {
                        it.price.replace(',', '.').toDoubleOrNull()?.let { n ->
                            n.isFinite() && n >= 0.0
                        } == true && it.currency.length == 3
                    }
                SupplierOrderLineDataModel(
                    id = newDiagnosticId(),
                    orderId = orderId,
                    goodsItemId = id,
                    requestedQuantity = unit.copy(total = count),
                    expectedSupplyPrice = price,
                )
            }
    return SupplierOrderWithLinesDataModel(
        SupplierOrderDataModel(
            id = orderId,
            userId = accountId,
            storeId = snapshot.storeId,
            supplierId = supplierId,
            status = SupplierOrderStatusDataModel.Sent,
            desiredDeliveryTimeMillis =
                settings
                    .arrival(today)
                    .atStartOfDayIn(TimeZone.of(snapshot.timeZoneId))
                    .toEpochMilliseconds(),
        ),
        lines,
    )
}
