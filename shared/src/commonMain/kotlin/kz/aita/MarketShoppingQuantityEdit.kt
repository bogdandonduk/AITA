package kz.aita

/** A quantity is a count of the retained selling unit, not a decimal stock amount. Never filter
 * invalid input into another valid quantity ("1.5" -> "15") or clamp a paste ("1000" -> "999").
 * Keep the original draft visible; validation bounds work before scanning or parsing it.
 */
fun marketShoppingQuantityFromDraft(draft: String): Int? {
    if (draft.length !in 1..16) return null
    val text = draft.trim()
    if (text.isEmpty() || text.any { it !in '0'..'9' }) return null
    return text.toIntOrNull()?.takeIf { it in 1..MARKET_SHOPPING_MAX_UNITS }
}

/** Reuse the frozen line/revision guard. A same-quantity draft is a local no-op; zero is never
 * removal here. Price-only refreshes are harmless, but no editor may borrow a newer revision.
 */
fun MarketShoppingLineReview.quantityEditUnits(current: MarketShoppingSnapshot?, draft: String): Int? {
    val units = marketShoppingQuantityFromDraft(draft) ?: return null
    return units.takeIf { it != line.units && matches(current) }
}
