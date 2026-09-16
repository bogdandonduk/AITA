package kz.aita

import kz.aita.updates.*
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
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

/** This directory contains only updater-owned, content-addressed artifacts. No Downloads-folder sweeps. */
internal class ManagedClientInstaller(
    directory: File,
    private val testConnection: ((String) -> HttpURLConnection)? = null,
    private val replace: (File, File) -> Unit
) {
    private val root = directory.canonicalFile
    init {
        if (directory.absoluteFile != root || (!root.isDirectory && !root.mkdirs())) throw ClientUpdateFailure("storage")
    }
    private fun child(name: String): File {
        if (!Regex("[A-Za-z0-9._-]{1,160}").matches(name)) throw ClientUpdateFailure("storage")
        val file = File(root, name)
        if (file.canonicalFile != file.absoluteFile || file.canonicalFile.parentFile != root) throw ClientUpdateFailure("storage")
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
    private fun openDownload(url: String): HttpURLConnection {
        var next = url
        repeat(6) {
            if (!isPublicClientReleaseUrl(next)) throw ClientUpdateFailure("integrity")
            val parsed = URL(next)
            val addresses = InetAddress.getAllByName(parsed.host)
            if (addresses.isEmpty() || addresses.any { it.isAnyLocalAddress || it.isLoopbackAddress || it.isLinkLocalAddress ||
                    it.isSiteLocalAddress || it.isMulticastAddress || (it.address.size == 4 && (it.address[0].toInt() and 255) == 100 && (it.address[1].toInt() and 192) == 64) }) throw ClientUpdateFailure("integrity")
            val connection = parsed.openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000; connection.readTimeout = 30_000
            connection.setRequestProperty("Accept-Encoding", "identity")
            connection.setRequestProperty("Cache-Control", "no-cache")
            // This is NOT the application's authenticated HTTP client. Never attach account headers/cookies.
            val code = try { connection.responseCode } catch (e: Exception) { connection.disconnect(); throw e }
            if (code in setOf(301,302,303,307,308)) {
                val location = connection.getHeaderField("Location") ?: run { connection.disconnect(); throw ClientUpdateFailure("network") }
                next = URL(parsed,location).toExternalForm(); connection.disconnect()
            } else {
                if (code != 200) { connection.disconnect(); throw ClientUpdateFailure("network") }
                return connection
            }
        }
        throw ClientUpdateFailure("network")
    }
    suspend fun prepare(release: ClientRelease, artifact: ClientArtifact, progress: (Long,Long)->Unit): PreparedClientInstaller = withContext(Dispatchers.IO) {
        if (!artifact.isFile || artifact.bytes !in 1..CLIENT_INSTALLER_MAX_BYTES) throw ClientUpdateFailure("integrity")
        restore(release, artifact)?.let { return@withContext it }
        val name = fileName(release, artifact); val part = child("$name.part"); val complete = child(name)
        val connection = testConnection?.invoke(artifact.url) ?: openDownload(artifact.url)
        try {
            val declared = connection.getHeaderField("Content-Length")?.toLongOrNull()
            if (declared != null && declared != artifact.bytes) throw ClientUpdateFailure("integrity")
            if (root.usableSpace < artifact.bytes + 16L*1024*1024) throw ClientUpdateFailure("space")
            val digest = MessageDigest.getInstance("SHA-256"); var count = 0L; var reported = 0L
            connection.inputStream.use { input -> FileOutputStream(part).use { out ->
                val buffer = ByteArray(64*1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val n = input.read(buffer); if (n < 0) break
                    count += n
                    if (count > artifact.bytes) throw ClientUpdateFailure("integrity")
                    digest.update(buffer,0,n); out.write(buffer,0,n)
                    if (count - reported >= 256*1024L) { progress(count, artifact.bytes); reported = count }
                }
                out.fd.sync()
            } }
            val actual = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
            if (count != artifact.bytes || actual != artifact.sha256) throw ClientUpdateFailure("integrity")
            replace(part, complete)
            val record = PreparedClientInstaller(release.id,release.build,release.channel,name,actual,count,System.currentTimeMillis())
            save(record); progress(count,count); record
        } finally { connection.disconnect(); if(part.exists()) part.delete() }
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
    fun readPreference(key: String): String? = child("pref-$key").takeIf { it.isFile && it.length() < CLIENT_RELEASE_MAX_BYTES*2 }?.readText()
    fun writePreference(key: String, value: String?) {
        val f = child("pref-$key")
        if (value == null) { f.delete(); return }
        if (value.length > CLIENT_RELEASE_MAX_BYTES*2) throw ClientUpdateFailure("storage")
        val tmp = child("pref-$key.tmp"); FileOutputStream(tmp).use { it.write(value.toByteArray()); it.fd.sync() }; replace(tmp,f)
    }
}
