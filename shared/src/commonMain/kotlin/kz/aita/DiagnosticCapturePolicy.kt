package kz.aita

/** Capture before the request starts; never attach the account that happens to be active on failure. */
fun captureNetworkDiagnosticContext(endpoint: String, expectedGeneration: Long?): DiagnosticContext? {
    if (!RuntimeDiagnostics.state.value.enabled || endpoint.trim('/').startsWith("diagnostics/", true)) return null
    if (expectedGeneration != null && !authenticatedSessionGenerationIsCurrent(expectedGeneration)) return null
    return try {
        val context = RuntimeDiagnostics.eventContext()
        if (cloudEndpointRequiresAuthentication(endpoint)) context else context.copy(accountId = null, storeId = null)
    } catch (_: Exception) { null }
}
fun recordUnexpectedNetworkDiagnostic(context: DiagnosticContext?, error: Throwable) {
    if (context == null || error is kotlinx.coroutines.CancellationException) return
    // Routine connectivity/HTTP failures already have their own UI and are not application crashes.
    if (error is kotlinx.serialization.SerializationException || error is IllegalStateException)
        RuntimeDiagnostics.capture(error, "network.runtime", eventContext = context)
}
