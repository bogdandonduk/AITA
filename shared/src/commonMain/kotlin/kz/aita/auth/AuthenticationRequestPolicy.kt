package kz.aita.auth

import io.ktor.client.plugins.auth.*
import io.ktor.client.request.*
import io.ktor.http.HttpHeaders
import io.ktor.http.encodedPath
import kz.aita.cloudEndpointRequiresAuthentication

/** A rejected public credential must never refresh/replay another account's stored session. */
@PublishedApi
internal fun HttpRequestBuilder.disableSessionAuthForPublicAuthRequest(endpointUrl: String) {
    val endpoint = endpointUrl.trim('/').lowercase()
    if (!cloudEndpointRequiresAuthentication(endpointUrl) && (endpoint.startsWith("auth/") || endpoint == "diagnostics/events/anonymous")) {
        // sendWithoutRequest(false) disables preemptive credentials, not a retry after 401.
        attributes.put(AuthCircuitBreaker, Unit)
        if (endpoint == "diagnostics/events/anonymous") headers.remove(HttpHeaders.Authorization)
    }
}

/** A queued account operation must not be authenticated/replayed as a newly selected account. */
@PublishedApi
internal fun HttpRequestBuilder.pinSessionAuthorization(accessToken: String) {
    attributes.put(AuthCircuitBreaker, Unit)
    headers.remove(HttpHeaders.Authorization)
    headers.append(HttpHeaders.Authorization, "Bearer $accessToken")
}

/**
 * Ktor 3.3.2 runs preemptive bearer injection before its AuthCircuitBreaker check.
 * Its provider removes an existing Authorization header even when the breaker is set.
 * Pinned account requests own that header; public/refresh requests own their lack of it.
 * Keep this predicate in the provider as well as the per-request circuit breaker.
 */
internal fun HttpRequestBuilder.allowsStoredSessionAuthorization(): Boolean =
    !attributes.contains(AuthCircuitBreaker) &&
        !headers.contains(HttpHeaders.Authorization) &&
        cloudEndpointRequiresAuthentication(url.encodedPath)
