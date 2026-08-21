package kz.aita

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SupplierIdentityFocusTest {
    private fun profile(id: String, active: Boolean = true): SupplierDataModel = SupplierDataModel(
        id = id,
        userIds = listOf("user-1"),
        isActive = active
    )

    @Test
    fun oneProfileIsSelectedAutomatically() {
        assertEquals("supplier-a", resolveSupplierProfileFocus(null, listOf(profile("supplier-a"))))
    }

    @Test
    fun severalProfilesWithoutPreferenceStayCombined() {
        assertNull(
            resolveSupplierProfileFocus(
                preferredSupplierId = null,
                profiles = listOf(profile("supplier-a"), profile("supplier-b"))
            )
        )
    }

    @Test
    fun validPreferenceIsPreservedCaseInsensitively() {
        assertEquals(
            "Supplier-B",
            resolveSupplierProfileFocus(
                preferredSupplierId = " supplier-b ",
                profiles = listOf(profile("supplier-a"), profile("Supplier-B"))
            )
        )
    }

    @Test
    fun unavailablePreferenceFallsBackSafely() {
        assertNull(
            resolveSupplierProfileFocus(
                preferredSupplierId = "deleted",
                profiles = listOf(profile("supplier-a"), profile("supplier-b"))
            )
        )
        assertEquals(
            "supplier-a",
            resolveSupplierProfileFocus(
                preferredSupplierId = "deleted",
                profiles = listOf(profile("supplier-a"))
            )
        )
    }

    @Test
    fun inactiveProfilesAreNeverSelected() {
        assertNull(resolveSupplierProfileFocus("supplier-a", listOf(profile("supplier-a", active = false))))
    }

    @Test
    fun supplierResourceFocusPredicatesUseNormalizedIdentity() {
        val order = SupplierOrderDataModel(storeId = "store-1", supplierId = "Supplier-A")
        val price = SupplierGoodsPriceDataModel(
            storeId = "store-1",
            supplierId = "Supplier-A",
            goodsItemId = "goods-1",
            supplyPrice = PriceDataModel("1", "KZT", "Supplier-A")
        )
        val contract = SupplierPartnershipContractDataModel(supplierId = "Supplier-A")

        assertTrue(order.matchesSupplierProfileFocus(null))
        assertTrue(order.matchesSupplierProfileFocus(" supplier-a "))
        assertTrue(price.matchesSupplierProfileFocus("supplier-a"))
        assertTrue(contract.matchesSupplierProfileFocus("SUPPLIER-A"))
        assertFalse(order.matchesSupplierProfileFocus("supplier-b"))
    }
}
