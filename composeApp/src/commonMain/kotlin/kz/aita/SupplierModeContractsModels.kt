package kz.aita

import androidx.compose.ui.graphics.Color

internal const val SUPPLIER_CONTRACT_FILTER_ALL = "all"
internal const val SUPPLIER_CONTRACT_FILTER_PENDING = "pending"
internal const val SUPPLIER_CONTRACT_FILTER_WAITING_ME = "waiting_me"
internal const val SUPPLIER_CONTRACT_FILTER_WAITING_OTHER = "waiting_other"
internal const val SUPPLIER_CONTRACT_FILTER_ACTIVE = "active"
internal const val SUPPLIER_CONTRACT_FILTER_DECLINED = "declined"

internal const val SUPPLIER_CONTRACT_SORT_ACTION = "action"
internal const val SUPPLIER_CONTRACT_SORT_RECENT = "recent"
internal const val SUPPLIER_CONTRACT_SORT_PARTNER = "partner"
internal const val SUPPLIER_CONTRACT_SORT_REVISION = "revision"

internal data class SupplierContractWorkspaceUiModel(
    val contract: SupplierPartnershipContractDataModel,
    val partnerKey: String,
    val partnerTitle: String,
    val partnerSubtitle: String,
    val searchKey: String,
    val latestActivityMillis: Long,
    val selectedGoodsCount: Int,
    val activePriceTermCount: Int,
    val waitsForMe: Boolean,
    val waitsForOtherSide: Boolean,
    val blocksSupply: Boolean
) {
    val id: String get() = contract.id
    val isActiveAgreement: Boolean get() = contract.status == SUPPLIER_CONTRACT_STATUS_ACTIVE
    val isDeclined: Boolean get() = contract.status == SUPPLIER_CONTRACT_STATUS_DECLINED
}


internal data class SupplierContractPartnerUiModel(
    val key: String,
    val storeId: String,
    val supplierId: String,
    val title: String,
    val subtitle: String
)

internal data class SupplierContractGoodsUiModel(
    val key: String,
    val storeId: String,
    val supplierId: String,
    val goodsItemId: String,
    val title: String,
    val subtitle: String,
    val priceText: String,
    val quantityText: String,
    val nameSnapshot: List<LocalizedStringDataModel>,
    val latestSupplyPrice: PriceDataModel?,
    val latestQuantity: QuantityDataModel?
)

internal data class SupplierContractLineGoodsFacet(
    val goodsItemId: String,
    val nameSnapshot: List<LocalizedStringDataModel>,
    val barcodeSnapshots: List<String>,
    val isSubstitute: Boolean
)

/**
 * A confirmed substitute is a distinct commercial product and must be contractable independently
 * from the Store's originally requested item. Equal IDs are deliberately collapsed.
 */
internal fun SupplierOrderLineDataModel.supplierContractGoodsFacets(): List<SupplierContractLineGoodsFacet> {
    val requestedGoodsItemId = goodsItemId.trim()
    val cleanSubstituteGoodsItemId = substituteGoodsItemId.orEmpty().trim()
    return buildList {
        if (requestedGoodsItemId.isNotBlank()) {
            add(
                SupplierContractLineGoodsFacet(
                    goodsItemId = requestedGoodsItemId,
                    nameSnapshot = goodsItemNameSnapshot,
                    barcodeSnapshots = goodsItemBarcodeSnapshots,
                    isSubstitute = false
                )
            )
        }
        if (
            cleanSubstituteGoodsItemId.isNotBlank() &&
            !cleanSubstituteGoodsItemId.equals(requestedGoodsItemId, ignoreCase = true)
        ) {
            add(
                SupplierContractLineGoodsFacet(
                    goodsItemId = cleanSubstituteGoodsItemId,
                    nameSnapshot = substituteGoodsItemNameSnapshot,
                    barcodeSnapshots = substituteGoodsItemBarcodeSnapshots,
                    isSubstitute = true
                )
            )
        }
    }
}

internal fun AppConfiguration.supplierContractStatusTitle(status: String): String = when (status) {
    SUPPLIER_CONTRACT_STATUS_PENDING_SUPPLIER -> localizedStringResource(1487, "Pending supplier")
    SUPPLIER_CONTRACT_STATUS_ACTIVE -> localizedStringResource(1488, "Active contract")
    SUPPLIER_CONTRACT_STATUS_DECLINED -> localizedStringResource(1489, "Declined")
    SUPPLIER_CONTRACT_STATUS_ARCHIVED -> localizedStringResource(1490, "Archived")
    else -> localizedStringResource(1486, "Pending store")
}

internal fun AppConfiguration.supplierContractScopeTitle(scope: String): String = when (scope) {
    SUPPLIER_CONTRACT_SCOPE_GOODS_ITEM -> localizedStringResource(1492, "Goods item")
    SUPPLIER_CONTRACT_SCOPE_GOODS_GROUP -> localizedStringResource(1493, "Goods group")
    else -> localizedStringResource(1491, "Partnership-wide")
}

internal fun AppConfiguration.supplierVisibleContractTitle(contract: SupplierPartnershipContractDataModel): String =
    contract.title.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { contract.summary.visibleLocalizedString(stateValues.appLanguage, "") }
        .ifBlank { supplierContractScopeTitle(contract.scopeType) }

internal fun AppConfiguration.supplierVisibleContractSummary(contract: SupplierPartnershipContractDataModel): String =
    contract.summary.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { contract.customTerms.visibleLocalizedString(stateValues.appLanguage, "") }
        .ifBlank { localizedStringResource(1512, "Both sides must accept the same revision before supply is unlocked.") }

internal fun AppConfiguration.supplierContractStatusColor(status: String): Color = when (status) {
    SUPPLIER_CONTRACT_STATUS_ACTIVE -> stateValues.OkayColor
    SUPPLIER_CONTRACT_STATUS_DECLINED, SUPPLIER_CONTRACT_STATUS_ARCHIVED -> stateValues.DisabledColor
    SUPPLIER_CONTRACT_STATUS_PENDING_SUPPLIER, SUPPLIER_CONTRACT_STATUS_PENDING_STORE -> stateValues.BorderlineBadColor
    else -> stateValues.PlaceholderTextColor
}

internal fun supplierContractRelationshipKey(
    storeId: String,
    supplierId: String
): Pair<String, String>? {
    val cleanStoreId = storeId.trim().lowercase()
    val cleanSupplierId = supplierId.trim().lowercase()
    if (cleanStoreId.isBlank() || cleanSupplierId.isBlank()) return null
    return cleanStoreId to cleanSupplierId
}

internal fun AppConfiguration.buildSupplierContractPartners(
    actorSide: String,
    fixedStoreId: String?,
    orders: List<SupplierOrderDataModel>,
    contracts: List<SupplierPartnershipContractDataModel>,
    prices: List<SupplierGoodsPriceDataModel>,
    suppliers: List<SupplierDataModel>,
    dashboard: SupplierModeDashboardDataModel? = null
): List<SupplierContractPartnerUiModel> {
    val cleanFixedStoreId = fixedStoreId?.trim()?.lowercase().orEmpty()
    val activeOrders = orders.filter { order ->
        order.isActive && (
                cleanFixedStoreId.isBlank() ||
                        order.storeId.trim().lowercase() == cleanFixedStoreId
                )
    }
    val activeContracts = contracts.filter { contract ->
        contract.isActive && (
                cleanFixedStoreId.isBlank() ||
                        contract.storeId.trim().lowercase() == cleanFixedStoreId
                )
    }
    val activePrices = prices.normalizedSupplierGoodsPriceBook().filter { price ->
        cleanFixedStoreId.isBlank() || price.storeId.trim().lowercase() == cleanFixedStoreId
    }

    val ordersByRelationship = activeOrders
        .mapNotNull { order ->
            supplierContractRelationshipKey(order.storeId, order.supplierId)?.let { it to order }
        }
        .groupBy(keySelector = { it.first }, valueTransform = { it.second })
    val contractsByRelationship = activeContracts
        .mapNotNull { contract ->
            supplierContractRelationshipKey(contract.storeId, contract.supplierId)?.let { it to contract }
        }
        .groupBy(keySelector = { it.first }, valueTransform = { it.second })
    val pricesByRelationship = activePrices
        .mapNotNull { price ->
            supplierContractRelationshipKey(price.storeId, price.supplierId)?.let { it to price }
        }
        .groupBy(keySelector = { it.first }, valueTransform = { it.second })
    val dashboardPartnersByStoreId = dashboard?.partnerHighlights.orEmpty()
        .filter { it.storeId.isNotBlank() }
        .associateBy { it.storeId.trim().lowercase() }
    val suppliersById = suppliers
        .filter { it.id.isNotBlank() }
        .associateBy { it.id.trim().lowercase() }

    val relationshipKeys = (
            ordersByRelationship.keys +
                    contractsByRelationship.keys +
                    pricesByRelationship.keys
            )
        .filter { (storeId, supplierId) -> storeId.isNotBlank() && supplierId.isNotBlank() }
        .distinct()

    val relationshipPartners = relationshipKeys.map { ids ->
        val relationshipOrders = ordersByRelationship[ids].orEmpty()
        val relationshipContracts = contractsByRelationship[ids].orEmpty()
        val latestOrder = relationshipOrders.maxByOrNull { it.supplierDeskSortTime() }
        val latestContract = relationshipContracts.maxByOrNull {
            maxOf(it.updatedAtMillis, it.createdAtMillis)
        }
        val dashboardPartner = dashboardPartnersByStoreId[ids.first]
        val supplier = suppliersById[ids.second]

        val storeTitle = latestOrder
            ?.let { supplierDeskStoreTitle(it) }
            .orEmpty()
            .ifBlank {
                latestContract?.storeNameSnapshot
                    ?.visibleLocalizedString(stateValues.appLanguage, "")
                    .orEmpty()
            }
            .ifBlank {
                dashboardPartner?.storeNameSnapshot
                    ?.visibleLocalizedString(stateValues.appLanguage, "")
                    .orEmpty()
            }
            .ifBlank { dashboardPartner?.storePublicIdSnapshot.orEmpty() }
            .ifBlank { latestOrder?.storePublicIdSnapshot.orEmpty() }
            .ifBlank { latestContract?.storePublicIdSnapshot.orEmpty() }
            .ifBlank { ids.first.take(12) }

        val supplierTitle = supplier
            ?.visibleSupplierName(stateValues.appLanguage)
            .orEmpty()
            .ifBlank {
                latestContract?.supplierNameSnapshot
                    ?.visibleLocalizedString(stateValues.appLanguage, "")
                    .orEmpty()
            }
            .ifBlank { ids.second.take(8) }

        SupplierContractPartnerUiModel(
            key = ids.first + "|" + ids.second,
            storeId = ids.first,
            supplierId = ids.second,
            title = if (actorSide == SUPPLIER_CONTRACT_SIDE_STORE) supplierTitle else storeTitle,
            subtitle = if (actorSide == SUPPLIER_CONTRACT_SIDE_STORE) storeTitle else supplierTitle
        )
    }

    if (actorSide != SUPPLIER_CONTRACT_SIDE_STORE || cleanFixedStoreId.isBlank()) {
        return relationshipPartners.sortedBy { it.title.lowercase() }
    }

    // A store can start a partnership contract before the first order or price-book row exists.
    // Keep every active supplier available in the store-side editor while deduplicating any supplier
    // that already appeared through an order, contract, or saved offer.
    val supplierPartners = suppliers
        .filter { it.isActive && it.id.isNotBlank() }
        .mapNotNull { supplier ->
            val ids = supplierContractRelationshipKey(cleanFixedStoreId, supplier.id)
                ?: return@mapNotNull null
            SupplierContractPartnerUiModel(
                key = ids.first + "|" + ids.second,
                storeId = ids.first,
                supplierId = ids.second,
                title = supplier.visibleSupplierName(stateValues.appLanguage),
                subtitle = localizedStringResource(1479, "Supplier contracts")
            )
        }

    return (relationshipPartners + supplierPartners)
        .distinctBy { it.key }
        .sortedBy { it.title.lowercase() }
}

internal fun normalizedSupplierContractGoodsIds(values: Iterable<String>): Set<String> =
    values
        .map { it.trim().lowercase() }
        .filter { it.isNotBlank() }
        .toSet()

internal fun AppConfiguration.buildSupplierContractGoodsOptions(
    orders: List<SupplierOrderDataModel>,
    lines: List<SupplierOrderLineDataModel>,
    prices: List<SupplierGoodsPriceDataModel>,
    contracts: List<SupplierPartnershipContractDataModel> = emptyList()
): List<SupplierContractGoodsUiModel> {
    val ordersById = orders
        .filter { it.isActive }
        .associateBy { it.id }

    val orderOptionsByKey = lines
        .asSequence()
        .filter { line -> line.isActive }
        .flatMap { line ->
            val order = ordersById[line.orderId] ?: return@flatMap emptySequence()
            line.supplierContractGoodsFacets()
                .asSequence()
                .mapNotNull { facet ->
                    supplierGoodsOfferRelationshipKey(
                        storeId = order.storeId,
                        supplierId = order.supplierId,
                        goodsItemId = facet.goodsItemId
                    )?.let { key -> key to (line to facet) }
                }
        }
        .groupBy(keySelector = { it.first }, valueTransform = { it.second })
        .mapNotNull { (key, lineFacets) ->
            val sample = lineFacets.maxByOrNull { (line, _) ->
                ordersById[line.orderId]?.supplierDeskSortTime() ?: 0L
            } ?: return@mapNotNull null
            val sampleLine = sample.first
            val sampleFacet = sample.second
            val sampleOrder = ordersById[sampleLine.orderId] ?: return@mapNotNull null
            val latestPrice = when {
                sampleFacet.isSubstitute ->
                    sampleLine.supplierOfferedSupplyPrice ?: sampleLine.expectedSupplyPrice

                sampleLine.substituteGoodsItemId.orEmpty().isNotBlank() ->
                    sampleLine.expectedSupplyPrice

                else -> sampleLine.supplierOfferedSupplyPrice ?: sampleLine.expectedSupplyPrice
            }?.takeIf { it.hasPositiveSupplierDeskPrice() }
            val latestQuantity = if (sampleFacet.isSubstitute) {
                sampleLine.supplierAcceptedQuantity ?: sampleLine.requestedQuantity
            } else {
                sampleLine.requestedQuantity
            }
            SupplierContractGoodsUiModel(
                key = key,
                storeId = sampleOrder.storeId.trim().lowercase(),
                supplierId = sampleOrder.supplierId.trim().lowercase(),
                goodsItemId = sampleFacet.goodsItemId.trim().lowercase(),
                title = if (sampleFacet.isSubstitute) {
                    supplierDeskSubstituteTitle(sampleLine)
                } else {
                    supplierDeskLineTitle(sampleLine)
                }.ifBlank { sampleFacet.goodsItemId.take(12) },
                subtitle = sampleFacet.barcodeSnapshots
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .joinToString(" • ")
                    .ifBlank { sampleFacet.goodsItemId.take(12) },
                priceText = latestPrice.supplierDeskMoneyText(),
                quantityText = latestQuantity.quantityText(stateValues.appLanguage),
                nameSnapshot = sampleFacet.nameSnapshot,
                latestSupplyPrice = latestPrice,
                latestQuantity = latestQuantity
            )
        }
        .associateBy { it.key }

    val contractOptionsByKey = contracts
        .asSequence()
        .filter { it.isActive }
        .flatMap { contract ->
            val activeTermsByGoodsId = contract.priceTerms
                .asSequence()
                .filter { it.isActive }
                .filter { it.goodsItemId.isNotBlank() }
                .associateBy { it.goodsItemId.trim().lowercase() }
            normalizedSupplierContractGoodsIds(
                contract.goodsItemIds + activeTermsByGoodsId.keys
            ).asSequence().mapNotNull { goodsItemId ->
                val key = supplierGoodsOfferRelationshipKey(
                    storeId = contract.storeId,
                    supplierId = contract.supplierId,
                    goodsItemId = goodsItemId
                ) ?: return@mapNotNull null
                val term = activeTermsByGoodsId[goodsItemId]
                val quantity = term?.packageQuantity ?: term?.minOrderQuantity
                key to (contract to SupplierContractGoodsUiModel(
                    key = key,
                    storeId = contract.storeId.trim().lowercase(),
                    supplierId = contract.supplierId.trim().lowercase(),
                    goodsItemId = goodsItemId,
                    title = term?.goodsItemNameSnapshot
                        .orEmpty()
                        .visibleLocalizedString(stateValues.appLanguage, "")
                        .ifBlank { goodsItemId.take(12) },
                    subtitle = supplierVisibleContractTitle(contract)
                        .ifBlank { goodsItemId.take(12) },
                    priceText = term?.supplyPrice
                        ?.takeIf { it.hasPositiveSupplierDeskPrice() }
                        .supplierDeskMoneyText(),
                    quantityText = quantity?.quantityText(stateValues.appLanguage).orEmpty(),
                    nameSnapshot = term?.goodsItemNameSnapshot.orEmpty(),
                    latestSupplyPrice = term?.supplyPrice
                        ?.takeIf { it.hasPositiveSupplierDeskPrice() },
                    latestQuantity = quantity
                ))
            }
        }
        .groupBy(keySelector = { it.first }, valueTransform = { it.second })
        .mapValues { (_, contractOptions) ->
            contractOptions.maxWithOrNull(
                compareBy<Pair<SupplierPartnershipContractDataModel, SupplierContractGoodsUiModel>> {
                    maxOf(it.first.updatedAtMillis, it.first.createdAtMillis)
                }.thenBy { it.first.revision }
                    .thenBy { it.first.id }
            )?.second
        }
        .mapNotNull { (key, option) -> option?.let { key to it } }
        .toMap()

    val priceOptionsByKey = prices
        .normalizedSupplierGoodsPriceBook()
        .mapNotNull { price ->
            val key = price.supplierGoodsOfferRelationshipKey()
                ?: return@mapNotNull null
            val title = price.supplierGoodsName.orEmpty()
                .ifBlank { orderOptionsByKey[key]?.title.orEmpty() }
                .ifBlank { price.supplierBarcode.orEmpty() }
                .ifBlank { price.goodsItemId.take(12) }
            val nameSnapshot = orderOptionsByKey[key]?.nameSnapshot
                .orEmpty()
                .ifEmpty {
                    price.supplierGoodsName
                        ?.takeIf { it.isNotBlank() }
                        ?.let { listOf(LocalizedStringDataModel("main", it)) }
                        .orEmpty()
                }
            val quantity = price.packageQuantity
                ?: price.minOrderQuantity
                ?: orderOptionsByKey[key]?.latestQuantity
            key to SupplierContractGoodsUiModel(
                key = key,
                storeId = price.storeId.trim().lowercase(),
                supplierId = price.supplierId.trim().lowercase(),
                goodsItemId = price.goodsItemId.trim().lowercase(),
                title = title,
                subtitle = price.supplierBarcode.orEmpty()
                    .ifBlank { orderOptionsByKey[key]?.subtitle.orEmpty() }
                    .ifBlank { price.goodsItemId.take(12) },
                priceText = price.supplyPrice
                    .takeIf { it.hasPositiveSupplierDeskPrice() }
                    .supplierDeskMoneyText(),
                quantityText = quantity?.quantityText(stateValues.appLanguage).orEmpty(),
                nameSnapshot = nameSnapshot,
                latestSupplyPrice = price.supplyPrice.takeIf { it.hasPositiveSupplierDeskPrice() }
                    ?: orderOptionsByKey[key]?.latestSupplyPrice,
                latestQuantity = quantity
            )
        }
        .toMap()

    fun mergeSupplierContractGoodsOption(
        primary: SupplierContractGoodsUiModel?,
        fallback: SupplierContractGoodsUiModel?
    ): SupplierContractGoodsUiModel? = when {
        primary == null -> fallback
        fallback == null -> primary
        else -> primary.copy(
            title = primary.title.ifBlank { fallback.title },
            subtitle = primary.subtitle.ifBlank { fallback.subtitle },
            nameSnapshot = primary.nameSnapshot.ifEmpty { fallback.nameSnapshot },
            latestSupplyPrice = primary.latestSupplyPrice ?: fallback.latestSupplyPrice,
            latestQuantity = primary.latestQuantity ?: fallback.latestQuantity,
            priceText = primary.priceText.ifBlank { fallback.priceText },
            quantityText = primary.quantityText.ifBlank { fallback.quantityText }
        )
    }

    return (orderOptionsByKey.keys + priceOptionsByKey.keys + contractOptionsByKey.keys)
        .distinct()
        .mapNotNull { key ->
            val contractOption = contractOptionsByKey[key]
            val orderOption = mergeSupplierContractGoodsOption(orderOptionsByKey[key], contractOption)
            mergeSupplierContractGoodsOption(priceOptionsByKey[key], orderOption)
        }
        .sortedBy { it.title.lowercase() }
}

internal fun normalizedSupplierContractFilter(
    filterId: String,
    actorSide: String,
    searchQuery: String = ""
): String {
    val clean = filterId.trim().lowercase()
    return when (clean) {
        SUPPLIER_CONTRACT_FILTER_ALL -> SUPPLIER_CONTRACT_FILTER_ALL
        SUPPLIER_CONTRACT_FILTER_PENDING, "open" -> SUPPLIER_CONTRACT_FILTER_PENDING
        SUPPLIER_CONTRACT_FILTER_WAITING_ME -> SUPPLIER_CONTRACT_FILTER_WAITING_ME
        SUPPLIER_CONTRACT_FILTER_WAITING_OTHER -> SUPPLIER_CONTRACT_FILTER_WAITING_OTHER
        SUPPLIER_CONTRACT_FILTER_ACTIVE -> SUPPLIER_CONTRACT_FILTER_ACTIVE
        SUPPLIER_CONTRACT_FILTER_DECLINED -> SUPPLIER_CONTRACT_FILTER_DECLINED
        SUPPLIER_CONTRACT_STATUS_PENDING_STORE -> if (actorSide == SUPPLIER_CONTRACT_SIDE_STORE) {
            SUPPLIER_CONTRACT_FILTER_WAITING_ME
        } else {
            SUPPLIER_CONTRACT_FILTER_WAITING_OTHER
        }
        SUPPLIER_CONTRACT_STATUS_PENDING_SUPPLIER -> if (actorSide == SUPPLIER_CONTRACT_SIDE_SUPPLIER) {
            SUPPLIER_CONTRACT_FILTER_WAITING_ME
        } else {
            SUPPLIER_CONTRACT_FILTER_WAITING_OTHER
        }
        else -> if (searchQuery.isBlank()) SUPPLIER_CONTRACT_FILTER_ALL else SUPPLIER_CONTRACT_FILTER_ALL
    }
}

internal fun normalizedSupplierContractSort(sortId: String): String = when (sortId.trim().lowercase()) {
    SUPPLIER_CONTRACT_SORT_RECENT -> SUPPLIER_CONTRACT_SORT_RECENT
    SUPPLIER_CONTRACT_SORT_PARTNER -> SUPPLIER_CONTRACT_SORT_PARTNER
    SUPPLIER_CONTRACT_SORT_REVISION -> SUPPLIER_CONTRACT_SORT_REVISION
    else -> SUPPLIER_CONTRACT_SORT_ACTION
}

internal fun AppConfiguration.buildSupplierContractWorkspaceItems(
    contracts: List<SupplierPartnershipContractDataModel>,
    partners: List<SupplierContractPartnerUiModel>,
    actorSide: String,
    fixedStoreId: String? = null
): List<SupplierContractWorkspaceUiModel> {
    val partnerByKey = partners.associateBy { it.key }
    val cleanFixedStoreId = fixedStoreId.orEmpty().trim().lowercase()
    return contracts
        .asSequence()
        .filter { it.isActive }
        .filter { contract ->
            cleanFixedStoreId.isBlank() || contract.storeId.trim().lowercase() == cleanFixedStoreId
        }
        .mapNotNull { contract ->
            val relationship = supplierContractRelationshipKey(contract.storeId, contract.supplierId)
                ?: return@mapNotNull null
            val partnerKey = relationship.first + "|" + relationship.second
            val partner = partnerByKey[partnerKey]
            val storeTitle = contract.storeNameSnapshot
                .visibleLocalizedString(stateValues.appLanguage, "")
                .ifBlank { contract.storePublicIdSnapshot }
                .ifBlank { contract.storeId.take(12) }
            val supplierTitle = contract.supplierNameSnapshot
                .visibleLocalizedString(stateValues.appLanguage, "")
                .ifBlank {
                    stateValues.suppliers.orEmpty()
                        .firstOrNull { it.id.trim().equals(contract.supplierId.trim(), ignoreCase = true) }
                        ?.visibleSupplierName(stateValues.appLanguage)
                        .orEmpty()
                }
                .ifBlank { contract.supplierId.take(12) }
            val partnerTitle = partner?.title.orEmpty().ifBlank {
                if (actorSide == SUPPLIER_CONTRACT_SIDE_STORE) supplierTitle else storeTitle
            }
            val partnerSubtitle = partner?.subtitle.orEmpty().ifBlank {
                if (actorSide == SUPPLIER_CONTRACT_SIDE_STORE) storeTitle else supplierTitle
            }
            val selectedGoodsIds = normalizedSupplierContractGoodsIds(
                contract.goodsItemIds + contract.priceTerms.filter { it.isActive }.map { it.goodsItemId }
            )
            val searchKey = buildString {
                append(contract.id).append(' ')
                append(contract.storeId).append(' ')
                append(contract.supplierId).append(' ')
                append(contract.storePublicIdSnapshot).append(' ')
                append(contract.status).append(' ')
                append(contract.scopeType).append(' ')
                append(partnerTitle).append(' ')
                append(partnerSubtitle).append(' ')
                append(supplierVisibleContractTitle(contract)).append(' ')
                append(supplierVisibleContractSummary(contract)).append(' ')
                contract.goodsItemIds.forEach { append(it).append(' ') }
                contract.conditions.forEach { append(it).append(' ') }
                contract.customTerms.forEach { append(it.value).append(' ') }
                contract.deliverySchedule.forEach { append(it.value).append(' ') }
                contract.paymentSchedule.forEach { append(it.value).append(' ') }
                contract.priceTerms.forEach { term ->
                    append(term.goodsItemId).append(' ')
                    append(term.goodsItemNameSnapshot.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
                    append(term.supplyPrice?.price.orEmpty()).append(' ')
                    append(term.suggestedSalePrice?.price.orEmpty()).append(' ')
                    term.scheduleText.forEach { append(it.value).append(' ') }
                    term.note.forEach { append(it.value).append(' ') }
                }
            }.lowercase()
            SupplierContractWorkspaceUiModel(
                contract = contract,
                partnerKey = partnerKey,
                partnerTitle = partnerTitle,
                partnerSubtitle = partnerSubtitle,
                searchKey = searchKey,
                latestActivityMillis = maxOf(contract.updatedAtMillis, contract.createdAtMillis),
                selectedGoodsCount = selectedGoodsIds.size,
                activePriceTermCount = contract.priceTerms.count { it.isActive },
                waitsForMe = contract.requiresAcceptanceFrom(actorSide),
                waitsForOtherSide = contract.waitsForOtherContractSide(actorSide),
                blocksSupply = contract.blocksSupplierSupplyForGoods(
                    contract.goodsItemIds.ifEmpty { selectedGoodsIds.toList() }
                )
            )
        }
        .toList()
}

internal fun SupplierContractWorkspaceUiModel.matchesSupplierContractFilter(filterId: String): Boolean = when (filterId) {
    SUPPLIER_CONTRACT_FILTER_PENDING -> waitsForMe || waitsForOtherSide
    SUPPLIER_CONTRACT_FILTER_WAITING_ME -> waitsForMe
    SUPPLIER_CONTRACT_FILTER_WAITING_OTHER -> waitsForOtherSide
    SUPPLIER_CONTRACT_FILTER_ACTIVE -> isActiveAgreement
    SUPPLIER_CONTRACT_FILTER_DECLINED -> isDeclined
    else -> true
}

internal fun List<SupplierContractWorkspaceUiModel>.countForSupplierContractFilter(filterId: String): Int =
    count { it.matchesSupplierContractFilter(filterId) }

internal fun List<SupplierContractWorkspaceUiModel>.sortedForSupplierContracts(sortId: String): List<SupplierContractWorkspaceUiModel> {
    val comparator = when (normalizedSupplierContractSort(sortId)) {
        SUPPLIER_CONTRACT_SORT_RECENT ->
            compareByDescending<SupplierContractWorkspaceUiModel> { it.latestActivityMillis }
                .thenBy { it.partnerTitle.lowercase() }

        SUPPLIER_CONTRACT_SORT_PARTNER ->
            compareBy<SupplierContractWorkspaceUiModel> { it.partnerTitle.lowercase() }
                .thenByDescending { it.latestActivityMillis }

        SUPPLIER_CONTRACT_SORT_REVISION ->
            compareByDescending<SupplierContractWorkspaceUiModel> { it.contract.revision }
                .thenByDescending { it.latestActivityMillis }

        else ->
            compareByDescending<SupplierContractWorkspaceUiModel> { if (it.waitsForMe) 1 else 0 }
                .thenByDescending { if (it.waitsForOtherSide) 1 else 0 }
                .thenByDescending { if (it.isActiveAgreement) 1 else 0 }
                .thenByDescending { it.latestActivityMillis }
                .thenBy { it.partnerTitle.lowercase() }
    }
    return sortedWith(comparator)
}

private fun QuantityDataModel?.validSupplierContractQuantityOrNull(): QuantityDataModel? =
    this?.takeIf { it.total.isFinite() && it.total > 0.0 && it.pricedAmount.isFinite() && it.pricedAmount > 0.0 }

internal fun buildSupplierContractDraftPriceTerms(
    existingContract: SupplierPartnershipContractDataModel?,
    selectableGoods: List<SupplierContractGoodsUiModel>,
    selectedGoodsIds: Set<String>,
    deliverySchedule: List<LocalizedStringDataModel>,
    summary: List<LocalizedStringDataModel>
): List<SupplierContractPriceTermDataModel> {
    val normalizedSelected = normalizedSupplierContractGoodsIds(selectedGoodsIds)
    val existingByGoodsId = existingContract?.priceTerms.orEmpty()
        .filter { it.isActive && it.goodsItemId.isNotBlank() }
        .associateBy { it.goodsItemId.trim().lowercase() }
    val goodsById = selectableGoods.associateBy { it.goodsItemId.trim().lowercase() }

    return normalizedSelected.mapNotNull { goodsItemId ->
        val existing = existingByGoodsId[goodsItemId]
        val goods = goodsById[goodsItemId]
        if (existing == null && goods == null) return@mapNotNull null
        SupplierContractPriceTermDataModel(
            id = existing?.id.orEmpty(),
            goodsItemId = goodsItemId,
            goodsItemNameSnapshot = existing?.goodsItemNameSnapshot.orEmpty()
                .ifEmpty { goods?.nameSnapshot.orEmpty() }
                .ifEmpty {
                    goods?.title?.takeIf { it.isNotBlank() }
                        ?.let { listOf(LocalizedStringDataModel("main", it)) }
                        .orEmpty()
                },
            supplyPrice = existing?.supplyPrice?.takeIf { it.hasPositiveSupplierDeskPrice() }
                ?: goods?.latestSupplyPrice?.takeIf { it.hasPositiveSupplierDeskPrice() },
            suggestedSalePrice = existing?.suggestedSalePrice?.takeIf { it.hasPositiveSupplierDeskPrice() },
            minOrderQuantity = existing?.minOrderQuantity.validSupplierContractQuantityOrNull()
                ?: goods?.latestQuantity.validSupplierContractQuantityOrNull(),
            packageQuantity = existing?.packageQuantity.validSupplierContractQuantityOrNull(),
            scheduleText = existing?.scheduleText.orEmpty().ifEmpty { deliverySchedule },
            note = existing?.note.orEmpty().ifEmpty { summary },
            isActive = true
        )
    }.sortedBy { it.goodsItemId }
}

internal fun AppConfiguration.supplierContractActionHint(
    contract: SupplierPartnershipContractDataModel,
    actorSide: String
): String = when {
    contract.status == SUPPLIER_CONTRACT_STATUS_ACTIVE -> localizedStringResource(1525, "Terms accepted by both sides")
    contract.requiresAcceptanceFrom(actorSide) -> localizedStringResource(1526, "Waiting for your acceptance")
    contract.waitsForOtherContractSide(actorSide) -> localizedStringResource(1527, "Waiting for the other side")
    else -> supplierContractStatusTitle(contract.status)
}

