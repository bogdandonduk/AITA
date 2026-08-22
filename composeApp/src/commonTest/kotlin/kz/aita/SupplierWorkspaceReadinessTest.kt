package kz.aita

import kotlin.test.Test
import kotlin.test.assertEquals

class SupplierWorkspaceReadinessTest {

    private fun profileItem(
        id: String,
        ready: Boolean = true,
        openOrders: Int = 0,
        catalogCount: Int = 1,
        partnerCount: Int = 1,
        agreementCount: Int = 1,
        selected: Boolean = false
    ) = SupplierProfileWorkspaceItemUiModel(
        supplier = SupplierDataModel(id = id),
        title = id,
        contactText = "",
        orderCount = openOrders,
        openOrderCount = openOrders,
        catalogSkuCount = catalogCount,
        savedOfferCount = 0,
        stockBatchCount = 0,
        commercialHistoryCount = 0,
        partnerCount = partnerCount,
        activeContractCount = agreementCount,
        pendingContractCount = 0,
        usageLoaded = true,
        usageCheckFailed = false,
        readinessIssues = if (ready) emptySet() else setOf(SupplierProfileReadinessIssue.MissingContact),
        selected = selected
    )

    @Test
    fun workspaceStartsWithProfileCreation() {
        val summary = buildSupplierWorkspaceReadiness(emptyList(), null)

        assertEquals(SupplierWorkspaceReadinessNextStep.CreateProfile, summary.nextStep)
        assertEquals(0, summary.readinessPercent)
    }

    @Test
    fun incompleteProfileTakesPriorityOverCommercialWork() {
        val summary = buildSupplierWorkspaceReadiness(
            items = listOf(
                profileItem(
                    id = "supplier-a",
                    ready = false,
                    openOrders = 4,
                    catalogCount = 0,
                    partnerCount = 0,
                    agreementCount = 0,
                    selected = true
                )
            ),
            activeSupplierId = "supplier-a"
        )

        assertEquals(SupplierWorkspaceReadinessNextStep.CompleteProfile, summary.nextStep)
        assertEquals("supplier-a", summary.targetSupplierId)
    }

    @Test
    fun readyProfilePrioritizesOpenOrdersThenMissingWorkspacePieces() {
        assertEquals(
            SupplierWorkspaceReadinessNextStep.ReviewOrders,
            buildSupplierWorkspaceReadiness(
                listOf(profileItem("supplier-a", openOrders = 2, selected = true)),
                "supplier-a"
            ).nextStep
        )
        assertEquals(
            SupplierWorkspaceReadinessNextStep.BuildCatalog,
            buildSupplierWorkspaceReadiness(
                listOf(profileItem("supplier-a", catalogCount = 0, selected = true)),
                "supplier-a"
            ).nextStep
        )
        assertEquals(
            SupplierWorkspaceReadinessNextStep.ConnectStores,
            buildSupplierWorkspaceReadiness(
                listOf(profileItem("supplier-a", partnerCount = 0, selected = true)),
                "supplier-a"
            ).nextStep
        )
        assertEquals(
            SupplierWorkspaceReadinessNextStep.ReviewAgreements,
            buildSupplierWorkspaceReadiness(
                listOf(profileItem("supplier-a", agreementCount = 0, selected = true)),
                "supplier-a"
            ).nextStep
        )
        assertEquals(
            SupplierWorkspaceReadinessNextStep.OpenInsights,
            buildSupplierWorkspaceReadiness(
                listOf(profileItem("supplier-a", selected = true)),
                "supplier-a"
            ).nextStep
        )
    }
}
