package kz.aita

/** A visible page window is replaced atomically. A failed later page never publishes a partial
 * refresh as success, and changing data cannot send the reader around a repeating cursor forever.
 */
suspend fun readMarketPageWindow(
    pages: Int,
    read: suspend (after: String?) -> ResponseDataModel<MarketPage>
): ResponseDataModel<MarketPage> {
    require(pages in 1..10)
    var cursor: String? = null
    val seen = mutableSetOf<String>()
    var window: MarketPage? = null
    repeat(pages) {
        val response = read(cursor)
        val data = response.payload
        if (response.negative || data == null) return response.copy(payload = null, negative = true,
            message = response.message ?: eventMessage("market.refresh_failed"))
        val previous = window
        window = if (previous == null) data else previous.copy(
            offers = (previous.offers + data.offers).distinctBy { it.id }, nextId = data.nextId,
            checkedAtMillis = minOf(previous.checkedAtMillis, data.checkedAtMillis))
        cursor = data.nextId
        if (cursor == null) return response.copy(payload = window)
        if (!seen.add(requireNotNull(cursor))) return ResponseDataModel(eventMessage("market.page_changed"), null, true, 409)
    }
    return ResponseDataModel(null, window, false, 200)
}
