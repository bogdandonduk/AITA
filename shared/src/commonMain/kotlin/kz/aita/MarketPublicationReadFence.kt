package kz.aita

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Seller drafts may be unpublished or have no name yet. Keep their validation separate from
 * public offers, but never accept another branch's editor or mixed-branch product rows. */
fun MarketPublicationDashboard.isValidPublicationDashboard(branchId: String): Boolean {
    fun id(value: String) = marketDiscoveryId(value) == value
    fun field(value: String, maximum: Int) = value.length <= maximum &&
        value.none { it.code < 32 && it != '\n' && it != '\t' }
    val shop = storefront
    return id(branchId) && id(shop.storeId) && shop.operatingBranchId == branchId && shop.revision >= 0 &&
        field(shop.displayName, 120) && field(shop.city, 100) && field(shop.publicAddress, 400) && field(shop.pickupNote, 1000) &&
        (!shop.published || (shop.displayName.isNotBlank() && shop.city.isNotBlank() && shop.publicAddress.isNotBlank())) &&
        listings.size <= 1000 && listings.map { it.id }.distinct().size == listings.size &&
        listings.map { it.goodsItemId }.distinct().size == listings.size &&
        listings.all { row -> id(row.id) && id(row.goodsItemId) && row.storeId == shop.storeId && row.revision > 0 &&
            row.title.isNotBlank() && field(row.title, 180) && field(row.description, 2000) &&
            (row.gtin == null || marketCanonicalGtin(row.gtin) == row.gtin) && row.product.isValidMarketProduct() } &&
        locationStoreIds.size <= MARKET_STOREFRONT_MAX_LOCATIONS && locationStoreIds.distinct().size == locationStoreIds.size &&
        locationStoreIds.all(::id) && availableLocations.size <= 200 &&
        availableLocations.map { it.storeId }.distinct().size == availableLocations.size &&
        availableLocations.all { id(it.storeId) && it.name.size <= 12 && it.name.isNotEmpty() &&
            it.name.all { name -> name.language.length <= 12 && field(name.value, 180) } &&
            it.address.isNotBlank() && field(it.address, 400) }
}

/** The same fence protects read and save acknowledgements. A malformed or late response cannot
 * replace a different mode/branch's editor, nor clear an unsaved draft as a successful write. */
suspend fun readOwnedMarketPublication(owner: MarketAccountScope, branchId: String,
    request: suspend () -> ResponseDataModel<MarketPublicationDashboard>): ResponseDataModel<MarketPublicationDashboard> {
    currentCoroutineContext().ensureActive()
    if (!owner.isCurrent()) return cloudSessionExpiredResponse()
    if (marketDiscoveryId(branchId) != branchId)
        return ResponseDataModel(eventMessage("market.invalid"), null, true, 400)
    val response = request()
    currentCoroutineContext().ensureActive()
    if (!owner.isCurrent()) return cloudSessionExpiredResponse()
    if (response.negative || response.transportFailure)
        return response.copy(payload = null, negative = true, message = response.message ?: eventMessage("market.refresh_failed"))
    if (response.httpStatusCode != 200 || response.payload?.isValidPublicationDashboard(branchId) != true)
        return ResponseDataModel(eventMessage("market.refresh_failed"), null, true, 502)
    return response
}
