package kz.aita

import kotlinx.serialization.Serializable

/** Public shop-window data only. Never reuse a stock/store DTO as an Internet listing. */
@Serializable
data class MarketStorefront(
    val storeId: String,
    val displayName: String = "",
    val city: String = "",
    val publicAddress: String = "",
    val pickupNote: String = "",
    val published: Boolean = false,
    val revision: Long = 0L
)

@Serializable
data class MarketListing(
    val id: String,
    val storeId: String,
    val goodsItemId: String,
    val title: String,
    val description: String = "",
    val gtin: String? = null,
    val published: Boolean = false,
    val revision: Long = 0L
)

@Serializable
data class MarketOffer(
    val id: String,
    val storefront: MarketStorefront,
    val title: String,
    val description: String = "",
    val gtin: String? = null,
    val categoryIds: List<String> = emptyList(),
    val priceMinor: Long? = null,
    val currencyCode: String? = null,
    val pricedAmount: Double? = null,
    val unitId: String? = null,
    val unitName: List<LocalizedStringDataModel> = emptyList(),
    val availability: String = MARKET_AVAILABILITY_CONFIRM,
    val checkedAtMillis: Long,
    val sourceUpdatedAtMillis: Long,
    val saved: Boolean = false
)

const val MARKET_AVAILABILITY_RECORDED = "recorded_in_stock"
const val MARKET_AVAILABILITY_CONFIRM = "confirm_with_store"

@Serializable
data class MarketPage(val offers: List<MarketOffer> = emptyList(), val nextId: String? = null,
    val checkedAtMillis: Long = 0L, val unavailableSavedCount: Int = 0)

@Serializable
data class MarketPublicationDashboard(val storefront: MarketStorefront, val listings: List<MarketListing> = emptyList())

@Serializable
data class MarketStorefrontUpdate(val storefront: MarketStorefront)

@Serializable
data class MarketListingUpdate(val listing: MarketListing)

@Serializable
data class MarketSavedUpdate(val offerId: String, val saved: Boolean)

/** Checks the actual GTIN check digit. Names and unverified internal barcodes never auto-merge. */
fun marketCanonicalGtin(raw: String): String? {
    val value = raw.trim()
    if (value.length !in setOf(8, 12, 13, 14) || value.any { it !in '0'..'9' } || value.all { it == '0' }) return null
    val sum = value.dropLast(1).reversed().mapIndexed { index, c ->
        (c - '0') * if (index % 2 == 0) 3 else 1
    }.sum()
    if ((10 - sum % 10) % 10 != value.last() - '0') return null
    return value.padStart(14, '0')
}

/** These can be compared as seller offers, not as a guarantee that a manufacturer verified identity. */
fun MarketOffer.comparisonKey(): String? {
    val code = gtin?.let(::marketCanonicalGtin) ?: return null
    val currency = currencyCode?.takeIf { it.matches(Regex("[A-Z]{3}")) } ?: return null
    val unit = unitId?.takeIf { it.isNotBlank() } ?: return null
    val amount = pricedAmount?.takeIf { it.isFinite() && it > 0.0 } ?: return null
    if (priceMinor == null || priceMinor < 0L) return null
    return "$code:$currency:$unit:$amount"
}

fun GoodsBatchDataModel.isMarketSellableAt(storeId: String, nowMillis: Long): Boolean =
    this.storeId == storeId && isActive && status in setOf(StockBatchStatusDataModel.Delivered, StockBatchStatusDataModel.OnShelf) &&
        quantity.total.isFinite() && quantity.total > 0.0 && quantity.pricedAmount.isFinite() && quantity.pricedAmount > 0.0 &&
        (expirationDateMillis == null || expirationDateMillis > nowMillis)

/** Small editor journal only; no catalogue arrays, secrets, or pending purchases in navigation state. */
@Serializable
data class MarketEditorDraft(val storefront: MarketStorefront? = null, val listing: MarketListing? = null,
    val storefrontDirty: Boolean = false, val listingDirty: Boolean = false)
