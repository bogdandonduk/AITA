package kz.aita

import kotlin.test.*

class InventoryRetentionPolicyTest {
    private val original = InventoryOwner("store-a", "account-a", 1, 1)

    @Test fun freshSessionForSameAccountKeepsItsLastStock() {
        assertTrue(sameInventoryAccountAndStore(original, original.copy(sessionGeneration = 2, epoch = 2)))
    }
    @Test fun switchingAccountOrStoreCannotRetainAnotherOwnersStock() {
        assertFalse(sameInventoryAccountAndStore(original, original.copy(accountId = "account-b")))
        assertFalse(sameInventoryAccountAndStore(original, original.copy(storeId = "store-b")))
        assertFalse(sameInventoryAccountAndStore(original, original.copy(storeId = null)))
        assertFalse(sameInventoryAccountAndStore(original.copy(accountId = null), original.copy(accountId = null)))
    }
    @Test fun expiredCloudAuthenticationDoesNotRevokeCachedInventory() {
        assertFalse(inventoryReadRevokesAccess(401, false))
        assertFalse(inventoryReadRevokesAccess(401, true))
    }
    @Test fun onlyAnExplicitNonTransportForbiddenResponseRevokesAccess() {
        assertTrue(inventoryReadRevokesAccess(403, false))
        assertFalse(inventoryReadRevokesAccess(403, true))
        for (status in listOf(null, 0, 200, 408, 429, 500, 502, 503, 504)) {
            assertFalse(inventoryReadRevokesAccess(status, false))
        }
    }
    @Test fun newSessionResetsTransientLoadButRetainsCacheAndWriteFailure() {
        val old = InventoryLoadStatus("store-a", true, InventoryLoadSource.Cache,
            inventoryLoadFailureMessage(), cacheWriteFailed = true)
        val next = old.afterSessionChange("store-a")
        assertFalse(next.loading)
        assertNull(next.failure)
        assertEquals(InventoryLoadSource.Cache, next.source)
        assertTrue(next.cacheWriteFailed)
    }
    @Test fun reauthenticationIsNotProofOfRestoredStorePermission() {
        val failure = inventoryLoadFailureMessage()
        val next = InventoryLoadStatus("store-a", true, failure = failure, accessDenied = true)
            .afterSessionChange("store-a")
        assertTrue(next.accessDenied)
        assertEquals(failure, next.failure)
        assertFalse(next.loading)
    }
    @Test fun cachesStaySharedAcrossSameAccountSessionsButNotAcrossAccounts() {
        assertEquals(inventoryCacheKey("stock", original), inventoryCacheKey("stock", original.copy(sessionGeneration = 2, epoch = 2)))
        assertNotEquals(inventoryCacheKey("stock", original), inventoryCacheKey("stock", original.copy(accountId = "another")))
    }
}
