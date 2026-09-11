package kz.aita

import kotlin.test.*

class OperationLogViewsTest {
    @Test fun unknownIsNotZero() {
        assertNull(OperationLogScopeState().records)
        assertEquals(0, OperationLogScopeState(records = emptyList()).records?.size)
    }
    @Test fun currentAndFamilyHaveSeparateRowsAndCacheKeys() {
        val owner = InventoryOwner("store", "account", 1, 1)
        val views = OperationLogViews(current = OperationLogScopeState(records = emptyList()))
        assertNull(views.family.records)
        assertNotEquals(operationLogCacheKey(owner, false), operationLogCacheKey(owner, true))
    }
    @Test fun noCrossAccountOrCrossStoreCacheReuse() {
        val owner = InventoryOwner("store", "account", 1, 1)
        assertNotEquals(operationLogCacheKey(owner, false), operationLogCacheKey(owner.copy(accountId = "other"), false))
        assertNotEquals(operationLogCacheKey(owner, false), operationLogCacheKey(owner.copy(storeId = "other"), false))
    }
    @Test fun sessionRenewalUsesTheSameAccountScopeCache() {
        val owner = InventoryOwner("store", "account", 1, 1)
        assertEquals(operationLogCacheKey(owner, true), operationLogCacheKey(owner.copy(sessionGeneration = 2, epoch = 2), true))
    }
    @Test fun onlyExplicitFamilyScopeSelectsTheFamily() {
        assertTrue(operationLogScopeIsFamily(OPERATION_LOG_SCOPE_ROOT.uppercase()))
        assertFalse(operationLogScopeIsFamily(OPERATION_LOG_SCOPE_CURRENT))
        assertFalse(operationLogScopeIsFamily(""))
        assertFalse(operationLogScopeIsFamily("invalid"))
    }
}
