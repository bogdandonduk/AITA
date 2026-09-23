package kz.aita

import kz.aita.updates.*
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal fun verifyRsaClientRelease(payload: ByteArray, signature: ByteArray, key: ByteArray): Boolean = runCatching {
    val publicKey = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(key)) as RSAPublicKey
    if (publicKey.modulus.bitLength() !in 2048..8192) return false
    Signature.getInstance("SHA256withRSA").run { initVerify(publicKey); update(payload); verify(signature) }
}.getOrDefault(false)

/** Resolve only the OS-provided private cache parent (Android can alias /data/user/0).
 * The updater-owned child and its files still pass the strict no-symlink checks. */
internal fun privateClientInstallerDirectory(appCacheDirectory: File): File =
    File(appCacheDirectory.canonicalFile, "client-updates")

/** This directory contains only updater-owned, content-addressed artifacts. No Downloads-folder sweeps. */
internal class ManagedClientInstaller(
    directory: File,
    private val testConnection: ((String) -> HttpURLConnection)? = null,
    private val replace: (File, File) -> Unit
) {
    private val root = directory.canonicalFile
    init {
        if (directory.absoluteFile != root || (!root.isDirectory && !root.mkdirs())) throw ClientUpdateFailure("storage")
        if (managedInstallerFileIdentity(root) != root) throw ClientUpdateFailure("storage")
    }
    private fun child(name: String): File {
        if (!Regex("[A-Za-z0-9._-]{1,160}").matches(name)) throw ClientUpdateFailure("storage")
        val file = File(root, name)
        val identity = managedInstallerFileIdentity(file)
        if (identity != file.absoluteFile || identity.parentFile != root) throw ClientUpdateFailure("storage")
        return file
    }
    private fun journalName(channel: ReleaseChannel) = "${channel.name.lowercase()}-pending.json"
    private fun journal(channel: ReleaseChannel): PreparedClientInstaller? = runCatching {
        val f = child(journalName(channel)); if (!f.isFile || f.length() > 8192) return null
        clientReleaseJson.decodeFromString<PreparedClientInstaller>(f.readText())
    }.getOrNull()
    private fun save(journal: PreparedClientInstaller) {
        val tmp = child(journalName(journal.channel) + ".tmp")
        FileOutputStream(tmp).use { it.write(clientReleaseJson.encodeToString(PreparedClientInstaller.serializer(), journal).toByteArray()); it.fd.sync() }
        replace(tmp, child(journalName(journal.channel)))
    }
    private fun fileName(release: ClientRelease, artifact: ClientArtifact) =
        "${release.channel.name.lowercase()}-${release.build}-${artifact.sha256}.${artifact.extension}"
    private fun validOwnedFileName(name: String) = Regex("(release|test)-[0-9]+-[0-9a-f]{64}\\.(apk|msi|exe|pkg|dmg|deb|rpm)(\\.part)?").matches(name)
    private fun hash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val buf = ByteArray(64 * 1024); while (true) { val n = input.read(buf); if(n < 0) break; digest.update(buf,0,n) } }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }
    fun verifiedFile(record: PreparedClientInstaller, artifact: ClientArtifact): File {
        if (!validOwnedFileName(record.fileName) || !record.fileName.endsWith(".${artifact.extension}") ||
            record.sha256 != artifact.sha256 || record.bytes != artifact.bytes) throw ClientUpdateFailure("integrity")
        val f = child(record.fileName)
        if (!f.isFile || f.length() != artifact.bytes || hash(f) != artifact.sha256) throw ClientUpdateFailure("integrity")
        return f
    }
    suspend fun restore(release: ClientRelease, artifact: ClientArtifact): PreparedClientInstaller? = withContext(Dispatchers.IO) {
        val j = journal(release.channel) ?: return@withContext null
        if (j.releaseId != release.id || j.build != release.build || j.fileName != fileName(release, artifact)) return@withContext null
        runCatching { verifiedFile(j,artifact); j }.getOrNull()
    }
    suspend fun prepare(release: ClientRelease, artifact: ClientArtifact, progress: (Long,Long)->Unit): PreparedClientInstaller = withContext(Dispatchers.IO) {
        if (!artifact.isFile || artifact.bytes !in 1..CLIENT_INSTALLER_MAX_BYTES) throw ClientUpdateFailure("integrity")
        restore(release, artifact)?.let { return@withContext it }
        val name = fileName(release, artifact); val part = child("$name.part"); val complete = child(name)
        try {
            if (root.usableSpace < artifact.bytes + 16L * 1024 * 1024) throw ClientUpdateFailure("space")
            transferVerifiedClientFile(part, artifact.url, artifact.bytes, artifact.sha256, progress,
                connectionFactory = { url, offset -> testConnection?.invoke(url) ?: publicDownloadConnection(url, offset) })
            replace(part, complete)
            val record = PreparedClientInstaller(release.id, release.build, release.channel, name, artifact.sha256, artifact.bytes, System.currentTimeMillis())
            save(record); record
        } finally { if (part.exists()) part.delete() }
    }
    suspend fun clean(installed: ClientBuildIdentity) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val protected = ReleaseChannel.entries.mapNotNull { channel ->
            val record = journal(channel) ?: return@mapNotNull null
            if (clientUpdateInstalled(record.build, channel, installed)) {
                val artifact = if(validOwnedFileName(record.fileName)) child(record.fileName) else null
                // Keep the journal on a sharing/permissions failure so the next launch retries cleanup.
                if (artifact == null || !artifact.exists() || artifact.delete()) {
                    child(journalName(channel)).delete(); null
                } else record.fileName
            } else record.fileName
        }.toSet()
        root.listFiles().orEmpty().filter { validOwnedFileName(it.name) && it.name !in protected }.forEach {
            if (it.name.endsWith(".part") || now - it.lastModified() > 14L*86_400_000L) runCatching { child(it.name).delete() }
        }
    }
    /** Copy a verified public download into private installer storage, rechecking the exact bytes.
     * The user's chosen download remains theirs; only this private copy is cleaned on next launch. */
    suspend fun importVerified(release: ClientRelease, artifact: ClientArtifact, source: File): PreparedClientInstaller = withContext(Dispatchers.IO) {
        if (!artifact.isFile || artifact.bytes !in 1..CLIENT_INSTALLER_MAX_BYTES || source.length() != artifact.bytes)
            throw ClientUpdateFailure("integrity")
        val name = fileName(release, artifact)
        val part = child("$name.part")
        try {
            if (root.usableSpace < artifact.bytes + 16L * 1024 * 1024) throw ClientUpdateFailure("space")
            var count = 0L
            val digest = MessageDigest.getInstance("SHA-256")
            source.inputStream().use { input -> FileOutputStream(part).use { out ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val n = input.read(buffer); if (n < 0) break
                    count += n
                    if (count > artifact.bytes) throw ClientUpdateFailure("integrity")
                    digest.update(buffer, 0, n); out.write(buffer, 0, n)
                }
                out.fd.sync()
            } }
            val hash = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
            if (count != artifact.bytes || hash != artifact.sha256) throw ClientUpdateFailure("integrity")
            replace(part, child(name))
            PreparedClientInstaller(release.id, release.build, release.channel, name, hash, count, System.currentTimeMillis())
                .also(::save)
        } finally { part.delete() }
    }
    fun readPreference(key: String): String? = child("pref-$key").takeIf { it.isFile && it.length() < CLIENT_RELEASE_MAX_BYTES*2 }?.readText()
    fun writePreference(key: String, value: String?) {
        val f = child("pref-$key")
        if (value == null) { f.delete(); return }
        if (value.length > CLIENT_RELEASE_MAX_BYTES*2) throw ClientUpdateFailure("storage")
        val tmp = child("pref-$key.tmp"); FileOutputStream(tmp).use { it.write(value.toByteArray()); it.fd.sync() }; replace(tmp,f)
    }
}
