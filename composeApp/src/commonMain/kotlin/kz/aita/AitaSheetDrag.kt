package kz.aita

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Distance is relative to the actual sheet; a short mouse drag springs back without dismissal. */
internal class AitaSheetDragState {
    var offset by mutableFloatStateOf(0f)
        private set
    var height by mutableFloatStateOf(1f)
    private var settling: Job? = null
    var pointerDown = false
    var nestedDrag = false

    fun start() { settling?.cancel() }
    fun drag(delta: Float): Float {
        start()
        val before = offset
        offset = (offset + delta).coerceIn(0f, height.coerceAtLeast(1f))
        return offset - before
    }
    fun shouldDismiss(velocity: Float, density: Float): Boolean =
        offset >= minOf(height * .25f, 160f * density) ||
            (offset > 24f * density && velocity > 1000f * density)

    fun settle(scope: CoroutineScope, velocity: Float, density: Float, onDismiss: () -> Unit) {
        settling?.cancel()
        val dismiss = shouldDismiss(velocity, density)
        settling = scope.launch {
            animate(offset, if (dismiss) height else 0f, animationSpec = tween(180)) { value, _ -> offset = value }
            if (dismiss) onDismiss()
        }
    }
}

@Composable
internal fun Modifier.aitaSheetHandle(drag: AitaSheetDragState, density: Float, onDismiss: () -> Unit): Modifier {
    val scope = rememberCoroutineScope()
    val dismiss by rememberUpdatedState(onDismiss)
    return draggable(rememberDraggableState { drag.drag(it) }, Orientation.Vertical,
        onDragStarted = { drag.start() },
        onDragStopped = { velocity -> drag.settle(scope, velocity, density, dismiss) })
}

/** Scrollable content keeps its scroll. Only unconsumed downward touch drags pull the sheet. */
@Composable
internal fun rememberSheetNestedScroll(drag: AitaSheetDragState, density: Float, onDismiss: () -> Unit): NestedScrollConnection {
    val scope = rememberCoroutineScope()
    val dismiss by rememberUpdatedState(onDismiss)
    return remember(drag, density, scope) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
                if (drag.pointerDown && source == NestedScrollSource.UserInput && drag.offset > 0f && available.y < 0f) {
                    drag.nestedDrag = true
                    Offset(0f, drag.drag(available.y))
                } else Offset.Zero

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
                if (drag.pointerDown && source == NestedScrollSource.UserInput && available.y > 0f) {
                    drag.nestedDrag = true
                    Offset(0f, drag.drag(available.y))
                } else Offset.Zero

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (drag.offset <= 0f) return Velocity.Zero
                drag.settle(scope, available.y, density, dismiss)
                return Velocity(0f, available.y)
            }
        }
    }
}

/** Mouse-wheel scroll is never a sheet drag. Also settle a cancelled child scroll with no fling. */
@Composable
internal fun Modifier.observeSheetPointer(drag: AitaSheetDragState, density: Float, onDismiss: () -> Unit): Modifier {
    val scope = rememberCoroutineScope()
    val dismiss by rememberUpdatedState(onDismiss)
    return pointerInput(drag, density) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pressed = event.changes.any { it.pressed }
                if (drag.pointerDown && !pressed && drag.nestedDrag) {
                    drag.nestedDrag = false
                    drag.settle(scope, 0f, density, dismiss)
                }
                drag.pointerDown = pressed
            }
        }
    }
}
