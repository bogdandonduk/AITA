package kz.aita

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.PI
import kotlin.math.sin

@Composable
private fun rememberLoadingPhase(): Animatable<Float, androidx.compose.animation.core.AnimationVector1D> {
    val phase = remember { Animatable(-1f) }
    LaunchedEffect(Unit) {
        val durationScale = coroutineContext[MotionDurationScale]
        snapshotFlow { durationScale?.scaleFactor ?: 1f }.collectLatest { scale ->
            if (scale <= 0f) phase.snapTo(0.45f)
            else {
                phase.snapTo(-1f)
                phase.animateTo(2f, infiniteRepeatable(tween(1450, easing = LinearEasing), RepeatMode.Restart))
            }
        }
    }
    return phase
}

/** A single draw-only animation feeds all bars in this placeholder. No fake text or zero counts. */
@Composable
internal fun AitaLoadingSkeleton(
    modifier: Modifier = Modifier,
    rows: Int = 3,
    compact: Boolean = false,
    color: Color = MaterialTheme.colorScheme.onSurface,
    label: String? = null
) {
    val phase = rememberLoadingPhase()
    @Composable fun Bar(modifier: Modifier) {
        Box(modifier.clip(RoundedCornerShape(7.dp)).background(color.copy(alpha = 0.07f))
            .drawWithCache {
                onDrawBehind {
                    val x = size.width * phase.value
                    drawRect(Brush.linearGradient(
                        listOf(Color.Transparent, color.copy(alpha = 0.13f), Color.Transparent),
                        start = Offset(x - size.width * 0.6f, 0f), end = Offset(x + size.width * 0.6f, size.height)))
                }
            })
    }
    Column(modifier.semantics(mergeDescendants = true) {
        progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
        if (label != null) contentDescription = label
    }, verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 18.dp)) {
        repeat(rows.coerceIn(1, 8)) { index ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!compact) Bar(Modifier.size(42.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Bar(Modifier.fillMaxWidth(if (index % 2 == 0) 0.69f else 0.84f).height(if (compact) 10.dp else 14.dp))
                    Bar(Modifier.fillMaxWidth(if (index % 2 == 0) 0.94f else 0.76f).height(10.dp))
                }
            }
        }
    }
}

@Composable
internal fun AppConfiguration.LoadingSkeleton(modifier: Modifier = Modifier, rows: Int = 3, compact: Boolean = false) =
    AitaLoadingSkeleton(modifier, rows, compact, stateValues.TextColor,
        authUiText("Loading", "Загрузка", "Жүктелуде"))

/** Actions keep their label/size; dots communicate pending work without pretending to be data. */
@Composable
internal fun AitaBusyIndicator(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    val phase = rememberLoadingPhase()
    Canvas(modifier.defaultMinSize(18.dp, 18.dp).semantics {
        progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
    }) {
        val radius = (size.minDimension / 9f).coerceAtLeast(1f)
        repeat(3) { i ->
            val strength = (0.5f + 0.5f * sin((phase.value * PI * 2 - i * 0.8).toFloat())).coerceIn(0f, 1f)
            drawCircle(color.copy(alpha = 0.35f + 0.65f * strength), radius * (0.85f + 0.15f * strength),
                Offset(size.width * (0.25f + 0.25f * i), size.height / 2f))
        }
    }
}

/** One live pane, no cross-faded outgoing form, no repeated effects when a value refreshes. */
@Composable
internal fun Modifier.aitaPaneEntrance(selection: Any?): Modifier {
    val progress = remember { Animatable(1f) }
    LaunchedEffect(selection) {
        if (coroutineContext[MotionDurationScale]?.scaleFactor == 0f) progress.snapTo(1f)
        else { progress.snapTo(0f); progress.animateTo(1f, tween(180)) }
    }
    return graphicsLayer {
        alpha = 0.80f + 0.20f * progress.value
        translationY = (1f - progress.value) * 8.dp.toPx()
    }
}

@Composable
private fun rememberValuePulse(value: Any?, identity: Any?): Animatable<Float, androidx.compose.animation.core.AnimationVector1D> {
    val pulse = remember(identity) { Animatable(0f) }
    var previous by remember(identity) { mutableStateOf(value) }
    LaunchedEffect(identity, value) {
        val changed = previous != null && value != null && previous != value
        previous = value
        if (changed) {
            pulse.snapTo(1f)
            delay(1600L)
            if (coroutineContext[MotionDurationScale]?.scaleFactor == 0f) pulse.snapTo(0f)
            else pulse.animateTo(0f, tween(1000))
        } else pulse.snapTo(0f)
    }
    return pulse
}

@Composable
internal fun AppConfiguration.changedValueColor(value: Any?, identity: Any?, base: Color = stateValues.TextColor): Color {
    val pulse = rememberValuePulse(value, identity)
    val accent = stateValues.AccentColor
    // A warning remains a warning during an update; a pulse must not falsely signal success.
    if (base == stateValues.ErrorColor || base == stateValues.BorderlineBadColor) return base
    return lerp(base, if (base == accent) lerp(accent, stateValues.OkayColor, 0.55f) else accent, pulse.value)
}

@Composable
internal fun AppConfiguration.dataChangeHighlight(modifier: Modifier, value: Any?, identity: Any?): Modifier {
    val pulse = rememberValuePulse(value, identity)
    val accent = stateValues.AccentColor
    return modifier.drawWithCache {
        onDrawBehind { drawRoundRect(accent.copy(alpha = 0.09f * pulse.value), cornerRadius = CornerRadius(10.dp.toPx())) }
    }
}

/** Exactly one live menu destination, retaining the existing per-route persistent draft mechanism. */
@Composable
internal fun AitaLiveMenuPane(
    navigationStack: List<NavigationScreenModel>,
    modifier: Modifier = Modifier,
    label: String,
    content: @Composable (List<NavigationScreenModel>) -> Unit
) {
    val destination = navigationStack.lastOrNull() ?: return
    val target = AitaSceneMotionTarget(label, destination.route, navigationStack.map { it.route })
    Box(modifier.aitaSceneMotion(target)) {
        key(destination.route) { content(navigationStack) }
    }
}
