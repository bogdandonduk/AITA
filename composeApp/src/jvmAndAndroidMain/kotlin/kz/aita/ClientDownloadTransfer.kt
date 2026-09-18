package kz.aita

import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kz.aita.updates.*

private fun publicDownloadConnection(url: String): HttpURLConnection {
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
        val code = try { connection.responseCode } catch (failure: Exception) { connection.disconnect(); throw failure }
        if (code in setOf(301, 302, 303, 307, 308)) {
            val location = connection.getHeaderField("Location") ?: run { connection.disconnect(); throw ClientUpdateFailure("network") }
            next = URL(parsed, location).toExternalForm(); connection.disconnect()
        } else {
            if (code != 200) { connection.disconnect(); throw ClientUpdateFailure("network") }
            return connection
        }
    }
    throw ClientUpdateFailure("network")
}

/** The destination is not created until all bytes and SHA-256 match the authenticated catalogue. */
internal suspend fun fetchVerifiedClientDownload(directory: File, file: ClientDownloadFile, progress: (Long, Long) -> Unit,
    connectionFactory: ((String) -> HttpURLConnection)? = null): File = withContext(Dispatchers.IO) {
    if (file.bytes !in 1..CLIENT_INSTALLER_MAX_BYTES || !Regex("[0-9a-f]{64}").matches(file.sha256) || !isPublicClientReleaseUrl(file.url))
        throw ClientUpdateFailure("integrity")
    if (!directory.isDirectory && !directory.mkdirs()) throw ClientUpdateFailure("storage")
    if (directory.usableSpace < file.bytes + 16L * 1024 * 1024) throw ClientUpdateFailure("space")
    val temporary = File.createTempFile("aita-download-", ".part", directory)
    var connection: HttpURLConnection? = null
    var complete = false
    try {
        connection = connectionFactory?.invoke(file.url) ?: publicDownloadConnection(file.url)
        val declared = connection.getHeaderField("Content-Length")?.toLongOrNull()
        if (declared != null && declared != file.bytes) throw ClientUpdateFailure("integrity")
        val digest = MessageDigest.getInstance("SHA-256"); var count = 0L; var reported = 0L
        connection.inputStream.use { input -> FileOutputStream(temporary).use { output ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val size = input.read(buffer); if (size < 0) break
                count += size
                if (count > file.bytes) throw ClientUpdateFailure("integrity")
                digest.update(buffer, 0, size); output.write(buffer, 0, size)
                if (count - reported >= 256 * 1024L) { progress(count, file.bytes); reported = count }
            }
            output.fd.sync()
        } }
        val hash = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
        if (count != file.bytes || hash != file.sha256) throw ClientUpdateFailure("integrity")
        progress(count, count); complete = true; temporary
    } finally {
        connection?.disconnect()
        if (!complete) temporary.delete()
    }
}
