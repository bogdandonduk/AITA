package kz.aita

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.Serializable

/** A detail read includes an account-owned saved marker. Do not accept a grid card, a private
 * seller listing or a cached response for another account as an authoritative detail result.
 */
@Serializable
data class MarketOfferDetailResult(val accountId: String, val offer: MarketOffer,
    val branchAvailability: List<MarketBranchAvailability> = emptyList(),
    val branchAvailabilityTruncated: Boolean = false)

fun MarketOfferDetailResult.isValidOfferDetailResult(account: String, requestedOfferId: String): Boolean {
    val id = marketDiscoveryId(requestedOfferId) ?: return false
    val value = offer
    return account.isNotBlank() && accountId == account && value.id == id &&
        value.storefront.isValidPublicMarketShop() && value.title.isNotBlank() && value.title.length <= 180 &&
        value.description.length <= 2000 && value.product.isValidMarketProduct() &&
        branchAvailability.size <= MARKET_BRANCH_MAX_LOCATIONS &&
        branchAvailability.distinctBy { it.branchId }.size == branchAvailability.size &&
        (value.storefront.shareBranchAvailability || (branchAvailability.isEmpty() && !branchAvailabilityTruncated)) &&
        branchAvailability.all { it.isValidMarketBranch(value) } && value.checkedAtMillis > 0L && value.sourceUpdatedAtMillis >= 0L &&
        (value.gtin == null || marketCanonicalGtin(value.gtin) == value.gtin) &&
        value.categoryIds.size <= 16 && value.categoryIds.distinct().size == value.categoryIds.size &&
        value.hasValidDiscoveryPrice() &&
        value.availability in setOf(MARKET_AVAILABILITY_RECORDED, MARKET_AVAILABILITY_CONFIRM) &&
        (value.availability != MARKET_AVAILABILITY_RECORDED || value.priceMinor != null)
}

/** Exactly one read. Missing endpoint, withdrawn offer, transport failure and malformed success
 * are distinct. Neither this reader nor its error recovery ever writes or falls back to a card.
 */
suspend fun readOwnedMarketOfferDetail(
    owner: MarketAccountScope,
    offerId: String,
    read: suspend (String) -> ResponseDataModel<MarketOfferDetailResult>
): ResponseDataModel<MarketOfferDetailResult> {
    currentCoroutineContext().ensureActive()
    if (!owner.isCurrent()) return cloudSessionExpiredResponse()
    val id = marketDiscoveryId(offerId)
        ?: return ResponseDataModel(eventMessage("market.invalid"), null, true, 400)
    val response = read(id)
    currentCoroutineContext().ensureActive()
    if (!owner.isCurrent()) return cloudSessionExpiredResponse()
    if (response.transportFailure) return response.copy(payload = null, negative = true,
        message = response.message ?: eventMessage("market.detail_failed"))
    if (response.httpStatusCode == 404) return response.copy(payload = null, negative = true,
        message = if (response.message?.eventMessageReferenceOrNull()?.key == "market.unavailable") response.message
            else eventMessage("market.detail_upgrade"))
    if (response.negative) return response.copy(payload = null,
        message = response.message ?: eventMessage("market.detail_failed"))
    if (response.httpStatusCode != 200 || response.payload?.isValidOfferDetailResult(owner.accountId, id) != true)
        return ResponseDataModel(eventMessage("market.detail_failed"), null, true, 502)
    currentCoroutineContext().ensureActive()
    if (!owner.isCurrent()) return cloudSessionExpiredResponse()
    return response
}

const val MARKET_OFFER_DETAIL_FRESH_MILLIS = 30_000L

/** A local refresh immediately retires old reads, before a conflated request channel is drained.
 * Object identity avoids counters wrapping or an older callback becoming valid after an ABA.
 * UI-confined; this is not a replacement for the authenticated session check or a reservation.
 */
class MarketOfferDetailReadFence {
    class Stamp internal constructor(internal val request: Any, internal val offerId: String, internal val revision: Long)
    private var request = Any()

    fun invalidate() { request = Any() }
    fun capture(offerId: String, revision: Long): Stamp = Stamp(request, offerId, revision)
    fun isCurrent(stamp: Stamp?, offerId: String, revision: Long): Boolean = stamp != null &&
        stamp.request === request && stamp.offerId == offerId && stamp.revision == revision

    /** Age comes from a monotonic mark taken on receipt, never the server/device wall clocks. */
    fun canUse(stamp: Stamp?, offerId: String, revision: Long, ageMillis: Long, loading: Boolean): Boolean =
        !loading && ageMillis in 0 until MARKET_OFFER_DETAIL_FRESH_MILLIS && isCurrent(stamp, offerId, revision)
}
