package kz.aita

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** A plan is one read, never a command or a retry. Check ownership/cancellation on both sides
 * of transport, including adapters which return normally after the coroutine was cancelled.
 */
suspend fun readOwnedMarketBasket(
    owner: MarketAccountScope,
    request: MarketBasketRequest,
    read: suspend (MarketBasketRequest) -> ResponseDataModel<MarketBasketResult>
): ResponseDataModel<MarketBasketResult> {
    currentCoroutineContext().ensureActive()
    if (!owner.isCurrent()) return cloudSessionExpiredResponse()
    val normalized = request.normalizedBasketRequest()
        ?: return ResponseDataModel(eventMessage("market.basket_invalid"), null, true, 400)
    val response = read(normalized)
    currentCoroutineContext().ensureActive()
    if (!owner.isCurrent()) return cloudSessionExpiredResponse()
    if (response.transportFailure) return response.copy(payload = null, negative = true,
        message = response.message ?: eventMessage("market.basket_refresh"))
    if (response.httpStatusCode == 404) return response.copy(payload = null, negative = true,
        message = eventMessage("market.basket_upgrade"))
    if (response.negative) return response.copy(payload = null,
        message = response.message ?: eventMessage("market.basket_refresh"))
    val result = response.payload
    if (response.httpStatusCode != 200 || result == null || !result.isValidBasketResult(owner.accountId, normalized))
        return ResponseDataModel(eventMessage("market.basket_refresh"), null, true, 502)
    currentCoroutineContext().ensureActive()
    if (!owner.isCurrent()) return cloudSessionExpiredResponse()
    return response
}
