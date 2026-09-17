package kz.aita

import kotlinx.coroutines.CancellationException
import kotlin.test.*

class DiagnosticRecorderTest {
    private val accountA = "11111111-1111-4111-8111-111111111111"
    private val accountB = "22222222-2222-4222-8222-222222222222"
    private val frame = "at kz.aita.Checkout.render(Checkout.kt:42)"
    private class Memory : DiagnosticLocalStorage {
        var raw: String? = null
        var fail = false
        override fun read() = raw
        override fun write(value: String) { check(!fail); if (shouldWriteDiagnosticJournal(raw, value)) raw = value }
        override fun reset(value: String) { check(!fail); raw = value }
    }
    private inner class Fixture(val memory: Memory = Memory()) {
        var time = 1_800_000_000_000L
        var id = 0
        val recorder = DiagnosticRecorder(memory, { "aaaaaaaa-aaaa-4aaa-8aaa-${(++id).toString().padStart(12, '0')}" }, { time })
        fun context(owner: String? = accountA) = DiagnosticContext("1.2.3", 9, "RELEASE", "abc1234",
            DiagnosticDevice("android", "Android 16", "Device model", "arm64"), owner, "Store", "Checkout", "en")
        fun capture(owner: String? = accountA, category: String = "printer.usb", fatal: Boolean = false,
            error: Throwable = IllegalStateException("password=private-example user@example.invalid payment=12345")) {
            recorder.capture(error, category, context(owner), fatal, listOf(frame))
        }
        fun ack(batch: DiagnosticBatch, ids: List<String> = batch.events.map { it.id }) =
            DiagnosticAck(batch.installationId, batch.events.first().context.accountId, ids, time)
    }
    @Test fun disabledByDefaultAndNoMessageOrRequestDataEntersJournal() {
        val f = Fixture(); f.capture(); assertTrue(f.recorder.state.value.events.isEmpty())
        assertFalse(f.recorder.state.value.enabled)
        f.recorder.setEnabled(true); f.capture()
        val raw = f.memory.raw!!
        for (secret in listOf("private-example", "user@example", "payment=", "12345", "password=")) assertFalse(raw.contains(secret))
        assertEquals(listOf(frame), f.recorder.state.value.events.single().frames)
        assertEquals("IllegalStateException", f.recorder.state.value.events.single().errorType)
    }
    @Test fun redactionDropsMessagesPathsUrlsQueriesAndUnstructuredText() {
        val raw = """IllegalStateException: password=secret user@example.invalid
            at kz.aita.Safe.run(Safe.kt:9)
            at fetch (https://example.invalid/x?token=secret:10:20)
            at other(/home/private/user/Secret.kt:9)
            account data
            symbol kz.aita.Native.call
        """.trimIndent()
        assertEquals(listOf("at kz.aita.Safe.run(Safe.kt:9)", "symbol kz.aita.Native.call"), diagnosticStackFrames(raw))
        val sanitized = diagnosticStackFrames(raw)
        assertEquals(sanitized, diagnosticStackFrames(sanitized.joinToString("\n")))
    }
    @Test fun cancellationsAreNotReportedAndRepeatedErrorsAreDeduplicated() {
        val f = Fixture(); f.recorder.setEnabled(true)
        f.capture(error = CancellationException()); assertTrue(f.recorder.state.value.events.isEmpty())
        f.capture(); f.capture(); assertEquals(1, f.recorder.state.value.events.size)
        f.time += 60_001; f.capture(); assertEquals(2, f.recorder.state.value.events.size)
    }
    @Test fun queueIsBoundedAndExpiresOldEventsWithoutUploadingThem() {
        val f = Fixture(); f.recorder.setEnabled(true)
        repeat(120) { f.capture(category = "runtime.event_$it") }
        assertEquals(100, f.recorder.state.value.events.size); assertEquals(20, f.recorder.state.value.dropped)
        assertEquals("runtime.event_20", f.recorder.state.value.events.first().category)
        f.time += DIAGNOSTIC_RETENTION_MILLIS + 1
        assertNull(f.recorder.batch(accountA)); assertTrue(f.recorder.state.value.events.isEmpty())
    }
    @Test fun reportOwnershipIsFrozenAndAnonymousReportsNeverBecomeSignedIn() {
        val f = Fixture(); f.recorder.setEnabled(true)
        f.capture(accountA); f.capture(null); f.capture(accountB)
        val anonymous = assertNotNull(f.recorder.batch(accountB))
        assertTrue(anonymous.events.all { it.context.accountId == null })
        assertTrue(f.recorder.acknowledge(anonymous, f.ack(anonymous)))
        val owned = assertNotNull(f.recorder.batch(accountB))
        assertTrue(owned.events.all { it.context.accountId == accountB })
        assertTrue(f.recorder.acknowledge(owned, f.ack(owned)))
        assertNull(f.recorder.batch(accountB)); assertNull(f.recorder.batch(null))
        assertEquals(accountA, assertNotNull(f.recorder.batch(accountA)).events.single().context.accountId)
    }
    @Test fun foreignDuplicateAndUnknownAcknowledgmentsCannotDeleteReports() {
        val f = Fixture(); f.recorder.setEnabled(true); f.capture()
        val batch = assertNotNull(f.recorder.batch(accountA)); val ack = f.ack(batch)
        for (bad in listOf(ack.copy(installationId = accountB), ack.copy(ownerAccountId = accountB),
            ack.copy(acceptedIds = listOf(accountB)), ack.copy(acceptedIds = ack.acceptedIds + ack.acceptedIds),
            ack.copy(acceptedIds = emptyList()), ack.copy(receivedAtMillis = 0))) {
            assertFalse(f.recorder.acknowledge(batch, bad)); assertEquals(1, f.recorder.state.value.events.size)
        }
        f.capture(category = "another.error")
        assertTrue(f.recorder.acknowledge(batch, ack)); assertEquals("another.error", f.recorder.state.value.events.single().category)
    }
    @Test fun partialAcknowledgmentPreservesRemainingReports() {
        val f = Fixture(); f.recorder.setEnabled(true)
        repeat(12) { f.capture(category = "runtime.$it") }
        val batch = assertNotNull(f.recorder.batch(accountA)); assertEquals(8, batch.events.size)
        assertTrue(f.recorder.acknowledge(batch, f.ack(batch, batch.events.take(2).map { it.id })))
        assertEquals(10, f.recorder.state.value.events.size)
    }
    @Test fun failedStoragePreventsUploadAndRetainsPendingEvents() {
        val f = Fixture(); f.recorder.setEnabled(true); f.memory.fail = true
        f.capture(); assertTrue(f.recorder.storageFailed.value)
        assertNull(f.recorder.batch(accountA)); assertEquals(1, f.recorder.state.value.events.size)
        f.memory.fail = false; assertNotNull(f.recorder.batch(accountA)); assertFalse(f.recorder.storageFailed.value)
    }
    @Test fun backoffSurvivesRestoreAndCannotWaitForeverAfterClockRollback() {
        val f = Fixture(); f.recorder.setEnabled(true); f.capture(); f.recorder.failedAttempt()
        assertEquals(f.time + 15_000, f.recorder.state.value.nextAttemptAtMillis)
        val restored = Fixture(f.memory); assertNull(restored.recorder.batch(accountA))
        restored.time += 15_000; assertNotNull(restored.recorder.batch(accountA))
        repeat(20) { restored.recorder.failedAttempt() }
        assertTrue(restored.recorder.state.value.nextAttemptAtMillis - restored.time <= 900_000)
        restored.time -= 1_000_000; assertNotNull(restored.recorder.batch(accountA))
    }
    @Test fun disablingClearsAllUnsentAccountsAndRevokingLocationStripsExistingReports() {
        val f = Fixture(); f.recorder.setEnabled(true); f.recorder.setLocation(true)
        f.capture(accountA); f.capture(accountB); f.capture(null)
        assertTrue(f.recorder.state.value.events.all { it.approximateLocation })
        f.recorder.setLocation(false); assertTrue(f.recorder.state.value.events.none { it.approximateLocation })
        f.recorder.clear(accountA); assertEquals(accountB, f.recorder.state.value.events.single().context.accountId)
        f.recorder.setEnabled(false); assertTrue(f.recorder.state.value.events.isEmpty())
        assertTrue(Fixture(f.memory).recorder.state.value.events.isEmpty())
    }
    @Test fun damagedJournalIsPreservedUntilExplicitReset() {
        val memory = Memory().apply { raw = "{unfinished" }
        val f = Fixture(memory)
        assertTrue(f.recorder.corrupt.value); assertFalse(f.recorder.state.value.enabled)
        assertFalse(f.recorder.setEnabled(true)); assertNull(f.recorder.batch(accountA)); assertEquals("{unfinished", memory.raw)
        assertTrue(f.recorder.resetDamagedJournal()); assertFalse(f.recorder.corrupt.value)
        assertFalse(f.recorder.state.value.enabled); assertTrue(f.recorder.state.value.events.isEmpty())
    }
    @Test fun staleWritesAreIgnoredAndEqualRevisionConflictsFailClosed() {
        val f = Fixture(); val initial = f.memory.raw!!
        f.recorder.setEnabled(true); val later = f.memory.raw!!
        assertFalse(shouldWriteDiagnosticJournal(later, initial))
        assertFalse(shouldWriteDiagnosticJournal(later, later))
        val conflicting = diagnosticJson.decodeFromString(DiagnosticJournal.serializer(), later).copy(approximateLocation = true)
        assertFailsWith<IllegalStateException> { shouldWriteDiagnosticJournal(later, diagnosticJson.encodeToString(DiagnosticJournal.serializer(), conflicting)) }
    }
    @Test fun protocolRejectsInvalidIdentityOversizedMetadataAndUnredactedFrames() {
        val f = Fixture(); f.recorder.setEnabled(true); f.capture()
        val event = f.recorder.state.value.events.single()
        assertFailsWith<IllegalArgumentException> { event.copy(id = "not-an-id").validated() }
        assertFailsWith<IllegalArgumentException> { event.copy(frames = listOf("password=secret")).validated() }
        assertFailsWith<IllegalArgumentException> { event.copy(context = event.context.copy(device = DiagnosticDevice("a".repeat(300)))).validated() }
        assertFailsWith<IllegalArgumentException> { DiagnosticBatch(f.recorder.state.value.installationId, List(9) { event }).validated() }
        for (template in diagnosticMessageTemplates()) for (language in SUPPORTED_APP_LANGUAGES) assertNotNull(template.exactText(language))
    }
}
