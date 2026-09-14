package kz.aita

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.Serializable

const val MARKET_COMPARISON_WINDOW_PROTOCOL = 1
const val MARKET_COMPARISON_MAX_CANDIDATES = MARKET_COMPARISON_PAGE_CANDIDATES * MARKET_COMPARISON_MAX_PAGES

/** Refresh/expand the entire candidate window in ONE server transaction. There is deliberately
 * no cursor: joining independently priced pages can produce a ranking that never existed.
 */
@Serializable
data class MarketComparisonWindowRequest(
    val selection: MarketComparisonSelection,
    val city: String = "",
    val candidateLimit: Int = MARKET_COMPARISON_PAGE_CANDIDATES
)

@Serializable
data class MarketComparisonWindowResult(
    val accountId: String,
    val request: MarketComparisonWindowRequest,
    val reference: MarketShoppingQuotedLine,
    val matches: List<MarketShoppingQuotedLine>,
    val candidatesChecked: Int,
    val moreCandidates: Boolean,
    val checkedAtMillis: Long,
    val protocolVersion: Int = MARKET_COMPARISON_WINDOW_PROTOCOL
)

fun MarketComparisonWindowRequest.normalizedComparisonWindowRequest(): MarketComparisonWindowRequest? {
    if (!selection.isValidMarketComparison() || candidateLimit !in MARKET_COMPARISON_PAGE_CANDIDATES..MARKET_COMPARISON_MAX_CANDIDATES ||
        candidateLimit % MARKET_COMPARISON_PAGE_CANDIDATES != 0) return null
    val id = marketDiscoveryId(selection.offerId) ?: return null
    val place = MarketDiscoveryQuery(city = city).normalizedDiscoveryQuery()?.city ?: return null
    return copy(selection = selection.copy(offerId = id), city = place)
}

private fun MarketShoppingQuotedLine.isValidComparisonWindowQuote(selection: MarketComparisonSelection, checkedAt: Long): Boolean {
    if (marketDiscoveryId(line.offerId) != line.offerId || marketDiscoveryId(line.storeId) != line.storeId ||
        line.title.isBlank() || line.title.length > 180 || line.shopName.isBlank() || line.shopName.length > 120 ||
        line.updatedAtMillis < 0L || line.units != selection.units || line.basis != selection.basis ||
        line.unitName.size > 12 || line.unitName.any { it.language.length > 12 || it.value.length > 120 }) return false
    val public = offer
    if (public == null) return status == MARKET_QUOTE_UNAVAILABLE && unitPriceMinor == null && subtotalMinor == null
    if (public.id != line.offerId || public.storefront.storeId != line.storeId || !public.storefront.isValidPublicMarketShop() ||
        public.title.isBlank() || public.title.length > 180 || public.description.length > 2000 ||
        public.checkedAtMillis != checkedAt || public.sourceUpdatedAtMillis < 0L || public.categoryIds.size > 16 ||
        (public.gtin != null && marketCanonicalGtin(public.gtin) != public.gtin) || !public.hasValidDiscoveryPrice() ||
        public.availability !in setOf(MARKET_AVAILABILITY_RECORDED, MARKET_AVAILABILITY_CONFIRM)) return false
    return when (status) {
        MARKET_QUOTE_ESTIMATED -> public.shoppingBasis() == line.basis && public.availability == MARKET_AVAILABILITY_RECORDED &&
            unitPriceMinor == public.priceMinor && subtotalMinor != null && subtotalMinor == marketShoppingSubtotal(unitPriceMinor, line.units)
        MARKET_QUOTE_QUANTITY -> public.shoppingBasis() == line.basis && public.availability == MARKET_AVAILABILITY_CONFIRM &&
            unitPriceMinor == null && subtotalMinor == null
        MARKET_QUOTE_PRICE -> unitPriceMinor == null && subtotalMinor == null
        MARKET_QUOTE_CHANGED -> public.shoppingBasis() != null && public.shoppingBasis() != line.basis &&
            unitPriceMinor == null && subtotalMinor == null
        else -> false
    }
}

/** Validate the whole response before displaying totals or offering a replacement. A withdrawn
 * reference may survive only as this account's existing list intent. Candidates are always current
 * public offers; every quote shares the exact same server check instant and requested quantity.
 */
fun MarketComparisonWindowResult.isValidComparisonWindowResult(account: String, wanted: MarketComparisonWindowRequest): Boolean {
    val normalized = wanted.normalizedComparisonWindowRequest() ?: return false
    if (protocolVersion != MARKET_COMPARISON_WINDOW_PROTOCOL || accountId != account || request != normalized || checkedAtMillis <= 0L ||
        candidatesChecked !in 0..normalized.candidateLimit || matches.size > candidatesChecked ||
        (moreCandidates && candidatesChecked != normalized.candidateLimit) ||
        reference.line.offerId != normalized.selection.offerId || !reference.isValidComparisonWindowQuote(normalized.selection, checkedAtMillis) ||
        matches.map { it.line.offerId }.distinct().size != matches.size || matches != matches.rankedComparison()) return false
    if (normalized.selection.shoppingRevision == null &&
        (reference.offer?.matchesComparison(normalized.selection) != true || reference.status == MARKET_QUOTE_UNAVAILABLE)) return false
    return matches.all { row ->
        val public = row.offer ?: return@all false
        row.line.offerId != normalized.selection.offerId && row.line.storeId != reference.line.storeId &&
            row.isValidComparisonWindowQuote(normalized.selection, checkedAtMillis) && public.matchesComparison(normalized.selection) &&
            row.line.title == public.title && row.line.shopName == public.storefront.displayName && row.line.unitName == public.unitName &&
            row.line.updatedAtMillis == public.sourceUpdatedAtMillis &&
            (normalized.city.isEmpty() || normalized.city.equals(public.storefront.city, ignoreCase = true))
    }
}

/** Exactly one read, no legacy page fallback, no list mutation, no cross-account late result. */
suspend fun readOwnedMarketComparisonWindow(
    owner: MarketAccountScope,
    request: MarketComparisonWindowRequest,
    read: suspend (MarketComparisonWindowRequest) -> ResponseDataModel<MarketComparisonWindowResult>
): ResponseDataModel<MarketComparisonWindowResult> {
    currentCoroutineContext().ensureActive()
    if (!owner.isCurrent()) return cloudSessionExpiredResponse()
    val normalized = request.normalizedComparisonWindowRequest()
        ?: return ResponseDataModel(eventMessage("market.comparison_window_invalid"), null, true, 400)
    val response = read(normalized)
    currentCoroutineContext().ensureActive()
    if (!owner.isCurrent()) return cloudSessionExpiredResponse()
    if (response.httpStatusCode == 404) {
        // A real withdrawn reference is not an old-backend error. Structured server messages
        // distinguish it from an absent /compare/window route without sending a second request.
        val missingOffer = response.message?.eventMessageReferenceOrNull()?.key == "market.unavailable"
        return response.copy(payload = null, negative = true,
            message = if (missingOffer) response.message else eventMessage("market.comparison_window_upgrade"))
    }
    if (response.negative || response.transportFailure) return response.copy(payload = null, negative = true)
    if (response.httpStatusCode != 200 || response.payload?.isValidComparisonWindowResult(owner.accountId, normalized) != true)
        return ResponseDataModel(eventMessage("market.comparison_window_refresh"), null, true, 502)
    return response
}

/** A query/window change or a catalogue invalidation observed during I/O cannot mark its older
 * reply fresh. The UI retains it only as stale context and schedules one trailing refresh.
 */
fun MarketComparisonWindowResult.matchesComparisonRead(
    request: MarketComparisonWindowRequest,
    startedRevision: Long,
    currentRevision: Long
): Boolean = this.request == request.normalizedComparisonWindowRequest() && startedRevision == currentRevision
