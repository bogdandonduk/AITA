@file:OptIn(kotlin.time.ExperimentalTime::class)

package kz.aita.server

import java.sql.Connection
import java.util.UUID
import kotlin.time.Instant
import kotlinx.datetime.*
import kz.aita.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

/** One consistent database snapshot; no remote calls and no writes to inventory or orders. */
internal suspend fun readReplenishment(
    user: UUID,
    store: UUID,
    days: Int,
    zone: String,
    now: Long,
): ReplenishmentSnapshot? =
    newSuspendedTransaction(
        aitaServerIoContext,
        transactionIsolation = Connection.TRANSACTION_REPEATABLE_READ,
        readOnly = true,
    ) {
        maxAttempts = 1
        exec("SET LOCAL statement_timeout = '15000ms'")
        replenishmentInsideTransaction(user, store, days, zone, now)
    }

internal fun replenishmentInsideTransaction(
    user: UUID,
    store: UUID,
    days: Int,
    zone: String,
    now: Long,
): ReplenishmentSnapshot? {
    if (!canReadPurchasingInsideTransaction(user, store)) return null
    require(days in setOf(7, 28, 90))
    val timezone = TimeZone.of(zone)
    val today = Instant.fromEpochMilliseconds(now).toLocalDateTime(timezone).date
    val end = today.atStartOfDayIn(timezone).toEpochMilliseconds()
    val start = today.minus(days, DateTimeUnit.DAY).atStartOfDayIn(timezone).toEpochMilliseconds()
    // Row and line bounds reject the whole calculation, never a misleading partial forecast.
    var lineCount = 0
    val tx =
        Transactions.selectAll()
            .where {
                (Transactions.storeId eq store) and
                    (Transactions.timeMillis greaterEq start) and
                    (Transactions.timeMillis less end) and
                    (Transactions.type inList listOf("purchase", "return"))
            }
            .limit(50001)
            .map {
                val model = it.toTransactionDataModel()
                lineCount += model.goodsInTransaction.size
                if (lineCount > 200_000) throw ReplenishmentTooLarge()
                model
            }
    if (tx.size > 50000) throw ReplenishmentTooLarge()
    val stock =
        StockItems.selectAll()
            .where { (StockItems.storeId eq store) and (StockItems.isActive eq true) }
            .limit(5001)
            .map { it.toGoodsItemDataModel() }
    if (stock.size > 5000) throw ReplenishmentTooLarge()
    val batches =
        StockBatchesV2.selectAll()
            .where { (StockBatchesV2.storeId eq store) and (StockBatchesV2.isActive eq true) }
            .limit(30001)
            .map { it.toGoodsBatchDataModel() }
    if (batches.size > 30000) throw ReplenishmentTooLarge()
    val orders =
        SupplierOrders.selectAll()
            .where {
                (SupplierOrders.storeId eq store) and
                    (SupplierOrders.isActive eq true) and
                    (SupplierOrders.status inList
                        listOf("Confirmed", "Packed", "InDelivery", "PartiallyDelivered"))
            }
            .limit(5001)
            .map { it.toSupplierOrderDataModel() }
    if (orders.size > 5000) throw ReplenishmentTooLarge()
    val orderIds = orders.map { UUID.fromString(it.id) }
    val lines =
        if (orderIds.isEmpty()) emptyList()
        else
            SupplierOrderLines.selectAll()
                .where {
                    (SupplierOrderLines.orderId inList orderIds) and
                        (SupplierOrderLines.isActive eq true)
                }
                .limit(30001)
                .map { it.toSupplierOrderLineDataModel() }
    if (lines.size > 30000) throw ReplenishmentTooLarge()
    return buildReplenishmentSnapshot(
        store.toString(),
        now,
        days,
        zone,
        stock,
        batches,
        tx,
        orders,
        lines,
        globalAppConfigurationState.payloadValue.goodsItemsQuantityUnits,
    )
}
