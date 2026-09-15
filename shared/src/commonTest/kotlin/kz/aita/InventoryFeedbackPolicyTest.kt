package kz.aita
import kotlin.test.*
class InventoryFeedbackPolicyTest {
    @Test fun cachedStockRemainsVisibleWhileConnectionIsBeingRetried() {
        val status=InventoryLoadStatus(source=InventoryLoadSource.Cache,loading=true,cacheChecked=true)
        assertEquals(InventoryFeedbackKind.SavedOffline,inventoryFeedbackKind(status,true,true,true))
    }
    @Test fun anAuthoritativeEmptyCacheIsStillASavedSnapshot() {
        val status=InventoryLoadStatus(source=InventoryLoadSource.Cache,cacheChecked=true)
        assertEquals(InventoryFeedbackKind.SavedOffline,inventoryFeedbackKind(status,true,status.source!=InventoryLoadSource.None,false))
    }
    @Test fun noSavedCopyIsNotClaimedBeforeDiskWasChecked() {
        assertEquals(InventoryFeedbackKind.Loading,inventoryFeedbackKind(InventoryLoadStatus(loading=true),true,false,true))
    }
    @Test fun missingOfflineCopyExplainsFirstSuccessfulSyncIsRequired() {
        assertEquals(InventoryFeedbackKind.NoCacheOffline,inventoryFeedbackKind(InventoryLoadStatus(cacheChecked=true),true,false,true))
    }
    @Test fun deniedAccessTakesPrecedenceOverSavedData() {
        assertEquals(InventoryFeedbackKind.Denied,inventoryFeedbackKind(InventoryLoadStatus(accessDenied=true),true,true,false))
    }
    @Test fun diskFailureDoesNotSilentlyPromiseDurability() {
        assertEquals(InventoryFeedbackKind.CacheWriteFailure,inventoryFeedbackKind(InventoryLoadStatus(cacheWriteFailed=true),true,true,false))
    }
    @Test fun successfulEmptyResponseIsNotAnEndlessSkeleton() {
        assertEquals(InventoryFeedbackKind.Empty,inventoryFeedbackKind(InventoryLoadStatus(source=InventoryLoadSource.Cloud),false,false,false))
    }
    @Test fun onlineFailureRemainsAnErrorNotEmptyStock() {
        assertEquals(InventoryFeedbackKind.Failure,inventoryFeedbackKind(InventoryLoadStatus(failure=listOf(LocalizedStringDataModel("en","Failed"))),false,false,false))
    }
}
