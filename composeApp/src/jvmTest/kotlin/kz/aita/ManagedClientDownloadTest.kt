package kz.aita

import kz.aita.updates.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlin.test.*

/** Uses the real streaming writer, hash, journal and cleanup; no network or executable installers. */
class ManagedClientDownloadTest {
    private val content = ByteArray(300_000) { (it % 251).toByte() }
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }
    private class Connection(val input: InputStream, val length: Long?) : HttpURLConnection(URL("https://updates.example.org/app.pkg")) {
        var disconnected = false
        override fun connect() = Unit
        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
        override fun getInputStream() = input
        override fun getHeaderField(name: String): String? = if(name == "Content-Length") length?.toString() else null
    }
    private fun scenario(bytes: ByteArray = content, declared: Long? = bytes.size.toLong(),
        input: InputStream = ByteArrayInputStream(bytes),
        body: suspend (java.io.File, ManagedClientInstaller, ClientRelease, ClientArtifact, Connection) -> Unit) = runBlocking {
        val root = Files.createTempDirectory("aita-download-test-").toRealPath().toFile()
        val connection = Connection(input, declared)
        val cache = ManagedClientInstaller(root, testConnection = { connection }) { a,b ->
            Files.move(a.toPath(),b.toPath(),StandardCopyOption.REPLACE_EXISTING); Unit
        }
        val artifact = ClientArtifact(ClientOs.MACOS,ClientArch.ARM64,InstallerKind.PKG,
            "https://updates.example.org/app.pkg",content.size.toLong(),sha(content))
        val release = ClientRelease(channel=ReleaseChannel.RELEASE,sequence=2,id="r2",version="1.0.0",build=2,
            publishedAtMillis=1,expiresAtMillis=Long.MAX_VALUE,artifacts=listOf(artifact))
        try { body(root,cache,release,artifact,connection) }
        finally { root.deleteRecursively() }
    }
    @Test fun verifiedDownloadCommitsJournalThenRestoresAfterRestart() = scenario { root, cache, release, artifact, connection ->
        val progress = mutableListOf<Pair<Long,Long>>()
        val saved = cache.prepare(release,artifact) { done,total -> progress.add(done to total) }
        assertEquals(content.size.toLong() to content.size.toLong(),progress.last())
        assertContentEquals(content,cache.verifiedFile(saved,artifact).readBytes())
        assertEquals(saved,cache.restore(release,artifact)); assertTrue(connection.disconnected)
        assertFalse(root.listFiles().orEmpty().any { it.name.endsWith(".part") })
    }
    @Test fun privateCacheParentAliasCanDownloadAndRestoreWithoutAcceptingLinkedUpdateDirectory() = scenario { root, _, release, artifact, connection ->
        val realCache = root.resolve("app-cache").apply { mkdirs() }
        val alias = root.resolve("os-cache-alias")
        Files.createSymbolicLink(alias.toPath(), realCache.toPath())
        try {
            // This Android regression relies on POSIX canonical paths. NTFS does not
            // resolve parent symlinks through File.canonicalFile; its own link tests still run.
            org.junit.Assume.assumeTrue("Requires Android/POSIX canonical parent aliases",
                alias.canonicalFile == realCache.canonicalFile)
            // Android can expose /data/user/0 while the canonical private parent is /data/data.
            assertFailsWith<ClientUpdateFailure> { ManagedClientInstaller(alias.resolve("client-updates")) { _, _ -> } }
            fun open() = ManagedClientInstaller(privateClientInstallerDirectory(alias), testConnection = { connection }) { a, b ->
                Files.move(a.toPath(), b.toPath(), StandardCopyOption.REPLACE_EXISTING); Unit
            }
            val cache = open()
            val saved = cache.prepare(release, artifact) { _, _ -> }
            val reopened = open()
            assertEquals(saved, reopened.restore(release, artifact))
            assertContentEquals(content, reopened.verifiedFile(saved, artifact).readBytes())

            val updates = realCache.resolve("client-updates")
            assertTrue(updates.deleteRecursively())
            val outside = root.resolve("outside").apply { mkdirs() }
            Files.createSymbolicLink(updates.toPath(), outside.toPath())
            try {
                assertFailsWith<ClientUpdateFailure> { open() }
                assertTrue(outside.listFiles().orEmpty().isEmpty())
            } finally { Files.delete(updates.toPath()) }
        } finally { Files.deleteIfExists(alias.toPath()) }
    }
    @Test fun truncatedResponseNeverCommitsInstallerOrJournal() = scenario(bytes=content.copyOf(99),declared=null) { root, cache, release, artifact, connection ->
        assertFailsWith<ClientUpdateFailure> { cache.prepare(release,artifact) { _,_ -> } }
        assertNull(cache.restore(release,artifact)); assertTrue(connection.disconnected)
        assertTrue(root.listFiles().orEmpty().isEmpty())
    }
    @Test fun oversizedResponseIsBoundedAndPartialFileRemoved() = scenario(bytes=content + byteArrayOf(1),declared=null) { root, cache, release, artifact, connection ->
        assertFailsWith<ClientUpdateFailure> { cache.prepare(release,artifact) { _,_ -> } }
        assertTrue(connection.disconnected); assertTrue(root.listFiles().orEmpty().isEmpty())
    }
    @Test fun sameLengthWrongHashIsRejected() = scenario(bytes=ByteArray(content.size)) { root, cache, release, artifact, connection ->
        assertFailsWith<ClientUpdateFailure> { cache.prepare(release,artifact) { _,_ -> } }
        assertTrue(connection.disconnected); assertTrue(root.listFiles().orEmpty().isEmpty())
    }
    @Test fun mismatchedContentLengthIsRejectedBeforeWriting() = scenario(declared=1) { root, cache, release, artifact, connection ->
        assertFailsWith<ClientUpdateFailure> { cache.prepare(release,artifact) { _,_ -> } }
        assertTrue(connection.disconnected); assertTrue(root.listFiles().orEmpty().isEmpty())
    }
    @Test fun cancellationCleansPartialDownloadAndDoesNotBecomeReady() = scenario(input=object : InputStream() {
        override fun read(): Int = throw CancellationException("test cancellation")
    }) { root, cache, release, artifact, connection ->
        assertFailsWith<CancellationException> { cache.prepare(release,artifact) { _,_ -> } }
        assertTrue(connection.disconnected); assertTrue(root.listFiles().orEmpty().isEmpty())
    }
}
