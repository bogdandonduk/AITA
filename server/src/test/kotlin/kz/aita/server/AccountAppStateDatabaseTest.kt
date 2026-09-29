package kz.aita.server

import kotlinx.coroutines.*
import kz.aita.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Assume.assumeTrue
import java.sql.DriverManager
import java.util.UUID
import kotlin.test.*

class AccountAppStateDatabaseTest {
    private val owner = UUID.fromString("11111111-1111-4111-8111-111111111111")
    private val other = UUID.fromString("22222222-2222-4222-8222-222222222222")
    private val scope = AppStateScope(0)
    private val doc = AppStateDocument(drafts = mapOf("search" to "Milk"))
    private fun fixture(block: suspend (AccountAppStateRepository) -> Unit) = runBlocking {
        val url = System.getenv("AITA_APP_STATE_TEST_JDBC").orEmpty()
        assumeTrue("Requires disposable aita_app_state_test database", url.isNotBlank())
        require(Regex("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/aita_app_state_test").matches(url))
        val user = System.getProperty("user.name")
        val schema = "app_state_test_" + UUID.randomUUID().toString().replace("-", "")
        DriverManager.getConnection(url, user, "").use { c -> c.createStatement().use { it.execute("CREATE SCHEMA $schema") } }
        val db = Database.connect("$url?currentSchema=$schema", driver = "org.postgresql.Driver", user = user, password = "")
        try {
            transaction(db) {
                exec("CREATE TABLE users(id UUID PRIMARY KEY, is_active boolean NOT NULL DEFAULT true)")
                exec("INSERT INTO users(id) VALUES ('$owner'), ('$other')")
                exec(javaClass.getResource("/db/migration/V117__account_app_state.sql")!!.readText())
            }
            block(AccountAppStateRepository(db))
        } finally {
            TransactionManager.closeAndUnregister(db)
            DriverManager.getConnection(url, user, "").use { c -> c.createStatement().use { it.execute("DROP SCHEMA $schema CASCADE") } }
        }
    }
    @Test fun savesAndRestoresWithoutMixingAccountsOrModes() = fixture { repo ->
        val saved = repo.write(owner, AppStateWrite(scope, 0, true, doc))
        assertEquals(1L, saved.state.revision); assertFalse(saved.conflict)
        assertEquals(doc, repo.read(owner, scope).document)
        assertNull(repo.read(other, scope).document)
        assertNull(repo.read(owner, AppStateScope(1)).document)
    }
    @Test fun concurrentFirstSavesCannotBothOverwrite() = fixture { repo -> coroutineScope {
        val results = List(8) { async(Dispatchers.IO) { repo.write(owner, AppStateWrite(scope, 0, true, doc)) } }.awaitAll()
        assertEquals(1, results.count { !it.conflict })
        assertEquals(1L, repo.read(owner, scope).revision)
    } }
    @Test fun turningOffErasesCopyAndRejectsStaleDevice() = fixture { repo ->
        repo.write(owner, AppStateWrite(scope, 0, true, doc))
        val disabled = repo.write(owner, AppStateWrite(scope, 1, false)).state
        assertFalse(disabled.enabled); assertNull(disabled.document)
        val stale = repo.write(owner, AppStateWrite(scope, 1, true, doc))
        assertTrue(stale.conflict); assertEquals(disabled, stale.state)
    }
    @Test fun oversizedOrSensitiveDraftsNeverReplaceSavedCopy() = fixture { repo ->
        repo.write(owner, AppStateWrite(scope, 0, true, doc))
        assertFailsWith<IllegalArgumentException> { repo.write(owner, AppStateWrite(scope, 1, true, doc.copy(drafts = mapOf("password" to "secret")))) }
        assertEquals(doc, repo.read(owner, scope).document)
    }
}
