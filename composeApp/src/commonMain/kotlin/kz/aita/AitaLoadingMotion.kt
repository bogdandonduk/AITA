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

/** A placeholder is a layout contract. Requiring the kind keeps new screens from accidentally
 * falling back to a generic avatar/list shape. All bars share one draw-only animation clock. */
internal enum class LoadingLayout {
    StockCard, MarketplaceCard, OfferDetail, ProductPhoto, ShopCard, ShoppingLine, Comparison,
    Notification, Conversation, Message, Subscription, PaymentIntegration, SupplierSummary,
    Form, InlineValue, Activity, Metrics
}

@Composable
internal fun AitaLoadingSkeleton(
    modifier: Modifier = Modifier,
    layout: LoadingLayout,
    rows: Int = 1,
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
    @Composable fun Line(fraction: Float = 1f, height: Int = 12) = Bar(Modifier.fillMaxWidth(fraction).height(height.dp))
    @Composable fun Actions(count: Int = 1) {
        repeat(count) { Line(1f, 40) }
    }
    @Composable fun Header(icon: Int, title: Float = .66f) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (icon > 0) Bar(Modifier.size(icon.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) { Line(title, 16); Line(.4f, 10) }
        }
    }
    @Composable fun ChipRow() {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(3) { Bar(Modifier.weight(1f).height(28.dp)) }
        }
    }
    Column(modifier.semantics(mergeDescendants = true) {
        progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
        if (label != null) contentDescription = label
    }, verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 16.dp)) {
        repeat(rows.coerceIn(1, 8)) { index ->
            val framed = layout in setOf(LoadingLayout.StockCard, LoadingLayout.MarketplaceCard, LoadingLayout.ShopCard,
                LoadingLayout.Subscription, LoadingLayout.Comparison, LoadingLayout.ShoppingLine, LoadingLayout.PaymentIntegration)
            Column(Modifier.fillMaxWidth().then(if (framed) Modifier.clip(RoundedCornerShape(16.dp))
                .background(color.copy(alpha = .025f)).padding(14.dp) else Modifier),
                verticalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 10.dp)) {
                when (layout) {
                    LoadingLayout.ProductPhoto -> Bar(Modifier.fillMaxWidth().height(170.dp))
                    LoadingLayout.StockCard -> { Header(0, .78f); Line(.56f, 10); Line(.8f, 10); ChipRow(); Line(.4f, 18); ChipRow() }
                    LoadingLayout.MarketplaceCard -> { Line(1f, 170); Header(0, .83f); Line(.42f, 20); Line(.7f); Line(.82f, 10); Actions(2) }
                    LoadingLayout.OfferDetail -> { Line(1f, 170); Line(.83f, 22); Line(.38f, 22); Line(); Line(.86f); Header(28); Line(); Actions(2) }
                    LoadingLayout.ShopCard -> { Header(36); Line(.86f); Line(.64f); Actions() }
                    LoadingLayout.ShoppingLine -> { Header(0, .78f); Line(.56f); ChipRow(); Line(.4f, 20); Actions() }
                    LoadingLayout.Comparison -> { Header(28, .8f); Line(.68f); Line(.38f, 20); ChipRow(); Actions() }
                    LoadingLayout.Notification -> { Header(22, .65f); Line(.96f); Line(.72f); Line(.32f, 9) }
                    LoadingLayout.Conversation -> { Header(0, .72f); Line(.92f); Line(.57f, 10) }
                    LoadingLayout.Message -> {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (index % 2 == 0) Arrangement.Start else Arrangement.End) {
                            Column(Modifier.fillMaxWidth(.76f).clip(RoundedCornerShape(16.dp)).background(color.copy(alpha = .025f)).padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)) { Line(.45f, 10); Line(); Line(.78f); Line(.2f, 8) }
                        }
                    }
                    LoadingLayout.Subscription -> { Header(42); Line(.42f, 26); Line(.9f); Line(.72f); ChipRow(); Actions() }
                    LoadingLayout.PaymentIntegration -> { Header(32); Line(.78f); Line(.38f, 9); Line(1f, 40); Line(.38f, 9); Line(1f, 40); Actions() }
                    LoadingLayout.SupplierSummary -> { Line(.7f, 15); Line(.52f, 10); ChipRow(); Line(.85f, 10) }
                    LoadingLayout.Form -> { Line(.38f, 10); Line(1f, 40) }
                    LoadingLayout.InlineValue -> { Line(.7f, 10); Line(.92f, 10) }
                    LoadingLayout.Activity -> { Header(22, .72f); Line(.9f); Line(.36f, 9) }
                    LoadingLayout.Metrics -> { ChipRow(); Line(.8f, 16); Line(.6f); Line(1f, 80) }
                }
            }
        }
    }
}

@Composable
internal fun AppConfiguration.LoadingSkeleton(modifier: Modifier = Modifier, layout: LoadingLayout, rows: Int = 1, compact: Boolean = false) =
    AitaLoadingSkeleton(modifier, layout, rows, compact, stateValues.TextColor,
        authUiText("Loading", "Загрузка", "Жүктелуде", "Жүктөлүүдө"))

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
    val paneWidth = (destination as? NavigationScreenModel.Menu)?.let(::menuPaneMaximumWidth) ?: 1200.dp
    CompositionLocalProvider(LocalAitaScreenContentMaximumWidth provides paneWidth) {
        Box(modifier.fillMaxSize().aitaSceneMotion(target)) {
            key(destination.route) { content(navigationStack) }
        }
    }
}
