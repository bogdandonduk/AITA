package kz.aita.server

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kz.aita.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.json.jsonb
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.UUID

internal object DiagnosticEvents : Table("runtime_diagnostic_events") {
    val sequence = long("sequence").autoIncrement()
    val id = uuid("event_id").uniqueIndex()
    val installation = uuid("installation_id")
    val owner = uuid("owner_user_id").nullable()
    val received = long("received_at_millis")
    val report = jsonb("report", diagnosticJson, DiagnosticEvent.serializer())
    val country = varchar("country_code", 2).nullable()
    val region = varchar("region_code", 32).nullable()
    override val primaryKey = PrimaryKey(sequence)
}
@Serializable internal data class StoredDiagnostic(val sequence: Long, val receivedAtMillis: Long, val event: DiagnosticEvent,
    val country: String?, val region: String?, val locationSource: String)
@Serializable internal data class DiagnosticPage(val events: List<StoredDiagnostic>, val nextCursor: Long, val hasMore: Boolean)
@Serializable internal data class DiagnosticChanges(val cursor: Long, val changed: Boolean)
internal data class TrustedDiagnosticLocation(val country: String, val region: String?)
internal class DiagnosticConflict : IllegalStateException()
internal class DiagnosticQuotaReached : IllegalStateException()
internal interface DiagnosticRepository {
    val changes: MutableStateFlow<Long>
    suspend fun ingest(owner: UUID?, batch: DiagnosticBatch, location: TrustedDiagnosticLocation?, now: Long): List<String>
    suspend fun page(after: Long, limit: Int, now: Long): DiagnosticPage
    suspend fun latest(now: Long): Long
    suspend fun cleanup(now: Long)
}
internal class DatabaseDiagnostics(private val database: Database? = null) : DiagnosticRepository {
    override val changes = MutableStateFlow(0L)
    override suspend fun ingest(owner: UUID?, batch: DiagnosticBatch, location: TrustedDiagnosticLocation?, now: Long): List<String> {
        batch.validated()
        require(batch.events.all { it.context.accountId == owner?.toString() })
        val installation = UUID.fromString(batch.installationId)
        val accepted = newSuspendedTransaction(Dispatchers.IO, db = database) {
            exec("SET LOCAL statement_timeout = '5000ms'")
            exec("SET LOCAL lock_timeout = '2000ms'")
            // Serializes reporter quotas only, never inventory or payment operations.
            exec("SELECT pg_advisory_xact_lock(668910703441)")
            val ids = batch.events.map { UUID.fromString(it.id) }
            val existing = DiagnosticEvents.selectAll().where { DiagnosticEvents.id inList ids }.associateBy { it[DiagnosticEvents.id] }
            if (existing.values.any { it[DiagnosticEvents.owner] != owner || it[DiagnosticEvents.installation] != installation }) throw DiagnosticConflict()
            val fresh = batch.events.filter { UUID.fromString(it.id) !in existing }
            if (fresh.isNotEmpty()) {
                val dayStart = (now - 86_400_000L).coerceAtLeast(0)
                val installationCount = DiagnosticEvents.selectAll().where {
                    (DiagnosticEvents.installation eq installation) and (DiagnosticEvents.received greaterEq dayStart)
                }.count()
                val globalCount = DiagnosticEvents.selectAll().where { DiagnosticEvents.received greaterEq dayStart }.count()
                if (installationCount + fresh.size > 500 || globalCount + fresh.size > 20_000) throw DiagnosticQuotaReached()
            }
            if (DiagnosticEvents.selectAll().count() + fresh.size > 100_000) {
                exec("DELETE FROM runtime_diagnostic_events WHERE sequence IN (SELECT sequence FROM runtime_diagnostic_events ORDER BY sequence LIMIT 5000)")
            }
            for (event in fresh) {
                val requestedStore = event.context.storeId?.let(UUID::fromString)
                val verifiedStore = requestedStore?.takeIf { owner != null && userHasStoreAccessInsideTransaction(owner, it) }
                val safe = event.copy(context = event.context.copy(accountId = owner?.toString(), storeId = verifiedStore?.toString()))
                val geo = location?.takeIf { event.approximateLocation }
                DiagnosticEvents.insert {
                    it[id] = UUID.fromString(event.id); it[DiagnosticEvents.installation] = installation
                    it[DiagnosticEvents.owner] = owner; it[received] = now; it[report] = safe
                    it[country] = geo?.country; it[region] = geo?.region
                }
            }
            batch.events.map { it.id }
        }
        changes.update { if (it == Long.MAX_VALUE) 0 else it + 1 }
        return accepted
    }
    override suspend fun page(after: Long, limit: Int, now: Long): DiagnosticPage = newSuspendedTransaction(Dispatchers.IO, db = database) {
        require(after >= 0 && limit in 1..50)
        exec("SET LOCAL statement_timeout = '5000ms'")
        val rows = DiagnosticEvents.selectAll().where {
            (DiagnosticEvents.sequence greater after) and (DiagnosticEvents.received greaterEq (now - DIAGNOSTIC_RETENTION_MILLIS).coerceAtLeast(0))
        }.orderBy(DiagnosticEvents.sequence to SortOrder.ASC).limit(limit + 1).toList()
        val visible = rows.take(limit).map { row -> StoredDiagnostic(row[DiagnosticEvents.sequence], row[DiagnosticEvents.received],
            row[DiagnosticEvents.report], row[DiagnosticEvents.country], row[DiagnosticEvents.region],
            if (row[DiagnosticEvents.country] == null) "unavailable" else "trusted-gateway") }
        DiagnosticPage(visible, visible.lastOrNull()?.sequence ?: after, rows.size > limit)
    }
    override suspend fun latest(now: Long): Long = newSuspendedTransaction(Dispatchers.IO, db = database) {
        exec("SET LOCAL statement_timeout = '5000ms'")
        DiagnosticEvents.select(DiagnosticEvents.sequence).where {
            DiagnosticEvents.received greaterEq (now - DIAGNOSTIC_RETENTION_MILLIS).coerceAtLeast(0)
        }.orderBy(DiagnosticEvents.sequence to SortOrder.DESC).limit(1).singleOrNull()?.get(DiagnosticEvents.sequence) ?: 0L
    }
    override suspend fun cleanup(now: Long) = newSuspendedTransaction(Dispatchers.IO, db = database) {
        exec("SET LOCAL statement_timeout = '5000ms'")
        val expiry = (now - DIAGNOSTIC_RETENTION_MILLIS).coerceAtLeast(0)
        // A bounded sweep avoids holding a large deletion transaction during customer traffic.
        exec("DELETE FROM runtime_diagnostic_events WHERE sequence IN (SELECT sequence FROM runtime_diagnostic_events WHERE received_at_millis < $expiry ORDER BY sequence LIMIT 5000)")
        Unit
    }
}
