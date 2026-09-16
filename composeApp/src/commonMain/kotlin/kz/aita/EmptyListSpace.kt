package kz.aita

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Height after preceding visible headers, not a second whole viewport below them. */
internal fun remainingListSpacePixels(
    viewportStart: Int, viewportEnd: Int, afterPadding: Int, itemOffset: Int?, minimum: Int
): Int {
    val floor = minimum.coerceAtLeast(0)
    if (viewportEnd <= viewportStart || itemOffset == null) return floor
    val start = maxOf(viewportStart, itemOffset).toLong()
    return (viewportEnd.toLong() - start - afterPadding.coerceAtLeast(0))
        .coerceIn(floor.toLong(), Int.MAX_VALUE.toLong()).toInt()
}

/**
 * Apply only to a keyed empty-state list item, never to loading rows or real data cards.
 * Existing headers stay scrollable. Large headers/text retain a minimum readable panel.
 * The state is reduced to one pixel height so unrelated list mutations do not recompose it.
 */
@Composable
internal fun Modifier.remainingListSpace(
    state: LazyListState, itemKey: Any, minimumHeight: Dp = 160.dp
): Modifier {
    val density = LocalDensity.current
    val floor = with(density) { minimumHeight.roundToPx() }
    val height by remember(state, itemKey, floor) {
        derivedStateOf {
            val info = state.layoutInfo
            remainingListSpacePixels(info.viewportStartOffset, info.viewportEndOffset,
                info.afterContentPadding, info.visibleItemsInfo.firstOrNull { it.key == itemKey }?.offset, floor)
        }
    }
    return heightIn(min = with(density) { height.toDp() })
}
