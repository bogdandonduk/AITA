package kz.aita

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kz.aita.updates.*

internal fun publicDownloadConnection(url: String, offset: Long = 0): HttpURLConnection {
    var next = url
    repeat(6) {
        if (!isPublicClientReleaseUrl(next)) throw ClientUpdateFailure("integrity")
        val parsed = URL(next)
        val addresses = InetAddress.getAllByName(parsed.host)
        if (addresses.isEmpty() || addresses.any { it.isAnyLocalAddress || it.isLoopbackAddress || it.isLinkLocalAddress ||
                it.isSiteLocalAddress || it.isMulticastAddress || (it.address.size == 4 && (it.address[0].toInt() and 255) == 100 && (it.address[1].toInt() and 192) == 64) })
            throw ClientUpdateFailure("integrity")
        val connection = parsed.openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 15_000; connection.readTimeout = 30_000
        connection.setRequestProperty("Accept-Encoding", "identity")
        connection.setRequestProperty("Cache-Control", "no-cache")
        if (offset > 0) connection.setRequestProperty("Range", "bytes=$offset-")
        val code = try { connection.responseCode } catch (failure: Exception) { connection.disconnect(); throw failure }
        if (code in setOf(301, 302, 303, 307, 308)) {
            val location = connection.getHeaderField("Location") ?: run { connection.disconnect(); throw ClientUpdateFailure("network") }
            next = URL(parsed, location).toExternalForm(); connection.disconnect()
        } else {
            if (code !in setOf(200, 206)) {
                connection.disconnect()
                if (code == 408 || code == 429 || code in 500..599) throw IOException("Temporary download response")
                throw ClientUpdateFailure("network")
            }
            return connection
        }
    }
    throw ClientUpdateFailure("network")
}

/** Reconnect only safe public GETs. Never append a full (200) response to a partial file.
 * The signed manifest's length and final SHA-256 remain mandatory across every retry.
 * Partial bytes live only within this attempt; cancellation/final failure is cleaned by callers.
 */
internal suspend fun transferVerifiedClientFile(destination: File, url: String, expected: Long, sha256: String,
    progress: (Long, Long) -> Unit, connectionFactory: (String, Long) -> HttpURLConnection = ::publicDownloadConnection,
    backoff: suspend (Long) -> Unit = { delay(it) }) {
    require(expected in 1..CLIENT_INSTALLER_MAX_BYTES)
    var count = 0L
    var reported = 0L
    val digest = MessageDigest.getInstance("SHA-256")
    repeat(4) { attempt ->
        currentCoroutineContext().ensureActive()
        var connection: HttpURLConnection? = null
        try {
            connection = connectionFactory(url, count)
            val range = connection.getHeaderField("Content-Range")
            if (connection.responseCode == 206 && range == null) throw ClientUpdateFailure("integrity")
            if (connection.responseCode == 200 && range != null) throw ClientUpdateFailure("integrity")
            if (range != null) {
                val match = Regex("bytes ([0-9]+)-([0-9]+)/([0-9]+)").matchEntire(range)
                    ?: throw ClientUpdateFailure("integrity")
                if (match.groupValues[1].toLongOrNull() != count || match.groupValues[2].toLongOrNull() != expected - 1 ||
                    match.groupValues[3].toLongOrNull() != expected) throw ClientUpdateFailure("integrity")
            } else if (count > 0) {
                // Servers/proxies may ignore Range. A fresh download still has to match the hash.
                count = 0L; reported = 0L; digest.reset(); progress(0L, expected)
            }
            val declared = connection.getHeaderField("Content-Length")?.toLongOrNull()
            if (declared != null && declared != expected - count) throw ClientUpdateFailure("integrity")
            val input = connection.inputStream
            input.use {
                val destinationStream = try { FileOutputStream(destination, count > 0) }
                    catch (_: IOException) { throw ClientUpdateFailure("storage") }
                destinationStream.use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val size = input.read(buffer)
                        if (size < 0) break
                        if (size == 0) continue
                        if (count + size > expected) throw ClientUpdateFailure("integrity")
                        // Disk failures are not retryable network failures.
                        try { output.write(buffer, 0, size) } catch (_: IOException) { throw ClientUpdateFailure("storage") }
                        digest.update(buffer, 0, size); count += size
                        if (count - reported >= 256 * 1024L) { progress(count, expected); reported = count }
                    }
                    try { output.fd.sync() } catch (_: IOException) { throw ClientUpdateFailure("storage") }
                }
            }
            if (count != expected) throw IOException("Incomplete download")
            val actual = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
            if (actual != sha256) throw ClientUpdateFailure("integrity")
            progress(count, expected)
            return
        } catch (failure: IOException) {
            if (attempt == 3) throw ClientUpdateFailure("network")
        } finally { connection?.disconnect() }
        backoff(500L * (1L shl attempt))
    }
}

/** No visible download/installer is published before complete byte and hash verification. */
internal suspend fun fetchVerifiedClientDownload(directory: File, file: ClientDownloadFile, progress: (Long, Long) -> Unit,
    connectionFactory: ((String) -> HttpURLConnection)? = null): File = withContext(Dispatchers.IO) {
    if (file.bytes !in 1..CLIENT_INSTALLER_MAX_BYTES || !Regex("[0-9a-f]{64}").matches(file.sha256) || !isPublicClientReleaseUrl(file.url))
        throw ClientUpdateFailure("integrity")
    if (!directory.isDirectory && !directory.mkdirs()) throw ClientUpdateFailure("storage")
    if (directory.usableSpace < file.bytes + 16L * 1024 * 1024) throw ClientUpdateFailure("space")
    val temporary = File.createTempFile("aita-download-", ".part", directory)
    var complete = false
    try {
        transferVerifiedClientFile(temporary, file.url, file.bytes, file.sha256, progress,
            connectionFactory = { url, offset -> connectionFactory?.invoke(url) ?: publicDownloadConnection(url, offset) })
        complete = true; temporary
    } finally { if (!complete) temporary.delete() }
}
