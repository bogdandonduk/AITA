package kz.aita

import kotlinx.serialization.Serializable

const val MARKET_BASKET_CANDIDATES_PER_LINE = 12
const val MARKET_BASKET_PAIR_SHOP_LIMIT = 32
const val MARKET_BASKET_CURRENT = "current"
const val MARKET_BASKET_ONE_SHOP = "one_shop"
const val MARKET_BASKET_TWO_SHOPS = "two_shops"
const val MARKET_BASKET_LOWEST_ITEMS = "lowest_items"
const val MARKET_BASKET_NO_BARCODE = "no_barcode"
const val MARKET_BASKET_REPEATED_PRODUCT = "repeated_product"

/** A read, never a shopping-list command. Identity/quantity comes from the authenticated list. */
@Serializable
data class MarketBasketRequest(val expectedRevision: Long, val city: String = "")

fun MarketBasketRequest.normalizedBasketRequest(): MarketBasketRequest? {
    if (expectedRevision < 0L) return null
    val normalizedCity = MarketDiscoveryQuery(city = city).normalizedDiscoveryQuery()?.city ?: return null
    return copy(city = normalizedCity)
}

/** The source line remains distinct from the proposed public offer. Nothing is silently merged. */
@Serializable
data class MarketBasketChoice(val sourceOfferId: String, val quote: MarketShoppingQuotedLine)

@Serializable
data class MarketBasketPlan(
    val kind: String,
    val choices: List<MarketBasketChoice>,
    val missingOfferIds: List<String>,
    val itemsSubtotalMinor: Long?,
    val storeIds: List<String>
) {
    val complete: Boolean get() = missingOfferIds.isEmpty() && choices.isNotEmpty() && itemsSubtotalMinor != null
    val changedLines: Int get() = choices.count { it.sourceOfferId != it.quote.line.offerId }
}

@Serializable
data class MarketBasketCurrencyPlans(
    val currencyCode: String,
    val current: MarketBasketPlan,
    val oneShop: MarketBasketPlan,
    val twoShops: MarketBasketPlan,
    val lowestItems: MarketBasketPlan,
    val eligibleShopCount: Int,
    val pairSearchShopCount: Int
) {
    fun plan(kind: String): MarketBasketPlan = when (kind) {
        MARKET_BASKET_CURRENT -> current
        MARKET_BASKET_ONE_SHOP -> oneShop
        MARKET_BASKET_TWO_SHOPS -> twoShops
        else -> lowestItems
    }
}

@Serializable
data class MarketBasketFixedLine(val offerId: String, val reason: String)

@Serializable
data class MarketBasketResult(
    val request: MarketBasketRequest,
    val snapshot: MarketShoppingSnapshot,
    val currencies: List<MarketBasketCurrencyPlans>,
    val fixedLines: List<MarketBasketFixedLine>,
    val limitedSourceOfferIds: List<String>,
    val candidatesChecked: Int,
    val protocolVersion: Int = 1
)

/** Repeated identities cannot share one candidate's independently quoted stock. Until aggregate
 * quantity quoting exists, keep EACH such line at its original location rather than undercounting.
 */
fun MarketShoppingSnapshot.basketFixedLines(): List<MarketBasketFixedLine> {
    val repeated = lines.groupingBy { it.line.basis }.eachCount().filterValues { it > 1 }.keys
    return lines.mapNotNull { row -> when {
        row.line.basis.gtin == null -> MarketBasketFixedLine(row.line.offerId, MARKET_BASKET_NO_BARCODE)
        row.line.basis in repeated -> MarketBasketFixedLine(row.line.offerId, MARKET_BASKET_REPEATED_PRODUCT)
        else -> null
    } }
}

fun MarketBasketPlan.savingsAgainst(current: MarketBasketPlan): Long? {
    if (!complete || !current.complete || choices.map { it.sourceOfferId }.toSet() != current.choices.map { it.sourceOfferId }.toSet()) return null
    val reference = current.choices.associateBy { it.sourceOfferId }
    val currencies = choices.map { it.quote.line.basis.currencyCode }.distinct()
    if (currencies.size != 1 || choices.any { choice ->
        val original = reference[choice.sourceOfferId]?.quote?.line
        original == null || original.basis != choice.quote.line.basis || original.units != choice.quote.line.units
    }) return null
    return requireNotNull(current.itemsSubtotalMinor) - requireNotNull(itemsSubtotalMinor)
}

internal fun basketSubtotal(choices: List<MarketBasketChoice>): Long? {
    if (choices.isEmpty()) return null
    var total = 0L
    choices.forEach { choice ->
        val value = choice.quote.subtotalMinor ?: return null
        if (value < 0L || total > Long.MAX_VALUE - value) return null
        total += value
    }
    return total
}

/** Only an exact, priced quantity at a public location is an eligible plan choice. */
internal fun MarketBasketChoice.isEligibleBasketChoice(source: MarketShoppingLine, checkedAt: Long): Boolean {
    val row = quote
    val offer = row.offer ?: return false
    val shop = offer.storefront
    if (marketDiscoveryId(offer.id) != offer.id || marketDiscoveryId(shop.storeId) != shop.storeId ||
        offer.title.isBlank() || offer.title.length > 180 || offer.description.length > 2000 ||
        shop.displayName.isBlank() || shop.displayName.length > 120 || shop.city.length > 100 ||
        shop.publicAddress.length > 400 || shop.pickupNote.length > 1000 || shop.revision < 0L ||
        offer.sourceUpdatedAtMillis < 0L || offer.priceMinor?.let { it !in 0L..1_000_000_000_000L } != false) return false
    if (sourceOfferId != source.offerId || row.line.units != source.units || row.line.basis != source.basis ||
        row.status != MARKET_QUOTE_ESTIMATED || row.line.offerId != offer.id || row.line.storeId != offer.storefront.storeId ||
        offer.shoppingBasis() != source.basis || !offer.storefront.published || offer.checkedAtMillis != checkedAt ||
        offer.availability != MARKET_AVAILABILITY_RECORDED || row.unitPriceMinor != offer.priceMinor ||
        row.subtotalMinor == null || row.subtotalMinor != marketShoppingSubtotal(row.unitPriceMinor, source.units)) return false
    return if (offer.id == source.offerId) offer.storefront.storeId == source.storeId
        else source.basis.gtin != null && offer.storefront.storeId != source.storeId
}

/** Validate wire data before showing prices or enabling navigation. It cannot prove the server's
 * optimisation, but it CAN reject missing lines disguised as a complete basket, mixed units,
 * duplicate targets, altered quantities, totals and a different account/revision/search scope.
 */
fun MarketBasketResult.isValidBasketResult(account: String, expected: MarketBasketRequest): Boolean {
    if (protocolVersion != 1 || request != expected.normalizedBasketRequest() ||
        snapshot.revision != expected.expectedRevision || snapshot.checkedAtMillis <= 0L ||
        !snapshot.isValidMarketShoppingSnapshot(account) ||
        snapshot.lines.any { marketDiscoveryId(it.line.offerId) != it.line.offerId || marketDiscoveryId(it.line.storeId) != it.line.storeId ||
            it.line.title.isBlank() || it.line.title.length > 180 || it.line.shopName.length > 120 ||
            it.line.unitName.size > 12 || it.line.unitName.any { name -> name.language.length > 12 || name.value.length > 120 } ||
            it.status !in setOf(MARKET_QUOTE_ESTIMATED, MARKET_QUOTE_UNAVAILABLE, MARKET_QUOTE_PRICE, MARKET_QUOTE_QUANTITY, MARKET_QUOTE_CHANGED) } ||
        candidatesChecked !in 0..(MARKET_SHOPPING_MAX_LINES * MARKET_BASKET_CANDIDATES_PER_LINE) ||
        fixedLines != snapshot.basketFixedLines() || limitedSourceOfferIds.distinct().size != limitedSourceOfferIds.size) return false
    val fixedIds = fixedLines.map { it.offerId }.toSet()
    val allIds = snapshot.lines.map { it.line.offerId }.toSet()
    if (limitedSourceOfferIds.any { it !in allIds || it in fixedIds } ||
        candidatesChecked < limitedSourceOfferIds.size * MARKET_BASKET_CANDIDATES_PER_LINE ||
        candidatesChecked > (allIds.size - fixedIds.size) * MARKET_BASKET_CANDIDATES_PER_LINE) return false
    val sourceGroups = snapshot.lines.groupBy { it.line.basis.currencyCode }
    if (currencies.map { it.currencyCode } != sourceGroups.keys.sorted()) return false
    return currencies.all groups@{ group ->
        val sources = sourceGroups.getValue(group.currencyCode).associateBy { it.line.offerId }
        if (group.eligibleShopCount !in 0..(allIds.size + candidatesChecked) ||
            group.pairSearchShopCount != minOf(group.eligibleShopCount, MARKET_BASKET_PAIR_SHOP_LIMIT)) return@groups false
        val expectedCurrent = basketPlan(MARKET_BASKET_CURRENT, sources.keys.toList(), sources.values.mapNotNull { row ->
            MarketBasketChoice(row.line.offerId, row).takeIf { it.isEligibleBasketChoice(row.line, snapshot.checkedAtMillis) }
        })
        if (group.current != expectedCurrent) return@groups false
        listOf(group.oneShop to MARKET_BASKET_ONE_SHOP, group.twoShops to MARKET_BASKET_TWO_SHOPS,
            group.lowestItems to MARKET_BASKET_LOWEST_ITEMS).all plans@{ (plan, kind) ->
            val chosenIds = plan.choices.map { it.sourceOfferId }
            plan.kind == kind && chosenIds.distinct().size == chosenIds.size &&
                chosenIds == sources.keys.filter { it in chosenIds } &&
                plan.choices.map { it.quote.line.offerId }.distinct().size == plan.choices.size &&
                plan.missingOfferIds == sources.keys.filter { it !in chosenIds } &&
                plan.itemsSubtotalMinor == basketSubtotal(plan.choices) &&
                plan.storeIds == plan.choices.map { it.quote.line.storeId }.distinct().sorted() &&
                plan.storeIds.size <= group.eligibleShopCount &&
                (kind != MARKET_BASKET_ONE_SHOP || plan.storeIds.size <= 1) &&
                (kind != MARKET_BASKET_TWO_SHOPS || plan.storeIds.size <= 2) &&
                plan.choices.all choices@{ choice ->
                    val source = sources[choice.sourceOfferId]?.line ?: return@choices false
                    val target = choice.quote.line.offerId
                    choice.isEligibleBasketChoice(source, snapshot.checkedAtMillis) &&
                        marketDiscoveryId(target) == target && marketDiscoveryId(choice.quote.line.storeId) == choice.quote.line.storeId &&
                        (target == source.offerId || (source.offerId !in fixedIds && target !in allIds)) &&
                        (request.city.isEmpty() || choice.quote.offer?.storefront?.city.equals(request.city, ignoreCase = true))
                }
        }
    }
}

internal fun basketPlan(kind: String, sourceIds: List<String>, selected: List<MarketBasketChoice>): MarketBasketPlan {
    val bySource = selected.associateBy { it.sourceOfferId }
    val choices = sourceIds.mapNotNull(bySource::get)
    return MarketBasketPlan(kind, choices, sourceIds.filter { it !in bySource }, basketSubtotal(choices),
        choices.map { it.quote.line.storeId }.distinct().sorted())
}
