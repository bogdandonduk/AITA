package kz.aita.auth

import io.ktor.client.plugins.auth.*
import io.ktor.client.request.*
import kz.aita.cloudEndpointRequiresAuthentication

/** A rejected public credential must never refresh/replay another account's stored session. */
@PublishedApi
internal fun HttpRequestBuilder.disableSessionAuthForPublicAuthRequest(endpointUrl: String) {
    if (!cloudEndpointRequiresAuthentication(endpointUrl) && endpointUrl.trim('/').startsWith("auth/", ignoreCase = true)) {
        // sendWithoutRequest(false) disables preemptive credentials, not a retry after 401.
        attributes.put(AuthCircuitBreaker, Unit)
    }
}
