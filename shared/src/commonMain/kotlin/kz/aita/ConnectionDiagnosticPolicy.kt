package kz.aita

enum class ConnectionFailureKind(val messageKey: String) {
    None("connection.available"), Dns("connection.dns"), Tls("connection.tls"), Timeout("connection.timeout"),
    Gateway("connection.gateway"), Origin("connection.origin"), UnexpectedResponse("connection.unexpected"),
    ServerRejected("connection.rejected"), Unknown("connection.unknown")
}

/** Diagnostic categories are not authorization decisions and never clear an inventory cache. */
internal fun classifyConnectionFailure(
    status: Int?, aitaResponse: Boolean, originError: Boolean = false, errorSummary: String? = null
): ConnectionFailureKind {
    if (status != null) return when {
        originError -> ConnectionFailureKind.Origin
        status in 200..299 && aitaResponse -> ConnectionFailureKind.None
        status in 500..599 && !aitaResponse -> ConnectionFailureKind.Gateway
        !aitaResponse -> ConnectionFailureKind.UnexpectedResponse
        else -> ConnectionFailureKind.ServerRejected
    }
    val error = errorSummary.orEmpty().lowercase()
    return when {
        listOf("unknownhost", "resolve host", "name resolution", "nodename", "dns").any(error::contains) -> ConnectionFailureKind.Dns
        listOf("ssl", "tls", "certificate", "handshake").any(error::contains) -> ConnectionFailureKind.Tls
        listOf("timeout", "timed out").any(error::contains) -> ConnectionFailureKind.Timeout
        else -> ConnectionFailureKind.Unknown
    }
}

/** Readiness is an AITA response, not just a successful status from an unrelated website. */
internal fun connectionDiagnosticMessage(knownGateway: Boolean, bindingConfigured: Boolean?, edgeStatus: Int?,
    originStatus: Int?, originAita: Boolean, originReady: Boolean): String = when {
    knownGateway && bindingConfigured == false -> "connection.binding_missing"
    knownGateway && originAita && originReady -> "connection.layers_ready"
    knownGateway -> "connection.edge_only"
    originAita && originReady -> "connection.origin_ready"
    edgeStatus == null && originStatus == null -> "connection.no_response"
    else -> "connection.layers_failed"
}
