package kz.aita

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SupplierModeProfilesTest {
    private fun profile(id: String = "supplier-a", name: String = "Alpha") = SupplierDataModel(
        id = id,
        userIds = listOf("user-a"),
        name = listOf(LocalizedStringDataModel("main", name)),
        phoneNumbers = listOf("77001112233"),
        isActive = true
    )

    @Test
    fun deleteRequiresCompleteUsageSnapshot() {
        val item = SupplierProfileWorkspaceItemUiModel(
            supplier = profile(),
            title = "Alpha",
            contactText = "77001112233",
            orderCount = 0,
            openOrderCount = 0,
            catalogSkuCount = 0,
            savedOfferCount = 0,
            stockBatchCount = 0,
            commercialHistoryCount = 0,
            partnerCount = 0,
            activeContractCount = 0,
            pendingContractCount = 0,
            usageLoaded = false,
            usageCheckFailed = false,
            readinessIssues = emptySet(),
            selected = false
        )
        assertFalse(item.canDeleteFromLoadedState)
        assertTrue(item.copy(usageLoaded = true).canDeleteFromLoadedState)
    }

    @Test
    fun anyCommercialHistoryBlocksDelete() {
        val base = SupplierProfileWorkspaceItemUiModel(
            supplier = profile(),
            title = "Alpha",
            contactText = "77001112233",
            orderCount = 0,
            openOrderCount = 0,
            catalogSkuCount = 0,
            savedOfferCount = 0,
            stockBatchCount = 0,
            commercialHistoryCount = 0,
            partnerCount = 0,
            activeContractCount = 0,
            pendingContractCount = 0,
            usageLoaded = true,
            usageCheckFailed = false,
            readinessIssues = emptySet(),
            selected = false
        )
        assertFalse(base.copy(commercialHistoryCount = 1).canDeleteFromLoadedState)
        assertFalse(base.copy(orderCount = 1).canDeleteFromLoadedState)
        assertFalse(base.copy(savedOfferCount = 1).canDeleteFromLoadedState)
        assertFalse(base.copy(stockBatchCount = 1).canDeleteFromLoadedState)
        assertFalse(base.copy(activeContractCount = 1).canDeleteFromLoadedState)
        assertFalse(base.copy(pendingContractCount = 1).canDeleteFromLoadedState)
    }

    @Test
    fun optimisticProfileSnapshotPreservesUsageCounts() {
        val initial = SupplierModeDashboardDataModel(
            supplierIds = listOf("supplier-a"),
            supplierProfiles = listOf(
                SupplierDashboardProfileDataModel(
                    supplierId = "supplier-a",
                    name = listOf(LocalizedStringDataModel("main", "Old")),
                    orderCount = 4,
                    savedOfferCount = 3,
                    stockBatchCount = 5,
                    commercialHistoryCount = 9,
                    activeContractCount = 2
                )
            )
        )
        val updated = initial.withSupplierProfileSnapshot(profile(name = "New"))
        val row = updated.supplierProfiles.single()
        assertEquals("New", row.name.first().value)
        assertEquals(4, row.orderCount)
        assertEquals(3, row.savedOfferCount)
        assertEquals(5, row.stockBatchCount)
        assertEquals(9, row.commercialHistoryCount)
        assertEquals(2, row.activeContractCount)
    }

    @Test
    fun deletingSnapshotRemovesOnlySelectedIdentity() {
        val dashboard = SupplierModeDashboardDataModel(
            supplierIds = listOf("supplier-a", "supplier-b"),
            supplierProfiles = listOf(
                SupplierDashboardProfileDataModel(supplierId = "supplier-a"),
                SupplierDashboardProfileDataModel(supplierId = "supplier-b")
            )
        )
        val next = dashboard.withoutSupplierProfileSnapshot(" SUPPLIER-A ")
        assertEquals(listOf("supplier-b"), next.supplierIds)
        assertEquals(listOf("supplier-b"), next.supplierProfiles.map { it.supplierId })
    }
    @Test
    fun failedUsageCheckNeverEnablesDelete() {
        val item = SupplierProfileWorkspaceItemUiModel(
            supplier = profile(),
            title = "Alpha",
            contactText = "77001112233",
            orderCount = 0,
            openOrderCount = 0,
            catalogSkuCount = 0,
            savedOfferCount = 0,
            stockBatchCount = 0,
            commercialHistoryCount = 0,
            partnerCount = 0,
            activeContractCount = 0,
            pendingContractCount = 0,
            usageLoaded = false,
            usageCheckFailed = true,
            readinessIssues = emptySet(),
            selected = false
        )
        assertFalse(item.canDeleteFromLoadedState)
        assertTrue(item.usageCheckFailed)
    }

}
