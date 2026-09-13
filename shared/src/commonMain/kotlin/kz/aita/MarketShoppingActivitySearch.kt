package kz.aita

import kotlinx.serialization.Serializable

const val MARKET_ACTIVITY_SEARCH_PROTOCOL = 1
const val MARKET_ACTIVITY_RESULT_APPLIED = "applied"
const val MARKET_ACTIVITY_RESULT_REJECTED = "rejected"
const val MARKET_ACTIVITY_RESULT_UNCHANGED = "unchanged"
const val MARKET_ACTIVITY_RESULT_CANCELLED = "cancelled"

/** Exact command reference, never a prefix, token, hash, or arbitrary SQL search expression. */
fun normalizedMarketChangeReference(value: String): String? =
    value.takeIf { it.length <= 64 }?.trim()?.let(::marketDiscoveryId)

@Serializable
data class MarketShoppingActivityFilter(
    val kind: String? = null,
    val result: String? = null,
    val commandId: String? = null
)

@Serializable
data class MarketShoppingActivityCursor(val recordedAtMillis: Long, val commandId: String)

/** A cursor is a navigation boundary, NOT a server snapshot or authority to another account.
 * Each read returns at most 20 immutable summaries. Newer reads take the nearest newer records;
 * both directions return descending order. No offset, growing history array or 200-record cutoff.
 */
@Serializable
data class MarketShoppingActivitySearchRequest(
    val filter: MarketShoppingActivityFilter = MarketShoppingActivityFilter(),
    val boundary: MarketShoppingActivityCursor? = null,
    val newer: Boolean = false
)

@Serializable
data class MarketShoppingActivitySearchPage(
    val accountId: String,
    val request: MarketShoppingActivitySearchRequest,
    val entries: List<MarketShoppingActivityEntry>,
    val hasOlder: Boolean,
    val hasNewer: Boolean,
    val checkedAtMillis: Long,
    val protocolVersion: Int = MARKET_ACTIVITY_SEARCH_PROTOCOL
)

fun MarketShoppingActivityEntry.activityCursor() = MarketShoppingActivityCursor(recordedAtMillis, commandId)

fun MarketShoppingActivityCursor.isValidActivityCursor(): Boolean =
    recordedAtMillis > 0 && marketDiscoveryId(commandId) == commandId

/** Canonical UUID strings sort the same way as their unsigned bytes. Tie-breaking is required:
 * many commands can share the same millisecond. Never paginate only on a timestamp.
 */
fun MarketShoppingActivityCursor.compareActivityCursor(other: MarketShoppingActivityCursor): Int =
    recordedAtMillis.compareTo(other.recordedAtMillis).takeIf { it != 0 } ?: commandId.compareTo(other.commandId)

fun MarketShoppingActivitySearchRequest.normalizedActivitySearch(): MarketShoppingActivitySearchRequest? {
    if (filter.kind != null && filter.kind !in setOf(MARKET_ACTIVITY_BASKET, MARKET_ACTIVITY_REPLACE,
            MARKET_ACTIVITY_REMOVE, MARKET_ACTIVITY_QUANTITY)) return null
    if (filter.result != null && filter.result !in setOf(MARKET_ACTIVITY_RESULT_APPLIED,
            MARKET_ACTIVITY_RESULT_REJECTED, MARKET_ACTIVITY_RESULT_UNCHANGED, MARKET_ACTIVITY_RESULT_CANCELLED)) return null
    val reference = filter.commandId?.let { normalizedMarketChangeReference(it) ?: return null }
    // A direct reference deliberately has no hidden filters that could disguise an owned record
    // as missing. UI clears filters explicitly when Find reference is pressed.
    if (reference != null && (filter.kind != null || filter.result != null || boundary != null || newer)) return null
    if (newer && boundary == null) return null
    if (boundary?.isValidActivityCursor() == false) return null
    return copy(filter = filter.copy(commandId = reference))
}

fun MarketShoppingActivityFilter.matchesActivity(entry: MarketShoppingActivityEntry): Boolean =
    (commandId == null || commandId == entry.commandId) && (kind == null || kind == entry.kind) && when (result) {
        null -> true
        MARKET_ACTIVITY_RESULT_APPLIED -> entry.accepted && entry.changed
        MARKET_ACTIVITY_RESULT_CANCELLED -> !entry.accepted && entry.errorKey == "market.shopping_cancelled"
        MARKET_ACTIVITY_RESULT_REJECTED -> !entry.accepted
        MARKET_ACTIVITY_RESULT_UNCHANGED -> entry.accepted && !entry.changed
        else -> false
    }

fun MarketShoppingActivitySearchPage.isValidActivitySearchPage(
    account: String, wanted: MarketShoppingActivitySearchRequest
): Boolean {
    val normalized = wanted.normalizedActivitySearch() ?: return false
    if (protocolVersion != MARKET_ACTIVITY_SEARCH_PROTOCOL || accountId != account || request != normalized ||
        checkedAtMillis <= 0 || entries.size > MARKET_SHOPPING_ACTIVITY_PAGE_SIZE ||
        entries.map { it.commandId }.distinct().size != entries.size) return false
    if (entries.isEmpty() && (hasOlder || hasNewer)) return false
    if (normalized.filter.commandId != null && (entries.size > 1 || hasOlder || hasNewer)) return false
    if (normalized.boundary == null && hasNewer) return false
    if ((if (normalized.newer) hasNewer else hasOlder) && entries.size != MARKET_SHOPPING_ACTIVITY_PAGE_SIZE) return false
    if (entries.any { !it.isValidShoppingActivityEntry() || it.details != null || !normalized.filter.matchesActivity(it) }) return false
    if (entries.zipWithNext().any { (a, b) -> a.activityCursor().compareActivityCursor(b.activityCursor()) <= 0 }) return false
    val boundary = normalized.boundary
    return boundary == null || entries.all {
        val comparison = it.activityCursor().compareActivityCursor(boundary)
        if (normalized.newer) comparison > 0 else comparison < 0
    }
}

/** Derived only from a validated current page, never guessed from a full-page row count. */
fun MarketShoppingActivitySearchPage.olderActivityRequest(): MarketShoppingActivitySearchRequest? =
    entries.lastOrNull()?.takeIf { hasOlder }?.let { request.copy(boundary = it.activityCursor(), newer = false) }

fun MarketShoppingActivitySearchPage.newerActivityRequest(): MarketShoppingActivitySearchRequest? =
    entries.firstOrNull()?.takeIf { hasNewer }?.let { request.copy(boundary = it.activityCursor(), newer = true) }
