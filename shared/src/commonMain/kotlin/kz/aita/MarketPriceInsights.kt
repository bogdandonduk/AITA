package kz.aita

import kotlinx.serialization.Serializable
import kotlin.math.abs

const val MARKET_PRICE_PAGE_SIZE = 30
const val MARKET_PRICE_CANDIDATES_PER_GTIN = 80
const val MARKET_PRICE_MIN_GROUPS = 3
const val MARKET_PRICE_MAX_MINOR = 1_000_000_000_000L
val marketPriceCountries = listOf("kz", "kg", "tj", "uz", "ru")

/** An explicit place, never an inference from the user's account or IP address. */
@Serializable
data class MarketPriceRequest(
    val country: String = "kz",
    val city: String = "",
    val search: String = "",
    val itemId: String? = null,
    val gtin: String? = null,
    val after: String? = null,
    val limit: Int = MARKET_PRICE_PAGE_SIZE,
)

private fun insightText(value: String, maximum: Int): String? {
    if (value.length > maximum || value.any { it.code < 32 || it.code == 127 }) return null
    return value.trim().replace(Regex("[ \\u00a0]+"), " ")
}

fun MarketPriceRequest.normalizedMarketPriceRequest(): MarketPriceRequest? {
    val place = insightText(city, 120) ?: return null
    val term = insightText(search, 120) ?: return null
    if (country !in marketPriceCountries || limit !in 1..MARKET_PRICE_PAGE_SIZE ||
        (itemId != null && marketDiscoveryId(itemId) != itemId) ||
        (after != null && marketDiscoveryId(after) != after) ||
        (itemId != null && after != null) || (gtin != null && itemId == null)) return null
    val code = gtin?.let { marketInsightGtin(it) ?: return null }
    return copy(city = place, search = term, gtin = code)
}

/** Restricted-circulation/weight labels are local identities, even with a valid check digit. */
fun marketInsightGtin(raw: String): String? {
    val canonical = marketCanonicalGtin(raw) ?: return null
    // GS1 General Specifications §2.1.11: RCN-8 prefixes 0/2; RCN-12/13 prefixes
    // 02, 04 and 20–29. Padding one to 14 digits does not make its identity global.
    if (canonical.startsWith("000000") && canonical[6] in "02") return null
    val prefix = canonical.drop(1)
    if (prefix.startsWith('2') || prefix.startsWith("02") || prefix.startsWith("04")) return null
    return canonical
}

@Serializable
data class MarketPriceStatistics(
    val groups: Int,
    val lowestMinor: Long,
    val lowerQuartileMinor: Long,
    val medianMinor: Long,
    val upperQuartileMinor: Long,
    val highestMinor: Long,
)

@Serializable
data class MarketPriceInsightRow(
    val goodsItemId: String,
    val name: List<LocalizedStringDataModel>,
    val barcodes: List<String> = emptyList(),
    val basis: MarketShoppingBasis? = null,
    val unitName: List<LocalizedStringDataModel> = emptyList(),
    val ownPriceMinor: Long? = null,
    val referenceKind: String = "unpriced",
    val compatibleGroups: Int = 0,
    val statistics: MarketPriceStatistics? = null,
    val sampleLimited: Boolean = false,
    val updatedAtMillis: Long = 0,
    val ownCurrencyCode: String? = basis?.currencyCode,
)

@Serializable
data class MarketPriceInsightsPage(
    val accountId: String,
    val storeId: String,
    val request: MarketPriceRequest,
    val rows: List<MarketPriceInsightRow> = emptyList(),
    val nextId: String? = null,
    val totalItems: Int = 0,
    val checkedAtMillis: Long,
    val cities: List<String> = emptyList(),
    val moreCities: Boolean = false,
)

/** Server-internal sample. Group identities never enter the report DTO or the local report cache. */
data class MarketPriceObservation(val groupId: String, val offer: MarketOffer)

private fun positivePrice(value: Long?) = value != null && value in 1..MARKET_PRICE_MAX_MINOR

/** Exact comparisons of the quoted selling quantity. 1 kg and 1000 g are the same quantity;
 * 500 g and 1 kg are not (a bulk price cannot be extrapolated into a smaller sale).
 */
fun marketPriceBasisMatches(reference: MarketShoppingBasis, candidate: MarketShoppingBasis): Boolean {
    if (!reference.isValidMarketBasis() || !candidate.isValidMarketBasis() || reference.gtin == null ||
        reference.gtin != candidate.gtin || reference.currencyCode != candidate.currencyCode) return false
    if (reference.unitId == candidate.unitId) return reference.pricedAmount == candidate.pricedAmount
    if (!reference.unitId.isWeightMeasurementUnitId() || !candidate.unitId.isWeightMeasurementUnitId()) return false
    fun grams(b: MarketShoppingBasis): Pair<String, Int> {
        // Compare the canonical decimal values rather than multiplying binary floats:
        // 1.001 kg * 1000 can otherwise become 1000.9999999999999 g.
        val parts = b.pricedAmount.toString().lowercase().split('e')
        val number = parts[0]
        var power = (parts.getOrNull(1)?.toIntOrNull() ?: 0) - number.substringAfter('.', "").length +
            if (b.unitId == "1") 3 else 0
        var digits = number.replace(".", "").trimStart('0')
        while (digits.endsWith('0')) { digits = digits.dropLast(1); power++ }
        return digits to power
    }
    // No epsilon or rounding may make genuinely different package quantities equal.
    return grams(reference) == grams(candidate)
}

fun marketPriceStatistics(values: Collection<Long>): MarketPriceStatistics? {
    if (values.size < MARKET_PRICE_MIN_GROUPS || values.size > MARKET_PRICE_CANDIDATES_PER_GTIN ||
        values.any { !positivePrice(it) }) return null
    val sorted = values.sorted()
    val n = sorted.size
    val lower = sorted[(n - 1) / 2]
    val upper = sorted[n / 2]
    val median = lower + (upper - lower) / 2 + (upper - lower) % 2
    return MarketPriceStatistics(n, sorted.first(), sorted[(n + 3) / 4 - 1], median,
        sorted[(3 * n + 3) / 4 - 1], sorted.last())
}

/** Each independent store family contributes its lowest currently available comparable offer.
 * Never average currencies, unpriced rows, different packs, or the merchant's own branches.
 */
fun marketPriceInsight(
    row: MarketPriceInsightRow,
    observations: List<MarketPriceObservation>,
    ownGroupId: String,
    checkedAtMillis: Long,
): MarketPriceInsightRow {
    val basis = row.basis ?: return row.copy(compatibleGroups = 0, statistics = null)
    if (basis.gtin == null || marketInsightGtin(basis.gtin) != basis.gtin || !positivePrice(row.ownPriceMinor))
        return row.copy(compatibleGroups = 0, statistics = null)
    val eligible = observations.filter { candidate ->
        candidate.groupId.isNotBlank() && candidate.groupId != ownGroupId &&
            candidate.offer.storefront.published && candidate.offer.availability == MARKET_AVAILABILITY_RECORDED &&
            candidate.offer.checkedAtMillis == checkedAtMillis && positivePrice(candidate.offer.priceMinor) &&
            candidate.offer.shoppingBasis()?.let { marketPriceBasisMatches(basis, it) } == true
    }.groupBy { it.groupId }.mapValues { (_, offers) -> offers.minOf { requireNotNull(it.offer.priceMinor) } }
    // A malformed or unbounded input must not accidentally produce a confident summary.
    if (observations.size > MARKET_PRICE_CANDIDATES_PER_GTIN) return row.copy(compatibleGroups = 0, statistics = null, sampleLimited = true)
    return row.copy(compatibleGroups = eligible.size, statistics = marketPriceStatistics(eligible.values))
}

/** Hundredths of a percent, rounded half away from zero. All prices are bounded to avoid overflow. */
fun marketPriceDifferenceBasisPoints(price: Long?, reference: Long?): Long? {
    if (!positivePrice(price) || !positivePrice(reference)) return null
    val numerator = (price!! - reference!!) * 10_000L
    val magnitude = abs(numerator)
    val rounded = magnitude / reference + if (magnitude % reference >= (reference + 1) / 2) 1 else 0
    return if (numerator < 0) -rounded else rounded
}

fun MarketPriceStatistics.isValidMarketPriceStatistics(): Boolean =
    groups in MARKET_PRICE_MIN_GROUPS..MARKET_PRICE_CANDIDATES_PER_GTIN &&
        positivePrice(lowestMinor) && positivePrice(highestMinor) &&
        lowestMinor <= lowerQuartileMinor && lowerQuartileMinor <= medianMinor &&
        medianMinor <= upperQuartileMinor && upperQuartileMinor <= highestMinor

/** Shared trust boundary for network responses and durable caches. */
fun MarketPriceInsightsPage.isValidMarketPricePage(account: String, store: String, expected: MarketPriceRequest): Boolean =
    accountId == account && storeId == store && request == expected &&
        expected.normalizedMarketPriceRequest() == expected && checkedAtMillis > 0 &&
        rows.size <= expected.limit && totalItems >= rows.size && totalItems <= 1_000_000 &&
        rows.map { it.goodsItemId }.distinct().size == rows.size && rows.map { it.goodsItemId } == rows.map { it.goodsItemId }.sorted() &&
        (nextId == null || (expected.itemId == null && rows.size == expected.limit && nextId == rows.lastOrNull()?.goodsItemId)) &&
        rows.all { row ->
            marketDiscoveryId(row.goodsItemId) == row.goodsItemId && (expected.itemId == null || row.goodsItemId == expected.itemId) &&
                (expected.after == null || row.goodsItemId > expected.after) &&
                row.name.size <= 12 && row.name.all { it.value.length <= 300 && it.language.length <= 12 } &&
                row.barcodes.size <= 20 && row.barcodes.distinct().size == row.barcodes.size && row.barcodes.all { marketInsightGtin(it) == it } &&
                row.updatedAtMillis >= 0 && row.referenceKind in setOf("shelf", "catalogue", "unpriced") &&
                (row.referenceKind == "unpriced") == (row.ownPriceMinor == null) &&
                row.compatibleGroups in 0..MARKET_PRICE_CANDIDATES_PER_GTIN &&
                (row.ownPriceMinor == null || positivePrice(row.ownPriceMinor)) &&
                ((row.ownPriceMinor == null && row.ownCurrencyCode == null) ||
                    (row.ownPriceMinor != null && row.ownCurrencyCode?.matches(Regex("[A-Z]{3}")) == true)) &&
                row.unitName.size <= 12 && row.unitName.all { it.value.length <= 120 && it.language.length <= 12 } &&
                (row.basis == null || (row.basis.isValidMarketBasis() && row.basis.gtin in row.barcodes &&
                    row.basis.currencyCode == row.ownCurrencyCode && positivePrice(row.ownPriceMinor))) &&
                ((row.statistics == null && row.compatibleGroups < MARKET_PRICE_MIN_GROUPS) ||
                    (row.basis != null && row.statistics?.isValidMarketPriceStatistics() == true && row.statistics.groups == row.compatibleGroups))
        } && cities.size <= 200 && cities.distinct().size == cities.size && cities.all { it.isNotBlank() && insightText(it, 120) == it }

/** The history is an observation, not proof that the same shops changed their prices. */
fun MarketPriceInsightRow.previousObservedMedian(previous: MarketPriceInsightsPage?, current: MarketPriceInsightsPage): Long? {
    if (previous == null || previous.storeId != current.storeId || previous.accountId != current.accountId ||
        previous.request.country != current.request.country || previous.request.city != current.request.city ||
        previous.checkedAtMillis >= current.checkedAtMillis) return null
    return previous.rows.firstOrNull { it.goodsItemId == goodsItemId && it.basis == basis }?.statistics?.medianMinor
}
