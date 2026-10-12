package kz.aita

import kotlin.test.*

class WorkerWorkplaceTest {
    private fun store(id: String, parent: String? = null) = StoreDataModel(
        id = id, parentStoreId = parent, userIds = listOf("owner"), storeTypeIds = emptyList(),
        name = emptyList(), alias = emptyList(), description = emptyList(), companyForms = emptyList(),
        location = LocationDataModel(), phoneNumbers = emptyList(), emails = emptyList(), countryLocales = emptyList(), createdAt = 1L)
    @Test fun parentEmployeeSeesTheCompanyButBranchEmployeeDoesNotGainSiblingAccess() {
        val tree = listOf(store("root").copy(branches = listOf(store("one", "root"), store("two", "root"))), store("other"))
        assertEquals(listOf("root", "one", "two"), workerWorkplaces(StoreWorkerDataModel(storeId = "root"), tree).map { it.id })
        assertEquals(listOf("one"), workerWorkplaces(StoreWorkerDataModel(storeId = "one"), tree).map { it.id })
        assertTrue(workerWorkplaces(StoreWorkerDataModel(storeId = "deleted"), tree).isEmpty())
    }
    @Test fun shiftMembershipAlwaysPrefersTheExactBranchAndNeverAnInactiveOrForeignUser() {
        val parent = StoreWorkerDataModel(id = "parent", userId = "u", storeId = "root")
        val branch = parent.copy(id = "branch", storeId = "one")
        assertEquals(branch, preferredWorkerMembership(listOf(parent, branch), "u", "one", "root"))
        assertEquals(branch, preferredWorkerMembership(listOf(branch, parent), "u", "one", "root"))
        assertEquals(parent, preferredWorkerMembership(listOf(parent, branch.copy(isActive = false)), "u", "one", "root"))
        assertNull(preferredWorkerMembership(listOf(parent), "other", "one", "root"))
    }
    @Test fun workerPlacementErrorsAreAvailableInAllSupportedLanguages() {
        listOf("workplace", "choose", "saved", "assign", "move", "parent_help", "move_help", "done", "denied", "shift", "duplicate", "changed", "pending", "invalid").forEach { key ->
            val messages = workerPlacementMessage(key)
            listOf("en", "ru", "kk", "ky", "tg", "uz").forEach { language -> assertTrue(messages.any { it.language == language && it.value.isNotBlank() }) }
        }
    }
}
