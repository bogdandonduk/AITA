package kz.aita

const val MARKET_SHOP_DIRECTORY_FRESH_MILLIS = 30_000L

/** UI-confined generation for a WHOLE shop window. Invalidating before enqueueing closes the
 * debounce/cooldown gap; comparing scope at completion also covers changes before recomposition.
 * Object identity prevents an A -> B -> A search or refresh from reviving an earlier response.
 */
class MarketShopDirectoryReadFence {
    class Stamp internal constructor(
        internal val token: Any,
        internal val request: MarketShopDirectoryRequest,
        internal val revision: Long
    )
    private var token = Any()

    fun invalidate() { token = Any() }

    fun capture(request: MarketShopDirectoryRequest, revision: Long): Stamp =
        Stamp(token, requireNotNull(request.normalizedShopDirectoryRequest()), revision)

    fun isCurrent(stamp: Stamp?, wanted: MarketShopDirectoryRequest, revision: Long): Boolean =
        stamp != null && stamp.token === token && stamp.request == wanted.normalizedShopDirectoryRequest() &&
            revision >= 0L && stamp.revision == revision

    /** Receipt age is local monotonic elapsed time, NOT the server's wall-clock checkedAt value.
     * Loading/error/input/owner checks are supplied by the view, including at click time.
     */
    fun canUse(stamp: Stamp?, result: MarketShopDirectoryResult?, wanted: MarketShopDirectoryRequest,
        revision: Long, ageMillis: Long, blocked: Boolean): Boolean =
        !blocked && result != null && result.request == wanted.normalizedShopDirectoryRequest() &&
            ageMillis in 0 until MARKET_SHOP_DIRECTORY_FRESH_MILLIS && isCurrent(stamp, wanted, revision)

    fun canVisit(stamp: Stamp?, result: MarketShopDirectoryResult?, displayed: MarketShopDirectoryEntry,
        wanted: MarketShopDirectoryRequest, revision: Long, ageMillis: Long, blocked: Boolean): Boolean =
        canUse(stamp, result, wanted, revision, ageMillis, blocked) && result?.shops?.contains(displayed) == true
}
