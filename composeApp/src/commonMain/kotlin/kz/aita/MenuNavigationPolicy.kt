package kz.aita

/** One permanent base per pane; detail destinations are never mistaken for a base. */
internal fun normalizeMenuStack(
    stack: List<NavigationScreenModel.Menu>, base: NavigationScreenModel.Menu
): List<NavigationScreenModel.Menu> = listOf(base) + stack.filterNot {
    it == base || it == NavigationScreenModel.Menu.List || it.isTemporarilyHiddenFromUi()
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
