package kz.aita

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlin.math.abs

const val MARKET_PRICE_WATCH_MAX_ITEMS = 10
const val MARKET_PRICE_WATCH_INTERVAL = 86_400_000L
internal const val MARKET_PRICE_ALERT_MAX_CHARS = 32_000
val marketPriceWatchThresholds = listOf(100, 300, 500, 1000, 2000)

@Serializable
data class MarketPriceWatchItem(val itemId: String, val gtin: String)

@Serializable
data class MarketPriceWatchSettings(
    val country: String = "kz",
    val city: String = "",
    val items: List<MarketPriceWatchItem> = emptyList(),
    val thresholdBasisPoints: Int = 500,
) {
    fun normalized(): MarketPriceWatchSettings? {
        val scope = MarketPriceRequest(country, city).normalizedMarketPriceRequest() ?: return null
        if (items.size > MARKET_PRICE_WATCH_MAX_ITEMS || items.map { it.itemId }.distinct().size != items.size ||
            thresholdBasisPoints !in marketPriceWatchThresholds || items.any { marketDiscoveryId(it.itemId) != it.itemId }) return null
        val clean = items.map { it.copy(gtin = marketInsightGtin(it.gtin) ?: return null) }.sortedBy { it.itemId }
        return copy(city = scope.city, items = clean)
    }
    fun reportRequest() = MarketPriceRequest(country, city)
}

@Serializable
data class MarketPriceWatchObservation(
    val itemId: String,
    val name: List<LocalizedStringDataModel> = emptyList(),
    val basis: MarketShoppingBasis? = null,
    val medianMinor: Long? = null,
    val baselineMinor: Long? = null,
    val groups: Int = 0,
    val checkedAtMillis: Long = 0,
    val status: String = "waiting",
)

@Serializable
data class MarketPriceWatchState(
    val accountId: String,
    val storeId: String,
    val revision: Long = 0,
    val enabled: Boolean = false,
    val settings: MarketPriceWatchSettings = MarketPriceWatchSettings(),
    val observations: List<MarketPriceWatchObservation> = emptyList(),
    val checkedAtMillis: Long? = null,
    val nextCheckAtMillis: Long? = null,
    val updatedAtMillis: Long = 0,
)

@Serializable
data class MarketPriceWatchUpdate(
    val requestId: String,
    val expectedRevision: Long,
    val enabled: Boolean,
    val settings: MarketPriceWatchSettings,
)

@Serializable
data class MarketPriceChange(
    val itemId: String,
    val name: List<LocalizedStringDataModel>,
    val basis: MarketShoppingBasis,
    val previousMinor: Long,
    val currentMinor: Long,
)

/** A bounded historical snapshot: opening an old notification must not replace its prices
 * with today's report. Only this user's watched items and aggregate prices are included.
 */
@Serializable
data class MarketPriceAlertDetails(
    val accountId: String,
    val storeId: String,
    val country: String,
    val city: String,
    val checkedAtMillis: Long,
    val changes: List<MarketPriceChange>,
    val schema: Int = 1,
)

fun NotificationDataModel.marketPriceAlertDetails(): MarketPriceAlertDetails? {
    if (category != "market_prices") return null
    val encoded = metadata["priceChanges"]?.takeIf { it.length <= MARKET_PRICE_ALERT_MAX_CHARS } ?: return null
    val details = runCatching { jsonBase.decodeFromString<MarketPriceAlertDetails>(encoded) }.getOrNull() ?: return null
    return details.takeIf { value ->
        value.schema == 1 && value.accountId == userId && value.storeId == storeId &&
            marketDiscoveryId(value.accountId) == value.accountId && marketDiscoveryId(value.storeId) == value.storeId &&
            value.checkedAtMillis > 0 && value.checkedAtMillis == createdAtMillis &&
            MarketPriceRequest(value.country, value.city).normalizedMarketPriceRequest() == MarketPriceRequest(value.country, value.city) &&
            value.changes.size in 1..MARKET_PRICE_WATCH_MAX_ITEMS && value.changes.map { it.itemId }.distinct().size == value.changes.size &&
            value.changes.all { change -> marketDiscoveryId(change.itemId) == change.itemId &&
                change.name.size <= 6 && change.name.all { it.language.length <= 12 && it.value.length <= 120 } &&
                change.basis.isValidMarketBasis() && marketInsightGtin(change.basis.gtin.orEmpty()) == change.basis.gtin && change.basis.gtin != null &&
                change.previousMinor in 1..MARKET_PRICE_MAX_MINOR && change.currentMinor in 1..MARKET_PRICE_MAX_MINOR &&
                change.previousMinor != change.currentMinor }
    }
}

data class MarketPriceWatchEvaluation(val observations: List<MarketPriceWatchObservation>, val changes: List<MarketPriceChange>) {
    val changedItemIds: List<String> get() = changes.map { it.itemId }
}

private fun List<LocalizedStringDataModel>.priceWatchDisplayNames(): List<LocalizedStringDataModel> {
    fun clean(value: String, maximum: Int): String {
        val bounded = value.filterNot { it.code < 32 || it.code in 127..159 }.take(maximum)
        // Imported titles may contain emoji at the truncation boundary. Never persist half
        // of a UTF-16 pair, or expand invisible control characters into a huge JSON string.
        return if (bounded.lastOrNull()?.isHighSurrogate() == true) bounded.dropLast(1) else bounded
    }
    return take(6).map { LocalizedStringDataModel(clean(it.language, 12), clean(it.value, 120)) }
}

/** Compare a complete checked snapshot with the last notified baseline. Unknown prices never
 * become zero; changed currencies/packs establish a new baseline rather than a false alert.
 */
fun evaluateMarketPriceWatch(
    settings: MarketPriceWatchSettings,
    previous: List<MarketPriceWatchObservation>,
    page: MarketPriceInsightsPage,
): MarketPriceWatchEvaluation {
    require(settings.normalized() == settings)
    require(page.request == settings.reportRequest() && page.isValidMarketPricePage(page.accountId, page.storeId, page.request))
    val rows = page.rows.associateBy { it.goodsItemId }
    val old = previous.associateBy { it.itemId }
    val changed = mutableListOf<MarketPriceChange>()
    val values = settings.items.map { selected ->
        val row = rows[selected.itemId]
        val before = old[selected.itemId]
        val basis = row?.basis?.takeIf { it.gtin == selected.gtin }
        val sameBasis = basis != null && before?.basis?.let { marketPriceBasisMatches(basis, it) } == true
        val status = when {
            row == null -> "missing"
            basis == null -> "unpriced"
            row.sampleLimited -> "limited"
            row.statistics == null -> "insufficient"
            else -> "watching"
        }
        val median = row?.statistics?.medianMinor?.takeIf { status == "watching" }
        var baseline = before?.baselineMinor?.takeIf { sameBasis && it in 1..MARKET_PRICE_MAX_MINOR }
        if (median != null) {
            if (baseline == null) baseline = median
            else if (abs(median - baseline) * 10_000L >= baseline * settings.thresholdBasisPoints) {
                changed += MarketPriceChange(selected.itemId, requireNotNull(row).name.priceWatchDisplayNames(),
                    requireNotNull(basis), baseline, median)
                baseline = median
            }
        }
        MarketPriceWatchObservation(selected.itemId, (row?.name ?: before?.name.orEmpty()).priceWatchDisplayNames(), basis, median, baseline,
            row?.compatibleGroups ?: 0, page.checkedAtMillis, status)
    }
    return MarketPriceWatchEvaluation(values, changed)
}

fun MarketPriceWatchState.isValidFor(account: String, store: String): Boolean =
    accountId == account && storeId == store && revision >= 0 && settings.normalized() == settings &&
        (!enabled || settings.items.isNotEmpty()) && observations.size <= MARKET_PRICE_WATCH_MAX_ITEMS &&
        observations.map { it.itemId }.distinct().size == observations.size &&
        observations.all { row -> settings.items.any { it.itemId == row.itemId } &&
            row.status in setOf("waiting", "watching", "missing", "unpriced", "limited", "insufficient") &&
            row.name.size <= 12 && row.name.all { it.language.length <= 12 && it.value.length <= 300 } &&
            (row.basis == null || (row.basis.isValidMarketBasis() && row.basis.gtin == settings.items.first { it.itemId == row.itemId }.gtin)) &&
            row.groups in 0..MARKET_PRICE_CANDIDATES_PER_GTIN &&
            (row.status != "watching" || (row.basis != null && row.medianMinor != null && row.baselineMinor != null && row.groups >= MARKET_PRICE_MIN_GROUPS)) &&
            (row.medianMinor == null || row.medianMinor in 1..MARKET_PRICE_MAX_MINOR) &&
            (row.baselineMinor == null || row.baselineMinor in 1..MARKET_PRICE_MAX_MINOR) && row.checkedAtMillis >= 0 } &&
        (checkedAtMillis == null || checkedAtMillis > 0) && (nextCheckAtMillis == null || nextCheckAtMillis > 0) &&
        (enabled == (nextCheckAtMillis != null)) && updatedAtMillis >= 0
