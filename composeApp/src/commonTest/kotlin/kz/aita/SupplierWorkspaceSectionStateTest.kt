package kz.aita

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SupplierWorkspaceSectionStateTest {
    @Test fun explicitOrderNavigationRevealsOrdersAndCarriesFiltersTogether() {
        val seed = supplierWorkspaceNavigationSeed("orders", true,
            "search" to "магазин", "status" to "needs_attention").toMap()
        assertEquals("orders", seed[SUPPLIER_WORKSPACE_SECTION_STATE_KEY])
        assertEquals("магазин", seed["search"])
        assertEquals("needs_attention", seed["status"])
    }

    @Test fun explicitPartnerNavigationRevealsPartnerList() {
        assertEquals("partners", supplierWorkspaceNavigationSeed("partners", true,
            "search" to "partner-a").toMap()[SUPPLIER_WORKSPACE_SECTION_STATE_KEY])
    }

    @Test fun backgroundPersistenceDoesNotIncludeASectionCommand() {
        val seed = supplierWorkspaceNavigationSeed("orders", false, "status" to "open")
        assertFalse(seed.any { it.first == SUPPLIER_WORKSPACE_SECTION_STATE_KEY })
        assertEquals(listOf("status" to "open"), seed)
    }

    @Test fun savingFiltersWhileViewingReadinessDoesNotSwitchTheTab() {
        val state = mutableMapOf(SUPPLIER_WORKSPACE_SECTION_STATE_KEY to "readiness")
        state.putAll(supplierWorkspaceNavigationSeed("orders", false, "search" to "bread"))
        assertEquals("readiness", state[SUPPLIER_WORKSPACE_SECTION_STATE_KEY])
        assertEquals("bread", state["search"])
    }

    @Test fun sameFilterExplicitNavigationStillLeavesHealthForResults() {
        val state = mutableMapOf(SUPPLIER_WORKSPACE_SECTION_STATE_KEY to "health", "search" to "store-a")
        state.putAll(supplierWorkspaceNavigationSeed("partners", true, "search" to "store-a"))
        assertEquals("partners", state[SUPPLIER_WORKSPACE_SECTION_STATE_KEY])
        assertEquals("store-a", state["search"])
    }

    @Test fun emptySearchIsPreservedToClearAnOldFilter() {
        val state = mutableMapOf("search" to "stale")
        state.putAll(supplierWorkspaceNavigationSeed("orders", true, "search" to ""))
        assertEquals("", state["search"])
    }

    @Test fun buildingASeedDoesNotMutateTheSuppliedFilters() {
        val filters = arrayOf("search" to "rice", "status" to "open")
        supplierWorkspaceNavigationSeed("orders", true, *filters)
        assertEquals(listOf("search" to "rice", "status" to "open"), filters.toList())
    }

    @Test fun emptyBackgroundSeedCannotChangeAnyState() {
        assertEquals(emptyList(), supplierWorkspaceNavigationSeed("orders", false))
    }
}
