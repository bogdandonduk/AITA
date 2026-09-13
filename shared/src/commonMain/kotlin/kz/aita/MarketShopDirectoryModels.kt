package kz.aita

import kotlinx.serialization.Serializable

const val MARKET_SHOPS_PAGE_SIZE = 20
const val MARKET_SHOPS_MAX_WINDOW = 200
const val MARKET_SHOPS_PROTOCOL = 1

/** Shop-name/address search, deliberately separate from product/category discovery. A larger
 * window replaces the whole visible window in one snapshot; it never splices offset pages.
 */
@Serializable
data class MarketShopDirectoryRequest(val text: String = "", val city: String = "", val limit: Int = MARKET_SHOPS_PAGE_SIZE)

@Serializable
data class MarketShopDirectoryEntry(val storefront: MarketStorefront, val publishedOffers: Long)

@Serializable
data class MarketShopDirectoryResult(
    val accountId: String,
    val request: MarketShopDirectoryRequest,
    val shops: List<MarketShopDirectoryEntry>,
    val totalShops: Long,
    val checkedAtMillis: Long,
    val protocolVersion: Int = MARKET_SHOPS_PROTOCOL
)

fun MarketShopDirectoryRequest.normalizedShopDirectoryRequest(): MarketShopDirectoryRequest? {
    if (limit !in MARKET_SHOPS_PAGE_SIZE..MARKET_SHOPS_MAX_WINDOW || limit % MARKET_SHOPS_PAGE_SIZE != 0) return null
    val normalized = MarketDiscoveryQuery(text = text, city = city).normalizedDiscoveryQuery() ?: return null
    return copy(text = normalized.text, city = normalized.city)
}

fun MarketStorefront.isValidPublicMarketShop(): Boolean = marketDiscoveryId(storeId) == storeId && published && revision > 0 &&
    displayName.isNotBlank() && displayName.length <= 120 && city.isNotBlank() && city.length <= 100 &&
    publicAddress.isNotBlank() && publicAddress.length <= 400 && pickupNote.length <= 1000

fun MarketShopDirectoryResult.isValidShopDirectoryResult(account: String, wanted: MarketShopDirectoryRequest): Boolean {
    val normalized = wanted.normalizedShopDirectoryRequest() ?: return false
    return protocolVersion == MARKET_SHOPS_PROTOCOL && accountId == account && request == normalized &&
        checkedAtMillis > 0 && totalShops >= 0 && shops.size == minOf(totalShops, normalized.limit.toLong()).toInt() &&
        shops.map { it.storefront.storeId }.distinct().size == shops.size &&
        shops.all { it.storefront.isValidPublicMarketShop() && it.publishedOffers >= 0 }
}

/** No account's result may cross a suspended ownership change. Errors keep the old UI window
 * visible but stale; a missing new endpoint is never replaced with private /stores data.
 */
suspend fun readOwnedMarketShopDirectory(owner: MarketAccountScope, request: MarketShopDirectoryRequest,
    read: suspend (MarketShopDirectoryRequest) -> ResponseDataModel<MarketShopDirectoryResult>
): ResponseDataModel<MarketShopDirectoryResult> {
    if (!owner.isCurrent()) return cloudSessionExpiredResponse()
    val normalized = request.normalizedShopDirectoryRequest()
        ?: return ResponseDataModel(eventMessage("market.shops_invalid"), null, true, 400)
    val response = read(normalized)
    if (!owner.isCurrent()) return cloudSessionExpiredResponse()
    if (response.httpStatusCode == 404) return response.copy(payload = null, negative = true, message = eventMessage("market.shops_upgrade"))
    if (response.negative) return response.copy(payload = null)
    if (response.payload?.isValidShopDirectoryResult(owner.accountId, normalized) != true)
        return ResponseDataModel(eventMessage("market.shops_failed"), null, true, 502)
    return response
}
