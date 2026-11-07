package kz.aita

import io.ktor.http.HttpMethod
import kotlinx.coroutines.*

data class MarketPriceWatchResult(
    val state: MarketPriceWatchState? = null,
    val message: List<LocalizedStringDataModel>? = null,
    val conflict: Boolean = false,
    val denied: Boolean = false,
    val rejected: Boolean = false,
)

/** Settings changes are confirmed online, never represented as enabled merely by a local toggle. */
suspend fun requestMarketPriceWatch(update: MarketPriceWatchUpdate? = null): MarketPriceWatchResult = withContext(Dispatchers.ourIo) {
    val owner = inventoryOwners.current
    val account = owner.accountId ?: return@withContext MarketPriceWatchResult(message = marketPriceMessage("access"), denied = true)
    val store = owner.storeId ?: return@withContext MarketPriceWatchResult(message = marketPriceMessage("access"), denied = true)
    val origin = globalAppConfigurationState.payloadValue.serverUrl.first
    fun current() = inventoryOwnerIsCurrent(owner) && canUseMarketPriceInsights(store) && globalAppConfigurationState.payloadValue.serverUrl.first == origin
    if (!current()) return@withContext MarketPriceWatchResult(message = marketPriceMessage("access"), denied = true)
    try {
        val response = withTimeoutOrNull(20_000) {
            networkRequest<MarketPriceWatchState, MarketPriceWatchUpdate>(if (update == null) HttpMethod.Get else HttpMethod.Put,
                endpointUrl = "analytics/market-prices/watch", headers = mapOf("store_id" to store), body = update,
                expectedSessionGeneration = owner.sessionGeneration)
        }
        currentCoroutineContext().ensureActive()
        if (!current()) return@withContext MarketPriceWatchResult()
        if (response?.httpStatusCode == 403 && !response.transportFailure) {
            try { revokeMarketPriceReports(account, store, origin) }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { /* The cache keeps its in-process denial even if disk persistence fails. */ }
        }
        val state = response?.payload?.takeIf { response.httpStatusCode == 200 && !response.negative && !response.transportFailure && it.isValidFor(account, store) }
        MarketPriceWatchResult(state, response?.message ?: if (state == null) marketPriceMessage("watch_unavailable") else null,
            conflict = response?.httpStatusCode == 409 && !response.transportFailure,
            denied = response?.httpStatusCode == 403 && !response.transportFailure,
            rejected = response?.httpStatusCode in setOf(400, 422) && response?.transportFailure == false)
    } catch (cancel: CancellationException) { throw cancel }
    catch (_: Exception) { if (current()) MarketPriceWatchResult(message = marketPriceMessage("watch_unavailable")) else MarketPriceWatchResult() }
}
