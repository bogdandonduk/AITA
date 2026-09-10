package kz.aita.server

import kz.aita.QuantityDataModel
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.round

internal data class StockBatchTransferQuantities(val moved: QuantityDataModel, val remaining: QuantityDataModel)

/** Totals are base units; request metadata never overrides the stored batch's measurement unit.
 * Reject malformed/fractional piece counts instead of silently truncating or saturating to Int.
 * Do not round the remainder a second time: that used to lose stock on fractional legacy totals.
 */
internal fun planStockBatchTransfer(source: QuantityDataModel, requestedTotal: Double): StockBatchTransferQuantities? {
    if (!source.total.isFinite() || source.total <= 0.0 || !requestedTotal.isFinite() || requestedTotal <= 0.0) return null
    val normalized = if (source.roundTotal) floor(requestedTotal) else round(requestedTotal * 1000.0) / 1000.0
    if (!normalized.isFinite() || normalized <= 0.0 || abs(normalized - requestedTotal) > 0.00000001) return null
    if (normalized - source.total > 0.00000001) return null
    // Absorb only floating-point residue on a full move, never manufacture negative stock.
    val moved = min(normalized, source.total)
    return StockBatchTransferQuantities(source.copy(total = moved), source.copy(total = source.total - moved))
}

internal fun restoreDeclinedStockQuantity(source: QuantityDataModel, moved: QuantityDataModel): QuantityDataModel? {
    if (source.id != moved.id || source.roundTotal != moved.roundTotal ||
        !source.total.isFinite() || source.total < 0.0 || !moved.total.isFinite() || moved.total <= 0.0) return null
    val total = source.total + moved.total
    return source.copy(total = total).takeIf { total.isFinite() }
}
