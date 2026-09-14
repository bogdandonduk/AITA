package kz.aita

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** A new endpoint prevents an old server from ignoring filters/cursors. History never changes
 * the list, retires a journal command, or falls back to a mutation when a result is absent.
 */
suspend fun readOwnedShoppingActivitySearch(
    owner: MarketAccountScope,
    request: MarketShoppingActivitySearchRequest,
    read: suspend (MarketShoppingActivitySearchRequest) -> ResponseDataModel<MarketShoppingActivitySearchPage>
): ResponseDataModel<MarketShoppingActivitySearchPage> {
    currentCoroutineContext().ensureActive()
    if (!owner.isCurrent()) return cloudSessionExpiredResponse()
    val normalized = request.normalizedActivitySearch()
        ?: return ResponseDataModel(eventMessage("market.activity_search_invalid"), null, true, 400)
    val response = read(normalized)
    currentCoroutineContext().ensureActive()
    if (!owner.isCurrent()) return cloudSessionExpiredResponse()
    if (response.transportFailure) return response.copy(payload = null, negative = true,
        message = response.message ?: eventMessage("market.shopping_activity_failed"))
    if (response.httpStatusCode == 404) return response.copy(payload = null, negative = true,
        message = eventMessage("market.activity_search_upgrade"))
    if (response.negative) return response.copy(payload = null,
        message = response.message ?: eventMessage("market.shopping_activity_failed"))
    if (response.httpStatusCode != 200)
        return ResponseDataModel(eventMessage("market.shopping_activity_failed"), null, true, 502)
    if (response.payload?.isValidActivitySearchPage(owner.accountId, normalized) != true)
        return ResponseDataModel(eventMessage("market.shopping_activity_failed"), null, true, 502)
    currentCoroutineContext().ensureActive()
    if (!owner.isCurrent()) return cloudSessionExpiredResponse()
    return response
}
