package kz.aita

const val MARKET_COMPARISON_FRESH_MILLIS = 60_000L

/** Local, UI-confined identity of one complete comparison. Invalidate before queuing a read,
 * including during the debounce, so an A -> B -> A edit cannot revive an earlier reply. This
 * is a pre-submission check only; pending Retry/Check/Cancel retain their original journal.
 */
class MarketComparisonReadFence {
    class Stamp internal constructor(
        internal val token: Any,
        internal val request: MarketComparisonWindowRequest,
        internal val signal: Long
    )
    private var token = Any()

    fun invalidate() { token = Any() }

    fun capture(request: MarketComparisonWindowRequest, signal: Long): Stamp =
        Stamp(token, requireNotNull(request.normalizedComparisonWindowRequest()), signal)

    fun isCurrent(stamp: Stamp?, wanted: MarketComparisonWindowRequest?, signal: Long): Boolean =
        stamp != null && stamp.token === token && signal >= 0L && stamp.signal == signal &&
            wanted?.normalizedComparisonWindowRequest()?.let { it == stamp.request } == true

    fun canUse(stamp: Stamp?, result: MarketComparisonWindowResult?, wanted: MarketComparisonWindowRequest?,
        accountId: String, signal: Long, current: MarketShoppingSnapshot?, ageMillis: Long,
        blocked: Boolean): Boolean {
        if (blocked || result == null || result.accountId != accountId || accountId.isBlank() ||
            result.request != wanted?.normalizedComparisonWindowRequest() || !isCurrent(stamp, wanted, signal) ||
            ageMillis !in 0 until MARKET_COMPARISON_FRESH_MILLIS) return false
        val selection = result.request.selection
        if (selection.shoppingRevision == null) return true
        // Compare retained intent, not its live quote: a price-only refresh must not invalidate
        // the source line, while even a same-revision corrupted/replaced line must not pass.
        return current?.userId == accountId && current.revision == selection.shoppingRevision &&
            current.lines.singleOrNull { it.line.offerId == selection.offerId }?.line == result.reference.line
    }

    fun canConfirm(stamp: Stamp?, result: MarketComparisonWindowResult?, candidate: MarketShoppingQuotedLine,
        wanted: MarketComparisonWindowRequest?, accountId: String, signal: Long,
        current: MarketShoppingSnapshot?, ageMillis: Long, blocked: Boolean): Boolean =
        canUse(stamp, result, wanted, accountId, signal, current, ageMillis, blocked) &&
            result != null && current != null && candidate in result.matches &&
            result.request.selection.reviewedReplacement(current, candidate, "review") != null
}
