package kz.aita

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class MarketPublicationReadFenceTest {
    private val branch = "10000000-0000-0000-0000-000000000001"
    private val shop = "10000000-0000-0000-0000-000000000002"
    private val product = "10000000-0000-0000-0000-000000000003"
    private val item = "10000000-0000-0000-0000-000000000004"
    private val location = "10000000-0000-0000-0000-000000000005"
    private class Owner : MarketAccountScope {
        var active = true
        override val accountId = "seller"
        override val generation = 3L
        override fun isCurrent() = active
    }
    private fun draft() = MarketPublicationDashboard(MarketStorefront(shop, branchStoreId = branch))
    private fun published() = MarketPublicationDashboard(
        MarketStorefront(shop, "Shop", "Almaty", "Pickup", published = true, revision = 1, branchStoreId = branch),
        listOf(MarketListing(product, shop, item, "Milk", gtin = "04006381333931", published = true, revision = 1)),
        listOf(location), listOf(MarketPublicationLocation(location, listOf(LocalizedStringDataModel("ru", "Склад")), "Address", true)))

    @Test fun unpublishedDraftAndMigratedPublicIdentityRemainEditable() {
        assertTrue(draft().isValidPublicationDashboard(branch))
        assertTrue(published().isValidPublicationDashboard(branch))
        // A removed physical location remains selected so the owner can explicitly remove it.
        assertTrue(published().copy(availableLocations = emptyList()).isValidPublicationDashboard(branch))
        assertFalse(published().isValidPublicationDashboard(shop))
    }
    @Test fun mixedStoreAndDuplicateProductResponsesAreRejected() {
        val value = published(); val listing = value.listings.single()
        assertFalse(value.copy(listings = listOf(listing.copy(storeId = branch))).isValidPublicationDashboard(branch))
        assertFalse(value.copy(listings = listOf(listing, listing.copy(id = location))).isValidPublicationDashboard(branch))
        assertFalse(value.copy(listings = listOf(listing, listing.copy(goodsItemId = location))).isValidPublicationDashboard(branch))
        assertFalse(value.copy(listings = listOf(listing.copy(gtin = "4006381333932"))).isValidPublicationDashboard(branch))
        assertFalse(value.copy(locationStoreIds = listOf(location, location)).isValidPublicationDashboard(branch))
    }
    @Test fun invalidAndOversizedDraftFieldsCannotEnterNavigationStorage() {
        val value = published()
        assertFalse(value.copy(storefront = value.storefront.copy(displayName = "x".repeat(121))).isValidPublicationDashboard(branch))
        assertFalse(value.copy(storefront = value.storefront.copy(city = " ")).isValidPublicationDashboard(branch))
        assertFalse(value.copy(storefront = value.storefront.copy(pickupNote = "hidden\u0000")).isValidPublicationDashboard(branch))
        assertFalse(value.copy(storefront = value.storefront.copy(revision = -1)).isValidPublicationDashboard(branch))
        assertFalse(value.copy(listings = value.listings.map { it.copy(revision = 0) }).isValidPublicationDashboard(branch))
        assertFalse(value.copy(availableLocations = value.availableLocations + value.availableLocations).isValidPublicationDashboard(branch))
    }
    @Test fun staleSessionNeverReadsAndSessionChangeNeverAcceptsResponse() = runTest {
        val owner = Owner().apply { active = false }
        var calls = 0
        assertTrue(readOwnedMarketPublication(owner, branch) { calls++; ResponseDataModel(message = null, negative = false, payload = published()) }.negative)
        assertEquals(0, calls)
        owner.active = true
        val result = readOwnedMarketPublication(owner, branch) { owner.active = false; ResponseDataModel(message = null, negative = false, payload = published()) }
        assertTrue(result.negative); assertNull(result.payload)
    }
    @Test fun successfulHttpStatusAndMatchingBranchAreBothRequired() = runTest {
        val owner = Owner()
        for (status in listOf(0, 202, 500)) {
            val result = readOwnedMarketPublication(owner, branch) { ResponseDataModel(message = null, negative = false, payload = published(), httpStatusCode = status) }
            assertTrue(result.negative); assertNull(result.payload)
        }
        val wrong = readOwnedMarketPublication(owner, shop) { ResponseDataModel(message = null, negative = false, payload = published(), httpStatusCode = 200) }
        assertTrue(wrong.negative); assertNull(wrong.payload)
        val result = readOwnedMarketPublication(owner, branch) { ResponseDataModel(message = null, negative = false, payload = published(), httpStatusCode = 200) }
        assertFalse(result.negative); assertEquals(published(), result.payload)
    }
    @Test fun deniedAndTransportResponsesCannotSmuggleAnEditorPayload() = runTest {
        for (denied in listOf(
            ResponseDataModel(message = null, payload = published(), negative = true, httpStatusCode = 403),
            ResponseDataModel(message = null, negative = false, payload = published(), httpStatusCode = 200, transportFailure = true)
        )) {
            val result = readOwnedMarketPublication(Owner(), branch) { denied }
            assertTrue(result.negative); assertNull(result.payload)
        }
    }
    @Test fun cancellationIsNotReportedAsSuccessfulSaveOrNetworkError() = runTest {
        assertFailsWith<CancellationException> { readOwnedMarketPublication(Owner(), branch) { throw CancellationException("scope ended") } }
    }
}
