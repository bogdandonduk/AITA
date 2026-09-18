package kz.aita

import kotlinx.serialization.Serializable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import io.ktor.http.HttpMethod

const val MARKET_SAVED_SHOPS_MAX = 100
@Serializable data class MarketSavedShops(val accountId: String, val revision: Long, val storeIds: List<String>, val checkedAtMillis: Long)
@Serializable data class MarketSavedShopChange(val storeId: String, val saved: Boolean, val expectedRevision: Long)
fun MarketSavedShopChange.isValidSavedShopChange() = marketDiscoveryId(storeId) == storeId && expectedRevision in 0 until Long.MAX_VALUE
fun MarketSavedShops.isValidSavedShops(account: String) = account.isNotBlank() && accountId == account && revision >= 0 &&
    checkedAtMillis > 0 && storeIds.size <= MARKET_SAVED_SHOPS_MAX && storeIds.distinct().size == storeIds.size &&
    storeIds.all { marketDiscoveryId(it) == it }

/** An old success or failure cannot cross account/session ownership. No optimistic save is claimed. */
suspend fun readOwnedSavedShops(owner: MarketAccountScope, change: MarketSavedShopChange? = null,
    request: suspend () -> ResponseDataModel<MarketSavedShops>): ResponseDataModel<MarketSavedShops> {
    currentCoroutineContext().ensureActive()
    if (!owner.isCurrent()) return cloudSessionExpiredResponse()
    if (change != null && !change.isValidSavedShopChange()) return ResponseDataModel(eventMessage("market.saved_shops_failed"), null, true, 400)
    val response = request()
    currentCoroutineContext().ensureActive()
    if (!owner.isCurrent()) return cloudSessionExpiredResponse()
    if (response.negative || response.transportFailure) return response.copy(payload = null, negative = true,
        message = response.message ?: eventMessage("market.saved_shops_failed"))
    val data = response.payload
    if (response.httpStatusCode != 200 || data?.isValidSavedShops(owner.accountId) != true ||
        (change != null && (data.revision < change.expectedRevision || (change.storeId in data.storeIds) != change.saved)))
        return ResponseDataModel(eventMessage("market.saved_shops_failed"), null, true, 502)
    return response
}
suspend fun loadMarketSavedShops(owner: MarketAccountScope) = readOwnedSavedShops(owner) {
    networkRequest<MarketSavedShops, Unit>(HttpMethod.Get, endpointUrl = "market/saved-shops", expectedSessionGeneration = owner.generation)
}
suspend fun changeMarketSavedShop(owner: MarketAccountScope, change: MarketSavedShopChange): ResponseDataModel<MarketSavedShops> {
    try { return readOwnedSavedShops(owner, change) {
        networkRequest<MarketSavedShops, MarketSavedShopChange>(HttpMethod.Put, endpointUrl = "market/saved-shops", body = change,
            expectedSessionGeneration = owner.generation)
    } } finally { if (owner.isCurrent()) MarketplaceSignals.changed() }
}
