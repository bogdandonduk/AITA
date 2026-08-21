package kz.aita

import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedCommonTest {

    @Test
    fun example() {
        assertEquals(3, 1 + 2)
    }

    @Test
    fun resolvedAddressUsesLocalizedSnapshotsAndCoordinatesAsIdentity() {
        val location = LocationDataModel(
            name = "Astana, Dostyk Street, 1",
            latitude = 51.1282,
            longitude = 71.4304,
            provider = AITA_ADDRESS_PROVIDER_YANDEX,
            providerObjectId = "ymapsbm1://geo?data=test",
            countryCode = "KZ",
            primaryLanguage = "ru",
            localizedNames = listOf(
                LocalizedStringDataModel("en", "Dostyk Street, 1"),
                LocalizedStringDataModel("ru", "улица Достык, 1"),
                LocalizedStringDataModel("kk", "Достық көшесі, 1")
            ),
            localizedAddresses = listOf(
                LocalizedStringDataModel("main", "Казахстан, Астана, улица Достык, 1"),
                LocalizedStringDataModel("en", "Kazakhstan, Astana, Dostyk Street, 1"),
                LocalizedStringDataModel("ru", "Казахстан, Астана, улица Достык, 1"),
                LocalizedStringDataModel("kk", "Қазақстан, Астана, Достық көшесі, 1")
            ),
            fallbackAddress = "Казахстан, Астана, улица Достык, 1",
            resolvedAtMillis = 100L,
            lastCheckedAtMillis = 200L,
            providerRevision = "revision"
        )

        assertTrue(location.isResolvedAddress())
        assertEquals("Kazakhstan, Astana, Dostyk Street, 1", location.displayAddress("en"))
        assertEquals("Қазақстан, Астана, Достық көшесі, 1", location.displayAddress("kk"))
        assertTrue(location.matchesDisplayedAddress("  Kazakhstan,   Astana, Dostyk Street, 1 ", "en"))
        assertFalse(location.matchesDisplayedAddress("Astana, somewhere else", "en"))
    }

    @Test
    fun legacyTextOnlyLocationRemainsDisplayableButCannotBeSavedAsVerified() {
        val legacy = LocationDataModel(
            name = "Legacy address text",
            postalIndex = "010000",
            latitude = 0.0,
            longitude = 0.0
        )

        assertEquals("Legacy address text", legacy.displayAddress("en"))
        assertFalse(legacy.isResolvedAddress())
        assertFalse(legacy.hasValidCoordinates())
    }

    @Test
    fun bootstrapDecoderAcceptsPrimaryPairAndMixedCandidateShapes() {
        val decoded = decodeBootstrapServerUrlCandidates(
            """
            {
              "serverUrl": {
                "first": "https://aita-api.bogdan-dond.uk.workers.dev/",
                "second": "1"
              },
              "serverCandidates": [
                {
                  "url": "https://aita-api.bogdan-dond.uk.workers.dev",
                  "priority": 100,
                  "supportsRealtime": true,
                  "role": "primary-domainless"
                },
                "https://legacy-public.example/",
                {
                  "serverUrl": "https://secondary.example.com/healthz"
                }
              ]
            }
            """.trimIndent()
        )

        assertEquals(
            listOf(
                "https://aita-api.bogdan-dond.uk.workers.dev",
                "https://legacy-public.example",
                "https://secondary.example.com"
            ),
            decoded
        )
    }

    @Test
    fun bootstrapDecoderAcceptsJsonEncodedPayloadCandidates() {
        val decoded = decodeBootstrapServerUrlCandidates(
            """
            {
              "message": null,
              "payload": "{\"serverCandidates\":[\"https://one.example\",{\"url\":\"https://two.example/config/global\"}]}"
            }
            """.trimIndent()
        )

        assertEquals(
            listOf("https://one.example", "https://two.example"),
            decoded
        )
    }

    @Test
    fun nonCanonicalPublicEndpointsCannotBecomeClientRequestCandidates() {
        val primary = Pair("https://aita-api.bogdan-dond.uk.workers.dev", "1")
        val blank = Pair("", "1")
        val nonCanonicalUrls = listOf(
            "https://legacy-public.example",
            "https://other-public.example/stock/get",
            "https://bootstrap-public.example/.well-known/aita-server.json"
        )

        nonCanonicalUrls.forEach { nonCanonicalUrl ->
            assertEquals(null, normalizedAutomaticAitaServerUrlOrNull(nonCanonicalUrl))
            assertEquals(null, normalizedExplicitAitaServerUrlOrNull(nonCanonicalUrl))
            assertEquals(primary, chooseClientServerUrlPair(primary, Pair(nonCanonicalUrl, "2")))
            assertEquals(primary, chooseClientServerUrlPair(blank, Pair(nonCanonicalUrl, "2")))
            assertEquals(
                primary,
                chooseClientServerUrlPair(Pair(nonCanonicalUrl, "1"), Pair(nonCanonicalUrl, "2"))
            )
        }
    }

    @Test
    fun canonicalGatewayAndLocalDevelopmentOverridesRemainAvailable() {
        val canonical = "https://aita-api.bogdan-dond.uk.workers.dev"

        assertEquals(canonical, normalizedAutomaticAitaServerUrlOrNull("$canonical/healthz"))
        assertEquals(canonical, normalizedExplicitAitaServerUrlOrNull(canonical))
        assertEquals("http://127.0.0.1:8080", normalizedExplicitAitaServerUrlOrNull("127.0.0.1"))
        assertEquals("http://100.95.12.3:8080", normalizedExplicitAitaServerUrlOrNull("100.95.12.3"))
        assertEquals("https://aita-ubuntu.tail-scale.ts.net", normalizedExplicitAitaServerUrlOrNull("https://aita-ubuntu.tail-scale.ts.net"))
        assertEquals("http://aita-ubuntu:8080", normalizedExplicitAitaServerUrlOrNull("aita-ubuntu"))
        assertEquals("http://[fd7a:115c:a1e0::1]:8080", normalizedExplicitAitaServerUrlOrNull("[fd7a:115c:a1e0::1]"))
        assertEquals(null, normalizedExplicitAitaServerUrlOrNull("https://secondary.example.com"))
        assertEquals(null, normalizedExplicitAitaServerUrlOrNull("https://fcpay.example.com"))
        assertEquals(null, normalizedExplicitAitaServerUrlOrNull("https://fd-service.example.com"))
    }

    @Test
    fun onlyCloudDataEndpointsRequireAuthenticatedSessionGate() {
        assertTrue(cloudEndpointRequiresAuthentication("auth/session"))
        assertTrue(cloudEndpointRequiresAuthentication("auth/security/settings"))
        assertTrue(cloudEndpointRequiresAuthentication("stock/add"))
        assertTrue(cloudEndpointRequiresAuthentication("notifications/add"))
        assertFalse(cloudEndpointRequiresAuthentication("auth/ping"))
        assertFalse(cloudEndpointRequiresAuthentication("auth/refresh"))
        assertFalse(cloudEndpointRequiresAuthentication("config/global"))
        assertFalse(cloudEndpointRequiresAuthentication("res/string"))
        assertFalse(cloudEndpointRequiresAuthentication("readyz"))
    }

    @Test
    fun expiredCloudSessionMessageIsActionableAndPreservesLocalDataMeaning() {
        val message = cloudSessionExpiredMessage()
        assertTrue(message.any { it.language == "en" && "Sign in again" in it.value && "local data" in it.value })
        assertTrue(message.any { it.language == "ru" && "Войдите снова" in it.value && "Локальные данные" in it.value })
        assertTrue(message.any { it.language == "kk" && "қайта кіріңіз" in it.value && "Жергілікті деректер" in it.value })
    }

    @Test
    fun onlyReadOnlyHttpMethodsMayFailOverAcrossAliases() {
        assertTrue(HttpMethod.Get.canRetryAcrossAitaServerAliases())
        assertTrue(HttpMethod.Head.canRetryAcrossAitaServerAliases())
        assertTrue(HttpMethod.Options.canRetryAcrossAitaServerAliases())
        assertFalse(HttpMethod.Post.canRetryAcrossAitaServerAliases())
        assertFalse(HttpMethod.Put.canRetryAcrossAitaServerAliases())
        assertFalse(HttpMethod.Patch.canRetryAcrossAitaServerAliases())
        assertFalse(HttpMethod.Delete.canRetryAcrossAitaServerAliases())
    }

    @Test
    fun aliasRetryRequiresReadOnlyMethodAndTransportLikeFailure() {
        assertTrue(
            shouldRetryNetworkRequestOnNextServerUrl(
                method = HttpMethod.Get,
                endpointUrl = "healthz",
                status = HttpStatusCode.ServiceUnavailable,
                rawBody = "{\"transportFailure\":true}",
                aitaServerResponse = true
            )
        )
        assertTrue(
            shouldRetryNetworkRequestOnNextServerUrl(
                method = HttpMethod.Get,
                endpointUrl = "config/global",
                status = HttpStatusCode.OK,
                rawBody = "<html>not AITA</html>",
                aitaServerResponse = false
            )
        )
        assertFalse(
            shouldRetryNetworkRequestOnNextServerUrl(
                method = HttpMethod.Post,
                endpointUrl = "transactions/add",
                status = HttpStatusCode.ServiceUnavailable,
                rawBody = "{\"transportFailure\":true}",
                aitaServerResponse = true
            )
        )
        assertFalse(
            shouldRetryNetworkRequestOnNextServerUrl(
                method = HttpMethod.Get,
                endpointUrl = "user/get",
                status = HttpStatusCode.Unauthorized,
                rawBody = "{\"negative\":true}",
                aitaServerResponse = true
            )
        )
    }

    @Test
    fun identicalSupplierReadsShareOneInFlightRequestAndResult() = runTest {
        val coordinator = SingleFlightRequestCoordinator<String, Int>()
        val requestMayComplete = CompletableDeferred<Unit>()
        var networkCallCount = 0

        val first = async {
            coordinator.run("supplier-workspace") {
                networkCallCount += 1
                requestMayComplete.await()
                42
            }
        }
        val second = async {
            coordinator.run("supplier-workspace") {
                networkCallCount += 1
                99
            }
        }

        yield()
        assertEquals(1, networkCallCount)

        requestMayComplete.complete(Unit)
        assertEquals(42, first.await())
        assertEquals(42, second.await())

        val nextRefresh = coordinator.run("supplier-workspace") {
            networkCallCount += 1
            7
        }
        assertEquals(7, nextRefresh)
        assertEquals(2, networkCallCount)
    }

    @Test
    fun supplierWorkspaceRefreshesOncePerShortNavigationBurst() {
        val first = supplierWorkspaceRefreshDecision(
            nowMillis = 100_000L,
            lastBaseRefreshAtMillis = 0L,
            lastContractsRefreshAtMillis = 0L,
            includeContracts = true,
            force = false
        )
        assertTrue(first.refreshBaseWorkspace)
        assertTrue(first.refreshContracts)

        val duplicate = supplierWorkspaceRefreshDecision(
            nowMillis = 105_000L,
            lastBaseRefreshAtMillis = 100_000L,
            lastContractsRefreshAtMillis = 100_000L,
            includeContracts = true,
            force = false
        )
        assertFalse(duplicate.refreshBaseWorkspace)
        assertFalse(duplicate.refreshContracts)
    }

    @Test
    fun supplierWorkspaceCanRefreshOnlyStaleContractsOrForceEverything() {
        val contractsOnly = supplierWorkspaceRefreshDecision(
            nowMillis = 100_000L,
            lastBaseRefreshAtMillis = 95_000L,
            lastContractsRefreshAtMillis = 70_000L,
            includeContracts = true,
            force = false
        )
        assertFalse(contractsOnly.refreshBaseWorkspace)
        assertTrue(contractsOnly.refreshContracts)

        val forced = supplierWorkspaceRefreshDecision(
            nowMillis = 100_000L,
            lastBaseRefreshAtMillis = 99_999L,
            lastContractsRefreshAtMillis = 99_999L,
            includeContracts = true,
            force = true
        )
        assertTrue(forced.refreshBaseWorkspace)
        assertTrue(forced.refreshContracts)
    }

    @Test
    fun supplierDeskPriceValidityRejectsZeroAndNonFiniteLegacyValues() {
        assertFalse(PriceDataModel("0", "KZT", "supplier-1").hasPositiveSupplierDeskPrice())
        assertFalse(PriceDataModel("Infinity", "KZT", "supplier-1").hasPositiveSupplierDeskPrice())
        assertTrue(PriceDataModel("125.50", "KZT", "supplier-1").hasPositiveSupplierDeskPrice())
    }

    @Test
    fun supplierPriceBookKeepsOneFreshActiveRowPerStoreSupplierAndProduct() {
        fun price(
            id: String,
            updatedAtMillis: Long,
            isActive: Boolean = true
        ) = SupplierGoodsPriceDataModel(
            id = id,
            storeId = "store-1",
            supplierId = "supplier-1",
            goodsItemId = "goods-1",
            supplyPrice = PriceDataModel(
                price = if (id == "new") "120" else "100",
                currency = "KZT",
                supplierId = "supplier-1"
            ),
            updatedAtMillis = updatedAtMillis,
            isActive = isActive
        )

        val normalized = listOf(
            price(id = "old", updatedAtMillis = 10L),
            price(id = "new", updatedAtMillis = 20L),
            price(id = "inactive-newest", updatedAtMillis = 30L, isActive = false)
        ).normalizedSupplierGoodsPriceBook()

        assertEquals(1, normalized.size)
        assertEquals("new", normalized.single().id)
        assertEquals("120", normalized.single().supplyPrice.price)
    }

    @Test
    fun supplierPriceBookUpsertReplacesCompositeIdentityEvenWhenServerIdChanges() {
        val existing = SupplierGoodsPriceDataModel(
            id = "old-id",
            storeId = " STORE-1 ",
            supplierId = "supplier-1",
            goodsItemId = "goods-1",
            supplyPrice = PriceDataModel("100", "KZT", "supplier-1"),
            updatedAtMillis = 10L
        )
        val replacement = existing.copy(
            id = "new-id",
            storeId = "store-1",
            supplyPrice = PriceDataModel("125", "KZT", "supplier-1"),
            updatedAtMillis = 20L
        )

        val updated = listOf(existing).upsertSupplierGoodsPriceByIdentity(replacement)

        assertEquals(1, updated.size)
        assertEquals("new-id", updated.single().id)
        assertEquals("125", updated.single().supplyPrice.price)
    }

    @Test
    fun supplierPriceBookFallbackIdentityNormalizesIdsButKeepsMalformedRowsDistinct() {
        val incompleteWithId = SupplierGoodsPriceDataModel(
            id = " PRICE-ROW-1 ",
            storeId = "",
            supplierId = "supplier-1",
            goodsItemId = "",
            supplyPrice = PriceDataModel("100", "KZT", "supplier-1")
        )
        assertEquals(
            "id:price-row-1",
            incompleteWithId.supplierPriceBookIdentity()
        )

        val firstMalformed = incompleteWithId.copy(
            id = "",
            supplyPrice = PriceDataModel("100", "KZT", "supplier-1"),
            createdAtMillis = 10L,
            updatedAtMillis = 20L
        )
        val secondMalformed = firstMalformed.copy(
            supplyPrice = PriceDataModel("200", "KZT", "supplier-1")
        )

        assertFalse(
            firstMalformed.supplierPriceBookIdentity() == secondMalformed.supplierPriceBookIdentity()
        )
        assertEquals(
            2,
            listOf(firstMalformed, secondMalformed).normalizedSupplierGoodsPriceBook().size
        )
    }

    @Test
    fun relatedNavigationStateValuesAreAppliedAtomically() = runTest {
        val host = object : StateHost() {}

        host.setState("unrelated" to "preserved")
        host.setStates(
            "search" to "milk",
            "filter" to "attention",
            "sort" to "recent"
        )

        assertEquals(
            mapOf(
                "unrelated" to "preserved",
                "search" to "milk",
                "filter" to "attention",
                "sort" to "recent"
            ),
            host.state.value
        )
    }

    @Test
    fun supplierActionPredicateIsCanonicalAcrossPackedAndClosedOrders() {
        assertTrue(
            SupplierOrderStatusDataModel.Sent.needsSupplierActionForSupplierDesk(
                hasResponseGaps = false
            )
        )
        assertFalse(
            SupplierOrderStatusDataModel.Packed.needsSupplierActionForSupplierDesk(
                hasResponseGaps = false
            )
        )
        assertTrue(
            SupplierOrderStatusDataModel.Packed.needsSupplierActionForSupplierDesk(
                hasResponseGaps = true
            )
        )
        assertFalse(
            SupplierOrderStatusDataModel.Delivered.needsSupplierActionForSupplierDesk(
                hasResponseGaps = true
            )
        )
        assertFalse(
            SupplierOrderStatusDataModel.Cancelled.needsSupplierActionForSupplierDesk(
                hasResponseGaps = true
            )
        )
    }

    @Test
    fun supplierOfferRelationshipKeyNormalizesIdsAndRejectsIncompleteRelationships() {
        assertEquals(
            "store-1|supplier-1|goods-1",
            supplierGoodsOfferRelationshipKey(
                storeId = " STORE-1 ",
                supplierId = " Supplier-1 ",
                goodsItemId = " Goods-1 "
            )
        )
        assertEquals(
            null,
            supplierGoodsOfferRelationshipKey(
                storeId = "store-1",
                supplierId = "",
                goodsItemId = "goods-1"
            )
        )
    }

    @Test
    fun supplierOfferPriceGapCountCombinesMissingAndLegacyInvalidOffersWithoutDoubleCounting() {
        val storeId = "store-1"
        val supplierId = "supplier-1"
        val firstKey = requireNotNull(
            supplierGoodsOfferRelationshipKey(storeId, supplierId, "goods-1")
        )
        val secondKey = requireNotNull(
            supplierGoodsOfferRelationshipKey(storeId, supplierId, "goods-2")
        )
        val prices = listOf(
            SupplierGoodsPriceDataModel(
                id = "valid",
                storeId = storeId,
                supplierId = supplierId,
                goodsItemId = "goods-1",
                supplyPrice = PriceDataModel("1250", "KZT", supplierId),
                updatedAtMillis = 10L
            ),
            SupplierGoodsPriceDataModel(
                id = "legacy-zero",
                storeId = storeId,
                supplierId = supplierId,
                goodsItemId = "goods-2",
                supplyPrice = PriceDataModel("0", "KZT", supplierId),
                updatedAtMillis = 10L
            ),
            SupplierGoodsPriceDataModel(
                id = "malformed",
                storeId = "",
                supplierId = supplierId,
                goodsItemId = "",
                supplyPrice = PriceDataModel("0", "KZT", supplierId),
                updatedAtMillis = 10L
            )
        )

        assertEquals(
            2,
            supplierGoodsOfferPriceGapCount(
                requiredRelationshipKeys = setOf(firstKey, secondKey),
                prices = prices
            )
        )
    }

    @Test
    fun supplierDashboardPartnerPayloadKeepsBackwardCompatibleDefaults() {
        val decoded = Json.decodeFromString(
            SupplierDashboardPartnerDataModel.serializer(),
            """{"storeId":"store-1","orderCount":3}"""
        )

        assertEquals("store-1", decoded.storeId)
        assertEquals(3, decoded.orderCount)
        assertEquals(0, decoded.actionRequiredOrderCount)
        assertEquals(0, decoded.readyToPackOrderCount)
        assertEquals(0, decoded.savedOfferCount)
        assertEquals(0, decoded.validOfferCount)
        assertEquals(0, decoded.priceGapCount)
    }


    @Test
    fun supplierContractsBlockSupplyOnlyForLivePendingMatchingScope() {
        val partnership = SupplierPartnershipContractDataModel(
            id = "partnership",
            status = SUPPLIER_CONTRACT_STATUS_PENDING_STORE,
            scopeType = SUPPLIER_CONTRACT_SCOPE_PARTNERSHIP
        )
        val goodsScoped = SupplierPartnershipContractDataModel(
            id = "goods",
            status = SUPPLIER_CONTRACT_STATUS_PENDING_SUPPLIER,
            scopeType = SUPPLIER_CONTRACT_SCOPE_GOODS_GROUP,
            goodsItemIds = listOf(" GOODS-1 ", "goods-2")
        )
        val malformedEmptyScope = goodsScoped.copy(
            id = "empty",
            goodsItemIds = emptyList()
        )

        assertTrue(partnership.blocksSupplierSupplyForGoods(listOf("anything")))
        assertTrue(goodsScoped.blocksSupplierSupplyForGoods(listOf("goods-1")))
        assertTrue(goodsScoped.blocksSupplierSupplyForGoods(listOf("GOODS-2")))
        assertFalse(goodsScoped.blocksSupplierSupplyForGoods(listOf("goods-3")))
        assertFalse(malformedEmptyScope.blocksSupplierSupplyForGoods(listOf("goods-1")))
        assertFalse(malformedEmptyScope.coversSupplierSupplyGoods(listOf("goods-1")))
        assertTrue(goodsScoped.coversSupplierSupplyGoods(listOf("goods-1")))
        assertTrue(goodsScoped.copy(status = SUPPLIER_CONTRACT_STATUS_ACTIVE).coversSupplierSupplyGoods(listOf("goods-1")))
        assertTrue(partnership.blocksSupplierSupplyForGoods(emptyList()))
        assertTrue(partnership.coversSupplierSupplyGoods(emptyList()))
        assertFalse(goodsScoped.copy(status = SUPPLIER_CONTRACT_STATUS_ACTIVE).blocksSupplierSupplyForGoods(listOf("goods-1")))
        assertFalse(goodsScoped.copy(status = SUPPLIER_CONTRACT_STATUS_DECLINED).blocksSupplierSupplyForGoods(listOf("goods-1")))
        assertFalse(
            goodsScoped.copy(
                status = SUPPLIER_CONTRACT_STATUS_ARCHIVED,
                isActive = false
            ).blocksSupplierSupplyForGoods(listOf("goods-1"))
        )
    }

    @Test
    fun supplierContractAcceptanceAndLifecycleActionsFollowTheCurrentRevisionState() {
        val waitingForStore = SupplierPartnershipContractDataModel(
            status = SUPPLIER_CONTRACT_STATUS_PENDING_STORE
        )
        val waitingForSupplier = SupplierPartnershipContractDataModel(
            status = SUPPLIER_CONTRACT_STATUS_PENDING_SUPPLIER
        )
        val active = SupplierPartnershipContractDataModel(
            status = SUPPLIER_CONTRACT_STATUS_ACTIVE
        )
        val declined = SupplierPartnershipContractDataModel(
            status = SUPPLIER_CONTRACT_STATUS_DECLINED
        )

        assertTrue(waitingForStore.requiresAcceptanceFrom(SUPPLIER_CONTRACT_SIDE_STORE))
        assertFalse(waitingForStore.requiresAcceptanceFrom(SUPPLIER_CONTRACT_SIDE_SUPPLIER))
        assertTrue(waitingForStore.waitsForOtherContractSide(SUPPLIER_CONTRACT_SIDE_SUPPLIER))
        assertTrue(waitingForSupplier.requiresAcceptanceFrom(SUPPLIER_CONTRACT_SIDE_SUPPLIER))
        assertTrue(waitingForSupplier.waitsForOtherContractSide(SUPPLIER_CONTRACT_SIDE_STORE))
        assertTrue(waitingForSupplier.canBeDeclinedByContractParty())
        assertFalse(waitingForSupplier.canBeArchivedByContractParty())
        assertFalse(active.canBeDeclinedByContractParty())
        assertTrue(active.canBeArchivedByContractParty())
        assertTrue(declined.canBeArchivedByContractParty())
    }

    @Test
    fun scopedSupplierContractReadsReplaceOnlyTheirOwnRelationship() {
        val oldScoped = SupplierPartnershipContractDataModel(
            id = "contract-a",
            storeId = "store-a",
            supplierId = "supplier-a",
            revision = 1,
            updatedAtMillis = 10L
        )
        val unrelated = SupplierPartnershipContractDataModel(
            id = "contract-b",
            storeId = "store-b",
            supplierId = "supplier-b",
            revision = 2,
            updatedAtMillis = 20L
        )
        val newerScoped = oldScoped.copy(revision = 3, updatedAtMillis = 30L)

        val merged = listOf(oldScoped, unrelated).mergedWithSupplierContractRead(
            incoming = listOf(newerScoped),
            storeId = " STORE-A ",
            supplierId = " SUPPLIER-A "
        )

        assertEquals(setOf("contract-a", "contract-b"), merged.map { it.id }.toSet())
        assertEquals(3, merged.single { it.id == "contract-a" }.revision)
        assertEquals(2, merged.single { it.id == "contract-b" }.revision)

        val scopedEmpty = merged.mergedWithSupplierContractRead(
            incoming = emptyList(),
            storeId = "store-a",
            supplierId = "supplier-a"
        )
        assertEquals(listOf("contract-b"), scopedEmpty.map { it.id })

        val fullReplacement = merged.mergedWithSupplierContractRead(
            incoming = listOf(newerScoped),
            storeId = null,
            supplierId = null
        )
        assertEquals(listOf("contract-a"), fullReplacement.map { it.id })
    }

    @Test
    fun supplierContractReadMergePrefersNewestRevisionAndThenNewestTimestamp() {
        val base = SupplierPartnershipContractDataModel(
            id = "same-contract",
            revision = 2,
            updatedAtMillis = 100L
        )
        val newerTimestamp = base.copy(updatedAtMillis = 200L)
        val newerRevision = base.copy(revision = 3, updatedAtMillis = 50L)

        val merged = emptyList<SupplierPartnershipContractDataModel>()
            .mergedWithSupplierContractRead(
                incoming = listOf(base, newerTimestamp, newerRevision)
            )

        assertEquals(1, merged.size)
        assertEquals(3, merged.single().revision)
        assertEquals(50L, merged.single().updatedAtMillis)
    }

    @Test
    fun supplierContractMutationResponseCannotRollBackANewerRealtimeRevision() {
        val realtimeRevision = SupplierPartnershipContractDataModel(
            id = "contract-1",
            revision = 4,
            updatedAtMillis = 400L
        )
        val lateMutationResponse = realtimeRevision.copy(
            revision = 3,
            updatedAtMillis = 500L
        )

        val merged = listOf(realtimeRevision)
            .upsertSupplierContractByRevision(lateMutationResponse)

        assertEquals(1, merged.size)
        assertEquals(4, merged.single().revision)
        assertEquals(400L, merged.single().updatedAtMillis)
    }

    @Test
    fun supplierContractLifecycleRequestCarriesTheExactRevision() {
        val request = SupplierContractRevisionActionRequestDataModel(
            contractId = "contract-1",
            revision = 7
        )
        val encoded = Json.encodeToString(
            SupplierContractRevisionActionRequestDataModel.serializer(),
            request
        )
        val decoded = Json.decodeFromString(
            SupplierContractRevisionActionRequestDataModel.serializer(),
            encoded
        )

        assertEquals(request, decoded)
        assertTrue("\"revision\":7" in encoded)
    }

    @Test
    fun realtimeSupplierUpdatesRespectWorkspaceScopeWithoutRefreshingUnrelatedStores() {
        assertTrue(
            realtimeUpdateIsRelevantToCurrentContext(
                entity = "suppliercontracts",
                updateStoreId = null,
                activeStoreId = null,
                appMode = APP_MODE_STORE
            )
        )
        assertTrue(
            realtimeUpdateIsRelevantToCurrentContext(
                entity = "stock",
                updateStoreId = " STORE-1 ",
                activeStoreId = "store-1",
                appMode = APP_MODE_STORE
            )
        )
        assertFalse(
            realtimeUpdateIsRelevantToCurrentContext(
                entity = "stock",
                updateStoreId = "store-2",
                activeStoreId = "store-1",
                appMode = APP_MODE_STORE
            )
        )
        assertTrue(
            realtimeUpdateIsRelevantToCurrentContext(
                entity = "supplierContracts/upsert",
                updateStoreId = "partner-store-2",
                activeStoreId = "store-1",
                appMode = APP_MODE_SUPPLIER
            )
        )
        assertFalse(
            realtimeUpdateIsRelevantToCurrentContext(
                entity = "stock",
                updateStoreId = "partner-store-2",
                activeStoreId = "store-1",
                appMode = APP_MODE_SUPPLIER
            )
        )
    }

    @Test
    fun supplierOrderStatusOutcomeReportsPartialAndSkippedIdsDeterministically() {
        val outcome = SupplierOrderStatusUpdateOutcome(
            requestedOrderIds = listOf("order-1", " ORDER-2 ", "order-3"),
            updatedOrderIds = listOf("ORDER-2", "order-3"),
            negative = false
        )

        assertTrue(outcome.partial)
        assertEquals(3, outcome.requestedCount)
        assertEquals(2, outcome.updatedCount)
        assertEquals(1, outcome.skippedCount)
        assertEquals(listOf("order-1"), outcome.skippedOrderIds)

        val failed = outcome.copy(updatedOrderIds = emptyList(), negative = true)
        assertFalse(failed.partial)
        assertEquals(3, failed.skippedCount)
    }

    @Test
    fun supplierDispatchPayloadKeepsBackwardCompatibleTrackingDefaults() {
        val decoded = Json.decodeFromString(
            SupplierDashboardDispatchRunDataModel.serializer(),
            """{"runId":"run-1","orderIds":["order-1"]}"""
        )

        assertEquals(listOf("order-1"), decoded.orderIds)
        assertTrue(decoded.inDeliveryOrderIds.isEmpty())
        assertTrue(decoded.issueOrderIds.isEmpty())
    }

}
