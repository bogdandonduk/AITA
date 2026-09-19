package kz.aita

import kotlinx.serialization.Serializable

/** Seller-authored public facts, not an inventory DTO. Never infer claims from a name/barcode. */
@Serializable
data class MarketProductAttribute(val name: String = "", val value: String = "")

@Serializable
data class MarketProductDetails(
    val imageUrls: List<String> = emptyList(),
    val brand: String = "",
    val manufacturer: String = "",
    val countryOfOrigin: String = "",
    val article: String = "",
    val ingredients: String = "",
    val allergens: String = "",
    val storageInstructions: String = "",
    val attributes: List<MarketProductAttribute> = emptyList()
)

/** Private, automatically seeded draft. Publication is a separately reviewed snapshot. */
@Serializable
data class StockMarketplaceProfile(
    val automaticFromStock: Boolean = true,
    val name: List<LocalizedStringDataModel> = emptyList(),
    val description: List<LocalizedStringDataModel> = emptyList(),
    val product: MarketProductDetails = MarketProductDetails()
)

const val MARKET_PRODUCT_MAX_IMAGES = 6
const val MARKET_PRODUCT_MAX_ATTRIBUTES = 16
const val MARKET_BRANCH_MAX_LOCATIONS = 60

private fun marketPublicText(raw: String, limit: Int): String = raw.trim()
    .filter { it.code >= 32 || it == '\n' || it == '\t' }.take(limit)

/** Public HTTPS URLs only, no credentials, local hosts, IP literals or signed query tokens.
 * Not a general URL validator or a server-side download permission. No backend fetch is made.
 */
fun marketPublicImageUrl(raw: String): String? {
    val url = raw.trim()
    if (url.length !in 12..1200 || !url.startsWith("https://", true) ||
        url.any { it.isWhitespace() || it.code < 32 || it == '\\' } ||
        url.contains('?') || url.contains('#')) return null
    val host = url.substring(8).substringBefore('/').lowercase()
    if (host.contains('@') || host.contains(':') || host.endsWith('.') ||
        !host.matches(Regex("[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?")) || !host.contains('.')) return null
    if (host.split('.').any { it.isEmpty() || it.length > 63 || it.startsWith('-') || it.endsWith('-') } ||
        host.length > 253 || host.substringAfterLast('.').none { it in 'a'..'z' } ||
        host == "localhost" || listOf(".localhost", ".local", ".internal", ".test", ".invalid").any(host::endsWith)) return null
    return "https://" + host + url.substring(8 + url.substring(8).substringBefore('/').length)
}

fun MarketProductDetails.normalizedMarketProduct(): MarketProductDetails = copy(
    imageUrls = imageUrls.mapNotNull(::marketPublicImageUrl).distinct().take(MARKET_PRODUCT_MAX_IMAGES),
    brand = marketPublicText(brand, 120), manufacturer = marketPublicText(manufacturer, 160),
    countryOfOrigin = marketPublicText(countryOfOrigin, 100), article = marketPublicText(article, 120),
    ingredients = marketPublicText(ingredients, 2000), allergens = marketPublicText(allergens, 1000),
    storageInstructions = marketPublicText(storageInstructions, 1000),
    attributes = attributes.map { MarketProductAttribute(marketPublicText(it.name, 80), marketPublicText(it.value, 400)) }
        .filter { it.name.isNotBlank() && it.value.isNotBlank() }.distinctBy { it.name.lowercase() }.take(MARKET_PRODUCT_MAX_ATTRIBUTES)
)

fun MarketProductDetails.isValidMarketProduct(): Boolean = this == normalizedMarketProduct()

private fun List<LocalizedStringDataModel>.publicProductText(limit: Int) = asSequence()
    .map { LocalizedStringDataModel(it.language.trim().take(12), marketPublicText(it.value, limit)) }
    .filter { it.language.isNotBlank() && it.value.isNotBlank() }.distinctBy { it.language }.take(12).toList()

fun StockMarketplaceProfile.fromCurrentStock(
    stockName: List<LocalizedStringDataModel>, stockDescription: List<LocalizedStringDataModel>, images: List<String>
): StockMarketplaceProfile = copy(
    name = (if (automaticFromStock) stockName else name).publicProductText(180),
    description = (if (automaticFromStock) stockDescription else description).publicProductText(2000),
    product = product.copy(imageUrls = if (automaticFromStock) images else product.imageUrls).normalizedMarketProduct()
)

fun GoodsItemDataModel.effectiveMarketplaceProfile(): StockMarketplaceProfile =
    (marketplaceProfile ?: StockMarketplaceProfile()).fromCurrentStock(name, description, imagePaths)

/** A branch receives an editable snapshot; its stock fields must not replace the parent's public text. */
fun GoodsItemDataModel.marketplaceProfileForBranchCopy(): StockMarketplaceProfile =
    effectiveMarketplaceProfile().copy(automaticFromStock = false)

/** Null in legacy edits means 'keep', not 'clear'. Automatic drafts track permitted stock fields. */
fun GoodsItemDataModel.marketplaceProfileForSave(previous: StockMarketplaceProfile? = null): StockMarketplaceProfile =
    (marketplaceProfile ?: previous ?: StockMarketplaceProfile()).fromCurrentStock(name, description, imagePaths)

fun GoodsItemDataModel.marketListingDraft(parentStoreId: String, language: String): MarketListing {
    val profile = effectiveMarketplaceProfile()
    fun title(values: List<LocalizedStringDataModel>) = values.firstOrNull { it.language == language }?.value
        ?: values.firstOrNull { it.language == "main" }?.value ?: values.firstOrNull()?.value.orEmpty()
    return MarketListing("", parentStoreId, id, title(profile.name), description = title(profile.description),
        gtin = standardBarcodeValues().firstNotNullOfOrNull(::marketCanonicalGtin), product = profile.product)
}

/** A point-in-time public availability hint, never a quote, reservation or exact stock count. */
@Serializable
data class MarketBranchAvailability(
    val branchId: String,
    val name: List<LocalizedStringDataModel>,
    val publicAddress: String,
    val availability: String,
    val checkedAtMillis: Long
)

/** Do not equate similarly named goods or private/internal barcodes across physical stores. */
fun marketSameBranchProduct(parent: GoodsItemDataModel, candidate: GoodsItemDataModel): Boolean {
    if (!parent.isActive || !candidate.isActive || parent.measurementUnitId != candidate.measurementUnitId) return false
    if (parent.id == candidate.id) return true
    val codes = parent.standardBarcodeValues().mapNotNull(::marketCanonicalGtin).toSet()
    return codes.isNotEmpty() && candidate.standardBarcodeValues().mapNotNull(::marketCanonicalGtin).any { it in codes }
}

fun MarketBranchAvailability.isValidMarketBranch(offer: MarketOffer): Boolean =
    marketDiscoveryId(branchId) != null && branchId != offer.storefront.storeId &&
        name.isNotEmpty() && name.size <= 12 && name.all { it.language.isNotBlank() && it.value.isNotBlank() && it.language.length in 1..12 && it.value.length in 1..180 && it.messageTemplate == null } &&
        publicAddress.isNotBlank() && publicAddress.length in 1..400 && availability in setOf(MARKET_AVAILABILITY_RECORDED, MARKET_AVAILABILITY_CONFIRM) &&
        checkedAtMillis == offer.checkedAtMillis

@Serializable
data class MarketStockPublicationEntry(val goodsItemId: String, val gtin: String? = null,
    val measurementUnitId: String, val published: Boolean)

@Serializable
data class MarketStockPublicationStatus(val accountId: String, val storeId: String, val parentStoreId: String,
    val marketplaceEnabled: Boolean, val entries: List<MarketStockPublicationEntry> = emptyList(), val checkedAtMillis: Long)

fun MarketStockPublicationStatus.isValidStockPublicationStatus(account: String, store: String): Boolean =
    accountId == account && storeId == store && marketDiscoveryId(parentStoreId) != null && checkedAtMillis > 0L &&
        (marketplaceEnabled || entries.isEmpty()) && entries.size <= 1000 && entries.distinctBy { it.goodsItemId }.size == entries.size &&
        entries.all { marketDiscoveryId(it.goodsItemId) != null && it.measurementUnitId.isNotBlank() &&
            (it.gtin == null || marketCanonicalGtin(it.gtin) == it.gtin) }

/** Editable rows may contain blanks/spacing; invalid non-empty image destinations never save silently. */
fun MarketProductDetails.hasInvalidMarketProductInput(): Boolean =
    imageUrls.count { it.isNotBlank() } > MARKET_PRODUCT_MAX_IMAGES ||
        imageUrls.any { it.isNotBlank() && marketPublicImageUrl(it) == null } ||
        attributes.count { it.name.isNotBlank() || it.value.isNotBlank() } > MARKET_PRODUCT_MAX_ATTRIBUTES ||
        attributes.any { it.name.isBlank() != it.value.isBlank() }
