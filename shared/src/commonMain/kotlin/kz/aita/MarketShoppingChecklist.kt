package kz.aita

import kotlinx.serialization.Serializable

const val MARKET_CHECKLIST_COLLECT = "collect"
const val MARKET_CHECKLIST_UNCHECK = "uncheck"
const val MARKET_CHECKLIST_REMOVE = "remove_collected"
const val MARKET_ACTIVITY_CHECKLIST = "checklist"

/** Personal shopping progress only. A checkmark never allocates stock or records a payment. */
@Serializable
data class MarketChecklistChange(val offerIds: List<String>, val action: String)

fun MarketChecklistChange.isValidChecklistChange(): Boolean =
    action in setOf(MARKET_CHECKLIST_COLLECT, MARKET_CHECKLIST_UNCHECK, MARKET_CHECKLIST_REMOVE) &&
        offerIds.size in 1..MARKET_SHOPPING_MAX_LINES && offerIds.distinct().size == offerIds.size &&
        offerIds.all { marketDiscoveryId(it) == it }

fun MarketChecklistChange.checklistIntentError(snapshot: MarketShoppingSnapshot, expectedRevision: Long): String? {
    if (!isValidChecklistChange()) return "market.checklist_invalid"
    if (snapshot.revision != expectedRevision || expectedRevision == Long.MAX_VALUE) return "market.shopping_changed"
    val rows = snapshot.lines.associateBy { it.line.offerId }
    if (offerIds.any { it !in rows }) return "market.shopping_changed"
    if (action == MARKET_CHECKLIST_REMOVE && offerIds.any { rows[it]?.line?.collected != true })
        return "market.checklist_changed"
    return null
}

/** Freeze the reviewed rows for bulk removal/reset; a later screen callback cannot select new rows. */
data class MarketChecklistReview(
    val accountId: String,
    val revision: Long,
    val lines: List<MarketShoppingLine>,
    val action: String
) {
    fun matches(snapshot: MarketShoppingSnapshot?): Boolean = accountId.isNotBlank() && snapshot != null && snapshot.userId == accountId &&
        snapshot.revision == revision && lines.isNotEmpty() &&
        lines.all { line -> snapshot.lines.singleOrNull { it.line.offerId == line.offerId }?.line == line }

    fun command(snapshot: MarketShoppingSnapshot?, commandId: String): MarketShoppingCommand? {
        if (!matches(snapshot)) return null
        val change = MarketChecklistChange(lines.map { it.offerId }, action)
        if (change.checklistIntentError(requireNotNull(snapshot), revision) != null) return null
        return MarketShoppingCommand(commandId, revision, "", 0, checklistChange = change)
            .takeIf { it.isValidMarketShoppingCommand() }
    }
}

fun MarketShoppingSnapshot.reviewChecklist(offerIds: List<String>, action: String): MarketChecklistReview? {
    if (userId.isBlank()) return null
    val change = MarketChecklistChange(offerIds, action)
    if (change.checklistIntentError(this, revision) != null) return null
    val byId = lines.associateBy { it.line.offerId }
    return MarketChecklistReview(userId, revision, offerIds.map { requireNotNull(byId[it]).line }, action)
}

fun MarketShoppingCommand.matchesChecklistOutcome(outcome: MarketShoppingOutcome): Boolean {
    val change = checklistChange ?: return false
    val applied = outcome.appliedRevision ?: return false
    if (outcome.snapshot.revision > applied) return true
    val rows = outcome.snapshot.lines.associateBy { it.line.offerId }
    return when (change.action) {
        MARKET_CHECKLIST_REMOVE -> change.offerIds.none { it in rows }
        MARKET_CHECKLIST_COLLECT -> change.offerIds.all { rows[it]?.line?.collected == true }
        MARKET_CHECKLIST_UNCHECK -> change.offerIds.all { rows[it]?.line?.collected == false }
        else -> false
    }
}

data class MarketTripProgress(val total: Int, val collected: Int) {
    val remaining: Int get() = total - collected
    val fraction: Float get() = if (total == 0) 0f else collected.toFloat() / total
}

fun MarketShoppingSnapshot.tripProgress(storeId: String? = null): MarketTripProgress {
    val rows = lines.filter { storeId == null || it.line.storeId == storeId }
    return MarketTripProgress(rows.size, rows.count { it.line.collected })
}
