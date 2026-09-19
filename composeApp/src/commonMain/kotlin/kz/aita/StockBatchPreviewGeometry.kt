package kz.aita

import kotlin.math.round

internal const val STOCK_BATCH_PREVIEW_WIDTH_DP = 200
internal const val STOCK_BATCH_PREVIEW_GAP_DP = 8

/** The visible card width and drag slot use one geometry, independent of a batch's text length. */
internal fun stockBatchPreviewDragOffset(index: Int, count: Int, stepPx: Float, proposed: Float): Float {
    if (count <= 0 || index !in 0 until count || !stepPx.isFinite() || stepPx <= 0 || !proposed.isFinite()) return 0f
    return proposed.coerceIn(-index * stepPx, (count - 1 - index) * stepPx)
}

internal fun stockBatchPreviewTarget(index: Int, count: Int, stepPx: Float, offsetPx: Float): Int {
    if (count <= 0) return 0
    val origin = index.coerceIn(0, count - 1)
    val offset = stockBatchPreviewDragOffset(origin, count, stepPx, offsetPx)
    if (!stepPx.isFinite() || stepPx <= 0) return origin
    return (origin + round(offset / stepPx).toInt()).coerceIn(0, count - 1)
}
