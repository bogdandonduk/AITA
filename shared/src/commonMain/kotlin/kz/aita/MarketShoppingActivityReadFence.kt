package kz.aita

/** UI-confined generation for an activity page. Refresh/filter changes retire a read BEFORE its
 * debounce starts. A cursor is just a boundary: a newer event must not evict an older immutable
 * page. Head/exact-reference reads do track events so a late empty result cannot claim freshness.
 * No wall-clock expiry: these are historical records, not current prices or mutation authority.
 */
class MarketShoppingActivityReadFence {
    class Stamp internal constructor(
        internal val token: Any,
        internal val request: MarketShoppingActivitySearchRequest,
        internal val signal: Long
    )
    private var token = Any()

    fun invalidate() { token = Any() }

    fun capture(request: MarketShoppingActivitySearchRequest, signal: Long): Stamp =
        Stamp(token, requireNotNull(request.normalizedActivitySearch()), signal)

    fun isCurrent(stamp: Stamp?, wanted: MarketShoppingActivitySearchRequest, signal: Long): Boolean =
        stamp != null && stamp.token === token && stamp.request == wanted.normalizedActivitySearch() &&
            signal >= 0L && stamp.signal >= 0L && (stamp.request.boundary != null || stamp.signal == signal)

    /** Call with the live page and live navigation scope, not only the rendered enabled state. */
    fun canUse(stamp: Stamp?, page: MarketShoppingActivitySearchPage?,
        wanted: MarketShoppingActivitySearchRequest, signal: Long, blocked: Boolean): Boolean =
        !blocked && page != null && page.request == wanted.normalizedActivitySearch() &&
            isCurrent(stamp, wanted, signal)
}
