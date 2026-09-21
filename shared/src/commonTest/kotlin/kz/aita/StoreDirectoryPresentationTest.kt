package kz.aita
import kotlin.test.*
class StoreDirectoryPresentationTest {
    private fun store(id: String) = StoreDataModel(id = id, userIds = emptyList(), storeTypeIds = emptyList(),
        name = emptyList(), alias = emptyList(), description = emptyList(), companyForms = emptyList(), location = LocationDataModel(),
        phoneNumbers = emptyList(), emails = emptyList(), countryLocales = emptyList(), createdAt = 0, architectureVersion = 2)
    @Test fun branchWithoutVisibleParentIsNeverDiscarded() {
        val branch = store("branch").copy(parentStoreId = "deleted-parent", userIds = listOf("owner"))
        assertEquals(listOf(branch), listOf(branch).storeDirectoryRoots())
        assertTrue(branch.storeDirectoryFamilyOwnedBy("owner"))
    }
    @Test fun branchOwnerCanFindTheirFamilyUnderMyStoresWithoutAcquiringParentOwnership() {
        val branch = store("branch").copy(parentStoreId = "parent", userIds = listOf("owner"))
        val parent = store("parent").copy(userIds = listOf("different-owner"), branches = listOf(branch))
        assertEquals(listOf(parent), listOf(parent).storeDirectoryRoots())
        assertTrue(parent.storeDirectoryFamilyOwnedBy("owner"))
        assertFalse("owner" in parent.userIds)
        assertFalse(parent.storeDirectoryFamilyOwnedBy("unrelated"))
        assertFalse(parent.storeDirectoryFamilyOwnedBy(""))
    }
}
