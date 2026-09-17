package kz.aita.server

import kotlinx.coroutines.runBlocking
import kz.aita.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Assume.assumeTrue
import java.sql.DriverManager
import java.util.UUID
import kotlin.test.*

/** Opt-in tests only against a freshly created loopback PostgreSQL test database. */
class DiagnosticDatabaseTest {
    private val owner = UUID.fromString("11111111-1111-4111-8111-111111111111")
    private val other = UUID.fromString("22222222-2222-4222-8222-222222222222")
    private val now = 1_800_000_000_000L
    private fun event(account: UUID? = null, geo: Boolean = false) = DiagnosticEvent(
        UUID.randomUUID().toString(), UUID.randomUUID().toString(), now, "runtime", "IllegalStateException",
        listOf("at kz.aita.Checkout.render(Checkout.kt:42)"), false,
        DiagnosticContext("1.0.0", device = DiagnosticDevice("desktop"), accountId = account?.toString()), geo)
    private fun fixture(block: suspend (Database, DatabaseDiagnostics) -> Unit) = runBlocking {
        val url = System.getenv("AITA_DIAGNOSTICS_TEST_JDBC").orEmpty()
        assumeTrue("Requires disposable aita_diagnostics_test database", url.isNotBlank())
        require(Regex("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/aita_diagnostics_test").matches(url))
        val user = System.getProperty("user.name")
        val schema = "diagnostics_test_" + UUID.randomUUID().toString().replace("-", "")
        DriverManager.getConnection(url, user, "").use { c -> c.createStatement().use { it.execute("CREATE SCHEMA $schema") } }
        val db = Database.connect("$url?currentSchema=$schema", driver = "org.postgresql.Driver", user = user, password = "")
        try {
            transaction(db) {
                exec("CREATE TABLE users(id UUID PRIMARY KEY)")
                exec("INSERT INTO users(id) VALUES ('$owner'), ('$other')")
                exec(javaClass.getResource("/db/migration/V116__runtime_diagnostics.sql")!!.readText())
            }
            block(db, DatabaseDiagnostics(db))
        } finally {
            TransactionManager.closeAndUnregister(db)
            DriverManager.getConnection(url, user, "").use { c -> c.createStatement().use { it.execute("DROP SCHEMA $schema CASCADE") } }
        }
    }
    @Test fun realMigrationPersistsAnonymousAndOwnedReportsAndPaginates() = fixture { db, repo ->
        val anonymous = DiagnosticBatch(UUID.randomUUID().toString(), listOf(event()))
        val owned = DiagnosticBatch(UUID.randomUUID().toString(), listOf(event(owner)))
        assertEquals(anonymous.events.map { it.id }, repo.ingest(null, anonymous, null, now))
        assertEquals(owned.events.map { it.id }, repo.ingest(owner, owned, null, now))
        val restarted = DatabaseDiagnostics(db)
        val first = restarted.page(0, 1, now)
        assertEquals(1, first.events.size); assertTrue(first.hasMore)
        assertNull(first.events.single().event.context.accountId)
        val next = restarted.page(first.nextCursor, 1, now)
        assertEquals(owner.toString(), next.events.single().event.context.accountId)
        assertFalse(next.hasMore); assertTrue(next.nextCursor > first.nextCursor)
        assertEquals(next.nextCursor, restarted.latest(now))
        assertTrue(restarted.page(next.nextCursor, 1, now).events.isEmpty())
    }
    @Test fun retriesDeduplicateAndForeignInstallationsCannotClaimEventIds() = fixture { _, repo ->
        val batch = DiagnosticBatch(UUID.randomUUID().toString(), listOf(event(owner)))
        repo.ingest(owner, batch, null, now)
        repo.ingest(owner, batch, null, now + 1)
        assertEquals(1, repo.page(0, 50, now).events.size)
        assertFailsWith<DiagnosticConflict> { repo.ingest(owner, batch.copy(installationId = UUID.randomUUID().toString()), null, now) }
        val forged = batch.copy(events = batch.events.map { it.copy(context = it.context.copy(accountId = other.toString())) })
        assertFailsWith<DiagnosticConflict> { repo.ingest(other, forged, null, now) }
        assertEquals(owner.toString(), repo.page(0, 50, now).events.single().event.context.accountId)
    }
    @Test fun coarseLocationRequiresEventConsentEvenWithTrustedGateway() = fixture { _, repo ->
        val batch = DiagnosticBatch(UUID.randomUUID().toString(), listOf(event(owner), event(owner, geo = true)))
        repo.ingest(owner, batch, TrustedDiagnosticLocation("KZ", "AST"), now)
        val rows = repo.page(0, 50, now).events
        assertNull(rows[0].country); assertNull(rows[0].region); assertEquals("unavailable", rows[0].locationSource)
        assertEquals("KZ", rows[1].country); assertEquals("AST", rows[1].region)
        assertEquals("trusted-gateway", rows[1].locationSource)
    }
    @Test fun retentionExcludesExpiredReportsAndCleanupDeletesThem() = fixture { db, repo ->
        val batch = DiagnosticBatch(UUID.randomUUID().toString(), listOf(event()))
        repo.ingest(null, batch, null, now - DIAGNOSTIC_RETENTION_MILLIS - 1)
        assertTrue(repo.page(0, 50, now).events.isEmpty()); assertEquals(0L, repo.latest(now))
        repo.cleanup(now)
        transaction(db) { exec("SELECT count(*) FROM runtime_diagnostic_events") { rs -> assertTrue(rs.next()); assertEquals(0, rs.getInt(1)) } }
    }
    @Test fun databaseQuotaSurvivesRepositoryRestartAndStillAllowsAcknowledgedRetries() = fixture { db, repo ->
        val installation = UUID.randomUUID().toString()
        val events = List(500) { event() }
        events.chunked(DIAGNOSTIC_BATCH_LIMIT).forEach { repo.ingest(null, DiagnosticBatch(installation, it), null, now) }
        val restarted = DatabaseDiagnostics(db)
        assertFailsWith<DiagnosticQuotaReached> { restarted.ingest(null, DiagnosticBatch(installation, listOf(event())), null, now) }
        assertEquals(listOf(events[0].id), restarted.ingest(null, DiagnosticBatch(installation, listOf(events[0])), null, now))
        transaction(db) { exec("SELECT count(*) FROM runtime_diagnostic_events") { rs -> assertTrue(rs.next()); assertEquals(500, rs.getInt(1)) } }
    }
    @Test fun schemaRejectsOwnerAndConsentMismatches() = fixture { db, repo ->
        val batch = DiagnosticBatch(UUID.randomUUID().toString(), listOf(event(owner)))
        repo.ingest(owner, batch, null, now)
        for (sql in listOf(
            "UPDATE runtime_diagnostic_events SET owner_user_id = '$other'",
            "UPDATE runtime_diagnostic_events SET country_code = 'KZ'",
            "UPDATE runtime_diagnostic_events SET report = '{}'::jsonb"
        )) assertFails { transaction(db) { exec(sql) } }
        assertEquals(owner.toString(), repo.page(0, 1, now).events.single().event.context.accountId)
    }
}
