package kz.aita

/** Read-only, deterministic, separable item estimates. No reservation, currency conversion,
 * route/distance guess, delivery fees, cross-item discounts or implicit list mutation.
 * Complete coverage wins before cost; a cheaper partial basket is never called savings.
 */
fun buildMarketBasketResult(
    snapshot: MarketShoppingSnapshot,
    request: MarketBasketRequest,
    alternatives: List<MarketBasketChoice>,
    limitedSourceOfferIds: List<String>,
    candidatesChecked: Int
): MarketBasketResult {
    val input = requireNotNull(request.normalizedBasketRequest())
    require(snapshot.revision == input.expectedRevision && snapshot.isValidMarketShoppingSnapshot(snapshot.userId))
    require(alternatives.size <= MARKET_SHOPPING_MAX_LINES * MARKET_BASKET_CANDIDATES_PER_LINE)
    val fixed = snapshot.basketFixedLines()
    val fixedIds = fixed.map { it.offerId }.toSet()
    val sources = snapshot.lines.associateBy { it.line.offerId }
    val accepted = alternatives.filter { candidate ->
        val source = sources[candidate.sourceOfferId]?.line
        source != null && source.offerId !in fixedIds && candidate.quote.line.offerId !in sources &&
            candidate.isEligibleBasketChoice(source, snapshot.checkedAtMillis)
    }
    val currentChoices = snapshot.lines.mapNotNull { row -> MarketBasketChoice(row.line.offerId, row)
        .takeIf { it.isEligibleBasketChoice(row.line, snapshot.checkedAtMillis) } }
    // A candidate selected independently for two demands can overstate stock or quantity breaks.
    // Even malformed/ambiguous candidate inputs must not make that possible.
    val collisions = accepted.groupBy { it.quote.line.offerId }.filterValues { choices ->
        choices.map { it.sourceOfferId }.distinct().size > 1
    }.keys
    val options = (currentChoices + accepted.filter { it.quote.line.offerId !in collisions })
        .filter { input.city.isEmpty() || it.quote.offer?.storefront?.city.equals(input.city, ignoreCase = true) }
        .distinctBy { it.sourceOfferId to it.quote.line.offerId }
    val groups = snapshot.lines.groupBy { it.line.basis.currencyCode }.toList().sortedBy { it.first }.map { (currency, rows) ->
        val ids = rows.map { it.line.offerId }
        val perSource = options.filter { it.sourceOfferId in ids }.groupBy { it.sourceOfferId }
            .mapValues { (_, values) -> values.sortedWith(basketChoiceOrder) }
        fun forShops(kind: String, shops: Set<String>?): MarketBasketPlan = basketPlan(kind, ids,
            ids.mapNotNull { id -> perSource[id]?.firstOrNull { shops == null || it.quote.line.storeId in shops } })
        val shopIds = perSource.values.flatten().map { it.quote.line.storeId }.distinct().sorted()
        val singlePlans = shopIds.map { forShops(MARKET_BASKET_ONE_SHOP, setOf(it)) }.sortedWith(basketPlanOrder)
        val empty = basketPlan(MARKET_BASKET_ONE_SHOP, ids, emptyList())
        val one = singlePlans.firstOrNull() ?: empty
        // Bound quadratic pair work explicitly. All shops still participate in the single-shop
        // and per-line price options. Selection prioritises coverage, then subtotal and stable ID.
        val pairShops = singlePlans.take(MARKET_BASKET_PAIR_SHOP_LIMIT).map { it.storeIds.single() }
        var two = one.copy(kind = MARKET_BASKET_TWO_SHOPS)
        for (left in pairShops.indices) for (right in left + 1 until pairShops.size) {
            val candidate = forShops(MARKET_BASKET_TWO_SHOPS, setOf(pairShops[left], pairShops[right]))
            if (basketPlanOrder.compare(candidate, two) < 0) two = candidate
        }
        MarketBasketCurrencyPlans(currency,
            basketPlan(MARKET_BASKET_CURRENT, ids, currentChoices.filter { it.sourceOfferId in ids }), one, two,
            forShops(MARKET_BASKET_LOWEST_ITEMS, null), shopIds.size, pairShops.size)
    }
    return MarketBasketResult(input, snapshot, groups, fixed, limitedSourceOfferIds.sorted(), candidatesChecked)
}

private val basketChoiceOrder = compareBy<MarketBasketChoice> { it.quote.subtotalMinor ?: Long.MAX_VALUE }
    .thenBy { it.sourceOfferId != it.quote.line.offerId }
    .thenBy { it.quote.line.storeId }.thenBy { it.quote.line.offerId }

/** Stable ties keep fewer shops and fewer replacements. Null/overflow is never a cheap total. */
private val basketPlanOrder = compareBy<MarketBasketPlan> { it.missingOfferIds.size }
    .thenBy { it.itemsSubtotalMinor == null }.thenBy { it.itemsSubtotalMinor ?: Long.MAX_VALUE }
    .thenBy { it.storeIds.size }.thenBy { it.changedLines }
    .thenBy { it.storeIds.joinToString(":") }.thenBy { it.choices.joinToString(":") { choice -> choice.quote.line.offerId } }
