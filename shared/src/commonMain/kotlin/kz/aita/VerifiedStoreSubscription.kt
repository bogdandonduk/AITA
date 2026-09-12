package kz.aita

import kotlinx.serialization.Serializable

@Serializable
internal data class VerifiedStoreSubscription(
    val accountId: String,
    val subscription: StoreSubscriptionStateDataModel,
    val verifiedAtLocalMillis: Long,
    val verifiedAtServerMillis: Long
) {
    /** Offline presentation uses the last server clock plus elapsed local time. A clock rollback
     * requires verification instead of extending a time-limited subscription indefinitely.
     * The backend independently verifies every cloud operation; this is not a signed access token.
     */
    fun effectiveNow(localNow: Long): Long? {
        if (verifiedAtLocalMillis <= 0L || verifiedAtServerMillis <= 0L || localNow < 0L) return null
        if (localNow < (verifiedAtLocalMillis - 5_000L).coerceAtLeast(0L)) return null
        val elapsed = (localNow - verifiedAtLocalMillis).coerceAtLeast(0L)
        return verifiedAtServerMillis.takeIf { it <= Long.MAX_VALUE - elapsed }?.plus(elapsed)
    }
    fun allows(storeId: String, localNow: Long): Boolean = effectiveNow(localNow)?.let {
        subscription.grantsStoreAccess(storeId, it)
    } == true
}
