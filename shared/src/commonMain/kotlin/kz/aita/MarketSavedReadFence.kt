package kz.aita

/** UI-thread confined fence. A GET started before a saved mutation's acknowledgement must not
 * put the old heart state back. A later GET may reveal a legitimate edit from another device.
 */
class MarketSavedReadFence {
    private data class Override(val revision: Long, val saved: Boolean)
    private var revision = 0L
    private val overrides = mutableMapOf<String, Override>()
    fun capture(): Long = revision
    fun acknowledge(offerId: String, saved: Boolean) {
        revision++
        overrides[offerId] = Override(revision, saved)
    }
    fun reconcile(page: MarketPage, requestRevision: Long, savedOnly: Boolean = false): MarketPage {
        val result = page.copy(offers = page.offers.map { offer ->
            overrides[offer.id]?.takeIf { it.revision > requestRevision }?.let { offer.copy(saved = it.saved) } ?: offer
        }.filter { !savedOnly || it.saved })
        overrides.entries.removeAll { it.value.revision <= requestRevision }
        return result
    }
}
