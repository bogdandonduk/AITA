package kz.aita

/** Local, frozen list intent, not a price quote and not part of the command's wire/hash format.
 * A background refresh may update prices without changing intent. A different list revision,
 * owner or retained line requires another review; never rebase an old click onto the new list.
 */
data class MarketShoppingLineReview internal constructor(
    val accountId: String,
    val expectedRevision: Long,
    val line: MarketShoppingLine
) {
    fun matches(current: MarketShoppingSnapshot?): Boolean = accountId.isNotBlank() &&
        expectedRevision in 0 until Long.MAX_VALUE && current?.userId == accountId &&
        current.revision == expectedRevision &&
        current.lines.singleOrNull { it.line.offerId == line.offerId }?.line == line

    fun command(current: MarketShoppingSnapshot?, units: Int, commandId: String): MarketShoppingCommand? {
        if (!matches(current) || units == line.units) return null
        return MarketShoppingCommand(commandId, expectedRevision, line.offerId, units,
            if (units == 0) null else line.basis).takeIf { it.isValidMarketShoppingCommand() }
    }

    fun comparison(current: MarketShoppingSnapshot?): MarketComparisonSelection? =
        if (matches(current)) line.comparisonSelection(expectedRevision) else null
}

/** Capture from the SAME snapshot that supplies the displayed row, not a later state read. */
fun MarketShoppingSnapshot.reviewShoppingLine(line: MarketShoppingLine): MarketShoppingLineReview? =
    MarketShoppingLineReview(userId, revision, line).takeIf {
        line.offerId.isNotBlank() && line.storeId.isNotBlank() &&
            line.units in 1..MARKET_SHOPPING_MAX_UNITS && line.basis.isValidMarketBasis() && it.matches(this)
    }

/** Discovery/detail/comparison Add controls are add-only. A late callback must never reset an
 * existing line to one unit (or a previously compared quantity). Existing rows use a frozen review.
 * Server revision checking still arbitrates a concurrent device after this local check.
 */
fun MarketShoppingSnapshot.newShoppingLineCommand(
    offerId: String, units: Int, basis: MarketShoppingBasis?, commandId: String
): MarketShoppingCommand? {
    if (userId.isBlank() || revision !in 0 until Long.MAX_VALUE || units !in 1..MARKET_SHOPPING_MAX_UNITS ||
        lines.size >= MARKET_SHOPPING_MAX_LINES || lines.any { it.line.offerId == offerId }) return null
    return MarketShoppingCommand(commandId, revision, offerId, units, basis)
        .takeIf { it.isValidMarketShoppingCommand() }
}
