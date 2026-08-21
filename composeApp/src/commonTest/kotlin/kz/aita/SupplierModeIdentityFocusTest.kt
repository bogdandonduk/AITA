package kz.aita

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SupplierModeIdentityFocusTest {
    @Test
    fun focusedResourceListsKeepOnlyTheSelectedSupplier() {
        val orders = listOf(
            SupplierOrderDataModel(id = "one", storeId = "store-1", supplierId = "supplier-a"),
            SupplierOrderDataModel(id = "two", storeId = "store-1", supplierId = "supplier-b")
        )
        val prices = listOf(
            SupplierGoodsPriceDataModel(
                id = "one",
                storeId = "store-1",
                supplierId = "supplier-a",
                goodsItemId = "goods-1",
                supplyPrice = PriceDataModel("1", "KZT", "supplier-a")
            ),
            SupplierGoodsPriceDataModel(
                id = "two",
                storeId = "store-1",
                supplierId = "supplier-b",
                goodsItemId = "goods-2",
                supplyPrice = PriceDataModel("1", "KZT", "supplier-b")
            )
        )
        val contracts = listOf(
            SupplierPartnershipContractDataModel(id = "one", supplierId = "supplier-a"),
            SupplierPartnershipContractDataModel(id = "two", supplierId = "supplier-b")
        )

        assertEquals(listOf("one"), orders.supplierOrdersForIdentity("SUPPLIER-A").map { it.id })
        assertEquals(listOf("one"), prices.supplierPricesForIdentity(" supplier-a ").map { it.id })
        assertEquals(listOf("one"), contracts.supplierContractsForIdentity("supplier-a").map { it.id })
        assertEquals(2, orders.supplierOrdersForIdentity(null).size)
    }

    @Test
    fun profileListFocusDoesNotGuessWhenCombined() {
        val profiles = listOf(
            SupplierDataModel(id = "supplier-a"),
            SupplierDataModel(id = "supplier-b")
        )

        assertEquals(2, profiles.supplierProfilesForIdentity(null).size)
        assertEquals("supplier-b", profiles.supplierProfilesForIdentity("Supplier-B").single().id)
        assertTrue(profiles.supplierProfilesForIdentity("missing").isEmpty())
        assertNull(normalizeSupplierProfileIdentityId("   "))
    }
    @Test
    fun combinedPresentationFindsTheCorrectSupplierTitle() {
        val presentation = SupplierIdentityPresentationUiModel(
            title = "All",
            subtitle = "Combined",
            activeSupplierId = null,
            options = listOf(
                SupplierIdentityOptionUiModel(null, "All", "", selected = true),
                SupplierIdentityOptionUiModel("Supplier-A", "North warehouse", "", selected = false),
                SupplierIdentityOptionUiModel("supplier-b", "South warehouse", "", selected = false)
            ),
            combined = true,
            profileCount = 2
        )

        assertEquals("North warehouse", presentation.titleForSupplierIdentity(" supplier-a "))
        assertEquals("", presentation.titleForSupplierIdentity("missing"))
    }

    @Test
    fun loadedSupplierProfilesAreAuthoritativeOverDashboardSnapshots() {
        assertTrue(supplierIdentityPresentationAllowsDashboardFallback(localProfilesLoaded = false))
        assertTrue(!supplierIdentityPresentationAllowsDashboardFallback(localProfilesLoaded = true))
    }

}
