package kz.aita

import kotlinx.coroutines.CancellationException

const val MARKET_SAVED_MAX_OFFERS = 100

/** The legacy /saved response is the complete bounded PUBLIC saved set, not a discovery page.
 * Unavailable bookmarks stay on the server and contribute only to the count. Validate before
 * using a reply as evidence to remove cards or override a concurrently running catalogue read.
 */
fun MarketPage.isValidMarketSavedSnapshot(): Boolean = checkedAtMillis > 0L && nextId == null &&
    unavailableSavedCount in 0..MARKET_SAVED_MAX_OFFERS &&
    offers.size <= MARKET_SAVED_MAX_OFFERS - unavailableSavedCount &&
    offers.map { it.id }.distinct().size == offers.size && offers.all { offer ->
        marketDiscoveryId(offer.id) == offer.id && offer.saved && offer.storefront.isValidPublicMarketShop() &&
            offer.checkedAtMillis == checkedAtMillis && offer.sourceUpdatedAtMillis >= 0L &&
            offer.title.isNotBlank() && offer.title.length <= 180 && offer.description.length <= 2000 &&
            offer.categoryIds.size <= 16 && offer.hasValidDiscoveryPrice() &&
            offer.availability in setOf(MARKET_AVAILABILITY_RECORDED, MARKET_AVAILABILITY_CONFIRM)
    }

/** A public set cannot prove that a hidden bookmark was removed. Require the server's
 * mutation acknowledgement AND the matching public state, never infer a write from a count.
 */
fun MarketPage.acknowledgesMarketSavedUpdate(request: MarketSavedUpdate): Boolean =
    marketDiscoveryId(request.offerId) == request.offerId && isValidMarketSavedSnapshot() &&
        savedMutation == request && !unavailableSavedCleared && offers.any { it.id == request.offerId } == request.saved

suspend fun readOwnedMarketSaved(owner: MarketAccountScope,
    read: suspend () -> ResponseDataModel<MarketPage>
): ResponseDataModel<MarketPage> {
    if (!owner.isCurrent()) return cloudSessionExpiredResponse()
    val response = read()
    if (!owner.isCurrent()) return cloudSessionExpiredResponse()
    if (response.negative || response.transportFailure || response.httpStatusCode != 200)
        return response.copy(payload = null, negative = true,
            message = response.message ?: eventMessage("market.refresh_failed"))
    return if (response.payload?.isValidMarketSavedSnapshot() == true) response
        else ResponseDataModel(eventMessage("market.refresh_failed"), null, true, 502)
}

suspend fun updateOwnedMarketSaved(owner: MarketAccountScope, request: MarketSavedUpdate,
    onChanged: () -> Unit = {},
    write: suspend (MarketSavedUpdate) -> ResponseDataModel<MarketPage>
): ResponseDataModel<MarketPage> {
    if (!owner.isCurrent()) return cloudSessionExpiredResponse()
    val id = marketDiscoveryId(request.offerId)
        ?: return ResponseDataModel(eventMessage("market.unavailable"), null, true, 400)
    val normalized = request.copy(offerId = id)
    return mutateOwnedMarketSaved(owner, onChanged, { it.acknowledgesMarketSavedUpdate(normalized) }) { write(normalized) }
}

suspend fun clearUnavailableOwnedMarketSaved(owner: MarketAccountScope,
    onChanged: () -> Unit = {},
    write: suspend () -> ResponseDataModel<MarketPage>
): ResponseDataModel<MarketPage> = mutateOwnedMarketSaved(owner, onChanged,
    { it.isValidMarketSavedSnapshot() && it.unavailableSavedCount == 0 &&
        it.unavailableSavedCleared && it.savedMutation == null }, write)

/** Never resend a bookmark mutation automatically. A lost/invalid reply can follow a commit;
 * invalidate the focused view for a READ even on that failure, without fabricating a save ack.
 * Scope is checked on both sides of I/O, including before publishing that invalidation.
 */
private suspend fun mutateOwnedMarketSaved(owner: MarketAccountScope, onChanged: () -> Unit,
    acknowledges: (MarketPage) -> Boolean, write: suspend () -> ResponseDataModel<MarketPage>
): ResponseDataModel<MarketPage> {
    if (!owner.isCurrent()) return cloudSessionExpiredResponse()
    try {
        val response = write()
        if (!owner.isCurrent()) return cloudSessionExpiredResponse()
        if (response.negative || response.transportFailure || response.httpStatusCode != 200)
            return response.copy(payload = null, negative = true,
                message = if (response.negative && !response.transportFailure && response.httpStatusCode in 400..499)
                    response.message ?: eventMessage("market.saved_unconfirmed") else eventMessage("market.saved_unconfirmed"))
        return if (response.payload?.let(acknowledges) == true) response
            else ResponseDataModel(eventMessage("market.saved_unconfirmed"), null, true, 502)
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) {
        return if (owner.isCurrent()) ResponseDataModel(eventMessage("market.saved_unconfirmed"), null, true, transportFailure = true)
            else cloudSessionExpiredResponse()
    } finally {
        if (owner.isCurrent()) onChanged()
    }
}
