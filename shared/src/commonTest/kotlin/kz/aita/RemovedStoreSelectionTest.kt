package kz.aita

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RemovedStoreSelectionTest {
    private fun store(id: String) = StoreDataModel(id = id, userIds = emptyList(), storeTypeIds = emptyList(),
        name = emptyList(), alias = emptyList(), description = emptyList(), companyForms = emptyList(), location = LocationDataModel(),
        phoneNumbers = emptyList(), emails = emptyList(), countryLocales = emptyList(), createdAt = 0)

    private val branch = store("branch").copy(parentStoreId = "parent")
    private val parent = store("parent").copy(branches = listOf(branch))

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
