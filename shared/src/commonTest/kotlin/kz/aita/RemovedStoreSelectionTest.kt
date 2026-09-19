package kz.aita

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RemovedStoreSelectionTest {
    private fun store(id: String) = StoreDataModel(id = id, userIds = emptyList(), storeTypeIds = emptyList(),
        name = emptyList(), alias = emptyList(), description = emptyList(), companyForms = emptyList(), location = LocationDataModel(),
        phoneNumbers = emptyList(), emails = emptyList(), countryLocales = emptyList(), createdAt = 0, architectureVersion = 2)

    private val branch = store("branch").copy(parentStoreId = "parent")
    private val parent = store("parent").copy(branches = listOf(branch))

    @Test fun emptySelectionRecoversTheOnlyManagementFamilyEvenWithSeveralBranches() {
        val family = parent.copy(branches = listOf(branch, branch.copy(id = "second")))
        assertEquals("parent", recoverUnselectedStore(listOf(family)))
        assertEquals("parent", recoverUnselectedStore(listOf(family), "parent"))
    }
    @Test fun emptySelectionDoesNotGuessBetweenUnrelatedFamilies() {
        assertNull(recoverUnselectedStore(listOf(parent, store("other-parent"))))
        assertNull(recoverUnselectedStore(emptyList()))
    }
    @Test fun branchWorkerCannotBeAutomaticallyMovedIntoAParentVisibleOnlyAsMetadata() {
        val branchAccess: (StoreDataModel) -> Boolean = { it.isBranchStore() }
        assertEquals("branch", recoverUnselectedStore(listOf(parent), "parent", branchAccess))
        val remaining = parent.copy(branches = listOf(branch.copy(id = "other-branch")))
        assertNull(replacementForRemovedStore("branch", listOf(parent), listOf(remaining), "parent", branchAccess))
        assertEquals("other-branch", recoverUnselectedStore(listOf(remaining), "parent", branchAccess))
    }
    @Test fun deletedBranchSelectsItsSurvivingParent() {
        assertEquals("parent", replacementForRemovedStore("branch", listOf(parent), listOf(parent.copy(branches = emptyList()))))
    }
    @Test fun deletionDoesNotOverrideAnotherSelectedStore() {
        assertEquals("parent", replacementForRemovedStore("parent", listOf(parent), listOf(parent.copy(branches = emptyList()))))
    }
    @Test fun persistedParentHintRepairsAColdStartAfterRemoteDeletion() {
        assertEquals("parent", replacementForRemovedStore("branch", emptyList(), listOf(parent.copy(branches = emptyList())), "parent"))
    }
    @Test fun removedParentOrUnreachableParentDoesNotSelectAnotherStore() {
        assertNull(replacementForRemovedStore("branch", listOf(parent), emptyList()))
        assertNull(replacementForRemovedStore("parent", listOf(parent), emptyList()))
        assertNull(replacementForRemovedStore(null, listOf(parent), listOf(parent)))
    }
}
