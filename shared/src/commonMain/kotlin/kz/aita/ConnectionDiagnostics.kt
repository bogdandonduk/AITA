package kz.aita

import io.ktor.client.request.*
import io.ktor.client.plugins.timeout
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.*

fun configuredConnectionHost(): String = runCatching { Url(globalAppConfigurationState.payloadValue.serverUrl.first).host }.getOrDefault("")

data class ConnectionProbeEvidence(
    val startedAtMillis: Long, val completedAtMillis: Long, val host: String,
    val httpStatus: Int?, val failure: ConnectionFailureKind
)
private val probeEvidence = MutableStateFlow<ConnectionProbeEvidence?>(null)
val connectionProbeEvidenceState = probeEvidence.asStateFlow()

internal fun recordConnectionProbe(started: Long, serverUrl: String, status: Int?, aita: Boolean,
                                   originError: Boolean = false, failureSummary: String? = null) {
    val host = runCatching { Url(serverUrl).host }.getOrDefault("")
    if (host.isBlank()) return
    val evidence = ConnectionProbeEvidence(started, getCurrentTimeMillis(), host, status,
        classifyConnectionFailure(status, aita, originError, failureSummary))
    while (true) {
        val old = probeEvidence.value
        if (old != null && old.startedAtMillis > started) return
        if (probeEvidence.compareAndSet(old, evidence)) return
    }
}

data class ConnectionDiagnosticResult(val host: String, val messageKey: String, val edgeHttpStatus: Int?,
    val originHttpStatus: Int?, val checkedAtMillis: Long)
private val diagnosticResult = MutableStateFlow<ConnectionDiagnosticResult?>(null)
val connectionDiagnosticResultState = diagnosticResult.asStateFlow()
private val diagnosticRunning = MutableStateFlow(false)
val connectionDiagnosticRunningState = diagnosticRunning.asStateFlow()

/** Public GETs only, no bearer tokens, no changes to production, and no unsafe TLS bypass. */
fun diagnoseAitaConnection() {
    if (!diagnosticRunning.compareAndSet(false, true)) return
    GlobalScope.launch(Dispatchers.ourIo) {
        try {
            val server = normalizedHttpServerUrlOrNull(globalAppConfigurationState.payloadValue.serverUrl.first) ?: return@launch
            val host = Url(server).host
            val outcome = withTimeoutOrNull(18_000L) {
                suspend fun probe(path: String): Triple<Int?, String?, Boolean> = try {
                    val response = cloudHealthHttpClient.get(networkTargetUrl(server, path)) {
                        timeout { requestTimeoutMillis = 8_000; connectTimeoutMillis = 5_000; socketTimeoutMillis = 8_000 }
                        header(HttpHeaders.CacheControl, "no-cache")
                    }
                    Triple(response.status.value, response.bodyAsText().take(16_384),
                        response.headers[AITA_SERVER_HEADER]?.equals(AITA_SERVER_HEADER_VALUE, ignoreCase = true) == true)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { Triple(null, null, false) }
                val edge = probe("_edge/health")
                val edgeJson = runCatching { jsonBase.parseToJsonElement(edge.second.orEmpty()).jsonObject }.getOrNull()
                val knownGateway = edge.first == 200 && edgeJson?.get("gateway")?.jsonPrimitive?.content == "aita-workers-vpc"
                val binding = edgeJson?.get("originBindingConfigured")?.jsonPrimitive?.booleanOrNull
                val origin = probe("readyz")
                val readyJson = runCatching { jsonBase.parseToJsonElement(origin.second.orEmpty()).jsonObject }.getOrNull()
                val ready = origin.first?.let { it in 200..299 } == true && readyJson?.get("status")?.jsonPrimitive?.content == "ready"
                val key = connectionDiagnosticMessage(knownGateway, binding, edge.first, origin.first,
                    origin.third, ready)
                ConnectionDiagnosticResult(host, key, edge.first, origin.first, getCurrentTimeMillis())
            } ?: ConnectionDiagnosticResult(host, "connection.timeout", null, null, getCurrentTimeMillis())
            if (normalizedHttpServerUrlOrNull(globalAppConfigurationState.payloadValue.serverUrl.first) == server)
                diagnosticResult.value = outcome
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* Keep the previous timestamped evidence, never manufacture a healthy result. */ }
        finally { diagnosticRunning.value = false }
    }
}
