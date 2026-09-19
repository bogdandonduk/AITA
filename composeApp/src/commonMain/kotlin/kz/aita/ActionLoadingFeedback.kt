package kz.aita

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** Observe only requests that appeared just after this gesture. This is feedback, not a lock. */
@Composable internal fun rememberActionNetworkFeedback(): Pair<Boolean, () -> Unit> {
    var generation by remember { mutableStateOf(0) }
    var before by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var active by remember { mutableStateOf(false) }
    LaunchedEffect(generation) {
        if (generation == 0) return@LaunchedEffect
        active=false
        try {
            delay(220)
            val requests = NetworkActionActivity.active.value - before
            if (requests.isNotEmpty()) {
                active=true
                withTimeoutOrNull(30_000) { NetworkActionActivity.active.first { now -> requests.none { it in now } } }
            }
        } finally { active=false }
    }
    return active to { before=NetworkActionActivity.active.value; generation++ }
}

/** Draw-only, small shadow beneath the action; idle and background tabs have no animation. */
@Composable internal fun Modifier.actionLoadingShadow(loading: Boolean, color: Color): Modifier {
    if (!getPlatformName().equals("wasmJs",true) || !LocalLoadingAnimationsEnabled.current) return this
    val focused=LocalWindowInfo.current.isWindowFocused
    val phase=remember { Animatable(0f) }
    LaunchedEffect(loading,focused) {
        phase.snapTo(0f)
        if (loading && focused) {
            delay(200)
            if (coroutineContext[MotionDurationScale]?.scaleFactor == 0f) phase.snapTo(.55f)
            else phase.animateTo(1f,infiniteRepeatable(tween(900),RepeatMode.Reverse))
        }
    }
    return drawWithContent {
        drawContent()
        val alpha=phase.value
        if (alpha>0f) {
            val depth=8.dp.toPx()
            drawRect(Brush.verticalGradient(listOf(color.copy(alpha=.24f*alpha),Color.Transparent),
                startY=size.height-1.dp.toPx(),endY=size.height+depth),
                topLeft=Offset(6.dp.toPx(),size.height-1.dp.toPx()),size=Size((size.width-12.dp.toPx()).coerceAtLeast(0f),depth))
        }
    }
}
