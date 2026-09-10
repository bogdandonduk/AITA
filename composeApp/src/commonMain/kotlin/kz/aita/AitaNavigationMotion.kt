package kz.aita

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

// A quick start with a clean, non-bouncy landing. Shared by pages, forms, dialogs and sheets.
internal val AitaNavigationEasing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)

private fun aitaNavigationContentTransform(
    motion: AitaNavigationMotion,
    maxTravelPx: Int,
    rightToLeft: Boolean,
    targetDepth: Int
): ContentTransform {
    if (motion == AitaNavigationMotion.NONE) {
        return ContentTransform(EnterTransition.None, ExitTransition.None, sizeTransform = null)
    }
    val direction = aitaNavigationDirectionSign(motion, rightToLeft)
    val enteringMillis = if (motion == AitaNavigationMotion.BACK) AITA_NAV_BACK_MILLIS else AITA_NAV_ENTER_MILLIS
    val entering = fadeIn(animationSpec = tween(AITA_NAV_FADE_MILLIS, delayMillis = 30)) +
        slideInHorizontally(animationSpec = tween(enteringMillis, easing = AitaNavigationEasing)) { width ->
            val travel = aitaNavigationTravelPx(width, maxTravelPx)
            direction * if (motion == AitaNavigationMotion.BACK) travel / 2 else travel
        }
    val exiting = fadeOut(animationSpec = tween(90)) +
        slideOutHorizontally(animationSpec = tween(AITA_NAV_EXIT_MILLIS, easing = AitaNavigationEasing)) { width ->
            val travel = aitaNavigationTravelPx(width, maxTravelPx)
            -direction * if (motion == AitaNavigationMotion.BACK) travel else travel / 2
        }
    return ContentTransform(
        targetContentEnter = entering,
        initialContentExit = exiting,
        targetContentZIndex = targetDepth.toFloat(),
        sizeTransform = SizeTransform(clip = true) { _, _ ->
            tween(durationMillis = AITA_NAV_SIZE_MILLIS, easing = AitaNavigationEasing)
        }
    )
}

/** Keep the existing stack/composition ownership; change only its bounded visual transition. */
@Composable
internal fun <T> aitaStackTransitionSpec(): AnimatedContentTransitionScope<List<T>>.() -> ContentTransform {
    val maxTravelPx = with(LocalDensity.current) { AITA_NAV_TRAVEL_DP.dp.roundToPx() }
    val rightToLeft = LocalLayoutDirection.current == LayoutDirection.Rtl
    return remember(maxTravelPx, rightToLeft) {
        {
            aitaNavigationContentTransform(
                aitaStackMotion(initialState, targetState), maxTravelPx, rightToLeft, targetState.size
            )
        }
    }
}

@Composable
internal fun <T> aitaOrderedTransitionSpec(index: (T) -> Int): AnimatedContentTransitionScope<T>.() -> ContentTransform {
    val maxTravelPx = with(LocalDensity.current) { 28.dp.roundToPx() }
    val rightToLeft = LocalLayoutDirection.current == LayoutDirection.Rtl
    return {
        aitaNavigationContentTransform(
            aitaOrderedMotion(index(initialState), index(targetState)), maxTravelPx, rightToLeft, index(targetState)
        )
    }
}

/**
 * Entrance-only motion for live business panes: never compose an outgoing second payment form,
 * barcode listener, support sender or camera. No navigation delay, input interception, re-key,
 * or business callbacks. The caller continues to own the exact same content/lifecycle.
 *
 * Animation values are read in the draw layer, not by the expensive screen composition. A new
 * destination cancels the previous LaunchedEffect instead of queuing animations behind taps.
 */
@Composable
internal fun Modifier.aitaSceneMotion(target: AitaSceneMotionTarget): Modifier {
    val progress = remember { Animatable(1f) }
    var previous by remember { mutableStateOf(target) }
    var motion by remember { mutableStateOf(AitaNavigationMotion.NONE) }
    val maxTravelPx = with(LocalDensity.current) { AITA_SCENE_TRAVEL_DP.dp.toPx() }
    val replacementLiftPx = with(LocalDensity.current) { 8.dp.toPx() }
    val rightToLeft = LocalLayoutDirection.current == LayoutDirection.Rtl

    LaunchedEffect(target) {
        motion = aitaSceneMotion(previous, target)
        previous = target
        if (motion == AitaNavigationMotion.NONE || coroutineContext[MotionDurationScale]?.scaleFactor == 0f) {
            progress.snapTo(1f)
        } else {
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = if (motion == AitaNavigationMotion.BACK) AITA_NAV_BACK_MILLIS else AITA_NAV_ENTER_MILLIS,
                    easing = AitaNavigationEasing
                )
            )
        }
    }
    return graphicsLayer {
        val remaining = 1f - progress.value
        val travel = (size.width / 8f).coerceAtMost(maxTravelPx)
        translationX = aitaNavigationDirectionSign(motion, rightToLeft) * travel * remaining
        translationY = if (motion == AitaNavigationMotion.REPLACE) replacementLiftPx * remaining else 0f
        // Stay readable immediately; do not blank a payment pane while it is already interactive.
        alpha = 1f - 0.32f * remaining
    }
}
