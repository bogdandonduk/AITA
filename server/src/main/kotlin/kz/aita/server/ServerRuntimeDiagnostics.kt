package kz.aita.server

import io.ktor.server.application.Application
import io.ktor.util.AttributeKey
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kz.aita.*
import java.util.UUID

private val serverDiagnosticCapture = AttributeKey<(Throwable) -> Unit>("AitaServerDiagnosticCapture")
internal fun Application.captureServerDiagnostic(error: Throwable) {
    if (error is CancellationException) return
    runCatching { attributes.getOrNull(serverDiagnosticCapture)?.invoke(error) }
}
internal fun Application.installServerDiagnosticCapture(repository: DiagnosticRepository, settings: DiagnosticServerSettings,
    scope: CoroutineScope) {
    if (!settings.enabled) return
    val installation = UUID.randomUUID().toString()
    val session = UUID.randomUUID().toString()
    val events = Channel<DiagnosticEvent>(DIAGNOSTIC_QUEUE_LIMIT)
    val context = DiagnosticContext(version = System.getenv("AITA_RELEASE_VERSION") ?: environment.config.propertyOrNull("app.version")?.getString() ?: "server",
        revision = (System.getenv("AITA_RELEASE_REVISION") ?: System.getenv("AITA_RELEASE_COMMIT")).orEmpty(), channel = "server",
        device = DiagnosticDevice("server", System.getProperty("os.name"), "JVM", System.getProperty("os.arch")),
        workspace = "server", screen = "request").sanitized()
    attributes.put(serverDiagnosticCapture) { error ->
        val frames = diagnosticStackFrames(error.stackTraceToString())
        val type = diagnosticCode(error.javaClass.simpleName, 120).ifBlank { "ServerFailure" }
        events.trySend(DiagnosticEvent(UUID.randomUUID().toString(), session, System.currentTimeMillis(),
            "server.request", type, frames, false, context))
    }
    scope.launch {
        val recent = LinkedHashMap<String, Long>()
        for (event in events) {
            val signature = event.errorType + event.frames.joinToString()
            val now = System.currentTimeMillis()
            recent.entries.removeAll { now - it.value > 60_000 }
            if (signature in recent) continue
            if (recent.size >= 100) recent.remove(recent.keys.first())
            recent[signature] = now
            // Never block the failing request or recursively report a diagnostics DB outage.
            try { withTimeout(7_000) { repository.ingest(null, DiagnosticBatch(installation, listOf(event)), null, now) } }
            catch (cancelled: CancellationException) { if (cancelled !is TimeoutCancellationException) throw cancelled }
            catch (_: Exception) { /* Existing server journal remains the source during database outages. */ }
        }
    }.invokeOnCompletion { events.close() }
}
