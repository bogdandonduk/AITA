package kz.aita

import java.nio.channels.FileChannel
import java.nio.file.*
import java.nio.file.attribute.PosixFilePermissions
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

internal class DesktopDiagnosticStorage(private val folder: Path) : DiagnosticLocalStorage, java.io.Closeable {
    private val file = folder.resolve("journal.json")
    private val lock = Any()
    private var ownerChannel: FileChannel? = null
    private var ownerLock: java.nio.channels.FileLock? = null
    private var closed = false
    private fun acquireOwnership() {
        check(!closed)
        if (ownerLock?.isValid == true) return
        check(!Files.isSymbolicLink(folder))
        Files.createDirectories(folder); permissions(folder, "rwx------")
        val path = folder.resolve("owner.lock")
        val channel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)
        try {
            permissions(path, "rw-------")
            val held = channel.tryLock() ?: error("Another AITA instance owns the diagnostic journal")
            ownerChannel = channel; ownerLock = held
        } catch (failure: Exception) { channel.close(); throw failure }
    }
    override fun close() = synchronized(lock) {
        ownerLock?.release(); ownerChannel?.close(); ownerLock = null; ownerChannel = null; closed = true
    }
    override fun read(): String? = synchronized(lock) {
        acquireOwnership()
        check(!Files.isSymbolicLink(folder) && !Files.isSymbolicLink(file))
        if (!Files.exists(file)) null else {
            require(Files.size(file) <= 2_000_000)
            Files.readString(file).also { require(it.length <= 2_000_000) }
        }
    }
    private fun permissions(path: Path, mode: String) {
        try { Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(mode)) }
        catch (_: UnsupportedOperationException) { /* Windows uses directory ACLs rather than POSIX permission bits. */ }
    }
    override fun write(value: String) = replace(value, false)
    override fun reset(value: String) = replace(value, true)
    private fun replace(value: String, reset: Boolean) = synchronized(lock) {
        acquireOwnership()
        require(value.length <= 2_000_000)
        diagnosticJson.decodeFromString(DiagnosticJournal.serializer(), value).validated()
        check(!Files.isSymbolicLink(folder))
        Files.createDirectories(folder); permissions(folder, "rwx------")
        val lockPath = folder.resolve("journal.lock")
        FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS).use { channel ->
            permissions(lockPath, "rw-------")
            val held = channel.tryLock() ?: error("Another AITA process is writing diagnostics")
            held.use {
                if (!reset && !shouldWriteDiagnosticJournal(read(), value)) return@synchronized
                val temporary = Files.createTempFile(folder, "journal-", ".tmp")
                try {
                    permissions(temporary, "rw-------")
                    Files.writeString(temporary, value)
                    FileChannel.open(temporary, StandardOpenOption.WRITE).use { it.force(true) }
                    try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
                    catch (_: AtomicMoveNotSupportedException) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING) }
                } finally { Files.deleteIfExists(temporary) }
            }
        }
        Unit
    }
}
private val desktopDiagnosticsInstalled = AtomicBoolean(false)
internal fun installDesktopRuntimeDiagnostics() {
    if (!desktopDiagnosticsInstalled.compareAndSet(false, true)) return
    val folder = Paths.get(jvmPersistentDataDirPath, "diagnostics")
    RuntimeDiagnostics.configure(DesktopDiagnosticStorage(folder), diagnosticBuildContext(DiagnosticDevice("desktop",
        "${System.getProperty("os.name")} ${System.getProperty("os.version")}",
        "JVM ${System.getProperty("java.version")}", System.getProperty("os.arch"))), newId = { UUID.randomUUID().toString() },
        frameExtractor = { error -> diagnosticStackFrames(error.stackTrace.take(48).joinToString("\n") { "at $it" }) })
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, error ->
        try { RuntimeDiagnostics.capture(error, "jvm.uncaught", fatal = true) }
        finally { if (previous != null) previous.uncaughtException(thread, error) else error.printStackTrace(System.err) }
    }
}
