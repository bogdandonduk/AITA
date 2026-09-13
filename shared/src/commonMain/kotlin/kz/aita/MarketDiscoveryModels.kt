package kz.aita

import kotlinx.serialization.Serializable

const val MARKET_DISCOVERY_PAGE_SIZE = 40
const val MARKET_DISCOVERY_MAX_OFFERS = 400
const val MARKET_CATEGORY_MAX_COUNT = 4096
const val MARKET_DISCOVERY_RECENT = "recent"
const val MARKET_DISCOVERY_TITLE = "title"

/** Only the public category identity, localized name and ancestry cross this boundary.
 * No merchant conditions, quantities, images or private stock classification DTOs. */
@Serializable
data class MarketCategory(val id: String, val name: List<LocalizedStringDataModel>, val ancestorIds: List<String> = emptyList())

@Serializable
data class MarketCategoryCatalogue(val version: String, val categories: List<MarketCategory>)

@Serializable
data class MarketDiscoveryQuery(
    val text: String = "",
    val city: String = "",
    val storefrontId: String? = null,
    val categoryId: String? = null,
    val savedOnly: Boolean = false,
    val sort: String = MARKET_DISCOVERY_RECENT
)

/** Refresh the ENTIRE visible window in one server snapshot. More increases limit; it does not
 * join independently changing offset pages. Taxonomy bytes are omitted only for a known revision. */
@Serializable
data class MarketDiscoveryRequest(val query: MarketDiscoveryQuery, val limit: Int = MARKET_DISCOVERY_PAGE_SIZE,
    val knownCategoryVersion: String? = null)

@Serializable
data class MarketDiscoveryResult(
    val query: MarketDiscoveryQuery,
    val limit: Int,
    val page: MarketPage,
    val totalOffers: Long,
    val totalShops: Long,
    val categoryVersion: String,
    val categories: List<MarketCategory>? = null
)

data class MarketDiscoverySnapshot(val result: MarketDiscoveryResult, val catalogue: MarketCategoryCatalogue)

fun marketDiscoveryId(value: String): String? = value.lowercase().takeIf {
    it.matches(Regex("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}"))
}

private fun discoveryText(value: String, max: Int): String? {
    if (value.length > 512 || value.any { (it.code < 32 || it.code == 127) && !it.isWhitespace() }) return null
    val normalized = buildString {
        var space = false
        value.forEach { char ->
            if (char.isWhitespace()) { if (isNotEmpty()) space = true }
            else { if (space) append(' '); append(char); space = false }
        }
    }
    return normalized.takeIf { it.length <= max }
}

/** Both sides use the same normalization. Unknown filters are rejected, never silently ignored. */
fun MarketDiscoveryQuery.normalizedDiscoveryQuery(): MarketDiscoveryQuery? {
    val search = discoveryText(text, 120) ?: return null
    val place = discoveryText(city, 100) ?: return null
    if (search.isNotEmpty() && search.split(' ').size > 12) return null
    if (sort !in setOf(MARKET_DISCOVERY_RECENT, MARKET_DISCOVERY_TITLE)) return null
    val shop = storefrontId?.let { marketDiscoveryId(it) ?: return null }
    val category = categoryId?.let { marketDiscoveryId(it) ?: return null }
    return copy(text = search, city = if (shop == null) place else "", storefrontId = shop, categoryId = category)
}

fun MarketDiscoveryRequest.isValidDiscoveryRequest(): Boolean = query.normalizedDiscoveryQuery() != null &&
    limit in MARKET_DISCOVERY_PAGE_SIZE..MARKET_DISCOVERY_MAX_OFFERS && limit % MARKET_DISCOVERY_PAGE_SIZE == 0 &&
    (knownCategoryVersion == null || knownCategoryVersion.matches(Regex("[0-9a-f]{64}")))

fun MarketCategoryCatalogue.isValidMarketCatalogue(): Boolean = version.matches(Regex("[0-9a-f]{64}")) &&
    categories.size <= MARKET_CATEGORY_MAX_COUNT && categories.map { it.id }.distinct().size == categories.size &&
    categories.all { category -> marketDiscoveryId(category.id) == category.id && category.name.isNotEmpty() && category.name.size <= 12 &&
        category.name.all { it.language.isNotBlank() && it.language.length <= 12 && it.value.isNotBlank() && it.value.length <= 480 } &&
        category.ancestorIds.size <= 64 && category.ancestorIds.all { marketDiscoveryId(it) == it } }

/** Refuse a response for a different filter/window, malformed counts or missing taxonomy rather
 * than labelling an unfiltered response as selected-category results (including an older server). */
fun MarketDiscoveryResult.validatedDiscovery(
    request: MarketDiscoveryRequest,
    cachedCatalogue: MarketCategoryCatalogue?
): MarketDiscoverySnapshot? {
    if (!request.isValidDiscoveryRequest() || query != request.query.normalizedDiscoveryQuery() || limit != request.limit ||
        totalOffers < 0 || totalShops < 0 || totalShops > totalOffers || page.checkedAtMillis <= 0 ||
        page.unavailableSavedCount < 0 || page.offers.size != minOf(totalOffers, limit.toLong()).toInt() ||
        page.offers.map { it.id }.distinct().size != page.offers.size ||
        (page.nextId != null) != (totalOffers > page.offers.size)) return null
    val catalogue = categories?.let { MarketCategoryCatalogue(categoryVersion, it) }
        ?: cachedCatalogue?.takeIf { it.version == categoryVersion && request.knownCategoryVersion == categoryVersion }
        ?: return null
    if (!catalogue.isValidMarketCatalogue()) return null
    val tree = MarketCategoryTree(catalogue.categories)
    val knownIds = tree.byId.keys
    val selectedIds = query.categoryId?.let(tree::subtreeIds)
    if (query.categoryId != null && query.categoryId !in knownIds) return null
    if (page.nextId != null && page.nextId != page.offers.lastOrNull()?.id) return null
    if (totalShops < page.offers.map { it.storefront.storeId }.distinct().size || (!query.savedOnly && page.unavailableSavedCount != 0)) return null
    if (page.offers.any { offer -> marketDiscoveryId(offer.id) != offer.id ||
        marketDiscoveryId(offer.storefront.storeId) != offer.storefront.storeId || !offer.storefront.published ||
        offer.checkedAtMillis != page.checkedAtMillis || offer.title.isBlank() || offer.title.length > 180 ||
        offer.sourceUpdatedAtMillis < 0 || offer.categoryIds.size > 16 || offer.categoryIds.any { it !in knownIds } ||
        (selectedIds != null && offer.categoryIds.none { it in selectedIds }) || !offer.hasValidDiscoveryPrice() ||
        (query.storefrontId != null && offer.storefront.storeId != query.storefrontId) || (query.savedOnly && !offer.saved) ||
        offer.availability !in setOf(MARKET_AVAILABILITY_RECORDED, MARKET_AVAILABILITY_CONFIRM) }) return null
    return MarketDiscoverySnapshot(this, catalogue)
}

private fun MarketOffer.hasValidDiscoveryPrice(): Boolean {
    val price = priceMinor
    if (price == null) return currencyCode == null && pricedAmount == null && unitId == null && unitName.isEmpty()
    return price in 0L..1_000_000_000_000L && currencyCode?.matches(Regex("[A-Z]{3}")) == true &&
        pricedAmount?.let { it.isFinite() && it > 0.0 } == true && !unitId.isNullOrBlank() && unitId.length <= 128 &&
        unitName.size <= 12 && unitName.all { it.language.length <= 12 && it.value.length <= 120 }
}

/** One canonical single-parent view of AITA's root-first ancestor arrays. Legacy text tags and
 * missing parents are ignored; the nearest known ancestor wins. Cyclic members become roots,
 * retaining the categories without recursive loops or changing the underlying catalogue. */
class MarketCategoryTree(categories: List<MarketCategory>) {
    val byId: Map<String, MarketCategory> = categories.associateBy { it.id }
    private val parent: Map<String, String?> = run {
        val result = categories.associate { category -> category.id to category.ancestorIds.lastOrNull { it != category.id && it in byId } }.toMutableMap()
        val done = mutableSetOf<String>()
        categories.forEach { category ->
            val path = mutableListOf<String>(); val position = mutableMapOf<String, Int>()
            var cursor: String? = category.id
            while (cursor != null && cursor !in done) {
                val cycleStart = position[cursor]
                if (cycleStart != null) { path.drop(cycleStart).forEach { result[it] = null }; break }
                position[cursor] = path.size; path += cursor; cursor = result[cursor]
            }
            done.addAll(path)
        }
        result
    }
    private val children = categories.groupBy { parent[it.id] }
    fun childrenOf(id: String?): List<MarketCategory> = children[id].orEmpty()
    fun pathTo(id: String?): List<MarketCategory> {
        val path = mutableListOf<MarketCategory>(); var cursor = id
        while (cursor != null) { path += byId[cursor] ?: break; cursor = parent[cursor] }
        return path.asReversed()
    }
    fun subtreeIds(id: String): Set<String> {
        if (id !in byId) return emptySet()
        val result = linkedSetOf(id); val queue = mutableListOf(id); var next = 0
        while (next < queue.size) childrenOf(queue[next++]).forEach { if (result.add(it.id)) queue += it.id }
        return result
    }
}

/** Full bookmark mutation replies are not filtered catalogue pages. Only reconcile the IDs
 * already displayed; the next discovery read supplies matching rows and authoritative counts. */
fun MarketSavedReadFence.acknowledgeDiscoverySaved(window: MarketPage?, saved: MarketPage, savedOnly: Boolean): MarketPage? {
    require(saved.offers.size <= 100 && saved.unavailableSavedCount in 0..100 && saved.offers.size + saved.unavailableSavedCount <= 100 && saved.nextId == null &&
        saved.offers.map { it.id }.distinct().size == saved.offers.size &&
        saved.offers.all { it.saved && marketDiscoveryId(it.id) == it.id }) { "Invalid saved-offer acknowledgement" }
    val before = capture()
    acknowledgeSnapshot(saved.offers.map { it.id }.toSet(), saved.unavailableSavedCount)
    return window?.let { reconcile(it, before, savedOnly) }
}
