package kz.aita

enum class InventoryFeedbackKind { Loading, Denied, CacheWriteFailure, SavedOffline, NoCacheOffline, Failure, Empty }
fun inventoryFeedbackKind(status: InventoryLoadStatus, offline: Boolean, visiblePayload: Boolean, expectsLoad: Boolean): InventoryFeedbackKind = when {
    status.accessDenied -> InventoryFeedbackKind.Denied
    status.cacheWriteFailed -> InventoryFeedbackKind.CacheWriteFailure
    offline && visiblePayload -> InventoryFeedbackKind.SavedOffline
    offline && status.cacheChecked -> InventoryFeedbackKind.NoCacheOffline
    status.failure != null -> InventoryFeedbackKind.Failure
    status.loading || expectsLoad -> InventoryFeedbackKind.Loading
    else -> InventoryFeedbackKind.Empty
}
