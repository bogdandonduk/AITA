package kz.aita

/** Both historical subscription routes now render the same screen. */
internal fun NavigationScreenModel.Menu.canonicalMenuDestination(): NavigationScreenModel.Menu =
    if (this == NavigationScreenModel.Menu.StoreSubscriptionPlans) NavigationScreenModel.Menu.StoreSubscription else this

/** One permanent base per pane; detail destinations are never mistaken for a base. */
internal fun normalizeMenuStack(
    stack: List<NavigationScreenModel.Menu>, base: NavigationScreenModel.Menu
): List<NavigationScreenModel.Menu> = listOf(base) + stack.map { it.canonicalMenuDestination() }.filterNot {
    it == base || it == NavigationScreenModel.Menu.List || it.isTemporarilyHiddenFromUi()
}.fold(emptyList()) { result, next ->
    if (next == NavigationScreenModel.Menu.StoreSubscription && result.lastOrNull() == next) result else result + next
}

internal fun pushMenuDestination(
    stack: List<NavigationScreenModel.Menu>, requested: NavigationScreenModel.Menu,
    base: NavigationScreenModel.Menu, remove: Boolean, forceSecond: Boolean
): List<NavigationScreenModel.Menu> {
    val normalized = normalizeMenuStack(stack, base)
    val destination = requested.canonicalMenuDestination()
    if (destination.isTemporarilyHiddenFromUi()) return normalized
    if (normalized.last() == destination && (!forceSecond || destination == NavigationScreenModel.Menu.StoreSubscription)) return normalized
    return (if (remove && normalized.size > 1) normalized.dropLast(1) else normalized) + destination
}

/** Transfer the detail trail on a resize, keeping List (narrow) / Account (wide) as the base. */
internal fun adaptMenuStacks(
    left: List<NavigationScreenModel.Menu>, right: List<NavigationScreenModel.Menu>, narrow: Boolean
): Pair<List<NavigationScreenModel.Menu>, List<NavigationScreenModel.Menu>> {
    val normalizedLeft = normalizeMenuStack(left, NavigationScreenModel.Menu.List)
    val normalizedRight = normalizeMenuStack(right, NavigationScreenModel.Menu.UserAccount)
    return if (narrow) {
        val trail = normalizedRight.drop(1).ifEmpty { normalizedLeft.drop(1) }
        normalizeMenuStack(trail, NavigationScreenModel.Menu.List) to listOf(NavigationScreenModel.Menu.UserAccount)
    } else {
        val trail = normalizedLeft.drop(1).ifEmpty { normalizedRight.drop(1) }
        listOf(NavigationScreenModel.Menu.List) to normalizeMenuStack(trail, NavigationScreenModel.Menu.UserAccount)
    }
}
