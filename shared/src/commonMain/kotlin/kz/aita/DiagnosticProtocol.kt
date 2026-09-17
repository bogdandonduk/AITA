package kz.aita

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

const val DIAGNOSTIC_QUEUE_LIMIT = 100
const val DIAGNOSTIC_BATCH_LIMIT = 8
const val DIAGNOSTIC_RETENTION_MILLIS = 30L * 24 * 60 * 60 * 1000
const val DIAGNOSTIC_BODY_LIMIT = 65_536
val diagnosticJson = Json { ignoreUnknownKeys = false; encodeDefaults = true }
private val diagnosticUuid = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
fun validDiagnosticId(value: String): Boolean = diagnosticUuid.matches(value)
fun diagnosticCode(value: String, maximum: Int = 80): String = value.trim().takeIf {
    it.length in 1..maximum && it.all { c -> c.isLetterOrDigit() || c in "._- $" } &&
        !it.contains(Regex("(?i)(gh[pousr]_|github_pat_|bearer|password|secret|token=)"))
}.orEmpty()

@Serializable data class DiagnosticDevice(val platform: String, val os: String = "", val model: String = "", val architecture: String = "")
@Serializable data class DiagnosticContext(
    val version: String = "", val build: Long = 1, val channel: String = "", val revision: String = "",
    val device: DiagnosticDevice = DiagnosticDevice("unknown"), val accountId: String? = null,
    val workspace: String = "startup", val screen: String = "startup", val language: String = "en", val storeId: String? = null
)
@Serializable data class DiagnosticEvent(val id: String, val sessionId: String, val occurredAtMillis: Long,
    val category: String, val errorType: String, val frames: List<String>, val fatal: Boolean,
    val context: DiagnosticContext, val approximateLocation: Boolean = false)
@Serializable data class DiagnosticBatch(val installationId: String, val events: List<DiagnosticEvent>)
@Serializable data class DiagnosticAck(val installationId: String, val ownerAccountId: String?, val acceptedIds: List<String>, val receivedAtMillis: Long)
@Serializable data class DiagnosticJournal(
    val schema: Int = 1, val revision: Long = 0, val installationId: String = "",
    val enabled: Boolean = false, val approximateLocation: Boolean = false,
    val events: List<DiagnosticEvent> = emptyList(), val failures: Int = 0, val nextAttemptAtMillis: Long = 0,
    val dropped: Long = 0, val lastSentAtMillis: Long = 0, val updatedAtMillis: Long = 0
)
interface DiagnosticLocalStorage { fun read(): String?; fun write(value: String); fun reset(value: String) = write(value) }
fun diagnosticJournalRevision(raw: String?): Long = raw?.let {
    require(it.length <= 2_000_000)
    diagnosticJson.parseToJsonElement(it).jsonObject["revision"]?.jsonPrimitive?.longOrNull
} ?: -1L
fun diagnosticCanUpload(event: DiagnosticEvent, accountId: String?): Boolean =
    event.context.accountId == null || event.context.accountId == accountId

/** Only structured frames; never retain the exception message, URLs, paths, SQL or request bodies. */
fun diagnosticStackFrames(raw: String): List<String> {
    val javaFrame = Regex("^\\s*at ([A-Za-z_$][A-Za-z0-9_.$]*)\\(([A-Za-z0-9_.$-]+\\.(?:kt|java|js|wasm)(?::[0-9]{1,7})?|Unknown Source|Native Method)\\)\\s*$")
    val nativeSymbol = Regex("^([A-Za-z_$][A-Za-z0-9_.$]{0,159})$")
    return raw.take(32_768).lineSequence().mapNotNull { line ->
        val match = javaFrame.matchEntire(line)
        if (match != null) "at ${match.groupValues[1]}(${match.groupValues[2]})"
        else line.trim().takeIf { it.startsWith("symbol ") }?.removePrefix("symbol ")?.takeIf { nativeSymbol.matches(it) }?.let { "symbol $it" }
    }.filter { it.length <= 240 }.filterNot { it.contains(Regex("(?i)(gh[pousr]_|github_pat_|password|bearer|secret=)")) }.take(32).toList()
}
fun DiagnosticContext.sanitized(): DiagnosticContext = copy(
    version = diagnosticCode(version, 40), build = build.coerceIn(1, 2_100_000_000), channel = diagnosticCode(channel, 20),
    revision = diagnosticCode(revision, 64), device = DiagnosticDevice(diagnosticCode(device.platform, 30),
        diagnosticCode(device.os, 120), diagnosticCode(device.model, 120), diagnosticCode(device.architecture, 40)),
    accountId = accountId?.takeIf(::validDiagnosticId), workspace = diagnosticCode(workspace, 40),
    screen = diagnosticCode(screen, 80), language = language.takeIf { it in SUPPORTED_APP_LANGUAGES } ?: "en",
    storeId = storeId?.takeIf { accountId?.let(::validDiagnosticId) == true && validDiagnosticId(it) }
)
fun DiagnosticEvent.validated(): DiagnosticEvent {
    require(validDiagnosticId(id) && validDiagnosticId(sessionId) && occurredAtMillis > 0)
    require(category.isNotBlank() && category == diagnosticCode(category))
    require(errorType.isNotBlank() && errorType == diagnosticCode(errorType, 120))
    require(context == context.sanitized() && context.version.isNotBlank() && context.device.platform.isNotBlank())
    require(frames.size <= 32 && frames.all { it.length <= 240 })
    require(frames == diagnosticStackFrames(frames.joinToString("\n")))
    return this
}
fun DiagnosticBatch.validated(): DiagnosticBatch {
    require(validDiagnosticId(installationId) && events.size in 1..DIAGNOSTIC_BATCH_LIMIT)
    require(events.map { it.id }.distinct().size == events.size)
    events.forEach { it.validated() }
    require(diagnosticJson.encodeToString(DiagnosticBatch.serializer(), this).encodeToByteArray().size <= DIAGNOSTIC_BODY_LIMIT)
    return this
}
fun DiagnosticJournal.validated(): DiagnosticJournal {
    require(schema == 1 && revision >= 0 && validDiagnosticId(installationId) && events.size <= DIAGNOSTIC_QUEUE_LIMIT)
    require(failures in 0..20 && nextAttemptAtMillis >= 0 && dropped >= 0 && lastSentAtMillis >= 0 && updatedAtMillis >= 0)
    require(events.map { it.id }.distinct().size == events.size)
    events.forEach { it.validated() }
    require(enabled || events.isEmpty())
    return this
}
