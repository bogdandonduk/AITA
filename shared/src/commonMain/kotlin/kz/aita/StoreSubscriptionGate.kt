package kz.aita

/** Presentation state, not authorization. No I/O may delay closing business navigation. */
enum class StoreSubscriptionGate { Checking, Active, Required }

internal fun resolveStoreSubscriptionGate(accountId: String?, storeId: String?,
    proof: VerifiedStoreSubscription?, denied: Boolean, localNow: Long): StoreSubscriptionGate {
    if (accountId.isNullOrBlank()) return StoreSubscriptionGate.Checking
    if (storeId.isNullOrBlank()) return StoreSubscriptionGate.Required
    if (denied) return StoreSubscriptionGate.Required
    if (proof == null || proof.accountId != accountId || proof.subscription.storeId != storeId)
        return StoreSubscriptionGate.Checking
    if (proof.allows(storeId, localNow)) return StoreSubscriptionGate.Active
    // Clock rollback/corruption is not evidence that another payment is due.
    return if (proof.effectiveNow(localNow) == null) StoreSubscriptionGate.Checking else StoreSubscriptionGate.Required
}
