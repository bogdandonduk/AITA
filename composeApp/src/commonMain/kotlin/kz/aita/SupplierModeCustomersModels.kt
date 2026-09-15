package kz.aita

internal const val SUPPLIER_CUSTOMERS_FILTER_ALL = "all"
internal const val SUPPLIER_CUSTOMERS_FILTER_ACTIVE = "active"
internal const val SUPPLIER_CUSTOMERS_FILTER_ATTENTION = "attention"
internal const val SUPPLIER_CUSTOMERS_FILTER_DELIVERY = "delivery"
internal const val SUPPLIER_CUSTOMERS_FILTER_CONTRACTS = "contracts"
internal const val SUPPLIER_CUSTOMERS_FILTER_PRICE_GAPS = "price_gaps"
internal const val SUPPLIER_CUSTOMERS_FILTER_OFFERS = "offers"

internal const val SUPPLIER_CUSTOMERS_SORT_ACTION = "action"
internal const val SUPPLIER_CUSTOMERS_SORT_RECENT = "recent"
internal const val SUPPLIER_CUSTOMERS_SORT_NAME = "name"
internal const val SUPPLIER_CUSTOMERS_SORT_ORDERS = "orders"

/**
 * A single commercial relationship can be represented by several independently loaded resources.
 * The grouping layer connects them only through immutable store identifiers. Historic snapshots use
 * title + address solely when both IDs are absent; title alone is never safe enough.
 */
internal data class SupplierPartnerRelationSource(
    val sourceKey: String,
    val storeId: String = "",
    val storePublicId: String = "",
    val storeTitle: String = "",
    val storeAddress: String = "",
    val order: SupplierOrderDataModel? = null,
    val price: SupplierGoodsPriceDataModel? = null,
    val contract: SupplierPartnershipContractDataModel? = null,
    val dashboard: SupplierDashboardPartnerDataModel? = null
)

internal data class SupplierPartnerRelationGroup(
    val groupKey: String,
    val sources: List<SupplierPartnerRelationSource>
)

private fun String.supplierPartnerIdentityToken(): String =
    trim().lowercase().replace(Regex("\\s+"), " ")

private fun SupplierPartnerRelationSource.relationshipTokens(): List<String> = buildList {
    storeId.supplierPartnerIdentityToken()
        .takeIf { it.isNotBlank() }
        ?.let { add("store-id:$it") }
    storePublicId.supplierPartnerIdentityToken()
        .takeIf { it.isNotBlank() }
        ?.let { add("store-public:$it") }

    if (isEmpty()) {
        val title = storeTitle.supplierPartnerIdentityToken()
        val address = storeAddress.supplierPartnerIdentityToken()
        if (title.isNotBlank() && address.isNotBlank()) {
            add("store-snapshot:$title|$address")
        }
    }

    if (isEmpty()) {
        add("source:${sourceKey.supplierPartnerIdentityToken()}")
    }
}

internal fun groupSupplierPartnerRelationSources(
    sources: List<SupplierPartnerRelationSource>
): List<SupplierPartnerRelationGroup> {
    if (sources.isEmpty()) return emptyList()

    // Malformed historical rows can be missing every Store identifier and even their source ID.
    // Give each such row a stable identity for this grouping pass instead of deriving identity from
    // data-class hash codes, which can collide for two identical malformed rows.
    val safeSources = sources.mapIndexed { index, source ->
        if (source.sourceKey.isBlank()) {
            source.copy(sourceKey = "anonymous-source-$index")
        } else {
            source
        }
    }

    val parent = IntArray(safeSources.size) { it }
    val rank = IntArray(safeSources.size)

    fun find(index: Int): Int {
        var current = index
        while (parent[current] != current) {
            parent[current] = parent[parent[current]]
            current = parent[current]
        }
        return current
    }

    fun union(first: Int, second: Int) {
        var firstRoot = find(first)
        var secondRoot = find(second)
        if (firstRoot == secondRoot) return
        if (rank[firstRoot] < rank[secondRoot]) {
            val swap = firstRoot
            firstRoot = secondRoot
            secondRoot = swap
        }
        parent[secondRoot] = firstRoot
        if (rank[firstRoot] == rank[secondRoot]) rank[firstRoot] += 1
    }

    val tokenOwner = mutableMapOf<String, Int>()
    safeSources.forEachIndexed { index, source ->
        source.relationshipTokens().forEach { token ->
            val existing = tokenOwner[token]
            if (existing == null) tokenOwner[token] = index else union(existing, index)
        }
    }

    return safeSources.indices
        .groupBy { find(it) }
        .values
        .map { indices ->
            val groupedSources = indices.map { safeSources[it] }
            val tokens = groupedSources.flatMap { it.relationshipTokens() }
            val preferredKey = tokens.firstOrNull { it.startsWith("store-id:") }
                ?: tokens.firstOrNull { it.startsWith("store-public:") }
                ?: tokens.first()
            SupplierPartnerRelationGroup(
                groupKey = preferredKey,
                sources = groupedSources
            )
        }
        .sortedBy { it.groupKey }
}

internal data class SupplierPartnerUiModel(
    val partnerKey: String,
    val storeId: String,
    val publicId: String,
    val title: String,
    val address: String,
    val supplierIds: List<String>,
    val supplierTitles: List<String>,
    val orders: List<SupplierOrderDataModel>,
    val lines: List<SupplierOrderLineDataModel>,
    val prices: List<SupplierGoodsPriceDataModel>,
    val contracts: List<SupplierPartnershipContractDataModel>,
    val orderCount: Int,
    val openOrderCount: Int,
    val attentionOrderCount: Int,
    val readyToPackOrderCount: Int,
    val packedOrderCount: Int,
    val inDeliveryOrderCount: Int,
    val partiallyDeliveredOrderCount: Int,
    val overdueOrderCount: Int,
    val deliveredOrderCount: Int,
    val issueOrderCount: Int,
    val activeContractCount: Int,
    val pendingSupplierContractCount: Int,
    val pendingStoreContractCount: Int,
    val savedOfferCount: Int,
    val validOfferCount: Int,
    val priceGapCount: Int,
    val connectedProductCount: Int,
    val latestStatus: SupplierOrderStatusDataModel?,
    val latestActivityMillis: Long,
    val actionId: String,
    val searchKey: String,
    val brief: String,
    val orderDetailsLoaded: Boolean = true,
    val priceDetailsLoaded: Boolean = true,
    val contractDetailsLoaded: Boolean = true,
    val offerReadinessKnown: Boolean = true,
    val agreementReadinessKnown: Boolean = true
) {
    val hasActiveWork: Boolean
        get() = openOrderCount > 0

    val hasAttention: Boolean
        get() = attentionOrderCount > 0 ||
                issueOrderCount > 0 ||
                pendingSupplierContractCount > 0 ||
                priceGapCount > 0 ||
                overdueOrderCount > 0

    val hasActiveDelivery: Boolean
        get() = readyToPackOrderCount > 0 ||
                packedOrderCount > 0 ||
                inDeliveryOrderCount > 0 ||
                partiallyDeliveredOrderCount > 0

    val hasContractRelationship: Boolean
        get() = activeContractCount > 0 ||
                pendingSupplierContractCount > 0 ||
                pendingStoreContractCount > 0

    val hasPriceGap: Boolean
        get() = priceGapCount > 0

    val hasOfferRelationship: Boolean
        get() = savedOfferCount > 0

    val navigationSearchQuery: String
        get() = storeId.ifBlank { publicId }.ifBlank { title }
}

internal enum class SupplierPartnerPortfolioNextStep {
    REVIEW_ATTENTION,
    REFRESH_RELATIONSHIP_DATA,
    BUILD_OFFERS,
    COMPLETE_AGREEMENTS,
    OPEN_INSIGHTS,
}

internal data class SupplierPartnerPortfolioHealthUiModel(
    val partnerCount: Int,
    val commerciallyReadyCount: Int,
    val attentionCount: Int,
    val missingActiveAgreementCount: Int,
    val missingUsableOfferCount: Int,
    val readinessUnknownCount: Int,
    val coveragePercent: Int,
    val nextStep: SupplierPartnerPortfolioNextStep,
) {
    val coverageIsFinal: Boolean
        get() = readinessUnknownCount == 0
}

/**
 * A single relationship-level recommendation keeps the Partner Store detail operational rather than
 * becoming a passive dossier. The ordering deliberately protects live commercial work first, then
 * resolves relationship readiness, and only then falls back to analytics.
 */
internal enum class SupplierPartnerNextAction {
    RESOLVE_ORDER_ATTENTION,
    REVIEW_AGREEMENT_REQUEST,
    PACK_READY_ORDERS,
    DISPATCH_PACKED_ORDERS,
    FOLLOW_ACTIVE_DELIVERY,
    COMPLETE_OFFER_PRICES,
    REFRESH_RELATIONSHIP_DATA,
    BUILD_USABLE_OFFER,
    COMPLETE_ACTIVE_AGREEMENT,
    REVIEW_OPEN_ORDERS,
    OPEN_INSIGHTS,
}

internal data class SupplierPartnerNextActionUiModel(
    val action: SupplierPartnerNextAction,
    val affectedCount: Int,
    val urgent: Boolean,
)

internal fun buildSupplierPartnerNextAction(
    partner: SupplierPartnerUiModel,
): SupplierPartnerNextActionUiModel = when {
    partner.issueOrderCount > 0 || partner.overdueOrderCount > 0 || partner.attentionOrderCount > 0 ->
        SupplierPartnerNextActionUiModel(
            action = SupplierPartnerNextAction.RESOLVE_ORDER_ATTENTION,
            affectedCount = maxOf(
                partner.issueOrderCount,
                partner.overdueOrderCount,
                partner.attentionOrderCount,
            ),
            urgent = true,
        )

    partner.pendingSupplierContractCount > 0 ->
        SupplierPartnerNextActionUiModel(
            action = SupplierPartnerNextAction.REVIEW_AGREEMENT_REQUEST,
            affectedCount = partner.pendingSupplierContractCount,
            urgent = true,
        )

    partner.readyToPackOrderCount > 0 ->
        SupplierPartnerNextActionUiModel(
            action = SupplierPartnerNextAction.PACK_READY_ORDERS,
            affectedCount = partner.readyToPackOrderCount,
            urgent = false,
        )

    partner.packedOrderCount > 0 ->
        SupplierPartnerNextActionUiModel(
            action = SupplierPartnerNextAction.DISPATCH_PACKED_ORDERS,
            affectedCount = partner.packedOrderCount,
            urgent = false,
        )

    partner.inDeliveryOrderCount > 0 || partner.partiallyDeliveredOrderCount > 0 ->
        SupplierPartnerNextActionUiModel(
            action = SupplierPartnerNextAction.FOLLOW_ACTIVE_DELIVERY,
            affectedCount = partner.inDeliveryOrderCount + partner.partiallyDeliveredOrderCount,
            urgent = false,
        )

    partner.priceGapCount > 0 ->
        SupplierPartnerNextActionUiModel(
            action = SupplierPartnerNextAction.COMPLETE_OFFER_PRICES,
            affectedCount = partner.priceGapCount,
            urgent = false,
        )

    !partner.offerReadinessKnown || !partner.agreementReadinessKnown ->
        SupplierPartnerNextActionUiModel(
            action = SupplierPartnerNextAction.REFRESH_RELATIONSHIP_DATA,
            affectedCount = 0,
            urgent = false,
        )

    partner.validOfferCount <= 0 ->
        SupplierPartnerNextActionUiModel(
            action = SupplierPartnerNextAction.BUILD_USABLE_OFFER,
            affectedCount = 0,
            urgent = false,
        )

    partner.activeContractCount <= 0 ->
        SupplierPartnerNextActionUiModel(
            action = SupplierPartnerNextAction.COMPLETE_ACTIVE_AGREEMENT,
            affectedCount = 0,
            urgent = false,
        )

    partner.openOrderCount > 0 ->
        SupplierPartnerNextActionUiModel(
            action = SupplierPartnerNextAction.REVIEW_OPEN_ORDERS,
            affectedCount = partner.openOrderCount,
            urgent = false,
        )

    else ->
        SupplierPartnerNextActionUiModel(
            action = SupplierPartnerNextAction.OPEN_INSIGHTS,
            affectedCount = 0,
            urgent = false,
        )
}

/**
 * Commercial readiness is intentionally derived only from authoritative relationship evidence
 * already loaded by the Supplier workspace. An active agreement and at least one currently usable
 * offer are both required. A zero count is not treated as "missing" until either the detailed list
 * or the matching dashboard partner snapshot has arrived; otherwise a partial initial load could
 * tell the Supplier to recreate an agreement or offer that already exists.
 *
 * Operational attention remains independent and keeps the highest action priority, so a known urgent
 * order still surfaces even while commercial-readiness details are being refreshed.
 */
internal fun buildSupplierPartnerPortfolioHealth(
    partners: List<SupplierPartnerUiModel>,
): SupplierPartnerPortfolioHealthUiModel {
    val partnerCount = partners.size
    val commerciallyReadyCount = partners.count { partner ->
        partner.activeContractCount > 0 && partner.validOfferCount > 0
    }
    val attentionCount = partners.count { it.hasAttention }
    val missingActiveAgreementCount = partners.count { partner ->
        partner.activeContractCount <= 0 && partner.agreementReadinessKnown
    }
    val missingUsableOfferCount = partners.count { partner ->
        partner.validOfferCount <= 0 && partner.offerReadinessKnown
    }
    val readinessUnknownCount = partners.count { partner ->
        (partner.activeContractCount <= 0 && !partner.agreementReadinessKnown) ||
                (partner.validOfferCount <= 0 && !partner.offerReadinessKnown)
    }
    val coveragePercent = if (partnerCount == 0) {
        0
    } else {
        ((commerciallyReadyCount * 100) + (partnerCount / 2)) / partnerCount
    }.coerceIn(0, 100)

    val nextStep = when {
        attentionCount > 0 -> SupplierPartnerPortfolioNextStep.REVIEW_ATTENTION
        readinessUnknownCount > 0 -> SupplierPartnerPortfolioNextStep.REFRESH_RELATIONSHIP_DATA
        missingUsableOfferCount > 0 -> SupplierPartnerPortfolioNextStep.BUILD_OFFERS
        missingActiveAgreementCount > 0 -> SupplierPartnerPortfolioNextStep.COMPLETE_AGREEMENTS
        else -> SupplierPartnerPortfolioNextStep.OPEN_INSIGHTS
    }

    return SupplierPartnerPortfolioHealthUiModel(
        partnerCount = partnerCount,
        commerciallyReadyCount = commerciallyReadyCount,
        attentionCount = attentionCount,
        missingActiveAgreementCount = missingActiveAgreementCount,
        missingUsableOfferCount = missingUsableOfferCount,
        readinessUnknownCount = readinessUnknownCount,
        coveragePercent = coveragePercent,
        nextStep = nextStep,
    )
}

private fun SupplierGoodsPriceDataModel.supplierPartnerActivityMillis(): Long =
    maxOf(lastUsedAtMillis ?: 0L, updatedAtMillis, createdAtMillis)

private fun SupplierPartnershipContractDataModel.supplierPartnerActivityMillis(): Long =
    maxOf(updatedAtMillis, createdAtMillis)

private fun SupplierOrderDataModel.supplierPartnerActivityMillis(): Long = supplierDeskSortTime()

private fun SupplierPartnerRelationSource.supplierPartnerActivityMillis(): Long = maxOf(
    order?.supplierPartnerActivityMillis() ?: 0L,
    price?.supplierPartnerActivityMillis() ?: 0L,
    contract?.supplierPartnerActivityMillis() ?: 0L,
    dashboard?.latestActivityMillis ?: 0L
)

private fun SupplierPartnerRelationGroup.preferredStoreId(): String = sources
    .asSequence()
    .map { it.storeId.trim() }
    .firstOrNull { it.isNotBlank() }
    .orEmpty()

private fun SupplierPartnerRelationGroup.preferredPublicId(): String = sources
    .sortedByDescending { it.supplierPartnerActivityMillis() }
    .asSequence()
    .map { it.storePublicId.trim() }
    .firstOrNull { it.isNotBlank() }
    .orEmpty()

private fun SupplierPartnerRelationGroup.preferredTitle(): String = sources
    .sortedByDescending { it.supplierPartnerActivityMillis() }
    .asSequence()
    .map { it.storeTitle.trim() }
    .firstOrNull { it.isNotBlank() }
    .orEmpty()

private fun SupplierPartnerRelationGroup.preferredAddress(): String = sources
    .sortedByDescending { it.supplierPartnerActivityMillis() }
    .asSequence()
    .map { it.storeAddress.trim() }
    .firstOrNull { it.isNotBlank() }
    .orEmpty()

private fun SupplierOrderDataModel.isOverdueSupplierPartnerOrder(nowMillis: Long): Boolean {
    val dueAt = confirmedDeliveryTimeMillis ?: desiredDeliveryTimeMillis ?: return false
    return !status.isClosedForSupplierDesk() && dueAt < nowMillis
}

/**
 * Detailed payloads become authoritative as soon as they have loaded. Until then, the dashboard can
 * fill gaps without suppressing partial local information. This prevents an older dashboard snapshot
 * from permanently winning through `maxOf(...)` after an order, offer, or contract was removed.
 */
internal fun resolvedSupplierPartnerCount(
    localCount: Int,
    dashboardCount: Int,
    detailedPayloadLoaded: Boolean
): Int = if (detailedPayloadLoaded) {
    localCount.coerceAtLeast(0)
} else {
    maxOf(localCount, dashboardCount).coerceAtLeast(0)
}

internal fun AppConfiguration.buildSupplierPartnerItems(
    orders: List<SupplierOrderDataModel>?,
    lines: List<SupplierOrderLineDataModel>?,
    supplierPrices: List<SupplierGoodsPriceDataModel>?,
    contracts: List<SupplierPartnershipContractDataModel>?,
    dashboard: SupplierModeDashboardDataModel?,
    nowMillis: Long = getCurrentTimeMillis()
): List<SupplierPartnerUiModel> {
    val hasOrderDetails = orders != null
    val hasLineDetails = lines != null
    val hasPriceDetails = supplierPrices != null
    val hasContractDetails = contracts != null
    val hasCompleteOrderDetails = hasOrderDetails && hasLineDetails
    val hasCompletePriceGapDetails = hasCompleteOrderDetails && hasPriceDetails
    val hasCompleteProductDetails = hasCompleteOrderDetails && hasPriceDetails && hasContractDetails
    val hasCompleteRelationshipDetails = hasOrderDetails && hasPriceDetails && hasContractDetails

    val activeOrders = orders.orEmpty().filter {
        it.isActive && it.status != SupplierOrderStatusDataModel.Draft
    }
    val activeOrderIds = activeOrders.map { it.id }.toSet()
    val activeLines = lines.orEmpty().filter { it.isActive && it.orderId in activeOrderIds }
    val activePrices = supplierPrices.orEmpty().normalizedSupplierGoodsPriceBook()
    val activeContracts = contracts.orEmpty().filter { it.isActive }

    val sources = buildList {
        activeOrders.forEach { order ->
            add(
                SupplierPartnerRelationSource(
                    sourceKey = "order:${order.id}",
                    storeId = order.storeId,
                    storePublicId = order.storePublicIdSnapshot,
                    storeTitle = supplierDeskStoreTitle(order),
                    storeAddress = order.storeAddressTextSnapshot,
                    order = order
                )
            )
        }
        activePrices.forEach { price ->
            add(
                SupplierPartnerRelationSource(
                    sourceKey = "price:${price.id.ifBlank { price.supplierPriceBookIdentity() }}",
                    storeId = price.storeId,
                    price = price
                )
            )
        }
        activeContracts.forEach { contract ->
            add(
                SupplierPartnerRelationSource(
                    sourceKey = "contract:${contract.id.ifBlank { "${contract.storeId}|${contract.supplierId}|${contract.revision}" }}",
                    storeId = contract.storeId,
                    storePublicId = contract.storePublicIdSnapshot,
                    storeTitle = contract.storeNameSnapshot.visibleLocalizedString(
                        stateValues.appLanguage,
                        ""
                    ),
                    contract = contract
                )
            )
        }
        dashboard?.partnerHighlights.orEmpty().forEachIndexed { index, partner ->
            add(
                SupplierPartnerRelationSource(
                    sourceKey = "dashboard:${partner.storeId}:${partner.storePublicIdSnapshot}:$index",
                    storeId = partner.storeId,
                    storePublicId = partner.storePublicIdSnapshot,
                    storeTitle = partner.storeNameSnapshot.visibleLocalizedString(
                        stateValues.appLanguage,
                        ""
                    ),
                    storeAddress = partner.storeAddressTextSnapshot,
                    dashboard = partner
                )
            )
        }
    }

    val linesByOrder = activeLines.groupBy { it.orderId }
    val suppliersById = stateValues.suppliers.orEmpty().associateBy { it.id.trim().lowercase() }
    val knownStores = stateValues.stores.orEmpty()

    return groupSupplierPartnerRelationSources(sources)
        .filter { group ->
            !hasCompleteRelationshipDetails || group.sources.any { source ->
                source.order != null || source.price != null || source.contract != null
            }
        }
        .map { group ->
            val groupOrders = group.sources.mapNotNull { it.order }
                .distinctBy { it.id }
                .sortedByDescending { it.supplierDeskSortTime() }
            val groupOrderIds = groupOrders.map { it.id }.toSet()
            val groupLines = activeLines
                .filter { it.orderId in groupOrderIds }
                .distinctBy {
                    it.id.ifBlank {
                        "${it.orderId}|${it.goodsItemId}|${it.goodsItemBarcodeSnapshots.joinToString()}"
                    }
                }
            val groupPrices = group.sources.mapNotNull { it.price }
                .normalizedSupplierGoodsPriceBook()
            val groupContracts = group.sources.mapNotNull { it.contract }
                .distinctBy {
                    it.id.ifBlank { "${it.storeId}|${it.supplierId}|${it.scopeType}|${it.revision}" }
                }
                .sortedByDescending { maxOf(it.updatedAtMillis, it.createdAtMillis) }
            val groupDashboard = group.sources.mapNotNull { it.dashboard }
                .maxByOrNull { it.latestActivityMillis }

            val storeId = group.preferredStoreId()
            val sourcePublicId = group.preferredPublicId()
            val knownStore = knownStores.findStoreOrBranch(storeId.ifBlank { sourcePublicId })
            val publicId = knownStore?.publicId.orEmpty().trim()
                .ifBlank { sourcePublicId }
            val title = knownStore?.name
                ?.visibleLocalizedString(stateValues.appLanguage, "")
                .orEmpty()
                .trim()
                .ifBlank { group.preferredTitle() }
                .ifBlank { publicId }
                .ifBlank { storeId.take(12) }
                .ifBlank { localizedStringResource(2392, "Store partner") }
            val address = knownStore
                ?.displayAddress(stateValues.appLanguage)
                .orEmpty()
                .trim()
                .ifBlank { group.preferredAddress() }

            val supplierIds = (
                    groupOrders.map { it.supplierId } +
                            groupPrices.map { it.supplierId } +
                            groupContracts.map { it.supplierId }
                    )
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinctBy { it.lowercase() }
            val supplierTitles = supplierIds.map { supplierId ->
                suppliersById[supplierId.lowercase()]
                    ?.visibleSupplierName(stateValues.appLanguage)
                    .orEmpty()
                    .ifBlank {
                        groupContracts
                            .firstOrNull { it.supplierId.equals(supplierId, ignoreCase = true) }
                            ?.supplierNameSnapshot
                            ?.visibleLocalizedString(stateValues.appLanguage, "")
                            .orEmpty()
                    }
                    .ifBlank { stateValues.stringNoName }
            }.distinct()

            val orderBundles = groupOrders.associateWith { order ->
                SupplierOrderWithLinesDataModel(
                    order = order,
                    lines = linesByOrder[order.id].orEmpty()
                )
            }
            val localOpenOrderCount = groupOrders.count { !it.status.isClosedForSupplierDesk() }
            val localAttentionOrderCount = orderBundles.values.count {
                it.needsSupplierActionForSupplierDesk()
            }
            val localReadyToPackOrderCount = orderBundles.values.count {
                it.isSupplierReadyToPackForSupplierDesk()
            }
            val localPackedOrderCount = groupOrders.count {
                it.status == SupplierOrderStatusDataModel.Packed
            }
            val localInDeliveryOrderCount = groupOrders.count {
                it.status == SupplierOrderStatusDataModel.InDelivery
            }
            val localPartiallyDeliveredOrderCount = groupOrders.count {
                it.status == SupplierOrderStatusDataModel.PartiallyDelivered
            }
            val localDeliveredOrderCount = groupOrders.count {
                it.status == SupplierOrderStatusDataModel.Delivered
            }
            val localIssueOrderCount = groupOrders.count {
                it.status == SupplierOrderStatusDataModel.IssueReported
            }
            val localOverdueOrderCount = groupOrders.count {
                it.isOverdueSupplierPartnerOrder(nowMillis)
            }
            val localActiveContractCount = groupContracts.count {
                it.status == SUPPLIER_CONTRACT_STATUS_ACTIVE
            }
            val localPendingSupplierContractCount = groupContracts.count {
                it.status == SUPPLIER_CONTRACT_STATUS_PENDING_SUPPLIER
            }
            val localPendingStoreContractCount = groupContracts.count {
                it.status == SUPPLIER_CONTRACT_STATUS_PENDING_STORE
            }
            val localValidOfferCount = groupPrices.count {
                it.supplyPrice.hasPositiveSupplierDeskPrice()
            }
            val openOrdersById = groupOrders
                .filterNot { it.status.isClosedForSupplierDesk() }
                .associateBy { it.id }
            val requiredOfferKeys = groupLines
                .asSequence()
                .flatMap { line ->
                    val order = openOrdersById[line.orderId]
                        ?: return@flatMap emptySequence()
                    sequenceOf(line.goodsItemId, line.substituteGoodsItemId.orEmpty())
                        .mapNotNull { goodsItemId ->
                            supplierGoodsOfferRelationshipKey(
                                storeId = order.storeId.ifBlank { storeId },
                                supplierId = order.supplierId,
                                goodsItemId = goodsItemId
                            )
                        }
                }
                .toSet()
            val localPriceGapCount = supplierGoodsOfferPriceGapCount(
                requiredRelationshipKeys = requiredOfferKeys,
                prices = groupPrices
            )

            val connectedProductIds = buildList {
                groupLines.forEach { line ->
                    line.goodsItemId.takeIf { it.isNotBlank() }?.let { add(it) }
                    line.substituteGoodsItemId?.takeIf { it.isNotBlank() }?.let { add(it) }
                }
                groupPrices.forEach { price ->
                    price.goodsItemId.takeIf { it.isNotBlank() }?.let { add(it) }
                }
                groupContracts.forEach { contract ->
                    addAll(contract.goodsItemIds.filter { it.isNotBlank() })
                    addAll(contract.priceTerms.map { it.goodsItemId }.filter { it.isNotBlank() })
                }
            }.distinctBy { it.trim().lowercase() }

            val orderCount = resolvedSupplierPartnerCount(
                localCount = groupOrders.size,
                dashboardCount = groupDashboard?.orderCount ?: 0,
                detailedPayloadLoaded = hasOrderDetails
            )
            val openOrderCount = resolvedSupplierPartnerCount(
                localCount = localOpenOrderCount,
                dashboardCount = groupDashboard?.openOrderCount ?: 0,
                detailedPayloadLoaded = hasOrderDetails
            )
            val attentionOrderCount = resolvedSupplierPartnerCount(
                localCount = localAttentionOrderCount,
                dashboardCount = groupDashboard?.actionRequiredOrderCount ?: 0,
                detailedPayloadLoaded = hasCompleteOrderDetails
            )
            val readyToPackOrderCount = resolvedSupplierPartnerCount(
                localCount = localReadyToPackOrderCount,
                dashboardCount = groupDashboard?.readyToPackOrderCount ?: 0,
                detailedPayloadLoaded = hasCompleteOrderDetails
            )
            val packedOrderCount = resolvedSupplierPartnerCount(
                localCount = localPackedOrderCount,
                dashboardCount = groupDashboard?.packedOrderCount ?: 0,
                detailedPayloadLoaded = hasOrderDetails
            )
            val inDeliveryOrderCount = resolvedSupplierPartnerCount(
                localCount = localInDeliveryOrderCount,
                dashboardCount = groupDashboard?.inDeliveryOrderCount ?: 0,
                detailedPayloadLoaded = hasOrderDetails
            )
            val partiallyDeliveredOrderCount = resolvedSupplierPartnerCount(
                localCount = localPartiallyDeliveredOrderCount,
                dashboardCount = groupDashboard?.partiallyDeliveredOrderCount ?: 0,
                detailedPayloadLoaded = hasOrderDetails
            )
            val overdueOrderCount = resolvedSupplierPartnerCount(
                localCount = localOverdueOrderCount,
                dashboardCount = groupDashboard?.overdueOrderCount ?: 0,
                detailedPayloadLoaded = hasOrderDetails
            )
            val deliveredOrderCount = resolvedSupplierPartnerCount(
                localCount = localDeliveredOrderCount,
                dashboardCount = groupDashboard?.deliveredOrderCount ?: 0,
                detailedPayloadLoaded = hasOrderDetails
            )
            val issueOrderCount = resolvedSupplierPartnerCount(
                localCount = localIssueOrderCount,
                dashboardCount = groupDashboard?.issueOrderCount ?: 0,
                detailedPayloadLoaded = hasOrderDetails
            )
            val activeContractCount = resolvedSupplierPartnerCount(
                localCount = localActiveContractCount,
                dashboardCount = groupDashboard?.activeContractCount ?: 0,
                detailedPayloadLoaded = hasContractDetails
            )
            val dashboardPendingContractCount = groupDashboard?.pendingContractCount ?: 0
            val pendingSupplierContractCount = if (hasContractDetails) {
                localPendingSupplierContractCount
            } else {
                // The dashboard only exposes a combined pending count. Until detailed contracts
                // arrive, treating it as supplier-side attention is safer than silently hiding work.
                dashboardPendingContractCount
            }
            val pendingStoreContractCount = if (hasContractDetails) {
                localPendingStoreContractCount
            } else {
                0
            }
            val savedOfferCount = resolvedSupplierPartnerCount(
                localCount = groupPrices.size,
                dashboardCount = groupDashboard?.savedOfferCount ?: 0,
                detailedPayloadLoaded = hasPriceDetails
            )
            val validOfferCount = resolvedSupplierPartnerCount(
                localCount = localValidOfferCount,
                dashboardCount = groupDashboard?.validOfferCount ?: 0,
                detailedPayloadLoaded = hasPriceDetails
            )
            val priceGapCount = resolvedSupplierPartnerCount(
                localCount = localPriceGapCount,
                dashboardCount = groupDashboard?.priceGapCount ?: 0,
                detailedPayloadLoaded = hasCompletePriceGapDetails
            )
            val connectedProductCount = resolvedSupplierPartnerCount(
                localCount = connectedProductIds.size,
                dashboardCount = groupDashboard?.catalogSkuCount ?: 0,
                detailedPayloadLoaded = hasCompleteProductDetails
            )

            val latestOrder = groupOrders.firstOrNull()
            val latestStatus = if (hasOrderDetails) {
                latestOrder?.status
            } else {
                latestOrder?.status ?: groupDashboard?.latestStatus?.takeUnless {
                    it == SupplierOrderStatusDataModel.Draft
                }
            }
            val localLatestActivityMillis = maxOf(
                groupOrders.maxOfOrNull { it.supplierPartnerActivityMillis() } ?: 0L,
                groupPrices.maxOfOrNull { it.supplierPartnerActivityMillis() } ?: 0L,
                groupContracts.maxOfOrNull { it.supplierPartnerActivityMillis() } ?: 0L
            )
            val latestActivityMillis = if (hasCompleteRelationshipDetails) {
                localLatestActivityMillis
            } else {
                maxOf(localLatestActivityMillis, groupDashboard?.latestActivityMillis ?: 0L)
            }
            val actionId = when {
                issueOrderCount > 0 -> "issue"
                overdueOrderCount > 0 -> "overdue"
                attentionOrderCount > 0 -> "orders"
                pendingSupplierContractCount > 0 -> "contract"
                readyToPackOrderCount > 0 ||
                        packedOrderCount > 0 ||
                        inDeliveryOrderCount > 0 ||
                        partiallyDeliveredOrderCount > 0 -> "delivery"
                priceGapCount > 0 -> "price"
                openOrderCount > 0 -> "open"
                pendingStoreContractCount > 0 -> "waiting_store"
                activeContractCount > 0 || validOfferCount > 0 -> "ready"
                orderCount > 0 -> "history"
                else -> "new"
            }

            val searchKey = buildString {
                append(group.groupKey).append(' ')
                append(storeId).append(' ')
                append(publicId).append(' ')
                append(title).append(' ')
                append(address).append(' ')
                append(supplierIds.joinToString(" ")).append(' ')
                append(supplierTitles.joinToString(" ")).append(' ')
                groupOrders.forEach { order ->
                    append(
                        supplierDeskOrderSearchText(
                            order,
                            linesByOrder[order.id].orEmpty()
                        )
                    ).append(' ')
                }
                groupPrices.forEach { price ->
                    append(price.id).append(' ')
                    append(price.goodsItemId).append(' ')
                    append(price.supplierGoodsName.orEmpty()).append(' ')
                    append(price.supplierBarcode.orEmpty()).append(' ')
                    append(price.supplyPrice.supplierDeskMoneyText()).append(' ')
                }
                groupContracts.forEach { contract ->
                    append(contract.id).append(' ')
                    append(contract.status).append(' ')
                    append(supplierVisibleContractTitle(contract)).append(' ')
                    append(supplierVisibleContractSummary(contract)).append(' ')
                    append(contract.conditions.joinToString(" ")).append(' ')
                    append(
                        contract.customTerms.visibleLocalizedString(
                            stateValues.appLanguage,
                            ""
                        )
                    ).append(' ')
                }
            }.lowercase()

            val brief = buildString {
                append(title)
                publicId.takeIf { it.isNotBlank() }?.let { append('\n').append("ID: ").append(it) }
                address.takeIf { it.isNotBlank() }?.let {
                    append('\n').append(localizedStringResource(147, "Address")).append(": ").append(it)
                }
                append('\n').append(localizedStringResource(1471, "Total orders")).append(": ").append(orderCount)
                append('\n').append(localizedStringResource(1455, "Open work")).append(": ").append(openOrderCount)
                append('\n').append(localizedStringResource(1371, "Needs attention")).append(": ").append(attentionOrderCount)
                append('\n').append(localizedStringResource(1472, "Delivered")).append(": ").append(deliveredOrderCount)
                append('\n').append(localizedStringResource(1473, "Issues")).append(": ").append(issueOrderCount)
                append('\n').append(localizedStringResource(1555, "Active contracts")).append(": ").append(activeContractCount)
                append('\n').append(localizedStringResource(2315, "Saved offers")).append(": ").append(savedOfferCount)
                append('\n').append(localizedStringResource(2393, "Connected products")).append(": ").append(connectedProductCount)
                latestActivityMillis.takeIf { it > 0L }?.let {
                    append('\n').append(localizedStringResource(2347, "Last activity")).append(": ").append(receiptUiDateTime(it))
                }
            }

            SupplierPartnerUiModel(
                partnerKey = group.groupKey,
                storeId = storeId,
                publicId = publicId,
                title = title,
                address = address,
                supplierIds = supplierIds,
                supplierTitles = supplierTitles,
                orders = groupOrders,
                lines = groupLines,
                prices = groupPrices,
                contracts = groupContracts,
                orderCount = orderCount,
                openOrderCount = openOrderCount,
                attentionOrderCount = attentionOrderCount,
                readyToPackOrderCount = readyToPackOrderCount,
                packedOrderCount = packedOrderCount,
                inDeliveryOrderCount = inDeliveryOrderCount,
                partiallyDeliveredOrderCount = partiallyDeliveredOrderCount,
                overdueOrderCount = overdueOrderCount,
                deliveredOrderCount = deliveredOrderCount,
                issueOrderCount = issueOrderCount,
                activeContractCount = activeContractCount,
                pendingSupplierContractCount = pendingSupplierContractCount,
                pendingStoreContractCount = pendingStoreContractCount,
                savedOfferCount = savedOfferCount,
                validOfferCount = validOfferCount,
                priceGapCount = priceGapCount,
                connectedProductCount = connectedProductCount,
                latestStatus = latestStatus,
                latestActivityMillis = latestActivityMillis,
                actionId = actionId,
                searchKey = searchKey,
                brief = brief,
                orderDetailsLoaded = hasCompleteOrderDetails,
                priceDetailsLoaded = hasPriceDetails,
                contractDetailsLoaded = hasContractDetails,
                offerReadinessKnown = hasPriceDetails || groupDashboard != null,
                agreementReadinessKnown = hasContractDetails || groupDashboard != null
            )
        }
        .sortedForSupplierCustomers(SUPPLIER_CUSTOMERS_SORT_ACTION)
}

internal fun SupplierPartnerUiModel.matchesSupplierCustomersFilter(filterId: String): Boolean = when (filterId) {
    SUPPLIER_CUSTOMERS_FILTER_ACTIVE -> hasActiveWork
    SUPPLIER_CUSTOMERS_FILTER_ATTENTION -> hasAttention
    SUPPLIER_CUSTOMERS_FILTER_DELIVERY -> hasActiveDelivery
    SUPPLIER_CUSTOMERS_FILTER_CONTRACTS -> hasContractRelationship
    SUPPLIER_CUSTOMERS_FILTER_PRICE_GAPS -> hasPriceGap
    SUPPLIER_CUSTOMERS_FILTER_OFFERS -> hasOfferRelationship
    else -> true
}

internal fun List<SupplierPartnerUiModel>.countForSupplierCustomersFilter(filterId: String): Int =
    count { it.matchesSupplierCustomersFilter(filterId) }

internal fun List<SupplierPartnerUiModel>.sortedForSupplierCustomers(
    sortId: String
): List<SupplierPartnerUiModel> = when (sortId) {
    SUPPLIER_CUSTOMERS_SORT_RECENT -> sortedWith(
        compareByDescending<SupplierPartnerUiModel> { it.latestActivityMillis }
            .thenBy { it.title.lowercase() }
    )

    SUPPLIER_CUSTOMERS_SORT_NAME -> sortedBy { it.title.lowercase() }

    SUPPLIER_CUSTOMERS_SORT_ORDERS -> sortedWith(
        compareByDescending<SupplierPartnerUiModel> { it.orderCount }
            .thenByDescending { it.openOrderCount }
            .thenByDescending { it.latestActivityMillis }
            .thenBy { it.title.lowercase() }
    )

    else -> sortedWith(
        compareByDescending<SupplierPartnerUiModel> { it.hasAttention }
            .thenByDescending { it.issueOrderCount }
            .thenByDescending { it.overdueOrderCount }
            .thenByDescending { it.attentionOrderCount }
            .thenByDescending { it.pendingSupplierContractCount }
            .thenByDescending {
                it.readyToPackOrderCount +
                        it.packedOrderCount +
                        it.inDeliveryOrderCount +
                        it.partiallyDeliveredOrderCount
            }
            .thenByDescending { it.priceGapCount }
            .thenByDescending { it.openOrderCount }
            .thenByDescending { it.latestActivityMillis }
            .thenBy { it.title.lowercase() }
    )
}
