package kz.aita

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*

/** Thread-safe immutable journal. The platform adapter serializes durable revision writes. */
internal class DiagnosticRecorder(
    private val storage: DiagnosticLocalStorage,
    private val newId: () -> String,
    private val now: () -> Long,
    private val publish: (DiagnosticJournal) -> Unit = {}
) {
    private val mutable = MutableStateFlow(DiagnosticJournal())
    val state = mutable.asStateFlow()
    val storageFailed = MutableStateFlow(false)
    val corrupt = MutableStateFlow(false)
    private val session = newId().also { require(validDiagnosticId(it)) }
    init {
        val fresh = DiagnosticJournal(installationId = newId().also { require(validDiagnosticId(it)) }, updatedAtMillis = now().coerceAtLeast(0))
        val initial = try { storage.read()?.let { diagnosticJson.decodeFromString(DiagnosticJournal.serializer(), it).validated() } ?: fresh }
            catch (_: Exception) { storageFailed.value = true; corrupt.value = true; fresh }
        mutable.value = initial; publish(initial)
        if (!corrupt.value) persist()
    }
    fun persist(): Boolean {
        if (corrupt.value) return false
        return try {
            val raw = diagnosticJson.encodeToString(DiagnosticJournal.serializer(), state.value.validated())
            require(raw.length <= 2_000_000)
            storage.write(raw); storageFailed.value = false; true
        } catch (_: Exception) { storageFailed.value = true; false }
    }
    private fun change(transform: (DiagnosticJournal) -> DiagnosticJournal): Boolean {
        if (corrupt.value) return false
        while (true) {
            val old = state.value
            val next = transform(old)
            if (next == old) return true
            if (old.revision == Long.MAX_VALUE) { storageFailed.value = true; return false }
            val value = next.copy(revision = old.revision + 1, updatedAtMillis = now().coerceAtLeast(0))
            if (mutable.compareAndSet(old, value)) { publish(value); return persist() }
        }
    }
    fun setEnabled(enabled: Boolean) = change { old -> old.copy(enabled = enabled,
        events = if (enabled) old.events else emptyList(), failures = 0, nextAttemptAtMillis = 0) }
    fun setLocation(enabled: Boolean) = change { old -> old.copy(approximateLocation = enabled,
        events = if (enabled) old.events else old.events.map { it.copy(approximateLocation = false) }) }
    fun clear(accountId: String?) = change { old -> old.copy(events = old.events.filterNot { diagnosticCanUpload(it, accountId) },
        failures = 0, nextAttemptAtMillis = 0) }
    fun capture(error: Throwable, category: String, context: DiagnosticContext, fatal: Boolean, frames: List<String>, explicitType: String? = null) {
        if (error is CancellationException || !state.value.enabled || corrupt.value) return
        val time = now()
        val event = DiagnosticEvent(newId(), session, time, diagnosticCode(category).ifBlank { "runtime" },
            diagnosticCode(explicitType ?: error::class.simpleName.orEmpty(), 120).ifBlank { "Throwable" },
            diagnosticStackFrames(frames.joinToString("\n")), fatal, context.sanitized(), state.value.approximateLocation).validated()
        change { old ->
            if (!old.enabled) old else {
                val recent = old.events.filter { time - it.occurredAtMillis <= DIAGNOSTIC_RETENTION_MILLIS }
                val duplicate = recent.any { time - it.occurredAtMillis in 0..60_000L && it.category == event.category &&
                    it.errorType == event.errorType && it.frames == event.frames && it.context == event.context && it.fatal == event.fatal }
                if (duplicate) old else {
                    val next = recent + event.copy(approximateLocation = old.approximateLocation)
                    old.copy(events = next.takeLast(DIAGNOSTIC_QUEUE_LIMIT),
                        dropped = minOf(old.dropped, Long.MAX_VALUE - 1 - (next.size - DIAGNOSTIC_QUEUE_LIMIT).coerceAtLeast(0)) + (next.size - DIAGNOSTIC_QUEUE_LIMIT).coerceAtLeast(0))
                }
            }
        }
    }
    fun batch(accountId: String?, force: Boolean = false): DiagnosticBatch? {
        val time = now()
        val journal = state.value
        if (!journal.enabled || corrupt.value || (!force && journal.nextAttemptAtMillis > time && journal.nextAttemptAtMillis - time <= 900_000L)) return null
        change { it.copy(events = it.events.filter { event -> time - event.occurredAtMillis <= DIAGNOSTIC_RETENTION_MILLIS }) }
        if (!persist()) return null
        val current = state.value
        val eligible = current.events.filter { diagnosticCanUpload(it, accountId) }
        val owner = eligible.firstOrNull()?.context?.accountId
        val selected = eligible.filter { it.context.accountId == owner }.take(DIAGNOSTIC_BATCH_LIMIT).toMutableList()
        while (selected.isNotEmpty()) {
            val batch = DiagnosticBatch(current.installationId, selected.toList())
            if (diagnosticJson.encodeToString(DiagnosticBatch.serializer(), batch).encodeToByteArray().size <= DIAGNOSTIC_BODY_LIMIT) return batch
            selected.removeAt(selected.lastIndex)
        }
        return null
    }
    fun acknowledge(batch: DiagnosticBatch, ack: DiagnosticAck): Boolean {
        val ids = batch.events.map { it.id }.toSet()
        val owner = batch.events.firstOrNull()?.context?.accountId
        if (ack.installationId != batch.installationId || ack.ownerAccountId != owner || ack.receivedAtMillis <= 0 ||
            ack.acceptedIds.isEmpty() || ack.acceptedIds.distinct().size != ack.acceptedIds.size || ack.acceptedIds.any { it !in ids }) return false
        return change { old -> old.copy(events = old.events.filterNot { it.id in ack.acceptedIds && it.context.accountId == owner },
            failures = 0, nextAttemptAtMillis = 0, lastSentAtMillis = now()) }
    }
    fun failedAttempt() = change { old ->
        val failures = (old.failures + 1).coerceAtMost(20)
        val delay = minOf(900_000L, 15_000L * (1L shl minOf(failures - 1, 6)))
        old.copy(failures = failures, nextAttemptAtMillis = now().coerceIn(0, Long.MAX_VALUE - delay) + delay)
    }
    fun resetDamagedJournal(): Boolean {
        if (!corrupt.value) return false
        val fresh = DiagnosticJournal(installationId = newId(), updatedAtMillis = now().coerceAtLeast(0))
        return try {
            storage.reset(diagnosticJson.encodeToString(DiagnosticJournal.serializer(), fresh.validated()))
            mutable.value = fresh; corrupt.value = false; storageFailed.value = false; publish(fresh); true
        } catch (_: Exception) { storageFailed.value = true; false }
    }
}
