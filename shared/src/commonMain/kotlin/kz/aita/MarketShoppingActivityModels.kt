package kz.aita

import kotlinx.serialization.Serializable

const val MARKET_SHOPPING_ACTIVITY_PROTOCOL = 1
const val MARKET_SHOPPING_ACTIVITY_PAGE_SIZE = 20
const val MARKET_SHOPPING_ACTIVITY_MAX_WINDOW = 200
const val MARKET_ACTIVITY_BASKET = "basket"
const val MARKET_ACTIVITY_REPLACE = "replace"
const val MARKET_ACTIVITY_REMOVE = "remove"
const val MARKET_ACTIVITY_QUANTITY = "quantity"

/** Public labels copied from this buyer's intent INSIDE the mutation transaction. Never join a
 * historical record to today's private catalogue or rewrite it after a seller renames a product.
 */
@Serializable
data class MarketShoppingActivityLine(val before: MarketShoppingLine? = null, val after: MarketShoppingLine? = null)

@Serializable
data class MarketShoppingActivityDetails(val lines: List<MarketShoppingActivityLine>)

/** A recorded command is not a purchase, payment, reservation, or reversible checkout receipt. */
@Serializable
data class MarketShoppingActivityEntry(
    val commandId: String,
    val recordedAtMillis: Long,
    val expectedRevision: Long,
    val accepted: Boolean,
    val appliedRevision: Long? = null,
    val errorKey: String? = null,
    val kind: String,
    val requestedUnits: Int,
    val reviewedCurrency: String? = null,
    val reviewedSubtotalMinor: Long? = null,
    val reviewedLines: Int = 1,
    val changedLines: Int = 0,
    // Missing for older records and rejections; absence is NOT permission to invent labels.
    val details: MarketShoppingActivityDetails? = null,
    val detailsRecorded: Boolean = details != null,
    val previewTitle: String? = null
) {
    val changed: Boolean get() = accepted && appliedRevision != expectedRevision
}

@Serializable
data class MarketShoppingActivityRequest(val limit: Int = MARKET_SHOPPING_ACTIVITY_PAGE_SIZE)

@Serializable
data class MarketShoppingActivityPage(
    val accountId: String,
    val request: MarketShoppingActivityRequest,
    val entries: List<MarketShoppingActivityEntry>,
    val hasMore: Boolean,
    val checkedAtMillis: Long,
    val protocolVersion: Int = MARKET_SHOPPING_ACTIVITY_PROTOCOL
)

/** null outcome means "not recorded in this read's snapshot", never "definitely not sent".
 * Another connection may still commit after the read. Only a matching recorded outcome retires
 * the journal's original command. Checking this result MUST NOT invoke a mutation endpoint.
 */
@Serializable
data class MarketShoppingCommandLookup(
    val commandId: String,
    val checkedAtMillis: Long,
    val outcome: MarketShoppingOutcome? = null,
    val protocolVersion: Int = MARKET_SHOPPING_ACTIVITY_PROTOCOL
)

fun MarketShoppingActivityRequest.isValidShoppingActivityRequest() =
    limit in MARKET_SHOPPING_ACTIVITY_PAGE_SIZE..MARKET_SHOPPING_ACTIVITY_MAX_WINDOW &&
        limit % MARKET_SHOPPING_ACTIVITY_PAGE_SIZE == 0

private fun MarketShoppingLine.validActivityLine(): Boolean =
    marketDiscoveryId(offerId) == offerId && marketDiscoveryId(storeId) == storeId &&
        title.isNotBlank() && title.length <= 180 && shopName.isNotBlank() && shopName.length <= 120 &&
        units in 1..MARKET_SHOPPING_MAX_UNITS && basis.isValidMarketBasis() && updatedAtMillis >= 0L

fun MarketShoppingActivityEntry.isValidShoppingActivityEntry(): Boolean {
    if (marketDiscoveryId(commandId) != commandId || recordedAtMillis <= 0L || expectedRevision < 0L ||
        kind !in setOf(MARKET_ACTIVITY_BASKET, MARKET_ACTIVITY_REPLACE,
            MARKET_ACTIVITY_REMOVE, MARKET_ACTIVITY_QUANTITY) || requestedUnits !in 0..MARKET_SHOPPING_MAX_UNITS ||
        reviewedLines !in 1..MARKET_SHOPPING_MAX_LINES || changedLines !in 0..reviewedLines) return false
    if (accepted) {
        if (expectedRevision == Long.MAX_VALUE || errorKey != null || appliedRevision == null ||
            appliedRevision !in expectedRevision..expectedRevision + 1L) return false
    } else if (appliedRevision != null || errorKey?.matches(Regex("market\\.[a-z0-9_]+")) != true || detailsRecorded || details != null) return false
    if (details != null && !detailsRecorded) return false
    if (previewTitle != null && (previewTitle.isBlank() || previewTitle.length > 180)) return false
    if (reviewedCurrency != null && !reviewedCurrency.matches(Regex("[A-Z]{3}"))) return false
    if (reviewedSubtotalMinor != null && reviewedSubtotalMinor < 0L) return false
    if (kind == MARKET_ACTIVITY_BASKET) {
        if (requestedUnits != 0 || reviewedCurrency == null || reviewedSubtotalMinor == null || changedLines == 0) return false
        if (accepted && !changed) return false
    } else {
        if (reviewedLines != 1 || changedLines > 1 || (kind == MARKET_ACTIVITY_REMOVE) != (requestedUnits == 0)) return false
        if (kind == MARKET_ACTIVITY_REPLACE && (reviewedSubtotalMinor == null || (accepted && !changed))) return false
    }
    val history = details ?: return true
    if (history.lines.size > reviewedLines || (changed && history.lines.isEmpty())) return false
    if (history.lines.any { it.before == null && it.after == null ||
            it.before?.validActivityLine() == false || it.after?.validActivityLine() == false }) return false
    val previous = history.lines.mapNotNull { it.before?.offerId }
    val following = history.lines.mapNotNull { it.after?.offerId }
    if (previous.distinct().size != previous.size || following.distinct().size != following.size) return false
    return when (kind) {
        MARKET_ACTIVITY_BASKET -> history.lines.size == reviewedLines && history.lines.all {
            val before = it.before; val after = it.after
            before != null && after != null && before.units == after.units && before.basis == after.basis &&
                after.basis.currencyCode == reviewedCurrency
        } && history.lines.count { it.before?.offerId != it.after?.offerId } == changedLines
        MARKET_ACTIVITY_REPLACE -> history.lines.size == 1 && history.lines.single().let {
            val before = it.before; val after = it.after
            before != null && after != null && before.offerId != after.offerId && before.units == after.units && before.basis == after.basis
        }
        MARKET_ACTIVITY_REMOVE -> history.lines.all { it.after == null && it.before != null }
        else -> history.lines.size == 1 && history.lines.single().let {
            val next = it.after
            next != null && next.units == requestedUnits && (it.before == null || it.before.offerId == next.offerId)
        }
    }
}

fun MarketShoppingActivityPage.isValidShoppingActivityPage(account: String, wanted: MarketShoppingActivityRequest): Boolean =
    protocolVersion == MARKET_SHOPPING_ACTIVITY_PROTOCOL && accountId == account && request == wanted &&
        wanted.isValidShoppingActivityRequest() && checkedAtMillis > 0L && entries.size <= wanted.limit &&
        (!hasMore || entries.size == wanted.limit) && entries.map { it.commandId }.distinct().size == entries.size &&
        entries.all { it.isValidShoppingActivityEntry() && it.details == null } && entries.zipWithNext().all { (a, b) ->
            a.recordedAtMillis > b.recordedAtMillis || (a.recordedAtMillis == b.recordedAtMillis && a.commandId > b.commandId)
        }

/** Capture before and after intent, not changing catalogue projections or a speculative result.
 * Rejected commands do not call this function. They record their existing rejection key only.
 */
fun MarketShoppingCommand.shoppingActivityDetails(
    before: List<MarketShoppingLine>, after: List<MarketShoppingLine>
): MarketShoppingActivityDetails {
    val old = before.associateBy { it.offerId }; val next = after.associateBy { it.offerId }
    val basket = basketChange
    val rows = if (basket != null) basket.lines.map {
        MarketShoppingActivityLine(requireNotNull(old[it.sourceOfferId]), requireNotNull(next[it.targetOfferId]))
    } else {
        val previous = old[replaceOfferId ?: offerId]
        val following = next[offerId].takeIf { units > 0 }
        if (previous == null && following == null) emptyList() else listOf(MarketShoppingActivityLine(previous, following))
    }
    return MarketShoppingActivityDetails(rows)
}

/** Shared by mutation acknowledgements AND read-only result recovery. */
fun MarketShoppingCommand.isValidShoppingOutcome(outcome: MarketShoppingOutcome, account: String): Boolean {
    if (!isValidMarketShoppingCommand() || outcome.commandId != commandId ||
        !outcome.snapshot.isValidMarketShoppingSnapshot(account)) return false
    if (!outcome.accepted) return outcome.appliedRevision == null &&
        outcome.errorKey?.matches(Regex("market\\.[a-z0-9_]+")) == true
    val applied = outcome.appliedRevision ?: return false
    if (outcome.errorKey != null || expectedRevision == Long.MAX_VALUE ||
        applied !in expectedRevision..expectedRevision + 1L || outcome.snapshot.revision < applied) return false
    if (basketChange != null) return matchesBasketOutcome(outcome)
    if (replaceOfferId != null && applied != expectedRevision + 1L) return false
    // A later snapshot may reflect another device's subsequent edits. At the applied revision,
    // however, even an ordinary quantity/removal reply must actually contain its claimed effect.
    if (outcome.snapshot.revision > applied) return true
    val lines = outcome.snapshot.lines.map { it.line }
    if (units == 0) return lines.none { it.offerId == offerId }
    val target = lines.singleOrNull { it.offerId == offerId } ?: return false
    return target.units == units && target.basis == basis &&
        (replaceOfferId == null || lines.none { it.offerId == replaceOfferId })
}

fun MarketShoppingCommandLookup.isValidShoppingLookup(command: MarketShoppingCommand, account: String): Boolean =
    protocolVersion == MARKET_SHOPPING_ACTIVITY_PROTOCOL && commandId == command.commandId && checkedAtMillis > 0L &&
        (outcome == null || command.isValidShoppingOutcome(outcome, account))
