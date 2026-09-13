package kz.aita

/** A new endpoint prevents an old server from ignoring filters/cursors. History never changes
 * the list, retires a journal command, or falls back to a mutation when a result is absent.
 */
suspend fun readOwnedShoppingActivitySearch(
    owner: MarketAccountScope,
    request: MarketShoppingActivitySearchRequest,
    read: suspend (MarketShoppingActivitySearchRequest) -> ResponseDataModel<MarketShoppingActivitySearchPage>
): ResponseDataModel<MarketShoppingActivitySearchPage> {
    if (!owner.isCurrent()) return cloudSessionExpiredResponse()
    val normalized = request.normalizedActivitySearch()
        ?: return ResponseDataModel(eventMessage("market.activity_search_invalid"), null, true, 400)
    val response = read(normalized)
    if (!owner.isCurrent()) return cloudSessionExpiredResponse()
    if (response.httpStatusCode == 404) return response.copy(payload = null, negative = true,
        message = eventMessage("market.activity_search_upgrade"))
    if (response.negative) return response.copy(payload = null)
    if (response.payload?.isValidActivitySearchPage(owner.accountId, normalized) != true)
        return ResponseDataModel(eventMessage("market.shopping_activity_failed"), null, true, 502)
    return response
}
