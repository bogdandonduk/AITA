package kz.aita

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlin.test.*

class MarketBranchPublicationTest {
    private val publicId="00000000-0000-0000-0000-000000000001"
    private val branchId="00000000-0000-0000-0000-000000000002"
    private val locationId="00000000-0000-0000-0000-000000000003"

    @Test fun oldPublicShopStillDecodesAndNewBranchOwnershipRoundTrips() {
        val old=jsonBase.decodeFromString<MarketStorefront>("""{"storeId":"$publicId"}""")
        assertEquals(publicId,old.operatingBranchId)
        val current=MarketStorefront(publicId,"Shop","Astana","Door",published=true,revision=3,branchStoreId=branchId)
        val restored=jsonBase.decodeFromString<MarketStorefront>(jsonBase.encodeToString(current))
        assertEquals(publicId,restored.storeId);assertEquals(branchId,restored.operatingBranchId)
        assertTrue(restored.isValidPublicMarketShop())
        assertFalse(restored.copy(branchStoreId="not-a-branch").isValidPublicMarketShop())
    }
    @Test fun oldEditorDoesNotClearExplicitLocationSelectionByOmittingIt() {
        val shop=MarketStorefront(publicId,branchStoreId=branchId)
        val old=jsonBase.decodeFromString<MarketStorefrontUpdate>("""{"storefront":{"storeId":"$publicId"}}""")
        assertNull(old.locationStoreIds)
        val clear=MarketStorefrontUpdate(shop,emptyList())
        assertEquals(emptyList(),jsonBase.decodeFromString<MarketStorefrontUpdate>(jsonBase.encodeToString(clear)).locationStoreIds)
        val draft=MarketEditorDraft(shop,storefrontDirty=true,locationStoreIds=listOf(locationId))
        assertEquals(draft,jsonBase.decodeFromString<MarketEditorDraft>(jsonBase.encodeToString(draft)))
    }
    @Test fun ownerLocationChoicesNeverEnterPublicStorefrontSerialization() {
        val dashboard=MarketPublicationDashboard(MarketStorefront(publicId,branchStoreId=branchId),
            locationStoreIds=listOf(locationId),availableLocations=listOf(MarketPublicationLocation(locationId,
                listOf(LocalizedStringDataModel("en","Private warehouse name")),"Selected warehouse address",true)))
        val public=jsonBase.encodeToString(dashboard.storefront)
        assertFalse(public.contains("Private warehouse name"));assertFalse(public.contains(locationId))
        assertEquals(dashboard,jsonBase.decodeFromString<MarketPublicationDashboard>(jsonBase.encodeToString(dashboard)))
    }
}
