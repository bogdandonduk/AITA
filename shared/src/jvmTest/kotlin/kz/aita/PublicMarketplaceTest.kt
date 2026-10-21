package kz.aita

import kotlin.test.*

class PublicMarketplaceTest {
    private val request = MarketDiscoveryRequest(MarketDiscoveryQuery())
    private fun result() = PublicMarketDiscovery(MarketDiscoveryResult(request.query, 40, MarketPage(checkedAtMillis = 100),
        0, 0, "a".repeat(64), emptyList()))
    @Test fun publicCatalogueDoesNotAcceptAnAccountBoundReplyOrSavedOnlySearch() {
        assertNotNull(result().validated(request, null))
        assertNull(result().copy(discovery = result().discovery.copy(accountId = "user")).validated(request, null))
        assertNull(result().validated(request.copy(query = MarketDiscoveryQuery(savedOnly = true)), null))
        assertNull(result().copy(publicVersion = 0).validated(request, null))
        assertNull(result().validated(request.copy(limit = 400), null))
    }
    @Test fun onlyTheExplicitPublicReadEndpointSkipsAuthentication() {
        assertFalse(cloudEndpointRequiresAuthentication("market/public/discovery"))
        listOf("market/discovery", "market/seller", "market/saved", "market/public/seller").forEach { assertTrue(cloudEndpointRequiresAuthentication(it)) }
        assertFalse(storeSubscriptionRequiredForEndpoint("workshifts/reports"))
        assertFalse(storeSubscriptionRequiredForEndpoint("workshifts/reports/shift"))
        assertTrue(storeSubscriptionRequiredForEndpoint("workshifts/start"))
    }
}
