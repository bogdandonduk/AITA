package kz.aita

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.QueryResult
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.sql.DriverManager
import java.util.concurrent.Executors
import kotlin.test.*

class JvmInventoryPersistenceTest {
    private fun pragma(driver: SqlDriver, name: String): Long = driver.executeQuery(null, "PRAGMA $name", {
        QueryResult.Value(if (it.next().value) it.getLong(0)!! else -1L)
    }, 0).value

    @Test fun settingsApplyToEveryConnectionAndSchemaVersionIsRecorded() {
        val root = Files.createTempDirectory("aita-db-version").toFile()
        val executor = Executors.newSingleThreadExecutor()
        try {
            val driver = openJvmLocalDatabase(root)
            try {
                assertEquals(AppDatabase.Schema.version, pragma(driver, "user_version"))
                repeat(2) {
                    assertEquals(1L, pragma(driver, "foreign_keys"))
                    assertEquals(5000L, pragma(driver, "busy_timeout"))
                }
                assertEquals(1L, executor.submit<Long> { pragma(driver, "foreign_keys") }.get())
                assertEquals(5000L, executor.submit<Long> { pragma(driver, "busy_timeout") }.get())
            } finally { driver.close() }
        } finally { executor.shutdownNow(); root.deleteRecursively() }
    }

    @Test fun chunkedStockBatchesAndPendingWritesSurviveVersionZeroUpgradeAndReopening() = runBlocking {
        val root = Files.createTempDirectory("aita-db-inventory").toFile()
        val snapshots = mapOf(
            "cache_json:inventory-v2:stock:account:store" to "[{\"id\":\"item\",\"note\":\"${"Товар 😀".repeat(20_000)}\"}]",
            "cache_json:inventory-v2:stock_batches:account:store" to "[{\"id\":\"batch\",\"quantity\":5}]",
            "inventory-create-outbox-v1:account" to "[{\"id\":\"offline-edit\"}]"
        )
        try {
            openJvmLocalDatabase(root).let { driver ->
                try {
                    setAppDatabaseForTests(AppDatabase(driver))
                    for ((key, value) in snapshots) writeJsonCacheText(key, value)
                    driver.execute(null, "PRAGMA user_version = 0", 0) // What earlier desktop releases left behind.
                } finally { setAppDatabaseForTests(null); driver.close() }
            }
            repeat(2) {
                val driver = openJvmLocalDatabase(root)
                try {
                    setAppDatabaseForTests(AppDatabase(driver))
                    for ((key, value) in snapshots) assertEquals(value, readJsonCacheText(key))
                    assertEquals(AppDatabase.Schema.version, pragma(driver, "user_version"))
                } finally { setAppDatabaseForTests(null); driver.close() }
            }
        } finally { root.deleteRecursively() }
    }

    @Test fun migrationIncludesCommittedWalAndNeverReimportsItOnLaterLaunches() {
        val root = Files.createTempDirectory("aita-db-import").toFile()
        val legacy = root.resolve("legacy").apply { mkdirs() }
        val data = root.resolve("data").apply { mkdirs() }
        val source = legacy.resolve("app_database.db")
        val destination = data.resolve("app_database.db")
        try {
            openJvmLocalDatabase(legacy).close()
            DriverManager.getConnection("jdbc:sqlite:$source").use { connection ->
                connection.createStatement().use {
                    it.execute("PRAGMA journal_mode = WAL")
                    it.execute("PRAGMA wal_autocheckpoint = 0")
                    it.execute("INSERT INTO key_value VALUES ('stock', 'committed in WAL')")
                }
                assertTrue(legacy.resolve("app_database.db-wal").length() > 0)
                assertTrue(migrateJvmPersistentDatabase(source, destination))
                DriverManager.getConnection("jdbc:sqlite:$destination").use { current ->
                    current.createStatement().use {
                        it.executeQuery("SELECT value FROM key_value WHERE key = 'stock'").use { result ->
                            assertTrue(result.next()); assertEquals("committed in WAL", result.getString(1))
                        }
                        it.execute("UPDATE key_value SET value = 'new local work' WHERE key = 'stock'")
                    }
                }
                assertFalse(migrateJvmPersistentDatabase(source, destination))
                assertFalse(data.resolve("app_database.db-wal").exists())
                DriverManager.getConnection("jdbc:sqlite:$destination").use { current ->
                    current.createStatement().use {
                        it.executeQuery("SELECT value FROM key_value WHERE key = 'stock'").use { result ->
                            assertTrue(result.next()); assertEquals("new local work", result.getString(1))
                        }
                    }
                }
            }
            assertTrue(source.exists())
        } finally { root.deleteRecursively() }
    }

    @Test fun failedMigrationKeepsOriginalAndDoesNotCreateAnEmptyReplacement() {
        val root = Files.createTempDirectory("aita-db-failure").toFile()
        try {
            val old = root.resolve("old.db").apply { writeText("damaged original") }
            val next = root.resolve("new.db")
            assertFails { migrateJvmPersistentDatabase(old, next) }
            assertEquals("damaged original", old.readText())
            assertFalse(next.exists())
            assertTrue(root.listFiles()!!.none { it.name.startsWith(".aita-database-import-") })
        } finally { root.deleteRecursively() }
    }

    @Test fun openingNewerSchemaRefusesDowngradeWithoutResettingData() {
        val root = Files.createTempDirectory("aita-db-newer").toFile()
        try {
            val driver = openJvmLocalDatabase(root)
            driver.execute(null, "PRAGMA user_version = ${AppDatabase.Schema.version + 1}", 0)
            driver.close()
            assertFailsWith<IllegalStateException> { openJvmLocalDatabase(root) }
            assertTrue(root.resolve("app_database.db").length() > 0)
        } finally { root.deleteRecursively() }
    }
}
