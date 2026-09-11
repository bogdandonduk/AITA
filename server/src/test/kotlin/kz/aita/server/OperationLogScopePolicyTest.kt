package kz.aita.server

import kotlin.test.*

class OperationLogScopePolicyTest {
    @Test fun currentParentMeansOnlyTheParentNotItsBranches() {
        assertEquals(listOf("parent"), operationLogScopeStoreIds("parent", listOf("parent", "branch"), false, true))
    }
    @Test fun familyContainsOnlyAlreadyPermissionFilteredPlaces() {
        assertEquals(listOf("parent", "branch"), operationLogScopeStoreIds("parent", listOf("parent", "branch", "parent"), true, true))
    }
    @Test fun noFamilyPrivilegeCannotExpandTheScope() {
        assertEquals(listOf("branch"), operationLogScopeStoreIds("branch", listOf("parent", "branch"), true, false))
    }
    @Test fun invisibleCurrentPlaceIsNotInvented() {
        assertEquals(emptyList(), operationLogScopeStoreIds("hidden", listOf("visible"), false, true))
    }
}
