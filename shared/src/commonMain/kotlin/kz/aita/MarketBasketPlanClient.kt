package kz.aita

/** Check ownership on BOTH sides of a suspended read; no result/journal can cross accounts. */
suspend fun readOwnedMarketBasket(
    owner: MarketAccountScope,
    request: MarketBasketRequest,
    read: suspend (MarketBasketRequest) -> ResponseDataModel<MarketBasketResult>
): ResponseDataModel<MarketBasketResult> {
    if (!owner.isCurrent()) return cloudSessionExpiredResponse()
    val normalized = request.normalizedBasketRequest()
        ?: return ResponseDataModel(eventMessage("market.basket_invalid"), null, true, 400)
    val response = read(normalized)
    if (!owner.isCurrent()) return cloudSessionExpiredResponse()
    if (response.httpStatusCode == 404) return response.copy(payload = null, negative = true, message = eventMessage("market.basket_upgrade"))
    if (response.negative) return response.copy(payload = null)
    val result = response.payload
    if (result == null || !result.isValidBasketResult(owner.accountId, normalized))
        return ResponseDataModel(eventMessage("market.basket_refresh"), null, true, 502)
    return response
}
