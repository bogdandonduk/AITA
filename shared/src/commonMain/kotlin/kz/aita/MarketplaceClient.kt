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
suspend fun loadMarketSaved(scope: MarketRequestScope): ResponseDataModel<MarketPage> =
    readOwnedMarketSaved(scope) {
        networkRequest<MarketPage, Unit>(HttpMethod.Get, endpointUrl = "market/saved", expectedSessionGeneration = scope.generation)
    }
suspend fun updateMarketSaved(scope: MarketRequestScope, id: String, saved: Boolean): ResponseDataModel<MarketPage> =
    updateOwnedMarketSaved(scope, MarketSavedUpdate(id, saved), MarketplaceSignals::changed) { request ->
        networkRequest(HttpMethod.Put, endpointUrl = "market/saved", body = request, expectedSessionGeneration = scope.generation)
    }
suspend fun clearUnavailableMarketSaved(scope: MarketRequestScope): ResponseDataModel<MarketPage> =
    clearUnavailableOwnedMarketSaved(scope, MarketplaceSignals::changed) {
        networkRequest<MarketPage, Unit>(HttpMethod.Post, endpointUrl = "market/saved/clear-unavailable", expectedSessionGeneration = scope.generation)
    }
suspend fun loadMarketPublication(scope: MarketRequestScope): ResponseDataModel<MarketPublicationDashboard> {
    if(!scope.isCurrent() || scope.storeId==null) return cloudSessionExpiredResponse()
    return networkRequest<MarketPublicationDashboard,Unit>(HttpMethod.Get,endpointUrl="market/seller",
        headers=mapOf("store_id" to scope.storeId),expectedSessionGeneration=scope.generation)
}
suspend fun saveMarketStorefront(scope: MarketRequestScope, value: MarketStorefront,
    locationStoreIds: List<String>? = null): ResponseDataModel<MarketPublicationDashboard> {
    if(!scope.isCurrent() || scope.storeId!=value.operatingBranchId) return cloudSessionExpiredResponse()
    return networkRequest(HttpMethod.Put,endpointUrl="market/seller/storefront",headers=mapOf("store_id" to value.operatingBranchId),
        body=MarketStorefrontUpdate(value, locationStoreIds),expectedSessionGeneration=scope.generation)
}
suspend fun saveMarketListing(scope: MarketRequestScope, value: MarketListing): ResponseDataModel<MarketPublicationDashboard> {
    if(!scope.isCurrent() || scope.storeId==null) return cloudSessionExpiredResponse()
    return networkRequest(HttpMethod.Put,endpointUrl="market/seller/listing",headers=mapOf("store_id" to scope.storeId),
        body=MarketListingUpdate(value, replaceProduct=true, branchStoreId=scope.storeId),expectedSessionGeneration=scope.generation)
}

suspend fun loadMarketOffer(scope: MarketRequestScope, id: String): ResponseDataModel<MarketOffer> {
    if (!scope.isCurrent()) return cloudSessionExpiredResponse()
    if (!id.matches(Regex("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")))
        return ResponseDataModel(eventMessage("market.unavailable"), null, true, 404)
    return networkRequest<MarketOffer, Unit>(HttpMethod.Get, endpointUrl = "market/offers/$id", expectedSessionGeneration = scope.generation)
}
/** Details require a validated account-bound response; legacy card reads remain for old callers. */
suspend fun loadMarketOfferDetail(scope: MarketRequestScope, id: String): ResponseDataModel<MarketOfferDetailResult> =
    readOwnedMarketOfferDetail(scope, id) { normalizedId ->
        networkRequest<MarketOfferDetailResult, Unit>(HttpMethod.Get, endpointUrl = "market/offers/$normalizedId/detail",
            expectedSessionGeneration = scope.generation)
    }

suspend fun loadMarketShop(scope: MarketRequestScope, id: String): ResponseDataModel<MarketStorefront> {
    if (!scope.isCurrent()) return cloudSessionExpiredResponse()
    if (!id.matches(Regex("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")))
        return ResponseDataModel(eventMessage("market.shop_unavailable"), null, true, 404)
    return networkRequest<MarketStorefront, Unit>(HttpMethod.Get, endpointUrl = "market/shops/$id", expectedSessionGeneration = scope.generation)
}

/** Read-only POST: structured comparison identity is kept out of the URL and not cached. */
suspend fun loadMarketComparison(scope: MarketRequestScope, request: MarketComparisonRequest): ResponseDataModel<MarketComparisonPage> {
    if (!scope.isCurrent()) return cloudSessionExpiredResponse()
    if (!request.selection.isValidMarketComparison()) return ResponseDataModel(eventMessage("market.comparison_invalid"), null, true, 400)
    return networkRequest(HttpMethod.Post, endpointUrl = "market/compare", body = request,
        expectedSessionGeneration = scope.generation)
}

/** One bounded read replaces the entire comparison window; never splice independently priced pages. */
suspend fun loadMarketComparisonWindow(owner: MarketRequestScope, request: MarketComparisonWindowRequest) =
    readOwnedMarketComparisonWindow(owner, request) { normalized ->
        networkRequest<MarketComparisonWindowResult, MarketComparisonWindowRequest>(HttpMethod.Post,
            endpointUrl = "market/compare/window", body = normalized, expectedSessionGeneration = owner.generation)
    }

/** A separate endpoint cannot be mistaken by an older server for a legacy unfiltered browse. */
suspend fun loadMarketDiscovery(scope: MarketRequestScope, request: MarketDiscoveryRequest,
    cachedCatalogue: MarketCategoryCatalogue? = null): ResponseDataModel<MarketDiscoverySnapshot> =
    readOwnedMarketDiscovery(scope, request, cachedCatalogue) { normalized ->
        networkRequest<MarketDiscoveryResult, MarketDiscoveryRequest>(HttpMethod.Post, endpointUrl = "market/discovery",
            body = normalized, expectedSessionGeneration = scope.generation)
    }

/** Read-only endpoint: never downgrade to an older command or unfiltered offer route. */
suspend fun loadMarketBasketPlan(scope: MarketRequestScope, request: MarketBasketRequest): ResponseDataModel<MarketBasketResult> =
    readOwnedMarketBasket(scope, request) { normalized ->
        networkRequest<MarketBasketResult, MarketBasketRequest>(HttpMethod.Post, endpointUrl = "market/shopping-list/plan",
            body = normalized, expectedSessionGeneration = scope.generation)
    }

suspend fun loadMarketShoppingActivity(owner: MarketAccountScope, request: MarketShoppingActivityRequest) =
    readOwnedShoppingActivity(owner, request) {
        networkRequest<MarketShoppingActivityPage, MarketShoppingActivityRequest>(io.ktor.http.HttpMethod.Post,
            endpointUrl = "market/shopping-list/activity", body = it, expectedSessionGeneration = owner.generation)
    }

suspend fun loadMarketShoppingActivitySearch(owner: MarketAccountScope, request: MarketShoppingActivitySearchRequest) =
    readOwnedShoppingActivitySearch(owner, request) {
        networkRequest<MarketShoppingActivitySearchPage, MarketShoppingActivitySearchRequest>(io.ktor.http.HttpMethod.Post,
            endpointUrl = "market/shopping-list/activity/search", body = it, expectedSessionGeneration = owner.generation)
    }

suspend fun loadMarketShoppingActivityDetail(owner: MarketAccountScope, summary: MarketShoppingActivityEntry) =
    readOwnedShoppingActivityDetail(owner, summary) {
        networkRequest<MarketShoppingActivityEntry, Unit>(io.ktor.http.HttpMethod.Get,
            endpointUrl = "market/shopping-list/activity/$it", expectedSessionGeneration = owner.generation)
    }

/** Public shop fields only, never the private merchant store-management DTO. */
suspend fun loadMarketShopDirectory(owner: MarketAccountScope, request: MarketShopDirectoryRequest) =
    readOwnedMarketShopDirectory(owner, request) {
        networkRequest<MarketShopDirectoryResult, MarketShopDirectoryRequest>(HttpMethod.Post,
            endpointUrl = "market/shops/search", body = it, expectedSessionGeneration = owner.generation)
    }
