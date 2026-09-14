package kz.aita

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Own one validated catalogue read. There is no second header request, mutation, automatic retry
 * or unfiltered fallback. An old backend is distinguished from a currently withdrawn storefront.
 */
suspend fun readOwnedMarketDiscovery(
    owner: MarketAccountScope,
    request: MarketDiscoveryRequest,
    cachedCatalogue: MarketCategoryCatalogue?,
    read: suspend (MarketDiscoveryRequest) -> ResponseDataModel<MarketDiscoveryResult>
): ResponseDataModel<MarketDiscoverySnapshot> {
    currentCoroutineContext().ensureActive()
    if (!owner.isCurrent()) return cloudSessionExpiredResponse()
    if (!request.isValidDiscoveryRequest()) return ResponseDataModel(eventMessage("market.discovery_invalid"), null, true, 400)
    val normalized = request.copy(query = requireNotNull(request.query.normalizedDiscoveryQuery()))
    val response = read(normalized)
    currentCoroutineContext().ensureActive()
    if (!owner.isCurrent()) return cloudSessionExpiredResponse()
    if (response.transportFailure) return ResponseDataModel(response.message ?: eventMessage("market.page_changed"),
        null, true, response.httpStatusCode, transportFailure = true)
    if (response.httpStatusCode == 404) {
        val missingShop = response.message?.eventMessageReferenceOrNull()?.key == "market.shop_unavailable"
        return ResponseDataModel(if (missingShop) response.message else eventMessage("market.discovery_scope_upgrade"), null, true, 404)
    }
    if (response.negative) return ResponseDataModel(response.message ?: eventMessage("market.page_changed"), null, true, response.httpStatusCode)
    if (response.httpStatusCode != 200) return ResponseDataModel(eventMessage("market.page_changed"), null, true, 502)
    val result = response.payload ?: return ResponseDataModel(eventMessage("market.page_changed"), null, true, 502)
    if (result.accountId == null) return ResponseDataModel(eventMessage("market.discovery_scope_upgrade"), null, true, 502)
    val checked = result.validatedDiscovery(normalized, cachedCatalogue, owner.accountId)
        ?: return ResponseDataModel(eventMessage("market.page_changed"), null, true, 502)
    currentCoroutineContext().ensureActive()
    if (!owner.isCurrent()) return cloudSessionExpiredResponse()
    return ResponseDataModel(response.message, checked, false, 200)
}

/** Compare semantic scope, not the taxonomy cache hint. A new category revision is a valid result;
 * a different filter/limit or an invalidation received during I/O is not a fresh visible window.
 */
fun MarketDiscoveryResult.matchesDiscoveryRead(
    wanted: MarketDiscoveryRequest,
    startedRevision: Long,
    currentRevision: Long,
    refreshQueued: Boolean = false
): Boolean = wanted.isValidDiscoveryRequest() && query == wanted.query.normalizedDiscoveryQuery() &&
    limit == wanted.limit && startedRevision >= 0L && startedRevision == currentRevision && !refreshQueued
