package kz.aita

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ComposeAppCommonTest {

    @Test
    fun example() {
        assertEquals(3, 1 + 2)
    }

    @Test
    fun newStockDraftStartsWithExactlyOneBarcodeRow() {
        assertEquals(listOf(""), emptyList<String>().normalizedInitialStockBarcodeRows())
        assertEquals(listOf(""), listOf("", "   ").normalizedInitialStockBarcodeRows())
        assertEquals(listOf("4601234567890", ""), listOf("4601234567890", "").normalizedInitialStockBarcodeRows())
    }

    @Test
    fun restoredRootStockEditorAlwaysHasAnEscapeFromEditMode() {
        assertFalse(shouldShowStockAddEditBack(isVeryFirstScreen = true, editedGoodsItemId = null))
        assertFalse(shouldShowStockAddEditBack(isVeryFirstScreen = true, editedGoodsItemId = ""))
        assertTrue(shouldShowStockAddEditBack(isVeryFirstScreen = true, editedGoodsItemId = "goods-42"))
        assertTrue(shouldShowStockAddEditBack(isVeryFirstScreen = false, editedGoodsItemId = null))
    }

    @Test
    fun appModeMenuDestinationIsVisibleAgain() {
        assertFalse(NavigationScreenModel.Menu.AppMode.isTemporarilyHiddenFromUi())
    }

    @Test
    fun currentReleaseOffersStoreAndSupplierWithoutTrappingLegacyModes() {
        assertTrue(appModeIsAvailableInCurrentRelease(APP_MODE_STORE, APP_MODE_STORE))
        assertTrue(appModeIsAvailableInCurrentRelease(APP_MODE_SUPPLIER, APP_MODE_STORE))
        assertFalse(appModeIsAvailableInCurrentRelease(APP_MODE_BUYER, APP_MODE_STORE))
        assertFalse(appModeIsAvailableInCurrentRelease(APP_MODE_MANUFACTURER, APP_MODE_STORE))
        assertTrue(appModeIsAvailableInCurrentRelease(APP_MODE_MANUFACTURER, APP_MODE_MANUFACTURER))
    }

    @Test
    fun supplierInboxAttentionFilterMatchesItsMetric() {
        assertTrue(supplierOrderNeedsAttentionInInbox(SupplierOrderStatusDataModel.Sent, hasResponseGaps = false))
        assertTrue(supplierOrderNeedsAttentionInInbox(SupplierOrderStatusDataModel.SeenBySupplier, hasResponseGaps = false))
        assertTrue(supplierOrderNeedsAttentionInInbox(SupplierOrderStatusDataModel.IssueReported, hasResponseGaps = false))
        assertTrue(supplierOrderNeedsAttentionInInbox(SupplierOrderStatusDataModel.Confirmed, hasResponseGaps = true))
        assertFalse(supplierOrderNeedsAttentionInInbox(SupplierOrderStatusDataModel.Delivered, hasResponseGaps = true))
        assertFalse(supplierOrderNeedsAttentionInInbox(SupplierOrderStatusDataModel.Confirmed, hasResponseGaps = false))
    }

    @Test
    fun supplierCatalogIncludesRequestedAndSubstituteProductsWithoutDuplicatingThem() {
        val line = SupplierOrderLineDataModel(
            id = "line-1",
            orderId = "order-1",
            goodsItemId = "11111111-1111-4111-8111-111111111111",
            requestedQuantity = QuantityDataModel(
                id = "0",
                immutableUnitName = listOf(LocalizedStringDataModel("main", "pc.")),
                total = 3.0,
                pricedAmount = 1.0,
                roundTotal = true
            ),
            substituteGoodsItemId = "22222222-2222-4222-8222-222222222222"
        )

        val facets = line.supplierCatalogLineFacets()

        assertEquals(2, facets.size)
        assertEquals(
            setOf(
                "11111111-1111-4111-8111-111111111111",
                "22222222-2222-4222-8222-222222222222"
            ),
            facets.map { it.goodsItemId }.toSet()
        )
        assertFalse(facets.first().isSubstitute)
        assertTrue(facets.last().isSubstitute)
    }

    @Test
    fun supplierCatalogDoesNotDuplicateAProductWhenSubstituteMatchesRequestedItem() {
        val sameId = "11111111-1111-4111-8111-111111111111"
        val line = SupplierOrderLineDataModel(
            id = "line-1",
            orderId = "order-1",
            goodsItemId = sameId,
            requestedQuantity = QuantityDataModel(
                id = "0",
                immutableUnitName = listOf(LocalizedStringDataModel("main", "pc.")),
                total = 1.0,
                pricedAmount = 1.0,
                roundTotal = true
            ),
            substituteGoodsItemId = sameId
        )

        assertEquals(1, line.supplierCatalogLineFacets().size)
        assertTrue(sameId.isSupplierCatalogServerId())
        assertFalse("temporary-goods-id".isSupplierCatalogServerId())
    }

    @Test
    fun supplierCatalogOfferIdentityNormalizesRelationshipIds() {
        assertEquals(
            "store-1|supplier-1|goods-1",
            supplierCatalogOfferIdentity(
                storeId = " store-1 ",
                supplierId = " SUPPLIER-1 ",
                goodsItemId = " GOODS-1 "
            )
        )
    }

    @Test
    fun supplierCatalogGroupsStoreLocalProductIdsBySharedBarcode() {
        fun line(
            id: String,
            orderId: String,
            goodsItemId: String,
            barcode: String
        ) = SupplierOrderLineDataModel(
            id = id,
            orderId = orderId,
            goodsItemId = goodsItemId,
            requestedQuantity = QuantityDataModel(
                id = "0",
                immutableUnitName = listOf(LocalizedStringDataModel("main", "pc.")),
                total = 1.0,
                pricedAmount = 1.0,
                roundTotal = true
            ),
            goodsItemBarcodeSnapshots = listOf(barcode)
        )

        val firstGoodsId = "11111111-1111-4111-8111-111111111111"
        val secondGoodsId = "22222222-2222-4222-8222-222222222222"
        val facets = listOf(
            line("line-1", "order-1", firstGoodsId, " 4601234567890 "),
            line("line-2", "order-2", secondGoodsId, "4601234567890")
        ).flatMap { it.supplierCatalogLineFacets() }
        val price = SupplierGoodsPriceDataModel(
            id = "price-1",
            storeId = "store-1",
            supplierId = "supplier-1",
            goodsItemId = firstGoodsId,
            supplyPrice = PriceDataModel("100", "KZT", "supplier-1"),
            supplierBarcode = "4601234567890"
        )

        val groups = groupSupplierCatalogSources(facets, listOf(price))

        assertEquals(1, groups.size)
        assertEquals(
            setOf(firstGoodsId, secondGoodsId),
            groups.single().facets.map { it.goodsItemId }.toSet()
        )
        assertEquals(listOf("price-1"), groups.single().prices.map { it.id })
    }

    @Test
    fun supplierCatalogDoesNotMergeProductsWithoutASharedIdentity() {
        fun facet(goodsItemId: String, barcode: String) = SupplierOrderLineDataModel(
            orderId = "order-$goodsItemId",
            goodsItemId = goodsItemId,
            requestedQuantity = QuantityDataModel(
                id = "0",
                immutableUnitName = listOf(LocalizedStringDataModel("main", "pc.")),
                total = 1.0,
                pricedAmount = 1.0,
                roundTotal = true
            ),
            goodsItemBarcodeSnapshots = listOf(barcode)
        ).supplierCatalogLineFacets().single()

        val groups = groupSupplierCatalogSources(
            facets = listOf(
                facet("goods-1", "111"),
                facet("goods-2", "222")
            ),
            prices = emptyList()
        )

        assertEquals(2, groups.size)
    }

    @Test
    fun supplierCatalogMetricCountsUseExactlyTheSamePredicatesAsFilters() {
        fun item(
            key: String,
            openOrderCount: Int = 0,
            needsReply: Boolean = false,
            hasSavedOffer: Boolean = false,
            hasMissingOffer: Boolean = false
        ) = SupplierCatalogItemUiModel(
            catalogKey = key,
            goodsItemId = key,
            goodsItemIds = listOf(key),
            orderSearchQuery = key,
            title = key,
            barcodeText = "",
            totalQuantityText = "",
            expectedPriceText = "",
            savedPriceText = "",
            storeTitles = emptyList(),
            openOrderCount = openOrderCount,
            orderCount = openOrderCount,
            lastActivityMillis = 0L,
            latestStatus = SupplierOrderStatusDataModel.Draft,
            needsReply = needsReply,
            hasSavedOffer = hasSavedOffer,
            hasMissingOffer = hasMissingOffer,
            searchKey = key,
            offerNote = "",
            offers = emptyList()
        )

        val items = listOf(
            item("open", openOrderCount = 2, hasMissingOffer = true),
            item("reply", openOrderCount = 1, needsReply = true, hasSavedOffer = true),
            item("price-book", hasSavedOffer = true),
            item("quiet")
        )

        listOf(
            SUPPLIER_CATALOG_FILTER_ALL,
            SUPPLIER_CATALOG_FILTER_OPEN,
            SUPPLIER_CATALOG_FILTER_MISSING_PRICE,
            SUPPLIER_CATALOG_FILTER_REPLY,
            SUPPLIER_CATALOG_FILTER_PRICE_BOOK
        ).forEach { filterId ->
            assertEquals(
                items.filter { it.matchesSupplierCatalogFilter(filterId) }.size,
                items.countForSupplierCatalogFilter(filterId)
            )
        }
    }

    @Test
    fun zeroValuedLegacyPriceBookRowStillNeedsARealOfferPrice() {
        val legacyRow = SupplierGoodsPriceDataModel(
            id = "price-1",
            storeId = "11111111-1111-4111-8111-111111111111",
            supplierId = "22222222-2222-4222-8222-222222222222",
            goodsItemId = "33333333-3333-4333-8333-333333333333",
            supplyPrice = PriceDataModel("0", "KZT", "22222222-2222-4222-8222-222222222222")
        )
        val offer = SupplierCatalogOfferUiModel(
            offerKey = "offer-1",
            storeId = legacyRow.storeId,
            supplierId = legacyRow.supplierId,
            goodsItemId = legacyRow.goodsItemId,
            storeTitle = "Store",
            storePublicId = "S-1",
            supplierTitle = "Supplier",
            existingPrice = legacyRow,
            expectedPrice = null,
            quantityTemplate = QuantityDataModel(
                id = "0",
                immutableUnitName = listOf(LocalizedStringDataModel("main", "pc.")),
                total = 1.0,
                pricedAmount = 1.0,
                roundTotal = true
            ),
            openOrderCount = 1,
            lastActivityMillis = 0L,
            supplierGoodsName = "Product",
            supplierBarcode = "4600000000000",
            canEdit = true
        )
        val item = SupplierCatalogItemUiModel(
            catalogKey = "product-1",
            goodsItemId = legacyRow.goodsItemId,
            goodsItemIds = listOf(legacyRow.goodsItemId),
            orderSearchQuery = legacyRow.goodsItemId,
            title = "Product",
            barcodeText = "4600000000000",
            totalQuantityText = "1 pc.",
            expectedPriceText = "",
            savedPriceText = "",
            storeTitles = listOf("Store"),
            openOrderCount = 1,
            orderCount = 1,
            lastActivityMillis = 0L,
            latestStatus = SupplierOrderStatusDataModel.Sent,
            needsReply = false,
            hasSavedOffer = offer.hasUsablePrice,
            hasMissingOffer = !offer.hasUsablePrice,
            searchKey = "product",
            offerNote = "",
            offers = listOf(offer)
        )

        assertFalse(offer.hasUsablePrice)
        assertEquals(0, item.savedOfferCount)
        assertFalse(item.matchesSupplierCatalogFilter(SUPPLIER_CATALOG_FILTER_PRICE_BOOK))
        assertTrue(item.matchesSupplierCatalogFilter(SUPPLIER_CATALOG_FILTER_MISSING_PRICE))
    }

    @Test
    fun editableSupplierBarcodeDoesNotMergeUnrelatedStoreProducts() {
        fun facet(goodsItemId: String, barcode: String) = SupplierOrderLineDataModel(
            id = "line-$goodsItemId",
            orderId = "order-$goodsItemId",
            goodsItemId = goodsItemId,
            requestedQuantity = QuantityDataModel(
                id = "0",
                immutableUnitName = listOf(LocalizedStringDataModel("main", "pc.")),
                total = 1.0,
                pricedAmount = 1.0,
                roundTotal = true
            ),
            goodsItemBarcodeSnapshots = listOf(barcode)
        ).supplierCatalogLineFacets().single()

        fun price(id: String, goodsItemId: String) = SupplierGoodsPriceDataModel(
            id = id,
            storeId = "store-$id",
            supplierId = "supplier-1",
            goodsItemId = goodsItemId,
            supplyPrice = PriceDataModel("100", "KZT", "supplier-1"),
            // This field belongs to a Store-specific offer and is editable by the supplier.
            // Sharing it must not become a product-identity bridge.
            supplierBarcode = "supplier-entered-shared-value"
        )

        val firstGoodsId = "11111111-1111-4111-8111-111111111111"
        val secondGoodsId = "22222222-2222-4222-8222-222222222222"
        val groups = groupSupplierCatalogSources(
            facets = listOf(
                facet(firstGoodsId, "store-barcode-1"),
                facet(secondGoodsId, "store-barcode-2")
            ),
            prices = listOf(
                price("one", firstGoodsId),
                price("two", secondGoodsId)
            )
        )

        assertEquals(2, groups.size)
        assertEquals(
            setOf(setOf(firstGoodsId), setOf(secondGoodsId)),
            groups.map { group -> group.facets.map { it.goodsItemId }.toSet() }.toSet()
        )

        val malformedPriceGroups = groupSupplierCatalogSources(
            facets = emptyList(),
            prices = listOf(
                price("malformed-one", "").copy(supplierBarcode = "same-editable-barcode"),
                price("malformed-two", "").copy(supplierBarcode = "same-editable-barcode")
            )
        )
        assertEquals(2, malformedPriceGroups.size)
    }

    @Test
    fun supplierCustomersMergeOrderContractAndOfferByImmutableStoreIdentity() {
        val groups = groupSupplierPartnerRelationSources(
            listOf(
                SupplierPartnerRelationSource(
                    sourceKey = "order-1",
                    storeId = "store-1",
                    storePublicId = "S-100",
                    storeTitle = "North Store"
                ),
                SupplierPartnerRelationSource(
                    sourceKey = "contract-1",
                    storePublicId = " s-100 ",
                    storeTitle = "Renamed North Store"
                ),
                SupplierPartnerRelationSource(
                    sourceKey = "price-1",
                    storeId = " STORE-1 "
                )
            )
        )

        assertEquals(1, groups.size)
        assertEquals(3, groups.single().sources.size)
        assertEquals("store-id:store-1", groups.single().groupKey)
    }

    @Test
    fun supplierCustomersKeepContractOnlyAndOfferOnlyRelationshipsVisible() {
        val groups = groupSupplierPartnerRelationSources(
            listOf(
                SupplierPartnerRelationSource(
                    sourceKey = "contract-only",
                    storeId = "contract-store"
                ),
                SupplierPartnerRelationSource(
                    sourceKey = "offer-only",
                    storeId = "offer-store"
                )
            )
        )

        assertEquals(2, groups.size)
        assertEquals(
            setOf("store-id:contract-store", "store-id:offer-store"),
            groups.map { it.groupKey }.toSet()
        )
    }

    @Test
    fun supplierCustomersNeverMergeMalformedPartnersOnlyBecauseTheirTitlesMatch() {
        val groups = groupSupplierPartnerRelationSources(
            listOf(
                SupplierPartnerRelationSource(
                    sourceKey = "broken-a",
                    storeTitle = "Store"
                ),
                SupplierPartnerRelationSource(
                    sourceKey = "broken-b",
                    storeTitle = "Store"
                )
            )
        )

        assertEquals(2, groups.size)
    }

    @Test
    fun supplierCustomersKeepFullyAnonymousHistoricalRowsSeparate() {
        val groups = groupSupplierPartnerRelationSources(
            listOf(
                SupplierPartnerRelationSource(sourceKey = ""),
                SupplierPartnerRelationSource(sourceKey = "")
            )
        )

        assertEquals(2, groups.size)
        assertEquals(2, groups.map { it.groupKey }.distinct().size)
    }

    @Test
    fun supplierCustomersUseTitleAndAddressTogetherOnlyAsHistoricalFallbackIdentity() {
        val groups = groupSupplierPartnerRelationSources(
            listOf(
                SupplierPartnerRelationSource(
                    sourceKey = "old-order",
                    storeTitle = " North Store ",
                    storeAddress = "1 Main Street"
                ),
                SupplierPartnerRelationSource(
                    sourceKey = "old-contract",
                    storeTitle = "north   store",
                    storeAddress = " 1 main street "
                ),
                SupplierPartnerRelationSource(
                    sourceKey = "different-address",
                    storeTitle = "North Store",
                    storeAddress = "2 Main Street"
                )
            )
        )

        assertEquals(2, groups.size)
        assertTrue(groups.any { group -> group.sources.size == 2 })
        assertTrue(groups.any { group -> group.sources.single().sourceKey == "different-address" })
    }

    @Test
    fun supplierCustomersDetailedPayloadReplacesStaleDashboardCounts() {
        assertEquals(
            0,
            resolvedSupplierPartnerCount(
                localCount = 0,
                dashboardCount = 7,
                detailedPayloadLoaded = true
            )
        )
        assertEquals(
            3,
            resolvedSupplierPartnerCount(
                localCount = 3,
                dashboardCount = 7,
                detailedPayloadLoaded = true
            )
        )
    }

    @Test
    fun supplierCustomersDashboardFillsOnlyNotYetLoadedDetailGaps() {
        assertEquals(
            7,
            resolvedSupplierPartnerCount(
                localCount = 3,
                dashboardCount = 7,
                detailedPayloadLoaded = false
            )
        )
        assertEquals(
            4,
            resolvedSupplierPartnerCount(
                localCount = 4,
                dashboardCount = 0,
                detailedPayloadLoaded = false
            )
        )
    }

    @Test
    fun supplierCustomersMetricCountsUseExactlyTheSamePredicatesAsFilters() {
        fun partner(
            key: String,
            open: Int = 0,
            attention: Int = 0,
            activeContracts: Int = 0,
            savedOffers: Int = 0,
            validOffers: Int = 0,
            priceGaps: Int = 0,
            readyToPack: Int = 0,
            inDelivery: Int = 0
        ) = SupplierPartnerUiModel(
            partnerKey = key,
            storeId = key,
            publicId = "",
            title = key,
            address = "",
            supplierIds = emptyList(),
            supplierTitles = emptyList(),
            orders = emptyList(),
            lines = emptyList(),
            prices = emptyList(),
            contracts = emptyList(),
            orderCount = open,
            openOrderCount = open,
            attentionOrderCount = attention,
            readyToPackOrderCount = readyToPack,
            packedOrderCount = 0,
            inDeliveryOrderCount = inDelivery,
            partiallyDeliveredOrderCount = 0,
            overdueOrderCount = 0,
            deliveredOrderCount = 0,
            issueOrderCount = 0,
            activeContractCount = activeContracts,
            pendingSupplierContractCount = 0,
            pendingStoreContractCount = 0,
            savedOfferCount = savedOffers,
            validOfferCount = validOffers,
            priceGapCount = priceGaps,
            connectedProductCount = 0,
            latestStatus = null,
            latestActivityMillis = 0L,
            actionId = "ready",
            searchKey = key,
            brief = key
        )

        val partners = listOf(
            partner("open", open = 2),
            partner("attention", attention = 1),
            partner("contract", activeContracts = 1),
            partner("offer", savedOffers = 1, validOffers = 1),
            partner("invalid-saved-offer", savedOffers = 1, priceGaps = 1),
            partner("missing-price", priceGaps = 1),
            partner("ready-to-pack", readyToPack = 1),
            partner("in-delivery", inDelivery = 1),
            partner("quiet")
        )

        listOf(
            SUPPLIER_CUSTOMERS_FILTER_ALL,
            SUPPLIER_CUSTOMERS_FILTER_ACTIVE,
            SUPPLIER_CUSTOMERS_FILTER_ATTENTION,
            SUPPLIER_CUSTOMERS_FILTER_DELIVERY,
            SUPPLIER_CUSTOMERS_FILTER_CONTRACTS,
            SUPPLIER_CUSTOMERS_FILTER_PRICE_GAPS,
            SUPPLIER_CUSTOMERS_FILTER_OFFERS
        ).forEach { filterId ->
            assertEquals(
                partners.filter { it.matchesSupplierCustomersFilter(filterId) }.size,
                partners.countForSupplierCustomersFilter(filterId)
            )
        }

        assertTrue(partners.first { it.partnerKey == "invalid-saved-offer" }.hasOfferRelationship)
        assertFalse(partners.first { it.partnerKey == "missing-price" }.hasOfferRelationship)
        assertTrue(partners.first { it.partnerKey == "ready-to-pack" }.hasActiveDelivery)
        assertTrue(partners.first { it.partnerKey == "in-delivery" }.hasActiveDelivery)
    }

    @Test
    fun supplierCustomersActionSortPutsOperationalRiskBeforeQuietHistory() {
        fun partner(key: String, attention: Int, activity: Long) = SupplierPartnerUiModel(
            partnerKey = key,
            storeId = key,
            publicId = "",
            title = key,
            address = "",
            supplierIds = emptyList(),
            supplierTitles = emptyList(),
            orders = emptyList(),
            lines = emptyList(),
            prices = emptyList(),
            contracts = emptyList(),
            orderCount = 0,
            openOrderCount = attention,
            attentionOrderCount = attention,
            readyToPackOrderCount = 0,
            packedOrderCount = 0,
            inDeliveryOrderCount = 0,
            partiallyDeliveredOrderCount = 0,
            overdueOrderCount = 0,
            deliveredOrderCount = 0,
            issueOrderCount = 0,
            activeContractCount = 0,
            pendingSupplierContractCount = 0,
            pendingStoreContractCount = 0,
            savedOfferCount = 0,
            validOfferCount = 0,
            priceGapCount = 0,
            connectedProductCount = 0,
            latestStatus = null,
            latestActivityMillis = activity,
            actionId = if (attention > 0) "orders" else "ready",
            searchKey = key,
            brief = key
        )

        val sorted = listOf(
            partner("quiet-newer", attention = 0, activity = 500L),
            partner("needs-action", attention = 1, activity = 100L)
        ).sortedForSupplierCustomers(SUPPLIER_CUSTOMERS_SORT_ACTION)

        assertEquals("needs-action", sorted.first().partnerKey)
    }

    @Test
    fun supplierContractRelationshipAndGoodsSelectionsNormalizePersistedIds() {
        assertEquals(
            "store-1" to "supplier-1",
            supplierContractRelationshipKey(" STORE-1 ", " Supplier-1 ")
        )
        assertEquals(
            setOf("goods-1", "goods-2"),
            normalizedSupplierContractGoodsIds(
                listOf(" GOODS-1 ", "goods-1", " Goods-2 ", " ")
            )
        )
    }

    @Test
    fun supplierContractEditorKeepsContractOnlyGoodsAvailable() {
        val quantity = QuantityDataModel(
            id = "quantity-1",
            immutableUnitName = listOf(LocalizedStringDataModel("main", "pcs")),
            total = 6.0,
            pricedAmount = 1.0,
            roundTotal = true
        )
        val contract = SupplierPartnershipContractDataModel(
            id = "contract-1",
            storeId = " STORE-1 ",
            supplierId = " SUPPLIER-1 ",
            goodsItemIds = listOf(" GOODS-1 "),
            title = listOf(LocalizedStringDataModel("main", "Store terms")),
            priceTerms = listOf(
                SupplierContractPriceTermDataModel(
                    goodsItemId = "goods-1",
                    goodsItemNameSnapshot = listOf(
                        LocalizedStringDataModel("main", "Contract widget")
                    ),
                    supplyPrice = PriceDataModel("125", "KZT", "supplier-1"),
                    packageQuantity = quantity
                )
            )
        )

        val options = AppConfiguration.buildSupplierContractGoodsOptions(
            orders = emptyList(),
            lines = emptyList(),
            prices = emptyList(),
            contracts = listOf(contract)
        )

        assertEquals(1, options.size)
        assertEquals("store-1|supplier-1|goods-1", options.single().key)
        assertEquals("Contract widget", options.single().title)
        assertEquals("125", options.single().latestSupplyPrice?.price)
        assertEquals(quantity, options.single().latestQuantity)
    }


    @Test
    fun supplierContractLegacyFiltersNormalizeForTheCurrentActorSide() {
        assertEquals(
            SUPPLIER_CONTRACT_FILTER_PENDING,
            normalizedSupplierContractFilter("open", SUPPLIER_CONTRACT_SIDE_SUPPLIER)
        )
        assertEquals(
            SUPPLIER_CONTRACT_FILTER_WAITING_ME,
            normalizedSupplierContractFilter(
                SUPPLIER_CONTRACT_STATUS_PENDING_STORE,
                SUPPLIER_CONTRACT_SIDE_STORE
            )
        )
        assertEquals(
            SUPPLIER_CONTRACT_FILTER_WAITING_OTHER,
            normalizedSupplierContractFilter(
                SUPPLIER_CONTRACT_STATUS_PENDING_STORE,
                SUPPLIER_CONTRACT_SIDE_SUPPLIER
            )
        )
        assertEquals(
            SUPPLIER_CONTRACT_FILTER_WAITING_ME,
            normalizedSupplierContractFilter(
                SUPPLIER_CONTRACT_STATUS_PENDING_SUPPLIER,
                SUPPLIER_CONTRACT_SIDE_SUPPLIER
            )
        )
        assertEquals(
            SUPPLIER_CONTRACT_FILTER_ALL,
            normalizedSupplierContractFilter("unknown", SUPPLIER_CONTRACT_SIDE_SUPPLIER)
        )
    }

    @Test
    fun supplierContractMetricsAndFiltersUseTheSamePredicates() {
        fun item(
            id: String,
            status: String,
            waitsForMe: Boolean = false,
            waitsForOther: Boolean = false
        ) = SupplierContractWorkspaceUiModel(
            contract = SupplierPartnershipContractDataModel(id = id, status = status),
            partnerKey = id,
            partnerTitle = id,
            partnerSubtitle = "",
            searchKey = id,
            latestActivityMillis = 0L,
            selectedGoodsCount = 0,
            activePriceTermCount = 0,
            waitsForMe = waitsForMe,
            waitsForOtherSide = waitsForOther,
            blocksSupply = waitsForMe || waitsForOther
        )

        val items = listOf(
            item("mine", SUPPLIER_CONTRACT_STATUS_PENDING_SUPPLIER, waitsForMe = true),
            item("partner", SUPPLIER_CONTRACT_STATUS_PENDING_STORE, waitsForOther = true),
            item("active", SUPPLIER_CONTRACT_STATUS_ACTIVE),
            item("declined", SUPPLIER_CONTRACT_STATUS_DECLINED)
        )

        listOf(
            SUPPLIER_CONTRACT_FILTER_ALL,
            SUPPLIER_CONTRACT_FILTER_PENDING,
            SUPPLIER_CONTRACT_FILTER_WAITING_ME,
            SUPPLIER_CONTRACT_FILTER_WAITING_OTHER,
            SUPPLIER_CONTRACT_FILTER_ACTIVE,
            SUPPLIER_CONTRACT_FILTER_DECLINED
        ).forEach { filterId ->
            assertEquals(
                items.filter { it.matchesSupplierContractFilter(filterId) }.size,
                items.countForSupplierContractFilter(filterId)
            )
        }
    }

    @Test
    fun supplierContractActionSortPrioritizesMyDecisionThenPartnerThenActiveHistory() {
        fun item(
            id: String,
            activity: Long,
            status: String,
            waitsForMe: Boolean = false,
            waitsForOther: Boolean = false
        ) = SupplierContractWorkspaceUiModel(
            contract = SupplierPartnershipContractDataModel(id = id, status = status),
            partnerKey = id,
            partnerTitle = id,
            partnerSubtitle = "",
            searchKey = id,
            latestActivityMillis = activity,
            selectedGoodsCount = 0,
            activePriceTermCount = 0,
            waitsForMe = waitsForMe,
            waitsForOtherSide = waitsForOther,
            blocksSupply = waitsForMe || waitsForOther
        )

        val sorted = listOf(
            item("active-new", 500L, SUPPLIER_CONTRACT_STATUS_ACTIVE),
            item("partner", 200L, SUPPLIER_CONTRACT_STATUS_PENDING_STORE, waitsForOther = true),
            item("mine", 100L, SUPPLIER_CONTRACT_STATUS_PENDING_SUPPLIER, waitsForMe = true)
        ).sortedForSupplierContracts(SUPPLIER_CONTRACT_SORT_ACTION)

        assertEquals(listOf("mine", "partner", "active-new"), sorted.map { it.id })
    }

    @Test
    fun supplierContractDraftTermsPreserveExistingTermsAndFillNewGoodsFromCatalog() {
        val existingMinimum = QuantityDataModel(
            id = "minimum",
            immutableUnitName = listOf(LocalizedStringDataModel("main", "pcs")),
            total = 5.0,
            pricedAmount = 1.0,
            roundTotal = true
        )
        val fallbackPackage = QuantityDataModel(
            id = "package",
            immutableUnitName = listOf(LocalizedStringDataModel("main", "pcs")),
            total = 12.0,
            pricedAmount = 1.0,
            roundTotal = true
        )
        val existing = SupplierPartnershipContractDataModel(
            id = "contract-1",
            priceTerms = listOf(
                SupplierContractPriceTermDataModel(
                    id = "term-1",
                    goodsItemId = "goods-1",
                    goodsItemNameSnapshot = listOf(LocalizedStringDataModel("main", "Existing name")),
                    suggestedSalePrice = PriceDataModel("150", "KZT", "supplier-1"),
                    minOrderQuantity = existingMinimum,
                    note = listOf(LocalizedStringDataModel("main", "Existing note"))
                ),
                SupplierContractPriceTermDataModel(
                    id = "term-contract-only",
                    goodsItemId = "goods-3",
                    supplyPrice = PriceDataModel("300", "KZT", "supplier-1")
                )
            )
        )
        val goods = listOf(
            SupplierContractGoodsUiModel(
                key = "store-1|supplier-1|goods-1",
                storeId = "store-1",
                supplierId = "supplier-1",
                goodsItemId = "goods-1",
                title = "Catalog name",
                subtitle = "111",
                priceText = "100 KZT",
                quantityText = "12 pcs",
                nameSnapshot = listOf(LocalizedStringDataModel("main", "Catalog name")),
                latestSupplyPrice = PriceDataModel("100", "KZT", "supplier-1"),
                latestQuantity = fallbackPackage
            ),
            SupplierContractGoodsUiModel(
                key = "store-1|supplier-1|goods-2",
                storeId = "store-1",
                supplierId = "supplier-1",
                goodsItemId = "goods-2",
                title = "New product",
                subtitle = "222",
                priceText = "200 KZT",
                quantityText = "12 pcs",
                nameSnapshot = listOf(LocalizedStringDataModel("main", "New product")),
                latestSupplyPrice = PriceDataModel("200", "KZT", "supplier-1"),
                latestQuantity = fallbackPackage
            )
        )
        val delivery = listOf(LocalizedStringDataModel("main", "Tuesday"))
        val summary = listOf(LocalizedStringDataModel("main", "Shared terms"))

        val terms = buildSupplierContractDraftPriceTerms(
            existingContract = existing,
            selectableGoods = goods,
            selectedGoodsIds = setOf("goods-1", "goods-2", "goods-3"),
            deliverySchedule = delivery,
            summary = summary
        )

        assertEquals(listOf("goods-1", "goods-2", "goods-3"), terms.map { it.goodsItemId })
        val first = terms.single { it.goodsItemId == "goods-1" }
        assertEquals("term-1", first.id)
        assertEquals("100", first.supplyPrice?.price)
        assertEquals("150", first.suggestedSalePrice?.price)
        assertEquals(existingMinimum, first.minOrderQuantity)
        assertEquals("Existing note", first.note.single().value)

        val second = terms.single { it.goodsItemId == "goods-2" }
        assertEquals("200", second.supplyPrice?.price)
        assertEquals(fallbackPackage, second.minOrderQuantity)
        assertEquals(delivery, second.scheduleText)
        assertEquals(summary, second.note)

        val contractOnly = terms.single { it.goodsItemId == "goods-3" }
        assertEquals("term-contract-only", contractOnly.id)
        assertEquals("300", contractOnly.supplyPrice?.price)
    }

    @Test
    fun supplierDispatchServerRunsClaimEveryOrderOnlyOnce() {
        val runs = listOf(
            SupplierDashboardDispatchRunDataModel(
                runId = "run-a",
                orderIds = listOf(" ORDER-1 ", "order-2"),
                packableOrderIds = listOf("order-3")
            ),
            SupplierDashboardDispatchRunDataModel(
                runId = "run-b",
                orderIds = listOf("order-2", "order-4"),
                inDeliveryOrderIds = listOf("ORDER-1", "order-5")
            )
        )

        val claimed = claimSupplierDispatchServerOrderIds(runs)

        assertEquals(listOf("ORDER-1", "order-2", "order-3"), claimed[0])
        assertEquals(listOf("order-4", "order-5"), claimed[1])
        assertEquals(5, claimed.flatten().map { it.lowercase() }.distinct().size)
    }

    @Test
    fun supplierDispatchActionsStayInsideTheRunAndExcludeBlockedOrders() {
        val safe = safeSupplierDispatchActionOrderIds(
            runOrderIds = listOf("order-1", "order-2", "order-3"),
            requestedActionOrderIds = listOf("ORDER-2", "order-3", "outside", "order-2"),
            contractBlockedOrderIds = listOf(" Order-3 ")
        )

        assertEquals(listOf("order-2"), safe)
    }

    @Test
    fun supplierDispatchMetricsUseTheSameOrderPredicatesAsFilters() {
        val first = SupplierDispatchWorkspaceRunUiModel(
            key = "first",
            orderIds = listOf("order-1", "order-2"),
            packableOrderIds = listOf("order-1"),
            dispatchableOrderIds = listOf("order-2"),
            attentionOrderIds = listOf("order-2")
        )
        val second = SupplierDispatchWorkspaceRunUiModel(
            key = "second",
            orderIds = listOf("order-3"),
            packableOrderIds = listOf("order-1", "order-3"),
            inDeliveryOrderIds = listOf("order-3"),
            contractBlockedOrderIds = listOf("order-3")
        )
        val runs = listOf(first, second)

        assertTrue(first.matchesSupplierDispatchFilter(SUPPLIER_DISPATCH_FILTER_READY_TO_PACK))
        assertFalse(first.matchesSupplierDispatchFilter(SUPPLIER_DISPATCH_FILTER_IN_DELIVERY))
        assertEquals(
            2,
            runs.distinctOrderCountForSupplierDispatchFilter(SUPPLIER_DISPATCH_FILTER_READY_TO_PACK)
        )
        assertEquals(
            2,
            runs.distinctOrderCountForSupplierDispatchFilter(SUPPLIER_DISPATCH_FILTER_ATTENTION)
        )
        assertEquals(
            listOf("order-3"),
            second.orderIdsForFilter(SUPPLIER_DISPATCH_FILTER_CONTRACTS)
        )
    }

    @Test
    fun supplierDispatchLoadedOrdersOverrideStaleServerRunMembership() {
        val claimed = listOf("order-open", "order-closed", "order-missing")

        assertEquals(
            claimed,
            supplierDispatchOrderIdsForLoadedState(
                claimedOrderIds = claimed,
                locallyOpenOrderIds = listOf("order-open"),
                ordersLoaded = false
            )
        )
        assertEquals(
            listOf("order-open"),
            supplierDispatchOrderIdsForLoadedState(
                claimedOrderIds = claimed,
                locallyOpenOrderIds = listOf(" ORDER-OPEN "),
                ordersLoaded = true
            )
        )
    }

    @Test
    fun supplierDispatchPrioritizesContractSafetyCheckBeforeOrdinaryReadyWork() {
        val normal = SupplierDispatchWorkspaceRunUiModel(
            key = "normal",
            orderIds = listOf("order-1"),
            packableOrderIds = listOf("order-1"),
            contractSafetyReady = true
        )
        val checking = SupplierDispatchWorkspaceRunUiModel(
            key = "checking",
            orderIds = listOf("order-2"),
            contractSafetyReady = false
        )

        assertEquals(
            listOf("checking", "normal"),
            listOf(normal, checking).sortedForSupplierDispatch(SUPPLIER_DISPATCH_SORT_ACTION).map { it.key }
        )
    }

}
