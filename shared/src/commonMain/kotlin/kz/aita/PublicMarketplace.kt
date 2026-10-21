package kz.aita

import io.ktor.http.HttpMethod
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable

@Serializable data class PublicMarketDiscovery(val discovery: MarketDiscoveryResult, val publicVersion: Int = 1)

/** Reuse all catalogue/price validation without accepting a private account envelope as public. */
fun PublicMarketDiscovery.validated(request: MarketDiscoveryRequest, catalogue: MarketCategoryCatalogue?): MarketDiscoverySnapshot? {
    if (publicVersion != 1 || discovery.accountId != null || request.query.savedOnly || request.limit > 120 ||
        discovery.page.offers.any { it.saved } || discovery.page.unavailableSavedCount != 0) return null
    val validated = discovery.copy(accountId = "public-catalogue").validatedDiscovery(request, catalogue, "public-catalogue") ?: return null
    return validated.copy(result = discovery)
}

suspend fun loadPublicMarketplace(request: MarketDiscoveryRequest, catalogue: MarketCategoryCatalogue? = null): ResponseDataModel<MarketDiscoverySnapshot> {
    val response = networkRequest<PublicMarketDiscovery, MarketDiscoveryRequest>(HttpMethod.Post,
        endpointUrl = "market/public/discovery", body = request)
    if (response.negative) return ResponseDataModel(response.message, null, true, response.httpStatusCode, transportFailure = response.transportFailure)
    val value = response.payload?.validated(request, catalogue)
        ?: return ResponseDataModel(eventMessage("market.page_changed"), null, true, 502)
    // Cache only the bounded home catalogue. No private account fields or arbitrary search history.
    if (request.query == MarketDiscoveryQuery() && request.limit == MARKET_DISCOVERY_PAGE_SIZE) {
        try { writeJsonCacheText("public-market-home-v1", jsonBase.encodeToString(PublicMarketDiscovery(value.result.copy(categories = value.catalogue.categories)))) }
        catch (c: CancellationException) { throw c } catch (_: Exception) { }
    }
    return ResponseDataModel(response.message, value, false, response.httpStatusCode)
}

suspend fun cachedPublicMarketplace(): MarketDiscoverySnapshot? = try {
    readRequiredJsonJournal("public-market-home-v1")?.let { jsonBase.decodeFromString<PublicMarketDiscovery>(it) }
        ?.validated(MarketDiscoveryRequest(MarketDiscoveryQuery()), null)
} catch (c: CancellationException) { throw c } catch (_: Exception) { null }
