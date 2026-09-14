package kz.aita

import kotlin.test.*

class MarketDiscoveryPolicyTest {
    private val account = "00000000-0000-0000-0000-000000000099"
    private fun id(n: Int) = "00000000-0000-0000-0000-${n.toString().padStart(12, '0')}"
    private fun category(n: Int, vararg parents: Int) = MarketCategory(id(n), listOf(LocalizedStringDataModel("en", "Category $n")), parents.map(::id))
    private val catalogue get() = MarketCategoryCatalogue("a".repeat(64), listOf(category(1), category(2, 1), category(3, 1, 2), category(4)))
    private fun offer(n: Int = 10, saved: Boolean = false) = MarketOffer(id(n), MarketStorefront(id(20), "Shop", "Astana", "Pickup", published = true, revision = 1),
        "Product", categoryIds = listOf(id(3)), saved = saved, checkedAtMillis = 100, sourceUpdatedAtMillis = 10)
    private fun result(request: MarketDiscoveryRequest = MarketDiscoveryRequest(MarketDiscoveryQuery())) = MarketDiscoveryResult(
        requireNotNull(request.query.normalizedDiscoveryQuery()), request.limit, MarketPage(listOf(offer(saved = request.query.savedOnly)), checkedAtMillis = 100),
        1, 1, catalogue.version, catalogue.categories, account,
        offer().storefront.takeIf { request.query.storefrontId != null })

    @Test fun whitespaceIsCanonicalAcrossSearchCityAndLineBreaks() {
        val query = MarketDiscoveryQuery("  green\t tea\n\u2003bag\u00a0", "  Astana  ").normalizedDiscoveryQuery()
        assertEquals("green tea bag", query?.text); assertEquals("Astana", query?.city)
    }
    @Test fun storeScopeDoesNotPretendCityFilterStillApplies() {
        assertEquals("", MarketDiscoveryQuery(city = "Other city", storefrontId = id(20)).normalizedDiscoveryQuery()?.city)
    }
    @Test fun explicitIdsAreValidatedAndCaseNormalized() {
        assertEquals("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", MarketDiscoveryQuery(categoryId = "AAAAAAAA-AAAA-AAAA-AAAA-AAAAAAAAAAAA").normalizedDiscoveryQuery()?.categoryId)
        listOf("", "invalid", " ${id(1)}", "0-0-0-0-1").forEach { assertNull(MarketDiscoveryQuery(categoryId = it).normalizedDiscoveryQuery()) }
    }
    @Test fun tooManyTermsLongInputsAndControlCharactersAreRejected() {
        listOf("x ".repeat(13), "x".repeat(121), "green\u0000tea", "a\u007fb").forEach { assertNull(MarketDiscoveryQuery(text = it).normalizedDiscoveryQuery()) }
        assertNotNull(MarketDiscoveryQuery(text = (1..12).joinToString(" ")).normalizedDiscoveryQuery())
        assertNull(MarketDiscoveryQuery(city = "a".repeat(101)).normalizedDiscoveryQuery())
    }
    @Test fun sortIsAnAllowListNotSqlInput() {
        assertNull(MarketDiscoveryQuery(sort = "price").normalizedDiscoveryQuery())
        assertNull(MarketDiscoveryQuery(sort = "title;DROP TABLE users").normalizedDiscoveryQuery())
        assertEquals(MARKET_DISCOVERY_TITLE, MarketDiscoveryQuery(sort = MARKET_DISCOVERY_TITLE).normalizedDiscoveryQuery()?.sort)
    }
    @Test fun literalSearchSyntaxIsNotAlteredIntoWildcards() {
        val text = "50% _ \\ ' /"
        assertEquals(text, MarketDiscoveryQuery(text).normalizedDiscoveryQuery()?.text)
    }
    @Test fun windowSizeMustBeAnExplicitBoundedPageMultiple() {
        listOf(0, 1, 39, 41, 401, Int.MAX_VALUE).forEach { assertFalse(MarketDiscoveryRequest(MarketDiscoveryQuery(), it).isValidDiscoveryRequest()) }
        listOf(40, 80, 400).forEach { assertTrue(MarketDiscoveryRequest(MarketDiscoveryQuery(), it).isValidDiscoveryRequest()) }
    }
    @Test fun taxonomyVersionMustBeFullLowercaseDigest() {
        assertFalse(MarketDiscoveryRequest(MarketDiscoveryQuery(), knownCategoryVersion = "latest").isValidDiscoveryRequest())
        assertTrue(MarketDiscoveryRequest(MarketDiscoveryQuery(), knownCategoryVersion = catalogue.version).isValidDiscoveryRequest())
    }
    @Test fun ancestorFilterIncludesEveryLevelButNotOtherRoots() {
        val tree = MarketCategoryTree(catalogue.categories)
        assertEquals(setOf(id(1), id(2), id(3)), tree.subtreeIds(id(1)))
        assertEquals(listOf(id(1), id(2), id(3)), tree.pathTo(id(3)).map { it.id })
        assertEquals(listOf(id(2)), tree.childrenOf(id(1)).map { it.id })
        assertEquals(setOf(id(1), id(4)), tree.childrenOf(null).map { it.id }.toSet())
    }
    @Test fun missingParentsAndLegacyTextTagsDoNotBecomeCategories() {
        val leaf = category(3).copy(ancestorIds = listOf("food", id(999), id(1), id(3)))
        val tree = MarketCategoryTree(listOf(category(1), leaf))
        assertEquals(listOf(id(1), id(3)), tree.pathTo(id(3)).map { it.id })
        assertTrue(tree.subtreeIds(id(999)).isEmpty()); assertTrue(tree.pathTo(id(999)).isEmpty())
    }
    @Test fun cyclesBecomeSeparateRootsWithoutLosingTheirChildren() {
        val tree = MarketCategoryTree(listOf(category(1, 2), category(2, 1), category(3, 1)))
        assertEquals(setOf(id(1), id(2)), tree.childrenOf(null).map { it.id }.toSet())
        assertEquals(setOf(id(1), id(3)), tree.subtreeIds(id(1)))
        assertEquals(listOf(id(1), id(3)), tree.pathTo(id(3)).map { it.id })
    }
    @Test fun largeDeepTaxonomyUsesIterativeTraversal() {
        val rows = (1..1000).map { category(it, *if (it == 1) intArrayOf() else intArrayOf(it - 1)) }
        val tree = MarketCategoryTree(rows)
        assertEquals(1000, tree.pathTo(id(1000)).size); assertEquals(1000, tree.subtreeIds(id(1)).size)
    }
    @Test fun firstResponseMustSupplyTaxonomy() {
        val request = MarketDiscoveryRequest(MarketDiscoveryQuery())
        assertNotNull(result().validatedDiscovery(request, null, account))
        assertNull(result().copy(categories = null).validatedDiscovery(request, catalogue, account))
    }
    @Test fun unchangedTaxonomyCanBeReusedButNotInvented() {
        val request = MarketDiscoveryRequest(MarketDiscoveryQuery(), knownCategoryVersion = catalogue.version)
        assertNotNull(result(request).copy(categories = null).validatedDiscovery(request, catalogue, account))
        assertNull(result(request).copy(categories = null).validatedDiscovery(request, null, account))
        assertNull(result(request).copy(categories = null, categoryVersion = "b".repeat(64)).validatedDiscovery(request, catalogue, account))
    }
    @Test fun changedTaxonomyReplacesKnownVersionExplicitly() {
        val request = MarketDiscoveryRequest(MarketDiscoveryQuery(), knownCategoryVersion = "b".repeat(64))
        assertEquals(catalogue.version, result(request).validatedDiscovery(request, null, account)?.catalogue?.version)
    }
    @Test fun wrongAccountScopeFiltersOrWindowNeverPassValidation() {
        val request = MarketDiscoveryRequest(MarketDiscoveryQuery(savedOnly = true, categoryId = id(1)))
        val valid = result(request)
        assertNotNull(valid.validatedDiscovery(request, null, account))
        assertNull(valid.copy(query = valid.query.copy(savedOnly = false)).validatedDiscovery(request, null, account))
        assertNull(valid.copy(query = valid.query.copy(categoryId = null)).validatedDiscovery(request, null, account))
        assertNull(valid.copy(limit = 80).validatedDiscovery(request, null, account))
    }
    @Test fun invalidStatisticsAndTruncatedSuccessAreRejected() {
        val request = MarketDiscoveryRequest(MarketDiscoveryQuery()); val response = result()
        assertNull(response.copy(totalOffers = -1).validatedDiscovery(request, null, account))
        assertNull(response.copy(totalShops = 2).validatedDiscovery(request, null, account))
        assertNull(response.copy(totalOffers = 2).validatedDiscovery(request, null, account))
        assertNull(response.copy(page = response.page.copy(nextId = id(10))).validatedDiscovery(request, null, account))
    }
    @Test fun duplicateOffersAndPrivateShopRowsAreRejected() {
        val request = MarketDiscoveryRequest(MarketDiscoveryQuery()); val response = result()
        assertNull(response.copy(page = response.page.copy(offers = listOf(offer(), offer())), totalOffers = 2).validatedDiscovery(request, null, account))
        assertNull(response.copy(page = response.page.copy(offers = listOf(offer().copy(storefront = offer().storefront.copy(published = false))))).validatedDiscovery(request, null, account))
    }
    @Test fun crossShopOrUnsaveRowsCannotMasqueradeAsFilteredResults() {
        val request = MarketDiscoveryRequest(MarketDiscoveryQuery(storefrontId = id(20), savedOnly = true)); val response = result(request)
        assertNotNull(response.validatedDiscovery(request, null, account))
        assertNull(response.copy(page = response.page.copy(offers = listOf(offer()))).validatedDiscovery(request, null, account))
        assertNull(response.copy(page = response.page.copy(offers = listOf(offer(saved = true).copy(storefront = offer().storefront.copy(storeId = id(21)))))).validatedDiscovery(request, null, account))
    }
    @Test fun unknownCategoryIdentifiersAndMalformedTaxonomyDoNotPublish() {
        val request = MarketDiscoveryRequest(MarketDiscoveryQuery()); val response = result()
        assertNull(response.copy(categories = catalogue.categories + category(1)).validatedDiscovery(request, null, account))
        assertNull(response.copy(page = response.page.copy(offers = listOf(offer().copy(categoryIds = listOf(id(999)))))).validatedDiscovery(request, null, account))
    }
    @Test fun filteredSavedMutationNeverInjectsTheRestOfTheSavedList() {
        val fence = MarketSavedReadFence()
        val window = MarketPage(listOf(offer(saved = true)), checkedAtMillis = 100)
        val ack = MarketPage(listOf(offer(saved = true), offer(11, true)), checkedAtMillis = 101)
        assertEquals(listOf(id(10)), fence.acknowledgeDiscoverySaved(window, ack, true)?.offers?.map { it.id })
        val removed = MarketPage(listOf(offer(11, true)), unavailableSavedCount = 2)
        val next = fence.acknowledgeDiscoverySaved(window, removed, true)
        assertTrue(next!!.offers.isEmpty()); assertEquals(2, next.unavailableSavedCount)
    }
    @Test fun normalCatalogueStaysVisibleWhenOfferIsUnsaved() {
        val fence = MarketSavedReadFence(); val window = MarketPage(listOf(offer(saved = true)))
        val result = fence.acknowledgeDiscoverySaved(window, MarketPage(), false)
        assertEquals(1, result?.offers?.size); assertFalse(result!!.offers.single().saved)
    }
    @Test fun malformedBookmarkAcknowledgementCannotAdvanceFenceOrDropCards() {
        val fence = MarketSavedReadFence(); val revision = fence.capture()
        assertFailsWith<IllegalArgumentException> { fence.acknowledgeDiscoverySaved(MarketPage(listOf(offer())), MarketPage(listOf(offer(saved = false))), true) }
        assertEquals(revision, fence.capture())
    }
    @Test fun lateFilteredReadCannotBringBackAnAcknowledgedUnsave() {
        val fence = MarketSavedReadFence(); val before = fence.capture(); val old = MarketPage(listOf(offer(saved = true)))
        fence.acknowledgeDiscoverySaved(old, MarketPage(), true)
        assertTrue(fence.reconcile(old, before, true).offers.isEmpty())
    }
    @Test fun allPricesRemainEstimatesAndFilterDoesNotRequireKnownStock() {
        val request = MarketDiscoveryRequest(MarketDiscoveryQuery()); val response = result()
        assertNotNull(response.validatedDiscovery(request, null, account)) // unknown price/stock is a valid published offer
    }
    @Test fun wholeWindowUsesTheLastDisplayedIdOnlyWhenMoreMatchesExist() {
        val request = MarketDiscoveryRequest(MarketDiscoveryQuery())
        val offers = (10..49).map { offer(it) }
        val response = result().copy(totalOffers = 41, page = MarketPage(offers, offers.last().id, 100))
        assertNotNull(response.validatedDiscovery(request, null, account))
        assertNull(response.copy(page = response.page.copy(nextId = offers.first().id)).validatedDiscovery(request, null, account))
        assertNull(response.copy(page = response.page.copy(nextId = null)).validatedDiscovery(request, null, account))
        assertNotNull(response.copy(totalOffers = 40, page = response.page.copy(nextId = null)).validatedDiscovery(request, null, account))
    }
    @Test fun selectedCategoryRequiresRealMembershipEvidenceEvenWithMatchingQueryEcho() {
        val request = MarketDiscoveryRequest(MarketDiscoveryQuery(categoryId = id(1)))
        val response = result(request)
        assertNotNull(response.validatedDiscovery(request, null, account))
        assertNull(response.copy(page = response.page.copy(offers = listOf(offer().copy(categoryIds = listOf(id(4)))))).validatedDiscovery(request, null, account))
        assertNull(response.copy(page = response.page.copy(offers = listOf(offer().copy(categoryIds = emptyList())))).validatedDiscovery(request, null, account))
    }
    @Test fun knownAndUnknownPricesCannotBeMixedIntoMalformedEstimates() {
        val request = MarketDiscoveryRequest(MarketDiscoveryQuery())
        val priced = offer().copy(priceMinor = 15000, currencyCode = "KZT", unitId = "piece", pricedAmount = 1.0)
        fun checked(value: MarketOffer) = result().copy(page = result().page.copy(offers = listOf(value))).validatedDiscovery(request, null, account)
        assertNotNull(checked(priced))
        assertNull(checked(priced.copy(priceMinor = -1)))
        assertNull(checked(priced.copy(priceMinor = 1_000_000_000_001)))
        assertNull(checked(priced.copy(currencyCode = "kzt")))
        assertNull(checked(priced.copy(pricedAmount = Double.NaN)))
        assertNull(checked(priced.copy(pricedAmount = 0.0)))
        assertNull(checked(priced.copy(unitId = "")))
        assertNull(checked(priced.copy(priceMinor = null)))
        assertNotNull(checked(offer()))
    }
    @Test fun taxonomyAndOfferPayloadBoundsFailBeforeDisplay() {
        val request = MarketDiscoveryRequest(MarketDiscoveryQuery())
        val response = result()
        assertNull(response.copy(categories = listOf(category(1).copy(name = emptyList()))).validatedDiscovery(request, null, account))
        assertNull(response.copy(categories = listOf(category(1).copy(ancestorIds = List(65) { id(2) }))).validatedDiscovery(request, null, account))
        assertNull(response.copy(categories = (1..MARKET_CATEGORY_MAX_COUNT + 1).map { category(it) }).validatedDiscovery(request, null, account))
        assertNull(response.copy(page = response.page.copy(offers = listOf(offer().copy(title = "x".repeat(181))))).validatedDiscovery(request, null, account))
        assertNull(response.copy(page = response.page.copy(offers = listOf(offer().copy(checkedAtMillis = 99)))).validatedDiscovery(request, null, account))
    }
    @Test fun fullBookmarkReplyBoundsAreValidatedBeforeChangingReadOrdering() {
        val fence = MarketSavedReadFence(); val revision = fence.capture()
        val offers = (10..109).map { offer(it, saved = true) }
        assertFailsWith<IllegalArgumentException> { fence.acknowledgeDiscoverySaved(null, MarketPage(offers, unavailableSavedCount = 1), true) }
        assertEquals(revision, fence.capture())
        assertFailsWith<IllegalArgumentException> { fence.acknowledgeDiscoverySaved(null, MarketPage(listOf(offer(saved = true)), nextId = id(10)), true) }
        assertEquals(revision, fence.capture())
    }

}
