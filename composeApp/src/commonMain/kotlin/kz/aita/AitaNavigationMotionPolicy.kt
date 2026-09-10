package kz.aita

internal enum class AitaNavigationMotion { NONE, FORWARD, BACK, REPLACE }

internal const val AITA_NAV_ENTER_MILLIS = 230
internal const val AITA_NAV_BACK_MILLIS = 210
internal const val AITA_NAV_EXIT_MILLIS = 150
internal const val AITA_NAV_FADE_MILLIS = 140
internal const val AITA_NAV_SIZE_MILLIS = 200
internal const val AITA_NAV_TRAVEL_DP = 48
internal const val AITA_SCENE_TRAVEL_DP = 24

/** A different stack length alone is not evidence of a push/pop (restoration/reset can replace it). */
internal fun <T> aitaStackMotion(initial: List<T>, target: List<T>): AitaNavigationMotion = when {
    initial == target -> AitaNavigationMotion.NONE
    initial.isEmpty() || target.isEmpty() -> AitaNavigationMotion.REPLACE
    target.size > initial.size && target.take(initial.size) == initial -> AitaNavigationMotion.FORWARD
    initial.size > target.size && initial.take(target.size) == target -> AitaNavigationMotion.BACK
    else -> AitaNavigationMotion.REPLACE
}

internal fun aitaOrderedMotion(initialIndex: Int?, targetIndex: Int?): AitaNavigationMotion = when {
    initialIndex == null || targetIndex == null -> AitaNavigationMotion.REPLACE
    targetIndex > initialIndex -> AitaNavigationMotion.FORWARD
    targetIndex < initialIndex -> AitaNavigationMotion.BACK
    else -> AitaNavigationMotion.NONE
}

/** Proportional on a phone, capped on desktop. No full-window sweeps or overflow. */
internal fun aitaNavigationTravelPx(extentPx: Int, limitPx: Int): Int =
    (extentPx.coerceAtLeast(0) / 8).coerceAtMost(limitPx.coerceAtLeast(0))

internal fun aitaNavigationDirectionSign(motion: AitaNavigationMotion, rightToLeft: Boolean): Int {
    val logical = when (motion) {
        AitaNavigationMotion.FORWARD -> 1
        AitaNavigationMotion.BACK -> -1
        else -> 0
    }
    return if (rightToLeft) -logical else logical
}

/** Visual identity only. No account payloads, preferences, credentials or business state. */
internal data class AitaSceneMotionTarget(
    val family: String,
    val selection: String,
    val stack: List<String>,
    val selectionIndex: Int? = null
)

internal fun aitaSceneMotion(initial: AitaSceneMotionTarget, target: AitaSceneMotionTarget): AitaNavigationMotion {
    if (initial.family != target.family) return AitaNavigationMotion.REPLACE
    if (initial.selection != target.selection) {
        return aitaOrderedMotion(initial.selectionIndex, target.selectionIndex)
            .takeUnless { it == AitaNavigationMotion.NONE } ?: AitaNavigationMotion.REPLACE
    }
    // A filtered tab list/permission refresh may change an ordinal without changing the scene.
    return aitaStackMotion(initial.stack, target.stack)
}
