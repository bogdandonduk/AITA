package kz.aita

import java.io.File
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.sql.DriverManager

/** Import once, including committed WAL content. Never mix sidecars from two databases. */
fun migrateJvmPersistentDatabase(source: File, destination: File): Boolean {
    if (destination.exists() || !source.isFile || source.canonicalFile == destination.canonicalFile) return false
    val parent = requireNotNull(destination.parentFile)
    Files.createDirectories(parent.toPath())
    check(listOf("-journal", "-wal", "-shm").none { File(destination.path + it).exists() }) {
        "AITA local database has recovery files but no main file; original files retained"
    }
    val snapshot = Files.createTempFile(parent.toPath(), ".aita-database-import-", ".db")
    try {
        // SQLite produces one consistent snapshot instead of separately copying the database,
        // WAL and rollback journal while their state can change. Keep the old copy for recovery.
        DriverManager.getConnection("jdbc:sqlite:${source.toURI()}?mode=ro").use { connection ->
            connection.createStatement().use { it.execute("PRAGMA busy_timeout = 5000") }
            connection.prepareStatement("VACUUM INTO ?").use { statement ->
                statement.setString(1, snapshot.toAbsolutePath().toString())
                statement.execute()
            }
        }
        DriverManager.getConnection("jdbc:sqlite:$snapshot").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("PRAGMA quick_check").use { result ->
                    check(result.next() && result.getString(1) == "ok" && !result.next()) {
                        "AITA imported database failed integrity verification; original files retained"
                    }
                }
            }
        }
        FileChannel.open(snapshot, StandardOpenOption.WRITE).use { it.force(true) }
        // Same-directory rename is the commit point. Do not replace a database created elsewhere.
        Files.move(snapshot, destination.toPath())
        return true
    } finally {
        Files.deleteIfExists(snapshot)
    }
}
