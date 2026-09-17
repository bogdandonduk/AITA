package kz.aita

/** Called inside the platform's write lock. An older snapshot never overwrites a newer one. */
fun shouldWriteDiagnosticJournal(previous: String?, next: String): Boolean {
    require(next.length <= 2_000_000)
    val nextJournal = diagnosticJson.decodeFromString(DiagnosticJournal.serializer(), next).validated()
    if (previous == null) return true
    require(previous.length <= 2_000_000)
    val old = diagnosticJson.decodeFromString(DiagnosticJournal.serializer(), previous).validated()
    if (old.revision > nextJournal.revision) return false
    if (old.revision == nextJournal.revision) {
        check(old == nextJournal) { "Diagnostic journal changed in another app instance" }
        return false
    }
    check(old.installationId == nextJournal.installationId) { "Diagnostic installation changed during a write" }
    return true
}
