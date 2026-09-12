package kz.aita

import io.ktor.http.HttpMethod
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Invalidation carries no customer/shop secrets. The focused screen reloads through normal auth. */
object MarketplaceSignals {
    private val counter = MutableStateFlow(0L)
    val revision = counter.asStateFlow()
    fun changed() { counter.update { it + 1L } }
}

interface MarketAccountScope {
    val accountId: String
    val generation: Long
    fun isCurrent(): Boolean
}

class MarketRequestScope internal constructor(override val accountId: String, override val generation: Long,
    val storeId: String?, private val inventoryEpoch: Long?) : MarketAccountScope {
    override fun isCurrent(): Boolean = userAccountState.payloadValue?.id == accountId &&
        authenticatedSessionGenerationIsCurrent(generation) && (storeId == null ||
        (activeStoreIdState.value == storeId && inventoryOwners.current.epoch == inventoryEpoch))
}
fun captureMarketRequestScope(storeId: String? = null): MarketRequestScope? {
    val account = userAccountState.payloadValue?.id?.takeIf { it.isNotBlank() } ?: return null
    val scope = MarketRequestScope(account,currentAuthenticatedSessionGeneration(),storeId,
        if(storeId==null) null else inventoryOwners.current.epoch)
    return scope.takeIf { it.isCurrent() }
}

suspend fun loadMarketOffers(scope: MarketRequestScope, search: String = "", city: String = "",
    after: String? = null, gtin: String? = null, storefrontId: String? = null): ResponseDataModel<MarketPage> {
    if(!scope.isCurrent()) return cloudSessionExpiredResponse()
    return networkRequest<MarketPage,Unit>(HttpMethod.Get,endpointUrl="market/offers",
        query=buildMap { put("q",search); put("city",city); after?.let { put("after",it) }; gtin?.let { put("gtin",it) }; storefrontId?.let { put("store",it) } },
        expectedSessionGeneration=scope.generation)
}
suspend fun loadMarketSaved(scope: MarketRequestScope): ResponseDataModel<MarketPage> {
    if(!scope.isCurrent()) return cloudSessionExpiredResponse()
    return networkRequest<MarketPage,Unit>(HttpMethod.Get,endpointUrl="market/saved",expectedSessionGeneration=scope.generation)
}
suspend fun updateMarketSaved(scope: MarketRequestScope, id: String, saved: Boolean): ResponseDataModel<MarketPage> {
    if(!scope.isCurrent()) return cloudSessionExpiredResponse()
    return networkRequest(HttpMethod.Put,endpointUrl="market/saved",body=MarketSavedUpdate(id,saved),expectedSessionGeneration=scope.generation)
}
suspend fun clearUnavailableMarketSaved(scope: MarketRequestScope): ResponseDataModel<MarketPage> {
    if(!scope.isCurrent()) return cloudSessionExpiredResponse()
    return networkRequest<MarketPage,Unit>(HttpMethod.Post,endpointUrl="market/saved/clear-unavailable",expectedSessionGeneration=scope.generation)
}
suspend fun loadMarketPublication(scope: MarketRequestScope): ResponseDataModel<MarketPublicationDashboard> {
    if(!scope.isCurrent() || scope.storeId==null) return cloudSessionExpiredResponse()
    return networkRequest<MarketPublicationDashboard,Unit>(HttpMethod.Get,endpointUrl="market/seller",
        headers=mapOf("store_id" to scope.storeId),expectedSessionGeneration=scope.generation)
}
suspend fun saveMarketStorefront(scope: MarketRequestScope, value: MarketStorefront): ResponseDataModel<MarketPublicationDashboard> {
    if(!scope.isCurrent() || scope.storeId!=value.storeId) return cloudSessionExpiredResponse()
    return networkRequest(HttpMethod.Put,endpointUrl="market/seller/storefront",headers=mapOf("store_id" to value.storeId),
        body=MarketStorefrontUpdate(value),expectedSessionGeneration=scope.generation)
}
suspend fun saveMarketListing(scope: MarketRequestScope, value: MarketListing): ResponseDataModel<MarketPublicationDashboard> {
    if(!scope.isCurrent() || scope.storeId!=value.storeId) return cloudSessionExpiredResponse()
    return networkRequest(HttpMethod.Put,endpointUrl="market/seller/listing",headers=mapOf("store_id" to value.storeId),
        body=MarketListingUpdate(value),expectedSessionGeneration=scope.generation)
}

suspend fun loadMarketOffer(scope: MarketRequestScope, id: String): ResponseDataModel<MarketOffer> {
    if (!scope.isCurrent()) return cloudSessionExpiredResponse()
    if (!id.matches(Regex("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")))
        return ResponseDataModel(eventMessage("market.unavailable"), null, true, 404)
    return networkRequest<MarketOffer, Unit>(HttpMethod.Get, endpointUrl = "market/offers/$id", expectedSessionGeneration = scope.generation)
}
suspend fun loadMarketShop(scope: MarketRequestScope, id: String): ResponseDataModel<MarketStorefront> {
    if (!scope.isCurrent()) return cloudSessionExpiredResponse()
    if (!id.matches(Regex("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")))
        return ResponseDataModel(eventMessage("market.shop_unavailable"), null, true, 404)
    return networkRequest<MarketStorefront, Unit>(HttpMethod.Get, endpointUrl = "market/shops/$id", expectedSessionGeneration = scope.generation)
}
