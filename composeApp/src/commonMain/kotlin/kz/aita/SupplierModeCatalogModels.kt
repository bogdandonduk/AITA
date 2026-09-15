package kz.aita


internal const val SUPPLIER_CATALOG_FILTER_STATE_KEY = "supplier_catalog_filter_v2"
internal const val SUPPLIER_CATALOG_SORT_STATE_KEY = "supplier_catalog_sort_v2"

internal const val SUPPLIER_CATALOG_FILTER_ALL = "all"
internal const val SUPPLIER_CATALOG_FILTER_OPEN = "open"
internal const val SUPPLIER_CATALOG_FILTER_MISSING_PRICE = "missing_price"
internal const val SUPPLIER_CATALOG_FILTER_REPLY = "reply"
internal const val SUPPLIER_CATALOG_FILTER_PRICE_BOOK = "pricebook"

internal const val SUPPLIER_CATALOG_SORT_ACTION = "action"
internal const val SUPPLIER_CATALOG_SORT_RECENT = "recent"
internal const val SUPPLIER_CATALOG_SORT_NAME = "name"
internal const val SUPPLIER_CATALOG_SORT_STORES = "stores"

private val supplierCatalogUuidRegex =
    Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$")

private fun String.supplierCatalogRelationId(): String = trim().lowercase()

private fun String.supplierCatalogBarcodeIdentity(): String =
    trim()
        .filterNot { it.isWhitespace() }
        .lowercase()

internal fun String.isSupplierCatalogServerId(): Boolean =
    trim().matches(supplierCatalogUuidRegex)

internal data class SupplierCatalogLineFacet(
    val catalogKey: String,
    val goodsItemId: String,
    val line: SupplierOrderLineDataModel,
    val isSubstitute: Boolean
)

private fun SupplierOrderLineDataModel.supplierCatalogSyntheticIdentity(): String = buildString {
    append(orderId.trim()).append('|')
    append(id.trim()).append('|')
    append(goodsItemBarcodeSnapshots.joinToString(",")).append('|')
    append(goodsItemNameSnapshot.joinToString(",") { "${it.language}:${it.value}" }).append('|')
    append(requestedQuantity.id).append('|')
    append(requestedQuantity.total)
}

private fun SupplierOrderLineDataModel.supplierCatalogStableLineIdentity(): String =
    id.trim().ifBlank { supplierCatalogSyntheticIdentity() }

internal fun SupplierOrderLineDataModel.supplierCatalogLineFacets(): List<SupplierCatalogLineFacet> {
    val requestedGoodsItemId = goodsItemId.supplierCatalogRelationId()
    val requestedKey = if (requestedGoodsItemId.isNotBlank()) {
        "goods:$requestedGoodsItemId"
    } else {
        "line:${supplierCatalogSyntheticIdentity()}:requested"
    }

    val facets = mutableListOf(
        SupplierCatalogLineFacet(
            catalogKey = requestedKey,
            goodsItemId = requestedGoodsItemId,
            line = this,
            isSubstitute = false
        )
    )

    val substituteId = substituteGoodsItemId.orEmpty().supplierCatalogRelationId()
    if (substituteId.isNotBlank() && substituteId != requestedGoodsItemId) {
        facets += SupplierCatalogLineFacet(
            catalogKey = "goods:$substituteId",
            goodsItemId = substituteId,
            line = this,
            isSubstitute = true
        )
    }

    return facets
}

private fun SupplierCatalogLineFacet.supplierCatalogGroupingTokens(): List<String> = buildList {
    goodsItemId.supplierCatalogRelationId()
        .takeIf { it.isNotBlank() }
        ?.let { add("goods:$it") }
    barcodeSnapshots()
        .asSequence()
        .map { it.supplierCatalogBarcodeIdentity() }
        .filter { it.isNotBlank() }
        .distinct()
        .forEach { add("barcode:$it") }
    if (isEmpty()) add(catalogKey)
}

private fun SupplierGoodsPriceDataModel.supplierCatalogGroupingTokens(): List<String> = buildList {
    val cleanGoodsItemId = goodsItemId.supplierCatalogRelationId()
    if (cleanGoodsItemId.isNotBlank()) {
        // A supplier barcode is editable per Store offer. It must never become a product-identity
        // bridge; the immutable Store-product relation ID is the only safe price-row anchor.
        add("goods:$cleanGoodsItemId")
    }
    if (isEmpty()) {
        add("price:${supplierPriceBookIdentity()}")
    }
}

internal data class SupplierCatalogSourceGroup(
    val catalogKey: String,
    val facets: List<SupplierCatalogLineFacet>,
    val prices: List<SupplierGoodsPriceDataModel>
)

internal fun groupSupplierCatalogSources(
    facets: List<SupplierCatalogLineFacet>,
    prices: List<SupplierGoodsPriceDataModel>
): List<SupplierCatalogSourceGroup> {
    data class Source(
        val facet: SupplierCatalogLineFacet? = null,
        val price: SupplierGoodsPriceDataModel? = null,
        val tokens: List<String>
    )

    val sources = buildList {
        facets.forEach { facet ->
            add(Source(facet = facet, tokens = facet.supplierCatalogGroupingTokens()))
        }
        prices.forEach { price ->
            add(Source(price = price, tokens = price.supplierCatalogGroupingTokens()))
        }
    }
    if (sources.isEmpty()) return emptyList()

    val parents = IntArray(sources.size) { it }
    val ranks = IntArray(sources.size)

    fun find(index: Int): Int {
        var cursor = index
        while (parents[cursor] != cursor) cursor = parents[cursor]
        val root = cursor
        cursor = index
        while (parents[cursor] != cursor) {
            val next = parents[cursor]
            parents[cursor] = root
            cursor = next
        }
        return root
    }

    fun union(first: Int, second: Int) {
        var firstRoot = find(first)
        var secondRoot = find(second)
        if (firstRoot == secondRoot) return
        if (ranks[firstRoot] < ranks[secondRoot]) {
            val swap = firstRoot
            firstRoot = secondRoot
            secondRoot = swap
        }
        parents[secondRoot] = firstRoot
        if (ranks[firstRoot] == ranks[secondRoot]) ranks[firstRoot] += 1
    }

    val firstSourceByToken = mutableMapOf<String, Int>()
    sources.forEachIndexed { index, source ->
        source.tokens.forEach { token ->
            val firstIndex = firstSourceByToken[token]
            if (firstIndex == null) {
                firstSourceByToken[token] = index
            } else {
                union(firstIndex, index)
            }
        }
    }

    return sources.indices
        .groupBy(::find)
        .values
        .map { sourceIndexes ->
            val groupedSources = sourceIndexes.map(sources::get)
            val facetTokens = groupedSources
                .filter { it.facet != null }
                .flatMap { it.tokens }
                .distinct()
            val priceTokens = groupedSources
                .filter { it.price != null }
                .flatMap { it.tokens }
                .distinct()
            val allTokens = (facetTokens + priceTokens).distinct()
            val catalogKey = facetTokens
                .filter { it.startsWith("barcode:") }
                .sorted()
                .firstOrNull()
                ?: facetTokens.filter { it.startsWith("goods:") }.sorted().firstOrNull()
                ?: priceTokens.filter { it.startsWith("goods:") }.sorted().firstOrNull()
                ?: priceTokens.filter { it.startsWith("barcode:") }.sorted().firstOrNull()
                ?: allTokens.sorted().first()

            SupplierCatalogSourceGroup(
                catalogKey = catalogKey,
                facets = groupedSources.mapNotNull { it.facet },
                prices = groupedSources.mapNotNull { it.price }
            )
        }
        .sortedBy { it.catalogKey }
}

internal fun supplierCatalogOfferIdentity(
    storeId: String,
    supplierId: String,
    goodsItemId: String
): String = listOf(
    storeId.supplierCatalogRelationId(),
    supplierId.supplierCatalogRelationId(),
    goodsItemId.supplierCatalogRelationId()
).joinToString("|")

internal data class SupplierCatalogOfferUiModel(
    val offerKey: String,
    val storeId: String,
    val supplierId: String,
    val goodsItemId: String,
    val storeTitle: String,
    val storePublicId: String,
    val supplierTitle: String,
    val existingPrice: SupplierGoodsPriceDataModel?,
    val expectedPrice: PriceDataModel?,
    val quantityTemplate: QuantityDataModel,
    val openOrderCount: Int,
    val lastActivityMillis: Long,
    val supplierGoodsName: String,
    val supplierBarcode: String,
    val canEdit: Boolean
) {
    /**
     * Older servers accepted zero-valued price-book rows. Keep such a row as the editable record
     * (and preserve its MOQ/name/barcode), but do not present it as a completed supplier offer.
     */
    val hasUsablePrice: Boolean
        get() = existingPrice?.supplyPrice.hasPositiveSupplierDeskPrice()
}

internal data class SupplierCatalogItemUiModel(
    val catalogKey: String,
    val goodsItemId: String,
    val goodsItemIds: List<String>,
    val orderSearchQuery: String,
    val title: String,
    val barcodeText: String,
    val totalQuantityText: String,
    val expectedPriceText: String,
    val savedPriceText: String,
    val storeTitles: List<String>,
    val openOrderCount: Int,
    val orderCount: Int,
    val lastActivityMillis: Long,
    val latestStatus: SupplierOrderStatusDataModel,
    val needsReply: Boolean,
    val hasSavedOffer: Boolean,
    val hasMissingOffer: Boolean,
    val searchKey: String,
    val offerNote: String,
    val offers: List<SupplierCatalogOfferUiModel>
) {
    val savedOfferCount: Int
        get() = offers.count { it.hasUsablePrice }

    val storeCount: Int
        get() = offers.map { it.storeId }.filter { it.isNotBlank() }.distinct().size
}

internal fun SupplierCatalogItemUiModel.matchesSupplierCatalogFilter(filterId: String): Boolean = when (filterId) {
    SUPPLIER_CATALOG_FILTER_OPEN -> openOrderCount > 0
    SUPPLIER_CATALOG_FILTER_MISSING_PRICE -> hasMissingOffer
    SUPPLIER_CATALOG_FILTER_REPLY -> needsReply
    SUPPLIER_CATALOG_FILTER_PRICE_BOOK -> hasSavedOffer
    else -> true
}

internal fun List<SupplierCatalogItemUiModel>.countForSupplierCatalogFilter(filterId: String): Int =
    count { it.matchesSupplierCatalogFilter(filterId) }

internal fun List<SupplierCatalogItemUiModel>.sortedForSupplierCatalog(sortId: String): List<SupplierCatalogItemUiModel> {
    val comparator = when (sortId) {
        SUPPLIER_CATALOG_SORT_RECENT ->
            compareByDescending<SupplierCatalogItemUiModel> { it.lastActivityMillis }
                .thenBy { it.title.lowercase() }

        SUPPLIER_CATALOG_SORT_NAME ->
            compareBy<SupplierCatalogItemUiModel> { it.title.lowercase() }
                .thenByDescending { it.lastActivityMillis }

        SUPPLIER_CATALOG_SORT_STORES ->
            compareByDescending<SupplierCatalogItemUiModel> { it.storeCount }
                .thenByDescending { it.openOrderCount }
                .thenBy { it.title.lowercase() }

        else ->
            compareByDescending<SupplierCatalogItemUiModel> { if (it.needsReply) 1 else 0 }
                .thenByDescending { if (it.hasMissingOffer) 1 else 0 }
                .thenByDescending { if (it.openOrderCount > 0) 1 else 0 }
                .thenByDescending { it.openOrderCount }
                .thenByDescending { it.lastActivityMillis }
                .thenBy { it.title.lowercase() }
    }
    return sortedWith(comparator)
}

internal suspend fun seedSupplierCatalogNavigation(
    searchQuery: String = "",
    filterId: String = SUPPLIER_CATALOG_FILTER_ALL,
    sortId: String = SUPPLIER_CATALOG_SORT_ACTION
) {
    NavigationScreenModel.Supplier.Catalog.Main.setStates(
        NavigationScreenModel.KEY_STATE_SEARCH_QUERY to searchQuery.trim(),
        SUPPLIER_CATALOG_FILTER_STATE_KEY to filterId.ifBlank { SUPPLIER_CATALOG_FILTER_ALL },
        SUPPLIER_CATALOG_SORT_STATE_KEY to sortId.ifBlank { SUPPLIER_CATALOG_SORT_ACTION }
    )
}

private fun AppConfiguration.supplierCatalogFacetTitle(facet: SupplierCatalogLineFacet): String =
    if (facet.isSubstitute) supplierDeskSubstituteTitle(facet.line) else supplierDeskLineTitle(facet.line)

private fun SupplierCatalogLineFacet.barcodeSnapshots(): List<String> =
    if (isSubstitute) line.substituteGoodsItemBarcodeSnapshots else line.goodsItemBarcodeSnapshots

private fun SupplierCatalogLineFacet.quantityForCatalog(): QuantityDataModel =
    if (isSubstitute) line.supplierAcceptedQuantity ?: line.requestedQuantity else line.requestedQuantity

private fun SupplierCatalogLineFacet.expectedPriceForCatalog(): PriceDataModel? = when {
    isSubstitute -> line.supplierOfferedSupplyPrice ?: line.expectedSupplyPrice
    line.substituteGoodsItemId.orEmpty().isNotBlank() -> line.expectedSupplyPrice
    else -> line.supplierOfferedSupplyPrice ?: line.expectedSupplyPrice
}

private fun supplierCatalogPreferredBarcode(
    facetBarcodes: List<String>,
    priceBarcodes: List<String>
): String {
    val candidates = (facetBarcodes + priceBarcodes)
        .map { it.trim() }
        .filter { it.isNotBlank() }
    if (candidates.isEmpty()) return ""

    val candidatesByIdentity = candidates.groupBy { it.supplierCatalogBarcodeIdentity() }
    val preferredIdentity = candidatesByIdentity.entries
        .sortedWith(
            compareByDescending<Map.Entry<String, List<String>>> { entry ->
                facetBarcodes.count {
                    it.supplierCatalogBarcodeIdentity() == entry.key
                }
            }
                .thenByDescending { it.value.size }
                .thenBy { it.key }
        )
        .first()
        .key
    return candidatesByIdentity.getValue(preferredIdentity).first()
}

internal fun AppConfiguration.supplierCatalogQuantityText(
    facets: List<SupplierCatalogLineFacet>
): String {
    val activeFacets = facets
        .filter { it.line.isActive }
        .groupBy { it.line.supplierCatalogStableLineIdentity() }
        .values
        .mapNotNull { lineFacets ->
            lineFacets.maxByOrNull { if (it.isSubstitute) 1 else 0 }
        }
    if (activeFacets.isEmpty()) return ""

    val quantities = activeFacets.map { it.quantityForCatalog() }
    val unitKeys = quantities.map { quantity ->
        quantity.id.ifBlank { quantity.immutableUnitName.visibleLocalizedString(stateValues.appLanguage, "") }
    }.distinct()

    return if (unitKeys.size == 1) {
        val firstQuantity = quantities.first()
        firstQuantity
            .copy(total = quantities.sumOf { it.total })
            .quantityText(stateValues.appLanguage)
    } else {
        buildString {
            append(quantities.take(3).joinToString(" • ") { it.quantityText(stateValues.appLanguage) })
            if (quantities.size > 3) append(" +").append(quantities.size - 3)
        }
    }
}

internal fun AppConfiguration.buildSupplierCatalogItems(
    orders: List<SupplierOrderDataModel>,
    lines: List<SupplierOrderLineDataModel>,
    supplierPrices: List<SupplierGoodsPriceDataModel>,
    dashboard: SupplierModeDashboardDataModel? = null
): List<SupplierCatalogItemUiModel> {
    val activeOrders = orders
        .filter { it.isActive && it.status != SupplierOrderStatusDataModel.Draft }
    val ordersById = activeOrders.associateBy { it.id }
    val activeLines = lines.filter { line -> line.isActive && ordersById[line.orderId] != null }
    val activeFacets = activeLines.flatMap { it.supplierCatalogLineFacets() }
    val activePrices = supplierPrices
        .normalizedSupplierGoodsPriceBook()
        .filter { price -> price.goodsItemId.supplierCatalogRelationId().isNotBlank() }

    val catalogGroups = groupSupplierCatalogSources(activeFacets, activePrices)

    val dashboardPartnersByStoreId = dashboard
        ?.partnerHighlights
        .orEmpty()
        .filter { it.storeId.isNotBlank() }
        .associateBy { it.storeId.supplierCatalogRelationId() }

    val dashboardProfilesBySupplierId = dashboard
        ?.supplierProfiles
        .orEmpty()
        .filter { it.supplierId.isNotBlank() }
        .associateBy { it.supplierId.supplierCatalogRelationId() }
    val localProfilesBySupplierId = stateValues.suppliers
        .orEmpty()
        .supplierProfilesOwnedBy(stateValues.userAccount?.id)
        .filter { it.id.isNotBlank() }
        .associateBy { it.id.supplierCatalogRelationId() }

    return catalogGroups.map { group ->
        val catalogKey = group.catalogKey
        val itemFacets = group.facets
        val itemPrices = group.prices
        val goodsItemIds = (
                itemFacets.map { it.goodsItemId } +
                        itemPrices.map { it.goodsItemId }
                )
            .map { it.supplierCatalogRelationId() }
            .filter { it.isNotBlank() }
            .distinct()
        val goodsItemId = goodsItemIds.firstOrNull().orEmpty()

        val relatedOrders = itemFacets
            .mapNotNull { ordersById[it.line.orderId] }
            .distinctBy { it.id }
            .sortedByDescending { it.supplierDeskSortTime() }
        val latestOrder = relatedOrders.firstOrNull()
        val latestPrice = itemPrices.maxByOrNull { price ->
            maxOf(price.lastUsedAtMillis ?: 0L, price.updatedAtMillis, price.createdAtMillis)
        }

        val title = itemFacets
            .asSequence()
            .sortedByDescending { facet -> ordersById[facet.line.orderId]?.supplierDeskSortTime() ?: 0L }
            .map { supplierCatalogFacetTitle(it) }
            .firstOrNull { it.isNotBlank() }
            ?: latestPrice?.supplierGoodsName?.takeIf { it.isNotBlank() }
            ?: goodsItemId.take(12).ifBlank { localizedStringResource(2312, "Products") }

        val facetBarcodes = itemFacets.flatMap { it.barcodeSnapshots() }
        val priceBarcodes = itemPrices.mapNotNull { it.supplierBarcode }
        val barcodeText = supplierCatalogPreferredBarcode(
            facetBarcodes = facetBarcodes,
            priceBarcodes = priceBarcodes
        )

        val relationIdentities = buildList {
            itemFacets.forEach { facet ->
                val order = ordersById[facet.line.orderId] ?: return@forEach
                if (
                    order.storeId.isNotBlank() &&
                    order.supplierId.isNotBlank() &&
                    facet.goodsItemId.isNotBlank()
                ) {
                    add(
                        supplierCatalogOfferIdentity(
                            order.storeId,
                            order.supplierId,
                            facet.goodsItemId
                        )
                    )
                }
            }
            itemPrices.forEach { price ->
                add(supplierCatalogOfferIdentity(price.storeId, price.supplierId, price.goodsItemId))
            }
        }.distinct()

        val offers = relationIdentities.mapNotNull { identity ->
            val identityParts = identity.split('|')
            if (identityParts.size != 3) return@mapNotNull null
            val storeId = identityParts[0]
            val supplierId = identityParts[1]
            val relationGoodsItemId = identityParts[2]
            val relationFacetOrderIds = itemFacets
                .filter {
                    it.goodsItemId.supplierCatalogRelationId() == relationGoodsItemId
                }
                .map { it.line.orderId }
                .toSet()
            val relationOrders = relatedOrders
                .filter {
                    it.id in relationFacetOrderIds &&
                            it.storeId.supplierCatalogRelationId() == storeId &&
                            it.supplierId.supplierCatalogRelationId() == supplierId
                }
                .sortedByDescending { it.supplierDeskSortTime() }
            val relationOpenOrders = relationOrders.filterNot { it.status.isSupplierOrderClosed() }
            val relationOrderIds = relationOrders.map { it.id }.toSet()
            val relationFacets = itemFacets.filter {
                it.line.orderId in relationOrderIds &&
                        it.goodsItemId.supplierCatalogRelationId() == relationGoodsItemId
            }
            val preferredOrderIds = relationOpenOrders
                .ifEmpty { relationOrders }
                .map { it.id }
                .toSet()
            val relationLatestFacet = relationFacets
                .filter { it.line.orderId in preferredOrderIds }
                .maxByOrNull { facet ->
                    ordersById[facet.line.orderId]?.supplierDeskSortTime() ?: 0L
                }
            val latestRelationOrder = relationOrders.firstOrNull()
            val preferredRelationOrder = relationOpenOrders.firstOrNull() ?: latestRelationOrder
            val existingPrice = itemPrices
                .filter {
                    it.storeId.supplierCatalogRelationId() == storeId &&
                            it.supplierId.supplierCatalogRelationId() == supplierId &&
                            it.goodsItemId.supplierCatalogRelationId() == relationGoodsItemId
                }
                .maxByOrNull { price ->
                    maxOf(price.lastUsedAtMillis ?: 0L, price.updatedAtMillis, price.createdAtMillis)
                }
            val dashboardPartner = dashboardPartnersByStoreId[storeId]
            val storeTitle = preferredRelationOrder?.let { supplierDeskStoreTitle(it) }
                .orEmpty()
                .ifBlank {
                    dashboardPartner
                        ?.storeNameSnapshot
                        .orEmpty()
                        .visibleLocalizedString(stateValues.appLanguage, "")
                }
                .ifBlank { dashboardPartner?.storePublicIdSnapshot.orEmpty() }
                .ifBlank { dashboardPartner?.storeAddressTextSnapshot.orEmpty() }
                .ifBlank { stateValues.stringNoName }
            val storePublicId = preferredRelationOrder?.storePublicIdSnapshot
                .orEmpty()
                .ifBlank { dashboardPartner?.storePublicIdSnapshot.orEmpty() }
            val supplierTitle = dashboardProfilesBySupplierId[supplierId]
                ?.name
                .orEmpty()
                .visibleLocalizedString(stateValues.appLanguage, "")
                .ifBlank {
                    localProfilesBySupplierId[supplierId]
                        ?.visibleSupplierName(stateValues.appLanguage)
                        .orEmpty()
                }
                .ifBlank { stateValues.stringNoName }
            val quantityTemplate = relationLatestFacet?.quantityForCatalog()
                ?: existingPrice?.minOrderQuantity
                ?: existingPrice?.packageQuantity
                ?: stateValues.globalAppConfiguration.goodsItemsQuantityUnits.firstOrNull()
                ?: QuantityDataModel(
                    id = "0",
                    immutableUnitName = listOf(LocalizedStringDataModel("main", "unit"), LocalizedStringDataModel("ky", "бирдик")),
                    total = 1.0,
                    pricedAmount = 1.0,
                    roundTotal = false
                )
            val expectedPrice = relationLatestFacet?.expectedPriceForCatalog()
            val lastOrderActivityMillis = latestRelationOrder?.supplierDeskSortTime() ?: 0L
            val lastPriceActivityMillis = existingPrice?.let { price ->
                maxOf(price.lastUsedAtMillis ?: 0L, price.updatedAtMillis, price.createdAtMillis)
            } ?: 0L

            SupplierCatalogOfferUiModel(
                offerKey = identity,
                storeId = storeId,
                supplierId = supplierId,
                goodsItemId = relationGoodsItemId,
                storeTitle = storeTitle,
                storePublicId = storePublicId,
                supplierTitle = supplierTitle,
                existingPrice = existingPrice,
                expectedPrice = expectedPrice,
                quantityTemplate = quantityTemplate,
                openOrderCount = relationOpenOrders.size,
                lastActivityMillis = maxOf(lastOrderActivityMillis, lastPriceActivityMillis),
                supplierGoodsName = if (existingPrice == null) {
                    title
                } else {
                    existingPrice.supplierGoodsName.orEmpty()
                },
                supplierBarcode = if (existingPrice == null) {
                    barcodeText
                } else {
                    existingPrice.supplierBarcode.orEmpty()
                },
                canEdit = storeId.isSupplierCatalogServerId() &&
                        supplierId.isSupplierCatalogServerId() &&
                        relationGoodsItemId.isSupplierCatalogServerId()
            )
        }.sortedWith(
            compareByDescending<SupplierCatalogOfferUiModel> { if (it.openOrderCount > 0) 1 else 0 }
                .thenByDescending { if (!it.hasUsablePrice) 1 else 0 }
                .thenByDescending { it.lastActivityMillis }
                .thenBy { it.storeTitle.lowercase() }
        )

        val itemLinesByOrder = itemFacets
            .groupBy { it.line.orderId }
            .mapValues { (_, facets) ->
                facets.map { it.line }.distinctBy { it.supplierCatalogStableLineIdentity() }
            }
        val needsReply = relatedOrders.any { order ->
            supplierOrderNeedsAttentionInInbox(
                status = order.status,
                hasResponseGaps = SupplierOrderWithLinesDataModel(
                    order = order,
                    lines = itemLinesByOrder[order.id].orEmpty()
                ).hasSupplierResponseGapsForSupplierDesk()
            )
        }
        val openOrderIds = relatedOrders
            .filterNot { it.status.isSupplierOrderClosed() }
            .map { it.id }
            .toSet()
        val openOrderCount = openOrderIds.size
        val totalQuantityText = supplierCatalogQuantityText(
            itemFacets.filter { it.line.orderId in openOrderIds }
        ).ifBlank { localizedStringResource(1681, "No open quantity yet") }
        val savedPrices = offers
            .filter { it.hasUsablePrice }
            .mapNotNull { it.existingPrice?.supplyPrice }
            .map { it.supplierDeskMoneyText() }
            .filter { it.isNotBlank() }
            .distinct()
        val savedPriceText = when (savedPrices.size) {
            0 -> ""
            1 -> savedPrices.first()
            else -> "${savedPrices.first()} +${savedPrices.size - 1}"
        }
        val expectedPriceText = offers
            .asSequence()
            .mapNotNull { it.expectedPrice }
            .map { it.supplierDeskMoneyText() }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
        val storeTitles = offers.map { it.storeTitle }.filter { it.isNotBlank() }.distinct()
        val orderSearchQuery = goodsItemIds.singleOrNull()
            ?: barcodeText.takeIf { it.isNotBlank() }
            ?: title
        val latestStatus = latestOrder?.status ?: SupplierOrderStatusDataModel.Draft
        val lastActivityMillis = maxOf(
            latestOrder?.supplierDeskSortTime() ?: 0L,
            latestPrice?.let { price ->
                maxOf(price.lastUsedAtMillis ?: 0L, price.updatedAtMillis, price.createdAtMillis)
            } ?: 0L
        )
        val hasSavedOffer = offers.any { it.hasUsablePrice }
        val hasMissingOffer = offers.any { offer ->
            offer.openOrderCount > 0 && !offer.hasUsablePrice
        }
        val storeListText = storeTitles.take(4).joinToString(", ")
            .ifBlank { localizedStringResource(1410, "Interested stores") }
        val offerNote = buildString {
            append(title)
            if (barcodeText.isNotBlank()) {
                append('\n').append(stateValues.stringBarcode).append(": ").append(barcodeText)
            }
            append('\n')
                .append(localizedStringResource(1424, "Total requested"))
                .append(": ")
                .append(totalQuantityText)
            if (expectedPriceText.isNotBlank()) {
                append('\n')
                    .append(localizedStringResource(2346, "Expected by store"))
                    .append(": ")
                    .append(expectedPriceText)
            }
            if (savedPrices.isNotEmpty()) {
                append('\n')
                    .append(localizedStringResource(2315, "Saved offers"))
                    .append(": ")
                    .append(savedPrices.size)
            }
            append('\n')
                .append(localizedStringResource(2335, "Stores"))
                .append(": ")
                .append(storeListText)
            append('\n')
                .append(localizedStringResource(2336, "Open requests"))
                .append(": ")
                .append(openOrderCount)
        }
        val searchKey = buildString {
            append(catalogKey).append(' ')
            append(goodsItemId).append(' ')
            append(goodsItemIds.joinToString(" ")).append(' ')
            append(title).append(' ')
            append(barcodeText).append(' ')
            append(totalQuantityText).append(' ')
            append(expectedPriceText).append(' ')
            append(savedPrices.joinToString(" ")).append(' ')
            append(storeTitles.joinToString(" ")).append(' ')
            append(relatedOrders.joinToString(" ") { order ->
                listOf(
                    order.id,
                    order.storeId,
                    order.supplierId,
                    order.storePublicIdSnapshot,
                    supplierDeskStoreTitle(order),
                    order.status.name
                ).joinToString(" ")
            }).append(' ')
            append(itemFacets.joinToString(" ") { facet ->
                val line = facet.line
                listOf(
                    line.id,
                    line.goodsItemId,
                    line.substituteGoodsItemId.orEmpty(),
                    line.goodsItemNameSnapshot.joinToString(" ") { it.value },
                    line.substituteGoodsItemNameSnapshot.joinToString(" ") { it.value },
                    line.goodsItemBarcodeSnapshots.joinToString(" "),
                    line.substituteGoodsItemBarcodeSnapshots.joinToString(" "),
                    line.additionalNotes.orEmpty(),
                    line.supplierComment.orEmpty()
                ).joinToString(" ")
            }).append(' ')
            append(offers.joinToString(" ") { offer ->
                listOf(
                    offer.storeId,
                    offer.supplierId,
                    offer.goodsItemId,
                    offer.storeTitle,
                    offer.storePublicId,
                    offer.supplierTitle,
                    offer.supplierGoodsName,
                    offer.supplierBarcode,
                    offer.existingPrice?.supplyPrice.supplierDeskMoneyText(),
                    offer.expectedPrice.supplierDeskMoneyText()
                ).joinToString(" ")
            })
        }.lowercase()

        SupplierCatalogItemUiModel(
            catalogKey = catalogKey,
            goodsItemId = goodsItemId,
            goodsItemIds = goodsItemIds,
            orderSearchQuery = orderSearchQuery,
            title = title,
            barcodeText = barcodeText,
            totalQuantityText = totalQuantityText,
            expectedPriceText = expectedPriceText,
            savedPriceText = savedPriceText,
            storeTitles = storeTitles,
            openOrderCount = openOrderCount,
            orderCount = relatedOrders.size,
            lastActivityMillis = lastActivityMillis,
            latestStatus = latestStatus,
            needsReply = needsReply,
            hasSavedOffer = hasSavedOffer,
            hasMissingOffer = hasMissingOffer,
            searchKey = searchKey,
            offerNote = offerNote,
            offers = offers
        )
    }.sortedForSupplierCatalog(SUPPLIER_CATALOG_SORT_ACTION)
}
