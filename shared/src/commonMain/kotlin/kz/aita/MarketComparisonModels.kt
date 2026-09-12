package kz.aita

import kotlinx.serialization.Serializable

const val MARKET_COMPARISON_PAGE_CANDIDATES = 40
const val MARKET_COMPARISON_MAX_PAGES = 10

/** Immutable identity chosen by the buyer. A list reference is resolved ONLY inside that account.
 * Keeping its revision prevents a comparison dialogue from replacing a line the buyer edited elsewhere.
 */
@Serializable
data class MarketComparisonSelection(
    val offerId: String,
    val basis: MarketShoppingBasis,
    val units: Int = 1,
    val shoppingRevision: Long? = null
)

@Serializable
data class MarketComparisonRequest(
    val selection: MarketComparisonSelection,
    val city: String = "",
    val after: String? = null
)

/** Pages follow candidate UUIDs, not volatile prices. Incompatible selling units are excluded by
 * the server BEFORE returning rows. A page can be empty while nextId still points to more candidates.
 */
@Serializable
data class MarketComparisonPage(
    val selection: MarketComparisonSelection,
    val reference: MarketShoppingQuotedLine,
    val matches: List<MarketShoppingQuotedLine> = emptyList(),
    val nextId: String? = null,
    val checkedAtMillis: Long = 0L,
    val city: String = ""
)

fun MarketComparisonSelection.isValidMarketComparison(): Boolean = offerId.isNotBlank() &&
    basis.isValidMarketBasis() && basis.gtin != null && units in 1..MARKET_SHOPPING_MAX_UNITS &&
    (shoppingRevision == null || shoppingRevision >= 0L)

fun MarketOffer.comparisonSelection(units: Int = 1): MarketComparisonSelection? =
    shoppingBasis()?.let { MarketComparisonSelection(id, it, units).takeIf(MarketComparisonSelection::isValidMarketComparison) }

fun MarketShoppingLine.comparisonSelection(revision: Long): MarketComparisonSelection? =
    MarketComparisonSelection(offerId, basis, units, revision).takeIf(MarketComparisonSelection::isValidMarketComparison)

/** Only candidates matching the chosen barcode, currency, actual unit and priced quantity qualify. */
fun MarketOffer.matchesComparison(selection: MarketComparisonSelection): Boolean =
    selection.isValidMarketComparison() && shoppingBasis() == selection.basis

fun MarketComparisonPage.isValidComparisonPage(expected: MarketComparisonSelection, place: String): Boolean =
    selection == expected && expected.isValidMarketComparison() && city == place.trim() && checkedAtMillis >= 0L &&
        reference.line.offerId == expected.offerId && reference.line.basis == expected.basis && reference.line.units == expected.units &&
        matches.size <= MARKET_COMPARISON_PAGE_CANDIDATES && matches.map { it.line.offerId }.distinct().size == matches.size &&
        matches.all { row -> row.line.offerId != expected.offerId && row.line.storeId != reference.line.storeId && row.line.basis == expected.basis && row.line.units == expected.units &&
            row.offer?.let { it.id == row.line.offerId && it.storefront.storeId == row.line.storeId && it.matchesComparison(expected) } == true } &&
        MarketShoppingSnapshot("comparison", lines = listOf(reference) + matches, checkedAtMillis = checkedAtMillis)
            .isValidMarketShoppingSnapshot("comparison")

/** Stable order of the loaded window, never a claim of cheapest across unseen results. */
fun List<MarketShoppingQuotedLine>.rankedComparison(): List<MarketShoppingQuotedLine> =
    sortedWith(compareBy<MarketShoppingQuotedLine> { if (it.status == MARKET_QUOTE_ESTIMATED && it.subtotalMinor != null) 0 else 1 }
        .thenBy { it.subtotalMinor ?: Long.MAX_VALUE }.thenBy { it.line.shopName.lowercase() }
        .thenBy { it.line.storeId }.thenBy { it.line.offerId })

/** Every page must succeed before the visible comparison window is replaced. */
suspend fun readMarketComparisonWindow(
    selection: MarketComparisonSelection,
    city: String,
    pages: Int,
    read: suspend (MarketComparisonRequest) -> ResponseDataModel<MarketComparisonPage>
): ResponseDataModel<MarketComparisonPage> {
    require(selection.isValidMarketComparison() && pages in 1..MARKET_COMPARISON_MAX_PAGES)
    var cursor: String? = null
    val seen = mutableSetOf<String>()
    var window: MarketComparisonPage? = null
    repeat(pages) {
        val response = read(MarketComparisonRequest(selection, city.trim(), cursor))
        val page = response.payload
        if (response.negative || page == null) return response.copy(payload = null, negative = true,
            message = response.message ?: eventMessage("market.comparison_refresh"))
        if (!page.isValidComparisonPage(selection, city)) return ResponseDataModel(eventMessage("market.page_changed"), null, true, 409)
        val previous = window
        // Never present a price delta against reference values from a different point in the read.
        if (previous != null && previous.reference.copy(offer = previous.reference.offer?.copy(checkedAtMillis = 0L)) !=
            page.reference.copy(offer = page.reference.offer?.copy(checkedAtMillis = 0L)))
            return ResponseDataModel(eventMessage("market.page_changed"), null, true, 409)
        window = if (previous == null) page else page.copy(matches = (previous.matches + page.matches).distinctBy { it.line.offerId },
            checkedAtMillis = minOf(previous.checkedAtMillis, page.checkedAtMillis))
        cursor = page.nextId
        if (cursor == null) return response.copy(payload = window?.let { it.copy(matches = it.matches.rankedComparison()) })
        if (!seen.add(requireNotNull(cursor))) return ResponseDataModel(eventMessage("market.page_changed"), null, true, 409)
    }
    return ResponseDataModel(null, window?.let { it.copy(matches = it.matches.rankedComparison()) }, false, 200)
}

/** Refuse a stale review, an implicit merge, changed unit, or unpriced candidate before journalling. */
fun MarketComparisonSelection.reviewedReplacement(
    snapshot: MarketShoppingSnapshot,
    candidate: MarketShoppingQuotedLine,
    commandId: String
): MarketShoppingCommand? {
    if (!isValidMarketComparison() || shoppingRevision == null || snapshot.revision != shoppingRevision) return null
    val original = snapshot.lines.firstOrNull { it.line.offerId == offerId }?.line ?: return null
    if (original.basis != basis || original.units != units || candidate.line.offerId == offerId || candidate.line.storeId == original.storeId ||
        snapshot.lines.any { it.line.offerId == candidate.line.offerId }) return null
    if (candidate.status != MARKET_QUOTE_ESTIMATED || candidate.line.basis != basis || candidate.line.units != units ||
        candidate.offer?.matchesComparison(this) != true || candidate.offer.id != candidate.line.offerId ||
        candidate.offer.storefront.storeId != candidate.line.storeId || candidate.subtotalMinor == null ||
        candidate.subtotalMinor != marketShoppingSubtotal(candidate.unitPriceMinor, units)) return null
    return MarketShoppingCommand(commandId, shoppingRevision, candidate.line.offerId, units, basis,
        replaceOfferId = offerId, reviewedSubtotalMinor = candidate.subtotalMinor).takeIf { it.isValidMarketShoppingCommand() }
}
