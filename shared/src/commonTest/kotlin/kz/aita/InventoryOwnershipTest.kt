package kz.aita

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class InventoryOwnershipTest {
    @Test fun repeatedSelectionDoesNotInvalidateAnInFlightRead() {
        val tracker = InventoryOwnerTracker()
        val first = tracker.select("store-a", "account", 3)
        assertEquals(first, tracker.select("store-a", "account", 3))
        assertTrue(tracker.owns(first))
    }

    @Test fun switchingStoresRejectsPreviousOwner() {
        val tracker = InventoryOwnerTracker()
        val first = tracker.select("store-a", "account", 3)
        tracker.select("store-b", "account", 3)
        assertFalse(tracker.owns(first))
    }

    @Test fun returningToSameStoreDoesNotReviveAnOldRead() {
        val tracker = InventoryOwnerTracker()
        val oldA = tracker.select("store-a", "account", 3)
        tracker.select("store-b", "account", 3)
        val newA = tracker.select("store-a", "account", 3)
        assertNotEquals(oldA.epoch, newA.epoch)
        assertFalse(tracker.owns(oldA))
        assertTrue(tracker.owns(newA))
    }

    @Test fun newSessionRejectsSameAccountSameStoreRead() {
        val tracker = InventoryOwnerTracker()
        val previous = tracker.select("store-a", "account", 3)
        tracker.select("store-a", "account", 4)
        assertFalse(tracker.owns(previous))
    }

    @Test fun anotherAccountNeverInheritsThePreviousOwnersCacheKey() {
        val tracker = InventoryOwnerTracker()
        val previous = tracker.select("store-a", "account-a", 3)
        val current = tracker.select("store-a", "account-b", 4)
        assertFalse(tracker.owns(previous))
        assertNotEquals(inventoryCacheKey("stock", previous), inventoryCacheKey("stock", current))
        assertNotEquals(inventoryCacheKey("stock", current), inventoryCacheKey("stock_batches", current))
    }

    @Test fun noStoreInvalidatesInventoryOwnership() {
        val tracker = InventoryOwnerTracker()
        val old = tracker.select("store-a", "account", 3)
        tracker.select(null, "account", 3)
        assertFalse(tracker.owns(old))
    }

    @Test fun lateCacheDoesNotOverwriteAnAuthoritativeEmptyList() {
        assertFalse(canHydrateInventory(hasPayload = true, ownerIsCurrent = true))
    }

    @Test fun cacheHydratesOnlyUnloadedCurrentInventory() {
        assertTrue(canHydrateInventory(hasPayload = false, ownerIsCurrent = true))
        assertFalse(canHydrateInventory(hasPayload = false, ownerIsCurrent = false))
    }

    @Test fun failureDoesNotPretendToBeAnEmptySuccess() {
        val failed = InventoryLoadStatus(storeId = "store", failure = inventoryLoadFailureMessage())
        assertFalse(failed.loading)
        assertEquals(InventoryLoadSource.None, failed.source)
        assertTrue(failed.failure.orEmpty().isNotEmpty())
    }
}
