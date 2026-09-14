package kz.aita

const val MARKET_BASKET_PLAN_FRESH_MILLIS = 60_000L

/** UI-confined generation for a whole basket read. Retire the old generation BEFORE enqueueing
 * a refresh, not after its debounce/cooldown. A -> B -> A scope changes cannot revive a reply.
 * Neither this fence nor the plan grants stock, a reservation or permission to replay a write.
 */
class MarketBasketPlanReadFence {
    class Stamp internal constructor(
        internal val token: Any,
        internal val request: MarketBasketRequest,
        internal val signal: Long
    )
    private var token = Any()

    fun invalidate() { token = Any() }

    fun capture(request: MarketBasketRequest, signal: Long): Stamp =
        Stamp(token, requireNotNull(request.normalizedBasketRequest()), signal)

    fun isCurrent(stamp: Stamp?, wanted: MarketBasketRequest?, signal: Long): Boolean =
        stamp != null && stamp.token === token && signal >= 0L && stamp.signal == signal &&
            wanted?.normalizedBasketRequest()?.let { it == stamp.request } == true

    fun canUse(stamp: Stamp?, result: MarketBasketResult?, wanted: MarketBasketRequest?, signal: Long,
        current: MarketShoppingSnapshot?, ageMillis: Long, blocked: Boolean): Boolean =
        accepts(stamp, result, wanted, signal, current, ageMillis, MARKET_BASKET_PLAN_FRESH_MILLIS, blocked)

    /** The confirmation has a longer, existing review deadline, measured from the SAME read
     * start, not from opening/reopening the panel. Check the exact frozen command at click time.
     * This applies only before NEW submission. Pending Retry/Check/Cancel keep their own journal.
     */
    fun canConfirm(stamp: Stamp?, result: MarketBasketResult?, command: MarketShoppingCommand,
        wanted: MarketBasketRequest?, signal: Long, current: MarketShoppingSnapshot?,
        ageMillis: Long, blocked: Boolean): Boolean {
        if (!accepts(stamp, result, wanted, signal, current, ageMillis,
                MARKET_BASKET_REVIEW_MAX_AGE_MILLIS, blocked)) return false
        val basket = command.basketChange ?: return false
        return result?.reviewedBasketCommand(basket.currencyCode, basket.kind, command.commandId) == command
    }

    private fun accepts(stamp: Stamp?, result: MarketBasketResult?, wanted: MarketBasketRequest?,
        signal: Long, current: MarketShoppingSnapshot?, ageMillis: Long, maxAgeMillis: Long,
        blocked: Boolean): Boolean = !blocked && result != null &&
        result.request == wanted?.normalizedBasketRequest() && isCurrent(stamp, wanted, signal) &&
        ageMillis in 0 until maxAgeMillis && result.snapshot.hasSameBasketIntent(current)
}

/** Retained intent only: a price/availability refresh is not a list edit. A different account,
 * revision, line, quantity or order cannot be substituted under an already rendered plan.
 */
internal fun MarketShoppingSnapshot.hasSameBasketIntent(current: MarketShoppingSnapshot?): Boolean =
    userId.isNotBlank() && current != null && current.userId == userId && current.revision == revision &&
        current.lines.map { it.line } == lines.map { it.line }
