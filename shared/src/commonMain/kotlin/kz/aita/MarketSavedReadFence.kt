package kz.aita

/** UI-thread confined acknowledgement fence, not a permanent override of another device's edits.
 * Keep evidence for older requests even after a newer GET completes; it may still return later.
 * Production mutation replies contain the full (bounded) public saved set, so one snapshot replaces
 * the per-offer overrides instead of growing a map for every product visited in a long session.
 */
class MarketSavedReadFence {
    private data class Override(val revision: Long, val saved: Boolean)
    private data class Snapshot(val revision: Long, val ids: Set<String>, val unavailable: Int)
    private var revision = 0L
    private val overrides = mutableMapOf<String, Override>()
    private var snapshot: Snapshot? = null
    fun capture(): Long = revision
    fun acknowledge(offerId: String, saved: Boolean) {
        revision++
        overrides[offerId] = Override(revision, saved)
    }
    fun acknowledgeSnapshot(savedIds: Set<String>, unavailableSavedCount: Int = 0) {
        revision++
        snapshot = Snapshot(revision, savedIds.toSet(), unavailableSavedCount.coerceAtLeast(0))
        overrides.clear()
    }
    fun reconcile(page: MarketPage, requestRevision: Long, savedOnly: Boolean = false): MarketPage {
        val whole = snapshot?.takeIf { it.revision > requestRevision }
        return page.copy(offers = page.offers.map { offer ->
            val change = overrides[offer.id]?.takeIf { it.revision > requestRevision }
            when {
                change != null -> offer.copy(saved = change.saved)
                whole != null -> offer.copy(saved = offer.id in whole.ids)
                else -> offer
            }
        }.filter { !savedOnly || it.saved }, unavailableSavedCount = if (savedOnly && whole != null) whole.unavailable else page.unavailableSavedCount)
    }
}
