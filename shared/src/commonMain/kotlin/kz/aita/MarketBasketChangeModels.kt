package kz.aita

import kotlinx.serialization.Serializable

const val MARKET_BASKET_REVIEW_MAX_AGE_MILLIS = 300_000L

/** A frozen review of ONE complete currency group, including the lines which stay where they are.
 * The list remains buyer intent. Applying this never reserves stock or fixes a checkout price.
 */
@Serializable
data class MarketBasketReviewedLine(
    val sourceOfferId: String,
    val targetOfferId: String,
    val targetStoreId: String,
    val units: Int,
    val basis: MarketShoppingBasis,
    val reviewedSubtotalMinor: Long,
    val reviewedTitle: String,
    val storefrontRevision: Long
)

@Serializable
data class MarketBasketChange(
    val currencyCode: String,
    val city: String,
    val kind: String,
    val checkedAtMillis: Long,
    val reviewedItemsSubtotalMinor: Long,
    val lines: List<MarketBasketReviewedLine>
) {
    val changedLines: Int get() = lines.count { it.sourceOfferId != it.targetOfferId }
}

fun MarketBasketChange.isValidBasketChange(): Boolean {
    if (!currencyCode.matches(Regex("[A-Z]{3}")) ||
        MarketBasketRequest(0L, city).normalizedBasketRequest()?.city != city ||
        kind !in setOf(MARKET_BASKET_ONE_SHOP, MARKET_BASKET_TWO_SHOPS, MARKET_BASKET_LOWEST_ITEMS) ||
        checkedAtMillis <= 0L || reviewedItemsSubtotalMinor < 0L ||
        lines.size !in 1..MARKET_SHOPPING_MAX_LINES || changedLines == 0 ||
        lines.map { it.sourceOfferId }.distinct().size != lines.size ||
        lines.map { it.targetOfferId }.distinct().size != lines.size) return false
    val shops = lines.map { it.targetStoreId }.distinct().size
    if ((kind == MARKET_BASKET_ONE_SHOP && shops > 1) || (kind == MARKET_BASKET_TWO_SHOPS && shops > 2)) return false
    var sum = 0L
    for (line in lines) {
        if (marketDiscoveryId(line.sourceOfferId) != line.sourceOfferId ||
            marketDiscoveryId(line.targetOfferId) != line.targetOfferId ||
            marketDiscoveryId(line.targetStoreId) != line.targetStoreId ||
            line.units !in 1..MARKET_SHOPPING_MAX_UNITS || !line.basis.isValidMarketBasis() ||
            line.basis.currencyCode != currencyCode || line.reviewedSubtotalMinor < 0L ||
            line.reviewedTitle.isBlank() || line.reviewedTitle.length > 180 || line.storefrontRevision < 0L ||
            (line.sourceOfferId != line.targetOfferId && line.basis.gtin == null) ||
            sum > Long.MAX_VALUE - line.reviewedSubtotalMinor) return false
        sum += line.reviewedSubtotalMinor
    }
    return sum == reviewedItemsSubtotalMinor
}

/** Ownership is enforced by the caller. No currency group, original line or quantity can be
 * omitted from the review. Targets already in the list are never merged, swapped or overwritten.
 */
fun MarketBasketChange.basketIntentError(current: MarketShoppingSnapshot, expectedRevision: Long): String? {
    if (!isValidBasketChange()) return "market.basket_apply_invalid"
    if (current.revision != expectedRevision || expectedRevision == Long.MAX_VALUE) return "market.shopping_changed"
    val sources = current.lines.filter { it.line.basis.currencyCode == currencyCode }.map { it.line }
    if (lines.map { it.sourceOfferId } != sources.map { it.offerId }) return "market.shopping_changed"
    val allIds = current.lines.map { it.line.offerId }.toSet()
    val fixedIds = current.basketFixedLines().map { it.offerId }.toSet()
    for ((index, line) in lines.withIndex()) {
        val source = sources[index]
        if (source.units != line.units || source.basis != line.basis) return "market.shopping_changed"
        if (line.targetOfferId == source.offerId) {
            if (line.targetStoreId != source.storeId) return "market.basket_apply_invalid"
        } else {
            if (source.offerId in fixedIds || line.targetStoreId == source.storeId) return "market.basket_apply_invalid"
            if (line.targetOfferId in allIds) return "market.comparison_already_listed"
        }
    }
    return null
}

/** Checked only for a NEW command, after looking up recorded outcomes. An acknowledged change
 * remains replayable after five minutes; an expired, never-applied review cannot become a write.
 */
fun MarketBasketChange.isWithinBasketReviewTime(nowMillis: Long): Boolean = nowMillis > 0L &&
    checkedAtMillis > 0L && (if (checkedAtMillis > nowMillis) checkedAtMillis - nowMillis <= 5_000L
        else nowMillis - checkedAtMillis <= MARKET_BASKET_REVIEW_MAX_AGE_MILLIS)

fun MarketBasketResult.reviewedBasketCommand(currency: String, kind: String, commandId: String): MarketShoppingCommand? {
    if (!isValidBasketResult(snapshot.userId, request)) return null
    val group = currencies.firstOrNull { it.currencyCode == currency } ?: return null
    val plan = group.plan(kind)
    if (plan.kind != kind || !plan.complete || plan.changedLines == 0) return null
    val rows = plan.choices.map { choice ->
        val offer = choice.quote.offer ?: return null
        MarketBasketReviewedLine(choice.sourceOfferId, offer.id, offer.storefront.storeId,
            choice.quote.line.units, choice.quote.line.basis, choice.quote.subtotalMinor ?: return null,
            offer.title, offer.storefront.revision)
    }
    val change = MarketBasketChange(currency, request.city, kind, snapshot.checkedAtMillis,
        plan.itemsSubtotalMinor ?: return null, rows)
    if (change.basketIntentError(snapshot, snapshot.revision) != null) return null
    // Empty legacy target + zero units intentionally FAIL validation in a downgraded client.
    // An older client cannot ignore basketChange and replay it as a single add/remove operation.
    return MarketShoppingCommand(commandId, snapshot.revision, "", 0, basketChange = change)
        .takeIf { it.isValidMarketShoppingCommand() }
}

/** Validate the actual re-quoted result for ALL reviewed lines, including unchanged sellers. */
fun MarketBasketChange.basketQuotesError(quotes: List<MarketShoppingQuotedLine>): String? {
    if (quotes.size != lines.size) return "market.basket_apply_unavailable"
    for ((index, reviewed) in lines.withIndex()) {
        val quote = quotes[index]
        val offer = quote.offer ?: return "market.basket_apply_unavailable"
        if (quote.line.offerId != reviewed.targetOfferId || quote.line.storeId != reviewed.targetStoreId ||
            quote.line.units != reviewed.units || quote.line.basis != reviewed.basis ||
            offer.shoppingBasis() != reviewed.basis || offer.id != reviewed.targetOfferId ||
            offer.storefront.storeId != reviewed.targetStoreId || !offer.storefront.published ||
            quote.status != MARKET_QUOTE_ESTIMATED || offer.availability != MARKET_AVAILABILITY_RECORDED ||
            quote.unitPriceMinor != offer.priceMinor || quote.subtotalMinor == null ||
            quote.subtotalMinor != marketShoppingSubtotal(quote.unitPriceMinor, reviewed.units)) return "market.basket_apply_unavailable"
        if (offer.storefront.revision != reviewed.storefrontRevision || offer.title != reviewed.reviewedTitle ||
            (city.isNotEmpty() && !offer.storefront.city.equals(city, ignoreCase = true))) return "market.basket_apply_offer_changed"
        if (quote.subtotalMinor != reviewed.reviewedSubtotalMinor) return "market.basket_apply_price_changed"
    }
    return null
}

/** One transport identity/journal for single-line and basket commands, separate backend routes. */
fun MarketShoppingCommand.shoppingMutationEndpoint(): String = when {
    checklistChange != null -> "market/shopping-list/checklist"
    basketChange != null -> "market/shopping-list/apply-plan"
    replaceOfferId != null -> "market/shopping-list/replace"
    else -> "market/shopping-list"
}

/** At the applied revision the returned intent must contain the ENTIRE reviewed group. A later
 * revision may legitimately reflect another device's subsequent edits; never restore old results.
 */
fun MarketShoppingCommand.matchesBasketOutcome(outcome: MarketShoppingOutcome): Boolean {
    val basket = basketChange ?: return true
    if (!outcome.accepted) return true
    if (outcome.appliedRevision != expectedRevision + 1L || outcome.snapshot.revision < expectedRevision + 1L) return false
    if (outcome.snapshot.revision > expectedRevision + 1L) return true
    val group = outcome.snapshot.lines.filter { it.line.basis.currencyCode == basket.currencyCode }.map { it.line }
    if (group.map { it.offerId }.toSet() != basket.lines.map { it.targetOfferId }.toSet()) return false
    val byId = group.associateBy { it.offerId }
    return basket.lines.all { reviewed -> byId[reviewed.targetOfferId]?.let {
        it.storeId == reviewed.targetStoreId && it.basis == reviewed.basis && it.units == reviewed.units
    } == true }
}
