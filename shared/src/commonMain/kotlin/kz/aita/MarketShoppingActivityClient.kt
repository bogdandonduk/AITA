package kz.aita

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** History is online, account-owned, and independent of the selected merchant store. An old or
 * malformed response cannot enter the view. This read never clears a pending command by itself.
 */
suspend fun readOwnedShoppingActivity(
    owner: MarketAccountScope,
    request: MarketShoppingActivityRequest,
    read: suspend (MarketShoppingActivityRequest) -> ResponseDataModel<MarketShoppingActivityPage>
): ResponseDataModel<MarketShoppingActivityPage> {
    currentCoroutineContext().ensureActive()
    if (!owner.isCurrent()) return cloudSessionExpiredResponse()
    if (!request.isValidShoppingActivityRequest())
        return ResponseDataModel(eventMessage("market.shopping_activity_invalid"), null, true, 400)
    val response = read(request)
    currentCoroutineContext().ensureActive()
    if (!owner.isCurrent()) return cloudSessionExpiredResponse()
    if (response.transportFailure) return response.copy(payload = null, negative = true,
        message = response.message ?: eventMessage("market.shopping_activity_failed"))
    if (response.httpStatusCode == 404) return response.copy(payload = null, negative = true,
        message = eventMessage("market.shopping_activity_upgrade"))
    if (response.negative) return response.copy(payload = null,
        message = response.message ?: eventMessage("market.shopping_activity_failed"))
    if (response.httpStatusCode != 200)
        return ResponseDataModel(eventMessage("market.shopping_activity_failed"), null, true, 502)
    val page = response.payload
    if (page?.isValidShoppingActivityPage(owner.accountId, request) != true)
        return ResponseDataModel(eventMessage("market.shopping_activity_failed"), null, true, 502)
    currentCoroutineContext().ensureActive()
    if (!owner.isCurrent()) return cloudSessionExpiredResponse()
    return response
}

/** A summary never carries every basket's labels. Fetch at most one historical detail on demand,
 * and reject a different command or a changed immutable summary instead of displaying it.
 */
suspend fun readOwnedShoppingActivityDetail(owner: MarketAccountScope, summary: MarketShoppingActivityEntry,
    read: suspend (String) -> ResponseDataModel<MarketShoppingActivityEntry>
): ResponseDataModel<MarketShoppingActivityEntry> {
    currentCoroutineContext().ensureActive()
    if (!owner.isCurrent()) return cloudSessionExpiredResponse()
    if (!summary.isValidShoppingActivityEntry())
        return ResponseDataModel(eventMessage("market.shopping_activity_failed"), null, true, 400)
    val response = read(summary.commandId)
    currentCoroutineContext().ensureActive()
    if (!owner.isCurrent()) return cloudSessionExpiredResponse()
    if (response.transportFailure) return response.copy(payload = null, negative = true,
        message = response.message ?: eventMessage("market.shopping_activity_failed"))
    if (response.negative) return response.copy(payload = null,
        message = response.message ?: eventMessage("market.shopping_activity_failed"))
    if (response.httpStatusCode != 200)
        return ResponseDataModel(eventMessage("market.shopping_activity_failed"), null, true, 502)
    val detail = response.payload
    if (detail == null || !detail.isValidShoppingActivityEntry() || detail.detailsRecorded != (detail.details != null) ||
        detail.copy(details = null) != summary.copy(details = null))
        return ResponseDataModel(eventMessage("market.shopping_activity_failed"), null, true, 502)
    currentCoroutineContext().ensureActive()
    if (!owner.isCurrent()) return cloudSessionExpiredResponse()
    return response
}
