package kz.aita

import java.nio.file.Files
import kotlin.test.*

class DesktopDataMigrationTest {
    @Test fun subsequentLaunchCannotImportOldDatabaseSidecarsIntoCurrentDatabase() {
        val root = Files.createTempDirectory("aita-data-migration").toFile()
        try {
            val legacy = root.resolve("old").apply { mkdirs() }
            val current = root.resolve("data").apply { mkdirs() }
            legacy.resolve("app_database.db").writeText("old database")
            current.resolve("app_database.db").writeText("current database")
            for (suffix in listOf("-journal", "-wal", "-shm")) {
                legacy.resolve("app_database.db$suffix").writeText("stale journal")
            }
            repeat(2) { migrateLegacyDesktopPersistentState(current, legacy) }
            assertEquals("current database", current.resolve("app_database.db").readText())
            for (suffix in listOf("-journal", "-wal", "-shm")) {
                assertFalse(current.resolve("app_database.db$suffix").exists(), "Must never replay $suffix from another database")
            }
            assertTrue(legacy.resolve("app_database.db-wal").exists(), "Recovery source must be retained")
        } finally { root.deleteRecursively() }
    }
}
